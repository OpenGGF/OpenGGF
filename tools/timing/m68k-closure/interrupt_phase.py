#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), rounds R4-R5 (2026-10-16); unconditional static jump successor rule from the
#   native-stall-counters lane (2026-10-16), found on real v9 stamps at $1C5A bra.s.
# Promoted with the kos46 instruction-closure patches (v3 9b76415d..., successor 81f3097a...) on 2026-10-17 at base 00f8ab78a.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Derive each VInt's interrupt-acceptance cycles from observation-only native stamps.

Purpose: GPGX 051d430d accepts an interrupt with m68ki_cycle_interrupts[(cycles / 7) % 10] = 50..59 cycles
(m68kcpu.h:1405,1449), so the cost depends on the cycle at which the interrupted instruction ended. Histograms cannot
see that phase. A v9 probe stamps (a) every candidate interrupted instruction at its start and (b) the first
instruction after acceptance (the RAM `jmp (VInt).l` at $FFFFF0), each with the stall ABI v1 snapshot; this script
recomputes the acceptance cost exactly and adds `vint_interrupt_cycles` to the counter rows.
Input CSV columns: event (last|accept), level_frame, pc, frame_base_mclk, m68k_cycles, refresh_events, z80_bus_mclk,
fifo_wait_mclk, dma_freeze_mclk, return_pc (accept rows: stacked PC), operand (last rows: empty or a register value).
Checks, failing closed (exit 2): the stacked return PC must be a decoded successor of the last stamped instruction; the
last instruction's cost must be exact (branch outcome from the return PC, no unresolved operand); the derived cost must
equal the GPGX table entry for the derived acceptance cycle. No timing is assumed or fitted.
Usage: interrupt_phase.py ROM GPGX_REPO STAMPS.csv COUNTERS.jsonl CASE FIRST_LF LAST_LF > COUNTERS_WITH_INTERRUPTS.jsonl
Origin: OpenGGF S3K ending Kos CPU-budget research, 2026-10-16. The probe is a separate native task.
"""
import csv
import json
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m68k_timing  # noqa: E402
import gpgx_cycles  # noqa: E402


class PhaseError(Exception):
    pass


def acceptance_cycles(last, accept, ins, cost, table):
    """Return the acceptance cost in 68000 cycles for one VInt."""
    ret = int(accept['return_pc'], 16)
    if ins.kind == 'cond' or ins.kind == 'dbcc':
        if ret == ins.target:
            own = cost['taken'][0]
        elif ret == ins.pc + ins.length:
            if cost['fall'][0] != cost['fall'][1]:
                raise PhaseError('fall-through cost of %06X not exact' % ins.pc)
            own = cost['fall'][0]
        else:
            raise PhaseError('return pc %06X is not a successor of %06X' % (ret, ins.pc))
    elif ins.kind == 'jump' and ins.target is not None:
        # unconditional static transfer (bra, jmp to an absolute/PC-relative target): its only successor is the target
        if ret != ins.target:
            raise PhaseError('return pc %06X is not the target %06X of %06X' % (ret, ins.target, ins.pc))
        if cost[0] != cost[1]:
            raise PhaseError('operand-dependent cost at %06X' % ins.pc)
        own = cost[0]
    else:
        if ins.kind != 'seq' or ret != ins.pc + ins.length:
            raise PhaseError('return pc %06X is not the successor of %06X' % (ret, ins.pc))
        if cost[0] != cost[1]:
            raise PhaseError('operand-dependent cost at %06X' % ins.pc)
        own = cost[0]
    t_last = int(last['frame_base_mclk']) + int(last['m68k_cycles'])
    t_acc = int(accept['frame_base_mclk']) + int(accept['m68k_cycles'])
    stalls = sum(int(accept[f]) - int(last[f]) for f in ('z80_bus_mclk', 'fifo_wait_mclk', 'dma_freeze_mclk'))
    stalls += 14 * (int(accept['refresh_events']) - int(last['refresh_events']))
    t0 = t_last + 7 * own + stalls            # cycle at which the interrupted instruction ended
    span = t_acc - t0
    if span % 7:
        raise PhaseError('acceptance span %d MCLK is not whole cycles' % span)
    c = span // 7
    # GPGX indexes the table with the frame-relative counter at acceptance time
    rel = int(accept['m68k_cycles']) - 7 * c
    if rel < 0 or table[(rel // 7) % 10] != c:
        raise PhaseError('derived %d cycles disagrees with m68ki_cycle_interrupts at %d MCLK' % (c, rel))
    return c


COUNTER_STAMP_FIELDS = ('refresh_events', 'z80_bus_mclk', 'fifo_wait_mclk', 'dma_freeze_mclk')


def run(rom, table, rows, counters, case, model_cost, frames, accept_pc=0xFFFFF0):
    """Derive one acceptance cost per level frame in `frames` (an explicit, non-empty set) for `case`.
    Fails closed on: no stamps; a stamp sequence that is not strictly `last` then `accept` pairs; an accept at a PC
    other than `accept_pc`; a pair whose level frames differ; a regressing clock or counter; two acceptances in one
    frame; any requested frame without a derived acceptance or without a counter row; an absent case; a derived
    frame outside the request; a pre-populated `vint_interrupt_cycles` that the new derivation does not reproduce."""
    frames = set(frames)
    if not frames:
        raise PhaseError('no frames requested')
    if not rows:
        raise PhaseError('no interrupt-phase stamps')
    dec = m68k_timing.Decoder(rom)
    per_lf = {}
    prev = None
    for k in range(0, len(rows), 2):
        pair = rows[k:k + 2]
        if len(pair) != 2 or pair[0]['event'] != 'last' or pair[1]['event'] != 'accept':
            raise PhaseError('stamp %d is not a last/accept pair' % k)
        last, accept = pair
        if int(accept['pc'], 16) != accept_pc:
            raise PhaseError('accept stamp at %s, expected %06X' % (accept['pc'], accept_pc))
        if last['level_frame'] != accept['level_frame']:
            raise PhaseError('pair spans LF %s and %s' % (last['level_frame'], accept['level_frame']))
        for r in pair:
            clock = int(r['frame_base_mclk']) + int(r['m68k_cycles'])
            if prev is not None and (clock < prev[0] or any(int(r[f]) < prev[1][f] for f in COUNTER_STAMP_FIELDS)):
                raise PhaseError('clock or counter regresses at LF %s' % r['level_frame'])
            prev = (clock, {f: int(r[f]) for f in COUNTER_STAMP_FIELDS})
        lf = int(accept['level_frame'])
        if lf in per_lf:
            raise PhaseError('two VInt acceptances in LF %d' % lf)
        ins = dec.decode(int(last['pc'], 16))
        per_lf[lf] = acceptance_cycles(last, accept, ins, model_cost(ins), table)
    if set(per_lf) != frames:
        raise PhaseError('derived frames %s differ from requested %s' % (sorted(per_lf), sorted(frames)))
    out, covered = [], set()
    for c in counters:
        if c['case'] == case:
            lf = int(c['level_frame'])
            if lf in frames:
                if lf in covered:
                    raise PhaseError('duplicate counter row LF %d' % lf)
                old = c.get('vint_interrupt_cycles')
                if old is not None and old != per_lf[lf]:
                    raise PhaseError('pre-populated phase %s at LF %d differs from derived %d' % (old, lf, per_lf[lf]))
                c = dict(c, vint_interrupt_cycles=per_lf[lf])
                covered.add(lf)
            elif c.get('vint_interrupt_cycles') is not None:
                raise PhaseError('LF %d carries an interrupt phase outside the requested frames' % lf)
        out.append(c)
    if covered != frames:
        raise PhaseError('counter rows for case %r missing LF %s' % (case, sorted(frames - covered)))
    return out


def main(argv):
    if len(argv) != 8:
        print(__doc__.split('Usage: ')[1].split('\n')[0], file=sys.stderr)
        return 3
    rom_path, repo, stamps, counters_path, case, first, last_lf = argv[1:]
    try:
        with open(rom_path, 'rb') as f:
            rom = f.read()
        table = gpgx_cycles.load_table(repo)
        irq = gpgx_cycles.load_interrupt_cycles(repo)
        word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        with open(stamps) as f:
            rows = list(csv.DictReader(f))
        with open(counters_path) as f:
            counters = [json.loads(line) for line in f]
        frames = range(int(first), int(last_lf) + 1)
        for row in run(rom, irq, rows, counters, case, lambda i: gpgx_cycles.cost(table, i, word(i.pc), word), frames):
            print(json.dumps(row, sort_keys=True))
    except (PhaseError, gpgx_cycles.IdentityError, m68k_timing.Unsupported, KeyError, ValueError, IndexError) as e:
        print('STATUS: INCOMPLETE %s' % e, file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
