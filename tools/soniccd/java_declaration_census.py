#!/usr/bin/env python3
"""Bounded, read-only Java source declaration census; JSON to stdout, no saved state.

Inputs: canonical Git directory + full SHA-1 commit + exact source path; optional
canonical FINAL .java file with externally supplied SHA256 and byte count.
Origin: 2026-10-26 private java-declaration-census task, J=f4a968965ef55f47ebff8b3c7aa0827c8ef7aad4.
Uses the existing enum_contract_audit.py lexer/pairs, never a compiler or resolver.
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
DEPENDENCY_BYTES = 39145
DEPENDENCY_SHA256 = '1931593367393ea399fb01d8f6a474756d0e1b61056dfd31c9e3ef29463516e0'
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


def inventory(data, limits):
    limits.validate()
    require(len(data) <= limits.max_file_bytes, 'Source byte budget exceeded')
    dep = lexer()
    try:
        java = dep.lex(data, limits)
    except dep.AuditError as error:
        raise CensusError(str(error)) from error
    return Declarations(java, limits).parse()


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


def parser():
    p = argparse.ArgumentParser(description=__doc__, epilog='Exit: 0 complete/equal; 1 complete diff changes; '
                                '2 invalid input/pin/read; 3 incomplete/limit/observed change.')
    p.add_argument('--git-dir', required=True, help='Canonical absolute local Git metadata directory')
    p.add_argument('--revision', required=True, help='Full lowercase 40-hex immutable commit, never HEAD/ref')
    p.add_argument('--source', required=True, help='Exact canonical relative .java path in commit')
    p.add_argument('--final', help='Canonical absolute external FINAL .java path')
    p.add_argument('--final-sha256', help='Externally supplied lowercase SHA256 pin; required with --final')
    p.add_argument('--final-bytes', type=int, help='Externally supplied byte pin; required with --final')
    for name,value in vars(Limits()).items():
        p.add_argument('--'+name.replace('_','-'),type=int,default=value,help=f'Lowerable bound, maximum {value}')
    return p


def run(args):
    limits = Limits(**{name:getattr(args,name) for name in vars(Limits())})
    limits.validate()
    require(SHA1.fullmatch(args.revision) is not None, 'Require a full lowercase immutable commit SHA-1', 2)
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
        code = 3 if result['status'] == 'incomplete' else int(result.get('diff',{}).get('has_changes',False))
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
