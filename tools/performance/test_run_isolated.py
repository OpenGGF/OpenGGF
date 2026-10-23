"""Regression checks for the P5 measurement isolation/ownership hazard."""
import importlib.util
from pathlib import Path
import signal
import selectors
import sys
import tempfile
import subprocess
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('run_isolated', Path(__file__).with_name('run_isolated.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TestMeasurementIsolation(unittest.TestCase):
    @unittest.skipUnless(sys.platform.startswith('linux'), 'Linux OS lease regression')
    def test_busy_own_tree_waits_outside_shared_queue(self):
        from maven_queue import worktree_build_slot
        with tempfile.TemporaryDirectory(prefix='ggf-isolation-own-') as temporary:
            root = Path(temporary)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            source = Path(__file__).with_name('run_isolated.py').resolve()
            code = f"""
import sys
sys.path.insert(0, {str(source.parent)!r})
import run_isolated as runner
runner.interfering_processes = lambda: []
sys.argv = ['run_isolated', '--worktree', {str(root)!r}, '--cwd', {str(root)!r},
            '--audit-report', {str(root/'audit.json')!r}, '--', sys.executable, '-c', 'pass']
raise SystemExit(runner.main())
"""
            process = None
            try:
                with worktree_build_slot(root):
                    process = subprocess.Popen([sys.executable, '-u', '-c', code],
                                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                               text=True)
                    with selectors.DefaultSelector() as reader:
                        reader.register(process.stdout, selectors.EVENT_READ)
                        self.assertTrue(reader.select(timeout=5), 'Owned child did not reach lock wait')
                        self.assertIn("Waiting for this worktree's build/test lock", process.stdout.readline())
                    self.assertIsNone(process.poll())
                    self.assertFalse((root/'.git/maven-waiters').exists())
                output, _ = process.communicate(timeout=5)
                self.assertEqual(0, process.returncode, output)
            finally:
                if process is not None:
                    if process.poll() is None:
                        process.terminate()
                    try:
                        process.communicate(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.communicate(timeout=5)

    def test_benchmark_classpath_is_not_a_maven_launcher(self):
        self.assertFalse(module.interfering_command([
            '/usr/bin/java', '-cp', '/tmp/org.codehaus.plexus.classworlds.launcher.Launcher.jar',
            'com.openggf.tools.RuntimeEngineBenchmarkTool']))
        self.assertFalse(module.interfering_command([]))

    def test_standalone_gameplay_input_branch_probe_is_interference(self):
        self.assertTrue(module.interfering_command([
            'java', '-Xmx1g', '-XX:ActiveProcessorCount=2',
            '-Dopenggf.headless.gl=surfaceless-egl', '-cp',
            '/tmp/immutable-openggf-runtime.jar:/tmp/lwjgl.jar',
            'com.openggf.tools.GameplayInputBranchTool', '/tmp/rom.gen',
            's3k', 'lrz', '1', '320', 'sonic', 'tails', '/tmp/route.bk2']))
        self.assertTrue(module.interfering_command([
            'java', '--class-path=/tmp/openggf.jar', '--add-opens',
            'java.base/java.lang=ALL-UNNAMED',
            'com.openggf.tools.GameplayInputBranchTool']))
        self.assertFalse(module.interfering_command([
            'java', '-cp', '/tmp/com.openggf.tools.GameplayInputBranchTool.jar',
            'com.openggf.tools.RuntimeEngineBenchmarkTool']))
        for command in [
                ['java', '-cp', 'com.openggf.tools.GameplayInputBranchTool',
                 'com.example.OtherMain'],
                ['java', '-cp', '/tmp/openggf.jar', 'com.example.OtherMain',
                 'com.openggf.tools.GameplayInputBranchTool'],
                ['java', '-jar', '/tmp/tool.jar',
                 'com.openggf.tools.GameplayInputBranchTool'],
                ['java', '@launcher.args',
                 'com.openggf.tools.GameplayInputBranchTool'],
                ['java', '--source', '25',
                 'com.openggf.tools.GameplayInputBranchTool'],
                ['java', '--class-path']]:
            with self.subTest(command=command):
                self.assertFalse(module.interfering_command(command))

    def test_direct_compiler_and_real_test_launchers_are_detected(self):
        for command in [['/usr/bin/javac', 'Probe.java'],
                        ['java', 'org.codehaus.plexus.classworlds.launcher.Launcher', 'compile'],
                        ['java', '-jar', '/tmp/surefirebooter-123.jar'],
                        ['/tmp/asprof', 'collect']]:
            with self.subTest(command=command):
                self.assertTrue(module.interfering_command(command))

    def test_changed_session_identity_cannot_signal_a_foreign_group(self):
        process = Mock(pid=123)
        process.poll.return_value = None
        with patch.object(module.os, 'getpgid', return_value=124), patch.object(module.os, 'killpg') as kill:
            with self.assertRaisesRegex(RuntimeError, 'identity changed'):
                module.stop_owned_group(process)
            kill.assert_not_called()

    def test_unresponsive_owned_session_has_bounded_cleanup(self):
        process = Mock(pid=123)
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired('owned', 10), 0]
        with patch.object(module.os, 'getpgid', return_value=123), patch.object(module.os, 'killpg') as kill:
            module.stop_owned_group(process)
            self.assertEqual(kill.call_args_list[0].args, (123, signal.SIGTERM))
            self.assertEqual(kill.call_args_list[1].args, (123, signal.SIGKILL))
            process.wait.assert_any_call(timeout=10)


    @unittest.skipUnless(sys.platform.startswith('linux'), 'Linux OS lease regression')
    def test_measurement_runs_once_while_foreign_category_retains_idle_tree(self):
        from maven_queue import worktree_build_slot
        import json
        with tempfile.TemporaryDirectory(prefix='ggf-isolation-') as temporary:
            root = Path(temporary) / 'repo'
            root.mkdir()
            def git(*args):
                subprocess.run(['git', *args], cwd=root, check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            git('init', '-b', 'main')
            git('-c', 'user.name=OpenGGF test fixture', '-c', 'user.email=noreply@openai.com',
                'commit', '--allow-empty', '-m', 'test: isolated lease fixture')
            peer = root / '.worktrees' / 'peer'
            git('worktree', 'add', '--detach', str(peer), 'HEAD')
            audit = root / 'audit.json'
            source = Path(__file__).with_name('run_isolated.py').resolve()
            code = f"""
import sys
sys.path.insert(0, {str(source.parent)!r})
import run_isolated as runner
runner.interfering_processes = lambda: []
sys.argv = ['run_isolated', '--worktree', {str(root)!r}, '--cwd', {str(root)!r},
            '--audit-report', {str(audit)!r}, '--', sys.executable, '-c', 'pass']
raise SystemExit(runner.main())
"""
            with worktree_build_slot(peer):
                for exit_code in (0, 7):
                    with self.subTest(exit_code=exit_code):
                        command = code.replace("'-c', 'pass'", f"'-c', 'raise SystemExit({exit_code})'")
                        result = subprocess.run([sys.executable, '-u', '-c', command],
                                                capture_output=True, text=True, timeout=10)
                        self.assertEqual(exit_code, result.returncode, result.stdout + result.stderr)
                        report = json.loads(audit.read_text())
                        self.assertEqual(exit_code, report['exit_code'])
                        self.assertTrue(report['exclusive_test_lease'])
                        self.assertTrue(report['exclusive_build_activity_lease'])
            records = [json.loads(line) for line in (root / '.git/maven-queue-log.jsonl').read_text().splitlines()]
            self.assertEqual(2, len(records))
            for record in records:
                self.assertEqual('completed', record['outcome'])
                self.assertGreater(record['holdSeconds'], 0)


if __name__ == '__main__':
    unittest.main()
