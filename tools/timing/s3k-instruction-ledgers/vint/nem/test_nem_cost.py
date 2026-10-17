#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Process_Nem_Queue_2 ledger tests.

COVERED (retained native histograms, comparison only): all 42 ending buckets take the empty path.
UNCOVERED (synthetic source vectors, NATIVE-ABSENT): every nonempty occupancy. The ending itself can reach it:
Ending code queues PLC_SKCredits / PLC_CreditsKnuxPose with Load_PLC_Raw (sonic3k.asm 121546, 121894-121924), and
105 Load_PLC* call sites exist in level code, so absence from the samples is not global absence. Nonempty results must
carry the open Nem_PCD_NewRow term.
"""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import nem_cost as nc  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

ROM_PATH, GPGX, NATIVE = os.environ.get('S3K_ROM'), os.environ.get('GPGX_GIT'), os.environ.get('KOS46_NATIVE_ROOT')
Q = tuple((0x100000 + 0x40 * k, 0x2000 + 0x20 * k) for k in range(16))


@unittest.skipUnless(ROM_PATH and GPGX and NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class NemQueue(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.ledger = nc.NemLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.motorola = nc.NemLedger(cls.rom, tv.motorola_cost)

    def test_native_empty_path(self):
        cycles, counts, terms, _ = self.ledger.run(nc.Nem())
        self.assertEqual((cycles, terms), (12 + 10 + 16, {}))     # tst.w abs.w, beq.s taken, rts
        n = 0
        for case in tv.CASES:
            hist = collections.defaultdict(dict)
            with open(os.path.join(NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    pc = int(r['pc'], 16)
                    if any(a <= pc < b for a, b in nc.RANGES):
                        hist[r['level_frame']][pc] = int(r['count'])
            for lf, native in hist.items():
                self.assertEqual(native, counts, (case[0], lf))
                n += 1
        self.assertEqual(n, 42)

    def test_budget_and_save_path(self):
        cycles, counts, terms, cpu = self.ledger.run(nc.Nem(5, Q[:2]))
        self.assertEqual(terms, {'Nem_PCD_NewRow': 3})
        self.assertEqual(counts[0x1852], 3)                      # bne.s Loop: taken twice, falls once
        self.assertEqual(counts[0x1854], 1)                      # decoder state saved
        self.assertNotIn(0x1872, counts)
        self.assertEqual(cpu.read(self.ledger.patterns_left, 'w'), 2)
        self.assertEqual(cpu.read(self.ledger.queue + 4, 'w'), Q[0][1] + 3 * 0x20)

    def test_last_patterns_shift_the_queue_with_the_shipped_16th_slot(self):
        for left in (1, 2, 3):
            _, counts, terms, cpu = self.ledger.run(nc.Nem(left, Q))
            self.assertEqual(terms, {'Nem_PCD_NewRow': left})
            self.assertEqual(counts[0x1878], 0x15 + 1)            # 22 longwords moved
            self.assertNotIn(0x1854, counts)
            self.assertEqual(cpu.read(self.ledger.queue, 'l'), Q[1][0])
            # FixBugs = 0: slot 15's last word is not popped, so it still holds the old destination
            self.assertEqual(cpu.read(self.ledger.queue + 6 * 15 + 4, 'w'), Q[15][1])

    def test_perturbations_and_motorola(self):
        seen = set()
        for left in (1, 2, 3, 4, 0x7FFF):
            r = self.ledger.run(nc.Nem(left, Q[:1]))
            self.assertEqual(r[0], self.motorola.run(nc.Nem(left, Q[:1]))[0])
            seen.add(tuple(sorted(r[1].items())))
        self.assertEqual(len(seen), 4)                           # 4 and $7FFF share the 3-pattern save path
        self.assertEqual(self.ledger.run(nc.Nem(4, Q[:1]))[1], self.ledger.run(nc.Nem(0x7FFF, Q[:1]))[1])

    def test_unreachable_or_out_of_range_states_fail_closed(self):
        for bad in (nc.Nem(-1, Q[:1]), nc.Nem(0x8000, Q[:1]), nc.Nem(3, ()), nc.Nem(3, Q + Q[:1])):
            with self.assertRaises(ValueError):
                self.ledger.run(bad)

    def test_rom_pins(self):
        bad = bytearray(self.rom)
        bad[0x1876] ^= 1
        with self.assertRaises(ValueError):
            nc.NemLedger(bytes(bad), tv.motorola_cost)


if __name__ == '__main__':
    unittest.main()
