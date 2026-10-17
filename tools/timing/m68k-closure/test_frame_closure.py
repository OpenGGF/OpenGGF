#!/usr/bin/env python3
"""Independent tests for the 68000 frame-closure tool.

Decoder vectors are hand-assembled opcodes with Motorola MC68000 UM section 8 times written out here. CLI tests build a
synthetic ROM and capture whose cycle totals are computed in this file from the stated rules (not by the tool), then
assert PASS / FAIL / INCOMPLETE / ERROR exit codes. Run: python3 -m unittest discover -s tools/timing/m68k-closure -p 'test_*.py'
"""
import collections
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import m68k_timing  # noqa: E402

TOOL = os.path.join(HERE, 'frame_closure.py')


def write(path, data):
    with open(path, 'wb' if isinstance(data, (bytes, bytearray)) else 'w') as f:
        f.write(data)


def digest(path, algo):
    with open(path, 'rb') as f:
        return hashlib.new(algo, f.read()).hexdigest()
NTSC_PERIOD_MCLK = 262 * 3420


def decoder(words):
    b = bytearray(0x100)
    for k, w in enumerate(words):
        b[2 * k], b[2 * k + 1] = w >> 8, w & 0xFF
    return m68k_timing.Decoder(bytes(b))


class DecoderVectors(unittest.TestCase):
    VECTORS = [
        ([0x4A38, 0xF62A], 'tst', 4, (12, 12)),             # tst.b (abs).w: 4 + 8
        ([0x303C, 0x0700], 'move', 4, (8, 8)),              # move.w #imm,d0
        ([0x22C0], 'move', 2, (12, 12)),                    # move.l d0,(a1)+
        ([0x3368, 0x0010, 0x0020], 'move', 6, (20, 20)),    # move.w d16(a0),d16(a1)
        ([0x48E7, 0xFFFE], 'movem', 4, (128, 128)),         # movem.l d0-a6,-(sp): 8 + 8*15
        ([0x4CDF, 0x7FFF], 'movem', 4, (132, 132)),         # movem.l (sp)+,d0-a6: 12 + 8*15
        ([0xE94E], 'lsl', 2, (14, 14)),                     # lsl.w #4,d6: 6 + 2*4
        ([0xE3A8], 'lsl', 2, (8, 134)),                     # lsl.l d1,d0: 8 + 2*(0..63), operand-dependent
        ([0x0839, 0x0000, 0x00A1, 0x1100], 'btst', 8, (20, 20)),  # btst #0,(abs).l: 8 + 12
        ([0x08C0, 0x0003], 'bset', 4, (10, 10)),            # bset #3,d0
        ([0x08C0, 0x0013], 'bset', 4, (12, 12)),            # bset #19,d0
        ([0xC0C1], 'mulu', 2, (38, 70)),
        ([0x41FA, 0x0010], 'lea', 4, (8, 8)),
        ([0x4EB9, 0x0000, 0x1234], 'jsr', 6, (20, 20)),
        ([0x4E75], 'rts', 2, (16, 16)),
        ([0x0C80, 0x0000, 0x0001], 'cmpi', 6, (14, 14)),
        ([0x5279, 0x00FF, 0xFE04], 'addq', 6, (20, 20)),    # addq.w #1,(abs).l: 8 + 12
        ([0xC141], 'exg', 2, (6, 6)),
    ]

    def test_vectors(self):
        for words, name, length, cyc in self.VECTORS:
            i = decoder(words).decode(0)
            self.assertEqual((i.name, i.length, (i.cost.lo, i.cost.hi)), (name, length, cyc), [hex(w) for w in words])

    def test_branch_costs(self):
        b = decoder([0x66FA]).decode(0)
        self.assertEqual((b.kind, b.taken.lo, b.fall.lo), ('cond', 10, 8))
        b = decoder([0x6700, 0x0010]).decode(0)
        self.assertEqual((b.taken.lo, b.fall.lo, b.target), (10, 12, 0x12))
        b = decoder([0x51C8, 0xFFFE]).decode(0)
        self.assertEqual((b.kind, b.taken.lo, b.fall.lo), ('dbcc', 10, 14))

    def test_unsupported_is_explicit(self):
        with self.assertRaises(m68k_timing.Unsupported):
            decoder([0xA000]).decode(0)


def gpgx_refresh_count(steps):
    """Independent oracle: GPGX m68k_run refresh rule over an explicit step list starting at refresh phase 0."""
    t, nxt, n = 0, 0, 0
    for d in steps:
        if t >= nxt:
            nxt, t, n = t + 128, t + 2, n + 1
        t += d
    return n


class ClosureCli(unittest.TestCase):
    ITERS = 5700

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        d = self.tmp.name
        rom = bytearray(0x100)
        for pc, words in ((0x00, [0x4A38, 0xF62A]), (0x04, [0x66FA]), (0x06, [0x4E75]), (0x10, [0x4E73]),
                          (0x30, [0xA000])):
            for k, w in enumerate(words):
                rom[pc + 2 * k], rom[pc + 2 * k + 1] = w >> 8, w & 0xFF
        self.rom = os.path.join(d, 'rom.bin')
        write(self.rom, rom)
        self.captures = os.path.join(d, 'captures')
        os.makedirs(os.path.join(self.captures, 'synthetic'))
        # per bucket: n idle iterations (last bne falls through), rts, one interrupt entry (44) and rte
        self.instr = 22 * self.ITERS - 2 + 16 + 20          # tst 12 + bne 10 (last 8), rts 16, rte 20
        steps = [12, 10] * self.ITERS
        steps[-1] = 8
        steps += [16, 44, 20]
        self.refresh = gpgx_refresh_count(steps)
        self.z80 = NTSC_PERIOD_MCLK - 7 * (self.instr + 44 + 2 * self.refresh)
        self.assertGreater(self.z80, 0)

    def tearDown(self):
        self.tmp.cleanup()

    def write_case(self, extra_pc=None):
        cdir = os.path.join(self.captures, 'synthetic')
        rows = []
        for lf in (1, 2):
            counts = {0x00: self.ITERS, 0x04: self.ITERS, 0x06: 1, 0x10: 1}
            if extra_pc is not None and lf == 2:
                counts[extra_pc] = 1
            rows += ['%d,%06X,%d' % (lf, pc, c) for pc, c in sorted(counts.items())]
        write(os.path.join(cdir, 'frame-pc-histogram.csv'), 'level_frame,pc,count\n' + '\n'.join(rows) + '\n')
        write(os.path.join(cdir, 'frame-dma.csv'),
              'level_frame,vint,entries,total_words,entries_hex\n1,1,0,0,\n2,2,0,0,\n')

    def write_spec(self, rom_sha1=None):
        cdir = os.path.join(self.captures, 'synthetic')
        h = lambda p: digest(p, 'sha256')  # noqa: E731
        spec = {'schema': 'openggf.m68k-frame-closure-spec.v1', 'cost_model': {'kind': 'motorola'},
                'rom_sha1': rom_sha1 or digest(self.rom, 'sha1'),
                'counters_schema': 'openggf.m68k-stall-counters.v1', 'tolerance_cycles': 12,
                'platform': {'vint_entry_pc': '000010', 'cited_instruction_cycles': {},
                             'idle_loop': {'pcs': ['000000', '000004'], 'step_cycles': [12, 10]},
                             'bookmark_redirect_pc': '000020', 'vint_dma_words_and_rates': [], 'queue_dma_rate': 102},
                'cases': [{'name': 'synthetic', 'capture': 'synthetic', 'region': 'ntsc', 'windows': [[2, 2]],
                           'histogram_sha256': h(os.path.join(cdir, 'frame-pc-histogram.csv')),
                           'dma_sha256': h(os.path.join(cdir, 'frame-dma.csv'))}]}
        path = os.path.join(self.tmp.name, 'spec.json')
        write(path, json.dumps(spec))
        return path

    def write_counters(self, z80_delta=0, refresh=None, **extra):
        path = os.path.join(self.tmp.name, 'counters.jsonl')
        with open(path, 'w') as f:
            for lf in (1, 2):
                row = {'case': 'synthetic', 'level_frame': lf, 'z80_bus_mclk': self.z80 + z80_delta,
                       'fifo_wait_mclk': 0, 'dma_freeze_mclk': 0, 'vint_to_increment_mclk': 300,
                       'refresh_events': self.refresh if refresh is None else refresh}
                row.update(extra)
                f.write(json.dumps(row) + '\n')
        return path

    def test_observed_interrupt_phase_is_reported(self):
        # Motorola model bound is [44, 44]; an observed 44 must survive into the window's observed evidence
        self.write_case()
        r = self.run_tool('report', self.write_spec(), self.write_counters(vint_interrupt_cycles=44))
        w = json.loads(r.stdout)['cases']['synthetic']['windows']['2-2']
        self.assertEqual((w['status'], w['observed'].get('interrupt_cycles')), (0, 44))
        self.assertIn('refresh', w['observed'])

    def test_observed_interrupt_phase_outside_model_fails(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(), self.write_counters(vint_interrupt_cycles=50))
        self.assertEqual(r.returncode, 1, r.stdout)
        self.assertIn('observed interrupt cycles', r.stdout)

    def run_tool(self, command, spec, counters=None):
        argv = [sys.executable, '-I', TOOL, command, '--spec', spec, '--rom', self.rom,
                '--capture-root', self.captures]
        if counters:
            argv += ['--counters', counters]
        return subprocess.run(argv, capture_output=True, text=True)

    def test_positive_closes(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(), self.write_counters())
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertIn('STATUS: PASS', r.stdout)

    def test_overprediction_fails(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(), self.write_counters(z80_delta=7000))
        self.assertEqual(r.returncode, 1, r.stdout)
        self.assertIn('over-predicts', r.stdout)

    def test_unexplained_residual_fails(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(), self.write_counters(z80_delta=-7000))
        self.assertEqual(r.returncode, 1, r.stdout)
        self.assertIn('unexplained residual', r.stdout)

    def test_observed_refresh_outside_model_fails(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(), self.write_counters(refresh=1))
        self.assertEqual(r.returncode, 1, r.stdout)
        self.assertIn('observed refresh', r.stdout)

    def test_missing_counters_is_incomplete(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec())
        self.assertEqual(r.returncode, 2, r.stdout)
        self.assertIn('stall counters absent', r.stdout)

    def test_unsupported_instruction_is_incomplete(self):
        self.write_case(extra_pc=0x30)
        r = self.run_tool('check', self.write_spec(), self.write_counters())
        self.assertEqual(r.returncode, 2, r.stdout)
        self.assertIn('unsupported', r.stdout)

    def with_shift_before_rts(self):
        """Insert lsl.l d1,d0 (E3A8, 8 + 2n cycles, n = d1 mod 64 not observed) before the rts, moved to $08."""
        with open(self.rom, 'rb') as f:
            rom = bytearray(f.read())
        rom[6:10] = bytes([0xE3, 0xA8, 0x4E, 0x75])
        write(self.rom, rom)
        self.write_case(extra_pc=0x08)

    def test_operand_interval_wider_than_tolerance_is_incomplete(self):
        # Residual interval [-64, 62] overlaps the +-12 tolerance but is not inside it: some shift count
        # would close, so closure within tolerance is not proved (review finding against 4f7cbbb26).
        self.with_shift_before_rts()
        r = self.run_tool('check', self.write_spec(), self.write_counters(z80_delta=-72 * 7))
        self.assertEqual(r.returncode, 2, r.stdout)
        self.assertIn('wider than the tolerance', r.stdout)
        self.assertNotIn('STATUS: PASS', r.stdout)

    def test_operand_interval_excluding_tolerance_still_fails(self):
        # The same unresolved shift cannot close a residual whose whole interval lies above the tolerance.
        self.with_shift_before_rts()
        r = self.run_tool('check', self.write_spec(), self.write_counters(z80_delta=-300 * 7))
        self.assertEqual(r.returncode, 1, r.stdout)
        self.assertIn('unexplained residual', r.stdout)

    def test_identity_mismatch_is_error(self):
        self.write_case()
        r = self.run_tool('check', self.write_spec(rom_sha1='0' * 40), self.write_counters())
        self.assertEqual(r.returncode, 3, r.stdout)
        self.assertIn('STATUS: ERROR', r.stdout)

    def test_report_mode_does_not_judge(self):
        self.write_case()
        r = self.run_tool('report', self.write_spec())
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(json.loads(r.stdout)['cases']['synthetic']['windows']['2-2']['status'], 2)


if __name__ == '__main__':
    unittest.main()
