#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R15 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes (routines and mappings), display lists, SST renderer fields, camera copies, gates, prior SAT.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K Render_Sprites / Draw_Sprite micro-execution ledger (kos46 research R15, 2026-10-17; skdisasm 044fa46, FixBugs=0).

Executes the shipped Render_Sprites ($1AD20-$1AF6A), its piece writers sub_1AF6C ($1AF6C: plain $1AF76, x-flip
$1AFA2, y-flip $1B038, xy-flip $1AFE8) and the multi-draw clipped writers sub_1B070 ($1B070: $1B07A/$1B0C6/$1B19C/
$1B12C), and Draw_Sprite ($1ABC6), on a private mini68k model (render_forms.py) whose RAM is built only from the
inputs a production renderer/object owner holds:
  * the 8 Sprite_table_input display lists (priority levels, $80 bytes each: count word = 2 x entries, then SST
    addresses) in the order Draw_Sprite appended them;
  * per listed SST: render_flags, x_pos, y_pos, width_pixels, height_pixels, mappings (ROM pointer), mapping_frame,
    art_tile, and for multi-draw (render_flags bit 6) mainspr_childsprites plus 6-byte child records at sub2_x_pos;
  * Camera_X_pos_copy / Camera_Y_pos_copy, Screen_Y_wrap_value, Level_started_flag, Current_zone, Competition_mode,
    Spritemask_flag, Player_1 routine, and the SAT's prior contents (link bytes are never written here).
Mapping frames, piece records and the flip width/height tables are read from ROM. Every executed instruction is
charged its pinned GPGX 051d430d time (dbcc: condition true = base, counter expired = base + 2) or Motorola time.
Outputs: cycles, per-PC counts, the SAT bytes, Sprites_drawn, each SST's render_flags (on-screen bit 7), the drained
list counts. Render_HUD, Render_Rings (Level_started_flag set) and sub_1CB68 (LRZ, after level 0) write SAT entries
and move d7/a6, so skipping them would corrupt the rest of the frame: they raise OpenPath, as does competition mode. Shipped behaviour kept: the x == 0 -> 1 fix-up only in sub_1AF6C; multi-draw pieces are clipped per piece
(y-clipped pieces skip 5 bytes, 4 in the y-flip writer; x-clipped ones rewind 6 bytes and continue with dbf); the
final loop zeroes only the y word of unused SAT entries; a multi-draw main mapping_frame of 0 draws no main pieces.
VDP FIFO/DMA of the SAT and all hardware terms are separate.
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import render_forms  # noqa: E402
import m68k_timing  # noqa: E402

RENDER, DRAW, SENTINEL = 0x1AD20, 0x1ABC6, 0xFFFFF0
RANGES = ((0x1AD20, 0x1AF6C), (0x1AF6C, 0x1AFD8), (0x1AFE8, 0x1B028), (0x1B038, 0x1B070), (0x1B070, 0x1B11C),
          (0x1B12C, 0x1B18C), (0x1B19C, 0x1B1F4), (0x1ABC6, 0x1ABE8))
COMPETITION = 0x1B1F4
PINS = {0x1AD20: 0x4A78, 0x1AD24: 0x6600, 0x1AD28: 0x7E4F, 0x1AD3E: 0x4EB9, 0x1AD44: 0x4EB9, 0x1AD56: 0x0228,
        0x1AD68: 0x0806, 0x1AD70: 0x0806, 0x1AD9C: 0xC278, 0x1ADB2: 0x0028, 0x1ADC2: 0x0806, 0x1ADCE: 0xD2F1,
        0x1ADDC: 0x4EBA, 0x1ADE0: 0x5555, 0x1ADE6: 0xBBFC, 0x1ADF6: 0x4EB9, 0x1AE00: 0xBBFC, 0x1AE14: 0x51CF,
        0x1AE18: 0x0446, 0x1AE1C: 0x4446, 0x1AE42: 0x57C9, 0x1AE56: 0x4E75, 0x1AF10: 0x4EBA, 0x1AF5C: 0x4EBA,
        0x1AF64: 0x5BCB, 0x1AF6C: 0xE20E, 0x1AF98: 0x5BCC, 0x1AFB4: 0x0A42, 0x1AFBA: 0x3419, 0x1AFBC: 0x4442,
        0x1ABCA: 0xD2E8, 0x1ABCE: 0x0C51, 0x1ABDC: 0xB2FC, 0x1ABE2: 0xD2FC}
FIELD_PCS = {'render_flags': (0x1AD56, 4), 'x_pos': (0x1AD60, 2), 'y_pos': (0x1AD64, 2),
             'width_pixels': (0x1AD78, 2), 'height_pixels': (0x1AD96, 2), 'mappings': (0x1ADBC, 2),
             'mapping_frame': (0x1ADC8, 2), 'art_tile': (0x1ADD8, 2), 'priority': (0x1ABCA, 2),
             'childsprites': (0x1AF1C, 2), 'sub2_x_pos': (0x1AF26, 2)}
RAM_PCS = {'competition_mode': (0x1AD20, 2), 'sprite_table_input': (0x1AD2C, 2), 'camera': (0x1AD30, 2),
           'sprite_table': (0x1AD34, 2), 'level_started': (0x1AD38, 2), 'y_wrap': (0x1AD9C, 2),
           'current_zone': (0x1ADEE, 4), 'sprites_drawn': (0x1AE1E, 2), 'spritemask': (0x1AE22, 2),
           'player1_routine': (0x1AE28, 4)}
OPEN = {}
MAX_CHILDREN = 8                     # 6-byte records from sub2_x_pos ($18) still inside the $4A-byte SST


class OpenPath(Exception):
    pass


@dataclass
class Sprite:
    addr: int                        # SST address (RAM word, e.g. $B000 + slot * $4A)
    render_flags: int = 0x04
    x: int = 0
    y: int = 0
    width: int = 8
    height: int = 8
    mappings: int = 0
    frame: int = 0
    art_tile: int = 0
    children: tuple = ()             # multi-draw: (x, y, frame) per child
    priority: int = 0                # Draw_Sprite input (offset in units of $80)


@dataclass
class Scene:
    levels: tuple = ((),) * 8        # 8 display lists of Sprite, in Draw_Sprite order
    camera_x: int = 0
    camera_y: int = 0
    y_wrap: int = 0xFFF
    level_started: bool = False
    current_zone: int = 0
    competition: bool = False
    spritemask: int = 0
    player1_routine: int = 2
    sat_fill: int = 0xA5             # prior SAT byte contents (links are not rewritten)


@dataclass
class RenderResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    open_terms: dict = field(default_factory=dict)
    sat: bytes = b''
    sprites_drawn: int = 0
    on_screen: dict = field(default_factory=dict)
    list_counts: tuple = ()


class RenderLedger:
    def __init__(self, rom, cost):
        """cost(ins, outcome) with outcome 'seq' | 'taken' | 'fall' | 'expired' (alloc_cost.gpgx_cost style)."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        w = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        self.f = {k: w(pc + o) for k, (pc, o) in FIELD_PCS.items()}
        self.ram = {k: 0xFFFF0000 | w(pc + o) for k, (pc, o) in RAM_PCS.items()}
        self.sti_end = int.from_bytes(rom[0x1AE02:0x1AE06], 'big')
        if self.sti_end & 0xFFFFFF != (self.ram['sprite_table_input'] + 8 * 0x80) & 0xFFFFFF:
            raise ValueError('Sprite_table_input_end is not 8 levels of $80')
        if self.dec.decode(0x1AD24).target != COMPETITION:
            raise ValueError('competition branch moved')
        self.open = {self.dec.decode(0x1AD3E).target: 'Render_HUD', self.dec.decode(0x1AD44).target: 'Render_Rings',
                     self.dec.decode(0x1ADF6).target: 'sub_1CB68'}

    # -- private RAM model from engine-owned inputs
    def build(self, sc):
        cpu = render_forms.CPU(self.rom)
        put = cpu.write
        put(self.ram['competition_mode'], 'w', int(sc.competition))
        put(self.ram['camera'], 'w', sc.camera_x & 0xFFFF)
        put(self.ram['camera'] + 4, 'w', sc.camera_y & 0xFFFF)
        put(self.ram['y_wrap'], 'w', sc.y_wrap & 0xFFFF)
        put(self.ram['level_started'], 'b', int(sc.level_started))
        put(self.ram['current_zone'], 'b', sc.current_zone)
        put(self.ram['spritemask'], 'w', sc.spritemask)
        put(self.ram['player1_routine'], 'b', sc.player1_routine)
        for k in range(0x280):
            put(self.ram['sprite_table'] + k, 'b', sc.sat_fill)
        seen = set()
        if len(sc.levels) != 8:
            raise ValueError('8 priority levels')
        for lvl, sprites in enumerate(sc.levels):
            base = self.ram['sprite_table_input'] + 0x80 * lvl
            if len(sprites) > 0x3F:
                raise ValueError('a level holds at most 63 entries ($7E bytes)')
            put(base, 'w', 2 * len(sprites))
            for i, sp in enumerate(sprites):
                put(base + 2 + 2 * i, 'w', sp.addr & 0xFFFF)
                if sp.addr in seen:
                    continue
                seen.add(sp.addr)
                self.write_sst(cpu, sp)
        return cpu

    def write_sst(self, cpu, sp):
        a, f, put = 0xFFFF0000 | (sp.addr & 0xFFFF), self.f, cpu.write
        if len(sp.children) > MAX_CHILDREN:
            raise ValueError('more than %d child records would read the next SST' % MAX_CHILDREN)
        put(a + f['render_flags'], 'b', sp.render_flags)
        put(a + f['x_pos'], 'w', sp.x & 0xFFFF)
        put(a + f['y_pos'], 'w', sp.y & 0xFFFF)
        put(a + f['width_pixels'], 'b', sp.width)
        put(a + f['height_pixels'], 'b', sp.height)
        put(a + f['mappings'], 'l', sp.mappings)
        put(a + f['mapping_frame'], 'b', sp.frame)
        put(a + f['art_tile'], 'w', sp.art_tile & 0xFFFF)
        put(a + f['priority'], 'w', sp.priority)
        if sp.render_flags & 0x40:
            put(a + f['childsprites'], 'w', len(sp.children))
            for k, (cx, cy, cf) in enumerate(sp.children):
                b = a + f['sub2_x_pos'] + 6 * k
                put(b, 'w', cx & 0xFFFF); put(b + 2, 'w', cy & 0xFFFF); put(b + 5, 'b', cf)

    def walk(self, cpu, entry):
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = entry
        cycles, counts, terms = 0, {}, {}
        for _ in range(2000000):
            pc = cpu.pc
            if pc == SENTINEL:
                return cycles, counts, terms
            if pc == COMPETITION:
                raise OpenPath('Render_Sprites_CompetitionMode is not priced')
            if pc in (0x1AD3E, 0x1ADF6):
                raise OpenPath('%s draws into the SAT and is not priced' %
                               ('Render_HUD/Render_Rings' if pc == 0x1AD3E else 'sub_1CB68 (LRZ)'))
            if not any(a <= pc < b for a, b in RANGES):
                raise ValueError('walk left the renderer at %06X' % pc)
            ins = self.dec.decode(pc)
            outcome = cpu.step(self.open, terms)
            counts[pc] = counts.get(pc, 0) + 1
            cycles += self.cost(ins, outcome)
        raise RuntimeError('renderer did not return')

    def render(self, sc):
        cpu = self.build(sc)
        cycles, counts, terms = self.walk(cpu, RENDER)
        r = RenderResult(cycles, counts, terms)
        st = self.ram['sprite_table']
        r.sat = bytes(cpu.read(st + k, 'b') for k in range(0x280))
        r.sprites_drawn = cpu.read(self.ram['sprites_drawn'], 'b')
        for sprites in sc.levels:
            for sp in sprites:
                flags = cpu.read((0xFFFF0000 | sp.addr) + self.f['render_flags'], 'b')
                r.on_screen[sp.addr] = bool(flags & 0x80)
        r.list_counts = tuple(cpu.read(self.ram['sprite_table_input'] + 0x80 * k, 'w') for k in range(8))
        return r

    def draw_sprite(self, sp, counts):
        """Draw_Sprite for SST `sp` given the 8 list byte-counts; returns cycles, level used (None: dropped)."""
        cpu = render_forms.CPU(self.rom)
        for k, c in enumerate(counts):
            cpu.write(self.ram['sprite_table_input'] + 0x80 * k, 'w', c)
        self.write_sst(cpu, sp)
        cpu.a[0] = 0xFFFF0000 | sp.addr
        cycles, pcs, _ = self.walk(cpu, DRAW)
        after = [cpu.read(self.ram['sprite_table_input'] + 0x80 * k, 'w') for k in range(8)]
        changed = [k for k in range(8) if after[k] != counts[k]]
        return cycles, (changed[0] if changed else None), pcs
