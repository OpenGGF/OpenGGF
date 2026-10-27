"""Optional queue observers run inside admission; no Java or Maven is launched.

Origin: 2026-10-27 Sonic CD source-bound integration tooling.
"""
from contextlib import ExitStack, contextmanager
import unittest
from unittest.mock import patch

import category_artifacts
import maven_activity
import maven_queue as queue
import maven_schedule


class QueueObserverTests(unittest.TestCase):
    def setUp(self):
        self.events = []
        self.held = set()
        self.returncode = 0
        self.wait_error = None
        self.launch_error = None
        self.cleanup_error = None
        self.building = False
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(queue, "needs_exclusive", return_value=False))
        self.stack.enter_context(patch.object(queue, "build_only", side_effect=lambda _: self.building))
        self.stack.enter_context(patch.object(maven_schedule, "maven_kind", return_value="synthetic"))
        self.stack.enter_context(patch.object(maven_schedule, "maven_estimate", return_value=30))
        self.stack.enter_context(patch.object(queue, "maven_slot", side_effect=lambda *a, **k: self.lease("queue")))
        self.stack.enter_context(patch.object(queue, "worktree_build_slot", side_effect=lambda *a, **k: self.lease("tree")))
        self.stack.enter_context(patch.object(maven_activity, "build_activity_slot", side_effect=lambda *a, **k: self.lease("activity")))
        self.stack.enter_context(patch.object(queue.subprocess, "Popen", side_effect=self.launch))
        self.stack.enter_context(patch.object(category_artifacts, "stop_process_tree", side_effect=self.cleanup))

    @contextmanager
    def lease(self, name):
        self.events.append(name + " acquired")
        self.held.add(name)
        try:
            yield (41,)
        finally:
            self.held.remove(name)
            self.events.append(name + " released")

    def launch(self, command, **kwargs):
        self.events.append(("launch", command))
        if self.launch_error:
            raise self.launch_error
        owner = self

        class Process:
            def __enter__(self):
                owner.events.append("process entered")
                return self

            def wait(self):
                owner.events.append("wait returned" if owner.wait_error is None else "wait interrupted")
                if owner.wait_error:
                    raise owner.wait_error
                return owner.returncode

            def __exit__(self, *args):
                owner.events.append("process exited")

        return Process()

    def cleanup(self, process):
        self.events.append("process cleaned")
        if self.cleanup_error:
            raise self.cleanup_error

    def before(self):
        self.events.append(("before", frozenset(self.held)))

    def after(self, returncode):
        self.events.append(("after", returncode, frozenset(self.held)))

    def run_queue(self, **callbacks):
        args = ["--normal", "-Dmse=off", "-Dtest=example.TestOwner", "test", "-B"]
        return queue.main(args, **callbacks)

    def test_callbacks_bracket_only_terminal_maven_inside_queue_lease(self):
        self.assertEqual(self.run_queue(before_run=self.before, after_run=self.after), 0)
        self.assertEqual(self.events, [
            "queue acquired", ("before", frozenset({"queue"})),
            ("launch", ["mvn", "-Dmse=off", "-Dtest=example.TestOwner", "test", "-B"]),
            "process entered", "wait returned", "process cleaned", "process exited",
            ("after", 0, frozenset({"queue"})), "queue released"])
        self.assertEqual(self.held, set())

    def test_build_callbacks_keep_both_tree_and_activity_admission(self):
        self.building = True
        self.assertEqual(self.run_queue(before_run=self.before, after_run=self.after), 0)
        self.assertEqual(self.events[:3], ["tree acquired", "activity acquired",
                         ("before", frozenset({"tree", "activity"}))])
        self.assertEqual(self.events[-3:], [
            ("after", 0, frozenset({"tree", "activity"})), "activity released", "tree released"])

    def test_before_rejection_prevents_launch_and_releases_admission(self):
        error = ValueError("source changed while queued")

        def reject():
            self.before()
            raise error

        with self.assertRaises(ValueError) as caught:
            self.run_queue(before_run=reject, after_run=self.after)
        self.assertIs(caught.exception, error)
        self.assertEqual(self.events, ["queue acquired", ("before", frozenset({"queue"})), "queue released"])

    def test_after_rejection_propagates_after_cleanup_and_releases_admission(self):
        error = ValueError("source changed during execution")

        def reject(returncode):
            self.after(returncode)
            raise error

        with self.assertRaises(ValueError) as caught:
            self.run_queue(after_run=reject)
        self.assertIs(caught.exception, error)
        self.assertEqual(self.events[-4:], ["process cleaned", "process exited",
                         ("after", 0, frozenset({"queue"})), "queue released"])

    def test_nonzero_maven_exit_is_observed_and_returned_unchanged(self):
        self.returncode = 17
        self.assertEqual(self.run_queue(after_run=self.after), 17)
        self.assertEqual(self.events[-2:], [("after", 17, frozenset({"queue"})), "queue released"])

    def test_default_call_preserves_original_process_and_admission_behavior(self):
        self.returncode = 23
        self.assertEqual(self.run_queue(), 23)
        self.assertEqual(self.events, ["queue acquired",
            ("launch", ["mvn", "-Dmse=off", "-Dtest=example.TestOwner", "test", "-B"]),
            "process entered", "wait returned", "process cleaned", "process exited", "queue released"])

    def test_noncallable_observers_fail_before_scheduling(self):
        for name in ("before_run", "after_run"):
            for value in (False, 1, "callback"):
                with self.subTest(name=name, value=value), self.assertRaises(ValueError):
                    self.run_queue(**{name: value})
                self.assertEqual(self.events, [])

    def test_popen_failure_does_not_publish_completed_observation(self):
        error = OSError("synthetic launch failure")
        self.launch_error = error
        with self.assertRaises(OSError) as caught:
            self.run_queue(before_run=self.before, after_run=self.after)
        self.assertIs(caught.exception, error)
        self.assertEqual(self.events[-1], "queue released")
        self.assertFalse(any(isinstance(e, tuple) and e[0] == "after" for e in self.events))

    def test_interrupted_wait_cleans_process_without_completed_observation(self):
        self.wait_error = KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            self.run_queue(after_run=self.after)
        self.assertEqual(self.events[-4:], ["wait interrupted", "process cleaned", "process exited", "queue released"])
        self.assertEqual(self.held, set())

    def test_failed_wait_cleans_process_without_completed_observation(self):
        error = OSError("synthetic wait failure")
        self.wait_error = error
        with self.assertRaises(OSError) as caught:
            self.run_queue(after_run=self.after)
        self.assertIs(caught.exception, error)
        self.assertEqual(self.events[-4:], ["wait interrupted", "process cleaned", "process exited", "queue released"])

    def test_cleanup_failure_does_not_publish_completed_observation(self):
        error = OSError("synthetic cleanup failure")
        self.cleanup_error = error
        with self.assertRaises(OSError) as caught:
            self.run_queue(after_run=self.after)
        self.assertIs(caught.exception, error)
        self.assertEqual(self.events[-3:], ["process cleaned", "process exited", "queue released"])
        self.assertEqual(self.held, set())

    def test_one_observer_can_be_selected_without_requiring_the_other(self):
        self.assertEqual(self.run_queue(before_run=self.before), 0)
        self.assertEqual(sum(isinstance(e, tuple) and e[0] == "before" for e in self.events), 1)
        self.assertFalse(any(isinstance(e, tuple) and e[0] == "after" for e in self.events))


if __name__ == "__main__":
    unittest.main()
