#!/usr/bin/env python3
"""Despeckle MICA's flat poses, keeping her scales.

Purpose: on tools/modding/playable_art/noise_audit.py her sheet's median was 0.33 against 0.19
for TALON's cleaned sheet; every pose is a reduction of a generated drawing and carried its lone
pixels and two-pixel flecks. Unlike TALON she does not have a different face per sheet (her poses
were already normalised to one skull and one face, see consistency-landmarks.json), so her
line-art fix is this cleanup, not a redraw: lone pixels and flecks fold into the surrounding
shade of their own material.
Inputs: sheet-despeckle.json beside this file (original rows per drawing, and what is left alone
with the reason). Writes art/mica/sheet/frames/*.png and keeps recipe.json native_pixels,
frames.yaml notes and the tutorial's part 2 copies of her frames (tutorial/part-2-first-playable,
which TestMicaTalonTutorial holds byte-identical to the sheet) in step. Her WALK/RUN slopes and
homing-dive directions derive from these flats: run ../slope_sets.py after this to redraw them.
Origin: owner line-art feedback on PR #287, 2026-11-12. Pillow; no ROM, Java or source generation.
Run from the repository root; --check compares without writing.

Rules: tools/modding/playable_art/despeckle.py rules 1-3 with `lines=True`, three rounds, the
material map of production/slope_sets.py (scales 2-6, sand 7-9, white 12-13; sand and white never
darkened). `lines=True` matters: her scales are drawn as one-pixel diagonal edges, and the plain
sprite cleanup reads those as isolated pixels and flattens the scales into one jade mass (0.17,
but no pangolin left). With the edges kept the flats land at 0.22-0.25. Ink, coral, ear pink and
eye brown pass through; alpha, ink, size and hotspot are identical.
"""
import argparse
import json
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/mica/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/mica'
RECORD = PROD / 'sheet-despeckle.json'
TUTORIAL = ROOT / 'examples/mica-and-talon/tutorial/part-2-first-playable/art/sheet'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
sys.path.insert(0, str(ROOT / 'examples/mica-and-talon/production'))
from despeckle import despeckle  # noqa: E402
from slope_sets import MICA  # noqa: E402

ROUNDS = 3
STAMP = 'Despeckle 2026-11-12'
NOTE = (' Despeckle 2026-11-12 (sheet_despeckle.py): lone pixels and flecks folded into their own material, '
        'scale edges kept; silhouette, ink and hotspot unchanged.')


def build(record, colors):
    lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in colors]
    wanted = {}
    for drawing in record['drawings']:
        source = [[int(v, 16) for v in row] for row in drawing['source_rows']]
        grid = despeckle(source, lum, MICA['material'], MICA['protect'], ROUNDS, lines=True)
        if [[min(v, 2) for v in row] for row in grid] != [[min(v, 2) for v in row] for row in source]:
            raise SystemExit('Silhouette or ink changed: ' + drawing['id'])
        wanted[drawing['id']] = [''.join(format(v, 'x') for v in row) for row in grid]
    return wanted


def note(old):
    old = old or ''
    return old if STAMP in old else (old + NOTE).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    record = json.loads(RECORD.read_text())
    colors = json.loads((SHEET / 'palette.yaml').read_text())['colors']
    wanted = build(record, colors)
    files = {f['id']: f['file'] for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    stale = []
    for frame, rows in wanted.items():
        path = SHEET / files[frame]
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == (len(rows[0]), len(rows)) and \
                list(actual.get_flattened_data()) == [int(v, 16) for row in rows for v in row]
        if not same:
            stale.append(path)
            if not args.check:
                image = Image.new('P', (len(rows[0]), len(rows)))
                image.putpalette(sum(colors, []) + [0] * (768 - 48))
                image.putdata([int(v, 16) for row in rows for v in row])
                image.save(path, transparency=0, optimize=True, bits=4)

    def sync(path, edit):
        text = path.read_text()
        data = json.loads(text)
        edit(data)
        fresh = json.dumps(data, indent=2) + '\n'
        if fresh != text:
            stale.append(path)
            if not args.check:
                path.write_text(fresh)

    def recipe(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['native_pixels']['rows'] = wanted[frame['id']]
                frame['notes'] = note(frame.get('notes'))

    def handoff(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['notes'] = note(frame.get('notes'))

    sync(PROD / 'recipe.json', recipe)
    sync(SHEET / 'frames.yaml', handoff)
    for copy in sorted((TUTORIAL / 'frames').glob('*.png')):   # the tutorial checkpoint ships the sheet's own bytes
        original = (SHEET / 'frames' / copy.name).read_bytes()
        if copy.read_bytes() != original:
            stale.append(copy)
            if not args.check:
                copy.write_bytes(original)
    if args.check and stale:
        raise SystemExit('Stale MICA despeckle: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    print(f"{len(wanted)} drawings " + ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
