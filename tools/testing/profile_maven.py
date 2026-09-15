#!/usr/bin/env python3
"""Sample a queued category run's descendant RSS and CPU (requires psutil).

Usage: python3 tools/testing/profile_maven.py --output target/maven-profile.json -- --category all --run
Inputs: category-runner arguments, host process counters. Output: bounded aggregate
JSON, updated every 30 samples. No raw Maven logs or per-sample archive is retained.
Origin: 2026-09-15 Maven resource profiling/concurrency investigation.
"""
import argparse
import json
import math
from pathlib import Path
import subprocess
import sys
import time


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] if ordered else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('runner_args', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    import psutil  # Optional profiling dependency; queue itself uses only stdlib.
    from category_artifacts import stop_process_tree
    from maven_queue import handle_termination

    forwarded = args.runner_args
    if forwarded[:1] == ['--']:
        forwarded = forwarded[1:]
    if '--run' not in forwarded:
        parser.error('Supply category-runner arguments including --run after --')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(Path(__file__).with_name('run_categories.py')), *forwarded]
    seen = {}
    resident = []
    cores = []
    started = time.monotonic()
    previous_time = started
    previous_cpu = 0
    minimum_available = psutil.virtual_memory().available
    process = None

    def save(complete=False):
        duration = time.monotonic() - started
        summary = dict(command=command, cwd=str(Path.cwd()),
                       commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                       complete=complete, exit_code=process.poll() if process else None,
                       wall_seconds_including_queue=duration, sample_interval_seconds=1,
                       samples=len(resident), peak_tree_rss_bytes=max(resident, default=0),
                       p95_tree_rss_bytes=percentile(resident, .95),
                       sampled_cpu_seconds=sum(seen.values()),
                       mean_cpu_cores_including_queue=sum(seen.values()) / duration,
                       p95_cpu_cores=percentile(cores, .95), peak_sample_cpu_cores=max(cores, default=0),
                       minimum_host_available_bytes=minimum_available,
                       host_logical_cpus=psutil.cpu_count(), host_memory_bytes=psutil.virtual_memory().total,
                       limits='RSS sums shared pages; one-second sampling can miss short-lived children and peaks. '
                              'CPU is sampled user+system time, a lower bound. Wall/mean include queue wait. '
                              'Complete means runner exited; inspect exit code and category results for coverage.')
        temporary = args.output.with_suffix(args.output.suffix + '.tmp')
        temporary.write_text(json.dumps(summary, indent=2) + '\n')
        temporary.replace(args.output)

    with handle_termination():
        try:
            process = subprocess.Popen(command, start_new_session=(sys.platform != 'win32'))
            parent = psutil.Process(process.pid)
            while process.poll() is None:
                rss = 0
                try:
                    children = parent.children(recursive=True)
                except psutil.Error:
                    children = []
                for child in children:
                    try:
                        with child.oneshot():
                            key = (child.pid, child.create_time())
                            cpu = child.cpu_times()
                            seen[key] = cpu.user + cpu.system
                            rss += child.memory_info().rss
                    except psutil.Error:
                        pass
                now = time.monotonic()
                total_cpu = sum(seen.values())
                cores.append(max(0, total_cpu - previous_cpu) / (now - previous_time))
                previous_time, previous_cpu = now, total_cpu
                resident.append(rss)
                minimum_available = min(minimum_available, psutil.virtual_memory().available)
                if len(resident) % 30 == 0:
                    save()
                time.sleep(1)
            save(complete=True)
            return process.returncode
        finally:
            if process is not None and process.poll() is None:
                stop_process_tree(process)
                save()


if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)
