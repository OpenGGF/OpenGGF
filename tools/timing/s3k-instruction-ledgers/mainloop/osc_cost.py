#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes (Osc_Data2), oscillation state an engine owner holds, Player_1 routine, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K OscillateNumDo instruction-cycle ledger and state step (kos46 research R14, 2026-10-17; skdisasm 044fa46).

Walks the shipped OscillateNumDo ($773C-$7790) from verified ROM bytes, deciding each branch from the oscillation state
an engine owner holds (OscillationManager: control word, 16 values, 16 deltas) and the ROM Osc_Data2 table ($7792:
speed, limit per oscillator), and returns both the instruction cycles (pinned GPGX 051d430d or Motorola) and the next
state, using the ROM's own arithmetic: word add/sub of speed into delta, word add of delta into value, then an
unsigned byte compare of the limit with the value's high byte (cmp.b (a1),d4): moving up, bhi keeps direction else
bset; moving down, bls keeps direction else bclr. Oscillator j (0..15) uses control bit 15 - j. Gates: Competition_mode
runs it always; otherwise Player_1 routine >= 6 returns at once. bset/bclr Dn,Dn use bit numbers 0..15 here, so GPGX
charges the table base (the +2 applies only to bits >= 16).
"""
import os
import sys
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
import m68k_timing  # noqa: E402

ENTRY, RET, OSC_DATA = 0x773C, 0x7790, 0x7792
PINS = {0x773C: 0x4A78, 0x7740: 0x6608, 0x7742: 0x0C38, 0x7748: 0x6446, 0x774E: 0x45F9, 0x7756: 0x720F,
        0x775C: 0x0303, 0x775E: 0x6614, 0x7764: 0xD042, 0x776A: 0xD151, 0x776C: 0xB811, 0x776E: 0x6216,
        0x7770: 0x03C3, 0x7772: 0x6012, 0x7778: 0x9042, 0x7782: 0x6302, 0x7784: 0x0383, 0x7788: 0x51C9,
        0x778C: 0x31C3, 0x7790: 0x4E75}


@dataclass
class Osc:
    control: int = 0
    values: tuple = (0,) * 16
    deltas: tuple = (0,) * 16
    competition_mode: bool = False
    player1_routine: int = 2


@dataclass
class OscResult:
    cycles: int = 0
    counts: dict = field(default_factory=dict)
    after: Osc = None
    ran: bool = False


class OscLedger:
    def __init__(self, rom, cost):
        """cost(ins, outcome) as in test_vint8_cost; bset/bclr Dn,Dn are charged their bit < 16 time."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost = rom, cost
        self.dec = m68k_timing.Decoder(rom)
        if int.from_bytes(rom[0x7750:0x7754], 'big') != OSC_DATA:
            raise ValueError('lea (Osc_Data2).l no longer points at $7792')
        self.speed = [(rom[OSC_DATA + 4 * j] << 8) | rom[OSC_DATA + 4 * j + 1] for j in range(16)]
        self.limit = [rom[OSC_DATA + 4 * j + 3] for j in range(16)]     # cmp.b uses the low byte of the word

    def run(self, s):
        if len(s.values) != 16 or len(s.deltas) != 16:
            raise ValueError('16 oscillators')
        values, deltas, control = list(s.values), list(s.deltas), s.control & 0xFFFF
        r = OscResult()
        pc, j, d1 = ENTRY, 0, None
        for _ in range(1000):
            ins = self.dec.decode(pc)
            r.counts[pc] = r.counts.get(pc, 0) + 1
            nxt, outcome = pc + ins.length, 'seq'
            if ins.kind == 'cond':
                if pc == 0x7740:
                    take = s.competition_mode
                elif pc == 0x7748:
                    take = s.player1_routine >= 6
                elif pc == 0x775E:
                    take = bool(control >> d1 & 1)               # moving down
                elif pc == 0x776E:
                    take = self.limit[j] > (values[j] >> 8)      # bhi: limit above value high byte, keep rising
                elif pc == 0x7782:
                    take = self.limit[j] <= (values[j] >> 8)     # bls: keep falling
                else:
                    raise ValueError('no input decides %06X' % pc)
                outcome = 'taken' if take else 'fall'
                nxt = ins.target if take else nxt
            elif ins.kind == 'jump':
                nxt = ins.target
            elif ins.kind == 'dbcc':
                j += 1
                if d1:
                    d1 -= 1
                    outcome, nxt = 'taken', ins.target
                else:
                    outcome = 'fall'
            elif ins.kind == 'ret':
                r.cycles += self.cost(ins, 'seq')
                r.after = Osc(control, tuple(values), tuple(deltas), s.competition_mode, s.player1_routine)
                return r
            # state effects of the instruction just decided/executed
            if pc == 0x7756:
                d1, r.ran = 15, True
            elif pc == 0x7766:                                   # up: delta += speed, value += delta
                deltas[j] = (deltas[j] + self.speed[j]) & 0xFFFF
                values[j] = (values[j] + deltas[j]) & 0xFFFF
            elif pc == 0x777A:                                   # down: delta -= speed, value += delta
                deltas[j] = (deltas[j] - self.speed[j]) & 0xFFFF
                values[j] = (values[j] + deltas[j]) & 0xFFFF
            elif pc == 0x7770:
                control |= 1 << d1
            elif pc == 0x7784:
                control &= ~(1 << d1) & 0xFFFF
            r.cycles += self.cost(ins, outcome)
            pc = nxt
        raise RuntimeError('OscillateNumDo did not return')


def gpgx_cost(rom, gpgx_table):
    """Pinned GPGX costs; bset/bclr Dn,Dn charged the table base because every bit number here is < 16
    (m68kops.h bset_32_r_d / bclr_32_r_d add 2 only when the mask is >= $10000)."""
    sys.path.insert(0, os.path.join(HERE, '..', '..', 'm68k-closure'))
    import gpgx_cycles
    word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731

    def cost(ins, outcome):
        k = gpgx_cycles.cost(gpgx_table, ins, word(ins.pc), word)
        if isinstance(k, dict):
            lo, hi = k['taken'] if outcome == 'taken' else k['fall']
            return hi if ins.name == 'dbf' and outcome == 'fall' else lo
        if ins.pc in (0x7770, 0x7784):
            return k[0]
        if k[0] != k[1]:
            raise ValueError('operand-dependent cost at %06X' % ins.pc)
        return k[0]
    return cost


def motorola_cost(ins, outcome):
    if ins.kind in ('cond', 'dbcc'):
        c = ins.taken if outcome == 'taken' else ins.fall
        return c.hi if ins.kind == 'dbcc' and outcome == 'fall' else c.lo
    if ins.pc in (0x7770, 0x7784):
        return ins.cost.lo                                  # BSET/BCLR Dn,Dn bit < 16
    if ins.cost.lo != ins.cost.hi:
        raise ValueError('operand-dependent cost at %06X' % ins.pc)
    return ins.cost.lo
