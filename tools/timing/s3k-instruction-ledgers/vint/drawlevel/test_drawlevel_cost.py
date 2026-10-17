#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""VInt_DrawLevel ledger tests.

COVERED (independent retained native histograms, comparison only): 40 ending buckets with an empty Plane_buffer and
one bucket in each v7c capture (sonic-chaos lf 3714, sonic-none lf 3303) that consumed four descriptors (two rows, two
columns, 144 data longwords). A per-PC histogram fixes only (rows, columns, total longwords); the instruction count
and cycles are invariant to how the longwords split between descriptors, which test_split_invariance checks.
UNCOVERED (synthetic source vectors, NATIVE-ABSENT): Plane_buffer_2 (Slots bonus stage only), wrap-split producers,
single descriptors, bounds. Intended perturbations must break the native match.
"""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import drawlevel_cost as dl  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

ROM_PATH, GPGX, NATIVE = os.environ.get('S3K_ROM'), os.environ.get('GPGX_GIT'), os.environ.get('KOS46_NATIVE_ROOT')
D = dl.Desc
FOUR = (D(0xC000, False, 21), D(0xC080, False, 21), D(0xE000, True, 15), D(0xE002, True, 15))  # 2 rows + 2 cols, 72


def native_buckets():
    out = []
    for case in tv.CASES:
        hist = collections.defaultdict(dict)
        with open(os.path.join(NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
            for r in csv.DictReader(f):
                pc = int(r['pc'], 16)
                if dl.RANGE[0] <= pc < dl.RANGE[1]:
                    hist[int(r['level_frame'])][pc] = int(r['count'])
        out += [(case[0], lf, c) for lf, c in sorted(hist.items())]
    return out


@unittest.skipUnless(ROM_PATH and GPGX and NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class DrawLevel(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.ledger = dl.DrawLevelLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.motorola = dl.DrawLevelLedger(cls.rom, tv.motorola_cost)
        cls.native = native_buckets()

    def test_native_buckets(self):
        empty = self.ledger.run(dl.Planes())[1]
        four = self.ledger.run(dl.Planes(FOUR))[1]
        kinds = collections.Counter()
        for case, lf, native in self.native:
            if native == empty:
                kinds['empty'] += 1
            else:
                self.assertEqual(native, four, (case, lf))
                kinds['four'] += 1
        self.assertEqual(kinds, {'empty': 40, 'four': 2})

    def test_split_invariance(self):
        ref = self.ledger.run(dl.Planes(FOUR))
        for split in ((20, 22, 16, 14), (32, 10, 16, 14), (26, 30, 8, 8), (8, 32, 16, 16)):
            self.assertEqual(sum(split), 72, split)
            p = dl.Planes((D(0xC000, False, split[0]), D(0xC080, False, split[1]), D(0xE000, True, split[2]),
                           D(0xE002, True, split[3])))
            got = self.ledger.run(p)
            self.assertEqual((got[0], got[1]), (ref[0], ref[1]), split)

    def test_perturbations_break_the_native_match(self):
        four = [c for _, _, c in self.native if c.get(0x4E770)][0]
        for p in ((D(0xC000, False, 21), D(0xC080, False, 21), D(0xE000, True, 15), D(0xE002, True, 14)),   # 71
                  (D(0xC000, False, 21), D(0xC080, False, 21), D(0xE000, True, 15), D(0xE002, True, 16)),   # 73
                  (D(0xC000, False, 21), D(0xC080, True, 15), D(0xE000, True, 15), D(0xE002, True, 15),
                   D(0xC100, False, 6)),                                                                    # 5 descs
                  (D(0xC000, False, 21), D(0xC080, True, 16), D(0xE000, True, 16), D(0xE002, True, 13)),    # 1 row
                  (D(0xC000, False, 32), D(0xC080, False, 25), D(0xE000, True, 15))):                       # 3 descs
            self.assertNotEqual(self.ledger.run(dl.Planes(p))[1], four, p)
        self.assertNotEqual(self.ledger.run(dl.Planes(FOUR, ()))[1], four)                     # list 2 present

    def test_closed_form_and_motorola(self):
        vectors = [dl.Planes(), dl.Planes(FOUR), dl.Planes((D(0xC000, False, 1),)), dl.Planes((D(0xE07E, True, 16),)),
                   dl.Planes((), ()), dl.Planes((D(0xC000, False, 32),), (D(0xE000, True, 1), D(0xE002, True, 15))),
                   dl.Planes(tuple(D(0xC000 + 0x100 * k, k % 2 == 1, 7) for k in range(8)))]
        for p in vectors:
            c = self.ledger.run(p)[0]
            self.assertEqual(c, dl.closed_form(p), p)
            self.assertEqual(c, self.motorola.run(p)[0], p)

    def test_loop_counts_follow_the_buffer(self):
        c, counts, cpu = self.ledger.run(dl.Planes((D(0xC000, False, 5),), (D(0xE000, True, 3),)))
        self.assertEqual(counts[0x4E770], 2)                      # clr.w (a0)+: one per descriptor
        self.assertEqual(counts[0x4E776], 1)                      # row: $8F02 increment
        self.assertEqual(counts[0x4E780], 1)                      # column: $8F80 increment
        self.assertEqual(counts[0x4E7BA], 2 * 5 + 2 * 3)          # move.l (a0)+,(a6)
        self.assertEqual(counts[0x4E76A], 1)                      # movea.l d0,a0: second list entered
        self.assertEqual(counts[0x4E79E], 2)                      # $8F02 restored after each list
        self.assertEqual(cpu.vdp_writes, 2 * (1 + 2) + 2 * (5 + 3) + 2)

    def test_wrap_split_row_is_two_descriptors(self):
        # Setup_TileRowDraw loc_4EBB2: a $15-block row starting at block $1A writes 6 then $F blocks
        whole = self.ledger.run(dl.Planes((D(0xC034, False, 0x15),)))[0]
        split = self.ledger.run(dl.Planes((D(0xC034, False, 6), D(0xC000, False, 0x0F))))[0]
        self.assertEqual(split - whole, 312)          # one extra descriptor header, same longwords

    def test_producer_bounds_fail_closed(self):
        for bad in ((D(0xC000, False, 0),), (D(0xC000, False, 33),), (D(0xE000, True, 17),), (D(0, False, 4),),
                    tuple(D(0xC000, False, 32) for _ in range(5))):
            with self.assertRaises(ValueError):
                self.ledger.run(dl.Planes(bad))

    def test_rom_pins(self):
        bad = bytearray(self.rom)
        bad[0x4E7BC] ^= 1
        with self.assertRaises(ValueError):
            dl.DrawLevelLedger(bytes(bad), tv.motorola_cost)


if __name__ == '__main__':
    unittest.main()
