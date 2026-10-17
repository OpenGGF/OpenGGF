#!/usr/bin/env python3
"""Offline 68000 frame-time closure check against native per-frame execution histograms.

Purpose: test whether a ROM-derived cost model explains every 68000 cycle of a native frame window, so that a
later production CPU-cost ledger can be validated without fitting. For each window of level-frame buckets it sums
  instruction cycles (ROM bytes decoded by m68k_timing, costed by GPGX rules or Motorola tables; conditional-branch
  outcomes solved from flow conservation, open outcomes kept as intervals)
  + interrupt entry + DMA freeze + Z80-area access latency + bus refresh
and compares the sum with the exact frame period (lines x 3420 / 7 cycles) plus the measured VInt-to-increment
endpoint difference. Native data is comparison input only; nothing here is a runtime cost.

Commands:
  report  write the full JSON analysis; exit 0 unless an identity or usage error occurs (exit 3).
  check   fail closed: exit 0 PASS only when every window's whole residual interval lies within tolerance with no
          unknown term (an interval that only overlaps the tolerance is INCOMPLETE);
          exit 1 FAIL when a window cannot close (over-prediction beyond tolerance, a residual that excludes zero
          with every term known, or an observed counter outside the model's bound); exit 2 INCOMPLETE when any
          term is unknown (unsupported instruction, missing stall counters or endpoint stamps, unverifiable
          endpoints, flow assumptions violated); exit 3 ERROR for identity or usage failures.
Inputs: --spec (cases, windows, identities), --rom, --capture-root, optional --counters (observer stall counters),
and --gpgx-git (Genesis-Plus-GX repository) unless the spec selects the Motorola cost model.
Origin: OpenGGF S3K ending Kos CPU-budget research (known-bug "Ending Reunion Kos Service Count"), 2026-10-16.
GPGX hardware rules cited from 051d430d: refresh m68kcpu.c:308-313; DMA vdp_ctrl.c:632-690; interrupt acceptance
m68ki_cycle_interrupts[(cycles/7)%10] = 50..59 cycles (m68kcpu.h:1405,1449; exact only with an observed phase); Z80-area latency mem68k.c:144,179; Z80 access to the 68000 bus memz80.c:95-118 and
VDP FIFO waits vdp_ctrl.c:2307,2401 are not visible in 68000 execution counts and must come from --counters.
"""
import argparse
import math
import collections
import csv
import hashlib
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m68k_timing  # noqa: E402
import gpgx_cycles  # noqa: E402
import refine  # noqa: E402

PASS, FAIL, INCOMPLETE, ERROR = 0, 1, 2, 3
STATUS = {PASS: 'PASS', FAIL: 'FAIL', INCOMPLETE: 'INCOMPLETE', ERROR: 'ERROR'}
MCLK_PER_LINE = 3420
LINES = {'ntsc': 262, 'pal': 313}
COUNTER_FIELDS = ('z80_bus_mclk', 'fifo_wait_mclk', 'refresh_events', 'dma_freeze_mclk', 'vint_to_increment_mclk')


class UsageError(Exception):
    pass


def sha(path, algo):
    h = hashlib.new(algo)
    with open(path, 'rb') as f:
        h.update(f.read())
    return h.hexdigest()


# ---------------------------------------------------------------- cost model

class CostModel:
    def __init__(self, spec, args, rom):
        self.kind = spec['cost_model']['kind']
        self.rom = rom
        if self.kind == 'gpgx':
            if not args.gpgx_git:
                raise UsageError('spec selects the GPGX cost model; --gpgx-git is required')
            self.table = gpgx_cycles.load_table(args.gpgx_git, spec['cost_model']['commit'],
                                                spec['cost_model']['cycles_blob'])
            if 'cpu_header_blob' not in spec['cost_model']:
                raise UsageError('GPGX cost model needs cpu_header_blob for the interrupt acceptance table')
            irq = gpgx_cycles.load_interrupt_cycles(args.gpgx_git, spec['cost_model']['commit'],
                                                    spec['cost_model']['cpu_header_blob'])
            self.interrupt = (min(irq), max(irq))
        elif self.kind == 'motorola':
            self.interrupt = (44, 44)   # Motorola UM autovector interrupt time; E-clock synchronisation not modelled
        else:
            raise UsageError('unknown cost model %r' % self.kind)

    def word(self, a):
        return (self.rom[a] << 8) | self.rom[a + 1]

    def cost(self, ins):
        if self.kind == 'gpgx':
            return gpgx_cycles.cost(self.table, ins, self.word(ins.pc), self.word)
        if ins.kind in ('cond', 'dbcc'):
            return {'taken': (ins.taken.lo, ins.taken.hi), 'fall': (ins.fall.lo, ins.fall.hi)}
        return (ins.cost.lo, ins.cost.hi)


# ---------------------------------------------------------------- flow solver

def solve_bucket(counts, dec, cache, cited, pins=None):
    """Decode executed PCs and solve conditional outcomes. Returns dict with intervals and solver evidence."""
    ins, unsupported = {}, {}
    for pc, c in counts.items():
        if pc in cited:
            continue
        if pc not in cache:
            try:
                cache[pc] = dec.decode(pc)
            except m68k_timing.Unsupported as e:
                cache[pc] = e
        if isinstance(cache[pc], Exception):
            unsupported[pc] = (str(cache[pc]), c)
        else:
            ins[pc] = cache[pc]
    known = collections.Counter()
    tin, fin = collections.defaultdict(list), collections.defaultdict(list)
    br = {}
    indirect = rte = 0
    for pc, i in ins.items():
        c = counts[pc]
        nxt = pc + i.length
        if i.kind == 'seq':
            known[nxt] += c
        elif i.kind in ('cond', 'dbcc'):
            br[pc] = list((pins or {}).get(pc, [0, c]))
            tin[i.target].append(pc)
            fin[nxt].append(pc)
        elif i.kind == 'jump':
            known[i.target] += c
        elif i.kind == 'call':
            known[i.target] += c
            known[nxt] += c
        elif i.kind == 'icall':
            known[nxt] += c
            indirect += c
        elif i.kind == 'ijump':
            indirect += c
        elif i.kind == 'rte':
            rte += c
    nodes = set(counts) | set(tin) | set(fin)
    land, deficit = collections.Counter(), collections.Counter()

    def rhs(x):
        return counts.get(x, 0) - known[x] - sum(counts[p] for p in fin[x])

    for _ in range(500):
        changed = False
        for x in nodes:
            r = rhs(x) - land[x] + deficit[x]
            terms = [(p, 1) for p in tin[x]] + [(p, -1) for p in fin[x]]
            smin = sum(br[p][0] if s > 0 else -br[p][1] for p, s in terms)
            smax = sum(br[p][1] if s > 0 else -br[p][0] for p, s in terms)
            if r > smax:
                land[x] += r - smax
                changed = True
                continue
            if r < smin:
                deficit[x] += smin - r
                changed = True
                continue
            for p, s in terms:
                omin = smin - (br[p][0] if s > 0 else -br[p][1])
                omax = smax - (br[p][1] if s > 0 else -br[p][0])
                lo, hi = (r - omax, r - omin) if s > 0 else (omin - r, omax - r)
                nlo, nhi = max(br[p][0], lo), min(br[p][1], hi)
                if nlo <= nhi and [nlo, nhi] != br[p]:
                    br[p] = [nlo, nhi]
                    changed = True
        if not changed:
            break
    else:
        raise RuntimeError('flow solver did not converge')
    return {'ins': ins, 'unsupported': unsupported, 'branches': br, 'landings': land, 'deficits': deficit,
            'indirect': indirect, 'rte': rte}


# ---------------------------------------------------------------- bucket terms

def idle_refresh_interval(iters, steps):
    """Refresh count range over every starting phase for a contiguous idle loop of `iters` iterations."""
    if iters <= 0:
        return 0, 0
    res = []
    for rot in range(len(steps)):
        seq = steps[rot:] + steps[:rot]
        for phase in range(0, 128 + max(steps), 2):
            t, nxt, n = 0, phase, 0
            for _ in range(iters):
                for d in seq:
                    if t >= nxt:
                        nxt = t + 128
                        t += 2
                        n += 1
                    t += d
            res.append(n)
    return min(res), max(res)


def bucket_terms(counts, model, dec, cache, plat, dma_words):
    cited = {int(k, 16): v for k, v in plat['cited_instruction_cycles'].items()}
    s, refined, notes = refine.resolve(model.rom, counts,
                                       lambda pins: solve_bucket(counts, dec, cache, cited, pins),
                                       model.cost, plat.get('refinements', []))
    lo = hi = 0
    durations = []
    open_branches = 0
    idle_pcs = [int(p, 16) for p in plat['idle_loop']['pcs']]
    for pc, i in s['ins'].items():
        c = counts[pc]
        k = model.cost(i)
        if pc in refined:
            lo += refined[pc]
            hi += refined[pc]
            d = max(k['taken'][1], k['fall'][1]) if isinstance(k, dict) else k[1]
            if pc not in idle_pcs:
                durations.extend([d] * c)
            continue
        if isinstance(k, dict):
            t0, t1 = s['branches'][pc]
            open_branches += t0 != t1
            cand = [t * k['taken'][j] + (c - t) * k['fall'][j] for t in (t0, t1) for j in (0, 1)]
            lo += min(cand)
            hi += max(cand)
            d = max(k['taken'][1], k['fall'][1])
        else:
            lo += c * k[0]
            hi += c * k[1]
            d = k[1]
        if pc not in idle_pcs:
            durations.extend([d] * c)
    for pc, cyc in cited.items():
        lo += counts.get(pc, 0) * cyc['cycles']
        hi += counts.get(pc, 0) * cyc['cycles']
        durations.extend([cyc['cycles']] * counts.get(pc, 0))
    vints = counts.get(int(plat['vint_entry_pc'], 16), 0)
    irq = [vints * model.interrupt[0], vints * model.interrupt[1]]
    z80lat = 0
    for pc, i in s['ins'].items():
        for a in i.abs_addrs:
            if 0xA00000 <= a <= 0xA0FFFF:
                z80lat += counts[pc] * (2 if i.size == 'l' else 1)
    dma_jumps = [(w * MCLK_PER_LINE) // r for w, r in plat['vint_dma_words_and_rates']] * vints
    dma_jumps += [(w * MCLK_PER_LINE) // plat['queue_dma_rate'] for w in dma_words]
    dma_mclk = sum(dma_jumps)
    idle_iters = counts.get(idle_pcs[0], 0)
    if any(counts.get(p, 0) != idle_iters for p in idle_pcs):
        idle_iters = None
    bookmarks = counts.get(int(plat['bookmark_redirect_pc'], 16), 0)
    # every RTE beyond the interrupt returns (e.g. a stacked-SR/PC resume) lands at a dynamic address
    budget_l = s['indirect'] + vints + 1 + bookmarks + max(0, s['rte'] - vints)
    budget_d = 1 + bookmarks
    return {'instr': [lo, hi], 'vints': vints, 'interrupt': irq, 'dma_jumps_mclk': dma_jumps, 'z80_area_latency': z80lat, 'dma_mclk': dma_mclk,
            'idle_iters': idle_iters, 'bookmarks': bookmarks, 'open_branches': open_branches,
            'unsupported': {('%06X' % pc): why for pc, (why, c) in s['unsupported'].items()},
            'forced_landings': sum(s['landings'].values()), 'landing_budget': budget_l,
            'forced_deficits': sum(s['deficits'].values()), 'deficit_budget': budget_d,
            'refined_pcs': sorted('%06X' % p for p in refined), 'refinement_notes': notes,
            'durations': durations}


def refresh_bounds(t, plat, stall_cycles):
    """Sound bounds on GPGX bus-refresh events in one bucket (m68kcpu.c:308-313).

    GPGX tests `m68k.cycles >= m68k.refresh_cycles` at every instruction fetch, then sets
    refresh_cycles = m68k.cycles + 128 and adds 2. m68k.cycles is the single clock that instructions, interrupt
    acceptance (m68kcpu.h:1449), DMA freezes (vdp_ctrl.c:690), FIFO waits (vdp_ctrl.c:2307,2401) and Z80 accesses to
    the 68000 bus (memz80.c:114) all advance, and system.c:687-688 rebases both counters by the same amount, so every
    one of those stalls is elapsed time for the refresh test. With W = bucket time without refresh stalls and
    T = W + 2R:
      upper: consecutive refreshes are >= 128 apart and a DMA freeze of J cycles lies inside one interval, so
             T >= 128 (R - 1) + sum(max(0, J - 128))
      lower: each interval exceeds 128 by less than the fetch gap that straddles its deadline (+2 for a refresh
             in that gap); deadlines are >= 128 apart, so at most floor(B / 128) + 2 fall outside the contiguous
             idle loop (gap <= its longest step) and every non-instruction stall enlarges at most one gap:
             T <= 128 (R + 1) + top_nb(body gaps) + (idle_step - 1)(R + 1) + J_total + 2 (R + 1).
    `stall_cycles` (Z80 bus + FIFO, measured) is required; without it no sound bound exists and None is returned."""
    if stall_cycles is None:
        return None
    dma = [j / 7 for j in t['dma_jumps_mclk']]
    jumps = t['interrupt'][1] + sum(dma) + stall_cycles
    w_lo = t['instr'][0] + t['interrupt'][0] + sum(dma) + stall_cycles
    w_hi = t['instr'][1] + t['interrupt'][1] + sum(dma) + stall_cycles
    absorbed = sum(max(0.0, j - 128) for j in dma)
    r_hi = math.floor(((w_hi - absorbed) / 128 + 1) * 64 / 63)
    steps = plat['idle_loop']['step_cycles']
    idle_time = (t['idle_iters'] or 0) * sum(steps)
    body_time = t['instr'][1] - idle_time + 2 + jumps
    nb = int(body_time // 128) + 3
    top = sum(sorted((d - 1 for d in t['durations']), reverse=True)[:nb])
    idle_over = max(steps) - 1
    r_lo = max(0, math.ceil((w_lo - jumps - top - 128 - idle_over - 2) / (128 + idle_over)))
    return r_lo, r_hi


# ---------------------------------------------------------------- windows

def close_window(case, a, b, terms, counters, plat, tol):
    unknown, failures = [], []
    need = [a - 1] + list(range(a, b + 1))
    missing = [n for n in need if n not in terms]
    if missing:
        return {'status': INCOMPLETE, 'unknown': ['bucket(s) %s absent from capture' % missing]}
    for n in (a - 1, b):
        t = terms[n]
        if not t['idle_iters'] or t['bookmarks']:
            unknown.append('VInt %d did not provably arrive in the idle loop' % n)
    win = [terms[n] for n in range(a, b + 1)]
    for n, t in zip(range(a, b + 1), win):
        if t['unsupported']:
            unknown.append('LF %d unsupported instructions %s' % (n, t['unsupported']))
        if t['forced_landings'] > t['landing_budget'] or t['forced_deficits'] > t['deficit_budget']:
            unknown.append('LF %d flow assumptions violated (landings %d/%d, deficits %d/%d)' % (
                n, t['forced_landings'], t['landing_budget'], t['forced_deficits'], t['deficit_budget']))
    period = (b - a + 1) * LINES[case['region']] * MCLK_PER_LINE / 7
    instr = [sum(t['instr'][0] for t in win), sum(t['instr'][1] for t in win)]
    fixed = sum(t['z80_area_latency'] + t['dma_mclk'] / 7 for t in win)
    irq = [sum(t['interrupt'][0] for t in win), sum(t['interrupt'][1] for t in win)]
    r_lo = r_hi = 0
    extra = None
    obs = {}
    if counters is None:
        unknown.append('stall counters absent: Z80 bus, FIFO, endpoint stamps')
        # informational only: with unmeasured stalls the refresh upper bound is open, and the window is INCOMPLETE
        rb = [refresh_bounds(t, plat, 0.0) for t in win]
        r_lo, r_hi = sum(x[0] for x in rb), sum(x[1] for x in rb)
    else:
        rows = [counters.get(n) for n in need]
        if any(r is None or any(r.get(f) is None for f in COUNTER_FIELDS) for r in rows):
            unknown.append('stall counters incomplete for LF %d..%d' % (a - 1, b))
        else:
            wrows = rows[1:]
            rb = [refresh_bounds(t, plat, (r['z80_bus_mclk'] + r['fifo_wait_mclk']) / 7) for t, r in zip(win, wrows)]
            r_lo, r_hi = sum(x[0] for x in rb), sum(x[1] for x in rb)
            obs_refresh = sum(r['refresh_events'] for r in wrows)
            obs_dma = sum(r['dma_freeze_mclk'] for r in wrows)
            model_dma = sum(t['dma_mclk'] for t in win)
            refresh_bound = [r_lo, r_hi]
            if not (r_lo <= obs_refresh <= r_hi):
                failures.append('observed refresh %d outside model bound [%d, %d]' % (obs_refresh, r_lo, r_hi))
            if obs_dma != model_dma:
                failures.append('observed DMA freeze %d MCLK != model %d MCLK' % (obs_dma, model_dma))
            r_lo = r_hi = obs_refresh
            endpoint = (rows[-1]['vint_to_increment_mclk'] - rows[0]['vint_to_increment_mclk']) / 7
            extra = sum(r['z80_bus_mclk'] + r['fifo_wait_mclk'] for r in wrows) / 7
            period += endpoint
            if all(r.get('vint_interrupt_cycles') is not None for r in wrows):
                obs_irq = sum(r['vint_interrupt_cycles'] for r in wrows)
                if not irq[0] <= obs_irq <= irq[1]:
                    failures.append('observed interrupt cycles %d outside model bound %s' % (obs_irq, irq))
                irq = [obs_irq, obs_irq]
                obs['interrupt_cycles'] = obs_irq
            elif any(r.get('vint_interrupt_cycles') is not None for r in wrows):
                unknown.append('interrupt phase observed for only part of LF %d..%d' % (a, b))
            obs.update({'refresh': obs_refresh, 'refresh_bound': refresh_bound, 'z80_fifo_cycles': round(extra, 3),
                        'endpoint_cycles': round(endpoint, 3)})
    known_lo = instr[0] + irq[0] + fixed + 2 * r_lo + (extra or 0)
    known_hi = instr[1] + irq[1] + fixed + 2 * r_hi + (extra or 0)
    resid = [period - known_hi, period - known_lo]
    out = {'buckets': b - a + 1, 'period': round(period, 3), 'instr': instr, 'interrupt': irq,
           'fixed_stalls': round(fixed, 3),
           'refresh': [r_lo, r_hi], 'residual': [round(resid[0], 3), round(resid[1], 3)], 'tolerance': tol,
           'observed': obs, 'unknown': unknown, 'failures': failures}
    if failures or resid[1] < -tol:
        if resid[1] < -tol:
            failures.append('model over-predicts by at least %.1f cycles' % -resid[1])
        out['status'] = FAIL
    elif unknown:
        out['status'] = INCOMPLETE
        out['required_unobserved_stall_cycles'] = [round(max(0.0, resid[0] - tol), 3), round(resid[1] + tol, 3)]
    elif resid[0] > tol:
        failures.append('unexplained residual of at least %.1f cycles' % (resid[0] - tol))
        out['status'] = FAIL
    elif resid[0] < -tol or resid[1] > tol:
        # Every term is known but operand-dependent times or unforced branch outcomes leave an interval that
        # only overlaps the tolerance: some unobserved value would close, which does not prove closure.
        unknown.append('residual interval [%.1f, %.1f] is wider than the tolerance +-%s (unresolved operand, branch-outcome or interrupt-phase '
                       'cycles)' % (resid[0], resid[1], tol))
        out['status'] = INCOMPLETE
    else:
        out['status'] = PASS
    return out


# ---------------------------------------------------------------- driver

def load_inputs(args):
    with open(args.spec) as f:
        spec = json.load(f)
    if spec.get('schema') != 'openggf.m68k-frame-closure-spec.v1':
        raise UsageError('unsupported spec schema %r' % spec.get('schema'))
    with open(args.rom, 'rb') as f:
        rom = f.read()
    if hashlib.sha1(rom).hexdigest() != spec['rom_sha1']:
        raise gpgx_cycles.IdentityError('ROM SHA-1 %s != spec %s' % (hashlib.sha1(rom).hexdigest(), spec['rom_sha1']))
    counters = {}
    if args.counters:
        if spec.get('counters_schema') != 'openggf.m68k-stall-counters.v1':
            raise UsageError('spec does not declare the stall-counter schema')
        with open(args.counters) as f:
            for line in f:
                r = json.loads(line)
                counters.setdefault(r['case'], {})[int(r['level_frame'])] = r
    return spec, rom, counters


def run(args):
    spec, rom, counters = load_inputs(args)
    model = CostModel(spec, args, rom)
    dec = m68k_timing.Decoder(rom)
    cache = {}
    plat = spec['platform']
    tol = spec['tolerance_cycles']
    result = {'spec_sha256': sha(args.spec, 'sha256'), 'rom_sha1': spec['rom_sha1'], 'cost_model': spec['cost_model'],
              'counters_sha256': sha(args.counters, 'sha256') if args.counters else None, 'cases': {}}
    worst = PASS
    for case in spec['cases']:
        cdir = os.path.join(args.capture_root, case['capture'])
        hist, dmaf = os.path.join(cdir, 'frame-pc-histogram.csv'), os.path.join(cdir, 'frame-dma.csv')
        for path, want in ((hist, case['histogram_sha256']), (dmaf, case['dma_sha256'])):
            if sha(path, 'sha256') != want:
                raise gpgx_cycles.IdentityError('%s sha256 differs from spec' % path)
        buckets = collections.defaultdict(collections.Counter)
        with open(hist) as f:
            hist_rows = list(csv.DictReader(f))
        with open(dmaf) as f:
            dma_rows = list(csv.DictReader(f))
        for r in hist_rows:
            buckets[int(r['level_frame'])][int(r['pc'], 16)] += int(r['count'])
        dma = collections.defaultdict(list)
        for r in dma_rows:
            if int(r['entries']):
                if int(r['entries']) != 1:
                    raise UsageError('multi-entry DMA rows need per-entry words')
                dma[int(r['level_frame'])].append(int(r['total_words']))
        terms = {lf: bucket_terms(c, model, dec, cache, plat, dma[lf]) for lf, c in sorted(buckets.items())}
        wins = {}
        for a, b in case['windows']:
            w = close_window(case, a, b, terms, counters.get(case['name']) if args.counters else None, plat, tol)
            wins['%d-%d' % (a, b)] = w
            if w['status'] == FAIL or worst == FAIL:
                worst = FAIL
            elif w['status'] == INCOMPLETE:
                worst = INCOMPLETE
        result['cases'][case['name']] = {
            'per_bucket': {lf: {k: v for k, v in t.items() if k != 'durations'} for lf, t in terms.items()},
            'windows': wins}
    return worst, result


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('command', choices=('report', 'check'))
    ap.add_argument('--spec', required=True)
    ap.add_argument('--rom', required=True)
    ap.add_argument('--capture-root', required=True)
    ap.add_argument('--counters')
    ap.add_argument('--gpgx-git')
    args = ap.parse_args(argv)
    try:
        status, result = run(args)
    except (UsageError, gpgx_cycles.IdentityError, OSError, KeyError, ValueError) as e:
        print('STATUS: ERROR %s' % e)
        return ERROR
    if args.command == 'report':
        json.dump(result, sys.stdout, indent=1, sort_keys=True, default=str)
        print()
        return PASS
    for name, c in result['cases'].items():
        for w, x in c['windows'].items():
            detail = '; '.join(x.get('failures', []) + x.get('unknown', []))
            print('%-11s %s %s residual=%s %s' % (STATUS[x['status']], name, w, x.get('residual'), detail))
    print('STATUS: %s' % STATUS[status])
    return status


if __name__ == '__main__':
    sys.exit(main())
