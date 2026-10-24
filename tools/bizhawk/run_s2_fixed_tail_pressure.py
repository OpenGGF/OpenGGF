#!/usr/bin/env python3
"""2026-10-24 Sideways research: build/run the bounded, comparison-only C# probe.

Inputs are an explicit BizHawk 2.11 installation and original S2 REV01/CPZ2 BK2.
All generated sources, binaries and observations stay in an owned external root.
No engine hydration, canonical publication, desktop, native patch or lock changes.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import time

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/testing'))
from maven_activity import build_activity_slot
from maven_queue import handle_termination, inherited_slot, maven_slot, worktree_build_slot

PIN = '322851cbfa2e1c1803332142e11ab74b4eb34eea'
NATIVE = '34fc7f130a96db171e1a1c43e28e0bad81cd6ca7'
CASES = ('live-last', 'empty-last', 'reinstall-last', 'live-two', 'empty-two', 'reinstall-two')


def sha(path, algorithm='sha256'):
    h = hashlib.new(algorithm)
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def command(args, env, log, leases, cwd, timeout=180):
    start = time.monotonic()
    with log.open('w') as output:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=output,
                                stderr=subprocess.STDOUT, timeout=timeout,
                                **inherited_slot(leases))
    return dict(command=list(map(str, args)), exit_code=result.returncode,
                execution_seconds=time.monotonic() - start, log=str(log))


def verify_output(output, case, rom):
    """Require completion markers, exact contexts/counts and unbroken observation identity."""
    summary = json.loads((output / 'summary.json').read_text())
    data = output / 'observations.jsonl'
    if not data.stat().st_size or sha(data) != summary['observations_sha256']:
        raise ValueError('Missing/changed observations')
    events = [json.loads(line) for line in data.read_text().splitlines()]
    if summary['execution_status'] != 'completed' or summary['case'] != case or summary['unmet_assertions']:
        raise ValueError('Case is not completed with all asserted paths')
    if [x['sequence'] for x in events] != list(range(1, len(events) + 1)):
        raise ValueError('Observation sequence discontinuity')
    frames = [x for x in events if x['boundary'] == 'frame_end']
    first = summary['setup_input_row']
    if [x['input_row'] for x in frames] != list(range(first, first + 361)):
        raise ValueError('Incomplete finite input window')
    if [x['completed_frame'] for x in frames] != list(range(frames[0]['completed_frame'], frames[0]['completed_frame'] + 361)):
        raise ValueError('Hardware-frame discontinuity')
    parent_count = 2 if case.endswith('two') else 1
    for name in ('parent_init', 'allocator_call', 'allocator_return', 'load_subobject',
                 'post_failure_writes', 'parent_retire', 'physical_tail_delete', 'pair_clear_confirmed'):
        if sum(x['boundary'] == name for x in events) != parent_count:
            raise ValueError('Missing/excess boundary: ' + name)
    for event in events:
        if event['boundary'] == 'allocator_return':
            regs = event['registers']
            if regs['pc'] != 0x18014 or regs['a1'] != 0xFFFFD000 or regs['caller'] != 0x2940C or regs['sr'] & 4:
                raise ValueError('Wrong exhausted-allocation CPU/caller context')
    # LoadTailsTailsDynPLC/TPLC_ReadEntry (pinned s2.asm:41637-41693).
    # Compare original-ROM descriptors with observed requests, never engine input.
    rom_bytes = rom.read_bytes()
    if hashlib.sha1(rom_bytes).hexdigest() != '8bca5dcef1af3e00098666fd892dc1c2a76333f9':
        raise ValueError('DPLC verification requires original S2 REV01')
    word = lambda address: int.from_bytes(rom_bytes[address:address+2], 'big')
    expected = None
    requests = []
    decisions = 0
    for event in events:
        boundary = event['boundary']
        if boundary == 'tail_dplc_entry':
            if expected is not None:
                raise ValueError('Overlapping tail DPLC decisions')
            frame = bytes.fromhex(event['tail'])[0x1A]
            expected = []; requests = []
            if frame != event['cache']:
                cursor = 0x7446C + int.from_bytes(rom_bytes[0x7446C+frame*2:0x7446E+frame*2], 'big', signed=True)
                entries = word(cursor)
                if entries > 64:
                    raise ValueError('Unbounded/unrecognised DPLC descriptor')
                destination = 0xF600
                for index in range(entries):
                    entry = word(cursor + 2 + index*2)
                    words = ((entry >> 12) + 1)*16
                    expected.append((0x64320 + ((entry & 0xFFF) << 5), destination, words))
                    destination += words*2
        elif boundary == 'tail_dma_request':
            detail = event['detail']
            # QueueDMATransfer consumes D2.w/D3.w; retain full raw registers in capture.
            requests.append((detail['source'], detail['destination'] & 0xFFFF, detail['words'] & 0xFFFF))
        elif boundary == 'tail_dplc_return':
            if expected != requests or event['cache'] != frame:
                raise ValueError('DPLC/cache/request evidence differs from original-ROM descriptor')
            decisions += 1; expected = None
    if expected is not None:
        raise ValueError('Unfinished tail DPLC decision')
    if case.startswith(('live', 'reinstall')) and not decisions:
        raise ValueError('Missing positive tail DPLC path')
    return dict(execution_status='completed', observations_sha256=sha(data), events=len(events), frames=len(frames))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--task-root', required=True, type=Path)
    parser.add_argument('--bizhawk-home', required=True, type=Path)
    parser.add_argument('--rom', required=True, type=Path)
    parser.add_argument('--movie', required=True, type=Path)
    parser.add_argument('--phase', choices=('prepare', 'run'), required=True)
    parser.add_argument('--case', action='append', choices=CASES)
    args = parser.parse_args()
    for path in (args.task_root, args.bizhawk_home, args.rom, args.movie):
        if not path.is_absolute() or path.is_symlink():
            parser.error('Inputs/root must be absolute, nonsymlink paths')
    task = args.task_root
    if task == ROOT or ROOT in task.parents or task in ROOT.parents:
        parser.error('Task root must be outside the source checkout')
    marker = task / 'ownership.json'
    if not task.exists():
        if args.phase != 'prepare':
            parser.error('Prepare an absent external root first')
        task.mkdir(mode=0o700)
        save(marker, dict(task='S2 fixed-tail native pressure 2026-10-24', uid=os.getuid(),
                          worktree=str(ROOT), tracechaser=PIN, s2disasm=NATIVE))
    owner = json.loads(marker.read_text())
    if task.stat().st_uid != os.getuid() or owner.get('uid') != os.getuid() or owner.get('worktree') != str(ROOT) or owner.get('tracechaser') != PIN:
        parser.error('External ownership mismatch; preserve contents')
    if sha(args.rom, 'sha1') != '8bca5dcef1af3e00098666fd892dc1c2a76333f9':
        parser.error('Original ROM is not expected S2 REV01')
    env = os.environ.copy()
    for name in ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    stage = task / 'tracechaser'
    env.update(BIZHAWK_HOME=str(args.bizhawk_home), PYTHONDONTWRITEBYTECODE='1',
               MONO_PATH=str(stage / 'bizhawk-headless/bin/Release') + ':' + str(args.bizhawk_home / 'dll'),
               LD_LIBRARY_PATH=str(args.bizhawk_home / 'dll'))
    source = ROOT / 'tools/bizhawk/S2FixedTailPressureProbe.cs'
    exe = task / 'S2FixedTailPressureProbe.exe'
    if args.phase == 'prepare':
        if not stage.exists():
            stage.mkdir()
            archive = subprocess.check_output(['git', '-C', str(ROOT / 'tools/tracechaser'),
                                              'archive', PIN, 'bizhawk', 'bizhawk-headless', 'dependencies', 'docs'])
            with tarfile.open(fileobj=io.BytesIO(archive)) as files:
                files.extractall(stage, filter='data')
        timings = {}; started = time.monotonic()
        with handle_termination(), worktree_build_slot(ROOT) as tree:
            with build_activity_slot(ROOT, timings=timings) as activity:
                timings['lease_wait_seconds'] = time.monotonic() - started
                leases = (tree,) + tuple(activity)
                records = []
                for name, cmd in (
                    ('explicit-preflight', ['bash', str(stage / 'bizhawk/preflight_bizhawk_2_11.sh'), '--bizhawk-home', str(args.bizhawk_home)]),
                    ('pinned-host-build', ['bash', str(stage / 'bizhawk-headless/build.sh')]),
                    ('probe-compile', ['mono', '/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe', '/nologo', '/deterministic+',
                                      '/langversion:7.3', '/out:' + str(exe),
                                      '/reference:' + str(stage / 'bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe'),
                                      *['/reference:' + str(args.bizhawk_home / 'dll' / name) for name in
                                        ('BizHawk.Emulation.Cores.dll', 'BizHawk.Emulation.Common.dll', 'Newtonsoft.Json.dll')],
                                      '/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll', str(source)])):
                    record = command(cmd, env, task / (name + '.log'), leases, stage)
                    records.append(record); print(json.dumps(record), flush=True)
                    if record['exit_code']:
                        save(task / 'prepare.json', dict(execution_status='failed', timings=timings, commands=records))
                        return record['exit_code']
        save(task / 'prepare.json', dict(execution_status='completed', timings=timings, commands=records,
             source_sha256=sha(source), host_sha256=sha(stage / 'bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe'),
             probe_sha256=sha(exe), tracechaser=PIN, native=NATIVE))
        return 0
    prepared = json.loads((task / 'prepare.json').read_text())
    if (prepared['execution_status'] != 'completed' or prepared['source_sha256'] != sha(source)
            or prepared['probe_sha256'] != sha(exe)
            or prepared['host_sha256'] != sha(stage / 'bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe')):
        parser.error('Prepared probe fingerprint does not match current source/binary')
    run = task / time.strftime('run-%Y%m%dT%H%M%SZ', time.gmtime())
    run.mkdir()  # no reuse of an existing output
    save(run / 'setup-manifest.json', dict(
        authority='declared diagnostic construction, comparison-only; not natural-route/canonical fixture authority',
        tracechaser=PIN, native=NATIVE, probe_source_sha256=sha(source),
        ingress='original CPZ2 BK2 from power-on; native level/zone/act, unlocked player, live initialized Sidekick/tail, ID-empty final parent slots',
        limits=dict(max_ingress_frames=11000, post_setup_frames=360, per_process_timeout_seconds=180),
        setup_boundary='RunObject $15FCC before the selected final dynamic slot ID read, after Sidekick dispatch',
        writes=[
            'Clear each ID-empty selected 64-byte parent SST; construct ID7A, subtype06, routine0.',
            'First original X = Camera_X_pos + $A8; next original X += $80; Y = Camera_Y_pos + $200.',
            'Set respawn_index $BD (last) or $BC (penultimate); set its initially-unused bit7 at Obj_respawn_data + index.',
            'Empty/reinstall cases clear all64 fixed-tail bytes once. DPLC cache and DMA state are untouched.',
            'Reinstall cases separately set Sidekick routine byte to0 once, after its dispatch. No subsequent correction.'],
        skipped_events='Native placement-loader admission/original route to these platforms and natural Sidekick death/respawn; synthetic remember entries do not prove placement-loader respawn.',
        native_sources=['Obj7A_Properties/Init/SubObjectLoop/End s2.asm:56276-56353',
                        'RunObject s2.asm:29832-29846', 'Obj02_Init_Continued s2.asm:38934-38952',
                        'Obj05 s2.asm:41713-41764', 'LoadTailsTailsDynPLC s2.asm:41637-41693',
                        'loc_294C4 s2.asm:56374-56387', 'DeleteObject2 s2.asm:30334-30345',
                        'SST layout s2.constants.asm:1139-1152'],
        cases=args.case or CASES))
    save(run / 'provenance.json', dict(prepared=prepared, rom=str(args.rom), movie=str(args.movie),
        rom_sha1=sha(args.rom, 'sha1'), movie_sha256=sha(args.movie),
        runtime={name: sha(args.bizhawk_home / 'dll' / name) for name in
                 ('BizHawk.Common.dll', 'BizHawk.Emulation.Cores.dll', 'gpgx.wbx.zst', 'libwaterboxhost.so')},
        launcher_sha256=sha(Path(__file__)), cases=args.case or CASES))
    started = time.monotonic(); records = []
    with handle_termination(), maven_slot(ROOT, kind='native:s2-fixed-tail-pressure', estimate=600) as leases:
        admitted = time.monotonic()
        for case in args.case or CASES:
            argv = ['mono', str(exe), str(args.rom), str(args.movie), str(run / case), case]
            record = dict(command=argv, exit_code=None)
            try:
                record = command(argv, env, run / (case + '.launch.log'), leases, task, timeout=180)
                record['output_check'] = verify_output(run / case, case, args.rom)
            except (subprocess.TimeoutExpired, OSError, ValueError, KeyError) as error:
                record.update(execution_status='incomplete', error=str(error))
            records.append(record); print(json.dumps(record), flush=True)
    complete = all(x['exit_code'] == 0 and x.get('output_check', {}).get('execution_status') == 'completed' for x in records)
    save(run / 'host.json', dict(execution_status='completed' if complete else 'incomplete',
        admission_wait_seconds=admitted-started, execution_seconds=time.monotonic()-admitted, commands=records))
    print('ARTIFACTS ' + str(run), flush=True)
    return 0 if complete else 2


if __name__ == '__main__':
    sys.exit(main())
