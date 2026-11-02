"""Disposable Git and process regressions for issue #256. No user refs touched."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).with_name('integrate.py')


@unittest.skipUnless(os.name == 'posix', 'POSIX ownership and process inheritance')
class IntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL=os.devnull, GIT_AUTHOR_NAME='Test',
                        GIT_AUTHOR_EMAIL='test@example.invalid', GIT_COMMITTER_NAME='Test',
                        GIT_COMMITTER_EMAIL='test@example.invalid', GIT_TERMINAL_PROMPT='0')
        self.remote = self.root / 'remote.git'
        self.tree = self.root / 'destination'
        self.peer = self.root / 'peer'
        self.git(self.root, 'init', '--bare', str(self.remote))
        self.git(self.root, 'clone', str(self.remote), str(self.tree))
        self.git(self.tree, 'checkout', '-b', 'develop')
        (self.tree / 'shared').write_text('base\n')
        self.git(self.tree, 'add', '.')
        self.git(self.tree, 'commit', '-m', 'base')
        self.base = self.head()
        self.git(self.tree, 'push', '-u', 'origin', 'develop')
        self.git(self.root, 'clone', '-b', 'develop', str(self.remote), str(self.peer))
        self.git(self.peer, 'checkout', '-b', 'feature')
        (self.peer / 'feature').write_text('feature\n')
        self.git(self.peer, 'add', '.')
        self.git(self.peer, 'commit', '-m', 'feature')
        self.source = self.git(self.peer, 'rev-parse', 'HEAD').strip()
        self.git(self.tree, 'fetch', str(self.peer), 'feature')

    def git(self, tree, *args, success=True):
        result = subprocess.run(['git', *args], cwd=tree, env=self.env,
                                text=True, capture_output=True)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def head(self):
        return self.git(self.tree, 'rev-parse', 'HEAD').strip()

    def remote_head(self):
        return self.git(self.remote, 'rev-parse', 'refs/heads/develop').strip()

    def call(self, *args, success=True, tree=None):
        result = subprocess.run([sys.executable, str(SCRIPT), '--checkout',
                                 str(tree or self.tree), *args], env=self.env,
                                text=True, capture_output=True, timeout=20)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def status(self, tree=None):
        return json.loads(self.call('status', tree=tree).stdout)

    def identity(self, expected=None, owner='alice', task='256'):
        return ['--owner', owner, '--task', task, '--branch', 'develop',
                '--expected-head', expected or self.head()]

    def delivery(self, command=None, extra=(), expected=None):
        return ['deliver', *self.identity(expected), '--source', self.source,
                *extra, '--', *(command or [sys.executable, '-c', 'pass'])]

    def hook(self, tree, name, body):
        hooks = Path(self.git(tree, 'rev-parse', '--absolute-git-dir').strip()) / 'hooks'
        path = hooks / name
        path.write_text('#!/bin/sh\nset -eu\n' + body + '\n')
        path.chmod(0o755)

    def marker_check(self):
        self.checked = self.root / 'checked'
        return [sys.executable, '-c', f'from pathlib import Path; Path({str(self.checked)!r}).touch()']

    def advance_remote(self, filename='remote'):
        self.git(self.peer, 'checkout', 'develop')
        (self.peer / filename).write_text(filename)
        self.git(self.peer, 'add', '.')
        self.git(self.peer, 'commit', '-m', filename)
        self.git(self.peer, 'push', 'origin', 'develop')

    def test_success_fetches_fast_forwards_merges_checks_and_pushes(self):
        self.advance_remote()
        self.call(*self.delivery(self.marker_check()))
        self.assertTrue(self.checked.exists())
        self.assertEqual(self.head(), self.remote_head())
        self.assertTrue((self.tree / 'remote').exists())
        self.assertTrue((self.tree / 'feature').exists())
        self.assertEqual(self.status()['record']['phase'], 'complete')
        self.assertFalse(self.status()['active'])

    def test_failed_fetch_cannot_merge_check_or_push(self):
        self.git(self.tree, 'remote', 'add', 'missing', str(self.root / 'missing'))
        self.call(*self.delivery(self.marker_check(), extra=('--remote', 'missing')), success=False)
        self.assertEqual(self.head(), self.base)
        self.assertEqual(self.remote_head(), self.base)
        self.assertFalse(self.checked.exists())
        self.assertEqual(self.status()['record']['phase'], 'failed')

    def test_failed_fast_forward_cannot_merge_check_or_push(self):
        (self.tree / 'local').write_text('local')
        self.git(self.tree, 'add', '.')
        self.git(self.tree, 'commit', '-m', 'local')
        local = self.head()
        self.advance_remote()
        remote = self.remote_head()
        result = self.call(*self.delivery(self.marker_check()), success=False)
        self.assertIn('fast-forwarding', result.stdout)
        self.assertNotIn('merging:', result.stdout)
        self.assertEqual(self.head(), local)
        self.assertEqual(self.remote_head(), remote)
        self.assertFalse(self.checked.exists())

    def test_failed_expected_head_or_branch_does_not_fetch(self):
        result = self.call(*self.delivery(expected='0' * 40), success=False)
        self.assertNotIn('fetching:', result.stdout)
        self.assertEqual(self.head(), self.base)
        self.git(self.tree, 'checkout', '-b', 'wrong')
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.remote_head(), self.base)

    def test_dirty_files_and_existing_merge_are_preserved(self):
        (self.tree / 'shared').write_text('staged\n')
        self.git(self.tree, 'add', 'shared')
        before = self.git(self.tree, 'diff', '--cached', '--binary')
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.git(self.tree, 'diff', '--cached', '--binary'), before)
        self.assertEqual(self.head(), self.base)

    def test_refused_dirty_delivery_does_not_claim_or_replace_ownership(self):
        stray = self.tree / 'stray'
        stray.write_text('user work')
        self.call(*self.delivery(), success=False)
        self.assertIsNone(self.status()['record'])
        stray.unlink()
        self.call(*self.delivery())
        complete = self.status()['record']
        stray.write_text('more user work')
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.status()['record'], complete)
        self.assertEqual(stray.read_text(), 'more user work')

    def test_missing_source_or_unnamed_remote_does_not_claim_ownership(self):
        source = self.source
        self.source = '0' * 40
        self.call(*self.delivery(), success=False)
        self.assertIsNone(self.status()['record'])
        self.source = source
        self.call(*self.delivery(extra=('--remote', str(self.remote))), success=False)
        self.assertIsNone(self.status()['record'])
        self.assertEqual(self.head(), self.base)
        self.assertEqual(self.remote_head(), self.base)

    def conflict(self):
        (self.tree / 'shared').write_text('destination\n')
        self.git(self.tree, 'add', '.')
        self.git(self.tree, 'commit', '-m', 'destination')
        (self.peer / 'shared').write_text('source\n')
        self.git(self.peer, 'add', '.')
        self.git(self.peer, 'commit', '-m', 'source')
        self.source = self.git(self.peer, 'rev-parse', 'HEAD').strip()
        self.git(self.tree, 'fetch', str(self.peer), 'feature')
        self.call(*self.delivery(self.marker_check()), success=False)
        self.assertFalse(self.checked.exists())
        self.assertEqual(self.remote_head(), self.base)

    def test_merge_failure_and_handoff_preserve_conflicts_and_staged_bytes(self):
        self.conflict()
        before = self.status()
        self.assertEqual(before['checkout']['merge_head'], self.source)
        staged = self.git(self.tree, 'ls-files', '--stage')
        content = (self.tree / 'shared').read_bytes()
        record = before['record']['id']
        self.call('handoff', *self.identity(), '--record', record,
                  '--expected-state', before['state_id'], '--to-owner', 'bob', '--to-task', '256')
        handed = self.status()
        self.assertEqual(handed['record']['owner'], 'bob')
        self.assertEqual(handed['state_id'], before['state_id'])
        self.assertEqual(self.git(self.tree, 'ls-files', '--stage'), staged)
        self.assertEqual((self.tree / 'shared').read_bytes(), content)
        self.call('recover', *self.identity(), '--record', handed['record']['id'],
                  '--expected-state', handed['state_id'], '--', 'git', 'status', success=False)
        self.call('recover', *self.identity(owner='bob'), '--record', record,
                  '--expected-state', handed['state_id'], '--', 'git', 'status', success=False)
        self.call('recover', *self.identity(owner='bob'), '--record', handed['record']['id'],
                  '--expected-state', handed['state_id'], '--', 'git', 'status', '--porcelain')

    def test_stale_handoff_snapshot_is_refused(self):
        self.call(*self.delivery([sys.executable, '-c', 'raise SystemExit(7)']), success=False)
        before = self.status()
        (self.tree / 'shared').write_text('changed\n')
        self.git(self.tree, 'add', 'shared')
        self.call('handoff', *self.identity(), '--record', before['record']['id'],
                  '--expected-state', before['state_id'], '--to-owner', 'bob', '--to-task', '256',
                  success=False)
        self.assertEqual(self.status()['record']['id'], before['record']['id'])

    def test_failed_check_cannot_push_and_requires_explicit_recovery(self):
        self.call(*self.delivery([sys.executable, '-c', 'raise SystemExit(7)']), success=False)
        self.assertEqual(self.remote_head(), self.base)
        self.assertEqual(self.head(), self.source)
        self.call(*self.delivery(), success=False)
        record = self.status()['record']['id']
        self.call(*self.delivery(extra=('--record', record)))
        self.assertEqual(self.remote_head(), self.source)

    def test_check_changing_head_prevents_push(self):
        self.call(*self.delivery(['git', 'commit', '--allow-empty', '-m', 'changed']), success=False)
        self.assertEqual(self.remote_head(), self.base)

    def test_remote_advancement_during_check_requires_reconciliation(self):
        command = [sys.executable, '-c',
                   'import subprocess; '
                   f'subprocess.run(["git", "push", "origin", "feature:develop"], cwd={str(self.peer)!r}, check=True)']
        result = self.call(*self.delivery(command), success=False)
        self.assertIn('Remote advanced', result.stderr)
        self.assertNotIn('pushing:', result.stdout)

    def test_push_rejection_stops_without_retry_or_force(self):
        self.hook(self.remote, 'pre-receive', 'exit 1')
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.remote_head(), self.base)
        self.assertEqual(self.status()['record']['phase'], 'failed')

    def test_remote_race_after_recheck_is_rejected_by_normal_push(self):
        self.git(self.peer, 'checkout', 'develop')
        (self.peer / 'remote').write_text('remote')
        self.git(self.peer, 'add', '.')
        self.git(self.peer, 'commit', '-m', 'concurrent remote')
        raced = self.git(self.peer, 'rev-parse', 'HEAD').strip()
        self.hook(self.tree, 'pre-push', f'git -C "{self.peer}" push origin develop')
        result = self.call(*self.delivery(), success=False)
        self.assertIn('pushing:', result.stdout)
        self.assertEqual(self.remote_head(), raced)
        self.assertEqual(self.head(), self.source)
        self.assertEqual(self.status()['record']['phase'], 'failed')

    def test_recovery_acknowledges_state_then_allows_delivery(self):
        self.call(*self.delivery([sys.executable, '-c', 'raise SystemExit(7)']), success=False)
        (self.tree / 'shared').write_text('resolved\n')
        self.git(self.tree, 'add', 'shared')
        observed = self.status()
        self.call('recover', *self.identity(), '--record', observed['record']['id'],
                  '--expected-state', observed['state_id'], '--', 'git', 'commit', '-m', 'resolved')
        current = self.status()
        self.assertEqual(current['record']['phase'], 'recovered')
        self.call(*self.delivery(extra=('--record', current['record']['id'])))
        self.assertEqual(self.remote_head(), self.head())

    def test_preexisting_merge_blocks_delivery_without_aborting(self):
        self.conflict()
        before = self.status()
        self.call(*self.delivery(extra=('--record', before['record']['id'])), success=False)
        self.assertEqual(self.status()['state_id'], before['state_id'])
        self.assertEqual(self.remote_head(), self.base)

    def test_unknown_record_schema_fails_closed(self):
        git_dir = Path(self.git(self.tree, 'rev-parse', '--absolute-git-dir').strip())
        (git_dir / 'integration-owner.json').write_text('{"schema": 999}')
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.head(), self.base)
        self.assertEqual(self.remote_head(), self.base)

    def start_blocked_check(self):
        self.entered = self.root / 'entered'
        self.release = self.root / 'release'
        command = [sys.executable, '-c',
                   'import pathlib,time; '
                   f'pathlib.Path({str(self.entered)!r}).write_text(str(__import__("os").getpid())); '
                   f'exec("while not pathlib.Path({str(self.release)!r}).exists(): time.sleep(.02)")']
        output = (self.root / 'process.log').open('w+')
        self.addCleanup(output.close)
        process = subprocess.Popen([sys.executable, str(SCRIPT), '--checkout', str(self.tree),
                                    *self.delivery(command)], env=self.env,
                                   stdout=output, stderr=output)
        self.addCleanup(self.stop, process)
        deadline = time.monotonic() + 10
        while not self.entered.exists():
            if process.poll() is not None or time.monotonic() > deadline:
                output.seek(0)
                self.fail(output.read())
            time.sleep(.02)
        return process

    @staticmethod
    def stop(process):
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=10)

    def test_concurrent_coordinators_excluded_but_independent_worktree_available(self):
        process = self.start_blocked_check()
        self.assertTrue(self.status()['active'])
        self.call(*self.delivery(), success=False)
        other = self.root / 'independent'
        self.git(self.tree, 'worktree', 'add', '-b', 'other', str(other), self.base)
        self.assertFalse(self.status(tree=other)['active'])
        self.assertIsNone(self.status(tree=other)['record'])
        self.release.touch()
        self.assertEqual(process.wait(timeout=10), 0)

    def test_sigterm_cancels_child_and_releases_lease(self):
        process = self.start_blocked_check()
        process.terminate()
        self.assertEqual(process.wait(timeout=10), 130)
        self.assertFalse(self.status()['active'])
        self.assertEqual(self.status()['record']['phase'], 'failed')
        self.assertEqual(self.remote_head(), self.base)

    def test_supervisor_death_keeps_lease_until_child_exits(self):
        process = self.start_blocked_check()
        process.kill()
        process.wait(timeout=10)
        try:
            self.assertTrue(self.status()['active'])
            self.call(*self.delivery(), success=False)
        finally:
            self.release.touch()
        deadline = time.monotonic() + 10
        while self.status()['active'] and time.monotonic() < deadline:
            time.sleep(.02)
        self.assertFalse(self.status()['active'])
        self.call(*self.delivery(), success=False)
        self.assertEqual(self.remote_head(), self.base)


if __name__ == '__main__':
    unittest.main()
