"""Fail-fast validation of the operator-filled account/app data.

Account setup on the Jira ticket + IF story is done by hand, so fields are
routinely missing or malformed. Today that only surfaces when the value is
*consumed* — usually deep in the Codemagic Upload Metadata stage, a ~30-minute
feedback loop after the run started. This module checks everything up front in
Step 0 and reports every problem at once, so the operator fixes them in a single
pass before anything expensive or destructive runs.

Only DATA is checked here (no network). External services (.p8 download,
Codemagic, GitHub, Google Sheets, Claude) are verified separately in preflight.
"""
import re

from . import jira, utils

# Format matchers for the well-defined Apple/App-Store fields. Kept deliberately
# lenient where a value's shape varies (bundle), strict where Apple's format is
# fixed (Team/Key ID length, Issuer UUID, numeric Apple ID).
_EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
_URL = re.compile(r"^https?://\S+$", re.I)
_TEN_ALNUM = re.compile(r"^[A-Za-z0-9]{10}$")
_UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
_E164 = re.compile(r"^\+\d{7,15}$")
_BUNDLE = re.compile(r"^\S+\.\S+$")  # non-space, reverse-DNS (at least one dot)
_NUMERIC = re.compile(r"^\d+$")


class Issue:
    __slots__ = ("severity", "field", "detail")

    def __init__(self, severity: str, field: str, detail: str):
        self.severity = severity
        self.field = field
        self.detail = detail


def _collect(ticket: jira.AppTicket, story: jira.IfStory) -> list:
    issues: list = []

    def req(field, value, why, pattern=None, fmt=None):
        """Required field: ERROR if empty, ERROR if present-but-malformed."""
        v = (value or "").strip()
        if not v:
            issues.append(Issue("ERROR", field, f"missing — {why}"))
        elif pattern is not None and not pattern.match(v):
            issues.append(Issue("ERROR", field, f"malformed ({v!r}); expected {fmt} — {why}"))

    def warn(field, detail):
        issues.append(Issue("WARN", field, detail))

    # ── App ticket ────────────────────────────────────────────────
    req("Bundle", ticket.bundle,
        "signs & uploads the build (PRODUCT_BUNDLE_IDENTIFIER / APP_IDENTIFIER)",
        _BUNDLE, "reverse-DNS, e.g. com.company.app")
    req("Team ID", ticket.team_id,
        "DEVELOPMENT_TEAM — code signing fails without a valid one",
        _TEN_ALNUM, "10 letters/digits")
    req("Key ID", ticket.key_id,
        "APP_STORE_CONNECT_KEY_IDENTIFIER — every App Store Connect API call authenticates with it "
        "(and the .p8 filename is matched against it)",
        _TEN_ALNUM, "10 letters/digits")
    req("Issuer ID", ticket.issuer_id,
        "APP_STORE_CONNECT_ISSUER_ID — App Store Connect API auth",
        _UUID, "a UUID (8-4-4-4-12 hex)")
    req("Appstore App ID", ticket.appstore_app_id,
        "CM_APP_STORE_APPLE_ID — identifies the app for metadata, declarations & submission",
        _NUMERIC, "the numeric Apple ID")
    req("App Name (IOS)", ticket.app_name_ios,
        "the App Store name and the seed for the AI-generated subtitle/description/keywords")
    req("Privacy Policy", ticket.privacy_policy,
        "the listing's privacy URL — App Store submission requires it",
        _URL, "an http(s) URL")
    req("AppStore Account Name", ticket.appstore_account_name,
        "the copyright line and the App Review contact name")

    name = (ticket.appstore_account_name or "").strip()
    if name and len(name.split()) < 2:
        warn("AppStore Account Name",
             f"{name!r} has no surname — the App Review contact last name will be blank")

    domain = (ticket.domain or "").strip()
    if domain:
        host = re.sub(r"^https?://", "", domain).strip("/").lstrip(".")
        if "." not in host or " " in host:
            warn("Klo link",
                 f"{domain!r} doesn't look like a bare domain — the status-webhook URL may be malformed")

    # ── IF account story (Jira field names given so the operator knows where) ──
    req("IF Email", story.email,
        "APPLE_ID / FASTLANE_USER (web-session login) + App Review contact email; Jira field 'mail for account'",
        _EMAIL, "a valid email")
    req("IF Phone", story.phone,
        "App Review contact phone; deliver (Upload Metadata) rejects an empty/invalid phone. "
        "Jira field 'Registration Number Phone' (or 'Main Phone Number')",
        _E164, "E.164, e.g. +380991234567")
    req("IF Password", story.password,
        "FASTLANE_PASSWORD — the web-session lane (App Privacy, a hard submit-blocker) can't log in without it. "
        "Jira field 'password for account'")
    req("IF 2FA Number", story.twofa_number,
        "SPACESHIP_2FA_SMS_DEFAULT_PHONE_NUMBER — Apple texts the 2FA code here; web-session login fails without it. "
        "Jira field '2FA Number'",
        _E164, "E.164, e.g. +380991234567")
    req("IF 2FA Link", story.twofa_link,
        "SMS_2FA_GATEWAY_URL — polled for the 2FA code; web-session login fails without it. "
        "Jira field '2fa Link'",
        _URL, "an http(s) URL")

    # ── CI-only: the tokens must live on the ticket ──────────────
    # Locally these fall back to an interactive prompt, but a CI run (--yes) has
    # no prompt — an empty ticket field there dies later with a cryptic
    # "no default available for prompt" instead of a clear message.
    if utils.is_assume_yes():
        req("Codemagic API Token", ticket.codemagic_api_token,
            "in CI it must be on the ticket — no interactive prompt to fall back to")
        req("GitHub Token", ticket.github_token,
            "in CI it must be on the ticket — no interactive prompt to fall back to")

    return issues


def check(ticket: jira.AppTicket, story: jira.IfStory):
    """Validate the gathered ticket/story data and fail fast on any blocker.

    Prints one consolidated report (all problems, not just the first). ERRORs are
    fatal in both CI and local runs — they would otherwise fail downstream on
    Codemagic. WARNINGs are printed but let the run proceed.
    """
    issues = _collect(ticket, story)
    utils.section("STEP 0.5: Validate account data")

    if not issues:
        print("  ✓ All required account/app fields are present and well-formed.")
        return

    errors = [i for i in issues if i.severity == "ERROR"]
    warnings = [i for i in issues if i.severity == "WARN"]
    for i in errors + warnings:
        mark = "✗" if i.severity == "ERROR" else "⚠"
        print(f"  {mark} [{i.severity:<5}] {i.field:<21} {i.detail}")

    print()
    print(f"  {len(errors)} error(s), {len(warnings)} warning(s)")
    if errors:
        utils.die(
            f"{len(errors)} blocking problem(s) in the account data (see above). "
            "These would otherwise fail later on Codemagic (e.g. at Upload Metadata). "
            "Fix the Jira ticket / IF story and re-run."
        )
