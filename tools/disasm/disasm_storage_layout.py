"""Read-only literal storage-layout query, never an assembler or memory model.

Accept a finite caller-selected slice and origin. Only rs.b/w/l with unsigned
literal counts, literal rsset and argument-free rsreset are supported. An unknown
line stops the result as incomplete; the preceding prefix remains explicit.
No includes, expressions, EQU, ds, conditional or macro expansion is attempted.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile


MAX_SOURCE_BYTES = 1024 * 1024
MAX_OUTPUT_BYTES = 1024 * 1024
MAX_METADATA_BYTES = 64 * 1024
MAX_SLICE_LINES = 1000
MAX_LINE_BYTES = 4096
MAX_CLEARS = 16
OID = re.compile(r'(?:[0-9a-f]{40}|[0-9a-f]{64})\Z')
SHA256 = re.compile(r'[0-9a-f]{64}\Z')
IDENTIFIER = r'[A-Za-z_][A-Za-z0-9_]*'
LITERAL = re.compile(r'(?:\$[0-9A-Fa-f]+|0[xX][0-9A-Fa-f]+|[0-9]+)\Z')
RESERVE = re.compile(rf'(?:(?P<symbol>{IDENTIFIER}):?[ \t]+)?rs\.(?P<size>[bwl])[ \t]+(?P<count>\S+)[ \t]*\Z', re.I)
RESET = re.compile(r'rsset[ \t]+(\S+)[ \t]*\Z', re.I)
NAME = re.compile(r'[A-Za-z_][A-Za-z0-9_.-]{0,79}\Z')
RESERVED_SYMBOLS = frozenset('if ifdef ifndef ifeq ifne ifgt ifge iflt ifle else elseif endif '
                             'endc rept endr while endw macro macros endm include incbin '
                             'rsset rsreset equ equs set org phase dephase'.split())


class LayoutError(ValueError):
    pass


def literal(value):
    if not LITERAL.fullmatch(value) or len(value) > 32:
        raise LayoutError('expected an unsigned decimal, $hex or 0xhex literal of at most 32 characters')
    return int(value[1:], 16) if value.startswith('$') else int(value, 16 if value[:2].lower() == '0x' else 10)


def absolute_path(value, *, directory=False):
    if not value or any(ord(char) < 32 for char in value):
        raise LayoutError('source paths must be nonempty and contain no control characters')
    path = Path(value)
    if not path.is_absolute() or '..' in path.parts:
        raise LayoutError('source paths must be absolute existing paths without symlinks or ..')
    try:
        if path.resolve(strict=True) != path or (directory and not path.is_dir()):
            raise LayoutError('source paths must be absolute existing paths without symlinks')
    except (OSError, RuntimeError) as exc:
        raise LayoutError('missing or invalid source path') from exc
    return path


def relative_path(value):
    if not value or value.startswith('/') or any(ord(c) < 32 for c in value) or any(
            part in ('', '.', '..') for part in value.split('/')):
        raise LayoutError('--path requires a literal relative POSIX path without control, empty, . or .. components')
    return value


def validate_text(data):
    if not data or len(data) > MAX_SOURCE_BYTES:
        raise LayoutError(f'source must contain 1..{MAX_SOURCE_BYTES} bytes')
    if any(byte < 32 and byte not in (9, 10, 13) for byte in data) or b'\x7f' in data:
        raise LayoutError('source must be UTF-8 text without binary controls')
    try:
        data.decode('utf-8')
    except UnicodeDecodeError as exc:
        raise LayoutError('source must be UTF-8 text') from exc


def provenance(data, **fields):
    validate_text(data)
    return dict(fields, bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def load_file(filename, sha256):
    if not sha256 or not SHA256.fullmatch(sha256):
        raise LayoutError('--file requires an exact lowercase --sha256 content pin')
    path = absolute_path(filename)
    try:
        fd = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0) | getattr(os, 'O_NONBLOCK', 0))
        with os.fdopen(fd, 'rb') as source:
            metadata = os.fstat(source.fileno())
            if not stat.S_ISREG(metadata.st_mode) or not 1 <= metadata.st_size <= MAX_SOURCE_BYTES:
                raise LayoutError(f'--file must be a regular file of 1..{MAX_SOURCE_BYTES} bytes')
            data = source.read(MAX_SOURCE_BYTES + 1)
    except OSError as exc:
        raise LayoutError('source file could not be read') from exc
    result = provenance(data, kind='file', path=str(path), revision=None, blob=None)
    if result['sha256'] != sha256:
        raise LayoutError('source SHA256 does not match --sha256; no layout was computed')
    return result, data


class GitSource:
    def __init__(self, git_dir):
        path = absolute_path(git_dir, directory=True)
        self.prefix = ['git', '--no-pager', '--no-replace-objects', '--literal-pathspecs', '--git-dir', str(path)]
        self.env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        self.env.update(GIT_NO_LAZY_FETCH='1', GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL=os.devnull, LC_ALL='C')

    def run(self, *args, limit=MAX_METADATA_BYTES):
        # Only cat-file and exact ls-tree are called. File size is checked before
        # reading the sole blob. Temporary spools are invocation-local, never an index.
        with tempfile.TemporaryFile() as output, tempfile.TemporaryFile() as error:
            try:
                result = subprocess.run(self.prefix + list(args), env=self.env,
                                        stdout=output, stderr=error, timeout=30, check=False)
            except (OSError, subprocess.TimeoutExpired) as exc:
                raise LayoutError(f'Git {args[0]} unavailable or exceeded 30 seconds') from exc
            if output.tell() > limit or error.tell() > MAX_METADATA_BYTES:
                raise LayoutError(f'Git {args[0]} exceeded its {limit}-byte output bound')
            if result.returncode:
                raise LayoutError(f'Git {args[0]} failed (exit {result.returncode}); verify source pin/path')
            output.seek(0)
            return output.read()


def load_git(git_dir, *, revision=None, path=None, blob=None):
    if blob is not None:
        if revision is not None or path is not None or not OID.fullmatch(blob):
            raise LayoutError('--blob requires a full lowercase object ID and excludes --rev/--path')
    elif revision is None or path is None or not OID.fullmatch(revision):
        raise LayoutError('--rev requires a full lowercase commit object ID and exact --path')
    else:
        relative_path(path)
    source = GitSource(git_dir)
    if revision is not None:
        if source.run('cat-file', '-t', revision).strip() != b'commit':
            raise LayoutError('--rev must identify a commit object, not a tag, blob or tree')
        entries = source.run('ls-tree', '-z', revision, '--', path).split(b'\0')
        entries = [entry for entry in entries if entry]
        if len(entries) != 1:
            raise LayoutError('--path must name one exact regular source blob at --rev')
        try:
            info, filename = entries[0].split(b'\t', 1)
            mode, kind, blob = info.decode('ascii').split()
            if filename.decode('utf-8') != path or mode not in ('100644', '100755') or kind != 'blob' or not OID.fullmatch(blob):
                raise LayoutError('--path must name one exact regular source blob, not a symlink, tree or gitlink')
        except (UnicodeError, ValueError) as exc:
            raise LayoutError('invalid or nonregular Git tree entry') from exc
    if source.run('cat-file', '-t', blob).strip() != b'blob':
        raise LayoutError('--blob must identify a blob object')
    try:
        size = int(source.run('cat-file', '-s', blob))
    except ValueError as exc:
        raise LayoutError('invalid Git blob size') from exc
    if not 1 <= size <= MAX_SOURCE_BYTES:
        raise LayoutError(f'Git source blob must contain 1..{MAX_SOURCE_BYTES} bytes')
    data = source.run('cat-file', 'blob', blob, limit=MAX_SOURCE_BYTES)
    if len(data) != size:
        raise LayoutError('Git blob size changed or was truncated')
    object_bytes = b'blob ' + str(size).encode('ascii') + b'\0' + data
    object_hash = hashlib.sha1(object_bytes) if len(blob) == 40 else hashlib.sha256(object_bytes)
    if object_hash.hexdigest() != blob:
        raise LayoutError('Git blob content does not match its object ID')
    return provenance(data, kind='git_revision' if revision else 'git_blob',
                      git_dir=str(Path(git_dir)), revision=revision, path=path, blob=blob), data


def lines(data):
    """LF is the only line delimiter; retain byte offsets, CRLF and missing LF."""
    offset = 0
    while offset < len(data):
        newline = data.find(b'\n', offset)
        end = len(data) if newline < 0 else newline + 1
        yield offset, data[offset:end]
        offset = end


def scan(data, *, start_line, end_line, base, address_bits, max_records=256):
    validate_text(data)
    total_lines = data.count(b'\n') + (not data.endswith(b'\n'))
    if (not 1 <= start_line <= end_line <= total_lines
            or end_line - start_line + 1 > MAX_SLICE_LINES):
        raise LayoutError(f'select existing one-based start/end lines, at most {MAX_SLICE_LINES} lines')
    if address_bits not in (24, 32) or not 0 <= base < (1 << address_bits):
        raise LayoutError('--address-bits must be 24 or 32 and --base must fit without masking')
    if not 1 <= max_records <= 1024:
        raise LayoutError('--max-records must be 1..1024')
    result = {'status': 'complete', 'records': [], 'counter_events': [],
              'processed_through_line': start_line - 1}
    counter, basis, seen, aliases = base, 'caller_origin', set(), {}
    slice_start = slice_end = None
    for number, (offset, raw) in enumerate(lines(data), 1):
        if number < start_line:
            continue
        if number > end_line:
            break
        if slice_start is None:
            slice_start = offset
        slice_end = offset + len(raw)
    # Hash the whole requested finite slice, including unsupported suffix. Parsing
    # below stops on the first unsupported line; no source outside this slice is interpreted.
    selected = data[slice_start:slice_end]
    result['slice'] = {'start_line': start_line, 'end_line': end_line, 'total_lines': total_lines,
                       'byte_offset': slice_start, 'bytes': len(selected),
                       'sha256': hashlib.sha256(selected).hexdigest()}
    for number, (_, raw) in enumerate(lines(selected), start_line):
        text = raw.decode('utf-8').removesuffix('\n').removesuffix('\r')
        code = text.removeprefix('\ufeff') if number == 1 else text
        code = '' if code.lstrip(' \t').startswith('*') else code.split(';', 1)[0].strip(' \t')
        reason = None
        if len(raw) > MAX_LINE_BYTES:
            reason = f'line exceeds {MAX_LINE_BYTES} bytes'
        elif not code:
            pass
        elif code.lower() == 'rsreset':
            counter, basis = 0, 'literal_rsreset'
            result['counter_events'].append({'line': number, 'value': counter, 'basis': basis, 'source': text})
        elif RESET.fullmatch(code):
            try:
                value = literal(RESET.fullmatch(code).group(1))
                if value >= 1 << address_bits:
                    raise LayoutError('rsset exceeds the selected address space; no masking is performed')
                counter, basis = value, 'literal_rsset'
                result['counter_events'].append({'line': number, 'value': counter, 'basis': basis, 'source': text})
            except LayoutError as exc:
                reason = str(exc)
        elif match := RESERVE.fullmatch(code):
            symbol = match.group('symbol')
            try:
                count = literal(match.group('count'))
                width = {'b': 1, 'w': 2, 'l': 4}[match.group('size').lower()]
                end = counter + count * width
                if symbol and symbol.casefold() in RESERVED_SYMBOLS:
                    raise LayoutError('reserved directive cannot be interpreted as a storage symbol')
                if symbol and symbol.casefold() in seen:
                    raise LayoutError('duplicate symbol (case-insensitive safety check)')
                if len(result['records']) >= max_records:
                    raise LayoutError(f'record bound --max-records={max_records} exceeded')
                if end > 1 << address_bits or (count and counter >= 1 << address_bits):
                    raise LayoutError('storage range exceeds selected address space; no masking or wrapping is performed')
                result['records'].append({'symbol': symbol, 'line': number, 'source': text,
                                          'start': counter, 'end': end, 'size_bytes': end - counter,
                                          'element_bytes': width, 'count': count, 'basis': basis,
                                          'kind': 'storage' if count else 'address_marker'})
                if symbol:
                    seen.add(symbol.casefold())
                    aliases.setdefault(counter, []).append(symbol)
                counter = end
            except LayoutError as exc:
                reason = str(exc)
        else:
            reason = 'unsupported syntax; only literal rs.b/w/l, literal rsset and rsreset are interpreted'
        if reason:
            result['status'] = 'incomplete'
            result['unsupported'] = {'line': number, 'reason': reason, 'source': text[:MAX_LINE_BYTES],
                                      'line_bytes': len(raw), 'line_sha256': hashlib.sha256(raw).hexdigest(),
                                      'source_truncated': len(text) > MAX_LINE_BYTES}
            break
        result['processed_through_line'] = number
    result['end_counter'] = counter
    result['same_address_symbols'] = [{'address': address, 'symbols': symbols}
                                      for address, symbols in aliases.items() if len(symbols) > 1]
    return result


def clear_intervals(values, address_bits):
    if not 1 <= len(values) <= MAX_CLEARS:
        raise LayoutError(f'supply 1..{MAX_CLEARS} named --clear intervals')
    result, seen = [], set()
    for value in values:
        parts = value.split(':')
        if len(parts) != 3 or not NAME.fullmatch(parts[0]) or parts[0].casefold() in seen:
            raise LayoutError('--clear requires unique NAME:START:END with an ASCII identifier name')
        name, start, end = parts[0], literal(parts[1]), literal(parts[2])
        if not 0 <= start < end <= 1 << address_bits:
            raise LayoutError('--clear requires a nonempty half-open interval inside the selected address space')
        seen.add(name.casefold())
        result.append({'name': name, 'start': start, 'end': end})
    return result


def annotate_clears(result, clears):
    for record in result['records']:
        record['clears'] = []
        for clear in clears:
            start, end = record['start'], record['end']
            if start == end:
                relation = 'point_inside' if clear['start'] <= start < clear['end'] else 'point_outside'
            elif clear['start'] <= start and end <= clear['end']:
                relation = 'contained'
            elif start < clear['end'] and clear['start'] < end:
                relation = 'overlap'
            else:
                relation = 'disjoint'
            record['clears'].append({'name': clear['name'], 'relation': relation})


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise LayoutError(message)


def arguments(argv):
    parser = Parser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--file', help='absolute regular UTF-8 file; requires --sha256')
    source.add_argument('--git-dir', help='absolute existing Git directory; no checkout/fetch or symlinks')
    parser.add_argument('--sha256', help='exact lowercase SHA256 pin for --file')
    pin = parser.add_mutually_exclusive_group()
    pin.add_argument('--rev', help='full lowercase commit object ID; requires --path')
    pin.add_argument('--blob', help='full lowercase blob object ID; excludes --path')
    parser.add_argument('--path', help='exact literal repository-relative regular blob path for --rev')
    parser.add_argument('--start-line', type=int, required=True)
    parser.add_argument('--end-line', type=int, required=True)
    parser.add_argument('--base', required=True, help='explicit initial counter; literal, never masked')
    parser.add_argument('--origin-name', required=True, help='caller name/evidence label for the selected origin')
    parser.add_argument('--address-bits', type=int, required=True, choices=(24, 32))
    parser.add_argument('--clear', action='append', required=True, help='caller-named half-open NAME:START:END interval')
    parser.add_argument('--max-records', type=int, default=256, help='1..1024, default 256; excess is incomplete')
    return parser.parse_args(argv)


def main(argv=None):
    try:
        args = arguments(argv)
        if not NAME.fullmatch(args.origin_name):
            raise LayoutError('--origin-name requires an ASCII identifier label of at most 80 characters')
        clears = clear_intervals(args.clear, args.address_bits)
        base = literal(args.base)
        if args.file:
            if any(value is not None for value in (args.rev, args.blob, args.path)):
                raise LayoutError('--file excludes --rev, --blob and --path')
            evidence, data = load_file(args.file, args.sha256)
        else:
            if args.sha256 is not None:
                raise LayoutError('--sha256 applies only to --file; Git source uses immutable object IDs')
            evidence, data = load_git(args.git_dir, revision=args.rev, path=args.path, blob=args.blob)
        result = scan(data, start_line=args.start_line, end_line=args.end_line, base=base,
                      address_bits=args.address_bits, max_records=args.max_records)
        annotate_clears(result, clears)
        result.update(version=1, source=evidence,
                      origin={'name': args.origin_name, 'base': base, 'address_bits': args.address_bits},
                      clear_intervals=clears,
                      semantics='Source-only literal counter layout of an explicit slice; no enclosing-context, '
                                'assembly, alignment, include, macro, conditional, EQU or ds inference. '
                                'All ranges are half-open; markers cover zero bytes. '
                                'Caller origin/intervals are supplied data, not verified native execution.')
        encoded = json.dumps(result, ensure_ascii=True, indent=2, sort_keys=True)
        if len(encoded.encode('utf-8')) + 1 > MAX_OUTPUT_BYTES:
            raise LayoutError(f'report exceeds {MAX_OUTPUT_BYTES} bytes; narrow the slice/record/clear selection')
        code = 0 if result['status'] == 'complete' else 1
    except (LayoutError, UnicodeError) as exc:
        encoded = json.dumps({'version': 1, 'status': 'error', 'error': str(exc)[:1024]}, ensure_ascii=True, sort_keys=True)
        code = 2
    print(encoded)
    return code


if __name__ == '__main__':
    sys.exit(main())
