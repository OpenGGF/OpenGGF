#!/usr/bin/env python3
"""Generate the Sonic CD non-level program catalogue: file offsets of title, stage-select and IPX data.

Purpose: the title screen (TITLEM.MMD, TITLES.BIN), the stage-select secret (STSEL_.MMD) and the
IPX flow tables (IPX___.MMD) read their art, mappings, palettes and tables from the user's disc
through one table. Each blob is assembled from the pinned scddisasm source (incbin files, straight
dc data, or the StageSels/SubCmds tables with System.inc command numbers) and located as exactly
one match in the disc file. The output holds offsets and lengths only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track) and scddisasm at SOURCE_PIN.
Output: src/main/resources/soniccd/program-assets.tsv (or --check against it).
Origin: lane K0 (program host) of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/program_catalogue.py --media-root ~/Downloads/Sonic-CD/media \
        --source <scddisasm checkout> [--check]
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_inventory import RawIso, Source, assemble_dc, dc_region, literal_data, require, scalar_table, unique  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/program-assets.tsv"
TS = "Title Screen/"
OBJ = TS + "Objects/"
SEC = TS + "Secrets/"

# (disc file, asset name, kind, source path, label or None)
#   incbin  - the bytes of a binary include
#   dc      - a pure data include (sprite mappings)
#   scalar  - a straight dc table after a label inside a code file
ASSETS = [
    ("TITLEM.MMD;1", "palTitle", "incbin", TS + "Data/Palette.bin", None),
    ("TITLEM.MMD;1", "palInit", "incbin", TS + "Data/Palette (Initialization).bin", None),
    ("TITLEM.MMD;1", "mapWater", "incbin", TS + "Data/Water Mappings.bin", None),
    ("TITLEM.MMD;1", "mapMountains", "incbin", TS + "Data/Moutains Mappings.bin", None),
    ("TITLEM.MMD;1", "mapEmblem", "incbin", TS + "Data/Emblem Mappings.bin", None),
    ("TITLEM.MMD;1", "artWater", "incbin", TS + "Data/Water Art.nem", None),
    ("TITLEM.MMD;1", "artMountains", "incbin", TS + "Data/Moutains Art.nem", None),
    ("TITLEM.MMD;1", "artEmblem", "incbin", TS + "Data/Emblem Art.nem", None),
    ("TITLEM.MMD;1", "artSolidColor", "incbin", TS + "Data/Solid Color Art.nem", None),
    ("TITLEM.MMD;1", "artBanner", "incbin", OBJ + "Banner/Data/Art.nem", None),
    ("TITLEM.MMD;1", "artPlanet", "incbin", OBJ + "Planet/Data/Art.nem", None),
    ("TITLEM.MMD;1", "artSonic", "incbin", OBJ + "Sonic/Data/Art.nem", None),
    ("TITLEM.MMD;1", "artPressStart", "incbin", OBJ + "Menu/Data/Art (Text, Press Start).nem", None),
    ("TITLEM.MMD;1", "artNewGame", "incbin", OBJ + "Menu/Data/Art (Text, New Game).nem", None),
    ("TITLEM.MMD;1", "artContinue", "incbin", OBJ + "Menu/Data/Art (Text, Continue).nem", None),
    ("TITLEM.MMD;1", "artTimeAttack", "incbin", OBJ + "Menu/Data/Art (Text, Time Attack).nem", None),
    ("TITLEM.MMD;1", "artRamData", "incbin", OBJ + "Menu/Data/Art (Text, RAM Data).nem", None),
    ("TITLEM.MMD;1", "artDaGarden", "incbin", OBJ + "Menu/Data/Art (Text, D.A. Garden).nem", None),
    ("TITLEM.MMD;1", "artVisualMode", "incbin", OBJ + "Menu/Data/Art (Text, Visual Mode).nem", None),
    ("TITLEM.MMD;1", "artMenuArrow", "incbin", OBJ + "Menu/Data/Art (Arrow).nem", None),
    ("TITLEM.MMD;1", "artCopyright", "incbin", OBJ + "Copyright/Data/Art (Copyright, USA).nem", None),
    ("TITLEM.MMD;1", "artTm", "incbin", OBJ + "Copyright/Data/Art (TM, USA).nem", None),
    ("TITLEM.MMD;1", "mapSprSonic", "dc", OBJ + "Sonic/Data/Mappings.asm", None),
    ("TITLEM.MMD;1", "mapSprBanner", "dc", OBJ + "Banner/Data/Mappings.asm", None),
    ("TITLEM.MMD;1", "mapSprPlanet", "dc", OBJ + "Planet/Data/Mappings.asm", None),
    ("TITLEM.MMD;1", "mapSprMenu", "dc", OBJ + "Menu/Data/Mappings (Menu).asm", "MapSpr_Menu"),
    ("TITLEM.MMD;1", "mapSprMenuArrow", "dc", OBJ + "Menu/Data/Mappings (Arrow).asm", None),
    ("TITLEM.MMD;1", "mapSprCopyright", "dc", OBJ + "Copyright/Data/Mappings (Copyright, USA).asm", None),
    ("TITLEM.MMD;1", "mapSprTm", "dc", OBJ + "Copyright/Data/Mappings (TM).asm", None),
    ("TITLEM.MMD;1", "waterPalCycle", "scalar", TS + "Main.asm", ".WaterPalCycle"),
    ("TITLEM.MMD;1", "planetOffsets", "scalar", OBJ + "Planet/Main.asm", ".Offsets"),
    ("TITLEM.MMD;1", "armFrames", "scalar", OBJ + "Sonic/Main.asm", ".Frames"),
    ("TITLES.BIN;1", "cloudStamps", "incbin", TS + "Data/Cloud Stamps.kos", None),
    ("TITLES.BIN;1", "cloudStampMap", "incbin", TS + "Data/Cloud Stamp Map.kos", None),
    ("TITLES.BIN;1", "sineTable", "scalar", TS + "Sub.asm", "SineTable"),
    ("STSEL_.MMD;1", "palSelect", "incbin", SEC + "Data/Palette.bin", None),
    ("STSEL_.MMD;1", "artSelect", "incbin", SEC + "Data/Art.nem", None),
    ("STSEL_.MMD;1", "mapSelectBg", "incbin", SEC + "Data/Background Mappings (H40).bin", None),
    ("STSEL_.MMD;1", "tilesUpper", "scalar", SEC + "Stage Select.asm", ".TilesUpper"),
    ("STSEL_.MMD;1", "tilesLower", "scalar", SEC + "Stage Select.asm", ".TilesLower"),
]

STAGE_SELECT_TEXT = 74  # (Text_SelectionsEnd-Text_Selections)/$E (Stage Select.asm MainLoop)


def binary(source: Source, path: str) -> bytes:
    return source.read(path)


def dc_include(source: Source, path: str, label: str | None) -> bytes:
    """A mapping include; its offsets may name the including label (Menu/Main.asm MapSpr_Menu)."""
    text = source.text(path)
    data, _, _, _ = literal_data((label + ":\n" if label else "") + text)
    return data


def commands(source: Source) -> dict[str, int]:
    """SCMD_* numbering: rsset 1 then one rs.b per command (System.inc:144-360)."""
    names, value, started = {}, 0, False
    for raw in source.text("_Include/System.inc").splitlines():
        line = raw.split(";", 1)[0].strip()
        if not started:
            if re.fullmatch(r"rsset\s+1", line):
                started, value = True, 1
            continue
        match = re.fullmatch(r"(SCMD_\w+)?\s*rs\.b\s+1", line)
        if match:
            if match[1]:
                names[match[1]] = value
            value += 1  # an unnamed rs.b is the invalid command slot (System.inc, "Invalid")
        elif line.startswith("rs") or line.startswith("SCMD"):
            break
    require(names.get("SCMD_R11A") == 1, "SCMD numbering did not start at SCMD_R11A = 1")
    return names


TIME_ZONES = {"TIME_PAST": 0, "TIME_PRESENT": 1, "TIME_FUTURE": 2}  # rsreset (Main CPU Variables.inc:12-15)


def ipx_block(source: Source, label: str, end: str, names: dict[str, int]) -> bytes:
    """A straight dc block of IPX.asm from a label up to (not including) another label."""
    text = source.text("CD Initial Program/IPX.asm")
    body = text.split("\n" + label + ":", 1)[1].split("\n" + end, 1)[0]
    _, entries, _ = dc_region(re.sub(r"^\s*;.*$", "", body, flags=re.M))
    return assemble_dc(entries, names)


def stage_select_text(data: bytes, mmd_origin: int) -> int:
    """Text_Selections: 74 lists of (dc.w 0, dc.l string, dc.l top/bottom VDP command) at plane (14, 13).

    TEXTPTR (Secrets/_Macros.inc:31-44) with H40 rows of $80: $C000+14*2+13*$80 = $C69C and $C71C.
    """
    entry = re.compile(rb"\x00\x00(....)\x46\x9c\x00\x03\x47\x1c\x00\x03", re.S)
    runs = []
    for match in entry.finditer(data):
        start = match.start()
        count = 0
        while entry.fullmatch(data, start + 14 * count, start + 14 * count + 14):
            count += 1
        runs.append((start, count))
    tables = sorted({start for start, count in runs if count == STAGE_SELECT_TEXT})
    require(len(tables) == 1, f"Text_Selections: expected one {STAGE_SELECT_TEXT}-entry table, got {tables}")
    first = int.from_bytes(data[tables[0] + 2:tables[0] + 6], "big")
    require(0 <= first - mmd_origin < len(data), "Text_Selections string pointer outside STSEL_.MMD")
    return tables[0]


def generate(media_root: Path, source_root: Path) -> str:
    source = Source(source_root)
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    names = commands(source) | TIME_ZONES
    lines = ["# Generated by tools/soniccd/program_catalogue.py from the USA disc and scddisasm "
             "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff; file offsets and lengths (hex) only.",
             "file\tname\toffset\tlength\tsource"]
    with RawIso(track) as disc:
        files: dict[str, bytes] = {}

        def file(name: str) -> bytes:
            if name not in files:
                files[name] = disc.file(name)
            return files[name]

        for disc_file, name, kind, path, label in ASSETS:
            if kind == "incbin":
                blob = binary(source, path)
            elif kind == "dc":
                blob = dc_include(source, path, label)
            else:
                blob = scalar_table(source, path, label)
            offset = unique(file(disc_file), blob, name)
            cite = path if label is None or kind == "dc" else f"{path} {label}"
            lines.append(f"{disc_file}\t{name}\t{offset:X}\t{len(blob):X}\t{cite}")

        ipx = file("IPX___.MMD;1")
        for name, label, end in (("stageSels", "StageSels", "; ----"),
                                 ("levelSubCmds", "R11SubCmds", "; ----"),
                                 ("timeAttackSelections", ".Selections", "TimeAttack_SS")):
            blob = ipx_block(source, label, end, names)
            offset = unique(ipx, blob, name)
            lines.append(f"IPX___.MMD;1\t{name}\t{offset:X}\t{len(blob):X}\tCD Initial Program/IPX.asm {label}")

        stsel = file("STSEL_.MMD;1")
        origin = int.from_bytes(stsel[2:6], "big") & 0xFFFFFF
        table = stage_select_text(stsel, origin)
        lines.append(f"STSEL_.MMD;1\ttextSelections\t{table:X}\t{14 * STAGE_SELECT_TEXT:X}\t"
                     f"{SEC}Stage Select.asm Text_Selections")
        header = bytes([0x1D, 0x1E, 0x0B, 0x11, 0x0F, 0x00, 0x1D, 0x0F, 0x16, 0x0F, 0x0D, 0x1E, 0xFF])
        offset = unique(stsel, header, "TextDat_StageSelect")
        lines.append(f"STSEL_.MMD;1\ttextStageSelect\t{offset:X}\t{len(header):X}\t"
                     f"{SEC}Stage Select.asm TextDat_StageSelect")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True, help="scddisasm checkout at the pin")
    parser.add_argument("--check", action="store_true", help="fail if the committed resource differs")
    args = parser.parse_args()
    text = generate(args.media_root.expanduser(), args.source.expanduser())
    if args.check:
        require(OUTPUT.read_text() == text, f"{OUTPUT} is stale; regenerate it")
        print(f"OK: {OUTPUT.relative_to(ROOT)} matches the disc")
        return
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({len(text.splitlines()) - 2} assets)")


if __name__ == "__main__":
    main()
