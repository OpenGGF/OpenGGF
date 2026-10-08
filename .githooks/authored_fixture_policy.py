#!/usr/bin/env python3
"""Verify the exact reviewed Tide Circuit blobs recorded in authored-fixtures.json.

Inputs are trusted hook policy and Git index/commit blobs, never working-tree
assets or executable candidate generators. Origin: 2026-10-07 creator readiness,
authored assets introduced by 294a6ac0993ffa680388dda8efb4d11544b5afe2.
All other ROM-like paths retain the repository denylist.
"""
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

MANIFEST = Path(__file__).with_name('authored-fixtures.json')
FIXTURE_ROOT = 'src/test/resources/mods/sample-two-act-campaign-src/project/'


@lru_cache(maxsize=1)
def reviewed_fixtures():
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    if (manifest.get('schema') != 1
            or not re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', manifest.get('originCommit', ''))
            or manifest.get('generator', {}).get('path') != FIXTURE_ROOT + 'tools/generate_assets.py'
            or not re.fullmatch(r'[0-9a-f]{64}', manifest.get('generator', {}).get('sha256', ''))):
        raise ValueError('Invalid authored-fixture provenance')
    fixtures = manifest.get('fixtures', [])
    if not isinstance(fixtures, list) or len(fixtures) != 22:
        raise ValueError('Reviewed authored-fixture inventory must contain exactly 22 entries')
    entries = {}
    prefix = FIXTURE_ROOT + 'src/main/resources/levels/tide/'
    for entry in fixtures:
        path = entry.get('path', '')
        relative = path.removeprefix(prefix)
        parts = relative.split('/')
        if (not path.startswith(prefix) or len(parts) != 2 or parts[0] not in ('act1', 'act2')
                or not re.fullmatch(r'[a-z]+(?:-[a-z]+)*\.bin', parts[1])
                or path in entries or entry.get('mode') != '100644'
                or type(entry.get('size')) is not int or not 0 < entry['size'] < 1_048_576
                or not re.fullmatch(r'[0-9a-f]{64}', entry.get('sha256', ''))):
            raise ValueError('Invalid or duplicate reviewed authored-fixture entry')
        entries[path] = entry
    return entries


def approved_fixture_blob(path, mode, size, read_blob):
    """Admit only an exact manifest path, regular mode, length and content digest."""
    entry = reviewed_fixtures().get(path)
    if entry is None or mode != entry['mode'] or size != entry['size']:
        return False
    content = read_blob()
    return len(content) == size and hashlib.sha256(content).hexdigest() == entry['sha256']


def git(*args):
    return subprocess.check_output(['git', *args], stderr=subprocess.PIPE)


def approved_git_fixture(ref, path):
    """Inspect the index or a commit; the caller cannot substitute local bytes."""
    if path not in reviewed_fixtures():
        return False
    if ref == 'INDEX':
        records = git('ls-files', '--stage', '-z', '--', path).split(b'\0')
    else:
        canonical = git('rev-parse', '--verify', ref + '^{commit}').decode().strip()
        records = git('ls-tree', '-l', '-z', canonical, '--', path).split(b'\0')
    records = [record for record in records if record]
    if len(records) != 1:
        return False
    metadata, actual_path = records[0].split(b'\t', 1)
    if actual_path.decode('utf-8', errors='surrogateescape') != path:
        return False
    if ref == 'INDEX':
        mode, oid, stage = metadata.decode('ascii').split()
        if stage != '0':
            return False
        size = int(git('cat-file', '-s', oid))
    else:
        mode, kind, oid, size = metadata.decode('ascii').split()
        if kind != 'blob':
            return False
        size = int(size)
    return approved_fixture_blob(path, mode, size, lambda: git('cat-file', 'blob', oid))


if __name__ == '__main__':
    try:
        if len(sys.argv) != 3:
            raise ValueError('usage: authored_fixture_policy.py {INDEX|commit} <path>')
        sys.exit(0 if approved_git_fixture(sys.argv[1], sys.argv[2]) else 1)
    except (ValueError, TypeError, AttributeError, OSError, subprocess.CalledProcessError) as error:
        print(f'policy: authored-fixture verification failed: {error}', file=sys.stderr)
        sys.exit(2)
