#!/usr/bin/env python3
"""Date object code-pointer transitions in a V5 aux trace, relative to an anchor pointer.

Purpose: a boss or set piece that runs as player-independent clockwork can be compared
between fixtures (and against an engine probe) by when its slots change routine. The
recorder's ``object_appeared`` rows carry the slot's new code pointer whenever it changes,
and ``object_removed`` rows mark slot release; this script prints those transitions for
the named pointers, with each frame relative to the first appearance of the anchor pointer.
Comparison-only evidence: it never feeds the engine.

Inputs: an ``aux_state.jsonl.gz`` path, the anchor pointer, and NAME=POINTER pairs (hex).
Pointers outside the map are ignored except as the previous owner of a named transition.
Origin: round-2 solo Sonic lane, 2026-10-16 (S3K Obj_LRZEndBoss: native Tails-alone and
Sonic+Tails fixtures both take the 14 mine hits at identical offsets from loc_797FA).

Example (LRZ3 end boss; slot removals of the floating-mine pointer are the mine hits):
  python3 -I tools/traces/aux_code_pointer_timeline.py \
      src/test/resources/traces/s3k/runs/s3k-tails-full-chain-all-emeralds/hpz22/aux_state.jsonl.gz \
      0x797FA boss.active=0x797FA boss.defeat=0x79988 stream=0x79A70 mine.float=0x79B54
"""
import argparse
import gzip
import json


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('aux', help='aux_state.jsonl.gz of a V5 trace')
    parser.add_argument('anchor', help='code pointer whose first appearance is frame 0 (hex)')
    parser.add_argument('names', nargs='+', help='NAME=POINTER pairs (hex)')
    args = parser.parse_args()
    anchor = int(args.anchor, 16)
    names = {}
    for pair in args.names:
        name, pointer = pair.split('=', 1)
        names[int(pointer, 16)] = name
    slot_code = {}
    rows = []
    zero = None
    with gzip.open(args.aux, 'rt') as stream:
        for line in stream:
            row = json.loads(line)
            event = row.get('event')
            if event == 'object_appeared':
                code = int(row['object_type'], 16)
                previous = slot_code.get(row['slot'])
                slot_code[row['slot']] = code
                if code == anchor and zero is None:
                    zero = row['frame']
                if code in names:
                    rows.append((row['frame'], names[code], row['slot'], row.get('x'), row.get('y'),
                                 names.get(previous, hex(previous) if previous else '-')))
            elif event == 'object_removed':
                previous = slot_code.pop(row['slot'], None)
                if previous in names:
                    rows.append((row['frame'], names[previous] + '.removed', row['slot'], None, None, '-'))
    if zero is None:
        raise SystemExit('anchor pointer never appears')
    print('frame relative event slot x y previous')
    for frame, name, slot, x, y, previous in rows:
        print(frame, frame - zero, name, slot, x or '-', y or '-', previous)


if __name__ == '__main__':
    main()
