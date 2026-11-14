#!/usr/bin/env python3
"""TALON's own idle, both characters' signpost plates and the victory pass.

Purpose: the owner asked for idle animations that are the characters' own instead of copies of
Sonic's and Tails', for signposts that read at 48x32 and match across the three games, and for
victory poses that read clearly (2026-11-12). The owner kept MICA's idle as it is and approved
TALON's "preen and ruffle": he preens a flight feather from base to tip, winds up into a puffed
ruffle with three slowing shakes, watches a loose feather sway down, snatches it and flicks it away.
Inputs: idle-sign-victory.json beside this file: the generation provenance (model, prompts, raw
hashes, the cell each drawing came from and its gate numbers), the loose feather, where it is
composited, TALON's WAIT script and native-frame map, and both characters' sign portraits.
Origin: idle, sign and victory lane, 2026-11-12. Pillow; no ROM, Java or source generation needed.
Run from the repository root; --check compares without writing.

What this tool owns and writes:
  * the eight feather frames of TALON's idle (wait-13..20): the loose feather composited on his
    finished drawings at the recorded offsets, so its sway costs no extra drawings;
  * wait-21, his existing LOOK_UP drawing on the idle's ground line: look_up-00 stands three pixels
    higher about its hotspot than the idle frames, which would make the loop hop;
  * TALON's WAIT script and the family-wait/* native-frame map, in frames.yaml and recipe.json;
  * the signpost plates of both characters: one portrait per character on all four face plates
    (S1, S2, S2 alternate, S3K) and, squeezed onto the turning panel as presentation.py spin()
    does, on the six spin plates. Backings are not touched: they are native host colours.
What it does not own: the twelve new idle drawings and the four redrawn victory drawings are
ordinary drawings of pose-cleanup.json and lineart-pass.json, so talon/pose_cleanup.py cleans them,
gives them WAIT's head and writes them, like every other pose; --check here only confirms those
records still hold the rows this lane selected. MICA's victory frames (variant-s3k-b0..b3) keep
their drawings and are cleaned by mica/sheet_despeckle.py.

Source drawings: flat colour with near-black interior lines about one sprite pixel thick, no pixel
grid, generated fresh from the characters' own sprites (GPT Image 2.6 Sunburst), reduced by
tools/modding/playable_art/pipeline.py with one factor per sheet taken from its standing cell.
TALON's plumage is then moved one step down his ramp, because the prompts named slate as the base
shade and his sheet's base is slate-shadow. The raw drawings stay outside Git.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
MOD = ROOT / 'examples/mica-and-talon'
RECORD = Path(__file__).resolve().parent / 'idle-sign-victory.json'
SHEET = MOD / 'art/talon/sheet'
TALON = MOD / 'production/talon'
SIGNS = ('s1-sign', 's2-sign', 's2-sign-alternate', 's3k-sign')
SPINS = (('s1-spin1', False), ('s1-spin3', True), ('s2-spin1', False), ('s2-spin3', True), ('s3k-spin1', False), ('s3k-spin3', True))
REHOMED_NOTE = 'Idle 2026-11-12 (idle_sign_victory.py): the pixels of {base}, registered to the ground line of WAIT; see idle-sign-victory.json.'
FEATHER_NOTE = ('Idle 2026-11-12 (idle_sign_victory.py): {base} with the loose feather composited at {at}'
                '{mirrored}; see idle-sign-victory.json.')


def grid_of(rows):
    return [[int(v, 16) for v in row] for row in rows]


def rows_of(grid):
    return [''.join(format(v, 'x') for v in row) for row in grid]


def digest(rows):
    return hashlib.sha256('\n'.join(rows).encode()).hexdigest()


def image_of(grid, colors):
    image = Image.new('P', (len(grid[0]), len(grid)))
    image.putpalette(sum(colors, []) + [0] * (768 - 48))
    image.putdata([v for row in grid for v in row])
    return image


def read(path):
    with Image.open(path) as image:
        if image.mode != 'P':
            raise SystemExit(f'{path} is not an indexed PNG')
        flat = list(image.get_flattened_data())
        return [flat[i:i + image.width] for i in range(0, len(flat), image.width)]


def spin(face, flip):
    """The portrait on the turning panel: each row squeezed, nearest, as presentation.py spin()."""
    out = [[0] * 32 for _ in range(32)]
    for y in range(2, 30):
        left, width = 5 + (y - 2) // 14, 22 - (y - 2) // 7
        row = face[y][2:46]
        for i in range(width):
            out[y][left + i] = row[min(len(row) - 1, int((i + 0.5) * len(row) / width))]
    return [row[::-1] for row in out] if flip else out


def composite(base, hotspot, feather, at, mirrored):
    """(grid, hotspot) of a finished drawing with the loose feather placed `at` relative to its hotspot."""
    if mirrored:
        feather = [row[::-1] for row in feather]
    fx, fy = hotspot[0] + at[0], hotspot[1] + at[1]
    x0, y0 = min(0, fx), min(0, fy)
    x1, y1 = max(len(base[0]), fx + len(feather[0])), max(len(base), fy + len(feather))
    out = [[0] * (x1 - x0) for _ in range(y1 - y0)]
    for y, row in enumerate(base):
        for x, value in enumerate(row):
            out[y - y0][x - x0] = value
    for y, row in enumerate(feather):
        for x, value in enumerate(row):
            if value:
                out[fy + y - y0][fx + x - x0] = value
    return out, [hotspot[0] - x0, hotspot[1] - y0]


def script_of(record):
    steps = record['talon_idle']['script']['intro'] + record['talon_idle']['script']['loop']
    return dict(frames=[s['frame'] for s in steps], durations=[s['ticks'] for s in steps], end='loop-back',
                loopBackSteps=len(record['talon_idle']['script']['loop']))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args(argv)
    record = json.loads(RECORD.read_text())
    stale = []

    def put(path, grid, colors):
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == (len(grid[0]), len(grid)) and \
                list(actual.get_flattened_data()) == [v for row in grid for v in row] and \
                actual.getpalette()[:48] == sum(colors, []) and actual.info.get('transparency') == 0
        if not same:
            stale.append(path)
            if not args.check:
                image_of(grid, colors).save(path, transparency=0, optimize=True, bits=4)

    def sync(path, indent, edit):
        text = path.read_text()
        data = json.loads(text)
        edit(data)
        fresh = json.dumps(data, indent=indent) + '\n'
        if fresh != text:
            stale.append(path)
            if not args.check:
                path.write_text(fresh)

    # ---- the drawings this lane selected are still the ones pose_cleanup.py builds
    held = {d['id']: d['source_rows'] for d in json.loads((TALON / 'pose-cleanup.json').read_text())['drawings']}
    for group in ('talon_idle', 'talon_victory'):
        for fid, entry in record[group]['drawings'].items():
            if fid not in held or digest(held[fid]) != entry['rows_sha256']:
                raise SystemExit(f'pose-cleanup.json no longer holds the selected rows of {fid}')

    # ---- TALON: the feather frames, the WAIT script and the native-frame map
    colors = json.loads((SHEET / 'palette.yaml').read_text())['colors']
    listed = {f['id']: f for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    feather = grid_of(record['talon_idle']['feather']['rows'])
    made = {}
    for fid, spec in record['talon_idle']['reregistered'].items():
        made[fid] = (read(SHEET / listed[spec['base']]['file']), spec['hotspot'])
    for fid, spec in record['talon_idle']['composites'].items():
        grid, hotspot = made[spec['base']] if spec['base'] in made else (read(SHEET / listed[spec['base']]['file']), listed[spec['base']]['hotspot'])
        made[fid] = composite(grid, hotspot, feather, spec['at'], spec['mirrored'])
    for fid in made:
        if fid not in listed:
            raise SystemExit(f'frames.yaml has no entry for {fid}')
        put(SHEET / listed[fid]['file'], made[fid][0], colors)
    script, native = script_of(record), record['talon_idle']['native_frames']

    def note(fid):
        if fid in record['talon_idle']['reregistered']:
            return REHOMED_NOTE.format(base=record['talon_idle']['reregistered'][fid]['base'])
        spec = record['talon_idle']['composites'][fid]
        return FEATHER_NOTE.format(base=spec['base'], at=spec['at'], mirrored=', mirrored' if spec['mirrored'] else '')

    def handoff(data):
        for frame in data['frames']:
            if frame['id'] in made:
                frame.update(hotspot=made[frame['id']][1], notes=note(frame['id']))
        data['scripts']['WAIT'] = script
        data['nativeFrames'].update(native)

    def recipe(data):
        for frame in data['frames']:
            if frame['id'] in made:
                frame.update(notes=note(frame['id']),
                             native_pixels=dict(anchor=made[frame['id']][1], rows=rows_of(made[frame['id']][0])))
        data['scripts']['WAIT'] = script
        data['nativeFrames'].update(native)

    sync(SHEET / 'frames.yaml', 2, handoff)
    sync(TALON / 'recipe.json', 2, recipe)
    used = set(script['frames']) | set(native.values())
    idle = {f for f, entry in listed.items() if entry['animation'] == 'WAIT'}
    if idle - used:
        raise SystemExit('WAIT frames no script step or native frame shows: ' + ', '.join(sorted(idle - used)))

    # ---- both characters: the signpost plates
    for name in ('mica', 'talon'):
        sign = record['signs'][name]
        face = grid_of(sign['rows'])
        if (len(face[0]), len(face)) != (48, 32):
            raise SystemExit(f'{name} sign portrait is not 48x32')
        palette = json.loads((MOD / f'art/{name}/sheet/palette.yaml').read_text())['colors']
        plates = {plate: face for plate in SIGNS}
        plates.update({plate: spin(face, flip) for plate, flip in SPINS})
        for plate, grid in plates.items():
            put(MOD / f'art/{name}/hud-and-screens/{plate}.png', grid, palette)
        why = f"2026-11-12: redrawn by production/idle_sign_victory.py from {sign['source']}; see idle-sign-victory.json."

        def presentation(data, plates=plates, name=name, why=why):
            if name == 'talon':
                for image in data['images']:
                    key = image['file'].removeprefix('presentation/').removesuffix('.png')
                    if image['file'].startswith('presentation/') and key in plates:
                        image.update(rows=rows_of(plates[key]), redrawn=why)
            else:
                for key, grid in plates.items():
                    data['images'][key].update(rows=rows_of(grid), redrawn=why)

        sync(MOD / f'production/{name}/presentation/native-pose.json', 2, presentation)

    if args.check and stale:
        raise SystemExit('Stale idle, sign or victory art: ' + ', '.join(str(p.relative_to(ROOT)) for p in stale))
    drawings = len(record['talon_idle']['drawings']) + len(record['talon_victory']['drawings'])
    print(f"{drawings} selected drawings held, {len(made)} feather and re-registered frames and {2 * (len(SIGNS) + len(SPINS))} sign plates "
          + ('match' if args.check else f'written ({len(stale)} files changed)'))


if __name__ == '__main__':
    sys.exit(main())
