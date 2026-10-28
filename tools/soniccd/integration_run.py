"""Bind one focused queued Maven run to a pinned installed handoff and fresh XML.

Origin: 2026-10-27 Sonic CD integration tooling. No import, retries, result cache
or retained log. Uses existing scheduler/leases; observation is not hermetic or
atomic and cannot certify native correctness. All Git-tracked bytes and visible
nonignored untracked files participate; ignored untracked/external inputs do not.
"""
from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import sys
import time

import integration_packet as packet
from integration_handoff import handoff_summary

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'testing'))
import maven_queue as queue

MAX_FILES = 50000
MAX_BYTES = 8 * 1024**3
MAX_ONE_FILE = 512 * 1024**2


def _git(root, *args):
    return subprocess.check_output(['git', '-c', 'core.fsmonitor=false',
        '-c', 'core.untrackedCache=false', *args], cwd=root, stderr=subprocess.PIPE,
        timeout=30, env={**os.environ, 'GIT_OPTIONAL_LOCKS':'0'})


def source_identity(root):
    """Hash actual bytes, including assume-unchanged files, not just git diff."""
    root, head = packet.worktree(root)
    index = _git(root, 'ls-files', '--stage', '-z')
    others = _git(root, 'ls-files', '--others', '--exclude-standard', '-z')
    entries = {}
    links = 0
    for entry in filter(None, index.split(b'\0')):
        header, name = entry.split(b'\t', 1)
        mode, blob, stage = header.split()
        packet.require(stage == b'0', 'Unmerged index cannot identify a tested source')
        packet.require(name not in entries, 'Duplicate tracked source path')
        entries[name] = mode
    for name in filter(None, others.split(b'\0')):
        packet.require(name not in entries, 'Tracked/untracked source alias')
        entries[name] = b'untracked'
    packet.require(len(entries) <= MAX_FILES, 'Source identity exceeds file limit')
    hashed = hashlib.sha256()
    hashed.update(head.encode()+b'\0'+index+b'\0')
    total = 0
    observed = []
    for name, mode in sorted(entries.items()):
        # Git output is byte-delimited: names with whitespace remain unambiguous.
        relative = os.fsdecode(name)
        path = root / relative
        packet.require(not Path(relative).is_absolute() and '..' not in Path(relative).parts,
                       'Unsafe source path in Git inventory')
        hashed.update(name+b'\0'+mode+b'\0')
        if mode == b'160000':
            links += 1
            continue  # Index pin only; initialized submodule contents require separate custody.
        packet.require(path.parent.resolve().is_relative_to(root), 'Source parent escapes worktree')
        try:
            info = path.lstat()
        except FileNotFoundError:
            hashed.update(b'absent\0')
            observed.append((path, None))
            continue
        observed.append((path, packet.signature(info)))
        hashed.update(str(stat.S_IMODE(info.st_mode)).encode()+b'\0')
        if stat.S_ISLNK(info.st_mode):
            hashed.update(b'link\0'+os.fsencode(os.readlink(path))+b'\0')
            continue  # Link text only. External target bytes must be an explicit pinned dependency.
        packet.require(stat.S_ISREG(info.st_mode), 'Unsupported source file kind')
        total += info.st_size
        packet.require(info.st_size <= MAX_ONE_FILE and total <= MAX_BYTES,
                       'Source identity exceeds byte limit')
        file_hash = hashlib.sha256()
        fd = os.open(path, os.O_RDONLY | getattr(os,'O_NOFOLLOW',0) | getattr(os,'O_NONBLOCK',0))
        with os.fdopen(fd, 'rb') as stream:
            packet.require(packet.signature(os.fstat(stream.fileno())) == packet.signature(info),
                           'Source changed before hashing')
            length = 0
            while chunk := stream.read(1024**2):
                length += len(chunk)
                packet.require(length <= MAX_ONE_FILE and length <= info.st_size,
                               'Source grew during hashing')
                file_hash.update(chunk)
            packet.require(length == info.st_size and
                           packet.signature(os.fstat(stream.fileno())) == packet.signature(info),
                           'Source changed during hashing')
        hashed.update(str(length).encode()+b'\0'+file_hash.digest())
    for path, signature in observed:
        try:
            current = packet.signature(path.lstat())
        except FileNotFoundError:
            current = None
        packet.require(current == signature, 'Source changed during identity observation')
    packet.require(_git(root,'rev-parse','HEAD').decode().strip() == head and
                   _git(root,'ls-files','--stage','-z') == index and
                   _git(root,'ls-files','--others','--exclude-standard','-z') == others,
                   'Source inventory/HEAD changed during identity observation')
    return dict(head=head, fingerprint=hashed.hexdigest(), files=len(entries), bytes=total,
                gitlinks=links)


def _report_signatures(root):
    reports = packet.canonical(root / 'target/surefire-reports')
    if not reports.exists():
        return {}
    packet.require(reports.is_dir(), 'Surefire reports is not a directory')
    paths = sorted(reports.glob('TEST-*.xml'))
    packet.require(len(paths) <= MAX_FILES, 'Too many existing XML reports')
    result = {}
    for path in paths:
        packet.canonical(path)
        info = path.lstat()
        packet.require(stat.S_ISREG(info.st_mode), 'XML report is not a regular file')
        result[path.name] = packet.signature(info)
    return result


def run_handoff(root, spec_path, *, lane='normal', execute=False):
    packet.require(lane in ('auto','normal','lean'), 'Unknown Maven lane')
    root, _ = packet.worktree(root)
    candidate = packet.canonical(Path(spec_path))
    packet.require(not candidate.is_relative_to(root), 'Handoff spec must be external')
    spec = packet.snapshot(candidate)
    ready = handoff_summary(root, spec_path, installed=True)
    packet.unchanged(spec, message='Handoff spec changed while reading its inventory')
    packet.require(ready['ready'], 'Installed handoff is blocked; inspect handoff --installed first')
    tests = ready['tests']
    methods = tests.get('methods', {})
    selectors = [name + ('#' + '+'.join(sorted(methods[name])) if name in methods else '')
                 for name in tests['classes']]
    args = ['-Dmse=off','-Dtest='+','.join(selectors)]
    args.extend('-D'+key+'='+value for key,value in sorted(tests['properties'].items()))
    args += ['test','-B']
    result = dict(version=1, id=ready['id'], stage='installed', executed=False,
                  requested_maven_args=args, lane=lane, classes=tests['classes'],
                  expected_tests=tests['expected_tests'],
                  observation='Non-atomic source/dependency/report observations; ignored untracked files, '
                    'symlink targets, initialized submodule contents, external media and runtime '
                    'environment require independent custody. '
                    'No native/API authenticity or whole-suite claim; no result cache.')
    if 'methods' in tests:
        result.update(methods=methods, expected_cases=tests['expected_cases'])
        result['observation'] += (' Exact literal case identities are observations only; '
            'method ownership, discovery and one invocation per selector/overload are not inferred.')
    if not execute:
        return result
    initial = source_identity(root)
    result.update(executed=True, status='incomplete', clean=False, issues=[], source_before=initial)
    state = {}

    def revalidate():
        packet.unchanged(spec, message='Handoff spec changed')
        current = handoff_summary(root, spec_path, installed=True)
        packet.unchanged(spec, message='Handoff spec changed during revalidation')
        packet.require(current == ready, 'Handoff declarations/dependencies changed')

    def before_run():
        revalidate()
        packet.require(source_identity(root) == initial, 'Source changed while awaiting admission')
        state['reports'] = _report_signatures(root)
        state['since'] = time.time_ns()
        result['started_at'] = datetime.now(timezone.utc).isoformat()

    def after_run(code):
        # The queue calls this only after reaping and cleanup, with its leases retained.
        result['maven_returncode'] = code
        result['completed_at'] = datetime.now(timezone.utc).isoformat()
        try:
            packet.require('since' in state, 'Missing admitted-run observation')
            revalidate()
            result['source_after'] = source_identity(root)
            packet.require(result['source_after'] == initial, 'Source changed during execution')
            current = _report_signatures(root)
            for name in tests['classes']:
                filename = 'TEST-'+name+'.xml'
                packet.require(filename in current and
                               current[filename] != state['reports'].get(filename),
                               'Requested XML was not rewritten by this invocation: '+name)
            summary = packet.junit_summary(root, tests['classes'], state['since'], tests['properties'])
            result['junit'] = summary
            case_identity_issues = []
            for suite in summary['classes']:
                if suite['counts']['tests'] != tests['expected_tests'][suite['name']]:
                    result['issues'].append('Unexpected test count: '+suite['name'])
                if suite['name'] in methods:
                    expected_ids = set(tests['expected_cases'][suite['name']])
                    observed_ids = {case['identity'] for case in suite['cases']}
                    if observed_ids != expected_ids:
                        case_identity_issues.append({'class':suite['name'],
                            'missing':sorted(expected_ids-observed_ids),
                            'unexpected':sorted(observed_ids-expected_ids)})
                version = suite['properties'].get('java.version','')
                if version.split('.')[0] != str(tests['java_major']):
                    result['issues'].append('Java major mismatch: '+suite['name'])
            if case_identity_issues:
                result['case_identity_issues'] = case_identity_issues
                raise packet.PacketError('Unexpected case identity inventory for method-selected classes')
            result['clean'] = code == 0 and summary['clean'] and not result['issues']
            result['status'] = 'passed' if result['clean'] else 'failed'
        except (ValueError, OSError, subprocess.SubprocessError, packet.ET.ParseError) as error:
            result['evidence_error'] = str(error)[:2048]

    previous = Path.cwd()
    try:
        os.chdir(root)
        with queue.handle_termination(), redirect_stdout(sys.stderr):
            queue.main(([] if lane=='auto' else ['--'+lane])+args,
                       before_run=before_run, after_run=after_run, maven_stdout=sys.stderr)
        packet.require('maven_returncode' in result, 'Queue returned without a terminal Maven observation')
    finally:
        os.chdir(previous)
    return result
