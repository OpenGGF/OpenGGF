#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R14 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: env S3K_ROM and GPGX_GIT (Genesis-Plus-GX repo holding 051d430d). Without them the tests SKIP;
#   a skip is not a pass.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""SST allocation/deletion ledger tests (kos46 research R14). SYNTHETIC/source-only: native buckets execute
AllocateObjectAfterCurrent (bucket 4403: 9 calls, 44 search steps) but the cursor slots and occupancy at each call are
not in the capture, so no native equality is claimed. Hand sums use the pinned instruction table."""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'vint'))
import alloc_cost as ac  # noqa: E402
import test_vint8_cost as tv  # noqa: E402


@unittest.skipUnless(tv.ROM_PATH and tv.GPGX, 'set S3K_ROM and GPGX_GIT')
class Alloc(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(tv.ROM_PATH, 'rb') as f:
            cls.rom = f.read()
        sys.path.insert(0, tv.vc.TOOL)
        import gpgx_cycles
        cls.g = ac.AllocLedger(cls.rom, ac.gpgx_cost(cls.rom, gpgx_cycles.load_table(tv.GPGX)))
        cls.m = ac.AllocLedger(cls.rom, ac.motorola_cost)

    def test_hand_sums(self):
        full = [1] * ac.SLOTS
        # lea 8, moveq 4, bra.s 10, 90 x (lea 8 + tst.l 12), 89 dbeq taken 10, expired 14, rts 16
        self.assertEqual(self.g.allocate(full), (8 + 4 + 10 + 90 * 20 + 89 * 10 + 14 + 16, self.g.allocate(full)[1], None))
        # moveq 4, moveq 4, 18 x move.l 12, 17 dbf taken 10, expired 14, move.w 8, rts 16 (+ movea.l 4)
        self.assertEqual(self.g.delete(False), 4 + 4 + 18 * 12 + 17 * 10 + 14 + 8 + 16)
        self.assertEqual(self.g.delete(True), self.g.delete(False) + 4)
        occ = [1] * 10 + [0] * 100
        c, counts, slot = self.g.after_current(50, occ)
        # movea 4, move.w # 8, sub.w a0 4, lsr.w #6 18, move.b (pc,d0) 14, bmi fall 8, lea 8, tst.l 12, dbeq true 12, rts
        self.assertEqual((c, slot), (4 + 8 + 4 + 18 + 14 + 8 + 8 + 12 + 12 + 16, 51))

    def test_shipped_quirks(self):
        empty = [0] * ac.SLOTS
        self.assertEqual(self.g.allocate(empty)[2], 4)              # first checked slot is Dynamic_object_RAM+1
        only93 = [1] * 93 + [0] * 17
        self.assertEqual(self.g.allocate(only93)[2], 93)            # reaches the first Level_object_RAM slot
        self.assertEqual(self.g.after_current(92, only93)[2], 93)   # last dynamic slot may allocate slot 93
        self.assertFalse(self.g.beyond_table)
        r = self.g.after_current(3, [1] * 60 + [0] * 50)
        self.assertTrue(self.g.beyond_table)                        # index 104: code byte after the table
        self.assertEqual(self.rom[ac.LOOKUP + 104], 0x43)
        self.assertEqual(r[2], 60)
        r = self.g.after_current(3, [1] * 80 + [0] * 30)            # $43 + 1 = 68 checks: slots 4..71 all busy
        self.assertIsNone(r[2])
        self.assertEqual(r[1][ac.LOOP], 0x43 + 1)

    def test_lookup_counts_and_motorola(self):
        occ = [1] * ac.SLOTS
        for cur in range(4, 93):
            g = self.g.after_current(cur, occ)
            n = g[1].get(ac.LOOP, 0)
            idx = ((self.g.dyn_end - self.g.slot_addr(cur)) & 0xFFFF) >> 6
            self.assertEqual(n, ((idx + 1) * 0x40 - 1) // 0x4A, cur)  # table entry + 1 checks
            self.assertEqual(g[0], self.m.after_current(cur, occ)[0], cur)

    def test_mutations_and_bounds(self):
        bad = bytearray(self.rom); bad[ac.LOOKUP + 7] ^= 1
        with self.assertRaises(ValueError):
            ac.AllocLedger(bytes(bad), ac.motorola_cost)
        bad = bytearray(self.rom); bad[0x1BB11] ^= 2                # dbeq displacement
        with self.assertRaises(ValueError):
            ac.AllocLedger(bytes(bad), ac.motorola_cost)
        # level-object callers wrap the word index into code bytes past the table (ROM data, executed as shipped)
        c, counts, slot = self.g.after_current(100, [1] * ac.SLOTS)  # byte $C0: negative, returns without a search
        self.assertEqual((slot, counts.get(ac.LOOP, 0)), (None, 0))
        with self.assertRaises(ValueError):
            self.g.after_current(94, [1] * ac.SLOTS)                 # byte $31: 50 checks would pass Object_RAM_end
        with self.assertRaises(ValueError):
            self.g.allocate([0] * 50)


if __name__ == '__main__':
    unittest.main()
