#!/usr/bin/env python3
"""Queue local Maven commands across linked worktrees; no manual registration.

Usage: python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestExample test
The OS owns execution and waiting leases. Short estimated runs are preferred;
five-minute aging protects larger waiters. Origin: 2026-09-14 testing queue cleanup.
"""
from contextlib import contextmanager, ExitStack
import errno
import math
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


def _execution_leases(stack, common, request, config):
    """Probe capacity under the admission lock; retain leases in stack on success."""
    from maven_resources import snapshot, admits

    tree = _open_lock(stack, Path(request['tree']))
    if not _acquire(tree):
        return None
    stack.callback(_unlock, tree)
    legacy = _open_lock(stack, common / 'maven-queue.lock')
    if not _acquire(legacy, shared=request['auto']):
        return None
    stack.callback(_unlock, legacy)
    if not request['auto']:
        return tree, legacy
    # Fixed namespace still sees live slots if maxRuns was lowered.
    slots = [_open_lock(stack, common / f'maven-slot-{i}.lock') for i in range(64)]
    free = []
    try:
        for slot in slots:
            if _acquire(slot):
                free.append(slot)
        resources = snapshot()
        if resources is not None and free and admits(resources, config, len(slots) - len(free)):
            selected = free.pop(0)
            stack.callback(_unlock, selected)
            return tree, legacy, selected
        return None
    finally:
        for slot in free:
            _unlock(slot)


@contextmanager
def maven_slot(root, *, exclusive=False, estimate=900):
    """Automatically schedule waiters, then retain OS-owned execution leases.

    Short estimates win until five-minute aging promotes arrival order. Blocked
    unaged requests allow backfilling; an aged head stops new admissions so active
    jobs can drain. Older clients retain lock compatibility but not priority.
    """
    from maven_resources import policy, snapshot
    from maven_schedule import WaitingRequest, choose, aged

    if not isinstance(estimate, (int, float)) or not math.isfinite(estimate) or estimate <= 0:
        raise ValueError('Maven duration estimate must be finite and positive')
    root = Path(root).resolve()
    common = Path(subprocess.check_output(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=root, text=True).strip()).resolve()
    git_dir = Path(subprocess.check_output(
        ['git', 'rev-parse', '--absolute-git-dir'], cwd=root, text=True).strip()).resolve()
    mode = os.environ.get('OPENGGF_MAVEN_QUEUE', 'auto')
    if mode not in ('auto', 'serial'):
        raise ValueError('OPENGGF_MAVEN_QUEUE must be auto or serial')
    config = policy(root)
    resources = snapshot() if mode == 'auto' and not exclusive and os.name == 'posix' else None
    auto = resources is not None and resources[1] >= 2 * config['cpuCores']
    with ExitStack() as stack:
        gate = _open_lock(stack, common / 'maven-admission.lock')
        started = next_notice = time.monotonic()
        request = None
        execution = None
        leases = None
        try:
            while leases is None:
                if _acquire(gate):
                    try:
                        if request is None:
                            request = WaitingRequest(common, dict(
                                tree=str(git_dir / 'maven-worktree.lock'), auto=auto,
                                estimate=estimate, enqueued=started), _acquire)

                        def fits(record):
                            nonlocal execution, leases
                            candidate = ExitStack()
                            try:
                                found = _execution_leases(candidate, common, record, config)
                                if found and record['id'] == request.record['id']:
                                    execution, leases = candidate, found
                                    return True
                                return found is not None
                            finally:
                                if candidate is not execution:
                                    candidate.close()

                        choose(request.pending(_acquire), time.monotonic(), fits)
                        if leases is not None:
                            request.remove()
                    finally:
                        _unlock(gate)
                if leases is None:
                    now = time.monotonic()
                    if now >= next_notice:
                        priority = 'aged FIFO' if request and aged(request.record, now) else 'short-run priority'
                        print(f'Waiting for Maven slot ({now - started:.0f}s; estimate {estimate:g}s; '
                              f'{priority}): {root}. Higher-priority requests or capacity may delay admission. '
                              'It will start automatically; Ctrl-C cancels this request.', flush=True)
                        next_notice = now + 30
                    time.sleep(.2)
            print(f'Maven slot acquired ({"resource-aware" if auto else "serial"}; '
                  f'estimate {estimate:g}s): {root}', flush=True)
            yield tuple(stream.fileno() for stream in leases)
        finally:
            if execution is not None:
                execution.close()
            if request is not None:
                # A killed waiter leaves an unlocked file, pruned by the next
                # scheduler scan. Never wait for cleanup during cancellation.
                request.close()
                if _acquire(gate):
                    try:
                        request.remove()
                    finally:
                        _unlock(gate)


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
    from maven_schedule import maven_estimate

    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args == ['--help']:
        print(__doc__)
        return 0
    if args[0] == '--':
        args.pop(0)
    if not args:
        raise ValueError('Supply Maven arguments after --')
    # Use the caller's worktree, including when this script lives in another one.
    with maven_slot(Path.cwd(), exclusive=needs_exclusive(args), estimate=maven_estimate(args)) as fd:
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
