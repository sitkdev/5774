import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

from . import (
    assets,
    babysit,
    config,
    github_remote,
    jira,
    metadata_check,
    metadata_gen,
    preflight,
    project_edits,
    telegraph,
    utils,
)
from .constants import (
    GSHEET_ID,
    METADATA_WORKFLOW_ID,
    RELEASE_BRANCH,
    RELEASE_WORKFLOW_ID,
)

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _cm_build_url(app_id: str, build_id: str) -> str:
    return f"https://codemagic.io/app/{app_id}/build/{build_id}"


def _cm_app_url(app_id: str) -> str:
    return f"https://codemagic.io/app/{app_id}"


def _sheet_tab_url(gid: int) -> str:
    return f"https://docs.google.com/spreadsheets/d/{GSHEET_ID}/edit#gid={gid}"


def _jira_issue_url(base: str, key: str) -> str:
    return f"{base.rstrip('/')}/browse/{key}"


def infer_ticket_number() -> str:
    name = PROJECT_ROOT.name
    m = re.match(r"^(\d+)", name)
    if m:
        return m.group(1)
    return utils.prompt(
        f"Could not infer ticket number from dir name '{name}'. Enter ticket number"
    )


def print_jira_block(ticket: jira.AppTicket, story: jira.IfStory):
    print()
    print("-" * 72)
    print(f"  Ticket key:          {ticket.key}")
    print(f"  Summary:             {ticket.summary}")
    print(f"  App Name (IOS):      {ticket.app_name_ios}")
    print(f"  Bundle:              {ticket.bundle}")
    print(f"  Team ID:             {ticket.team_id}")
    print(f"  AppStore Acc Name:   {ticket.appstore_account_name}")
    print(f"  Appstore App ID:     {ticket.appstore_app_id}")
    print(f"  Key ID:              {ticket.key_id}")
    print(f"  Issuer ID:           {ticket.issuer_id}")
    print(f"  Privacy Policy:      {ticket.privacy_policy}")
    print(f"  Octo Profile:        {ticket.octo_profile}")
    print(f"  Linked IF story:     {story.key}")
    print(f"  IF Email:            {story.email}")
    print(f"  IF Phone:            {story.phone}")
    print(f"  Attachments:         {[a.get('filename') for a in ticket.attachments]}")
    print("-" * 72)


def _pick_if_key(ticket: jira.AppTicket, jc: jira.JiraClient) -> str:
    if ticket.if_keys_related:
        if len(ticket.if_keys_related) > 1:
            print(
                f"  Multiple 'related account' IF links on {ticket.key}: "
                f"{ticket.if_keys_related} — using first."
            )
        return ticket.if_keys_related[0]
    others = ticket.if_keys_other
    if not others:
        if ticket.octo_profile:
            print(
                f"  No linked IF story on {ticket.key}; "
                f"trying 'Octo Profile' fallback: {ticket.octo_profile!r}"
            )
            found = jc.find_if_story_by_octo_profile(ticket.octo_profile)
            if found:
                print(f"  ✓ Resolved IF story via Octo Profile: {found}")
                return found
        utils.die(
            f"No linked IF story on {ticket.key} and no match for "
            f"'Octo Profile' ({ticket.octo_profile!r}) in '{jira.IF_PROJECT_NAME}'. "
            f"Link a story (via 'related account' or any link type) "
            f"or fix the Octo Profile value and re-run."
        )
    if len(others) == 1:
        print(
            f"  No 'related account' link on {ticket.key}; "
            f"using single linked IF story: {others[0]}"
        )
        return others[0]
    print(f"  No 'related account' link on {ticket.key}; multiple linked IF stories:")
    for i, k in enumerate(others):
        print(f"    [{i}] {k}")
    idx = int(utils.prompt("Select index", "0"))
    return others[idx]


def _split_name(full: str) -> tuple:
    parts = (full or "").strip().split(maxsplit=1)
    if len(parts) == 2:
        return parts[0], parts[1]
    return full or "", ""


def _git_steps(
    ticket_number: str,
    cache: config.Cache,
    ticket: jira.AppTicket,
    username_hint: Optional[str] = None,
    repo_hint: Optional[str] = None,
) -> str:
    if not utils.confirm(
        f"About to: create branch '{RELEASE_BRANCH}', add production remote, commit, push. Proceed?"
    ):
        utils.die("Aborted at git step")

    username = username_hint or cache.get("github.username") or utils.prompt("GitHub username (production account)")
    token = (
        ticket.github_token
        or cache.get("github.token")
        or utils.prompt("GitHub PAT for production account")
    )
    cache.set("github.username", username)
    cache.set("github.token", token)

    repo_name = repo_hint or github_remote.find_repo_name(username, token, ticket_number)
    github_remote.ensure_branch(RELEASE_BRANCH)
    public_url = github_remote.add_production_remote(username, token, repo_name)
    github_remote.commit_all_and_push(RELEASE_BRANCH)
    return public_url


def _set_cm_variables(
    cm,
    app_id: str,
    group: str,
    ticket: jira.AppTicket,
    story: jira.IfStory,
    p8_contents: str,
):
    vars_ = [
        ("APP_STORE_CONNECT_PRIVATE_KEY", p8_contents, True),
        ("DEVELOPMENT_TEAM", ticket.team_id, False),
        ("GSHEET_TAB", group, False),
        ("GSHEET_ID", GSHEET_ID, False),
        ("CM_APP_STORE_APPLE_ID", ticket.appstore_app_id, False),
        ("APP_STORE_CONNECT_KEY_IDENTIFIER", ticket.key_id, False),
        ("APP_STORE_CONNECT_ISSUER_ID", ticket.issuer_id, False),
        ("APP_IDENTIFIER", ticket.bundle, False),
        ("APPLE_ID", story.email, False),
    ]
    for key, value, secure in vars_:
        cm.upsert_variable(app_id, key, value, group, secure)
        print(f"  ✓ {key}" + (" (secret)" if secure else ""))


MANUAL_STEPS = """
14. Set price to $0 (Free) and availability to all territories
    (+ "Make available in new territories") on App Store Connect.

15. Submit app to review on App Store Connect.
    (copyright, age rating, categories, and auto-release are already
    configured by this script.)
"""


def _set_cm_metadata_vars(
    cm,
    app_id: str,
    group: str,
    copyright_value: str,
    primary_category: str,
    secondary_category: str,
):
    vars_ = [
        ("APP_COPYRIGHT", copyright_value, False),
        ("APP_PRIMARY_CATEGORY", primary_category, False),
        ("APP_SECONDARY_CATEGORY", secondary_category, False),
    ]
    for key, value, secure in vars_:
        cm.upsert_variable(app_id, key, value, group, secure)
        print(f"  ✓ {key} = {value!r}")


def run():
    cfg = config.GlobalConfig.load()
    cache = config.Cache()
    summary: dict = {}

    # ── Step 0: gather ────────────────────────────────────────────
    utils.section("STEP 0: Gather Jira data")
    ticket_number = infer_ticket_number()
    print(f"Ticket number: {ticket_number}")
    jc = jira.JiraClient(cfg)
    ticket = jc.find_ticket_by_number(ticket_number)
    if_key = _pick_if_key(ticket, jc)
    story = jc.get_if_story(if_key)
    print_jira_block(ticket, story)
    summary["jira_ticket"] = (ticket.key, _jira_issue_url(cfg.jira_browse_base_url, ticket.key))
    summary["if_story"] = (story.key, _jira_issue_url(cfg.jira_browse_base_url, story.key))

    if not utils.confirm("Data looks correct — continue?", default=True):
        sys.exit(0)

    # ── Preflight: verify external services fail-fast ────────────
    pre = preflight.run(cfg, cache, jc, ticket, ticket_number)
    summary["codemagic_app"] = _cm_app_url(pre.cm_app_id)

    # ── Steps 1-4, 6: file edits ─────────────────────────────────
    utils.section("STEPS 1-4, 6: Edit project files")
    project_edits.set_bundle_and_team(ticket.bundle, ticket.team_id)
    print(f"  ✓ bundle={ticket.bundle}, team={ticket.team_id}")
    project_edits.set_codemagic_group(ticket_number)
    print(f"  ✓ codemagic.yaml group → {ticket_number}")
    first, last = _split_name(ticket.appstore_account_name)
    project_edits.set_fastfile_review_info(first, last, story.email, story.phone)
    print(f"  ✓ Fastfile app_review_information ({first} {last}, {story.email}, {story.phone})")
    project_edits.set_gradle_properties()
    print("  ✓ gradle.properties #Gradle section")

    # ── Step 5: screenshots ───────────────────────────────────────
    utils.section("STEP 5: Screenshots (iosApp/fastlane/white)")
    assets.check_screenshots(jc, ticket)

    # ── Step 7: populate locales ─────────────────────────────────
    utils.section("STEP 7: Populate locales")
    populate_cmd = [sys.executable, str(PROJECT_ROOT / "iosApp" / "fastlane" / "populate_locales_white.py")]
    if utils.is_assume_yes():
        populate_cmd.append("--yes")
    subprocess.run(populate_cmd, check=True, cwd=PROJECT_ROOT)

    # ── Steps 8-9: git ────────────────────────────────────────────
    utils.section("STEPS 8-9: Git remote + commit + push")
    summary["github_repo"] = _git_steps(ticket_number, cache, ticket, pre.gh_username, pre.gh_repo)

    # ── Step 10: Codemagic env vars ──────────────────────────────
    utils.section("STEP 10: Codemagic env vars")
    _set_cm_variables(pre.cm, pre.cm_app_id, ticket_number, ticket, story, pre.p8_contents)

    # ── Step 11: trigger iOS release ─────────────────────────────
    utils.section("STEP 11: Trigger iOS release workflow")
    if utils.confirm(
        f"Trigger Codemagic '{RELEASE_WORKFLOW_ID}' build on {RELEASE_BRANCH} branch?",
        default=True,
    ):
        build_id = pre.cm.trigger_build(pre.cm_app_id, RELEASE_WORKFLOW_ID, RELEASE_BRANCH)
        url = _cm_build_url(pre.cm_app_id, build_id)
        print(f"  ✓ Triggered: {url}")
        summary["release_build"] = url

    # ── Step 12: Sheets metadata ─────────────────────────────────
    utils.section("STEP 12: Sheets + Telegraph metadata")
    print("  Generating App Store metadata with Claude Agent SDK...")
    meta = metadata_gen.generate(ticket.app_name_ios)
    print(f"    subtitle:    {meta.get('subtitle')}")
    desc_preview = (meta.get("description") or "")[:120].replace("\n", " ")
    print(f"    description: {desc_preview}...")
    print(f"    keywords:    {meta.get('keywords')}")

    print("  Determining App Store categories with Claude Agent SDK...")
    cats = metadata_gen.determine_categories(ticket.app_name_ios)
    print(f"    primary:     {cats['primary']}")
    print(f"    secondary:   {cats['secondary'] or '(none)'}")
    summary["categories"] = (
        f"{cats['primary']}"
        + (f" / {cats['secondary']}" if cats['secondary'] else "")
    )

    print("  Setting Codemagic metadata env vars (copyright, categories)...")
    _set_cm_metadata_vars(
        pre.cm,
        pre.cm_app_id,
        ticket_number,
        copyright_value=ticket.appstore_account_name,
        primary_category=cats["primary"],
        secondary_category=cats["secondary"],
    )

    new_ws = pre.sheets_client.duplicate_template(ticket_number)
    summary["sheet_tab"] = _sheet_tab_url(new_ws.id)
    print(f"  ✓ Duplicated 'Template 2' → '{ticket_number}'")

    tg_token = cache.get("telegraph.accessToken")
    if not tg_token:
        tg_token = telegraph.create_account(
            short_name=ticket.app_name_ios or "App",
            author_name=ticket.appstore_account_name or "Author",
        )
        cache.set("telegraph.accessToken", tg_token)
    support_url = telegraph.create_support_page(
        access_token=tg_token,
        app_name=ticket.app_name_ios,
        author_name=ticket.appstore_account_name or "",
        email=story.email,
    )
    print(f"  ✓ Telegraph support URL: {support_url}")
    summary["telegraph"] = support_url

    pre.sheets_client.fill_metadata(
        tab=ticket_number,
        app_name=ticket.app_name_ios,
        subtitle=meta.get("subtitle", ""),
        description=meta.get("description", ""),
        keywords=meta.get("keywords", ""),
        privacy_url=ticket.privacy_policy,
        support_url=support_url,
    )

    # Validate + fix per-locale metadata against App Store character limits
    metadata_check.check_and_fix(
        pre.sheets_client.sh.worksheet(ticket_number),
        en_row={
            "name": ticket.app_name_ios,
            "subtitle": meta.get("subtitle", ""),
            "description": meta.get("description", ""),
            "keywords": meta.get("keywords", ""),
        },
    )

    # ── Step 13: metadata workflow ───────────────────────────────
    utils.section("STEP 13: Trigger metadata workflow")
    metadata_build_id = None
    if utils.confirm(f"Trigger Codemagic '{METADATA_WORKFLOW_ID}' build?", default=True):
        metadata_build_id = pre.cm.trigger_build(pre.cm_app_id, METADATA_WORKFLOW_ID, RELEASE_BRANCH)
        url = _cm_build_url(pre.cm_app_id, metadata_build_id)
        print(f"  ✓ Triggered: {url}")
        summary["metadata_build"] = url

    # ── Manual checklist ─────────────────────────────────────────
    utils.section("MANUAL STEPS REMAINING")
    print(MANUAL_STEPS)

    _print_summary(summary)
    _logout_github(cache)

    if metadata_build_id:
        babysit.run(pre.cm, pre.cm_app_id, metadata_build_id)


def _logout_github(cache: config.Cache):
    username = cache.get("github.username")
    if not username:
        return
    print()
    print(f"Logging out Git Credential Manager for production account '{username}'...")
    result = subprocess.run(
        ["git", "credential-manager", "github", "logout", username],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        print(f"  ✓ Logged out {username}")
    else:
        err = (result.stderr or result.stdout or "").strip()
        print(f"  [warn] logout exited {result.returncode}: {err}")


def _print_summary(summary: dict):
    utils.section("RUN SUMMARY")
    def row(label, value):
        if value is None:
            return
        if isinstance(value, tuple):
            key, url = value
            print(f"  {label:<22} {key}  {url}")
        else:
            print(f"  {label:<22} {value}")
    row("Jira ticket:", summary.get("jira_ticket"))
    row("IF story:", summary.get("if_story"))
    row("GitHub (production):", summary.get("github_repo"))
    row("Codemagic app:", summary.get("codemagic_app"))
    row("  iOS release build:", summary.get("release_build"))
    row("  iOS metadata build:", summary.get("metadata_build"))
    row("Sheet tab:", summary.get("sheet_tab"))
    row("Telegraph support:", summary.get("telegraph"))
    row("Category:", summary.get("categories"))
    print()
