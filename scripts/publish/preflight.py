from dataclasses import dataclass
from typing import Optional

import requests

from . import codemagic, jira, metadata_gen, sheets, utils
from .config import Cache, GlobalConfig
from .constants import SHEET_TEMPLATE_TAB


@dataclass
class Preflight:
    cm: codemagic.CodemagicClient
    cm_app: dict
    cm_app_id: str
    gh_username: Optional[str]
    gh_repo: Optional[str]
    sheets_client: sheets.SheetsClient
    p8_contents: str


def run(
    cfg: GlobalConfig,
    cache: Cache,
    jc: jira.JiraClient,
    ticket: jira.AppTicket,
    ticket_number: str,
) -> Preflight:
    utils.section("PREFLIGHT: verify external services")

    print("\n→ Jira .p8 attachment...")
    p8_contents = _download_p8(jc, ticket)

    print("\n→ Codemagic...")
    cm, cm_app = _codemagic(cache, ticket_number, ticket)
    gh_username, gh_repo = _derive_github_repo(cm_app)

    print("\n→ GitHub...")
    gh_username = _github(cache, gh_username, gh_repo, ticket)

    print("\n→ Google Sheets...")
    sh = _google_sheets(cfg)

    print("\n→ Claude Agent SDK...")
    _claude_sdk()

    return Preflight(
        cm=cm,
        cm_app=cm_app,
        cm_app_id=cm_app["_id"],
        gh_username=gh_username,
        gh_repo=gh_repo,
        sheets_client=sh,
        p8_contents=p8_contents,
    )


def _download_p8(jc: jira.JiraClient, ticket: jira.AppTicket) -> str:
    p8s = [a for a in ticket.attachments if a.get("filename", "").lower().endswith(".p8")]
    if not p8s:
        utils.die("No .p8 attachment on the Jira ticket")
    expected = [f"{ticket.key_id}.p8", f"AuthKey_{ticket.key_id}.p8"]
    match = next((a for a in p8s if a.get("filename") in expected), None)
    if not match:
        if len(p8s) == 1:
            match = p8s[0]
            print(
                f"  One .p8 found ({match['filename']}); using it despite "
                f"no match to Key ID '{ticket.key_id}'"
            )
        else:
            print(f"  Multiple .p8s; none match {expected}:")
            for i, a in enumerate(p8s):
                print(f"    [{i}] {a.get('filename')}")
            idx = int(utils.prompt("Select index", "0"))
            match = p8s[idx]
    print(f"  Downloading {match['filename']}...")
    contents = jc.download_attachment(match).decode("utf-8")
    print(f"  ✓ got {len(contents)} bytes")
    return contents


def _codemagic(cache: Cache, ticket_number: str, ticket: jira.AppTicket):
    token = (
        ticket.codemagic_api_token
        or cache.get("codemagic.apiToken")
        or utils.prompt("Codemagic API token")
    )
    if ticket.codemagic_api_token:
        print("  Using Codemagic API token from Jira ticket")
    cache.set("codemagic.apiToken", token)
    cm = codemagic.CodemagicClient(token)
    candidates = [ticket_number]
    try:
        candidates.append(str(int(ticket_number) - 1))
    except ValueError:
        pass
    app = cm.find_app_by_names(candidates)
    if not app:
        name = utils.prompt("Codemagic app name (manual)")
        app = cm.find_app_by_names([name])
        if not app:
            utils.die(f"Codemagic app '{name}' not found")
    print(f"  ✓ app: {app.get('appName')} (id={app.get('_id')})")
    return cm, app


def _derive_github_repo(cm_app: dict):
    info = codemagic.extract_github_repo(cm_app)
    if info:
        user, repo = info
        print(f"  ✓ GitHub repo derived: {user}/{repo}")
        return user, repo
    print("  [warn] GitHub repo not derivable from Codemagic; will prompt at git step.")
    return None, None


def _github(cache: Cache, derived_username, derived_repo, ticket: jira.AppTicket):
    username = (
        derived_username
        or cache.get("github.username")
        or utils.prompt("GitHub username (production account)")
    )
    token = (
        ticket.github_token
        or cache.get("github.token")
        or utils.prompt("GitHub PAT for production account")
    )
    if ticket.github_token:
        print("  Using GitHub token from Jira ticket")
    cache.set("github.username", username)
    cache.set("github.token", token)
    try:
        r = requests.get(
            "https://api.github.com/user", auth=(username, token), timeout=15
        )
    except requests.RequestException as e:
        utils.die(f"GitHub auth probe failed: {e}")
    if r.status_code >= 400:
        utils.die(
            f"GitHub auth probe failed: HTTP {r.status_code} for user "
            f"'{username}'. Check PAT scopes and re-run."
        )
    login = (r.json() or {}).get("login", "") or username
    print(f"  ✓ authenticated as {login}")
    if login.lower() != username.lower():
        print(f"  [warn] token resolves to '{login}' but you entered '{username}'.")
    if derived_repo:
        r2 = requests.get(
            f"https://api.github.com/repos/{username}/{derived_repo}",
            auth=(username, token),
            timeout=15,
        )
        if r2.status_code >= 400:
            print(
                f"  [warn] repo {username}/{derived_repo} → HTTP {r2.status_code}; "
                f"will fall back to name search at git step."
            )
        else:
            print(f"  ✓ repo visible: {username}/{derived_repo}")
    return username


def _google_sheets(cfg: GlobalConfig) -> sheets.SheetsClient:
    try:
        sh = sheets.SheetsClient(cfg.sheets_credentials_path)
        sh.sh.worksheet(SHEET_TEMPLATE_TAB)
    except Exception as e:
        utils.die(
            f"Google Sheets preflight failed: {e}\n"
            f"  - Check that Sheets + Drive APIs are enabled for the service account project.\n"
            f"  - Check that '{cfg.sheets_credentials_path}' exists and is valid.\n"
            f"  - Check that the service account has access to the spreadsheet."
        )
    print(f"  ✓ opened spreadsheet, template tab '{SHEET_TEMPLATE_TAB}' readable")
    return sh


def _claude_sdk():
    try:
        metadata_gen.probe()
    except SystemExit:
        raise
    except Exception as e:
        utils.die(
            f"Claude Agent SDK preflight failed: {e}\n"
            f"  - Check auth (claude-agent-sdk is logged in)\n"
            f"  - Check session/quota limits on your Claude account"
        )
    print("  ✓ Claude Agent SDK responded")
