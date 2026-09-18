"""Prerequisite checks and per-invocation timeouts for local validation."""
import os
import re
import subprocess

DEFAULT_MINUTES = 40
IDLE_SECONDS = 600
BROAD_CLASSES = 500


def preflight(root, plan):
    """Check the actual launch environment before starting any test lane."""
    checks = [('Maven with Java 21', ['mvn', '-v'], r'Java version:\s*21(?:\D|$)')]
    if plan['guards']:
        checks += [
            ('Lua 5.4 (set LUA_BIN)', [os.environ.get('LUA_BIN', 'lua'), '-v'], r'Lua 5\.4(?:\D|$)'),
            ('PowerShell on PATH', ['pwsh', '-NoLogo', '-NoProfile', '-Command', 'exit 0'], None),
        ]
    failures = []
    for label, command, expected in checks:
        try:
            result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, timeout=20)
            if result.returncode or (expected and not re.search(expected, result.stdout)):
                failures.append(f'{label}: wrong version or unsuccessful launch')
        except (OSError, subprocess.TimeoutExpired) as error:
            failures.append(f'{label}: {error}')
    if failures:
        raise ValueError('Prerequisite check failed before tests: ' + '; '.join(failures))


def is_broad(plan):
    return plan['full'] or len(plan['tests']) >= BROAD_CLASSES
