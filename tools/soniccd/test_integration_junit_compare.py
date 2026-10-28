#!/usr/bin/env python3
"""Bounded synthetic matched-control evidence; no Java/Maven/native/media runs.

Run: python3 -B -m unittest discover -s tools/soniccd -p test_integration_junit_compare.py
Origin: 2026-10-28 Sonic CD integration comparison tooling.
"""
from contextlib import redirect_stderr, redirect_stdout
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

import integration_packet as packet
import integration_junit_compare as comparison

SCRIPT = Path(__file__).with_name('integration_packet.py')
CLASS = 'example.TestRoute'


class ComparisonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.roots = [self.base / side for side in ('baseline', 'candidate')]
        self.started = time.time() - 2
        self.since = datetime.fromtimestamp(self.started, timezone.utc).isoformat()
        for root in self.roots:
            root.mkdir()
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Fixture', '-c',
                'user.email=fixture@example.invalid', 'commit', '--allow-empty', '-qm',
                'independent synthetic evidence'], check=True)
            (root / 'target/surefire-reports').mkdir(parents=True)
            self.report(root)

    def report(self, root, cases=None, classname=CLASS, properties=None):
        # Construct reports independently of the comparator's parsing/classification.
        cases = cases if cases is not None else [('witness()', 'passed', '', '')]
        suite = ET.Element('testsuite', name=classname, tests=str(len(cases)),
            failures=str(sum(c[1] == 'failure' for c in cases)),
            errors=str(sum(c[1] == 'error' for c in cases)),
            skipped=str(sum(c[1] == 'skipped' for c in cases)))
        props = {'java.version': '25.0.1', 'user.dir': str(root),
            'openggf.headless.gl': 'surfaceless-egl', 'soniccd.media.root': '/reviewed/media',
            'API_TOKEN': 'NEVER-PRINT-THIS'}
        props.update(properties or {})
        group = ET.SubElement(suite, 'properties')
        for key, value in props.items():
            if value is not None:
                ET.SubElement(group, 'property', name=key, value=value)
        for name, status, message, kind in cases:
            node = ET.SubElement(suite, 'testcase', classname=classname, name=name)
            if status != 'passed':
                ET.SubElement(node, status, message=message, type=kind)
        path = root / 'target/surefire-reports' / f'TEST-{classname}.xml'
        ET.ElementTree(suite).write(path, encoding='utf-8', xml_declaration=True)
        return path

    def compare(self, **changes):
        args = dict(baseline_worktree=str(self.roots[0]), candidate_worktree=str(self.roots[1]),
            baseline_since=self.since, candidate_since=self.since, classes=[CLASS],
            baseline_properties={'openggf.headless.gl': 'surfaceless-egl'},
            candidate_properties={'openggf.headless.gl': 'surfaceless-egl'})
        args.update(changes)
        return comparison.compare_junit(**args)

    def cli(self, *extra):
        return subprocess.run([sys.executable, '-B', str(SCRIPT), 'compare-junit',
            '--baseline-worktree', str(self.roots[0]), '--candidate-worktree', str(self.roots[1]),
            '--baseline-since', self.since, '--candidate-since', self.since, '--class', CLASS,
            *map(str, extra)], capture_output=True, text=True)

    def test_unchanged_failure_error_and_skip_remain_visible_without_claiming_green(self):
        cases = [('pass()', 'passed', '', ''), ('fail[1]', 'failure', 'expected: <9> but was: <8>',
            'org.opentest4j.AssertionFailedError'), ('boot()', 'error', 'service unavailable',
            'java.lang.IllegalStateException'), ('rom()', 'skipped', 'missing reviewed ROM', '')]
        for root in self.roots:
            self.report(root, cases)
        result = self.compare()
        self.assertTrue(result['test_outcomes_equivalent'])
        self.assertFalse(result['candidate_all_passed'])
        self.assertEqual([c['identity'] for c in result['unchanged_failures']], [CLASS+'#fail[1]'])
        self.assertEqual(result['unchanged_errors'][0]['message'], 'service unavailable')
        self.assertEqual(result['unchanged_skips'][0]['message'], 'missing reviewed ROM')
        self.assertEqual(len(result['unchanged_passes']), 1)
        self.assertNotIn('clean', result)
        self.assertNotIn('NEVER-PRINT-THIS', json.dumps(result))

    def test_independent_literal_surefire_control_preserves_exact_message_and_body_fallback(self):
        # Literal Surefire-style XML is independent of the ET fixture generator above.
        template = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="example.TestRoute" tests="5" failures="1" errors="1" skipped="1">
  <properties>
    <property name="java.version" value="25.0.1"/>
    <property name="user.dir" value="{root}"/>
    <property name="openggf.headless.gl" value="surfaceless-egl"/>
    <property name="API_TOKEN" value="NEVER-PRINT-THIS"/>
  </properties>
  <testcase classname="example.TestRoute" name="route[2, SONIC]">
    <failure message="expected: &lt;0&gt; but was: &lt;{actual}&gt;" type="org.opentest4j.AssertionFailedError">stack trace ignored when message present</failure>
  </testcase>
  <testcase classname="example.TestRoute" name="nativeBoot()">
    <error type="java.lang.IllegalStateException">service not ready
  at example.Owner.start(Owner.java:19)</error>
  </testcase>
  <testcase classname="example.TestRoute" name="romFeature()"><skipped message="reviewed ROM unavailable"/></testcase>
  <testcase classname="example.TestRoute" name="witness()"/>
  <testcase classname="example.TestRoute" name="{extra}"/>
  <system-out>NEVER-PRINT-THIS</system-out>
</testsuite>
"""
        for root, actual, extra in ((self.roots[0], 7, 'removedWitness()'),
                                    (self.roots[1], 9, 'addedWitness()')):
            path = root / 'target/surefire-reports/TEST-example.TestRoute.xml'
            path.write_text(template.format(root=root, actual=actual, extra=extra), encoding='utf-8')
        observed = self.cli('--baseline-property', 'openggf.headless.gl=surfaceless-egl',
                            '--candidate-property', 'openggf.headless.gl=surfaceless-egl')
        self.assertEqual(observed.returncode, 1, observed.stderr)
        self.assertEqual(observed.stderr, '')
        result = json.loads(observed.stdout)
        self.assertEqual(result['changed_failures'][0]['baseline']['message'], 'expected: <0> but was: <7>')
        self.assertEqual(result['changed_failures'][0]['candidate']['message'], 'expected: <0> but was: <9>')
        self.assertEqual(result['unchanged_errors'][0]['message'],
                         'service not ready\n  at example.Owner.start(Owner.java:19)')
        self.assertEqual(len(result['unchanged_skips']), 1)
        self.assertEqual(result['missing_identities'][0]['identity'], CLASS+'#removedWitness()')
        self.assertEqual(result['added_identities'][0]['identity'], CLASS+'#addedWitness()')
        self.assertNotIn('NEVER-PRINT-THIS', json.dumps(result))

    def test_one_live_worktree_is_not_accepted_as_two_distinct_evidence_sets(self):
        with self.assertRaises(packet.PacketError):
            self.compare(candidate_worktree=str(self.roots[0]))

    def test_equal_totals_with_swapped_outcomes_are_different_exact_identities(self):
        self.report(self.roots[0], [('a()', 'passed', '', ''), ('b()', 'failure', 'old', 'Assert')])
        self.report(self.roots[1], [('a()', 'failure', 'new', 'Assert'), ('b()', 'passed', '', '')])
        result = self.compare()
        self.assertFalse(result['test_outcomes_equivalent'])
        self.assertEqual(result['new_failures'][0]['identity'], CLASS+'#a()')
        self.assertEqual(result['resolved_failures'][0]['identity'], CLASS+'#b()')

    def test_changed_message_type_whitespace_and_dynamic_values_are_not_normalized(self):
        for candidate in [('failure', 'expected 7 got 9', 'Assert'),
                          ('failure', 'expected 7 got 8 ', 'Assert'),
                          ('failure', 'expected 7 got 8', 'Other')]:
            with self.subTest(candidate=candidate):
                self.report(self.roots[0], [('same()', 'failure', 'expected 7 got 8', 'Assert')])
                self.report(self.roots[1], [('same()', *candidate)])
                result = self.compare()
                self.assertEqual(len(result['changed_failures']), 1)
                self.assertEqual(result['unchanged_failures'], [])
                self.assertEqual(result['changed_failures'][0]['candidate']['message'], candidate[1])

    def test_transition_partition_covers_all_outcomes_and_reports_added_missing_separately(self):
        states = ('passed', 'failure', 'error', 'skipped')
        baseline, candidate = [], []
        for before in states:
            for after in states:
                name = before + '_to_' + after
                baseline.append((name, before, '' if before == 'passed' else 'same', 'Kind' if before != 'passed' else ''))
                candidate.append((name, after, '' if after == 'passed' else 'same', 'Kind' if after != 'passed' else ''))
        baseline.append(('gone()', 'failure', 'not silently resolved', 'Assert'))
        candidate.append(('new()', 'passed', '', ''))
        self.report(self.roots[0], baseline)
        self.report(self.roots[1], candidate)
        result = self.compare()
        self.assertEqual(len(result['worsened_failures']), 1)
        self.assertEqual(len(result['changed_failures']), 1)  # error -> assertion failure
        self.assertEqual(len(result['new_failures']), 4)
        self.assertEqual(len(result['became_skipped']), 2)
        self.assertEqual(len(result['new_skips']), 1)
        self.assertEqual(len(result['resolved_failures']), 2)
        self.assertEqual(len(result['resolved_skips']), 1)
        self.assertEqual(result['missing_identities'][0]['identity'], CLASS+'#gone()')
        self.assertEqual(result['added_identities'][0]['identity'], CLASS+'#new()')
        flattened = [entry['identity'] for key in comparison.BUCKETS for entry in result[key]]
        self.assertEqual(len(flattened), 18)
        self.assertEqual(len(set(flattened)), 18)

    def test_changed_skip_is_explicit_and_cannot_disappear_in_skip_totals(self):
        self.report(self.roots[0], [('rom()', 'skipped', 'ROM A missing', '')])
        self.report(self.roots[1], [('rom()', 'skipped', 'ROM B missing', '')])
        self.assertEqual(len(self.compare()['changed_skips']), 1)

    def test_simple_archunit_owner_keeps_canonical_identity(self):
        path = self.report(self.roots[0])
        tree = ET.parse(path)
        tree.getroot().find('testcase').set('classname', 'TestRoute')
        tree.write(path)
        result = self.compare()
        self.assertTrue(result['test_outcomes_equivalent'])
        self.assertEqual(result['baseline']['classes'][0]['cases'][0]['reported_classname'], 'TestRoute')

    def test_side_properties_are_explicit_and_environment_is_observed_without_equivalence_claim(self):
        self.report(self.roots[1], properties={'soniccd.media.root': '/other/media', 'java.version': '25.0.2'})
        result = self.compare(baseline_properties={'soniccd.media.root': '/reviewed/media'},
            candidate_properties={'soniccd.media.root': '/other/media'})
        self.assertTrue(result['test_outcomes_equivalent'])
        self.assertEqual({p['property'] for p in result['environment_differences']},
                         {'java.version', 'soniccd.media.root'})
        self.assertIn('command equivalence', result['limits']['observation'])
        with self.assertRaises(packet.PacketError):
            self.compare(candidate_properties={'soniccd.media.root': '/wrong'})

    def test_missing_or_wrong_required_environment_rejects_instead_of_baseline_known(self):
        for props in ({'java.version': None}, {'user.dir': '/wrong'}, {'openggf.headless.gl': None}):
            with self.subTest(props=props):
                self.report(self.roots[0], properties=props)
                with self.assertRaises(packet.PacketError):
                    self.compare()

    def test_each_side_cutoff_is_independent_and_must_be_explicit_utc(self):
        path = self.report(self.roots[0])
        os.utime(path, (self.started-10, self.started-10))
        older = datetime.fromtimestamp(self.started-20, timezone.utc).isoformat()
        self.assertTrue(self.compare(baseline_since=older)['test_outcomes_equivalent'])
        for stamp in (self.since, '2026-01-01T01:00:00+01:00', '2026-01-01T00:00:00', 'nonsense'):
            with self.subTest(stamp=stamp), self.assertRaises(packet.PacketError):
                self.compare(baseline_since=stamp)

    def test_missing_unexpected_empty_duplicate_and_foreign_owner_xml_reject(self):
        for kind in ('missing', 'unexpected', 'empty', 'duplicate', 'foreign', 'count'):
            with self.subTest(kind=kind):
                path = self.report(self.roots[0])
                other = path.parent / 'TEST-example.Extra.xml'
                if other.exists():
                    other.unlink()
                if kind == 'missing':
                    path.unlink()
                elif kind == 'unexpected':
                    self.report(self.roots[0], classname='example.Extra')
                else:
                    tree = ET.parse(path)
                    suite = tree.getroot()
                    if kind == 'empty':
                        suite.remove(suite.find('testcase'))
                        suite.set('tests', '0')
                    elif kind == 'duplicate':
                        suite.append(suite.find('testcase'))
                        suite.set('tests', '2')
                    elif kind == 'foreign':
                        suite.find('testcase').set('classname', 'foreign.TestRoute')
                    else:
                        suite.set('tests', '999')
                    tree.write(path)
                with self.assertRaises(packet.PacketError):
                    self.compare()

    def test_stale_unrelated_xml_is_not_parsed_or_deleted_but_unsafe_aliases_reject(self):
        old = self.roots[0] / 'target/surefire-reports/TEST-example.Old.xml'
        old.write_text('old unrelated malformed XML')
        os.utime(old, (self.started-10, self.started-10))
        result = self.compare()
        self.assertEqual(result['baseline']['ignored_stale_reports'], 1)
        self.assertEqual(old.read_text(), 'old unrelated malformed XML')
        old.unlink()
        old.symlink_to(self.report(self.roots[0]))
        with self.assertRaises(packet.PacketError):
            self.compare()

    def test_selected_xml_file_parent_symlink_hardlink_and_fifo_reject(self):
        root = self.roots[0]
        path = self.report(root)
        original = self.base / 'original.xml'
        path.rename(original)
        for kind in ('symlink', 'hardlink', 'fifo'):
            with self.subTest(kind=kind):
                if kind == 'symlink':
                    path.symlink_to(original)
                elif kind == 'hardlink':
                    os.link(original, path)
                else:
                    os.mkfifo(path)
                with self.assertRaises(packet.PacketError):
                    self.compare()
                path.unlink()
        path.parent.rmdir()
        path.parent.symlink_to(original.parent, target_is_directory=True)
        with self.assertRaises(packet.PacketError):
            self.compare()

    def test_unsafe_encoding_entity_and_malformed_xml_reject_without_traceback(self):
        path = self.report(self.roots[0])
        for raw in (b'<!DOCTYPE testsuite [<!ENTITY x "bad">]><testsuite/>',
                    '<!DOCTYPE testsuite [<!ENTITY x "bad">]><testsuite/>'.encode('utf-16'),
                    b'<testsuite>\xff</testsuite>', b'<testsuite>'):
            with self.subTest(raw=raw[:20]):
                path.write_bytes(raw)
                result = self.cli()
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, '')
                self.assertNotIn('Traceback', result.stderr)

    def test_bounds_for_classes_inventory_bytes_cases_and_depth(self):
        for changes in ({'classes': []}, {'classes': [CLASS, CLASS]}, {'classes': ['example.*']},
                        {'classes': [f'example.T{i}' for i in range(51)]},
                        {'baseline_properties': {'API_TOKEN': 'DO-NOT-ECHO'}}):
            with self.subTest(changes=changes), self.assertRaises(packet.PacketError):
                self.compare(**changes)
        for limit, maximum in (('MAX_DIRECTORY_ENTRIES', 0), ('MAX_SELECTED_BYTES', 1),
                               ('MAX_CASES', 0), ('MAX_XML_DEPTH', 1)):
            with self.subTest(limit=limit), mock.patch.object(comparison, limit, maximum), \
                 self.assertRaises(packet.PacketError):
                self.compare()

    def test_rechecks_reject_baseline_changes_during_candidate_inspection(self):
        real = packet.junit_summary
        def race(root, *args, **kwargs):
            result = real(root, *args, **kwargs)
            if str(root) == str(self.roots[1]):
                self.report(self.roots[0], [('changed()', 'passed', '', '')])
            return result
        with mock.patch.object(packet, 'junit_summary', side_effect=race), \
             self.assertRaises(packet.PacketError):
            self.compare()

    def test_rechecks_reject_new_report_and_head_change(self):
        real = packet.junit_summary
        for action in ('report', 'head'):
            with self.subTest(action=action):
                def race(root, *args, **kwargs):
                    result = real(root, *args, **kwargs)
                    if str(root) == str(self.roots[1]):
                        if action == 'report':
                            self.report(self.roots[0], classname='example.New')
                        else:
                            subprocess.run(['git', '-C', str(self.roots[0]), '-c', 'user.name=Fixture',
                                '-c', 'user.email=fixture@example.invalid', 'commit', '--allow-empty',
                                '-qm', 'observed moving HEAD'], check=True)
                    return result
                with mock.patch.object(packet, 'junit_summary', side_effect=race), \
                     self.assertRaises(packet.PacketError):
                    self.compare()
                extra = self.roots[0] / 'target/surefire-reports/TEST-example.New.xml'
                if extra.exists():
                    extra.unlink()

    def test_reader_validates_actual_bytes_and_does_not_trust_preflight(self):
        real = packet.snapshot
        def race(path, **kwargs):
            saved = real(path, **kwargs)
            if path.parent.name == 'surefire-reports':
                saved.data = b'<!DOCTYPE testsuite [<!ENTITY x "bad">]><testsuite/>'
            return saved
        with mock.patch.object(packet, 'snapshot', side_effect=race), \
             self.assertRaises(packet.PacketError):
            self.compare()

    def test_cli_exit_codes_json_only_and_forbidden_options(self):
        result = self.cli()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(json.loads(result.stdout)['test_outcomes_equivalent'])
        self.report(self.roots[1], [('witness()', 'failure', 'explicit mismatch', 'Assert')])
        result = self.cli()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertFalse(json.loads(result.stdout)['test_outcomes_equivalent'])
        self.assertEqual(result.stderr, '')
        self.report(self.roots[1])
        for extra in (('--execute',), ('--apply',), ('--output', str(self.base/'must-not-exist')),
                      ('--baseline-property', 'API_TOKEN=DO-NOT-ECHO')):
            with self.subTest(extra=extra):
                result = self.cli(*extra)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, '')
                self.assertNotIn('DO-NOT-ECHO', result.stderr)
        self.assertFalse((self.base/'must-not-exist').exists())

    def test_read_only_fixed_git_calls_only_without_scheduler_or_writes(self):
        paths = [p for root in self.roots for p in (root/'target/surefire-reports').iterdir()]
        before = [(p.read_bytes(), packet.signature(p.stat())) for p in paths]
        real = packet.subprocess.check_output
        with mock.patch.object(packet.subprocess, 'check_output', wraps=real) as calls, \
             mock.patch.object(packet, 'worktree_build_slot', side_effect=AssertionError('no lease')), \
             mock.patch.object(packet, 'apply_packet', side_effect=AssertionError('no apply')):
            result = self.compare()
        self.assertTrue(result['test_outcomes_equivalent'])
        for call in calls.call_args_list:
            self.assertIn(call.args[0], (['git', 'rev-parse', '--show-toplevel'], ['git', 'rev-parse', 'HEAD']))
        self.assertEqual([(p.read_bytes(), packet.signature(p.stat())) for p in paths], before)


if __name__ == '__main__':
    unittest.main()
