#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R12 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: as mini68k.py (adds the Nemesis decoder forms only).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""mini68k forms used only by the S3K Nemesis queue decoder (kos46 research, 2026-10-17).

Adds, with real 68000 register/flag semantics and nothing else:
  LSL/LSR.W|L Dx,Dy (count = Dx & 63)      Nem_Process_Compressed_Data $1604, Nem_PCD_InlineData $1650, BCT $16A8
  ASL.W #n,Dn                                Process_Nem_Queue_Init, refill paths
  OR.B|W Dn,Dn / EOR.L Dn,Dn                 Nem_PCD_WritePixel, Nem_BCT, Nem_PCD_WriteRowToVDP_XOR
  JMP (An)                                   Nem_PCD_WritePixel -> (a3) row writer
  ADDA.W #imm,An                             Process_Nem_Queue_Init XOR writer select
Every other form falls through to vdp_forms -> mini68k, which raise Unsupported. VDP data-port longword
writes are recorded (value only) so decoded rows can be compared; the private model RAM is the only state touched.
`shift_count` exposes the register count of the instruction about to execute so the ledger can charge
GPGX m68k_op_lsr_16_r & co. (m68kops.h 9614-9623: USE_CYCLES(shift * CYC_SHIFT), CYC_SHIFT = 2 * MUL).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import vdp_forms  # noqa: E402
import mini68k  # noqa: E402

M = mini68k.M
SZ = mini68k.SZ


def is_reg_shift(op):
    return (op >> 12) == 0xE and ((op >> 6) & 3) != 3 and op & 0x20 and ((op >> 3) & 3) == 1


class CPU(vdp_forms.CPU):
    def __init__(self, rom, ram=None):
        super().__init__(rom, ram)
        self.data_writes = []
        self.control_writes = []

    def write(self, addr, size, v):
        a = addr & 0xFFFFFF
        if a == 0xC00000:
            if size != 'l':
                raise mini68k.Unsupported('non-longword VDP data write')
            self.data_writes.append(v & M)
        elif a == 0xC00004:
            self.control_writes.append((size, v & SZ[size][1]))
        super().write(addr, size, v)

    def shift_count(self):
        op = self.word(self.pc)
        return self.d[(op >> 9) & 7] & 63 if is_reg_shift(op) else None

    def step(self, open_calls, open_terms):
        op = self.word(self.pc)
        top, reg, mode = op >> 12, op & 7, (op >> 3) & 7
        sb = (op >> 6) & 3
        if is_reg_shift(op) and sb in (1, 2):                          # LSL/LSR.W|L Dx,Dy
            size = 'bwl'[sb]
            n, m = SZ[size]
            cnt = self.d[(op >> 9) & 7] & 63
            v = self.d[reg] & m
            out = 0
            if cnt:
                if op & 0x100:
                    out = (v >> (8 * n - cnt)) & 1 if cnt <= 8 * n else 0
                    v = (v << cnt) & m
                else:
                    out = (v >> (cnt - 1)) & 1 if cnt <= 8 * n else 0
                    v = v >> cnt if cnt < 8 * n else 0
                self.c = self.x = out
            else:
                self.c = 0
            self.put(('d', reg), size, v)
            self.nz(v, size); self.v = 0
            self.pc += 2
            return 'seq'
        if top == 0xE and sb == 1 and not op & 0x20 and ((op >> 3) & 3) == 0 and op & 0x100:   # ASL.W #n,Dn
            cnt = ((op >> 9) & 7) or 8
            v = self.d[reg] & 0xFFFF
            res = (v << cnt) & 0xFFFF
            lost = v >> (16 - cnt)                                    # bits shifted through bit 15
            msb_bits = (v >> (15 - cnt)) & ((1 << (cnt + 1)) - 1)     # every value bit 15 took during the shift
            self.v = int(msb_bits not in (0, (1 << (cnt + 1)) - 1))
            self.c = self.x = lost & 1
            self.put(('d', reg), 'w', res)
            self.nz(res, 'w')
            self.pc += 2
            return 'seq'
        if top == 8 and ((op >> 6) & 7) in (0, 1) and mode == 0:         # OR.B|W Dy,Dx
            size = 'bw'[(op >> 6) & 7]
            r = (op >> 9) & 7
            val = (self.d[r] | self.d[reg]) & SZ[size][1]
            self.put(('d', r), size, val)
            self.nz(val, size); self.v = self.c = 0
            self.pc += 2
            return 'seq'
        if top == 0xB and ((op >> 6) & 7) == 6 and mode == 0:             # EOR.L Dx,Dy
            val = (self.d[(op >> 9) & 7] ^ self.d[reg]) & M
            self.d[reg] = val
            self.nz(val, 'l'); self.v = self.c = 0
            self.pc += 2
            return 'seq'
        if (op & 0xFFF8) == 0x4ED0:                                       # JMP (An)
            self.pc = self.a[reg] & 0xFFFFFF
            return 'seq'
        if (op & 0xF1FF) == 0xD0FC:                                       # ADDA.W #imm,An
            self.a[(op >> 9) & 7] = (self.a[(op >> 9) & 7] + mini68k.sx(self.word(self.pc + 2), 'w')) & M
            self.pc += 4
            return 'seq'
        return super().step(open_calls, open_terms)
