#!/usr/bin/env python3
"""Bounded SOURCE-ONLY JUnit selection preflight; never a runner or count authority.

Inputs: explicit canonical Git metadata, exact saved commit, external v1 handoff.
Origin: 2026-11-08 R115 wrong Body root and omitted Boot$ActualUsa regressions.
Only conventional src/test/java roots and pinned incoming overlays are supported.
Stdlib only. No moving checkout, discovery, compilation, queue or result reads.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import types

PINS = {
    'enum_contract_audit': (47279, 'bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d'),
    'java_declaration_census': (65795, '304b6314407adaa114ae06f17e9e0c23da93f89110e454a1c681dfe51000a625'),
    'integration_packet': (50642, '418fcc6bc520687a4670d545713e1a4096983ea7790ad37c4a535bc52de088b3'),
    'integration_handoff': (17292, '844ec5be26c1260085db5be61db5bba322a2a933aeecc2c386191364a2dece65'),
}
CAPS = dict(max_file_bytes=1048576, max_total_bytes=8388608, max_tokens=100000,
            max_context_tokens=4096, max_nodes=50000, max_output_bytes=1048576)
MAX_SPEC = 4 * 1024 * 1024
SHA = re.compile(r'[0-9a-f]{64}\Z')
COMMIT = re.compile(r'[0-9a-f]{40}\Z')
LIMITATIONS = [
    'Zero findings means bounded written-source evidence only; no compilation, classpath resolution, JUnit discovery/execution, expected_count correctness, XML aggregation, native/API approval or queue authority.',
    'Only exact conventional top-level src/test/java/<qualified/name>.java roots are supported. Missing conventional source leaves generated/custom roots unresolved, never certified absent; no remapping or repository scan.',
    'Literal ordinary @Test/@Nested scopes and direct JUnit imports only. Unknown/composed/dynamic annotations, ambiguous imports, inheritance, local/anonymous types and unsupported grammar are incomplete.',
    'Report suite names are compared to syntactic scopes only. All counts, owners and case labels remain caller declarations, never inferred from method counts. Both methodless and mixed method/whole-root runtime aggregation remain unverified.',
    'Only selected source bytes, handoff and pinned tool dependencies are read and rechecked, non-atomically. Changes restored between observations can escape detection. Other packet sources and declared dependencies are schema-only, not readiness evidence.',
    'Trusted local Git metadata required. Fixed read-only Git argv disable hooks, replacement objects, lazy fetch and network protocols. No supplied source or candidate-tree tool is imported/executed.',
]


class PreflightError(ValueError):
    def __init__(self, message, code=2):
        super().__init__(message)
        self.code = code


def need(ok, message, code=2):
    if not ok:
        raise PreflightError(message, code)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def absolute(value):
    need(isinstance(value, str) and 0 < len(value) <= 4096 and value.startswith('/')
         and not any(ord(c) < 32 or ord(c) == 127 for c in value) and '\\' not in value and ':' not in value,
         'Require canonical absolute POSIX path')
    need(all(p and p not in ('.', '..') for p in value.split('/')[1:]), 'Path alias/traversal unsupported')
    return Path(value)


@contextmanager
def opened(path, directory=False):
    path = absolute(str(path))
    fds = []
    try:
        need(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW'), 'POSIX no-follow descriptors required')
        fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
        fds.append(fd)
        for i, part in enumerate(path.parts[1:]):
            is_dir = directory or i < len(path.parts) - 2
            flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
            if is_dir:
                flags |= os.O_DIRECTORY
            fd = os.open(part, flags, dir_fd=fd)
            fds.append(fd)
            info = os.fstat(fd)
            need(stat.S_ISDIR(info.st_mode) if is_dir else stat.S_ISREG(info.st_mode),
                 'Require regular file/directory without symlinks')
        yield fd
    except OSError as error:
        raise PreflightError('Cannot safely open declared regular path') from error
    finally:
        for fd in reversed(fds):
            os.close(fd)


def identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_nlink, info.st_size,
            info.st_mtime_ns, info.st_ctime_ns)


def read(path, cap):
    with opened(path) as fd:
        before = os.fstat(fd)
        need(before.st_nlink == 1, 'Hardlink aliases unsupported')
        need(before.st_size <= cap, 'Input byte budget exceeded before read', 3)
        data = bytearray()
        while len(data) <= before.st_size:
            block = os.read(fd, min(65536, before.st_size + 1 - len(data)))
            if not block:
                break
            data.extend(block)
        need(identity(before) == identity(os.fstat(fd)) and len(data) == before.st_size,
             'Input changed during read', 3)
    return bytes(data), identity(before)


def recheck(saved):
    for path, (data, info) in saved.items():
        current, now = read(path, len(data))
        need(now == info and current == data, 'Input changed during non-atomic recheck', 3)


def dependencies(saved):
    """Execute only reviewed byte-pinned siblings; never Python from input Git/packet."""
    modules = {}
    for name, (size, digest) in PINS.items():
        path = Path(__file__).absolute().parent / (name + '.py')
        data, info = read(path, size)
        need(len(data) == size and sha(data) == digest, 'Reviewed tool dependency pin mismatch')
        saved[path] = data, info
        module = types.ModuleType('_selection_' + name)
        module.__file__ = str(path)
        sys.modules[module.__name__] = module  # dataclass needs its own module identity
        previous = sys.modules.get('integration_packet')
        if name == 'integration_handoff':
            sys.modules['integration_packet'] = modules['integration_packet']
        try:
            exec(compile(data, str(path), 'exec'), module.__dict__)
        finally:
            if name == 'integration_handoff':
                if previous is None:
                    sys.modules.pop('integration_packet', None)
                else:
                    sys.modules['integration_packet'] = previous
        modules[name] = module
    # Avoid an unpinned second import: the census uses exactly this reviewed lexer.
    modules['java_declaration_census']._LEXER = modules['enum_contract_audit']
    return modules


def bounded_json(data, packet, max_nodes):
    need(data.endswith(b'\n') and b'\r' not in data and b'\0' not in data,
         'Handoff requires LF-terminated UTF-8 JSON')
    try:
        value = json.loads(data.decode('utf-8'), object_pairs_hook=packet.unique,
                           parse_constant=lambda _: (_ for _ in ()).throw(PreflightError('Nonfinite JSON number')))
    except (UnicodeError, ValueError, RecursionError) as error:
        raise PreflightError('Malformed/duplicate/nonfinite handoff JSON') from error
    stack, nodes = [(value, 0)], 0
    while stack:
        item, depth = stack.pop()
        nodes += 1
        need(nodes <= max_nodes and depth <= 24, 'JSON node/depth budget exceeded', 3)
        if isinstance(item, dict):
            need(nodes + len(stack) + len(item) <= max_nodes, 'JSON node budget exceeded', 3)
            stack.extend((v, depth+1) for v in item.values())
        elif isinstance(item, list):
            need(nodes + len(stack) + len(item) <= max_nodes, 'JSON node budget exceeded', 3)
            stack.extend((v, depth+1) for v in item)
    return value


def relative(value):
    need(isinstance(value, str) and 0 < len(value) <= 4096 and not value.startswith('/')
         and not any(ord(c) < 32 or ord(c) == 127 for c in value) and '\\' not in value and ':' not in value,
         'Require canonical relative destination')
    need(all(p and p not in ('.', '..') and p.lower() != '.git' for p in value.split('/')),
         'Unsafe destination/path alias')
    return value


def schema(value, mods, git_dir, handoff_path):
    """Reuse exact v1 test/report schema, without worktree or dependency reads."""
    p, h = mods['integration_packet'], mods['integration_handoff']
    p.keys(value, ('version', 'id', 'packet', 'dependencies', 'api_changes', 'known_defects', 'tests'), 'handoff')
    need(type(value['version']) is int and value['version'] == 1, 'Unsupported handoff version')
    h._identifier(value['id'], 'id')
    h._descriptions(value['api_changes'], 'api_changes')
    h._descriptions(value['known_defects'], 'known_defects')
    h._tests(value['tests'])
    manifest = value['packet']
    p.keys(manifest, ('version', 'worktree_head', 'files'), 'packet')
    need(type(manifest['version']) is int and manifest['version'] == 1 and
         isinstance(manifest['worktree_head'], str) and COMMIT.fullmatch(manifest['worktree_head']), 'Invalid packet version/HEAD pin')
    files = manifest['files']
    need(isinstance(files, list) and 1 <= len(files) <= h.MAX_FILES, 'Require bounded packet files')
    seen, sources, total = {}, {}, 0
    # A source inside the selected Git repo's conventional checkout is not an external overlay.
    # Other linked worktrees cannot be inferred without moving metadata inspection;
    # selected external inputs must independently match frozen 0444 pins.
    forbidden = git_dir.parent if git_dir.name == '.git' else git_dir
    def external(text):
        path = absolute(text)
        need(not path.is_relative_to(forbidden) and not path.is_relative_to(git_dir)
             and '.git' not in (s.lower() for s in path.parts)
             and not path.is_relative_to(Path(__file__).absolute().parents[2]),
             'Input must be external to Git metadata and tool/source checkout')
        return path
    for entry in files:
        p.keys(entry, ('source', 'destination', 'sha256', 'bytes', 'mode', 'utf8', 'lf', 'expected'), 'packet file')
        h._pin(entry, 'packet file')
        need(entry['mode'] == '0444' and entry['utf8'] is True and entry['lf'] is True,
             'Incoming sources require frozen UTF-8/LF contract')
        source = external(entry['source'])
        target = relative(entry['destination'])
        need(target not in seen and source not in sources and source != handoff_path,
             'Duplicate/aliased source/destination role')
        seen[target], total = entry, total + entry['bytes']
        sources[source] = {k: entry[k] for k in ('bytes', 'sha256', 'mode')}
        expected = entry['expected']
        need(isinstance(expected, dict) and ((set(expected) == {'absent'} and expected['absent'] is True) or
             (set(expected) == {'sha256'} and isinstance(expected['sha256'], str) and SHA.fullmatch(expected['sha256']))),
             'Require exact absent/baseline hash declaration')
    need(total <= p.MAX_PACKET, 'Declared packet byte budget exceeded', 3)
    deps = value['dependencies']
    need(isinstance(deps, list) and len(deps) <= h.MAX_DEPENDENCIES, 'Require bounded dependencies')
    names, paths = set(), dict(sources)
    for entry in deps:
        p.keys(entry, ('name', 'path', 'sha256', 'bytes', 'mode'), 'dependency')
        h._identifier(entry['name'], 'dependency name')
        h._pin(entry, 'dependency')
        path = external(entry['path'])
        pin = {k: entry[k] for k in ('bytes', 'sha256', 'mode')}
        need(entry['name'] not in names and path != handoff_path and
             (path not in paths or paths[path] == pin), 'Duplicate/conflicting dependency path role')
        names.add(entry['name']); paths[path] = pin
    # Schema-only declarations are never followed. Some handoffs deliberately name
    # nonexistent unrelated files; their readiness belongs to the integration runner.
    return seen


def header_problem(d, java, census):
    """Check bounded lexer tokens before trusting the helpers' joined names."""
    i, package, imports = 0, '', []
    for keyword in ('package', 'import'):
        while d.v(i) == keyword:
            line = d.ts[i].line
            i += 1
            static = keyword == 'import' and d.v(i) == 'static'
            i += static
            parts, want_identifier, identifiers = [], True, 0
            while d.v(i) not in (';', '<EOF>'):
                value = d.v(i)
                if want_identifier:
                    if census.IDENT.fullmatch(value) and value not in census.RESERVED:
                        identifiers += 1
                    elif not (keyword == 'import' and value == '*' and identifiers and
                              d.v(i+1) == ';'):
                        return 'Malformed package/import tokens at line ' + str(line)
                elif value != '.':
                    return 'Malformed package/import tokens at line ' + str(line)
                parts.append(value)
                want_identifier = not want_identifier
                i += 1
            if want_identifier or d.v(i) != ';':
                return 'Malformed package/import tokens at line ' + str(line)
            name = ''.join(parts)  # Alternating token shape has now been checked.
            if keyword == 'package':
                package = name
            else:
                imports.append((bool(static), name))
            i += 1
    if package != java.package or imports != java.imports:
        return 'Package/import metadata includes unsupported non-leading tokens'
    return None


def inventory(data, selected, census, limits):
    d, java = census.source_declarations(data, limits)
    malformed = header_problem(d, java, census)
    if malformed:
        return dict(package=None, root_matches=False, scopes=[], candidate_methods=[],
                    incomplete_reasons=[malformed])
    reasons, scopes, methods = [], [], []
    def incomplete(reason):
        if reason not in reasons:
            reasons.append(reason)
    types = [r for r in d.result if r['kind'] in ('class', 'interface', 'enum', 'record')]
    top = [r for r in types if r['owner'] == java.package]
    root_matches = len(top) == 1 and java.package + '.' + top[0]['name'] == selected
    if not root_matches:
        incomplete('Selected package/top-level name does not match exact declared root')
    type_names = {r['name'] for r in types}
    bindings = {}
    wildcards = []
    for static, name in java.imports:
        if name.endswith('.*'):
            if name != 'org.junit.jupiter.api.Assertions.*':
                wildcards.append((static, name))
        else:
            bindings.setdefault(name.rsplit('.', 1)[-1], set()).add((static, name))
    tags_allowed = {'Test', 'Nested', 'BeforeAll', 'AfterAll', 'BeforeEach', 'AfterEach', 'DisplayName'}
    org_shadow = 'org' in bindings or 'org' in type_names or any(s for s, _ in wildcards)
    covered = set()
    def annotations(start, end):
        tags, i = [], start
        while i < end:
            if d.v(i) != '@':
                i += 1; continue
            covered.add(i)
            stop = d.annotation(i)
            j = i+2
            while d.v(j) == '.': j += 2
            spelling = ''.join(t.value for t in d.ts[i+1:j])
            simple = spelling.rsplit('.', 1)[-1]
            exact = 'org.junit.jupiter.api.' + simple
            resolved = (simple in tags_allowed and
                ((spelling == exact and not org_shadow) or
                 (spelling == simple and simple not in type_names and
                  (bindings.get(simple) == {(False, exact)} or
                   (simple not in bindings and wildcards == [(False, 'org.junit.jupiter.api.*')])))))
            if not resolved:
                incomplete('Unsupported or ambiguous annotation binding at line ' + str(d.ts[i].line))
            if resolved and simple != 'DisplayName' and not (j == stop or stop == j+2):
                incomplete('Unsupported annotation arguments at line ' + str(d.ts[i].line))
            if resolved:
                tags.append(simple)
            i = stop
        return tags
    mapping = {}
    constructors = {}
    for row, (start, end) in zip(d.result, d.spans):
        prefix_end, _ = d.prefix(start)
        tags = annotations(start, prefix_end)
        kind, owner = row['kind'], row['owner']
        if kind in ('class', 'interface', 'enum', 'record'):
            lexical = owner + '.' + row['name']
            binary = (mapping[owner] + '$' + row['name']) if owner in mapping else lexical
            mapping[lexical] = binary
            nested = owner in mapping
            header = [t.value for t in d.ts[prefix_end:end]]
            if (kind != 'class' or '$' in row['name'] or
                any(v in header for v in ('<', 'extends', 'implements', 'permits')) or
                set(row['modifiers']) - {'public', 'final', 'strictfp'}):
                incomplete('Unsupported class shape at line ' + str(row['line']))
            if nested and 'Nested' not in tags:
                incomplete('Member class without resolved @Nested at line ' + str(row['line']))
            if ('Test' in tags or (not nested and 'Nested' in tags) or
                    set(tags) - {'Nested', 'DisplayName'} or len(tags) != len(set(tags))):
                incomplete('Unsupported class annotation placement at line ' + str(row['line']))
            scopes.append(dict(**{'class': binary}, package=java.package, line=row['line'], nested=nested,
                               resolved_nested_test=('Nested' in tags) if nested else False))
            continue
        if kind == 'constructor':
            constructors[owner] = constructors.get(owner, 0) + 1
            if row['arity'] or 'private' in row['modifiers'] or tags:
                incomplete('Unsupported constructor at line ' + str(row['line']))
        if len(tags) != len(set(tags)):
            incomplete('Duplicate declaration annotations at line ' + str(row['line']))
        executable = set(tags) - {'DisplayName'}
        if executable:
            if kind != 'method' or len(executable) != 1 or 'Nested' in executable:
                incomplete('Unsupported annotation placement at line ' + str(row['line']))
                continue
            tag = next(iter(executable))
            if (tag not in tags_allowed or row['arity'] or row['type'] != 'void' or
                d.v(end) != '{' or d.v(prefix_end) == '<' or
                set(row['modifiers']) & {'private', 'abstract', 'native'} or
                ('static' in row['modifiers']) != (tag in ('BeforeAll', 'AfterAll'))):
                incomplete('Unsupported test/lifecycle shape at line ' + str(row['line']))
            if tag == 'Test':
                methods.append(dict(owner=mapping.get(owner, owner), name=row['name'], line=row['line']))
    # Do not let field/parameter/body annotations masquerade as member declarations.
    if any(t.value == '@' and i not in covered for i, t in enumerate(d.ts)):
        incomplete('Annotations outside supported declaration prefixes')
    if any(count > 1 for count in constructors.values()):
        incomplete('Multiple constructors unsupported')
    if not methods:
        incomplete('No ordinary literal test candidates observed')
    return dict(package=java.package, root_matches=root_matches, scopes=scopes,
                candidate_methods=methods, incomplete_reasons=reasons)


def bounded_output(report, caps):
    # Bound output nodes and bytes before accumulating/printing a whole JSON string.
    stack, nodes = [report], 0
    while stack:
        item = stack.pop()
        nodes += 1
        need(nodes <= caps['max_nodes'], 'Output node budget exceeded', 3)
        children = list(item.values()) if isinstance(item, dict) else item if isinstance(item, list) else []
        need(nodes + len(stack) + len(children) <= caps['max_nodes'], 'Output node budget exceeded', 3)
        stack.extend(children)
    size = 1
    for chunk in json.JSONEncoder(ensure_ascii=True, sort_keys=True).iterencode(report):
        size += len(chunk.encode('ascii'))
        need(size <= caps['max_output_bytes'], 'Output byte budget exceeded', 3)


def check(git_dir, commit, handoff, **overrides):
    caps = dict(CAPS)
    need(set(overrides) <= set(caps), 'Unknown limit')
    for name, value in overrides.items():
        need(type(value) is int and 1 <= value <= caps[name], 'Limits may only lower hard ceilings')
        caps[name] = value
    saved = {}
    mods = dependencies(saved)
    c, p = mods['java_declaration_census'], mods['integration_packet']
    git_dir, spec = absolute(git_dir), absolute(handoff)
    need(isinstance(commit, str) and COMMIT.fullmatch(commit), 'Require one exact lowercase saved commit')
    try:
        with opened(git_dir, directory=True) as fd:
            info = os.fstat(fd)
            directory = info.st_dev, info.st_ino, info.st_mode
        need(c.git_read(git_dir, ['cat-file', '-t', commit], 128).strip() == b'commit', 'Saved commit is not a local commit')
        need(not spec.is_relative_to(git_dir.parent if git_dir.name == '.git' else git_dir)
             and not spec.is_relative_to(Path(__file__).absolute().parents[2]), 'Handoff must be external')
        saved[spec] = read(spec, MAX_SPEC)
        need(stat.S_IMODE(saved[spec][1][2]) in (0o444, 0o644), 'Unsupported handoff file mode')
        value = bounded_json(saved[spec][0], p, caps['max_nodes'])
        overlay = schema(value, mods, git_dir, spec)
        tests, roots, findings, git_sources = value['tests'], [], [], []
        total = 0
        limits = c.Limits(max_file_bytes=caps['max_file_bytes'], max_total_bytes=min(caps['max_total_bytes'], c.Limits().max_total_bytes),
                          max_tokens=caps['max_tokens'], max_context_tokens=caps['max_context_tokens'])
        methods = tests.get('methods', {})
        topology = ('methodless_whole_roots' if not methods else 'literal_methods_only' if
                    set(methods) == set(tests['classes']) else 'mixed_literal_methods_and_whole_roots')
        for selected in tests['classes']:
            # '$' roots and non-ASCII/custom paths are outside this conventional subset.
            source = 'src/test/java/' + selected.replace('.', '/') + '.java'
            row = {'class': selected, 'source': source, 'selection': 'literal_methods' if selected in methods else 'whole_root',
                   'requested_methods': methods.get(selected, []), 'scopes': [], 'incomplete_reasons': []}
            roots.append(row)
            def finding(kind):
                findings.append(dict(kind=kind, **{'class': selected}, source=source))
            if '$' in selected:
                finding('unsupported_conventional_root'); continue
            remaining = min(caps['max_file_bytes'], caps['max_total_bytes'] - total)
            need(remaining > 0, 'Aggregate source byte budget exceeded', 3)
            if source in overlay:
                entry = overlay[source]
                need(entry['bytes'] <= remaining, 'Pinned source byte budget exceeded', 3)
                path = absolute(entry['source'])
                data, info = read(path, entry['bytes'])
                need(len(data) == entry['bytes'] and sha(data) == entry['sha256'] and
                     stat.S_IMODE(info[2]) == int(entry['mode'], 8), 'Incoming source pin/mode mismatch')
                need(b'\r' not in data and b'\0' not in data and (not data or data.endswith(b'\n')), 'Incoming source violates LF contract')
                saved[path] = data, info
                provenance = dict(authority='incoming_packet', path=str(path), destination=source,
                                  sha256=sha(data), bytes=len(data), mode=entry['mode'])
            else:
                tree = c.git_read(git_dir, ['ls-tree', '-z', commit, '--', source], 8192)
                if not tree:
                    git_sources.append((source, None))
                    finding('missing_conventional_source'); continue
                data, provenance = c.read_baseline(git_dir, commit, source, remaining)
                provenance['authority'] = 'immutable_git_commit'
                git_sources.append((source, provenance))
            total += len(data)
            row['provenance'] = provenance
            try:
                row.update(inventory(data, selected, c, limits))
            except c.CensusError:
                row['incomplete_reasons'].append('Unsupported Java scope/grammar; source inventory incomplete')
            if row.get('root_matches') is False:
                finding('declared_root_mismatch')
            if row['incomplete_reasons']:
                finding('unresolved_source_scope')
            for name in methods.get(selected, []):
                candidates = [m for m in row.get('candidate_methods', []) if m['owner'] == selected and m['name'] == name]
                if len(candidates) != 1:
                    findings.append(dict(kind='missing_literal_method', **{'class': selected}, source=source, method=name))
            observed = {s['class'] for s in row['scopes']}
            groups = tests.get('report_groups', {}).get(selected)
            row['declared_group_classes'] = list(groups) if groups is not None else None
            row['missing_group_classes'] = sorted(observed - set(groups or {})) if groups is not None or len(observed) > 1 else []
            row['extra_group_classes'] = sorted(set(groups or {}) - observed)
            if (groups is not None and set(groups) != observed) or (len(observed) > 1 and groups is None):
                finding('report_group_scope_mismatch')
            bounded_output(dict(roots=roots, findings=findings), caps)
        for source, old in git_sources:
            if old is None:
                need(not c.git_read(git_dir, ['ls-tree', '-z', commit, '--', source], 8192), 'Git source changed', 3)
            else:
                _, now = c.read_baseline(git_dir, commit, source, old['bytes'])
                need(all(now[k] == old[k] for k in now), 'Git source changed', 3)
        recheck(saved)
        with opened(git_dir, directory=True) as fd:
            info = os.fstat(fd)
            need(directory == (info.st_dev, info.st_ino, info.st_mode), 'Git metadata path changed', 3)
        report = dict(version=1, status='INCOMPLETE' if findings else 'BOUNDED_SOURCE_ONLY',
                      git_dir=str(git_dir), commit=commit, handoff=dict(path=str(spec), sha256=sha(saved[spec][0]),
                      bytes=len(saved[spec][0]), mode=format(stat.S_IMODE(saved[spec][1][2]), '04o')),
                      topology=topology, tests={k: v for k, v in tests.items() if k != 'properties'},
                      roots=roots, findings=findings, absence_certificate=False,
                      dependencies={name: dict(bytes=size, sha256=digest) for name, (size, digest) in PINS.items()},
                      limits=caps, limitations=LIMITATIONS)
        bounded_output(report, caps)
        return report
    except (c.CensusError, p.PacketError) as error:
        # Never echo candidate source text, schema values, Git stderr or property content.
        raise PreflightError('Pinned helper rejected input/source or exhausted a bound', getattr(error, 'code', 2)) from error
    except (OSError, UnicodeError, RecursionError, TypeError, KeyError, OverflowError) as error:
        raise PreflightError('Unsupported/malformed input or bounded source read failure') from error


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--git-dir', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--handoff', required=True)
    for name, cap in CAPS.items():
        parser.add_argument('--' + name.replace('_', '-'), type=int, default=cap)
    args = vars(parser.parse_args(argv))
    try:
        report = check(args.pop('git_dir'), args.pop('commit'), args.pop('handoff'), **args)
        print(json.dumps(report, ensure_ascii=True, sort_keys=True))
        return 3 if report['status'] == 'INCOMPLETE' else 0
    except PreflightError as error:
        message = json.dumps(dict(version=1, status='INCOMPLETE' if error.code == 3 else 'INVALID',
                                  reason=str(error), limitations=LIMITATIONS))
        cap = args.get('max_output_bytes', CAPS['max_output_bytes'])
        cap = cap if type(cap) is int and cap > 0 else CAPS['max_output_bytes']
        if len(message.encode()) + 1 > cap:
            message = json.dumps(dict(status='INCOMPLETE' if error.code == 3 else 'INVALID'))
        if len(message.encode()) + 1 > cap:
            message = '{}' if cap >= 3 else ''
        if message:
            print(message)
        return error.code


if __name__ == '__main__':
    sys.exit(main())
