"""Adversarial native trace producer checks without running an engine or Maven."""
from dataclasses import replace
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import report as core
import measure_native as native


class NativeTraceProducer(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.raw = self.root / 'target/parity-native/run'
        self.surefire = self.raw / 'surefire'
        self.trace = self.raw / 'trace'
        self.surefire.mkdir(parents=True)
        (self.trace / 'trace').mkdir(parents=True)
        definition = {
            'id': 's1.ghz1.native-gate', 'game': 's1', 'domain': 'gameplay',
            'subject': 'Recorded GHZ1 player comparison', 'context': {'viewport': 320},
            'contract': 'Complete the existing exact replay gate.',
            'reference': {'rom_sha1': core.ROMS['s1'], 'citation': 'Original recorded movie.'},
            'required_cases': ['complete-replay'],
            'case_contracts': {'complete-replay': {'expected': 1,
                'unit': native.UNIT, 'evidence_kinds': ['native-state']}}}
        self.obligation = core.parse_obligation(definition)
        self.gate = native.TraceGate(self.obligation, 'com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay',
            'replayMatchesTrace', 'src/test/resources/traces/s1/fixture',
            'src/test/resources/traces/s1/fixture/movie.bk2', 'trace', 'single', 's1_ghz1', 12)
        self.started = time.time_ns()
        self.payload = {
            'error_count': 0, 'warning_count': 0, 'bootstrap_error_count': 0,
            'bootstrap_warning_count': 0, 'total_frames': 9,
            'verification_groups': {'physics': {'error_count': 0, 'warning_count': 0},
                                    'animation': {'error_count': 0, 'warning_count': 0}},
            'replay_gate': {'schema': 1, 'comparison_gate_reached': True,
                            'planned_trace_rows': 12, 'verification': 'all'}}
        self.write_result()

    def write_result(self, failure=False, skipped=False):
        name, owner = native.report_owner(self.gate)
        path = self.trace / 'trace' / name
        path.write_text(json.dumps(self.payload))
        Path(str(path) + '.owner.json').write_text(json.dumps({
            'logical_key': self.gate.logical_key, 'owner_key': owner,
            'physical_path': str(path)}))
        child = '<failure message="native mismatch"/>' if failure else '<skipped/>' if skipped else ''
        (self.surefire / ('TEST-' + self.gate.class_name + '.xml')).write_text(
            f'<testsuite name="{self.gate.class_name}" tests="1" failures="{int(failure)}" '
            f'errors="0" skipped="{int(skipped)}"><testcase classname="{self.gate.class_name}" '
            f'name="replayMatchesTrace">{child}</testcase></testsuite>')
        result = 'FAILURE' if failure else 'SUCCESS'
        self.log = self.raw / 'maven.log'
        self.log.write_text(
            f'[INFO] Tests run: 1, Failures: {int(failure)}, Errors: 0, Skipped: {int(skipped)}, '
            f'Time elapsed: 1 s -- in {self.gate.class_name}\n'
            f'[INFO] Tests run: 1, Failures: {int(failure)}, Errors: 0, Skipped: {int(skipped)}\n'
            f'[INFO] BUILD {result}\n[INFO] Finished at: 2026-10-29T10:00:00Z\n')
        return path

    def collect(self, exit_code=0):
        return native.collect_results([self.gate], self.surefire, self.trace,
                                      self.log, exit_code, self.started)

    def test_complete_exact_gate_does_not_require_gameplay_comparison_count_equal_rows(self):
        row = self.collect()[0]
        self.assertEqual('exact', row['status'])
        self.assertEqual((1, 1, native.UNIT), (row['expected'], row['compared'], row['unit']))
        self.assertEqual(9, row['metrics']['gameplay_comparisons'])

    def test_completed_native_mismatch_is_observed_despite_junit_failure(self):
        self.payload['error_count'] = 2
        self.payload['verification_groups']['physics']['error_count'] = 2
        self.payload['verification_groups']['physics']['first_error'] = {
            'field': 'x', 'start_frame': 7, 'expected_at_start': 100, 'actual_at_start': 101}
        self.write_result(failure=True)
        row = self.collect(1)[0]
        self.assertEqual('mismatch', row['status'])
        self.assertEqual('first-divergence', row['causal_status'])
        self.assertEqual({'field': 'x', 'start_frame': 7, 'expected_at_start': 100, 'actual_at_start': 101},
                         row['diagnostics']['verification_groups']['physics']['first_error'])

    def test_clean_aborted_prefix_stays_incomplete(self):
        self.payload['replay_gate']['comparison_gate_reached'] = False
        self.write_result(failure=True)
        row = self.collect(1)[0]
        self.assertEqual('incomplete', row['status'])
        self.assertEqual(0, row['compared'])

    def test_complete_zero_difference_failure_is_not_a_native_mismatch(self):
        self.write_result(failure=True)
        self.assertEqual('incomplete', self.collect(1)[0]['status'])

    def test_warnings_and_missing_aux_are_reference_limited(self):
        for field in ('warnings', 'aux'):
            with self.subTest(field=field):
                if field == 'warnings':
                    self.payload['warning_count'] = 1
                    self.payload['verification_groups']['animation']['warning_count'] = 1
                else:
                    self.payload['missing_advertised_aux_schemas'] = ['native-owner']
                self.write_result(failure=True)
                self.assertEqual('reference-limited', self.collect(1)[0]['status'])

    def test_skipped_case_cannot_qualify(self):
        self.write_result(skipped=True)
        with self.assertRaises(ValueError):
            self.collect()

    def test_changed_owner_or_case_identity_is_rejected(self):
        path = self.write_result()
        sidecar = Path(str(path) + '.owner.json')
        owner = json.loads(sidecar.read_text())
        owner['owner_key'] = 'f' * 64
        sidecar.write_text(json.dumps(owner))
        with self.assertRaises(ValueError):
            self.collect()
        self.write_result()
        xml = next(self.surefire.glob('*.xml'))
        xml.write_text(xml.read_text().replace('name="replayMatchesTrace"', 'name="otherMethod"'))
        with self.assertRaises(ValueError):
            self.collect()

    def test_truncated_extent_or_missing_verification_group_is_rejected(self):
        for field in ('extent', 'groups', 'counts'):
            with self.subTest(field=field):
                original = json.loads(json.dumps(self.payload))
                if field == 'extent':
                    self.payload['replay_gate']['planned_trace_rows'] = 1
                elif field == 'groups':
                    del self.payload['verification_groups']['animation']
                else:
                    self.payload['verification_groups']['physics']['error_count'] = True
                self.write_result()
                with self.assertRaises(ValueError):
                    self.collect()
                self.payload = original

    def test_extra_or_stale_results_are_rejected(self):
        path = self.write_result()
        os.utime(path, ns=(self.started - 1, self.started - 1))
        with self.assertRaises(ValueError):
            self.collect()
        self.write_result()
        (self.trace / 'trace/extra.json').write_text('{}')
        with self.assertRaises(ValueError):
            self.collect()

    def test_summary_and_xml_verdict_must_agree(self):
        self.write_result(failure=True)
        with self.assertRaises(ValueError):
            self.collect(0)

    def test_process_environment_rejects_injected_java_and_diagnostic_options(self):
        for key in ('JAVA_TOOL_OPTIONS', 'MAVEN_ARGS', 'OGGF_SLOT_PROBE', 'JDK_JAVA_OPTIONS'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                native.checked_environment({key: '-Dtrace.frontier.stop=true'})
        allowed = native.checked_environment({'JAVA_HOME': '/jdk25', 'LUA_BIN': '/usr/bin/lua5.4',
                                              'OGGF_SCRATCH_ROOT': '/tmp/owned-scratch'})
        self.assertEqual('/jdk25', allowed['JAVA_HOME'])
        self.assertEqual('/tmp/owned-scratch', allowed['OGGF_SCRATCH_ROOT'])
        self.assertEqual('1', allowed['MAVEN_SKIP_RC'])

    def test_wrong_rom_is_rejected_before_a_native_run(self):
        rom = self.root / 'wrong.gen'
        rom.write_bytes(b'not the original')
        with self.assertRaisesRegex(ValueError, 'ROM'):
            native.verify_inputs(self.root, [self.gate], {'s1': rom})

    def fixture_inputs(self):
        rom = self.root / 'controlled.gen'
        rom.write_bytes(b'controlled original-ROM identity for parser tests')
        identity = hashlib.sha1(rom.read_bytes()).hexdigest()
        md5 = hashlib.md5(rom.read_bytes()).hexdigest()
        fixture = self.root / self.gate.fixture
        fixture.mkdir(parents=True, exist_ok=True)
        (fixture / 'metadata.json').write_text(json.dumps({'game': 's1', 'trace_schema': 5,
            'recorder': 'native-bizhawk-headless', 'trace_frame_count': 12, 'rom_checksum': md5}))
        with gzip.open(fixture / 'physics.csv.gz', 'wt') as stream:
            stream.write('frame,input\n' + ''.join(f'{i},00\n' for i in range(12)))
        with ZipFile(self.root / self.gate.bk2, 'w') as movie:
            movie.writestr('Header.txt', f'Core Genplus-gx\nemuVersion Version 2.11\nSHA1 {md5}\n')
        inputs = {p.relative_to(self.root).as_posix(): native.sha256(p) for p in fixture.iterdir()}
        definition = {**self.gate.obligation.definition, 'reference': {'rom_sha1': identity,
            'citation': 'Controlled parser fixture, not parity evidence.', 'inputs': inputs,
            'core': 'Genplus-gx', 'emulator': 'BizHawk 2.11', 'legacy_movie_md5': md5}}
        return replace(self.gate, obligation=replace(self.obligation, definition=definition)), rom, identity

    def test_input_snapshot_binds_every_fixture_file_and_legacy_movie_md5(self):
        gate, rom, identity = self.fixture_inputs()
        with patch.dict(core.ROMS, {'s1': identity}):
            first = native.verify_inputs(self.root, [gate], {'s1': rom})
            self.assertEqual(first, native.verify_inputs(self.root, [gate], {'s1': rom}))
            self.assertIn(gate.fixture + '/physics.csv.gz', first)
            path = self.root / gate.fixture / 'metadata.json'
            path.write_text(path.read_text() + '\n')
            with self.assertRaisesRegex(ValueError, 'input differs'):
                native.verify_inputs(self.root, [gate], {'s1': rom})

    def test_pinned_fixture_cannot_hide_additional_input_files(self):
        gate, rom, identity = self.fixture_inputs()
        (self.root / gate.fixture / 'extra-input.json').write_text('{}')
        with patch.dict(core.ROMS, {'s1': identity}), self.assertRaisesRegex(ValueError, 'every fixture file'):
            native.verify_inputs(self.root, [gate], {'s1': rom})

    def test_preferred_shared_movie_cannot_replace_the_bound_local_movie(self):
        gate, rom, identity = self.fixture_inputs()
        metadata = self.root / gate.fixture / 'metadata.json'
        raw = json.loads(metadata.read_text())
        raw['source_bk2'] = 'shared.bk2'
        metadata.write_text(json.dumps(raw))
        gate.obligation.definition['reference']['inputs'][metadata.relative_to(self.root).as_posix()] = native.sha256(metadata)
        shared = (self.root / gate.fixture).parent / '_movies/shared.bk2'
        shared.parent.mkdir()
        shared.write_bytes((self.root / gate.bk2).read_bytes())
        with patch.dict(core.ROMS, {'s1': identity}), self.assertRaisesRegex(ValueError, 'resolved movie'):
            native.verify_inputs(self.root, [gate], {'s1': rom})

    def test_ambiguous_local_movie_selection_is_rejected_even_when_pinned(self):
        gate, rom, identity = self.fixture_inputs()
        extra = self.root / gate.fixture / 'second.bk2'
        extra.write_bytes((self.root / gate.bk2).read_bytes())
        gate.obligation.definition['reference']['inputs'][extra.relative_to(self.root).as_posix()] = native.sha256(extra)
        with patch.dict(core.ROMS, {'s1': identity}), self.assertRaisesRegex(ValueError, 'ambiguous'):
            native.verify_inputs(self.root, [gate], {'s1': rom})

    def test_measurement_cannot_validate_another_checkout_with_this_checkout_code(self):
        with self.assertRaisesRegex(ValueError, 'owning code checkout'):
            native.measure(self.root, ['s1'], {}, {})

    def test_wrong_movie_rom_or_truncated_csv_is_rejected_even_when_hash_is_pinned(self):
        for corrupt in ('movie', 'csv'):
            with self.subTest(corrupt=corrupt):
                gate, rom, identity = self.fixture_inputs()
                if corrupt == 'movie':
                    path = self.root / gate.bk2
                    with ZipFile(path, 'w') as movie:
                        movie.writestr('Header.txt', 'Core Genplus-gx\nemuVersion Version 2.11\nSHA1 ' + '0' * 32)
                else:
                    path = self.root / gate.fixture / 'physics.csv.gz'
                    with gzip.open(path, 'wt') as stream:
                        stream.write('frame,input\n0,00\n')
                gate.obligation.definition['reference']['inputs'][path.relative_to(self.root).as_posix()] = native.sha256(path)
                with patch.dict(core.ROMS, {'s1': identity}), self.assertRaises(ValueError):
                    native.verify_inputs(self.root, [gate], {'s1': rom})


if __name__ == '__main__':
    unittest.main()
