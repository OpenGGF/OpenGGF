"""Prerequisite checks and per-invocation timeouts for local validation."""
from contextlib import nullcontext
import os
import re
import subprocess

from maven_activity import build_activity_slot
from maven_queue import inherited_slot

DEFAULT_MINUTES = 40
IDLE_SECONDS = 600
BROAD_CLASSES = 500


def preflight(root, plan):
    """Check the actual launch environment before starting any test lane."""
    checks = [('Maven with Java 25', ['mvn', '-v'], r'Java version:\s*25(?:\D|$)')]
    if plan['guards']:
        checks += [
            ('Lua 5.4 (set LUA_BIN)', [os.environ.get('LUA_BIN', 'lua'), '-v'], r'Lua 5\.4(?:\D|$)'),
            ('PowerShell on PATH', ['pwsh', '-NoLogo', '-NoProfile', '-Command', 'exit 0'], None),
        ]
    failures = []
    for label, command, expected in checks:
        try:
            # Even the Maven version probe starts a JVM. Coordinate it with
            # measurements, releasing activity before any test admission.
            activity = build_activity_slot(root) if command == ['mvn', '-v'] else nullcontext(())
            with activity as fds:
                result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, text=True, timeout=20,
                                        **inherited_slot(None, fds))
            if result.returncode or (expected and not re.search(expected, result.stdout)):
                failures.append(f'{label}: wrong version or unsuccessful launch')
        except (OSError, subprocess.TimeoutExpired) as error:
            failures.append(f'{label}: {error}')
    if failures:
        raise ValueError('Prerequisite check failed before tests: ' + '; '.join(failures))


def is_broad(plan):
    return plan['full'] or len(plan['tests']) >= BROAD_CLASSES
