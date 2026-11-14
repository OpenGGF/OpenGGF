#!/usr/bin/env python3
"""Generate the Palmtree Panic palette-cycle table offsets for acts 1 to 3 (R11A-D, R12A-D, R13C-D).

Purpose: PpzPaletteCycle reads each period's cycle scripts and colours from the stage file instead
of carrying copies of disc bytes. Each stage file holds its own copy of the cycle routine, so every
table is located by the instruction that reads it (same method as stage_catalogue.py). The
instruction sequences are taken from scddisasm (pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff):
  Present, Past: PalCycle_Do, six CycleColor calls
      (Level/Palmtree Panic/Palette Cycle (Present).asm:86-111, ...(Past).asm:86-111)
  Good Future: PaletteCycle with two data sets (...(Good Future).asm:11-39, adda.w at :36)
  Bad Future: the same two sets, then a third script (...(Bad Future).asm:11-65)
The output holds offsets only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/ppz-palette-cycles.tsv (or --check against it).
Origin: Palmtree Panic presentation lanes of the 2026-11-12 Sonic CD re-host (act 1, then acts 2-3).
Acts 2 and 3 include the same four Palette Cycle files as act 1 (Level/Palmtree Panic/Act 2 Present.asm:12,
Act 2 Past:15, Act 2 Good Future:15, Act 2 Bad Future:15, Act 3 Good Future:15, Act 3 Bad Future:15), so each
stage file holds the same instruction sequences and only the period selects the routine.

    python3 tools/soniccd/ppz_cycle_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_inventory import RawIso, require  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/ppz-palette-cycles.tsv"
WORD_RAM = 0x200000
# Period routine per stage: Present and Past use PalCycle_Do, Good Future the prototype routine, Bad
# Future the prototype routine plus a third script (act 2: Palette Cycle (X).asm included at lines 12-15 of
# each stage's top-level file; act 3: Act 3 Good Future.asm:15, Act 3 Bad Future.asm:15).
STAGES = {"R11A": "script", "R11B": "script", "R11C": "proto", "R11D": "bad",
          "R12A": "script", "R12B": "script", "R12C": "proto", "R12D": "bad",
          "R13C": "proto", "R13D": "bad"}

# lea palCycleTimers.w,a5 ; lea palCycleSteps.w,a4
TIMER_STEP_LEAS = b"\x4b\xf8..\x49\xf8.."
# lea Script,a1 ; lea Colors,a2
SCRIPT_COLORS = b"\x43\xf9(....)\x45\xf9(....)"


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def scripts(stage: str, data: bytes) -> list[tuple[int, int, int]]:
    """Six CycleColor calls (Present, Past): bsr.s CycleColor is 61xx; the sixth falls through."""
    pattern = re.compile(TIMER_STEP_LEAS + (SCRIPT_COLORS + b"\x61.") * 5 + SCRIPT_COLORS, re.S)
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one PalCycle_Do, got {len(matches)}")
    groups = matches[0].groups()
    return [(slot, offset(groups[2 * slot], data), offset(groups[2 * slot + 1], data)) for slot in range(6)]


def prototype(stage: str, data: bytes) -> tuple[int, int]:
    """lea PalCycleData1,a0 ; subq.b #1,timer ; bpl.s ; move.b #7,timer ... adda.w #D2-D1,a0."""
    first = re.compile(b"\x41\xf9(....)\x53\x38..\x6a.\x11\xfc\x00\x07..\x70\x00\x10\x38", re.S)
    matches = list(first.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one PaletteCycle prototype, got {len(matches)}")
    data1 = offset(matches[0].group(1), data)
    delta = re.compile(b"\xd0\xfc(..)\x53\x38..\x6a.\x11\xfc\x00\x05", re.S)
    deltas = list(delta.finditer(data, matches[0].end()))
    require(len(deltas) >= 1, f"{stage}: no second data set")
    return data1, data1 + int.from_bytes(deltas[0].group(1), "big")


def third_script(stage: str, data: bytes) -> tuple[int, int]:
    """Bad Future: lea palCycleTimers+2,a5 ; lea palCycleSteps+2,a4 ; lea Script,a1 ; lea Colors,a2."""
    pattern = re.compile(TIMER_STEP_LEAS + SCRIPT_COLORS, re.S)
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one third-cycle script, got {len(matches)}")
    return offset(matches[0].group(1), data), offset(matches[0].group(2), data)


def rows(stage: str, data: bytes) -> list[tuple]:
    out = []
    if STAGES[stage] == "script":
        out += [(stage, "script", slot, s, c) for slot, s, c in scripts(stage, data)]
    else:
        d1, d2 = prototype(stage, data)
        out.append((stage, "proto", 0, d1, d2))
        if STAGES[stage] == "bad":
            s, c = third_script(stage, data)
            out.append((stage, "script", 2, s, c))
    return out


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/ppz_cycle_catalogue.py from the USA disc; file offsets (hex) only.",
             "stage\tkind\tslot\ta\tb"]
    with RawIso(track) as disc:
        for stage in STAGES:
            for stage_, kind, slot, a, b in rows(stage, disc.file(f"{stage}__.MMD;1")):
                lines.append(f"{stage_}\t{kind}\t{slot}\t{a:X}\t{b:X}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--check", action="store_true", help="fail if the committed resource differs")
    args = parser.parse_args()
    text = generate(args.media_root.expanduser())
    if args.check:
        require(OUTPUT.read_text() == text, f"{OUTPUT} is stale; regenerate it")
        print(f"OK: {OUTPUT.relative_to(ROOT)} matches the disc")
        return
    OUTPUT.write_text(text)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({len(text.splitlines()) - 2} rows)")


if __name__ == "__main__":
    main()
