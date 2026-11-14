#!/usr/bin/env python3
"""Generate the Sonic CD player stage tables: per-stage file offsets of the tables
the player routines read.

Purpose: the per-zone player profiles read ObjSonic_SpecialChunks' four special
chunk IDs, ObjSonic_Handle3DRamp's 3D-ramp chunk list and the block check's
per-time-zone block lists, Stardust Speedway's spark blocks, Collision Chaos's slope-bumper heights and Tidal Tempest's water-current records from each stage's own copy
of the level engine. As in stage_catalogue.py, each table is located through
the instruction that consumes it, the sequences taken from the scddisasm routines
cited beside them (pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), and the output
holds offsets only, never disc bytes. A stage without the consuming instruction
gets "-".

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/player-stage-tables.tsv (or --check against it).
Origin: lane PX (player extras) of the 2026-11-12 Sonic CD re-host.

    python3 tools/soniccd/player_stage_tables.py --media-root ~/Downloads/Sonic-CD/media [--check]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_catalogue import STAGES, operands, pattern, to_offset  # noqa: E402
from stage_inventory import RawIso, require  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/player-stage-tables.tsv"

# (column, signature, citation). "AAAAAAAA" is the absolute-long operand being located.
ANCHORS = [
    # lea SpecChunks,a1; move.l (a1),specialChunks.w (Level/Palmtree Panic/
    # Scroll (Act 1 Present).asm:113-114; common dialect: move.l (a1),loop_chunk_1,
    # r3/scroll_1d.asm:160).
    ("specialChunks", "43F9 AAAAAAAA 21D1 ????",
     "LevelSizeLoad tail: lea SpecChunks,a1; move.l (a1),specialChunks.w"),
    # ObjSonic_Handle3DRamp: lea .ChunkList,a2; .CheckChunk: move.b (a2)+,d0;
    # bmi.s .NotFound; cmp.b d0,d1 (Level/_Objects/Sonic/Main.asm:798-803).
    ("ramp3dChunks", "45F9 AAAAAAAA 101A 6B?? B200",
     "ObjSonic_Handle3DRamp: lea .ChunkList,a2; move.b (a2)+,d0; bmi.s; cmp.b d0,d1"),
    # CheckWaterCurrents: lea WaterCurrents,a2; CheckWaterCurrentsScan: move.w obj.x(a1),d0;
    # cmp.w (a2),d0 (src/r4/main.asm:790-793). Tidal Tempest stages only.
    ("waterCurrents", "45F9 AAAAAAAA 3029 0008 B052",
     "CheckWaterCurrents: lea WaterCurrents,a2; move.w obj.x(a1),d0; cmp.w (a2),d0"),
]
# The block check's per-time-zone block lists, read PC-relative: add.w d1,d1;
# move.w PlayerBlocks(pc,d1.w),d1; lea PlayerBlocks(pc,d1.w),a1; moveq #0,d6;
# move.w (a1)+,d6; moveq #0,d1 (src/r5/player_block_check.asm:52-58; the Collision
# Chaos copy in src/r3 has the same lookup). The table is the word displacement's
# base: its extension word plus the signed byte.
PC_RELATIVE_ANCHORS = [
    ("playerBlocks", "D241 323B 10?? 43FB 10?? 7C00 3C19 7200", 4,
     "PlayerCheckBlock lookup: move.w/lea PlayerBlocks(pc,d1.w)"),
    # Stardust Speedway's spark blocks: add.w d1,d1; move.w off_20657A(pc,d1.w),d1;
    # lea off_20657A(pc,d1.w),a1; loc_20656E: move.w (a1)+,d1; bmi.s (src/r7/player_block_check.asm:28-34);
    # each list ends at a negative word.
    ("sparkBlocks", "D241 323B 10?? 43FB 10?? 3219 6B", 4,
     "PlayerCheckBlock (r7) lookup: move.w/lea off_20657A(pc,d1.w); move.w (a1)+,d1; bmi.s"),
    # Collision Chaos's slope bumpers, read by PlayerCheckBlock_2_Routine12 (src/r3/player_block_check.asm:442-446):
    # subi.w #$12,d1; bmi.s; move.w table(pc,d1.w),d0; lea table(pc,d0.w),a1.
    ("bumperSlopes", "0441 0012 6B?? 303B 10?? 43FB 00", 8,
     "PlayerCheckBlock_2_Routine12: move.w/lea PlayerBumperCollide(pc,d1.w/d0.w)"),
]
COLUMNS = ["stage"] + [a[0] for a in ANCHORS] + [a[0] for a in PC_RELATIVE_ANCHORS]


def pc_relative(data: bytes, signature: str, extension: int) -> list[int]:
    found = []
    for match in pattern(signature).finditer(data):
        base = match.start() + extension
        displacement = data[base + 1]
        target = base + (displacement - 0x100 if displacement & 0x80 else displacement)
        if target not in found:
            found.append(target)
    return found


def stage_row(stage: str, data: bytes) -> dict:
    row = {}
    for name, signature, _ in ANCHORS:
        hits = operands(data, signature)
        require(len(hits) <= 1, f"{stage} {name}: expected at most one operand, got {[hex(h) for h in hits]}")
        row[name] = to_offset(hits[0], data, name) if hits else None
    for name, signature, extension, _ in PC_RELATIVE_ANCHORS:
        hits = pc_relative(data, signature, extension)
        require(len(hits) <= 1, f"{stage} {name}: expected at most one table, got {[hex(h) for h in hits]}")
        row[name] = hits[0] if hits else None
    return row


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    tracks = parse_cue(cues[0].read_text())
    track = (cues[0].parent / tracks[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/player_stage_tables.py from the USA disc; file offsets (hex) only.",
             "\t".join(COLUMNS)]
    require(len(STAGES) == 70, f"Expected 70 stage payloads, got {len(STAGES)}")
    with RawIso(track) as disc:
        for stage in STAGES:
            row = stage_row(stage, disc.file(f"{stage}__.MMD;1"))
            lines.append("\t".join([stage] + ["-" if row[c] is None else f"{row[c]:X}" for c in COLUMNS[1:]]))
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
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({len(text.splitlines()) - 2} stages)")


if __name__ == "__main__":
    main()
