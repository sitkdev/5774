#!/usr/bin/env python3
"""
Populate each locale directory with screenshots copied from white/.
Run from the repo root or from iosApp/fastlane/.

Behaviour:
  • Detects inconsistent screenshot dimensions in white/ and resizes them to a
    single chosen target size before copying.
  • Validates the target size against App Store Connect's accepted resolutions
    and offers to resize to the closest supported size if it isn't.
  • Strips alpha channels (App Store rejects transparent PNGs) by flattening
    onto a white background.

Pass --yes / -y to skip all prompts (auto-accept defaults).

Requires Pillow:  pip install Pillow
"""

import argparse
import shutil
import sys
from collections import Counter
from pathlib import Path

LOCALES = [
    "en-GB"
]

# App Store Connect accepted screenshot resolutions, portrait orientation.
# Landscape variants (rotated) are accepted automatically.
APP_STORE_PORTRAIT_SIZES = {
    (1284, 2778),
}


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--yes", "-y",
        action="store_true",
        help="Skip all prompts (auto-accept default for size selection and App Store resize).",
    )
    return parser.parse_args()


args = parse_args()
ASSUME_YES = args.yes

script_dir = Path(__file__).parent
screenshots_dir = script_dir / "screenshots"
source_dir = script_dir / "white"

if not source_dir.exists():
    raise SystemExit(f"Source directory not found: {source_dir}")

source_files = sorted(f for f in source_dir.iterdir() if f.is_file())
if not source_files:
    raise SystemExit(f"No files found in {source_dir}")

# ── Pillow / size detection ───────────────────────────────────────────────────

def _pil():
    try:
        from PIL import Image
        return Image
    except ImportError:
        raise SystemExit(
            "Pillow is required for size detection/resizing/alpha stripping.\n"
            "Install it with:  pip install Pillow"
        )

def read_size(path: Path):
    Image = _pil()
    with Image.open(path) as img:
        return img.size  # (width, height)


def has_alpha(path: Path) -> bool:
    Image = _pil()
    with Image.open(path) as img:
        return (
            img.mode in ("RGBA", "LA")
            or (img.mode == "P" and "transparency" in img.info)
        )


def is_app_store_supported(size: tuple) -> bool:
    w, h = size
    return (w, h) in APP_STORE_PORTRAIT_SIZES or (h, w) in APP_STORE_PORTRAIT_SIZES


def closest_app_store_size(size: tuple) -> tuple:
    """Return the closest accepted size in the same orientation as `size`."""
    w, h = size
    is_landscape = w > h
    if is_landscape:
        candidates = {(ph, pw) for (pw, ph) in APP_STORE_PORTRAIT_SIZES}
    else:
        candidates = APP_STORE_PORTRAIT_SIZES
    return min(candidates, key=lambda s: (s[0] - w) ** 2 + (s[1] - h) ** 2)


# ── Read all screenshot sizes ─────────────────────────────────────────────────

size_map: dict[Path, tuple[int, int]] = {}
unreadable = []
for f in source_files:
    try:
        size_map[f] = read_size(f)
    except Exception as e:
        unreadable.append((f, e))

if unreadable:
    for f, e in unreadable:
        print(f"[warn]  Could not read dimensions of {f.name}: {e}")

size_counts = Counter(size_map.values())

# ── Resolve a single target size if there are mismatches ─────────────────────

target_size: tuple[int, int] | None = None

if len(size_counts) > 1:
    ranked = size_counts.most_common()

    print(f"\n⚠  Inconsistent screenshot sizes in white/ ({len(ranked)} distinct sizes found):\n")
    for i, (size, count) in enumerate(ranked):
        label = "  ← suggested (most common)" if i == 0 else ""
        print(f"  [{i + 1}]  {size[0]}×{size[1]}  —  {count} file{'s' if count != 1 else ''}{label}")

    if ASSUME_YES:
        target_size = ranked[0][0]
        print(f"\n--yes: auto-selecting most common size {target_size[0]}×{target_size[1]}")
    else:
        print()
        choice = input(
            f"Press Enter to use {ranked[0][0][0]}×{ranked[0][0][1]}, "
            f"or enter a number to choose a different size: "
        ).strip()

        if choice == "":
            target_size = ranked[0][0]
        else:
            try:
                idx = int(choice) - 1
                if not (0 <= idx < len(ranked)):
                    raise ValueError
                target_size = ranked[idx][0]
            except ValueError:
                raise SystemExit("Invalid choice. Aborting.")

    resized_count = sum(c for s, c in ranked if s != target_size)
    print(f"\nTarget size: {target_size[0]}×{target_size[1]}  "
          f"({resized_count} file{'s' if resized_count != 1 else ''} will be resized)\n")

elif len(size_counts) == 1:
    only_size = next(iter(size_counts))
    print(f"All {len(source_files)} screenshots are {only_size[0]}×{only_size[1]}.\n")

# ── App Store resolution check ────────────────────────────────────────────────

effective_size = target_size or (next(iter(size_counts)) if len(size_counts) == 1 else None)

if effective_size and not is_app_store_supported(effective_size):
    closest = closest_app_store_size(effective_size)
    bar = "!" * 72
    print()
    print(bar)
    print(f"!! WARNING: {effective_size[0]}×{effective_size[1]} is NOT an App Store-supported resolution.")
    print(f"!! Closest accepted size:  {closest[0]}×{closest[1]}")
    print(f"!! App Store Connect will reject screenshots at the current size.")
    print(bar)
    print()

    if ASSUME_YES:
        accept_resize = True
        print(f"--yes: auto-resizing to {closest[0]}×{closest[1]}")
    else:
        ans = input(
            f"Resize all screenshots to {closest[0]}×{closest[1]}? "
            f"[Y/n] (n = leave as-is): "
        ).strip().lower()
        accept_resize = ans in ("", "y", "yes")

    if accept_resize:
        target_size = closest
        print(f"\nTarget size updated → {target_size[0]}×{target_size[1]}\n")
    else:
        print("\nLeaving sizes as-is. Note: App Store will likely reject the upload.\n")

# ── Copy (resize + strip alpha) to each locale ───────────────────────────────

def _strip_alpha(img, Image):
    """Flatten transparency onto white. Returns an RGB image."""
    if img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info):
        rgba = img.convert("RGBA")
        bg = Image.new("RGB", rgba.size, (255, 255, 255))
        bg.paste(rgba, mask=rgba.split()[-1])
        return bg
    if img.mode != "RGB":
        return img.convert("RGB")
    return img


def copy_file(src: Path, dst: Path) -> None:
    Image = _pil()
    src_size = size_map.get(src)
    needs_resize = target_size is not None and src_size != target_size
    src_has_alpha = has_alpha(src)

    if not needs_resize and not src_has_alpha:
        shutil.copy2(src, dst)
        return

    LANCZOS = getattr(Image, "Resampling", Image).LANCZOS
    with Image.open(src) as img:
        out = img
        if needs_resize:
            out = out.resize(target_size, LANCZOS)
        if src_has_alpha:
            out = _strip_alpha(out, Image)
        out.save(dst)


for locale in LOCALES:
    target_dir = screenshots_dir / locale
    if target_dir.exists():
        print(f"[skip]  {locale}/ already exists — delete it first to overwrite")
        continue

    target_dir.mkdir(parents=True)
    for src in source_files:
        copy_file(src, target_dir / src.name)
    print(f"[done]  {locale}/ ({len(source_files)} files)")

print("\nAll done.")
