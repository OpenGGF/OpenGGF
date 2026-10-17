#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R9 (2026-10-17; lag path added in R13).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes (opcode pins checked), a `State` an engine owner would hold, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K VInt / VInt_8 / Set_Kos_Bookmark instruction-cycle ledger (kos46 research prototype, 2026-10-17).

Purpose: walk the shipped VInt handler for a level frame ($FFFFF0 V_int_jump, VInt $584-$5E4, VInt_8 $7EA-$976,
Poll_Controllers $10DE-$1118, SpecialVInt_Function $4E800-$4E80E, Process_DMA_Queue $1588-$15B8, Do_Updates
skeleton, Set_Kos_Bookmark $1BF0-$1C18) from verified ROM bytes and charge each executed instruction its pinned
GPGX 051d430d (or Motorola) time. Branches are decided ONLY by explicit semantic state an engine owner would hold
(see `State`); nothing reads native data. Separate terms, never included here: interrupt acceptance (50..59),
refresh, Z80->68000 bus waits, FIFO waits, DMA freeze (priced by the DMA ledger owner).
Callees that are not closed here (VInt_DrawLevel, UpdateHUD, Process_Nem_Queue_2, non-zero SpecialVInt routines)
are returned as named open terms with their call counts; vint_frame.py composes the closed ones.
R13: the lag path (V_int_routine == 0: $58E beq.w -> VInt_0_Main $608 .. VInt_0_Done $762 -> VInt_Done $5DC) is
walked too. It skips the VBLANK wait, Set_Kos_Bookmark and Do_Updates; Result.lag marks it. VInt_8's deferral
(Water_flag and H_int_counter < 92: $946 move.b #1,(Do_Updates_in_H_int).w) is reported as Result.deferred_updates
for hint/hint_cost.py.
Hardware invariants asserted, not parameterised: the VBLANK wait at $592-$59C runs once (GPGX raises VInt only after
setting the VBLANK flag, system.c) and stopZ80's BUSACK wait at $7F2-$7FA runs once (verified against every native
bucket in the tests; a failure there means the invariant, not a cost, is wrong).
"""
import os
import sys
from dataclasses import dataclass, field

TOOL = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'm68k-closure')
sys.path.insert(0, TOOL)
import m68k_timing  # noqa: E402

MAIN, DONE = 0x1C26, 0x1CFA
CLOSED = [(0x584, 0x5E6), (0x606, 0x76E), (0x7EA, 0x978), (0x10DE, 0x111A), (0x4E800, 0x4E810), (0x1588, 0x15BA), (0x1BF0, 0x1C1A)]
OPEN_CALLEES = {0x4E758: 'VInt_DrawLevel', 0xDD06: 'UpdateHUD', 0x17F4: 'Process_Nem_Queue_2'}
PINS = {0x584: 0x4E71, 0x586: 0x48E7, 0x5B0: 0x0838, 0x5BC: 0x51C8, 0x5D8: 0x4EBB, 0x5E4: 0x4E73, 0x7EA: 0x33FC,
        0x804: 0x6732, 0x83C: 0x6626, 0x8B4: 0x673C, 0x916: 0x6100, 0x920: 0x4EB9, 0x926: 0x4EB9, 0x93C: 0x6714,
        0x952: 0x6106, 0x954: 0x4EF9, 0x95A: 0x4EB9, 0x966: 0x6100, 0x96E: 0x6700, 0x1594: 0x6714, 0x15A8: 0x66E8,
        0x1BF4: 0x6A22, 0x1C00: 0x6516, 0x1C08: 0x640E, 0x1C18: 0x4E75, 0x4E80A: 0x4EFB,
        0x608: 0x5278, 0x612: 0x671A, 0x61A: 0x6712, 0x622: 0x670A, 0x62A: 0x6702, 0x62C: 0x60AE, 0x632: 0x6700,
        0x642: 0x6708, 0x648: 0x51C8, 0x662: 0x66F6, 0x668: 0x6626, 0x6C0: 0x6000, 0x6D0: 0x6708, 0x6D6: 0x51C8,
        0x6F8: 0x66F6, 0x6FE: 0x6762, 0x716: 0x6726, 0x76A: 0x6000, 0x946: 0x11FC}
GAME_MODE_LEVEL = (0x88, 0x8C, 0x08, 0x0C)   # $60C-$62A compare order: demo/level, pre-level bit 7 first
DMA_QUEUE_SLOTS = 18          # DMA_queue $FB00..DMA_queue_slot $FBFC, 14-byte entries (sonic3k.asm 1770-1792)


@dataclass
class State:
    """Semantic inputs; every field names the RAM byte/word the ROM tests and the engine owner that must supply it."""
    vint_routine: int = 8                  # V_int_routine, set by the main loop before Wait_VSync (LevelLoop: 8)
    pal: bool = False                      # Graphics_flags bit 6 (region)
    hyper_flash_timer: int = 0             # Hyper_Sonic_flash_timer (Hyper Sonic double-jump owner)
    water_full_screen: bool = False        # Water_full_screen_flag (water owner)
    competition_mode: bool = False         # Competition_mode
    dma_queue_entries: int = 0             # Process_DMA_Queue entries this VInt (DMA ledger owner)
    special_vint_routine: int = 0          # Special_V_int_routine word (0: rts)
    water_flag: bool = False               # Water_flag
    h_int_counter: int = 0xFF              # H_int_counter (only read when water_flag)
    demo_timer: int = 0                    # Demo_timer
    kos_busy: bool = False                 # Kos_decomp_queue_count bit 15 when VInt is taken (Kos consumer)
    kos_interrupted_pc: int = None         # stacked PC when busy (Kos consumer bookmark)
    game_mode: int = 0x0C                  # Game_mode byte (lag path only: level/demo, +$80 while loading)
    sprite_table_page: int = 0             # Current_sprite_table_page (lag path, Competition_mode only)


@dataclass
class Result:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    open_terms: dict = field(default_factory=dict)
    bookmarked: bool = False
    lag: bool = False
    deferred_updates: bool = False


class VIntLedger:
    def __init__(self, rom, cost):
        """cost(ins, outcome) -> cycles where outcome is 'seq', 'taken' or 'fall' (dbcc fall = expired)."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)

    def word(self, a):
        return (self.rom[a] << 8) | self.rom[a + 1]

    def run(self, s, trace=None):
        if s.vint_routine & 0xFF and (s.vint_routine & 0x3E) == 0:
            raise ValueError('nonzero V_int_routine that masks to VInt_0 ($606, addq.w #4,sp) is not an official value')
        if s.dma_queue_entries < 0 or s.dma_queue_entries > DMA_QUEUE_SLOTS:
            raise ValueError('impossible DMA queue length')
        r = Result()
        r.cycles += 12                      # $FFFFF0 V_int_jump: jmp (VInt).l (sonic3k.asm:273 writes $4EF9), GPGX 12
        r.counts[0xFFFFF0] = 1
        pc, stack = 0x584, [None]
        dbf_left = {}
        dma_seen = 0
        for _ in range(100000):
            if pc is None:
                return r
            if not any(a <= pc < b for a, b in CLOSED):
                raise ValueError('walk left the closed routines at %06X' % pc)
            ins = self.dec.decode(pc)
            if trace is not None:
                trace.append((pc, r.cycles))             # instruction cycles elapsed before this instruction starts
            r.counts[pc] = r.counts.get(pc, 0) + 1
            nxt = pc + ins.length
            outcome = 'seq'
            if ins.kind == 'cond':
                take = self.decide(pc, s, dma_seen)
                outcome = 'taken' if take else 'fall'
                nxt = ins.target if take else nxt
                if pc == 0x1594 and not take:
                    dma_seen += 1
            elif ins.kind == 'dbcc':
                if pc not in dbf_left:
                    dbf_left[pc] = {0x5BC: 0x700, 0x648: 0x700, 0x6D6: 0x700, 0x824: 0x1F, 0x832: 0x1E}[pc]
                if dbf_left[pc] > 0:
                    dbf_left[pc] -= 1
                    outcome, nxt = 'taken', ins.target
                else:
                    del dbf_left[pc]
                    outcome = 'fall'
            elif ins.kind in ('jump',):
                nxt = ins.target
            elif ins.kind == 'call':
                if ins.target in OPEN_CALLEES:
                    name = OPEN_CALLEES[ins.target]
                    r.open_terms[name] = r.open_terms.get(name, 0) + 1
                else:
                    stack.append(nxt)
                    nxt = ins.target
            elif ins.kind == 'icall':                     # jsr VInt_Table(pc,d0.w)
                if pc != 0x5D8:
                    raise ValueError('unexpected indirect call at %06X' % pc)
                table = 0x5E6
                nxt = (table + self.word(table + (s.vint_routine & 0x3E))) & 0xFFFFFF
                if nxt != 0x7EA:
                    r.open_terms['VInt_routine_%02X' % s.vint_routine] = 1
                    nxt = pc + ins.length
                else:
                    stack.append(pc + ins.length)
            elif ins.kind == 'ijump':                      # jmp SpecialVInt_Array(pc,d0.w)
                if pc != 0x4E80A:
                    raise ValueError('unexpected indirect jump at %06X' % pc)
                if s.special_vint_routine:
                    r.open_terms['SpecialVInt_%02X' % s.special_vint_routine] = 1
                    nxt = stack.pop()
                else:
                    nxt = 0x4E80E
            elif ins.kind == 'ret':
                nxt = stack.pop()
            elif ins.kind == 'rte':
                nxt = None
            r.cycles += self.cost(ins, outcome)
            if pc == 0x1C10:
                r.bookmarked = True
            if pc == 0x608:
                r.lag = True
            if pc == 0x946:
                r.deferred_updates = True
            pc = nxt
        raise RuntimeError('walk did not terminate')

    def decide(self, pc, s, dma_seen):
        if pc == 0x58E:
            return s.vint_routine == 0
        if pc in (0x612, 0x61A, 0x622, 0x62A):
            return s.game_mode == GAME_MODE_LEVEL[(pc - 0x612) // 8]
        if pc == 0x632:
            return not s.water_flag                           # beq.w VInt_0_NoWater
        if pc in (0x642, 0x6D0):
            return not s.pal                                  # btst #6,(Graphics_flags).w; beq.s: NTSC skips delay
        if pc in (0x662, 0x6F8):
            return False                                      # stopZ80 BUSACK granted on first test (invariant)
        if pc == 0x668:
            return s.water_full_screen                        # bne.s VInt_0_FullyUnderwater
        if pc == 0x6FE:
            return not s.competition_mode                     # beq.s VInt_0_Done
        if pc == 0x716:
            return s.sprite_table_page == 0                   # beq.s: upload Sprite_table_alternate
        if pc == 0x59C:
            return False                       # VBLANK flag already set (hardware invariant)
        if pc == 0x5B6:
            return not s.pal
        if pc == 0x7FA:
            return False                       # BUSACK granted on first test (hardware invariant)
        if pc == 0x804:
            return s.hyper_flash_timer == 0
        if pc == 0x83C:
            return s.water_full_screen
        if pc == 0x8B4:
            return not s.competition_mode
        if pc in (0x8BA, 0x8CA):
            raise ValueError('competition sprite-table page flip is an open term')
        if pc == 0x1594:
            return dma_seen >= s.dma_queue_entries            # entry word zero ends the queue
        if pc == 0x15A8:
            return dma_seen < DMA_QUEUE_SLOTS                 # slot end reached only when the queue is full
        if pc == 0x93C:
            return not s.water_flag
        if pc == 0x944:
            return s.h_int_counter >= 92
        if pc == 0x96E:
            return s.demo_timer == 0
        if pc == 0x1BF4:
            return not s.kos_busy
        if pc == 0x1C00:
            return s.kos_interrupted_pc < MAIN
        if pc == 0x1C08:
            return s.kos_interrupted_pc >= DONE
        raise ValueError('no semantic input decides the branch at %06X' % pc)
