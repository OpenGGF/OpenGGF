#!/usr/bin/env python3
"""Synthetic descriptor tests; no ROM copying or runtime/queue execution.

Origin: promotion of the C4 comparison-only KosM lookup, 2026-11-05.
"""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "identify_kosm_fingerprint", Path(__file__).with_name("identify_kosm_fingerprint.py"))
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class FingerprintLookupTest(unittest.TestCase):
    def test_native_canonical_tuple_matches_recorded_descriptor(self):
        self.assertEqual(
            "sha256:372cb6ea63b128105dcf80cbf0dd640017be90e073fadef71b8c87b8ba69f4a6",
            tool.module_fingerprint(0x18008C, 863, 0xA000, 2112))
        self.assertNotEqual(tool.module_fingerprint(0x18008C, 863, 0xA000, 2112),
                            tool.module_fingerprint(0x18008C, 864, 0xA000, 2112))

    def test_bounded_padding_lookup_and_ambiguous_native_source(self):
        with tempfile.TemporaryDirectory() as directory:
            asset = Path(directory) / "sample.kosm"
            data = bytes.fromhex("0040") + bytes(range(1, 12))
            asset.write_bytes(data)
            target = tool.module_fingerprint(32, len(data) - 2, 0xA000, 64)
            rom = bytes(32) + data + bytes(32)
            result = tool.lookup(rom, [asset], [target], padding_bytes=2)
            self.assertEqual([], result["unmatched"])
            self.assertEqual(1, len(result["matches"]))
            self.assertEqual(len(data) - 2, result["matches"][0]["compressedLength"])
            self.assertEqual(0xA000, result["matches"][0]["destinationAddress"])
            outside = tool.lookup(rom, [asset], [target], padding_bytes=1)
            self.assertEqual([target], outside["unmatched"])
            ambiguous = tool.lookup(rom + data, [asset], [target], padding_bytes=2)
            self.assertEqual([], ambiguous["matches"])
            self.assertEqual(0, ambiguous["assets_matched_uniquely_in_rom"])

    def test_unknown_hash_and_invalid_bounds_do_not_fabricate_a_match(self):
        target = "sha256:" + "a" * 64
        self.assertEqual([target], tool.lookup(b"", [], [target])["unmatched"])
        for padding in [-1, 65]:
            with self.assertRaises(ValueError):
                tool.lookup(b"", [], [target], padding)
        with self.assertRaises(ValueError):
            tool.lookup(b"", [], ["not-a-hash"])
        with self.assertRaises(ValueError):
            tool.lookup(b"", [], [])


if __name__ == "__main__":
    unittest.main()
