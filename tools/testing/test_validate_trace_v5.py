"""Executable contract tests for the read-only v5 trace fleet validator."""

from __future__ import annotations

import gzip
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = REPOSITORY_ROOT / "tools" / "traces" / "validate_trace_v5.py"
FINGERPRINT = "sha256:" + "a" * 64


class ValidateTraceV5Tests(unittest.TestCase):
    """Each test names the v5 contract regression it prevents."""

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_accepts_native_v5_plain_and_gzipped_payloads_without_rewriting_them(self) -> None:
        fixture = self.write_fixture("s3k", "complete_run", compressed=True)
        original = {path: path.read_bytes() for path in fixture.rglob("*") if path.is_file()}

        result = self.validate()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(original, {path: path.read_bytes() for path in fixture.rglob("*") if path.is_file()})

    def test_reports_exact_paths_for_envelope_legacy_width_timing_manifest_and_sidecar_errors(self) -> None:
        fixture = self.write_fixture("s1", "complete_run")
        metadata = json.loads((fixture / "metadata.json").read_text())
        metadata.update({
            "trace_schema": 4,
            "csv_version": 7,
            "recorder": "lua-bizhawk-diagnostic",
            "recorder_version": "9.2-s2",
        })
        (fixture / "metadata.json").write_text(json.dumps(metadata))
        (fixture / "physics.csv").write_text("a,b\n1,2\n")
        (fixture / "hardware_timing.jsonl").write_text("{\"event\":\"wrong\"}\n")
        (fixture / "physics_retro.csv").write_text("legacy\n")
        self.write_manifest({"run_schema": 2, "trace_schema": 4})

        result = self.validate()

        self.assertNotEqual(0, result.returncode)
        for path in (
            fixture / "metadata.json",
            fixture / "physics.csv",
            fixture / "hardware_timing.jsonl",
            fixture / "physics_retro.csv",
            self.root / "run_manifest.json",
        ):
            self.assertIn(str(path), result.stderr)
        self.assertIn("recorder must be native-bizhawk-headless", result.stderr)
        self.assertIn("recorder_version must be 3.0", result.stderr)

    def test_rejects_special_stage_rows_that_do_not_match_the_game_owned_width(self) -> None:
        fixture = self.write_fixture("s2", "s2_special_stage")
        (fixture / "physics.csv").write_text("a,b\n1,2\n")

        result = self.validate()

        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"{fixture / 'physics.csv'}: row 1 has 2 columns; expected 48", result.stderr)

    def test_rejects_timing_that_omits_the_current_direct_queue_grammar(self) -> None:
        fixture = self.write_fixture("s3k", "complete_run")
        (fixture / "hardware_timing.jsonl").write_text(
            json.dumps({
                "event": "hardware_work_completed",
                "raw_frame": 0,
                "boundary": "post_objects",
                "kind": "kos_decompression_queue",
                "ordinal": 0,
                "submission_fingerprint": FINGERPRINT,
            }) + "\n")

        result = self.validate()

        self.assertNotEqual(0, result.returncode)
        self.assertIn(f"{fixture / 'hardware_timing.jsonl'}: line 1", result.stderr)
        self.assertIn("pre_main_loop", result.stderr)

    def test_requires_current_manifest_gap_array_and_native_provenance(self) -> None:
        self.write_manifest({"dynamic_art_gap_transitions": {}})

        result = self.validate()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("run_manifest.json: dynamic_art_gap_transitions must be an array", result.stderr)

    def test_requires_current_run_manifest_arrays(self) -> None:
        self.write_manifest({"segments": {}})

        result = self.validate()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("run_manifest.json: segments must be an array", result.stderr)

    def write_fixture(self, game: str, profile: str, compressed: bool = False) -> Path:
        fixture = self.root / "fixture"
        fixture.mkdir()
        metadata = {
            "game": game,
            "trace_profile": profile,
            "trace_frame_count": 2,
            "recorder": "native-bizhawk-headless",
            "recorder_version": "3.0",
            "trace_schema": 5,
        }
        self.write_payload(fixture / "metadata.json", json.dumps(metadata), compressed)
        width = {"s1_special_stage": 14, "s2_special_stage": 48,
                 "s3k_special_stage": 20}.get(profile, 42)
        row = ",".join(["column"] * width) + "\n" + ",".join(["0"] * width) + "\n"
        self.write_payload(fixture / "physics.csv", row, compressed)
        return fixture

    def write_manifest(self, updates: dict[str, object]) -> None:
        manifest = {
            "game": "s1",
            "run_id": "fixture-run",
            "recorder": "native-bizhawk-headless",
            "recorder_version": "3.0",
            "trace_schema": 5,
            "segments": [],
            "transitions": [],
            "dynamic_art_gap_transitions": [],
        }
        manifest.update(updates)
        (self.root / "run_manifest.json").write_text(json.dumps(manifest))

    @staticmethod
    def write_payload(path: Path, content: str, compressed: bool) -> None:
        if compressed:
            with gzip.open(path.with_suffix(path.suffix + ".gz"), "wt", encoding="utf-8") as output:
                output.write(content)
        else:
            path.write_text(content)

    def validate(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(VALIDATOR), str(self.root)],
            text=True,
            capture_output=True,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
