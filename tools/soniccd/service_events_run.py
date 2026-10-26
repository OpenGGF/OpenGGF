#!/usr/bin/env python3
"""Capture official-core CD service callback boundaries for the 2026-10-23 CD-02 task.

Reuses frozen oracle/device sources. Generated files stay under Downloads/Sonic-CD.
No engine state is populated from these diagnostic observations.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from oracle_boot import ASSEMBLIES, COMPILER_SHA256, digest, media_identity
from device_observer_run import RUNTIME_PINS, LAYOUT_SHA256, verify_pins

ROOT = Path.home() / "Downloads/Sonic-CD/native/service-events"
FROZEN = Path.home() / "Downloads/Sonic-CD/native/device-observer"
ALLOWED = {"Start", "A", "B", "C", "Up", "Down", "Left", "Right"}


def parse_inputs(text, frames):
    result = []
    for token in text.split(","):
        if not token:
            continue
        match = re.fullmatch(r"(\d+)(?:-(\d+))?:(Start|A|B|C|Up|Down|Left|Right)", token)
        if not match:
            raise ValueError("Input must be first[-last]:button intervals")
        first, last = int(match[1]), int(match[2] or match[1])
        if not 1 <= first <= last <= frames:
            raise ValueError("Input outside hardware-frame interval")
        result.append({"first": first, "last": last, "button": match[3]})
    return sorted(result, key=lambda p: (p["first"], p["last"], p["button"]))


def verify_run(path, frames, inputs, events):
    summary = json.loads((path / "summary.json").read_text())
    if (summary["frames"] != frames or not summary["restore_120_frames_equal"]
            or not summary["observation_savestate_unchanged"]):
        raise ValueError("Native restore/state control failed")
    if not summary["disc_before"] == summary["disc_original"] == summary["disc_after"]:
        raise ValueError("Disc forwarding count differs")
    av = [json.loads(line) for line in (path / "av.jsonl").read_text().splitlines()]
    if [r["frame"] for r in av] != list(range(1, frames + 1)):
        raise ValueError("AV frame ordering differs")
    for row in av:
        buttons = sorted({p["button"] for p in inputs if p["first"] <= row["frame"] <= p["last"]})
        if row["buttons"] != buttons:
            raise ValueError("Observed controller input differs")
    if events:
        previous = (0, 0)
        pairs = []
        with (path / "events.jsonl").open() as stream:
            for line in stream:
                row = json.loads(line)
                key = row["frame_call"], row["ordinal_in_call"]
                if not 1 <= key[0] <= frames or key <= previous or key[1] != (previous[1] + 1 if key[0] == previous[0] else 1):
                    raise ValueError("Event order or hardware frame differs")
                previous = key
                if row["phase"] == "disc_sector_before_original_callback":
                    pairs.append(row["access"])
                elif row["phase"] == "disc_sector_after_original_callback_before_stream_copy":
                    if not pairs or pairs.pop() != {k: row["access"][k] for k in ("lba", "subcode")}:
                        raise ValueError("Disc boundary pairing differs")
        if pairs or previous == (0, 0):
            raise ValueError("Incomplete or empty event stream")
    return summary


def compare(left, right, names):
    for name in names:
        if digest(left / name) != digest(right / name):
            raise ValueError("Native comparison differs: " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bizhawk-home", type=Path, required=True)
    parser.add_argument("--cue", type=Path, required=True)
    parser.add_argument("--firmware-directory", type=Path, required=True)
    parser.add_argument("--bios-sha1", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frames", type=int, default=1800)
    parser.add_argument("--inputs", default="600:Start,900:Start,1200:Start")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--with-codedatalog", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists() or output == ROOT or not output.is_relative_to(ROOT):
        parser.error("Require a fresh child output under the service-events root")
    if not 1 <= args.frames <= 36000 or args.timeout < 1 or not re.fullmatch(r"[0-9a-fA-F]{40}", args.bios_sha1):
        parser.error("Invalid frame bound, timeout or BIOS identity")
    inputs = parse_inputs(args.inputs, args.frames)
    repo = Path(__file__).resolve().parents[2]
    dll = args.bizhawk_home.resolve(strict=True) / "dll"
    compiler = Path("/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe")
    if digest(compiler) != COMPILER_SHA256:
        raise ValueError("Pinned compiler mismatch")
    runtime = verify_pins(dll, RUNTIME_PINS)
    layout = FROZEN / "layout.tsv"
    if digest(layout) != LAYOUT_SHA256:
        raise ValueError("Pinned device layout mismatch")
    reference = json.loads((FROZEN / "usa-title-final-1800/manifest.json").read_text())
    for name in ("device_observer_probe.cs", "oracle_probe.cs"):
        if digest(Path(__file__).with_name(name)) != reference["tools_sha256"][name]:
            raise ValueError("Frozen observer source differs: " + name)
    media = media_identity(args.cue)
    source_files = [Path(__file__), Path(__file__).with_name("service_events_probe.cs"),
                    Path(__file__).with_name("device_observer_probe.cs"), Path(__file__).with_name("oracle_probe.cs"),
                    Path(__file__).with_name("oracle_boot.py"), Path(__file__).with_name("device_observer_run.py")]
    source_hashes = {p.name: digest(p) for p in source_files}
    output.mkdir(parents=True)
    build = output / "build"
    build.mkdir()
    (build / "dll").symlink_to(dll, target_is_directory=True)
    input_path = output / "inputs.json"
    input_path.write_text(json.dumps(inputs, indent=2) + "\n")
    exe = build / "service_events.exe"
    command = ["mono", str(compiler), "/nologo", "/langversion:9", "/deterministic+", "/main:ServiceEvents", f"/out:{exe}",
               "/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll"]
    subprocess.run(command + [f"/reference:{dll / name}" for name in ASSEMBLIES]
                   + [str(p) for p in source_files if p.suffix == ".cs"], cwd=repo, check=True, timeout=30)
    env = os.environ.copy()
    for key in ("DISPLAY", "WAYLAND_DISPLAY"):
        env.pop(key, None)
    env.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll), TMPDIR=str(build))
    modes = [("run-1", "events"), ("run-2", "events"), ("control", "control")]
    if args.with_codedatalog:
        modes.append(("codedatalog", "events-cdl"))
    summaries = {}
    for name, mode in modes:
        command = ["mono", str(exe), str(args.cue.resolve()), str(args.firmware_directory.resolve()), str(output / name),
                   str(args.frames), str(input_path), args.bios_sha1.lower(), str(layout), mode]
        with (output / (name + ".log")).open("w") as log:
            subprocess.run(command, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=args.timeout)
        summaries[name] = verify_run(output / name, args.frames, inputs, mode != "control")
        print(f"Completed {name}: {args.frames} hardware frames and restored 120-frame event/AV/device continuation", flush=True)
    compare(output / "run-1", output / "run-2", ("events.jsonl", "devices.jsonl", "av.jsonl", "counts.jsonl", "continuation.jsonl"))
    for name, mode in modes[2:]:
        compare(output / "run-1", output / name, ("av.jsonl", "devices.jsonl"))
    if len({v["checkpoint_sha256"] for v in summaries.values()}) != 1:
        raise ValueError("Detached checkpoint differs from control")
    for path in source_files:
        if digest(path) != source_hashes[path.name]:
            raise ValueError("Producer input changed during capture")
    verify_pins(dll, RUNTIME_PINS)
    manifest = {"schema": "openggf.soniccd.service-events-manifest.v1", "purpose": "Diagnostic only, not trace v5 or engine input",
                "frames": args.frames, "inputs": inputs, "input_sha256": digest(input_path), "media": media,
                "bios_sha1": args.bios_sha1.lower(), "runtime_sha256": runtime, "producer_sha256": source_hashes,
                "layout_sha256": digest(layout), "compiler_sha256": digest(compiler), "summaries": summaries,
                "native_repeat_equal": True, "control_av_devices_checkpoint_equal": True,
                "read_retention": "first value per bus address per hardware frame, then every value change; all invocations counted",
                "streams_sha256": {name: {f: digest(output / name / f) for f in ("av.jsonl", "events.jsonl", "devices.jsonl", "counts.jsonl", "continuation.jsonl")}
                                   for name, mode in modes}}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(output / "manifest.json")


if __name__ == "__main__":
    main()
