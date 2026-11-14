"""ROM-free rejection checks for the CD-02 native device observer."""
import json
from pathlib import Path
import tempfile
import unittest

from device_observer_run import compare_observations, digest, verify_pins, verify_run


class DeviceObserverChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def run_files(self, label="run"):
        run = self.root / label
        run.mkdir()
        (run / "summary.json").write_text(json.dumps({"frames": 2,
            "restore_120_frames_equal": True, "observation_savestate_unchanged": True}))
        for name in ("devices.jsonl", "av.jsonl"):
            (run / name).write_text('{"frame":1}\n{"frame":2}\n')
        return run

    def test_changed_native_binary_fails_closed(self):
        core = self.root / "gpgx.wbx.zst"
        core.write_bytes(b"pinned native binary")
        pins = {core.name: digest(core)}
        self.assertEqual(verify_pins(self.root, pins), pins)
        core.write_bytes(b"different native binary")
        with self.assertRaisesRegex(ValueError, "Pinned file mismatch"):
            verify_pins(self.root, pins)

    def test_capture_requires_every_device_frame_in_order(self):
        run = self.run_files()
        verify_run(run, 2, True)
        (run / "devices.jsonl").write_text('{"frame":2}\n{"frame":1}\n')
        with self.assertRaisesRegex(ValueError, "reordered"):
            verify_run(run, 2, True)

    def test_capture_rejects_restore_divergence(self):
        run = self.run_files()
        p = run / "summary.json"
        summary = json.loads(p.read_text())
        summary["restore_120_frames_equal"] = False
        p.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, "restore"):
            verify_run(run, 2, True)

    def test_capture_rejects_observer_side_effect(self):
        run = self.run_files()
        p = run / "summary.json"
        summary = json.loads(p.read_text())
        summary["observation_savestate_unchanged"] = False
        p.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, "side-effect"):
            verify_run(run, 2, True)

    def test_repeat_rejects_device_difference(self):
        left, right = self.run_files("left"), self.run_files("right")
        compare_observations(left, right, ("devices.jsonl", "av.jsonl"))
        (right / "devices.jsonl").write_text('{"frame":1,"pcm_cursor":123}\n{"frame":2}\n')
        with self.assertRaisesRegex(ValueError, "devices.jsonl"):
            compare_observations(left, right, ("devices.jsonl",))

    def test_control_rejects_av_difference(self):
        left, right = self.run_files("left"), self.run_files("right")
        (right / "av.jsonl").write_text('{"frame":1,"audio_sha256":"different"}\n{"frame":2}\n')
        with self.assertRaisesRegex(ValueError, "av.jsonl"):
            compare_observations(left, right, ("av.jsonl",))


if __name__ == "__main__":
    unittest.main()
