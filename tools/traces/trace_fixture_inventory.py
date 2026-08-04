#!/usr/bin/env python3
"""Build and verify deterministic inventories of committed trace fixtures."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable


INVENTORY_FORMAT = "openggf-trace-fixture-inventory-v1"


class InventoryVerificationError(ValueError):
    """Explains every path-level difference from a frozen inventory."""

    def __init__(self, differences: list[str]) -> None:
        super().__init__("; ".join(differences))
        self.differences = differences


def build_inventory(root: Path) -> dict[str, Any]:
    """Hash every regular file below *root* without modifying it."""

    if not root.is_dir():
        raise ValueError(f"fixture root is not a directory: {root}")
    files = [
        inventory_entry(path.relative_to(root).as_posix(), path.read_bytes())
        for path in sorted(root.rglob("*"))
        if path.is_file()
    ]
    return inventory_document(files)


def build_index_inventory(repository_root: Path, fixture_root: Path) -> dict[str, Any]:
    """Hash the Git index version of every file below *fixture_root*.

    This gives Task 8 the same added/removed/changed comparison for staged
    fixture edits that it uses for the worktree.  It reads index blobs only.
    """

    repository_root = repository_root.resolve()
    relative_root = fixture_root.resolve().relative_to(repository_root).as_posix()
    result = subprocess.run(
        ["git", "-C", str(repository_root), "ls-files", "-z", "--", relative_root],
        check=True,
        capture_output=True,
    )
    index_paths = sorted(
        Path(path.decode("utf-8"))
        for path in result.stdout.split(b"\0")
        if path
    )
    files = []
    prefix = Path(relative_root)
    for index_path in index_paths:
        content = subprocess.run(
            ["git", "-C", str(repository_root), "show", f":{index_path.as_posix()}"],
            check=True,
            capture_output=True,
        ).stdout
        files.append(inventory_entry(index_path.relative_to(prefix).as_posix(), content))
    return inventory_document(files)


def verify_inventory(root: Path, expected: dict[str, Any]) -> None:
    """Raise with sorted added/removed/changed differences from *expected*."""

    validate_inventory_document(expected)
    actual = build_inventory(root)
    differences = compare_inventory_documents(expected, actual)
    if differences:
        raise InventoryVerificationError(differences)


def verify_index_inventory(repository_root: Path, fixture_root: Path, expected: dict[str, Any]) -> None:
    """Apply the frozen inventory comparison to the staged Git index."""

    validate_inventory_document(expected)
    differences = compare_inventory_documents(expected, build_index_inventory(repository_root, fixture_root))
    if differences:
        raise InventoryVerificationError(differences)


def inventory_entry(relative_path: str, content: bytes) -> dict[str, str]:
    """Create one canonical record from a relative path and exact stored bytes."""

    entry = {
        "path": relative_path,
        "kind": file_kind(relative_path),
        "stored_sha256": sha256(content),
    }
    if relative_path.endswith(".gz"):
        try:
            logical = gzip.decompress(content)
        except OSError as error:
            raise ValueError(f"cannot decompress gzip fixture {relative_path}: {error}") from error
        entry["logical_sha256"] = sha256(logical)
    return entry


def inventory_document(files: Iterable[dict[str, str]]) -> dict[str, Any]:
    """Return a canonical document whose aggregate is independent of JSON layout."""

    ordered_files = sorted(files, key=lambda entry: entry["path"])
    return {
        "format": INVENTORY_FORMAT,
        "files": ordered_files,
        "aggregate_sha256": aggregate_sha256(ordered_files),
    }


def compare_inventory_documents(expected: dict[str, Any], actual: dict[str, Any]) -> list[str]:
    """Compare canonical documents without treating an aggregate as a path delta."""

    expected_files = {entry["path"]: entry for entry in expected["files"]}
    actual_files = {entry["path"]: entry for entry in actual["files"]}
    differences: list[str] = []
    for path in sorted(actual_files.keys() - expected_files.keys()):
        differences.append(f"added {path}")
    for path in sorted(expected_files.keys() - actual_files.keys()):
        differences.append(f"removed {path}")
    for path in sorted(expected_files.keys() & actual_files.keys()):
        for field in ("kind", "stored_sha256", "logical_sha256"):
            if expected_files[path].get(field) != actual_files[path].get(field):
                differences.append(f"changed {path} {field}")
    return sorted(differences)


def validate_inventory_document(inventory: dict[str, Any]) -> None:
    """Reject malformed or aggregate-tampered frozen inventory documents."""

    if not isinstance(inventory, dict) or inventory.get("format") != INVENTORY_FORMAT:
        raise InventoryVerificationError(["inventory format is not supported"])
    files = inventory.get("files")
    if not isinstance(files, list) or any(not isinstance(entry, dict) for entry in files):
        raise InventoryVerificationError(["inventory files must be an array of records"])
    paths = [entry.get("path") for entry in files]
    if any(not isinstance(path, str) for path in paths):
        raise InventoryVerificationError(["inventory paths must be unique and sorted"])
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        raise InventoryVerificationError(["inventory paths must be unique and sorted"])
    if inventory.get("aggregate_sha256") != aggregate_sha256(files):
        raise InventoryVerificationError(["inventory aggregate_sha256 does not match its files"])


def aggregate_sha256(files: Iterable[dict[str, Any]]) -> str:
    """Hash canonical JSON records, one LF-delimited record per sorted path."""

    digest = hashlib.sha256()
    for entry in sorted(files, key=lambda item: item["path"]):
        digest.update(json.dumps(entry, sort_keys=True, separators=(",", ":")).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def file_kind(relative_path: str) -> str:
    """Classify fixture names without attaching route-specific meaning to them."""

    name = Path(relative_path).name.removesuffix(".gz")
    kinds = {
        "metadata.json": "metadata",
        "physics.csv": "physics",
        "aux_state.jsonl": "auxiliary-state",
        "hardware_timing.jsonl": "hardware-timing",
        "run_manifest.json": "run-manifest",
    }
    return kinds.get(name, "other")


def sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def load_inventory(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read inventory {path}: {error}") from error


def git_worktree_root(path: Path) -> Path:
    """Resolve the enclosing Git worktree for an absolute or relative fixture root."""

    return Path(subprocess.run(
        ["git", "-C", str(path), "rev-parse", "--show-toplevel"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()).resolve()


def encoded_inventory(inventory: dict[str, Any]) -> str:
    validate_inventory_document(inventory)
    return json.dumps(inventory, indent=2, sort_keys=True) + "\n"


def write_inventory(inventory: dict[str, Any], fixture_root: Path, output: Path) -> None:
    """Write a frozen artifact while refusing to create any file below its root."""

    root = fixture_root.resolve()
    destination = output.resolve()
    if destination.is_relative_to(root):
        raise ValueError(f"inventory artifact must not be written under fixture root: {output}")
    output.write_text(encoded_inventory(inventory), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    generate = commands.add_parser("generate", help="print a deterministic inventory")
    generate.add_argument("root", type=Path)
    generate.add_argument("--output", type=Path, help="write the artifact outside the fixture root")
    verify = commands.add_parser("verify", help="compare a root with a frozen inventory")
    verify.add_argument("root", type=Path)
    verify.add_argument("inventory", type=Path)
    verify.add_argument("--git-index", action="store_true", help="verify staged index bytes instead of worktree bytes")
    args = parser.parse_args(argv)
    try:
        if args.command == "generate":
            inventory = build_inventory(args.root)
            if args.output is not None:
                write_inventory(inventory, args.root, args.output)
                print(f"trace fixture inventory written: {args.output}")
            else:
                print(encoded_inventory(inventory), end="")
            return 0
        expected = load_inventory(args.inventory)
        if args.git_index:
            verify_index_inventory(git_worktree_root(args.root), args.root, expected)
        else:
            verify_inventory(args.root, expected)
    except (InventoryVerificationError, ValueError, subprocess.CalledProcessError) as error:
        differences = error.differences if isinstance(error, InventoryVerificationError) else [str(error)]
        print("trace fixture inventory verification failed:", file=sys.stderr)
        print(*differences, sep="\n", file=sys.stderr)
        return 1
    print(f"trace fixture inventory verification passed: {args.root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
