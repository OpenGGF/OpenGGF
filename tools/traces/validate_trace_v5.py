#!/usr/bin/env python3
"""Read-only validator for the single supported v5 trace fixture contract."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


LEGACY_KEYS = frozenset({
    "lua_script_version", "csv_version", "ss_csv_version",
    "hardware_timing_schema", "run_schema",
})
SPECIAL_STAGE_WIDTHS = {
    ("s1", "s1_special_stage"): 14,
    ("s2", "s2_special_stage"): 48,
    ("s3k", "s3k_special_stage"): 20,
}
SPECIAL_STAGE_PROFILE_OWNERS = {
    profile: game for game, profile in SPECIAL_STAGE_WIDTHS
}
TIMING_FIELDS = frozenset({
    "event", "raw_frame", "boundary", "kind", "ordinal", "submission_fingerprint",
})
BOUNDARY_ORDER = {"vint_service": 0, "post_objects": 1, "pre_main_loop": 2}
KIND_ORDER = {"kos_module_queue": 0, "kos_decompression_queue": 1}
FINGERPRINT = re.compile(r"sha256:[0-9a-f]{64}\Z")


class Validation:
    """Collects independent contract failures without mutating the fixture root."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []

    def reject(self, path: Path, reason: str) -> None:
        self.errors.append(f"{path}: {reason}")

    def run(self) -> list[str]:
        if not self.root.is_dir():
            self.reject(self.root, "fixture root is not a directory")
            return self.errors
        files = sorted(path for path in self.root.rglob("*") if path.is_file())
        for path in files:
            if "_retro" in path.name:
                self.reject(path, "alternate *_retro sidecars are forbidden")
        metadata_paths = [path for path in files if path.name in {"metadata.json", "metadata.json.gz"}]
        manifest_paths = [path for path in files if path.name in {"run_manifest.json", "run_manifest.json.gz"}]
        if not metadata_paths:
            self.reject(self.root, "trace fleet must contain at least one metadata document")
        for path in metadata_paths:
            self.validate_metadata(path)
        for path in manifest_paths:
            self.validate_manifest(path)
        return self.errors

    def validate_metadata(self, path: Path) -> None:
        metadata = self.read_json(path)
        if metadata is None:
            return
        if not isinstance(metadata, dict):
            self.reject(path, "metadata must be a JSON object")
            return
        self.validate_envelope(path, metadata)
        game = metadata.get("game")
        profile = metadata.get("trace_profile")
        if game not in {"s1", "s2", "s3k"}:
            self.reject(path, "game must be one of s1, s2, s3k")
        if not isinstance(profile, str) or not profile:
            self.reject(path, "trace_profile must be a non-empty string")
        owner = SPECIAL_STAGE_PROFILE_OWNERS.get(profile)
        if owner is not None and game != owner:
            self.reject(path, f"trace_profile {profile} is owned by another game")
        trace_frame_count = metadata.get("trace_frame_count")
        valid_trace_frame_count = (
            isinstance(trace_frame_count, int)
            and not isinstance(trace_frame_count, bool)
            and trace_frame_count >= 0
        )
        if not valid_trace_frame_count:
            self.reject(path, "trace_frame_count must be a non-negative integer")
        fixture_directory = path.parent
        physics = self.single_payload(fixture_directory, "physics.csv")
        if physics is not None and isinstance(game, str) and isinstance(profile, str):
            self.validate_rows(physics, SPECIAL_STAGE_WIDTHS.get((game, profile), 42))
        timing = self.single_payload(fixture_directory, "hardware_timing.jsonl", required=False)
        if timing is not None and valid_trace_frame_count:
            self.validate_timing(timing, trace_frame_count)

    def validate_manifest(self, path: Path) -> None:
        manifest = self.read_json(path)
        if manifest is None:
            return
        if not isinstance(manifest, dict):
            self.reject(path, "run manifest must be a JSON object")
            return
        self.validate_envelope(path, manifest)
        if manifest.get("game") not in {"s1", "s2", "s3k"}:
            self.reject(path, "game must be one of s1, s2, s3k")
        if not isinstance(manifest.get("run_id"), str) or not manifest["run_id"]:
            self.reject(path, "run_id must be a non-empty string")
        if not isinstance(manifest.get("segments"), list):
            self.reject(path, "segments must be an array")
        if not isinstance(manifest.get("transitions"), list):
            self.reject(path, "transitions must be an array")
        transitions = manifest.get("dynamic_art_gap_transitions")
        if not isinstance(transitions, list):
            self.reject(path, "dynamic_art_gap_transitions must be an array")

    def validate_envelope(self, path: Path, document: dict[str, Any]) -> None:
        for key in sorted(LEGACY_KEYS.intersection(document)):
            self.reject(path, f"forbidden legacy key {key}")
        if document.get("trace_schema") != 5 or isinstance(document.get("trace_schema"), bool):
            self.reject(path, "trace_schema must be integer 5")
        if document.get("recorder") != "native-bizhawk-headless":
            self.reject(path, "recorder must be native-bizhawk-headless")
        if document.get("recorder_version") != "3.0":
            self.reject(path, "recorder_version must be 3.0")

    def single_payload(self, directory: Path, name: str, required: bool = True) -> Path | None:
        candidates = [directory / name, directory / f"{name}.gz"]
        present = [path for path in candidates if path.is_file()]
        if len(present) == 1:
            return present[0]
        if not present and required:
            self.reject(directory / name, f"missing required {name} payload")
        elif len(present) > 1:
            self.reject(directory / name, f"both plain and gzipped {name} payloads are present")
        return None

    def validate_rows(self, path: Path, width: int) -> None:
        content = self.read_text(path)
        if content is None:
            return
        try:
            rows = csv.reader(content.splitlines())
            for number, row in enumerate(rows):
                if len(row) != width:
                    self.reject(path, f"row {number} has {len(row)} columns; expected {width}")
        except csv.Error as error:
            self.reject(path, f"invalid CSV: {error}")

    def validate_timing(self, path: Path, frame_count: int) -> None:
        content = self.read_text(path)
        if content is None:
            return
        if content and (not content.endswith("\n") or "\r" in content):
            self.reject(path, "hardware_timing.jsonl must use LF-terminated UTF-8 lines")
            return
        previous: tuple[int, int, int, int] | None = None
        ordinals: dict[str, int] = {}
        identities: set[tuple[str, int]] = set()
        for number, line in enumerate(content.splitlines(), start=1):
            if not line or line != line.strip():
                self.reject(path, f"line {number} must be one compact JSON event")
                continue
            event = self.parse_timing_event(path, number, line)
            if event is None:
                continue
            raw_frame, boundary, kind, ordinal = event
            if raw_frame >= frame_count:
                self.reject(path, f"line {number} raw_frame {raw_frame} is outside [0, {frame_count})")
            identity = (kind, ordinal)
            if identity in identities:
                self.reject(path, f"line {number} has duplicate identity {kind}#{ordinal}")
            identities.add(identity)
            if kind in ordinals and ordinal <= ordinals[kind]:
                self.reject(path, f"line {number} ordinal must increase per kind {kind}")
            ordinals[kind] = ordinal
            ordering = (raw_frame, BOUNDARY_ORDER[boundary], KIND_ORDER[kind], ordinal)
            if previous is not None and ordering <= previous:
                self.reject(path, f"line {number} events must use canonical ordering")
            previous = ordering

    def parse_timing_event(self, path: Path, number: int, line: str) -> tuple[int, str, str, int] | None:
        try:
            event = json.loads(line, object_pairs_hook=reject_duplicate_keys)
        except (json.JSONDecodeError, ValueError) as error:
            self.reject(path, f"line {number} malformed JSON: {error}")
            return None
        if not isinstance(event, dict):
            self.reject(path, f"line {number} must be a JSON object")
            return None
        if set(event) != TIMING_FIELDS:
            unknown = sorted(set(event).symmetric_difference(TIMING_FIELDS))[0]
            self.reject(path, f"line {number} has unknown or missing field {unknown}")
            return None
        if event["event"] != "hardware_work_completed":
            self.reject(path, f"line {number} has invalid event")
        raw_frame = event["raw_frame"]
        if not isinstance(raw_frame, int) or isinstance(raw_frame, bool) or raw_frame < 0:
            self.reject(path, f"line {number} has invalid raw_frame")
            return None
        boundary = event["boundary"]
        if boundary not in BOUNDARY_ORDER:
            self.reject(path, f"line {number} has invalid boundary")
            return None
        kind = event["kind"]
        if kind not in KIND_ORDER:
            self.reject(path, f"line {number} has invalid kind")
            return None
        ordinal = event["ordinal"]
        if not isinstance(ordinal, int) or isinstance(ordinal, bool) or ordinal < 0:
            self.reject(path, f"line {number} has invalid ordinal")
            return None
        if not isinstance(event["submission_fingerprint"], str) or not FINGERPRINT.fullmatch(event["submission_fingerprint"]):
            self.reject(path, f"line {number} has invalid submission_fingerprint")
        if kind == "kos_decompression_queue" and boundary != "pre_main_loop":
            self.reject(path, f"line {number} direct completion kind requires pre_main_loop boundary")
        return raw_frame, boundary, kind, ordinal

    def read_json(self, path: Path) -> Any | None:
        content = self.read_text(path)
        if content is None:
            return None
        try:
            return json.loads(content, object_pairs_hook=reject_duplicate_keys)
        except (json.JSONDecodeError, ValueError) as error:
            self.reject(path, f"malformed JSON: {error}")
            return None

    def read_text(self, path: Path) -> str | None:
        try:
            if path.name.endswith(".gz"):
                with gzip.open(path, "rt", encoding="utf-8", newline="") as source:
                    return source.read()
            return path.read_text(encoding="utf-8", newline="")
        except (OSError, UnicodeDecodeError) as error:
            self.reject(path, f"cannot read UTF-8 payload: {error}")
            return None


def reject_duplicate_keys(pairs: Iterable[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON field {key}")
        result[key] = value
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="trace fixture root to validate without modifying")
    args = parser.parse_args(argv)
    errors = Validation(args.root).run()
    if errors:
        print("trace v5 validation failed:", file=sys.stderr)
        print(*errors, sep="\n", file=sys.stderr)
        return 1
    print(f"trace v5 validation passed: {args.root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
