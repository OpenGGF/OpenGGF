#!/usr/bin/env python3
"""Pose locked generated identities with native-measured body motion.

Inputs: original indexed head/torso components and numeric native PCA landmarks.
Origin: MICA/TALON special-stage body-tilt correction, 2026-11-10. This offline
RotSprite-style EPX/nearest pipeline never copies native pixels into creator art.
Geometry, relighting and screen-space specular placement are separate operations.
"""
from functools import lru_cache
import math
from pathlib import Path
from PIL import Image, ImageFilter

ORIGIN = 64
SIZE = 128
UPSCALE = 4


def principal_axis(points, heading=0):
    count = len(points)
    cx = sum(x for x, y in points) / count
    cy = sum(y for x, y in points) / count
    xx = sum((x - cx) ** 2 for x, y in points) / count
    xy = sum((x - cx) * (y - cy) for x, y in points) / count
    yy = sum((y - cy) ** 2 for x, y in points) / count
    discriminant = math.hypot(xx - yy, 2 * xy)
    major, minor = (xx + yy + discriminant) / 2, (xx + yy - discriminant) / 2
    vx, vy = ((xy, major - xx) if abs(xy) > 1e-10 else
              (1, 0) if xx > yy else (0, 1))
    radians = math.radians(heading)
    if vx * math.sin(radians) - vy * math.cos(radians) < 0:
        vx, vy = -vx, -vy
    angle = math.degrees(math.atan2(vx, -vy))
    angle = heading + (angle - heading + 180) % 360 - 180
    return dict(angle=angle, centroid=[cx, cy], major_std=math.sqrt(major),
                minor_std=math.sqrt(max(0, minor)), area=count)


def mask_points(labels, bit=8):
    return [(x - ORIGIN + .5, y - ORIGIN + .5)
            for y in range(SIZE) for x in range(SIZE) if labels.getpixel((x, y)) & bit]


def rotate_point(point, degrees):
    angle = math.radians(degrees)
    cs, sn = math.cos(angle), math.sin(angle)
    return [cs * point[0] - sn * point[1], sn * point[0] + cs * point[1]]


def posed_point(point, motion):
    pivot = motion["pivot"]
    value = rotate_point([point[j] - pivot[j] for j in range(2)], motion["rotation"])
    target_pivot = rotate_point(pivot, motion["heading"])
    return [value[j] + target_pivot[j] + motion["shift"][j] for j in range(2)]


def _epx(image, labels):
    """Scale2x, propagating anatomical labels with the selected colour pixel."""
    result = Image.new("P", (image.width * 2, image.height * 2))
    result.putpalette(image.getpalette())
    tagged = Image.new("L", result.size)
    src, tags, dst, out = image.load(), labels.load(), result.load(), tagged.load()
    for y in range(image.height):
        for x in range(image.width):
            e = (x, y)
            b, d = (x, max(0, y - 1)), (max(0, x - 1), y)
            f, h = (min(image.width - 1, x + 1), y), (x, min(image.height - 1, y + 1))
            choices = ((d if src[d] == src[b] else e, f if src[b] == src[f] else e,
                        d if src[d] == src[h] else e, f if src[h] == src[f] else e)
                       if src[b] != src[h] and src[d] != src[f] else (e,) * 4)
            for (dx, dy), point in zip(((0, 0), (1, 0), (0, 1), (1, 1)), choices):
                dst[x * 2 + dx, y * 2 + dy] = src[point]
                out[x * 2 + dx, y * 2 + dy] = tags[point]
    return result, tagged


def _components(points):
    points, groups = set(points), []
    while points:
        first = points.pop()
        todo, group = [first], {first}
        while todo:
            x, y = todo.pop()
            for dx in [-1, 0, 1]:
                for dy in [-1, 0, 1]:
                    point = x + dx, y + dy
                    if point in points:
                        points.remove(point)
                        group.add(point)
                        todo.append(point)
        groups.append(group)
    return groups


@lru_cache(maxsize=24)
def _source(folder, sources, palette, ramp, pivot, glint_indices):
    image = Image.new("P", (SIZE, SIZE))
    image.putpalette(list(palette) + [0] * 720)
    labels = Image.new("L", image.size)
    for role, filename, x, y, width, height, modified in sources:
        with Image.open(Path(folder) / filename) as original:
            part = original.resize((width, height), Image.Resampling.NEAREST)
        for py in range(height):
            for px in range(width):
                value = part.getpixel((px, py))
                if not value:
                    continue
                wx, wy = x + px, y + py
                image.putpixel((ORIGIN + wx, ORIGIN + wy), value)
                labels.putpixel((ORIGIN + wx, ORIGIN + wy),
                                (2 if role == "head" else 1) | (8 if wy + .5 <= pivot[1] else 0))
    original = image.copy()
    head_pixels = [(x, y) for y in range(SIZE) for x in range(SIZE)
                   if labels.getpixel((x, y)) & 3 == 2]
    head_box = _box(head_pixels)
    eligible = [(x, y) for x, y in head_pixels
                if image.getpixel((x, y)) in glint_indices
                and x < head_box[0] + (head_box[2] - head_box[0]) * .65
                and y < head_box[1] + (head_box[3] - head_box[1]) * .55]
    if not eligible:
        eligible = [(x, y) for x, y in head_pixels if image.getpixel((x, y)) == ramp[-1]
                    and y < head_box[1] + (head_box[3] - head_box[1]) * .55]
    group = max(_components(eligible), key=len)
    glint_box = _box(group)
    glint = Image.new("P", (glint_box[2] - glint_box[0], glint_box[3] - glint_box[1]))
    glint.putpalette(image.getpalette())
    for x, y in group:
        glint.putpixel((x - glint_box[0], y - glint_box[1]), image.getpixel((x, y)))
        image.putpixel((x, y), ramp[-2])
    glint_uv = [((glint_box[j] + glint_box[j + 2]) / 2 - head_box[j]) /
                (head_box[j + 2] - head_box[j]) for j in range(2)]
    fields = {}
    for role in [1, 2]:
        points = [(x, y) for y in range(SIZE) for x in range(SIZE)
                  if labels.getpixel((x, y)) & 3 == role]
        box = _box(points)
        rank, weight = Image.new("L", image.size), Image.new("L", image.size)
        for x, y in points:
            value = image.getpixel((x, y))
            if value in ramp:
                rank.putpixel((x, y), ramp.index(value) * 50)
                weight.putpixel((x, y), 255)
        radius = max(2, (box[2] - box[0]) * .15)
        fields[role] = (box, rank.filter(ImageFilter.GaussianBlur(radius)),
                        weight.filter(ImageFilter.GaussianBlur(radius)))
    high, tagged = image, labels
    for _ in range(2):
        high, tagged = _epx(high, tagged)
    return original, labels, high, tagged, fields, glint, glint_uv


def _box(points):
    return [min(x for x, y in points), min(y for x, y in points),
            max(x for x, y in points) + 1, max(y for x, y in points) + 1]


def source_layers(folder, document, motion):
    sources = tuple((p["role"], p["part"], *p["position"], *p["size"],
                     (folder / p["part"]).stat().st_mtime_ns) for p in motion["source"])
    return _source(str(folder), sources, tuple(sum(document["palette"], [])),
                   tuple(motion["ramp"]), tuple(motion["pivot"]), tuple(motion["glint_indices"]))


def _inverse(motion, scale):
    angle = math.radians(motion["rotation"])
    cs, sn = math.cos(angle), math.sin(angle)
    pivot = motion["pivot"]
    out = rotate_point(pivot, motion["heading"])
    out = [scale * (ORIGIN + out[j] + motion["shift"][j]) for j in range(2)]
    return (cs, sn, scale * (ORIGIN + pivot[0]) - cs * out[0] - sn * out[1],
            -sn, cs, scale * (ORIGIN + pivot[1]) + sn * out[0] - cs * out[1])


def render_body(folder, document, motion, relight=True):
    original, source_labels, high, tagged, fields, glint, glint_uv = source_layers(folder, document, motion)
    inverse = _inverse(motion, UPSCALE)
    image = high.transform(high.size, Image.Transform.AFFINE, inverse,
                           Image.Resampling.NEAREST).resize((SIZE, SIZE), Image.Resampling.NEAREST)
    labels = tagged.transform(tagged.size, Image.Transform.AFFINE, inverse,
                              Image.Resampling.NEAREST).resize((SIZE, SIZE), Image.Resampling.NEAREST)
    if not relight:
        return image, labels, {}
    ramp = motion["ramp"]
    for role, (source_box, field, weight) in fields.items():
        points = [(x, y) for y in range(SIZE) for x in range(SIZE)
                  if labels.getpixel((x, y)) & 3 == role]
        box = _box(points)
        fixed_field = field.crop(tuple(source_box)).resize((box[2] - box[0], box[3] - box[1]), Image.Resampling.BILINEAR)
        fixed_weight = weight.crop(tuple(source_box)).resize(fixed_field.size, Image.Resampling.BILINEAR)
        old_field = field.transform(field.size, Image.Transform.AFFINE, _inverse(motion, 1), Image.Resampling.BILINEAR)
        old_weight = weight.transform(weight.size, Image.Transform.AFFINE, _inverse(motion, 1), Image.Resampling.BILINEAR)
        for x, y in points:
            value = image.getpixel((x, y))
            if value not in ramp or any(not image.getpixel((x + dx, y + dy))
                                       for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]):
                continue
            fw, ow = fixed_weight.getpixel((x - box[0], y - box[1])), old_weight.getpixel((x, y))
            if fw > 30 and ow > 30:
                before = old_field.getpixel((x, y)) * 255 / ow / 50
                after = fixed_field.getpixel((x - box[0], y - box[1])) * 255 / fw / 50
                rank = max(0, min(len(ramp) - 1, round(ramp.index(value) - before + after)))
                image.putpixel((x, y), ramp[rank])
    head = [(x, y) for y in range(SIZE) for x in range(SIZE)
            if labels.getpixel((x, y)) & 3 == 2]
    box = _box(head)
    desired = [round(box[j] + glint_uv[j] * (box[j + 2] - box[j]) - glint.size[j] / 2) for j in range(2)]
    patch = [(x, y, glint.getpixel((x, y))) for y in range(glint.height)
             for x in range(glint.width) if glint.getpixel((x, y))]
    centre = [sum(p[j] for p in head) / len(head) for j in range(2)]
    candidates = []
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            pos = desired[0] + dx, desired[1] + dy
            fitted = [(pos[0] + x, pos[1] + y, value) for x, y, value in patch
                      if labels.getpixel((pos[0] + x, pos[1] + y)) & 3 == 2
                      and image.getpixel((pos[0] + x, pos[1] + y)) in ramp
                      and all(image.getpixel((pos[0] + x + sx, pos[1] + y + sy))
                              for sx, sy in [(0, 1), (0, -1), (1, 0), (-1, 0)])]
            if not fitted:
                continue
            delta = [sum(p[j] for p in fitted) / len(fitted) - centre[j] for j in range(2)]
            hemisphere_penalty = 10000 if any(v >= -.25 for v in delta) else 0
            candidates.append((hemisphere_penalty + (len(patch) - len(fitted)) * 100
                               + dx * dx + dy * dy, fitted))
    fitted = min(candidates, key=lambda c: c[0])[1]
    for x, y, value in fitted:
        image.putpixel((x, y), value)
        labels.putpixel((x, y), labels.getpixel((x, y)) | 4)
    glint_delta = [sum(p[j] for p in fitted) / len(fitted) - centre[j] for j in range(2)]
    return image, labels, dict(specular_delta=glint_delta, specular_pixels=len(fitted))
