#!/usr/bin/env python3
"""Commit an exact prepared message and explicitly named staged scope (#230)."""

import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def git(*args: str, input: bytes | None = None) -> bytes:
    return subprocess.run(["git", *args], input=input, stdout=subprocess.PIPE,
                          check=True).stdout


def checked_commit(message: bytes, paths: list[str], coauthors: list[str]) -> None:
    root = Path(os.fsdecode(git("rev-parse", "--show-toplevel")).rstrip("\n"))
    os.chdir(root)
    branch = git("symbolic-ref", "--quiet", "HEAD")
    if branch.strip() == b"refs/heads/master":
        raise ValueError("use this entry point for ordinary non-master commits")
    merge_head = Path(os.fsdecode(git("rev-parse", "--git-path", "MERGE_HEAD")).rstrip("\n"))
    if merge_head.exists():
        raise ValueError("merge commits are outside this entry point's scope")
    head = git("rev-parse", "--verify", "HEAD")
    tree = git("write-tree")
    staged = {os.fsdecode(path) for path in git(
        "diff", "--cached", "--no-renames", "--name-only", "-z").split(b"\0") if path}
    if not paths or len(paths) != len(set(paths)) or set(paths) != staged:
        raise ValueError(f"expected literal staged paths {paths!r}, found {sorted(staged)!r}")

    lines = message.decode("utf-8").rstrip("\n").split("\n")
    expected = [f"Co-authored-by: {author}" for author in coauthors]
    if (not coauthors or len(expected) != len(set(expected))
            or any("\n" in author or "\r" in author for author in coauthors)
            or lines[-len(expected):] != expected
            or len(lines) <= len(expected) or lines[-len(expected)-1] != ""):
        raise ValueError("message must end with the exact contiguous co-author block after a blank line")
    parsed = git("interpret-trailers", "--parse", input=message).decode("utf-8").splitlines()
    if parsed != expected:
        raise ValueError("parsed final trailers must match every intended co-author exactly")

    with tempfile.TemporaryDirectory(prefix="openggf-commit-") as directory:
        prepared = Path(directory) / "message"
        prepared.write_bytes(message)
        # The existing dispatcher owns all seven policy lines and staged-content rules.
        subprocess.run(["sh", str(root / ".githooks/run-policy"), "commit-msg",
                        str(prepared)], check=True)
        if prepared.read_bytes() != message:
            raise ValueError("validator changed the prepared message")
        if (git("symbolic-ref", "--quiet", "HEAD") != branch
                or git("rev-parse", "--verify", "HEAD") != head
                or git("write-tree") != tree):
            raise ValueError("branch, HEAD, or staged content changed during preparation")
        subprocess.run(["git", "commit", "--cleanup=verbatim", "--file", str(prepared)], check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--message-file", type=Path, required=True)
    parser.add_argument("--co-author", action="append", required=True,
                        help="exact Name <email> value, repeat in final-block order")
    parser.add_argument("paths", nargs="+", help="literal repository-root-relative staged paths")
    args = parser.parse_args()
    try:
        checked_commit(args.message_file.read_bytes(), args.paths, args.co_author)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"checked-commit: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
