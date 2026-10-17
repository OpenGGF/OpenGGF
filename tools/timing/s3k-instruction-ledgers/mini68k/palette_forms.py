#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R17 (2026-10-17; frozen at R17 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: as mini68k.py (adds SUBI.B/W #imm and MOVE #imm,SR only).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""mini68k forms used by the S3K palette fade routines (kos46 research R17, 2026-10-17).

Local wrapper over the frozen deform_forms.CPU (not edited). Adds only SUBI.B|W #imm,<Dn|(An)|(An)+|d16(An)>
with real flags (render_forms accepted SUBI.W #imm,Dn alone and refused the rest) and MOVE #imm,SR (Wait_VSync's
move #$2300,sr: sets the condition codes from the low byte and records the interrupt mask; no interrupt is modelled). Every other form falls through to
the frozen chain (deform_forms -> render_forms -> nem_forms -> vdp_forms -> mini68k), which raises Unsupported.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import deform_forms  # noqa: E402
import mini68k  # noqa: E402

SZ = mini68k.SZ


class CPU(deform_forms.CPU):
    def step(self, open_calls, open_terms):
        pc = self.pc
        op = self.word(pc)
        mode, reg, sb = (op >> 3) & 7, op & 7, (op >> 6) & 3
        if (op >> 8) == 0x04 and sb in (0, 1) and mode in (0, 2, 3, 5):           # SUBI.B|W #imm,<ea>
            size = 'bw'[sb]
            imm = self.word(pc + 2) & SZ[size][1]
            loc, nxt = self.ea(mode, reg, size, pc + 4)
            self.arith(loc, size, self.get(loc, size), imm, add=False)
            self.pc = nxt
            return 'seq'
        if op == 0x46FC:                                                          # MOVE #imm,SR
            v = self.word(pc + 2)
            self.sr_mask = (v >> 8) & 7
            self.x, self.n, self.z, self.v, self.c = (v >> 4) & 1, (v >> 3) & 1, (v >> 2) & 1, (v >> 1) & 1, v & 1
            self.pc = pc + 4
            return 'seq'
        return super().step(open_calls, open_terms)
