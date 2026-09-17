#!/usr/bin/env python3
"""Compare engine trace-capture frames with native BizHawk checkpoints, pixel by pixel.

Origin: SOZ pixel checkpoints, 2026-09-17. Inputs: a trace directory (metadata.json,
physics.csv.gz), a full-run TraceCaptureTool MKV at --scale 1 --width 320, and a native
output directory from capture_movie_checkpoints.lua. Output: comparison.json with the
differing-pixel count and bounding box per row, plus diff-<row>.png images when requested.

Row mapping, both measured on soz_completerun:
- native frame = bk2_frame_offset + pre_trace_osc_frames + row;
- the full-run MKV presents no frame on lag rows (physics lag_counter != 0), so MKV frame
  n = row - (lag rows before it). Lag rows themselves have no engine frame and are skipped.
Both sides are reduced to 3-bit Genesis channels before comparison (engine 255/7 steps,
GPGX 34 steps); the native image is cropped from its 348x240 border to 320x224.
--mask x0,y0,x1,y1 (inclusive) excludes regions such as HUD values the trace does not
carry. Requires Pillow and ffmpeg.
"""
import argparse
import csv
import gzip
import json
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


def quantize(image, native):
    image = image.convert('RGB')
    if native:
        if image.size != (348, 240):
            raise ValueError(f'native framebuffer must be 348x240, got {image.size}')
        image = image.crop((14, 8, 334, 232))
        lut = [(v + 17) // 34 for v in range(256)]
    else:
        if image.size != (320, 224):
            raise ValueError(f'engine frame must be 320x224, got {image.size}')
        lut = [(v * 7 + 127) // 255 for v in range(256)]
    return Image.merge('RGB', [band.point(lut) for band in image.split()])


def row_map(trace_dir, rows):
    wanted = set(rows)
    lag_before = 0
    mapping = {}
    with gzip.open(trace_dir / 'physics.csv.gz', 'rt', newline='') as fh:
        for row, record in enumerate(csv.DictReader(fh)):
            lag = record['lag_counter'] != '0000'
            if row in wanted:
                mapping[row] = None if lag else row - lag_before
            if lag:
                lag_before += 1
    return mapping


def extract(mkv, frames, directory):
    # ffmpeg select expressions stop parsing when very long, so extract in chunks.
    ordered = sorted(set(frames))
    for start in range(0, len(ordered), 30):
        chunk = ordered[start:start + 30]
        expr = '+'.join(f'eq(n\\,{n})' for n in chunk)
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(mkv),
                        '-vf', f"select='{expr}'", '-fps_mode', 'passthrough',
                        '-frame_pts', '1', str(directory / 'n%06d.png')], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--trace-dir', type=Path, required=True)
    parser.add_argument('--engine-mkv', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--rows', required=True,
                        help='comma-separated rows, or START:END:STEP')
    parser.add_argument('--mask', action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--diff-images', action='store_true')
    args = parser.parse_args()

    if ':' in args.rows:
        start, end, step = (int(v) for v in args.rows.split(':'))
        rows = list(range(start, end, step))
    else:
        rows = [int(v) for v in args.rows.split(',')]
    masks = [tuple(int(v) for v in m.split(',')) for m in args.mask]
    meta = json.loads((args.trace_dir / 'metadata.json').read_text())
    native_base = meta['bk2_frame_offset'] + meta.get('pre_trace_osc_frames', 0)
    mapping = row_map(args.trace_dir, rows)
    args.output.mkdir(parents=True, exist_ok=True)

    results = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        extract(args.engine_mkv, [n for n in mapping.values() if n is not None], tmp)
        for row in rows:
            n = mapping.get(row)
            native = args.native_dir / f'f{native_base + row}.png'
            engine = tmp / f'n{n:06d}.png' if n is not None else None
            if engine is None or not engine.exists() or not native.exists():
                results.append({'row': row, 'status': 'missing', 'engine_frame': n})
                continue
            diff = ImageChops.difference(quantize(Image.open(engine), False),
                                         quantize(Image.open(native), True))
            mask = diff.convert('L').point(lambda v: 255 if v else 0)
            draw = ImageDraw.Draw(mask)
            for rect in masks:
                draw.rectangle(rect, fill=0)
            count = mask.histogram()[255]
            results.append({'row': row, 'engine_frame': n, 'native_frame': native_base + row,
                            'differing_pixels': count, 'bbox': mask.getbbox()})
            if args.diff_images and count:
                view = Image.eval(Image.open(engine).convert('RGB'), lambda v: v // 4)
                view.paste((255, 0, 255), mask=mask)
                view.save(args.output / f'diff-{row:06d}.png')
    summary = {'trace': str(args.trace_dir), 'engine_mkv': str(args.engine_mkv),
               'native_dir': str(args.native_dir), 'masks': masks, 'rows': results,
               'exact': sum(1 for r in results if r.get('differing_pixels') == 0),
               'compared': sum(1 for r in results if 'differing_pixels' in r)}
    (args.output / 'comparison.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(f"{summary['exact']} exact of {summary['compared']} compared rows")


if __name__ == '__main__':
    main()
