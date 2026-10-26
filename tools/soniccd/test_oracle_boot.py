"""ROM-free checks for native evidence identity and incomplete/mismatched captures."""

import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from oracle_boot import compare_runs, media_identity, verify_run


class TestOracleBoot(unittest.TestCase):
    def test_media_fingerprint_covers_audio_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cue = root / "disc.cue"
            cue.write_text('FILE "data.bin" BINARY\n TRACK 01 MODE1/2352\n INDEX 01 00:00:00\n'
                           'FILE "audio.bin" BINARY\n TRACK 02 AUDIO\n INDEX 01 00:00:00\n')
            (root / "data.bin").write_bytes(b"data")
            (root / "audio.bin").write_bytes(b"sound")
            first = media_identity(cue)
            self.assertEqual(first["files"][1]["sha256"], hashlib.sha256(b"sound").hexdigest())
            (root / "audio.bin").write_bytes(b"altered")
            self.assertNotEqual(first["source_fingerprint"], media_identity(cue)["source_fingerprint"])

    def test_cue_cannot_escape_its_media_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            cue = Path(temporary) / "disc.cue"
            cue.write_text('FILE "../outside.bin" BINARY\n')
            with self.assertRaises(ValueError):
                media_identity(cue)

    def make_run(self, root):
        root.mkdir()
        (root / "summary.json").write_text(json.dumps({"advanced_hardware_frames": 2,
            "is_mega_cd": True, "replay_120_frames_equal": True, "input_start_frames": [2]}))
        (root / "av.jsonl").write_text('{"frame":1,"start":false}\n{"frame":2,"start":true}\n')
        (root / "frames.jsonl").write_text('{"frame":2,"registers":{"M68K PC":1024}}\n')
        (root / "frame-00002.ppm").write_bytes(b"P6\n1 1\n255\n\x01\x02\x03")

    def test_equal_complete_captures_compare(self):
        with tempfile.TemporaryDirectory() as temporary:
            a, b = Path(temporary) / "a", Path(temporary) / "b"
            self.make_run(a)
            self.make_run(b)
            verify_run(a, 2, [2])
            self.assertEqual(compare_runs(a, b)["captures"], 1)

    def test_differences_in_each_observed_output_are_rejected(self):
        for name in ("av.jsonl", "frames.jsonl", "frame-00002.ppm", "summary.json"):
            with self.subTest(output=name), tempfile.TemporaryDirectory() as temporary:
                a, b = Path(temporary) / "a", Path(temporary) / "b"
                self.make_run(a)
                self.make_run(b)
                if name == "summary.json":
                    data = json.loads((b / name).read_text())
                    data["advanced_hardware_frames"] = 1
                    (b / name).write_text(json.dumps(data))
                else:
                    with (b / name).open("ab") as stream:
                        stream.write(b"changed")
                with self.assertRaises(ValueError):
                    compare_runs(a, b)

    def test_incomplete_av_or_wrong_input_are_rejected(self):
        for observations in ('{"frame":1,"start":false}\n',
                             '{"frame":1,"start":false}\n{"frame":2,"start":false}\n'):
            with self.subTest(av=observations), tempfile.TemporaryDirectory() as temporary:
                run = Path(temporary) / "run"
                self.make_run(run)
                (run / "av.jsonl").write_text(observations)
                with self.assertRaises(ValueError):
                    verify_run(run, 2, [2])


if __name__ == "__main__":
    unittest.main()
