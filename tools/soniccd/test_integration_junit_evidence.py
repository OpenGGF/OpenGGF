#!/usr/bin/env python3
"""Synthetic fresh-XML/export safety tests; never inspect engine reports or run Maven.

Run from an owned external mirror with TMPDIR in separate owned E scratch:
python3 -B -m unittest discover -s tools/soniccd -p test_integration_junit_evidence.py
Origin: 2026-11-04 explicit JUnit evidence export tooling task.
"""
import io
import json
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import integration_packet as packet
import test_integration_packet as legacy


class JunitEvidenceTests(unittest.TestCase):
    def setUp(self):
        # Reuse existing synthetic Git/XML fixtures; do not duplicate the strict parser.
        self.fixture = legacy.JunitCliTests('runTest')
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.output = self.fixture.base / 'observations.json'

    def save(self, *extra, output=None):
        return self.fixture.summary('--evidence', str(self.output if output is None else output), *extra)

    def direct(self, module, *, output=None):
        return module.save_junit_evidence(self.fixture.root, ['example.TestOne'],
                packet.cutoff(self.fixture.since), {}, str(self.output if output is None else output))

    def test_exact_complete_inventory_hashes_properties_and_concise_stdout(self):
        first = self.fixture.report(outcomes=('passed', 'failure', 'error', 'skipped'))
        second = self.fixture.report('example.TestTwo', ('passed',))
        before = {p: p.read_bytes() for p in (first, second)}
        result = self.save('--class', 'example.TestTwo', '--property', 'openggf.headless.gl=surfaceless-egl')
        self.assertEqual(result.returncode, 1, result.stderr)
        concise = json.loads(result.stdout)
        self.assertEqual(concise['evidence'], str(self.output))
        self.assertEqual(concise['sha256'], packet.digest(self.output.read_bytes()))
        self.assertEqual(concise['bytes'], self.output.stat().st_size)
        self.assertEqual(concise['classes'], 2)
        self.assertEqual([concise[k] for k in ('tests', 'failures', 'errors', 'skipped')], [5, 1, 1, 1])
        self.assertNotIn('cases', result.stdout)
        self.assertLess(len(result.stdout), 2048)
        data = json.loads(self.output.read_text())
        self.assertEqual(data['version'], 1)
        self.assertEqual(data['kind'], 'junit-xml-observations')
        report = data['report']
        self.assertEqual(report['requested_classes'], ['example.TestOne', 'example.TestTwo'])
        self.assertEqual(report['requested_properties'], {'openggf.headless.gl': 'surfaceless-egl'})
        self.assertEqual(report['head_at_inspection'], self.fixture.head)
        self.assertIn('caller must establish command completion', report['attribution'])
        cases = [case for group in report['classes'] for case in group['cases']]
        self.assertEqual([c['identity'] for c in cases], ['example.TestOne#case(0)', 'example.TestOne#case(1)',
                'example.TestOne#case(2)', 'example.TestOne#case(3)', 'example.TestTwo#case(0)'])
        self.assertEqual(cases[2], {'identity': 'example.TestOne#case(2)', 'status': 'error',
                'message': 'exact error message', 'type': 'example.Cause'})
        for group in report['classes']:
            p = Path(group['xml'])
            self.assertEqual(group['xml_sha256'], packet.digest(before[p]))
            self.assertEqual(group['xml_bytes'], len(before[p]))
            self.assertEqual(group['properties']['java.version'], '25.0.1')
            self.assertEqual(group['properties']['user.dir'], str(self.fixture.root))
            self.assertEqual(p.read_bytes(), before[p])
        self.assertNotIn('DO-NOT-PRINT-SECRET', self.output.read_text() + result.stdout + result.stderr)
        self.assertNotIn('API_TOKEN', self.output.read_text())
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o600)

    def test_plain_junit_output_stays_unchanged_and_does_not_export(self):
        self.fixture.report()
        result = self.fixture.summary()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('cases', json.loads(result.stdout)['classes'][0])
        self.assertFalse(self.output.exists())

    def test_explicit_empty_evidence_path_is_rejected_instead_of_running_plain_junit(self):
        self.fixture.report()
        result = self.fixture.summary('--evidence', '')
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(result.stdout, '')
        self.assertIn('bounded explicit canonical absolute path', result.stderr)
        self.assertFalse(self.output.exists())

    def test_all_cases_are_retained_without_a_compact_detail_budget(self):
        self.fixture.report(outcomes=('passed',) * 300)
        result = self.save()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(self.output.read_text())['report']
        self.assertEqual(len(report['classes'][0]['cases']), 300)
        self.assertEqual(report['classes'][0]['cases'][-1]['identity'], 'example.TestOne#case(299)')
        self.assertLess(len(result.stdout), 2048)

    def test_literal_display_identity_messages_and_simple_owner_provenance_are_not_normalized(self):
        path = self.fixture.report(outcomes=('failure',))
        tree = ET.parse(path)
        case = tree.getroot().find('testcase')
        case.set('classname', 'TestOne')
        case.set('name', 'literal([x=44],  a # () ü)')
        case.find('failure').set('message', ' first line\n  expected44 != actual0  ')
        tree.write(path, encoding='utf-8', xml_declaration=True)
        result = self.save()
        self.assertEqual(result.returncode, 1, result.stderr)
        observed = json.loads(self.output.read_text())['report']['classes'][0]['cases'][0]
        self.assertEqual(observed['identity'], 'example.TestOne#literal([x=44],  a # () ü)')
        self.assertEqual(observed['message'], ' first line\n  expected44 != actual0  ')
        self.assertEqual(observed['reported_classname'], 'TestOne')

    def test_invalid_freshness_class_and_environment_evidence_creates_nothing(self):
        path = self.fixture.report()
        os.utime(path, (self.fixture.started - 10,) * 2)
        self.assertEqual(self.save().returncode, 2)
        self.assertFalse(self.output.exists())
        self.fixture.report()
        other = self.fixture.report('example.Unexpected')
        self.assertEqual(self.save().returncode, 2)
        self.assertFalse(self.output.exists())
        other.unlink()  # Owned synthetic fixture only; production mode never deletes reports.
        self.assertEqual(self.save('--property', 'openggf.headless.gl=wrong').returncode, 2)
        self.assertFalse(self.output.exists())

    def test_existing_strict_reader_rejects_utf16_and_hardlinked_reports(self):
        path = self.fixture.report()
        original = path.read_bytes()
        path.write_bytes(original.decode('utf-8').replace("encoding='utf-8'", "encoding='utf-16'").encode('utf-16'))
        self.assertEqual(self.save().returncode, 2)
        self.assertFalse(self.output.exists())
        path.write_bytes(original)
        alias = self.fixture.base / 'alias.xml'
        os.link(path, alias)
        self.assertEqual(self.save().returncode, 2)
        self.assertFalse(self.output.exists())
        self.assertEqual(alias.read_bytes(), original)

    def test_existing_file_directory_and_hardlink_destinations_are_never_overwritten(self):
        self.fixture.report()
        protected = self.fixture.base / 'unrelated.txt'
        protected.write_bytes(b'unrelated preserved bytes')
        os.link(protected, self.output)
        self.assertEqual(self.save().returncode, 2)
        self.assertEqual(self.output.read_bytes(), b'unrelated preserved bytes')
        self.assertEqual(protected.read_bytes(), b'unrelated preserved bytes')
        self.output.unlink()
        self.output.mkdir()
        self.assertEqual(self.save().returncode, 2)
        self.assertTrue(self.output.is_dir())

    def test_relative_dot_parent_duplicate_separator_and_target_paths_are_rejected(self):
        self.fixture.report()
        target = self.fixture.base / 'target'
        target.mkdir()
        for value in ('relative.json', str(self.fixture.base) + '/./bad.json', str(self.fixture.base) + '//bad.json',
                str(self.fixture.base / 'missing' / '..' / 'bad.json'), str(target / 'bad.json')):
            with self.subTest(value=value):
                result = self.save(output=value)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertFalse((self.fixture.base / 'bad.json').exists())

    def test_selected_and_tool_repository_destinations_are_rejected_without_rejecting_external_home_git(self):
        self.fixture.report()
        other = self.fixture.base / 'other-checkout'
        other.mkdir()
        (other / '.git').write_text('gitdir: /uninspected/linked/repository')
        for output in (self.fixture.root / 'evidence.json', packet.ROOT / 'evidence.json'):
            with self.subTest(output=output):
                result = self.save(output=output)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertFalse(output.exists())
        # A broad unrelated dotfiles checkout must not swallow the caller's external artifact area.
        (self.fixture.base / '.git').mkdir()
        result = self.save(output=other / 'external.json')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_file_symlink_parent_symlink_and_missing_parent_are_rejected(self):
        self.fixture.report()
        protected = self.fixture.base / 'unrelated.json'
        protected.write_text('keep')
        self.output.symlink_to(protected)
        self.assertEqual(self.save().returncode, 2)
        self.assertEqual(protected.read_text(), 'keep')
        linked = self.fixture.base / 'linked'
        linked.symlink_to(self.fixture.base, target_is_directory=True)
        self.assertEqual(self.save(output=linked / 'fresh.json').returncode, 2)
        self.assertEqual(self.save(output=self.fixture.base / 'absent' / 'fresh.json').returncode, 2)
        self.assertFalse((self.fixture.base / 'fresh.json').exists())

    def test_destination_is_exclusive_even_if_created_after_initial_validation(self):
        import integration_junit_evidence as evidence
        self.fixture.report()
        original = evidence._Evidence.recheck
        def collision(current):
            original(current)
            self.output.write_bytes(b'concurrent unrelated file')
        with mock.patch.object(evidence._Evidence, 'recheck', collision):
            with self.assertRaises((packet.PacketError, OSError)):
                self.direct(evidence)
        self.assertEqual(self.output.read_bytes(), b'concurrent unrelated file')

    def test_changed_report_is_rejected_before_export(self):
        import integration_junit_evidence as evidence
        path = self.fixture.report()
        original = evidence._Evidence.recheck
        def changed(current):
            path.write_bytes(path.read_bytes() + b'\n')
            return original(current)
        with mock.patch.object(evidence._Evidence, 'recheck', changed):
            with self.assertRaises(packet.PacketError):
                self.direct(evidence)
        self.assertFalse(self.output.exists())

    def test_output_limit_fails_before_creating_or_truncating_an_artifact(self):
        import integration_junit_evidence as evidence
        self.fixture.report()
        with mock.patch.object(evidence, 'MAX_OUTPUT_BYTES', 32):
            with self.assertRaises(packet.PacketError):
                self.direct(evidence)
        self.assertFalse(self.output.exists())

    def test_parent_replacement_after_inspection_is_rejected_before_creation(self):
        import integration_junit_evidence as evidence
        self.fixture.report()
        parent = self.fixture.base / 'export'
        parent.mkdir()
        output = parent / 'evidence.json'
        moved = self.fixture.base / 'moved-parent'
        original = evidence._Evidence.recheck
        def replace_parent(current):
            original(current)
            parent.rename(moved)
            parent.mkdir()
        with mock.patch.object(evidence._Evidence, 'recheck', replace_parent):
            with self.assertRaises(packet.PacketError):
                self.direct(evidence, output=output)
        self.assertFalse(output.exists())
        self.assertFalse((moved / 'evidence.json').exists())

    def test_write_failure_reports_partial_retains_only_the_new_artifact_and_prints_no_success(self):
        self.fixture.report()
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch('integration_junit_evidence.os.fsync', side_effect=OSError('synthetic disk failure')), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            status = packet.main(['junit', '--worktree', str(self.fixture.root), '--class', 'example.TestOne',
                    '--since', self.fixture.since, '--evidence', str(self.output)])
        self.assertEqual(status, 3)
        self.assertEqual(stdout.getvalue(), '')
        error = json.loads(stderr.getvalue())
        self.assertTrue(error['partial'])
        self.assertEqual(error['evidence'], str(self.output))
        self.assertTrue(self.output.is_file())
        self.assertTrue(self.fixture.reports.is_dir())

    def test_inspection_never_imports_or_calls_a_test_runner(self):
        self.fixture.report()
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch.object(packet, 'worktree_build_slot', side_effect=AssertionError('no build lease')), \
                mock.patch.dict('sys.modules', {'integration_run': None}), redirect_stdout(stdout), redirect_stderr(stderr):
            status = packet.main(['junit', '--worktree', str(self.fixture.root), '--class', 'example.TestOne',
                    '--since', self.fixture.since, '--evidence', str(self.output)])
        self.assertEqual(status, 0, stderr.getvalue())
        self.assertTrue(self.output.is_file())


if __name__ == '__main__':
    unittest.main()
