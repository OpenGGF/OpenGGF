#!/usr/bin/env python3
"""Compare bounded category-run failures and skips with an explicit light baseline.

Inputs: completed run_categories.py results.json and a provenance-bearing JSON
baseline with failures/skips arrays. Origin: Mutator Lab expansion, 2026-10-08,
afb16ca18; promoted from the task's repeated full-assertion comparison probe.
This only compares negative cases. Selection, source identity, whole-run completion
and baseline reuse authority remain the caller's responsibility. No tests are run,
and no runner diagnostics are retained or deleted by this tool.
"""
import argparse
import json
from pathlib import Path
import re
import sys

PREFIX = 'org.opentest4j.AssertionFailedError: '
SSZ = ('com.openggf.tools.TestSszTailsColdRouteCapture#'
       'coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]')


class EvidenceError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise EvidenceError(message)


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, f'Duplicate JSON key: {key}')
            result[key] = value
        return result
    try:
        return json.loads(path.read_text(), object_pairs_hook=unique)
    except (OSError, ValueError) as error:
        raise EvidenceError(f'Cannot read {path}: {error}') from error


def line(value, label, cap=None):
    require(isinstance(value, str) and bool(value), f'Missing {label}')
    value = value.rstrip('\r\n')
    require('\n' not in value and '\r' not in value, f'Multiline {label} needs explicit review')
    require(cap is None or len(value) < cap, f'Capped {label} needs complete evidence')
    return value


def identity(case):
    class_name = line(case.get('class'), 'test class', 512)
    test_name = line(case.get('test'), 'test name', 512)
    return class_name + '#' + test_name


def assertion(case):
    key = identity(case)
    message = line(case.get('message'), f'assertion for {key}')
    require(len(message) <= 2048, f'Unexpected message length for {key}')
    if len(message) == 2048:
        detail = case.get('detail', '')
        require(isinstance(detail, str) and bool(detail), f'Missing full assertion for {key}')
        first = detail.splitlines()[0]
        require(len(first) < 8192, f'Capped assertion detail for {key}')
        complete = first.removeprefix(PREFIX)
        prefix = message.removeprefix(PREFIX)
        require(complete.startswith(prefix) and len(complete) > len(prefix),
                f'Detail does not complete the exact assertion for {key}')
        return complete
    return message.removeprefix(PREFIX)


def normalize(key, value, enabled):
    value = value.removeprefix(PREFIX)
    if enabled and key == SSZ:
        # This opt-in recognizes the owner's already-verified @HASH projection as
        # well as actual Java identity hashes. No other identity/value is changed.
        value = re.sub(r'RewindObjectStateBlob@(?:[0-9a-fA-F]+|HASH)\b',
                       'RewindObjectStateBlob@<identity>', value)
    return value


def index_cases(cases, label, key_function):
    require(isinstance(cases, list), f'Missing {label} inventory')
    result = {}
    for case in cases:
        require(isinstance(case, dict), f'Invalid {label} entry')
        key = key_function(case)
        require(key not in result, f'Duplicate {label} identity: {key}')
        result[key] = case
    return result


def validate_lane(lane):
    require(isinstance(lane, dict), 'Invalid lane')
    require(lane.get('status') != 'incomplete', 'Incomplete lane cannot be compared')
    for field in ('reports', 'tests', 'failures', 'errors', 'skipped',
                  'failed_cases_omitted', 'skipped_cases_omitted'):
        require(type(lane.get(field)) is int and lane[field] >= 0, f'Invalid lane counter: {field}')
    require(lane['failed_cases_omitted'] == lane['skipped_cases_omitted'] == 0,
            'Omitted identities prevent comparison')
    require(lane['reports'] > 0 and lane['tests'] > lane['skipped'], 'Empty or wholly skipped lane')
    require(lane['tests'] >= lane['failures'] + lane['errors'] + lane['skipped'],
            'Outcomes exceed executed case count')
    require(type(lane.get('exit_code')) is int and lane['exit_code'] in (0, 1),
            'Missing or incomplete Maven exit')
    require(lane['exit_code'] == int(bool(lane['failures'] or lane['errors'])),
            'Maven exit disagrees with test outcomes; inspect build/fork failures')
    failed = index_cases(lane.get('failed_cases'), 'failure', identity)
    skipped = index_cases(lane.get('skipped_cases'), 'skip', identity)
    require(not failed.keys() & skipped.keys(), 'Identity is both failed and skipped')
    for kind, counter in (('failure', 'failures'), ('error', 'errors')):
        require(sum(case.get('kind') == kind for case in failed.values()) == lane[counter],
                f'{kind} inventory disagrees with totals')
    require(len(failed) == lane['failures'] + lane['errors'], 'Unknown failure kind')
    require(len(skipped) == lane['skipped'], 'Skip inventory disagrees with totals')
    return failed, skipped


def compare(lanes, reference, normalize_known_ssz_blobs=False):
    require(isinstance(reference, dict), 'Missing baseline')
    source = reference.get('source') or reference.get('tested_sha')
    require(isinstance(source, str) and re.fullmatch('[0-9a-fA-F]{40}', source),
            'Baseline must identify its exact tested source')
    line(reference.get('run'), 'baseline run')
    expected_failures = index_cases(reference.get('failures'), 'baseline failure',
                                   lambda case: line(case.get('identity'), 'baseline identity'))
    expected_skips = index_cases(reference.get('skips'), 'baseline skip',
                                lambda case: line(case.get('identity'), 'baseline identity'))
    require(not expected_failures.keys() & expected_skips.keys(), 'Baseline identity has two outcomes')
    expected_assertions = {}
    for key, case in expected_failures.items():
        require(case.get('kind') in ('failure', 'error'), f'Missing baseline failure kind: {key}')
        line(case.get('type'), f'baseline exception type: {key}', 512)
        expected_assertions[key] = line(case.get('message'), f'baseline assertion: {key}')
    expected_causes = {key: line(case.get('message'), f'baseline skip cause: {key}', 4096)
                       for key, case in expected_skips.items()}
    require(isinstance(lanes, list) and len(lanes) == 2
            and all(isinstance(lane, dict) for lane in lanes)
            and {lane.get('lane') for lane in lanes} == {'ordinary', 'guards'},
            'Both completed ordinary and guard lanes are required')
    inventories = {lane['lane']: validate_lane(lane) for lane in lanes}
    failed, skipped = inventories['ordinary']
    assertions = {key: assertion(case) for key, case in failed.items()}
    causes = {key: line(case.get('reason'), f'skip cause: {key}', 4096)
              for key, case in skipped.items()}
    changed = sorted(key for key in failed.keys() & expected_failures.keys()
                     if failed[key].get('kind') != expected_failures[key]['kind']
                     or failed[key].get('type') != expected_failures[key]['type']
                     or normalize(key, assertions[key], normalize_known_ssz_blobs)
                     != normalize(key, expected_assertions[key], normalize_known_ssz_blobs))
    guard_failed, guard_skipped = inventories['guards']
    # Inspect full guard evidence too; truncated diagnostics never become a pass.
    for case in guard_failed.values():
        assertion(case)
    for key, case in guard_skipped.items():
        line(case.get('reason'), f'guard skip cause: {key}', 4096)
    result = {
        'new_failures': sorted(failed.keys() - expected_failures.keys()),
        'missing_expected_failures': sorted(expected_failures.keys() - failed.keys()),
        'changed_failures': changed,
        'new_skips': sorted(skipped.keys() - expected_skips.keys()),
        'missing_expected_skips': sorted(expected_skips.keys() - skipped.keys()),
        'changed_skip_causes': sorted(key for key in skipped.keys() & expected_skips.keys()
                                     if causes[key] != expected_causes[key]),
        'guard_failures': sorted(guard_failed), 'guard_skips': sorted(guard_skipped),
    }
    result['negative_cases_unchanged'] = not any(result.values())
    result.update(complete_assertions=assertions, skip_causes=causes,
                  normalize_known_ssz_blobs=normalize_known_ssz_blobs)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--results', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--normalize-known-ssz-blobs', action='store_true',
                        help='Only after independently verifying the named SSZ Java blob hashes')
    args = parser.parse_args()
    if args.output.resolve() in {args.baseline.resolve(), args.results.resolve()}:
        parser.error('Output must not replace either input')
    try:
        reference, lanes = read_json(args.baseline), read_json(args.results)
        result = compare(lanes, reference, args.normalize_known_ssz_blobs)
        summary = {'status': 'compared', 'baseline_path': str(args.baseline), 'results_path': str(args.results),
                   'baseline_source': reference.get('source') or reference['tested_sha'],
                   'baseline_run': reference['run'], 'comparison': result,
                   'limits': 'Negative-case comparison only; verify source, selection, terminal status and baseline authority separately.'}
        args.output.write_text(json.dumps(summary, indent=2) + '\n')
        print(json.dumps({key: value for key, value in result.items()
                          if key not in ('complete_assertions', 'skip_causes')}, indent=2))
        return 0 if result['negative_cases_unchanged'] else 1
    except (EvidenceError, OSError) as error:
        print(str(error), file=sys.stderr)
        # Replace a prior comparison at the requested output with the invalid
        # verdict, so a failed retry cannot leave a stale successful summary.
        try:
            args.output.write_text(json.dumps({'status': 'invalid', 'error': str(error)}, indent=2) + '\n')
        except OSError as output_error:
            print(f'Cannot write invalid verdict: {output_error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
