#!/usr/bin/env python3
"""Generated capture controls for strict native/engine PCM window comparison.

No ROM, emulator, or captured game asset is needed. Origin: audio evidence gap
closure, 2026-10-22. Run with python3 -m unittest discover -s tools/audio/tests
-p test_compare_native_pcm_window.py.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / 'compare_native_pcm_window.py'
SPEC = importlib.util.spec_from_file_location('compare_native_pcm_window', SCRIPT)
COMPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPARE)


class NativePcmWindowContract(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.native = self.root / 'native'
        self.engine = self.root / 'engine'
        self.native.mkdir()
        self.engine.mkdir()
        self.rows = [[(1, -1), (2, -2)], [(3, -3)], [(4, -4), (5, -5)]]
        self.write_native(self.rows)
        self.write_engine(self.rows)

    @staticmethod
    def pcm(rows):
        samples = [sample for row in rows for pair in row for sample in pair]
        return struct.pack('<%dh' % len(samples), *samples)

    def write_native(self, rows):
        pcm = self.pcm(rows)
        (self.native / 'pcm.s16').write_bytes(pcm)
        lines = ['row\tlag\tstereo_frames\tcumulative_stereo_frames']
        cumulative = 0
        for index, row in enumerate(rows):
            cumulative += len(row)
            lines.append(f'{index}\t0\t{len(row)}\t{cumulative}')
        table = ('\n'.join(lines) + '\n').encode()
        (self.native / 'frames.tsv').write_bytes(table)
        self.metadata = {
            'schema': 'openggf.final-pcm-window.v2',
            'rom_sha1': '1' * 40,
            'movie_sha256': '2' * 64,
            'movie_frame_count': len(rows) + 10,
            'frames': len(rows), 'lag_frames': 0,
            'genesis_fm_sound_chip': 'Nuked_YM2612', 'filter': 'None',
            'low_pass_range': 26214, 'low_freq': 880, 'high_freq': 5000,
            'gains_x100': [100, 100, 100], 'sample_rate': 44100,
            'stereo_frames': cumulative, 'pcm_bytes': len(pcm),
            'pcm_sha256': hashlib.sha256(pcm).hexdigest(),
            'frames_tsv_sha256': hashlib.sha256(table).hexdigest(),
            'core_file': 'gpgx.wbx.zst', 'core_size': 400161,
            'core_sha256': '3' * 64, 'bizhawk_managed_version': '2.11.0.0',
            'bizhawk_emulation_cores_version': '2.11.0.0',
            'bizhawk_emulation_cores_sha256': '4' * 64,
        }
        self.write_metadata()

    def write_metadata(self):
        (self.native / 'metadata.json').write_text(json.dumps(self.metadata))

    def write_engine(self, rows, sessions=None):
        (self.engine / 'native-pcm.s16').write_bytes(self.pcm(rows))
        lines = ['frame\tstereo_frames\tcumulative_stereo_frames\tsessions']
        cumulative = 0
        for index, row in enumerate(rows):
            cumulative += len(row)
            session_count = sessions[index] if sessions else 1
            lines.append(f'{index}\t{len(row)}\t{cumulative}\t{session_count}')
        (self.engine / 'native-pcm-frames.tsv').write_text('\n'.join(lines) + '\n')

    def arguments(self, start=0):
        return ['--native-dir', str(self.native), '--start-row', str(start),
                '--engine-dir', str(self.engine)]

    def compare(self, start=0):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = COMPARE.main(self.arguments(start))
        return status, json.loads(output.getvalue())

    def assert_invalid(self):
        status, report = self.compare()
        self.assertEqual(2, status, report)
        self.assertEqual('INVALID_CAPTURE', report['verdict'])
        self.assertTrue(report['error'])

    def test_complete_equal_window_matches(self):
        status, report = self.compare()
        self.assertEqual(0, status)
        self.assertEqual('MATCH', report['verdict'])
        self.assertEqual(5, report['compared_stereo_frames'])
        self.assertEqual(3, report['selected_native_end_row'])

    def test_extra_native_rows_are_valid_after_the_selected_engine_span(self):
        self.write_engine(self.rows[1:2])
        status, report = self.compare(start=1)
        self.assertEqual(0, status)
        self.assertEqual('MATCH', report['verdict'])
        self.assertEqual(2, report['native_origin_stereo_frame'])
        self.assertEqual(2, report['selected_native_end_row'])
        self.assertEqual(1, report['engine_presentation_frames'])
        self.assertEqual(1, report['native_remaining_presentation_frames'])
        self.assertIsNone(report['requested_rows'])
        self.assertEqual('inferred_observed_span', report['window_scope'])

    def test_declared_complete_window_matches_with_its_requested_scope(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = COMPARE.main(self.arguments() + ['--rows', '3'])
        report = json.loads(output.getvalue())
        self.assertEqual(0, status)
        self.assertEqual('MATCH', report['verdict'])
        self.assertEqual(3, report['requested_rows'])
        self.assertEqual(3, report['selected_native_end_row'])
        self.assertEqual('declared_window', report['window_scope'])

    def test_self_consistent_early_engine_end_cannot_shorten_a_declared_600_row_window(self):
        rows = [[(index, -index)] for index in range(600)]
        self.write_native(rows)
        self.write_engine(rows[:10])
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = COMPARE.main(self.arguments() + ['--rows', '600'])
        report = json.loads(output.getvalue())
        self.assertEqual(2, status)
        self.assertEqual('INVALID_CAPTURE', report['verdict'])
        self.assertEqual(600, report['requested_rows'])
        self.assertIn('10 presentation rows, expected 600', report['error'])

    def test_declared_window_count_must_be_positive(self):
        for rows in [0, -1]:
            with self.subTest(rows=rows):
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    status = COMPARE.main(self.arguments() + ['--rows', str(rows)])
                report = json.loads(output.getvalue())
                self.assertEqual(2, status)
                self.assertEqual('INVALID_CAPTURE', report['verdict'])
                self.assertEqual(rows, report['requested_rows'])

    def test_equal_shorter_pcm_prefix_with_a_longer_table_is_invalid(self):
        (self.engine / 'native-pcm.s16').write_bytes(self.pcm(self.rows[:1]))
        self.assert_invalid()

    def test_complete_shorter_pcm_is_a_length_mismatch(self):
        self.write_engine([self.rows[0], [], []])
        status, report = self.compare()
        self.assertEqual(3, status)
        self.assertEqual('DIFFERENT', report['verdict'])
        self.assertIn('SAMPLE_COUNT_MISMATCH', report['mismatch_kinds'])
        self.assertIsNone(report['first_different_stereo_frame'])
        self.assertEqual(5, report['native_selected_stereo_frames'])
        self.assertEqual(2, report['engine_stereo_frames'])

    def test_equal_pcm_with_different_row_lengths_is_a_shape_mismatch(self):
        self.write_engine([self.rows[0][:1], self.rows[0][1:] + self.rows[1], self.rows[2]])
        status, report = self.compare()
        self.assertEqual(3, status)
        self.assertEqual('DIFFERENT', report['verdict'])
        self.assertEqual(['FRAME_SHAPE_MISMATCH'], report['mismatch_kinds'])
        self.assertEqual(0, report['first_different_presentation_frame']['frame'])

    def test_changed_sample_is_reported_without_alignment(self):
        changed = [list(row) for row in self.rows]
        changed[1][0] = (300, -3)
        self.write_engine(changed)
        status, report = self.compare()
        self.assertEqual(3, status)
        self.assertEqual(['SAMPLE_MISMATCH'], report['mismatch_kinds'])
        self.assertEqual(2, report['first_different_stereo_frame'])
        self.assertEqual({'native': [3, -3], 'engine': [300, -3]},
                         report['first_different_values'])

    def test_multiple_natural_audio_sessions_are_valid(self):
        self.write_engine(self.rows, sessions=[0, 1, 2])
        status, report = self.compare()
        self.assertEqual(0, status)
        self.assertEqual([0, 1, 2], report['engine_stage_sessions'])

    def test_native_reference_must_cover_the_whole_selected_row_span(self):
        self.write_engine(self.rows + [[(6, -6)]])
        self.assert_invalid()

    def test_discontinuous_engine_coordinates_are_invalid(self):
        path = self.engine / 'native-pcm-frames.tsv'
        path.write_text(path.read_text().replace('1\t1\t3\t1', '2\t1\t3\t1'))
        self.assert_invalid()

    def test_discontinuous_native_coordinates_are_invalid_even_with_valid_digest(self):
        path = self.native / 'frames.tsv'
        path.write_text(path.read_text().replace('1\t0\t1\t3', '2\t0\t1\t3'))
        self.metadata['frames_tsv_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_metadata()
        self.assert_invalid()

    def test_wrong_cumulative_count_is_invalid(self):
        path = self.engine / 'native-pcm-frames.tsv'
        path.write_text(path.read_text().replace('1\t1\t3\t1', '1\t1\t4\t1'))
        self.assert_invalid()

    def test_wrong_native_pcm_digest_is_invalid(self):
        self.metadata['pcm_sha256'] = '0' * 64
        self.write_metadata()
        self.assert_invalid()

    def test_wrong_native_table_digest_is_invalid(self):
        self.metadata['frames_tsv_sha256'] = '0' * 64
        self.write_metadata()
        self.assert_invalid()

    def test_native_completion_marker_is_required(self):
        (self.native / 'metadata.json').unlink()
        self.assert_invalid()

    def test_old_metadata_without_authenticated_completion_is_invalid(self):
        self.metadata['schema'] = 'openggf.final-pcm-window.v1'
        self.write_metadata()
        self.assert_invalid()

    def test_native_metadata_counts_must_match_closed_files(self):
        for key in ['frames', 'stereo_frames', 'pcm_bytes', 'lag_frames']:
            with self.subTest(key=key):
                self.metadata[key] += 1
                self.write_metadata()
                self.assert_invalid()
                self.metadata[key] -= 1

    def test_duplicate_or_non_integral_metadata_counts_are_invalid(self):
        path = self.native / 'metadata.json'
        text = path.read_text()
        path.write_text(text[:-1] + ', "frames": 3}')
        self.assert_invalid()
        for value in [True, 3.0, '3']:
            with self.subTest(value=value):
                self.metadata['frames'] = value
                self.write_metadata()
                self.assert_invalid()

    def test_native_cumulative_count_must_match_even_with_valid_table_digest(self):
        path = self.native / 'frames.tsv'
        path.write_text(path.read_text().replace('1\t0\t1\t3', '1\t0\t1\t4'))
        self.metadata['frames_tsv_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_metadata()
        self.assert_invalid()

    def test_unfinished_or_empty_engine_table_is_invalid(self):
        path = self.engine / 'native-pcm-frames.tsv'
        original = path.read_text()
        for text in [original.rstrip('\n'), original.splitlines()[0] + '\n']:
            with self.subTest(table=text):
                path.write_text(text)
                self.assert_invalid()

    def test_negative_declared_start_row_is_invalid(self):
        status, report = self.compare(start=-1)
        self.assertEqual(2, status)
        self.assertEqual('INVALID_CAPTURE', report['verdict'])

    def test_output_file_retains_the_same_report_and_status(self):
        self.write_engine([[], [], []])
        output = io.StringIO()
        report_path = self.root / 'comparison.json'
        with contextlib.redirect_stdout(output):
            status = COMPARE.main(self.arguments() + ['--out', str(report_path)])
        self.assertEqual(3, status)
        self.assertEqual(json.loads(output.getvalue()), json.loads(report_path.read_text()))

    def test_torn_sample_or_stereo_frame_is_invalid(self):
        for member in [self.native / 'pcm.s16', self.engine / 'native-pcm.s16']:
            original = member.read_bytes()
            for amount in [1, 2, 3]:
                with self.subTest(member=member.name, removed=amount):
                    member.write_bytes(original[:-amount])
                    self.assert_invalid()
            member.write_bytes(original)

    def test_malformed_table_shape_is_invalid(self):
        path = self.engine / 'native-pcm-frames.tsv'
        original = path.read_text()
        for replacement in [original.replace('\tsessions', '\tsessions\tsessions'),
                            original.replace('0\t2\t2\t1', '0\t2\t2'),
                            original.replace('0\t2\t2\t1', '0\t-2\t2\t1')]:
            with self.subTest(table=replacement):
                path.write_text(replacement)
                self.assert_invalid()

    def test_process_exit_status_distinguishes_match_mismatch_and_invalid(self):
        def invoke():
            return subprocess.run([sys.executable, str(SCRIPT), *self.arguments()],
                                  capture_output=True, text=True, check=False)
        self.assertEqual(0, invoke().returncode)
        self.write_engine([[(9, -9)], self.rows[1], self.rows[2]])
        mismatch = invoke()
        self.assertEqual(3, mismatch.returncode, mismatch.stderr)
        (self.native / 'metadata.json').unlink()
        invalid = invoke()
        self.assertEqual(2, invalid.returncode, invalid.stderr)
        self.assertEqual('INVALID_CAPTURE', json.loads(invalid.stdout)['verdict'])

    def test_small_fixture_retains_its_complete_report(self):
        self.assertEqual({
            'schema': 'openggf.native-pcm-window-comparison.v1',
            'native_metadata': self.metadata,
            'start_row': 0, 'requested_rows': 3, 'window_scope': 'declared_window',
            'selected_native_end_row': 3, 'native_remaining_presentation_frames': 0,
            'native_origin_stereo_frame': 0, 'native_selected_stereo_frames': 5,
            'compared_stereo_frames': 5, 'engine_stereo_frames': 5,
            'engine_presentation_frames': 3, 'engine_stage_sessions': [1],
            'verdict': 'MATCH', 'mismatch_kinds': [],
            'first_different_presentation_frame': None,
            'first_different_stereo_frame': None, 'first_different_values': None,
            'equal_stereo_frames': 5,
            'descriptive': {
                'native_first_nonzero_stereo_frame': 0,
                'engine_first_nonzero_stereo_frame': 0,
                'native_rms': 3.3, 'engine_rms': 3.3, 'per_736_chunk_rms': [],
            },
        }, COMPARE.compare(self.native, self.engine, 0, 3))

    def test_chunk_boundary_and_final_right_channel_differences_keep_exact_offsets(self):
        count = COMPARE.PCM_CHUNK_FRAMES + 5
        reference = [(0, 0)] * (count - 1) + [(0, -7)]
        self.write_native([reference])
        for changed_frame in [COMPARE.PCM_CHUNK_FRAMES - 1, COMPARE.PCM_CHUNK_FRAMES, count - 1]:
            with self.subTest(changed_frame=changed_frame):
                actual = reference.copy()
                actual[changed_frame] = (0, 6)
                self.write_engine([actual])
                status, report = self.compare()
                self.assertEqual(3, status, report)
                self.assertEqual(['SAMPLE_MISMATCH'], report['mismatch_kinds'])
                self.assertEqual(changed_frame, report['first_different_stereo_frame'])
                self.assertEqual({'native': list(reference[changed_frame]), 'engine': [0, 6]},
                                 report['first_different_values'])
                self.assertEqual(count - 1, report['equal_stereo_frames'])
                self.assertEqual(count - 1, report['descriptive']['native_first_nonzero_stereo_frame'])
                self.assertEqual(changed_frame, report['descriptive']['engine_first_nonzero_stereo_frame'])

    @staticmethod
    def descriptive_oracle(native, engine):
        """Independent whole-vector arithmetic, restricted to small generated tests."""
        count = min(len(native), len(engine))
        native = native[:count]
        engine = engine[:count]

        def level(pairs):
            values = [value for pair in pairs for value in pair]
            return round(math.sqrt(sum(value ** 2 for value in values) / len(values)), 1) if values else 0.0

        return {
            'native_first_nonzero_stereo_frame': next((i for i, pair in enumerate(native)
                                                       if pair != (0, 0)), None),
            'engine_first_nonzero_stereo_frame': next((i for i, pair in enumerate(engine)
                                                       if pair != (0, 0)), None),
            'native_rms': level(native), 'engine_rms': level(engine),
            'per_736_chunk_rms': [
                {'chunk': k, 'native_rms': level(native[k * 736:(k + 1) * 736]),
                 'engine_rms': level(engine[k * 736:(k + 1) * 736])}
                for k in range(min(12, count // 736))],
        }

    def test_integer_rms_and_profile_match_independent_vectors_at_every_chunk_size(self):
        reference = [(0, 0)] * 40 + [
            (((index % 101) - 50) * 400, 32767 if index % 2 else -32768)
            for index in range(12 * 736 + 13 - 40)]
        actual = reference.copy()
        actual[736] = (-32768, 32767)
        actual[-1] = (32767, -32768)
        self.write_native([reference])
        self.write_engine([actual])
        expected = self.descriptive_oracle(reference, actual)
        first_report = None
        for chunk_frames in [1, 113, 735, 736, 737, 16384]:
            with self.subTest(chunk_frames=chunk_frames), \
                    mock.patch.object(COMPARE, 'PCM_CHUNK_FRAMES', chunk_frames):
                status, report = self.compare()
                self.assertEqual(3, status, report)
                self.assertEqual(expected, report['descriptive'])
                self.assertEqual(736, report['first_different_stereo_frame'])
                self.assertEqual(len(reference) - 2, report['equal_stereo_frames'])
                if first_report is None:
                    first_report = report
                else:
                    self.assertEqual(first_report, report)

    def test_unselected_native_prefix_and_tail_still_require_the_complete_digest(self):
        count = COMPARE.PCM_CHUNK_FRAMES
        rows = [[(10, -10)] * (count + 1), self.rows[0], [(20, -20)] * (count + 3)]
        self.write_native(rows)
        self.write_engine(rows[1:2])
        status, report = self.compare(start=1)
        self.assertEqual(0, status, report)
        self.assertEqual(count + 1, report['native_origin_stereo_frame'])
        path = self.native / 'pcm.s16'
        original = path.read_bytes()
        for byte_offset in [0, len(original) - 1]:
            with self.subTest(byte_offset=byte_offset):
                changed = bytearray(original)
                changed[byte_offset] ^= 1
                path.write_bytes(changed)
                status, report = self.compare(start=1)
                self.assertEqual(2, status, report)
                self.assertIn('pcm_sha256 differs from capture', report['error'])
        path.write_bytes(original)

    def test_complete_length_differences_after_a_chunk_never_match_the_common_prefix(self):
        count = COMPARE.PCM_CHUNK_FRAMES + 3
        pairs = [(8, -9)] * count
        for native_count, engine_count in [(count, count - 1), (count - 1, count)]:
            with self.subTest(native=native_count, engine=engine_count):
                self.write_native([pairs[:native_count]])
                self.write_engine([pairs[:engine_count]])
                status, report = self.compare()
                self.assertEqual(3, status, report)
                self.assertEqual(['SAMPLE_COUNT_MISMATCH', 'FRAME_SHAPE_MISMATCH'],
                                 report['mismatch_kinds'])
                self.assertIsNone(report['first_different_stereo_frame'])
                self.assertEqual(count - 1, report['compared_stereo_frames'])
                self.assertEqual(count - 1, report['equal_stereo_frames'])
                self.assertEqual(self.descriptive_oracle(pairs[:native_count], pairs[:engine_count]),
                                 report['descriptive'])

    def test_truncated_files_after_complete_chunks_are_invalid(self):
        pairs = [(30, -31)] * (2 * COMPARE.PCM_CHUNK_FRAMES + 3)
        self.write_native([pairs])
        self.write_engine([pairs])
        for path in [self.native / 'pcm.s16', self.engine / 'native-pcm.s16']:
            original = path.read_bytes()
            for removed in [1, 4, 4 * (COMPARE.PCM_CHUNK_FRAMES + 1)]:
                with self.subTest(member=path.name, removed=removed):
                    path.write_bytes(original[:-removed])
                    self.assert_invalid()
            path.write_bytes(original)

    def test_self_consistent_pcm_metadata_cannot_hide_a_missing_declared_native_frame(self):
        pairs = [(30, -31)] * (COMPARE.PCM_CHUNK_FRAMES + 3)
        self.write_native([pairs])
        self.write_engine([pairs])
        shortened = self.pcm([pairs[:-1]])
        (self.native / 'pcm.s16').write_bytes(shortened)
        self.metadata.update(pcm_sha256=hashlib.sha256(shortened).hexdigest(),
                             pcm_bytes=len(shortened), stereo_frames=len(pairs) - 1)
        self.write_metadata()
        self.assert_invalid()

    def test_unselected_tail_growth_cannot_hide_behind_a_selected_match(self):
        self.write_engine(self.rows[:1])
        path = self.native / 'pcm.s16'
        original = path.read_bytes()
        for extra in [b'\x00', b'\x00\x00\x00\x00']:
            with self.subTest(extra_bytes=len(extra)):
                path.write_bytes(original + extra)
                self.assert_invalid()

    def test_pcm_is_read_once_sequentially_using_only_bounded_requests(self):
        count = COMPARE.PCM_CHUNK_FRAMES
        rows = [[(10, -10)] * (count + 1), [(2, -2)] * (count + 5),
                [(20, -20)] * (count + 3)]
        self.write_native(rows)
        self.write_engine(rows[1:2])
        original_open = Path.open
        opened = {}
        consumed = {}
        maximum_request = count * 4

        class BoundedReader:
            def __init__(self, stream, path):
                self.stream = stream
                self.path = path

            def __enter__(self):
                return self

            def __exit__(self, *args):
                self.stream.close()

            def read(self, size=-1):
                if not 0 < size <= maximum_request:
                    raise AssertionError(f'unbounded PCM read: {size}')
                data = self.stream.read(size)
                consumed[self.path] = consumed.get(self.path, 0) + len(data)
                return data

            def seek(self, *args):
                raise AssertionError('PCM must be authenticated and compared in one sequential pass')

        def bounded_open(path, *args, **kwargs):
            stream = original_open(path, *args, **kwargs)
            if path.suffix == '.s16':
                opened[path] = opened.get(path, 0) + 1
                return BoundedReader(stream, path)
            return stream

        with mock.patch.object(Path, 'open', bounded_open):
            status, report = self.compare(start=1)
        self.assertEqual(0, status, report)
        for path in [self.native / 'pcm.s16', self.engine / 'native-pcm.s16']:
            self.assertEqual(1, opened[path])
            self.assertEqual(path.stat().st_size, consumed[path])

    def test_empty_common_pcm_span_retains_zero_descriptive_statistics(self):
        self.write_engine([[], [], []])
        status, report = self.compare()
        self.assertEqual(3, status, report)
        self.assertEqual(0, report['compared_stereo_frames'])
        self.assertEqual(0, report['equal_stereo_frames'])
        self.assertEqual(self.descriptive_oracle(self.rows[0], []), report['descriptive'])


if __name__ == '__main__':
    unittest.main()
