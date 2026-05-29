import subprocess
from pathlib import Path

import requests

from .constants import COMMIT_MESSAGE

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _git(*args, capture=False, check=True):
    if capture:
        r = subprocess.run(
            ["git", *args],
            cwd=PROJECT_ROOT,
            check=check,
            capture_output=True,
            text=True,
        )
        return r.stdout.strip()
    return subprocess.run(["git", *args], cwd=PROJECT_ROOT, check=check)


def ensure_branch(name: str):
    current = _git("rev-parse", "--abbrev-ref", "HEAD", capture=True)
    if current == name:
        return
    existing = _git("branch", "--list", name, capture=True)
    if existing:
        _git("checkout", name)
    else:
        _git("checkout", "-b", name)


def find_repo_name(username: str, token: str, ticket_number: str) -> str:
    """Try {n}, {n-1}, else prompt."""
    candidates = [ticket_number]
    try:
        candidates.append(str(int(ticket_number) - 1))
    except ValueError:
        pass
    for candidate in candidates:
        url = f"https://api.github.com/repos/{username}/{candidate}"
        resp = requests.get(url, auth=(username, token), timeout=15)
        if resp.status_code == 200:
            print(f"  Found GitHub repo: {username}/{candidate}")
            return candidate
        print(f"  GitHub repo not found: {username}/{candidate} ({resp.status_code})")
    from . import utils
    return utils.prompt("Enter GitHub repo name manually")


def add_production_remote(username: str, token: str, repo_name: str) -> str:
    url = f"https://{username}:{token}@github.com/{username}/{repo_name}.git"
    existing = subprocess.run(
        ["git", "remote", "get-url", "production"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
    )
    if existing.returncode == 0 and existing.stdout.strip():
        _git("remote", "set-url", "production", url)
    else:
        _git("remote", "add", "production", url)
    return f"https://github.com/{username}/{repo_name}.git"


def commit_all_and_push(branch: str):
    _git("add", "-A")
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    if status.stdout.strip():
        _git("commit", "-m", COMMIT_MESSAGE)
    else:
        print("  No changes to commit.")
    _git("push", "production", branch)
