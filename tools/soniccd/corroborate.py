#!/usr/bin/env python3
"""Read-only secondary comparison of the contact reference against static-report constants;
player-contact-reference-opus-r2, 2026-10-24 (R1: player-contact-reference-opus-r1/corroborate.py 53e9aeac...).

Input: validated final route directories (kill, hurt) from contact_reference.py. Output: one new JSON comparing
observed post-frame values with constants TRANSCRIBED from research/player-enemy-contact.md (cases
K1/E1/E6/P1/F1/H1/L1/L7/U2/U4). This script reads no source and checks no pin: it is secondary comparison
context, not an independent source oracle. F1 was changed after observation to the full-call semantics
(Flower/Main.asm:44-97, .NoFlags -> Seed -> .Fall -> Animate); the review re-derived it from the pinned source.
The L1 velocities are CalcSine at angles $88, $88 with X negated, and $98, each shifted left by 2
(Ring/Main.asm:343-397). The exact instruction phase of every poll stays unknown.
Usage: corroborate.py <native-root> <output.json>
"""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True


def rows(root):
    capture = [json.loads(s) for s in (root / 'observed-1/player.jsonl').read_text().splitlines()]
    forward = [json.loads(s)['player'] for s in (root / 'observed-1/forward.jsonl').read_text().splitlines()]
    return {r['frame']: r for r in capture + forward if r.get('program', '').startswith('USA_R11A')}


def slot(r, s):
    for x in r['dynamic']['contact_slots']:
        if x['slot'] == s:
            b = bytes.fromhex(x['raw_hex'])
            w = lambda o, n=2: int.from_bytes(b[o:o + n], 'big')
            sw = lambda o: w(o) - 0x10000 if w(o) >= 0x8000 else w(o)
            return dict(id=b[0], spr=b[1], tile=w(2), map=w(4, 4), x=w(8), y=w(12), xv=sw(16), yv=sw(18), yrad=b[22], xrad=b[23],
                        priority=b[24], width=b[25], map_frame=b[26], anim_frame=b[27], anim=b[28], prev_anim=b[29], anim_time=b[30],
                        col_type=b[32], col_status=b[33], flags=b[34], routine=b[36], routine2=b[37], subtype=b[40], subtype2=b[41],
                        var2a=b[42], var3e=w(62))
    return None


def check(name, observed, expected):
    return dict(case=name, observed=observed, expected_from_source=expected, equal=observed == expected)


def kill(root, ev):
    R = rows(root)
    k, s = ev['frame'], ev['slot']
    p0, p1 = R[k - 1], R[k]
    ps = ev['points_spawn']['slot']
    # Air gravity $38 precedes the touch in the same Main; KillEnemy then picks the source bounce branch.
    pre = p0['y_velocity_s16'] + 0x38
    branch = 'BounceDown +$100' if pre < 0 else 'BounceUp -$100' if p1['centre_y'] >= ev['mosqui_before']['y'] else 'neg'
    want = pre + 0x100 if pre < 0 else pre - 0x100 if branch.startswith('BounceUp') else -pre
    out = [check(f'K1 bounce branch {branch} (previous-poll air step, contact-poll yv)',
                 [p0['y_velocity_s16'] - R[k - 2]['y_velocity_s16'], p1['y_velocity_s16']], [0x38, want]),
           check('K1 score/chain/updateHUDScore', [p1['contact_globals'][x] for x in ('score', 'scoreChain', 'updateHUDScore')], [10, 2, 1])]
    e = slot(p1, s)
    out.append(check('E1 explosion after first run (routine, priority, tile, map, colType, subtype, flags bit7, var3E)',
                     [e['routine'], e['priority'], e['tile'], e['map'], e['col_type'], e['subtype'], e['flags'] & 0x80, e['var3e']],
                     [2, 1, 0x8680, 0x23f088, 0, 1, 0x80, 0]))
    out.append(check('E1 explosion animation after first run (anim, prev, animFrame, timer, mapFrame)',
                     [e['anim'], e['prev_anim'], e['anim_frame'], e['anim_time'], e['map_frame']], [1, 1, 1, 3, 0]))
    as18 = [f for f in sorted(R) if f >= k and (slot(R[f], s) or {}).get('id') == 0x18]
    r4 = [f for f in as18 if slot(R[f], s)['routine'] == 4]
    out.append(check('E6 polls as $18 / first routine-4 poll index / $1F next poll',
                     [len(as18), r4[0] - k + 1 if r4 else None, slot(R[as18[-1] + 1], s)['id'], slot(R[as18[-1] + 1], s)['routine']], [21, 21, 0x1f, 0]))
    pts = [f for f in sorted(R) if f >= k and (slot(R[f], ps) or {}).get('id') == 0x1c]
    first = slot(R[k + 1], ps)
    out.append(check('P1 points first run (routine, tile, map, mapFrame, timer, y = explosion y - 2)',
                     [first['routine'], first['tile'], first['map'], first['map_frame'], first['var2a'], first['y']],
                     [2, 0x6c6, 0x20ac64, 0, 23, e['y'] - 2]))
    out.append(check('P1 points lifetime: polls with $1C after the spawn poll (23 rises + delete)', len(pts) - 1, 23))
    rises = [slot(R[f], ps)['y'] for f in pts[1:]]
    out.append(check('P1 points rise 2 px per run', all(b - a == -2 for a, b in zip(rises, rises[1:])), True))
    fl = slot(R[as18[-1] + 2], s)
    # Flower/Main.asm:54-97: .NoFlags falls through into Seed (.Fall y+2) and Animate in the same call, so the
    # Init write oAnim word $0002 (prev 2) is reset by AnimateObject before the post-call poll.
    c = check('F1 flower after its first (Init->Seed->Animate) call (routine, tile, map, anim, prev, animFrame, timer, y-explosion y)',
              [fl['routine'], fl['tile'], fl['map'], fl['anim'], fl['prev_anim'], fl['anim_frame'], fl['anim_time'], fl['y'] - e['y']],
              [2, 0x6d7, 0x235ef4, 0, 0, 1, 3, 2])
    c['note'] = 'player-enemy-contact.md F1 lists prev 2, the mid-call Init write; the same call continues through Seed and Animate'
    out.append(c)
    land = next((f for f in sorted(R) if f > as18[-1] and (slot(R[f], s) or {}).get('routine') == 4), None)
    out.append(dict(case='F1 flower seed landing (routine 4) poll', observed=land, yrad=slot(R[land], s)['yrad'] if land else None,
                    expected_from_source='routine 4, oAnim 1, oYRadius $30 at landing; poll index not constructed'))
    return out


def hurt(root, ev):
    R = rows(root)
    h = ev['frame']
    p = R[h]
    f = p['fields']
    raw = bytes.fromhex(p['player_raw_hex'])
    out = [check('H1 player writes (routine, flags, anim, hurt, xv, yv, gv, charge, jump)',
                 [f['oRoutine'], f['oFlags'], f['oAnim'], p['contact_player_fields']['oPlayerHurt'], p['x_velocity_s16'], p['y_velocity_s16'],
                  p['inertia_s16'], f['oPlayerCharge'], f['oPlayerJump']], [4, 2, 0x1a, 0x78, -0x200, -0x400, 0, 0, 0]),
           check('H1 globals (scoreChain, combineRing, shield, rings, updateHUDRings, livesFlags)',
                 [p['contact_globals'][x] for x in ('scoreChain', 'combineRing', 'shield')] + [p['globals']['rings']] +
                 [p['contact_globals'][x] for x in ('updateHUDRings', 'livesFlags')], [0, 0, 0, 0, 0x80, 0])]
    # Ring order from the source spawn loop: spawner (first free) is ring 0, then FindObjSlot order.
    table = [(-0xc4, -0x3ec), (0xc4, -0x3ec), (-0x238, -0x350), (0x238, -0x350)]
    rings = []
    for i, s in enumerate(ev['lost_ring_slots']):
        x = slot(p, s)
        rings.append(check(f'L1 lost ring {i} in slot {s} after Init+Main (CalcSine {("$88", "$88 X negated", "$98", "$98 X negated")[i]} << 2: xv, yv+$18; tile, priority, spr&$7F, colType, width, radii, map, routine)',
                           [x['xv'], x['yv'], x['tile'], x['priority'], x['spr'] & 0x7f, x['col_type'], x['width'], x['yrad'], x['xrad'], x['map'], x['routine']],
                           [table[i][0], table[i][1] + 0x18, 0xa7ae, 3, 4, 0x47, 8, 8, 8, 0x20a0fa, 2]))
    out += rings
    spill = [(R[f]['contact_globals']['ringLossAnimTimer'], R[f]['contact_globals']['ringLossAnimAccum'], R[f]['contact_globals']['ringLossAnimFrame'])
             for f in range(h, h + 9)]
    accum, series = 0, []
    timer = 0xff
    for _ in range(9):
        accum = (accum + timer) & 0xffff
        series.append((timer - 1, accum, (accum >> 9) & 3))
        timer -= 1
    out.append(check('L7 .RingSpill from timer $FF, accum 0 (post-frame timer, accum, frame)', [list(x) for x in spill], [list(x) for x in series]))
    held = [R[f]['contact_player_fields']['oPlayerHurt'] for f in range(h, ev['landing_frame'])]
    out.append(check('U2 no Display in routine 4: hurt timer constant until landing', sorted(set(held)), [0x78]))
    L = R[ev['landing_frame']]
    out.append(check('U2 landing writes (routine, xv, yv, gv, anim, hurt)', [L['fields']['oRoutine'], L['x_velocity_s16'], L['y_velocity_s16'],
                     L['inertia_s16'], L['fields']['oAnim'], L['contact_player_fields']['oPlayerHurt']], [2, 0, 0, 0, 0, 120]))
    out.append(check('U2 Display decrement on the next routine-2 poll', R[ev['landing_frame'] + 1]['contact_player_fields']['oPlayerHurt'], 119))
    bounces = []
    for s in ev['lost_ring_slots']:
        prev = None
        for fr in sorted(R):
            if fr < h:
                continue
            x = slot(R[fr], s)
            if not x or x['id'] != 0x11:
                break
            if prev is not None and prev['yv'] >= 0 and x['yv'] < 0:
                # Ring/Main.asm:415-418 reads levelVIntCounter+3, the low byte of the big-endian long, plus d7.
                bounces.append(dict(slot=s, frame=fr, d7=127 - s, level_vint_counter_postframe=R[fr]['globals']['levelVIntCounter'],
                                    rule_with_postframe_counter=((R[fr]['globals']['levelVIntCounter'] & 0xff) + 127 - s) & 3))
            prev = x
    c = check('U4 floor-bounce polls satisfy ((low byte of post-frame levelVIntCounter + d7) & 3) == 0',
              sorted({b['rule_with_postframe_counter'] for b in bounces}), [0])
    c.update(bounces=bounces, note='consistent with the in-pass counter equalling the post-frame value mod 4 at these polls; '
             'non-bounce polls are not tested and no V-int phase is certified')
    out.append(c)
    return out


def main():
    root, output = Path(sys.argv[1]), Path(sys.argv[2])
    if output.exists():
        raise SystemExit('Output exists')
    kev = json.loads((root / 'kill/events.json').read_text())['events'][0]
    hev = json.loads((root / 'hurt/events.json').read_text())['events'][0]
    result = dict(schema='soniccd-contact-corroboration-v2', comparison_only=True,
                  expectation_provenance=('constants transcribed from research/player-enemy-contact.md; secondary comparison '
                                          'context, not an independent source oracle; F1 corrected after observation to the '
                                          'full-call semantics of Flower/Main.asm:44-97 (re-derived from the pinned source in review)'),
                  phase='after_FrameAdvance_and_GetSamplesSync; exact_VInt/player/DPLC_slot_unknown',
                  source_cases='research/player-enemy-contact.md (static report over pin 1ecc5f3a and the USA R11A tables)',
                  kill=kill(root / 'kill', kev), hurt=hurt(root / 'hurt', hev))
    output.write_text(json.dumps(result, indent=1) + '\n')
    for group in ('kill', 'hurt'):
        for c in result[group]:
            print(group, c.get('equal'), c['case'], c['observed'] if not isinstance(c['observed'], list) or len(str(c['observed'])) < 160 else '...')


if __name__ == '__main__':
    main()
