"""Read-only local browser view of existing Maven queue leases and telemetry.

Inputs are the caller's shared Git directory and OS counters. Queue files are
never locked, created or pruned here. Origin: 2026-10-16 live queue view task.
"""
import argparse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from itertools import islice
import json
import math
import os
from pathlib import Path
import stat
import subprocess
import sys
import threading
import time

from maven_resources import GIB, policy, snapshot
from maven_running import FRESH_SECONDS, LEASE_NAME, LOG_NAME
from maven_schedule import AGING_SECONDS, REQUEST_NAME, ordered

DEFAULT_PORT = 8766
REFRESH_SECONDS = 2
MAX_RECORDS = 512
LOG_TAIL_BYTES = 64 * 1024
_DEFAULT = object()


def _number(value, *, positive=False, signed=False):
    if type(value) not in (int, float):
        return None
    try:
        if not math.isfinite(value):
            return None
    except OverflowError:
        return None
    return value if signed or value > 0 or (not positive and value == 0) else None


def _label(value):
    return value[:160] if isinstance(value, str) and value else None


def _identity(info):
    if hasattr(os, 'major'):
        return os.major(info.st_dev), os.minor(info.st_dev), info.st_ino
    return None, info.st_dev, info.st_ino


def _read(path, limit, *, tail=False, start=0):
    descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_NONBLOCK', 0))
    with os.fdopen(descriptor, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or path.is_symlink():
            raise ValueError('Not a regular queue file')
        offset = max(0, info.st_size - limit) if tail else start
        stream.seek(offset)
        data = stream.read(limit + (0 if tail else 1))
        if len(data) > limit:
            raise ValueError('Queue record is too large')
        if tail and offset:
            data = data.partition(b'\n')[2]
        return data, _identity(info)


def _parse_locks(data):
    locks = {}
    for line in data.decode(errors='replace').splitlines():
        fields = line.split()
        if len(fields) < 8 or fields[1] != 'FLOCK' or fields[3] not in ('READ', 'WRITE'):
            continue
        try:
            major, minor, inode = fields[5].split(':')
            identity = (int(major, 16), int(minor, 16), int(inode))
            pid = int(fields[4])
        except (ValueError, IndexError):
            continue
        locks.setdefault(identity, []).append((pid if pid > 0 else None, fields[3]))
    return locks


class ObservedLocks:
    def __init__(self, records, proc):
        self.records = records
        self.proc = proc
        self.owner_files = {}

    def get(self, identity):
        if identity in self.records:
            return self.records[identity]
        matched, unknown = [], False
        for alias, owners in self.records.items():
            if alias[2] != identity[2]:
                continue
            for pid, mode in owners:
                if pid is None:
                    unknown = True
                    continue
                if pid not in self.owner_files:
                    self.owner_files[pid] = self._files(pid)
                files = self.owner_files[pid]
                if files is None:
                    unknown = True
                elif (identity, alias) in files:
                    matched.append((pid, mode))
        return matched or (None if unknown else [])

    def _files(self, pid):
        # Overlay mounts can report a different device in stat and /proc/locks.
        # Verify that alias against the owner's actual fd and its lock record.
        try:
            entries = list(islice((self.proc / str(pid) / 'fd').iterdir(), 4097))
        except OSError:
            return None
        if len(entries) > 4096:
            return None
        files = set()
        for entry in entries:
            try:
                info = entry.stat()
                identity = _identity(info)
                with (self.proc / str(pid) / 'fdinfo' / entry.name).open('rb') as stream:
                    data = stream.read(8192)
                for line in data.splitlines():
                    if line.startswith(b'lock:\t'):
                        for alias in _parse_locks(line[6:]):
                            files.add((identity, alias))
            except FileNotFoundError:
                continue
            except OSError:
                return None
        return files


def _lock_index(proc):
    if sys.platform != 'linux':
        return None
    try:
        with (proc / 'locks').open('rb') as stream:
            data = stream.read(1024 * 1024 + 1)
        if len(data) > 1024 * 1024:
            return None
    except OSError:
        return None
    return ObservedLocks(_parse_locks(data), proc)


def _locks_for(path, locks):
    if locks is None:
        return []
    try:
        info = path.lstat()
        if not stat.S_ISREG(info.st_mode):
            return []
        return locks.get(_identity(info))
    except OSError:
        return []


def _records(directory, pattern, locks, warnings):
    try:
        if directory.is_symlink():
            warnings.append('Ignored a symlinked queue directory.')
            return []
        paths = sorted(islice(directory.iterdir(), MAX_RECORDS + 1))
    except FileNotFoundError:
        return []
    except OSError:
        warnings.append('Some queue records could not be read.')
        return []
    if len(paths) > MAX_RECORDS:
        warnings.append('Queue record display is limited to 512 entries per directory.')
    rows, stale, invalid = [], 0, 0
    for path in paths[:MAX_RECORDS]:
        if not pattern.fullmatch(path.name):
            continue
        try:
            # Byte zero is exclusively locked by live Windows leases. The
            # JSON payload starts at byte one on every supported platform.
            data, identity = _read(path, 8192, start=1)
            held = locks.get(identity) if locks is not None else None
            if held == []:
                stale += 1
                continue
            record = json.loads(data)
            if not isinstance(record, dict):
                raise ValueError('Invalid queue record')
            pid = next((pid for pid, _ in held if pid), None) if held else None
            rows.append((path.stem, record, pid, held is not None))
        except FileNotFoundError:
            continue
        except (OSError, ValueError, TypeError, RecursionError):
            invalid += 1
    if stale:
        warnings.append(f'Ignored {stale} unlocked queue record(s); files were left untouched.')
    if invalid:
        warnings.append(f'Ignored {invalid} unreadable or incomplete queue record(s).')
    return rows


def _worktree(record, pid, proc):
    name = _label(record.get('worktree'))
    if name is None and pid is not None:
        try:
            name = Path(os.readlink(proc / str(pid) / 'cwd')).name
        except OSError:
            pass
    if name is None and isinstance(record.get('tree'), str):
        directory = Path(record['tree']).parent
        if directory.parent.name == 'worktrees':
            name = directory.name
    return _label(name)


def _elapsed(now, then):
    then = _number(then)
    return round(now - then, 1) if then is not None and then <= now else None


def _job(identifier, record, pid, proc, liveness):
    return dict(id=identifier, worktree=_worktree(record, pid, proc),
                kind=_label(record.get('kind')), mode=None, pid=pid,
                waitSeconds=None, runSeconds=None, schedulingAgeSeconds=None,
                estimateSeconds=None, aged=None, retainedTree=None, rssBytes=None,
                cpuCores=None, budgetMemoryBytes=None, budgetCpuCores=None,
                usageFresh=False, position=None, liveness=liveness)


def _recent(common, warnings):
    try:
        data, _ = _read(common / LOG_NAME, LOG_TAIL_BYTES, tail=True)
    except FileNotFoundError:
        return []
    except (OSError, ValueError):
        warnings.append('Recent queue telemetry could not be read.')
        return []
    rows = []
    for line in reversed(data.splitlines()):
        try:
            record = json.loads(line)
            if not isinstance(record, dict):
                continue
            rss = _number(record.get('peakRssGiB'))
            rows.append(dict(end=_label(record.get('end')), worktree=_label(record.get('tree')),
                kind=_label(record.get('kind')), mode=_label(record.get('mode')),
                outcome=_label(record.get('outcome')), waitSeconds=_number(record.get('waitSeconds')),
                runSeconds=_number(record.get('holdSeconds')),
                peakRssBytes=_number(rss * GIB) if rss is not None else None,
                meanCores=_number(record.get('meanCores'))))
        except (ValueError, TypeError, RecursionError):
            continue
        if len(rows) == 20:
            break
    return rows


def collect_snapshot(root, *, common, policy_config=None, now=None, monotonic=None,
                     proc=Path('/proc'), resources=_DEFAULT):
    """Observe a bounded snapshot without acquiring queue or worktree locks."""
    root, common, proc = Path(root), Path(common), Path(proc)
    now = time.time() if now is None else now
    monotonic = time.monotonic() if monotonic is None else monotonic
    warnings = []
    locks = _lock_index(proc)
    liveness = 'verified' if locks is not None else 'unknown'
    if locks is None:
        warnings.append('OS lease liveness is unavailable. Unverified records may be stale.')
    resources = snapshot(proc=proc) if resources is _DEFAULT else resources
    host = dict(availableMemoryBytes=None, usableCores=None, load1=None)
    if resources is not None:
        host = dict(zip(host, resources))
    else:
        warnings.append('Host resource counters are unavailable; admission may use serial mode.')
    running, waiting, unverified = [], [], []
    for identifier, record, pid, verified in _records(common / 'maven-running', LEASE_NAME, locks, warnings):
        job = _job(identifier, record, pid, proc, 'verified' if verified else 'unknown')
        job['mode'] = record.get('mode') if record.get('mode') in ('serial', 'resource-aware') else None
        job['runSeconds'] = _elapsed(monotonic, record.get('admitted'))
        admitted, submitted = _number(record.get('admitted')), _number(record.get('submitted'))
        if admitted is not None and submitted is not None and submitted <= admitted <= monotonic:
            job['waitSeconds'] = round(admitted - submitted, 1)
        job['budgetMemoryBytes'] = _number(record.get('memoryBytes'), positive=True)
        job['budgetCpuCores'] = _number(record.get('cpuCores'), positive=True)
        updated = _number(record.get('updated'))
        fresh = updated is not None and 0 <= now - updated <= FRESH_SECONDS
        rss, cores = _number(record.get('rss')), _number(record.get('cores'))
        job['usageFresh'] = fresh and rss is not None and cores is not None
        if job['usageFresh']:
            job['rssBytes'], job['cpuCores'] = rss, cores
        (running if verified else unverified).append(job)
    candidates = []
    for identifier, record, pid, verified in _records(common / 'maven-waiters', REQUEST_NAME, locks, warnings):
        enqueued, estimate = _number(record.get('enqueued'), signed=True), _number(record.get('estimate'), positive=True)
        tree = Path(record.get('tree', '')) if isinstance(record.get('tree'), str) else Path()
        valid_tree = tree.name == 'maven-worktree.lock' and (
            tree.parent == common or tree.parent.parent == common / 'worktrees')
        if (record.get('id') != identifier or not valid_tree or enqueued is None
                or enqueued > monotonic or estimate is None or type(record.get('auto')) is not bool):
            warnings.append('Ignored a waiting record with invalid scheduling fields.')
            continue
        job = _job(identifier, record, pid, proc, 'verified' if verified else 'unknown')
        job.update(mode='resource-aware' if record['auto'] else 'serial',
                   waitSeconds=_elapsed(monotonic, record.get('submitted')),
                   schedulingAgeSeconds=round(monotonic - enqueued, 1),
                   estimateSeconds=estimate, aged=monotonic - enqueued >= AGING_SECONDS,
                   retainedTree=record.get('retained_tree') if type(record.get('retained_tree')) is bool else None)
        reservation = record.get('reservation')
        if isinstance(reservation, list) and len(reservation) == 2:
            job['budgetMemoryBytes'] = _number(reservation[0], positive=True)
            job['budgetCpuCores'] = _number(reservation[1], positive=True)
        candidates.append(dict(id=identifier, enqueued=enqueued, estimate=estimate, job=job))
    for record in ordered(candidates, monotonic):
        record['job']['position'] = len(waiting) + 1 if record['job']['liveness'] == 'verified' else None
        (waiting if record['job']['liveness'] == 'verified' else unverified).append(record['job'])
    held_slots, exclusive_held = None, None
    if locks is not None:
        slots = [(i, _locks_for(common / f'maven-slot-{i}.lock', locks)) for i in range(64)]
        held_slots = sum(bool(owners) for _, owners in slots) if all(owners is not None for _, owners in slots) else None
        compatibility = _locks_for(common / 'maven-queue.lock', locks)
        exclusive = [(pid, mode) for pid, mode in (compatibility or [])
                     if mode == 'WRITE']
        exclusive_held = bool(exclusive) if compatibility is not None else None
        owners = {job['pid'] for job in running if job['pid'] is not None}
        unmatched = 0
        for label, lock_owners in [(f'slot-{i}', owners) for i, owners in slots if owners] + [('serial', exclusive)]:
            for pid, _ in lock_owners:
                if pid is not None and pid in owners:
                    for job in running:
                        if job['pid'] == pid and job['mode'] is None:
                            job['mode'] = 'serial' if label == 'serial' else 'resource-aware'
                    continue
                job = _job(label, {}, pid, proc, liveness)
                job['mode'] = 'serial' if label == 'serial' else 'resource-aware'
                running.append(job)
                if pid is not None:
                    owners.add(pid)
                unmatched += 1
        if unmatched:
            warnings.append(f'{unmatched} admission holder(s) have no matching live usage record; timing and usage are unknown.')
    if unverified or (locks is not None and (held_slots is None or exclusive_held is None)):
        warnings.append('Some lease owners could not be verified. Their records may be stale.')
    if any(job['runSeconds'] is None for job in running) or any(job['waitSeconds'] is None for job in waiting):
        warnings.append('Older clients do not publish actual start times. Scheduling age is not elapsed waiting.')
    return dict(version=1, capturedAt=datetime.fromtimestamp(now, timezone.utc).isoformat(timespec='seconds'),
                refreshSeconds=REFRESH_SECONDS, root=str(root), liveness=liveness, host=host,
                policy=policy_config, heldSlots=held_slots, exclusiveHeld=exclusive_held,
                running=running, waiting=waiting, unverified=unverified,
                recent=_recent(common, warnings), warnings=list(dict.fromkeys(warnings)))


class SnapshotSource:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.common = Path(subprocess.check_output(
            ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'],
            cwd=self.root, text=True).strip()).resolve()
        self.config = None
        self.policy_warning = None
        self.policy_at = float('-inf')
        self.snapshot_at = float('-inf')
        self.current = None
        self.lock = threading.Lock()

    def __call__(self):
        with self.lock:
            return self.refresh()

    def refresh(self):
        now = time.monotonic()
        if now - self.snapshot_at < REFRESH_SECONDS:
            return self.current
        if now - self.policy_at >= 30:
            try:
                self.config = policy(self.root)
                self.policy_warning = None
            except (OSError, ValueError):
                self.config = None
                self.policy_warning = 'Configured resource budgets could not be read.'
            self.policy_at = now
        self.current = collect_snapshot(self.root, common=self.common, policy_config=self.config)
        if self.policy_warning:
            self.current['warnings'].append(self.policy_warning)
        self.snapshot_at = now
        return self.current


def make_server(source, *, port=DEFAULT_PORT, page=None):
    page = Path(__file__).with_name('queue_dashboard.html').read_bytes() if page is None else page

    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            self.request.settimeout(5)
            super().setup()

        def handle(self):
            try:
                super().handle()
            except (ConnectionError, TimeoutError):
                pass

        def do_GET(self):
            if self.headers.get('Host') not in (f'127.0.0.1:{self.server.server_port}',
                                                f'localhost:{self.server.server_port}'):
                self.send_error(403)
                return
            if self.path == '/':
                body, content_type = page, 'text/html; charset=utf-8'
            elif self.path == '/api/queue':
                body = json.dumps(source(), allow_nan=False).encode()
                content_type = 'application/json; charset=utf-8'
            else:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def log_message(self, format, *args):
            pass

        def end_headers(self):
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
            super().end_headers()

    return ThreadingHTTPServer(('127.0.0.1', port), Handler)


def main(argv=None):
    parser = argparse.ArgumentParser(description='Read-only Maven queue dashboard on 127.0.0.1.')
    parser.add_argument('--port', type=int, default=DEFAULT_PORT)
    args = parser.parse_args(argv)
    if not 1 <= args.port <= 65535:
        parser.error('--port must be between 1 and 65535')
    try:
        source = SnapshotSource(Path.cwd())
        with make_server(source, port=args.port) as server:
            print(f'Maven queue dashboard: http://127.0.0.1:{server.server_port}/ (Ctrl-C stops the view)', flush=True)
            server.serve_forever(poll_interval=.25)
    except KeyboardInterrupt:
        print('\nQueue dashboard stopped.', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
