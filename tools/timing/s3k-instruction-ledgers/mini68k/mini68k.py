#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R10 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: caller-supplied ROM image, private RAM model and per-instruction cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Minimal fail-closed 68000 interpreter for pricing one ROM routine (kos46 research, 2026-10-17).

Executes only the opcode forms listed in `step` with real register/flag/memory semantics so that every branch and
loop count follows from the input values; anything else raises. Instruction time per execution comes from the
caller's cost function (pinned GPGX 051d430d table + dynamic rules, or Motorola). VDP port accesses ($C00000-$C0001F)
are accepted as writes with no state; their FIFO/DMA stalls are separate ledger terms. Calls to addresses listed in
`open_calls` are not entered and are reported as open terms.
"""

M = 0xFFFFFFFF
SZ = {'b': (1, 0xFF), 'w': (2, 0xFFFF), 'l': (4, M)}


class Unsupported(Exception):
    pass


def sx(v, size):
    n, m = SZ[size]
    v &= m
    return v - (m + 1) if v >> (8 * n - 1) else v


class CPU:
    def __init__(self, rom, ram=None):
        self.rom = rom
        self.ram = ram if ram is not None else bytearray(0x10000)
        self.d = [0] * 8
        self.a = [0] * 8
        self.a[7] = 0xFFFE00
        self.x = self.n = self.z = self.v = self.c = 0
        self.pc = 0
        self.vdp_writes = 0

    # -- memory
    def read(self, addr, size):
        n = SZ[size][0]
        addr &= 0xFFFFFF
        v = 0
        for k in range(n):
            a = addr + k
            if a >= 0xFF0000:
                b = self.ram[a & 0xFFFF]
            elif a < len(self.rom):
                b = self.rom[a]
            else:
                raise Unsupported('read at %06X' % a)
            v = v << 8 | b
        return v

    def write(self, addr, size, v):
        n = SZ[size][0]
        addr &= 0xFFFFFF
        if 0xC00000 <= addr <= 0xC0001F:
            self.vdp_writes += 1
            return
        if addr < 0xFF0000:
            raise Unsupported('write at %06X' % addr)
        for k in range(n):
            self.ram[(addr + k) & 0xFFFF] = (v >> (8 * (n - 1 - k))) & 0xFF

    def word(self, a):
        return self.read(a, 'w')

    # -- effective addresses: returns (kind, value) where kind in d, a, mem, imm
    def ea(self, mode, reg, size, ext):
        if mode == 0:
            return ('d', reg), ext
        if mode == 1:
            return ('a', reg), ext
        if mode == 2:
            return ('mem', self.a[reg]), ext
        if mode == 3:
            n = SZ[size][0] if not (reg == 7 and size == 'b') else 2
            loc = ('mem', self.a[reg])
            self.a[reg] = (self.a[reg] + n) & M
            return loc, ext
        if mode == 4:
            n = SZ[size][0] if not (reg == 7 and size == 'b') else 2
            self.a[reg] = (self.a[reg] - n) & M
            return ('mem', self.a[reg]), ext
        if mode == 5:
            return ('mem', (self.a[reg] + sx(self.word(ext), 'w')) & M), ext + 2
        if mode == 6:
            return ('mem', (self.a[reg] + self.index(ext)) & M), ext + 2
        if mode == 7:
            if reg == 0:
                return ('mem', sx(self.word(ext), 'w') & M), ext + 2
            if reg == 1:
                return ('mem', self.read(ext, 'l')), ext + 4
            if reg == 2:
                return ('mem', (ext + sx(self.word(ext), 'w')) & M), ext + 2
            if reg == 3:
                return ('mem', (ext + self.index(ext)) & M), ext + 2
            if reg == 4:
                if size == 'l':
                    return ('imm', self.read(ext, 'l')), ext + 4
                return ('imm', self.word(ext) & SZ[size][1]), ext + 2
        raise Unsupported('ea %d/%d' % (mode, reg))

    def index(self, ext):
        w = self.word(ext)
        r = (w >> 12) & 7
        v = self.a[r] if w & 0x8000 else self.d[r]
        v = v if w & 0x800 else sx(v, 'w')
        return v + sx(w & 0xFF, 'b')

    def get(self, loc, size):
        k, v = loc
        if k == 'd':
            return self.d[v] & SZ[size][1]
        if k == 'a':
            return self.a[v] & SZ[size][1]
        if k == 'imm':
            return v
        return self.read(v, size)

    def put(self, loc, size, val):
        k, v = loc
        m = SZ[size][1]
        if k == 'd':
            self.d[v] = (self.d[v] & ~m & M) | (val & m)
        elif k == 'a':
            self.a[v] = val & M
        else:
            self.write(v, size, val)

    def nz(self, v, size):
        n, m = SZ[size]
        v &= m
        self.n, self.z = v >> (8 * n - 1), int(v == 0)

    def cond(self, cc):
        n, z, v, c = self.n, self.z, self.v, self.c
        return {0: True, 1: False, 2: not c and not z, 3: c or z, 4: not c, 5: c, 6: not z, 7: z, 8: not v, 9: v,
                10: not n, 11: n, 12: n == v, 13: n != v, 14: not z and n == v, 15: z or n != v}[cc]

    # -- one instruction; returns outcome 'seq'|'taken'|'fall'
    def step(self, open_calls, open_terms):
        pc = self.pc
        op = self.word(pc)
        top = op >> 12
        mode, reg = (op >> 3) & 7, op & 7
        size_bits = (op >> 6) & 3
        nxt = pc + 2
        if op == 0x4E71:
            self.pc = nxt
            return 'seq'
        if op == 0x4E75:
            self.pc = self.read(self.a[7], 'l') & 0xFFFFFF
            self.a[7] = (self.a[7] + 4) & M
            return 'seq'
        if top in (1, 2, 3):                               # MOVE / MOVEA
            size = {1: 'b', 3: 'w', 2: 'l'}[top]
            src, nxt = self.ea(mode, reg, size, nxt)
            val = self.get(src, size)
            dmode, dreg = (op >> 6) & 7, (op >> 9) & 7
            dst, nxt = self.ea(dmode, dreg, size, nxt)
            if dmode == 1:
                self.a[dreg] = sx(val, size) & M
            else:
                self.put(dst, size, val)
                self.nz(val, size)
                self.v = self.c = 0
            self.pc = nxt
            return 'seq'
        if top == 7 and not op & 0x100:                    # MOVEQ
            r = (op >> 9) & 7
            self.d[r] = sx(op & 0xFF, 'b') & M
            self.nz(self.d[r], 'l'); self.v = self.c = 0
            self.pc = nxt
            return 'seq'
        if (op & 0xFFB8) == 0x4880:                        # EXT.W / EXT.L
            if op & 0x40:
                self.d[reg] = sx(self.d[reg], 'w') & M; self.nz(self.d[reg], 'l')
            else:
                self.put(('d', reg), 'w', sx(self.d[reg], 'b')); self.nz(self.d[reg], 'w')
            self.v = self.c = 0
            self.pc = nxt
            return 'seq'
        if (op & 0xF1C0) == 0x41C0:                        # LEA
            loc, nxt = self.ea(mode, reg, 'l', nxt)
            self.a[(op >> 9) & 7] = loc[1] & M
            self.pc = nxt
            return 'seq'
        if (op & 0xFF00) in (0x4200, 0x4A00) and size_bits != 3:   # CLR / TST
            size = 'bwl'[size_bits]
            loc, nxt = self.ea(mode, reg, size, nxt)
            if (op & 0xFF00) == 0x4200:
                self.put(loc, size, 0); self.n, self.z = 0, 1
            else:
                self.nz(self.get(loc, size), size)
            self.v = self.c = 0
            self.pc = nxt
            return 'seq'
        if top == 0 and ((op >> 9) & 7) in (3, 6) and size_bits != 3 and not op & 0x100:   # ADDI / CMPI
            size = 'bwl'[size_bits]
            imm_loc, nxt = self.ea(7, 4, size, nxt)
            imm = imm_loc[1]
            loc, nxt = self.ea(mode, reg, size, nxt)
            cur = self.get(loc, size)
            if (op >> 9) & 7 == 3:
                self.arith(loc, size, cur, imm, add=True)
            else:
                self.compare(cur, imm, size)
            self.pc = nxt
            return 'seq'
        if top == 5 and size_bits != 3:                    # ADDQ / SUBQ
            size = 'bwl'[size_bits]
            q = ((op >> 9) & 7) or 8
            loc, nxt = self.ea(mode, reg, size, nxt)
            if mode == 1:
                self.a[reg] = (self.a[reg] + (-q if op & 0x100 else q)) & M
            else:
                self.arith(loc, size, self.get(loc, size), q, add=not op & 0x100)
            self.pc = nxt
            return 'seq'
        if top == 5 and size_bits == 3 and mode == 1:      # DBcc
            disp = sx(self.word(nxt), 'w')
            if self.cond((op >> 8) & 0xF):
                self.pc = nxt + 2
                return 'fall'
            v = (self.d[reg] - 1) & 0xFFFF
            self.d[reg] = (self.d[reg] & 0xFFFF0000) | v
            if v == 0xFFFF:
                self.pc = nxt + 2
                return 'fall'
            self.pc = (nxt + disp) & 0xFFFFFF
            return 'taken'
        if top == 6:                                       # Bcc / BRA / BSR
            cc = (op >> 8) & 0xF
            d8 = op & 0xFF
            if d8 == 0:
                disp, after = sx(self.word(nxt), 'w'), nxt + 2
            else:
                disp, after = sx(d8, 'b'), nxt
            target = (nxt + disp) & 0xFFFFFF
            if cc == 1:                                    # BSR
                if target in open_calls:
                    open_terms[open_calls[target]] = open_terms.get(open_calls[target], 0) + 1
                    self.pc = after
                    return 'seq'
                self.a[7] = (self.a[7] - 4) & M
                self.write(self.a[7], 'l', after)
                self.pc = target
                return 'seq'
            if cc == 0 or self.cond(cc):
                self.pc = target
                return 'taken' if cc else 'seq'
            self.pc = after
            return 'fall'
        if top in (9, 0xD) and ((op >> 6) & 7) in (0, 1, 2):   # SUB / ADD <ea>,Dn
            size = 'bwl'[(op >> 6) & 3]
            src, nxt = self.ea(mode, reg, size, nxt)
            r = (op >> 9) & 7
            self.arith(('d', r), size, self.get(('d', r), size), self.get(src, size), add=top == 0xD)
            self.pc = nxt
            return 'seq'
        if top == 0xE and size_bits != 3 and not op & 0x20 and ((op >> 3) & 3) == 1:   # LSL/LSR #n,Dn
            size = 'bwl'[size_bits]
            cnt = ((op >> 9) & 7) or 8
            n, m = SZ[size]
            v = self.d[reg] & m
            if op & 0x100:
                out = (v >> (8 * n - cnt)) & 1
                v = (v << cnt) & m
            else:
                out = (v >> (cnt - 1)) & 1
                v >>= cnt
            self.put(('d', reg), size, v)
            self.nz(v, size); self.v = 0; self.c = self.x = out
            self.pc = nxt
            return 'seq'
        if (op & 0xFFC0) in (0x4E80,):                      # JSR
            loc, nxt = self.ea(mode, reg, 'l', nxt)
            if loc[1] in open_calls:
                open_terms[open_calls[loc[1]]] = open_terms.get(open_calls[loc[1]], 0) + 1
                self.pc = nxt
                return 'seq'
            self.a[7] = (self.a[7] - 4) & M
            self.write(self.a[7], 'l', nxt)
            self.pc = loc[1] & 0xFFFFFF
            return 'seq'
        raise Unsupported('opcode %04X at %06X' % (op, pc))

    def arith(self, loc, size, a, b, add):
        n, m = SZ[size]
        r = (a + b) if add else (a - b)
        res = r & m
        sa, sb, sr = a >> (8 * n - 1) & 1, b >> (8 * n - 1) & 1, res >> (8 * n - 1) & 1
        if add:
            self.c = int(r > m)
            self.v = int(sa == sb and sr != sa)
        else:
            self.c = int(b > a)
            self.v = int(sa != sb and sr != sa)
        self.x = self.c
        self.put(loc, size, res)
        self.nz(res, size)

    def compare(self, a, b, size):
        n, m = SZ[size]
        res = (a - b) & m
        sa, sb, sr = a >> (8 * n - 1) & 1, b >> (8 * n - 1) & 1, res >> (8 * n - 1) & 1
        self.c = int(b > a)
        self.v = int(sa != sb and sr != sa)
        self.nz(res, size)
