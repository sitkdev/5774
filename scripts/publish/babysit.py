import time
from typing import Optional

from . import codemagic, utils
from .constants import METADATA_WORKFLOW_ID, RELEASE_BRANCH

POLL_INTERVAL_SECS = 60
MAX_ATTEMPTS = 3
STUCK_TAIL_LINES = 5
STUCK_MIN_MATCHES = 2

STUCK_SUBSTRINGS = (
    "Waiting for screenshots to appear",
    'Server error got 500',
)
METADATA_STEP_HINT = "upload metadata"
TERMINAL_STATUSES = {"finished", "failed", "canceled", "timeout", "warning"}


def _build_url(app_id: str, build_id: str) -> str:
    return f"https://codemagic.io/app/{app_id}/build/{build_id}"


def run(cm: codemagic.CodemagicClient, app_id: str, initial_build_id: str):
    utils.section("BABYSIT metadata build (Ctrl-C to exit)")
    print(
        f"  Polling every {POLL_INTERVAL_SECS}s; on fastlane ASC-500 stuck "
        f"pattern, cancel + retrigger (up to {MAX_ATTEMPTS} attempts total)."
    )
    build_id = initial_build_id
    attempt = 1
    try:
        while True:
            status, stuck = _poll_once(cm, build_id)
            ts = time.strftime("%H:%M:%S")
            stuck_flag = " [STUCK]" if stuck else ""
            print(f"  [{ts}] attempt {attempt}/{MAX_ATTEMPTS}  status={status or '?'}{stuck_flag}  {_build_url(app_id, build_id)}")

            if status in TERMINAL_STATUSES:
                print(f"  ✓ build ended: {status}")
                return

            if stuck:
                if attempt >= MAX_ATTEMPTS:
                    print(
                        f"  [warn] max attempts ({MAX_ATTEMPTS}) reached; "
                        f"leaving build running — resolve manually."
                    )
                    return
                print(f"  Cancelling stuck build...")
                try:
                    cm.cancel_build(build_id)
                except Exception as e:
                    print(f"  [warn] cancel failed: {e}")
                try:
                    build_id = cm.trigger_build(app_id, METADATA_WORKFLOW_ID, RELEASE_BRANCH)
                except Exception as e:
                    print(f"  [warn] retrigger failed: {e}")
                    return
                attempt += 1
                print(f"  ✓ retriggered (attempt {attempt}/{MAX_ATTEMPTS}): {_build_url(app_id, build_id)}")

            time.sleep(POLL_INTERVAL_SECS)
    except KeyboardInterrupt:
        print()
        print(f"  Babysit exited — build continues at {_build_url(app_id, build_id)}")


def _poll_once(cm: codemagic.CodemagicClient, build_id: str):
    try:
        build = cm.get_build(build_id)
    except Exception as e:
        print(f"  [warn] GET build {build_id}: {e}")
        return "unknown", False
    status = (build.get("status") or "").lower()
    if status in TERMINAL_STATUSES:
        return status, False
    log_url = _find_metadata_step_log_url(build)
    if not log_url:
        return status, False
    try:
        log_text = cm.get_step_log(log_url)
    except Exception as e:
        print(f"  [warn] fetching step log: {e}")
        return status, False
    return status, _is_stuck(log_text)


def _find_metadata_step_log_url(build: dict) -> Optional[str]:
    for action in build.get("buildActions", []) or []:
        name = (action.get("name") or "").lower()
        if METADATA_STEP_HINT not in name:
            continue
        for sub in action.get("subactions") or []:
            url = sub.get("logUrl")
            if url:
                return url
        if action.get("logUrl"):
            return action.get("logUrl")
    return None


def _is_stuck(log_text: str) -> bool:
    lines = [l for l in log_text.splitlines() if l.strip()]
    tail = lines[-STUCK_TAIL_LINES:]
    matches = sum(
        1 for line in tail
        if any(sub in line for sub in STUCK_SUBSTRINGS)
    )
    return matches >= STUCK_MIN_MATCHES
