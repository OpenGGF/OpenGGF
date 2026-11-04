#!/usr/bin/env python3
"""Synthetic CLI checks for exact, coverage-aware FrameComparison analysis.

Inputs: small temporary plain/gzip JSONL records; Python standard library only.
Origin: camera/carried-state trace attribution, 2026-11-04.
"""
from __future__ import annotations

import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


TOOL = Path(__file__).with_name("compare_frame_comparisons.py")


def field(name="position", expected="10", actual="10", severity="MATCH", **extra):
    return {
        "fieldName": name,
        "expected": expected,
        "actual": actual,
        "severity": severity,
        "delta": 0,
        "observedMismatch": False,
        "verificationGroup": "PHYSICS",
        **extra,
    }


def row(frame, *fields, **extra):
    return {
        "frame": frame,
        "fields": {item["fieldName"]: item for item in fields},
        "romDiagnostics": "reference context",
        "engineDiagnostics": "candidate context",
        **extra,
    }


class CompareFrameComparisonsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write_rows(self, name, rows, compressed=False):
        path = self.root / name
        opener = gzip.open if compressed else open
        with opener(path, "wt", encoding="utf-8") as stream:
            for item in rows:
                stream.write(json.dumps(item) + "\n")
        return path

    def run_tool(self, baseline, candidate, *extra):
        return subprocess.run(
            [sys.executable, str(TOOL), "--baseline", str(baseline),
             "--candidate", str(candidate), *map(str, extra)],
            text=True, capture_output=True, check=False,
        )

    def compare(self, baseline_rows, candidate_rows):
        baseline = self.write_rows("baseline.jsonl", baseline_rows)
        candidate = self.write_rows("candidate.jsonl", candidate_rows)
        result = self.run_tool(baseline, candidate)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def test_changed_actual_under_existing_error_adds_and_removes_exact_observation(self):
        baseline = row(8, field(actual="11", severity="ERROR"))
        candidate = row(8, field(actual="12", severity="ERROR"), engineDiagnostics="new context")
        report = self.compare([baseline], [candidate])
        delta = report["common_row_error_delta"]
        self.assertEqual((1, 1), (delta["added_count"], delta["removed_count"]))
        self.assertEqual("11", delta["removed"][0]["actual"])
        self.assertEqual("12", delta["added"][0]["actual"])
        self.assertEqual(8, delta["first_added_row"])
        self.assertEqual({"baseline": baseline, "candidate": candidate},
                         delta["first_added_row_snapshots"])
        self.assertEqual([], delta["additions_on_baseline_error_free_rows"])
        self.assertEqual(8, report["baseline"]["first_error_row"])
        self.assertEqual(8, report["candidate"]["first_error_row"])
        difference = report["first_differing_compared_field"]
        self.assertEqual((8, "position"), (difference["row"], difference["field"]))
        self.assertEqual(baseline["fields"]["position"], difference["baseline"])
        self.assertEqual(candidate["fields"]["position"], difference["candidate"])

    def test_repair_removes_error_without_adding_one(self):
        report = self.compare(
            [row(4, field(actual="11", severity="ERROR"))], [row(4, field())])
        delta = report["common_row_error_delta"]
        self.assertEqual((0, 1), (delta["added_count"], delta["removed_count"]))
        self.assertIsNone(delta["first_added_row"])
        self.assertIsNone(delta["first_added_row_snapshots"])
        self.assertEqual(4, report["baseline"]["first_error_row"])
        self.assertIsNone(report["candidate"]["first_error_row"])
        self.assertEqual(0, report["candidate"]["raw_error_count"])

    def test_added_mismatch_on_known_error_free_row_is_explicit(self):
        inherited = row(9, field(actual="15", severity="ERROR"))
        report = self.compare(
            [row(3, field()), inherited],
            [row(3, field(actual="11", severity="ERROR")), inherited])
        delta = report["common_row_error_delta"]
        self.assertEqual(3, delta["first_added_row"])
        clean = delta["additions_on_baseline_error_free_rows"]
        self.assertEqual([3], [item["row"] for item in clean])
        self.assertEqual([], clean[0]["candidate_only_fields"])
        self.assertEqual(["position"], [item["field"] for item in clean[0]["added_errors"]])
        self.assertEqual((1, 2), (report["baseline"]["raw_error_count"],
                                  report["candidate"]["raw_error_count"]))

    def test_all_match_additions_exclude_warnings_observed_mismatches_and_unknown_fields(self):
        baseline = [row(0, field()), row(1, field(actual="11", severity="WARNING")),
                    row(2, field(actual="11", observedMismatch=True)), row(3, field())]
        candidate = [row(i, field(actual="12", severity="ERROR")) for i in range(3)]
        candidate.append(row(3, field(), field("new_field", actual="12", severity="ERROR")))
        delta = self.compare(baseline, candidate)["common_row_error_delta"]
        self.assertEqual([0, 1, 2, 3],
                         [item["row"] for item in delta["additions_on_baseline_error_free_rows"]])
        self.assertEqual([0],
                         [item["row"] for item in delta["additions_on_baseline_all_match_rows"]])

    def test_unmatched_rows_are_coverage_not_added_or_repaired_errors(self):
        unchanged = row(2, field(actual="11", severity="ERROR"))
        report = self.compare(
            [unchanged, row(5, field(actual="12", severity="ERROR"))],
            [unchanged, row(7, field(actual="13", severity="ERROR")), row(8, field())])
        coverage = report["coverage"]
        self.assertEqual([2], coverage["common_rows"])
        self.assertEqual([5], coverage["baseline_only_rows"])
        self.assertEqual([7, 8], coverage["candidate_only_rows"])
        delta = report["common_row_error_delta"]
        self.assertEqual((0, 0), (delta["added_count"], delta["removed_count"]))
        self.assertIsNone(delta["first_added_row"])
        unmatched = report["unmatched_row_errors"]
        self.assertEqual([5], [item["row"] for item in unmatched["baseline_only"]])
        self.assertEqual([7], [item["row"] for item in unmatched["candidate_only"]])
        self.assertTrue(report["analysis_only"])

    def test_group_and_expected_changes_are_part_of_error_identity(self):
        for change in ({"verificationGroup": "ANIMATION"}, {"expected": "9"}):
            with self.subTest(change=change):
                report = self.compare(
                    [row(1, field(actual="11", severity="ERROR"))],
                    [row(1, field(actual="11", severity="ERROR", **change))])
                delta = report["common_row_error_delta"]
                self.assertEqual((1, 1), (delta["added_count"], delta["removed_count"]))

    def test_added_field_is_missing_baseline_coverage_even_on_error_free_row(self):
        report = self.compare(
            [row(2, field())],
            [row(2, field(), field("new_field", actual="11", severity="ERROR"))])
        coverage = report["coverage"]["field_coverage_changes"]
        self.assertEqual([{"row": 2, "baseline_only_fields": [],
                           "candidate_only_fields": ["new_field"]}], coverage)
        clean = report["common_row_error_delta"]["additions_on_baseline_error_free_rows"]
        self.assertEqual(["new_field"], clean[0]["candidate_only_fields"])
        difference = report["first_differing_compared_field"]
        self.assertFalse(difference["baseline_present"])
        self.assertTrue(difference["candidate_present"])
        self.assertIsNone(difference["baseline"])

    def test_raw_mismatches_include_warning_and_ignored_value_differences(self):
        sample = row(1, field("warn", actual="11", severity="WARNING"),
                     field("ignored", actual="12", observedMismatch=True),
                     field("tolerated", actual="13"), field("same"))
        report = self.compare([sample], [sample])
        summary = report["baseline"]
        self.assertEqual(3, summary["raw_mismatch_count"])
        self.assertEqual(0, summary["raw_error_count"])
        self.assertEqual(1, summary["raw_warning_count"])
        self.assertEqual(1, summary["observed_mismatch_count"])
        self.assertIsNone(report["first_differing_compared_field"])

    def test_gaps_are_reported_without_inventing_observations(self):
        records = [row(5, field(actual="11", severity="ERROR")), row(1, field())]
        report = self.compare(records, records)
        coverage = report["coverage"]
        self.assertEqual([1, 5], coverage["common_rows"])
        self.assertEqual([{"after_row": 1, "before_row": 5, "missing_id_count": 3}],
                         coverage["baseline_internal_gaps"])
        self.assertEqual(2, report["baseline"]["row_count"])
        self.assertEqual(1, report["baseline"]["raw_error_count"])
        self.assertEqual(5, report["baseline"]["first_error_row"])

    def test_empty_baseline_does_not_certify_candidate_rows(self):
        report = self.compare([], [row(0, field(actual="11", severity="ERROR"))])
        self.assertEqual([], report["coverage"]["common_rows"])
        self.assertEqual([0], report["coverage"]["candidate_only_rows"])
        self.assertEqual(0, report["common_row_error_delta"]["added_count"])
        self.assertEqual(1, len(report["unmatched_row_errors"]["candidate_only"]))

    def test_duplicate_frames_are_rejected_without_writing_report(self):
        baseline = self.write_rows("baseline.jsonl", [row(8, field()), row(8, field())])
        candidate = self.write_rows("candidate.jsonl", [row(8, field())])
        output = self.root / "report.json"
        output.write_text("existing report", encoding="utf-8")
        result = self.run_tool(baseline, candidate, "--output", output)
        self.assertEqual(2, result.returncode)
        self.assertIn("duplicate frame 8", result.stderr)
        self.assertIn(":2:", result.stderr)
        self.assertIn("line 1", result.stderr)
        self.assertEqual("existing report", output.read_text(encoding="utf-8"))
        self.assertEqual("", result.stdout)

    def test_malformed_records_and_frame_ids_are_rejected(self):
        invalid = [[], {}, row(True), row(1.5), row(-1),
                   row(0, fields=[]), row(0, romDiagnostics=[]),
                   row(0, field(actual=1)), row(0, field(severity="UNKNOWN")),
                   row(0, field(verificationGroup=None)),
                   row(0, field(observedMismatch="true")),
                   row(0, fields={"position": {"expected": "10", "severity": "ERROR",
                                               "verificationGroup": "PHYSICS"}})]
        for item in invalid:
            with self.subTest(item=item):
                baseline = self.write_rows("baseline.jsonl", [item])
                candidate = self.write_rows("candidate.jsonl", [row(0, field())])
                result = self.run_tool(baseline, candidate)
                self.assertEqual(2, result.returncode)
                self.assertIn("baseline.jsonl:1:", result.stderr)
                self.assertEqual("", result.stdout)

    def test_invalid_json_and_duplicate_json_members_are_rejected(self):
        candidate = self.write_rows("candidate.jsonl", [row(0, field())])
        baseline = self.root / "baseline.jsonl"
        for text in ('{broken}\n', '\n', '{"frame":0,"frame":1}\n'):
            with self.subTest(text=text):
                baseline.write_text(text, encoding="utf-8")
                result = self.run_tool(baseline, candidate)
                self.assertEqual(2, result.returncode)
                self.assertIn("baseline.jsonl:1:", result.stderr)
                self.assertEqual("", result.stdout)

    def test_corrupt_or_truncated_gzip_is_reported_as_input_error(self):
        baseline = self.root / "baseline.jsonl.gz"
        candidate = self.write_rows("candidate.jsonl", [row(0, field())])
        damaged = [bytes.fromhex("1f8b08000000000002ff07"),
                   gzip.compress((json.dumps(row(0, field())) + "\n").encode())[:-2]]
        for content in damaged:
            with self.subTest(content=content):
                baseline.write_bytes(content)
                result = self.run_tool(baseline, candidate)
                self.assertEqual(2, result.returncode)
                self.assertIn("baseline.jsonl.gz:", result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertEqual("", result.stdout)

    def test_output_cannot_overwrite_input(self):
        baseline = self.write_rows("baseline.jsonl", [row(0, field())])
        candidate = self.write_rows("candidate.jsonl", [row(0, field())])
        original = baseline.read_bytes()
        result = self.run_tool(baseline, candidate, "--output", baseline)
        self.assertEqual(2, result.returncode)
        self.assertIn("must not overwrite either input", result.stderr)
        self.assertEqual(original, baseline.read_bytes())

    def test_gzip_and_plain_inputs_produce_equivalent_analysis_and_output_option(self):
        baseline_rows = [row(0, field()), row(1, field(actual="11", severity="ERROR"))]
        candidate_rows = [row(0, field()), row(1, field())]
        baseline = self.write_rows("baseline.jsonl", baseline_rows)
        candidate = self.write_rows("candidate.jsonl", candidate_rows)
        plain = self.run_tool(baseline, candidate)
        self.assertEqual(0, plain.returncode, plain.stderr)
        compressed_baseline = self.write_rows("baseline.data", baseline_rows, compressed=True)
        compressed_candidate = self.write_rows("candidate.data", candidate_rows, compressed=True)
        output = self.root / "report.json"
        compressed = self.run_tool(compressed_baseline, compressed_candidate, "--output", output)
        self.assertEqual(0, compressed.returncode, compressed.stderr)
        self.assertEqual("", compressed.stdout)
        plain_report = json.loads(plain.stdout)
        compressed_report = json.loads(output.read_text(encoding="utf-8"))
        plain_report.pop("inputs")
        compressed_report.pop("inputs")
        self.assertEqual(plain_report, compressed_report)


if __name__ == "__main__":
    unittest.main()
