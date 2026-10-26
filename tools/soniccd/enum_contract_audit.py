#!/usr/bin/env python3
"""Read-only, bounded lexical Java enum migration audit; no compiler or symbol resolver.

Inputs: explicit Git worktree, full immutable base commit, enum source/name/type,
explicit Java scan roots. Output: version-1 JSON or text on stdout; no saved state.
Origin: 2026-10-26 private enum-contract tooling task (62 -> 64 mapper contract).
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

VERSION = 1
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*\Z")
TYPE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*\Z")
TOKEN = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*|[0-9][A-Za-z0-9_]*")
KEYWORDS = frozenset('class interface record enum public private protected static final abstract implements extends void new return'.split())
LIMITS = {
    'max_files': (1000, 10000), 'max_entries': (20000, 100000),
    'max_file_bytes': (1024 * 1024, 16 * 1024 * 1024),
    'max_total_bytes': (16 * 1024 * 1024, 64 * 1024 * 1024),
    'max_hits': (1000, 10000), 'max_tokens': (200000, 1000000),
    'max_context_tokens': (4096, 32768),
}
LIMITATIONS = [
    'Lexical evidence only: no Java AST, type checking, overload or symbol resolution.',
    'Only fully qualified, same-package owner, or exact single-type imports are resolved lexically; shadowing and inheritance are not modeled.',
    'Only direct values().length and java.util.Arrays.stream(values()) chains are analyzed; aliases, reflection, method references and static values imports are unsupported.',
    'Only whole decimal/hex/binary/octal integer arguments in lexical assertEquals calls are compared; symbolic counts and arithmetic are not evaluated.',
    'A syntactic limit anywhere in the same direct stream chain marks a bounded slice; its runtime value and semantic sufficiency are not proved.',
    'Selected Java paths and HEAD are read twice, non-atomically. Unselected files/index are not hashed; edits restored between observations can escape detection.',
    'Zero findings proves neither complete consumer coverage nor compilation, tests, ordinal compatibility semantics or runtime behavior.',
]


class AuditError(ValueError):
    def __init__(self, message, code=2):
        super().__init__(message)
        self.code = code


def require(condition, message, code=2):
    if not condition:
        raise AuditError(message, code)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def signature(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def relative(value):
    require(isinstance(value, str) and 0 < len(value) <= 4096 and
            '\\' not in value and ':' not in value and not any(ord(c) < 32 for c in value),
            'Require canonical relative POSIX paths (no control characters, backslashes or colons)')
    parts = value.split('/')
    require(all(p and p not in ('.', '..') and p.lower() != '.git' for p in parts),
            f'Unsafe relative path: {value!r}')
    return value


def canonical_root(value):
    require(0 < len(value) <= 4096 and not any(ord(c) < 32 for c in value), 'Malformed root path')
    root = Path(value)
    try:
        resolved = root.resolve()
    except (OSError, RuntimeError) as error:
        raise AuditError(f'Cannot resolve root safely: {error}') from error
    require(root.is_absolute() and str(root) == value and '..' not in root.parts and resolved == root,
            'Require canonical absolute --root without symlinks or traversal')
    require(root.is_dir(), f'Not a directory: {root}')
    require(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW') and hasattr(os, 'O_DIRECTORY'),
            'Safe no-follow reading requires POSIX directory descriptors')
    return root


@contextmanager
def opened(root, name, directory=False):
    """Walk every component through no-follow descriptors, including during races."""
    descriptors = []
    try:
        fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        descriptors.append(fd)
        parts = relative(name).split('/')
        for i, part in enumerate(parts):
            is_dir = i < len(parts) - 1 or directory
            flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
            if is_dir:
                flags |= os.O_DIRECTORY
            fd = os.open(part, flags, dir_fd=fd)
            descriptors.append(fd)
            info = os.fstat(fd)
            require(stat.S_ISDIR(info.st_mode) if is_dir else stat.S_ISREG(info.st_mode),
                    f'Not a regular {"directory" if is_dir else "file"}: {name}')
        yield fd
    finally:
        for fd in reversed(descriptors):
            os.close(fd)


@dataclass
class Saved:
    data: bytes
    sig: tuple

    def public(self, path):
        return dict(path=path, bytes=len(self.data), sha256=sha(self.data))


def read_file(root, name, cap):
    with opened(root, name) as fd:
        before = os.fstat(fd)
        require(before.st_size <= cap, f'Byte budget exceeded before reading {name}', 3)
        data = bytearray()
        while len(data) <= before.st_size:
            block = os.read(fd, min(65536, before.st_size + 1 - len(data)))
            if not block:
                break
            data.extend(block)
        after = os.fstat(fd)
    require(signature(before) == signature(after) and len(data) == before.st_size,
            f'File changed during read: {name}', 3)
    return Saved(bytes(data), signature(before))


def git(root, args, cap=8192):
    """Bound both pipes before allocating output; no shell, hooks, pager or writes."""
    env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
    env.update(GIT_OPTIONAL_LOCKS='0', GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull,
               GIT_TERMINAL_PROMPT='0', GIT_NO_REPLACE_OBJECTS='1', GIT_NO_LAZY_FETCH='1',
               GIT_ALLOW_PROTOCOL='', LC_ALL='C')
    argv = ['git', '--no-optional-locks', '--literal-pathspecs', '-c', 'core.fsmonitor=false', '-c', 'protocol.allow=never', *args]
    proc = subprocess.Popen(argv, cwd=root, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            stdin=subprocess.DEVNULL, shell=False)
    output = {'out': bytearray(), 'err': bytearray()}
    deadline = time.monotonic() + 15
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(proc.stdout, selectors.EVENT_READ, ('out', cap))
            selector.register(proc.stderr, selectors.EVENT_READ, ('err', 8192))
            while selector.get_map():
                remaining = deadline - time.monotonic()
                require(remaining > 0, 'Git read timed out', 3)
                for key, _ in selector.select(min(remaining, 0.25)):
                    label, limit = key.data
                    block = os.read(key.fd, min(65536, limit + 1 - len(output[label])))
                    if not block:
                        selector.unregister(key.fileobj)
                    else:
                        output[label].extend(block)
                        require(len(output[label]) <= limit, 'Git output byte budget exceeded', 3)
        result = proc.wait(timeout=max(0.01, deadline - time.monotonic()))
        require(result == 0, 'Git read failed: ' + output['err'].decode('utf-8', 'replace').strip())
        return bytes(output['out'])
    finally:
        if proc.poll() is None:
            proc.kill()
        proc.wait()
        proc.stdout.close()
        proc.stderr.close()


def head(root):
    top = git(root, ['rev-parse', '--show-toplevel']).decode('utf-8').strip()
    require(top == str(root), 'Select the exact Git worktree root, not a subdirectory')
    value = git(root, ['rev-parse', '--verify', 'HEAD']).decode('ascii').strip()
    require(COMMIT.fullmatch(value), 'Require a committed SHA-1 Git worktree')
    return value


def baseline(root, base, source, cap):
    require(git(root, ['cat-file', '-t', base]).strip() == b'commit', '--base must identify a commit object')
    entry = git(root, ['ls-tree', '-z', base, '--', source])
    require(entry.endswith(b'\0') and entry.count(b'\0') == 1, 'Enum source absent/ambiguous at --base')
    metadata, filename = entry[:-1].split(b'\t', 1)
    mode, kind, oid = metadata.decode('ascii').split()
    require(filename == source.encode('utf-8') and mode in ('100644', '100755') and kind == 'blob' and
            COMMIT.fullmatch(oid), 'Baseline source must be a regular Git blob (no symlinks)')
    size = int(git(root, ['cat-file', '-s', oid]).strip())
    require(size <= cap, 'Baseline source byte budget exceeded before read', 3)
    data = git(root, ['cat-file', 'blob', oid], cap=size)
    require(len(data) == size and hashlib.sha1(b'blob ' + str(size).encode('ascii') + b'\0' + data).hexdigest() == oid,
            'Baseline blob size/hash mismatch', 3)
    return data, oid


def enumerate_java(root, roots, limits):
    result, entries = set(), 0
    pending = list(reversed(roots))
    while pending:
        name = pending.pop()
        with opened(root, name, directory=True) as fd:
            children = []
            with os.scandir(fd) as iterator:
                for entry in iterator:
                    entries += 1
                    require(entries <= limits.max_entries, 'Directory-entry budget exhausted', 3)
                    path = name + '/' + entry.name
                    relative(path)
                    info = entry.stat(follow_symlinks=False)
                    require(not stat.S_ISLNK(info.st_mode), f'Symlink inside scan scope: {path}')
                    if stat.S_ISDIR(info.st_mode):
                        children.append(path)
                    elif path.endswith('.java'):
                        require(stat.S_ISREG(info.st_mode), f'Non-regular Java source: {path}')
                        require(len(result) < limits.max_files, 'Java file budget exhausted', 3)
                        result.add(path)
            pending.extend(reversed(sorted(children)))
    return sorted(result)


@dataclass(frozen=True)
class Token:
    value: str
    line: int


@dataclass
class Java:
    tokens: list[Token]
    pairs: dict[int, int]
    parents: list[int | None]
    package: str
    imports: list[tuple[bool, str]]


def lex(data, limits):
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError as error:
        raise AuditError('Unsupported non-UTF-8 Java source', 3) from error
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    require('\0' not in text and not re.search(r'\\u+[0-9a-fA-F]{4}', text),
            'Unsupported NUL or Java Unicode escape preprocessing', 3)
    tokens, pos, line = [], 0, 1
    while pos < len(text):
        char = text[pos]
        if char.isspace():
            line += char == '\n'
            pos += 1
            continue
        if text.startswith('//', pos):
            end = text.find('\n', pos + 2)
            pos = len(text) if end < 0 else end
            continue
        if text.startswith('/*', pos):
            end = text.find('*/', pos + 2)
            require(end >= 0, f'Unterminated comment at line {line}', 3)
            line += text.count('\n', pos, end + 2)
            pos = end + 2
            continue
        start_line = line
        if char in ('"', "'"):
            marker = '"""' if text.startswith('"""', pos) else char
            end = pos + len(marker)
            while end < len(text):
                if text[end] == '\\':
                    end += 2
                elif text.startswith(marker, end):
                    end += len(marker)
                    break
                else:
                    require(marker == '"""' or text[end] not in '\r\n',
                            f'Unterminated literal at line {line}', 3)
                    end += 1
            else:
                raise AuditError(f'Unterminated literal at line {line}', 3)
            require(end <= len(text), f'Unterminated literal at line {line}', 3)
            line += text.count('\n', pos, end)
            value, pos = '<literal>', end
        else:
            match = TOKEN.match(text, pos)
            if match:
                value, pos = match.group(), match.end()
            else:
                require(ord(char) < 128 and char.isprintable(),
                        f'Unsupported identifier/character at line {line}', 3)
                value, pos = char, pos + 1
        require(len(tokens) < limits.max_tokens, 'Token budget exhausted before expansion', 3)
        tokens.append(Token(value, start_line))
    pairs, stack, parents = {}, [], []
    parens = []
    for i, token in enumerate(tokens):
        parents.append(parens[-1] if parens else None)
        if token.value in ('(', '[', '{'):
            require(len(stack) < 128, 'Delimiter nesting budget exhausted', 3)
            stack.append(i)
            if token.value == '(':
                parens.append(i)
        elif token.value in (')', ']', '}'):
            require(stack and tokens[stack[-1]].value == {')': '(', ']': '[', '}': '{'}[token.value],
                    f'Unbalanced delimiters at line {token.line}', 3)
            opening = stack.pop()
            pairs[opening], pairs[i] = i, opening
            if token.value == ')':
                parens.pop()
    require(not stack, 'Unbalanced delimiters at end of source', 3)
    package, imports = '', []
    for i, token in enumerate(tokens):
        if token.value not in ('package', 'import'):
            continue
        j = i + 1
        static = token.value == 'import' and j < len(tokens) and tokens[j].value == 'static'
        j += static
        first = j
        while j < len(tokens) and tokens[j].value != ';':
            require(j - first < limits.max_context_tokens, 'Import context budget exhausted', 3)
            j += 1
        require(j < len(tokens), 'Unterminated package/import declaration', 3)
        name = ''.join(t.value for t in tokens[first:j])
        require(TYPE.fullmatch(name) or (token.value == 'import' and name.endswith('.*') and TYPE.fullmatch(name[:-2])),
                'Unsupported package/import grammar', 3)
        if token.value == 'package':
            package = name
        else:
            imports.append((bool(static), name))
    return Java(tokens, pairs, parents, package, imports)


def enum_body(java, name, selected_type, limits):
    ts, pairs = java.tokens, java.pairs
    declarations = []
    for i in range(len(ts) - 1):
        if ts[i].value not in ('class', 'interface', 'record', 'enum') or (i and ts[i-1].value == '.'):
            continue
        if not IDENT.fullmatch(ts[i+1].value):
            continue
        j = i + 2
        while j < len(ts) and ts[j].value not in ('{', ';'):
            require(j - i < limits.max_context_tokens, 'Type header context budget exhausted', 3)
            j = pairs[j] + 1 if ts[j].value in ('(', '[') else j + 1
        require(len(declarations) < 1000, 'Type declaration budget exhausted', 3)
        if j < len(ts) and ts[j].value == '{':
            declarations.append((i, j, pairs[j], ts[i].value, ts[i+1].value))
    candidates = []
    for begin, opening, closing, kind, declared in declarations:
        if kind != 'enum' or declared != name:
            continue
        owners = [d[4] for d in declarations if d[1] < begin < d[2]]
        local = '.'.join([*owners, declared])
        qualified = '.'.join(p for p in (java.package, local) if p)
        if selected_type in (local, qualified):
            candidates.append((opening, closing, qualified, local))
    require(len(candidates) == 1, 'Selected enum/type must have exactly one lexical declaration in source')
    return candidates[0], declarations


def enum_constants(java, name, selected_type, limits):
    ts, pairs = java.tokens, java.pairs
    (opening, closing, qualified, local), _ = enum_body(java, name, selected_type, limits)
    names, i = [], opening + 1
    while i < closing and ts[i].value != ';':
        while ts[i].value == '@':
            i += 1
            require(i < closing and IDENT.fullmatch(ts[i].value), 'Unsupported enum annotation')
            i += 1
            while i + 1 < closing and ts[i].value == '.' and IDENT.fullmatch(ts[i+1].value):
                i += 2
            if ts[i].value == '(':
                i = pairs[i] + 1
        require(i < closing and IDENT.fullmatch(ts[i].value) and ts[i].value not in KEYWORDS,
                'Unsupported enum constant grammar')
        require(len(names) < 10000, 'Enum constant budget exhausted', 3)
        names.append(ts[i].value)
        i += 1
        if i < closing and ts[i].value == '(':
            i = pairs[i] + 1
        if i < closing and ts[i].value == '{':
            i = pairs[i] + 1
        require(i <= closing and ts[i].value in (',', ';', '}'), 'Unsupported enum constant tail')
        if ts[i].value == ',':
            i += 1
        else:
            break
    require(len(names) == len(set(names)), 'Duplicate enum constant names')
    return names, qualified, local


def dotted_before(ts, end, limit):
    """Identifier/dot sequence ending immediately before end; no expression parsing."""
    i = end - 1
    if i < 0 or not IDENT.fullmatch(ts[i].value):
        return end, ''
    start = i
    while start >= 2 and ts[start-1].value == '.' and IDENT.fullmatch(ts[start-2].value):
        require(end - start < limit, 'Qualifier context budget exhausted', 3)
        start -= 2
    require(end - start <= limit, 'Qualifier context budget exhausted', 3)
    return start, ''.join(t.value for t in ts[start:end])


def strip_parens(java, start, end):
    while end > start and java.tokens[start].value == '(' and java.pairs[start] == end - 1:
        start, end = start + 1, end - 1
    return start, end


def integer(java, start, end):
    start, end = strip_parens(java, start, end)
    sign = 1
    if start < end and java.tokens[start].value in ('+', '-'):
        sign = -1 if java.tokens[start].value == '-' else 1
        start += 1
    if end - start != 1:
        return None
    text = java.tokens[start].value
    if len(text) > 128 or not re.fullmatch(r'(?:0[xX][0-9a-fA-F][0-9a-fA-F_]*|0[bB][01][01_]*|[0-9][0-9_]*)(?:[lL])?', text):
        return None
    text = text.rstrip('lL')
    if text.endswith('_'):
        return None
    text = text.replace('_', '')
    try:
        value = int(text, 16 if text.lower().startswith('0x') else 2 if text.lower().startswith('0b') else
                    8 if len(text) > 1 and text.startswith('0') else 10)
    except ValueError:
        return None
    return sign * value if value <= (1 << 63) - 1 else None


def arguments(java, opening, limits):
    closing = java.pairs[opening]
    require(closing - opening <= limits.max_context_tokens, 'Argument context budget exhausted', 3)
    result, start, i = [], opening + 1, opening + 1
    while i < closing:
        value = java.tokens[i].value
        if value in ('(', '[', '{'):
            i = java.pairs[i] + 1
        elif value == ',':
            result.append((start, i))
            start = i + 1
            i += 1
        else:
            i += 1
    if start < closing:
        result.append((start, closing))
    return result


def assertion_count(java, start, end, old_count, new_count, limits):
    """Compare only whole count expressions in either of the first two arguments.

    A third message argument is allowed. This is a lexical assertEquals candidate,
    not a claim about the resolved JUnit overload or symbolic expression values.
    """
    opening = java.parents[start]
    while opening is not None:
        if opening > 0 and java.tokens[opening-1].value == 'assertEquals':
            args = arguments(java, opening, limits)
            require(java.pairs[opening] - start <= limits.max_context_tokens,
                    'Assertion context budget exhausted', 3)
            if len(args) not in (2, 3):
                return None, 'unsupported_assertion_expression'
            for index in (0, 1):
                if strip_parens(java, *args[index]) == (start, end):
                    literal = integer(java, *args[1-index])
                    state = ('symbolic_or_expression' if literal is None else
                             'current_total' if literal == new_count else
                             'old_total' if literal == old_count else 'other_literal')
                    return literal, state
            return None, 'unsupported_assertion_expression'
        opening = java.parents[opening]
    return None, 'none'


def resolution(java, qualifier, qualified, local, path, source):
    if qualifier == qualified:
        return 'fully_qualified'
    package = qualified[:-len(local)].rstrip('.')
    if qualifier == local and java.package == package:
        return 'same_package_owner'
    for static, imported in java.imports:
        if imported.endswith('.*'):
            continue
        parts = qualified.split('.')
        for length in range(1, len(parts) + 1):
            prefix = '.'.join(parts[:length])
            shortened = '.'.join(parts[length-1:])
            if imported == prefix and qualifier == shortened:
                return 'exact_type_import'
    if path == source and qualifier == local.rsplit('.', 1)[-1]:
        return 'enum_source_simple_name'
    return None


def related(qualifier, local):
    parts = local.split('.')
    return qualifier in ('.'.join(parts[i:]) for i in range(len(parts)))



def implicit_enum_values(java, local, limits):
    """Yield observed zero-argument calls in the selected lexical enum body.

    Do not bind these calls: report incomplete. Exclude other named types and
    directly recognized new-expression class bodies rather than attributing
    their unrelated unqualified methods to the selected enum.
    """
    ts, pairs = java.tokens, java.pairs
    (opening, closing, _, _), declarations = enum_body(java, local.rsplit('.', 1)[-1], local, limits)
    excluded = {start: end for _, start, end, _, _ in declarations if opening < start < closing}
    i = opening + 1
    while i < closing:
        if i in excluded:
            i = excluded[i] + 1
            continue
        if ts[i].value == 'new':
            # Skip balanced argument/annotation/array groups while locating an
            # anonymous class body. This is bounded lexical scope, not binding.
            j = i + 1
            while j < closing and ts[j].value not in ('{', '}', ';'):
                require(j - i <= limits.max_context_tokens, 'Anonymous type context budget exhausted', 3)
                if ts[j].value in ('(', '['):
                    end = pairs[j] + 1
                    require(end - i <= limits.max_context_tokens, 'Anonymous type context budget exhausted', 3)
                    if ts[j].value == '(' and end < closing and ts[end].value == '{':
                        excluded[end] = pairs[end]
                        break
                    j = end
                else:
                    j += 1
        if (ts[i].value == 'values' and i + 2 < closing and ts[i+1].value == '(' and
                pairs[i+1] == i+2 and ts[i-1].value not in ('.', ':', '@', 'new')):
            yield i
        i += 1


def inspect_consumers(java, path, saved, qualified, local, source, old_count, new_count, limits, findings, issues):
    ts = java.tokens
    file_sha256 = sha(saved.data)
    implicit = implicit_enum_values(java, local, limits) if path == source else iter(())
    next_implicit = next(implicit, None)
    def add(index, kind, qualifier, literal=None, severity='inventory', **details):
        require(len(findings) < limits.max_hits, 'Finding budget exhausted before append', 3)
        findings.append(dict(path=path, line=ts[index].line, kind=kind, literal=literal,
                             type=qualified, qualifier=qualifier, sha256=file_sha256,
                             severity=severity, **details))
    def unsupported(index, kind, qualifier, message):
        add(index, kind, qualifier, severity='incomplete', reason=message)
        issues.append(dict(path=path, line=ts[index].line, kind=kind, message=message))
    for i, token in enumerate(ts):
        if token.value != 'values':
            continue
        if i >= 2 and ts[i-1].value == ':' and ts[i-2].value == ':':
            start, qualifier = dotted_before(ts, i - 2, limits.max_context_tokens)
            if resolution(java, qualifier, qualified, local, path, source) or related(qualifier, local):
                unsupported(start, 'unsupported_method_reference', qualifier, 'Method reference is not followed through consumers')
            continue
        if i + 1 >= len(ts) or ts[i+1].value != '(':
            continue
        if i < 1 or ts[i-1].value != '.':
            if i == next_implicit:
                unsupported(i, 'unsupported_implicit_values', '',
                            'Unqualified values() in selected enum body is not resolved')
                next_implicit = next(implicit, None)
            elif any(static and imported in (qualified + '.values', qualified + '.*') for static, imported in java.imports):
                unsupported(i, 'unsupported_static_values', '', 'Static values import/call is not resolved')
            continue
        start, qualifier = dotted_before(ts, i - 1, limits.max_context_tokens)
        resolved = resolution(java, qualifier, qualified, local, path, source)
        if not resolved:
            if related(qualifier, local):
                unsupported(start, 'unresolved_type_reference', qualifier, 'No supported exact import or same-package owner form')
            continue
        end = java.pairs[i+1] + 1
        if end != i + 3:
            unsupported(start, 'unsupported_values_use', qualifier, 'values call has arguments')
            continue
        if end + 1 < len(ts) and ts[end].value == '.' and ts[end+1].value == 'length':
            literal, assertion = assertion_count(java, start, end+2, old_count, new_count, limits)
            stale = assertion == 'old_total' and old_count != new_count
            if assertion == 'unsupported_assertion_expression':
                unsupported(start, 'unsupported_assertion_expression', qualifier,
                            'Count is not a whole supported assertEquals argument')
            else:
                add(start, 'stale_total_assertion' if stale else 'values_length', qualifier,
                    literal=literal, severity='review' if stale else 'inventory',
                    resolution=resolved, assertion=assertion)
            continue
        # Only a direct Arrays.stream(enum.values()) wrapper has known chain ownership.
        if start > 0 and ts[start-1].value == '(' and java.pairs[start-1] == end:
            stream_start, wrapper = dotted_before(ts, start-1, limits.max_context_tokens)
            stream_ok = wrapper == 'java.util.Arrays.stream' or (wrapper == 'Arrays.stream' and
                        (False, 'java.util.Arrays') in java.imports) or (wrapper == 'stream' and
                        (True, 'java.util.Arrays.stream') in java.imports)
            if stream_ok:
                cursor, methods = end + 1, []
                while cursor + 2 < len(ts) and ts[cursor].value == '.' and IDENT.fullmatch(ts[cursor+1].value) and ts[cursor+2].value == '(':
                    closing = java.pairs[cursor+2]
                    require(closing - stream_start <= limits.max_context_tokens, 'Stream chain context budget exhausted', 3)
                    methods.append((cursor+1, ts[cursor+1].value, cursor+2))
                    cursor = closing + 1
                if cursor < len(ts) and ts[cursor].value == '.' and (cursor + 1 >= len(ts) or ts[cursor+1].value == '<'):
                    unsupported(start, 'unsupported_stream_chain', qualifier, 'Generic/indirect stream tail cannot establish an explicit limit')
                    continue
                skips = [m for m in methods if m[1] == 'skip']
                bounded = any(m[1] == 'limit' for m in methods)
                if not skips:
                    add(start, 'values_stream', qualifier, resolution=resolved)
                for index, _, opening in skips:
                    args = arguments(java, opening, limits)
                    value = integer(java, *args[0]) if len(args) == 1 else None
                    add(index, 'bounded_stream_skip' if bounded else 'unbounded_stream_skip', qualifier,
                        literal=value, severity='inventory' if bounded else 'review', explicit_limit=bounded,
                        resolution=resolved)
                continue
        unsupported(start, 'unsupported_values_use', qualifier, 'Indirect array/stream use is not followed')


def scope_hash(saved):
    digest = hashlib.sha256()
    for path in sorted(saved):
        data = saved[path].data
        digest.update(path.encode('utf-8') + b'\0' + str(len(data)).encode('ascii') + b'\0' + sha(data).encode('ascii') + b'\n')
    return digest.hexdigest()


def audit(args):
    report = dict(version=VERSION, status='rejected', exit_code=2, findings=[], issues=[], limitations=LIMITATIONS)
    observing_after = False
    try:
        require(COMMIT.fullmatch(args.base), '--base must be exactly 40 lowercase hexadecimal characters, not a ref/expression')
        require(len(args.enum) <= 256 and len(args.type) <= 4096 and
                IDENT.fullmatch(args.enum) and TYPE.fullmatch(args.type) and args.type.split('.')[-1] == args.enum,
                '--enum and --type must name the same ASCII Java enum')
        source = relative(args.source)
        require(source.endswith('.java'), '--source must name a Java file')
        require(0 < len(args.scan_root) <= 16, 'Supply 1..16 explicit directory --scan-root values')
        roots = sorted(relative(value) for value in args.scan_root)
        require(all(a != b and not b.startswith(a + '/') for i, a in enumerate(roots) for b in roots[i+1:]),
                'Scan roots must be distinct and non-overlapping')
        for key, (_, maximum) in LIMITS.items():
            require(0 < getattr(args, key) <= maximum, f'--{key.replace("_", "-")} must be in 1..{maximum}')
        require(args.max_file_bytes <= args.max_total_bytes, 'Per-file budget cannot exceed total byte budget')
        root = canonical_root(args.root)
        root_sig = signature(root.lstat())
        before_head = head(root)
        report['provenance'] = dict(root=str(root), source=source, scan_roots=roots, base_commit=args.base,
                                    head_before=before_head, head_after=None, source_sha256_before=None,
                                    source_sha256_after=None, scope_sha256_before=None, scope_sha256_after=None,
                                    non_atomic=True, scope='selected Java files plus enum source and HEAD; excludes index/unselected files')
        report['budgets'] = {key: getattr(args, key) for key in LIMITS}
        old_data, old_blob = baseline(root, args.base, source, args.max_file_bytes)
        old_names, qualified, local = enum_constants(lex(old_data, args), args.enum, args.type, args)
        paths = enumerate_java(root, roots, args)
        scan_paths = set(paths)
        if source not in paths:
            require(len(paths) < args.max_files, 'Java file budget exhausted by enum source', 3)
            paths = sorted([*paths, source])
        saved, total = {}, 0
        for path in paths:
            saved[path] = read_file(root, path, min(args.max_file_bytes, args.max_total_bytes - total))
            total += len(saved[path].data)
        report['provenance'].update(source_sha256_before=sha(saved[source].data),
                                     scope_sha256_before=scope_hash(saved), base_blob=old_blob,
                                     base_source_sha256=sha(old_data),
                                     current_blob_sha1=hashlib.sha1(b'blob ' + str(len(saved[source].data)).encode('ascii') + b'\0' + saved[source].data).hexdigest())
        new_names, current_type, current_local = enum_constants(lex(saved[source].data, args), args.enum, args.type, args)
        require((qualified, local) == (current_type, current_local), 'Enum owner/package changed; migration outside supported scope', 3)
        prefix = new_names[:len(old_names)] == old_names
        old_ordinals = {name: i for i, name in enumerate(old_names)}
        new_ordinals = {name: i for i, name in enumerate(new_names)}
        report['enum'] = dict(type=qualified, old_names=old_names, new_names=new_names,
                              old_count=len(old_names), new_count=len(new_names), append_only_prefix=prefix,
                              added=[n for n in new_names if n not in old_ordinals],
                              removed=[n for n in old_names if n not in new_ordinals],
                              changed_ordinals=[dict(name=n, old=i, new=new_ordinals[n]) for i, n in enumerate(old_names)
                                                if n in new_ordinals and new_ordinals[n] != i])
        for path in paths:
            if path not in scan_paths:
                continue
            try:
                java = lex(saved[path].data, args)
                inspect_consumers(java, path, saved[path], qualified, local, source, len(old_names), len(new_names),
                                  args, report['findings'], report['issues'])
            except AuditError as error:
                if 'budget' in str(error).lower():
                    raise
                report['issues'].append(dict(path=path, line=None, kind='unsupported_java', message=str(error)))
        observing_after = True
        after_paths = enumerate_java(root, roots, args)
        if source not in after_paths:
            after_paths = sorted([*after_paths, source])
        require(after_paths == paths, 'Java path set changed between observations', 3)
        after, total = {}, 0
        for path in paths:
            after[path] = read_file(root, path, min(args.max_file_bytes, args.max_total_bytes - total))
            total += len(after[path].data)
        after_head = head(root)
        report['provenance'].update(head_after=after_head, source_sha256_after=sha(after[source].data),
                                     scope_sha256_after=scope_hash(after))
        require(before_head == after_head and root_sig == signature(root.lstat()) and
                all(saved[p].sig == after[p].sig and saved[p].data == after[p].data for p in paths),
                'HEAD, root or scoped source changed between observations', 3)
        report['files'] = [saved[path].public(path) for path in paths]
        code = 3 if report['issues'] else 1 if not prefix or any(f['severity'] == 'review' for f in report['findings']) else 0
        report.update(exit_code=code, status={0: 'bounded_inventory', 1: 'needs_review', 3: 'incomplete'}[code])
    except (AuditError, OSError, UnicodeError, subprocess.SubprocessError) as error:
        code = error.code if isinstance(error, AuditError) else (3 if observing_after else 2)
        report.update(exit_code=code, status='incomplete' if code == 3 else 'rejected', error=str(error))
    report['findings'].sort(key=lambda f: (f['path'], f['line'], f['kind'], f['qualifier']))
    report['issues'].sort(key=lambda f: (f['path'], f['line'] or 0, f['kind']))
    report['coverage'] = dict(complete_consumer_proof=False, unsupported_observations=len(report['issues']),
                              stable_bounded_read=report['exit_code'] in (0, 1) or
                              ('provenance' in report and report['provenance']['head_after'] is not None and
                               report['provenance']['scope_sha256_before'] == report['provenance']['scope_sha256_after'] and
                               'error' not in report))
    return report


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise AuditError(message)


def parser():
    result = Parser(description=__doc__, allow_abbrev=False, epilog='Exit: 0 stable bounded inventory; 1 migration/review candidates; 2 rejected input; 3 incomplete/changed/budget-exhausted scan. No code is executed or changed.')
    result.add_argument('--root', required=True, help='canonical absolute Git worktree root')
    result.add_argument('--base', required=True, help='full 40-character immutable commit, never a branch/ref expression')
    result.add_argument('--source', required=True, help='canonical relative enum Java source path')
    result.add_argument('--enum', required=True, help='simple ASCII enum name')
    result.add_argument('--type', required=True, help='exact source owner path or fully qualified enum type')
    result.add_argument('--scan-root', required=True, action='append', help='explicit canonical relative directory; repeat up to 16 times, no overlap')
    result.add_argument('--json', action='store_true', help='version-1 machine-readable report (also on errors)')
    for key, (default, maximum) in LIMITS.items():
        result.add_argument('--' + key.replace('_', '-'), type=int, default=default,
                            help=f'positive scan budget (default {default}, hard maximum {maximum})')
    return result


def human(report):
    lines = [f"enum-contract-audit v{VERSION}: {report['status']} (exit {report['exit_code']})"]
    if 'enum' in report:
        value = report['enum']
        lines.append(f"{value['type']}: {value['old_count']} -> {value['new_count']}; append-only prefix={value['append_only_prefix']}")
    if 'provenance' in report:
        p = report['provenance']
        lines.append(f"base={p['base_commit']} blob={p.get('base_blob')} HEAD={p['head_before']} -> {p['head_after']}")
        lines.append(f"source SHA256={p['source_sha256_before']} -> {p['source_sha256_after']}; non-atomic selected-file read")
    for finding in report['findings']:
        lines.append(f"{finding['path']}:{finding['line']}: {finding['kind']} literal={finding['literal']} type={finding['type']} SHA256={finding['sha256']}")
    for issue in report['issues']:
        lines.append(f"{issue['path']}:{issue['line'] or '?'}: {issue['kind']}: {issue['message']}")
    if report.get('error'):
        lines.append('ERROR: ' + report['error'])
    lines.extend('LIMIT: ' + item for item in report['limitations'])
    return '\n'.join(lines)


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    try:
        args = parser().parse_args(argv)
        report = audit(args)
    except AuditError as error:
        report = dict(version=VERSION, status='rejected', exit_code=2, findings=[], issues=[],
                      limitations=LIMITATIONS, error=str(error))
    print(json.dumps(report, indent=2, sort_keys=True, ensure_ascii=True) if '--json' in argv else human(report))
    return report['exit_code']


if __name__ == '__main__':
    raise SystemExit(main())
