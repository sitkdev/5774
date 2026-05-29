import sys


_assume_yes = False


def set_assume_yes(value: bool) -> None:
    global _assume_yes
    _assume_yes = value


def is_assume_yes() -> bool:
    return _assume_yes


def confirm(message: str, default: bool = False) -> bool:
    if _assume_yes:
        print(f"{message} [auto-yes]")
        return True
    suffix = "[Y/n]" if default else "[y/N]"
    ans = input(f"{message} {suffix} ").strip().lower()
    if not ans:
        return default
    return ans in ("y", "yes")


def prompt(message: str, default: str = "") -> str:
    if _assume_yes:
        if default != "":
            print(f"{message} [auto: {default}]")
            return default
        die(f"--yes set but no default available for prompt: {message!r}")
    suffix = f" [{default}]" if default else ""
    ans = input(f"{message}{suffix}: ").strip()
    return ans or default


def section(title: str):
    print()
    print("=" * 72)
    print(f"  {title}")
    print("=" * 72)


def die(message: str):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)
