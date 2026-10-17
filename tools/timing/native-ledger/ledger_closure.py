#!/usr/bin/env python3
# Origin: S3K Kos CPU-budget research, native stall ABI v2 first exact interval (native-stall-counters lane, Claude
#   Opus 5.5, 2026-10-17; original sha256 af521b1d...), corrected by Root (OpenAI Codex GPT-6.1) after review
#   findings N1/N4/N10 and the odd-cycle/label-coverage follow-up (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a; promotion changed only the default decoder
#   directory (../m68k-closure) and this header.
# Inputs: see the docstring and README.md.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Validate native stall ABI v2 evidence, interval conservation and ROM instruction-cost admissibility.

Purpose: for each complete interval, prove (1) every MCLK between the two acceptance instants is attributed:
  close - open == sum(slot MCLK) + carry + 14*refresh + sum(classified kinds) - dma_rewind      (exactly, no tolerance)
and (2) every executed PC's native instruction time is admissible under the ROM decoder's GPGX 051d430d costs:
  exact cost c:            mclk == 7*count*c
  conditional/DBcc:        consistent with SOME unobserved taken/fall mix (mix-admissible, not exact)
  operand-dependent [lo,hi]: 7*count*lo <= mclk <= 7*count*hi  (reported as bounded, not exact)
  RAM PCs:                 only cited constants (no ROM bytes); anything else is unsupported.
Checks metadata hashes and counts, bank/slot structure, joins and interrupt acceptances before selecting intervals.
The straddling instruction of an interval is completed with the next interval's carry before (2). Aggregate
conservation does not independently validate in-instruction stall classification or main/IRQ attribution. No
flow solution is inferred from a branch mix. Unsupported instruction costs and missing carries stay incomplete.
Exit 0 PASS (all selected intervals close and every PC is admissible), 1 FAIL, 2 INCOMPLETE, 3 usage/identity.
--perturb KIND:SEQ[:PC[:CONTEXT]] applies an in-memory negative control after evidence validation. The default
slot is fixed-cost, never an arbitrary branch. conserve_dma/conserve_z80_bus move 7 MCLK from a classified
stall into that instruction, leaving conservation at zero so the cost check must catch the error. Named branch
count controls may remain admissible: aggregate branch data cannot prove the unseen execution path.
Inputs: runner output directory, ROM, a Genesis-Plus-GX repository holding commit 051d430d (cycles table), the
closure tool directory providing m68k_timing.py/gpgx_cycles.py. Origin: OpenGGF S3K Kos CPU-budget research
(stall ABI v2 first exact interval), 2026-10-17.
"""
import argparse, collections, csv, hashlib, json, re, sys
from pathlib import Path

KINDS = ('z80_bus', 'fifo', 'dma', 'z80_area', 'lockup', 'stopped', 'accept_vint', 'accept_hint', 'accept_other')
CITED_RAM = {0xFFFFF0: (12, 'jmp (abs).l at V_int_jump; GPGX base 12 for 4EF9')}
S3K_SHA1 = 'cfbf98c36c776677290a872547ac47c53d2761d6'


class EvidenceError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise EvidenceError(message)


def integer(value, label, minimum=0):
    require(type(value) is int and value >= minimum, '%s must be an integer >= %d' % (label, minimum))
    return value


def jsonl(path):
    with path.open(encoding='utf-8') as f:
        return [json.loads(line) for line in f]


def load(run):
    run = Path(run)
    intervals = jsonl(run / 'intervals.jsonl')
    slots = collections.defaultdict(list)
    with (run / 'interval-slots.csv').open(newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            slots[int(r['sequence'])].append([int(r['pc']), int(r['context']), int(r['count']), int(r['mclk'])])
    return intervals, slots


def validate_evidence(run, rom, interrupt_costs):
    """Verify the whole completed output, including rows outside a requested label slice."""
    run = Path(run)
    meta = json.loads((run / 'metadata.json').read_text(encoding='utf-8'))
    require(isinstance(meta, dict), 'metadata must be an object')
    require(meta['schema'] == 'openggf.m68k-ledger-window.v1', 'unsupported evidence schema')
    require(meta['ledger'] is True, 'ledger was disabled')
    require(meta['ingress_kind'] == 'declared_setup', 'unsupported ingress kind (authenticity is not established)')
    require(meta['rom_sha1'] == hashlib.sha1(rom).hexdigest(), 'ROM SHA-1 differs from the recording')
    provenance = meta['ingress_provenance']
    require(isinstance(provenance, dict), 'ingress provenance must be an object')
    for outer, inner in [('ingress_kind', 'ingress_kind'), ('rom_sha1', 'rom_sha1'),
                         ('movie_sha256', 'movie_sha256'), ('core_sha256', 'core_sha256'),
                         ('ingress_state_sha256', 'state_sha256')]:
        require(meta[outer] == provenance[inner], 'provenance mismatch: ' + outer)
    require(provenance['region'] in ('ntsc', 'pal'), 'missing explicit ingress region')
    required = {'frames.jsonl', 'intervals.jsonl', 'interval-slots.csv', 'accepts.jsonl',
                'audio-window.s16', 'state-end.bin'}
    hashes = meta['files_sha256']
    require(isinstance(hashes, dict), 'file hashes must be an object')
    require(required <= hashes.keys(), 'required output missing from completion hashes')
    require(set(hashes) <= required | {'state-after-label.bin'}, 'unknown output in completion hashes')
    for name, expected in hashes.items():
        require(hashlib.sha256((run / name).read_bytes()).hexdigest() == expected, 'output hash mismatch: ' + name)
    intervals, slots = load(run)
    accepts = jsonl(run / 'accepts.jsonl')
    frames = jsonl(run / 'frames.jsonl')
    require(len(frames) == integer(meta['frames'], 'frames', 1), 'recorded frame count mismatch')
    require(len(accepts) == integer(meta['acceptances'], 'acceptances'), 'recorded acceptance count mismatch')
    require(intervals, 'no interval rows')
    require(sum(i['complete'] is True for i in intervals) == integer(meta['intervals_complete'], 'intervals_complete'),
            'complete interval count mismatch')
    require(set(slots) == set(range(len(intervals))), 'orphan or missing interval slots')
    by_frame = collections.Counter()
    for ordinal, a in enumerate(accepts):
        require(a['ordinal'] == ordinal, 'acceptance ordinal gap')
        require(1 <= integer(a['level'], 'interrupt level') <= 7, 'invalid interrupt level')
        require(a['inside_instruction'] in (0, 1), 'invalid acceptance instruction flag')
        idx = integer(a['table_index'], 'interrupt table index')
        require(idx < len(interrupt_costs) and a['cycles'] == interrupt_costs[idx], 'interrupt cost table mismatch')
        integer(a['stamp_mclk'], 'acceptance stamp')
        if ordinal:
            require(a['stamp_mclk'] > accepts[ordinal-1]['stamp_mclk'], 'acceptance time does not advance')
        frame = integer(a['frame'], 'acceptance frame')
        require(frame < len(frames), 'acceptance frame outside output')
        require(a['label'] == frames[frame]['label'], 'acceptance frame label mismatch')
        if a['level'] == 6:
            by_frame[frame] += 1
        else:
            require(a['closes_bank_sequence'] == -1, 'non-VInt acceptance claims a bank')
    for frame, row in enumerate(frames):
        require(row['frame'] == frame, 'frame ordinal gap')
        require(row['vint_accepts'] == by_frame[frame] <= 1, 'frame VInt count mismatch')
        require(row['context'] in (0, 1), 'invalid frame context')
    for seq, iv in enumerate(intervals):
        require(iv['sequence'] == seq, 'interval sequence gap')
        require(iv['complete'] is (seq != 0), 'missing initial partial interval or extra partial')
        begin = integer(iv['open_stamp_mclk'], 'interval start')
        end = integer(iv['close_stamp_mclk'], 'interval end')
        require(end > begin, 'interval time does not advance')
        sl = slots[seq]
        require(len(sl) == integer(iv['entries'], 'entries', 1), 'slot entry count mismatch')
        require(sum(s[2] for s in sl) == integer(iv['executed'], 'executed', 1), 'executed count mismatch')
        require(len({(s[0], s[1]) for s in sl}) == len(sl), 'duplicate PC/context slot')
        for pc, ctx, n, m in sl:
            require(0 <= pc <= 0xFFFFFF and pc % 2 == 0, 'invalid instruction PC')
            require(ctx in (0, 1), 'invalid slot context')
            integer(n, 'instruction count', 1)
            integer(m, 'instruction MCLK')
        require(set(iv['kind_mclk']) == set(KINDS) | {'dma_rewind'}, 'unrecognised or missing stall kind')
        for k, v in iv['kind_mclk'].items():
            integer(v, k)
        for k in ('refresh_events', 'irq_delay_inline', 'synthetic_rte', 'abandoned_frames'):
            integer(iv[k], k)
        require(iv['abandoned_frames'] == 0, 'exception frames were abandoned; context needs explanation')
        close = integer(iv['close_accept_ordinal'], 'closing acceptance')
        require(close < len(accepts), 'closing acceptance missing')
        a = accepts[close]
        require(a['level'] == 6 and a['stamp_mclk'] == end and a['closes_bank_sequence'] == seq,
                'bank close does not match VInt acceptance')
        require(a['frame'] == iv['frame'] and a['label'] == iv['label'], 'bank and acceptance labels differ')
        require(bool(a['inside_instruction']) == (iv['straddle_pc'] >= 0), 'acceptance straddle flag mismatch')
        if seq == 0:
            require(iv['open_accept_ordinal'] == -1, 'initial partial has an opening acceptance')
            require(iv['carry_pc'] == -1, 'initial partial has a carry')
        else:
            prev = intervals[seq-1]
            require(begin == prev['close_stamp_mclk'] and iv['open_accept_ordinal'] == prev['close_accept_ordinal'],
                    'interval does not join its predecessor')
            require(iv['carry_pc'] == prev['straddle_pc'], 'carry PC differs from preceding straddle')
        integer(iv['carry_mclk'], 'carry MCLK')
        integer(iv['straddle_mclk'], 'straddle MCLK')
        if iv['carry_pc'] == -1:
            require(iv['carry_mclk'] == 0, 'carry time without a carry instruction')
        if iv['straddle_pc'] == -1:
            require(iv['straddle_mclk'] == 0, 'straddle time without a straddle instruction')
        else:
            matching = [s for s in sl if s[:2] == [iv['straddle_pc'], iv['straddle_context']]]
            require(len(matching) == 1 and matching[0][3] >= iv['straddle_mclk'], 'straddle slot absent or too short')
        within = [a for a in accepts if begin <= a['stamp_mclk'] < end]
        for suffix, chosen in [('vint', lambda level: level == 6), ('hint', lambda level: level == 4),
                               ('other', lambda level: level not in (4, 6))]:
            relevant = [a for a in within if chosen(a['level'])]
            require(iv['accept_' + suffix] == len(relevant), 'acceptance kind count mismatch: ' + suffix)
            require(iv['kind_mclk']['accept_' + suffix] == sum(a['cycles']*7 for a in relevant),
                    'acceptance kind time mismatch: ' + suffix)
    closed = {iv['close_accept_ordinal'] for iv in intervals}
    require(closed == {a['ordinal'] for a in accepts if a['level'] == 6}, 'unmatched VInt acceptance')
    return meta, intervals, slots


def identity(iv, sl):
    k = iv['kind_mclk']
    total = sum(s[3] for s in sl) + iv['carry_mclk'] + 14 * iv['refresh_events'] + sum(k[n] for n in KINDS) - k['dma_rewind']
    return (iv['close_stamp_mclk'] - iv['open_stamp_mclk']) - total


def admissible(dec, table, word, pc, count, mclk):
    import gpgx_cycles, m68k_timing
    if count <= 0 or mclk < 0 or mclk % 7:
        return 'fail', 'MCLK %d is not whole cycles' % mclk
    c = mclk // 7
    if pc >= 0xE00000:
        if pc in CITED_RAM:
            if hashlib.sha1(dec.rom).hexdigest() != S3K_SHA1:
                return 'unsupported', 'RAM citation applies only to the pinned S3K ROM'
            k = CITED_RAM[pc][0]
            return ('exact', '') if c == count * k else ('fail', 'cited %d x %d != %d' % (count, k, c))
        return 'unsupported', 'RAM PC without a cited cost'
    try:
        ins = dec.decode(pc)
    except m68k_timing.Unsupported as e:
        return 'unsupported', str(e)
    cost = gpgx_cycles.cost(table, ins, word(pc), word)
    if isinstance(cost, dict):
        (tl, th), (fl, fh) = cost['taken'], cost['fall']
        for a in range(count + 1):
            minimum = a * tl + (count - a) * fl
            if minimum <= c <= a * th + (count - a) * fh and (c - minimum) % 2 == 0:
                return ('mix-admissible' if tl == th and fl == fh else 'mix-bounded'), 'some mix: taken %d of %d' % (a, count)
        return 'fail', '%d cycles not a taken/fall mix of %s' % (c, cost)
    lo, hi = cost
    # The operand-dependent adjustments in the pinned GPGX helpers move in two-cycle steps, not every integer.
    if count * lo <= c <= count * hi and (c - count * lo) % 2 == 0:
        return ('exact' if lo == hi else 'bounded'), ''
    return 'fail', '%d cycles outside %d x [%d, %d]' % (c, count, lo, hi)


def perturb(intervals, slots, spec, dec, table, word, selected_sequences):
    parts = spec.split(':')
    require(2 <= len(parts) <= 4, 'perturb must be KIND:SEQ[:PC[:CONTEXT]]')
    what, seq = parts[0], int(parts[1])
    require(seq in selected_sequences, 'perturb must target a selected complete interval')
    matches = [i for i in intervals if i['sequence'] == seq]
    require(len(matches) == 1, 'perturb sequence not found')
    iv = matches[0]
    if what in ('kind', 'refresh'):
        require(len(parts) == 2, 'kind/refresh controls do not take a PC')
        if what == 'kind':
            iv['kind_mclk']['z80_bus'] += 7
        else:
            iv['refresh_events'] += 1
        return
    require(what in ('count', 'mclk', 'conserve_dma', 'conserve_z80_bus'), 'unknown perturb kind')
    rows = slots[seq]
    if len(parts) >= 3:
        pc = int(parts[2], 0)
        context = int(parts[3]) if len(parts) == 4 else None
        candidates = [s for s in rows if s[0] == pc and (context is None or s[1] == context)]
        require(len(candidates) == 1, 'perturb PC/context is missing or ambiguous')
    else:
        candidates = [s for s in rows if s[0] != iv['straddle_pc'] and
                      admissible(dec, table, word, s[0], s[2], s[3])[0] == 'exact']
        require(candidates, 'no fixed-cost instruction available for perturb')
    chosen = candidates[0]
    if what == 'count':
        chosen[2] += 1
    else:
        if what.startswith('conserve_'):
            stall = what[len('conserve_'):]
            require(iv['kind_mclk'][stall] >= 7, 'not enough classified stall time for control')
            iv['kind_mclk'][stall] -= 7
        chosen[3] += 7


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument('run'); ap.add_argument('--rom', required=True); ap.add_argument('--gpgx-git', required=True)
    ap.add_argument('--tools', default=str(Path(__file__).resolve().parent.parent / 'm68k-closure'),
                    help='directory providing m68k_timing.py/gpgx_cycles.py (default: ../m68k-closure)')
    ap.add_argument('--perturb')
    ap.add_argument('--labels', help='LO-HI: only intervals whose closing label is in range')
    a = ap.parse_args(argv)
    sys.path.insert(0, a.tools)
    import gpgx_cycles, m68k_timing
    try:
        lo = hi = None
        if a.labels is not None:
            require(re.fullmatch(r'[0-9]+-[0-9]+', a.labels), 'labels must be LO-HI')
            lo, hi = (int(x) for x in a.labels.split('-'))
            require(0 <= lo <= hi <= 65535, 'labels must satisfy 0 <= LO <= HI <= 65535')
        rom = Path(a.rom).read_bytes()
        meta, intervals, slots = validate_evidence(a.run, rom, gpgx_cycles.load_interrupt_cycles(a.gpgx_git))
        table = gpgx_cycles.load_table(a.gpgx_git)
    except (OSError, ValueError, KeyError, TypeError, AttributeError, gpgx_cycles.IdentityError) as error:
        print('STATUS: INVALID EVIDENCE ' + str(error)); return 3
    selected = [i for i in intervals if i['complete'] and (lo is None or lo <= i['label'] <= hi)]
    if not selected:
        print('STATUS: INCOMPLETE no complete interval selected'); return 2
    missing_labels = set() if lo is None else set(range(lo, hi + 1)) - {i['label'] for i in selected}
    dec = m68k_timing.Decoder(rom)
    word = lambda x: (rom[x] << 8) | rom[x + 1]
    if a.perturb:
        try:
            perturb(intervals, slots, a.perturb, dec, table, word, {i['sequence'] for i in selected})
        except ValueError as error:
            print('STATUS: INVALID CONTROL ' + str(error)); return 3
    by_seq = {i['sequence']: i for i in intervals}
    report = []
    for iv in selected:
        seq = iv['sequence']
        sl = [list(s) for s in slots[seq]]
        res = identity(iv, sl)
        nxt = by_seq.get(seq + 1)
        if iv['straddle_pc'] >= 0:
            if nxt is None or nxt['carry_pc'] != iv['straddle_pc']:
                report.append((seq, iv['label'], res, 'FAIL' if res != 0 else 'INCOMPLETE', 'straddle carry not available'))
                continue
            for s in sl:
                if s[0] == iv['straddle_pc'] and s[1] == iv['straddle_context']: s[3] += nxt['carry_mclk']
        counts = collections.Counter(); fails = []
        for pc, ctx, n, m in sl:
            kind, why = admissible(dec, table, word, pc, n, m)
            counts[kind] += 1
            if kind in ('fail', 'unsupported'): fails.append('%06X/%d n=%d mclk=%d %s: %s' % (pc, ctx, n, m, kind, why))
        st = 'PASS' if res == 0 and not fails else ('FAIL' if res != 0 or any('fail' in f.split(':')[0] for f in fails) else 'INCOMPLETE')
        report.append((seq, iv['label'], res, st, dict(counts), fails[:6], iv['close_stamp_mclk'] - iv['open_stamp_mclk'],
                       iv['accept_vint'], iv['accept_hint'], iv['kind_mclk']['z80_bus'], iv['refresh_events'],
                       iv['straddle_pc'], iv['irq_delay_inline'], iv['synthetic_rte'], iv['abandoned_frames']))
    for r in report:
        print(json.dumps(r))
    worst = max(({'PASS': 0, 'FAIL': 1, 'INCOMPLETE': 2}[r[3]] for r in report))
    if missing_labels:
        print('COVERAGE: INCOMPLETE missing %d requested labels, first=%d' % (len(missing_labels), min(missing_labels)))
        worst = 2
    fails = sum(1 for r in report if r[3] == 'FAIL')
    print('STATUS: %s intervals=%d fail=%d core=%s ingress=%s' % (['PASS', 'FAIL', 'INCOMPLETE'][1 if fails else worst],
          len(report), fails, meta['core_sha256'][:16], meta['ingress_kind']))
    return 1 if fails else worst


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
