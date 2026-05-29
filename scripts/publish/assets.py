import io
import zipfile
from pathlib import Path

from . import utils

PROJECT_ROOT = Path(__file__).resolve().parents[2]
WHITE_DIR = PROJECT_ROOT / "iosApp" / "fastlane" / "white"
IMAGE_EXTS = {".png", ".jpg", ".jpeg"}
MAX_IMAGES = 10


def check_screenshots(jc=None, ticket=None) -> list:
    WHITE_DIR.mkdir(parents=True, exist_ok=True)
    tried_jira = False
    while True:
        images = [
            f for f in sorted(WHITE_DIR.iterdir())
            if f.is_file() and f.suffix.lower() in IMAGE_EXTS
        ]
        if not images:
            if not tried_jira and jc is not None and ticket is not None:
                tried_jira = True
                _try_fetch_from_jira(jc, ticket)
                continue
            if utils.is_assume_yes():
                utils.die(
                    f"No images in {WHITE_DIR} and Jira auto-fetch failed; "
                    f"--yes set so cannot wait for manual drop."
                )
            print(f"No images found in {WHITE_DIR}")
            print("Drop up to 10 images (banner + screenshots) there, then press Enter.")
            input()
            continue
        if len(images) > MAX_IMAGES:
            raise SystemExit(
                f"Too many images ({len(images)}) in {WHITE_DIR}; max is {MAX_IMAGES}"
            )
        print(f"Found {len(images)} image(s) in {WHITE_DIR}:")
        for img in images:
            print(f"  - {img.name}")
        return images


def _try_fetch_from_jira(jc, ticket) -> bool:
    parent_key = getattr(ticket, "parent_key", "") or ""
    if not parent_key:
        print(f"  [warn] {ticket.key} has no parent ticket — skipping auto-fetch.")
        return False
    print(f"  Looking for design zip via parent {parent_key}...")
    result = jc.fetch_design_zip(parent_key)
    if not result:
        return False
    des_key, zip_bytes, filename = result
    print(f"  ✓ Got {filename} ({len(zip_bytes)} bytes) from {des_key}")
    try:
        written = _extract_images(zip_bytes, WHITE_DIR)
    except Exception as e:
        print(f"  [warn] Could not extract {filename}: {e}")
        return False
    if not written:
        print(f"  [warn] No usable images in {filename}")
        return False
    print(f"  ✓ Extracted {len(written)} image(s) from {filename}")
    return True


def _extract_images(zip_bytes: bytes, out_dir: Path) -> list:
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        for name in sorted(zf.namelist()):
            if name.endswith("/"):
                continue
            basename = Path(name).name
            if not basename:
                continue
            # macOS Finder zips ship resource forks under __MACOSX/ and as
            # ._<name> AppleDouble files; they keep the .png extension but are
            # not real PNGs and break Pillow downstream.
            if "__MACOSX" in name.replace("\\", "/").split("/"):
                continue
            if basename.startswith("._") or basename.startswith("."):
                continue
            if Path(basename).suffix.lower() not in IMAGE_EXTS:
                continue
            if "icon" in basename.lower():
                continue
            with zf.open(name) as src:
                data = src.read()
            if not _has_image_magic(data):
                print(
                    f"  [warn] {name!r} has image extension but no PNG/JPEG "
                    f"signature — skipping ({len(data)} bytes)"
                )
                continue
            dest = _unique_path(out_dir / basename, written)
            dest.write_bytes(data)
            written.append(dest)
    return written


def _has_image_magic(data: bytes) -> bool:
    return data.startswith(b"\x89PNG\r\n\x1a\n") or data.startswith(b"\xff\xd8\xff")


def _unique_path(path: Path, already_written: list) -> Path:
    if path not in already_written and not path.exists():
        return path
    stem, suffix = path.stem, path.suffix
    i = 1
    while True:
        candidate = path.with_name(f"{stem}_{i}{suffix}")
        if candidate not in already_written and not candidate.exists():
            return candidate
        i += 1
