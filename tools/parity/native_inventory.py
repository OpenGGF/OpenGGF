#!/usr/bin/env python3
"""Census original S1/S2/S3K dispatch tables without consulting engine registries.

Inputs: explicit absolute paths to the three SHA-1-matched original ROMs.
Output: deterministic unresolved native dispatch entries and reference provenance.
Origin: 2026-10-29 quantitative stock parity measurement implementation.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import struct
import sys

if __package__:
    from .report import ROMS, canonical, read_json
else:
    from report import ROMS, canonical, read_json


SCHEMA = 'openggf.native-dispatch-inventory.v1'
ROM_LENGTHS = {'s1': 0x80000, 's2': 0x100000, 's3k': 0x400000}
ALIAS_DEFINITION = 'Other native IDs in the same table with the same target; excludes this entry.'


@dataclass(frozen=True)
class TableSpecification:
    game: str
    table: str
    offset: int
    first_id: int
    last_id: int
    source_submodule: str
    source_pin: str
    citation: str
    context: str
    zone_ranges: tuple[tuple[int, int], ...] = ()

    @property
    def count(self):
        return self.last_id - self.first_id + 1


TABLES = (
    TableSpecification('s1', 'Obj_Index', 0xDA10, 1, 0x8C, 'docs/s1disasm',
        '28872a46e2d4c94c5c4818cbd3c04e6eb17edb67',
        'Obj_Index (_inc/Object Pointers.asm:5-156)', 'S1 level object dispatch'),
    TableSpecification('s2', 'Obj_Index', 0x1600C, 1, 0xDC, 'docs/s2disasm',
        '34fc7f130a96db171e1a1c43e28e0bad81cd6ca7',
        'Obj_Index (s2.asm:29908-30144)', 'S2 level object dispatch'),
    TableSpecification('s3k', 'Sprite_Listing3', 0x94EA2, 0, 0xFF, 'docs/skdisasm',
        '8b2cf3c4178f2382f844da67798de4befc50c5e0',
        'Sprite_Listing3 (Levels/Misc/Object pointers - SK Set 1.asm:11-266)',
        'S3KL table selected by unsigned zone byte, independently of mode',
        ((0x00, 0x06), (0x0E, 0x15))),
    TableSpecification('s3k', 'Sprite_ListingK', 0x952A2, 0, 0xB8, 'docs/skdisasm',
        '8b2cf3c4178f2382f844da67798de4befc50c5e0',
        'Sprite_ListingK (Levels/Misc/Object pointers - SK Set 2.asm:11-195)',
        'SKL table selected by unsigned zone byte, independently of mode',
        ((0x07, 0x0D), (0x16, 0xFF))),
)

LIMITS = [
    'Counts are native dispatch entries, not distinct objects, implementations or behavior obligations.',
    'Every entry remains an unresolved semantic subject, including shared targets and filler aliases.',
    'Dynamic children, fixed player/helper owners and internal routine transitions are not enumerated.',
    'A semantic contract for one facet does not close the remaining behavior inventory of its subject.',
    'S1/S2 ID 00 is an empty runtime slot outside these tables; S3K ID 00 is a real Ring entry.',
    'S1 IDs 02..07 share NullObject at 0xDC40, which falls through into ObjectFall under shipped FixBugs=0.',
    'S2 IDs 4C/4D/4E/4F/62/D0/D1 share ObjNull at 0x1637C, which branches to DeleteObject.',
    'S3K shared Ring targets remain contextual aliases; a shared target does not prove an ID unused.',
]


def decode_table(data: bytes, specification: TableSpecification):
    """Decode one bounded original table; ROM identity is enforced by generation."""
    spec = specification
    if not (spec.game in ROMS and 0 <= spec.first_id <= spec.last_id <= 255
            and spec.offset >= 0 and spec.offset % 2 == 0):
        raise ValueError('invalid dispatch table bounds')
    end = spec.offset + 4 * spec.count
    if end > len(data):
        raise ValueError(f'truncated dispatch table {spec.game}/{spec.table}')
    pointers = struct.unpack_from(f'>{spec.count}I', data, spec.offset)
    return native_rows(spec, pointers, len(data))


def native_rows(spec, pointers, rom_length):
    if len(pointers) != spec.count:
        raise ValueError('dispatch entry count differs from table bounds')
    aliases = defaultdict(list)
    for native_id, target in enumerate(pointers, spec.first_id):
        if type(target) is not int or target <= 0 or target % 2 or target >= rom_length:
            raise ValueError(f'invalid code pointer at {spec.game}/{spec.table}/{native_id:02X}')
        aliases[target].append(native_id)
    return [{
        'kind': 'native-dispatch-entry',
        'id': f'{spec.game}.{ROMS[spec.game]}.{spec.table}.{native_id:02X}',
        'game': spec.game, 'rom_sha1': ROMS[spec.game], 'table': spec.table,
        'native_id': native_id, 'entry_address': spec.offset + 4 * (native_id - spec.first_id),
        'target_address': target, 'alias_ids': [other for other in aliases[target] if other != native_id],
        'status': 'uncatalogued-subject',
    } for native_id, target in enumerate(pointers, spec.first_id)]


def table_metadata(spec, rows):
    return {
        'game': spec.game, 'table': spec.table, 'rom_sha1': ROMS[spec.game],
        'offset': spec.offset, 'exclusive_end': spec.offset + spec.count * 4,
        'first_id': spec.first_id, 'last_id': spec.last_id, 'entry_count': spec.count,
        'distinct_targets': len({row['target_address'] for row in rows}),
        'encoding': 'big-endian absolute 32-bit code pointers',
        'source_submodule': spec.source_submodule, 'source_pin': spec.source_pin,
        'citation': spec.citation, 'context': spec.context,
        'zone_ranges': [list(interval) for interval in spec.zone_ranges],
    }


def generator_identity():
    return {'path': 'tools/parity/native_inventory.py',
            'sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}


def validate_inventory(raw):
    """Validate structure/provenance; the caller must also pin artifact bytes."""
    if not isinstance(raw, dict) or raw.get('schema') != SCHEMA:
        raise ValueError('unsupported native dispatch inventory schema')
    if set(raw) != {'schema', 'generator', 'reference_roms', 'alias_ids_definition', 'limits', 'tables', 'subjects'}:
        raise ValueError('unknown or missing native inventory fields')
    expected_roms = {game: {'sha1': ROMS[game], 'byte_length': ROM_LENGTHS[game]} for game in ROMS}
    if canonical(raw.get('generator')) != canonical(generator_identity()) or canonical(raw.get('reference_roms')) != canonical(expected_roms):
        raise ValueError('native inventory generator or original ROM identity differs')
    if raw.get('limits') != LIMITS or raw.get('alias_ids_definition') != ALIAS_DEFINITION:
        raise ValueError('native inventory scope or alias definition differs')
    subjects = raw.get('subjects')
    if not isinstance(subjects, list) or len(subjects) != sum(spec.count for spec in TABLES):
        raise ValueError('native inventory must retain every original dispatch entry')
    position, expected_tables = 0, []
    for spec in TABLES:
        rows = subjects[position:position + spec.count]
        if not all(isinstance(row, dict) for row in rows):
            raise ValueError('native inventory subjects must be objects')
        reconstructed = native_rows(spec, [row.get('target_address') for row in rows], ROM_LENGTHS[spec.game])
        if canonical(rows) != canonical(reconstructed):
            raise ValueError('native inventory subject IDs, table addresses, status or aliases differ')
        expected_tables.append(table_metadata(spec, reconstructed))
        position += spec.count
    if canonical(raw.get('tables')) != canonical(expected_tables):
        raise ValueError('native inventory table specifications differ')
    return subjects


def generate_inventory(rom_paths):
    if set(rom_paths) != set(ROMS):
        raise ValueError('provide exactly the S1, S2 and S3K original ROMs')
    originals = {}
    for game, path in rom_paths.items():
        path = Path(path)
        if not path.is_absolute() or not path.is_file():
            raise ValueError(f'{game} requires an absolute existing original ROM path')
        data = path.read_bytes()
        if hashlib.sha1(data).hexdigest() != ROMS[game]:
            raise ValueError(f'wrong original ROM identity for {game}')
        originals[game] = data
    tables, subjects = [], []
    for spec in TABLES:
        rows = decode_table(originals[spec.game], spec)
        tables.append(table_metadata(spec, rows))
        subjects.extend(rows)
    return {
        'schema': SCHEMA,
        'generator': generator_identity(),
        'reference_roms': {game: {'sha1': ROMS[game], 'byte_length': len(originals[game])}
                           for game in ROMS},
        'alias_ids_definition': ALIAS_DEFINITION,
        'limits': LIMITS, 'tables': tables, 'subjects': subjects,
    }


def render_inventory(inventory):
    header = {key: value for key, value in inventory.items() if key != 'subjects'}
    beginning = json.dumps(header, indent=2, ensure_ascii=True, allow_nan=False)
    rows = ',\n'.join('    ' + json.dumps(row, separators=(',', ':'), allow_nan=False)
                      for row in inventory['subjects'])
    return beginning[:-2] + ',\n  "subjects": [\n' + rows + '\n  ]\n}\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for game in ROMS:
        parser.add_argument(f'--{game}-rom', type=Path, required=True)
    output = parser.add_mutually_exclusive_group()
    output.add_argument('--output', type=Path, help='Create a new inventory file; existing paths are refused.')
    output.add_argument('--check', type=Path, help='Regenerate and compare an existing inventory.')
    args = parser.parse_args(argv)
    try:
        inventory = generate_inventory({game: getattr(args, f'{game}_rom') for game in ROMS})
        rendered = render_inventory(inventory)
        if args.check:
            if read_json(args.check) != inventory or args.check.read_text() != rendered:
                raise ValueError('inventory differs from original-ROM regeneration')
            print(f'Original-ROM inventory matches: {len(inventory["subjects"])} dispatch entries.')
        elif args.output:
            with args.output.open('x', encoding='utf-8') as stream:
                stream.write(rendered)
        else:
            print(rendered, end='')
        return 0
    except (ValueError, OSError) as error:
        print(f'native inventory: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
