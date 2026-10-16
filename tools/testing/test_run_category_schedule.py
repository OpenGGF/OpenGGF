"""Priority and aging policy, independent of host resources and wall-clock sleeps."""
import unittest
import json
from pathlib import Path
import tempfile

import maven_schedule as schedule


class SchedulingTests(unittest.TestCase):
    def request(self, name, estimate, enqueued=0):
        return dict(id=name, estimate=estimate, enqueued=enqueued)

    def test_service_credit_caps_wait_before_charging_service(self):
        credit = schedule.ServiceCredit()
        self.assertEqual(0, credit.seconds)
        credit.account(waited=3600, held=40)
        self.assertEqual(260, credit.seconds)
        self.assertEqual(3740, credit.enqueued(4000))

    def test_service_credit_accumulates_wait_and_drains_without_wait(self):
        credit = schedule.ServiceCredit()
        for _ in range(4):
            credit.account(waited=100, held=20)
        self.assertEqual(280, credit.seconds)
        credit.account(waited=0, held=100)
        self.assertEqual(180, credit.seconds)
        credit.account(waited=0, held=200)
        self.assertEqual(0, credit.seconds)

    def test_service_credit_rejects_invalid_initial_values(self):
        for seconds in (-1, 301, float('nan'), float('inf'), True, '10'):
            with self.subTest(seconds=seconds), self.assertRaises(ValueError):
                schedule.ServiceCredit(seconds)

    def test_continuation_still_yields_to_older_aged_waiter(self):
        credit = schedule.ServiceCredit(260)
        requests = [self.request('continuation', 45, credit.enqueued(1000)),
                    self.request('older', 900, 700),
                    self.request('focused', 30, 1050)]
        self.assertEqual(['older', 'continuation', 'focused'],
                         [r['id'] for r in schedule.ordered(requests, 1050)])

    def test_focused_work_wins_until_continuation_earns_back_service(self):
        credit = schedule.ServiceCredit(260)
        requests = [self.request('continuation', 45, credit.enqueued(1000)),
                    self.request('focused', 30, 1000)]
        self.assertEqual('focused', schedule.choose(requests, 1039, lambda r: True)['id'])
        self.assertEqual('continuation', schedule.choose(requests, 1040, lambda r: True)['id'])

    def test_fixed_work_simulation_bounds_category_delay_without_monopolizing_queue(self):
        def simulate(retain_credit):
            now = 0
            credit = schedule.ServiceCredit()
            batches, focused = 6, 60
            category = self.request('category', 45)
            single = self.request('focused', 30)
            category_started = 0
            events = []
            while batches or focused:
                requests = ([category] if batches else []) + ([single] if focused else [])
                selected = schedule.choose(requests, now, lambda r: True)
                start = now
                if selected['id'] == 'category':
                    now += 40
                    credit.account(waited=start - category_started, held=40)
                    batches -= 1
                    category_started = now
                    category = self.request('category', 45,
                        credit.enqueued(now) if retain_credit else now)
                else:
                    now += 30
                    focused -= 1
                    single = self.request('focused', 30, now)
                events.append((selected['id'], start, now))
            return events

        reset, retained = simulate(False), simulate(True)
        for events in (reset, retained):
            self.assertEqual(6, sum(kind == 'category' for kind, _, _ in events))
            self.assertEqual(60, sum(kind == 'focused' for kind, _, _ in events))
            self.assertEqual(2040, events[-1][2])
        category_events = [event for event in retained if event[0] == 'category']
        self.assertEqual([300, 400, 500, 600, 700, 800],
                         [start for _, start, _ in category_events])
        self.assertEqual(840, category_events[-1][2])
        self.assertEqual(2040, [end for kind, _, end in reset if kind == 'category'][-1])
        self.assertEqual(20, sum(kind == 'focused' for kind, _, end in retained if end <= 840))
        self.assertFalse(any(left[0] == right[0] == 'category'
                             for left, right in zip(retained, retained[1:])))

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

    def test_aged_busy_worktree_does_not_block_other_trees_or_lose_its_priority(self):
        requests = [dict(self.request('older', 900), tree='busy', auto=True),
                    dict(self.request('same-tree', 30, 10), tree='busy', auto=True),
                    dict(self.request('other', 30, 20), tree='free', auto=True)]
        visited = []
        def fits(request):
            visited.append(request['id'])
            return schedule.WORKTREE_BUSY if request['id'] == 'older' else True
        self.assertEqual('other', schedule.choose(requests, 350, fits)['id'])
        self.assertEqual(['older', 'other'], visited)
        self.assertEqual('older', schedule.choose(requests, 350, lambda r: True)['id'])
        requests[0]['auto'] = False
        self.assertIsNone(schedule.choose(requests, 350, fits))

    def test_vanished_foreign_worktree_does_not_admit_or_age_block_in_either_mode(self):
        for auto in (False, True):
            with self.subTest(auto=auto):
                requests = [dict(self.request('gone', 30), tree='gone', auto=auto),
                            dict(self.request('valid', 900, 301), tree='valid', auto=auto)]
                visited = []
                def fits(request):
                    visited.append(request['id'])
                    return schedule.WORKTREE_GONE if request['id'] == 'gone' else True
                self.assertEqual('valid', schedule.choose(requests, 350, fits)['id'])
                self.assertEqual(['gone', 'valid'], visited)

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

    def test_retained_tree_continuation_can_resume_past_its_aged_waiter(self):
        for auto in (False, True):
            requests = [dict(self.request('blocked', 30), tree='held', auto=auto),
                        dict(self.request('other', 30, 1), tree='free', auto=True),
                        dict(self.request('continuation', 30, 400), tree='held', auto=auto, retained_tree=True)]
            visited = []
            def fits(request):
                visited.append(request['id'])
                if request['id'] == 'blocked':
                    return schedule.WORKTREE_BUSY
                return request['id'] == 'continuation'
            # The higher-priority unrelated request still blocks on capacity.
            self.assertIsNone(schedule.choose(requests, 401, fits))
            requests.pop(1)
            visited.clear()
            self.assertEqual('continuation', schedule.choose(requests, 401, fits)['id'])
            self.assertEqual(['blocked', 'continuation'], visited)

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

    def test_invalid_reservations_fail_before_any_git_or_lock_access(self):
        from maven_queue import maven_slot
        for reservation in ((0, 4), (1, float('nan')), (True, 4), (1,), 'small'):
            with self.subTest(reservation=reservation), self.assertRaises(ValueError):
                with maven_slot(Path('/does-not-exist'), reservation=reservation):
                    self.fail('invalid budget admitted')
        with tempfile.TemporaryDirectory() as tmp:
            common = Path(tmp)
            request = schedule.WaitingRequest(common, dict(
                tree=str(common / 'maven-worktree.lock'), auto=True,
                estimate=30, enqueued=123), lambda stream: True)
            try:
                # A live legacy request lacks a reservation and remains valid.
                request.validate(request.record, request.path)
                for reservation in ([0, 4], [1, float('inf')], [True, 4], [1], 'small'):
                    with self.subTest(reservation=reservation), self.assertRaises(ValueError):
                        request.validate(dict(request.record, reservation=reservation), request.path)
            finally:
                request.remove()


if __name__ == '__main__':
    unittest.main()
