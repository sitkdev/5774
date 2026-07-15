import base64
import re
from dataclasses import dataclass, field

import requests

try:
    import phonenumbers
except ImportError:  # optional — account-phone country-code completion degrades gracefully
    phonenumbers = None

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
    # Backend domain for the App Store Connect status webhook (bare host, e.g.
    # "domain.com"). Optional: tickets without it just skip webhook setup.
    "Klo link",
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
    domain: str = ""
    attachments: list = field(default_factory=list)
    if_keys_related: list = field(default_factory=list)
    parent_key: str = ""
    # Parent (Main) fallbacks for IF-story resolution (see _pick_if_key).
    parent_octo_profile: str = ""
    parent_if_keys_related: list = field(default_factory=list)


@dataclass
class IfStory:
    key: str
    email: str
    phone: str
    password: str = ""
    twofa_number: str = ""  # "2FA Number" — trusted phone Apple texts the 2FA code to
    twofa_link: str = ""     # "2fa Link" — SMS-retrieval gateway URL to poll for the code


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


def _clean_phone(p) -> str:
    p = re.sub(r"\s+", "", (p or "").strip())
    # Defensive: a Jira "Number"-type field arrives as a float ("380991234567.0").
    # "2FA Number" is a Short Text field now, but keep this in case a phone field
    # is (re)typed as Number again — harmless on text values.
    return re.sub(r"\.0+$", "", p)


def _norm_phone(p) -> str:
    """Light normalization: clean + ensure a leading '+'. Assumes the country code
    is already present (used for the 2fa Number, which must be explicit)."""
    p = _clean_phone(p)
    return ("+" + p) if p and not p.startswith("+") else p


def _geo_country_code(v) -> str:
    """2-letter ISO code from the 'GEO Farm' cascading-select field (bare code like
    'UA'/'IT' — at the parent level, with the child level as a fallback)."""
    if not isinstance(v, dict):
        return ""
    for candidate in (v.get("value"), (v.get("child") or {}).get("value")):
        c = (candidate or "").strip().upper()
        if re.fullmatch(r"[A-Z]{2}", c):
            return c
    return ""


def _complete_account_phone(raw, region: str) -> str:
    """Account phone → E.164. Account guys sometimes omit the country code, so when a
    2-letter region (from 'GEO Farm') is known we parse the national number with
    `phonenumbers`, which knows each country's dial code / trunk-prefix / length —
    heuristics can't (e.g. Italian mobiles start with '39', Italy's own dial code).
    Falls back to clean + prepend '+' if the lib/region is missing or parsing fails."""
    p = _clean_phone(raw)
    if not p:
        return ""
    if phonenumbers and region:
        try:
            parsed = phonenumbers.parse(p, region)
            if phonenumbers.is_valid_number(parsed):
                return phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)
            print(f"  [warn] phone {p!r} not valid for region {region!r}; leaving as-is")
        except Exception as e:
            print(f"  [warn] phone parse failed ({p!r}, region {region!r}): {e}")
    elif region and not phonenumbers:
        print("  [warn] 'phonenumbers' not installed — cannot complete country code from GEO Farm")
    return p if p.startswith("+") else "+" + p


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

        return self._build_app_ticket(matches[0], optional_field_ids)

    def find_ticket_by_key(self, key: str) -> AppTicket:
        """Fetch the iOS app ticket directly by its Jira key.

        Used when the trigger (Jira Automation -> middleware) already tells us
        exactly which issue this run is for, so we never infer a number from the
        repo/dir name — the repo's APP_CODE is not the iOS ticket's summary.
        """
        self._resolve_fields()
        field_ids = [self._field_id(n) for n in APP_TICKET_FIELDS]
        optional_field_ids = {
            n: self._optional_field_id(n) for n in OPTIONAL_APP_TICKET_FIELDS
        }
        field_ids.extend(fid for fid in optional_field_ids.values() if fid)
        requested = ["summary", "issuetype", "issuelinks", "attachment", "parent", *field_ids]
        r = self.session.get(
            f"{self.cfg.jira_base_url}/rest/api/3/issue/{key}",
            params={"fields": ",".join(requested)},
        )
        _check(r, f"GET /issue/{key}")
        issue = r.json()
        itype = ((issue.get("fields") or {}).get("issuetype") or {}).get("name", "") or ""
        if itype.lower() != IOS_ISSUE_TYPE.lower():
            print(
                f"  [warn] {key} is issue type '{itype}', expected "
                f"'{IOS_ISSUE_TYPE}'; using it anyway."
            )
        return self._build_app_ticket(issue, optional_field_ids)

    def _extract_related_if_keys(self, issuelinks) -> list:
        """IF-* keys linked via the 'related account' link type.

        Jira's link *type* here is named "Account"; "related account" is its
        *inward* description ("related apps" is the outward one). The app ticket
        sees the account on the inward side, so we match the phrase against the
        type's name AND its inward/outward labels — matching name alone (the old
        behaviour) never hit, since the name is "Account", and silently returned
        no linked account.
        """
        want = RELATED_ACCOUNT_LINK.lower()
        keys = []
        for link in issuelinks or []:
            t = link.get("type") or {}
            labels = {
                (t.get("name") or "").strip().lower(),
                (t.get("inward") or "").strip().lower(),
                (t.get("outward") or "").strip().lower(),
            }
            if want not in labels:
                continue
            for side in ("outwardIssue", "inwardIssue"):
                other = link.get(side)
                if not other:
                    continue
                k = other.get("key", "")
                if k.startswith(IF_PROJECT_KEY_PREFIX) and k not in keys:
                    keys.append(k)
        return keys

    def _fetch_parent_fields(self, parent_key: str, optional_field_ids: dict) -> dict:
        """Parent (Main) ticket's custom fields + issuelinks, for fallbacks.
        Lenient: any HTTP error → {} (fallbacks just yield empty)."""
        field_ids = [self._field_id(n) for n in APP_TICKET_FIELDS]
        field_ids.extend(fid for fid in optional_field_ids.values() if fid)
        requested = ["issuelinks", *field_ids]
        r = self.session.get(
            f"{self.cfg.jira_base_url}/rest/api/3/issue/{parent_key}",
            params={"fields": ",".join(requested)},
        )
        if r.status_code >= 400:
            print(f"  [warn] GET parent {parent_key} for fallback → HTTP {r.status_code}")
            return {}
        return r.json().get("fields", {}) or {}

    def _build_app_ticket(self, issue: dict, optional_field_ids: dict) -> AppTicket:
        fields = issue["fields"]
        parent = fields.get("parent") or {}
        parent_key = parent.get("key", "") if isinstance(parent, dict) else ""

        # Parent (Main) fields are fetched lazily and once: empty child fields fall
        # back to the parent, and IF-story resolution uses the parent's 'related
        # account' link + Octo Profile. Octo Profile is deliberately NOT part of the
        # field fallback — resolution keeps the child's and parent's values distinct.
        parent_cache = {}

        def parent_fields():
            if not parent_key:
                return {}
            if "f" not in parent_cache:
                parent_cache["f"] = self._fetch_parent_fields(parent_key, optional_field_ids)
            return parent_cache["f"]

        def f(name):  # required field: child, then parent fallback if empty
            fid = self._field_id(name)
            val = _extract_text(fields.get(fid))
            if not val and parent_key:
                val = _extract_text(parent_fields().get(fid))
            return val

        def of(name):  # optional field: child, then parent fallback if empty
            fid = optional_field_ids.get(name)
            if not fid:
                return ""
            val = _extract_text(fields.get(fid))
            if not val and parent_key:
                val = _extract_text(parent_fields().get(fid))
            return val

        # Octo Profile is child-only here; the parent's is captured separately below.
        child_octo = _extract_text(fields.get(self._field_id("Octo Profile")))
        if_keys_related = self._extract_related_if_keys(fields.get("issuelinks"))

        parent_if_keys_related = []
        parent_octo = ""
        if parent_key:
            pf = parent_fields()
            parent_if_keys_related = self._extract_related_if_keys(pf.get("issuelinks"))
            parent_octo = _extract_text(pf.get(self._field_id("Octo Profile")))

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
            octo_profile=child_octo,
            codemagic_api_token=of("Codemagic API Token"),
            github_token=of("GitHub Token"),
            domain=of("Klo link"),
            attachments=fields.get("attachment") or [],
            if_keys_related=if_keys_related,
            parent_key=parent_key,
            parent_octo_profile=parent_octo,
            parent_if_keys_related=parent_if_keys_related,
        )

    def get_if_story(self, key: str) -> IfStory:
        email_id = self._field_id("mail for account")
        phone_id = self._field_id("Registration Number Phone")
        main_phone_id = self._optional_field_id("Main Phone Number")
        password_id = self._optional_field_id("password for account")
        twofa_number_id = self._optional_field_id("2FA Number")
        twofa_link_id = self._optional_field_id("2fa Link")
        geo_id = self._optional_field_id("GEO Farm")
        requested = [email_id, phone_id]
        for fid in (main_phone_id, password_id, twofa_number_id, twofa_link_id, geo_id):
            if fid:
                requested.append(fid)
        r = self.session.get(
            f"{self.cfg.jira_base_url}/rest/api/3/issue/{key}",
            params={"fields": ",".join(requested)},
        )
        _check(r, f"GET /issue/{key}")
        fields = r.json()["fields"]
        email = _extract_text(fields.get(email_id))
        phone = _extract_text(fields.get(phone_id))
        password = _extract_text(fields.get(password_id)) if password_id else ""
        twofa_number = _extract_text(fields.get(twofa_number_id)) if twofa_number_id else ""
        twofa_link = _extract_text(fields.get(twofa_link_id)) if twofa_link_id else ""
        if not phone and main_phone_id:
            main_phone = _extract_text(fields.get(main_phone_id))
            if main_phone:
                print(
                    f"  'Registration Number Phone' empty on {key}; "
                    f"falling back to 'Main Phone Number'."
                )
                phone = main_phone

        # Account phone: complete a missing country code using GEO Farm's region.
        geo_country = _geo_country_code(fields.get(geo_id)) if geo_id else ""
        phone = _complete_account_phone(phone, geo_country)
        # 2fa Number must be explicit (no GEO completion) — just clean + ensure '+'.
        twofa_number = _norm_phone(twofa_number)
        return IfStory(
            key=key, email=email, phone=phone, password=password,
            twofa_number=twofa_number, twofa_link=twofa_link,
        )

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

    def _get_attachments(self, key: str) -> list:
        r = self.session.get(
            f"{self.cfg.jira_base_url}/rest/api/3/issue/{key}",
            params={"fields": "attachment"},
        )
        if r.status_code >= 400:
            print(f"  [warn] GET attachments for {key} → HTTP {r.status_code}")
            return []
        return r.json().get("fields", {}).get("attachment") or []

    @staticmethod
    def _pick_screenshot_zip(attachments: list):
        """First .zip whose filename contains both 'ios' and 'screen'."""
        for a in attachments or []:
            name = (a.get("filename") or "").lower()
            if name.endswith(".zip") and "ios" in name and "screen" in name:
                return a
        return None

    def fetch_screenshot_zip(self, ticket):
        """Lenient fallback: find a screenshots .zip whose name contains both
        'ios' and 'screen', attached either to the ticket itself or its parent.
        Returns (src_key, zip_bytes, filename) or None on any failure.
        """
        try:
            sources = [(ticket.key, ticket.attachments or [])]
            parent_key = getattr(ticket, "parent_key", "") or ""
            if parent_key:
                sources.append((parent_key, self._get_attachments(parent_key)))
            for src_key, attachments in sources:
                att = self._pick_screenshot_zip(attachments)
                if not att:
                    continue
                r = self.session.get(att["content"])
                if r.status_code >= 400:
                    print(
                        f"  [warn] download {att.get('filename')} → "
                        f"HTTP {r.status_code}"
                    )
                    continue
                return src_key, r.content, att.get("filename") or "ios_screenshots.zip"
            scope = ticket.key + (f" or parent {parent_key}" if parent_key else "")
            print(f"  [warn] No 'ios'+'screen' .zip attachment on {scope}")
            return None
        except Exception as e:
            print(f"  [warn] fetch_screenshot_zip({ticket.key}): {e}")
            return None

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
