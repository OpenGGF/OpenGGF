#!/usr/bin/env python3
"""Exercise Three Openings through its actual X11/UI and owned worker processes.

Inputs: built classpath, original ROM paths, outside-repository evidence folder.
Checks: loading cancellation, pause with an in-flight stopped worker, focus,
restart, worker fault/retry, missing ROM and repeated clean exit. No gameplay
values or per-world input are injected. Origin: 2026-10-07 multigame prototype.
Linux X11, Python Xlib, ffmpeg and PulseAudio-compatible PipeWire are required.
"""
from __future__ import annotations
import argparse
import csv
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid
from capture_host import stop, window_for_pid, worker_pids, validate_window, native_environment, add_native_options, recorded_frames


def wait_for(check, description, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = check()
        if value:
            return value
        time.sleep(.025)
    raise RuntimeError("Timed out: " + description)


class Session:
    def __init__(self, args, output, sink, missing=False):
        from Xlib import display
        self.display = display.Display()
        self.output = output
        output.mkdir(parents=True, exist_ok=True)
        self.timeline = output / "presentation.csv"
        self.owned_workers = set()
        self.stopped = set()
        self.log_path = Path(__file__).resolve().parents[2] / "target" / ("challenge-ui-" + uuid.uuid4().hex + ".log")
        self.log = self.log_path.open("wb")
        command = ["java", "-cp", args.classpath, "com.openggf.tools.challenge.ThreeOpeningsTool"]
        for game in ("s1", "s2", "s3k"):
            rom = output / "absent-s2.gen" if missing and game == "s2" else getattr(args, game).resolve()
            command += ["--" + game, str(rom)]
        command += ["--capture-dir", str(output)]
        env = native_environment(args, sink)
        self.host = subprocess.Popen(command, cwd=Path(__file__).resolve().parents[2], env=env,
                                     stdout=self.log, stderr=self.log)
        self.command = command
        self.video = None
        try:
            self.window = wait_for(lambda: window_for_pid(self.display, self.host.pid), "own host window")
            from Xlib import X
            self.window.set_input_focus(X.RevertToParent, X.CurrentTime)
            self.display.sync()
            self.window_receipt = validate_window(self.display, self.window, self.host)
            geometry = self.window.get_geometry()
            self.video = subprocess.Popen(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "x11grab",
                "-window_id", str(self.window.id), "-video_size", f"{geometry.width}x{geometry.height}",
                "-framerate", "30", "-draw_mouse", "0", "-i", env.get("DISPLAY", ":0"),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "16", str(output / "window.mkv")],
                stdout=subprocess.DEVNULL, stderr=self.log)
        except Exception:
            self.close()
            raise

    def rows(self):
        if not self.timeline.exists():
            return []
        with self.timeline.open() as source:
            return [row for row in csv.DictReader(source)
                    if (row.get("tick") or "").isdigit() and row.get("window_focused") in ("true", "false")]

    def latest(self):
        rows = self.rows()
        return rows[-1] if rows else {}

    def scene(self, scene, seconds=45):
        return wait_for(lambda: self.latest() if self.latest().get("scene") == scene else None,
                        "scene " + scene, seconds)

    def key(self, name):
        from Xlib import X, XK
        from Xlib.protocol import event as xevent
        code = self.display.keysym_to_keycode(XK.string_to_keysym(name))
        if not code:
            raise RuntimeError("Unavailable X11 key: " + name)
        self.window.set_input_focus(X.RevertToParent, X.CurrentTime)
        for kind, mask in ((X.KeyPress, X.KeyPressMask), (X.KeyRelease, X.KeyReleaseMask)):
            event_class = xevent.KeyPress if kind == X.KeyPress else xevent.KeyRelease
            event = event_class(detail=code,
                time=int(time.monotonic() * 1000) & 0xffffffff,
                root=self.display.screen().root, window=self.window, child=X.NONE,
                root_x=0, root_y=0, event_x=0, event_y=0, state=0, same_screen=1)
            self.window.send_event(event, event_mask=mask)
            self.display.flush()
            time.sleep(.075)

    def workers(self):
        found = worker_pids(self.host.pid)
        self.owned_workers.update(found)
        return found

    def signal_worker(self, pid, sig):
        if pid not in self.workers():
            raise RuntimeError("Process is no longer this host's owned worker")
        os.kill(pid, sig)
        if sig == signal.SIGSTOP:
            self.stopped.add(pid)
        elif sig in (signal.SIGCONT, signal.SIGKILL):
            self.stopped.discard(pid)

    def start(self):
        self.key("Return")
        self.scene("READY")
        self.key("Return")
        self.scene("PLAY")
        wait_for(lambda: int(self.latest().get("tick", 0)) >= 8, "committed gameplay ticks")

    def require_clean_exit(self):
        try:
            code = self.host.wait(timeout=10)
        except subprocess.TimeoutExpired:
            # Diagnose only the exact process this probe launched. The bounded
            # dump is consumed with the temporary log during cleanup.
            try:
                subprocess.run(["jcmd", str(self.host.pid), "Thread.print"],
                               stdout=self.log, stderr=self.log, timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                pass
            raise RuntimeError(f"Owned host {self.host.pid} did not exit within10s") from None
        if code != 0:
            raise AssertionError(f"Owned host exited with code{code}")

    def close(self):
        for pid in list(self.stopped):
            try:
                self.signal_worker(pid, signal.SIGCONT)
            except (ProcessLookupError, RuntimeError):
                pass
        failures = []
        for name, process in (("host", self.host), ("video", self.video)):
            try:
                stop(process)
            except Exception as failure:
                failures.append(f"{name}: {failure}")
        self.log.close()
        self.display.close()
        if self.video is not None:
            try:
                self.window_receipt["actual_window_frames"] = recorded_frames(self.output / "window.mkv")
                (self.output / "window.json").write_text(json.dumps(self.window_receipt, indent=2) + "\n")
            except Exception as failure: failures.append(f"recorded frames: {failure}")
        lines = self.log_path.read_text(errors="replace").splitlines()
        print("\n".join(lines[-15:]))
        self.log_path.unlink()
        alive = [pid for pid in self.owned_workers if Path(f"/proc/{pid}").exists()]
        if alive:
            failures.append("Owned workers remain: " + str(alive))
        if failures:
            raise RuntimeError("; ".join(failures))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True)
    for game in ("s1", "s2", "s3k"):
        parser.add_argument("--" + game, type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--missing-rom-only", action="store_true", help="Run only title/ROM-fault/exit without gameplay workers")
    add_native_options(parser)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("evidence must be outside the repository")
    output.mkdir(parents=True, exist_ok=True)
    sink = "openggf_lifecycle_" + uuid.uuid4().hex[:12]
    module = subprocess.run(["pactl", "load-module", "module-null-sink", "sink_name=" + sink,
        "rate=48000", "channels=2"], check=True, capture_output=True, text=True).stdout.strip()
    if not module.isdigit():
        raise RuntimeError("No exact private audio module ID")
    result = {"state": "running", "module": module, "checks": [], "sessions": [],
              "scope": "missing ROM only" if args.missing_rom_only else "full UI lifecycle"}
    receipt = output / "lifecycle.json"
    receipt.write_text(json.dumps(result, indent=2) + "\n")
    session = None
    device_audio = None
    try:
        device_audio = subprocess.Popen(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "pulse",
            "-i", sink + ".monitor", "-ar", "48000", "-ac", "2", "-c:a", "pcm_s16le",
            str(output / "speaker.wav")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        result["device_audio_pid"] = device_audio.pid
        if not args.missing_rom_only:
            session = Session(args, output / "valid", sink)
            result["sessions"].append({"host": session.host.pid, "command": session.command})
            session.scene("TITLE")
            session.key("Return")
            session.scene("LOADING")
            cancelled = wait_for(session.workers, "acquired loading worker")
            session.key("Escape")
            session.scene("TITLE")
            wait_for(lambda: not session.workers(), "loading cancellation cleanup", 10)
            result["checks"].append({"loading_cancel": "pass", "workers": cancelled})
            session.start()
            worker = session.workers()[0]
            session.signal_worker(worker, signal.SIGSTOP)
            wait_for(lambda: session.latest().get("pending") == "true", "stalled pending step")
            time.sleep(.15)
            before = int(session.latest()["tick"])
            session.key("p")
            session.scene("PAUSE")
            if session.latest()["pending"] != "true":
                raise AssertionError("Pause did not observe the deliberately stalled step")
            session.signal_worker(worker, signal.SIGCONT)
            wait_for(lambda: session.latest().get("pending") == "false", "paused tuple completion")
            after = int(session.latest()["tick"])
            if after != before + 1:
                raise AssertionError("Pause did not commit exactly one in-flight tuple")
            time.sleep(.35)
            if int(session.latest()["tick"]) != after:
                raise AssertionError("Paused host admitted another step")
            result["checks"].append({"inflight_pause": "pass", "before": before, "after": after})
            session.key("p")
            session.scene("PLAY")
            session.key("Tab")
            wait_for(lambda: session.latest().get("focus") == "1", "audio focus selection")
            result["checks"].append({"focus": "pass"})
            original_generation = int(session.latest()["generation"])
            session.key("r")
            session.scene("READY")
            if int(session.latest()["generation"]) != original_generation + 1:
                raise AssertionError("Restart did not change generation")
            session.key("Return")
            session.scene("PLAY")
            worker = session.workers()[0]
            session.signal_worker(worker, signal.SIGKILL)
            session.scene("FAULT")
            wait_for(lambda: not session.workers(), "fault sibling teardown", 10)
            time.sleep(.4)
            result["checks"].append({"restart_fault": "pass"})
            session.start()
            session.key("Escape")
            session.scene("TITLE")
            session.key("Escape")
            session.require_clean_exit()
            session.close()
            session = None
            result["checks"].append({"retry_exit": "pass"})
        session = Session(args, output / "missing-rom", sink, missing=True)
        result["sessions"].append({"host": session.host.pid, "command": session.command})
        session.scene("TITLE")
        time.sleep(.5)
        session.key("Return")
        session.scene("FAULT")
        if session.workers():
            raise AssertionError("Missing ROM launched gameplay workers")
        time.sleep(.4)
        session.key("Escape")
        session.scene("TITLE")
        session.key("Escape")
        session.require_clean_exit()
        session.close()
        session = None
        result["checks"].append({"missing_rom_exit": "pass"})
        result["state"] = "pass"
    except BaseException as failure:
        result["state"] = "failed"
        result["error"] = f"{type(failure).__name__}: {str(failure)[:500]}"
        raise
    finally:
        failures = []
        if session is not None:
            try:
                session.close()
            except Exception as failure:
                failures.append(str(failure))
        try:
            stop(device_audio)
            result["device_audio_exit"] = device_audio.returncode if device_audio else None
        except Exception as failure:
            failures.append(f"device recorder: {failure}")
        try:
            subprocess.run(["pactl", "unload-module", module], check=True)
            result["module"] = None
        except Exception as failure:
            failures.append(f"audio module {module}: {failure}")
        if failures:
            result["state"] = "failed"
        result["cleanup"] = failures or "all owned processes stopped; private module unloaded"
        receipt.write_text(json.dumps(result, indent=2) + "\n")
        if failures:
            raise RuntimeError("Lifecycle cleanup pending: " + "; ".join(failures))
    print(receipt.read_text())


if __name__ == "__main__":
    main()
