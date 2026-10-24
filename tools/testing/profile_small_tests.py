#!/usr/bin/env python3
"""Bounded ordinary-Surefire memory diagnostic for recurring small-test review.

Inputs: required normal/lean lane and <=50 exact FQCNs; optional cold compilation,
execution budget and new output path. Origin: 2026-10-24 lean-test qualification.
Linux /proc sampling only: no JVM probes, instrumentation or forced GC. This
diagnostic never edits the registry and is not memory/coverage certification.
"""
import argparse
from collections import Counter
from contextlib import ExitStack
import hashlib
import json
import os
from pathlib import Path
import re
import select
import subprocess
import sys
import tempfile
import threading
import time
import xml.etree.ElementTree as ET

from category_artifacts import RollingLog, stop_process_tree
from maven_activity import build_activity_slot
from maven_queue import handle_termination, inherited_slot, lean_command, maven_slot, worktree_build_slot
from profile_ordinary_memory import clear_compiled_outputs
from profile_test_memory import selectors
from run_categories import git, summarize

INPUT_PATHS = ('src', 'racing', 'examples', '.mvn', 'pom.xml',
               'mod-api-release-policy.properties', 'tools/testing')


def source_fingerprint(root):
    """Hash actual tracked/untracked inputs, independent of HEAD and prose edits.

    Scoped counterpart of run_categories.tree_state: src and added roots include
    fixtures/resources. Testing runtime helpers count; dashboard and Python tests
    do not. Ignored build outputs and research/report directories do not count.
    """
    names = subprocess.check_output(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z',
                                     '--', *INPUT_PATHS], cwd=root).split(b'\0')
    digest = hashlib.sha256()
    for name in sorted(set(filter(None, names))):
        relative = Path(os.fsdecode(name))
        if relative.parts[:2] == ('tools', 'testing') and (
                relative.name.startswith(('queue_dashboard', 'test_')) or relative.suffix == '.md'):
            continue
        path = root / relative
        digest.update(name + b'\0')
        if not path.exists():
            digest.update(b'missing\0')
        else:
            with path.open('rb') as stream:
                digest.update(hashlib.file_digest(stream, 'sha256').digest())
    return digest.hexdigest()


def arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--lane', choices=('normal', 'lean'), required=True)
    parser.add_argument('--test', required=True)
    parser.add_argument('--cold-compile', action='store_true')
    parser.add_argument('--max-minutes', type=float, default=10)
    parser.add_argument('--output', type=Path, default=Path('target/small-test-memory.json'))
    args = parser.parse_args(argv)
    if not 0 < args.max_minutes <= 30:
        parser.error('max-minutes must be finite and in (0,30]')
    return args


def exact_tests(root, value):
    names = value.split(',')
    if not 0 < len(names) <= 50 or any(not re.fullmatch(
            r'[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)+', name) for name in names):
        raise ValueError('Use 1–50 exact comma-separated FQCNs, without patterns or methods')
    return selectors(root, names)


def maven_command(lane, selected, temporary):
    args = ['-Dmse=off', '-B', '-Dtest=' + ','.join(selected), 'test']
    for key, directory in (('surefire.reports', 'reports'), ('test.tmpdir', 'tmp'),
                           ('test.diagnostics', 'diagnostics'), ('trace.reports', 'traces'),
                           ('artifact.root', 'artifacts')):
        args.append('-Dopenggf.' + key + '=' + str(temporary / directory))
    # Share the existing launch-shape checks (including environment/.mvn config).
    args, environment = lean_command(args)
    if lane == 'normal':
        args = [arg.replace('-Xmx1g', '-Xmx3g') if arg.startswith('-Dsurefire.argLine=') else arg for arg in args]
        environment = dict(os.environ)  # Maven keeps its ordinary default heap.
    environment.update({key: str(temporary) for key in ('TMPDIR', 'TMP', 'TEMP')})
    return ['mvn', *args], environment


def descendant_usage(root_pid, proc=None):
    """Walk owned Linux task children; fail on unreadable live process data.

    The queue's best-effort global telemetry scan intentionally suppresses read
    errors. Diagnostic evidence must distinguish those from normal process exit.
    Reading each thread's children also finds children launched by worker threads.
    """
    proc = Path('/proc') if proc is None else proc
    page = os.sysconf('SC_PAGE_SIZE')
    pending, visited, rows = [(root_pid, None)], set(), []
    while pending:
        pid, parent = pending.pop()
        if pid in visited:
            continue
        visited.add(pid)
        directory = proc / str(pid)
        try:
            text = (directory / 'stat').read_text()
            fields = text[text.rindex(')') + 2:].split()
            if parent is not None and int(fields[1]) != parent:
                continue  # Child exited and its PID was reused or reparented.
            if fields[0] == 'Z':
                continue
            start, resident = int(fields[19]), int(fields[21]) * page
            tasks = list((directory / 'task').iterdir())
        except (FileNotFoundError, ProcessLookupError):
            if pid == root_pid:
                raise RuntimeError('Sampler process disappeared from procfs')
            continue
        if parent is not None:
            rows.append((pid, start, resident))
        for task in tasks:
            try:
                children = (task / 'children').read_text().split()
            except (FileNotFoundError, ProcessLookupError):
                continue  # Thread exited between listing and reading children.
            pending.extend((int(child), pid) for child in children)
    return rows


class MemorySampler(threading.Thread):
    """Retain aggregates only; sample every 100 ms plus process-scan cost.

    RSS counts shared pages repeatedly. Peaks are sampled lower bounds, not
    allocations/retained heap. PID start ticks distinguish reused process IDs.
    """
    def __init__(self):
        super().__init__(daemon=True)
        self.done = threading.Event()
        self.processes = {}
        self.samples = self.peak_rss = self.peak_swap = self.peak_combined = 0
        self.errors = []
        self.error_count = 0

    def record(self, rows):
        rss = swap = 0
        for pid, start, role, xmx, resident, swapped in rows:
            key = (pid, start)
            if key not in self.processes and len(self.processes) >= 1024:
                raise RuntimeError('Process evidence limit (1024) exceeded')
            row = self.processes.setdefault(key, dict(pid=pid, start=start, role=role,
                xmxArgs=xmx, peakRssBytes=0, peakSwapBytes=0))
            if role != 'other':
                row.update(role=role, xmxArgs=xmx)  # Shell may exec Java with the same PID/start.
            row['peakRssBytes'] = max(row['peakRssBytes'], resident)
            row['peakSwapBytes'] = max(row['peakSwapBytes'], swapped)
            rss += resident
            swap += swapped
        self.samples += 1
        self.peak_rss = max(self.peak_rss, rss)
        self.peak_swap = max(self.peak_swap, swap)
        self.peak_combined = max(self.peak_combined, rss + swap)

    def sample(self):
        rows = []
        for pid, start, resident in descendant_usage(os.getpid()):
            directory = Path('/proc') / str(pid)
            try:
                args = [os.fsdecode(arg) for arg in (directory / 'cmdline').read_bytes().split(b'\0') if arg]
                status = (directory / 'status').read_text()
                stat = (directory / 'stat').read_text()
                if int(stat[stat.rindex(')') + 2:].split()[19]) != start:
                    continue  # Process exited and its PID was reused during this scan.
                if re.search(r'^State:\s+Z', status, re.MULTILINE):
                    continue
                swapped = re.search(r'^VmSwap:\s+(\d+)\s+kB', status, re.MULTILINE)
                if swapped is None:
                    raise ValueError(f'PID {pid}: missing VmSwap')
            except (FileNotFoundError, ProcessLookupError):
                continue  # Normal exit between enumeration and reading procfs.
            java = bool(args) and Path(args[0]).name == 'java'
            role = ('test' if java and any('surefirebooter' in arg for arg in args) else
                    'maven' if java and 'org.codehaus.plexus.classworlds.launcher.Launcher' in args else 'other')
            rows.append((pid, start, role, [arg for arg in args if arg.startswith('-Xmx')], resident, int(swapped[1]) * 1024))
        self.record(rows)

    def run(self):
        try:
            while not self.done.is_set():
                self.sample()
                self.done.wait(.1)
        except Exception as error:
            self.error_count += 1
            self.errors.append(f'{type(error).__name__}: {error}'[:512])

    def summary(self):
        roles = {row['role'] for row in self.processes.values()}
        return dict(samples=self.samples, intervalSeconds=.1, peakRssBytes=self.peak_rss,
                    peakSwapBytes=self.peak_swap, peakRssPlusSwapBytes=self.peak_combined,
                    processes=list(self.processes.values()), errors=self.errors, errorCount=self.error_count,
                    missingJvmRoles=sorted({'maven', 'test'} - roles),
                    valid=bool(self.samples and not self.error_count and {'maven', 'test'} <= roles))

    def finish(self):
        self.done.set()
        self.join()
        return self.summary()


def execute(command, environment, root, temporary, fds, seconds, result):
    """Own the watchdog directly; run_logged would reacquire shared activity."""
    log = RollingLog(temporary / 'execution.log')
    process = sampler = started = None
    stopped = False
    try:
        process = subprocess.Popen(command, cwd=root, env=environment, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, start_new_session=True, **inherited_slot(fds))
        started = time.monotonic()  # Admission, build drain and cold cleanup are outside this budget.
        sampler = MemorySampler()
        sampler.start()
        while True:
            if time.monotonic() - started >= seconds:
                result['timedOut'] = True
                stop_process_tree(process)
                stopped = True
                break
            ready, _, _ = select.select([process.stdout], [], [], .1)
            if ready:
                chunk = process.stdout.read1(65536)
                if chunk:
                    log.write(chunk)
                    continue
            if process.poll() is not None:
                break
            if ready:
                time.sleep(.1)  # EOF with a still-running parent must not spin.
        result['commandExitCode'] = process.wait()
    finally:
        try:
            if process is not None:
                if not stopped:
                    # A child may outlive an already-exited Maven parent.
                    stop_process_tree(process)
                result['commandExitCode'] = process.returncode
                process.stdout.close()
        finally:
            if started is not None:
                result['executionSeconds'] = time.monotonic() - started
            log.close()
            if sampler is not None:
                result['memory'] = sampler.finish()


def outcomes(reports, selected):
    summary = summarize(reports)
    cases = Counter()
    for path in sorted(reports.glob('TEST-*.xml')):
        for case in ET.parse(path).getroot().findall('testcase'):
            status = next((kind for kind in ('error', 'failure', 'skipped') if case.find(kind) is not None), 'passed')
            cases[(case.get('classname', ''), case.get('name', ''), status)] += 1
    counts = Counter()
    for (_, _, status), count in cases.items():
        counts[status] += count
    observed = {name.split('$')[0] for name, _, _ in cases}
    missing = sorted(set(selected) - observed)
    unexpected = sorted(observed - set(selected))
    complete = (bool(cases) and not missing and not unexpected and summary['tests'] == sum(counts.values())
                and all(summary[key] == counts[status] for key, status in
                        (('errors', 'error'), ('failures', 'failure'), ('skipped', 'skipped')))
                and not summary['skipped_cases_omitted'] and not summary['failed_cases_omitted'])
    return dict(summary, complete=complete, missingClasses=missing, unexpectedClasses=unexpected,
                cases=[dict(zip(('class', 'name', 'status', 'count'), (*key, count))) for key, count in sorted(cases.items())])


def acquire(stack, lease, timings, key):
    started = time.monotonic()
    try:
        return stack.enter_context(lease)
    finally:
        timings[key] = time.monotonic() - started


def main(argv=None):
    args = arguments(argv)
    root = Path.cwd().resolve()
    output = root / args.output
    try:
        selected = exact_tests(root, args.test)
        if sys.platform != 'linux' or not Path('/proc/self/stat').is_file():
            raise ValueError('This sampler requires Linux procfs')
        if output.exists() or output.is_symlink():
            raise ValueError('Refusing to overwrite output: ' + str(output))
        if args.cold_compile and any(output.resolve().is_relative_to(root / 'target' / name)
                                    for name in ('classes', 'test-classes', 'maven-status')):
            raise ValueError('Output cannot live under cold-compile cleanup directories')
    except ValueError as error:
        raise SystemExit(str(error)) from error
    result = dict(schema=1, lane=args.lane, selected=selected, coldCompile=args.cold_compile,
                  maxMinutes=args.max_minutes, commandExitCode=None, timedOut=False, complete=False,
                  scope='Bounded sampled diagnostic, not certification; RSS double-counts shared pages.',
                  fingerprintScope='src, racing, examples, .mvn, pom.xml, mod-api-release-policy.properties, tools/testing runtime helpers; excludes dashboard, Python tests, research/prose outside source roots and ignored outputs',
                  timings={}, errors=[])
    temporary = report = None
    with handle_termination(), ExitStack() as stack:
        try:
            result.update(commit=git(root, 'rev-parse', 'HEAD').strip(),
                          sourceInitial=source_fingerprint(root))
            tree_fd = acquire(stack, worktree_build_slot(root), result['timings'], 'treeWaitSeconds')
            output.parent.mkdir(parents=True, exist_ok=True)
            report = stack.enter_context(output.open('x'))
            (root / 'target').mkdir(exist_ok=True)
            temporary = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix='small-tests-', dir=root / 'target')))
            command, environment = maven_command(args.lane, selected, temporary)
            result['command'] = command
            slot = acquire(stack, maven_slot(root, exclusive=True, tree_fd=tree_fd,
                kind='profile:small-tests', estimate=60, reservation=(4 * 1024**3, 4) if args.lane == 'lean' else (7 * 1024**3, 8)),
                result['timings'], 'queueWaitSeconds')
            activity = acquire(stack, build_activity_slot(root, exclusive=True), result['timings'], 'buildDrainWaitSeconds')
            result['sourceBefore'] = source_fingerprint(root)
            if result['sourceBefore'] != result['sourceInitial']:
                raise ValueError('Source inputs changed while waiting; restart the diagnostic')
            if args.cold_compile:
                clear_compiled_outputs(root)
            fds = tuple(dict.fromkeys((tree_fd, *slot, *activity)))
            execute(command, environment, root, temporary, fds, args.max_minutes * 60, result)
        except (Exception, KeyboardInterrupt) as error:
            result['errors'].append(f'{type(error).__name__}: {error}'[:1024])
        finally:
            if temporary is not None:
                for key, read in (('surefire', lambda: outcomes(temporary / 'reports', selected)),
                                  ('commitAfter', lambda: git(root, 'rev-parse', 'HEAD').strip()),
                                  ('sourceAfter', lambda: source_fingerprint(root))):
                    try:
                        result[key] = read()
                    except Exception as error:
                        result['errors'].append(f'{type(error).__name__}: {error}'[:1024])
                result['sourceDrift'] = not (result.get('sourceInitial') ==
                    result.get('sourceBefore') == result.get('sourceAfter'))
                result['complete'] = bool(not result['errors'] and result['commandExitCode'] == 0
                    and not result['timedOut'] and not result['sourceDrift'] and result.get('memory', {}).get('valid')
                    and result.get('surefire', {}).get('complete') and not result['surefire']['failures'] and not result['surefire']['errors'])
                log = temporary / 'execution.log'
                if not result['complete'] and log.exists():
                    with log.open('rb') as stream:
                        stream.seek(max(0, log.stat().st_size - 12000))
                        result['logTail'] = stream.read().decode(errors='replace')
            if report is not None:
                json.dump(result, report, separators=(',', ':'))
                report.write('\n')
    if temporary is None:
        print('; '.join(result['errors']), file=sys.stderr)
        return 1
    print(f'Report: {output}; complete={result["complete"]}; Maven exit={result["commandExitCode"]}')
    return 0 if result['complete'] else 1


if __name__ == '__main__':
    sys.exit(main())
