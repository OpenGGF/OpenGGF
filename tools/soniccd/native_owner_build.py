#!/usr/bin/env python3
"""Rebuild pinned, private CD-02 cores without changing any input checkout.

2026-10-23 owner-ledger task. Inputs: read-only complete Git object stores,
locked LLVM packages, external output. No Git writes, installs or retail data.
The unmodified and patched builds always have independent source/obj trees.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path.home() / 'Downloads/Sonic-CD/native/owner-ledger'
PINS = {
    'bizhawk': ('TASEmulators/BizHawk', '427556b5ef3ac437eba754d90c5e7e9096c9a8df',
                '7281227ed2f3b89c0962b2792b28539e35361c6b', ''),
    'gpgx': ('TASEmulators/Genesis-Plus-GX', '051d430d3d1b54625f9900c8f152d7f232e06daf',
             '1bb96ca74d660d383e70d9cd56b88906a0773519', 'waterbox/gpgx/Genesis-Plus-GX'),
    'musl': ('nattthebear/musl', '2063abc4e16c84218757b1db10d3cdf9f36ef3f8',
             'a9969a63cd1780cdcc4c09745a8789206a72b8b4', 'waterbox/musl'),
}
# Byte pins from the read-only TraceChaser prepare-toolchain recipe. Packages
# are unpacked locally, never installed. The host image is not attested.
PACKAGES = dict(line.split() for line in '''
clang-16_16.0.6-15_amd64.deb b9cd4d27a5d1b6c429fccf56a4ac1c4ac5baf2cb9b5a53e2a20fcd6593153e5a
libclang-cpp16_16.0.6-15_amd64.deb 39eb3e73119ef0180489c7e594d29398152b3a2d7eec2361cf87d367032f466a
libllvm16_16.0.6-15_amd64.deb 3353bbe1910cfc99a8ef96e1cd7df45c65e2aaebefcfc801bcb7587bab819a15
llvm-16-linker-tools_16.0.6-15_amd64.deb 39f6c47b5ecc04c064899a99d224650b2d932e7f27ac02246073395fc8bd1300
libclang-common-16-dev_16.0.6-15_all.deb ada57e3ac045bb324397c6d269dbad56a0b0f3608c89d321d1fed38206570ff5
lld-16_16.0.6-15_amd64.deb e75a2e784d2da2e3d90a31d7b8002892ac58b90e53073a14c7db1a8d80172204
libclang-rt-16-dev_16.0.6-15_amd64.deb 20f3b1a105d5b8fba261a03bd6ad531e09a87c929f33f54e5dd4db78f980dda2
libedit2_3.1-20221030-2_amd64.deb d1c26768f5e108c97d9520c8a19356ddf5a1967222af4f38efb1f5af21da46b5
libxml2_2.9.14+dfsg-1.3ubuntu0.1_amd64.deb 7c4d4ec04145f854bb824cb72fb34233c99f7db3eaafaa3d2049bd82800c0f85
libicu72_72.1-3ubuntu3_amd64.deb 3db0831a7a8da3c8d878fdbc4644d4131ed914b22c8a0cffbcabe68a2c3f6ec4
'''.strip().splitlines())


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def run(command, **kwargs):
    return subprocess.run(list(map(str, command)), check=True, **kwargs)


def fresh(path):
    path = Path(path).absolute()
    if path.resolve() != path or path == ROOT or not path.is_relative_to(ROOT):
        raise ValueError('Output must be a real child under owner-ledger, without symlink ancestors')
    path.mkdir(parents=True, exist_ok=False)
    return path


def git(repository, *args):
    return subprocess.check_output(['git', '--no-optional-locks', '-C', str(repository), *args])


def verify_commit(repository, commit, tree):
    data = git(repository, 'cat-file', 'commit', commit)
    actual = hashlib.sha1(b'commit ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    if actual != commit or not data.startswith(('tree ' + tree + '\n').encode()):
        raise ValueError('Pinned commit/tree mismatch')
    data = git(repository, 'cat-file', 'tree', tree)
    if hashlib.sha1(b'tree ' + str(len(data)).encode() + b'\0' + data).hexdigest() != tree:
        raise ValueError('Corrupt root tree')


def verify_files(repository, commit, output):
    """Check every tracked non-gitlink file against pinned Git blob identity."""
    count = 0
    records = git(repository, 'ls-tree', '-rz', commit).split(b'\0')
    for record in filter(None, records):
        prefix, name = record.split(b'\t', 1)
        mode, kind, identity = prefix.decode().split()
        if kind == 'commit':
            continue  # unrelated optional submodules are not build inputs
        path = output / os.fsdecode(name)
        data = os.readlink(path).encode() if mode == '120000' else path.read_bytes()
        actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        if actual != identity:
            raise ValueError('Pinned source blob differs: ' + str(path))
        if mode in ('100644', '100755') and bool(path.stat().st_mode & 0o111) != (mode == '100755'):
            raise ValueError('Source executable mode differs: ' + str(path))
        count += 1
    return count


def restore_archive_attributes(repository, commit, output):
    """git archive expands export-subst / omits export-ignore; restore blobs.

    Only the newly extracted destination is written. The object store and its
    potentially dirty working tree are always read-only inputs.
    """
    restored = 0
    for record in filter(None, git(repository, 'ls-tree', '-rz', commit).split(b'\0')):
        prefix, name = record.split(b'\t', 1)
        mode, kind, identity = prefix.decode().split()
        if kind != 'blob':
            continue
        path = output / os.fsdecode(name)
        data = os.readlink(path).encode() if path.is_symlink() else path.read_bytes() if path.is_file() else None
        if data is not None and hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == identity:
            continue
        data = git(repository, 'cat-file', 'blob', identity)
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.is_symlink():
            path.unlink()
        if mode == '120000':
            path.symlink_to(os.fsdecode(data))
        else:
            path.write_bytes(data)
            path.chmod(0o755 if mode == '100755' else 0o644)
        restored += 1
    return restored


def prepare(repository, output):
    out = fresh(output)
    sources = out / 'source'
    sources.mkdir()
    provenance = {}
    for name, (origin, commit, tree, relative) in PINS.items():
        repo = repository / relative
        verify_commit(repo, commit, tree)
        url = f'https://api.github.com/repos/{origin}/git/commits/{commit}'
        request = urllib.request.Request(url, headers={'User-Agent': 'SonicCD-private-owner-ledger'})
        with urllib.request.urlopen(request, timeout=60) as f:
            response = f.read()
        info = json.loads(response)
        if info['sha'] != commit or info['tree']['sha'] != tree:
            raise ValueError('Official source provenance differs')
        (out / (name + '-official-commit.json')).write_bytes(response)
        archive = out / (name + '.tar')
        with archive.open('wb') as f:
            run(['git', '--no-optional-locks', '-C', repo, 'archive', '--format=tar', commit], stdout=f)
        destination = sources / relative
        destination.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as f:
            f.extractall(destination, filter='data')
        restored = restore_archive_attributes(repo, commit, destination)
        files = verify_files(repo, commit, destination)
        provenance[name] = dict(repository=origin, commit=commit, tree=tree,
                                verified_files=files, archive_attribute_restorations=restored,
                                archive_sha256=sha(archive), official_url=url,
                                official_response_sha256=sha(out / (name + '-official-commit.json')))
    provenance['note'] = 'Complete tracked root, GPGX and musl archives, no copied working-tree files or .git; unrelated optional submodules uninitialized.'
    (out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print(out / 'provenance.json', flush=True)


def environment(toolchain):
    return dict(PATH=f'{toolchain}/clang/usr/bin:/usr/bin:/bin',
                LD_LIBRARY_PATH=f'{toolchain}/clang/usr/lib/x86_64-linux-gnu:{toolchain}/clang/usr/lib/llvm-16/lib',
                LC_ALL='C', TZ='UTC', SOURCE_DATE_EPOCH='1758367997', MAKEFLAGS='-j1')


def toolchain(source, packages, output):
    out = fresh(output)
    (out / 'native_owner_build.py').write_bytes(Path(__file__).read_bytes())
    for name, expected in PACKAGES.items():
        if sha(packages / name) != expected:
            raise ValueError('Locked package mismatch: ' + name)
    for name in PACKAGES:
        # ar/tar also work on hosts without Debian's package manager.
        members = subprocess.check_output(['ar', 't', packages / name], text=True).splitlines()
        data = [m for m in members if m.startswith('data.tar.')]
        if len(data) != 1:
            raise ValueError('Package data archive missing: ' + name)
        payload = out / 'package-data.tar'
        with payload.open('wb') as f:
            run(['ar', 'p', packages / name, data[0]], stdout=f)
        (out / 'clang').mkdir(exist_ok=True)
        run(['tar', '--no-same-owner', '-xf', payload, '-C', out / 'clang'])
        payload.unlink()
    (out / 'clang/usr/bin/ld.lld').symlink_to('ld.lld-16')
    for name, expected in [('clang', 'bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0'),
                           ('ld.lld', 'f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df')]:
        if sha(out / 'clang/usr/lib/llvm-16/bin' / name) != expected:
            raise ValueError('Pinned compiler/linker mismatch')
    work = out / 'work'
    shutil.copytree(source / 'waterbox/musl', work / 'musl', symlinks=True)
    env = environment(out)
    env.update(CC='clang-16', SYSROOT=str(out / 'sysroot'))
    with (out / 'build.log').open('w') as f:
        run(['./wbox_configure.sh'], cwd=work / 'musl', env=env, stdout=f, stderr=subprocess.STDOUT)
        # Upstream wbox_build.sh uses unbounded make -j. Use its two actual
        # build/install steps with a single worker instead.
        run(['make', '-j1'], cwd=work / 'musl', env=env, stdout=f, stderr=subprocess.STDOUT)
        run(['make', '-j1', 'install'], cwd=work / 'musl', env=env, stdout=f, stderr=subprocess.STDOUT)
    (out / 'sysroot/lib/linux').mkdir()
    shutil.copy2(out / 'clang/usr/lib/llvm-16/lib/clang/16/lib/linux/libclang_rt.builtins-x86_64.a', out / 'sysroot/lib/linux')
    manifest = dict(schema='soniccd.native-owner-toolchain.v1', package_sha256=PACKAGES,
                    package_source_directory=str(packages), musl_pin=PINS['musl'][1],
                    compiler_version=subprocess.check_output([str(out / 'clang/usr/bin/clang-16'), '--version'], env=env, text=True),
                    host_attested=False, parallelism=1)
    (out / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(out / 'provenance.json', flush=True)


def build(source, toolchain_path, output, patch):
    out = fresh(output)
    (out / 'native_owner_build.py').write_bytes(Path(__file__).read_bytes())
    src = out / 'source'
    shutil.copytree(source, src, symlinks=True)
    if patch:
        with patch.open('rb') as f:
            run(['patch', '--binary', '--batch', '--fuzz=0', '-p1'], cwd=src, stdin=f)
    shutil.copytree(toolchain_path / 'sysroot', src / 'waterbox/sysroot', symlinks=True)
    old, new = str(toolchain_path / 'sysroot'), str(src / 'waterbox/sysroot')
    for wrapper in (src / 'waterbox/sysroot/bin').iterdir():
        if wrapper.is_file() and not wrapper.is_symlink():
            wrapper.write_bytes(wrapper.read_bytes().replace(old.encode(), new.encode()))
    env = environment(toolchain_path)
    with (out / 'build.log').open('w') as f:
        run(['make', '-C', src / 'waterbox/emulibc', '-j1'], env=env, stdout=f, stderr=subprocess.STDOUT)
        run(['make', '-C', src / 'waterbox/gpgx', '-j1'], env=env, stdout=f, stderr=subprocess.STDOUT)
    shutil.copy2(src / 'waterbox/gpgx/obj/release/gpgx.wbx', out / 'gpgx.wbx')
    with (out / 'gpgx.wbx.zst').open('wb') as f:
        run(['zstd', '-19', '-T1', '--stdout', out / 'gpgx.wbx'], stdout=f)
    (out / 'symbols.txt').write_bytes(subprocess.check_output(['readelf', '-Ws', out / 'gpgx.wbx']))
    manifest = dict(schema='soniccd.native-owner-build.v1',
                    source_provenance_sha256=sha(source.parent / 'provenance.json'),
                    toolchain_provenance_sha256=sha(toolchain_path / 'provenance.json'),
                    patch_sha256=sha(patch) if patch else None,
                    raw_sha256=sha(out / 'gpgx.wbx'), compressed_sha256=sha(out / 'gpgx.wbx.zst'),
                    recipe_sha256=sha(__file__), parallelism=1)
    (out / 'identity.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(out / 'identity.json', flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    a = sub.add_parser('prepare')
    a.add_argument('--repository', type=Path, required=True)
    a.add_argument('--output', type=Path, required=True)
    a = sub.add_parser('toolchain')
    a.add_argument('--source', type=Path, required=True)
    a.add_argument('--packages', type=Path, required=True)
    a.add_argument('--output', type=Path, required=True)
    a = sub.add_parser('build')
    a.add_argument('--source', type=Path, required=True)
    a.add_argument('--toolchain', type=Path, required=True)
    a.add_argument('--output', type=Path, required=True)
    a.add_argument('--patch', type=Path)
    args = p.parse_args()
    if args.command == 'prepare':
        prepare(args.repository.resolve(strict=True), args.output)
    elif args.command == 'toolchain':
        toolchain(args.source.resolve(strict=True), args.packages.resolve(strict=True), args.output)
    else:
        build(args.source.resolve(strict=True), args.toolchain.resolve(strict=True), args.output, args.patch)


if __name__ == '__main__':
    main()
