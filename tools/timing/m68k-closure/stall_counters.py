#!/usr/bin/env python3
"""Convert native 68000 stall-counter stamps into per-bucket closure counters (schema openggf.m68k-stall-counters.v1).

Input: a CSV written by a native probe that reads the observation-only GPGX export `gpgx_m68k_stall_read` (stall ABI v1)
inside execute callbacks at three S3K points, columns
  event (vint|kos|increment), level_frame, frame_ordinal, frame_base_mclk, m68k_cycles,
  z80_bus_mclk, fifo_wait_mclk, refresh_events, dma_freeze_mclk
where `increment` is read at LevelLoop $6520 (addq.w #1,(Level_frame_counter).w, before it executes; level_frame is the
bucket that ends there), `vint` at the VInt handler's first ROM instruction $584 and `kos` at Process_Kos_Queue $1C1A.
Counter fields are cumulative since enable. Output: one JSON line per complete bucket N with the counter deltas between
the increment stamps of N-1 and N, `vint_to_increment_mclk` (VInt N handler entry to increment N) and
`kos_to_vint_mclk` (Process_Kos_Queue N entry to VInt N, the Kos service window), all in MCLK.
Fails closed (exit 2) on a missing or duplicated stamp, more than one VInt between increments, a non-monotonic clock or
counter, or a frame-ordinal regression; there are no default values.
Origin: OpenGGF S3K ending Kos CPU-budget research, 2026-10-16. The probe itself is a separate native task.
"""
import csv
import json
import sys

FIELDS = ('z80_bus_mclk', 'fifo_wait_mclk', 'refresh_events', 'dma_freeze_mclk')


class StampError(Exception):
    pass


def convert(rows, case):
    stamps = []
    last_clock = None
    last = None
    for r in rows:
        clock = int(r['frame_base_mclk']) + int(r['m68k_cycles'])
        if last_clock is not None and clock < last_clock:
            raise StampError('clock regresses at %s LF %s' % (r['event'], r['level_frame']))
        if last is not None:
            if int(r['frame_ordinal']) < int(last['frame_ordinal']):
                raise StampError('frame ordinal regresses at LF %s' % r['level_frame'])
            for f in FIELDS:
                if int(r[f]) < int(last[f]):
                    raise StampError('%s regresses at LF %s' % (f, r['level_frame']))
        if r['event'] not in ('vint', 'kos', 'increment'):
            raise StampError('unknown event %r' % r['event'])
        stamps.append((r['event'], int(r['level_frame']), clock, {f: int(r[f]) for f in FIELDS}))
        last_clock, last = clock, r
    incs = [k for k, s in enumerate(stamps) if s[0] == 'increment']
    out = []
    for a, b in zip(incs, incs[1:]):
        prev, cur = stamps[a], stamps[b]
        between = stamps[a + 1:b]
        vints = [s for s in between if s[0] == 'vint']
        koses = [s for s in between if s[0] == 'kos']
        if cur[1] != prev[1] + 1:
            raise StampError('increment stamps LF %d then %d' % (prev[1], cur[1]))
        if len(vints) != 1 or len(koses) != 1:
            raise StampError('LF %d has %d vint and %d kos stamps' % (cur[1], len(vints), len(koses)))
        if not koses[0][2] <= vints[0][2] <= cur[2]:
            raise StampError('LF %d stamps out of order' % cur[1])
        row = {'case': case, 'level_frame': cur[1]}
        row.update({f: cur[3][f] - prev[3][f] for f in FIELDS})
        row['vint_to_increment_mclk'] = cur[2] - vints[0][2]
        row['kos_to_vint_mclk'] = vints[0][2] - koses[0][2]
        out.append(row)
    return out


def main(argv):
    if len(argv) != 3:
        print('usage: stall_counters.py CASE_NAME STAMPS.csv > counters.jsonl', file=sys.stderr)
        return 3
    try:
        with open(argv[2]) as f:
            rows = list(csv.DictReader(f))
        for row in convert(rows, argv[1]):
            print(json.dumps(row, sort_keys=True))
    except (StampError, KeyError, ValueError) as e:
        print('STATUS: INCOMPLETE %s' % e, file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
