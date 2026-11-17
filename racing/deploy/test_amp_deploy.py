"""Update-wait handling in racing/deploy/amp_deploy.py against a scripted AMP; no network."""
import time
import unittest
from unittest import mock

import amp_deploy as ad

STALE_FAILED = {"Id": "old", "Name": "Downloading OpenGGF Multiplayer Master", "Origin": "Core.GetUpdates",
                "State": 3, "EndTime": "2026-11-08T10:45:11Z", "Status": False, "Reason": "503 (Service Unavailable)"}


def task(task_id, ended, ok=True):
    return {"Id": task_id, "Name": "Running Update Tasks", "Origin": "Core.UpdateApplication",
            "Speed": "Update step 3/4 - Server Download", "State": 2 if ok else 3,
            "EndTime": "2026-11-17T03:06:00Z" if ended else None, "Status": ok if ended else None,
            "Reason": None if ok else "503 (Service Unavailable)"}


class ScriptedAmp:
    """Returns successive GetUpdates task lists, keeping the last; records UpdateApplication calls."""

    def __init__(self, *task_lists):
        self.task_lists = list(task_lists)
        self.updates_started = 0

    def call(self, path, body=None, session=None):
        if path == "Core/GetStatus":
            return {"State": ad.STOPPED}
        if path == "Core/UpdateApplication":
            self.updates_started += 1
            return None
        if path == "Core/GetUpdates":
            tasks = self.task_lists.pop(0) if len(self.task_lists) > 1 else self.task_lists[0]
            return {"Tasks": tasks, "ConsoleEntries": []}
        raise AssertionError(path)


@mock.patch.object(ad.time, "sleep", lambda _s: None)
class UpdateWaitTests(unittest.TestCase):
    def deadline(self):
        return time.monotonic() + 5

    def test_a_failed_task_left_by_an_earlier_update_does_not_hold_the_wait(self):
        amp = ScriptedAmp([STALE_FAILED], [STALE_FAILED, task("new", ended=False)],
                          [STALE_FAILED, task("new", ended=True)])
        ad.update(amp, "s", self.deadline())
        self.assertEqual(1, amp.updates_started)

    def test_a_failed_download_in_this_update_fails_then_retries(self):
        amp = ScriptedAmp([], [task("a", ended=True, ok=False)], [task("a", ended=True, ok=False)],
                          [task("a", ended=True, ok=False), task("b", ended=True)])
        ad.update(amp, "s", self.deadline())
        self.assertEqual(2, amp.updates_started)

    def test_repeated_failures_report_the_failing_stage_and_reason(self):
        amp = ScriptedAmp([], [task("a", ended=True, ok=False)])
        with self.assertRaisesRegex(ad.AmpError, r"Server Download: 503"):
            ad.update(amp, "s", self.deadline(), attempts=1)

    def test_an_update_that_never_ends_times_out(self):
        amp = ScriptedAmp([task("a", ended=False)])
        with self.assertRaisesRegex(ad.AmpError, "update timed out"):
            ad.wait_for_update(amp, "s", time.monotonic() + 0.05, set())


if __name__ == "__main__":
    unittest.main()
