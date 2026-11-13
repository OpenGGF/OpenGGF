#!/usr/bin/env python3
"""Rebuild/check original oriented stage PNGs from selected indexed generation rows.

Inputs: character oriented-stage/native-pose-generation.json and locked palette.
Origin: 2026-11-10 MICA/TALON native-flip lighting correction. GPT Image raw outputs
and decoded native pose references stay in scratch; this tool neither paints nor
generates art. Runtime uses the PNGs through the official StageSpriteSet API.
"""
import argparse
import json
from pathlib import Path
from PIL import Image


def rebuild(folder: Path, check: bool = False) -> int:
    document = json.loads((folder / "native-pose-generation.json").read_text())
    count = 0
    for frame in document["frames"]:
        width, height = frame["dimensions"]
        rows = frame["rows"]
        if len(rows) != height or any(len(row) != width for row in rows):
            raise ValueError(f"Invalid indexed dimensions: {frame['file']}")
        image = Image.new("P", (width, height))
        image.putpalette(sum(document["palette"], []) + [0] * 720)
        image.putdata([int(pixel, 16) for row in rows for pixel in row])
        path = folder / frame["file"]
        if check:
            with Image.open(path) as actual:
                if (actual.mode != "P" or actual.size != image.size
                        or list(actual.get_flattened_data()) != list(image.get_flattened_data())
                        or actual.getpalette()[:48] != image.getpalette()[:48]
                        or actual.info.get("transparency") != 0):
                    raise ValueError(f"Selected generation differs: {path}")
        else:
            image.save(path, bits=4, transparency=0)
        count += 1
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    print(f"{'Checked' if args.check else 'Rebuilt'} {rebuild(args.folder, args.check)} original drawings")
