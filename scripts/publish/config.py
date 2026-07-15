import base64
import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
UTILS_LOCAL_PROPS = PROJECT_ROOT.parent / "Utils" / "local.properties"
CACHE_PATH = PROJECT_ROOT / "scripts" / ".publish-cache.json"


def _load_properties(path: Path) -> dict:
    if not path.exists():
        return {}
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        if "=" not in s:
            continue
        k, _, v = s.partition("=")
        out[k.strip()] = v.strip()
    return out


# Each config field can be supplied two ways. Env vars win; the
# Utils/local.properties file is the fallback. This lets the same flow run
# unattended in CI (env only — there is no sibling Utils/ checkout there) and
# interactively on a developer machine (file only).
#   field                -> (env var,                local.properties key)
FIELD_SOURCES = {
    "jira_base_url":        ("JIRA_BASE_URL",        "jira.baseUrl"),
    "jira_browse_base_url": ("JIRA_BROWSE_BASE_URL", "jira.browseBaseUrl"),
    "jira_email":           ("JIRA_EMAIL",           "jira.email"),
    "jira_api_token":       ("JIRA_API_TOKEN",       "jira.apiToken"),
}


@dataclass
class GlobalConfig:
    jira_base_url: str
    jira_browse_base_url: str
    jira_email: str
    jira_api_token: str
    sheets_credentials_path: str

    @staticmethod
    def load() -> "GlobalConfig":
        props = _load_properties(UTILS_LOCAL_PROPS)

        def resolve(env_key: str, prop_key: str) -> str:
            val = os.environ.get(env_key)
            if val:
                return val.strip()
            return (props.get(prop_key) or "").strip()

        values = {
            field: resolve(env_key, prop_key)
            for field, (env_key, prop_key) in FIELD_SOURCES.items()
        }

        # Browse URL defaults to the API base URL — same host on Atlassian
        # Cloud, so CI only needs to provide JIRA_BASE_URL.
        if not values["jira_browse_base_url"]:
            values["jira_browse_base_url"] = values["jira_base_url"]

        values["sheets_credentials_path"] = _resolve_sheets_credentials(props)

        missing = [f for f, v in values.items() if not v]
        if missing:
            raise SystemExit(
                "Missing required config (set via env var, or in "
                f"{UTILS_LOCAL_PROPS}):\n  "
                + "\n  ".join(_describe_missing(m) for m in missing)
                + "\n\nProvide them and re-run."
            )
        return GlobalConfig(**values)


def _resolve_sheets_credentials(props: dict) -> str:
    """Resolve the Google service-account JSON to a file path.

    Precedence:
      1. GOOGLE_SHEETS_CREDENTIALS_PATH        — path to an existing json file
      2. GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64 — base64 of the json, decoded to
         a temp file (used in CI, where the SA key is a masked CI/CD variable)
      3. google.sheets.credentialsPath in local.properties (local dev)
    """
    path = os.environ.get("GOOGLE_SHEETS_CREDENTIALS_PATH")
    if path:
        return path.strip()

    b64 = os.environ.get("GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64")
    if b64:
        try:
            raw = base64.b64decode(b64.strip(), validate=True)
        except Exception as e:
            raise SystemExit(
                f"GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64 is not valid base64: {e}"
            )
        # Fail fast with a clear message rather than deep inside gspread.
        try:
            json.loads(raw.decode("utf-8"))
        except Exception as e:
            raise SystemExit(
                "GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64 did not decode to valid "
                f"JSON: {e}"
            )
        fd, tmp = tempfile.mkstemp(prefix="gsheets-sa-", suffix=".json")
        with os.fdopen(fd, "wb") as fh:
            fh.write(raw)
        return tmp

    return (props.get("google.sheets.credentialsPath") or "").strip()


def _describe_missing(field: str) -> str:
    if field == "sheets_credentials_path":
        return (
            "sheets_credentials_path (env GOOGLE_SHEETS_CREDENTIALS_PATH or "
            "GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64, or "
            "google.sheets.credentialsPath)"
        )
    env_key, prop_key = FIELD_SOURCES[field]
    return f"{field} (env {env_key} or {prop_key})"


class Cache:
    def __init__(self):
        self.path = CACHE_PATH
        self._data = {}
        if self.path.exists():
            try:
                self._data = json.loads(self.path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                self._data = {}

    def get(self, key: str, default=None):
        return self._data.get(key, default)

    def set(self, key: str, value):
        self._data[key] = value
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self._data, indent=2), encoding="utf-8")
