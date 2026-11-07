"""Finite nested attribution controls; temporary synthetic files only.

Origin: 2026-11-07 private Sonic CD tool packet. No Git, Java, queue or media
execution: worktree/source observations and queue callbacks are explicit fakes.
"""
import copy
from contextlib import ExitStack, nullcontext, redirect_stdout, redirect_stderr
import importlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import types
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

import integration_packet as packet
import integration_handoff as handoff
import integration_junit_compare as compare
import integration_junit_evidence as evidence

# No real scheduler is imported, inspected or manipulated, including at import time.
_fake_queue = types.ModuleType('maven_queue')
_fake_queue.main = mock.Mock(side_effect=AssertionError('unmocked synthetic queue'))
_fake_queue.handle_termination = nullcontext
with mock.patch.dict(sys.modules, {'maven_queue': _fake_queue}):
    run = importlib.import_module('integration_run')

ROOT = 'example.Outer'
CHILD = ROOT + '$Actual'
DEEP = CHILD + '$Deep'


def declaration():
    return {ROOT: {ROOT: dict(expected_tests=0, owners=[ROOT]),
                   CHILD: dict(expected_tests=2, owners=[ROOT, CHILD])}}


class NestedFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base/'tree'; self.root.mkdir()
        self.reports = self.root/'target/surefire-reports'; self.reports.mkdir(parents=True)
        self.groups = declaration()
        self.classes = [ROOT]
        self.since = time.time_ns() - 1_000_000_000
        self.head = '1'*40
        self.stack = ExitStack(); self.addCleanup(self.stack.close)
        self.stack.enter_context(mock.patch.object(run, 'queue', _fake_queue))
        self.stack.enter_context(mock.patch.object(packet, 'worktree', side_effect=lambda root: (Path(root), self.head)))
        # Catch accidental subprocess execution, including Git and Maven, in every control.
        self.stack.enter_context(mock.patch.object(subprocess, 'Popen', side_effect=AssertionError('no subprocess permitted')))
        self.stack.enter_context(mock.patch.object(evidence, '_protected', side_effect=lambda root: [root, packet.ROOT]))

    def xml(self, name, cases=(), *, java='25.0.1', user_dir=None, status=None):
        suite = ET.Element('testsuite', name=name, tests=str(len(cases)),
            failures=str(int(status == 'failure')), errors=str(int(status == 'error')),
            skipped=str(int(status == 'skipped')))
        props = ET.SubElement(suite, 'properties')
        for key, value in {'java.version':java, 'user.dir':str(self.root) if user_dir is None else user_dir,
                           'soniccd.opening.egl':'true', 'SECRET':'not public'}.items():
            ET.SubElement(props, 'property', name=key, value=value)
        for index, (owner, label) in enumerate(cases):
            case = ET.SubElement(suite, 'testcase', classname=owner, name=label)
            if index == 0 and status:
                ET.SubElement(case, status, message='expected44 != actual0', type='example.Cause')
            ET.SubElement(case, 'system-out').text = 'probe: hello\n'
        path = self.reports/f'TEST-{name}.xml'
        ET.ElementTree(suite).write(path, encoding='utf-8', xml_declaration=True)
        stamp = time.time_ns(); os.utime(path, ns=(stamp, stamp))
        return path

    def four_groups(self):
        self.classes = [ROOT, 'example.Two', 'example.Three', 'example.Four']
        self.groups = {name:{name:dict(expected_tests=1, owners=[name])} for name in self.classes}
        for name in self.classes[:3]:
            child = name+'$Actual'
            self.groups[name] = {name:dict(expected_tests=0, owners=[name]), child:dict(expected_tests=2, owners=[name,child])}

    def write_four(self):
        for name in self.classes[:3]:
            child = name+'$Actual'
            self.xml(name); self.xml(child, [(name,'parent()'),(child,'child()')])
        self.xml(self.classes[3], [(self.classes[3],'only()')])

    def write(self):
        self.xml(ROOT)
        return self.xml(CHILD, [(ROOT, 'parent()'), (CHILD, 'child()')])

    def summary(self, **kwargs):
        return packet.junit_summary(self.root, self.classes, self.since, {}, report_groups=self.groups, **kwargs)

    def declared_tests(self):
        return dict(classes=self.classes, properties={'soniccd.opening.egl':'true'},
                    expected_tests={name:sum(e['expected_tests'] for e in self.groups[name].values()) for name in self.classes},
                    java_major=25, report_groups=self.groups)

    def execute(self, action=None, *, code=0, export=False, before=None, terminal=True, compact=False, source=None, real_handoff=False, test_changes=None):
        spec = self.base/'handoff.json'; spec.write_text('synthetic immutable declaration\n')
        tests = self.declared_tests()
        if test_changes: tests.update(test_changes)
        handoff._tests(tests)
        if real_handoff:
            incoming = self.base/'input.txt'; incoming.write_bytes(b'synthetic payload\n'); incoming.chmod(0o444)
            target = self.root/'input.txt'; target.write_bytes(incoming.read_bytes())
            dependency = self.base/'dep.txt'; dependency.write_bytes(b'final dependency\n')
            self.dependency = dependency
            value = dict(version=1,id='synthetic-nested',packet=dict(version=1,worktree_head=self.head,files=[dict(
                source=str(incoming),sha256=packet.digest(incoming.read_bytes()),bytes=incoming.stat().st_size,
                mode='0444',utf8=True,lf=True,destination='input.txt',expected=dict(sha256=packet.digest(target.read_bytes())))]),
                dependencies=[dict(name='final',path=str(dependency),sha256=packet.digest(dependency.read_bytes()),
                                   bytes=dependency.stat().st_size,mode='0644')],api_changes=[],known_defects=[],tests=tests)
            spec.write_text(json.dumps(value)+'\n')
        ready = dict(ready=True, id='synthetic-nested', tests=tests)
        identity = dict(head=self.head, fingerprint='2'*64, files=1, bytes=2, gitlinks=0)
        calls = []
        def queue(args, *, before_run, before_prepared_run, after_run, maven_stdout):
            calls.append(args)
            if before: before()
            before_prepared_run()
            (action or self.write)()
            if terminal: after_run(code)
        with (nullcontext() if real_handoff else mock.patch.object(run, 'handoff_summary', return_value=ready)), \
             mock.patch.object(run, 'source_identity', side_effect=source or (lambda _: identity)), \
             mock.patch.object(run.queue, 'main', queue):
            result = run.run_handoff(self.root, spec, execute=True, compact_output=compact,
                                     evidence=str(self.base/'receipt.json') if export else None)
        return result, calls


class NestedParsingTests(NestedFixture):
    def test_parent_and_child_qualified_actual_identities(self):
        self.write(); result = self.summary()
        self.assertTrue(result['clean']); self.assertEqual(result['tests'], 2)
        cases = next(g for g in result['classes'] if g['name'] == CHILD)['cases']
        self.assertEqual([c['identity'] for c in cases], [ROOT+'#parent()', CHILD+'#child()'])
        self.assertEqual(cases[0]['reported_classname'], ROOT)

    def test_four_roots_seven_reports_aggregate(self):
        self.four_groups(); self.write_four()
        handoff._tests(self.declared_tests())
        result = self.summary(); self.assertEqual(len(result['classes']), 7); self.assertEqual(result['tests'], 7)

    def test_multilevel_empty_ancestors_and_actual_ancestor_owner(self):
        self.groups[ROOT][CHILD]['expected_tests'] = 0
        self.groups[ROOT][DEEP] = dict(expected_tests=3, owners=[ROOT, CHILD, DEEP])
        self.xml(ROOT); self.xml(CHILD)
        self.xml(DEEP, [(ROOT,'same()'), (CHILD,'same()'), (DEEP,'same()')])
        result = self.summary(); self.assertEqual(len({c['identity'] for g in result['classes'] for c in g['cases']}), 3)

    def test_global_duplicate_across_reports_rejected(self):
        self.groups[ROOT][ROOT]['expected_tests'] = 1
        self.xml(ROOT, [(ROOT,'parent()')]); self.xml(CHILD, [(ROOT,'parent()'), (CHILD,'child()')])
        with self.assertRaisesRegex(packet.PacketError, 'Duplicate test identity'): self.summary()

    def test_duplicate_within_report_rejected(self):
        self.xml(ROOT); self.xml(CHILD, [(ROOT,'same()'), (ROOT,'same()')])
        with self.assertRaisesRegex(packet.PacketError, 'Duplicate test identity'): self.summary()

    def test_unlisted_ancestor_owner_rejected(self):
        self.groups[ROOT][CHILD]['owners'] = [CHILD]
        self.write()
        with self.assertRaisesRegex(packet.PacketError, 'owner mismatch'): self.summary()

    def test_foreign_sibling_simple_and_undeclared_descendant_owner_rejected(self):
        self.xml(ROOT)
        for owner in ('foreign.Outer', ROOT+'$Sibling', 'Outer', 'Outer$Actual', CHILD+'$New'):
            with self.subTest(owner=owner):
                self.xml(CHILD, [(owner,'case()'), (CHILD,'other()')])
                with self.assertRaisesRegex(packet.PacketError, 'owner mismatch'): self.summary()

    def test_missing_child_rejected(self):
        self.xml(ROOT)
        with self.assertRaisesRegex(packet.PacketError, 'missing='): self.summary()

    def test_unexpected_fresh_child_rejected(self):
        self.write(); self.xml(CHILD+'$New', [(CHILD+'$New','new()')])
        with self.assertRaisesRegex(packet.PacketError, 'unexpected='): self.summary()

    def test_stale_child_rejected(self):
        path = self.write(); os.utime(path, ns=(self.since-1, self.since-1))
        with self.assertRaisesRegex(packet.PacketError, 'Stale XML'): self.summary()

    def test_unrelated_stale_report_ignored(self):
        self.write(); path = self.xml('example.Unrelated', [('example.Unrelated','case()')])
        os.utime(path, ns=(self.since-1, self.since-1))
        self.assertEqual(self.summary()['ignored_stale_reports'], 1)

    def test_empty_descendant_cannot_justify_empty_parent(self):
        self.xml(ROOT); self.xml(CHILD)
        with self.assertRaisesRegex(packet.PacketError, 'report test count'): self.summary()

    def test_xml_wrong_declared_totals_rejected(self):
        path = self.write(); tree = ET.parse(path); tree.getroot().set('tests', '99'); tree.write(path)
        with self.assertRaisesRegex(packet.PacketError, 'XML count mismatch'): self.summary()

    def test_expected_report_count_cannot_be_inferred(self):
        self.write(); self.groups[ROOT][CHILD]['expected_tests'] = 3
        with self.assertRaisesRegex(packet.PacketError, 'report test count'): self.summary()

    def test_empty_positive_parent_is_rejected(self):
        self.groups[ROOT][ROOT]['expected_tests'] = 1
        self.write()
        with self.assertRaisesRegex(packet.PacketError, 'report test count'): self.summary()

    def test_environment_checked_on_empty_parent(self):
        self.write(); self.xml(ROOT, user_dir='/wrong')
        result = self.summary(); self.assertFalse(result['clean']); self.assertIn('wrong user.dir', result['issues'][0])
        with self.assertRaisesRegex(packet.PacketError, 'Environment evidence rejected'):
            compare._Evidence(self.root, self.classes, self.since, {}, report_groups=self.groups)

    def test_default_four_roots_reject_seven_fresh_reports(self):
        self.four_groups(); self.write_four()
        with self.assertRaisesRegex(packet.PacketError, 'Report class set mismatch'):
            packet.junit_summary(self.root, self.classes, self.since, {})

    def test_default_all_reports_still_reject_empty_parent(self):
        self.xml(ROOT); self.xml(CHILD, [(CHILD, 'child()')])
        with self.assertRaisesRegex(packet.PacketError, 'No observed testcases'):
            packet.junit_summary(self.root, [ROOT, CHILD], self.since, {})

    def test_default_flat_ancestor_owner_still_rejected(self):
        self.xml(CHILD, [(ROOT,'case()')])
        with self.assertRaisesRegex(packet.PacketError, 'owner mismatch'):
            packet.junit_summary(self.root, [CHILD], self.since, {})

    def test_default_flat_simple_owner_and_output_shape_unchanged(self):
        self.xml(ROOT, [('Outer','case()')])
        result = packet.junit_summary(self.root, [ROOT], self.since, {})
        self.assertEqual(result['classes'][0]['cases'][0], dict(identity=ROOT+'#case()', status='passed', message='', type='', reported_classname='Outer'))
        self.assertNotIn('report_groups', result)


class NestedSchemaTests(NestedFixture):
    def test_handoff_root_aggregate_validated(self):
        tests = self.declared_tests(); tests['expected_tests'][ROOT] = 99
        with self.assertRaisesRegex(packet.PacketError, 'root totals'): handoff._tests(tests)

    def test_no_missing_ancestors_or_overlapping_roots(self):
        self.groups[ROOT][DEEP] = self.groups[ROOT].pop(CHILD)
        with self.assertRaisesRegex(packet.PacketError, 'ancestor'): handoff._tests(self.declared_tests())

    def test_zero_leaf_and_all_empty_rejected(self):
        for name in (ROOT, CHILD): self.groups[ROOT][name]['expected_tests'] = 0
        with self.assertRaises(packet.PacketError): packet.report_inventory(self.classes, self.groups)

    def test_foreign_sibling_and_descendant_owner_declarations_rejected(self):
        sibling = ROOT+'$Sibling'; self.groups[ROOT][sibling] = dict(expected_tests=1, owners=[sibling])
        for owner in ('foreign.Outer', sibling, DEEP, 'Outer'):
            with self.subTest(owner=owner):
                self.groups[ROOT][CHILD]['owners'] = [owner]
                with self.assertRaises(packet.PacketError): packet.report_inventory(self.classes, self.groups)

    def test_declaration_unknown_fields_boolean_counts_and_budgets_reject(self):
        for mutation in (lambda g:g[ROOT][ROOT].update(extra=1),
                         lambda g:g[ROOT][ROOT].update(expected_tests=False),
                         lambda g:g[ROOT][CHILD].update(owners=[CHILD,CHILD]),
                         lambda g:g.update({'foreign.Root':{}})):
            groups = copy.deepcopy(self.groups); mutation(groups)
            with self.assertRaises(packet.PacketError): packet.report_inventory(self.classes, groups)
        with mock.patch.object(packet, 'MAX_REPORT_SUITES', 1):
            with self.assertRaisesRegex(packet.PacketError, 'bounded inventory|limit'): packet.report_inventory(self.classes, self.groups)

    def test_methods_expected_cases_are_group_actual_identities(self):
        tests = self.declared_tests(); tests.update(methods={ROOT:['selected']}, expected_cases={ROOT:[ROOT+'#parent()', CHILD+'#child()']})
        handoff._tests(tests)
        tests['expected_cases'][ROOT][1] = ROOT+'$Foreign#child()'
        with self.assertRaisesRegex(packet.PacketError, 'owning Class'): handoff._tests(tests)

    def test_explicit_json_duplicate_keys_rejected(self):
        path = self.base/'groups.json'; path.write_text('{"example.Outer":{},"example.Outer":{}}')
        with self.assertRaisesRegex(packet.PacketError, 'Duplicate JSON'): packet.read_report_groups(path)


class NestedEvidenceTests(NestedFixture):
    def test_export_preserves_topology_actual_identities_and_comparison_only_scope(self):
        self.write(); output = self.base/'xml.json'
        result = evidence.save_junit_evidence(self.root, self.classes, self.since, {}, str(output), report_groups=self.groups)
        document = json.loads(output.read_text())
        self.assertEqual(document['version'], 1); self.assertEqual(document['kind'], 'junit-xml-observations')
        self.assertEqual(document['report']['report_groups'], self.groups)
        self.assertEqual(next(g for g in document['report']['classes'] if g['name'] == CHILD)['cases'][0]['identity'], ROOT+'#parent()')
        self.assertEqual(result['classes'], 2); self.assertNotIn('SECRET', output.read_text())

    def test_nested_selected_hardlink_and_symlink_rejected(self):
        path = self.write(); alias = self.base/'alias'; os.link(path, alias)
        with self.assertRaisesRegex(packet.PacketError, 'Hardlinked'): compare._Evidence(self.root, self.classes, self.since, {}, report_groups=self.groups)
        alias.unlink(); target = self.base/'saved.xml'; path.rename(target); path.symlink_to(target)
        with self.assertRaisesRegex(packet.PacketError, 'symlink|canonical'): compare._Evidence(self.root, self.classes, self.since, {}, report_groups=self.groups)

    def test_selected_child_included_in_byte_budget(self):
        self.write()
        with mock.patch.object(compare, 'MAX_SELECTED_BYTES', 1):
            with self.assertRaisesRegex(packet.PacketError, 'byte limit'): compare._Evidence(self.root, self.classes, self.since, {}, report_groups=self.groups)

    def test_changed_child_rechecked(self):
        path = self.write(); observed = compare._Evidence(self.root, self.classes, self.since, {}, report_groups=self.groups)
        path.write_bytes(path.read_bytes()+b'\n')
        with self.assertRaisesRegex(packet.PacketError, 'changed'): observed.recheck()

    def test_cli_opt_in_and_default_rejection(self):
        self.write(); path = self.base/'groups.json'; path.write_text(json.dumps(self.groups))
        args = ['junit','--worktree',str(self.root),'--class',ROOT,'--since','2026-01-01T00:00:00Z']
        stdout = io.StringIO(); stderr = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            status = packet.main(args+['--report-groups',str(path)])
        self.assertEqual(status, 0, stderr.getvalue()); self.assertEqual(json.loads(stdout.getvalue())['tests'],2)
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()): self.assertEqual(packet.main(args),2)


class NestedDiagnosticsBoundaryTests(NestedFixture):
    def test_retained_incomplete_receipt_is_not_reclassified_by_xml(self):
        import junit_diagnostics as diagnostics
        result,_ = self.execute(action=lambda:self.xml(ROOT), export=True)
        self.assertEqual(result['status'], 'incomplete')
        path = self.base/'receipt.json'; before = path.read_bytes()
        # Fresh successful XML cannot rewrite or promote the older receipt.
        self.write()
        with self.assertRaisesRegex(packet.PacketError,'incomplete|terminal'):
            diagnostics.inspect_diagnostics(str(path), CHILD)
        self.assertEqual(path.read_bytes(), before)

    def test_nested_receipt_diagnostics_uses_actual_identities(self):
        import junit_diagnostics as diagnostics
        self.execute(export=True)
        path = self.base/'receipt.json'; before = path.read_bytes()
        result = json.loads(diagnostics.inspect_diagnostics(str(path), CHILD))
        self.assertEqual(result['root_selector'],ROOT)
        self.assertEqual(result['run_counts']['tests'],2)
        self.assertEqual(path.read_bytes(), before)


class NestedRunTests(NestedFixture):
    def test_selected_run_plumbing_root_selector_and_all_report_rewrites(self):
        result, calls = self.execute(export=True, compact=True)
        self.assertEqual(calls, [['--normal','-Dmse=off','-Dtest='+ROOT,'-Dsoniccd.opening.egl=true','test','-B']])
        self.assertTrue(result['clean']); self.assertEqual(result['status'], 'passed')
        document = json.loads((self.base/'receipt.json').read_text())
        self.assertEqual(document['run']['report_groups'], self.groups)
        self.assertEqual(document['run']['expected_tests'], {ROOT:2})
        self.assertEqual(len(document['run']['junit']['classes']),2)
        self.assertEqual(next(g for g in document['run']['junit']['classes'] if g['name'] == CHILD)['cases'][0]['identity'], ROOT+'#parent()')

    def test_four_root_seven_suite_handoff_to_run_to_receipt(self):
        self.four_groups()
        result,calls = self.execute(real_handoff=True,action=self.write_four,export=True)
        self.assertEqual(result['status'],'passed')
        self.assertEqual(calls[0][2],'-Dtest='+','.join(self.classes))
        self.assertEqual(len(result['junit']['classes']),7); self.assertEqual(result['junit']['tests'],7)

    def test_real_handoff_schema_to_run_to_receipt(self):
        result, calls = self.execute(real_handoff=True, export=True)
        self.assertEqual(result['status'], 'passed')
        self.assertEqual(calls[0][2], '-Dtest='+ROOT)

    def test_actual_dependencies_rechecked_at_terminal(self):
        def action(): self.write(); self.dependency.write_bytes(b'changed dependency\n')
        result,_ = self.execute(real_handoff=True, action=action)
        self.assertEqual(result['status'], 'incomplete')
        self.assertIn('Handoff declarations/dependencies changed',result['evidence_error'])

    def test_nested_method_literal_inventory_verified_across_reports(self):
        changes = dict(methods={ROOT:['selected']}, expected_cases={ROOT:[ROOT+'#parent()',CHILD+'#child()']})
        result,calls = self.execute(test_changes=changes)
        self.assertTrue(result['clean']); self.assertEqual(calls[0][2],'-Dtest='+ROOT+'#selected')
        changes['expected_cases'][ROOT][1] = CHILD+'#different()'
        result,_ = self.execute(test_changes=changes)
        self.assertEqual(result['status'],'incomplete'); self.assertIn('case identity',result['evidence_error'])
        self.assertEqual(result['case_identity_issues'][0]['missing'],[CHILD+'#different()'])

    def test_source_change_stays_incomplete(self):
        ids = [dict(head=self.head, fingerprint=x*64, files=1, bytes=2, gitlinks=0) for x in ('2','2','3')]
        result,_ = self.execute(source=mock.Mock(side_effect=ids))
        self.assertEqual(result['status'],'incomplete'); self.assertIn('Source changed',result['evidence_error'])

    def test_unrewritten_child_even_future_timestamp_rejected(self):
        path = self.write(); future = time.time_ns()+60_000_000_000; os.utime(path,ns=(future,future))
        result,_ = self.execute(action=lambda:self.xml(ROOT))
        self.assertEqual(result['status'],'incomplete'); self.assertIn(CHILD,result['evidence_error'])

    def test_wrong_java_on_empty_parent_and_wrong_environment_stay_failed(self):
        def action(): self.write(); self.xml(ROOT, java='24',user_dir='/wrong')
        result,_ = self.execute(action=action,export=True)
        self.assertEqual(result['status'],'failed'); self.assertFalse(result['clean'])
        self.assertIn('Java major mismatch: '+ROOT,result['issues'])
        self.assertIn('wrong user.dir',result['junit']['issues'][0])

    def test_nonzero_terminal_with_green_xml_stays_failed(self):
        result,_ = self.execute(code=1,export=True)
        self.assertEqual(result['status'],'failed'); self.assertEqual(result['maven_returncode'],1)

    def test_missing_terminal_never_exports(self):
        with self.assertRaisesRegex(packet.PacketError, 'terminal'): self.execute(terminal=False,export=True)
        self.assertFalse((self.base/'receipt.json').exists())

    def test_skip_and_failure_stay_failed(self):
        for status in ('skipped','failure','error'):
            with self.subTest(status=status):
                result,_ = self.execute(action=lambda:(self.xml(ROOT),self.xml(CHILD,[(ROOT,'parent()'),(CHILD,'child()')],status=status)))
                self.assertEqual(result['status'],'failed'); self.assertFalse(result['clean'])

    def test_leaf_count_mismatch_never_passes(self):
        result,_ = self.execute(action=lambda:(self.xml(ROOT),self.xml(CHILD,[(CHILD,'only()')])))
        self.assertEqual(result['status'],'incomplete'); self.assertFalse(result['clean'])

    def test_admission_drift_blocks_callbacks(self):
        calls=[]
        identity = dict(head=self.head,fingerprint='2'*64,files=1,bytes=2,gitlinks=0)
        changed = dict(identity,fingerprint='3'*64)
        with self.assertRaisesRegex(packet.PacketError,'awaiting admission'):
            self.execute(source=mock.Mock(side_effect=[identity,changed]),action=lambda:calls.append('launched'))
        self.assertEqual(calls,[])


if __name__ == '__main__': unittest.main()
