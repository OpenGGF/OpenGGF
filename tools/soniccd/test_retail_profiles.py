"""Run with python3 -B -m unittest discover -s tools/soniccd -p test_retail_profiles.py."""

import copy
import hashlib
import struct
import unittest
import xml.etree.ElementTree as ET

import retail_profiles as retail


def fixture():
    cue = []
    files = []
    record = ET.Element("game")
    ET.SubElement(record, "rom", name="disc.cue")
    for number in range(1, 36):
        name = f"disc (Track {number:02}).bin"
        cue += [f'FILE "{name}" BINARY', f'TRACK {number:02} ' + ("MODE1/2352" if number == 1 else "AUDIO")]
        if number > 1:
            cue += ["INDEX 00 00:00:00"]
        cue += ["INDEX 01 00:00:00" if number == 1 else "INDEX 01 00:01:74"]
        files.append({"path": name, "bytes": 2352 * 200, "crc32": "1234ABCD", "sha1": "1" * 40, "sha256": "2" * 64})
        ET.SubElement(record, "rom", name=name, size=str(2352 * 200), crc="1234abcd", sha1="1" * 40)
    return files, "\n".join(cue), record


class RetailProfileTests(unittest.TestCase):
    def test_exactly_35_bin_entries_excludes_the_36th_cue_node(self):
        files, cue, record = fixture()
        tracks = retail.verified_tracks(files, cue, record)
        self.assertEqual(35, len(tracks))
        self.assertEqual(200 + 149, tracks[1]["discIndex01Frame"])
        self.assertEqual(51, tracks[1]["programFrames"])
        self.assertEqual(149, tracks[1]["storedPregapFrames"])
        self.assertEqual(0, tracks[1]["syntheticPregapFrames"])

    def test_every_dat_triple_must_match_including_audio_track_13(self):
        files, cue, record = fixture()
        for field, value in (("bytes", 2352), ("crc32", "00000000"), ("sha1", "0" * 40)):
            changed = copy.deepcopy(files)
            changed[12][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "DAT .* mismatch"):
                retail.verified_tracks(changed, cue, record)

    def test_missing_track_or_extra_dat_binary_fails(self):
        files, cue, record = fixture()
        with self.assertRaises(ValueError):
            retail.verified_tracks(files[:-1], cue, record)
        ET.SubElement(record, "rom", name="extra.bin")
        with self.assertRaises(ValueError):
            retail.verified_tracks(files, cue, record)

    def test_geometry_cannot_silently_insert_synthetic_gaps_or_cook_raw_data(self):
        _, cue, _ = fixture()
        for changed in (cue.replace("INDEX 00 00:00:00", "PREGAP 00:02:00", 1),
                        cue.replace("MODE1/2352", "MODE1/2048"),
                        cue.replace("00:01:74", "00:01:75", 1)):
            with self.subTest(cue=changed[:120]), self.assertRaises(ValueError):
                retail.parse_cue(changed)

    def test_normalization_uses_big_endian_widths_and_raw_digest_bytes(self):
        track = {"number": 1, "mode": "MODE1_2352", "discIndex01Frame": 0,
                 "programFrames": 200, "storedPregapFrames": 0, "syntheticPregapFrames": 0,
                 "storedFramesSha256": "2" * 64}
        expected = (b'\x00\x19scd-cue-layout-content-v1' + struct.pack(">ii", 1, 1)
                    + b'\x00\x0aMODE1_2352' + struct.pack(">qqqq", 0, 200, 0, 0) + b'\x22' * 32)
        self.assertEqual(hashlib.sha256(expected).hexdigest(), retail.normalized_sha256([track]))

    def test_content_hash_ignores_filenames_and_cue_formatting(self):
        files, cue, record = fixture()
        before = retail.verified_tracks(files, cue, record)
        renamed = [{**item, "path": item["path"].replace("disc", "renamed")} for item in files]
        after = retail.verified_tracks(renamed, "REM formatting\n" + cue.replace("disc", "renamed"), record)
        self.assertEqual(retail.normalized_sha256(before), retail.normalized_sha256(after))


if __name__ == "__main__":
    unittest.main()
