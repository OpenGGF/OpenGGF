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
    # Three runs is the documented honest ceiling (measurement hazard 43); memory
    # admission normally binds first on a 30 GiB host with other applications open.
    defaults = {'maxRuns': 3, 'memoryGiB': 7, 'cpuCores': 8, 'headroomGiB': 2}
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


def admits(resources, config, active, credit=(0, 0)):
    """Reserve full budgets for existing runs too, including startup bursts.

    MemAvailable and load already contain what active runs use. ``credit`` is
    that realised usage (bytes, cores) as published by live running leases,
    each capped at its own reservation, so it is not counted twice. Runs
    without a live lease (older clients, dead parents) earn no credit and
    keep their full reservation.
    """
    available, cpus, load = resources
    count = active + 1
    memory, cores = credit
    return (count <= config['maxRuns']
            and available + memory >= (count * config['memoryGiB'] + config['headroomGiB']) * GIB
            and cpus + cores >= count * config['cpuCores'] + load)


def tree_usage(root_pid, proc=Path('/proc')):
    """Return {(pid, start): (rss_bytes, cpu_seconds)} for descendants of root_pid.

    Linux only; returns {} elsewhere or when /proc is unreadable. RSS sums shared
    pages, so it can overcount physical memory (see the 2026-09-15 profiling).
    """
    if sys.platform != 'linux':
        return {}
    page, ticks = os.sysconf('SC_PAGE_SIZE'), os.sysconf('SC_CLK_TCK')
    stats = {}
    try:
        entries = [entry for entry in proc.iterdir() if entry.name.isdigit()]
    except OSError:
        return {}
    for entry in entries:
        try:
            text = (entry / 'stat').read_text()
        except OSError:
            continue  # Exited between listing and reading.
        # Field 2 (comm) may contain spaces and parentheses; split after its end.
        fields = text[text.rindex(')') + 2:].split()
        stats[int(entry.name)] = (int(fields[1]), int(fields[19]),
                                  (int(fields[11]) + int(fields[12])) / ticks, int(fields[21]) * page)
    children = {}
    for pid, (parent, *_rest) in stats.items():
        children.setdefault(parent, []).append(pid)
    usage, pending = {}, list(children.get(root_pid, ()))
    while pending:
        pid = pending.pop()
        _parent, start, cpu, rss = stats[pid]
        usage[(pid, start)] = (rss, cpu)
        pending.extend(children.get(pid, ()))
    return usage
