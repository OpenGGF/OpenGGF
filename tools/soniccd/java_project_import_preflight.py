#!/usr/bin/env python3
"""Bounded read-only hints for explicitly selected immutable project types.

Inputs: canonical Git metadata, full commit, explicit qualified type=source pairs,
and one exact Git source or externally byte/hash-pinned FINAL Java file.
Opt-in known enums compare only explicit owner.enum.MEMBER constant selectors.
Opt-in member owners diagnose nonstatic member classes after static-star imports.
Origin: 2026-11-05 Sonic CD source-only handoff missed CdR12aCameraArtResidency
import; 92-second Maven test-precompile rejected it before tests launched.
Reuses hash-pinned enum lexer and census safe readers; never a Java compiler.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import stat
import subprocess
import sys
import time
import types

VERSION = 1
SHA1 = re.compile(r'[0-9a-f]{40}\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')
IDENT = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*\Z')
RESERVED = frozenset(('abstract assert boolean break byte case catch char class const continue default '
    'do double else enum extends final finally float for goto if implements import instanceof int '
    'interface long native new package private protected public return short static strictfp super '
    'switch synchronized this throw throws transient try void volatile while true false null _').split())
TYPE_WORDS = frozenset(('class', 'interface', 'enum', 'record'))
DEPENDENCIES = {
    'java_declaration_census.py': (45026, '91d710f6b8aefbe918ff19ef085113cd12ea3d5d1cc30ad856fb25ab192465ff'),
    'enum_contract_audit.py': (47279, 'bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d'),
}
LIMITATIONS = [
    'Opt-in known enums compare fully spelled selected owner.enum.MEMBER tokens with immutable declared enum constants; method calls/class literals and unselected qualifiers stay out of scope. No static-field/member/accessibility or compiler resolution claim.',
    'Known-enum value/type shadows, indirect receivers, competing imports and unsupported containing scopes block missing-member hints. Other unsupported scopes/import availability remain incomplete and may retain explicitly ambiguous hints.',
    'Source-only possible-missing-import hints for explicitly selected known project types; no compiler, ABI, accessibility, import-completeness, test or Java-resolution claim.',
    'Unselected project types and external-library symbols are out of scope. No classpath, inheritance, generated source, module, type inference or expression resolution is performed.',
    'Root identifiers matching a selected type are lexical candidates, including possible values/methods. Selected-name shadow clues, unknown static imports, competing explicit imports and unsupported scopes make observations incomplete. Undetected value/inherited/package shadowing can still cause false positives or false availability.',
    'Ordinary ASCII named top-level/member types and lexical import spellings are supported. Local/anonymous/annotation type scopes, Unicode preprocessing and unrecognized header/scope syntax are incomplete. Method/field/body Java grammar is not validated; malformed Java can still yield hints.',
    'Comments, string, character and text-block contents are redacted by the shared lexer. A token following a dot is never another root-type candidate; fully qualified spellings are not resolved or certified.',
    'Selected source/blob bytes and canonical path identities are re-observed non-atomically; change-and-restore can escape detection. Git metadata must be trusted. No index/cache, destination inference, supplied Java execution, Maven, shell, network, Git mutation or output files.',
    'Exit0 means no hints in the supported selected lexical scope only; exit1 means possible missing imports; exit2 invalid input/pin/read; exit3 incomplete/ambiguous/bounded observation, possibly with hints.',
]


class PreflightError(ValueError):
    def __init__(self, message, code=3):
        super().__init__(message)
        self.code = code


def need(condition, message, code=3):
    if not condition:
        raise PreflightError(message, code)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def absolute(value):
    need(isinstance(value, str) and 0 < len(value) <= 4096 and value.startswith('/') and
         not any(ord(c) < 32 or ord(c) == 127 for c in value) and '\\' not in value and ':' not in value,
         'Require canonical absolute POSIX path', 2)
    need(len(value.split('/')) <= 65 and all(p and p not in ('.', '..') for p in value.split('/')[1:]),
         'Path aliases/traversal or more than64 components unsupported', 2)
    return Path(value)


def relative(value):
    need(isinstance(value, str) and 0 < len(value) <= 4096 and '\\' not in value and ':' not in value and
         not any(ord(c) < 32 or ord(c) == 127 for c in value) and value.endswith('.java'),
         'Require exact canonical relative .java path', 2)
    need(len(value.split('/')) <= 64 and all(p and p not in ('.', '..') and p.lower() != '.git' for p in value.split('/')),
         'Unsafe relative Java path', 2)
    return value


@contextmanager
def opened(path, directory=False):
    """Bootstrap trusted helpers using census's no-follow descriptor-walk pattern."""
    path = absolute(str(path))
    need(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW') and hasattr(os, 'O_DIRECTORY'),
         'POSIX no-follow descriptor support required', 2)
    fds = []
    try:
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
                 'Require regular file/directory without symlinks', 2)
        yield fd
    except OSError as error:
        raise PreflightError('Cannot safely open selected path: ' + str(error), 2) from error
    finally:
        for fd in reversed(fds):
            os.close(fd)


_DEPS = None


def dependencies():
    global _DEPS
    if _DEPS is None:
        modules = {}
        for name, (size, digest) in DEPENDENCIES.items():
            path = Path(__file__).absolute().parent / name
            with opened(path) as fd:
                before = os.fstat(fd)
                need(before.st_size == size, 'Trusted sibling helper byte pin mismatch: ' + name, 2)
                data = bytearray()
                while len(data) <= size:
                    block = os.read(fd, min(65536, size + 1 - len(data)))
                    if not block:
                        break
                    data.extend(block)
                need((before.st_dev, before.st_ino, before.st_mode, before.st_size, before.st_mtime_ns, before.st_ctime_ns) ==
                     (lambda info: (info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns, info.st_ctime_ns))(os.fstat(fd)) and
                     len(data) == size and sha(data) == digest,
                     'Trusted sibling helper hash/identity pin mismatch: ' + name, 2)
            module = types.ModuleType('_project_import_' + name.replace('.', '_'))
            module.__file__ = str(path)
            sys.modules[module.__name__] = module
            # Only these trusted hash-pinned Python helpers execute, never Java input.
            exec(compile(bytes(data), str(path), 'exec'), module.__dict__)
            modules[name] = module
        _DEPS = modules['java_declaration_census.py'], modules['enum_contract_audit.py']
    return _DEPS


def read_final(path, cap):
    census, _ = dependencies()
    try:
        return census.read_final(path, cap)
    except census.CensusError as error:
        raise PreflightError(str(error), error.code) from error


def directory_id(path):
    census, _ = dependencies()
    try:
        return census.directory_id(path)
    except census.CensusError as error:
        raise PreflightError(str(error), error.code) from error


@dataclass(frozen=True)
class Limits:
    max_file_bytes: int = 1048576
    max_total_bytes: int = 4194304
    max_tokens: int = 100000
    max_context_tokens: int = 4096
    max_depth: int = 32
    max_hits: int = 256
    max_known_types: int = 16
    max_seconds: int = 30
    max_output_bytes: int = 1048576

    def validate(self):
        for name, ceiling in vars(Limits()).items():
            value = getattr(self, name)
            need(type(value) is int and 1 <= value <= ceiling, name + ' outside lowerable bound', 2)


class Engine:
    def __init__(self, limits):
        self.limits = limits
        self.deadline = time.monotonic() + limits.max_seconds
        self.bytes = 0
        self.tokens = 0

    def check(self):
        need(time.monotonic() < self.deadline, 'Preflight time budget exhausted')

    def git(self, git_dir, args, cap):
        self.check()
        allowed = len(args) == 3 and args[:2] in (['cat-file', '-t'], ['cat-file', '-s'], ['cat-file', 'blob']) and SHA1.fullmatch(args[2])
        if len(args) == 5 and args[:2] == ['ls-tree', '-z'] and SHA1.fullmatch(args[2]) and args[3] == '--':
            relative(args[4]); allowed = True
        need(allowed and type(cap) is int and 0 <= cap <= self.limits.max_file_bytes,
             'Unsupported Git argv/output bound', 2)
        env = dict(PATH=os.defpath, HOME='/nonexistent', XDG_CONFIG_HOME='/nonexistent',
                   GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_SYSTEM=os.devnull, GIT_CONFIG_GLOBAL=os.devnull,
                   GIT_TERMINAL_PROMPT='0', GIT_NO_REPLACE_OBJECTS='1', GIT_NO_LAZY_FETCH='1',
                   GIT_OPTIONAL_LOCKS='0', GIT_ALLOW_PROTOCOL='', LC_ALL='C')
        argv = ['git', '--no-replace-objects', '--no-optional-locks', '--literal-pathspecs',
                '--git-dir=' + str(git_dir), '-c', 'core.fsmonitor=false', '-c', 'protocol.allow=never',
                '-c', 'core.hooksPath=/dev/null', *args]
        output = {'out': bytearray(), 'err': bytearray()}
        try:
            proc = subprocess.Popen(argv, cwd='/', env=env, stdin=subprocess.DEVNULL,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=False)
        except OSError as error:
            raise PreflightError('Cannot start read-only local Git: ' + str(error), 2) from error
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(proc.stdout, selectors.EVENT_READ, ('out', cap))
                selector.register(proc.stderr, selectors.EVENT_READ, ('err', 4096))
                while selector.get_map():
                    self.check()
                    for key, _ in selector.select(min(0.1, max(0, self.deadline - time.monotonic()))):
                        label, limit = key.data
                        block = os.read(key.fd, min(65536, limit + 1 - len(output[label])))
                        if not block:
                            selector.unregister(key.fileobj)
                        else:
                            output[label].extend(block)
                            need(len(output[label]) <= limit, 'Git output byte budget exhausted')
            try:
                status = proc.wait(timeout=max(0.001, self.deadline - time.monotonic()))
            except subprocess.TimeoutExpired as error:
                raise PreflightError('Git read time budget exhausted') from error
            self.check()
            need(status == 0, 'Git read failed: ' + output['err'].decode('utf-8', 'replace').strip(), 2)
            return bytes(output['out'])
        finally:
            if proc.poll() is None:
                proc.kill()
            proc.wait()
            proc.stdout.close()
            proc.stderr.close()

    def blob(self, git_dir, revision, path, account=True):
        path = relative(path)
        entry = self.git(git_dir, ['ls-tree', '-z', revision, '--', path], min(8192, self.limits.max_file_bytes))
        need(entry.endswith(b'\0') and entry.count(b'\0') == 1 and b'\t' in entry,
             'Exact Git Java source absent/ambiguous', 2)
        metadata, name = entry[:-1].split(b'\t', 1)
        parts = metadata.decode('ascii').split()
        need(len(parts) == 3 and parts[0] in ('100644', '100755') and parts[1] == 'blob' and
             SHA1.fullmatch(parts[2]) and name == path.encode(), 'Select exact regular Git blob; symlinks rejected', 2)
        oid = parts[2]
        size_text = self.git(git_dir, ['cat-file', '-s', oid], min(128, self.limits.max_file_bytes)).strip()
        need(size_text.isdigit() and len(size_text) <= 12, 'Invalid blob byte count')
        size = int(size_text)
        need(size <= self.limits.max_file_bytes, 'Git source byte budget exhausted')
        if account:
            self.account(size)
        data = self.git(git_dir, ['cat-file', 'blob', oid], size)
        need(len(data) == size and hashlib.sha1(b'blob ' + str(size).encode() + b'\0' + data).hexdigest() == oid,
             'Git blob hash/size mismatch')
        return data, dict(kind='git_blob', git_dir=str(git_dir), revision=revision,
                          path=path, blob_sha1=oid, bytes=size, sha256=sha(data))

    def account(self, size):
        self.bytes += size
        need(self.bytes <= self.limits.max_total_bytes, 'Aggregate selected source byte budget exhausted')
        self.check()

    def parse(self, data):
        self.check()
        need(len(data) <= self.limits.max_file_bytes, 'Java source byte budget exhausted')
        _, lexer = dependencies()
        try:
            java = lexer.lex(data, self.limits)
        except lexer.AuditError as error:
            raise PreflightError(str(error), 3) from error
        self.tokens += len(java.tokens)
        need(self.tokens <= self.limits.max_tokens, 'Aggregate token budget exhausted')
        self.check()
        return Scope(java, self.limits, self.check)


class Scope:
    def __init__(self, java, limits, check):
        self.java, self.ts, self.limits, self.check = java, java.tokens, limits, check
        self.header_end, self.declarations, self.reasons = 0, [], []
        self.unsupported_ranges = []
        self.parse_headers()
        self.parse_types()

    def v(self, i):
        return self.ts[i].value if 0 <= i < len(self.ts) else '<EOF>'

    def parse_headers(self):
        i, package_seen = 0, False
        while self.v(i) in ('package', 'import'):
            keyword = self.v(i)
            need(keyword != 'package' or (not package_seen and i == 0), 'Duplicate/misplaced package declaration')
            package_seen |= keyword == 'package'
            start = i
            while self.v(i) not in (';', '<EOF>'):
                i += 1
                need(i - start <= self.limits.max_context_tokens, 'Package/import context budget exhausted')
            need(self.v(i) == ';', 'Unterminated source header')
            # Validate the original token spelling before trusting the shared lexer's joined name.
            # Whitespace/comments can separate identifiers; they never create one identifier.
            first = start + 1
            if keyword == 'import' and self.v(first) == 'static':
                first += 1
            count = i - first
            need(count > 0 and count % 2 == 1, 'Malformed package/import token sequence')
            for at in range(first, i):
                self.check()
                value = self.v(at)
                if (at - first) % 2:
                    need(value == '.', 'Malformed package/import separator')
                elif value == '*':
                    need(keyword == 'import' and at == i - 1 and at >= first + 2,
                         'Malformed package/import wildcard')
                else:
                    need(IDENT.fullmatch(value) and value not in RESERVED,
                         'Malformed package/import identifier')
            i += 1
        self.header_end = i
        need(not any(t.value in ('package', 'import') for t in self.ts[i:]), 'Misplaced package/import declaration')
        for name in [self.java.package, *(n[:-2] if n.endswith('.*') else n for _, n in self.java.imports)]:
            need(not name or all(IDENT.fullmatch(p) and p not in RESERVED for p in name.split('.')),
                 'Unsupported reserved/malformed package/import name')
        need(not any(self.v(i) == 'package' and self.v(i+1) == 'static' for i in range(self.header_end)),
             'Unsupported static package declaration')

    def parse_types(self):
        parents, stack = {}, []
        for i, token in enumerate(self.ts):
            self.check()
            if token.value == '{':
                need(len(stack) < self.limits.max_depth, 'Brace nesting budget exhausted')
                parents[i] = stack[-1] if stack else None
                stack.append(i)
            elif token.value == '}':
                stack.pop()  # shared lexer already checked delimiter pairing
        openings = {}
        for i in range(self.header_end, len(self.ts)):
            self.check()
            word = self.v(i)
            if word not in TYPE_WORDS or self.v(i-1) == '.':
                continue
            need(not (word == 'interface' and self.v(i-1) == '@'), 'Annotation type scope unsupported')
            need(IDENT.fullmatch(self.v(i+1)) and self.v(i+1) not in RESERVED, 'Unsupported named-type syntax')
            j = i + 2
            while self.v(j) not in ('{', ';', '<EOF>'):
                self.check()
                need(j - i <= self.limits.max_context_tokens, 'Type header context budget exhausted')
                following = self.java.pairs[j] + 1 if self.v(j) in ('(', '[') else j + 1
                need(following - i <= self.limits.max_context_tokens, 'Type header context budget exhausted')
                j = following
            need(j - i <= self.limits.max_context_tokens, 'Type header context budget exhausted')
            need(self.v(j) == '{', 'Unsupported named-type header')
            parent = openings.get(parents[j])
            local = parents[j] is not None and parent is None
            owner = parent['qualified'] if parent else self.java.package
            row = dict(name=self.v(i+1), name_at=i+1, start=i, opening=j, closing=self.java.pairs[j],
                       owner=owner, qualified='.'.join(p for p in (owner, self.v(i+1)) if p),
                       local=local, top_level=parents[j] is None)
            self.declarations.append(row)
            openings[j] = row
            if local:
                self.unsupported_ranges.append((j, self.java.pairs[j]))
                self.reasons.append('Local type scope unsupported at line ' + str(self.ts[i].line))
            if any(self.v(k) in ('extends', 'implements', 'permits', '<') for k in range(i+2, j)):
                self.unsupported_ranges.append((j, self.java.pairs[j]))
                self.reasons.append('Inheritance/generic type scope not resolved at line ' + str(self.ts[i].line))
        need(any(d['top_level'] for d in self.declarations), 'No ordinary top-level named type observed')
        # Anonymous bodies can contribute inherited/member names: do not guess.
        for i in range(self.header_end, len(self.ts)-1):
            if self.v(i) == ')' and self.v(i+1) == '{':
                opening = self.java.pairs[i]
                j = opening - 1
                while j >= self.header_end and self.v(j) not in (';', '{', '}', '='):
                    self.check()
                    if self.v(j) == 'new':
                        self.unsupported_ranges.append((i+1, self.java.pairs[i+1]))
                        self.reasons.append('Anonymous type scope unsupported at line ' + str(self.ts[j].line))
                        break
                    j -= 1
                    need(opening - j <= self.limits.max_context_tokens, 'Anonymous-scope context budget exhausted')

    def local_availability(self, name, position):
        for d in self.declarations:
            if d['name'] == name and not d['local']:
                if d['top_level']:
                    return 'file_top_level_type'
                owner = next((p for p in self.declarations if p['qualified'] == d['owner'] and
                              p['opening'] < position < p['closing']), None)
                if owner:
                    return 'file_member_type'
        return None


def analyze(scope, known, engine):
    reasons, uses, hints = list(scope.reasons), [], []
    declarations = {d['name_at'] for d in scope.declarations}
    names = {k['simple_name'] for k in known}
    shadowed = set()
    for i, t in enumerate(scope.ts):
        engine.check()
        if i < scope.header_end or t.value not in names or i in declarations or scope.v(i-1) == '.':
            continue
        prev, nxt = scope.v(i-1), scope.v(i+1)
        if ((nxt in ('=', ';', ',', ')', ':') and
             (IDENT.fullmatch(prev) or prev in (']', '>')) and prev not in ('return', 'throw', 'new', 'case')) or (
                prev == '<' and nxt in ('>', ',', 'extends'))):
            shadowed.add(t.value)
    for d in scope.declarations:
        if d['name'] in names and d['top_level'] and any(
                not static and name.rsplit('.', 1)[-1] == d['name'] and name != d['qualified']
                for static, name in scope.java.imports):
            reasons.append('Top-level declared type conflicts with explicit import: ' + d['name'])
    for name in sorted(shadowed):
        reasons.append('Selected-name value/type-parameter shadow ambiguity: ' + name)
    for i, token in enumerate(scope.ts):
        engine.check()
        if i < scope.header_end or i in declarations or scope.v(i-1) == '.':
            continue
        matches = [k for k in known if k['simple_name'] == token.value]
        if not matches:
            continue
        engine.check()
        need(len(uses) < engine.limits.max_hits, 'Selected-type hit budget exhausted')
        local = scope.local_availability(token.value, i)
        candidate = matches[0] if len(matches) == 1 else None
        availability = local
        uncertain = token.value in shadowed or len(matches) != 1
        if len(matches) != 1:
            reasons.append('Several selected project types share simple name: ' + token.value)
        if not availability and candidate:
            fqcn, package = candidate['qualified_name'], candidate['package']
            direct = [name for static, name in scope.java.imports if not static and name.rsplit('.',1)[-1] == token.value]
            static = [name for is_static, name in scope.java.imports if is_static]
            if direct and direct != [fqcn]:
                uncertain = True
                reasons.append('Competing/duplicate explicit import for selected name: ' + token.value)
            if any(name.endswith('.*') or name.rsplit('.',1)[-1] == token.value for name in static):
                uncertain = True
                reasons.append('Static-import type/value binding unobserved for selected name: ' + token.value)
            if direct != [fqcn] and scope.java.package != package and any(
                    not is_static and name.endswith('.*') and name != package + '.*'
                    for is_static, name in scope.java.imports):
                uncertain = True
                reasons.append('Other wildcard-import bindings unobserved for selected name: ' + token.value)
            if direct == [fqcn]:
                availability = 'explicit_import'
                # Exact type import remains visible; unknown static imports do not
                # erase its spelling, but uncertainty above remains explicit.
            elif scope.java.package == package:
                availability = 'same_package'
            elif (False, package + '.*') in scope.java.imports:
                availability = 'package_wildcard_import'
            else:
                availability = 'missing_selected_import'
        row = dict(name=token.value, line=token.line, availability=availability or 'ambiguous_selected_name',
                   ambiguous=uncertain, candidates=[k['qualified_name'] for k in matches])
        uses.append(row)
        if availability in (None, 'missing_selected_import'):
            hints.append(dict(name=token.value, line=token.line, kind='possible_missing_import',
                              suggested_import=candidate['qualified_name'] if candidate else None,
                              ambiguous=uncertain))
    return uses, hints, sorted(set(reasons))



def known_enum(scope, qualified, provenance, engine):
    """One exact ordinary enum declaration; shared token extractor stays unchanged."""
    engine.check()
    need(not scope.reasons, 'Known enum source scope unsupported: ' + '; '.join(scope.reasons))
    matches = [d for d in scope.declarations if d['qualified'] == qualified]
    need(len(matches) <= 1, 'Selected enum declaration is lexically ambiguous: ' + qualified)
    need(len(matches) == 1 and scope.v(matches[0]['start']) == 'enum' and not matches[0]['local'],
         'Selected enum lacks exact ordinary declaration provenance: ' + qualified, 2)
    declaration = matches[0]
    roots = [d for d in scope.declarations if d['top_level'] and
             d['opening'] <= declaration['opening'] < declaration['closing'] <= d['closing']]
    need(len(roots) == 1, 'Known enum has ambiguous lexical top-level owner')
    _, lexer = dependencies()
    try:
        constants, actual, local = lexer.enum_constants(scope.java, declaration['name'], qualified, engine.limits)
    except lexer.AuditError as error:
        raise PreflightError('Known enum constants incomplete: ' + str(error), 3) from error
    engine.check()
    need(actual == qualified, 'Known enum qualified token spelling differs')
    return dict(qualified_name=qualified, local_name=local, owner_type=roots[0]['qualified'],
                simple_owner=roots[0]['name'], package=scope.java.package, constants=constants,
                declaration_line=scope.ts[declaration['name_at']].line, provenance=provenance)


def analyze_enums(scope, known, engine, prior_hits):
    """Compare only explicit owner.enum.MEMBER token chains, never inferred expressions."""
    uses, hints, reasons, covered = [], [], [], set()
    if not known:
        return uses, hints, reasons
    _, lexer = dependencies()
    shadows = lexer.direct_shadow_names(scope.java, '<consumer>', '<immutable enum>', '')
    annotated_generics = False
    for i, token in enumerate(scope.ts):
        engine.check()
        if token.value in ('<', ',') and scope.v(i+1) == '@':
            # An annotation can hide a method type-parameter name from the
            # ordinary shadow heuristic. Do not infer its binding or scope.
            annotated_generics = True
            reasons.append('Annotated generic parameter scope unobserved at line ' + str(token.line))
        if scope.v(i-1) in ('<', ',') and scope.v(i+1) in ('>', ',', 'extends', '&'):
            shadows.add(token.value)
    for i, token in enumerate(scope.ts):
        engine.check()
        if scope.v(i+1) == '-' and scope.v(i+2) == '>':
            shadows.add(token.value)
        if token.value == '(' and i in scope.java.pairs:
            close = scope.java.pairs[i]
            if scope.v(close+1) == '-' and scope.v(close+2) == '>':
                need(close-i <= engine.limits.max_context_tokens, 'Lambda parameter context budget exhausted')
                for at in range(i+1, close):
                    engine.check()
                    if IDENT.fullmatch(scope.v(at)) and scope.v(at+1) in (',', ')'):
                        shadows.add(scope.v(at))
    qualifiers = []
    for item in known:
        for spelling in dict.fromkeys([item['qualified_name'], item['local_name']]):
            qualifiers.append((item, spelling.split('.'), spelling == item['qualified_name']))
    for i in range(scope.header_end, len(scope.ts)):
        engine.check()
        if i in covered:
            continue
        matches = []
        for item, parts, full in qualifiers:
            if scope.v(i) != parts[0]:
                continue
            need(2 * len(parts) + 1 <= engine.limits.max_context_tokens,
                 'Known-enum selector context budget exhausted')
            matched = 0
            for n, part in enumerate(parts):
                if scope.v(i + n*2) != part or n and scope.v(i + n*2-1) != '.':
                    break
                matched += 1
            if matched != len(parts):
                # Never join split identifiers into an intended qualifier. Only inspect
                # a malformed chain after an actually matched selected owner token.
                threshold = len(item['package'].split('.')) + 1 if full and item['package'] else 1
                if matched >= threshold and scope.v(i+1) == '.':
                    end = min(len(scope.ts), i + 2*len(parts)+2)
                    for at in range(i+1, end-1):
                        if scope.v(at) == ';':
                            break
                        if scope.v(at) in ('*', '?', '<', '>', '@') or scope.v(at) == scope.v(at+1) == '.' or (
                                IDENT.fullmatch(scope.v(at)) and IDENT.fullmatch(scope.v(at+1)) and
                                scope.v(at) not in RESERVED and scope.v(at+1) not in RESERVED):
                            reasons.append('Malformed known-enum qualifier tokens at line ' + str(scope.ts[i].line))
                            break
                continue
            dot = i + 2*len(parts)-1
            if scope.v(dot) != '.':
                continue  # The enum type itself, not a selected constant.
            member_at = dot + 1
            member = scope.v(member_at)
            if member == 'class' or IDENT.fullmatch(member) and member not in RESERVED and scope.v(member_at+1) == '(':
                continue  # Class literals/method calls are not enum-constant selectors.
            malformed = not IDENT.fullmatch(member) or member in RESERVED or (
                IDENT.fullmatch(scope.v(member_at+1)) and scope.v(member_at+1) not in RESERVED) or (
                scope.v(member_at+1) == '.' and not IDENT.fullmatch(scope.v(member_at+2)))
            matches.append((item, parts, full, member_at, member, malformed))
        if not matches:
            continue
        need(prior_hits + len(uses) < engine.limits.max_hits, 'Selected-type/enum hit budget exhausted')
        # Two selected enums can share the same short owner.enum spelling. Never choose one.
        unique = {m[0]['qualified_name'] for m in matches}
        item, parts, full, member_at, member, malformed = matches[0]
        covered.update(range(i, member_at+1))
        blocked, uncertain, availability = False, False, 'fully_spelled_qualifier' if full else 'unobserved_qualifier'
        local_reasons = []
        if annotated_generics:
            blocked = True
            local_reasons.append('Known-enum binding blocked by annotated generic parameter scope')
        if malformed:
            blocked = True
            local_reasons.append('Malformed known-enum member tokens at line ' + str(scope.ts[i].line))
        if len(unique) != 1:
            blocked = True
            local_reasons.append('Several selected enums share qualifier at line ' + str(scope.ts[i].line))
        if any(opening < i < closing for opening,closing in scope.unsupported_ranges):
            blocked = True
            local_reasons.append('Unsupported containing scope for known-enum selector at line ' + str(scope.ts[i].line))
        elif scope.reasons:
            uncertain = True
            local_reasons.append('Other consumer scopes remain unresolved for known-enum selector at line ' + str(scope.ts[i].line))
        if scope.v(i-1) == '.':
            blocked = True
            local_reasons.append('Indirect known-enum qualifier receiver at line ' + str(scope.ts[i].line))
        if parts[0] in shadows or any(d['qualified'] == item['owner_type'] for d in scope.declarations):
            blocked = True
            local_reasons.append('Known-enum qualifier value/type shadow ambiguity at line ' + str(scope.ts[i].line))
        if full:
            # The first package-looking identifier can bind an imported type
            # or value instead. Fully spelled tokens do not bypass shadowing.
            if any(not name.endswith('.*') and name.rsplit('.',1)[-1] == parts[0]
                   for _,name in scope.java.imports):
                blocked = True
                local_reasons.append('Imported first identifier shadows fully spelled known-enum qualifier at line ' + str(scope.ts[i].line))
            if any(name.endswith('.*') for _,name in scope.java.imports):
                uncertain = True
                local_reasons.append('Wildcard import bindings unobserved for fully spelled known-enum qualifier at line ' + str(scope.ts[i].line))
        else:
            direct = [name for static,name in scope.java.imports if not static and name.rsplit('.',1)[-1] == parts[0]]
            static = [name for is_static,name in scope.java.imports if is_static]
            if direct and direct != [item['owner_type']] or any(not name.endswith('.*') and name.rsplit('.',1)[-1] == parts[0] for name in static):
                blocked = True
                local_reasons.append('Conflicting/static import for known-enum qualifier at line ' + str(scope.ts[i].line))
            elif direct == [item['owner_type']]:
                availability = 'explicit_import'
            elif scope.java.package == item['package']:
                availability = 'same_package'
            elif (False,item['package']+'.*') in scope.java.imports:
                availability = 'package_wildcard_import'
                uncertain = uncertain or any(not static and name.endswith('.*') and name != item['package']+'.*' for static,name in scope.java.imports)
            else:
                uncertain = True
            if any(name.endswith('.*') for name in static):
                uncertain = True
            if uncertain:
                local_reasons.append('Known-enum qualifier availability unobserved at line ' + str(scope.ts[i].line))
        reasons.extend(local_reasons)
        exists = member in item['constants'] if len(unique) == 1 and not malformed else None
        row = dict(enum=item['qualified_name'] if len(unique) == 1 else None, candidates=sorted(unique),
                   qualifier='.'.join(parts), member=None if malformed else member,
                   line=scope.ts[i].line, member_line=scope.ts[member_at].line if member_at < len(scope.ts) else scope.ts[i].line,
                   known_constant=exists, ambiguous=blocked or uncertain, availability=availability)
        uses.append(row)
        if not blocked and not exists:
            hints.append(dict(kind='possible_missing_enum_member', enum=item['qualified_name'],
                              qualifier=row['qualifier'], member=member, line=row['member_line'],
                              ambiguous=uncertain, reason='not_in_selected_enum_constants'))
    return uses, hints, sorted(set(reasons))


MEMBER_LIMITATIONS = [
    'Opt-in member-owner checks observe only direct ordinary declared member types under an explicitly imported static-on-demand owner. Nonstatic member classes require ordinary type imports, qualification or a lexical owner; no compiler, accessibility, inherited/type/value resolution claim.',
    'Selected member uses remain lexical candidates. Unsupported owner modifiers, inheritance, generic/anonymous/local scopes, value/method shadows, competing imports and indirect qualifiers are incomplete/advisory, never a clean binding proof. Implicit-static records/enums/interfaces and interface members are distinguished from nonstatic classes.',
    'Member owners use exact immutable Git mappings or independently byte/hash-pinned canonical FINAL files. All selection, source, token, context, depth, time, hit/output and no-follow/re-observation bounds are shared; direct member inventory also cannot exceed max_hits.',
]


def member_enum_anonymous_reasons(scope, engine):
    """Opt-in uncertainty only; the legacy shared Scope/JSON stays byte-exact."""
    reasons = []
    for declaration in scope.declarations:
        engine.check()
        if scope.v(declaration['start']) != 'enum':
            continue
        at = start = declaration['opening'] + 1
        while at < declaration['closing'] and scope.v(at) != ';':
            engine.check()
            need(at-start <= engine.limits.max_context_tokens, 'Enum constant context budget exhausted')
            token = scope.v(at)
            if token in ('(', '[', '{'):
                end = scope.java.pairs[at] + 1
                need(end-start <= engine.limits.max_context_tokens, 'Enum constant context budget exhausted')
                if token == '{':
                    reasons.append('Enum constant anonymous type scope unsupported at line ' + str(scope.ts[at].line))
                at = end
            else:
                if token == ',':
                    start = at + 1
                at += 1
    return reasons


def member_qualified_construction(scope, position, engine):
    """Observe .new (including bounded explicit constructor arguments), not its receiver type."""
    at = position - 1
    if scope.v(at) == '>':
        depth = 0
        while at >= scope.header_end:
            engine.check()
            need(position-at <= engine.limits.max_context_tokens, 'Member constructor context budget exhausted')
            if scope.v(at) == '>':
                depth += 1
            elif scope.v(at) == '<':
                depth -= 1
                if depth == 0:
                    at -= 1
                    break
            elif scope.v(at) in (';', '{', '}', '='):
                return False
            at -= 1
    return scope.v(at) == 'new' and scope.v(at-1) == '.'


def known_member_owner(scope, qualified, provenance, engine):
    """Direct declarations only; never infer inherited members or staticness."""
    matches = [d for d in scope.declarations if d['qualified'] == qualified and not d['local']]
    need(len(matches) == 1, 'Known member owner lacks exact ordinary declaration provenance: ' + qualified, 2)
    owner = matches[0]
    reasons = list(scope.reasons) + member_enum_anonymous_reasons(scope, engine)
    members = []
    modifiers = {'public','protected','private','abstract','final','static','strictfp'}
    for declaration in scope.declarations:
        engine.check()
        if declaration['owner'] != qualified or declaration['local']:
            continue
        kind = scope.v(declaration['start'])
        static_kind, is_static = 'unknown_member_staticness', None
        if kind in ('record','enum','interface'):
            static_kind, is_static = 'implicit_static_' + kind, True
        elif scope.v(owner['start']) == 'interface':
            static_kind, is_static = 'implicit_static_interface_member', True
        else:
            at = declaration['start'] - 1
            prefix = []
            while at >= scope.header_end and scope.v(at) not in (';','{','}'):
                engine.check()
                need(declaration['start']-at <= engine.limits.max_context_tokens, 'Member modifier context budget exhausted')
                prefix.append(scope.v(at));at -= 1
            if all(word in modifiers for word in prefix):
                is_static = 'static' in prefix
                static_kind = 'explicit_static_class' if is_static else 'nonstatic_member_class'
            else:
                reasons.append('Unsupported direct member modifier context at line ' + str(scope.ts[declaration['name_at']].line))
        need(len(members) < engine.limits.max_hits, 'Known member inventory budget exhausted')
        members.append(dict(name=declaration['name'], qualified_name=declaration['qualified'], owner=qualified,
            kind=kind, static=is_static, static_kind=static_kind, declaration_line=scope.ts[declaration['name_at']].line))
    if len({m['qualified_name'] for m in members}) != len(members):
        reasons.append('Duplicate direct member declarations unresolved')
    return dict(qualified_name=qualified, package=scope.java.package, members=members,
                incomplete_reasons=sorted(set(reasons)), provenance=provenance)


def analyze_members(scope, owners, engine, prior_hits):
    """Static-star import diagnostic, not general Java name resolution."""
    uses, hints = [], []
    reasons = [item['qualified_name'] + ': ' + reason for item in owners for reason in item['incomplete_reasons']]
    active = [item for item in owners if (True,item['qualified_name']+'.*') in scope.java.imports]
    names = {}
    for item in active:
        for member in item['members']:
            names.setdefault(member['name'],[]).append((item,member))
    if not names:
        return uses,hints,sorted(set(reasons))
    member_scope_reasons = member_enum_anonymous_reasons(scope, engine)
    reasons.extend(member_scope_reasons)
    by_name, visible_cache = {}, {}
    for declaration in scope.declarations:
        engine.check()
        by_name.setdefault(declaration['name'],[]).append(declaration)
    duplicate_types = len({d['qualified'] for d in scope.declarations}) != len(scope.declarations)
    if duplicate_types:
        reasons.append('Duplicate consumer type declarations unresolved for member binding')
    def visible(name,position):
        key = name,position
        if key not in visible_cache:
            containing,rows = set(),[]
            if name in by_name:
                for declaration in scope.declarations:
                    engine.check()
                    if declaration['opening'] < position < declaration['closing']:
                        containing.add(declaration['qualified'])
                for declaration in by_name[name]:
                    engine.check()
                    if not declaration['local'] and (declaration['top_level'] or declaration['owner'] in containing):
                        rows.append(declaration)
            visible_cache[key] = rows
        return visible_cache[key]
    _, lexer = dependencies()
    shadows = lexer.direct_shadow_names(scope.java,'<consumer>','<member owner>','')
    annotated_generic = False
    for i,token in enumerate(scope.ts):
        engine.check()
        if token.value in ('<',',') and scope.v(i+1) == '@': annotated_generic = True
        if scope.v(i-1) in ('<',',') and scope.v(i+1) in ('>',',','extends','&'): shadows.add(token.value)
        if scope.v(i+1) == '-' and scope.v(i+2) == '>': shadows.add(token.value)
    declarations = {d['name_at'] for d in scope.declarations}
    for i,token in enumerate(scope.ts):
        engine.check()
        if i < scope.header_end or i in declarations or token.value not in names:
            continue
        need(prior_hits+len(uses) < engine.limits.max_hits, 'Selected-type/enum/member hit budget exhausted')
        matches = names[token.value]
        unique = len(matches) == 1
        owner,member = matches[0]
        candidates = sorted(m['qualified_name'] for _,m in matches)
        uncertain = not unique or bool(scope.reasons) or bool(owner['incomplete_reasons']) or annotated_generic or duplicate_types or bool(member_scope_reasons)
        local_reasons = []
        available, availability = False, 'static_on_demand_excludes_nonstatic_member_class'
        qualified = scope.v(i-1) == '.'
        if member_qualified_construction(scope, i, engine):
            available, availability, uncertain = True, 'unresolved_qualified_construction_receiver', True
            local_reasons.append('Qualified inner construction receiver unresolved at line ' + str(token.line))
        elif qualified:
            at = i-2; parts = []
            while IDENT.fullmatch(scope.v(at)) and scope.v(at) not in RESERVED:
                engine.check();need(i-at <= engine.limits.max_context_tokens,'Member qualifier context budget exhausted')
                parts.append(scope.v(at))
                if scope.v(at-1) != '.': break
                at -= 2
            prefix = '.'.join(reversed(parts))
            local_owner = owner['qualified_name'][len(owner['package'])+1:] if owner['package'] else owner['qualified_name']
            expected = prefix in (owner['qualified_name'],local_owner,owner['qualified_name'].rsplit('.',1)[-1])
            available,availability = True,'qualified_member_spelling' if expected else 'unresolved_qualified_receiver'
            if not expected:
                uncertain = True;local_reasons.append('Indirect/unknown member qualifier at line ' + str(token.line))
            elif prefix != owner['qualified_name']:
                first = prefix.split('.')[0]
                top_spelling = first == local_owner.split('.')[0]
                imported_root = (owner['package']+'.'+first if owner['package'] else first) if top_spelling else owner['qualified_name']
                if not ((False,imported_root) in scope.java.imports or top_spelling and (scope.java.package == owner['package'] or
                        (False,owner['package']+'.*') in scope.java.imports) or visible(first,i)):
                    uncertain = True;local_reasons.append('Member qualifier root availability unresolved at line ' + str(token.line))
            first = prefix.split('.')[0] if prefix else ''
            expected_import = owner['qualified_name'] if first == owner['qualified_name'].rsplit('.',1)[-1] else '.'.join(filter(None,[owner['package'],first]))
            if first in shadows or any(not name.endswith('.*') and name.rsplit('.',1)[-1] == first and name != expected_import
                                       for _,name in scope.java.imports):
                uncertain = True;local_reasons.append('Member qualifier root shadow unresolved at line ' + str(token.line))
        else:
            local = visible(token.value,i)
            direct = [name for static,name in scope.java.imports if not static and not name.endswith('.*') and name.rsplit('.',1)[-1] == token.value]
            if local:
                selected = any(not d['top_level'] and d['qualified'] in candidates for d in local)
                available,availability = True,'enclosing_type' if selected else 'lexical_type_declaration'
            elif unique and direct == [member['qualified_name']]:
                available,availability = True,'ordinary_named_import'
            elif direct:
                available,availability,uncertain = True,'competing_named_import',True
                local_reasons.append('Competing ordinary named member import at line ' + str(token.line))
            elif unique and (False,owner['qualified_name']+'.*') in scope.java.imports:
                available,availability = True,'ordinary_owner_on_demand_import'
            elif unique and member['static'] is True:
                available,availability = True,'static_on_demand_import'
            elif member['static'] is None:
                availability,uncertain = 'unresolved_member_staticness',True
            if not local:
                if token.value in shadows:
                    uncertain = True;local_reasons.append('Member type/value/declaration shadow unresolved: ' + token.value)
                type_context = (IDENT.fullmatch(scope.v(i+1)) and scope.v(i+1) not in RESERVED or
                    scope.v(i-1) in ('new','instanceof') or scope.v(i+1)=='[' or scope.v(i+1)=='.' and scope.v(i+2)=='class')
                if not type_context:
                    uncertain = True;local_reasons.append('Member type/value use context unresolved at line ' + str(token.line))
            for static,name in scope.java.imports:
                engine.check()
                if (static and name.endswith('.*') and name != owner['qualified_name']+'.*' or
                    not static and name.endswith('.*') and name != owner['qualified_name']+'.*' or
                    static and not name.endswith('.*') and name.rsplit('.',1)[-1]==token.value):
                    uncertain = True;local_reasons.append('Other/static member import binding unresolved at line ' + str(token.line))
        if uncertain and not local_reasons:
            local_reasons.append('Selected member owner/containing scope unresolved at line ' + str(token.line))
        reasons.extend(local_reasons)
        uses.append(dict(name=token.value,line=token.line,owner=owner['qualified_name'] if unique else None,
            candidates=candidates,static_kind=member['static_kind'] if unique else 'ambiguous_selected_owners',
            availability=availability,ambiguous=uncertain))
        if not available and any(m['static'] is False for _,m in matches):
            hints.append(dict(kind='possible_missing_nonstatic_member_import',name=token.value,line=token.line,
                suggested_import=member['qualified_name'] if unique else None,candidates=candidates,
                reason='static_on_demand_excludes_nonstatic_member_class',ambiguous=uncertain))
    return uses,hints,sorted(set(reasons))


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--git-dir', required=True)
    p.add_argument('--revision', required=True, help='Full lowercase local commit SHA-1; never HEAD/ref')
    p.add_argument('--known-type', action='append', default=[], metavar='FQCN=PATH',
                   help='Explicit ordinary top-level project type and exact immutable Java source')
    p.add_argument('--known-enum', action='append', default=[], metavar='FQCN=PATH',
                   help='Opt-in fully qualified ordinary enum (including nested owners) and exact immutable source')
    p.add_argument('--known-member-owner', action='append', default=[], metavar='FQCN=PATH',
                   help='Opt-in direct member-type import check for one exact immutable owner source')
    p.add_argument('--known-member-owner-final', action='append', default=[], metavar='FQCN=BYTES=SHA256=ABS_PATH',
                   help='Opt-in owner from an independently byte/hash-pinned canonical external FINAL Java source')
    source = p.add_mutually_exclusive_group(required=True)
    source.add_argument('--source', help='Exact relative Java blob at revision')
    source.add_argument('--final', help='Canonical external FINAL Java file')
    p.add_argument('--final-sha256')
    p.add_argument('--final-bytes', type=int)
    for name, value in vars(Limits()).items():
        p.add_argument('--' + name.replace('_', '-'), type=int, default=value,
                       help='Lowerable bound; maximum ' + str(value))
    return p


def run(args):
    limits = Limits(**{k: getattr(args, k) for k in vars(Limits())})
    limits.validate()
    engine = Engine(limits)
    engine.check()
    need(SHA1.fullmatch(args.revision) is not None, 'Require full lowercase immutable commit SHA-1', 2)
    git_dir = absolute(args.git_dir)
    root_id = directory_id(git_dir)
    need(engine.git(git_dir, ['cat-file', '-t', args.revision], min(128, limits.max_file_bytes)).strip() == b'commit',
         'Revision must be an existing local commit', 2)
    type_selections = args.known_type or []
    enum_selections = getattr(args, 'known_enum', []) or []
    member_selections = getattr(args, 'known_member_owner', []) or []
    member_finals = getattr(args, 'known_member_owner_final', []) or []
    member_opt_in = bool(member_selections or member_finals)
    if member_opt_in:
        need(len(type_selections)+len(enum_selections)+len(member_selections)+len(member_finals) <= limits.max_known_types
             and len(set(member_selections))==len(member_selections) and len(set(member_finals))==len(member_finals),
             'Distinct type/enum/member-owner selections must fit the shared bound',2)
    need((member_opt_in or 1 <= len(type_selections) + len(enum_selections)) and len(type_selections)+len(enum_selections) <= limits.max_known_types and
         len(set(type_selections)) == len(type_selections) and len(set(enum_selections)) == len(enum_selections),
         'Select distinct known types/enums within shared bound', 2)
    known, enums, saved, by_path = [], [], [], {}
    for selection in type_selections:
        need(isinstance(selection, str) and len(selection) <= 8192 and selection.count('=') == 1,
             'Known type requires FQCN=exact/path.java', 2)
        fqcn, path = selection.split('=')
        need(all(IDENT.fullmatch(p) and p not in RESERVED for p in fqcn.split('.')), 'Invalid qualified selected type', 2)
        relative(path)
        if path not in by_path:
            data, provenance = engine.blob(git_dir, args.revision, path)
            scope = engine.parse(data)
            by_path[path] = data, provenance, scope
            saved.append((path, data, provenance))
        _, provenance, scope = by_path[path]
        matches = [d for d in scope.declarations if d['top_level'] and d['qualified'] == fqcn]
        need(len(matches) == 1, 'Selected type lacks exact top-level package/declaration provenance: ' + fqcn, 2)
        known.append(dict(qualified_name=fqcn, simple_name=matches[0]['name'], package=scope.java.package,
                          declaration_line=scope.ts[matches[0]['name_at']].line, provenance=provenance))
    need(len({k['qualified_name'] for k in known}) == len(known), 'Duplicate selected qualified type', 2)
    for selection in enum_selections:
        need(isinstance(selection, str) and len(selection) <= 8192 and selection.count('=') == 1,
             'Known enum requires FQCN=exact/path.java', 2)
        qualified, path = selection.split('=')
        need(all(IDENT.fullmatch(part) and part not in RESERVED for part in qualified.split('.')),
             'Invalid qualified selected enum', 2)
        relative(path)
        if path not in by_path:
            data, provenance = engine.blob(git_dir, args.revision, path)
            scope = engine.parse(data)
            by_path[path] = data, provenance, scope
            saved.append((path, data, provenance))
        _, provenance, scope = by_path[path]
        enums.append(known_enum(scope, qualified, provenance, engine))
    need(len({k['qualified_name'] for k in enums}) == len(enums), 'Duplicate selected qualified enum', 2)
    member_owners, owner_final_cache = [], {}
    for selection in member_selections:
        need(isinstance(selection,str) and len(selection)<=8192 and selection.count('=')==1,
             'Known member owner requires FQCN=exact/path.java',2)
        qualified,path = selection.split('=')
        need(all(IDENT.fullmatch(part) and part not in RESERVED for part in qualified.split('.')),
             'Invalid qualified member owner',2)
        relative(path)
        if path not in by_path:
            data,provenance = engine.blob(git_dir,args.revision,path)
            owner_scope = engine.parse(data)
            by_path[path] = data,provenance,owner_scope
            saved.append((path,data,provenance))
        _,provenance,owner_scope = by_path[path]
        member_owners.append(known_member_owner(owner_scope,qualified,provenance,engine))
    for selection in member_finals:
        need(isinstance(selection,str) and len(selection)<=8192 and len(selection.split('=',3))==4,
             'FINAL member owner requires FQCN=BYTES=SHA256=ABS_PATH',2)
        qualified,size_text,digest,path_text = selection.split('=',3)
        need(all(IDENT.fullmatch(part) and part not in RESERVED for part in qualified.split('.')) and
             re.fullmatch(r'0|[1-9][0-9]{0,11}',size_text) and SHA256.fullmatch(digest),
             'Invalid FINAL member owner declaration/pins',2)
        size = int(size_text);need(size<=limits.max_file_bytes,'FINAL member owner exceeds source bound',2)
        path = absolute(path_text)
        need(path.name.endswith('.java'),'FINAL member owner must name exact Java source',2)
        if path not in owner_final_cache:
            engine.account(size);data,signature = read_final(path,limits.max_file_bytes)
            need(len(data)==size and sha(data)==digest,'FINAL member owner byte/SHA256 pin mismatch',2)
            provenance = dict(kind='external_final',path=str(path),bytes=len(data),sha256=sha(data))
            owner_final_cache[path] = data,signature,provenance,engine.parse(data)
        data,_,provenance,owner_scope = owner_final_cache[path]
        need(len(data)==size and sha(data)==digest,'Conflicting FINAL member owner pins',2)
        member_owners.append(known_member_owner(owner_scope,qualified,provenance,engine))
    need(len({item['qualified_name'] for item in member_owners})==len(member_owners),'Duplicate selected member owner',2)
    need(sum(len(item['members']) for item in member_owners)<=limits.max_hits,'Known member inventory budget exhausted')
    final_path = final_sig = None
    if args.final is not None:
        final_path = absolute(args.final)
        need(isinstance(args.final_sha256, str) and SHA256.fullmatch(args.final_sha256) and
             type(args.final_bytes) is int and 0 <= args.final_bytes <= limits.max_file_bytes,
             'FINAL requires externally supplied byte/SHA256 pins within bound', 2)
        if final_path in owner_final_cache:
            data,final_sig,_,scope = owner_final_cache[final_path]
        else:
            engine.account(args.final_bytes)
            data, final_sig = read_final(final_path, limits.max_file_bytes)
        need(len(data) == args.final_bytes and sha(data) == args.final_sha256, 'FINAL byte/SHA256 pin mismatch', 2)
        provenance = dict(kind='external_final', path=str(final_path), bytes=len(data), sha256=sha(data))
        if final_path not in owner_final_cache:
            scope = engine.parse(data)
    else:
        need(args.final_sha256 is None and args.final_bytes is None, 'Orphan FINAL pins', 2)
        path = relative(args.source)
        if path in by_path:
            data, provenance, scope = by_path[path]
        else:
            data, provenance = engine.blob(git_dir, args.revision, path)
            scope = engine.parse(data)
            saved.append((path, data, provenance))
    uses, hints, reasons = analyze(scope, known, engine)
    enum_uses, enum_hints, enum_reasons = analyze_enums(scope, enums, engine, len(uses))
    hints.extend(enum_hints)
    member_uses,member_hints,member_reasons = analyze_members(scope,member_owners,engine,len(uses)+len(enum_uses))
    hints.extend(member_hints)
    reasons = sorted(set(reasons + enum_reasons + member_reasons))
    for path, previous, previous_provenance in saved:
        repeat, repeat_provenance = engine.blob(git_dir, args.revision, path, account=False)
        need(repeat == previous and repeat_provenance == previous_provenance, 'Immutable selected source changed on re-observation')
    for owner_path,(previous,signature,_,_) in owner_final_cache.items():
        engine.check()
        repeat,repeat_signature = read_final(owner_path,limits.max_file_bytes)
        need(repeat==previous and repeat_signature==signature,'FINAL member owner changed on re-observation')
    if final_path is not None:
        repeat, repeat_sig = read_final(final_path, limits.max_file_bytes)
        need(repeat == data and repeat_sig == final_sig, 'FINAL source changed on re-observation')
    need(directory_id(git_dir) == root_id, 'Git directory changed on re-observation')
    engine.check()
    result = dict(version=VERSION, status='incomplete' if reasons else 'observed',
                  source=provenance, known_types=known, known_enums=enums, enum_uses=enum_uses, package=scope.java.package,
                  imports=[dict(static=s, name=n) for s, n in scope.java.imports],
                  uses=uses, hints=hints, incomplete_reasons=reasons, limits=vars(limits),
                  selected_bytes=engine.bytes, selected_tokens=engine.tokens, limitations=LIMITATIONS)
    if member_opt_in:
        result.update(member_check_version=1,known_member_owners=member_owners,member_uses=member_uses)
        result['limitations'] = [*LIMITATIONS,*MEMBER_LIMITATIONS]
    need(len(render(result)) <= limits.max_output_bytes, 'JSON output byte budget exhausted')
    return result


def render(result):
    return (json.dumps(result, ensure_ascii=True, sort_keys=True, indent=2) + '\n').encode()


def main(argv=None):
    try:
        result = run(parser().parse_args(argv))
        code = 3 if result['status'] == 'incomplete' else int(bool(result['hints']))
    except PreflightError as error:
        code = error.code
        result = dict(version=VERSION, status='incomplete' if code == 3 else 'error', error=str(error)[:1024],
                      limitations=LIMITATIONS)
    except (OSError, UnicodeError) as error:
        code = 2
        result = dict(version=VERSION, status='error', error=str(error)[:1024], limitations=LIMITATIONS)
    sys.stdout.buffer.write(render(result))
    return code


if __name__ == '__main__':
    sys.exit(main())
