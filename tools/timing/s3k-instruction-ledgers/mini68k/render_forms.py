#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R15 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: as mini68k.py (adds the renderer forms only).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""mini68k forms used by the S3K sprite renderer and Draw_Sprite (kos46 research R15, 2026-10-17).

Adds, with real 68000 semantics, only:
  ORI/ANDI/EORI.B|W #imm,<Dn|(An)|(An)+|d16(An)>, SUBI.W #imm,Dn, BTST #imm,Dn, AND.W/CMP.B|W|L <ea>,Dn,
  CMPA.W|L <ea>,An, ADDA.W <ea>,An, NEG.W Dn, and DBcc that reports 'expired' (counter ran out) separately from
  'fall' (condition true) so GPGX's base/base+2 split can be charged.
Everything else falls through to nem_forms -> vdp_forms -> mini68k, which raise Unsupported. VDP and
SAT writes are ordinary RAM writes in the private model; nothing here touches engine or emulator memory.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nem_forms  # noqa: E402
import mini68k  # noqa: E402

M, SZ, sx = mini68k.M, mini68k.SZ, mini68k.sx


class CPU(nem_forms.CPU):
    def logic_flags(self, v, size):
        self.nz(v, size)
        self.v = self.c = 0

    def step(self, open_calls, open_terms):
        pc = self.pc
        op = self.word(pc)
        top, mode, reg = op >> 12, (op >> 3) & 7, op & 7
        sb = (op >> 6) & 3
        # ORI / ANDI / SUBI / EORI #imm,<ea> (.b/.w), data-alterable modes 0,2,3,5 only
        if top == 0 and ((op >> 9) & 7) in (0, 1, 2, 5) and sb in (0, 1) and mode in (0, 2, 3, 5) and not op & 0x100:
            kind = (op >> 9) & 7
            if kind == 2 and (sb != 1 or mode != 0):
                raise mini68k.Unsupported('SUBI form %04X' % op)
            size = 'bw'[sb]
            imm = self.word(pc + 2) & SZ[size][1]
            loc, nxt = self.ea(mode, reg, size, pc + 4)
            cur = self.get(loc, size)
            if kind == 2:
                self.arith(loc, size, cur, imm, add=False)
            else:
                val = {0: cur | imm, 1: cur & imm, 5: cur ^ imm}[kind]
                self.put(loc, size, val)
                self.logic_flags(val, size)
            self.pc = nxt
            return 'seq'
        if (op & 0xFFF8) == 0x0800:                                        # BTST #imm,Dn
            bit = self.word(pc + 2) & 31
            self.z = int(not (self.d[reg] >> bit) & 1)
            self.pc = pc + 4
            return 'seq'
        if top == 0xC and ((op >> 6) & 7) == 1:                            # AND.W <ea>,Dn
            src, nxt = self.ea(mode, reg, 'w', pc + 2)
            r = (op >> 9) & 7
            val = self.get(('d', r), 'w') & self.get(src, 'w')
            self.put(('d', r), 'w', val)
            self.logic_flags(val, 'w')
            self.pc = nxt
            return 'seq'
        if top == 0xB and ((op >> 6) & 7) in (0, 1, 2):                    # CMP <ea>,Dn
            size = 'bwl'[(op >> 6) & 3]
            src, nxt = self.ea(mode, reg, size, pc + 2)
            self.compare(self.get(('d', (op >> 9) & 7), size), self.get(src, size), size)
            self.pc = nxt
            return 'seq'
        if top == 0xB and ((op >> 6) & 7) in (3, 7):                       # CMPA.W / CMPA.L <ea>,An
            size = 'w' if ((op >> 6) & 7) == 3 else 'l'
            src, nxt = self.ea(mode, reg, size, pc + 2)
            v = self.get(src, size)
            v = sx(v, 'w') & M if size == 'w' else v
            self.compare(self.a[(op >> 9) & 7] & M, v, 'l')
            self.pc = nxt
            return 'seq'
        if top == 0xD and ((op >> 6) & 7) == 3:                            # ADDA.W <ea>,An
            src, nxt = self.ea(mode, reg, 'w', pc + 2)
            r = (op >> 9) & 7
            self.a[r] = (self.a[r] + sx(self.get(src, 'w'), 'w')) & M
            self.pc = nxt
            return 'seq'
        if (op & 0xFFF8) == 0x4440:                                        # NEG.W Dn
            v = self.d[reg] & 0xFFFF
            res = (-v) & 0xFFFF
            self.put(('d', reg), 'w', res)
            self.nz(res, 'w')
            self.v = int(v == 0x8000)
            self.c = self.x = int(v != 0)
            self.pc = pc + 2
            return 'seq'
        if top == 5 and sb == 3 and mode == 1:                             # DBcc with expired outcome
            disp = sx(self.word(pc + 2), 'w')
            if self.cond((op >> 8) & 0xF):
                self.pc = pc + 4
                return 'fall'
            v = (self.d[reg] - 1) & 0xFFFF
            self.d[reg] = (self.d[reg] & 0xFFFF0000) | v
            if v == 0xFFFF:
                self.pc = pc + 4
                return 'expired'
            self.pc = (pc + 2 + disp) & 0xFFFFFF
            return 'taken'
        return super().step(open_calls, open_terms)
