"""Launcher safety controls; harmless owned processes, no ROM/emulator or Maven tests."""
import contextlib
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_s2_fixed_tail_pressure as probe


class RuntimeBindingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.task = self.root / 'task'; self.task.mkdir()
        self.stage = self.task / 'tracechaser'
        (self.stage / 'dependencies').mkdir(parents=True)
        self.binary = self.stage / 'bizhawk-headless/bin/Release'; self.binary.mkdir(parents=True)
        self.home = self.root / 'hawk'; (self.home / 'dll').mkdir(parents=True)
        required = ['EmuHawk.exe', 'dll/BizHawk.Common.dll', 'dll/BizHawk.Emulation.Cores.dll',
                    'dll/gpgx.wbx.zst', 'dll/libwaterboxhost.so']
        for name in required + ['dll/AdditionalDependency.dll']:
            (self.home / name).write_bytes(name.encode())
        (self.binary / 'ShadowingDependency.dll').write_bytes(b'staged dependency')
        probe.save(self.stage / 'dependencies/bizhawk-2.11-linux-x64.lock.json', dict(required_files=required))
        self.rom = self.root / 'original.rom'; self.rom.write_bytes(b'identity controlled by test')
        self.movie = self.root / 'original.bk2'; self.movie.write_bytes(b'original input')
        self.exe = self.task / 'S2FixedTailPressureProbe.exe'; self.exe.write_bytes(b'owned probe')
        self.host = self.binary / 'BizHawk.Headless.Gpgx.exe'; self.host.write_bytes(b'owned host')
        probe.save(self.task / 'ownership.json', dict(uid=os.getuid(), worktree=str(probe.ROOT), tracechaser=probe.PIN))
        self.prepared = dict(execution_status='completed', source_sha256=probe.sha(probe.ROOT / 'tools/bizhawk/S2FixedTailPressureProbe.cs'),
                             probe_sha256=probe.sha(self.exe), host_sha256=probe.sha(self.host),
                             runtime_binding=probe.runtime_binding(self.home, self.stage))
        probe.save(self.task / 'prepare.json', self.prepared)
        self.active = set(); self.order = []; self.commands = []

    @contextlib.contextmanager
    def lease(self, name):
        if name == 'admission':
            self.assertFalse(self.active)
            self.assertIn('preflight', self.order)
        self.active.add(name); self.order.append('enter-' + name)
        try:
            yield (13,) if name == 'activity' else 12
        finally:
            self.active.remove(name); self.order.append('leave-' + name)

    def launch(self, home=None, preflight_code=0, during_admission=None):
        original_sha = probe.sha
        def digest(path, algorithm='sha256'):
            if Path(path) == self.rom and algorithm == 'sha1':
                return '8bca5dcef1af3e00098666fd892dc1c2a76333f9'
            return original_sha(path, algorithm)
        def command(args, env, log, leases, cwd, timeout=180):
            self.commands.append(args)
            self.assertTrue(all(key not in env for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY')))
            if args[0] == 'bash':
                self.assertEqual({'tree', 'activity'}, self.active)
                self.assertEqual(['--bizhawk-home', str(home or self.home)], args[-2:])
                self.order.append('preflight')
                code = preflight_code
            else:
                self.assertEqual({'admission'}, self.active)
                self.order.append('native')
                code = 0
            return dict(command=args, exit_code=code, execution_seconds=0, log=str(log))
        @contextlib.contextmanager
        def admission(*args, **kwargs):
            with self.lease('admission') as fd:
                if during_admission:
                    during_admission()
                yield fd
        argv = ['--phase', 'run', '--task-root', str(self.task), '--bizhawk-home', str(home or self.home),
                '--rom', str(self.rom), '--movie', str(self.movie), '--case', 'reinstall-two']
        with patch.dict(os.environ, dict(DISPLAY='fixture', WAYLAND_DISPLAY='fixture', XAUTHORITY='fixture')), \
                patch.object(probe, 'sha', side_effect=digest), patch.object(probe, 'command', side_effect=command), \
                patch.object(probe, 'worktree_build_slot', side_effect=lambda *a, **k: self.lease('tree')), \
                patch.object(probe, 'build_activity_slot', side_effect=lambda *a, **k: self.lease('activity')), \
                patch.object(probe, 'maven_slot', side_effect=admission), \
                patch.object(probe, 'verify_output', return_value=dict(execution_status='completed')), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return probe.main(argv)

    def test_matching_runtime_revalidates_before_admission(self):
        self.assertEqual(0, self.launch())
        self.assertLess(self.order.index('preflight'), self.order.index('enter-admission'))
        self.assertLess(self.order.index('leave-activity'), self.order.index('enter-admission'))
        self.assertLess(self.order.index('enter-admission'), self.order.index('native'))
        self.assertEqual(2, len(self.commands))
        provenance = json.loads(next(self.task.glob('run-*/provenance.json')).read_text())
        self.assertEqual(self.prepared['runtime_binding'], provenance['prepared']['runtime_binding'])
        self.assertEqual(0, provenance['run_preflight']['command']['exit_code'])

    def test_fresh_prepare_does_not_inject_absent_mono_search_directory(self):
        self.host.unlink(); (self.binary / 'ShadowingDependency.dll').unlink(); self.binary.rmdir()
        original_sha = probe.sha
        def digest(path, algorithm='sha256'):
            return ('8bca5dcef1af3e00098666fd892dc1c2a76333f9' if Path(path) == self.rom and algorithm == 'sha1'
                    else original_sha(path, algorithm))
        calls = []
        def command(args, env, log, leases, cwd, timeout=180):
            self.assertEqual({'tree', 'activity'}, self.active)
            self.assertEqual(str(self.home / 'dll'), env['MONO_PATH'])
            calls.append(args)
            if 'build.sh' in args[1]:
                self.assertFalse(self.binary.exists())
                self.binary.mkdir(parents=True); self.host.write_bytes(b'fresh host')
            return dict(command=args, exit_code=0, execution_seconds=0)
        with patch.object(probe, 'sha', side_effect=digest), patch.object(probe, 'command', side_effect=command), \
                patch.object(probe, 'worktree_build_slot', side_effect=lambda *a, **k: self.lease('tree')), \
                patch.object(probe, 'build_activity_slot', side_effect=lambda *a, **k: self.lease('activity')), \
                patch.object(probe, 'maven_slot') as admission, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, probe.main(['--phase', 'prepare', '--task-root', str(self.task),
                '--bizhawk-home', str(self.home), '--rom', str(self.rom), '--movie', str(self.movie)]))
        self.assertEqual(3, len(calls)); admission.assert_not_called()
        prepared = json.loads((self.task / 'prepare.json').read_text())
        self.assertEqual(probe.runtime_binding(self.home, self.stage), prepared['runtime_binding'])

    def test_different_selected_installation_rejected_before_admission(self):
        other = self.root / 'other'; (other / 'dll').mkdir(parents=True)
        for source in self.home.rglob('*'):
            if source.is_file():
                (other / source.relative_to(self.home)).write_bytes(source.read_bytes())
        with self.assertRaises(SystemExit) as raised:
            self.launch(home=other)
        self.assertEqual(2, raised.exception.code)
        self.assertEqual([], self.commands); self.assertEqual([], self.order)

    def test_changed_installation_dependency_rejected_before_admission(self):
        (self.home / 'dll/AdditionalDependency.dll').write_bytes(b'changed after prepare')
        with self.assertRaises(SystemExit): self.launch()
        self.assertEqual([], self.commands); self.assertEqual([], self.order)

    def test_new_installation_dependency_rejected_before_admission(self):
        (self.home / 'dll/NewDependency.dll').write_bytes(b'new loader candidate')
        with self.assertRaises(SystemExit): self.launch()
        self.assertEqual([], self.commands); self.assertEqual([], self.order)

    def test_changed_staged_dependency_rejected_before_admission(self):
        (self.binary / 'ShadowingDependency.dll').write_bytes(b'changed staged loader candidate')
        with self.assertRaises(SystemExit): self.launch()
        self.assertEqual([], self.commands); self.assertEqual([], self.order)

    def test_failed_strict_preflight_rejected_before_admission(self):
        with self.assertRaises(SystemExit): self.launch(preflight_code=1)
        self.assertEqual(1, len(self.commands)); self.assertNotIn('enter-admission', self.order)
        self.assertFalse(self.active)

    def test_runtime_change_while_queued_rejected_before_native(self):
        def mutate(): (self.home / 'dll/AdditionalDependency.dll').write_bytes(b'changed during queue wait')
        self.assertEqual(2, self.launch(during_admission=mutate))
        self.assertEqual(1, len(self.commands)); self.assertNotIn('native', self.order)
        host = json.loads(next(self.task.glob('run-*/host.json')).read_text())
        self.assertEqual('incomplete', host['execution_status'])
        self.assertIsNone(host['commands'][0]['exit_code'])


@unittest.skipUnless(sys.platform == 'linux', 'Native launcher uses Linux child-subreaper ownership')
class OwnedCompilerCleanupTests(unittest.TestCase):
    def exercise(self, mode):
        with probe.owned_child_reaper(), tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # All fixtures are our own process groups. The peer is a negative kill-scope control.
            peer = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True)
            driver = None
            try:
                code = r'''
import contextlib,fcntl,json,os,signal,subprocess,sys,time
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import run_s2_fixed_tail_pressure as p
root=Path(sys.argv[2]);mode=sys.argv[3]
grandchild="import os,signal,time;from pathlib import Path;signal.signal(signal.SIGTERM,signal.SIG_IGN);Path(%r).write_text(str(os.getpid()));time.sleep(30)" % str(root/'grandchild.pid')
parent="import json,os,signal,subprocess,sys,time;from pathlib import Path;Path(%r).write_text(str(os.getpid()));Path(%r).write_text(json.dumps(dict(blocked=sorted(int(s) for s in signal.pthread_sigmask(signal.SIG_BLOCK,[]) if s in (signal.SIGINT,signal.SIGTERM)),sigint_ignored=signal.getsignal(signal.SIGINT)==signal.SIG_IGN,sigterm_default=signal.getsignal(signal.SIGTERM)==signal.SIG_DFL)));subprocess.Popen([sys.executable,'-c',%r],close_fds=False);\nwhile not Path(%r).exists():time.sleep(.01)\n" % (str(root/'parent.pid'),str(root/'child-signals.json'),grandchild,str(root/'grandchild.pid'))
parent += "time.sleep(30)" if not mode.startswith('success') else ""
if mode.startswith('spawn-'):
 real_popen=p.subprocess.Popen
 def signal_inside_popen(*args,**kwargs):
  process=real_popen(*args,**kwargs)
  deadline=time.monotonic()+5
  while not (root/'grandchild.pid').exists() and time.monotonic()<deadline:time.sleep(.01)
  assert (root/'grandchild.pid').exists(),'spawn boundary child did not become ready'
  (root/'spawn-signal').write_text(str(process.pid))
  os.kill(os.getpid(),signal.SIGTERM if mode=='spawn-term' else signal.SIGINT)
  return process
 p.subprocess.Popen=signal_inside_popen
if mode=='success-ignore-int':signal.signal(signal.SIGINT,signal.SIG_IGN)
@contextlib.contextmanager
def lease(name):
 with (root/name).open('w') as lock:
  fcntl.flock(lock,fcntl.LOCK_EX)
  try:yield lock.fileno()
  finally:
   for pidfile in (root/'parent.pid',root/'grandchild.pid'):
    assert pidfile.exists(),pidfile
    assert not Path('/proc/'+pidfile.read_text()).exists(), 'owned descendant not reaped before lease release'
   try:os.waitpid(-int((root/'parent.pid').read_text()),os.WNOHANG)
   except ChildProcessError:pass
   else:raise AssertionError('unreaped owned group child')
   with (root/name).open() as contender:
    try:fcntl.flock(contender,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:pass
    else:raise AssertionError('lease released before cleanup verification')
   (root/(name+'.checked')).write_text('descendants reaped while lease held')
   fcntl.flock(lock,fcntl.LOCK_UN)
try:
 with p.handle_termination(),lease('tree') as tree,lease('activity') as activity:
  handlers={s:signal.getsignal(s) for s in (signal.SIGINT,signal.SIGTERM)}
  try:p.command([sys.executable,'-c',parent],os.environ.copy(),root/'compiler.log',(tree,activity),root,timeout=.7 if mode=='timeout' else 10)
  finally:assert all(signal.getsignal(s)==h for s,h in handlers.items()),'parent handlers not restored'
except subprocess.TimeoutExpired:assert mode=='timeout'
except KeyboardInterrupt:assert mode in ('cancel','spawn-term','spawn-int')
else:assert mode.startswith('success')
(root/'completed').write_text(mode)
'''
                driver = subprocess.Popen([sys.executable, '-B', '-c', code, str(Path(__file__).resolve().parent), str(root), mode],
                                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True)
                deadline = time.monotonic() + 5
                while not (root / 'grandchild.pid').exists() and driver.poll() is None and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue((root / 'grandchild.pid').exists(), 'owned fixture did not become ready')
                if mode == 'cancel': driver.send_signal(signal.SIGTERM)
                output, _ = driver.communicate(timeout=15)
                self.assertEqual(0, driver.returncode, output)
                self.assertEqual(mode, (root / 'completed').read_text())
                self.assertTrue((root / 'tree.checked').is_file())
                self.assertTrue((root / 'activity.checked').is_file())
                child_signals = json.loads((root / 'child-signals.json').read_text())
                self.assertEqual([], child_signals['blocked'])
                self.assertTrue(child_signals['sigterm_default'])
                self.assertEqual(mode == 'success-ignore-int', child_signals['sigint_ignored'])
                if mode.startswith('spawn-'): self.assertTrue((root / 'spawn-signal').is_file())
                self.assertIsNone(peer.poll(), 'cleanup signalled separate owned peer group')
            finally:
                if driver is not None and driver.poll() is None:
                    os.killpg(driver.pid, signal.SIGKILL); driver.wait()
                pidfile = root / 'parent.pid'
                if pidfile.exists():
                    group = int(pidfile.read_text())
                    with contextlib.suppress(ProcessLookupError): os.killpg(group, signal.SIGKILL)
                    while True:
                        try: os.waitpid(-group, 0)
                        except ChildProcessError: break
                peer.terminate(); peer.wait(timeout=5)

    def test_timeout_reaps_descendants_before_build_leases_release(self): self.exercise('timeout')
    def test_cancellation_reaps_descendants_before_build_leases_release(self): self.exercise('cancel')
    def test_successful_parent_cannot_leave_compiler_descendants(self): self.exercise('success')
    def test_spawn_boundary_sigterm_reaps_descendants_before_build_leases_release(self): self.exercise('spawn-term')
    def test_spawn_boundary_sigint_reaps_descendants_before_build_leases_release(self): self.exercise('spawn-int')
    def test_child_cancellation_mask_and_ignored_handler_preserved(self): self.exercise('success-ignore-int')


if __name__ == '__main__':
    unittest.main()
