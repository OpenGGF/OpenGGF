#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R10 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""UpdateHUD ledger tests. Native oracle (comparison only): all 42 retained ending buckets take the same branch family
(timer running, no second rollover), so they check that path's exact per-PC counts. Every other branch is
NATIVE-ABSENT and is covered by synthetic vectors whose expected loop counts are derived here from the source
(repeated subtraction by 100000..1, leading-zero suppression, fixed draw blocks)."""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import hud_cost as hc  # noqa: E402
import vint8_cost as vc  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

ROM_PATH, GPGX, NATIVE = os.environ.get('S3K_ROM'), os.environ.get('GPGX_GIT'), os.environ.get('KOS46_NATIVE_ROOT')


@unittest.skipUnless(ROM_PATH and GPGX and NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class UpdateHud(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.ledger = hc.HudLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.motorola = hc.HudLedger(cls.rom, tv.motorola_cost)

    def run_hud(self, **kw):
        return self.ledger.run(hc.Hud(**kw))

    def test_native_timer_running_path(self):
        n = 0
        for case in tv.CASES:
            hist = collections.defaultdict(dict)
            with open(os.path.join(NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    pc = int(r['pc'], 16)
                    if 0xDD06 <= pc <= 0xDDCC:
                        hist[r['level_frame']][pc] = int(r['count'])
            for lf, native in hist.items():
                cycles, counts, terms, cpu = self.run_hud(update_timer=1, timer=(0, 2, 10, 17))
                self.assertEqual(counts, native, (case[0], lf))
                n += 1
        self.assertEqual(n, 42)

    def test_timer_frame_advances_state(self):
        _, _, _, cpu = self.run_hud(update_timer=1, timer=(0, 2, 10, 17))
        self.assertEqual(cpu.read(self.ledger.addr['timer'] + 3, 'b'), 18)

    def test_score_redraw_digit_loops(self):
        cycles, counts, _, _ = self.run_hud(update_score=1, score=1230)
        self.assertEqual(counts[0xDF9C], (0 + 1) + (0 + 1) + (1 + 1) + (2 + 1) + (3 + 1) + (0 + 1))  # sub.l per digit
        self.assertEqual(counts[0xDFBC], 4)          # first move.l (a3)+,(a6): digits after the leading zeros
        self.assertEqual(counts[0xDFDC], 6)          # addi.l #$40<<16,d0 once per position

    def test_ring_redraw_and_zero_rings(self):
        _, counts, _, _ = self.run_hud(update_rings=1, ring_count=57)
        self.assertEqual(counts[0xDF9C], (0 + 1) + (5 + 1) + (7 + 1))
        self.assertEqual(counts[0xDFBC], 2)
        _, counts, _, _ = self.run_hud(update_rings=-1, ring_count=0)
        self.assertEqual(counts[0xDEC2], 3)          # HUD_DrawZeroRings: three parts from HUD_Zero_Rings
        self.assertEqual(counts.get(0xDFBC, 0), 0)   # zero ring count draws no digit after the zero art

    def test_second_rollover_draws_minute_and_seconds(self):
        cycles, counts, _, cpu = self.run_hud(update_timer=1, timer=(0, 0, 37, 59))
        self.assertEqual(counts[0xE074], (0 + 1) + (3 + 1) + (8 + 1))   # minute 0, seconds 38
        self.assertEqual(counts[0xE090], 3)                            # three digit draw blocks
        self.assertEqual(cpu.read(self.ledger.addr['timer'] + 2, 'b'), 38)

    def test_lives_two_digit_with_blank_tens(self):
        _, counts, _, _ = self.run_hud(update_lives=1, life_count=3)
        self.assertEqual(counts[0xE140], (0 + 1) + (3 + 1))
        self.assertEqual(counts[0xE17E], 8)          # blank tile: 8 x move.l #0,(a6)

    def test_perturbations_change_counts(self):
        base = self.run_hud(update_timer=1, timer=(0, 0, 37, 59))[1]
        for kw in (dict(update_timer=1, timer=(0, 0, 38, 59)), dict(update_timer=1, timer=(0, 0, 37, 58)),
                   dict(update_timer=1, timer=(0, 0, 37, 59), game_paused=1)):
            self.assertNotEqual(self.run_hud(**kw)[1], base, kw)

    def test_open_terms_fail_closed(self):
        for kw in (dict(debug_mode=1), dict(competition_mode=1), dict(update_timer=1, timer=(0, 9, 59, 59))):
            with self.assertRaises(ValueError):
                self.run_hud(**kw)

    def test_gpgx_equals_motorola_here(self):
        for kw in (dict(update_timer=1, timer=(0, 0, 37, 59)), dict(update_score=1, score=987654),
                   dict(update_lives=1, life_count=12)):
            self.assertEqual(self.ledger.run(hc.Hud(**kw))[0], self.motorola.run(hc.Hud(**kw))[0])

    def test_rom_pins(self):
        bad = bytearray(self.rom)
        bad[0xDD1E] ^= 1
        with self.assertRaises(ValueError):
            hc.HudLedger(bytes(bad), tv.motorola_cost)


if __name__ == '__main__':
    unittest.main()
