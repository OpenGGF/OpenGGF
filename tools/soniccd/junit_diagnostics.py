#!/usr/bin/env python3
"""Bounded read-only diagnostics from one explicit source-bound run receipt.

Purpose/origin: 2026-11-05 recurring huge single-line observer XML inspection.
Inputs are caller-supplied observations, not authenticated completion or source.
Uses the existing bounded XML/snapshot reader; never invokes its Git discovery.
"""
from __future__ import annotations

import sys
sys.dont_write_bytecode = True

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import stat
import xml.etree.ElementTree as ET

import integration_packet as packet
import integration_junit_compare as strict

MAX_INPUT_LINES = 200_000
MAX_PREFIXES = 16
MAX_PREFIX_CHARACTERS = 128
MAX_ID_CHARACTERS = 512
MAX_LINES = 256
MAX_LINE_CHARACTERS = 4096
MAX_OUTPUT_BYTES = 262_144
DEFAULT_OUTPUT_BYTES = 32_768
COUNTS = ('tests', 'failures', 'errors', 'skipped')
STATUSES = ('passed', 'failure', 'error', 'skipped')
ATTRIBUTION = ('Non-atomic receipt/XML correspondence observations only. Recorded completion and '
    'source equality are supplied evidence, not independently established command completion, '
    'current source identity, API/native authenticity, discovery, a cache or permission to clean up. '
    'Change-and-restore can escape rechecks; reads may update atimes. No source, Git, queue, '
    'process, sibling report, network or gameplay-state inspection is performed.')


def _require(value, message):
    packet.require(value, message)


def _integer(value, label, minimum=0, maximum=2**63-1):
    _require(type(value) is int and minimum <= value <= maximum, 'Invalid bounded integer: ' + label)
    return value


def _text(value, label):
    _require(isinstance(value, str), 'Expected text: ' + label)
    value.encode('utf-8', errors='strict')
    return value


def _class(value):
    _text(value, 'class')
    _require(0 < len(value) <= 512 and '.' in value and packet.CLASS.fullmatch(value),
             'Require one exact fully qualified class; no patterns')
    return value


def _path(value):
    _text(value, 'path')
    _require(0 < len(value.encode('utf-8')) <= 4096 and value == os.path.normpath(value)
             and not any(ord(c) < 32 for c in value), 'Require a bounded canonical absolute path')
    path = packet.canonical(Path(value))
    _require(len(path.parts) <= 64, 'Path exceeds parent-depth limit')
    return path


def _parents(path):
    result = []
    for parent in path.parents:
        info = parent.lstat()
        _require(stat.S_ISDIR(info.st_mode), 'Not a regular parent directory')
        result.append((parent, (info.st_dev, info.st_ino, info.st_mode)))
    return result


def _recheck_parents(saved):
    for path, expected in saved:
        info = path.lstat()
        _require((info.st_dev, info.st_ino, info.st_mode) == expected, 'Input parent changed')


def _counts(value):
    packet.keys(value, COUNTS, 'counts')
    return {key: _integer(value[key], key, maximum=strict.MAX_CASES) for key in COUNTS}


def _json(data):
    def constant(_):
        raise packet.PacketError('Nonfinite JSON numbers are unsupported')
    return json.loads(data.decode('utf-8', errors='strict'), object_pairs_hook=packet.unique,
                      parse_constant=constant)


def _encode(value):
    return (json.dumps(value, ensure_ascii=False, allow_nan=False, sort_keys=True,
                       separators=(',', ':')) + '\n').encode('utf-8')


def _utc(value):
    value = _text(value, 'observation timestamp')
    moment = datetime.fromisoformat(value.replace('Z', '+00:00'))
    _require(moment.tzinfo is not None and moment.utcoffset() == timezone.utc.utcoffset(moment),
             'Observation timestamps must be explicit UTC')
    return moment


def _source(value):
    packet.keys(value, ('head', 'fingerprint', 'files', 'bytes', 'gitlinks'), 'source identity')
    _require(isinstance(value['head'], str) and packet.COMMIT.fullmatch(value['head']), 'Invalid recorded source HEAD')
    _require(isinstance(value['fingerprint'], str) and packet.HASH.fullmatch(value['fingerprint']), 'Invalid recorded source fingerprint')
    _integer(value['files'], 'source files')
    _integer(value['bytes'], 'source bytes')
    _integer(value['gitlinks'], 'source gitlink count', maximum=value['files'])


def _case_inventory(group):
    cases = group.get('cases')
    _require(isinstance(cases, list) and 0 < len(cases) <= strict.MAX_CASES, 'Missing/bounded case inventory')
    counts = dict.fromkeys(COUNTS, 0)
    identities = set()
    for case in cases:
        _require(isinstance(case, dict) and set(case) in (
            {'identity', 'status', 'message', 'type'},
            {'identity', 'status', 'message', 'type', 'reported_classname'}), 'Invalid receipt case fields')
        identity = _text(case['identity'], 'case identity')
        _require(identity.startswith(group['name'] + '#') and len(identity) > len(group['name']) + 1
                 and identity not in identities, 'Wrong or duplicate receipt case identity')
        identities.add(identity)
        _require(case['status'] in STATUSES, 'Unknown receipt case outcome')
        _text(case['message'], 'case message'); _text(case['type'], 'case type')
        if 'reported_classname' in case:
            _require(case['reported_classname'] == group['name'].rsplit('.', 1)[-1], 'Wrong reported simple case owner')
        counts['tests'] += 1
        if case['status'] != 'passed':
            counts[{'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[case['status']]] += 1
    _require(_counts(group.get('counts')) == counts, 'Receipt case/count mismatch')
    return counts


def _receipt(document, name):
    packet.keys(document, ('version', 'kind', 'run', 'attribution', 'limits'), 'receipt')
    _require(type(document['version']) is int and document['version'] == 1
             and document['kind'] == 'source-bound-run-observations', 'Require a version1 source-bound run receipt')
    _text(document['attribution'], 'receipt attribution')
    _require(isinstance(document['limits'], dict), 'Missing receipt limits')
    run = document['run']
    _require(isinstance(run, dict) and type(run.get('version')) is int and run['version'] == 1
             and run.get('executed') is True and run.get('stage') == 'installed', 'Receipt has no executed installed run')
    _require(run.get('status') in ('passed', 'failed') and type(run.get('clean')) is bool
             and not run.get('evidence_error'), 'Receipt is incomplete or lacks an explicit terminal run status')
    code = _integer(run.get('maven_returncode'), 'recorded return code', minimum=-2**31, maximum=2**31-1)
    _require(_utc(run.get('completed_at')) >= _utc(run.get('started_at')), 'Incomplete/reversed observation times')
    _source(run.get('source_before')); _source(run.get('source_after'))
    _require(run['source_before'] == run['source_after'], 'Recorded before/after source differs')
    classes = run.get('classes')
    _require(isinstance(classes, list) and 0 < len(classes) <= strict.MAX_CLASSES
             and len(set(classes)) == len(classes), 'Invalid declared class inventory')
    for item in classes: _class(item)
    _require(name in classes, 'Selected class is absent from receipt')
    expected = run.get('expected_tests')
    _require(isinstance(expected, dict) and set(expected) == set(classes), 'Missing expected class counts')
    for count in expected.values(): _integer(count, 'expected tests', minimum=1, maximum=strict.MAX_CASES)
    _require(isinstance(run.get('issues'), list) and all(isinstance(v, str) for v in run['issues']), 'Invalid recorded run issues')
    junit = run.get('junit')
    _require(isinstance(junit, dict) and isinstance(junit.get('classes'), list), 'Missing complete JUnit observations')
    _require(junit.get('head_at_inspection') == run['source_before']['head'], 'Recorded JUnit/source HEAD mismatch')
    _integer(junit.get('since_ns'), 'XML cutoff', minimum=1)
    requested = junit.get('requested_properties')
    _require(isinstance(requested, dict) and all(k in packet.PROPERTIES - {'java.version', 'user.dir'}
             and isinstance(v, str) for k, v in requested.items()), 'Unsupported requested property inventory')
    _require(isinstance(junit.get('issues'), list) and all(isinstance(v, str) for v in junit['issues']), 'Invalid recorded XML issues')
    groups, total = {}, dict.fromkeys(COUNTS, 0)
    environment_complete = True
    for group in junit['classes']:
        _require(isinstance(group, dict) and group.get('name') in classes and group['name'] not in groups,
                 'Receipt suite/class mismatch')
        groups[group['name']] = group
        for key, value in _case_inventory(group).items(): total[key] += value
        _require(total['tests'] <= strict.MAX_CASES, 'Receipt exceeds testcase budget')
        props = group.get('properties')
        _require(isinstance(props, dict) and set(props) == {'java.version', 'user.dir', *requested}
                 and all(v is None or isinstance(v, str) for v in props.values()), 'Invalid selected property projection')
        # Receipt/XML equality alone does not establish the producer's environment verdict.
        # Match the existing summary's nonempty JVM/worktree/requested-property conditions
        # for every recorded class, including a nonselected failed environment.
        if (not props['java.version'] or props['user.dir'] != junit.get('worktree')
                or any(props[key] is None or props[key] != value for key, value in requested.items())):
            environment_complete = False
    _require(set(groups) == set(classes) and all(junit.get(k) == v and type(junit.get(k)) is int for k, v in total.items()),
             'Receipt aggregate count/class mismatch')
    xml_clean = not (junit['issues'] or not environment_complete or total['failures'] or total['errors'] or total['skipped'])
    _require(type(junit.get('clean')) is bool and junit['clean'] == xml_clean, 'Inconsistent recorded XML verdict')
    expected_match = all(groups[k]['counts']['tests'] == expected[k] for k in classes)
    should_pass = code == 0 and xml_clean and not run['issues'] and expected_match
    _require(run['clean'] == (run['status'] == 'passed') and (run['status'] == 'passed') == should_pass,
             'Inconsistent recorded run verdict')
    return run, junit, groups[name]


def _project(suite, name, requested):
    _require(suite.tag == 'testsuite' and suite.get('name') == name, 'XML suite/class mismatch')
    _require(all(n.tag in ('properties', 'testcase', 'system-out', 'system-err') for n in suite), 'Unsupported suite structure')
    props = {}
    for node in suite.findall('./properties/property'):
        key = node.get('name')
        if key in packet.PROPERTIES:
            _require(key not in props, 'Duplicate allowlisted XML property')
            props[key] = node.get('value')
    selected = {k: props.get(k) for k in ('java.version', 'user.dir', *requested)}
    cases, counts, identities = [], dict.fromkeys(COUNTS, 0), set()
    for case in suite.findall('testcase'):
        owner, label = case.get('classname'), case.get('name')
        _require(owner in (name, name.rsplit('.', 1)[-1]) and bool(label), 'XML case owner/name mismatch')
        _require(all(n.tag in ('failure', 'error', 'skipped', 'system-out', 'system-err') for n in case), 'Unsupported case structure')
        identity = name + '#' + label
        _require(identity not in identities, 'Duplicate XML case identity')
        identities.add(identity)
        outcomes = [n for n in case if n.tag in ('failure', 'error', 'skipped')]
        _require(len(outcomes) <= 1, 'Ambiguous XML case outcome')
        node = outcomes[0] if outcomes else None
        status = node.tag if node is not None else 'passed'
        item = dict(identity=identity, status=status, message=node.get('message', node.text or '') if node is not None else '',
                    type=node.get('type', '') if node is not None else '')
        if owner != name: item['reported_classname'] = owner
        cases.append(item); counts['tests'] += 1
        if status != 'passed': counts[{'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[status]] += 1
    _require(cases and all(suite.get(k) is not None and suite.get(k).isdigit() and int(suite.get(k)) == v for k, v in counts.items()),
             'XML declared/observed count mismatch')
    return selected, cases, counts


def _diagnostic_fields(suite, name):
    case_index = 0
    for node in suite:
        if node.tag in ('system-out', 'system-err'):
            _require(not len(node), 'Nested stream XML is unsupported')
            yield dict(scope='suite', kind=node.tag), node.text or '', True
        elif node.tag == 'testcase':
            identity = name + '#' + node.get('name')
            metadata = dict(scope='case', case_index=case_index, case_identity=identity[:MAX_ID_CHARACTERS],
                case_identity_characters=len(identity), case_identity_truncated=len(identity) > MAX_ID_CHARACTERS,
                case_identity_sha256=packet.digest(identity.encode('utf-8')))
            for child in node:
                _require(not len(child), 'Nested diagnostic XML is unsupported')
                if child.tag in ('system-out', 'system-err'):
                    yield dict(metadata, kind=child.tag), child.text or '', True
                else:
                    for field, text in [('message', child.get('message', '')), ('type', child.get('type', '')), ('text', child.text or '')]:
                        yield dict(metadata, kind=child.tag, field=field), text, False
            case_index += 1


def _records(suite, name, prefixes, max_lines, max_line_chars):
    stats = dict(input_lines=0, input_characters=0, filtered_lines=0, filtered_characters=0,
                 selected_lines=0, selected_characters=0)
    records = []
    for metadata, text, stream in _diagnostic_fields(suite, name):
        for line_number, line in enumerate(text.splitlines(keepends=True) or [''], 1):
            stats['input_lines'] += 1; stats['input_characters'] += len(line)
            _require(stats['input_lines'] <= MAX_INPUT_LINES, 'Diagnostic input line budget exceeded; inspection incomplete')
            if stream and prefixes and not any(line.startswith(p) for p in prefixes):
                stats['filtered_lines'] += 1; stats['filtered_characters'] += len(line)
                continue
            stats['selected_lines'] += 1; stats['selected_characters'] += len(line)
            if len(records) < max_lines:
                records.append(dict(metadata, line=line_number, text=line[:max_line_chars],
                    input_characters=len(line), omitted_characters=max(0, len(line)-max_line_chars),
                    truncated=len(line) > max_line_chars))
    return records, stats


def _finish(result, records, stats, limit):
    result['output_bytes'] = 0
    while True:
        emitted_input = sum(r['input_characters'] for r in records)
        emitted_text = sum(len(r['text']) for r in records)
        counts = dict(stats, emitted_lines=len(records), omitted_lines=stats['selected_lines']-len(records),
            omitted_characters=stats['selected_characters']-emitted_input,
            truncated_lines=sum(r['truncated'] for r in records), truncated_characters=emitted_input-emitted_text,
            emitted_text_characters=emitted_text, case_identity_truncations=sum(r.get('case_identity_truncated', False) for r in records))
        complete = not (counts['omitted_lines'] or counts['truncated_lines'] or counts['case_identity_truncations'])
        result['diagnostics'] = dict(counts, selected_complete=complete,
            all_diagnostics_complete=complete and not counts['filtered_lines'], records=records)
        for _ in range(8):
            payload = _encode(result)
            if result['output_bytes'] == len(payload): break
            result['output_bytes'] = len(payload)
        if len(payload) <= limit: return payload
        _require(bool(records), 'Output budget cannot contain required metadata; inspection incomplete')
        records.pop()


def inspect_diagnostics(evidence, test_class, *, prefixes=(), max_lines=32, max_line_chars=512,
                        max_output_bytes=DEFAULT_OUTPUT_BYTES):
    """Return bounded JSON bytes after explicit receipt/XML verification and final rechecks."""
    name = _class(test_class)
    _integer(max_lines, 'max lines', maximum=MAX_LINES)
    _integer(max_line_chars, 'max line characters', minimum=1, maximum=MAX_LINE_CHARACTERS)
    _integer(max_output_bytes, 'max output bytes', minimum=4096, maximum=MAX_OUTPUT_BYTES)
    _require(isinstance(prefixes, (tuple, list)) and len(prefixes) <= MAX_PREFIXES
             and len(set(prefixes)) == len(prefixes), 'Too many or duplicate literal prefixes')
    for prefix in prefixes:
        _text(prefix, 'prefix')
        _require(0 < len(prefix) <= MAX_PREFIX_CHARACTERS and not any(ord(c) < 32 and c != '\t' for c in prefix), 'Invalid literal prefix')
    path = _path(evidence); parents = _parents(path); saved = packet.snapshot(path)
    _require(saved.info.st_nlink == 1, 'Hardlinked receipt evidence is unsupported')
    run, junit, group = _receipt(_json(saved.data), name)
    root = _path(junit.get('worktree')); xml = _path(group.get('xml'))
    _require(xml == root/'target/surefire-reports'/('TEST-'+name+'.xml') and not path.is_relative_to(root), 'Receipt/XML path correspondence mismatch')
    xml_parents = _parents(xml); directories = strict._directories(root)
    info = xml.lstat()
    # Reuse the existing pure bounded/no-alias UTF8/XML/depth/case reader, not its Git/discovery constructor.
    reader = object.__new__(strict._Evidence)
    reader.inventory = {xml.name: packet.signature(info)}; reader.saved = []; reader.bytes = reader.cases = 0
    observed = reader.read(xml)
    _require(isinstance(group.get('xml_sha256'), str) and packet.HASH.fullmatch(group['xml_sha256'])
             and type(group.get('xml_bytes')) is int and group['xml_bytes'] == len(observed.data)
             and group['xml_sha256'] == packet.digest(observed.data), 'Selected XML hash/size mismatch')
    _require(type(group.get('mtime_ns')) is int and group['mtime_ns'] == observed.info.st_mtime_ns
             and group['mtime_ns'] >= junit['since_ns'], 'Selected XML is stale or changed from receipt')
    suite = ET.fromstring(observed.data)
    props, cases, counts = _project(suite, name, junit['requested_properties'])
    _require(props == group['properties'], 'Selected XML/property correspondence mismatch')
    _require(cases == group['cases'] and counts == group['counts'], 'Selected XML/case/count correspondence mismatch')
    records, stats = _records(suite, name, prefixes, max_lines, max_line_chars)
    result = dict(version=1, kind='junit-diagnostics-observations', inspection_status='verified-correspondence',
        test_class=name, evidence=dict(path=str(path), sha256=packet.digest(saved.data), bytes=len(saved.data)),
        xml=dict(path=str(xml), sha256=packet.digest(observed.data), bytes=len(observed.data), mtime_ns=observed.info.st_mtime_ns),
        run_status=run['status'], run_clean=run['clean'], maven_returncode=run['maven_returncode'],
        run_counts={key: junit[key] for key in COUNTS}, counts=counts, counts_scope='selected class',
        expected_tests=run['expected_tests'][name], expected_count_matches=run['expected_tests'][name] == counts['tests'],
        recorded_source_equal=True, recorded_source=dict(head=run['source_before']['head'], fingerprint=run['source_before']['fingerprint'],
            identity_sha256=packet.digest(_encode(run['source_before']))),
        case_inventory_sha256=packet.digest(_encode(cases)), properties=props, prefixes=list(prefixes), attribution=ATTRIBUTION,
        text_scope='XML1.0 decoded character data; XML normalizes literal CRLF/CR to LF. Unicode line endings are retained by splitlines(keepends=True); no stripping, ellipsis or gameplay parsing.',
        limits=dict(max_input_bytes_per_file=packet.MAX_FILE, max_xml_depth=strict.MAX_XML_DEPTH,
            max_testcases=strict.MAX_CASES, max_input_lines=MAX_INPUT_LINES, max_lines=max_lines,
            max_line_characters=max_line_chars, max_output_bytes=max_output_bytes, max_case_identity_characters=MAX_ID_CHARACTERS))
    payload = _finish(result, records, stats, max_output_bytes)
    packet.unchanged(observed, message='Selected XML changed during diagnostic inspection')
    packet.unchanged(saved, message='Receipt changed during diagnostic inspection')
    _recheck_parents(parents); _recheck_parents(xml_parents)
    _require(strict._directories(root) == directories, 'Report parent directories changed')
    return payload


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        raise packet.PacketError('Invalid command arguments: ' + message[:384])


def main(argv=None):
    parser = _Parser(description=__doc__)
    parser.add_argument('--evidence', required=True, help='Explicit canonical source-bound-run-observations JSON')
    parser.add_argument('--class', dest='test_class', required=True, help='One exact fully qualified class')
    parser.add_argument('--prefix', action='append', default=[], help='Repeatable literal stream-line prefix; no regex or stripping')
    parser.add_argument('--max-lines', type=int, default=32, help='Emitted diagnostic lines: 0..256')
    parser.add_argument('--max-line-chars', type=int, default=512, help='Code points per line: 1..4096')
    parser.add_argument('--max-output-bytes', type=int, default=DEFAULT_OUTPUT_BYTES, help='Complete UTF8 JSON+LF: 4096..262144 bytes')
    try:
        args = parser.parse_args(argv)
        payload = inspect_diagnostics(args.evidence, args.test_class, prefixes=args.prefix,
            max_lines=args.max_lines, max_line_chars=args.max_line_chars, max_output_bytes=args.max_output_bytes)
        sys.stdout.write(payload.decode('utf-8'))
        return 0
    except (packet.PacketError, OSError, ValueError, TypeError, KeyError, ET.ParseError, UnicodeError, RecursionError) as error:
        message = str(error).encode('utf-8', errors='replace').decode('utf-8')[:512]
        sys.stderr.write(_encode(dict(version=1, inspection_status='rejected', incomplete=True, error=message,
            observation='No positive diagnostic correspondence published; no inputs changed by this tool.')).decode('utf-8'))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
