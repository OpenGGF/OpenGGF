#!/usr/bin/env python3
"""Purpose: inventory ROM animation scripts and review shared creator family phases.
Inputs: TestCharacterArtCoverageAudit JSON, optional external native PNG references.
Origin: animation-family consolidation, 2026-11-09. Native pixels stay outside Git.
"""
import argparse
import csv
import html
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw, GifImagePlugin

ROOT = Path(__file__).resolve().parents[2]


def load(path):
    return json.loads(path.read_text())


def labels(game, character):
    if game == 'S3K':
        path = Path(f'docs/skdisasm/General/Sprites/{character.title()}/Anim - {character.title()}.asm')
        text = (ROOT / path).read_text()
        names = re.findall(r'dc.w\s+(\w+)-', text)
    elif game == 'S2':
        path = Path('docs/s2disasm/s2.asm')
        text = (ROOT / path).read_text()
        start = text.index('SonicAniData:' if character == 'sonic' else 'TailsAniData:')
        names = re.findall(r'offsetTableEntry.w\s+(\w+)', text[start:])[:34 if character == 'sonic' else 33]
    else:
        path = Path('docs/s1disasm/_anim/Sonic.asm')
        text = (ROOT / path).read_text()
        names = re.findall(r'\bsonani\s+(SonAni_\w+)', text)
    lines = text.splitlines()
    return [f'{name} ({str(path).removeprefix("docs/" ).split("/", 1)[-1]}:{next(i for i, line in enumerate(lines, 1) if line.startswith(name+":"))})' for name in names]


def export_references(audit, destination):
    """Disposable native reference pixels; never a runtime asset fallback.

    Reuses the maintained native-pose decoder and the example's pinned S3K
    mapping/DPLC reader. Origin: batch-2 native reference capture method.
    """
    import functools
    import importlib.util
    spec = importlib.util.spec_from_file_location('family_native_pose', ROOT / 'tools/modding/playable_art/native_pose.py')
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    tables = native.load(ROOT / 'examples/mica-and-talon/production/mica/wave3/round5.py', 'family_native_tables')
    tables.mapping = functools.lru_cache()(tables.mapping)
    tables.dplc = functools.lru_cache()(tables.dplc)
    destination.mkdir(parents=True, exist_ok=True)
    refs = {}
    for game in ['S1', 'S2', 'S3K']:
        scripts = load(audit / f'mica-{game}.json')['nativeScripts']
        for index in sorted({frame for script in scripts.values() for frame in script['frames']}):
            if game == 'S2':
                image, hotspot, label = (Image.new('RGBA', (1, 1)), [0, 0], 'null') if index == 0 else native.player(index)
            else:
                if game == 'S1':
                    base = ROOT / 'docs/s1disasm'
                    maps = (base / '_maps/Sonic.asm').read_text()
                    plcs = (base / '_maps/Sonic - Dynamic Gfx Script.asm').read_text()
                    label = re.findall(r'mappingsTableEntry.w\s+(\w+)', maps)[index]
                    plc = re.findall(r'mappingsTableEntry.w\s+(\w+)', plcs)[index]
                    pieces = [list(map(native.num, values.split(','))) for values in re.findall(
                        r'spritePiece\s+([^;\n]+)', maps.split(label+':')[1].split(label+'_End')[0])]
                    loads = [list(map(native.num, values)) for values in re.findall(
                        r'dplcEntry\s+([^,]+),\s*([^\s;]+)', plcs.split(plc+':')[1].split(plc+'_End')[0])]
                    raw = (base / 'artunc/Sonic.unc').read_bytes()
                    colors = native.palette(base / 'palette/Sonic.bin')
                else:
                    base = ROOT / 'docs/skdisasm/General/Sprites/Sonic'
                    label, pieces = tables.mapping(str(base / 'Map - Sonic.asm'), 'Map_Sonic_')[index]
                    _, loads = tables.dplc(str(base / 'DPLC - Sonic.asm'), 'PLC_Sonic_')[index]
                    raw = (base / 'Art/Sonic.unc').read_bytes()
                    colors = native.palette(base / 'Palettes/SonicAndTails.bin')
                data = b''.join(raw[tile*32:(tile+count)*32] for count, tile in loads)
                canvas = Image.new('RGBA', (128, 128))
                for x, y, width, height, tile, flip_x, flip_y, *rest in reversed(pieces):
                    if rest:
                        assert rest[0] == 0, 'Normal player palette line expected'
                    piece = native.piece(data, tile, width, height, colors)
                    if flip_x:
                        piece = piece.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                    if flip_y:
                        piece = piece.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                    canvas.alpha_composite(piece, (64+x, 64+y))
                box = canvas.getbbox()
                image, hotspot = (canvas.crop(box), [64-box[0], 64-box[1]]) if box else (Image.new('RGBA', (1, 1)), [0, 0])
            key = f'{game.lower()}-{index:02x}'
            image.save(destination / f'{key}-native.png')
            refs[key] = dict(hotspot=hotspot, label=label, size=list(image.size))
    (destination / 'references.json').write_text(json.dumps(refs, indent=2)+'\n')


def write_csv(path, rows):
    with path.open('w') as output:
        writer = csv.DictWriter(output, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def reference_binding(game, sid, script, binding):
    """Reference-only Tails vocabulary; do not treat Sonic slot names as evidence."""
    extra = {31: 'RUN', 32: 'TAILS_FLY', 33: 'TAILS_FLY_ASCEND', 34: 'TAILS_FLY_CARRY',
             35: 'TAILS_FLY_CARRY_ASCEND', 36: 'TAILS_FLY_TIRED', 37: 'TAILS_SWIM',
             38: 'TAILS_SWIM_ASCEND', 39: 'TAILS_SWIM_CARRY', 40: 'TAILS_SWIM_TIRED', 41: 'SUPER_TRANSFORM'}
    if sid in extra:
        binding = dict(animation=extra[sid], family=extra[sid])
    elif sid in (18, 19):
        binding = dict(animation='TAILS_BLINK' if sid == 18 else 'TAILS_BLINK2', family='BLINK')
    elif sid in (10, 11, 29, 30) or (game == 'S2' and sid == 12):
        binding = dict(animation=None, family=None)
    unique = list(dict.fromkeys(script['frames']))
    # These are local reference phases, not a runtime Sonic-to-Tails correspondence.
    return dict(binding, phases=[unique.index(frame) for frame in script['frames']], offsetX=0, offsetY=0)


def trigger_for(game, character, sid, binding):
    animation = binding.get('animation')
    if binding.get('family') is None:
        return 'reserved/character-specific script; no normal-character selector claimed'
    if character == 'sonic' and sid == 32:
        return 'deep-idle lying loop (legacy FLY slot)'
    if game == 'S1':
        if animation == 'WARP3':
            return 'unused ceiling-squash routine: Sonic_SquashUnused (_incObj/01 Sonic.asm:1030-1042)'
        if animation in ('WARP1', 'WARP2', 'WARP4', 'LEAP1', 'LEAP2', 'SURF', 'BURNT', 'SHRINK'):
            return 'retained pose script; no live selector found in pinned S1 (ending uses a separate object bank)'
        if animation == 'FLOAT3':
            return 'continue-screen falling entry: CSon_Main (_incObj/80, 81 Continue Screen Elements and Sonic.asm:133-142)'
        if animation == 'FLOAT4':
            return 'continue-screen Start recovery: CSon_Animate (_incObj/80, 81 Continue Screen Elements and Sonic.asm:163-178)'
    if game == 'S3K' and character == 'sonic':
        if sid == 25:
            return 'ice slide / ending recoil: loc_726E (sonic3k.asm:9120-9124), loc_7402 (sonic3k.asm:9278-9283)'
        if animation in ('BALANCE3', 'BALANCE4'):
            return 'retained reverse-edge slot; ordinary S3K mirrors facing and selects BALANCE/BALANCE2: loc_11128 (sonic3k.asm:22531-22541)'
        if animation in ('BLINK', 'GET_UP'):
            return 'legacy idle-interrupt slot; no single-player finite idle gate'
    if binding.get('family') == 'TAILS_CARRIED' and animation is None:
        return 'carried-player release'
    triggers = {
        'WALK': 'grounded movement below running threshold', 'RUN': 'fast grounded movement',
        'ROLL': 'curled jump or ground roll', 'ROLL2': 'alternate rolling speed script',
        'PUSH': 'grounded movement blocked by a wall', 'DUCK': 'grounded Down input',
        'LOOK_UP': 'grounded Up input', 'SPINDASH': 'duck plus jump charging/release',
        'SPRING': 'spring launch', 'SKID': 'opposite input while moving fast',
        'STOP': 'opposite input while moving fast', 'WAIT': 'stationary ground wait timer',
        'BALANCE': 'standing on ledge, facing edge', 'BALANCE2': 'near ledge limit, facing edge',
        'BALANCE3': 'standing on ledge, facing away', 'BALANCE4': 'near ledge limit, facing away',
        'BLINK': 'deep-idle interruption', 'GET_UP': 'deep-idle recovery',
        'TAILS_BLINK': 'character-specific blink pose selector (loop-back script)',
        'TAILS_BLINK2': 'alternate character-specific blink pose selector (loop-back script)',
        'BUBBLE': 'air bubble collection', 'GET_AIR': 'air bubble collection',
        'HURT': 'damage recoil', 'HURT2': 'alternate damage slot', 'HURT_FALL': 'object-owned slide/recoil',
        'DEATH': 'kill routine', 'BURNT': 'burned-death pose selector', 'DROWN': 'air countdown expired',
        'TAILS_CARRIED': 'carried-player object control', 'VICTORY': 'ending pose selection',
        'FLOAT': 'object-owned suspension', 'FLOAT2': 'object-owned tumble',
        'FLOAT3': 'alternate floating entry', 'FLOAT4': 'floating recovery',
        'SLIDE': 'oil slide', 'WATER_SLIDE': 'water slide', 'HANG': 'bar or vine object control',
        'HANG2': 'alternate hanging object control', 'BLANK': 'literal hidden-player mapping',
        'NULL_ANIM': 'literal hidden-player mapping (e.g. giant-ring flash)',
        'SUPER_TRANSFORM': 'powered-form transformation', 'TAILS_FLY': 'flight state',
        'TAILS_FLY_ASCEND': 'ascending flight state', 'TAILS_FLY_CARRY': 'flight with carried player',
        'TAILS_FLY_CARRY_ASCEND': 'ascending flight with carried player', 'TAILS_FLY_TIRED': 'flight timer exhausted',
        'TAILS_SWIM': 'underwater flight state', 'TAILS_SWIM_ASCEND': 'ascending underwater flight',
        'TAILS_SWIM_CARRY': 'underwater flight with carried player', 'TAILS_SWIM_TIRED': 'underwater flight timer exhausted',
    }
    return triggers.get(animation, 'character-specific slide/recoil pose selector')


def family_table(rows):
    sonic = [row for row in rows if row['character'] == 'sonic' and row['family']]
    page = '<h2>Families across native hosts</h2><table><tr><th>Family<th>S1<th>S2<th>S3K'
    for family in sorted({row['family'] for row in sonic}):
        page += '<tr><th>'+html.escape(family)
        for game in ['S1', 'S2', 'S3K']:
            scripts = [row for row in sonic if row['host'] == game and row['family'] == family]
            page += '<td>'+('<br>'.join(html.escape(f"${row['native_id']:02X} {row['animation']}: {row['frame_count']} steps, delay {row['delay']}, {row['end']} {row['parameter']}") for row in scripts)
                            if scripts else 'N/A: no native family script')
    return page+'</table>'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('audit', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--references', type=Path)
    parser.add_argument('--export-references', action='store_true', help='decode disposable native PNGs from pinned development references')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.export_references:
        args.references = args.output / 'native-references'
        export_references(args.audit, args.references)
    rows = []
    for character, games in [('sonic', ['S1', 'S2', 'S3K']), ('tails', ['S2', 'S3K'])]:
        for game in games:
            data = load(args.audit / f'{"mica" if character == "sonic" else "talon"}-{game}.json')
            profile = load(ROOT / f'src/main/resources/animation-families/{game.lower()}.json')
            citations = labels(game, character)
            for sid, script in data['nativeScripts'].items():
                binding = dict(profile.get(sid, {}))
                if character == 'tails':
                    binding = reference_binding(game, int(sid), script, binding)
                animation = binding.get('animation')
                rows.append(dict(character=character, host=game, native_id=int(sid),
                    animation=animation or ('unnamed native family script' if binding.get('family') else 'N/A: unused/character-specific slot'),
                    family=binding.get('family'), phases=binding.get('phases', []), offset_x=binding.get('offsetX',0), offset_y=binding.get('offsetY',0),
                    frame_count=len(script['frames']), frame_order=script['frames'], delay=script['delay'],
                    ticks=script['delay']+1 if script['delay'] < 128 else 'velocity handler',
                    end=script['endAction'], parameter=script['endParam'],
                    trigger=trigger_for(game, character, int(sid), binding),
                    citation=citations[int(sid)] if int(sid) < len(citations) else 'N/A'))
    write_csv(args.output / 'family-inventory.csv', rows)
    page = '<!doctype html><meta charset="utf-8"><title>Animation families</title><style>body{font:15px system-ui;background:#faf7ef;margin:24px}table{border-collapse:collapse}td,th{border:1px solid #aaa;padding:6px}img{image-rendering:pixelated;max-width:100%}.hosts{display:flex;flex-wrap:wrap;gap:12px}.host{max-width:576px}.inventory{overflow:auto}</style><h1>Shared animation families</h1><p>Native Sonic left; one MICA phase set right. ROM timers and terminators remain host-owned. GIF centiseconds use cumulative 60 Hz rounding; speed-driven roll uses a one-tick illustrative interval. Full frames, exact palette union, no palette differencing. Finite animations restart for review only.</p><p><a href="family-inventory.csv">Full Sonic and reference-only Tails inventory</a>. Tails phases are local reference indices, not runtime Sonic phase bindings; reserved slots do not add TALON art obligations.</p>'
    page += family_table(rows)
    page += '<details><summary>All native script rows, including Tails references</summary><div class="inventory"><table><tr>'+''.join('<th>'+k for k in rows[0])
    for row in rows:
        page += '<tr>'+''.join('<td>'+html.escape(str(v)) for v in row.values())
    page += '</table></div></details>'
    manifest = load(ROOT / 'examples/mica-and-talon/art/mica/sheet/frames.yaml')
    frames = {frame['id']: frame for frame in manifest['frames']}
    reviews = {}
    if args.references:
        refs = load(args.references / 'references.json')
        artroot = ROOT / 'examples/mica-and-talon/art/mica'
        for game in ['S1', 'S2', 'S3K']:
            profile = load(ROOT / f'src/main/resources/animation-families/{game.lower()}.json')
            native = load(args.audit / f'mica-{game}.json')['nativeScripts']
            for sid, binding in profile.items():
                family = binding['family']
                if family is None:
                    continue
                keys = [f'family-{family.lower().replace("_", "-")}/{p}' for p in binding['phases']]
                if any(key not in manifest['nativeFrames'] for key in keys):
                    continue
                script = native[sid]
                seq = list(zip(script['frames'], keys))
                if script['endAction'] == 'LOOP_BACK':
                    seq += seq[-script['endParam']:]*2
                pages = []
                for native_frame, key in seq:
                    ref = f'{game.lower()}-{native_frame:02x}'
                    if ref not in refs:
                        pages = []
                        break
                    frame = frames[manifest['nativeFrames'][key]]
                    canvas = Image.new('RGB', (192, 112), '#929292')
                    for x, image, hotspot in [(0, Image.open(args.references / f'{ref}-native.png'), refs[ref]['hotspot']),
                            (96, Image.open(artroot / frame['file']), frame['hotspot'])]:
                        rgba = image.convert('RGBA')
                        canvas.paste(rgba, (x+48-hotspot[0]+(binding.get('offsetX',0) if x else 0),
                                            48-hotspot[1]+(binding.get('offsetY',0) if x else 0)), rgba)
                    pages.append(canvas.resize((576, 336), Image.Resampling.NEAREST))
                if not pages:
                    continue
                colors = list(dict.fromkeys(color for image in pages for color in (image.get_flattened_data() if hasattr(image, 'get_flattened_data') else image.getdata())))
                assert len(colors) <= 256, 'Exact GIF palette required'
                palette = Image.new('P', (1, 1))
                palette.putpalette(sum(map(list, colors), [])+[0]*(768-3*len(colors)))
                filename = f'{game}-{binding["animation"] or "script-"+sid}.gif'
                ticks = script['delay']+1 if script['delay'] < 128 else 1
                elapsed = 0
                with (args.output / filename).open('wb') as output:
                    for block in GifImagePlugin._get_global_header(pages[0].quantize(palette=palette), dict(loop=0)):
                        output.write(block)
                    for image in pages:
                        duration = (round((elapsed+ticks)*100/60)-round(elapsed*100/60))*10
                        elapsed += ticks
                        GifImagePlugin._write_frame_data(output, image.quantize(palette=palette, dither=Image.Dither.NONE), (0, 0), dict(duration=duration, disposal=2))
                    output.write(b';')
                with Image.open(args.output / filename) as result:
                    assert result.n_frames == len(pages)
                reviews.setdefault(family, []).append(f'<div class="host"><h3>{game}/{binding["animation"] or "$"+format(int(sid), "02X")}</h3><p>{script["delay"]} delay; {script["endAction"]} {script["endParam"]}; phases {binding["phases"]}</p><img src="{filename}"></div>')
    for family, comparisons in sorted(reviews.items()):
        page += '<h2>'+html.escape(family)+'</h2><div class="hosts">'+''.join(comparisons)+'</div>'
    retained = [f['id'] for f in manifest['frames'] if f['id'].startswith('variant-s1-') or f['id'].startswith('variant-s2-')]
    page += '<h2>Retained host-specific drawings</h2><p>S1 warp rotations, retained leap/surf, burnt and shrinking poses have no matching later-game pose. S2 GET_UP is a distinct crouched recovery; HANG2 retains its second hand phase. Phase sets otherwise prefer existing S3K drawings.</p><pre>'+html.escape('\n'.join(retained))+'</pre>'
    (args.output / 'index.html').write_text(page)
    print(f'{len(rows)} inventory rows; {len(retained)} retained host-specific drawings')


if __name__ == '__main__':
    main()
