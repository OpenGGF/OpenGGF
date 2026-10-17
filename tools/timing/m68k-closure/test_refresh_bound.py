#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R5 (2026-10-16).
# Inputs: none beyond this directory (independent simulation of the GPGX refresh rule).
# Promoted with the kos46 instruction-closure patches (v3 9b76415d..., successor 81f3097a...) on 2026-10-17 at base 00f8ab78a.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Refresh-bound tests against an independent simulation of GPGX 051d430d m68k_run (m68kcpu.c:308-313):
at each instruction fetch, if t >= R then R = t + 128 and t += 2; stalls (Z80 bus, DMA freeze, interrupt acceptance)
advance t between fetches. Positive: the tool's bound contains the simulated count. Red: the 73d296f12 formula
(instruction-only body plus a stall-free idle simulation) excludes it once Z80 stalls are PAL-dense. Broken
precondition: without the measured stall the tool returns no bound, and a zero stall is not a valid substitute."""
import os
import random
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import frame_closure as fc  # noqa: E402

PLAT = {'idle_loop': {'pcs': ['000000', '000004'], 'step_cycles': [12, 10]}}
DMA = [2145, 1532, 158]          # VInt_8 freezes in cycles (VRAM 448/320 words, CRAM 64 words)


def simulate(events, phase):
    t, nxt, n = 0, phase, 0
    for kind, d in events:
        if kind == 'ins':
            if t >= nxt:
                nxt, t, n = t + 128, t + 2, n + 1
        t += d
    return n


def bucket(seed, z80_accesses, idle_iters=4000):
    rng = random.Random(seed)
    body = [rng.choice([4, 4, 8, 8, 12, 12, 16, 20, 24, 28]) for _ in range(3000)]
    events = [('ins', d) for d in body[:1500]]
    events += [('ins', d) for _ in range(idle_iters) for d in (12, 10)]
    events += [('jump', 55)] + [('ins', d) for d in body[1500:2000]] + [('jump', j) for j in DMA] + \
              [('ins', d) for d in body[2000:]]
    z80 = 0
    for _ in range(z80_accesses):
        j = rng.choice([10, 11])
        events.insert(rng.randrange(len(events)), ('jump', j))
        z80 += j
    instr = sum(body) + idle_iters * 22
    t = {'instr': [instr, instr], 'interrupt': [50, 59], 'dma_jumps_mclk': [j * 7 for j in DMA],
         'idle_iters': idle_iters, 'durations': body}
    return events, t, z80


def old_73d_bound(t):
    a, b = fc.idle_refresh_interval(t['idle_iters'], PLAT['idle_loop']['step_cycles'])
    body = t['instr'][0] - t['idle_iters'] * 22 + t['interrupt'][0]
    rmax = int(body / 128) + 2
    over = sorted((d - 1 for d in t['durations']), reverse=True)[:rmax]
    rmin = max(0, int((body - sum(over)) / 128) - 1)
    return a + rmin, b + rmax


class RefreshBound(unittest.TestCase):
    def test_bound_contains_simulated_counts(self):
        for seed in range(12):
            for z80 in (0, 20, 330):
                events, t, stall = bucket(seed, z80)
                lo, hi = fc.refresh_bounds(t, PLAT, stall)
                for phase in (0, 37, 101):
                    n = simulate(events, phase)
                    self.assertTrue(lo <= n <= hi, (seed, z80, phase, n, lo, hi))

    def test_73d_formula_misses_pal_dense_stalls(self):
        misses = 0
        for seed in range(12):
            events, t, stall = bucket(seed, 330)
            lo, hi = old_73d_bound(t)
            misses += simulate(events, 0) > hi
        self.assertGreater(misses, 0)

    def test_missing_or_zero_stall_is_not_a_bound(self):
        events, t, stall = bucket(3, 3000)        # dense Z80 stalls (about 31k cycles)
        self.assertIsNone(fc.refresh_bounds(t, PLAT, None))
        lo, hi = fc.refresh_bounds(t, PLAT, stall)
        self.assertTrue(lo <= simulate(events, 0) <= hi)
        lo, hi = fc.refresh_bounds(t, PLAT, 0.0)
        self.assertGreater(simulate(events, 0), hi)


if __name__ == '__main__':
    unittest.main()
