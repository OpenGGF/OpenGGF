#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R10 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, the HUD RAM values an engine owner holds, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""UpdateHUD instruction-cycle ledger (kos46 research prototype, 2026-10-17).

Runs the shipped UpdateHUD ($DD06) and its draw callees (DrawSixDigitNumber $DF8A, DrawThreeDigitNumber $DF80,
DrawTwoDigitNumber $E062, DrawSingleDigitNumber $E05A, HUD_DrawZeroRings $DE88, HUD_Lives $E11E) on mini68k with
the HUD RAM an engine owner holds, charging each executed instruction its pinned GPGX 051d430d (or Motorola) time.
Digits come from repeated subtraction in the ROM (no DIVU on this path). VDP data/control writes cost only their
instruction time; FIFO waits, DMA and renderer tile work are separate. Open terms (not entered): Kill_Character
(time over), HUD_Debug (debug placement), loc_DE7E (competition HUD).
RAM addresses are read from the instruction operands at pinned PCs, not assumed.
"""
import os
import sys
from dataclasses import dataclass

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'mini68k'))
import mini68k  # noqa: E402
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'm68k-closure'))

ENTRY, SENTINEL = 0xDD06, 0xFFFFF0
PINS = {0xDD06: 0x4E71, 0xDD0E: 0x4A78, 0xDD1E: 0x4A38, 0xDD36: 0x4A38, 0xDD56: 0x4A38, 0xDD66: 0x4A78,
        0xDD6C: 0x43F8, 0xDD2E: 0x2238, 0xDD4E: 0x3238, 0xDDBE: 0x4A38, 0xE126: 0x1238, 0xDD16: 0x4A78}
# symbol -> (pc of the instruction that names it, offset of its abs.w operand)
OPERANDS = {'competition_mode': (0xDD0E, 2), 'debug_mode': (0xDD16, 2), 'update_score': (0xDD1E, 2),
            'update_rings': (0xDD36, 2), 'update_timer': (0xDD56, 2), 'game_paused': (0xDD66, 2),
            'timer': (0xDD6C, 2), 'score': (0xDD2E, 2), 'ring_count': (0xDD4E, 2), 'update_lives': (0xDDBE, 2),
            'life_count': (0xE126, 2)}



@dataclass
class Hud:
    """Engine-owned HUD state at VInt (Update_HUD_* dirty flags and the values UpdateHUD draws/advances)."""
    competition_mode: int = 0
    debug_mode: int = 0
    update_score: int = 0
    score: int = 0
    update_rings: int = 0          # 0 none, >0 redraw, <0 (bit 7) clear to zero art first
    ring_count: int = 0
    update_timer: int = 0          # 0 stopped, >0 running, <0 (bit 7) redraw once then running
    game_paused: int = 0
    timer: tuple = (0, 0, 0, 0)    # Timer long: (unused byte, minute, second, frame)
    update_lives: int = 0
    life_count: int = 3


class HudLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.addr = {k: 0xFFFF0000 | ((rom[pc + o] << 8) | rom[pc + o + 1]) for k, (pc, o) in OPERANDS.items()}
        import m68k_timing
        self.dec = m68k_timing.Decoder(rom)
        # open callees resolved from the branch targets in UpdateHUD
        self.open = {self.target(0xDD12): 'CompetitionHUD', self.target(0xDD1A): 'DebugHUD',
                     self.target(0xDD76): 'UpdateHUD_TimeOver'}

    def target(self, pc):
        return self.dec.decode(pc).target

    def run(self, h):
        cpu = mini68k.CPU(self.rom)
        w = lambda key, size, v: cpu.write(self.addr[key], size, v)  # noqa: E731
        w('competition_mode', 'w', h.competition_mode); w('debug_mode', 'w', h.debug_mode)
        w('update_score', 'b', h.update_score & 0xFF); w('score', 'l', h.score)
        w('update_rings', 'b', h.update_rings & 0xFF); w('ring_count', 'w', h.ring_count)
        w('update_timer', 'b', h.update_timer & 0xFF); w('game_paused', 'w', h.game_paused)
        for k, b in enumerate(h.timer):
            cpu.write(self.addr['timer'] + k, 'b', b)
        w('update_lives', 'b', h.update_lives & 0xFF); w('life_count', 'b', h.life_count)
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = ENTRY
        cycles, counts, terms = 0, {}, {}
        for _ in range(200000):
            if cpu.pc == SENTINEL:
                return cycles, counts, terms, cpu
            if cpu.pc in self.open:
                raise ValueError('open term %s reached' % self.open[cpu.pc])
            pc = cpu.pc
            ins = self.dec.decode(pc)
            outcome = cpu.step({}, terms)
            counts[pc] = counts.get(pc, 0) + 1
            cycles += self.cost(ins, outcome)
        raise RuntimeError('UpdateHUD did not return')
