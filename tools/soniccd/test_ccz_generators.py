#!/usr/bin/env python3
"""Tests for the Collision Chaos table generators.

Run: python3 -B -m unittest discover -s tools/soniccd -p test_ccz_generators.py
The disc-side checks build constructed instruction sequences; no commercial media is read. The
committed profile resource is checked for internal consistency, and against scddisasm only when
CCZ_SCDDISASM names a checkout.
"""
import os
import unittest
from pathlib import Path

import ccz_cycle_catalogue as disc
import ccz_scroll_profiles as scroll

ROOT = Path(__file__).resolve().parents[2]


def lea(opcode: bytes, address: int) -> bytes:
    return opcode + (0x200000 + address).to_bytes(4, "big")


class DiscTableTests(unittest.TestCase):
    def test_script_pattern_reads_each_call_in_order(self):
        code = b"\x4b\xf8\xf6\x20\x49\xf8\xf6\x10"
        calls = [(0x100, 0x120), (0x130, 0x150)]
        code += lea(b"\x43\xf9", calls[0][0]) + lea(b"\x45\xf9", calls[0][1]) + b"\x61\x14"
        code += lea(b"\x43\xf9", calls[1][0]) + lea(b"\x45\xf9", calls[1][1]) + b"\x53\x15"
        data = b"\x00" * 16 + code + b"\x00" * 0x200
        rows = disc.scripts("TEST", data, 2)
        self.assertEqual([(r[2], r[3], r[4]) for r in rows], [(0, 0x100, 0x120), (1, 0x130, 0x150)])

    def test_script_pattern_rejects_a_stage_without_the_routine(self):
        with self.assertRaises(ValueError):
            disc.scripts("TEST", b"\x00" * 0x300, 2)

    def test_wobble_pattern_returns_table_before_bounds(self):
        code = b"\x45\xf2\x00\x00" + lea(b"\x47\xf9", 0x2C12) + lea(b"\x49\xf9", 0x3A746) + b"\x06\x78\x00\x40\xf6\x6c"
        data = b"\x00" * 8 + code + b"\x00" * 0x40000
        row = disc.wobble("TEST", data)
        self.assertEqual(row, ("TEST", "wobble", 0, 0x3A746, 0x2C12))

    def test_animated_pattern_resolves_the_frame_table_and_both_dma_windows(self):
        # lsl.w #3,d0 ; movea.l T(pc,d0.w),a1 ; movea.l T+4(pc,d0.w),a2
        code = b"\xe7\x48\x22\x7b\x00\x68\x24\x7b\x00\x68"
        gap = b"\x00" * (0x68 + 4 - len(code) - 4 + 4)
        dma = (b"\x2a\xbc\x94\x00\x93\xa0\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f\x3a\xbc\x76\x80"
               b"\x2a\xbc\x94\x00\x93\x60\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f\x3a\xbc\x79\xc0")
        table_at = 0x100 + 4 + 0x68
        data = bytearray(0x100) + bytearray(code) + bytearray(dma)
        data += bytearray(table_at + 32 - len(data))
        for index, (primary, secondary) in enumerate([(0x3459A, 0x346DA), (0x3439A, 0x344DA)]):
            data[table_at + 8 * index: table_at + 8 * index + 4] = (0x200000 + primary).to_bytes(4, "big")
            data[table_at + 8 * index + 4: table_at + 8 * index + 8] = (0x200000 + secondary).to_bytes(4, "big")
        data += bytearray(0x40000)
        rows = disc.animated("TEST", bytes(data))
        self.assertEqual(rows, [("TEST", "frame", 0, 0x3459A, 0x346DA), ("TEST", "frame", 1, 0x3439A, 0x344DA),
                                ("TEST", "dma", 0, 0xA0, 0x7680), ("TEST", "dma", 1, 0x60, 0x79C0)])


class ProfileResourceTests(unittest.TestCase):
    """The committed profiles must describe a 96-word (R33D: 95) row table and the right array sizes."""

    @classmethod
    def setUpClass(cls):
        lines = (ROOT / "src/main/resources/soniccd/ccz-scroll-profiles.tsv").read_text().splitlines()
        cls.rows = [l.split("\t") for l in lines if l and not l.startswith("#")][1:]

    def stage_rows(self, stage):
        return [r for r in self.rows if r[0] == stage]

    def test_every_stage_has_rates_curve_and_water_and_a_full_row_table(self):
        for stage in scroll.STAGES:
            rows = self.stage_rows(stage)
            kinds = [r[1] for r in rows]
            self.assertEqual(kinds.count("rates"), 1, stage)
            self.assertEqual(kinds.count("curve"), 1, stage)
            self.assertEqual(kinds.count("water"), 1, stage)
            words = 0
            for r in rows:
                if r[1] == "fill":
                    words += int(r[3])
                elif r[1] == "deform":
                    words += sum(int(x) + 1 for x in r[3].split(","))
                elif r[1] == "persp":
                    words += int(r[5]) if r[6] == "-" else sum(int(x) + 1 for x in r[6].split(","))
            self.assertEqual(words, 95 if stage == "R33D" else 96, f"{stage} row-table words")

    def test_deform_blocks_read_as_many_longs_as_the_rate_array_holds(self):
        for stage in scroll.STAGES:
            rates = len(next(r for r in self.stage_rows(stage) if r[1] == "rates")[2].split(","))
            deform = next(r for r in self.stage_rows(stage) if r[1] == "deform")
            self.assertEqual(int(deform[2]), rates, stage)

    @unittest.skipUnless(os.environ.get("CCZ_SCDDISASM"), "set CCZ_SCDDISASM to a scddisasm checkout")
    def test_resource_matches_the_disassembly(self):
        text = scroll.render(Path(os.environ["CCZ_SCDDISASM"]))
        self.assertEqual((ROOT / "src/main/resources/soniccd/ccz-scroll-profiles.tsv").read_text(), text)


if __name__ == "__main__":
    unittest.main()
