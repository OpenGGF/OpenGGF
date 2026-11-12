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


def motion_identity(folder, document, frame):
    try:
        from .stage_body_motion import render_body, principal_axis, mask_points
    except ImportError:
        from stage_body_motion import render_body, principal_axis, mask_points
    from PIL import Image
    motion = frame["body_motion"]
    body, labels, light = render_body(folder, document, motion)
    actual = Image.new("P", body.size)
    image = read_indexed(folder / frame["file"])
    actual.paste(image, (64 - frame["hotspot"][0], 64 - frame["hotspot"][1]))
    occlusion = Image.new("L", body.size)
    seen_body = False
    for placement in frame["placements"]:
        if placement.get("kind") == "locked_body":
            seen_body = True
            continue
        if seen_body:
            part = transformed(read_indexed(folder / placement["part"]), placement["size"],
                               placement.get("rotation", 0))
            mask = part.point(lambda value: 255 if value else 0, "L")
            occlusion.paste(255, (64 + placement["position"][0], 64 + placement["position"][1],
                                 64 + placement["position"][0] + part.width,
                                 64 + placement["position"][1] + part.height), mask)
    result = {}
    for role, prefix in [(2, "head_ear"), (1, "torso")]:
        expected = {(x, y) for y in range(128) for x in range(128)
                    if labels.getpixel((x, y)) & 3 == role and not occlusion.getpixel((x, y))}
        observed = {p for p in expected if actual.getpixel(p)}
        result[f"{prefix}_iou"] = len(expected & observed) / len(expected | observed)
        result["head_rgb_differences" if role == 2 else "torso_rgb_differences"] = sum(
            actual.getpixel(p) != body.getpixel(p) for p in expected)
    measured = principal_axis(mask_points(labels), motion["heading"])
    visible = [(x - 64 + .5, y - 64 + .5) for y in range(128) for x in range(128)
               if labels.getpixel((x, y)) & 8 and not occlusion.getpixel((x, y))
               and actual.getpixel((x, y))]
    visible_axis = principal_axis(visible, motion["heading"])
    native = motion["native"]
    native_lean, mod_lean = native["angle"] - motion["heading"], measured["angle"] - motion["heading"]
    result.update(native_angle=native["angle"], mod_angle=measured["angle"],
                  visible_mod_angle=visible_axis["angle"],
                  lean_error=measured["angle"] - native["angle"],
                  visible_lean_error=visible_axis["angle"] - native["angle"],
                  matching_sign=abs(native_lean) < .05 or native_lean * mod_lean > 0,
                  visible_matching_sign=abs(native_lean) < .05 or
                      native_lean * (visible_axis["angle"] - motion["heading"]) > 0,
                  head_dx=motion["shift"][0], head_dy=motion["shift"][1],
                  sway_error=measured["centroid"][0] - motion["desired_centroid"][0],
                  bob_error=measured["centroid"][1] - motion["desired_centroid"][1],
                  native_sway=native["centroid"][0] - motion["native_reference_centroid"][0],
                  native_bob=native["centroid"][1] - motion["native_reference_centroid"][1],
                  mod_sway=measured["centroid"][0] - motion["baseline_centroid"][0],
                  mod_bob=measured["centroid"][1] - motion["baseline_centroid"][1],
                  specular_dx=light["specular_delta"][0], specular_dy=light["specular_delta"][1])
    gaps = []
    for placement in frame["placements"]:
        if placement.get("role") not in ["calf", "upper_arm"]:
            continue
        part = transformed(read_indexed(folder / placement["part"]), placement["size"], placement.get("rotation", 0))
        ps = [(x + placement["position"][0] + .5, y + placement["position"][1] + .5)
              for x, y in points(part)]
        gaps.extend(min(math.dist(p, joint) for p in ps) for joint in placement["joints"])
    result["joint_gap_max"] = max(gaps, default=0)
    return result


def measure(folder):
    document = json.loads((folder / "native-pose-generation.json").read_text())
    rows = []
    for frame in document["frames"]:
        row = {k: frame[k] for k in ["game", "char", "id", "orientation", "layer"]}
        if frame["layer"] == "upright":
            canonical = document["canonical"][frame["game"]]
            if "body_motion" in frame:
                row.update(motion_identity(folder, document, frame))
            else:
                row.update(legacy_identity(folder, document, frame))
            errors, hand_errors, contacts = [], [], []
            for limb in frame["limb_landmarks"]:
                target = limb["native_bbox"]
                creator_box = limb["creator_bbox"]
                placement = next(p for p in frame["placements"]
                                 if p.get("part") is not None
                                 and ("glove-" in p["part"] if limb["kind"] == "glove" else
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


def legacy_identity(folder, document, frame):
    row = {}
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
    return row

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
