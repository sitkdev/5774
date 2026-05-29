import random
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]

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
        "org.gradle.configuration-cache=true",
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
