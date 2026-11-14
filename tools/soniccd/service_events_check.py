#!/usr/bin/env python3
"""Check captured identities and actual native rejection paths for the CD-02 task.

Inputs are completed service-events captures and the installed official runtime.
All generated negative experiments remain under the external service-events root.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

from oracle_boot import digest
from device_observer_run import RUNTIME_PINS, verify_pins
from service_events_run import ROOT, compare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title", type=Path, required=True)
    parser.add_argument("--route", type=Path, required=True)
    parser.add_argument("--bizhawk-home", type=Path, required=True)
    parser.add_argument("--cue", type=Path, required=True)
    parser.add_argument("--firmware-directory", type=Path, required=True)
    parser.add_argument("--bios-sha1", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists() or output == ROOT or not output.is_relative_to(ROOT):
        parser.error("Require a fresh child output under service-events")
    title, route = args.title.resolve(strict=True), args.route.resolve(strict=True)
    for capture in (title, route):
        if not capture.is_relative_to(ROOT):
            parser.error("Capture is outside service-events")
        manifest = json.loads((capture / "manifest.json").read_text())
        for name, expected in manifest["producer_sha256"].items():
            if digest(Path(__file__).with_name(name)) != expected:
                raise ValueError("Captured producer differs: " + name)
        for mode, pins in manifest["streams_sha256"].items():
            for name, expected in pins.items():
                if digest(capture / mode / name) != expected:
                    raise ValueError("Captured data differs: " + mode + "/" + name)
        compare(capture / "run-1", capture / "run-2", ("events.jsonl", "devices.jsonl", "av.jsonl", "counts.jsonl", "continuation.jsonl"))
    prior = Path.home() / "Downloads/Sonic-CD/native/device-observer/usa-title-final-1800/run-1"
    old_av = [json.loads(s) for s in (prior / "av.jsonl").read_text().splitlines()]
    new_av = [json.loads(s) for s in (title / "run-1/av.jsonl").read_text().splitlines()]
    for old, new in zip(old_av, new_av, strict=True):
        if any(old[k] != new[k] for k in ("frame", "lag", "audio_samples", "audio_sha256", "video_sha256")):
            raise ValueError("Prior verified title oracle AV differs")
    if [r["frame"] for r in new_av if "Start" in r["buttons"]] != [600, 900, 1200]:
        raise ValueError("Prior title inputs differ")
    dll = args.bizhawk_home.resolve(strict=True) / "dll"
    verify_pins(dll, RUNTIME_PINS)
    output.mkdir(parents=True)
    env = os.environ.copy()
    for key in ("DISPLAY", "WAYLAND_DISPLAY"):
        env.pop(key, None)
    env.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll), TMPDIR=str(output))
    bad = output / "changed-core-build"
    bad.mkdir()
    (bad / "dll").mkdir()
    for name in RUNTIME_PINS:
        if name != "gpgx.wbx.zst":
            (bad / "dll" / name).symlink_to(dll / name)
    (bad / "dll/gpgx.wbx.zst").write_bytes(b"wrong native core identity\n")
    shutil.copyfile(title / "build/service_events.exe", bad / "service_events.exe")
    layout = Path.home() / "Downloads/Sonic-CD/native/device-observer/layout.tsv"
    wrong_layout = output / "wrong-layout.tsv"
    wrong_layout.write_text("not the pinned C ABI\n")
    results = {}
    for label, exe, abi, expected in (
            ("changed-core", bad / "service_events.exe", layout, "Pinned runtime mismatch gpgx.wbx.zst"),
            ("changed-layout", title / "build/service_events.exe", wrong_layout, "Pinned header-derived layout mismatch")):
        run = output / label
        command = ["mono", str(exe), str(args.cue.resolve()), str(args.firmware_directory.resolve()), str(run), "1800",
                   str(title / "inputs.json"), args.bios_sha1.lower(), str(abi), "events"]
        result = subprocess.run(command, cwd=Path(__file__).resolve().parents[2], env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30, text=True)
        (output / (label + ".log")).write_text(result.stdout)
        if result.returncode == 0 or expected not in result.stdout or (run / "summary.json").exists():
            raise ValueError("Native negative control failed: " + label)
        results[label] = {"exit_code": result.returncode, "required_error": expected, "success_summary_absent": True}
    verify_pins(dll, RUNTIME_PINS)
    results.update(prior_title_av_equal=True, current_capture_inputs_equal=True,
                   capture_stream_hashes_equal=True, native_repeat_equal=True, installed_runtime_unchanged=True,
                   checker_sha256=digest(__file__),
                   manifests_sha256={str(p): digest(p / "manifest.json") for p in (title, route)})
    (output / "summary.json").write_text(json.dumps(results, indent=2) + "\n")
    print(output / "summary.json")


if __name__ == "__main__":
    main()
