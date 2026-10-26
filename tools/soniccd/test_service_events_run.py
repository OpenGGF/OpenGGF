"""Fail-closed tests for the CD-02 diagnostic service-event verifier, 2026-10-23."""
import json
from pathlib import Path
import tempfile
import unittest

from service_events_run import ROOT, parse_inputs, verify_run, compare


class ServiceEventsTests(unittest.TestCase):
    def setUp(self):
        ROOT.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=ROOT, prefix="negative-test-")
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.inputs = parse_inputs("1:Start", 2)
        self.summary = {"frames": 2, "restore_120_frames_equal": True,
                        "observation_savestate_unchanged": True,
                        "disc_before": 1, "disc_original": 1, "disc_after": 1}
        self.av = [{"frame": 1, "buttons": ["Start"]}, {"frame": 2, "buttons": []}]
        self.events = [
            {"frame_call": 1, "ordinal_in_call": 1, "phase": "disc_sector_before_original_callback", "access": {"lba": 7, "subcode": False}},
            {"frame_call": 1, "ordinal_in_call": 2, "phase": "disc_sector_after_original_callback_before_stream_copy",
             "access": {"lba": 7, "subcode": False, "bytes": 2352, "payload_sha256": "a" * 64}},
        ]

    def write(self):
        (self.path / "summary.json").write_text(json.dumps(self.summary))
        for name, rows in (("av", self.av), ("events", self.events)):
            (self.path / (name + ".jsonl")).write_text("".join(json.dumps(r) + "\n" for r in rows))

    def check(self):
        self.write()
        return verify_run(self.path, 2, self.inputs, True)

    def test_valid(self):
        self.check()

    def test_input_boundaries(self):
        self.assertEqual(parse_inputs("1-2:A,2:Start", 2)[0], {"first": 1, "last": 2, "button": "A"})
        for text in ("0:A", "3:Start", "2-1:B", "1:X", "1:A;echo nope"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_inputs(text, 2)

    def test_controller_mismatch(self):
        self.av[0]["buttons"] = ["B"]
        with self.assertRaisesRegex(ValueError, "controller"):
            self.check()

    def test_frame_order(self):
        self.av.reverse()
        with self.assertRaisesRegex(ValueError, "ordering"):
            self.check()

    def test_restore_failure(self):
        self.summary["restore_120_frames_equal"] = False
        with self.assertRaisesRegex(ValueError, "restore/state"):
            self.check()

    def test_observer_mutation(self):
        self.summary["observation_savestate_unchanged"] = False
        with self.assertRaisesRegex(ValueError, "restore/state"):
            self.check()

    def test_disc_forward_count(self):
        self.summary["disc_original"] = 2
        with self.assertRaisesRegex(ValueError, "forwarding"):
            self.check()

    def test_event_order(self):
        self.events.reverse()
        with self.assertRaises(ValueError):
            self.check()

    def test_disc_pair_lba(self):
        self.events[1]["access"]["lba"] = 8
        with self.assertRaisesRegex(ValueError, "pairing"):
            self.check()

    def test_missing_disc_after(self):
        self.events.pop()
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            self.check()

    def test_comparison_difference(self):
        other = self.path / "other"
        other.mkdir()
        (self.path / "av.jsonl").write_text("one")
        (other / "av.jsonl").write_text("two")
        with self.assertRaisesRegex(ValueError, "comparison"):
            compare(self.path, other, ("av.jsonl",))


if __name__ == "__main__":
    unittest.main()
