"""Isolate prebuilt runtime measurements from linked-worktree builds and tests.

Inputs: repository worktree, working directory, audit report, and a prebuilt
measurement command. Origin: 2026-10-12 runtime performance series; P5 audit
(c08d7a658) found that test admission alone does not block build-only commands.
Never invoke a build/test command inside this lease. Active wrapped builds are
excluded by a repository-wide OS lease; idle category worktrees can retain their
output ownership. Linux bypass detection is sampled every 0.5 seconds.
"""
from pathlib import Path
import sys, subprocess
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'testing'))
from maven_queue import maven_slot, worktree_build_slot, inherited_slot, handle_termination
from maven_activity import build_activity_slot
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
    root=Path(subprocess.check_output(['git','rev-parse','--show-toplevel'],
                                     cwd=a.worktree, text=True).strip()).resolve()
    # Wait for our own artifacts before joining the queue, then exclude actual
    # execution elsewhere. Never wait on retained foreign worktree ownership.
    # A build activity holder must not wait for test admission.
    with (handle_termination(), worktree_build_slot(root) as tree_fd,
          maven_slot(root, exclusive=True, estimate=a.estimate_seconds,
                     kind='runtime-performance-measurement', tree_fd=tree_fd) as fd):
        with build_activity_slot(root, exclusive=True) as build_fds:
            fd=(fd if isinstance(fd,tuple) else (fd,))+build_fds
            print('Exclusive test and build activity leases held', flush=True)
            bad=interfering_processes()
            if bad: raise SystemExit('Unexpected build/test/profiler before batch: '+str(bad))
            p=subprocess.Popen(command, cwd=a.cwd, start_new_session=True, **inherited_slot(fd))
            audit={'started_utc_epoch':time.time(),'guard_interval_seconds':0.5,
                   'exclusive_test_lease':True,'exclusive_build_activity_lease':True,
                   'batch_pid':p.pid}
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
            return p.returncode


if __name__ == '__main__':
    raise SystemExit(main())
