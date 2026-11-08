#!/usr/bin/env python3
"""Purpose: assemble indexed native poses into converter PNG/YAML, without ROM assets.
Inputs: art directory (frames.yaml, palette.yaml, frames/*.png), output directory.
Origin: original-character W2/W7 integration, 2026-11-08.
"""
import argparse
from collections import defaultdict
import json
from pathlib import Path
from PIL import Image

# S2 Sonic is the shared authored timing reference. The host supplies velocity timing.
# SonAni_Walk through SonAni_Hurt (docs/s2disasm/s2.asm:38692-38754).
# Native timer values are delay-minus-one. Art phases are original MICA phases.
FIXED = {'WAIT': (6, 'loop'), 'SPINDASH': (1, 'loop'), 'DUCK': (6, 'hold'),
         'LOOK_UP': (6, 'hold'), 'SKID': (6, 'next'), 'STOP': (6, 'next'),
         'SPRING': (24, 'next'), 'BALANCE': (10, 'loop'), 'HURT': (65, 'loop'),
         'DEATH': (33, 'loop'), 'DROWN': (33, 'loop'), 'GET_AIR': (12, 'next'),
         'HANG': (2, 'loop')}
SPEED = {'WALK', 'RUN', 'ROLL', 'PUSH'}
SLOPES = ('flat', 'up45', 'steep', 'inverted')


def assemble(root, output):
    handoff = json.loads((root / 'frames.yaml').read_text())
    source = handoff['frames']
    colors = json.loads((root / 'palette.yaml').read_text())['colors']
    palette = sum(colors, [])
    sheet = Image.new('P', (512, ((len(source) + 7) // 8) * 48), 0)
    sheet.putpalette(palette + [0] * (768 - len(palette)))
    frames, groups, custom = [], defaultdict(list), {}
    max_tiles = 0
    ids = set()
    for i, frame in enumerate(source):
        if frame['id'] in ids:
            raise ValueError('Duplicate frame id: ' + frame['id'])
        ids.add(frame['id'])
        with Image.open(root / frame['file']) as image:
            if image.mode != 'P' or image.getpalette()[:48] != palette or image.info.get('transparency') != 0:
                raise ValueError('Frame must use the shared indexed palette: ' + frame['id'])
            if max(image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()) > 15 or image.width > 64 or image.height > 48:
                raise ValueError('Frame exceeds indexed cell bounds: ' + frame['id'])
            sx, sy = (i % 8) * 64, (i // 8) * 48
            sheet.paste(image, (sx, sy))
            w, h = (image.width + 7) // 8 * 8, (image.height + 7) // 8 * 8
        pieces = []
        for y in range(0, h, 32):
            for x in range(0, w, 32):
                pieces.append(dict(sourceX=sx+x, sourceY=sy+y,
                                   widthPixels=min(32, w-x), heightPixels=min(32, h-y),
                                   xOffset=x-frame['hotspot'][0], yOffset=y-frame['hotspot'][1],
                                   hFlip=False, vFlip=False, paletteIndex=0, priority=False))
        max_tiles = max(max_tiles, w*h//64)
        frames.append(dict(delay=1, pieces=pieces))
        if frame['animation'] == 'HOMING_DIVE':
            custom.setdefault('HOMING_DIVE', []).append(i)
        elif frame['animation'] in SPEED | FIXED.keys():
            groups[frame['animation']].append((frame, i))
        else:
            raise ValueError('Unknown animation: ' + frame['animation'])
    if set(groups) != SPEED | FIXED.keys():
        raise ValueError('Missing canonical core animations')
    animations = {}
    for name, entries in groups.items():
        entries.sort(key=lambda item: item[0]['order'])
        indices = [i for _, i in entries]
        spec = dict(frames=indices, end='loop')
        if name in SPEED:
            spec['speedDriven'] = True
            if name in ('WALK', 'RUN'):
                cycles = [[i for f, i in entries if f['angle_set'] == slope] for slope in SLOPES]
                if not cycles[0] or len({len(c) for c in cycles}) != 1 or len(cycles[0]) % 2:
                    raise ValueError('Four equal even slope cycles required')
                spec.update(frames=cycles[0], slopes=cycles)
        else:
            ticks, end = FIXED[name]
            spec.update(durations=[ticks]*len(indices), end=end)
            if end == 'next':
                spec['next'] = 'WALK'
            if name == 'WAIT':
                # Legacy four-pose handoffs retain the 31*6-tick stock wait.
                # Authored scripts can animate that interval using repeated poses.
                spec['durations'] = [186, 6, 30, 18]
                authored = handoff.get('scripts', {}).get('WAIT')
                if authored:
                    by_id = {f['id']: i for f, i in entries}
                    steps, durations = authored['frames'], authored['durations']
                    if (not steps or len(steps) != len(durations)
                            or any(type(t) is not int or not 1 <= t <= 256 for t in durations)
                            or authored['end'] not in ('loop', 'hold', 'next')):
                        raise ValueError('Invalid authored WAIT script')
                    spec = dict(frames=[by_id[key] for key in steps],
                                durations=durations, end=authored['end'])
                    if spec['end'] == 'next':
                        if authored.get('next') not in SPEED | FIXED.keys():
                            raise ValueError('Invalid WAIT next animation')
                        spec['next'] = authored['next']
            if name == 'GET_AIR':
                spec.update(frames=[indices[0], indices[0], indices[1], indices[1]], durations=[12]*4)
        animations[name] = spec
    manifest = dict(formatVersion=1, paletteLine=0,
                    palette=['#%02X%02X%02X' % tuple(c) for c in colors],
                    frames=frames, animations=animations)
    output.mkdir(parents=True, exist_ok=True)
    sheet.save(output / 'mica.png', transparency=0, bits=8)
    (output / 'mica-sheet.yaml').write_text(json.dumps(manifest, indent=2)+'\n')
    # Separate hook, deliberately not a canonical animation enum or converter input.
    (output / 'mica-custom-frames.json').write_text(json.dumps(custom, indent=2)+'\n')
    stats = dict(frames=len(frames), maxTilesPerFrame=max_tiles, animations=len(animations))
    print(json.dumps(stats))
    return manifest, custom, stats


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    assemble(args.root, args.output)
