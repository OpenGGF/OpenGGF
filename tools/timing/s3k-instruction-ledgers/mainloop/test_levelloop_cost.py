#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d); KOS46_NATIVE_ROOT enables the native
#   comparison case (comparison only). Without them the guarded tests SKIP; a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""LevelLoop skeleton ledger tests (kos46 research R14).

NATIVE COMPARISON (comparison only, 42 retained ending buckets): per-PC counts over every range the ledger walks must
equal the bucket. Source-known inputs: Ending_running_flag set (Ending_ScreenInit), Demo_mode_flag 0, no water
(CheckLevelForWater), Restart_level_flag 0, Game_mode $0C, Teleport/Competition 0, Current_zone the Ending zone.
TEST-ORACLE inputs read from the bucket and labelled as such (production must supply them from their owners, never
from traces): Wait_VSync test count (timing-model output), the number of occupied SST slots (object manager; slot
positions do not change any skeleton count), Player_1 routine class (< 6, from the bucket's full-loop path).
NOT COMPARED: Process_Kos_Module_Queue/Queue_Kos ($1B28-$1BF0). Queue_Kos is shared with Ending event callers (three
calls in frame 4402), and the capture's prepared_after_events row is sampled after the coordinator (it already shows
the ori.b #$80 result), so the coordinator's pre-call inputs are not in the capture: its tests are SYNTHETIC only.
SYNTHETIC: branch vectors, closed-form slopes derived from the instruction table, split loop, coordinator paths,
OpenPath refusals and mutations.
"""
import csv
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'vint'))
import levelloop_cost as lc  # noqa: E402
import test_vint8_cost as tv  # noqa: E402

COMPARED = [r for r in lc.RANGES if r != (0x1B28, 0x1BF0)]
ENDING_ZONE = 0x19          # LevelEventArray: Ending_ScreenEvent at zone $19 act 1 and $1A act 0 (neither 9 nor $15)


def occupied(n):
    return tuple(0x10000 + 0x100 * k if k < n else 0 for k in range(lc.SLOTS))


def native(case):
    hist = {}
    with open(os.path.join(tv.NATIVE, case, 'frame-pc-histogram.csv')) as f:
        for r in csv.DictReader(f):
            pc = int(r['pc'], 16)
            if any(a <= pc < b for a, b in COMPARED):
                hist.setdefault(int(r['level_frame']), {})[pc] = int(r['count'])
    return hist


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class LevelLoop(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        cls.ledger = lc.LevelLoopLedger(cls.rom, tv.gpgx_cost(cls.rom))
        cls.motorola = lc.LevelLoopLedger(cls.rom, tv.motorola_cost)

    def run_state(self, **kw):
        kw.setdefault('slots', occupied(0))
        return self.ledger.run(lc.LoopState(**kw))

    @unittest.skipUnless(tv.NATIVE, 'set KOS46_NATIVE_ROOT')
    def test_native_skeleton_counts(self):
        n = 0
        for case in tv.CASES:
            for lf, counts in sorted(native(case[0]).items()):
                state = lc.LoopState(ending_running=True, current_zone=ENDING_ZONE,
                                     wait_iterations=counts[0x1D1C],                 # TEST ORACLE (timing output)
                                     slots=occupied(counts.get(0x1AB02, 0)))        # TEST ORACLE (object manager)
                r = self.ledger.run(state)
                got = {pc: k for pc, k in r.counts.items() if any(a <= pc < b for a, b in COMPARED)}
                self.assertEqual(got, counts, (case[0], lf))
                self.assertEqual(r.exit, 'loop')
                n += 1
        self.assertEqual(n, 42)

    def test_slopes_from_the_instruction_table(self):
        base = self.run_state().cycles
        self.assertEqual(self.run_state(slots=occupied(5)).cycles - base, 5 * (8 + 4 + 16 - 10))   # beq fall+movea+jsr
        self.assertEqual(self.run_state(wait_iterations=4).cycles - base, 3 * (12 + 10))          # tst.b + bne taken
        self.assertEqual(self.run_state(current_zone=9).cycles - base, 8 - 10 + 20)              # jsr LRZ rocks
        self.assertEqual(self.run_state(current_zone=0x15).cycles - base, 8 - 10 + 20 + 20)      # two Slots jsr
        self.assertEqual(self.run_state(ending_running=True).cycles - base,
                         (12 + 8 + 16) - (12 + 10 + 12 + 12 + 12 + 8 + 12 + 8 + 10 + 16))        # early rts

    def test_process_sprites_slot_loop(self):
        r = self.run_state(slots=occupied(12))
        self.assertEqual((r.counts[0x1AAFC], r.counts[0x1AB02], r.counts[0x1AB08], r.dispatched), (110, 12, 110, 12))
        self.assertEqual(sum(1 for k in r.open_terms if k.startswith('handler:')), 12)
        vis = tuple(k % 3 == 0 for k in range(lc.SLOTS))
        s = self.run_state(slots=occupied(60), player1_routine=6, render_visible=vis)
        self.assertEqual(s.counts[0x1AAFC], 4 + 16)               # players + first dynamic slot, then level objects
        self.assertEqual(s.counts[0x1AB1A], 90)                   # dynamic objects: draw only
        drawn = sum(1 for k in range(4, 94) if k < 60 and vis[k])
        self.assertEqual(s.open_terms.get('Draw_Sprite', 0), drawn)
        self.assertEqual(s.dispatched, 4)
        t = self.run_state(slots=occupied(30), teleport_active=True)
        self.assertNotIn(0x1AAFC, t.counts)
        self.assertEqual(self.run_state(slots=occupied(30), player1_routine=0x0C).counts[0x1AAFC], 110)

    def test_kos_module_coordinator_paths(self):
        idle = self.run_state()
        self.assertEqual(idle.counts[0x1B2E], 1)
        q = self.run_state(kos_modules_left=3, kos_decomp_queue_count=1)
        self.assertEqual((q.counts[0x1BD8], q.counts[0x1B46]), (1, 1))          # Queue_Kos, ori.b #$80
        full = self.run_state(kos_modules_left=3, kos_decomp_queue_count=0x8001)
        self.assertNotIn(0x1BD8, full.counts)                                    # busy bit reads as a full queue
        busy = self.run_state(kos_modules_left=0x83, kos_decomp_queue_count=0x8000)
        self.assertNotIn(0x1B54, busy.counts)
        done = self.run_state(kos_modules_left=0x83, kos_decomp_queue_count=0)
        self.assertEqual(done.open_terms.get('Add_To_DMA_Queue'), 1)
        self.assertNotIn(0x1BA6, done.counts)                                    # not the last module
        last = self.run_state(kos_modules_left=0x81, kos_decomp_queue_count=0)
        self.assertEqual([last.counts[pc] for pc in (0x1BAE, 0x1BB2, 0x1BB6, 0x1BBA)], [1, 1, 1, 1])  # unrolled shift
        self.assertEqual(last.counts.get(0x1BD6), 1)
        nxt = self.run_state(kos_modules_left=0x81, kos_decomp_queue_count=0, kos_next_module_queued=True)
        self.assertEqual(nxt.open_terms.get('Process_Kos_Module_Queue_Init'), 1)
        self.assertEqual(nxt.exit, 'loop')

    def test_exits_open_paths_and_motorola(self):
        self.assertEqual(self.run_state(restart_level=True).exit, 'restart')
        self.assertEqual(self.run_state(game_mode=0x1C).exit, 'return')         # e.g. Game_mode changed: loop exit
        for kw in (dict(game_paused=True), dict(start_pressed=True), dict(life_count=0), dict(demo_mode=True),
                   dict(water_flag=True), dict(game_mode=0x08)):
            with self.assertRaises(lc.OpenPath, msg=kw):
                self.run_state(**kw)
        for kw in (dict(), dict(slots=occupied(40), wait_iterations=9, current_zone=0x15),
                   dict(kos_modules_left=0x81, kos_decomp_queue_count=0, kos_next_module_queued=True)):
            kw.setdefault('slots', occupied(0))
            g = self.ledger.run(lc.LoopState(**kw))
            # pinned GPGX charges andi.l #imm,Dn 16 (m68ki_cycles $0280/$0281); the Motorola model 14. GPGX is the
            # authority (native reference); the divergence is asserted, not hidden.
            andi_l = g.counts.get(0x1B82, 0) + g.counts.get(0x1B94, 0)
            self.assertEqual(g.cycles - self.motorola.run(lc.LoopState(**kw)).cycles, 2 * andi_l)

    def test_mutations_and_bad_inputs(self):
        with self.assertRaises(ValueError):
            self.ledger.run(lc.LoopState(slots=occupied(0)[:109]))
        with self.assertRaises(ValueError):
            self.ledger.run(lc.LoopState(slots=occupied(0), wait_iterations=0))
        bad = bytearray(self.rom); bad[0x1AAFB] ^= 1                             # moveq #$6D -> #$6C
        with self.assertRaises(ValueError):
            lc.LevelLoopLedger(bytes(bad), tv.motorola_cost)
        wrong = self.run_state(slots=occupied(12))
        self.assertNotEqual(wrong.counts, self.run_state(slots=occupied(13)).counts)


if __name__ == '__main__':
    unittest.main()
