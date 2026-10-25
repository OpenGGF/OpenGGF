#!/usr/bin/env python3
"""Declaration-only record policy probe. Input: Java source on stdin; exit 2 for other types.

Origin: 2026-10-22 object-pipeline performance work. The compiled signature test
remains authoritative; this lexical admission check excludes private state and
method bodies while retaining record components and visible declaration contracts.
"""
import re
import subprocess
import sys

TOKEN = re.compile(r'/\*[\s\S]*?\*/|//[^\n]*|"""(?:\\.|(?!""")[\s\S])*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[A-Za-z_$][\w$]*|[^\s]')
KINDS = {'record', 'class', 'interface', 'enum'}
TERMINAL_POLICY = 'src/main/java/com/openggf/mods/code/ModApiSignatureSurface.java'


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


def field_declaration(ts):
    """Keep every field declarator, excluding its executable initializer value."""
    result = []
    parens = brackets = braces = 0
    initializer = False
    for token in ts:
        if initializer and parens == brackets == braces == 0 and token == ',':
            initializer = False
        if not initializer:
            if token == '=' and parens == brackets == braces == 0:
                initializer = True
                continue
            result.append(token)
        if token == '(': parens += 1
        elif token == ')': parens -= 1
        elif token == '[': brackets += 1
        elif token == ']': brackets -= 1
        elif token == '{': braces += 1
        elif token == '}': braces -= 1
    return result


def enum_surface(ts, owner):
    """Retain constants, then inspect enum members with their own visibility."""
    result = []
    parens = brackets = 0
    end = 0
    while end < len(ts):
        token = ts[end]
        if token == '(': parens += 1
        elif token == ')': parens -= 1
        elif token == '[': brackets += 1
        elif token == ']': brackets -= 1
        elif parens == brackets == 0:
            if token == ';':
                return result + [';'] + member_surface(ts[end + 1:], owner)
            if token == '{':
                # An enum constant's anonymous implementation is executable code.
                end = closing(ts, end) + 1
                result.extend(['{', '}'])
                continue
        result.append(token)
        end += 1
    return result


def policy_blob(ref):
    """Read only the compared tree/index, including absence in historical trees."""
    if ref == 'EMPTY':
        return ''
    def git(*args):
        return subprocess.check_output(['git', *args], stderr=subprocess.PIPE).decode('utf-8')
    if ref == 'INDEX':
        entries = git('ls-files', '--stage', '-z', '--', TERMINAL_POLICY)
        spec = ':' + TERMINAL_POLICY
    else:
        entries = git('ls-tree', '-z', ref, '--', TERMINAL_POLICY)
        spec = ref + ':' + TERMINAL_POLICY
    if not entries:
        return ''
    records = entries.rstrip('\0').split('\0')
    if len(records) != 1 or not records[0].startswith(('100644 ', '100755 ')):
        raise ValueError('Unsupported terminal policy Git entry')
    if ref == 'INDEX' and records[0].split('\t')[0].split()[-1] != '0':
        raise ValueError('Unmerged terminal policy')
    return git('show', spec)


def terminal_names(source):
    """Accept the authoritative literal Set.of registry; never evaluate Java."""
    ts = tokens(source)
    if 'ENGINE_INTERNAL_TYPES' not in ts:
        return set()  # Historical repositories before the terminal policy existed.
    if '\\u' in source:
        raise ValueError('Unsupported Unicode escape in terminal policy')
    declarations = [i for i, t in enumerate(ts)
                    if t == 'ENGINE_INTERNAL_TYPES' and ts[i + 1:i + 2] == ['=']]
    if len(declarations) != 1:
        raise ValueError('Expected one terminal policy declaration')
    i = declarations[0]
    if (ts[max(0, i - 7):i] != ['private', 'static', 'final', 'Set', '<', 'String', '>']
            or ts[i + 1:i + 6] != ['=', 'Set', '.', 'of', '(']):
        raise ValueError('Unsupported terminal policy declaration')
    start = i + 5
    end = closing(ts, start, '(', ')')
    if ts[end + 1:end + 2] != [';']:
        raise ValueError('Unsupported terminal policy initializer')
    values = ts[start + 1:end]
    names = set()
    for j, value in enumerate(values):
        if j % 2:
            if value != ',':
                raise ValueError('Expected terminal policy separator')
        else:
            if not re.fullmatch(r'"com\.openggf(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+"', value):
                raise ValueError('Expected literal terminal policy type name')
            name = value[1:-1]
            if name in names:
                raise ValueError('Duplicate terminal policy type name')
            names.add(name)
    if values and len(values) % 2 == 0:
        raise ValueError('Trailing terminal policy separator')
    return names


def terminal_source(source, established):
    """Hide only nested terminal internals, retaining declaration/owner identity.

    Origin: 2026-10-25 audit remediation, native fixed-tail rewind prerequisite.
    Both hook implementations feed this projection to their existing conservative
    declaration probes. The compiled signature remains authoritative. Registry
    intersection prevents a newly classified type from exempting its own edit.
    """
    if not established:
        return source
    if '\\u' in source:
        raise ValueError('Unsupported Unicode escape in terminal source')
    matches = [m for m in TOKEN.finditer(source) if not m.group().startswith(('//', '/*'))]
    ts = [m.group() for m in matches]
    package = ''
    if 'package' in ts:
        start = ts.index('package') + 1
        end = ts.index(';', start)
        package = ''.join(ts[start:end])
        if not re.fullmatch(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*', package):
            raise ValueError('Unsupported Java package declaration')
    replacements = []

    def type_header(start, end):
        parens = 0
        for i in range(start, end):
            if ts[i] == '(':
                parens += 1
            elif ts[i] == ')':
                parens -= 1
            elif (parens == 0 and ts[i] in KINDS and i + 1 < end
                  and re.fullmatch(r'[A-Za-z_$][\w$]*', ts[i + 1])
                  and (i == start or ts[i - 1] != '.')):
                return i
        return None

    def visit(start, end, owners):
        header = start
        parens = brackets = 0
        initializer = False
        i = start
        while i < end:
            t = ts[i]
            if t == '(':
                parens += 1
            elif t == ')':
                parens -= 1
            elif t == '[':
                brackets += 1
            elif t == ']':
                brackets -= 1
            elif t == '{':
                stop = closing(ts, i)
                if parens == brackets == 0:
                    kind = None if initializer else type_header(header, i)
                    if kind is not None:
                        name = ts[kind + 1]
                        binary = package + '.' + '$'.join([*owners, name])
                        if owners and binary in established:
                            replacement = '() {}' if ts[kind] == 'record' else ' {}'
                            replacements.append((matches[kind + 1].end(), matches[stop].end(), replacement))
                        else:
                            visit(i + 1, stop, [*owners, name])
                    header = stop + 1
                    initializer = False
                i = stop
            elif parens == brackets == 0:
                if t == '=':
                    initializer = True
                elif t == ';':
                    header = i + 1
                    initializer = False
                elif t == '}':
                    raise ValueError('Unbalanced Java type declaration')
            if parens < 0 or brackets < 0:
                raise ValueError('Unbalanced Java header')
            i += 1
        if parens or brackets:
            raise ValueError('Incomplete Java header')

    visit(0, len(ts), [])
    for start, end, replacement in reversed(replacements):
        source = source[:start] + replacement + source[end:]
    return source


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
                            result.extend(enum_surface(body, name) if kind == 'enum'
                                          else member_surface(body, name, kind == 'interface'))
                        result.append('}')
                    start = stop + 1
                    break
                if t == ';':
                    header = ts[start:end]
                    if 'private' not in header and ('public' in header or 'protected' in header or implicit):
                        result.extend(field_declaration(header) + [';'])
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
        if len(sys.argv) == 4 and sys.argv[1] == '--terminal-source':
            old = terminal_names(policy_blob(sys.argv[2]))
            new = terminal_names(policy_blob(sys.argv[3]))
            print(terminal_source(sys.stdin.read(), old & new))
            sys.exit(0)
        surface = record_surface(sys.stdin.read())
        if surface is None: sys.exit(2)
        print(surface)
    except (ValueError, subprocess.CalledProcessError, UnicodeError) as failure:
        print('Cannot inspect Mod API declaration or terminal policy: ' + str(failure), file=sys.stderr)
        sys.exit(1)
