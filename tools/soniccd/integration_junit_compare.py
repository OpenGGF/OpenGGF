"""Compare two explicit fresh Surefire selections by exact test outcome signatures.

Inputs: canonical Git worktree roots, separate run-start UTC cutoffs, a shared
exact class inventory, and separate allowlisted property requirements. Stdout is
owned by integration_packet.py compare-junit; this module writes nothing and
never starts tests. Origin: 2026-10-28 Sonic CD integration comparison tooling.
"""
from datetime import datetime, timedelta
import io
import os
import stat
import xml.etree.ElementTree as ET

import integration_packet as packet

MAX_CLASSES = 50
MAX_DIRECTORY_ENTRIES = 4096
MAX_SELECTED_BYTES = 64 * 1024**2  # per side; existing reader also caps each XML at 16 MiB
MAX_CASES = 50000  # per side, including nested/unsupported testcase elements
MAX_XML_DEPTH = 64
BUCKETS = ('unchanged_passes', 'unchanged_failures', 'unchanged_errors', 'unchanged_skips',
    'new_failures', 'worsened_failures', 'changed_failures', 'resolved_failures',
    'new_skips', 'became_skipped', 'changed_skips', 'resolved_skips',
    'added_identities', 'missing_identities')
BAD = frozenset(('failure', 'error'))


def _utc_cutoff(value):
    since = packet.cutoff(value)
    packet.require(datetime.fromisoformat(value.replace('Z', '+00:00')).utcoffset() == timedelta(0),
                   'Comparison run-start timestamps must be explicit UTC (+00:00 or Z)')
    return since


def _properties(properties):
    packet.require(isinstance(properties, dict) and
        set(properties) <= packet.PROPERTIES - {'java.version', 'user.dir'} and
        all(isinstance(value, str) and len(value) <= 4096 for value in properties.values()),
        'Comparison properties require allowlisted media/EGL names and bounded string values; never credentials')
    return dict(properties)


def _inventory(root):
    reports = packet.canonical(root / 'target/surefire-reports')
    packet.require(reports.is_dir(), f'Missing reports directory: {reports}')
    result = {}
    # Bound the whole flat directory traversal, including entries not selected as XML.
    with os.scandir(reports) as entries:
        for count, entry in enumerate(entries, 1):
            packet.require(count <= MAX_DIRECTORY_ENTRIES, 'Report directory exceeds entry limit')
            if not (entry.name.startswith('TEST-') and entry.name.endswith('.xml')):
                continue
            path = packet.canonical(reports / entry.name)
            info = path.lstat()
            packet.require(stat.S_ISREG(info.st_mode), f'Not a regular report file: {path}')
            result[entry.name] = packet.signature(info)
    return result


def _directories(root):
    result = []
    for path in (root, root/'target', root/'target/surefire-reports'):
        packet.canonical(path)
        info = path.stat()
        packet.require(stat.S_ISDIR(info.st_mode), f'Not a report parent directory: {path}')
        result.append((path, (info.st_dev, info.st_ino, info.st_mode)))
    return result


class _Evidence:
    def __init__(self, root, classes, since, properties):
        self.root, self.head = packet.worktree(root)
        self.directories = _directories(self.root)
        self.inventory = _inventory(self.root)
        self.saved = []
        self.bytes = self.cases = 0
        expected = {f'TEST-{name}.xml' for name in classes}
        selected = [info for name, info in self.inventory.items() if name in expected]
        packet.require(sum(info[2] for info in selected) <= MAX_SELECTED_BYTES,
                       'Selected XML exceeds per-side byte limit')
        self.summary = packet.junit_summary(self.root, classes, since, properties, _read_report=self.read)
        packet.require(not self.summary['issues'], 'Environment evidence rejected: ' + '; '.join(self.summary['issues']))
        # Report byte identities, never source/execution identities or arbitrary properties.
        for group, saved in zip(self.summary['classes'], self.saved):
            group['xml_sha256'] = packet.digest(saved.data)
            group['xml_bytes'] = len(saved.data)
        self.summary['requested_properties'] = properties

    def read(self, path):
        saved = packet.snapshot(path)
        packet.require(packet.signature(saved.info) == self.inventory.get(path.name),
                       f'Report changed after inventory: {path}')
        packet.require(saved.info.st_nlink == 1, f'Hardlinked report evidence is unsupported: {path}')
        self.bytes += len(saved.data)
        packet.require(self.bytes <= MAX_SELECTED_BYTES, 'Selected XML exceeds per-side byte limit')
        # Validate the actual bytes passed to the shared parser, rather than an earlier read.
        # UTF-16/32 and declarations must not bypass its ASCII declaration rejection.
        saved.data.decode('utf-8', errors='strict')
        packet.require(b'\0' not in saved.data and b'<!DOCTYPE' not in saved.data.upper() and
                       b'<!ENTITY' not in saved.data.upper(), f'Unsafe XML encoding/declarations: {path}')
        depth = 0
        for event, node in ET.iterparse(io.BytesIO(saved.data), events=('start', 'end')):
            if event == 'start':
                depth += 1
                packet.require(depth <= MAX_XML_DEPTH, 'XML exceeds depth limit')
                if node.tag == 'testcase':
                    self.cases += 1
                    packet.require(self.cases <= MAX_CASES, 'Selected XML exceeds per-side testcase limit')
            else:
                depth -= 1
                node.clear()
        self.saved.append(saved)
        return saved

    def recheck(self):
        for saved in self.saved:
            packet.unchanged(saved, message='Report changed during comparison')
        packet.require(_inventory(self.root) == self.inventory, 'Report inventory changed during comparison')
        packet.require(_directories(self.root) == self.directories, 'Report parent directory changed during comparison')
        packet.require(packet.worktree(self.root)[1] == self.head, 'Worktree HEAD changed during comparison')


def _cases(summary):
    return {case['identity']: case for group in summary['classes'] for case in group['cases']}


def _signature(case):
    # Deliberately do not trim messages, normalize numbers/paths or use totals.
    return tuple(case[key] for key in ('status', 'message', 'type'))


def _outcome(case):
    return {key: case[key] for key in ('status', 'message', 'type')}


def compare_junit(*, baseline_worktree, candidate_worktree, baseline_since, candidate_since,
                  classes, baseline_properties=None, candidate_properties=None):
    """Read-only comparison; accepted differences do not prove causality or regressions."""
    packet.require(isinstance(classes, (list, tuple)) and 1 <= len(classes) <= MAX_CLASSES and
        all(isinstance(name, str) and packet.CLASS.fullmatch(name) for name in classes) and
        len(set(classes)) == len(classes), 'Supply 1–50 distinct exact --class names; no patterns')
    classes = sorted(classes)
    baseline_properties = _properties({} if baseline_properties is None else baseline_properties)
    candidate_properties = _properties({} if candidate_properties is None else candidate_properties)
    # Validate both declarations before observing either evidence set.
    baseline_ns, candidate_ns = _utc_cutoff(baseline_since), _utc_cutoff(candidate_since)
    baseline = _Evidence(baseline_worktree, classes, baseline_ns, baseline_properties)
    candidate = _Evidence(candidate_worktree, classes, candidate_ns, candidate_properties)
    packet.require(baseline.root != candidate.root,
                   'Select distinct baseline and candidate worktree roots; one live report set is not two runs')
    before, after = _cases(baseline.summary), _cases(candidate.summary)
    result = dict(version=1, baseline=baseline.summary, candidate=candidate.summary,
                  **{bucket: [] for bucket in BUCKETS})
    for identity in sorted(before.keys() | after.keys()):
        old, new = before.get(identity), after.get(identity)
        if old is None:
            result['added_identities'].append(new)
            continue
        if new is None:
            result['missing_identities'].append(old)
            continue
        entry = dict(identity=identity, baseline=_outcome(old), candidate=_outcome(new))
        if _signature(old) == _signature(new):
            bucket = {'passed': 'unchanged_passes', 'failure': 'unchanged_failures',
                      'error': 'unchanged_errors', 'skipped': 'unchanged_skips'}[new['status']]
            result[bucket].append(dict(identity=identity, **_outcome(new)))
            continue
        old_status, new_status = old['status'], new['status']
        if new_status in BAD:
            if old_status not in BAD:
                bucket = 'new_failures'
            elif old_status == 'failure' and new_status == 'error':
                bucket = 'worsened_failures'
            else:
                bucket = 'changed_failures'
        elif new_status == 'passed':
            bucket = 'resolved_failures' if old_status in BAD else 'resolved_skips'
        else:
            bucket = 'became_skipped' if old_status in BAD else 'new_skips' if old_status == 'passed' else 'changed_skips'
        result[bucket].append(entry)
    result['test_outcomes_equivalent'] = before.keys() == after.keys() and all(
        _signature(before[key]) == _signature(after[key]) for key in before.keys() & after.keys())
    result['candidate_all_passed'] = all(case['status'] == 'passed' for case in after.values())
    result['environment_differences'] = []
    for old, new in zip(baseline.summary['classes'], candidate.summary['classes']):
        # user.dir necessarily belongs to each explicitly selected root; it is verified
        # separately, never rewritten to manufacture test/command equivalence.
        for name in sorted((old['properties'].keys() | new['properties'].keys()) - {'user.dir'}):
            if old['properties'].get(name) != new['properties'].get(name):
                result['environment_differences'].append(dict(test_class=old['name'], property=name,
                    baseline=dict(observed=name in old['properties'], value=old['properties'].get(name)),
                    candidate=dict(observed=name in new['properties'], value=new['properties'].get(name))))
    result['limits'] = dict(max_classes=MAX_CLASSES, max_directory_entries_per_side=MAX_DIRECTORY_ENTRIES,
        max_xml_bytes_per_file=packet.MAX_FILE, max_selected_xml_bytes_per_side=MAX_SELECTED_BYTES,
        max_testcases_per_side=MAX_CASES, max_xml_depth=MAX_XML_DEPTH,
        observation='Non-atomic XML observations only; no command completion, command equivalence, '
            'source identity, causality or test discovery certification. Change-and-restore can escape rechecks; '
            'reads may update atimes. No tests, retries, imports, caches, receipts or log archives are created.',
        comparison='Exact identity/status/message/type; failure-to-error is labelled worsened only as an '
            'outcome transition. Changed messages/types remain changed failures; stack text is used only '
            'when the existing reader finds no message attribute. Added/missing identities are never resolved.')
    # Keep both selections stable across the complete observation, including classification.
    baseline.recheck()
    candidate.recheck()
    return result
