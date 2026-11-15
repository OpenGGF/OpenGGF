#!/usr/bin/env python3
"""Generate the Collision Chaos disc-table offsets for R31A-R33D.

Purpose: CczPaletteCycle, CczScrollHandler and CczAnimatedTiles read their tables from the stage
file instead of carrying copies of disc bytes. Each stage file holds its own copy of the routines,
so every table is located by the instruction that reads it (same method as ppz_cycle_catalogue.py).
Instruction sequences are taken from scddisasm (pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff):
  palette cycle scripts: Level/Collision Chaos/Palette Cycle (Act 1 Variant).asm:7-31 (Past: rts,
      Good Future: two scripts, Bad Future: three) and Palette Cycle (Present).asm:11-28 (four);
      CycleColor is Level/Palette Cycle Script.asm:15-47
  wobble table: the lea WobbleTable,a4 of the water pass (r3/scroll_*.asm, Scroll (Act 1 Present).asm)
  animated tiles: Animated Tiles Update (Act 2).asm:48-85 (frame table :100-103, two DMA windows)
The output holds offsets, lengths and VRAM words only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/ccz-disc-tables.tsv (or --check against it).
Origin: Collision Chaos presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/ccz_cycle_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/ccz-disc-tables.tsv"
WORD_RAM = 0x200000
STAGES = ["R31A", "R31B", "R31C", "R31D", "R32A", "R32B", "R32C", "R32D", "R33C", "R33D"]
# CycleColor calls by time period: Present four, Past none, Good Future two, Bad Future three.
SCRIPTS = {"A": 4, "B": 0, "C": 2, "D": 3}
# Acts 1 and 3 jump straight to the powerup loader (Animated Tiles Update (Act 1 Present).asm:17-18);
# act 2 animates its Present, Good Future and Bad Future (Animated Tiles Update (Act 2).asm:48-85).
ANIMATED = {"R32A", "R32C", "R32D"}

# lea palCycleTimers.w,a5 ; lea palCycleSteps.w,a4
TIMER_STEP_LEAS = b"\x4b\xf8..\x49\xf8.."
# lea Script,a1 ; lea Colors,a2
SCRIPT_COLORS = b"\x43\xf9(....)\x45\xf9(....)"
# subq.b #1,(a5): the first instruction of CycleColor the last call falls into
CYCLE_COLOR_ENTRY = b"\x53\x15"


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def scripts(stage: str, data: bytes, count: int) -> list[tuple]:
    pattern = re.compile(TIMER_STEP_LEAS + (SCRIPT_COLORS + b"\x61.") * (count - 1) + SCRIPT_COLORS
                         + CYCLE_COLOR_ENTRY, re.S)
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one {count}-script PaletteCycle, got {len(matches)}")
    groups = matches[0].groups()
    return [(stage, "script", slot, offset(groups[2 * slot], data), offset(groups[2 * slot + 1], data))
            for slot in range(count)]


def wobble(stage: str, data: bytes) -> tuple:
    """lea (a2,d0.w),a2 ; lea Bounds,a3 ; lea WobbleTable,a4 ; addi.w #$40,bgWaterDeform."""
    pattern = re.compile(b"\x45\xf2\x00\x00\x47\xf9(....)\x49\xf9(....)\x06\x78\x00\x40..", re.S)
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one water-pass prologue, got {len(matches)}")
    return stage, "wobble", 0, offset(matches[0].group(2), data), offset(matches[0].group(1), data)


def animated(stage: str, data: bytes) -> list[tuple]:
    """Frame table (four pointers) and the two DMA windows of UpdateAnimTiles."""
    # lsl.w #3,d0 ; movea.l T(pc,d0.w),a1 ; movea.l T+4(pc,d0.w),a2. Bytes such as $24 and $2A are
    # regular-expression metacharacters, so the literal parts are escaped.
    code = re.compile(re.escape(b"\xe7\x48\x22\x7b\x00") + b"(.)" + re.escape(b"\x24\x7b\x00") + b"(.)", re.S)
    matches = list(code.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one CCAnimatedTileFrames lookup, got {len(matches)}")
    m = matches[0]
    # The displacement is relative to the extension word of each movea.l (two bytes into it).
    table = m.start() + 4 + m.group(1)[0]
    require(table + 16 <= len(data), f"{stage}: frame table outside the stage file")
    require(m.start() + 8 + m.group(2)[0] == table + 4, f"{stage}: second lookup is not table+4")
    out = [(stage, "frame", index,
            offset(data[table + 8 * index: table + 8 * index + 4], data),
            offset(data[table + 8 * index + 4: table + 8 * index + 8], data)) for index in (0, 1)]
    # move.l #$9400 93nn,(a5) ; move.l #$968C95C0,(a5) ; move.w #$977F,(a5) ; move.w #VRAM,(a5)
    dma = re.compile(re.escape(b"\x2a\xbc\x94\x00\x93") + b"(.)"
                     + re.escape(b"\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f\x3a\xbc") + b"(..)", re.S)
    found = list(dma.finditer(data, m.end()))
    require(len(found) >= 2, f"{stage}: expected two DMA command groups after the frame lookup")
    for slot, hit in enumerate(found[:2]):
        out.append((stage, "dma", slot, hit.group(1)[0], int.from_bytes(hit.group(2), "big")))
    return out


def rows(stage: str, data: bytes) -> list[tuple]:
    out: list[tuple] = []
    count = SCRIPTS[stage[-1]]
    if count:
        out += scripts(stage, data, count)
    out.append(wobble(stage, data))
    if stage in ANIMATED:
        out += animated(stage, data)
    return out


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/ccz_cycle_catalogue.py from the USA disc; stage-file offsets (hex) only.",
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
