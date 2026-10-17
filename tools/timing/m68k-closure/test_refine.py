#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R4 (2026-10-16); R6 predicate controls.
# Inputs: none beyond this directory (hand-assembled programs).
# Promoted with the kos46 instruction-closure patches (v3 9b76415d..., successor 81f3097a...) on 2026-10-17 at base 00f8ab78a.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Tests for refine.py on hand-assembled programs. Expected totals are Motorola MC68000 section 8 times written out
here; each rule has a positive case and a negative control whose broken precondition must leave the interval open."""
import collections
import os
import sys
import types
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import frame_closure as fc  # noqa: E402
import m68k_timing  # noqa: E402
import refine  # noqa: E402


def run(program, counts, facts=()):
    rom = bytearray(0x100)
    for pc, words in program.items():
        for k, w in enumerate(words):
            rom[pc + 2 * k], rom[pc + 2 * k + 1] = w >> 8, w & 0xFF
    rom = bytes(rom)
    model = fc.CostModel({'cost_model': {'kind': 'motorola'}}, types.SimpleNamespace(gpgx_git=None), rom)
    dec, cache = m68k_timing.Decoder(rom), {}
    counts = collections.Counter(counts)
    solved, totals, notes = refine.resolve(rom, counts, lambda pins: fc.solve_bucket(counts, dec, cache, {}, pins),
                                           model.cost, list(facts))
    return solved, totals, notes


class SccFlagPair(unittest.TestCase):
    def test_outcome_from_adjacent_complementary_branch(self):
        # 0 smi d4 | 2 bpl.s 6 | 4 nop | 6 nop | 8 rts ; N set once in 10
        _, totals, _ = run({0: [0x5BC4], 2: [0x6A02], 4: [0x4E71], 6: [0x4E71], 8: [0x4E75]},
                           {0: 10, 2: 10, 4: 1, 6: 10, 8: 10})
        self.assertEqual(totals[0], 1 * 6 + 9 * 4)   # Scc Dn: 6 true, 4 false

    def test_flag_changing_instruction_between_declines(self):
        # 0 smi d4 | 2 tst.w d0 | 4 bpl.s 8 | 6 nop | 8 nop | A rts
        _, totals, notes = run({0: [0x5BC4], 2: [0x4A40], 4: [0x6A02], 6: [0x4E71], 8: [0x4E71], 0xA: [0x4E75]},
                               {0: 10, 2: 10, 4: 10, 6: 1, 8: 10, 0xA: 10})
        self.assertNotIn(0, totals)
        self.assertTrue(any('scc_flag_pair 000000' in n for n in notes))


class DbfCounterBit(unittest.TestCase):
    PROGRAM = {0: [0x7203], 2: [0x03C3], 4: [0x51C9, 0xFFFC], 8: [0x4E75]}   # moveq #3,d1; bset d1,d3; dbf d1; rts

    def test_bit_register_below_16(self):
        _, totals, _ = run(self.PROGRAM, {0: 1, 2: 4, 4: 4, 8: 1})
        self.assertEqual(totals[2], 4 * 6)           # BSET Dn,Dn: 8 maximum, 6 for bit numbers below 16

    def test_counter_above_15_declines(self):
        program = dict(self.PROGRAM)
        program[0] = [0x7214]                       # moveq #20,d1
        _, totals, _ = run(program, {0: 1, 2: 21, 4: 21, 8: 1})
        self.assertNotIn(2, totals)


class NonnegativeCounter(unittest.TestCase):
    # 0 moveq #5,d7 | 2 subq.w #1,d7 | 4 dbmi d4,2 | 8 move.w d7,d6 | A bmi.s E | C nop | E rts
    PROGRAM = {0: [0x7E05], 2: [0x5347], 4: [0x5BCC, 0xFFFC], 8: [0x3C07], 0xA: [0x6B02], 0xC: [0x4E71],
               0xE: [0x4E75]}
    FACT = {'kind': 'nonnegative_counter', 'register': 7, 'entry_pc': '000000', 'init_pc': '000000',
            'sign_test_branch_pc': '00000A'}

    def test_dbmi_exits_are_expiries(self):
        _, totals, _ = run(self.PROGRAM, {0: 1, 2: 3, 4: 3, 8: 1, 0xA: 1, 0xC: 1, 0xE: 1}, [self.FACT])
        self.assertEqual(totals[4], 2 * 10 + 1 * 14)  # DBcc: 10 loop, 14 count expired

    def test_negative_sign_test_declines(self):
        _, totals, notes = run(self.PROGRAM, {0: 1, 2: 3, 4: 3, 8: 1, 0xA: 1, 0xC: 0, 0xE: 1}, [self.FACT])
        self.assertNotIn(4, totals)
        self.assertTrue(any('never-taken bmi' in n for n in notes))


class NonnegativeCounterMoveaControl(unittest.TestCase):
    # Review counterexample: the sign test is fed by movea.w d7,a0, which leaves the CCR from tst.w d0 (non-negative)
    # 0 moveq #5,d7 | 2 subq.w #1,d7 | 4 dbmi d4,2 | 8 tst.w d0 | A movea.w d7,a0 | C bmi.s 10 | E nop | 10 rts
    # d7 reaches -1 on the sixth pass, so the last dbmi exit is condition-true (12): true total 5*10 + 12 = 62.
    PROGRAM = {0: [0x7E05], 2: [0x5347], 4: [0x5BCC, 0xFFFC], 8: [0x4A40], 0xA: [0x3047], 0xC: [0x6B02],
               0xE: [0x4E71], 0x10: [0x4E75]}
    COUNTS = {0: 1, 2: 6, 4: 6, 8: 1, 0xA: 1, 0xC: 1, 0xE: 1, 0x10: 1}
    FACT = {'kind': 'nonnegative_counter', 'register': 7, 'entry_pc': '000000', 'init_pc': '000000',
            'sign_test_branch_pc': '00000C'}

    def test_movea_is_not_a_sign_test(self):
        _, totals, notes = run(self.PROGRAM, self.COUNTS, [self.FACT])
        self.assertNotIn(4, totals)                  # interval [5*10+12, 5*10+14] stays open; 64 alone would be wrong
        self.assertTrue(any('sign test flags do not come from' in n for n in notes))

    def test_move_to_data_register_still_proves(self):
        program = dict(self.PROGRAM)
        program[8], program[0xA] = [0x4E71], [0x3C07]   # nop | move.w d7,d6 (Render_Sprites form)
        counts = {0: 1, 2: 3, 4: 3, 8: 1, 0xA: 1, 0xC: 1, 0xE: 1, 0x10: 1}
        program[0] = [0x7E05]
        _, totals, _ = run(program, counts, [self.FACT])
        self.assertEqual(totals[4], 2 * 10 + 14)


class FlagPredicates(unittest.TestCase):
    def test_ccr_producing_forms(self):
        self.assertTrue(refine._sets_nz_from_word_dn(0x3C07, 7))     # move.w d7,d6
        self.assertTrue(refine._sets_nz_from_word_dn(0x4A47, 7))     # tst.w d7
        self.assertFalse(refine._sets_nz_from_word_dn(0x3047, 7))    # movea.w d7,a0
        self.assertFalse(refine._sets_nz_from_word_dn(0x3087, 7))    # move.w d7,(a0): memory form not accepted
        self.assertTrue(refine._move_sets_nz(0x1010))                # move.b (a0),d0
        self.assertFalse(refine._move_sets_nz(0x3048))               # movea.w a0,a0
        self.assertFalse(refine._move_sets_nz(0x2040))               # movea.l d0,a0


class ZExitMoveaControl(unittest.TestCase):
    def test_bmi_after_movea_declines(self):
        program = dict(ZExitAccounting.PROGRAM)
        program[0x20] = [0x3050]                      # movea.w (a0),a0: does not set N/Z for the bmi
        counts = {0: 3, 6: 3, 8: 3, 0xA: 3, 0xC: 3, 0x20: 3, 0x22: 3, 0x24: 6, 0x26: 6, 0x2A: 3}
        solved, totals, notes = run(program, counts, [ZExitAccounting.FACT])
        self.assertNotIn(0x26, totals)
        self.assertTrue(any('bmi flags do not come from' in n for n in notes))


class ZExitAccounting(unittest.TestCase):
    # caller: 0 jsr $20 | 6 bne.s A | 8 nop | A nop | C rts
    # callee: 20 move.b (a0),d0 | 22 bmi.s 2A | 24 tst.l (a1) | 26 dbeq d0,24 | 2A rts
    PROGRAM = {0: [0x4EB9, 0x0000, 0x0020], 6: [0x6602], 8: [0x4E71], 0xA: [0x4E71], 0xC: [0x4E75],
               0x20: [0x1010], 0x22: [0x6B06], 0x24: [0x4A91], 0x26: [0x57C8, 0xFFFC], 0x2A: [0x4E75]}
    FACT = {'kind': 'z_exit_accounting', 'entry_pcs': ['000020'], 'return_pc': '00002A',
            'z0_branch_exit_pc': '000022', 'dbcc_exit_pc': '000026'}

    def test_callers_fix_the_exit_mix(self):
        counts = {0: 3, 6: 3, 8: 3, 0xA: 3, 0xC: 3, 0x20: 3, 0x22: 3, 0x24: 6, 0x26: 6, 0x2A: 3}
        solved, open_totals, _ = run(self.PROGRAM, counts)
        self.assertNotEqual(*solved['branches'][0x22])            # without the fact the bmi outcome is open
        solved, totals, _ = run(self.PROGRAM, counts, [self.FACT])
        self.assertEqual(solved['branches'][0x22], [0, 0])
        self.assertEqual(totals[0x26], 3 * 10 + 3 * 12)           # 3 loops, 3 exits on equality (12)

    def test_failed_allocation_keeps_interval(self):
        counts = {0: 3, 6: 3, 8: 2, 0xA: 3, 0xC: 3, 0x20: 3, 0x22: 3, 0x24: 6, 0x26: 6, 0x2A: 3}
        _, totals, _ = run(self.PROGRAM, counts, [self.FACT])
        self.assertNotIn(0x26, totals)


class DataWrites(unittest.TestCase):
    def test_register_write_sets(self):
        self.assertEqual(refine.data_writes(0x5347), {7})          # subq.w #1,d7
        self.assertEqual(refine.data_writes(0x3C07), {6})          # move.w d7,d6
        self.assertEqual(refine.data_writes(0x4A47), set())        # tst.w d7
        self.assertEqual(refine.data_writes(0x51CF), {7})          # dbf d7
        self.assertEqual(refine.data_writes(0x03C3), {3})          # bset d1,d3
        self.assertIsNone(refine.data_writes(0x4CDF))              # movem to registers: unknown, fail closed


if __name__ == '__main__':
    unittest.main()
