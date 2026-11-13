#!/usr/bin/env python3
"""Assemble generated special-stage parts without redrawing the identity layer.

Inputs: original indexed component PNGs and measured native placement recipes.
Origin: MICA/TALON temporal-consistency correction, 2026-11-10. Native pixels and
raw GPT Image generations stay in scratch. This reproduces creator art only.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageFilter


def indexed(size, palette):
    image = Image.new("P", size)
    image.putpalette(sum(palette, []) + [0] * 720)
    return image


def read_indexed(path):
    with Image.open(path) as source:
        return source.copy()


def transformed(source, size, rotation=0):
    image = source.resize(tuple(size), Image.Resampling.NEAREST)
    if rotation:
        image = image.rotate(rotation, Image.Resampling.NEAREST, expand=True)
    return image


def rotate_material_light(image, angle, ramp):
    """Rotate generated detail/geometry and reproject its own broad light field.

    The contour and non-material features retain their original indices. Only
    the material's low-frequency shade field stays in screen coordinates;
    high-frequency generated scale/feather detail rotates with the geometry.
    """
    if not angle:
        return image.copy()
    ranks, mask = Image.new("L", image.size), Image.new("L", image.size)
    for y in range(image.height):
        for x in range(image.width):
            value = image.getpixel((x, y))
            if value in ramp:
                ranks.putpixel((x, y), ramp.index(value) * 50)
                mask.putpixel((x, y), 255)
    radius = max(2, image.width * .15)
    blur = ranks.filter(ImageFilter.GaussianBlur(radius))
    weights = mask.filter(ImageFilter.GaussianBlur(radius))
    result = image.rotate(angle, Image.Resampling.NEAREST, expand=True)
    rotated_blur = blur.rotate(angle, Image.Resampling.BILINEAR, expand=True)
    rotated_weights = weights.rotate(angle, Image.Resampling.BILINEAR, expand=True)
    fixed_blur = blur.resize(result.size, Image.Resampling.BILINEAR)
    fixed_weights = weights.resize(result.size, Image.Resampling.BILINEAR)
    for y in range(result.height):
        for x in range(result.width):
            value = result.getpixel((x, y))
            if (value in ramp[1:] and rotated_weights.getpixel((x, y)) > 30
                    and fixed_weights.getpixel((x, y)) > 30):
                old = rotated_blur.getpixel((x, y)) * 255 / rotated_weights.getpixel((x, y)) / 50
                new = fixed_blur.getpixel((x, y)) * 255 / fixed_weights.getpixel((x, y)) / 50
                rank = max(1, min(len(ramp) - 1, round(ramp.index(value) - old + new)))
                result.putpixel((x, y), ramp[rank])
    return result


def derive_curl(source, palette, allowed, transform, glint):
    """Derive a native-observed blur/glint phase from one generated curl."""
    image = source.copy()
    for point in transform["source_glint_pixels"]:
        image.putpixel(tuple(point), transform["under_glint"])
    if transform["median"]:
        rgb = image.convert("RGB").filter(ImageFilter.MedianFilter(transform["median"]))
        for y in range(image.height):
            for x in range(image.width):
                if source.getpixel((x, y)):
                    colour = rgb.getpixel((x, y))
                    image.putpixel((x, y), min(allowed, key=lambda k:
                        sum((v - palette[k][j]) ** 2 for j, v in enumerate(colour))))
    patch = glint.resize(tuple(transform["glint_size"]), Image.Resampling.NEAREST)
    px, py = transform["glint_position"]
    for y in range(patch.height):
        for x in range(patch.width):
            point = px + x, py + y
            if (patch.getpixel((x, y)) and 0 <= point[0] < source.width
                    and 0 <= point[1] < source.height and source.getpixel(point)):
                image.putpixel(point, patch.getpixel((x, y)))
    scale = transform["envelope_scale"]
    if scale != 1:
        image = image.resize((round(image.width * scale), round(image.height * scale)),
                             Image.Resampling.NEAREST)
    return image


def assemble(folder, document, frame):
    canvas = indexed((128, 128), document["palette"])
    for placement in frame["placements"]:
        if placement.get("kind") == "locked_body":
            try:
                from .stage_body_motion import render_body
            except ImportError:
                from stage_body_motion import render_body
            image, _, _ = render_body(folder, document, frame["body_motion"])
        else:
            source = read_indexed(folder / placement["part"])
            image = transformed(source, placement["size"], placement.get("rotation", 0))
        mask = image.point(lambda value: 255 if value else 0, "L")
        x, y = placement["position"]
        canvas.paste(image, (64 + x, 64 + y), mask)
    box = canvas.getbbox()
    if box is None:
        raise ValueError("Empty creator frame")
    result = canvas.crop(box)
    return result, [64 - box[0], 64 - box[1]]


def rebuild(folder, check=False):
    document = json.loads((folder / "native-pose-generation.json").read_text())
    for frame in document["frames"]:
        image, hotspot = assemble(folder, document, frame)
        if hotspot != frame["hotspot"] or list(image.size) != frame["dimensions"]:
            raise ValueError(f"Placement bounds changed: {frame['file']}")
        if len(set(image.get_flattened_data()) - {0}) > document["colour_budget"][frame["game"]]:
            raise ValueError(f"Native colour budget exceeded: {frame['file']}")
        target = folder / frame["file"]
        if check:
            with Image.open(target) as actual:
                if (actual.mode != "P" or actual.size != image.size
                        or list(actual.get_flattened_data()) != list(image.get_flattened_data())
                        or actual.getpalette()[:48] != image.getpalette()[:48]
                        or actual.info.get("transparency") != 0):
                    raise ValueError(f"Generated-parts assembly differs: {target}")
        else:
            image.save(target, bits=4, transparency=0)
    return len(document["frames"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    print(f"{'Checked' if args.check else 'Rebuilt'} {rebuild(args.folder, args.check)} locked-layer drawings")
