import json
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


REQUIRED_KEYS = {
    "jira.baseUrl": "jira_base_url",
    "jira.browseBaseUrl": "jira_browse_base_url",
    "jira.email": "jira_email",
    "jira.apiToken": "jira_api_token",
    "google.sheets.credentialsPath": "sheets_credentials_path",
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
        missing = [k for k in REQUIRED_KEYS if not props.get(k)]
        if missing:
            raise SystemExit(
                f"Missing keys in {UTILS_LOCAL_PROPS}:\n  "
                + "\n  ".join(missing)
                + "\n\nAdd them and re-run."
            )
        return GlobalConfig(**{v: props[k] for k, v in REQUIRED_KEYS.items()})


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
