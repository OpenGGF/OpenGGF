"""ROM-free controls for player-contact-reference-opus-r2 (2026-10-24).

Synthetic rows, files and Git fixtures only; no media, emulator or engine. Every negative control must be
rejected for its stated reason. Run with TMPDIR outside every tool and evidence root (the system default).
R2 adds: independent-specimen gates (P2-1), deterministic inventories (P2-2), media/research/--protect
guards (P2-3), pinned R2 imports and enforced checkpoint/end-state equality (P2-4), control placeholders.
"""
import copy
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
R2 = Path(os.environ.get('CONTACT_R2_DIRECTORY') or
          (HERE.parent / 'player-reference-tools-r2' if (HERE.parent / 'player-reference-tools-r2').is_dir() else HERE))
sys.dont_write_bytecode = True
import contact_reference as producer  # noqa: E402
import contact_reference_check as check  # noqa: E402

FIELDS = dict(oID=(0, 1), oSprFlags=(1, 1), oTile=(2, 2), oMap=(4, 4), oX=(8, 2), oXSub=(10, 2), oY=(12, 2), oYSub=(14, 2),
              oXVel=(16, 2), oYVel=(18, 2), oYRadius=(22, 1), oXRadius=(23, 1), oPriority=(24, 1), oWidth=(25, 1),
              oMapFrame=(26, 1), oAnimFrame=(27, 1), oAnim=(28, 1), oPrevAnim=(29, 1), oAnimTime=(30, 1),
              oColType=(32, 1), oColStatus=(33, 1), oFlags=(34, 1), oRoutine=(36, 1), oRoutine2=(37, 1), oAngle=(38, 1),
              oSubtype=(40, 1), oSubtype2=(41, 1))
CONTACT = dict(
    globals=dict(scoreChain=dict(offset=0xf7d0, width=2), score=dict(offset=0x1518, width=4),
                 invincible=dict(offset=0x151f, width=1), timeWarp=dict(offset=0x1521, width=1),
                 ringLossAnimTimer=dict(offset=0x1916, width=1)),
    player_fields=dict(oPlayerHurt=dict(offset=0x30, width=2)),
    slot_fields={k: dict(offset=o, width=w) for k, (o, w) in FIELDS.items()},
    object_specific=dict(explosion=dict(oExplodeBadnik=dict(offset=0x25, width=1), oExplodePoints=dict(offset=0x3e, width=2)),
                         points=dict(oPntsTimer=dict(offset=0x2a, width=1))),
    dynamic=dict(offset=0xd800, count=96, slot_bytes=64, first_whole=32),
    ids=dict(mosqui=0x3d, explosion=0x18, points=0x1c, flower=0x1f, lost_ring=0x11),
    expected_maps=dict(mosqui=[0x20b6e6, 0x20b722], explosion=[0x23f088], points=[0x20ac64], flower=[0x235ef4], lost_ring=[0x20a0fa]),
    enemy_points=dict(values=[10, 20, 50, 100]))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def slot(**values):
    b = bytearray(64)
    for k, v in values.items():
        o, w = FIELDS[k] if k in FIELDS else {'points': (0x3e, 2), 'timer': (0x2a, 1)}[k]
        b[o:o + w] = (v & ((1 << (8 * w)) - 1)).to_bytes(w, 'big')
    return bytes(b)


def mosqui(x=0x270, y=0x760, routine=2):
    return slot(oID=0x3d, oMap=0x20b6e6, oColType=0x2b, oRoutine=routine, oX=x, oY=y, oSprFlags=0x84)


class Timeline:
    """Builds contiguous admitted rows with ID/routine transitions computed like the probe."""
    def __init__(self, frame=100):
        self.frame, self.rows, self.avs, self.prev = frame, [], {}, None
        self.player = dict(x=0x200, y=0x76b, xv=0, yv=0, gv=0, routine=2, anim=0, flags=0, hurt=0)
        self.g = dict(scoreChain=0, score=0, invincible=0, timeWarp=0, ringLossAnimTimer=0)
        self.rings = 3
        self.slots = {}
        self.pad = []

    def emit(self, **change):
        self.player.update({k: v for k, v in change.items() if k in self.player})
        self.g.update({k: v for k, v in change.items() if k in self.g})
        self.rings = change.get('rings', self.rings)
        p = self.player
        raw = bytearray(64)
        raw[8:10], raw[12:14] = p['x'].to_bytes(2, 'big'), p['y'].to_bytes(2, 'big')
        raw[0x30:0x32] = p['hurt'].to_bytes(2, 'big')
        dyn = bytearray(96 * 64)
        for s, b in self.slots.items():
            dyn[(s - 32) * 64:(s - 31) * 64] = b
        ids, routines = bytes(dyn[i * 64] for i in range(96)), bytes(dyn[i * 64 + 36] for i in range(96))
        transitions = []
        if self.prev is not None:
            for i in range(96):
                if (self.prev[i * 64], self.prev[i * 64 + 36]) != (ids[i], routines[i]):
                    transitions.append(dict(slot=32 + i, before_hex=bytes(self.prev[i * 64:(i + 1) * 64]).hex(),
                                            after_hex=bytes(dyn[i * 64:(i + 1) * 64]).hex()))
        m = check.mask(['P1 ' + b for b in self.pad])
        row = dict(frame=self.frame, program='USA_R11A_source_and_loaded_windows_matched', accepted=True, active_player=p['routine'] == 2,
                   fields=dict(oRoutine=p['routine'], oAnim=p['anim'], oFlags=p['flags']),
                   globals=dict(p1CtrlHold=m, p1CtrlTap=0, playerCtrlHold=m, playerCtrlTap=0, ctrlLocked=0, rings=self.rings),
                   centre_x=p['x'], centre_y=p['y'], x_16_16_unsigned=p['x'] << 16, y_16_16_unsigned=p['y'] << 16,
                   x_velocity_s16=p['xv'], y_velocity_s16=p['yv'], inertia_s16=p['gv'], player_raw_hex=bytes(raw).hex(),
                   contact_globals=dict(self.g),
                   contact_globals_raw_hex={k: v.to_bytes(CONTACT['globals'][k]['width'], 'big').hex() for k, v in self.g.items()},
                   contact_player_fields=dict(oPlayerHurt=p['hurt']),
                   dynamic=dict(first_whole=32, ids_hex=ids.hex(), routines_hex=routines.hex(), region_sha256='0' * 64,
                                contact_slots=[dict(slot=32 + i, raw_hex=bytes(dyn[i * 64:(i + 1) * 64]).hex())
                                               for i in range(96) if ids[i] in CONTACT['ids'].values()],
                                transitions=transitions))
        self.rows.append(row)
        self.avs[self.frame] = dict(frame=self.frame, input=sorted('P1 ' + b for b in self.pad))
        self.prev, self.frame = dyn, self.frame + 1
        return row


def kill_timeline(points=True, flower=True, anim=2, score=10, routine2=0):
    t = Timeline()
    t.slots[40] = mosqui()
    t.emit()
    t.slots[40] = slot(oID=0x18, oMap=0x23f088, oSubtype=1, oFlags=0x80, oRoutine=2, oRoutine2=routine2, oX=0x270, oY=0x760, oSprFlags=0x84)
    if points:
        t.slots[32] = slot(oID=0x1c, oMap=0x20ac64, oSubtype=0x80, oRoutine=2, oX=0x270, oY=0x75e, timer=23)
    t.emit(anim=anim, scoreChain=2, score=score)
    for i in range(22):
        if points and i == 22 - 1:
            del t.slots[32]
        t.emit()
    if flower:
        t.slots[40] = slot(oID=0x1f, oMap=0x23f088, oRoutine=0, oX=0x270, oY=0x760)
        t.emit()
        t.slots[40] = slot(oID=0x1f, oMap=0x235ef4, oRoutine=2, oX=0x270, oY=0x762)
        t.emit()
    else:
        for i in range(3):
            t.emit()
    return t


def hurt_timeline(rings=3, displaced=True, ringloss=True, sign=-1, yv=-0x400):
    t = Timeline(200)
    t.rings = rings
    t.slots[41] = mosqui(x=0x290, y=0x76b, routine=8)
    t.player.update(x=0x280)
    t.pad = ['Right']
    t.emit()
    if ringloss:
        for i in range(min(rings, 32)):
            t.slots[32 + i if 32 + i != 41 else 60] = slot(oID=0x11, oMap=0x20a0fa, oRoutine=2, oX=0x280 + (i % 3), oY=0x768)
    t.emit(routine=4, anim=0x1a, hurt=0x78, flags=2, yv=yv, xv=sign * 0x200, gv=0, scoreChain=0,
           rings=0 if ringloss else rings, ringLossAnimTimer=0xfe if ringloss else 0)
    for i in range(1, 12):
        if displaced:
            t.emit(x=t.player['x'] + sign * 2, y=t.player['y'] - 3, ringLossAnimTimer=0xfe - i if ringloss else 0)
        else:
            t.emit()
    t.emit(routine=2, anim=0, xv=0, yv=0, gv=0)
    return t


class Schedules(unittest.TestCase):
    def setUp(self):
        self.r2 = producer.load_r2(R2)
        self.final = dict(frames=10, inputs=[dict(first=1, last=1, button='Start')], screenshots=[1], continuation_screenshots=[11, 130])
        self.disc = dict(frames=10, inputs=[], screenshots=[], branches=[dict(name='a', frames=5, inputs=[dict(first=11, last=15, button='Right')], screenshots=[12])])

    def test_valid_schedules(self):
        producer.check_final(self.final, self.r2)
        self.assertEqual(producer.check_discovery(self.disc), 15)

    def test_final_rejects_out_of_window_screens_branches_and_bound(self):
        for change, message in ((dict(continuation_screenshots=[10]), 'Continuation screenshot'),
                                (dict(continuation_screenshots=[131]), 'Continuation screenshot'),
                                (dict(branches=[]), 'no discovery branches'), (dict(frames=5761), 'must fit 6000')):
            s = dict(copy.deepcopy(self.final), **change)
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, message):
                producer.check_final(s, self.r2)

    def test_discovery_rejects_bad_branches(self):
        cases = ((lambda s: s['branches'][0].update(frames=5991), '6000-call'),
                 (lambda s: s['branches'][0]['inputs'][0].update(last=16), 'Input outside'),
                 (lambda s: s['branches'][0].update(name='../x'), 'Unsafe branch name'),
                 (lambda s: s['branches'].append(copy.deepcopy(s['branches'][0])), 'unique names'),
                 (lambda s: s['branches'][0]['inputs'][0].update(button='Cheat'), 'Unknown button'),
                 (lambda s: s['inputs'].append(dict(first=11, last=11, button='A')), 'Input outside'),
                 (lambda s: s['branches'][0].update(screenshots=[10]), 'Screenshot outside branch'))
        for mutate, message in cases:
            s = copy.deepcopy(self.disc)
            mutate(s)
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                producer.check_discovery(s)

    def test_r2_producer_pin(self):
        with tempfile.TemporaryDirectory() as d:
            Path(d, 'player_reference.py').write_bytes((R2 / 'player_reference.py').read_bytes() + b'# drift\n')
            with self.assertRaisesRegex(ValueError, 'R2 producer hash mismatch'):
                producer.load_r2(d)

    def test_reference_pin(self):
        with tempfile.TemporaryDirectory() as d:
            fake = Path(d, 'netstandard.dll')
            fake.write_bytes(b'not the pinned facade')
            with self.assertRaisesRegex(ValueError, 'netstandard reference hash mismatch'):
                producer.verify_pin(fake, producer.NETSTANDARD_SHA256, 'netstandard reference')

    def test_rs_width_aliases(self):
        inv = types.SimpleNamespace(expression=lambda text, names=None: eval(text.replace('$', '0x'), {}, dict(names or {})))
        text = '\trsreset\na\trs.b\t1\nalias\trs.b\t0\nb\trs.w\t1\n\trs.b\t2\nc\trs.l\t3\n'
        widths, offsets, _ = producer.rs_widths(inv, text, {})
        self.assertEqual((widths, offsets), (dict(a=1, alias=2, b=2, c=12), dict(a=0, alias=1, b=1, c=5)))


class Events(unittest.TestCase):
    def test_valid_kill(self):
        t = kill_timeline()
        for r in t.rows:
            check.contact_row_check(r, CONTACT)
        kills, gone = check.kill_events(t.rows, CONTACT, t.avs)
        self.assertEqual((len(kills), gone), (1, []))
        self.assertEqual((kills[0]['frame'], kills[0]['points'], kills[0]['flower_frame']), (101, 10, 124))

    def test_mosqui_disappearing_without_explosion_is_not_a_kill(self):
        t = Timeline()
        t.slots[40] = mosqui()
        t.emit()
        del t.slots[40]
        t.emit()
        kills, gone = check.kill_events(t.rows, CONTACT, t.avs)
        self.assertEqual((kills, [g['new_id'] for g in gone]), ([], [0]))

    def test_false_kills_rejected(self):
        for kwargs, message in ((dict(points=False), 'Points object'), (dict(flower=False), 'never became a flower'),
                                (dict(anim=0), 'roll/jump'), (dict(score=20), 'Score delta'),
                                (dict(routine2=2), 'badnik precondition')):
            with self.subTest(kwargs=kwargs), self.assertRaisesRegex(ValueError, message):
                t = kill_timeline(**kwargs)
                check.kill_events(t.rows, CONTACT, t.avs)

    def test_kill_latch_mismatch_rejected(self):
        t = kill_timeline()
        t.rows[1]['globals']['playerCtrlHold'] = 8
        with self.assertRaisesRegex(ValueError, 'Input latch divergence'):
            check.kill_events(t.rows, CONTACT, t.avs)

    def test_raw_latch_must_match_schedule(self):
        t = kill_timeline()
        t.avs[101]['input'] = ['P1 Right']
        with self.assertRaisesRegex(ValueError, 'Raw input latch'):
            check.kill_events(t.rows, CONTACT, t.avs)

    def test_actor_gate_and_summary_consistency(self):
        t = kill_timeline()
        r = copy.deepcopy(t.rows[2])
        s = next(x for x in r['dynamic']['contact_slots'] if x['slot'] == 40)
        b = bytearray.fromhex(s['raw_hex']); b[4:8] = (0x123456).to_bytes(4, 'big'); s['raw_hex'] = b.hex()
        with self.assertRaisesRegex(ValueError, 'Unverified explosion actor'):
            check.contact_row_check(r, CONTACT)
        r = copy.deepcopy(t.rows[2])
        r['dynamic']['contact_slots'].pop()
        with self.assertRaisesRegex(ValueError, 'Contact slot set'):
            check.contact_row_check(r, CONTACT)
        r = copy.deepcopy(t.rows[2])
        r['contact_globals']['score'] += 1
        with self.assertRaisesRegex(ValueError, 'endian/value'):
            check.contact_row_check(r, CONTACT)
        r = copy.deepcopy(t.rows[2])
        r['contact_globals_raw_hex']['scoreChain'] = '0002'[:2]
        with self.assertRaisesRegex(ValueError, 'width'):
            check.contact_row_check(r, CONTACT)

    def test_valid_walking_hurt(self):
        t = hurt_timeline()
        for r in t.rows:
            check.contact_row_check(r, CONTACT)
        hurts = check.hurt_events(t.rows, CONTACT, t.avs)
        self.assertEqual((len(hurts), hurts[0]['rings_before'], len(hurts[0]['lost_ring_slots']), hurts[0]['landing_frame']), (1, 3, 3, 213))

    def test_false_hurts_rejected(self):
        for kwargs, message in ((dict(displaced=False), 'actual displacement'), (dict(ringloss=False), 'Ring count'),
                                (dict(rings=0, ringloss=False), 'nonzero rings'), (dict(sign=1), 'Knockback direction'),
                                (dict(yv=-0x300), 'source knockback')):
            with self.subTest(kwargs=kwargs), self.assertRaisesRegex(ValueError, message):
                t = hurt_timeline(**kwargs)
                check.hurt_events(t.rows, CONTACT, t.avs)

    def same_pass(self, include_second_ring=True):
        t = Timeline(200)
        t.slots[41] = mosqui(x=0x290, y=0x76b, routine=8)
        t.slots[32] = slot(oID=0x10, oRoutine=2, oX=240, oY=1824)  # off-screen ring deleted earlier in the pass
        t.player.update(x=0x280)
        t.pad = ['Right']
        t.rings = 2
        t.emit()
        t.slots[32] = slot(oID=0x11, oMap=0x20a0fa, oRoutine=2, oX=0x280, oY=0x76b)
        if include_second_ring:
            t.slots[33] = slot(oID=0x11, oMap=0x20a0fa, oRoutine=2, oX=0x280, oY=0x767)
        t.emit(routine=4, anim=0x1a, hurt=0x78, flags=2, yv=-0x400, xv=-0x200, gv=0, rings=0, ringLossAnimTimer=0xfe)
        for i in range(1, 12):
            t.emit(x=t.player['x'] - 2, y=t.player['y'] - 3, ringLossAnimTimer=0xfe - i)
        return t

    def test_same_pass_slot_reuse_counted_and_reported(self):
        t = self.same_pass()
        for r in t.rows:
            check.contact_row_check(r, CONTACT)
        hurt = check.hurt_events(t.rows, CONTACT, t.avs)[0]
        self.assertEqual((hurt['lost_ring_slots'], hurt['same_pass_slot_reuse']), ([32, 33], [32]))

    def test_missing_lost_ring_rejected_with_consistent_rows(self):
        # The rows, summaries and transitions all agree that only one ring spawned for two lost rings.
        t = self.same_pass(include_second_ring=False)
        for r in t.rows:
            check.contact_row_check(r, CONTACT)
        with self.assertRaisesRegex(ValueError, 'Lost-ring count'):
            check.hurt_events(t.rows, CONTACT, t.avs)

    def test_hurt_flags_without_writes_rejected(self):
        t = hurt_timeline()
        t.rows[1]['contact_player_fields']['oPlayerHurt'] = 0
        with self.assertRaisesRegex(ValueError, 'HurtPlayer writes'):
            check.hurt_events(t.rows, CONTACT, t.avs)

    def test_noncontiguous_stream_rejected(self):
        t = hurt_timeline()
        del t.rows[5]
        with self.assertRaisesRegex(ValueError, 'contiguous'):
            check.hurt_events(t.rows, CONTACT, t.avs)


class Restore(unittest.TestCase):
    H = dict(av='a', player_core='b', contact='c', dynamic='d', screens='e')

    def summary(self, mode='observed-1'):
        cats = {c: True for c in check.CATEGORIES}
        if mode == 'control':
            cats.update({c: 'not_observed' for c in check.CONTROL_NOT_OBSERVED})
        return dict(mode=mode, restore_category_equal=cats, restore_120_equal=True, forward_category_sha256=dict(self.H),
                    replay_category_sha256=dict(self.H), raw_final_states_equal=True, final_state_sha256='x', replay_final_state_sha256='x',
                    raw_state_comparison='complete_unmasked_serialized_bytes', raw_final_different_bytes=0,
                    raw_final_different_offsets_complete=[], final_state_bytes=10, replay_final_state_bytes=10)

    def test_valid_observed_control_and_reported_raw_difference(self):
        check.restore_report(self.summary(), 'observed-1')
        report = check.restore_report(self.summary('control'), 'control')
        self.assertEqual(sorted(k for k, v in report['categories'].items() if v == 'not_observed'), sorted(check.CONTROL_NOT_OBSERVED))
        s = self.summary()
        s.update(raw_final_states_equal=False, replay_final_state_sha256='y', raw_final_different_bytes=2, raw_final_different_offsets_complete=[3, 4])
        self.assertEqual(check.restore_report(s, 'observed-1')['raw_different_bytes'], 2)

    def test_masked_partial_or_placeholder_restore_rejected(self):
        cases = [
            ('observed-1', lambda s: s['restore_category_equal'].update(dynamic_slots=False), 'masks a failed category'),
            ('observed-1', lambda s: (s['restore_category_equal'].update(dynamic_slots=False), s.update(restore_120_equal=False)),
             'Category label differs from hashes'),
            ('observed-1', lambda s: s.update(replay_final_state_sha256='y'), 'False raw-state equality label'),
            ('observed-1', lambda s: s.update(raw_state_comparison='masked'), 'Masked raw-state comparison'),
            ('observed-1', lambda s: s.update(raw_final_states_equal=False, replay_final_state_sha256='y', raw_final_different_bytes=3,
                                              raw_final_different_offsets_complete=[1]), 'Incomplete raw difference list'),
            ('observed-1', lambda s: s['restore_category_equal'].pop('av'), 'Restore category set differs'),
            ('observed-1', lambda s: s['restore_category_equal'].update(dynamic_slots='not_observed'), 'Observed category not compared'),
            ('control', lambda s: s['restore_category_equal'].update(player_core=True), 'Control category must be not_observed'),
            ('control', lambda s: s['restore_category_equal'].update(av=False), 'masks a failed category'),
            ('observed-1', lambda s: s.update(mode='control'), 'Summary mode differs'),
        ]
        for mode, mutate, message in cases:
            s = self.summary(mode)
            mutate(s)
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                check.restore_report(s, mode)


class Specimens(unittest.TestCase):
    """P2-1/P2-4b: comparisons need independent physical files and real equality."""
    SCENARIO = dict(frames=10, screenshots=[10], continuation_screenshots=[12])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='contact specimens ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'final'
        self.summaries = {}
        for mode in check.MODES:
            d = self.root / mode
            d.mkdir(parents=True)
            names = check.evidence_names(self.SCENARIO, mode)
            for name in names:
                (d / name).write_bytes(b'checkpoint' if name == 'checkpoint.state' else name.encode())
            (d / 'forward-end.state').write_bytes(b'ABCDEF')
            (d / 'replay-end.state').write_bytes(b'ABxDEy')
            (d / 'summary.json').write_text('{}')
            self.summaries[mode] = dict(checkpoint_sha256=sha(b'checkpoint'), final_state_sha256=sha(b'ABCDEF'),
                                        replay_final_state_sha256=sha(b'ABxDEy'), raw_final_different_offsets_complete=[2, 5],
                                        raw_final_different_bytes=2, final_state_bytes=6, replay_final_state_bytes=6)

    def test_independent_specimens_accepted(self):
        result = check.independent_specimens(self.root, self.SCENARIO)
        self.assertGreater(result['pairs'], 20)
        check.checkpoint_equality(self.root, self.summaries)
        self.assertEqual(check.end_state_check(self.root, self.summaries)['observed-1']['different_bytes'], 2)

    def relink(self, a, b, kind):
        b.unlink()
        if kind == 'hard':
            os.link(a, b)
        else:
            b.symlink_to(os.path.relpath(a, b.parent))

    def test_aliased_pairs_rejected(self):
        o1, o2, c = (self.root / m for m in check.MODES)
        cases = [(o1 / 'player.jsonl', o2 / 'player.jsonl', 'hard'), (o1 / 'player.jsonl', o2 / 'player.jsonl', 'sym'),
                 (o1 / 'forward.jsonl', o1 / 'replay.jsonl', 'hard'), (o1 / 'checkpoint.state', c / 'checkpoint.state', 'hard'),
                 (o2 / 'forward-postframe-00012.ppm', o2 / 'replay-postframe-00012.ppm', 'sym'),
                 (o1 / 'forward-end.state', c / 'forward-end.state', 'hard')]
        for a, b, kind in cases:
            with self.subTest(pair=(a.name, b.name, kind)):
                self.setUp()
                o1, o2, c = (self.root / m for m in check.MODES)
                a, b = self.root / a.parent.name / a.name, self.root / b.parent.name / b.name
                self.relink(a, b, kind)
                with self.assertRaisesRegex(ValueError, 'one physical file'):
                    check.independent_specimens(self.root, self.SCENARIO)

    def test_symlink_to_independent_copy_and_external_hardlink_rejected(self):
        o2 = self.root / 'observed-2'
        outside = Path(self.temp.name) / 'outside-copy'
        outside.write_bytes((o2 / 'av.jsonl').read_bytes())
        (o2 / 'av.jsonl').unlink()
        (o2 / 'av.jsonl').symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'is a symlink'):
            check.independent_specimens(self.root, self.SCENARIO)
        (o2 / 'av.jsonl').unlink()
        os.link(outside, o2 / 'av.jsonl')
        with self.assertRaisesRegex(ValueError, 'not an independent regular file'):
            check.independent_specimens(self.root, self.SCENARIO)

    def test_symlinked_mode_directory_and_missing_specimen_rejected(self):
        import shutil
        shutil.rmtree(self.root / 'observed-2')
        (self.root / 'observed-2').symlink_to('observed-1', target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'Mode directory is not a real directory'):
            check.independent_specimens(self.root, self.SCENARIO)
        (self.root / 'observed-2').unlink()
        (self.root / 'observed-2').mkdir()
        with self.assertRaisesRegex(ValueError, 'Missing evidence specimen'):
            check.independent_specimens(self.root, self.SCENARIO)

    def test_checkpoint_divergence_rejected(self):
        (self.root / 'control' / 'checkpoint.state').write_bytes(b'checkpoinT')
        summaries = copy.deepcopy(self.summaries)
        summaries['control']['checkpoint_sha256'] = sha(b'checkpoinT')
        with self.assertRaisesRegex(ValueError, 'Checkpoint divergence across cold modes'):
            check.checkpoint_equality(self.root, summaries)
        with self.assertRaisesRegex(ValueError, 'Checkpoint hash differs from summary'):
            check.checkpoint_equality(self.root, self.summaries)

    def test_end_state_retention_and_recomputation(self):
        s = copy.deepcopy(self.summaries)
        s['observed-2']['raw_final_different_offsets_complete'] = [2]
        with self.assertRaisesRegex(ValueError, 'Recomputed raw difference differs from summary'):
            check.end_state_check(self.root, s)
        (self.root / 'control' / 'replay-end.state').write_bytes(b'ABxDEz')
        s = copy.deepcopy(self.summaries)
        s['control']['replay_final_state_sha256'] = sha(b'ABxDEz')
        with self.assertRaisesRegex(ValueError, 'End states differ across cold modes'):
            check.end_state_check(self.root, s)
        with self.assertRaisesRegex(ValueError, 'End-state hash differs from summary'):
            check.end_state_check(self.root, self.summaries)


class PinnedImports(unittest.TestCase):
    """P2-4a: the frozen R2 checker must bind the pinned player_reference, whatever sys.path/sys.modules hold."""
    def fixture(self, producer_suffix=b'', checker_suffix=b''):
        d = tempfile.TemporaryDirectory(prefix='contact pins ')
        self.addCleanup(d.cleanup)
        root = Path(d.name)
        (root / 'player_reference.py').write_bytes((R2 / 'player_reference.py').read_bytes() + producer_suffix)
        (root / 'player_reference_check.py').write_bytes((R2 / 'player_reference_check.py').read_bytes() + checker_suffix)
        return root

    def test_pinned_modules_bound(self):
        # Unittest discovery may already place the promoted tools/ directory on sys.path.
        # The loader must preserve either caller layout, rather than require the path absent.
        without_r2 = [p for p in sys.path if not p or Path(p).resolve() != R2.resolve()]
        for path in (without_r2, [str(R2.resolve()), *without_r2]):
            with self.subTest(preexisting_r2=str(R2.resolve()) in path), patch.object(sys, 'path', path.copy()):
                before = sys.path.copy()
                loaded = check.load_r2_check(R2)
                self.assertEqual(Path(loaded.require.__code__.co_filename).resolve(), (R2 / 'player_reference.py').resolve())
                self.assertEqual(before, sys.path)

    def test_drifted_modules_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Frozen R2 producer hash mismatch'):
            check.load_r2_check(self.fixture(producer_suffix=b'# drift\n'))
        with self.assertRaisesRegex(ValueError, 'Frozen R2 checker hash mismatch'):
            check.load_r2_check(self.fixture(checker_suffix=b'# drift\n'))

    def test_foreign_module_and_path_cannot_bypass_pin(self):
        foreign = types.ModuleType('player_reference')
        foreign.require = foreign.check_schedule = foreign.dump = lambda *a, **k: None
        shadow = tempfile.TemporaryDirectory(prefix='contact shadow ')
        self.addCleanup(shadow.cleanup)
        Path(shadow.name, 'player_reference.py').write_text('def require(*a): pass\ncheck_schedule = dump = require\n')
        saved_path = list(sys.path)
        with patch.dict(sys.modules, {'player_reference': foreign}):
            sys.path.insert(0, shadow.name)
            try:
                loaded = check.load_r2_check(R2)
                self.assertIs(sys.modules['player_reference'], foreign)
            finally:
                sys.path[:] = saved_path
        self.assertEqual(Path(loaded.check_schedule.__code__.co_filename).resolve(), (R2 / 'player_reference.py').resolve())
        with self.assertRaisesRegex(ValueError, 'Capture plus 240'):
            loaded.check_schedule(dict(frames=6000, inputs=[], screenshots=[]))

    def test_producer_identity_must_match_capture(self):
        with tempfile.TemporaryDirectory(prefix='contact identity ') as d:
            tool = Path(d, 'tool.py')
            tool.write_bytes(b'frozen')
            check.producer_identity(dict(producer_sha256={str(tool): sha(b'frozen')}))
            tool.write_bytes(b'edited')
            with self.assertRaisesRegex(ValueError, 'Producer identity changed since capture'):
                check.producer_identity(dict(producer_sha256={str(tool): sha(b'frozen')}))


class Inventory(unittest.TestCase):
    """P2-2: deterministic inventory, string-ordered records, self-excluded, inode counts and symlinks reported."""
    def test_inventory_method(self):
        with tempfile.TemporaryDirectory(prefix='contact inventory ') as d:
            root = Path(d)
            (root / 'a').mkdir()
            (root / 'a' / 'b.txt').write_bytes(b'one')
            (root / 'a-b.txt').write_bytes(b'two')
            os.link(root / 'a-b.txt', root / 'c.txt')
            (root / 'link').symlink_to('a-b.txt')
            (root / 'inventory.json').write_text('{}')
            inv = check.artifact_inventory(root, exclude=('inventory.json',))
            records = ''.join(f'{p}\t{sha(v)}\n' for p, v in (('a-b.txt', b'two'), ('a/b.txt', b'one'), ('c.txt', b'two')))
            self.assertEqual(list(inv['files']), ['a-b.txt', 'a/b.txt', 'c.txt'])
            self.assertEqual(inv['fingerprint'], sha(records.encode()))
            self.assertEqual((inv['regular_files'], inv['distinct_inodes'], inv['multi_linked_files'], inv['symlinks']),
                             (3, 2, 2, {'link': 'a-b.txt'}))
            pathlib_order = ''.join(f'{p.relative_to(root)}\t{sha(p.read_bytes())}\n' for p in sorted(root.rglob('*'))
                                    if p.is_file() and not p.is_symlink() and p.name != 'inventory.json')
            self.assertNotEqual(sha(pathlib_order.encode()), inv['fingerprint'])  # the R1 6decd5f4 ordering hazard


class OutputGuards(unittest.TestCase):
    """P2-3: refusals precede scenario reads, preparation and output creation (synthetic Git and layout only)."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='contact guards ')
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name).resolve()
        self.assertFalse(root.is_relative_to(HERE) or root.is_relative_to(R2.resolve()), 'Run with TMPDIR outside the tool directories')
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull, GIT_CONFIG_NOSYSTEM='1', GIT_TERMINAL_PROMPT='0')
        def git(cwd, *args):
            subprocess.run(['git', '--no-optional-locks', '-C', str(cwd), *args], env=env, check=True, timeout=10,
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.main = root / 'main checkout'
        self.main.mkdir()
        git(self.main, 'init', '--template=')
        git(self.main, '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', '-c', 'core.hooksPath=' + str(root / 'none'),
            'commit', '--allow-empty', '-m', 'fixture')
        self.linked = root / 'linked checkout'
        git(self.main, 'worktree', 'add', '--detach', str(self.linked), 'HEAD')
        self.producer = self.linked / 'tools' / 'soniccd'
        self.producer.mkdir(parents=True)
        self.base = root / 'Downloads'
        for d in ('media/USA', 'media/Japan', 'media/BIOS', 'research', 'native/earlier-r1', 'native/r2', '.work/earlier-tools'):
            (self.base / d).mkdir(parents=True)
        self.scenario = self.base / 'scenario.json'
        self.scenario.write_text('{"frames":1,"inputs":[],"screenshots":[],"continuation_screenshots":[]}')
        self.r2 = producer.load_r2(R2)

    def argv(self, output, protect=('native/earlier-r1', '.work/earlier-tools'), extra=(), cue='media/USA/disc.cue', firmware='media/BIOS'):
        b = self.base
        paths = {'producer-directory': self.producer, 'source': b / 'source', 'inventory': b / 'research' / 'inventory.json',
                 'bizhawk-home': b / 'runtime', 'cue': b / cue, 'firmware-directory': b / firmware, 'scenario': self.scenario,
                 'output': output, 'r2-directory': R2}
        argv = ['contact_reference.py', *[x for k, p in paths.items() for x in ('--' + k, str(p))], '--bios-sha1', '0' * 40, '--kind', 'final']
        for p in protect:
            argv += ['--protect', str(b / p)]
        return argv + list(extra)

    def refuse(self, output, message='Output overlaps protected input', **kwargs):
        r2 = self.r2
        with patch.object(sys, 'argv', self.argv(output, **kwargs)), patch.object(producer, 'load_r2', return_value=r2), \
             patch.object(r2, 'prepare', side_effect=AssertionError('prepare reached')) as prepare, \
             patch.object(Path, 'read_text', side_effect=AssertionError('scenario read reached')), \
             patch.object(Path, 'mkdir', side_effect=AssertionError('output creation reached')):
            with self.assertRaisesRegex(ValueError, message):
                producer.main()
            prepare.assert_not_called()
        self.assertFalse(os.path.lexists(output))

    def admit(self, output, **kwargs):
        r2 = self.r2
        with patch.object(sys, 'argv', self.argv(output, **kwargs)), patch.object(producer, 'load_r2', return_value=r2), \
             patch.object(r2, 'prepare', side_effect=RuntimeError('preflight accepted')) as prepare:
            with self.assertRaisesRegex(RuntimeError, 'preflight accepted'):
                producer.main()
            prepare.assert_called_once()
        self.assertFalse(output.exists())

    def test_repository_tool_and_r2_outputs_refused(self):
        for output in (self.main / 'target' / 'new', self.linked / 'target' / 'new', HERE / 'guard-output-must-not-exist',
                       R2 / 'guard-output-must-not-exist'):
            with self.subTest(output=output):
                self.refuse(output)

    def test_whole_media_root_research_and_earlier_evidence_refused(self):
        b = self.base
        for output in (b / 'media' / 'new', b / 'media' / 'Japan' / 'x', b / 'media' / 'USA' / 'x', b / 'media' / 'BIOS' / 'x',
                       b / 'research' / 'x', b / 'native' / 'earlier-r1' / 'x', b / '.work' / 'earlier-tools' / 'x'):
            with self.subTest(output=output):
                self.refuse(output)

    def test_native_parent_not_protected_and_new_sibling_admitted(self):
        self.admit(self.base / 'native' / 'r2' / 'kill')
        self.admit(self.base / 'native' / 'new-sibling')

    def test_dot_dot_alias_into_protected_root_refused(self):
        self.refuse(self.base / 'native' / 'r2' / '..' / 'earlier-r1' / 'nested')

    def test_missing_protect_root_and_uninferable_media_root_refused(self):
        self.refuse(self.base / 'native' / 'r2' / 'x', 'Protected root does not exist', protect=('native/typo-root',))
        self.refuse(self.base / 'native' / 'r2' / 'x', 'Cannot infer media root', cue='media/disc.cue', firmware='media/BIOS')
        self.refuse(self.base / 'native' / 'r2' / 'x', 'must contain the CUE and firmware',
                    extra=('--media-root', str(self.base / 'research')))

    def test_explicit_media_root_protects_whole_root(self):
        self.refuse(self.base / 'media' / 'Japan' / 'x', extra=('--media-root', str(self.base / 'media')))
        self.admit(self.base / 'native' / 'r2' / 'y', extra=('--media-root', str(self.base / 'media')))

    def test_symlink_alias_of_checkout_and_earlier_root_refused(self):
        alias = self.base / 'alias'
        alias.symlink_to(self.main, target_is_directory=True)
        self.refuse(alias / 'target' / 'new')
        earlier = self.base / 'earlier-alias'
        earlier.symlink_to(self.base / 'native' / 'earlier-r1', target_is_directory=True)
        self.refuse(earlier / 'x')

    def test_existing_output_refused(self):
        output = self.base / 'native' / 'r2'
        with patch.object(sys, 'argv', self.argv(output)), patch.object(producer, 'load_r2', return_value=self.r2), \
             patch.object(self.r2, 'prepare', side_effect=AssertionError('prepare reached')) as prepare:
            with self.assertRaisesRegex(ValueError, 'Output already exists'):
                producer.main()
            prepare.assert_not_called()
        self.assertEqual(sorted(p.name for p in output.iterdir()), [])

    def test_guard_only_admits_without_reading_or_creating(self):
        output = self.base / 'native' / 'r2' / 'guard-probe'
        with patch.object(sys, 'argv', self.argv(output, extra=('--guard-only',))), patch.object(producer, 'load_r2', return_value=self.r2), \
             patch.object(self.r2, 'prepare', side_effect=AssertionError('prepare reached')) as prepare, \
             patch.object(Path, 'read_text', side_effect=AssertionError('scenario read reached')), \
             patch.object(Path, 'mkdir', side_effect=AssertionError('output creation reached')):
            producer.main()
            prepare.assert_not_called()
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
