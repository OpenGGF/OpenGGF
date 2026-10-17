#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R6 (slice 1, 2026-10-16).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes (opcode pins checked), a cost function, a per-window instruction-cycle budget (caller-supplied).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Resumable S3K Process_Kos_Queue service model (kos46 slice 1, research prototype, 2026-10-16).

Purpose: execute the shipped routine Process_Kos_Queue .. Restore_Kos_Bookmark (sonic3k.asm 2872-2999, ROM $1C1A-$1D0A)
instruction by instruction on a minimal state (d0-d6, a0-a2, CCR, queue RAM $FF0E-$FF5F, destination RAM) and charge
each executed instruction its GPGX 051d430d cycles (decoded from the ROM bytes at that PC, branch outcome from the
executed semantics). Given an instruction-cycle budget for one service window, it stops at the first instruction
boundary where the budget is reached, as the 68000 does when VInt is asserted, and reports bytes emitted, the
bookmark (resume PC) or retirement, and the preserved register/cursor state. Restoring that state and serving the next
window resumes through Restore_Kos_Bookmark exactly as the ROM does.
Every handled PC is pinned to the opcode word in the shipped ROM; any other PC raises. Interruption outside
Process_Kos_Queue_Main..Done (where Set_Kos_Bookmark does not bookmark) is reported, not modelled.
Budgets are inputs (tests feed native windows as comparison data); nothing here is a measured cost.
Not modelled (caller's ledger terms): refresh, Z80-bus and FIFO stalls inside the window, VInt acceptance,
Set_Kos_Bookmark/Backup_Kos_Registers time after the VInt.
"""
import copy

MAIN, DONE, RESTORE = 0x1C26, 0x1CFA, 0x1CFC
COUNT, QUEUE, DESC, STORED, STORED_SR, BOOKMARK = 0xFF0E, 0xFF40, 0xFF3E, 0xFF10, 0xFF38, 0xFF3A
PINS = {0x1C1A: 0x4A78, 0x1C1E: 0x6700, 0x1C22: 0x6B00, 0x1C26: 0x0078, 0x1C2C: 0x2078, 0x1C30: 0x2278,
        0x1C34: 0x45F8, 0x1C38: 0x1558, 0x1C3C: 0x1498, 0x1C3E: 0x3A12, 0x1C40: 0x780F, 0x1C42: 0xE24D,
        0x1C44: 0x40C6, 0x1C46: 0x51CC, 0x1C4A: 0x1558, 0x1C4E: 0x1498, 0x1C50: 0x3A12, 0x1C52: 0x780F,
        0x1C54: 0x44C6, 0x1C56: 0x6404, 0x1C58: 0x12D8, 0x1C5A: 0x60E6, 0x1C5C: 0x7600, 0x1C5E: 0xE24D,
        0x1C60: 0x40C6, 0x1C62: 0x51CC, 0x1C66: 0x1558, 0x1C6A: 0x1498, 0x1C6C: 0x3A12, 0x1C6E: 0x780F,
        0x1C70: 0x44C6, 0x1C72: 0x652C, 0x1C74: 0xE24D, 0x1C76: 0x51CC, 0x1C7A: 0x1558, 0x1C7E: 0x1498,
        0x1C80: 0x3A12, 0x1C82: 0x780F, 0x1C84: 0xE353, 0x1C86: 0xE24D, 0x1C88: 0x51CC, 0x1C8C: 0x1558,
        0x1C90: 0x1498, 0x1C92: 0x3A12, 0x1C94: 0x780F, 0x1C96: 0xE353, 0x1C98: 0x5243, 0x1C9A: 0x74FF,
        0x1C9C: 0x1418, 0x1C9E: 0x6016, 0x1CA0: 0x1018, 0x1CA2: 0x1218, 0x1CA4: 0x74FF, 0x1CA6: 0x1401,
        0x1CA8: 0xEB4A, 0x1CAA: 0x1400, 0x1CAC: 0x0241, 0x1CB0: 0x6710, 0x1CB2: 0x1601, 0x1CB4: 0x5243,
        0x1CB6: 0x1031, 0x1CBA: 0x12C0, 0x1CBC: 0x51CB, 0x1CC0: 0x6080, 0x1CC2: 0x1218, 0x1CC4: 0x670C,
        0x1CC6: 0x0C01, 0x1CCA: 0x6700, 0x1CCE: 0x1601, 0x1CD0: 0x60E4, 0x1CD2: 0x21C8, 0x1CD6: 0x21C9,
        0x1CDA: 0x0278, 0x1CE0: 0x5378, 0x1CE4: 0x6714, 0x1CE6: 0x41F8, 0x1CEA: 0x43F8, 0x1CEE: 0x20D9,
        0x1CF0: 0x20D9, 0x1CF2: 0x20D9, 0x1CF4: 0x20D9, 0x1CF6: 0x20D9, 0x1CF8: 0x20D9, 0x1CFA: 0x4E75,
        0x1CFC: 0x4CF8, 0x1D02: 0x2F38, 0x1D06: 0x3F38, 0x1D0A: 0x4E73}


class Unmodelled(Exception):
    pass


class State:
    """Everything Backup_Kos_Registers/Restore_Kos_Bookmark preserve, plus the RAM the routine touches."""
    def __init__(self):
        self.d = [0] * 8
        self.a = [0] * 3
        self.x = self.n = self.z = self.v = self.c = 0
        self.ram = bytearray(0x10000)          # $FF0000-$FFFFFF
        self.pc = 0x1C1A

    def ccr(self):
        return self.x << 4 | self.n << 3 | self.z << 2 | self.v << 1 | self.c

    def set_ccr(self, v):
        self.x, self.n, self.z, self.v, self.c = (v >> 4) & 1, (v >> 3) & 1, (v >> 2) & 1, (v >> 1) & 1, v & 1

    def snapshot(self):
        return copy.deepcopy(self)


class KosService:
    def __init__(self, rom, cost_of):
        """cost_of(pc, outcome) -> cycles; outcome is 'seq', 'taken' or 'fall'. Pins are checked against `rom`."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise Unmodelled('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost_of = rom, cost_of

    # ---- memory
    def rb(self, s, addr):
        addr &= 0xFFFFFF
        if addr >= 0xFF0000:
            return s.ram[addr & 0xFFFF]
        if addr < len(self.rom):
            return self.rom[addr]
        raise Unmodelled('read at %06X' % addr)

    def wb(self, s, addr, v):
        addr &= 0xFFFFFF
        if addr < 0xFF0000:
            raise Unmodelled('write at %06X' % addr)
        s.ram[addr & 0xFFFF] = v & 0xFF

    def rw(self, s, addr):
        return self.rb(s, addr) << 8 | self.rb(s, addr + 1)

    def ww(self, s, addr, v):
        self.wb(s, addr, v >> 8)
        self.wb(s, addr + 1, v)

    def rl(self, s, addr):
        return self.rw(s, addr) << 16 | self.rw(s, addr + 2)

    def wl(self, s, addr, v):
        self.ww(s, addr, v >> 16)
        self.ww(s, addr + 2, v)

    @staticmethod
    def nz(s, v, bits):
        v &= (1 << bits) - 1
        s.n, s.z, s.v, s.c = v >> (bits - 1), int(v == 0), 0, 0

    # ---- queue setup (what Queue_Kos stores; tests and callers provide it)
    @staticmethod
    def queue(s, entries):
        s.ram[COUNT & 0xFFFF:(COUNT & 0xFFFF) + 2] = len(entries).to_bytes(2, 'big')
        for k, (src, dst) in enumerate(entries):
            base = (QUEUE & 0xFFFF) + 8 * k
            s.ram[base:base + 4] = (src & 0xFFFFFFFF).to_bytes(4, 'big')
            s.ram[base + 4:base + 8] = (dst | 0xFF000000 if dst < 0x1000000 else dst).to_bytes(4, 'big')

    # ---- one instruction; returns (next pc, outcome)
    def step(self, s):
        pc, d, a = s.pc, s.d, s.a
        w = lambda r, v: d.__setitem__(r, (d[r] & 0xFFFF0000) | (v & 0xFFFF))  # noqa: E731
        b = lambda r, v: d.__setitem__(r, (d[r] & 0xFFFFFF00) | (v & 0xFF))     # noqa: E731
        def postinc_byte():
            v = self.rb(s, a[0])
            a[0] = (a[0] + 1) & 0xFFFFFFFF
            return v
        def refill_hi():
            v = postinc_byte(); self.wb(s, 0xFFFF00 | (DESC + 1), v); self.nz(s, v, 8)
        def refill_lo():
            v = postinc_byte(); self.wb(s, 0xFFFF00 | DESC, v); self.nz(s, v, 8)
        def lsr_d5():
            v = d[5] & 0xFFFF
            s.c = s.x = v & 1
            w(5, v >> 1)
            s.n, s.z, s.v = 0, int((v >> 1) == 0), 0
        def dbf(r, target, nxt):
            v = (d[r] - 1) & 0xFFFF
            w(r, v)
            return (target, 'taken') if v != 0xFFFF else (nxt, 'fall')
        def roxl_d3():
            v = d[3] & 0xFFFF
            out = v >> 15
            v = ((v << 1) | s.x) & 0xFFFF
            w(3, v)
            s.x = s.c = out
            s.n, s.z, s.v = v >> 15, int(v == 0), 0
        def addq_d3():
            v = (d[3] & 0xFFFF) + 1
            s.x = s.c = int(v > 0xFFFF)
            v &= 0xFFFF
            s.v = int(v == 0x8000)
            w(3, v)
            s.n, s.z = v >> 15, int(v == 0)
        desc = lambda: self.rw(s, 0xFFFF00 | DESC)   # noqa: E731
        if pc not in PINS:
            raise Unmodelled('pc %06X outside the modelled routine' % pc)
        if pc == 0x1C1A:
            self.nz(s, self.rw(s, 0xFFFF00 | COUNT), 16); return 0x1C1E, 'seq'
        if pc == 0x1C1E:
            return (DONE, 'taken') if s.z else (0x1C22, 'fall')
        if pc == 0x1C22:
            return (RESTORE, 'taken') if s.n else (MAIN, 'fall')
        if pc == 0x1C26:
            v = self.rw(s, 0xFFFF00 | COUNT) | 0x8000; self.ww(s, 0xFFFF00 | COUNT, v); self.nz(s, v, 16)
            return 0x1C2C, 'seq'
        if pc == 0x1C2C:
            a[0] = self.rl(s, 0xFFFF00 | QUEUE); return 0x1C30, 'seq'
        if pc == 0x1C30:
            a[1] = self.rl(s, 0xFFFF00 | (QUEUE + 4)); return 0x1C34, 'seq'
        if pc == 0x1C34:
            a[2] = 0xFFFF00 | DESC; return 0x1C38, 'seq'
        if pc in (0x1C38, 0x1C4A, 0x1C66, 0x1C7A, 0x1C8C):
            refill_hi(); return pc + 4, 'seq'
        if pc in (0x1C3C, 0x1C4E, 0x1C6A, 0x1C7E, 0x1C90):
            refill_lo(); return pc + 2, 'seq'
        if pc in (0x1C3E, 0x1C50, 0x1C6C, 0x1C80, 0x1C92):
            v = desc(); w(5, v); self.nz(s, v, 16); return pc + 2, 'seq'
        if pc in (0x1C40, 0x1C52, 0x1C6E, 0x1C82, 0x1C94):
            d[4] = 15; self.nz(s, 15, 32)
            return {0x1C40: 0x1C42, 0x1C52: 0x1C54, 0x1C6E: 0x1C70, 0x1C82: 0x1C84, 0x1C94: 0x1C96}[pc], 'seq'
        if pc in (0x1C42, 0x1C5E, 0x1C74, 0x1C86):
            lsr_d5(); return pc + 2, 'seq'
        if pc in (0x1C44, 0x1C60):
            w(6, s.ccr()); return pc + 2, 'seq'               # move sr,d6 (system byte not modelled)
        if pc == 0x1C46:
            return dbf(4, 0x1C54, 0x1C4A)
        if pc == 0x1C62:
            return dbf(4, 0x1C70, 0x1C66)
        if pc == 0x1C76:
            return dbf(4, 0x1C84, 0x1C7A)
        if pc == 0x1C88:
            return dbf(4, 0x1C96, 0x1C8C)
        if pc in (0x1C54, 0x1C70):
            s.set_ccr(d[6] & 0x1F); return pc + 2, 'seq'
        if pc == 0x1C56:
            return (0x1C5C, 'taken') if not s.c else (0x1C58, 'fall')
        if pc == 0x1C58:
            v = postinc_byte(); self.wb(s, a[1], v); a[1] = (a[1] + 1) & 0xFFFFFFFF; self.nz(s, v, 8)
            return 0x1C5A, 'seq'
        if pc in (0x1C5A, 0x1CC0):
            return 0x1C42, 'seq'
        if pc == 0x1C5C:
            d[3] = 0; self.nz(s, 0, 32); return 0x1C5E, 'seq'
        if pc == 0x1C72:
            return (0x1CA0, 'taken') if s.c else (0x1C74, 'fall')
        if pc in (0x1C84, 0x1C96):
            roxl_d3(); return pc + 2, 'seq'
        if pc in (0x1C98, 0x1CB4):
            addq_d3(); return pc + 2, 'seq'
        if pc in (0x1C9A, 0x1CA4):
            d[2] = 0xFFFFFFFF; self.nz(s, d[2], 32); return pc + 2, 'seq'
        if pc == 0x1C9C:
            v = postinc_byte(); b(2, v); self.nz(s, v, 8); return 0x1C9E, 'seq'
        if pc == 0x1C9E:
            return 0x1CB6, 'seq'
        if pc == 0x1CA0:
            v = postinc_byte(); b(0, v); self.nz(s, v, 8); return 0x1CA2, 'seq'
        if pc in (0x1CA2, 0x1CC2):
            v = postinc_byte(); b(1, v); self.nz(s, v, 8); return pc + 2, 'seq'
        if pc == 0x1CA6:
            b(2, d[1]); self.nz(s, d[1], 8); return 0x1CA8, 'seq'
        if pc == 0x1CA8:
            v = d[2] & 0xFFFF
            s.c = s.x = (v >> 11) & 1
            v = (v << 5) & 0xFFFF
            w(2, v); s.n, s.z, s.v = v >> 15, int(v == 0), 0       # V for LSL is always 0
            return 0x1CAA, 'seq'
        if pc == 0x1CAA:
            b(2, d[0]); self.nz(s, d[0], 8); return 0x1CAC, 'seq'
        if pc == 0x1CAC:
            v = d[1] & 7; w(1, v); self.nz(s, v, 16); return 0x1CB0, 'seq'
        if pc == 0x1CB0:
            return (0x1CC2, 'taken') if s.z else (0x1CB2, 'fall')
        if pc in (0x1CB2, 0x1CCE):
            b(3, d[1]); self.nz(s, d[1], 8); return pc + 2, 'seq'
        if pc == 0x1CB6:
            off = d[2] & 0xFFFF
            off = off - 0x10000 if off & 0x8000 else off
            v = self.rb(s, a[1] + off); b(0, v); self.nz(s, v, 8); return 0x1CBA, 'seq'
        if pc == 0x1CBA:
            self.wb(s, a[1], d[0]); a[1] = (a[1] + 1) & 0xFFFFFFFF; self.nz(s, d[0], 8); return 0x1CBC, 'seq'
        if pc == 0x1CBC:
            return dbf(3, 0x1CB6, 0x1CC0)
        if pc == 0x1CC4:
            return (0x1CD2, 'taken') if s.z else (0x1CC6, 'fall')
        if pc == 0x1CC6:
            v = (d[1] - 1) & 0xFF
            s.n, s.z, s.v, s.c = v >> 7, int(v == 0), 0, int((d[1] & 0xFF) < 1)
            return 0x1CCA, 'seq'
        if pc == 0x1CCA:
            return (0x1C42, 'taken') if s.z else (0x1CCE, 'fall')
        if pc == 0x1CD0:
            return 0x1CB6, 'seq'
        if pc == 0x1CD2:
            self.wl(s, 0xFFFF00 | QUEUE, a[0]); self.nz(s, a[0], 32); return 0x1CD6, 'seq'
        if pc == 0x1CD6:
            self.wl(s, 0xFFFF00 | (QUEUE + 4), a[1]); self.nz(s, a[1], 32); return 0x1CDA, 'seq'
        if pc == 0x1CDA:
            v = self.rw(s, 0xFFFF00 | COUNT) & 0x7FFF; self.ww(s, 0xFFFF00 | COUNT, v); self.nz(s, v, 16)
            return 0x1CE0, 'seq'
        if pc == 0x1CE0:
            v = (self.rw(s, 0xFFFF00 | COUNT) - 1) & 0xFFFF; self.ww(s, 0xFFFF00 | COUNT, v)
            s.n, s.z = v >> 15, int(v == 0)
            return 0x1CE4, 'seq'
        if pc == 0x1CE4:
            return (DONE, 'taken') if s.z else (0x1CE6, 'fall')
        if pc == 0x1CE6:
            a[0] = 0xFFFF00 | QUEUE; return 0x1CEA, 'seq'
        if pc == 0x1CEA:
            a[1] = 0xFFFF00 | (QUEUE + 8); return 0x1CEE, 'seq'
        if 0x1CEE <= pc <= 0x1CF8:
            v = self.rl(s, a[1]); a[1] += 4; self.wl(s, a[0], v); a[0] += 4; self.nz(s, v, 32)
            return pc + 2, 'seq'
        if pc == DONE:
            return None, 'seq'
        if pc == RESTORE:
            base = 0xFFFF00 | STORED
            for k in range(7):
                d[k] = self.rl(s, base + 4 * k)
            for k in range(3):
                a[k] = self.rl(s, base + 28 + 4 * k)
            return 0x1D02, 'seq'
        if pc in (0x1D02, 0x1D06):
            return pc + 4, 'seq'                            # stack pushes consumed by the rte below
        if pc == 0x1D0A:
            s.set_ccr(self.rw(s, 0xFFFF00 | STORED_SR) & 0x1F)
            return self.rl(s, 0xFFFF00 | BOOKMARK) & 0xFFFFFF, 'seq'
        raise Unmodelled('unhandled pc %06X' % pc)

    def serve(self, s, budget):
        """One service window from Process_Kos_Queue entry. Returns a dict with cycles, outcome and bytes."""
        s.pc = 0x1C1A
        a1_start = None
        cycles = 0
        counts = {}
        a1_end = None
        while True:
            counts[s.pc] = counts.get(s.pc, 0) + 1
            nxt, outcome = self.step(s)
            last_cost = self.cost_of(s.pc, outcome)
            cycles += last_cost
            if s.pc == 0x1C30 or (s.pc == 0x1D0A and nxt >= 0x1C34):
                a1_start = s.a[1]                            # output cursor: freshly loaded, or restored after load
            if s.pc == 0x1CD2:
                a1_end = s.a[1]                              # output cursor at EndReached, before the queue shift
            prev = s.pc
            if nxt is None:                                  # rts at Done: retired or idle queue
                return {'cycles': cycles, 'outcome': 'returned', 'pc': None, 'counts': counts,
                        'bytes': (a1_end - a1_start) if a1_end is not None else 0, 'retired': a1_end is not None}
            s.pc = nxt
            if cycles >= budget:
                busy = self.rw(s, 0xFFFF00 | COUNT) & 0x8000
                if busy and MAIN <= s.pc < DONE:
                    # Set_Kos_Bookmark + Backup_Kos_Registers: save PC, SR and d0-d6/a0-a2
                    self.wl(s, 0xFFFF00 | BOOKMARK, s.pc)
                    self.ww(s, 0xFFFF00 | STORED_SR, 0x2000 | s.ccr())
                    base = 0xFFFF00 | STORED
                    for k in range(7):
                        self.wl(s, base + 4 * k, s.d[k])
                    for k in range(3):
                        self.wl(s, base + 28 + 4 * k, s.a[k])
                    return {'cycles': cycles, 'outcome': 'bookmarked', 'pc': s.pc, 'counts': counts,
                            'bytes': (s.a[1] - a1_start) if a1_start is not None else 0, 'last_pc': prev, 'last_cost': last_cost}
                raise Unmodelled('budget reached at %06X outside the bookmark range (busy=%d)' % (s.pc, bool(busy)))
