#!/usr/bin/env python3
"""Report declared ROM parity obligations against current producer observations.

Inputs: a versioned partial catalogue and optional normalized, invocation-owned
producer evidence. Origin: 2026-10-29 cross-game parity measurement design.
This tool validates claims and computes counts; it does not execute or attest
comparators, import test passes, or persist reusable validation results.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
from enum import Enum
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ROMS = {'s1': '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b',
        's2': '8bca5dcef1af3e00098666fd892dc1c2a76333f9',
        's3k': 'cfbf98c36c776677290a872547ac47c53d2761d6'}
DOMAINS = ('gameplay', 'visuals', 'objects', 'badniks', 'bosses', 'camera-events',
           'terrain', 'special-stages', 'progression', 'audio')
INPUTS = ('src/main', 'src/test', 'racing', 'examples', '.mvn', 'tools/testing',
          'tools/parity', '.githooks', 'pom.xml', 'mvnw', 'mvnw.cmd',
          'mod-api-release-policy.properties')
REPORT_SCHEMA = 'openggf.parity-report.v1'
PARITY_KINDS = {'native-state', 'native-rgb', 'native-pcm', 'rom-derived'}
EVIDENCE_KINDS = PARITY_KINDS | {'engine-consistency', 'implementation-metadata'}


class Outcome(str, Enum):
    EXACT = 'exact'
    MISMATCH = 'mismatch'
    SKIPPED = 'skipped'
    INCOMPLETE = 'incomplete'
    REFERENCE_LIMITED = 'reference-limited'


@dataclass(frozen=True)
class Scope:
    game: str
    domain: str
    closed: bool
    reason: str


@dataclass(frozen=True)
class Obligation:
    id: str
    game: str
    domain: str
    required_cases: tuple[str, ...]
    definition: dict
    contract_digest: str


@dataclass(frozen=True)
class Catalogue:
    version: str
    digest: str
    scopes: tuple[Scope, ...]
    obligations: tuple[Obligation, ...]


@dataclass(frozen=True)
class SourceIdentity:
    commit: str
    fingerprint: str
    dirty: bool


@dataclass(frozen=True)
class Producer:
    id: str
    adapter_path: str
    adapter_sha256: str
    evidence_kinds: tuple[str, ...]


@dataclass(frozen=True)
class Observation:
    obligation_id: str
    case_id: str
    detail: dict


def require(condition, message):
    if not condition:
        raise ValueError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True,
                      allow_nan=False).encode('utf-8')


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def text(value, description):
    require(isinstance(value, str) and bool(value.strip()), f'{description} must be nonempty text')
    return value


def hex_digest(value, length, description):
    require(isinstance(value, str) and re.fullmatch(f'[0-9a-f]{{{length}}}', value),
            f'{description} must be a lowercase {length}-character hexadecimal digest')
    return value


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, f'duplicate JSON key {key} in {path}')
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError(f'nonfinite JSON number {value} in {path}')

    try:
        return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique,
                          parse_constant=invalid_constant)
    except (OSError, UnicodeError) as error:
        raise ValueError(f'cannot read {path}: {error}') from error


def parse_obligation(raw):
    require(isinstance(raw, dict), 'obligation must be an object')
    allowed = {'id', 'game', 'domain', 'subject', 'context', 'contract', 'reference',
               'required_cases', 'case_contracts', 'accepted_discrepancy', 'kind',
               'inventory_entry', 'native_trace'}
    require(not raw.keys() - allowed, 'unknown obligation fields')
    require(raw.get('kind', 'rom-behavior') in ('rom-behavior', 'inventory-placeholder'),
            'unknown obligation kind')
    for key in ('id', 'subject', 'contract'):
        text(raw.get(key), f'obligation {key}')
    require(raw.get('game') in ROMS and raw.get('domain') in DOMAINS, 'unknown game or domain')
    require(isinstance(raw.get('context'), dict) and raw['context'], 'explicit context is required')
    canonical(raw['context'])
    reference = raw.get('reference')
    require(isinstance(reference, dict), 'reference must be an object')
    require(reference.get('rom_sha1') == ROMS[raw['game']], 'wrong reference ROM identity')
    text(reference.get('citation'), 'reference citation')
    cases = raw.get('required_cases')
    require(isinstance(cases, list) and cases, 'nonempty required_cases are required')
    require(all(isinstance(case, str) and case.strip() for case in cases), 'invalid required case')
    require(len(cases) == len(set(cases)), 'duplicate required case')
    bindings = raw.get('case_contracts')
    require(isinstance(bindings, dict) and bindings.keys() == set(cases),
            'case_contracts must bind exactly every required case')
    for binding in bindings.values():
        require(isinstance(binding, dict), 'case contract must be an object')
        count = binding.get('expected')
        require(type(count) is int and 0 < count <= 2**63 - 1, 'invalid pinned expected count')
        text(binding.get('unit'), 'case observation unit')
        kinds = binding.get('evidence_kinds')
        require(isinstance(kinds, list) and kinds and all(kind in PARITY_KINDS for kind in kinds)
                and len(kinds) == len(set(kinds)), 'case must name specific parity evidence kinds')
    if 'accepted_discrepancy' in raw:
        text(raw['accepted_discrepancy'], 'accepted discrepancy annotation')
    return Obligation(raw['id'], raw['game'], raw['domain'], tuple(cases), raw, digest(raw))


def parse_catalogue(raw):
    require(isinstance(raw, dict) and type(raw.get('schema')) is int and raw['schema'] == 1,
            'unsupported catalogue schema')
    version = text(raw.get('version'), 'catalogue version')
    require(isinstance(raw.get('scopes'), list), 'catalogue scopes must be a list')
    scopes = []
    for scope in raw['scopes']:
        require(isinstance(scope, dict) and scope.get('game') in ROMS
                and scope.get('domain') in DOMAINS, 'invalid scope')
        require(type(scope.get('closed')) is bool, 'scope closed must be boolean')
        require(not scope['closed'], 'v1 curated inventories cannot close original-game scope')
        scopes.append(Scope(scope['game'], scope['domain'], scope['closed'],
                            text(scope.get('reason'), 'scope reason')))
    keys = [(scope.game, scope.domain) for scope in scopes]
    require(len(keys) == len(set(keys)), 'duplicate scope')
    require(set(keys) == {(game, domain) for game in ROMS for domain in DOMAINS},
            'catalogue must retain every game/domain scope, including unresolved empty scopes')
    require(isinstance(raw.get('obligations'), list), 'obligations must be a list')
    obligations = tuple(parse_obligation(item) for item in raw['obligations'])
    ids = [item.id for item in obligations]
    require(len(ids) == len(set(ids)), 'duplicate obligation ID')
    return Catalogue(version, digest(raw), tuple(scopes), obligations)


def git(root, *arguments):
    return subprocess.check_output(['git', '-C', str(root), *arguments], text=True,
                                   stderr=subprocess.PIPE).strip()


def load_catalogue(root, path):
    raw = read_json(path)
    require(isinstance(raw, dict), 'catalogue must be an object')
    binding = raw.get('reference_inventory')
    if binding is None:
        return parse_catalogue(raw)
    require(isinstance(binding, dict), 'reference inventory binding must be an object')
    relative = text(binding.get('path'), 'reference inventory path')
    require(not Path(relative).is_absolute() and '..' not in Path(relative).parts,
            'reference inventory must be inside the repository')
    inventory_path = Path(root) / relative
    require(not inventory_path.is_symlink() and inventory_path.resolve().is_relative_to(Path(root).resolve()),
            'reference inventory must be a regular repository input')
    checksum = hex_digest(binding.get('sha256'), 64, 'reference inventory digest')
    require(hashlib.sha256(inventory_path.read_bytes()).hexdigest() == checksum,
            'reference inventory digest differs from catalogue pin')
    if __package__:
        from .native_inventory import validate_inventory
    else:
        from native_inventory import validate_inventory
    subjects = validate_inventory(read_json(inventory_path))
    placeholders = []
    for subject in subjects:
        placeholders.append({
            'id': 'inventory.' + subject['id'], 'kind': 'inventory-placeholder',
            'game': subject['game'], 'domain': 'objects',
            'subject': f"{subject['table']} native dispatch ID {subject['native_id']:02X}",
            'context': {'native_table': subject['table'], 'native_id': subject['native_id']},
            'contract': 'Native dispatch semantics remain uncatalogued. This is an inventory gap, not a behavior verdict.',
            'reference': {'rom_sha1': subject['rom_sha1'],
                          'citation': f"Original {subject['table']} entry at ROM {subject['entry_address']:#x}"},
            'inventory_entry': subject,
            'required_cases': ['uncatalogued-subject'],
            'case_contracts': {'uncatalogued-subject': {
                'expected': 1, 'unit': 'unresolved native dispatch semantics',
                'evidence_kinds': ['rom-derived']}}})
    return parse_catalogue({**raw, 'obligations': raw['obligations'] + placeholders})


def source_identity(root, producers=()):
    root = Path(root).resolve()
    state = hashlib.sha256()
    files = []
    for name in dict.fromkeys((*INPUTS, *(item.adapter_path for item in producers))):
        path = root / name
        require(not path.is_symlink(), f'source input symlink is unsupported: {path}')
        if path.is_dir():
            for child in path.rglob('*'):
                if '__pycache__' not in child.parts and 'target' not in child.relative_to(path).parts:
                    require(not child.is_symlink(), f'source input symlink is unsupported: {child}')
                    if child.is_file():
                        files.append(child)
        elif path.is_file():
            files.append(path)
    for path in sorted(set(files)):
        relative = path.relative_to(root).as_posix().encode()
        file_hash = hashlib.sha256()
        with path.open('rb') as stream:
            while chunk := stream.read(1024 * 1024):
                file_hash.update(chunk)
        state.update(relative + b'\0' + file_hash.digest())
    return SourceIdentity(git(root, 'rev-parse', 'HEAD'), state.hexdigest(),
                          bool(git(root, 'status', '--porcelain', '--ignore-submodules=all')))


def parse_source(raw):
    require(isinstance(raw, dict), 'source identity must be an object')
    hex_digest(raw.get('commit'), 40, 'source commit')
    hex_digest(raw.get('fingerprint'), 64, 'source fingerprint')
    require(type(raw.get('dirty')) is bool, 'source dirty must be boolean')
    return SourceIdentity(raw['commit'], raw['fingerprint'], raw['dirty'])


def parse_producers(raw, root=None):
    require(isinstance(raw, dict) and type(raw.get('schema')) is int and raw['schema'] == 1,
            'unsupported producer registry schema')
    require(isinstance(raw.get('producers'), list), 'producer registry must contain a list')
    producers = []
    for item in raw['producers']:
        require(isinstance(item, dict), 'producer declaration must be an object')
        name = text(item.get('id'), 'producer ID')
        relative = text(item.get('adapter_path'), 'producer adapter path')
        require(not Path(relative).is_absolute() and '..' not in Path(relative).parts
                and 'target' not in Path(relative).parts, 'producer adapter must be a source path inside the repository')
        checksum = hex_digest(item.get('adapter_sha256'), 64, 'registered adapter digest')
        kinds = item.get('evidence_kinds')
        require(isinstance(kinds, list) and kinds and all(kind in EVIDENCE_KINDS for kind in kinds)
                and len(kinds) == len(set(kinds)), 'invalid producer evidence kinds')
        if root is not None:
            path = Path(root) / relative
            require(path.resolve().is_relative_to(Path(root).resolve()) and not path.is_symlink()
                    and path.is_file(), 'registered adapter is not a regular repository file')
            require(hashlib.sha256(path.read_bytes()).hexdigest() == checksum,
                    'registered adapter bytes differ from registry digest')
        require(name not in {producer.id for producer in producers}, 'duplicate producer ID')
        producers.append(Producer(name, relative, checksum, tuple(kinds)))
    return tuple(producers)


def parse_evidence(raw, catalogue, source, producers):
    require(isinstance(raw, dict) and type(raw.get('schema')) is int and raw['schema'] == 1,
            'unsupported evidence schema')
    origin = parse_source(raw.get('source'))
    require(raw.get('catalogue_digest') == catalogue.digest, 'evidence catalogue digest differs')
    producer = raw.get('producer')
    require(isinstance(producer, dict), 'producer identity is required')
    producer_id = text(producer.get('id'), 'producer ID')
    text(producer.get('version'), 'producer version')
    comparator = hex_digest(producer.get('comparator_sha256'), 64, 'comparator digest')
    require(type(raw.get('synthetic')) is bool, 'synthetic must be an explicit boolean')
    registered = next((item for item in producers if item.id == producer_id), None)
    rows = raw.get('observations')
    require(isinstance(rows, list), 'observations must be a list')
    obligations = {item.id: item for item in catalogue.obligations}
    result = []
    current = (origin.commit, origin.fingerprint) == (source.commit, source.fingerprint)
    for row in rows:
        require(isinstance(row, dict), 'observation must be an object')
        obligation = obligations.get(row.get('obligation_id'))
        require(obligation is not None, 'unknown observation obligation')
        require(obligation.definition.get('kind') != 'inventory-placeholder',
                'inventory placeholder cannot be discharged by measurement evidence')
        require(row.get('case_id') in obligation.required_cases, 'unknown observation case')
        require(row.get('contract_digest') == obligation.contract_digest, 'observation contract differs')
        require(row.get('rom_sha1') == obligation.definition['reference']['rom_sha1'], 'wrong observation ROM')
        outcome = Outcome(row.get('status'))
        kind = row.get('evidence_kind')
        require(kind in EVIDENCE_KINDS, 'unknown evidence kind')
        causal = row.get('causal_status')
        require(causal in ('isolated', 'first-divergence', 'post-divergence'), 'invalid causal status')
        require(causal != 'first-divergence' or outcome == Outcome.MISMATCH,
                'first-divergence is valid only for a mismatch')
        expected, compared = row.get('expected'), row.get('compared')
        require(type(expected) is int and 0 < expected <= 2**63 - 1, 'invalid expected observation count')
        require(type(compared) is int and 0 <= compared <= expected, 'invalid compared observation count')
        binding = obligation.definition['case_contracts'][row['case_id']]
        require(expected == binding['expected'], 'observation expected extent differs from pinned contract')
        require(row.get('unit') == binding['unit'], 'observation unit differs from pinned contract')
        if outcome in (Outcome.EXACT, Outcome.MISMATCH):
            require(compared == expected, 'decisive evidence must compare the complete expected extent')
        hex_digest(row.get('artifact_sha256'), 64, 'artifact digest')
        metrics = row.get('metrics', {})
        require(isinstance(metrics, dict), 'metrics must be an object')
        for key, value in metrics.items():
            text(key, 'metric name')
            require(type(value) in (int, float) and abs(value) <= 2**63 - 1
                    and math.isfinite(value), 'metric must be a finite bounded number')
        status = outcome.value
        if not current:
            status = 'stale'
        elif raw['synthetic']:
            status = 'synthetic'
        elif causal == 'post-divergence':
            status = 'post-divergence'
        elif registered is None:
            status = 'unregistered-producer'
        elif comparator != registered.adapter_sha256 or kind not in registered.evidence_kinds or kind not in binding['evidence_kinds']:
            status = 'insufficient-authority'
        detail = {**row, 'producer': producer, 'source': asdict(origin), 'synthetic': raw['synthetic'],
                  'reported_status': outcome.value, 'status': status}
        result.append(Observation(obligation.id, row['case_id'], detail))
    return result


def fraction(numerator, denominator):
    return {'numerator': numerator, 'denominator': denominator,
            'ratio': numerator / denominator if denominator else None}


def summary(rows):
    n = len(rows)
    passed = sum(row['status'] == 'verified' for row in rows)
    failed = sum(row['status'] == 'mismatch' for row in rows)
    return {'declared': n, 'verified': passed, 'mismatched': failed, 'unresolved': n - passed - failed,
            'inventory_placeholders': sum(row['definition'].get('kind') == 'inventory-placeholder' for row in rows),
            'verified_coverage': fraction(passed, n),
            'measurement_coverage': fraction(passed + failed, n),
            'measured_agreement': fraction(passed, passed + failed)}


def obligation_status(cases):
    statuses = [case['status'] for case in cases]
    return ('mismatch' if 'mismatch' in statuses else
            'verified' if all(status == 'exact' for status in statuses) else 'unknown')


def assess(catalogue, source, observations, producers):
    index = {}
    for observation in observations:
        key = (observation.obligation_id, observation.case_id)
        require(key not in index, f'duplicate observation: {key}')
        index[key] = observation
    rows = []
    for obligation in catalogue.obligations:
        cases = []
        for case in obligation.required_cases:
            observation = index.get((obligation.id, case))
            missing = ('uncatalogued-subject' if obligation.definition.get('kind') == 'inventory-placeholder'
                       else 'missing')
            cases.append(observation.detail if observation else {'case_id': case, 'status': missing})
        rows.append({'id': obligation.id, 'game': obligation.game, 'domain': obligation.domain,
                     'contract_digest': obligation.contract_digest, 'definition': obligation.definition,
                     'status': obligation_status(cases), 'cases': cases})
    scopes = [{**asdict(scope), **summary([row for row in rows if
               (row['game'], row['domain']) == (scope.game, scope.domain)])} for scope in catalogue.scopes]
    games = {game: summary([row for row in rows if row['game'] == game]) for game in ROMS}
    whole = {game: None for game in ROMS}
    qualified = any(case['status'] in ('exact', 'mismatch') for row in rows for case in row['cases'])
    placeholders = [row for row in rows if row['definition'].get('kind') == 'inventory-placeholder']
    return {'schema': REPORT_SCHEMA, 'catalogue_version': catalogue.version,
            'catalogue_digest': catalogue.digest, 'source': asdict(source),
            'inventory_method': 'curated behavior contracts plus generated original dispatch placeholders; complete behavior denominator is unknown',
            'reference_subjects': {'dispatch_entries': len(placeholders),
                'count_meaning': 'original dispatch entries, not distinct objects or complete behavior contracts'},
            'fraction_scope': 'of declared obligations and inventory gaps; scope open, true behavior denominator unknown',
            'qualification': 'trusted-producer-claims' if qualified else 'none',
            'producer_registry': {'schema': 1, 'producers': [{**asdict(item), 'evidence_kinds': list(item.evidence_kinds)} for item in producers]},
            'evidence_boundary': 'Validated trusted-producer claims; execution and native provenance are not independently attested by this tool.',
            'whole_game_parity': whole, 'games': games, 'scopes': scopes, 'obligations': rows}


def report_rows(raw):
    require(isinstance(raw, dict) and raw.get('schema') == REPORT_SCHEMA, 'unsupported report schema')
    parse_source(raw.get('source'))
    hex_digest(raw.get('catalogue_digest'), 64, 'report catalogue digest')
    require(isinstance(raw.get('obligations'), list), 'report obligations must be a list')
    producers = parse_producers(raw.get('producer_registry'))
    rows = {}
    for row in raw['obligations']:
        require(isinstance(row, dict), 'report obligation must be an object')
        obligation = parse_obligation(row.get('definition'))
        require(row.get('id') == obligation.id and row.get('game') == obligation.game
                and row.get('domain') == obligation.domain, 'report identity disagrees with definition')
        require(row.get('contract_digest') == obligation.contract_digest, 'report contract digest differs')
        require(row.get('status') in ('verified', 'mismatch', 'unknown'), 'invalid report verdict')
        cases = row.get('cases')
        require(isinstance(cases, list) and len(cases) == len(obligation.required_cases),
                'report must retain every required case')
        case_ids = [case.get('case_id') for case in cases if isinstance(case, dict)]
        require(len(case_ids) == len(cases) and set(case_ids) == set(obligation.required_cases),
                'report has missing, duplicate or unknown cases')
        catalogue = Catalogue('', raw['catalogue_digest'], (), (obligation,))
        current_source = parse_source(raw['source'])
        for case in cases:
            if case.get('status') == 'missing':
                continue
            if case.get('status') == 'uncatalogued-subject':
                require(obligation.definition.get('kind') == 'inventory-placeholder',
                        'behavior case cannot become an inventory placeholder')
                continue
            observation = {**case, 'status': case.get('reported_status')}
            parsed = parse_evidence({'schema': 1, 'source': case.get('source'),
                'catalogue_digest': raw['catalogue_digest'], 'producer': case.get('producer'),
                'synthetic': case.get('synthetic'), 'observations': [observation]}, catalogue, current_source, producers)
            require(parsed[0].detail['status'] == case.get('status'), 'report case source/verdict disagrees')
        require(row['status'] == obligation_status(cases), 'report verdict disagrees with its cases')
        require(obligation.id not in rows, 'duplicate report obligation')
        rows[obligation.id] = row
    return rows


def compare_reports(before, after):
    old, new = report_rows(before), report_rows(after)
    shared = old.keys() & new.keys()
    unchanged = {key for key in shared if old[key]['contract_digest'] == new[key]['contract_digest']}
    return {'schema': 'openggf.parity-delta.v1',
            'before_source': before['source'], 'after_source': after['source'],
            'catalogue_changed': before['catalogue_digest'] != after['catalogue_digest'],
            'added': sorted(new.keys() - old.keys()), 'removed': sorted(old.keys() - new.keys()),
            'removed_mismatches': sorted(key for key in old.keys() - new.keys() if old[key]['status'] == 'mismatch'),
            'producer_registry_changed': before['producer_registry'] != after['producer_registry'],
            'changed_contracts': sorted(shared - unchanged),
            'changed_references': sorted(key for key in shared if
                old[key]['definition']['reference'] != new[key]['definition']['reference']),
            'comparable_obligations': len(unchanged),
            'transitions': [{'id': key, 'before': old[key]['status'], 'after': new[key]['status']}
                            for key in sorted(unchanged) if old[key]['status'] != new[key]['status']]}


def ratio_text(value):
    ratio = value['ratio']
    return f"{value['numerator']}/{value['denominator']}" + (f" ({ratio:.1%})" if ratio is not None else ' (unavailable)')


def markdown(result):
    if result['schema'] == 'openggf.parity-delta.v1':
        return '\n'.join(['# Parity report comparison', '',
                          'Changes compare only unchanged obligation contracts.', '',
                          '```json', json.dumps(result, indent=2), '```']) + '\n'
    lines = ['# Declared parity obligations', '',
             f"Source `{result['source']['commit']}`, dirty `{str(result['source']['dirty']).lower()}`.",
             f"Fingerprint `{result['source']['fingerprint']}`.", '',
             'Whole-game parity is unavailable wherever the reference inventory is open.', '',
             'Every fraction is of declared obligations and inventory gaps; scope open, true behavior denominator unknown.', '',
             'Measured agreement describes only measured obligations and cannot establish whole-game parity.', '',
             f"Qualification `{result['qualification']}`.", '',
             '| Game | Domain | Inventory | Verified coverage | Measurement coverage | Measured agreement | Mismatch | Unresolved |',
             '| --- | --- | --- | --- | --- | --- | ---: | ---: |']
    for scope in result['scopes']:
        lines.append(f"| {scope['game']} | {scope['domain']} | {'closed' if scope['closed'] else 'open'} | "
                     f"{ratio_text(scope['verified_coverage'])} | {ratio_text(scope['measurement_coverage'])} | "
                     f"{ratio_text(scope['measured_agreement'])} | {scope['mismatched']} | {scope['unresolved']} |")
    lines.extend(['', '| Obligation | Verdict |', '| --- | --- |'])
    for row in result['obligations']:
        if row['definition'].get('kind') != 'inventory-placeholder':
            lines.append(f"| {row['id']} | {row['status']} |")
    count = result['reference_subjects']['dispatch_entries']
    if count:
        lines.extend(['', f'{count} original dispatch entries retain unresolved semantic inventory placeholders.',
                      'These are table entries, not a count of distinct objects or behavior contracts.'])
    lines.extend(['', result['evidence_boundary']])
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('report', 'inventory', 'compare'), nargs='?', default='report')
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--catalogue', type=Path, default=Path(__file__).with_name('catalogue.json'))
    parser.add_argument('--evidence', type=Path, action='append', default=[])
    parser.add_argument('--format', choices=('json', 'markdown'), default='json')
    parser.add_argument('--before', type=Path)
    parser.add_argument('--after', type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == 'compare':
            require(args.before is not None and args.after is not None, 'compare requires --before and --after')
            require(not args.evidence, 'compare does not accept evidence')
            result = compare_reports(read_json(args.before), read_json(args.after))
        else:
            require(args.before is None and args.after is None, '--before/--after require compare')
            require(args.command != 'inventory' or not args.evidence, 'inventory does not accept evidence')
            catalogue = load_catalogue(args.root, args.catalogue)
            producers = parse_producers(read_json(args.root / 'tools/parity/producers.json'), args.root)
            source = source_identity(args.root, producers)
            observations = [observation for path in args.evidence for observation in
                            parse_evidence(read_json(path), catalogue, source, producers)]
            result = assess(catalogue, source, observations, producers)
            require(source == source_identity(args.root, producers), 'source changed while deriving the report')
        print(markdown(result) if args.format == 'markdown' else json.dumps(result, indent=2, allow_nan=False), end='\n' if args.format == 'json' else '')
        return 0
    except (ValueError, TypeError, KeyError, OSError, subprocess.SubprocessError) as error:
        print(f'parity report: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
