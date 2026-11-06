#!/usr/bin/env python3
"""Install checksum-pinned Community 25.4 for the default native release jobs.

Origin: 2026-10-27 code-mod promotion. No system JDK changes or old-JDK fallback.
"""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import tarfile
import urllib.request
import zipfile

VERSION = '25.4.4.1.1+1.1'
BASE = 'https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.4.4.1.1/'
ASSETS = {
    ('Linux', 'x86_64'): ('linux-x64_bin.tar.gz', '05ccbbe783210b6886ff7b08fcd0b061c5dce4852b05db87284fc0e24abb08e2'),
    ('Windows', 'AMD64'): ('windows-x64_bin.zip', '2733ea1331f1a98b05dd55a768b07347051dad48f030dbbfa191b11a2ccf4144'),
    ('Darwin', 'arm64'): ('macos-aarch64_bin.tar.gz', '4ec9932aa4aab1afca0ea31982d32d6f956615c61fe21a227b49756b25adda4f'),
}

def install(output):
    asset, checksum = ASSETS[(platform.system(), platform.machine())]
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    archive = output / ('graalvm.zip' if asset.endswith('.zip') else 'graalvm.tar.gz')
    urllib.request.urlretrieve(BASE + 'graalvm-community-jdk-25i4-25.0.4.1.1_' + asset, archive)
    with archive.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != checksum:
            raise ValueError('GraalVM publisher checksum mismatch')
    if asset.endswith('.zip'):
        with zipfile.ZipFile(archive) as jar:
            for name in jar.namelist():
                if Path(name).is_absolute() or '..' in Path(name).parts: raise ValueError('Unsafe JDK archive')
            jar.extractall(output)
    else:
        with tarfile.open(archive) as tar: tar.extractall(output, filter='data')
    home = output / ('graalvm-community-' + VERSION)
    if platform.system() == 'Darwin': home /= 'Contents/Home'
    if not (home / 'release').is_file(): raise ValueError('Pinned JDK archive layout changed')
    if 'GITHUB_ENV' in os.environ:
        with open(os.environ['GITHUB_ENV'], 'a') as env: env.write(f'JAVA_HOME={home}\nGRAALVM_HOME={home}\n')
        with open(os.environ['GITHUB_PATH'], 'a') as paths: paths.write(str(home / 'bin') + '\n')
    print(home)
    return home

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    install(parser.parse_args().output)
