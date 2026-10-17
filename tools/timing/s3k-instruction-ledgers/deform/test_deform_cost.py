#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R16 (2026-10-17; frozen at R16 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Deformation ledger tests (kos46 research R16).

SOURCE/SYNTHETIC: ROM micro-execution must equal the independent references (deform_reference.py) on every
H_scroll_buffer long, HScroll_table byte and Camera_Y_pos_BG_copy, over the three ROM band tables the ending uses
(Ending_BGDeformArray1 $5B144, Ending_BGDeformArray2 $5B15A, AIZ1_DeformArray $23B258), synthetic band lists (per-line
bands, odd/even heights, a camera above the first band), ApplyDeformation3 line counts, both byte-identical copies, and
random camera/drift state. Hand sums use 68000 instruction times; GPGX and Motorola must agree.
NATIVE (comparison only): AIZ1_Deform's path is input-independent, so all its PCs are compared exactly in the 28
buckets that run it; for ApplyDeformation only the entry and exit PCs are compared (its body depends on
Camera_Y_pos_BG_copy, which the capture does not hold); the AIZ1_ApplyDeformWater dry branch is a TEST-ORACLE gate.
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
import deform_cost as dc  # noqa: E402
import deform_reference as ref  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

TABLES = {'Ending_BGDeformArray1': 0x5B144, 'Ending_BGDeformArray2': 0x5B15A, 'AIZ1_DeformArray': 0x23B258}


def words(blob):
    return b''.join((w & 0xFFFF).to_bytes(2, 'big') for w in blob)


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class Deform(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.vc.TOOL)
        import gpgx_cycles
        cls.table = gpgx_cycles.load_table(tv.GPGX)
        cls.g = dc.DeformLedger(cls.rom, dc.costs(cls.rom, cls.table))
        cls.m = dc.DeformLedger(cls.rom, dc.costs(cls.rom))

    def ref_apply(self, rom, table, state, scroll_off, d1=0xDF):
        tw = lambda i: (rom[table + 2 * i] << 8) | rom[table + 2 * i + 1]  # noqa: E731
        hs = state.hscroll_table

        def sw(i):                     # private model RAM: bytes outside the supplied HScroll_table read 0
            a = scroll_off + 2 * i
            return (hs[a] << 8) | hs[a + 1] if 0 <= a < len(hs) - 1 else 0
        return ref.apply_deformation(tw, sw, state.camera_bg_y, state.camera_x, d1)

    def rand_state(self, rng, bg_y):
        return dc.DeformState(camera_x=rng.randrange(0x10000), camera_y=rng.randrange(0x800), camera_bg_y=bg_y,
                              water_level=0x1000, hscroll_table=bytes(rng.randrange(256) for _ in range(0x80)))

    def test_rom_tables_both_copies(self):
        rng = random.Random(16)
        for name, table in TABLES.items():
            span = sum(w & 0x7FFF for w in self.table_words(table)[:-1])
            for _ in range(40):
                s = self.rand_state(rng, rng.randrange(-0x40, span + 0x40))
                for copy in ('S3', 'SK'):
                    r = self.g.apply(s, table, 0xA800 + 8, copy=copy)
                    self.assertEqual(list(r.hscroll), self.ref_apply(self.rom, table, s, 8), (name, copy, s.camera_bg_y))
                    self.assertEqual(r.cycles, self.m.apply(s, table, 0xA800 + 8, copy=copy).cycles)

    def table_words(self, table):
        out = []
        while True:
            w = (self.rom[table + 2 * len(out)] << 8) | self.rom[table + 2 * len(out) + 1]
            out.append(w)
            if w & 0x7FFF >= 0x7000 or len(out) > 40:
                return out

    def test_synthetic_bands_and_line_counts(self):
        rng = random.Random(61)
        for _ in range(120):
            bands = [rng.choice((1, 2, 3, 7, 8, 13, 0x8000 | rng.randrange(1, 40), rng.randrange(1, 60)))
                     for _ in range(rng.randrange(1, 12))] + [0x7FFF]
            rom = self.rom + words(bands) + bytes(8)
            base = len(self.rom)
            led = dc.DeformLedger(rom, dc.costs(rom, self.table))
            s = self.rand_state(rng, rng.randrange(-0x30, 0x200))
            d1 = rng.choice((0xDF, 0x0F, 0x4F, 0))
            entry = 'ApplyDeformation' if d1 == 0xDF else 'ApplyDeformation3'
            r = led.apply(s, base, 0xA800, entry=entry, d1=None if entry == 'ApplyDeformation' else d1)
            self.assertEqual(list(r.hscroll), self.ref_apply(rom, base, s, 0, d1), (bands, s.camera_bg_y, d1))

    def test_aiz1_deform_reference_and_constant_cost(self):
        rng = random.Random(3)
        costs = set()
        for _ in range(200):
            s = dc.DeformState(rng.randrange(0x10000), rng.randrange(0x10000), 0,
                               hscroll_table=bytes(rng.randrange(256) for _ in range(0x80)))
            r = self.g.aiz1_deform(s)
            bg, table = ref.aiz1_deform(s.camera_x, s.camera_y, s.hscroll_table)
            self.assertEqual((r.after.camera_bg_y, r.after.hscroll_table), (bg, table))
            costs.add(r.cycles)
            self.assertEqual(r.cycles, self.m.aiz1_deform(s).cycles)
        self.assertEqual(len(costs), 1)

    def test_hand_sums(self):
        # AIZ1_Deform, 68000 times instruction by instruction
        aiz = [12, 8, 12, 12, 8, 4, 4, 18, 4, 8, 4, 14, 8, 8, 4, 8, 4, 8, 4, 12, 12, 12, 4, 8, 4, 12, 12, 4, 8, 4, 12,
               8, 4, 4, 8, 4, 16, 32, 10, 4] + [8, 4, 8, 4, 8] * 6 + [10] * 5 + [14] + [8, 4, 14, 4] + \
            [8, 4, 8, 4] * 13 + [10] * 12 + [14] + [16]
        self.assertEqual(self.g.aiz1_deform(dc.DeformState()).cycles, sum(aiz))
        # one $7FFF band at BG y 0, 224 lines: entry 8+8+12+12; band word 8, smi false 4, bpl 10, sub 4, bmi 10,
        # tst.b 4, beq 10, neg 4, move 4, sub 4, bcc fall 8, move 4, addq 4, neg 4, swap 4, subq 4, tst.b 4, beq 10,
        # move.w (a5)+ 8, neg 4, lsr.w #1 8, bcc fall 8 (223 is odd), 112 x (move.l 12 + move.l 12), 111 dbf 10,
        # expired 14, tst.w 4, bmi 10, rts 16
        rom = self.rom + words([0x7FFF]) + bytes(8)
        led = dc.DeformLedger(rom, dc.costs(rom, self.table))
        hand = 8 + 8 + 12 + 12 + 8 + 4 + 10 + 4 + 10 + 4 + 10 + 4 + 4 + 4 + 8 + 4 + 4 + 4 + 4 + 4 + 4 + 10 + 8 + 4 + 8 \
            + 8 + 112 * 24 + 111 * 10 + 14 + 4 + 10 + 16
        self.assertEqual(led.apply(dc.DeformState(), len(self.rom), 0xA800).cycles, hand)

    def test_aiz1_water_dry_split_and_refusal(self):
        rw = lambda a: (self.rom[a] << 8) | self.rom[a + 1]  # noqa: E731
        rng = random.Random(1016)
        paths = collections.Counter()
        for _ in range(150):
            cy = rng.randrange(0, 0x800)
            wl = (cy + rng.choice((rng.randrange(1, 0xE0), rng.randrange(0xE0, 0x400)))) & 0xFFFF
            s = dc.DeformState(camera_x=rng.randrange(0x10000), camera_y=cy,
                               camera_bg_y=rng.randrange(-0x20, 0x380) & 0xFFFF, water_level=wl,
                               level_frame_counter=rng.randrange(0x10000),
                               hscroll_table=bytes(rng.randrange(256) for _ in range(0x80)))
            r = self.g.aiz1_apply_water(s)
            hs = s.hscroll_table

            def sw(i):
                a = 8 + 2 * i
                return (hs[a] << 8) | hs[a + 1] if 0 <= a < len(hs) - 1 else 0
            want = ref.aiz1_water(rw, lambda i: rw(dc.AIZ1_ARRAY + 2 * i), sw, s.camera_x, cy, s.camera_bg_y, wl,
                                  s.level_frame_counter, dc.FG_DELTA, dc.BG_DELTA)
            self.assertEqual(list(r.hscroll), want, (hex(wl), hex(cy)))
            self.assertEqual(r.cycles, self.m.aiz1_apply_water(s).cycles)
            paths['split' if dc.S3_FGBG in r.counts else 'dry'] += 1
        self.assertGreater(paths['split'], 30)
        self.assertGreater(paths['dry'], 30)
        for wl in (0x100, 0x0F0, 0x000):                          # water line at/above the screen top: refused
            with self.assertRaises(dc.OpenPath):
                self.g.aiz1_apply_water(dc.DeformState(camera_y=0x100, water_level=wl))
        with self.assertRaises(ValueError):
            self.g.apply(dc.DeformState(), 0x5B144, 0xA800, entry='ApplyDeformation3')   # caller must own d1

    def test_mutations(self):
        bad = bytearray(self.rom); bad[0x239EC9] ^= 0x01                        # bcc.s displacement in the S3 copy
        with self.assertRaises(ValueError):
            dc.DeformLedger(bytes(bad), dc.costs(bytes(bad)))                    # copies no longer identical
        flip = bytearray(self.rom)
        for base in (dc.SK_APPLY, dc.S3_APPLY):
            flip[base + 0x3C] = 0x67                                             # bcc.s -> beq.s at loc_..ec8
        s = dc.DeformState(camera_x=5, camera_bg_y=0x10, hscroll_table=bytes(range(0x80)))
        r = dc.DeformLedger(bytes(flip), dc.costs(bytes(flip))).apply(s, 0x5B144, 0xA808)
        self.assertNotEqual(list(r.hscroll), self.ref_apply(self.rom, 0x5B144, s, 8))
        pin = bytearray(self.rom); pin[0x23B0B9] ^= 2
        with self.assertRaises(ValueError):
            dc.DeformLedger(bytes(pin), dc.costs(bytes(pin)))

    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_input_independent_pcs(self):
        aiz = self.g.aiz1_deform(dc.DeformState()).counts
        dry = self.g.aiz1_apply_water(dc.DeformState(camera_y=0x100))      # TEST-ORACLE gate: dry branch observed
        entry_exit = {dc.S3_APPLY + o for o in (0, 4, 8, 0xC, 0x8A)} | {dc.SK_APPLY + o for o in (0, 4, 8, 0xC, 0x8A)}
        water_pcs = {pc for pc in dry.counts if dc.AIZ1_WATER <= pc < dc.AIZ1_WATER + 0x1A}
        seen = collections.Counter()
        for case in tv.CASES:
            hist = collections.defaultdict(dict)
            with open(os.path.join(tv.NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    pc = int(r['pc'], 16)
                    if pc in aiz or pc in entry_exit or pc in water_pcs:
                        hist[r['level_frame']][pc] = int(r['count'])
            for lf, counts in hist.items():
                a = {pc: k for pc, k in counts.items() if pc in aiz}
                if a:
                    self.assertEqual(a, aiz, (case[0], lf))
                    self.assertEqual({pc: k for pc, k in counts.items() if pc in water_pcs},
                                     {pc: dry.counts[pc] for pc in water_pcs})
                    seen['aiz1'] += 1
                for base in (dc.S3_APPLY, dc.SK_APPLY):
                    e = {pc: k for pc, k in counts.items() if base <= pc < base + dc.APPLY_LEN}
                    if e:
                        self.assertEqual(e, {pc: 1 for pc in entry_exit if base <= pc < base + dc.APPLY_LEN}, (case[0], lf))
                        seen['S3' if base == dc.S3_APPLY else 'SK'] += 1
        self.assertEqual(dict(seen), {'aiz1': 28, 'S3': 28, 'SK': 14})


if __name__ == '__main__':
    unittest.main()
