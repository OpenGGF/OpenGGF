"""Generate the Sonic CD special-stage catalogue: file offsets of its data in SPSS__.BIN / SPMM__.MMD.

Purpose: the special-stage provider (game/soniccd/special) reads every runtime byte of the
special stage from the user's disc through one offset table. Data the disassembly includes
with incbin is located by its unique occurrence in the retail program file; tables the
disassembly writes as dc directives are assembled from the pinned source and located the
same way. The output holds offsets and lengths only, never disc bytes, and the citation of
the label that owns each block.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track) and a docs/scddisasm checkout
at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff (research reference; never a runtime input).
Output: src/main/resources/soniccd/special-stage-catalogue.tsv (or --check against it).
Origin: lane SS1 of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/special_stage_catalogue.py --media-root ~/Downloads/Sonic-CD/media \
        --scddisasm <checkout> [--check]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_inventory import RawIso, Source, dc_region, assemble_dc, occurrences, require  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/special-stage-catalogue.tsv"
SUB = "SPSS__.BIN;1"
MAIN = "SPMM__.MMD;1"
SUB_ASM = "Special Stage/Sub.asm"
MAIN_ASM = "Special Stage/Main.asm"
SONIC_ASM = "Special Stage/Objects/Sonic/Main.asm"
STAGE_DATA_ASM = "Special Stage/Stage Data.asm"

# Stamp type names (Sub.asm STAMP_PATH..STAMP_OIL, lines 1501-1510).
STAMP_NAMES = {"STAMP_PATH": 0, "STAMP_BUMPER": 1, "STAMP_FAN": 2, "STAMP_WATER": 3, "STAMP_ROUGH": 4,
               "STAMP_SPRING": 5, "STAMP_HAZARD": 6, "STAMP_BOOSTER_BIG": 7, "STAMP_BOOSTER_SMALL": 8,
               "STAMP_OIL": 9}


def incbin_path(text, label):
    """The file a label's incbin directive includes (the first incbin after the label)."""
    match = re.search(r"^" + re.escape(label) + r":\s*\n(?:.*\n){0,3}?\s*incbin\s+\"([^\"]+)\"", text, re.M)
    require(match is not None, f"No incbin after {label}")
    return match[1]


def dc_table(text, label, names=None):
    """Assemble the straight dc block following a label (global or local) up to the next label."""
    start = re.search(r"^" + re.escape(label) + r":[ \t]*(;.*)?$", text, re.M)
    require(start is not None, f"Missing dc label {label}")
    lines = []
    for raw in text[start.end():].splitlines():
        line = raw.split(";", 1)[0].strip()
        if not line:
            continue
        if not line.startswith("dc.") and line != "even":
            break
        lines.append(line)
    _, entries, _ = dc_region("\n".join(lines))
    require(bool(entries), f"Empty dc table {label}")
    return assemble_dc(entries, names or {})


def dc_local(text, parent, label, names=None):
    """A routine-local dc table (".Cycle") found after its owning global label."""
    start = re.search(r"^" + re.escape(parent) + r":", text, re.M)
    require(start is not None, f"Missing {parent}")
    return dc_table(text[start.end():], label, names)


def local_line(text, parent, label):
    start = text.index("\n" + parent + ":")
    hit = re.search(r"^" + re.escape(label) + r":", text[start:], re.M)
    require(hit is not None, f"Missing {parent}{label}")
    return text[:start + hit.start()].count("\n") + 1


def line_of(text, label):
    hits = [i + 1 for i, line in enumerate(text.splitlines()) if re.match(re.escape(label) + r":", line)]
    require(len(hits) == 1, f"Ambiguous label {label}")
    return hits[0]


# Mapping tables of the 3D sprite section are pointer graphs, so they are located through the
# instruction that installs them in oMap: move.l #MapSpr_*,oMap(a0) = 217C <address> 0006,
# behind the move.w #tile,oTile(a0) (317C <tile> 0004) or test that precedes it. The sub-CPU
# program is loaded at PRG RAM $10000 (Sub.asm:198), so file offset = address - $10000.
SUB_ORIGIN = 0x10000
MAP_ANCHORS = [
    ("MapSpr_Sonic", r"317c85e00004217c(00[0-9a-f]{6})0006", "Special Stage/Objects/Sonic/Main.asm", "ObjSonic_Init"),
    ("MapSpr_Splash", r"317c85820004217c(00[0-9a-f]{6})0006", "Special Stage/Objects/Splash/Main.asm", "ObjSplash_Init"),
    ("MapSpr_UFORings", r"317ce44000046100[0-9a-f]{4}217c(00[0-9a-f]{6})0006", "Special Stage/Objects/UFO/Main.asm", "ObjUFO_Init"),
    ("MapSpr_UFOShoes", r"0c28000000626708217c(00[0-9a-f]{6})0006", "Special Stage/Objects/UFO/Main.asm", "ObjUFO_Init"),
    ("MapSpr_Shadow", r"317ce6dc0004217c(00[0-9a-f]{6})0006", "Special Stage/Objects/Shadow/Main.asm", "ObjSonicShadow_Init"),
    ("MapSpr_TitleCardText", r"317c85160004217c(00[0-9a-f]{6})0006317c01c80028", "Special Stage/Objects/Title Card/Main.asm", "ObjTitleCardText_Init"),
    ("MapSpr_TitleCardBar", r"317c85160004217c(00[0-9a-f]{6})0006317c00f40028", "Special Stage/Objects/Title Card/Main.asm", "ObjTitleCardBar_Init"),
    ("MapSpr_TimeStone", r"317ce4240004217c(00[0-9a-f]{6})0006", "Special Stage/Objects/Time Stone/Main.asm", "ObjTimeStone_Init"),
    ("MapSpr_Item", r"317c878f0004217c(00[0-9a-f]{6})0006", "Special Stage/Objects/Item/Main.asm", "ObjItem_Init"),
    ("MapSpr_Explosion", r"317c87ae0004217c(00[0-9a-f]{6})00067000", "Special Stage/Objects/Explosion/Main.asm", "ObjExplosion_Init"),
    ("MapSpr_Dust", r"317c87ae0004217c(00[0-9a-f]{6})0006317c00f00028", "Special Stage/Objects/Dust/Main.asm", "ObjDust_Init"),
    ("SonicArt", r"2a2a2a2a(?!)", "Special Stage/Sub.asm", "SonicArt"),
]


def short(asm_path):
    """Citation file name: the last component, or the object directory for an object's Main.asm."""
    parts = asm_path.split("/")
    return "/".join(parts[-2:]) if parts[-1] == "Main.asm" and parts[-2] != "Special Stage" else parts[-1]


def locate(data, needle, name, program):
    hits = occurrences(data, needle)
    require(len(hits) == 1, f"{name}: expected one match in {program}, got {len(hits)}")
    return hits[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--media-root", required=True, type=Path)
    parser.add_argument("--scddisasm", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    cue = args.media_root / "USA" / "Sonic CD (US).cue"
    tracks = parse_cue(cue.read_text())
    source = Source(args.scddisasm)
    with RawIso(cue.parent / tracks[0]["file"]) as iso:
        files = {}
        for name in (SUB, MAIN):
            entry = iso.files[name]
            files[name] = iso.read(entry["lba"] * 2048, entry["size"])

    sub_text, main_text = source.text(SUB_ASM), source.text(MAIN_ASM)
    sonic_text, data_text = source.text(SONIC_ASM), source.text(STAGE_DATA_ASM)
    rows = []

    def add_bin(name, program, path, asm_path, asm_text, label):
        needle = source.read(path)
        offset = locate(files[program], needle, name, program)
        rows.append((name, program.split(";")[0], offset, len(needle),
                     f"{label} ({short(asm_path)}:{line_of(asm_text, label)})"))

    def add_dc(name, program, asm_path, asm_text, label, names=None):
        needle = dc_table(asm_text, label, names)
        offset = locate(files[program], needle, name, program)
        rows.append((name, program.split(";")[0], offset, len(needle),
                     f"{label} ({short(asm_path)}:{line_of(asm_text, label)})"))

    def add_chain(items, program, asm_path, asm_text, max_gap):
        """Blocks laid out consecutively in source order (identical stages share bytes, so a
        lone content match is ambiguous): accept the unique start whose whole chain fits."""
        needles = [path if isinstance(path, bytes) else source.read(path) for _, path in items]
        data = files[program]
        starts = []
        for first in occurrences(data, needles[0]):
            offsets, cursor, ok = [first], first + len(needles[0]), True
            for needle in needles[1:]:
                hit = data.find(needle, cursor)
                if hit < 0 or hit - cursor >= max_gap:
                    ok = False
                    break
                offsets.append(hit)
                cursor = hit + len(needle)
            if ok:
                starts.append(offsets)
        require(len(starts) == 1, f"{items[0][0]}: expected one consecutive chain, got {len(starts)}")
        for (label, _), offset, needle in zip(items, starts[0], needles):
            rows.append((label, program.split(";")[0], offset, len(needle),
                         f"{label} ({short(asm_path)}:{line_of(asm_text, label)})"))

    stamp_labels = [l for s in range(1, 9) for l in (["Stamps_SS3_1", "Stamps_SS3_2"] if s == 3 else [f"Stamps_SS{s}"])]
    add_chain([(l, incbin_path(sub_text, l)) for l in stamp_labels]
              + [(f"StampMap_SS{s}", incbin_path(sub_text, f"StampMap_SS{s}")) for s in range(1, 9)],
              SUB, SUB_ASM, sub_text, 0x10)
    add_chain([(l, incbin_path(sub_text, l)) for s in range(1, 9) for l in (f"HazardAnim_SS{s}", f"FanAnim_SS{s}")],
              SUB, SUB_ASM, sub_text, 2)
    add_chain([(f"StampTypes_SS{s}", dc_table(sub_text, f"StampTypes_SS{s}", STAMP_NAMES)) for s in range(1, 9)],
              SUB, SUB_ASM, sub_text, 2)
    for stage in range(1, 9):
        add_chain([(l, incbin_path(data_text, l)) for l in (f"Pal_SS{stage}", f"Art_SS{stage}BG")],
                  SUB, STAGE_DATA_ASM, data_text, 2)
    sub_hex = files[SUB].hex()
    for name, pattern, asm_path, label in MAP_ANCHORS:
        if name == "SonicArt":
            continue
        hits = [m for m in re.finditer(pattern, sub_hex) if m.start() % 2 == 0]
        require(len(hits) >= 1 and len({h[1] for h in hits}) == 1, f"{name}: expected one installed address, got {len(hits)}")
        address = int(hits[0][1], 16)
        asm_text = source.text(asm_path)
        rows.append((name, SUB.split(";")[0], address - SUB_ORIGIN, 0,
                     f"{label} ({short(asm_path)}:{line_of(asm_text, label)}) installs {name}"))
    # UFO paths (UFO/Main.asm:512-958): one block per UFO, consecutive in source order. The
    # UFOPATHNODE macro (UFO/Main.asm:506-508) is five dc.w words.
    ufo_asm = "Special Stage/Objects/UFO/Main.asm"
    ufo_text = re.sub(r"UFOPATHNODE\s+", "dc.w\t", source.text(ufo_asm))
    ufo_labels = []
    add_dc("TimeUFOPath", SUB, ufo_asm, ufo_text, "TimeUFOPath")
    for stage in range(1, 9):
        count = re.search(rf"^UFOPaths_SS{stage}:\s*\n\s*dc\.w\s+(\d+)", ufo_text, re.M)
        require(count is not None, f"No UFO count for stage {stage}")
        ufo_labels += [f"UFOPath_SS{stage}_{i}" for i in range(1, int(count[1]) + 1)]
        rows.append((f"UFOCount_SS{stage}", "-", int(count[1]), 0,
                     f"UFOPaths_SS{stage} ({short(ufo_asm)}:{line_of(ufo_text, f'UFOPaths_SS{stage}')})"))
    add_chain([(l, dc_table(ufo_text, l)) for l in ufo_labels], SUB, ufo_asm, ufo_text, 2)
    # Main-program art (Start, Main.asm:109-132; Results, Main.asm:339-346; LoadSS6Data, :758-760).
    for label in ("Art_TimeStone", "Art_UFO", "Art_TitleCard", "Art_Splash", "Art_Shadow", "Art_Items",
                  "Art_Explosion", "Art_HUD", "Art_SS6BGWater", "Art_Results", "Art_ExtraPlayersText"):
        add_bin(label, MAIN, incbin_path(main_text, label), MAIN_ASM, main_text, label)
    # DrawTilemaps .Tilemaps (scddisasm/src/Special Stage/Main.asm:3707-4152): located through the routine's own code
    # (moveq #3,d7; moveq #0,d6; move.b d5,d6; beq; mulu.w #$C,d6; movea.l .Tilemaps-$C(pc,d6.w),a0),
    # 12-byte entries up to DrawTilemap's lea VDPCTRL,a2.
    main_hex = files[MAIN].hex()
    hit = [m for m in re.finditer(r"7e037c001c0567[0-9a-f]{2}ccfc000c207b60([0-9a-f]{2})", main_hex) if m.start() % 2 == 0]
    require(len(hit) == 1, "DrawTilemaps code")
    table = hit[0].start() // 2 + 14 + int(hit[0][1], 16) + 0xC  # (d8,PC): PC = the extension word
    end = files[MAIN].find(bytes.fromhex("45f900c00004"), table)
    require(end > table and (end - table) % 12 == 0, "Tilemaps table end")
    rows.append(("Tilemaps", MAIN.split(";")[0], table, end - table,
                 f".Tilemaps (Main.asm:{local_line(main_text, 'DrawTilemaps', '.Tilemaps')})"))
    # Special stage data blocks: the sub-CPU table at SpecStageData (_Global Variables.inc:7).
    rows.append(("SpecStageData", SUB.split(";")[0], 0x18000 - SUB_ORIGIN, 8 * 6, "SpecStageData (_Global Variables.inc:7)"))
    for parent, labels in (("PalCycle_SS1", [".Cycle"]), ("PalCycle_SS2", [".Cycle"]), ("PalCycle_SS3", [".Cycle1", ".Cycle2"]),
                           ("PalCycle_SS4", [".Cycle1", ".Cycle2"]), ("PalCycle_SS5", [".Cycle1", ".Cycle2", ".Cycle3", ".Cycle4"]),
                           ("PalCycle_SS6", [".Cycle1", ".Cycle2"]), ("PalCycle_SS7", [".Cycle"])):
        for label in labels:
            needle = dc_local(main_text, parent, label)
            offset = locate(files[MAIN], needle, parent + label, MAIN)
            rows.append((parent + label, MAIN.split(";")[0], offset, len(needle),
                         f"{parent} {label} (Main.asm:{local_line(main_text, parent, label)})"))
    for label in ("SS5BGScrollSpeeds", "SS6BGWave", "TimeStonePalettes", "NumTilesUpper", "NumTilesLower"):
        add_dc(label, MAIN, MAIN_ASM, main_text, label)
    # ObjUFO_Draw .Frames (scddisasm/src/Special Stage/Objects/UFO/Main.asm:412-423): animation by distance / 16.
    frames = dc_local(source.text("Special Stage/Objects/UFO/Main.asm"), "ObjUFO_Draw", ".Frames")
    rows.append(("UFODistanceFrames", SUB.split(";")[0], locate(files[SUB], frames, "UFO .Frames", SUB), len(frames),
                 f"ObjUFO_Draw .Frames (UFO/Main.asm:{local_line(source.text('Special Stage/Objects/UFO/Main.asm'), 'ObjUFO_Draw', '.Frames')})"))
    # SonicArt (scddisasm/src/Special Stage/Sub.asm:2694-2696) heads the 3D sprite section at PRG RAM $30000 (Sub.asm:2691).
    rows.append(("SonicArt", SUB.split(";")[0], 0x30000 - SUB_ORIGIN, 0, f"SonicArt (Sub.asm:{line_of(sub_text, 'SonicArt')})"))
    add_dc("SineTable", SUB, SUB_ASM, sub_text, "SineTable")
    add_dc("TrajectoryTable", SUB, SUB_ASM, sub_text, "TrajectoryTable")
    add_dc("DistanceTable", SUB, SUB_ASM, sub_text, "DistanceTable")
    add_dc("SonicStartPos", SUB, SONIC_ASM, sonic_text, ".StartPos")
    add_dc("SonicWalkAnims", SUB, SONIC_ASM, sonic_text, ".WalkAnims")
    for label in ("Pal_Stage", "Pal_Results"):
        add_bin(label, MAIN, incbin_path(main_text, label), MAIN_ASM, main_text, label)
    add_bin("Stage1Demo", MAIN, "Special Stage/Data/Stage 1/Demo (NTSC).kos", MAIN_ASM, main_text, "Stage1Demo")
    add_bin("Stage6Demo", MAIN, "Special Stage/Data/Stage 6/Demo (NTSC).kos", MAIN_ASM, main_text, "Stage6Demo")

    # Stamp animation entry counts, InitStampAnim .Animations (scddisasm/src/Special Stage/Sub.asm:1195-1234): dc.w <count>-1.
    block = sub_text.split("\n.Animations:", 1)[1].split("; ----", 1)[0]
    counts = [int(m[1:], 16) if m.startswith("$") else int(m) for m in re.findall(r"dc\.w\s+(\$?[0-9A-Fa-f]+)-1", block)]
    require(len(counts) == 16, "Expected hazard and fan counts for eight stages")
    anim_line = sub_text[:sub_text.index("\n.Animations:")].count("\n") + 2

    out = ["# Generated by tools/soniccd/special_stage_catalogue.py from the user's USA disc and docs/scddisasm "
           + "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Offsets into the named retail file; no disc bytes.",
           "name\tfile\toffset\tlength\tcitation"]
    for name, program, offset, length, cite in rows:
        out.append(f"{name}\t{program}\t{offset:#07x}\t{length:#06x}\t{cite}")
    for stage in range(8):
        out.append(f"HazardAnimCount_SS{stage + 1}\t-\t{counts[2 * stage]:#07x}\t0x0000\t"
                   f".Animations (Sub.asm:{anim_line})")
        out.append(f"FanAnimCount_SS{stage + 1}\t-\t{counts[2 * stage + 1]:#07x}\t0x0000\t"
                   f".Animations (Sub.asm:{anim_line})")
    text = "\n".join(out) + "\n"
    if args.check:
        require(OUTPUT.read_text() == text, f"{OUTPUT} is stale")
        print("special-stage catalogue matches")
    else:
        OUTPUT.write_text(text)
        print(f"wrote {len(rows)} blocks to {OUTPUT}")


if __name__ == "__main__":
    main()
