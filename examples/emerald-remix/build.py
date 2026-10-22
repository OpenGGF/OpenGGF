#!/usr/bin/env python3
"""Build Emerald Remix using the shared launcher; preserve target/emerald-remix/emerald-remix.jar.

Origin: Emerald Remix, 2026-10-21. Requires Java 25; the remix reads the player's ROM, Clover Valley is original.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "examples"))
from build_example import main

if __name__ == "__main__":
    main(default_name="emerald-remix", output=ROOT / "target/emerald-remix")
