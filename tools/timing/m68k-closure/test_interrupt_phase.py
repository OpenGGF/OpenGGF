#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), rounds R4-R5 (2026-10-16) plus the successor case (native-stall-counters lane).
# Inputs: none beyond this directory (hand-written stamps).
# Promoted with the kos46 instruction-closure patches (v3 9b76415d..., successor 81f3097a...) on 2026-10-17 at base 00f8ab78a.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Tests for interrupt_phase.py. Stamps are written by hand from the GPGX 051d430d rule
(acceptance cost = table[(frame-relative cycles / 7) % 10], table 50,59,58,...,51) so the expected cost is stated here."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import interrupt_phase as ip  # noqa: E402

TABLE = [50, 59, 58, 57, 56, 55, 54, 53, 52, 51]
# idle loop: $1D1C tst.b (abs).w = 12, $1D20 bne.s = 10 taken; synthetic ROM holds those two words at the same PCs
ROM = bytearray(0x1D30)
ROM[0x1D1C:0x1D20] = bytes([0x4A, 0x38, 0xF6, 0x2A])
ROM[0x1D20:0x1D22] = bytes([0x66, 0xFA])
ROM = bytes(ROM)


def motorola_cost(i):
    if i.kind in ('cond', 'dbcc'):
        return {'taken': (i.taken.lo, i.taken.hi), 'fall': (i.fall.lo, i.fall.hi)}
    return (i.cost.lo, i.cost.hi)


def row(event, pc, cycles, refresh=0, z80=0, ret=''):
    return {'event': event, 'level_frame': '4401', 'pc': pc, 'frame_base_mclk': '1000000', 'm68k_cycles': str(cycles),
            'refresh_events': str(refresh), 'z80_bus_mclk': str(z80), 'fifo_wait_mclk': '0', 'dma_freeze_mclk': '0',
            'return_pc': ret, 'operand': ''}


class InterruptPhase(unittest.TestCase):
    def accept_after_tst(self, start, refresh=0):
        # tst.b starts at `start` (frame-relative MCLK, multiple of 7); ends 12 cycles (+2 if a refresh hit its fetch)
        end = start + 7 * 12 + 14 * refresh
        c = TABLE[(end // 7) % 10]
        return c, [row('last', '001D1C', start), row('accept', 'FFFFF0', end + 7 * c, refresh=refresh, ret='001D20')]

    def test_each_phase_recovers_table_cost(self):
        for k in range(10):
            c, rows = self.accept_after_tst(700 * 3 + 7 * k)
            out = ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})
            self.assertEqual(out[0]['vint_interrupt_cycles'], c)

    def test_refresh_inside_the_interrupted_instruction(self):
        c, rows = self.accept_after_tst(7 * 1003, refresh=1)
        out = ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})
        self.assertEqual(out[0]['vint_interrupt_cycles'], c)

    def test_wrong_successor_fails_closed(self):
        _, rows = self.accept_after_tst(7 * 1000)
        rows[1]['return_pc'] = '001D1C'
        with self.assertRaises(ip.PhaseError):
            ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})

    def test_inconsistent_with_table_fails_closed(self):
        _, rows = self.accept_after_tst(7 * 1000)
        rows[1]['m68k_cycles'] = str(int(rows[1]['m68k_cycles']) + 7)   # one cycle too late for any table entry
        with self.assertRaises(ip.PhaseError):
            ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})

    def test_branch_outcome_from_return_pc(self):
        start = 7 * 2000
        end = start + 7 * 10                       # bne.s taken back to $1D1C
        c = TABLE[(end // 7) % 10]
        rows = [row('last', '001D20', start), row('accept', 'FFFFF0', end + 7 * c, ret='001D1C')]
        out = ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})
        self.assertEqual(out[0]['vint_interrupt_cycles'], c)



class FailClosed(unittest.TestCase):
    """Negative controls for the v9 input contract (review reproducer 2026-10-16 first)."""
    COUNTERS = [{'case': 'x', 'level_frame': 4401}]

    def good(self):
        start = 7 * 2000
        end = start + 7 * 12
        c = TABLE[(end // 7) % 10]
        return c, [row('last', '001D1C', start), row('accept', 'FFFFF0', end + 7 * c, ret='001D20')]

    def bad(self, rows, counters=None, frames=(4401,)):
        with self.assertRaises(ip.PhaseError):
            ip.run(ROM, TABLE, rows, self.COUNTERS if counters is None else counters, 'x', motorola_cost, frames)

    def test_review_reproducer_empty_stamps_with_prepopulated_phase(self):
        with self.assertRaises(ip.PhaseError):
            ip.run(bytes(16), [50] * 10, [], [{'case': 'tails', 'level_frame': 4401, 'vint_interrupt_cycles': 55}],
                   'tails', lambda i: (4, 4), {4401})

    def test_no_frames_requested(self):
        self.bad(self.good()[1], frames=())

    def test_absent_case(self):
        self.bad(self.good()[1], counters=[{'case': 'other', 'level_frame': 4401}])

    def test_requested_frame_without_acceptance(self):
        self.bad(self.good()[1], counters=self.COUNTERS + [{'case': 'x', 'level_frame': 4402}], frames=(4401, 4402))

    def test_trailing_unmatched_last(self):
        c, rows = self.good()
        self.bad(rows + [row('last', '001D1C', 7 * 9000)])

    def test_double_last(self):
        c, rows = self.good()
        self.bad([row('last', '001D20', 7 * 1990)] + rows)

    def test_accept_at_wrong_pc(self):
        c, rows = self.good()
        rows[1]['pc'] = '000584'
        self.bad(rows)

    def test_regressing_counter(self):
        c, rows = self.good()
        rows[0]['refresh_events'] = '5'
        self.bad(rows)

    def test_stale_prepopulated_phase_rejected_and_revalidated_phase_kept(self):
        c, rows = self.good()
        self.bad(rows, counters=[{'case': 'x', 'level_frame': 4401, 'vint_interrupt_cycles': c + 1}])
        out = ip.run(ROM, TABLE, rows, [{'case': 'x', 'level_frame': 4401, 'vint_interrupt_cycles': c}], 'x',
                     motorola_cost, {4401})
        self.assertEqual(out[0]['vint_interrupt_cycles'], c)

    def test_phase_outside_requested_frames_rejected(self):
        c, rows = self.good()
        self.bad(rows, counters=self.COUNTERS + [{'case': 'x', 'level_frame': 4402, 'vint_interrupt_cycles': 55}])

    def test_unconditional_branch_successor_is_its_target(self):
        # bra.s back to $1D1C (60FA at $1D20 decodes as a conditional bne here, so use a dedicated bra.s at $1D24)
        rom = bytearray(ROM); rom[0x1D24:0x1D26] = bytes([0x60, 0xF6]); rom = bytes(rom)   # bra.s $1D1C
        start = 7 * 3000
        end = start + 7 * 10
        c = TABLE[(end // 7) % 10]
        rows = [row('last', '001D24', start), row('accept', 'FFFFF0', end + 7 * c, ret='001D1C')]
        out = ip.run(rom, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})
        self.assertEqual(out[0]['vint_interrupt_cycles'], c)
        rows[1]['return_pc'] = '001D26'
        with self.assertRaises(ip.PhaseError):
            ip.run(rom, TABLE, rows, [{'case': 'x', 'level_frame': 4401}], 'x', motorola_cost, {4401})


if __name__ == '__main__':
    unittest.main()
