"""Contract tests for read-only v5 candidate comparison and credits evidence."""

from __future__ import annotations

import gzip
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.traces.compare_trace_v5_candidates import compare_roots
from tools.traces.s1_credits_raw_evidence import require_outside_roots


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
EVIDENCE_BUILDER = Path(__file__).resolve().parents[1] / "traces" / "build_s1_credits_raw_host_evidence.py"
EVIDENCE_VERIFIER = Path(__file__).resolve().parents[1] / "traces" / "verify_s1_credits_raw_host_evidence.py"
REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
S3K_V5_DOCUMENTS = (
    "s3k-trace-recorder-behavior.md",
    "s3k-complete-run-behavior.md",
    "s3k-run-publication.md",
    "s3k-aux-events.md",
    "s3k-profiles-and-hooks.md",
    "s3k-completerun-profiles.md",
)
S1_S2_V5_DOCUMENTS = (
    "s1-trace-recorder-behavior.md",
    "s1-complete-run-behavior.md",
    "s1-run-mode-behavior.md",
    "s2-trace-recorder-behavior.md",
    "s2-run-mode-behavior.md",
)


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

    def test_aux_comparison_explains_same_event_count_payload_changes(self) -> None:
        self.write_fixture(self.old, HEADER_42, [["0"] * 42])
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])
        old_fixture = self.old / "s1" / "credits_00_ghz1"
        candidate_fixture = self.candidate / "s1" / "credits_00_ghz1"
        (old_fixture / "aux_state.jsonl").write_text('{"event":"state","x":1}\n')
        (candidate_fixture / "aux_state.jsonl").write_text('{"event":"state","x":2}\n')

        report = compare_roots(self.old, self.candidate)

        comparison = self.file_report(
            report, "s1/credits_00_ghz1/aux_state.jsonl")["comparison"]
        self.assertEqual({}, comparison["event_count_deltas"])
        self.assertEqual(1, comparison["literal_delta_count"])
        self.assertEqual(1, len(comparison["literal_deltas"]))
        delta = comparison["literal_deltas"][0]
        self.assertEqual("replace", delta["tag"])
        self.assertEqual((0, 1), (delta["predecessor_start"], delta["predecessor_end"]))
        self.assertEqual((0, 1), (delta["candidate_start"], delta["candidate_end"]))
        self.assertEqual(['{"event":"state","x":1}'], delta["predecessor_lines"])
        self.assertEqual(['{"event":"state","x":2}'], delta["candidate_lines"])
        self.assertFalse(delta["lines_truncated"])
        self.assertFalse(comparison["literal_deltas_truncated"])

    def test_aux_literal_explanations_are_deterministically_bounded(self) -> None:
        self.write_fixture(self.old, HEADER_42, [["0"] * 42])
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])
        old_fixture = self.old / "s1" / "credits_00_ghz1"
        candidate_fixture = self.candidate / "s1" / "credits_00_ghz1"
        old_lines = [json.dumps({"event": "state", "index": index, "payload": "a" * 2048})
                     for index in range(66)]
        new_lines = [line if index % 2 else line.replace('"a', '"b', 1)
                     for index, line in enumerate(old_lines)]
        (old_fixture / "aux_state.jsonl").write_text("\n".join(old_lines) + "\n")
        (candidate_fixture / "aux_state.jsonl").write_text("\n".join(new_lines) + "\n")

        comparison = self.file_report(
            compare_roots(self.old, self.candidate),
            "s1/credits_00_ghz1/aux_state.jsonl")["comparison"]

        self.assertGreater(comparison["literal_delta_count"], 32)
        self.assertEqual(32, len(comparison["literal_deltas"]))
        self.assertTrue(comparison["literal_deltas_truncated"])
        for delta in comparison["literal_deltas"]:
            self.assertLessEqual(len(delta["predecessor_lines"]), 8)
            self.assertLessEqual(len(delta["candidate_lines"]), 8)
            for line in delta["predecessor_lines"] + delta["candidate_lines"]:
                self.assertLessEqual(len(line), 512)
            self.assertRegex(delta["predecessor_sha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(delta["candidate_sha256"], r"^[0-9a-f]{64}$")

    def test_v5_mode_rejects_legacy_envelopes_without_treating_scanning_as_acceptance(self) -> None:
        self.write_fixture(self.old, HEADER_20, [["0"] * 20], v5=False)
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])

        with self.assertRaisesRegex(ValueError, "predecessor root is not v5"):
            compare_roots(self.old, self.candidate)

        report = compare_roots(self.old, self.candidate, mode="credits-20-to-42")
        self.assertIn("lua_script_version", report["predecessor_scan"]["legacy_keys"])
        self.assertEqual([20], report["predecessor_scan"]["physics_widths"])

    def test_rejects_empty_or_arbitrary_candidate_roots_in_both_modes(self) -> None:
        self.write_fixture(self.old, HEADER_20, [["0"] * 20], v5=False)
        for name, content in (("empty", None), ("arbitrary", "not a trace\n")):
            with self.subTest(name=name):
                root = Path(self.temporary_directory.name) / name
                root.mkdir()
                if content is not None:
                    (root / "notes.txt").write_text(content)
                with self.assertRaisesRegex(ValueError, "candidate root is not v5"):
                    compare_roots(self.old, root, mode="credits-20-to-42")

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

    def test_cli_refuses_to_replace_an_existing_frozen_report(self) -> None:
        self.write_fixture(self.old, HEADER_42, [["0"] * 42])
        self.write_fixture(self.candidate, HEADER_42, [["0"] * 42])
        output = Path(self.temporary_directory.name) / "comparison.json"
        output.write_text("frozen\n")

        result = subprocess.run(
            [sys.executable, str(COMPARATOR), str(self.old), str(self.candidate),
             "--output", str(output)], text=True, capture_output=True, check=False)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual("frozen\n", output.read_text())

    def test_s3k_current_contract_documentation_has_only_the_v5_axes(self) -> None:
        docs = REPOSITORY_ROOT / "tools" / "bizhawk-headless" / "docs"
        for name in S3K_V5_DOCUMENTS:
            with self.subTest(document=name):
                document = (docs / name).read_text()
                self.assertIn("## Pre-v5 historical evidence", document)
                current = document.split("## Pre-v5 historical evidence", maxsplit=1)[0]
                self.assertIn('`recorder: native-bizhawk-headless`', current)
                self.assertIn('`recorder_version: 3.0`', current)
                self.assertIn('`trace_schema: 5`', current)
                self.assertRegex(current, r"one\s+module-plus-direct\s+timing grammar")
                for legacy_axis in (
                    "lua_script_version", "LUA_SCRIPT_VERSION", "csv_version",
                    "hardware_timing_schema", "trace_schema: 7", "trace_schema 7",
                    "schema 1", "schema 2", "schema-1", "schema-2",
                    "Lua is the behavioral authority", "Lua is authoritative",
                ):
                    self.assertNotIn(legacy_axis, current)
                self.assertIsNone(re.search(r"\b(?:v)?6\.\d", current))

    def test_s1_s2_current_contract_documentation_is_strict_v5(self) -> None:
        docs = REPOSITORY_ROOT / "tools" / "bizhawk-headless" / "docs"
        for name in S1_S2_V5_DOCUMENTS:
            with self.subTest(document=name):
                document = (docs / name).read_text()
                self.assertIn("## Pre-v5 historical evidence", document)
                current = document.split("## Pre-v5 historical evidence", maxsplit=1)[0]
                self.assertIn('`recorder: native-bizhawk-headless`', current)
                self.assertIn('`recorder_version: 3.0`', current)
                self.assertIn('`trace_schema: 5`', current)
                self.assertIn("are absent", current)
                for predecessor_claim in (
                    "trace_schema: 4", "trace_schema 4", "trace_schema: 9",
                    "trace_schema 9", "CSV v7", "ss_csv_version 1",
                    "Lua is the behavioral authority", "Lua is authoritative",
                ):
                    self.assertNotIn(predecessor_claim, current)

    def test_bizhawk_readme_s1_s2_live_contract_is_native_v5_only(self) -> None:
        readme = (REPOSITORY_ROOT / "tools" / "bizhawk" / "README.md").read_text()
        preamble = readme[:readme.index("## Native S1/S2 v5 capture contract")]
        self.assertIn("predecessor Lua support", preamble)
        self.assertNotIn("*_csv_version", preamble)
        live = readme[readme.index("## Native S1/S2 v5 capture contract"):
                      readme.index("## Pre-v5 historical evidence: S1/S2")]
        for required in (
            "tools/bizhawk-headless/run.sh", "trace_schema: 5",
            "recorder: native-bizhawk-headless", "recorder_version: 3.0",
            "dynamic_art_transfer_state_per_frame", "are absent",
        ):
            self.assertIn(required, live)
        for predecessor_claim in (
            "trace_schema 4", "trace_schema 9", "schema v3", "schema v8",
            "3.5", "9.13-s2", "dynamic_art_transfer_state_per_frame_v1",
        ):
            self.assertNotIn(predecessor_claim, live)

    def test_trace_skills_name_the_current_dynamic_art_capability(self) -> None:
        for skill in ("trace-capture", "trace-green-fleet"):
            agent = (REPOSITORY_ROOT / ".agents" / "skills" / skill / "SKILL.md").read_text()
            claude = (REPOSITORY_ROOT / ".claude" / "skills" / skill / "SKILL.md").read_text()
            with self.subTest(skill=skill):
                self.assertEqual(agent, claude)
                self.assertIn("dynamic_art_transfer_state_per_frame", agent)
                self.assertNotIn("dynamic_art_transfer_state_per_frame_v1", agent)

    def test_bootstrap_snapshot_javadoc_names_the_semantic_v5_capability(self) -> None:
        source = (REPOSITORY_ROOT / "src" / "test" / "java" / "com" / "openggf" / "tests"
                  / "trace" / "AbstractTraceReplayTest.java").read_text()
        paragraph = source[source.index("Capture a read-only snapshot"):source.index(
            "private EngineSnapshot captureEngineSnapshot")]
        self.assertIn("native_prelude_bootstrap", paragraph)
        self.assertNotIn("lua_script_version", paragraph)

    def test_fixture_root_authority_guard_compares_paths_without_platform_separators(self) -> None:
        source = (REPOSITORY_ROOT / "src" / "test" / "java" / "com" / "openggf" / "tests"
                  / "trace" / "TestTraceFixtureRootOverride.java").read_text()
        self.assertIn(".map(Path::normalize)", source)
        self.assertNotIn(".map(Path::toString)", source)

    def test_bizhawk_readme_s3k_live_sections_use_only_v5(self) -> None:
        readme = (REPOSITORY_ROOT / "tools" / "bizhawk" / "README.md").read_text()
        sections = (
            readme[readme.index("#### S3K hardware-timing stream"):readme.index(
                "**Deferred: hook-driven aux families.")],
            readme[readme.index("### Sonic 3 & Knuckles complete-run and run mode"):readme.index(
                "#### Pre-v5 historical capture notes")],
        )
        for section in sections:
            self.assertIn("trace_schema: 5", section)
            self.assertIn("recorder_version: 3.0", section)
            self.assertIn("module-plus-direct", section)
            for legacy in ("hardware_timing_schema", "trace schema 7", "schema-1",
                           "schema-2", "6.40", "6.41", "6.42"):
                self.assertNotIn(legacy, section)
            self.assertIsNone(re.search(r"\b(?:v)?6\.\d", section))

    def test_bizhawk_readme_round_trip_publication_is_native_v5_only(self) -> None:
        readme = (REPOSITORY_ROOT / "tools" / "bizhawk" / "README.md").read_text()
        self.assertEqual(3, readme.count("## Pre-v5 historical capture notes: S3K"))
        live = readme[readme.index("## Recording S3K Round-Trip Traces (Native v5)"):
                      readme.index("## Pre-v5 historical capture notes: S3K Bonus")]
        for required in (
            "tools/bizhawk-headless/run.sh", "trace_schema: 5",
            "recorder_version: 3.0", "validate_trace_v5.py",
            "trace-v5-publication.md", "scratch",
        ):
            self.assertIn(required, live)
        for forbidden in (
            "run_bizhawk_lua", "s3k_complete_run_recorder.lua",
            "ss_csv_version", "src/test/resources/traces/s3k/bonus_",
            "src/test/resources/traces/s3k/runs/", "gzip compression is applied at commit time",
        ):
            self.assertNotIn(forbidden, live)

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


class CreditsRawHostEvidencePipelineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        root = Path(self.temporary_directory.name)
        self.predecessor = root / "predecessor"
        self.candidate = root / "candidate"
        self.predecessor.mkdir()
        self.candidate.mkdir()
        self.logical_hash_by_route: dict[str, str] = {}
        for route, candidate_directory in (
                ("credits_00_ghz1", "00_ghz1_credits_demo_1"),
                ("credits_01_mz2", "01_mz2_credits_demo"),
                ("credits_02_syz3", "02_syz3_credits_demo"),
                ("credits_03_lz3", "03_lz3_credits_demo"),
                ("credits_04_slz3", "04_slz3_credits_demo"),
                ("credits_05_sbz1", "05_sbz1_credits_demo"),
                ("credits_06_sbz2", "06_sbz2_credits_demo"),
                ("credits_07_ghz1b", "07_ghz1_credits_demo_2"),
        ):
            old_fixture = self.predecessor / "s1" / route
            old_fixture.mkdir(parents=True)
            old_row = ["0000"] * 20
            (old_fixture / "metadata.json").write_text(json.dumps({
                "game": "s1", "trace_profile": "credits_demo", "trace_frame_count": 1,
                "lua_script_version": "credits-retro-1.4", "csv_version": 4,
            }) + "\n")
            (old_fixture / "physics.csv").write_text(
                ",".join(HEADER_20) + "\n" + ",".join(old_row) + "\n")

            fixture = self.candidate / "s1" / candidate_directory
            fixture.mkdir(parents=True)
            candidate_row = ["0000"] * 42
            candidate_row[HEADER_42.index("gameplay_frame_counter")] = "0001"
            payload = ",".join(HEADER_42) + "\n" + ",".join(candidate_row) + "\n"
            with gzip.GzipFile(fixture / "physics.csv.gz", "wb", mtime=0) as output:
                output.write(payload.encode())
            (fixture / "metadata.json").write_text(json.dumps({
                "game": "s1", "trace_profile": "credits_demo", "trace_type": "credits_demo",
                "trace_frame_count": 1, "recorder": "native-bizhawk-headless",
                "recorder_version": "3.0", "trace_schema": 5,
            }) + "\n")
            self.logical_hash_by_route[route] = hashlib.sha256(payload.encode()).hexdigest()
        self.report = root / "comparison.json"
        self.report_document = compare_roots(
            self.predecessor, self.candidate, mode="credits-20-to-42")
        self.report.write_text(json.dumps(self.report_document))
        self.raw_sidecar = root / "raw-observations.jsonl"
        self.write_raw_sidecar(self.raw_sidecar)
        self.evidence = root / "raw-host-evidence.json"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_builder_and_verifier_recompute_disclosed_divergences_from_all_independent_inputs(self) -> None:
        built = self.run_builder()
        self.assertEqual(0, built.returncode, built.stderr)
        document = json.loads(self.evidence.read_text())
        self.assertEqual("openggf-s1-credits-raw-host-evidence-v1", document["format"])
        self.assertEqual(8, len(document["routes"]))
        self.assertTrue(all(route["observations"][0]["common_field"] == "v_framecount"
                            for route in document["routes"]))

        verified = self.run_verifier()
        self.assertEqual(0, verified.returncode, verified.stderr)

    def test_pipeline_rejects_missing_duplicate_reordered_fabricated_and_swapped_observations(self) -> None:
        documents = [json.loads(line) for line in self.raw_sidecar.read_text().splitlines()]
        mutations = {
            "missing": documents[:2] + documents[3:],
            "duplicate": documents[:-1] + [documents[1]] + documents[-1:],
            "reordered": documents[:1] + [documents[2], documents[1]] + documents[3:],
        }
        fabricated = json.loads(json.dumps(documents))
        fabricated[19].pop("ram_address", None)
        fabricated[19].pop("endianness", None)
        fabricated[19]["derivation"] = "invented_from_candidate_csv"
        mutations["fabricated"] = fabricated
        wrong_width = json.loads(json.dumps(documents))
        wrong_width[1]["raw_value"] = "00"
        mutations["wrong_width"] = wrong_width
        swapped = json.loads(json.dumps(documents))
        swapped[0]["candidate_root"] = str(self.candidate.parent / "other-candidate")
        swapped[-1]["candidate_root"] = swapped[0]["candidate_root"]
        mutations["swapped"] = swapped

        for label, mutated_documents in mutations.items():
            with self.subTest(label=label):
                path = self.raw_sidecar.with_name(f"raw-{label}.jsonl")
                self.write_documents(path, mutated_documents)
                result = self.run_builder(raw_sidecar=path,
                                          evidence=self.evidence.with_name(f"evidence-{label}.json"))
                self.assertNotEqual(0, result.returncode)

    def test_pipeline_rejects_truncation_report_drift_path_overlap_and_existing_output(self) -> None:
        truncated = self.raw_sidecar.with_name("raw-truncated.jsonl")
        truncated.write_text("\n".join(self.raw_sidecar.read_text().splitlines()[:-1]) + "\n")
        self.assertNotEqual(0, self.run_builder(raw_sidecar=truncated).returncode)

        drifted = json.loads(self.report.read_text())
        drifted["files"][0]["candidate"]["logical_sha256"] = "0" * 64
        drifted_report = self.report.with_name("comparison-drifted.json")
        drifted_report.write_text(json.dumps(drifted))
        self.assertNotEqual(0, self.run_builder(report=drifted_report).returncode)

        inside = self.candidate / "evidence.json"
        self.assertNotEqual(0, self.run_builder(evidence=inside).returncode)
        self.assertFalse(inside.exists())

        self.evidence.write_text("frozen\n")
        result = self.run_builder()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("frozen\n", self.evidence.read_text())

    def test_pipeline_rejects_symlink_aliases_swapped_capture_and_fabricated_final_evidence(self) -> None:
        raw_alias = self.raw_sidecar.with_name("raw-alias.jsonl")
        raw_alias.symlink_to(self.candidate / "s1" / "00_ghz1_credits_demo_1" / "metadata.json")
        self.assertNotEqual(0, self.run_builder(raw_sidecar=raw_alias).returncode)
        escaped_alias = self.candidate / "raw-observations-outside-alias.jsonl"
        escaped_alias.symlink_to(self.raw_sidecar)
        with self.assertRaisesRegex(ValueError, "outside"):
            require_outside_roots(
                escaped_alias, self.predecessor.resolve(), self.candidate.resolve(), "raw sidecar")
        escaped_alias.unlink()

        sidecar_b = self.raw_sidecar.with_name("raw-capture-b.jsonl")
        documents = [json.loads(line) for line in self.raw_sidecar.read_text().splitlines()]
        documents[0]["capture_id"] = "capture-b"
        documents[-1]["capture_id"] = "capture-b"
        self.write_documents(sidecar_b, documents)
        evidence_b = self.evidence.with_name("evidence-b.json")
        self.assertEqual(0, self.run_builder(raw_sidecar=sidecar_b, evidence=evidence_b).returncode)
        swapped = subprocess.run([
            sys.executable, str(EVIDENCE_VERIFIER), str(self.predecessor), str(self.candidate),
            str(self.report), str(self.raw_sidecar), str(evidence_b),
        ], text=True, capture_output=True, check=False)
        self.assertNotEqual(0, swapped.returncode)

        self.assertEqual(0, self.run_builder().returncode)
        fabricated = json.loads(self.evidence.read_text())
        fabricated["routes"][0]["observations"][0]["raw_value"] = "0000"
        self.evidence.write_text(json.dumps(fabricated))
        self.assertNotEqual(0, self.run_verifier().returncode)

    def write_raw_sidecar(self, path: Path) -> None:
        candidate_root = str(self.candidate.resolve())
        documents: list[dict[str, object]] = [{
            "record_type": "header",
            "format": "openggf-s1-credits-raw-observations-v1",
            "capture_id": "capture-a",
            "candidate_root": candidate_root,
            "rom_sha1": "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b",
            "recorder": "native-bizhawk-headless",
            "recorder_version": "3.0",
        }]
        field_sources = {
            "frame": {"derivation": "trace_row_ordinal"},
            "input": {"derivation": "s1_rom_controller_mask"},
            "x": {"ram_address": "0xFFFFD008", "endianness": "big"},
            "y": {"ram_address": "0xFFFFD00C", "endianness": "big"},
            "x_speed": {"ram_address": "0xFFFFD010", "endianness": "big"},
            "y_speed": {"ram_address": "0xFFFFD012", "endianness": "big"},
            "g_speed": {"ram_address": "0xFFFFD014", "endianness": "big"},
            "angle": {"ram_address": "0xFFFFD026", "endianness": "byte"},
            "air": {"derivation": "s1_status_air_bit"},
            "rolling": {"derivation": "s1_status_rolling_bit"},
            "ground_mode": {"derivation": "s1_ground_mode"},
            "x_sub": {"ram_address": "0xFFFFD00A", "endianness": "big"},
            "y_sub": {"ram_address": "0xFFFFD00E", "endianness": "big"},
            "routine": {"ram_address": "0xFFFFD024", "endianness": "byte"},
            "camera_x": {"ram_address": "0xFFFFF700", "endianness": "big"},
            "camera_y": {"ram_address": "0xFFFFF704", "endianness": "big"},
            "rings": {"ram_address": "0xFFFFFE20", "endianness": "big"},
            "status_byte": {"ram_address": "0xFFFFD022", "endianness": "byte"},
            "v_framecount": {"ram_address": "0xFFFFFE04", "endianness": "big"},
            "stand_on_obj": {"ram_address": "0xFFFFD03D", "endianness": "byte"},
        }
        routes = list(self.logical_hash_by_route)
        for demo_index, route in enumerate(routes):
            candidate_directory = {
                "credits_00_ghz1": "00_ghz1_credits_demo_1",
                "credits_01_mz2": "01_mz2_credits_demo",
                "credits_02_syz3": "02_syz3_credits_demo",
                "credits_03_lz3": "03_lz3_credits_demo",
                "credits_04_slz3": "04_slz3_credits_demo",
                "credits_05_sbz1": "05_sbz1_credits_demo",
                "credits_06_sbz2": "06_sbz2_credits_demo",
                "credits_07_ghz1b": "07_ghz1_credits_demo_2",
            }[route]
            for field in HEADER_20:
                if field in {"air", "rolling", "ground_mode"}:
                    value = "0"
                elif field in {"angle", "routine", "status_byte", "stand_on_obj"}:
                    value = "00"
                else:
                    value = "0001" if field == "v_framecount" else "0000"
                documents.append({
                    "record_type": "observation", "demo_index": demo_index,
                    "route": route, "candidate_directory": candidate_directory,
                    "row": 0, "common_field": field, **field_sources[field],
                    "raw_value": value,
                })
        preceding = "".join(json.dumps(item, sort_keys=True, separators=(",", ":")) + "\n"
                            for item in documents).encode()
        completion = {
            "record_type": "completion", "capture_id": "capture-a",
            "candidate_root": candidate_root, "all_eight_complete": True,
            "route_rows": {route: 1 for route in routes}, "total_rows": 8,
            "observation_count": 160, "preceding_byte_count": len(preceding),
            "preceding_sha256": hashlib.sha256(preceding).hexdigest(),
        }
        path.write_bytes(preceding + (
            json.dumps(completion, sort_keys=True, separators=(",", ":")) + "\n").encode())

    @staticmethod
    def write_documents(path: Path, documents: list[dict[str, object]]) -> None:
        completion = documents[-1]
        preceding = "".join(
            json.dumps(item, sort_keys=True, separators=(",", ":")) + "\n"
            for item in documents[:-1]).encode()
        completion["observation_count"] = len(documents) - 2
        completion["preceding_byte_count"] = len(preceding)
        completion["preceding_sha256"] = hashlib.sha256(preceding).hexdigest()
        path.write_bytes(preceding + (
            json.dumps(completion, sort_keys=True, separators=(",", ":")) + "\n").encode())

    def run_builder(self, *, raw_sidecar: Path | None = None, report: Path | None = None,
                    evidence: Path | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run([
            sys.executable, str(EVIDENCE_BUILDER), str(self.predecessor), str(self.candidate),
            str(report or self.report), str(raw_sidecar or self.raw_sidecar),
            str(evidence or self.evidence),
        ], text=True, capture_output=True, check=False)

    def run_verifier(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run([
            sys.executable, str(EVIDENCE_VERIFIER), str(self.predecessor), str(self.candidate),
            str(self.report), str(self.raw_sidecar), str(self.evidence),
        ], text=True, capture_output=True, check=False)


if __name__ == "__main__":
    unittest.main()
