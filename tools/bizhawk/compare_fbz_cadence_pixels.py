#!/usr/bin/env python3
"""Pair opaque FBZ native/GPU source pixels; originating task FBZ 2026-09-14.
Inputs: native BizHawk VRAM/CRAM/VSRAM/PNG/RAM series, engine cadence receipts
with actual-GPU source masks and PNGs, independently reviewed native rectangle.
Outputs remain external. This does not accept transparent backgrounds, occlusions,
whole frames, or a checkpoint merely because producer hashes agree.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
from PIL import Image

RANGES = {'200': (0x200, 8), '208': (0x208, 8), '210': (0x210, 32),
          '230': (0x230, 8), '238': (0x238, 16)}

def sha(data):
    return hashlib.sha256(data).hexdigest().upper()

def rgb3(rgb, native):
    return tuple(round(v / 34 if native else v * 7 / 255) for v in rgb)

def native_mask(stem, region, channel):
    v = stem.with_suffix('.vram').read_bytes()
    cr = stem.with_suffix('.cram').read_bytes()
    vs = stem.with_suffix('.vsram').read_bytes()
    im = Image.open(stem.with_suffix('.png')).convert('RGB')
    if im.size != (348, 240):
        raise ValueError('Native evidence requires measured 348x240 framebuffer')
    im = im.crop((14, 8, 334, 232))
    word = lambda data, p: int.from_bytes(data[p:p+2], 'big')
    palette = [((word(cr, p) & 15)*17, ((word(cr, p)>>4)&15)*17,
                ((word(cr, p)>>8)&15)*17) for p in range(0, 128, 2)]
    start, count = RANGES[channel]
    records = []
    def sample(attr, lx, ly, x, y):
        tile = attr & 0x7ff
        rx, ry, rw, rh = region
        if not (start <= tile < start+count and rx <= x < rx+rw and ry <= y < ry+rh):
            return
        if attr & 0x800: lx = 7-lx
        if attr & 0x1000: ly = 7-ly
        n = (v[tile*32+ly*4+lx//2] >> (4 if lx%2 == 0 else 0)) & 15
        if not n: return
        colour = ((attr >> 13) & 3)*16+n
        actual = im.getpixel((x, y))
        records.append({'x': x, 'y': y, 'tile': tile, 'local': ly*8+lx,
                        'colour': colour, 'rgb3': rgb3(actual, True),
                        'matched': actual == palette[colour]})
    if channel != '200':
        for plane, base in enumerate((0xc000, 0xe000)):
            vertical = word(vs, plane*2)
            for y in range(224):
                sy = (y+vertical) % 256
                scroll = word(v, 0xf000+y*4+plane*2)
                for x in range(320):
                    sx = (x-scroll) % 512
                    sample(word(v, base+(sy//8*64+sx//8)*2), sx%8, sy%8, x, y)
    else:
        index = 0
        seen = set()
        while index not in seen and index < 80:
            seen.add(index)
            base = 0xf800+index*8
            sy, sx = word(v, base)-128, (word(v, base+6)&0x1ff)-128
            size, attr = v[base+2], word(v, base+4)
            w, h = ((size>>2)&3)+1, (size&3)+1
            for y in range(max(0, sy), min(224, sy+h*8)):
                for x in range(max(0, sx), min(320, sx+w*8)):
                    dx, dy = x-sx, y-sy
                    if attr&0x800: dx = w*8-1-dx
                    if attr&0x1000: dy = h*8-1-dy
                    tile = (attr&0x7ff)+dx//8*h+dy//8
                    sample((attr&0xe000)|tile, dx%8, dy%8, x, y)
            index = v[base+3]&0x7f
            if index == 0: break
    return records, sha(v[start*32:(start+count)*32])

def compare(args):
    receipts = sorted(args.engine_series.joinpath('provenance').glob('*.json'),
                      key=lambda p: json.loads(p.read_text())['capture_index'])
    if len(receipts) != 6: raise ValueError('Exactly six engine frames required')
    rows = []
    args.output.mkdir(parents=True, exist_ok=True)
    for i, path in enumerate(receipts):
        e = json.loads(path.read_text())
        stem = args.native_series / f'sample-{args.native_frame_start+i}'
        native, payload = native_mask(stem, args.native_region, args.channel)
        proof = e['destination_pixel_evidence']
        if args.channel == '200': proof = proof['sprite_destination_evidence']
        if payload != e['pattern_payload_sha256'] or payload != proof['gpu_destination_sha256']:
            raise ValueError(f'Frame{i}: native and actual engine GPU presentation phases differ')
        mask = proof['source_pixel_mask']
        image_path = args.engine_series / (path.stem+'.png')
        im = Image.open(image_path).convert('RGB')
        records = bytes.fromhex(mask['records_hex'])
        palette = mask['uploaded_palette_rgb24']
        keys = {}
        engine_matched = 0
        for x, y, tile, sample, colour in struct.iter_unpack('>HHHBB', records):
            actual = im.getpixel((x, y))
            value = palette[colour]
            expected = (value>>16 & 255, value>>8 & 255, value & 255)
            matched = actual == expected
            if matched != bool(sample&128): raise ValueError('Engine mask/framebuffer disagreement')
            if matched:
                engine_matched += 1
                keys.setdefault((tile, sample&63, colour), set()).add(rgb3(actual, False))
        eligible = [r for r in native if r['matched']]
        comparable = [r for r in eligible if (r['tile'], r['local'], r['colour']) in keys]
        equal = sum(tuple(r['rgb3']) in keys[(r['tile'], r['local'], r['colour'])] for r in comparable)
        native_mask_path = args.output / f'native-source-mask-{i:02}.json'
        native_mask_path.write_text(json.dumps(native, separators=(',', ':'))+'\n')
        row = dict(index=i, native_frame=args.native_frame_start+i,
                   native_opaque=len(native), native_matched=len(eligible),
                   native_occluded_or_mismatched=len(native)-len(eligible),
                   engine_opaque=len(records)//8, engine_matched=engine_matched,
                   engine_occluded_or_mismatched=len(records)//8-engine_matched,
                   comparable_native_pixels=len(comparable), equal_source_pixels=equal,
                   native_unrepresented_source_pixels=len(eligible)-len(comparable),
                   payload_sha256=payload, engine_receipt=str(path),
                   engine_png_sha256=sha(image_path.read_bytes()),
                   native_png_sha256=sha(stem.with_suffix('.png').read_bytes()),
                   engine_receipt_sha256=sha(path.read_bytes()),
                   engine_artifact_sha256=e.get('built_artifact_sha256'),
                   engine_timer_before=e.get('timer_before'), engine_timer_after=e.get('timer_after'),
                   engine_index_before=e.get('frame_before'), engine_index_after=e.get('frame_after'),
                   native_source_mask=str(native_mask_path), native_source_mask_sha256=sha(native_mask_path.read_bytes()))
        rows.append(row)
    result = {'status': 'candidate-independent-review-required', 'channel': args.channel,
              'scope': 'opaque source-local pixels with matching tile/local/palette/nibble and service phase; excludes transparent backgrounds, occlusions, whole frames',
              'native_region': args.native_region,
              'pixel_comparison': 'PASS' if all(r['comparable_native_pixels'] > 0
                  and r['equal_source_pixels'] == r['comparable_native_pixels']
                  and r['native_unrepresented_source_pixels'] == 0 for r in rows) else 'FAIL',
              'comparator_sha256': sha(Path(__file__).read_bytes()), 'frames': rows}
    (args.output/'comparison.json').write_text(json.dumps(result, indent=2)+'\n')
    return result

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--native-series', type=Path, required=True)
    p.add_argument('--native-frame-start', type=int, required=True)
    p.add_argument('--native-region', nargs=4, type=int, required=True)
    p.add_argument('--engine-series', type=Path, required=True)
    p.add_argument('--channel', choices=RANGES, required=True)
    p.add_argument('--output', type=Path, required=True)
    result = compare(p.parse_args())
    for row in result['frames']:
        print(row['index'], row['equal_source_pixels'], '/', row['comparable_native_pixels'],
              'unrepresented', row['native_unrepresented_source_pixels'])
