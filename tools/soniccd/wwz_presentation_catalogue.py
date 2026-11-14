#!/usr/bin/env python3
"""Generate the Wacky Workbench palette-cycle, animated-tile and sine-table offsets for the ten stages.

Purpose: the WWZ presentation classes read each stage's cycle scripts, colours, tile-animation tables
and the perspective sine table from the stage file instead of carrying copies of disc bytes. Every
stage file carries its own copy of the routines, so each table is located by the instruction that reads
it (same method as ppz_cycle_catalogue.py and stage_catalogue.py). The instruction sequences come from
scddisasm (pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), directory src/r6/ (and src/Level/Wacky Workbench/
for R61A, which assembles the same routines):
  palette cycle:  PaletteCycle / CyclePalette: four CycleColor calls
      (Level/Wacky Workbench/Palette Cycle (Present).asm:12-31, r6/palette_cycle_{a,b,c,d}.asm:3-19);
      palette_cycle_c/d add the boss-colour override `btst #5,boss_flags` (:17-19)
  animated tiles: AnimateStageGfx (r6/stage_gfx_animate_*.asm:3-47; Level/Wacky Workbench/Animated Tiles
      Update (Act 1 Present).asm:7-34). Shapes: plain (1A 1B 1D 2A 2B 2D, 3 calls), blink (1C 2C: stage_gfx_
      animate_1c.asm:19-77), bossgate (3C 3D: stage_gfx_animate_3c.asm:10-64).
  sine table:     SineTable (Level/Functions (Misc).asm:76-96), 320 words.
The output holds offsets and a few decoded operands only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/wwz-presentation.tsv (or --check against it).
Origin: Wacky Workbench presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/wwz_presentation_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/wwz-presentation.tsv"
WORD_RAM = 0x200000
STAGES = ["R61A", "R61B", "R61C", "R61D", "R62A", "R62B", "R62C", "R62D", "R63C", "R63D"]
# Stages whose scroll routine is the sine-perspective one (r6/scroll_{1a,1c,1d,2a,2c,2d,3c,3d}.asm).
SINE_STAGES = {"R61A", "R61C", "R61D", "R62A", "R62C", "R62D", "R63C", "R63D"}

# lea palCycleTimers.w,a5 ; lea palCycleSteps.w,a4
TIMER_STEP_LEAS = rb"\x4b\xf8..\x49\xf8.."
# lea Script,a1 ; lea Colors,a2
SCRIPT_COLORS = rb"\x43\xf9(....)\x45\xf9(....)"
# bsr.w CycleColor | bsr.s CycleColor
BSR = rb"(?:\x61\x00..|\x61[^\x00\xff])"
# btst #5,boss_flags ; beq.s ; lea BossColors,a2   (palette_cycle_c.asm:17-19)
BOSS_COLORS = rb"(?:\x08\x38\x00\x05..\x67\x06\x45\xf9(....))?"

# lea VDP_CTRL,a5 ; move.l #$9400LLLL,(a5) ; move.l #$968C95C0,(a5) ; move.w #$977F,(a5) ; move.w #dest,(a5) ;
# move.w #$00SS,dma_stack.w ; move.w dma_stack.w,(a5)      (stage_gfx_animate_1b.asm:11-17)
DMA = (rb"\x4b\xf9\x00\xc0\x00\x04\x2a\xbc\x94\x00(..)\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f"
       rb"\x3a\xbc(..)\x31\xfc\x00(.)\xf6\x40\x3a\xb8\xf6\x40")
# lea Table,a1 ; move.w #d6,d6 ; bsr.w Animate
ANIM_HEAD = rb"\x43\xf9(....)\x3c\x3c(..)\x61\x00(..)"
# Special blink (stage_gfx_animate_1c.asm:19-30): subq.b #1,timers+2 ; bmi.s ; bra.s ; eori.b #1,frames+2 ;
# move.b frames+2,d0 ; ext.w d0 ; move.b dur(pc,d0.w),timers+2
BLINK = (rb"\x53\x38(..)\x6b\x02\x60\x12\x0a\x38\x00\x01(..)\x10\x38..\x48\x80\x11\xfb\x00(.)..")


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def dest(w1: bytes, w2: bytes) -> int:
    """VDP command word + the dma_stack word -> VRAM byte address (stage_gfx_animate_1b.asm:15-17)."""
    return (int.from_bytes(w1, "big") & 0x3FFF) | ((w2[0] & 3) << 14)


def palette_rows(stage: str, data: bytes) -> list[tuple]:
    pattern = re.compile(TIMER_STEP_LEAS + (SCRIPT_COLORS + BSR) * 3 + SCRIPT_COLORS + BOSS_COLORS, re.S)
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, f"{stage}: expected one PaletteCycle, got {len(matches)}")
    groups = matches[0].groups()
    rows = [(stage, "script", slot, offset(groups[2 * slot], data), offset(groups[2 * slot + 1], data), 0)
            for slot in range(4)]
    if groups[8] is not None:
        rows.append((stage, "bosscolors", 3, offset(groups[8], data), 0, 0))
    return rows


def routine_kind(data: bytes, target: int) -> str:
    """AnimateTilesSimple (subq ; bpl.w ; move.b (a1),(a2)) vs AnimateStageGfxScript (subq ; bpl.s ; moveq)."""
    head = data[target:target + 12]
    if re.match(rb"\x53\x12\x6a\x00..\x14\x91", head, re.S):
        return "simple"
    if re.match(rb"\x53\x12\x6a[^\x00\xff]\x70\x00\x10\x14", head, re.S):
        return "script"
    raise ValueError(f"unrecognised animation routine at {target:#x}")


def animation_rows(stage: str, data: bytes) -> list[tuple]:
    """Every `lea Table,a1 ; move.w #d6,d6 ; bsr.w Animate*` call and the DMA block that uploads its frame.

    Slots are call order: each Animate* call advances a2/a4 by one (stage_gfx_animate_1b.asm:5-6, 9, 22, 35).
    Shapes (see module docstring): plain, blink (the 1C/2C blinker uses timer slot 2 by hand and is not a call),
    bossgate (the first call's ordinary upload follows the boss branches, stage_gfx_animate_3c.asm:10-51).
    """
    shape = {"R61C": "blink", "R62C": "blink", "R63C": "bossgate", "R63D": "bossgate"}.get(stage, "plain")
    # The live routine starts `jsr LoadPowerupArt ; lea timers,a2 ; lea frames,a4 ; lea Table,a1` (gfx_animate_1b.asm:4-7)
    # and ends at its first rts. Some stage files also carry unreachable copies of a neighbouring stage's routine.
    anchors = list(re.finditer(rb"\x4e\xb9....\x45\xf8..\x49\xf8..(?=\x43\xf9)", data, re.S))
    require(len(anchors) >= 1, f"{stage}: no AnimateStageGfx entry")
    start = anchors[0].end()
    heads = []
    previous = start
    for m in re.finditer(ANIM_HEAD, data, re.S):
        if m.start() < start:
            continue
        if m.start() - previous > 0x140:           # a later, unconnected head belongs to another routine copy
            break
        target = m.start() + 12 + int.from_bytes(m.group(3), "big", signed=True)
        try:
            kind = routine_kind(data, target)
        except ValueError:
            continue
        heads.append((m, kind))
        previous = m.start()
    # Calls per stage: plain stages and R63D run the 3-frame, 2-frame and scripted animators; R61C/R62C and R63C
    # have two calls (stage_gfx_animate_1c.asm:7-57, stage_gfx_animate_3c.asm:7-66).
    expected = 2 if stage in ("R61C", "R62C", "R63C") else 3
    require(len(heads) == expected, f"{stage}: expected {expected} animation calls, got {len(heads)}")
    out: list[tuple] = []
    for slot, (m, kind) in enumerate(heads):
        limit = heads[slot + 1][0].start() if slot + 1 < len(heads) else m.end() + 400
        found = None
        for dm in re.finditer(DMA, data[m.end():limit], re.S):
            if shape == "bossgate" and slot == 0 and dm.group(3)[0] & 3 == 2:
                continue                          # the boss-region upload (dma_stack $82), needs boss_flags
            found = dm
            break
        require(found is not None, f"{stage}: call {slot} has no DMA block")
        d6 = int.from_bytes(m.group(2), "big")
        require(found.group(1)[0] == 0x93, f"{stage}: DMA length register is not $13")
        words = found.group(1)[1]                  # $9400 (length high) then $93LL (length low), words
        require(words * 2 == (d6 + 1) * 4, f"{stage}: DMA length {words:#x} words disagrees with d6 {d6:#x}")
        out.append((stage, f"anim_{kind}", slot, offset(m.group(1), data), d6, dest(found.group(2), found.group(3))))
    if shape == "blink":
        bm = list(re.finditer(BLINK, data, re.S))
        require(len(bm) == 1, f"{stage}: expected one special blink, got {len(bm)}")
        # move.b dur(pc,d0.w),timers+2 is `11 FB 00 dd xx xx`: displacement is relative to the extension word.
        ext_word = bm[0].end() - 4
        out.append((stage, "blink", 2, ext_word + bm[0].group(3)[0], 0, 0))
    return out


def sine_row(stage: str, data: bytes) -> list[tuple]:
    if stage not in SINE_STAGES:
        return []
    head = bytes.fromhex("0000 0006 000C 0012 0019 001F 0025 002B 0031 0038".replace(" ", ""))
    at = [m.start() for m in re.finditer(re.escape(head), data)]
    require(len(at) >= 1, f"{stage}: no sine table")
    # SineTable is followed by 64 extra cosine words; the first hit is the table (Functions (Misc).asm:76-96).
    table = at[0]
    require(int.from_bytes(data[table + 128:table + 130], "big") == 0x100, f"{stage}: sine peak missing")
    require(int.from_bytes(data[table + 640 - 2:table + 640], "big") == 0x00FF, f"{stage}: cosine tail missing")
    return [(stage, "sine", 0, table, 0, 0)]


def rows(stage: str, data: bytes) -> list[tuple]:
    return palette_rows(stage, data) + animation_rows(stage, data) + sine_row(stage, data)


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/wwz_presentation_catalogue.py from the USA disc; file offsets (hex) only.",
             "stage\tkind\tslot\ta\tb\tc"]
    with RawIso(track) as disc:
        for stage in STAGES:
            for stage_, kind, slot, a, b, c in rows(stage, disc.file(f"{stage}__.MMD;1")):
                lines.append(f"{stage_}\t{kind}\t{slot}\t{a:X}\t{b:X}\t{c:X}")
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
