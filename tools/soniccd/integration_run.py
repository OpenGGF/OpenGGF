"""Bind one focused queued Maven run to a pinned installed handoff and fresh XML.

Origin: 2026-10-27 Sonic CD integration tooling. No import, retries, result cache
or retained log. Uses existing scheduler/leases; observation is not hermetic or
atomic and cannot certify native correctness. All Git-tracked bytes and visible
nonignored untracked files participate; ignored untracked/external inputs do not.
"""
from contextlib import nullcontext, redirect_stdout
import codecs
from datetime import datetime, timezone
import hashlib
import os
import re
from pathlib import Path
import stat
import subprocess
import sys
import threading
import time

import integration_packet as packet
from integration_handoff import handoff_summary

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'testing'))
import maven_queue as queue

MAX_FILES = 50000
MAX_BYTES = 8 * 1024**3
MAX_ONE_FILE = 512 * 1024**2
COMPACT_CASES = 20  # One global structured-detail budget, independent of progress display.
COMPACT_FIELD_CHARACTERS = 1000


MAVEN_PROGRESS_CONTENT_LINES = 64
MAVEN_PROGRESS_STATUS_LINES = 4  # Reserved within the content budget for BUILD status.
MAVEN_PROGRESS_WARNING_LINES = 8
MAVEN_PROGRESS_LINE_CHARACTERS = 512
MAVEN_PROGRESS_SUMMARY_CHARACTERS = 512
MAVEN_PROGRESS_MAX_LINES = MAVEN_PROGRESS_CONTENT_LINES + 1
_MAVEN_TRUNCATED = ' … [truncated]'
MAVEN_PROGRESS_MAX_CHARACTERS = (MAVEN_PROGRESS_CONTENT_LINES *
    (MAVEN_PROGRESS_LINE_CHARACTERS + len(_MAVEN_TRUNCATED) + 1) +
    MAVEN_PROGRESS_SUMMARY_CHARACTERS + 1)
_MAVEN_ANSI = re.compile(r'\x1b\[[0-?]*[ -/]*[@-~]')
_MAVEN_BUILD = re.compile(r'^\[(?:INFO|ERROR)\]\s+BUILD (?:SUCCESS|FAILURE)\b')
_MAVEN_INFO = re.compile(r'^\[INFO\]\s+(?:Tests run:|Results:|Compiling |Total time:|Finished at:)')


class _ConciseMavenProgress:
    """Bound the Maven child stdout display only, never test/source evidence.

    Popen needs a descriptor, so execution uses an owned pipe and a draining
    reader. The queue keeps its normal stdout/stderr and lifecycle. A display
    error is retained while the pipe drains, then raised after queue cleanup and
    callbacks; it cannot turn failed output into a positive result. No log copy
    is retained. Text counters exclude CR/LF and count Unicode code points.
    """
    def __init__(self, stream):
        self._stream = stream
        self._pending = ''
        self._line_characters = 0
        self._after_cr = False
        self._ordinary_lines = self._status_lines = self._warning_lines = 0
        self._counts = dict.fromkeys(('input_lines', 'input_characters', 'displayed_lines',
            'displayed_characters', 'suppressed_lines', 'suppressed_characters',
            'truncated_lines', 'truncated_characters'), 0)
        self._write_fd = self._read_fd = self._reader = None
        self._read_lock = threading.Lock()
        self._read_closed = False
        self._error = None
        self.closed = False

    def metadata(self):
        return dict(mode='concise', observation='Displayed Maven text is incomplete; only child stdout '
            'is filtered. Queue notices, structured diagnostics, fresh XML, verdict and source/dependency '
            'checks remain unchanged. Character counts exclude CR/LF; decoded Unicode code points '
            'are counted, with undecodable process bytes replaced. No log is retained.',
            limits=dict(content_lines=MAVEN_PROGRESS_CONTENT_LINES,
                reserved_build_status_lines=MAVEN_PROGRESS_STATUS_LINES,
                warning_lines=MAVEN_PROGRESS_WARNING_LINES,
                line_prefix_characters=MAVEN_PROGRESS_LINE_CHARACTERS,
                pending_characters=MAVEN_PROGRESS_LINE_CHARACTERS,
                summary_characters=MAVEN_PROGRESS_SUMMARY_CHARACTERS,
                printed_lines=MAVEN_PROGRESS_MAX_LINES,
                printed_characters=MAVEN_PROGRESS_MAX_CHARACTERS,
                pipe_read_bytes=4096), counts=dict(self._counts))

    def _check_open(self):
        if self.closed:
            raise ValueError('Maven progress stream is closed')

    def _append(self, text, start, end):
        self._line_characters += end-start
        remaining = MAVEN_PROGRESS_LINE_CHARACTERS-len(self._pending)
        if remaining:
            self._pending += text[start:min(end, start+remaining)]

    def _line(self):
        text, length = self._pending, self._line_characters
        self._pending = ''
        self._line_characters = 0
        counts = self._counts
        counts['input_lines'] += 1
        counts['input_characters'] += length
        marker = _MAVEN_ANSI.sub('', text).lstrip()
        status = bool(_MAVEN_BUILD.match(marker))
        warning = marker.startswith('[WARNING]')
        useful = status or warning or marker.startswith('[ERROR]') or _MAVEN_INFO.match(marker)
        available = (self._status_lines < MAVEN_PROGRESS_STATUS_LINES if status else
            self._ordinary_lines < MAVEN_PROGRESS_CONTENT_LINES-MAVEN_PROGRESS_STATUS_LINES)
        if not useful or not available or (warning and self._warning_lines >= MAVEN_PROGRESS_WARNING_LINES):
            counts['suppressed_lines'] += 1
            counts['suppressed_characters'] += length
            return
        self._stream.write(text+(_MAVEN_TRUNCATED if length > len(text) else '')+'\n')
        # Like Maven's former direct descriptor, progress is visible while running.
        self._stream.flush()
        self._status_lines += int(status)
        self._ordinary_lines += int(not status)
        self._warning_lines += int(warning)
        counts['displayed_lines'] += 1
        counts['displayed_characters'] += len(text)
        if length > len(text):
            counts['truncated_lines'] += 1
            counts['truncated_characters'] += length-len(text)

    def write(self, text):
        self._check_open()
        if not isinstance(text, str):
            raise TypeError('Maven progress requires text')
        start = 0
        for separator in re.finditer(r'[\r\n]', text):
            end = separator.start()
            if end > start:
                self._append(text, start, end)
                self._after_cr = False
            if not (self._after_cr and separator[0] == '\n'):
                self._line()
            self._after_cr = separator[0] == '\r'
            start = end+1
        if start < len(text):
            self._append(text, start, len(text))
            self._after_cr = False
        return len(text)

    def flush(self):
        self._check_open()
        self._stream.flush()  # A flush does not finalize a split/partial input line.

    def fileno(self):
        self._check_open()
        if self._write_fd is None:
            raise ValueError('Maven progress pipe is not open')
        return self._write_fd

    def _drain(self):
        decoder = codecs.getincrementaldecoder('utf-8')(errors='replace')
        try:
            while chunk := self._read_chunk():
                if self._error is None:
                    try:
                        self.write(decoder.decode(chunk))
                    except BaseException as error:
                        self._error = error
                # After an output error, keep draining so the child and terminal
                # callback cannot deadlock behind a full pipe. Never retry output.
            if self._error is None:
                self.write(decoder.decode(b'', final=True))
        except BaseException as error:
            self._error = self._error or error
        finally:
            self._close_read_pipe()

    def _read_chunk(self):
        # Serialize reads with startup cancellation. Closing the writer first
        # releases a blocked read; a late-starting reader sees retired ownership.
        with self._read_lock:
            return b'' if self._read_closed else os.read(self._read_fd, 4096)

    def _close_read_pipe(self):
        with self._read_lock:
            if not self._read_closed and self._read_fd is not None:
                self._read_closed = True
                os.close(self._read_fd)

    def __enter__(self):
        self._check_open()
        if self._reader is not None:
            raise ValueError('Maven progress pipe is already open')
        self._read_fd, self._write_fd = os.pipe()
        try:
            self._reader = threading.Thread(target=self._drain, name='soniccd-maven-progress')
            self._reader.start()
        except BaseException as original:
            try:
                try:
                    os.close(self._write_fd)
                    self._write_fd = None
                finally:
                    self._close_read_pipe()
                if self._reader is not None and self._reader.ident is not None:
                    self._reader.join()
            except BaseException as cleanup_error:
                original.add_note('Maven progress startup cleanup failed: '+str(cleanup_error))
            finally:
                # A possibly late native launch owns no pipe and cannot be
                # retargeted by admitting this failed sink a second time.
                self.closed = True
                self._reader = None
            raise
        return self

    def close(self):
        if self.closed:
            return
        try:
            if self._write_fd is not None:
                os.close(self._write_fd)
                self._write_fd = None
            if self._reader is not None:
                self._reader.join()  # Queue has already reaped/stopped its child tree.
            if self._error is not None:
                raise self._error
            if self._line_characters:
                self._line()
            counts = self._counts
            summary = ('[maven-progress concise] displayed text is incomplete; '+
                '; '.join(name+'='+str(counts[name]) for name in (
                    'suppressed_lines', 'suppressed_characters', 'truncated_lines', 'truncated_characters')))
            if len(summary) > MAVEN_PROGRESS_SUMMARY_CHARACTERS:
                summary = ('[maven-progress concise] displayed text is incomplete; exact suppression/'
                           'truncation counts exceed summary space; see result JSON')
            self._stream.write(summary+'\n')
            self._stream.flush()
        finally:
            self.closed = True  # The borrowed stderr is never closed.

    def __exit__(self, kind, error, traceback):
        try:
            self.close()
        except BaseException as output_error:
            if error is None:
                raise
            # Preserve cancellation/queue exceptions while exposing a second
            # cleanup/output failure, rather than replacing the original cause.
            error.add_note('Maven progress cleanup/output failed: '+str(output_error))
        return False


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


def _compact_result(result):
    """Project only after verification; never use omitted details for decisions.

    Integrity issues remain complete, so this bounds ordinary case expansion,
    not total output bytes. No report, diagnostic or full-result copy is saved.
    """
    result = dict(result)
    omitted = dict(passed=0, failure=0, error=0, skipped=0)
    output = dict(mode='compact', full_result_status=result.get('status', 'not-executed'),
                  full_result_clean=result.get('clean'),
                  limits=dict(nonpassing_cases=COMPACT_CASES,
                              case_field_characters=COMPACT_FIELD_CHARACTERS),
                  omitted_cases=omitted, expected_case_identities_omitted={})
    if 'expected_cases' in result:
        output['expected_case_identities_omitted'] = {
            name:len(identities) for name,identities in result.pop('expected_cases').items()}
    if 'junit' in result:
        summary = result['junit'] = dict(result['junit'])
        classes, remaining = [], COMPACT_CASES
        for original in summary['classes']:
            suite = dict(original)
            suite['cases'] = []
            suite['omitted_cases'] = dict.fromkeys(omitted, 0)
            for case in original['cases']:
                if case['status'] == 'passed' or remaining == 0:
                    suite['omitted_cases'][case['status']] += 1
                    omitted[case['status']] += 1
                    continue
                detail, truncated = dict(case), {}
                for key, value in case.items():
                    if isinstance(value, str) and len(value) > COMPACT_FIELD_CHARACTERS:
                        detail[key] = value[:COMPACT_FIELD_CHARACTERS]
                        truncated[key] = len(value)-COMPACT_FIELD_CHARACTERS
                if truncated:
                    detail['truncated_characters'] = truncated
                suite['cases'].append(detail)
                remaining -= 1
            classes.append(suite)
        summary['classes'] = classes
    result['output'] = output
    return result


def run_handoff(root, spec_path, *, lane='normal', execute=False, compact_output=False,
                maven_progress='full'):
    packet.require(lane in ('auto','normal','lean'), 'Unknown Maven lane')
    packet.require(maven_progress in ('full', 'concise'), 'Unknown Maven progress mode')
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
    progress = _ConciseMavenProgress(sys.stderr) if maven_progress == 'concise' else None
    if progress is not None:
        result['maven_progress'] = progress.metadata()
    if not execute:
        return _compact_result(result) if compact_output else result
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
            with progress if progress is not None else nullcontext(sys.stderr) as maven_output:
                queue.main(([] if lane=='auto' else ['--'+lane])+args,
                           before_run=before_run, after_run=after_run, maven_stdout=maven_output)
        if progress is not None:
            result['maven_progress'] = progress.metadata()
        packet.require('maven_returncode' in result, 'Queue returned without a terminal Maven observation')
    finally:
        os.chdir(previous)
    return _compact_result(result) if compact_output else result
