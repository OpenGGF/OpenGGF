#!/usr/bin/env python3
"""Verify S1 credits raw-host evidence against a comparison and candidate root."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.traces.compare_trace_v5_candidates import (
    CREDITS_CANDIDATE_DIRECTORIES,
    CREDITS_COLUMN_MAP,
)


EVIDENCE_FORMAT = "openggf-s1-credits-raw-host-evidence-v1"
COMPARISON_FORMAT = "openggf-trace-v5-candidate-comparison-v1"


def verify_evidence(candidate_root: Path, comparison_report: Path, evidence_path: Path) -> None:
    candidate_root = candidate_root.resolve()
    if evidence_path.resolve().is_relative_to(candidate_root):
        raise ValueError("evidence artifact must remain outside candidate root")
    report, evidence = load_document(comparison_report), load_document(evidence_path)
    if report.get("format") != COMPARISON_FORMAT:
        raise ValueError("comparison report format is not supported")
    if evidence.get("format") != EVIDENCE_FORMAT or not isinstance(evidence.get("routes"), list):
        raise ValueError("evidence format is not supported")
    disclosed = disclosed_first_divergences(report)
    observed: set[tuple[str, int, str]] = set()
    for route in evidence["routes"]:
        verify_route(candidate_root, route, observed)
    missing, extra = sorted(disclosed - observed), sorted(observed - disclosed)
    if missing:
        raise ValueError(f"missing evidence for disclosed first divergences: {missing}")
    if extra:
        raise ValueError(f"evidence does not match a disclosed first divergence: {extra}")


def verify_route(candidate_root: Path, route: dict[str, Any],
                 observed: set[tuple[str, int, str]]) -> None:
    route_name = require_string(route, "route")
    logical_path = require_string(route, "candidate_payload")
    candidate_directory = CREDITS_CANDIDATE_DIRECTORIES.get(route_name)
    if candidate_directory is None or logical_path != f"s1/{candidate_directory}/physics.csv":
        raise ValueError(f"candidate payload does not match route {route_name}")
    content = read_logical(resolve_payload(candidate_root, logical_path))
    if hashlib.sha256(content).hexdigest() != require_string(route, "candidate_logical_sha256"):
        raise ValueError(f"candidate logical hash drift for {route_name}")
    rows = list(csv.reader(content.decode("utf-8").splitlines()))
    if not rows:
        raise ValueError(f"candidate physics has no header for {route_name}")
    index = {name: position for position, name in enumerate(rows[0])}
    observations = route.get("observations")
    if not isinstance(observations, list):
        raise ValueError(f"observations must be an array for {route_name}")
    for observation in observations:
        row, field = observation.get("row"), observation.get("common_field")
        if not isinstance(row, int) or isinstance(row, bool) or row < 0:
            raise ValueError(f"invalid evidence row for {route_name}")
        candidate_field = CREDITS_COLUMN_MAP.get(field) if isinstance(field, str) else None
        if candidate_field is None or candidate_field not in index:
            raise ValueError(f"invalid evidence field for {route_name}")
        raw, emitted = require_string(observation, "raw_value"), require_string(observation, "emitted_value")
        if raw != emitted:
            raise ValueError(f"raw/emitted mismatch for {route_name} row {row} field {field}")
        has_ram = isinstance(observation.get("ram_address"), str)
        has_derivation = isinstance(observation.get("derivation"), str)
        if has_ram == has_derivation:
            raise ValueError("evidence requires exactly one RAM address or documented derivation")
        if has_ram and observation.get("endianness") not in {"big", "little", "byte"}:
            raise ValueError("RAM evidence requires big, little, or byte endianness")
        try:
            candidate_value = rows[row + 1][index[candidate_field]]
        except IndexError as error:
            raise ValueError(f"candidate row is absent for {route_name} row {row}") from error
        if candidate_value != emitted:
            raise ValueError(f"emitted value mismatch for {route_name} row {row} field {field}")
        key = (route_name, row, field)
        if key in observed:
            raise ValueError(f"duplicate evidence for {key}")
        observed.add(key)


def disclosed_first_divergences(report: dict[str, Any]) -> set[tuple[str, int, str]]:
    result: set[tuple[str, int, str]] = set()
    for file_report in report.get("files", []):
        parts = Path(file_report.get("logical_path", "")).parts
        if len(parts) != 3 or parts[0] != "s1" or not parts[1].startswith("credits_") \
                or parts[2] != "physics.csv":
            continue
        first_by_field: dict[str, int] = {}
        for mismatch in file_report.get("comparison", {}).get("common_field_mismatches", []):
            field, row = mismatch.get("column"), mismatch.get("row")
            if isinstance(field, str) and isinstance(row, int):
                first_by_field[field] = min(row, first_by_field.get(field, row))
        result.update((parts[1], row, field) for field, row in first_by_field.items())
    return result


def resolve_payload(root: Path, logical_path: str) -> Path:
    plain, compressed = root / logical_path, root / f"{logical_path}.gz"
    present = [path for path in (plain, compressed) if path.is_file()]
    if len(present) != 1:
        raise ValueError(f"candidate payload must exist exactly once: {logical_path}")
    return present[0]


def read_logical(path: Path) -> bytes:
    content = path.read_bytes()
    return gzip.decompress(content) if path.name.endswith(".gz") else content


def load_document(path: Path) -> dict[str, Any]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(f"document must be a JSON object: {path}")
    return document


def require_string(document: dict[str, Any], key: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{key} must be a non-empty string")
    return value


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate_root", type=Path)
    parser.add_argument("comparison_report", type=Path)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args(argv)
    try:
        verify_evidence(args.candidate_root, args.comparison_report, args.evidence)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        print(f"credits raw-host evidence verification failed: {error}", file=sys.stderr)
        return 1
    print(f"credits raw-host evidence verified: {args.evidence}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
