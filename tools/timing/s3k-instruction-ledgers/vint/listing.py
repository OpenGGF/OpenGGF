#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R9 helper (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs (argv): ROM, sonic3k.lst listing, frame-pc-histogram.csv, level frame, hex ranges START-END.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Linear decode of ROM ranges with lst text and native per-PC counts (research helper)."""
import sys, re, csv, collections, os
TOOL = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'm68k-closure')
sys.path.insert(0, TOOL)
import m68k_timing
ROM, LST, HIST, LF = sys.argv[1:5]
ranges = [tuple(int(x, 16) for x in r.split('-')) for r in sys.argv[5:]]
rom = open(ROM, 'rb').read()
dec = m68k_timing.Decoder(rom)
txt = {}
pat = re.compile(r'^\s*\d+/\s*([0-9A-F]+) : ([0-9A-F ]+?)\s{2,}\t*(\S.*)$')
for line in open(LST, errors='replace'):
    m = pat.match(line.rstrip('\n'))
    if m and m.group(2).strip() and '(MACRO' not in m.group(2):
        txt.setdefault(int(m.group(1), 16), re.sub(r'\s+', ' ', m.group(3).split(';')[0]))
cnt = collections.Counter()
for r in csv.DictReader(open(HIST)):
    if r['level_frame'] == LF:
        cnt[int(r['pc'], 16)] += int(r['count'])
for a, b in ranges:
    pc = a
    while pc < b:
        try:
            i = dec.decode(pc)
            print('%06X %-6s x%-4d %-10s %s' % (pc, '%04X' % ((rom[pc] << 8) | rom[pc + 1]), cnt.get(pc, 0), i.name + ('.' + i.size if i.size else ''), txt.get(pc, '')))
            pc += i.length
        except m68k_timing.Unsupported as e:
            print('%06X UNSUPPORTED %s' % (pc, e)); pc += 2
    print('----')
