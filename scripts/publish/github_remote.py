import os
import subprocess
from pathlib import Path

import requests

from .constants import COMMIT_MESSAGE

PROJECT_ROOT = Path(__file__).resolve().parents[2]

# Identity for the Release commit pushed to the production GitHub repo. CI
# runners have no ambient git identity (git fails with "unable to auto-detect
# email address"), so set it explicitly. Overridable via env; passed inline with
# `git -c ...` at commit time so we never mutate global or repo git config.
_COMMIT_NAME = (
    os.environ.get("PUBLISH_GIT_NAME")
    or os.environ.get("GIT_AUTHOR_NAME")
    or "Trident iOS Publisher"
)
_COMMIT_EMAIL = (
    os.environ.get("PUBLISH_GIT_EMAIL")
    or os.environ.get("GIT_AUTHOR_EMAIL")
    or "ci@noreply.trident.tools"
)


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
    # -B creates the branch if missing, or RESETS it to the current HEAD if it
    # already exists (e.g. a stale ios_release left over in a reused CI
    # workspace), then checks it out. Since the target is HEAD, the working tree
    # doesn't change, so uncommitted publish edits and untracked files never
    # block the switch — unlike `git checkout <existing-divergent-branch>`,
    # which aborts with "local changes would be overwritten".
    _git("checkout", "-B", name)


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
    # If this ticket was already published, the production branch exists and our
    # freshly-cloned local branch diverges from it, so a plain push is rejected
    # (non-fast-forward). Re-base this Release on top of the existing branch:
    # fetch it, move HEAD to the remote tip while KEEPING our working-tree edits
    # (reset --soft), then commit the difference. This fast-forwards cleanly and
    # works even when the histories are unrelated — no rebase/merge conflicts.
    _git("fetch", "production", check=False)
    remote_ref = f"production/{branch}"
    remote_exists = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", remote_ref],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
    ).returncode == 0
    if remote_exists:
        print(f"  {remote_ref} already exists — basing this Release on top of it.")
        _git("reset", "--soft", remote_ref)

    _git("add", "-A")
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    if status.stdout.strip():
        _git(
            "-c", f"user.name={_COMMIT_NAME}",
            "-c", f"user.email={_COMMIT_EMAIL}",
            "commit", "-m", COMMIT_MESSAGE,
        )
    else:
        print("  No changes to commit (working tree matches the existing release).")
    _git("push", "production", branch)
