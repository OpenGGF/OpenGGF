#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, Nemesis queue occupancy an engine PLC owner holds, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Process_Nem_Queue_2 instruction-cycle ledger (kos46 research prototype, 2026-10-17).

Runs the shipped Process_Nem_Queue_2 ($17F4) and Process_Nem_Queue_Main / ShiftUp ($180C-$1880) on mini68k with the
Nemesis queue RAM an engine PLC owner holds, charging each executed instruction its pinned GPGX 051d430d (or
Motorola) time. Control flow here depends only on Nem_patterns_left and the 3-pattern frame budget; the empty path
(Nem_patterns_left == 0) is fully closed.

OPEN, never estimated: Nem_PCD_NewRow (one call per pattern decoded this VInt). Its cost is a function of the
compressed bitstream from the decoder's resume point: Nem_decomp_queue head source pointer, Nem_decomp_vars
(WriteRowToVDP vs _XOR), Nem_repeat_count, Nem_palette_index, Nem_previous_row, Nem_data_word, Nem_shift_value and the
Nem_code_table that Process_Nem_Queue_Init built in the main loop with Nem_Build_Code_Table. Those are all
determined by (PLC art ROM address, XOR flag, patterns already decoded); pricing them needs the ROM Nemesis decoder
routines (Nem_PCD_NewRow, Nem_PCD_InlineData, Nem_PCD_WriteRowToVDP[_XOR], Nem_Build_Code_Table) executed, which
this ledger does not do. A nonempty result therefore carries the open term and must not be totalled.

FixBugs = 0 (shipped ROM): ShiftUp moves 22 longwords and never clears the 16th slot; it is executed as shipped.
"""
import os
import sys
from dataclasses import dataclass

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', '..', 'm68k-closure'))
import vdp_forms  # noqa: E402

ENTRY, SENTINEL = 0x17F4, 0xFFFFF0
PINS = {0x17F4: 0x4A78, 0x17F8: 0x6776, 0x17FA: 0x31FC, 0x1806: 0x0678, 0x180C: 0x49F9, 0x1816: 0x0040,
        0x181A: 0x4840, 0x1840: 0x3A7C, 0x1844: 0x6100, 0x1848: 0x5378, 0x184C: 0x6724, 0x184E: 0x5378,
        0x1852: 0x66EC, 0x1870: 0x4E75, 0x1872: 0x41F8, 0x1876: 0x7015, 0x1878: 0x20E8, 0x187C: 0x51C8,
        0x1880: 0x4E75}
RANGES = ((0x17F4, 0x1872), (0x1872, 0x1882))
QUEUE_SLOTS = 16                                  # Nem_decomp_queue ds.b 6*$10 (sonic3k.constants.asm:622)


@dataclass
class Nem:
    """Nemesis queue state when VInt runs (engine PLC owner)."""
    patterns_left: int = 0       # Nem_patterns_left (set by Process_Nem_Queue_Init from the art header & $7FFF)
    queue: tuple = ()            # (source long, VRAM destination word) entries, head first


class NemLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        w = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        self.patterns_left = 0xFFFF0000 | w(0x17F6)
        self.frame_left = 0xFFFF0000 | w(0x17FE)
        self.queue = 0xFFFF0000 | w(0x1874)
        import m68k_timing
        self.dec = m68k_timing.Decoder(rom)
        self.newrow = self.dec.decode(0x1844).target

    def run(self, n):
        if not 0 <= n.patterns_left <= 0x7FFF:
            raise ValueError('Nem_patterns_left %d outside the header range' % n.patterns_left)
        if n.patterns_left and not n.queue:
            raise ValueError('patterns left with an empty queue is not a ROM-reachable state')
        if len(n.queue) > QUEUE_SLOTS:
            raise ValueError('Nemesis queue has %d slots' % QUEUE_SLOTS)
        cpu = vdp_forms.CPU(self.rom)
        cpu.write(self.patterns_left, 'w', n.patterns_left)
        for k, (src, dst) in enumerate(n.queue):
            cpu.write(self.queue + 6 * k, 'l', src)
            cpu.write(self.queue + 6 * k + 4, 'w', dst)
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = ENTRY
        cycles, counts, terms = 0, {}, {}
        for _ in range(10000):
            if cpu.pc == SENTINEL:
                return cycles, counts, {k: v for k, v in terms.items()}, cpu
            pc = cpu.pc
            if not any(a <= pc < b for a, b in RANGES):
                raise ValueError('walk left Process_Nem_Queue_2 at %06X' % pc)
            ins = self.dec.decode(pc)
            outcome = cpu.step({self.newrow: 'Nem_PCD_NewRow'}, terms)
            counts[pc] = counts.get(pc, 0) + 1
            cycles += self.cost(ins, outcome)
        raise RuntimeError('Process_Nem_Queue_2 did not return')
