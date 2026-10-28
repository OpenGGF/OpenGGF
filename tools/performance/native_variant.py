#!/usr/bin/env python3
"""Build one research variant from prepared native-image arguments.

Run through maven_queue.py in serial mode, separately from runtime measurement.
Does not regenerate the preservation contract or alter shipping inputs. Optional
class overlays replace existing entries in BOTH prepared engine jars (including
class resources); arbitrary additions are rejected. Output must be new.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-args', type=Path, required=True)
    p.add_argument('--native-image', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--classes', type=Path)
    p.add_argument('--sources', type=Path, help='Compile only these replacement Java sources')
    p.add_argument('--closed-world', action='store_true')
    p.add_argument('--option', action='append', default=[])
    a = p.parse_args()
    a.output = a.output.resolve()
    a.output.mkdir(parents=True, exist_ok=False)
    args = ['-O2', '-H:-DeleteLocalSymbols',
            *[json.loads(line) for line in a.base_args.read_text().splitlines()]]
    if a.sources:
        if a.classes:
            p.error('Choose --sources or --classes')
        a.classes = a.output / 'classes'
        a.classes.mkdir()
        sources = sorted(str(f.resolve()) for f in a.sources.rglob('*.java'))
        if not sources:
            p.error('Empty source overlay')
        subprocess.run([str(a.native_image.resolve().parent / 'javac'), '--release', '25',
                        '-g', '-sourcepath', '', '-cp', args[args.index('-cp') + 1],
                        '-d', str(a.classes), *sources], check=True)
    replacements = {}
    if a.classes:
        overlays = {str(f.relative_to(a.classes)): f.read_bytes()
                    for f in a.classes.rglob('*.class')}
        if not overlays:
            p.error('Empty class overlay')
        cp_index = args.index('-cp') + 1
        jars = [Path(s) for s in args[cp_index].split(':')
                if Path(s).name in ('preserved-engine.jar', 'engine-input.jar')]
        for jar in jars:
            out = a.output / jar.name
            with zipfile.ZipFile(jar) as src, zipfile.ZipFile(out, 'w') as dst:
                if jar.name == 'engine-input.jar' and not overlays.keys() <= set(src.namelist()):
                    raise ValueError('Overlay adds classes outside prepared engine')
                for info in src.infolist():
                    dst.writestr(info, overlays.get(info.filename, src.read(info)))
            replacements[str(jar)] = str(out)
        for old, new in replacements.items():
            args = [arg.replace(old, new) for arg in args]
    if a.closed_world:
        args = [s for s in args if s not in ('-H:+RuntimeClassLoading', '-H:-PreserveIncludesJNI')
                and not s.startswith(('-H:Preserve=', '--features=', '-J--add-exports='))]
        args = ['-H:IncludeResources=META-INF/openggf/bundled-mods.json'
                if s.startswith('-H:IncludeResources=') else s for s in args]
        args[-2] = 'com.openggf.tools.nativeimage.LegacyBenchmarkEngine'
    args[0:0] = a.option
    args[-1] = str(a.output / 'OpenGGF')
    argfile = a.output / 'native-image.args'
    argfile.write_text('\n'.join(json.dumps(s) for s in args) + '\n')
    started = time.monotonic()
    with (a.output / 'build.log').open('w') as log:
        result = subprocess.run(['/usr/bin/time', '-v', '-o', str(a.output / 'time.txt'),
                                 str(a.native_image.resolve()), '@' + str(argfile)],
                                stdout=log, stderr=subprocess.STDOUT)
    record = {'build_seconds': time.monotonic() - started, 'exit_code': result.returncode,
              'arguments': args, 'replacement_jars': replacements}
    if result.returncode == 0:
        binary = a.output / 'OpenGGF'
        subprocess.run(['objcopy', '--only-keep-debug', str(binary), str(binary) + '.symbols'], check=True)
        subprocess.run(['objcopy', '--strip-all', str(binary)], check=True)
        record.update(bytes=binary.stat().st_size,
                      sha256=hashlib.sha256(binary.read_bytes()).hexdigest())
    (a.output / 'build.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({k: v for k, v in record.items() if k != 'arguments'}), flush=True)
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
