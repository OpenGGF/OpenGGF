"""The queue dashboard observes leases without acquiring or changing them."""
import contextlib
import io
import json
import os
from pathlib import Path
import socket
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import maven_queue
from maven_running import RunningLease
from maven_schedule import WaitingRequest
import queue_dashboard as dashboard


class DashboardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.common = self.root / '.git'
        self.common.mkdir()
        self.now, self.monotonic = time.time(), time.monotonic()

    def collect(self, **kwargs):
        return dashboard.collect_snapshot(self.root, common=self.common, now=self.now,
            monotonic=self.monotonic, resources=(12 * 1024 ** 3, 16, 2.5), **kwargs)

    def running(self, **display):
        lease = RunningLease(self.common, (7 * 1024 ** 3, 8), maven_queue._acquire, display=display)
        self.addCleanup(lease.remove)
        return lease

    def waiting(self, **extra):
        record = dict(tree=str(self.common / 'maven-worktree.lock'), auto=True,
                      estimate=30, enqueued=self.monotonic - 10)
        record.update(extra)
        request = WaitingRequest(self.common, record, maven_queue._acquire)
        self.addCleanup(request.remove)
        return request

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_live_records_have_actual_timings_and_reading_changes_no_files(self):
        self.now = time.time() + .1
        lease = self.running(worktree='feature-a', kind='focused', mode='resource-aware',
            submitted=self.monotonic - 80, admitted=self.monotonic - 20,
            toolingRevision=dashboard.TOOLING_REVISION)
        lease.publish(1024 ** 3, 1.25)
        self.waiting(worktree='feature-b', kind='batch-2', submitted=self.monotonic - 5,
                     enqueued=self.monotonic - 305, retained_tree=True,
                     reservation=[4 * 1024 ** 3, 4], toolingRevision=dashboard.TOOLING_REVISION)
        before = {p: p.read_bytes() for p in self.common.rglob('*') if p.is_file()}
        view = self.collect()
        self.assertEqual('verified', view['liveness'])
        running, waiting = view['running'][0], view['waiting'][0]
        self.assertEqual(('feature-a', 60, 20, 1024 ** 3, 1.25),
            (running['worktree'], running['waitSeconds'], running['runSeconds'], running['rssBytes'], running['cpuCores']))
        self.assertEqual((5, 305, True, 1),
            (waiting['waitSeconds'], waiting['schedulingAgeSeconds'], waiting['aged'], waiting['position']))
        self.assertEqual(dict(revision=dashboard.TOOLING_REVISION, status='current'), running['tooling'])
        self.assertEqual(running['tooling'], waiting['tooling'])
        self.assertEqual((4 * 1024 ** 3, 4),
                         (waiting['budgetMemoryBytes'], waiting['budgetCpuCores']))
        self.assertEqual(before, {p: p.read_bytes() for p in self.common.rglob('*') if p.is_file()})

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_legacy_live_rows_keep_unknown_timings_and_find_owner_cwd(self):
        self.running()
        self.waiting()
        view = self.collect()
        self.assertEqual(Path.cwd().name, view['running'][0]['worktree'])
        self.assertEqual(os.getpid(), view['running'][0]['pid'])
        self.assertIsNone(view['running'][0]['runSeconds'])
        self.assertIsNone(view['running'][0]['waitSeconds'])
        self.assertIsNone(view['waiting'][0]['waitSeconds'])
        self.assertEqual(10, view['waiting'][0]['schedulingAgeSeconds'])
        self.assertEqual(dict(revision=None, status='unknown'), view['waiting'][0]['tooling'])
        self.assertEqual(view['waiting'][0]['tooling'], view['running'][0]['tooling'])
        self.assertIsNone(view['waiting'][0]['budgetMemoryBytes'])
        self.assertIsNone(view['waiting'][0]['budgetCpuCores'])
        self.assertTrue(any('Older clients' in item for item in view['warnings']))
        self.assertTrue(any('usable queue tooling revision' in item for item in view['warnings']))

    def test_tooling_metadata_distinguishes_missing_older_and_future_revisions(self):
        with patch.object(dashboard, 'TOOLING_REVISION', 3):
            for value in (None, True, '3', 0, -1, 2.5, 2**63, [], {}):
                with self.subTest(value=value):
                    self.assertEqual(dict(revision=None, status='unknown'),
                                     dashboard._tooling(dict(toolingRevision=value)))
            for value, status in ((1, 'older'), (3, 'current'), (4, 'newer')):
                self.assertEqual(dict(revision=value, status=status),
                                 dashboard._tooling(dict(toolingRevision=value)))

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_mixed_tooling_is_advisory_and_keeps_every_live_waiter(self):
        with patch.object(dashboard, 'TOOLING_REVISION', 3):
            for value in (None, 1, 3, 4):
                self.waiting(toolingRevision=value)
            before = {p: p.read_bytes() for p in self.common.rglob('*') if p.is_file()}
            view = self.collect()
            self.assertEqual(4, len(view['waiting']))
            self.assertEqual({'unknown', 'older', 'current', 'newer'},
                             {job['tooling']['status'] for job in view['waiting']})
            self.assertTrue(any('older queue tooling' in item for item in view['warnings']))
            self.assertTrue(any('newer queue tooling than this dashboard' in item for item in view['warnings']))
            self.assertEqual(before, {p: p.read_bytes() for p in self.common.rglob('*') if p.is_file()})

    def test_budget_summary_uses_loaded_registry_and_keeps_configured_policy_separate(self):
        configured = dict(maxRuns=2, memoryGiB=6.5, cpuCores=6, headroomGiB=1)
        with patch.object(dashboard, 'QUALIFIED_CLASSES', frozenset({'example.One', 'example.Two'})), \
                patch.object(dashboard, 'MAX_SMALL_CLASSES', 12), \
                patch.object(dashboard, 'SMALL_RESERVATION', (3 * 1024 ** 3, 2)):
            view = self.collect(policy_config=configured)
        self.assertEqual(configured, view['policy'])
        self.assertEqual(dict(registeredClasses=2, maxClasses=12,
                              memoryBytes=3 * 1024 ** 3, cpuCores=2), view['smallJobs'])
        self.assertIsNone(self.collect(policy_config=None)['policy'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_fresh_authored_reasons_preserve_provenance_and_validated_details(self):
        blocker = self.waiting(worktree='<script>unexpected()</script>')
        request = self.waiting(admission=dict(reason='memory', updated=self.now - 2,
            memoryShortfallBytes=1.4 * dashboard.GIB, cpuShortfallCores=0,
            blockedBy=blocker.record['id'], backfillAvailable=True))
        before = request.path.read_bytes()
        jobs = {job['id']: job for job in self.collect()['waiting']}
        report = jobs[request.record['id']]['admission']
        self.assertEqual('scheduler', report['source'])
        self.assertEqual('memory', report['reason'])
        self.assertEqual(1.4 * dashboard.GIB, report['memoryShortfallBytes'])
        self.assertEqual(blocker.record['id'], report['blockedBy'])
        self.assertTrue(report['backfillAvailable'])
        self.assertEqual('<script>unexpected()</script>', jobs[blocker.record['id']]['worktree'])
        self.assertEqual(before, request.path.read_bytes())

    def test_stale_future_and_invalid_authored_reasons_are_unknown(self):
        valid = dict(reason='memory', updated=self.now)
        for changed in (dict(updated=self.now - dashboard.FRESH_SECONDS - .01),
                        dict(updated=self.now + .01), dict(updated=True),
                        dict(updated=float('inf')), dict(reason='<script>'), dict(reason=[])):
            with self.subTest(changed=changed):
                self.assertIsNone(dashboard._reported_admission({'admission': dict(valid, **changed)}, self.now))
        report = dashboard._reported_admission({'admission': dict(valid,
            memoryShortfallBytes=float('nan'), cpuShortfallCores=-1,
            blockedBy='<img src=x onerror=alert(1)>', backfillAvailable='yes')}, self.now)
        self.assertEqual('memory', report['reason'])
        for field in ('memoryShortfallBytes', 'cpuShortfallCores', 'blockedBy', 'backfillAvailable'):
            self.assertIsNone(report[field])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_legacy_resource_shortfall_uses_fresh_complete_holder_evidence(self):
        lease = self.running()
        lease.publish(2.5 * dashboard.GIB, 1.25)
        self.now = time.time() + .1
        large = self.waiting()
        small = self.waiting(reservation=[4 * dashboard.GIB, 4])
        authored = self.waiting(admission=dict(reason='priority', updated=self.now))
        config = dict(maxRuns=3, memoryGiB=7, cpuCores=8, headroomGiB=2)
        with (self.common / 'maven-slot-0.lock').open('wb') as stream:
            maven_queue._try_lock(stream)
            jobs = {job['id']: job for job in self.collect(policy_config=config)['waiting']}
        report = jobs[large.record['id']]['admission']
        self.assertEqual('observation', report['source'])
        self.assertEqual('memory', report['reason'])
        self.assertEqual(1.5 * dashboard.GIB, report['memoryShortfallBytes'])
        self.assertIsNone(jobs[small.record['id']]['admission'])
        self.assertEqual('priority', jobs[authored.record['id']]['admission']['reason'])
        self.assertEqual('scheduler', jobs[authored.record['id']]['admission']['source'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_missing_or_stale_holder_usage_cannot_explain_legacy_admission(self):
        self.waiting()
        config = dict(maxRuns=3, memoryGiB=7, cpuCores=8, headroomGiB=2)
        with (self.common / 'maven-slot-0.lock').open('wb') as stream:
            maven_queue._try_lock(stream)
            self.assertIsNone(self.collect(policy_config=config)['waiting'][0]['admission'])
            self.running()
            self.now += dashboard.FRESH_SECONDS + 1
            self.assertIsNone(self.collect(policy_config=config)['waiting'][0]['admission'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_exclusive_holder_and_invalid_reservation_do_not_get_resource_guesses(self):
        self.running()
        self.now = time.time() + .1
        invalid = self.waiting(reservation=[0, 4])
        valid = self.waiting()
        config = dict(maxRuns=3, memoryGiB=7, cpuCores=8, headroomGiB=2)
        for name in ('maven-slot-0.lock', 'maven-queue.lock'):
            with self.subTest(lock=name), (self.common / name).open('wb') as stream:
                maven_queue._try_lock(stream)
                jobs = {job['id']: job for job in self.collect(policy_config=config)['waiting']}
                self.assertIsNone(jobs[invalid.record['id']]['admission'])
                if name == 'maven-slot-0.lock':
                    self.assertEqual('memory', jobs[valid.record['id']]['admission']['reason'])
                else:
                    self.assertIsNone(jobs[valid.record['id']]['admission'])

    def test_unverified_waiter_has_no_authored_admission_claim(self):
        self.waiting(admission=dict(reason='memory', updated=self.now, memoryShortfallBytes=dashboard.GIB))
        view = self.collect(proc=self.root / 'no-proc')
        self.assertIsNone(view['unverified'][0]['admission'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_stale_unlocked_records_are_ignored_and_never_pruned(self):
        running, waiting = self.running(), self.waiting()
        maven_queue._unlock(running.stream)
        maven_queue._unlock(waiting.stream)
        before = (running.path.read_bytes(), waiting.path.read_bytes())
        view = self.collect()
        self.assertEqual([], view['running'])
        self.assertEqual([], view['waiting'])
        self.assertEqual(before, (running.path.read_bytes(), waiting.path.read_bytes()))

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_waiting_order_uses_existing_aging_and_short_estimates(self):
        oldest = self.waiting(estimate=900, enqueued=self.monotonic - 700)
        aged = self.waiting(estimate=10, enqueued=self.monotonic - 400)
        long = self.waiting(estimate=900, enqueued=self.monotonic - 200)
        short = self.waiting(estimate=30, enqueued=self.monotonic - 30)
        self.assertEqual([r.path.stem for r in (oldest, aged, short, long)],
                         [r['id'] for r in self.collect()['waiting']])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_old_usage_is_unknown_and_malformed_records_do_not_crash(self):
        lease = self.running()
        lease.publish(1024, 2)
        self.now += 60
        view = self.collect()
        self.assertFalse(view['running'][0]['usageFresh'])
        self.assertIsNone(view['running'][0]['rssBytes'])
        with lease.lock:
            lease.stream.seek(1)
            lease.stream.write(b'{"rss":')
            lease.stream.truncate()
            lease.stream.flush()
        self.assertEqual([], self.collect()['running'])
        self.assertTrue(lease.path.exists())

    def test_missing_liveness_is_separate_from_running_and_waiting(self):
        self.running()
        self.waiting()
        view = self.collect(proc=self.root / 'no-proc')
        self.assertEqual('unknown', view['liveness'])
        self.assertEqual([], view['running'])
        self.assertEqual([], view['waiting'])
        self.assertEqual(2, len(view['unverified']))
        self.assertIsNone(view['heldSlots'])
        self.assertIsNone(view['exclusiveHeld'])

    def test_other_platforms_keep_records_unverified(self):
        self.running()
        with patch.object(dashboard.sys, 'platform', 'darwin'):
            view = self.collect()
        self.assertEqual('unknown', view['liveness'])
        self.assertEqual([], view['running'])
        self.assertEqual(1, len(view['unverified']))

    def test_live_payload_reads_skip_the_windows_locked_byte(self):
        self.running()
        self.waiting()
        original = os.fdopen

        @contextlib.contextmanager
        def guarded(descriptor, mode):
            with original(descriptor, mode) as stream:
                class Reader:
                    fileno = stream.fileno
                    seek = stream.seek

                    def read(inner, size):
                        if stream.tell() == 0:
                            raise PermissionError('Lease byte zero is locked')
                        return stream.read(size)

                yield Reader()

        with patch.object(dashboard.os, 'fdopen', side_effect=guarded), \
                patch.object(dashboard, '_lock_index', return_value=None):
            view = self.collect()
        self.assertEqual(2, len(view['unverified']))

    def test_malformed_values_and_deep_json_remain_observational(self):
        lease = self.running()
        with lease.lock:
            lease.stream.seek(1)
            lease.stream.write(b'[' * 2000 + b'0' + b']' * 2000)
            lease.stream.truncate()
            lease.stream.flush()
        self.assertEqual([], self.collect()['running'])
        self.assertTrue(lease.path.exists())
        log = self.common / 'maven-queue-log.jsonl'
        log.write_text(json.dumps(dict(peakRssGiB=1e308, waitSeconds=10**500)) + '\n')
        recent = self.collect()['recent'][0]
        self.assertIsNone(recent['peakRssBytes'])
        self.assertIsNone(recent['waitSeconds'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_admission_holder_without_usage_record_is_visible(self):
        with (self.common / 'maven-slot-0.lock').open('wb') as stream:
            maven_queue._try_lock(stream)
            view = self.collect()
            self.assertEqual(1, view['heldSlots'])
            self.assertFalse(view['exclusiveHeld'])
            self.assertEqual('slot-0', view['running'][0]['id'])
            self.assertEqual(Path.cwd().name, view['running'][0]['worktree'])
            self.assertTrue(any('no matching live usage' in warning for warning in view['warnings']))

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_serial_holder_and_matching_lease_are_not_duplicated(self):
        self.running()
        with (self.common / 'maven-queue.lock').open('wb') as stream:
            maven_queue._try_lock(stream)
            view = self.collect()
            self.assertEqual(1, len(view['running']))
            self.assertTrue(view['exclusiveHeld'])
            self.assertEqual(0, view['heldSlots'])
            self.assertEqual('serial', view['running'][0]['mode'])

    @unittest.skipUnless(sys.platform == 'linux', 'Requires Linux lease observation')
    def test_unverifiable_device_alias_does_not_mark_a_record_stale(self):
        lease = self.running()
        proc = self.root / 'proc'
        proc.mkdir()
        inode = lease.path.stat().st_ino
        (proc / 'locks').write_text(f'1: FLOCK ADVISORY WRITE 999999 01:02:{inode} 0 EOF\n')
        view = self.collect(proc=proc)
        self.assertEqual([], view['running'])
        self.assertEqual(1, len(view['unverified']))
        self.assertEqual('unknown', view['unverified'][0]['liveness'])
        self.assertFalse(any('unlocked' in warning for warning in view['warnings']))

    @unittest.skipUnless(hasattr(os, 'mkfifo'), 'Requires FIFOs')
    def test_matching_fifo_is_rejected_without_waiting_for_a_writer(self):
        directory = self.common / 'maven-running'
        directory.mkdir()
        path = directory / ('f' * 32 + '.lease')
        os.mkfifo(path)
        view = self.collect()
        self.assertEqual([], view['running'])
        self.assertTrue(path.exists())
        self.assertTrue(any('unreadable' in warning for warning in view['warnings']))

    def test_symlinks_invalid_numeric_values_and_deletion_races_are_safe(self):
        lease = self.running()
        lease.publish(float('nan'), 2)
        link = lease.directory / ('f' * 32 + '.lease')
        link.symlink_to(lease.path)
        original = dashboard._read
        def disappear(path, limit, **kwargs):
            if path == lease.path:
                raise FileNotFoundError()
            return original(path, limit, **kwargs)
        with patch.object(dashboard, '_read', side_effect=disappear):
            self.assertEqual([], self.collect()['running'])
        self.assertTrue(link.is_symlink())
        self.assertTrue(lease.path.exists())
        view = self.collect()
        self.assertIsNone((view['running'] + view['unverified'])[0]['rssBytes'])

    def test_recent_history_is_bounded_and_ignores_corrupt_tail(self):
        path = self.common / 'maven-queue-log.jsonl'
        rows = [dict(tree='task', kind='focused', end=str(i), waitSeconds=i,
                     holdSeconds=1, peakRssGiB=.5, meanCores=.2) for i in range(1000)]
        content = b''.join((json.dumps(row) + '\n').encode() for row in rows) + b'{broken'
        path.write_bytes(content)
        view = self.collect()
        self.assertEqual(20, len(view['recent']))
        self.assertEqual('999', view['recent'][0]['end'])
        self.assertEqual(.5 * 1024 ** 3, view['recent'][0]['peakRssBytes'])
        self.assertEqual(content, path.read_bytes())

    def test_http_has_only_local_read_only_routes(self):
        view = self.collect()
        with dashboard.make_server(lambda: view, port=0, page=b'<title>Queue</title>') as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                self.assertEqual('127.0.0.1', server.server_address[0])
                url = f'http://127.0.0.1:{server.server_port}'
                with urlopen(url + '/api/queue') as response:
                    self.assertEqual('no-store', response.headers['Cache-Control'])
                    self.assertIsNone(response.headers.get('Access-Control-Allow-Origin'))
                    self.assertEqual(view, json.load(response))
                with urlopen(url + '/') as response:
                    self.assertEqual(b'<title>Queue</title>', response.read())
                for path in ('/maven', '/../pom.xml', '/tools/testing/', '/api/queue?command=test'):
                    with self.assertRaises(HTTPError) as caught:
                        urlopen(url + path)
                    self.assertEqual(404, caught.exception.code)
                    caught.exception.close()
                with self.assertRaises(HTTPError) as caught:
                    urlopen(Request(url + '/api/queue', data=b'{}', method='POST'))
                self.assertEqual(501, caught.exception.code)
                caught.exception.close()
                with self.assertRaises(HTTPError) as caught:
                    urlopen(Request(url + '/api/queue', headers={'Host': 'external.example'}))
                self.assertEqual(403, caught.exception.code)
                caught.exception.close()
            finally:
                server.shutdown()
                thread.join(timeout=2)

    def test_idle_browser_connection_does_not_block_another_tab(self):
        with dashboard.make_server(lambda: {'version': 1}, port=0, page=b'queue') as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                with socket.create_connection(server.server_address) as idle:
                    idle.sendall(b'GET / HTTP/1.1\r\n')
                    with urlopen(f'http://127.0.0.1:{server.server_port}/api/queue', timeout=2) as response:
                        self.assertEqual({'version': 1}, json.load(response))
            finally:
                server.shutdown()
                thread.join(timeout=2)

    def test_refresh_cache_resolves_common_directory_once_and_bounds_sampling(self):
        with patch.object(dashboard.subprocess, 'check_output', return_value=str(self.common)) as git, \
                patch.object(dashboard, 'policy', return_value={}) as policy, \
                patch.object(dashboard, 'collect_snapshot', return_value={'warnings': []}) as collect, \
                patch.object(dashboard.time, 'monotonic', side_effect=[100, 101, 103, 131]):
            source = dashboard.SnapshotSource(self.root)
            for _ in range(4):
                source()
            self.assertEqual(1, git.call_count)
            self.assertEqual(2, policy.call_count)
            self.assertEqual(3, collect.call_count)

    def test_dashboard_cli_never_dispatches_maven(self):
        with patch.object(dashboard, 'main', return_value=0) as serve, \
                patch.object(maven_queue, 'maven_slot', side_effect=AssertionError('No admission')), \
                patch.object(maven_queue.subprocess, 'Popen', side_effect=AssertionError('No Maven')):
            self.assertEqual(0, maven_queue.main(['--dashboard', '--port', '8767']))
            serve.assert_called_once_with(['--port', '8767'])

    def test_extended_usage_record_fits_existing_reader_limit(self):
        lease = self.running(worktree='🦔' * 160, kind='🦔' * 160, pid=os.getpid(),
                             submitted=self.monotonic, admitted=self.monotonic, mode='resource-aware')
        data = lease.path.read_bytes()
        self.assertLess(len(data), 4096)
        record = json.loads(data[1:])
        self.assertEqual(7 * 1024 ** 3, record['memoryBytes'])

    def test_ctrl_c_reports_dashboard_stop(self):
        server = unittest.mock.MagicMock()
        server.__enter__.return_value = server
        server.serve_forever.side_effect = KeyboardInterrupt
        server.server_port = 8766
        output = io.StringIO()
        with patch.object(dashboard, 'SnapshotSource'), patch.object(dashboard, 'make_server', return_value=server), \
                contextlib.redirect_stdout(output):
            self.assertEqual(0, dashboard.main([]))
        self.assertIn('Queue dashboard stopped.', output.getvalue())
        self.assertNotIn('request cancelled', output.getvalue())


if __name__ == '__main__':
    unittest.main()
