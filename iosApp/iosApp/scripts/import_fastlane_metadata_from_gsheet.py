#!/usr/bin/env python3
from __future__ import annotations

import csv
import io
import os
from pathlib import Path
from urllib.parse import quote

import requests


FIELD_TO_FILENAME = {
    "name": "name.txt",
    "subtitle": "subtitle.txt",
    "description": "description.txt",
    "keywords": "keywords.txt",
    "release_notes": "release_notes.txt",
    "promotional_text": "promotional_text.txt",
    "privacy_url": "privacy_url.txt",
    "support_url": "support_url.txt",
    "marketing_url": "marketing_url.txt",
}

LOCALES = [
    "en-GB"
]


def build_csv_url(sheet_id: str, sheet_name: str) -> str:
    return (
        f"https://docs.google.com/spreadsheets/d/{sheet_id}/gviz/tq"
        f"?tqx=out:csv&sheet={quote(sheet_name)}"
    )


def normalize(s: str) -> str:
    return s.strip().lower()


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + ("\n" if content.strip() else ""), encoding="utf-8")


def main() -> None:
    sheet_id = os.environ["GSHEET_ID"]
    sheet_name = os.environ["GSHEET_TAB"]
    metadata_dir = Path(os.environ.get("FASTLANE_METADATA_DIR", "iosApp/fastlane/metadata")).resolve()

    url = build_csv_url(sheet_id, sheet_name)
    print(f"Downloading metadata CSV from: {url}")

    response = requests.get(url, timeout=60)
    response.raise_for_status()

    content = response.content.decode("utf-8-sig", errors="replace")
    reader = csv.DictReader(io.StringIO(content))

    if not reader.fieldnames:
        raise RuntimeError("CSV has no headers")

    headers = {normalize(h): h for h in reader.fieldnames}

    if "locale" not in headers:
        raise RuntimeError(f"CSV must contain 'locale' column. Found: {reader.fieldnames}")

    # Ensure all locale directories exist
    for locale in LOCALES:
        (metadata_dir / locale).mkdir(parents=True, exist_ok=True)

    processed = 0
    for row in reader:
        locale = (row.get(headers["locale"]) or "").strip()
        if not locale:
            continue

        locale_dir = metadata_dir / locale
        locale_dir.mkdir(parents=True, exist_ok=True)

        for field, filename in FIELD_TO_FILENAME.items():
            if field in headers:
                value = row.get(headers[field], "") or ""
                write_file(locale_dir / filename, value)

        processed += 1

    print(f"Done. Processed {processed} locale rows.")
    print(f"Metadata directory: {metadata_dir}")


if __name__ == "__main__":
    main()