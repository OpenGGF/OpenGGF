#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, `LoopState` (gates, Wait_VSync test count as a timing-model output, SST slots), a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K LevelLoop iteration skeleton ledger (kos46 research R14, 2026-10-17; source skdisasm 044fa46, FixBugs = 0).

Walks one LevelLoop iteration ($650C .. the beq.w LevelLoop at $65AC) from verified ROM bytes and charges each
executed instruction its pinned GPGX 051d430d (or Motorola) time. Entered and priced here:
  LevelLoop body; Pause_Game ($13E4) up to Pause_NoPause; Wait_VSync ($1D18) with an explicit wait count;
  Demo_PlayRecord gate ($7616); Handle_Onscreen_Water_Height no-water gate ($6C34 -> loc_6C9C);
  Process_Sprites ($1AADA) dispatch over the 110 SST slots, including the Player_1 routine >= 6 split loop;
  Process_Kos_Module_Queue ($1B28) coordinator and Queue_Kos ($1BD8).
Closed elsewhere (not entered, reported in `closed_elsewhere`): Process_Kos_Queue (Kos consumer), Process_Nem_Queue_Init
(R12). Everything else called is an OPEN term with its call count (object handlers keyed by code pointer). Paused,
Pause_Unpause, demo playback, DemoMode and water paths raise OpenPath instead of being guessed.
Wait_VSync's .loop count is an OUTPUT of the frame timing model (how long the CPU idles before the V-int); it is an
explicit argument here and must never come from trace rows. Interrupt time (VInt/H-int ledgers), acceptance, refresh,
Z80/FIFO waits and DMA freeze stay separate.
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import m68k_timing  # noqa: E402

LOOP, LOOP_BACK = 0x650C, 0x65AC
SLOTS = 110                                           # (Object_RAM_end - Object_RAM) / object_size = $1FCC / $4A
CALLS = {0x650C: 'Pause_Game', 0x6516: 'Process_Kos_Queue', 0x651C: 'Wait_VSync', 0x6524: 'Demo_PlayRecord',
         0x6528: 'Animate_Palette', 0x652E: 'SpecialEvents', 0x6534: 'Load_Sprites', 0x653A: 'Process_Sprites',
         0x6548: 'DeformBgLayer', 0x654E: 'ScreenEvents', 0x6554: 'Handle_Onscreen_Water_Height',
         0x6558: 'Load_Rings', 0x6566: 'Draw_LRZ_Special_Rock_Sprites', 0x656C: 'Animate_Tiles',
         0x6572: 'Process_Nem_Queue_Init', 0x6576: 'Process_Kos_Module_Queue', 0x657C: 'OscillateNumDo',
         0x6580: 'ChangeRingFrame', 0x6584: 'Render_Sprites', 0x6592: 'Slots_RenderLayout',
         0x6598: 'Slots_CycleOptions'}
ENTERED = {'Pause_Game', 'Wait_VSync', 'Demo_PlayRecord', 'Process_Sprites', 'Handle_Onscreen_Water_Height',
           'Process_Kos_Module_Queue'}
CLOSED_ELSEWHERE = {'Process_Kos_Queue', 'Process_Nem_Queue_Init'}
RANGES = [(0x650C, 0x65B2), (0x13E4, 0x1408), (0x14BA, 0x14BC), (0x1D18, 0x1D24), (0x7616, 0x761E),
          (0x6C34, 0x6C3C), (0x6C9C, 0x6CA2), (0x1AADA, 0x1AB32), (0x1B28, 0x1BF0)]
INNER_OPEN = {0x1BD0: 'Process_Kos_Module_Queue_Init'}          # jmp tail
PINS = {0x650C: 0x6100, 0x6544: 0x6600, 0x6564: 0x6606, 0x6590: 0x660C, 0x65A4: 0x670C, 0x65AC: 0x6700,
        0x65B0: 0x4E75, 0x13EA: 0x6702, 0x13EC: 0x4E75, 0x13F2: 0x6700, 0x13FA: 0x660C, 0x1404: 0x6700,
        0x14BA: 0x4E75, 0x1D18: 0x46FC, 0x1D20: 0x66FA, 0x761A: 0x6602, 0x6C38: 0x6700, 0x6C9C: 0x6100,
        0x1AADE: 0x662C, 0x1AAE8: 0x6610, 0x1AAF0: 0x6708, 0x1AAF8: 0x6414, 0x1AAFA: 0x7E6D, 0x1AAFE: 0x6704,
        0x1AB02: 0x4E91, 0x1AB08: 0x51CF, 0x1AB0E: 0x7E03, 0x1AB12: 0x7E59, 0x1AB16: 0x7E0F, 0x1AB1C: 0x670A,
        0x1AB22: 0x6A04, 0x1AB24: 0x4EBA, 0x1AB2C: 0x51CF, 0x1B2C: 0x6602, 0x1B30: 0x6B1C, 0x1B38: 0x64F4,
        0x1B52: 0x66DA, 0x1B64: 0x6604, 0x1B9A: 0x4EB9, 0x1BA4: 0x6630, 0x1BC8: 0x670C, 0x1BD0: 0x4EF9}


class OpenPath(Exception):
    """The iteration takes a path this ledger does not price; the named routine/inputs are missing."""


@dataclass
class LoopState:
    """Engine-owned state read by the skeleton, each named by the RAM the ROM tests."""
    ending_running: bool = False      # Ending_running_flag (st in Ending_ScreenInit, clr in Level)
    life_count: int = 3               # Life_count
    game_paused: bool = False         # Game_paused
    start_pressed: bool = False       # Ctrl_1_pressed & button_start_mask (controller owner)
    demo_mode: bool = False           # Demo_mode_flag
    restart_level: bool = False       # Restart_level_flag after Process_Sprites
    current_zone: int = 0             # Current_zone
    game_mode: int = 0x0C             # Game_mode at the loop tail ($0C level, $08 demo, else loop exit)
    water_flag: bool = False          # Water_flag (Handle_Onscreen_Water_Height gate)
    wait_iterations: int = 1          # Wait_VSync .loop tests: timing-model OUTPUT, >= 1
    teleport_active: bool = False     # Teleport_active_flag
    competition_mode: bool = False    # Competition_mode
    player1_routine: int = 2          # Player_1+routine
    slots: tuple = ()                 # code pointer per SST slot as the cursor reaches it (0 = empty), len 110
    render_visible: tuple = ()        # render_flags bit 7 per slot (read only by the split loop's draw-only slots)
    kos_modules_left: int = 0         # Kos_modules_left byte (bit 7: module decompression started)
    kos_decomp_queue_count: int = 0   # Kos_decomp_queue_count word (bit 15 busy + count)
    kos_next_module_queued: bool = False   # Kos_module_queue+6 long nonzero (next module after the shift)


@dataclass
class LoopResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    open_terms: dict = field(default_factory=dict)
    closed_elsewhere: dict = field(default_factory=dict)
    exit: str = None                  # 'loop' (beq.w LevelLoop), 'restart' (bne.w Level), 'return' (rts: Game_mode)
    dispatched: int = 0


class LevelLoopLedger:
    def __init__(self, rom, cost):
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        self.targets = {}
        for pc, name in CALLS.items():
            self.targets[self.dec.decode(pc).target] = name
        self.process_sprites = self.dec.decode(0x653A).target

    def run(self, s):
        if len(s.slots) != SLOTS:
            raise ValueError('slots must list all %d SST code pointers in cursor order' % SLOTS)
        if s.wait_iterations < 1:
            raise ValueError('Wait_VSync tests V_int_routine at least once')
        r = LoopResult()
        pc, stack = LOOP, []
        slot, waits, d7 = 0, 0, None
        mods = s.kos_modules_left
        for _ in range(200000):
            if not any(a <= pc < b for a, b in RANGES):
                raise ValueError('walk left the skeleton at %06X' % pc)
            ins = self.dec.decode(pc)
            r.counts[pc] = r.counts.get(pc, 0) + 1
            nxt, outcome = pc + ins.length, 'seq'
            stop = None
            if ins.kind == 'cond':
                take = self.decide(pc, s, mods, slot, waits)
                if pc == 0x1D20:
                    waits += 1
                if pc == 0x1AAFE and not take:
                    r.dispatched += 1
                outcome = 'taken' if take else 'fall'
                nxt = ins.target if take else nxt
                if pc == LOOP_BACK:
                    stop = 'loop' if take else None
                if pc == 0x6544 and take:
                    stop = 'restart'
                if pc == 0x65A4 and take:
                    raise OpenPath('DemoMode tail (Game_mode $08) is not priced')
            elif ins.kind == 'dbcc':
                if pc not in (0x1AB08, 0x1AB2C):
                    raise ValueError('unexpected dbcc at %06X' % pc)
                slot += 1                                    # dbf d7: next physical slot
                if d7:
                    d7 -= 1
                    outcome, nxt = 'taken', ins.target
                else:
                    d7 = None
                    outcome = 'fall'
            elif ins.kind == 'jump':
                if pc in INNER_OPEN:
                    r.open_terms[INNER_OPEN[pc]] = r.open_terms.get(INNER_OPEN[pc], 0) + 1
                    nxt = stack.pop()                       # tail jmp: its rts returns to LevelLoop
                else:
                    nxt = ins.target
            elif ins.kind in ('call', 'icall'):
                name = self.callee(pc, ins, s, slot)
                if name in CLOSED_ELSEWHERE:
                    r.closed_elsewhere[name] = r.closed_elsewhere.get(name, 0) + 1
                elif name is None:                          # entered
                    stack.append(nxt)
                    nxt = ins.target
                else:
                    r.open_terms[name] = r.open_terms.get(name, 0) + 1
            elif ins.kind == 'ret':
                if not stack:
                    stop = 'return'
                else:
                    nxt = stack.pop()
            elif ins.kind != 'seq':
                raise ValueError('unsupported control kind %s at %06X' % (ins.kind, pc))
            if pc in (0x1AAFA, 0x1AB0E, 0x1AB12, 0x1AB16):  # moveq #n,d7: slots in this pass - 1
                d7 = self.rom[pc + 1]
            if pc == 0x1B54:                                # andi.b #$7F then subq.b #1 (Kos_modules_left)
                mods = ((mods & 0x7F) - 1) & 0xFF
            r.cycles += self.cost(ins, outcome)
            if stop:
                r.exit = stop
                if slot != (0 if s.teleport_active else SLOTS):
                    raise AssertionError('slot cursor ended at %d' % slot)
                return r
            pc = nxt
        raise RuntimeError('iteration did not terminate')

    def callee(self, pc, ins, s, slot):
        if pc == 0x1AB02:                                   # jsr (a1): the slot's code pointer
            return 'handler:%06X' % (s.slots[slot] & 0xFFFFFF)
        if pc == 0x1AB24:
            return 'Draw_Sprite'
        if pc in (0x1AB10, 0x1AB14, 0x1B42):                # bsr.s sub_1AAFC / sub_1AB1A, bsr.w Queue_Kos
            return None
        if pc == 0x1B9A:
            return 'Add_To_DMA_Queue'
        if pc == 0x6C9C:
            return 'sub_714E'
        name = CALLS.get(pc)
        if name is None:
            raise ValueError('unexpected call at %06X' % pc)
        return None if name in ENTERED else name

    def decide(self, pc, s, mods, slot, waits):
        if pc == 0x13EA:
            return not s.ending_running                     # beq.s Pause_Main
        if pc == 0x13F2:
            if s.life_count == 0:
                raise OpenPath('Pause_Unpause (Life_count 0) is not priced')
            return False
        if pc == 0x13FA:
            if s.game_paused:
                raise OpenPath('paused frame (Pause_Loop) is not priced')
            return False
        if pc == 0x1404:
            if s.start_pressed:
                raise OpenPath('pause entry (Start pressed) is not priced')
            return True                                     # beq.w Pause_NoPause
        if pc == 0x1D20:
            return waits + 1 < s.wait_iterations            # bne.s .loop until the V-int clears V_int_routine
        if pc == 0x761A:
            if s.demo_mode:
                raise OpenPath('Demo_PlayRecord playback is not priced')
            return False
        if pc == 0x6C38:
            if s.water_flag:
                raise OpenPath('Handle_Onscreen_Water_Height water path is not priced')
            return True
        if pc == 0x6544:
            return s.restart_level
        if pc == 0x6564:
            return s.current_zone != 9
        if pc == 0x6590:
            return s.current_zone != 0x15
        if pc == 0x65A4:
            return s.game_mode == 0x08
        if pc == LOOP_BACK:
            return s.game_mode == 0x0C
        # Process_Sprites
        if pc == 0x1AADE:
            return s.teleport_active
        if pc == 0x1AAE8:
            return s.competition_mode
        if pc == 0x1AAF0:
            return s.player1_routine == 0x0C
        if pc == 0x1AAF8:
            return s.player1_routine >= 6                   # bhs.s loc_1AB0E (split loop, "this is broken?")
        if pc == 0x1AAFE:
            return (s.slots[slot] & 0xFFFFFFFF) == 0        # beq.s: empty slot
        if pc == 0x1AB1C:
            return (s.slots[slot] & 0xFFFFFFFF) == 0
        if pc == 0x1AB22:
            return not s.render_visible[slot]               # bpl.s: not on screen, no Draw_Sprite
        # Process_Kos_Module_Queue
        if pc == 0x1B2C:
            return s.kos_modules_left != 0
        if pc == 0x1B30:
            return bool(s.kos_modules_left & 0x80)
        if pc == 0x1B38:
            return s.kos_decomp_queue_count >= 4            # unsigned: the busy bit reads as full
        if pc == 0x1B52:
            return s.kos_decomp_queue_count != 0
        if pc in (0x1B64, 0x1BA4):
            return mods != 0
        if pc == 0x1BC8:
            return not s.kos_next_module_queued
        raise ValueError('no semantic input decides the branch at %06X' % pc)
