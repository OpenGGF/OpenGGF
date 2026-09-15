"""Conservative Linux admission estimates for local Maven runs.

Inputs are OS resource counters and shared Git configuration; no third-party
packages. Origin: 2026-09-15 Maven resource profiling and concurrency task.
"""
import math
import os
from pathlib import Path
import subprocess
import sys

GIB = 1024 ** 3


def policy(root):
    defaults = {'maxRuns': 2, 'memoryGiB': 7, 'cpuCores': 8, 'headroomGiB': 2}
    values = {}
    for key, default in defaults.items():
        result = subprocess.run(['git', 'config', '--get', 'openggf.maven' + key],
                                cwd=root, text=True, capture_output=True)
        if result.returncode not in (0, 1):
            raise ValueError('Cannot read Maven queue configuration: ' + result.stderr)
        value = float(result.stdout.strip()) if result.returncode == 0 else default
        if not math.isfinite(value) or value <= 0:
            raise ValueError('openggf.maven' + key + ' must be finite and positive')
        values[key] = value
    if values['maxRuns'] != int(values['maxRuns']) or values['maxRuns'] > 64:
        raise ValueError('openggf.mavenMaxRuns must be an integer from 1 to 64')
    return values


def snapshot(proc=Path('/proc'), base=Path('/sys/fs/cgroup')):
    """Return available bytes, usable CPUs and load, or None for serial fallback.

    Apply visible cgroup v2 ancestor limits as well as affinity. Unknown platforms
    retain the established serial queue. Counters are advisory, not isolation.
    """
    if sys.platform != 'linux':
        return None
    try:
        mem = dict((line.split(':')[0], int(line.split()[1]) * 1024)
                   for line in (proc / 'meminfo').read_text().splitlines())
        available = mem['MemAvailable']
        cpus = len(os.sched_getaffinity(0))
        cgroup = next((line[3:] for line in (proc / 'self/cgroup').read_text().splitlines()
                       if line.startswith('0::')), None)
        if cgroup is None or not (base / 'cgroup.controllers').exists():
            return None
        if cgroup is not None:
            current = base / cgroup.lstrip('/')
            if not current.is_dir():
                return None
            for directory in (current, *current.parents):
                if directory == base.parent:
                    break
                limit = directory / 'memory.max'
                if limit.exists() and (value := limit.read_text().strip()) != 'max':
                    used = int((directory / 'memory.current').read_text())
                    available = min(available, max(0, int(value) - used))
                quota = directory / 'cpu.max'
                if quota.exists():
                    amount, period = quota.read_text().split()
                    if amount != 'max':
                        cpus = min(cpus, int(amount) / int(period))
        return available, cpus, os.getloadavg()[0]
    except (OSError, ValueError, KeyError, IndexError):
        return None


def admits(resources, config, active):
    """Reserve full budgets for existing runs too, including startup bursts.

    This deliberately double-counts resident usage already in MemAvailable and
    load: conservative admission is preferable to optimistic peak extrapolation.
    """
    available, cpus, load = resources
    count = active + 1
    return (count <= config['maxRuns']
            and available >= (count * config['memoryGiB'] + config['headroomGiB']) * GIB
            and cpus >= count * config['cpuCores'] + load)
