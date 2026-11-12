#!/usr/bin/env python3
"""Purpose: score every frame of an indexed sheet against the sheet's own crisp reference poses,
so "this pose looks like another artist drew it" becomes a list of frames and of what is off.
Inputs: a profile JSON naming, per sheet, its directory, material ramps, reference animations,
animations to skip and the frames accepted as they are. Output: a ranked table on stdout,
optionally JSON. Origin: owner line-art feedback on PR #287, 2026-11-12. Pillow; no ROM or Java.
Sibling of noise_audit.py, whose iso and ramp measures it reuses.

Every pose here was reduced on its own from a separately drawn source, so line weight and the
number of shading bands that survive depend on that drawing and on how far it was shrunk.
Absolute thresholds cannot tell a deliberate style from a damaged one; the sheet's references
can. Each measure is compared with its median over the reference frames, in units of their
spread (1.4826 x the median absolute deviation, floored so a tight reference set does not flag
noise), and a frame is flagged when a measure is more than LIMIT spreads off on its bad side.
A reference frame can be flagged too (the references are not identical); that is shown, and is
never a --check failure, because the references are the standard.

Measures, over a frame's opaque pixels (index 0 transparent, `ink` the contour index or indices):
  gap      share of the silhouette (opaque beside transparency) that is not ink: outline breaks.
  weight   share of the body pixels touching ink that are the darkest shade of their ramp: a dark
           rim inside the outline reads as a heavier line.
  bands    shades holding at least a tenth of their ramp, averaged over the ramps by size: how many
           shading bands survived.
  ramp     distance between the frame's use of each ramp's shades and the references' mean use
           (total variation, weighted by ramp size): the same palette shaded another way.
  cluster  mean size of a same-index patch, in pixels.
  iso      isolated pixels; step, one-pixel luminance steps (noise_audit.py iso and ramp).
gap, weight, ramp, iso and step are bad when high; bands and cluster when far off either way.

Usage:
  style_audit.py PROFILE [--sheet NAME] [--top N] [--summary] [--json FILE]
  style_audit.py PROFILE --check     fail when a frame is flagged that the profile does not accept
"""
import argparse
import json
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import noise_audit  # noqa: E402

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
LIMIT = 3.0
MEASURES = ('gap', 'weight', 'bands', 'ramp', 'cluster', 'iso', 'step')
HIGH_IS_BAD = {'gap', 'weight', 'ramp', 'iso', 'step'}
FLOOR = dict(gap=0.02, weight=0.05, bands=0.5, ramp=0.06, cluster=0.5, iso=0.03, step=0.03)


def shares(grid, ramps):
    """ramp name -> (pixels, {index: share within the ramp})."""
    counts = Counter(v for row in grid for v in row)
    out = {}
    for name, indices in ramps.items():
        total = sum(counts[i] for i in indices)
        out[name] = (total, {i: counts[i] / total for i in indices} if total else {})
    return out


def measure(grid, lum, ramps, ink=1, reference=None):
    """One frame's measures; `ramp` needs the reference shares and is None without them.
    ink is the contour index, or several (a powered bank outlines on ink and on its cycling contour slot)."""
    height, width = len(grid), len(grid[0])
    inks = {ink} if isinstance(ink, int) else set(ink)

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

    opaque = [(x, y) for y in range(height) for x in range(width) if grid[y][x]]
    body = [(x, y) for x, y in opaque if grid[y][x] not in inks]
    if not body:
        return None
    darkest = {i: min(indices, key=lambda k: lum[k]) for indices in ramps.values() for i in indices}
    silhouette = [(x, y) for x, y in opaque if any(at(x + a, y + b) == 0 for a, b in N4)]
    beside = [(x, y) for x, y in body if any(at(x + a, y + b) in inks for a, b in N4)]
    rim = sum(1 for x, y in beside if darkest.get(grid[y][x]) == grid[y][x] and len(
        next(v for v in ramps.values() if grid[y][x] in v)) > 1)
    seen, patches = set(), 0
    for start in body:
        if start in seen:
            continue
        patches += 1
        value, todo = grid[start[1]][start[0]], [start]
        seen.add(start)
        while todo:
            cx, cy = todo.pop()
            for a, b in N4:
                near = (cx + a, cy + b)
                if near not in seen and at(*near) == value:
                    seen.add(near)
                    todo.append(near)
    used = shares(grid, ramps)
    shaded = {name: item for name, item in used.items() if len(ramps[name]) > 1 and item[0]}
    weight_total = sum(pixels for pixels, _ in shaded.values()) or 1
    bands = sum(pixels / weight_total * sum(1 for share in have.values() if share >= 0.1)
                for pixels, have in shaded.values())
    noise = noise_audit.metrics(grid, lum)
    result = dict(gap=sum(grid[y][x] not in inks for x, y in silhouette) / len(silhouette),
                  weight=rim / (len(beside) or 1), bands=bands,
                  cluster=len(body) / patches, iso=noise['iso'], step=noise['ramp'], ramp=None, pixels=len(opaque))
    if reference:
        total = sum(pixels for pixels, _ in used.values()) or 1
        result['ramp'] = sum(pixels / total * 0.5 * sum(abs(have.get(i, 0) - reference[name].get(i, 0))
                                                         for i in ramps[name])
                             for name, (pixels, have) in used.items() if pixels)
    return result


def load(path):
    with Image.open(path) as image:
        if image.mode != 'P':
            raise ValueError(f'{path} is not an indexed PNG')
        pixels = image.load()
        grid = [[pixels[x, y] for x in range(image.width)] for y in range(image.height)]
        return grid, noise_audit.luminance(image.getpalette())


def frames_of(root, sheet):
    """(id, animation, path) of the frames a profile sheet names: a frames.yaml manifest or a glob."""
    if 'glob' in sheet:
        return [(path.stem, sheet.get('animation', 'ART'), path) for path in sorted(root.glob(sheet['glob']))]
    manifest = json.loads((root / 'frames.yaml').read_text())['frames']
    return [(f.get('id') or Path(f['file']).stem, f['animation'], (root / f['file']).resolve())
            for f in manifest if f.get('angle_set', 'flat') == 'flat']


def audit(profile_path, name):
    """rows: id -> dict(animation, measures, z, flags); plus the reference medians and spreads."""
    profile = json.loads(Path(profile_path).read_text())
    sheet = profile['sheets'][name]
    root = (Path(profile_path).resolve().parent / sheet['root']).resolve()
    ramps = {k: tuple(v) for k, v in sheet['ramps'].items()}
    ink = sheet.get('ink', 1)
    frames = [f for f in frames_of(root, sheet) if f[1] not in sheet.get('skip', ())]
    loaded = {fid: load(path) for fid, _, path in frames}
    is_reference = {fid: animation in sheet['reference'] or fid in sheet['reference'] for fid, animation, _ in frames}
    pooled = defaultdict(lambda: defaultdict(list))
    for fid, flag in is_reference.items():
        if flag:
            for ramp, (pixels, have) in shares(loaded[fid][0], ramps).items():
                if pixels:
                    for index, share in have.items():
                        pooled[ramp][index].append(share)
    reference = {ramp: {index: statistics.mean(values) for index, values in by_index.items()}
                 for ramp, by_index in pooled.items()}
    for ramp in ramps:
        reference.setdefault(ramp, {})
    rows = {}
    for fid, animation, _ in frames:
        grid, lum = loaded[fid]
        measured = measure(grid, lum, ramps, ink, reference)
        if measured:
            rows[fid] = dict(animation=animation, reference=is_reference[fid], **measured)
    centre, spread = {}, {}
    if not any(row['reference'] for row in rows.values()):
        raise SystemExit(f'{name}: no frame matches the reference animations or ids {sheet["reference"]}')
    for key in MEASURES:
        values = [row[key] for row in rows.values() if row['reference']]
        centre[key] = statistics.median(values)
        spread[key] = max(1.4826 * statistics.median(abs(v - centre[key]) for v in values), FLOOR[key])
    for row in rows.values():
        row['z'] = {key: (row[key] - centre[key]) / spread[key] for key in MEASURES}
        row['flags'] = [key for key in MEASURES
                        if (row['z'][key] if key in HIGH_IS_BAD else abs(row['z'][key])) > LIMIT]
        row['worst'] = max((row['z'][key] if key in HIGH_IS_BAD else abs(row['z'][key])) for key in MEASURES)
    return rows, centre, spread, sheet


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('profile', type=Path)
    parser.add_argument('--sheet', action='append', help='sheet name from the profile (default: all)')
    parser.add_argument('--top', type=int, default=20, help='flagged frames to list per sheet (0 for all)')
    parser.add_argument('--summary', action='store_true', help='group by animation')
    parser.add_argument('--json', type=Path, help='write every row of every sheet')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    names = args.sheet or list(json.loads(args.profile.read_text())['sheets'])
    dump, unexpected = {}, []
    for name in names:
        rows, centre, spread, sheet = audit(args.profile, name)
        dump[name] = dict(reference=dict(median=centre, spread=spread), frames=rows)
        flagged = {fid: row for fid, row in rows.items() if row['flags']}
        accepted = sheet.get('accepted', {})
        unexpected += [f'{name}/{fid} ({", ".join(row["flags"])})' for fid, row in flagged.items()
                       if not row['reference'] and fid not in accepted and row['animation'] not in accepted]
        if args.check:
            continue
        references = sum(row['reference'] for row in rows.values())
        print(f"{name}: {len(rows)} frames, {references} reference ({', '.join(sheet['reference'])}); "
              f"{len(flagged)} flagged beyond {LIMIT:g} spreads")
        print('  ' + 'reference median'.ljust(34) + ' '.join(f'{centre[k]:7.2f}' for k in MEASURES))
        print('  ' + 'reference spread'.ljust(34) + ' '.join(f'{spread[k]:7.2f}' for k in MEASURES))
        print('  ' + ('animation' if args.summary else 'frame').ljust(34) + ' '.join(k.rjust(7) for k in MEASURES) +
              '   flagged')
        if args.summary:
            groups = defaultdict(list)
            for row in rows.values():
                groups[row['animation']].append(row)
            for animation, members in sorted(groups.items(), key=lambda kv: -statistics.mean(r['worst'] for r in kv[1])):
                hit = Counter(flag for row in members for flag in row['flags'])
                print('  ' + f'{animation} x{len(members)}'.ljust(34) +
                      ' '.join(f'{statistics.mean(r[k] for r in members):7.2f}' for k in MEASURES) + '   ' +
                      (', '.join(f'{k} {n}' for k, n in hit.most_common()) or '-'))
        else:
            ranked = sorted(flagged.items(), key=lambda kv: -kv[1]['worst'])
            for fid, row in ranked[:args.top or None]:
                print('  ' + fid.ljust(34) + ' '.join(f'{row[k]:7.2f}' for k in MEASURES) + '   ' +
                      ', '.join(f"{k} {row['z'][k]:+.1f}" for k in row['flags']))
    if args.json:
        args.json.write_text(json.dumps(dump, indent=1) + '\n')
    if args.check:
        if unexpected:
            raise SystemExit('Frames off the reference style and not accepted: ' + '; '.join(unexpected))
        print(f"style audit: no unaccepted outlier in {', '.join(names)}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
