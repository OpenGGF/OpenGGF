"""Capture isolation and failure controls; never access the user's display."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('engine_window_capture.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureOwnershipFailures(unittest.TestCase):
    def test_missing_virtual_display_fails_before_desktop_engine_or_audio_access(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'repo'; root.mkdir(); (root / 'pom.xml').touch()
            argv = ['capture', '--repo', str(root), '--classpath', '/compiled',
                    '--missing-rom', '--out', str(Path(temp) / 'evidence')]
            with patch.object(capture.sys, 'argv', argv), \
                    patch('shutil.which', return_value=None), \
                    patch.object(capture.display, 'Display', side_effect=RuntimeError('desktop accessed')) as connect, \
                    patch.object(capture.subprocess, 'Popen') as launch, \
                    patch.object(capture.subprocess, 'check_output') as audio, \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'Xvfb.*required'):
                    capture.main()
                connect.assert_not_called()
                launch.assert_not_called()
                audio.assert_not_called()

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

    def test_key_focus_distinguishes_owned_foreign_and_pointer_root(self):
        window = SimpleNamespace(id=123)
        for focus, owned, focus_id in ((SimpleNamespace(id=123), True, 123), (SimpleNamespace(id=456), False, 456),
                                       (capture.X.PointerRoot, False, capture.X.PointerRoot)):
            with self.subTest(focus=focus):
                connection = SimpleNamespace(get_input_focus=lambda: SimpleNamespace(focus=focus))
                self.assertEqual(capture.key_focus(connection, window), {'window_id': focus_id, 'owned': owned})

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
        virtual = SimpleNamespace(name=':1234', process=SimpleNamespace(pid=776, poll=lambda: None),
                                  start=lambda: {'DISPLAY': ':1234'}, close=Mock())
        def stop(process):
            if process is video: raise OSError('injected recorder stop failure')
            if process is audio: process.poll = lambda: 0
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'repo'; root.mkdir(); (root / 'pom.xml').touch()
            out = Path(temp) / 'evidence'
            argv = ['capture', '--repo', str(root), '--classpath', '/compiled', '--missing-rom', '--out', str(out)]
            with patch.object(capture.sys, 'argv', argv), patch.object(capture.display, 'Display', return_value=connection) as connect, \
                    patch.object(capture, 'VirtualDisplay', return_value=virtual), \
                    patch.object(capture, 'owned_window', return_value=window), patch.object(capture.time, 'sleep'), \
                    patch.object(capture.subprocess, 'check_output', return_value='123'), \
                    patch.object(capture.subprocess, 'Popen', side_effect=[host, video, audio]) as launch, \
                    patch.object(capture.subprocess, 'run', side_effect=OSError('injected sink failure')), \
                    patch.object(capture, 'stop', stop), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError, 'Unexpected Engine exit 37'):
                    capture.main()
                connect.assert_called_once_with(':1234')
                for invocation in launch.call_args_list:
                    self.assertEqual(invocation.kwargs['env']['DISPLAY'], ':1234')
            receipt = json.loads((out / 'window-evidence.json').read_text())
            self.assertEqual(receipt['status'], 'failed')
            self.assertIn('Unexpected Engine exit 37', receipt['failure'])
            self.assertFalse(receipt['all_owned_processes_stopped'])
            self.assertIsNone(receipt['owned_audio_module_removed'])
            self.assertEqual(len(receipt['cleanup']['errors']), 2)
            connection.close.assert_called_once()
            virtual.close.assert_called_once()
            capture_display = receipt['virtual_display']
            self.assertEqual(capture_display, {'name': ':1234', 'pid': 776, 'owned': True})


class VirtualDisplayIsolation(unittest.TestCase):
    def server(self, response, log):
        """Use a real bounded child to exercise displayfd and teardown, without X11."""
        original_launch = capture.subprocess.Popen
        def launch(command, **kwargs):
            self.assertIn('-nolisten', command)
            self.assertEqual(command[command.index('-nolisten') + 1], 'tcp')
            self.assertNotIn('DISPLAY', kwargs['env'])
            self.assertNotIn('WAYLAND_DISPLAY', kwargs['env'])
            fd = command[command.index('-displayfd') + 1]
            script = 'import os,sys,time; os.write(int(sys.argv[1]),bytes.fromhex(sys.argv[2])); time.sleep(30)'
            return original_launch([capture.sys.executable, '-c', script, fd, response.hex()], **kwargs)
        return capture.VirtualDisplay(log), launch

    def test_allocated_display_and_child_environment_replace_desktop_and_wayland(self):
        with tempfile.TemporaryFile() as log, \
                patch.dict(os.environ, {'DISPLAY': ':0', 'WAYLAND_DISPLAY': 'wayland-0',
                                       'XDG_SESSION_TYPE': 'wayland', 'XAUTHORITY': '/desktop/auth'}):
            virtual, launch = self.server(b'1234\n', log)
            with patch.object(capture.shutil, 'which', return_value='/controlled/Xvfb'), \
                    patch.object(capture.subprocess, 'Popen', side_effect=launch):
                try:
                    environment = virtual.start()
                    self.assertEqual(environment['DISPLAY'], ':1234')
                    self.assertNotIn('WAYLAND_DISPLAY', environment)
                    self.assertEqual(environment['XDG_SESSION_TYPE'], 'x11')
                    self.assertEqual(environment['XAUTHORITY'], '/dev/null')
                    self.assertEqual(os.environ['DISPLAY'], ':0')
                    self.assertEqual(os.environ['WAYLAND_DISPLAY'], 'wayland-0')
                finally:
                    virtual.close()
            self.assertIsNotNone(virtual.process.poll())

    def test_bad_or_desktop_display_number_fails_and_reaps_server(self):
        for response, message in ((b'not-a-display\n', 'invalid display number'),
                                  (b'0\n', 'desktop display number')):
            with self.subTest(response=response), tempfile.TemporaryFile() as log, \
                    patch.dict(os.environ, {'DISPLAY': ':0'}):
                virtual, launch = self.server(response, log)
                with patch.object(capture.shutil, 'which', return_value='/controlled/Xvfb'), \
                        patch.object(capture.subprocess, 'Popen', side_effect=launch):
                    with self.assertRaisesRegex(RuntimeError, message):
                        virtual.start()
                self.assertIsNotNone(virtual.process.poll())
                self.assertIsNone(virtual.name)

    def test_failed_virtual_display_cleanup_does_not_skip_other_cleanup(self):
        virtual = SimpleNamespace(close=Mock(side_effect=OSError('server stop failed')))
        connection, log = Mock(), Mock()
        with patch.object(capture.subprocess, 'run') as unload:
            result = capture.cleanup_owned({}, None, connection, '123', log, virtual)
        self.assertFalse(result['virtual_display_stopped'])
        connection.close.assert_called_once()
        unload.assert_called_once()
        log.close.assert_called_once()

    def test_virtual_display_timeout_reaps_server_without_desktop_fallback(self):
        with tempfile.TemporaryFile() as log:
            virtual, launch = self.server(b'', log)
            with patch.object(capture.shutil, 'which', return_value='/controlled/Xvfb'), \
                    patch.object(capture.subprocess, 'Popen', side_effect=launch), \
                    patch.object(capture.select, 'select', return_value=([], [], [])):
                with self.assertRaisesRegex(RuntimeError, 'allocation timed out'):
                    virtual.start()
            self.assertIsNotNone(virtual.process.poll())
            self.assertIsNone(virtual.name)


if __name__ == '__main__':
    unittest.main()
