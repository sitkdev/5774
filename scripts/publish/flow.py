import os
import re
import secrets
import subprocess
import sys
from pathlib import Path
from typing import Optional

from . import (
    assets,
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
    SUBMIT_WORKFLOW_ID,
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
    print(f"  Klo link (webhook):  {ticket.domain or '(none — webhook skipped)'}")
    print(f"  Octo Profile:        {ticket.octo_profile}")
    print(f"  Linked IF story:     {story.key}")
    print(f"  IF Email:            {story.email}")
    print(f"  IF Phone:            {story.phone}")
    print(f"  IF 2FA Number:       {story.twofa_number or '(none — 2FA will fail!)'}")
    print(f"  IF 2FA Link:         {'set' if story.twofa_link else '(none — 2FA will fail!)'}")
    print(f"  Attachments:         {[a.get('filename') for a in ticket.attachments]}")
    print("-" * 72)


def _pick_if_key(ticket: jira.AppTicket, jc: jira.JiraClient) -> str:
    """Resolve the IF account story, in order:
      1. 'related account' link on the child (IOS) ticket
      2. 'related account' link on the parent (Main) ticket
      3. Octo Profile search using the child's value
      4. Octo Profile search using the parent's value
    """
    # 1. child 'related account' link
    if ticket.if_keys_related:
        if len(ticket.if_keys_related) > 1:
            print(
                f"  Multiple 'related account' IF links on {ticket.key}: "
                f"{ticket.if_keys_related} — using first."
            )
        return ticket.if_keys_related[0]

    # 2. parent 'related account' link
    if ticket.parent_if_keys_related:
        if len(ticket.parent_if_keys_related) > 1:
            print(
                f"  Multiple 'related account' IF links on parent {ticket.parent_key}: "
                f"{ticket.parent_if_keys_related} — using first."
            )
        print(
            f"  No 'related account' link on {ticket.key}; using parent "
            f"{ticket.parent_key}'s link: {ticket.parent_if_keys_related[0]}"
        )
        return ticket.parent_if_keys_related[0]

    # 3. child Octo Profile search
    if ticket.octo_profile:
        print(f"  No linked IF story; trying child Octo Profile: {ticket.octo_profile!r}")
        found = jc.find_if_story_by_octo_profile(ticket.octo_profile)
        if found:
            print(f"  ✓ Resolved IF story via child Octo Profile: {found}")
            return found

    # 4. parent Octo Profile search
    if ticket.parent_octo_profile:
        print(f"  Trying parent Octo Profile: {ticket.parent_octo_profile!r}")
        found = jc.find_if_story_by_octo_profile(ticket.parent_octo_profile)
        if found:
            print(f"  ✓ Resolved IF story via parent Octo Profile: {found}")
            return found

    utils.die(
        f"No IF story for {ticket.key}: no 'related account' link on the ticket or its "
        f"parent ({ticket.parent_key or 'none'}), and no Octo Profile match "
        f"(child {ticket.octo_profile!r}, parent {ticket.parent_octo_profile!r}) in "
        f"'{jira.IF_PROJECT_NAME}'. Add a 'related account' link or fix the Octo Profile."
    )


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
        # Web-session auth for the `web_session_declarations` lane (App Privacy +
        # DSA have no .p8 API — see Fastfile). spaceship logs in with these and
        # auto-handles SMS 2FA via the gateway below. Creds from the IF account story.
        ("FASTLANE_USER", story.email, False),
        ("FASTLANE_PASSWORD", story.password, True),
    ]
    for key, value, secure in vars_:
        cm.upsert_variable(app_id, key, value, group, secure)
        print(f"  ✓ {key}" + (" (secret)" if secure else ""))

    # Apple SMS 2FA — both come straight from the IF story with NO fallback: an
    # empty value fails the web_session_declarations lane loudly rather than
    # mis-targeting another account's number/inbox.
    #   "2FA Number" → the trusted phone spaceship forces the SMS to (must be exact)
    #   "2fa Link"   → the gateway the Fastfile polls for the code
    if story.twofa_number:
        cm.upsert_variable(app_id, "SPACESHIP_2FA_SMS_DEFAULT_PHONE_NUMBER", story.twofa_number, group, False)
        print(f"  ✓ SPACESHIP_2FA_SMS_DEFAULT_PHONE_NUMBER = {story.twofa_number!r}")
    else:
        print("  • No '2fa Number' on the IF story — 2FA will fail until it's filled.")
    if story.twofa_link:
        cm.upsert_variable(app_id, "SMS_2FA_GATEWAY_URL", story.twofa_link, group, True)
        print("  ✓ SMS_2FA_GATEWAY_URL (secret, from IF '2fa Link')")
    else:
        print("  • No '2fa Link' on the IF story — 2FA will fail until it's filled.")

    # App Store Connect status webhook. The `set_app_compliance` lane reads
    # ASC_WEBHOOK_URL and, when present, registers a per-app webhook that pushes
    # review-status (and other) events to our backend instead of us watching by
    # hand. URL = "https://api." + <bare domain from the "Klo link" field> +
    # "/<random>". The backend validates by payload shape, so the random suffix
    # and the signing secret are free-form. No "Klo link" on the ticket → no URL
    # → the lane skips webhook creation.
    if ticket.domain:
        host = re.sub(r"^https?://", "", ticket.domain.strip()).strip("/").lstrip(".")
        webhook_url = f"https://api.{host}/{secrets.token_urlsafe(16)}"
        cm.upsert_variable(app_id, "ASC_WEBHOOK_URL", webhook_url, group, False)
        cm.upsert_variable(app_id, "ASC_WEBHOOK_SECRET", secrets.token_hex(32), group, True)
        print(f"  ✓ ASC_WEBHOOK_URL = {webhook_url!r}")
        print("  ✓ ASC_WEBHOOK_SECRET (secret)")
    else:
        print("  • No 'Klo link' on ticket — skipping ASC webhook env vars")


MANUAL_STEPS = """
The App Store Connect steps are fully automated on Codemagic via
fastlane/spaceship (the orchestrator never touches Apple directly):
  * primary locale (en-GB) + removal of all other locales, content rights, Free
    pricing, all-territory availability (+ new), and a status webhook (when the
    ticket has a 'Klo link') — the '%s' workflow runs the `set_app_compliance`
    lane (.p8 API) after the metadata upload
  * App Privacy ('Data Not Collected', published), DSA trader status ('not a
    trader', account-level), and the regulated-medical-device form ('No',
    whenever the question applies) — the same workflow runs the
    `web_session_declarations` lane over a web session with automated SMS 2FA,
    since Apple exposes none of these via the .p8 API
  * build attach + submit for review (3-step) — the '%s' workflow runs the
    `submit_for_review` lane (it waits for Apple to finish processing the build)
  * auto-release after approval — already enabled in fastlane `deliver`

Remaining: watch the '%s' build. App Review status is pushed to the backend via
the ASC webhook (when the ticket has a 'Klo link'); otherwise watch it in ASC.
""" % (METADATA_WORKFLOW_ID, SUBMIT_WORKFLOW_ID, SUBMIT_WORKFLOW_ID)


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
    jc = jira.JiraClient(cfg)
    # ticket_number is the app/repo code (e.g. "5730") — the Codemagic env group
    # and Google Sheet tab name — and always comes from the repo/dir name.
    # JIRA_ISSUE_KEY (from the Jira Automation -> middleware) only changes WHICH
    # issue we read fields from: the iOS ticket's summary is NOT the repo code
    # (here KA-684 summary "5731" vs repo "5730"), so we fetch it directly by key
    # rather than searching by number. Number-inference fetch remains for local runs.
    ticket_number = infer_ticket_number()
    issue_key = os.environ.get("JIRA_ISSUE_KEY", "").strip()
    if issue_key:
        print(f"Using Jira issue from trigger: {issue_key} (app code {ticket_number})")
        ticket = jc.find_ticket_by_key(issue_key)
    else:
        print(f"Ticket number: {ticket_number}")
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
    project_edits.set_info_plist_encryption()
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
    print("  Generating App Store metadata + categories with Claude Agent SDK...")
    meta = metadata_gen.generate(ticket.app_name_ios)
    print(f"    subtitle:    {meta.get('subtitle')}")
    desc_preview = (meta.get("description") or "")[:120].replace("\n", " ")
    print(f"    description: {desc_preview}...")
    print(f"    keywords:    {meta.get('keywords')}")
    print(f"    primary:     {meta['primary']}")
    print(f"    secondary:   {meta['secondary'] or '(none)'}")
    summary["categories"] = (
        f"{meta['primary']}"
        + (f" / {meta['secondary']}" if meta['secondary'] else "")
    )

    print("  Setting Codemagic metadata env vars (copyright, categories)...")
    _set_cm_metadata_vars(
        pre.cm,
        pre.cm_app_id,
        ticket_number,
        copyright_value=ticket.appstore_account_name,
        primary_category=meta["primary"],
        secondary_category=meta["secondary"],
    )

    new_ws = pre.sheets_client.duplicate_template(ticket_number)
    summary["sheet_tab"] = _sheet_tab_url(new_ws.id)
    print(f"  ✓ Duplicated 'Template 3' → '{ticket_number}'")

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

    # Validate + fix en-GB metadata against App Store character limits
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
    if utils.confirm(f"Trigger Codemagic '{METADATA_WORKFLOW_ID}' build?", default=True):
        metadata_build_id = pre.cm.trigger_build(pre.cm_app_id, METADATA_WORKFLOW_ID, RELEASE_BRANCH)
        url = _cm_build_url(pre.cm_app_id, metadata_build_id)
        print(f"  ✓ Triggered: {url}")
        summary["metadata_build"] = url

    # ── Step 14: submit-for-review workflow ──────────────────────
    # Declarations (content rights / price / availability / App Privacy) run
    # on Codemagic in the metadata workflow's `set_app_compliance` lane. This
    # final workflow waits for Apple build-processing (10-60 min) off the
    # orchestrator, attaches the build, and runs the 3-step submission.
    utils.section("STEP 14: Trigger submit-for-review workflow")
    if utils.confirm(
        f"Trigger Codemagic '{SUBMIT_WORKFLOW_ID}' (waits for build, then submits to review)?",
        default=True,
    ):
        submit_build_id = pre.cm.trigger_build(pre.cm_app_id, SUBMIT_WORKFLOW_ID, RELEASE_BRANCH)
        url = _cm_build_url(pre.cm_app_id, submit_build_id)
        print(f"  ✓ Triggered: {url}")
        summary["submit_build"] = url

    # ── Manual checklist ─────────────────────────────────────────
    utils.section("WHAT'S LEFT")
    print(MANUAL_STEPS)

    _print_summary(summary)
    _logout_github(cache)


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
    row("  iOS submit build:", summary.get("submit_build"))
    row("Sheet tab:", summary.get("sheet_tab"))
    row("Telegraph support:", summary.get("telegraph"))
    row("Category:", summary.get("categories"))
    print()
