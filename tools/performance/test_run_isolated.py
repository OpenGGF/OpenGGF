"""Regression checks for the P5 measurement isolation/ownership hazard."""
import importlib.util
from pathlib import Path
import signal
import os
import sys
import tempfile
import selectors
import time
import subprocess
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('run_isolated', Path(__file__).with_name('run_isolated.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TestMeasurementIsolation(unittest.TestCase):
    def test_benchmark_classpath_is_not_a_maven_launcher(self):
        self.assertFalse(module.interfering_command([
            '/usr/bin/java', '-cp', '/tmp/org.codehaus.plexus.classworlds.launcher.Launcher.jar',
            'com.openggf.tools.RuntimeEngineBenchmarkTool']))
        self.assertFalse(module.interfering_command([]))

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
    def test_retained_worktree_can_resume_before_measurement_admission(self):
        import fcntl
        with tempfile.TemporaryDirectory(prefix='ggf-isolation-') as temporary:
            root = Path(temporary) / 'repo'
            root.mkdir()
            def git(*args):
                subprocess.run(['git', *args], cwd=root, check=True,
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            git('init', '-b', 'main')
            git('-c', 'user.name=OpenGGF test fixture', '-c', 'user.email=noreply@openai.com',
                'commit', '--allow-empty', '-m',
                'test: create isolated lease fixture\n\nCo-authored-by: OpenAI Codex GPT-6.1-Sol <noreply@openai.com>')
            peer = root / '.worktrees' / 'peer'
            git('worktree', 'add', '--detach', str(peer), 'HEAD')
            audit = root / 'audit.json'
            source = Path(__file__).with_name('run_isolated.py').resolve()
            code = f"""
import sys
from pathlib import Path
from contextlib import contextmanager
sys.path.insert(0, {str(source.parent)!r})
import run_isolated as runner
original = runner.worktree_build_slot
@contextmanager
def observed(tree):
    if Path(tree) == Path({str(peer)!r}):
        print('WAITING_FOR_PEER', flush=True)
    with original(tree) as fd:
        yield fd
runner.worktree_build_slot = observed
# This private lock fixture launches no compiler/game and does not inspect
# unrelated host workloads. The separate classifier tests cover that guard.
runner.interfering_processes = lambda: []
sys.argv = ['run_isolated', '--worktree', {str(root)!r}, '--cwd', {str(root)!r},
            '--audit-report', {str(audit)!r}, '--', sys.executable, '-c', 'pass']
runner.main()
"""
            process = None
            try:
                with module.worktree_build_slot(peer):
                    process = subprocess.Popen([sys.executable, '-u', '-c', code],
                                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                               text=True)
                    with selectors.DefaultSelector() as selector:
                        selector.register(process.stdout, selectors.EVENT_READ)
                        deadline = time.monotonic() + 10
                        observed_output = b''
                        while time.monotonic() < deadline:
                            if selector.select(.2):
                                observed_output += os.read(process.stdout.fileno(), 4096)
                                if b'WAITING_FOR_PEER\n' in observed_output:
                                    break
                            if process.poll() is not None:
                                self.fail('isolation process exited before the retained-peer boundary')
                        else:
                            self.fail('isolation process never reached the retained-peer boundary')
                    # A category holder retains peer/target while requesting its
                    # next batch. It must be able to acquire shared admission and
                    # finish before the measurement takes exclusive admission.
                    with (root / '.git' / 'maven-queue.lock').open('a+b') as shared:
                        try:
                            fcntl.flock(shared, fcntl.LOCK_SH | fcntl.LOCK_NB)
                        except BlockingIOError:
                            self.fail('measurement blocks admission while waiting for a retained worktree')
                        fcntl.flock(shared, fcntl.LOCK_UN)
                output, _ = process.communicate(timeout=10)
                self.assertEqual(0, process.returncode, output[-2000:])
                import json
                self.assertEqual(0, json.loads(audit.read_text())['exit_code'])
            finally:
                if process is not None:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=5)
                    process.stdout.close()


if __name__ == '__main__':
    unittest.main()
