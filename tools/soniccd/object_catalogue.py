#!/usr/bin/env python3
"""Generate the Sonic CD object catalogues: per-stage file offsets of object tables, one TSV per owner.

Purpose: each object lane (common, ppz, ccz, ttz, qqz, wwz, ssz, mmz) owns one anchor module,
tools/soniccd/object_catalogue_<owner>.py, and one generated resource,
src/main/resources/soniccd/object-catalogue-<owner>.tsv, so parallel lanes never edit the
same file. This module is the shared core: the anchor search, the stage loop, --check and
--media-root. Every stage carries its own copy of the routines, so each table is located
through the instruction that consumes it, exactly as tools/soniccd/stage_catalogue.py does for
the level tables. The output holds offsets only, never disc bytes.

An owner module defines ANCHORS, a list of (column, signature(s), mode, citation):
  mode "required"   one operand in every stage the owner covers;
       "optional"   one operand or none ("-"); more is an error;
       "ambiguous"  like optional, but two or more operands also read "-" (stages linking two
                    copies of a routine whose live copy is not resolved here).
It may define STAGES_COVERED(stage) -> bool (default: every stage; others read "-") and
EXTRA_COLUMNS with extra_columns(stage, data) -> dict for data that is not an operand.

Signature tokens (hex words): "AAAAAAAA" the absolute-long operand (a main-CPU address);
"PPPP" a d16(PC) extension word; "xxQQ" (also "00PP") the extension word of a d8(PC,Xn)
operand with high byte xx; "??" wildcards; anything else literal. PC-relative targets are the
extension word's offset plus the signed displacement.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Origin: lane OBJ0 of the 2026-11-12 Sonic CD re-host; split per owner by lane COMMON2
(scddisasm pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff).

    python3 tools/soniccd/object_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--owner ppz] [--check]
"""
from __future__ import annotations

import argparse
import importlib
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_catalogue import STAGES, WORD_RAM  # noqa: E402
from stage_inventory import RawIso, require  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
# Boss owners (object_catalogue_boss.py for the boss pattern, object_catalogue_boss<zone>.py for one
# zone's boss) are found by file name, so each boss lane adds its module without editing this file.
OWNERS = ("common", "ppz", "ccz", "ttz", "qqz", "wwz", "ssz", "mmz", "shared", "projector") + tuple(sorted(
    p.stem[len("object_catalogue_"):] for p in Path(__file__).resolve().parent.glob("object_catalogue_boss*.py")))


def output_path(owner: str) -> Path:
    return ROOT / f"src/main/resources/soniccd/object-catalogue-{owner}.tsv"


def pattern(signature: str) -> re.Pattern:
    out = b""
    for token in signature.split():
        if token == "AAAAAAAA":
            out += b"(?P<abs>....)"
        elif token == "PPPP":
            out += b"(?P<d16>..)"
        elif len(token) == 4 and (token.endswith("QQ") or token.endswith("PP")):
            out += re.escape(bytes.fromhex(token[:2])) + b"(?P<d8>.)"
        elif "?" in token:
            out += b"." * (len(token) // 2)
        else:
            out += re.escape(bytes.fromhex(token))
    return re.compile(out, re.S)


def operands(data: bytes, signatures) -> list[int]:
    """Distinct file offsets the signature(s) point at, in match order."""
    if isinstance(signatures, str):
        signatures = (signatures,)
    matches = [m for signature in signatures for m in pattern(signature).finditer(data)]
    found = []
    for m in sorted(matches, key=lambda m: m.start()):
        groups = m.groupdict()
        if groups.get("abs") is not None:
            offset = (int.from_bytes(groups["abs"], "big") & 0xFFFFFF) - WORD_RAM
        elif groups.get("d16") is not None:
            offset = m.start("d16") + int.from_bytes(groups["d16"], "big", signed=True)
        else:
            offset = m.start("d8") - 1 + int.from_bytes(groups["d8"], "big", signed=True)
        if offset not in found:
            found.append(offset)
    return found


def load_owner(owner: str):
    require(owner in OWNERS, f"Unknown owner {owner}")
    return importlib.import_module(f"object_catalogue_{owner}")


def columns(module) -> list[str]:
    return ["stage"] + [a[0] for a in module.ANCHORS] + list(getattr(module, "EXTRA_COLUMNS", ()))


def stage_row(module, stage: str, data: bytes) -> dict:
    covered = getattr(module, "STAGES_COVERED", lambda s: True)(stage)
    row = {}
    for name, signature, mode, _ in module.ANCHORS:
        hits = operands(data, signature) if covered else []
        if mode == "required":
            require(len(hits) == 1, f"{stage} {name}: expected one operand, got {[hex(h) for h in hits]}")
        elif mode == "optional":
            require(len(hits) <= 1, f"{stage} {name}: expected at most one operand, got {[hex(h) for h in hits]}")
        elif mode == "ambiguous":
            hits = hits if len(hits) == 1 else []
        else:
            require(False, f"{name}: unknown mode {mode}")
        if hits:
            require(0 <= hits[0] < len(data), f"{stage} {name} at {hits[0]:#x} is outside the stage file")
        row[name] = hits[0] if hits else None
    if hasattr(module, "extra_columns"):
        row.update(module.extra_columns(stage, data) if covered else {c: None for c in module.EXTRA_COLUMNS})
    return row


def generate(owner: str, media_root: Path) -> str:
    module = load_owner(owner)
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    tracks = parse_cue(cues[0].read_text())
    track = (cues[0].parent / tracks[0]["file"]).resolve()
    cols = columns(module)
    lines = [f"# Generated by tools/soniccd/object_catalogue.py (owner {owner}) from the USA disc;"
             " file offsets (hex) only, '-' where a stage has no such routine.",
             "\t".join(cols)]
    require(len(STAGES) == 70, f"Expected 70 stage payloads, got {len(STAGES)}")
    with RawIso(track) as disc:
        for stage in STAGES:
            row = stage_row(module, stage, disc.file(f"{stage}__.MMD;1"))
            lines.append("\t".join([stage] + ["-" if row[c] is None else f"{row[c]:X}" for c in cols[1:]]))
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--owner", choices=OWNERS, action="append", help="owner(s) to generate (default: all)")
    parser.add_argument("--check", action="store_true", help="fail if a committed resource differs")
    args = parser.parse_args()
    for owner in args.owner or OWNERS:
        text = generate(owner, args.media_root.expanduser())
        out = output_path(owner)
        if args.check:
            require(out.exists() and out.read_text() == text, f"{out} is stale; regenerate it")
            print(f"OK: {out.relative_to(ROOT)} matches the disc")
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text)
        print(f"Wrote {out.relative_to(ROOT)} ({len(text.splitlines()) - 2} stages)")


if __name__ == "__main__":
    main()
