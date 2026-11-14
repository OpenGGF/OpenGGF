#!/usr/bin/env python3
"""Generate the Stardust Speedway palette-cycle, animation-absence and sine-table offsets for the ten stages.

Purpose: the SSZ presentation classes read each stage's palette-cycle scripts and colours (and the sine
table the Past wobble uses) from the stage file instead of carrying copies of disc bytes. Every stage file
carries its own copy of the routines, so each table is located by the instruction that reads it (same method
as wwz_presentation_catalogue.py and ppz_cycle_catalogue.py). The instruction sequences come from scddisasm
(pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), directory src/r7/:
  palette cycle:  CyclePalette (r7/palette_cycle_{a,b,c,d}.asm:3-27, called at common/main.asm:447).
      A (Present, R71A R72A): three CycleColor calls (palette_cycle_a.asm:3-14).
      B and C (Past, Good Future): the routine's FIRST instruction is a bare `rts` (palette_cycle_b.asm:4,
      palette_cycle_c.asm:4), so the four calls behind it never run in the shipped game: no cycling at all.
      D (Bad Future, R71D R72D R73D): six calls; the sixth picks its script with `tst.b act`
      (palette_cycle_d.asm:21-27), act 0 -> byte_2002E4, otherwise byte_2002C2.
  animated tiles: AnimateStageGfx is only `jmp LoadPowerupArt` in all ten stages (r7/stage_gfx_animate_*.asm:3-4).
      It is called from the stage start (common/main.asm:309) and every V-blank (common/main.asm:892); the call
      site `jsr AnimateStageGfx ; move.b #1,fade_enable_display` is found and its target must start with the
      `jmp` opcode, else the generator fails (a stage with a real animator is not a "noanim" stage).
  sine table:     SineTable (common/math.asm:14-54), 320 words, used by the Past wobble (r7/scroll_{1b,2b}.asm:386-404).
The output holds offsets and a few decoded operands only, never disc bytes.

Rows: stage, kind, slot, a, b, c (hex).
  inert      slot 0; the palette-cycle routine is a bare rts                 (a = offset of the `rts`)
  script     slot = CycleColor call number, a = script offset, b = colours offset,
             c = 0 always, 1 = only when `act` is 0 (R71*), 2 = only when `act` is not 0 (R72*, R73*)
  noanim     slot 0; AnimateStageGfx is `jmp LoadPowerupArt` only             (a = offset of the routine)
  sine       slot 0; SineTable                                                 (a = offset)

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/ssz-presentation.tsv (or --check against it).
Origin: Stardust Speedway presentation lane of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/ssz_presentation_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/ssz-presentation.tsv"
WORD_RAM = 0x200000
STAGES = ["R71A", "R71B", "R71C", "R71D", "R72A", "R72B", "R72C", "R72D", "R73C", "R73D"]
# Stages whose scroll routine reads SineTable (the Past wobble, r7/scroll_{1b,2b}.asm).
SINE_STAGES = {"R71B", "R72B"}

# lea palCycleTimers.w,a5 ; lea palCycleSteps.w,a4
TIMER_STEP_LEAS = rb"\x4b\xf8..\x49\xf8.."
# lea Script,a1 ; lea Colors,a2
SCRIPT_COLORS = rb"\x43\xf9(....)\x45\xf9(....)"
# bsr.w CycleColor | bsr.s CycleColor
BSR = rb"(?:\x61\x00..|\x61[^\x00\xff])"
# lea ScriptNonZeroAct,a1 ; tst.b act ; bne.s +6 ; lea ScriptZeroAct,a1 ; lea Colors,a2   (palette_cycle_d.asm:21-27)
ACT_FINAL = rb"\x43\xf9(....)\x4a\x39....\x66\x06\x43\xf9(....)\x45\xf9(....)"
# subq.b #1,(a5) : the first instruction of CycleColor, which ends a fall-through call sequence
CYCLE_COLOR_HEAD = rb"\x53\x15"
# jsr AnimateStageGfx ; move.b #1,fade_enable_display   (common/main.asm:309-310)
ANIM_CALL = rb"\x4e\xb9(....)\x13\xfc\x00\x01"


def offset(address: bytes, data: bytes) -> int:
    value = (int.from_bytes(address, "big") & 0xFFFFFF) - WORD_RAM
    require(0 <= value < len(data), f"{value:#x} outside the stage file")
    return value


def palette_rows(stage: str, data: bytes) -> list[tuple]:
    heads = list(re.finditer(TIMER_STEP_LEAS + rb"(?=\x43\xf9)", data, re.S))
    require(len(heads) == 1, f"{stage}: expected one CyclePalette, got {len(heads)}")
    start = heads[0].start()
    if data[start - 2:start] == b"\x4e\x75":
        # r7/palette_cycle_{b,c}.asm:4: `rts` is the routine's first instruction; everything behind it is dead.
        return [(stage, "inert", 0, start - 2, 0, 0)]
    pos = heads[0].end()
    rows: list[tuple] = []
    slot = 0
    while True:
        act_final = re.compile(ACT_FINAL, re.S).match(data, pos)
        if act_final is not None:
            non_zero_act, zero_act, colors = (offset(g, data) for g in act_final.groups())
            rows.append((stage, "script", slot, non_zero_act, colors, 2))
            rows.append((stage, "script", slot, zero_act, colors, 1))
            pos = act_final.end()
            break
        pair = re.compile(SCRIPT_COLORS, re.S).match(data, pos)
        require(pair is not None, f"{stage}: CycleColor call {slot} not found at {pos:#x}")
        rows.append((stage, "script", slot, offset(pair.group(1), data), offset(pair.group(2), data), 0))
        pos = pair.end()
        call = re.compile(BSR, re.S).match(data, pos)
        if call is None:
            break                                  # fall-through into CycleColor itself
        pos = call.end()
        slot += 1
    require(re.compile(CYCLE_COLOR_HEAD).match(data, pos) is not None, f"{stage}: no CycleColor after the last call")
    return rows


def animation_rows(stage: str, data: bytes) -> list[tuple]:
    calls = list(re.finditer(ANIM_CALL, data, re.S))
    require(len(calls) >= 1, f"{stage}: no AnimateStageGfx call site")
    target = offset(calls[0].group(1), data)
    require(data[target:target + 2] == b"\x4e\xf9", f"{stage}: AnimateStageGfx at {target:#x} is not a bare jmp")
    return [(stage, "noanim", 0, target, 0, 0)]


def sine_row(stage: str, data: bytes) -> list[tuple]:
    if stage not in SINE_STAGES:
        return []
    head = bytes.fromhex("0000 0006 000C 0012 0019 001F 0025 002B 0031 0038".replace(" ", ""))
    at = [m.start() for m in re.finditer(re.escape(head), data)]
    require(len(at) >= 1, f"{stage}: no sine table")
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
    lines = ["# Generated by tools/soniccd/ssz_presentation_catalogue.py from the USA disc; file offsets (hex) only.",
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
