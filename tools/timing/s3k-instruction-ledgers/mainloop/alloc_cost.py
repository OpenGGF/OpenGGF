#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, cursor slot and per-slot occupancy an object manager holds, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K SST allocation/deletion instruction-cycle ledger (kos46 research R14, 2026-10-17; source skdisasm 044fa46).

AllocateObject ($1BAF2), AllocateObjectAfterCurrent ($1BAFA) and Delete_Current_Sprite / Delete_Referenced_Sprite
($1ABB6/$1ABB8), priced per execution from verified ROM bytes with the pinned GPGX 051d430d table (dbeq: condition
true = table base, counter expired = base + 2) or Motorola. These are the child-scheduling and deletion building
blocks called from object handlers; the handlers themselves stay open.

Shipped semantics (FixBugs = 0), kept exactly:
  * AllocateObject starts at Dynamic_object_RAM and lea's first, so the first slot checked is Dynamic_object_RAM+1
    (SST slot 4) and the 90 checks run to slot 93, the first Level_object_RAM slot.
  * AllocateObjectAfterCurrent takes its loop counter from .lookup[(Dynamic_object_RAM_end - a0) >> 6] ($1BB16, 104
    bytes, "divide by $40 even though SSTs are $4A"; the source notes the division should round up). A negative entry
    returns without searching. The table has 104 entries (indices 0..103); callers in slots 0..3 (players, reserved,
    first dynamic slot) produce indices 104..107 and the ROM reads the code bytes after the table as the counter, the
    rounding mistake the source comment admits. That is deterministic ROM behaviour and is executed as shipped
    (Result flag beyond_table). For a0 at or past Dynamic_object_RAM_end the word wraps; any search that would pass
    Object_RAM_end is refused (fail closed).
  * A child found after the cursor is dispatched this frame by Process_Sprites; one found before it runs next frame.
  * Delete_Referenced_Sprite clears 18 longs + 1 word ($4A bytes), so the slot reads empty to the cursor.
Occupancy is the object manager's per-slot code pointer state at the call (engine-owned), never trace data.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import m68k_timing  # noqa: E402

ALLOC, AFTER, LOOP, DBEQ, RET, LOOKUP = 0x1BAF2, 0x1BAFA, 0x1BB0A, 0x1BB10, 0x1BB14, 0x1BB16
DELETE, DELETE_REF = 0x1ABB6, 0x1ABB8
SLOTS, OBJECT_SIZE = 110, 0x4A
PINS = {0x1BAF2: 0x43F8, 0x1BAF6: 0x7059, 0x1BAF8: 0x6010, 0x1BAFA: 0x2248, 0x1BAFC: 0x303C, 0x1BB00: 0x9048,
        0x1BB02: 0xEC48, 0x1BB04: 0x103B, 0x1BB08: 0x6B0A, 0x1BB0A: 0x43E9, 0x1BB0E: 0x4A91, 0x1BB10: 0x57C8,
        0x1BB14: 0x4E75, 0x1ABB6: 0x2248, 0x1ABB8: 0x7011, 0x1ABBA: 0x7200, 0x1ABBC: 0x22C1, 0x1ABBE: 0x51C8,
        0x1ABC2: 0x32C1, 0x1ABC4: 0x4E75, 0x1AAE0: 0x41F8}


class AllocLedger:
    def __init__(self, rom, cost):
        """cost(ins, outcome) with outcome 'seq' | 'taken' | 'fall' | 'expired' (dbcc counter ran out)."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        for pc, target in ((DBEQ, LOOP), (0x1BB08, RET), (0x1BAF8, LOOP), (0x1ABBE, 0x1ABBC)):
            if self.dec.decode(pc).target != target:
                raise ValueError('branch at %06X no longer targets %06X' % (pc, target))
        w = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        self.object_ram = 0xFFFF0000 | w(0x1AAE2)
        self.dyn = 0xFFFF0000 | w(0x1BAF4)
        self.dyn_end = 0xFFFF0000 | w(0x1BAFE)
        self.dyn_slot = (self.dyn - self.object_ram) // OBJECT_SIZE
        if (self.dyn - self.object_ram) % OBJECT_SIZE or (self.dyn_end - self.dyn) != 90 * OBJECT_SIZE:
            raise ValueError('SST layout differs from the pinned source')
        for i in range(104):
            if rom[LOOKUP + i] != (((i + 1) * 0x40 - 1) // OBJECT_SIZE - 1) & 0xFF:
                raise ValueError('lookup table entry %d differs from the source formula' % i)

    def slot_addr(self, k):
        return self.object_ram + k * OBJECT_SIZE

    def _walk(self, entry, start_slot, counter, occupancy):
        """Executes from `entry`; returns cycles, counts, found slot or None."""
        cycles, counts = 0, {}
        pc, slot, d0 = entry, start_slot, counter
        while True:
            ins = self.dec.decode(pc)
            counts[pc] = counts.get(pc, 0) + 1
            nxt, outcome = pc + ins.length, 'seq'
            if pc == 0x1BB08:                                   # bmi.s .return
                take = d0 & 0x80 != 0
                outcome, nxt = ('taken', RET) if take else ('fall', nxt)
                d0 = None if take else d0
            elif pc == 0x1BAF8:                                 # bra.s .loop
                nxt = ins.target
            elif pc == LOOP:
                slot += 1
                if slot >= SLOTS:
                    raise ValueError('search ran past Object_RAM_end')
            elif pc == DBEQ:
                free = occupancy[slot] == 0
                if free:
                    outcome = 'fall'
                else:
                    d0 -= 1
                    outcome = 'expired' if d0 == -1 else 'taken'
                    nxt = LOOP if d0 != -1 else nxt
                if free:
                    found = slot
                elif d0 == -1:
                    found = None
            elif pc == RET:
                cycles += self.cost(ins, 'seq')
                if d0 is None:
                    return cycles, counts, None
                return cycles, counts, found
            cycles += self.cost(ins, outcome)
            pc = nxt

    def after_current(self, cursor_slot, occupancy):
        if len(occupancy) != SLOTS:
            raise ValueError('occupancy must cover all %d SST slots' % SLOTS)
        a0 = self.slot_addr(cursor_slot)
        idx = ((self.dyn_end - a0) & 0xFFFF) >> 6
        self.beyond_table = idx >= 104
        return self._walk(AFTER, cursor_slot, self.rom[LOOKUP + idx], occupancy)

    def allocate(self, occupancy):
        if len(occupancy) != SLOTS:
            raise ValueError('occupancy must cover all %d SST slots' % SLOTS)
        return self._walk(ALLOC, self.dyn_slot, 89, occupancy)

    def delete(self, current=True):
        """Delete_Current_Sprite (movea.l a0,a1 first) or Delete_Referenced_Sprite: 18 dbf iterations + word."""
        cycles, pc, d0 = 0, DELETE if current else DELETE_REF, None
        while True:
            ins = self.dec.decode(pc)
            nxt, outcome = pc + ins.length, 'seq'
            if pc == 0x1ABB8:
                d0 = 0x11
            if pc == 0x1ABBE:
                if d0:
                    d0 -= 1
                    outcome, nxt = 'taken', ins.target
                else:
                    outcome = 'expired'
            cycles += self.cost(ins, outcome)
            if pc == 0x1ABC4:
                return cycles
            pc = nxt


def gpgx_cost(rom, gpgx_table):
    """Pinned GPGX costs, with the dbcc counter-expired branch charged base + 2 (gpgx_cycles.cost 'fall' hi)."""
    sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
    import gpgx_cycles
    word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731

    def cost(ins, outcome):
        k = gpgx_cycles.cost(gpgx_table, ins, word(ins.pc), word)
        if isinstance(k, dict):
            if outcome == 'taken':
                return k['taken'][0]
            if outcome == 'expired':
                return k['fall'][1]
            return k['fall'][0]
        if k[0] != k[1]:
            raise ValueError('operand-dependent cost at %06X' % ins.pc)
        return k[0]
    return cost


def motorola_cost(ins, outcome):
    if ins.kind in ('cond', 'dbcc'):
        if outcome == 'taken':
            return ins.taken.lo
        return ins.fall.hi if outcome == 'expired' else ins.fall.lo
    if ins.cost.lo != ins.cost.hi:
        raise ValueError('operand-dependent cost at %06X' % ins.pc)
    return ins.cost.lo
