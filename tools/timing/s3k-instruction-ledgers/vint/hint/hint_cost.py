#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R13 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, `HState` (water/H-int RAM an engine owner holds), a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K water H-interrupt handler instruction-cycle ledger, including the deferred Do_Updates (kos46 research, R13).

Walks the shipped HInt3 ($DD8), HInt5 ($E6C), HInt4 ($F00), HInt_6 ($F92) and HInt2 ($1024) from verified ROM bytes and
charges each executed instruction its pinned GPGX 051d430d (or Motorola) time. Branches are decided only by semantic
state (`HState`). When VInt_8 deferred Do_Updates (Water_flag and H_int_counter < 92, $946), the handler tail
($xx_Do_Updates: clr.b, movem.l d0-a6, jsr/bsr Do_Updates $95A, movem.l, rte) runs Do_Updates, whose UpdateHUD and
Process_Nem_Queue_2 calls are returned as open terms for vint_frame-style composition with hud/ and nem/.

Handler selection (engine-owned, not decided here): the water setup installs HInt2 for HCZ, otherwise HInt3 or, when
V_blank_cycles >= $1000, HInt4 (sonic3k.asm 7678-7692 / 9824-9830); the full-screen variants HInt5 / HInt_6 come from
Handle_Onscreen_Water_Height (8559-8592; 044fa46 names HInt_6 `HInt6`). HInt5/HInt_6 are opcode-for-opcode copies of HInt3/HInt4 except the palette
lea operand; the constructor checks that rather than assuming it.

Separate terms, never included: H-int acceptance, refresh, Z80 waits, VDP FIFO waits on the CRAM writes. The
stopZ80 BUSACK wait is the same single-test invariant as in VInt_8. FixBugs plays no part in these handlers.
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))
import vint8_cost as vc  # noqa: E402
sys.path.insert(0, vc.TOOL)
import m68k_timing  # noqa: E402

DO_UPDATES = (0x95A, 0x978)
OPEN_CALLEES = {0xDD06: 'UpdateHUD', 0x17F4: 'Process_Nem_Queue_2'}
# offsets from the handler entry
LAYOUT = {
    'A': dict(size=0x94, flag=0x04, busack=0x2C, delay=0x34, lo=0x42, hi=0x46, color_delay=0x64, colors=0x68,
              deferred=0x7C, call=0x88),
    'B': dict(size=0x92, flag=0x04, busack=0x2C, delay=0x34, lo=0x42, hi=0x46, color_delay=0x62, colors=0x66,
              deferred=0x7A, call=0x86),
    'C': dict(size=0x8A, flag=0x08, deferred=0x74, call=0x80),
}
HANDLERS = {'HInt3': (0xDD8, 'A'), 'HInt5': (0xE6C, 'A'), 'HInt4': (0xF00, 'B'), 'HInt_6': (0xF92, 'B'),
            'HInt2': (0x1024, 'C')}
OPS = {'flag': 0x67, 'busack': 0x66F6, 'delay': 0x51C8, 'lo': 0x65, 'hi': 0x65, 'color_delay': 0x51C8,
       'colors': 0x51C9, 'deferred': 0x6602}


@dataclass
class HState:
    """Engine-owned state when the line interrupt is accepted."""
    handler: str = 'HInt3'          # H_int_addr target (water owner + V_blank_cycles, see module doc)
    h_int_flag: bool = True         # H_int_flag: set by the last VInt; cleared by the first H-int of the frame
    h_int_counter: int = 0x60       # H_int_counter byte (water line)
    water_palette_data: int = None  # Water_palette_data_addr: ROM pointer to the zone's water colour table
    do_updates_in_h_int: bool = False   # Do_Updates_in_H_int (set by VInt_8 $946)
    demo_timer: int = 0


@dataclass
class HResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    open_terms: dict = field(default_factory=dict)
    colors: int = 0                 # colour transfers performed
    after: dict = field(default_factory=dict)   # state transitions the ROM performs


class HIntLedger:
    def __init__(self, rom, cost):
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        w = self.word
        for name, (base, kind) in HANDLERS.items():
            lay = LAYOUT[kind]
            for key, op in OPS.items():
                if key not in lay:
                    continue
                got = w(base + lay[key])
                if (got >> 8 if op < 0x100 else got) != op:
                    raise ValueError('%s+%X is %04X, not the pinned %X' % (name, lay[key], got, op))
            if self.dec.decode(base + lay['call']).target != DO_UPDATES[0]:
                raise ValueError('%s does not call Do_Updates' % name)
        for twin, orig in (('HInt5', 'HInt3'), ('HInt_6', 'HInt4')):
            a, b = HANDLERS[twin][0], HANDLERS[orig][0]
            diff = [k for k in range(0, LAYOUT[HANDLERS[orig][1]]['size'], 2) if w(a + k) != w(b + k)]
            if diff != [0x4C]:          # only the lea (Water_palette|Normal_palette).w operand differs
                raise ValueError('%s is not an operand-only copy of %s: %s' % (twin, orig, diff))
        if w(0x96E) != 0x6700 or w(0x95A) != 0x4EB9 or w(0x966) != 0x6100:
            raise ValueError('Do_Updates opcodes changed')

    def word(self, a):
        return (self.rom[a] << 8) | self.rom[a + 1]

    def color_count(self, s):
        """$$transferColors iterations: d1 = first word of the colour table; when H_int_counter >= 200 the
        low byte is reduced by H_int_counter - 200 (sub.b), and a borrow skips the transfer."""
        d1 = self.word(s.water_palette_data)
        if s.h_int_counter < 200:
            return d1 + 1
        low = (d1 & 0xFF) - (s.h_int_counter - 200)
        return 0 if low < 0 else ((d1 & 0xFF00) | low) + 1

    @staticmethod
    def transitions(s):
        """RAM the handler writes. With H_int_flag clear the beq at entry+flag targets the final rte, so neither the
        body nor the deferred check runs (later line interrupts of the frame)."""
        if not s.h_int_flag:
            return {}
        out = {'h_int_flag': False}
        if s.do_updates_in_h_int:
            out.update(do_updates_in_h_int=False, lag_frame_count=0)      # clr.b; Do_Updates move.w #0
            if s.demo_timer:
                out['demo_timer'] = s.demo_timer - 1
        return out

    def run(self, s):
        if s.handler not in HANDLERS:
            raise ValueError('handler %s is not a water H-int with deferred updates' % s.handler)
        base, kind = HANDLERS[s.handler]
        lay = LAYOUT[kind]
        at = {base + v: k for k, v in lay.items() if k not in ('size', 'call')}
        ranges = [(base, base + lay['size']), DO_UPDATES]
        r = HResult()
        if kind != 'C' and s.h_int_flag:
            if s.water_palette_data is None:
                raise ValueError('Water_palette_data_addr is required when the handler body runs')
            r.colors = self.color_count(s)
            if r.colors > 0x100:
                raise ValueError('colour count %d is not a shipped table value' % r.colors)
        dbf = {}
        colors_left = r.colors
        pc, stack = base, [None]
        for _ in range(100000):
            if pc is None:
                r.after = self.transitions(s)
                return r
            if not any(a <= pc < b for a, b in ranges):
                raise ValueError('walk left the handler at %06X' % pc)
            ins = self.dec.decode(pc)
            r.counts[pc] = r.counts.get(pc, 0) + 1
            nxt, outcome = pc + ins.length, 'seq'
            role = at.get(pc)
            if ins.kind == 'cond':
                if role == 'flag':
                    take = not s.h_int_flag
                elif role == 'busack':
                    take = False
                elif role == 'lo':
                    take = s.h_int_counter < 200
                elif role == 'hi':
                    take = r.colors == 0
                elif role == 'deferred':
                    take = s.do_updates_in_h_int
                elif pc == 0x96E:
                    take = s.demo_timer == 0
                else:
                    raise ValueError('no semantic input decides the branch at %06X' % pc)
                outcome = 'taken' if take else 'fall'
                nxt = ins.target if take else nxt
            elif ins.kind == 'dbcc':
                if role in ('delay', 'color_delay'):
                    if pc not in dbf:
                        prev = self.word(pc - 2)
                        if prev >> 8 != 0x70:
                            raise ValueError('delay count at %06X is not a moveq' % pc)
                        dbf[pc] = prev & 0xFF
                    if dbf[pc]:
                        dbf[pc] -= 1
                        outcome, nxt = 'taken', ins.target
                    else:
                        del dbf[pc]
                        outcome = 'fall'
                elif role == 'colors':
                    colors_left -= 1
                    if colors_left:
                        outcome, nxt = 'taken', ins.target
                    else:
                        outcome = 'fall'
                else:
                    raise ValueError('unexpected dbcc at %06X' % pc)
            elif ins.kind == 'jump':
                nxt = ins.target
            elif ins.kind == 'call':
                if ins.target in OPEN_CALLEES:
                    name = OPEN_CALLEES[ins.target]
                    r.open_terms[name] = r.open_terms.get(name, 0) + 1
                else:
                    stack.append(nxt)
                    nxt = ins.target
            elif ins.kind == 'ret':
                nxt = stack.pop()
            elif ins.kind == 'rte':
                nxt = None
            elif ins.kind not in ('seq',):
                raise ValueError('unsupported control kind %s at %06X' % (ins.kind, pc))
            r.cycles += self.cost(ins, outcome)
            pc = nxt
        raise RuntimeError('handler did not return')
