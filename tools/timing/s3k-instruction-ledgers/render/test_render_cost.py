#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R15 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Render_Sprites / Draw_Sprite ledger tests (kos46 research R15).

SYNTHETIC/SOURCE: ROM micro-execution must equal the independent reference (render_reference.py) on SAT bytes,
Sprites_drawn, on-screen flags and drained lists, over real ROM mappings (Map_Sonic, Map_Tails, Map_Ring,
Map_Monitor, Map_Spring) and synthetic mapping blobs placed past the ROM end in the private model (static mappings,
x == 0 fix-up, SAT budget, multi-draw clipping in all four flip writers, Spritemask). Hand sums use the pinned table.
NATIVE (comparison only): the 42 ending buckets are compared on display-list-independent PCs only; Level_started_flag
= 0 and Spritemask_flag = 0 are TEST-ORACLE gate values (the bucket's observed path), not engine-derived here.
Display-list contents, SST fields and SAT state in those buckets are not in the capture, so no other native claim.
"""
import collections
import csv
import os
import random
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'mainloop'))
sys.path.insert(0, os.path.join(HERE, '..', 'vint'))
import render_cost as rc  # noqa: E402
import render_reference as rr  # noqa: E402
import alloc_cost as ac  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

REAL_MAPS = (0x146620, 0x148EB8, 0x1A99A, 0x1DBA2, 0x2375C)    # Map_Sonic, Map_Tails, Map_Ring, Map_Monitor, Map_Spring
S = rc.Sprite
STATE_FREE = (0x1AD20, 0x1AD24, 0x1AD28, 0x1AD2A, 0x1AD2C, 0x1AD30, 0x1AD34, 0x1AD38, 0x1AD3C, 0x1AD4A, 0x1AD4C,
              0x1ADE6, 0x1ADEC, 0x1ADEE, 0x1ADF4, 0x1ADFC, 0x1AE00, 0x1AE06, 0x1AE0A, 0x1AE18, 0x1AE1C, 0x1AE1E,
              0x1AE22, 0x1AE26, 0x1AE56)


def mapping_blob(frames):
    """frames: list of piece lists [(y, size, tile, x)]; returns bytes with a word offset table."""
    head = 2 * len(frames)
    body, offs = b'', []
    for pieces in frames:
        offs.append(head + len(body))
        body += len(pieces).to_bytes(2, 'big')
        for y, size, tile, x in pieces:
            body += bytes([y & 0xFF, size]) + (tile & 0xFFFF).to_bytes(2, 'big') + (x & 0xFFFF).to_bytes(2, 'big')
    return b''.join(o.to_bytes(2, 'big') for o in offs) + body


def valid_frames(rom, m):
    """Frames whose piece count is <= 64 and every size byte <= $F. Shipped tables also hold unused entries that
    decode to garbage (e.g. Map_Sonic's last entry, several Map_Spring entries); the ROM would read them as given,
    the reference refuses them, and real objects do not select them."""
    out = []
    for f in range(nframes(rom, m)):
        o = (rom[m + 2 * f] << 8) | rom[m + 2 * f + 1]
        a = (m + (o - 0x10000 if o & 0x8000 else o)) & 0xFFFFFF
        c = (rom[a] << 8) | rom[a + 1]
        if c <= 64 and all(rom[a + 3 + 6 * k] <= 0x0F for k in range(c)):
            out.append(f)
    return out


def nframes(rom, m):
    """Frames in a mapping's offset table: entries before the lowest offset (offset tables precede frame data)."""
    k, low = 0, 1 << 16
    while 2 * k < low:
        low = min(low, (rom[m + 2 * k] << 8) | rom[m + 2 * k + 1])
        k += 1
    return k


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class Render(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.vc.TOOL)
        import gpgx_cycles
        cls.table = gpgx_cycles.load_table(tv.GPGX)
        cls.g = rc.RenderLedger(cls.rom, ac.gpgx_cost(cls.rom, cls.table))
        cls.m = rc.RenderLedger(cls.rom, ac.motorola_cost)
        cls.frames = {m: valid_frames(cls.rom, m) for m in REAL_MAPS}

    def ledger(self, rom):
        return rc.RenderLedger(rom, ac.gpgx_cost(rom, self.table))

    def with_blob(self, blob):
        base = 0x400000
        return self.rom + blob + bytes(64), base

    def check(self, sc, rom=None, ledger=None):
        rom = rom or self.rom
        r = (ledger or (self.g if rom is self.rom else self.ledger(rom))).render(sc)
        sat, drawn, on, lists = rr.Ref(rom, sc).run()
        self.assertEqual(r.sat, sat)
        self.assertEqual((r.sprites_drawn, r.on_screen, r.list_counts), (drawn, on, lists))
        return r

    def random_scene(self, rng, maps, flags_pool, n_obj, levels=8):
        lv = [[] for _ in range(8)]
        for k in range(n_obj):
            m = rng.choice(maps)
            sp = S(0xB000 + 0x4A * k, rng.choice(flags_pool), rng.randrange(-64, 0x200), rng.randrange(-64, 0x180),
                   rng.randrange(1, 64), rng.randrange(1, 64), m, rng.choice(self.frames[m]),
                   rng.randrange(0x10000))
            lv[rng.randrange(levels)].append(sp)
        return rc.Scene(levels=tuple(tuple(x) for x in lv), camera_x=rng.randrange(0x100), camera_y=rng.randrange(0x80),
                        y_wrap=rng.choice((0xFFF, 0x7FF, 0xFFFF)))

    # -- equality with the independent reference
    def test_real_rom_mappings_all_flips(self):
        rng = random.Random(15)
        for _ in range(60):
            sc = self.random_scene(rng, REAL_MAPS, (0x04, 0x05, 0x06, 0x07, 0x00, 0x01, 0x02, 0x03), rng.randrange(0, 14))
            r = self.check(sc)
            self.assertEqual(r.cycles, self.m.render(sc).cycles)

    def test_synthetic_static_zero_x_and_budget(self):
        blob = mapping_blob([[(-8, 0x05, 0x10, -8)], [(0, 0x0F, 0, 0)] * 30, [(4, 0x0A, 2, 0x180 - 0x80)]])
        rom, base = self.with_blob(blob)
        static = base + 2 * 3 + 2                                 # frame 0's first piece as a static mapping
        sc = rc.Scene(levels=((S(0xB000, 0x20, 0x100, 0x100, mappings=static),
                               S(0xB04A, 0x00, 0x80, 0x100, mappings=base, frame=2)),)   # x + $80 = $180 -> & $1FF
                      + ((),) * 7)
        r = self.check(sc, rom)
        self.assertEqual(r.sprites_drawn, 2)
        zero = rc.Scene(levels=((S(0xB000, 0x00, 0x180, 0x100, mappings=base, frame=2),),) + ((),) * 7)
        self.check(zero, rom)                                     # $180 + $80 = $200 -> & $1FF = 0 -> 1
        many = rc.Scene(levels=tuple(tuple(S(0xB000 + 0x4A * k, 0x04 | (k & 3), 0x60, 0x60, 16, 16, base, 1)
                                           for k in range(4)) if lvl == 3 else () for lvl in range(8)))
        r = self.check(many, rom)                                 # 120 pieces requested, budget 80
        self.assertEqual(r.sprites_drawn, 0x50)
        self.assertNotIn(0x1AE10, r.counts)                       # no unused entries to clear
        self.assertTrue(all(r.on_screen.values()))

    def test_zero_x_fixup_in_every_unclipped_writer(self):
        # piece x word 0, size 0 (width 8): plain/y-flip x = 0 + d0, x-flip/xy-flip x = -0 - 8 + d0; d0 = $200 / $208
        rom, base = self.with_blob(mapping_blob([[(0, 0, 0, 0)]]))
        led = self.ledger(rom)
        for flags, x, pc in ((0x00, 0x200, 0x1AF92), (0x01, 0x208, 0x1AFCC), (0x02, 0x200, 0x1B064),
                             (0x03, 0x208, 0x1B01C)):
            sc = rc.Scene(levels=((S(0xB000, flags, x, 0x100, mappings=base),),) + ((),) * 7)
            r = self.check(sc, rom, led)
            self.assertEqual(r.counts.get(pc), 1, hex(flags))           # addq.w #1,d2 executed
            self.assertEqual(r.sat[6:8], b'\x00\x01')

    def test_multidraw_clipping_all_writers(self):
        frames = [[], [(-0x40, 0x0F, 1, -0x40), (0, 0x05, 2, 0), (0x60, 0x0A, 3, 0x120)],
                  [(-0x7F, 0x03, 4, 0x7FF), (0x30, 0x0C, 5, -0x10)]]
        rom, base = self.with_blob(mapping_blob(frames))
        rng = random.Random(7)
        for flags in (0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47):
            for _ in range(12):
                kids = tuple((rng.randrange(0, 0x220), rng.randrange(0, 0x1A0), rng.randrange(3))
                             for _ in range(rng.randrange(0, 9)))
                sp = S(0xB000, flags, rng.randrange(0, 0x200), rng.randrange(0, 0x180), rng.randrange(1, 80),
                       rng.randrange(1, 80), base, rng.randrange(3), 0x2345, kids)
                sc = rc.Scene(levels=((), (sp,)) + ((),) * 6, camera_x=rng.randrange(0x80), camera_y=rng.randrange(0x40))
                self.check(sc, rom)

    def test_spritemask_scan(self):
        blob = mapping_blob([[(0, 0, 0x7C0 - 0x100, 0x20)] * 3])
        rom, base = self.with_blob(blob)
        sc = rc.Scene(levels=((S(0xB000, 0x00, 0x100, 0x100, mappings=base, art_tile=0x100),),) + ((),) * 7,
                      spritemask=1, player1_routine=6)
        self.check(sc, rom)

    # -- hand sums
    def test_hand_sums(self):
        empty = self.g.render(rc.Scene()).cycles
        # head: tst.w 12, bne.w fall 12, moveq 4 x2, lea 8 x3, tst.b 12, beq.s 10
        head = 12 + 12 + 4 + 4 + 3 * 8 + 12 + 10
        # each level: tst.w (a5) 8, beq.w taken 10, cmpa.l # 14, bne.s, lea 8, cmpa.l # 14, blo.w
        lvl0 = 8 + 10 + 14 + 8 + 16 + 10 + 8 + 14 + 10        # level 0: bne.s fall, cmpi.b #9 16, bne.s taken
        mid = 8 + 10 + 14 + 10 + 8 + 14 + 10
        last = 8 + 10 + 14 + 10 + 8 + 14 + 12                 # blo.w falls after level 7
        clear = 4 + 8 + 4 + 80 * (8 + 8) + 79 * 10 + 14         # move.w d7,d6, bmi fall, moveq, loop, dbf
        tail = 8 + 4 + 12 + 12 + 10 + 16                        # subi.w, neg.w, move.b abs, tst.w, beq.s, rts
        self.assertEqual(empty, head + lvl0 + 6 * mid + last + clear + tail)
        blob = mapping_blob([[(0, 0, 0, 0x10)] * 2, [(0, 0, 0, 0x10)] * 3])
        rom, base = self.with_blob(blob)
        led = self.ledger(rom)
        one = led.render(rc.Scene(levels=((S(0xB000, 0, 0x100, 0x100, mappings=base, frame=0),),) + ((),) * 7)).cycles
        two = led.render(rc.Scene(levels=((S(0xB000, 0, 0x100, 0x100, mappings=base, frame=1),),) + ((),) * 7)).cycles
        # one more plain piece: 16 piece instructions with dbmi taken, one fewer clear iteration (move.w + addq + dbf)
        piece = 8 + 4 + 4 + 8 + 12 + 8 + 8 + 4 + 8 + 8 + 4 + 8 + 10 + 8 + 4 + 10
        self.assertEqual(two - one, piece - (8 + 8 + 10))

    def test_piece_cost_per_writer_variant(self):
        # one more piece in each unclipped writer, minus one clear-loop iteration (move.w 8 + addq 8 + dbf 10)
        hand = {0: [8, 4, 4, 8, 12, 8, 8, 4, 8, 8, 4, 8, 10, 8, 4, 10],
                1: [8, 4, 4, 8, 8, 8, 8, 8, 4, 8, 8, 8, 4, 14, 4, 4, 8, 10, 8, 4, 10],
                2: [8, 4, 4, 8, 12, 14, 4, 4, 8, 8, 8, 4, 8, 8, 8, 4, 8, 10, 8, 4, 10],
                3: [8, 4, 4, 8, 14, 4, 4, 8, 8, 8, 8, 8, 4, 8, 8, 8, 4, 14, 4, 4, 8, 10, 8, 4, 10]}
        rom, base = self.with_blob(mapping_blob([[(0, 5, 0, 0x10)] * 2, [(0, 5, 0, 0x10)] * 3]))
        led = self.ledger(rom)
        for flags, parts in hand.items():
            c = [led.render(rc.Scene(levels=((S(0xB000, flags, 0x100, 0x100, mappings=base, frame=f),),)
                                     + ((),) * 7)).cycles for f in (0, 1)]
            self.assertEqual(c[1] - c[0], sum(parts) - (8 + 8 + 10), flags)

    # -- Draw_Sprite
    def test_draw_sprite(self):
        sp = S(0xB000, priority=0x180)
        c, lvl, _ = self.g.draw_sprite(sp, [0] * 8)
        self.assertEqual(lvl, 3)
        # lea 8, adda.w d16 16, cmpi.w #,(a1) 12, bhs fall 8, addq.w #2,(a1) 12, adda.w (a1) 12, move.w a0,(a1) 8, rts 16
        self.assertEqual(c, 8 + 16 + 12 + 8 + 12 + 12 + 8 + 16)
        full = [0] * 8
        full[3] = full[4] = 0x7E
        c2, lvl2, _ = self.g.draw_sprite(sp, full)
        self.assertEqual(lvl2, 5)                                 # overflow walks to lower-priority levels
        self.assertEqual(self.g.draw_sprite(S(0xB000, priority=0x380), [0x7E] * 8)[1], None)   # level 7 full: dropped
        self.assertGreater(c2, c)

    # -- native comparison (state-independent PCs only)
    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_list_independent_pcs(self):
        want = self.g.render(rc.Scene()).counts           # Level_started_flag 0, Spritemask_flag 0: TEST ORACLE gates
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

    # -- mutations and refusals
    def test_mutations_and_fail_closed(self):
        wide = mapping_blob([[(0, 0x0A, 0, 0x10)]])
        rom_w, base_w = self.with_blob(wide)
        sc = rc.Scene(levels=((S(0xB000, 0x01, 0x100, 0x100, mappings=base_w),),) + ((),) * 7)   # x-flip, size $0A
        bad = bytearray(rom_w); bad[0x1AFD8 + 0x0A] ^= 0x08                      # x-flip width table entry
        r = self.ledger(bytes(bad)).render(sc)
        self.assertNotEqual(r.sat, rr.Ref(rom_w, sc).run()[0])
        self.assertEqual(self.ledger(rom_w).render(sc).sat, rr.Ref(rom_w, sc).run()[0])
        blob = mapping_blob([[(0, 0, 0, 0)]])
        rom, base = self.with_blob(blob)
        nofix = bytearray(rom); nofix[0x1AF90] = 0x60                           # $1AF90 bne.s -> bra.s: drop x == 0 -> 1
        zero = rc.Scene(levels=((S(0xB000, 0x00, 0x200, 0x100, mappings=base),),) + ((),) * 7)
        self.assertNotEqual(self.ledger(bytes(nofix)).render(zero).sat, rr.Ref(rom, zero).run()[0])
        pinned = bytearray(self.rom); pinned[0x1AE15] ^= 1
        with self.assertRaises(ValueError):
            rc.RenderLedger(bytes(pinned), ac.motorola_cost)
        for kw in (dict(competition=True), dict(level_started=True), dict(current_zone=9)):
            with self.assertRaises(rc.OpenPath):
                self.g.render(rc.Scene(**kw))
        with self.assertRaises(ValueError):
            self.g.render(rc.Scene(levels=(tuple(S(0xB000) for _ in range(64)),) + ((),) * 7))
        with self.assertRaises(ValueError):
            self.g.render(rc.Scene(levels=((S(0xB000, 0x40, children=((0, 0, 0),) * 9),),) + ((),) * 7))


if __name__ == '__main__':
    unittest.main()
