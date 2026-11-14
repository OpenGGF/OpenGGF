#!/usr/bin/env python3
"""Storyboard art for the crash screen's three MICA gags.

Purpose: re-poses approved MICA sprite frames with hand-placed props, one picture per
`com.openggf.game.crash.FaultKind`, as the rough storyboard the MICA art process starts from.
Generated replacements follow the native-pose method and replace these files in place.
Inputs: examples/mica-and-talon/art/mica/sheet/frames/{death-00,drown-01,balance-00}.png
Outputs: src/main/resources/crash/mica-{engine,rewind,level-load}.png (56x56 drawn, shipped 2x)
Origin: crash screen feature, 2026-11-14. Run from the repository root; --check compares.
"""
from __future__ import annotations

import argparse
import io
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
FRAMES = ROOT / 'examples/mica-and-talon/art/mica/sheet/frames'
OUT = ROOT / 'src/main/resources/crash'
SIZE = 56
SCALE = 2

INK = (0, 0, 0, 255)
BOMB = (36, 36, 48, 255)
BOMB_SHINE = (146, 146, 170, 255)
FUSE = (146, 109, 73, 255)
SPARK = (255, 219, 73, 255)
SPARK_HOT = (255, 255, 219, 255)
FLAME = (255, 109, 36, 255)
TAPE = (58, 40, 36, 255)
TAPE_GLINT = (130, 100, 80, 255)
CASSETTE = (40, 40, 52, 255)
LABEL = (219, 219, 200, 255)
GRASS = (36, 182, 73, 255)
GRASS_DARK = (0, 109, 36, 255)
SOIL = (182, 109, 36, 255)
SOIL_DARK = (109, 73, 36, 255)
MISSING_A = (255, 0, 219, 255)
MISSING_B = (24, 0, 36, 255)


def frame(name: str) -> Image.Image:
    return Image.open(FRAMES / f'{name}.png').convert('RGBA')


def outline(layer: Image.Image) -> Image.Image:
    """One-pixel ink silhouette around a prop layer, as the sprite pipeline inks sprites."""
    alpha = layer.getchannel('A')
    inked = Image.new('RGBA', layer.size)
    px, a = inked.load(), alpha.load()
    w, h = layer.size
    for y in range(h):
        for x in range(w):
            if a[x, y]:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and a[x + dx, y + dy]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = INK
    inked.alpha_composite(layer)
    return inked


def disc(draw: ImageDraw.ImageDraw, cx: int, cy: int, r: int, fill) -> None:
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)


def spark(draw: ImageDraw.ImageDraw, x: int, y: int) -> None:
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0), (-1, -1), (1, 1), (1, -1), (-1, 1)):
        draw.point((x + dx, y + dy), fill=FLAME if abs(dx) + abs(dy) == 2 and dx and dy else SPARK)
    draw.point((x, y), fill=SPARK_HOT)


def engine() -> Image.Image:
    """MICA left holding the bomb over her head: the Atari ST homage."""
    canvas = Image.new('RGBA', (SIZE, SIZE))
    mica = frame('death-00')
    mx, my = (SIZE - mica.width) // 2, SIZE - mica.height
    props = Image.new('RGBA', (SIZE, SIZE))
    draw = ImageDraw.Draw(props)
    cx, cy = mx + 17, my - 2
    disc(draw, cx, cy, 7, BOMB)
    draw.rectangle((cx + 3, cy - 9, cx + 6, cy - 6), fill=BOMB)
    for point in ((cx - 3, cy - 3), (cx - 4, cy - 2), (cx - 2, cy - 4)):
        draw.point(point, fill=BOMB_SHINE)
    for point in ((cx + 6, cy - 10), (cx + 7, cy - 11), (cx + 8, cy - 11), (cx + 9, cy - 10)):
        draw.point(point, fill=FUSE)
    canvas.alpha_composite(mica, (mx, my))
    canvas.alpha_composite(outline(props))
    spark_layer = Image.new('RGBA', (SIZE, SIZE))
    spark(ImageDraw.Draw(spark_layer), cx + 11, cy - 11)
    canvas.alpha_composite(spark_layer)
    return canvas


def rewind() -> Image.Image:
    """MICA tangled in tape spooled out of a cassette."""
    canvas = Image.new('RGBA', (SIZE, SIZE))
    mica = frame('drown-01')
    mx, my = (SIZE - mica.width) // 2 - 4, SIZE - mica.height - 4
    canvas.alpha_composite(mica, (mx, my))
    tape = Image.new('RGBA', (SIZE, SIZE))
    draw = ImageDraw.Draw(tape)
    # One loop pins her arms at the elbows, one wraps her waist; the tape runs back to the cassette.
    loops = [(mx + 2, my + 13, mx + 32, my + 21), (mx + 4, my + 23, mx + 30, my + 31)]
    for box in loops:
        draw.ellipse(box, outline=TAPE, width=2)
    draw.line([(mx + 29, my + 28), (mx + 33, my + 34), (mx + 36, my + 38), (SIZE - 10, SIZE - 8)], fill=TAPE, width=2)
    pixels = tape.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if pixels[x, y][3] and (x + y) % 3 == 0:
                pixels[x, y] = TAPE_GLINT
    canvas.alpha_composite(outline(tape))
    cassette = Image.new('RGBA', (SIZE, SIZE))
    cd = ImageDraw.Draw(cassette)
    x0, y0 = SIZE - 17, SIZE - 9
    cd.rectangle((x0, y0, x0 + 14, y0 + 7), fill=CASSETTE)
    cd.rectangle((x0 + 2, y0 + 1, x0 + 12, y0 + 3), fill=LABEL)
    for rx in (x0 + 4, x0 + 10):
        cd.point((rx, y0 + 5), fill=LABEL)
    canvas.alpha_composite(outline(cassette))
    return canvas


def level_load() -> Image.Image:
    """MICA teetering where the level stops, its missing tiles falling away."""
    canvas = Image.new('RGBA', (SIZE, SIZE))
    ground = Image.new('RGBA', (SIZE, SIZE))
    draw = ImageDraw.Draw(ground)
    top = SIZE - 12
    edge = 28
    draw.rectangle((0, top, edge - 1, SIZE - 1), fill=SOIL)
    for x in range(0, edge, 8):
        for y in range(top + 4, SIZE, 8):
            draw.rectangle((x + 1, y + 1, x + 3, y + 3), fill=SOIL_DARK)
    draw.rectangle((0, top, edge - 1, top + 2), fill=GRASS)
    for x in range(0, edge, 3):
        draw.point((x, top + 3), fill=GRASS_DARK)
    canvas.alpha_composite(outline(ground))
    tiles = Image.new('RGBA', (SIZE, SIZE))
    td = ImageDraw.Draw(tiles)
    for tx, ty, size in ((edge + 4, top + 4, 6), (edge + 13, top - 6, 5), (edge + 9, SIZE - 6, 6), (edge + 18, top + 7, 4)):
        for y in range(size):
            for x in range(size):
                td.point((tx + x, ty + y), fill=MISSING_A if (x // 2 + y // 2) % 2 == 0 else MISSING_B)
    canvas.alpha_composite(outline(tiles))
    mica = frame('balance-00')
    canvas.alpha_composite(mica, (edge - mica.width + 7, top - mica.height + 1))
    return canvas


SCENES = {'mica-engine.png': engine, 'mica-rewind.png': rewind, 'mica-level-load.png': level_load}


def render(build) -> bytes:
    image = build().resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)
    out = io.BytesIO()
    image.save(out, format='PNG', optimize=True)
    return out.getvalue()


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='fail when a committed picture differs')
    args = parser.parse_args(argv)
    stale = []
    for name, build in SCENES.items():
        target = OUT / name
        data = render(build)
        if args.check:
            if not target.exists() or Image.open(target).tobytes() != Image.open(io.BytesIO(data)).tobytes():
                stale.append(name)
        else:
            OUT.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    if stale:
        print('Stale crash art: ' + ', '.join(stale), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
