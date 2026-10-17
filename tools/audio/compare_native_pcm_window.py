#!/usr/bin/env python3
"""Compare a BizHawk final-PCM window with the engine's native-compatible PCM, sample for sample.

Purpose: first real-window tier for the opt-in native-compatible output stage
(docs/architecture/validation/audio/2026-10-16-native-compatible-pcm-output.md).
Inputs:
  --native-dir   output of TraceChaser `--final-pcm-window` (pcm.s16, frames.tsv, metadata.json)
  --start-row    BK2 row that is the declared window origin (e.g. a trace segment's bk2_frame_offset)
  --engine-dir   output of `GameplayCaptureTool --audio --native-pcm` started at that row
                 (native-pcm.s16, native-pcm-frames.tsv)
Verdict: exact equality from the declared origin, or the first differing stereo frame. No shift,
tolerance, gain or normalisation is applied; the onset and level figures are descriptive only.
Originating task: OpenGGF round-2 lane pcm-output, 2026-10-16.
"""
import argparse
import json
import math
import struct
import sys
from pathlib import Path


def read_s16(path):
    data = path.read_bytes()
    return struct.unpack('<%dh' % (len(data) // 2), data)


def read_tsv(path):
    lines = path.read_text().splitlines()
    header = lines[0].split('\t')
    return [dict(zip(header, line.split('\t'))) for line in lines[1:] if line]


def rms(samples):
    return math.sqrt(sum(s * s for s in samples) / len(samples)) if samples else 0.0


def first_nonzero(samples):
    return next((i // 2 for i, s in enumerate(samples) if s), None)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--start-row', type=int, required=True)
    parser.add_argument('--engine-dir', type=Path, required=True)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args(argv)

    native_rows = read_tsv(args.native_dir / 'frames.tsv')
    if args.start_row < 0 or args.start_row >= len(native_rows):
        sys.exit('start row outside the native window')
    origin = sum(int(row['stereo_frames']) for row in native_rows[:args.start_row])
    native = read_s16(args.native_dir / 'pcm.s16')[origin * 2:]
    engine = read_s16(args.engine_dir / 'native-pcm.s16')
    engine_rows = read_tsv(args.engine_dir / 'native-pcm-frames.tsv')
    sessions = sorted({int(row['sessions']) for row in engine_rows})
    frames = min(len(native), len(engine)) // 2
    first = next((i for i in range(frames)
                  if native[2 * i] != engine[2 * i] or native[2 * i + 1] != engine[2 * i + 1]), None)
    equal_frames = sum(1 for i in range(frames)
                       if native[2 * i] == engine[2 * i] and native[2 * i + 1] == engine[2 * i + 1])
    chunk = 736
    profile = []
    for k in range(min(12, frames // chunk)):
        a, b = 2 * k * chunk, 2 * (k + 1) * chunk
        profile.append({'chunk': k, 'native_rms': round(rms(native[a:b]), 1), 'engine_rms': round(rms(engine[a:b]), 1)})
    summary = {
        'schema': 'openggf.native-pcm-window-comparison.v1',
        'native_metadata': json.loads((args.native_dir / 'metadata.json').read_text()),
        'start_row': args.start_row,
        'native_origin_stereo_frame': origin,
        'compared_stereo_frames': frames,
        'engine_stereo_frames': len(engine) // 2,
        'engine_presentation_frames': len(engine_rows),
        'engine_stage_sessions': sessions,
        'verdict': 'MATCH' if first is None and frames > 0 else 'DIFFERENT',
        'first_different_stereo_frame': first,
        'first_different_values': None if first is None else {
            'native': [native[2 * first], native[2 * first + 1]],
            'engine': [engine[2 * first], engine[2 * first + 1]]},
        'equal_stereo_frames': equal_frames,
        'descriptive': {
            'native_first_nonzero_stereo_frame': first_nonzero(native[:2 * frames]),
            'engine_first_nonzero_stereo_frame': first_nonzero(engine[:2 * frames]),
            'native_rms': round(rms(native[:2 * frames]), 1),
            'engine_rms': round(rms(engine[:2 * frames]), 1),
            'per_736_chunk_rms': profile,
        },
    }
    text = json.dumps(summary, indent=2)
    if args.out:
        args.out.write_text(text + '\n')
    print(text)
    return 0


if __name__ == '__main__':
    sys.exit(main())
