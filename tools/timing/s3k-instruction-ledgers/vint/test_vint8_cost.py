#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R9 (2026-10-17; R13 lag vectors).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Also the shared native-oracle loader imported by the other ledger tests.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Tests for vint8_cost.py. Native histograms and window analyses are TEST ORACLES only: they supply the semantic
state a future engine owner would hold (region, DMA entry count, Kos busy/interrupted PC) and the per-PC counts the
walk must reproduce exactly inside the closed routines. Negative perturbations must break the match."""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import vint8_cost as vc  # noqa: E402

ROM_PATH = os.environ.get('S3K_ROM')
GPGX = os.environ.get('GPGX_GIT')
NATIVE = os.environ.get('KOS46_NATIVE_ROOT')

CASES = [  # histogram capture, window analysis case, analysis dir, region
    ('native-kos-budget-tails-super-ntsc-320-v7', 'native-kos-budget-tails-super-320-v6', 'native-kos-budget-v6-analysis', False),
    ('native-kos-budget-sonic-super-ntsc-320-v7', 'native-kos-budget-sonic-super-320-v6', 'native-kos-budget-v6-analysis', False),
    ('native-kos-budget-tails-super-pal-320-v7p', 'native-kos-budget-tails-super-pal-320-v7p', 'native-kos-budget-v7p-pal-analysis', True),
    ('native-kos-budget-sonic-super-pal-320-v7p', 'native-kos-budget-sonic-super-pal-320-v7p', 'native-kos-budget-v7p-pal-analysis', True),
    ('native-kos-budget-sonic-chaos-ntsc-320-v7c', 'native-kos-budget-sonic-chaos-ntsc-320-v7c', 'native-kos-budget-v7c-nonhyper-analysis', False),
    ('native-kos-budget-sonic-none-ntsc-320-v7c', 'native-kos-budget-sonic-none-ntsc-320-v7c', 'native-kos-budget-v7c-nonhyper-analysis', False),
]


def gpgx_cost(rom):
    sys.path.insert(0, vc.TOOL)
    import gpgx_cycles
    table = gpgx_cycles.load_table(GPGX)
    word = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731

    def cost(ins, outcome):
        k = gpgx_cycles.cost(table, ins, word(ins.pc), word)
        if isinstance(k, dict):
            lo, hi = k['taken'] if outcome == 'taken' else k['fall']
            return hi if ins.name == 'dbf' else lo
        if k[0] != k[1]:
            raise ValueError('operand-dependent cost at %06X' % ins.pc)
        return k[0]
    return cost


def motorola_cost(ins, outcome):
    if ins.kind in ('cond', 'dbcc'):
        c = ins.taken if outcome == 'taken' else ins.fall
        return c.hi if ins.kind == 'dbcc' and outcome == 'fall' else c.lo
    if ins.cost.lo != ins.cost.hi:
        raise ValueError('operand-dependent cost at %06X' % ins.pc)
    return ins.cost.lo


def in_closed(pc):
    return pc == 0xFFFFF0 or any(a <= pc < b for a, b in vc.CLOSED)


def oracle(case, wcase, analysis, pal):
    """Per bucket: (native closed-region counts, semantic state built from comparison data)."""
    hist = collections.defaultdict(collections.Counter)
    with open(os.path.join(NATIVE, case, 'frame-pc-histogram.csv')) as f:
        for r in csv.DictReader(f):
            pc = int(r['pc'], 16)
            if in_closed(pc):
                hist[int(r['level_frame'])][pc] += int(r['count'])
    dma = {}
    with open(os.path.join(NATIVE, case, 'frame-dma.csv')) as f:
        for r in csv.DictReader(f):
            dma[int(r['level_frame'])] = int(r['entries'])
    win = {}
    with open(os.path.join(NATIVE, analysis, 'windows.csv')) as f:
        for r in csv.DictReader(f):
            if r['case'] == wcase:
                win[int(r['level_frame'])] = r
    out = {}
    for lf, counts in hist.items():
        w = win.get(lf)
        busy = bool(w and w['interrupted_pc'])
        out[lf] = (dict(counts), vc.State(pal=pal, dma_queue_entries=dma[lf], kos_busy=busy,
                                          kos_interrupted_pc=int(w['interrupted_pc'], 16) if busy else None))
    return out


@unittest.skipUnless(ROM_PATH and GPGX and NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class NativeOracle(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.ledger = vc.VIntLedger(cls.rom, gpgx_cost(cls.rom))
        cls.motorola = vc.VIntLedger(cls.rom, motorola_cost)

    def test_every_bucket_reproduces_native_closed_counts(self):
        n = 0
        for case in CASES:
            for lf, (native, state) in sorted(oracle(*case).items()):
                r = self.ledger.run(state)
                self.assertEqual(r.counts, native, (case[0], lf))
                self.assertEqual(r.bookmarked, state.kos_busy, (case[0], lf))
                self.assertEqual(r.open_terms, {'VInt_DrawLevel': 1, 'UpdateHUD': 1, 'Process_Nem_Queue_2': 1})
                n += 1
        self.assertGreaterEqual(n, 40)

    def test_region_term_is_the_pal_delay_loop(self):
        ntsc = self.ledger.run(vc.State())
        pal = self.ledger.run(vc.State(pal=True))
        # move.w #$700,d0 (8) + $700 taken dbf (10 each) + one expired (14) - the not-taken beq.s 8 + taken 10
        self.assertEqual(pal.cycles - ntsc.cycles, 8 + 0x700 * 10 + 14 + 8 - 10)

    def test_dma_entry_and_bookmark_terms(self):
        base = self.ledger.run(vc.State())
        one = self.ledger.run(vc.State(dma_queue_entries=1))
        # one entry: move.w (a1)+,d0 8 + beq.s fall 8 + 7 x move.w (a1)+/d0,(a5) (8, 12 x6) + cmpa.w # 10 + bne.s 10
        self.assertEqual(one.cycles - base.cycles, 8 + 8 + 8 + 6 * 12 + 10 + 10)
        bm = self.ledger.run(vc.State(kos_busy=True, kos_interrupted_pc=0x1C56))
        live = self.ledger.run(vc.State(kos_busy=True, kos_interrupted_pc=0x1D1C))
        self.assertTrue(bm.bookmarked and not live.bookmarked)
        self.assertGreater(bm.cycles, live.cycles)

    def test_negative_perturbations_break_the_match(self):
        native, state = oracle(*CASES[0])[4403]           # the tail-DPLC DMA frame
        for wrong in (vc.State(pal=True, dma_queue_entries=state.dma_queue_entries, kos_busy=state.kos_busy,
                               kos_interrupted_pc=state.kos_interrupted_pc),
                      vc.State(dma_queue_entries=state.dma_queue_entries + 1, kos_busy=state.kos_busy,
                               kos_interrupted_pc=state.kos_interrupted_pc),
                      vc.State(dma_queue_entries=state.dma_queue_entries, kos_busy=not state.kos_busy,
                               kos_interrupted_pc=state.kos_interrupted_pc or 0x1C56),
                      vc.State(hyper_flash_timer=1, dma_queue_entries=state.dma_queue_entries,
                               kos_busy=state.kos_busy, kos_interrupted_pc=state.kos_interrupted_pc)):
            self.assertNotEqual(self.ledger.run(wrong).counts, native)

    def test_gpgx_and_motorola_agree_on_this_path(self):
        for s in (vc.State(), vc.State(pal=True, dma_queue_entries=2), vc.State(kos_busy=True, kos_interrupted_pc=0x1CB6),
                  vc.State(hyper_flash_timer=3)):
            self.assertEqual(self.ledger.run(s).cycles, self.motorola.run(s).cycles, s)

    def test_unmodelled_inputs_fail_closed(self):
        for bad in (vc.State(vint_routine=0x40), vc.State(competition_mode=True), vc.State(dma_queue_entries=19)):
            with self.assertRaises(ValueError):
                self.ledger.run(bad)
        self.assertIn('SpecialVInt_04', self.ledger.run(vc.State(special_vint_routine=4)).open_terms)

    def test_rom_pins(self):
        bad = bytearray(self.rom)
        bad[0x5BC] ^= 1
        with self.assertRaises(ValueError):
            vc.VIntLedger(bytes(bad), motorola_cost)


if __name__ == '__main__':
    unittest.main()
