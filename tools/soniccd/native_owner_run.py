#!/usr/bin/env python3
"""Private CD-02 source-core controls; outputs only in native/owner-ledger.

2026-10-23: independent unaltered source vs official core, two cold boots,
120-frame restore, complete exposed RAM-domain/CPU views and per-frame AV.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess

from oracle_boot import ASSEMBLIES, COMPILER_SHA256, digest, media_identity
from device_observer_run import RUNTIME_PINS, verify_pins

ROOT = Path.home() / 'Downloads/Sonic-CD/native/owner-ledger'


def verify_run(run, frames, starts):
    summary = json.loads((run / 'summary.json').read_text())
    if (summary['frames'] != frames or not summary['is_mega_cd']
            or not summary['restore_120_frames_equal'] or not summary['observation_savestate_unchanged']):
        raise ValueError('Native control/restore failed')
    for name, expected in [('av.jsonl', list(range(1, frames + 1))),
                           ('continuation.jsonl', list(range(frames + 1, frames + 121)))]:
        rows = [json.loads(line) for line in (run / name).read_text().splitlines()]
        if [r['frame'] for r in rows] != expected:
            raise ValueError('Missing or unordered native frames: ' + name)
        if [r['frame'] for r in rows if r['start']] != [f for f in starts if f in expected]:
            raise ValueError('Native input differs: ' + name)
    return summary


def compare(left, right):
    for name in ['av.jsonl', 'views.jsonl', 'continuation.jsonl']:
        if digest(left / name) != digest(right / name):
            raise ValueError('Core behavior differs: ' + name)
    a, b = [json.loads((p / 'summary.json').read_text()) for p in [left, right]]
    for key in ['checkpoint_view', 'final_view', 'fps_num', 'fps_den']:
        if a[key] != b[key]:
            raise ValueError('Core behavior differs: ' + key)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bizhawk-home', type=Path, required=True)
    p.add_argument('--core-build', type=Path, required=True)
    p.add_argument('--instrumented-build', type=Path)
    p.add_argument('--reference-controls', type=Path)
    p.add_argument('--cue', type=Path, required=True)
    p.add_argument('--firmware-directory', type=Path, required=True)
    p.add_argument('--bios-sha1', required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--frames', type=int, default=1800)
    p.add_argument('--start-frames', default='600,900,1200')
    p.add_argument('--timeout', type=int, default=240)
    args = p.parse_args()
    output = args.output.absolute()
    if output.resolve() != output or output == ROOT or not output.is_relative_to(ROOT) or output.exists():
        p.error('Require fresh real child output in native/owner-ledger')
    if not 1 <= args.frames <= 3600 or args.timeout < 1:
        p.error('Invalid bounds')
    starts = sorted(set(int(f) for f in args.start_frames.split(',') if f))
    if any(f < 1 or f > args.frames for f in starts):
        p.error('Input outside bound')
    dll = args.bizhawk_home.resolve(strict=True) / 'dll'
    runtime = verify_pins(dll, RUNTIME_PINS)
    core_identity = json.loads((args.core_build / 'identity.json').read_text())
    if core_identity['patch_sha256'] is not None or core_identity['compressed_sha256'] != digest(args.core_build / 'gpgx.wbx.zst'):
        p.error('Require verified independently built unmodified core')
    instrumented_identity = None
    if args.instrumented_build:
        instrumented_identity = json.loads((args.instrumented_build / 'identity.json').read_text())
        if (not instrumented_identity['patch_sha256'] or instrumented_identity['compressed_sha256']
                != digest(args.instrumented_build / 'gpgx.wbx.zst') or not args.reference_controls):
            p.error('Require verified instrumented build and completed independent reference controls')
        reference = json.loads((args.reference_controls / 'verified-controls.json').read_text())
        if (not reference['all_av_ram_register_restore_controls_equal'] or reference['frames'] != args.frames
                or reference['start_frames'] != starts
                or reference['core_sha256']['source-1'] != core_identity['compressed_sha256']):
            p.error('Reference coverage/source identity mismatch')
    compiler = Path('/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe')
    if digest(compiler) != COMPILER_SHA256:
        p.error('Pinned Roslyn compiler mismatch')
    repo = Path(__file__).resolve().parents[2]
    tools = Path(__file__).resolve().parent
    sources = [tools / n for n in ['oracle_probe.cs', 'native_owner_probe.cs', 'native_owner_run.py',
               'oracle_boot.py', 'device_observer_run.py', 'native_owner_schema.py', 'native_owner_analyze.py']]
    before = {f.name: digest(f) for f in sources}
    media = media_identity(args.cue)
    output.mkdir(parents=True)
    saved = output / 'producer'
    saved.mkdir()
    for f in sources:
        (saved / f.name).write_bytes(f.read_bytes())
        if digest(saved / f.name) != before[f.name]:
            raise ValueError('Producer changed while snapshotting: ' + f.name)
    summaries = {}
    modes = ['disabled-1', 'enabled-1', 'disabled-2', 'enabled-2'] if args.instrumented_build else ['official-1', 'official-2', 'source-1', 'source-2']
    for mode in modes:
        build = output / 'build' / mode
        (build / 'dll').mkdir(parents=True)
        core = (args.instrumented_build if args.instrumented_build else args.core_build) / 'gpgx.wbx.zst'
        if mode.startswith('official'):
            core = dll / 'gpgx.wbx.zst'
        identity = digest(core)
        # Symlink individual immutable files into an owned runtime. Never
        # replace an installed file or symlink the whole installed directory.
        for f in dll.iterdir():
            if f.name not in ('gpgx.wbx', 'gpgx.wbx.zst'):
                (build / 'dll' / f.name).symlink_to(f, target_is_directory=f.is_dir())
        (build / 'dll/gpgx.wbx.zst').symlink_to(core.resolve(strict=True))
        exe = build / 'native_owner.exe'
        command = ['mono', str(compiler), '/nologo', '/langversion:9', '/deterministic+', '/main:NativeOwner',
                   f'/out:{exe}', '/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll']
        subprocess.run(command + [f'/reference:{dll / n}' for n in ASSEMBLIES]
                       + [str(saved / n) for n in ['oracle_probe.cs', 'native_owner_probe.cs']],
                       cwd=repo, check=True, timeout=30)
        env = os.environ.copy()
        for n in ['DISPLAY', 'WAYLAND_DISPLAY']:
            env.pop(n, None)
        env.update(MONO_PATH=str(build / 'dll'), LD_LIBRARY_PATH=str(build / 'dll'), TMPDIR=str(build))
        command = ['mono', str(exe), str(args.cue.resolve()), str(args.firmware_directory.resolve()),
                   str(output / mode), str(args.frames), ','.join(map(str, starts)), args.bios_sha1.lower(), identity,
                   mode.split('-')[0] if args.instrumented_build else 'control']
        with (output / (mode + '.log')).open('w') as f:
            subprocess.run(command, env=env, cwd=repo, check=True, timeout=args.timeout, stdout=f, stderr=subprocess.STDOUT)
        summaries[mode] = verify_run(output / mode, args.frames, starts)
        if args.instrumented_build:
            compare(args.reference_controls / 'official-1', output / mode)
        elif mode != 'official-1':
            compare(output / 'official-1', output / mode)
        print('Completed ' + mode + ': AV/domain/register/120-frame restore control', flush=True)
    # Native processes compile the immutable saved .cs files, not live files.
    changed_since_snapshot = [f.name for f in sources if before[f.name] != digest(f)]
    verify_pins(dll, RUNTIME_PINS)
    if args.instrumented_build:
        from native_owner_analyze import analyze
        for label in ['enabled-1', 'enabled-2']:
            analyze(output / label, args.frames)
        for name in ['events.bin', 'counts.jsonl', 'continuation-events.bin']:
            if digest(output / 'enabled-1' / name) != digest(output / 'enabled-2' / name):
                raise ValueError('Repeated owner ledger differs: ' + name)
    (output / 'manifest.json').write_text(json.dumps(dict(schema='soniccd.native-owner-controls.v1',
        media=media, bios_sha1=args.bios_sha1.lower(), producer_sha256=before, runtime_sha256=runtime,
        source_build_identity=core_identity, frames=args.frames, start_frames=starts, summaries=summaries,
        instrumented_build_identity=instrumented_identity,
        reference_controls_sha256=digest(args.reference_controls / 'verified-controls.json') if args.reference_controls else None,
        live_sources_changed_since_snapshot=changed_since_snapshot,
        official_source_av_views_restore_equal=True, raw_state_comparison='within identical binaries only; no masks',
        display=None, audio_device=None), indent=2) + '\n')
    print(output / 'manifest.json')


if __name__ == '__main__':
    main()
