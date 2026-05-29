import base64
import re
from dataclasses import dataclass, field

import requests

from .config import GlobalConfig
from .constants import (
    DES_PROJECT_KEY_PREFIX,
    IF_PROJECT_KEY_PREFIX,
    IOS_ISSUE_TYPE,
    IOS_WHITE_DESIGN_SUMMARY,
    JIRA_JQL_PROJECTS,
    RELATED_ACCOUNT_LINK,
)

APP_TICKET_FIELDS = [
    "Bundle",
    "AppStore Account Name",
    "Appstore App ID",
    "Team ID",
    "Key ID",
    "Issuer ID",
    "App Name (IOS)",
    "privacy policy",
    "Octo Profile",
]

OPTIONAL_APP_TICKET_FIELDS = [
    "Codemagic API Token",
    "GitHub Token",
]

IF_PROJECT_NAME = "IOS Farm"


@dataclass
class AppTicket:
    key: str
    summary: str
    bundle: str
    appstore_account_name: str
    appstore_app_id: str
    team_id: str
    key_id: str
    issuer_id: str
    app_name_ios: str
    privacy_policy: str
    octo_profile: str = ""
    codemagic_api_token: str = ""
    github_token: str = ""
    attachments: list = field(default_factory=list)
    if_keys_related: list = field(default_factory=list)
    if_keys_other: list = field(default_factory=list)
    parent_key: str = ""


@dataclass
class IfStory:
    key: str
    email: str
    phone: str


def _adf_text(node) -> str:
    if not isinstance(node, dict):
        return ""
    if node.get("type") == "text":
        return node.get("text", "")
    parts = []
    for child in node.get("content", []) or []:
        parts.append(_adf_text(child))
    return "".join(parts)


def _extract_text(v) -> str:
    if v is None:
        return ""
    if isinstance(v, str):
        return v.strip()
    if isinstance(v, dict):
        if v.get("type") == "doc":
            return _adf_text(v).strip()
        for k in ("displayName", "value", "name"):
            if k in v and v[k] is not None:
                return str(v[k]).strip()
    if isinstance(v, list):
        return ", ".join(_extract_text(x) for x in v if x)
    return str(v).strip()


def _check(r: requests.Response, context: str):
    if r.status_code >= 400:
        body = (r.text or "")[:2000]
        raise SystemExit(
            f"Jira {context} failed: HTTP {r.status_code}\n"
            f"URL: {r.request.method} {r.request.url}\n"
            f"Body: {body}"
        )


class JiraClient:
    def __init__(self, cfg: GlobalConfig):
        self.cfg = cfg
        creds = f"{cfg.jira_email}:{cfg.jira_api_token}"
        auth_header = "Basic " + base64.b64encode(creds.encode()).decode()
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": auth_header,
            "Accept": "application/json",
        })
        self._field_map: dict = {}

    def _resolve_fields(self):
        if self._field_map:
            return
        r = self.session.get(f"{self.cfg.jira_base_url}/rest/api/3/field")
        _check(r, "GET /field")
        for f in r.json():
            self._field_map[f["name"]] = f["id"]

    def _field_id(self, name: str) -> str:
        self._resolve_fields()
        fid = self._field_map.get(name)
        if not fid:
            raise SystemExit(
                f"Jira field '{name}' not found. "
                f"Available: {sorted(self._field_map.keys())}"
            )
        return fid

    def _optional_field_id(self, name: str):
        self._resolve_fields()
        return self._field_map.get(name)

    def _search_ios_by_summary(self, number: str, field_ids: list) -> tuple:
        """Return (all_issues, ios_matches) for summary == number."""
        payload = {
            "jql": f'({JIRA_JQL_PROJECTS}) AND summary ~ "{number}"',
            "fields": [
                "summary", "issuetype", "issuelinks", "attachment", "parent",
                *field_ids,
            ],
            "maxResults": 50,
        }
        r = self.session.post(
            f"{self.cfg.jira_base_url}/rest/api/3/search/jql", json=payload
        )
        _check(r, "POST /search/jql")
        issues = r.json().get("issues", []) or []
        matches = []
        for i in issues:
            flds = i["fields"]
            if (flds.get("summary") or "").strip() != number:
                continue
            itype = (flds.get("issuetype") or {}).get("name", "") or ""
            if itype.lower() != IOS_ISSUE_TYPE.lower():
                continue
            matches.append(i)
        return issues, matches

    def find_ticket_by_number(self, number: str) -> AppTicket:
        self._resolve_fields()
        field_ids = [self._field_id(n) for n in APP_TICKET_FIELDS]
        optional_field_ids = {
            n: self._optional_field_id(n) for n in OPTIONAL_APP_TICKET_FIELDS
        }
        field_ids.extend(fid for fid in optional_field_ids.values() if fid)

        candidates = [number]
        try:
            candidates.append(str(int(number) + 1))
        except ValueError:
            pass

        matches = []
        number_used = number
        seen = {}
        for n in candidates:
            issues, matching = self._search_ios_by_summary(n, field_ids)
            seen[n] = [
                {
                    "key": i["key"],
                    "summary": i["fields"].get("summary"),
                    "type": (i["fields"].get("issuetype") or {}).get("name"),
                }
                for i in issues
            ]
            if matching:
                matches = matching
                number_used = n
                if n != number:
                    print(
                        f"  No iOS ticket with summary '{number}'; "
                        f"using '{n}' (KMP→iOS split off-by-one)."
                    )
                break

        if not matches:
            raise SystemExit(
                f"No '{IOS_ISSUE_TYPE}' ticket with summary in {candidates}. "
                f"Near matches: {seen}"
            )
        if len(matches) > 1:
            raise SystemExit(
                f"Multiple '{IOS_ISSUE_TYPE}' tickets match summary "
                f"'{number_used}': {[i['key'] for i in matches]}"
            )

        issue = matches[0]
        fields = issue["fields"]

        def f(name):
            return _extract_text(fields.get(self._field_id(name)))

        def of(name):
            fid = optional_field_ids.get(name)
            return _extract_text(fields.get(fid)) if fid else ""

        if_keys_related = []
        if_keys_other = []
        for link in fields.get("issuelinks", []) or []:
            type_name = (link.get("type") or {}).get("name", "")
            is_related = type_name.lower() == RELATED_ACCOUNT_LINK.lower()
            for side in ("outwardIssue", "inwardIssue"):
                other = link.get(side)
                if not other:
                    continue
                other_key = other.get("key", "")
                if not other_key.startswith(IF_PROJECT_KEY_PREFIX):
                    continue
                bucket = if_keys_related if is_related else if_keys_other
                if other_key not in bucket:
                    bucket.append(other_key)

        parent = fields.get("parent") or {}
        parent_key = parent.get("key", "") if isinstance(parent, dict) else ""

        return AppTicket(
            key=issue["key"],
            summary=(fields.get("summary") or "").strip(),
            bundle=f("Bundle"),
            appstore_account_name=f("AppStore Account Name"),
            appstore_app_id=f("Appstore App ID"),
            team_id=f("Team ID"),
            key_id=f("Key ID"),
            issuer_id=f("Issuer ID"),
            app_name_ios=f("App Name (IOS)"),
            privacy_policy=f("privacy policy"),
            octo_profile=f("Octo Profile"),
            codemagic_api_token=of("Codemagic API Token"),
            github_token=of("GitHub Token"),
            attachments=fields.get("attachment") or [],
            if_keys_related=if_keys_related,
            if_keys_other=if_keys_other,
            parent_key=parent_key,
        )

    def get_if_story(self, key: str) -> IfStory:
        email_id = self._field_id("mail for account")
        phone_id = self._field_id("Registration Number Phone")
        r = self.session.get(
            f"{self.cfg.jira_base_url}/rest/api/3/issue/{key}",
            params={"fields": f"{email_id},{phone_id}"},
        )
        _check(r, f"GET /issue/{key}")
        fields = r.json()["fields"]
        email = _extract_text(fields.get(email_id))
        phone = _extract_text(fields.get(phone_id))
        phone = re.sub(r"\s+", "", phone)
        if phone and not phone.startswith("+"):
            phone = "+" + phone
        return IfStory(key=key, email=email, phone=phone)

    def find_if_story_by_octo_profile(self, octo_profile: str):
        """Lenient fallback: search IF_PROJECT_NAME by summary for the given
        Octo Profile value. Returns the ticket key or None on any failure.
        """
        if not octo_profile:
            return None
        try:
            safe = octo_profile.replace('"', '\\"')
            payload = {
                "jql": f'project = "{IF_PROJECT_NAME}" AND summary ~ "{safe}"',
                "fields": ["summary"],
                "maxResults": 20,
            }
            r = self.session.post(
                f"{self.cfg.jira_base_url}/rest/api/3/search/jql", json=payload
            )
            if r.status_code >= 400:
                print(
                    f"  [warn] Octo Profile JQL search → HTTP {r.status_code}: "
                    f"{(r.text or '')[:200]}"
                )
                return None
            issues = r.json().get("issues", []) or []
            if not issues:
                print(
                    f"  [warn] No '{IF_PROJECT_NAME}' ticket matches summary "
                    f"{octo_profile!r}"
                )
                return None
            exact = [
                i for i in issues
                if (i["fields"].get("summary") or "").strip() == octo_profile
            ]
            if exact:
                if len(exact) > 1:
                    keys = [i["key"] for i in exact]
                    print(f"  [warn] Multiple exact matches {keys}; using first.")
                return exact[0]["key"]
            if len(issues) == 1:
                return issues[0]["key"]
            pairs = [(i["key"], i["fields"].get("summary")) for i in issues]
            print(
                f"  [warn] Multiple fuzzy matches for {octo_profile!r}, "
                f"no exact hit: {pairs}"
            )
            return None
        except Exception as e:
            print(f"  [warn] find_if_story_by_octo_profile({octo_profile!r}): {e}")
            return None

    def download_attachment(self, att: dict) -> bytes:
        url = att["content"]
        r = self.session.get(url)
        _check(r, f"download {att.get('filename')}")
        return r.content

    def fetch_design_zip(self, parent_key: str):
        """Lenient: return (des_key, zip_bytes, filename) or None on any failure.

        Walks parent's issue links for a DES-project ticket whose summary
        matches IOS_WHITE_DESIGN_SUMMARY, then downloads its first .zip
        attachment. Any HTTP or parsing error → None + warning.
        """
        if not parent_key:
            return None
        try:
            r = self.session.get(
                f"{self.cfg.jira_base_url}/rest/api/3/issue/{parent_key}",
                params={"fields": "issuelinks"},
            )
            if r.status_code >= 400:
                print(f"  [warn] GET issue {parent_key} → HTTP {r.status_code}")
                return None
            links = r.json().get("fields", {}).get("issuelinks", []) or []
            des_key = None
            wanted = IOS_WHITE_DESIGN_SUMMARY.lower()
            for link in links:
                for side in ("outwardIssue", "inwardIssue"):
                    other = link.get(side)
                    if not other:
                        continue
                    key = other.get("key", "")
                    if not key.startswith(DES_PROJECT_KEY_PREFIX):
                        continue
                    summary = (other.get("fields", {}) or {}).get("summary", "") or ""
                    if wanted in summary.lower():
                        des_key = key
                        break
                if des_key:
                    break
            if not des_key:
                print(
                    f"  [warn] No '{IOS_WHITE_DESIGN_SUMMARY}' "
                    f"{DES_PROJECT_KEY_PREFIX}* ticket linked from {parent_key}"
                )
                return None

            r2 = self.session.get(
                f"{self.cfg.jira_base_url}/rest/api/3/issue/{des_key}",
                params={"fields": "attachment"},
            )
            if r2.status_code >= 400:
                print(f"  [warn] GET issue {des_key} → HTTP {r2.status_code}")
                return None
            attachments = r2.json().get("fields", {}).get("attachment") or []
            zips = [a for a in attachments if (a.get("filename") or "").lower().endswith(".zip")]
            if not zips:
                print(f"  [warn] No .zip attachment on {des_key}")
                return None
            att = zips[0]
            if len(zips) > 1:
                names = [a.get("filename") for a in zips]
                print(f"  Multiple zips on {des_key} ({names}); using {att.get('filename')}")

            r3 = self.session.get(att["content"])
            if r3.status_code >= 400:
                print(f"  [warn] download {att.get('filename')} → HTTP {r3.status_code}")
                return None
            return des_key, r3.content, att.get("filename") or "design.zip"
        except Exception as e:
            print(f"  [warn] fetch_design_zip({parent_key}): {e}")
            return None
