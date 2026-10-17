#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: as mini68k.py (adds SWAP, ORI.W/ANDI.W Dn only).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""mini68k plus the three register forms VInt_VRAMWrite / Process_Nem_Queue_Main need (kos46 research, 2026-10-17).

Adds exactly SWAP Dn, ORI.W #imm,Dn and ANDI.W #imm,Dn with real flag semantics; every other form is delegated to
mini68k.CPU, which raises Unsupported for anything it does not execute. Not a general extension point.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mini68k  # noqa: E402


class CPU(mini68k.CPU):
    def step(self, open_calls, open_terms):
        op = self.word(self.pc)
        r = op & 7
        if (op & 0xFFF8) == 0x4840:                                   # SWAP Dn
            v = self.d[r]
            self.d[r] = ((v << 16) | (v >> 16)) & mini68k.M
            self.nz(self.d[r], 'l'); self.v = self.c = 0
            self.pc += 2
            return 'seq'
        if (op & 0xFFF8) in (0x0040, 0x0240):                        # ORI.W / ANDI.W #imm,Dn
            imm = self.word(self.pc + 2)
            cur = self.d[r] & 0xFFFF
            val = (cur | imm) if op & 0x0200 == 0 else (cur & imm)
            self.put(('d', r), 'w', val)
            self.nz(val, 'w'); self.v = self.c = 0
            self.pc += 4
            return 'seq'
        return super().step(open_calls, open_terms)
