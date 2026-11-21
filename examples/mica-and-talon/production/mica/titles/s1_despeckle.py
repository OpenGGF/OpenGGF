#!/usr/bin/env python3
"""Despeckle MICA's Sonic 1 title-screen portrait.

Purpose: the owner asked to "try and despeckle the Sonic 1 title screen". The eight BODY frames
(art/mica/titles/s1-0..7.png) are reductions of a generated drawing and carried its residue: lone
pixels and two-pixel flecks of a neighbouring shade scattered through the scales, sand and gloves.
This folds them into the surrounding shade of their own material with the shared cleanup
(tools/modding/playable_art/despeckle.py) and her sheet's material map.
Inputs: s1-despeckle.json beside this file (each frame's original rows). Writes the eight PNGs;
s1-frames.txt, its bank, selectors and hotspots are not touched, and no pixel changes its alpha.
Origin: owner line-art feedback on PR #287, 2026-11-12. Pillow; no ROM, Java or source generation.
Run from the repository root; --check compares without writing.

Wag continuity: frames 5-7 are the finger-wag loop, drawn as three whole portraits whose scale edges,
outline and cheek differ by about 730 pixels each, so her scales shimmered at every swap (owner,
2026-11-20: "she has a shimmering border on her scales", seen in the 0.8 promo). After the despeckle,
frames 6 and 7 take frame 5's pixels everywhere but the hand: the glove's whites (12-13) of either
frame inside WAG_BOX, grown two pixels to take in its ink and filled. Only the hand moves now.

Rules: despeckle.py rules 1-3 with `lines=True`. A portrait this size draws its scale edges as
one-pixel diagonal lines, which the sprite cleanup would read as isolated pixels and erase (the
first try flattened the scales); a pixel or fleck that continues diagonally into its own index is
kept. Scales (2-6), sand (7-9) and white (12-13) only, never across a material; sand and white are
never darkened; ink, coral, ear pink and eye brown pass through.
"""
import argparse
import json
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[5]
TITLES = ROOT / 'examples/mica-and-talon/art/mica/titles'
RECORD = Path(__file__).resolve().parent / 's1-despeckle.json'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
sys.path.insert(0, str(ROOT / 'examples/mica-and-talon/production'))
from despeckle import despeckle  # noqa: E402
from slope_sets import MICA  # noqa: E402

ROUNDS = 3
WAG_BODY, WAG_HANDS = 's1-5.png', ('s1-6.png', 's1-7.png')
WAG_BOX = (54, 20, 88, 72)      # x0, y0, x1, y1: the glove and finger in every wag frame; the eye whites lie left of it
GLOVE = (12, 13)


def grow(mask, steps):
    """`mask` (rows of bools) grown by `steps` pixels in all eight directions."""
    h, w = len(mask), len(mask[0])
    for _ in range(steps):
        mask = [[any(mask[yy][xx] for yy in range(max(0, y - 1), min(h, y + 2)) for xx in range(max(0, x - 1), min(w, x + 2)))
                 for x in range(w)] for y in range(h)]
    return mask


def fill(mask):
    """`mask` with every region it encloses (not reachable from the edge) filled."""
    h, w = len(mask), len(mask[0])
    outside, todo = set(), [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while todo:
        x, y = todo.pop()
        if (x, y) in outside or not (0 <= x < w and 0 <= y < h) or mask[y][x]:
            continue
        outside.add((x, y))
        todo += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return [[mask[y][x] or (x, y) not in outside for x in range(w)] for y in range(h)]


def wag_continuity(wanted):
    """Frames 6 and 7 on frame 5's body: only the hand is their own."""
    body = wanted[WAG_BODY][0]
    x0, y0, x1, y1 = WAG_BOX
    for name in WAG_HANDS:
        grid, palette = wanted[name]
        if (len(grid), len(grid[0])) != (len(body), len(body[0])):
            raise SystemExit('Wag frame size differs from its body: ' + name)
        glove = [[x0 <= x < x1 and y0 <= y < y1 and (body[y][x] in GLOVE or grid[y][x] in GLOVE)
                  for x in range(len(grid[0]))] for y in range(len(grid))]
        hand = fill(grow(glove, 2))
        wanted[name] = ([[grid[y][x] if hand[y][x] else body[y][x] for x in range(len(grid[0]))]
                         for y in range(len(grid))], palette)
    return wanted


def build(record):
    """file name -> rows."""
    wanted = {}
    for frame in record['frames']:
        palette = frame['palette']
        lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in palette]
        source = [[int(v, 16) for v in row] for row in frame['source_rows']]
        grid = despeckle(source, lum, MICA['material'], MICA['protect'], ROUNDS, lines=True)
        if [[v == 0 for v in row] for row in grid] != [[v == 0 for v in row] for row in source] or \
                [[v == 1 for v in row] for row in grid] != [[v == 1 for v in row] for row in source]:
            raise SystemExit('Silhouette or ink changed: ' + frame['file'])
        wanted[frame['file']] = (grid, palette)
    return wag_continuity(wanted)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for name, (grid, palette) in build(json.loads(RECORD.read_text())).items():
        path = TITLES / name
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == (len(grid[0]), len(grid)) and \
                list(actual.get_flattened_data()) == [v for row in grid for v in row]
            info = dict(actual.info)
        if not same:
            stale.append(name)
            if not args.check:
                image = Image.new('P', (len(grid[0]), len(grid)))
                image.putpalette(sum(palette, []) + [0] * (768 - 48))
                image.putdata([v for row in grid for v in row])
                image.save(path, transparency=info.get('transparency', 0), optimize=True, bits=4)
    if args.check and stale:
        raise SystemExit('Stale S1 title despeckle: ' + ', '.join(stale))
    print('8 title frames ' + ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
