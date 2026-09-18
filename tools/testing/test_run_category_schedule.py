"""Priority and aging policy, independent of host resources and wall-clock sleeps."""
import unittest
import json
from pathlib import Path
import tempfile

import maven_schedule as schedule


class SchedulingTests(unittest.TestCase):
    def request(self, name, estimate, enqueued=0):
        return dict(id=name, estimate=estimate, enqueued=enqueued)

    def test_short_requests_overtake_earlier_large_requests(self):
        requests = [self.request('full', 900), self.request('single', 30, 10)]
        self.assertEqual(['single', 'full'], [r['id'] for r in schedule.ordered(requests, 20)])

    def test_aged_requests_win_in_arrival_order(self):
        requests = [self.request('full', 900), self.request('medium', 180, 1),
                    self.request('new-single', 30, 301)]
        self.assertEqual(['full', 'medium', 'new-single'],
                         [r['id'] for r in schedule.ordered(requests, 302)])

    def test_backfill_before_aging_then_drain_for_aged_head(self):
        requests = [self.request('blocked', 30), self.request('fits', 60, 10)]
        fits = lambda r: r['id'] == 'fits'
        self.assertEqual('fits', schedule.choose(requests, 20, fits)['id'])
        self.assertIsNone(schedule.choose(requests, 300, fits))

    def test_maven_estimates_distinguish_exact_tests_patterns_and_suites(self):
        self.assertEqual(30, schedule.maven_estimate(['-Dtest=TestCollisionLogic', 'test']))
        self.assertEqual(30.3, schedule.maven_estimate(['-Dtest=One,Two', 'test']))
        self.assertEqual(900, schedule.maven_estimate(['-Dtest=Test*', 'test']))
        self.assertEqual(900, schedule.maven_estimate(['package']))
        self.assertEqual(180, schedule.maven_estimate(['-Pguards', 'test']))
        self.assertEqual(900, schedule.maven_estimate(['-Punknown', 'test']))
        self.assertEqual(900, schedule.maven_estimate(['-Pguards', '-Punknown', 'test']))
        self.assertEqual(180, schedule.maven_estimate(['--activate-profiles', 'guards', 'test']))
        self.assertLess(schedule.plan_estimate(dict(full=False, tests=list(range(1000)), guards=True)), 900)

    def test_category_estimates_follow_selected_scope_and_guards(self):
        self.assertEqual(30, schedule.plan_estimate(dict(full=False, tests=['one'], guards=False)))
        self.assertEqual(32.7, schedule.plan_estimate(dict(full=False, tests=list(range(10)), guards=False)))
        self.assertEqual(210, schedule.plan_estimate(dict(full=False, tests=['one'], guards=True)))
        self.assertEqual(900, schedule.plan_estimate(dict(full=True, tests=[], guards=True)))

    def test_request_payload_starts_after_lock_byte_even_when_lock_moves_cursor(self):
        with tempfile.TemporaryDirectory() as tmp:
            common = Path(tmp)
            def acquire(stream):
                stream.seek(0)
                return True
            record = dict(tree=str(common / 'maven-worktree.lock'), auto=False,
                          estimate=30, enqueued=123)
            request = schedule.WaitingRequest(common, record, acquire)
            try:
                payload = request.path.read_bytes()
                self.assertEqual(b'\0', payload[:1])
                self.assertEqual(request.record, json.loads(payload[1:]))
            finally:
                request.remove()
            self.assertFalse(request.path.exists())

    def test_stale_malformed_requests_are_pruned_without_reading_or_deleting_foreign_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            common = Path(tmp)
            request = schedule.WaitingRequest(common, dict(
                tree=str(common / 'maven-worktree.lock'), auto=False,
                estimate=30, enqueued=123), lambda stream: True)
            try:
                stale = request.directory / ('a' * 32 + '.request')
                stale.write_bytes(b'incomplete publication')
                foreign = request.directory / 'user-notes.txt'
                foreign.write_text('keep')
                self.assertEqual([request.record], request.pending(lambda stream: True))
                self.assertFalse(stale.exists())
                self.assertEqual('keep', foreign.read_text())
            finally:
                request.remove()

    def test_invalid_estimates_fail_before_any_git_or_lock_access(self):
        from maven_queue import maven_slot
        for estimate in (0, -1, float('nan'), float('inf'), '30'):
            with self.subTest(estimate=estimate), self.assertRaises(ValueError):
                with maven_slot(Path('/does-not-exist'), estimate=estimate):
                    self.fail('invalid request admitted')


if __name__ == '__main__':
    unittest.main()
