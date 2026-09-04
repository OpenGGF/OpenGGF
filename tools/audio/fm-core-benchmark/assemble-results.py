#!/usr/bin/env python3
"""Validate benchmark controls and write one machine-readable evidence record."""

import argparse
import hashlib
import json
import os
import platform
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"assemble-results: {message}")


def read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid JSON in {path}: {error}")
    if not isinstance(value, dict):
        fail(f"expected object in {path}")
    return value


def command(*parts: str) -> str:
    try:
        process = subprocess.run(parts, check=True, text=True, capture_output=True)
        return (process.stdout or process.stderr).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unavailable"


parser = argparse.ArgumentParser()
parser.add_argument("--java", required=True, type=Path)
parser.add_argument("--native", required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--repo", required=True, type=Path)
parser.add_argument("--frames", required=True, type=int)
parser.add_argument("--warmups", required=True, type=int)
parser.add_argument("--iterations", required=True, type=int)
parser.add_argument("--affinity", default="none")
parser.add_argument("--nuked-lock", required=True, type=Path)
parser.add_argument("--ymfm-lock", required=True, type=Path)
parser.add_argument("--build-input", action="append", default=[])
parser.add_argument("--native-c-flags", required=True)
parser.add_argument("--native-cxx-flags", required=True)
args = parser.parse_args()

java = read_json(args.java)
native = read_json(args.native)
try:
    implementations = native["implementations"]
    c_nuked = implementations["c-nuked"]
    ymfm = implementations["cpp-ymfm"]
    records = [java, c_nuked, ymfm]
    checksum_match = java["checksum"] == c_nuked["checksum"]
    snapshots_pass = all(item["snapshot_errors"] == 0 for item in records)
    controls_pass = all(item["negative_control_changes"] > 0 for item in records)
except (KeyError, TypeError) as error:
    fail(f"missing result field: {error}")
if not checksum_match:
    fail("Java and C Nuked checksums differ")
if not snapshots_pass:
    fail("snapshot replay failed")
if not controls_pass:
    fail("negative control was inert")
if args.frames <= 0 or args.warmups < 0 or args.iterations <= 0:
    fail("invalid measurement dimensions")

repo = args.repo.resolve()
output = args.output.resolve()
target = repo / "target"
if target not in output.parents:
    fail("output must be below the invoking worktree's target directory")
output.parent.mkdir(parents=True, exist_ok=True)

head = command("git", "-C", str(repo), "rev-parse", "HEAD")
status = command("git", "-C", str(repo), "status", "--porcelain")
build_inputs = {}
for item in args.build_input:
    if "=" not in item:
        fail(f"malformed build input: {item}")
    label, raw_path = item.split("=", 1)
    path = Path(raw_path)
    if not label or label in build_inputs or not path.is_file() or path.is_symlink():
        fail(f"invalid build input: {item}")
    build_inputs[label] = hashlib.sha256(path.read_bytes()).hexdigest()
if not build_inputs:
    fail("at least one build input is required")
record = {
    "schema": "openggf.fm-core-benchmark.v1",
    "created_utc": datetime.now(timezone.utc).isoformat(),
    "repository": {"head": head, "tracked_tree_clean": status == ""},
    "environment": {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "processor": platform.processor() or "unreported",
        "java": command("java", "-version"),
        "javac": command("javac", "-version"),
        "c_compiler": command(os.environ.get("CC", "cc"), "--version").splitlines()[0],
        "cxx_compiler": command(os.environ.get("CXX", "c++"), "--version").splitlines()[0],
        "python": platform.python_version(),
        "affinity": args.affinity,
    },
    "measurement": {
        "frames_per_iteration": args.frames,
        "warmups": args.warmups,
        "iterations": args.iterations,
        "publishable": False,
        "note": "Local diagnostic only; host reservation and publication review are external requirements.",
    },
    "build": {
        "inputs": build_inputs,
        "java_flags": "javac defaults for JDK 21",
        "native_c_flags": args.native_c_flags,
        "native_cxx_flags": args.native_cxx_flags,
    },
    "source_pins": {
        "nuked": {
            "commit": "335747d78cb0abbc3b55b004e62dad9763140115",
            "tree": "6637a500d1da3b08cbc0cec1532ab305197b8978",
            "license": "LGPL-2.1-or-later",
            "lock_sha256": hashlib.sha256(args.nuked_lock.read_bytes()).hexdigest(),
        },
        "ymfm": {
            "commit": "81aec25ccbb98f4873a255f7551ac4dadac59b4a",
            "tree": "03f76ed27b1281357c91005e99d043eebd5119c1",
            "license": "BSD-3-Clause",
            "lock_sha256": hashlib.sha256(args.ymfm_lock.read_bytes()).hexdigest(),
        },
    },
    "validation": {
        "java_c_nuked_checksum_match": True,
        "snapshot_replay": "pass",
        "active_negative_controls": "pass",
    },
    "java": java,
    "native": native,
}
output.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(output)
