#!/usr/bin/env python3
"""Capture native FBZ evidence using official BizHawk 2.11 and a supplied ROM/BK2.

Origin: 2026-09-14 FBZ completion. Inputs are explicit immutable source paths;
outputs/configuration stay in a new external task directory. The PNG probe
observes rendered content, never claims to read hardware registers or writes RAM.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time


def digest(path, algorithm="sha256"):
    return hashlib.new(algorithm, path.read_bytes()).hexdigest().upper()


def framebuffer_content(size, pixel_at):
    """Inspect active pixels; kept independent of image decoding for guard tests."""
    if size == (348, 240):
        left, top = 14, 8
    elif size == (320, 224):
        left, top = 0, 0
    else:
        return None
    # A blanked GPGX scanline uses one backdrop index. Require horizontal
    # content on separated rows, excluding border or palette-only gradients.
    # Lua separately gates title/fade/overlays; human review owns feature meaning.
    rows = [y for y in range(224) if any(pixel_at(left + x, top + y)
            != pixel_at(left, top + y) for x in range(1, 320))]
    return rows if len(rows) >= 2 and rows[-1] - rows[0] >= 112 else None


def probe(path):
    from PIL import Image
    with Image.open(path) as image:
        pixels = image.convert("RGB").load()
        rows = framebuffer_content(image.size, lambda x, y: pixels[x, y])
        if rows is None:
            return "FAIL insufficient-active-framebuffer-content"
        return f"PASS {digest(path)} {len(rows)} {rows[0]} {rows[-1]}"


def capture_plan(start_frame: int, window: int) -> str:
    channels = ("200", "208", "210", "230", "238")
    return ('return {manifest_sha256="D13D037BAF52BBD65D28096A71A54ACACB4229B8C4C560C76DCB921E90DC40DD",'
            'bk2_frame_offset=237913,observation_limit_frames=' + str(window)
            + ',checkpoints={{id="fbz1-start-outdoor",bk2_frame=' + str(start_frame) + '}}'
            + ',cadence_series={' + ','.join('["aniplc-cadence-' + ch + '"]={' + str(start_frame) + '}'
                                            for ch in channels) + '}}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probe-framebuffer", type=Path)
    parser.add_argument("--bizhawk-home", type=Path)
    parser.add_argument("--exporter", type=Path, help="explicit diagnostic Lua override; hash recorded")
    parser.add_argument("--rom", type=Path)
    parser.add_argument("--movie", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--start-frame", type=int, default=237914)
    parser.add_argument("--window", type=int, default=1024)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    if args.probe_framebuffer:
        try:
            print(probe(args.probe_framebuffer))
        except Exception as error:
            print("FAIL " + type(error).__name__)
        return
    for name in ("bizhawk_home", "rom", "movie", "output"):
        if getattr(args, name) is None:
            parser.error("--" + name.replace("_", "-") + " is required")
    home, rom, movie, output = (getattr(args, key).resolve() for key in
                                ("bizhawk_home", "rom", "movie", "output"))
    if digest(rom, "sha1") != "CFBF98C36C776677290A872547AC47C53D2761D6":
        parser.error("ROM must be the verified locked-on S3K image")
    if args.start_frame < 237914 or args.window < 1 or args.timeout < 1:
        parser.error("invalid capture window or timeout")
    assembly = subprocess.check_output(["monodis", "--assembly", str(home / "EmuHawk.exe")],
                                       text=True, timeout=10)
    if not re.search(r"^Version:\s+2\.11\.0\.0$", assembly, re.MULTILINE):
        parser.error("EmuHawk must be official version 2.11.0.0")
    output.mkdir(parents=True, exist_ok=False)
    for part in ("raw/time-series", "time-series/provenance", "provenance"):
        (output / part).mkdir(parents=True, exist_ok=True)
    config = json.loads((home / "config.ini").read_text())
    for key in ("SoundEnabled", "SoundEnabledNormal", "SoundEnabledRWFF", "SoundThrottle",
                "StartPaused", "DisplayFps", "DisplayFrameCounter", "DisplayLagCounter",
                "DisplayInput", "DisplayRerecordCount", "DisplayMessages", "DisplayRamWatch",
                "DisplaySubtitles"):
        config[key] = False
    config["RunLuaDuringTurbo"] = True
    for entry in config["PathEntries"]["Paths"]:
        path = home / "Firmware" if entry["Type"] == "Firmware" else output / "runtime" / entry["System"] / entry["Type"].replace(" ", "_")
        if entry["Type"] != "Firmware":
            path.mkdir(parents=True, exist_ok=True)
        entry["Path"] = str(path)
    (output / "config.ini").write_text(json.dumps(config))
    (output / "plan.lua").write_text(capture_plan(args.start_frame, args.window))
    exporter = (args.exporter or Path(__file__).with_suffix(".lua")).resolve()
    env = os.environ.copy()
    env.update({"LD_LIBRARY_PATH": f"{home}/dll:{home}:/usr/lib/x86_64-linux-gnu",
                "MONO_CRASH_NOFILE": "1", "MONO_WINFORMS_XIM_STYLE": "disabled",
                "OGGF_FBZ_VISUAL_PLAN": str(output / "plan.lua"),
                "OGGF_FBZ_VISUAL_OUTPUT": str(output), "OGGF_FBZ_ROM_SHA1": digest(rom, "sha1"),
                "OGGF_FBZ_BK2_SHA256": digest(movie),
                "OGGF_FBZ_HOST_RECEIPT": str(output / "host.json"),
                "OGGF_FBZ_FRAMEBUFFER_PROBE": str(Path(__file__).resolve()),
                "OGGF_FBZ_PYTHON": sys.executable})
    command = ["mono", str(home / "EmuHawk.exe"), "--audiosync", "false", "--config",
               str(output / "config.ini"), "--chromeless", "--lua", str(exporter),
               "--movie", str(movie), str(rom)]
    receipt = {"command": command, "rom_sha1": digest(rom, "sha1"), "bk2_sha256": digest(movie),
               "lua_sha256": digest(exporter), "host_sha256": digest(Path(__file__)),
               "emuhawk_sha256": digest(home / "EmuHawk.exe"), "timeout_seconds": args.timeout,
               "client_common_sha256": digest(home / "dll/BizHawk.Client.Common.dll"),
               "gpgx_archive_sha256": digest(home / "dll/gpgx.wbx.zst"),
               "acceptance": "pending-independent-pixel-and-state-review"}
    start = time.monotonic()
    try:
        with (output / "launch.log").open("w") as log:
            result = subprocess.run(command, cwd=output, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=args.timeout)
            receipt["exit_code"] = result.returncode
    except subprocess.TimeoutExpired:
        receipt["timeout"] = True
    finally:
        receipt["wall_seconds"] = round(time.monotonic() - start, 3)
        (output / "host.json").write_text(json.dumps(receipt, indent=2))
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
