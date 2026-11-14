#!/usr/bin/env python3
"""Purpose: measure per-frame pixel noise and softness in an indexed playable sheet, and rank it.
Inputs: one or more sheet directories holding frames.yaml and its indexed PNGs (index 0
transparent, index 1 ink). Output: a ranked table on stdout, optionally a CSV.
Origin: owner report that TALON's walk read blurry, 2026-11-12. Pillow; no ROM or Java needed.

Every share is over the frame's non-ink opaque pixels, so frames of different sizes compare:
  iso    isolated pixels: no 4-neighbour has the same index.
  fleck  pixels in a same-index 4-connected patch of one or two pixels.
  short  short-run density: mean of the horizontal and vertical shares of one-pixel runs.
  ramp   one-pixel steps strictly between two different neighbours in luminance on an axis,
         the signature of an anti-aliased reduction quantised to the palette.
  trans  share of 4-adjacent non-ink pairs whose indices differ.
  step   mean luminance difference across those differing pairs; low is soft.
  edge   share of the silhouette (opaque pixels beside transparency) drawn in ink.
noise is the mean of iso, short and ramp; frames are ranked by it, highest first.

Read the ranking against a reference frame rather than as an absolute: reductions of generated
art sit near 0.4, hand-clean bands near 0.2. `--summary` groups by animation and angle set.
"""
import argparse
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path
from PIL import Image

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
FIELDS = ('noise', 'iso', 'fleck', 'short', 'ramp', 'trans', 'step', 'edge', 'ink', 'pixels')


def luminance(palette):
    return [0.299 * palette[i * 3] + 0.587 * palette[i * 3 + 1] + 0.114 * palette[i * 3 + 2]
            for i in range(len(palette) // 3)]


def metrics(grid, lum):
    """grid: rows of palette indices. Returns None for an empty frame."""
    height, width = len(grid), len(grid[0])

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

    opaque = [(x, y) for y in range(height) for x in range(width) if grid[y][x]]
    if not opaque:
        return None
    silhouette = [(x, y) for x, y in opaque if any(at(x + a, y + b) == 0 for a, b in N4)]
    body = [(x, y) for x, y in opaque if grid[y][x] != 1]
    count = len(body) or 1
    iso = sum(1 for x, y in body if all(at(x + a, y + b) != grid[y][x] for a, b in N4))
    seen, fleck = set(), 0
    for start in body:
        if start in seen:
            continue
        value, patch, todo = grid[start[1]][start[0]], [start], [start]
        seen.add(start)
        while todo:
            cx, cy = todo.pop()
            for a, b in N4:
                near = (cx + a, cy + b)
                if near not in seen and at(*near) == value:
                    seen.add(near)
                    patch.append(near)
                    todo.append(near)
        if len(patch) <= 2:
            fleck += len(patch)
    across = sum(1 for x, y in body if at(x - 1, y) != grid[y][x] != at(x + 1, y))
    down = sum(1 for x, y in body if at(x, y - 1) != grid[y][x] != at(x, y + 1))
    ramp = 0
    for x, y in body:
        value = grid[y][x]
        for a, b in ((1, 0), (0, 1)):
            p, q = at(x - a, y - b), at(x + a, y + b)
            if p > 1 and q > 1 and p != value != q and p != q and (
                    lum[p] < lum[value] < lum[q] or lum[p] > lum[value] > lum[q]):
                ramp += 1
                break
    pairs, steps = 0, []
    for x, y in body:
        for a, b in ((1, 0), (0, 1)):
            near = at(x + a, y + b)
            if near > 1:
                pairs += 1
                if near != grid[y][x]:
                    steps.append(abs(lum[near] - lum[grid[y][x]]))
    result = dict(iso=iso / count, fleck=fleck / count, short=(across + down) / (2 * count), ramp=ramp / count,
                  trans=len(steps) / (pairs or 1), step=statistics.mean(steps) if steps else 0.0,
                  edge=sum(grid[y][x] == 1 for x, y in silhouette) / len(silhouette),
                  ink=(len(opaque) - len(body)) / len(opaque), pixels=len(opaque))
    result['noise'] = (result['iso'] + result['short'] + result['ramp']) / 3
    return result


def frame_metrics(path):
    with Image.open(path) as image:
        if image.mode != 'P':
            raise ValueError(f'{path} is not an indexed PNG')
        pixels = image.load()
        grid = [[pixels[x, y] for x in range(image.width)] for y in range(image.height)]
        return metrics(grid, luminance(image.getpalette()))


def sheet(root):
    """(animation, angle_set, id, metrics) for every drawn frame of a sheet directory."""
    root = Path(root)
    rows = []
    for frame in json.loads((root / 'frames.yaml').read_text())['frames']:
        measured = frame_metrics(root / frame['file'])
        if measured:
            rows.append((frame['animation'], frame.get('angle_set', 'flat'), frame['id'], measured))
    return rows


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('sheets', nargs='+', type=Path)
    parser.add_argument('--top', type=int, default=20, help='frames to list per sheet (0 for all)')
    parser.add_argument('--summary', action='store_true', help='group by animation and angle set')
    parser.add_argument('--csv', type=Path, help='write every frame of every sheet')
    args = parser.parse_args(argv)
    table = []
    for root in args.sheets:
        rows = sorted(sheet(root), key=lambda row: (-row[3]['noise'], row[2]))
        table += [(str(root),) + row for row in rows]
        values = sorted(row[3]['noise'] for row in rows)
        print(f"{root}: {len(rows)} frames, noise median {values[len(values) // 2]:.3f}, "
              f"p10 {values[len(values) // 10]:.3f}, p90 {values[9 * len(values) // 10]:.3f}, "
              f"silhouette in ink {statistics.mean(row[3]['edge'] for row in rows):.1%}")
        header = '  ' + 'frame'.ljust(34) + ' '.join(name.rjust(6) for name in FIELDS)
        if args.summary:
            groups = defaultdict(list)
            for animation, angle, _, measured in rows:
                groups[f'{animation}/{angle}'].append(measured)
            print(header.replace('frame', 'group'))
            for name, group in sorted(groups.items(), key=lambda kv: -statistics.mean(m['noise'] for m in kv[1])):
                print('  ' + f'{name} x{len(group)}'.ljust(34) + ' '.join(
                    f"{statistics.mean(m[field] for m in group):6.0f}" if field in ('step', 'pixels')
                    else f"{statistics.mean(m[field] for m in group):6.3f}" for field in FIELDS))
        else:
            print(header)
            for animation, angle, name, measured in rows[:args.top or None]:
                print('  ' + name.ljust(34) + ' '.join(
                    f'{measured[field]:6.0f}' if field in ('step', 'pixels') else f'{measured[field]:6.3f}'
                    for field in FIELDS))
    if args.csv:
        with args.csv.open('w', newline='') as handle:
            writer = csv.writer(handle)
            writer.writerow(('sheet', 'animation', 'angle_set', 'id') + FIELDS)
            for root, animation, angle, name, measured in table:
                writer.writerow((root, animation, angle, name) + tuple(round(measured[f], 4) for f in FIELDS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
