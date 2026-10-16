"""Check prerequisites, invocation timeouts and unrestricted retries without Maven."""
import io
from contextlib import nullcontext
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import category_control as control
import run_categories as runner


class ControlTests(unittest.TestCase):
    def plan(self):
        return dict(full=True, tests=['Example.java'], guards=True,
                    working_tree_fingerprint='state', categories=['common'], inventory_count=1)

    def test_preflight_collects_all_missing_tools_before_tests(self):
        with patch.object(control.subprocess, 'run', side_effect=FileNotFoundError('missing')) as process:
            with self.assertRaises(ValueError) as failure:
                control.preflight(Path('.'), self.plan())
        self.assertEqual(3, process.call_count)
        for name in ('Java 25', 'Lua 5.4', 'PowerShell'):
            self.assertIn(name, str(failure.exception))

    def test_versions_and_guard_environment_are_checked(self):
        outputs = ['Java version: 25.0.10, vendor: test', 'Lua 5.4.8', '']
        with patch.dict(control.os.environ, {'LUA_BIN': '/configured/lua'}), patch.object(
                control.subprocess, 'run', side_effect=[subprocess.CompletedProcess([], 0, o) for o in outputs]) as process:
            control.preflight(Path('.'), self.plan())
        self.assertEqual('/configured/lua', process.call_args_list[1].args[0][0])
        for version in ('17.0.1', '21.0.12', '24.0.2', '26', '250'):
            with self.subTest(version=version), patch.object(control.subprocess, 'run',
                    return_value=subprocess.CompletedProcess([], 0, 'Java version: ' + version)):
                with self.assertRaisesRegex(ValueError, 'Java 25'):
                    control.preflight(Path('.'), dict(guards=False))

    def test_focused_preflight_does_not_require_guard_only_tools(self):
        with patch.object(control.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'Java version: 25')) as process:
            control.preflight(Path('.'), dict(guards=False))
        self.assertEqual(1, process.call_count)

    def test_cli_prerequisite_failure_never_calls_runner(self):
        with patch.object(runner, 'worktree_build_slot', return_value=nullcontext(None)), patch.object(runner, 'preflight', side_effect=ValueError('missing tool')), patch.object(runner, 'run_plan') as run, patch('sys.stdout', new_callable=io.StringIO), patch('sys.stderr', new_callable=io.StringIO):
            self.assertEqual(2, runner.main(['--category', 'all', '--run']))
        run.assert_not_called()

    def test_timeout_keeps_incomplete_status_without_starting_guards_and_allows_retry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'run_logged', side_effect=TimeoutError('timeout')) as process:
                with self.assertRaises(TimeoutError):
                    runner.run_plan(root, self.plan())
            self.assertEqual(1, process.call_count)
            run = next((root / 'target/category-tests').iterdir())
            self.assertEqual('incomplete', json.loads((run / 'status.json').read_text())['status'])
            self.assertFalse((run / 'ordinary-tmp').exists())
            summary = dict(reports=1, tests=1, failures=0, errors=0, skipped=0)
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), patch.object(runner, 'run_logged', return_value=0) as process:
                self.assertEqual(0, runner.run_plan(root, self.plan()))
                self.assertEqual(3, process.call_count)

    def test_invocation_timeout_is_shared_between_lanes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            summary = dict(reports=1, tests=1, failures=0, errors=0, skipped=0)
            with patch.object(runner, 'tree_state', return_value='state'), patch.object(runner, 'rom_args', return_value=[]), patch.object(runner, 'summarize', side_effect=lambda _: dict(summary)), patch.object(runner, 'run_logged', return_value=0) as process, patch.object(runner.time, 'monotonic', side_effect=[0, 5, 10, 20, 30, 50]):
                self.assertEqual(0, runner.run_plan(root, self.plan(), max_minutes=1))
            self.assertEqual([60, 55, 45], [c.kwargs['timeout'] for c in process.call_args_list])


class QueueCliTests(unittest.TestCase):
    def test_cli_replans_after_worktree_wait_and_passes_retained_fd(self):
        from contextlib import contextmanager
        events = []
        plans = [dict(full=False, tests=['old'], guards=False),
                 dict(full=False, tests=['new'], guards=False)]

        @contextmanager
        def tree_slot(root):
            events.append('tree acquired')
            yield 12
            events.append('tree released')

        @contextmanager
        def slot(root, *, exclusive=False, estimate=900, kind='maven', tree_fd=None,
                 service_credit=None, reservation=None, small_job=False):
            self.assertEqual(30, estimate)
            self.assertEqual('category-batch', kind)
            self.assertIsNone(reservation)
            self.assertFalse(small_job)
            self.assertEqual(12, tree_fd)
            self.assertIsInstance(service_credit, runner.ServiceCredit)
            events.append('shared acquired')
            yield 13
            events.append('shared released')

        def plan(*args):
            events.append('plan')
            return plans.pop(0)

        def execute(root, current, *args, admission, **kwargs):
            self.assertEqual(['new'], current['tests'])
            self.assertEqual(12, kwargs['tree_fd'])
            with admission(current, 'ordinary') as fd:
                self.assertEqual(13, fd)
            return 7

        with patch.object(runner, 'worktree_build_slot', tree_slot), patch.object(runner, 'maven_slot', slot), patch.object(runner, 'make_plan', side_effect=plan), patch.object(runner, 'preflight') as prerequisites, patch.object(runner, 'run_plan', side_effect=execute) as run, patch('sys.stdout', new_callable=io.StringIO):
            self.assertEqual(7, runner.main(['--category', 'physics', '--run', '--max-minutes', '90']))
        self.assertEqual(['plan', 'tree acquired', 'plan', 'shared acquired', 'shared released', 'tree released'], events)
        self.assertEqual(90, run.call_args.args[2])
        prerequisites.assert_called_once()

    def test_two_worker_cli_requests_exclusive_only_for_ordinary(self):
        plan = dict(full=False, tests=['test'], guards=True)

        def execute(root, current, *args, admission, **kwargs):
            for lane in ('ordinary', 'guards'):
                with admission(current, lane):
                    pass
            return 0

        with patch.object(runner, 'worktree_build_slot', return_value=nullcontext(12)), \
                patch.object(runner, 'maven_slot', return_value=nullcontext(13)) as slot, \
                patch.object(runner, 'needs_exclusive', return_value=False), \
                patch.object(runner, 'make_plan', return_value=plan), \
                patch.object(runner, 'preflight'), \
                patch.object(runner, 'run_plan', side_effect=execute), \
                patch('sys.stdout', new_callable=io.StringIO):
            self.assertEqual(0, runner.main(['--category', 'physics', '--workers', '2', '--run']))
        self.assertEqual([True, False], [call.kwargs['exclusive'] for call in slot.call_args_list])
        self.assertIs(slot.call_args_list[0].kwargs['service_credit'],
                      slot.call_args_list[1].kwargs['service_credit'])


if __name__ == '__main__':
    unittest.main()
