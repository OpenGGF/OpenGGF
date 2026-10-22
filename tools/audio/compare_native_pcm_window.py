#!/usr/bin/env python3
"""Compare a BizHawk final-PCM window with the engine's native-compatible PCM, sample for sample.

Purpose: first real-window tier for the opt-in native-compatible output stage
(docs/architecture/validation/audio/2026-10-16-native-compatible-pcm-output.md).
Inputs:
  --native-dir   output of TraceChaser `--final-pcm-window` (pcm.s16, frames.tsv, metadata.json)
  --start-row    BK2 row that is the declared window origin (e.g. a trace segment's bk2_frame_offset)
  --engine-dir   output of `GameplayCaptureTool --audio --native-pcm` started at that row
                 (native-pcm.s16, native-pcm-frames.tsv)
  --rows         optional positive intended presentation count; required to detect a capture
                 that ended early with internally consistent PCM and TSV files
With --rows, the engine must contain exactly that many presentation rows, selecting native
rows [--start-row, --start-row + --rows). Otherwise the engine presentation row count selects
an inferred observed span: a match proves only that span, never the intended run or whole movie.
Native rows after that span are allowed. Both tables and the native v2 completion marker must
agree with their closed PCM files. Exit 0 means exact equality, 3 means a valid mismatch, and
2 means invalid or incomplete capture evidence. No shift, tolerance, gain or normalisation is
applied; the onset and level figures are descriptive only.
PCM files are read once in bounded chunks; only the per-row tables are retained.
Originating task: OpenGGF round-2 lane pcm-output, 2026-10-16.
Complete-window integrity: audio evidence gap closure, 2026-10-22.
"""
import argparse
import array
import hashlib
import json
import math
import re
import sys
from pathlib import Path


SCHEMA = 'openggf.native-pcm-window-comparison.v1'
NATIVE_COLUMNS = ['row', 'lag', 'stereo_frames', 'cumulative_stereo_frames']
ENGINE_COLUMNS = ['frame', 'stereo_frames', 'cumulative_stereo_frames', 'sessions']
PCM_CHUNK_FRAMES = 16384
RMS_PROFILE_FRAMES = 736
RMS_PROFILE_CHUNKS = 12


class PcmReader:
    """Count and optionally hash every byte, including unselected native rows."""

    def __init__(self, stream, name, *, hash_pcm=False):
        self.stream = stream
        self.name = name
        self.byte_count = 0
        self.digest = hashlib.sha256() if hash_pcm else None

    def read(self, count):
        data = self.stream.read(count)
        self.byte_count += len(data)
        if self.digest is not None:
            self.digest.update(data)
        return data

    def read_frames(self, count):
        data = self.read(count * 4)
        if len(data) != count * 4:
            raise ValueError(f'{self.name}: PCM ended before its declared stereo frames')
        return data

    def skip_frames(self, count):
        while count:
            frames = min(count, PCM_CHUNK_FRAMES)
            self.read_frames(frames)
            count -= frames

    def finish(self):
        while self.read(PCM_CHUNK_FRAMES * 4):
            pass
        if self.byte_count % 4:
            raise ValueError(f'{self.name}: torn stereo s16 frame ({self.byte_count} bytes)')


def samples(data):
    result = array.array('h')
    result.frombytes(data)
    if sys.byteorder != 'little':
        result.byteswap()
    return result


def read_tsv(data, name, columns):
    if not data.endswith(b'\n'):
        raise ValueError(f'{name}: missing final newline')
    lines = data.decode('utf-8').splitlines()
    if not lines or lines[0].split('\t') != columns:
        raise ValueError(f'{name}: expected columns {columns}')
    rows = []
    cumulative = 0
    for index, line in enumerate(lines[1:]):
        fields = line.split('\t')
        if len(fields) != len(columns) or any(not re.fullmatch(r'0|[1-9][0-9]*', value)
                                               for value in fields):
            raise ValueError(f'{name}: malformed row {index}')
        row = dict(zip(columns, map(int, fields)))
        if row[columns[0]] != index:
            raise ValueError(f'{name}: coordinate {row[columns[0]]}, expected {index}')
        cumulative += row['stereo_frames']
        if row['cumulative_stereo_frames'] != cumulative:
            raise ValueError(f'{name}: cumulative sample count differs at row {index}')
        if 'lag' in row and row['lag'] not in (0, 1):
            raise ValueError(f'{name}: invalid lag flag at row {index}')
        rows.append(row)
    if not rows:
        raise ValueError(f'{name}: no completed presentation rows')
    return rows


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'metadata.json: duplicate field {key}')
        result[key] = value
    return result


def metadata_integer(metadata, key, minimum=0):
    value = metadata.get(key)
    if type(value) is not int or value < minimum:
        raise ValueError(f'metadata.json: {key} must be an integer >= {minimum}')
    return value


def require_count(metadata, key, expected):
    if metadata_integer(metadata, key) != expected:
        raise ValueError(f'metadata.json: {key} differs from capture ({expected})')


def require_digest(metadata, key, actual_digest):
    digest = metadata.get(key)
    if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
        raise ValueError(f'metadata.json: malformed {key}')
    if digest != actual_digest:
        raise ValueError(f'metadata.json: {key} differs from capture')


def validate_native_metadata(path, pcm_bytes, pcm_digest, table, rows):
    metadata = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
    if not isinstance(metadata, dict) or metadata.get('schema') != 'openggf.final-pcm-window.v2':
        raise ValueError('metadata.json: a final-pcm-window.v2 completion marker is required')
    require_count(metadata, 'frames', len(rows))
    require_count(metadata, 'lag_frames', sum(row['lag'] for row in rows))
    require_count(metadata, 'stereo_frames', pcm_bytes // 4)
    require_count(metadata, 'pcm_bytes', pcm_bytes)
    require_count(metadata, 'sample_rate', 44100)
    if metadata_integer(metadata, 'movie_frame_count', 1) < len(rows):
        raise ValueError('metadata.json: capture exceeds movie_frame_count')
    require_digest(metadata, 'pcm_sha256', pcm_digest)
    require_digest(metadata, 'frames_tsv_sha256', hashlib.sha256(table).hexdigest())
    return metadata


def require_pcm_count(name, pcm_bytes, rows):
    expected = rows[-1]['cumulative_stereo_frames']
    if pcm_bytes // 4 != expected:
        raise ValueError(f'{name}: PCM has {pcm_bytes // 4} stereo frames, table declares {expected}')


def rms(sum_squares, sample_count):
    return math.sqrt(sum_squares / sample_count) if sample_count else 0.0


def first_nonzero(samples):
    return next((i // 2 for i, s in enumerate(samples) if s), None)


def compare_pcm(native_path, engine_path, origin, native_frames, engine_frames):
    """Compare the common selected span while authenticating the complete native stream.

    Integer sums of squares preserve the previous whole-array RMS calculation:
    there is one division and square root after all chunks, with no accumulated
    floating-point rounding. Profile chunks retain their fixed 736-frame origin.
    """
    frames = min(native_frames, engine_frames)
    first = None
    first_values = None
    equal_frames = 0
    first_nonzero_frames = [None, None]
    sum_squares = [0, 0]
    profile_squares = [[0, 0] for _ in range(min(RMS_PROFILE_CHUNKS,
                                               frames // RMS_PROFILE_FRAMES))]
    with native_path.open('rb') as native_file, engine_path.open('rb') as engine_file:
        native_reader = PcmReader(native_file, native_path.name, hash_pcm=True)
        engine_reader = PcmReader(engine_file, engine_path.name)
        native_reader.skip_frames(origin)
        offset = 0
        while offset < frames:
            count = min(PCM_CHUNK_FRAMES, frames - offset)
            native = samples(native_reader.read_frames(count))
            engine = samples(engine_reader.read_frames(count))
            for i in range(count):
                index = 2 * i
                if native[index] == engine[index] and native[index + 1] == engine[index + 1]:
                    equal_frames += 1
                elif first is None:
                    first = offset + i
                    first_values = {'native': [native[index], native[index + 1]],
                                    'engine': [engine[index], engine[index + 1]]}
            for side, values in enumerate((native, engine)):
                sum_squares[side] += sum(value * value for value in values)
                if first_nonzero_frames[side] is None:
                    nonzero = first_nonzero(values)
                    if nonzero is not None:
                        first_nonzero_frames[side] = offset + nonzero
                for k in range(offset // RMS_PROFILE_FRAMES, len(profile_squares)):
                    start = max(offset, k * RMS_PROFILE_FRAMES)
                    end = min(offset + count, (k + 1) * RMS_PROFILE_FRAMES)
                    if end <= start:
                        break
                    selected = values[2 * (start - offset):2 * (end - offset)]
                    profile_squares[k][side] += sum(value * value for value in selected)
            offset += count
        native_reader.finish()
        engine_reader.finish()
    return {
        'native_pcm_bytes': native_reader.byte_count,
        'native_pcm_digest': native_reader.digest.hexdigest(),
        'engine_pcm_bytes': engine_reader.byte_count,
        'compared_stereo_frames': frames,
        'first_different_stereo_frame': first,
        'first_different_values': first_values,
        'equal_stereo_frames': equal_frames,
        'descriptive': {
            'native_first_nonzero_stereo_frame': first_nonzero_frames[0],
            'engine_first_nonzero_stereo_frame': first_nonzero_frames[1],
            'native_rms': round(rms(sum_squares[0], frames * 2), 1),
            'engine_rms': round(rms(sum_squares[1], frames * 2), 1),
            'per_736_chunk_rms': [
                {'chunk': k, 'native_rms': round(rms(squares[0], RMS_PROFILE_FRAMES * 2), 1),
                 'engine_rms': round(rms(squares[1], RMS_PROFILE_FRAMES * 2), 1)}
                for k, squares in enumerate(profile_squares)],
        },
    }


def compare(native_dir, engine_dir, start_row, expected_rows=None):
    if expected_rows is not None and (type(expected_rows) is not int or expected_rows <= 0):
        raise ValueError('requested rows must be a positive presentation count')
    native_table = (native_dir / 'frames.tsv').read_bytes()
    native_rows = read_tsv(native_table, 'frames.tsv', NATIVE_COLUMNS)
    engine_rows = read_tsv((engine_dir / 'native-pcm-frames.tsv').read_bytes(),
                           'native-pcm-frames.tsv', ENGINE_COLUMNS)
    if expected_rows is not None and len(engine_rows) != expected_rows:
        raise ValueError(f'engine capture has {len(engine_rows)} presentation rows, expected {expected_rows}')
    end_row = start_row + len(engine_rows)
    if start_row < 0 or start_row >= len(native_rows) or end_row > len(native_rows):
        raise ValueError(f'native capture does not cover selected rows [{start_row},{end_row})')
    selected_rows = native_rows[start_row:end_row]
    origin = native_rows[start_row - 1]['cumulative_stereo_frames'] if start_row else 0
    end = selected_rows[-1]['cumulative_stereo_frames']
    if end == origin:
        raise ValueError('selected native span contains no stereo frames')
    native_frames = end - origin
    engine_frames = engine_rows[-1]['cumulative_stereo_frames']
    pcm = compare_pcm(native_dir / 'pcm.s16', engine_dir / 'native-pcm.s16',
                      origin, native_frames, engine_frames)
    require_pcm_count('native capture', pcm['native_pcm_bytes'], native_rows)
    require_pcm_count('engine capture', pcm['engine_pcm_bytes'], engine_rows)
    metadata = validate_native_metadata(native_dir / 'metadata.json', pcm['native_pcm_bytes'],
                                        pcm['native_pcm_digest'], native_table, native_rows)
    sessions = sorted({row['sessions'] for row in engine_rows})
    shape = next(({'frame': index, 'native_row': start_row + index,
                   'native_stereo_frames': reference['stereo_frames'],
                   'engine_stereo_frames': actual['stereo_frames']}
                  for index, (reference, actual) in enumerate(zip(selected_rows, engine_rows))
                  if reference['stereo_frames'] != actual['stereo_frames']), None)
    mismatches = []
    if native_frames != engine_frames:
        mismatches.append('SAMPLE_COUNT_MISMATCH')
    if shape is not None:
        mismatches.append('FRAME_SHAPE_MISMATCH')
    if pcm['first_different_stereo_frame'] is not None:
        mismatches.append('SAMPLE_MISMATCH')
    summary = {
        'schema': SCHEMA,
        'native_metadata': metadata,
        'start_row': start_row,
        'requested_rows': expected_rows,
        'window_scope': 'declared_window' if expected_rows is not None else 'inferred_observed_span',
        'selected_native_end_row': end_row,
        'native_remaining_presentation_frames': len(native_rows) - end_row,
        'native_origin_stereo_frame': origin,
        'native_selected_stereo_frames': native_frames,
        'compared_stereo_frames': pcm['compared_stereo_frames'],
        'engine_stereo_frames': engine_frames,
        'engine_presentation_frames': len(engine_rows),
        'engine_stage_sessions': sessions,
        'verdict': 'DIFFERENT' if mismatches else 'MATCH',
        'mismatch_kinds': mismatches,
        'first_different_presentation_frame': shape,
        'first_different_stereo_frame': pcm['first_different_stereo_frame'],
        'first_different_values': pcm['first_different_values'],
        'equal_stereo_frames': pcm['equal_stereo_frames'],
        'descriptive': pcm['descriptive'],
    }
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--start-row', type=int, required=True)
    parser.add_argument('--engine-dir', type=Path, required=True)
    parser.add_argument('--rows', type=int,
                        help='intended presentation count; otherwise scope is the inferred observed span')
    parser.add_argument('--out', type=Path)
    args = parser.parse_args(argv)
    try:
        summary = compare(args.native_dir, args.engine_dir, args.start_row, args.rows)
        status = 0 if summary['verdict'] == 'MATCH' else 3
    except (OSError, ValueError) as failure:
        summary = {'schema': SCHEMA, 'start_row': args.start_row, 'requested_rows': args.rows,
                   'verdict': 'INVALID_CAPTURE', 'error': str(failure)}
        status = 2
    text = json.dumps(summary, indent=2)
    if args.out:
        try:
            args.out.write_text(text + '\n', encoding='utf-8')
        except OSError as failure:
            print(f'cannot write comparison report: {failure}', file=sys.stderr)
            return 2
    print(text)
    return status


if __name__ == '__main__':
    sys.exit(main())
