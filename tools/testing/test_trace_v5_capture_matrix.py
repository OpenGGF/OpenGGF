"""Focused contracts for the reviewed Task 9 scratch capture matrix."""

from __future__ import annotations

import copy
import hashlib
import tempfile
import unittest
from pathlib import Path

import tools.traces.trace_v5_capture_matrix as capture_matrix
from tools.traces.trace_v5_capture_matrix import (
    MATRIX_DOCUMENT,
    assemble,
    expand_commands,
    load_document,
    preflight,
    verify_freeze,
    verify_movies,
    verify_roms,
)


class TraceV5CaptureMatrixTests(unittest.TestCase):
    def setUp(self) -> None:
        self.document = load_document(MATRIX_DOCUMENT)
        self.repository_root = Path(__file__).resolve().parents[2]

    def test_document_has_exact_reviewed_rows_and_no_legacy_selector_axes(self) -> None:
        self.assertEqual(36, len(self.document["rows"]))
        self.assertEqual(36, len({row["id"] for row in self.document["rows"]}))
        self.assertEqual(
            {"s1-credits-a", "s1-credits-b"},
            {row["id"] for row in self.document["rows"] if row.get("credits")},
        )
        selectors = " ".join(
            selector for row in self.document["rows"] for selector in row["selectors"]
        )
        self.assertNotIn("schema", selectors)
        self.assertNotIn("version", selectors)
        self.assertEqual("s3k-knuckles-superemeralds", self.document["rows"][-1]["id"])

    def test_extraction_document_has_exact_six_reviewed_movies(self) -> None:
        document = load_document(capture_matrix.EXTRACTION_MATRIX_DOCUMENT)

        self.assertEqual(capture_matrix.EXTRACTION_MATRIX_FORMAT, document["format"])
        self.assertEqual(
            [
                ("s1-ghz1", "s1/ghz1_fullrun/ghz1_fullrun.bk2", "dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea"),
                ("s1-emeralds-run", "s1/runs/s1-sonic-complete-withemeralds/sonic1-complete-withemeralds.bk2", "f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b"),
                ("s2-ehz1", "s2/ehz1_fullrun/s2-ehz1.bk2", "db310fa5e70a3cbaca4bafb06d98509894df920e4ab267d3e22db3f530104eed"),
                ("s2-emeralds-run", "s2/runs/s2-sonic-tails-complete-emeralds/sonic-2-sonic-tails-complete-emeralds.bk2", "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5"),
                ("s3k-aiz", "s3k/aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2", "6837de0f67db7eb68f20b6f6df6a2872713a613d8b4dbc804847209c16b56e97"),
                ("s3k-complete", "s3k/_movies/s3k-complete-sonic-tails.bk2", "82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf"),
            ],
            [(row["id"], row["movie"], row["movie_sha256"]) for row in document["rows"]],
        )
        self.assertEqual("extraction-build-test-v1", document["freeze"]["policy"])
        self.assertNotIn("native_artifact", document["freeze"])
        self.assertNotIn("native_test_artifact", document["freeze"])

    def test_hash_preflight_inputs_match_frozen_rom_and_movie_identities(self) -> None:
        verify_roms(self.repository_root, self.document)
        verify_movies(self.repository_root, self.document)

    def test_freeze_diff_uses_immutable_base_after_origin_develop_moves(self) -> None:
        document = copy.deepcopy(self.document)
        document["freeze"]["source_diff_base_commit"] = (
            "36be0aa44e4e1db9d2d586fff984e52ffd4fe053"
        )
        local_artifact = Path(__file__).relative_to(self.repository_root)
        artifact_bytes = (self.repository_root / local_artifact).read_bytes()
        artifact = {
            "path": local_artifact.as_posix(),
            "sha256": hashlib.sha256(artifact_bytes).hexdigest(),
            "size": len(artifact_bytes),
        }
        document["freeze"]["native_artifact"] = artifact
        document["freeze"]["native_test_artifact"] = artifact

        verify_freeze(self.repository_root, document)

    def test_freeze_diff_requires_immutable_base(self) -> None:
        document = copy.deepcopy(self.document)
        document["freeze"].pop("source_diff_base_commit", None)

        with self.assertRaisesRegex(ValueError, "source diff base commit is missing"):
            verify_freeze(self.repository_root, document)

    def test_freeze_diff_rejects_unavailable_immutable_base(self) -> None:
        document = copy.deepcopy(self.document)
        document["freeze"]["source_diff_base_commit"] = "0" * 40

        with self.assertRaisesRegex(ValueError, "source diff base commit is unavailable"):
            verify_freeze(self.repository_root, document)

    def test_historical_freeze_still_enforces_exact_native_artifacts(self) -> None:
        with self.assertRaisesRegex(ValueError, "frozen native_artifact identity mismatch"):
            verify_freeze(self.repository_root, self.document)

    def test_extraction_freeze_uses_immutable_boundary_after_origin_moves(self) -> None:
        capture_matrix.verify_extraction_freeze(
            self.repository_root,
            self._extraction_document(),
            bizhawk_home=self.repository_root,
            build_test_runner=lambda *_: 0,
        )

    def test_extraction_freeze_rejects_wrong_source_diff(self) -> None:
        document = self._extraction_document()
        document["freeze"]["source_commit"] = document["freeze"]["source_diff_base_commit"]

        with self.assertRaisesRegex(ValueError, "extraction source diff hash mismatch"):
            capture_matrix.verify_extraction_freeze(
                self.repository_root,
                document,
                bizhawk_home=self.repository_root,
                build_test_runner=lambda *_: 0,
            )

    def test_extraction_freeze_rejects_wrong_toolchain(self) -> None:
        document = self._extraction_document()
        document["freeze"]["toolchain"]["roslyn_csc_sha256"] = "0" * 64

        with self.assertRaisesRegex(ValueError, "extraction Roslyn compiler SHA-256 mismatch"):
            capture_matrix.verify_extraction_freeze(
                self.repository_root,
                document,
                bizhawk_home=self.repository_root,
                build_test_runner=lambda *_: 0,
            )

    def test_extraction_freeze_rejects_failed_clean_native_build_and_tests(self) -> None:
        with self.assertRaisesRegex(ValueError, "extraction native build/tests failed"):
            capture_matrix.verify_extraction_freeze(
                self.repository_root,
                self._extraction_document(),
                bizhawk_home=self.repository_root,
                build_test_runner=lambda *_: 1,
            )

    def test_extraction_freeze_requires_reviewed_native_test_filters(self) -> None:
        document = self._extraction_document()
        document["freeze"]["native_test_filters"] = []

        with self.assertRaisesRegex(ValueError, "extraction native test filters are missing"):
            capture_matrix.verify_extraction_freeze(
                self.repository_root,
                document,
                bizhawk_home=self.repository_root,
                build_test_runner=lambda *_: 0,
            )

    @staticmethod
    def _extraction_document() -> dict:
        return {
            "freeze": {
                "policy": "extraction-build-test-v1",
                "source_commit": "41828f10998f531e614d855c858ba1b26429d757",
                "source_diff_base_commit": "081167cb9363f989b74d56e7551b3cce37a8017a",
                "source_diff_sha256": "4238071a54cb4e23b2b19b63a05bf6ed57c535f61a0cd18ae9b34cc44be75b90",
                "native_test_filters": [
                    "Bk2Reader",
                    "S1TraceCaptureRunner",
                    "S1RunCaptureRunner",
                    "S2TraceCaptureRunner",
                    "S2RunCaptureRunner records a level-ss-level round trip",
                    "S2RunCaptureRunner publishes mandatory audit manifest",
                    "S3K runner",
                    "S3KCompleteRun",
                    "S3K complete-run",
                    "TraceCli",
                ],
                "toolchain": {
                    "mono_version": "Mono JIT compiler version 6.12.0 (makepkg/0cbf0e290c3 Tue Jun 11 13:06:07 CEST 2024)",
                    "xbuild_version": "XBuild Engine Version 14.0",
                    "roslyn_csc_path": "/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe",
                    "roslyn_csc_sha256": "81e98ade50f3e4127237128211778bd6ebe0c3998c9cc2f5eb44f3196a0297f8",
                    "roslyn_csc_version": "3.9.0-6.21124.20 (db94f4cc)",
                },
            }
        }

    def test_expander_emits_one_literal_trace_command_per_row(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            commands = expand_commands(self.repository_root, Path(temporary), self.document)
        self.assertEqual(36, len(commands))
        for row, command in zip(self.document["rows"], commands):
            self.assertIn("tools/bizhawk-headless/run.sh --mode trace --rom", command)
            self.assertIn(f"captures/{row['id']}", command)
            self.assertNotIn("--trace-schema", command)
            if row["movie"] is None:
                self.assertNotIn("--movie", command)
                self.assertIn("--credits-raw-observations", command)
                self.assertIn(row["credits"]["observation_id"], command)
            else:
                self.assertIn(row["movie"], command)

    def test_preflight_refuses_existing_capture_output_and_candidate(self) -> None:
        document = copy.deepcopy(self.document)
        local_artifact = Path(__file__).relative_to(self.repository_root)
        artifact_bytes = (self.repository_root / local_artifact).read_bytes()
        artifact = {
            "path": local_artifact.as_posix(),
            "sha256": hashlib.sha256(artifact_bytes).hexdigest(),
            "size": len(artifact_bytes),
        }
        document["freeze"]["native_artifact"] = artifact
        document["freeze"]["native_test_artifact"] = artifact
        document["freeze"]["fixture_inventory"]["path"] = (
            "docs/architecture/validation/trace/"
            "2026-08-29-tracechaser-extraction-fixture-inventory.json"
        )
        with tempfile.TemporaryDirectory() as temporary:
            batch = Path(temporary) / "batch"
            candidate = Path(temporary) / "candidate"
            output = batch / "captures" / document["rows"][0]["id"]
            output.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, "capture output must be absent"):
                preflight(self.repository_root, batch, candidate, document, require_capacity=False)
            output.rmdir()
            candidate.mkdir()
            with self.assertRaisesRegex(ValueError, "candidate root must be absent"):
                preflight(self.repository_root, batch, candidate, document, require_capacity=False)

    def test_assembler_copies_static_inputs_and_refuses_replacement(self) -> None:
        row = copy.deepcopy(self.document["rows"][0])
        document = copy.deepcopy(self.document)
        document["rows"] = [row]
        with tempfile.TemporaryDirectory() as temporary:
            batch = Path(temporary) / "batch"
            capture = batch / "captures" / row["id"]
            capture.mkdir(parents=True)
            (capture / "metadata.json").write_text("{}\n", encoding="utf-8")
            candidate = Path(temporary) / "candidate"
            result = assemble(self.repository_root, batch, candidate, document)
            self.assertGreaterEqual(result["copied_files"], 2)
            self.assertEqual("{}\n", (candidate / "s1/ghz1_fullrun/metadata.json").read_text())
            self.assertEqual(
                len(list((self.repository_root / "src/test/resources/traces").rglob("*.bk2"))),
                len(list(candidate.rglob("*.bk2"))),
            )
            with self.assertRaisesRegex(ValueError, "candidate root must be absent"):
                assemble(self.repository_root, batch, candidate, document)


if __name__ == "__main__":
    unittest.main()
