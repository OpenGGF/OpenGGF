"""Attribute expensive local checks without retaining raw Surefire reports."""
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import run_categories as runner


class TimingSummaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.reports = Path(self.temp.name)

    def report(self, name, seconds, cases):
        suite = ET.Element('testsuite', name=name, tests=str(len(cases)),
                           failures='0', errors='0', skipped='0')
        if seconds is not None:
            suite.set('time', str(seconds))
        for test, duration in cases:
            case = ET.SubElement(suite, 'testcase', classname=name, name=test)
            if duration is not None:
                case.set('time', str(duration))
        ET.ElementTree(suite).write(self.reports / f'TEST-{name}.xml')

    def test_suite_setup_and_case_costs_remain_distinct(self):
        self.report('SlowSetup', 100, [('cheap assertion', .01)])
        self.report('SlowCase', 20, [('route[character=Tails]', 19)])
        summary = runner.summarize(self.reports)
        self.assertEqual(['SlowSetup', 'SlowCase'],
                         [r['class'] for r in summary['slowest_classes']])
        self.assertEqual([100, 20],
                         [r['reported_seconds'] for r in summary['slowest_classes']])
        self.assertEqual('route[character=Tails]', summary['slowest_cases'][0]['test'])
        self.assertEqual(19, summary['slowest_cases'][0]['reported_seconds'])
        self.assertEqual(2, summary['tests'])

    def test_top_twenty_is_global_across_batches_and_ties_are_deterministic(self):
        total = {}
        for batch in range(3):
            for path in self.reports.glob('*.xml'):
                path.unlink()
            for i in reversed(range(batch * 30, (batch + 1) * 30)):
                self.report(f'Class{i:03}', i // 2, [('case', i // 2)])
            runner.merge_summary(total, runner.summarize(self.reports))
        # Tie ordering uses identity, independent of report/batch arrival order.
        expected = sorted(range(90), key=lambda i: (-(i // 2), f'Class{i:03}'))[:20]
        self.assertEqual([f'Class{i:03}' for i in expected],
                         [r['class'] for r in total['slowest_classes']])
        self.assertEqual(20, len(total['slowest_cases']))
        self.assertEqual(90, total['reports'])
        self.assertEqual(90, total['tests'])

    def test_missing_negative_and_nonfinite_times_do_not_poison_diagnostics(self):
        for i, duration in enumerate([None, '-1', 'NaN', 'Infinity', 'bad', '0']):
            self.report(f'Class{i}', duration, [('test', duration)])
        summary = runner.summarize(self.reports)
        self.assertEqual(['Class5'], [r['class'] for r in summary['slowest_classes']])
        self.assertEqual(6, summary['tests'])
        json.dumps(summary, allow_nan=False)

    def test_legacy_or_empty_batches_do_not_erase_earlier_timings(self):
        self.report('Slow', 3, [('test', 2)])
        total = {}
        runner.merge_summary(total, runner.summarize(self.reports))
        runner.merge_summary(total, dict(reports=0, tests=0, failures=0, errors=0, skipped=0))
        self.assertEqual('Slow', total['slowest_classes'][0]['class'])
        self.assertEqual('test', total['slowest_cases'][0]['test'])
