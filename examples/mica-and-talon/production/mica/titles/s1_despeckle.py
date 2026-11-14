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
    return wanted


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
