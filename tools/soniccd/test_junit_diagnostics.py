"""Owned synthetic receipt/XML tests; never inspect engine reports or execute Java/Maven.
Run: python3 -B -m unittest discover -s tools/soniccd -p test_junit_diagnostics.py
Origin: 2026-11-05 bounded diagnostic inspection task.
"""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import junit_diagnostics as diagnostics

CLASS = 'example.TestObserver'


class DiagnosticsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='junit-diagnostics-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / 'worktree'
        self.reports = self.root / 'target/surefire-reports'
        self.reports.mkdir(parents=True)
        self.xml = self.reports / ('TEST-' + CLASS + '.xml')
        self.evidence = self.base / 'evidence.json'

    def fixture(self, out='OBS ordinary\n', err='ERR ordinary\n', outcome='error',
                message='observer boundary', name='observeRoute()', classname=CLASS):
        counts = dict(tests=1, failures=int(outcome == 'failure'), errors=int(outcome == 'error'),
                      skipped=int(outcome == 'skipped'))
        suite = ET.Element('testsuite', name=CLASS, **{k: str(v) for k, v in counts.items()})
        properties = {'java.version': '25.0.1', 'user.dir': str(self.root),
                      'openggf.headless.gl': 'surfaceless-egl'}
        props = ET.SubElement(suite, 'properties')
        for key, value in {**properties, 'API_TOKEN': 'DO-NOT-PRINT-SECRET'}.items():
            ET.SubElement(props, 'property', name=key, value=value)
        case = ET.SubElement(suite, 'testcase', classname=classname, name=name)
        if outcome != 'passed':
            ET.SubElement(case, outcome, message=message, type='example.Unowned').text = 'exact trace\n'
        ET.SubElement(suite, 'system-out').text = out
        ET.SubElement(suite, 'system-err').text = err
        ET.ElementTree(suite).write(self.xml, encoding='utf-8', xml_declaration=True)
        raw = self.xml.read_bytes()
        case_info = dict(identity=CLASS + '#' + name, status=outcome, message=message if outcome != 'passed' else '',
                         type='example.Unowned' if outcome != 'passed' else '')
        if classname != CLASS:
            case_info['reported_classname'] = classname
        group = dict(name=CLASS, xml=str(self.xml), mtime_ns=self.xml.stat().st_mtime_ns,
                     properties=properties, counts=counts, cases=[case_info],
                     xml_sha256=hashlib.sha256(raw).hexdigest(), xml_bytes=len(raw))
        # Exact integration_run.source_identity shape: gitlinks is a count, not a list.
        source = dict(head='1' * 40, fingerprint='a' * 64, files=1, bytes=8, gitlinks=0)
        clean = outcome == 'passed'
        self.document = dict(version=1, kind='source-bound-run-observations', attribution='Original terminal observed evidence; non-atomic.',
            limits={}, run=dict(version=1, id='owned', stage='installed', executed=True,
                requested_maven_args=['-Dmse=off', '-Dtest=' + CLASS, 'test', '-B'], lane='normal', classes=[CLASS],
                expected_tests={CLASS: 1}, source_before=source, source_after=copy.deepcopy(source),
                status='passed' if clean else 'failed', clean=clean, issues=[], maven_returncode=0 if clean else 1,
                started_at='2026-11-05T00:00:00+00:00', completed_at='2026-11-05T00:01:00+00:00',
                junit=dict(worktree=str(self.root), head_at_inspection=source['head'], since_ns=group['mtime_ns'] - 1,
                    attribution='XML observations only', **counts, classes=[group], issues=[], ignored_stale_reports=0,
                    requested_properties={'openggf.headless.gl': 'surfaceless-egl'}, clean=clean)))
        self.save_receipt()
        return suite

    def save_receipt(self):
        self.evidence.write_text(json.dumps(self.document, ensure_ascii=False) + '\n', encoding='utf-8')
        self.evidence.chmod(0o600)

    def invoke(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = diagnostics.main(['--evidence', str(self.evidence), '--class', CLASS, *args])
        return code, out.getvalue(), err.getvalue()

    def refresh_xml(self, suite=None):
        if suite is not None:
            ET.ElementTree(suite).write(self.xml, encoding='utf-8', xml_declaration=True)
        raw = self.xml.read_bytes()
        group = self.document['run']['junit']['classes'][0]
        group.update(xml_sha256=hashlib.sha256(raw).hexdigest(), xml_bytes=len(raw),
                     mtime_ns=self.xml.stat().st_mtime_ns)
        self.document['run']['junit']['since_ns'] = group['mtime_ns'] - 1
        self.save_receipt()

    def rejected(self, *args):
        code, out, err = self.invoke(*args)
        self.assertEqual(code, 2)
        self.assertEqual(out, '')
        result = json.loads(err)
        self.assertEqual(result['inspection_status'], 'rejected')
        self.assertTrue(result['incomplete'])
        self.assertLessEqual(len(err.encode('utf-8')), 4096)
        return result

    def test_full96_and_huge_single_line_cannot_escape_the_default_stdout_budget(self):
        images = '|'.join(f'{n:02x}:' + 'a5' * 64 for n in range(96))
        self.fixture(out='OBS full96=' + images + ' reserved=' + 'x' * 1_000_000 + '\nOBS tail\n')
        code, out, err = self.invoke()
        self.assertEqual(code, 0, err)
        self.assertLessEqual(len(out.encode('utf-8')), 32768)
        data = json.loads(out)
        self.assertEqual(data['output_bytes'], len(out.encode('utf-8')))
        self.assertGreater(data['diagnostics']['truncated_characters'], 1_000_000)
        self.assertFalse(data['diagnostics']['selected_complete'])
        self.assertEqual(data['xml']['sha256'], hashlib.sha256(self.xml.read_bytes()).hexdigest())
        self.assertEqual(data['evidence']['sha256'], hashlib.sha256(self.evidence.read_bytes()).hexdigest())

    def test_failed_observer_inspects_successfully_without_promoting_its_verdict_or_counts(self):
        self.fixture()
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        result = json.loads(out)
        self.assertEqual(result['run_status'], 'failed')
        self.assertFalse(result['run_clean'])
        self.assertEqual(result['maven_returncode'], 1)
        self.assertEqual(result['counts'], dict(tests=1, failures=0, errors=1, skipped=0))
        self.assertIn('not independently established command completion', result['attribution'])
        self.assertIn('not', result['attribution'])
        self.assertNotIn('DO-NOT-PRINT-SECRET', out + err)
        self.assertNotIn('API_TOKEN', out + err)

    def test_pass_failure_skip_and_nonzero_only_receipts_keep_exact_status(self):
        for status in ('passed', 'failure', 'skipped'):
            with self.subTest(status=status):
                self.fixture(outcome=status)
                code, out, err = self.invoke()
                self.assertEqual((code, err), (0, ''))
                result = json.loads(out)
                self.assertEqual(result['run_status'], 'passed' if status == 'passed' else 'failed')
                self.assertEqual(result['counts']['tests'], 1)
        self.fixture(outcome='passed')
        self.document['run'].update(status='failed', clean=False, maven_returncode=1)
        self.save_receipt()
        self.assertEqual(json.loads(self.invoke()[1])['run_status'], 'failed')

    def test_recorded_environment_and_expected_count_failures_remain_inspectable_failures(self):
        suite = self.fixture(outcome='passed')
        suite.find('./properties/property').set('value', '')
        group = self.document['run']['junit']['classes'][0]
        group['properties']['java.version'] = ''
        self.document['run']['junit'].update(clean=False, issues=['missing Java version'])
        self.document['run'].update(status='failed', clean=False)
        self.refresh_xml(suite)
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        self.assertEqual(json.loads(out)['run_status'], 'failed')
        self.fixture(outcome='passed')
        self.document['run']['expected_tests'][CLASS] = 2
        self.document['run'].update(status='failed', clean=False, issues=['Unexpected test count'])
        self.save_receipt()
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        self.assertFalse(json.loads(out)['expected_count_matches'])
        self.assertEqual(json.loads(out)['counts']['tests'], 1)

    def test_matching_xml_cannot_make_a_contradictory_environment_receipt_clean(self):
        for key, value in (("openggf.headless.gl", "glfw"), ("java.version", None),
                           ("java.version", ""), ("user.dir", "/different-worktree"),
                           ("openggf.headless.gl", None)):
            with self.subTest(property=key, value=value):
                suite = self.fixture(outcome="passed")
                node = next(p for p in suite.findall("./properties/property") if p.get("name") == key)
                if value is None:
                    suite.find("properties").remove(node)
                else:
                    node.set("value", value)
                self.document["run"]["junit"]["classes"][0]["properties"][key] = value
                self.refresh_xml(suite)
                self.rejected()

    def test_unselected_environment_contradiction_cannot_be_hidden_by_selected_success(self):
        self.fixture(outcome="passed")
        run = self.document["run"]
        other = copy.deepcopy(run["junit"]["classes"][0])
        other["name"] = "example.Other"
        other["cases"][0]["identity"] = "example.Other#observeRoute()"
        other["properties"]["user.dir"] = "/different-worktree"
        run["classes"].append(other["name"])
        run["expected_tests"][other["name"]] = 1
        run["junit"]["classes"].append(other)
        run["junit"]["tests"] = 2
        self.save_receipt()
        self.rejected()

    def test_prefixes_are_repeatable_literal_and_preserve_leading_space(self):
        self.fixture(out='[OBS].* literal\n[OBS] other\n OBS leading\n')
        code, out, err = self.invoke('--prefix', '[OBS].*')
        self.assertEqual((code, err), (0, ''))
        data = json.loads(out)
        streams = [r['text'] for r in data['diagnostics']['records'] if r['kind'] == 'system-out']
        self.assertEqual(streams, ['[OBS].* literal\n'])
        self.assertEqual(data['diagnostics']['filtered_lines'], 3)
        self.assertTrue(data['diagnostics']['selected_complete'])
        self.assertFalse(data['diagnostics']['all_diagnostics_complete'])
        code, out, err = self.invoke('--prefix', '[OBS]', '--prefix', ' OBS')
        self.assertEqual((code, err), (0, ''))
        self.assertEqual([r['text'] for r in json.loads(out)['diagnostics']['records'] if r['kind'] == 'system-out'],
                         ['[OBS].* literal\n', '[OBS] other\n', ' OBS leading\n'])

    def test_unicode_crlf_and_character_truncation_are_explicit_and_do_not_split_utf8(self):
        self.fixture(out=' OBS ü🙂\r\nOBS second\n', name='literal([ü=44],  🙂 # ())', classname='TestObserver')
        code, out, err = self.invoke('--prefix', ' OBS')
        self.assertEqual((code, err), (0, ''))
        records = json.loads(out)['diagnostics']['records']
        self.assertEqual([r['text'] for r in records if r['kind'] == 'system-out'], [' OBS ü🙂\n'])
        self.assertEqual(records[0]['case_identity'], CLASS + '#literal([ü=44],  🙂 # ())')
        self.fixture(out='OBS 🙂🙂🙂🙂\n')
        code, out, err = self.invoke('--max-line-chars', '6')
        self.assertEqual((code, err), (0, ''))
        row = next(r for r in json.loads(out)['diagnostics']['records'] if r['kind'] == 'system-out')
        self.assertEqual(row['text'], 'OBS 🙂🙂')
        self.assertEqual(row['input_characters'], 9)
        self.assertEqual(row['omitted_characters'], 3)
        self.assertTrue(row['truncated'])

    def test_line_and_total_byte_budgets_account_for_every_omission(self):
        self.fixture(out=''.join('OBS ' + str(n) + 'x' * 2000 + '\n' for n in range(100)))
        code, out, err = self.invoke('--max-lines', '256', '--max-line-chars', '4096', '--max-output-bytes', '4096')
        self.assertEqual((code, err), (0, ''))
        result = json.loads(out)
        stats = result['diagnostics']
        self.assertLessEqual(len(out.encode('utf-8')), 4096)
        self.assertEqual(result['output_bytes'], len(out.encode('utf-8')))
        self.assertGreater(stats['omitted_lines'], 0)
        self.assertEqual(stats['selected_lines'], stats['emitted_lines'] + stats['omitted_lines'])
        self.assertEqual(stats['selected_characters'], stats['emitted_text_characters'] + stats['omitted_characters'] + stats['truncated_characters'])
        code, out, err = self.invoke('--max-lines', '0')
        self.assertEqual((code, err), (0, ''))
        stats = json.loads(out)['diagnostics']
        self.assertEqual(stats['records'], [])
        self.assertEqual(stats['omitted_lines'], stats['selected_lines'])

    def test_huge_outcome_message_and_identity_are_bounded_without_silent_relabelling(self):
        self.fixture(message='reserved=' + 'f' * 100_000, name='case(' + 'ü' * 10_000 + ')')
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        data = json.loads(out)
        self.assertLessEqual(len(out.encode('utf-8')), 32768)
        self.assertGreater(data['diagnostics']['case_identity_truncations'], 0)
        record = data['diagnostics']['records'][0]
        self.assertEqual(len(record['case_identity']), diagnostics.MAX_ID_CHARACTERS)
        self.assertTrue(record['case_identity_truncated'])
        self.assertEqual(record['case_identity_sha256'], hashlib.sha256((CLASS + '#case(' + 'ü' * 10_000 + ')').encode()).hexdigest())
        self.assertEqual(record['text'], ('reserved=' + 'f' * 100_000)[:512])
        self.assertTrue(record['truncated'])

    def test_xml_hash_size_missing_and_staleness_cannot_be_promoted(self):
        self.fixture()
        self.xml.write_bytes(self.xml.read_bytes() + b'\n')
        self.rejected()
        self.fixture()
        self.document['run']['junit']['classes'][0]['xml_bytes'] += 1
        self.save_receipt(); self.rejected()
        self.fixture()
        self.document['run']['junit']['since_ns'] = self.xml.stat().st_mtime_ns + 1
        self.save_receipt(); self.rejected()
        self.fixture(); self.xml.unlink(); self.rejected()

    def test_changed_mtime_rejects_even_when_hash_bytes_are_identical(self):
        self.fixture()
        stamp = self.xml.stat().st_mtime_ns
        os.utime(self.xml, ns=(stamp + 10_000, stamp + 10_000))
        self.rejected()

    def test_xml_class_case_count_and_property_mismatches_reject_after_repinning_bytes(self):
        for field in ('suite', 'case', 'count', 'property', 'case-name'):
            with self.subTest(field=field):
                suite = self.fixture()
                if field == 'suite': suite.set('name', 'example.Other')
                elif field == 'case': suite.find('testcase').set('classname', 'foreign.TestObserver')
                elif field == 'count': suite.set('tests', '2')
                elif field == 'property': suite.find('./properties/property').set('value', '26.0')
                else: suite.find('testcase').set('name', 'changed()')
                self.refresh_xml(suite)
                self.rejected()

    def test_receipt_case_count_property_and_class_inventory_mismatches_reject(self):
        for field in ('case', 'count', 'property', 'classes', 'aggregate'):
            with self.subTest(field=field):
                self.fixture()
                group = self.document['run']['junit']['classes'][0]
                if field == 'case': group['cases'][0]['identity'] = CLASS + '#another()'
                elif field == 'count': group['counts']['tests'] = 2
                elif field == 'property': group['properties']['java.version'] = '26.0'
                elif field == 'classes': self.document['run']['classes'] = ['example.Other']
                else: self.document['run']['junit']['errors'] = 0
                self.save_receipt(); self.rejected()

    def test_incomplete_unequal_source_and_false_positive_run_receipts_reject(self):
        mutations = [lambda r: r.pop('completed_at'), lambda r: r.update(status='incomplete'),
            lambda r: r.update(executed=False), lambda r: r.pop('source_after'),
            lambda r: r['source_after'].update(fingerprint='b' * 64), lambda r: r.pop('maven_returncode'),
            lambda r: r.pop('junit'), lambda r: r.update(evidence_error='incomplete XML'),
            lambda r: r.update(status='passed', clean=True, maven_returncode=0),
            lambda r: r.update(started_at='2026-11-05T00:02:00+00:00'),
            lambda r: r.update(completed_at='2026-11-05T00:01:00'), lambda r: r.update(version=True)]
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                self.fixture(); mutate(self.document['run']); self.save_receipt(); self.rejected()

    def test_real_producer_scalar_gitlink_count_is_preserved_and_wrong_shapes_reject(self):
        self.fixture()
        for side in ('source_before', 'source_after'):
            self.document['run'][side]['gitlinks'] = 4
            self.document['run'][side]['files'] = 5
        self.save_receipt()
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        self.assertEqual(json.loads(out)['recorded_source']['identity_sha256'],
            hashlib.sha256(diagnostics._encode(self.document['run']['source_before'])).hexdigest())
        for wrong in ([], True, -1, '4'):
            self.fixture()
            for side in ('source_before', 'source_after'):
                self.document['run'][side]['gitlinks'] = wrong
            self.save_receipt(); self.rejected()

    def test_excessive_json_nesting_is_rejected_with_bounded_json_error(self):
        self.fixture()
        self.evidence.write_text('[' * 2000 + '0' + ']' * 2000)
        self.rejected()

    def test_selected_passing_class_cannot_hide_failed_run_or_read_unselected_reports(self):
        self.fixture(outcome='passed')
        run = self.document['run']
        other = copy.deepcopy(run['junit']['classes'][0])
        other.update(name='example.Other', xml=str(self.reports / 'TEST-example.Other.xml'))
        other['cases'][0].update(identity='example.Other#failed()', status='error', message='exact other failure')
        other['counts']['errors'] = 1
        run['classes'].append('example.Other')
        run['expected_tests']['example.Other'] = 1
        run['junit']['classes'].append(other)
        run['junit'].update(tests=2, errors=1, clean=False)
        run.update(status='failed', clean=False, maven_returncode=1)
        self.save_receipt()
        code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        result = json.loads(out)
        self.assertEqual(result['run_status'], 'failed')
        self.assertFalse(result['run_clean'])
        self.assertEqual(result['run_counts'], dict(tests=2, failures=0, errors=1, skipped=0))
        self.assertEqual(result['counts'], dict(tests=1, failures=0, errors=0, skipped=0))
        self.assertFalse(Path(other['xml']).exists())

    def test_standalone_xml_receipt_duplicate_json_and_nonfinite_numbers_are_unsupported(self):
        self.fixture(); self.document['kind'] = 'junit-xml-observations'; self.save_receipt(); self.rejected()
        self.fixture(); text = self.evidence.read_text(); self.evidence.write_text(text.replace('"version": 1', '"version": 1, "version": 1', 1)); self.rejected()
        self.fixture(); self.evidence.write_text(self.evidence.read_text().replace('"files": 1', '"files": NaN')); self.rejected()

    def test_unsafe_alias_paths_and_class_patterns_are_rejected(self):
        self.fixture()
        for path in ('relative.json', str(self.base) + '/./evidence.json', str(self.base) + '//evidence.json', str(self.base / 'absent' / '..' / 'evidence.json')):
            with self.subTest(path=path): self.rejected('--evidence', path)
        for name in ('TestObserver', 'example.*', 'example.TestObserver#method', 'example.Test\nOther'):
            with self.subTest(name=name): self.rejected('--class', name)
        inside = self.root / 'evidence.json'; inside.write_bytes(self.evidence.read_bytes())
        self.rejected('--evidence', str(inside))

    def test_file_parent_symlinks_hardlinks_and_nonregular_inputs_reject(self):
        self.fixture()
        alias = self.base / 'alias.json'; alias.symlink_to(self.evidence)
        self.rejected('--evidence', str(alias)); alias.unlink()
        os.link(self.evidence, alias); self.rejected(); alias.unlink()
        alias = self.base / 'alias.xml'; os.link(self.xml, alias); self.rejected(); alias.unlink()
        self.xml.unlink(); self.xml.symlink_to(self.base / 'missing.xml'); self.rejected()
        self.xml.unlink(); self.xml.mkdir(); self.rejected(); self.xml.rmdir()
        self.fixture(); linked = self.base / 'linked'; linked.symlink_to(self.base, target_is_directory=True)
        self.rejected('--evidence', str(linked / 'evidence.json'))

    def test_existing_reader_rejects_utf16_dtd_entities_and_depth_exhaustion(self):
        self.fixture(); self.xml.write_bytes(self.xml.read_bytes().decode().encode('utf-16')); self.refresh_xml(); self.rejected()
        self.fixture(); self.xml.write_bytes(b'<!DOCTYPE testsuite [<!ENTITY x "expanded">]>' + self.xml.read_bytes()); self.refresh_xml(); self.rejected()
        self.fixture(); raw = b'<testsuite>' + b'<n>' * 65 + b'x' + b'</n>' * 65 + b'</testsuite>'; self.xml.write_bytes(raw); self.refresh_xml(); self.rejected()

    def test_duplicate_owners_ambiguous_outcomes_and_nested_streams_reject(self):
        for kind in ('property', 'case', 'outcome', 'stream'):
            with self.subTest(kind=kind):
                suite = self.fixture()
                if kind == 'property': ET.SubElement(suite.find('properties'), 'property', name='java.version', value='25.0.1')
                elif kind == 'case': suite.append(copy.deepcopy(suite.find('testcase'))); suite.set('tests', '2'); suite.set('errors', '2')
                elif kind == 'outcome': ET.SubElement(suite.find('testcase'), 'failure')
                else: ET.SubElement(suite.find('system-out'), 'child').text = 'not plain character data'
                self.refresh_xml(suite); self.rejected()

    def test_changed_xml_receipt_and_parent_are_rejected_before_any_stdout(self):
        original = diagnostics.strict._Evidence.read
        for kind in ('xml', 'receipt', 'parent'):
            with self.subTest(kind=kind):
                self.fixture()
                def race(reader, path):
                    saved = original(reader, path)
                    if kind == 'xml': self.xml.write_bytes(saved.data + b'\n')
                    elif kind == 'receipt': self.evidence.write_bytes(self.evidence.read_bytes() + b'\n')
                    else:
                        moved = self.root / 'target/moved'
                        self.reports.rename(moved); self.reports.mkdir(); self.xml.write_bytes(saved.data)
                    return saved
                with mock.patch.object(diagnostics.strict._Evidence, 'read', race): self.rejected()
                if kind == 'parent':
                    import shutil
                    shutil.rmtree(self.root / 'target/moved')

    def test_input_line_and_file_budgets_fail_closed_without_partial_success(self):
        self.fixture()
        with mock.patch.object(diagnostics, 'MAX_INPUT_LINES', 2): self.rejected()
        self.fixture(); self.xml.write_bytes(b'x' * (diagnostics.packet.MAX_FILE + 1)); self.rejected()
        self.fixture(); self.evidence.write_bytes(b' ' * (diagnostics.packet.MAX_FILE + 1)); self.rejected()

    def test_invalid_budgets_prefixes_and_execute_flags_reject_without_work(self):
        self.fixture()
        for args in [('--max-lines', '-1'), ('--max-lines', '257'), ('--max-line-chars', '0'),
                     ('--max-line-chars', '4097'), ('--max-output-bytes', '4095'), ('--max-output-bytes', '262145'),
                     ('--prefix', ''), ('--prefix', 'x' * 129), ('--prefix', 'x', '--prefix', 'x'),
                     ('--prefix', 'x\ny'), ('--execute',), ('--cleanup',)]:
            with self.subTest(args=args): self.rejected(*args)

    def test_diagnostics_never_execute_discover_other_reports_or_write_inputs(self):
        self.fixture()
        unrelated = self.reports / 'TEST-foreign.Unrelated.xml'; unrelated.write_text('unrelated malformed evidence must not be read')
        before = {str(p.relative_to(self.base)): (p.read_bytes(), p.stat().st_mode, p.stat().st_mtime_ns)
                  for p in self.base.rglob('*') if p.is_file()}
        with mock.patch('subprocess.Popen', side_effect=AssertionError('no process')), \
                mock.patch('subprocess.run', side_effect=AssertionError('no subprocess')), \
                mock.patch('subprocess.check_output', side_effect=AssertionError('no Git')), \
                mock.patch.object(diagnostics.packet, 'worktree', side_effect=AssertionError('no worktree discovery')), \
                mock.patch.object(diagnostics.strict, '_inventory', side_effect=AssertionError('no report scan')), \
                mock.patch('os.scandir', side_effect=AssertionError('no directory inventory')), \
                mock.patch.dict('sys.modules', {'integration_run': None, 'maven_queue': None}):
            code, out, err = self.invoke()
        self.assertEqual((code, err), (0, ''))
        self.assertEqual(json.loads(out)['inspection_status'], 'verified-correspondence')
        after = {str(p.relative_to(self.base)): (p.read_bytes(), p.stat().st_mode, p.stat().st_mtime_ns)
                 for p in self.base.rglob('*') if p.is_file()}
        self.assertEqual(before, after)


if __name__ == '__main__':
    unittest.main()
