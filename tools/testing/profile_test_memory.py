#!/usr/bin/env python3
"""Profile explicit JUnit tests in a repeated, isolated JVM, under the Maven queue.

Inputs: exact test class[#method] selectors and optional GC/sample controls.
Origin: 2026-10-07 Maven throughput and per-test memory investigation. Reports
are diagnostics, not Surefire validation; no instrumentation on ordinary runs.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time

from category_artifacts import run_logged
from maven_queue import handle_termination, maven_slot
from run_categories import rom_args


def selectors(root, requested):
    """Resolve exact names from source; reject accidental whole-suite patterns."""
    resolved = []
    for name in requested:
        if not re.fullmatch(r'[\w.]+(?:#[\w]+)?', name, flags=re.ASCII):
            raise ValueError(f'Use an exact class or class#method, not a pattern: {name}')
        class_name, _, method = name.partition('#')
        matches = list((root / 'src/test/java').rglob(class_name.split('.')[-1] + '.java'))
        names = []
        for source in matches:
            package = re.search(r'^package\s+([\w.]+)\s*;', source.read_text(), flags=re.MULTILINE)
            qualified = (package[1] + '.' if package else '') + source.stem
            if '.' not in class_name or qualified == class_name:
                names.append(qualified + ('#' + method if method else ''))
        if len(names) != 1:
            raise ValueError(f'No test source or ambiguous selector: {name}')
        if names[0] not in resolved:
            resolved.append(names[0])
    return resolved


def analyse(events):
    groups = {'test': {}, 'class': {}}
    floors, skipped, summary = [], [], None
    for event in events:
        kind = event.get('type')
        if kind in groups:
            key = event['id']
            row = groups[kind].setdefault(key, {
                k: event.get(k) for k in ('id', 'className', 'methodName', 'displayName')})
            for field in ('threadAllocatedBytes', 'heapDeltaBytes', 'postGcHeapDeltaBytes',
                          'elapsedSeconds', 'status', 'failure', 'gcCount', 'gcMillis'):
                row.setdefault(field, [])
                if event.get(field) is not None:
                    row[field].append(event[field])
            for field in ('peakHeapBytes', 'peakRssBytes', 'peakDirectBytes', 'peakMappedBytes'):
                value = event.get(field)
                if value is not None:
                    row[field] = max(value, row.get(field, 0))
        elif kind == 'run':
            floors.append(event)
        elif kind == 'skipped':
            skipped.append(event)
        elif kind == 'finished':
            summary = event
    return {'schema': 1, 'complete': summary is not None,
            'summary': summary, 'runFloors': floors, 'skipped': skipped,
            'tests': sorted(groups['test'].values(), key=lambda r: max(r['threadAllocatedBytes'], default=0), reverse=True),
            'classes': sorted(groups['class'].values(), key=lambda r: max(r['threadAllocatedBytes'], default=0), reverse=True)}


def diagnostic_command(root, temporary, classpath, gc, repeats, interval, selected):
    jars = [Path(p) for p in classpath.split(os.pathsep)]
    engine = next(p for p in jars if p.name.startswith('junit-platform-engine-'))
    version = engine.parent.name
    launcher = engine.parents[2] / 'junit-platform-launcher' / version / f'junit-platform-launcher-{version}.jar'
    if not launcher.is_file():
        launcher = temporary / 'libs' / launcher.name
    mockito = next(p for p in jars if p.name.startswith('mockito-core-'))
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    command = [java, '-Xmx3g', '-Xshare:off', '-javaagent:' + str(mockito)]
    if sys.platform == 'darwin':
        command.append('-XstartOnFirstThread')
    command += ['-Djava.awt.headless=true', '-Dproject.basedir=' + str(root),
                '-Djava.io.tmpdir=' + str(temporary),
                '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + str(temporary / 'lwjgl'),
                '-Dopenggf.test.tmpdir=' + str(temporary),
                '-Dopenggf.test.diagnostics=' + str(temporary / 'diagnostics'),
                '-Dopenggf.trace.reports=' + str(temporary / 'trace-reports'),
                '-Dopenggf.artifact.root=' + str(temporary / 'artifacts'),
                '-Djava.util.logging.config.file=' + str(root / 'src/test/resources/quiet-logging.properties')]
    command += rom_args(root)
    command += ['-cp', os.pathsep.join([str(temporary / 'classes'), str(root / 'target/test-classes'),
                                     str(root / 'target/classes'), classpath, str(launcher)]),
                'com.openggf.tools.MemoryProfileLauncher', str(temporary / 'events.jsonl'),
                gc, str(repeats), str(interval), *selected]
    return command, launcher


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tests', nargs='+', help='Exact class or class#method (no wildcards)')
    parser.add_argument('--gc', choices=('none', 'class', 'test'), default='none',
                        help='Extra GC at class/test boundaries; every pass also measures a post-GC floor')
    parser.add_argument('--repeat', type=int, choices=range(1, 11), default=3, metavar='1..10')
    parser.add_argument('--sample-ms', type=int, default=100)
    parser.add_argument('--max-minutes', type=float, default=20)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args(argv)
    if args.sample_ms < 10 or not 0 < args.max_minutes <= 120:
        parser.error('sample-ms must be >= 10 and max-minutes in (0, 120]')
    root = Path.cwd().resolve()
    selected = selectors(root, args.tests)
    output = args.output or root / 'target/test-memory-report.json'
    output = output.resolve()
    print(f'Memory diagnostic: {len(selected)} selectors, {args.repeat} passes, GC={args.gc}; '
          f'exclusive queue slot, stop after {args.max_minutes:g} execution minutes.', flush=True)
    temporary = None
    result = analyse([])
    code = 1
    try:
        with handle_termination(), maven_slot(root, exclusive=True, kind='profile:test-memory', estimate=600) as slot:
            (root / 'target').mkdir(exist_ok=True)
            temporary = Path(tempfile.mkdtemp(prefix='test-memory-', dir=root / 'target'))
            deadline = time.monotonic() + args.max_minutes * 60

            def execute(command):
                code = run_logged(command, root, temporary / 'execution.log', temporary,
                                  timeout=deadline - time.monotonic(), idle_timeout=600, queue_fd=slot)
                if code:
                    raise RuntimeError(f'Diagnostic command failed ({code}): {command[0]}')

            execute(['mvn', '-Dmse=off', 'dependency:build-classpath', '-DincludeScope=test',
                     '-Dmdep.outputFile=' + str(temporary / 'classpath.txt'), 'test-compile', '-DskipTests'])
            classpath = (temporary / 'classpath.txt').read_text().strip()
            command, launcher = diagnostic_command(root, temporary, classpath, args.gc,
                                                    args.repeat, args.sample_ms, selected)
            if not launcher.is_file():
                engine = next(Path(p) for p in classpath.split(os.pathsep) if Path(p).name.startswith('junit-platform-engine-'))
                execute(['mvn', '-Dmse=off', 'dependency:copy',
                         '-Dartifact=org.junit.platform:junit-platform-launcher:' + engine.parent.name,
                         '-DoutputDirectory=' + str(temporary / 'libs')])
            javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else 'javac'
            execute([javac, '--release', '21', '-cp', classpath + os.pathsep + str(launcher),
                     '-d', str(temporary / 'classes'),
                     str(Path(__file__).parent / 'java/com/openggf/tools/MemoryProfileLauncher.java')])
            code = run_logged(command, root, temporary / 'execution.log', temporary,
                              timeout=deadline - time.monotonic(), idle_timeout=600, queue_fd=slot)
            result = analyse(json.loads(line) for line in (temporary / 'events.jsonl').read_text().splitlines())
    except (RuntimeError, TimeoutError, KeyboardInterrupt, OSError) as error:
        print(str(error) or 'Cancelled: diagnostic incomplete', file=sys.stderr)
        if temporary is not None:
            for path in [temporary / 'execution.log.1', temporary / 'execution.log']:
                if path.is_file():
                    print(path.read_text(errors='replace')[-8000:], file=sys.stderr)
            if (temporary / 'events.jsonl').is_file():
                result = analyse(json.loads(line) for line in (temporary / 'events.jsonl').read_text().splitlines())
        code = 1
        result['error'] = str(error) or 'Cancelled'
    finally:
        try:
            result['commandExitCode'] = code
            result['selectors'] = selected
            result['gcMode'] = args.gc
            result['sampleMillis'] = args.sample_ms
            result['commit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
            result['dirty'] = bool(subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=root, text=True))
            result['limits'] = [
                'Diagnostic launcher, not Surefire validation or a full ordinary-suite census.',
                'Sampled JVM-global heap/RSS/direct/mapped peaks include caches, other threads and profiler overhead; short peaks can be missed.',
                'Allocated bytes count the executing thread only; worker/native allocations are excluded.',
                'Test-boundary post-GC deltas can include live fixtures; repeat-pass floors are taken after launcher execution returns.',
                'GC changes timing and allocation patterns; retained growth is a candidate requiring ownership investigation, not proof of a leak.',
                'RSS may stay high after objects are collected; LWJGL native malloc is outside the direct-buffer pool metric.']
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(result, indent=2) + '\n')
            print(f'Report: {output}; {result["summary"] or "incomplete"}', flush=True)
        finally:
            if temporary is not None:
                shutil.rmtree(temporary)
    return code if result['complete'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except ValueError as error:
        sys.exit(str(error))
