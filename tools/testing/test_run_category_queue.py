"""Exercise the shared Maven queue with real competing processes, without Java."""
import os
import json
import errno
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import subprocess
import sys
import tempfile
import shutil
import time
from types import SimpleNamespace
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
               estimate=900, aging_seconds=300, realised_rss=0, reservation=None, clock=None):
        code = '''
import sys, time, json
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from maven_queue import maven_slot, handle_termination
import maven_resources, maven_schedule
if sys.argv[9]:
    import maven_queue
    from types import SimpleNamespace
    maven_queue.time = SimpleNamespace(
        monotonic=lambda: float(Path(sys.argv[9]).read_text()), sleep=time.sleep)
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
                                    str(estimate), str(aging_seconds), str(realised_rss), json.dumps(reservation),
                                    str(clock) if clock is not None else ''],
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

    def test_retained_worktree_lock_releases_shared_admission_between_batches(self):
        code = '''
import sys, time
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from maven_queue import maven_slot, worktree_build_slot
import maven_schedule
maven_schedule.AGING_SECONDS = .1
root, marker = map(Path, sys.argv[2:4])
with worktree_build_slot(root) as tree_fd:
    for number in (1, 2):
        with maven_slot(root, estimate=30, tree_fd=tree_fd):
            marker.with_name(marker.name + f'-{number}').with_suffix('.started').touch()
            while not marker.with_suffix(f'.{number}.release').exists():
                time.sleep(.02)
        marker.with_suffix(f'.{number}.released').touch()
        if number == 1:
            while not marker.with_suffix('.next').exists():
                time.sleep(.02)
'''
        marker = Path(self.temp.name) / 'batched'
        process = subprocess.Popen([sys.executable, '-c', code, str(TOOLS), str(self.root), str(marker)],
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.processes.append(process)
        self.wait_started(process, marker.with_name(marker.name + '-1'))
        same, same_marker = self.launch('same-tree', estimate=30, aging_seconds=.1)
        self.wait_queued(1)
        other, other_marker = self.launch('other-tree', self.linked, estimate=30)
        self.wait_queued(2)
        time.sleep(.2)  # Aged same-tree waiters cannot prevent their holder resuming.
        marker.with_suffix('.1.release').touch()
        self.wait_started(other, other_marker)
        self.assertFalse(same_marker.with_suffix('.started').exists())
        marker.with_suffix('.next').touch()
        self.wait_queued(2)
        self.assertFalse(marker.with_name(marker.name + '-2').with_suffix('.started').exists())
        other_marker.with_suffix('.release').touch()
        other.wait(timeout=5)
        self.wait_started(process, marker.with_name(marker.name + '-2'))
        self.assertFalse(same_marker.with_suffix('.started').exists())
        marker.with_suffix('.2.release').touch()
        self.assertEqual(0, process.wait(timeout=5), process.communicate()[0])
        self.wait_started(same, same_marker)
        same_marker.with_suffix('.release').touch()
        self.assertEqual(0, same.wait(timeout=5))

    def test_retained_descriptor_must_belong_to_the_requested_worktree(self):
        from maven_queue import maven_slot
        with (self.root / 'unrelated-file').open('w+b') as unrelated:
            with self.assertRaisesRegex(ValueError, 'worktree'):
                with maven_slot(self.root, tree_fd=unrelated.fileno()):
                    self.fail('foreign lease accepted')
        for descriptor in (True, -1, '1'):
            with self.subTest(descriptor=descriptor), self.assertRaises(ValueError):
                with maven_slot(self.root, tree_fd=descriptor):
                    self.fail('invalid lease accepted')

    def test_retained_continuation_uses_credit_against_a_real_competing_process(self):
        self.assert_retained_continuation_priority()

    @unittest.skipUnless(sys.platform == 'linux', 'Linux resource admission')
    def test_resource_aware_retained_continuation_preserves_priority_against_foreign_probe(self):
        os.environ['OPENGGF_MAVEN_QUEUE'] = 'auto'
        self.git('config', 'openggf.mavenMaxRuns', '1')
        self.assert_retained_continuation_priority()

    def assert_retained_continuation_priority(self):
        import maven_queue as queue
        code = '''
import sys, time
from pathlib import Path
from types import SimpleNamespace
sys.path.insert(0, sys.argv[1])
import maven_queue as queue
import maven_resources
from maven_schedule import ServiceCredit
maven_resources.snapshot = lambda: (100 * 1024**3, 128, 0)
root, marker, clock = map(Path, sys.argv[2:5])
queue.time = SimpleNamespace(monotonic=lambda: float(clock.read_text()), sleep=time.sleep)
credit = ServiceCredit()
with queue.worktree_build_slot(root) as tree_fd:
    for number in (1, 2):
        with queue.maven_slot(root, estimate=45, tree_fd=tree_fd,
                              service_credit=credit, kind='category'):
            marker.with_name(marker.name + f'-{number}').with_suffix('.started').touch()
            while not marker.with_suffix(f'.{number}.release').exists():
                time.sleep(.02)
        marker.with_suffix('.credit').write_text(str(credit.seconds))
        if number == 1:
            marker.with_name(marker.name + '-between').with_suffix('.started').touch()
            while not marker.with_suffix('.next').exists():
                time.sleep(.02)
'''
        clock = Path(self.temp.name) / 'clock'
        def advance(now):
            replacement = clock.with_suffix('.next')
            replacement.write_text(str(now))
            replacement.replace(clock)
        advance(1000)
        marker = Path(self.temp.name) / 'credited-batches'
        with (self.root / '.git/maven-queue.lock').open('a+b') as legacy:
            self.assertTrue(queue._acquire(legacy))
            process = subprocess.Popen([sys.executable, '-c', code, str(TOOLS),
                                        str(self.root), str(marker), str(clock)],
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            self.processes.append(process)
            self.wait_queued(1)
            advance(4600)
            queue._unlock(legacy)
            self.wait_started(process, marker.with_name(marker.name + '-1'))
            advance(4640)
            marker.with_suffix('.1.release').touch()
            self.wait_started(process, marker.with_name(marker.name + '-between'))
            self.assertEqual(260, float(marker.with_suffix('.credit').read_text()))
            with (self.root / '.git/maven-worktree.lock').open('a+b') as competing:
                self.assertFalse(queue._acquire(competing))
            self.assertTrue(queue._acquire(legacy))
            focused, focused_marker = self.launch('focused', self.linked, estimate=30, clock=clock)
            self.wait_queued(1)
            marker.with_suffix('.next').touch()
            self.wait_queued(2)
            advance(4680)
            queue._unlock(legacy)
            self.wait_started(process, marker.with_name(marker.name + '-2'))
            self.assertFalse(focused_marker.with_suffix('.started').exists())
            advance(4720)
            marker.with_suffix('.2.release').touch()
            self.assertEqual(0, process.wait(timeout=5), process.communicate()[0])
            self.assertEqual(260, float(marker.with_suffix('.credit').read_text()))
            self.wait_started(focused, focused_marker)
            advance(4750)
            focused_marker.with_suffix('.release').touch()
            self.assertEqual(0, focused.wait(timeout=5), focused.communicate()[0])
        rows = [json.loads(line) for line in
                (self.root / '.git/maven-queue-log.jsonl').read_text().splitlines()]
        self.assertEqual([(3600, 40), (40, 40)],
                         [(row['waitSeconds'], row['holdSeconds']) for row in rows
                          if row['kind'] == 'category'])
        expected_mode = 'resource-aware' if os.environ['OPENGGF_MAVEN_QUEUE'] == 'auto' else 'serial'
        self.assertEqual({expected_mode}, {row['mode'] for row in rows})
        self.assertEqual([], list((self.root / '.git/maven-waiters').glob('*.request')))

    def test_service_credit_preserves_actual_wait_telemetry_and_charges_interrupted_service(self):
        import maven_queue as queue
        from maven_schedule import ServiceCredit
        common = self.root / '.git'
        credit = ServiceCredit(250)
        clock = [1000]
        output = StringIO()
        with (common / 'maven-queue.lock').open('a+b') as legacy:
            self.assertTrue(queue._acquire(legacy))
            def release_after_wait(_delay):
                records = list((common / 'maven-waiters').glob('*.request'))
                self.assertEqual(1, len(records))
                record = json.loads(records[0].read_bytes()[1:])
                self.assertEqual(750, record['enqueued'])
                clock[0] = 1010
                queue._unlock(legacy)
            with patch.object(queue, 'time', SimpleNamespace(
                    monotonic=lambda: clock[0], sleep=release_after_wait)), redirect_stdout(output):
                with self.assertRaisesRegex(RuntimeError, 'interrupted batch'):
                    with queue.maven_slot(self.root, service_credit=credit):
                        clock[0] = 1050
                        raise RuntimeError('interrupted batch')
        self.assertEqual(220, credit.seconds)
        row, = map(json.loads, (common / 'maven-queue-log.jsonl').read_text().splitlines())
        self.assertEqual('interrupted', row['outcome'])
        self.assertEqual(10, row['waitSeconds'])
        self.assertEqual(40, row['holdSeconds'])
        self.assertIn('Waiting for Maven slot (0s;', output.getvalue())
        self.assertIn('waited 10s)', output.getvalue())
        self.assertEqual([], list((common / 'maven-waiters').glob('*.request')))
        self.assertEqual([], list((common / 'maven-running').glob('*.lease')))
        with queue.maven_slot(self.root):
            pass

    def test_cancelled_wait_does_not_earn_credit_or_release_retained_tree(self):
        import maven_queue as queue
        from maven_schedule import ServiceCredit
        common = self.root / '.git'
        credit = ServiceCredit(250)
        clock = [1000]
        def cancel_after_wait(_delay):
            clock[0] = 4600
            raise KeyboardInterrupt
        with queue.worktree_build_slot(self.root) as tree_fd:
            with (common / 'maven-queue.lock').open('a+b') as legacy:
                self.assertTrue(queue._acquire(legacy))
                with patch.object(queue, 'time', SimpleNamespace(
                        monotonic=lambda: clock[0], sleep=cancel_after_wait)):
                    with self.assertRaises(KeyboardInterrupt):
                        with queue.maven_slot(self.root, tree_fd=tree_fd, service_credit=credit):
                            self.fail('blocked request admitted')
            self.assertEqual(250, credit.seconds)
            with (common / 'maven-worktree.lock').open('a+b') as competing:
                self.assertFalse(queue._acquire(competing))
            self.assertEqual([], list((common / 'maven-waiters').glob('*.request')))
        row, = map(json.loads, (common / 'maven-queue-log.jsonl').read_text().splitlines())
        self.assertEqual('cancelled-waiting', row['outcome'])
        self.assertEqual(3600, row['waitSeconds'])
        self.assertEqual(0, row['holdSeconds'])
        with queue.maven_slot(self.root):
            pass

    def test_vanished_foreign_metadata_during_probe_does_not_block_aged_serial_waiter(self):
        import maven_queue as queue
        from maven_schedule import WaitingRequest, AGING_SECONDS
        common = self.root / '.git'
        metadata = Path(subprocess.check_output(
            ['git', 'rev-parse', '--absolute-git-dir'], cwd=self.linked, text=True).strip())
        foreign_lock = metadata / 'maven-worktree.lock'
        foreign = WaitingRequest(common, dict(tree=str(foreign_lock), auto=False,
                estimate=30, enqueued=time.monotonic() - AGING_SECONDS - 1), queue._acquire)
        original = queue._open_lock
        probed = []
        def remove_metadata_during_open(stack, path):
            if path == foreign_lock:
                probed.append(path)
                shutil.rmtree(metadata)
            return original(stack, path)
        try:
            with patch.object(queue, '_open_lock', side_effect=remove_metadata_during_open):
                with queue.maven_slot(self.root) as leases:
                    self.assertTrue(leases)
                    # Admission still owns a real worktree lease; it did not
                    # merely ignore the vanished request and bypass locking.
                    with (common / 'maven-worktree.lock').open('a+b') as competing:
                        self.assertFalse(queue._acquire(competing))
            self.assertEqual([foreign_lock], probed)
            self.assertFalse(metadata.exists(), 'never recreate another worktree metadata directory')
            self.assertTrue(foreign.path.exists(), 'the foreign live waiting lease belongs to its owner')
        finally:
            foreign.remove()

    def test_own_metadata_disappearing_during_probe_remains_fatal(self):
        import maven_queue as queue
        import maven_schedule as schedule
        metadata = Path(subprocess.check_output(
            ['git', 'rev-parse', '--absolute-git-dir'], cwd=self.linked, text=True).strip())
        own_lock = metadata / 'maven-worktree.lock'
        original = queue._open_lock
        def remove_own_metadata(stack, path):
            if path == own_lock:
                shutil.rmtree(metadata)
            return original(stack, path)
        def probe_once(requests, now, fits):
            self.assertEqual(1, len(requests))
            fits(requests[0])
            self.fail('the current worktree cannot be skipped when its own lock disappears')
        with patch.object(queue, '_open_lock', side_effect=remove_own_metadata), \
                patch.object(schedule, 'choose', side_effect=probe_once):
            with self.assertRaises(FileNotFoundError) as failure:
                with queue.maven_slot(self.linked):
                    self.fail('own missing worktree admitted')
        self.assertEqual(str(own_lock), failure.exception.filename)

    def test_foreign_lock_errors_with_existing_metadata_remain_fatal(self):
        import maven_queue as queue
        from maven_schedule import WaitingRequest, AGING_SECONDS
        common = self.root / '.git'
        metadata = Path(subprocess.check_output(
            ['git', 'rev-parse', '--absolute-git-dir'], cwd=self.linked, text=True).strip())
        foreign_lock = metadata / 'maven-worktree.lock'
        foreign = WaitingRequest(common, dict(tree=str(foreign_lock), auto=False,
                estimate=30, enqueued=time.monotonic() - AGING_SECONDS - 1), queue._acquire)
        original = queue._open_lock
        try:
            for error in (FileNotFoundError(errno.ENOENT, 'dangling lock target', str(foreign_lock)),
                          PermissionError(errno.EACCES, 'permission denied', str(foreign_lock)),
                          OSError(errno.EIO, 'I/O error', str(foreign_lock))):
                def failing_open(stack, path):
                    if path == foreign_lock:
                        raise error
                    return original(stack, path)
                with self.subTest(error=type(error).__name__), \
                        patch.object(queue, '_open_lock', side_effect=failing_open):
                    with self.assertRaises(type(error)) as failure:
                        with queue.maven_slot(self.root):
                            self.fail('an existing foreign worktree error was ignored')
                    self.assertIs(error, failure.exception)
                    self.assertTrue(metadata.is_dir())
        finally:
            foreign.remove()

    def test_foreign_metadata_inspection_permission_error_is_not_treated_as_vanished(self):
        import maven_queue as queue
        from maven_schedule import WaitingRequest, AGING_SECONDS
        common = self.root / '.git'
        metadata = Path(subprocess.check_output(
            ['git', 'rev-parse', '--absolute-git-dir'], cwd=self.linked, text=True).strip())
        foreign_lock = metadata / 'maven-worktree.lock'
        foreign = WaitingRequest(common, dict(tree=str(foreign_lock), auto=False,
                estimate=30, enqueued=time.monotonic() - AGING_SECONDS - 1), queue._acquire)
        original_open, original_stat = queue._open_lock, Path.stat
        denied = PermissionError(errno.EACCES, 'metadata inspection denied', str(metadata))
        def failing_open(stack, path):
            if path == foreign_lock:
                raise FileNotFoundError(errno.ENOENT, 'lock unavailable', str(path))
            return original_open(stack, path)
        def failing_stat(path, *args, **kwargs):
            if path == metadata:
                raise denied
            return original_stat(path, *args, **kwargs)
        try:
            with patch.object(queue, '_open_lock', side_effect=failing_open), \
                    patch.object(Path, 'stat', failing_stat):
                with self.assertRaises(PermissionError) as failure:
                    with queue.maven_slot(self.root):
                        self.fail('metadata inspection failure was ignored')
            self.assertIs(denied, failure.exception)
        finally:
            foreign.remove()

    def test_other_missing_file_is_fatal_even_if_foreign_metadata_also_disappeared(self):
        import maven_queue as queue
        from maven_schedule import WaitingRequest, AGING_SECONDS
        common = self.root / '.git'
        metadata = Path(subprocess.check_output(
            ['git', 'rev-parse', '--absolute-git-dir'], cwd=self.linked, text=True).strip())
        foreign_lock = metadata / 'maven-worktree.lock'
        foreign = WaitingRequest(common, dict(tree=str(foreign_lock), auto=False,
                estimate=30, enqueued=time.monotonic() - AGING_SECONDS - 1), queue._acquire)
        original = queue._open_lock
        missing = FileNotFoundError(errno.ENOENT, 'shared file unavailable', str(common / 'maven-queue.lock'))
        def failing_open(stack, path):
            if path == foreign_lock:
                shutil.rmtree(metadata)
                raise missing
            return original(stack, path)
        try:
            with patch.object(queue, '_open_lock', side_effect=failing_open):
                with self.assertRaises(FileNotFoundError) as failure:
                    with queue.maven_slot(self.root):
                        self.fail('a shared-file failure was ignored')
            self.assertIs(missing, failure.exception)
        finally:
            foreign.remove()

    @unittest.skipUnless(os.name == 'posix', 'POSIX open-file-description leases')
    def test_unowned_descriptor_cannot_bypass_a_foreign_worktree_holder(self):
        from contextlib import ExitStack
        from maven_queue import _execution_leases
        holder, marker = self.launch('holder')
        self.wait_started(holder, marker)
        lock = self.root / '.git/maven-worktree.lock'
        request = dict(tree=str(lock), auto=False)
        with lock.open('a+b') as unowned, ExitStack() as stack:
            import maven_schedule
            self.assertIs(maven_schedule.WORKTREE_BUSY,
                          _execution_leases(stack, self.root / '.git', request, {}, tree_fd=unowned.fileno()))
        self.assertIsNone(holder.poll())
        marker.with_suffix('.release').touch()

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
if sys.argv[3] == 'yes' and 'test-compile' in sys.argv[4:]:
    marker.with_suffix('.prepared').touch()
    sys.exit(0)
marker.with_suffix('.args').write_text(json.dumps({'cwd': str(Path.cwd()), 'args': sys.argv[4:],
                                                  'mavenOpts': os.environ.get('MAVEN_OPTS')}))
marker.with_suffix('.started').touch()
while not marker.with_suffix('.release').exists():
    time.sleep(.02)
for arg in sys.argv[4:]:
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
        command = [sys.executable, '-u', '-c', fake, marker, code, category, *command[1:]]
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

    def test_mod_launcher_build_bypasses_another_worktrees_test_queue(self):
        holder, first = self.launch('test-holder')
        self.wait_started(holder, first)
        args = ['-B', '-q', '-Dmse=off', '-DskipTests', 'compile',
                'dependency:build-classpath', '-Dmdep.outputFile=target/examples-classpath.txt']
        build, marker = self.launch_command('mod-build', args=args)
        self.wait_started(build, marker)
        self.assertIsNone(holder.poll())
        self.assertEqual([], list((self.root / '.git/maven-waiters').glob('*.request')))
        observed = json.loads(marker.with_suffix('.args').read_text())
        self.assertEqual(args, observed['args'])
        self.assertEqual(str(self.linked.resolve()), observed['cwd'])
        marker.with_suffix('.release').touch()
        output = build.communicate(timeout=5)[0]
        self.assertEqual(0, build.returncode, output)
        self.assertNotIn('Maven slot acquired', output)
        first.with_suffix('.release').touch()

    def test_mod_launcher_build_waits_for_its_own_worktree_test(self):
        holder, first = self.launch('same-tree-test', self.linked)
        self.wait_started(holder, first)
        build, marker = self.launch_command('mod-build', args=[
            '-DskipTests', 'compile', 'dependency:build-classpath'])
        time.sleep(.3)
        self.assertFalse(marker.with_suffix('.started').exists())
        first.with_suffix('.release').touch()
        holder.wait(timeout=5)
        self.wait_started(build, marker)
        marker.with_suffix('.release').touch()

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
        self.assertIn('-Dsurefire.argLine=--enable-native-access=ALL-UNNAMED ${test.cds.argLine} ${mockito.agent.argLine} -Xmx1g', invoked['args'])
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
        self.assertTrue(marker.with_suffix('.prepared').exists())
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
