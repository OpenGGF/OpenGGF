"""Constructed safety controls for the research Nemesis oracle; no media discovery or asset fixtures."""
import unittest

from r11a_asset_check import native_mask_fragments, nemesis


class R11aReferenceDecoderTest(unittest.TestCase):
    def test_prefix_tree_decodes_known_nibbles_without_read_ahead(self):
        data = bytes([0, 1, 0x8a, 0x71, 0, 0xff, 0, 0, 77])
        output, cursor = nemesis(data, 32)
        self.assertEqual(bytes([0xaa]) * 32, output)
        self.assertEqual(7, cursor)

    def test_eight_bit_reader_lookahead_has_an_independent_cursor(self):
        data = bytes([0, 1, 0x8a, 0x71, 0, 0xff, 0, 0, 77])
        output, cursor = nemesis(data, 32, reader_lookahead=True)
        self.assertEqual(bytes([0xaa]) * 32, output)
        self.assertEqual(8, cursor)

    def test_inline_run_without_a_prefix_table(self):
        bits = ("111111" + f"{0x7a:07b}") * 8
        data = bytes([0, 1, 0xff]) + int(bits, 2).to_bytes(13, "big")
        self.assertEqual(bytes([0xaa]) * 32, nemesis(data, 32)[0])
        self.assertEqual(bytes([0xaa]) * 32, nemesis(data, 32, reader_lookahead=True)[0])

    def test_xor_is_over_complete_32_bit_rows(self):
        data = bytes([0x80, 1, 0x8a, 0x71, 0, 0xff, 0, 0])
        self.assertEqual((bytes([0xaa]) * 4 + bytes(4)) * 4, nemesis(data, 32)[0])

    def test_wrong_output_bounds_header_truncation_and_run_overflow(self):
        valid = bytes([0, 1, 0x8a, 0x71, 0, 0xff, 0, 0])
        for expected in (0, 31, 64, 0x10020):
            with self.subTest(expected=expected), self.assertRaises(ValueError):
                nemesis(valid, expected)
        with self.assertRaises(ValueError):
            nemesis(valid[:1], 32)
        with self.assertRaises(ValueError):
            nemesis(valid[:7], 32, reader_lookahead=True)
        with self.assertRaises(ValueError):
            nemesis(bytes([0, 1, 0x8a, 0x61, 0, 0xff, 0, 0, 0]), 32)

    def test_unassigned_and_overlapping_prefixes_are_rejected(self):
        with self.assertRaises(ValueError):
            nemesis(bytes([0, 1, 0xff, 0, 0]), 32)
        with self.assertRaises(ValueError):
            nemesis(bytes([0, 1, 0x8a, 0x71, 0, 0x72, 0, 0xff, 0, 0]), 32)

    def test_native_mask_fragment_mutation_is_not_a_valid_reference(self):
        data = self.mask_fragments()
        native_mask_fragments(data, "USA")
        data[0x339d] ^= 2
        with self.assertRaises(ValueError):
            native_mask_fragments(data, "USA")

    def test_extra_or_misbound_instruction_occurrences_are_rejected(self):
        data = self.mask_fragments()
        data[0:4] = bytes.fromhex("08100004")
        with self.assertRaises(ValueError):
            native_mask_fragments(data, "USA")
        with self.assertRaises(ValueError):
            native_mask_fragments(self.mask_fragments(), "Japan")

    @staticmethod
    def mask_fragments():
        # Only constructed instruction fragments, not an authentic MMD fixture.
        data = bytearray(0x4000)
        for pattern, offsets in (
                ("3610024303ffe74bd2c370014e75", [0x3398]),
                ("30113800024007ff", [0x11bc, 0x127c, 0x132c, 0x13dc]),
                ("08100004", [0x32da]), ("08100003", [0x32e0, 0x3312])):
            fragment = bytes.fromhex(pattern)
            for offset in offsets:
                data[offset:offset + len(fragment)] = fragment
        return data


if __name__ == "__main__":
    unittest.main()
