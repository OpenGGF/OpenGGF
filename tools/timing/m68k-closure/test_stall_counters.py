#!/usr/bin/env python3
"""Tests for stall_counters.py with hand-written cumulative stamps (expected deltas computed by hand)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stall_counters as sc  # noqa: E402

COLS = ('event', 'level_frame', 'frame_ordinal', 'frame_base_mclk', 'm68k_cycles',
        'z80_bus_mclk', 'fifo_wait_mclk', 'refresh_events', 'dma_freeze_mclk')


def rows(*tuples):
    return [dict(zip(COLS, map(str, t))) for t in tuples]


GOOD = rows(('increment', 10, 0, 0, 5000, 0, 0, 0, 0),
            ('kos', 11, 0, 0, 300000, 700, 0, 300, 0),
            ('vint', 11, 0, 0, 896040 - 1000, 1400, 0, 900, 0),
            ('increment', 11, 1, 896040, 4000, 1477, 0, 940, 26855))


class StallCounters(unittest.TestCase):
    def test_deltas_and_split(self):
        out = sc.convert(GOOD, 'c')
        self.assertEqual(out, [{'case': 'c', 'level_frame': 11, 'z80_bus_mclk': 1477, 'fifo_wait_mclk': 0,
                                'refresh_events': 940, 'dma_freeze_mclk': 26855,
                                'vint_to_increment_mclk': 896040 + 4000 - (896040 - 1000),
                                'kos_to_vint_mclk': (896040 - 1000) - 300000}])

    def test_missing_vint_fails_closed(self):
        with self.assertRaises(sc.StampError):
            sc.convert([GOOD[0], GOOD[1], GOOD[3]], 'c')

    def test_lag_frame_double_vint_fails_closed(self):
        with self.assertRaises(sc.StampError):
            sc.convert([GOOD[0], GOOD[1], GOOD[2], GOOD[2], GOOD[3]], 'c')

    def test_counter_regression_fails_closed(self):
        bad = [dict(r) for r in GOOD]
        bad[3]['refresh_events'] = '1'
        with self.assertRaises(sc.StampError):
            sc.convert(bad, 'c')


if __name__ == '__main__':
    unittest.main()
