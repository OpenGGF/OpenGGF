"""Mocked focused lean lifecycle regression; no Maven, Java, Git or queue admission.

Origin: 2026-11-04 bounded compatibility backport of main91151314217a8b2e51c471e93700b766da1e1b68.
Inherited I3a22085590a7f8d680ac5f59a417e315b3ff4b23 adds observer callbacks retained here.
"""
from contextlib import ExitStack, contextmanager
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch

import category_artifacts
import maven_activity
import maven_queue as queue
import maven_schedule
import maven_small
import run_categories


class LeanCompilationTests(unittest.TestCase):
    def setUp(self):
        self.events = []
        self.held = set()
        self.commands = []
        self.codes = [0, 0]
        self.process_error = None
        self.cleanup_error = None
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.dict(os.environ, {}, clear=True))
        self.stack.enter_context(patch.object(Path, 'cwd', return_value=Path('/component-fixture')))
        self.stack.enter_context(patch.object(maven_small, 'automatic_lean', return_value=None))
        self.slot = self.stack.enter_context(patch.object(queue, 'maven_slot', side_effect=self.admission))
        self.tree = self.stack.enter_context(patch.object(queue, 'worktree_build_slot', side_effect=lambda *a: self.lease('tree', 42)))
        self.stack.enter_context(patch.object(maven_activity, 'build_activity_slot', side_effect=lambda *a: self.lease('activity', (43,))))
        self.preflight = self.stack.enter_context(patch.object(run_categories, 'validate_preparation_contract'))
        self.state = self.stack.enter_context(patch.object(run_categories, 'tree_state', return_value='fixed-source'))
        self.stack.enter_context(patch.object(queue.subprocess, 'Popen', side_effect=self.launch))
        self.cleanup = self.stack.enter_context(patch.object(category_artifacts, 'stop_process_tree', side_effect=self.stop))

    @contextmanager
    def lease(self, name, value):
        self.events.append(name + '+')
        self.held.add(name)
        try:
            yield value
        finally:
            self.held.remove(name)
            self.events.append(name + '-')

    def admission(self, *args, **kwargs):
        self.assertNotIn('activity', self.held)
        return self.lease('queue', (42, 44) if kwargs.get('tree_fd') is not None else 44)

    def launch(self, command, **kwargs):
        self.commands.append((command, kwargs, frozenset(self.held)))
        self.events.append('launch')
        owner = self

        class Process:
            def __enter__(self):
                return self

            def wait(self):
                owner.events.append('wait')
                if owner.process_error:
                    raise owner.process_error
                return owner.codes.pop(0)

            def __exit__(self, *args):
                owner.events.append('process-')

        return Process()

    def stop(self, process):
        self.events.append('cleanup')
        if self.cleanup_error:
            raise self.cleanup_error

    def before(self):
        self.events.append(('before', frozenset(self.held)))

    def after(self, code):
        self.events.append(('after', code, frozenset(self.held)))

    def run_lean(self, extra=(), **kwargs):
        return queue.main(['--lean', '-Dmse=off', '-Dtest=example.TestOwner', *extra, 'test'], **kwargs)

    def test_compile_gets_normal_memory_and_only_prepared_execution_gets_lean_heap(self):
        stream = io.StringIO()
        self.assertEqual(0, self.run_lean(before_run=self.before, after_run=self.after, maven_stdout=stream))
        self.assertEqual(2, len(self.commands))
        preparation, execution = self.commands
        self.assertEqual(['mvn', '-Dmse=off', '-Dtest=example.TestOwner', 'test-compile'], preparation[0])
        self.assertIsNone(preparation[1]['env'])
        self.assertEqual(frozenset({'tree', 'activity'}), preparation[2])
        self.assertEqual('surefire:test', execution[0][3])
        self.assertNotIn('test', execution[0])
        self.assertEqual('-Xmx1g', execution[1]['env']['MAVEN_OPTS'])
        self.assertIn('-Xmx1g', ' '.join(execution[0]))
        self.assertEqual(frozenset({'tree', 'queue'}), execution[2])
        self.assertIs(stream, preparation[1]['stdout'])
        self.assertIs(stream, execution[1]['stdout'])
        self.assertEqual([('before', frozenset({'tree', 'activity'})),
                          ('after', 0, frozenset({'tree', 'queue'}))],
                         [event for event in self.events if isinstance(event, tuple)])
        self.assertEqual(2, self.cleanup.call_count)
        self.assertEqual((4 * 1024 ** 3, 4), self.slot.call_args.kwargs['reservation'])
        self.assertEqual(42, self.slot.call_args.kwargs['tree_fd'])
        self.assertFalse(self.slot.call_args.kwargs['small_job'])
        self.assertEqual(set(), self.held)

    def test_clean_runs_once_during_normal_memory_preparation(self):
        self.assertEqual(0, self.run_lean(extra=['clean']))
        self.assertIn('clean', self.commands[0][0])
        self.assertNotIn('clean', self.commands[1][0])

    def test_automatic_lean_preserves_qualified_budget_and_uses_same_split(self):
        bounded = queue.lean_command(['-Dmse=off', '-Dtest=example.TestOwner', 'test'])
        with patch.object(maven_small, 'automatic_lean', return_value=bounded):
            self.assertEqual(0, queue.main(['-Dmse=off', '-Dtest=example.TestOwner', 'test']))
        self.assertEqual(2, len(self.commands))
        self.assertIsNone(self.commands[0][1]['env'])
        self.assertEqual('surefire:test', self.commands[1][0][3])
        self.assertTrue(self.slot.call_args.kwargs['small_job'])
        self.assertEqual('focused-auto-lean', self.slot.call_args.kwargs['kind'])

    def test_normal_opt_out_keeps_original_single_process_and_observer_scope(self):
        self.assertEqual(0, queue.main(['--normal', '-Dmse=off', '-Dtest=example.TestOwner', 'test'],
                                      before_run=self.before, after_run=self.after))
        self.assertEqual(1, len(self.commands))
        self.assertIsNone(self.commands[0][1]['env'])
        self.assertIn('test', self.commands[0][0])
        self.preflight.assert_not_called()
        self.state.assert_not_called()
        self.assertEqual([('before', frozenset({'queue'})), ('after', 0, frozenset({'queue'}))],
                         [event for event in self.events if isinstance(event, tuple)])

    def test_failed_preparation_returns_original_code_and_launches_no_tests_or_admission(self):
        self.codes = [19]
        self.assertEqual(19, self.run_lean(before_run=self.before, after_run=self.after))
        self.assertEqual(1, len(self.commands))
        self.slot.assert_not_called()
        self.assertEqual(('after', 19, frozenset({'tree'})), [x for x in self.events if isinstance(x, tuple)][-1])
        self.assertEqual(set(), self.held)

    def test_source_change_during_compile_prevents_test_admission(self):
        self.state.side_effect = ['before', 'changed']
        with self.assertRaisesRegex(ValueError, 'during test preparation'):
            self.run_lean(after_run=self.after)
        self.assertEqual(1, len(self.commands))
        self.slot.assert_not_called()
        self.assertFalse(any(isinstance(x, tuple) for x in self.events))
        self.assertEqual(set(), self.held)

    def test_source_change_while_waiting_prevents_test_process(self):
        self.state.side_effect = ['before', 'before', 'changed']
        with self.assertRaisesRegex(ValueError, 'waiting for test admission'):
            self.run_lean()
        self.assertEqual(1, len(self.commands))
        self.assertEqual(1, self.slot.call_count)
        self.assertEqual(set(), self.held)

    def test_prepared_observer_rechecks_external_custody_at_test_admission(self):
        def admitted():
            self.assertEqual(1, len(self.commands))
            self.assertEqual(1, self.cleanup.call_count)
            self.events.append(('prepared', frozenset(self.held)))
        self.assertEqual(0, self.run_lean(before_run=self.before,
                                        before_prepared_run=admitted, after_run=self.after))
        self.assertEqual([('before', frozenset({'tree', 'activity'})),
                          ('prepared', frozenset({'tree', 'queue'})),
                          ('after', 0, frozenset({'tree', 'queue'}))],
                         [event for event in self.events if isinstance(event, tuple)])

    def test_prepared_observer_rejection_prevents_test_process_and_releases_leases(self):
        error = ValueError('external dependency changed during admission wait')
        def reject():
            self.assertEqual({'tree', 'queue'}, self.held)
            raise error
        with self.assertRaises(ValueError) as caught:
            self.run_lean(before_prepared_run=reject, after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual(1, len(self.commands))
        self.assertEqual(1, self.cleanup.call_count)
        self.assertEqual(set(), self.held)
        self.assertFalse(any(isinstance(x, tuple) for x in self.events))

    def test_prepared_observer_must_be_callable_before_any_launch(self):
        with self.assertRaisesRegex(ValueError, 'before_prepared_run must be callable'):
            self.run_lean(before_prepared_run=42)
        self.assertEqual([], self.commands)
        self.tree.assert_not_called()

    def test_normal_lane_does_not_repeat_the_admission_observer(self):
        def unexpected():
            self.fail('normal lane must use only its original admitted boundary')
        self.assertEqual(0, queue.main(['--normal', '-Dmse=off', '-Dtest=example.TestOwner', 'test'],
                                      before_run=self.before, before_prepared_run=unexpected,
                                      after_run=self.after))
        self.assertEqual(1, len(self.commands))

    def test_unpreserved_lifecycle_rejects_explicit_lean_before_any_launch(self):
        self.preflight.side_effect = ValueError('custom test-bound execution')
        with self.assertRaisesRegex(ValueError, 'custom test-bound execution'):
            self.run_lean()
        self.assertEqual([], self.commands)
        self.tree.assert_not_called()
        self.slot.assert_not_called()

    def test_unpreserved_lifecycle_keeps_automatic_request_on_normal_lane(self):
        bounded = queue.lean_command(['-Dmse=off', '-Dtest=example.TestOwner', 'test'])
        self.preflight.side_effect = ValueError('custom test-bound execution')
        with patch.object(maven_small, 'automatic_lean', return_value=bounded):
            self.assertEqual(0, queue.main(['-Dmse=off', '-Dtest=example.TestOwner', 'test']))
        self.assertEqual(1, len(self.commands))
        self.assertIsNone(self.commands[0][1]['env'])
        self.assertIn('test', self.commands[0][0])
        self.assertFalse(self.slot.call_args.kwargs['small_job'])
        self.assertIsNone(self.slot.call_args.kwargs['reservation'])

    def test_before_rejection_cleans_leases_without_launching_either_phase(self):
        error = ValueError('observer rejected source')
        def reject():
            self.before()
            raise error
        with self.assertRaises(ValueError) as caught:
            self.run_lean(before_run=reject, after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual([], self.commands)
        self.assertEqual(set(), self.held)

    def test_process_interruption_preserves_identity_cleanup_and_never_retries(self):
        error = KeyboardInterrupt('synthetic interruption')
        self.process_error = error
        with self.assertRaises(KeyboardInterrupt) as caught:
            self.run_lean(after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual(1, len(self.commands))
        self.assertEqual(1, self.cleanup.call_count)
        self.slot.assert_not_called()
        self.assertEqual(set(), self.held)

    def test_execution_failure_is_observed_after_both_process_cleanups(self):
        self.codes = [0, 23]
        self.assertEqual(23, self.run_lean(after_run=self.after))
        self.assertEqual(2, self.cleanup.call_count)
        self.assertEqual(('after', 23, frozenset({'tree', 'queue'})), [x for x in self.events if isinstance(x, tuple)][-1])
        self.assertEqual(set(), self.held)

    def test_cleanup_fault_never_publishes_completed_observation(self):
        error = OSError('synthetic cleanup failure')
        self.cleanup_error = error
        with self.assertRaises(OSError) as caught:
            self.run_lean(after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual(1, len(self.commands))
        self.assertFalse(any(isinstance(x, tuple) for x in self.events))
        self.assertEqual(set(), self.held)

    def test_execution_interruption_cleans_both_phases_without_completed_observation(self):
        error = KeyboardInterrupt('test process interrupted')
        launch = self.launch
        def interrupt_second(*args, **kwargs):
            process = launch(*args, **kwargs)
            if len(self.commands) == 2:
                self.process_error = error
            return process
        with patch.object(queue.subprocess, 'Popen', side_effect=interrupt_second):
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.run_lean(before_run=self.before, after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual(2, self.cleanup.call_count)
        self.assertEqual([('before', frozenset({'tree', 'activity'}))],
                         [event for event in self.events if isinstance(event, tuple)])
        self.assertEqual(set(), self.held)

    def test_admission_exception_releases_retained_tree_without_launch_or_retry(self):
        error = OSError('admission unavailable')
        self.slot.side_effect = error
        with self.assertRaises(OSError) as caught:
            self.run_lean(after_run=self.after)
        self.assertIs(error, caught.exception)
        self.assertEqual(1, len(self.commands))
        self.assertEqual(1, self.cleanup.call_count)
        self.assertEqual(set(), self.held)

    def test_terminal_observer_rejection_keeps_identity_after_process_cleanup(self):
        error = ValueError('terminal source attestation failed')
        def reject(code):
            self.after(code)
            self.assertEqual(2, self.cleanup.call_count)
            raise error
        with self.assertRaises(ValueError) as caught:
            self.run_lean(after_run=reject)
        self.assertIs(error, caught.exception)
        self.assertEqual(2, len(self.commands))
        self.assertEqual(set(), self.held)

    def test_split_rejects_repeated_or_reordered_goals_and_split_definitions(self):
        for args in (['test', 'clean'], ['test', 'test'], ['clean', 'clean', 'test'], ['-D', 'test'], ['--define', 'test']):
            with self.subTest(args=args), self.assertRaises(ValueError):
                queue.lean_command(['-Dtest=example.TestOwner', *args])
        self.assertEqual([], self.commands)
