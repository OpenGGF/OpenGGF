#!/usr/bin/env python3
"""Build Sonic Survivors using the shared launcher; preserve target/sonic-survivors/sonic-survivors.jar.

Origin: mod audit remediation 2026-10-12. Requires Java 21; assets use the player's ROM.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "examples"))
from build_example import main

if __name__ == "__main__":
    main(default_name="sonic-survivors", output=ROOT / "target/sonic-survivors")
