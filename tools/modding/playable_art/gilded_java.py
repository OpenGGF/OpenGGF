"""Regenerate MICA's Gilded super-form tables in MicaGilded.java from gilded.yaml.

Purpose: gilded.yaml (written by presentation.py) is the single source of MICA's
Gilded palette cycles; the example mod's Java profile carries the same tables as
Mega Drive colour words. This keeps the two in step.
Inputs: examples/mica-and-talon/art/mica/presentation/gilded.yaml (JSON subset).
Origin: MICA character mod, 2026-11-08; added after the brighter Gilded ramp landed
in the YAML but not in the Java tables.

Usage: python3 -I tools/modding/playable_art/gilded_java.py [--check]
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
YAML = ROOT / "examples/mica-and-talon/art/mica/presentation/gilded.yaml"
JAVA = ROOT / "examples/mica-and-talon/src/main/java/mica/MicaGilded.java"
STEPS = [0, 36, 73, 109, 146, 182, 219, 255]


def md_word(rgb):
    r, g, b = (STEPS.index(c) * 2 for c in rgb)
    return (b << 8) | (g << 4) | r


def table(frames):
    rows = ["List.of(" + ",".join(hex(md_word(c)) for c in frame) + ")" for frame in frames]
    return "List.of(" + ",\n            ".join(rows) + ")"


def render(source, data):
    tables = {"fade": data["fade_frames"], "s2": data["cycles"]["s2"]["frames"],
              "s3k": data["cycles"]["s3k"]["frames"]}
    for name, frames in tables.items():
        pattern = re.compile(r"(private static List<List<Integer>> " + name + r"\(\) \{ return ).*?(; \})", re.S)
        source, count = pattern.subn(lambda m: m.group(1) + table(frames) + m.group(2), source)
        if count != 1:
            raise SystemExit(f"MicaGilded.{name}() not found exactly once")
    return source


def main(argv):
    data = json.loads(YAML.read_text())
    current = JAVA.read_text()
    wanted = render(current, data)
    if "--check" in argv:
        if current != wanted:
            print("MicaGilded.java is out of date with gilded.yaml; run gilded_java.py")
            return 1
        return 0
    JAVA.write_text(wanted)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
