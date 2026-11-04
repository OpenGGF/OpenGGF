#!/usr/bin/env python3
"""Compare exact serialized FrameComparison observations, without span expansion.

Purpose: expose changed ERROR values and coverage gaps hidden by grouped reports.
Inputs: explicit --baseline and --candidate UTF-8 JSONL paths, plain or gzip
(detected by magic bytes); optional --output JSON path, otherwise stdout.
Each line is a FrameComparison object with a nonnegative integer frame, fields
map, and string/null romDiagnostics and engineDiagnostics. Compared fields need
expected/actual string or null, severity, and verificationGroup; extra serialized
members are retained in snapshots. Duplicate frame IDs or JSON members and
malformed records are rejected, never overwritten or silently skipped.
Origin: camera/carried-state exact field-row attribution, 2026-11-04; generalized
from the task's diff-attribution.py and LaneComparisonDump.java observer.

Only common rows contribute added/removed ERROR tuples, keyed by row, field,
expected, actual and verificationGroup. Unmatched-row errors stay separate;
missing rows/fields do not imply baseline agreement. Raw mismatch counts use
serialized expected != actual, independently of producer-supplied severity.
The first addition includes both complete source records, including diagnostics.
This is comparison-only analysis: it neither approves parity nor attributes a
cause, and cannot establish that captures share inputs, selection or environment.
Exit 0 means the analysis completed; invalid/unreadable inputs exit 2.

Example:
    python3 tools/traces/compare_frame_comparisons.py \
        --baseline baseline.jsonl.gz --candidate candidate.jsonl \
        --output comparison.json
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import gzip
import json
from pathlib import Path
import sys
import zlib


class InputError(ValueError):
    """An input is not an unambiguous serialized FrameComparison stream."""


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InputError(f"duplicate JSON member {key!r}")
        result[key] = value
    return result


def _reject_constant(value):
    raise InputError(f"non-JSON numeric constant {value!r}")


def _validate_record(record):
    if not isinstance(record, dict):
        raise InputError("record must be a JSON object")
    frame = record.get("frame")
    if type(frame) is not int or frame < 0:
        raise InputError("frame must be a nonnegative integer (not a boolean)")
    if not isinstance(record.get("fields"), dict):
        raise InputError("fields must be an object")
    for name in ("romDiagnostics", "engineDiagnostics"):
        if name not in record or (record[name] is not None and not isinstance(record[name], str)):
            raise InputError(f"{name} must be present and a string or null")
    for name, compared in record["fields"].items():
        if not name or not isinstance(compared, dict):
            raise InputError(f"field {name!r} must have a nonempty name and object value")
        for key in ("expected", "actual", "severity", "verificationGroup"):
            if key not in compared:
                raise InputError(f"field {name!r} is missing {key}")
        for key in ("expected", "actual"):
            if compared[key] is not None and not isinstance(compared[key], str):
                raise InputError(f"field {name!r} {key} must be a string or null")
        if compared["severity"] not in ("MATCH", "WARNING", "ERROR"):
            raise InputError(f"field {name!r} has an unknown severity")
        group = compared["verificationGroup"]
        if not isinstance(group, str) or not group:
            raise InputError(f"field {name!r} verificationGroup must be a nonempty string")
        if "fieldName" in compared and compared["fieldName"] != name:
            raise InputError(f"field {name!r} disagrees with its fieldName")
        if "observedMismatch" in compared and type(compared["observedMismatch"]) is not bool:
            raise InputError(f"field {name!r} observedMismatch must be a boolean")
        if "delta" in compared and type(compared["delta"]) is not int:
            raise InputError(f"field {name!r} delta must be an integer")


def read_rows(path):
    """Read actual rows by frame ID, rejecting ambiguous or malformed records."""
    path = Path(path)
    rows = {}
    first_lines = {}
    try:
        with path.open("rb") as stream:
            compressed = stream.read(2) == b"\x1f\x8b"
        opener = gzip.open if compressed else open
        with opener(path, "rt", encoding="utf-8") as stream:
            for number, line in enumerate(stream, 1):
                try:
                    record = json.loads(line, object_pairs_hook=_unique_object,
                                        parse_constant=_reject_constant)
                    _validate_record(record)
                except ValueError as error:
                    raise InputError(f"{path}:{number}: {error}") from error
                frame = record["frame"]
                if frame in rows:
                    raise InputError(
                        f"{path}:{number}: duplicate frame {frame} "
                        f"(first seen on line {first_lines[frame]})")
                rows[frame] = record
                first_lines[frame] = number
    except (OSError, UnicodeError, EOFError, zlib.error) as error:
        raise InputError(f"{path}: cannot read JSONL: {error}") from error
    return rows


def _error_tuples(rows):
    return {
        (frame, name, compared["expected"], compared["actual"], compared["verificationGroup"])
        for frame, record in rows.items()
        for name, compared in record["fields"].items()
        if compared["severity"] == "ERROR"
    }


def _observations(errors):
    keys = ("row", "field", "expected", "actual", "verificationGroup")
    # JSON ordering handles string/null values without coalescing them.
    return [dict(zip(keys, item)) for item in sorted(
        errors, key=lambda item: (item[0], item[1], json.dumps(item[2:])))]


def _summary(rows, errors):
    fields = [compared for record in rows.values() for compared in record["fields"].values()]
    return {
        "row_count": len(rows),
        "field_observation_count": len(fields),
        "raw_mismatch_count": sum(item["expected"] != item["actual"] for item in fields),
        "raw_error_count": len(errors),
        "raw_warning_count": sum(item["severity"] == "WARNING" for item in fields),
        "observed_mismatch_count": sum(item.get("observedMismatch", False) for item in fields),
        "first_error_row": min((item[0] for item in errors), default=None),
    }


def _internal_gaps(rows):
    ordered = sorted(rows)
    return [
        {"after_row": before, "before_row": after, "missing_id_count": after - before - 1}
        for before, after in zip(ordered, ordered[1:]) if after > before + 1
    ]


def compare_rows(baseline, candidate):
    """Analyze validated rows; absence is coverage, not a matched observation."""
    common = baseline.keys() & candidate.keys()
    baseline_only = baseline.keys() - candidate.keys()
    candidate_only = candidate.keys() - baseline.keys()
    baseline_errors = _error_tuples(baseline)
    candidate_errors = _error_tuples(candidate)
    baseline_common_errors = {item for item in baseline_errors if item[0] in common}
    candidate_common_errors = {item for item in candidate_errors if item[0] in common}
    added = _observations(candidate_common_errors - baseline_common_errors)
    removed = _observations(baseline_common_errors - candidate_common_errors)
    field_coverage_changes = []
    first_difference = None
    for frame in sorted(common):
        before = baseline[frame]["fields"]
        after = candidate[frame]["fields"]
        if before.keys() != after.keys():
            field_coverage_changes.append({
                "row": frame,
                "baseline_only_fields": sorted(before.keys() - after.keys()),
                "candidate_only_fields": sorted(after.keys() - before.keys()),
            })
        for name in sorted(before.keys() | after.keys()):
            if first_difference is None and before.get(name) != after.get(name):
                first_difference = {
                    "row": frame, "field": name,
                    "baseline_present": name in before, "candidate_present": name in after,
                    "baseline": before.get(name), "candidate": after.get(name),
                }
    added_by_row = defaultdict(list)
    for observation in added:
        added_by_row[observation["row"]].append(observation)
    baseline_error_rows = {item[0] for item in baseline_errors}
    error_free_additions = [
        {"row": frame, "added_errors": observations,
         "candidate_only_fields": sorted(candidate[frame]["fields"].keys()
                                          - baseline[frame]["fields"].keys())}
        for frame, observations in sorted(added_by_row.items())
        if frame not in baseline_error_rows
    ]
    all_match_additions = [
        item for item in error_free_additions
        if not item["candidate_only_fields"]
        and baseline[item["row"]]["fields"]
        and all(field["severity"] == "MATCH" and not field.get("observedMismatch", False)
                for field in baseline[item["row"]]["fields"].values())
    ]
    first_added = added[0]["row"] if added else None
    return {
        "analysis_only": True,
        "limits": [
            "Analysis is not automatic parity approval or causal attribution; exit 0 only means analysis completed.",
            "Capture source, input, selection and environment equivalence must be established separately.",
            "Only actual common rows contribute ERROR deltas; unmatched-row errors are separate coverage.",
            "Missing rows or compared fields are unknown coverage, never baseline agreement.",
            "Internal gaps count absent integer IDs between observed rows only; leading/trailing coverage is unknown.",
            "No grouped spans are expanded and no missing observations are synthesized.",
            "Raw mismatches count serialized expected != actual; ERROR/WARNING counts retain producer severity.",
            "A baseline-error-free row may still contain warnings, ignored mismatches or missing compared fields.",
        ],
        "baseline": _summary(baseline, baseline_errors),
        "candidate": _summary(candidate, candidate_errors),
        "coverage": {
            "common_rows": sorted(common),
            "baseline_only_rows": sorted(baseline_only),
            "candidate_only_rows": sorted(candidate_only),
            "baseline_internal_gaps": _internal_gaps(baseline),
            "candidate_internal_gaps": _internal_gaps(candidate),
            "field_coverage_changes": field_coverage_changes,
        },
        "common_row_error_delta": {
            "unchanged_count": len(baseline_common_errors & candidate_common_errors),
            "added_count": len(added), "removed_count": len(removed),
            "added": added, "removed": removed,
            "first_added_row": first_added,
            "first_added_row_snapshots": None if first_added is None else {
                "baseline": baseline[first_added], "candidate": candidate[first_added],
            },
            "additions_on_baseline_error_free_rows": error_free_additions,
            "additions_on_baseline_all_match_rows": all_match_additions,
        },
        "unmatched_row_errors": {
            "baseline_only": _observations(item for item in baseline_errors if item[0] in baseline_only),
            "candidate_only": _observations(item for item in candidate_errors if item[0] in candidate_only),
        },
        "first_differing_compared_field": first_difference,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", required=True, type=Path, help="baseline FrameComparison JSONL (plain/gzip)")
    parser.add_argument("--candidate", required=True, type=Path, help="candidate FrameComparison JSONL (plain/gzip)")
    parser.add_argument("--output", type=Path, help="write JSON report here instead of stdout")
    args = parser.parse_args(argv)
    try:
        baseline = read_rows(args.baseline)
        candidate = read_rows(args.candidate)
        if args.output is not None:
            for source in (args.baseline, args.candidate):
                if args.output.resolve() == source.resolve() or (
                        args.output.exists() and args.output.samefile(source)):
                    raise InputError("--output must not overwrite either input")
        report = compare_rows(baseline, candidate)
        report["inputs"] = {"baseline": str(args.baseline), "candidate": str(args.candidate)}
        rendered = json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
        if args.output is None:
            sys.stdout.write(rendered)
        else:
            args.output.write_text(rendered, encoding="utf-8")
    except (InputError, OSError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
