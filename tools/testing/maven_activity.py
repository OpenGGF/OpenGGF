"""Coordinate actual builds with isolated measurements across linked worktrees.

Worktree ownership may span idle category gaps. This lease covers only execution;
measurements hold it exclusively after gaining exclusive test admission. Origin:
2026-10-16 queue throughput follow-up, benchmark drain/retry loop investigation.
"""
from contextlib import contextmanager, ExitStack
import os
from pathlib import Path
import subprocess
import time


@contextmanager
def build_activity_slot(root, *, exclusive=False, timings=None):
    """Yield inheritable leases; never call test admission while holding these.

    The intent mutex closes the entrance to new builds while a measurement waits
    for existing builds to finish. Builds release intent as soon as their shared
    activity lease is held. Idle worktree holders do not participate.
    """
    from maven_queue import _open_lock, _acquire, _unlock

    if os.name != 'posix':
        if exclusive:
            raise ValueError('Isolated measurements require POSIX activity leases')
        yield ()
        return
    common = Path(subprocess.check_output(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=root, text=True).strip()).resolve()
    with ExitStack() as stack:
        intent = _open_lock(stack, common / 'maven-build-intent.lock')
        activity = _open_lock(stack, common / 'maven-build-activity.lock')
        started = next_notice = time.monotonic()
        try:
            for lease, shared in ((intent, False), (activity, not exclusive)):
                while not _acquire(lease, shared=shared):
                    now = time.monotonic()
                    if now >= next_notice:
                        reason = 'active builds to finish' if exclusive else 'isolated measurement to finish'
                        print(f'Waiting for {reason} ({now - started:.0f}s): {root}', flush=True)
                        next_notice = now + 30
                    time.sleep(.2)
        finally:
            if timings is not None:
                timings['build_wait_seconds'] = time.monotonic() - started
        stack.callback(_unlock, activity)
        if exclusive:
            stack.callback(_unlock, intent)
            yield intent.fileno(), activity.fileno()
        else:
            _unlock(intent)
            yield (activity.fileno(),)
