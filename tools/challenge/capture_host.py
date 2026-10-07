#!/usr/bin/env python3
"""Record the real Three Openings window and isolated OpenAL device output.

Inputs: built JVM classpath/jar, three original ROM paths, committed held-pad
program, outside-repository output directory. Linux X11/PulseAudio-compatible
PipeWire, Python Xlib and ffmpeg required. Origin: 2026-10-07 multigame prototype.
No gameplay state is supplied; commands/clock ownership stay in the host/worker.
"""
from __future__ import annotations
import argparse
import csv
import json
import os
import re
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


def window_for_pid(display, pid: int, visible=True):
    from Xlib import X
    atom = display.intern_atom("_NET_WM_PID")
    def visit(window, depth=0):
        try:
            value = window.get_full_property(atom, 0)
            if (value is not None and len(value.value) == 1 and int(value.value[0]) == pid and window.get_wm_name() == "Three Openings | OpenGGF" and (not visible or window.get_attributes().map_state == X.IsViewable)
                    and window.get_geometry().width > 0 and window.get_geometry().height > 0):
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


def acquire_window(display, host, unmanaged=False):
    """Normal mapping by default; optional diagnostic mapping touches only this PID/title."""
    from Xlib import X
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline and host.poll() is None:
        window = window_for_pid(display, host.pid, visible=not unmanaged)
        if window is not None:
            if unmanaged:
                # Default-WM failures are evidence, never silently replaced.
                # This explicitly requested diagnostic omits WM decorations.
                window.change_attributes(override_redirect=True)
                window.map()
                display.sync()
                time.sleep(.3)
            validate_window(display, window, host)
            return window
        time.sleep(.1)
    raise RuntimeError("Host window unavailable; inspect the bounded stderr tail")


def validate_window(display, window, host):
    from Xlib import X
    prop = window.get_full_property(display.intern_atom("_NET_WM_PID"), X.AnyPropertyType)
    geometry = window.get_geometry()
    attributes = window.get_attributes()
    title = window.get_wm_name()
    if (host.poll() is not None or prop is None or len(prop.value) != 1
            or int(prop.value[0]) != host.pid or title != "Three Openings | OpenGGF"
            or attributes.map_state != X.IsViewable or geometry.width <= 0 or geometry.height <= 0):
        raise RuntimeError("Owned host PID/title/visibility/geometry invalid before recorder start")
    return {"window_id": window.id, "window_title": title, "window_map_state": attributes.map_state,
            "window_size": [geometry.width, geometry.height], "window_depth": geometry.depth}


def send_key(display, window, host, name):
    """Send an owned-window tap; a release after that window's exit is harmless."""
    from Xlib import X, XK, error
    from Xlib.protocol import event as xevent
    validate_window(display, window, host)
    code = display.keysym_to_keycode(XK.string_to_keysym(name))
    if not code:
        raise RuntimeError("Unavailable X11 key: " + name)
    window.set_input_focus(X.RevertToParent, X.CurrentTime)
    display.sync()
    for kind, mask in ((X.KeyPress, X.KeyPressMask), (X.KeyRelease, X.KeyReleaseMask)):
        if kind == X.KeyRelease and host.poll() is not None:
            return
        event_class = xevent.KeyPress if kind == X.KeyPress else xevent.KeyRelease
        event = event_class(detail=code, time=int(time.monotonic() * 1000) & 0xffffffff,
            root=display.screen().root, window=window, child=X.NONE,
            root_x=0, root_y=0, event_x=0, event_y=0, state=0, same_screen=1)
        errors = []
        window.send_event(event, event_mask=mask,
            onerror=lambda failure, request: errors.append(failure))
        display.sync()
        for failure in errors:
            if kind != X.KeyRelease or not isinstance(failure, error.BadWindow):
                raise RuntimeError("Owned-window key dispatch failed: " + str(failure))
        time.sleep(.075)


def native_environment(args, sink):
    env = os.environ.copy()
    env.update(PULSE_SINK=sink, ALSOFT_DRIVERS="pulse")
    if args.keyutils_preload:
        library = args.keyutils_preload.resolve(strict=True)
        env["LD_PRELOAD"] = str(library)
    if args.disable_vsync:
        env.update(vblank_mode="0", __GL_SYNC_TO_VBLANK="0")
    return env


def add_native_options(parser):
    parser.add_argument("--keyutils-preload", type=Path, help="Child-only existing library for OpenAL keyutils dependency")
    parser.add_argument("--disable-vsync", action="store_true", help="Child-only driver settings; host admission remains 60 Hz")
    parser.add_argument("--unmanaged-window", action="store_true",
        help="Explicit diagnostic mapping of only the owned PID/title window without WM decorations")


def recorded_frames(path):
    result = subprocess.run(["ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
        "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", str(path)],
        check=True, capture_output=True, text=True, timeout=15)
    count = int(result.stdout.strip())
    if count <= 0: raise RuntimeError("Window recorder produced no actual frames")
    return count


def worker_pids(host_pid: int) -> list[int]:
    """Find fixed worker children across every host thread, including its admission owner."""
    found = set()
    try:
        tasks = list(Path(f"/proc/{host_pid}/task").iterdir())
    except FileNotFoundError:
        return []
    for task in tasks:
        try:
            children = (task / "children").read_text().split()
        except FileNotFoundError:
            continue
        for text in children:
            pid = int(text)
            try:
                command = Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
                if b"com.openggf.tools.challenge.ChallengeWorker" in command:
                    found.add(pid)
            except FileNotFoundError:
                pass
    return sorted(found)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    for game in ("s1", "s2", "s3k"):
        parser.add_argument("--" + game, type=Path, required=True)
    parser.add_argument("--program", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cycle-sound", action="store_true",
        help="Tap host audio focus at committed ticks 900 and 1440; requires a longer program")
    add_native_options(parser)
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
    receipt = {"sink": sink, "owned_module": None, "processes": {}, "state": "starting",
               "sampled_peak_host_rss_kib": 0, "rss_sample_interval_seconds": .2}
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
        env = native_environment(args, sink)
        receipt["child_environment"] = {k: env[k] for k in ("PULSE_SINK", "ALSOFT_DRIVERS", "LD_PRELOAD", "vblank_mode", "__GL_SYNC_TO_VBLANK") if k in env}
        command = ["java", "-cp", args.classpath, "com.openggf.tools.challenge.ThreeOpeningsTool"]
        for game in ("s1", "s2", "s3k"):
            command += ["--" + game, str(getattr(args, game).resolve())]
        command += ["--program", str(args.program.resolve()), "--capture-dir", str(output)]
        receipt["command"] = command
        host = subprocess.Popen(command, cwd=root, env=env, stdout=log, stderr=log)
        receipt["processes"]["host"] = host.pid
        record()
        receipt["host_window_management"] = "owned override_redirect diagnostic" if args.unmanaged_window else "default"
        record()
        window = acquire_window(display, host, args.unmanaged_window)
        from Xlib import X
        window.set_input_focus(X.RevertToParent, X.CurrentTime)
        display.sync()
        receipt.update(validate_window(display, window, host))
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
        receipt["audio_focus_events"] = []
        record()
        deadline = time.monotonic() + 180
        focus_ticks = [900, 1440] if args.cycle_sound else []
        while host.poll() is None:
            try:
                for line in Path(f"/proc/{host.pid}/status").read_text().splitlines():
                    if line.startswith("VmRSS:"):
                        receipt["sampled_peak_host_rss_kib"] = max(
                            receipt["sampled_peak_host_rss_kib"], int(line.split()[1]))
            except (FileNotFoundError, ProcessLookupError):
                pass
            if (video.poll() is not None or device_audio.poll() is not None) and host.poll() is None:
                raise RuntimeError("A recorder stopped while the host was running")
            timeline = output / "presentation.csv"
            if focus_ticks and timeline.exists():
                with timeline.open() as source:
                    rows = [row for row in csv.DictReader(source)
                        if (row.get("tick") or "").isdigit() and row.get("scene") == "PLAY"
                        and row.get("window_focused") in ("true", "false")]
                if rows and int(rows[-1]["tick"]) >= focus_ticks[0]:
                    before = rows[-1]
                    send_key(display, window, host, "Tab")
                    receipt["audio_focus_events"].append({"requested_tick": focus_ticks.pop(0),
                        "observed_tick_before": int(before["tick"]), "focus_before": int(before["focus"])})
                    record()
            if time.monotonic() >= deadline:
                raise RuntimeError("Host exceeded the bounded capture duration")
            time.sleep(.2)
        code = host.returncode
        receipt["host_exit_code"] = code
        if code:
            raise RuntimeError("Host returned failure")
        if focus_ticks:
            raise RuntimeError("Program ended before all requested audio-focus observations")
        if args.cycle_sound:
            with (output / "presentation.csv").open() as source:
                played_focus = {row.get("focus") for row in csv.DictReader(source)
                    if row.get("scene") == "PLAY"}
            if not {"0", "1", "2"}.issubset(played_focus):
                raise RuntimeError("The host did not publish all three requested audio-focus states")
        receipt["state"] = "captured"
    except BaseException as failure:
        receipt["state"] = "failed"
        receipt["error"] = repr(failure)[:500]
        raise
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
        if video is not None:
            try: receipt["actual_window_frames"] = recorded_frames(output / "window.mkv")
            except Exception as failure: cleanup_errors.append(f"recorded frames: {failure}")
        receipt["process_exit_codes"] = {name: p.poll() for name, p in (("host", host), ("video", video), ("device_audio", device_audio)) if p is not None}
        if cleanup_errors:
            receipt["state"] = "failed"
        receipt["cleanup"] = cleanup_errors or "all owned processes stopped; private audio module unloaded"
        record()
        display.close()
        text = log_path.read_text(errors="replace")
        budget = re.search(r"Challenge steps=(\d+) p50=([\d.]+)ms p95=([\d.]+)ms p99=([\d.]+)ms", text)
        if budget:
            receipt["host_admission_to_publication"] = {
                "steps": int(budget[1]), "p50_ms": float(budget[2]),
                "p95_ms": float(budget[3]), "p99_ms": float(budget[4])}
            record()
        print("\n".join(text.splitlines()[-25:]))
        log_path.unlink()
        if cleanup_errors:
            raise RuntimeError("Capture cleanup pending: " + "; ".join(cleanup_errors))
    print(f"Real window/device capture: {output}")


if __name__ == "__main__":
    main()
