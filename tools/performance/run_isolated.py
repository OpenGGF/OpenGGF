"""Isolate prebuilt runtime measurements from linked-worktree builds and tests.

Inputs: repository worktree, working directory, audit report, and a prebuilt
measurement command. Origin: 2026-10-12 runtime performance series; P5 audit
(c08d7a658) found that test admission alone does not block build-only commands.
Never invoke a build/test command inside this lease. Linux bypass detection is
sampled every 0.5 seconds; wrapped worktree builds are excluded by OS leases.
"""
from pathlib import Path
import sys, subprocess
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'testing'))
from maven_queue import maven_slot, worktree_build_slot, inherited_slot, handle_termination
from contextlib import ExitStack
import argparse

import os, signal, time, json

def interfering_command(args):
    if not args:
        return False
    executable = Path(args[0]).name
    return executable in ('mvn', 'mvnw', 'javac', 'asprof') or (
        executable == 'java' and (
            'org.codehaus.plexus.classworlds.launcher.Launcher' in args
            or any('surefirebooter' in value for value in args)))


def interfering_processes():
    bad = []
    for item in Path('/proc').iterdir():
        if not item.name.isdecimal():
            continue
        try:
            args = [value.decode(errors='replace')
                    for value in (item/'cmdline').read_bytes().split(b'\0') if value]
            if interfering_command(args):
                bad.append({'pid': int(item.name), 'executable': Path(args[0]).name,
                            'cwd': str((item/'cwd').resolve())})
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            pass
    return bad


def stop_owned_group(process):
    """Never signal a discovered foreign PID; only our launched session."""
    if process.poll() is not None:
        return
    if os.getpgid(process.pid) != process.pid:
        raise RuntimeError('Owned measurement session identity changed')
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        if process.poll() is None and os.getpgid(process.pid) == process.pid:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--worktree',type=Path,default=Path.cwd())
    parser.add_argument('--cwd',type=Path,default=Path.cwd())
    parser.add_argument('--audit-report',type=Path,required=True)
    parser.add_argument('--estimate-seconds',type=float,default=1800)
    parser.add_argument('command',nargs=argparse.REMAINDER)
    a=parser.parse_args()
    command=a.command[1:] if a.command[:1]==['--'] else a.command
    if not command:parser.error('a prebuilt measurement command is required')
    if not Path('/proc').is_dir():parser.error('Linux /proc is required for bypass detection')
    root=a.worktree.resolve()
    with handle_termination(), maven_slot(root, exclusive=True, estimate=a.estimate_seconds, kind='runtime-performance-measurement') as fd, ExitStack() as build_leases:
        inventory=subprocess.check_output(['git','worktree','list','--porcelain'], cwd=root, text=True)
        trees=sorted({Path(line[9:]).resolve() for line in inventory.splitlines() if line.startswith('worktree ')})
        build_fds=[build_leases.enter_context(worktree_build_slot(tree)) for tree in trees if tree!=root and tree.exists()]
        fd=(fd if isinstance(fd,tuple) else (fd,))+tuple(build_fds)
        print('Linked worktree build leases held:',len(build_fds), flush=True)
        bad=interfering_processes()
        if bad: raise SystemExit('Unexpected build/test/profiler before batch: '+str(bad))
        p=subprocess.Popen(command, cwd=a.cwd, start_new_session=True, **inherited_slot(fd))
        audit={'started_utc_epoch':time.time(),'guard_interval_seconds':0.5,'exclusive_test_lease':True,'batch_pid':p.pid,'build_locked_worktrees':[str(t) for t in trees]}
        try:
            while p.poll() is None:
                bad=interfering_processes()
                if bad:
                    audit['interference']=bad
                    stop_owned_group(p)
                    raise SystemExit('Rejected timing batch due to bypassing build/test/profiler: '+str(bad))
                time.sleep(0.5)
            audit['exit_code']=p.returncode
        finally:
            if p.poll() is None:
                stop_owned_group(p)
            audit['ended_utc_epoch']=time.time()
            a.audit_report.parent.mkdir(parents=True, exist_ok=True)
            a.audit_report.write_text(json.dumps(audit,indent=2)+'\n')
        raise SystemExit(p.returncode)


if __name__ == '__main__':
    main()
