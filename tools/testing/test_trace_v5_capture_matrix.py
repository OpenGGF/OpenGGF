"""Focused contracts for the reviewed Task 9 scratch capture matrix."""

from __future__ import annotations

import copy
import tempfile
import unittest
from pathlib import Path

from tools.traces.trace_v5_capture_matrix import (
    MATRIX_DOCUMENT,
    assemble,
    expand_commands,
    load_document,
    preflight,
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

    def test_hash_preflight_inputs_match_frozen_rom_and_movie_identities(self) -> None:
        verify_roms(self.repository_root, self.document)
        verify_movies(self.repository_root, self.document)

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
        with tempfile.TemporaryDirectory() as temporary:
            batch = Path(temporary) / "batch"
            candidate = Path(temporary) / "candidate"
            output = batch / "captures" / self.document["rows"][0]["id"]
            output.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, "capture output must be absent"):
                preflight(self.repository_root, batch, candidate, self.document, require_capacity=False)
            output.rmdir()
            candidate.mkdir()
            with self.assertRaisesRegex(ValueError, "candidate root must be absent"):
                preflight(self.repository_root, batch, candidate, self.document, require_capacity=False)

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
