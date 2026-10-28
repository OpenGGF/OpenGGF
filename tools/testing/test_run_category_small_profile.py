"""Bounded qualification controls; no Maven/JVM launches or real PID signals."""
from contextlib import contextmanager, ExitStack
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import profile_small_tests as profile


class SmallProfileTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        source = self.root / 'src/test/java/a/TestOne.java'
        source.parent.mkdir(parents=True)
        source.write_text('package a; class TestOne {}')
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.dict(os.environ, {}, clear=True))
        self.stack.enter_context(patch.object(profile.Path, 'cwd', return_value=self.root))

    def test_selector_and_timeout_limits_reject_suite_expansion(self):
        self.assertEqual(['a.TestOne'], profile.exact_tests(self.root, 'a.TestOne,a.TestOne'))
        for text in ('', 'TestOne', 'a.Test*', 'a.TestOne#one', 'a.TestOne,',
                     '!a.TestOne', 'a..TestOne', 'a.Missing', ','.join(['a.TestOne'] * 51)):
            with self.subTest(selector=text), self.assertRaises(ValueError):
                profile.exact_tests(self.root, text)
        args = ['--lane', 'lean', '--test', 'a.TestOne']
        self.assertEqual(10, profile.arguments(args).max_minutes)
        self.assertEqual(30, profile.arguments(args + ['--max-minutes', '30']).max_minutes)
        for extra in (['--max-minutes', '0'], ['--max-minutes', '-1'],
                      ['--max-minutes', '30.01'], ['--max-minutes', 'nan'],
                      ['--max-minutes', 'inf'], ['--profile', 'smoke']):
            with self.subTest(extra=extra), patch('sys.stderr', new=io.StringIO()), self.assertRaises(SystemExit):
                profile.arguments(args + extra)
        for missing in ([], ['--test', 'a.TestOne'], ['--lane', 'normal']):
            with patch('sys.stderr', new=io.StringIO()), self.assertRaises(SystemExit):
                profile.arguments(missing)

    def test_lane_commands_preserve_selection_agents_and_normal_maven_heap(self):
        for lane, heap in (('normal', '3g'), ('lean', '1g')):
            command, environment = profile.maven_command(lane, ['a.TestOne'], self.root / 'run')
            self.assertEqual(['mvn', '-Dmse=off', '-B', '-Dtest=a.TestOne'], command[:4])
            line = next(arg for arg in command if arg.startswith('-Dsurefire.argLine='))
            self.assertIn('-Xmx' + heap, line)
            self.assertIn('${mockito.agent.argLine}', line)
            self.assertIn('${test.cds.argLine}', line)
            self.assertIn('-Dopenggf.surefire.reports=' + str(self.root / 'run/reports'), command)
            self.assertEqual('-Xmx1g' if lane == 'lean' else None, environment.get('MAVEN_OPTS'))
            self.assertFalse(any(arg.startswith('-P') or 'listener' in arg or 'Xlog' in arg for arg in command))
            self.assertEqual(1, command.count('test'))
        for options in ({'MAVEN_ARGS': '-Pfull-suite'}, {'_JAVA_OPTIONS': '-Xmx8g'}):
            with patch.dict(os.environ, options):
                for workers in (1, 2):
                    with self.assertRaises(ValueError):
                        profile.maven_command('normal', ['a.TestOne'], self.root, workers)

    def test_two_forks_keep_heap_agents_isolation_and_conservative_admission(self):
        for lane, heap in (('normal', '3g'), ('lean', '1g')):
            command, environment = profile.maven_command(lane, ['a.TestOne'], self.root / 'run', 2)
            self.assertIn('-Ptest-concurrent', command)
            self.assertIn('-Dsurefire.forkCount=2', command)
            self.assertNotIn('-Dsurefire.forkCount=1', command)
            self.assertIn('-Dsurefire.reuseForks=true', command)
            self.assertIn('-Xmx' + heap, next(arg for arg in command if arg.startswith('-Dsurefire.argLine=')))
            self.assertEqual(1, command.count('test'))
        with self.assertRaises(ValueError):
            profile.maven_command('lean', ['a.TestOne'], self.root, 4)
        self.assertEqual(2, profile.arguments(['--lane', 'lean', '--test', 'a.TestOne', '--workers', '2']).workers)
        self.assertEqual(0, self.run_main(workers=2)[0])

    def test_fingerprint_ignores_dashboard_and_prose_but_tracks_actual_build_inputs(self):
        relevant = ['pom.xml', '.mvn/jvm.config', 'src/test/java/a/TestOne.java',
                    'src/test/resources/fixture.bin', 'src/main/resources/config.yaml',
                    'tools/testing/maven_queue.py', 'mod-api-release-policy.properties',
                    'racing/time-attack/src/main/java/Probe.java', 'examples/mods/Probe.java']
        irrelevant = ['tools/testing/queue_dashboard.py', 'tools/testing/queue_dashboard.html',
                      'tools/testing/README.md', 'tools/testing/test_run_category_dashboard.py']
        for name in relevant + irrelevant:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('before')
        listing = '\0'.join(relevant + irrelevant).encode() + b'\0'
        with patch.object(profile.subprocess, 'check_output', return_value=listing) as inventory:
            before = profile.source_fingerprint(self.root)
            for name in irrelevant:
                (self.root / name).write_text('concurrent dashboard/prose edit')
            self.assertEqual(before, profile.source_fingerprint(self.root))
            for name in relevant:
                with self.subTest(input=name):
                    (self.root / name).write_text('changed')
                    self.assertNotEqual(before, profile.source_fingerprint(self.root))
                    (self.root / name).write_text('before')
            (self.root / relevant[-1]).unlink()
            self.assertNotEqual(before, profile.source_fingerprint(self.root))
            command = inventory.call_args.args[0]
            self.assertNotIn('HEAD', command)
            self.assertEqual(['src', 'racing', 'examples', '.mvn', 'pom.xml', 'mod-api-release-policy.properties', 'tools/testing'],
                             command[command.index('--') + 1:])
            self.assertIn('-o', command)  # New untracked inputs participate too.

    def test_memory_uses_simultaneous_total_not_sum_of_independent_peaks(self):
        sampler = profile.MemorySampler()
        sampler.record([(11, 100, 'other', [], 900, 0), (12, 101, 'test', ['-Xmx1g'], 100, 200)])
        sampler.record([(11, 100, 'maven', ['-Xmx1g'], 100, 900), (12, 101, 'test', ['-Xmx1g'], 800, 0)])
        result = sampler.summary()
        self.assertEqual(2, result['samples'])
        self.assertEqual(1000, result['peakRssBytes'])
        self.assertEqual(900, result['peakSwapBytes'])
        self.assertEqual(1800, result['peakRssPlusSwapBytes'])
        self.assertEqual('maven', result['processes'][0]['role'])
        self.assertEqual(['-Xmx1g'], result['processes'][0]['xmxArgs'])
        self.assertEqual(100, result['processes'][0]['start'])
        self.assertEqual(900, result['processes'][0]['peakRssBytes'])

    def test_parallel_qualification_requires_simultaneously_observed_test_jvms(self):
        sampler = profile.MemorySampler(expected_test_workers=2)
        sampler.record([(1, 1, 'maven', [], 10, 0), (2, 2, 'test', ['-Xmx1g'], 20, 0)])
        sampler.record([(1, 1, 'maven', [], 10, 0), (3, 3, 'test', ['-Xmx1g'], 20, 0)])
        self.assertFalse(sampler.summary()['valid'], 'successive forks do not prove overlap')
        sampler.record([(1, 1, 'maven', [], 10, 0), (2, 2, 'test', ['-Xmx1g'], 20, 0),
                        (3, 3, 'test', ['-Xmx1g'], 20, 0)])
        result = sampler.summary()
        self.assertTrue(result['valid'])
        self.assertEqual(2, result['expectedTestWorkers'])
        self.assertEqual(2, result['peakConcurrentTestWorkers'])

    def test_sampler_exception_and_missing_jvms_cannot_be_valid_evidence(self):
        sampler = profile.MemorySampler()
        with patch.object(sampler, 'sample', side_effect=RuntimeError('observer broke')):
            sampler.run()
        result = sampler.summary()
        self.assertEqual(1, result['errorCount'])
        self.assertIn('observer broke', result['errors'][0])
        self.assertFalse(result['valid'])
        sampler = profile.MemorySampler()
        sampler.record([(11, 100, 'maven', [], 100, 0)])
        self.assertFalse(sampler.summary()['valid'])

    def test_procfs_sampling_keeps_swap_units_heap_args_and_process_identity(self):
        proc = self.root / 'proc'
        for pid, start, args, swap in ((11, 100, ['/bin/java', '-Xmx1g', 'org.codehaus.plexus.classworlds.launcher.Launcher'], 7),
                                       (12, 101, ['/bin/java', '-Xmx1g', '-jar', 'surefirebooter.jar'], 3)):
            directory = proc / str(pid)
            directory.mkdir(parents=True)
            (directory / 'cmdline').write_bytes(('\0'.join(args) + '\0').encode())
            (directory / 'status').write_text(f'State:\tR (running)\nVmSwap:\t{swap} kB\n')
            fields = ['S'] + ['0'] * 21
            fields[19] = str(start)
            (directory / 'stat').write_text(f'{pid} (java) ' + ' '.join(fields))
        sampler = profile.MemorySampler()
        with patch.object(profile, 'descendant_usage', return_value=[(11, 100, 1000), (12, 101, 2000)]), \
                patch.object(profile, 'Path', side_effect=lambda value: proc if value == '/proc' else Path(value)):
            sampler.sample()
            summary = sampler.summary()
            self.assertTrue(summary['valid'])
            self.assertEqual(10240, summary['peakSwapBytes'])
            self.assertEqual(13240, summary['peakRssPlusSwapBytes'])
            (proc / '12/status').write_text('State:\tR (running)\n')
            sampler.run()
            self.assertFalse(sampler.summary()['valid'])
            self.assertIn('missing VmSwap', sampler.summary()['errors'][0])

    def test_missing_swap_requires_proven_exit_and_released_address_space(self):
        proc = self.root / 'proc'
        directory = proc / '11'
        directory.mkdir(parents=True)
        (directory / 'cmdline').write_bytes(b'/bin/java\0')
        (directory / 'status').write_text('State:\tR (running)\n')
        for flags, vsize, rss, threads, skipped in ((4, 0, 0, 1, True), (4, 0, 0, 0, True), (0, 0, 0, 1, False),
                                            (4, 1, 0, 1, False), (4, 0, 1, 1, False),
                                            (4, 0, 0, 2, False), (4, 0, 0, None, False)):
            with self.subTest(flags=flags, vsize=vsize, rss=rss, threads=threads):
                (directory / 'status').write_text('State:\tR (running)\n' +
                    ('' if threads is None else f'Threads:\t{threads}\n'))
                fields = ['R'] + ['0'] * 21
                fields[6], fields[19], fields[20], fields[21] = map(str, (flags, 100, vsize, rss))
                (directory / 'stat').write_text('11 (java) ' + ' '.join(fields))
                sampler = profile.MemorySampler()
                with patch.object(profile, 'descendant_usage', return_value=[(11, 100, 1000)]), \
                        patch.object(profile, 'Path', side_effect=lambda value: proc if value == '/proc' else Path(value)):
                    if skipped:
                        sampler.sample()
                    else:
                        with self.assertRaisesRegex(ValueError, 'missing VmSwap'):
                            sampler.sample()
                self.assertEqual(int(skipped), sampler.summary()['exitingSamplesSkipped'])
                self.assertEqual([], sampler.summary()['processes'])

    def test_unreadable_procfs_is_an_observer_error_but_normal_exit_is_not(self):
        for error, errors in ((PermissionError('denied'), 1), (FileNotFoundError('exited'), 0)):
            sampler = profile.MemorySampler()
            with patch.object(profile, 'descendant_usage', return_value=[(11, 100, 1000)]), \
                    patch.object(profile.Path, 'read_bytes', side_effect=error):
                if errors:
                    sampler.run()
                else:
                    sampler.sample()
            self.assertEqual(errors, sampler.summary()['errorCount'])
            self.assertFalse(sampler.summary()['valid'])

    def reports(self, directory, text=None):
        directory.mkdir(parents=True, exist_ok=True)
        (directory / 'TEST-a.TestOne.xml').write_text(text or '''<testsuite tests="3" failures="0" errors="0" skipped="1">
          <testcase classname="a.TestOne" name="duplicate"/>
          <testcase classname="a.TestOne" name="duplicate"/>
          <testcase classname="a.TestOne" name="disabled"><skipped message="fixture unavailable"/></testcase>
        </testsuite>''')

    def proc_tree(self):
        proc = self.root / 'proc'
        for pid, parent, threads in ((10, 1, {10: '', 11: '20 99'}),
                                      (20, 10, {20: '30 40'}), (30, 20, {30: ''}),
                                      (40, 999, {40: ''})):
            directory = proc / str(pid)
            directory.mkdir(parents=True)
            fields = ['S'] + ['0'] * 21
            fields[1], fields[19], fields[21] = str(parent), str(pid * 10), '2'
            (directory / 'stat').write_text(f'{pid} (java) ' + ' '.join(fields))
            for tid, children in threads.items():
                task = directory / 'task' / str(tid)
                task.mkdir(parents=True)
                (task / 'children').write_text(children)
        return proc

    def test_owned_tree_walk_includes_worker_children_and_ignores_exits_and_reused_pids(self):
        proc = self.proc_tree()
        with patch.object(profile.os, 'sysconf', return_value=4096):
            self.assertEqual([(20, 200, 8192), (30, 300, 8192)],
                             profile.descendant_usage(10, proc))

    def test_actual_enumeration_and_owned_stat_errors_invalidate_prior_good_samples(self):
        proc = self.proc_tree()
        original = Path.read_text
        def deny_owned_stat(path, *args, **kwargs):
            if path == proc / '20/stat':
                raise PermissionError('owned stat denied')
            return original(path, *args, **kwargs)
        for method, error in (('iterdir', PermissionError('inventory denied')),
                              ('read_text', deny_owned_stat)):
            sampler = profile.MemorySampler()
            sampler.record([(1, 1, 'maven', [], 1, 0), (2, 2, 'test', [], 1, 0)])
            with patch.object(profile.os, 'getpid', return_value=10), \
                    patch.object(profile, 'Path', side_effect=lambda value: proc if value == '/proc' else Path(value)), \
                    patch.object(Path, method, autospec=True, side_effect=error):
                sampler.run()
            self.assertFalse(sampler.summary()['valid'])
            self.assertEqual(1, sampler.summary()['errorCount'])

    def test_outcomes_keep_full_case_multiset_skip_causes_and_missing_candidates(self):
        self.reports(self.root / 'reports')
        result = profile.outcomes(self.root / 'reports', ['a.TestOne'])
        self.assertTrue(result['complete'])
        self.assertEqual([{'class': 'a.TestOne', 'name': 'disabled', 'status': 'skipped', 'count': 1},
                          {'class': 'a.TestOne', 'name': 'duplicate', 'status': 'passed', 'count': 2}], result['cases'])
        self.assertEqual('fixture unavailable', result['skipped_cases'][0]['reason'])
        self.assertFalse(profile.outcomes(self.root / 'reports', ['a.Other'])['complete'])
        self.reports(self.root / 'reports', '<testsuite tests="2"><testcase classname="a.TestOne" name="one"/></testsuite>')
        self.assertFalse(profile.outcomes(self.root / 'reports', ['a.TestOne'])['complete'])

    def run_main(self, exit_code=0, broken_xml=False, drift=False, lane='lean', memory_valid=True, cancelled=False, write_failure=False, wait_drift=False, workers=1, prepare=False, prepare_exit=0, prepare_seconds=0):
        entered = []
        clock = [0]
        @contextmanager
        def lease(name, value, **kwargs):
            entered.append((name, kwargs))
            try:
                yield value
            finally:
                entered.append(('released:' + name, {}))
        def execute(command, environment, root, temporary, fds, seconds, result):
            self.assertEqual((21, 22, 23, 24), fds)
            if prepare:
                self.assertLessEqual(seconds, 600)
                self.assertGreater(seconds, 590)
                for directory in ('tmp', 'tmp/fork-1', 'reports', 'diagnostics', 'traces', 'artifacts'):
                    self.assertTrue((temporary / directory).is_dir())
                self.assertIn('surefire:test', command)
                self.assertNotIn('test', command)
            else:
                self.assertEqual(600, seconds)
                self.assertFalse((root / 'target/classes').exists())
            self.reports(temporary / 'reports', '<broken' if broken_xml else None)
            (temporary / 'execution.log').write_text('x' * 20000)
            result.update(commandExitCode=exit_code, timedOut=False, executionSeconds=2,
                          memory={'valid': memory_valid})
            if cancelled:
                raise KeyboardInterrupt
        (self.root / 'target/classes').mkdir(parents=True, exist_ok=True)
        (self.root / 'target/preserve.txt').write_text('keep')
        with ExitStack() as stack:
            stack.enter_context(patch.object(profile, 'worktree_build_slot', side_effect=lambda root: lease('tree', 21)))
            stack.enter_context(patch.object(profile, 'maven_slot', side_effect=lambda root, **kw: lease('queue', (21, 22), **kw)))
            stack.enter_context(patch.object(profile, 'build_activity_slot', side_effect=lambda root, **kw: lease('activity', (23, 24), **kw)))
            stack.enter_context(patch.object(profile, 'source_fingerprint', side_effect=['before', *(['before'] if prepare else []), 'changed while queued' if wait_drift else 'before', 'after' if drift else ('changed while queued' if wait_drift else 'before')]))
            stack.enter_context(patch.object(profile, 'git', return_value='abc'))
            execution = stack.enter_context(patch.object(profile, 'execute', side_effect=execute))
            stack.enter_context(patch.object(profile, 'validate_preparation_contract'))
            def prepare_run(*args, **kwargs):
                clock[0] += prepare_seconds
                if isinstance(prepare_exit, Exception):
                    raise prepare_exit
                return prepare_exit
            preparation = stack.enter_context(patch.object(profile, 'run_logged', side_effect=prepare_run))
            stack.enter_context(patch.object(profile.time, 'monotonic', side_effect=lambda: clock[0]))
            stack.enter_context(patch('sys.stdout', new=io.StringIO()))
            if write_failure:
                stack.enter_context(patch.object(profile.json, 'dump', side_effect=OSError('disk full')))
                with self.assertRaisesRegex(OSError, 'disk full'):
                    profile.main(['--lane', lane, '--test', 'a.TestOne', *(['--prepare'] if prepare else ['--cold-compile']), '--workers', str(workers)])
                self.assertFalse(list((self.root / 'target').glob('small-tests-*')))
                self.assertEqual('released:tree', entered[-1][0])
                return
            code = profile.main(['--lane', lane, '--test', 'a.TestOne', *(['--prepare'] if prepare else ['--cold-compile']), '--workers', str(workers)])
        result = json.loads((self.root / 'target/small-test-memory.json').read_text())
        if prepare:
            preparation.assert_called_once()
            self.assertEqual(['mvn', '-Dmse=off', 'test-compile', '-B'], preparation.call_args.args[0])
            self.assertEqual(21, preparation.call_args.kwargs['queue_fd'])
            self.assertNotIn('environment', preparation.call_args.kwargs)
            if prepare_exit or prepare_seconds >= 600:
                execution.assert_not_called()
                self.assertEqual(['tree', 'released:tree'], [name for name, _ in entered])
                return code, result
        if wait_drift:
            execution.assert_not_called()
            self.assertTrue((self.root / 'target/classes').is_dir())
        result = json.loads((self.root / 'target/small-test-memory.json').read_text())
        self.assertEqual(['tree', 'queue', 'activity', 'released:activity', 'released:queue', 'released:tree'],
                         [name for name, _ in entered])
        self.assertEqual(dict(exclusive=True, tree_fd=21, kind='profile:small-tests', estimate=60,
                              reservation=(4 * 1024**3, 4) if lane == 'lean' and workers == 1 else (7 * 1024**3, 8)), entered[1][1])
        self.assertTrue(entered[2][1]['exclusive'])
        self.assertEqual('keep', (self.root / 'target/preserve.txt').read_text())
        self.assertFalse(list((self.root / 'target').glob('small-tests-*')))
        self.assertEqual('abc', result['commitAfter'])
        self.assertIn('sourceAfter', result)
        self.assertEqual({'treeWaitSeconds', 'queueWaitSeconds', 'buildDrainWaitSeconds'}, set(result['timings']))
        return code, result

    def test_success_cleans_raw_evidence_and_omits_log_tail(self):
        code, result = self.run_main()
        self.assertEqual(0, code)
        self.assertTrue(result['complete'])
        self.assertNotIn('logTail', result)
        self.assertEqual(3, result['surefire']['tests'])

    def test_prepared_qualification_uses_normal_build_then_only_bounded_surefire(self):
        code, result = self.run_main(prepare=True)
        self.assertEqual(0, code)
        self.assertTrue(result['prepared'])
        self.assertEqual(0, result['preparation']['exitCode'])
        self.assertIn('surefire:test', result['command'])
        self.assertIn('-Dsurefire.forkCount=1', result['command'])
        with patch('sys.stderr', new=io.StringIO()), self.assertRaises(SystemExit):
            profile.arguments(['--lane', 'lean', '--test', 'a.TestOne', '--prepare', '--cold-compile'])

    def test_failed_preparation_never_requests_a_test_slot(self):
        code, result = self.run_main(prepare=True, prepare_exit=1)
        self.assertEqual(1, code)
        self.assertFalse(result['complete'])
        self.assertIn('Normal-memory preparation failed', result['errors'][0])

    def test_preparation_timeouts_are_reported_and_never_request_test_admission(self):
        for options in ({'prepare_exit': TimeoutError('preparation watchdog')}, {'prepare_seconds': 600}):
            with self.subTest(options=options):
                code, result = self.run_main(prepare=True, **options)
                self.assertEqual(1, code)
                self.assertFalse(result['complete'])
                self.assertTrue(result['timedOut'])
                (self.root / 'target/small-test-memory.json').unlink()

    def test_input_change_while_waiting_rejects_run_before_cold_cleanup_or_maven(self):
        code, result = self.run_main(wait_drift=True)
        self.assertEqual(1, code)
        self.assertTrue(result['sourceDrift'])
        self.assertIn('changed while waiting', result['errors'][0])

    def test_failure_drift_and_malformed_xml_remain_failed_and_cleaned(self):
        for options in ({'exit_code': 7}, {'broken_xml': True}, {'drift': True}, {'memory_valid': False}, {'cancelled': True}):
            with self.subTest(options=options):
                code, result = self.run_main(**options)
                self.assertNotEqual(0, code)
                self.assertFalse(result['complete'])
                self.assertLessEqual(len(result['logTail']), 12000)
                (self.root / 'target/small-test-memory.json').unlink()

    def test_normal_lane_uses_normal_reservation(self):
        self.assertEqual(0, self.run_main(lane='normal')[0])

    def test_report_write_failure_still_removes_raw_evidence(self):
        self.run_main(write_failure=True)

    def test_wait_measurement_keeps_cancelled_wait_outside_execution(self):
        @contextmanager
        def cancelled():
            raise KeyboardInterrupt
            yield  # pragma: no cover
        timings = {}
        with ExitStack() as stack, patch.object(profile.time, 'monotonic', side_effect=[10, 70]):
            with self.assertRaises(KeyboardInterrupt):
                profile.acquire(stack, cancelled(), timings, 'queueWaitSeconds')
        self.assertEqual({'queueWaitSeconds': 60}, timings)

    def test_existing_output_is_never_overwritten_and_never_takes_leases(self):
        output = self.root / 'existing.json'
        output.write_text('original')
        with patch.object(profile, 'worktree_build_slot') as lease, patch('sys.stderr', new=io.StringIO()):
            with self.assertRaises(SystemExit):
                profile.main(['--lane', 'normal', '--test', 'a.TestOne', '--output', str(output)])
        self.assertEqual('original', output.read_text())
        lease.assert_not_called()

    def test_watchdog_and_cleanup_use_only_owned_fake_process(self):
        temporary = self.root / 'run'
        temporary.mkdir()
        process = Mock(pid=987654321, returncode=-15)
        process.poll.return_value = None
        process.wait.return_value = -15
        result = {}
        with patch.object(profile.subprocess, 'Popen', return_value=process) as launch, \
                patch.object(profile, 'stop_process_tree') as stop, \
                patch.object(profile, 'MemorySampler') as sampler, \
                patch.object(profile.time, 'monotonic', side_effect=[100, 102, 103]):
            sampler.return_value.finish.return_value = {'valid': False}
            profile.execute(['mvn'], {}, self.root, temporary, (21, 22, 23, 24), 1, result)
        stop.assert_called_once_with(process)
        self.assertTrue(result['timedOut'])
        self.assertEqual(-15, result['commandExitCode'])
        self.assertEqual(3, result['executionSeconds'])
        self.assertEqual((21, 22, 23, 24), launch.call_args.kwargs['pass_fds'])
        self.assertTrue(launch.call_args.kwargs['start_new_session'])

    def test_output_read_failure_terminates_process_and_preserves_measurement(self):
        temporary = self.root / 'run'
        temporary.mkdir()
        process = Mock(pid=987654321, returncode=-15)
        process.poll.return_value = None
        process.stdout.read1.side_effect = OSError('read failed')
        result = {}
        with patch.object(profile.subprocess, 'Popen', return_value=process), \
                patch.object(profile, 'stop_process_tree') as stop, \
                patch.object(profile, 'MemorySampler') as sampler, \
                patch.object(profile.select, 'select', return_value=([process.stdout], [], [])):
            sampler.return_value.finish.return_value = {'valid': False}
            with self.assertRaisesRegex(OSError, 'read failed'):
                profile.execute(['mvn'], {}, self.root, temporary, (), 60, result)
        stop.assert_called_once_with(process)
        self.assertFalse(result['memory']['valid'])
        self.assertIn('executionSeconds', result)

    def test_completed_parent_still_cleans_up_its_process_group(self):
        temporary = self.root / 'run'
        temporary.mkdir()
        process = Mock(pid=987654321, returncode=0)
        process.poll.return_value = process.wait.return_value = 0
        process.stdout.read1.return_value = b''
        with patch.object(profile.subprocess, 'Popen', return_value=process), \
                patch.object(profile, 'stop_process_tree') as stop, \
                patch.object(profile, 'MemorySampler'), \
                patch.object(profile.select, 'select', return_value=([process.stdout], [], [])):
            profile.execute(['mvn'], {}, self.root, temporary, (), 60, {})
        stop.assert_called_once_with(process)

    def test_unrequested_class_does_not_count_as_complete_selection(self):
        reports = self.root / 'reports'
        self.reports(reports)
        (reports / 'TEST-other.Test.xml').write_text(
            '<testsuite tests="1" failures="0" errors="0" skipped="0">'
            '<testcase classname="other.Test" name="unexpected"/></testsuite>')
        result = profile.outcomes(reports, ['a.TestOne'])
        self.assertFalse(result['complete'])
        self.assertEqual([], result['missingClasses'])
        self.assertEqual(['other.Test'], result['unexpectedClasses'])


if __name__ == '__main__':
    unittest.main()
