"""Running-job usage leases and bounded queue telemetry for the Maven queue.

A job that holds an execution slot publishes its process-tree RSS and recent CPU
in an OS-leased `.git/maven-running/*.lease` record. Admission credits that
realised usage against the job's own reservation instead of counting it twice
(once in MemAvailable/load, once as a full reservation). A record whose lease is
not held, is stale or cannot be parsed earns no credit, so failure is conservative.
Records never authorize execution: slot locks do that.

Completed jobs append one line to `.git/maven-queue-log.jsonl` (wait, hold, peak
RSS, mean CPU; no arguments, results or pass/fail). `maven_queue.py --stats`
summarises it. Origin: 2026-09-24 Maven queue throughput task.
"""
from collections import deque
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import re
import threading
import time
import uuid

SAMPLE_SECONDS = 2
FRESH_SECONDS = 15
CPU_WINDOW_SECONDS = 60
LOG_NAME = 'maven-queue-log.jsonl'
LOG_LIMIT_BYTES = 512 * 1024
LEASE_NAME = re.compile(r'[0-9a-f]{32}\.lease')


class RunningLease:
    """Created and removed under the admission lock; updated by its sampler."""
    def __init__(self, common, reservation, acquire):
        self.directory = common / 'maven-running'
        self.directory.mkdir(exist_ok=True)
        self.path = self.directory / (uuid.uuid4().hex + '.lease')
        self.stream = self.path.open('x+b')
        self.lock = threading.Lock()
        self.memory, self.cores = reservation
        try:
            self.stream.write(b'\0')
            self.stream.flush()
            if not acquire(self.stream):
                raise RuntimeError('New running lease could not acquire its lock')
            # No credit until the sampler has measured the young job.
            self.publish(0, 0)
        except BaseException:
            self.stream.close()
            self.path.unlink(missing_ok=True)
            raise

    def publish(self, rss, cores):
        record = json.dumps(dict(memoryBytes=self.memory, cpuCores=self.cores,
                                 rss=rss, cores=cores, updated=time.time())).encode()
        with self.lock:
            if self.stream.closed:
                return
            self.stream.seek(1)
            self.stream.write(record)
            self.stream.truncate()
            self.stream.flush()

    def remove(self):
        with self.lock:
            self.stream.close()
        self.path.unlink(missing_ok=True)


def live_credit(common, acquire, now=None):
    """Sum usage already realised by live running jobs, capped per reservation.

    Requires the admission lock. Unlocked leases belong to dead holders and are
    pruned; unreadable or stale live records earn no credit.
    """
    now = time.time() if now is None else now
    directory = common / 'maven-running'
    memory = cores = 0
    if not directory.is_dir():
        return 0, 0
    for path in directory.iterdir():
        if not LEASE_NAME.fullmatch(path.name) or path.is_symlink():
            continue
        try:
            with path.open('r+b') as stream:
                if acquire(stream):
                    stale = True
                else:
                    stale = False
                    stream.seek(1)
                    try:
                        record = json.loads(stream.read(4096))
                        values = [float(record[k]) for k in ('memoryBytes', 'cpuCores', 'rss', 'cores', 'updated')]
                    except (KeyError, TypeError, ValueError):
                        continue  # Torn or malformed write: no credit.
                    if all(math.isfinite(v) and v >= 0 for v in values) and now - values[4] <= FRESH_SECONDS:
                        memory += min(values[2], values[0])
                        cores += min(values[3], values[1])
            if stale:
                path.unlink(missing_ok=True)
        except FileNotFoundError:
            continue
    return memory, cores


class UsageSampler(threading.Thread):
    """Sample this process's descendants; publish to the lease, keep peak and mean."""
    def __init__(self, lease=None, root_pid=None, interval=SAMPLE_SECONDS, usage=None):
        super().__init__(daemon=True, name='maven-usage-sampler')
        from maven_resources import tree_usage
        self.lease = lease
        self.root_pid = os.getpid() if root_pid is None else root_pid
        self.interval = interval
        self.usage = usage or tree_usage
        self.stopping = threading.Event()
        self.cpu_seen = {}
        self.history = deque()
        self.started_at = time.monotonic()
        self.peak_rss = 0

    def sample(self):
        now = time.monotonic()
        tree = self.usage(self.root_pid)
        rss = sum(value[0] for value in tree.values())
        for key, (_rss, cpu) in tree.items():
            # Keep exited descendants' last CPU so cumulative time never drops.
            self.cpu_seen[key] = max(cpu, self.cpu_seen.get(key, 0))
        total = sum(self.cpu_seen.values())
        self.history.append((now, total))
        while len(self.history) > 2 and now - self.history[1][0] >= CPU_WINDOW_SECONDS:
            self.history.popleft()
        first_time, first_total = self.history[0]
        cores = (total - first_total) / (now - first_time) if now > first_time else 0
        self.peak_rss = max(self.peak_rss, rss)
        if self.lease is not None:
            self.lease.publish(rss, cores)

    def run(self):
        while not self.stopping.is_set():
            try:
                self.sample()
            except (OSError, ValueError, IndexError):
                pass  # Telemetry must never disturb the Maven job it observes.
            self.stopping.wait(self.interval)

    def stop(self):
        self.stopping.set()
        if self.is_alive():
            self.join(timeout=5)

    def summary(self):
        elapsed = time.monotonic() - self.started_at
        cpu = sum(self.cpu_seen.values())
        return dict(peakRssGiB=round(self.peak_rss / 1024 ** 3, 3),
                    meanCores=round(cpu / elapsed, 3) if elapsed > 0 else 0)


def append_log(common, entry):
    """Append one telemetry line; O_APPEND keeps concurrent short lines whole."""
    entry = dict(entry, end=datetime.now(timezone.utc).isoformat(timespec='seconds'))
    line = (json.dumps(entry, sort_keys=True) + '\n').encode()
    descriptor = os.open(common / LOG_NAME, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o644)
    try:
        os.write(descriptor, line)
    finally:
        os.close(descriptor)


def trim_log(common, limit=LOG_LIMIT_BYTES):
    """Keep the newest half once over the limit. Requires the admission lock."""
    path = common / LOG_NAME
    try:
        if path.stat().st_size <= limit:
            return
        lines = path.read_bytes().splitlines(keepends=True)
    except FileNotFoundError:
        return
    kept, size = [], 0
    for line in reversed(lines):
        if size + len(line) > limit // 2:
            break
        kept.append(line)
        size += len(line)
    temporary = path.with_name(LOG_NAME + '.tmp')
    temporary.write_bytes(b''.join(reversed(kept)))
    os.replace(temporary, path)


def _percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(fraction * len(ordered)))] if ordered else 0


def summarise(common):
    """Return printable per-kind wait/hold/memory statistics from the log."""
    path = common / LOG_NAME
    rows = []
    if path.exists():
        for line in path.read_text(errors='replace').splitlines():
            try:
                rows.append(json.loads(line))
            except ValueError:
                continue
    if not rows:
        return 'No Maven queue telemetry recorded yet.'
    kinds = {}
    for row in rows:
        kinds.setdefault(row.get('kind', 'unknown'), []).append(row)
    total_wait = sum(row.get('waitSeconds', 0) for row in rows) or 1
    out = [f'{len(rows)} Maven jobs since {rows[0].get("end", "?")} '
           f'(wait/hold seconds p50/p95/max; peak RSS GiB p95/max; mean cores p50)',
           f'{"kind":<34}{"jobs":>5}{"wait":>18}{"wait%":>7}{"hold":>18}{"peak RSS":>12}{"cores":>7}']
    for kind, group in sorted(kinds.items(), key=lambda item: -sum(r.get('waitSeconds', 0) for r in item[1])):
        wait = [row.get('waitSeconds', 0) for row in group]
        hold = [row.get('holdSeconds', 0) for row in group]
        rss = [row.get('peakRssGiB', 0) for row in group]
        cores = [row.get('meanCores', 0) for row in group]
        spread = lambda values: f'{_percentile(values, .5):.0f}/{_percentile(values, .95):.0f}/{max(values):.0f}'
        out.append(f'{kind[:33]:<34}{len(group):>5}{spread(wait):>18}{100 * sum(wait) / total_wait:>6.0f}%'
                   f'{spread(hold):>18}{f"{_percentile(rss, .95):.2f}/{max(rss):.2f}":>12}'
                   f'{_percentile(cores, .5):>7.2f}')
    return '\n'.join(out)
