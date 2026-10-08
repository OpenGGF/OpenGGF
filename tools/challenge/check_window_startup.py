#!/usr/bin/env python3
"""Tell a desktop that withholds new windows apart from a host startup stall.

Purpose: when the Three Openings window does not appear, first map a plain Xlib
control window (no GLFW, no GL). If the window manager leaves even that
unmapped, the cause is the desktop session (a locked KDE Plasma session did so
on 2026-10-08). Then launch the real host and record whether its main thread
keeps running (no busy spin), whether it holds the title until the window is
viewable, and whether it closes cleanly through the normal WM_DELETE_WINDOW
path. ``--map-after`` additionally maps the owned window with override_redirect
as an explicit diagnostic, proving the held title proceeds once the window is
viewable; that is not normal window-manager evidence.

Inputs: built classpath, three original ROM paths, outside-repository output
directory. Linux X11 or XWayland with Python Xlib. Gameplay is never started:
the host stays on its title screen. Origin: 2026-10-08 multigame Opus polish
(feature/ai-multigame-opus-polish), startup stall diagnosis.
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

TITLE = "Three Openings | OpenGGF"


def control_window(seconds: float) -> dict:
    """Map a plain toplevel and report whether the window manager shows it."""
    from Xlib import X, display as xdisplay
    display = xdisplay.Display()
    screen = display.screen()
    window = screen.root.create_window(80, 80, 320, 120, 0, screen.root_depth, X.InputOutput,
        X.CopyFromParent, background_pixel=screen.black_pixel,
        event_mask=X.StructureNotifyMask | X.VisibilityChangeMask)
    window.set_wm_name("Three Openings startup control")
    window.map()
    display.flush()
    started = time.monotonic()
    events = []
    while time.monotonic() - started < seconds:
        while display.pending_events():
            events.append(type(display.next_event()).__name__)
        if "VisibilityNotify" in events:
            break
        time.sleep(.01)
    state = window.get_attributes().map_state
    window.destroy()
    display.close()
    return {"viewable": state == X.IsViewable, "events": sorted(set(events)),
            "seconds": round(time.monotonic() - started, 2)}


def host_window(display, pid):
    from Xlib import X
    atom = display.intern_atom("_NET_WM_PID")
    for window in display.screen().root.query_tree().children:
        try:
            value = window.get_full_property(atom, X.AnyPropertyType)
            if value is not None and int(value.value[0]) == pid and window.get_wm_name() == TITLE:
                return window
        except Exception:
            continue
    return None


def main_thread_ticks(pid: int) -> dict[str, int]:
    ticks = {}
    for task in Path(f"/proc/{pid}/task").iterdir():
        try:
            fields = (task / "stat").read_text().rsplit(")", 1)[1].split()
            ticks[task.name] = int(fields[11]) + int(fields[12])
        except (FileNotFoundError, IndexError, ValueError):
            pass
    return ticks


def busiest_thread_share(pid: int, seconds: float = 1.0) -> float:
    """CPU share of the busiest host thread (1.0 = one core fully busy)."""
    before = main_thread_ticks(pid)
    time.sleep(seconds)
    after = main_thread_ticks(pid)
    hz = os.sysconf("SC_CLK_TCK")
    deltas = [after[t] - before[t] for t in after if t in before]
    return round(max(deltas, default=0) / hz / seconds, 2)


def scenes(path: Path) -> list[str]:
    if not path.exists():
        return []
    with path.open() as source:
        return [row["scene"] for row in csv.DictReader(source)]


def close_window(display, window):
    """Ask the owned window to close the way a window manager would."""
    from Xlib import X
    from Xlib.protocol import event as xevent
    protocols = display.intern_atom("WM_PROTOCOLS")
    delete = display.intern_atom("WM_DELETE_WINDOW")
    message = xevent.ClientMessage(window=window, client_type=protocols,
                                   data=(32, [delete, X.CurrentTime, 0, 0, 0]))
    window.send_event(message, event_mask=X.NoEventMask)
    display.sync()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--classpath", required=True)
    for game in ("s1", "s2", "s3k"):
        parser.add_argument("--" + game, type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wait", type=float, default=6, help="Seconds to observe the unshown host")
    parser.add_argument("--map-after", action="store_true",
        help="Then map the owned window with override_redirect (diagnostic, not normal WM evidence)")
    parser.add_argument("--keyutils-preload", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("output must be outside the repository")
    output.mkdir(parents=True, exist_ok=True)
    result = {"control": control_window(3)}
    from Xlib import X, display as xdisplay
    display = xdisplay.Display()
    # Title cues go to a private null sink, never the session's speakers.
    sink = "openggf_startup_" + uuid.uuid4().hex[:12]
    module = subprocess.run(["pactl", "load-module", "module-null-sink", "sink_name=" + sink,
                             "rate=48000", "channels=2"], check=True, text=True,
                            capture_output=True).stdout.strip()
    env = os.environ.copy()
    env.update(PULSE_SINK=sink, ALSOFT_DRIVERS="pulse")
    if args.keyutils_preload:
        env["LD_PRELOAD"] = str(args.keyutils_preload.resolve(strict=True))
    command = ["java", "-cp", args.classpath, "com.openggf.tools.challenge.ThreeOpeningsTool"]
    for game in ("s1", "s2", "s3k"):
        command += ["--" + game, str(getattr(args, game).resolve())]
    command += ["--capture-dir", str(output / "host")]
    log = (output / "host.log").open("wb")
    host = subprocess.Popen(command, cwd=output, env=env, stdout=log, stderr=subprocess.STDOUT)
    result["host_pid"] = host.pid
    try:
        deadline = time.monotonic() + 20
        window = None
        while window is None and time.monotonic() < deadline and host.poll() is None:
            window = host_window(display, host.pid)
            time.sleep(.1)
        if window is None:
            raise RuntimeError("Host window was never created")
        time.sleep(max(0, args.wait - 1))
        attributes = window.get_attributes()
        result["unshown"] = {
            "viewable": attributes.map_state == X.IsViewable,
            "busiest_thread_cpu": busiest_thread_share(host.pid),
            "scenes": scenes(output / "host" / "presentation.csv"),
            "waiting_notice": b"Waiting for the desktop" in (output / "host.log").read_bytes(),
        }
        if args.map_after and attributes.map_state != X.IsViewable:
            window.unmap()
            display.sync()
            window.change_attributes(override_redirect=True)
            window.map()
            display.sync()
            time.sleep(2)
            result["diagnostic_mapped"] = {
                "viewable": window.get_attributes().map_state == X.IsViewable,
                "scenes": scenes(output / "host" / "presentation.csv"),
                "title_screenshot": (output / "host" / "title.png").exists(),
            }
        close_window(display, window)
        try:
            result["exit_code"] = host.wait(timeout=10)
        except subprocess.TimeoutExpired:
            result["exit_code"] = None
            result["close_ignored"] = True
    finally:
        if host.poll() is None:
            host.send_signal(signal.SIGKILL)
            host.wait(timeout=10)
            result["killed"] = True
        log.close()
        display.close()
        subprocess.run(["pactl", "unload-module", module], check=True)
        (output / "startup.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
