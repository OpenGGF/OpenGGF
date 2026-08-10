#!/usr/bin/env python3
"""Compare predecessor and v5 candidate trace roots without modifying either."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import sys
from collections import Counter
from hashlib import sha256
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.traces.trace_fixture_inventory import build_inventory, compare_inventory_documents
from tools.traces.validate_trace_v5 import LEGACY_KEYS, Validation
from tools.traces.no_replace_output import write_bytes_no_replace


REPORT_FORMAT = "openggf-trace-v5-candidate-comparison-v1"
MAX_AUX_LITERAL_DELTAS = 32
MAX_AUX_LINES_PER_DELTA = 8
MAX_AUX_CHARACTERS_PER_LINE = 512
MODES = ("v5-literal", "credits-20-to-42")
CREDITS_CANDIDATE_DIRECTORIES = {
    "credits_00_ghz1": "00_ghz1_credits_demo_1",
    "credits_01_mz2": "01_mz2_credits_demo",
    "credits_02_syz3": "02_syz3_credits_demo",
    "credits_03_lz3": "03_lz3_credits_demo",
    "credits_04_slz3": "04_slz3_credits_demo",
    "credits_05_sbz1": "05_sbz1_credits_demo",
    "credits_06_sbz2": "06_sbz2_credits_demo",
    "credits_07_ghz1b": "07_ghz1_credits_demo_2",
}
CREDITS_COLUMN_MAP = {
    "frame": "frame", "input": "input", "x": "player_x", "y": "player_y",
    "x_speed": "player_x_speed", "y_speed": "player_y_speed",
    "g_speed": "player_g_speed", "angle": "player_angle", "air": "player_air",
    "rolling": "player_rolling", "ground_mode": "player_ground_mode",
    "x_sub": "player_x_sub", "y_sub": "player_y_sub", "routine": "player_routine",
    "camera_x": "camera_x", "camera_y": "camera_y", "rings": "rings",
    "status_byte": "player_status_byte", "v_framecount": "gameplay_frame_counter",
    "stand_on_obj": "player_stand_on_obj",
}


def compare_roots(predecessor_root: Path, candidate_root: Path,
                  mode: str = "v5-literal") -> dict[str, Any]:
    """Return a literal machine-readable comparison; never write either root."""
    predecessor_root = predecessor_root.resolve()
    candidate_root = candidate_root.resolve()
    if mode not in MODES:
        raise ValueError(f"unsupported comparison mode {mode}")
    candidate_errors = Validation(candidate_root).run()
    if candidate_errors:
        raise ValueError("candidate root is not v5:\n" + "\n".join(candidate_errors))
    if mode == "v5-literal":
        predecessor_errors = Validation(predecessor_root).run()
        if predecessor_errors:
            raise ValueError("predecessor root is not v5:\n" + "\n".join(predecessor_errors))

    predecessor_inventory = build_inventory(predecessor_root)
    candidate_inventory = build_inventory(candidate_root)
    predecessor_files = files_by_logical_path(predecessor_root)
    candidate_files = files_by_logical_path(
        candidate_root, remap_credits=mode == "credits-20-to-42")
    reports = [compare_file(path, predecessor_files.get(path), candidate_files.get(path), mode)
               for path in sorted(predecessor_files.keys() | candidate_files.keys())]
    inventory_changes = compare_inventory_documents(predecessor_inventory, candidate_inventory)
    return {
        "format": REPORT_FORMAT,
        "mode": mode,
        "predecessor_root": str(predecessor_root),
        "candidate_root": str(candidate_root),
        "equal": not inventory_changes and all(item.get("logical_equal", False) for item in reports),
        "predecessor_scan": scan_predecessor(predecessor_root),
        "predecessor_inventory": predecessor_inventory,
        "candidate_inventory": candidate_inventory,
        "inventory_changes": inventory_changes,
        "files": reports,
    }


def files_by_logical_path(root: Path, remap_credits: bool = False) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        logical = relative[:-3] if relative.endswith(".gz") else relative
        if remap_credits:
            parts = Path(logical).parts
            if len(parts) >= 3 and parts[0] == "s1":
                reverse = {candidate: predecessor
                           for predecessor, candidate in CREDITS_CANDIDATE_DIRECTORIES.items()}
                if parts[1] in reverse:
                    logical = Path("s1", reverse[parts[1]], *parts[2:]).as_posix()
        if logical in result:
            raise ValueError(f"both plain and gzipped files represent {logical} under {root}")
        result[logical] = path
    return result


def compare_file(logical_path: str, predecessor: Path | None,
                 candidate: Path | None, mode: str) -> dict[str, Any]:
    report: dict[str, Any] = {
        "logical_path": logical_path,
        "kind": file_kind(logical_path),
        "predecessor": file_identity(predecessor),
        "candidate": file_identity(candidate),
        "logical_equal": False,
    }
    if predecessor is None or candidate is None:
        report["status"] = "added" if predecessor is None else "removed"
        return report
    old_content, new_content = logical_bytes(predecessor), logical_bytes(candidate)
    report["logical_equal"] = old_content == new_content
    report["status"] = "unchanged" if report["logical_equal"] else "changed"
    if logical_path.endswith("physics.csv"):
        report["comparison"] = compare_physics(logical_path, old_content, new_content, mode)
    elif logical_path.endswith("aux_state.jsonl"):
        report["comparison"] = compare_aux(old_content, new_content)
    elif logical_path.endswith(("metadata.json", "run_manifest.json")):
        report["comparison"] = {
            "predecessor_document": json.loads(old_content),
            "candidate_document": json.loads(new_content),
        }
    elif logical_path.endswith("hardware_timing.jsonl"):
        report["comparison"] = {
            "predecessor_lines": old_content.decode("utf-8").splitlines(),
            "candidate_lines": new_content.decode("utf-8").splitlines(),
        }
    return report


def compare_physics(logical_path: str, predecessor: bytes, candidate: bytes,
                    mode: str) -> dict[str, Any]:
    old_rows = list(csv.reader(predecessor.decode("utf-8").splitlines()))
    new_rows = list(csv.reader(candidate.decode("utf-8").splitlines()))
    if not old_rows or not new_rows:
        raise ValueError(f"physics payload has no header: {logical_path}")
    old_header, new_header = old_rows[0], new_rows[0]
    if len(set(old_header)) != len(old_header) or len(set(new_header)) != len(new_header):
        raise ValueError(f"physics payload has duplicate columns: {logical_path}")
    if mode == "credits-20-to-42" and "/credits_" in f"/{logical_path}" \
            and (len(old_header), len(new_header)) != (20, 42):
        raise ValueError(f"credits 20-to-42 mode requires widths 20 and 42: {logical_path}")
    old_index = {name: index for index, name in enumerate(old_header)}
    new_index = {name: index for index, name in enumerate(new_header)}
    if mode == "credits-20-to-42" and "/credits_" in f"/{logical_path}":
        if set(old_header) != set(CREDITS_COLUMN_MAP):
            raise ValueError(f"credits predecessor header is not the canonical 20-column shape: {logical_path}")
        pairs = [(name, CREDITS_COLUMN_MAP[name]) for name in old_header]
        missing = [candidate_name for _, candidate_name in pairs if candidate_name not in new_index]
        if missing:
            raise ValueError(f"credits candidate is missing mapped column {missing[0]}: {logical_path}")
    else:
        pairs = [(name, name) for name in old_header if name in new_index]
    mismatches = []
    for row_index in range(min(len(old_rows), len(new_rows)) - 1):
        old_row, new_row = old_rows[row_index + 1], new_rows[row_index + 1]
        if len(old_row) != len(old_header) or len(new_row) != len(new_header):
            raise ValueError(f"physics row width changed at data row {row_index}: {logical_path}")
        for old_name, new_name in pairs:
            old_value, new_value = old_row[old_index[old_name]], new_row[new_index[new_name]]
            if old_value != new_value:
                mismatches.append({"row": row_index, "column": old_name,
                                   "predecessor": old_value, "candidate": new_value})
    return {
        "predecessor_width": len(old_header),
        "candidate_width": len(new_header),
        "predecessor_row_count": max(0, len(old_rows) - 1),
        "candidate_row_count": max(0, len(new_rows) - 1),
        "common_columns": [{"predecessor": old_name, "candidate": new_name}
                           for old_name, new_name in pairs],
        "added_columns": [name for name in new_header
                          if name not in {candidate_name for _, candidate_name in pairs}],
        "removed_columns": [name for name in old_header
                            if name not in {old_name for old_name, _ in pairs}],
        "common_field_mismatches": mismatches,
    }


def compare_aux(predecessor: bytes, candidate: bytes) -> dict[str, Any]:
    old_events, new_events = event_counts(predecessor), event_counts(candidate)
    old_lines = predecessor.decode("utf-8").splitlines()
    new_lines = candidate.decode("utf-8").splitlines()
    delta_count, deltas = literal_deltas(old_lines, new_lines)
    return {
        "predecessor_event_counts": dict(sorted(old_events.items())),
        "candidate_event_counts": dict(sorted(new_events.items())),
        "added_event_types": sorted(new_events.keys() - old_events.keys()),
        "removed_event_types": sorted(old_events.keys() - new_events.keys()),
        "event_count_deltas": {event: new_events[event] - old_events[event]
                               for event in sorted(old_events.keys() | new_events.keys())
                               if new_events[event] != old_events[event]},
        "literal_delta_count": delta_count,
        "literal_deltas": deltas,
        "literal_deltas_truncated": delta_count > len(deltas),
    }


def literal_deltas(old_lines: list[str], new_lines: list[str]) -> tuple[int, list[dict[str, Any]]]:
    count = 0
    result: list[dict[str, Any]] = []
    for index in range(max(len(old_lines), len(new_lines))):
        old = old_lines[index:index + 1]
        new = new_lines[index:index + 1]
        if old == new:
            continue
        count += 1
        if len(result) == MAX_AUX_LITERAL_DELTAS:
            continue
        tag = "replace" if old and new else "delete" if old else "insert"
        result.append(literal_delta(tag, index, index + len(old), index, index + len(new), old, new))
    return count, result


def literal_delta(tag: str, old_start: int, old_end: int,
                  new_start: int, new_end: int,
                  old_lines: list[str], new_lines: list[str]) -> dict[str, Any]:
    previews = old_lines[:MAX_AUX_LINES_PER_DELTA], new_lines[:MAX_AUX_LINES_PER_DELTA]
    truncated = (
        len(old_lines) > MAX_AUX_LINES_PER_DELTA
        or len(new_lines) > MAX_AUX_LINES_PER_DELTA
        or any(len(line) > MAX_AUX_CHARACTERS_PER_LINE for lines in previews for line in lines)
    )
    return {
        "tag": tag,
        "predecessor_start": old_start,
        "predecessor_end": old_end,
        "candidate_start": new_start,
        "candidate_end": new_end,
        "predecessor_line_count": len(old_lines),
        "candidate_line_count": len(new_lines),
        "predecessor_sha256": lines_sha256(old_lines),
        "candidate_sha256": lines_sha256(new_lines),
        "predecessor_lines": [line[:MAX_AUX_CHARACTERS_PER_LINE] for line in previews[0]],
        "candidate_lines": [line[:MAX_AUX_CHARACTERS_PER_LINE] for line in previews[1]],
        "lines_truncated": truncated,
    }


def lines_sha256(lines: list[str]) -> str:
    encoded = json.dumps(lines, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return sha256(encoded).hexdigest()


def event_counts(content: bytes) -> Counter[str]:
    counts: Counter[str] = Counter()
    for number, line in enumerate(content.decode("utf-8").splitlines(), start=1):
        document = json.loads(line)
        event = document.get("event")
        if not isinstance(event, str) or not event:
            raise ValueError(f"aux line {number} has no event name")
        counts[event] += 1
    return counts


def scan_predecessor(root: Path) -> dict[str, Any]:
    legacy_keys: set[str] = set()
    widths: set[int] = set()
    for logical, path in files_by_logical_path(root).items():
        if logical.endswith("metadata.json"):
            legacy_keys.update(LEGACY_KEYS.intersection(json.loads(logical_bytes(path))))
        elif logical.endswith("physics.csv"):
            rows = list(csv.reader(logical_bytes(path).decode("utf-8").splitlines()))
            if rows:
                widths.add(len(rows[0]))
    return {"legacy_keys": sorted(legacy_keys), "physics_widths": sorted(widths)}


def file_identity(path: Path | None) -> dict[str, Any] | None:
    if path is None:
        return None
    stored, logical = path.read_bytes(), logical_bytes(path)
    return {"path": str(path), "stored_bytes": len(stored), "logical_bytes": len(logical),
            "stored_sha256": sha256(stored).hexdigest(),
            "logical_sha256": sha256(logical).hexdigest()}


def logical_bytes(path: Path) -> bytes:
    stored = path.read_bytes()
    return gzip.decompress(stored) if path.name.endswith(".gz") else stored


def file_kind(logical_path: str) -> str:
    return {"metadata.json": "metadata", "run_manifest.json": "run-manifest",
            "physics.csv": "physics", "aux_state.jsonl": "auxiliary-state",
            "hardware_timing.jsonl": "hardware-timing"}.get(Path(logical_path).name, "other")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("predecessor_root", type=Path)
    parser.add_argument("candidate_root", type=Path)
    parser.add_argument("--mode", choices=MODES, default="v5-literal")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--fail-on-difference", action="store_true")
    args = parser.parse_args(argv)
    try:
        report = compare_roots(args.predecessor_root, args.candidate_root, args.mode)
        encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
        if args.output:
            destination = args.output.resolve()
            roots = (args.predecessor_root.resolve(), args.candidate_root.resolve())
            if any(destination.is_relative_to(root) for root in roots):
                raise ValueError("comparison report must remain outside both compared roots")
            write_bytes_no_replace(
                args.output, encoded.encode("utf-8"), "comparison report")
        else:
            print(encoded, end="")
        return 1 if args.fail_on_difference and not report["equal"] else 0
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        print(f"trace candidate comparison failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
