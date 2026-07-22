"""Single source of truth for cross-module constants."""

# ── Google Sheets ────────────────────────────────────────────────────
GSHEET_ID = "1bLUv9ONi7KrAOpbWur7U8KcrbydPXs3zxZkz-B_4CzE"
SHEET_TEMPLATE_TAB = "Template 3"

# Row 2 holds the en-GB metadata. Columns:
# A=locale B=name C=subtitle D=description E=keywords
# F=release_notes (unused) G=promotional_text (unused) H=privacy_url I=support_url
SHEET_LOCALE_ROW_FIRST = 2
SHEET_FIELD_COLUMNS = {"name": 2, "subtitle": 3, "description": 4, "keywords": 5}

# ── Jira ─────────────────────────────────────────────────────────────
# Mirrors the proven-working form; Kross-Apps needs quoting (hyphen), IOSOR must not.
JIRA_JQL_PROJECTS = 'project = "Kross-Apps" OR project = IOSOR'
RELATED_ACCOUNT_LINK = "related account"
IF_PROJECT_KEY_PREFIX = "IF-"
DES_PROJECT_KEY_PREFIX = "DES-"
IOS_WHITE_DESIGN_SUMMARY = "IOS White Design"
IOS_ISSUE_TYPE = "IOS"

# ── Git ──────────────────────────────────────────────────────────────
RELEASE_BRANCH = "ios_release"
COMMIT_MESSAGE = "Release"

# ── Codemagic ────────────────────────────────────────────────────────
RELEASE_WORKFLOW_ID = "ios_kmp_release"
METADATA_WORKFLOW_ID = "upload_ios_metadata"
SUBMIT_WORKFLOW_ID = "submit_ios_for_review"

# ── Codemagic build-wait timeouts (seconds) ──────────────────────────
# The orchestrator waits for each triggered build to finish before moving on, so
# a failed build fails the run (rather than being reported upstream as a success)
# and submit never starts before release+metadata finish. These are generous
# "give up and report" ceilings — a build normally finishes far sooner. The Free
# plan runs builds one-at-a-time, so RELEASE's ceiling also allows for it sitting
# queued behind the metadata build; SUBMIT includes Apple's build-processing wait
# (up to ~1h, per the lane's own polling). Tune here if your Codemagic account is
# busier/slower.
RELEASE_BUILD_TIMEOUT = 120 * 60
METADATA_BUILD_TIMEOUT = 90 * 60
SUBMIT_BUILD_TIMEOUT = 140 * 60
BUILD_POLL_INTERVAL = 15

# ── App Store per-locale character limits ────────────────────────────
APPSTORE_LIMITS = {
    "name": 30,
    "subtitle": 30,
    "keywords": 100,
    "description": 4000,
}
