#!/usr/bin/env python3
"""Re-shade TALON's flight and carry drawings to the 37 px rung set's index usage.

Purpose: the twelve free-flight/ascend and twelve carry/carry-ascend frames kept the 2026-11-10
"selective material contour" look (no ink silhouette, slate plumage fill, cream wing tips), so they
read washed out beside WALK/RUN/WAIT. This rebuilds them from their original indexed rows with the
sheet's own conventions; no pixel is added, removed or moved.
Inputs: tonal-reshade.json beside this file (original rows, wing-tip boxes and first shoe row per
drawing, hand patches). Writes art/talon/sheet/frames/*.png and keeps recipe.json native_pixels,
frames.yaml notes, feedback-generation.json selected rows and rescale-37.json in step.
Origin: promo owner feedback, 2026-11-12. Pillow; no ROM, Java or source generation needed.
Run from the repository root; --check compares without writing.

Rules, in order, on the original indices (palette.yaml names):
  1. Silhouette: every opaque pixel with a transparent 4-neighbour becomes ink (1). This is
     pipeline.outline(), the rule every other sheet frame already has; recipe.json drops the
     frames' outline_mode "material" opt-out.
  2. Feather tips (light pixels inside the drawing's wing-tip boxes): cream 7 -> feather-tip 6,
     6 -> glove-shadow 13, 13 -> slate-light 5, cream-shade 8 -> 13. The approved wings end in
     grey-white, never chest cream.
  3. Plumage moves one step down: slate 4 -> slate-shadow 3, 3 -> slate-deep 2, 5 -> 4. A slate
     pixel touching a feather tip stays 4 (the tip ramp), and a 5 that shades a white or cream
     part outside the wing tips stays 5.
  4. Separators: a one-pixel-wide run (three or more pixels) of slate-deep 2 becomes ink; blocks
     of 2 stay as shade. Brown/red contour pixels between amber and plumage or face become ink.
  5. Above the first shoe row, shoe reds are quantisation strays: shoe-shadow 11 -> satchel-brown
     9, shoe-red 10 -> amber 14 (satchel, goggle strap and beak shading).
"""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/talon/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/talon'
RECORD = PROD / 'tonal-reshade.json'
N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
PLUMAGE, LIGHT, AMBER, RED = {2, 3, 4, 5}, {6, 7, 8, 12, 13}, {14, 15}, {10, 11}
TIP = {7: 6, 6: 13, 13: 5, 8: 13, 12: 12}
MIN_LINE = 3
STAMP = 'Tonal re-shade 2026-11-12'
NOTE = {
    'fly': (' Tonal re-shade 2026-11-12 (tonal_reshade.py): ink silhouette and separators, slate-shadow '
            'plumage and grey-white feather tips as the 37 px rung set; silhouette and hotspot unchanged.'),
    'carry': (' Tonal re-shade 2026-11-12 (tonal_reshade.py): ink silhouette and separators, slate-shadow '
              'plumage, grey-white feather tips and brown satchel as the 37 px rung set; silhouette, hotspot '
              'and grip pixels unchanged.'),
}
OUTDATED = ('; profile identity and material contours match approved carry;',
            '; original generated contours, native selective material outlines; palette quantization only.')
REPLACED = ('; profile identity matches the carry;', '; original generated contours.')
RESCALE_NOTE = ('2026-11-12: indices re-shaded to the rung set (tonal-reshade.json); size, silhouette and '
                'hotspot unchanged.')


def reshade(rows, drawing):
    grid = [[int(v, 16) for v in row] for row in rows]
    height, width = len(grid), len(grid[0])

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

    def tip(x, y):
        return any(a <= x <= c and b <= y <= d for a, b, c, d in drawing['wing_tip_boxes'])

    def edge(x, y):
        return any(at(x + dx, y + dy) == 0 for dx, dy in N4)

    # Rule 4: thin slate-deep runs. A pixel inside any 2x2 block of ink/slate-deep is shade, not line.
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
    shoe_row = drawing['first_shoe_row']
    for y in range(height):
        for x in range(width):
            value = grid[y][x]
            if not value:
                continue
            near = [at(x + dx, y + dy) for dx, dy in N4]
            if edge(x, y):
                out[y][x] = 1
                continue
            if tip(x, y) and value in LIGHT:
                out[y][x] = TIP[value]
            elif value == 5:
                out[y][x] = 5 if not tip(x, y) and any(n in LIGHT for n in near) else 4
            elif value == 4:
                ramp = any(tip(x + dx, y + dy) and at(x + dx, y + dy) in LIGHT for dx, dy in N4)
                out[y][x] = 4 if ramp else 3
            elif value == 3:
                out[y][x] = 2
            elif value == 2 and (x, y) in lines:
                out[y][x] = 1
            elif value in RED and y < shoe_row:
                out[y][x] = 9 if value == 11 else 14
            if (value in (9, 11) and y < shoe_row and any(n in AMBER for n in near)
                    and any(n in PLUMAGE or n in LIGHT for n in near)):
                out[y][x] = 1
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


def note(old, kind):
    if STAMP in old:
        return old
    for outdated, replaced in zip(OUTDATED, REPLACED):
        old = old.replace(outdated, replaced)
    return old + NOTE[kind]


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
        for frame in drawing['frames']:
            wanted[frame] = (rows, 'carry' if 'carry' in frame else 'fly')

    stale = []
    for frame, (rows, _) in wanted.items():
        path = SHEET / 'frames' / (frame + '.png')
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == (len(rows[0]), len(rows)) and \
                list(actual.get_flattened_data()) == [int(v, 16) for row in rows for v in row]
        if not same:
            stale.append(path)
            if not args.check:
                image(rows, colors).save(path, transparency=0, optimize=True, bits=4)

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
                rows, kind = wanted[frame['id']]
                frame['native_pixels']['rows'] = rows
                frame.pop('outline_mode', None)  # default "ink": the pipeline's own silhouette rule
                frame['notes'] = note(frame['notes'], kind)

    def handoff(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['notes'] = note(frame['notes'], wanted[frame['id']][1])

    def feedback(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['selected_pixels']['rows'] = wanted[frame['id']][0]
                frame['tonal_reshade'] = RESCALE_NOTE

    def rescale(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                frame['tonal_reshade'] = RESCALE_NOTE

    sync(PROD / 'recipe.json', 2, recipe)
    sync(SHEET / 'frames.yaml', 2, handoff)
    sync(PROD / 'feedback-generation.json', 2, feedback)
    sync(PROD / 'rescale-37.json', 1, rescale)
    if args.check and stale:
        raise SystemExit('Stale tonal re-shade: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    print(f"{len(wanted)} frames from {len(record['drawings'])} drawings " +
          ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
