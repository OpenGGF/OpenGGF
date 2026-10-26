"""Read-only immutable-source lookup behind disasm_citations.py lookup.

Inputs: an existing Git directory, full commit ID, literal tree root and label.
Git grep finds candidate .asm blobs; only those blobs are parsed, without a
checkout, persistent index, assembler, runtime assets or network fallback.
Origin: 2026-10-26 private integration source-lookup task (guessed native paths).
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile


OID = re.compile(r'(?:[0-9a-f]{40}|[0-9a-f]{64})\Z')
LABEL = re.compile(r'[.@]?[A-Za-z_][A-Za-z0-9_.]*\Z')
LOCAL_DEF = re.compile(r'^([.@][A-Za-z_][A-Za-z0-9_.]*):')
TOKEN = re.compile(r'(?<![A-Za-z0-9_.@$])[A-Za-z_.@][A-Za-z0-9_.@]*(?![A-Za-z0-9_.@$])')
MAX_BLOB = 16 * 1024 * 1024
MAX_SOURCE = 64 * 1024 * 1024
MAX_METADATA = 1024 * 1024
MAX_OUTPUT = 1024 * 1024


class LookupError(ValueError):
    pass


class HitLimitExceeded(LookupError):
    pass


def relative_path(value, *, allow_dot=False):
    if allow_dot and value == '.':
        return value
    if not value or value.startswith('/') or '\0' in value or any(
            part in ('', '.', '..') for part in value.split('/')):
        raise LookupError('paths must be literal relative POSIX paths without empty, . or .. components')
    return value


class GitSource:
    def __init__(self, git_dir, revision):
        path = Path(git_dir)
        if not path.is_absolute() or '..' in path.parts:
            raise LookupError('--git-dir must be an absolute existing directory without symlinks or ..')
        try:
            resolved = path.resolve(strict=True)
        except (OSError, RuntimeError) as exc:
            raise LookupError('missing or invalid --git-dir') from exc
        if path != resolved or not path.is_dir():
            raise LookupError('--git-dir must be an existing directory without symlinks')
        if not OID.fullmatch(revision):
            raise LookupError('--rev requires a full lowercase commit object ID; refs and revision expressions are rejected')
        self.prefix = ['git', '--no-pager', '--no-replace-objects',
                       '--literal-pathspecs', '--git-dir', str(path)]
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        self.env.update(GIT_NO_LAZY_FETCH='1', GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL=os.devnull, LC_ALL='C')
        self.revision = revision
        if self.run('cat-file', '-t', revision).strip() != b'commit':
            raise LookupError('--rev must identify a commit object, not a tag, tree or blob')

    def run(self, *args, limit=MAX_METADATA, accept=(0,)):
        # Spool only invocation-local bytes, then fail explicitly over the bound.
        # No unbounded pipe allocation, shell, pager, textconv or lazy fetch.
        with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
            try:
                p = subprocess.run(self.prefix + list(args), env=self.env, stdout=out,
                                   stderr=err, timeout=30, check=False)
            except (OSError, subprocess.TimeoutExpired) as exc:
                raise LookupError(f'Git {args[0]} unavailable or exceeded 30 seconds') from exc
            if out.tell() > limit or err.tell() > MAX_METADATA:
                raise LookupError(f'Git {args[0]} exceeded the {limit}-byte output bound; narrow --root/--path')
            if p.returncode not in accept:
                raise LookupError(f'Git {args[0]} failed (exit {p.returncode}); verify the existing repository, commit and path')
            out.seek(0)
            return out.read()

    def entry(self, path):
        raw = self.run('ls-tree', '-z', self.revision, '--', path)
        entries = [item for item in raw.split(b'\0') if item]
        if len(entries) != 1:
            raise LookupError(f'path is missing or not exact at the commit: {path!r}')
        info, name = entries[0].split(b'\t', 1)
        mode, kind, oid = info.decode('ascii').split()
        if name.decode('utf-8') != path:
            raise LookupError(f'path is not exact at the commit: {path!r}')
        return mode, kind, oid

    def candidates(self, root, path, label):
        if root != '.' and self.entry(root)[:2] != ('040000', 'tree'):
            raise LookupError('--root must name a tree, not a file or symlink')
        if path is not None:
            if root != '.' and not path.startswith(root + '/'):
                raise LookupError('--path must be inside --root')
            mode, kind, _ = self.entry(path)
            if kind != 'blob' or mode not in ('100644', '100755'):
                raise LookupError('--path must name a regular .asm blob, not a symlink or gitlink')
            if not path.lower().endswith('.asm'):
                raise LookupError('--path must name an .asm source file')
        raw = self.run('grep', '--no-textconv', '--no-recurse-submodules',
                       '-l', '-z', '-F', '-e', label, self.revision, '--', path or root,
                       accept=(0, 1))
        prefix = (self.revision + ':').encode('ascii')
        result = []
        for item in raw.split(b'\0'):
            if not item:
                continue
            if not item.startswith(prefix):
                raise LookupError('unexpected Git grep path framing')
            name = item[len(prefix):].decode('utf-8')
            relative_path(name)
            if name.lower().endswith('.asm'):
                result.append(name)
        if len(result) > 256:
            raise LookupError('more than 256 candidate .asm files; narrow --root/--path')
        return sorted(set(result))

    def blob(self, path):
        mode, kind, oid = self.entry(path)
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise LookupError(f'candidate is not a regular source blob: {path!r}')
        if int(self.run('cat-file', '-s', oid)) > MAX_BLOB:
            raise LookupError(f'source blob exceeds {MAX_BLOB} bytes: {path!r}')
        data = self.run('cat-file', 'blob', oid, limit=MAX_BLOB)
        if any(b < 32 and b not in (9, 10, 13) for b in data):
            raise LookupError(f'source must be UTF-8 text without binary controls: {path!r}')
        try:
            data.decode('utf-8')
        except UnicodeDecodeError as exc:
            raise LookupError(f'source must be UTF-8 text: {path!r}') from exc
        return oid, data


def code_only(line):
    """Blank quoted strings and semicolon comments, preserving character offsets."""
    if line.lstrip().startswith('*'):
        return ''
    result, quote, escaped = [], None, False
    for char in line:
        if quote:
            result.append(' ')
            if char == quote and not escaped:
                quote = None
            escaped = char == '\\' and not escaped
        elif char == ';':
            break
        elif char in ('"', "'"):
            quote, escaped = char, False
            result.append(' ')
        else:
            result.append(char)
    return ''.join(result)


def lf_lines(data):
    """Yield (byte offset, raw line) lazily; only LF terminates a line."""
    offset = 0
    while offset < len(data):
        newline = data.find(b'\n', offset)
        end = len(data) if newline < 0 else newline + 1
        yield offset, data[offset:end]
        offset = end


def source_hits(data, label, scope, global_definition, remaining_hits):
    """Colon labels only; lexical per-file scope, never an assembled include graph."""
    owner, hits, number = None, [], 0
    for number, (offset, raw) in enumerate(lf_lines(data), 1):
        text = raw.decode('utf-8')
        if number == 1:
            text = text.removeprefix('\ufeff')
        code = code_only(text).lstrip()
        definition = LOCAL_DEF.match(code) or global_definition.match(code)
        if definition and not definition.group(1).startswith(('.', '@')):
            owner = {'label': definition.group(1), 'line': number}
        directive = code[definition.end():].lstrip() if definition else code
        if re.match(r'(?i)(?:include|incsrc)\s', directive):
            # An included file can introduce a global label. Its local scope is
            # unknown without assembling the include graph; don't inherit ours.
            owner = None
        if scope is not None and (owner is None or owner['label'] != scope):
            continue
        kinds = set()
        for token in TOKEN.finditer(code):
            if token.group() == label:
                kind = 'definition' if definition and token.start() == 0 else 'reference'
                if kind not in kinds:
                    if len(hits) + len(kinds) >= remaining_hits:
                        # Reject the first excess line/kind before allocating
                        # its hit or reading/splitting any later source lines.
                        raise HitLimitExceeded
                    kinds.add(kind)
        for kind in sorted(kinds):
            hits.append(({'line': number, 'kind': kind,
                          'scope': dict(owner) if label.startswith(('.', '@')) and owner else None}, offset))
    return number, hits


def context_window(data, hit, offset, total_lines, radius, remaining_bytes=None):
    """Find at most radius lines either side without retaining a line index."""
    start, end = max(1, hit['line'] - radius), min(total_lines, hit['line'] + radius)
    start_byte = offset
    for _ in range(hit['line'] - start):
        start_byte = data.rfind(b'\n', 0, start_byte - 1) + 1
    end_byte = offset
    for _ in range(end - hit['line'] + 1):
        newline = data.find(b'\n', end_byte)
        end_byte = len(data) if newline < 0 else newline + 1
    budget = MAX_OUTPUT if remaining_bytes is None else remaining_bytes
    if end_byte - start_byte > budget:
        raise LookupError('report exceeds context byte bound; narrow --context/--path')
    window = data[start_byte:end_byte]
    return {'start_line': start, 'end_line': end, 'total_lines': total_lines,
            'byte_offset': start_byte, 'bytes': len(window),
            'sha256': hashlib.sha256(window).hexdigest(),
            'text': window.decode('utf-8'), 'base64': base64.b64encode(window).decode('ascii')}


def lookup(args, global_definition):
    relative_path(args.root, allow_dot=True)
    if args.path is not None:
        relative_path(args.path)
    if not LABEL.fullmatch(args.label):
        raise LookupError('--label must be an exact ASCII assembly identifier')
    if args.scope is not None and (not LABEL.fullmatch(args.scope)
                                   or args.scope.startswith(('.', '@'))
                                   or not args.label.startswith(('.', '@'))):
        raise LookupError('--scope requires a global identifier and a local --label')
    if not 0 <= args.context <= 20 or not 1 <= args.max_hits <= 100:
        raise LookupError('--context must be 0..20; --max-hits must be 1..100')
    source = GitSource(args.git_dir, args.rev)
    hits, total_bytes, context_bytes = [], 0, 0
    for path in source.candidates(args.root, args.path, args.label):
        oid, data = source.blob(path)
        total_bytes += len(data)
        if total_bytes > MAX_SOURCE:
            raise LookupError(f'candidate source exceeds {MAX_SOURCE} bytes; narrow --root/--path')
        try:
            total_lines, matches = source_hits(data, args.label, args.scope, global_definition,
                                               args.max_hits - len(hits))
        except HitLimitExceeded:
            raise LookupError(f'more than --max-hits={args.max_hits}; narrow --root/--path/--scope or raise the bound') from None
        show = source.prefix + ['show', '--no-textconv', f'{source.revision}:{path}']
        file_sha256 = hashlib.sha256(data).hexdigest()
        for hit, offset in matches:
            window = context_window(data, hit, offset, total_lines, args.context,
                                    MAX_OUTPUT - context_bytes)
            context_bytes += window['bytes']
            hits.append(dict(hit, path=path, blob=oid, file_bytes=len(data),
                             file_sha256=file_sha256,
                             context=window,
                             git_show={'argv': show, 'shell': shlex.join(show)}))
    counts = {kind: sum(h['kind'] == kind for h in hits) for kind in ('definition', 'reference')}
    if counts['definition'] > 1:
        status = 'ambiguous'
    elif any(h['scope'] is None for h in hits) and args.label.startswith(('.', '@')):
        status = 'unresolved_scope'
    elif counts['definition'] == 1:
        status = 'found'
    else:
        status = 'references_only' if hits else 'no_match'
    return {'version': 1, 'status': status, 'git_dir': str(Path(args.git_dir)),
            'revision': args.rev, 'root': args.root, 'path': args.path,
            'label': args.label, 'scope': args.scope, 'counts': counts, 'hits': hits,
            'semantics': 'colon labels; exact case; lexical per-file local scope; no include/conditional expansion'}


def render(report, args):
    if args.json:
        return json.dumps(report, ensure_ascii=True, sort_keys=True, indent=2)
    output = [f"{report['status']}: {json.dumps(args.label)}"]
    if 'error' in report:
        output.append(report['error'])
    for hit in report.get('hits', []):
        output.append(f"{json.dumps(hit['path'])}:{hit['line']} {hit['kind']} scope={hit['scope']}")
        output.append(f"  blob={hit['blob']} sha256={hit['file_sha256']} bytes={hit['file_bytes']}")
        window = hit['context']
        output.append(f"  context={window['start_line']}-{window['end_line']}/{window['total_lines']} "
                      f"offset={window['byte_offset']} bytes={window['bytes']} sha256={window['sha256']}")
        for number, line in enumerate(window['text'].split('\n'), window['start_line']):
            if number <= window['end_line']:
                output.append(f'  {number}: {json.dumps(line)}')
        output.append('  git_show=' + json.dumps(hit['git_show']['shell']))
    return '\n'.join(output)


def command(args, global_definition):
    try:
        report = lookup(args, global_definition)
        encoded = render(report, args)
        if len(encoded.encode('utf-8')) + 1 > MAX_OUTPUT:
            raise LookupError(f'report exceeds {MAX_OUTPUT} bytes; narrow --root/--path/--scope or --context')
        code = 0 if report['status'] == 'found' else 1
    except (LookupError, UnicodeError) as exc:
        report = {'version': 1, 'status': 'error', 'error': str(exc)}
        encoded, code = render(report, args), 2
    print(encoded)
    return code


def add_parser(subparsers, global_definition):
    parser = subparsers.add_parser('lookup', help='find exact colon labels/references in immutable Git source',
                                  description='Source-only lookup: no checkout, assembler or ROM authentication. '
                                  'Ambiguous definitions stay ambiguous; local scopes are lexical per file.')
    parser.add_argument('--git-dir', required=True, help='absolute existing Git object repository; no symlinks')
    parser.add_argument('--rev', required=True, help='full lowercase commit ID; no HEAD, tags or expressions')
    parser.add_argument('--root', required=True, help='literal repository-relative tree root, or . for the entire tree')
    parser.add_argument('--label', required=True, help='exact case-sensitive colon label or code reference')
    parser.add_argument('--path', help='optional exact repository-relative .asm path within --root')
    parser.add_argument('--scope', help='optional preceding global colon label for a .local/@local query')
    parser.add_argument('--context', type=int, default=3, help='lines either side of each hit, 0..20 (default: 3)')
    parser.add_argument('--max-hits', type=int, default=20, help='1..100 (default: 20); exceeding fails without partial hits')
    parser.add_argument('--json', action='store_true', help='structured provenance, exact text and reproduction argv')
    parser.set_defaults(handler=lambda args: command(args, global_definition))
