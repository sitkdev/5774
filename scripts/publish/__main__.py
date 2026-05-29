import argparse

from . import utils
from .flow import run


def main():
    parser = argparse.ArgumentParser(prog="python -m scripts.publish")
    parser.add_argument(
        "--yes", "-y",
        action="store_true",
        help="Skip all confirmations and assume yes (fully automatic run).",
    )
    args = parser.parse_args()
    if args.yes:
        utils.set_assume_yes(True)
    run()


if __name__ == "__main__":
    main()
