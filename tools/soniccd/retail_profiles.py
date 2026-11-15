#!/usr/bin/env python3
"""Reproduce Sonic CD retail fingerprints from pinned public metadata and a local inventory.

Origin: 2026-10-23 Sonic CD retail-content task, foundation 9152f2ef2.
Inputs are the retained Redump MCD DAT/CUE ZIPs and media-inventory.json.
No BIN bytes are copied or treated as runtime assets. SHA-256 comes from the
inventory only after all 105 size/CRC32/SHA-1 triples match the primary DAT.
This proves metadata correspondence, not current file bytes or physical provenance.
"""

import argparse
import hashlib
import json
import re
import shlex
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

DEFINITION = "scd-cue-layout-content-v1"
DAT_SHA256 = "6910bd275ab99049147bb43367f9dfa149e164bf1dd0486acb96523e740f4fea"
CUES_SHA256 = "22d174ee0acb6422b8316185897ec7b63acfea096b36e69844586987c9c73741"
INVENTORY_SHA256 = "86e18d5281fbd79db5d0a520b675626045a740679848241bc06aeeb6c5f8eaa6"
RECORDS = {"USA": "29188", "Europe": "8302", "Japan": "1632"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def parse_cue(text):
    """Accept only the independently documented split, raw 35-track profile shape."""
    tracks = []
    filename = None
    for line in text.splitlines():
        words = shlex.split(line)
        if not words or words[0] in ("REM", "CATALOG"):
            continue
        if words[0] == "FILE":
            require(len(words) == 3 and words[2] == "BINARY", "Expected BINARY file")
            filename = words[1]
        elif words[0] == "TRACK":
            require(len(words) == 3 and filename is not None, "Invalid TRACK")
            number = int(words[1])
            require(number == len(tracks) + 1, "Nonconsecutive tracks")
            require(words[2] == ("MODE1/2352" if number == 1 else "AUDIO"), "Unexpected track mode")
            tracks.append({"number": number, "file": filename, "indexes": {}})
        elif words[0] == "INDEX":
            require(len(words) == 3 and tracks, "Invalid INDEX")
            match = re.fullmatch(r"(\d+):([0-5]\d):([0-6]\d|7[0-4])", words[2])
            require(match is not None, "Invalid frame timestamp")
            minute, second, frame = map(int, match.groups())
            require(words[1] not in tracks[-1]["indexes"], "Duplicate INDEX")
            tracks[-1]["indexes"][words[1]] = minute * 4500 + second * 75 + frame
        else:
            raise ValueError(f"Unsupported retail CUE directive {words[0]}")
    require(len(tracks) == 35, "Expected 35 tracks")
    require(len({track["file"] for track in tracks}) == 35, "Expected one file per track")
    for track in tracks:
        indexes = track["indexes"]
        if track["number"] == 1:
            require(indexes == {"01": 0}, "Unexpected data indexes")
        else:
            require(set(indexes) == {"00", "01"} and indexes["00"] == 0, "Unexpected audio indexes")
    return tracks


def verified_tracks(files, cue_text, record):
    tracks = parse_cue(cue_text)
    roms = record.findall("rom")
    binaries = [rom for rom in roms if rom.attrib["name"].endswith(".bin")]
    require(len(roms) == 36 and len(binaries) == 35, "Expected CUE plus 35 DAT BIN entries")
    by_number = {}
    for rom in binaries:
        match = re.search(r"\(Track (\d+)\)\.bin$", rom.attrib["name"])
        require(match is not None, "Missing DAT track ordinal")
        number = int(match[1])
        require(number not in by_number, "Duplicate DAT track")
        by_number[number] = rom.attrib
    local = {Path(item["path"]).name: item for item in files if item["path"].endswith(".bin")}
    require(len(local) == 35, "Expected 35 inventory BINs")
    result = []
    cursor = 0
    for track in tracks:
        item = local[track["file"]]
        expected = by_number[track["number"]]
        require(item["bytes"] == int(expected["size"]), "DAT size mismatch")
        require(item["crc32"].lower() == expected["crc"].lower(), "DAT CRC32 mismatch")
        require(item["sha1"].lower() == expected["sha1"].lower(), "DAT SHA-1 mismatch")
        require(item["bytes"] % 2352 == 0, "Incomplete raw frame")
        require(re.fullmatch(r"[0-9a-f]{64}", item["sha256"]) is not None, "Invalid inventory SHA-256")
        gap = track["indexes"]["01"]
        frames = item["bytes"] // 2352
        require(frames > gap, "Empty program")
        result.append({"number": track["number"], "mode": "MODE1_2352" if track["number"] == 1 else "AUDIO",
                       "discIndex01Frame": cursor + gap, "programFrames": frames - gap,
                       "storedPregapFrames": gap, "syntheticPregapFrames": 0,
                       "storedFramesSha256": item["sha256"]})
        cursor += frames
    return result


def normalized_sha256(tracks):
    def utf(text):
        raw = text.encode("ascii")
        return struct.pack(">H", len(raw)) + raw

    content = bytearray(utf(DEFINITION) + struct.pack(">i", len(tracks)))
    for track in tracks:
        content += struct.pack(">i", track["number"]) + utf(track["mode"])
        content += struct.pack(">qqqq", track["discIndex01Frame"], track["programFrames"],
                               track["storedPregapFrames"], track["syntheticPregapFrames"])
        content += bytes.fromhex(track["storedFramesSha256"])
    return hashlib.sha256(content).hexdigest()


def profiles(inventory_path, dat_path, cues_path):
    inventory_bytes = inventory_path.read_bytes()
    require(hashlib.sha256(inventory_bytes).hexdigest() == INVENTORY_SHA256, "Inventory pin mismatch")
    require(hashlib.sha256(cues_path.read_bytes()).hexdigest() == CUES_SHA256, "CUE ZIP pin mismatch")
    with zipfile.ZipFile(dat_path) as archive:
        require(len(archive.namelist()) == 1, "Expected one DAT")
        dat = archive.read(archive.namelist()[0])
    require(hashlib.sha256(dat).hexdigest() == DAT_SHA256, "DAT pin mismatch")
    root = ET.fromstring(dat)
    inventories = {entry["region_label"]: entry for entry in json.loads(inventory_bytes)["archives"]}
    result = {}
    with zipfile.ZipFile(cues_path) as cues:
        for region, record_id in RECORDS.items():
            record = root.find(f"game[@id='{record_id}']")
            require(record is not None, "Missing primary record")
            files = inventories[region]["files"]
            cue_files = [item for item in files if item["path"].endswith(".cue")]
            require(len(cue_files) == 1, "Expected one local CUE")
            cue_bytes = (inventory_path.parent / cue_files[0]["path"]).read_bytes()
            require(hashlib.sha256(cue_bytes).hexdigest() == cue_files[0]["sha256"], "Local CUE changed")
            primary = cues.read(record.attrib["name"] + ".cue")
            primary_rom = next(rom for rom in record.findall("rom") if rom.attrib["name"].endswith(".cue"))
            require(hashlib.sha1(primary).hexdigest() == primary_rom.attrib["sha1"], "Primary CUE mismatch")
            local_text, primary_text = cue_bytes.decode("utf-8"), primary.decode("utf-8")
            require(local_text.casefold() == primary_text.casefold(), "CUE differs beyond filename case")
            tracks = verified_tracks(files, local_text, record)
            result[region] = {"key": f"redump-{record_id}", "referenceUrl": f"https://redump.info/disc/{record_id}",
                              "normalizedSha256": normalized_sha256(tracks), "tracks": tracks}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--dat", type=Path, required=True)
    parser.add_argument("--cues", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(profiles(args.inventory, args.dat, args.cues), indent=2))


if __name__ == "__main__":
    main()
