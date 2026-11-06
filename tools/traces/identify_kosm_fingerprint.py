#!/usr/bin/env python3
"""Comparison-only lookup of bounded, single-module KOS_MODULE_QUEUE tuples.

Inputs: original locked-on S3K ROM, pinned .kosm reference files, opaque hashes.
Origin: C4 trace hard-abort investigation, 2026-11-04 (missing DEZ entry batch).
Reference bytes must match the ROM uniquely. A match identifies a descriptor;
it never proves production submission/execution or supplies runtime readiness.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

S3K_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6"


def word(value: int) -> bytes:
    return struct.pack(">i", value)


def text(value: str) -> bytes:
    data = value.encode("utf-8")
    return word(len(data)) + data


def normalize_fingerprint(value: str) -> str:
    value = value.removeprefix("sha256:").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Expected a SHA-256 hex fingerprint, optionally prefixed sha256:")
    return value


def module_fingerprint(source: int, length: int, destination: int, output: int) -> str:
    fields = (text("KOS_MODULE_QUEUE") + word(source) + word(length)
              + word(destination) + word(output) + text("kosinski_moduled") + word(1))
    return "sha256:" + hashlib.sha256(fields).hexdigest()


def lookup(rom: bytes, assets: list[Path], fingerprints: list[str], padding_bytes: int = 16) -> dict:
    if not 0 <= padding_bytes <= 64:
        raise ValueError("Padding search must be bounded to 0..64 bytes")
    targets = {normalize_fingerprint(value) for value in fingerprints}
    if not targets:
        raise ValueError("Supply at least one fingerprint")
    found, matched_assets, tested = [], 0, 0
    for path in sorted(assets):
        data = path.read_bytes()
        if len(data) < 3:
            continue
        output = int.from_bytes(data[:2], "big")
        if not 0 < output <= 4096:
            continue
        source = rom.find(data)
        if source < 0 or rom.find(data, source + 1) >= 0:
            continue
        matched_assets += 1
        tail = word(output) + text("kosinski_moduled") + word(1)
        for length in range(max(3, len(data) - padding_bytes), len(data) + 1):
            prefix = text("KOS_MODULE_QUEUE") + word(source) + word(length)
            for destination in range(0, 0x10000, 32):
                tested += 1
                fingerprint = hashlib.sha256(prefix + word(destination) + tail).hexdigest()
                if fingerprint in targets:
                    found.append(dict(asset=str(path), sourceAddress=source,
                                      compressedLength=length, destinationAddress=destination,
                                      destinationLength=output, moduleCount=1,
                                      fingerprint="sha256:" + fingerprint))
        if {match["fingerprint"][7:] for match in found} == targets:
            break
    resolved = {match["fingerprint"][7:] for match in found}
    return dict(analysis_only=True, matches=found,
                unmatched=["sha256:" + value for value in sorted(targets - resolved)],
                assets_matched_uniquely_in_rom=matched_assets, canonical_candidates=tested,
                bounds=dict(module_count=1, maximum_output_bytes=4096,
                            padding_search_bytes=padding_bytes, tile_destinations=2048),
                limits=["Reference bytes must match the original ROM uniquely; no asset fallback.",
                        "Candidate compressed ends require independent decoder/descriptor corroboration.",
                        "Hash identity does not establish production submission, execution or timing.",
                        "No runtime, gameplay, readiness, queue or recorder mutation."])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--fingerprint", action="append", required=True)
    parser.add_argument("--padding-bytes", type=int, default=16)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rom = args.rom.read_bytes()
    if hashlib.sha1(rom).hexdigest() != S3K_SHA1:
        parser.error("ROM is not the original locked-on S3K image declared in AGENTS.md")
    assets = list(args.assets.rglob("*.kosm"))
    if not assets:
        parser.error("No .kosm reference assets found")
    try:
        result = lookup(rom, assets, args.fingerprint, args.padding_bytes)
    except ValueError as error:
        parser.error(str(error))
    result["rom_sha1"] = S3K_SHA1
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered)
    print(rendered, end="")
    return 1 if result["unmatched"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
