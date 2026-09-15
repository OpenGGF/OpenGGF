#!/usr/bin/env python3
"""Queue local Maven commands across linked worktrees; no task bookkeeping.

Usage: python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestExample test
The OS owns the lock, so an idle file never blocks execution. Waiting order is
chosen by the OS, not guaranteed FIFO. Origin: 2026-09-14 testing queue cleanup.
"""
from contextlib import contextmanager, ExitStack
import errno
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def _try_lock(stream, shared=False):
    if os.name == 'posix':
        import fcntl
        fcntl.flock(stream, (fcntl.LOCK_SH if shared else fcntl.LOCK_EX) | fcntl.LOCK_NB)
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


def _open_lock(stack, path):
    stream = stack.enter_context(path.open('a+b'))
    if os.fstat(stream.fileno()).st_size == 0:
        stream.write(b'\0')
        stream.flush()
    return stream


def _acquire(stream, shared=False):
    try:
        _try_lock(stream, shared)
        return True
    except OSError as error:
        if error.errno not in (errno.EACCES, errno.EAGAIN, errno.EDEADLK):
            raise
        return False


@contextmanager
def maven_slot(root, *, exclusive=False):
    """Queue with OS-owned leases; never delete persistent lock files.

    Shared ownership of the original lock permits resource-admitted runs while
    remaining mutually exclusive with serial callers and older queue versions.
    All execution leases are inherited by Maven on POSIX, including the tree lock.
    """
    from maven_resources import policy, snapshot, admits

    root = Path(root).resolve()
    common = Path(subprocess.check_output(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=root, text=True).strip())
    git_dir = Path(subprocess.check_output(
        ['git', 'rev-parse', '--absolute-git-dir'], cwd=root, text=True).strip())
    mode = os.environ.get('OPENGGF_MAVEN_QUEUE', 'auto')
    if mode not in ('auto', 'serial'):
        raise ValueError('OPENGGF_MAVEN_QUEUE must be auto or serial')
    config = policy(root)
    resources = snapshot() if mode == 'auto' and not exclusive and os.name == 'posix' else None
    # Small CPU allocations cannot accommodate two full reservations. Preserve
    # serial progress instead of making the default budget impossible to admit.
    auto = resources is not None and resources[1] >= 2 * config['cpuCores']
    with ExitStack() as stack:
        legacy = _open_lock(stack, common / 'maven-queue.lock')
        tree = _open_lock(stack, git_dir / 'maven-worktree.lock')
        gate = _open_lock(stack, common / 'maven-admission.lock')
        # Fixed namespace allows lowering maxRuns without overlooking live leases.
        slots = [_open_lock(stack, common / f'maven-slot-{i}.lock') for i in range(64)] if auto else []
        started = next_notice = time.monotonic()
        leases = None
        while leases is None:
            reason = "this worktree is busy"
            if _acquire(tree):
                reason = "an exclusive Maven run is active"
                if _acquire(legacy, shared=auto):
                    if not auto:
                        leases = (tree, legacy)
                    elif _acquire(gate):
                        free = []
                        try:
                            for slot in slots:
                                if _acquire(slot):
                                    free.append(slot)
                            resources = snapshot()
                            active = len(slots) - len(free)
                            reason = (f"{active} active; {resources[0] / 1024**3:.1f} GiB available, "
                                      f"{resources[1]:g} CPUs, load {resources[2]:.1f}"
                                      if resources else "resource counters unavailable")
                            if resources is not None and free and admits(resources, config, len(slots) - len(free)):
                                leases = (tree, legacy, free.pop(0))
                        finally:
                            for slot in free:
                                _unlock(slot)
                            _unlock(gate)
                    if leases is None:
                        _unlock(legacy)
                if leases is None:
                    _unlock(tree)
            if leases is None:
                now = time.monotonic()
                if now >= next_notice:
                    print(f'Waiting for Maven slot/resources ({now - started:.0f}s; {reason}): {root}. '
                          'It will start automatically; Ctrl-C cancels this request.', flush=True)
                    next_notice = now + 30
                time.sleep(.2)
        print(f'Maven slot acquired ({"resource-aware" if auto else "serial"}): {root}', flush=True)
        try:
            yield tuple(stream.fileno() for stream in leases)
        finally:
            for stream in reversed(leases):
                _unlock(stream)


def inherited_slot(fd):
    # Retain all leases in Maven if the Python parent is killed unexpectedly.
    if os.name != 'posix' or fd is None:
        return {}
    return {'pass_fds': fd if isinstance(fd, tuple) else (fd,)}


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


def needs_exclusive(args):
    """Unmeasured fork/heap/profile overrides retain the serial contract."""
    profiles = []
    following_profile = False
    for arg in args:
        if following_profile:
            profiles.extend(arg.split(','))
            following_profile = False
        elif arg in ('-P', '--activate-profiles'):
            following_profile = True
        elif arg.startswith('-P'):
            profiles.extend(arg[2:].split(','))
        elif arg.startswith('--activate-profiles='):
            profiles.extend(arg.split('=', 1)[1].split(','))
        if (arg.startswith(('-T', '--threads'))
                or any(key in arg for key in ('argLine', 'forkCount', '-Xmx', '-Xms'))):
            return True
    return (following_profile or bool(set(profiles) - {'smoke', 'guards'})
            or any(os.environ.get(key) for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS')))


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
    with maven_slot(Path.cwd(), exclusive=needs_exclusive(args)) as fd:
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
