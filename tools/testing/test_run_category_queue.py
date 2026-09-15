"""Exercise the shared Maven queue with real competing processes, without Java."""
import os
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parent


class QueueTests(unittest.TestCase):
    def setUp(self):
        self.mode = patch.dict(os.environ, {"OPENGGF_MAVEN_QUEUE": "serial"})
        self.mode.start()
        self.addCleanup(self.mode.stop)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'repo'
        self.root.mkdir()
        self.git('init', '-q')
        (self.root / '.git/info/exclude').write_text('target/\n')
        self.git('-c', 'user.name=Test', '-c', 'user.email=test@example.com',
                 'commit', '--allow-empty', '-qm', 'initial')
        self.linked = Path(self.temp.name) / 'linked'
        self.git('worktree', 'add', '--detach', '-q', str(self.linked))
        self.processes = []
        self.addCleanup(self.stop_processes)

    def git(self, *args):
        subprocess.run(['git', *args], cwd=self.root, check=True, capture_output=True)

    def stop_processes(self):
        for process in self.processes:
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=5)

    def launch(self, name, root=None, resource_snapshot=(100 * 1024**3, 128, 0)):
        code = '''
import sys, time, json
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from maven_queue import maven_slot
import maven_resources
maven_resources.snapshot = lambda: json.loads(sys.argv[4])
root, marker = map(Path, sys.argv[2:4])
with maven_slot(root):
    marker.with_suffix('.started').touch()
    while not marker.with_suffix('.release').exists():
        time.sleep(.02)
'''
        marker = Path(self.temp.name) / name
        process = subprocess.Popen([sys.executable, '-c', code, str(TOOLS),
                                    str(root or self.root), str(marker), json.dumps(resource_snapshot)],
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.processes.append(process)
        return process, marker

    def wait_started(self, process, marker):
        deadline = time.monotonic() + 5
        while not marker.with_suffix('.started').exists():
            if process.poll() is not None:
                self.fail(process.communicate()[0])
            if time.monotonic() > deadline:
                self.fail('Queued process did not acquire its slot')
            time.sleep(.02)

    def test_linked_worktrees_wait_and_continue_without_task_registration(self):
        holder, first = self.launch('first')
        self.wait_started(holder, first)
        waiter, second = self.launch('second', self.linked)
        time.sleep(.3)
        self.assertIsNone(waiter.poll())
        self.assertFalse(second.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        self.assertEqual(0, holder.wait(timeout=5))
        self.wait_started(waiter, second)
        second.with_suffix('.release').touch()
        output = waiter.communicate(timeout=5)[0]
        self.assertEqual(0, waiter.returncode, output)
        self.assertIn('Waiting', output)
        # Persistent lock files are not ownership: the next process still runs.
        third, marker = self.launch('third')
        self.wait_started(third, marker)
        marker.with_suffix('.release').touch()
        self.assertEqual(0, third.wait(timeout=5))

    def test_cancelled_waiter_does_not_block_later_work(self):
        holder, first = self.launch('first')
        self.wait_started(holder, first)
        cancelled, second = self.launch('cancelled', self.linked)
        time.sleep(.2)
        cancelled.terminate()
        cancelled.wait(timeout=5)
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        later, third = self.launch('later')
        self.wait_started(later, third)
        self.assertFalse(second.with_suffix('.started').exists())
        third.with_suffix('.release').touch()
        self.assertEqual(0, later.wait(timeout=5))

    def test_dead_holder_releases_os_lock_without_manual_cleanup(self):
        holder, first = self.launch('first')
        self.wait_started(holder, first)
        waiter, second = self.launch('second')
        holder.kill()
        holder.wait(timeout=5)
        self.wait_started(waiter, second)
        second.with_suffix('.release').touch()
        self.assertEqual(0, waiter.wait(timeout=5))

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_auto_allows_linked_worktrees_but_serializes_same_tree(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        self.git('config', 'openggf.mavenMemoryGiB', '0.01')
        self.git('config', 'openggf.mavenCpuCores', '0.01')
        self.git('config', 'openggf.mavenHeadroomGiB', '0.01')
        first_process, first = self.launch('first')
        self.wait_started(first_process, first)
        second_process, second = self.launch('second', self.linked)
        self.wait_started(second_process, second)
        third_process, third = self.launch('third')
        time.sleep(.3)
        self.assertFalse(third.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        first_process.wait(timeout=5)
        self.wait_started(third_process, third)
        second.with_suffix('.release').touch()
        third.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_auto_queues_when_memory_budget_cannot_fit(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        self.git('config', 'openggf.mavenMemoryGiB', '1000000')
        process, marker = self.launch('too-large')
        time.sleep(.4)
        self.assertIsNone(process.poll())
        self.assertFalse(marker.with_suffix('.started').exists())

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_serial_holder_excludes_auto_request(self):
        holder, first = self.launch('serial')
        self.wait_started(holder, first)
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        waiter, second = self.launch('auto', self.linked)
        time.sleep(.3)
        self.assertFalse(second.with_suffix('.started').exists())
        self.assertIsNone(waiter.poll())

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_auto_ceiling_blocks_a_third_distinct_worktree(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        other = Path(self.temp.name) / 'other'
        self.git('worktree', 'add', '--detach', '-q', str(other))
        holder, first = self.launch('first')
        self.wait_started(holder, first)
        second_process, second = self.launch('second', self.linked)
        self.wait_started(second_process, second)
        third_process, third = self.launch('third', other)
        time.sleep(.3)
        self.assertFalse(third.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(third_process, third)
        second.with_suffix('.release').touch()
        third.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_auto_holder_excludes_serial_request(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        holder, first = self.launch('auto')
        self.wait_started(holder, first)
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'serial'
        waiter, second = self.launch('serial', self.linked)
        time.sleep(.3)
        self.assertFalse(second.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(waiter, second)
        second.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux inherited descriptor contract')
    def test_auto_killed_wrapper_retains_worktree_lease(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        command, marker = self.launch_command('auto-killed')
        self.wait_started(command, marker)
        command.kill()
        command.wait(timeout=5)
        later, last = self.launch('same-tree', self.linked)
        time.sleep(.3)
        self.assertFalse(last.with_suffix('.started').exists())
        # Another tree may still use the second slot.
        other, other_marker = self.launch('other-tree')
        self.wait_started(other, other_marker)
        marker.with_suffix('.release').touch()
        self.wait_started(later, last)
        last.with_suffix('.release').touch()
        other_marker.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_unavailable_counters_and_small_cpu_allocations_keep_serial_progress(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        for index, snapshot in enumerate((None, (100 * 1024**3, 2, 0))):
            first_process, first = self.launch(f'fallback-{index}', resource_snapshot=snapshot)
            self.wait_started(first_process, first)
            second_process, second = self.launch(f'fallback-next-{index}', self.linked,
                                                  resource_snapshot=snapshot)
            time.sleep(.3)
            self.assertFalse(second.with_suffix('.started').exists())
            first.with_suffix('.release').touch()
            first_process.wait(timeout=5)
            self.wait_started(second_process, second)
            second.with_suffix('.release').touch()
            second_process.wait(timeout=5)

    def launch_command(self, name, category=False, exit_code=0):
        """Run the real CLIs/runner, replacing only the Maven executable with a probe."""
        marker = Path(self.temp.name) / name
        self.addCleanup(marker.with_suffix('.release').touch)
        fake_maven = r"""
import json, sys, time
from pathlib import Path
marker = Path(sys.argv[1])
marker.with_suffix('.args').write_text(json.dumps({'cwd': str(Path.cwd()), 'args': sys.argv[3:]}))
marker.with_suffix('.started').touch()
while not marker.with_suffix('.release').exists():
    time.sleep(.02)
for arg in sys.argv[3:]:
    if arg.startswith('-Dopenggf.surefire.reports='):
        reports = Path(arg.split('=', 1)[1])
        reports.mkdir(parents=True)
        (reports / 'TEST-probe.xml').write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Probe" name="ran"/></testsuite>')
print('probe output', flush=True)
sys.exit(int(sys.argv[2]))
"""
        launcher = r"""
import sys, subprocess
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, sys.argv[1])
import maven_queue as queue
import maven_resources
maven_resources.snapshot = lambda: (100 * 1024**3, 128, 0)
import run_categories as runner
original = subprocess.Popen
marker, fake, code, category = sys.argv[2:]
def probe(command, *args, **kwargs):
    if command[0] == 'mvn':
        command = [sys.executable, '-u', '-c', fake, marker, code, *command[1:]]
    return original(command, *args, **kwargs)
try:
    with queue.handle_termination(), patch.object(subprocess, 'Popen', probe):
        if category == 'yes':
            runner.ROOT = Path.cwd()
            plan = dict(full=False, tests=['Probe.java'], guards=False, categories=['common'], inventory_count=1)
            with patch.object(runner, 'make_plan', return_value=plan), patch.object(runner, 'preflight'):
                result = runner.main(['--category', 'common', '--run', '--max-minutes', '0.05'])
        else:
            result = queue.main(['-Dmse=off', '-Dprobe=spaces and $literal', 'test'])
    sys.exit(result)
except KeyboardInterrupt:
    sys.exit(130)
"""
        process = subprocess.Popen([sys.executable, '-c', launcher, str(TOOLS), str(marker),
                                    fake_maven, str(exit_code), 'yes' if category else 'no'],
                                   cwd=self.linked, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True)
        self.processes.append(process)
        return process, marker

    def test_maven_wrapper_waits_for_same_slot_preserves_args_cwd_output_and_failure(self):
        holder, first = self.launch('holder')
        self.wait_started(holder, first)
        command, marker = self.launch_command('maven', exit_code=7)
        time.sleep(.3)
        self.assertIsNone(command.poll())
        self.assertFalse(marker.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(command, marker)
        marker.with_suffix('.release').touch()
        output = command.communicate(timeout=5)[0]
        self.assertEqual(7, command.returncode, output)
        self.assertIn('probe output', output)
        self.assertIn('Waiting', output)
        observed = json.loads(marker.with_suffix('.args').read_text())
        self.assertEqual(str(self.linked.resolve()), observed['cwd'])
        self.assertEqual(['-Dmse=off', '-Dprobe=spaces and $literal', 'test'], observed['args'])
        later, last = self.launch('after-failure')
        self.wait_started(later, last)
        last.with_suffix('.release').touch()
        self.assertEqual(0, later.wait(timeout=5))

    def test_category_cli_waits_ignores_old_receipts_and_excludes_wait_from_timeout(self):
        legacy = self.root / '.git/openggf-validation'
        legacy.mkdir()
        (legacy / 'task.json').write_text('old receipt is deliberately not parseable')
        (legacy / 'task.lock').write_text('not a new queue lock')
        holder, first = self.launch('holder')
        self.wait_started(holder, first)
        command, marker = self.launch_command('category', category=True)
        # Longer than the entire category invocation timeout (three seconds).
        time.sleep(3.2)
        self.assertIsNone(command.poll())
        self.assertFalse(marker.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(command, marker)
        marker.with_suffix('.release').touch()
        output = command.communicate(timeout=5)[0]
        self.assertEqual(0, command.returncode, output)
        self.assertIn('Selected checks passed', output)
        self.assertIn('Waiting', output)
        runs = list((self.linked / 'target/category-tests').iterdir())
        self.assertEqual(1, len(runs))
        self.assertEqual('passed', json.loads((runs[0] / 'status.json').read_text())['status'])
        self.assertFalse((self.linked / 'target/category-tests-last-broad.json').exists())
        self.assertEqual('not a new queue lock', (legacy / 'task.lock').read_text())

    @unittest.skipUnless(os.name == 'posix', 'POSIX SIGTERM and inherited descriptor contract')
    def test_terminating_running_wrapper_reaps_child_before_next_slot(self):
        command, marker = self.launch_command('maven')
        self.wait_started(command, marker)
        command.terminate()
        output = command.communicate(timeout=5)[0]
        self.assertEqual(130, command.returncode, output)
        marker.with_suffix('.release').touch()
        self.assertNotIn('probe output', output)
        later, last = self.launch('after-cancel')
        self.wait_started(later, last)
        last.with_suffix('.release').touch()
        self.assertEqual(0, later.wait(timeout=5))

    @unittest.skipUnless(os.name == 'posix', 'POSIX inherited descriptor contract')
    def test_killed_wrapper_keeps_slot_until_inheriting_child_exits(self):
        command, marker = self.launch_command('maven')
        self.wait_started(command, marker)
        command.kill()
        command.wait(timeout=5)
        later, last = self.launch('after-kill')
        time.sleep(.3)
        self.assertIsNone(later.poll())
        self.assertFalse(last.with_suffix('.started').exists())
        marker.with_suffix('.release').touch()
        self.wait_started(later, last)
        last.with_suffix('.release').touch()
        self.assertEqual(0, later.wait(timeout=5))


if __name__ == '__main__':
    unittest.main()
