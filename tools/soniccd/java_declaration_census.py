#!/usr/bin/env python3
"""Bounded, read-only Java declaration/ordinary JUnit source census; JSON to stdout.

Inputs: canonical Git directory + full SHA-1 commit + exact source path; optional
canonical FINAL .java file with externally supplied SHA256 and byte count.
Origin: 2026-10-26 private java-declaration-census task, J=f4a968965ef55f47ebff8b3c7aa0827c8ef7aad4.
Uses the existing enum_contract_audit.py lexer/pairs, never a compiler or resolver.
Optional test inventory is a static source hint, never JUnit discovery/execution.
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
RESERVED = frozenset(('abstract assert boolean break byte case catch char class const continue default do '
    'double else enum extends final finally float for goto if implements import instanceof int interface '
    'long native new package private protected public return short static strictfp super switch synchronized '
    'this throw throws transient try void volatile while true false null _').split())
PRIMITIVES = frozenset('boolean byte char double float int long short'.split())
MODIFIERS = frozenset(('public protected private abstract static final sealed strictfp native '
                       'synchronized transient volatile default').split())
DEPENDENCY_BYTES = 47279
DEPENDENCY_SHA256 = 'bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d'
LIMITATIONS = [
    'Convenience source evidence only: no compiler/ABI compatibility, owner/phase/authentication, test pass, overload-call resolution or constant evaluation.',
    'One explicitly selected Git .java blob and at most one explicitly pinned external FINAL file; no discovery, package/destination inference or migration application.',
    'ASCII declaration identifiers, UTF-8 source, ordinary named classes/interfaces/enums/records and member types only. Annotation types, receivers, local/anonymous types, Unicode preprocessing and unrecognized declaration grammar are incomplete.',
    'Fields, enum constants, initializer blocks and executable bodies are not inventoried or grammar-validated; opaque regions are checked for unsupported type scopes. Inheritance, generated declarations, implicit modifiers and implicit constructors/accessors are not inferred.',
    'Signature/type strings are space-joined lexical tokens, not resolved or erased Java types. Lines identify declaration names. Compact constructors have arity null and no written parameters; record_component_count is context only.',
    'The shared lexer redacts string/character/text-block contents as <literal>. Equal token headers containing literals cannot prove equal declarations across different file bytes: diff returns incomplete rather than a false equality.',
    'Diff ignores line/body changes; exact lexical parameter types match first, then unique residual owner/kind/name pairs. Ambiguous overload groups remain added/removed without guessing identity.',
    'Selected inputs and path identity are observed twice, non-atomically. Changes restored between observations can escape detection. Unselected files, Git index/HEAD and unrelated metadata are not inventoried.',
    'Requires trusted local Git metadata and the hash-pinned existing sibling lexer tool. Read-only Git commands disable replacement objects, lazy fetch and protocols; no supplied Java code is executed.',
]


class CensusError(ValueError):
    def __init__(self, message, code=3):
        super().__init__(message)
        self.code = code


def require(condition, message, code=3):
    if not condition:
        raise CensusError(message, code)


@dataclass(frozen=True)
class Limits:
    max_file_bytes: int = 1048576
    max_total_bytes: int = 2097152
    max_tokens: int = 100000
    max_declarations: int = 5000
    max_context_tokens: int = 4096
    max_depth: int = 32

    def validate(self):
        for name, ceiling in vars(Limits()).items():
            value = getattr(self, name)
            require(type(value) is int and 1 <= value <= ceiling,
                    f'{name} must be 1..{ceiling}', 2)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def relative(value):
    require(isinstance(value, str) and 0 < len(value) <= 4096 and
            not any(ord(c) < 32 or ord(c) == 127 for c in value) and '\\' not in value and ':' not in value,
            'Require canonical relative POSIX source path', 2)
    require(all(p and p not in ('.', '..') and p.lower() != '.git' for p in value.split('/')),
            'Unsafe relative source path', 2)
    require(value.endswith('.java'), 'Select an explicit .java source file', 2)
    return value


def absolute(value):
    require(isinstance(value, str) and 0 < len(value) <= 4096 and value.startswith('/') and
            not any(ord(c) < 32 or ord(c) == 127 for c in value) and '\\' not in value and ':' not in value,
            'Require canonical absolute POSIX path', 2)
    require(all(p and p not in ('.', '..') for p in value.split('/')[1:]),
            'Path aliases/traversal are unsupported', 2)
    return Path(value)


@contextmanager
def opened(value, directory=False):
    """No-follow descriptor walk from /, protecting every path component."""
    path = absolute(str(value))
    require(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW') and hasattr(os, 'O_DIRECTORY'),
            'POSIX no-follow directory descriptors required', 2)
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
            require(stat.S_ISDIR(info.st_mode) if is_dir else stat.S_ISREG(info.st_mode),
                    'Require regular file/directory without symlinks', 2)
        yield fd
    except OSError as error:
        raise CensusError(f'Cannot safely open {path}: {error.strerror}', 2) from error
    finally:
        for fd in reversed(fds):
            os.close(fd)


def identity(info):
    return info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def directory_id(path):
    with opened(path, directory=True) as fd:
        info = os.fstat(fd)
        return info.st_dev, info.st_ino, info.st_mode


def read_regular(path, cap, no_alias=False):
    with opened(path) as fd:
        before = os.fstat(fd)
        require(not no_alias or before.st_nlink == 1, 'Hardlink alias FINAL input unsupported', 2)
        require(before.st_size <= cap, 'File byte budget exceeded before read')
        data = bytearray()
        while len(data) <= before.st_size:
            block = os.read(fd, min(65536, before.st_size + 1 - len(data)))
            if not block:
                break
            data.extend(block)
        after = os.fstat(fd)
        require(identity(before) == identity(after) and len(data) == before.st_size,
                'Source changed during read')
        return bytes(data), identity(before)


def read_final(path, cap):
    require(str(path).endswith('.java') and '.git' not in (p.lower() for p in path.parts),
            'FINAL must be an explicit .java file outside Git metadata', 2)
    return read_regular(path, cap, no_alias=True)


_LEXER = None


def lexer():
    global _LEXER
    if _LEXER is None:
        path = Path(__file__).absolute().parent / 'enum_contract_audit.py'
        data, _ = read_regular(path, DEPENDENCY_BYTES)
        require(len(data) == DEPENDENCY_BYTES and sha(data) == DEPENDENCY_SHA256,
                'Existing sibling enum lexer does not match the documented dependency pin', 2)
        module = types.ModuleType('_census_enum_lexer')
        module.__file__ = str(path)
        sys.modules[module.__name__] = module
        # Execute only this pinned trusted tool, never any supplied Java input.
        exec(compile(data, str(path), 'exec'), module.__dict__)
        _LEXER = module
    return _LEXER


def git_read(git_dir, args, cap):
    """Fixed read-only argv shapes, bounded pipes/time, no shell or inherited Git env."""
    allowed = ((len(args) == 3 and args[:2] in (['cat-file', '-t'], ['cat-file', '-s'],
                                                                ['cat-file', 'blob']) and SHA1.fullmatch(args[2])))
    # ls-tree has exactly five arguments; source is never a revision/pathspec option.
    if len(args) == 5 and args[:2] == ['ls-tree', '-z'] and SHA1.fullmatch(args[2]) and args[3] == '--':
        relative(args[4])
        allowed = True
    require(allowed and type(cap) is int and 0 <= cap <= Limits().max_file_bytes,
            'Unsupported Git argv shape/output cap', 2)
    env = dict(PATH=os.defpath, HOME='/nonexistent', XDG_CONFIG_HOME='/nonexistent',
               GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_SYSTEM=os.devnull, GIT_CONFIG_GLOBAL=os.devnull,
               GIT_TERMINAL_PROMPT='0', GIT_NO_REPLACE_OBJECTS='1', GIT_NO_LAZY_FETCH='1',
               GIT_OPTIONAL_LOCKS='0', GIT_ALLOW_PROTOCOL='', LC_ALL='C')
    argv = ['git', '--no-replace-objects', '--no-optional-locks', '--literal-pathspecs',
            '--git-dir=' + str(git_dir), '-c', 'core.fsmonitor=false', '-c', 'protocol.allow=never',
            '-c', 'core.hooksPath=/dev/null', *args]
    try:
        proc = subprocess.Popen(argv, cwd='/', env=env, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=False)
    except OSError as error:
        raise CensusError(f'Cannot start local Git: {error.strerror}', 2) from error
    output = {'out': bytearray(), 'err': bytearray()}
    deadline = time.monotonic() + 10
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout, selectors.EVENT_READ, ('out', cap))
            selector.register(proc.stderr, selectors.EVENT_READ, ('err', 4096))
            while selector.get_map():
                remaining = deadline - time.monotonic()
                require(remaining > 0, 'Git read timed out')
                for key, _ in selector.select(min(remaining, 0.25)):
                    label, limit = key.data
                    block = os.read(key.fd, min(65536, limit + 1 - len(output[label])))
                    if not block:
                        selector.unregister(key.fileobj)
                    else:
                        output[label].extend(block)
                        require(len(output[label]) <= limit, 'Git output byte budget exceeded')
        try:
            status = proc.wait(timeout=max(0.01, deadline - time.monotonic()))
        except subprocess.TimeoutExpired as error:
            raise CensusError('Git read timed out') from error
        require(status == 0, 'Git read failed: ' + output['err'].decode('utf-8', 'replace').strip(), 2)
        return bytes(output['out'])
    finally:
        if proc.poll() is None:
            proc.kill()
        proc.wait()
        proc.stdout.close()
        proc.stderr.close()


def read_baseline(git_dir, revision, source, cap):
    require(git_read(git_dir, ['cat-file', '-t', revision], 128).strip() == b'commit',
            'Revision must identify an existing local commit', 2)
    entry = git_read(git_dir, ['ls-tree', '-z', revision, '--', source], 8192)
    require(entry.endswith(b'\0') and entry.count(b'\0') == 1 and b'\t' in entry,
            'Source is absent/ambiguous at immutable revision', 2)
    metadata, name = entry[:-1].split(b'\t', 1)
    parts = metadata.decode('ascii').split()
    require(len(parts) == 3 and parts[0] in ('100644', '100755') and parts[1] == 'blob' and
            SHA1.fullmatch(parts[2]) and name == source.encode('utf-8'),
            'Baseline must be an exact regular Git blob, not a tree/symlink', 2)
    oid = parts[2]
    size_text = git_read(git_dir, ['cat-file', '-s', oid], 128).strip()
    require(size_text.isdigit() and len(size_text) <= 12, 'Invalid Git blob size')
    size = int(size_text)
    require(size <= cap, 'Baseline byte budget exceeded before read')
    data = git_read(git_dir, ['cat-file', 'blob', oid], size)
    require(len(data) == size and hashlib.sha1(b'blob ' + str(size).encode() + b'\0' + data).hexdigest() == oid,
            'Baseline blob size/hash changed or mismatched')
    return data, dict(git_dir=str(git_dir), revision=revision, path=source,
                      blob_sha1=oid, sha256=sha(data), bytes=size)


class Declarations:
    def __init__(self, java, limits):
        self.ts, self.pairs, self.package = java.tokens, java.pairs, java.package
        self.limits, self.result, self.seen = limits, [], set()
        self.parameter_spans = {}
        self.spans = []
        self.type_names = set()

    def v(self, i):
        return self.ts[i].value if i < len(self.ts) else '<EOF>'

    def ident(self, i):
        return bool(IDENT.fullmatch(self.v(i))) and self.v(i) not in RESERVED

    def need(self, ok, i, detail):
        require(ok, f'{detail} at line {self.ts[i].line if i < len(self.ts) else "EOF"}')

    def context(self, start, end):
        require(end - start <= self.limits.max_context_tokens, 'Declaration context budget exhausted')

    def text(self, start, end):
        self.context(start, end)
        return ' '.join(t.value for t in self.ts[start:end])

    def annotation(self, i):
        start = i
        self.need(self.v(i) == '@' and self.ident(i+1) and self.v(i+1) != 'interface', i,
                  'Unsupported annotation/type declaration')
        i += 2
        while self.v(i) == '.' and self.v(i+1) != '.':
            self.need(self.ident(i+1), i, 'Invalid annotation name')
            i += 2
        if self.v(i) == '(':
            i = self.pairs[i] + 1
        self.context(start, i)
        return i

    def prefix(self, i, parameter=False):
        start, modifiers = i, []
        while True:
            if self.v(i) == '@':
                i = self.annotation(i)
            elif self.v(i) in ({'final'} if parameter else MODIFIERS):
                self.need(self.v(i) not in modifiers, i, 'Duplicate modifier')
                modifiers.append(self.v(i)); i += 1
            elif not parameter and [self.v(i+k) for k in range(3)] == ['non','-','sealed']:
                self.need('non-sealed' not in modifiers, i, 'Duplicate modifier')
                modifiers.append('non-sealed'); i += 3
            else:
                break
        self.context(start, i)
        return i, modifiers

    def angles(self, i, parameters=False, depth=0):
        require(depth < self.limits.max_depth, 'Generic nesting budget exhausted')
        start = i
        self.need(self.v(i) == '<', i, 'Expected generic list')
        i += 1
        while True:
            while self.v(i) == '@':
                i = self.annotation(i)
            if parameters:
                self.need(self.ident(i), i, 'Unsupported type parameter')
                i += 1
                if self.v(i) == 'extends':
                    i = self.type(i+1, depth=depth+1)
                    while self.v(i) == '&':
                        i = self.type(i+1, depth=depth+1)
            elif self.v(i) == '?':
                i += 1
                if self.v(i) in ('extends', 'super'):
                    i = self.type(i+1, depth=depth+1)
            else:
                i = self.type(i, depth=depth+1)
            self.context(start, i)
            if self.v(i) != ',':
                break
            i += 1
        self.need(self.v(i) == '>', i, 'Unrecognized generic grammar')
        return i + 1

    def dimensions(self, i):
        while True:
            start = i
            while self.v(i) == '@':
                i = self.annotation(i)
            if self.v(i) == '[' and self.v(i+1) == ']':
                i += 2
            else:
                return start

    def type(self, i, allow_void=False, depth=0):
        start = i
        while self.v(i) == '@':
            i = self.annotation(i)
        primitive = self.v(i) in PRIMITIVES or (allow_void and self.v(i) == 'void')
        self.need(primitive or self.ident(i), i, 'Unsupported type grammar')
        i += 1
        if not primitive:
            if self.v(i) == '<':
                i = self.angles(i, depth=depth)
            while self.v(i) == '.' and self.v(i+1) != '.':
                i += 1
                while self.v(i) == '@':
                    i = self.annotation(i)
                self.need(self.ident(i), i, 'Invalid qualified type')
                i += 1
                if self.v(i) == '<':
                    i = self.angles(i, depth=depth)
        i = self.dimensions(i)
        self.context(start, i)
        return i

    def parameters(self, opening, components=False):
        closing = self.pairs[opening]
        self.context(opening, closing+1)
        result, spans, i = [], [], opening + 1
        while i < closing:
            begin = i
            i, mods = self.prefix(i, parameter=True)
            self.need(not components or not mods, i, 'Record component modifiers unsupported')
            type_begin = i
            i = self.type(i)
            # Annotations immediately before ... are part of the written type.
            while self.v(i) == '@':
                i = self.annotation(i)
            varargs = [self.v(i+k) for k in range(3)] == ['.','.','.']
            if varargs:
                i += 3
            type_end = i
            self.need(self.ident(i), i, 'Unsupported parameter (including receivers)')
            name_at = i
            name, line = self.v(i), self.ts[i].line
            i += 1
            dims_start = i
            i = self.dimensions(i)
            self.need(not varargs or i == dims_start, i, 'Varargs trailing arrays unsupported')
            type_text = self.text(type_begin, type_end)
            if i > dims_start:
                type_text += ' ' + self.text(dims_start, i)
            result.append(dict(name=name, type=type_text, varargs=varargs, modifiers=mods,
                               signature=self.text(begin, i), line=line))
            spans.append((begin, i, name_at))
            self.need(self.v(i) in (',', ')') and (not varargs or i == closing), i,
                      'Unrecognized parameter grammar or non-final varargs')
            if i < closing:
                i += 1
                self.need(i < closing, i, 'Trailing parameter comma')
        self.need(len({p['name'] for p in result}) == len(result), opening, 'Duplicate parameter/component name')
        self.parameter_spans[opening] = spans
        return result, closing + 1

    def add(self, owner, name_at, kind, modifiers, start, end, parameters=None, type_text=None, **extra):
        require(len(self.result) < self.limits.max_declarations, 'Declaration budget exhausted')
        parameters = parameters or []
        key = (owner, kind, self.v(name_at), tuple(p['type'] for p in parameters))
        self.need(key not in self.seen, name_at, 'Ambiguous duplicate source declaration')
        self.seen.add(key)
        row = dict(owner=owner, name=self.v(name_at), kind=kind, modifiers=modifiers,
                   arity=len(parameters) if kind in ('method', 'constructor') else None,
                   parameters=parameters, type=type_text, line=self.ts[name_at].line,
                   signature=self.text(start, end), **extra)
        self.result.append(row)
        self.spans.append((start, end))

    def opaque(self, start, end):
        """Reject declaration scopes we cannot inventory; do not validate statements."""
        i = start
        while i < end:
            v = self.v(i)
            if v in ('class', 'interface', 'enum') and (i == start or self.v(i-1) != '.'):
                self.need(False, i, 'Local/anonymous/unsupported type scope')
            if v == 'record' and self.ident(i+1) and self.v(i+2) in ('(', '<'):
                self.need(False, i, 'Local record scope unsupported')
            if v == 'new':
                j = i+1
                while j < end and self.v(j) not in ('(', ';', '{', '}', '=', ':'):
                    self.context(i,j)
                    if self.v(j) == '[':
                        break
                    if self.v(j) == '@':
                        j = self.annotation(j)
                    else:
                        j += 1
                if self.v(j) == '(' and self.v(self.pairs[j]+1) == '{':
                    self.need(False, i, 'Anonymous class scope unsupported')
            i += 1

    def body_or_semi(self, i):
        self.need(self.v(i) in ('{',';'), i, 'Expected body or semicolon')
        if self.v(i) == ';':
            return i+1
        end = self.pairs[i]
        self.opaque(i+1, end)
        return end+1

    def callable(self, owner, owner_name, kind, name_at, start, i, mods, type_text=None):
        parameters, i = self.parameters(i)
        if kind == 'method':
            dims = i
            i = self.dimensions(i)
            if dims != i:
                type_text += ' ' + self.text(dims, i)
        if self.v(i) == 'throws':
            i = self.type(i+1)
            while self.v(i) == ',':
                i = self.type(i+1)
        self.add(owner, name_at, kind, mods, start, i, parameters, type_text)
        return self.body_or_semi(i)

    def enum_constants(self, i, end):
        start = i
        if self.v(i) in (';', '}'):
            return i+1 if self.v(i) == ';' else i
        while i < end:
            while self.v(i) == '@':
                i = self.annotation(i)
            self.need(self.ident(i), i, 'Unsupported enum constant')
            i += 1
            if self.v(i) == '(':
                close = self.pairs[i]
                self.context(i,close+1)
                self.opaque(i+1,close)
                i = close+1
            self.need(self.v(i) != '{', i, 'Enum constant class body unsupported')
            self.need(self.v(i) in (',',';','}'), i, 'Unrecognized enum constant tail')
            if self.v(i) == ',':
                i += 1
                if self.v(i) not in (';', '}'):
                    continue
            return i+1 if self.v(i) == ';' else i
        self.need(False,start,'Unterminated enum constants')

    def named_type(self, i, owner, start, mods, depth):
        require(depth <= self.limits.max_depth, 'Member type nesting budget exhausted')
        kind = self.v(i)
        name_at = i+1
        self.need(self.ident(name_at), name_at, 'Expected type name')
        full = '.'.join(x for x in (owner, self.v(name_at)) if x)
        self.need(full not in self.type_names, name_at, 'Ambiguous duplicate type name')
        self.type_names.add(full)
        i += 2
        if self.v(i) == '<':
            self.need(kind != 'enum', i, 'Enum type parameters unsupported')
            i = self.angles(i, parameters=True)
        components, component_open = [], None
        if kind == 'record':
            self.need(self.v(i) == '(', i, 'Expected record components')
            component_open = i
            components, i = self.parameters(i, components=True)
        if self.v(i) == 'extends':
            self.need(kind in ('class','interface'), i, 'Unsupported extends clause')
            i = self.type(i+1)
            while kind == 'interface' and self.v(i) == ',':
                i = self.type(i+1)
        if self.v(i) == 'implements':
            self.need(kind != 'interface', i, 'Unsupported implements clause')
            i = self.type(i+1)
            while self.v(i) == ',':
                i = self.type(i+1)
        if self.v(i) == 'permits':
            self.need(kind in ('class','interface'), i, 'Unsupported permits clause')
            i = self.type(i+1)
            while self.v(i) == ',':
                i = self.type(i+1)
        self.need(self.v(i) == '{', i, 'Unrecognized type header')
        self.add(owner, name_at, kind, mods, start, i,
                 record_component_count=len(components) if kind == 'record' else None)
        if components:
            # Component source spans are already normalized and bounded by parameters().
            for p, (begin, stop, name_at_component) in zip(components, self.parameter_spans[component_open]):
                self.add(full, name_at_component, 'record_component', [], begin, stop, type_text=p['type'])
        end = self.pairs[i]
        i += 1
        if kind == 'enum':
            i = self.enum_constants(i,end)
        while i < end:
            if self.v(i) == ';':
                i += 1
                continue
            begin = i
            i, member_mods = self.prefix(i)
            v = self.v(i)
            if v in ('class','interface','enum','record') and self.ident(i+1):
                i = self.named_type(i,full,begin,member_mods,depth+1)
                continue
            if v == '{':
                self.need(member_mods in ([],['static']) and i-begin == len(member_mods), i,
                          'Unsupported initializer modifiers')
                i = self.body_or_semi(i)
                continue
            if v == '<':
                i = self.angles(i, parameters=True)
            if self.v(i) == self.v(name_at) and self.v(i+1) == '(':
                i = self.callable(full,self.v(name_at),'constructor',i,begin,i+1,member_mods)
                continue
            if kind == 'record' and self.v(i) == self.v(name_at) and self.v(i+1) == '{':
                self.add(full,i,'compact_constructor',member_mods,begin,i+1,
                         record_component_count=len(components))
                i = self.body_or_semi(i+1)
                continue
            type_begin = i
            i = self.type(i, allow_void=True)
            type_text = self.text(type_begin,i)
            self.need(self.ident(i), i, 'Expected member name')
            member_at = i
            i += 1
            if self.v(i) == '(':
                i = self.callable(full,self.v(name_at),'method',member_at,begin,i,member_mods,type_text)
                continue
            # Fields are opaque after a recognized type/name, never declaration evidence.
            i = self.dimensions(i)
            self.need(self.v(i) in ('=',',',';'), i, 'Unsupported field/member grammar')
            if self.v(i) != ';':
                expression_start = i
                while i < end and self.v(i) != ';':
                    if self.v(i) in ('(', '[', '{'):
                        i = self.pairs[i]+1
                    else:
                        i += 1
                self.opaque(expression_start,i)
            self.need(self.v(i) == ';', i, 'Unterminated field')
            i += 1
        self.need(i == end, i, 'Member scope overrun')
        return end+1

    def parse(self):
        i, count = 0, 0
        for keyword in ('package','import'):
            while self.v(i) == keyword:
                self.need(keyword != 'package' or count == 0, i, 'Duplicate package')
                start = i
                i += 1
                while self.v(i) not in (';', '<EOF>'):
                    i += 1
                    self.context(start,i)
                self.need(self.v(i) == ';',i,'Unterminated package/import')
                i += 1
                count += keyword == 'package'
        while i < len(self.ts):
            if self.v(i) == ';':
                i += 1
                continue
            start = i
            i, mods = self.prefix(i)
            self.need(self.v(i) in ('class','interface','enum','record'),i,'Unsupported top-level declaration')
            i = self.named_type(i,self.package,start,mods,1)
        require(self.result, 'No supported named type declaration observed')
        return self.result


def source_declarations(data, limits):
    limits.validate()
    require(len(data) <= limits.max_file_bytes, 'Source byte budget exceeded')
    dep = lexer()
    try:
        java = dep.lex(data, limits)
    except dep.AuditError as error:
        raise CensusError(str(error)) from error
    declarations = Declarations(java, limits)
    declarations.parse()
    return declarations, java


def inventory(data, limits):
    return source_declarations(data, limits)[0].result


JUNIT_SIMPLE = frozenset(('Test', 'BeforeAll', 'AfterAll', 'BeforeEach', 'AfterEach'))
JUNIT_PREFIX = 'org.junit.jupiter.api.'
TEST_LIMITATIONS = [
    'Static ordinary @Test candidate methods only, not JUnit discovery, invocations, execution, XML evidence or a pass/skip guarantee. No Java is executed.',
    'A proposed count requires one ordinary top-level class, no written inheritance/member types/generics, and only directly imported or fully qualified, argument-free literal JUnit Test/lifecycle annotations. Unknown, composed, disabled, conditional, extension, parameterized, repeated, dynamic and nested forms are incomplete.',
    'External JUnit configuration, filters, automatic extensions, classpath, compilation, runtime assumptions and lifecycle failures are unobserved. Candidate names cannot authenticate an integration_run expected count or relax its strict executed-XML checks.',
    'Expected count checks FINAL when provided, otherwise baseline; it is compared only to a complete static proposal. No count is guessed for incomplete input.',
]


def test_inventory(data, limits, selected_class):
    """Recognize only a bounded literal ordinary-JUnit subset; never discover tests."""
    require(isinstance(selected_class, str) and len(selected_class) <= 4096 and
            all(IDENT.fullmatch(p) and p not in RESERVED for p in selected_class.split('.')),
            'Require an exact qualified Java class name for test inventory', 2)
    d, java = source_declarations(data, limits)
    reasons, candidates, annotations, covered = [], [], {}, set()
    reason_seen = set()

    def incomplete(message):
        if message not in reason_seen:
            reason_seen.add(message)
            reasons.append(message)

    bindings = {}
    for static, name in java.imports:
        if not static and name.endswith('.*'):
            incomplete('Wildcard type imports leave annotation binding unsupported')
        bindings.setdefault(name.rsplit('.', 1)[-1], set()).add((static, name))
    # A qualified spelling still starts with an in-scope type-or-package name.
    # Imported/member types named org can shadow the JUnit package root; unknown
    # static on-demand imports may contribute that type too. Do not resolve them.
    org_shadowed = ('org' in bindings or
        any(r['name'] == 'org' and r['kind'] in ('class', 'interface', 'enum', 'record') for r in d.result) or
        any(static and name.endswith('.*') and name != 'org.junit.jupiter.api.Assertions.*'
            for static, name in java.imports))
    for i, token in enumerate(d.ts):
        if token.value != '@':
            continue
        end = d.annotation(i)
        j = i + 2
        while d.v(j) == '.':
            j += 2
        name = ''.join(t.value for t in d.ts[i+1:j])
        resolved = name if '.' in name else None
        if resolved and resolved.startswith('org.') and org_shadowed:
            resolved = None
        if resolved is None and name in JUNIT_SIMPLE and bindings.get(name) == {(False, JUNIT_PREFIX + name)}:
            resolved = JUNIT_PREFIX + name
        simple = resolved[len(JUNIT_PREFIX):] if resolved and resolved.startswith(JUNIT_PREFIX) else None
        trusted = simple if simple in JUNIT_SIMPLE else None
        literal = j == end or (d.v(j) == '(' and end == j + 2)
        annotations[i] = (trusted, literal)
        if trusted is None:
            incomplete(f'Unknown/unsupported annotation {name} at line {token.line}')
        elif not literal:
            incomplete(f'Annotation arguments are unsupported at line {token.line}')

    types = [r for r in d.result if r['kind'] in ('class', 'interface', 'enum', 'record')]
    selected = [r for r in types if '.'.join(p for p in (r['owner'], r['name']) if p) == selected_class]
    if len(types) != 1 or len(selected) != 1 or selected[0]['owner'] != java.package:
        incomplete('Require exactly one selected top-level class and no additional/member types')
    for row, (start, end) in zip(d.result, d.spans):
        prefix_end, _ = d.prefix(start)
        positions = [i for i in range(start, prefix_end) if i in annotations]
        covered.update(positions)
        tags = [annotations[i][0] for i in positions if annotations[i][0] and annotations[i][1]]
        if row['kind'] in ('class', 'interface', 'enum', 'record'):
            header = [t.value for t in d.ts[prefix_end:end]]
            if (row['kind'] != 'class' or set(row['modifiers']) - {'public', 'final', 'strictfp'} or
                    any(v in header for v in ('<', 'extends', 'implements', 'permits'))):
                incomplete('Nonordinary class header/inheritance is unsupported')
        if row['kind'] == 'constructor' and (row['arity'] != 0 or 'private' in row['modifiers']):
            incomplete('Only an implicit or one nonprivate zero-argument constructor is supported')
        if not tags:
            continue
        ordinary = row['kind'] == 'method' and row['owner'] == selected_class
        if ordinary and 'Test' in tags:
            candidates.append(dict(owner=row['owner'], name=row['name'], line=row['line']))
        if not ordinary or len(tags) != 1:
            incomplete(f'Unsupported annotation placement/combination at line {row["line"]}')
            continue
        tag = tags[0]
        static_required = tag in ('BeforeAll', 'AfterAll')
        if (row['arity'] != 0 or row['type'] != 'void' or d.v(prefix_end) == '<' or
                d.v(end) != '{' or set(row['modifiers']) & {'private', 'abstract', 'native'} or
                ('static' in row['modifiers']) != static_required):
            incomplete(f'Nonordinary {tag} method shape at line {row["line"]}')
    if len([r for r in d.result if r['kind'] == 'constructor']) > 1:
        incomplete('Multiple written constructors are unsupported')
    if set(annotations) - covered:
        incomplete('Annotations outside supported declaration prefixes are unsupported')
    if not candidates:
        incomplete('No ordinary literal @Test method candidates observed in selected class')
    return dict(complete=not reasons, selected_class=selected_class,
                candidate_methods=candidates, proposed_count=None if reasons else len(candidates),
                incomplete_reasons=reasons)


def semantic(row):
    return {k: ([{pk: pv for pk, pv in p.items() if pk != 'line'} for p in v]
                if k == 'parameters' else v) for k,v in row.items() if k != 'line'}


def compare(before, after):
    groups = {}
    for side, declarations in enumerate((before,after)):
        for row in declarations:
            key = row['owner'], row['kind'], row['name']
            groups.setdefault(key,[[],[]])[side].append(row)
    added, removed, changed, ambiguous, literal_ambiguities = [], [], [], [], []
    for key, (left,right) in sorted(groups.items()):
        remaining = list(right)
        residual = []
        for row in left:
            params = tuple(p['type'] for p in row['parameters'])
            matches = [r for r in remaining if tuple(p['type'] for p in r['parameters']) == params]
            if len(matches) == 1:
                match = matches[0]
                remaining.remove(match)
                if semantic(row) != semantic(match):
                    changed.append(dict(before=row,after=match,match='same_parameter_types'))
                elif '<literal>' in row['signature']:
                    literal_ambiguities.append(dict(owner=key[0],kind=key[1],name=key[2]))
            else:
                residual.append(row)
        if len(residual) == len(remaining) == 1:
            changed.append(dict(before=residual.pop(), after=remaining.pop(),match='unique_residual_name_kind'))
        elif residual and remaining:
            ambiguous.append(dict(owner=key[0],kind=key[1],name=key[2],
                                  before_count=len(residual),after_count=len(remaining)))
        removed.extend(residual)
        added.extend(remaining)
    return dict(complete=not literal_ambiguities,has_changes=bool(added or removed or changed),
                added=added,removed=removed,changed=changed,ambiguous_groups=ambiguous,
                literal_ambiguities=literal_ambiguities)



# BEGIN opt-in literal public method inventory 20261107.
PUBLIC_INVENTORY_LIMITATIONS = [
    'Opt-in source comparison only: caller-known owner/source mapping is supplied, not independently resolved Java. No compiler, reflection execution, ABI, bridge or inherited-method certificate.',
    'Only expected-first literal List.of name/decimal-arity strings and the direct Arrays.stream(Owner.class.getDeclaredMethods()).filter(public).map(name+"/"+parameterCount).sorted().toList() shape are recognized. Natural ASCII Java String order and duplicates are preserved; no alternate comparator, alias, count inference or test editing.',
    'Unsupported owner/consumer inheritance, generics, records, binding/shadow clues, wildcard ambiguity and other selected reflection shapes remain incomplete. Written-source mismatches can remain visible as conditional observations; matches is null when incomplete.',
    'Owner FINAL and test FINAL are selected independently, without union/fallback. All four potential inputs share the existing total-byte bound and are rechecked. Additional contexts, rows and output use the existing bounds. No new cache, gate or output file.',
]


def _public_literal_payloads(data, java, limits):
    """Recover only simple ASCII literal payloads AFTER the pinned lexer validated/redacted.

    Delimiter scanning mirrors the approved lexer; never interprets Java escapes or
    executes an expression. Text blocks/characters/escaped strings stay unsupported.
    Every raw literal is aligned to its existing redacted token, including unrelated ones.
    """
    text = data.decode('utf-8').replace('\r\n', '\n').replace('\r', '\n')
    indices = iter(i for i, t in enumerate(java.tokens) if t.value == '<literal>')
    result, pos = {}, 0
    while pos < len(text):
        if text.startswith('//', pos):
            end = text.find('\n', pos+2)
            pos = len(text) if end < 0 else end
        elif text.startswith('/*', pos):
            pos = text.find('*/', pos+2)+2  # Already validated by the pinned lexer.
        elif text[pos] in ('"', "'"):
            marker = '"""' if text.startswith('"""', pos) else text[pos]
            end = pos+len(marker)
            while end < len(text):
                if text[end] == '\\':
                    end += 2
                elif text.startswith(marker, end):
                    end += len(marker)
                    break
                else:
                    end += 1
            token = next(indices, None)
            require(token is not None, 'Literal/token alignment incomplete')
            # No copy of a large unrelated text block or arbitrary string is needed.
            value = None
            if marker == '"' and end-pos-2 <= limits.max_context_tokens:
                payload = text[pos+1:end-1]
                if payload == '/' or re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*/[0-9]+', payload):
                    value = payload
            result[token] = value
            pos = end
        else:
            pos += 1
    require(next(indices, None) is None, 'Literal/token alignment incomplete')
    return result


def public_method_inventory(owner_data, test_data, limits, selected_owner):
    """Bounded exact written public name/arity vs one declared literal reflection shape."""
    limits.validate()
    require(isinstance(selected_owner, str) and 0 < len(selected_owner) <= 4096 and
            all(IDENT.fullmatch(x) and x not in RESERVED for x in selected_owner.split('.')),
            'Public inventory requires exact qualified owner', 2)
    require(len(owner_data)+len(test_data) <= limits.max_total_bytes, 'Public inventory combined byte budget exceeded')
    od, oj = source_declarations(owner_data, limits)
    td, tj = source_declarations(test_data, limits)
    reasons, checks, findings = [], [], []
    output_rows, output_chars = 0, len(selected_owner)+2048

    def reason(message):
        nonlocal output_rows, output_chars
        if message not in reasons:
            require(output_rows < limits.max_declarations and output_chars+len(message)+16 <= limits.max_total_bytes,
                    'Public inventory output/reason budget exhausted')
            output_rows += 1; output_chars += len(message)+16
            reasons.append(message)

    types = {'.'.join(filter(None, (r['owner'], r['name']))): (r, span)
             for r, span in zip(od.result, od.spans) if r['kind'] in ('class','record','interface','enum')}
    selected = types.get(selected_owner)
    if selected is None:
        reason('Selected owner is not an observed named declaration')
    else:
        row, (start, end) = selected
        if row['kind'] != 'class':
            reason('Selected owner is not an ordinary class; implicit/generated members unresolved')
        if any(t.value in ('<','extends','implements','permits') for t in oj.tokens[start:end]):
            reason('Owner generic/inherited/bridge scopes unresolved')
    for name, (ancestor, (start, end)) in types.items():
        if selected_owner.startswith(name+'.') and any(t.value in ('<','extends','implements','permits') for t in oj.tokens[start:end]):
            reason('Enclosing owner generic/inherited scope unresolved')
    observed = []
    for row, (start, end) in zip(od.result, od.spans):
        if row['owner'] == selected_owner and row['kind'] == 'method' and 'public' in row['modifiers']:
            if any(t.value == '<' for t in oj.tokens[start:end]):
                reason('Public generic signature/bridge scope unresolved at line '+str(row['line']))
            require(len(row['name']) <= limits.max_context_tokens, 'Public method name context budget exceeded')
            require(output_rows < limits.max_declarations and output_chars+len(row['name'])+32 <= limits.max_total_bytes,
                    'Public inventory output budget exhausted')
            observed.append(row['name']+'/'+str(row['arity']))
            output_rows += 1; output_chars += len(row['name'])+32
    # Only ASCII names and decimal arities are admitted; Python and Java natural
    # String sorting agree for this subset. No set/deduplication or numeric sort.
    observed.sort()
    payloads = _public_literal_payloads(test_data, tj, limits)
    ts = tj.tokens
    values = [t.value for t in ts]
    owner_spellings = (selected_owner, selected_owner.rsplit('.',1)[-1])
    qual = r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*'
    chain = re.compile(r'(?P<arrays>'+qual+r')\.stream\((?P<owner>'+qual+r')\.class\.getDeclaredMethods\(\)\)'
        r'\.filter\((?P<f>[A-Za-z_$][A-Za-z0-9_$]*)->(?P<modifier>'+qual+r')\.isPublic\((?P=f)\.getModifiers\(\)\)\)'
        r'\.map\((?P<m>[A-Za-z_$][A-Za-z0-9_$]*)->(?P=m)\.getName\(\)\+<literal>\+(?P=m)\.getParameterCount\(\)\)'
        r'\.sorted\(\)\.toList\(\)')
    ordinary_imports = [name for static, name in tj.imports if not static]
    static_imports = [name for static, name in tj.imports if static]
    if any(name.endswith('.*') for name in ordinary_imports):
        reason('Consumer ordinary wildcard import bindings unresolved')
    if any(name.endswith('.*') and name != 'org.junit.jupiter.api.Assertions.*' for name in static_imports):
        reason('Consumer static wildcard import bindings unresolved')
    declared_names = {r['name'] for r in td.result if r['kind'] in ('class','interface','enum','record','method')}
    parameters = {p['name'] for row in td.result for p in row['parameters']}
    sensitive = {'List','Arrays','Modifier','assertEquals',owner_spellings[-1],selected_owner.split('.')[0],'java','org'}
    if any(not name.endswith('.*') and name.rsplit('.',1)[-1] in sensitive for name in static_imports):
        reason('Consumer named static helper/owner binding unresolved')
    if (declared_names | parameters) & sensitive:
        reason('Consumer declaration/parameter shadows a selected helper/owner root')
    for i, v in enumerate(values):
        if v in sensitive and (i == 0 or values[i-1] != '.') and i+1 < len(values) and values[i+1] in ('=',',',';','[',')','-',':'):
            reason('Consumer possible local/field root binding unresolved at line '+str(ts[i].line))

    def binding(spelling, expected):
        if spelling == expected:
            root = spelling.split('.')[0]
            if root in declared_names | parameters or any(x.rsplit('.',1)[-1] == root for x in ordinary_imports if not x.endswith('.*')):
                reason('Qualified helper root shadow/unresolved: '+root)
        elif spelling != expected.rsplit('.',1)[-1] or (expected not in ordinary_imports and tj.package != expected.rsplit('.',1)[0]):
            reason('Consumer helper binding unresolved: '+spelling)
        elif any(x.rsplit('.',1)[-1] == spelling and x != expected for x in ordinary_imports if not x.endswith('.*')):
            reason('Competing consumer import for '+spelling)

    def class_literal_positions(start, end):
        found = []
        for i in range(start+2, end):
            if values[i] != 'class' or values[i-1] != '.':
                continue
            a = i-2
            while a >= start+2 and values[a-1] == '.' and IDENT.fullmatch(values[a-2]):
                a -= 2
            if ''.join(values[a:i-1]) in owner_spellings:
                found.append(i)
        return found

    from bisect import bisect_left
    selected_literals = class_literal_positions(0,len(ts))
    reached = [i+2 for i in selected_literals if i+2 < len(ts) and values[i+1] == '.'
               and values[i+2] in ('getDeclaredMethods','getMethods')]
    handled = set()
    for at, token in enumerate(ts):
        if token.value != 'assertEquals' or at+1 >= len(ts) or values[at+1] != '(':
            continue
        end = tj.pairs[at+1]
        selected_positions = reached[bisect_left(reached,at+2):bisect_left(reached,end)]
        if not selected_positions:
            continue
        handled.update(selected_positions)
        require(end-at <= limits.max_context_tokens, 'Public inventory assertion context budget exhausted')
        # Split only immediate arguments, without scanning/copying a nested scope.
        args, begin, i = [], at+2, at+2
        while i < end:
            if values[i] in ('(','[','{'):
                i = tj.pairs[i]+1
            elif values[i] == ',':
                args.append((begin,i));begin=i+1;i+=1
            else:
                i+=1
        args.append((begin,end))
        if len(args) != 2:
            reason('Selected assertion is not an expected-first two-argument inventory at line '+str(token.line));continue
        left, right = args
        a,b = left
        opening = next((i for i in range(a,b) if values[i] == '('),None)
        if opening is None or ''.join(values[a:opening]) not in ('List.of','java.util.List.of') or tj.pairs[opening] != b-1:
            reason('Expected inventory is not a direct literal List.of at line '+str(token.line));continue
        expected, i, literal_ok = [], opening+1, True
        while i < b-1:
            if values[i] != '<literal>' or payloads.get(i) is None or payloads[i] == '/':
                literal_ok=False;break
            require(len(expected) < limits.max_declarations, 'Public inventory literal row budget exhausted')
            expected.append(payloads[i]);i+=1
            if i < b-1:
                if values[i] != ',' or i+1 == b-1:
                    literal_ok=False;break
                i+=1
        if not literal_ok:
            reason('Inventory literal/escape/expression unsupported at line '+str(token.line));continue
        actual = ''.join(values[right[0]:right[1]])
        match = chain.fullmatch(actual)
        if match is None or match['owner'] not in owner_spellings or len(selected_positions) != 1:
            reason('Selected reflection/filter/map/natural-sort chain unsupported at line '+str(token.line));continue
        slash = [i for i in range(*right) if values[i] == '<literal>']
        if len(slash) != 1 or payloads.get(slash[0]) != '/':
            reason('Name/arity separator must be the unescaped slash literal at line '+str(token.line));continue
        containing = [(r,span) for r,span in zip(td.result,td.spans) if r['kind']=='method' and
                      span[1] < at and values[span[1]] == '{' and at < tj.pairs[span[1]]]
        if len(containing) != 1:
            reason('Assertion ordinary method scope unresolved at line '+str(token.line))
        else:
            row, span = containing[0]
            if any(t.value == '<' for t in ts[span[0]:span[1]]):
                reason('Consumer generic method scope unresolved at line '+str(token.line))
            for parent, parent_span in zip(td.result,td.spans):
                full = '.'.join(filter(None,(parent['owner'],parent['name'])))
                if parent['kind'] in ('class','record','enum','interface') and (full == row['owner'] or row['owner'].startswith(full+'.')) and (parent['kind'] != 'class' or any(t.value in ('<','extends','implements','permits') for t in ts[parent_span[0]:parent_span[1]])):
                    reason('Consumer inherited/generic/nonordinary scope unresolved at line '+str(token.line))
        binding(''.join(values[a:opening-2]),'java.util.List')
        binding(match['arrays'],'java.util.Arrays');binding(match['modifier'],'java.lang.reflect.Modifier')
        binding(match['owner'],selected_owner)
        if at >= 1 and values[at-1] == '.':
            start = at-2
            while start >= 2 and values[start-1] == '.' and IDENT.fullmatch(values[start-2]):start-=2
            binding(''.join(values[start:at-1]),'org.junit.jupiter.api.Assertions')
        elif 'org.junit.jupiter.api.Assertions.*' not in static_imports and 'org.junit.jupiter.api.Assertions.assertEquals' not in static_imports:
            reason('Unqualified assertEquals binding unresolved')
        # Reserve a conservative per-check upper bound BEFORE multiset/output expansion.
        require(output_rows+2*len(expected)+2*len(observed)+2 <= limits.max_declarations,
                'Public inventory aggregate row/output budget exhausted')
        reserve=1024+2*sum(len(v)+32 for v in expected)+2*sum(len(v)+32 for v in observed)
        require(output_chars+reserve <= limits.max_total_bytes,'Public inventory aggregate output byte budget exhausted')
        output_rows+=2*len(expected)+2*len(observed)+2;output_chars+=reserve
        # Counter preserves duplicates; source natural order and literal order remain separate.
        from collections import Counter
        missing_counts, extra_counts = Counter(observed)-Counter(expected), Counter(expected)-Counter(observed)
        missing, extra = [], []
        for value in observed:
            if missing_counts[value]:missing.append(value);missing_counts[value]-=1
        for value in expected:
            if extra_counts[value]:extra.append(value);extra_counts[value]-=1
        same_multiset = not missing and not extra
        equal = expected == observed
        kind = None if equal else 'order_mismatch' if same_multiset else 'member_mismatch'
        entry=dict(line=token.line,expected_literal=expected,observed_matches=equal,same_multiset=same_multiset,
                   missing_from_literal=missing,extra_in_literal=extra,finding=kind)
        checks.append(entry)
        if kind is not None:findings.append(dict(line=token.line,kind=kind))
    if set(selected_literals)-{i-2 for i in handled}:
        reason('Selected class literal exists outside the supported direct reflection assertion')
    if not checks:
        reason('No supported selected public-method inventory assertion observed')
    complete = not reasons
    return dict(selected_owner=selected_owner,binding_basis='Explicit caller-known owner/source mapping; no independent Java resolution',
                observed_public_methods=observed,checks=checks,findings=findings,complete=complete,
                incomplete_reasons=reasons,matches=all(c['observed_matches'] for c in checks) if complete else None)


def _public_inventory_options(args, limits):
    owner = getattr(args,'public_method_inventory',None)
    names = ('inventory_test_source','inventory_test_final','inventory_test_final_sha256','inventory_test_final_bytes')
    if owner is None:
        require(all(getattr(args,k,None) is None for k in names),'Orphan public inventory test options',2)
        return None
    require(isinstance(owner,str) and 0 < len(owner) <= 4096 and
            all(IDENT.fullmatch(x) and x not in RESERVED for x in owner.split('.')),
            'Public inventory requires exact qualified owner',2)
    require(args.test_inventory is None,'Public method inventory and JUnit test inventory are separate modes',2)
    require(getattr(args,'inventory_test_source',None) is not None,'Public inventory requires immutable inventory-test-source',2)
    source=relative(args.inventory_test_source)
    final=getattr(args,'inventory_test_final',None)
    if final is None:
        require(args.inventory_test_final_sha256 is None and args.inventory_test_final_bytes is None,
                'Orphan inventory-test FINAL pins',2)
    else:
        final=absolute(final)
        require(isinstance(args.inventory_test_final_sha256,str) and SHA256.fullmatch(args.inventory_test_final_sha256) and
                type(args.inventory_test_final_bytes) is int and 0 <= args.inventory_test_final_bytes <= limits.max_file_bytes,
                'Inventory-test FINAL requires external bytes/SHA256 pins within budget',2)
    return source,final


def _append_public_inventory(args, limits, result, git_dir, owner_data, owner_final_data, options):
    source, final=options
    selected_owner=owner_final_data if owner_final_data is not None else owner_data
    used=len(owner_data)+(len(owner_final_data) if owner_final_data is not None else 0)
    reserved=args.inventory_test_final_bytes if final is not None else 0
    require(used+reserved < limits.max_total_bytes,'Combined inventory byte budget exhausted before test read')
    data,provenance=read_baseline(git_dir,args.revision,source,min(limits.max_file_bytes,limits.max_total_bytes-used-reserved))
    selected_test=data;final_data=final_sig=None
    tests=dict(baseline=dict(provenance=provenance))
    if final is not None:
        final_data,final_sig=read_final(final,min(limits.max_file_bytes,limits.max_total_bytes-used-len(data)))
        require(len(final_data)==args.inventory_test_final_bytes and sha(final_data)==args.inventory_test_final_sha256,
                'Inventory-test FINAL bytes/SHA256 mismatch',2)
        tests['final']=dict(provenance=dict(path=str(final),bytes=len(final_data),sha256=sha(final_data)))
        selected_test=final_data
    observed=public_method_inventory(selected_owner,selected_test,limits,args.public_method_inventory)
    observed.update(owner_source='final' if owner_final_data is not None else 'baseline',test_source='final' if final is not None else 'baseline')
    result['public_method_inventory']=observed
    result['inventory_test']=tests
    result['limitations']=result['limitations']+PUBLIC_INVENTORY_LIMITATIONS
    if not observed['complete']:result['status']='incomplete'
    repeat,rp=read_baseline(git_dir,args.revision,source,min(limits.max_file_bytes,limits.max_total_bytes-used-reserved))
    require(repeat==data and rp==provenance,'Immutable inventory test changed between observations')
    if final is not None:
        try:repeat,rs=read_final(final,min(limits.max_file_bytes,limits.max_total_bytes-used-len(data),len(final_data)))
        except CensusError as error:raise CensusError('Inventory-test FINAL changed/unreadable: '+str(error)) from error
        require(repeat==final_data and rs==final_sig,'Inventory-test FINAL changed between observations')
# END opt-in literal public method inventory 20261107.


def parser():
    p = argparse.ArgumentParser(description=__doc__, epilog='Exit: 0 complete/equal; 1 complete diff changes; '
                                '2 invalid input/pin/read; 3 incomplete/limit/observed change. '
                                'Test inventory: 1 complete static count mismatch; never a JUnit result.')
    p.add_argument('--git-dir', required=True, help='Canonical absolute local Git metadata directory')
    p.add_argument('--revision', required=True, help='Full lowercase 40-hex immutable commit, never HEAD/ref')
    p.add_argument('--source', required=True, help='Exact canonical relative .java path in commit')
    p.add_argument('--final', help='Canonical absolute external FINAL .java path')
    p.add_argument('--final-sha256', help='Externally supplied lowercase SHA256 pin; required with --final')
    p.add_argument('--final-bytes', type=int, help='Externally supplied byte pin; required with --final')
    p.add_argument('--test-inventory', metavar='CLASS', help='Static ordinary @Test subset for one exact qualified class')
    p.add_argument('--expected-count', type=int, help='Compare static proposal for FINAL if supplied, otherwise baseline')
    p.add_argument('--require-ordinary-test', action='append', metavar='METHOD',
                   help='Require a direct ordinary @Test name in the selected inventory (repeatable, at most 50)')
    p.add_argument('--public-method-inventory', metavar='OWNER', help='Opt-in caller-known ordinary-class public name/arity literal comparison; source diff still applies, incomplete exits3 before mismatches' )
    p.add_argument('--inventory-test-source', help='Exact immutable Git .java consumer containing the literal inventory assertion')
    p.add_argument('--inventory-test-final', help='Optional canonical pinned FINAL inventory-test .java source')
    p.add_argument('--inventory-test-final-sha256', help='Externally supplied inventory-test FINAL SHA256')
    p.add_argument('--inventory-test-final-bytes', type=int, help='Externally supplied inventory-test FINAL bytes')
    for name,value in vars(Limits()).items():
        p.add_argument('--'+name.replace('_','-'),type=int,default=value,help=f'Lowerable bound, maximum {value}')
    return p


def run(args):
    limits = Limits(**{name:getattr(args,name) for name in vars(Limits())})
    limits.validate()
    require(SHA1.fullmatch(args.revision) is not None, 'Require a full lowercase immutable commit SHA-1', 2)
    require(args.expected_count is None or (args.test_inventory is not None and
            type(args.expected_count) is int and 0 <= args.expected_count <= limits.max_declarations),
            'Expected count requires test-inventory mode and a nonnegative count within the declaration bound', 2)
    requested = getattr(args, 'require_ordinary_test', None)
    if requested is not None:
        require(args.test_inventory is not None and type(requested) is list and 1 <= len(requested) <= 50 and
                all(isinstance(name, str) and len(name) <= 4096 and IDENT.fullmatch(name) and
                    name not in RESERVED for name in requested) and len(set(requested)) == len(requested),
                'Require test-inventory and 1..50 distinct ordinary method identifiers (maximum 4096 characters each)', 2)
    public_options = _public_inventory_options(args, limits)
    source = relative(args.source)
    git_dir = absolute(args.git_dir)
    root_id = directory_id(git_dir)
    final = None
    if args.final is not None:
        final = absolute(args.final)
        require(isinstance(args.final_sha256,str) and SHA256.fullmatch(args.final_sha256) is not None and
                type(args.final_bytes) is int and 0 <= args.final_bytes <= limits.max_file_bytes,
                'FINAL requires externally supplied valid SHA256 and byte pins within budget', 2)
    else:
        require(args.final_sha256 is None and args.final_bytes is None, 'Orphan FINAL pins', 2)
    data, provenance = read_baseline(git_dir,args.revision,source,limits.max_file_bytes)
    require(len(data) + (args.final_bytes if final is not None else 0) <= limits.max_total_bytes,
            'Combined two-file byte budget exceeded')
    final_data = final_sig = None
    if final is not None:
        final_data, final_sig = read_final(final,limits.max_file_bytes)
        require(len(final_data) == args.final_bytes and sha(final_data) == args.final_sha256,
                'FINAL bytes/SHA256 do not match externally supplied pins', 2)
    if args.test_inventory is not None:
        result = dict(version=VERSION, mode='test_inventory', status='complete', limits=vars(limits),
                      limitations=LIMITATIONS + TEST_LIMITATIONS,
                      baseline=dict(provenance=provenance,
                                    test_inventory=test_inventory(data,limits,args.test_inventory)))
        if final is not None:
            result['final'] = dict(provenance=dict(path=str(final),bytes=len(final_data),sha256=sha(final_data)),
                                  test_inventory=test_inventory(final_data,limits,args.test_inventory))
        if any(not result[side]['test_inventory']['complete'] for side in ('baseline','final') if side in result):
            result['status'] = 'incomplete'
        if args.expected_count is not None:
            side = 'final' if final is not None else 'baseline'
            proposal = result[side]['test_inventory']['proposed_count']
            result['expected_count'] = dict(value=args.expected_count,source=side,
                                           matches=None if proposal is None else proposal == args.expected_count)
        if requested is not None:
            side = 'final' if final is not None else 'baseline'
            observed = result[side]['test_inventory']
            names = {method['name'] for method in observed['candidate_methods']}
            complete = observed['complete']
            result['ordinary_test_selection'] = dict(
                source=side, selected_class=observed['selected_class'], requested_names=requested,
                selected_ids=[observed['selected_class'] + '#' + name for name in requested if name in names]
                    if complete else None,
                missing_names=[name for name in requested if name not in names] if complete else None,
                matches=all(name in names for name in requested) if complete else None)
    else:
        baseline = dict(provenance=provenance,declarations=inventory(data,limits))
        result = dict(version=VERSION,status='complete',limits=vars(limits),limitations=LIMITATIONS,
                      baseline=baseline)
        if final is not None:
            result['final'] = dict(provenance=dict(path=str(final),bytes=len(final_data),sha256=sha(final_data)),
                                   declarations=inventory(final_data,limits))
            result['diff'] = compare(baseline['declarations'],result['final']['declarations'])
            if data == final_data:
                result['diff']['literal_ambiguities'] = []
                result['diff']['complete'] = True
            elif not result['diff']['complete']:
                result['status'] = 'incomplete'
                result['error'] = 'Redacted declaration literals cannot be compared across different file bytes'
    if public_options is not None:
        _append_public_inventory(args,limits,result,git_dir,data,final_data,public_options)
    require(directory_id(git_dir) == root_id, 'Git directory identity changed')
    repeat, repeat_provenance = read_baseline(git_dir,args.revision,source,limits.max_file_bytes)
    require(repeat == data and repeat_provenance == provenance, 'Immutable baseline changed between observations')
    if final is not None:
        try:
            repeat, repeat_sig = read_final(final,limits.max_file_bytes)
        except CensusError as error:
            raise CensusError('FINAL changed/unreadable on re-observation: '+str(error)) from error
        require(repeat == final_data and repeat_sig == final_sig, 'FINAL source changed between observations')
    require(directory_id(git_dir) == root_id, 'Git directory identity changed')
    return result


def main(argv=None):
    try:
        result = run(parser().parse_args(argv))
        code = 3 if result['status'] == 'incomplete' else int(
            result.get('expected_count',{}).get('matches') is False or
            result.get('ordinary_test_selection',{}).get('matches') is False or
            result.get('diff',{}).get('has_changes',False) or
            result.get('public_method_inventory',{}).get('matches') is False)
    except CensusError as error:
        code = error.code
        result = dict(version=VERSION,status='incomplete' if code == 3 else 'error',
                      error=str(error),limitations=LIMITATIONS)
    except (OSError, UnicodeError) as error:
        code = 2
        result = dict(version=VERSION,status='error',error=str(error),limitations=LIMITATIONS)
    print(json.dumps(result,ensure_ascii=True,sort_keys=True,indent=2))
    return code


if __name__ == '__main__':
    sys.exit(main())
