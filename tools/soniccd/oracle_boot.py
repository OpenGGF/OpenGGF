#!/usr/bin/env python3
"""Build/run bounded BizHawk 2.11 CD boot observations for the 2026-10-23 CD-02 task.

Inputs are an installed BizHawk, user CUE/BIN files, an exact BIOS SHA1 and
an external output directory. No emulator, firmware or disc bytes ship here.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


COMPILER_SHA256 = "81e98ade50f3e4127237128211778bd6ebe0c3998c9cc2f5eb44f3196a0297f8"
ASSEMBLIES = (
    "BizHawk.Common.dll", "BizHawk.Emulation.Common.dll",
    "BizHawk.Emulation.Cores.dll", "BizHawk.Emulation.DiscSystem.dll",
    "BizHawk.BizInvoke.dll", "Newtonsoft.Json.dll",
)


def digest(path, algorithm="sha256"):
    h = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def media_identity(cue):
    cue = Path(cue).resolve(strict=True)
    references = re.findall(r'^\s*FILE\s+"([^"]+)"\s+BINARY\s*$',
                            cue.read_text(), flags=re.MULTILINE | re.IGNORECASE)
    if not references:
        raise ValueError("CUE must reference quoted BINARY files")
    tracks = []
    for name in references:
        path = cue.parent / name
        if Path(name).is_absolute() or not path.resolve().is_relative_to(cue.parent):
            raise ValueError("CUE track must remain within its media directory")
        tracks.append({"file": name, "bytes": path.stat().st_size, "sha256": digest(path)})
    identity = {"cue_sha256": digest(cue), "files": tracks}
    identity["source_fingerprint"] = hashlib.sha256(
        json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return identity


def compare_runs(left, right):
    """Reject differing observed frames, samples, memory/register states or captures."""
    left, right = Path(left), Path(right)
    for name in ("frames.jsonl", "av.jsonl"):
        if digest(left / name) != digest(right / name):
            raise ValueError(f"Repeated cold boot differs in {name}")
    captures = sorted(p.name for p in left.glob("frame-*.ppm"))
    if not captures or captures != sorted(p.name for p in right.glob("frame-*.ppm")):
        raise ValueError("Repeated cold boot capture inventory differs or is empty")
    for name in captures:
        if digest(left / name) != digest(right / name):
            raise ValueError(f"Repeated cold boot differs in {name}")
    a = json.loads((left / "summary.json").read_text())
    b = json.loads((right / "summary.json").read_text())
    if a != b:
        raise ValueError("Repeated cold boot summary differs")
    return {"av_sha256": digest(left / "av.jsonl"),
            "states_sha256": digest(left / "frames.jsonl"), "captures": len(captures)}


def verify_run(run, frames, starts):
    run = Path(run)
    summary = json.loads((run / "summary.json").read_text())
    if (summary["advanced_hardware_frames"] != frames or not summary["is_mega_cd"]
            or not summary["replay_120_frames_equal"] or summary["input_start_frames"] != starts):
        raise ValueError("Native run did not complete its requested CD scenario")
    av = [json.loads(line) for line in (run / "av.jsonl").read_text().splitlines()]
    if [row["frame"] for row in av] != list(range(1, frames + 1)):
        raise ValueError("Native AV observations are missing, repeated or out of order")
    if [row["frame"] for row in av if row["start"]] != starts:
        raise ValueError("Observed controller input differs from requested input")
    states = [json.loads(line) for line in (run / "frames.jsonl").read_text().splitlines()]
    if not states or states[-1]["frame"] != frames:
        raise ValueError("Final memory/register observation is missing")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bizhawk-home", type=Path, required=True)
    parser.add_argument("--cue", type=Path, required=True)
    parser.add_argument("--firmware-directory", type=Path, required=True)
    parser.add_argument("--bios-sha1", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frames", type=int, default=1800)
    parser.add_argument("--start-frames", default="600,900,1200")
    parser.add_argument("--repeat", type=int, default=2)
    parser.add_argument("--timeout", type=int, default=120, help="Seconds per native process")
    parser.add_argument("--compiler", type=Path,
                        default=Path("/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe"))
    parser.add_argument("--netstandard", type=Path,
                        default=Path("/usr/lib/mono/4.8-api/Facades/netstandard.dll"))
    args = parser.parse_args()
    if not 1 <= args.frames <= 36000 or not 1 <= args.repeat <= 5 or args.timeout < 1:
        parser.error("Require 1..36000 frames, 1..5 repeats and a positive timeout")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.bios_sha1):
        parser.error("BIOS SHA1 must be exactly 40 hex digits")
    starts = sorted(set(int(f) for f in args.start_frames.split(",") if f))
    if any(f < 1 or f > args.frames for f in starts):
        parser.error("Start frames must be within the capture bound")
    output = args.output.resolve()
    repo = Path(__file__).resolve().parents[2]
    protected = (repo, args.cue.resolve().parent, args.firmware_directory.resolve(),
                 args.bizhawk_home.resolve())
    if any(output.is_relative_to(p) or p.is_relative_to(output) for p in protected):
        parser.error("Output must be external and separate from source, installation and media")
    if output.exists():
        parser.error("Output already exists; choose a new observation directory")
    if digest(args.compiler) != COMPILER_SHA256:
        parser.error("Compiler does not match the pinned Roslyn 3.9 SHA256")
    dll = args.bizhawk_home.resolve(strict=True) / "dll"
    binaries = {name: digest(dll / name) for name in ASSEMBLIES + (
        "gpgx.wbx.zst", "libwaterboxhost.so")}
    identity = media_identity(args.cue)
    output.mkdir(parents=True)
    build = output / "build"
    build.mkdir()
    (build / "dll").symlink_to(dll, target_is_directory=True)
    source = Path(__file__).with_name("oracle_probe.cs").resolve()
    source_hash, wrapper_hash = digest(source), digest(__file__)
    executable = build / "oracle_probe.exe"
    command = ["mono", str(args.compiler), "/nologo", "/langversion:9", "/deterministic+",
               "/target:exe", f"/out:{executable}", f"/reference:{args.netstandard}"]
    command += [f"/reference:{dll / name}" for name in ASSEMBLIES]
    subprocess.run(command + [str(source)], check=True, timeout=args.timeout, cwd=repo)
    environment = os.environ.copy()
    for name in ("DISPLAY", "WAYLAND_DISPLAY"):
        environment.pop(name, None)
    environment["MONO_PATH"] = str(dll)
    environment["LD_LIBRARY_PATH"] = str(dll)
    comparisons = []
    for n in range(1, args.repeat + 1):
        run = output / f"run-{n}"
        command = ["mono", str(executable), str(args.cue.resolve()),
                   str(args.firmware_directory.resolve()), str(run), str(args.frames),
                   ",".join(map(str, starts)), args.bios_sha1.lower()]
        with (output / f"run-{n}.log").open("w") as log:
            subprocess.run(command, env=environment, cwd=repo, stdout=log,
                           stderr=subprocess.STDOUT, check=True, timeout=args.timeout)
        verify_run(run, args.frames, starts)
        if n > 1:
            comparisons.append(compare_runs(output / "run-1", run))
        print(f"Completed cold boot {n}/{args.repeat}", flush=True)
    if digest(source) != source_hash or digest(__file__) != wrapper_hash:
        raise ValueError("Oracle source changed during capture; provenance is incomplete")
    manifest = {"schema": "openggf.soniccd.native-boot.v1", "media": identity,
                "bios_sha1": args.bios_sha1.lower(), "installed_binaries_sha256": binaries,
                "probe_sha256": source_hash, "wrapper_sha256": wrapper_hash,
                "compiler_sha256": digest(args.compiler), "frames": args.frames,
                "start_frames": starts, "repetitions": args.repeat,
                "repeated_observations_equal": True if comparisons else None,
                "comparisons": comparisons, "display": None, "audio_device": None}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {output / 'manifest.json'}")


if __name__ == "__main__":
    main()
