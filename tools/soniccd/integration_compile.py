"""Bind compilation, without test execution evidence, to an installed handoff.

Origin: 2026-10-29 Sonic CD integration tooling. Reuses the existing build-only
Maven wrapper and its admitted/terminal callbacks under its worktree lock and
activity lease. No test admission, test lifecycle, XML inspection, retry, cache,
receipt or output file is requested. Source observations are non-atomic and
exclude ignored untracked bytes, symlink targets and initialized gitlink contents.
"""
from contextlib import redirect_stdout
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import sys

import integration_packet as packet
from integration_handoff import handoff_summary
from integration_run import queue, source_identity


def compile_handoff(root, spec_path, *, execute=False):
    try:
        return _compile_handoff(root, spec_path, execute=execute)
    except subprocess.SubprocessError as error:
        # Observation/launch faults provide no attributed compiler exit. Keep
        # them in the CLI's incomplete-evidence category, never compiler RED.
        raise packet.PacketError('Compilation evidence incomplete: '+str(error)[:2048]) from error


def _compile_handoff(root, spec_path, *, execute=False):
    root, _ = packet.worktree(root)
    candidate = packet.canonical(Path(spec_path))
    packet.require(not candidate.is_relative_to(root), 'Handoff spec must be external')
    spec = packet.snapshot(candidate)
    ready = handoff_summary(root, candidate, installed=True)
    packet.unchanged(spec, message='Handoff spec changed while reading its inventory')
    packet.require(ready['ready'], 'Installed handoff is blocked; inspect handoff --installed first')
    # The existing handoff schema validates property names and values. Its test
    # selectors/counts remain declarations only: test-compile selects none of them.
    args = ['-Dmse=off']
    args.extend('-D'+key+'='+value for key, value in sorted(ready['tests']['properties'].items()))
    args += ['test-compile', '-B']
    result = dict(version=1, id=ready['id'], stage='installed', scope='compilation-only',
        executed=False, tests_executed=False, status='planned', compilation_succeeded=None,
        requested_maven_args=args, source_equal=None, declarations_equal=None,
        observation='Compilation-only Maven invocation; no test lifecycle, discovery, pass, '
            'Java-version verification or native/API authenticity is inferred. The configured '
            'build executes code and is not sandboxed. Non-atomic source/dependency rechecks '
            'can miss change-and-restore. Ignored untracked files, symlink targets, initialized '
            'submodule contents, external media and runtime environment require independent '
            'custody. No JUnit/report inspection, deletion, pass cache or receipt.')
    if not execute:
        return result
    initial = source_identity(root)
    result.update(status='incomplete', compilation_succeeded=False, source_before=initial, issues=[])
    admitted = False

    def revalidate():
        packet.unchanged(spec, message='Handoff spec changed')
        current = handoff_summary(root, candidate, installed=True)
        packet.unchanged(spec, message='Handoff spec changed during revalidation')
        packet.require(current == ready, 'Handoff declarations/dependencies changed')

    def before_run():
        nonlocal admitted
        # queue.main invokes this after its build lock AND activity lease are
        # acquired. Never acquire another lock around the wrapper.
        packet.require(queue.build_only(args), 'Existing wrapper no longer recognizes a build-only invocation')
        revalidate()
        packet.require(source_identity(root) == initial, 'Source changed while awaiting build access')
        result['started_at'] = datetime.now(timezone.utc).isoformat()
        admitted = True

    def after_run(code):
        # Existing wrapper calls only after process wait/cleanup, retaining both
        # leases. Launch/wait/cleanup exceptions omit this terminal callback.
        result.update(executed=True, maven_returncode=code,
                      completed_at=datetime.now(timezone.utc).isoformat())
        packet.require(admitted and type(code) is int, 'Missing admitted/terminal compilation observation')
        try:
            revalidate()
            result['declarations_equal'] = True
        except (ValueError, OSError, subprocess.SubprocessError) as error:
            result['declarations_equal'] = False
            result['issues'].append('Declaration/dependency observation: '+str(error)[:2048])
        # Source equality is independently observed even if an external spec or
        # dependency changed; neither observation can conceal the other.
        try:
            result['source_after'] = source_identity(root)
            result['source_equal'] = result['source_after'] == initial
            if not result['source_equal']:
                result['issues'].append('Source changed during compilation')
        except (ValueError, OSError, subprocess.SubprocessError) as error:
            result['issues'].append('Source observation: '+str(error)[:2048])
        if result['source_equal'] and result['declarations_equal']:
            result['compilation_succeeded'] = code == 0
            result['status'] = 'compiled' if code == 0 else 'failed'

    previous = Path.cwd()
    try:
        os.chdir(root)
        # MAVEN_ARGS/custom Maven config can change the lifecycle. Preserve the
        # wrapper's conservative classification: reject uncertainty instead of
        # inventing a build-only bypass or submitting a compiler check as tests.
        packet.require(queue.build_only(args), 'Existing wrapper does not recognize a build-only invocation')
        with queue.handle_termination(), redirect_stdout(sys.stderr):
            queue.main(args, before_run=before_run, after_run=after_run, maven_stdout=sys.stderr)
        if 'maven_returncode' not in result:
            raise packet.PacketError('Wrapper returned without a terminal compilation observation', result)
    finally:
        os.chdir(previous)
    return result
