#!/usr/bin/env python3
"""Clean TALON's WALK and RUN drawings and re-derive their slope sets.

Purpose: the owner found his walk blurry. tools/modding/playable_art/noise_audit.py puts the twelve
flat WALK/RUN frames with most of the sheet (a third of their body pixels isolated, the residue of a
generated drawing reduced to the palette), and the loop makes that speckle boil. This despeckles
plumage and white/cream inside their own material, leaves ink, goggles, beak, strap, satchel and
shoes alone, and rebuilds the 36 slope frames from the cleaned flats with a mode-reduced rotation.
Inputs: walk-cleanup.json beside this file (original rows and hand patches per flat; support
distance per slope frame). Writes art/talon/sheet/frames/{walk,run}-*.png and keeps recipe.json
native_pixels, frames.yaml hotspots and notes, and rescale-37.json in step.
Origin: promo owner feedback follow-up, 2026-11-12. Pillow; no ROM, Java or source generation needed.
Run from the repository root; --check compares without writing.

Flat rules, on the original indices (palette.yaml names). Only plumage (slate-deep 2, slate-shadow 3,
slate 4, slate-light 5) and white/cream (6, 7, 8, 12, 13) are ever rewritten, and only to another
index of the same material; transparency and ink never change, so the silhouette cannot.
  1. Isolated pixel (no 4-neighbour of its index) with two or more neighbours of its own material
     takes the commonest of them; a tie goes to the nearer luminance, then the lower index.
  2. Otherwise, an isolated pixel that is a one-pixel luminance step between its two neighbours on
     an axis (an anti-alias fringe) takes the nearer neighbour of its own material.
  3. A two-pixel fleck whose surround is at least four pixels and 60% its own material takes the
     commonest of them. Rules 1-3 repeat, at most three rounds, until nothing changes.
     White and cream are never darkened by rules 1-3: a lone lighter pixel there is a highlight
     (cheek, eye, glove shine) and the speckle to remove is the darker fringe. Plumage goes both ways.
  4. Slate-light 5 that does not touch white or cream becomes slate 4, leaving plumage on the
     three bands of his wait and flight frames; a 5 shading a white part stays.
  5. Light pixels that belong to another part are put back: a white/cream patch with no plumage
     beside it that touches amber, brown or red is a goggle glint, beak shine or shoe white.
  6. Hand patches from the record, x,y,index, last.
A pixel alone in its material is a feature (eye, glint, strap end) and is never touched.

Slopes: derive_slopes.derive(method='mode'). The flat's silhouette ink is lifted, the drawing is
enlarged with Scale2x three times, turned 45 degrees, reduced to the commonest index of each 8x8
block, cleaned with rules 1-4 and only then inked; steep is the flat's exact quarter turn and
inverted the quarter turn of up45. Each frame keeps the support distance of the frame it replaces.
"""
import argparse
import json
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/talon/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/talon'
RECORD = PROD / 'walk-cleanup.json'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
from derive_slopes import ANGLES, derive  # noqa: E402
from despeckle import despeckle  # noqa: E402

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
MATERIAL = {2: 'plumage', 3: 'plumage', 4: 'plumage', 5: 'plumage',
            6: 'light', 7: 'light', 8: 'light', 12: 'light', 13: 'light'}
PLUMAGE = {2, 3, 4, 5}
ACCENT = {9, 10, 11, 14, 15}  # satchel-brown, shoe reds, amber: goggles, beak, strap, satchel, shoes
ROUNDS = 3
STAMP = 'Cleanup 2026-11-12'
FLAT_NOTE = (' Cleanup 2026-11-12 (walk_cleanup.py): plumage and white/cream despeckled within their own material, '
             'three plumage bands; silhouette, ink and hotspot unchanged.')
SLOPE_NOTE = ('Cleanup 2026-11-12 (walk_cleanup.py): derive_slopes.derive(method="mode") from cleaned {base}, '
              '{angle} degrees CCW; silhouette ink lifted, Scale2x x3, mode reduction, material cleanup, then ink; '
              'integer support alignment.')
RESCALE_NOTE = '2026-11-12: walk_cleanup.py, see walk-cleanup.json.'


def part_detail(grid):
    """Rule 5: pixels of white/cream patches that sit in goggles, beak or shoes rather than on the body."""
    height, width = len(grid), len(grid[0])
    seen, detail = set(), set()
    for y in range(height):
        for x in range(width):
            if MATERIAL.get(grid[y][x]) != 'light' or (x, y) in seen:
                continue
            patch, todo, beside = [(x, y)], [(x, y)], set()
            seen.add((x, y))
            while todo:
                cx, cy = todo.pop()
                for a, b in N4:
                    near = (cx + a, cy + b)
                    if not (0 <= near[0] < width and 0 <= near[1] < height):
                        continue
                    value = grid[near[1]][near[0]]
                    if MATERIAL.get(value) == 'light':
                        if near not in seen:
                            seen.add(near)
                            patch.append(near)
                            todo.append(near)
                    else:
                        beside.add(value)
            if not beside & PLUMAGE and beside & ACCENT:
                detail.update(patch)
    return detail


def clean(grid, lum):
    """Rules 1-3 are tools/modding/playable_art/despeckle.py; rule 4 is his plumage band; rule 5 part_detail."""
    source = grid
    grid = despeckle(grid, lum, MATERIAL, protect=('light',), rounds=ROUNDS)
    height, width = len(grid), len(grid[0])

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

    for y in range(height):  # rule 4
        for x in range(width):
            if grid[y][x] == 5 and not any(MATERIAL.get(at(x + a, y + b)) == 'light' for a, b in N4):
                grid[y][x] = 4
    for x, y in part_detail(source):  # rule 5
        grid[y][x] = source[y][x]
    return grid


def to_grid(image):
    pixels = image.load()
    return [[pixels[x, y] for x in range(image.width)] for y in range(image.height)]


def to_image(grid, colors):
    image = Image.new('P', (len(grid[0]), len(grid)))
    image.putpalette(sum(colors, []) + [0] * (768 - 48))
    image.putdata([v for row in grid for v in row])
    return image


def rows_of(grid):
    return [''.join(format(v, 'x') for v in row) for row in grid]


def build(record, colors):
    """id -> (rows, hotspot or None for an unchanged flat hotspot, note kind, base, angle)."""
    lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in colors]
    hotspots = {f['id']: f['hotspot'] for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    wanted, flats = {}, {}
    for flat in record['flats']:
        source = [[int(v, 16) for v in row] for row in flat['source_rows']]
        grid = clean(source, lum)
        for x, y, index in flat.get('patches', []):
            if MATERIAL.get(source[y][x]) is None or MATERIAL.get(index) != MATERIAL[source[y][x]]:
                raise SystemExit(f"Patch must stay inside plumage or white/cream: {flat['id']} {x},{y}")
            grid[y][x] = index
        if [[min(v, 2) for v in row] for row in grid] != [[min(v, 2) for v in row] for row in source]:
            raise SystemExit('Silhouette or ink changed: ' + flat['id'])
        flats[flat['id']] = to_image(grid, colors)
        wanted[flat['id']] = (rows_of(grid), None, 'flat', None, None)

    def finish(image):
        return to_image(clean(to_grid(image), lum), colors)

    for slope in record['slopes']:
        group, label, phase = slope['id'].split('-')
        base = f'{group}-{phase}'
        image, hotspot = derive(flats[base], hotspots[base], ANGLES[label], slope['support'], 'mode', finish)
        wanted[slope['id']] = (rows_of(to_grid(image)), hotspot, 'slope', base, ANGLES[label])
    return wanted


def note(old, kind, base, angle):
    if kind == 'slope':
        return SLOPE_NOTE.format(base=base, angle=angle)
    return old if STAMP in old else old + FLAT_NOTE


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    record = json.loads(RECORD.read_text())
    colors = json.loads((SHEET / 'palette.yaml').read_text())['colors']
    wanted = build(record, colors)

    stale = []
    for frame, (rows, *_rest) in wanted.items():
        path = SHEET / 'frames' / (frame + '.png')
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
                rows, hotspot, kind, base, angle = wanted[frame['id']]
                frame['native_pixels']['rows'] = rows
                if hotspot:
                    frame['native_pixels']['anchor'] = hotspot
                frame['notes'] = note(frame['notes'], kind, base, angle)

    def handoff(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                _, hotspot, kind, base, angle = wanted[frame['id']]
                if hotspot:
                    frame['hotspot'] = hotspot
                frame['notes'] = note(frame['notes'], kind, base, angle)

    def rescale(data):
        for frame in data['frames']:
            if frame['id'] in wanted:
                rows, hotspot, kind, _, _ = wanted[frame['id']]
                frame['cleanup'] = RESCALE_NOTE
                if kind == 'slope':
                    frame['cleanup_size'] = [len(rows[0]), len(rows)]
                    frame['cleanup_hotspot'] = hotspot

    sync(PROD / 'recipe.json', 2, recipe)
    sync(SHEET / 'frames.yaml', 2, handoff)
    sync(PROD / 'rescale-37.json', 1, rescale)
    if args.check and stale:
        raise SystemExit('Stale walk cleanup: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    print(f"{len(record['flats'])} flats and {len(record['slopes'])} slope frames " +
          ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    main()
