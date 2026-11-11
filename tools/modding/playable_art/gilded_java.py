"""Regenerate MicaGilded's art-side palette table from the production gilded.yaml.

The historical command name is retained for existing callers. Palette rows are
now data; form gates, native physics and timing decisions remain in Java.
"""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
YAML = ROOT / "examples/mica-and-talon/production/mica/presentation/gilded.yaml"
TABLE = ROOT / "examples/mica-and-talon/art/mica/powered/palettes.txt"
STEPS = [0, 36, 73, 109, 146, 182, 219, 255]


def md_word(rgb):
    r, g, b = (STEPS.index(value) * 2 for value in rgb)
    return (b << 8) | (g << 4) | r


def render(data):
    banks = {"fade": data["fade_frames"], "s2": data["cycles"]["s2"]["frames"],
             "s3k": data["cycles"]["s3k"]["frames"]}
    rows = ["# bank followed by sixteen Mega Drive colour words, in animation order."]
    for bank, frames in banks.items():
        rows.extend(bank + " " + " ".join(f"{md_word(color):03X}" for color in frame) for frame in frames)
    return "\n".join(rows) + "\n"


def main(argv):
    wanted = render(json.loads(YAML.read_text()))
    if "--check" in argv:
        if TABLE.read_text() != wanted:
            print("Palette table is out of date; run gilded_java.py")
            return 1
        return 0
    TABLE.write_text(wanted)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
