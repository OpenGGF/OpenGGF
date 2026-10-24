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
        with patch.dict(os.environ, MAVEN_ARGS='-Pfull-suite'):
            with self.assertRaises(ValueError):
                profile.maven_command('normal', ['a.TestOne'], self.root)

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

    def run_main(self, exit_code=0, broken_xml=False, drift=False, lane='lean', memory_valid=True, cancelled=False, write_failure=False, wait_drift=False):
        entered = []
        @contextmanager
        def lease(name, value, **kwargs):
            entered.append((name, kwargs))
            try:
                yield value
            finally:
                entered.append(('released:' + name, {}))
        def execute(command, environment, root, temporary, fds, seconds, result):
            self.assertEqual((21, 22, 23, 24), fds)
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
            stack.enter_context(patch.object(profile, 'source_fingerprint', side_effect=['before', 'changed while queued' if wait_drift else 'before', 'after' if drift else ('changed while queued' if wait_drift else 'before')]))
            stack.enter_context(patch.object(profile, 'git', return_value='abc'))
            execution = stack.enter_context(patch.object(profile, 'execute', side_effect=execute))
            stack.enter_context(patch('sys.stdout', new=io.StringIO()))
            if write_failure:
                stack.enter_context(patch.object(profile.json, 'dump', side_effect=OSError('disk full')))
                with self.assertRaisesRegex(OSError, 'disk full'):
                    profile.main(['--lane', lane, '--test', 'a.TestOne', '--cold-compile'])
                self.assertFalse(list((self.root / 'target').glob('small-tests-*')))
                self.assertEqual('released:tree', entered[-1][0])
                return
            code = profile.main(['--lane', lane, '--test', 'a.TestOne', '--cold-compile'])
        if wait_drift:
            execution.assert_not_called()
            self.assertTrue((self.root / 'target/classes').is_dir())
        result = json.loads((self.root / 'target/small-test-memory.json').read_text())
        self.assertEqual(['tree', 'queue', 'activity', 'released:activity', 'released:queue', 'released:tree'],
                         [name for name, _ in entered])
        self.assertEqual(dict(exclusive=True, tree_fd=21, kind='profile:small-tests', estimate=60,
                              reservation=(4 * 1024**3, 4) if lane == 'lean' else (7 * 1024**3, 8)), entered[1][1])
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
