"""Validate and fix App Store metadata against character limits."""
from . import metadata_gen, utils
from .constants import (
    APPSTORE_LIMITS,
    SHEET_FIELD_COLUMNS,
    SHEET_LOCALE_ROW_FIRST,
)


def check_and_fix(ws, en_row: dict) -> None:
    utils.section("STEP 12.5: Validate + fix metadata")

    oversized = _find_en_oversized(ws)
    if not oversized:
        print("  ✓ All metadata within limits")
        return

    for field, (value, length) in oversized.items():
        limit = APPSTORE_LIMITS[field]
        print(f"  '{field}' is {length} > {limit}; shortening...")
        shorter = metadata_gen.shorten_english(field, value, limit)
        _write_cell(ws, SHEET_LOCALE_ROW_FIRST, SHEET_FIELD_COLUMNS[field], shorter)
        en_row[field] = shorter

    print("  ✓ Metadata shortened to within limits")


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


# ── Writes ───────────────────────────────────────────────────────────

def _write_cell(ws, row: int, col: int, value: str) -> None:
    ws.batch_update(
        [{"range": _a1(row, col), "values": [[value]]}],
        value_input_option="RAW",
    )


def _a1(row: int, col: int) -> str:
    letters = ""
    c = col
    while c > 0:
        c, rem = divmod(c - 1, 26)
        letters = chr(65 + rem) + letters
    return f"{letters}{row}"
