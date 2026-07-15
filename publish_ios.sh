#!/usr/bin/env bash
# =============================================================================
# App Store publishing entry point — android/app-store-publisher (canonical).
# =============================================================================
# This repo is the single source of truth for the iOS App Store auto-publish
# flow. The android-ci-policy `publish_ios` job clones it, overlays its files
# onto the consumer project's checkout, and runs THIS script from the project
# root on the ios-builder macOS runner.
#
# Config comes from CI/CD env vars (there is no Utils/local.properties in CI):
#   JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN          — group CI/CD vars
#   GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64              — base64 of the SA json
#   JIRA_ISSUE_KEY                                     — iOS ticket to publish
# The Codemagic API token and production GitHub PAT are read from the Jira
# ticket fields ("Codemagic API Token" / "GitHub Token") by the flow itself.
# =============================================================================
set -euo pipefail

# The project checkout is the working directory; use it as the repo root.
REPO_ROOT="${CI_PROJECT_DIR:-$PWD}"
cd "$REPO_ROOT"

PYTHON_BIN="${PYTHON_BIN:-python3}"
# Keep the venv OUTSIDE the repo — the flow runs `git add -A`, and a venv inside
# the checkout would get committed/pushed. Sibling dir named for the project.
VENV_DIR="${PUBLISH_VENV_DIR:-$(dirname "$REPO_ROOT")/.publish-venv-$(basename "$REPO_ROOT")}"
# Don't write .pyc / __pycache__ into the repo (same reason).
export PYTHONDONTWRITEBYTECODE=1

echo "==> Repo root: $REPO_ROOT"
echo "==> Creating Python venv ($VENV_DIR)"
"$PYTHON_BIN" -m venv "$VENV_DIR"
# shellcheck disable=SC1090,SC1091
source "$VENV_DIR/bin/activate"

echo "==> Installing publish dependencies"
python -m pip install --quiet --upgrade pip
python -m pip install --quiet -r scripts/requirements.txt

echo "==> Running App Store publish flow (non-interactive)"
# exec so the flow's exit code becomes this script's (and thus the job's) result.
exec python -m scripts.publish --yes
