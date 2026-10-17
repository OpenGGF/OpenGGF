#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R16 (2026-10-17; frozen at R16 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: as mini68k.py (adds ASR #n, Scc Dn and the two JMP forms only).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""mini68k forms used by ApplyDeformation and AIZ1_Deform (kos46 research R16, 2026-10-17).

Adds, with real 68000 semantics, only: ASR.W|L #n,Dn; Scc Dn (outcome 'taken' when the condition is true, so the
GPGX +2 for a true Scc Dn can be charged: m68kops.h m68k_op_s*_8_d USE_CYCLES(CYC_SCC_R_TRUE)); JMP d16(PC) and
JMP (xxx).l. Everything else falls through to render_forms -> nem_forms -> vdp_forms -> mini68k, which
raise Unsupported for any other form.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import render_forms  # noqa: E402
import mini68k  # noqa: E402

M, SZ, sx = mini68k.M, mini68k.SZ, mini68k.sx


class CPU(render_forms.CPU):
    def step(self, open_calls, open_terms):
        pc = self.pc
        op = self.word(pc)
        top, mode, reg = op >> 12, (op >> 3) & 7, op & 7
        sb = (op >> 6) & 3
        if top == 0xE and sb in (1, 2) and not op & 0x20 and ((op >> 3) & 3) == 0 and not op & 0x100:   # ASR #n
            size = 'bwl'[sb]
            n, m = SZ[size]
            cnt = ((op >> 9) & 7) or 8
            v = sx(self.d[reg], size)
            out = (v >> (cnt - 1)) & 1
            res = (v >> cnt) & m
            self.put(('d', reg), size, res)
            self.nz(res, size)
            self.v = 0
            self.c = self.x = out
            self.pc = pc + 2
            return 'seq'
        if top == 5 and sb == 3 and mode == 0:                                       # Scc Dn
            true = self.cond((op >> 8) & 0xF)
            self.put(('d', reg), 'b', 0xFF if true else 0)
            self.pc = pc + 2
            return 'taken' if true else 'seq'
        if (op & 0xFFFF) in (0x4EFA, 0x4EF9):                                        # JMP d16(PC) / JMP (xxx).l
            if op == 0x4EFA:
                self.pc = (pc + 2 + sx(self.word(pc + 2), 'w')) & 0xFFFFFF
            else:
                self.pc = self.read(pc + 2, 'l') & 0xFFFFFF
            if self.pc in open_calls:
                raise mini68k.Unsupported('jmp to open routine %06X' % self.pc)
            return 'seq'
        return super().step(open_calls, open_terms)
