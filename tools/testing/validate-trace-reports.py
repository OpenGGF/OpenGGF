#!/usr/bin/env python3
"""Fail-closed validation for owner-keyed trace report evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


OWNER_KEY = re.compile(r"[0-9a-f]{64}")
OWNER_HASH = re.compile(r"[0-9a-f]{16}")
OWNER_FIELDS = {"logical_key", "owner_key", "physical_path"}
REQUIRED_VERIFICATION_GROUPS = {"physics", "animation"}
RUN_CHAIN_SEGMENT_LANE = re.compile(r"segment-([0-9]+)")
RUN_CHAIN_INTERIOR_LANE = re.compile(r"segment-([0-9]+)-dynamic-art")


@dataclass(frozen=True)
class ReportEvidence:
    path: Path
    profile: str
    logical_key: str
    lane: str
    owner_key: str
    schema: str
    error_count: int | None
    warning_count: int | None


def safe_component(value: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", value)
    if not safe:
        return "unnamed"
    return f"_{safe}" if safe in {".", ".."} else safe


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate target-local owner-keyed trace reports and sidecars.")
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--fail-on-warnings", action="store_true")
    parser.add_argument("--warning-context", default="blocking")
    parser.add_argument("--require-clean-profile")
    parser.add_argument("--require-clean-logical-key")
    parser.add_argument("--require-clean-lane")
    arguments = parser.parse_args()

    selector = (
        arguments.require_clean_profile,
        arguments.require_clean_logical_key,
        arguments.require_clean_lane,
    )
    if any(value is not None for value in selector) and not all(
            value is not None for value in selector):
        parser.error("all --require-clean-* selector arguments must be supplied together")
    return arguments


def read_json(path: Path, description: str, errors: list[str]) -> object | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        errors.append(f"{path}: malformed {description}: {failure}")
        return None


def require_count(payload: dict[str, object], field: str, report: Path,
                  errors: list[str]) -> int | None:
    if field not in payload:
        errors.append(f"{report}: payload is missing required {field}")
        return None
    value = payload[field]
    if type(value) is not int:
        errors.append(f"{report}: {field} must be a JSON integer, got {type(value).__name__}")
        return None
    if value < 0:
        errors.append(f"{report}: {field} must be nonnegative, got {value}")
        return None
    return value


def require_object(payload: dict[str, object], field: str, report: Path,
                   errors: list[str]) -> dict[str, object] | None:
    if field not in payload:
        errors.append(f"{report}: payload is missing required {field}")
        return None
    value = payload[field]
    if not isinstance(value, dict):
        errors.append(f"{report}: {field} must be a JSON object")
        return None
    return value


def require_list(payload: dict[str, object], field: str, report: Path,
                 errors: list[str]) -> list[object] | None:
    if field not in payload:
        errors.append(f"{report}: payload is missing required {field}")
        return None
    value = payload[field]
    if not isinstance(value, list):
        errors.append(f"{report}: {field} must be a JSON array")
        return None
    return value


def require_string(payload: dict[str, object], field: str, report: Path,
                   errors: list[str], *, nonblank: bool = True) -> str | None:
    if field not in payload:
        errors.append(f"{report}: payload is missing required {field}")
        return None
    value = payload[field]
    if not isinstance(value, str) or (nonblank and not value.strip()):
        suffix = "nonblank " if nonblank else ""
        errors.append(f"{report}: {field} must be a {suffix}JSON string")
        return None
    return value


def require_boolean(payload: dict[str, object], field: str, report: Path,
                    errors: list[str]) -> bool | None:
    if field not in payload:
        errors.append(f"{report}: payload is missing required {field}")
        return None
    value = payload[field]
    if type(value) is not bool:
        errors.append(f"{report}: {field} must be a JSON boolean")
        return None
    return value


def validate_string_list(values: list[object], field: str, report: Path,
                         errors: list[str]) -> None:
    for index, value in enumerate(values):
        if not isinstance(value, str):
            errors.append(f"{report}: {field}[{index}] must be a JSON string")


def validate_run_chain_mismatch(item: object, field: str, report: Path,
                                errors: list[str]) -> None:
    if not isinstance(item, dict):
        errors.append(f"{report}: {field} entry must be a JSON object")
        return
    require_count(item, "frame", report, errors)
    for key in ("field", "romValue", "engineValue", "delta", "severity"):
        require_string(item, key, report, errors, nonblank=key in {"field", "severity"})
    require_count(item, "repeatCount", report, errors)


def validate_verification_groups(groups: dict[str, object], report: Path,
                                 errors: list[str]) -> None:
    if set(groups) != REQUIRED_VERIFICATION_GROUPS:
        errors.append(
            f"{report}: verification_groups must contain exactly "
            f"{sorted(REQUIRED_VERIFICATION_GROUPS)}, got {sorted(groups)}")
    for group_name in sorted(REQUIRED_VERIFICATION_GROUPS):
        group = groups.get(group_name)
        if not isinstance(group, dict):
            errors.append(
                f"{report}: verification_groups.{group_name} must be a JSON object")
            continue
        field_name = f"verification_groups.{group_name}.error_count"
        if "error_count" not in group:
            errors.append(
                f"{report}: verification_groups.{group_name} is missing required error_count")
            continue
        value = group["error_count"]
        if type(value) is not int:
            errors.append(
                f"{report}: {field_name} must be a JSON integer, "
                f"got {type(value).__name__}")
        elif value < 0:
            errors.append(f"{report}: {field_name} must be nonnegative, got {value}")


def validate_dynamic_art_gap(payload: dict[str, object], logical_key: str,
                             report: Path, errors: list[str]) -> tuple[str, int | None, None]:
    run_id = require_string(payload, "runId", report, errors)
    if run_id is not None and logical_key != f"{run_id}_dynamic_art_gap":
        errors.append(
            f"{report}: dynamic-art-gap logical_key must equal "
            f"{run_id!r} + '_dynamic_art_gap'")
    gap_count = require_count(payload, "gapCount", report, errors)
    failure_count = require_count(payload, "failureCount", report, errors)
    failures = require_list(payload, "failures", report, errors)
    gaps = require_list(payload, "gaps", report, errors)
    if failures is not None:
        validate_string_list(failures, "failures", report, errors)
        if failure_count is not None and failure_count != len(failures):
            errors.append(
                f"{report}: failureCount={failure_count} does not match failures size "
                f"{len(failures)}")
    if gaps is not None:
        if gap_count is not None and gap_count != len(gaps):
            errors.append(
                f"{report}: gapCount={gap_count} does not match gaps size {len(gaps)}")
        for index, item in enumerate(gaps):
            if not isinstance(item, dict):
                errors.append(f"{report}: gaps[{index}] must be a JSON object")
                continue
            for key in ("representedSegmentDir", "nextSegmentDir"):
                require_string(item, key, report, errors)
            for key in (
                    "gapStartMovieLogicalFrame", "nextSegmentArmMovieLogicalFrame",
                    "transitionCountAtGapStart", "transitionCountAfterNextArm"):
                require_count(item, key, report, errors)
            transitions = require_list(
                item, "transitionsAddedAcrossBoundary", report, errors)
            if transitions is not None:
                validate_string_list(
                    transitions, "transitionsAddedAcrossBoundary", report, errors)
    return "dynamic-art-gap", failure_count, None


def validate_dynamic_art_interior(
        payload: dict[str, object], logical_key: str, segment_index: str,
        report: Path, errors: list[str]) -> tuple[str, int | None, None]:
    if not logical_key.endswith(f"_seg{segment_index}_dynamic_art"):
        errors.append(
            f"{report}: dynamic-art interior logical_key must end with "
            f"_seg{segment_index}_dynamic_art")
    require_count(payload, "comparisonCount", report, errors)
    error_count = require_count(payload, "errorCount", report, errors)
    mismatches = require_list(payload, "mismatches", report, errors)
    if mismatches is not None:
        for index, item in enumerate(mismatches):
            if not isinstance(item, dict):
                errors.append(f"{report}: mismatches[{index}] must be a JSON object")
                continue
            require_count(item, "frame", report, errors)
            require_string(item, "field", report, errors)
            require_string(item, "expected", report, errors, nonblank=False)
            require_string(item, "actual", report, errors, nonblank=False)
            require_string(item, "severity", report, errors)
    return "dynamic-art-interior", error_count, None


def validate_run_chain_segment(
        payload: dict[str, object], logical_key: str, segment_index: str,
        report: Path, errors: list[str]) -> tuple[str, int | None, int | None]:
    if not logical_key.endswith(f"_seg{segment_index}"):
        errors.append(
            f"{report}: run-chain segment logical_key must end with _seg{segment_index}")
    error_count = require_count(payload, "errorCount", report, errors)
    warning_count = require_count(payload, "warningCount", report, errors)
    require_count(payload, "laggedFrames", report, errors)
    require_boolean(payload, "complete", report, errors)
    recent = require_list(payload, "recentMismatches", report, errors)
    if recent is not None:
        for item in recent:
            validate_run_chain_mismatch(item, "recentMismatches", report, errors)
    verification_groups = require_object(
        payload, "verification_groups", report, errors)
    if verification_groups is not None:
        validate_verification_groups(verification_groups, report, errors)
    require_count(payload, "bootstrapErrorCount", report, errors)
    first = payload.get("firstNonCameraPhysicsMismatch")
    if first is not None:
        validate_run_chain_mismatch(
            {**first, "repeatCount": 0} if isinstance(first, dict) else first,
            "firstNonCameraPhysicsMismatch", report, errors)
    return "run-chain-segment", error_count, warning_count


def validate_payload(payload: dict[str, object], profile: str, logical_key: str,
                     lane: str, report: Path,
                     errors: list[str]) -> tuple[str, int | None, int | None] | None:
    if profile in {"trace", "special-stage"}:
        return (
            "divergence",
            require_count(payload, "error_count", report, errors),
            require_count(payload, "warning_count", report, errors),
        )
    if profile == "run-chain" and lane == "dynamic-art-gap":
        return validate_dynamic_art_gap(payload, logical_key, report, errors)
    interior = RUN_CHAIN_INTERIOR_LANE.fullmatch(lane)
    if profile == "run-chain" and interior is not None:
        return validate_dynamic_art_interior(
            payload, logical_key, interior.group(1), report, errors)
    segment = RUN_CHAIN_SEGMENT_LANE.fullmatch(lane)
    if profile == "run-chain" and segment is not None:
        return validate_run_chain_segment(
            payload, logical_key, segment.group(1), report, errors)
    errors.append(
        f"{report}: unknown owner-keyed report schema for profile={profile!r}, lane={lane!r}")
    return None


def resolved_within(path: Path, root: Path, description: str,
                    errors: list[str]) -> Path | None:
    try:
        resolved = path.resolve(strict=True)
    except (OSError, RuntimeError) as failure:
        errors.append(f"{path}: cannot resolve {description}: {failure}")
        return None
    try:
        resolved.relative_to(root)
    except ValueError:
        errors.append(f"{path}: {description} escapes trace report root {root}")
        return None
    return resolved


def validate_report(report: Path, root: Path, root_resolved: Path,
                    errors: list[str]) -> ReportEvidence | None:
    if report.parent.parent != root:
        errors.append(
            f"{report}: report must be exactly one profile directory below {root}")
        return None
    if report.is_symlink():
        errors.append(f"{report}: report payload must not be a symbolic link")
        return None
    report_resolved = resolved_within(report, root_resolved, "report payload", errors)
    if report_resolved is None:
        return None

    profile = report.parent.name
    if not profile or safe_component(profile) != profile:
        errors.append(f"{report}: profile directory is not a safe component")
        return None

    sidecar = Path(f"{report}.owner.json")
    if not sidecar.is_file():
        errors.append(f"{report}: missing owner sidecar {sidecar}")
        return None
    if sidecar.is_symlink():
        errors.append(f"{sidecar}: owner sidecar must not be a symbolic link")
        return None
    if resolved_within(sidecar, root_resolved, "owner sidecar", errors) is None:
        return None

    payload_value = read_json(report, "report JSON", errors)
    sidecar_value = read_json(sidecar, "owner JSON", errors)
    if payload_value is None or sidecar_value is None:
        return None
    if not isinstance(payload_value, dict):
        errors.append(f"{report}: payload JSON must be an object")
        return None
    if not isinstance(sidecar_value, dict):
        errors.append(f"{sidecar}: owner JSON must be an object")
        return None

    if set(sidecar_value) != OWNER_FIELDS:
        errors.append(
            f"{sidecar}: owner JSON fields must be exactly {sorted(OWNER_FIELDS)}, "
            f"got {sorted(sidecar_value)}")
        return None
    logical_key = sidecar_value["logical_key"]
    owner_key = sidecar_value["owner_key"]
    physical_path = sidecar_value["physical_path"]
    if not isinstance(logical_key, str) or not logical_key.strip():
        errors.append(f"{sidecar}: logical_key must be a nonblank string")
        return None
    if not isinstance(owner_key, str) or OWNER_KEY.fullmatch(owner_key) is None:
        errors.append(f"{sidecar}: owner_key must be 64 lowercase hexadecimal characters")
        return None
    if not isinstance(physical_path, str) or not physical_path.strip():
        errors.append(f"{sidecar}: physical_path must be a nonblank string")
        return None

    stem = report.name.removesuffix(".json")
    logical_component = safe_component(logical_key)
    logical_prefix = f"{logical_component}-"
    if not stem.startswith(logical_prefix):
        errors.append(
            f"{report}: filename logical key does not match sidecar logical_key "
            f"{logical_key!r} (safe component {logical_component!r})")
        return None
    lane_and_hash = stem[len(logical_prefix):]
    if "-" not in lane_and_hash:
        errors.append(f"{report}: filename is missing lane and owner hash")
        return None
    lane, filename_hash = lane_and_hash.rsplit("-", 1)
    if not lane or safe_component(lane) != lane:
        errors.append(f"{report}: filename lane is not a nonblank safe component")
        return None
    if OWNER_HASH.fullmatch(filename_hash) is None:
        errors.append(f"{report}: filename owner hash must be 16 lowercase hexadecimal characters")
        return None
    if filename_hash != owner_key[:16]:
        errors.append(
            f"{report}: filename owner hash {filename_hash} does not match owner_key prefix")
        return None

    metadata_path = Path(physical_path)
    if not metadata_path.is_absolute():
        metadata_path = Path.cwd() / metadata_path
    try:
        metadata_resolved = metadata_path.resolve(strict=True)
    except (OSError, RuntimeError) as failure:
        errors.append(f"{sidecar}: cannot resolve physical_path {physical_path!r}: {failure}")
        return None
    try:
        metadata_resolved.relative_to(root_resolved)
    except ValueError:
        errors.append(f"{sidecar}: physical_path escapes trace report root: {physical_path!r}")
        return None
    if metadata_resolved != report_resolved:
        errors.append(
            f"{sidecar}: physical_path does not resolve to report: "
            f"{metadata_resolved} != {report_resolved}")
        return None

    validated_payload = validate_payload(
        payload_value, profile, logical_key, lane, report, errors)
    if validated_payload is None:
        return None
    schema, error_count, warning_count = validated_payload
    return ReportEvidence(
        report, profile, logical_key, lane, owner_key, schema,
        error_count, warning_count)


def main() -> int:
    arguments = parse_arguments()
    errors: list[str] = []
    root = arguments.root.absolute()
    try:
        root_resolved = root.resolve(strict=True)
    except (OSError, RuntimeError) as failure:
        print(f"Trace report validation failed: cannot resolve {root}: {failure}",
              file=sys.stderr)
        return 1
    if not root_resolved.is_dir():
        print(f"Trace report validation failed: {root} is not a directory", file=sys.stderr)
        return 1

    sidecars = sorted(root.rglob("*.json.owner.json"))
    reports = sorted(
        path for path in root.rglob("*.json")
        if not path.name.endswith(".owner.json"))
    if not reports:
        errors.append(f"No owner-keyed trace reports found below {root}")

    report_set = set(reports)
    for sidecar in sidecars:
        report_name = sidecar.name.removesuffix(".owner.json")
        report = sidecar.with_name(report_name)
        if report not in report_set:
            errors.append(f"{sidecar}: orphan owner sidecar has no report payload")

    evidence: list[ReportEvidence] = []
    for report in reports:
        validated = validate_report(report, root, root_resolved, errors)
        if validated is not None:
            evidence.append(validated)

    owners: dict[str, Path] = {}
    for item in evidence:
        prior_owner = owners.get(item.owner_key)
        if prior_owner is not None:
            errors.append(
                f"{item.path}: duplicate owner_key; already used by {prior_owner}")
        else:
            owners[item.owner_key] = item.path

    if arguments.fail_on_warnings:
        for item in evidence:
            if item.warning_count is not None and item.warning_count != 0:
                warning_field = (
                    "warning_count" if item.schema == "divergence" else "warningCount")
                errors.append(
                    f"{item.path}: Trace replay warnings are "
                    f"{arguments.warning_context}; "
                    f"{warning_field}={item.warning_count}")

    selector = (
        arguments.require_clean_profile,
        arguments.require_clean_logical_key,
        arguments.require_clean_lane,
    )
    if all(value is not None for value in selector):
        selected = [
            item for item in evidence
            if (item.profile, item.logical_key, item.lane) == selector
        ]
        if len(selected) != 1:
            errors.append(
                f"Required keep-green trace report {selector} must resolve exactly once; "
                f"found {len(selected)}")
        elif selected[0].schema != "divergence":
            errors.append(
                f"{selected[0].path}: required keep-green trace must use divergence schema")
        elif selected[0].error_count != 0 or selected[0].warning_count != 0:
            errors.append(
                f"{selected[0].path}: required keep-green trace is red: "
                f"error_count={selected[0].error_count} "
                f"warning_count={selected[0].warning_count}")

    if errors:
        print("Trace report validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print(f"Validated {len(evidence)} owner-keyed trace reports below {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
