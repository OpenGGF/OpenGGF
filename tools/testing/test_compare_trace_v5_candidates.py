"""Contract tests for read-only v5 candidate comparison and credits evidence."""

from __future__ import annotations

import gzip
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.traces.compare_trace_v5_candidates import compare_roots
from tools.traces.verify_s1_credits_raw_host_evidence import verify_evidence


HEADER_20 = ["frame", "input", "x", "y", "x_speed", "y_speed", "g_speed", "angle",
             "air", "rolling", "ground_mode", "x_sub", "y_sub", "routine", "camera_x",
             "camera_y", "rings", "status_byte", "v_framecount", "stand_on_obj"]
HEADER_42 = ["frame", "input", "camera_x", "camera_y", "rings", "gameplay_frame_counter",
             "vblank_counter", "lag_counter", "player_present", "player_x", "player_y",
             "player_x_speed", "player_y_speed", "player_g_speed", "player_angle",
             "player_air", "player_rolling", "player_ground_mode", "player_x_sub",
             "player_y_sub", "player_routine", "player_status_byte", "player_stand_on_obj",
             "player_animation_id", "player_mapping_frame", "sidekick_present", "sidekick_x",
             "sidekick_y", "sidekick_x_speed", "sidekick_y_speed", "sidekick_g_speed",
             "sidekick_angle", "sidekick_air", "sidekick_rolling", "sidekick_ground_mode",
             "sidekick_x_sub", "sidekick_y_sub", "sidekick_routine", "sidekick_status_byte",
             "sidekick_stand_on_obj", "sidekick_animation_id", "sidekick_mapping_frame"]
COMPARATOR = Path(__file__).resolve().parents[1] / "traces" / "compare_trace_v5_candidates.py"


class CompareTraceV5CandidatesTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        root = Path(self.temporary_directory.name)
        self.old = root / "old"
        self.candidate = root / "candidate"
        self.old.mkdir()
        self.candidate.mkdir()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_credits_mode_reports_all_common_mismatches_and_every_added_column(self) -> None:
        old_row = ["0"] * 20
        old_row[2] = "1"
        candidate_row = ["0"] * 42
        candidate_row[HEADER_42.index("player_x")] = "9"
        old_fixture = self.write_fixture(self.old, HEADER_20, [old_row], v5=False)
        candidate_fixture = self.write_fixture(
            self.candidate, HEADER_42, [candidate_row], compressed=True,
            name="00_ghz1_credits_demo_1")
        before = self.snapshot(self.old) | self.snapshot(self.candidate)

        report = compare_roots(self.old, self.candidate, mode="credits-20-to-42")

        physics = self.file_report(report, "s1/credits_00_ghz1/physics.csv")
        self.assertEqual(20, len(physics["comparison"]["common_columns"]))
        self.assertIn({"predecessor": "x", "candidate": "player_x"},
                      physics["comparison"]["common_columns"])
        self.assertEqual(22, len(physics["comparison"]["added_columns"]))
        self.assertEqual([], physics["comparison"]["removed_columns"])
        self.assertEqual(
            [{"row": 0, "column": "x", "predecessor": "1", "candidate": "9"}],
            physics["comparison"]["common_field_mismatches"],
        )
        self.assertNotEqual(physics["predecessor"]["stored_sha256"], physics["candidate"]["stored_sha256"])
        self.assertEqual(hashlib.sha256((candidate_fixture / "physics.csv.gz").read_bytes()).hexdigest(),
                         physics["candidate"]["stored_sha256"])
        self.assertEqual(before, self.snapshot(self.old) | self.snapshot(self.candidate))
        self.assertTrue(old_fixture.exists())

    def test_literal_v5_comparison_reports_metadata_manifest_timing_aux_and_inventory(self) -> None:
        self.write_fixture(self.old, HEADER_42, [["0"] * 42])
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])
        (self.old / "run_manifest.json").write_text(self.manifest("old"))
        (self.candidate / "run_manifest.json").write_text(self.manifest("new"))
        old_fixture = self.old / "s1" / "credits_00_ghz1"
        candidate_fixture = self.candidate / "s1" / "credits_00_ghz1"
        (old_fixture / "aux_state.jsonl").write_text('{"event":"old"}\n')
        (candidate_fixture / "aux_state.jsonl").write_text('{"event":"new"}\n')
        (old_fixture / "hardware_timing.jsonl").write_text(self.timing(0))
        (candidate_fixture / "hardware_timing.jsonl").write_text(self.timing(1))
        (self.candidate / "added.txt").write_text("added\n")

        report = compare_roots(self.old, self.candidate)

        self.assertIn("added added.txt", report["inventory_changes"])
        manifest = self.file_report(report, "run_manifest.json")
        self.assertFalse(manifest["logical_equal"])
        timing = self.file_report(report, "s1/credits_00_ghz1/hardware_timing.jsonl")
        self.assertFalse(timing["logical_equal"])
        aux = self.file_report(report, "s1/credits_00_ghz1/aux_state.jsonl")
        self.assertEqual(["new"], aux["comparison"]["added_event_types"])
        self.assertEqual(["old"], aux["comparison"]["removed_event_types"])
        metadata = self.file_report(report, "s1/credits_00_ghz1/metadata.json")
        self.assertTrue(metadata["logical_equal"])

    def test_v5_mode_rejects_legacy_envelopes_without_treating_scanning_as_acceptance(self) -> None:
        self.write_fixture(self.old, HEADER_20, [["0"] * 20], v5=False)
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])

        with self.assertRaisesRegex(ValueError, "predecessor root is not v5"):
            compare_roots(self.old, self.candidate)

        report = compare_roots(self.old, self.candidate, mode="credits-20-to-42")
        self.assertIn("lua_script_version", report["predecessor_scan"]["legacy_keys"])
        self.assertEqual([20], report["predecessor_scan"]["physics_widths"])

    def test_cli_refuses_to_write_a_report_inside_either_compared_root(self) -> None:
        self.write_fixture(self.old, HEADER_42, [["0"] * 42])
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])
        output = self.candidate / "comparison.json"

        result = subprocess.run(
            [sys.executable, str(COMPARATOR), str(self.old), str(self.candidate),
             "--output", str(output)], text=True, capture_output=True, check=False)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("outside both compared roots", result.stderr)
        self.assertFalse(output.exists())

    def write_fixture(self, root: Path, header: list[str], rows: list[list[str]],
                      compressed: bool = False, v5: bool = True,
                      name: str = "credits_00_ghz1") -> Path:
        fixture = root / "s1" / name
        fixture.mkdir(parents=True)
        metadata = {
            "game": "s1", "trace_profile": "complete_run", "trace_frame_count": len(rows),
            "recorder": "native-bizhawk-headless", "recorder_version": "3.0", "trace_schema": 5,
        } if v5 else {
            "game": "s1", "trace_profile": "complete_run", "trace_frame_count": len(rows),
            "lua_script_version": "credits-retro-1.4", "csv_version": 4,
        }
        (fixture / "metadata.json").write_text(json.dumps(metadata) + "\n")
        csv_text = ",".join(header) + "\n" + "\n".join(",".join(row) for row in rows) + "\n"
        if compressed:
            with gzip.GzipFile(fixture / "physics.csv.gz", "wb", mtime=0) as output:
                output.write(csv_text.encode())
        else:
            (fixture / "physics.csv").write_text(csv_text)
        return fixture

    @staticmethod
    def manifest(run_id: str) -> str:
        return json.dumps({"game": "s1", "run_id": run_id, "recorder": "native-bizhawk-headless",
                           "recorder_version": "3.0", "trace_schema": 5, "segments": [],
                           "transitions": [], "dynamic_art_gap_transitions": []}) + "\n"

    @staticmethod
    def timing(ordinal: int) -> str:
        return json.dumps({"event": "hardware_work_completed", "raw_frame": 0,
                           "boundary": "vint_service", "kind": "kos_module_queue",
                           "ordinal": ordinal, "submission_fingerprint": "sha256:" + "a" * 64}) + "\n"

    @staticmethod
    def snapshot(root: Path) -> dict[Path, bytes]:
        return {path: path.read_bytes() for path in root.rglob("*") if path.is_file()}

    @staticmethod
    def file_report(report: dict[str, object], logical_path: str) -> dict[str, object]:
        return next(item for item in report["files"] if item["logical_path"] == logical_path)


class CreditsRawHostEvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        root = Path(self.temporary_directory.name)
        self.candidate = root / "candidate"
        fixture = self.candidate / "s1" / "00_ghz1_credits_demo_1"
        fixture.mkdir(parents=True)
        candidate_row = ["0"] * 42
        candidate_row[HEADER_42.index("player_x")] = "0001"
        payload = ",".join(HEADER_42) + "\n" + ",".join(candidate_row) + "\n"
        with gzip.GzipFile(fixture / "physics.csv.gz", "wb", mtime=0) as output:
            output.write(payload.encode())
        self.logical_hash = hashlib.sha256(payload.encode()).hexdigest()
        self.report = root / "comparison.json"
        self.report.write_text(json.dumps({"format": "openggf-trace-v5-candidate-comparison-v1", "files": [{
            "logical_path": "s1/credits_00_ghz1/physics.csv",
            "comparison": {"common_field_mismatches": [
                {"row": 0, "column": "x", "predecessor": "0000", "candidate": "0001"}
            ]},
        }]}))
        self.evidence = root / "evidence.json"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_verifier_binds_each_disclosed_first_divergence_to_raw_value_and_candidate_hash(self) -> None:
        self.evidence.write_text(json.dumps(self.document()))

        verify_evidence(self.candidate, self.report, self.evidence)

    def test_verifier_rejects_missing_observation_raw_mismatch_hash_drift_and_artifact_inside_candidate(self) -> None:
        document = self.document()
        cases = []
        missing = self.document(); missing["routes"][0]["observations"] = []; cases.append((missing, "missing evidence"))
        raw = self.document(); raw["routes"][0]["observations"][0]["raw_value"] = "0000"; cases.append((raw, "raw/emitted mismatch"))
        digest = self.document(); digest["routes"][0]["candidate_logical_sha256"] = "0" * 64; cases.append((digest, "hash"))
        for index, (payload, message) in enumerate(cases):
            with self.subTest(message=message):
                path = self.evidence.with_name(f"evidence-{index}.json")
                path.write_text(json.dumps(payload))
                with self.assertRaisesRegex(ValueError, message):
                    verify_evidence(self.candidate, self.report, path)
        inside = self.candidate / "evidence.json"
        inside.write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError, "outside candidate root"):
            verify_evidence(self.candidate, self.report, inside)

    def document(self) -> dict[str, object]:
        return {"format": "openggf-s1-credits-raw-host-evidence-v1", "routes": [{
            "route": "credits_00_ghz1",
            "candidate_payload": "s1/00_ghz1_credits_demo_1/physics.csv",
            "candidate_logical_sha256": self.logical_hash, "observations": [{
                "row": 0, "common_field": "x", "ram_address": "0xFFFFD008",
                "endianness": "big", "raw_value": "0001", "emitted_value": "0001",
            }],
        }]}


if __name__ == "__main__":
    unittest.main()
