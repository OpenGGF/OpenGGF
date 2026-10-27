#!/usr/bin/env python3
"""Read a bounded, explicit frozen-packet handoff and its FINAL dependencies.

Inputs: an external version-1 JSON handoff and a committed destination worktree.
Observes supplied pins/readiness only; never imports, executes source, writes
files, or establishes API/native authenticity. Origin: 2026-10-27 Sonic CD
integration workflow task. CLI wiring belongs to integration_packet.py.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
import re
import stat

import integration_packet as packet

MAX_SPEC = 4 * 1024 * 1024
MAX_FILES = 1024
MAX_DEPENDENCIES = 128
MAX_DESCRIPTIONS = 128
MAX_CLASSES = 50
IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z", re.ASCII)
OBSERVATION = ("Non-atomic file/metadata and Git HEAD observations with rechecks; "
               "ready means declared bytes and dependencies match at this stage, "
               "not API acceptance, producer authority, test execution or native authenticity")


def _plain(value, limit, label, *, nonempty=False):
    packet.require(isinstance(value, str) and len(value) <= limit
                   and not any(c in value for c in ("\0", "\r", "\n"))
                   and (not nonempty or bool(value)), f"{label}: invalid plain string")
    try:
        value.encode("utf-8", errors="strict")
    except UnicodeError as error:
        raise packet.PacketError(f"{label}: invalid Unicode scalar value") from error
    return value


def _identifier(value, label):
    packet.require(isinstance(value, str) and IDENTIFIER.fullmatch(value),
                   f"{label}: invalid bounded identifier")


def _path(value, label):
    _plain(value, 4096, label, nonempty=True)
    path = Path(value)
    packet.require(str(path) == value, f"{label}: lexical path alias")
    return packet.canonical(path)


def _external(value, root, label):
    path = _path(value, label)
    packet.require(not path.is_relative_to(root), f"{label}: must be external to worktree")
    return path


def _pin(value, label):
    packet.require(isinstance(value["sha256"], str) and packet.HASH.fullmatch(value["sha256"]),
                   f"{label}: invalid SHA256")
    packet.require(type(value["bytes"]) is int and 0 <= value["bytes"] <= packet.MAX_FILE,
                   f"{label}: invalid byte count")
    packet.require(value["mode"] in ("0444", "0644"), f"{label}: invalid mode")


def _descriptions(value, label):
    packet.require(isinstance(value, list) and len(value) <= MAX_DESCRIPTIONS,
                   f"{label}: require bounded array")
    for item in value:
        _plain(item, 2048, label, nonempty=True)


def _tests(value):
    packet.keys(value, ("classes", "properties", "expected_tests", "java_major"), "tests")
    classes = value["classes"]
    packet.require(isinstance(classes, list) and 1 <= len(classes) <= MAX_CLASSES,
                   "tests.classes: require 1..50 exact fully qualified class names")
    packet.require(all(isinstance(name, str) and len(name) <= 512 and "." in name
                       and packet.CLASS.fullmatch(name) for name in classes),
                   "tests.classes: require exact fully qualified class names, no methods/patterns")
    packet.require(len(classes) == len(set(classes)), "tests.classes: duplicate class")
    properties = value["properties"]
    packet.require(isinstance(properties, dict), "tests.properties: require object")
    allowed = packet.PROPERTIES - {"java.version", "user.dir"}
    packet.require(set(properties) <= allowed, "tests.properties: property not allowlisted")
    for text in properties.values():
        _plain(text, 4096, "tests.properties")
    expected = value["expected_tests"]
    packet.require(isinstance(expected, dict) and set(expected) == set(classes),
                   "tests.expected_tests: require exact class inventory")
    packet.require(all(type(count) is int and 0 < count <= 1_000_000 for count in expected.values()),
                   "tests.expected_tests: require bounded positive integer counts")
    packet.require(type(value["java_major"]) is int and value["java_major"] == 25,
                   "tests.java_major: this repository requires Java 25")


def _manifest_schema(root, manifest):
    """Reject malformed/unsafe declarations before classifying observed pin drift."""
    packet.keys(manifest, ("version", "worktree_head", "files"), "packet")
    packet.require(type(manifest["version"]) is int and manifest["version"] == 1,
                   "packet: unsupported version")
    packet.require(isinstance(manifest["worktree_head"], str)
                   and packet.COMMIT.fullmatch(manifest["worktree_head"]), "packet: invalid HEAD pin")
    files = manifest["files"]
    packet.require(isinstance(files, list) and 1 <= len(files) <= MAX_FILES,
                   "packet.files: require bounded nonempty array")
    seen, total, pairs = set(), 0, []
    for index, entry in enumerate(files):
        label = f"packet.files[{index}]"
        packet.keys(entry, ("source", "sha256", "bytes", "mode", "utf8", "lf", "destination", "expected"), label)
        _pin(entry, label)
        packet.require(entry["mode"] == "0444" and entry["utf8"] is True and entry["lf"] is True,
                       f"{label}: source requires frozen UTF-8/LF contract")
        source = _external(entry["source"], root, label + ".source")
        _plain(entry["destination"], 4096, label + ".destination", nonempty=True)
        target = packet.destination(root, entry["destination"])
        packet.require(target not in seen, f"{label}: duplicate destination")
        seen.add(target)
        expected = entry["expected"]
        packet.require(isinstance(expected, dict), f"{label}: expected requires object")
        if set(expected) == {"sha256"}:
            packet.require(isinstance(expected["sha256"], str)
                           and packet.HASH.fullmatch(expected["sha256"]), f"{label}: invalid baseline pin")
        else:
            packet.require(set(expected) == {"absent"} and expected["absent"] is True,
                           f"{label}: require baseline SHA256 or absent=true")
        total += entry["bytes"]
        pairs.append((source, target, entry))
    packet.require(total <= packet.MAX_PACKET, "packet declared bytes exceed packet budget")
    return pairs


def handoff_summary(root, spec_path, *, installed=False):
    """Check baseline/import or installed/FINAL readiness; perform no writes.

    Missing/changed declared content blocks readiness. Malformed declarations,
    unsafe paths/aliases, budget exhaustion or changes observed during this call
    raise PacketError. Matching values never grant invocation/API authority.
    """
    packet.require(type(installed) is bool, "installed must be boolean")
    root, initial_head = packet.worktree(root)
    spec_path = _external(str(spec_path), root, "spec")
    packet.require(spec_path.lstat().st_size <= MAX_SPEC, "Handoff exceeds spec byte budget")
    spec = packet.snapshot(spec_path)
    packet.require(len(spec.data) <= MAX_SPEC and b"\r" not in spec.data and b"\0" not in spec.data
                   and spec.data.endswith(b"\n"), "Handoff requires bounded LF-terminated UTF-8 text")
    try:
        value = json.loads(spec.data.decode("utf-8"), object_pairs_hook=packet.unique,
                           parse_constant=lambda _: (_ for _ in ()).throw(packet.PacketError("Nonfinite JSON number")))
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise packet.PacketError("Handoff is not bounded valid UTF-8 JSON") from error
    packet.keys(value, ("version", "id", "packet", "dependencies", "api_changes", "known_defects", "tests"), "handoff")
    packet.require(type(value["version"]) is int and value["version"] == 1, "Unsupported handoff version")
    _identifier(value["id"], "id")
    _descriptions(value["api_changes"], "api_changes")
    _descriptions(value["known_defects"], "known_defects")
    _tests(value["tests"])
    pairs = _manifest_schema(root, value["packet"])
    declared = value["dependencies"]
    packet.require(isinstance(declared, list) and len(declared) <= MAX_DEPENDENCIES,
                   "dependencies: require bounded array")
    names, dependencies = set(), []
    for index, entry in enumerate(declared):
        label = f"dependencies[{index}]"
        packet.keys(entry, ("name", "path", "sha256", "bytes", "mode"), label)
        _identifier(entry["name"], label + ".name")
        packet.require(entry["name"] not in names, "Duplicate dependency name")
        names.add(entry["name"])
        _pin(entry, label)
        path = _external(entry["path"], root, label + ".path")
        dependencies.append((path, entry))

    # Snapshot all named paths, including blocked/absent inputs, so aliases and
    # observed concurrent mutations cannot hide behind an earlier pin mismatch.
    snapshots = {spec_path: spec}
    roles = {}
    parents = {}
    identities = {}
    observed_bytes = 0

    def observe(path, role, pin=None):
        nonlocal observed_bytes
        if path in roles:
            previous_role, previous_pin = roles[path]
            packet.require(role == previous_role == "input" and pin == previous_pin,
                           "Conflicting input path role or pins")
        else:
            roles[path] = (role, pin)
        if path not in snapshots:
            try:
                declared_size = path.lstat().st_size
            except FileNotFoundError:
                declared_size = 0
            packet.require(observed_bytes + declared_size <= 3 * packet.MAX_PACKET,
                           "Observed handoff bytes exceed aggregate budget")
            snapshots[path] = packet.snapshot(path, absent=True)
            observed_bytes += len(snapshots[path].data or b"")
            packet.require(observed_bytes <= 3 * packet.MAX_PACKET,
                           "Observed handoff bytes exceed aggregate budget")
        saved = snapshots[path]
        if saved.info is not None:
            identity = (saved.info.st_dev, saved.info.st_ino)
            packet.require(identity not in identities or identities[identity] == path,
                           "Distinct named paths are hardlink aliases")
            identities[identity] = path
        parent = path.parent
        while True:
            try:
                info = parent.lstat()
                break
            except FileNotFoundError:
                parent = parent.parent
        packet.require(stat.S_ISDIR(info.st_mode), "Input parent must be a directory")
        parents[parent] = (info.st_dev, info.st_ino, info.st_mode)
        return saved

    observe(spec_path, "spec")
    for source, target, entry in pairs:
        observe(source, "input", {key: entry[key] for key in ("sha256", "bytes", "mode")})
        observe(target, "destination")
    for path, entry in dependencies:
        observe(path, "input", {key: entry[key] for key in ("sha256", "bytes", "mode")})
    packet.require(sum(len(saved.data) for path, saved in snapshots.items()
                       if path != spec_path and saved.data is not None) <= 3 * packet.MAX_PACKET,
                   "Observed handoff bytes exceed aggregate budget")

    reasons, observed_dependencies = [], []
    for path, entry in dependencies:
        observed = snapshots[path].public()
        expected = {key: entry[key] for key in ("sha256", "bytes", "mode")}
        matches = all(observed.get(key) == expected[key] for key in expected)
        status = "matched" if matches else "missing" if snapshots[path].info is None else "changed"
        observed_dependencies.append(dict(name=entry["name"], path=str(path), status=status,
                                          expected=expected, observed=observed))
        if not matches:
            reasons.append(f"Dependency {entry['name']}: {status}")
    effective = copy.deepcopy(value["packet"])
    if installed:
        for entry in effective["files"]:
            entry["expected"] = {"sha256": entry["sha256"]}
    try:
        packet.validate_packet(root, effective)
    except packet.PacketError as error:
        reasons.append(str(error)[:512])
        for issue in error.report.get("issues", [])[:MAX_FILES]:
            reasons.append(str(issue.get("error", "Packet pin mismatch"))[:512])
    # The snapshots above are observations, never a filesystem transaction.
    for saved in snapshots.values():
        packet.unchanged(saved, message="Handoff input changed during observation")
    for path, identity in parents.items():
        packet.canonical(path)
        info = path.lstat()
        packet.require((info.st_dev, info.st_ino, info.st_mode) == identity,
                       "Handoff parent changed during observation")
    _, final_head = packet.worktree(root)
    packet.require(initial_head == final_head, "Worktree HEAD changed during handoff observation")
    return dict(version=1, id=value["id"], ready=not reasons,
                stage="installed" if installed else "baseline", packet=value["packet"],
                dependencies=observed_dependencies, tests=value["tests"],
                api_changes=value["api_changes"], known_defects=value["known_defects"],
                reasons=reasons, observation=OBSERVATION)
