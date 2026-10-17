#!/usr/bin/env python3
"""Compare every declared native/engine picture as raw RGB, fail closed.

Inputs: two capture directories, unique nonnegative spots (one per line), explicit
identity or native-poll-plus-one mapping, expected pair count and resulting size.
Optional crops are explicit x,y,width,height rectangles, independently per image.
No normalization, shift, rescale, mask or alignment search. Origin: 2026-10-17 S3K
remaining-parity ending sampler; replaces the external comparator's missing-PNG skip.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from PIL import Image


def read_spots(path):
    lines = Path(path).read_text().splitlines()
    if not lines or any(not re.fullmatch(r"0|[1-9][0-9]*", line) for line in lines):
        raise ValueError("Spots must be nonempty canonical nonnegative decimal indices, one per line")
    spots = [int(line) for line in lines]
    if len(set(spots)) != len(spots):
        raise ValueError("Duplicate spots")
    if any(spot >= 60000 for spot in spots):
        raise ValueError("Spot outside bounded observation range [0,60000)")
    return sorted(spots)


def engine_index(spot, mapping):
    if mapping == "identity":
        return spot
    if mapping == "native-poll-plus-one":
        if spot >= 59999:
            raise ValueError("Mapped engine outer is outside [0,60000)")
        return spot + 1
    raise ValueError("An explicit supported observation mapping is required")


def dimensions(text):
    if not re.fullmatch(r"[1-9][0-9]*x[1-9][0-9]*", text):
        raise ValueError("Expected positive WIDTHxHEIGHT")
    return tuple(map(int, text.split("x")))


def crop_rectangle(text):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*),(?:0|[1-9][0-9]*),[1-9][0-9]*,[1-9][0-9]*", text):
        raise ValueError("Crop must be nonnegative x,y and positive width,height")
    return tuple(map(int, text.split(",")))


def load_rgb(path, crop):
    # Force decoding inside the context; malformed/missing files propagate as errors.
    with Image.open(path) as image:
        image.load()
        if image.mode not in ("RGB", "RGBA"):
            raise ValueError(f"Expected RGB/RGBA channels, got {image.mode}: {path}")
        original_size = image.size
        if crop is not None:
            x, y, width, height = crop
            if x + width > image.width or y + height > image.height:
                raise ValueError(f"Crop outside image: {path} {image.size} {crop}")
            image = image.crop((x, y, x + width, y + height))
        # RGB means the original R/G/B bytes, excluding alpha; no color transform.
        rgb = image.convert("RGB")
        return original_size, rgb.size, rgb.tobytes()


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def compare(native, engine, spots, mapping, expected_count, size, native_crop=None, engine_crop=None):
    if expected_count <= 0 or len(spots) != expected_count:
        raise ValueError(f"Expected exactly {expected_count} requested pairs, got {len(spots)}")
    if len(set(spots)) != len(spots) or any(type(p) is not int or p < 0 or p >= 60000 for p in spots):
        raise ValueError("Invalid or duplicate spots")
    if len(size) != 2 or any(type(n) is not int or n <= 0 for n in size):
        raise ValueError("Invalid expected size")
    for crop in (native_crop, engine_crop):
        if crop is not None and (len(crop) != 4 or any(type(n) is not int for n in crop)
                                 or min(crop[:2]) < 0 or min(crop[2:]) <= 0):
            raise ValueError("Invalid crop")
    pairs = []
    for poll in sorted(spots):
        outer = engine_index(poll, mapping)
        np = Path(native) / f"input-{poll:05d}.ppm"
        ep = Path(engine) / f"outer-{outer:05d}.png"
        ns, nsize, a = load_rgb(np, native_crop)
        es, esize, b = load_rgb(ep, engine_crop)
        if nsize != esize or nsize != size:
            raise ValueError(f"Size mismatch at spot {poll}: native {ns}->{nsize}, engine {es}->{esize}, expected {size}")
        differing = [i // 3 for i in range(0, len(a), 3) if a[i:i + 3] != b[i:i + 3]]
        first = None
        if differing:
            index = differing[0]
            first = dict(x=index % size[0], y=index // size[0],
                         native=list(a[index * 3:index * 3 + 3]), engine=list(b[index * 3:index * 3 + 3]))
        pairs.append(dict(native_poll=poll, engine_outer=outer, native_size=ns, engine_size=es,
                          native_sha256=sha256(np), engine_sha256=sha256(ep),
                          mismatching_rgb_pixels=len(differing), first_difference=first))
    return dict(mapping=mapping, expected_pairs=expected_count, compared_pairs=len(pairs), size=size,
                native_crop=native_crop, engine_crop=engine_crop,
                mismatching_pairs=sum(p['mismatching_rgb_pixels'] != 0 for p in pairs),
                mismatching_rgb_pixels=sum(p['mismatching_rgb_pixels'] for p in pairs), pairs=pairs)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-dir', required=True, type=Path)
    parser.add_argument('--engine-dir', required=True, type=Path)
    parser.add_argument('--spots', required=True, type=Path)
    parser.add_argument('--mapping', required=True, choices=['identity', 'native-poll-plus-one'])
    parser.add_argument('--expected-count', required=True, type=int)
    parser.add_argument('--size', required=True, help='Result size WIDTHxHEIGHT (asserted for every pair)')
    parser.add_argument('--native-crop', help='Explicit x,y,width,height; omitted means no crop')
    parser.add_argument('--engine-crop', help='Explicit x,y,width,height; omitted means no crop')
    parser.add_argument('--report', required=True, type=Path, help='New JSON path; existing paths are refused')
    args = parser.parse_args(argv)
    try:
        result = compare(args.native_dir, args.engine_dir, read_spots(args.spots), args.mapping,
                         args.expected_count, dimensions(args.size),
                         crop_rectangle(args.native_crop) if args.native_crop else None,
                         crop_rectangle(args.engine_crop) if args.engine_crop else None)
        result.update(spots_sha256=sha256(args.spots), comparator_sha256=sha256(__file__),
                      native_dir=str(args.native_dir.resolve()), engine_dir=str(args.engine_dir.resolve()))
        with args.report.open('x') as report:
            json.dump(result, report, indent=2)
            report.write('\n')
        first = next((p for p in result['pairs'] if p['mismatching_rgb_pixels']), None)
        print(json.dumps({k: v for k, v in result.items() if k != 'pairs'}, indent=2))
        if first:
            print('first mismatching pair:', json.dumps(first))
        return 1 if result['mismatching_pairs'] else 0
    except (OSError, ValueError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
