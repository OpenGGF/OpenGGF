#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R17 (2026-10-17; frozen at R17 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: none beyond this directory (hand-assembled synthetic vectors).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Opcode / flag / EA tests for palette_forms.py (kos46 research R17) with old-red checks: the frozen
deform_forms.CPU must refuse each new form; neighbours must still refuse."""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import palette_forms as pf  # noqa: E402
import deform_forms as df  # noqa: E402
import mini68k  # noqa: E402


def make(cls, words, setup=None):
    rom = bytearray(0x2000)
    for k, w in enumerate(words):
        rom[0x1000 + 2 * k:0x1002 + 2 * k] = w.to_bytes(2, 'big')
    c = cls(bytes(rom))
    c.pc = 0x1000
    if setup:
        setup(c)
    return c


NEW = ([0x0458, 0x0020], [0x0403, 0x0020], [0x0468, 0x0001, 0x0004], [0x46FC, 0x2300])


class PaletteForms(unittest.TestCase):
    def test_old_model_refused(self):
        for words in NEW:
            with self.assertRaises(mini68k.Unsupported, msg=[hex(w) for w in words]):
                make(df.CPU, words, lambda c: c.a.__setitem__(0, 0xFFFF8000)).step({}, {})

    def test_subi_memory_postinc_and_byte_register(self):
        c = make(pf.CPU, [0x0458, 0x0200], lambda c: (c.a.__setitem__(0, 0xFFFF8000), c.write(0xFFFF8000, 'w', 0x0E00)))
        c.step({}, {})
        self.assertEqual((c.read(0xFFFF8000, 'w'), c.a[0], c.c, c.z, c.n, c.pc), (0x0C00, 0xFFFF8002, 0, 0, 0, 0x1004))
        c = make(pf.CPU, [0x0403, 0x0020], lambda c: c.d.__setitem__(3, 0x12340020))
        c.step({}, {})
        self.assertEqual((c.d[3], c.z, c.c), (0x12340000, 1, 0))
        c = make(pf.CPU, [0x0468, 0x0001, 0x0004], lambda c: (c.a.__setitem__(0, 0xFFFF8000), c.write(0xFFFF8004, 'w', 0x8000)))
        c.step({}, {})
        self.assertEqual((c.read(0xFFFF8004, 'w'), c.v, c.pc), (0x7FFF, 1, 0x1006))       # d16(An), signed overflow

    def test_move_to_sr(self):
        c = make(pf.CPU, [0x46FC, 0x2315])
        c.step({}, {})
        self.assertEqual((c.sr_mask, c.x, c.n, c.z, c.v, c.c, c.pc), (3, 1, 0, 1, 0, 1, 0x1004))

    def test_neighbours_still_refused(self):
        for words in ([0x0498, 0, 0x20],     # SUBI.L #,(a0)+
                      [0x0478, 0x0020, 0x8000],   # SUBI.W #,(xxx).w
                      [0x46C0],              # MOVE D0,SR
                      [0x40C0]):             # MOVE SR,D0
            with self.assertRaises(mini68k.Unsupported, msg=[hex(w) for w in words]):
                make(pf.CPU, words, lambda c: c.a.__setitem__(0, 0xFFFF8000)).step({}, {})


if __name__ == '__main__':
    unittest.main()
