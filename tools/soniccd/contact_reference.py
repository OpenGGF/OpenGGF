#!/usr/bin/env python3
"""Bounded native R11A Mosqui contact reference; 2026-10-24 player-contact-reference-opus-r2.

Purpose: corroborate the first R11A Present Mosqui defeat/aftermath ($3D -> $18 -> $1C/$1F)
and an ordinary walking hurt with ring loss (player routine 4, $11) on the official
BizHawk 2.11 GPGX core through controller-only cold routes. Comparison only.

Inputs: the frozen R2 player_reference.py (loaded only after its pinned SHA256 matches; its
unchanged output/repository guards, schedule bound and identity preflight are reused), the
pinned scddisasm, existing soniccd producer dependencies, official BizHawk 2.11, the user's
USA CUE/BIOS, a fixed controller schedule and a NEW external output. It never writes guest
memory, installs callbacks/hooks, hydrates an engine or reads guest I/O through bus peeks.

R2 (review player-contact-reference-opus-review.md, P2-3): before the scenario read, preflight,
prepare or any mkdir it also protects the whole media root (derived as the CUE region directory's
parent, which must also hold the firmware directory, or an explicit --media-root), the research
root (inventory parent), this tool directory, the frozen R2 directory and every repeatable
--protect root (each must exist). --guard-only stops after the guard. Evidence files are never
deduplicated or linked (P2-1); the netstandard reference is pinned (review advisory 10).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import struct
import subprocess
import sys
import time

sys.dont_write_bytecode = True
R2_PRODUCER_SHA256 = 'a0fa773a4a3a0c6a0d4a9f191272c6a56f4d3ad89347f2969eae82fa28d521f4'
NETSTANDARD_SHA256 = '3911a732c3f7349b24c74265a5372345c8a9a327429dfdcbb7d27b82f2514fe0'
MAX_CALLS = 6000
BUTTONS = ('Up', 'Down', 'Left', 'Right', 'A', 'B', 'C', 'Start')
CONTACT_LABELS = {'ObjMosqui': 'mosqui', 'ObjExplosion': 'explosion', 'ObjHUDPoints': 'points',
                  'ObjFlower': 'flower', 'ObjLostRing': 'lost_ring'}
CONTACT_GLOBALS = ['scoreChain', 'score', 'nextLifeScore', 'lives', 'livesFlags', 'updateHUDLives',
                   'updateHUDRings', 'updateHUDScore', 'updateHUDTime', 'shield', 'invincible', 'speedShoes',
                   'timeWarp', 'combineRing', 'debugCheat', 'miniSonic', 'ringLossAnimTimer',
                   'ringLossAnimFrame', 'ringLossAnimAccum', 'bottomBound', 'destBottomBound',
                   'levelRestart', 'timeStopTimer', 'flowerCount']
SLOT_FIELDS = ['oID', 'oSprFlags', 'oTile', 'oMap', 'oX', 'oXSub', 'oY', 'oYSub', 'oXVel', 'oYVel', 'oYRadius',
               'oXRadius', 'oPriority', 'oWidth', 'oMapFrame', 'oAnimFrame', 'oAnim', 'oPrevAnim', 'oAnimTime',
               'oColType', 'oColStatus', 'oFlags', 'oRoutine', 'oRoutine2', 'oAngle', 'oSubtype', 'oSubtype2']
MAPPINGS = {  # label: (include path, consumer opcode or 'lea_pc', expected consumers)
    'MapSpr_Explosion': ('Level/_Objects/Explosion/Data/Mappings.asm', '217c', 1),
    'MapSpr_Points': ('Level/_Objects/HUD and Points/Data/Mappings (Points).asm', '217c', 1),
    'MapSpr_Flower': ('Level/Palmtree Panic/Objects/Flower/Data/Mappings.asm', '217c', 1),
    'MapSpr_Ring': ('Level/_Objects/Ring/Data/Mappings.asm', '237c', 2),
    'MapSpr_Mosqui1': ('Level/Palmtree Panic/Objects/Mosqui/Data/Mappings (Normal).asm', 'lea_pc', 1),
    'MapSpr_Mosqui2': ('Level/Palmtree Panic/Objects/Mosqui/Data/Mappings (Damaged).asm', 'lea_pc', 1),
}
EXPECTED_MAPS = {'mosqui': ['MapSpr_Mosqui1', 'MapSpr_Mosqui2'], 'explosion': ['MapSpr_Explosion'],
                 'points': ['MapSpr_Points'], 'flower': ['MapSpr_Flower'], 'lost_ring': ['MapSpr_Ring']}
PPZ = 'Level/Palmtree Panic/'
COLLISION = PPZ + 'Player Object Collision.asm'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def dump(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def load_r2(directory):
    """Load the frozen R2 producer only after its exact bytes match the reviewed pin."""
    path = Path(directory).resolve(strict=True) / 'player_reference.py'
    with open(path, 'rb') as stream:
        data = stream.read()
    require(sha(data) == R2_PRODUCER_SHA256, 'Frozen R2 producer hash mismatch')
    module = type(sys)('player_reference_r2_frozen')
    module.__file__ = str(path)
    exec(compile(data, str(path), 'exec'), module.__dict__)
    return module


def verify_pin(path, pin, name):
    with open(path, 'rb') as stream:
        require(sha(stream.read()) == pin, f'{name} hash mismatch')


def guard_roots(a):
    """Roots refused for output in addition to R2's repository checkouts; no content reads."""
    cue, firmware = a.cue.resolve(), a.firmware_directory.resolve()
    if a.media_root is not None:
        media = a.media_root.resolve()
        require(cue.is_relative_to(media) and firmware.is_relative_to(media),
                'Explicit --media-root must contain the CUE and firmware directories')
    else:
        media = cue.parent.parent
        require(firmware.parent == media and cue.parent != media,
                'Cannot infer media root: pass --media-root containing the CUE and firmware directories')
    protect = []
    for root in a.protect:
        try:
            protect.append(Path(root).resolve(strict=True))
        except (OSError, RuntimeError) as exc:
            raise ValueError(f'Protected root does not exist: {root}') from exc
    return dict(media_root=media, research_root=a.inventory.resolve().parent, protect=protect)


def independent_payloads(directory):
    """Fresh evidence: every regular file is its own inode and nothing is a symlink (P2-1)."""
    for p in sorted(Path(directory).iterdir()):
        st = os.lstat(p)
        require(not os.path.islink(p) and st.st_nlink == 1, f'Evidence payload is linked: {p}')
    return sorted(p.name for p in Path(directory).iterdir())


def check_buttons(rows, first, last):
    for row in rows:
        require(type(row['first']) is int and type(row['last']) is int and first <= row['first'] <= row['last'] <= last,
                'Input outside its bounded call range')
        require(row['button'] in BUTTONS, 'Unknown button')


def check_final(scenario, r2):
    r2.check_schedule(scenario)  # Unchanged R2 bound: capture + 240 continuation calls fit 6000.
    n = scenario['frames']
    shots = scenario.get('continuation_screenshots')
    require(isinstance(shots, list) and all(type(f) is int and n < f <= n + 120 for f in shots),
            'Continuation screenshot outside the 120-call continuation')
    require('branches' not in scenario, 'Final schedules have no discovery branches')


def check_discovery(scenario):
    n = scenario['frames']
    require(type(n) is int and n >= 1, 'Invalid branch point')
    check_buttons(scenario['inputs'], 1, n)
    require(all(type(f) is int and 1 <= f <= n for f in scenario['screenshots']), 'Screenshot outside prelude')
    branches = scenario['branches']
    require(branches and len({b['name'] for b in branches}) == len(branches), 'Branches need unique names')
    total = n
    for b in branches:
        require(re.fullmatch(r'[A-Za-z0-9_-]{1,32}', b['name']) is not None, 'Unsafe branch name')
        require(type(b['frames']) is int and b['frames'] >= 1, 'Invalid branch length')
        check_buttons(b['inputs'], n + 1, n + b['frames'])
        require(all(type(f) is int and n < f <= n + b['frames'] for f in b['screenshots']), 'Screenshot outside branch')
        total += b['frames']
    require(total <= MAX_CALLS, 'Discovery calls exceed the 6000-call process bound')
    return total


def rs_widths(inv, text, base):
    """Mirror stage_inventory.rs_symbols over the same text, recording declared widths.

    Zero-size declarations alias the next sized field at the same cursor.
    """
    values, cursor, widths, offsets, pending = dict(base), 0, {}, {}, []
    for raw in text.splitlines():
        line = raw.split(';', 1)[0].strip()
        if not line:
            continue
        if line == 'rsreset':
            cursor, pending = 0, []
        elif line.startswith('rsset\t') or line.startswith('rsset '):
            cursor, pending = inv.expression(line.split(None, 1)[1], values), []
        elif match := re.fullmatch(r'(\w+)\s+EQU\s+(.+)', line):
            values['__rs'] = cursor
            values[match[1]] = inv.expression(match[2], values)
        elif match := re.fullmatch(r'(?:(\w+)\s+)?rs\.([bwl])\s+(.+)', line):
            size = {'b': 1, 'w': 2, 'l': 4}[match[2]] * inv.expression(match[3], values)
            if match[1]:
                values[match[1]] = offsets[match[1]] = cursor
                if size == 0:
                    pending.append(match[1])
                else:
                    widths[match[1]] = size
                    for alias in pending:
                        widths[alias] = size
                    pending = []
            elif size:
                pending = []
            cursor += size
        else:
            raise ValueError(f'Unsupported rs declaration: {line}')
    return widths, offsets, values


def source_widths(inv, source):
    cpu = source.text('_Include/Main CPU.inc')
    base = dict(WORKRAM=inv.expression(re.search(r'^WORKRAM\s+EQU\s+(\$[0-9A-F]+)', cpu, re.M)[1]))
    text = source.text('_Include/Main CPU Variables.inc')
    widths, offsets, values = rs_widths(inv, text[text.index('\trsreset'):], base)
    defs = source.text('Level/_Definitions.inc')
    size = inv.expression(re.search(r'^oSize\s+EQU\s+([^;\n]+)', defs, re.M)[1])
    values.update({f'oVar{i:02X}': i for i in range(size)})
    values['oSize'] = size
    defs = defs[defs.index('\trsreset'):defs.index('; VDP DMA from 68000 memory to VDP memory')]
    w2, o2, _ = rs_widths(inv, defs, values)
    widths.update(w2)
    offsets.update(o2)
    return widths, offsets


def cite(source, path, pattern):
    lines = [i + 1 for i, line in enumerate(source.text(path).splitlines()) if re.search(pattern, line)]
    require(len(lines) == 1, f'Missing/ambiguous source evidence {path}: {pattern}')
    return dict(path='src/' + path, line=lines[0], pattern=pattern)


def encode(*words):
    return b''.join(struct.pack('>H', w & 0xffff) for w in words)


def assemble_mapping(inv, source, label, path):
    text = re.sub(r'(?<!\w)\.(\w+)', r'Local_\1', source.text(path))
    labels, entries, _ = inv.dc_region(text)
    names = dict(labels)
    names[label] = 0  # Offsets in these includes are relative to their including label.
    return inv.assemble_dc(entries, names)


def contact_layout(a, cfg, provenance):
    """Source-bound contact layout over the already authenticated USA R11A image."""
    inv = sys.modules['stage_inventory']
    source = inv.Source(a.source)
    variables = provenance['source_variables']
    track = a.cue.resolve().parent / provenance['media']['files'][0]['file']
    with inv.RawIso(track) as disc:
        data = inv.Mmd(disc.file('R11A__.MMD;1')).data
    require(sha(data) == cfg['r11a_sha256'], 'R11A identity changed between preflights')
    widths, offsets = source_widths(inv, source)
    require(all(variables[k] == v for k, v in offsets.items() if k in variables), 'Width parser disagrees with source offsets')
    v = variables
    # Contact globals: addresses and widths from the same rs declarations; all in 68K work RAM.
    globals_ = {}
    for name in CONTACT_GLOBALS:
        address = v[name] & 0xffffff
        require(0xff0000 <= address <= 0xffffff and widths.get(name) in (1, 2, 3, 4), f'Unsupported contact global {name}')
        globals_[name] = dict(offset=address & 0xffff, width=widths[name], native_address=address)
    r2_width_check = {k: dict(r2=g['width'], source=widths.get(k), equal=g['width'] == widths.get(k))
                      for k, g in cfg['globals'].items()}
    # Player-specific EQU aliases have no rs width; bind it to cited word-sized instructions.
    player_fields = dict(oPlayerHurt=dict(offset=v['oPlayerHurt'], width=2, evidence=[
        cite(source, COLLISION, r'^\s*tst\.w\s+oPlayerHurt\(a0\)'),
        cite(source, COLLISION, r'^\s*move\.w\s+#\$78,oPlayerHurt\(a0\)')]))
    slot_fields = {k: dict(offset=v[k], width=widths[k]) for k in SLOT_FIELDS}
    dynamic = dict(offset=v['dynObjects'] & 0xffff, count=v['DYNOBJCOUNT'], slot_bytes=v['oSize'],
                   first_whole=(v['dynObjects'] - v['objects']) // v['oSize'], native_address=v['dynObjects'] & 0xffffff)
    require(v['dynObjectsEnd'] - v['dynObjects'] == dynamic['count'] * dynamic['slot_bytes'] == widths['dynObjects'],
            'Dynamic slot extent differs from source')
    require(dynamic['first_whole'] == v['RESOBJCOUNT'] and dynamic['first_whole'] + dynamic['count'] == v['OBJCOUNT'],
            'Dynamic slot numbering differs from source')
    specific = dict(
        explosion=dict(oExplodeBadnik=dict(offset=v['oRoutine2'], width=1, evidence=cite(source, 'Level/_Objects/Explosion/Main.asm', r'^oExplodeBadnik\s+EQU\s+oRoutine2')),
                       oExplodePoints=dict(offset=0x3e, width=2, evidence=[cite(source, 'Level/_Objects/Explosion/Main.asm', r'^oExplodePoints\s+EQU\s+oVar3E'),
                                                                        cite(source, 'Level/_Objects/Explosion/Main.asm', r'^\s*move\.w\s+oExplodePoints\(a0\),d1')])),
        points=dict(oPntsTimer=dict(offset=0x2a, width=1, evidence=[cite(source, 'Level/_Objects/HUD and Points/Main.asm', r'^oPntsTimer\s+EQU\s+oVar2A'),
                                                                 cite(source, 'Level/_Objects/HUD and Points/Main.asm', r'^\s*subq\.b\s+#1,oPntsTimer\(a0\)')])),
        mosqui=dict(origin_x=dict(offset=0x2a, width=2, evidence=cite(source, PPZ + 'Objects/Mosqui/Main.asm', r'^\s*move\.w\s+oX\(a0\),oVar2A\(a0\)')),
                    patrol_velocity=dict(offset=0x2c, width=4, evidence=cite(source, PPZ + 'Objects/Mosqui/Main.asm', r'^\s*move\.l\s+d0,oVar2C\(a0\)'))))
    # Object IDs from the pinned source index, checked against the loaded table's alias relations.
    index = [line.split()[1] for line in source.text(PPZ + 'Object Index (Act 1 Present).asm').splitlines()
             if re.match(r'\s*dc\.l\s+', line)]
    table = next(w for w in cfg['windows'] if w['name'] == 'ObjectIndex')['offset']
    pointers = [int.from_bytes(inv.bounded(data, table + 4 * i, 4), 'big') for i in range(len(index))]
    aliases = {}
    for name, pointer in zip(index, pointers, strict=True):
        require(aliases.setdefault(name, pointer) == pointer, 'Loaded object index alias mismatch')
    ids, dispatch = {}, {}
    for label, kind in CONTACT_LABELS.items():
        require(index.count(label) == 1, f'Object label not unique: {label}')
        ids[kind] = index.index(label) + 1
        dispatch[kind] = dict(id=ids[kind], label=label, native_address=pointers[ids[kind] - 1],
                              file_offset=pointers[ids[kind] - 1] - 0x200000)
    # Mapping data assembled from pinned includes, joined by explicit consumer operands.
    maps = {}
    for label, (path, opcode, expected) in MAPPINGS.items():
        blob = assemble_mapping(inv, source, label, path)
        at = inv.unique(data, blob, label)
        if opcode == 'lea_pc':
            joins = [o for o in inv.occurrences(data, bytes.fromhex('43fa'))
                     if o % 2 == 0 and o + 2 + struct.unpack('>h', inv.bounded(data, o + 2, 2))[0] == at]
        else:
            joins = inv.occurrences(data, bytes.fromhex(opcode) + struct.pack('>I', 0x200000 + at) + struct.pack('>H', v['oMap']))
        require(len(joins) == expected, f'Mapping consumer join differs: {label}')
        maps[label] = dict(file_offset=at, native_address=0x200000 + at, bytes=len(blob), sha256=sha(blob),
                           consumer_offsets=joins, consumer=opcode, source='src/' + path)
    expected_maps = {kind: [maps[label]['native_address'] for label in labels] for kind, labels in EXPECTED_MAPS.items()}
    points = inv.scalar_table(source, COLLISION, 'EnemyPoints')
    points_at = inv.unique(data, points, 'EnemyPoints')
    code = {
        'KillEnemy_id_routine_subtype': encode(0x137c, 0x18, v['oID'], 0x137c, 0, v['oRoutine'], 0x137c, 1, v['oSubtype']),
        'HurtPlayer_lost_ring_spawn': encode(0x137c, 0x11, v['oID'], 0x3368, v['oX'], v['oX'], 0x3368, v['oY'], v['oY']),
        'HurtPlayer_knockback': encode(0x08e8, 1, v['oFlags'], 0x317c, -0x400, v['oYVel'], 0x317c, -0x200, v['oXVel']),
        'HurtPlayer_hurt_tail': encode(0x317c, 0, v['oPlayerGVel'], 0x117c, 0x1a, v['oAnim'], 0x317c, 0x78, v['oPlayerHurt']),
        'ObjMosqui_Init_collision': encode(0x117c, 0x10, v['oXRadius'], 0x117c, 0x10, v['oWidth'], 0x117c, 0x10, v['oYRadius'],
                                           0x117c, 0x2b, v['oColType'], 0x3168, v['oX'], 0x2a),
        'ObjExplosion_MakePoints_spawn': encode(0x137c, 0x1c, v['oID'], 0x3368, v['oX'], v['oX'], 0x3368, v['oY'], v['oY'], 0x1341, v['oSubtype']),
        'ObjExplosion_Done_flower': encode(0x117c, 0x1f, v['oID'], 0x117c, 0, v['oRoutine']),
        'ObjPoints_timer': encode(0x117c, 24, 0x2a, 0x5328, 0x2a),
        'ObjLostRing_DidInit': encode(0x33fc, 0) + struct.pack('>I', v['rings']) + encode(0x13fc, 0x80) +
                               struct.pack('>I', v['updateHUDRings']) + encode(0x13fc, 0) + struct.pack('>I', v['livesFlags']),
    }
    code_sources = {
        'KillEnemy_id_routine_subtype': cite(source, COLLISION, r'^\s*move\.b\s+#\$18,oID\(a1\)'),
        'HurtPlayer_lost_ring_spawn': cite(source, COLLISION, r'^\s*move\.b\s+#\$11,oID\(a1\)'),
        'HurtPlayer_knockback': cite(source, COLLISION, r'^\s*move\.w\s+#-\$400,oYVel\(a0\)'),
        'HurtPlayer_hurt_tail': cite(source, COLLISION, r'^\s*move\.b\s+#\$1A,oAnim\(a0\)'),
        'ObjMosqui_Init_collision': cite(source, PPZ + 'Objects/Mosqui/Main.asm', r'^\s*move\.b\s+#\$2B,oColType\(a0\)'),
        'ObjExplosion_MakePoints_spawn': cite(source, 'Level/_Objects/Explosion/Main.asm', r'^\s*move\.b\s+#\$1C,oID\(a1\)'),
        'ObjExplosion_Done_flower': cite(source, 'Level/_Objects/Explosion/Main.asm', r'^\s*move\.b\s+#\$1F,oID\(a0\)'),
        'ObjPoints_timer': cite(source, 'Level/_Objects/HUD and Points/Main.asm', r'^\s*move\.b\s+#24,oPntsTimer\(a0\)'),
        'ObjLostRing_DidInit': cite(source, 'Level/_Objects/Ring/Main.asm', r'^\s*move\.b\s+#\$80,updateHUDRings'),
    }
    windows, code_offsets = [], {}
    def window(name, at, n):
        block = inv.bounded(data, at, n)
        windows.append(dict(name=name, offset=at, length=n, sha256=sha(block), prefix_hex=block[:32].hex()))
    window('contact_ObjectIndex_full', table, 4 * len(index))
    for kind, row in dispatch.items():
        window('contact_dispatch_' + kind, row['file_offset'], 32)
    for name, needle in code.items():
        at = inv.unique(data, needle, name)
        code_offsets[name] = dict(file_offset=at, encoding_hex=needle.hex(), source=code_sources[name])
        window('contact_code_' + name, max(0, at - 16), 64)
    window('contact_EnemyPoints', points_at, len(points))
    for label, row in maps.items():
        window('contact_map_' + label, row['file_offset'], min(64, row['bytes']))
    require(len({w['name'] for w in cfg['windows'] + windows}) == len(cfg['windows']) + len(windows), 'Duplicate window name')
    layout = dict(schema='soniccd-contact-layout-v1', globals=globals_, player_fields=player_fields,
                  slot_fields=slot_fields, object_specific=specific, dynamic=dynamic,
                  ids={kind: ids[kind] for kind in CONTACT_LABELS.values()}, dispatch=dispatch,
                  mappings=maps, expected_maps=expected_maps, code=code_offsets,
                  enemy_points=dict(file_offset=points_at, values=list(struct.unpack(f'>{len(points)//2}H', points)),
                                    source=cite(source, COLLISION, r'^EnemyPoints:')),
                  r2_global_width_check=r2_width_check,
                  source_widths={k: widths[k] for k in CONTACT_GLOBALS + SLOT_FIELDS + ['dynObjects']})
    return layout, windows, source.used


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for n in ['producer-directory', 'source', 'inventory', 'bizhawk-home', 'cue', 'firmware-directory',
              'scenario', 'output', 'r2-directory']:
        p.add_argument('--' + n, type=Path, required=True)
    p.add_argument('--bios-sha1', required=True)
    p.add_argument('--kind', choices=('final', 'discovery'), required=True)
    p.add_argument('--timeout', type=int, default=900)
    p.add_argument('--modes', default='observed-1,observed-2,control')
    p.add_argument('--compiler', type=Path, default=Path('/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe'))
    p.add_argument('--netstandard', type=Path, default=Path('/usr/lib/mono/4.8-api/Facades/netstandard.dll'))
    p.add_argument('--media-root', type=Path)
    p.add_argument('--protect', type=Path, action='append', default=[])
    p.add_argument('--guard-only', action='store_true')
    a = p.parse_args()
    r2 = load_r2(a.r2_directory)
    roots = guard_roots(a)
    protected = [*r2.repository_checkouts(a.producer_directory), a.producer_directory, a.source, a.bizhawk_home,
                 roots['media_root'], a.cue.parent, a.firmware_directory, roots['research_root'],
                 Path(__file__).parent, a.r2_directory, *roots['protect']]
    output = r2.new_external(a.output, protected)
    if a.guard_only:
        print(json.dumps(dict(admitted=str(output), created=False, protected_roots=len(protected))))
        return
    scenario = json.loads(a.scenario.read_text())
    if a.kind == 'final':
        check_final(scenario, r2)
        modes = a.modes.split(',')
        require(modes and all(m in ('observed-1', 'observed-2', 'control') for m in modes) and len(set(modes)) == len(modes),
                'Invalid modes')
    else:
        check_discovery(scenario)
        modes = ['discovery']
    cfg, provenance, ob, dev = r2.prepare(a)
    contact, windows, contact_sources = contact_layout(a, cfg, provenance)
    cfg['windows'] = cfg['windows'] + windows  # R2 windows first and unchanged; contact windows strengthen the gate.
    cfg['contact'] = contact
    dll = a.bizhawk_home.resolve() / 'dll'
    binaries = dev.verify_pins(dll, dev.RUNTIME_PINS)
    require(ob.digest(a.compiler) == ob.COMPILER_SHA256, 'Compiler mismatch')
    verify_pin(a.netstandard, NETSTANDARD_SHA256, 'netstandard reference')
    here = Path(__file__).resolve()
    inputs = [here, here.with_name('contact_reference_probe.cs'), a.producer_directory / 'oracle_probe.cs',
              a.r2_directory.resolve() / 'player_reference.py']
    inputs_hash = {str(f): ob.digest(f) for f in inputs}
    output.mkdir()
    build = output / 'build'
    build.mkdir()
    (build / 'dll').symlink_to(dll, target_is_directory=True)
    dump(output / 'layout.json', cfg)
    dump(output / 'scenario.json', scenario)
    exe = build / 'contact_reference.exe'
    command = ['mono', str(a.compiler), '/nologo', '/langversion:9', '/deterministic+', '/main:ContactReference',
               f'/out:{exe}', f'/reference:{a.netstandard}']
    command += [f'/reference:{dll / name}' for name in ob.ASSEMBLIES] + [str(inputs[1]), str(inputs[2])]
    subprocess.run(command, check=True, timeout=60, cwd=build)
    env = os.environ.copy()
    for key in ['DISPLAY', 'WAYLAND_DISPLAY', 'PULSE_SERVER', 'JACK_DEFAULT_SERVER']:
        env.pop(key, None)
    env.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll), TMPDIR=str(build))
    commands, timing, results = [], {}, {}
    for mode in modes:
        cmd = ['mono', str(exe), str(a.cue.resolve()), str(a.firmware_directory.resolve()), str(output / mode),
               str(output / 'layout.json'), str(output / 'scenario.json'), a.bios_sha1, mode]
        commands.append(cmd)
        started = time.time()
        clock = time.monotonic()
        with (output / (mode + '.log')).open('w') as log:
            proc = subprocess.Popen(cmd, env=env, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                result = proc.wait(timeout=a.timeout)
            except BaseException:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait()
                raise
        timing[mode] = dict(started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(started)),
                            wall_seconds=round(time.monotonic() - clock, 3), exit_code=result)
        results[mode] = result
        if (output / mode).is_dir():
            timing[mode]['independent_payloads'] = len(independent_payloads(output / mode))
        print(f'{mode} exit {result} in {timing[mode]["wall_seconds"]} s', flush=True)
        if result != 0:
            break  # Sequential bound: never start another native process after a failure.
    require(all(ob.digest(f) == inputs_hash[str(f)] for f in inputs), 'Producer changed during run')
    dev.verify_pins(dll, dev.RUNTIME_PINS)
    summaries = {m: json.loads((output / m / 'summary.json').read_text()) for m in results if (output / m / 'summary.json').exists()}
    provenance['contact_source_inputs'] = contact_sources
    manifest = dict(schema='soniccd-contact-reference-v2', kind=a.kind, comparison_only=True, phase=cfg['phase'],
                    protected_roots=sorted({str(Path(x).resolve()) for x in protected}), media_root=str(roots['media_root']),
                    research_root=str(roots['research_root']), netstandard_sha256=NETSTANDARD_SHA256,
                    retention='independent files per mode and stream; no hard links, symlinks or deduplication of evidence',
                    provenance=provenance, runtime_sha256=binaries, producer_sha256=inputs_hash,
                    executable_sha256=ob.digest(exe), compiler_sha256=ob.digest(a.compiler),
                    scenario_sha256=ob.digest(output / 'scenario.json'), layout_sha256=ob.digest(output / 'layout.json'),
                    modes=modes, commands=commands, exit_codes=results, timing=timing, display=None, audio_device=None,
                    per_run_call_limit=MAX_CALLS, summaries=summaries,
                    r2_reused=dict(module=str(inputs[3]), sha256=R2_PRODUCER_SHA256,
                                   functions=['new_external', 'repository_checkouts', 'check_schedule', 'prepare']))
    dump(output / 'manifest.json', manifest)
    require(all(r == 0 for r in results.values()) and len(results) == len(modes), 'Native run failed; evidence retained')
    print(output / 'manifest.json')


if __name__ == '__main__':
    main()
