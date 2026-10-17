#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R12 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM art bytes, decode progress, pinned per-event instruction costs listed in this file.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Format-level Nemesis cost model for a production port (kos46 research prototype, 2026-10-17).

A plain Nemesis decoder instrumented with the ROM's own decoder state (Nem_shift_value d6, run carry d0, palette
index, XOR row) that charges, per decode event, the instruction costs of the exact ROM PC path that event executes.
Event costs are summed at construction from the pinned per-instruction cost function along PC/outcome lists taken
from sonic3k.asm 1854-2012 (no measured totals, no fitted constants); register shifts add 2 x count (GPGX
m68kops.h 9614-9623, Motorola identical). The tests require it to equal the mini68k micro-execution of the ROM on
every VInt of every Offs_PLC art and both Ending credits jobs, so a Java port of this file is the source-complete piece
for the decoder term. It covers the decoder ($1604-$16A7) and Process_Nem_Queue_Init + Nem_Build_Code_Table
(main-loop term); the Process_Nem_Queue_2 fixed part is nem_cost.NemLedger.
"""

S, T, F = 'seq', 'taken', 'fall'
NEWROW_CARRY = [(0x1646, S), (0x1648, S), (0x164A, T)]
NEWROW_FETCH = [(0x1646, S), (0x1648, S), (0x164A, F), (0x164E, S)]
PIXEL = [(0x163C, S), (0x163E, S), (0x1640, S)]
ROW_CONT_MORE = [(0x1642, T), (0x164A, T)]
ROW_CONT_FETCH = [(0x1642, T), (0x164A, F), (0x164E, S)]
ROW_END = [(0x1642, F), (0x1644, S)]
WRITER = {False: ([(0x167C, S), (0x167E, S), (0x1680, S), (0x1682, T)],
                  [(0x167C, S), (0x167E, S), (0x1680, S), (0x1682, F), (0x1684, S)]),
          True: ([(0x1686, S), (0x1688, S), (0x168A, S), (0x168C, S), (0x168E, T)],
                 [(0x1686, S), (0x1688, S), (0x168A, S), (0x168C, S), (0x168E, F), (0x1690, S)])}
FETCH_HEAD = [(0x1604, S), (0x1606, S), (0x1608, S), (0x160A, S), (0x160C, S)]          # $160A: lsr.w d7,d1
TABLE_BODY = [(0x1610, F), (0x1612, S), (0x1616, S), (0x1618, S), (0x161C, S), (0x161E, S), (0x1620, S)]
TABLE_REFILL = {True: [(0x1624, F), (0x1626, S), (0x1628, S), (0x162A, S)], False: [(0x1624, T)]}
TABLE_TAIL = [(0x162C, S), (0x1630, S), (0x1632, S), (0x1636, S), (0x163A, S)]
INLINE_A = [(0x1610, T), (0x1650, S), (0x1652, S)]
INLINE_REFILL1 = {True: [(0x1656, F), (0x1658, S), (0x165A, S), (0x165C, S)], False: [(0x1656, T)]}
INLINE_B = [(0x165E, S), (0x1660, S), (0x1662, S), (0x1664, S), (0x1666, S), (0x166A, S), (0x166E, S)]  # lsr.w d6
INLINE_REFILL2 = {True: [(0x1672, F), (0x1674, S), (0x1676, S), (0x1678, S), (0x167A, S)], False: [(0x1672, T)]}
INLINE_TAIL = [(0x163A, S)]
# Process_Nem_Queue_Init ($1780) armed path; $179E bpl.s taken for plain art, falls into adda.w for XOR art
INIT_HEAD = [(0x1780, S), (0x1784, F), (0x1786, S), (0x178A, F), (0x178C, S), (0x1790, S), (0x1796, S), (0x1798, S),
             (0x179C, S)]
INIT_MODE = {False: [(0x179E, T)], True: [(0x179E, F), (0x17A0, S)]}
INIT_TAIL = [(0x17A4, S), (0x17A8, S), (0x17AC, S), (0x17AE, S), (0x17B0, S), (0x17B2, S), (0x17B4, S), (0x17B6, S),
             (0x17BA, S), (0x17BE, S), (0x17C2, S), (0x17C6, S), (0x17CA, S), (0x17CE, S), (0x17D2, S), (0x17D6, S)]
# Nem_Build_Code_Table ($16A8)
BCT_FIRST = [(0x16A8, S)]
BCT_CHK = [(0x16AA, S)]
BCT_END = [(0x16AE, F), (0x16B0, S)]
BCT_NEWPAL = [(0x16AE, T), (0x16B2, S)]
BCT_LOOP = [(0x16B4, S), (0x16B6, S)]
BCT_TO_CHK = [(0x16BA, T)]
BCT_REC = [(0x16BA, F), (0x16BC, S), (0x16BE, S), (0x16C2, S), (0x16C6, S), (0x16C8, S), (0x16CC, S), (0x16CE, S),
           (0x16D0, S), (0x16D2, S), (0x16D4, S)]
BCT_CODE8 = [(0x16D6, F), (0x16D8, S), (0x16DA, S), (0x16DC, S), (0x16E0, S)]
BCT_SHORT = [(0x16D6, T), (0x16E2, S), (0x16E4, S), (0x16E6, S), (0x16E8, S), (0x16EA, S), (0x16EC, S)]  # 2 shifts
BCT_SHORT_ENTRY = [(0x16EE, S), (0x16F2, S)]
BCT_SHORT_DONE = [(0x16F4, F), (0x16F8, S)]
BCT_SHORT_AGAIN = [(0x16F4, T)]


class NemEventCost:
    def __init__(self, rom, cost, decoder, shift_base=None):
        """cost(ins, outcome) is the pinned per-instruction cost; register shifts are charged shift_base(op) (GPGX
        m68ki_cycles entry; default the Motorola zero-count time) and the count is added per event.
        `decoder` is m68k_timing.Decoder(rom)."""
        self.rom, self.shift_base = rom, shift_base
        c = lambda path: sum(self._one(cost, decoder, pc, o) for pc, o in path)  # noqa: E731
        self.c = c
        self.k = {name: c(globals()[name]) for name in (
            'NEWROW_CARRY', 'NEWROW_FETCH', 'PIXEL', 'ROW_CONT_MORE', 'ROW_CONT_FETCH', 'ROW_END', 'FETCH_HEAD',
            'TABLE_BODY', 'TABLE_TAIL', 'INLINE_A', 'INLINE_B', 'INLINE_TAIL', 'INIT_HEAD', 'INIT_TAIL', 'BCT_FIRST',
            'BCT_CHK', 'BCT_END', 'BCT_NEWPAL', 'BCT_LOOP', 'BCT_TO_CHK', 'BCT_REC', 'BCT_CODE8', 'BCT_SHORT',
            'BCT_SHORT_ENTRY', 'BCT_SHORT_DONE', 'BCT_SHORT_AGAIN')}
        for name, d in (('TABLE_REFILL', TABLE_REFILL), ('INLINE_REFILL1', INLINE_REFILL1),
                        ('INLINE_REFILL2', INLINE_REFILL2), ('INIT_MODE', INIT_MODE)):
            for flag, path in d.items():
                self.k[(name, flag)] = c(path)
        for xor, (nxt, last) in WRITER.items():
            self.k[('WRITER', xor, False)] = c(nxt)
            self.k[('WRITER', xor, True)] = c(last)

    def _one(self, cost, decoder, pc, outcome):
        ins = decoder.decode(pc)
        op = (self.rom[pc] << 8) | self.rom[pc + 1]
        if (op >> 12) == 0xE and ((op >> 6) & 3) != 3 and op & 0x20 and ((op >> 3) & 3) == 1:   # LSL/LSR Dx,Dy
            if pc not in (0x160A, 0x1662, 0x16E4, 0x16EA):
                raise ValueError('unexpected register shift at %06X' % pc)
            return self.shift_base(op) if self.shift_base else ins.cost.lo
        return cost(ins, outcome)

    # -- main loop: Process_Nem_Queue_Init (armed) + Nem_Build_Code_Table
    def init_cycles(self, art):
        rom, k = self.rom, self.k
        hdr = (rom[art] << 8) | rom[art + 1]
        cyc = k['INIT_HEAD'] + k[('INIT_MODE', bool(hdr & 0x8000))] + k['INIT_TAIL']
        pos = art + 2
        cyc += k['BCT_FIRST']
        b = rom[pos]; pos += 1
        while True:
            cyc += k['BCT_CHK']
            if b == 0xFF:
                cyc += k['BCT_END']
                return cyc, pos
            cyc += k['BCT_NEWPAL']
            while True:
                cyc += k['BCT_LOOP']
                b = rom[pos]; pos += 1
                if b >= 0x80:
                    cyc += k['BCT_TO_CHK']
                    break
                cyc += k['BCT_REC']
                n = b & 0xF
                pos += 1
                if n == 8:
                    cyc += k['BCT_CODE8']
                    continue
                if not 1 <= n <= 8:
                    raise ValueError('code length %d is outside the format (table write out of range)' % n)
                sh = 8 - n
                cyc += k['BCT_SHORT'] + 2 * sh + 2 * sh + (1 << sh) * k['BCT_SHORT_ENTRY'] + \
                    ((1 << sh) - 1) * k['BCT_SHORT_AGAIN'] + k['BCT_SHORT_DONE']

    # -- VInt: decoder cycles per Process_Nem_Queue_2 call
    def patterns(self, art):
        """Yields (decoder cycles, carried state, rows) for each pattern of the art, in decode order."""
        rom = self.rom
        hdr = (rom[art] << 8) | rom[art + 1]
        xor, count = bool(hdr & 0x8000), hdr & 0x7FFF
        _, pos = self.init_cycles(art)
        codes = self._codes(art)
        st = {'bit': pos * 8, 'd6': 16, 'd0': 0, 'pal': 0, 'prev': 0}
        for _ in range(count):
            rows, cyc = [], 0
            for r in range(8):
                cyc += self._row(st, codes, xor, r == 7, rows)
            yield cyc, dict(st), rows

    def job(self, art, budget=3):
        """Per-VInt decoder cycles (Process_Nem_Queue_2 fixed part excluded) for a whole art job, with the
        explicit carried state and rows after each VInt."""
        cyc, rows, n = 0, [], 0
        for c, st, rs in self.patterns(art):
            cyc += c; rows += rs; n += 1
            if n == budget:
                yield cyc, st, rows
                cyc, rows, n = 0, [], 0
        if n:
            yield cyc, st, rows

    def vint_cycles(self, art, done, budget=3):
        """Decoder cycles of the VInt that starts with `done` patterns of `art` already decoded."""
        costs = [c for c, _, _ in self.patterns(art)]
        if not 0 <= done < len(costs):
            raise ValueError('progress %d outside 0..%d' % (done, len(costs) - 1))
        return sum(costs[done:done + budget])

    def _codes(self, art):
        import nem_reference
        return nem_reference.parse(self.rom, art)[2]

    def _peek(self, bit, n):
        v = 0
        for i in range(bit, bit + n):
            v = (v << 1) | ((self.rom[i >> 3] >> (7 - (i & 7))) & 1)
        return v

    def _fetch(self, st, codes):
        k = self.k
        cyc = k['FETCH_HEAD'] + 2 * (st['d6'] - 8)                    # lsr.w d7,d1 with d7 = d6 - 8
        if self._peek(st['bit'], 6) == 0x3F:
            cyc += k['INLINE_A']
            st['d6'] -= 6
            r1 = st['d6'] < 9
            st['d6'] += 8 if r1 else 0
            cyc += k[('INLINE_REFILL1', r1)]
            st['d6'] -= 7
            cyc += k['INLINE_B'] + 2 * st['d6']                       # lsr.w d6,d1
            r2 = st['d6'] < 9
            st['d6'] += 8 if r2 else 0
            cyc += k[('INLINE_REFILL2', r2)] + k['INLINE_TAIL']
            v = self._peek(st['bit'] + 6, 7)
            st['bit'] += 13
            st['pal'], run = v & 0xF, (v >> 4) + 1
        else:
            for n in range(1, 9):
                hit = codes.get((n, self._peek(st['bit'], n)))
                if hit:
                    break
            else:
                raise ValueError('no code at bit %d' % st['bit'])
            st['bit'] += n
            st['d6'] -= n
            r = st['d6'] < 9
            st['d6'] += 8 if r else 0
            cyc += k['TABLE_BODY'] + k[('TABLE_REFILL', r)] + k['TABLE_TAIL']
            st['pal'], run = hit
        st['d0'] = run - 1
        return cyc

    def _row(self, st, codes, xor, last, rows):
        k = self.k
        if st['d0'] > 0:
            cyc = k['NEWROW_CARRY']
            st['d0'] -= 1
        else:
            cyc = k['NEWROW_FETCH'] + self._fetch(st, codes)
        row = 0
        for px in range(8):
            cyc += k['PIXEL']
            row = (row << 4) | st['pal']
            if px == 7:
                break
            if st['d0'] > 0:
                cyc += k['ROW_CONT_MORE']
                st['d0'] -= 1
            else:
                cyc += k['ROW_CONT_FETCH'] + self._fetch(st, codes)
        cyc += k['ROW_END'] + k[('WRITER', xor, last)]
        st['prev'] = (st['prev'] ^ row) if xor else row
        rows.append(st['prev'])
        return cyc
