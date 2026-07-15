import plistlib
import random
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]

# Info.plist paths we never want to treat as the main app target's plist.
_INFO_PLIST_SKIP_DIRS = ("Pods", "build", "DerivedData", ".git", "fastlane")

REVIEW_NOTES_POOL = [
    "No special review instructions.",
    "No additional information needed for review.",
    "The app does not require login or special setup.",
    "No account or credentials needed to test the app.",
    "All features are accessible on first launch - no setup required.",
    "App is fully functional on launch; no login or sign-up needed.",
    "Nothing special is required to review the app.",
    "App does not require any special access or configuration to be reviewed.",
]


def set_bundle_and_team(bundle: str, team_id: str):
    xc = PROJECT_ROOT / "iosApp" / "Configuration" / "Config.xcconfig"
    text = xc.read_text(encoding="utf-8")
    text, n1 = re.subn(r"^TEAM_ID=.*$", f"TEAM_ID={team_id}", text, flags=re.M)
    text, n2 = re.subn(
        r"^PRODUCT_BUNDLE_IDENTIFIER=.*$",
        f"PRODUCT_BUNDLE_IDENTIFIER={bundle}",
        text,
        flags=re.M,
    )
    if n1 == 0 or n2 == 0:
        raise SystemExit(f"Failed to update {xc} (team={n1}, bundle={n2})")
    xc.write_text(text, encoding="utf-8")

    pbx = PROJECT_ROOT / "iosApp" / "iosApp.xcodeproj" / "project.pbxproj"
    text = pbx.read_text(encoding="utf-8")
    text, bn = re.subn(
        r"PRODUCT_BUNDLE_IDENTIFIER = [^;]+;",
        f"PRODUCT_BUNDLE_IDENTIFIER = {bundle};",
        text,
    )
    text, tn = re.subn(
        r'DEVELOPMENT_TEAM = "[^"]*";',
        f'DEVELOPMENT_TEAM = "{team_id}";',
        text,
    )
    if tn == 0:
        raise SystemExit(f"No DEVELOPMENT_TEAM line found in {pbx}")
    if bn == 0:
        def _insert(m):
            indent = m.group(1)
            return f'{m.group(0)}\n{indent}PRODUCT_BUNDLE_IDENTIFIER = {bundle};'
        text, ins = re.subn(
            r'^([ \t]*)DEVELOPMENT_TEAM = "[^"]*";',
            _insert,
            text,
            flags=re.M,
        )
        if ins == 0:
            raise SystemExit(f"Failed to insert PRODUCT_BUNDLE_IDENTIFIER in {pbx}")
    pbx.write_text(text, encoding="utf-8")


def _find_info_plist() -> Path:
    """Locate the main app target's Info.plist in the consumer checkout.

    The standard KMP iOS template keeps it at iosApp/iosApp/Info.plist; fall back
    to the shallowest Info.plist under iosApp/ (skipping Pods/build/etc.) so we
    still find it in projects that renamed the target.
    """
    ios = PROJECT_ROOT / "iosApp"
    preferred = ios / "iosApp" / "Info.plist"
    if preferred.is_file():
        return preferred
    candidates = [
        p for p in ios.rglob("Info.plist")
        if not any(part in _INFO_PLIST_SKIP_DIRS for part in p.parts)
    ]
    if not candidates:
        raise SystemExit(
            f"No Info.plist found under {ios}; cannot set "
            "ITSAppUsesNonExemptEncryption."
        )
    # Shallowest path wins — the app target's plist sits near the top, test/
    # framework plists are nested deeper.
    candidates.sort(key=lambda p: len(p.parts))
    if len(candidates) > 1:
        print(f"  • Multiple Info.plist found; using {candidates[0]}")
    return candidates[0]


def set_info_plist_encryption():
    """Ensure ITSAppUsesNonExemptEncryption is present in the app's Info.plist.

    Older projects omit the key, which makes App Store submission impossible: with
    no declaration Apple demands the export-compliance answer interactively, which
    the pipeline can't provide. We add it as `false` only when it's ABSENT — if the
    dev already declared a value, we leave their choice untouched.
    """
    plist = _find_info_plist()
    with plist.open("rb") as fh:
        data = plistlib.load(fh)
    key = "ITSAppUsesNonExemptEncryption"
    if key in data:
        print(f"  • {key} already in {plist.name} ({data[key]!r}); leaving as-is")
        return
    data[key] = False
    with plist.open("wb") as fh:
        plistlib.dump(data, fh)
    print(f"  ✓ {plist} → {key}=false")


def set_codemagic_group(ticket_number: str):
    path = PROJECT_ROOT / "codemagic.yaml"
    text = path.read_text(encoding="utf-8")
    new_text, n = re.subn(r'- "9999"', f'- "{ticket_number}"', text)
    if n == 0 and f'- "{ticket_number}"' not in text:
        raise SystemExit(f'Could not find \'- "9999"\' in {path}')
    path.write_text(new_text, encoding="utf-8")


def set_fastfile_review_info(first: str, last: str, email: str, phone: str):
    path = PROJECT_ROOT / "iosApp" / "fastlane" / "Fastfile"
    text = path.read_text(encoding="utf-8")
    notes = random.choice(REVIEW_NOTES_POOL)
    new_block = (
        "app_review_information: {\n"
        f'          first_name: "{first}",\n'
        f'          last_name: "{last}",\n'
        f'          phone_number: "{phone}",\n'
        f'          email_address: "{email}",\n'
        f'          notes: "{notes}"\n'
        "        }"
    )
    new_text, n = re.subn(
        r"app_review_information:\s*\{[^}]*\}",
        lambda _m: new_block,
        text,
        count=1,
        flags=re.S,
    )
    if n == 0:
        raise SystemExit(f"app_review_information block not found in {path}")
    path.write_text(new_text, encoding="utf-8")


def set_gradle_properties():
    path = PROJECT_ROOT / "gradle.properties"
    new_section_lines = [
        "#Gradle",
        "org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnExit -Dfile.encoding=UTF-8",
        "org.gradle.configuration-cache=false",
        "org.gradle.caching=true",
        "org.gradle.daemon=false",
    ]
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    i = 0
    replaced = False
    while i < len(lines):
        if lines[i].strip() == "#Gradle":
            out.extend(new_section_lines)
            replaced = True
            i += 1
            while i < len(lines) and not re.match(r"^#\w", lines[i]):
                i += 1
            if i < len(lines):
                out.append("")
            continue
        out.append(lines[i])
        i += 1
    if not replaced:
        raise SystemExit(f"#Gradle section not found in {path}")
    text = "\n".join(out)
    if not text.endswith("\n"):
        text += "\n"
    path.write_text(text, encoding="utf-8")
