#!/usr/bin/env python3
"""Choose the base a pushed-branch policy check must validate from.

Purpose: a push run's own ``github.event.before`` only covers that push. When a
pending run is superseded by a newer push, or a run fails and another push
follows, the earlier range would never be validated. This prints the newest
commit whose policy job actually succeeded on the branch, so the next run
re-covers every commit pushed since.

Inputs: the GitHub Actions API through ``gh api`` (needs ``GH_TOKEN`` with
``actions: read``), the local clone (full history), and an optional floor ref
whose history is already validated elsewhere (master, by release.yml).

Rules: a candidate is the head commit of a push run of the workflow on the
branch that concluded success (the whole run, or only ``--job`` for a workflow
whose other jobs may fail after policy passed), and it must be an ancestor of
the pushed head. With a floor, the floor's merge base with the head is used unless a
candidate descends from it. Without either, ``--before`` is printed. API errors
fail the step rather than narrowing the range.

Origin: 2026-11-05, after two non-compliant commits reached develop unchecked.
"""
import argparse
import json
import subprocess
import sys

RUN_PAGES = 5
RUNS_PER_PAGE = 100


def git(*args, check=True):
    return subprocess.run(['git', *args], text=True, capture_output=True, check=check)


def is_commit(sha):
    return git('cat-file', '-e', sha + '^{commit}', check=False).returncode == 0


def is_ancestor(older, newer):
    return git('merge-base', '--is-ancestor', older, newer, check=False).returncode == 0


def gh_api(path):
    result = subprocess.run(['gh', 'api', '-H', 'Accept: application/vnd.github+json', path],
                            text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(f'gh api {path} failed: {result.stderr.strip()}')
    return json.loads(result.stdout)


def floor_base(floor_ref, head):
    if not floor_ref or not is_commit(floor_ref):
        return None
    result = git('merge-base', floor_ref, head, check=False)
    return result.stdout.strip() or None


def job_succeeded(api, repository, run_id, job_name):
    jobs = api(f'repos/{repository}/actions/runs/{run_id}/jobs?per_page=100').get('jobs', [])
    return any(job.get('name') == job_name and job.get('conclusion') == 'success' for job in jobs)


def resolve(api, repository, workflow, job_name, branch, head, before, floor_ref=None):
    floor = floor_base(floor_ref, head)
    status = 'completed' if job_name else 'success'
    for page in range(1, RUN_PAGES + 1):
        runs = api(f'repos/{repository}/actions/workflows/{workflow}/runs?branch={branch}'
                   f'&event=push&status={status}&per_page={RUNS_PER_PAGE}&page={page}')
        runs = runs.get('workflow_runs', [])
        for run in runs:
            candidate = run.get('head_sha', '')
            if not candidate or not is_commit(candidate) or not is_ancestor(candidate, head):
                continue
            if floor and not is_ancestor(floor, candidate):
                if is_ancestor(candidate, floor):
                    # The floor already covers this candidate; it is always sound.
                    return floor, f'floor {floor_ref} merge base {floor}'
                continue
            if not job_name:
                return candidate, f'last successful run {run["id"]}'
            if job_succeeded(api, repository, run['id'], job_name):
                return candidate, f'last successful {job_name} job in run {run["id"]}'
        if len(runs) < RUNS_PER_PAGE:
            break
    if floor:
        return floor, f'floor {floor_ref} merge base {floor}'
    return before, 'no earlier successful run; using the push event base'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--repository', required=True)
    parser.add_argument('--workflow', required=True)
    parser.add_argument('--job')
    parser.add_argument('--branch', required=True)
    parser.add_argument('--head', required=True)
    parser.add_argument('--before', required=True)
    parser.add_argument('--floor-ref')
    args = parser.parse_args(argv)
    base, reason = resolve(gh_api, args.repository, args.workflow, args.job, args.branch,
                           args.head, args.before, args.floor_ref)
    print(f'policy base: {base} ({reason})', file=sys.stderr)
    print(base)
    return 0


if __name__ == '__main__':
    sys.exit(main())
