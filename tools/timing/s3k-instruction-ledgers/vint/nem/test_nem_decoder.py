#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R12 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d). Without them the tests SKIP;
#   a skip is not a pass. Runtime about 45 s.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Nemesis queue micro-execution tests.

NATIVE-ABSENT: no retained capture contains a nonempty Process_Nem_Queue_2 frame, so nothing here is a native
witness. Evidence classes:
  REAL ROM ART   the two Ending credits jobs (PLC_SKCredits, PLC_CreditsKnuxPose; Load_PLC_Raw at sonic3k.asm
                 121546/121894) and every art referenced from Offs_PLC, decoded by micro-executing the ROM and
                 compared byte-for-byte with the independent format decoder (nem_reference.decode).
  SYNTHETIC      encoded streams with a known pixel sequence (ground truth independent of both decoders) covering
                 plain and XOR writers, inline escapes, both refill branches, runs carried across rows, patterns
                 and the 3-pattern VInt boundary, and back-to-back queue entries.
  MUTATIONS      must turn the comparisons red.
GPGX and Motorola costs come from independent tables and must agree on every VInt.
"""
import os
import random
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..'))
import nem_decoder as nd  # noqa: E402
import nem_reference as ref  # noqa: E402
import nem_cost as nc  # noqa: E402
import nem_event_cost as ne  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

ROM_PATH, GPGX = os.environ.get('S3K_ROM'), os.environ.get('GPGX_GIT')
ENDING = {0x5B1EC: ('PLC_SKCredits', 65, 22), 0x5B50C: ('PLC_CreditsKnuxPose', 134, 45)}
TABLE = {(1, 8): '0', (2, 3): '10', (3, 1): '110', (4, 2): '1110', (5, 5): '11110'}   # prefix-free, no 111111


def synthetic(seed, patterns, xor):
    """Random (palette, run) symbols filling `patterns`; pairs absent from TABLE are emitted inline."""
    rng = random.Random(seed)
    syms, left = [], 64 * patterns
    while left:
        pal, run = rng.choice([(1, 8), (2, 3), (3, 1), (4, 2), (5, 5), (6, 7), (7, 8), (9, 1), (0xF, 4)])
        run = min(run, left)
        syms.append((pal, run))
        left -= run
    return syms


@unittest.skipUnless(ROM_PATH and GPGX, 'set S3K_ROM and GPGX_GIT')
class NemDecoder(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.os.path.dirname(tv.__file__))
        import gpgx_cycles
        table = gpgx_cycles.load_table(GPGX)
        cls.table = table
        cls.gcost = staticmethod(tv.gpgx_cost(cls.rom))
        import m68k_timing
        cls.event = ne.NemEventCost(cls.rom, tv.gpgx_cost(cls.rom), m68k_timing.Decoder(cls.rom), table.__getitem__)
        cls.decoder_cls = m68k_timing.Decoder

    def model(self, rom=None, motorola=False):
        rom = rom or self.rom
        if motorola:
            return nd.NemQueueModel(rom, tv.motorola_cost)
        return nd.NemQueueModel(rom, tv.gpgx_cost(rom) if rom is not self.rom else self.gcost, self.table.__getitem__)

    def run_job(self, jobs, rom=None, motorola=False, between=None):
        m = self.model(rom, motorola)
        m.load_plc_raw(jobs)
        out = []
        for k in range(100000):
            m.main_loop_init()
            if m.read('patterns_left') == 0:
                return m, out
            out.append(m.vint())
            if between:
                between(m, k)
        raise RuntimeError

    def with_art(self, art):
        base = (len(self.rom) + 0xFF) & ~0xFF
        return self.rom + bytes(base - len(self.rom)) + art, base

    # -- real ROM art
    def test_ending_credit_jobs(self):
        for plc, (name, patterns, vints) in ENDING.items():
            jobs = nd.NemQueueModel.plc_list(self.rom, plc)
            m, steps = self.run_job(jobs)
            moto = self.run_job(jobs, motorola=True)[1]
            self.assertEqual([r for s in steps for r in s.rows], ref.decode(self.rom, jobs[0][0]), name)
            self.assertEqual((len(steps), sum(len(s.rows) for s in steps)), (vints, 8 * patterns), name)
            self.assertEqual([s.cycles for s in steps], [s.cycles for s in moto], name)
            fixed = nc.NemLedger(self.rom, self.gcost)
            for k, s in enumerate(steps):
                left = patterns - 3 * k
                self.assertEqual(len(s.rows), 8 * min(3, left))
                # independent fixed-part ledger (Nem_PCD_NewRow open) must equal cycles outside the decoder
                self.assertEqual(s.cycles - s.decoder_cycles, fixed.run(nc.Nem(left, jobs))[0], (name, k))
            self.assertTrue(any(s.state.repeat_count & 0xFFFF for s in steps[:-1]), name)   # real carried runs
            self.assertEqual(m.cpu.read(m.queue, 'l'), 0)                                     # queue shifted up

    def test_every_offs_plc_art_decodes_equal(self):
        offs = 0x9238C
        first = (self.rom[offs] << 8) | self.rom[offs + 1]
        arts = set()
        for k in range(first // 2):
            plc = offs + ((self.rom[offs + 2 * k] << 8) | self.rom[offs + 2 * k + 1])
            arts.update(a for a, _ in nd.NemQueueModel.plc_list(self.rom, plc))
        modes = {True: 0, False: 0}
        for art in sorted(arts):
            hdr = (self.rom[art] << 8) | self.rom[art + 1]
            if hdr & 0x7FFF == 0:
                continue
            m, steps = self.run_job([(art, 0x2000)])
            self.assertEqual([r for s in steps for r in s.rows], ref.decode(self.rom, art), hex(art))
            self.check_event_model(self.rom, self.event, art, steps)
            modes[bool(hdr & 0x8000)] += 1
        self.assertGreater(modes[True], 0)
        self.assertGreater(modes[False], 0)
        print('\nOffs_PLC arts decoded equal: %d plain, %d XOR' % (modes[False], modes[True]))

    def check_event_model(self, rom, event, art, steps):
        model = list(event.job(art))
        self.assertEqual([c for c, _, _ in model], [s.decoder_cycles for s in steps], hex(art))
        self.assertEqual([r for _, _, rs in model for r in rs], [r for s in steps for r in s.rows], hex(art))
        # the final VInt takes ShiftUp and returns without the $1854-$186C save, so its RAM vars are stale (ROM)
        for (_, st, _), s in zip(model[:-1], steps[:-1]):
            self.assertEqual((st['d0'] & 0xFFFF, st['d6']), (s.state.repeat_count & 0xFFFF, s.state.shift_value))
        init = nd.NemQueueModel(rom, tv.gpgx_cost(rom), self.table.__getitem__)
        init.load_plc_raw([(art, 0x2000)])
        self.assertEqual(init.main_loop_init()[0], event.init_cycles(art)[0], hex(art))

    def test_event_model_synthetic_and_motorola(self):
        for xor in (False, True):
            syms = synthetic(11 + xor, 8, xor)
            rom, base = self.with_art(ref.encode(syms, TABLE, xor))
            steps = self.run_job([(base, 0x2000)], rom=rom)[1]
            event = ne.NemEventCost(rom, tv.gpgx_cost(rom), self.decoder_cls(rom), self.table.__getitem__)
            self.check_event_model(rom, event, base, steps)
            moto = ne.NemEventCost(rom, tv.motorola_cost, self.decoder_cls(rom))
            self.assertEqual([c for c, _, _ in moto.job(base)], [s.decoder_cycles for s in steps])
        # mutation: forgetting the register-shift count must turn the model red
        flat = ne.NemEventCost(self.rom, tv.gpgx_cost(self.rom), self.decoder_cls(self.rom), self.table.__getitem__)
        real_fetch = flat._fetch
        def no_shift(st, codes):
            d6 = st['d6']
            c = real_fetch(st, codes)
            return c - 2 * (d6 - 8)
        flat._fetch = no_shift
        art = nd.NemQueueModel.plc_list(self.rom, 0x5B1EC)[0][0]
        self.assertNotEqual([c for c, _, _ in flat.job(art)], [c for c, _, _ in self.event.job(art)])

    # -- synthetic
    def test_synthetic_both_writers_inline_refill_and_carry(self):
        for xor in (False, True):
            syms = synthetic(7 + xor, 7, xor)
            art = ref.encode(syms, TABLE, xor)
            rom, base = self.with_art(art)
            m, steps = self.run_job([(base, 0x2000)], rom=rom)
            rows = [r for s in steps for r in s.rows]
            self.assertEqual(rows, ref.pixels_to_rows(syms, xor))
            self.assertEqual(rows, ref.decode(rom, base))
            counts = {}
            for s in steps:
                for pc, k in s.counts.items():
                    counts[pc] = counts.get(pc, 0) + k
            self.assertGreater(counts.get(0x1650, 0), 0)                 # Nem_PCD_InlineData
            self.assertGreater(counts.get(0x1626, 0), 0)                 # table-code refill
            self.assertGreater(counts.get(0x1658, 0) + counts.get(0x1674, 0), 0)   # inline refills
            self.assertEqual(0x1686 in counts, xor)                       # XOR writer only in XOR mode
            self.assertEqual([len(s.rows) // 8 for s in steps], [3, 3, 1])

    def test_run_carried_across_the_vint_boundary(self):
        syms = [(1, 8)] * 23 + [(2, 3), (4, 2), (5, 5)]
        syms += [(3, 1)] * (64 * 5 - sum(r for _, r in syms))
        # pixels 0..183, (2,3) 184-186, (4,2) 187-188, (5,5) 189-193: three pixels before the pattern 3 -> 4 (VInt)
        # boundary at pixel 192, two owed after it
        art = ref.encode(syms, TABLE)
        rom, base = self.with_art(art)
        _, steps = self.run_job([(base, 0x2000)], rom=rom)
        self.assertEqual(steps[0].state.repeat_count & 0xFFFF, 2)        # 2 pixels of the (5,5) run still owed
        self.assertEqual([r for s in steps for r in s.rows], ref.pixels_to_rows(syms))

    def test_back_to_back_entries_start_on_the_next_frame(self):
        a1 = ref.encode(synthetic(1, 4, False), TABLE)
        a2 = ref.encode(synthetic(2, 2, True), TABLE, xor=True)
        rom, base = self.with_art(a1 + a2)
        m, steps = self.run_job([(base, 0x2000), (base + len(a1), 0x3000)], rom=rom)
        self.assertEqual([len(s.rows) // 8 for s in steps], [3, 1, 2])  # entry 1 ends without topping up
        self.assertEqual([r for s in steps for r in s.rows], ref.decode(rom, base) + ref.decode(rom, base + len(a1)))

    # -- mutation reds
    def test_mutations_turn_red(self):
        jobs = nd.NemQueueModel.plc_list(self.rom, 0x5B1EC)
        art = jobs[0][0]
        want = ref.decode(self.rom, art)
        bad = bytearray(self.rom); bad[art + 0x60] ^= 0x10
        rows = [r for s in self.run_job(jobs, rom=bytes(bad))[1] for r in s.rows]
        self.assertNotEqual(rows, want)                                          # art bit flip seen only by ROM run

        def drop_carry(m, k):
            if m.read('repeat_count') & 0xFFFF:
                m.cpu.write(m.addr['repeat_count'], 'l', 0)
        rows = [r for s in self.run_job(jobs, between=drop_carry)[1] for r in s.rows]
        self.assertNotEqual(rows, want)                                          # lost carry state

        def plain_writer(m, k):
            m.cpu.write(m.addr['write_routine'], 'l', 0x167C)
        rows = [r for s in self.run_job(jobs, between=plain_writer)[1] for r in s.rows]
        self.assertNotEqual(rows, want)                                          # XOR writer replaced

        flat = nd.NemQueueModel(self.rom, self.gcost, self.table.__getitem__)
        flat.charge = lambda pc, outcome, shift: nd.NemQueueModel.charge(flat, pc, outcome, None if shift is None else 0)
        flat.load_plc_raw(jobs); flat.main_loop_init()
        good = self.model(); good.load_plc_raw(jobs); good.main_loop_init()
        self.assertNotEqual(flat.vint().cycles, good.vint().cycles)              # register-shift count ignored

    def test_fail_closed(self):
        m = self.model()
        with self.assertRaises(ValueError):
            m.load_plc_raw([(0x100000, 0)] * 16)                                  # retained 16th slot
        zero = bytearray(self.rom); zero[0x400000 - 2:0x400000] = b'\x00\x00'
        z = self.model(bytes(zero) + b'\x80\x00\xFF' + bytes(8))
        z.load_plc_raw([(0x400000, 0x2000)])
        with self.assertRaises(ValueError):
            z.main_loop_init()                                                    # zero-pattern header
        bad = bytearray(self.rom); bad[0x1630:0x1632] = b'\x4E\x72'              # STOP in the decoder path
        bm = self.model(bytes(bad))
        bm.load_plc_raw(nd.NemQueueModel.plc_list(self.rom, 0x5B1EC)); bm.main_loop_init()
        with self.assertRaises(Exception):
            bm.vint()
        pinned = bytearray(self.rom); pinned[0x164A] ^= 1
        with self.assertRaises(ValueError):
            nd.NemQueueModel(bytes(pinned), tv.motorola_cost)


if __name__ == '__main__':
    unittest.main()
