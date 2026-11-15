#!/usr/bin/env python3
"""CD-06 private PCM fixture constants and immutable input handling, 2026-10-23."""
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT = Path.home() / 'Downloads/Sonic-CD/native/pcm-fixtures'
LEDGER = Path.home() / 'Downloads/Sonic-CD/native/owner-ledger'
FROZEN = LEDGER / 'source'
SOURCE_PROVENANCE = '6bdb2680310ec4ffa7cf8d733ceb85d48d157ae1ad48915f4cde185630fde1ac'
TOOLCHAIN_PROVENANCE = 'd6a419fd4be690735967962d1afb664d0df91f1fa215597719c8108a9d435caa'
FROZEN_PINS = {
    'native_owner_build.py': '1eb54326f7b7c1e6bfe9a7d9b7ad596c54389d4dc0c4cb0d4bd46315ea279ba5',
    'oracle_boot.py': 'a80d516b4dca9d76ae95555e1b2e3347e014a4a79ee9c7720445fc8e624913d1',
    'oracle_probe.cs': '7bf4b522b82b689927aeabec54020ae28571518f5fcb0e3d2abf1b37db5ef92f',
}
RUNTIME_PINS = {
    'BizHawk.Common.dll': '438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33',
    'BizHawk.Emulation.Common.dll': 'f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4',
    'BizHawk.Emulation.Cores.dll': '0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7',
    'BizHawk.Emulation.DiscSystem.dll': 'a18f4ca3a7bb36aeea117dc36311664ad410b660d0052f55617055b3d6822d64',
    'BizHawk.BizInvoke.dll': '8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7',
    'Newtonsoft.Json.dll': 'e1e27af7b07eeedf5ce71a9255f0422816a6fc5849a483c6714e1b472044fa9d',
    'gpgx.wbx.zst': 'c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12',
    'libwaterboxhost.so': 'd2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78',
}
ASSEMBLIES = tuple(n for n in RUNTIME_PINS if n.endswith('.dll'))
COMPILER_PIN = '81e98ade50f3e4127237128211778bd6ebe0c3998c9cc2f5eb44f3196a0297f8'
UNALTERED_CORE = '35b82201fe676095872dfc04fcc43c661400bb6838c72b9962378bc0dc3590fe'
USA_MEDIA = '8d01adfb6898be7084f113b369488e39d2b447df8035711bc784ff3474e5f010'
USA_BIOS = 'f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed'
BLOCK_BYTES, STATE_BYTES, RAM_BYTES, SAMPLE_BYTES, CAPACITY = 262656, 224, 65536, 32, 4096


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def require_hash(path, expected):
    if sha(path) != expected:
        raise ValueError('Input identity mismatch: ' + str(path))


def frozen_module(name):
    path = FROZEN / (name + '.py')
    require_hash(path, FROZEN_PINS[path.name])
    spec = importlib.util.spec_from_file_location('pcm_frozen_' + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def fresh(path):
    path = Path(path).absolute()
    if path.resolve() != path or path == ROOT or not path.is_relative_to(ROOT) or path.exists():
        raise ValueError('Require a fresh real child under native/pcm-fixtures')
    path.mkdir(parents=True)
    return path


def write_json(path, data):
    Path(path).write_text(json.dumps(data, indent=2) + '\n')


def inventory(root):
    """Bind every input file, symlink target string and executable bit."""
    rows = {}
    for p in sorted(Path(root).rglob('*')):
        if p.is_symlink():
            rows[str(p.relative_to(root))] = {'link': p.readlink().as_posix()}
        elif p.is_file():
            rows[str(p.relative_to(root))] = {'sha256': sha(p), 'executable': bool(p.stat().st_mode & 0o111)}
    return rows
