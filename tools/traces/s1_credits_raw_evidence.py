#!/usr/bin/env python3
"""Strict join and validation for streamed S1 credits raw-host observations."""

from __future__ import annotations

import csv
import gzip
import hashlib
import json
import re
from pathlib import Path
from typing import Any

from tools.traces.compare_trace_v5_candidates import (
    CREDITS_CANDIDATE_DIRECTORIES,
    CREDITS_COLUMN_MAP,
    REPORT_FORMAT,
    compare_roots,
)
from tools.traces.no_replace_output import write_bytes_no_replace


RAW_FORMAT = "openggf-s1-credits-raw-observations-v1"
EVIDENCE_FORMAT = "openggf-s1-credits-raw-host-evidence-v1"
MAX_RAW_BYTES = 64 * 1024 * 1024
MAX_OBSERVATIONS = 86_400
CAPTURE_ID = re.compile(r"[\x20-\x7e]+\Z")
HEX_SHA1 = re.compile(r"[0-9a-f]{40}\Z")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}\Z")

COMMON_FIELDS = tuple(CREDITS_COLUMN_MAP)
ROUTES = tuple(CREDITS_CANDIDATE_DIRECTORIES)
PROVENANCE: dict[str, dict[str, str]] = {
    "frame": {"derivation": "trace_row_ordinal"},
    "input": {"derivation": "s1_rom_controller_mask"},
    "x": {"ram_address": "0xFFFFD008", "endianness": "big"},
    "y": {"ram_address": "0xFFFFD00C", "endianness": "big"},
    "x_speed": {"ram_address": "0xFFFFD010", "endianness": "big"},
    "y_speed": {"ram_address": "0xFFFFD012", "endianness": "big"},
    "g_speed": {"ram_address": "0xFFFFD014", "endianness": "big"},
    "angle": {"ram_address": "0xFFFFD026", "endianness": "byte"},
    "air": {"derivation": "s1_status_air_bit"},
    "rolling": {"derivation": "s1_status_rolling_bit"},
    "ground_mode": {"derivation": "s1_ground_mode"},
    "x_sub": {"ram_address": "0xFFFFD00A", "endianness": "big"},
    "y_sub": {"ram_address": "0xFFFFD00E", "endianness": "big"},
    "routine": {"ram_address": "0xFFFFD024", "endianness": "byte"},
    "camera_x": {"ram_address": "0xFFFFF700", "endianness": "big"},
    "camera_y": {"ram_address": "0xFFFFF704", "endianness": "big"},
    "rings": {"ram_address": "0xFFFFFE20", "endianness": "big"},
    "status_byte": {"ram_address": "0xFFFFD022", "endianness": "byte"},
    "v_framecount": {"ram_address": "0xFFFFFE04", "endianness": "big"},
    "stand_on_obj": {"ram_address": "0xFFFFD03D", "endianness": "byte"},
}
RAW_VALUE_WIDTHS = {
    "frame": 4, "input": 4,
    "angle": 2, "routine": 2, "status_byte": 2, "stand_on_obj": 2,
    "air": 1, "rolling": 1, "ground_mode": 1,
    "x": 4, "y": 4, "x_speed": 4, "y_speed": 4, "g_speed": 4,
    "x_sub": 4, "y_sub": 4, "camera_x": 4, "camera_y": 4,
    "rings": 4, "v_framecount": 4,
}

HEADER_KEYS = {
    "record_type", "format", "capture_id", "candidate_root", "rom_sha1",
    "recorder", "recorder_version",
}
COMPLETION_KEYS = {
    "record_type", "capture_id", "candidate_root", "all_eight_complete",
    "route_rows", "total_rows", "observation_count", "preceding_byte_count",
    "preceding_sha256",
}


def build_expected_evidence(predecessor_root: Path, candidate_root: Path,
                            comparison_report: Path, raw_sidecar: Path) -> dict[str, Any]:
    predecessor_root = predecessor_root.resolve()
    candidate_root = candidate_root.resolve()
    require_outside_roots(comparison_report, predecessor_root, candidate_root, "comparison report")
    require_outside_roots(raw_sidecar, predecessor_root, candidate_root, "raw sidecar")

    report_bytes = comparison_report.read_bytes()
    report = parse_object(report_bytes, comparison_report)
    if report.get("format") != REPORT_FORMAT or report.get("mode") != "credits-20-to-42":
        raise ValueError("comparison report is not the credits-20-to-42 format")
    recomputed = compare_roots(predecessor_root, candidate_root, mode="credits-20-to-42")
    if report != recomputed:
        raise ValueError("comparison report drifted from predecessor and candidate roots")

    raw_bytes = raw_sidecar.read_bytes()
    header, observations, completion = parse_raw_sidecar(raw_bytes, candidate_root)
    candidate_rows = load_candidate_rows(candidate_root)
    expected_route_rows = {route: len(candidate_rows[route][1]) for route in ROUTES}
    if completion["route_rows"] != expected_route_rows:
        raise ValueError("raw sidecar route row counts do not match candidate physics")
    if completion["total_rows"] != sum(expected_route_rows.values()):
        raise ValueError("raw sidecar total row count does not match candidate physics")

    by_key = {(item["route"], item["row"], item["common_field"]): item
              for item in observations}
    first_divergences = disclosed_first_divergences(report)
    routes: list[dict[str, Any]] = []
    for route in ROUTES:
        selected: list[dict[str, Any]] = []
        header_row, rows, payload_path, logical_hash = candidate_rows[route]
        index = {field: position for position, field in enumerate(header_row)}
        for field, row in sorted(first_divergences.get(route, {}).items(),
                                 key=lambda item: (item[1], COMMON_FIELDS.index(item[0]))):
            raw = by_key.get((route, row, field))
            if raw is None:
                raise ValueError(f"missing raw observation for {route} row {row} field {field}")
            candidate_field = CREDITS_COLUMN_MAP[field]
            try:
                emitted = rows[row][index[candidate_field]]
            except (KeyError, IndexError) as error:
                raise ValueError(
                    f"candidate value is absent for {route} row {row} field {field}") from error
            if raw["raw_value"] != emitted:
                raise ValueError(
                    f"raw/emitted mismatch for {route} row {row} field {field}")
            selected.append({
                "row": row,
                "common_field": field,
                **PROVENANCE[field],
                "raw_value": raw["raw_value"],
                "emitted_value": emitted,
            })
        if selected:
            routes.append({
                "route": route,
                "candidate_payload": f"s1/{CREDITS_CANDIDATE_DIRECTORIES[route]}/physics.csv",
                "candidate_logical_sha256": logical_hash,
                "observations": selected,
            })

    disclosed = sum(len(fields) for fields in first_divergences.values())
    if sum(len(route["observations"]) for route in routes) != disclosed:
        raise ValueError("raw evidence selection does not cover every disclosed first divergence")
    return {
        "format": EVIDENCE_FORMAT,
        "capture_id": header["capture_id"],
        "predecessor_root": str(predecessor_root),
        "candidate_root": str(candidate_root),
        "comparison_report_sha256": hashlib.sha256(report_bytes).hexdigest(),
        "raw_sidecar_sha256": hashlib.sha256(raw_bytes).hexdigest(),
        "routes": routes,
    }


def parse_raw_sidecar(content: bytes, candidate_root: Path) -> tuple[
        dict[str, Any], list[dict[str, Any]], dict[str, Any]]:
    if len(content) > MAX_RAW_BYTES:
        raise ValueError("raw sidecar exceeds 64 MiB")
    if not content.endswith(b"\n"):
        raise ValueError("raw sidecar is truncated: final newline is absent")
    lines = content.splitlines(keepends=True)
    if len(lines) < 3:
        raise ValueError("raw sidecar requires header, observations, and completion")
    documents = [parse_object(line, Path(f"raw sidecar line {index}"))
                 for index, line in enumerate(lines, start=1)]
    header, completion = documents[0], documents[-1]
    observations = documents[1:-1]

    require_exact_keys(header, HEADER_KEYS, "raw sidecar header")
    if header["record_type"] != "header" or header["format"] != RAW_FORMAT:
        raise ValueError("raw sidecar header format is not supported")
    capture_id = require_capture_id(header.get("capture_id"))
    if Path(require_string(header, "candidate_root")).resolve() != candidate_root:
        raise ValueError("raw sidecar candidate root does not match candidate root")
    if HEX_SHA1.fullmatch(require_string(header, "rom_sha1")) is None:
        raise ValueError("raw sidecar ROM SHA-1 must be lowercase hexadecimal")
    if header.get("recorder") != "native-bizhawk-headless" \
            or header.get("recorder_version") != "3.0":
        raise ValueError("raw sidecar recorder provenance is not canonical")

    require_exact_keys(completion, COMPLETION_KEYS, "raw sidecar completion")
    if completion["record_type"] != "completion":
        raise ValueError("raw sidecar has no completion record")
    if completion.get("capture_id") != capture_id \
            or completion.get("candidate_root") != header["candidate_root"]:
        raise ValueError("raw sidecar completion identity does not match header")
    if completion.get("all_eight_complete") is not True:
        raise ValueError("raw sidecar does not record all-eight completion")
    preceding = b"".join(lines[:-1])
    if completion.get("preceding_byte_count") != len(preceding):
        raise ValueError("raw sidecar preceding byte count is inconsistent")
    digest = completion.get("preceding_sha256")
    if not isinstance(digest, str) or HEX_SHA256.fullmatch(digest) is None \
            or digest != hashlib.sha256(preceding).hexdigest():
        raise ValueError("raw sidecar preceding SHA-256 is inconsistent")
    if completion.get("observation_count") != len(observations):
        raise ValueError("raw sidecar observation count is inconsistent")
    if len(observations) > MAX_OBSERVATIONS:
        raise ValueError("raw sidecar exceeds 86,400 observations")
    route_rows = completion.get("route_rows")
    if not isinstance(route_rows, dict) or list(route_rows) != list(ROUTES):
        raise ValueError("raw sidecar route row counts must name all eight routes in order")
    for route, rows in route_rows.items():
        if not isinstance(rows, int) or isinstance(rows, bool) or rows < 1:
            raise ValueError(f"raw sidecar route row count is invalid for {route}")

    expected = [(demo, route, row, field)
                for demo, route in enumerate(ROUTES)
                for row in range(route_rows[route])
                for field in COMMON_FIELDS]
    if len(expected) != len(observations):
        raise ValueError("raw sidecar observations are missing or duplicated")
    for ordinal, (item, identity) in enumerate(zip(observations, expected)):
        demo, route, row, field = identity
        expected_keys = {
            "record_type", "demo_index", "route", "candidate_directory", "row",
            "common_field", "raw_value", *PROVENANCE[field],
        }
        require_exact_keys(item, expected_keys, f"raw observation {ordinal}")
        if item.get("record_type") != "observation" or (
                item.get("demo_index"), item.get("route"), item.get("row"),
                item.get("common_field")) != identity:
            raise ValueError(f"raw observation {ordinal} is duplicated or out of canonical order")
        if item.get("candidate_directory") != CREDITS_CANDIDATE_DIRECTORIES[route]:
            raise ValueError(f"raw observation {ordinal} candidate directory is inconsistent")
        for key, value in PROVENANCE[field].items():
            if item.get(key) != value:
                raise ValueError(f"raw observation {ordinal} has fabricated provenance")
        raw_value = item.get("raw_value")
        if (not isinstance(raw_value, str)
                or len(raw_value) != RAW_VALUE_WIDTHS[field]
                or any(character not in "0123456789ABCDEF" for character in raw_value)):
            raise ValueError(f"raw observation {ordinal} value is not canonical uppercase hex")
    return header, observations, completion


def disclosed_first_divergences(report: dict[str, Any]) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for file_report in report.get("files", []):
        parts = Path(file_report.get("logical_path", "")).parts
        if len(parts) != 3 or parts[0] != "s1" or parts[1] not in ROUTES \
                or parts[2] != "physics.csv":
            continue
        fields = result.setdefault(parts[1], {})
        comparison = file_report.get("comparison", {})
        for mismatch in comparison.get("common_field_mismatches", []):
            field, row = mismatch.get("column"), mismatch.get("row")
            if field in COMMON_FIELDS and isinstance(row, int) and not isinstance(row, bool):
                fields[field] = min(row, fields.get(field, row))
    return result


def load_candidate_rows(candidate_root: Path) -> dict[
        str, tuple[list[str], list[list[str]], Path, str]]:
    result = {}
    for route, directory in CREDITS_CANDIDATE_DIRECTORIES.items():
        logical = candidate_root / "s1" / directory / "physics.csv"
        candidates = [path for path in (logical, Path(str(logical) + ".gz")) if path.is_file()]
        if len(candidates) != 1:
            raise ValueError(f"candidate physics must exist exactly once for {route}")
        payload_path = candidates[0]
        stored = payload_path.read_bytes()
        content = gzip.decompress(stored) if payload_path.name.endswith(".gz") else stored
        rows = list(csv.reader(content.decode("utf-8").splitlines()))
        if not rows:
            raise ValueError(f"candidate physics has no header for {route}")
        result[route] = (rows[0], rows[1:], payload_path, hashlib.sha256(content).hexdigest())
    return result


def write_no_replace_outside_roots(path: Path, document: dict[str, Any],
                                   predecessor_root: Path, candidate_root: Path) -> None:
    require_outside_roots(path, predecessor_root.resolve(), candidate_root.resolve(), "evidence output")
    encoded = json.dumps(document, indent=2, sort_keys=True) + "\n"
    write_bytes_no_replace(path, encoded.encode("utf-8"), "evidence output")


def require_outside_roots(path: Path, predecessor_root: Path, candidate_root: Path,
                          label: str) -> None:
    identities = (path.absolute(), path.resolve())
    for root in (predecessor_root, candidate_root):
        for identity in identities:
            if identity == root or identity.is_relative_to(root) or root.is_relative_to(identity):
                raise ValueError(f"{label} must remain outside predecessor and candidate roots")


def parse_object(content: bytes, path: Path) -> dict[str, Any]:
    try:
        document = json.loads(content)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"malformed JSON document: {path}") from error
    if not isinstance(document, dict):
        raise ValueError(f"document must be a JSON object: {path}")
    return document


def require_exact_keys(document: dict[str, Any], expected: set[str], label: str) -> None:
    if set(document) != expected:
        raise ValueError(f"{label} has missing or extra fields")


def require_capture_id(value: Any) -> str:
    if not isinstance(value, str) or not value or CAPTURE_ID.fullmatch(value) is None \
            or value in {".", ".."} or "/" in value or "\\" in value:
        raise ValueError("capture identity must be printable ASCII without path separators")
    return value


def require_string(document: dict[str, Any], key: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{key} must be a non-empty string")
    return value
