#!/usr/bin/env python3
"""MC68000 instruction decoder and Motorola-table execution times, read from ROM bytes.

Purpose: decode instructions at given addresses from caller-supplied ROM bytes and return each instruction's
68000 execution time from the Motorola MC68000 User's Manual section 8 tables (no wait states).
Timing is a Cost(lo, hi, why): exact when lo == hi, an explicit interval when the time depends on an operand
(register shift count, MULU/MULS/DIVU/DIVS operand, dynamic bit number, DBcc true-vs-expired fall-through).
Conditional branches and DBcc expose separate 'taken' and 'fall' costs; callers supply outcome counts.
Unsupported encodings raise Unsupported; there is no default or zero timing.
Inputs: ROM bytes only (identity is verified by the caller). Origin: OpenGGF S3K ending Kos CPU-budget
research (known-bug "Ending Reunion Kos Service Count"), 2026-10-16.
"""
from dataclasses import dataclass, field

class Unsupported(Exception):
    pass

@dataclass
class Cost:
    lo: int
    hi: int
    why: str = ''
    def exact(self):
        return self.lo == self.hi

@dataclass
class Insn:
    pc: int
    length: int
    name: str
    size: str
    kind: str                 # 'seq' | 'cond' | 'dbcc' | 'jump' | 'call' | 'ijump' | 'icall' | 'ret' | 'rte' | 'stop'
    target: int = None        # static target (cond/dbcc/jump/call)
    cost: Cost = None         # seq/jump/call/ijump/icall/ret/rte cost
    taken: Cost = None        # cond/dbcc: cost when branch taken
    fall: Cost = None         # cond/dbcc: cost when falling through
    src: str = ''
    dst: str = ''
    abs_addrs: list = field(default_factory=list)   # absolute memory operands (for bus-latency accounting)

# --- effective-address calculation times (Motorola Table 8-1): (byte/word, long)
EA_TIME = {'Dn': (0, 0), 'An': (0, 0), '(An)': (4, 8), '(An)+': (4, 8), '-(An)': (6, 10), 'd16(An)': (8, 12),
           'd8(An,Xn)': (10, 14), 'abs.W': (8, 12), 'abs.L': (12, 16), 'd16(PC)': (8, 12), 'd8(PC,Xn)': (10, 14),
           '#': (4, 8)}
DST_ORDER = ['Dn', 'An', '(An)', '(An)+', '-(An)', 'd16(An)', 'd8(An,Xn)', 'abs.W', 'abs.L']
# MOVE byte/word (Table 8-2) and long (Table 8-3): rows = source mode, columns = DST_ORDER
MOVE_BW = {'Dn': [4, 4, 8, 8, 8, 12, 14, 12, 16], '(An)': [8, 8, 12, 12, 12, 16, 18, 16, 20],
           '-(An)': [10, 10, 14, 14, 14, 18, 20, 18, 22], 'd16(An)': [12, 12, 16, 16, 16, 20, 22, 20, 24],
           'd8(An,Xn)': [14, 14, 18, 18, 18, 22, 24, 22, 26], 'abs.W': [12, 12, 16, 16, 16, 20, 22, 20, 24],
           'abs.L': [16, 16, 20, 20, 20, 24, 26, 24, 28], '#': [8, 8, 12, 12, 12, 16, 18, 16, 20]}
MOVE_L = {'Dn': [4, 4, 12, 12, 12, 16, 18, 16, 20], '(An)': [12, 12, 20, 20, 20, 24, 26, 24, 28],
          '-(An)': [14, 14, 22, 22, 22, 26, 28, 26, 30], 'd16(An)': [16, 16, 24, 24, 24, 28, 30, 28, 32],
          'd8(An,Xn)': [18, 18, 26, 26, 26, 30, 32, 30, 34], 'abs.W': [16, 16, 24, 24, 24, 28, 30, 28, 32],
          'abs.L': [20, 20, 28, 28, 28, 32, 34, 32, 36], '#': [12, 12, 20, 20, 20, 24, 26, 24, 28]}
for t in (MOVE_BW, MOVE_L):
    t['An'] = t['Dn']; t['(An)+'] = t['(An)']; t['d16(PC)'] = t['d16(An)']; t['d8(PC,Xn)'] = t['d8(An,Xn)']
JMP_T = {'(An)': 8, 'd16(An)': 10, 'd8(An,Xn)': 14, 'abs.W': 10, 'abs.L': 12, 'd16(PC)': 10, 'd8(PC,Xn)': 14}
JSR_T = {'(An)': 16, 'd16(An)': 18, 'd8(An,Xn)': 22, 'abs.W': 18, 'abs.L': 20, 'd16(PC)': 18, 'd8(PC,Xn)': 22}
LEA_T = {'(An)': 4, 'd16(An)': 8, 'd8(An,Xn)': 12, 'abs.W': 8, 'abs.L': 12, 'd16(PC)': 8, 'd8(PC,Xn)': 12}
PEA_T = {'(An)': 12, 'd16(An)': 16, 'd8(An,Xn)': 20, 'abs.W': 16, 'abs.L': 20, 'd16(PC)': 16, 'd8(PC,Xn)': 20}
MOVEM_M2R = {'(An)': 12, '(An)+': 12, 'd16(An)': 16, 'd8(An,Xn)': 18, 'abs.W': 16, 'abs.L': 20, 'd16(PC)': 16, 'd8(PC,Xn)': 18}
MOVEM_R2M = {'(An)': 8, '-(An)': 8, 'd16(An)': 12, 'd8(An,Xn)': 14, 'abs.W': 12, 'abs.L': 16}
CC = ['t', 'f', 'hi', 'ls', 'cc', 'cs', 'ne', 'eq', 'vc', 'vs', 'pl', 'mi', 'ge', 'lt', 'gt', 'le']

def u16(b, a):
    return (b[a] << 8) | b[a + 1]

def s16(v):
    return v - 0x10000 if v & 0x8000 else v

def s8(v):
    return v - 0x100 if v & 0x80 else v

class Decoder:
    def __init__(self, rom: bytes):
        self.rom = rom

    def w(self, a):
        if a < 0 or a + 1 >= len(self.rom):
            raise Unsupported('address %06X outside ROM' % a)
        return u16(self.rom, a)

    def ea(self, mode, reg, size, ext_at, pc_of_ext):
        """Return (mode_name, ext_bytes, abs_address_or_None, pc_target_or_None)."""
        if mode == 0: return 'Dn', 0, None, None
        if mode == 1: return 'An', 0, None, None
        if mode == 2: return '(An)', 0, None, None
        if mode == 3: return '(An)+', 0, None, None
        if mode == 4: return '-(An)', 0, None, None
        if mode == 5: return 'd16(An)', 2, None, None
        if mode == 6: return 'd8(An,Xn)', 2, None, None
        if mode == 7:
            if reg == 0:
                v = s16(self.w(ext_at)) & 0xFFFFFF
                return 'abs.W', 2, v, None
            if reg == 1:
                v = ((self.w(ext_at) << 16) | self.w(ext_at + 2)) & 0xFFFFFF
                return 'abs.L', 4, v, None
            if reg == 2:
                return 'd16(PC)', 2, None, (pc_of_ext + s16(self.w(ext_at))) & 0xFFFFFF
            if reg == 3:
                return 'd8(PC,Xn)', 2, None, None
            if reg == 4:
                return '#', 4 if size == 'l' else 2, None, None
        raise Unsupported('ea mode %d reg %d' % (mode, reg))

    def decode(self, pc):
        op = self.w(pc)
        top = op >> 12
        try:
            return getattr(self, '_g%X' % top)(pc, op)
        except Unsupported:
            raise
        except (KeyError, IndexError) as e:
            raise Unsupported('opcode %04X at %06X (%s)' % (op, pc, e))

    # ---------- helpers
    def _mk(self, pc, length, name, size, cost, kind='seq', **kw):
        return Insn(pc, length, name, size, kind, cost=cost if isinstance(cost, Cost) else Cost(cost, cost), **kw)

    @staticmethod
    def _sz(bits):
        return {0: 'b', 1: 'w', 2: 'l'}.get(bits)

    @staticmethod
    def _et(m, size):
        return EA_TIME[m][1 if size == 'l' else 0]

    # ---------- 0000: bit ops, immediate ops, MOVEP
    def _g0(self, pc, op):
        mode, reg = (op >> 3) & 7, op & 7
        if op & 0x0100:  # dynamic bit op or MOVEP
            if mode == 1:
                opm = (op >> 6) & 3
                return self._mk(pc, 4, 'movep', 'wl'[opm & 1], 16 if (opm & 1) == 0 else 24)
            typ = (op >> 6) & 3
            name = ['btst', 'bchg', 'bclr', 'bset'][typ]
            m, n, a, _ = self.ea(mode, reg, 'b', pc + 2, pc + 2)
            if m == 'Dn':
                if typ == 0: c = Cost(6, 6)
                else:
                    base = {1: 8, 2: 10, 3: 8}[typ]
                    c = Cost(base - 2, base, 'dynamic bit number: -2 if bit<16')
            else:
                c = Cost((4 if typ == 0 else 8) + self._et(m, 'b'), (4 if typ == 0 else 8) + self._et(m, 'b'))
            return self._mk(pc, 2 + n, name, 'b' if m != 'Dn' else 'l', c, dst=m, abs_addrs=[a] if a is not None else [])
        sel = (op >> 9) & 7
        if sel == 4:  # static bit op
            typ = (op >> 6) & 3
            name = ['btst', 'bchg', 'bclr', 'bset'][typ]
            bit = self.w(pc + 2) & 0xFF
            m, n, a, _ = self.ea(mode, reg, 'b', pc + 4, pc + 4)
            if m == 'Dn':
                if typ == 0: c = 10
                else:
                    base = {1: 12, 2: 14, 3: 12}[typ]
                    c = base if (bit & 31) >= 16 else base - 2
            else:
                c = (8 if typ == 0 else 12) + self._et(m, 'b')
            return self._mk(pc, 4 + n, name, 'b', c, src='#', dst=m, abs_addrs=[a] if a is not None else [])
        size = self._sz((op >> 6) & 3)
        if size is None:
            raise Unsupported('imm op size 11 %04X' % op)
        name = {0: 'ori', 1: 'andi', 2: 'subi', 3: 'addi', 5: 'eori', 6: 'cmpi'}.get(sel)
        if name is None:
            raise Unsupported('imm sel %d' % sel)
        il = 4 if size == 'l' else 2
        if mode == 7 and reg == 4:  # to CCR / SR
            return self._mk(pc, 4, name + '_sr' if size == 'w' else name + '_ccr', size, 20)
        m, n, a, _ = self.ea(mode, reg, size, pc + 2 + il, pc + 2 + il)
        if m == 'Dn':
            if name == 'cmpi': c = 14 if size == 'l' else 8
            elif name == 'andi': c = 14 if size == 'l' else 8
            else: c = 16 if size == 'l' else 8
        else:
            if name == 'cmpi': c = (12 if size == 'l' else 8) + self._et(m, size)
            else: c = (20 if size == 'l' else 12) + self._et(m, size)
        return self._mk(pc, 2 + il + n, name, size, c, src='#', dst=m, abs_addrs=[a] if a is not None else [])

    # ---------- 0001/0010/0011: MOVE
    def _move(self, pc, op, size):
        smode, sreg = (op >> 3) & 7, op & 7
        dmode, dreg = (op >> 6) & 7, (op >> 9) & 7
        sm, sn, sa, _ = self.ea(smode, sreg, size, pc + 2, pc + 2)
        dm, dn, da, _ = self.ea(dmode, dreg, size, pc + 2 + sn, pc + 2 + sn)
        if dm in ('#', 'd16(PC)', 'd8(PC,Xn)'):
            raise Unsupported('move dst %s' % dm)
        tbl = MOVE_L if size == 'l' else MOVE_BW
        c = tbl[sm][DST_ORDER.index(dm)]
        name = 'movea' if dm == 'An' else 'move'
        return self._mk(pc, 2 + sn + dn, name, size, c, src=sm, dst=dm,
                        abs_addrs=[x for x in (sa, da) if x is not None])

    def _g1(self, pc, op): return self._move(pc, op, 'b')
    def _g2(self, pc, op): return self._move(pc, op, 'l')
    def _g3(self, pc, op): return self._move(pc, op, 'w')

    # ---------- 0100: misc
    def _g4(self, pc, op):
        mode, reg = (op >> 3) & 7, op & 7
        if op == 0x4E71: return self._mk(pc, 2, 'nop', '', 4)
        if op == 0x4E75: return self._mk(pc, 2, 'rts', '', 16, kind='ret')
        if op == 0x4E73: return self._mk(pc, 2, 'rte', '', 20, kind='rte')
        if op == 0x4E77: return self._mk(pc, 2, 'rtr', '', 20, kind='ret')
        if op == 0x4E76: return self._mk(pc, 2, 'trapv', '', 4)
        if op == 0x4E72: return self._mk(pc, 4, 'stop', '', 4, kind='stop')
        if op == 0x4E70: return self._mk(pc, 2, 'reset', '', 132)
        if (op & 0xFFF0) == 0x4E40: return self._mk(pc, 2, 'trap', '', 34, kind='stop')
        if (op & 0xFFF8) == 0x4E50: return self._mk(pc, 4, 'link', '', 16)
        if (op & 0xFFF8) == 0x4E58: return self._mk(pc, 2, 'unlk', '', 12)
        if (op & 0xFFF0) == 0x4E60: return self._mk(pc, 2, 'move_usp', 'l', 4)
        if (op & 0xFFC0) in (0x4E80, 0x4EC0):
            m, n, a, t = self.ea(mode, reg, 'l', pc + 2, pc + 2)
            tgt = a if a is not None else t
            isjsr = (op & 0xFFC0) == 0x4E80
            c = (JSR_T if isjsr else JMP_T)[m]
            kind = ('call' if isjsr else 'jump') if tgt is not None else ('icall' if isjsr else 'ijump')
            return self._mk(pc, 2 + n, 'jsr' if isjsr else 'jmp', '', c, kind=kind, target=tgt, dst=m)
        if (op & 0xF1C0) == 0x41C0:
            m, n, a, t = self.ea(mode, reg, 'l', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'lea', 'l', LEA_T[m], src=m)
        if (op & 0xF1C0) == 0x4180:
            m, n, a, _ = self.ea(mode, reg, 'w', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'chk', 'w', Cost(10 + self._et(m, 'w'), 10 + self._et(m, 'w'), 'no trap'), src=m)
        if (op & 0xFFC0) == 0x4840:
            if mode == 0: return self._mk(pc, 2, 'swap', 'w', 4)
            m, n, a, _ = self.ea(mode, reg, 'l', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'pea', 'l', PEA_T[m], src=m)
        if (op & 0xFFB8) == 0x4880:
            return self._mk(pc, 2, 'ext', 'l' if op & 0x40 else 'w', 4)
        if (op & 0xFB80) == 0x4880:  # MOVEM
            size = 'l' if op & 0x40 else 'w'
            mask = self.w(pc + 2)
            nreg = bin(mask).count('1')
            m, n, a, _ = self.ea(mode, reg, size, pc + 4, pc + 4)
            per = 8 if size == 'l' else 4
            if op & 0x0400:  # memory to registers
                c = MOVEM_M2R[m] + per * nreg
            else:
                c = MOVEM_R2M[m] + per * nreg
            return self._mk(pc, 4 + n, 'movem', size, c, src=m, abs_addrs=[a] if a is not None else [])
        hi = (op >> 8) & 0xF
        szb = (op >> 6) & 3
        if hi in (0x0, 0x2, 0x4, 0x6) and szb != 3:
            size = self._sz(szb)
            name = {0: 'negx', 2: 'clr', 4: 'neg', 6: 'not'}[hi]
            m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
            c = (6 if size == 'l' else 4) if m == 'Dn' else (12 if size == 'l' else 8) + self._et(m, size)
            return self._mk(pc, 2 + n, name, size, c, dst=m, abs_addrs=[a] if a is not None else [])
        if hi == 0x0 and szb == 3:
            m, n, a, _ = self.ea(mode, reg, 'w', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'move_from_sr', 'w', 6 if m == 'Dn' else 8 + self._et(m, 'w'))
        if hi in (0x4, 0x6) and szb == 3:
            m, n, a, _ = self.ea(mode, reg, 'w', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'move_to_ccr' if hi == 4 else 'move_to_sr', 'w', 12 + self._et(m, 'w'), src=m)
        if hi == 0xA:
            if szb == 3:
                m, n, a, _ = self.ea(mode, reg, 'b', pc + 2, pc + 2)
                return self._mk(pc, 2 + n, 'tas', 'b', 4 if m == 'Dn' else 14 + self._et(m, 'b'))
            size = self._sz(szb)
            m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'tst', size, 4 + self._et(m, size) if m not in ('Dn', 'An') else 4,
                            src=m, abs_addrs=[a] if a is not None else [])
        if hi == 0x8 and szb == 0:
            m, n, a, _ = self.ea(mode, reg, 'b', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, 'nbcd', 'b', 6 if m == 'Dn' else 8 + self._et(m, 'b'))
        raise Unsupported('group4 %04X' % op)

    # ---------- 0101: ADDQ/SUBQ/Scc/DBcc
    def _g5(self, pc, op):
        mode, reg = (op >> 3) & 7, op & 7
        szb = (op >> 6) & 3
        if szb == 3:
            cc = CC[(op >> 8) & 0xF]
            if mode == 1:
                disp = s16(self.w(pc + 2))
                tgt = (pc + 2 + disp) & 0xFFFFFF
                fall = Cost(14, 14, 'expired') if cc == 'f' else Cost(12, 14, 'cc-true 12 or expired 14')
                if cc == 't':
                    fall = Cost(12, 12, 'cc true')
                return Insn(pc, 4, 'db' + cc, 'w', 'dbcc', target=tgt, taken=Cost(10, 10), fall=fall)
            m, n, a, _ = self.ea(mode, reg, 'b', pc + 2, pc + 2)
            if m == 'Dn':
                c = Cost(4, 6, 'Scc Dn: 4 false / 6 true') if cc not in ('t', 'f') else Cost(6, 6) if cc == 't' else Cost(4, 4)
            else:
                c = Cost(8 + self._et(m, 'b'), 8 + self._et(m, 'b'))
            return self._mk(pc, 2 + n, 's' + cc, 'b', c, dst=m, abs_addrs=[a] if a is not None else [])
        size = self._sz(szb)
        m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
        name = 'subq' if op & 0x100 else 'addq'
        if m == 'Dn': c = 8 if size == 'l' else 4
        elif m == 'An': c = 8
        else: c = (12 if size == 'l' else 8) + self._et(m, size)
        return self._mk(pc, 2 + n, name, size, c, dst=m, abs_addrs=[a] if a is not None else [])

    # ---------- 0110: Bcc/BRA/BSR
    def _g6(self, pc, op):
        cond = (op >> 8) & 0xF
        d = op & 0xFF
        if d == 0:
            disp = s16(self.w(pc + 2)); ln = 4; short = False
        elif d == 0xFF:
            raise Unsupported('68020 32-bit branch')
        else:
            disp = s8(d); ln = 2; short = True
        tgt = (pc + 2 + disp) & 0xFFFFFF
        if cond == 0:
            return self._mk(pc, ln, 'bra', '', 10, kind='jump', target=tgt)
        if cond == 1:
            return self._mk(pc, ln, 'bsr', '', 18, kind='call', target=tgt)
        return Insn(pc, ln, 'b' + CC[cond], 's' if short else 'w', 'cond', target=tgt,
                    taken=Cost(10, 10), fall=Cost(8, 8) if short else Cost(12, 12))

    def _g7(self, pc, op):
        if op & 0x100:
            raise Unsupported('moveq bit 8 set %04X' % op)
        return self._mk(pc, 2, 'moveq', 'l', 4)

    # ---------- 1000/1001/1011/1100/1101: arithmetic/logic
    def _arith(self, pc, op, base):
        mode, reg = (op >> 3) & 7, op & 7
        opm = (op >> 6) & 7
        if opm in (3, 7):  # word/long to An (ADDA/SUBA/CMPA) or MUL/DIV
            if base in ('or', 'and'):
                size = 'w'
                m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
                et = self._et(m, size)
                if base == 'or':
                    nm = 'divu' if opm == 3 else 'divs'
                    c = Cost(76 + et, 140 + et, 'DIVU operand') if nm == 'divu' else Cost(120 + et, 158 + et, 'DIVS operand (bound)')
                else:
                    nm = 'mulu' if opm == 3 else 'muls'
                    c = Cost(38 + et, 70 + et, 'MUL operand bit pattern')
                return self._mk(pc, 2 + n, nm, 'w', c, src=m)
            size = 'w' if opm == 3 else 'l'
            m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
            et = self._et(m, size)
            if base == 'cmp': c = 6 + et
            elif size == 'w': c = 8 + et
            else: c = (8 if m in ('Dn', 'An', '#') else 6) + et
            return self._mk(pc, 2 + n, base + 'a', size, c, src=m, abs_addrs=[a] if a is not None else [])
        size = self._sz(opm & 3)
        if opm >= 4 and mode in (0, 1):
            if base == 'cmp':
                if mode == 1:
                    return self._mk(pc, 2, 'cmpm', size, 20 if size == 'l' else 12)
            elif base in ('add', 'sub'):
                c = (8 if size == 'l' else 4) if mode == 0 else (30 if size == 'l' else 18)
                return self._mk(pc, 2, base + 'x', size, c)
            elif opm == 4:
                return self._mk(pc, 2, 'sbcd' if base == 'or' else 'abcd', 'b', 6 if mode == 0 else 18)
            elif base == 'and' and (opm == 5 or (opm == 6 and mode == 1)):
                return self._mk(pc, 2, 'exg', 'l', 6)
            else:
                raise Unsupported('group %s opm %d mode %d' % (base, opm, mode))
        m, n, a, _ = self.ea(mode, reg, size, pc + 2, pc + 2)
        et = self._et(m, size)
        if base == 'cmp' and opm >= 4:
            base = 'eor'
        if opm < 4 and base != 'eor':  # <ea>,Dn
            if size == 'l':
                if base == 'cmp': c = 6 + et
                else: c = (8 if m in ('Dn', 'An', '#') else 6) + et
            else:
                c = 4 + et
            return self._mk(pc, 2 + n, base, size, c, src=m, dst='Dn', abs_addrs=[a] if a is not None else [])
        # Dn,<ea>
        if m == 'Dn':
            c = 8 if size == 'l' else 4
        else:
            c = (12 if size == 'l' else 8) + et
        return self._mk(pc, 2 + n, base, size, c, src='Dn', dst=m, abs_addrs=[a] if a is not None else [])

    def _g8(self, pc, op): return self._arith(pc, op, 'or')
    def _g9(self, pc, op): return self._arith(pc, op, 'sub')
    def _gB(self, pc, op): return self._arith(pc, op, 'cmp')
    def _gC(self, pc, op): return self._arith(pc, op, 'and')
    def _gD(self, pc, op): return self._arith(pc, op, 'add')

    # ---------- 1110: shifts/rotates
    def _gE(self, pc, op):
        typ = ['as', 'ls', 'rox', 'ro']
        d = 'l' if op & 0x100 else 'r'
        if ((op >> 6) & 3) == 3:
            mode, reg = (op >> 3) & 7, op & 7
            m, n, a, _ = self.ea(mode, reg, 'w', pc + 2, pc + 2)
            return self._mk(pc, 2 + n, typ[(op >> 9) & 3] + d, 'w', 8 + self._et(m, 'w'), dst=m)
        size = self._sz((op >> 6) & 3)
        base = 8 if size == 'l' else 6
        name = typ[(op >> 3) & 3] + d
        if op & 0x20:
            return self._mk(pc, 2, name, size, Cost(base, base + 2 * 63, 'register shift count (Dn mod 64)'))
        cnt = (op >> 9) & 7 or 8
        return self._mk(pc, 2, name, size, base + 2 * cnt)

    def _gA(self, pc, op): raise Unsupported('line-A %04X' % op)
    def _gF(self, pc, op): raise Unsupported('line-F %04X' % op)
