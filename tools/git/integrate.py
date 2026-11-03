#!/usr/bin/env python3
"""Cooperatively own one checkout's Git mutations. Inputs: explicit owner/task,
branch, full expected HEAD and pinned source. Origin: OpenGGF issue #256.
See INTEGRATION.md for recovery, handoff and the POSIX/local-filesystem boundary.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager, suppress
from dataclasses import asdict, dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import uuid


class Refused(Exception):
    pass


@dataclass(frozen=True)
class Identity:
    owner: str
    task: str


@dataclass(frozen=True)
class CheckoutState:
    branch: str
    head: str
    merge_head: str
    operation: bool
    status: str
    tracked_changes: str
    index_entries: str

    @property
    def fingerprint(self):
        return hashlib.sha256(json.dumps(asdict(self), sort_keys=True).encode()).hexdigest()

    @property
    def clean(self):
        return not self.status and not self.operation


def run(argv, root, fd=None, capture=False):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS='0', GIT_MERGE_AUTOEDIT='no')
    with subprocess.Popen(argv, cwd=root, env=env, start_new_session=True,
                          pass_fds=() if fd is None else (fd,),
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None) as child:
        try:
            stdout, stderr = child.communicate()
        except BaseException:
            with suppress(ProcessLookupError):
                os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()
            raise
        if child.returncode:
            detail = stderr.decode(errors='replace').strip() if stderr else ''
            raise Refused(f'{argv[0]} {argv[1:]} exited {child.returncode}. {detail}')
        return stdout or b''


class Checkout:
    def __init__(self, path):
        self.root = Path(path).resolve()
        self.git_dir = Path(self.git('rev-parse', '--absolute-git-dir').decode().strip())
        if self.git('rev-parse', '--show-toplevel').decode().strip() != str(self.root):
            raise Refused('--checkout must name the checkout root')
        self.record_path = self.git_dir / 'integration-owner.json'
        self.lock_path = self.git_dir / 'integration.lock'

    def git(self, *args, fd=None):
        return run(['git', *args], self.root, fd, capture=True)

    def state(self):
        def marker(name):
            path = self.git_dir / name
            return path.read_text().strip() if path.is_file() else ''

        return CheckoutState(
            self.git('rev-parse', '--abbrev-ref', 'HEAD').decode().strip(),
            self.git('rev-parse', 'HEAD').decode().strip(), marker('MERGE_HEAD'),
            any((self.git_dir / name).exists() for name in (
                'MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'rebase-merge',
                'rebase-apply', 'sequencer', 'BISECT_LOG')),
            self.git('status', '--porcelain=v1', '-z', '--untracked-files=all').decode(errors='backslashreplace'),
            hashlib.sha256(self.git('diff', '--no-ext-diff', '--binary', 'HEAD', '--')).hexdigest(),
            hashlib.sha256(self.git('ls-files', '--stage', '-z')).hexdigest())

    def read_record(self):
        if not self.record_path.exists():
            return None
        try:
            record = json.loads(self.record_path.read_text())
            if record['schema'] != 1 or not all(isinstance(record[key], str) for key in (
                    'id', 'owner', 'task', 'phase', 'branch', 'expected_head', 'state')):
                raise ValueError('unknown schema or field type')
            return record
        except (KeyError, TypeError, ValueError) as error:
            raise Refused(f'Invalid ownership record; inspect it without deleting locks: {error}') from error

    def write_record(self, record):
        temporary = self.record_path.with_suffix('.tmp')
        with temporary.open('w') as stream:
            json.dump(record, stream, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(self.record_path)

    @contextmanager
    def lease(self):
        import fcntl

        # Keep this inode, and close rather than LOCK_UN: a surviving child
        # retains exclusion through its inherited open-file description.
        with self.lock_path.open('a+b') as stream:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as error:
                raise Refused(f'Checkout is owned by an active process. Record: {self.read_record()}') from error
            yield stream.fileno()


def check_identity(record, args, state):
    if record is None:
        if args.record:
            raise Refused('No ownership record matches --record')
        return
    if args.record:
        if record['id'] != args.record or Identity(record['owner'], record['task']) != Identity(args.owner, args.task):
            raise Refused('Stale record ID or wrong receiving owner/task')
        if args.action == 'deliver' and record['state'] != state.fingerprint:
            raise Refused('Checkout changed since record; inspect status and use explicit recovery')
    elif record['phase'] != 'complete':
        raise Refused(f'Prior operation needs explicit recovery/handoff with --record {record["id"]}')


def check_destination(state, args):
    if state.branch != args.branch or state.head != args.expected_head:
        raise Refused(f'Expected {args.branch} at {args.expected_head}; found {state.branch} at {state.head}')


def full_oid(value):
    if not re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', value):
        raise argparse.ArgumentTypeError('use a full lowercase Git object ID')
    return value


def delivery_preconditions(checkout, args, state):
    if not state.clean:
        raise Refused('Destination has local changes or an in-progress Git operation; nothing was cleaned')
    source = checkout.git('rev-parse', '--verify', args.source + '^{commit}').decode().strip()
    if source != args.source:
        raise Refused('--source must pin a commit, not a tag object')
    checkout.git('remote', 'get-url', args.remote)


def deliver(checkout, args, fd, record):
    remote_ref = f'refs/heads/{args.branch}'

    def phase(name):
        record['phase'] = name
        record['state'] = checkout.state().fingerprint
        checkout.write_record(record)
        print(f'{name}: {args.owner} / {args.task}', flush=True)

    def unchanged(expected):
        actual = checkout.state()
        if actual.branch != args.branch or actual.head != expected or not actual.clean:
            raise Refused('Destination changed during delivery; inspect status before recovery')

    def fetch():
        checkout.git('fetch', '--no-tags', args.remote, remote_ref, fd=fd)
        return checkout.git('rev-parse', 'FETCH_HEAD').decode().strip()

    phase('fetching')
    remote_head = fetch()
    unchanged(args.expected_head)
    phase('fast-forwarding')
    checkout.git('merge', '--ff-only', '--no-edit', remote_head, fd=fd)
    updated = checkout.state().head
    if updated not in (args.expected_head, remote_head):
        raise Refused('Unexpected HEAD after fast-forward')
    unchanged(updated)
    phase('merging')
    checkout.git('merge', '--no-edit', args.source, fd=fd)
    candidate = checkout.state().head
    unchanged(candidate)
    phase('checking')
    run(args.command, checkout.root, fd)
    unchanged(candidate)
    phase('rechecking-remote')
    if fetch() != remote_head:
        raise Refused('Remote advanced during delivery; reconcile normally, then verify again')
    unchanged(candidate)
    phase('pushing')
    checkout.git('push', args.remote, f'{candidate}:{remote_ref}', fd=fd)
    unchanged(candidate)
    phase('complete')


def mutate(checkout, args):
    with checkout.lease() as fd:
        state = checkout.state()
        previous = checkout.read_record()
        check_identity(previous, args, state)
        check_destination(state, args)
        if args.action in ('recover', 'handoff'):
            if previous is None or state.fingerprint != args.expected_state:
                raise Refused('Recovery/handoff requires the current record and exact status state ID')
        else:
            delivery_preconditions(checkout, args, state)
        record = dict(schema=1, id=uuid.uuid4().hex, owner=args.owner, task=args.task,
                      branch=args.branch, expected_head=args.expected_head,
                      phase='acquired', state=state.fingerprint, merge_head=state.merge_head)
        checkout.write_record(record)
        try:
            if args.action == 'deliver':
                deliver(checkout, args, fd, record)
            elif args.action == 'recover':
                record['phase'] = 'recovering'
                checkout.write_record(record)
                run(args.command, checkout.root, fd)
                if checkout.state().branch != args.branch:
                    raise Refused('Recovery changed the destination branch; inspect before continuing')
                record['phase'] = 'recovered'
            else:
                record.update(phase='handed-off', owner=args.to_owner, task=args.to_task,
                              from_owner=args.owner, from_task=args.task)
        except (Refused, OSError, KeyboardInterrupt) as error:
            record.update(phase='failed', failure=str(error) or 'cancelled')
            raise
        finally:
            current = checkout.state()
            record.update(state=current.fingerprint, merge_head=current.merge_head,
                          observed_head=current.head)
            checkout.write_record(record)
            print(json.dumps(record), flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkout', required=True, type=Path)
    actions = parser.add_subparsers(dest='action', required=True)
    actions.add_parser('status', help='show live lease availability and current snapshot')
    for action in ('deliver', 'recover', 'handoff'):
        command = actions.add_parser(action)
        command.add_argument('--owner', required=True)
        command.add_argument('--task', required=True)
        command.add_argument('--branch', required=True)
        command.add_argument('--expected-head', required=True, type=full_oid)
        command.add_argument('--record', required=action != 'deliver')
        if action != 'deliver':
            command.add_argument('--expected-state', required=True)
        if action == 'handoff':
            command.add_argument('--to-owner', required=True)
            command.add_argument('--to-task', required=True)
        else:
            command.add_argument('command', nargs=argparse.REMAINDER,
                                 help='verification argv for deliver, one repair argv for recover; after --')
        if action == 'deliver':
            command.add_argument('--source', required=True, type=full_oid)
            command.add_argument('--remote', default='origin')
    args = parser.parse_args(argv)
    if os.name != 'posix':
        parser.error('integration ownership currently supports POSIX local filesystems only')
    if hasattr(args, 'command'):
        if args.command[:1] == ['--']:
            args.command = args.command[1:]
        if not args.command:
            parser.error('provide a verification/repair command after --')
    if hasattr(args, 'branch') and (args.branch.startswith('-') or
            subprocess.run(['git', 'check-ref-format', '--branch', args.branch],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode):
        parser.error('invalid destination branch')
    if hasattr(args, 'remote') and (args.remote.startswith('-') or not args.remote):
        parser.error('invalid remote')
    if hasattr(args, 'owner') and (not args.owner.strip() or not args.task.strip()):
        parser.error('owner and task must be nonempty')
    if args.action == 'handoff' and (not args.to_owner.strip() or not args.to_task.strip()):
        parser.error('receiving owner and task must be nonempty')

    def cancelled(signum, frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, cancelled)
    try:
        checkout = Checkout(args.checkout)
        if args.action == 'status':
            try:
                with checkout.lease():
                    active = False
            except Refused:
                active = True
            state = checkout.state()
            print(json.dumps(dict(active=active, record=checkout.read_record(),
                                  state_id=state.fingerprint, checkout=asdict(state))))
        else:
            mutate(checkout, args)
    except (Refused, OSError) as error:
        print(f'Integration stopped: {error}', file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print('Integration cancelled; inspect status before recovery.', file=sys.stderr)
        return 130
    return 0


if __name__ == '__main__':
    sys.exit(main())
