"""Short-job priority with bounded backfilling for the shared Maven queue.

Inputs: invocation shape and automatically leased waiting records. Records contain
no Maven arguments and never authorize execution: OS locks do that.
Origin: 2026-09-15 short-run priority follow-up to Maven resource profiling.
"""
from dataclasses import dataclass
import json
import math
import os
from pathlib import Path
import re
import uuid

AGING_SECONDS = 300
# Advisory only. Bump when admission, ordering, validation or lease semantics change;
# never use this stamp to authorize, prioritize or reject a request.
TOOLING_REVISION = 6
FULL_SECONDS = 900
REQUEST_NAME = re.compile(r'[0-9a-f]{32}\.request')
WORKTREE_BUSY = object()
WORKTREE_GONE = object()
SHORT_SECONDS = 60
SMALL_RESERVATION = (4 * 1024 ** 3, 4)


@dataclass
class ServiceCredit:
    """Invocation-local waiting credit, spent by each admitted batch's service."""
    seconds: float = 0.0

    def __post_init__(self):
        if (type(self.seconds) not in (int, float) or not math.isfinite(self.seconds)
                or not 0 <= self.seconds <= AGING_SECONDS):
            raise ValueError('Service credit must be finite and within the aging window')

    def enqueued(self, started):
        return started - self.seconds

    def account(self, waited, held):
        # Cap before charging service so a long wait cannot retain full priority.
        self.seconds = max(0, min(AGING_SECONDS, self.seconds + waited) - held)


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


@dataclass
class Selection:
    request: dict | None = None
    backfill_for: str | None = None
    blocked_by: str | None = None
    draining: bool = False


def small_request(request):
    reservation = request.get('reservation')
    return (request.get('small_job') is True and request.get('auto') is True
            and request['estimate'] <= SHORT_SECONDS and reservation is not None
            and all(0 < value <= maximum for value, maximum in zip(reservation, SMALL_RESERVATION)))


def select(requests, now, fits, *, backfill=None, reasons=None):
    """Choose without consuming allowance; only the admitting owner commits it.

    An aged head may permit one bounded small admission when memory or CPU is
    blocking it. The allowance survives that small job's exit. Serial requests,
    unknown state and exhausted allowances keep the established draining rule.
    """
    reasons = {} if reasons is None else reasons
    busy_trees = set()
    retained_trees = {request.get('tree', request['id']) for request in requests
                      if request.get('retained_tree', False)}
    ranked = ordered(requests, now)
    for index, request in enumerate(ranked):
        tree = request.get('tree', request['id'])
        if tree in busy_trees and not request.get('retained_tree', False):
            continue
        result = fits(request)
        if result is WORKTREE_GONE:
            continue
        if result is WORKTREE_BUSY and (request['auto'] or tree in retained_trees):
            busy_trees.add(tree)
            continue
        if result is not WORKTREE_BUSY and result:
            return Selection(request=request)
        if not aged(request, now):
            continue
        if (request.get('auto') and backfill is not None and backfill.available(request['id'])
                and reasons.get(request['id'], {}).get('reason') in ('memory', 'cpu')):
            for candidate in ranked[index + 1:]:
                # An exclusive aged request must drain the entire queue.
                if aged(candidate, now) and not candidate.get('auto'):
                    break
                candidate_tree = candidate.get('tree', candidate['id'])
                if candidate_tree == tree or candidate_tree in busy_trees or not small_request(candidate):
                    continue
                found = fits(candidate)
                if found is not WORKTREE_BUSY and found is not WORKTREE_GONE and found:
                    return Selection(candidate, request['id'], request['id'], True)
        return Selection(blocked_by=request['id'], draining=True)
    return Selection()


def choose(requests, now, fits):
    return select(requests, now, fits).request


class BackfillLedger:
    """Gate-protected consumed allowances, keyed by live waiting lease IDs.

    This is scheduling fairness state, never an execution lease. Bad or unknown
    files disable extra overtaking while normal admission remains available.
    """
    def __init__(self, common, requests):
        self.path = common / 'maven-backfill.json'
        self.used = None
        live = {request['id'] for request in requests}
        try:
            if self.path.is_symlink() or (self.path.exists() and not self.path.is_file()):
                return
            try:
                with self.path.open('rb') as stream:
                    raw = stream.read(131073)
                if len(raw) > 131072:
                    return
                data = json.loads(raw)
            except FileNotFoundError:
                data = dict(version=1, used=[])
            if (len(data.get('used', [])) > 2048 or data.get('version') != 1
                    or not isinstance(data.get('used'), list)
                    or any(not isinstance(value, str) or not re.fullmatch(r'[0-9a-f]{32}', value)
                           for value in data['used'])):
                return
            self.used = set(data['used']) & live
            if self.used != set(data['used']):
                self._save()
        except (OSError, ValueError, TypeError, AttributeError, RecursionError):
            self.used = None

    def available(self, request_id):
        return self.used is not None and request_id not in self.used and len(self.used) < 2048

    def consume(self, request_id):
        if not self.available(request_id):
            return False
        self.used.add(request_id)
        try:
            self._save()
            return True
        except OSError:
            self.used = None
            return False

    def _save(self):
        temporary = self.path.with_name(self.path.name + '.' + uuid.uuid4().hex + '.tmp')
        try:
            with temporary.open('xb') as stream:
                stream.write(json.dumps(dict(version=1, used=sorted(self.used))).encode())
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, self.path)
        finally:
            temporary.unlink(missing_ok=True)


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

    def publish(self):
        self.stream.seek(1)
        self.stream.write(json.dumps(self.record).encode())
        self.stream.truncate()
        self.stream.flush()

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
                or type(record.get('retained_tree', False)) is not bool
                or type(record.get('small_job', False)) is not bool
                or not all(type(record[k]) in (int, float) and math.isfinite(record[k])
                           for k in ('enqueued', 'estimate')) or record['estimate'] <= 0):
            raise ValueError('Invalid Maven waiting request')
        reservation = record.get('reservation')
        if reservation is not None and (not isinstance(reservation, list) or len(reservation) != 2
                or not all(type(v) in (int, float) and math.isfinite(v) and v > 0 for v in reservation)):
            raise ValueError('Invalid Maven resource reservation')

    def close(self):
        self.stream.close()

    def remove(self):
        self.close()
        self.path.unlink(missing_ok=True)
