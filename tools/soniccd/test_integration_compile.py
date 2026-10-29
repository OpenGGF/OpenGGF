"""Compile-only source binding: synthetic Git/files and mocked Maven wrapper only."""
import importlib
import io
import json
import os
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import integration_packet as packet


class CompileTests(unittest.TestCase):
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
                 'commit', '-qm', 'synthetic compile source')
        self.final = self.base / 'Final.java'
        self.final.write_bytes(self.target.read_bytes())
        self.final.chmod(0o444)
        self.dependency = self.base / 'dependency.java'
        self.dependency.write_text('dependency\n')
        self.dependency.chmod(0o444)
        self.spec = self.base / 'handoff.json'
        self.value = dict(version=1, id='compile-r1', packet=dict(version=1,
            worktree_head=self.git('rev-parse', 'HEAD').strip(), files=[dict(
                source=str(self.final), sha256=packet.digest(self.final.read_bytes()),
                bytes=self.final.stat().st_size, mode='0444', utf8=True, lf=True,
                destination='src/Main.java', expected={'absent': True})]),
            dependencies=[dict(name='body', path=str(self.dependency),
                sha256=packet.digest(self.dependency.read_bytes()),
                bytes=self.dependency.stat().st_size, mode='0444')],
            api_changes=['Adds Main'], known_defects=[], tests=dict(
                classes=['example.TestMain'], properties={'soniccd.opening.egl':'true'},
                expected_tests={'example.TestMain':1}, java_major=25))
        self.save()
        # Never permit an inherited Maven environment to change a synthetic run.
        self.environment = mock.patch.dict(os.environ, {}, clear=False)
        self.environment.start()
        self.addCleanup(self.environment.stop)
        for name in ('MAVEN_ARGS', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
            os.environ.pop(name, None)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True,
                                       stderr=subprocess.DEVNULL)

    def save(self):
        self.spec.write_text(json.dumps(self.value)+'\n')

    def compiler(self):
        self.assertIsNotNone(importlib.util.find_spec('integration_compile'),
                             'Compile-only source-bound helper is missing')
        return importlib.import_module('integration_compile')

    def invoke(self, action=None, *, code=0, waiting=None):
        compiler = self.compiler()
        launched = []
        def wrapped(args, *, before_run, after_run, maven_stdout):
            self.assertEqual(Path.cwd(), self.root)
            self.assertIs(maven_stdout, sys.stderr)
            self.assertEqual(args, ['-Dmse=off', '-Dsoniccd.opening.egl=true', 'test-compile', '-B'])
            if waiting:
                waiting()
            before_run()
            launched.append(True)
            print('synthetic build output')
            if action:
                action()
            after_run(code)
            return code
        with mock.patch.object(compiler.queue, 'main', wrapped), \
             mock.patch.object(packet, 'junit_summary', side_effect=AssertionError('No JUnit reads')), \
             redirect_stderr(io.StringIO()):
            report = compiler.compile_handoff(self.root, self.spec, execute=True)
        self.assertEqual(launched, [True])
        return report

    def cli(self, *extra):
        return subprocess.run([sys.executable, '-B', str(Path(packet.__file__)), 'compile',
            '--worktree', str(self.root), '--spec', str(self.spec), *extra],
            capture_output=True, text=True, timeout=20)

    def test_cli_plan_accepts_installed_handoff_and_never_creates_target(self):
        result = self.cli()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report['scope'], 'compilation-only')
        self.assertFalse(report['tests_executed'])
        self.assertFalse(report['executed'])
        self.assertEqual(report['status'], 'planned')
        self.assertEqual(report['requested_maven_args'],
            ['-Dmse=off', '-Dsoniccd.opening.egl=true', 'test-compile', '-B'])
        self.assertNotIn('expected_tests', report)
        self.assertNotIn('classes', report)
        self.assertFalse((self.root/'target').exists())

    def _preterminal_git_fault_is_incomplete(self, observation):
        compiler = self.compiler()
        original = compiler.source_identity
        for error in (subprocess.TimeoutExpired(['git', 'ls-files'], 1),
                      subprocess.CalledProcessError(17, ['git', 'ls-files'])):
            with self.subTest(observation=observation, error=type(error).__name__):
                seen = 0
                launched = []
                def identity(root):
                    nonlocal seen
                    seen += 1
                    if seen == observation:
                        raise error
                    return original(root)
                def wrapper(args, *, before_run, after_run, maven_stdout):
                    before_run()
                    launched.append(True)
                    self.fail('Git observation fault must precede compiler launch')
                output, diagnostic = io.StringIO(), io.StringIO()
                previous = Path.cwd()
                with mock.patch.object(compiler, 'source_identity', identity), \
                     mock.patch.object(compiler.queue, 'main', wrapper), \
                     redirect_stdout(output), redirect_stderr(diagnostic):
                    code = packet.main(['compile', '--worktree', str(self.root),
                                        '--spec', str(self.spec), '--execute'])
                self.assertEqual(code, 2, output.getvalue())
                self.assertEqual(output.getvalue(), '')
                result = json.loads(diagnostic.getvalue())
                self.assertIn('incomplete', result['error'].lower())
                self.assertNotIn('maven_returncode', result)
                self.assertNotIn('compilation_succeeded', result)
                self.assertEqual(launched, [])
                self.assertEqual(Path.cwd(), previous)
                self.assertFalse((self.root / 'target').exists())

    def test_initial_git_observation_fault_is_incomplete_not_compiler_failure(self):
        self._preterminal_git_fault_is_incomplete(1)

    def test_admission_git_observation_fault_is_incomplete_not_compiler_failure(self):
        self._preterminal_git_fault_is_incomplete(2)

    def test_success_is_terminal_compilation_only_with_full_source_equality(self):
        report = self.invoke()
        self.assertEqual(report['status'], 'compiled')
        self.assertTrue(report['compilation_succeeded'])
        self.assertFalse(report['tests_executed'])
        self.assertEqual(report['maven_returncode'], 0)
        self.assertTrue(report['source_equal'])
        self.assertTrue(report['declarations_equal'])
        self.assertEqual(report['source_before'], report['source_after'])
        self.assertLessEqual(report['started_at'], report['completed_at'])
        self.assertNotIn('junit', report)
        self.assertFalse((self.root/'target').exists())

    def test_nonzero_compiler_exit_is_failed_without_waiting_for_xml(self):
        report = self.invoke(code=7)
        self.assertEqual(report['status'], 'failed')
        self.assertFalse(report['compilation_succeeded'])
        self.assertEqual(report['maven_returncode'], 7)
        self.assertTrue(report['source_equal'])
        self.assertNotIn('junit', report)

    def test_existing_surefire_reports_are_neither_read_nor_changed(self):
        reports = self.root/'target/surefire-reports'
        reports.mkdir(parents=True)
        old = reports/'TEST-example.TestMain.xml'
        old.write_bytes(b'not XML: existing diagnostic must remain untouched\n')
        os.utime(old, ns=(1, 1))
        before = (old.read_bytes(), old.stat().st_mtime_ns)
        report = self.invoke()
        self.assertEqual(report['status'], 'compiled')
        self.assertEqual((old.read_bytes(), old.stat().st_mtime_ns), before)

    def test_preimport_bytes_block_compilation(self):
        self.target.write_text('old source\n')
        compiler = self.compiler()
        with mock.patch.object(compiler.queue, 'main', side_effect=AssertionError('No launch')):
            with self.assertRaises(packet.PacketError):
                compiler.compile_handoff(self.root, self.spec, execute=True)

    def test_admission_wait_full_source_drift_prevents_launch(self):
        other = self.root/'src/Other.java'
        other.write_text('class Other {}\n')
        with self.assertRaises(packet.PacketError):
            self.invoke(waiting=lambda:other.write_text('class Changed {}\n'))
        self.assertFalse((self.root/'target').exists())

    def test_admission_wait_spec_drift_prevents_launch(self):
        with self.assertRaises(packet.PacketError):
            self.invoke(waiting=lambda:self.spec.write_text(self.spec.read_text()+' '))

    def test_admission_wait_external_dependency_drift_prevents_launch(self):
        def change():
            self.dependency.chmod(0o644)
            self.dependency.write_text('changed dependency\n')
            self.dependency.chmod(0o444)
        with self.assertRaises(packet.PacketError):
            self.invoke(waiting=change)

    def test_source_drift_during_compilation_is_incomplete(self):
        other = self.root/'src/Other.java'
        other.write_text('class Other {}\n')
        report = self.invoke(lambda:other.write_text('class Changed {}\n'))
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['compilation_succeeded'])
        self.assertFalse(report['source_equal'])
        self.assertEqual(report['maven_returncode'], 0)

    def test_spec_drift_during_compilation_retains_independent_source_observation(self):
        report = self.invoke(lambda:self.spec.write_text(self.spec.read_text()+' '))
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['declarations_equal'])
        self.assertTrue(report['source_equal'])
        self.assertFalse(report['compilation_succeeded'])

    def test_dependency_drift_during_compilation_is_incomplete(self):
        def change():
            self.dependency.chmod(0o644)
            self.dependency.write_text('changed dependency\n')
            self.dependency.chmod(0o444)
        report = self.invoke(change)
        self.assertEqual(report['status'], 'incomplete')
        self.assertFalse(report['declarations_equal'])
        self.assertTrue(report['source_equal'])

    def test_missing_terminal_callback_cannot_claim_compilation(self):
        compiler = self.compiler()
        with mock.patch.object(compiler.queue, 'main', return_value=0):
            with self.assertRaises(packet.PacketError):
                compiler.compile_handoff(self.root, self.spec, execute=True)

    def test_cancellation_and_launch_failure_restore_caller_directory(self):
        compiler = self.compiler()
        previous = Path.cwd()
        for error in (KeyboardInterrupt(), OSError('mock launch rejected')):
            with self.subTest(error=type(error).__name__):
                with mock.patch.object(compiler.queue, 'main', side_effect=error):
                    with self.assertRaises(type(error)):
                        compiler.compile_handoff(self.root, self.spec, execute=True)
                self.assertEqual(Path.cwd(), previous)

    def test_method_declarations_are_validated_but_not_used_as_test_selectors(self):
        self.value['tests'].update(methods={'example.TestMain':['one']},
            expected_cases={'example.TestMain':['example.TestMain#one']})
        self.save()
        report = self.invoke()
        self.assertEqual(report['status'], 'compiled')
        self.assertNotIn('methods', report)
        self.assertNotIn('expected_cases', report)

    def test_disallowed_property_cannot_become_maven_argument(self):
        self.value['tests']['properties']['skipTests'] = 'true'
        self.save()
        compiler = self.compiler()
        with mock.patch.object(compiler.queue, 'main', side_effect=AssertionError('No launch')):
            with self.assertRaises(packet.PacketError):
                compiler.compile_handoff(self.root, self.spec, execute=True)

    def test_custom_maven_args_cannot_redirect_the_compile_lifecycle(self):
        compiler = self.compiler()
        with mock.patch.dict(os.environ, {'MAVEN_ARGS':'test'}), \
             mock.patch.object(compiler.queue, 'main', side_effect=AssertionError('No launch')):
            with self.assertRaises(packet.PacketError):
                compiler.compile_handoff(self.root, self.spec, execute=True)

    def test_spec_symlink_and_internal_spec_are_rejected(self):
        compiler = self.compiler()
        link = self.base/'alias.json'
        link.symlink_to(self.spec)
        inside = self.root/'internal.json'
        inside.write_bytes(self.spec.read_bytes())
        for path in (link, inside):
            with self.subTest(path=path):
                with self.assertRaises(packet.PacketError):
                    compiler.compile_handoff(self.root, path)

    def test_compact_output_preserves_all_plan_evidence_and_uses_compact_json(self):
        regular, compact = self.cli(), self.cli('--compact-output')
        self.assertEqual(regular.returncode, 0, regular.stderr)
        self.assertEqual(compact.returncode, 0, compact.stderr)
        self.assertEqual(json.loads(regular.stdout), json.loads(compact.stdout))
        self.assertEqual(len(compact.stdout.splitlines()), 1)

    def test_compile_rejects_run_lane_selector_and_progress_options(self):
        for flags in (('--lane','lean'), ('--lane','normal'), ('--maven-progress','concise'),
                      ('--class','example.TestMain'), ('--exec',)):
            with self.subTest(flags=flags):
                result = self.cli(*flags)
                self.assertEqual(result.returncode, 2)
                self.assertFalse(result.stdout)
        self.assertFalse((self.root/'target').exists())

    def test_cli_exit_codes_are_compile_specific(self):
        compiler = self.compiler()
        for status, expected in (('compiled',0), ('failed',1), ('incomplete',2)):
            report = dict(executed=True, status=status, scope='compilation-only', tests_executed=False)
            with self.subTest(status=status), \
                 mock.patch.object(compiler, 'compile_handoff', return_value=report), \
                 redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(packet.main(['compile','--worktree',str(self.root),
                    '--spec',str(self.spec),'--execute']), expected)


if __name__ == '__main__':
    unittest.main()
