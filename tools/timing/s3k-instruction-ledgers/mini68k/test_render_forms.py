#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R15 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: none beyond this directory (hand-assembled synthetic vectors).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Opcode / flag / effective-address tests for render_forms.py (kos46 research R15). Each case assembles a few words
by hand at $1000 of a synthetic image, executes them, and checks registers, memory, CCR and the step outcome against
68000 semantics computed independently here. Unsupported variants must raise."""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import render_forms as rf  # noqa: E402
import mini68k  # noqa: E402


def cpu_with(words, setup=None):
    rom = bytearray(0x2000)
    for k, w in enumerate(words):
        rom[0x1000 + 2 * k] = w >> 8
        rom[0x1000 + 2 * k + 1] = w & 0xFF
    rom[0x1800:0x1802] = b'\xFF\xFE'                     # data word -2 at $1800
    c = rf.CPU(bytes(rom))
    c.pc = 0x1000
    if setup:
        setup(c)
    return c


class Forms(unittest.TestCase):
    def flags(self, c):
        return (c.x, c.n, c.z, c.v, c.c)

    def test_ori_andi_byte_memory_d16(self):
        c = cpu_with([0x0028, 0x0080, 0x0004], lambda c: (c.a.__setitem__(0, 0xFFFFB000), c.write(0xFFFFB004, 'b', 0x05)))
        c.c = c.v = 1
        self.assertEqual(c.step({}, {}), 'seq')
        self.assertEqual((c.read(0xFFFFB004, 'b'), c.pc, c.n, c.z, c.v, c.c), (0x85, 0x1006, 1, 0, 0, 0))
        c = cpu_with([0x0228, 0x007F, 0x0004], lambda c: (c.a.__setitem__(0, 0xFFFFB000), c.write(0xFFFFB004, 'b', 0x80)))
        c.step({}, {})
        self.assertEqual((c.read(0xFFFFB004, 'b'), c.z, c.n), (0x00, 1, 0))

    def test_eori_subi_neg_word(self):
        c = cpu_with([0x0A42, 0x0800], lambda c: c.d.__setitem__(2, 0x12340801))
        c.step({}, {})
        self.assertEqual(c.d[2], 0x12340001)
        c = cpu_with([0x0446, 0x004F], lambda c: c.d.__setitem__(6, 0xAAAA0010))
        c.step({}, {})
        self.assertEqual((c.d[6], c.c, c.x, c.n, c.z), (0xAAAAFFC1, 1, 1, 1, 0))       # $10 - $4F borrows
        c = cpu_with([0x0446, 0x0001], lambda c: c.d.__setitem__(6, 0x8000))
        c.step({}, {})
        self.assertEqual((c.d[6] & 0xFFFF, c.v), (0x7FFF, 1))                         # signed overflow
        for v, res, flags in ((0, 0, (0, 0, 1, 0, 0)), (1, 0xFFFF, (1, 1, 0, 0, 1)), (0x8000, 0x8000, (1, 1, 0, 1, 1))):
            c = cpu_with([0x4446], lambda c, v=v: c.d.__setitem__(6, 0x55550000 | v))
            c.step({}, {})
            self.assertEqual((c.d[6], self.flags(c)), (0x55550000 | res, flags), hex(v))

    def test_btst_immediate_register_mod_32(self):
        c = cpu_with([0x0806, 0x0006], lambda c: c.d.__setitem__(6, 0x40))
        c.step({}, {})
        self.assertEqual((c.z, c.pc), (0, 0x1004))
        c = cpu_with([0x0806, 0x0026], lambda c: c.d.__setitem__(6, 0x40))           # bit 38 -> 6
        c.step({}, {})
        self.assertEqual(c.z, 0)
        c = cpu_with([0x0806, 0x0005], lambda c: c.d.__setitem__(6, 0x40))
        c.step({}, {})
        self.assertEqual(c.z, 1)

    def test_and_cmp_cmpa_adda(self):
        c = cpu_with([0xC278, 0xEEAA], lambda c: (c.d.__setitem__(1, 0x00011234), c.write(0xFFFFEEAA, 'w', 0x0FF0)))
        c.step({}, {})
        self.assertEqual((c.d[1], c.n, c.z), (0x00010230, 0, 0))
        c = cpu_with([0xB242], lambda c: (c.d.__setitem__(1, 0x0100), c.d.__setitem__(2, 0x0120)))
        c.step({}, {})
        self.assertEqual((c.c, c.z, c.n), (1, 0, 1))                                  # $100 - $120: borrow -> blo
        self.assertTrue(c.cond(5) and not c.cond(4))
        c = cpu_with([0xB2FC, 0xAC00], lambda c: c.a.__setitem__(1, 0xFFFFAC00))     # cmpa.w sign-extends
        c.step({}, {})
        self.assertEqual(c.z, 1)
        c = cpu_with([0xBBFC, 0xFFFF, 0xB000], lambda c: c.a.__setitem__(5, 0xFFFFAC00))
        c.step({}, {})
        self.assertTrue(c.cond(5))                                                    # blo: $FFFFAC00 < $FFFFB000
        c = cpu_with([0xD2F1, 0x4000], lambda c: (c.a.__setitem__(1, 0x1800), c.d.__setitem__(4, 0)))
        c.step({}, {})
        self.assertEqual(c.a[1], 0x17FE)                                              # adda.w (a1,d4.w): word -2

    def test_dbcc_outcomes(self):
        # subq.w #1,d7 ; dbmi d4,* (displacement -4 back to the subq)
        prog = [0x5347, 0x5BCC, 0xFFFC]
        c = cpu_with(prog, lambda c: (c.d.__setitem__(7, 5), c.d.__setitem__(4, 1)))
        outs = []
        for _ in range(4):
            c.step({}, {})
            outs.append(c.step({}, {}))
            if outs[-1] != 'taken':
                break
        self.assertEqual(outs, ['taken', 'expired'])                                  # counter ran out first
        c = cpu_with(prog, lambda c: (c.d.__setitem__(7, 1), c.d.__setitem__(4, 9)))
        outs = []
        for _ in range(4):
            c.step({}, {})
            outs.append(c.step({}, {}))
            if outs[-1] != 'taken':
                break
        self.assertEqual(outs, ['taken', 'fall'])                                     # d7 went negative: MI true

    def test_unsupported_variants_raise(self):
        for words in ([0x0080, 0, 0x0001],          # ORI.L #imm,D0
                      [0x0468, 0x0001, 0x0004],     # SUBI.W #imm,d16(A0)
                      [0x023C, 0x00FF],             # ANDI #imm,CCR
                      [0x4468],                     # NEG.W d16(A0)
                      [0x4E72, 0x2700]):            # STOP
            c = cpu_with(words)
            with self.assertRaises(mini68k.Unsupported, msg=[hex(w) for w in words]):
                c.step({}, {})


if __name__ == '__main__':
    unittest.main()
