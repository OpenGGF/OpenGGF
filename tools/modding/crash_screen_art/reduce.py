#!/usr/bin/env python3
"""Reduce the crash screen's three MICA gag drawings to the shipped pictures.

Purpose: one picture per `com.openggf.game.crash.FaultKind`, drawn at the crash page's
112x112 art box in MICA's sprite palette plus the gags' prop colours.
Inputs: tools/modding/crash_screen_art/{engine,rewind,level-load}.png, GPT Image drawings
(Sol, 2026-11-14) made fresh from MICA's concept art and her death-00, drown-01 and
balance-00 sprites: flat colour, heavy near-black interior lines, pure white background.
Outputs: src/main/resources/crash/mica-{engine,rewind,level-load}.png.
Method: key out the white background from the edges, map every source pixel to the
palette, then take each output pixel's block majority. A block that is at least
INK_SHARE line ink stays ink, so the drawings' interior lines survive the reduction;
the silhouette is inked one pixel inside, as the sprite pipeline does.
Origin: crash screen feature, 2026-11-14. Run from the repository root; --check compares.
Requires Pillow.
"""
from __future__ import annotations

import argparse
import io
import sys
from collections import Counter, deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SOURCES = Path(__file__).resolve().parent
OUT = ROOT / 'src/main/resources/crash'
GAGS = ('engine', 'rewind', 'level-load')
SIZE = 112
BOX = SIZE - 2
INK_SHARE = 0.35
WHITE_TOLERANCE = 40

INK = (36, 36, 36)
MICA = [INK, (0, 73, 73), (0, 109, 109), (0, 146, 109), (36, 182, 146), (109, 219, 182),
        (255, 219, 146), (219, 182, 109), (182, 146, 109), (255, 73, 73), (182, 36, 36),
        (255, 255, 255), (219, 219, 219), (255, 146, 146), (73, 36, 0)]
PROPS = [(73, 73, 109), (146, 146, 182), (255, 219, 36), (255, 146, 0), (146, 73, 36),
         (109, 73, 36), (36, 182, 36), (0, 109, 0), (182, 109, 36), (255, 0, 219),
         (219, 219, 255)]
PALETTE = MICA + PROPS
TRANSPARENT = -1


def key_background(image: Image.Image) -> Image.Image:
    """Clear the near-white region connected to the edges; enclosed whites (gloves) stay."""
    image = image.convert('RGBA')
    px, (w, h) = image.load(), image.size
    todo = deque([(x, y) for x in range(w) for y in (0, h - 1)]
                 + [(x, y) for y in range(h) for x in (0, w - 1)])
    seen = set()
    while todo:
        x, y = todo.popleft()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
            continue
        seen.add((x, y))
        if min(px[x, y][:3]) >= 255 - WHITE_TOLERANCE:
            px[x, y] = (0, 0, 0, 0)
            todo.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    return image.crop(image.getbbox())


def palette_indices(image: Image.Image) -> list[int]:
    """Nearest palette entry per pixel, TRANSPARENT where the background was keyed out."""
    cache: dict[tuple[int, int, int], int] = {}
    out = []
    pixels = image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()
    for r, g, b, a in pixels:
        if a < 128:
            out.append(TRANSPARENT)
            continue
        rgb = (r, g, b)
        if rgb not in cache:
            cache[rgb] = min(range(len(PALETTE)),
                             key=lambda i: sum((rgb[c] - PALETTE[i][c]) ** 2 for c in range(3)))
        out.append(cache[rgb])
    return out


def reduce(source: Path) -> Image.Image:
    drawing = key_background(Image.open(source))
    sw, sh = drawing.size
    indices = palette_indices(drawing)
    factor = max(sw, sh) / BOX
    w, h = round(sw / factor), round(sh / factor)
    left, top = (SIZE - w) // 2, SIZE - 1 - h
    grid = [[TRANSPARENT] * SIZE for _ in range(SIZE)]
    ink = PALETTE.index(INK)
    for y in range(h):
        for x in range(w):
            block = [indices[sy * sw + sx]
                     for sy in range(int(y * factor), min(sh, int((y + 1) * factor)))
                     for sx in range(int(x * factor), min(sw, int((x + 1) * factor)))]
            opaque = [i for i in block if i != TRANSPARENT]
            if not block or len(opaque) * 2 < len(block):
                continue
            colours = [i for i in opaque if i != ink]
            if len(opaque) - len(colours) >= INK_SHARE * len(opaque) or not colours:
                grid[top + y][left + x] = ink
            else:
                grid[top + y][left + x] = Counter(colours).most_common(1)[0][0]

    def filled(x: int, y: int) -> bool:
        return 0 <= x < SIZE and 0 <= y < SIZE and grid[y][x] != TRANSPARENT

    picture = Image.new('RGBA', (SIZE, SIZE))
    px = picture.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if not filled(x, y):
                continue
            edge = not all(filled(a, b) for a, b in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
            px[x, y] = (INK if edge else PALETTE[grid[y][x]]) + (255,)
    return picture


def encoded(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    return buffer.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='fail if a shipped picture differs')
    args = parser.parse_args()
    stale = []
    for gag in GAGS:
        target = OUT / f'mica-{gag}.png'
        picture = reduce(SOURCES / f'{gag}.png')
        if args.check:
            shipped = Image.open(target).convert('RGBA')
            if shipped.size != picture.size or shipped.tobytes() != picture.tobytes():
                stale.append(target.name)
        else:
            target.write_bytes(encoded(picture))
    if stale:
        print('stale crash pictures: ' + ', '.join(stale), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
