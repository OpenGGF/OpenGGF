#!/usr/bin/env python3
"""CD-02 research observer for the exact official BizHawk 2.11 Linux core.

Uses caller-owned media and pinned source headers. All generated files stay in
the external Sonic-CD device-observer directory. No native core is rebuilt.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from oracle_boot import ASSEMBLIES, COMPILER_SHA256, digest, media_identity

OUTPUT_ROOT = Path.home() / "Downloads/Sonic-CD/native/device-observer"
BIZHAWK_COMMIT = "427556b5ef3ac437eba754d90c5e7e9096c9a8df"
GPGX_COMMIT = "051d430d3d1b54625f9900c8f152d7f232e06daf"
LAYOUT_SHA256 = "e53146a7b8908c5c8ccd2e565211cfa5c27efcae19a462ec43bfa58a8f8058b4"
RUNTIME_PINS = {
    "BizHawk.Common.dll": "438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33",
    "BizHawk.Emulation.Common.dll": "f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4",
    "BizHawk.Emulation.Cores.dll": "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7",
    "BizHawk.Emulation.DiscSystem.dll": "a18f4ca3a7bb36aeea117dc36311664ad410b660d0052f55617055b3d6822d64",
    "BizHawk.BizInvoke.dll": "8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7",
    "Newtonsoft.Json.dll": "e1e27af7b07eeedf5ce71a9255f0422816a6fc5849a483c6714e1b472044fa9d",
    "gpgx.wbx.zst": "c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12",
    "libwaterboxhost.so": "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78",
}
HEADER_PINS = {
    "cdc.h": "cfda745794a0a559a9b3d0153b56ea6523079178f0b6f85396655551f75ed0d4",
    "scd.h": "5306deea8b8ab9167540bd96b0e8c571b0e69d2c84b3a1ee8a406ed10f52d292",
    "cdd.h": "ccf917a9973001473ae5633c4b27e7b2377e41c53d79a4182c0cb6424e74a249",
    "pcm.h": "b86a4d0fc67175dd5124b76a20a898f230682025969bead722863bede13e032d",
    "types.h": "1159e5ebd0f4bc004b3df0f9f661630a0da65f3fb0abcfbe4298b2f1332d6442",
    "gfx.h": "3b79d6cb43eec5e26f6ea33890d8c8dd4645d363a1d0676f90971045c398ab67",
    "cd_cart.h": "6b2ca30013aadc853e174955fc263cba73a52fc56fd86233a38102a7f34e2778",
    "blip_buf.h": "367414a873407fad24cf0207429cf71e62aed080a278f40884b157f25930428e",
    "cdStream.h": "d2eb169519dd23df1e61c9aa27cbb898313acc208b1b06e3a420365f3b9f78fc",
    "cdStreamImpl.c": "8d13ea79e78e5861182baca2b7023eda2385b182d5ca948fdbcf54fe7e030a81",
}


def verify_pins(directory, pins):
    actual = {name: digest(directory / name) for name in pins}
    for name, expected in pins.items():
        if actual[name] != expected:
            raise ValueError(f"Pinned file mismatch: {name}")
    return actual


def verify_run(directory, frames, observed):
    summary = json.loads((directory / "summary.json").read_text())
    if summary["frames"] != frames or not summary["restore_120_frames_equal"] or not summary["observation_savestate_unchanged"]:
        raise ValueError("Native run did not satisfy restore and side-effect checks")
    for name in ("av.jsonl", "devices.jsonl") if observed else ("av.jsonl",):
        rows = [json.loads(line) for line in (directory / name).read_text().splitlines()]
        if [row["frame"] for row in rows] != list(range(1, frames + 1)):
            raise ValueError(f"Missing or reordered observations: {name}")
    return summary


def compare_observations(left, right, names):
    for name in names:
        if digest(left / name) != digest(right / name):
            raise ValueError(f"Native observation differs: {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bizhawk-home", type=Path, required=True)
    parser.add_argument("--source-directory", type=Path, default=OUTPUT_ROOT / "source")
    parser.add_argument("--cue", type=Path, required=True)
    parser.add_argument("--firmware-directory", type=Path, required=True)
    parser.add_argument("--bios-sha1", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frames", type=int, default=1800)
    parser.add_argument("--start-frames", default="600,900,1200")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--with-callbacks", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUTPUT_ROOT) or output == OUTPUT_ROOT or output.exists():
        parser.error("Require a new child directory under the external device-observer root")
    if not 1 <= args.frames <= 36000 or args.timeout < 1 or not re.fullmatch(r"[0-9a-fA-F]{40}", args.bios_sha1):
        parser.error("Invalid frame bound, timeout or BIOS SHA1")
    starts = sorted(set(int(x) for x in args.start_frames.split(",") if x))
    if any(x < 1 or x > args.frames for x in starts):
        parser.error("Start input outside capture interval")
    repo = Path(__file__).resolve().parents[2]
    dll = args.bizhawk_home.resolve(strict=True) / "dll"
    source = args.source_directory.resolve(strict=True)
    compiler = Path("/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe")
    if digest(compiler) != COMPILER_SHA256:
        raise ValueError("Pinned Roslyn compiler mismatch")
    binaries = verify_pins(dll, RUNTIME_PINS)
    headers = verify_pins(source, HEADER_PINS)
    media = media_identity(args.cue)
    inputs = [Path(__file__), Path(__file__).with_name("device_observer_probe.cs"),
              Path(__file__).with_name("device_observer_layout.c"), Path(__file__).with_name("oracle_probe.cs"),
              Path(__file__).with_name("oracle_boot.py")]
    tool_hashes = {p.name: digest(p) for p in inputs}
    output.mkdir(parents=True)
    build = output / "build"
    build.mkdir()
    (build / "dll").symlink_to(dll, target_is_directory=True)
    stream = re.search(r"struct cdStream_t\s*\{[^}]*\};", (source / "cdStreamImpl.c").read_text())
    if stream is None:
        raise ValueError("Pinned CD stream declaration missing")
    (build / "device_observer_stream.h").write_text(stream.group(0) + "\n")
    subprocess.run(["gcc", "-std=c99", "-DLSB_FIRST", f"-I{build}", f"-I{source}",
                    str(inputs[2]), "-o", str(build / "layout-probe")], check=True, cwd=repo, timeout=30)
    layout = output / "layout.tsv"
    layout.write_bytes(subprocess.check_output([str(build / "layout-probe")], cwd=repo, timeout=10))
    if digest(layout) != LAYOUT_SHA256:
        raise ValueError("Compiler ABI disagrees with the pinned layout")
    executable = build / "device_observer.exe"
    command = ["mono", str(compiler), "/nologo", "/langversion:9", "/deterministic+", "/main:DeviceObserver",
               f"/out:{executable}", "/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll"]
    subprocess.run(command + [f"/reference:{dll / name}" for name in ASSEMBLIES] +
                   [str(inputs[1]), str(inputs[3])], check=True, cwd=repo, timeout=30)
    environment = os.environ.copy()
    for name in ("DISPLAY", "WAYLAND_DISPLAY"):
        environment.pop(name, None)
    environment.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll))
    environment["TMPDIR"] = str(build)
    summaries = {}
    modes = [("run-1", "symbols"), ("run-2", "symbols"), ("control", "off")]
    if args.with_callbacks:
        modes.append(("callbacks", "callbacks"))
    for label, mode in modes:
        run = output / label
        command = ["mono", str(executable), str(args.cue.resolve()), str(args.firmware_directory.resolve()),
                   str(run), str(args.frames), ",".join(map(str, starts)), args.bios_sha1.lower(), str(layout), mode]
        with (output / f"{label}.log").open("w") as log:
            subprocess.run(command, check=True, cwd=repo, env=environment, stdout=log,
                           stderr=subprocess.STDOUT, timeout=args.timeout)
        summaries[label] = verify_run(run, args.frames, mode != "off")
        print(f"Completed {label}, {args.frames} hardware frames and 120-frame restore comparison", flush=True)
    compare_observations(output / "run-1", output / "run-2", ("devices.jsonl", "av.jsonl", "graphics.jsonl", "continuation.jsonl"))
    compare_observations(output / "run-1", output / "control", ("av.jsonl",))
    if args.with_callbacks:
        compare_observations(output / "run-1", output / "callbacks", ("devices.jsonl", "av.jsonl", "graphics.jsonl", "continuation.jsonl"))
    for p in inputs:
        if digest(p) != tool_hashes[p.name]:
            raise ValueError("Observer source changed during capture")
    verify_pins(dll, RUNTIME_PINS)
    verify_pins(source, HEADER_PINS)
    manifest = {"schema": "openggf.soniccd.device-observer-manifest.v1", "purpose": "Research only, not trace v5",
                "bizhawk_commit": BIZHAWK_COMMIT, "gpgx_source_commit": GPGX_COMMIT, "runtime_sha256": binaries,
                "source_sha256": headers, "tools_sha256": tool_hashes, "layout_sha256": digest(layout),
                "compiler_sha256": digest(compiler), "media": media, "bios_sha1": args.bios_sha1.lower(),
                "frames": args.frames, "start_frames": starts, "modes": modes,
                "cold_boot_devices_equal": True, "observer_control_av_equal": True,
                "callbacks_equal": True if args.with_callbacks else None, "summaries": summaries,
                "streams_sha256": {label: {name: digest(output / label / name) for name in
                    ("devices.jsonl", "av.jsonl", "graphics.jsonl", "continuation.jsonl")} for label, mode in modes},
                "display": None, "audio_device": None}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(output / "manifest.json")


if __name__ == "__main__":
    main()
