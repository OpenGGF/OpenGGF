"""Source/queue/report integration safety, using synthetic Git and XML only."""
import importlib
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

import integration_packet as packet


class RunFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'tree'
        self.root.mkdir()
        self.git('init', '-q')
        (self.root / 'src').mkdir()
        self.target = self.root / 'src/Main.java'
        self.target.write_text('class Main {}\n')
        (self.root / '.gitignore').write_text('target/\n')
        self.git('add', '.')
        self.git('-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                 'commit', '-qm', 'synthetic source')
        self.source = self.base / 'Final.java'
        self.source.write_bytes(self.target.read_bytes())
        self.source.chmod(0o444)
        self.spec = self.base / 'handoff.json'
        self.value = dict(version=1, id='test-r1', packet=dict(version=1,
            worktree_head=self.git('rev-parse', 'HEAD').strip(), files=[dict(
                source=str(self.source), sha256=packet.digest(self.source.read_bytes()),
                bytes=self.source.stat().st_size, mode='0444', utf8=True, lf=True,
                destination='src/Main.java', expected={'absent': True})]),
            dependencies=[], api_changes=['Adds Main'], known_defects=[], tests=dict(
                classes=['example.TestMain'], properties={'soniccd.opening.egl':'true'},
                expected_tests={'example.TestMain':1}, java_major=25))
        self.save()

    def save(self):
        self.spec.write_text(json.dumps(self.value)+'\n')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True,
                                       stderr=subprocess.DEVNULL)

    def reports(self, *, tests=1, java='25.0.1', skipped=False, extra=False):
        import xml.etree.ElementTree as ET
        reports = self.root/'target/surefire-reports'
        reports.mkdir(parents=True, exist_ok=True)
        suite = ET.Element('testsuite', name='example.TestMain', tests=str(tests),
                           failures='0', errors='0', skipped=str(int(skipped)))
        props = ET.SubElement(suite, 'properties')
        for name, value in {'java.version':java, 'user.dir':str(self.root),
                            'soniccd.opening.egl':'true'}.items():
            ET.SubElement(props, 'property', name=name, value=value)
        for i in range(tests):
            case = ET.SubElement(suite,'testcase',classname='example.TestMain',name=f'case{i}')
            if skipped and i==0:
                ET.SubElement(case,'skipped',message='explicit fixture prerequisite')
        ET.ElementTree(suite).write(reports/'TEST-example.TestMain.xml')
        if extra:
            (reports/'TEST-example.Other.xml').write_text('<testsuite/>')

    def runner(self):
        return importlib.import_module('integration_run')

    def invoke(self, action=None, *, code=0, before=None, **kwargs):
        run = self.runner()
        def queued(args, *, before_run, after_run, maven_stdout):
            if before:
                before()
            before_run()
            if action is None:
                self.reports()
            else:
                action()
            after_run(code)
            return code
        with mock.patch.object(run.queue, 'main', queued):
            return run.run_handoff(self.root,self.spec,execute=True,**kwargs)


class PlanTests(RunFixture):
    def test_cli_plan_accepts_installed_final_without_launching_maven(self):
        result = subprocess.run([sys.executable,'-B',str(Path(packet.__file__)),
            'run','--worktree',str(self.root),'--spec',str(self.spec)],capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        report=json.loads(result.stdout)
        self.assertFalse(report['executed'])
        self.assertEqual(report['stage'],'installed')
        self.assertEqual(report['requested_maven_args'],['-Dmse=off','-Dtest=example.TestMain',
            '-Dsoniccd.opening.egl=true','test','-B'])
        self.assertFalse((self.root/'target').exists())

    def test_handoff_can_emit_existing_packet_for_reviewed_import(self):
        self.value['packet']['files'][0]['expected']={'sha256':packet.digest(self.target.read_bytes())}
        self.save()
        result=subprocess.run([sys.executable,'-B',str(Path(packet.__file__)),
            'handoff','--worktree',str(self.root),'--spec',str(self.spec),'--packet-only'],
            capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(json.loads(result.stdout),self.value['packet'])

    def test_blocked_handoff_cannot_emit_an_import_packet(self):
        result=subprocess.run([sys.executable,'-B',str(Path(packet.__file__)),
            'handoff','--worktree',str(self.root),'--spec',str(self.spec),'--packet-only'],
            capture_output=True,text=True)
        self.assertEqual(result.returncode,2)
        self.assertFalse(result.stdout)

    @unittest.skipUnless(os.name=='posix','Synthetic executable fixture requires POSIX')
    def test_cli_uses_real_queue_and_keeps_subprocess_logs_out_of_json(self):
        binary=self.base/'bin';binary.mkdir()
        fake=binary/'mvn'
        fake.write_text('#!'+sys.executable+'\n'+'''import pathlib, sys, xml.etree.ElementTree as E
print('synthetic Maven process output')
p=pathlib.Path('target/surefire-reports');p.mkdir(parents=True,exist_ok=True)
s=E.Element('testsuite',name='example.TestMain',tests='1',failures='0',errors='0',skipped='0')
props=E.SubElement(s,'properties')
for key,value in {'java.version':'25.0.1','user.dir':str(pathlib.Path.cwd()),'soniccd.opening.egl':'true'}.items():
 E.SubElement(props,'property',name=key,value=value)
E.SubElement(s,'testcase',classname='example.TestMain',name='synthetic')
E.ElementTree(s).write(p/'TEST-example.TestMain.xml')
''')
        fake.chmod(0o755)
        environment={**os.environ,'PATH':str(binary)+os.pathsep+os.environ['PATH'],
                     'OPENGGF_MAVEN_QUEUE':'serial'}
        for name in ('MAVEN_ARGS','MAVEN_OPTS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'):
            environment.pop(name,None)
        result=subprocess.run([sys.executable,'-B',str(Path(packet.__file__)),
            'run','--worktree',str(self.root),'--spec',str(self.spec),'--execute'],
            env=environment,capture_output=True,text=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stderr)
        report=json.loads(result.stdout)
        self.assertEqual(report['status'],'passed')
        self.assertEqual(report['junit']['tests'],1)
        self.assertIn('synthetic Maven process output',result.stderr)


class ExecutionTests(RunFixture):
    def test_consumed_inventory_is_bound_to_the_pinned_spec(self):
        run=self.runner();original=run.handoff_summary
        def replace_after_read(*args,**kwargs):
            result=original(*args,**kwargs)
            self.value['id']='replacement'
            self.value['tests']['classes']=['example.TestReplacement']
            self.value['tests']['expected_tests']={'example.TestReplacement':2}
            self.save()
            return result
        with mock.patch.object(run,'handoff_summary',side_effect=replace_after_read):
            with self.assertRaises(packet.PacketError):
                run.run_handoff(self.root,self.spec)
        self.assertFalse((self.root/'target').exists())

    def test_terminal_success_binds_source_and_fresh_exact_counts(self):
        report=self.invoke()
        self.assertEqual(report['status'],'passed')
        self.assertTrue(report['clean'])
        self.assertEqual(report['maven_returncode'],0)
        self.assertEqual(report['junit']['tests'],1)
        self.assertEqual(report['source_before'],report['source_after'])

    def test_preimport_baseline_is_not_an_installed_final(self):
        self.target.write_text('old source\n')
        with self.assertRaises(packet.PacketError): self.invoke()

    def test_admission_wait_source_edit_prevents_execution(self):
        with self.assertRaises(packet.PacketError):
            self.invoke(before=lambda:self.target.write_text('class Changed {}\n'))
        self.assertFalse((self.root/'target').exists())

    def test_drift_outside_packet_rejects_result(self):
        other=self.root/'src/Other.java';other.write_text('class Other {}\n')
        def action():
            self.reports();other.write_text('class Changed {}\n')
        report=self.invoke(action)
        self.assertEqual(report['status'],'incomplete')
        self.assertFalse(report['clean'])

    def test_spec_edit_during_run_rejects_result(self):
        def action():
            self.reports();self.spec.write_text(self.spec.read_text()+' ')
        self.assertEqual(self.invoke(action)['status'],'incomplete')

    def test_nonzero_maven_cannot_be_overridden_by_green_xml(self):
        result=self.invoke(code=1)
        self.assertEqual(result['status'],'failed')
        self.assertFalse(result['clean'])

    def test_missing_xml_is_incomplete_not_a_build_pass(self):
        self.assertEqual(self.invoke(lambda:None)['status'],'incomplete')

    def test_declared_case_count_is_enforced(self):
        report=self.invoke(lambda:self.reports(tests=2))
        self.assertEqual(report['status'],'failed')
        self.assertTrue(report['issues'])

    def test_wrong_java_major_rejects_positive_result(self):
        report=self.invoke(lambda:self.reports(java='21.0.9'))
        self.assertFalse(report['clean'])
        self.assertIn('Java', ' '.join(report['issues']))

    def test_skips_remain_negative_observations(self):
        report=self.invoke(lambda:self.reports(skipped=True))
        self.assertEqual(report['status'],'failed')
        self.assertEqual(report['junit']['skipped'],1)

    def test_unexpected_fresh_suite_is_incomplete(self):
        self.assertEqual(self.invoke(lambda:self.reports(extra=True))['status'],'incomplete')

    def test_preexisting_future_dated_xml_must_actually_change(self):
        self.reports()
        xml=self.root/'target/surefire-reports/TEST-example.TestMain.xml'
        os.utime(xml,(4102444800,4102444800))
        self.assertEqual(self.invoke(lambda:None)['status'],'incomplete')

    def test_dependency_edit_rejects_result_even_outside_tree(self):
        dep=self.base/'dep.java';dep.write_text('final dep\n');dep.chmod(0o444)
        self.value['dependencies']=[dict(name='body',path=str(dep),sha256=packet.digest(dep.read_bytes()),
                                        bytes=dep.stat().st_size,mode='0444')];self.save()
        def action():
            self.reports();dep.chmod(0o644);dep.write_text('changed dep\n');dep.chmod(0o444)
        self.assertEqual(self.invoke(action)['status'],'incomplete')

    def test_cancelled_queue_cannot_produce_success(self):
        run=self.runner()
        with mock.patch.object(run.queue,'main',side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                run.run_handoff(self.root,self.spec,execute=True)


class IdentityTests(RunFixture):
    def test_full_tracked_bytes_are_checked_even_with_assume_unchanged(self):
        run=self.runner();first=run.source_identity(self.root)
        self.git('update-index','--assume-unchanged','src/Main.java')
        self.target.write_text('class Fail {}\n')
        self.assertNotEqual(first['fingerprint'],run.source_identity(self.root)['fingerprint'])

    def test_new_untracked_source_changes_identity(self):
        run=self.runner();first=run.source_identity(self.root)
        (self.root/'src/New.java').write_text('class New {}\n')
        self.assertNotEqual(first['fingerprint'],run.source_identity(self.root)['fingerprint'])

    def test_gitlink_pin_change_is_visible_despite_display_ignore(self):
        run=self.runner();first=run.source_identity(self.root)
        self.git('update-index','--add','--cacheinfo','160000,'+'1'*40+',docs/reference')
        self.git('config','submodule.docs/reference.ignore','all')
        self.assertNotEqual(first['fingerprint'],run.source_identity(self.root)['fingerprint'])

    def test_missing_tracked_file_changes_identity(self):
        run=self.runner();first=run.source_identity(self.root);self.target.unlink()
        self.assertNotEqual(first['fingerprint'],run.source_identity(self.root)['fingerprint'])

    def test_plan_rejects_unknown_lane(self):
        with self.assertRaises(packet.PacketError):
            self.runner().run_handoff(self.root,self.spec,lane='unsafe')


class CompactOutputTests(RunFixture):
    """Exercise full verification before presentation, with no real Maven."""
    def select_cases(self, count):
        self.value['tests'].update(methods={'example.TestMain':['selected']},
            expected_cases={'example.TestMain':[f'example.TestMain#case{i}' for i in range(count)]})
        self.value['tests']['expected_tests']['example.TestMain'] = count
        self.save()

    def outcome_reports(self, statuses, *, owner='example.TestMain', message='diagnostic',
                        type_name='example.Cause', long_identity=False):
        import xml.etree.ElementTree as ET
        reports = self.root/'target/surefire-reports'
        reports.mkdir(parents=True, exist_ok=True)
        suite = ET.Element('testsuite', name=owner, tests=str(len(statuses)),
            failures=str(statuses.count('failure')), errors=str(statuses.count('error')),
            skipped=str(statuses.count('skipped')))
        properties = ET.SubElement(suite, 'properties')
        for key, value in {'java.version':'25.0.1', 'user.dir':str(self.root),
                           'soniccd.opening.egl':'true'}.items():
            ET.SubElement(properties, 'property', name=key, value=value)
        for index, status in enumerate(statuses):
            name = ('x'*2000 if long_identity else 'case')+str(index)
            case = ET.SubElement(suite, 'testcase', classname=owner, name=name)
            if status != 'passed':
                ET.SubElement(case, status, message=message, type=type_name)
        path = reports/f'TEST-{owner}.xml'
        ET.ElementTree(suite).write(path, encoding='utf-8')
        stamp = time.time_ns()
        os.utime(path, ns=(stamp, stamp))
        return path

    def test_cli_compact_plan_keeps_exact_selection_without_expanding_literal_inventory(self):
        self.select_cases(2)
        result = subprocess.run([sys.executable, '-B', str(Path(packet.__file__)),
            'run', '--worktree', str(self.root), '--spec', str(self.spec), '--compact-output'],
            capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertFalse(report['executed'])
        self.assertEqual(report['requested_maven_args'][1], '-Dtest=example.TestMain#selected')
        self.assertEqual(report['methods'], {'example.TestMain':['selected']})
        self.assertNotIn('expected_cases', report)
        self.assertEqual(report['output']['expected_case_identities_omitted'], {'example.TestMain':2})
        self.assertEqual(report['output']['full_result_status'], 'not-executed')
        self.assertIsNone(report['output']['full_result_clean'])
        self.assertFalse((self.root/'target').exists())

    def test_ten_thousand_passing_literal_cases_are_verified_then_omitted(self):
        self.select_cases(10000)
        report = self.invoke(lambda:self.outcome_reports(['passed']*10000), compact_output=True)
        self.assertEqual(report['status'], 'passed')
        self.assertTrue(report['clean'])
        self.assertEqual(report['source_before'], report['source_after'])
        self.assertEqual(report['junit']['tests'], 10000)
        self.assertEqual(report['junit']['classes'][0]['cases'], [])
        self.assertEqual(report['output']['omitted_cases'],
                         {'passed':10000, 'failure':0, 'error':0, 'skipped':0})
        self.assertEqual(report['output']['expected_case_identities_omitted'], {'example.TestMain':10000})
        self.assertEqual(report['output']['full_result_status'], 'passed')
        self.assertTrue(report['output']['full_result_clean'])
        self.assertLess(len(json.dumps(report)), 10000)

    def test_global_negative_detail_budget_counts_all_omissions_and_truncated_characters(self):
        self.value['tests'].update(classes=['example.TestMain', 'example.Other'],
            expected_tests={'example.TestMain':30, 'example.Other':30})
        self.save()
        statuses = ['failure']*10+['error']*10+['skipped']*10
        def action():
            for owner in ('example.TestMain', 'example.Other'):
                self.outcome_reports(statuses, owner=owner, message='m'*5000,
                                     type_name='t'*3000, long_identity=True)
        report = self.invoke(action, compact_output=True)
        self.assertEqual(report['status'], 'failed')
        self.assertFalse(report['clean'])
        self.assertEqual(report['junit']['failures'], 20)
        self.assertEqual(report['junit']['errors'], 20)
        self.assertEqual(report['junit']['skipped'], 20)
        cases = [case for suite in report['junit']['classes'] for case in suite['cases']]
        self.assertEqual(len(cases), 20)  # Global, not a fresh budget per class.
        self.assertEqual(report['output']['omitted_cases'],
                         {'passed':0, 'failure':10, 'error':10, 'skipped':20})
        self.assertEqual(report['junit']['classes'][1]['omitted_cases'],
                         {'passed':0, 'failure':10, 'error':10, 'skipped':10})
        for case in cases:
            self.assertEqual(len(case['message']), 1000)
            self.assertEqual(len(case['type']), 1000)
            self.assertEqual(len(case['identity']), 1000)
            self.assertEqual(case['truncated_characters']['message'], 4000)
            self.assertEqual(case['truncated_characters']['type'], 2000)
            self.assertGreater(case['truncated_characters']['identity'], 1000)
        self.assertEqual(report['output']['full_result_status'], 'failed')
        self.assertFalse(report['output']['full_result_clean'])

    def test_all_missing_and_unexpected_literal_ids_survive_budget_and_equal_counts(self):
        self.select_cases(30)
        report = self.invoke(lambda:self.outcome_reports(['passed']*30, long_identity=True),
                             compact_output=True)
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['clean'])
        self.assertEqual(report['junit']['classes'][0]['cases'], [])
        issues = report['case_identity_issues'][0]
        self.assertEqual(set(issues['missing']), {f'example.TestMain#case{i}' for i in range(30)})
        self.assertEqual(set(issues['unexpected']),
                         {'example.TestMain#'+'x'*2000+str(i) for i in range(30)})
        self.assertEqual(report['output']['full_result_status'], 'incomplete')
        self.assertIn('identity', report['evidence_error'].lower())

    def test_same_count_and_case_ids_cannot_hide_same_size_source_byte_drift(self):
        self.select_cases(1)
        other = self.root/'src/Other.java'
        other.write_text('class Other {}\n')
        def action():
            self.outcome_reports(['passed'])
            saved = other.stat()
            other.write_text('class Alter {}\n')  # Same size, restored mtime, outside packet.
            os.utime(other, ns=(saved.st_atime_ns, saved.st_mtime_ns))
        report = self.invoke(action, compact_output=True)
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['clean'])
        self.assertEqual(report['source_before']['bytes'], report['source_after']['bytes'])
        self.assertNotEqual(report['source_before']['fingerprint'], report['source_after']['fingerprint'])
        self.assertIn('Source changed', report['evidence_error'])
        self.assertEqual(report['output']['full_result_status'], 'incomplete')

    def test_property_and_java_mismatches_remain_complete_with_omitted_passes(self):
        self.select_cases(1)
        def action():
            import xml.etree.ElementTree as ET
            path = self.outcome_reports(['passed'])
            tree = ET.parse(path)
            tree.getroot().find('./properties/property[@name="soniccd.opening.egl"]').set('value', 'false')
            tree.getroot().find('./properties/property[@name="java.version"]').set('value', '21.0.9')
            tree.write(path)
            stamp = time.time_ns(); os.utime(path, ns=(stamp, stamp))
        report = self.invoke(action, compact_output=True)
        self.assertEqual(report['status'], 'failed')
        self.assertEqual(report['junit']['classes'][0]['properties']['soniccd.opening.egl'], 'false')
        self.assertEqual(report['junit']['issues'],
                         ["example.TestMain: property soniccd.opening.egl expected='true' actual='false'"])
        self.assertEqual(report['issues'], ['Java major mismatch: example.TestMain'])
        self.assertEqual(report['junit']['classes'][0]['cases'], [])

    def test_nonzero_maven_and_queue_progress_survive_green_compacted_xml(self):
        run = self.runner()
        stderr = io.StringIO()
        from contextlib import redirect_stderr
        def queued(args, *, before_run, after_run, maven_stdout):
            self.assertEqual(args, ['--normal', '-Dmse=off', '-Dtest=example.TestMain',
                                   '-Dsoniccd.opening.egl=true', 'test', '-B'])
            print('waiting for existing queue admission')
            before_run()
            print('Maven diagnostic failure', file=maven_stdout)
            self.outcome_reports(['passed'])
            after_run(7)
            return 7
        with redirect_stderr(stderr), mock.patch.object(run.queue, 'main', queued):
            report = run.run_handoff(self.root, self.spec, execute=True, compact_output=True)
        self.assertIn('waiting for existing queue admission', stderr.getvalue())
        self.assertIn('Maven diagnostic failure', stderr.getvalue())
        self.assertEqual(report['maven_returncode'], 7)
        self.assertEqual(report['status'], 'failed')
        self.assertFalse(report['output']['full_result_clean'])

    @unittest.skipUnless(os.name=='posix', 'Synthetic executable fixture requires POSIX')
    def test_compact_cli_real_queue_fake_maven_preserves_terminal_and_integrity_verdicts(self):
        self.select_cases(1)
        other = self.root/'src/Other.java'; other.write_text('class Other {}\n')
        binary = self.base/'bin'; binary.mkdir()
        fake = binary/'mvn'
        fake.write_text('#!'+sys.executable+'\n'+'''import os, pathlib, sys, xml.etree.ElementTree as E
mode=os.environ['SYNTHETIC_MAVEN_MODE']
print('[ERROR] synthetic Maven terminal diagnostic: '+mode)
p=pathlib.Path('target/surefire-reports');p.mkdir(parents=True,exist_ok=True)
s=E.Element('testsuite',name='example.TestMain',tests='1',failures='0',errors='0',skipped='0')
props=E.SubElement(s,'properties')
for key,value in {'java.version':'25.0.1','user.dir':str(pathlib.Path.cwd()),'soniccd.opening.egl':'true'}.items():
 E.SubElement(props,'property',name=key,value=value)
E.SubElement(s,'testcase',classname='example.TestMain',name='other' if mode=='identity' else 'case0')
E.ElementTree(s).write(p/'TEST-example.TestMain.xml')
if mode=='source':
 f=pathlib.Path('src/Other.java');saved=f.stat();f.write_text('class Alter {}\\n')
 os.utime(f,ns=(saved.st_atime_ns,saved.st_mtime_ns))
sys.exit(7 if mode=='nonzero' else 0)
''')
        fake.chmod(0o755)
        environment = {**os.environ, 'PATH':str(binary)+os.pathsep+os.environ['PATH'],
                       'OPENGGF_MAVEN_QUEUE':'serial'}
        for name in ('MAVEN_ARGS', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
            environment.pop(name, None)
        for progress in ('full', 'concise'):
            for mode, status, exit_code in (('passed','passed',0), ('nonzero','failed',1),
                                           ('identity','incomplete',2), ('source','incomplete',2)):
                with self.subTest(progress=progress, mode=mode):
                    other.write_text('class Other {}\n')
                    result = subprocess.run([sys.executable, '-B', str(Path(packet.__file__)),
                        'run', '--worktree', str(self.root), '--spec', str(self.spec),
                        '--execute', '--compact-output', '--maven-progress', progress],
                        env={**environment, 'SYNTHETIC_MAVEN_MODE':mode},
                        capture_output=True, text=True, timeout=30)
                    self.assertEqual(result.returncode, exit_code, result.stderr)
                    report = json.loads(result.stdout)
                    self.assertEqual(report['status'], status)
                    self.assertEqual(report['output']['full_result_status'], status)
                    self.assertEqual(report['clean'], mode=='passed')
                    self.assertIn('synthetic Maven terminal diagnostic: '+mode, result.stderr)
                    self.assertEqual('maven_progress' in report, progress=='concise')
                    if mode=='identity':
                        self.assertEqual(report['case_identity_issues'], [{'class':'example.TestMain',
                            'missing':['example.TestMain#case0'], 'unexpected':['example.TestMain#other']}])
                    if mode=='source':
                        self.assertIn('Source changed', report['evidence_error'])

    def test_default_result_retains_full_identity_cases_and_diagnostic_text(self):
        self.select_cases(30)
        plan = self.runner().run_handoff(self.root, self.spec)
        explicit = self.runner().run_handoff(self.root, self.spec, compact_output=False)
        self.assertEqual(json.dumps(plan), json.dumps(explicit))
        report = self.invoke(lambda:self.outcome_reports(['failure']*30, message='m'*5000))
        self.assertNotIn('output', report)
        self.assertEqual(report['expected_cases'], self.value['tests']['expected_cases'])
        suite = report['junit']['classes'][0]
        self.assertEqual(len(suite['cases']), 30)
        self.assertEqual(suite['cases'][-1]['message'], 'm'*5000)
        self.assertNotIn('omitted_cases', suite)




class ConciseProgressTests(unittest.TestCase):
    """Presentation must stay bounded without treating log strings as evidence."""
    def sink(self, output):
        return importlib.import_module('integration_run')._ConciseMavenProgress(output)

    def test_arbitrary_text_splits_crlf_and_final_partial_have_exact_counts(self):
        text = 'noise\r\n[INFO] Tests run: 1, Failures: 0\r[WARNING] café\n[ERROR] Ωpartial'
        expected = ['[INFO] Tests run: 1, Failures: 0', '[WARNING] café', '[ERROR] Ωpartial']
        for split in range(len(text)+1):
            with self.subTest(split=split):
                output = io.StringIO(); sink = self.sink(output)
                self.assertEqual(sink.write(text[:split]), split)
                self.assertEqual(sink.write(text[split:]), len(text)-split)
                sink.close()
                self.assertEqual(output.getvalue().splitlines()[:-1], expected)
                counts = sink.metadata()['counts']
                self.assertEqual(counts['input_lines'], 4)
                self.assertEqual(counts['input_characters'], len('noise')+sum(map(len, expected)))
                self.assertEqual(counts['suppressed_lines'], 1)
                self.assertEqual(counts['suppressed_characters'], 5)
                self.assertEqual(counts['displayed_characters'], sum(map(len, expected)))
                self.assertEqual(counts['truncated_characters'], 0)

    def test_huge_unterminated_unicode_line_keeps_only_a_bounded_prefix(self):
        import tracemalloc
        run = importlib.import_module('integration_run')
        output = io.StringIO(); sink = self.sink(output)
        payload = '[ERROR] '+('界'*2_000_000)
        tracemalloc.start()
        try:
            sink.write(payload)
            _, peak = tracemalloc.get_traced_memory()
        finally:
            tracemalloc.stop()
        self.assertLess(peak, 128*1024)
        self.assertLessEqual(len(sink._pending), run.MAVEN_PROGRESS_LINE_CHARACTERS)
        sink.close()
        counts = sink.metadata()['counts']
        self.assertEqual(counts['input_lines'], 1)
        self.assertEqual(counts['truncated_lines'], 1)
        self.assertEqual(counts['truncated_characters'], len(payload)-counts['displayed_characters'])
        self.assertIn('[truncated]', output.getvalue())
        self.assertLessEqual(len(output.getvalue()), run.MAVEN_PROGRESS_MAX_CHARACTERS)

    def test_warning_and_total_budgets_leave_reserved_build_status_and_exact_suppression(self):
        run = importlib.import_module('integration_run')
        lines = ['[WARNING] warning']*100+['[ERROR] diagnostic']*100
        lines += ['[INFO] BUILD FAILURE', '[INFO] BUILD SUCCESS']
        output = io.StringIO(); sink = self.sink(output)
        sink.write('\n'.join(lines)+'\n'); sink.close()
        shown = output.getvalue().splitlines()
        counts = sink.metadata()['counts']
        self.assertEqual(shown.count('[WARNING] warning'), 8)
        self.assertIn('[INFO] BUILD FAILURE', shown)
        self.assertIn('[INFO] BUILD SUCCESS', shown)
        self.assertLessEqual(len(shown), run.MAVEN_PROGRESS_MAX_LINES)
        self.assertLessEqual(len(output.getvalue()), run.MAVEN_PROGRESS_MAX_CHARACTERS)
        self.assertEqual(counts['input_lines'], len(lines))
        self.assertEqual(counts['suppressed_lines'], len(lines)-counts['displayed_lines'])
        self.assertEqual(counts['input_characters'], sum(map(len, lines)))
        self.assertEqual(counts['input_characters'], counts['displayed_characters']+
                         counts['suppressed_characters']+counts['truncated_characters'])
        self.assertIn('suppressed_lines='+str(counts['suppressed_lines']), shown[-1])
        self.assertIn('suppressed_characters='+str(counts['suppressed_characters']), shown[-1])

    def test_compilation_summaries_ansi_markers_and_suppressed_long_lines(self):
        run = importlib.import_module('integration_run')
        lines = ['[INFO] Compiling 17 source files', '[ERROR] COMPILATION ERROR :',
                 '[INFO] Results:', '[INFO] Total time: 3.1 s', '[INFO] Finished at: now',
                 '\x1b[31m[ERROR] colored failure\x1b[0m']
        noise = 'uninteresting '+('界'*10000)
        output = io.StringIO(); sink = self.sink(output)
        sink.write(noise+'\r\r\n'+'\n'.join(lines)); sink.close()
        self.assertEqual(output.getvalue().splitlines()[:-1], lines)
        counts = sink.metadata()['counts']
        self.assertEqual(counts['input_lines'], len(lines)+2)
        self.assertEqual(counts['suppressed_lines'], 2)
        self.assertEqual(counts['suppressed_characters'], len(noise))
        self.assertEqual(counts['truncated_characters'], 0)
        self.assertEqual(sink.metadata()['limits']['pending_characters'], run.MAVEN_PROGRESS_LINE_CHARACTERS)

    def test_pipe_start_failure_closes_both_owned_descriptors(self):
        run = importlib.import_module('integration_run')
        sink = self.sink(io.StringIO()); descriptors = os.pipe()
        with mock.patch.object(run.os, 'pipe', return_value=descriptors), \
             mock.patch.object(run.threading.Thread, 'start', side_effect=RuntimeError('owned start failure')):
            with self.assertRaisesRegex(RuntimeError, 'owned start failure'):
                sink.__enter__()
        for descriptor in descriptors:
            with self.assertRaises(OSError): os.fstat(descriptor)

    def test_pipe_constructor_failure_closes_both_owned_descriptors(self):
        run = importlib.import_module('integration_run')
        sink = self.sink(io.StringIO()); descriptors = os.pipe()
        try:
            with mock.patch.object(run.os, 'pipe', return_value=descriptors), \
                 mock.patch.object(run.threading, 'Thread', side_effect=RuntimeError('owned constructor failure')):
                with self.assertRaisesRegex(RuntimeError, 'owned constructor failure'):
                    sink.__enter__()
            for descriptor in descriptors:
                with self.assertRaises(OSError): os.fstat(descriptor)
        finally:
            for descriptor in descriptors:
                try: os.close(descriptor)
                except OSError: pass

    def test_interrupted_launched_reader_has_one_close_and_no_live_thread(self):
        run = importlib.import_module('integration_run')
        sink = self.sink(io.StringIO()); descriptors = os.pipe()
        real_start, real_close = run.threading.Thread.start, os.close
        launched, read_closes, thread_errors = [], [], []
        def interrupted_start(reader):
            launched.append(reader)
            real_start(reader)
            raise KeyboardInterrupt('owned post-launch interruption')
        def tracking_close(descriptor):
            if descriptor == descriptors[0]: read_closes.append(descriptor)
            return real_close(descriptor)
        try:
            with mock.patch.object(run.os, 'pipe', return_value=descriptors), \
                 mock.patch.object(run.os, 'close', side_effect=tracking_close), \
                 mock.patch.object(run.threading.Thread, 'start', interrupted_start), \
                 mock.patch.object(run.threading, 'excepthook', side_effect=lambda error: thread_errors.append(error)):
                with self.assertRaisesRegex(KeyboardInterrupt, 'owned post-launch interruption'):
                    sink.__enter__()
                for reader in launched:
                    reader.join(timeout=2)
                    self.assertFalse(reader.is_alive())
                self.assertEqual(read_closes, [descriptors[0]], 'a reused numeric FD must never be closed by the retired reader')
                self.assertEqual(thread_errors, [])
            for descriptor in descriptors:
                with self.assertRaises(OSError): os.fstat(descriptor)
        finally:
            for descriptor in descriptors:
                try: real_close(descriptor)
                except OSError: pass
            for reader in launched:
                reader.join(timeout=2)

    def test_flush_does_not_finalize_partial_and_close_preserves_borrowed_stream(self):
        output = io.StringIO(); sink = self.sink(output)
        sink.write('[ERROR] unfinished'); sink.flush()
        self.assertEqual(output.getvalue(), '')
        sink.close(); saved = output.getvalue(); sink.close()
        self.assertEqual(output.getvalue(), saved)
        self.assertFalse(output.closed)
        with self.assertRaises(ValueError): sink.write('after close')
        with self.assertRaises(ValueError): sink.flush()

    def test_pipe_decodes_split_utf8_drains_final_line_and_closes_owned_fds_and_thread(self):
        output = io.StringIO(); sink = self.sink(output)
        with sink:
            descriptor = sink.fileno(); reader = sink._reader
            for byte in '[ERROR] café Ω'.encode()+b'\xff':
                os.write(descriptor, bytes([byte]))
        self.assertEqual(output.getvalue().splitlines()[0], '[ERROR] café Ω�')
        self.assertFalse(reader.is_alive())
        for descriptor in (descriptor, sink._read_fd):
            with self.assertRaises(OSError): os.fstat(descriptor)
        self.assertEqual(sink.metadata()['counts']['input_lines'], 1)

    def test_cancellation_drains_and_preserves_the_original_exception(self):
        output = io.StringIO(); sink = self.sink(output)
        with self.assertRaises(KeyboardInterrupt):
            with sink:
                descriptor = sink.fileno()
                os.write(descriptor, b'[ERROR] before cancellation')
                raise KeyboardInterrupt
        self.assertIn('[ERROR] before cancellation', output.getvalue())
        self.assertFalse(sink._reader.is_alive())
        with self.assertRaises(OSError): os.fstat(descriptor)

    def test_output_failure_drains_more_than_pipe_capacity_then_propagates(self):
        class Broken(io.StringIO):
            def write(self, value): raise OSError('synthetic broken output')
        sink = self.sink(Broken())
        with self.assertRaisesRegex(OSError, 'synthetic broken output'):
            with sink:
                descriptor = sink.fileno()
                data = b'[ERROR] diagnostic\n'*20000
                while data:
                    data = data[os.write(descriptor, data):]
        self.assertFalse(sink._reader.is_alive())
        with self.assertRaises(OSError): os.fstat(descriptor)


class ConciseRunTests(RunFixture):
    def test_default_and_explicit_full_pass_the_same_stderr_object_without_metadata(self):
        run = self.runner(); stderr = io.StringIO()
        from contextlib import redirect_stderr
        def queued(args, *, before_run, after_run, maven_stdout):
            self.assertIs(maven_stdout, stderr)
            before_run(); print('unfiltered default text', file=maven_stdout)
            CompactOutputTests.outcome_reports(self, ['passed']); after_run(0); return 0
        with redirect_stderr(stderr), mock.patch.object(run.queue, 'main', queued):
            default = run.run_handoff(self.root, self.spec, execute=True)
            explicit = run.run_handoff(self.root, self.spec, execute=True, maven_progress='full')
        self.assertNotIn('maven_progress', default)
        self.assertNotIn('maven_progress', explicit)
        self.assertEqual(stderr.getvalue().count('unfiltered default text'), 2)
        self.assertEqual(default['requested_maven_args'], explicit['requested_maven_args'])

    def test_concise_queue_notices_and_callbacks_keep_exact_lane_args_and_no_new_source_files(self):
        run = self.runner(); stderr = io.StringIO(); observations = []
        from contextlib import redirect_stderr
        expected = ['-Dmse=off', '-Dtest=example.TestMain', '-Dsoniccd.opening.egl=true', 'test', '-B']
        before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*'))
        for lane in ('normal', 'lean', 'auto'):
            def queued(args, *, before_run, after_run, maven_stdout):
                observations.append(args)
                print('queue notice remains complete '+('q'*5000))
                before_run()
                os.write(maven_stdout.fileno(), b'noise hidden\n[INFO] Tests run: 1, Failures: 0\n')
                CompactOutputTests.outcome_reports(self, ['passed']); after_run(0); return 0
            with redirect_stderr(stderr), mock.patch.object(run.queue, 'main', queued):
                result = run.run_handoff(self.root, self.spec, execute=True,
                                         lane=lane, maven_progress='concise')
            self.assertEqual(result['requested_maven_args'], expected)
            self.assertEqual(result['lane'], lane)
            self.assertEqual(result['status'], 'passed', result.get('evidence_error'))
            self.assertEqual(result['maven_progress']['counts']['input_lines'], 2)
            self.assertIn('incomplete', result['maven_progress']['observation'])
        self.assertEqual(observations, [['--normal']+expected, ['--lean']+expected, expected])
        self.assertEqual(stderr.getvalue().count('queue notice remains complete '+('q'*5000)), 3)
        self.assertNotIn('noise hidden', stderr.getvalue())
        after = sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*')
                       if not p.relative_to(self.root).parts[0] == 'target')
        self.assertEqual(after, before)

    def test_contradictory_logs_do_not_change_xml_verdicts_or_full_diagnostics(self):
        run = self.runner()
        from contextlib import redirect_stderr
        for compact in (False, True):
            for status, code, log in (('passed', 0, '[INFO] BUILD FAILURE'),
                                      ('failure', 0, '[INFO] BUILD SUCCESS'),
                                      ('passed', 7, '[INFO] BUILD SUCCESS')):
                with self.subTest(compact=compact, status=status, code=code):
                    def queued(args, *, before_run, after_run, maven_stdout):
                        before_run(); os.write(maven_stdout.fileno(), log.encode())
                        CompactOutputTests.outcome_reports(self, [status], message='actual XML diagnostic')
                        after_run(code); return code
                    with redirect_stderr(io.StringIO()), mock.patch.object(run.queue, 'main', queued):
                        result = run.run_handoff(self.root, self.spec, execute=True,
                            compact_output=compact, maven_progress='concise')
                    self.assertEqual(result['clean'], status=='passed' and code==0)
                    self.assertEqual(result['status'], 'passed' if result['clean'] else 'failed')
                    self.assertEqual(result['maven_returncode'], code)
                    self.assertEqual(result['junit']['tests'], 1)
                    if status == 'failure':
                        self.assertEqual(result['junit']['classes'][0]['cases'][0]['message'],
                                         'actual XML diagnostic')

    def test_success_log_does_not_admit_missing_xml_or_changed_source(self):
        run = self.runner()
        from contextlib import redirect_stderr
        other = self.root/'src/Other.java'; other.write_text('class Other {}\n')
        for mutate in (False, True):
            with self.subTest(mutate=mutate):
                def queued(args, *, before_run, after_run, maven_stdout):
                    before_run(); os.write(maven_stdout.fileno(), b'[INFO] BUILD SUCCESS\n')
                    if mutate:
                        CompactOutputTests.outcome_reports(self, ['passed']); other.write_text('class Changed {}\n')
                    after_run(0); return 0
                with redirect_stderr(io.StringIO()), mock.patch.object(run.queue, 'main', queued):
                    result = run.run_handoff(self.root, self.spec, execute=True, maven_progress='concise')
                self.assertFalse(result['clean']); self.assertEqual(result['status'], 'incomplete')
                self.assertIn('Source changed' if mutate else 'not rewritten', result['evidence_error'])

    def test_output_failure_occurs_after_terminal_callback_and_restores_cwd(self):
        class Broken(io.StringIO):
            def write(self, value): raise OSError('owned stderr failed')
        run = self.runner(); completed = []; previous = Path.cwd(); captured = []
        from contextlib import redirect_stderr
        def queued(args, *, before_run, after_run, maven_stdout):
            captured.append(maven_stdout)
            before_run(); os.write(maven_stdout.fileno(), b'[ERROR] display failed\n')
            CompactOutputTests.outcome_reports(self, ['passed']); after_run(0); completed.append(True); return 0
        with redirect_stderr(Broken()), mock.patch.object(run.queue, 'main', queued):
            with self.assertRaisesRegex(OSError, 'owned stderr failed'):
                run.run_handoff(self.root, self.spec, execute=True, maven_progress='concise')
        self.assertEqual(completed, [True]); self.assertEqual(Path.cwd(), previous)
        self.assertFalse(captured[0]._reader.is_alive())

    def test_concise_plan_cli_combinations_never_execute_and_values_fail_closed(self):
        run = self.runner()
        from contextlib import redirect_stderr, redirect_stdout
        for compact in (False, True):
            stdout = io.StringIO()
            with redirect_stdout(stdout), mock.patch.object(run.queue, 'main', side_effect=AssertionError('no execution')):
                code = packet.main(['run', '--worktree', str(self.root), '--spec', str(self.spec),
                    '--maven-progress', 'concise']+(['--compact-output'] if compact else []))
            result = json.loads(stdout.getvalue()); self.assertEqual(code, 0)
            self.assertFalse(result['executed']); self.assertEqual(result['maven_progress']['mode'], 'concise')
            self.assertFalse((self.root/'target').exists())
        for argv in (['run', '--worktree', str(self.root), '--spec', str(self.spec), '--maven-progress', 'brief'],
                     ['run', '--worktree', str(self.root), '--spec', str(self.spec), '--maven-prog', 'concise'],
                     ['handoff', '--worktree', str(self.root), '--spec', str(self.spec), '--maven-progress', 'concise']):
            with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as rejection:
                packet.main(argv)
            self.assertEqual(rejection.exception.code, 2)
        with self.assertRaisesRegex(packet.PacketError, 'Unknown Maven progress'):
            run.run_handoff(self.root, self.spec, maven_progress='brief')


if __name__=='__main__': unittest.main()
