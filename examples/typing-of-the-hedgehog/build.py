#!/usr/bin/env python3
"""Build/package the Typing of the Hedgehog example mod against this checkout (Java 21 + Maven).

Usage:
  python3 examples/typing-of-the-hedgehog/build.py          # compile and package target/examples/typing-of-the-hedgehog/typing-of-the-hedgehog.jar
  python3 examples/typing-of-the-hedgehog/build.py --run    # ...then launch the engine with the mod as a development mod

A shortcut for `python3 examples/build_example.py typing-of-the-hedgehog`, which does the work.
Origin: Typing of the Hedgehog example mod, 2026-10-09. No ROM data is included in the jar:
art, music and sound effects are read from the player's own Sonic ROMs at runtime; the jar's
only audio is the mod's original voice bank.
"""
import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_example  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", action="store_true", help="launch the JVM engine with this development mod")
    parser.add_argument("--skip-engine", action="store_true", help="reuse the existing engine build in target/")
    args = parser.parse_args()
    build_example.build("typing-of-the-hedgehog", run=args.run, skip_engine=args.skip_engine)


if __name__ == "__main__":
    main()
