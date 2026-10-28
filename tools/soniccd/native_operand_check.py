#!/usr/bin/env python3
"""Verify explicit native-file span and address recipes without executing code.

Inputs are a canonical selected binary root and an external version-1 JSON recipe.
Only hashes, byte counts, offsets and computed addresses leave the process. There
is no extraction, assembler, CPU model, media traversal, repair or output file.
Origin: 2026-10-28 Sonic CD integration tooling; see soniccd-packet-integration skill.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

MAX_BINARY = 32 * 1024 * 1024
MAX_RECIPE = 256 * 1024
MAX_CHECKS = 512
MAX_LITERAL_BYTES = 64 * 1024
MAX_PATH = 4096
HASH = re.compile(r'[0-9a-f]{64}\Z')
HEX = re.compile(r'(?:[0-9a-f]{2})+\Z')
ID = re.compile(r'[A-Za-z][A-Za-z0-9_.-]{0,63}\Z', re.ASCII)
KINDS = {'absolute-long': (4, None), 'pc-relative-word': (2, 'pc_base'),
         'table-relative-word': (2, 'table_base')}


class CheckError(ValueError):
    def __init__(self, message, code=2):
        super().__init__(message)
        self.code = code


def require(condition, message, code=2):
    if not condition:
        raise CheckError(message, code)


def keys(value, expected, label):
    require(type(value) is dict and set(value) == set(expected), f'{label}: unexpected or missing fields')


def number(value, label, low=0, high=0xffffffff):
    require(type(value) is int and low <= value <= high, f'{label}: out-of-range integer')
    return value


def digest(value):
    return hashlib.sha256(value).hexdigest()


def signature(value):
    return (value.st_dev, value.st_ino, value.st_mode, value.st_size,
            value.st_mtime_ns, value.st_ctime_ns)


def canonical(value, *, directory=False):
    require(isinstance(value, (str, Path)), 'Path must be text')
    text = str(value)
    require(0 < len(text) <= MAX_PATH and '\0' not in text and '\\' not in text,
            'Invalid path')
    path = Path(text)
    require(path.is_absolute() and text == str(path) and '..' not in path.parts,
            'Require canonical absolute path')
    try:
        require(path.resolve(strict=True) == path, 'Symlink or noncanonical path')
        if directory:
            require(stat.S_ISDIR(path.lstat().st_mode), 'Selected root must be a directory')
    except (OSError, RuntimeError) as error:
        raise CheckError('Path unavailable') from error
    return path


def parents(path):
    result = []
    for item in path.parents:
        info = item.lstat()
        require(stat.S_ISDIR(info.st_mode), 'Input parent is not a real directory')
        result.append((item, signature(info)))
    return result


@dataclass
class Snapshot:
    path: Path
    data: bytes
    info: os.stat_result
    parent_ids: list


def _read(path, limit):
    path = canonical(path)
    try:
        before = path.lstat()
        require(stat.S_ISREG(before.st_mode), 'Input must be a regular file')
        require(before.st_size <= limit, 'Input exceeds byte limit')
        parent_ids = parents(path)
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, 'rb') as stream:
            require(signature(os.fstat(stream.fileno())) == signature(before), 'Input changed before read')
            data = stream.read(limit + 1)
            after = os.fstat(stream.fileno())
        require(len(data) == before.st_size <= limit and signature(after) == signature(before),
                'Input changed during read')
        value = Snapshot(path, data, before, parent_ids)
        _recheck(value)
        return value
    except OSError as error:
        raise CheckError('Cannot read regular input safely') from error


def _recheck(value):
    try:
        canonical(value.path)
        require(signature(value.path.lstat()) == signature(value.info), 'Input changed after read')
        for path, old in value.parent_ids:
            require(signature(path.lstat()) == old, 'Input parent changed')
    except OSError as error:
        raise CheckError('Input unavailable after read') from error


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key')
        result[key] = value
    return result


def reject_constant(value):
    raise CheckError('Nonfinite JSON number')


def decode(data):
    try:
        return json.loads(data.decode('utf-8'), object_pairs_hook=unique, parse_constant=reject_constant)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise CheckError('Invalid UTF-8 JSON recipe') from error


def array(value, label):
    require(type(value) is list and len(value) <= MAX_CHECKS, f'{label}: invalid count or array')
    return value


def hash_value(value, label):
    require(type(value) is str and HASH.fullmatch(value), f'{label}: require lowercase SHA256')


def hex_value(value, label):
    require(type(value) is str and len(value) <= MAX_LITERAL_BYTES * 2 and HEX.fullmatch(value),
            f'{label}: require nonempty lowercase byte hex')
    return bytes.fromhex(value)


def range_check(offset, length, size, label):
    number(offset, label + ' offset')
    number(length, label + ' bytes', 1, MAX_BINARY)
    require(offset <= size and length <= size - offset, f'{label}: range exceeds binary')
    return offset, offset + length


def nonoverlap(ranges, label):
    ordered = sorted(ranges)
    require(all(a[1] <= b[0] for a, b in zip(ordered, ordered[1:])), f'{label}: overlapping ranges')


def verify(recipe_path, root):
    """Return bounded observations; supplied pins and address bases remain caller claims."""
    require(os.name == 'posix' and hasattr(os, 'O_NOFOLLOW'), 'POSIX no-follow reads required', 3)
    root = canonical(root, directory=True)
    root_id = signature(root.lstat())
    recipe = _read(recipe_path, MAX_RECIPE)
    spec = decode(recipe.data)
    keys(spec, ('version', 'binary', 'spans', 'literals', 'operands'), 'recipe')
    require(type(spec['version']) is int and spec['version'] == 1, 'Unsupported recipe version', 3)
    binary = spec['binary']
    keys(binary, ('path', 'bytes', 'sha256'), 'binary')
    relative = binary['path']
    require(type(relative) is str and 0 < len(relative) <= MAX_PATH and '\\' not in relative and '\0' not in relative,
            'Invalid relative binary path')
    require(not relative.startswith('/') and all(part not in ('', '.', '..') for part in relative.split('/')),
            'Require explicit relative POSIX binary path')
    size = number(binary['bytes'], 'binary bytes', 1, MAX_BINARY)
    hash_value(binary['sha256'], 'binary sha256')
    spans = array(spec['spans'], 'spans')
    literals = array(spec['literals'], 'literals')
    operands = array(spec['operands'], 'operands')
    require(0 < len(spans) + len(literals) + len(operands) <= MAX_CHECKS, 'Require bounded nonempty checks')
    ids, prepared, category_ranges, literal_total = set(), [], {}, 0

    def identify(item, label):
        name = item.get('id') if type(item) is dict else None
        require(type(name) is str and ID.fullmatch(name) and name not in ids, f'{label}: invalid or duplicate id')
        ids.add(name)
        return name

    for item in spans:
        name = identify(item, 'span')
        keys(item, ('id', 'offset', 'bytes', 'sha256'), 'span')
        hash_value(item['sha256'], 'span sha256')
        start, end = range_check(item['offset'], item['bytes'], size, name)
        prepared.append(('span', name, start, end, item))
        category_ranges.setdefault('span', []).append((start, end))
    for item in literals:
        name = identify(item, 'literal')
        keys(item, ('id', 'offset', 'hex'), 'literal')
        raw = hex_value(item['hex'], name)
        literal_total += len(raw)
        start, end = range_check(item['offset'], len(raw), size, name)
        prepared.append(('literal', name, start, end, raw))
        category_ranges.setdefault('literal', []).append((start, end))
    for item in operands:
        name = identify(item, 'operand')
        kind = item.get('kind')
        require(type(kind) is str and kind in KINDS, 'Unsupported operand kind', 3)
        width, base_key = KINDS[kind]
        keys(item, ('id', 'kind', 'offset', 'width', 'encoded', 'expected_target') + ((base_key,) if base_key else ()), 'operand')
        require(type(item['width']) is int and item['width'] == width, f'{name}: wrong fixed operand width')
        raw = hex_value(item['encoded'], name)
        require(len(raw) == width, f'{name}: encoded width differs')
        literal_total += width
        number(item['expected_target'], name + ' target')
        if base_key:
            number(item[base_key], name + ' base')
        start, end = range_check(item['offset'], width, size, name)
        prepared.append(('operand', name, start, end, item))
        category_ranges.setdefault('operand', []).append((start, end))
    require(literal_total <= MAX_LITERAL_BYTES, 'Aggregate literal/encoded byte limit exceeded')
    for category, ranges in category_ranges.items():
        nonoverlap(ranges, category)

    actual = _read(root / relative, MAX_BINARY)
    require((actual.info.st_dev, actual.info.st_ino) != (recipe.info.st_dev, recipe.info.st_ino),
            'Recipe and binary must be distinct files')
    require(len(actual.data) == size and digest(actual.data) == binary['sha256'], 'Binary identity mismatch', 1)
    result = {'version': 1, 'status': 'verified',
              'recipe': {'path': str(recipe.path), 'bytes': len(recipe.data), 'sha256': digest(recipe.data)},
              'binary': {'path': str(actual.path), 'bytes': size, 'sha256': digest(actual.data)},
              'spans': [], 'literals': [], 'operands': [],
              'runtime_or_source_authentication': False,
              'limits': {'max_binary_bytes': MAX_BINARY, 'max_recipe_bytes': MAX_RECIPE,
                         'max_checks': MAX_CHECKS, 'max_literal_bytes': MAX_LITERAL_BYTES},
              'observation_limit': 'Non-atomic reads/rechecks; supplied bases, pins and source/region labels are not authenticated; no execution or coverage proof.'}
    for category, name, start, end, item in prepared:
        raw = actual.data[start:end]
        common = {'id': name, 'offset': start, 'bytes': end - start, 'sha256': digest(raw)}
        if category == 'span':
            require(digest(raw) == item['sha256'], f'{name}: span hash mismatch', 1)
            result['spans'].append(common)
        elif category == 'literal':
            require(raw == item, f'{name}: literal mismatch', 1)
            result['literals'].append(common)
        else:
            require(raw == bytes.fromhex(item['encoded']), f'{name}: operand encoding mismatch', 1)
            width, base_key = KINDS[item['kind']]
            value = int.from_bytes(raw, 'big', signed=base_key is not None)
            target = value + item[base_key] if base_key else value
            number(target, name + ' derived target')
            require(target == item['expected_target'], f'{name}: derived target mismatch', 1)
            common.update(kind=item['kind'], actual_target=target)
            if base_key:
                common.update(displacement=value)
                common[base_key] = item[base_key]
            result['operands'].append(common)
    result['cross_category_overlaps'] = sum(
        category != other and max(start, begin) < min(end, finish)
        for index, (category, _, start, end, _) in enumerate(prepared)
        for other, _, begin, finish, _ in prepared[index + 1:])
    _recheck(actual)
    _recheck(recipe)
    require(signature(root.lstat()) == root_id, 'Selected root changed')
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, help='Canonical absolute selected binary root')
    parser.add_argument('--recipe', required=True, help='Canonical absolute version-1 JSON recipe')
    args = parser.parse_args(argv)
    try:
        result = verify(args.recipe, args.root)
        print(json.dumps(result, sort_keys=True))
        return 0
    except CheckError as error:
        status = {1: 'mismatch', 2: 'invalid', 3: 'incomplete'}[error.code]
        print(json.dumps({'version': 1, 'status': status, 'error': str(error)[:300]}, sort_keys=True), file=sys.stderr)
        return error.code
    except (OSError, ValueError) as error:
        print(json.dumps({'version': 1, 'status': 'incomplete', 'error': type(error).__name__}), file=sys.stderr)
        return 3


if __name__ == '__main__':
    raise SystemExit(main())
