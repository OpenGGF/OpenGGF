#!/usr/bin/env python3
"""Build frozen S1 credits divergence evidence from independent raw observations."""

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
    write_no_replace_outside_roots,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("predecessor_root", type=Path)
    parser.add_argument("candidate_root", type=Path)
    parser.add_argument("comparison_report", type=Path)
    parser.add_argument("raw_sidecar", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args(argv)
    try:
        evidence = build_expected_evidence(
            args.predecessor_root, args.candidate_root,
            args.comparison_report, args.raw_sidecar)
        write_no_replace_outside_roots(
            args.output, evidence, args.predecessor_root, args.candidate_root)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        print(f"credits raw-host evidence build failed: {error}", file=sys.stderr)
        return 1
    print(f"credits raw-host evidence built: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
