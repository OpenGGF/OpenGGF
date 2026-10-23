#!/usr/bin/env python3
"""R11A bounded decoder reference, originating in the 2026-10-23 CD04/CD10 task.

Inputs: user-supplied split raw media and the external schema-v2 stage inventory.
Prints counts, consumed cursors and hashes only; never writes commercial bytes.
The independent prefix-tree/word-XOR Nemesis decoder is research, not runtime.
Its optional eight-bit lookahead models the Java reader; it does not execute Java.
No disassembly checkout, Java, Maven, target directory or native core is used.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

from retail_profiles import parse_cue
from stage_inventory import RawIso

R2_SHA256 = "f7eedea40b127a09b31441134ff26c361beb787c346d903834133db4e1460cbf"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def nemesis(data, expected, reader_lookahead=False):
    """Use a prefix tree, then XOR complete 32-bit rows (not Java's lookup table)."""
    require(0 < expected <= 0x10000 and expected % 32 == 0, "Invalid output limit")
    require(len(data) >= 3, "Truncated Nemesis header")
    header = int.from_bytes(data[:2], "big")
    require((header & 0x7fff) * 32 == expected, "Declared Nemesis output differs")
    cursor, palette, codes = 2, 0, {}

    def byte():
        nonlocal cursor
        require(cursor < len(data), "Truncated Nemesis stream")
        value = data[cursor]
        cursor += 1
        return value

    while (control := byte()) != 255:
        if control & 128:
            palette = control & 15
            continue
        length, code = control & 15, byte()
        require(1 <= length <= 8 and code < (1 << length), "Invalid prefix declaration")
        key = f"{code:0{length}b}"
        require(not any(key.startswith(old) or old.startswith(key) for old in codes), "Overlapping codes")
        codes[key] = ((control >> 4) + 1, palette)

    bits = ""

    def ensure(count):
        nonlocal bits
        while len(bits) < count:
            bits += f"{byte():08b}"

    def take(count):
        nonlocal bits
        ensure(count)
        value, bits = bits[:count], bits[count:]
        return value

    nibbles = []
    while len(nibbles) < expected * 2:
        # The prefix-tree decoder needs only enough bits to identify a code.
        # The separate reader check measures NemesisReader's 8-bit lookahead.
        if reader_lookahead:
            ensure(8)
        prefix = ""
        while len(prefix) < 6 and prefix not in codes:
            prefix += take(1)
        if prefix == "111111":
            value = int(take(7), 2)
            repeat, palette = (value >> 4) + 1, value & 15
        else:
            while prefix not in codes and len(prefix) < 8:
                prefix += take(1)
            require(prefix in codes, "Unknown Nemesis prefix")
            repeat, palette = codes[prefix]
        require(len(nibbles) + repeat <= expected * 2, "Run exceeds output")
        nibbles.extend([palette] * repeat)
    raw = bytes(a * 16 + b for a, b in zip(nibbles[::2], nibbles[1::2]))
    if header & 0x8000:
        rows, previous = [], 0
        for (word,) in struct.iter_unpack(">I", raw):
            previous ^= word
            rows.append(struct.pack(">I", previous))
        raw = b"".join(rows)
    return raw, cursor


def native_mask_fragments(data, region):
    """Match instruction encodings for the two distinct native block-index masks.

    These are bounded fragments, not complete collision/drawing execution proofs.
    Block Draw.asm:GetBlockDataAbsXY/DrawBlock; Collision Floor.asm:FindLevelFloor
    and its alternate-direction counterparts. No source checkout is read here.
    """
    delta = 0 if region == "USA" else -4
    fragments = {
        "drawing_block_10_bits": ("3610024303ffe74bd2c370014e75", [0x3398 + delta]),
        "collision_block_11_bits": ("30113800024007ff", [0x11bc, 0x127c, 0x132c, 0x13dc]),
        "drawing_flip_y_bit_12": ("08100004", [0x32da + delta]),
        "drawing_flip_x_bit_11": ("08100003", [0x32e0 + delta, 0x3312 + delta]),
    }
    result = {}
    for name, (hexcode, expected_offsets) in fragments.items():
        pattern = bytes.fromhex(hexcode)
        offsets, cursor = [], 0
        while (cursor := data.find(pattern, cursor)) >= 0:
            offsets.append(cursor)
            cursor += 1
        require(offsets == expected_offsets, f"Native mask/flip fragment differs: {name}")
        result[name] = offsets
    return result


def facts(data, stage, region):
    assets = stage["load_plan"]["assets"]
    require(hashlib.sha256(data).hexdigest() == stage["sha256"], "R11A identity mismatch")

    def view(name):
        entry = assets[name]
        start, size = entry["file_offset"], entry["length"]
        require(0 <= start <= len(data) and 0 <= size <= len(data) - start, "View exceeds file")
        result = data[start:start + size]
        require(hashlib.sha256(result).hexdigest() == entry["sha256"], "View identity mismatch")
        return result

    result, decoded = {}, {}
    for name, expected in (("LevelBlocks", 6432), ("Art_LevelTiles", 25600)):
        decoded[name], cursor = nemesis(view(name), expected)
        asset = assets[name]
        # One real next-file byte is permitted solely for the reader's lookahead.
        read_view = data[asset["file_offset"]:asset["end_exclusive"] + 1]
        reader_output, reader_cursor = nemesis(read_view, expected, reader_lookahead=True)
        require(reader_output == decoded[name], "Lookahead changed output")
        # Counterfactual controls: the added byte supplies no decoded payload bits.
        if reader_cursor > asset["length"]:
            for last in (0, 0x55, 0xaa, 0xff):
                changed = read_view[:-1] + bytes([last])
                control, control_cursor = nemesis(changed, expected, reader_lookahead=True)
                require(control == decoded[name] and control_cursor == reader_cursor, "Read-ahead byte changed payload")
        result[name] = dict(output_bytes=expected, payload_consumed_bytes=cursor,
                            reader_lookahead_model_consumed_bytes=reader_cursor,
                            input_bytes=len(view(name)), sha256=hashlib.sha256(decoded[name]).hexdigest())
    patterns = [word[0] & 0x7ff for word in struct.iter_unpack(">H", decoded["LevelBlocks"])]
    chunks = [word[0] for word in struct.iter_unpack(">H", view("LevelChunks"))]
    result["maximum_pattern_index"] = max(patterns)
    result["maximum_render_block_index"] = max(word & 0x3ff for word in chunks)
    result["maximum_collision_block_index"] = max(word & 0x7ff for word in chunks)
    ram = bytearray(0x800)
    for name, destination in (("LayoutFG", 0), ("LayoutBG", 0x40)):
        raw = view(name)
        width, height = raw[0] + 1, raw[1] + 1
        require(width <= 0x40 and height <= 0x10 and len(raw) == 2 + width * height, "Layout dimensions")
        ids = raw[2:]
        for row in range(height):
            ram[destination + row * 0x80:destination + row * 0x80 + width] = ids[row * width:(row + 1) * width]
        referenced = [value & 0x7f for value in ids if value & 0x7f]
        require(all(value <= 121 for value in referenced), "Chunk reference outside extent")
        selected = [chunks[(value - 1) * 256 + offset] & 0x3ff for value in referenced for offset in range(256)]
        collision = [chunks[(value - 1) * 256 + offset] & 0x7ff for value in referenced for offset in range(256)]
        require(max(selected) < 804 and max(collision) < 900, "Referenced block/collision exceeds decoded extent")
        result[name] = dict(columns=width, rows=height, maximum_chunk_id=max(referenced),
                            maximum_referenced_block_index=max(selected), maximum_referenced_collision_index=max(collision))
    result["layout_ram_sha256"] = hashlib.sha256(ram).hexdigest()
    palette = view("Pal_Sonic") + view("Pal_Level")
    result["initial_palette_sha256"] = hashlib.sha256(palette).hexdigest()
    rows = list(struct.iter_unpack(">HHBBBB", view("ObjectPlacementRecords")))
    require(rows[-1][0] == 0xffff and all(row[0] != 0xffff for row in rows[:-1]), "Placement sentinel")
    require(all(1 <= row[2] & 0x7f <= 64 and row[4] in (0x20, 0x40, 0x60, 0x80, 0xa0, 0xc0, 0xe0)
                for row in rows[:-1]), "Placement ABI fields")
    require([row[0] for row in rows[:-1]] == sorted(row[0] for row in rows[:-1]), "Placement order")
    result["placements"] = dict(shared=len(rows) - 1, **{name: sum(bool(row[4] & bit) for row in rows[:-1])
                               for name, bit in (("past", 0x20), ("present", 0x40), ("future", 0x80))})
    result["native_mask_instruction_fragments"] = native_mask_fragments(data, region)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--media-root", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    options = parser.parse_args()
    inventory_bytes = options.inventory.read_bytes()
    require(hashlib.sha256(inventory_bytes).hexdigest() == R2_SHA256, "Authoritative R2 metadata hash differs")
    inventory = json.loads(inventory_bytes)
    require(inventory["schema"] == "soniccd-stage-inventory-v2", "Requires R2 inventory")
    require(inventory["source_pin"] == "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff", "Wrong research source pin")
    for release in inventory["releases"]:
        directory = options.media_root / release["region"]
        cues = list(directory.glob("*.cue"))
        require(len(cues) == 1, "Expected one supplied CUE per region")
        tracks = parse_cue(cues[0].read_text())
        require(tracks[0]["file"] == release["data_track"], "R2 data-track binding differs")
        with RawIso(directory / release["data_track"]) as image:
            stage = next(stage for stage in release["stages"] if stage["stage"] == "R11A")
            print(json.dumps(dict(region=release["region"], **facts(image.file(stage["file"]), stage, release["region"])), sort_keys=True))
        require(len(tracks) == 35, "Unexpected supplied track count")


if __name__ == "__main__":
    main()
