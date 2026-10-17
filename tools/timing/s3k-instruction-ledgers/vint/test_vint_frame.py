#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17; R12 credits job).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM (S3&K locked-on ROM), GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) and KOS46_NATIVE_ROOT
#   (retained native ending captures, comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Whole-VInt composition test. The native per-PC histogram over every routine the composed ledger may execute must
equal the composed counts in all 42 retained ending buckets (comparison only; the semantic state per bucket is the
same test-oracle state the component tests use). A nonempty Nemesis queue must leave the frame open."""
import collections
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import vint_frame as vf  # noqa: E402
import test_vint8_cost as tv  # noqa: E402
sys.path.insert(0, os.path.join(HERE, 'drawlevel'))
import test_drawlevel_cost as td  # noqa: E402

RANGES = [(0xFFFFF0, 0xFFFFF6)] + vf.vc.CLOSED + [vf.dl.RANGE] + list(vf.nc.RANGES) + [(0xDD06, 0xDDCE)]
HUD_ENDING = dict(update_timer=1, timer=(0, 2, 10, 17))


def covered(pc):
    return any(a <= pc < b for a, b in RANGES)


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX and tv.NATIVE, 'set S3K_ROM, GPGX_GIT and KOS46_NATIVE_ROOT')
class Frame(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, vf.vc.TOOL)
        import gpgx_cycles
        cls.table = gpgx_cycles.load_table(tv.GPGX)
        cls.ledger = vf.FrameLedger(cls.rom, tv.gpgx_cost(cls.rom), cls.table.__getitem__)

    def test_all_buckets_match_native_over_the_whole_call_tree(self):
        n = 0
        for case in tv.CASES:
            hist = collections.defaultdict(collections.Counter)
            with open(os.path.join(tv.NATIVE, case[0], 'frame-pc-histogram.csv')) as f:
                for r in csv.DictReader(f):
                    pc = int(r['pc'], 16)
                    if covered(pc):
                        hist[int(r['level_frame'])][pc] += int(r['count'])
            states = tv.oracle(*case)
            for lf, native in sorted(hist.items()):
                planes = vf.dl.Planes(td.FOUR if native.get(0x4E770) else ())
                out = self.ledger.run(states[lf][1], planes, vf.nc.Nem(), vf.hc.Hud(**HUD_ENDING))
                self.assertTrue(out['closed'], (case[0], lf))
                self.assertEqual(out['counts'], dict(native), (case[0], lf))
                self.assertTrue(all(covered(pc) for pc in out['counts']))
                n += 1
        self.assertEqual(n, 42)

    def test_nonempty_nemesis_leaves_the_frame_open(self):
        out = self.ledger.run(vf.vc.State(), vf.dl.Planes(), vf.nc.Nem(5, ((0x100000, 0x2000),)),
                              vf.hc.Hud(**HUD_ENDING))
        self.assertEqual(out['open_terms'], {'Nem_PCD_NewRow': 3})
        self.assertFalse(out['closed'])

    def test_ending_credits_job_closes_each_vint_exactly(self):
        import nem_decoder as nd
        jobs = nd.NemQueueModel.plc_list(self.rom, 0x5B1EC)               # PLC_SKCredits (sonic3k.asm 121546)
        micro = nd.NemQueueModel(self.rom, tv.gpgx_cost(self.rom), self.table.__getitem__)
        micro.load_plc_raw(jobs)
        base = self.ledger.run(vf.vc.State(), vf.dl.Planes(), vf.nc.Nem(), vf.hc.Hud(**HUD_ENDING))['cycles']
        done = 0
        while True:
            micro.main_loop_init()
            if micro.read('patterns_left') == 0:
                break
            step = micro.vint()
            out = self.ledger.run(vf.vc.State(), vf.dl.Planes(), vf.NemJob(jobs[0][0], done, tuple(jobs)),
                                  vf.hc.Hud(**HUD_ENDING))
            self.assertTrue(out['closed'])
            self.assertEqual(out['cycles'] - base, step.cycles - 38, done)    # empty path costs 38
            done += len(step.rows) // 8
        self.assertEqual(done, 65)

    def test_skipped_do_updates_runs_no_hud_or_nemesis(self):
        out = self.ledger.run(vf.vc.State(water_flag=True, h_int_counter=40), vf.dl.Planes(),
                              vf.nc.Nem(5, ((0x100000, 0x2000),)), vf.hc.Hud(**HUD_ENDING))
        self.assertTrue(out['closed'])                         # Do_Updates deferred to H-int: callees not entered
        self.assertNotIn(0x17F4, out['counts'])


if __name__ == '__main__':
    unittest.main()
