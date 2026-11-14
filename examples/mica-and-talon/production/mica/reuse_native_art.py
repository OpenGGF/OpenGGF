#!/usr/bin/env python3
"""Purpose: reuse accepted MICA normal-Sonic mapping poses in the core sheet.
Inputs: original indexed ending/slot PNGs, their manifests, core recipe/provenance.
Origin: MICA W2 native-frame alias delivery, 2026-11-08.
No ROM/disassembly input: only original creator pixels are copied. Run the
shared reducer and assembler afterward; source hashes guard stale re-adoption.
"""
import hashlib
import importlib.util
import json
from pathlib import Path

from PIL import Image


def main():
    root = Path(__file__).resolve().parents[4]
    art = root / 'examples/mica-and-talon/art/mica'
    spec = importlib.util.spec_from_file_location(
        'pipeline', root / 'tools/modding/playable_art/pipeline.py')
    pipeline = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(pipeline)
    recipe = json.loads((Path(__file__).resolve().parent / 'recipe.json').read_text())
    metadata = json.loads((Path(__file__).resolve().parent / 'native-pose-core.json').read_text())
    colors = sum(json.loads((Path(__file__).resolve().parent / 'palette.yaml').read_text())['colors'], [])
    ending = json.loads((art / 'endings/s3k/s3k.yaml').read_text())['frames']
    slots = next(surface for surface in json.loads((art / 'wave3/wave3.yaml').read_text())['surfaces']
                 if surface['surface'] == 'slot_machine')['frames']
    continued = next(game for game in json.loads((art / 'continue/continue.yaml').read_text())['games']
                     if game['game'] == 's3k')['frames']
    inputs = []
    for index in (0xBA, 0xCA, 0xCB):
        frame = next(frame for frame in ending if frame['index'] == index)
        inputs.append((index, art / 'endings/s3k' / frame['file'], frame))
    for index in range(0x96, 0x9B):
        frame = next(frame for frame in slots if frame['id'] == index)
        inputs.append((index, art / 'wave3' / frame['file'], frame))

    for index in (0x24, 0x55, 0x56, 0x57, 0x59, 0x5A):
        frame = next(frame for frame in continued if frame['bank'] == 'player' and frame['id'] == index)
        inputs.append((index, art / 'continue' / frame['file'], frame))

    for order, (index, path, frame) in enumerate(inputs):
        key = f'native-pose-{index:02x}'
        with Image.open(path) as original:
            if (original.mode != 'P' or original.getpalette()[:48] != colors
                    or original.info.get('transparency') != 0):
                raise ValueError('Expected the shared MICA indexed palette: ' + str(path))
            out = pipeline.outline(original)
        bounds = out.getbbox()
        out = out.crop(bounds)
        anchor = [frame['hotspot'][0] - bounds[0], frame['hotspot'][1] - bounds[1]]
        recipe['frames'] = [frame for frame in recipe['frames'] if frame['id'] != key]
        recipe['frames'].append(dict(
            id=key, animation='NATIVE_POSE', order=order, angle_set='flat', ticks=6,
            notes='Reuse original MICA normal-Sonic mapping art from ' + str(path.relative_to(art))
                  + '; no scaling or native pixel copying.',
            native_pixels=dict(rows=[''.join(format(out.getpixel((x, y)), 'x')
                                             for x in range(out.width)) for y in range(out.height)],
                               anchor=anchor)))
        geometry = dict(game='S3K', mapping=index, label=frame['native_source_label'],
                        size=list(out.size), hotspot=anchor)
        metadata['frames'][key] = dict(
            source='existing original MICA native-pose art', source_asset=str(path.relative_to(art)),
            source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            reference=geometry, geometry_reference=geometry, native_size=list(out.size), hotspot=anchor,
            cleanup='Original indexed pixels; inside-ink convention; opaque crop and corresponding '
                    'hotspot adjustment; no rescale/rotation.')
    recipe['nativeFrames'].update({
        'ride/stand': 'native-pose-ba', 'crane/fall': 'native-pose-ca', 'hang/crane': 'native-pose-cb',
        'bar/release': 'native-pose-24',
        **{f'twist/{index - 0x55}': f'native-pose-{index:02x}' for index in (0x55, 0x56, 0x57, 0x59, 0x5A)},
        **{f'roll/{index}': f'native-pose-{0x96 + index:02x}' for index in range(5)}})
    (Path(__file__).resolve().parent / 'recipe.json').write_text(json.dumps(recipe, indent=2) + '\n')
    (Path(__file__).resolve().parent / 'native-pose-core.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    main()
