"""Base selection for pushed-branch policy runs, against a disposable Git repository."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import policy_push_base  # noqa: E402

REPOSITORY = 'OpenGGF/OpenGGF'


class PolicyPushBaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.previous = os.getcwd()
        self.addCleanup(os.chdir, self.previous)
        os.chdir(self.temporary.name)
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_AUTHOR_NAME='Test', GIT_AUTHOR_EMAIL='test@example.invalid',
                        GIT_COMMITTER_NAME='Test', GIT_COMMITTER_EMAIL='test@example.invalid')
        self.git('init', '-q', '-b', 'develop')
        self.runs = []
        self.jobs = {}
        self.calls = []

    def git(self, *args):
        return subprocess.run(['git', *args], check=True, text=True, capture_output=True,
                              env=self.env).stdout.strip()

    def commit(self, name):
        self.git('commit', '-q', '--allow-empty', '-m', name)
        return self.git('rev-parse', 'HEAD')

    def run_of(self, sha, run_id, policy='success'):
        self.runs.append({'id': run_id, 'head_sha': sha})
        self.jobs[run_id] = [{'name': 'smoke', 'conclusion': 'success'},
                             {'name': 'policy', 'conclusion': policy}]

    def api(self, path):
        self.calls.append(path)
        if '/jobs' in path:
            run_id = int(path.split('/runs/')[1].split('/')[0])
            return {'jobs': self.jobs[run_id]}
        page = int(path.rsplit('page=', 1)[1])
        runs = self.runs
        if 'status=success' in path:
            runs = [run for run in runs if run.get('conclusion', 'success') == 'success']
        return {'workflow_runs': runs if page == 1 else []}

    def resolve(self, head, before, floor_ref=None, job='policy'):
        return policy_push_base.resolve(self.api, REPOSITORY, 'release.yml', job, 'develop',
                                        head, before, floor_ref)[0]

    def test_cancelled_and_failed_ranges_are_revalidated_from_last_success(self):
        validated = self.commit('validated')
        cancelled = self.commit('cancelled push')
        failed = self.commit('failed push')
        head = self.commit('new push')
        self.runs.append({'id': 3, 'head_sha': failed})
        self.jobs[3] = [{'name': 'policy', 'conclusion': 'failure'}]
        self.runs.append({'id': 2, 'head_sha': cancelled})
        self.jobs[2] = [{'name': 'policy', 'conclusion': 'cancelled'}]
        self.run_of(validated, 1)
        self.assertEqual(validated, self.resolve(head, failed))
        self.assertIn('workflows/release.yml/runs?branch=develop&event=push&status=completed',
                      self.calls[0])

    def test_single_job_workflow_trusts_the_run_conclusion(self):
        validated = self.commit('validated')
        failed = self.commit('failed push')
        head = self.commit('new push')
        self.runs.append({'id': 2, 'head_sha': failed, 'conclusion': 'failure'})
        self.runs.append({'id': 1, 'head_sha': validated})
        self.assertEqual(validated, self.resolve(head, failed, job=None))
        self.assertIn('event=push&status=success', self.calls[0])
        self.assertFalse([call for call in self.calls if '/jobs' in call])

    def test_skipped_policy_job_is_not_success(self):
        old = self.commit('validated long ago')
        skipped = self.commit('policy skipped')
        head = self.commit('new push')
        self.run_of(skipped, 2, policy='skipped')
        self.run_of(old, 1)
        self.assertEqual(old, self.resolve(head, skipped))

    def test_runs_off_the_pushed_history_are_ignored(self):
        root = self.commit('root')
        self.git('switch', '-q', '-c', 'rewritten')
        rewritten = self.commit('force-pushed away')
        self.git('switch', '-q', 'develop')
        head = self.commit('new push')
        self.run_of(rewritten, 2)
        self.run_of(root, 1)
        self.assertEqual(root, self.resolve(head, rewritten))
        self.runs.append({'id': 9, 'head_sha': 'f' * 40})
        self.assertEqual(root, self.resolve(head, rewritten))

    def test_floor_replaces_older_or_missing_candidates(self):
        old = self.commit('validated before the release')
        self.git('branch', 'master')
        released = self.git('rev-parse', 'HEAD')
        head = self.commit('develop work')
        self.assertEqual(released, self.resolve(head, old, 'master'))
        self.run_of(old, 1)
        self.assertEqual(released, self.resolve(head, old, 'master'))
        newer = self.commit('validated after the release')
        head = self.commit('next push')
        self.run_of(newer, 2)
        self.runs.reverse()
        self.assertEqual(newer, self.resolve(head, newer, 'master'))

    def test_without_success_or_floor_the_event_base_is_used(self):
        before = self.commit('before')
        head = self.commit('head')
        self.assertEqual(before, self.resolve(head, before))
        self.assertEqual(before, self.resolve(head, before, 'missing-ref'))

    def test_api_failure_fails_closed(self):
        head = self.commit('head')

        def broken(path):
            raise RuntimeError('gh api failed')

        with self.assertRaises(RuntimeError):
            policy_push_base.resolve(broken, REPOSITORY, 'push-policy.yml', None, 'develop', head, head)


if __name__ == '__main__':
    unittest.main()
