import re
from typing import Optional

import requests

BASE = "https://api.codemagic.io"


def extract_github_repo(app: dict) -> Optional[tuple]:
    """Parse (username, repo_name) from the app's connected GitHub repository.

    Checks every plausible URL field in app['repository'] and handles both
    https://github.com/user/repo(.git) and git@github.com:user/repo(.git).
    Returns None if nothing matches — caller should fall back to prompting.
    """
    repo = app.get("repository") or {}
    candidates = [
        repo.get("htmlUrl"),
        repo.get("url"),
        repo.get("cloneUrl"),
        repo.get("sshUrl"),
        app.get("repositoryUrl"),
    ]
    for url in candidates:
        if not url:
            continue
        m = re.search(r"github\.com[/:]([^/]+)/([^/]+?)(?:\.git)?/?$", url)
        if m:
            return m.group(1), m.group(2)
    return None


class CodemagicClient:
    def __init__(self, token: str):
        self.session = requests.Session()
        self.session.headers.update({
            "x-auth-token": token,
            "Content-Type": "application/json",
        })

    def list_apps(self) -> list:
        r = self.session.get(f"{BASE}/apps", timeout=30)
        r.raise_for_status()
        data = r.json()
        if isinstance(data, list):
            return data
        return data.get("applications", [])

    def find_app_by_names(self, names: list) -> Optional[dict]:
        apps = self.list_apps()
        for name in names:
            for app in apps:
                if app.get("appName") == name:
                    return app
        return None

    def get_app(self, app_id: str) -> dict:
        r = self.session.get(f"{BASE}/apps/{app_id}", timeout=30)
        r.raise_for_status()
        data = r.json()
        return data.get("application", data)

    def upsert_variable(
        self, app_id: str, key: str, value: str, group: str, secure: bool = False
    ):
        app = self.get_app(app_id)
        existing = app.get("environmentVariables", []) or []
        for v in existing:
            if v.get("key") == key and v.get("group") == group:
                var_id = v.get("_id") or v.get("id")
                if var_id:
                    r = self.session.delete(f"{BASE}/apps/{app_id}/variables/{var_id}", timeout=30)
                    r.raise_for_status()
                break
        r = self.session.post(
            f"{BASE}/apps/{app_id}/variables",
            json={"key": key, "value": value, "group": group, "secure": secure},
            timeout=30,
        )
        r.raise_for_status()

    def trigger_build(self, app_id: str, workflow_id: str, branch: str) -> str:
        r = self.session.post(
            f"{BASE}/builds",
            json={"appId": app_id, "workflowId": workflow_id, "branch": branch},
            timeout=30,
        )
        r.raise_for_status()
        return r.json().get("buildId", "")

    def get_build(self, build_id: str) -> dict:
        r = self.session.get(f"{BASE}/builds/{build_id}", timeout=30)
        r.raise_for_status()
        data = r.json()
        return data.get("build", data)

    def cancel_build(self, build_id: str):
        r = self.session.post(f"{BASE}/builds/{build_id}/cancel", timeout=30)
        r.raise_for_status()

    def get_step_log(self, log_url: str) -> str:
        r = self.session.get(log_url, timeout=30)
        r.raise_for_status()
        return r.text
