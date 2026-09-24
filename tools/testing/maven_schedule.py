"""Short-job priority with bounded backfilling for the shared Maven queue.

Inputs: invocation shape and automatically leased waiting records. Records contain
no Maven arguments or PIDs and never authorize execution: OS locks do that.
Origin: 2026-09-15 short-run priority follow-up to Maven resource profiling.
"""
import json
import math
from pathlib import Path
import re
import uuid

AGING_SECONDS = 300
FULL_SECONDS = 900
REQUEST_NAME = re.compile(r'[0-9a-f]{32}\.request')


def class_estimate(count):
    # The earlier ordinary profile took ~722s for 2,588 classes. Round its
    # per-class cost to 0.3s and retain a 30s compilation/startup allowance.
    return min(FULL_SECONDS, 30 + .3 * max(0, count - 1))


def profiles_of(args):
    """Return explicitly activated Maven profiles and whether -P lacks a value."""
    profiles = []
    following = False
    for arg in args:
        if following:
            profiles.extend(arg.split(','))
            following = False
        elif arg in ('-P', '--activate-profiles'):
            following = True
        elif arg.startswith('-P'):
            profiles.extend(arg[2:].split(','))
        elif arg.startswith('--activate-profiles='):
            profiles.extend(arg.split('=', 1)[1].split(','))
    return profiles, following


def maven_estimate(args):
    """Coarse ordering estimates, not timeouts or memory reservations."""
    selectors = [arg.split('=', 1)[1] for arg in args if arg.startswith('-Dtest=')]
    if selectors:
        selected = selectors[-1].split(',')
        if all(selected) and not any(c in selectors[-1] for c in '*?%!'):
            return class_estimate(len(selected))
        return FULL_SECONDS
    profiles, dangling = profiles_of(args)
    return 180 if not dangling and set(profiles) == {'guards'} else FULL_SECONDS


def maven_kind(args, exclusive):
    """Telemetry label: exclusive, profile names, focused selector or full."""
    if exclusive:
        return 'exclusive'
    profiles = sorted(set(profiles_of(args)[0]))
    if profiles:
        return 'profile:' + ','.join(profiles)
    return 'focused' if maven_estimate(args) < FULL_SECONDS else 'full'


def plan_estimate(plan):
    if plan['full']:
        return FULL_SECONDS
    return min(FULL_SECONDS, class_estimate(len(plan['tests'])) + (180 if plan['guards'] else 0))


def aged(request, now):
    return now - request['enqueued'] >= AGING_SECONDS


def ordered(requests, now):
    return sorted(requests, key=lambda r: (
        0 if aged(r, now) else 1,
        r['enqueued'] if aged(r, now) else r['estimate'],
        r['enqueued'], r['id']))


def choose(requests, now, fits):
    """Backfill eligible work until an aged head needs capacity to drain.

    Five minutes is a priority promotion, not a start-time guarantee: running
    Maven jobs are never preempted and external resource pressure may persist.
    """
    for request in ordered(requests, now):
        if fits(request):
            return request
        if aged(request, now):
            return None
    return None


class WaitingRequest:
    """All methods except close require the common admission lock.

    The unique file is also its liveness lease. Windows locks byte zero; JSON
    begins at byte one, allowing other processes to read a live request. Close a
    dead lease before unlinking it so Windows deletion and POSIX locking agree.
    """
    def __init__(self, common, record, acquire):
        self.directory = common / 'maven-waiters'
        self.directory.mkdir(exist_ok=True)
        self.record = dict(record, id=uuid.uuid4().hex)
        self.path = self.directory / (self.record['id'] + '.request')
        self.stream = self.path.open('x+b')
        try:
            self.stream.write(b'\0')
            self.stream.flush()
            if not acquire(self.stream):
                raise RuntimeError('New waiting request could not acquire its lease')
            self.stream.seek(1)  # Byte-lock acquisition may move the Windows cursor.
            self.stream.write(json.dumps(self.record).encode())
            self.stream.flush()
        except BaseException:
            self.close()
            self.path.unlink()
            raise

    def pending(self, acquire):
        requests = []
        for path in self.directory.iterdir():
            if not REQUEST_NAME.fullmatch(path.name):
                continue
            if path == self.path:
                requests.append(self.record)
                continue
            if path.is_symlink():
                raise ValueError('Refusing a symlinked Maven waiting request')
            try:
                with path.open('r+b') as stream:
                    stale = acquire(stream)
                    if not stale:
                        stream.seek(1)
                        try:
                            record = json.loads(stream.read(8193))
                            self.validate(record, path)
                        except (KeyError, TypeError, ValueError) as error:
                            raise ValueError('Invalid live Maven waiting request: ' + path.name) from error
                        requests.append(record)
                if stale:
                    path.unlink()
            except FileNotFoundError:
                continue
        return requests

    def validate(self, record, path):
        common = self.directory.parent
        tree = Path(record['tree'])
        valid_tree = (tree.name == 'maven-worktree.lock' and
                      (tree.parent == common or tree.parent.parent == common / 'worktrees'))
        if (record['id'] != path.stem or not valid_tree or type(record['auto']) is not bool
                or not all(type(record[k]) in (int, float) and math.isfinite(record[k])
                           for k in ('enqueued', 'estimate')) or record['estimate'] <= 0):
            raise ValueError('Invalid Maven waiting request')

    def close(self):
        self.stream.close()

    def remove(self):
        self.close()
        self.path.unlink(missing_ok=True)
