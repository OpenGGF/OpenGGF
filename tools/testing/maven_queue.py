#!/usr/bin/env python3
"""Queue local Maven tests across linked worktrees; builds use only a worktree lock.

Usage: python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestExample test
Build without shared admission: python3 tools/testing/maven_queue.py -DskipTests package
Focused low-memory lane: add --lean before the Maven arguments (1 GiB JVM heaps).
The OS owns execution and waiting leases. Short estimated runs are preferred;
five-minute aging protects larger waiters. Origin: 2026-09-14 testing queue cleanup.
`maven_queue.py --stats` summarises recorded wait/hold/memory telemetry.
"""
from contextlib import contextmanager, ExitStack
import errno
import math
import os
from pathlib import Path
import signal
import shlex
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
def worktree_build_slot(root):
    """Protect target/ without joining the shared test scheduler.

    Retain the same local lease as queued tests, including in the Maven child.
    Other worktrees' tests, serial overrides and queue capacity cannot block it.
    """
    root = Path(root).resolve()
    git_dir = Path(subprocess.check_output(
        ['git', 'rev-parse', '--absolute-git-dir'], cwd=root, text=True).strip()).resolve()
    with ExitStack() as stack:
        lease = _open_lock(stack, git_dir / 'maven-worktree.lock')
        started = next_notice = time.monotonic()
        while not _acquire(lease):
            now = time.monotonic()
            if now >= next_notice:
                print(f'Waiting for this worktree\'s build/test lock ({now - started:.0f}s): {root}',
                      flush=True)
                next_notice = now + 30
            time.sleep(.2)
        stack.callback(_unlock, lease)
        print(f'Build-only Maven command; shared test queue bypassed: {root}', flush=True)
        yield lease.fileno()


@contextmanager
def worktree_metadata_slot(root):
    """Protect local diagnostics without reserving a JVM execution slot.

    Use the same tree/compatibility lock order as Maven. A shared compatibility
    lease permits other resource-aware worktrees but excludes legacy/serial
    clients. Windows retains exclusive compatibility locking.
    """
    root = Path(root).resolve()
    common = Path(subprocess.check_output(
        ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
        cwd=root, text=True).strip()).resolve()
    git_dir = Path(subprocess.check_output(
        ['git', 'rev-parse', '--absolute-git-dir'], cwd=root, text=True).strip()).resolve()
    with ExitStack() as stack:
        started = next_notice = time.monotonic()
        for path, shared in ((git_dir / 'maven-worktree.lock', False),
                             (common / 'maven-queue.lock', True)):
            lease = _open_lock(stack, path)
            while not _acquire(lease, shared=shared):
                now = time.monotonic()
                if now >= next_notice:
                    print(f'Waiting for diagnostic cleanup lock ({now - started:.0f}s): {root}',
                          flush=True)
                    next_notice = now + 30
                time.sleep(.2)
            stack.callback(_unlock, lease)
        yield


def _execution_leases(stack, common, request, config):
    """Probe capacity under the admission lock; retain leases in stack on success."""
    from maven_resources import GIB, snapshot, admits
    from maven_running import live_load
    from maven_schedule import WORKTREE_BUSY

    tree = _open_lock(stack, Path(request['tree']))
    if not _acquire(tree):
        return WORKTREE_BUSY
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
        active = len(slots) - len(free)
        normal = (config['memoryGiB'] * GIB, config['cpuCores'])
        credit, reserved = live_load(common, _acquire, active, normal)
        if resources is not None and free and admits(resources, config, active, credit,
                reservation=request.get('reservation', normal), reserved=reserved):
            selected = free.pop(0)
            stack.callback(_unlock, selected)
            return tree, legacy, selected
        return None
    finally:
        for slot in free:
            _unlock(slot)


@contextmanager
def maven_slot(root, *, exclusive=False, estimate=900, kind='maven', reservation=None):
    """Automatically schedule waiters, then retain OS-owned execution leases.

    Short estimates win until five-minute aging promotes arrival order. Blocked
    unaged requests allow backfilling; an aged head needing shared capacity stops
    new admissions so active jobs can drain. Busy trees retain local priority.
    Older clients retain lock compatibility but not priority.
    While running, a usage lease lets later admissions credit this job's realised
    RSS/CPU; the finished job appends one telemetry line.
    """
    from maven_resources import GIB, policy, snapshot
    from maven_running import RunningLease, UsageSampler, append_log, trim_log
    from maven_schedule import WaitingRequest, choose, aged, WORKTREE_BUSY

    if not isinstance(estimate, (int, float)) or not math.isfinite(estimate) or estimate <= 0:
        raise ValueError('Maven duration estimate must be finite and positive')
    if reservation is not None and (not isinstance(reservation, (tuple, list)) or len(reservation) != 2
            or not all(type(v) in (int, float) and math.isfinite(v) and v > 0 for v in reservation)):
        raise ValueError('Maven resource reservation must contain positive finite bytes and cores')
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
    reservation = (config['memoryGiB'] * GIB, config['cpuCores']) if reservation is None else reservation
    resources = snapshot() if mode == 'auto' and not exclusive and os.name == 'posix' else None
    auto = resources is not None and resources[1] >= 2 * reservation[1]
    with ExitStack() as stack:
        gate = _open_lock(stack, common / 'maven-admission.lock')
        started = next_notice = time.monotonic()
        request = None
        execution = None
        leases = None
        running = sampler = None
        admitted = None
        outcome = 'cancelled-waiting'
        try:
            while leases is None:
                if _acquire(gate):
                    try:
                        if request is None:
                            request = WaitingRequest(common, dict(
                                tree=str(git_dir / 'maven-worktree.lock'), auto=auto,
                                estimate=estimate, enqueued=started, reservation=reservation), _acquire)

                        def fits(record):
                            nonlocal execution, leases
                            candidate = ExitStack()
                            try:
                                found = _execution_leases(candidate, common, record, config)
                                if found is WORKTREE_BUSY:
                                    return found
                                if found and record['id'] == request.record['id']:
                                    execution, leases = candidate, found
                                    return True
                                return found is not None
                            finally:
                                if candidate is not execution:
                                    candidate.close()

                        choose(request.pending(_acquire), time.monotonic(), fits)
                        if leases is not None:
                            admitted = time.monotonic()
                            request.remove()
                            try:
                                running = RunningLease(common, reservation, _acquire)
                            except (OSError, RuntimeError):
                                running = None  # No lease only forfeits credit; admission stays valid.
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
                  f'budget {reservation[0] / GIB:g} GiB/{reservation[1]:g} cores; '
                  f'estimate {estimate:g}s; waited {admitted - started:.0f}s): {root}', flush=True)
            sampler = UsageSampler(running)
            sampler.start()
            outcome = 'interrupted'
            yield tuple(stream.fileno() for stream in leases)
            outcome = 'completed'
        finally:
            if sampler is not None:
                sampler.stop()
            if running is not None:
                running.remove()
            if execution is not None:
                execution.close()
            if request is not None:
                # A killed waiter leaves an unlocked file, pruned by the next
                # scheduler scan. Never wait for cleanup during cancellation.
                request.close()
                if _acquire(gate):
                    try:
                        request.remove()
                        trim_log(common)
                    finally:
                        _unlock(gate)
            ended = time.monotonic()
            try:
                append_log(common, dict(
                    kind=kind, tree=root.name, mode='resource-aware' if auto else 'serial',
                    estimate=estimate, outcome=outcome,
                    waitSeconds=round((admitted or ended) - started, 1),
                    holdSeconds=round(ended - admitted, 1) if admitted else 0,
                    **(sampler.summary() if sampler is not None else dict(peakRssGiB=0, meanCores=0))))
            except OSError:
                pass  # Telemetry never changes the job's result.


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


# Profiles that keep the default shape: one reused fork with the shared -Xmx3g
# surefire.argLine and no external emulator. They run within the default
# reservation; test_run_category_resources pins that shape against pom.xml.
# Benchmarks (wall-clock), test-concurrent (two forks), audio-stress (unmeasured),
# tracechaser-integration (external tools) and packaging stay exclusive.
SHARED_PROFILES = frozenset({
    'smoke', 'guards', 'ci', 'trace-replay', 'trace-segments', 'trace-replay-r7',
    'trace-diagnostics', 'fbz-routes', 'audio-reference', 'audio-local-wave'})


def build_only(args):
    """Recognize this POM's build lifecycles; uncertain invocations stay queued.

    Skip properties use the last command-line definition, as Maven does. Never
    infer that arbitrary plugin goals obey Surefire's test-skip properties.
    Custom launch arguments/POMs/profiles may introduce earlier test executions.
    """
    if os.environ.get('MAVEN_ARGS'):
        return False
    config = Path.cwd() / '.mvn/maven.config'
    if config.exists() and set(shlex.split(config.read_text(), comments=True)) - {'-Dmse=relaxed', '-Dmse=off'}:
        return False
    before_tests = {
        'clean', 'validate', 'initialize', 'generate-sources', 'process-sources',
        'generate-resources', 'process-resources', 'compile', 'process-classes',
        'generate-test-sources', 'process-test-sources', 'generate-test-resources',
        'process-test-resources', 'test-compile', 'process-test-classes'}
    after_tests = {'test', 'prepare-package', 'package', 'pre-integration-test',
                   'integration-test', 'post-integration-test', 'verify', 'install', 'deploy'}
    profiles = SHARED_PROFILES | {'tracechaser-integration', 'benchmarks', 'test-concurrent',
        'audio-stress', 'native', 'universal-jar', 'dev-run', 'lwjgl-natives-linux',
        'lwjgl-natives-macos', 'lwjgl-natives-macos-arm64', 'lwjgl-natives-windows'}
    switches = {'-B', '--batch-mode', '-q', '--quiet', '-e', '--errors', '-X', '--debug',
                '-U', '--update-snapshots', '-o', '--offline', '-ntp', '--no-transfer-progress',
                '-nsu', '--no-snapshot-updates', '-C', '--strict-checksums', '-c', '--lax-checksums'}
    properties, goals = {}, []
    tokens = iter(args)
    for token in tokens:
        if token in ('-D', '--define', '-P', '--activate-profiles', '-T', '--threads'):
            value = next(tokens, None)
            if value is None or value.startswith('-'):
                return False
            token = ('-D' if token in ('-D', '--define') else
                     '-P' if token in ('-P', '--activate-profiles') else '-T') + value
        if token.startswith(('-D', '--define=')):
            definition = token[2:] if token.startswith('-D') else token.split('=', 1)[1]
            key, separator, value = definition.partition('=')
            properties[key] = value if separator else 'true'
        elif token.startswith(('-P', '--activate-profiles=')):
            value = token[2:] if token.startswith('-P') else token.split('=', 1)[1]
            if not value or any(p.lstrip('!?-') not in profiles for p in value.split(',')):
                return False
        elif token in switches or token.startswith(('-T', '--threads=', '--color=')):
            continue
        elif token in before_tests | after_tests:
            goals.append(token)
        else:
            return False
    skip = any(properties.get(key, '').lower() == 'true' for key in ('skipTests', 'maven.test.skip'))
    return bool(goals) and (skip or all(goal in before_tests for goal in goals))


def needs_exclusive(args):
    """Unmeasured fork/heap/profile overrides retain the serial contract."""
    from maven_schedule import profiles_of
    if any(arg.startswith(('-T', '--threads'))
           or any(key in arg for key in ('argLine', 'forkCount', '-Xmx', '-Xms')) for arg in args):
        return True
    profiles, following_profile = profiles_of(args)
    return (following_profile or bool(set(profiles) - SHARED_PROFILES)
            or any(os.environ.get(key) for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS')))


def lean_command(args):
    """Bound both JVM heaps for explicit focused tests, preserving test agents.

    This is opt-in: memory-heavy tests use the normal lane. Never silently retry
    an OOM as a passing lean run. Unknown shapes cannot borrow its smaller budget.
    """
    from maven_schedule import profiles_of, maven_estimate, FULL_SECONDS
    if (needs_exclusive(args) or profiles_of(args)[0] or maven_estimate(args) == FULL_SECONDS
            or 'test' not in args or any(not arg.startswith('-') and arg not in ('clean', 'test') for arg in args)
            or any(arg.startswith(('-f', '--file', '-s', '--settings', '-gs', '--global-settings',
                                   '-t', '--toolchains', '-Dmaven.compiler.', '-Dmaven.surefire.debug'))
                   or 'reuseForks' in arg for arg in args)
            or os.environ.get('MAVEN_ARGS')):
        raise ValueError('--lean requires exact -Dtest selectors and the test goal, without profiles or JVM/build overrides')
    for name in ('jvm.config', 'maven.config'):
        path = Path.cwd() / '.mvn' / name
        # The tracked defaults only isolate Maven's temporary files and set
        # log verbosity. Any custom launch configuration needs its own budget.
        allowed = {'-Djava.io.tmpdir=target/maven-tmp'} if name == 'jvm.config' else {'-Dmse=relaxed'}
        if path.exists() and set(shlex.split(path.read_text(), comments=True)) - allowed:
            raise ValueError('--lean cannot bound custom .mvn/' + name + '; use the normal lane')
    line = '${test.cds.argLine} ${mockito.agent.argLine} -Xmx1g'
    if sys.platform == 'darwin':
        line = '-XstartOnFirstThread ' + line
    return [*args, '-Dsurefire.argLine=' + line, '-Dsurefire.forkCount=1',
            '-Dsurefire.reuseForks=true'], dict(os.environ, MAVEN_OPTS='-Xmx1g')


def main(argv=None):
    from category_artifacts import stop_process_tree
    from maven_schedule import maven_estimate, maven_kind

    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args == ['--help']:
        print(__doc__)
        return 0
    if args == ['--stats']:
        from maven_running import summarise
        print(summarise(Path(subprocess.check_output(
            ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'], text=True).strip())))
        return 0
    if args[0] == '--':
        args.pop(0)
    lean = bool(args and args[0] == '--lean')
    if lean:
        args.pop(0)
    if not args:
        raise ValueError('Supply Maven arguments after --')
    # Use the caller's worktree, including when this script lives in another one.
    exclusive = needs_exclusive(args)
    environment = None
    reservation = None
    kind = maven_kind(args, exclusive)
    estimate = maven_estimate(args)
    if lean:
        args, environment = lean_command(args)
        from maven_resources import GIB
        reservation = (4 * GIB, 4)
        kind = 'focused-lean'
    slot = (worktree_build_slot(Path.cwd()) if not lean and build_only(args) else
            maven_slot(Path.cwd(), exclusive=exclusive, estimate=estimate,
                       kind=kind, reservation=reservation))
    with slot as fd:
        with subprocess.Popen(['mvn', *args], start_new_session=(os.name == 'posix'),
                              env=environment, **inherited_slot(fd)) as process:
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
