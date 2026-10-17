#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R16 (2026-10-17; frozen at R16 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: none beyond this directory (hand-assembled synthetic vectors).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Opcode / flag / EA tests for deform_forms.py (kos46 research R16), with old-red checks: the previous model
(render_forms.CPU) must raise Unsupported on every newly added form, and the new model must execute it with
68000 semantics computed independently here. Unsupported neighbours must still raise."""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import deform_forms as df  # noqa: E402
import render_forms as rf  # noqa: E402
import mini68k  # noqa: E402


def make(cls, words, setup=None):
    rom = bytearray(0x3000)
    for k, w in enumerate(words):
        rom[0x1000 + 2 * k:0x1002 + 2 * k] = w.to_bytes(2, 'big')
    c = cls(bytes(rom))
    c.pc = 0x1000
    if setup:
        setup(c)
    return c


NEW = ([0xE240], [0xEA80], [0x5BC4], [0x4EFA, 0x0100], [0x4EF9, 0x0000, 0x2000])


class DeformForms(unittest.TestCase):
    def test_old_model_refused_every_new_form(self):
        for words in NEW:
            with self.assertRaises(mini68k.Unsupported, msg=[hex(w) for w in words]):
                make(rf.CPU, words).step({}, {})

    def test_asr_word_and_long(self):
        c = make(df.CPU, [0xE240], lambda c: c.d.__setitem__(0, 0x1234FFFB))     # asr.w #1: -5 -> -3, C=X=1
        c.step({}, {})
        self.assertEqual((c.d[0], c.c, c.x, c.n, c.z, c.v), (0x1234FFFD, 1, 1, 1, 0, 0))
        c = make(df.CPU, [0xEA80], lambda c: c.d.__setitem__(0, 0x80000000))     # asr.l #5
        c.step({}, {})
        self.assertEqual((c.d[0], c.c, c.n), (0xFC000000, 0, 1))
        c = make(df.CPU, [0xE682], lambda c: c.d.__setitem__(2, 0x00000004))     # asr.l #3: 4 -> 0, C = bit 2
        c.step({}, {})
        self.assertEqual((c.d[2], c.c, c.z), (0, 1, 1))

    def test_scc_value_and_outcome(self):
        c = make(df.CPU, [0x5BC4], lambda c: (c.d.__setitem__(4, 0x12345600), setattr(c, 'n', 1)))
        self.assertEqual(c.step({}, {}), 'taken')
        self.assertEqual(c.d[4], 0x123456FF)
        c = make(df.CPU, [0x5BC4], lambda c: (c.d.__setitem__(4, 0x123456AA), setattr(c, 'n', 0)))
        self.assertEqual(c.step({}, {}), 'seq')
        self.assertEqual(c.d[4], 0x12345600)

    def test_jmp_targets(self):
        c = make(df.CPU, [0x4EFA, 0xFF00])
        c.step({}, {})
        self.assertEqual(c.pc, (0x1002 - 0x100) & 0xFFFFFF)
        c = make(df.CPU, [0x4EF9, 0x0000, 0x2000])
        c.step({}, {})
        self.assertEqual(c.pc, 0x2000)

    def test_neighbours_still_refused(self):
        for words in ([0xE0D0],          # asr.w (a0): memory shift
                      [0x5BD0],          # smi (a0)
                      [0x4ED0 | 0x0028, 0x0004],   # jmp d16(a0)
                      [0xE6A0]):         # asr.l d3,d0: register count
            with self.assertRaises(mini68k.Unsupported, msg=[hex(w) for w in words]):
                make(df.CPU, words).step({}, {})


if __name__ == '__main__':
    unittest.main()
