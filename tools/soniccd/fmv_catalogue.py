#!/usr/bin/env python3
"""Generate the Sonic CD video, ending and secret-program catalogue: file offsets of their data blobs.

Purpose: the opening, ending and pencil-test video programs (OPEN_M.MMD, OPEN_S.BIN, ENDING.MMD, PTEST.MMD)
read palettes, border art and maps, PCM register values, the credits screen, the credits event stream and the
end-text art from the user's disc through one table. Blobs with a binary include in the pinned scddisasm are
located as exactly one match. ENDING.MMD's listing is a raw dump whose data are reached through PC-relative
operands; for those the generator decodes the operand of the named instruction in the disc file (opcode
checked) and records the target's offset. The output holds offsets and lengths only, never disc bytes.

Inputs: the user's USA disc (CUE + split MODE1/2352 data track) and scddisasm at SOURCE_PIN.
Output: src/main/resources/soniccd/fmv-assets.tsv (or --check against it).
Origin: lane END (endings and remaining programs) of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/fmv_catalogue.py --media-root ~/Downloads/Sonic-CD/media \
        --source <scddisasm checkout> [--check]
"""
from __future__ import annotations

import argparse
import re
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from retail_profiles import parse_cue  # noqa: E402
from stage_inventory import RawIso, Source, require, scalar_table, unique  # noqa: E402
from program_catalogue import commands  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/soniccd/fmv-assets.tsv"
DRIVERS = ROOT / "src/main/resources/soniccd/soundtest-pcm-drivers.tsv"
FMV = "FMV/"
ENDING = FMV + "Main (Ending).asm"

# (disc file, asset name, kind, source path, label)
INCLUDES = [
    ("OPEN_M.MMD;1", "palOpening", "incbin", FMV + "Data/Palette (Opening).bin", None),
    ("OPEN_M.MMD;1", "artBorderOpening", "incbin", FMV + "Data/Art (Border, Opening).bin", None),
    ("OPEN_M.MMD;1", "mapBorderOpening", "incbin", FMV + "Data/Border Mappings (Opening).bin", None),
    # InitPCMRegs reads 14 bytes from .RegValues: PCM1's seven, then PCM2's seven past the table (Sub (Opening).asm:185-215).
    ("OPEN_S.BIN;1", "pcmRegValues", "scalar", FMV + "Sub (Opening).asm", ".RegValues"),
    ("GOODEND.BIN;1", "pcmRegValuesBadEnding", "scalar", FMV + "Sub (Ending).asm", ".RegValues"),
    ("BADEND.BIN;1", "pcmRegValuesGoodEnding", "scalar", FMV + "Sub (Ending).asm", ".RegValues"),
]

SEC = "Title Screen/Secrets/"
# The sound test (SOSEL_.MMD) and the five hidden pictures (Sound Test Image.asm built with EASTEREGG 0-4).
INCLUDES += [
    ("SOSEL_.MMD;1", "palSoundTest", "incbin", SEC + "Data/Palette.bin", None),
    ("SOSEL_.MMD;1", "artSoundTest", "incbin", SEC + "Data/Art.nem", None),
    ("SOSEL_.MMD;1", "mapSoundTestBg", "incbin", SEC + "Data/Background Mappings (H40).bin", None),
    ("SOSEL_.MMD;1", "soundTestTilesUpper", "scalar", SEC + "Sound Test.asm", ".TilesUpper"),
    ("SOSEL_.MMD;1", "soundTestTilesLower", "scalar", SEC + "Sound Test.asm", ".TilesLower"),
    ("SOSEL_.MMD;1", "soundTestDigitsUpper", "scalar", SEC + "Sound Test.asm", "NumTilesUpper"),
    ("SOSEL_.MMD;1", "soundTestDigitsLower", "scalar", SEC + "Sound Test.asm", "NumTilesLower"),
    ("SOSEL_.MMD;1", "soundTestCodes", "scalar", SEC + "Sound Test.asm", ".Codes"),
]
for _file, _name, _stem in (("NISI.MMD;1", "FunIsInfinite", "Fun Is Infinite"), ("DUMMY0.MMD;1", "McSonic", "M.C. Sonic"),
                            ("DUMMY1.MMD;1", "Tails", "Tails"), ("DUMMY2.MMD;1", "Batman", "Batman"),
                            ("DUMMY3.MMD;1", "CuteSonic", "Cute Sonic")):
    INCLUDES += [
        (_file, "pal" + _name, "incbin", SEC + f"Data/{_stem} Palette.bin", None),
        (_file, "art" + _name, "incbin", SEC + f"Data/{_stem} Art.nem", None),
        (_file, "map" + _name, "incbin", SEC + f"Data/{_stem} Mappings.bin", None),
    ]
# Best staff times (DUMMY4.MMD, Best Staff Times.asm): H32 data and its two TEXTSTART lists.
INCLUDES += [
    ("DUMMY4.MMD;1", "palBestTimes", "incbin", SEC + "Data/Palette.bin", None),
    ("DUMMY4.MMD;1", "artBestTimes", "incbin", SEC + "Data/Art.nem", None),
    ("DUMMY4.MMD;1", "mapBestTimesBg", "incbin", SEC + "Data/Background Mappings (H32).bin", None),
    ("DUMMY4.MMD;1", "bestTimesTilesUpper", "scalar", SEC + "Best Staff Times.asm", ".TilesUpper"),
    ("DUMMY4.MMD;1", "bestTimesTilesLower", "scalar", SEC + "Best Staff Times.asm", ".TilesLower"),
]


def text_list(data: bytes, x: int, y: int, count: int, stride: int) -> int:
    """A TEXTSTART list (Secrets/_Macros.inc:12-46): dc.w count-1, then (string, top, bottom) per entry; located by
    its first entry's plane commands at stride {@code stride} (H32 $40, H40 $80)."""
    top = 0x40000003 | (((0xC000 + 2 * x + y * stride) & 0x3FFF) << 16)
    bottom = 0x40000003 | (((0xC000 + 2 * x + (y + 1) * stride) & 0x3FFF) << 16)
    hits = [i - 6 for i in range(6, len(data) - 8) if data[i:i + 8] == struct.pack(">II", top, bottom)
            and struct.unpack(">H", data[i - 6:i - 4])[0] == count - 1]
    require(len(hits) == 1, f"TEXTSTART list at {x},{y}")
    return hits[0]


# The sound test's three .IDs tables (FM, PCM, CD audio, in source order) and its strings by TEXTPTR position.
SOUND_TEST_IDS = ("soundTestFmIds", "soundTestPcmIds", "soundTestCddaIds")
SOUND_TEST_TEXT = (("textSoundTest", 15, 7), ("textFmNo", 5, 11), ("textPcmNo", 15, 11), ("textCddaNo", 26, 11),
                   ("textSpecStg8", 4, 18))


def id_tables(source: Source) -> list[bytes]:
    """The dc.b runs after each '.IDs:' in Sound Test.asm, with System.inc/Sound.inc names resolved by the disc match."""
    text = source.text(SEC + "Sound Test.asm")
    out = []
    for part in text.split(".IDs:")[1:]:
        names = []
        for raw in part.splitlines()[1:]:
            line = raw.split(";", 1)[0].strip()
            if not line:
                continue
            if not line.startswith("dc.b"):
                break
            names.append(line[4:].strip())
        out.append(names)
    return out


def pcm_drivers(source: Source) -> str:
    """Sound-test PCM commands and the driver their handler loads first ({@code SPCmd_Test*}: lea File_RnPCM(pc),a0;
    bsr.w LoadPCMDriver, CD System Program/SPX.asm), keyed by the generated sub-CPU command table's handler column."""
    spx = source.text("CD System Program/SPX.asm")
    files = dict(re.findall(r"^(File_\w+):\s*\n\s*dc\.b\s+\"([^\"]+)\"", spx, flags=re.M))
    table = (ROOT / "src/main/resources/soniccd/subcpu-audio-commands.tsv").read_text().splitlines()
    rows = ["# Generated by tools/soniccd/fmv_catalogue.py: sound-test sub-CPU command (hex), PCM driver its handler loads.",
            "command\tdriver\thandler"]
    for line in table:
        if line.startswith("#") or not line.strip():
            continue
        command, _name, handler, action = line.split("\t")[:4]
        if action != "pcm_queue" or not handler.startswith("SPCmd_Test"):
            continue
        body = spx.split("\n" + handler + ":", 1)[1].split("SPCmdFinish", 1)[0]
        m = re.search(r"lea\s+(File_\w+)\(pc\),a0\s*(?:;[^\n]*)?\n\s*bsr\.w\s+LoadPCMDriver", body)
        require(m is not None, f"{handler} loads no driver")
        rows.append(f"{command}\t{files[m[1]]}\t{handler}")
    return "\n".join(rows) + "\n"


def rs_names(source: Source, path: str) -> dict[str, int]:
    """rsset/rs.b numbering of an include (FM/_Sound IDs.inc; System.inc SCMD_* through program_catalogue)."""
    names, value = {}, 0
    for raw in source.text(path).splitlines():
        line = raw.split(";", 1)[0].strip()
        if m := re.fullmatch(r"rsset\s+\$([0-9A-Fa-f]+)", line):
            value = int(m[1], 16)
        elif m := re.fullmatch(r"(\w+)?\s*rs\.([bw])\s+(\d+)", line):
            if m[1]:
                names[m[1]] = value
            value += int(m[3]) * (1 if m[2] == "b" else 2)
    return names


def text_pointer(data: bytes, origin: int, x: int, y: int) -> int:
    """TEXTPTR (Secrets/_Macros.inc:35-46): a string pointer, then the two H40 plane-A write commands."""
    top = 0x40000003 | (((0xC000 + 2 * x + y * 0x80) & 0x3FFF) << 16)
    bottom = 0x40000003 | (((0xC000 + 2 * x + (y + 1) * 0x80) & 0x3FFF) << 16)
    needle = struct.pack(">II", top, bottom)
    hits = [i for i in range(4, len(data) - 8) if data[i:i + 8] == needle]
    require(len(hits) >= 1, f"TEXTPTR {x},{y}")
    pointers = {struct.unpack(">I", data[h - 4:h])[0] for h in hits}
    require(len(pointers) == 1, f"TEXTPTR {x},{y} names one string")
    return pointers.pop() - origin + MMD_HEADER


# ENDING.MMD data through PC-relative operands: (name, instruction MMD address, opcode word, length, cite).
# The instruction addresses are those of the retail file (MMD origin $FF2000); the cited labels name them in the
# pinned listing. Lengths are the counts the consuming loops use.
ENDING_REFS = [
    ("endCram", 0xFF2636, 0x41FA, 0x80, "Ending_InitializePaletteAndFont (lea EndingFontData, 32 longs)"),
    ("endStillPicture", 0xFF23D4, 0x45FA, 0x12E0, "Ending_LoadTimeAttackData (lea EndingTimeAttackData): palette + $12C0 tiles"),
    ("endCreditsMap", 0xFF29C6, 0x47FA, 0x380, "Ending_VBlankAnimationDataPointer (lea EndingAnimationData, 32x14 words)"),
    ("endEvents", 0xFF24AC, 0x45FA, 0x328A, "Ending_StartEventPlaybackSetStreamPointer (lea EndingEventData)"),
    ("endTextBad", 0xFF2A52, 0x43FA, 0x12C, "Ending_VBlankUploadArtSetA (5 rows of $1E words)"),
    ("endTextGood", 0xFF2AB2, 0x43FA, 0x40, "Ending_VBlankUploadArtSetB (2 rows of $10 words)"),
    ("endFontGood", 0xFF2ADA, 0x43FA, 0x260, "Ending_VBlankUploadArtSetC ($130 words)"),
    ("endFontBad", 0xFF2AEC, 0x43FA, 0x360, "Ending_VBlankUploadArtSetD ($1B0 words)"),
    ("endImageArt", 0xFFC132, 0x41FA, 0x1F2, "Ending_ImageStartupLoadArt (lea EndingArt, Nemesis, up to EndingMap)"),
    ("endImageMap", 0xFFC13A, 0x43FA, 0x78, "Ending_ImageStartupLoadMap (lea EndingMap, 3 rows of $14 words)"),
    ("endImagePalette", 0xFFC14C, 0x41FA, 0x80, "Ending_ImageStartupCopyVdpState (lea EndingVDPState, 32 longs)"),
]
# movem.w d8(pc,d0.w),d0-d1 at Ending_VBlankLoadEventStopTileWords: brief extension word, base = extension address + d8.
ENDING_STOP_COLOURS = ("endStopColours", 0xFF28E0, 0x4CBB, 0x28, "Ending_VBlankTileAnimationTable (movem.w)")
MMD_HEADER = 0x100


def pc_relative(data: bytes, origin: int, address: int, opcode: int) -> int:
    at = address - origin + MMD_HEADER
    found = struct.unpack(">H", data[at:at + 2])[0]
    require(found == opcode, f"Instruction at ${address:06X} is ${found:04X}, expected ${opcode:04X}")
    disp = struct.unpack(">h", data[at + 2:at + 4])[0]
    return address + 2 + disp - origin + MMD_HEADER


def movem_table(data: bytes, origin: int, address: int, opcode: int) -> int:
    at = address - origin + MMD_HEADER
    require(struct.unpack(">H", data[at:at + 2])[0] == opcode, "movem.w (pc,d0.w) expected")
    extension = struct.unpack(">H", data[at + 4:at + 6])[0]
    require(extension & 0xFF00 == 0x0000, "brief extension word with d0.w expected")
    disp = struct.unpack(">b", bytes([extension & 0xFF]))[0]
    return address + 4 + disp - origin + MMD_HEADER


def generate(media_root: Path, source_root: Path) -> str:
    source = Source(source_root)
    cues = sorted((media_root / "USA").glob("*.cue"))
    require(len(cues) == 1, "Expected one USA CUE")
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    lines = ["# Generated by tools/soniccd/fmv_catalogue.py from the USA disc and scddisasm "
             "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff; file offsets and lengths (hex) only.",
             "file\tname\toffset\tlength\tsource"]
    with RawIso(track) as disc:
        for disc_file, name, kind, path, label in INCLUDES:
            blob = source.read(path) if kind == "incbin" else scalar_table(source, path, label)
            offset = unique(disc.file(disc_file), blob, name)
            cite = path if label is None else f"{path} {label}"
            lines.append(f"{disc_file}\t{name}\t{offset:X}\t{len(blob):X}\t{cite}")
        sound = disc.file("SOSEL_.MMD;1")
        sound_origin = struct.unpack(">I", sound[2:6])[0] & 0xFFFFFF
        # The ID tables: located by their FM entry count and order in the code, after the .Codes table.
        best = disc.file("DUMMY4.MMD;1")
        for name, x, y, count in (("bestTimesHeader", 3, 1, 1), ("bestTimesList", 4, 4, 28)):
            at = text_list(best, x, y, count, 0x40)
            lines.append(f"DUMMY4.MMD;1\t{name}\t{at:X}\t{2 + 12 * count:X}\t{SEC}Best Staff Times.asm TEXTSTART at {x}, {y}")
        values = rs_names(source, "Sound Drivers/FM/_Sound IDs.inc") | commands(source)
        for name, names in zip(SOUND_TEST_IDS, id_tables(source)):
            blob = bytes(values[n] for n in names)
            offset = unique(sound, blob, name)
            lines.append(f"SOSEL_.MMD;1\t{name}\t{offset:X}\t{len(blob):X}\t{SEC}Sound Test.asm .IDs ({len(blob)} entries)")
        for name, x, y in SOUND_TEST_TEXT:
            at = text_pointer(sound, sound_origin, x, y)
            end = sound.index(b"\xff", at)
            lines.append(f"SOSEL_.MMD;1\t{name}\t{at:X}\t{end - at:X}\t{SEC}Sound Test.asm TEXTPTR {x}, {y}")
        ending = disc.file("ENDING.MMD;1")
        origin = struct.unpack(">I", ending[2:6])[0] & 0xFFFFFF
        require(origin == 0xFF2000, "ENDING.MMD origin")
        for name, address, opcode, length, cite in ENDING_REFS:
            offset = pc_relative(ending, origin, address, opcode)
            require(offset + length <= len(ending), f"{name} past ENDING.MMD")
            lines.append(f"ENDING.MMD;1\t{name}\t{offset:X}\t{length:X}\t{ENDING} {cite} at ${address:06X}")
        name, address, opcode, length, cite = ENDING_STOP_COLOURS
        offset = movem_table(ending, origin, address, opcode)
        require(ending[offset + length - 4:offset + length] == b"\xff\xff\xff\xff", "stop-colour table end marker")
        lines.append(f"ENDING.MMD;1\t{name}\t{offset:X}\t{length:X}\t{ENDING} {cite} at ${address:06X}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True, help="scddisasm checkout at the pin")
    parser.add_argument("--check", action="store_true", help="fail if the committed resource differs")
    args = parser.parse_args()
    text = generate(args.media_root.expanduser(), args.source.expanduser())
    drivers = pcm_drivers(Source(args.source.expanduser()))
    if args.check:
        require(OUTPUT.read_text() == text, f"{OUTPUT} is stale; rerun without --check")
        require(DRIVERS.read_text() == drivers, f"{DRIVERS} is stale; rerun without --check")
        print("fmv-assets.tsv OK")
        return
    OUTPUT.write_text(text)
    DRIVERS.write_text(drivers)
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
