#!/usr/bin/env python3
"""Measure native special-stage body PCA, sway, bob and second-moment spread.

Inputs: external indexed native pose PNGs and their mapping-hotspot inventory.
Origin: 2026-11-10 MICA/TALON body-tilt correction. No reference pixels are copied
into creator art. Output numeric tables stay external until incorporated into an
original-art recipe. The native motion is baked into the art, not an angle timer:
loc_907E (sonic3k.asm:11504-11538) / SSPlayer_Animate (s2.asm:69746-69789) select it.
"""
import argparse
import csv
import json
import math
from pathlib import Path
from PIL import Image
try:
    from .stage_body_motion import principal_axis
except ImportError:
    from stage_body_motion import principal_axis


def measure(references, inventory):
    rows = []
    for game in ["s3k", "s2"]:
        for character, native in [("mica", "sonic"), ("talon", "tails")]:
            for selector in (range(1, 9) if game == "s3k" else range(16)):
                record = next(r for r in inventory if r["game"] == game
                              and r["char"] == character and r["id"] == selector)
                with Image.open(references / f"{game}-{character}-{selector:02}.png") as source:
                    if source.mode != "P":
                        raise ValueError("Native references must retain their palette indices")
                    image = source.copy()
                variants = (["n", "h"] if game == "s3k" and selector in [6, 7, 8] else
                            ["n", "h", "v", "hv"] if game == "s2" and selector < 12 else
                            ["n", "h"] if game == "s2" else ["n"])
                for orientation in variants:
                    current = image.copy()
                    hx, hy = record["hotspot"]
                    heading = 0
                    if game == "s2":
                        if "h" in orientation:
                            current = current.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                            hx = current.width - hx
                        if "v" in orientation:
                            current = current.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                            hy = current.height - hy
                        group = 0 if selector < 4 else 4 if selector < 12 else 12
                        headings = ({"n": 0, "h": 0, "v": 180, "hv": 180} if group == 0 else
                                    {"n": 45, "h": -45, "v": 135, "hv": -135} if group == 4 else
                                    {"n": 90, "h": -90})
                        heading = headings[orientation]
                    angle = math.radians(heading)
                    ux, uy = math.sin(angle), -math.cos(angle)
                    pivot = ([0, 0 if selector == 1 else -8] if game == "s3k" else
                             [-8 * math.sin(angle), 8 * math.cos(angle)])
                    ramp = range(8, 12) if game == "s3k" else range(2, 8)
                    points = [(x - hx + .5, y - hy + .5)
                              for y in range(current.height) for x in range(current.width)
                              if current.getpixel((x, y)) in ramp
                              and (x - hx + .5 - pivot[0]) * ux + (y - hy + .5 - pivot[1]) * uy >= 0]
                    result = principal_axis(points, heading)
                    result.update(game=game, native=native, id=selector, orientation=orientation,
                                  heading=heading, pivot=pivot,
                                  bbox=[min(x for x, y in points) - .5,
                                        min(y for x, y in points) - .5,
                                        max(x for x, y in points) + .5,
                                        max(y for x, y in points) + .5])
                    rows.append(result)
    for row in rows:
        selector = 2 if row["game"] == "s3k" else 0 if row["id"] < 4 else 4 if row["id"] < 12 else 12
        orientation = "n" if row["game"] == "s3k" else row["orientation"]
        base = next(r for r in rows if r["game"] == row["game"] and r["native"] == row["native"]
                    and r["id"] == selector and r["orientation"] == orientation)
        row.update(sway=row["centroid"][0] - base["centroid"][0],
                   bob=row["centroid"][1] - base["centroid"][1],
                   major_ratio=row["major_std"] / base["major_std"],
                   minor_ratio=row["minor_std"] / base["minor_std"])
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    rows = measure(args.references, json.loads(args.inventory.read_text()))
    args.out.write_text(json.dumps(rows, indent=2) + "\n")
    flat = [{k: json.dumps(v) if isinstance(v, list) else v for k, v in r.items()} for r in rows]
    with args.out.with_suffix(".csv").open("w") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(flat[0]))
        writer.writeheader()
        writer.writerows(flat)
    print(f"Measured {len(rows)} native poses: {args.out}")
