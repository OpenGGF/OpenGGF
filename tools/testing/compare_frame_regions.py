#!/usr/bin/env python3
"""Compare lockstep captures, rejecting changes outside explicitly owned regions.

Purpose: detect misplaced creator pixels as well as native-art remnants.
Inputs: stock, clean baseline and candidate PNG sequences; per-frame allowed
rectangles from an independent read-only capture ledger (exclusive right/bottom).
Origin: S2 ending pilot review, 2026-11-11. Comparison only; no gameplay input.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw


def difference_mask(left: Image.Image, right: Image.Image) -> Image.Image:
    if left.size != right.size:
        raise ValueError(f"Capture dimensions differ: {left.size} / {right.size}")
    r, g, b = ImageChops.difference(left.convert("RGB"), right.convert("RGB")).split()
    return ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda value: 255 if value else 0)


def outside_regions(mask: Image.Image, regions: list) -> Image.Image:
    result = mask.copy()
    draw = ImageDraw.Draw(result)
    for rect in regions:
        if len(rect) != 4 or any(type(value) is not int for value in rect):
            raise ValueError(f"Expected four integer rectangle coordinates: {rect}")
        left, top, right, bottom = rect
        if right <= left or bottom <= top:
            raise ValueError(f"Empty/inverted allowed rectangle: {rect}")
        draw.rectangle((left, top, right - 1, bottom - 1), fill=0)
    return result


def compare_frame(stock: Image.Image, baseline: Image.Image,
                  candidate: Image.Image, regions: list) -> dict:
    changed = difference_mask(candidate, baseline)
    non_stock = ImageChops.darker(changed, difference_mask(candidate, stock))
    outside = outside_regions(changed, regions)
    new_outside = outside_regions(non_stock, regions)
    return {
        "unexpected_pixels": outside.histogram()[255],
        "unexpected_bounds": outside.getbbox(),
        "new_non_stock_pixels_outside": new_outside.histogram()[255],
        "new_non_stock_bounds": new_outside.getbbox(),
    }


def scan(stock: Path, baseline: Path, candidate: Path, regions: dict,
         first: int, last: int) -> dict:
    if first < 0 or last < first:
        raise ValueError("Expected a nonnegative inclusive frame range")
    rows = []
    digest = hashlib.sha256()
    for frame in range(first, last + 1):
        if str(frame) not in regions:
            raise ValueError(f"Missing region ownership for frame {frame}")
        pictures = []
        digest.update(str(frame).encode())
        for directory in (stock, baseline, candidate):
            path = directory / f"{frame:06d}.png"
            with Image.open(path) as image:
                picture = image.convert("RGB")
                digest.update(str(picture.size).encode())
                digest.update(picture.tobytes())
                pictures.append(picture)
        rows.append({"frame": frame, **compare_frame(*pictures, regions[str(frame)])})
    failures = [row for row in rows if row["unexpected_pixels"]]
    return {"verdict": "FAIL" if failures else "PASS", "frames": len(rows),
            "first": first, "last": last, "triplet_pixels_sha256": digest.hexdigest(),
            "failed_frames": len(failures), "results": rows}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("stock", "baseline", "candidate", "regions", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--first", type=int, required=True)
    parser.add_argument("--last", type=int, required=True)
    args = parser.parse_args()
    result = scan(args.stock, args.baseline, args.candidate,
                  json.loads(args.regions.read_text()), args.first, args.last)
    result["inputs"] = {key: str(getattr(args, key).resolve())
                        for key in ("stock", "baseline", "candidate", "regions")}
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f'{result["verdict"]}: {result["frames"]} frames, '
          f'{result["failed_frames"]} frames change outside expected regions')
    return int(result["verdict"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
