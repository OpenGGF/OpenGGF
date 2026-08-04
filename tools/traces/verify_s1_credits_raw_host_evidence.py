#!/usr/bin/env python3
"""Independently verify frozen S1 credits raw-host divergence evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.traces.s1_credits_raw_evidence import (
    build_expected_evidence,
    parse_object,
    require_outside_roots,
)


def verify_evidence(predecessor_root: Path, candidate_root: Path,
                    comparison_report: Path, raw_sidecar: Path,
                    evidence_path: Path) -> None:
    require_outside_roots(
        evidence_path, predecessor_root.resolve(), candidate_root.resolve(), "evidence artifact")
    expected = build_expected_evidence(
        predecessor_root, candidate_root, comparison_report, raw_sidecar)
    actual = parse_object(evidence_path.read_bytes(), evidence_path)
    if actual != expected:
        raise ValueError("evidence artifact does not match independently recomputed evidence")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("predecessor_root", type=Path)
    parser.add_argument("candidate_root", type=Path)
    parser.add_argument("comparison_report", type=Path)
    parser.add_argument("raw_sidecar", type=Path)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args(argv)
    try:
        verify_evidence(
            args.predecessor_root, args.candidate_root, args.comparison_report,
            args.raw_sidecar, args.evidence)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        print(f"credits raw-host evidence verification failed: {error}", file=sys.stderr)
        return 1
    print(f"credits raw-host evidence verified: {args.evidence}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
