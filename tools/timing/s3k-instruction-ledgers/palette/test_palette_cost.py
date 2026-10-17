#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R17 (2026-10-17; frozen at R17 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Palette fade ledger tests (kos46 research R17).

SOURCE/SYNTHETIC: ROM micro-execution equals the independent per-colour reference (palette_reference.py) on both
palettes, Palette_fade_timer and Pal_fade_delay2 for every routine, over random palettes (colour-only and arbitrary
words), ranges, Water_flag, zone/act carve-outs and timers, and over multi-step fade sequences. Hand sums use 68000
instruction times; GPGX and Motorola must agree. Mutations, refusals and old-red form checks.
NATIVE (comparison only): Pal_ToBlack's loop-control PCs and Pal_DecColor entries in the 18 buckets that run it,
with the range from the caller's source constant (move.w #$40-1,(Palette_fade_info).w, 044fa46 sonic3k.asm
121001/121126; caller owned by the ending-events worker); Animate_Palette's timer < 0 return in all 42 buckets is a
TEST-ORACLE gate (observed path). Colour branches depend on palette RAM, which the capture does not hold.
"""
import collections
import csv
import os
import random
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'deform'))
sys.path.insert(0, os.path.join(HERE, '..', 'vint'))
import palette_cost as pc  # noqa: E402
import palette_reference as ref  # noqa: E402
import palette_forms as pf  # noqa: E402
import deform_forms as df  # noqa: E402
import deform_cost as dc  # noqa: E402
import mini68k  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

ROUTINES = ('Pal_ToBlack', 'Pal_FromBlack', 'Pal_ToWhite', 'Pal_FromWhite', 'Pal_FillBlack', 'Pal_FillWhite',
            'Animate_Palette')


def colour(rng, junk=False):
    if junk:
        return rng.randrange(0x10000)
    return rng.randrange(8) << 9 | rng.randrange(8) << 5 | rng.randrange(8) << 1


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class Palette(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.vc.TOOL)
        import gpgx_cycles
        cls.g = pc.PaletteLedger(cls.rom, dc.costs(cls.rom, gpgx_cycles.load_table(tv.GPGX)))
        cls.m = pc.PaletteLedger(cls.rom, dc.costs(cls.rom))

    def state(self, rng, junk=False):
        off = 2 * rng.randrange(64)
        count = rng.randrange(64 - off // 2)
        return pc.PalState(*(tuple(colour(rng, junk) for _ in range(64)) for _ in range(4)), off, count,
                           rng.random() < 0.5, rng.choice((0, 0x0D01, 0x1701, 0x0C00)),
                           rng.choice((-1, -0x100, 1, 7, 0x7FFF)), rng.randrange(-3, 4))

    def check(self, name, s):
        r = self.g.run(name, s)
        n, w, timer, delay2 = ref.step(name, s)
        self.assertEqual((r.after.normal, r.after.water, r.after.fade_timer, r.after.delay2), (n, w, timer, delay2), name)
        self.assertEqual(r.cycles, self.m.run(name, s).cycles, name)
        return r

    def test_every_routine_against_reference(self):
        rng = random.Random(17)
        for junk in (False, True):
            for _ in range(60):
                s = self.state(rng, junk)
                for name in ROUTINES:
                    self.check(name, s)

    def test_multi_step_fades(self):
        rng = random.Random(171)
        # one component per call: up to 7 + 7 + 7 steps to black (Pal_FadeToBlack runs $15 + 1 = 22 iterations)
        for name, steps in (('Pal_ToBlack', 21), ('Pal_FromBlack', 22), ('Pal_ToWhite', 22), ('Pal_FromWhite', 22)):
            s = self.state(rng)
            s.fade_offset, s.fade_count, s.water_flag = 0, 0x3F, True
            for _ in range(steps):
                r = self.check(name, s)
                a = r.after
                s = pc.PalState(a.normal, a.water, s.target, s.target_water, s.fade_offset, s.fade_count,
                                s.water_flag, s.zone_act, a.fade_timer, a.delay2)
            if name == 'Pal_ToBlack':
                self.assertEqual(s.normal, (0,) * 64)

    def test_hand_sums(self):
        # Pal_DecColor paths: zero 8+10+8+16; red 8+8+4+8+8+12+16; green +10/+4+8+8+16 subi; blue
        zero = 8 + 10 + 8 + 16
        red = 8 + 8 + 4 + 8 + 8 + 12 + 16
        green = 8 + 8 + 4 + 8 + 10 + 4 + 8 + 8 + 16 + 16
        blue = 8 + 8 + 4 + 8 + 10 + 4 + 8 + 10 + 4 + 8 + 8 + 16 + 16
        head = 4 + 8 + 12 + 8 + 12                                 # moveq, lea, move.b abs, adda.w, move.b abs
        def black(per):
            return 2 * (head + 64 * (18 + per) + 63 * 10 + 14) + 16
        for c, per in ((0, zero), (0x002, red), (0x020, green), (0x200, blue)):
            s = pc.PalState(normal=(c,) * 64, water=(c,) * 64, fade_count=0x3F)
            self.assertEqual(self.g.run('Pal_ToBlack', s).cycles, black(per), hex(c))
        # Animate_Palette with a negative timer: tst.w 12, bmi taken 10, rts 16
        self.assertEqual(self.g.run('Animate_Palette', pc.PalState(fade_timer=-1)).cycles, 12 + 10 + 16)

    def test_blocking_fade_loops(self):
        rng = random.Random(1717)
        first = {'Pal_FadeFromBlack': 'Pal_FillBlack', 'Pal_FadeFromWhite': 'Pal_FillWhite'}
        step = {'Pal_FadeFromBlack': 'Pal_FromBlack', 'Pal_FadeToBlack': 'Pal_ToBlack',
                'Pal_FadeFromWhite': 'Pal_FromWhite', 'Pal_FadeToWhite': 'Pal_ToWhite'}
        for name in step:
            s = self.state(rng)
            s.fade_offset, s.fade_count = 0, 0x3F                       # the loop sets Palette_fade_info = $3F
            waits = [rng.randrange(1, 400) for _ in range(22)]
            r = self.g.run_fade(name, s, waits)
            self.assertEqual(r.terms, {'VInt_12': 22, 'Process_Nem_Queue_Init': 22})
            # reference composition and per-step costs from single-step runs on the same states
            cur, steps = s, 0
            if name in first:
                one = self.g.run(first[name], cur)
                steps += one.cycles
                cur = self.next_state(cur, one.after)
                pre = 16 + 18 + 8                                       # move.w #,abs.w; jsr/bsr; move.w #$15,d4
            else:
                pre = 16 + 8
            for _ in range(22):
                one = self.check(step[name], cur)
                steps += one.cycles
                cur = self.next_state(cur, one.after)
            self.assertEqual((r.after.normal, r.after.water, r.after.delay2), (cur.normal, cur.water, cur.delay2))
            # per iteration: move.b #,abs.w 16, bsr.w 18, Wait_VSync (move #,sr 16, N tst.b 12, N-1 bne taken 10,
            # bne fall 8, rts 16), bsr.s 18, bsr.w 18 (Nem init entered elsewhere), dbf; then rts 16
            loop = sum(16 + 18 + 16 + 12 * n + 10 * (n - 1) + 8 + 16 + 18 + 18 for n in waits) + 21 * 10 + 14 + 16
            self.assertEqual(r.cycles, pre + steps + loop, name)
        with self.assertRaises(ValueError):
            self.g.run_fade('Pal_FadeToBlack', pc.PalState(), [0] * 22)

    def next_state(self, s, a):
        return pc.PalState(a.normal, a.water, s.target, s.target_water, s.fade_offset, s.fade_count, s.water_flag,
                           s.zone_act, a.fade_timer, a.delay2)

    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_range_dependent_pcs(self):
        zero = self.g.run('Pal_ToBlack', pc.PalState(fade_count=0x3F)).counts
        control = {p for p in zero if 0x3C04 <= p < 0x3C32} | {0x3C32, 0x3C34}
        anim = self.g.run('Animate_Palette', pc.PalState(fade_timer=-1)).counts
        seen = collections.Counter()
        for case in tv.CASES:
            hist = collections.defaultdict(dict)
            with open(os.path.join(tv.NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    p = int(r['pc'], 16)
                    if p in control or p in anim:
                        hist[r['level_frame']][p] = int(r['count'])
            for lf, counts in hist.items():
                self.assertEqual({p: k for p, k in counts.items() if p in anim}, anim, (case[0], lf))
                seen['anim'] += 1
                c = {p: k for p, k in counts.items() if p in control}
                if c:
                    self.assertEqual(c, {p: zero[p] for p in control}, (case[0], lf))
                    seen['toblack'] += 1
        self.assertEqual(dict(seen), {'anim': 42, 'toblack': 18})

    def test_mutations_refusals_and_old_red(self):
        s = pc.PalState(normal=(0x022,) * 64, water=(0x022,) * 64)          # red 2: masked away by #$C
        bad = bytearray(self.rom); bad[0x3C3B] = 0x0C                         # andi.w #$E -> #$C (red mask)
        r = pc.PaletteLedger(bytes(bad), dc.costs(bytes(bad))).run('Pal_ToBlack', s)
        self.assertNotEqual(r.after.normal, ref.step('Pal_ToBlack', s)[0])
        hi = bytearray(self.rom); hi[0x3B72] = 0x63                            # second bhi.s -> bls.s in Pal_AddColor
        t = pc.PalState(normal=(0,) * 64, target=(0x0EE,) * 64)                  # blue step too big, green step fits
        self.assertNotEqual(pc.PaletteLedger(bytes(hi), dc.costs(bytes(hi))).run('Pal_FromBlack', t).after.normal,
                            ref.step('Pal_FromBlack', t)[0])
        pin = bytearray(self.rom); pin[0x3C4A] ^= 1
        with self.assertRaises(ValueError):
            pc.PaletteLedger(bytes(pin), dc.costs(bytes(pin)))
        with self.assertRaises(ValueError):
            self.g.run('Pal_ToBlack', pc.PalState(fade_offset=0x40, fade_count=0x3F))    # runs past the palette
        with self.assertRaises(pc.OpenPath):
            self.g.run('Animate_Palette', pc.PalState(fade_timer=0))                       # AnPal_Load
        for words in ([0x0458, 0x0020], [0x0403, 0x0020]):                    # subi.w #,(a0)+ / subi.b #,d3
            c = df.CPU(bytes(0x2000)); c.mem = None
            rom = bytearray(0x2000); rom[0x1000:0x1004] = b''.join(w.to_bytes(2, 'big') for w in words)
            old = df.CPU(bytes(rom)); old.pc = 0x1000; old.a[0] = 0xFFFF8000
            with self.assertRaises(mini68k.Unsupported):
                old.step({}, {})
            new = pf.CPU(bytes(rom)); new.pc = 0x1000; new.a[0] = 0xFFFF8000
            new.write(0xFFFF8000, 'w', 0x0010); new.d[3] = 0x10
            new.step({}, {})
            if words[0] == 0x0458:
                self.assertEqual((new.read(0xFFFF8000, 'w'), new.a[0], new.c, new.n), (0xFFF0, 0xFFFF8002, 1, 1))
            else:
                self.assertEqual((new.d[3] & 0xFF, new.c, new.z), (0xF0, 1, 0))


if __name__ == '__main__':
    unittest.main()
