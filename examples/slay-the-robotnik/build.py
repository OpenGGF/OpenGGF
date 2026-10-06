#!/usr/bin/env python3
"""Build/package the Slay the Robotnik example mod against this checkout (Java 21 + Maven).

Usage:
  python3 examples/slay-the-robotnik/build.py          # compile and package target/examples/slay-the-robotnik/slay-the-robotnik.jar
  python3 examples/slay-the-robotnik/build.py --run    # ...then launch the engine with the mod as a development mod

A shortcut for `python3 examples/build_example.py slay-the-robotnik`, which does the work.
Origin: Slay the Robotnik example mod, 2026-10-05. No ROM data is included in the jar:
art and audio are read from the player's own Sonic 3 & Knuckles ROM at runtime.
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
    build_example.build("slay-the-robotnik", run=args.run, skip_engine=args.skip_engine)


if __name__ == "__main__":
    main()
