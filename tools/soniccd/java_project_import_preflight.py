#!/usr/bin/env python3
"""Bounded read-only hints for explicitly selected immutable project types.

Inputs: canonical Git metadata, full commit, explicit qualified type=source pairs,
and one exact Git source or externally byte/hash-pinned FINAL Java file.
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
    'java_declaration_census.py': (43439, '10ec54d1e68c76a29a673634315acea0977d31761f3cfa7846b9f85310ed0e2f'),
    'enum_contract_audit.py': (47279, 'bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d'),
}
LIMITATIONS = [
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
                self.reasons.append('Local type scope unsupported at line ' + str(self.ts[i].line))
            if any(self.v(k) in ('extends', 'implements', 'permits', '<') for k in range(i+2, j)):
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


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--git-dir', required=True)
    p.add_argument('--revision', required=True, help='Full lowercase local commit SHA-1; never HEAD/ref')
    p.add_argument('--known-type', required=True, action='append', metavar='FQCN=PATH',
                   help='Explicit ordinary top-level project type and exact immutable Java source')
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
    need(1 <= len(args.known_type) <= limits.max_known_types and len(set(args.known_type)) == len(args.known_type),
         'Select distinct known types within bound', 2)
    known, saved, by_path = [], [], {}
    for selection in args.known_type:
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
    final_path = final_sig = None
    if args.final is not None:
        final_path = absolute(args.final)
        need(isinstance(args.final_sha256, str) and SHA256.fullmatch(args.final_sha256) and
             type(args.final_bytes) is int and 0 <= args.final_bytes <= limits.max_file_bytes,
             'FINAL requires externally supplied byte/SHA256 pins within bound', 2)
        engine.account(args.final_bytes)
        data, final_sig = read_final(final_path, limits.max_file_bytes)
        need(len(data) == args.final_bytes and sha(data) == args.final_sha256, 'FINAL byte/SHA256 pin mismatch', 2)
        provenance = dict(kind='external_final', path=str(final_path), bytes=len(data), sha256=sha(data))
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
    for path, previous, previous_provenance in saved:
        repeat, repeat_provenance = engine.blob(git_dir, args.revision, path, account=False)
        need(repeat == previous and repeat_provenance == previous_provenance, 'Immutable selected source changed on re-observation')
    if final_path is not None:
        repeat, repeat_sig = read_final(final_path, limits.max_file_bytes)
        need(repeat == data and repeat_sig == final_sig, 'FINAL source changed on re-observation')
    need(directory_id(git_dir) == root_id, 'Git directory changed on re-observation')
    engine.check()
    result = dict(version=VERSION, status='incomplete' if reasons else 'observed',
                  source=provenance, known_types=known, package=scope.java.package,
                  imports=[dict(static=s, name=n) for s, n in scope.java.imports],
                  uses=uses, hints=hints, incomplete_reasons=reasons, limits=vars(limits),
                  selected_bytes=engine.bytes, selected_tokens=engine.tokens, limitations=LIMITATIONS)
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
