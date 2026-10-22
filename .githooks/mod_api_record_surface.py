#!/usr/bin/env python3
"""Declaration-only record policy probe. Input: Java source on stdin; exit 2 for other types.

Origin: 2026-10-22 object-pipeline performance work. The compiled signature test
remains authoritative; this lexical admission check excludes private state and
method bodies while retaining record components and visible declaration contracts.
"""
import re
import sys

TOKEN = re.compile(r'/\*[\s\S]*?\*/|//[^\n]*|"""(?:\\.|(?!""")[\s\S])*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[A-Za-z_$][\w$]*|[^\s]')
KINDS = {'record', 'class', 'interface', 'enum'}


def tokens(source):
    return [m.group() for m in TOKEN.finditer(source)
            if not m.group().startswith(('//', '/*'))]


def closing(ts, start, left='{', right='}'):
    depth = 0
    for i in range(start, len(ts)):
        if ts[i] == left: depth += 1
        elif ts[i] == right:
            depth -= 1
            if depth == 0: return i
    raise ValueError('Unbalanced Java declaration')


def member_surface(ts, owner, implicit=False):
    result = []
    start = 0
    while start < len(ts):
        if ts[start] == ';': start += 1; continue
        parens = brackets = 0
        initializer = False
        end = start
        while end < len(ts):
            t = ts[end]
            if t == '(': parens += 1
            elif t == ')': parens -= 1
            elif t == '[': brackets += 1
            elif t == ']': brackets -= 1
            elif parens == brackets == 0:
                if t == '=': initializer = True
                if t == '{':
                    stop = closing(ts, end)
                    # Annotation-element array defaults are declaration values.
                    # A default interface method's body follows its parameter list,
                    # not the default keyword, and must still be omitted.
                    if initializer or (end > start and ts[end - 1] == 'default'):
                        end = stop + 1
                        continue
                    header = ts[start:end]
                    visible = 'private' not in header and ('public' in header or 'protected' in header or implicit)
                    # Compact constructors can omit explicit access; their annotations
                    # and access still belong to the declaration, never their body.
                    compact = bool(header) and header[-1] == owner
                    if visible or compact:
                        result.extend(header + ['{'])
                        nested = next((i for i, token in enumerate(header) if token in KINDS), None)
                        if nested is not None:
                            kind = header[nested]
                            name = header[nested + 1]
                            body = ts[end + 1:stop]
                            result.extend(body if kind == 'enum' else member_surface(body, name, kind == 'interface'))
                        result.append('}')
                    start = stop + 1
                    break
                if t == ';':
                    header = ts[start:end]
                    if 'private' not in header and ('public' in header or 'protected' in header or implicit):
                        result.extend(header + [';'])
                    start = end + 1
                    break
            end += 1
        else:
            if ts[start:]: raise ValueError('Incomplete Java record member')
            break
    return result


def record_surface(source):
    ts = tokens(source)
    parens = 0
    declaration = 0
    for i, t in enumerate(ts):
        if t == '(': parens += 1
        elif t == ')': parens -= 1
        elif t == ';' and parens == 0: declaration = i + 1
        elif t in KINDS and parens == 0:
            if t != 'record': return None
            owner = ts[i + 1]
            for j in range(i + 2, len(ts)):
                if ts[j] == '(': parens += 1
                elif ts[j] == ')': parens -= 1
                elif ts[j] == '{' and parens == 0:
                    end = closing(ts, j)
                    return ' '.join(ts[declaration:j + 1] + member_surface(ts[j + 1:end], owner) + ['}'])
            raise ValueError('Incomplete Java record header')
    return None


if __name__ == '__main__':
    try:
        surface = record_surface(sys.stdin.read())
        if surface is None: sys.exit(2)
        print(surface)
    except ValueError as failure:
        print(str(failure), file=sys.stderr)
        sys.exit(1)
