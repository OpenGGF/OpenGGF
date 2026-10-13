"""Regression checks for the P5 measurement isolation/ownership hazard."""
import importlib.util
from pathlib import Path
import signal
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


if __name__ == '__main__':
    unittest.main()
