#!/usr/bin/env python3
"""Generate the Sonic CD stage catalogue: per-stage file offsets of the level tables.

Purpose: CdStageLoader reads terrain, collision, layouts, palettes, placements,
start position and player art for all 70 retail stage files through one table.
Each stage file carries its own copy of the level engine, so every table is
located by the instruction that consumes it in that copy. The instruction
sequences below are taken from the scddisasm routines cited beside them
(pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff); each must yield exactly one
operand per stage. The output holds offsets only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track).
Output: src/main/resources/soniccd/stage-catalogue.tsv (or --check against it).
Origin: lane K3b of the 2026-11-12 Sonic CD re-host.

    python3 tools/soniccd/stage_catalogue.py --media-root ~/Downloads/Sonic-CD/media [--check]
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
OUTPUT = ROOT / "src/main/resources/soniccd/stage-catalogue.tsv"
WORD_RAM = 0x200000

# Stage file names follow FID_R11A..FID_R83D (System.inc:12-99) in CdStagePayload order.
STAGES = [s for s in re.findall(r"^\s+(R\d\d[A-D])\(", (ROOT / "src/main/java/com/openggf/game/soniccd/CdStagePayload.java").read_text(), re.M)]

# (column, signature, citation). "AAAAAAAA" is the absolute-long operand being located.
ANCHORS = [
    ("levelDataIndex", "7000 45F9 AAAAAAAA 7000 1012",
     "LevelStart .NormalLoad: lea LevelDataIndex,a2 (Level/Main.asm:156-159)"),
    ("plcLists", "43F9 AAAAAAAA D040 3031 0000 43F1 0000",
     "LoadPLC: lea PLCLists,a1 (Level/Functions (General).asm, PLC loader)"),
    ("levelLayouts", "7000 D041 43F9 AAAAAAAA 3031 0000",
     "LoadLevelLayout .LoadPlane: lea LevelLayouts,a1 (Level/Palmtree Panic/Load Level Data.asm:75-79)"),
    ("colAngleMap", "45F9 AAAAAAAA 18B2 0000",
     "FindLevelFloor: lea LegacyCollisionAngleMap,a2 (Level/Collision Floor.asm:710-711)"),
    ("colHeightMap", "45F9 AAAAAAAA 1032 1000 4880",
     "FindLevelFloor: lea LegacyCollisionHeightMap,a2 (Level/Collision Floor.asm:730-732); first distinct"),
    ("colWidthMap", "45F9 AAAAAAAA 1032 1000 4880",
     "FindLevelWall: lea LegacyCollisionWidthMap,a2 (Level/Collision Floor.asm:919-921); second distinct"),
    ("paletteTable", "43F9 AAAAAAAA E748 D2C0 2459",
     "LoadPalette: lea PaletteTable,a1 (Level/Palette Load.asm:14-18)"),
    ("objectLayouts", "41F9 AAAAAAAA 2248 D0D0",
     "SpawnObjects_Init: lea ObjectLayouts,a0 (Level/Object Spawner.asm:45-47)"),
    ("camBounds", "41F9 AAAAAAAA 3018 31C0",
     "LevelSizeLoad: lea CamBounds,a0 (Level/Palmtree Panic/Scroll (Act 1 Present).asm:19-21)"),
    ("levelStartLoc", ("43F9 AAAAAAAA 7200 3219", "43F9 AAAAAAAA 4A79 ???????? 6A??"),
     "LevelSizeLoad_StartPos .DefaultStart: lea LevelStartLoc,a1 (Scroll (Act 1 Present).asm:77-80;"
     " with the Sonic 1 credits check, Scroll (Act 1 Past).asm:79-82)"),
    ("playerMappings", "217C AAAAAAAA 0004 317C 0780 0002",
     "ObjSonic_Init: move.l #MapSpr_Sonic,oMap(a0) (Level/_Objects/Sonic/Main.asm:146-147)"),
    ("playerAnimations", "43F9 AAAAAAAA 7000 1028 001C B028",
     "ObjSonic_Animate: lea Ani_Sonic,a1 (Level/_Objects/Sonic/Main.asm:2645-2649)"),
    ("playerDplc", "45F9 AAAAAAAA D040 D4F2 0000",
     "LoadSonicDynPLC: lea DPLC_Sonic,a2 (Level/_Objects/Sonic/Main.asm:3066-3068)"),
    ("objColSizes", "45F9 AAAAAAAA 45F2 00FE",
     "Player object collision .CheckWidth: lea ObjColSizes,a2; lea -2(a2,d0.w),a2"
     " (Level/Palmtree Panic/Player Object Collision.asm:41-45)"),
    ("playerArt", "EB8A 43F9 AAAAAAAA D3C2",
     "LoadSonicDynPLC .PieceLoop: lea Art_Sonic,a1 (Level/_Objects/Sonic/Main.asm:3087-3089)"),
]
# LoadLevelCollision: lsl.w #2,d0; move.l LevelColIndex(pc,d0.w),collisionPtr.w; rts,
# then eight identical dc.l LevelCollision (Level/Main.asm:490-507).
COLLISION_SIGNATURE = "E548 21FB 0006 ???? 4E75"
# Rebuilt stages set the pointer directly before UpdateGlobalAnims:
# move.l #StageCollision,stage_collision; rts (r4/main.asm:434-442).
DIRECT_COLLISION_SIGNATURE = "21FC AAAAAAAA ???? 4E75 5339"


def pattern(signature: str) -> re.Pattern:
    out = b""
    for token in signature.split():
        if token == "AAAAAAAA":
            out += b"(....)"
        elif "?" in token:
            out += b"." * (len(token) // 2)
        else:
            out += re.escape(bytes.fromhex(token))
    return re.compile(out, re.S)


def operands(data: bytes, signatures) -> list[int]:
    found = []
    if isinstance(signatures, str):
        signatures = (signatures,)
    matches = [m for signature in signatures for m in pattern(signature).finditer(data)]
    for match in sorted(matches, key=lambda m: m.start()):
        value = int.from_bytes(match.group(1), "big") & 0xFFFFFF
        if value not in found:
            found.append(value)
    return found


def to_offset(address: int, data: bytes, name: str) -> int:
    offset = address - WORD_RAM
    require(0 <= offset < len(data), f"{name} at {address:#x} is outside the stage file")
    return offset


def stage_row(stage: str, data: bytes) -> dict:
    row = {}
    for name, signature, _ in ANCHORS:
        hits = operands(data, signature)
        if name == "colHeightMap":
            require(len(hits) == 2, f"{stage} {name}: expected two collision maps, got {len(hits)}")
            hits = hits[:1]
        elif name == "colWidthMap":
            hits = hits[1:]
        require(len(hits) == 1, f"{stage} {name}: expected one operand, got {[hex(h) for h in hits]}")
        row[name] = to_offset(hits[0], data, name)
    collision = [m.end() for m in pattern(COLLISION_SIGNATURE).finditer(data)]
    if collision:
        require(len(collision) == 1, f"{stage} levelCollision: expected one LevelColIndex, got {len(collision)}")
        table = [int.from_bytes(data[collision[0] + 4 * i:collision[0] + 4 * i + 4], "big") & 0xFFFFFF
                 for i in range(8)]
        require(len(set(table)) == 1, f"{stage} LevelColIndex entries differ")
    else:
        table = operands(data, DIRECT_COLLISION_SIGNATURE)
        require(len(table) == 1, f"{stage} levelCollision: no LevelColIndex or direct pointer")
    row["levelCollision"] = to_offset(table[0], data, "levelCollision")
    require(row["colWidthMap"] - row["colHeightMap"] == 0x1000, f"{stage} collision maps are not adjacent")
    return row


COLUMNS = ["stage"] + [a[0] for a in ANCHORS] + ["levelCollision"]


def generate(media_root: Path) -> str:
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    tracks = parse_cue(cues[0].read_text())
    track = (cues[0].parent / tracks[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/stage_catalogue.py from the USA disc; file offsets (hex) only.",
             "\t".join(COLUMNS)]
    require(len(STAGES) == 70, f"Expected 70 stage payloads, got {len(STAGES)}")
    with RawIso(track) as disc:
        for stage in STAGES:
            row = stage_row(stage, disc.file(f"{stage}__.MMD;1"))
            lines.append("\t".join([stage] + [f"{row[c]:X}" for c in COLUMNS[1:]]))
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
