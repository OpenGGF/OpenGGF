#!/usr/bin/env python3
"""Join the bounded AIZ1/HCZ1 pilot obligations to exact Surefire case identities.

Inputs: explicit expected inventory, original ROM paths, committed route fixtures,
and fresh XML reports. Origin: October 15, 2026 architecture debt remediation.
This is independent native-route/rewind evidence, not whole-act certification.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from category_artifacts import stop_process_tree
from maven_queue import handle_termination

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INVENTORY = Path(__file__).with_name('aiz-hcz-route-obligations.json')
ROMS = {
    's3k': {'property': 's3k.rom.path', 'sha1': 'cfbf98c36c776677290a872547ac47c53d2761d6'},
}


def sha1_file(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha1').hexdigest()


def identity(case):
    return case['class'] + '#' + case['name']


def java_code(text):
    # Mask comments and literals without moving declarations. The bounded
    # recognizers below must never bind a commented or quoted annotation.
    return re.sub(r'/\*.*?\*/|//[^\r\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                  lambda match: re.sub(r'[^\r\n]', ' ', match.group()), text, flags=re.S)


def validate_inventory(inventory, root=ROOT):
    if inventory.get('schema') != 1 or not inventory.get('lane') or not inventory.get('cases'):
        raise ValueError('inventory requires schema 1, lane and nonempty cases')
    if not inventory.get('roms') or not set(inventory['roms']) <= ROMS.keys():
        raise ValueError('inventory requires known ROM prerequisites')
    seen = set()
    for case in inventory['cases']:
        if not all(isinstance(case.get(key), str) and case[key] for key in ('class', 'name', 'method')):
            raise ValueError('case requires exact class, name and method')
        if not re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+', case['class']):
            raise ValueError('invalid test class: ' + case['class'])
        if not re.fullmatch(r'[A-Za-z_$][\w$]*', case['method']):
            raise ValueError('invalid method: ' + case['method'])
        key = identity(case)
        if key in seen:
            raise ValueError('duplicate expected identity: ' + key)
        seen.add(key)
        if not isinstance(case.get('obligations'), list) or not case['obligations']:
            raise ValueError('case requires obligations: ' + key)
        source = Path(root) / 'src/test/java' / (case['class'].replace('.', '/') + '.java')
        if not source.is_file():
            raise ValueError('stale test class source: ' + str(source))
        if not re.search(r'\bvoid\s+' + re.escape(case['method']) + r'\s*\(', source.read_text()):
            raise ValueError('stale test method: ' + case['class'] + '#' + case['method'])
    # Surefire indexes parameter invocations. Tie those indexes back to the
    # source values so a reordered enum/width row cannot retain a stale obligation.
    for binding in inventory.get('parameter_sources', []):
        source = Path(root) / 'src/test/java' / (binding['class'].replace('.', '/') + '.java')
        text = java_code(source.read_text())
        if binding['kind'] == 'enum':
            match = re.search(r'\benum\s+' + re.escape(binding['type']) + r'\s*\{([^}]+)\}', text)
            # This bounded pilot supports plain constants only. Constructor
            # arguments, bodies, annotations and other Java syntax require an
            # explicit future extension, never a best-effort token extraction.
            if not match or not re.fullmatch(
                    r'\s*[A-Z][A-Z0-9_]*(?:\s*,\s*[A-Z][A-Z0-9_]*)*\s*,?\s*', match.group(1)):
                raise ValueError('unsupported enum declaration: ' + binding['class'] + '#'
                                 + binding['type'] + '; this pilot accepts plain constants only')
            values = [value.strip() for value in match.group(1).strip().rstrip(',').split(',')]
        elif binding['kind'] == 'ints':
            # Bind the annotation directly to this method declaration. No
            # intervening member/body is accepted, so an earlier method cannot
            # supply this method's values. Unsupported annotation syntax fails closed.
            match = re.search(
                r'@ValueSource\s*\(\s*ints\s*=\s*\{([^{}]*)\}\s*\)\s*'
                r'(?:@[\w.]+(?:\([^{};]*\))?\s*)*'
                r'(?:(?:public|protected|private|static|final|synchronized)\s+)*void\s+'
                + re.escape(binding['method']) + r'\s*\([^{};]*\)\s*(?:throws\s+[\w.,\s]+)?\s*\{',
                text)
            if not match or not re.fullmatch(r'\s*\d+(?:\s*,\s*\d+)*\s*,?\s*', match.group(1)):
                raise ValueError('unsupported int parameter source: ' + binding['class'] + '#' + binding['method'])
            values = [int(value.strip()) for value in match.group(1).strip().rstrip(',').split(',')]
        else:
            raise ValueError('unknown parameter source kind')
        if values != binding['values']:
            raise ValueError('stale parameter source: ' + binding['class'] + '#' + binding['method'])
    return inventory


def preflight(inventory, rom_paths, root=ROOT):
    validate_inventory(inventory, root)
    for game in inventory['roms']:
        path = rom_paths.get(game)
        if path is None or not Path(path).is_file():
            raise ValueError('required original ROM unavailable: ' + game)
        if sha1_file(path) != ROMS[game]['sha1']:
            raise ValueError('wrong ROM identity: ' + game + ' at ' + str(path))
    for fixture in inventory.get('fixtures', []):
        path = (Path(root) / fixture).resolve()
        if not path.is_relative_to(Path(root).resolve()) or not path.is_file():
            raise ValueError('required input fixture unavailable: ' + fixture)


def check_reports(inventory, reports):
    expected = {identity(case) for case in inventory['cases']}
    selected_classes = {case['class'] for case in inventory['cases']}
    observed = Counter()
    issues = []
    for report in sorted(Path(reports).glob('TEST-*.xml')):
        try:
            suite = ET.parse(report).getroot()
        except (OSError, ET.ParseError) as error:
            issues.append('invalid XML report ' + str(report) + ': ' + str(error))
            continue
        for case in suite.iter('testcase'):
            if case.get('classname') not in selected_classes:
                continue
            key = case.get('classname') + '#' + case.get('name', '')
            observed[key] += 1
            if key not in expected:
                issues.append('unexpected selected case: ' + key)
            for outcome in ('skipped', 'failure', 'error'):
                if case.find(outcome) is not None:
                    issues.append(outcome + ' selected case: ' + key)
    issues += ['missing expected case: ' + key for key in sorted(expected - observed.keys())]
    issues += ['duplicate selected case: ' + key for key, count in sorted(observed.items()) if count != 1]
    return issues


def excluded_groups(root):
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(Path(root) / 'pom.xml').getroot()
    groups = pom.findtext('m:properties/m:surefire.excludedGroups', namespaces=ns)
    if groups is None:
        raise ValueError('POM has no explicit ordinary excluded groups')
    # Preserve all ordinary exclusions except this pilot's explicitly requested route tag.
    return ','.join(group.strip() for group in groups.split(',') if group.strip() != 'slow-suite')


def maven_command(inventory, rom_paths, reports, root=ROOT):
    classes = sorted({case['class'] for case in inventory['cases']})
    command = [sys.executable, str(Path(root) / 'tools/testing/maven_queue.py'), '-Dmse=off',
               '-Dtest=' + ','.join(classes),
               '-Dsurefire.excludedGroups=' + excluded_groups(root),
               '-Dopenggf.surefire.reports=' + str(Path(reports).resolve()),
               '-DfailIfNoTests=true', '-Dsurefire.failIfNoSpecifiedTests=true']
    command += ['-D' + ROMS[game]['property'] + '=' + str(Path(rom_paths[game]).resolve())
                for game in inventory['roms']]
    command += ['test', '-B']
    return command


def run_queued(command, root=ROOT):
    # The queue wrapper owns Maven's process group and admission leases. Terminate
    # that owned wrapper on cancellation so its finally block retires Maven too.
    with handle_termination():
        with subprocess.Popen(command, cwd=root, start_new_session=(os.name == 'posix')) as process:
            try:
                return process.wait()
            finally:
                stop_process_tree(process)


def parse_roms(values):
    paths = {}
    for item in values:
        game, separator, path = item.partition('=')
        if not separator or game not in ROMS or game in paths or not path:
            raise ValueError('ROM must be a unique known game=/absolute/path: ' + item)
        resolved = Path(path)
        if not resolved.is_absolute():
            raise ValueError('ROM path must be absolute: ' + path)
        paths[game] = resolved
    return paths


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=DEFAULT_INVENTORY)
    parser.add_argument('--rom', action='append', default=[], help='s3k=/absolute/path/to/existing-ROM')
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--preflight', action='store_true')
    modes.add_argument('--reports', type=Path,
                       help='read existing XML only; caller must supply fresh reports from the exact '
                            'tested build, command and fixtures; no freshness or build identity is inferred')
    modes.add_argument('--run', action='store_true', help='preflight, queue the exact lane, verify fresh XML, clean up')
    args = parser.parse_args(argv)
    try:
        inventory = json.loads(args.inventory.read_text())
        rom_paths = parse_roms(args.rom)
        preflight(inventory, rom_paths, ROOT)
        if args.preflight:
            print('Route pilot prerequisites verified; no tests executed.')
            return 0
        exit_code = 0
        if args.run:
            from category_control import preflight as environment_preflight
            environment_preflight(ROOT, {'guards': False})
            target = ROOT / 'target'
            target.mkdir(exist_ok=True)
            with tempfile.TemporaryDirectory(prefix='route-obligations-', dir=target) as temporary:
                reports = Path(temporary)
                exit_code = run_queued(maven_command(inventory, rom_paths, reports, ROOT), ROOT)
                issues = check_reports(inventory, reports)
        else:
            issues = check_reports(inventory, args.reports)
        for issue in issues:
            print(issue, file=sys.stderr)
        if exit_code or issues:
            print('Route pilot incomplete or failed; no coverage awarded.', file=sys.stderr)
            return 1
        print(f"Route pilot passed: {len(inventory['cases'])} exact cases; independent AIZ1/HCZ1 obligations only.")
        return 0
    except KeyboardInterrupt:
        print('Route pilot cancelled; validation incomplete.', file=sys.stderr)
        return 130
    except (OSError, ValueError, ET.ParseError) as error:
        print('Route pilot rejected: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
