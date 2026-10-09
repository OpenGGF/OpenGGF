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

    def launch(self, name, root=None, resource_snapshot=(100 * 1024**3, 128, 0),
               estimate=900, aging_seconds=300, realised_rss=0, reservation=None):
        code = '''
import sys, time, json
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from maven_queue import maven_slot, handle_termination
import maven_resources, maven_schedule
maven_schedule.AGING_SECONDS = float(sys.argv[6])
maven_resources.snapshot = lambda: json.loads(sys.argv[4])
if float(sys.argv[7]):
    # Stand-in for a grown Maven tree: one descendant with this RSS.
    maven_resources.tree_usage = lambda pid, proc=None: {(1, 1): (float(sys.argv[7]), 0.0)}
root, marker = map(Path, sys.argv[2:4])
try:
    with handle_termination(), maven_slot(root, estimate=float(sys.argv[5]),
                                         reservation=json.loads(sys.argv[8])):
        marker.with_suffix('.started').touch()
        while not marker.with_suffix('.release').exists():
            time.sleep(.02)
except KeyboardInterrupt:
    sys.exit(130)
'''
        marker = Path(self.temp.name) / name
        process = subprocess.Popen([sys.executable, '-c', code, str(TOOLS),
                                    str(root or self.root), str(marker), json.dumps(resource_snapshot),
                                    str(estimate), str(aging_seconds), str(realised_rss), json.dumps(reservation)],
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

    def wait_queued(self, count):
        deadline = time.monotonic() + 5
        while len(list((self.root / '.git/maven-waiters').glob('*.request'))) < count:
            if time.monotonic() > deadline:
                self.fail('Requests were not published to the scheduler')
            time.sleep(.02)

    def launch_cleanup(self, root=None):
        root = root or self.linked
        run = root / 'target/category-tests/20261007T120000Z-00000000'
        run.mkdir(parents=True)
        (run / 'plan.json').write_text('{}')
        code = '''
import sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from category_artifacts import acknowledge_run
from maven_queue import handle_termination
with handle_termination():
    acknowledge_run(Path(sys.argv[2]), sys.argv[3])
'''
        process = subprocess.Popen([sys.executable, '-c', code, str(TOOLS), str(root), run.name],
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.processes.append(process)
        return process, run

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_acknowledgment_overlaps_another_worktree_job(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        holder, marker = self.launch('holder')
        self.wait_started(holder, marker)
        cleanup, run = self.launch_cleanup()
        output = cleanup.communicate(timeout=3)[0]
        self.assertEqual(0, cleanup.returncode, output)
        self.assertFalse(run.exists())
        self.assertIsNone(holder.poll())
        self.assertEqual([], list((self.root / '.git/maven-waiters').glob('*.request')))
        marker.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_acknowledgment_waits_for_its_own_worktree(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        holder, marker = self.launch('holder', self.linked)
        self.wait_started(holder, marker)
        cleanup, run = self.launch_cleanup()
        time.sleep(.3)
        self.assertIsNone(cleanup.poll())
        self.assertTrue(run.exists())
        marker.with_suffix('.release').touch()
        holder.wait(timeout=5)
        output = cleanup.communicate(timeout=3)[0]
        self.assertEqual(0, cleanup.returncode, output)
        self.assertFalse(run.exists())

    def test_acknowledgment_waits_for_legacy_exclusive_lock(self):
        from maven_queue import _try_lock, _unlock
        with (self.root / '.git/maven-queue.lock').open('a+b') as legacy:
            legacy.write(b'\0')
            legacy.flush()
            _try_lock(legacy)
            try:
                cleanup, run = self.launch_cleanup()
                time.sleep(.3)
                self.assertIsNone(cleanup.poll())
                self.assertTrue(run.exists())
            finally:
                _unlock(legacy)
            output = cleanup.communicate(timeout=3)[0]
            self.assertEqual(0, cleanup.returncode, output)
            self.assertFalse(run.exists())

    @unittest.skipUnless(os.name == 'posix', 'POSIX process termination')
    def test_cancelled_cleanup_releases_its_worktree_lock(self):
        from maven_queue import _try_lock, _unlock, _acquire
        git_dir = Path(subprocess.check_output(['git', 'rev-parse', '--absolute-git-dir'],
                                              cwd=self.linked, text=True).strip())
        with (self.root / '.git/maven-queue.lock').open('a+b') as legacy:
            legacy.write(b'\0')
            legacy.flush()
            _try_lock(legacy)
            try:
                cleanup, run = self.launch_cleanup()
                tree_path = git_dir / 'maven-worktree.lock'
                deadline = time.monotonic() + 3
                while not tree_path.exists():
                    self.assertLess(time.monotonic(), deadline)
                    time.sleep(.02)
                with tree_path.open('a+b') as tree:
                    # Wait until cleanup owns its local lock, blocked on compatibility.
                    while _acquire(tree):
                        _unlock(tree)
                        self.assertLess(time.monotonic(), deadline)
                        time.sleep(.02)
                    cleanup.terminate()
                    cleanup.communicate(timeout=3)
                    self.assertTrue(_acquire(tree))
                    _unlock(tree)
                    self.assertTrue(run.exists())
            finally:
                _unlock(legacy)

    def test_legacy_unregistered_lock_and_scheduler_remain_mutually_exclusive(self):
        from maven_queue import _try_lock, _unlock, _acquire
        with (self.root / '.git/maven-queue.lock').open('a+b') as legacy:
            legacy.write(b'\0')
            legacy.flush()
            _try_lock(legacy)
            try:
                process, marker = self.launch('new-client', self.linked, estimate=30)
                self.wait_queued(1)
                self.assertFalse(marker.with_suffix('.started').exists())
            finally:
                _unlock(legacy)
            self.wait_started(process, marker)
            self.assertFalse(_acquire(legacy))
            marker.with_suffix('.release').touch()
            process.wait(timeout=5)
            self.assertTrue(_acquire(legacy))
            _unlock(legacy)

    def test_short_run_overtakes_queued_full_suite(self):
        holder, first = self.launch('holder')
        self.wait_started(holder, first)
        large, full = self.launch('full', self.linked, estimate=900)
        self.wait_queued(1)
        small, focused = self.launch('focused', estimate=30)
        self.wait_queued(2)
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(small, focused)
        self.assertFalse(full.with_suffix('.started').exists())
        focused.with_suffix('.release').touch()
        small.wait(timeout=5)
        self.wait_started(large, full)
        full.with_suffix('.release').touch()
        large.wait(timeout=5)
        self.assertEqual([], list((self.root / '.git/maven-waiters').glob('*.request')))

    def test_aged_large_run_wins_before_new_short_run(self):
        holder, first = self.launch('holder', aging_seconds=.3)
        self.wait_started(holder, first)
        large, full = self.launch('full', self.linked, estimate=900, aging_seconds=.3)
        self.wait_queued(1)
        time.sleep(.4)
        small, focused = self.launch('focused', estimate=30, aging_seconds=.3)
        self.wait_queued(2)
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(large, full)
        self.assertFalse(focused.with_suffix('.started').exists())
        full.with_suffix('.release').touch()
        large.wait(timeout=5)
        self.wait_started(small, focused)
        focused.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_aged_busy_worktree_allows_unrelated_backfill(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        other = Path(self.temp.name) / 'other'
        self.git('worktree', 'add', '--detach', '-q', str(other))
        holder, first = self.launch('holder', aging_seconds=.5)
        self.wait_started(holder, first)
        blocked, waiting = self.launch('blocked', estimate=30, aging_seconds=.5)
        self.wait_queued(1)
        backfill, second = self.launch('backfill', self.linked, estimate=60, aging_seconds=.5)
        self.wait_started(backfill, second)
        self.assertFalse(waiting.with_suffix('.started').exists())
        time.sleep(.6)
        second.with_suffix('.release').touch()
        backfill.wait(timeout=5)
        later, third = self.launch('later', other, estimate=30, aging_seconds=.5)
        self.wait_started(later, third)
        self.assertFalse(waiting.with_suffix('.started').exists())
        self.assertIsNone(holder.poll())  # Aging never preempts running work.
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(blocked, waiting)
        self.wait_started(later, third)
        waiting.with_suffix('.release').touch()
        third.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_two_lean_runs_fit_where_two_normal_runs_cannot(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        snapshot = (10 * 1024**3, 32, 0)
        lean = (4 * 1024**3, 4)
        holder, first = self.launch('lean-one', resource_snapshot=snapshot, reservation=lean)
        self.wait_started(holder, first)
        normal, large = self.launch('normal', self.linked, resource_snapshot=snapshot)
        self.wait_queued(1)
        small, second = self.launch('lean-two', self.linked, resource_snapshot=snapshot, reservation=lean)
        self.wait_started(small, second)
        self.assertFalse(large.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        second.with_suffix('.release').touch()
        holder.wait(timeout=5)
        small.wait(timeout=5)
        self.wait_started(normal, large)
        large.with_suffix('.release').touch()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_aged_resource_blocked_normal_run_still_drains_lean_backfill(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        snapshot = (10 * 1024**3, 32, 0)
        lean = (4 * 1024**3, 4)
        other = Path(self.temp.name) / 'other'
        self.git('worktree', 'add', '--detach', '-q', str(other))
        holder, first = self.launch('lean-holder', resource_snapshot=snapshot, reservation=lean)
        self.wait_started(holder, first)
        normal, large = self.launch('aged-normal', self.linked, resource_snapshot=snapshot, aging_seconds=.3)
        self.wait_queued(1)
        time.sleep(.4)
        small, second = self.launch('lean-backfill', other, resource_snapshot=snapshot,
                                    reservation=lean, aging_seconds=.3)
        self.wait_queued(2)
        time.sleep(.3)
        self.assertFalse(second.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(normal, large)
        self.assertFalse(second.with_suffix('.started').exists())
        large.with_suffix('.release').touch()
        normal.wait(timeout=5)
        self.wait_started(small, second)
        second.with_suffix('.release').touch()

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
    def test_realised_usage_credit_admits_a_run_that_double_counting_blocked(self):
        # 15 GiB free: one 7 GiB reservation plus 2 GiB headroom fits; two do not,
        # unless the running job's own 3 GiB is credited instead of counted twice.
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        snapshot = (15 * 1024**3, 128, 0)
        for realised, admitted in ((0, False), (3 * 1024**3, True)):
            with self.subTest(realised=realised):
                holder, first = self.launch(f'holder-{realised}', resource_snapshot=snapshot,
                                            realised_rss=realised)
                self.wait_started(holder, first)
                waiter, second = self.launch(f'second-{realised}', self.linked, resource_snapshot=snapshot)
                if admitted:
                    self.wait_started(waiter, second)
                    second.with_suffix('.release').touch()
                    waiter.wait(timeout=5)
                else:
                    time.sleep(.5)
                    self.assertFalse(second.with_suffix('.started').exists())
                first.with_suffix('.release').touch()
                holder.wait(timeout=5)
                if not admitted:
                    self.wait_started(waiter, second)
                    second.with_suffix('.release').touch()
                    waiter.wait(timeout=5)
        self.assertEqual([], list((self.root / '.git/maven-running').glob('*.lease')))

    def test_finished_and_cancelled_requests_leave_one_telemetry_line_each(self):
        from maven_running import summarise
        holder, first = self.launch('holder', estimate=30)
        self.wait_started(holder, first)
        waiter, second = self.launch('cancelled', self.linked)
        self.wait_queued(1)
        waiter.terminate()
        waiter.wait(timeout=5)
        time.sleep(.3)  # Hold long enough to register at 0.1-second resolution.
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        log = self.root / '.git/maven-queue-log.jsonl'
        deadline = time.monotonic() + 5
        while len(log.read_text().splitlines()) < 2 and time.monotonic() < deadline:
            time.sleep(.02)
        rows = sorted((json.loads(line) for line in log.read_text().splitlines()), key=lambda r: r['outcome'])
        self.assertEqual(['cancelled-waiting', 'completed'], [row['outcome'] for row in rows])
        self.assertEqual(0, rows[0]['holdSeconds'])
        self.assertGreater(rows[1]['holdSeconds'], 0)
        self.assertEqual({'maven'}, {row['kind'] for row in rows})
        self.assertNotIn('args', rows[1])
        self.assertIn('maven', summarise(self.root / '.git'))
        self.assertEqual([], list((self.root / '.git/maven-running').glob('*.lease')))

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
    def test_auto_ceiling_blocks_a_fourth_distinct_worktree(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        trees = []
        for name in ('other', 'another'):
            trees.append(Path(self.temp.name) / name)
            self.git('worktree', 'add', '--detach', '-q', str(trees[-1]))
        holder, first = self.launch('first')
        self.wait_started(holder, first)
        second_process, second = self.launch('second', self.linked)
        self.wait_started(second_process, second)
        third_process, third = self.launch('third', trees[0])
        self.wait_started(third_process, third)
        fourth_process, fourth = self.launch('fourth', trees[1])
        time.sleep(.3)
        self.assertFalse(fourth.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(fourth_process, fourth)
        for marker in (second, third, fourth):
            marker.with_suffix('.release').touch()

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

    def launch_command(self, name, category=False, exit_code=0, lean=False, args=None, root=None):
        """Run the real CLIs/runner, replacing only the Maven executable with a probe."""
        marker = Path(self.temp.name) / name
        self.addCleanup(marker.with_suffix('.release').touch)
        fake_maven = r"""
import json, os, sys, time
from pathlib import Path
marker = Path(sys.argv[1])
marker.with_suffix('.args').write_text(json.dumps({'cwd': str(Path.cwd()), 'args': sys.argv[3:],
                                                  'mavenOpts': os.environ.get('MAVEN_OPTS')}))
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
marker, fake, code, category, lean, supplied_args = sys.argv[2:]
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
            import json
            args = json.loads(supplied_args) if supplied_args != 'null' else ['-Dmse=off', '-Dprobe=spaces and $literal', 'test']
            if lean == 'yes':
                args = ['--lean', '-Dtest=TestProbe', *args]
            result = queue.main(args)
    sys.exit(result)
except KeyboardInterrupt:
    sys.exit(130)
"""
        process = subprocess.Popen([sys.executable, '-c', launcher, str(TOOLS), str(marker),
                                    fake_maven, str(exit_code), 'yes' if category else 'no', 'yes' if lean else 'no',
                                    json.dumps(args)],
                                   cwd=root or self.linked, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True)
        self.processes.append(process)
        return process, marker

    def test_build_cli_bypasses_another_worktrees_serial_test_queue(self):
        holder, first = self.launch('test-holder')
        self.wait_started(holder, first)
        args = ['-Dmse=off', '-Dprobe=spaces and $literal', 'clean', 'package', '-DskipTests']
        build, marker = self.launch_command('build', args=args, exit_code=7)
        self.wait_started(build, marker)
        self.assertIsNone(holder.poll())
        self.assertEqual([], list((self.root / '.git/maven-waiters').glob('*.request')))
        observed = json.loads(marker.with_suffix('.args').read_text())
        self.assertEqual(args, observed['args'])
        self.assertEqual(str(self.linked.resolve()), observed['cwd'])
        marker.with_suffix('.release').touch()
        output = build.communicate(timeout=5)[0]
        self.assertEqual(7, build.returncode, output)
        self.assertIn('probe output', output)
        self.assertNotIn('Maven slot acquired', output)
        first.with_suffix('.release').touch()

    def test_compile_waits_for_its_own_worktree_test(self):
        holder, first = self.launch('same-tree-test', self.linked)
        self.wait_started(holder, first)
        build, marker = self.launch_command('compile', args=['compile'])
        time.sleep(.3)
        self.assertFalse(marker.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(build, marker)
        marker.with_suffix('.release').touch()

    def test_test_waits_for_its_own_worktree_build(self):
        build, marker = self.launch_command('compile-holder', args=['test-compile'])
        self.wait_started(build, marker)
        test, last = self.launch('same-tree-test', self.linked)
        self.wait_queued(1)
        self.assertFalse(last.with_suffix('.started').exists())
        marker.with_suffix('.release').touch()
        build.wait(timeout=5)
        self.wait_started(test, last)
        last.with_suffix('.release').touch()

    def test_two_builds_in_one_worktree_serialize(self):
        build, first = self.launch_command('first-build', args=['compile'])
        self.wait_started(build, first)
        later, last = self.launch_command('second-build', args=['package', '-Dmaven.test.skip=true'])
        time.sleep(.3)
        self.assertFalse(last.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        build.wait(timeout=5)
        self.wait_started(later, last)
        last.with_suffix('.release').touch()

    @unittest.skipUnless(os.name == 'posix', 'POSIX SIGTERM and inherited descriptor contract')
    def test_build_cancellation_and_killed_parent_preserve_worktree_safety(self):
        for killed in (False, True):
            with self.subTest(killed=killed):
                build, first = self.launch_command(f'build-{killed}', args=['compile'])
                self.wait_started(build, first)
                build.kill() if killed else build.terminate()
                build.wait(timeout=5)
                later, last = self.launch_command(f'after-{killed}', args=['compile'])
                if killed:
                    time.sleep(.3)
                    self.assertFalse(last.with_suffix('.started').exists())
                    first.with_suffix('.release').touch()
                self.wait_started(later, last)
                last.with_suffix('.release').touch()
                later.wait(timeout=5)

    def test_lean_cli_passes_bounded_heaps_and_records_its_own_budget(self):
        process, marker = self.launch_command('lean-cli', lean=True)
        self.wait_started(process, marker)
        invoked = json.loads(marker.with_suffix('.args').read_text())
        self.assertEqual('-Xmx1g', invoked['mavenOpts'])
        self.assertIn('-Dprobe=spaces and $literal', invoked['args'])
        self.assertIn('-Dsurefire.argLine=${test.cds.argLine} ${mockito.agent.argLine} -Xmx1g', invoked['args'])
        self.assertIn('-Dsurefire.forkCount=1', invoked['args'])
        self.assertIn('-Dsurefire.reuseForks=true', invoked['args'])
        records = list((self.root / '.git/maven-running').glob('*.lease'))
        self.assertEqual(1, len(records))
        self.assertEqual(4 * 1024**3, json.loads(records[0].read_bytes()[1:])['memoryBytes'])
        marker.with_suffix('.release').touch()
        output = process.communicate(timeout=5)[0]
        self.assertEqual(0, process.returncode, output)
        self.assertIn('budget 4 GiB/4 cores', output)
        row = json.loads((self.root / '.git/maven-queue-log.jsonl').read_text())
        self.assertEqual('focused-lean', row['kind'])

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
