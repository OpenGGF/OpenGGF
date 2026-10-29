#!/usr/bin/env python3
"""Measure catalogue-bound native trace gates through the existing Maven queue.

Inputs: verified original ROMs and pinned native V5 fixtures. Raw Maven/XML/trace
artifacts are invocation-owned temporary files. Origin: October 29, 2026 parity
producer integration. A gate verdict is not a frame or whole-game accuracy ratio.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import csv
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZipFile

import report as core

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'testing'))
from category_artifacts import stop_process_tree
from compare_release_traces import completed_summary, fresh
from maven_queue import handle_termination

_spec = importlib.util.spec_from_file_location('parity_trace_report_validator',
    Path(__file__).resolve().parents[1] / 'testing/validate-trace-reports.py')
validator = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = validator
_spec.loader.exec_module(validator)

UNIT = 'completed existing trace gate'
PRODUCER_ID = 'native-trace-gate-v1'
ROM_PROPERTIES = {'s1': 'sonic1.rom.path', 's2': 'sonic2.rom.path', 's3k': 's3k.rom.path'}


@dataclass(frozen=True)
class TraceGate:
    obligation: core.Obligation
    class_name: str
    method: str
    fixture: str
    bk2: str
    profile: str
    lane: str
    logical_key: str
    recorded_rows: int


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def repository_file(root, relative):
    path = Path(relative)
    core.require(not path.is_absolute() and '..' not in path.parts, 'input must be repository-relative')
    path = root / path
    core.require(not path.is_symlink() and path.is_file() and path.resolve().is_relative_to(root.resolve()),
                 f'nonregular or escaping reference input: {relative}')
    return path


def selected_gates(catalogue, games):
    core.require(games and len(games) == len(set(games)) and set(games) <= core.ROMS.keys(),
                 'games must be unique members of s1,s2,s3k')
    gates = []
    for game in games:
        candidates = [o for o in catalogue.obligations if o.game == game and 'native_trace' in o.definition]
        core.require(len(candidates) == 1, f'exactly one registered trace gate is required for {game}')
        obligation = candidates[0]
        binding = obligation.definition['native_trace']
        core.require(isinstance(binding, dict), 'native_trace must be an object')
        core.require(re.fullmatch(r'com\.openggf\.tests\.trace\.' + game + r'\.Test\w+TraceReplay',
                                  binding.get('class', '')), 'unexpected native trace class')
        core.require(binding.get('method') == 'replayMatchesTrace' and binding.get('profile') == 'trace'
                     and binding.get('lane') == 'single', 'unsupported native trace gate owner')
        core.require(type(binding.get('recorded_rows')) is int and binding['recorded_rows'] > 0,
                     'native gate requires its intended recorded row extent')
        for key in ('fixture', 'bk2', 'logical_key'):
            core.text(binding.get(key), 'native gate ' + key)
        core.require(obligation.required_cases == ('complete-replay',), 'native gate requires one complete-replay case')
        case = obligation.definition['case_contracts']['complete-replay']
        core.require(case['expected'] == 1 and case['unit'] == UNIT and case['evidence_kinds'] == ['native-state'],
                     'native gate must declare one completed existing trace gate')
        gates.append(TraceGate(obligation, binding['class'], binding['method'], binding['fixture'],
            binding['bk2'], binding['profile'], binding['lane'], binding['logical_key'], binding['recorded_rows']))
    return gates


def checked_environment(environment=None):
    environment = dict(os.environ if environment is None else environment)
    forbidden = {'MAVEN_ARGS', 'MAVEN_OPTS', 'MAVEN_PROJECTBASEDIR', 'MAVEN_BASEDIR',
                 'MAVEN_EXT_CLASS_PATH', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'}
    for key, value in environment.items():
        allowed_graphics = key in {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'} and value == '-Dopenggf.headless.gl=surfaceless-egl'
        core.require(not value or allowed_graphics or key == 'OGGF_SCRATCH_ROOT'
                     or not (key in forbidden or key.startswith(('OGGF_', 'OPENGGF_TRACE_', 'TRACE_'))),
                     f'injected native measurement environment is unsupported: {key}')
    environment['MAVEN_SKIP_RC'] = '1'
    return environment


def check_project_options(root):
    allowed = re.compile(r'-D(?:mse=(?:off|relaxed)|java\.io\.tmpdir=[^\s]+|openggf\.headless\.gl=surfaceless-egl)')
    for name in ('.mvn/maven.config', '.mvn/jvm.config'):
        path = root / name
        if path.exists():
            core.require(all(allowed.fullmatch(option) for option in path.read_text().split()),
                         f'unsupported native measurement options in {name}')


def resolved_movie(fixture, metadata):
    source = metadata.get('source_bk2')
    if source is not None:
        core.require(isinstance(source, str), 'invalid source_bk2')
        if source.strip():
            for candidate in (fixture.parent / '_movies' / source, fixture.parent / source):
                if candidate.exists():
                    return candidate
    local = [path for path in fixture.iterdir() if str(path).endswith('.bk2')]
    core.require(len(local) == 1, 'missing or ambiguous local native movie selection')
    return local[0]


def verify_inputs(root, gates, rom_paths):
    snapshots = {}
    for gate in gates:
        game, reference = gate.obligation.game, gate.obligation.definition['reference']
        rom = rom_paths.get(game)
        core.require(isinstance(rom, Path) and rom.is_absolute() and rom.is_file(), f'absolute original ROM path required for {game}')
        raw = rom.read_bytes()
        core.require(hashlib.sha1(raw).hexdigest() == core.ROMS[game] == reference['rom_sha1'], f'wrong original ROM for {game}')
        md5 = hashlib.md5(raw).hexdigest()
        core.require(md5 == reference.get('legacy_movie_md5'), f'wrong reference movie ROM digest for {game}')
        snapshots['rom:' + game] = hashlib.sha256(raw).hexdigest()
        inputs = reference.get('inputs')
        core.require(isinstance(inputs, dict) and inputs, 'native gate requires pinned reference inputs')
        for relative, expected in inputs.items():
            core.hex_digest(expected, 64, 'reference input digest')
            actual = sha256(repository_file(root, relative))
            core.require(actual == expected, f'native reference input differs: {relative}')
            snapshots[relative] = actual
        fixture = root / gate.fixture
        core.require(fixture.is_dir() and fixture.resolve().is_relative_to(root.resolve()), 'invalid native fixture directory')
        actual_files = {path.relative_to(root).as_posix() for path in fixture.rglob('*') if path.is_file()}
        core.require(actual_files | {gate.bk2} == set(inputs), 'native reference must pin every fixture file and movie')
        meta_path = gate.fixture + '/metadata.json'
        core.require(meta_path in inputs and gate.bk2 in inputs, 'native metadata and movie must be pinned')
        metadata = core.read_json(root / meta_path)
        core.require(isinstance(metadata, dict) and metadata.get('game') == game
                     and type(metadata.get('trace_schema')) is int and metadata['trace_schema'] == 5
                     and metadata.get('recorder') == 'native-bizhawk-headless'
                     and type(metadata.get('trace_frame_count')) is int
                     and metadata['trace_frame_count'] == gate.recorded_rows, 'native fixture provenance/extent differs')
        core.require(resolved_movie(fixture, metadata).resolve() == (root / gate.bk2).resolve(),
                     'Java resolved movie differs from the pinned native movie')
        physics = repository_file(root, gate.fixture + '/physics.csv.gz')
        with gzip.open(physics, 'rt', newline='') as stream:
            core.require(sum(1 for _ in csv.DictReader(stream)) == gate.recorded_rows, 'native CSV row extent differs')
        with ZipFile(repository_file(root, gate.bk2)) as movie:
            core.require(len(movie.namelist()) == len(set(movie.namelist())), 'duplicate BK2 members')
            header = movie.read('Header.txt').decode('utf-8-sig')
        fields = {}
        for line in header.splitlines():
            if line.strip():
                key, _, value = line.partition(' ')
                core.require(key not in fields, 'duplicate BK2 header field')
                fields[key] = value.strip()
        core.require(fields.get('Core') == reference.get('core') == 'Genplus-gx'
                     and fields.get('emuVersion') == 'Version 2.11'
                     and reference.get('emulator') == 'BizHawk 2.11', 'native reference core/version differs')
        # These native GEN movies retain an MD5 in a legacy field named SHA1.
        core.require(fields.get('SHA1', '').lower() == md5, 'native movie ROM digest differs')
        checksum = metadata.get('rom_checksum', '').lower()
        core.require(not checksum or checksum in (md5, reference['rom_sha1']), 'native metadata ROM digest differs')
    return snapshots


def report_owner(gate):
    unique = f'[engine:junit-jupiter]/[class:{gate.class_name}]/[method:{gate.method}()]'
    invocation = hashlib.sha256(unique.encode()).hexdigest()[:16]
    owner = hashlib.sha256('\n'.join((gate.profile, gate.class_name, gate.method,
                                      '0', invocation, gate.lane, gate.logical_key)).encode()).hexdigest()
    return f'{gate.logical_key}-{gate.lane}-{owner[:16]}.json', owner


def count(payload, key):
    value = payload.get(key)
    core.require(type(value) is int and value >= 0, f'invalid native report count: {key}')
    return value


def trace_observation(gate, payload, case_failed, artifact_digest):
    errors, warnings = count(payload, 'error_count'), count(payload, 'warning_count')
    bootstrap_errors = count(payload, 'bootstrap_error_count')
    bootstrap_warnings = count(payload, 'bootstrap_warning_count')
    comparisons = count(payload, 'total_frames')
    groups = payload.get('verification_groups')
    core.require(isinstance(groups, dict) and set(groups) == {'physics', 'animation'}, 'native report requires both verification groups')
    for group in groups.values():
        core.require(isinstance(group, dict), 'invalid verification group')
        count(group, 'error_count'); count(group, 'warning_count')
    core.require(sum(g['error_count'] for g in groups.values()) == errors
                 and sum(g['warning_count'] for g in groups.values()) == warnings, 'native report group counts disagree')
    core.require(bootstrap_errors <= groups['physics']['error_count'] and bootstrap_warnings <= groups['physics']['warning_count'],
                 'native report bootstrap counts disagree')
    extent = payload.get('replay_gate')
    core.require(isinstance(extent, dict) and type(extent.get('schema')) is int and extent['schema'] == 1
                 and type(extent.get('comparison_gate_reached')) is bool
                 and type(extent.get('planned_trace_rows')) is int and extent['planned_trace_rows'] == gate.recorded_rows
                 and extent.get('verification') == 'all', 'native report is missing the intended gate extent')
    missing = payload.get('missing_advertised_aux_schemas', [])
    core.require(isinstance(missing, list) and all(isinstance(s, str) and s for s in missing), 'invalid missing-aux evidence')
    complete = extent['comparison_gate_reached']
    core.require(not complete or comparisons > 0, 'completed native gate has no gameplay observations')
    if not complete:
        status = 'incomplete'
    elif warnings or missing:
        status = 'reference-limited'
    elif errors:
        core.require(case_failed, 'native mismatch disagrees with passing JUnit verdict')
        status = 'mismatch'
    else:
        status = 'incomplete' if case_failed else 'exact'
    return {'obligation_id': gate.obligation.id, 'case_id': 'complete-replay',
            'contract_digest': gate.obligation.contract_digest, 'rom_sha1': core.ROMS[gate.obligation.game],
            'status': status, 'expected': 1, 'compared': int(complete), 'unit': UNIT,
            'evidence_kind': 'native-state', 'causal_status': 'first-divergence' if status == 'mismatch' else 'isolated',
            'artifact_sha256': artifact_digest,
            'diagnostics': {'verification_groups': {name: {key: group[key]
                for key in ('first_error', 'first_warning') if key in group}
                for name, group in groups.items()}},
            'metrics': {'recorded_rows': gate.recorded_rows, 'gameplay_comparisons': comparisons,
                        'error_groups': errors, 'warning_groups': warnings,
                        'bootstrap_errors': bootstrap_errors, 'bootstrap_warnings': bootstrap_warnings,
                        'missing_aux_schemas': len(missing)}}


def collect_results(gates, surefire, trace, log_path, exit_code, started):
    fresh(log_path, started)
    log = log_path.read_text(errors='replace')
    totals = completed_summary(log, exit_code)
    core.require(totals[0] == len(gates) and totals[2:] == (0, 0), 'native test count, errors or skips differ')
    expected_xml = {'TEST-' + gate.class_name + '.xml' for gate in gates}
    core.require({p.name for p in surefire.glob('TEST-*.xml')} == expected_xml, 'native XML inventory differs')
    expected_reports, observations, failed = set(), [], 0
    for gate in gates:
        xml = surefire / ('TEST-' + gate.class_name + '.xml')
        fresh(xml, started)
        suite = ET.parse(xml).getroot()
        cases = list(suite.iter('testcase'))
        core.require(suite.tag == 'testsuite' and suite.get('name') == gate.class_name and len(cases) == 1
                     and suite.get('tests') == '1', 'native XML must contain one exact executed case')
        case = cases[0]
        core.require(case.get('classname') == gate.class_name and case.get('name') == gate.method
                     and case.find('skipped') is None and case.find('error') is None, 'native case identity, skip or error differs')
        case_failed = case.find('failure') is not None
        failed += case_failed
        core.require(suite.get('failures') == str(int(case_failed)) and suite.get('errors') == '0'
                     and suite.get('skipped') == '0', 'native XML counters disagree')
        verdicts = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: [^\n]*? -- in '
                              + re.escape(gate.class_name) + r'(?=\s|$)', log)
        core.require(verdicts == [('1', str(int(case_failed)), '0', '0')], 'native Maven/XML case verdict differs')
        name, owner = report_owner(gate)
        path = trace / gate.profile / name
        sidecar = Path(str(path) + '.owner.json')
        fresh(path, started); fresh(sidecar, started)
        payload, metadata = core.read_json(path), core.read_json(sidecar)
        errors = []
        validated = validator.validate_report(path, trace, trace.resolve(), errors)
        core.require(validated is not None and not errors, '; '.join(errors) or 'invalid native trace report')
        core.require(metadata.get('owner_key') == owner and metadata.get('logical_key') == gate.logical_key,
                     'native report belongs to another JUnit invocation')
        expected_reports.update((path, sidecar))
        observation = trace_observation(gate, payload, case_failed,
            core.digest({'xml': sha256(xml), 'report': sha256(path), 'owner': sha256(sidecar), 'log': sha256(log_path)}))
        if case_failed:
            observation['diagnostics']['junit_failure_message'] = case.find('failure').get('message', '')[:2048]
        observations.append(observation)
    core.require(totals[1] == failed, 'native Maven/XML failure count differs')
    core.require(set(trace.rglob('*.json')) == expected_reports, 'extra or orphan native report artifacts')
    return observations


def run_maven(command, root, log_path, environment):
    with log_path.open('w') as log:
        process = subprocess.Popen(command, cwd=root, env=environment, text=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=os.name == 'posix')
        size = 0
        try:
            for line in process.stdout:
                size += len(line.encode())
                core.require(size <= 16 * 1024 * 1024, 'native measurement exceeded its bounded diagnostic log')
                log.write(line)
                if line.startswith(('Waiting for ', 'Worktree build/test lock acquired', 'Maven slot acquired')):
                    print(line.rstrip(), file=sys.stderr, flush=True)
            return process.wait()
        except BaseException:
            stop_process_tree(process)
            raise


def measure(root, games, rom_paths, environment):
    core.require(root.resolve() == Path(__file__).resolve().parents[2],
                 'native measurement must use its owning code checkout')
    check_project_options(root)
    catalogue = core.load_catalogue(root, root / 'tools/parity/catalogue.json')
    producers = core.parse_producers(core.read_json(root / 'tools/parity/producers.json'), root)
    adapter = next((p for p in producers if p.id == PRODUCER_ID), None)
    checksum = sha256(Path(__file__))
    core.require(adapter is not None and adapter.adapter_sha256 == checksum, 'native trace producer is not registered with its current code digest')
    gates = selected_gates(catalogue, games)
    source = core.source_identity(root, producers)
    inputs = verify_inputs(root, gates, rom_paths)
    target = root / 'target/parity-native'
    target.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='run-', dir=target) as temporary:
        directory = Path(temporary).resolve()
        surefire, trace, log = directory / 'surefire', directory / 'trace', directory / 'maven.log'
        command = [sys.executable, str(root / 'tools/testing/maven_queue.py'), '-Dmse=off', '-Ptrace-replay',
                   '-Dtest=' + ','.join(g.class_name + '#' + g.method for g in gates), '-Dtrace.verification=all',
                   '-Dopenggf.surefire.reports=' + str(surefire), '-Dopenggf.trace.reports=' + str(trace)]
        command.extend('-D' + ROM_PROPERTIES[game] + '=' + str(rom_paths[game]) for game in games)
        started = time.time_ns()
        exit_code = run_maven(command, root, log, environment)
        try:
            observations = collect_results(gates, surefire, trace, log, exit_code, started)
        except (ValueError, OSError, ET.ParseError):
            print('Native measurement did not yield valid evidence. Final Maven output:', file=sys.stderr)
            print('\n'.join(log.read_text(errors='replace').splitlines()[-40:]), file=sys.stderr)
            print('Observed trace files: ' + ', '.join(
                path.relative_to(trace).as_posix() for path in sorted(trace.rglob('*.json'))[:12]), file=sys.stderr)
            raise
        core.require(inputs == verify_inputs(root, gates, rom_paths), 'ROM or native reference inputs changed during execution')
        core.require(source == core.source_identity(root, producers), 'source changed during native measurement')
        envelope = {'schema': 1, 'source': asdict(source), 'catalogue_digest': catalogue.digest,
                    'producer': {'id': PRODUCER_ID, 'version': '1', 'comparator_sha256': checksum},
                    'synthetic': False, 'observations': observations}
        return core.assess(catalogue, source, core.parse_evidence(envelope, catalogue, source, producers), producers)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=core.ROOT)
    parser.add_argument('--games', default='s1,s2,s3k')
    for game in core.ROMS:
        parser.add_argument('--' + game + '-rom', type=Path)
    parser.add_argument('--format', choices=('json', 'markdown'), default='markdown')
    args = parser.parse_args(argv)
    try:
        with handle_termination():
            result = measure(args.root.resolve(), args.games.split(','),
                             {game: getattr(args, game + '_rom') for game in core.ROMS}, checked_environment())
        print(core.markdown(result) if args.format == 'markdown' else json.dumps(result, indent=2), end='\n' if args.format == 'json' else '')
        return 0
    except (ValueError, OSError, TypeError, KeyError, ET.ParseError, BadZipFile, csv.Error, subprocess.SubprocessError) as error:
        print(f'native parity measurement: {error}', file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print('native parity measurement cancelled; no qualification produced', file=sys.stderr)
        return 130


if __name__ == '__main__':
    sys.exit(main())
