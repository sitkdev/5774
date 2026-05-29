"""Validate and fix App Store metadata against per-locale character limits."""
import time

from . import metadata_gen, utils
from .constants import (
    APPSTORE_LIMITS,
    LOCALE_FIX_THRESHOLD,
    METADATA_FIX_MAX_ITERATIONS,
    SHEET_FIELD_COLUMNS,
    SHEET_LOCALE_ROW_FIRST,
    SHEET_LOCALE_ROW_LAST,
)


def check_and_fix(ws, en_row: dict) -> None:
    utils.section("STEP 12.5: Validate + fix per-locale metadata")

    for iteration in range(1, METADATA_FIX_MAX_ITERATIONS + 1):
        print(f"  Iteration {iteration}: waiting for sheet translations...")
        _wait_for_translations(ws)

        oversized_en = _find_en_oversized(ws)
        oversized_translated = _find_translated_oversized(ws)

        if not oversized_en and not oversized_translated:
            print("  ✓ All metadata within limits")
            return

        english_changed = False

        # Phase A: en-US cells over limit get shortened (safety net for the generator).
        for field, (value, length) in oversized_en.items():
            limit = APPSTORE_LIMITS[field]
            print(f"  en-US '{field}' is {length} > {limit}; shortening English...")
            shorter = metadata_gen.shorten_english(field, value, limit)
            _write_cell(ws, SHEET_LOCALE_ROW_FIRST, SHEET_FIELD_COLUMNS[field], shorter)
            en_row[field] = shorter
            english_changed = True
            oversized_translated.pop(field, None)

        # Phase B: translated-only oversize — threshold rule decides strategy.
        for field, items in oversized_translated.items():
            limit = APPSTORE_LIMITS[field]
            if len(items) <= LOCALE_FIX_THRESHOLD:
                print(
                    f"  '{field}' oversized in {len(items)} locale(s); "
                    f"translating per-locale: {[i[1] for i in items]}"
                )
                _fix_per_locale(ws, field, items, limit, en_row[field])
            else:
                print(
                    f"  '{field}' oversized in {len(items)} locales "
                    f"(> threshold {LOCALE_FIX_THRESHOLD}); shortening English..."
                )
                shorter = metadata_gen.shorten_english(field, en_row[field], limit)
                _write_cell(ws, SHEET_LOCALE_ROW_FIRST, SHEET_FIELD_COLUMNS[field], shorter)
                en_row[field] = shorter
                english_changed = True

        if not english_changed:
            print("  ✓ Per-locale fixes applied")
            return

    print(
        f"  [warn] metadata fix reached max iterations "
        f"({METADATA_FIX_MAX_ITERATIONS}); some cells may still exceed limits"
    )


# ── Translation wait ─────────────────────────────────────────────────

def _wait_for_translations(ws, timeout_s: int = 120, poll_s: int = 3) -> None:
    first = SHEET_LOCALE_ROW_FIRST + 1
    last = SHEET_LOCALE_ROW_LAST
    range_ = f"B{first}:E{last}"
    start = time.time()
    while time.time() - start < timeout_s:
        rows = ws.get(range_) or []
        if _all_populated(rows, expected_rows=last - first + 1):
            return
        time.sleep(poll_s)
    print(f"  [warn] translations still incomplete after {timeout_s}s — continuing")


def _all_populated(rows, expected_rows: int) -> bool:
    if len(rows) < expected_rows:
        return False
    for row in rows:
        padded = (list(row) + ["", "", "", ""])[:4]
        for v in padded:
            v = (v or "").strip()
            if not v or _is_loading(v):
                return False
    return True


def _is_loading(v: str) -> bool:
    return v.startswith("Loading") or v.startswith("#")


# ── Oversize detection ───────────────────────────────────────────────

def _find_en_oversized(ws) -> dict:
    row = SHEET_LOCALE_ROW_FIRST
    data = ws.get(f"B{row}:E{row}") or [[]]
    cells = (data[0] if data else []) + ["", "", "", ""]
    out = {}
    for field, col in SHEET_FIELD_COLUMNS.items():
        value = cells[col - 2] or ""
        limit = APPSTORE_LIMITS[field]
        if len(value) > limit:
            out[field] = (value, len(value))
    return out


def _find_translated_oversized(ws) -> dict:
    first = SHEET_LOCALE_ROW_FIRST + 1
    last = SHEET_LOCALE_ROW_LAST
    data = ws.get(f"A{first}:E{last}") or []
    out: dict = {}
    for offset, row in enumerate(data):
        row_idx = first + offset
        padded = (list(row) + ["", "", "", "", ""])[:5]
        locale = (padded[0] or "").strip()
        for field, col in SHEET_FIELD_COLUMNS.items():
            value = padded[col - 1] or ""
            limit = APPSTORE_LIMITS[field]
            if len(value) > limit:
                out.setdefault(field, []).append((row_idx, locale, value, len(value)))
    return out


# ── Writes ───────────────────────────────────────────────────────────

def _write_cell(ws, row: int, col: int, value: str) -> None:
    ws.batch_update(
        [{"range": _a1(row, col), "values": [[value]]}],
        value_input_option="RAW",
    )


def _fix_per_locale(ws, field: str, items: list, limit: int, english: str) -> None:
    col = SHEET_FIELD_COLUMNS[field]
    updates = []
    for row_idx, locale, _current, length in items:
        short = metadata_gen.translate_short(field, english, locale, limit)
        print(f"    {locale} {_a1(row_idx, col)}: {length} → {len(short)} chars")
        updates.append({"range": _a1(row_idx, col), "values": [[short]]})
    if updates:
        ws.batch_update(updates, value_input_option="RAW")


def _a1(row: int, col: int) -> str:
    letters = ""
    c = col
    while c > 0:
        c, rem = divmod(c - 1, 26)
        letters = chr(65 + rem) + letters
    return f"{letters}{row}"
