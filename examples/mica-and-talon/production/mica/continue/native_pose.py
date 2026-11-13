#!/usr/bin/env python3
"""Rebuild accepted MICA GPT native-pose redraws from lossless indexed rows.

Inputs: native-pose.json, unchanged palette.yaml and continue.yaml geometry.
Origin: owner-approved per-frame native 8x + concept + pilot method, 2026-11-08.
Native reference pixels and raw GPT images remain disposable review inputs.
"""
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent


def rebuild(game):
    source = json.loads((Path(__file__).resolve().parent / 'native-pose.json').read_text())['frames']
    palette = json.loads((HERE.parent / 'palette.yaml').read_text())['colors']
    manifest = json.loads((Path(__file__).resolve().parent / 'continue.yaml').read_text())
    surface = next(s for s in manifest['games'] if s['game'] == game)
    for frame in surface['frames']:
        rows = source[frame['file']]['rows']
        assert [len(rows[0]), len(rows)] == frame['dimensions']
        assert all(len(row) == len(rows[0]) for row in rows)
        im = Image.new('P', tuple(frame['dimensions']))
        im.putpalette(sum(palette, []) + [0] * 720)
        im.putdata([int(pixel, 16) for row in rows for pixel in row])
        im.save(HERE / frame['file'], bits=4, transparency=0)
    return surface
