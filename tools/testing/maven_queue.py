#!/usr/bin/env python3
"""Serialize local Maven commands across linked worktrees; no task bookkeeping.

Usage: python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestExample test
The OS owns the lock, so an idle file never blocks execution. Waiting order is
chosen by the OS, not guaranteed FIFO. Origin: 2026-09-14 testing queue cleanup.
"""
from contextlib import contextmanager
import errno
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def _try_lock(stream):
    if os.name == 'posix':
        import fcntl
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
    else:
        import msvcrt
        stream.seek(0)
        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)


def _unlock(stream):
    if os.name == 'posix':
        import fcntl
        fcntl.flock(stream, fcntl.LOCK_UN)
    else:
        import msvcrt
        stream.seek(0)
        msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)


@contextmanager
def maven_slot(root):
    """Wait automatically for the shared repository slot; release on exit.

    Never unlink the lock file: replacing its inode would let two processes own
    different locks. No PID/receipt is read to decide whether another run is live.
    """
    common = subprocess.check_output(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=root, text=True).strip()
    lock = Path(common) / 'maven-queue.lock'
    with lock.open('a+b') as stream:
        # Windows byte-range locking needs a byte even on the first launch.
        if os.fstat(stream.fileno()).st_size == 0:
            stream.write(b'\0')
            stream.flush()
        started = time.monotonic()
        next_notice = started
        while True:
            try:
                _try_lock(stream)
                break
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN, errno.EDEADLK):
                    raise
                now = time.monotonic()
                if now >= next_notice:
                    print(f'Waiting for Maven slot ({now - started:.0f}s): {root}. '
                          'It will start automatically; Ctrl-C cancels this request.', flush=True)
                    next_notice = now + 30
                time.sleep(.2)
        print(f'Maven slot acquired: {root}', flush=True)
        try:
            yield stream.fileno()
        finally:
            _unlock(stream)


def inherited_slot(fd):
    # Retain the lock in Maven if the Python parent is killed unexpectedly.
    # Normal cancellation reaps the process tree before releasing the slot.
    return {'pass_fds': (fd,)} if os.name == 'posix' and fd is not None else {}


@contextmanager
def handle_termination():
    """Route CLI termination through subprocess/queue cleanup, like Ctrl-C."""
    previous = signal.getsignal(signal.SIGTERM)

    def terminate(signum, frame):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, terminate)
    try:
        yield
    finally:
        signal.signal(signal.SIGTERM, previous)


def main(argv=None):
    from category_artifacts import stop_process_tree

    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args == ['--help']:
        print(__doc__)
        return 0
    if args[0] == '--':
        args.pop(0)
    if not args:
        raise ValueError('Supply Maven arguments after --')
    # Use the caller's worktree, including when this script lives in another one.
    with maven_slot(Path.cwd()) as fd:
        with subprocess.Popen(['mvn', *args], start_new_session=(os.name == 'posix'),
                              **inherited_slot(fd)) as process:
            try:
                return process.wait()
            finally:
                stop_process_tree(process)


if __name__ == '__main__':
    try:
        with handle_termination():
            sys.exit(main())
    except KeyboardInterrupt:
        print('Maven request cancelled; validation is incomplete.', file=sys.stderr)
        sys.exit(130)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        sys.exit(2)
