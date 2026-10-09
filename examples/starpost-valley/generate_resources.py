#!/usr/bin/env python3
"""Generate the original flat placeholder's typed assets into a packaging directory.

Input: output resource root (typically target/classes). No ROM input or extracted
art: all tiles are blank and collision is an authored floor at Y=192.
Origin: Starpost Valley act bridge, 2026-10-09. Phase 1 replaces this placeholder.
"""
import argparse
from pathlib import Path
import struct


def generate(output):
    folder = Path(output) / "levels/town-placeholder"
    folder.mkdir(parents=True, exist_ok=True)

    def write(name, magic, width, count, payload):
        (folder / name).write_bytes(magic + struct.pack(">HHI", 1, width, count) + payload)

    write("patterns.bin", b"GPTN", 32, 1, bytes(32))
    write("chunks.bin", b"GCHK", 8, 2, bytes(16))
    # Empty block 0; block 1's lower half is full-solid chunk 1.
    cells = [0] * 64 + [0] * 32 + [0x3001] * 32
    write("blocks.bin", b"GBLK", 0x0800, 2, struct.pack(">128H", *cells))
    (folder / "fg-map.bin").write_bytes(b"GMAP" + struct.pack(">HHHHI", 1, 26, 2, 1, 52)
        + bytes(26) + bytes([1]) * 26)
    for name, magic in (("solid-heights.bin", b"GSHG"), ("solid-widths.bin", b"GSWD")):
        write(name, magic, 16, 2, bytes(16) + bytes([16]) * 16)
    write("solid-angles.bin", b"GSAN", 1, 2, bytes(2))
    write("collision-primary.bin", b"GCOL", 0x0002, 2, struct.pack(">2H", 0, 1))
    write("collision-secondary.bin", b"GCOL", 0x0102, 2, struct.pack(">2H", 0, 1))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    generate(parser.parse_args().output)
