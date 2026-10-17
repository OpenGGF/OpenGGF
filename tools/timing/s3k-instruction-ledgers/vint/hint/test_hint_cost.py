#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R13 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# test_native_absence also reads KOS46_NATIVE_EXTRA_ROOTS (os.pathsep list of further capture trees); it fails
#   below its 30-histogram floor rather than passing on too little evidence.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Lag VInt_0 and water H-int (deferred Do_Updates) ledger tests (kos46 research, R13).

NATIVE-ABSENT: no retained capture executes VInt_0_Main ($608) or any H-int handler ($D10/$DD8/$E6C/$F00/$F92/$1024);
test_native_absence pins that so a future capture that does is noticed. All other vectors are SYNTHETIC source checks:
hand-derived instruction sums from the GPGX/Motorola tables, branch predicates from the ROM text, and mutations.
"""
import csv
import glob
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import hint_cost as hc  # noqa: E402
import vint8_cost as vc  # noqa: E402
import vint_frame as vf  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

AIZ1 = 0x74E0          # WaterTransition_AIZ1 (CheckLevelForWater default, sonic3k.asm 9832)
HUD = dict(update_timer=1, timer=(0, 2, 10, 17))


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class LagAndHInt(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.vint = vc.VIntLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.vint_m = vc.VIntLedger(cls.rom, tv.motorola_cost)
        cls.hint = hc.HIntLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.hint_m = hc.HIntLedger(cls.rom, tv.motorola_cost)
        cls.frame = vf.FrameLedger(cls.rom, tv.gpgx_cost(cls.rom))

    def lag(self, **kw):
        return self.vint.run(vc.State(vint_routine=0, **kw))

    # -- VInt_0
    def test_lag_level_ntsc_hand_sum(self):
        # jmp 12, nop 4, movem.l d0-a6,-(sp) 128, tst.b 12, beq.w 10 | addq.w 16, 4 x cmpi.b 16, 3 x beq.s fall 8,
        # beq.s 10 | tst.b 12, beq.w 10 | NoWater: move.w abs.l 16, btst 16, beq.s 10, move.w #1 16,
        # move.w abs.w->abs.l 24, stopZ80 20 + 20 + 8, tst.w 12, beq.s 10 | startZ80 20, bra.w 10 |
        # addq.l abs.w 24, movem.l (sp)+ 132, rte 20
        hand = 12 + 4 + 128 + 12 + 10 + 16 + 4 * 16 + 3 * 8 + 10 + 12 + 10 + 16 + 16 + 10 + 16 + 24 + 20 + 20 + 8 \
            + 12 + 10 + 20 + 10 + 24 + 132 + 20
        r = self.lag()
        self.assertEqual((r.cycles, r.lag, r.bookmarked, r.open_terms), (hand, True, False, {}))
        self.assertNotIn(0x592, r.counts)                         # VBLANK wait skipped
        self.assertNotIn(0x1BF0, r.counts)                        # no Set_Kos_Bookmark on a lag VInt

    def test_lag_branch_terms(self):
        base = self.lag().cycles
        self.assertEqual(self.lag(pal=True).cycles - base, 8 + 0x700 * 10 + 14 + 8 - 10)
        self.assertEqual(self.lag(water_flag=True).cycles - base, 110)
        self.assertEqual(self.lag(water_flag=True, water_full_screen=True).cycles - base, 102)
        # Game_mode compare order $88, $8C, $08, $0C; anything else returns from V-int at $62C
        self.assertEqual([self.lag(game_mode=g).cycles - base for g in (0x88, 0x8C, 0x08)], [-72, -48, -24])
        self.assertEqual(self.lag(game_mode=0x04).cycles - base, 464 - 660)
        self.assertEqual(self.lag(competition_mode=True).cycles - base, 188)
        self.assertEqual(self.lag(competition_mode=True, sprite_table_page=1).cycles - base, 196)

    def test_lag_gpgx_equals_motorola_and_fails_closed(self):
        for kw in ({}, dict(pal=True, water_flag=True), dict(competition_mode=True, sprite_table_page=1)):
            self.assertEqual(self.lag(**kw).cycles, self.vint_m.run(vc.State(vint_routine=0, **kw)).cycles)
        with self.assertRaises(ValueError):
            self.vint.run(vc.State(vint_routine=0x40))           # masks to VInt_0 via jsr: not an official value
        bad = bytearray(self.rom); bad[0x6FE] ^= 1
        with self.assertRaises(ValueError):
            vc.VIntLedger(bytes(bad), tv.motorola_cost)

    def test_vint8_deferral_flag(self):
        r = self.vint.run(vc.State(water_flag=True, h_int_counter=91))
        self.assertTrue(r.deferred_updates)
        self.assertNotIn('UpdateHUD', r.open_terms)
        r = self.vint.run(vc.State(water_flag=True, h_int_counter=92))
        self.assertFalse(r.deferred_updates)
        self.assertIn('UpdateHUD', r.open_terms)

    # -- H-int
    def test_handler_hand_sums(self):
        self.assertEqual(self.hint.run(hc.HState(h_int_flag=False, do_updates_in_h_int=True)).cycles, 12 + 10 + 20)
        self.assertEqual(self.hint.run(hc.HState('HInt2', h_int_flag=False)).cycles, 16 + 12 + 10 + 20)
        a = self.hint.run(hc.HState(water_palette_data=AIZ1)).cycles
        d = self.hint.run(hc.HState(water_palette_data=AIZ1, do_updates_in_h_int=True)).cycles
        # tst.b/bne taken 22 + clr.b 16 + movem 128 + jsr 20 + Do_Updates (jsr 20, move.w 16, bsr.w 18, tst.w 12,
        # beq.w 10, rts 16) + movem 132 + rte 20, versus tst.b/bne fall/rte 40
        self.assertEqual(d - a, 22 + 16 + 128 + 20 + (20 + 16 + 18 + 12 + 10 + 16) + 132 + 20 - 40)

    def test_colour_count_follows_rom_table_and_counter(self):
        n = ((self.rom[AIZ1] << 8) | self.rom[AIZ1 + 1]) + 1
        self.assertEqual(n, 16)                                  # watertransheader: words - 1
        for counter, want in ((0x60, n), (200, n), (205, n - 5), (200 + n - 1, 1), (200 + n, 0)):
            r = self.hint.run(hc.HState(water_palette_data=AIZ1, h_int_counter=counter))
            self.assertEqual(r.colors, want, counter)
            if want:
                self.assertEqual(r.counts[0xE40], want)          # dbf d1,$$transferColors
                self.assertEqual(r.counts[0xE3C], want * (0x24 + 1))
        hi4 = self.hint.run(hc.HState('HInt4', water_palette_data=AIZ1))
        self.assertEqual(hi4.counts[0xF62], 16 * (0x33 + 1))

    def test_twins_cost_the_same_and_motorola_agrees(self):
        for a, b in (('HInt5', 'HInt3'), ('HInt_6', 'HInt4')):
            for kw in (dict(), dict(do_updates_in_h_int=True), dict(h_int_counter=207)):
                ra = self.hint.run(hc.HState(a, water_palette_data=AIZ1, **kw)).cycles
                self.assertEqual(ra, self.hint.run(hc.HState(b, water_palette_data=AIZ1, **kw)).cycles)
                self.assertEqual(ra, self.hint_m.run(hc.HState(a, water_palette_data=AIZ1, **kw)).cycles)

    def test_deferred_do_updates_matches_the_vint_body(self):
        v = self.vint.run(vc.State())
        h = self.hint.run(hc.HState(water_palette_data=AIZ1, do_updates_in_h_int=True))
        body = range(*hc.DO_UPDATES)
        self.assertEqual({pc: k for pc, k in h.counts.items() if pc in body},
                         {pc: k for pc, k in v.counts.items() if pc in body})
        out = self.frame.run_hint(hc.HState(water_palette_data=AIZ1, do_updates_in_h_int=True, demo_timer=3),
                                  vf.nc.Nem(), vf.hc.Hud(**HUD))
        self.assertTrue(out['closed'])
        self.assertEqual(out['after'], {'h_int_flag': False, 'do_updates_in_h_int': False, 'lag_frame_count': 0,
                                        'demo_timer': 2})
        hud = self.frame.hud.run(vf.hc.Hud(**HUD))[0]
        self.assertEqual(out['cycles'], self.hint.run(hc.HState(water_palette_data=AIZ1, do_updates_in_h_int=True,
                                                               demo_timer=3)).cycles + hud + 38)

    def test_mutations_and_fail_closed(self):
        bad = bytearray(self.rom); bad[0xE6C + 0x10] ^= 1                # break the HInt5 / HInt3 twin
        with self.assertRaises(ValueError):
            hc.HIntLedger(bytes(bad), tv.motorola_cost)
        with self.assertRaises(ValueError):
            self.hint.run(hc.HState('HInt', water_palette_data=AIZ1))  # competition handler: no deferred path
        with self.assertRaises(ValueError):
            self.hint.run(hc.HState(water_palette_data=None))
        other = self.hint.run(hc.HState(water_palette_data=0x7502)).cycles      # WaterTransition_AIZ2
        self.assertNotEqual(other, self.hint.run(hc.HState(water_palette_data=AIZ1)).cycles)

    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_absence(self):
        # Promotion: the research copy also searched a sibling scratch tree by a fixed relative name; that tree is
        # now named explicitly (KOS46_NATIVE_EXTRA_ROOTS, os.pathsep-separated). The 30-file floor is unchanged.
        extra = [r for r in os.environ.get('KOS46_NATIVE_EXTRA_ROOTS', '').split(os.pathsep) if r]
        roots = [tv.NATIVE] + extra
        pcs = {0x608, 0xD10, 0xDD8, 0xE6C, 0xF00, 0xF92, 0x1024}
        files = [f for r in roots for f in glob.glob(os.path.join(r, '**', 'frame-pc-histogram.csv'), recursive=True)]
        self.assertGreater(len(files), 30)
        for f in files:
            with open(f) as fh:
                hit = [r for r in csv.DictReader(fh) if int(r['pc'], 16) in pcs]
            self.assertEqual(hit, [], f)


if __name__ == '__main__':
    unittest.main()
