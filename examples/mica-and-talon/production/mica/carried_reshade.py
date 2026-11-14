#!/usr/bin/env python3
"""Re-shade MICA's three carried drawings to the rest of her sheet's index usage.

Purpose: hang-00..02 (HANG, HANG2 and the TAILS_CARRIED script) kept the 2026-11-10 "selective
material contour" look of the joint carry redraw: a jade-deep, tan and brown silhouette with ink
on a third of it, jade-deep scale lines and no jade-shadow. Beside TALON's re-shaded carry frames
(../talon/tonal_reshade.py) they were the only un-inked sprites in the carry shot. This rebuilds
them from their original indexed rows with her sheet's conventions; no pixel is added, removed or
moved, so the gloves TALON grips are where they were.
Inputs: carried-reshade.json beside this file (original rows per drawing, hand patches). Writes
art/mica/sheet/frames/hang-0*.png and keeps recipe.json native_pixels, frames.yaml notes and
native-pose-core.json in step.
Origin: promo owner feedback follow-up, 2026-11-12. Pillow; no ROM, Java or source generation needed.
Run from the repository root; --check compares without writing.

Rules, in order, on the original indices (palette.yaml names):
  1. Silhouette: every opaque pixel with a transparent 4-neighbour becomes ink (1). This is
     pipeline.outline(), the rule every other frame of her sheet already has; recipe.json and
     native-pose-core.json drop the frames' outline_mode "material" opt-out.
  2. Scale separators: a one-pixel-wide run (three or more pixels) of jade-deep 2 becomes ink;
     blocks of 2 stay as shade.
  3. Scale shade: jade 4 with a contour or separator (ink, jade-deep or a silhouette pixel) directly
     to its right or below becomes jade-shadow 3, the lower-right shade her other frames carry.
"""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/mica/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/mica'
RECORD = PROD / 'carried-reshade.json'
N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
MIN_LINE = 3
STAMP = 'Tonal re-shade 2026-11-12'
NOTE = (' Tonal re-shade 2026-11-12 (carried_reshade.py): ink silhouette and scale separators, jade-shadow '
        'scale shade as the rest of her sheet; silhouette, hotspot and grip pixels unchanged.')
OUTDATED = '; original generated contours, native selective material outlines; palette quantization only.'
REPLACED = '; original generated contours.'
CORE_NOTE = ('2026-11-12: indices re-shaded to her sheet (carried-reshade.json); the material contour is now the '
             'pipeline ink silhouette. Size, silhouette and hotspot unchanged.')


def reshade(rows, drawing):
    grid = [[int(v, 16) for v in row] for row in rows]
    height, width = len(grid), len(grid[0])

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

    def edge(x, y):
        return any(at(x + dx, y + dy) == 0 for dx, dy in N4)

    def contour(x, y):
        return at(x, y) in (1, 2) or (at(x, y) and edge(x, y))

    # Rule 2: thin jade-deep runs. A pixel inside any 2x2 block of ink/jade-deep is shade, not line.
    candidates = {(x, y) for y in range(height) for x in range(width)
                  if grid[y][x] == 2 and not edge(x, y) and not any(
                      all(at(x + a + i, y + b + j) in (1, 2) for i in (0, 1) for j in (0, 1))
                      for a in (-1, 0) for b in (-1, 0))}
    lines, seen = set(), set()
    for start in sorted(candidates):
        if start in seen:
            continue
        run, todo = [start], [start]
        seen.add(start)
        while todo:
            cx, cy = todo.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    near = (cx + dx, cy + dy)
                    if near in candidates and near not in seen:
                        seen.add(near)
                        run.append(near)
                        todo.append(near)
        if len(run) >= MIN_LINE:
            lines.update(run)

    out = [row[:] for row in grid]
    for y in range(height):
        for x in range(width):
            value = grid[y][x]
            if not value:
                continue
            if edge(x, y):
                out[y][x] = 1
            elif value == 2 and (x, y) in lines:
                out[y][x] = 1
            elif value == 4 and (contour(x + 1, y) or contour(x, y + 1)):
                out[y][x] = 3
    for x, y, index in drawing.get('patches', []):
        if not grid[y][x] or not 0 < index < 16:
            raise SystemExit(f"Patch outside the silhouette: {drawing['id']} {x},{y}")
        out[y][x] = index
    return [''.join(format(v, 'x') for v in row) for row in out]


def image(rows, colors):
    im = Image.new('P', (len(rows[0]), len(rows)))
    im.putpalette(sum(colors, []) + [0] * (768 - 48))
    im.putdata([int(v, 16) for row in rows for v in row])
    return im


def note(old):
    return old if STAMP in old else old.replace(OUTDATED, REPLACED) + NOTE


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    record = json.loads(RECORD.read_text())
    colors = json.loads((SHEET / 'palette.yaml').read_text())['colors']
    wanted = {}
    for drawing in record['drawings']:
        source = drawing['source_rows']
        rows = reshade(source, drawing)
        if [[v != '0' for v in row] for row in rows] != [[v != '0' for v in row] for row in source]:
            raise SystemExit('Silhouette changed: ' + drawing['id'])
        wanted[drawing['id']] = rows

    stale = []
    for frame, rows in wanted.items():
        path = SHEET / 'frames' / (frame + '.png')
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == (len(rows[0]), len(rows)) and \
                list(actual.get_flattened_data()) == [int(v, 16) for row in rows for v in row]
        if not same:
            stale.append(path)
            if not args.check:
                image(rows, colors).save(path, transparency=0, optimize=True, bits=4)

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
                frame.pop('outline_mode', None)  # default "ink": the pipeline's own silhouette rule
                frame['notes'] = note(frame['notes'])

    def handoff(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['notes'] = note(frame['notes'])

    def core(data):
        for frame in wanted:
            data['frames'][frame].pop('outline_mode', None)
            data['frames'][frame]['tonal_reshade'] = CORE_NOTE

    sync(PROD / 'recipe.json', recipe)
    sync(SHEET / 'frames.yaml', handoff)
    sync(PROD / 'native-pose-core.json', core)
    if args.check and stale:
        raise SystemExit('Stale carried re-shade: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    print(f"{len(wanted)} carried drawings " + ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
