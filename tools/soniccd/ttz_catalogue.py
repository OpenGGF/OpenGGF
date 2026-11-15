#!/usr/bin/env python3
"""Generate the Tidal Tempest presentation-table offsets for the ten R4 stage files.

Purpose: the TtzPaletteCycle, TtzScrollHandler and TtzPatternAnimator read their cycle scripts, colour
tables, wobble table and animated-tile scripts from the stage file instead of carrying copies of disc
bytes. Each stage file holds its own copy of the engine, so every table is located by the instruction
that reads it (same method as ppz_cycle_catalogue.py and stage_catalogue.py). The instruction sequences
come from scddisasm (pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff):
  cycle, R41A/R42A: CyclePalette (src/r4/palette_cycle_a.asm:4-30 standard path of four CycleColor
      calls, :34-53 the six-call upper-screen path of act 1 Present)
  cycle, R41B-R43D: R4PaletteCycleActive (src/r4/palette_cycle_b.asm:11-22, palette_cycle_c.asm:11-22)
      three CycleColor calls, the last reached through "bra.w *+4"
  wobble, all ten: UpdateScroll's water loop loads WaterWobble through "lea WobbleTable,a4" right after
      "lea <table>,a3" and before "move.b bg_water_deform,d3" (src/r4/scroll_1a.asm:308-311;
      Level/Tidal Tempest/R41A Legacy Scroll.asm:315-317; src/r4/scroll_2a.asm:262-265)
  anim, R41A only: AnimateStageGfx (src/r4/stage_gfx_animate_1a.asm:11-49): three scripts, each followed
      by a DMA whose destination is the VDP command pair "move.w #$4500,(a5) ... move.w #$81,dma_stack"
The output holds offsets only, never disc bytes. Row layout (all numbers hex):
  stage kind slot a b
  cycle-std / cycle-upper : slot = timer/step slot, a = timing script offset, b = colour table offset
  wobble                  : a = WobbleTable offset (256 bytes), b = 0
  anim                    : slot = script number, a = script offset, b = destination VRAM byte address

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/ttz-presentation.tsv (or --check against it).
Origin: Tidal Tempest presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 tools/soniccd/ttz_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/ttz-presentation.tsv"
WORD_RAM = 0x200000
STAGES = ["R41A", "R41B", "R41C", "R41D", "R42A", "R42B", "R42C", "R42D", "R43C", "R43D"]

# lea palette_cycle_timers.w,a5 ; lea palette_cycle_steps.w,a4
TIMER_STEP_LEAS = rb"\x4b\xf8..\x49\xf8.."
# lea Timing,a1 ; lea Colors,a2
GROUP = rb"\x43\xf9(....)\x45\xf9(....)"
BSR = rb"\x61\x00.."          # bsr.w CycleColor
BRA_NEXT = rb"\x60\x00\x00\x02"  # bra.w *+4 (falls into CycleColor)
# lea <table>,a3 ; lea WobbleTable,a4 ; move.b bg_water_deform.w,d3
WOBBLE = rb"\x47\xf9....\x49\xf9(....)\x16\x38.."
# lea Script,a1 ; move.w #$7F,d6 ; bsr.w AnimateStageGfxScript ; bne.s ; lea VDP_CTRL,a5 ;
# move.l #$94019300,(a5) ; move.l #$968C95C0,(a5) ; move.w #$977F,(a5) ; move.w #$xxxx,(a5) ;
# move.w #$81,dma_stack
ANIM = (rb"\x43\xf9(....)\x3c\x3c\x00\x7f\x61\x00..\x66.\x4b\xf9\x00\xc0\x00\x04"
        rb"\x2a\xbc\x94\x01\x93\x00\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f\x3a\xbc(..)\x31\xfc(..)..")


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def cycle_rows(stage: str, data: bytes) -> list[tuple]:
    """Every lea-timer/lea-step run followed by CycleColor calls, classified by call count."""
    runs = []
    for lead in re.finditer(TIMER_STEP_LEAS, data, re.S):
        position = lead.end()
        groups = []
        while True:
            found = re.compile(GROUP, re.S).match(data, position)
            if not found:
                break
            groups.append((offset(found.group(1), data), offset(found.group(2), data)))
            position = found.end()
            if re.compile(BSR, re.S).match(data, position):
                position += 4
            elif re.compile(BRA_NEXT, re.S).match(data, position):
                position += 4
                break
            else:
                break          # the last call falls through into CycleColor
        if groups:
            runs.append(groups)
    # R41A/R42A: a four-call standard path then a six-call upper-screen path. Others: one three-call path.
    if stage in ("R41A", "R42A"):
        standard = [g for g in runs if len(g) == 4]
        upper = [g for g in runs if len(g) == 6]
        require(len(standard) == 1 and len(upper) == 1, f"{stage}: expected one 4-call and one 6-call path, got "
                f"{[len(g) for g in runs]}")
        return ([(stage, "cycle-std", slot, s, c) for slot, (s, c) in enumerate(standard[0])]
                + [(stage, "cycle-upper", slot, s, c) for slot, (s, c) in enumerate(upper[0])])
    three = [g for g in runs if len(g) == 3]
    require(len(three) == 1, f"{stage}: expected one 3-call path, got {[len(g) for g in runs]}")
    return [(stage, "cycle-std", slot, s, c) for slot, (s, c) in enumerate(three[0])]


def wobble_row(stage: str, data: bytes) -> tuple:
    matches = list(re.finditer(WOBBLE, data, re.S))
    require(len(matches) == 1, f"{stage}: expected one wobble load, got {len(matches)}")
    return (stage, "wobble", 0, offset(matches[0].group(1), data), 0)


def anim_rows(stage: str, data: bytes) -> list[tuple]:
    matches = list(re.finditer(ANIM, data, re.S))
    require(len(matches) == 3, f"{stage}: expected three animated-tile scripts, got {len(matches)}")
    rows = []
    for slot, match in enumerate(matches):
        first = int.from_bytes(match.group(2), "big")
        second = int.from_bytes(match.group(3), "big")
        # VDP DMA command pair: A13-0 in the first word, A15-14 in bits 1-0 of the second.
        vram = (first & 0x3FFF) | ((second & 3) << 14)
        rows.append((stage, "anim", slot, offset(match.group(1), data), vram))
    return rows


def rows(stage: str, data: bytes) -> list[tuple]:
    out = cycle_rows(stage, data)
    out.append(wobble_row(stage, data))
    if stage == "R41A":          # the other nine stages' AnimateStageGfx is "jmp LoadPowerupArt" only
        out += anim_rows(stage, data)
    return out


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/ttz_catalogue.py from the USA disc; file offsets (hex) only.",
             "stage\tkind\tslot\ta\tb"]
    with RawIso(track) as disc:
        for stage in STAGES:
            for row in rows(stage, disc.file(f"{stage}__.MMD;1")):
                lines.append(f"{row[0]}\t{row[1]}\t{row[2]}\t{row[3]:X}\t{row[4]:X}")
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
