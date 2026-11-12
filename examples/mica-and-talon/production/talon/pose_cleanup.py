#!/usr/bin/env python3
"""Clean TALON's remaining flat poses to match his cleaned walk and run.

Purpose: after walk_cleanup.py the rest of his normal sheet still carried the speckle of its
reduced source drawings (noise 0.25-0.41 on tools/modding/playable_art/noise_audit.py, against
0.20-0.22 for the cleaned WALK/RUN flats and 0.15 for the flight set), so WAIT and the other poses
read noisier than the loops beside them. This applies walk_cleanup.clean, the same rules and the
same material map, to every other flat drawing that is not already clean.
Inputs: pose-cleanup.json beside this file (original rows and hand patches per drawing, and the
drawings left alone with the reason). Writes art/talon/sheet/frames/*.png and keeps recipe.json
native_pixels, frames.yaml notes and rescale-37.json in step.
Origin: owner request after the walk cleanup, 2026-11-12. Pillow; no ROM, Java or source
generation needed. Run from the repository root; --check compares without writing.

Rules (walk_cleanup.py, rules 1-4; tools/modding/playable_art/despeckle.py): isolated pixels,
one-pixel anti-alias steps and two-pixel flecks fold into the surrounding shade of their own
material, for plumage (2-5) and white/cream (6, 7, 8, 12, 13) only; white and cream are never
darkened; slate-light 5 that does not touch white or cream becomes slate 4. Ink, goggles, beak,
strap, satchel and shoes are never rewritten, so alpha, ink, size and hotspot are identical.
Hand patches from the record, x,y,index inside one material, are applied last.

Not touched here: WALK/RUN and their slopes (walk_cleanup.py), the flight and carry sets
(tonal_reshade.py, already clean), and the drawings listed under left_alone in the record.
No slope frame derives from these flats, so slope_sets.py has nothing to re-derive. Sixteen object
poses are another one turned or mirrored; each has its own rows here, and scale_audit.py turns them
again when it rebuilds their source at a new size.
"""
import argparse
import json
import sys
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from walk_cleanup import MATERIAL, PROD, ROOT, SHEET, clean, rows_of, to_image  # noqa: E402

RECORD = PROD / 'pose-cleanup.json'
STAMP = 'Cleanup 2026-11-12'
NOTE = (' Cleanup 2026-11-12 (pose_cleanup.py): plumage and white/cream despeckled within their own material, '
        'three plumage bands; silhouette, ink and hotspot unchanged.')
RESCALE_NOTE = '2026-11-12: pose_cleanup.py, see pose-cleanup.json.'


def build(record, colors):
    lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in colors]
    wanted = {}
    for drawing in record['drawings']:
        source = [[int(v, 16) for v in row] for row in drawing['source_rows']]
        grid = clean(source, lum)
        for x, y, index in drawing.get('patches', []):
            if MATERIAL.get(source[y][x]) is None or MATERIAL.get(index) != MATERIAL[source[y][x]]:
                raise SystemExit(f"Patch must stay inside plumage or white/cream: {drawing['id']} {x},{y}")
            grid[y][x] = index
        if [[min(v, 2) for v in row] for row in grid] != [[min(v, 2) for v in row] for row in source]:
            raise SystemExit('Silhouette or ink changed: ' + drawing['id'])
        wanted[drawing['id']] = rows_of(grid)
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
                to_image([[int(v, 16) for v in row] for row in rows], colors).save(
                    path, transparency=0, optimize=True, bits=4)

    def sync(path, indent, edit):
        text = path.read_text()
        data = json.loads(text)
        edit(data)
        fresh = json.dumps(data, indent=indent) + '\n'
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

    def rescale(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['cleanup'] = RESCALE_NOTE

    sync(PROD / 'recipe.json', 2, recipe)
    sync(SHEET / 'frames.yaml', 2, handoff)
    sync(PROD / 'rescale-37.json', 1, rescale)
    if args.check and stale:
        raise SystemExit('Stale pose cleanup: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    print(f"{len(wanted)} drawings " + ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
