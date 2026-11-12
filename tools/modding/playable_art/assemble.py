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
OPTIONAL = {'BLINK': (2, 'next'), 'GET_UP': (4, 'next'), 'FLY': (2, 'loop'), 'TAILS_FLY': (2, 'loop'),
            'TAILS_FLY_TIRED': (12, 'loop')}

OPTIONAL |= {name: (8, 'loop') for name in (
    'SUPER_TRANSFORM', 'TAILS_FLY_ASCEND', 'TAILS_FLY_CARRY', 'TAILS_FLY_CARRY_ASCEND',
    'TAILS_SWIM', 'TAILS_SWIM_ASCEND', 'TAILS_SWIM_CARRY', 'TAILS_SWIM_TIRED',
    'ROLL2', 'WARP1', 'WARP2', 'WARP3', 'WARP4', 'FLOAT', 'FLOAT2', 'FLOAT3',
    'FLOAT4', 'LEAP1', 'LEAP2', 'SURF', 'BURNT', 'SHRINK', 'WATER_SLIDE',
    'NULL_ANIM', 'BLANK', 'BLINK', 'GET_UP', 'BALANCE2', 'BALANCE3', 'BALANCE4',
    'HANG2', 'BUBBLE', 'HURT2', 'SLIDE', 'VICTORY', 'HURT_FALL', 'TAILS_CARRIED', 'GLIDE_SLIDE')}

SPEED = {'WALK', 'RUN', 'ROLL', 'ROLL2', 'PUSH'}
SLOPES = ('flat', 'up45', 'steep', 'inverted')
# Atlas cell per frame. Mappings are cut into 32x32 pieces, so the cell only bounds the source
# atlas layout; 64 rows fit tall forms such as a crest above a 37 px body.
CELL_W, CELL_H = 64, 64


def assemble(root, output):
    handoff = json.loads((root / 'frames.yaml').read_text())
    source = handoff['frames']
    character = handoff.get('character', 'mica')
    if not character.isidentifier() or not character.islower():
        raise ValueError('Invalid character output name')
    fixed = FIXED | OPTIONAL
    colors = json.loads((root / 'palette.yaml').read_text())['colors']
    palette = sum(colors, [])
    sheet = Image.new('P', (8 * CELL_W, ((len(source) + 7) // 8) * CELL_H), 0)
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
            if max(image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()) > 15 or image.width > CELL_W or image.height > CELL_H:
                raise ValueError('Frame exceeds indexed cell bounds: ' + frame['id'])
            sx, sy = (i % 8) * CELL_W, (i // 8) * CELL_H
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
        if frame['animation'] in {'HOMING_DIVE', 'HOMING_DIVE_FORWARD', 'HOMING_DIVE_FORWARD_UP',
                                  'HOMING_DIVE_UP', 'HOMING_DIVE_FORWARD_DOWN', 'HOMING_DIVE_DOWN'}:
            custom.setdefault(frame['animation'], []).append(i)
        elif frame['animation'] in SPEED | fixed.keys():
            groups[frame['animation']].append((frame, i))
        elif frame['animation'] != 'NATIVE_POSE':
            raise ValueError('Unknown animation: ' + frame['animation'])
    required = set(handoff.get('requiredAnimations', (SPEED - {'ROLL2'}) | FIXED.keys()))
    # Scripts may reuse drawings already owned by another canonical cycle.
    for name in handoff.get('scripts', {}):
        if name in fixed:
            groups.setdefault(name, [])
    if not required <= SPEED | fixed.keys() or not required <= set(groups):
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
                if cycles[0] and not any(cycles[1:]):
                    # Flat-only first sheets: the converter repeats the flat cycle on every slope.
                    spec.update(frames=cycles[0])
                elif not cycles[0] or len({len(c) for c in cycles}) != 1 or len(cycles[0]) % 2:
                    raise ValueError('Four equal even slope cycles required')
                else:
                    spec.update(frames=cycles[0], slopes=cycles)
        else:
            ticks, end = fixed[name]
            if name == 'SPRING' and len(indices) == 1:
                ticks = 48  # SonAni_Spring has one mapping and delay $2F + 1.
            spec.update(durations=[ticks]*len(indices), end=end)
            if end == 'next':
                spec['next'] = 'WALK'
            if name == 'WAIT':
                # Legacy four-pose handoffs retain the 31*6-tick stock wait.
                # Authored scripts can animate that interval using repeated poses.
                spec['durations'] = [186, 6, 30, 18]
            if name == 'GET_AIR' and not handoff.get('scripts', {}).get(name):
                spec.update(frames=[indices[0], indices[0], indices[1], indices[1]], durations=[12]*4)
        # Preserve native interleaves using symbolic art IDs after sheet packing.
        if name in ('ROLL', 'SPINDASH'):
            authored = handoff.get('scripts', {}).get(name)
            if authored:
                by_id = {f['id']: i for f, i in entries}
                spec['frames'] = [by_id[key] for key in authored['frames']]
                if name == 'SPINDASH':
                    spec['durations'] = [1] * len(spec['frames'])
        # Other characters may supply their own native timer bytes and script order.
        authored = handoff.get('scripts', {}).get(name)
        if authored:
            by_id = {f['id']: i for i, f in enumerate(source)}
            steps = authored['frames']
            if not steps or any(key not in by_id for key in steps):
                raise ValueError('Invalid authored script frames: ' + name)
            spec['frames'] = [by_id[key] for key in steps]
            if 'durations' in authored:
                durations = authored['durations']
                if len(durations) != len(steps) or any(type(t) is not int or not 1 <= t <= 256 for t in durations):
                    raise ValueError('Invalid authored script durations: ' + name)
                spec['durations'] = durations
            if authored.get('speedDriven'):
                spec['speedDriven'] = True
                spec.pop('durations', None)
            if 'end' in authored:
                if authored['end'] not in ('loop', 'loop-back', 'hold', 'next'):
                    raise ValueError('Invalid authored script end: ' + name)
                spec['end'] = authored['end']
                spec.pop('next', None)
                if authored['end'] == 'loop-back':
                    count = authored.get('loopBackSteps')
                    if type(count) is not int or not 1 <= count <= len(steps):
                        raise ValueError('Invalid loopBackSteps: ' + name)
                    spec['loopBackSteps'] = count
                if authored['end'] == 'next':
                    if authored.get('next') not in groups:
                        raise ValueError('Invalid next animation: ' + name)
                    spec['next'] = authored['next']
        animations[name] = spec
    for name, indices in custom.items():
        animations[name] = dict(frames=indices, durations=[2]*len(indices), end='loop')
    frame_indices = {frame['id']: i for i, frame in enumerate(source)}
    native_frames = {}
    for key, frame_id in handoff.get('nativeFrames', {}).items():
        if frame_id not in frame_indices:
            raise ValueError('Unknown native pose frame: ' + str(frame_id))
        native_frames[key] = frame_indices[frame_id]
    manifest = dict(formatVersion=1, paletteLine=0,
                    palette=['#%02X%02X%02X' % tuple(c) for c in colors],
                    frames=frames, animations=animations, nativeFrames=native_frames)
    output.mkdir(parents=True, exist_ok=True)
    sheet.save(output / f'{character}.png', transparency=0, bits=8)
    (output / f'{character}-sheet.yaml').write_text(json.dumps(manifest, indent=2)+'\n')
    # Frame inventory for reviewers and custom animation consumers.
    (output / f'{character}-custom-frames.json').write_text(json.dumps(custom, indent=2)+'\n')
    stats = dict(frames=len(frames), maxTilesPerFrame=max_tiles, animations=len(animations))
    print(json.dumps(stats))
    return manifest, custom, stats


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    assemble(args.root, args.output)
