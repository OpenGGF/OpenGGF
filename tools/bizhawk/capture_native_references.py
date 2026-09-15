#!/usr/bin/env python3
"""Capture native zone references with an explicit BizHawk 2.11 Lua exporter/plan.

Origin: 2026-09-15 SOZ methodology v2; extracted from the FBZ capture host.
Inputs: supplied ROM identity, BK2, exporter, plan and optional native save state.
Outputs: isolated configuration, launch log, hashes and exporter observations in
an unused task directory. Successful execution still requires evidence review.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
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


def argument_parser(description=__doc__):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--probe-framebuffer", type=Path)
    parser.add_argument("--bizhawk-home", type=Path)
    parser.add_argument("--exporter", type=Path, help="explicit diagnostic Lua script; hash recorded")
    parser.add_argument("--plan", type=Path, help="explicit Lua table-returning plan; copied and hashed")
    parser.add_argument("--fixture-state", type=Path, help="native saved state; identity recorded, never supplied to engine")
    parser.add_argument("--rom", type=Path)
    parser.add_argument("--rom-sha1", help="expected SHA-1 of the user-supplied ROM")
    parser.add_argument("--movie", type=Path)
    parser.add_argument("--output", type=Path, help="new external task directory")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--require-output", action="append", default=[], metavar="RELATIVE_PATH",
                        help="fail unless the exporter creates this nonempty file; repeatable")
    return parser


def run_capture(args, parser, *, plan_text=None, environment_aliases=None, entrypoint=None):
    if args.probe_framebuffer:
        try:
            result = probe(args.probe_framebuffer)
        except Exception as error:
            result = "FAIL " + type(error).__name__
        print(result)
        return 0 if result.startswith("PASS ") else 1
    for name in ("bizhawk_home", "rom", "rom_sha1", "movie", "exporter", "output"):
        if getattr(args, name) is None:
            parser.error("--" + name.replace("_", "-") + " is required")
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    if args.plan is not None and plan_text is not None:
        parser.error("--plan cannot be combined with a generated profile plan")
    if args.plan is None and plan_text is None:
        parser.error("--plan is required")
    try:
        home, rom, movie, exporter = (getattr(args, key).resolve(strict=True) for key in
                                     ("bizhawk_home", "rom", "movie", "exporter"))
        if args.plan is not None:
            plan_text = args.plan.resolve(strict=True).read_text()
        fixture_state = args.fixture_state.resolve(strict=True) if args.fixture_state else None
    except OSError as error:
        parser.error(str(error))
    output = args.output.resolve()
    if output.exists():
        parser.error("--output must not already exist")
    for name in args.require_output:
        path = Path(name)
        if path.is_absolute() or ".." in path.parts or not path.parts:
            parser.error("--require-output must be a relative file inside --output")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.rom_sha1) or digest(rom, "sha1") != args.rom_sha1.upper():
        parser.error("ROM does not match --rom-sha1")
    assembly = subprocess.check_output(["monodis", "--assembly", str(home / "EmuHawk.exe")],
                                       text=True, timeout=10)
    if not re.search(r"^Version:\s+2\.11\.0\.0$", assembly, re.MULTILINE):
        parser.error("EmuHawk must be official version 2.11.0.0")
    config = json.loads((home / "config.ini").read_text())
    output.mkdir(parents=True, exist_ok=False)
    for part in ("raw/time-series", "time-series/provenance", "provenance"):
        (output / part).mkdir(parents=True, exist_ok=True)
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
    (output / "plan.lua").write_text(plan_text)
    env = os.environ.copy()
    native_env = {"OGGF_NATIVE_PLAN": str(output / "plan.lua"),
                  "OGGF_NATIVE_OUTPUT": str(output), "OGGF_NATIVE_ROM_SHA1": digest(rom, "sha1"),
                  "OGGF_NATIVE_BK2_SHA256": digest(movie),
                  "OGGF_NATIVE_HOST_RECEIPT": str(output / "host.json"),
                  "OGGF_NATIVE_FRAMEBUFFER_PROBE": str(Path(__file__).resolve()),
                  "OGGF_NATIVE_PYTHON": sys.executable}
    if fixture_state:
        native_env["OGGF_NATIVE_FIXTURE_STATE"] = str(fixture_state)
    else:
        env.pop("OGGF_NATIVE_FIXTURE_STATE", None)
    env.update(native_env)
    for alias, name in (environment_aliases or {}).items():
        env.pop(alias, None)
        if name in native_env:
            env[alias] = native_env[name]
    env.update({"LD_LIBRARY_PATH": f"{home}/dll:{home}:/usr/lib/x86_64-linux-gnu",
                "MONO_CRASH_NOFILE": "1", "MONO_WINFORMS_XIM_STYLE": "disabled"})
    command = ["mono", str(home / "EmuHawk.exe"), "--audiosync", "false", "--config",
               str(output / "config.ini"), "--chromeless", "--lua", str(exporter),
               "--movie", str(movie), str(rom)]
    receipt = {"command": command, "rom_sha1": digest(rom, "sha1"), "bk2_sha256": digest(movie),
               "lua_sha256": digest(exporter), "plan_sha256": digest(output / "plan.lua"),
               "host_sha256": digest(Path(__file__)),
               "emuhawk_sha256": digest(home / "EmuHawk.exe"), "timeout_seconds": args.timeout,
               "client_common_sha256": digest(home / "dll/BizHawk.Client.Common.dll"),
               "gpgx_archive_sha256": digest(home / "dll/gpgx.wbx.zst"),
               "acceptance": "pending-independent-pixel-and-state-review"}
    if entrypoint:
        receipt["entrypoint_sha256"] = digest(Path(entrypoint))
    if fixture_state:
        receipt["fixture_state"] = str(fixture_state)
        receipt["fixture_state_sha256"] = digest(fixture_state)
    start = time.monotonic()
    failures = []
    try:
        with (output / "launch.log").open("w") as log:
            result = subprocess.run(command, cwd=output, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=args.timeout)
            receipt["exit_code"] = result.returncode
            if result.returncode:
                failures.append(f"emulator-exit-{result.returncode}")
    except subprocess.TimeoutExpired:
        receipt["timeout"] = True
        failures.append("timeout")
    except OSError as error:
        receipt["launch_error"] = str(error)
        failures.append("launch-error")
    # BizHawk can exit zero after a Lua assertion. The process exit alone is not
    # evidence that the exporter ran; observed script exceptions must fail closed.
    log_text = (output / "launch.log").read_text(errors="replace")
    if re.search(r"(?:LuaScriptException|LuaException):", log_text):
        failures.append("lua-script-error")
    for name in args.require_output:
        path = output / name
        if not path.resolve().is_relative_to(output) or not path.is_file() or path.stat().st_size == 0:
            failures.append("missing-or-empty-output:" + name)
    receipt["failures"] = failures
    receipt["execution_status"] = "failed" if failures else "completed"
    receipt["wall_seconds"] = round(time.monotonic() - start, 3)
    (output / "host.json").write_text(json.dumps(receipt, indent=2))
    print(json.dumps(receipt))
    return 1 if failures else 0


def main(argv=None):
    parser = argument_parser()
    return run_capture(parser.parse_args(argv), parser)


if __name__ == "__main__":
    sys.exit(main())
