#!/usr/bin/env python3
"""Purpose: isolate a mascot retained between two native title-plane compositions.
Inputs: RGBA mascot-only and composed PNG exports, an explicit vertical bound.
Origin: MICA/TALON title lane, 2026-11-10; final S3K portrait/emblem ownership probe.
Native images stay in scratch; the binary output contains no RGB or tile art.
"""
import argparse
from pathlib import Path
from PIL import Image


def matching_mask(mascot, composition, height):
    if mascot.size != composition.size:
        raise ValueError("Reference dimensions must match")
    width, full_height = mascot.size
    if not 0 <= height <= full_height:
        raise ValueError("Vertical bound is outside the reference")
    return bytes(int(y < height and mascot.getpixel((x,y))[3] != 0
                     and mascot.getpixel((x,y)) == composition.getpixel((x,y)))
                 for y in range(full_height) for x in range(width))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mascot', type=Path, required=True)
    parser.add_argument('--composition', type=Path, required=True)
    parser.add_argument('--height', type=int, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    mascot = Image.open(args.mascot).convert('RGBA')
    composition = Image.open(args.composition).convert('RGBA')
    args.out.write_bytes(matching_mask(mascot, composition, args.height))


if __name__ == '__main__':
    main()
