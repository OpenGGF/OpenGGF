#!/usr/bin/env python3
"""Produce partial native title stamp fixtures for CD-05, 2026-10-23.

Inputs are user-owned CUE/BIOS and the frozen CD-02 observer's verified ABI.
Generated RAM payloads and builds stay under Downloads/Sonic-CD/native/stamp-fixtures.
These research fixtures are comparison-only and do not implement trace v5.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from device_observer_run import (BIZHAWK_COMMIT, GPGX_COMMIT, HEADER_PINS,
                                 LAYOUT_SHA256, RUNTIME_PINS, verify_pins)
from oracle_boot import ASSEMBLIES, COMPILER_SHA256, digest, media_identity

OUTPUT_ROOT = Path.home() / "Downloads/Sonic-CD/native/stamp-fixtures"
OBSERVER_ROOT = Path.home() / "Downloads/Sonic-CD/native/device-observer"
SCHEMA = "openggf.soniccd.stamp-fixtures-manifest.v1"
# Exact reviewed wrapper revision: the sole change from the frozen observer is
# OUTPUT_ROOT = Path.home() / "Downloads/Sonic-CD/native/device-observer".
# Keep the original manifest immutable and record both identities in new captures.
PORTABLE_OBSERVER_WRAPPER_SHA256 = "c8db7929e5ac7f8e0dadfa2ad3181411e22edbd078d9553c5ebaa0326c916f52"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def continuation_image(ingress, devices, remaining):
    """Exact title mode-7 continuation of pinned gfx_render, without timing claims."""
    if devices["regs.58"] & 0xff != 7 or (devices["regs.02"] >> 3) & 3:
        raise ValueError("Only observed title mode 7 and normal priority are certified")
    ram = bytearray(ingress)
    cursor = devices["gfx_hw.tracePtr"]
    start = devices["gfx_hw.bufferStart"]
    width = devices["regs.62"] & 0x1ff
    touched = set()

    def word(offset):
        return (ram[offset & 0x3ffff] << 8) | ram[(offset + 1) & 0x3ffff]

    def transform(row, col, mask, orientation):
        if orientation & 4:
            col ^= mask
        if orientation & 2:
            col ^= mask
            row ^= mask
        if orientation & 1:
            col, row = row ^ mask, col
        return row, col

    for line in range(remaining):
        x, y, dx, dy = (word(cursor + n * 2) for n in range(4))
        cursor = (cursor + 8) & 0x3ffff
        x <<= 8
        y <<= 8
        dx = dx - 0x10000 if dx & 0x8000 else dx
        dy = dy - 0x10000 if dy & 0x8000 else dy
        destination = start + line * 8
        for _ in range(width):
            x &= devices["gfx_hw.dotMask"]
            y &= devices["gfx_hw.dotMask"]
            index = (x >> devices["gfx_hw.stampShift"]) | ((y >> devices["gfx_hw.stampShift"]) << devices["gfx_hw.mapShift"])
            stamp = word(devices["gfx_hw.mapPtr"] + index * 2)
            source = (stamp & 0x7fc) << 8
            pixel = 0
            if source:
                orientation = stamp >> 13
                row, col = transform((y >> 14) & 3, (x >> 14) & 3, 3, orientation)
                source |= (row + col * 4) << 6
                row, col = transform((y >> 11) & 7, (x >> 11) & 7, 7, orientation)
                source |= col + row * 8
                pair = ram[source >> 1]
                pixel = (pair & 15) if source & 1 else pair >> 4
            address = (destination >> 1) & 0x3ffff
            touched.add(destination & 0x7ffff)
            ram[address] = ((ram[address] & 0xf0) | pixel) if destination & 1 else ((ram[address] & 15) | (pixel << 4))
            destination += 1 if destination & 7 != 7 else devices["gfx_hw.bufferOffset"]
            x += dx
            y += dy
    return bytes(ram), touched


def validate_fixture(directory, fixture):
    if (fixture["callback_address"] & 0xfffffe != 0xa12002 or fixture["callback_flags"] != 0x1000
            or fixture["callback_value"] & 1 != 1 or fixture["egress_frame_call"] <= fixture["ingress_frame"]):
        raise ValueError("Egress is not a later main-CPU ownership read")
    if fixture["observation_kind"] != "partial-device-continuation":
        raise ValueError("A partial ingress cannot claim a full submission")
    payloads = []
    for side in ("ingress", "egress"):
        name = fixture[f"{side}_file"]
        if Path(name).name != name:
            raise ValueError("Payload path escapes fixture directory")
        with gzip.open(directory / name, "rb") as stream:
            data = stream.read(0x40001)
        if len(data) != 0x40000 or sha(data) != fixture[f"{side}_sha256"]:
            raise ValueError(f"Invalid {side} payload extent or hash")
        payloads.append(data)
    before, after = payloads
    request = fixture["request"]
    devices, end = fixture["ingress_devices"], fixture["egress_devices"]
    # Pinned scd.c:1299-1393: MODE=bit 2, RET=bit 0, DMNA=bit 1;
    # gfx.c:581 selects priority from bits 3-4. Only 2M/sub, normal is covered.
    if (devices["regs.02"] & 7 != 2 or devices["regs.02"] & 0x18
            or request["wordram_mode_register"] != devices["regs.02"]):
        raise ValueError("Ingress requires matching raw 2M sub-owner, normal-priority mode")
    # gfx_start/gfx_update (gfx.c:671,709) set/clear only the high busy byte.
    if (devices["regs.58"] != request["stamp_size_register"] | 0x8000
            or end["regs.58"] != request["stamp_size_register"]
            or (end["regs.02"] ^ devices["regs.02"]) & 0x1c):
        raise ValueError("Request stamp mode or Word RAM mode/priority changed before egress")
    completed, remaining = request["completed_lines"], request["remaining_lines"]
    if completed < 1 or remaining < 1 or completed + remaining != 96 or request["reconstructed_total_lines"] != 96:
        raise ValueError("Invalid declared partial progress")
    if (request["trace_base"], request["map_offset"], request["width"],
            request["stamp_size_register"], request["image_start_register"]) != (0x1c800, 0x20000, 256, 7, 0x7400):
        raise ValueError("Uncertified title descriptor")
    if (fixture["image_base"], fixture["image_bytes"], fixture["wordram_bytes"], fixture["wordram_encoding"]) != (0x1d000, 12288, 0x40000, "68000-address-order-big-endian"):
        raise ValueError("Invalid declared image extent or encoding")
    trace = request["trace_base"]
    words = [int.from_bytes(before[n:n + 2], "big") for n in range(trace, trace + 96 * 8, 2)]
    if words != request["trace_words"]:
        raise ValueError("Trace words differ from bound ingress RAM")
    if (devices["gfx_hw.tracePtr"] != trace + completed * 8 or request["cursor"] != devices["gfx_hw.tracePtr"]
            or devices["gfx_hw.bufferStart"] != 0x3a000 + completed * 8
            or devices["regs.64"] & 255 != remaining):
        raise ValueError("Ingress cursor, ownership or remaining count disagrees")
    expected_fields = {"gfx_hw.dotMask": 0x7fffff, "gfx_hw.stampShift": 16,
                       "gfx_hw.mapShift": 7, "gfx_hw.mapPtr": 0x20000,
                       "gfx_hw.bufferOffset": 761, "regs.5a": 0x8000, "regs.5c": 11, "regs.60": 0,
                       "regs.5e": 0x7400, "regs.62": 256, "regs.66": 0x7200}
    if any(devices[key] != value for key, value in expected_fields.items()):
        raise ValueError("Native derived descriptor disagrees with title setup")
    if (end["regs.02"] & 7 != 1 or end["regs.64"] != 0
            or end["gfx_hw.tracePtr"] != trace + 96 * 8
            or end["gfx_hw.bufferStart"] != 0x3a000 + 96 * 8):
        raise ValueError("Egress ownership or completed cursor disagrees")
    for key in expected_fields:
        if end[key] != devices[key]:
            raise ValueError("Request descriptor changed before egress")
    predicted, touched = continuation_image(before, devices, remaining)
    base, size = fixture["image_base"], fixture["image_bytes"]
    if any(not base * 2 <= dot < (base + size) * 2 for dot in touched) or len(touched) != remaining * 256:
        raise ValueError("Touched nibble extent is not the declared continuation")
    if before[:base] != after[:base] or before[base + size:] != after[base + size:]:
        raise ValueError("Another owner changed Word RAM outside the image")
    if predicted != after:
        at = next(n for n, (a, b) in enumerate(zip(predicted, after)) if a != b)
        raise ValueError(f"Exact native continuation mismatch at Word RAM byte {at:#x}")
    return {"ordinal": fixture["ordinal"], "completed_lines": completed, "remaining_lines": remaining,
            "trace_sha256": sha(before[trace:trace + 768]), "image_sha256": sha(after[base:base + size]),
            "image_bytes_compared": size, "wordram_bytes_compared": len(after), "touched_nibbles": len(touched)}


def validate_run(directory, frames, count):
    summary = json.loads((directory / "summary.json").read_text())
    if summary["frames"] != frames or not summary["restore_120_frames_equal"] or not summary["observation_savestate_unchanged"]:
        raise ValueError("Incomplete restore or observer side-effect check")
    rows = [json.loads(line) for line in (directory / "av.jsonl").read_text().splitlines()]
    if [row["frame"] for row in rows] != list(range(1, frames + 1)):
        raise ValueError("Missing or reordered AV frames")
    fixtures = json.loads((directory / "fixtures.json").read_text())
    if len(fixtures) != count or summary["fixture_count"] != count or summary["wordram_accesses_before_egress"]:
        raise ValueError("Missing fixtures or premature image consumption")
    if [f["ordinal"] for f in fixtures] != list(range(1, count + 1)):
        raise ValueError("Missing or reordered request ordinal")
    evidence = [validate_fixture(directory, f) for f in fixtures]
    if count and (len({r["trace_sha256"] for r in evidence}) != count or len({r["image_sha256"] for r in evidence}) != count):
        raise ValueError("Fixtures do not contain distinct native operations")
    return summary, evidence


def compare_runs(left, right, fixtures=True):
    names = ["av.jsonl", "continuation.jsonl"]
    if fixtures:
        names.append("fixtures.json")
        a = json.loads((left / "fixtures.json").read_text())
        for f in a:
            names += [f["ingress_file"], f["egress_file"]]
    for name in names:
        if digest(left / name) != digest(right / name):
            raise ValueError(f"Repeated native/control bytes differ in {name}")
    a, b = (json.loads((p / "summary.json").read_text()) for p in (left, right))
    if a["checkpoint_sha256"] != b["checkpoint_sha256"]:
        raise ValueError("Observer changed native checkpoint")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bizhawk-home", type=Path, required=True)
    parser.add_argument("--cue", type=Path, required=True)
    parser.add_argument("--firmware-directory", type=Path, required=True)
    parser.add_argument("--bios-sha1", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frames", type=int, default=1500)
    parser.add_argument("--fixtures", type=int, default=6)
    parser.add_argument("--start-frames", default="600,900,1200")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    output = args.output.resolve()
    if output == OUTPUT_ROOT or not output.is_relative_to(OUTPUT_ROOT) or output.exists():
        parser.error("Require a new child under the external stamp-fixtures root")
    if not 1 <= args.frames <= 1800 or not 3 <= args.fixtures <= 16 or args.timeout < 1 or not re.fullmatch(r"[0-9a-fA-F]{40}", args.bios_sha1):
        parser.error("Invalid bounded capture arguments")
    starts = sorted({int(x) for x in args.start_frames.split(",") if x})
    if any(f < 1 or f > args.frames for f in starts):
        parser.error("Start input outside capture interval")
    repo = Path(__file__).resolve().parents[2]
    dll = args.bizhawk_home.resolve(strict=True) / "dll"
    layout = OBSERVER_ROOT / "usa-title-final-1800/layout.tsv"
    if digest(layout) != LAYOUT_SHA256:
        raise ValueError("Frozen observer layout mismatch")
    runtime = verify_pins(dll, RUNTIME_PINS)
    verify_pins(OBSERVER_ROOT / "source", HEADER_PINS)
    provenance = json.loads((OBSERVER_ROOT / "source-provenance.json").read_text())
    for name in ("gfx.c", "gfx.h", "scd.c", "scd.h"):
        data = (OBSERVER_ROOT / "source" / name).read_bytes()
        blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
        if blob != provenance["source_git_blobs_verified"]["core/cd_hw/" + name]:
            raise ValueError("Pinned source blob changed")
    compiler = Path("/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe")
    if digest(compiler) != COMPILER_SHA256:
        raise ValueError("Pinned compiler mismatch")
    sources = [Path(__file__), Path(__file__).with_name("stamp_fixture_probe.cs"),
               Path(__file__).with_name("device_observer_probe.cs"), Path(__file__).with_name("oracle_probe.cs"),
               Path(__file__).with_name("device_observer_run.py"), Path(__file__).with_name("oracle_boot.py")]
    hashes = {p.name: digest(p) for p in sources}
    frozen = json.loads((OBSERVER_ROOT / "usa-title-final-1800/manifest.json").read_text())["tools_sha256"]
    for name in ("device_observer_probe.cs", "oracle_probe.cs", "oracle_boot.py"):
        if hashes[name] != frozen[name]:
            raise ValueError("Frozen observer producer changed")
    if hashes["device_observer_run.py"] != PORTABLE_OBSERVER_WRAPPER_SHA256:
        raise ValueError("Reviewed portable observer wrapper changed")
    media = media_identity(args.cue)
    output.mkdir(parents=True)
    build = output / "build"
    build.mkdir()
    (build / "dll").symlink_to(dll, target_is_directory=True)
    executable = build / "stamp_fixture.exe"
    command = ["mono", str(compiler), "/nologo", "/langversion:9", "/deterministic+", "/main:StampFixtureProbe",
               f"/out:{executable}", "/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll"]
    subprocess.run(command + [f"/reference:{dll / name}" for name in ASSEMBLIES] + [str(p) for p in sources[1:4]],
                   check=True, cwd=repo, timeout=30)
    environment = os.environ.copy()
    for name in ("DISPLAY", "WAYLAND_DISPLAY"):
        environment.pop(name, None)
    environment.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll), TMPDIR=str(build))
    summaries, evidence = {}, {}
    for label, mode in (("run-1", "observe"), ("run-2", "observe"), ("control", "control")):
        with (output / f"{label}.log").open("w") as log:
            subprocess.run(["mono", str(executable), str(args.cue.resolve()), str(args.firmware_directory.resolve()),
                            str(output / label), str(args.frames), ",".join(map(str, starts)), args.bios_sha1.lower(),
                            str(layout), mode, str(args.fixtures)], check=True, timeout=args.timeout,
                           cwd=repo, env=environment, stdout=log, stderr=subprocess.STDOUT)
        summaries[label], evidence[label] = validate_run(output / label, args.frames, args.fixtures if mode == "observe" else 0)
        print(f"Completed {label}, exact native fixtures {len(evidence[label])}", flush=True)
    compare_runs(output / "run-1", output / "run-2")
    compare_runs(output / "run-1", output / "control", fixtures=False)
    prior = OBSERVER_ROOT / "usa-title-final-1800/run-1/av.jsonl"
    prior_rows = prior.read_text().splitlines()[:args.frames]
    if (output / "run-1/av.jsonl").read_text().splitlines() != prior_rows:
        raise ValueError("AV differs from frozen original native boot")
    if media_identity(args.cue) != media:
        raise ValueError("User disc changed during capture")
    if any(digest(p) != hashes[p.name] for p in sources):
        raise ValueError("Producer changed during capture")
    verify_pins(dll, RUNTIME_PINS)
    manifest = {"schema": SCHEMA, "purpose": "Partial pure-device comparison only, not trace v5 or gameplay input",
                "bizhawk_commit": BIZHAWK_COMMIT, "gpgx_source_commit": GPGX_COMMIT,
                "runtime_sha256": runtime, "layout_sha256": digest(layout), "compiler_sha256": digest(compiler),
                "source_provenance_sha256": digest(OBSERVER_ROOT / "source-provenance.json"),
                "producer_executable_sha256": digest(executable),
                "producer_sha256": hashes, "media": media, "bios_sha1": args.bios_sha1.lower(),
                "observer_wrapper_revision": {
                    "frozen_sha256": frozen["device_observer_run.py"],
                    "current_sha256": hashes["device_observer_run.py"],
                    "change": "Only OUTPUT_ROOT literal replaced by Path.home() / Downloads/Sonic-CD/native/device-observer; native observer sources unchanged"},
                "frames": args.frames, "start_frames": starts, "fixtures": args.fixtures,
                "repeat_equal": True, "control_av_equal": True, "prior_boot_av_equal": True,
                "summaries": summaries, "exact_comparisons": evidence,
                "streams_sha256": {label: {name: digest(output / label / name) for name in
                    ("fixtures.json", "av.jsonl", "continuation.jsonl", "summary.json")} for label in summaries}}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(output / "manifest.json")


if __name__ == "__main__":
    main()
