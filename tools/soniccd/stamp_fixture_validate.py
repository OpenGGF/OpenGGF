#!/usr/bin/env python3
"""Recheck CD-05 native fixture payloads, exact continuations and run controls.

Input is a completed external stamp_fixture_run directory. No emulator or ROM
is required. Does not write files or admit these research bytes to gameplay.
"""
import argparse
import json
from pathlib import Path

from oracle_boot import digest
from stamp_fixture_run import SCHEMA, compare_runs, validate_run


def validate_capture(root):
    manifest = json.loads((root / "manifest.json").read_text())
    if manifest["schema"] != SCHEMA:
        raise ValueError("Unsupported fixture manifest")
    result = {}
    for label in ("run-1", "run-2", "control"):
        for name, expected in manifest["streams_sha256"][label].items():
            if name not in ("fixtures.json", "av.jsonl", "continuation.jsonl", "summary.json") or digest(root / label / name) != expected:
                raise ValueError("Bound fixture stream changed")
        summary, evidence = validate_run(root / label, manifest["frames"], manifest["fixtures"] if label != "control" else 0)
        if summary != manifest["summaries"][label] or evidence != manifest["exact_comparisons"][label]:
            raise ValueError("Native fixture evidence differs from manifest")
        result[label] = evidence
    compare_runs(root / "run-1", root / "run-2")
    compare_runs(root / "run-1", root / "control", fixtures=False)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_root", type=Path)
    args = parser.parse_args()
    result = validate_capture(args.capture_root.resolve(strict=True))
    print(json.dumps({"exact_native_fixtures": len(result["run-1"]), "repeat_and_control_equal": True,
                      "comparisons": result["run-1"]}, indent=2))


if __name__ == "__main__":
    main()
