#!/usr/bin/env python3
"""Check owner-ledger exports and the actual Waterbox invisible section.

CD-02 2026-10-23: read-only ELF inspection; writes derived provenance beside
the private source-built core. Section addresses/indices are never guessed.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

from native_owner_build import sha

EXPORTS = {'native_owner_' + n for n in ['abi_version', 'event_size', 'capacity',
            'fault', 'dropped', 'count', 'configure', 'poll']}


def inspect(build):
    core = build / 'gpgx.wbx'
    identity = json.loads((build / 'identity.json').read_text())
    if sha(core) != identity['raw_sha256'] or sha(build / 'gpgx.wbx.zst') != identity['compressed_sha256']:
        raise ValueError('Built core identity mismatch')
    sections = subprocess.check_output(['readelf', '-SW', str(core)], text=True)
    symbols = subprocess.check_output(['readelf', '-sW', str(core)], text=True)
    invisible = re.search(r'^\s*\[\s*(\d+)\]\s+\.invis\s+PROGBITS\s+(\w+)\s+(\w+)\s+(\w+)', sections, re.MULTILINE)
    owners = re.findall(r'^\s*\d+:\s+(\w+)\s+(\w+)\s+OBJECT\s+LOCAL\s+DEFAULT\s+(\d+)\s+owner$', symbols, re.MULTILINE)
    if invisible is None or len(owners) != 1 or owners[0][2] != invisible[1]:
        raise ValueError('Mutable ledger state escaped the invisible section')
    exports = set(re.findall(r'^\s*\d+:\s+\w+\s+\w+\s+FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+(native_owner_\w+)$', symbols, re.MULTILINE))
    if exports != EXPORTS:
        raise ValueError('Owner export set differs')
    return dict(schema='soniccd.native-owner-elf-abi.v1', core_sha256=identity['raw_sha256'],
                owner_address_hex=owners[0][0], owner_size= int(owners[0][1], 0),
                invisible_section_index=int(invisible[1]), invisible_address_hex=invisible[2],
                invisible_bytes=int(invisible[4], 16), exports=sorted(exports),
                event_bytes=256, event_words=64, event_capacity=8192,
                note='All mutable instrumentation bytes are the single owner object in .invis; raw-state mutation controls separately validate exclusion.')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build', type=Path, required=True)
    args = p.parse_args()
    result = inspect(args.build)
    (args.build / 'abi.json').write_text(json.dumps(result, indent=2) + '\n')
    print(args.build / 'abi.json')


if __name__ == '__main__':
    main()
