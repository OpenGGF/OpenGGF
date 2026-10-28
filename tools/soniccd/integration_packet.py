#!/usr/bin/env python3
"""Inspect reference custody, frozen handoffs, source-bound runs and byte preservation.

Inputs: external preservation recipe with pinned frozen baseline/FINAL text and
baseline-coordinate Base64 edits; or packet/draft pins and explicit Git worktree;
or exact Surefire classes and UTC cutoffs, optionally compared across two roots.
Only run --execute launches tests,
through the existing queue; other read-only modes never execute supplied code.
Origin: 2026-10-26 private Sonic CD packet preservation tooling task. Workflow:
.agents/skills/soniccd-packet-integration/SKILL.md. Output is stdout/stderr only.
"""
from __future__ import annotations

import argparse
import base64
import binascii
from contextlib import contextmanager, ExitStack
from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def worktree_build_slot(root):
    # Read-only commands need no queue module or lease. Apply retains the existing lock.
    sys.path.insert(0, str(ROOT / "tools/testing"))
    from maven_queue import worktree_build_slot as build_slot
    return build_slot(root)


HASH = re.compile(r"[0-9a-f]{64}\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
CLASS = re.compile(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\Z", re.ASCII)
PROPERTIES = frozenset({"java.version", "user.dir", "openggf.headless.gl",
                        "soniccd.media.root", "soniccd.opening.egl", "sonic1.rom.path", "sonic2.rom.path", "s3k.rom.path"})
# Source packets are bounded UTF-8 text; refusing binary and oversized inputs is intentional.
MAX_FILE = 16 * 1024 * 1024
MAX_PACKET = 64 * 1024 * 1024
# Preservation is a small explicit recipe, not a general patch engine.
MAX_RECIPE = 4 * 1024 * 1024
MAX_EDITS = 4096
MAX_EDIT_BYTES = 1024 * 1024  # aggregate decoded before + after bytes


class PacketError(ValueError):
    def __init__(self, message, report=None):
        super().__init__(message)
        self.report = report or {}


def require(condition, message):
    if not condition:
        raise PacketError(message)


def keys(value, required, label):
    require(isinstance(value, dict) and set(value) == set(required),
            f"{label}: require exactly keys {', '.join(required)}")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def signature(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mode, info.st_mtime_ns, info.st_ctime_ns)


def canonical(path):
    try:
        resolved = path.resolve()
    except (OSError, RuntimeError) as error:
        raise PacketError(f"Cannot resolve path without symlinks: {path}: {error}") from error
    require(path.is_absolute() and path == resolved, f"Require canonical absolute path without symlinks: {path}")
    # resolve() alone normalizes '..'; reject lexical aliases as well.
    require(".." not in path.parts, f"Parent traversal: {path}")
    return path


def worktree(path):
    root = canonical(Path(path))
    require(root.is_dir(), f"Not a worktree directory: {root}")
    try:
        top = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=root, text=True,
                                      stderr=subprocess.PIPE).strip()
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True,
                                       stderr=subprocess.PIPE).strip()
    except subprocess.CalledProcessError as error:
        raise PacketError(f"Not a committed Git worktree: {root}") from error
    require(Path(top) == root, f"Select the exact worktree root, not a subdirectory: {root} (owner {top})")
    return root, head


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_manifest(path):
    with Path(path).open(encoding="utf-8") as stream:
        value = json.load(stream, object_pairs_hook=unique)
    keys(value, ("version", "worktree_head", "files"), "manifest")
    require(type(value["version"]) is int and value["version"] == 1, "Unsupported manifest version")
    require(isinstance(value["worktree_head"], str) and COMMIT.fullmatch(value["worktree_head"]),
            "worktree_head must be an exact lowercase Git commit SHA")
    require(isinstance(value["files"], list) and bool(value["files"]), "Empty/non-array packet")
    return value


@dataclass
class Snapshot:
    path: Path
    data: bytes | None
    info: os.stat_result | None

    def public(self):
        if self.info is None:
            return {"absent": True}
        return dict(sha256=digest(self.data), bytes=len(self.data),
                    mode=f"{stat.S_IMODE(self.info.st_mode):04o}", mtime_ns=self.info.st_mtime_ns)


def snapshot(path, *, absent=False):
    canonical(path)
    try:
        before = path.lstat()
    except FileNotFoundError:
        require(absent, f"Missing file: {path}")
        return Snapshot(path, None, None)
    require(stat.S_ISREG(before.st_mode), f"Not a regular file: {path}")
    require(before.st_size <= MAX_FILE, f"File exceeds {MAX_FILE}-byte text-packet limit: {path}")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
    with os.fdopen(fd, "rb") as stream:
        require(signature(os.fstat(stream.fileno())) == signature(before), f"File changed before read: {path}")
        data = stream.read(MAX_FILE + 1)
        after = os.fstat(stream.fileno())
    require(signature(before) == signature(after) == signature(path.lstat()) and len(data) == before.st_size,
            f"File changed during read: {path}")
    canonical(path)
    return Snapshot(path, data, before)


def destination(root, value):
    require(isinstance(value, str) and bool(value) and "\\" not in value and ":" not in value,
            f"Destination must be an explicit relative POSIX path: {value!r}")
    parts = value.split("/")
    require(all(part and part not in (".", "..") and part.lower() != ".git" for part in parts),
            f"Unsafe destination: {value!r}")
    path = canonical(root / value)
    require(path.is_relative_to(root) and path != root, f"Destination outside worktree: {path}")
    require(path.parent.is_dir(), f"Create/review destination directories separately before import: {path.parent}")
    return path


def validate_packet(root, manifest):
    root, head = worktree(root)
    require(head == manifest["worktree_head"], f"Worktree HEAD current={head} expected={manifest['worktree_head']}")
    planned, errors, seen = [], [], set()
    for index, entry in enumerate(manifest["files"]):
        try:
            keys(entry, ("source", "sha256", "bytes", "mode", "utf8", "lf", "destination", "expected"),
                 f"files[{index}]")
            require(isinstance(entry["source"], str), "source must be an absolute path string")
            source_path = canonical(Path(entry["source"]))
            require(not source_path.is_relative_to(root), f"Source must be external to selected worktree: {source_path}")
            require(isinstance(entry["sha256"], str) and HASH.fullmatch(entry["sha256"]), "Malformed source SHA256")
            require(type(entry["bytes"]) is int and 0 <= entry["bytes"] <= MAX_FILE, "Malformed source byte length")
            require(entry["mode"] == "0444" and entry["utf8"] is True and entry["lf"] is True,
                    "Source contract requires mode=0444, utf8=true, lf=true")
            target = destination(root, entry["destination"])
            require(target not in seen, f"Duplicate/conflicting destination: {target}")
            seen.add(target)
            expected = entry["expected"]
            require(isinstance(expected, dict), "expected must pin SHA256 or explicitly absent=true")
            if set(expected) == {"sha256"}:
                require(isinstance(expected["sha256"], str) and HASH.fullmatch(expected["sha256"]),
                        "Malformed destination baseline SHA256")
            else:
                require(expected == {"absent": True} and expected["absent"] is True,
                        "expected must pin SHA256 or explicitly absent=true")
            source = snapshot(source_path)
            current = snapshot(target, absent=True)
            require(stat.S_IMODE(source.info.st_mode) == 0o444,
                    f"Source is not frozen 0444: {source_path} current={stat.S_IMODE(source.info.st_mode):04o}")
            require(digest(source.data) == entry["sha256"] and len(source.data) == entry["bytes"],
                    f"Source current={source.public()} expected SHA256={entry['sha256']} bytes={entry['bytes']}")
            source.data.decode("utf-8", errors="strict")
            require(b"\r" not in source.data and b"\0" not in source.data and
                    (not source.data or source.data.endswith(b"\n")), f"Require LF-terminated UTF-8 text without CR/NUL: {source_path}")
            require((current.info is None and expected == {"absent": True}) or
                    (current.info is not None and expected == {"sha256": digest(current.data)}),
                    f"Baseline changed: {target} current={current.public()} expected={expected}")
            planned.append((source, current, expected))
        except (ValueError, OSError) as error:
            errors.append(dict(index=index, source=entry.get("source") if isinstance(entry, dict) else None,
                               destination=str(root / str(entry.get("destination", ""))) if isinstance(entry, dict) else None,
                               error=str(error)))
    # Check all source/destination aliases, including cross-entry aliases.
    source_ids = {(s.info.st_dev, s.info.st_ino) for s, _, _ in planned}
    destination_ids = set()
    for _, current, _ in planned:
        if current.info is not None:
            identity = (current.info.st_dev, current.info.st_ino)
            if identity in source_ids or identity in destination_ids:
                errors.append(dict(destination=str(current.path), error="Source/destination or destination hardlink alias"))
            destination_ids.add(identity)
    require(sum(len(source.data) for source, _, _ in planned) <= MAX_PACKET, "Packet exceeds 64 MiB limit")
    if errors:
        raise PacketError("Packet rejected; no files written", dict(issues=errors))
    return root, head, planned


def draft_manifest(root, spec_path):
    """Observe a pinned external mapping and destination state; never approve or write.

    Rechecks detect observed changes, not an atomic filesystem snapshot. The caller
    must review stdout and establish source custody before normal packet/apply.
    """
    root, head = worktree(root)
    require(isinstance(head, str) and COMMIT.fullmatch(head), "HEAD must be an exact lowercase Git commit SHA")
    spec_path = canonical(Path(spec_path))
    require(not spec_path.is_relative_to(root), "Draft spec must be external to selected worktree")
    spec = snapshot(spec_path)
    try:
        value = json.loads(spec.data.decode("utf-8", errors="strict"), object_pairs_hook=unique)
    except RecursionError as error:
        raise PacketError("Draft spec JSON nesting exceeds decoder limit") from error
    keys(value, ("version", "files"), "draft spec")
    require(type(value["version"]) is int and value["version"] == 1, "Unsupported draft spec version")
    require(isinstance(value["files"], list) and bool(value["files"]), "Empty/non-array draft files")
    total = 0
    for index, entry in enumerate(value["files"]):
        keys(entry, ("source", "sha256", "bytes", "destination"), f"draft files[{index}]")
        require(type(entry["bytes"]) is int and 0 <= entry["bytes"] <= MAX_FILE, "Malformed source byte length")
        total += entry["bytes"]
    require(total <= MAX_PACKET, "Packet exceeds 64 MiB limit")
    manifest = dict(version=1, worktree_head=head, files=[])
    baselines, directories = [], {}
    for entry in value["files"]:
        target = destination(root, entry["destination"])
        # Track directory identities/metadata too, including absent destinations.
        parent = target.parent
        while parent.is_relative_to(root):
            info = parent.stat()
            require(stat.S_ISDIR(info.st_mode), f"Not a destination directory: {parent}")
            if parent in directories:
                require(directories[parent] == signature(info), f"Destination directory changed: {parent}")
            else:
                directories[parent] = signature(info)
            if parent == root:
                break
            parent = parent.parent
        current = snapshot(target, absent=True)
        require(current.info is None or not os.path.samestat(current.info, spec.info),
                f"Draft spec/destination hardlink alias: {target}")
        expected = {"absent": True} if current.info is None else {"sha256": digest(current.data)}
        manifest["files"].append(dict(entry, mode="0444", utf8=True, lf=True, expected=expected))
        baselines.append(current)
    # Share packet's frozen-source, UTF-8/LF, path, alias and budget contracts.
    _, _, planned = validate_packet(root, manifest)
    unchanged(spec, message="File changed during draft")
    for source, current, _ in planned:
        unchanged(source, message="File changed during draft")
        unchanged(current, message="File changed during draft")
    for current in baselines:
        unchanged(current, message="File changed during draft")
    for parent, saved in directories.items():
        canonical(parent)
        require(signature(parent.stat()) == saved, f"Destination directory changed: {parent}")
    _, final_head = worktree(root)
    require(final_head == head, f"Worktree HEAD changed during draft: current={final_head} expected={head}")
    return manifest


def plan_report(root, head, planned):
    return dict(worktree=str(root), head=head, applied=False,
                files=[dict(source=str(source.path), destination=str(current.path), expected=expected,
                            current=current.public(), incoming=source.public(), destination_mode="0644")
                       for source, current, expected in planned])


@contextmanager
def parent_fd(root, path):
    """Open each parent without following links; replacement stays in that directory."""
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    with ExitStack() as stack:
        fd = os.open(root, flags)
        stack.callback(os.close, fd)
        for part in path.relative_to(root).parts[:-1]:
            fd = os.open(part, flags, dir_fd=fd)
            stack.callback(os.close, fd)
        require(os.path.samestat(os.fstat(fd), path.parent.stat()), f"Destination parent changed: {path}")
        yield fd


def unchanged(saved, *, message="Concurrent file change; refusing replacement"):
    current = snapshot(saved.path, absent=True)
    require((saved.info is None and current.info is None) or
            (saved.info is not None and current.info is not None and
             signature(saved.info) == signature(current.info) and saved.data == current.data),
            f"{message}: {saved.path}")


def reconstruct_preservation(data, edits, *, inverse=False):
    """Splice declared bytes in linear space; inverse offsets include prior deltas."""
    pieces, cursor, delta = [], 0, 0
    for index, (offset, before, after) in enumerate(edits):
        start = offset + delta if inverse else offset
        old, new = (after, before) if inverse else (before, after)
        require(data[start:start + len(old)] == old,
                f"{'Inverse' if inverse else 'Before'} mismatch: edit={index} baseline_offset={offset}")
        pieces.extend((data[cursor:start], new))
        cursor = start + len(old)
        delta += len(after) - len(before)
    pieces.append(data[cursor:])
    return b"".join(pieces)


def preservation_equal(actual, expected, label):
    if actual != expected:
        offset = next((i for i, (a, b) in enumerate(zip(actual, expected)) if a != b),
                      min(len(actual), len(expected)))
        raise PacketError(f"{label} mismatch at byte offset {offset}; "
                          f"expected_bytes={len(expected)} actual_bytes={len(actual)}")


def verify_preservation(recipe_path):
    """Observe two pinned files and both whole-file transformations; never write.

    File/directory rechecks are non-atomic. Custody and semantic acceptance remain
    with the caller; 0444 does not prevent an owner from changing an input.
    """
    directories = {}

    def external(value):
        require(isinstance(value, str) and 0 < len(value) <= 4096 and
                "\0" not in value and str(Path(value)) == value,
                "Require an explicit canonical absolute external path (limit 4096 characters)")
        path = canonical(Path(value))
        require(not path.is_relative_to(ROOT), "Preservation inputs must be external to the tool checkout")
        for parent in path.parents:
            info = parent.lstat()
            require(stat.S_ISDIR(info.st_mode), "Preservation parent is not a regular directory")
            identity = (info.st_dev, info.st_ino, info.st_mode)
            if parent in directories:
                require(directories[parent] == identity, "Preservation parent directory changed")
            else:
                directories[parent] = identity
        return path

    def bounded_snapshot(path, limit):
        require(path.lstat().st_size <= limit, f"Preservation input exceeds {limit}-byte limit")
        saved = snapshot(path)
        require(len(saved.data) <= limit, f"Preservation input exceeds {limit}-byte limit")
        return saved

    recipe = bounded_snapshot(external(str(recipe_path)), MAX_RECIPE)

    def nonfinite(_):
        raise PacketError("Nonfinite JSON constants are not supported")

    def recipe_keys(pairs):
        result = {}
        for key, item in pairs:
            require(key not in result, "Duplicate JSON key in preservation recipe")
            result[key] = item
        return result

    try:
        value = json.loads(recipe.data.decode("utf-8", errors="strict"),
                           object_pairs_hook=recipe_keys, parse_constant=nonfinite)
    except RecursionError as error:
        raise PacketError("Preservation recipe nesting exceeds decoder limit") from error
    keys(value, ("version", "baseline", "final", "edits"), "preservation recipe")
    require(type(value["version"]) is int and value["version"] == 1, "Unsupported preservation recipe version")
    require(isinstance(value["edits"], list) and len(value["edits"]) <= MAX_EDITS,
            f"Edits must be an array within {MAX_EDITS}-edit limit")
    files, identities = {}, {(recipe.info.st_dev, recipe.info.st_ino)}
    for name in ("baseline", "final"):
        entry = value[name]
        keys(entry, ("path", "sha256", "bytes"), name)
        require(isinstance(entry["sha256"], str) and HASH.fullmatch(entry["sha256"]),
                f"{name}: require externally supplied lowercase SHA256")
        require(type(entry["bytes"]) is int and 0 <= entry["bytes"] <= MAX_FILE,
                f"{name}: byte pin exceeds {MAX_FILE}-byte limit or has wrong type")
        path = external(entry["path"])
        require(path not in (recipe.path, *(saved.path for saved in files.values())),
                "Preservation input path alias")
        saved = bounded_snapshot(path, MAX_FILE)
        identity = (saved.info.st_dev, saved.info.st_ino)
        require(identity not in identities, "Preservation input hardlink alias")
        identities.add(identity)
        require(stat.S_IMODE(saved.info.st_mode) == 0o444, f"{name}: require frozen mode 0444")
        require(len(saved.data) == entry["bytes"] and digest(saved.data) == entry["sha256"],
                f"{name}: external SHA256/byte pin mismatch")
        saved.data.decode("utf-8", errors="strict")
        require(b"\r" not in saved.data and b"\0" not in saved.data and
                (not saved.data or saved.data.endswith(b"\n")),
                f"{name}: require LF-terminated UTF-8 text without CR/NUL")
        files[name] = saved

    edits, decoded, last, cursor = [], 0, -1, 0
    expected_size = len(files["baseline"].data)
    for index, entry in enumerate(value["edits"]):
        keys(entry, ("offset", "before", "after"), f"edits[{index}]")
        offset = entry["offset"]
        require(type(offset) is int and last < offset <= len(files["baseline"].data) and offset >= cursor,
                f"Invalid, duplicate, decreasing or overlapping baseline offset: edit={index}")
        fragments = []
        for name in ("before", "after"):
            encoded = entry[name]
            require(isinstance(encoded, str), f"edit={index}: {name} must be standard Base64 text")
            # Bound allocation before decoding, then enforce the precise aggregate size.
            require(len(encoded) <= 4 * ((MAX_EDIT_BYTES - decoded + 2) // 3),
                    "Decoded edit bytes exceed aggregate limit")
            try:
                fragment = base64.b64decode(encoded, validate=True)
            except (ValueError, binascii.Error) as error:
                raise PacketError(f"edit={index}: malformed standard Base64 {name}") from error
            require(base64.b64encode(fragment).decode("ascii") == encoded,
                    f"edit={index}: noncanonical standard Base64 {name}")
            decoded += len(fragment)
            require(decoded <= MAX_EDIT_BYTES, "Decoded edit bytes exceed aggregate limit")
            fragments.append(fragment)
        before, after = fragments
        require(before or after, f"edit={index}: empty/empty edit is not allowed")
        require(len(before) <= len(files["baseline"].data) - offset,
                f"Before range exceeds baseline at offset: edit={index}")
        edits.append((offset, before, after))
        last, cursor = offset, offset + len(before)
        expected_size += len(after) - len(before)
    require(0 <= expected_size <= MAX_FILE, f"Reconstructed file exceeds {MAX_FILE}-byte limit")
    baseline, final = files["baseline"].data, files["final"].data
    expected = reconstruct_preservation(baseline, edits)
    preservation_equal(final, expected, "Forward")
    restored = reconstruct_preservation(final, edits, inverse=True)
    preservation_equal(restored, baseline, "Inverse")
    for saved in (recipe, *files.values()):
        unchanged(saved, message="Preservation input changed during verification")
    for parent, identity in directories.items():
        canonical(parent)
        info = parent.lstat()
        require((info.st_dev, info.st_ino, info.st_mode) == identity,
                "Preservation parent directory changed during verification")
    return dict(version=1, baseline={key: value["baseline"][key] for key in ("sha256", "bytes")},
                final={key: value["final"][key] for key in ("sha256", "bytes")}, edits=len(edits),
                forward_equal=True, inverse_equal=True,
                observation="Non-atomic byte observations and rechecks only; no semantic/API/test/native authenticity")


def replace_one(root, source, current):
    with parent_fd(root, current.path) as fd:
        temporary = ".integration-packet-" + secrets.token_hex(12)
        created = False
        failure = None
        try:
            out = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=fd)
            created = True
            with os.fdopen(out, "wb") as stream:
                stream.write(source.data)
                stream.flush()
                os.fchmod(stream.fileno(), 0o644)
                now = time.time_ns()
                os.utime(stream.fileno(), ns=(now, now))
                os.fsync(stream.fileno())
            unchanged(source)
            unchanged(current)
            require(os.path.samestat(os.fstat(fd), current.path.parent.stat()),
                    f"Destination directory changed: {current.path}")
            os.replace(temporary, current.path.name, src_dir_fd=fd, dst_dir_fd=fd)
            created = False
        except (OSError, ValueError) as error:
            failure = error
            raise
        finally:
            if created:
                try:
                    os.unlink(temporary, dir_fd=fd)
                except OSError as error:
                    raise PacketError(f"Owned temporary cleanup failed: {current.path.parent / temporary}: {error}; prior error={failure}") from error


def apply_packet(root, manifest, *, maven_terminal=False):
    require(maven_terminal, "--apply requires --maven-terminal: caller must confirm task-owned queued/running Maven is terminal")
    require(os.name == "posix" and hasattr(os, "O_NOFOLLOW") and hasattr(os, "O_DIRECTORY"),
            "Safe apply requires POSIX directory descriptors and O_NOFOLLOW; use read-only plan here")
    # Check everything before even acquiring a write lease; then repeat after any wait.
    root, _, _ = validate_packet(root, manifest)
    with worktree_build_slot(root):
        root, head, planned = validate_packet(root, manifest)
        result = plan_report(root, head, planned)
        completed = []
        try:
            for source, current, _ in planned:
                replace_one(root, source, current)
                completed.append(str(current.path))
        except (OSError, ValueError) as error:
            raise PacketError(f"Import stopped: {error}; completed replacements are retained, no rollback attempted",
                              dict(completed=completed, remaining=[str(c.path) for _, c, _ in planned[len(completed):]],
                                   partial=bool(completed))) from error
        result.update(applied=True, completed=completed)
        return result


def cutoff(value):
    try:
        stamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
        require(stamp.tzinfo is not None, "--since requires timezone/UTC offset")
        result = stamp.timestamp()
        require(math.isfinite(result) and 0 <= result <= time.time(), "--since must be a past, nonnegative run-start timestamp")
        return int(result * 1_000_000_000)
    except (ValueError, OverflowError) as error:
        raise PacketError(f"Invalid --since: {value!r}: {error}") from error


def requested_properties(values):
    result = {}
    for value in values:
        name, separator, expected = value.partition("=")
        require(separator and name in PROPERTIES and name not in ("java.version", "user.dir"),
                "--property accepts only allowlisted media/EGL properties (see --help); never credentials")
        require(name not in result, f"Duplicate expected property: {name}")
        result[name] = expected
    return result


def junit_summary(root, classes, since, properties, *, _read_report=None):
    # Comparison supplies a bounded reader; existing callers retain snapshot semantics.
    root, head = worktree(root)
    require(classes and len(classes) == len(set(classes)) and all(CLASS.fullmatch(c) for c in classes),
            "Supply distinct exact fully qualified --class names; no patterns")
    reports = canonical(root / "target/surefire-reports")
    require(reports.is_dir(), f"Missing reports directory: {reports}")
    all_paths = sorted(reports.glob("TEST-*.xml"))
    expected = {f"TEST-{name}.xml" for name in classes}
    stale = {path.name for path in all_paths if path.lstat().st_mtime_ns < since}
    require(not (expected & stale), f"Stale XML for requested classes: {sorted(expected & stale)}")
    paths = [path for path in all_paths if path.name not in stale]
    actual = {path.name for path in paths}
    require(actual == expected, f"Report class set mismatch: missing={sorted(expected-actual)}, unexpected={sorted(actual-expected)}")
    result = dict(worktree=str(root), head_at_inspection=head, since_ns=since,
                  attribution="XML observations only; caller must establish command completion and unchanged source",
                  tests=0, failures=0, errors=0, skipped=0, classes=[], issues=[],
                  ignored_stale_reports=len(stale))
    for path in paths:
        saved = (_read_report or snapshot)(path)
        require(saved.info.st_mtime_ns >= since, f"Stale XML: {path} mtime_ns={saved.info.st_mtime_ns} cutoff_ns={since}")
        require(b"<!DOCTYPE" not in saved.data.upper() and b"<!ENTITY" not in saved.data.upper(), f"XML declarations not supported: {path}")
        suite = ET.fromstring(saved.data)
        name = path.name[5:-4]
        require(suite.tag == "testsuite" and suite.get("name") == name, f"XML suite/filename owner mismatch: {path}")
        require(all(node.tag in ("properties", "testcase", "system-out", "system-err") for node in suite),
                f"Unsupported suite-level result structure: {path}")
        prop = {}
        for node in suite.findall("./properties/property"):
            key = node.get("name")
            if key in PROPERTIES:
                require(key not in prop, f"Duplicate allowlisted JVM property {key}: {path}")
                prop[key] = node.get("value")
        selected = {key: prop.get(key) for key in ("java.version", "user.dir", *properties)}
        for key, value in selected.items():
            if value is None or (key in ("java.version", "user.dir") and not value):
                result["issues"].append(f"{name}: missing property {key}")
        if prop.get("user.dir") != str(root):
            result["issues"].append(f"{name}: wrong user.dir expected={root} actual={prop.get('user.dir')}")
        for key, value in properties.items():
            if prop.get(key) != value:
                result["issues"].append(f"{name}: property {key} expected={value!r} actual={prop.get(key)!r}")
        cases, counts, identities = [], dict(tests=0, failures=0, errors=0, skipped=0), set()
        for case in suite.findall("testcase"):
            reported_owner = case.get("classname")
            require(reported_owner in (name, name.rsplit(".", 1)[-1]) and bool(case.get("name")),
                    f"Testcase class/name owner mismatch: {path}")
            require(all(node.tag in ("failure", "error", "skipped", "system-out", "system-err") for node in case),
                    f"Unsupported testcase result structure: {path}")
            identity = name + "#" + case.get("name")
            require(identity not in identities, f"Duplicate test identity: {identity}")
            identities.add(identity)
            outcomes = [node for node in case if node.tag in ("failure", "error", "skipped")]
            require(len(outcomes) <= 1, f"Ambiguous testcase outcome: {identity}")
            outcome = outcomes[0] if outcomes else None
            status = outcome.tag if outcome is not None else "passed"
            counts["tests"] += 1
            if outcome is not None:
                counts[{"failure": "failures", "error": "errors", "skipped": "skipped"}[status]] += 1
            cases.append(dict(identity=identity, status=status,
                              message=outcome.get("message", outcome.text or "") if outcome is not None else "",
                              type=outcome.get("type", "") if outcome is not None else ""))
            if reported_owner != name:
                cases[-1]["reported_classname"] = reported_owner
        require(counts["tests"] > 0, f"No observed testcases: {path}")
        for key, observed in counts.items():
            require(suite.get(key) is not None and suite.get(key).isdigit() and int(suite.get(key)) == observed,
                    f"XML count mismatch {name} {key}: declared={suite.get(key)} observed={observed}")
            result[key] += observed
        unchanged(saved)
        result["classes"].append(dict(name=name, xml=str(path), mtime_ns=saved.info.st_mtime_ns,
                                      properties=selected, counts=counts, cases=cases))
    require({p.name for p in reports.glob("TEST-*.xml") if p.lstat().st_mtime_ns >= since} == expected,
            "Fresh report set changed during inspection")
    result["clean"] = not (result["issues"] or result["failures"] or result["errors"] or result["skipped"])
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    custody = commands.add_parser("custody", help="read-only Git reference and explicit resource-link observations")
    custody.add_argument("--worktree", required=True, help="canonical absolute Git worktree root")
    custody.add_argument("--link", action="append", default=[], metavar="RELATIVE=EXPECTED_ABSOLUTE_TARGET",
                         help="inspect an explicit resource link without reading target bytes; repeat as needed")
    handoff = commands.add_parser("handoff", help="read-only explicit packet/dependency/API/test readiness")
    handoff.add_argument("--worktree", required=True)
    handoff.add_argument("--spec", required=True, type=Path)
    handoff.add_argument("--installed", action="store_true", help="require FINAL incoming bytes already installed")
    handoff.add_argument("--packet-only", action="store_true", help="emit reviewed baseline-ready inner packet; no import")
    run = commands.add_parser("run", help="plan or execute one source-bound exact focused queued test run",
                              allow_abbrev=False)
    run.add_argument("--worktree", required=True)
    run.add_argument("--spec", required=True, type=Path)
    run.add_argument("--lane", choices=("auto","normal","lean"), default="normal")
    run.add_argument("--execute", action="store_true", help="explicitly launch Maven through existing queue")
    run.add_argument("--compact-output", action="store_true",
                     help="compact structured JSON only; verification and queue/Maven stderr unchanged")
    run.add_argument("--maven-progress", choices=("full", "concise"), default="full",
                     help="child Maven stdout display only; concise is incomplete, queue notices stay complete")
    preserve = commands.add_parser("preserve", help="read-only whole-file forward/inverse checks from explicit external pins and edits")
    preserve.add_argument("--recipe", required=True, help="canonical external version-1 JSON recipe; see mirrored skill")
    draft = commands.add_parser("draft", help="read-only manifest draft from externally pinned source spec; review before packet/apply")
    draft.add_argument("--worktree", required=True, help="canonical absolute Git worktree root")
    draft.add_argument("--spec", required=True, type=Path, help="external JSON: version=1, files=[{source, sha256, bytes, destination}]")
    packet = commands.add_parser("packet", help="validate all frozen sources/baselines; read-only unless --apply")
    packet.add_argument("--worktree", required=True, help="canonical absolute Git worktree root")
    packet.add_argument("--manifest", required=True, type=Path)
    packet.add_argument("--apply", action="store_true", help="atomic individual replacements under existing build/test lock")
    packet.add_argument("--maven-terminal", action="store_true", help="caller declares task-owned queued/running Maven requests terminal; lock alone does not prove this")
    junit = commands.add_parser("junit", help="inspect exact flat Surefire report set; does not execute tests")
    junit.add_argument("--worktree", required=True)
    junit.add_argument("--class", dest="classes", action="append", required=True, help="exact class name; repeat for complete expected class set")
    junit.add_argument("--since", required=True, help="run-start ISO-8601 timestamp with timezone, e.g. 2026-10-26T10:00:00Z")
    junit.add_argument("--property", action="append", default=[], metavar="NAME=VALUE",
                       help="exact expected property; repeat. Allowlist: " + ", ".join(sorted(PROPERTIES - {"java.version", "user.dir"})))
    comparison = commands.add_parser("compare-junit", help="read-only exact matched Surefire outcome comparison; never runs tests")
    comparison.add_argument("--baseline-worktree", required=True)
    comparison.add_argument("--candidate-worktree", required=True)
    comparison.add_argument("--baseline-since", required=True, help="actual baseline run-start UTC timestamp (Z or +00:00)")
    comparison.add_argument("--candidate-since", required=True, help="actual candidate run-start UTC timestamp (Z or +00:00)")
    comparison.add_argument("--class", dest="classes", action="append", required=True, help="shared complete exact class set, 1–50 classes; repeat")
    comparison.add_argument("--baseline-property", action="append", default=[], metavar="NAME=VALUE", help="baseline allowlisted media/EGL requirement; repeat")
    comparison.add_argument("--candidate-property", action="append", default=[], metavar="NAME=VALUE", help="candidate allowlisted media/EGL requirement; repeat")
    args = parser.parse_args(argv)
    try:
        if args.command == "compare-junit":
            from integration_junit_compare import compare_junit
            result = compare_junit(baseline_worktree=args.baseline_worktree,
                candidate_worktree=args.candidate_worktree, baseline_since=args.baseline_since,
                candidate_since=args.candidate_since, classes=args.classes,
                baseline_properties=requested_properties(args.baseline_property),
                candidate_properties=requested_properties(args.candidate_property))
        elif args.command == "custody":
            from integration_custody import custody_summary
            links = []
            for value in args.link:
                name, separator, target = value.partition("=")
                require(separator and name and target, "--link requires RELATIVE=EXPECTED_ABSOLUTE_TARGET")
                links.append((name, target))
            result = custody_summary(args.worktree, links=links)
        elif args.command == "handoff":
            from integration_handoff import handoff_summary
            result = handoff_summary(args.worktree, args.spec, installed=args.installed)
            if args.packet_only:
                require(not args.installed and result['ready'],
                        '--packet-only requires a baseline-ready handoff, without --installed')
                print(json.dumps(result['packet'], indent=2, ensure_ascii=True))
                return 0
        elif args.command == "run":
            from integration_run import run_handoff
            result = run_handoff(args.worktree, args.spec, lane=args.lane, execute=args.execute,
                                 compact_output=args.compact_output, maven_progress=args.maven_progress)
        elif args.command == "preserve":
            result = verify_preservation(args.recipe)
        elif args.command == "draft":
            result = draft_manifest(args.worktree, args.spec)
        elif args.command == "packet":
            manifest = read_manifest(args.manifest)
            if args.apply:
                # The existing lock helper writes human-readable notices; keep JSON stdout parseable.
                from contextlib import redirect_stdout
                with redirect_stdout(sys.stderr):
                    result = apply_packet(args.worktree, manifest, maven_terminal=args.maven_terminal)
            else:
                require(not args.maven_terminal, "--maven-terminal is only meaningful with --apply")
                result = plan_report(*validate_packet(args.worktree, manifest))
        else:
            result = junit_summary(args.worktree, args.classes, cutoff(args.since), requested_properties(args.property))
        print(json.dumps(result, indent=2, ensure_ascii=True))
        if args.command == "compare-junit":
            return 0 if result["test_outcomes_equivalent"] else 1
        if args.command in ("custody", "handoff"):
            return 0 if result['ready'] else 1
        if args.command == "run":
            return 0 if not result['executed'] or result['clean'] else 2 if result['status']=='incomplete' else 1
        return 0 if args.command in ("preserve", "draft", "packet") or result["clean"] else 1
    except KeyboardInterrupt:
        print(json.dumps(dict(error="Cancelled; validation remains incomplete")), file=sys.stderr)
        return 130
    except (ValueError, OSError, ET.ParseError) as error:
        if args.command in ("preserve", "compare-junit"):
            # No source dumps or unbounded attacker-controlled JSON keys/path diagnostics.
            print(json.dumps(dict(error=str(error)[:512]), ensure_ascii=True), file=sys.stderr)
            return 2
        report = dict(error=str(error), **(error.report if isinstance(error, PacketError) else {}))
        print(json.dumps(report, indent=2, ensure_ascii=True), file=sys.stderr)
        return 3 if report.get("partial") else 2


if __name__ == "__main__":
    sys.exit(main())
