#!/usr/bin/env python3
"""Exact method-selection safety using synthetic source, XML and a fake queue.

No real Java/Maven/native/media process. Origin: 2026-10-28 Sonic CD focused-run
method selection. Run alongside the existing integration safety suites.
"""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from unittest import mock
import unittest
import xml.etree.ElementTree as ET

import integration_handoff as handoff
import integration_packet as packet
from test_integration_run import RunFixture


class MethodSelectorTests(RunFixture):
    def select(self, *, methods=None, identities=None):
        tests = self.value['tests']
        tests['methods'] = methods if methods is not None else {'example.TestMain': ['selected']}
        tests['expected_cases'] = identities if identities is not None else {
            'example.TestMain': ['example.TestMain#selected()']}
        tests['expected_tests']['example.TestMain'] = len(tests['expected_cases'].get('example.TestMain', []))
        self.save()

    @staticmethod
    def fresh(path):
        # Real run freshness stays strict. Synthetic writes use a post-write clock
        # stamp rather than a filesystem's potentially coarser inode clock.
        stamp = time.time_ns()
        os.utime(path, ns=(stamp, stamp))

    def cases(self, names=('selected()',), *, owner='example.TestMain', status=None):
        reports = self.root/'target/surefire-reports'
        reports.mkdir(parents=True, exist_ok=True)
        suite = ET.Element('testsuite', name=owner, tests=str(len(names)),
            failures=str(int(status=='failure')), errors=str(int(status=='error')),
            skipped=str(int(status=='skipped')))
        properties = ET.SubElement(suite, 'properties')
        for name, value in {'java.version':'25.0.1', 'user.dir':str(self.root),
                            'soniccd.opening.egl':'true', 'API_TOKEN':'DO-NOT-EXPOSE'}.items():
            ET.SubElement(properties, 'property', name=name, value=value)
        for index, name in enumerate(names):
            case = ET.SubElement(suite, 'testcase', classname=owner, name=name)
            if index==0 and status:
                ET.SubElement(case, status, message='exact negative observation', type='example.Cause')
        path = reports/f'TEST-{owner}.xml'
        ET.ElementTree(suite).write(path, encoding='utf-8', xml_declaration=True)
        self.fresh(path)
        return path

    def validate(self, **changes):
        tests = copy.deepcopy(self.value['tests'])
        tests.update(changes)
        return handoff._tests(tests)

    def test_mixed_whole_classes_and_exact_method_lists_render_one_canonical_selector_argument(self):
        self.value['tests'].update(classes=['example.Runtime', 'example.World', 'example.Egl', 'example.Body'],
            expected_tests={'example.Runtime':9, 'example.World':7, 'example.Egl':2, 'example.Body':1},
            methods={'example.Egl':['zCheck','aCheck'], 'example.Body':['bodyWitness']},
            expected_cases={'example.Egl':['example.Egl#aCheck()', 'example.Egl#zCheck()'],
                            'example.Body':['example.Body#bodyWitness()']})
        self.save()
        with mock.patch.object(self.runner().queue, 'main', side_effect=AssertionError('plan must not queue')):
            result = self.runner().run_handoff(self.root, self.spec)
        self.assertFalse(result['executed'])
        self.assertEqual(result['requested_maven_args'], ['-Dmse=off',
            '-Dtest=example.Runtime,example.World,example.Egl#aCheck+zCheck,example.Body#bodyWitness',
            '-Dsoniccd.opening.egl=true', 'test', '-B'])
        self.assertEqual(result['methods'], self.value['tests']['methods'])
        self.assertEqual(result['expected_cases'], self.value['tests']['expected_cases'])
        self.assertFalse((self.root/'target').exists())

    def test_absent_pair_preserves_old_plan_shape_and_selection_exactly(self):
        result = self.runner().run_handoff(self.root, self.spec)
        self.assertNotIn('methods', result)
        self.assertNotIn('expected_cases', result)
        self.assertEqual(result['requested_maven_args'][1], '-Dtest=example.TestMain')

    def test_valid_method_handoff_cli_is_read_only_and_does_not_rewrite_declaration_order(self):
        self.select(methods={'example.TestMain':['zCheck','aCheck']}, identities={
            'example.TestMain':['example.TestMain#zCheck()', 'example.TestMain#aCheck()']})
        before = self.spec.read_bytes()
        result = subprocess.run([sys.executable, '-B', str(Path(packet.__file__)),
            'handoff', '--worktree', str(self.root), '--spec', str(self.spec), '--installed'],
            capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['tests'], self.value['tests'])
        self.assertEqual(self.spec.read_bytes(), before)

    def test_method_map_and_literal_case_map_must_be_present_together_and_nonempty(self):
        for fields in ({'methods':{}}, {'expected_cases':{}}, {'methods':{}, 'expected_cases':{}},
                       {'methods':None, 'expected_cases':None},
                       {'methods':{'example.TestMain':['selected']}, 'expected_cases':{}}):
            with self.subTest(fields=fields), self.assertRaises(packet.PacketError):
                self.validate(**fields)

    def test_method_map_keys_are_only_declared_classes_and_case_map_keys_match_exactly(self):
        self.select()
        for fields in ({'methods':{'other.Test':['selected']}},
                       {'methods':{1:['selected']}},
                       {'expected_cases':{'other.Test':['other.Test#selected()']}},
                       {'methods':{'example.TestMain':['selected'], 'other.Test':['other']}}):
            with self.subTest(fields=fields), self.assertRaises(packet.PacketError):
                self.validate(**fields)

    def test_method_selectors_reject_nonstring_duplicate_patterns_paths_and_shell_syntax(self):
        self.select()
        invalid = (None, True, 1, {}, [], ['selected','selected'], [''], ['a'*129], ['_'], ['class'],
            ['true'], ['null'], ['bad name'], ['select()'], ['selected(int)'], ['a+b'], ['a#b'],
            ['a,b'], ['a*'], ['a?'], ['%regex[x]'], ['../selected'], ['a/b'], ['a\\b'],
            ['a;touch marker'], ['$(touch marker)'], ['`touch marker`'], ['x\ny'], ['x\0y'],
            ['éclair'], [False], [None], [42])
        for methods in invalid:
            with self.subTest(methods=methods), self.assertRaises(packet.PacketError):
                self.validate(methods={'example.TestMain':methods})
        self.assertFalse((self.base/'marker').exists())

    def test_safe_ascii_java_identifiers_dollar_underscore_and_digits_are_exact(self):
        self.select(methods={'example.TestMain':['$probe', '_probe2', 'a9']}, identities={
            'example.TestMain':['example.TestMain#literal one', 'example.TestMain#literal two',
                                'example.TestMain#literal three']})
        self.validate()
        result = self.runner().run_handoff(self.root, self.spec)
        self.assertEqual(result['requested_maven_args'][1], '-Dtest=example.TestMain#$probe+_probe2+a9')

    def test_expected_cases_require_unique_nonempty_bounded_literal_owner_identities_and_count(self):
        self.select()
        for identities in (None, True, 'example.TestMain#selected()', [], [None], [True],
            ['example.TestMain#selected()', 'example.TestMain#selected()'], ['other.Test#selected()'],
            ['example.TestMain#'], ['example.TestMain.selected()'], ['example.TestMain#bad\nname'],
            ['example.TestMain#bad\tname'], ['example.TestMain#'+'x'*2049],
            ['example.TestMain#\ud800'], ['example.TestMain#first()', 'example.TestMain#second()']):
            with self.subTest(identities=identities), self.assertRaises(packet.PacketError):
                self.validate(expected_cases={'example.TestMain':identities})

    def test_duplicate_selector_and_identity_json_keys_are_rejected_before_launch(self):
        self.select()
        for field in ('methods', 'expected_cases'):
            text = json.dumps(self.value)
            text = text.replace('"'+field+'": {', '"'+field+'": {}, "'+field+'": {')
            self.spec.write_text(text+'\n')
            with mock.patch.object(self.runner().queue, 'main', side_effect=AssertionError('no launch')), \
                 self.assertRaises(packet.PacketError):
                self.runner().run_handoff(self.root, self.spec, execute=True)

    def test_selector_budgets_are_enforced_before_queue_or_report_reads(self):
        self.select()
        limits = (('MAX_METHODS_PER_CLASS', 0), ('MAX_SELECTED_METHODS', 0), ('MAX_EXPECTED_CASES', 0))
        for name, limit in limits:
            with self.subTest(name=name), mock.patch.object(handoff, name, limit), \
                 self.assertRaises(packet.PacketError):
                self.validate()
        self.value['tests']['methods']['example.TestMain'] = ['m'+str(i) for i in range(51)]
        with self.assertRaises(packet.PacketError):
            self.validate()

    def test_aggregate_budgets_apply_across_classes_not_only_per_class(self):
        fields = dict(classes=['example.A', 'example.B'], expected_tests={'example.A':2, 'example.B':2},
            methods={'example.A':['a'], 'example.B':['b']},
            expected_cases={'example.A':['example.A#first', 'example.A#second'],
                            'example.B':['example.B#first', 'example.B#second']})
        self.validate(**fields)
        for name, limit in (('MAX_SELECTED_METHODS', 1), ('MAX_EXPECTED_CASES', 3)):
            with self.subTest(name=name), mock.patch.object(handoff, name, limit), \
                 self.assertRaises(packet.PacketError):
                self.validate(**fields)

    def test_fake_queue_receives_exact_argv_and_checks_identities_inside_the_existing_callbacks(self):
        self.select()
        run = self.runner()
        seen = []
        def queue(args, *, before_run, after_run, maven_stdout):
            seen.append(list(args))
            before_run()
            self.cases()
            after_run(0)
            return 0
        with mock.patch.object(run.queue, 'main', queue):
            result = run.run_handoff(self.root, self.spec, execute=True)
        self.assertEqual(seen, [['--normal', '-Dmse=off', '-Dtest=example.TestMain#selected',
                                '-Dsoniccd.opening.egl=true', 'test', '-B']])
        self.assertEqual(result['status'], 'passed')
        self.assertTrue(result['clean'])
        self.assertEqual(result['source_before'], result['source_after'])
        self.assertEqual(result['junit']['classes'][0]['cases'][0]['identity'], 'example.TestMain#selected()')
        self.assertNotIn('DO-NOT-EXPOSE', json.dumps(result))

    def test_rewritten_same_count_unexpected_method_is_incomplete_with_missing_and_unexpected_ids(self):
        self.select()
        result = self.invoke(lambda:self.cases(('otherMethod()',)))
        self.assertEqual(result['status'], 'incomplete')
        self.assertFalse(result['clean'])
        self.assertEqual(result['junit']['tests'], 1)
        self.assertEqual(result['case_identity_issues'], [{'class':'example.TestMain',
            'missing':['example.TestMain#selected()'], 'unexpected':['example.TestMain#otherMethod()']}])
        self.assertIn('identity', result['evidence_error'].lower())

    def test_parameterized_display_names_and_whitespace_are_never_normalized(self):
        identities = {'example.TestMain':['example.TestMain#selected(int)[1] 6',
                                         'example.TestMain#selected(int)[2] 8']}
        self.select(identities=identities)
        exact = ('selected(int)[1] 6', 'selected(int)[2] 8')
        self.assertEqual(self.invoke(lambda:self.cases(exact))['status'], 'passed')
        for names in (('selected(int)[1] 6', 'selected(int)[2] 9'),
                      ('selected(int)[1] 6', 'selected(int)[2] 8 ')):
            with self.subTest(names=names):
                result = self.invoke(lambda:self.cases(names))
                self.assertEqual(result['status'], 'incomplete')
                self.assertTrue(result.get('case_identity_issues'), result.get('evidence_error'))

    def test_one_named_method_may_have_multiple_explicit_invocations_without_inferred_ownership(self):
        self.select(identities={'example.TestMain':['example.TestMain#selected()',
                                                   'example.TestMain#selected(int)[1] 6']})
        result = self.invoke(lambda:self.cases(('selected()', 'selected(int)[1] 6')))
        self.assertEqual(result['status'], 'passed')
        self.assertEqual(result['junit']['tests'], 2)
        self.assertNotIn('discovered_methods', result)
        self.assertIn('method ownership', result['observation'])

    def test_case_identities_are_literal_observations_not_a_method_name_heuristic(self):
        self.select(methods={'example.TestMain':['selected']}, identities={
            'example.TestMain':['example.TestMain#custom display ∆']})
        result = self.invoke(lambda:self.cases(('custom display ∆',)))
        self.assertEqual(result['status'], 'passed')
        self.assertEqual(result['requested_maven_args'][1], '-Dtest=example.TestMain#selected')

    def test_wholeclass_in_mixed_selection_retains_count_contract_without_invented_ids(self):
        self.value['tests']['classes'].append('example.Whole')
        self.value['tests']['expected_tests']['example.Whole'] = 2
        self.select()
        def action():
            self.cases()
            self.cases(('first display', 'second display'), owner='example.Whole')
        result = self.invoke(action)
        self.assertEqual(result['status'], 'passed')
        self.assertEqual(result['junit']['tests'], 3)
        self.assertNotIn('example.Whole', result['expected_cases'])

    def test_negative_outcomes_nonzero_exit_and_wrong_environment_remain_negative_with_exact_ids(self):
        self.select()
        for status in ('failure', 'error', 'skipped'):
            with self.subTest(status=status):
                result = self.invoke(lambda:self.cases(status=status))
                self.assertEqual(result['status'], 'failed')
                self.assertFalse(result['clean'])
                self.assertEqual(result['junit']['classes'][0]['cases'][0]['message'], 'exact negative observation')
        self.assertFalse(self.invoke(lambda:self.cases(), code=1)['clean'])
        def wrong_property():
            path = self.cases()
            tree = ET.parse(path)
            tree.getroot().find('./properties/property[@name="soniccd.opening.egl"]').set('value', 'false')
            tree.write(path)
            self.fresh(path)
        result = self.invoke(wrong_property)
        self.assertEqual(result['status'], 'failed')
        self.assertTrue(result['junit']['issues'])
        def wrong_java():
            path = self.cases()
            tree = ET.parse(path)
            tree.getroot().find('./properties/property[@name="java.version"]').set('value', '21.0.9')
            tree.write(path)
            self.fresh(path)
        result = self.invoke(wrong_java)
        self.assertEqual(result['status'], 'failed')
        self.assertIn('Java', ' '.join(result['issues']))

    def test_same_ids_with_unrewritten_xml_or_source_drift_are_still_incomplete(self):
        self.select()
        self.cases()
        self.assertEqual(self.invoke(lambda:None)['status'], 'incomplete')
        def drift():
            self.cases()
            self.target.write_text('class Changed {}\n')
        self.assertEqual(self.invoke(drift)['status'], 'incomplete')

    def test_selector_spec_changes_during_admission_prevent_launch_and_after_launch_reject(self):
        self.select()
        def replace_spec():
            self.value['tests']['methods']['example.TestMain'] = ['different']
            self.save()
        with self.assertRaises(packet.PacketError):
            self.invoke(before=replace_spec)
        self.select()
        def action():
            self.cases()
            replace_spec()
        self.assertEqual(self.invoke(action)['status'], 'incomplete')

    def test_mixed_literal_surefire_control_checks_full_identity_inventory_not_totals(self):
        self.select(methods={'example.TestMain':['selected']}, identities={
            'example.TestMain':['example.TestMain#selected(java.lang.String)[1] α',
                                'example.TestMain#selected(java.lang.String)[2] β']})
        template = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="example.TestMain" tests="2" failures="0" errors="0" skipped="0">
<properties><property name="java.version" value="25.0.1"/>
<property name="user.dir" value="{root}"/><property name="soniccd.opening.egl" value="true"/></properties>
<testcase classname="example.TestMain" name="selected(java.lang.String)[1] α"/>
<testcase classname="example.TestMain" name="selected(java.lang.String)[2] {last}"/>
</testsuite>'''
        def write(last):
            path = self.root/'target/surefire-reports'
            path.mkdir(parents=True, exist_ok=True)
            xml = path/'TEST-example.TestMain.xml'
            xml.write_text(template.format(root=self.root, last=last))
            self.fresh(xml)
        self.assertEqual(self.invoke(lambda:write('β'))['status'], 'passed')
        result = self.invoke(lambda:write('γ'))
        self.assertEqual(result['status'], 'incomplete')
        self.assertEqual(result['case_identity_issues'][0]['unexpected'],
                         ['example.TestMain#selected(java.lang.String)[2] γ'])


if __name__ == '__main__':
    unittest.main()
