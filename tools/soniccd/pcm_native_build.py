#!/usr/bin/env python3
"""Build/inspect a separate CD-06 PCM observer, reusing frozen CD-02 build code.

2026-10-23. Inputs: complete pinned-r2 source, toolchain-r2, a NEW derived PCM
patch. Writes only a NEW external pcm-fixtures child. No installed-core, old
artifact, Git, Java, Maven, target or TraceChaser mutations.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

from pcm_native_common import (ROOT, LEDGER, FROZEN, SOURCE_PROVENANCE, TOOLCHAIN_PROVENANCE,
                               frozen_module, require_hash, sha, inventory, write_json)
from pcm_native_patch import generate

EXPORTS = {'pcm_native_' + n for n in ['abi', 'summary_bytes', 'block_bytes', 'capacity',
           'fault', 'dropped', 'count', 'ready', 'configure', 'poll', 'copy_block']}


def inspect(build):
    identity = json.loads((build / 'identity.json').read_text())
    core = build / 'gpgx.wbx'
    require_hash(core, identity['raw_sha256'])
    require_hash(build / 'gpgx.wbx.zst', identity['compressed_sha256'])
    sections = subprocess.check_output(['readelf', '-SW', str(core)], text=True)
    symbols = subprocess.check_output(['readelf', '-sW', str(core)], text=True)
    invis = re.search(r'^\s*\[\s*(\d+)\]\s+\.invis\s+PROGBITS\s+(\w+)\s+(\w+)\s+(\w+)', sections, re.M)
    objects = re.findall(r'^\s*\d+:\s+(\w+)\s+(\w+)\s+OBJECT\s+LOCAL\s+DEFAULT\s+(\d+)\s+pcm_observer$', symbols, re.M)
    if not invis or len(objects) != 1 or objects[0][2] != invis[1]:
        raise ValueError('PCM observer escaped .invis')
    exports = set(re.findall(r'^\s*\d+:\s+\w+\s+\w+\s+FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+(pcm_native_\w+)$', symbols, re.M))
    if exports != EXPORTS:
        raise ValueError('PCM ABI export set mismatch')
    address, size = int(objects[0][0], 16), int(objects[0][1], 0)
    start, extent = int(invis[2], 16), int(invis[4], 16)
    if not start <= address < address + size <= start + extent:
        raise ValueError('PCM observer extent exceeds .invis')
    return dict(schema='soniccd.pcm-native-abi.v1', raw_sha256=sha(core), exports=sorted(exports),
                invisible_index=int(invis[1]), invisible_address=start, invisible_bytes=extent,
                observer_address=address, observer_bytes=size, block_bytes=262656,
                state_bytes=224, ram_bytes=65536, sample_bytes=32, summary_bytes=64, capacity=4096)


def build(source, toolchain, output, patch, object_store):
    lever = frozen_module('native_owner_build')
    require_hash(source.parent / 'provenance.json', SOURCE_PROVENANCE)
    require_hash(toolchain / 'provenance.json', TOOLCHAIN_PROVENANCE)
    # Verify actual bytes against read-only pinned Git objects, not just labels
    # or an excerpt. This performs no Git writes or network access.
    counts = {}
    for name, (_, commit, tree, relative) in lever.PINS.items():
        lever.verify_commit(object_store / relative, commit, tree)
        counts[name] = lever.verify_files(object_store / relative, commit, source / relative)
    if counts != {'bizhawk': 6818, 'gpgx': 974, 'musl': 2671}:
        raise ValueError('Complete source verification count differs')
    for n, expected in [('clang', 'bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0'),
                        ('ld.lld', 'f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df')]:
        require_hash(toolchain / 'clang/usr/lib/llvm-16/bin' / n, expected)
    if patch.read_bytes() != generate(source).encode():
        raise ValueError('Derived PCM patch differs from current observer/source')
    before = {'source': inventory(source), 'sysroot': inventory(toolchain / 'sysroot')}
    # Rebind only the imported module's destination guard. Its frozen recipe
    # copies sources/sysroot, relocates wrappers in the NEW copy, make -j1.
    lever.ROOT = ROOT
    lever.build(source, toolchain, output, patch)
    if before != {'source': inventory(source), 'sysroot': inventory(toolchain / 'sysroot')}:
        raise ValueError('Read-only build inputs changed')
    write_json(output / 'input-inventory.json', before)
    identity = json.loads((output / 'identity.json').read_text())
    identity.update(pcm_recipe_sha256=sha(__file__), complete_source_verified_files=counts,
                    input_inventory_sha256=sha(output / 'input-inventory.json'),
                    frozen_lever_path=str(FROZEN / 'native_owner_build.py'),
                    source_pins={n: v[1] for n, v in lever.PINS.items()})
    write_json(output / 'identity.json', identity)
    write_json(output / 'abi.json', inspect(output))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, default=LEDGER / 'pinned-r2/source')
    p.add_argument('--toolchain', type=Path, default=LEDGER / 'toolchain-r2')
    p.add_argument('--object-store', type=Path, default=Path.home() / 'scratch/agent-tmp/codex/s2-native-authority-live-evidence-20260902/source-a')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--patch', type=Path, default=Path(__file__).with_name('pcm_native_core.patch'))
    a = p.parse_args()
    build(a.source.resolve(strict=True), a.toolchain.resolve(strict=True), a.output.absolute(), a.patch, a.object_store)
