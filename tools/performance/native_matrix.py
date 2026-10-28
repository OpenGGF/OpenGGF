#!/usr/bin/env python3
"""Repeat prebuilt native/JVM gameplay benchmarks sequentially under run_isolated.py.

Inputs: JSON mapping runtime labels to launcher argument arrays (ending at the
benchmark entry point), original ROM, committed BK2, and disposable output path.
Origin: 2026-10-27 Community native optimization comparison. Builds are separate;
never invoke Maven or native-image while holding the measurement leases.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--launchers', type=Path, required=True)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--bk2', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runs', type=int, default=5)
    parser.add_argument('--traversal', type=int, default=2000)
    parser.add_argument('--frames', type=int, default=4000)
    args = parser.parse_args()
    if args.runs < 5:
        parser.error('At least five repetitions are required')
    launchers = json.loads(args.launchers.read_text())
    args.output.mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ)
    for name in ('DISPLAY', 'WAYLAND_DISPLAY', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(name, None)
    results = []
    labels = list(launchers)
    for run in range(args.runs):
        # Rotate order to avoid assigning every late/hot run to the same build.
        order = labels[run % len(labels):] + labels[:run % len(labels)]
        for label in order:
            report = (args.output / f'{label}-{run + 1}.json').resolve()
            command = launchers[label] + [str(args.rom.resolve()), str(args.bk2.resolve()),
                    str(report), str(args.traversal), str(args.frames),
                    '2' if label == 'jvm' else '0']
            print(f'BEGIN {label} repetition {run + 1}', flush=True)
            started = time.monotonic()
            first_frame = None
            with (args.output / f'{label}-{run + 1}.log').open('w') as log:
                with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                      text=True, env=environment) as process:
                    for line in process.stdout:
                        if line.strip() == 'BENCH_FIRST_FRAME':
                            first_frame = time.monotonic() - started
                        log.write(line)
                    code = process.wait()
            if code or first_frame is None:
                raise RuntimeError(f'{label} failed ({code}), or no first frame; inspect run log')
            result = json.loads(report.read_text())
            result.update(label=label, repetition=run + 1, command=command,
                          startup_s=first_frame, process_wall_s=time.monotonic() - started)
            results.append(result)
            (args.output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
            print(f'END {label} repetition {run + 1}: startup={first_frame:.3f}s '
                  f'frame={result["wall"]["mean_ms"]:.3f}ms '
                  f'CPU={result["process_cpu"]["mean_ms"]:.3f}ms', flush=True)
    for key in ('trajectory', 'object_observations', 'final_x', 'final_y', 'pixel_hash'):
        if len({row[key] for row in results}) != 1:
            raise RuntimeError(f'Workload mismatch: {key}; comparison invalid')
    print('All trajectories, object counts and final framebuffer hashes match', flush=True)


if __name__ == '__main__':
    main()
