#!/usr/bin/env python3
"""Purpose: count powered script/frame obligations before drawing MICA Gilded.
Inputs: pinned S2/S3K animation, mapping and DPLC disassembly sources.
Origin: owner-directed powered-pose lane, 2026-11-10. Research only; no runtime assets.
Outputs are comparison references under scratch; never commit decoded native pixels.
"""
import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]

def load(path, name):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def block(text, label):
    return re.split(r'\n[\w.]+:', text.split(label + ':', 1)[1], maxsplit=1)[0]


def script(text, label):
    values = []
    for line in block(text, label).splitlines():
        if 'dc.b' in line:
            values.extend(int(x.strip().replace('$', '0x'), 0)
                          for x in line.split('dc.b', 1)[1].split(';')[0].split(',') if x.strip())
    delay, *values = values
    frames = []
    for i, value in enumerate(values):
        if value >= 0xF0:
            return dict(delay=delay, frames=frames, terminator=value,
                        parameter=values[i + 1] if value in (0xFD, 0xFE) else 0)
        frames.append(value)
    raise ValueError(label)


def inventory():
    s2 = (ROOT / 'docs/s2disasm/s2.asm').read_text()
    s3 = (ROOT / 'docs/skdisasm/General/Sprites/Sonic/Anim - Sonic.asm').read_text()
    rows = []
    for game, text, normal, powered in [('S2', s2, 'SonicAniData', 'SuperSonicAniData'),
                                       ('S3K', s3, 'AniSonic_', 'AniSuperSonic')]:
        if game == 'S2':
            labels = lambda name: re.findall(r'offsetTableEntry.w\s+(\w+)', text.split(name + ':', 1)[1].split('\n\n', 1)[0])
        else:
            labels = lambda name: re.findall(r'dc.w\s+(\w+)-' + name, block(text, name))
        ordinary = labels(normal)
        special = labels(powered)
        assert len(ordinary) == (34 if game == 'S2' else 36), (game, len(ordinary))
        assert len(special) == 32
        for i, label in enumerate(ordinary):
            selected = special[i] if i < len(special) else label
            row = dict(game=game, animation=i, label=selected, normal_label=label,
                       script_shared=selected == label, **script(text, selected))
            frames = set(row['frames'])
            # SAnim_Super/SAnim_SuperWalk and loc_12766/loc_12780.
            if i == 0:
                frames = {f + slope * 8 for f in frames for slope in range(4)}
                if game == 'S2': frames |= {f + 0x20 for f in frames}
            elif i == 1:
                frames = {f + slope * (1 if game == 'S2' else 4) for f in frames for slope in range(4)}
            row['reachable_frames'] = sorted(frames)
            rows.append(row)
    return rows


def export_references(out, rows):
    """Render mapped/DPLC comparison inputs and count identical pixel geometry."""
    from PIL import Image
    native = load('tools/modding/playable_art/native_pose.py', 'powered_native')
    ending = load('examples/mica-and-talon/art/mica/endings/s3k/build.py', 'powered_s3k')
    maps = ending.r.mapping(ending.MAP, 'Map_SuperSonic_')
    normal_maps = ending.r.mapping(ending.MAP, 'Map_Sonic_')
    plcs = ending.r.dplc(ending.DPLC, 'PLC_SuperSonic_')
    normal_plcs = ending.r.dplc(ending.DPLC, 'PLC_Sonic_')
    raw = (ROOT / 'docs/skdisasm/General/Sprites/Sonic/Art/Sonic.unc').read_bytes()
    records = []
    for game in ('S2', 'S3K'):
        frames = sorted({f for row in rows if row['game'] == game for f in row['reachable_frames']})
        seen = {}
        for frame in frames:
            if game == 'S2':
                if frame == 0:
                    image, hotspot, label = Image.new('RGBA', (1, 1)), [0, 0], 'Map_Sonic_01AC'
                else:
                    image, hotspot, label = native.player(frame)
                colors = native.palette(ROOT / 'docs/s2disasm/art/palettes/SonicAndTails.bin')
                words = (ROOT / 'docs/s2disasm/art/palettes/Super Sonic transformation.bin').read_bytes()[0x30:0x38]
                gold = [c[:] for c in colors]
                for slot in range(4):
                    word = int.from_bytes(words[slot * 2:slot * 2 + 2], 'big')
                    gold[2 + slot] = [round(((word >> shift) & 14) * 255 / 14) for shift in (0, 4, 8)]
                swaps = {tuple(old): tuple(new) for old, new in zip(colors, gold)}
                image.putdata([(*swaps.get(pixel[:3], pixel[:3]), pixel[3]) for pixel in image.get_flattened_data()])
                shared = all(row['script_shared'] for row in rows if row['game'] == game and frame in row['reachable_frames'])
            else:
                label, pieces = maps[frame]
                _, loads = plcs[frame]
                if not pieces:
                    image = Image.new('RGBA', (1, 1))
                    hotspot = [0, 0]
                else:
                    data = b''.join(raw[t * 32:(t + n) * 32] for n, t in loads)
                    colors = native.palette(ROOT / 'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')
                    text = (ROOT / 'docs/skdisasm/sonic3k.asm').read_text().split('\nPalCycle_SuperSonic:', 1)[1].split('\nPalCycle_SuperSonicUnderwater', 1)[0]
                    words = re.findall(r'dc.w\s+([^\n;]+)', text)[6].split(',')
                    for slot, word in enumerate(words, 2):
                        value = native.num(word)
                        colors[slot] = [round(((value >> shift) & 14) * 255 / 14) for shift in (0, 4, 8)]
                    required = max(t + w * h for x, y, w, h, t, *_ in pieces)
                    if required * 32 > len(data):
                        records.append(dict(id=f'{game.lower()}-{frame:02x}', game=game, frame=frame,
                                            label=label, normal_geometry_shared=(maps[frame][1] == normal_maps[frame][1] and plcs[frame][1] == normal_plcs[frame][1]),
                                            contextual_bank_required=True, identical_reference=None,
                                            note='Mapping addresses beyond the supplied DPLC bank; contextual rendering required, isolated render omitted.'))
                        continue
                    image = ending.native(pieces, data, colors).convert('RGBA')
                    box = ending.bounds(pieces)
                    hotspot = [-box[0], -box[1]]
                shared = maps[frame][1] == normal_maps[frame][1] and plcs[frame][1] == normal_plcs[frame][1]
            key = f'{game.lower()}-{frame:02x}'
            fingerprint = hashlib.sha256(bytes(str((image.size, hotspot)), 'utf8') + image.tobytes()).hexdigest()
            alias = seen.get(fingerprint)
            seen.setdefault(fingerprint, key)
            image.save(out / (key + '.png'))
            image.resize((image.width * 8, image.height * 8), Image.Resampling.NEAREST).save(out / (key + '-8x.png'))
            records.append(dict(id=key, frame=frame, game=game, label=label, size=list(image.size),
                                hotspot=hotspot, opaque_area=sum(a >= 128 for a in image.getchannel('A').get_flattened_data()),
                                normal_geometry_shared=shared, identical_reference=alias, sha256=fingerprint))
    (out / 'references.json').write_text(json.dumps(records, indent=2) + '\n')
    return {game: dict(contextual_references=sum(r['game'] == game and r.get('contextual_bank_required', False) for r in records), unique_geometry=sum(r['game'] == game and not r.get('contextual_bank_required', False) and not r['identical_reference'] for r in records),
                       distinct_powered_geometry=sum(r['game'] == game and not r.get('contextual_bank_required', False) and not r['normal_geometry_shared'] and not r['identical_reference'] for r in records))
            for game in ('S2', 'S3K')}


def compare_shared_poses(out):
    """Compare shared-script silhouettes at the native hotspot; no native pixels enter Git."""
    from PIL import Image
    ending = load('examples/mica-and-talon/art/mica/endings/s3k/build.py', 'identity_ending')
    native = load('tools/modding/playable_art/native_pose.py', 'identity_native')
    maps = ending.r.mapping(ending.MAP, 'Map_Sonic_')
    plcs = ending.r.dplc(ending.DPLC, 'PLC_Sonic_')
    raw = (ROOT / 'docs/skdisasm/General/Sprites/Sonic/Art/Sonic.unc').read_bytes()
    colors = native.palette(ROOT / 'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')
    refs = {r['id']: r for r in json.loads((out / 'references.json').read_text())}

    def silhouette(image, hotspot):
        return {(x - hotspot[0], y - hotspot[1])
                for y in range(image.height) for x in range(image.width)
                if image.getpixel((x, y))[3] >= 128}

    result = []
    for row in inventory():
        if not row['script_shared']:
            continue
        frames = []
        for frame in row['reachable_frames']:
            ref = refs[f'{row["game"].lower()}-{frame:02x}']
            same = ref['normal_geometry_shared']
            if ref.get('contextual_bank_required'):
                same = False
                equal = None
                reason = 'Retained bank tile required; isolated reference omitted. Not verified as a normal pose alias.'
            elif same:
                equal = True
                reason = ('Identical mapping and DPLC (S2 also shares source art bank); '
                          'same hotspot and silhouette by identity.')
            else:
                pieces = maps[frame][1]
                data = b''.join(raw[t * 32:(t + n) * 32] for n, t in plcs[frame][1])
                image = ending.native(pieces, data, colors).convert('RGBA')
                box = ending.bounds(pieces)
                hotspot = [-box[0], -box[1]]
                powered = Image.open(out / f's3k-{frame:02x}.png').convert('RGBA')
                equal = silhouette(image, hotspot) == silhouette(powered, ref['hotspot'])
                reason = 'Rendered alpha coordinates compared relative to the native centre hotspot.'
            frames.append(dict(frame=frame, normal_geometry_identity=same,
                               silhouette_hotspot_equal=equal, evidence=reason))
        result.append(dict(game=row['game'], animation=row['animation'],
                           label=row['label'], frames=frames))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--references', action='store_true')
    parser.add_argument('--compare-poses', action='store_true')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(Path.home() / 'scratch/agent-tmp'):
        raise ValueError('Native comparison data belongs in scratch/agent-tmp')
    out.mkdir(parents=True, exist_ok=True)
    rows = inventory()
    if args.compare_poses:
        print(json.dumps(compare_shared_poses(out), indent=2))
        return
    summary = {}
    for game in ('S2', 'S3K'):
        subset = [r for r in rows if r['game'] == game]
        frames = sorted({f for r in subset for f in r['reachable_frames']})
        unique = sorted({f for r in subset if not r['script_shared'] for f in r['reachable_frames']})
        summary[game] = dict(rows=len(subset), unique_script_overrides=sum(not r['script_shared'] for r in subset),
                             reachable_frame_count=len(frames), override_frame_count=len(unique),
                             reachable_frames=frames, override_frames=unique)
    (out / 'inventory.json').write_text(json.dumps(dict(summary=summary, rows=rows), indent=2) + '\n')
    if args.references:
        print(json.dumps(export_references(out, rows), indent=2))
    else:
        print(json.dumps({g: {k: v for k, v in d.items() if not isinstance(v, list)} for g, d in summary.items()}, indent=2))


if __name__ == '__main__':
    main()
