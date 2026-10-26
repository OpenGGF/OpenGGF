#!/usr/bin/env python3
"""Draft/validate/import frozen packets and inspect fresh, explicitly scoped JUnit XML.

Inputs: externally pinned draft spec or version-1 JSON manifest, explicit Git
worktree, or exact Surefire class set and UTC run-start cutoff. No game code,
media, probes, tests or Git mutations run.
Origin: 2026-10-26 private Sonic CD integration tooling task. Schema and workflow:
.agents/skills/soniccd-packet-integration/SKILL.md. Output is stdout/stderr only.
"""
from __future__ import annotations

import argparse
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
                        "soniccd.media.root", "sonic1.rom.path", "sonic2.rom.path", "s3k.rom.path"})
# Source packets are bounded UTF-8 text; refusing binary and oversized inputs is intentional.
MAX_FILE = 16 * 1024 * 1024
MAX_PACKET = 64 * 1024 * 1024


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


def junit_summary(root, classes, since, properties):
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
        saved = snapshot(path)
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
    args = parser.parse_args(argv)
    try:
        if args.command == "draft":
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
        return 0 if args.command in ("draft", "packet") or result["clean"] else 1
    except (ValueError, OSError, ET.ParseError) as error:
        report = dict(error=str(error), **(error.report if isinstance(error, PacketError) else {}))
        print(json.dumps(report, indent=2, ensure_ascii=True), file=sys.stderr)
        return 3 if report.get("partial") else 2


if __name__ == "__main__":
    sys.exit(main())
