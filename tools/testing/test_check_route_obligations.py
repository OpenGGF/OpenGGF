"""Safety cases for the bounded AIZ1/HCZ1 JUnit obligation join."""
import copy
import json
import contextlib
import io
import importlib.util
from pathlib import Path
import tempfile
import subprocess
import shutil
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

SPEC = importlib.util.spec_from_file_location('route_obligations', Path(__file__).with_name('check_route_obligations.py'))
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


class RouteObligationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.reports = self.root / 'reports'
        self.reports.mkdir()
        self.inventory = {
            'schema': 1, 'lane': 'test-pilot', 'roms': ['s3k'],
            'fixtures': ['fixture/inputs.bk2'],
            'cases': [
                {'class': 'example.Route', 'name': 'replays(Spot)[1]', 'method': 'replays',
                 'obligations': ['AIZ1.INTRO.REWIND']},
                {'class': 'example.Route', 'name': 'replays(Spot)[2]', 'method': 'replays',
                 'obligations': ['AIZ1.TREE.REWIND']},
            ]}
        source = self.root / 'src/test/java/example/Route.java'
        source.parent.mkdir(parents=True)
        source.write_text('class Route { void replays(Spot spot) {} }')
        (self.root / 'fixture').mkdir()
        (self.root / 'fixture/inputs.bk2').write_bytes(b'input fixture')
        (self.root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>'
            '<surefire.excludedGroups>slow-suite,performance-measurement,audio-reference</surefire.excludedGroups>'
            '</properties></project>')
        self.rom = self.root / 'original-rom.bin'
        self.rom.write_bytes(b'unit fixture, never a real ROM')

    def report(self, names=None, suffix='', outcome=None, classname='example.Route'):
        suite = ET.Element('testsuite')
        for name in names or [case['name'] for case in self.inventory['cases']]:
            case = ET.SubElement(suite, 'testcase', classname=classname, name=name)
            if outcome:
                ET.SubElement(case, outcome, message='test evidence')
        ET.ElementTree(suite).write(self.reports / ('TEST-route' + suffix + '.xml'))

    def test_exact_executed_identities_pass(self):
        self.report()
        self.assertEqual([], gate.check_reports(self.inventory, self.reports))

    def test_unrelated_skipped_or_failed_cases_do_not_join_this_lane(self):
        self.report()
        self.report(['unrelated'], '-other', 'failure', 'other.Suite')
        self.assertEqual([], gate.check_reports(self.inventory, self.reports))

    def test_missing_case_fails_even_with_matching_aggregate_count(self):
        self.report([self.inventory['cases'][0]['name'], 'differentCase'])
        issues = '\n'.join(gate.check_reports(self.inventory, self.reports))
        self.assertIn('missing', issues)
        self.assertIn('unexpected', issues)
        self.assertIn('replays(Spot)[2]', issues)

    def test_each_skipped_failed_or_errored_expected_case_fails(self):
        for outcome in ('skipped', 'failure', 'error'):
            with self.subTest(outcome=outcome):
                self.report(outcome=outcome)
                self.assertIn(outcome, '\n'.join(gate.check_reports(self.inventory, self.reports)))

    def test_duplicate_cases_across_reports_fail(self):
        self.report()
        self.report(suffix='-duplicate')
        self.assertIn('duplicate', '\n'.join(gate.check_reports(self.inventory, self.reports)))

    def test_empty_report_directory_is_missing_coverage(self):
        self.assertIn('missing', '\n'.join(gate.check_reports(self.inventory, self.reports)))

    def test_duplicate_inventory_row_is_rejected(self):
        self.inventory['cases'].append(copy.deepcopy(self.inventory['cases'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            gate.validate_inventory(self.inventory, self.root)

    def test_stale_method_reference_is_rejected_before_launch(self):
        self.inventory['cases'][0]['method'] = 'renamedAway'
        with self.assertRaisesRegex(ValueError, 'method'):
            gate.validate_inventory(self.inventory, self.root)

    def test_reordered_enum_arguments_reject_stale_parameter_obligations(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { enum Spot { TREE, INTRO } void replays(Spot spot) {} }')
        self.inventory['parameter_sources'] = [
            {'class': 'example.Route', 'method': 'replays', 'kind': 'enum',
             'type': 'Spot', 'values': ['INTRO', 'TREE']}]
        with self.assertRaisesRegex(ValueError, 'parameter source'):
            gate.validate_inventory(self.inventory, self.root)

    def test_enum_constructor_arguments_are_rejected_instead_of_mislabelled(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { enum Spot { INTRO(1), TREE(2); '
                          'Spot(int code) {} } void replays(Spot spot) {} }')
        self.inventory['parameter_sources'] = [
            {'class': 'example.Route', 'method': 'replays', 'kind': 'enum',
             'type': 'Spot', 'values': ['INTRO', 'TREE']}]
        with self.assertRaisesRegex(ValueError, 'unsupported enum declaration'):
            gate.validate_inventory(self.inventory, self.root)

    def ints_binding(self, values):
        self.inventory['parameter_sources'] = [
            {'class': 'example.Route', 'method': 'replays', 'kind': 'ints', 'values': values}]

    def test_int_binding_uses_selected_methods_own_values(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { '
                          '@ValueSource(ints = {320, 400}) void earlier(int width) {} '
                          '@ValueSource(ints = {640, 800}) void replays(int width) {} }')
        self.ints_binding([640, 800])
        gate.validate_inventory(self.inventory, self.root)

    def test_earlier_matching_values_cannot_mask_changed_selected_method(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { '
                          '@ValueSource(ints = {320, 400}) void earlier(int width) {} '
                          '@ValueSource(ints = {640, 800}) void replays(int width) {} }')
        self.ints_binding([320, 400])
        with self.assertRaisesRegex(ValueError, 'parameter source'):
            gate.validate_inventory(self.inventory, self.root)

    def test_commented_annotation_cannot_supply_selected_method_values(self):
        source = self.root / 'src/test/java/example/Route.java'
        self.ints_binding([320, 400])
        for comment in ('/* @ValueSource(ints = {320, 400}) */',
                        '// @ValueSource(ints = {320, 400})\n'):
            with self.subTest(comment=comment):
                source.write_text('class Route { ' + comment + ' void replays(int width) {} }')
                with self.assertRaises(ValueError):
                    gate.validate_inventory(self.inventory, self.root)

    def test_comments_between_real_annotation_and_selected_method_are_ignored(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { @ValueSource(ints = {320, /* gap */ 400}) '
                          '// comment mentions @ValueSource(ints = {640, 800})\n'
                          'void replays(int width) {} }')
        self.ints_binding([320, 400])
        gate.validate_inventory(self.inventory, self.root)

    def test_nonliteral_int_arguments_fail_closed(self):
        source = self.root / 'src/test/java/example/Route.java'
        source.write_text('class Route { @ValueSource(ints = {320 + 1, 400}) '
                          'void replays(int width) {} }')
        self.ints_binding([320, 1, 400])
        with self.assertRaises(ValueError):
            gate.validate_inventory(self.inventory, self.root)

    def test_missing_rom_fails_preflight(self):
        with self.assertRaisesRegex(ValueError, 'ROM'):
            gate.preflight(self.inventory, {}, self.root)

    def test_wrong_rom_identity_fails_preflight(self):
        with self.assertRaisesRegex(ValueError, 'identity'):
            gate.preflight(self.inventory, {'s3k': self.rom}, self.root)

    def test_canonical_rom_and_fixtures_pass_preflight(self):
        with mock.patch.object(gate, 'sha1_file', return_value=gate.ROMS['s3k']['sha1']):
            gate.preflight(self.inventory, {'s3k': self.rom}, self.root)

    def test_missing_fixture_fails_even_with_verified_rom(self):
        (self.root / 'fixture/inputs.bk2').unlink()
        with mock.patch.object(gate, 'sha1_file', return_value=gate.ROMS['s3k']['sha1']):
            with self.assertRaisesRegex(ValueError, 'fixture'):
                gate.preflight(self.inventory, {'s3k': self.rom}, self.root)

    def test_owned_queue_wrapper_is_stopped_on_cancellation(self):
        with mock.patch.object(gate.subprocess, 'Popen') as launch, \
                mock.patch.object(gate, 'stop_process_tree') as stop:
            process = launch.return_value.__enter__.return_value
            process.wait.side_effect = KeyboardInterrupt
            with self.assertRaises(KeyboardInterrupt):
                gate.run_queued(['queue-wrapper'], self.root)
            stop.assert_called_once_with(process)

    def test_owned_queue_wrapper_is_reaped_after_failed_maven(self):
        with mock.patch.object(gate.subprocess, 'Popen') as launch, \
                mock.patch.object(gate, 'stop_process_tree') as stop:
            process = launch.return_value.__enter__.return_value
            process.wait.return_value = 1
            self.assertEqual(1, gate.run_queued(['queue-wrapper'], self.root))
            stop.assert_called_once_with(process)

    def test_missing_prerequisite_rejects_run_before_maven_or_report_creation(self):
        inventory_path = self.root / 'inventory.json'
        inventory_path.write_text(json.dumps(self.inventory))
        with mock.patch.object(gate, 'ROOT', self.root), \
                mock.patch.object(gate.subprocess, 'Popen') as launch, \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, gate.main(['--inventory', str(inventory_path), '--run']))
            launch.assert_not_called()
            self.assertFalse((self.root / 'target').exists())

    def test_report_temporary_directory_is_removed_after_validation_failure(self):
        inventory_path = self.root / 'inventory.json'
        inventory_path.write_text(json.dumps(self.inventory))
        with mock.patch.object(gate, 'ROOT', self.root), \
                mock.patch.object(gate, 'sha1_file', return_value=gate.ROMS['s3k']['sha1']), \
                mock.patch('category_control.preflight'), \
                mock.patch.object(gate, 'run_queued', return_value=1), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, gate.main(['--inventory', str(inventory_path), '--run',
                                          '--rom', 's3k=' + str(self.rom)]))
            self.assertEqual([], list((self.root / 'target').iterdir()))

    def test_command_selects_exact_lane_and_does_not_use_full_suite(self):
        command = gate.maven_command(self.inventory, {'s3k': self.rom}, self.reports, self.root)
        self.assertIn('-Dtest=example.Route', command)
        self.assertIn('-Ds3k.rom.path=' + str(self.rom), command)
        self.assertNotIn('-Pfull-suite', command)
        self.assertIn('-Dopenggf.surefire.reports=' + str(self.reports), command)
        self.assertTrue(any(arg.startswith('-Dsurefire.excludedGroups=') and 'slow-suite' not in arg
                            for arg in command))


class ExactCandidateTests(unittest.TestCase):
    """Real private Git tree and generated fake ROM bytes; Maven is a test double."""
    report = RouteObligationTests.report

    def setUp(self):
        RouteObligationTests.setUp(self)
        self.inventory_path = self.root / 'tools/testing/aiz-hcz-route-obligations.json'
        self.inventory_path.parent.mkdir(parents=True)
        self.inventory_path.write_text(json.dumps(self.inventory))
        (self.root / '.gitignore').write_text('target/\nreports/\noriginal-rom.bin\nignored.bin\n'
                                             'tools/testing/ignored.py\n')
        self.git('init', '-q')
        self.git('config', 'user.name', 'Safety Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('add', '.')
        self.git('commit', '-qm', 'Generated safety fixture')
        self.candidate = self.git('rev-parse', 'HEAD')
        roms = copy.deepcopy(gate.ROMS)
        roms['s3k']['sha1'] = gate.sha1_file(self.rom)
        self.rom_patch = mock.patch.dict(gate.ROMS, roms)
        self.rom_patch.start()
        self.addCleanup(self.rom_patch.stop)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True,
                                       stderr=subprocess.PIPE).strip()

    def state(self, candidate=None):
        return gate.candidate_state(candidate or self.candidate, self.inventory_path,
                                    {'s3k': self.rom}, self.root)

    def run_gate(self, runner=None, mode='--run', extra=()):
        output = io.StringIO()
        with mock.patch.object(gate, 'ROOT', self.root), \
                mock.patch.object(gate, 'DEFAULT_INVENTORY', self.inventory_path), \
                mock.patch('category_control.preflight'), \
                mock.patch.object(gate, 'run_queued', side_effect=runner) as queued, \
                contextlib.redirect_stderr(output), contextlib.redirect_stdout(output):
            code = gate.main(['--inventory', str(self.inventory_path), mode, *extra,
                              '--expected-candidate', self.candidate,
                              '--rom', 's3k=' + str(self.rom)])
        return code, output.getvalue(), queued

    def executed(self, command, root):
        self.assertIn('clean', command)
        self.assertIn('-Dopenggf.headless.gl=surfaceless-egl', command)
        self.assertNotIn('-Pfull-suite', command)
        reports = next(arg.split('=', 1)[1] for arg in command
                       if arg.startswith('-Dopenggf.surefire.reports='))
        self.reports = Path(reports)
        self.report()
        path = self.reports / 'TEST-route.xml'
        suite = ET.parse(path).getroot()
        suite.attrib.update(tests='2', failures='0', errors='0', skipped='0')
        ET.ElementTree(suite).write(path)
        return 0

    def test_successful_exact_candidate_uses_fresh_rebuild_and_removes_evidence(self):
        code, output, queued = self.run_gate(self.executed)
        self.assertEqual(0, code, output)
        queued.assert_called_once()
        self.assertIn('at candidate ' + self.candidate, output)
        self.assertIn('zero skips', output)
        self.assertIn('not release certification', output)
        self.assertEqual([], list((self.root / 'target').iterdir()))

    def test_clean_recreates_report_directory_and_admission_has_no_worktree_lock(self):
        from contextlib import contextmanager
        locked = False
        @contextmanager
        def lock(root):
            nonlocal locked
            self.assertFalse(locked)
            locked = True
            try:
                yield
            finally:
                locked = False
        def clean_and_execute(command, root):
            self.assertFalse(locked, 'never request shared admission while holding local build lock')
            reports = Path(next(arg.split('=', 1)[1] for arg in command
                                if arg.startswith('-Dopenggf.surefire.reports=')))
            shutil.rmtree(root / 'target')
            reports.mkdir(parents=True)
            return self.executed(command, root)
        with mock.patch.object(gate, 'worktree_build_slot', side_effect=lock):
            code, output, _ = self.run_gate(clean_and_execute)
        self.assertEqual(0, code, output)
        self.assertFalse(locked)

    def test_cancelled_queue_removes_temporary_reports_and_awards_no_coverage(self):
        def cancel(command, root):
            raise KeyboardInterrupt
        code, output, _ = self.run_gate(cancel)
        self.assertEqual(130, code, output)
        self.assertNotIn('Route pilot passed', output)
        self.assertEqual([], list((self.root / 'target').iterdir()))

    def test_wrong_commit_or_moving_ref_fails_closed(self):
        for candidate in ('0' * 40, 'HEAD', self.candidate[:12]):
            with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                self.state(candidate)

    def test_dirty_staged_and_untracked_inputs_rejected(self):
        source = self.root / 'src/test/java/example/Route.java'
        original = source.read_text()
        for staged in (False, True):
            source.write_text(original + '\n// local change')
            if staged:
                self.git('add', str(source))
            with self.assertRaisesRegex(ValueError, 'clean checkout'):
                self.state()
            source.write_text(original)
            self.git('add', str(source))
        extra = source.with_name('Extra.java')
        extra.write_text('class Extra {}')
        with self.assertRaisesRegex(ValueError, 'clean checkout'):
            self.state()

    def test_ignored_untracked_source_cannot_supplement_candidate(self):
        (self.root / 'src/ignored.bin').write_bytes(b'generated non-ROM input')
        with self.assertRaisesRegex(ValueError, 'ignored untracked'):
            self.state()

    def test_ignored_untracked_tooling_cannot_supplement_candidate(self):
        (self.root / 'tools/testing/ignored.py').write_text('injected = True\n')
        with self.assertRaisesRegex(ValueError, 'ignored untracked'):
            self.state()

    def test_assume_unchanged_dirty_source_is_rejected_before_launch(self):
        relative = 'src/test/java/example/Route.java'
        self.git('update-index', '--assume-unchanged', relative)
        with (self.root / relative).open('a') as stream:
            stream.write('\n// hidden local edit')
        with self.assertRaisesRegex(ValueError, 'committed blob'):
            self.state()

    def test_dirty_candidate_rejects_before_queue_and_target_creation(self):
        with (self.root / 'src/test/java/example/Route.java').open('a') as stream:
            stream.write('\n// local edit')
        code, output, queued = self.run_gate(self.executed)
        self.assertEqual(2, code, output)
        queued.assert_not_called()
        self.assertFalse((self.root / 'target').exists())

    def test_custom_inventory_cannot_narrow_release_pilot(self):
        inventory = self.root / 'target/narrow.json'
        inventory.parent.mkdir()
        inventory.write_text(json.dumps(self.inventory))
        with self.assertRaisesRegex(ValueError, 'committed AIZ1/HCZ1'):
            gate.candidate_state(self.candidate, inventory, {'s3k': self.rom}, self.root)

    def test_missing_or_wrong_original_rom_rejected(self):
        self.rom.write_bytes(b'different generated fake ROM')
        with self.assertRaisesRegex(ValueError, 'wrong ROM identity'):
            self.state()
        self.rom.unlink()
        with self.assertRaisesRegex(ValueError, 'ROM unavailable'):
            self.state()

    def test_exact_preflight_does_not_execute_or_award_qualification(self):
        code, output, queued = self.run_gate(mode='--preflight')
        self.assertEqual(0, code, output)
        queued.assert_not_called()
        self.assertIn('no tests executed', output)
        self.assertNotIn('passed', output)

    def test_existing_reports_never_qualify_exact_candidate(self):
        code, output, queued = self.run_gate(mode='--reports', extra=(str(self.reports),))
        self.assertEqual(2, code, output)
        queued.assert_not_called()
        self.assertIn('pre-existing reports', output)

    def test_candidate_fixture_inventory_and_rom_drift_during_queue_or_execution(self):
        paths = [self.root / 'src/test/java/example/Route.java',
                 self.root / 'fixture/inputs.bk2', self.inventory_path, self.rom,
                 self.root / 'tools/testing/checker.py']
        for path in paths:
            with self.subTest(path=path):
                original = path.read_bytes() if path.exists() else None
                def drift(command, root):
                    result = self.executed(command, root)
                    path.write_bytes((original or b'') + b'\n')
                    return result
                code, output, _ = self.run_gate(drift)
                self.assertEqual(2, code, output)
                self.assertNotIn('Route pilot passed', output)
                self.assertEqual([], list((self.root / 'target').iterdir()))
                if original is None:
                    path.unlink()
                else:
                    path.write_bytes(original)

    def test_head_change_during_execution_rejected(self):
        def drift(command, root):
            self.executed(command, root)
            self.git('commit', '--allow-empty', '-qm', 'Different candidate')
            return 0
        code, output, _ = self.run_gate(drift)
        self.assertEqual(2, code, output)
        self.assertIn('wrong candidate', output)

    def test_source_and_fixture_fingerprints_detect_git_assume_unchanged_drift(self):
        for relative in ('src/test/java/example/Route.java', 'fixture/inputs.bk2',
                         'tools/testing/aiz-hcz-route-obligations.json'):
            with self.subTest(relative=relative):
                path = self.root / relative
                original = path.read_bytes()
                self.git('update-index', '--assume-unchanged', relative)
                # The fixture lives outside src only in this unit test; the
                # production fixture paths are src/test/resources/traces.
                def drift(command, root):
                    self.executed(command, root)
                    path.write_bytes(original + b'\n')
                    return 0
                code, output, _ = self.run_gate(drift)
                self.assertEqual(2, code, output)
                path.write_bytes(original)
                self.git('update-index', '--no-assume-unchanged', relative)

    def test_required_skips_and_forged_skip_counters_never_qualify(self):
        for outcome in ('skipped', 'failure', 'error', 'counter', 'missing', 'duplicate'):
            with self.subTest(outcome=outcome):
                def bad(command, root):
                    self.executed(command, root)
                    path = self.reports / 'TEST-route.xml'
                    suite = ET.parse(path).getroot()
                    if outcome == 'counter':
                        suite.set('skipped', '1')
                    elif outcome == 'missing':
                        suite.remove(suite[0])
                    elif outcome == 'duplicate':
                        suite.append(copy.deepcopy(suite[0]))
                    else:
                        ET.SubElement(suite[0], outcome)
                    ET.ElementTree(suite).write(path)
                    return 0
                code, output, _ = self.run_gate(bad)
                self.assertEqual(1, code, output)
                self.assertNotIn('Route pilot passed', output)
                self.assertEqual([], list((self.root / 'target').iterdir()))

    def test_maven_failure_cannot_qualify_even_with_all_passing_xml(self):
        def failed(command, root):
            self.executed(command, root)
            return 1
        code, output, _ = self.run_gate(failed)
        self.assertEqual(1, code, output)


if __name__ == '__main__':
    unittest.main()
