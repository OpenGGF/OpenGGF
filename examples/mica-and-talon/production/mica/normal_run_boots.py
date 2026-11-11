#!/usr/bin/env python3
"""Rebuild/check the interim normal RUN footwear selections.

Purpose: preserve approved MICA boot reuse, upper anatomy and native ground support.
Inputs: normal-run-boots.json original/selected indexed rows and boot source hash.
Origin: owner interim feedback, 2026-11-11. No ROM or decoded native pixels needed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
ART = ROOT / 'examples/mica-and-talon/art/mica/sheet'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
from derive_slopes import derive, ANGLES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    source = json.loads(Path(__file__).with_name('normal-run-boots.json').read_text())
    palette = json.loads((ART / 'palette.yaml').read_text())['colors']
    with Image.open(ART / source['boot_source']['file']) as boot:
        assert hashlib.sha256(boot.tobytes()).hexdigest() == source['boot_source']['source_index_sha256']
    frames = {}
    for record in source['frames']:
        image = Image.new('P', tuple(record['size']))
        image.putpalette(sum(palette, []) + [0] * (768 - 48))
        image.putdata([int(value, 16) for row in record['rows'] for value in row])
        frames[record['id']] = image
        path = ART / record['file']
        if args.check:
            with Image.open(path) as actual:
                assert actual.size == image.size and actual.tobytes() == image.tobytes(), record['id']
                assert actual.getpalette()[:48] == image.getpalette()[:48], record['id']
        else:
            image.save(path, transparency=0, optimize=True, bits=4)
    hand = json.loads((ART / 'frames.yaml').read_text())
    by_id = {frame['id']: frame for frame in hand['frames']}
    geometry = json.loads(Path(__file__).with_name('native-pose-core.json').read_text())['frames']
    for original in source['flat_sources']:
        key = original['id']
        image = frames[key]
        assert image.height - by_id[key]['hotspot'][1] == 20, key
        dx, dy = original['canvas_shift']
        for y, row in enumerate(original['source_rows'][:13]):
            for x, value in enumerate(row):
                assert image.getpixel((x + dx, y + dy)) == int(value, 16), (key, 'upper anatomy')
        for x, y in original['protected_pixels']:
            if y < 20:
                assert image.getpixel((x + dx, y + dy)) == int(original['source_rows'][y][x], 16), (key, 'face/glove/body')
        for slope, angle in ANGLES.items():
            target = key.replace('run-', f'run-{slope}-')
            turned, hotspot = derive(image, by_id[key]['hotspot'], angle, geometry[target]['native_support'])
            assert turned.size == frames[target].size and turned.tobytes() == frames[target].tobytes(), target
            assert hotspot == by_id[target]['hotspot'], target
    print('16 normal RUN footwear selections match; upper anatomy and native ground support retained')


if __name__ == '__main__':
    main()
