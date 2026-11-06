#!/usr/bin/env python3
"""Sample PCs of an owned native-image child, using gdb without perf/ptrace attach.

Inputs: prebuilt launcher JSON, label, original ROM/BK2, symbol-only ELF sidecar,
output directory, interval and workload frames. Origin: 3b5b578a3 (2026-10-27
native runtime-loading/retention ablation). Run via run_isolated.py; compile
before admission. Sampling perturbs timings: never use these frame times as
performance acceptance. Thread snapshots include sleeping threads; only use
PC counts as wall-time samples, not exact CPU attribution; retain thread identities.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    # gdb's embedded interpreter does not reliably include subprocess's _posixsubprocess
    # on every distribution. This host has it; fail visibly if unavailable.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--launchers', type=Path, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--symbols', type=Path, required=True)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--bk2', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--interval', type=float, default=.020)
    parser.add_argument('--traversal', type=int, default=2000)
    parser.add_argument('--frames', type=int, default=4000)
    parser.add_argument('--passes', type=int, default=0)
    parser.add_argument('--window-begin-prefix')
    parser.add_argument('--window-end-prefix')
    args = parser.parse_args()
    if args.interval <= 0 or args.frames < 1 or args.passes < 0:
        parser.error('positive interval/frames and nonnegative passes required')
    if bool(args.window_begin_prefix) != bool(args.window_end_prefix):
        parser.error('window boundaries must be supplied together')
    symbols = subprocess.check_output(['nm', '--defined-only', str(args.symbols)], text=True)
    def unique_symbol(prefix):
        matches = [line.split()[-1] for line in symbols.splitlines()
                   if line.split() and line.split()[-1].startswith(prefix)]
        if len(matches) != 1:
            parser.error(f'Expected one symbol for {prefix}, found {len(matches)}')
        return matches[0]
    boundaries = ([unique_symbol(args.window_begin_prefix), unique_symbol(args.window_end_prefix)]
                  if args.window_begin_prefix else [])
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        parser.error('Choose an empty output directory to avoid stale results')
    output.mkdir(parents=True, exist_ok=True)
    launcher = json.loads(args.launchers.read_text())[args.label]
    command = launcher + [str(args.rom.resolve()), str(args.bk2.resolve()),
                         str(output/'benchmark.json'), str(args.traversal),
                         str(args.frames), str(args.passes)]
    config = {'interval': args.interval, 'symbols': str(args.symbols.resolve()),
              'output': str(output), 'command': command, 'boundaries': boundaries,
              'timer_python': sys.executable}
    (output/'config.json').write_text(json.dumps(config, indent=2)+'\n')
    environment = dict(os.environ)
    for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(key, None)
    environment['OPENGGF_GDB_SAMPLE_CONFIG'] = str(output/'config.json')
    script = Path(__file__).resolve()
    # Feed this file to gdb's Python interpreter; the host-side entry is not called.
    expression = f'exec(compile(open({str(script)!r}).read(), {str(script)!r}, "exec"))'
    with (output/'gdb.log').open('w') as log:
        result = subprocess.run(['gdb', '-nx', '-q', '-batch', '-ex',
                                 'python '+expression, '--args', *command],
                                env=environment, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f'gdb failed ({result.returncode}); inspect {output}/gdb.log')
    summary = json.loads((output/'summary.json').read_text())
    if not summary['snapshots'] or summary['exit_code'] != 0 or not (output/'benchmark.json').is_file():
        raise SystemExit(f'Incomplete sampled workload: {summary}')
    print(json.dumps(summary), flush=True)


def in_gdb():
    import gdb
    import time
    config = json.loads(Path(os.environ['OPENGGF_GDB_SAMPLE_CONFIG']).read_text())
    output = Path(config['output'])
    for command in ('set pagination off', 'set confirm off', 'set print thread-events off',
                    'set disable-randomization on', 'set debuginfod enabled off', 'handle SIGPIPE nostop noprint pass',
                    'handle SIGSEGV nostop noprint pass',
                    'handle SIGINT noprint nopass', 'handle SIGINT stop'):
        gdb.execute(command, to_string=True)
    gdb.execute('symbol-file '+json.dumps(config['symbols']), to_string=True)
    status = {'exit_code': None, 'signal': None, 'breakpoints': []}

    def exited(event):
        status['exit_code'] = getattr(event, 'exit_code', None)

    def stopped(event):
        status['signal'] = getattr(event, 'stop_signal', None)
        status['breakpoints'] = [b.number for b in getattr(event, 'breakpoints', [])]

    gdb.events.exited.connect(exited)
    gdb.events.stop.connect(stopped)
    gdb.execute('starti', to_string=True)
    inferior_pid = gdb.selected_inferior().pid
    end_break = None
    window_ended = False
    if config['boundaries']:
        start_break = gdb.Breakpoint(config['boundaries'][0], temporary=True, internal=True)
        start_number = start_break.number
        gdb.execute('continue', to_string=True)
        if start_number not in status['breakpoints']:
            raise RuntimeError('Did not reach the requested begin symbol: '+str(status))
        end_break = gdb.Breakpoint(config['boundaries'][1], temporary=True, internal=True)
        end_number = end_break.number
    (output/'maps.txt').write_text(Path(f'/proc/{inferior_pid}/maps').read_text())
    gdb.execute('sharedlibrary', to_string=True)
    # Native-image's loader need not expose all DSOs through gdb's solib list.
    # Load their exported symbols at the child's observed ELF mapping bias.
    known = {Path(o.filename).resolve() for o in gdb.objfiles() if o.filename}
    library_errors = []
    for line in (output/'maps.txt').read_text().splitlines():
        fields = line.split(maxsplit=5)
        if len(fields) != 6 or int(fields[2], 16) != 0:
            continue
        path = Path(fields[5])
        if '.so' not in path.name or not path.is_file() or path.resolve() in known:
            continue
        with path.open('rb') as elf:
            if elf.read(4) != b'\x7fELF':
                continue
        bias = int(fields[0].split('-')[0], 16)
        try:
            gdb.execute('add-symbol-file '+json.dumps(str(path))+' -o '+hex(bias), to_string=True)
            known.add(path.resolve())
        except gdb.error as error:
            library_errors.append({'path': str(path), 'error': str(error)})
    (output/'library-errors.json').write_text(json.dumps(library_errors, indent=2)+'\n')
    (output/'shared-libraries.txt').write_text(gdb.execute('info sharedlibrary', to_string=True))
    pidfd = os.pidfd_open(inferior_pid)
    symbol_cache = {}
    snapshots = 0
    begin = time.monotonic()
    # Do not use threading.Timer: continue holds gdb Python's GIL. Signal the
    # inferior through a pre-opened pidfd: a late signal cannot interrupt gdb's
    # Python at the end breakpoint, or reach a reused process ID.
    with (output/'samples.jsonl').open('w') as stream:
        while gdb.selected_inferior().threads():
            timer = ('import signal,sys,time; time.sleep(float(sys.argv[1])); '
                     'signal.pidfd_send_signal(int(sys.argv[2]), signal.SIGINT)')
            sender = subprocess.Popen([config['timer_python'], '-S', '-c', timer,
                                       str(config['interval']), str(pidfd)], pass_fds=(pidfd,),
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            status['signal'] = None
            status['breakpoints'] = []
            try:
                gdb.execute('continue', to_string=True)
            finally:
                if sender.poll() is None:
                    sender.terminate()
                sender.wait()
            threads = gdb.selected_inferior().threads()
            if not threads:
                break
            if end_break is not None and end_number in status['breakpoints']:
                window_ended = True
                break
            if status['signal'] != 'SIGINT':
                raise RuntimeError('Unexpected stop: '+str(status))
            rows = []
            for thread in threads:
                thread.switch()
                frame = gdb.newest_frame()
                pc = int(frame.pc())
                if pc not in symbol_cache:
                    symbol_cache[pc] = gdb.execute('info symbol '+hex(pc), to_string=True).strip()
                lwp = thread.ptid[1]
                try:
                    stat = Path(f'/proc/{gdb.selected_inferior().pid}/task/{lwp}/stat').read_text()
                    state = stat.rsplit(')', 1)[1].split()[0]
                except OSError:
                    state = '?'
                stack = []
                for _ in range(6):
                    if frame is None:
                        break
                    try:
                        stack.append({'pc': hex(int(frame.pc())), 'name': frame.name()})
                        frame = frame.older()
                    except gdb.error:
                        break
                rows.append({'thread': thread.num, 'lwp': lwp, 'name': thread.name,
                             'state': state, 'pc': hex(pc), 'symbol': symbol_cache[pc], 'stack': stack})
            stream.write(json.dumps({'sample': snapshots, 'elapsed_s': time.monotonic()-begin,
                                     'threads': rows})+'\n')
            stream.flush()
            snapshots += 1
    if config['boundaries'] and not window_ended:
        raise RuntimeError('Did not reach the requested end symbol')
    # Consume a possible queued late SIGINT without adding a post-window sample.
    while gdb.selected_inferior().threads():
        status['signal'] = None
        gdb.execute('continue', to_string=True)
        if gdb.selected_inferior().threads() and status['signal'] != 'SIGINT':
            raise RuntimeError('Unexpected post-window stop: '+str(status))
    os.close(pidfd)
    summary = dict(status, snapshots=snapshots, window_ended=window_ended, unique_pcs=len(symbol_cache),
                   elapsed_s=time.monotonic()-begin, interval_s=config['interval'],
                   command=config['command'], inferior_pid=inferior_pid)
    (output/'summary.json').write_text(json.dumps(summary, indent=2)+'\n')


if 'gdb' in sys.modules:
    in_gdb()
elif __name__ == '__main__':
    main()
