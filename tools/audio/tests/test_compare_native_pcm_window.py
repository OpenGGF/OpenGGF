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
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest


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


if __name__ == '__main__':
    unittest.main()
