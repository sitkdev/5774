import json
from pathlib import Path

import anyio
from claude_agent_sdk import ClaudeAgentOptions, query

PROJECT_ROOT = Path(__file__).resolve().parents[2]

_MODEL = "claude-sonnet-4-6"

APPSTORE_CATEGORIES = [
    "BUSINESS", "DEVELOPER_TOOLS", "EDUCATION", "ENTERTAINMENT", "FINANCE",
    "FOOD_AND_DRINK", "GAMES", "GRAPHICS_AND_DESIGN", "HEALTH_AND_FITNESS",
    "LIFESTYLE", "MAGAZINES_AND_NEWSPAPERS", "MEDICAL", "MUSIC", "NAVIGATION",
    "NEWS", "PHOTO_AND_VIDEO", "PRODUCTIVITY", "REFERENCE", "SHOPPING",
    "SOCIAL_NETWORKING", "SPORTS", "STICKERS", "TRAVEL", "UTILITIES", "WEATHER",
]


GENERATE_PROMPT_TEMPLATE = """Generate App Store metadata AND determine the App Store
category(ies) for the iOS app named "{app_name}".

This is a Kotlin Multiplatform project that ships on both iOS and Android; the iOS App
Store name ("{app_name}") is usually different from the Android app name. Treat
"{app_name}" as the authoritative product name for all generated copy and do NOT use the
Android name even if you encounter it while exploring.

Explore README, build.gradle.kts files, iosApp/, strings resources, and main source
packages only to understand purpose, features, and theme — not to pick a name.

IMPORTANT: copy must fit Apple's per-locale limits — 30 chars (subtitle) and
100 chars (keywords). Keep the subtitle and keywords short.

For the category, pick exactly ONE primary category. Optionally pick ONE secondary
category that fits naturally (or leave it empty if no second category clearly applies).
Use ONLY these exact uppercase values:
{categories}

Output ONLY a JSON object (no prose, no code fences):
{{
  "subtitle":    "2-3 WORDS ONLY, <=30 chars, punchy hook",
  "description": "<=4000 chars, 2-4 paragraphs, engaging App Store copy",
  "keywords":    "exactly 4-5 SHORT single-word keywords, comma-separated, no spaces after commas, <=100 chars total",
  "primary":     "PRIMARY_CATEGORY",
  "secondary":   "SECONDARY_CATEGORY or empty string"
}}"""


FIELD_SHORTEN_HINTS = {
    "subtitle": (
        "HARD RULE: 2-3 words maximum. Use the shortest possible words."
    ),
    "keywords": (
        "HARD RULE: exactly 4-5 short single-word keywords, comma-separated, "
        "no spaces after commas. Pick the shortest synonym for each concept."
    ),
    "name": "",
    "description": "",
}


async def _complete_text(prompt: str, allowed_tools=()) -> str:
    options = ClaudeAgentOptions(
        cwd=str(PROJECT_ROOT),
        allowed_tools=list(allowed_tools),
        permission_mode="default",
        model=_MODEL,
    )
    final_text = ""
    async for msg in query(prompt=prompt, options=options):
        for block in getattr(msg, "content", []) or []:
            text = getattr(block, "text", None)
            if text:
                final_text = text
    return final_text


def _strip_wrap(s: str) -> str:
    s = (s or "").strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in ('"', "'"):
        s = s[1:-1].strip()
    return s


def _validate_categories(data: dict) -> tuple:
    primary = (data.get("primary") or "").strip().upper()
    secondary = (data.get("secondary") or "").strip().upper()
    if primary not in APPSTORE_CATEGORIES:
        raise SystemExit(
            f"Claude returned invalid primary category: {primary!r}. "
            f"Allowed: {APPSTORE_CATEGORIES}"
        )
    if secondary and secondary not in APPSTORE_CATEGORIES:
        raise SystemExit(
            f"Claude returned invalid secondary category: {secondary!r}. "
            f"Allowed: {APPSTORE_CATEGORIES} (or empty)"
        )
    if secondary == primary:
        secondary = ""
    return primary, secondary


async def _run_generate(app_name: str) -> dict:
    prompt = GENERATE_PROMPT_TEMPLATE.format(
        app_name=app_name,
        categories=", ".join(APPSTORE_CATEGORIES),
    )
    text = await _complete_text(prompt, allowed_tools=["Read", "Glob", "Grep"])
    if not text:
        raise SystemExit("No assistant text received from Claude Agent SDK")
    start = text.index("{")
    end = text.rindex("}") + 1
    data = json.loads(text[start:end])
    data["primary"], data["secondary"] = _validate_categories(data)
    return data


def generate(app_name: str) -> dict:
    """Generate App Store metadata (subtitle/description/keywords) and pick the
    App Store category(ies) in a single agent pass. Returns a dict with keys:
    subtitle, description, keywords, primary, secondary.
    """
    if not app_name:
        raise ValueError("generate() requires an explicit iOS app name")
    return anyio.run(_run_generate, app_name)


async def _run_probe() -> str:
    return await _complete_text("Reply with exactly: OK")


def probe() -> str:
    """Smoke-test the Claude Agent SDK — verifies auth and quota cheaply.
    Raises SystemExit on failure.
    """
    text = anyio.run(_run_probe)
    if not (text or "").strip():
        raise SystemExit("Claude Agent SDK probe returned empty response")
    return text


def _format_attempts(history: list, limit: int) -> str:
    lines = []
    for label, text in history:
        length = len(text)
        over = length - limit
        over_note = f", over limit by {over}" if over > 0 else ""
        lines.append(f"  [{label}, {length} chars{over_note}]: {text}")
    return "\n".join(lines)


def shorten_english(field: str, text: str, limit: int, max_attempts: int = 3) -> str:
    """Ask Claude to shorten the English copy to at most `limit` characters."""
    hint = FIELD_SHORTEN_HINTS.get(field, "")
    hint_block = f"{hint}\n" if hint else ""
    history = [("Original", text)]
    for attempt_idx in range(1, max_attempts + 1):
        attempts_block = _format_attempts(history, limit)
        prompt = (
            f"Shorten this App Store {field} to AT MOST {limit} characters.\n"
            f"This is attempt {attempt_idx} of {max_attempts}. "
            f"Be MORE AGGRESSIVE than any prior attempt below.\n"
            f"{hint_block}"
            f"\nHistory (all over the limit):\n{attempts_block}\n\n"
            f"Output ONLY the new shortened text — no quotes, no prose, no explanation."
        )
        result = _strip_wrap(anyio.run(_complete_text, prompt))
        if result and len(result) <= limit:
            return result
        if result:
            history.append((f"Attempt {attempt_idx}", result))
    return history[-1][1][:limit]
