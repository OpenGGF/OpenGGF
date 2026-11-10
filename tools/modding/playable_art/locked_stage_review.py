#!/usr/bin/env python3
"""Measure locked identity reuse and generated limb landmarks independently.

Inputs: a consistent-stage generation manifest and its original indexed parts.
Origin: MICA/TALON owner consistency review, 2026-11-10. The native bounding
landmarks in the recipe are numeric observations, never decoded native pixels.
Reports component measurements before ordinary limb/body occlusion separately
from the final image; a fitted rectangle alone is not a measured hand centroid.
"""
import argparse
import csv
import json
import math
from pathlib import Path
try:
    from .locked_stage import transformed, read_indexed
except ImportError:
    from locked_stage import transformed, read_indexed


def points(image, values=None):
    return [(x, y) for y in range(image.height) for x in range(image.width)
            if image.getpixel((x, y)) and (values is None or image.getpixel((x, y)) in values)]


def bounds(ps):
    return [min(x for x, y in ps), min(y for x, y in ps),
            max(x for x, y in ps) + 1, max(y for x, y in ps) + 1]


def measure(folder):
    document = json.loads((folder / "native-pose-generation.json").read_text())
    rows = []
    for frame in document["frames"]:
        row = {k: frame[k] for k in ["game", "char", "id", "orientation", "layer"]}
        if frame["layer"] == "upright":
            canonical = document["canonical"][frame["game"]]
            head = next(p for p in frame["placements"] if "-head-angle" in p["part"])
            source = read_indexed(folder / head["part"])
            reference = read_indexed(folder / canonical["head"])
            # S2 orientation templates are deterministic derivatives of this
            # same canonical; compare reuse within the corresponding angle.
            if frame["head_angle"]:
                reference = read_indexed(folder / f'parts/{frame["game"]}-head-angle{frame["head_angle"]}.png')
            actual = transformed(source, head["size"], head.get("rotation", 0))
            a, b = set(points(actual)), set(points(reference))
            row["head_ear_iou"] = len(a & b) / len(a | b)
            row["head_rgb_differences"] = sum(x != y for x, y in zip(actual.get_flattened_data(), reference.get_flattened_data()))
            torso = next(p for p in frame["placements"] if "-torso-angle" in p["part"])
            actual_torso = transformed(read_indexed(folder / torso["part"]), torso["size"])
            reference_torso = read_indexed(folder / canonical["torso"] if not frame["head_angle"] else
                folder / f'parts/{frame["game"]}-torso-angle{frame["head_angle"]}.png')
            a, b = set(points(actual_torso)), set(points(reference_torso))
            row["torso_iou"] = len(a & b) / len(a | b)
            row["torso_rgb_differences"] = sum(x != y for x, y in zip(
                actual_torso.get_flattened_data(), reference_torso.get_flattened_data()))
            row["head_dx"], row["head_dy"] = frame["head_offset"]
            errors, hand_errors, contacts = [], [], []
            for limb in frame["limb_landmarks"]:
                target = limb["native_bbox"]
                creator_box = limb["creator_bbox"]
                placement = next(p for p in frame["placements"]
                                 if ("glove-" in p["part"] if limb["kind"] == "glove" else
                                     any(s in p["part"] for s in ["-boot.", "-boot-", "-sole.", "-sole-"]))
                                 and p["position"] == creator_box[:2]
                                 and p["size"] == [target[2] - target[0], target[3] - target[1]])
                image = transformed(read_indexed(folder / placement["part"]), placement["size"])
                ps = points(image)
                measured = [v + creator_box[i % 2] for i, v in enumerate(bounds(ps))]
                errors.append(max(abs(a - b) for a, b in zip(measured, target)))
                if limb["kind"] == "glove":
                    white = points(image, [12])
                    if white and "native_white_centroid" in limb:
                        centre = [sum(p[j] for p in white) / len(white) + creator_box[j] for j in range(2)]
                        hand_errors.append(math.dist(centre, limb["native_white_centroid"]))
                elif "native_contact" in limb:
                    occupied = set(ps)
                    edge = [p for p in ps if any((p[0] + dx, p[1] + dy) not in occupied
                                                for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)])]
                    contacts.append(min(math.dist([p[j] + creator_box[j] for j in range(2)],
                                                  limb["native_contact"]) for p in edge))
            row["limb_edge_error_max"] = max(errors, default=0)
            row["glove_centroid_error_max"] = max(hand_errors, default=0)
            row["shoe_contact_error_max"] = max(contacts, default=0)
            row["visible_native_limbs"] = len(frame["limb_landmarks"])
        rows.append(row)
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    rows = measure(args.folder)
    fields = list(dict.fromkeys(k for r in rows for k in r))
    with args.out.open("w") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Measured {len(rows)} frames: {args.out}")
