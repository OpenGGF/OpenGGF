#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R17 (2026-10-17; frozen at R17 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, both palettes, Palette_fade_info, Water_flag, zone/act, fade timers (palette-owner inputs).
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K palette fade micro-execution ledger (kos46 research R17, 2026-10-17; skdisasm 044fa46 sonic3k.asm, FixBugs = 0).

Executes, from verified ROM bytes on a private mini68k model (palette_forms.py), charging pinned GPGX 051d430d (or
Motorola) time per instruction (../deform costs(): dbcc expired = base + 2):
  Pal_ToBlack $3C04 / Pal_DecColor $3C32      one step toward black: per colour red, else green, else blue
  Pal_FromBlack $3B14 / Pal_AddColor $3B54    one step toward Target_palette; whole-word compares (+$200, +$20, +2)
  Pal_ToWhite $3D50 / Pal_AddColor2 $3D7E     one step toward $EEE: red, else green, else blue
  Pal_FromWhite $3CA4 / Pal_DecColor2 $3CF0   every third call (Pal_fade_delay2) all components step toward target
  Pal_FillBlack $3B80 / Pal_FillWhite $3C62   fill the range (zone/act $0D01 and $1701 fill white instead)
  Animate_Palette $3BB8                       Palette_fade_timer < 0: return; > 0: decrement, then FromBlack (or
                                              FromWhite for $0D01/$1701); == 0: jmp AnPal_Load (OpenPath)
The range is Palette_fade_info: offset byte ($F626) and count-1 byte ($F627) into the 64-colour palettes. ToBlack and
ToWhite always also step Water_palette; FromBlack, FromWhite and FillBlack touch the water copy only when Water_flag.
A range that runs past the 64-colour palette touches the following RAM: refused here.
Blocking fades Pal_FadeFromBlack $3AF0, Pal_FadeToBlack $3BE4, Pal_FadeFromWhite $3C82, Pal_FadeToWhite $3D30 (set
Palette_fade_info = $3F; FromBlack/FromWhite fill first; 22 iterations of: V_int_routine = $12, Wait_VSync, one step,
Process_Nem_Queue_Init): run_fade() takes the Wait_VSync test count per iteration as a timing-model OUTPUT (the
V-int arrives before that test), never from traces; the VInt_12 handler and Process_Nem_Queue_Init (R12) are
separate terms reported in `terms`.
Inputs (engine-owned palette owner): Normal/Water/Target/Target_water palettes, Palette_fade_info, Water_flag,
Current_zone_and_act, Palette_fade_timer, Pal_fade_delay2. Outputs: cycles, per-PC counts, palettes and counters after.
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import palette_forms  # noqa: E402
import m68k_timing  # noqa: E402

ENTRIES = {'Pal_FromBlack': 0x3B14, 'Pal_FillBlack': 0x3B80, 'Animate_Palette': 0x3BB8, 'Pal_ToBlack': 0x3C04,
           'Pal_FillWhite': 0x3C62, 'Pal_FromWhite': 0x3CA4, 'Pal_ToWhite': 0x3D50}
RANGE = (0x3B14, 0x3BE4), (0x3C04, 0x3C82), (0x3CA4, 0x3D30), (0x3D50, 0x3DBE)
ANPAL_LOAD, SENTINEL = 0x2142, 0xFFFFF0
FADES = {'Pal_FadeFromBlack': (0x3AF0, 0x3B14), 'Pal_FadeToBlack': (0x3BE4, 0x3C04),
         'Pal_FadeFromWhite': (0x3C82, 0x3CA4), 'Pal_FadeToWhite': (0x3D30, 0x3D50)}
WAIT_VSYNC, WAIT_TEST, NEM_INIT = 0x1D18, 0x1D1C, 0x1780
PINS = {0x3B14: 0x7000, 0x3B2A: 0x6128, 0x3B30: 0x4A38, 0x3B54: 0x3419, 0x3B58: 0xB642, 0x3B64: 0x6204, 0x3B78: 0x5458,
        0x3B80: 0x0C78, 0x3BAE: 0x3141, 0x3BB8: 0x4A78, 0x3BBC: 0x6B24, 0x3BDC: 0x4EF9, 0x3C04: 0x7000, 0x3C14: 0x611C,
        0x3C32: 0x3410, 0x3C3E: 0x5558, 0x3C4A: 0x0458, 0x3C58: 0x0458, 0x3C5E: 0x5448, 0x3C7C: 0x4278, 0x3CA4: 0x5378,
        0x3CF0: 0x1419, 0x3D18: 0x0403, 0x3D2A: 0x8605, 0x3D7E: 0x3410, 0x3D80: 0x0C42, 0x3DB4: 0x0658,
        0x3AF0: 0x31FC, 0x3BE4: 0x31FC, 0x3C82: 0x31FC, 0x3D30: 0x31FC, 0x3BEE: 0x11FC, 0x1D18: 0x46FC,
        0x1D1C: 0x4A38, 0x1D20: 0x66FA}
OPERANDS = {'normal': (0x3B16, 2), 'target': (0x3B1A, 2), 'water': (0x3B38, 2), 'target_water': (0x3B3C, 2),
            'fade_info': (0x3B1E, 2), 'fade_count': (0x3B26, 2), 'water_flag': (0x3B30, 2), 'zone_act': (0x3B80, 4),
            'fade_timer': (0x3BB8, 2), 'delay2': (0x3CA4, 2)}


class OpenPath(Exception):
    pass


@dataclass
class PalState:
    normal: tuple = (0,) * 64
    water: tuple = (0,) * 64
    target: tuple = (0,) * 64
    target_water: tuple = (0,) * 64
    fade_offset: int = 0              # Palette_fade_info high byte: byte offset into the palette
    fade_count: int = 0x3F            # Palette_fade_count: colours - 1
    water_flag: bool = False
    zone_act: int = 0
    fade_timer: int = 0               # Palette_fade_timer (signed word)
    delay2: int = 0                   # Pal_fade_delay2 (signed word)


@dataclass
class PalResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    after: PalState = None
    terms: dict = field(default_factory=dict)


class PaletteLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        w = lambda a: 0xFFFF0000 | ((rom[a] << 8) | rom[a + 1])  # noqa: E731
        self.ram = {k: w(pc + o) for k, (pc, o) in OPERANDS.items()}
        if self.ram['fade_count'] != self.ram['fade_info'] + 1:
            raise ValueError('Palette_fade_count is not Palette_fade_info + 1')
        if int.from_bytes(rom[0x3BDE:0x3BE2], 'big') != ANPAL_LOAD:
            raise ValueError('Animate_Palette AnPal_Load target moved')

    def build(self, s):
        if s.fade_offset & 1 or s.fade_offset + 2 * (s.fade_count + 1) > 0x80:
            raise ValueError('fade range runs past the 64-colour palette (touches following RAM)')
        cpu = palette_forms.CPU(self.rom)
        for key in ('normal', 'water', 'target', 'target_water'):
            for k, c in enumerate(getattr(s, key)):
                cpu.write(self.ram[key] + 2 * k, 'w', c)
        cpu.write(self.ram['fade_info'], 'b', s.fade_offset)
        cpu.write(self.ram['fade_count'], 'b', s.fade_count)
        cpu.write(self.ram['water_flag'], 'b', int(s.water_flag))
        cpu.write(self.ram['zone_act'], 'w', s.zone_act)
        cpu.write(self.ram['fade_timer'], 'w', s.fade_timer & 0xFFFF)
        cpu.write(self.ram['delay2'], 'w', s.delay2 & 0xFFFF)
        return cpu

    def run(self, name, s):
        return self._run(ENTRIES[name], s, RANGE, None)

    def run_fade(self, name, s, wait_tests):
        """Whole blocking fade; wait_tests[i] = Wait_VSync tests in iteration i (timing-model output, >= 1)."""
        if len(wait_tests) != 22 or min(wait_tests) < 1:
            raise ValueError('22 iterations, each testing V_int_routine at least once')
        lo, hi = FADES[name]
        return self._run(lo, s, RANGE + ((lo, hi), (WAIT_VSYNC, WAIT_VSYNC + 0xC)), list(wait_tests))

    def _run(self, entry, s, ranges, waits):
        cpu = self.build(s)
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = entry
        r = PalResult()
        r.terms = {}
        vint = 0xFFFF0000 | ((self.rom[0x3BF2] << 8) | self.rom[0x3BF3])     # move.b #$12,(V_int_routine).w operand
        tests = 0
        for _ in range(400000):
            pc = cpu.pc
            if waits is not None and pc == WAIT_TEST:
                tests += 1
                if tests == waits[0]:
                    cpu.write(vint, 'b', 0)                                  # the V-int has run (separate term)
                    r.terms['VInt_12'] = r.terms.get('VInt_12', 0) + 1
                    waits.pop(0)
                    tests = 0
            if pc == SENTINEL:
                rd = lambda key: tuple(cpu.read(self.ram[key] + 2 * k, 'w') for k in range(64))  # noqa: E731
                sw = lambda v: v - 0x10000 if v & 0x8000 else v  # noqa: E731
                r.after = PalState(rd('normal'), rd('water'), rd('target'), rd('target_water'),
                                   cpu.read(self.ram['fade_info'], 'b'), cpu.read(self.ram['fade_count'], 'b'),
                                   bool(cpu.read(self.ram['water_flag'], 'b')), cpu.read(self.ram['zone_act'], 'w'),
                                   sw(cpu.read(self.ram['fade_timer'], 'w')), sw(cpu.read(self.ram['delay2'], 'w')))
                return r
            if pc == ANPAL_LOAD:
                raise OpenPath('Animate_Palette with Palette_fade_timer 0 jumps to AnPal_Load (zone palette cycling)')
            if not any(a <= pc < b for a, b in ranges):
                raise ValueError('walk left the palette routines at %06X' % pc)
            ins = self.dec.decode(pc)
            outcome = cpu.step({NEM_INIT: 'Process_Nem_Queue_Init'}, r.terms)
            r.counts[pc] = r.counts.get(pc, 0) + 1
            r.cycles += self.cost(ins, outcome)
        raise RuntimeError('palette routine did not return')
