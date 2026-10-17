#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R7 (context inventory, 2026-10-16).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs (argv): ROM, sonic3k.lst listing, s3k-fast-v1.json and s3k-v1.json load-time manifests; JSON map to stdout.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Resolve S3K load-time manifest fingerprints to ROM descriptors (kos46 context inventory, research, 2026-10-16).
Candidates: every sonic3k.lst label containing 'Kos' (direct Queue_Kos streams) and every KosM module child
(first at label+2; next at end + ((prev - end) & $F), Process_Kos_Module_Queue, sonic3k.asm 2794-2799), each decoded with the
Kosinski format of Process_Kos_Queue to get compressed/decompressed lengths; destinations: every `lea (abs),a2`
operand in the listing, Kos_decomp_buffer, and every 16-byte-aligned RAM address $FFFF0000-$FFFFFFF0. Fingerprint = S3kLoadTimeProfileGenerator.computeFingerprint
(KOS_DECOMPRESSION_QUEUE, source, compressed length, destination, decompressed length, "kosinski", 1)."""
import hashlib, json, re, struct, sys
ROM, LST, FAST, V1 = sys.argv[1:5]
rom = open(ROM, 'rb').read()
assert hashlib.sha1(rom).hexdigest() == 'cfbf98c36c776677290a872547ac47c53d2761d6'

def kos(src):
    """Return (compressed_length, decompressed_length) or None."""
    p, out = src, 0
    def word():
        nonlocal p
        v = rom[p] | rom[p + 1] << 8; p += 2; return v
    desc, bits = word(), 16
    def bit():
        nonlocal desc, bits
        b = desc & 1; desc >>= 1; bits -= 1
        if bits == 0:
            desc, bits = word(), 16
        return b
    for _ in range(200000):
        if p >= len(rom) - 4: return None
        if bit():
            p += 1; out += 1; continue
        if bit():
            lo, hi = rom[p], rom[p + 1]; p += 2
            cnt = hi & 7
            if cnt == 0:
                n = rom[p]; p += 1
                if n == 0: return (p - src, out)
                if n == 1: continue
                out += n + 1
            else:
                out += cnt + 2
        else:
            c = bit() << 1 | bit()
            p += 1; out += c + 2
        if out > 0x20000: return None
    return None

def fp(src, clen, dst, dlen):
    def t(s): b = s.encode(); return struct.pack('>I', len(b)) + b
    data = t('KOS_DECOMPRESSION_QUEUE') + struct.pack('>IIII', src, clen, dst & 0xFFFFFFFF, dlen) + t('kosinski') + struct.pack('>I', 1)
    return 'sha256:' + hashlib.sha256(data).hexdigest()

lab = re.compile(r'^(?:\(\d+\))?\s*\d+/\s*([0-9A-F]+) :\s+([A-Za-z_][A-Za-z0-9_]*):')
ins = re.compile(r'^\s*\d+/\s*([0-9A-F]+) : ([0-9A-F ]+?)\s{2,}\t*(lea\s+\(.*?\)\.[wl],a2)')
labels, dests = {}, {0xFFFFD000} | {0xFFFF0000 + k for k in range(0, 0x10000, 0x10)}
for line in open(LST, errors='replace'):
    m = lab.match(line)
    if m and 'Kos' in m.group(2) and int(m.group(1), 16) < 0x400000:
        labels[int(m.group(1), 16)] = m.group(2)
    m = ins.match(line.rstrip('\n'))
    if m:
        b = m.group(2).replace(' ', '')
        if b.startswith('45F8') and len(b) == 8:       # lea (abs).w,a2
            v = int(b[4:8], 16); dests.add(0xFFFF0000 | v if v & 0x8000 else v)
        elif b.startswith('45F9') and len(b) == 12:    # lea (abs).l,a2
            dests.add(int(b[4:12], 16) | (0xFF000000 if int(b[4:12], 16) & 0xFF0000 == 0xFF0000 else 0))
cands = {}
for addr, name in labels.items():
    if name.endswith('KosM') or 'KosM_' in name or name.startswith('ArtKosM'):
        size = rom[addr] << 8 | rom[addr + 1]
        n = ((size >> 12) & 0x1F) + (1 if size & 0xFFF else 0)
        src = addr + 2
        for k in range(max(n, 1)):
            r = kos(src)
            if not r: break
            cands[src] = (name + '#module%d' % k, r)
            end = src + r[0]
            src = end + ((src - end) & 0xF)
    else:
        r = kos(addr)
        if r: cands[addr] = (name, r)
table = {}
for src, (name, (clen, dlen)) in cands.items():
    for dst in dests:
        table[fp(src, clen, dst, dlen)] = {'source': '%06X' % src, 'label': name, 'compressed_length': clen,
                                           'destination': '%08X' % (dst & 0xFFFFFFFF), 'decompressed_length': dlen}
out = {}
for path in (FAST, V1):
    m = json.load(open(path))
    for e in m['entries']:
        out.setdefault(e['submissionFingerprint'], table.get(e['submissionFingerprint']))
print(json.dumps({'candidates': len(cands), 'destinations': ['%08X' % (d & 0xFFFFFFFF) for d in sorted(dests)],
                  'resolved': sum(1 for v in out.values() if v), 'total': len(out), 'map': out}, indent=1))
