#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs (argv): sonic3k.lst, capture directories, optional --closed closed-ranges.txt, --s3-lst and --rom.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Attribute native per-PC histogram executions to ROM routines, for prioritisation only (kos46 research R14).

Purpose: rank which routines dominate the retained ending buckets so the next source closures target the critical
path. The histogram is a comparison/prioritisation oracle; nothing here feeds a runtime value or a cost.
Inputs: ROM, a sonic3k.lst listing, one or more capture directories (frame-pc-histogram.csv), optional
--closed FILE of 'start-end name' ranges already priced elsewhere. Each PC is assigned to the nearest preceding
global (non loc_/locret_/word_/byte_/off_ and non-local; sub_ kept) label in the listing; instruction cycles are not
estimated (branch outcomes are not in the histogram), only executed-instruction counts.
Caveat: listing labels come from the checkout that produced the .lst (1a454a0 here); ROM addresses are identical
for the pinned 044fa46 (same ROM), but some label names differ between the two disassembly revisions.
Origin: kos46 research task, 2026-10-17. Usage:
  attribute_pcs.py LST CAPTURE_DIR [CAPTURE_DIR...] [--closed RANGES] [--top N] [--s3-lst S3LST --rom ROM]
PCs at $200000-$3FFFFF belong to the locked-on S3 half; with --s3-lst they are attributed to s3.lst labels at base
$200000 ('S3:' prefix), after checking that each used label's first listed opcode word equals the ROM bytes.
"""
import bisect
import collections
import csv
import os
import re
import sys

LABEL = re.compile(r'^\s+\d+/\s+([0-9A-F]+) :\s+(?:\(MACRO\)\s+)?([A-Za-z_][A-Za-z0-9_]*):')
LOCAL = re.compile(r'^(loc|locret|word|byte|off|unk|dword|ObjDat|Ani|Map|DPLC|PLC_)_?')   # sub_ kept: subroutine bodies


OPLINE = re.compile(r'^\s+\d+/\s+([0-9A-F]+) : ([0-9A-F]{4})')


def labels(lst, base=0, prefix='', limit=None):
    out, first_op, pending = {}, {}, None
    with open(lst, encoding='latin-1') as f:
        for line in f:
            m = LABEL.match(line)
            if m:
                addr, name = int(m.group(1), 16) + base, m.group(2)
                if not LOCAL.match(name) and addr not in out:
                    out[addr] = prefix + name
                    pending = addr
                continue
            if pending is not None:
                o = OPLINE.match(line)
                if o and int(o.group(1), 16) + base == pending:
                    first_op[pending] = int(o.group(2), 16)
                    pending = None
    if limit is not None:
        out = {k: v for k, v in out.items() if k < limit}
    keys = sorted(out)
    return keys, [out[k] for k in keys], first_op


def owner(keys, names, pc):
    i = bisect.bisect_right(keys, pc) - 1
    return names[i] if i >= 0 else '?'


def load(capture):
    per = collections.defaultdict(collections.Counter)
    with open(os.path.join(capture, 'frame-pc-histogram.csv')) as f:
        for r in csv.DictReader(f):
            per[int(r['level_frame'])][int(r['pc'], 16)] += int(r['count'])
    return per


def closed_ranges(path):
    out = []
    if path:
        for line in open(path):
            if line.strip() and not line.startswith('#'):
                rng, name = line.split(None, 1)
                a, b = (int(x, 16) for x in rng.split('-'))
                out.append((a, b, name.strip()))
    return out


def main(argv):
    top, closed_file, caps, s3lst, rom_path = 40, None, [], None, None
    it = iter(argv[1:])
    lst = next(it)
    for a in it:
        if a == '--top':
            top = int(next(it))
        elif a == '--closed':
            closed_file = next(it)
        elif a == '--s3-lst':
            s3lst = next(it)
        elif a == '--rom':
            rom_path = next(it)
        else:
            caps.append(a)
    keys, names, _ = labels(lst, limit=0x200000)
    first_op = {}
    if s3lst:
        if not rom_path:
            raise SystemExit('--s3-lst needs --rom for the opcode check')
        k3, n3, first_op = labels(s3lst, base=0x200000, prefix='S3:')
        keys, names = keys + k3, names + n3
        rom = open(rom_path, 'rb').read()
    closed = closed_ranges(closed_file)
    total = collections.Counter()
    frames = 0
    for cap in caps:
        for lf, hist in load(cap).items():
            frames += 1
            for pc, k in hist.items():
                tag = next((n for a, b, n in closed if a <= pc < b), None)
                total[('CLOSED ' + tag) if tag else owner(keys, names, pc)] += k
    if s3lst:
        used = {n for n in total if n.startswith('S3:')}
        for addr, name in zip(keys, names):
            if name in used and addr in first_op and ((rom[addr] << 8) | rom[addr + 1]) != first_op[addr]:
                raise SystemExit('S3 label %s at %06X does not match the ROM' % (name, addr))
    allk = sum(total.values())
    print('%d buckets, %d executed instructions (%.0f per bucket)' % (frames, allk, allk / max(frames, 1)))
    for name, k in total.most_common(top):
        print('%8.1f  %5.1f%%  %s' % (k / frames, 100.0 * k / allk, name))


if __name__ == '__main__':
    main(sys.argv)
