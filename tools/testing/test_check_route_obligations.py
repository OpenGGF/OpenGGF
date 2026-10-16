"""Safety cases for the bounded AIZ1/HCZ1 JUnit obligation join."""
import copy
import json
import contextlib
import io
import importlib.util
from pathlib import Path
import tempfile
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


if __name__ == '__main__':
    unittest.main()
