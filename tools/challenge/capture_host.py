#!/usr/bin/env python3
"""Record the real Three Openings window and isolated OpenAL device output.

Inputs: built JVM classpath/jar, three original ROM paths, committed held-pad
program, outside-repository output directory. Linux X11/PulseAudio-compatible
PipeWire, Python Xlib and ffmpeg required. Origin: 2026-10-07 multigame prototype.
No gameplay state is supplied; commands/clock ownership stay in the host/worker.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid


def stop(process: subprocess.Popen | None) -> None:
    if process is None or process.poll() is not None:
        return
    process.send_signal(signal.SIGINT)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def window_for_pid(display, pid: int):
    atom = display.intern_atom("_NET_WM_PID")
    def visit(window, depth=0):
        try:
            value = window.get_full_property(atom, 0)
            if value is not None and int(value.value[0]) == pid and window.get_wm_name() == "Three Openings | OpenGGF":
                return window
            if depth < 3:
                for child in window.query_tree().children:
                    found = visit(child, depth + 1)
                    if found is not None:
                        return found
        except Exception:
            return None
        return None
    return visit(display.screen().root)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    for game in ("s1", "s2", "s3k"):
        parser.add_argument("--" + game, type=Path, required=True)
    parser.add_argument("--program", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("capture output must be outside the repository")
    output.mkdir(parents=True, exist_ok=True)
    from Xlib import display as xdisplay
    display = xdisplay.Display()
    token = uuid.uuid4().hex[:12]
    sink = "openggf_multigame_" + token
    module = None
    host = video = device_audio = None
    log_path = root / "target" / ("challenge-capture-" + token + ".log")
    log_path.parent.mkdir(exist_ok=True)
    log = log_path.open("wb")
    receipt = {"sink": sink, "owned_module": None, "processes": {}, "state": "starting"}
    receipt_path = output / "capture.json"
    def record():
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")
    record()
    try:
        result = subprocess.run(["pactl", "load-module", "module-null-sink", "sink_name=" + sink,
                                 "rate=48000", "channels=2"], check=True, text=True, capture_output=True)
        module = result.stdout.strip()
        if not module.isdigit():
            raise RuntimeError("Audio module returned no exact management ID")
        receipt["owned_module"] = module
        env = os.environ.copy()
        env["PULSE_SINK"] = sink
        env["ALSOFT_DRIVERS"] = "pulse"
        command = ["java", "-cp", args.classpath, "com.openggf.tools.challenge.ThreeOpeningsTool"]
        for game in ("s1", "s2", "s3k"):
            command += ["--" + game, str(getattr(args, game).resolve())]
        command += ["--program", str(args.program.resolve()), "--capture-dir", str(output)]
        receipt["command"] = command
        host = subprocess.Popen(command, cwd=root, env=env, stdout=log, stderr=log)
        receipt["processes"]["host"] = host.pid
        record()
        deadline = time.monotonic() + 30
        window = None
        while window is None and time.monotonic() < deadline and host.poll() is None:
            window = window_for_pid(display, host.pid)
            time.sleep(.1)
        if window is None:
            raise RuntimeError("Host window unavailable; inspect the bounded stderr tail")
        geometry = window.get_geometry()
        video = subprocess.Popen(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "x11grab",
            "-window_id", str(window.id), "-video_size", f"{geometry.width}x{geometry.height}",
            "-framerate", "30", "-draw_mouse", "0", "-i", env.get("DISPLAY", ":0"),
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "16", str(output / "window.mkv")],
            stdout=subprocess.DEVNULL, stderr=log)
        device_audio = subprocess.Popen(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "pulse",
            "-i", sink + ".monitor", "-ar", "48000", "-ac", "2", "-c:a", "pcm_s16le",
            str(output / "speaker.wav")], stdout=subprocess.DEVNULL, stderr=log)
        receipt["processes"].update(video=video.pid, device_audio=device_audio.pid)
        receipt["window_id"] = window.id
        receipt["state"] = "recording"
        record()
        deadline = time.monotonic() + 180
        while host.poll() is None:
            if (video.poll() is not None or device_audio.poll() is not None) and host.poll() is None:
                raise RuntimeError("A recorder stopped while the host was running")
            if time.monotonic() >= deadline:
                raise RuntimeError("Host exceeded the bounded capture duration")
            time.sleep(.2)
        code = host.returncode
        receipt["host_exit_code"] = code
        if code:
            raise RuntimeError("Host returned failure")
        receipt["state"] = "captured"
    finally:
        cleanup_errors = []
        for name, process in (("host", host), ("video", video), ("device_audio", device_audio)):
            try:
                stop(process)
            except Exception as failure:
                cleanup_errors.append(f"{name}: {failure}")
        log.close()
        if module is not None:
            try:
                subprocess.run(["pactl", "unload-module", module], check=True)
                receipt["owned_module"] = None
            except Exception as failure:
                cleanup_errors.append(f"audio module {module}: {failure}")
        receipt["process_exit_codes"] = {name: p.poll() for name, p in (("host", host), ("video", video), ("device_audio", device_audio)) if p is not None}
        receipt["cleanup"] = cleanup_errors or "all owned processes stopped; private audio module unloaded"
        record()
        display.close()
        text = log_path.read_text(errors="replace")
        print("\n".join(text.splitlines()[-25:]))
        log_path.unlink()
        if cleanup_errors:
            raise RuntimeError("Capture cleanup pending: " + "; ".join(cleanup_errors))
    print(f"Real window/device capture: {output}")


if __name__ == "__main__":
    main()
