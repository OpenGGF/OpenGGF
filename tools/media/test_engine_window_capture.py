"""Failure injection for owned desktop capture; no display or process is launched."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('engine_window_capture.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureOwnershipFailures(unittest.TestCase):
    def test_unexpected_clean_or_failed_engine_exit_is_rejected(self):
        for code in (0, 37):
            with self.subTest(code=code):
                host = SimpleNamespace(returncode=code, poll=lambda: code)
                with self.assertRaisesRegex(RuntimeError, 'Unexpected Engine exit'):
                    capture.finish_engine_run(host, False, {'status': 'recording'})

    def test_intentional_eof_is_distinct_from_normal_close(self):
        host = SimpleNamespace(returncode=None, poll=lambda: None)
        receipt = {'status': 'recording'}
        capture.finish_engine_run(host, True, receipt)
        self.assertEqual(receipt['status'], 'stopped before normal window close')

    def test_nonzero_window_close_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'close exited 7'):
            capture.require_successful_close(SimpleNamespace(returncode=7))

    def test_cleanup_attempts_every_resource_and_reports_failures(self):
        events = []
        processes = {name: SimpleNamespace(poll=lambda: None) for name in ('engine', 'video', 'audio')}
        def stop(process):
            name = next(k for k, p in processes.items() if p is process)
            events.append(name)
            if name == 'engine': raise OSError('injected stop failure')
            process.poll = lambda: 0
        def focus_fail():
            events.append('focus'); raise OSError('injected focus failure')
        def unload(*args, **kwargs):
            events.append('sink'); raise OSError('injected unload failure')
        connection = SimpleNamespace(close=lambda: events.append('display'))
        log = SimpleNamespace(close=lambda: events.append('log'))
        with patch.object(capture, 'stop', stop), patch.object(capture.subprocess, 'run', unload):
            result = capture.cleanup_owned(processes, SimpleNamespace(destroy=focus_fail), connection, '123', log)
        self.assertEqual(events, ['engine', 'video', 'audio', 'focus', 'display', 'sink', 'log'])
        self.assertEqual(result['processes_stopped'], {'engine': False, 'video': True, 'audio': True})
        self.assertFalse(result['focus_window_destroyed'])
        self.assertFalse(result['audio_module_unloaded'])
        self.assertTrue(result['display_closed'])
        self.assertTrue(result['temporary_log_closed'])
        self.assertEqual(len(result['errors']), 3)

    def test_primary_late_exit_survives_cleanup_failure(self):
        class Host:
            pid = 777
            returncode = None
            polls = 0
            def poll(self):
                self.polls += 1
                if self.polls > 2: self.returncode = 37
                return self.returncode
        host = Host()
        video = SimpleNamespace(pid=778, poll=lambda: None)
        audio = SimpleNamespace(pid=779, poll=lambda: None)
        window = Mock(id=123)
        window.get_wm_name.return_value = 'OpenGGF test'
        window.get_geometry.return_value = SimpleNamespace(width=640, height=448, depth=24)
        window.get_attributes.return_value = SimpleNamespace(map_state=capture.X.IsViewable)
        window.get_full_property.return_value = SimpleNamespace(value=[777])
        connection = Mock()
        def stop(process):
            if process is video: raise OSError('injected recorder stop failure')
            if process is audio: process.poll = lambda: 0
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'repo'; root.mkdir(); (root / 'pom.xml').touch()
            out = Path(temp) / 'evidence'
            argv = ['capture', '--repo', str(root), '--classpath', '/compiled', '--missing-rom', '--out', str(out)]
            with patch.object(capture.sys, 'argv', argv), patch.object(capture.display, 'Display', return_value=connection), \
                    patch.object(capture, 'owned_window', return_value=window), patch.object(capture.time, 'sleep'), \
                    patch.object(capture.subprocess, 'check_output', return_value='123'), \
                    patch.object(capture.subprocess, 'Popen', side_effect=[host, video, audio]), \
                    patch.object(capture.subprocess, 'run', side_effect=OSError('injected sink failure')), \
                    patch.object(capture, 'stop', stop), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'Unexpected Engine exit 37'):
                    capture.main()
            receipt = json.loads((out / 'window-evidence.json').read_text())
            self.assertEqual(receipt['status'], 'failed')
            self.assertIn('Unexpected Engine exit 37', receipt['failure'])
            self.assertFalse(receipt['all_owned_processes_stopped'])
            self.assertIsNone(receipt['owned_audio_module_removed'])
            self.assertEqual(len(receipt['cleanup']['errors']), 2)
            connection.close.assert_called_once()


if __name__ == '__main__':
    unittest.main()
