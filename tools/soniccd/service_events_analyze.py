#!/usr/bin/env python3
"""Summarize actual service callback observations, not inferred device transitions.

Origin is the 2026-10-23 CD-02 task. Input is a service-events capture root.
"""
import argparse
from collections import Counter
import json
from pathlib import Path


def analyze(root):
    phases, addresses, ranges = Counter(), Counter(), Counter()
    previous = None
    changes = []
    first_busy = None
    title_write = title_after_write = title_first_clear = None
    disc = {"before": 0, "after": 0, "unique_lbas": set(), "subcode": 0}
    watched = ("regs.02", "dmna", "regs.0e", "cdd_hw.status", "cdd_hw.index", "cdd_hw.latency",
               "cdc_hw.dma_active", "cdc_hw.dma_halted", "cdc_hw.irq", "pcm_hw.enabled", "pcm_hw.status", "pcm_hw.bank")
    with (root / "run-1/events.jsonl").open() as stream:
        for line in stream:
            row = json.loads(line)
            phase, raw, access = row["phase"], row["raw"], row["access"]
            if title_write is not None and title_after_write is None:
                title_after_write = row
            if (title_write is None and phase == "bus_write_before_handler" and access["address"] == 0xff8066
                    and access["value"] == 0x7200 and raw["regs.64"] == 96 and raw["regs.62"] == 256):
                title_write = row
            if title_after_write is not None and title_first_clear is None and not raw["regs.58"] & 0x8000:
                title_first_clear = row
            phases[phase] += 1
            if "address" in access:
                addresses[f'{phase}:{access["address"]:06x}'] += 1
                ranges[f'{phase}:{access["address"] & 0xffff00:06x}'] += 1
            if phase == "disc_sector_before_original_callback":
                disc["before"] += 1
                disc["unique_lbas"].add(access["lba"])
                disc["subcode"] += int(access["subcode"])
            if phase == "disc_sector_after_original_callback_before_stream_copy":
                disc["after"] += 1
            current = {k: raw[k] for k in watched}
            current["graphics_busy"] = bool(raw["regs.58"] & 0x8000)
            if current["graphics_busy"] and first_busy is None:
                first_busy = row
            if previous is not None:
                delta = {k: [previous[k], current[k]] for k in current if previous[k] != current[k]}
                if delta:
                    changes.append({"frame_call": row["frame_call"], "ordinal_in_call": row["ordinal_in_call"],
                                    "observation_phase": phase, "access": access, "observed_changes": delta,
                                    "raw_clocks": {k: raw[k] for k in ("cycles", "cdd_hw.cycles", "cdc_hw.cycles[0]", "cdc_hw.cycles[1]",
                                                                               "gfx_hw.cycles", "pcm_hw.cycles", "timer", "stopwatch")}})
            previous = current
    disc["unique_lbas"] = len(disc["unique_lbas"])
    counts = [json.loads(line) for line in (root / "run-1/counts.jsonl").read_text().splitlines()]
    manifest = json.loads((root / "manifest.json").read_text())
    continuation_controls = []
    for mode in manifest["summaries"]:
        if mode == "run-1":
            continue
        with (root / "run-1/continuation.jsonl").open() as left, (root / mode / "continuation.jsonl").open() as right:
            count = 0
            for a, b in zip(left, right, strict=True):
                a, b = json.loads(a), json.loads(b)
                if a["av"] != b["av"] or a["devices"] != b["devices"]:
                    raise ValueError("Restored continuation control differs: " + mode)
                count += 1
            if count != 120:
                raise ValueError("Restored continuation length differs")
        continuation_controls.append(mode)
    return {"schema": "openggf.soniccd.service-events-summary.v1", "boundary_warning": "Changes are first observed changes between samples, not exact submission or completion edges",
            "phases": dict(phases), "address_ranges": dict(ranges), "address_counts": dict(addresses), "disc": disc,
            "read_invocations": sum(r["reads"] for r in counts), "suppressed_same_value_reads": sum(r["suppressed_reads"] for r in counts),
            "write_invocations": sum(r["writes"] for r in counts), "first_busy_observation": first_busy,
            "title_descriptor_source": "InitGfxOperation and RunGfxOperation, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Title Screen/Sub.asm:270-278,322-328",
            "first_title_trace_write_candidate": title_write, "next_retained_observation": title_after_write,
            "first_clear_observation_after_title_candidate": title_first_clear,
            "continuation_av_device_controls_equal": continuation_controls,
            "changed_observations": changes}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture-root", type=Path, required=True)
    args = parser.parse_args()
    root = args.capture_root.resolve(strict=True)
    allowed = Path.home() / "Downloads/Sonic-CD/native/service-events"
    if not root.is_relative_to(allowed) or root == allowed:
        parser.error("Capture must be a child of the external service-events root")
    summary = analyze(root)
    destination = root / "event-summary.json"
    destination.write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({k: v for k, v in summary.items() if k not in ("changed_observations", "first_busy_observation", "address_counts",
                     "first_title_trace_write_candidate", "next_retained_observation", "first_clear_observation_after_title_candidate")}, indent=2))
    print(destination)


if __name__ == "__main__":
    main()
