#!/usr/bin/env python3
"""Build Super Sonic Bros. with the shared example launcher.

Usage:
  python3 examples/super-sonic-bros/build.py          # compile and package target/super-sonic-bros/super-sonic-bros.jar
  python3 examples/super-sonic-bros/build.py --run    # ...then launch the engine with the mod (JVM build)

Origin: Super Sonic Bros. example mod, 2026-10-19. Requires Java 25; all art and audio come from
the player's Sonic 3 & Knuckles ROM at runtime.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "examples"))
from build_example import main

if __name__ == "__main__":
    main(default_name="super-sonic-bros", output=ROOT / "target/super-sonic-bros")
