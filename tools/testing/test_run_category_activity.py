"""Exercise build/measurement exclusion with real leases in a private repository."""
from contextlib import ExitStack
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

from maven_activity import build_activity_slot
from maven_queue import _acquire, _unlock, inherited_slot

TOOLS = Path(__file__).resolve().parent


@unittest.skipUnless(os.name == 'posix', 'POSIX shared activity leases')
class BuildActivityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.processes = []
        self.addCleanup(self.stop_processes)

    def stop_processes(self):
        for process in self.processes:
            if process.poll() is None:
                process.terminate()
            try:
                process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate(timeout=5)

    def launch(self, name, *, exclusive=False, code=None, env=None):
        marker = self.root / name
        script = f'''
import sys, time, json
from pathlib import Path
sys.path.insert(0, {str(TOOLS)!r})
from maven_activity import build_activity_slot
from maven_queue import handle_termination
root, marker = Path({str(self.root)!r}), Path({str(marker)!r})
with handle_termination():
'''
        body = code or f'''
with build_activity_slot(root, exclusive={exclusive}):
    marker.with_suffix('.started').touch()
    while not marker.with_suffix('.release').exists():
        time.sleep(.01)
'''
        script += ''.join('    ' + line + '\n' for line in body.strip().splitlines())
        process = subprocess.Popen([sys.executable, '-u', '-c', script], cwd=self.root,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, env=env)
        self.processes.append(process)
        return process, marker

    def wait_file(self, process, path):
        deadline = time.monotonic() + 5
        while not path.exists():
            if process.poll() is not None:
                self.fail(process.communicate()[0])
            if time.monotonic() >= deadline:
                self.fail('Owned process did not reach ' + path.name)
            time.sleep(.01)

    def wait_intent(self, process):
        deadline = time.monotonic() + 5
        with (self.root / '.git/maven-build-intent.lock').open('a+b') as stream:
            while _acquire(stream):
                _unlock(stream)
                if process.poll() is not None or time.monotonic() >= deadline:
                    self.fail('Measurement did not close the build entrance')
                time.sleep(.01)

    def test_builds_overlap_and_measurement_drains_without_new_builds_overtaking(self):
        with ExitStack() as active:
            active.enter_context(build_activity_slot(self.root))
            active.enter_context(build_activity_slot(self.root))
            measurement, measured = self.launch('measurement', exclusive=True)
            self.wait_intent(measurement)
            later, built = self.launch('later-build')
            time.sleep(.15)
            self.assertFalse(measured.with_suffix('.started').exists())
            self.assertFalse(built.with_suffix('.started').exists())
        self.wait_file(measurement, measured.with_suffix('.started'))
        self.assertFalse(built.with_suffix('.started').exists())
        measured.with_suffix('.release').touch()
        self.assertEqual(0, measurement.wait(timeout=5))
        self.wait_file(later, built.with_suffix('.started'))
        built.with_suffix('.release').touch()
        self.assertEqual(0, later.wait(timeout=5))

    def test_cancelled_measurement_releases_intent_without_releasing_active_build(self):
        with build_activity_slot(self.root):
            process, marker = self.launch('cancelled', exclusive=True)
            self.wait_intent(process)
            process.terminate()
            process.communicate(timeout=5)
            self.assertFalse(marker.with_suffix('.started').exists())
            with build_activity_slot(self.root):
                with (self.root / '.git/maven-build-activity.lock').open('a+b') as probe:
                    self.assertFalse(_acquire(probe))

    def test_cli_build_waits_for_measurement_without_joining_test_queue(self):
        binary = self.root / 'bin'
        binary.mkdir()
        (binary / 'mvn').write_text('#!/bin/sh\nexit 0\n')
        (binary / 'mvn').chmod(0o755)
        env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'])
        with build_activity_slot(self.root, exclusive=True):
            process, marker = self.launch('compile', env=env, code='''
from maven_queue import main
marker.with_suffix('.ready').touch()
result = main(['-Dmse=off', 'compile'])
marker.with_suffix('.started').touch()
raise SystemExit(result)
''')
            self.wait_file(process, marker.with_suffix('.ready'))
            time.sleep(.2)
            self.assertFalse(marker.with_suffix('.started').exists())
            self.assertFalse((self.root / '.git/maven-waiters').exists())
        self.assertEqual(0, process.wait(timeout=5))
        self.assertTrue(marker.with_suffix('.started').exists())

    def test_logged_preparation_excludes_isolation_wait_from_process_timeout(self):
        binary = self.root / 'bin'
        binary.mkdir()
        (binary / 'mvn').write_text('#!/bin/sh\necho prepared\n')
        (binary / 'mvn').chmod(0o755)
        env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'])
        with build_activity_slot(self.root, exclusive=True):
            process, marker = self.launch('prepare', env=env, code='''
from category_artifacts import run_logged
timings = {}
marker.with_suffix('.ready').touch()
result = run_logged(['mvn', '-Dmse=off', 'test-compile'], root, root/'build.log',
                    timeout=.3, timings=timings)
marker.with_suffix('.result').write_text(json.dumps(dict(code=result, **timings)))
''')
            self.wait_file(process, marker.with_suffix('.ready'))
            time.sleep(.6)
            self.assertFalse(marker.with_suffix('.result').exists())
        self.assertEqual(0, process.wait(timeout=5))
        result = json.loads(marker.with_suffix('.result').read_text())
        self.assertEqual(0, result['code'])
        self.assertGreater(result['build_wait_seconds'], .3)

    def test_activity_is_inherited_by_child_after_wrapper_death(self):
        process, marker = self.launch('orphan', code='''
import os, subprocess
from maven_queue import inherited_slot
child = "from pathlib import Path; import time; p=Path(" + repr(str(marker)) + "); p.with_suffix('.started').touch(); " + "\\nwhile not p.with_suffix('.release').exists(): time.sleep(.01)"
with build_activity_slot(root) as fds:
    subprocess.Popen([sys.executable, '-c', child], **inherited_slot(None, fds),
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    while not marker.with_suffix('.started').exists(): time.sleep(.01)
    os._exit(0)
''')
        self.addCleanup(marker.with_suffix('.release').touch)
        self.wait_file(process, marker.with_suffix('.started'))
        self.assertEqual(0, process.wait(timeout=5))
        try:
            with (self.root / '.git/maven-build-activity.lock').open('a+b') as probe:
                self.assertFalse(_acquire(probe))
        finally:
            marker.with_suffix('.release').touch()
        with build_activity_slot(self.root, exclusive=True):
            pass


if __name__ == '__main__':
    unittest.main()
