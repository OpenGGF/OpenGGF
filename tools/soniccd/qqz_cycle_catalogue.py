#!/usr/bin/env python3
"""Generate the Quartz Quadrant palette-cycle table offsets for R51A-R53D.

Purpose: QqzPaletteCycle reads each stage's cycle tables from the stage file instead of carrying
copies of disc bytes. Every stage file holds its own copy of CyclePalette, so each table is found by
the instruction that reads it (same method as ppz_cycle_catalogue.py). Four routine bodies exist
(scddisasm pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/r5/):
  a (R51A, R52A) palette_cycle_a.asm:3-24  slot 0 table + reverse table; slot 1 table chosen by r5_bg_change
  b (R51B, R52B) palette_cycle_b.asm:3-23  slot 0 table + reverse table; slot 1 table only while r5_bg_change is even
  c (R51C, R52C, R53C) palette_cycle_c.asm:3-21  boss_flags guard; slot 0 table + reverse table
  d (R51D, R52D, R53D) palette_cycle_d.asm:3-38  slot 0 (skipped under boss_flags) + slots 1 and 2 while r5_bg_change is odd
Each table is {colour index, frame count, colours per frame, reload} then count*width colour words
(sub_2001AA, palette_cycle_a.asm:27-53). The output holds offsets only, never disc bytes.

Output rows: stage, slot (palette_cycle_timers/steps index), gate (always | noboss | bg_even | bg_odd),
table offset, reverse-table offset ('-' when the slot has none).

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/qqz-palette-cycles.tsv (or --check against it).
Origin: Quartz Quadrant presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 tools/soniccd/qqz_cycle_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/qqz-palette-cycles.tsv"
WORD_RAM = 0x200000
VARIANT = {"R51A": "a", "R52A": "a", "R51B": "b", "R52B": "b",
           "R51C": "c", "R52C": "c", "R53C": "c", "R51D": "d", "R52D": "d", "R53D": "d"}

# lea palette_cycle_timers.w,a4 ; lea palette_cycle_steps.w,a5
TIMER_STEP = rb"\x49\xf8..\x4b\xf8.."
# btst #0,r5_bg_change (abs.l)
BTST_BG = rb"\x08\x39\x00\x00...."
# The shared head of every variant: lea Table(pc),a1 ; tst.b r5_conveyor_reverse ; beq.s ; lea Reverse(pc),a1
PC_TABLE = rb"\x43\xfa(..)\x4a\x39....\x67\x04\x43\xfa(..)"
ABS_LEA = rb"\x43\xf9(....)"

PATTERNS = {
    # TIMER_STEP bsr.s ; btst ; beq.s ; lea S1,a1 ; bra.w ; lea S0,a1 ; bra.s ; PC_TABLE
    "a": rb"" + TIMER_STEP + rb"\x61." + BTST_BG + rb"\x67." + ABS_LEA + rb"\x60\x00.." + ABS_LEA + rb"\x60." + PC_TABLE,
    # TIMER_STEP bsr.s ; btst ; beq.s ; rts ; lea S0,a1 ; bra.s ; PC_TABLE
    "b": rb"" + TIMER_STEP + rb"\x61." + BTST_BG + rb"\x67.\x4e\x75" + ABS_LEA + rb"\x60." + PC_TABLE,
    # tst.b boss_flags ; beq.s ; rts ; TIMER_STEP ; bra.w +2 ; PC_TABLE
    "c": rb"\x4a\x38..\x67.\x4e\x75" + TIMER_STEP + rb"\x60\x00\x00\x02" + PC_TABLE,
    # TIMER_STEP ; tst.b boss_flags ; beq.s ; adda a4 ; adda a5 ; bra.s ; bsr.s ; btst ; bne.s ; rts ;
    # lea S1,a1 ; bsr.s ; lea S2,a1 ; bra.s ; PC_TABLE
    "d": rb"" + TIMER_STEP + rb"\x4a\x38..\x67.\xd8\xfc\x00\x01\xda\xfc\x00\x01\x60.\x61." + BTST_BG
         + rb"\x66.\x4e\x75" + ABS_LEA + rb"\x61." + ABS_LEA + rb"\x60." + PC_TABLE,
}


def absolute(raw: bytes, data: bytes) -> int:
    value = (int.from_bytes(raw, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def relative(raw: bytes, match: re.Match, group: int, data: bytes) -> int:
    """PC-relative lea: target = address of the displacement word + signed displacement."""
    disp = int.from_bytes(raw, "big", signed=True)
    value = match.start(group) + disp
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def check_table(stage: str, data: bytes, offset: int) -> None:
    index, count, width, reload = data[offset:offset + 4]
    require(index < 64 and 0 < count < 40 and 0 < width <= 8 and offset + 4 + 2 * count * width <= len(data),
            f"{stage}: {offset:#x} is not a cycle table header")


def rows(stage: str, data: bytes) -> list[tuple]:
    variant = VARIANT[stage]
    matches = list(re.compile(PATTERNS[variant], re.S).finditer(data))
    require(len(matches) == 1, f"{stage}: expected one CyclePalette (variant {variant}), got {len(matches)}")
    m = matches[0]
    g = m.groups()
    out: list[tuple] = []
    if variant == "a":
        s1, s0 = absolute(g[0], data), absolute(g[1], data)
        t0, rev = relative(g[2], m, 3, data), relative(g[3], m, 4, data)
        out = [(0, "always", t0, rev), (1, "bg_even", s0, None), (1, "bg_odd", s1, None)]
    elif variant == "b":
        s0 = absolute(g[0], data)
        t0, rev = relative(g[1], m, 2, data), relative(g[2], m, 3, data)
        out = [(0, "always", t0, rev), (1, "bg_even", s0, None)]
    elif variant == "c":
        t0, rev = relative(g[0], m, 1, data), relative(g[1], m, 2, data)
        out = [(0, "noboss", t0, rev)]
    else:
        s1, s2 = absolute(g[0], data), absolute(g[1], data)
        t0, rev = relative(g[2], m, 3, data), relative(g[3], m, 4, data)
        out = [(0, "noboss", t0, rev), (1, "bg_odd", s1, None), (2, "bg_odd", s2, None)]
    for _, _, table, reverse in out:
        check_table(stage, data, table)
        if reverse is not None:
            check_table(stage, data, reverse)
    return [(stage, slot, gate, table, reverse) for slot, gate, table, reverse in out]


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/qqz_cycle_catalogue.py from the USA disc; file offsets (hex) only.",
             "stage\tslot\tgate\ttable\treverse"]
    with RawIso(track) as disc:
        for stage in VARIANT:
            for stage_, slot, gate, table, reverse in rows(stage, disc.file(f"{stage}__.MMD;1")):
                lines.append(f"{stage_}\t{slot}\t{gate}\t{table:X}\t{'-' if reverse is None else format(reverse, 'X')}")
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
