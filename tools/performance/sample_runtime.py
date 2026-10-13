#!/usr/bin/env python3
"""Sample a live runtime's RSS/peak and deduplicated resident DRM memory.

Inputs: an owned JVM PID, its RuntimeEngineBenchmarkTool log and report paths.
Origin: 2026-10-12 complete-runtime performance task. Sampling runs outside the
application; CPU acceptance comes from its exact all-thread JVM process counter.
Only the marked filled-history window contributes steady-state samples. Never
use drm-total/amd-requested bytes as residency or count duplicated DRM FDs twice.
"""
import argparse
import json
import statistics
import time
from pathlib import Path


def memory_bytes(value):
    fields = value.split()
    unit = fields[1] if len(fields) > 1 else ""
    scales = {"": 1, "KiB": 1024, "kB": 1024, "MiB": 1024 * 1024}
    if unit not in scales:
        raise ValueError(f"Unknown process-memory unit: {unit}")
    return int(fields[0]) * scales[unit]


def sample(pid):
    root = Path(f"/proc/{pid}")
    status = {k: v.strip() for line in (root / "status").read_text().splitlines()
              if ":" in line for k, v in [line.split(":", 1)]}
    clients = drm_clients(root / "fdinfo")
    return {"rss": memory_bytes(status["VmRSS"]),
            "hwm": memory_bytes(status["VmHWM"]),
            "vram": sum(c["resident_vram"] for c in clients),
            "shared_vram": sum(c["shared_vram"] for c in clients),
            "clients": clients}


def drm_clients(directory):
    clients = {}
    for path in directory.iterdir():
        try:
            values = {k: v.strip() for line in path.read_text().splitlines()
                      if ":" in line for k, v in [line.split(":", 1)]}
        except OSError:
            continue
        if "drm-client-id" not in values:
            continue
        identity = (values.get("drm-pdev", "global"), values["drm-client-id"])
        if "drm-resident-vram" in values:
            clients[identity] = {
                "device": identity[0], "client": identity[1],
                "resident_vram": memory_bytes(values["drm-resident-vram"]),
                "shared_vram": memory_bytes(values.get("drm-shared-vram", "0")),
                "gfx_ns": int(values.get("drm-engine-gfx", "0").split()[0]),
            }
    return list(clients.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--interval", type=float, default=.05)
    args = parser.parse_args()
    if args.interval <= 0:
        parser.error("interval must be positive")
    rss, vram, shared = [], [], []
    hwm, peak_vram, max_gap = 0, 0, 0
    measuring = False
    begun = ended = False
    offset = 0
    previous = time.monotonic()
    last_clients = []
    stat_path = Path(f"/proc/{args.pid}/stat")
    identity = stat_path.read_text().rsplit(")", 1)[1].split()[19]
    pending_log = ""
    first_log_read = True
    while stat_path.exists():
        try:
            if stat_path.read_text().rsplit(")", 1)[1].split()[19] != identity:
                break  # PID was reused; never sample the replacement process.
        except OSError:
            break
        now = time.monotonic()
        max_gap = max(max_gap, now - previous)
        previous = now
        try:
            with args.log.open() as log:
                log.seek(offset)
                lines = log.read()
                offset = log.tell()
            pending_log += lines
            complete, separator, pending_log = pending_log.rpartition("\n")
            if not separator:
                pending_log = complete + pending_log
                complete = ""
            for line in complete.splitlines():
                if line.startswith("RUNTIME_MEASURE_BEGIN "):
                    if first_log_read:
                        raise SystemExit("Sampler attached after the marked window began")
                    measuring = begun = True
                elif line.startswith("RUNTIME_MEASURE_END "):
                    measuring = False
                    ended = True
            first_log_read = False
            current = sample(args.pid)
        except (OSError, KeyError):
            time.sleep(args.interval)
            continue
        hwm = max(hwm, current["hwm"])
        peak_vram = max(peak_vram, current["vram"])
        if current["clients"]:
            last_clients = current["clients"]
        if measuring:
            if not current["clients"]:
                raise SystemExit("Missing process DRM residency in marked window")
            rss.append(current["rss"])
            vram.append(current["vram"])
            shared.append(current["shared_vram"])
        time.sleep(args.interval)
    if not begun or not ended or not rss or not args.report.is_file():
        raise SystemExit("Incomplete marked runtime window; no memory acceptance result")
    result = {
        "samplingIntervalSeconds": args.interval, "sampleCount": len(rss),
        "maxObservedSamplingGapSeconds": max_gap,
        "steadyRssMedianBytes": statistics.median(rss),
        "steadyRssMinimumBytes": min(rss), "steadyRssMaximumBytes": max(rss),
        "wholeProcessPeakRssBytes": hwm,
        "steadyResidentVramMedianBytes": statistics.median(vram),
        "steadyResidentVramMinimumBytes": min(vram),
        "steadyResidentVramMaximumBytes": max(vram),
        "sampledWholeProcessPeakResidentVramBytes": peak_vram,
        "steadySharedVramMaximumBytes": max(shared),
        "lastObservedDrmClients": last_clients,
    }
    report = json.loads(args.report.read_text())
    report["externalProcessMemory"] = result
    temporary = args.report.with_suffix(args.report.suffix + ".sample-tmp")
    temporary.write_text(json.dumps(report, indent=2) + "\n")
    temporary.replace(args.report)
    print(f"Sampled {len(rss)} steady-state process-memory observations")


if __name__ == "__main__":
    main()
