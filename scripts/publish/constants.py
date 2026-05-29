"""Single source of truth for cross-module constants."""

# ── Google Sheets ────────────────────────────────────────────────────
GSHEET_ID = "1bLUv9ONi7KrAOpbWur7U8KcrbydPXs3zxZkz-B_4CzE"
SHEET_TEMPLATE_TAB = "Template 2"

# Rows 2-37 are locales (row 2 = en-US). Columns:
# A=locale B=name C=subtitle D=description E=keywords
# F=release_notes (unused) G=promotional_text (unused) H=privacy_url I=support_url
SHEET_LOCALE_ROW_FIRST = 2
SHEET_LOCALE_ROW_LAST = 37
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

# ── App Store per-locale character limits ────────────────────────────
APPSTORE_LIMITS = {
    "name": 30,
    "subtitle": 30,
    "keywords": 100,
    "description": 4000,
}

# Metadata-fix rule: if <=N locales are oversized for a field, patch those locales
# individually; if more, shorten the English copy and let re-translation happen.
LOCALE_FIX_THRESHOLD = 3

# Max iterations of the check-and-fix loop (shortening English, re-waiting).
METADATA_FIX_MAX_ITERATIONS = 3
