#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R16 (2026-10-17; frozen at R16 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, camera copies, Water_level, Level_frame_counter, HScroll_table and band-list pointers.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K background deformation micro-execution ledger (kos46 research R16, 2026-10-17; skdisasm 044fa46, FixBugs = 0).

Executes on a private mini68k model (deform_forms.py), from verified ROM bytes, charging the pinned GPGX 051d430d
(or Motorola) time per instruction:
  * ApplyDeformation / ApplyDeformation3 / ApplyDeformation2: S&K copy $4F0CE (sonic3k.asm) and the byte-identical
    S3-half copy $239E8C (s3.asm ApplyDeformation at $39E8C + $200000). Control flow depends only on the band-height
    table a4 (ROM word list; bit 15 marks a band with one scroll word per line), Camera_Y_pos_BG_copy and the line
    count d1 ($DF for ApplyDeformation); the scroll words at a5 and Camera_X_pos_copy only change the data written
    to H_scroll_buffer (one long per line: -camera X << 16 | -scroll).
  * AIZ1_Deform ($23B03E, s3.asm $3B03E): straight-line plus fixed 6- and 13-iteration loops, constant cost; writes
    Camera_Y_pos_BG_copy = Camera_Y_pos_copy asr 1, HScroll_table band scrolls, and adds $2000 to HScroll_table+$3C.
  * AIZ1_ApplyDeformWater ($23B0D4..$23B152): when Water_level - Camera_Y_pos_copy (signed) >= $E0 it jumps to the S3
    ApplyDeformation with a4 = AIZ1_DeformArray ($23B258), a5 = HScroll_table+8. Otherwise (water split, loc_3B0EE)
    ApplyDeformation3 draws the lines above the water, MakeFGDeformArray ($239E74) builds the per-line FG scroll at
    HScroll_table+$40 from AIZ1_WaterFGDeformDelta ($23A170) and ApplyFGandBGDeformation ($239F18, byte-identical to
    the S&K $4F1F0) draws the lines below with AIZ1_WaterBGDeformDelta ($23A3B0) ripple, both indexed by
    Level_frame_counter and Water_level. A water line at or above the screen top (difference <= 0) gives
    ApplyDeformation3 a negative line count, which writes past H_scroll_buffer: refused (OpenPath).
Inputs are engine-owned: camera copies, Water_level, the HScroll_table contents (zone deformation state), and the
ROM tables. Outputs: cycles, per-PC counts, H_scroll_buffer longs, HScroll_table/Camera_Y_pos_BG_copy next state.
Hardware terms (H-scroll DMA in VInt, FIFO, refresh) are separate.
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import deform_forms  # noqa: E402
import m68k_timing  # noqa: E402

SK_APPLY, S3_APPLY, AIZ1_DEFORM, AIZ1_WATER, AIZ1_ARRAY = 0x4F0CE, 0x239E8C, 0x23B03E, 0x23B0D4, 0x23B258
S3_MAKEFG, SK_MAKEFG, S3_FGBG, SK_FGBG, WATER_END = 0x239E74, 0x4F0B6, 0x239F18, 0x4F1F0, 0x23B152
FG_DELTA, BG_DELTA = 0x23A170, 0x23A3B0
MAKEFG_LEN, FGBG_LEN = 0x18, 0x9C
APPLY_LEN = 0x8C
SENTINEL = 0xFFFFF0
OFF = {'ApplyDeformation': 0, 'ApplyDeformation3': 4, 'ApplyDeformation2': 0x10}
PINS = {0x23B03E: 0x3038, 0x23B042: 0xE240, 0x23B054: 0xEA80, 0x23B0A2: 0x06B8, 0x23B0AA: 0xE280, 0x23B0B8: 0x51C9,
        0x23B0CE: 0x51C9, 0x23B0D2: 0x4E75, 0x23B0D4: 0x49FA, 0x23B0E4: 0x0C41, 0x23B0E8: 0x6D04, 0x23B0EA: 0x4EFA}
APPLY_PINS = {0x00: 0x323C, 0x04: 0x43F8, 0x10: 0x341C, 0x12: 0x5BC4, 0x1C: 0x6B0E, 0x48: 0x4A04, 0x5C: 0x51C8,
              0x6E: 0x51C8, 0x8A: 0x4E75}


class OpenPath(Exception):
    pass


@dataclass
class DeformState:
    """Engine-owned RAM read or written by the deformation routines."""
    camera_x: int = 0                 # Camera_X_pos_copy
    camera_y: int = 0                 # Camera_Y_pos_copy (AIZ1_Deform / water test)
    camera_bg_y: int = 0              # Camera_Y_pos_BG_copy (ApplyDeformation; AIZ1_Deform overwrites it)
    water_level: int = 0x1000         # Water_level ($1000 when the zone has no water)
    level_frame_counter: int = 0      # Level_frame_counter (water ripple phase)
    hscroll_table: bytes = bytes(0x80)   # HScroll_table[$00..$7F] (zone deformation state)


@dataclass
class DeformResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    hscroll: tuple = ()               # H_scroll_buffer longs written ($E0 lines unless d1 differs)
    after: DeformState = None


class DeformLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        for base in (SK_APPLY, S3_APPLY):
            for off, op in APPLY_PINS.items():
                if ((rom[base + off] << 8) | rom[base + off + 1]) != op:
                    raise ValueError('ApplyDeformation at %06X+%X is not the pinned %04X' % (base, off, op))
        for a, b, n in ((SK_APPLY, S3_APPLY, APPLY_LEN), (SK_MAKEFG, S3_MAKEFG, MAKEFG_LEN), (SK_FGBG, S3_FGBG, FGBG_LEN)):
            if rom[a:a + n] != rom[b:b + n]:
                raise ValueError('S&K and S3 copies at %06X / %06X differ' % (a, b))
        for pc, op in {0x23B0EE: 0x5341, 0x23B0F0: 0x4EBA, 0x23B102: 0x0441, 0x23B11C: 0x4EBA, 0x23B142: 0xE242,
                       0x23B14E: 0x4EFA, S3_MAKEFG: 0x3001, S3_FGBG: 0x4847, S3_FGBG + 0x9A: 0x4E75}.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        w = lambda a: 0xFFFF0000 | ((rom[a] << 8) | rom[a + 1])  # noqa: E731
        self.ram = {'hscroll_buffer': w(S3_APPLY + 6), 'camera_bg_y': w(S3_APPLY + 0xA), 'camera_x': w(S3_APPLY + 0xE),
                    'camera_y': w(0x23B040), 'hscroll_table': w(0x23B062) - 0x30, 'water_level': w(0x23B0DE),
                    'level_frame_counter': w(0x23B10A)}
        if w(0x23B046) != self.ram['camera_bg_y'] or w(0x23B04A) != self.ram['camera_x']:
            raise ValueError('AIZ1_Deform camera operands differ from ApplyDeformation')
        if self.dec.decode(0x23B0EA).target != S3_APPLY or (0x23B0D6 + ((rom[0x23B0D6] << 8) | rom[0x23B0D7])) != AIZ1_ARRAY:
            raise ValueError('AIZ1_ApplyDeformWater targets moved')
        if (self.dec.decode(0x23B0F0).target, self.dec.decode(0x23B11C).target, self.dec.decode(0x23B14E).target) != \
                (S3_APPLY + 4, S3_MAKEFG, S3_FGBG):
            raise ValueError('water split callees moved')
        sd = lambda a: (((rom[a] << 8) | rom[a + 1]) ^ 0x8000) - 0x8000  # noqa: E731  (signed d16)
        if (0x23B0FC + sd(0x23B0FC)) & 0xFFFFFF != FG_DELTA or (0x23B130 + sd(0x23B130)) & 0xFFFFFF != BG_DELTA:
            raise ValueError('water delta tables moved')
        self.open = {}

    def build(self, s):
        cpu = deform_forms.CPU(self.rom)
        cpu.write(self.ram['camera_x'], 'w', s.camera_x & 0xFFFF)
        cpu.write(self.ram['camera_y'], 'w', s.camera_y & 0xFFFF)
        cpu.write(self.ram['camera_bg_y'], 'w', s.camera_bg_y & 0xFFFF)
        cpu.write(self.ram['water_level'], 'w', s.water_level & 0xFFFF)
        cpu.write(self.ram['level_frame_counter'], 'w', s.level_frame_counter & 0xFFFF)
        for k, b in enumerate(s.hscroll_table):
            cpu.write(self.ram['hscroll_table'] + k, 'b', b)
        for k in range(0x380):
            cpu.write(self.ram['hscroll_buffer'] + k, 'b', 0xA5)
        return cpu

    def walk(self, cpu, entry, ranges):
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = entry
        cycles, counts = 0, {}
        for _ in range(500000):
            pc = cpu.pc
            if pc == SENTINEL:
                return cycles, counts
            if pc in self.open:
                raise OpenPath('%s (MakeFGDeformArray / ApplyFGandBGDeformation) is not priced' % self.open[pc])
            if not any(a <= pc < b for a, b in ranges):
                raise ValueError('walk left the deformation routines at %06X' % pc)
            ins = self.dec.decode(pc)
            outcome = cpu.step({}, {})
            counts[pc] = counts.get(pc, 0) + 1
            cycles += self.cost(ins, outcome)
        raise RuntimeError('deformation did not return')

    def result(self, cpu, cycles, counts, lines):
        r = DeformResult(cycles, counts)
        hb = self.ram['hscroll_buffer']
        r.hscroll = tuple(cpu.read(hb + 4 * k, 'l') for k in range(lines))
        r.after = DeformState(cpu.read(self.ram['camera_x'], 'w'), cpu.read(self.ram['camera_y'], 'w'),
                              cpu.read(self.ram['camera_bg_y'], 'w'), cpu.read(self.ram['water_level'], 'w'),
                              cpu.read(self.ram['level_frame_counter'], 'w'),
                              bytes(cpu.read(self.ram['hscroll_table'] + k, 'b') for k in range(0x80)))
        return r

    def apply(self, s, table, scroll, entry='ApplyDeformation', copy='S3', lines=0xE0, d1=None):
        """table: ROM address of the band list (a4); scroll: RAM address of the scroll words (a5)."""
        base = S3_APPLY if copy == 'S3' else SK_APPLY
        cpu = self.build(s)
        cpu.a[4], cpu.a[5] = table, 0xFFFF0000 | (scroll & 0xFFFF)
        if entry != 'ApplyDeformation':
            if d1 is None:
                raise ValueError('ApplyDeformation3/2 need the caller-owned line count d1')
            cpu.d[1] = d1 & 0xFFFF
            lines = d1 + 1
        if entry == 'ApplyDeformation2':
            cpu.a[1] = self.ram['hscroll_buffer']
            cpu.d[0], cpu.d[3] = s.camera_bg_y & 0xFFFF, s.camera_x & 0xFFFF
        cycles, counts = self.walk(cpu, base + OFF[entry], [(base, base + APPLY_LEN)])
        return self.result(cpu, cycles, counts, lines)

    def aiz1_deform(self, s):
        cpu = self.build(s)
        cycles, counts = self.walk(cpu, AIZ1_DEFORM, [(AIZ1_DEFORM, AIZ1_WATER)])
        return self.result(cpu, cycles, counts, 0)

    def aiz1_apply_water(self, s):
        """AIZ1_ApplyDeformWater: dry path into the S3 ApplyDeformation, or the water split."""
        above = s.water_level - s.camera_y
        above = (above & 0xFFFF) - 0x10000 if above & 0x8000 else above & 0xFFFF
        if above < 0xE0 and above <= 0:
            raise OpenPath('water line at/above the screen top: ApplyDeformation3 would write past H_scroll_buffer')
        cpu = self.build(s)
        ranges = [(AIZ1_WATER, WATER_END), (S3_APPLY, S3_APPLY + APPLY_LEN), (S3_MAKEFG, S3_MAKEFG + MAKEFG_LEN),
                  (S3_FGBG, S3_FGBG + FGBG_LEN)]
        cycles, counts = self.walk(cpu, AIZ1_WATER, ranges)
        return self.result(cpu, cycles, counts, 0xE0)


def costs(rom, gpgx_table=None):
    """GPGX (gpgx_table) or Motorola cost(ins, outcome) with dbcc 'expired' = base + 2 and Scc Dn true = base + 2."""
    sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
    import gpgx_cycles
    word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731

    def scc(ins):
        return ins.kind == 'seq' and ins.name[:1] == 's' and ins.dst == 'Dn' and ins.name not in ('swap', 'sub', 'subq',
                                                                                                 'subi', 'st', 'sf')

    def gpgx(ins, outcome):
        k = gpgx_cycles.cost(gpgx_table, ins, word(ins.pc), word)
        if isinstance(k, dict):
            if outcome == 'taken':
                return k['taken'][0]
            return k['fall'][1] if outcome == 'expired' else k['fall'][0]
        if scc(ins) and k[0] != k[1]:
            return k[1] if outcome == 'taken' else k[0]
        if k[0] != k[1]:
            raise ValueError('operand-dependent cost at %06X' % ins.pc)
        return k[0]

    def motorola(ins, outcome):
        if ins.kind in ('cond', 'dbcc'):
            if outcome == 'taken':
                return ins.taken.lo
            return ins.fall.hi if outcome == 'expired' else ins.fall.lo
        if scc(ins) and ins.cost.lo != ins.cost.hi:
            return ins.cost.hi if outcome == 'taken' else ins.cost.lo
        if ins.cost.lo != ins.cost.hi:
            raise ValueError('operand-dependent cost at %06X' % ins.pc)
        return ins.cost.lo
    return gpgx if gpgx_table is not None else motorola
