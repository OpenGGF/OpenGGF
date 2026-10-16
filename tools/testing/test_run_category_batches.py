"""Bounded smoke batches retain coverage while yielding shared Maven admission."""
from contextlib import contextmanager
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import run_categories as runner


class BatchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.plan = dict(full=True, tests=[f'com/openggf/Test{i:03d}.java' for i in range(101)],
                         guards=True, categories=['common'], inventory_count=101, workers=1)
        self.clock = 0
        self.state = 'initial'
        self.events = []
        self.selections = []
        self.active = False
        self.timeouts = []
        self.preparation_timeouts = []
        self.outcomes = [(0, 'passed')] * 4
        for name, value in [('tree_state', lambda root: self.state), ('rom_args', lambda *args: []),
                            ('run_logged', self.maven)]:
            mock = patch.object(runner, name, side_effect=value)
            mock.start()
            self.addCleanup(mock.stop)
        for mock in (patch.object(runner.time, 'monotonic', side_effect=lambda: self.clock),
                     patch('sys.stdout', new_callable=io.StringIO)):
            mock.start()
            self.addCleanup(mock.stop)

    @contextmanager
    def admission(self, plan, lane):
        self.assertFalse(self.active)
        self.events.append(('acquire', lane))
        self.clock += 3600  # Deliberately enormous queue waiting, never execution.
        self.active = True
        try:
            yield 27
        finally:
            self.active = False
            self.events.append(('release', lane))

    def maven(self, command, cwd, log, temporary, **kwargs):
        if 'test-compile' in command:
            self.assertFalse(self.active)
            self.assertIsNone(kwargs['queue_fd'])
            self.preparation_timeouts.append(kwargs['timeout'])
            self.events.append(('execute', 'preparation'))
            self.clock += 5
            return 0
        self.assertTrue(self.active)
        self.assertEqual(27, kwargs['queue_fd'])
        self.timeouts.append(kwargs['timeout'])
        self.assertEqual(runner.IDLE_SECONDS, kwargs['idle_timeout'])
        self.assertIn('surefire:test', command)
        self.assertNotIn('test', command)
        for fork in (1, 2):
            self.assertTrue((temporary / f'fork-{fork}').is_dir())
        lane = 'guards' if '-Pguards' in command else 'ordinary'
        self.events.append(('execute', lane))
        if lane == 'ordinary':
            self.assertIn('-Psmoke', command)
            self.assertNotIn('-Pfull-suite', command)
            includes = Path(next(arg.split('=', 1)[1] for arg in command
                                 if arg.startswith('-Dsurefire.includesFile=')))
            candidates = includes.read_text().splitlines()
            self.assertLessEqual(len(candidates), 50)
            self.selections.append(candidates)
            self.assertIn('-DfailIfNoTests=false', command)
            self.assertIn('-Dsurefire.failIfNoSpecifiedTests=false', command)
        else:
            self.assertFalse(any('includesFile' in arg for arg in command))
            self.assertNotIn('-Ptest-concurrent', command)
        # Report directories cannot contain stale reports from earlier batches.
        reports = Path(next(arg.split('=', 1)[1] for arg in command
                            if arg.startswith('-Dopenggf.surefire.reports=')))
        self.assertFalse(reports.exists())
        code, outcome = self.outcomes.pop(0)
        if outcome != 'empty':
            reports.mkdir()
            suite = ET.Element('testsuite', tests='1', errors='0',
                               failures=str(int(outcome == 'failed')),
                               skipped=str(int(outcome == 'skipped')))
            case = ET.SubElement(suite, 'testcase', classname='Test' + str(len(self.events)), name='example')
            if outcome == 'failed':
                ET.SubElement(case, 'failure', message='assertion failed').text = 'evidence'
            if outcome == 'skipped':
                ET.SubElement(case, 'skipped', message='missing fixture')
            ET.ElementTree(suite).write(reports / 'TEST-example.xml')
        self.clock += 10
        return code

    def execute(self, **kwargs):
        return runner.run_plan(self.root, self.plan, admission=self.admission, **kwargs)

    def diagnostics(self):
        return next((self.root / 'target/category-tests').iterdir())

    def results(self):
        return json.loads((self.diagnostics() / 'results.json').read_text())

    def test_complete_inventory_is_partitioned_and_admission_yields_before_guards(self):
        self.assertEqual(0, self.execute())
        self.assertEqual([50, 50, 1], [len(batch) for batch in self.selections])
        self.assertEqual(self.plan['tests'], sum(self.selections, []))
        self.assertEqual([('execute', 'preparation')] +
                         [(event, lane) for lane in ['ordinary'] * 3 + ['guards']
                          for event in ('acquire', 'execute', 'release')], self.events)
        ordinary, guards = self.results()
        self.assertEqual(3, ordinary['reports'])
        self.assertEqual(3, ordinary['batches_completed'])
        self.assertEqual(1, guards['reports'])
        commands = list(self.diagnostics().glob('*command*.json'))
        self.assertEqual(5, len(commands))
        self.assertEqual(self.plan['tests'], json.loads((self.diagnostics() / 'plan.json').read_text())['tests'])
        self.assertFalse(list(self.diagnostics().glob('*-reports')))
        self.assertFalse(list(self.diagnostics().glob('*-tmp')))
        self.assertFalse((self.diagnostics() / 'includes.txt').exists())

    def test_queue_waits_do_not_consume_combined_execution_budget(self):
        self.assertEqual(0, self.execute(max_minutes=1))
        self.assertEqual([60], self.preparation_timeouts)
        self.assertEqual([55, 45, 35, 25], self.timeouts)
        self.assertEqual([30, 10], [result['elapsed_seconds'] for result in self.results()])

    def test_small_batch_keeps_includes_coverage_and_applies_both_bounded_heaps(self):
        shapes = []
        original = self.maven
        def observe(command, *args, **kwargs):
            if 'surefire:test' in command:
                shapes.append((command, kwargs.get('environment')))
            return original(command, *args, **kwargs)
        bounded = (['-Dmse=off', '-Dtest=placeholder', 'test', '-Dsurefire.argLine=-Xmx1g',
                    '-Dsurefire.forkCount=1', '-Dsurefire.reuseForks=true'], {'MAVEN_OPTS': '-Xmx1g'})
        with patch.object(runner, 'batch_lean', return_value=bounded), \
                patch.object(runner, 'run_logged', side_effect=observe):
            self.assertEqual(0, self.execute())
        self.assertEqual(self.plan['tests'], sum(self.selections, []))
        for command, env in shapes[:3]:
            self.assertIn('-Dsurefire.argLine=-Xmx1g', command)
            self.assertFalse(any(a.startswith('-Dtest=') for a in command))
            self.assertEqual('-Xmx1g', env['MAVEN_OPTS'])
        self.assertIsNone(shapes[-1][1])
        self.assertNotIn('-Dsurefire.argLine=-Xmx1g', shapes[-1][0])

    def test_exhausted_execution_budget_does_not_rejoin_queue(self):
        with self.assertRaisesRegex(TimeoutError, 'exhausted before another batch'):
            self.execute(max_minutes=1 / 4)
        self.assertEqual([('execute', 'preparation'), ('acquire', 'ordinary'),
                          ('execute', 'ordinary'), ('release', 'ordinary')], self.events)
        ordinary, = self.results()
        self.assertEqual(1, ordinary['batches_completed'])
        self.assertEqual('incomplete', ordinary['status'])

    def test_failure_and_skip_evidence_aggregate_across_batches(self):
        self.outcomes = [(1, 'failed'), (0, 'skipped'), (0, 'passed'), (0, 'passed')]
        self.assertEqual(1, self.execute())
        ordinary, guards = self.results()
        self.assertEqual((3, 1, 1), (ordinary['tests'], ordinary['failures'], ordinary['skipped']))
        self.assertEqual('assertion failed', ordinary['failed_cases'][0]['message'])
        self.assertEqual('missing fixture', ordinary['skipped_cases'][0]['reason'])
        self.assertEqual('failed', ordinary['status'])
        self.assertEqual('passed', guards['status'])
        self.assertEqual('failed', json.loads((self.diagnostics() / 'status.json').read_text())['status'])

    def test_empty_tag_excluded_batch_does_not_abort_nonempty_lane(self):
        self.outcomes = [(0, 'empty'), (0, 'passed'), (0, 'empty'), (0, 'passed')]
        self.assertEqual(0, self.execute())
        self.assertEqual(1, self.results()[0]['reports'])
        self.assertEqual(3, self.results()[0]['batches_completed'])

    def test_maven_failure_without_test_failures_stops_and_keeps_causal_log(self):
        self.outcomes = [(1, 'empty'), (0, 'passed'), (0, 'passed'), (0, 'passed')]
        original = self.maven
        def failure_log(command, cwd, log, temporary, **kwargs):
            code = original(command, cwd, log, temporary, **kwargs)
            log.write_text('compiler failed before tests: causal evidence\n')
            return code
        with patch.object(runner, 'run_logged', side_effect=failure_log):
            self.assertEqual(1, self.execute())
        self.assertEqual(1, len(self.selections))
        ordinary, = self.results()
        self.assertEqual('incomplete', ordinary['status'])
        self.assertEqual(1, ordinary['failed_batch'])
        self.assertIn('without test failure reports', ordinary['reason'])
        self.assertIn('compiler failed before tests', (self.diagnostics() / 'ordinary.log').read_text())
        self.assertEqual('incomplete', json.loads((self.diagnostics() / 'status.json').read_text())['status'])

    def test_entire_empty_or_skipped_lane_fails_without_guard_launch(self):
        for outcome in ('empty', 'skipped'):
            with self.subTest(outcome=outcome):
                self.outcomes = [(0, outcome)] * 3
                self.assertEqual(1, self.execute())
                self.assertEqual(['ordinary'], [result['lane'] for result in self.results()])

    def test_changed_sources_during_queue_wait_stop_before_launch(self):
        @contextmanager
        def changing(plan, lane):
            with self.admission(plan, lane) as fd:
                self.state = 'edited while queued'
                yield fd
        with self.assertRaisesRegex(ValueError, 'Working tree changed while waiting'):
            runner.run_plan(self.root, self.plan, admission=changing)
        self.assertEqual([], self.selections)
        self.assertEqual('incomplete', self.results()[0]['status'])
        self.assertFalse(self.active)
        self.assertEqual('incomplete', json.loads((self.diagnostics() / 'status.json').read_text())['status'])

    def test_changed_sources_after_first_batch_stop_before_another_admission(self):
        original = self.maven
        def changed(*args, **kwargs):
            code = original(*args, **kwargs)
            if 'surefire:test' in args[0]:
                self.state = 'edited during execution'
            return code
        with patch.object(runner, 'run_logged', side_effect=changed):
            with self.assertRaisesRegex(ValueError, 'Working tree changed during validation'):
                self.execute()
        self.assertEqual([('execute', 'preparation'), ('acquire', 'ordinary'),
                          ('execute', 'ordinary'), ('release', 'ordinary')], self.events)
        self.assertEqual(1, self.results()[0]['batches_completed'])

    def test_timeout_aggregates_completed_and_partial_reports_before_releasing(self):
        original = self.maven
        calls = 0
        def timeout(*args, **kwargs):
            nonlocal calls
            code = original(*args, **kwargs)
            calls += int('surefire:test' in args[0])
            if calls == 2:
                raise TimeoutError('execution budget exhausted')
            return code
        with patch.object(runner, 'run_logged', side_effect=timeout):
            with self.assertRaisesRegex(TimeoutError, 'budget exhausted'):
                self.execute()
        ordinary, = self.results()
        self.assertEqual(2, ordinary['reports'])
        self.assertEqual(1, ordinary['batches_completed'])
        self.assertEqual('incomplete', ordinary['status'])
        self.assertFalse(self.active)
        self.assertFalse(list(self.diagnostics().glob('*-tmp')))

    def test_case_evidence_cap_retains_omission_counts_across_batches(self):
        total = {}
        summary = dict(reports=1, tests=750, errors=0, failures=0, skipped=750,
                       skipped_cases=[{'reason': 'missing'}] * 750, skipped_cases_omitted=2)
        runner.merge_summary(total, summary)
        runner.merge_summary(total, summary)
        self.assertEqual(1500, total['skipped'])
        self.assertEqual(1000, len(total['skipped_cases']))
        self.assertEqual(504, total['skipped_cases_omitted'])


if __name__ == '__main__':
    unittest.main()
