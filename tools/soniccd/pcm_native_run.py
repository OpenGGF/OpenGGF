#!/usr/bin/env python3
"""Capture bounded natural-gameplay native PCM fixtures with source/AV controls.

CD-06 2026-10-23. All outputs, managed compilation, complete RAM, native states
and compression remain in a fresh external pcm-fixtures child. No installed
core, Java, Maven, target, Git or TraceChaser writes. Inputs are authored normal
buttons only; observed PCM ordinals select diagnostics, never gameplay.
"""
import argparse
import gzip
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

from pcm_native_common import (ROOT, LEDGER, FROZEN, FROZEN_PINS, RUNTIME_PINS, ASSEMBLIES,
    COMPILER_PIN, SOURCE_PROVENANCE, TOOLCHAIN_PROVENANCE, USA_BIOS, USA_MEDIA, UNALTERED_CORE,
    frozen_module, fresh, require_hash, sha, write_json)
from pcm_native_build import inspect
from pcm_native_check import SCHEMA, SOURCE_PINS, validate_capture, summaries, uncompress

DEFAULT_INPUTS='600:Start,900:Start,1200:Start,1800:Start,1900:A,2100:Start,2400:Start'


def parse_inputs(text, frames):
    rows=[]
    for token in filter(None,text.split(',')):
        m=re.fullmatch(r'(\d+)(?:-(\d+))?:(Start|A|B|C|Up|Down|Left|Right)',token)
        if not m:
            raise ValueError('Invalid authored input: '+token)
        first,last=int(m[1]),int(m[2] or m[1])
        if not 1 <= first <= last <= frames:
            raise ValueError('Input outside bounded route')
        rows.append(dict(first=first,last=last,button=m[3]))
    return sorted(rows,key=lambda r:(r['first'],r['last'],r['button']))


def compress_outputs(run):
    for path in sorted(run.iterdir()):
        if path.suffix in ('.bin','.state'):
            (path.with_name(path.name+'.gz')).write_bytes(gzip.compress(path.read_bytes(),compresslevel=9,mtime=0))
            path.unlink()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bizhawk-home',type=Path,required=True)
    p.add_argument('--instrumented-build',type=Path,required=True)
    p.add_argument('--unaltered-build',type=Path,default=LEDGER / 'unaltered')
    p.add_argument('--cue',type=Path,required=True)
    p.add_argument('--firmware-directory',type=Path,required=True)
    p.add_argument('--bios-sha1',default=USA_BIOS)
    p.add_argument('--frames',type=int,default=3000)
    p.add_argument('--inputs',default=DEFAULT_INPUTS)
    p.add_argument('--select',default='',help='Observed pcm_run ordinals, ascending, at most eight')
    p.add_argument('--discover',action='store_true',help='Provisional enabled summaries only; no fixture/control claim')
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--timeout',type=int,default=240)
    args=p.parse_args()
    if not 1 <= args.frames <= 3360 or not 1 <= args.timeout <= 900 or args.bios_sha1 != USA_BIOS:
        p.error('Require bounded USA route, total native calls <=3600')
    selected=[int(n) for n in args.select.split(',') if n]
    if len(selected)>8 or selected!=sorted(set(selected)) or any(not 1 <= n <= 10000000 for n in selected):
        p.error('Require <=8 distinct ascending observed PCM call ordinals')
    if args.discover and selected:
        p.error('Discovery copies no wave RAM')
    inputs=parse_inputs(args.inputs,args.frames)
    dll=args.bizhawk_home.resolve(strict=True) / 'dll'
    for name,expected in RUNTIME_PINS.items():
        require_hash(dll / name,expected)
    compiler=Path('/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe')
    require_hash(compiler,COMPILER_PIN)
    oracle=frozen_module('oracle_boot')
    media=oracle.media_identity(args.cue)
    if media['source_fingerprint'] != USA_MEDIA:
        p.error('Require pinned USA retail media')
    source_identity=json.loads((args.unaltered_build / 'identity.json').read_text())
    if source_identity['patch_sha256'] is not None or source_identity['compressed_sha256'] != UNALTERED_CORE:
        p.error('Require independent pinned unaltered source core')
    require_hash(args.unaltered_build / 'gpgx.wbx.zst',UNALTERED_CORE)
    abi=inspect(args.instrumented_build)
    instrumented_identity=json.loads((args.instrumented_build / 'identity.json').read_text())
    tools=Path(__file__).resolve().parent
    producer_names=['pcm_native_probe.cs','pcm_native_run.py','pcm_native_check.py','pcm_native_common.py',
                    'pcm_native_build.py','pcm_native_patch.py','pcm_native_core.patch','pcm_native_observer.c','pcm_native_observer.h']
    paths=[tools / name for name in producer_names]+[FROZEN / n for n in FROZEN_PINS]
    producer={path.name:sha(path) for path in paths}
    for name in FROZEN_PINS:
        require_hash(FROZEN / name,FROZEN_PINS[name])
    if producer['pcm_native_core.patch'] != instrumented_identity['patch_sha256']:
        p.error('Instrumented core differs from this PCM patch')
    output=fresh(args.output)
    saved=output / 'producer'; saved.mkdir()
    for path in paths:
        shutil.copy2(path,saved / path.name)
        require_hash(saved / path.name,producer[path.name])
    write_json(output / 'inputs.json',inputs)
    write_json(output / 'runtime-pins.json',RUNTIME_PINS)
    build=output / 'build'; build.mkdir()
    exe=build / 'pcm_native.exe'
    command=['mono',str(compiler),'/nologo','/langversion:9','/deterministic+','/main:PcmNative',
             '/out:'+str(exe),'/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll']
    subprocess.run(command+['/reference:'+str(dll / name) for name in ASSEMBLIES]
                   +[str(saved / 'pcm_native_probe.cs'),str(saved / 'oracle_probe.cs')],check=True,timeout=30)
    modes={'enabled-1':'enabled'} if args.discover else {
        'official-1':'control','official-2':'control','source-1':'control','source-2':'control',
        'disabled-1':'disabled','enabled-1':'enabled','disabled-2':'disabled','enabled-2':'enabled','overflow':'overflow'}
    commands={}
    env=os.environ.copy()
    for name in ['DISPLAY','WAYLAND_DISPLAY']:
        env.pop(name,None)
    summaries_by_label={}
    for label,mode in modes.items():
        runtime=build / label; (runtime / 'dll').mkdir(parents=True)
        for file in dll.iterdir():
            if file.name not in ('gpgx.wbx','gpgx.wbx.zst'):
                (runtime / 'dll' / file.name).symlink_to(file,target_is_directory=file.is_dir())
        core=dll / 'gpgx.wbx.zst' if label.startswith('official') else (args.unaltered_build if label.startswith('source') else args.instrumented_build) / 'gpgx.wbx.zst'
        (runtime / 'dll/gpgx.wbx.zst').symlink_to(core.resolve(strict=True))
        shutil.copy2(exe,runtime / exe.name)
        child_env=dict(env,MONO_PATH=str(runtime / 'dll'),LD_LIBRARY_PATH=str(runtime / 'dll'),TMPDIR=str(runtime))
        selection=[] if mode=='overflow' else selected
        command=['mono',str(runtime / exe.name),str(args.cue.resolve()),str(args.firmware_directory.resolve()),
                 str(output / label),str(args.frames),str(output / 'inputs.json'),args.bios_sha1,sha(core),mode,
                 ','.join(map(str,selection)),str(output / 'runtime-pins.json')]
        commands[label]=(command,child_env)
        with (output / (label+'.log')).open('w') as log:
            subprocess.run(command,check=True,env=child_env,stdout=log,stderr=subprocess.STDOUT,timeout=args.timeout)
        compress_outputs(output / label)
        summaries_by_label[label]=json.loads((output / label / 'summary.json').read_text())
        (output / (label+'.log')).unlink()
        print('Completed '+label+': '+str(args.frames)+' calls + 120 forward + 120 restored',flush=True)
    negatives={}
    if not args.discover:
        command,child_env=commands['enabled-1']
        for label,index,value in [('wrong-core',8,'0'*64),('wrong-layout',9,'bad-layout')]:
            bad=command.copy(); bad[4]=str(output / label); bad[index]=value
            result=subprocess.run(bad,env=child_env,capture_output=True,text=True,timeout=args.timeout)
            expected='runtime identity mismatch' if label=='wrong-core' else 'ABI/layout mismatch'
            negatives[label]=result.returncode!=0 and expected in result.stderr and not (output / label).exists()
            if not negatives[label]:
                raise ValueError('Native negative control failed: '+label+': '+result.stderr[-1000:])
    for path in paths:
        require_hash(path,producer[path.name])
    for name,expected in RUNTIME_PINS.items():
        require_hash(dll / name,expected)
    streams={label:{file.name:{'bytes':file.stat().st_size,'sha256':sha(file)}
                   for file in sorted((output / label).iterdir()) if file.is_file()} for label in modes}
    manifest=dict(schema=SCHEMA,frames=args.frames,inputs=inputs,selected=selected,modes=modes,
        controls_complete=not args.discover,negative_controls=negatives,media=media,bios_sha1=args.bios_sha1,
        runtime_sha256=RUNTIME_PINS,compiler_sha256=COMPILER_PIN,executable_sha256=sha(exe),producer_sha256=producer,
        source_pins=SOURCE_PINS,source_provenance_sha256=SOURCE_PROVENANCE,toolchain_provenance_sha256=TOOLCHAIN_PROVENANCE,
        unaltered_identity=source_identity,instrumented_build=str(args.instrumented_build.resolve()),instrumented_identity=instrumented_identity,
        abi_sha256=sha(args.instrumented_build / 'abi.json'),abi=abi,summaries=summaries_by_label,streams=streams,
        display=None,audio_device=None,comparison='all declared device/RAM/output extents; no masks/tolerances',
        phase='pcm_run after integer limiter, before host volume/blip/resampling; unity host PCM volume verified')
    write_json(output / 'manifest.json',manifest)
    validate_capture(output,require_controls=not args.discover)
    rows=summaries(uncompress(output / 'enabled-1/calls.bin.gz',3600*4096*64),args.frames)
    write_json(output / 'observed-summary.json',dict(total_pcm_calls=len(rows),
        active_calls=sum(bool(r['enabled'] and r['status']) for r in rows),
        nonzero_calls=sum(r['nonzero_samples']>0 for r in rows),loop_calls=sum(r['loop_markers']>0 for r in rows),
        max_length=max((r['length'] for r in rows),default=0),selected=selected))
    print(output / 'manifest.json',flush=True)


if __name__=='__main__':
    main()
