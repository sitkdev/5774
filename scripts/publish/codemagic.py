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


def _iter_app_vars(node):
    """Yield every variable object from a Codemagic app's `appEnvironmentVariables`,
    flattening whatever container shape the API returns (a list, or a dict keyed
    by group/id). A variable object is a dict carrying at least a "key"."""
    if isinstance(node, dict):
        if "key" in node and ("id" in node or "_id" in node):
            yield node
        else:
            for v in node.values():
                yield from _iter_app_vars(v)
    elif isinstance(node, list):
        for x in node:
            yield from _iter_app_vars(x)


class CodemagicClient:
    def __init__(self, token: str):
        self.session = requests.Session()
        self.session.headers.update({
            "x-auth-token": token,
            "Content-Type": "application/json",
        })

    def list_apps(self) -> list:
        r = self.session.get(f"{BASE}/apps")
        r.raise_for_status()
        data = r.json()
        if isinstance(data, list):
            return data
        return data.get("applications", [])

    def find_app_by_names(self, names: list, apps: Optional[list] = None) -> Optional[dict]:
        if apps is None:
            apps = self.list_apps()
        for name in names:
            for app in apps:
                if app.get("appName") == name:
                    return app
        return None

    def get_app(self, app_id: str) -> dict:
        r = self.session.get(f"{BASE}/apps/{app_id}")
        r.raise_for_status()
        data = r.json()
        return data.get("application", data)

    def _delete_variable(self, app_id: str, key: str, group: str):
        """Delete any existing variable matching key+group (no-op if absent).

        Codemagic returns the app's variables under `appEnvironmentVariables`
        (NOT `environmentVariables`), including secure ones, each with an `id`.
        """
        app = self.get_app(app_id)
        for v in _iter_app_vars(app.get("appEnvironmentVariables")):
            if v.get("key") == key and v.get("group") == group:
                var_id = v.get("id") or v.get("_id")
                if var_id:
                    self.session.delete(
                        f"{BASE}/apps/{app_id}/variables/{var_id}"
                    ).raise_for_status()

    def upsert_variable(
        self, app_id: str, key: str, value: str, group: str, secure: bool = False
    ):
        # Override semantics: delete any existing value, then create. Codemagic
        # has no update endpoint, so re-create is how you override.
        payload = {"key": key, "value": value, "group": group, "secure": secure}
        self._delete_variable(app_id, key, group)
        r = self.session.post(f"{BASE}/apps/{app_id}/variables", json=payload)
        if not r.ok:
            # The var may already exist even though GET /apps didn't surface it
            # (e.g. secure vars aren't returned), so the delete above missed it
            # and the create collided. Re-resolve, delete, and retry once so a
            # re-run overrides the prior value instead of failing.
            self._delete_variable(app_id, key, group)
            r = self.session.post(f"{BASE}/apps/{app_id}/variables", json=payload)
        r.raise_for_status()

    def trigger_build(self, app_id: str, workflow_id: str, branch: str) -> str:
        r = self.session.post(
            f"{BASE}/builds",
            json={"appId": app_id, "workflowId": workflow_id, "branch": branch},
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
