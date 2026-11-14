#!/usr/bin/env python3
"""Sonic CD lag and mid-pass census over native v5 stage traces.

Purpose: list every row of a Sonic CD native trace on which the stage main loop did
not start a new pass (a levelFrames plateau) or the V-INT counter did not advance by
exactly one, classify each by its counter shape, and say what the sampled state shows
about the pass in flight. Used by the LAG lane (2026-11-13) to decide which native lag
behaviours the engine reproduces and which the replay comparison must absorb.

Inputs: one or more directories, each searched for ``trace/physics.csv`` (with its
``aux_state.jsonl`` and ``metadata.json``) up to three levels deep -- the 70 stage
fixtures (``native/rehost-fixtures``), the full-act routes (``native/rehost-long``)
and the boss movies (``native/rehost-boss-*``). Optional ``--csv`` writes one line per
event; the summary goes to stdout.

Origin: written for the LAG lane report (``planning/rehost/LAG-report.md``). Recorder
facts it relies on (TraceChaser ``ScdTraceCaptureRunner``): row N is read after BK2 row
offset+N completes; ``gameplay_frame_counter`` is ``levelFrames`` (+1 per unpaused main
loop pass, Level/Main.asm:400); ``vblank_counter`` is the low word of
``levelVIntCounter`` (+1 at the end of every V-INT, lag or not, Level/Main.asm:711-712);
``lag_state.lagged`` is BizHawk's "no controller poll this frame" flag, true exactly
when every V-INT in the frame took ``VInt_Lag`` (Level/Main.asm:685-686, 738-766), which
does not call ReadControllers.

Counter shapes, (d levelFrames, d levelVIntCounter, lag flag) against the previous row:
  FULL     (1,1,F)  a pass started; ordinary row.
  LAG      (0,1,T)  the frame's V-INT found vintRoutine 0: the pass of the previous row was
                    still running (sampled mid-pass on the previous row when its state differs).
  HELD     (0,0,*)  no V-INT finished inside the frame: the handler (or an interrupt mask)
                    ran past the sample; no pass started.
  CATCHUP  (1,2,*)  the held handler finished plus this frame's V-INT; a pass started.
  STALLFADE(0,1,F)  the V-INT read the pads (not VInt_Lag) but no pass started: a blocking
                    VSync loop other than Level_MainLoop (palette fade, pause) owns the frame.
  OTHER    anything else (restart, pause, counter reset).
"""
import argparse
import csv
import json
import os
import sys
from collections import Counter, defaultdict

PLAYER_FIELDS = ("player_x", "player_y", "player_x_speed", "player_y_speed", "player_g_speed",
                 "player_angle", "player_x_sub", "player_y_sub", "player_routine",
                 "player_status_byte", "player_stand_on_obj", "player_animation_id",
                 "player_mapping_frame")
CAMERA_FIELDS = ("camera_x", "camera_y")


def find_traces(roots):
    for root in roots:
        root = os.path.abspath(root)
        for dirpath, dirnames, filenames in os.walk(root):
            depth = dirpath[len(root):].count(os.sep)
            if depth > 3:
                dirnames[:] = []
                continue
            dirnames[:] = sorted(d for d in dirnames if not d.startswith(("_", ".")))
            if os.path.basename(dirpath) == "trace" and "physics.csv" in filenames:
                yield dirpath


def load(trace_dir):
    with open(os.path.join(trace_dir, "physics.csv"), newline="") as f:
        rows = list(csv.DictReader(f))
    lag, locked, routine = {}, {}, {}
    with open(os.path.join(trace_dir, "aux_state.jsonl")) as f:
        for line in f:
            e = json.loads(line)
            if e["event"] == "lag_state":
                lag[e["frame"]] = bool(e["lagged"])
            elif e["event"] == "mode_change" and e.get("field") == "control_locked":
                locked[e["frame"]] = e["to"]
    with open(os.path.join(trace_dir, "metadata.json")) as f:
        meta = json.load(f)
    return rows, lag, locked, meta


def h(row, key):
    return int(row[key], 16)


def release_row(rows, locked):
    """The first row on which the player's move lock is released (title card over)."""
    for frame in sorted(locked):
        if locked[frame] == 0:
            return frame
    return 0


def shape(prev, cur, lagged):
    dg = (h(cur, "gameplay_frame_counter") - h(prev, "gameplay_frame_counter")) & 0xFFFF
    dv = (h(cur, "vblank_counter") - h(prev, "vblank_counter")) & 0xFFFF
    if dg == 1 and dv == 1:
        return "FULL", dg, dv
    if dg == 0 and dv == 1:
        return ("LAG" if lagged else "STALLFADE"), dg, dv
    if dg == 0 and dv == 0:
        return "HELD", dg, dv
    if dg == 1 and dv == 2:
        return "CATCHUP", dg, dv
    return "OTHER", dg, dv


def diff_fields(a, b, fields):
    return [f for f in fields if a[f] != b[f]]


def phase_of(i, rows, release):
    r = rows[i]
    if h(r, "player_routine") >= 6:
        return "dead"
    if i < release:
        return "title-card"
    return "play"


def census(trace_dir):
    rows, lag, locked, meta = load(trace_dir)
    release = release_row(rows, locked)
    events = []
    for i in range(1, len(rows)):
        kind, dg, dv = shape(rows[i - 1], rows[i], lag.get(i, False))
        if kind == "FULL":
            continue
        ev = {"row": i, "kind": kind, "dg": dg, "dv": dv, "lagged": lag.get(i, False),
              "levelFrames": h(rows[i], "gameplay_frame_counter"), "phase": phase_of(i, rows, release)}
        if kind in ("LAG", "STALLFADE", "HELD"):
            # The pass that the previous row sampled; this row shows it later. A difference
            # means the previous row was taken while that pass was still running.
            ev["player_diff"] = diff_fields(rows[i - 1], rows[i], PLAYER_FIELDS)
            ev["camera_diff"] = diff_fields(rows[i - 1], rows[i], CAMERA_FIELDS)
        events.append(ev)
    return rows, release, meta, events


def subkind(ev):
    if ev["kind"] != "LAG":
        return ev["kind"]
    if ev["player_diff"]:
        return "LAG/player-mid-pass"
    if ev["camera_diff"]:
        return "LAG/camera-mid-pass"
    return "LAG/state-complete"


def runs(events, kinds):
    """Group consecutive rows of the given kinds into runs: (first row, length)."""
    out, start, prev = [], None, None
    for ev in events:
        if ev["kind"] not in kinds:
            continue
        if prev is not None and ev["row"] == prev + 1:
            prev = ev["row"]
            continue
        if start is not None:
            out.append((start, prev - start + 1))
        start = prev = ev["row"]
    if start is not None:
        out.append((start, prev - start + 1))
    return out


# ---------------------------------------------------------------------------------------------
# Native sample context from lag_native_probe (optional). The probe logs, per BK2 row after the
# stage starts, the 68K PC/SR, vintRoutine and the long in Level_MainLoop's stack slot ($FFFCE8),
# which holds the return address of the main-loop call in progress, and dumps Word RAM once.

MAIN_LOOP_HEAD = bytes.fromhex("11FC0008F62A6100")      # move.b #8,vintRoutine.w ; bsr.w VSync
LEVEL_FRAMES_INC = bytes.fromhex("527900FF1504")        # addq.w #1,levelFrames
# Calls after the levelFrames increment, in source order (Level/Main.asm:400-430; r4/main.asm
# StageMainLoopUpdate:390-416 adds CheckWaterCurrents after UpdateScroll, r5 its zone call).
PASS_CALLS = ["SpawnObjects", "RunObjects", "LevelScroll", "DrawObjects", "PaletteCycle",
              "UpdateSectionArt", "ProcessPLCs", "UpdateGlobalAnims"]
PASS_CALLS_ZONE = ["SpawnObjects", "RunObjects", "LevelScroll", "ZoneAfterScroll", "DrawObjects",
                   "PaletteCycle", "UpdateSectionArt", "ProcessPLCs", "UpdateGlobalAnims"]


def main_loop_calls(wordram):
    """Return-address -> call name for the running stage program's Level_MainLoop."""
    i = wordram.find(MAIN_LOOP_HEAD)
    if i < 0:
        return {}
    inc = wordram.find(LEVEL_FRAMES_INC, i)
    names, p, calls = {}, i + 6, []
    while p < i + 0x300:
        op = wordram[p:p + 2]
        if op == b"\x4e\xb9":
            calls.append(0x200000 + p + 6)
            p += 6
            continue
        if op == b"\x61\x00":
            calls.append(0x200000 + p + 4)
            p += 4
            continue
        if op == b"\x60\x00":
            disp = int.from_bytes(wordram[p + 2:p + 4], "big", signed=True)
            if p + 2 + disp == i:
                break
        p += 2
    names[calls[0]] = "VSync"
    after = [c for c in calls if c > 0x200000 + inc]
    order = PASS_CALLS if len(after) == len(PASS_CALLS) else PASS_CALLS_ZONE
    for ret, name in zip(after, order):
        names[ret] = name
    for ret in calls:
        names.setdefault(ret, "pause-branch")
    return names


def load_probe(probe_dir):
    with open(os.path.join(probe_dir, "wordram.bin"), "rb") as f:
        names = main_loop_calls(f.read())
    by_row = {}
    with open(os.path.join(probe_dir, "lag.csv"), newline="") as f:
        for r in csv.DictReader(f):
            by_row[int(r["row"])] = r
    return names, by_row


def sample_context(p, names):
    """Where the main CPU was when the recorder sampled this row."""
    level = (int(p["sr"], 16) >> 8) & 7
    call = names.get(int(p["mlret"], 16), "?" + p["mlret"])
    where = "vsync" if call == "VSync" else "pass:" + call
    routine = p.get("vintRoutine", "")
    if where == "vsync" and routine not in ("08", ""):
        where = "vsync(routine " + routine + ")"
    elif where.startswith("pass:") and routine not in ("00", ""):
        # A pass that called VSync itself: an object's blocking loop (e.g. a palette fade, vintRoutine $12).
        where += "+blocking-vsync(routine " + routine + ")"
    if level >= 6:
        return "vint>" + where
    if level >= 4:
        return "hint>" + where
    return where


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("roots", nargs="+")
    ap.add_argument("--csv", help="write one line per event")
    ap.add_argument("--detail", action="store_true", help="print every non-FULL row")
    ap.add_argument("--probe", help="lag_native_probe output root: <set>_<payload>/lag.csv per trace")
    args = ap.parse_args(argv)
    total = Counter()
    by_phase = defaultdict(Counter)
    causes = Counter()
    mid_without_lag = []
    mismatches = []
    out = None
    if args.csv:
        out = csv.writer(open(args.csv, "w", newline=""))
        out.writerow(["trace", "row", "kind", "subkind", "dg", "dv", "lagged", "levelFrames", "phase",
                      "player_diff", "camera_diff", "sample_prev", "sample", "ctrl_locked", "plc_head"])
    for trace_dir in find_traces(args.roots):
        rows, release, meta, events = census(trace_dir)
        root = next(os.path.abspath(r) for r in args.roots if os.path.abspath(trace_dir).startswith(os.path.abspath(r)))
        rel = os.path.relpath(os.path.dirname(trace_dir), os.path.dirname(root))
        name = rel.replace(os.sep, "_")
        probe = None
        if args.probe and os.path.isdir(os.path.join(args.probe, name)):
            names, by_row = load_probe(os.path.join(args.probe, name))
            offset = int(meta["bk2_frame_offset"])
            probe = (names, by_row, offset)
            # The probe must see the recorder's counters on every row, or the join is wrong.
            for i, r in enumerate(rows):
                p = by_row.get(offset + i)
                if p is None or p["levelFrames"] != r["gameplay_frame_counter"] or p["vint"] != r["vblank_counter"]:
                    mismatches.append((name, i))
                    break
            for i in range(len(rows) - 1):
                p = by_row.get(offset + i)
                if p is None:
                    continue
                ctx = sample_context(p, names)
                nxt = (h(rows[i + 1], "gameplay_frame_counter") - h(rows[i], "gameplay_frame_counter")) & 0xFFFF
                if ctx.startswith("pass:") and nxt != 0:
                    mid_without_lag.append((name, i, ctx))
        c = Counter(subkind(e) for e in events)
        vints = h(rows[-1], "vblank_counter") - h(rows[0], "vblank_counter")
        passes = h(rows[-1], "gameplay_frame_counter") - h(rows[0], "gameplay_frame_counter")
        lag_vints = sum(e["dv"] for e in events if e["kind"] == "LAG")
        print(f"{name:34s} rows {len(rows):5d} passes {passes:5d} vints {vints:5d} lagV {lag_vints:3d} "
              + " ".join(f"{k}={v}" for k, v in sorted(c.items())))
        for e in events:
            sk = subkind(e)
            sample_prev = sample = locked = plc = ""
            if probe:
                names, by_row, offset = probe
                pp, pc = by_row.get(offset + e["row"] - 1), by_row.get(offset + e["row"])
                sample_prev = sample_context(pp, names) if pp else ""
                sample = sample_context(pc, names) if pc else ""
                locked = pp.get("ctrlLocked", "") if pp else ""
                plc = pp.get("plcHead", "") if pp else ""
                causes[(sk, sample_prev, "locked" if locked not in ("", "00") else "free")] += 1
            total[sk] += 1
            if out:
                out.writerow([name, e["row"], e["kind"], sk, e["dg"], e["dv"], int(e["lagged"]), e["levelFrames"],
                              e["phase"], " ".join(e.get("player_diff", [])), " ".join(e.get("camera_diff", [])),
                              sample_prev, sample, locked, plc])
            if args.detail:
                print("   ", e["row"], sk, sample_prev, "->", sample, e.get("player_diff", ""), e.get("camera_diff", ""))
    print("TOTAL", dict(total))
    for key, n in sorted(causes.items(), key=lambda kv: -kv[1]):
        print("CAUSE", n, *key)
    print("MID-PASS SAMPLES WITHOUT A FOLLOWING PLATEAU", len(mid_without_lag), mid_without_lag[:40])
    print("PROBE/TRACE COUNTER MISMATCHES", mismatches)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
