#!/usr/bin/env python3
"""Generate the Metallic Madness palette-cycle, animated-tile and scroll-gradient table offsets for the ten stages.

Purpose: the MMZ presentation classes read each stage's cycle scripts, colours, tile-animation tables and
the background gradient segment-length tables from the stage file instead of carrying copies of disc bytes.
Every stage file carries its own copy of the routines, so each table is located by the instruction that
reads it (same method as wwz_presentation_catalogue.py and ppz_cycle_catalogue.py). Sources: scddisasm (pin
1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), src/r8/ and src/Level/Metallic Madness/:
  palette cycle:  CyclePalette = r8/palette_cycle_{a,b,c,d}.asm:3-30 (a: R81A R82A; b: R81B R82B; c: R81C R82C
      R83C; d: R81D R82D R83D). The routine branches on `act` (0 = act 1, 1 = act 2, 2 = act 3;
      `cmpi.b #1,act` / `tst.b act`), so the generator walks the stage's own machine code with the stage's act
      value and records the (script, colours) pair of every `bsr/bra CycleColor` it reaches, in order.
  animated tiles: AnimateStageGfx = r8/stage_gfx_animate_*.asm (calls AnimateTilesSimple or
      AnimateStageGfxScript, then a DMA block per call; R83C/R83D gate their last call on `boss_door_flag`
      and frame slot (a4), stage_gfx_animate_3c.asm:loc_20FB18). R81B's AnimateStageGfx is a bare
      `jmp LoadPowerupArt` and has no animation.
  scroll:         the gradient segment-length tables that UpdateScroll's helper reads with
      `move.b table(pc,d6.w),d5` (r8/scroll_1a.asm:sub_202B38, scroll_2a.asm:sub_202B10 and sub_202B36, ...)
      or `move.b (a2,d6.w),d5` with a2 loaded before each call (r8/scroll_1b.asm:UpdateScroll, sub_202AE0).
The output holds offsets and a few decoded operands only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/mmz-presentation.tsv (or --check against it).
Origin: Metallic Madness presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/mmz_presentation_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/mmz-presentation.tsv"
WORD_RAM = 0x200000
STAGES = ["R81A", "R81B", "R81C", "R81D", "R82A", "R82B", "R82C", "R82D", "R83C", "R83D"]
ACT = {"R81": 0, "R82": 1, "R83": 2}
# Executed CycleColor calls per stage (r8/palette_cycle_*.asm): the walk is checked against these counts.
CYCLE_CALLS = {"R81A": 6, "R82A": 6, "R81B": 5, "R82B": 5, "R81C": 4, "R82C": 3, "R83C": 3,
               "R81D": 6, "R82D": 6, "R83D": 6}
# Animation calls per stage (r8/stage_gfx_animate_*.asm); R83C/R83D include the gated last call.
ANIM_CALLS = {"R81A": 5, "R81B": 0, "R81C": 3, "R81D": 4, "R82A": 5, "R82B": 1, "R82C": 4, "R82D": 5,
              "R83C": 4, "R83D": 5}
# Gradient helper tables per stage in file order (asc then desc where both exist).
GRAD_TABLES = {"R81A": 1, "R81B": 2, "R81C": 1, "R81D": 1, "R82A": 2, "R82B": 2, "R82C": 2, "R82D": 2,
               "R83C": 2, "R83D": 0}

CYCLE_ENTRY = rb"\x4b\xf8..\x49\xf8.."          # lea palCycleTimers.w,a5 ; lea palCycleSteps.w,a4
CYCLE_COLOR_HEAD = b"\x53\x15"                    # CycleColor: subq.b #1,(a5)

# lea VDP_CTRL,a5 (only the first block of a routine has it) ; move.l #$9400LLLL,(a5) ; move.l #$968C95C0,(a5) ;
# move.w #$977F,(a5) ; move.w #dest,(a5) ; move.w #$00SS,dma_stack.w ; move.w dma_stack.w,(a5)
# (stage_gfx_animate_1a.asm:loc_20E94E ff.)
DMA = (rb"(?:\x4b\xf9\x00\xc0\x00\x04)?\x2a\xbc\x94(.)\x93(.)\x2a\xbc\x96\x8c\x95\xc0\x3a\xbc\x97\x7f"
       rb"\x3a\xbc(..)\x31\xfc\x00(.)\xf6\x40\x3a\xb8\xf6\x40")
# lea Table,a1 ; move.w #d6,d6 ; bsr.w Animate
ANIM_HEAD = rb"\x43\xf9(....)\x3c\x3c(..)\x61\x00(..)"


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def dest(w1: bytes, w2: bytes) -> int:
    """VDP command word + the dma_stack word -> VRAM byte address (stage_gfx_animate_1b.asm:11-17)."""
    return (int.from_bytes(w1, "big") & 0x3FFF) | ((w2[0] & 3) << 14)


def palette_rows(stage: str, data: bytes) -> list[tuple]:
    """Walk CyclePalette (r8/palette_cycle_*.asm:3-30) for the stage's own act and list its CycleColor calls."""
    act = ACT[stage[:3]]
    entries = list(re.finditer(CYCLE_ENTRY + rb"(?=\x43\xf9)", data, re.S))
    require(len(entries) == 1, f"{stage}: expected one CyclePalette, got {len(entries)}")
    pc = entries[0].end()
    a1 = a2 = None
    zero = False
    calls: list[tuple[int, int]] = []

    def is_cycle_color(target: int) -> bool:
        return data[target:target + 2] == CYCLE_COLOR_HEAD

    for _ in range(400):
        op = data[pc:pc + 2]
        word = int.from_bytes(op, "big")
        if is_cycle_color(pc):                                # fell through into CycleColor (palette_cycle_a.asm:CyclePalette tail)
            calls.append((a1, a2))
            break
        if word == 0x43F9:                                    # lea abs.l,a1
            a1 = offset(data[pc + 2:pc + 6], data)
            pc += 6
        elif word == 0x45F9:                                  # lea abs.l,a2
            a2 = offset(data[pc + 2:pc + 6], data)
            pc += 6
        elif word == 0x0C39 and data[pc + 2:pc + 4] == b"\x00\x01":   # cmpi.b #1,act.l
            zero = act == 1
            pc += 8
        elif word == 0x4A39:                                  # tst.b act.l
            zero = act == 0
            pc += 6
        elif op[0] in (0x66, 0x67):                           # bne / beq
            taken = zero if op[0] == 0x67 else not zero
            disp = op[1]
            if disp == 0:
                target = pc + 2 + int.from_bytes(data[pc + 2:pc + 4], "big", signed=True)
                size = 4
            else:
                target = pc + 2 + (disp - 256 if disp >= 128 else disp)
                size = 2
            pc = target if taken else pc + size
        elif op[0] == 0x61:                                   # bsr
            disp = op[1]
            if disp == 0:
                target = pc + 2 + int.from_bytes(data[pc + 2:pc + 4], "big", signed=True)
                size = 4
            else:
                target = pc + 2 + (disp - 256 if disp >= 128 else disp)
                size = 2
            require(is_cycle_color(target), f"{stage}: bsr at {pc:#x} is not CycleColor")
            calls.append((a1, a2))
            pc += size
        elif op[0] == 0x60:                                   # bra (tail call or jump to rts)
            disp = op[1]
            if disp == 0:
                target = pc + 2 + int.from_bytes(data[pc + 2:pc + 4], "big", signed=True)
                size = 4
            else:
                target = pc + 2 + (disp - 256 if disp >= 128 else disp)
                size = 2
            if is_cycle_color(target):
                calls.append((a1, a2))
                break
            pc = target
        elif word == 0x4E75:                                  # rts
            break
        else:
            raise ValueError(f"{stage}: unhandled opcode {word:04x} at {pc:#x}")
    require(len(calls) == CYCLE_CALLS[stage], f"{stage}: expected {CYCLE_CALLS[stage]} CycleColor calls, got {len(calls)}")
    return [(stage, "script", slot, s, c, 0) for slot, (s, c) in enumerate(calls)]


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

    Slots are call order: each Animate* call advances a2/a4 by one (stage_gfx_animate_1a.asm:AnimateTilesSimple).
    R83C/R83D: the last call is preceded by `tst.b boss_door_flag ; beq ; tst.b (a4) ; beq rts`
    (stage_gfx_animate_3c.asm:loc_20FB18-20FB24) and gets an `anim_gate` row.
    """
    expected = ANIM_CALLS[stage]
    anchors = list(re.finditer(rb"\x4e\xb9....\x45\xf8..\x49\xf8..(?=\x43\xf9)", data, re.S))
    if expected == 0:
        require(len(anchors) == 0, f"{stage}: has an AnimateStageGfx entry but none expected")
        return []
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
    require(len(heads) == expected, f"{stage}: expected {expected} animation calls, got {len(heads)}")
    out: list[tuple] = []
    for slot, (m, kind) in enumerate(heads):
        limit = heads[slot + 1][0].start() if slot + 1 < len(heads) else m.end() + 400
        found = re.search(DMA, data[m.end():limit], re.S)
        require(found is not None, f"{stage}: call {slot} has no DMA block")
        d6 = int.from_bytes(m.group(2), "big")
        total = (found.group(1)[0] << 8) | found.group(2)[0]     # $94HH $93LL: DMA length in words
        require(total * 2 == (d6 + 1) * 4, f"{stage}: DMA length {total:#x} words disagrees with d6 {d6:#x}")
        out.append((stage, f"anim_{kind}", slot, offset(m.group(1), data), d6, dest(found.group(3), found.group(4))))
    if stage in ("R83C", "R83D"):
        gate = list(re.finditer(rb"\x4a\x39....\x67.\x4a\x14\x67.\x43\xf9", data, re.S))
        require(len(gate) >= 1, f"{stage}: no boss_door_flag gate before the last animation call")
        gated = [g for g in gate if heads[-1][0].start() - 0x20 < g.start() <= heads[-1][0].start()]
        require(len(gated) == 1, f"{stage}: boss_door_flag gate is not in front of the last call")
        out.append((stage, "anim_gate", expected - 1, 0, 0, 0))
    return out


def gradient_rows(stage: str, data: bytes) -> list[tuple]:
    """Segment-length tables of the gradient helpers (see module docstring)."""
    rows = []
    if stage == "R81B":
        # lea Table,a2 ; moveq #bytes,d4 ; moveq #n,d6 ; bsr.w sub_202AE0 (scroll_1b.asm:UpdateScroll)
        calls = list(re.finditer(rb"\x45\xf9(....)\x78.\x7c.\x61\x00", data, re.S))
        require(len(calls) == 2, f"{stage}: expected two gradient calls, got {len(calls)}")
        helper = list(re.finditer(rb"\x1a\x32\x60\x00", data))
        require(len(helper) == 1, f"{stage}: expected one a2-indexed table read")
        for slot, m in enumerate(calls):
            rows.append((stage, "grad", slot, offset(m.group(1), data), 0, 0))
        return rows
    reads = list(re.finditer(rb"\x1a\x3b[\x60-\x6f]", data))
    require(len(reads) == GRAD_TABLES[stage], f"{stage}: expected {GRAD_TABLES[stage]} gradient tables, got {len(reads)}")
    for slot, m in enumerate(reads):
        ext = m.start() + 2
        disp = data[ext + 1]
        rows.append((stage, "grad", slot, ext + (disp - 256 if disp >= 128 else disp), 0, 0))
    return rows


def rows(stage: str, data: bytes) -> list[tuple]:
    return palette_rows(stage, data) + animation_rows(stage, data) + gradient_rows(stage, data)


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/mmz_presentation_catalogue.py from the USA disc; file offsets (hex) only.",
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
