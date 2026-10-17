#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R6 (slice 1, 2026-10-16).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Optional: KOS46_CLOSURE_TOOL overrides the ../../m68k-closure decoder directory.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Tests for kos_service.py.

Synthetic vectors: hand-assembled Kosinski streams with outputs written out here (format: 16-bit little-endian
descriptor read LSB first; 1 = literal; 00xx = inline copy of xx+2 bytes from offset -256..-1; 01 = separate copy with
13-bit offset and 3-bit count c+2, or c=0 then a count byte n: n=0 end, n=1 no-op, else n+1 bytes). They run on a
synthetic ROM that carries the shipped routine bytes copied from the original ROM (pins enforce identity).
Native comparison (ROM-backed; skipped without the ROM path): per-window per-PC execution counts from the v6 native
probe are the reference, and the window budget is those counts costed with GPGX 051d430d; the model must stop at the
same boundary, emit the same bytes and execute the same per-PC counts. Native data is test input only."""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
TOOL = os.environ.get('KOS46_CLOSURE_TOOL', os.path.join(HERE, '..', '..', 'm68k-closure'))
sys.path.insert(0, TOOL)
import kos_service as ks  # noqa: E402
import m68k_timing  # noqa: E402

ROM_PATH = os.environ.get('S3K_ROM')
GPGX = os.environ.get('GPGX_GIT')
NATIVE = os.environ.get('KOS46_NATIVE_ROOT')
ROUTINE = (0x1C1A, 0x1D0C)
NATIVE_SUMMARY = []


def motorola_cost_of(rom):
    dec = m68k_timing.Decoder(rom)

    def cost(pc, outcome):
        i = dec.decode(pc)
        if i.kind in ('cond', 'dbcc'):
            c = i.taken if outcome == 'taken' else i.fall
            if i.kind == 'dbcc' and outcome == 'fall':
                return c.hi                      # dbf fall-through is always a count expiry
            return c.lo
        if i.cost.lo != i.cost.hi:
            raise ks.Unmodelled('operand-dependent cost at %06X' % pc)
        return i.cost.lo
    return cost


def gpgx_cost_of(rom, repo):
    import gpgx_cycles
    table = gpgx_cycles.load_table(repo)
    dec = m68k_timing.Decoder(rom)
    word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731

    def cost(pc, outcome):
        i = dec.decode(pc)
        k = gpgx_cycles.cost(table, i, word(pc), word)
        if isinstance(k, dict):
            lo, hi = k['taken'] if outcome == 'taken' else k['fall']
            if i.name == 'dbf' or lo == hi:
                return hi
            raise ks.Unmodelled('ambiguous fall cost at %06X' % pc)
        if k[0] != k[1]:
            raise ks.Unmodelled('operand-dependent cost at %06X' % pc)
        return k[0]
    return cost


def synthetic_rom(stream, at=0x2000):
    rom = bytearray(0x4000)
    with open(ROM_PATH, 'rb') as f:
        real = f.read()
    rom[ROUTINE[0]:ROUTINE[1]] = real[ROUTINE[0]:ROUTINE[1]]
    rom[at:at + len(stream)] = stream
    return bytes(rom)


def run_all(rom, cost, src, dst=0xFFFF0000, budget=10 ** 9):
    svc = ks.KosService(rom, cost)
    s = ks.State()
    svc.queue(s, [(src, dst)])
    r = svc.serve(s, budget)
    return svc, s, r


def output(s, dst, n):
    return bytes(s.ram[dst & 0xFFFF:(dst & 0xFFFF) + n])


def bits(*flags):
    """Pack descriptor flags (first flag = bit 0) into one little-endian word."""
    v = sum(1 << k for k, f in enumerate(flags) if f)
    return bytes([v & 0xFF, v >> 8])


END = bytes([0x00, 0xF0, 0x00])                 # separate copy, count bits 0, count byte 0 = end marker


@unittest.skipUnless(ROM_PATH and os.path.exists(ROM_PATH), 'set S3K_ROM to the original locked-on ROM')
class SyntheticStreams(unittest.TestCase):
    def check(self, stream, expected, budget=10 ** 9):
        rom = synthetic_rom(stream)
        svc, s, r = run_all(rom, motorola_cost_of(rom), 0x2000, budget=budget)
        self.assertEqual(r['outcome'], 'returned')
        self.assertEqual(output(s, 0xFFFF0000, len(expected) + 4), expected + bytes(4))
        self.assertEqual(r['bytes'], len(expected))
        return svc, s, r

    def test_literals_then_end(self):
        # flags: 1,1,1 literals; 0,1 separate copy -> bytes 00 F0 = offset field, count 0 -> byte 00 = end
        self.check(bits(1, 1, 1, 0, 1) + b'ABC' + bytes([0x00, 0xF0, 0x00]), b'ABC')

    def test_inline_copy_two_bit_count(self):
        # 1,1 literals 'AB'; 0,0 inline, count bits 1,0 -> roxl gives c=2, +1 -> dbf runs 4 bytes; offset FE (-2)
        stream = bits(1, 1, 0, 0, 1, 0, 0, 1) + b'AB' + bytes([0xFE]) + bytes([0x00, 0xF0, 0x00])
        self.check(stream, b'AB' + b'ABAB')

    def test_separate_copy_three_bit_count(self):
        # 1,1,1 literals 'XYZ'; 0,1 separate: d0=FD, d1=F8|2 -> offset ((F8 << 5) | FD) = -3 (13 bits), count 2+1 -> 4
        stream = bits(1, 1, 1, 0, 1, 0, 1) + b'XYZ' + bytes([0xFD, 0xFA]) + bytes([0x00, 0xF0, 0x00])
        self.check(stream, b'XYZ' + b'XYZX')

    def test_extended_count_and_noop(self):
        # literal 'Q'; separate copy offset -1 with count byte 4 -> 5 bytes; then no-op (count byte 1); then end
        stream = bits(1, 0, 1, 0, 1, 0, 1) + b'Q' + bytes([0xFF, 0xF8, 0x04]) + bytes([0x00, 0xF0, 0x01]) + \
            bytes([0x00, 0xF0, 0x00])
        self.check(stream, b'Q' + b'QQQQQ')

    def test_descriptor_refill_after_sixteen_flags(self):
        # Loop: lsr, then dbf d4 expires on the 16th flag and the next descriptor is read before that flag's byte,
        # so the second descriptor sits after the 15th literal. 17 literals, then end.
        stream = bits(*([1] * 16)) + b'ABCDEFGHIJKLMNO' + bits(1, 0, 1) + b'P' + b'Q' + END
        self.check(stream, b'ABCDEFGHIJKLMNOPQ')

    def test_capture_restore_mid_command_and_two_cycle_replay(self):
        stream = bits(*([1] * 16)) + b'ABCDEFGHIJKLMNO' + bits(1, 0, 1, 0, 1) + b'P' + b'R' + \
            bytes([0xFF, 0xF8, 0x09]) + END
        expected = b'ABCDEFGHIJKLMNOPR' + b'R' * 10
        rom = synthetic_rom(stream)
        cost = motorola_cost_of(rom)
        _, whole, r_whole = run_all(rom, cost, 0x2000)
        total = r_whole['cycles']
        for split in range(40, total - 40, 37):
            svc = ks.KosService(rom, cost)
            s = ks.State()
            svc.queue(s, [(0x2000, 0xFFFF0000)])
            r1 = svc.serve(s, split)
            if r1['outcome'] != 'bookmarked':
                continue
            snap = s.snapshot()
            r2 = svc.serve(s, 10 ** 9)
            again = snap.snapshot()
            r3 = svc.serve(again, 10 ** 9)                 # replay from the same captured state
            self.assertEqual((r2['cycles'], r2['bytes']), (r3['cycles'], r3['bytes']))
            self.assertEqual(output(s, 0xFFFF0000, len(expected)), expected)
            self.assertEqual(output(again, 0xFFFF0000, len(expected)), expected)
            self.assertEqual(r1['bytes'] + r2['bytes'], len(expected))
            # resumed window pays Process_Kos_Queue entry + Restore_Kos_Bookmark once more than the whole run
            self.assertGreater(r1['cycles'] + r2['cycles'], total)

    def test_one_retirement_per_window_no_successor(self):
        stream = bits(1, 0, 1) + b'A' + bytes([0x00, 0xF0, 0x00])
        rom = synthetic_rom(stream + bytes(8))
        svc = ks.KosService(rom, motorola_cost_of(rom))
        s = ks.State()
        svc.queue(s, [(0x2000, 0xFFFF0000), (0x2000, 0xFFFF1000)])
        r = svc.serve(s, 10 ** 9)
        self.assertEqual((r['outcome'], r['bytes']), ('returned', 1))
        self.assertEqual(s.ram[0x1000], 0)                 # successor did not start in this window
        self.assertEqual(svc.rw(s, 0xFFFF0E), 1)
        r = svc.serve(s, 10 ** 9)
        self.assertEqual(s.ram[0x1000], ord('A'))

    def test_exact_exhaustion_boundary(self):
        stream = bits(*([1] * 8), 0, 1) + b'ABCDEFGH' + bytes([0x00, 0xF0, 0x00])
        rom = synthetic_rom(stream)
        cost = motorola_cost_of(rom)
        _, _, whole = run_all(rom, cost, 0x2000)
        for budget in range(60, 200):
            svc = ks.KosService(rom, cost)
            s = ks.State()
            svc.queue(s, [(0x2000, 0xFFFF0000)])
            r = svc.serve(s, budget)
            if r['outcome'] == 'bookmarked':
                # the VInt is taken at the first instruction boundary at or after the budget
                self.assertGreaterEqual(r['cycles'], budget)
                self.assertLess(r['cycles'] - r['last_cost'], budget)
            else:
                self.assertGreaterEqual(budget, whole['cycles'] - 30)


def native_windows(case_dir):
    counts = collections.defaultdict(collections.Counter)
    with open(os.path.join(case_dir, 'kos-pc-counts.csv')) as f:
        for r in csv.DictReader(f):
            counts[int(r['window_level_frame'])][int(r['pc'], 16)] += int(r['count'])
    return counts


@unittest.skipUnless(ROM_PATH and GPGX and NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class NativeWindows(unittest.TestCase):
    """Replay job $1D17A6 window by window against native v6 Tails/+2 and Sonic/+2 NTSC counts."""

    # FIFO at LF 4402 on every D01 product (native windows.csv: count 3, jobs in this order)
    FIFO = [(0x1D17A6, 0xFFFF0000), (0x1CF2D4, 0xFFFF9000), (0x1CFB46, 0xFFFFD000)]

    def replay(self, case, analysis='native-kos-budget-v6-analysis', frames=range(4402, 4413)):
        job = self.FIFO[0]
        with open(ROM_PATH, 'rb') as f:
            rom = f.read()
        cost = gpgx_cost_of(rom, GPGX)
        native = native_windows(os.path.join(NATIVE, case))
        with open(os.path.join(NATIVE, analysis, 'windows.csv')) as f:
            meta = {int(r['level_frame']): r for r in csv.DictReader(f) if r['case'] == case}
        svc = ks.KosService(rom, cost)
        s = ks.State()
        svc.queue(s, self.FIFO)
        results = []
        resume = None
        for lf in frames:
            m = meta[lf]
            if m['job_source'] != '%08X' % job[0] or (m['retired'] != 'True' and not m['interrupted_pc']):
                break
            pre = {pc: c for pc, c in native[lf].items() if ROUTINE[0] <= pc < ROUTINE[1]}
            if m['retired'] == 'True':
                pre[0x1CD2] = 1          # the v6 probe hooks $1CD2 and does not count it (analyze_kos_budget_v6.py)
            stop = int(m['interrupted_pc'], 16) if m['interrupted_pc'] else None
            # cost the native counts: branch outcomes from single-predecessor fall-through counts; the final arrival at
            # the interrupted PC is not counted natively, so a branch whose fall-through is that PC gains one fall
            # likewise the resume PC's native count includes the RTE landing from Restore_Kos_Bookmark, which is not a
            # fall-through of the branch before it
            budget = sum(self.cost_native(svc, cost, pre, pc, stop, resume) for pc in pre)
            resume = stop
            r = svc.serve(s, budget)
            results.append((lf, r['outcome'], r['bytes'], r['pc'], r['cycles'], budget))
            self.assertEqual(r['bytes'], int(m['bytes_out']), (case, lf))
            self.assertEqual(r['cycles'], budget, (case, lf))
            if m['retired'] == 'True':
                self.assertEqual((r['outcome'], r['retired']), ('returned', True))
                done = len([x for x in results if x[1] == 'returned'])
                if done == len(self.FIFO):
                    break                                  # later windows serve module children queued afterwards
                job = self.FIFO[done]
            else:
                self.assertEqual((r['outcome'], r['pc']), ('bookmarked', int(m['interrupted_pc'], 16)), (case, lf))
            self.assertEqual({pc: c for pc, c in r['counts'].items()}, pre, (case, lf))
        return results

    @staticmethod
    def cost_native(svc, cost, pre, pc, stop=None, resume=None):
        c = pre[pc]
        dec = m68k_timing.Decoder(svc.rom)
        i = dec.decode(pc)
        if i.kind in ('cond', 'dbcc'):
            fall = pre.get(pc + i.length, 0) + (1 if stop == pc + i.length else 0) - \
                (1 if resume == pc + i.length else 0)
            return fall * cost(pc, 'fall') + (c - fall) * cost(pc, 'taken')
        if pc in (0x1C5A, 0x1C9E, 0x1CC0, 0x1CD0):      # bra.s: unconditional
            return c * cost(pc, 'seq')
        return c * cost(pc, 'seq')

    def test_tails_plus2_ntsc(self):
        self.results = self.replay('native-kos-budget-tails-super-320-v6')

    def test_sonic_plus2_ntsc(self):
        self.results = self.replay('native-kos-budget-sonic-super-320-v6')

    def test_tails_plus2_pal(self):
        self.results = self.replay('native-kos-budget-tails-super-pal-320-v7p', 'native-kos-budget-v7p-pal-analysis')

    def test_sonic_plus2_pal(self):
        self.results = self.replay('native-kos-budget-sonic-super-pal-320-v7p', 'native-kos-budget-v7p-pal-analysis')

    def test_sonic_zero_ntsc(self):
        self.results = self.replay('native-kos-budget-sonic-chaos-ntsc-320-v7c', 'native-kos-budget-v7c-nonhyper-analysis',
                                   range(3713, 3730))

    def test_sonic_minus2_ntsc(self):
        self.results = self.replay('native-kos-budget-sonic-none-ntsc-320-v7c', 'native-kos-budget-v7c-nonhyper-analysis',
                                   range(3302, 3320))

    def tearDown(self):
        r = getattr(self, 'results', None)
        if r:
            NATIVE_SUMMARY.append((self.id().split('.')[-1], [(lf, o[0], b) for lf, o, b, *_ in r]))


if __name__ == '__main__':
    try:
        unittest.main()
    finally:
        for name, rows in NATIVE_SUMMARY:
            print(name, len(rows), 'windows', rows, file=sys.stderr)
