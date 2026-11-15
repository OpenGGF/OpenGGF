#!/usr/bin/env python3
"""Validate bounded native R11A contact comparisons; player-contact-reference-opus-r2, 2026-10-24.

Input: an external contact_reference.py (R2) output. The frozen R2 player-reference checker supplies the
unchanged identity/field/schedule/cold-repeat/control checks, loaded only after BOTH its own bytes and the
player_reference module it imports match their pins (P2-4a). This module adds, before any comparison, an
independent-specimen gate that refuses aliased evidence (os.path.samefile, symlinks, shared inodes; P2-1),
enforced three-checkpoint and end-state byte equality (P2-4b), control placeholders labelled not_observed,
producer identity against capture, contact-row checks, Mosqui kill / walking-hurt event acceptance and a
deterministic self-excluding artifact inventory (P2-2). Never supplies gameplay inputs or authority.
R1 provenance: player-contact-reference-opus-r1 checker f62d9643...; review player-contact-reference-opus-review.md.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import types

sys.dont_write_bytecode = True
R2_CHECK_SHA256 = 'fe392527550e7a515ec6df937d85267ec050d2ab0d455be03405023afdd63d65'
R2_PRODUCER_SHA256 = 'a0fa773a4a3a0c6a0d4a9f191272c6a56f4d3ad89347f2969eae82fa28d521f4'
PAD = {'Up': 1, 'Down': 2, 'Left': 4, 'Right': 8, 'B': 0x10, 'C': 0x20, 'A': 0x40, 'Start': 0x80}
MODES = ('observed-1', 'observed-2', 'control')
CATEGORIES = ('av', 'player_core', 'contact_globals_and_player_fields', 'dynamic_slots',
              'continuation_screenshots', 'complete_rows')
CONTROL_NOT_OBSERVED = ('player_core', 'contact_globals_and_player_fields', 'dynamic_slots')
NOT_OBSERVED = 'not_observed'
WINDOW = 2  # Polls allowed between a native write and its observed consequence; phase is unknown.
INVENTORY_METHOD = ('SHA256 over records "relative-path TAB file-sha256 LF" for regular files only, sorted by the '
                    'POSIX relative path as a Python str; symlinks listed separately and excluded; the inventory '
                    'file itself excluded')
_MISSING = object()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def rows(path):
    return [json.loads(s) for s in Path(path).read_text().splitlines()]


def _pinned_module(path, pin, name, label):
    with open(path, 'rb') as stream:
        data = stream.read()
    require(sha(data) == pin, f'Frozen R2 {label} hash mismatch')
    module = types.ModuleType(name)
    module.__file__ = str(path)
    exec(compile(data, str(path), 'exec'), module.__dict__)
    return module


def load_r2_check(directory):
    """Bind the frozen R2 checker to the pinned player_reference bytes; no sys.path or foreign module use."""
    directory = Path(directory).resolve(strict=True)
    producer = _pinned_module(directory / 'player_reference.py', R2_PRODUCER_SHA256, 'player_reference', 'producer')
    saved = sys.modules.get('player_reference', _MISSING)
    sys.modules['player_reference'] = producer
    try:
        checker = _pinned_module(directory / 'player_reference_check.py', R2_CHECK_SHA256,
                                 'player_reference_check_r2_frozen', 'checker')
    finally:
        if saved is _MISSING:
            sys.modules.pop('player_reference', None)
        else:
            sys.modules['player_reference'] = saved
    for name in ('require', 'check_schedule', 'dump'):
        require(getattr(checker, name) is getattr(producer, name), 'R2 checker bound an unpinned player_reference')
    return checker


def producer_identity(manifest):
    """The capture's recorded producer/probe/R2 bytes must still be the files that are validated and frozen."""
    for path, pin in manifest['producer_sha256'].items():
        require(Path(path).is_file() and digest(path) == pin, f'Producer identity changed since capture: {path}')


def evidence_names(scenario, mode):
    names = ['av.jsonl', 'forward.jsonl', 'replay.jsonl', 'checkpoint.state', 'forward-end.state', 'replay-end.state']
    names += [f'postframe-{f:05d}.ppm' for f in scenario['screenshots']]
    names += [f'{s}-postframe-{f:05d}.ppm' for f in scenario['continuation_screenshots'] for s in ('forward', 'replay')]
    if mode != 'control':
        names.append('player.jsonl')
    return names


def independent_specimens(root, scenario):
    """P2-1: every compared payload must be its own physical file; refuse aliases before any comparison."""
    root = Path(root)
    real = root.resolve(strict=True)
    for mode in MODES:
        d = root / mode
        require(not d.is_symlink() and d.is_dir() and d.resolve() == real / mode, f'Mode directory is not a real directory: {mode}')
    pairs = []
    for a, b in (('observed-1', 'observed-2'), ('observed-1', 'control'), ('observed-2', 'control')):
        for name in sorted(set(evidence_names(scenario, a)) & set(evidence_names(scenario, b))):
            pairs.append((root / a / name, root / b / name))
    for mode in MODES:
        d = root / mode
        pairs += [(d / 'forward.jsonl', d / 'replay.jsonl'), (d / 'forward-end.state', d / 'replay-end.state')]
        pairs += [(d / f'forward-postframe-{f:05d}.ppm', d / f'replay-postframe-{f:05d}.ppm') for f in scenario['continuation_screenshots']]
    for a, b in pairs:
        if os.path.lexists(a) and os.path.lexists(b):
            try:
                same = os.path.samefile(a, b)  # Follows symlinks, so resolved aliases are caught here.
            except OSError:
                same = False  # A dangling link is refused below as a symlink.
            require(not same, f'Comparison pair is one physical file: {a.relative_to(root)} = {b.relative_to(root)}')
    nodes, total = set(), 0
    for mode in MODES:
        for name in evidence_names(scenario, mode) + ['summary.json']:
            p = root / mode / name
            require(os.path.lexists(p), f'Missing evidence specimen: {p.relative_to(root)}')
            st = os.lstat(p)
            require(not stat.S_ISLNK(st.st_mode), f'Evidence path is a symlink: {p.relative_to(root)}')
            require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1, f'Evidence is not an independent regular file: {p.relative_to(root)}')
            nodes.add((st.st_dev, st.st_ino))
            total += 1
    require(len(nodes) == total, 'Evidence inodes are not distinct')
    return dict(pairs=len(pairs), files=total, distinct_inodes=len(nodes), rule='pairwise samefile refused; regular, unlinked (nlink 1), non-symlink files')


def checkpoint_equality(root, summaries):
    """P2-4b: three independent cold checkpoints must be byte-identical and match their process summaries."""
    data = {}
    for mode in MODES:
        b = (Path(root) / mode / 'checkpoint.state').read_bytes()
        require(sha(b) == summaries[mode]['checkpoint_sha256'], f'Checkpoint hash differs from summary: {mode}')
        data[mode] = b
    require(len(set(data.values())) == 1, 'Checkpoint divergence across cold modes')
    return dict(sha256=sha(data['observed-1']), bytes=len(data['observed-1']), modes=list(MODES))


def end_state_check(root, summaries):
    """Retained end states: hashes, complete unmasked recomputed differences, and cross-mode equality."""
    out, ends = {}, {}
    for mode in MODES:
        f = (Path(root) / mode / 'forward-end.state').read_bytes()
        r = (Path(root) / mode / 'replay-end.state').read_bytes()
        s = summaries[mode]
        require(sha(f) == s['final_state_sha256'] and sha(r) == s['replay_final_state_sha256'], f'End-state hash differs from summary: {mode}')
        require(len(f) == s['final_state_bytes'] and len(r) == s['replay_final_state_bytes'], f'End-state size differs from summary: {mode}')
        diffs = [] if f == r else [i for i in range(min(len(f), len(r))) if f[i] != r[i]]
        count = len(diffs) + abs(len(f) - len(r))
        require(diffs == list(s['raw_final_different_offsets_complete']) and count == s['raw_final_different_bytes'],
                f'Recomputed raw difference differs from summary: {mode}')
        out[mode] = dict(different_bytes=count, offsets=diffs, forward_sha256=sha(f), replay_sha256=sha(r), bytes=[len(f), len(r)])
        ends[mode] = (f, r)
    require(len({v[0] for v in ends.values()}) == 1 and len({v[1] for v in ends.values()}) == 1, 'End states differ across cold modes')
    return out


def artifact_inventory(root, exclude=()):
    """P2-2: deterministic inventory of regular files (string-ordered), symlinks separate, inode counts."""
    root = Path(root)
    files, links = {}, {}
    for dirpath, dirnames, filenames in os.walk(root):
        for name in dirnames + filenames:
            p = Path(dirpath) / name
            rel = p.relative_to(root).as_posix()
            if rel in exclude:
                continue
            if p.is_symlink():
                links[rel] = os.readlink(p)
            elif p.is_file():
                st = p.stat()
                files[rel] = dict(bytes=st.st_size, sha256=digest(p), inode=st.st_ino, nlink=st.st_nlink)
    ordered = {k: files[k] for k in sorted(files)}
    fingerprint = sha(''.join(f'{k}\t{v["sha256"]}\n' for k, v in ordered.items()).encode())
    return dict(method=INVENTORY_METHOD, excluded=sorted(exclude), files=ordered, symlinks={k: links[k] for k in sorted(links)},
                regular_files=len(ordered), distinct_inodes=len({v['inode'] for v in ordered.values()}),
                multi_linked_files=sum(1 for v in ordered.values() if v['nlink'] > 1),
                total_bytes=sum(v['bytes'] for v in ordered.values()), fingerprint=fingerprint)


def mask(names):
    value = 0
    for name in names:
        require(name.startswith('P1 ') and name[3:] in PAD, 'Unknown controller input')
        value |= PAD[name[3:]]
    return value


def decode_slot(raw_hex, contact):
    b = bytes.fromhex(raw_hex)
    require(len(b) == contact['dynamic']['slot_bytes'], 'Dynamic slot extent')
    f = {k: int.from_bytes(b[s['offset']:s['offset'] + s['width']], 'big') for k, s in contact['slot_fields'].items()}
    for kind, names in contact['object_specific'].items():
        for name, s in names.items():
            f[kind + '.' + name] = int.from_bytes(b[s['offset']:s['offset'] + s['width']], 'big')
    return f


def kind_of(contact, oid):
    return next((k for k, v in contact['ids'].items() if v == oid), None)


def contact_row_check(r, contact):
    """Width/endian/summary/actor consistency of one admitted contact row."""
    if r.get('program') != 'USA_R11A_source_and_loaded_windows_matched':
        require(not r.get('accepted') and 'dynamic' not in r, 'Unknown program row carries contact fields')
        return
    for name, spec in contact['globals'].items():
        raw = r['contact_globals_raw_hex'][name]
        require(len(raw) == 2 * spec['width'], f'Contact global width mismatch: {name}')
        require(r['contact_globals'][name] == int(raw, 16), f'Contact global endian/value mismatch: {name}')
    require(set(r['contact_globals']) == set(contact['globals']), 'Contact global set mismatch')
    player = bytes.fromhex(r['player_raw_hex'])
    for name, spec in contact['player_fields'].items():
        require(r['contact_player_fields'][name] == int.from_bytes(player[spec['offset']:spec['offset'] + spec['width']], 'big'),
                f'Player contact field width/endian mismatch: {name}')
    d, dyn = r['dynamic'], contact['dynamic']
    ids, routines = bytes.fromhex(d['ids_hex']), bytes.fromhex(d['routines_hex'])
    require(len(ids) == len(routines) == dyn['count'] and d['first_whole'] == dyn['first_whole'], 'Dynamic summary extent')
    wanted = {dyn['first_whole'] + i for i, oid in enumerate(ids) if kind_of(contact, oid)}
    require({s['slot'] for s in d['contact_slots']} == wanted, 'Contact slot set differs from ID summary')
    io, ro = contact['slot_fields']['oID']['offset'], contact['slot_fields']['oRoutine']['offset']
    for s in d['contact_slots']:
        i = s['slot'] - dyn['first_whole']
        f = decode_slot(s['raw_hex'], contact)
        require(f['oID'] == ids[i] and f['oRoutine'] == routines[i], 'Contact slot raw differs from summary')
        kind = kind_of(contact, f['oID'])
        if f['oRoutine'] >= 2:
            require(f['oMap'] in contact['expected_maps'][kind], f'Unverified {kind} actor mapping')
    for t in d['transitions']:
        i = t['slot'] - dyn['first_whole']
        after, before = bytes.fromhex(t['after_hex']), bytes.fromhex(t['before_hex'])
        require(len(after) == len(before) == dyn['slot_bytes'], 'Transition extent')
        require(after[io] == ids[i] and after[ro] == routines[i], 'Transition differs from summary')
        require((before[io], before[ro]) != (after[io], after[ro]), 'Transition without ID/routine change')


def admitted(stream):
    return [r for r in stream if r and r.get('program') == 'USA_R11A_source_and_loaded_windows_matched']


def slots_of(r, contact, kind):
    oid = contact['ids'][kind]
    return {s['slot']: decode_slot(s['raw_hex'], contact) for s in r['dynamic']['contact_slots']
            if int(s['raw_hex'][:2], 16) == oid}


def contiguous(seq):
    require(all(b['frame'] == a['frame'] + 1 for a, b in zip(seq, seq[1:])), 'Event stream is not contiguous')


def latch(r, av, require_logical):
    g = r['globals']
    require(g['p1CtrlHold'] == mask(av['input']), f'Raw input latch differs from schedule at {r["frame"]}')
    if require_logical:
        require(g['playerCtrlHold'] == g['p1CtrlHold'] and g['playerCtrlTap'] == g['p1CtrlTap'],
                f'Input latch divergence at {r["frame"]}')


def kill_events(seq, contact, avs):
    """Accept $3D->$18 only with the source kill path and its observed aftermath chain."""
    contiguous(seq)
    m, ex, pt, fl = (contact['ids'][k] for k in ('mosqui', 'explosion', 'points', 'flower'))
    events, disappearances = [], []
    for k in range(1, len(seq)):
        r0, r1 = seq[k - 1], seq[k]
        a, b = bytes.fromhex(r0['dynamic']['ids_hex']), bytes.fromhex(r1['dynamic']['ids_hex'])
        for i in range(len(a)):
            slot = contact['dynamic']['first_whole'] + i
            if a[i] == m and b[i] != m:
                if b[i] != ex:
                    disappearances.append(dict(frame=r1['frame'], slot=slot, new_id=b[i]))
                    continue
                events.append(accept_kill(seq, k, slot, contact, avs))
    return events, disappearances


def accept_kill(seq, k, slot, contact, avs):
    r0, r1 = seq[k - 1], seq[k]
    m, ex, pt, fl = (contact['ids'][x] for x in ('mosqui', 'explosion', 'points', 'flower'))
    before = slots_of(r0, contact, 'mosqui')[slot]
    after = slots_of(r1, contact, 'explosion')[slot]
    require(before['oMap'] in contact['expected_maps']['mosqui'] and before['oColType'] == 0x2b, 'Pre-kill slot is not an admitted Mosqui')
    require(after['oSubtype'] == 1 and after['oFlags'] & 0x80, 'KillEnemy slot writes (subtype 1, flags bit 7) absent')
    # Not a KillEnemy write: inherited oRoutine2 = 0 selects points and flower (Explosion/Main.asm:15-16,99-100).
    require(after['explosion.oExplodeBadnik'] == 0, 'Explosion badnik precondition (oRoutine2 = 0) absent')
    f, g0, g1 = r1['fields'], r0['contact_globals'], r1['contact_globals']
    require(f['oAnim'] == 2 or g1['invincible'] or g1['timeWarp'], 'Kill without roll/jump or invincible/time-warp path')
    old = g1['scoreChain'] - 2
    require(old == g0['scoreChain'] or (old == 0 and g0['scoreChain'] != 0 and not f['oFlags'] & 2),
            'Score chain did not advance by the KillEnemy step')
    points = 1000 if g1['scoreChain'] >= 0x20 else contact['enemy_points']['values'][min(old, 6) // 2]
    require(g1['score'] - g0['score'] == points, 'Score delta differs from EnemyPoints')
    expect_points = ((after['explosion.oExplodePoints'] & 0xff) >> 1) | 0x80
    require(after['explosion.oExplodePoints'] == (0xa if g1['scoreChain'] >= 0x20 else min(old, 6)), 'Explosion points index differs')
    for r, av in ((r0, avs.get(r0['frame'])), (r1, avs.get(r1['frame']))):
        require(av is not None, 'Missing AV row for latch check')
        latch(r, av, r['fields']['oRoutine'] == 2 and r['globals']['ctrlLocked'] == 0)
    later = seq[k:]
    init = next((i for i, r in enumerate(later[:WINDOW + 1]) if (lambda s: s and s['oRoutine'] >= 2 and s['oColType'] == 0)
                 (slots_of(r, contact, 'explosion').get(slot))), None)
    require(init is not None, 'Explosion initialization not observed')
    spawn = None
    for i, r in enumerate(later[:WINDOW + 1]):
        for t in r['dynamic']['transitions']:
            if bytes.fromhex(t['before_hex'])[0] != pt and bytes.fromhex(t['after_hex'])[0] == pt:  # same-pass reuse allowed
                p = decode_slot(t['after_hex'], contact)
                if p['oSubtype'] == expect_points and p['oX'] == after['oX'] and p['oY'] in (after['oY'], (after['oY'] - 2) & 0xffff):
                    spawn = dict(frame=r['frame'], slot=t['slot'], subtype=p['oSubtype'])
    require(spawn is not None, 'Points object not spawned at the explosion')
    gone = next((r['frame'] for r in later if spawn['slot'] - contact['dynamic']['first_whole'] < 96 and r['frame'] > spawn['frame']
                 and bytes.fromhex(r['dynamic']['ids_hex'])[spawn['slot'] - contact['dynamic']['first_whole']] == 0), None)
    flower = next((r['frame'] for r in later if bytes.fromhex(r['dynamic']['ids_hex'])[slot - contact['dynamic']['first_whole']] == fl), None)
    require(flower is not None, 'Explosion never became a flower in the observed stream')
    for r in later:
        cur = bytes.fromhex(r['dynamic']['ids_hex'])[slot - contact['dynamic']['first_whole']]
        if r['frame'] < flower:
            require(cur == ex, 'Explosion slot changed before flower')
    planted = next((r['frame'] for r in later if (lambda s: s and s['oRoutine'] >= 2)(slots_of(r, contact, 'flower').get(slot))), None)
    return dict(kind='mosqui_kill', frame=r1['frame'], slot=slot, mosqui_before=dict(x=before['oX'], y=before['oY'], routine=before['oRoutine']),
                player=dict(x=r1['centre_x'], y=r1['centre_y'], anim=f['oAnim'], flags=f['oFlags'], y_velocity=r1['y_velocity_s16']),
                score=[g0['score'], g1['score']], score_chain=[g0['scoreChain'], g1['scoreChain']], points=points,
                explosion_initialized_frame=later[init]['frame'], points_spawn=spawn, points_deleted_frame=gone,
                flower_frame=flower, flower_initialized_frame=planted,
                observed_explosion_polls=flower - r1['frame'],
                observed_points_polls=(gone - spawn['frame']) if gone else None)


def hurt_events(seq, contact, avs):
    """Accept player routine 2->4 only with knockback, ring loss and actual displacement."""
    contiguous(seq)
    events = []
    for k in range(1, len(seq)):
        if seq[k - 1]['fields']['oRoutine'] == 2 and seq[k]['fields']['oRoutine'] == 4:
            events.append(accept_hurt(seq, k, contact, avs))
    return events


def accept_hurt(seq, k, contact, avs):
    r0, r1 = seq[k - 1], seq[k]
    f, g0, g1 = r1['fields'], r0['contact_globals'], r1['contact_globals']
    water = bool(f['oFlags'] & 0x40)
    require(f['oAnim'] == 0x1a and r1['contact_player_fields']['oPlayerHurt'] == 0x78 and f['oFlags'] & 2,
            'Hurt flags without HurtPlayer writes')
    require(r1['y_velocity_s16'] == (-0x200 if water else -0x400) and abs(r1['x_velocity_s16']) == (0x100 if water else 0x200)
            and r1['inertia_s16'] == 0, 'Hurt without source knockback')
    require(g1['scoreChain'] == 0, 'ResetOnFloor score-chain write absent')
    hazards = [(slot, s) for slot, s in slots_of(r0, contact, 'mosqui').items()
               if s['oMap'] in contact['expected_maps']['mosqui'] and s['oColType'] == 0x2b
               and abs(s['oX'] - r0['centre_x']) <= 32 and abs(s['oY'] - r0['centre_y']) <= 48]
    require(len(hazards) == 1, 'No unique admitted Mosqui hazard beside the player')
    slot, hz = hazards[0]
    hx1 = slots_of(r1, contact, 'mosqui').get(slot, hz)['oX']
    sign = 1 if r1['x_velocity_s16'] > 0 else -1
    require(any((sign < 0) == (r1['centre_x'] < x) for x in (hz['oX'], hx1)), 'Knockback direction differs from hazard side')
    rings = r0['globals']['rings']  # R2 global (source rs.w), not duplicated in contact globals.
    require(rings > 0, 'Hurt recipe requires nonzero rings')
    want = min(rings, 32)
    free = sum(1 for oid in bytes.fromhex(r0['dynamic']['ids_hex']) if oid == 0)
    # FindObjSlot returns only ID-0 slots at its call, but a slot deleted earlier in the same object
    # pass can be reused, so a post-frame transition may read <other ID> -> $11 (recorded, not hidden).
    lost, cleared = {}, None
    lr = contact['ids']['lost_ring']
    for r in seq[k:k + WINDOW + 1]:
        for t in r['dynamic']['transitions']:
            before, after = bytes.fromhex(t['before_hex']), bytes.fromhex(t['after_hex'])
            if after[0] == lr and before[0] != lr:
                ring = decode_slot(t['after_hex'], contact)
                require(abs(ring['oX'] - r0['centre_x']) <= 16 and abs(ring['oY'] - r0['centre_y']) <= 16, 'Lost ring not at player')
                lost[t['slot']] = dict(frame=r['frame'], previous_postframe_id=before[0], x=ring['oX'], y=ring['oY'], routine=ring['oRoutine'])
        if cleared is None and r['globals']['rings'] == 0 and r['contact_globals']['ringLossAnimTimer']:
            cleared = r['frame']
    require(cleared is not None, 'Ring count/loss timer not updated')
    require(len(lost) == want or (free < want and free <= len(lost) < want), 'Lost-ring count differs from min(rings, 32)')
    for r, av in ((r0, avs.get(r0['frame'])), (r1, avs.get(r1['frame']))):
        require(av is not None, 'Missing AV row for latch check')
        latch(r, av, True)
    later = seq[k + 1:k + 9]
    require(any(r['x_16_16_unsigned'] != r1['x_16_16_unsigned'] for r in later) and
            any(r['y_16_16_unsigned'] < r1['y_16_16_unsigned'] for r in later), 'Hurt without actual displacement')
    moved = next(r for r in later if r['x_16_16_unsigned'] != r1['x_16_16_unsigned'])
    require((moved['x_16_16_unsigned'] > r1['x_16_16_unsigned']) == (sign > 0), 'Displacement opposes knockback')
    landing = next((r for r in seq[k + 1:] if r['fields']['oRoutine'] == 2), None)
    if landing:
        require(landing['contact_player_fields']['oPlayerHurt'] == 0x78 and landing['x_velocity_s16'] == 0 and
                landing['y_velocity_s16'] == 0 and landing['inertia_s16'] == 0, 'Hurt landing writes differ')
    return dict(kind='walking_hurt', frame=r1['frame'], hazard_slot=slot, hazard=dict(x=hz['oX'], y=hz['oY'], routine=hz['oRoutine']),
                player_before=dict(x=r0['centre_x'], y=r0['centre_y'], anim=r0['fields']['oAnim'], flags=r0['fields']['oFlags']),
                player_after=dict(x=r1['centre_x'], y=r1['centre_y'], x_velocity=r1['x_velocity_s16'], y_velocity=r1['y_velocity_s16']),
                rings_before=rings, lost_ring_slots=sorted(lost), lost_rings=lost, rings_cleared_frame=cleared,
                same_pass_slot_reuse=sorted(s for s, v in lost.items() if v['previous_postframe_id'] != 0),
                landing_frame=landing['frame'] if landing else None)


def restore_report(s, mode):
    """Separate restore categories. Control placeholders are not_observed, never a pass (review advisory 2)."""
    require(s.get('mode') == mode, 'Summary mode differs')
    cats = s['restore_category_equal']
    require(set(cats) == set(CATEGORIES), 'Restore category set differs')
    for name, value in cats.items():
        if mode == 'control' and name in CONTROL_NOT_OBSERVED:
            require(value == NOT_OBSERVED, f'Control category must be not_observed: {name}')
        else:
            require(type(value) is bool, f'Observed category not compared: {name}')
    observed = {k: v for k, v in cats.items() if v != NOT_OBSERVED}
    require(s['restore_120_equal'] == all(observed.values()), 'Restore label masks a failed category')
    names = {'av': 'av', 'player_core': 'player_core', 'contact': 'contact_globals_and_player_fields',
             'dynamic': 'dynamic_slots', 'screens': 'continuation_screenshots'}
    for key, name in names.items():
        if name in observed:
            require((s['forward_category_sha256'][key] == s['replay_category_sha256'][key]) == observed[name],
                    'Category label differs from hashes')
    raw = s['raw_final_states_equal']
    require(raw == (s['final_state_sha256'] == s['replay_final_state_sha256']), 'False raw-state equality label')
    require(s['raw_state_comparison'] == 'complete_unmasked_serialized_bytes', 'Masked raw-state comparison')
    if raw:
        require(s['raw_final_different_bytes'] == 0 and not s['raw_final_different_offsets_complete'], 'Raw equality with differences')
    else:
        require(s['raw_final_different_bytes'] > 0, 'Raw mismatch unaccounted')
        if s['final_state_bytes'] == s['replay_final_state_bytes']:
            require(len(s['raw_final_different_offsets_complete']) == s['raw_final_different_bytes'], 'Incomplete raw difference list')
    return dict(categories=cats, observed_categories=sorted(observed), not_observed=sorted(k for k, v in cats.items() if v == NOT_OBSERVED),
                raw_equal=raw, raw_different_bytes=s['raw_final_different_bytes'],
                raw_different_offsets=s['raw_final_different_offsets_complete'][:64])


def validate(root, recipe, r2_directory):
    root = Path(root)
    r2 = load_r2_check(r2_directory)
    cfg = json.loads((root / 'layout.json').read_text())
    scenario = json.loads((root / 'scenario.json').read_text())
    manifest = json.loads((root / 'manifest.json').read_text())
    require(manifest.get('kind') == 'final' and manifest['modes'] == list(MODES), 'Final three-mode capture required')
    producer_identity(manifest)
    independence = independent_specimens(root, scenario)  # Before any comparison can be tautological.
    base = r2.validate(root)  # Unchanged R2 identity/field/schedule/cold/control checks.
    summaries = {mode: json.loads((root / mode / 'summary.json').read_text()) for mode in MODES}
    checkpoint = checkpoint_equality(root, summaries)
    require(base['checkpoint_bytes_equal'] and len(set(base['checkpoint_sha256'].values())) == 1, 'Checkpoint divergence across cold modes')
    contact, n = cfg['contact'], scenario['frames']
    restore = {}
    for mode in MODES:
        restore[mode] = restore_report(summaries[mode], mode)
        require(all(v is True for v in restore[mode]['categories'].values() if v != NOT_OBSERVED), f'{mode} restored continuation differs')
        for shot in scenario['continuation_screenshots']:
            fw, rp = root / mode / f'forward-postframe-{shot:05d}.ppm', root / mode / f'replay-postframe-{shot:05d}.ppm'
            require(digest(fw) == digest(rp) == digest(root / 'observed-1' / fw.name), 'Continuation screenshot differs')
        if mode != 'control':
            for name in ('forward.jsonl', 'replay.jsonl'):
                require(all(isinstance(r['player'], dict) for r in rows(root / mode / name)), f'Observed {mode} {name} has a null payload')
    end_states = end_state_check(root, summaries)
    capture = rows(root / 'observed-1' / 'player.jsonl')
    forward = [r['player'] for r in rows(root / 'observed-1' / 'forward.jsonl')]
    avs = {r['frame']: r for r in rows(root / 'observed-1' / 'av.jsonl')}
    avs.update({r['av']['frame']: r['av'] for r in rows(root / 'observed-1' / 'forward.jsonl')})
    stream = capture + forward
    for r in stream:
        contact_row_check(r, contact)
    for r in [x['player'] for x in rows(root / 'observed-1' / 'replay.jsonl')]:
        contact_row_check(r, contact)
    seq = admitted(stream)
    kills, disappearances = kill_events(seq, contact, avs)
    hurts = hurt_events(seq, contact, avs)
    events = kills if recipe == 'kill' else hurts
    require(len(events) >= 1, f'No accepted {recipe} event')
    first = events[0]
    require(n < first['frame'] <= n + 120, 'Outcome is outside the restored 120-call continuation')
    cp = summaries['observed-1']['checkpoint_poll']
    require(cp['active_player'] and cp['fields']['oRoutine'] == 2, 'Checkpoint does not precede the outcome actively')
    if recipe == 'kill':
        require(first['flower_frame'] <= n + 120 and first['points_deleted_frame'] is not None, 'Kill aftermath incomplete inside continuation')
        require(not hurts, 'Kill recipe also contains a hurt')
    else:
        require(not kills, 'Hurt recipe also contains a kill')
    return dict(schema='soniccd-contact-events-v2', comparison_only=True, recipe=recipe, phase=cfg['phase'],
                checkpoint_frame=n, continuation=[n + 1, n + 120], events=events, kills_observed=len(kills), hurts_observed=len(hurts),
                mosqui_disappearances_without_explosion=disappearances, restore=restore, checkpoint=checkpoint,
                end_states={m: {k: v for k, v in e.items() if k != 'offsets'} | dict(offsets=e['offsets'][:64]) for m, e in end_states.items()},
                independence=independence, r2_validation={k: v for k, v in base.items() if k != 'evidence_sha256'},
                evidence_sha256={mode: {p.name: digest(p) for p in sorted((root / mode).iterdir()) if p.is_file()} for mode in MODES})


def discovery_outcome(root):
    """Compact per-branch timelines for authoring fixed schedules; never a final claim."""
    root = Path(root)
    cfg = json.loads((root / 'layout.json').read_text())
    contact = cfg['contact']
    prelude = rows(root / 'discovery' / 'player.jsonl')
    prelude_av = {r['frame']: r for r in rows(root / 'discovery' / 'av.jsonl')}
    scenario = json.loads((root / 'scenario.json').read_text())
    out = dict(schema='soniccd-contact-discovery-outcome-v1', comparison_only=True, branch_point=scenario['frames'], branches={})
    def track(seq):
        sample = []
        for r in seq:
            if r['frame'] % 5 == 0:
                ms = {s: (v['oX'], v['oY'], v['oRoutine']) for s, v in slots_of(r, contact, 'mosqui').items()}
                sample.append(dict(frame=r['frame'], x=r['centre_x'], y=r['centre_y'], xv=r['x_velocity_s16'], routine=r['fields']['oRoutine'],
                                   anim=r['fields']['oAnim'], rings=r['globals']['rings'], mosqui=ms))
        return sample
    pre = admitted(prelude)
    out['prelude_track'] = track(pre[-200:])
    for b in scenario['branches']:
        data = rows(root / 'discovery' / f'branch-{b["name"]}.jsonl')
        seq = [r['player'] for r in data]
        avs = dict(prelude_av)
        avs.update({r['av']['frame']: r['av'] for r in data})
        for r in seq:
            contact_row_check(r, contact)
        seq = admitted(pre[-1:] + seq)
        result = dict(inputs=b['inputs'])
        for name, fn in (('kills', lambda: kill_events(seq, contact, avs)[0]), ('hurts', lambda: hurt_events(seq, contact, avs))):
            try:
                result[name] = fn()
            except ValueError as error:
                result[name] = dict(rejected=str(error))
        result['disappearances'] = kill_events_safe(seq, contact, avs)
        result['routine_changes'] = [dict(frame=b2['frame'], routine=[a2['fields']['oRoutine'], b2['fields']['oRoutine']])
                                     for a2, b2 in zip(seq, seq[1:]) if a2['fields']['oRoutine'] != b2['fields']['oRoutine']]
        result['ring_changes'] = [dict(frame=b2['frame'], rings=[a2['globals']['rings'], b2['globals']['rings']])
                                  for a2, b2 in zip(seq, seq[1:]) if a2['globals']['rings'] != b2['globals']['rings']]
        result['contact_transitions'] = [dict(frame=r['frame'], slot=t['slot'], ids=[t['before_hex'][:2], t['after_hex'][:2]],
                                              routines=[t['before_hex'][72:74], t['after_hex'][72:74]])
                                         for r in seq for t in r['dynamic']['transitions']
                                         if kind_of(contact, int(t['before_hex'][:2], 16)) or kind_of(contact, int(t['after_hex'][:2], 16))]
        result['track'] = track(seq)
        out['branches'][b['name']] = result
    return out


def kill_events_safe(seq, contact, avs):
    m, ex = contact['ids']['mosqui'], contact['ids']['explosion']
    found = []
    for a, b in zip(seq, seq[1:]):
        x, y = bytes.fromhex(a['dynamic']['ids_hex']), bytes.fromhex(b['dynamic']['ids_hex'])
        found += [dict(frame=b['frame'], slot=contact['dynamic']['first_whole'] + i, new_id=y[i]) for i in range(len(x))
                  if x[i] == m and y[i] != m]
    return found


def write_inventory(root, output):
    root, output = Path(root), Path(output)
    rel = output.resolve().relative_to(root.resolve()).as_posix()
    require(not output.exists(), 'Inventory output exists')
    inv = artifact_inventory(root, exclude=(rel,))
    output.write_text(json.dumps(inv, indent=1) + '\n')
    again = artifact_inventory(root, exclude=(rel,))
    require(again == inv, 'Inventory changed while it was written')
    return {k: v for k, v in inv.items() if k not in ('files', 'symlinks')}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('root', type=Path)
    p.add_argument('--recipe', choices=('kill', 'hurt', 'discovery', 'inventory'), required=True)
    p.add_argument('--r2-directory', type=Path)
    p.add_argument('--output', type=Path)
    a = p.parse_args()
    if a.recipe == 'inventory':
        require(a.output is not None, 'Inventory needs --output inside the root')
        print(json.dumps(write_inventory(a.root, a.output), indent=1))
        return
    require(a.r2_directory is not None, '--r2-directory required')
    result = discovery_outcome(a.root) if a.recipe == 'discovery' else validate(a.root, a.recipe, a.r2_directory)
    if a.output:
        require(not a.output.exists(), 'Validation output exists')
        a.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k not in ('evidence_sha256', 'branches', 'prelude_track')}, indent=1)[:6000])


if __name__ == '__main__':
    main()
