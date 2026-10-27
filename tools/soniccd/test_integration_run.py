"""Source/queue/report integration safety, using synthetic Git and XML only."""
import importlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
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


if __name__=='__main__': unittest.main()
