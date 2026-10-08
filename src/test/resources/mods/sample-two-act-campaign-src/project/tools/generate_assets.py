#!/usr/bin/env python3
"""Recreate Tide Circuit's original bounded binary art/geometry (2026-10-07 readiness task).

Inputs are the small authored constants below; no ROM or disassembly is read.
Level placement/bounds/music metadata remains hand-authored in each level.json.
Run --check to compare committed bytes without writing, or omit it to regenerate.
"""
import argparse
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/levels/tide"

def words(values):
    return struct.pack(">" + "H" * len(values), *values)

def records(magic, stride, values):
    return magic + struct.pack(">HHI", 1, stride, len(values)) + b"".join(values)

def authored():
    # 8px packed 4bpp tiles: empty, solid floor, animated background color.
    patterns = records(b"GPTN", 32, [bytes(32), bytes([0x11])*32, bytes([0x22])*32])
    chunks = records(b"GCHK", 8, [words([tile]*4) for tile in (0, 1, 2)])
    # Original full floor: 8x8 chunks per 128px block, top/LRB solidity on chunk 1.
    blocks = b"GBLK" + struct.pack(">HBBI", 1, 8, 0, 3) + b"".join(words([chunk]*64) for chunk in (0, 0x5001, 2))
    def layout(cells):
        return b"GMAP" + struct.pack(">HHHHI", 1, 12, 3, 1, 36) + bytes(cells)
    palette = b"GPAL" + struct.pack(">HHHH", 1, 4, 16, 0) + words([0, 0x0A64, 0x0E80, 0x0E22] + [0]*60)
    return {
        "patterns.bin": patterns, "chunks.bin": chunks, "blocks.bin": blocks,
        "fg-map.bin": layout([0]*24 + [1]*12), "bg-map.bin": layout([2]*36),
        "palettes.bin": palette,
        "solid-heights.bin": records(b"GSHG", 16, [bytes(16), bytes([16])*16]),
        "solid-widths.bin": records(b"GSWD", 16, [bytes(16), bytes([16])*16]),
        "solid-angles.bin": records(b"GSAN", 1, [b"\0", b"\0"]),
        "collision-primary.bin": b"GCOL" + struct.pack(">HBBI", 1, 0, 2, 3) + words([0, 1, 0]),
        "collision-secondary.bin": b"GCOL" + struct.pack(">HBBI", 1, 1, 2, 3) + words([0, 1, 0]),
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for act in (1, 2):
        folder = ROOT / f"act{act}"
        for name, payload in authored().items():
            target = folder / name
            if args.check:
                if not target.exists() or target.read_bytes() != payload:
                    raise SystemExit(f"Authored asset mismatch: {target}")
            else:
                folder.mkdir(parents=True, exist_ok=True)
                target.write_bytes(payload)
    print("Verified 22 original bounded assets" if args.check else "Generated 22 original bounded assets")

if __name__ == "__main__":
    main()
