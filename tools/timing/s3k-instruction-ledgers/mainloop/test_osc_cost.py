#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""OscillateNumDo ledger tests (kos46 research R14).

SYNTHETIC: random oscillation states checked against an independent step written from OscillationManager.update's
rule (engine owner) and the ROM Osc_Data2 table; hand sums; gates; mutations.
NATIVE STRUCTURE ONLY: the retained buckets run OscillateNumDo once per frame with a 16-iteration loop. The
oscillation RAM is not in the capture, so only state-independent PCs (gates, head, loop control, store) are compared;
which oscillators rise or turn is not claimed.
"""
import collections
import csv
import os
import random
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'vint'))
import osc_cost as oc  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

STATE_FREE = (0x773C, 0x7740, 0x7742, 0x7748, 0x774A, 0x774E, 0x7754, 0x7756, 0x7758, 0x775A, 0x775C, 0x775E,
              0x7786, 0x7788, 0x778C, 0x7790)
ENGINE_SPEEDS = (2, 2, 2, 2, 4, 8, 8, 4, 2, 2, 2, 3, 5, 7, 2, 2)                 # OscillationManager S2_SPEEDS
ENGINE_LIMITS = (0x10, 0x18, 0x20, 0x30, 0x20, 0x08, 0x40, 0x40, 0x38, 0x38, 0x20, 0x30, 0x50, 0x70, 0x40, 0x40)


def reference_step(control, values, deltas, speeds, limits):
    values, deltas = list(values), list(deltas)
    for i in range(16):
        bit = 15 - i
        if not control >> bit & 1:
            deltas[i] = (deltas[i] + speeds[i]) & 0xFFFF
            values[i] = (values[i] + deltas[i]) & 0xFFFF
            if (values[i] >> 8) >= limits[i]:
                control |= 1 << bit
        else:
            deltas[i] = (deltas[i] - speeds[i]) & 0xFFFF
            values[i] = (values[i] + deltas[i]) & 0xFFFF
            if (values[i] >> 8) < limits[i]:
                control &= ~(1 << bit) & 0xFFFF
    return control, tuple(values), tuple(deltas)


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class Oscillate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.vc.TOOL)
        import gpgx_cycles
        cls.g = oc.OscLedger(cls.rom, oc.gpgx_cost(cls.rom, gpgx_cycles.load_table(tv.GPGX)))
        cls.m = oc.OscLedger(cls.rom, oc.motorola_cost)

    def test_rom_table_equals_engine_tables(self):
        self.assertEqual((tuple(self.g.speed), tuple(self.g.limit)), (ENGINE_SPEEDS, ENGINE_LIMITS))

    def test_state_step_matches_independent_reference(self):
        rng = random.Random(46)
        for _ in range(400):
            s = oc.Osc(rng.randrange(0x10000), tuple(rng.randrange(0x10000) for _ in range(16)),
                       tuple(rng.randrange(0x10000) for _ in range(16)))
            r = self.g.run(s)
            want = reference_step(s.control, s.values, s.deltas, self.g.speed, self.g.limit)
            self.assertEqual((r.after.control, r.after.values, r.after.deltas), want)
            self.assertEqual(r.cycles, self.m.run(s).cycles)
            up = r.counts.get(0x7760, 0)
            self.assertEqual(up + r.counts.get(0x7774, 0), 16)
            self.assertEqual(up, 16 - bin(s.control & 0xFFFF).count('1'))
            turned = r.counts.get(0x7770, 0) + r.counts.get(0x7784, 0)
            self.assertEqual(turned, bin((s.control ^ r.after.control) & 0xFFFF).count('1'))

    def test_hand_sum_and_gates(self):
        # all rising, none turning: head 12+8+16+8 + lea 8 + lea 12 + move.w 8 + moveq 4; per oscillator
        # move.w 8, move.w 8, btst 6, bne fall 8, move.w d16 12, add 4, move.w d16 12, add.w Dn,(An) 12, cmp.b 8,
        # bhi taken 10, addq 8 (An), dbf; 15 x 10 + 14; store 12; rts 16
        s = oc.Osc(0, (0,) * 16, (0,) * 16)
        r = self.g.run(s)
        self.assertEqual(r.counts.get(0x7770, 0), 0)
        per = 8 + 8 + 6 + 8 + 12 + 4 + 12 + 12 + 8 + 10 + 8
        self.assertEqual(r.cycles, 12 + 8 + 16 + 8 + 8 + 12 + 8 + 4 + 16 * per + 15 * 10 + 14 + 12 + 16)
        self.assertEqual(self.g.run(oc.Osc(player1_routine=6)).cycles, 12 + 8 + 16 + 10 + 16)
        self.assertTrue(self.g.run(oc.Osc(player1_routine=6, competition_mode=True)).ran)

    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_state_free_pcs(self):
        want = dict(self.g.run(oc.Osc()).counts)
        n = 0
        for case in tv.CASES:
            hist = collections.defaultdict(dict)
            with open(os.path.join(tv.NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    pc = int(r['pc'], 16)
                    if pc in STATE_FREE:
                        hist[r['level_frame']][pc] = int(r['count'])
            for lf, counts in hist.items():
                self.assertEqual(counts, {pc: want[pc] for pc in STATE_FREE if pc in want}, (case[0], lf))
                n += 1
        self.assertEqual(n, 42)

    def test_mutations(self):
        bad = bytearray(self.rom); bad[0x776E] = 0x64                          # bhi -> bcc
        with self.assertRaises(ValueError):
            oc.OscLedger(bytes(bad), oc.motorola_cost)
        lim = bytearray(self.rom); lim[oc.OSC_DATA + 3] ^= 0x40               # oscillator 0 limit
        s = oc.Osc(0, (0x1000,) + (0,) * 15, (0,) * 16)
        self.assertNotEqual(oc.OscLedger(bytes(lim), oc.motorola_cost).run(s).after.control,
                            self.m.run(s).after.control)


if __name__ == '__main__':
    unittest.main()
