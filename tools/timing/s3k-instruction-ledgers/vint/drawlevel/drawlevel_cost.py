#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, plane-buffer descriptor lists as the ROM producers write them, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""VInt_DrawLevel instruction-cycle ledger (kos46 research prototype, 2026-10-17).

Runs the shipped VInt_DrawLevel ($4E758), VInt_DrawLevel_2 and VInt_VRAMWrite ($4E7A6) on mini68k over plane-buffer
descriptors laid out exactly as the ROM producers write them, charging each executed instruction its pinned GPGX
051d430d (or Motorola) time. Loop and branch counts follow from the buffer contents the ROM reads, not from a formula.

Descriptor format (Setup_TileRowDraw / Setup_TileColumnDraw, sonic3k.asm 102711-103021): word VRAM nametable address
(nonzero; zero terminates), word n-1 with bit 15 set for a column, then 2*n longwords (two VInt_VRAMWrite passes of n
longwords: rows at +$80, columns at +2). Each producer call ends the list with clr.w (a0). n is the producer's d6
block count after the nametable wrap split: rows 1..$20 (plane width / 16 px), columns 1..$10 (plane height / 16 px).
Plane_buffer is $480 bytes (sonic3k.constants.asm:541). Plane_buffer_2_addr is nonzero only after
Slots_BackgroundEvent stores $FF1000 (sonic3k.asm:119216); LevelSetup clears it (102236).

Separate terms, never included: VDP FIFO waits on the data-port writes, interrupt acceptance, refresh, Z80 waits,
DMA freeze. Inputs outside the producer contract raise instead of being priced.
"""
import os
import sys
from dataclasses import dataclass

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', '..', 'm68k-closure'))
import vdp_forms  # noqa: E402

ENTRY, SENTINEL = 0x4E758, 0xFFFFF0
PINS = {0x4E758: 0x4DF9, 0x4E75E: 0x41F8, 0x4E762: 0x6108, 0x4E764: 0x2038, 0x4E768: 0x673A, 0x4E76C: 0x3010,
        0x4E76E: 0x672E, 0x4E770: 0x4258, 0x4E774: 0x6B0A, 0x4E786: 0x0241, 0x4E79C: 0x60CE, 0x4E7A4: 0x4E75,
        0x4E7A6: 0x4840, 0x4E7B0: 0x0040, 0x4E7BA: 0x2C98, 0x4E7BC: 0x51C9, 0x4E7C0: 0x4E75}
RANGE = (0x4E758, 0x4E7C2)
ROW_MAX, COL_MAX, BUF_BYTES = 0x20, 0x10, 0x480


@dataclass(frozen=True)
class Desc:
    vram: int          # nametable address word the producer computed (d7 + row/column offset)
    column: bool       # Setup_TileColumnDraw (bset #7) vs Setup_TileRowDraw
    blocks: int        # n: longwords per VInt_VRAMWrite pass


@dataclass
class Planes:
    """Plane-draw requests queued by the main loop before this VInt (engine plane/scroll owner)."""
    plane_buffer: tuple = ()
    plane_buffer_2: tuple = None       # None: Plane_buffer_2_addr == 0 (every zone except the Slots bonus stage)


class DrawLevelLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        w = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        self.buf = 0xFFFF0000 | w(0x4E760)          # lea (Plane_buffer).w,a0
        self.buf2_ptr = 0xFFFF0000 | w(0x4E766)     # move.l (Plane_buffer_2_addr).w,d0
        import m68k_timing
        self.dec = m68k_timing.Decoder(rom)

    def lay_out(self, cpu, base, descs):
        a, total = base, 2
        for k, d in enumerate(descs):
            lim = COL_MAX if d.column else ROW_MAX
            if not 1 <= d.blocks <= lim:
                raise ValueError('descriptor %d: %d blocks outside the producer range 1..%d' % (k, d.blocks, lim))
            if not 0 < d.vram <= 0xFFFF:
                raise ValueError('descriptor %d: VRAM word %X is a terminator or not a word' % (k, d.vram))
            total += 4 + 8 * d.blocks
            if total > BUF_BYTES:
                raise ValueError('plane buffer overflow: %d bytes > $480' % total)
            cpu.write(a, 'w', d.vram)
            cpu.write(a + 2, 'w', (d.blocks - 1) | (0x8000 if d.column else 0))
            for i in range(2 * d.blocks):
                cpu.write(a + 4 + 4 * i, 'l', 0x01000100 * (i + 1))
            a += 4 + 8 * d.blocks
        cpu.write(a, 'w', 0)                         # producer clr.w (a0)

    def run(self, p):
        cpu = vdp_forms.CPU(self.rom)
        self.lay_out(cpu, self.buf, p.plane_buffer)
        if p.plane_buffer_2 is None:
            cpu.write(self.buf2_ptr, 'l', 0)
        else:
            cpu.write(self.buf2_ptr, 'l', 0xFF1000)
            self.lay_out(cpu, 0xFFFF1000, p.plane_buffer_2)
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = ENTRY
        cycles, counts = 0, {}
        for _ in range(200000):
            if cpu.pc == SENTINEL:
                self.check_consumed(cpu, p)
                return cycles, counts, cpu
            pc = cpu.pc
            if not RANGE[0] <= pc < RANGE[1]:
                raise ValueError('walk left VInt_DrawLevel at %06X' % pc)
            ins = self.dec.decode(pc)
            outcome = cpu.step({}, {})
            counts[pc] = counts.get(pc, 0) + 1
            cycles += self.cost(ins, outcome)
        raise RuntimeError('VInt_DrawLevel did not return')

    def check_consumed(self, cpu, p):
        """ROM semantics: clr.w (a0)+ zeroes every consumed address word; the final control write restores $8F02."""
        for base, descs in ((self.buf, p.plane_buffer), (0xFFFF1000, p.plane_buffer_2 or ())):
            a = base
            for d in descs:
                if cpu.read(a, 'w') != 0:
                    raise AssertionError('descriptor at %06X not consumed' % a)
                a += 4 + 8 * d.blocks


def closed_form(p):
    """Source-derived GPGX/Motorola instruction cycles (identical here); the tests check it against the walk."""
    def body(descs):
        return 50 + sum((308 if d.column else 312) + 60 * d.blocks for d in descs)
    tail = 26 if p.plane_buffer_2 is None else 12 + body(p.plane_buffer_2)
    return 38 + body(p.plane_buffer) + 16 + tail
