#!/usr/bin/env python3
"""Serial all-level frame-time, allocation, GC and hitch sweep.

Purpose: measure every reachable S1/S2/S3K level in the live configuration
(unpaced, rewind recording on, audio on, offscreen GL with glFinish), reduce the
per-frame CSVs to per-level statistics, route progress and the top spikes with
their dominant profiler section, find the over-budget frames that recur across
runs, and attribute spikes and steady-state allocation/CPU to code from JFR, so
hitch causes can be grouped and ranked.

Inputs: a compiled worktree (``target/classes``) and ``target/cp.txt`` from
``maven_queue.py -Dmse=off compile dependency:build-classpath
-Dmdep.outputFile=target/cp.txt``; verified ROMs found the usual way (config or
root ``.gen`` files). Drivers: ``com.openggf.tools.TraceBenchmarkTool`` replays
each trace fixture (``--mode full``, whole trace) and
``com.openggf.tools.RouteFrameBudgetProbe`` drives engine-authored S3K route
movies with their acceptance tests' launch state.

Usage (from the worktree root; probe JVMs run strictly one at a time):
  python3 tools/performance/level_sweep.py list
  python3 tools/performance/level_sweep.py run --out target/perf-sweep [--only REGEX]
        [--repeat 3] [--jfr [--method-trace 'pkg.Class::method;...']]
  python3 tools/performance/level_sweep.py summarise --out target/perf-sweep
        [--html target/perf-sweep/report.html] [--notes findings.html]
  python3 tools/performance/level_sweep.py attribute --csv X.csv --jfr X.jfr
        [--threshold 16.67] [--output X.txt]

``run`` skips cases whose CSV already exists, so an interrupted sweep resumes;
``--repeat N`` adds runs r2..rN. ``--jfr`` records a 1 ms execution-sample JFR
beside each CSV; ``--method-trace`` adds JDK 25 (JEP 520) method tracing, e.g.
of ``LevelTilemapManager::invalidateAllTilemaps``, to name what requested a
render-time rebuild. ``summarise`` writes ``summary.json`` and
``reproducible.json`` (frames over 16.67 ms in at least two runs) and the HTML
report. ``attribute`` matches JFR samples to spike frames through the CSV's
``epoch_us`` column and prints per-spike inclusive/leaf/allocation sites,
traced calls, and the steady-state allocation and CPU profile of the other
settled frames. Environment variables pass through to the probes, so
``DRI_PRIME=1`` (discrete GPU) and ``mesa_glthread=false`` serve as controls.
Statistics exclude the first ``--warmup`` frames (default 600) of each fresh
JVM, which are JIT/class-loading cost reported separately as ``warm_*``.

Hazard: the default surfaceless device may be an integrated GPU shared with the
desktop; synchronous texture uploads then absorb its queue latency. Confirm GPU
stalls with a ``DRI_PRIME=1`` control before ranking them.

Origin: 2026-11-13 all-level performance sweep (feature/ai-perf-level-sweep),
following the 2026-11-12 LRZ frame-budget study (RouteFrameBudgetProbe).
"""
import argparse
import csv
import json
import math
import os
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

JAVA = os.environ.get("OPENGGF_JAVA", "/usr/lib/jvm/java-25-openjdk/bin/java")
TRACES = Path("src/test/resources/traces")
ROUTES = Path("src/test/resources/routes/s3k")
BUDGET_120 = 1000.0 / 120.0
BUDGET_60 = 1000.0 / 60.0

# Trace fixtures: one per level the trace catalogue covers (top-level, capturable
# directories only; multi-segment runs/ are not accepted by TraceBenchmarkTool).
S1_TRACES = ["ghz1", "ghz2", "ghz3", "mz1", "mz2", "mz3", "syz1", "syz2", "syz3",
             "lz1", "lz2", "lz3", "slz1", "slz2", "slz3", "sbz1", "sbz2", "sbz3", "fz"]
S2_TRACES = ["ehz1_fullrun", "cpz", "cpz2", "arz", "arz2", "cnz", "cnz2", "htz", "htz2",
             "mcz", "mcz2", "ooz", "ooz2", "mtz", "mtz2", "mtz3", "scz", "wfz",
             "dez_ending", "special_stage"]
S3K_TRACES = ["aiz", "hcz", "mgz", "cnz", "fbz", "icz", "lbz", "mhz", "soz", "lrz",
              "hpz", "hpz22", "ssz", "dez", "ddz"]

# Route movies: (id, file, zone, act, main, sidekick, extra probe flags). Launch
# state follows each route's acceptance test or script header.
S3K_ROUTES = [
    ("cnz2-route", "cnz2-knuckles-cold-released-icz-320.bk2", 3, 1, "knuckles", "", ["--title-card"]),
    ("fbz1-route", "fbz1-tails-cold-320.bk2", 4, 0, "tails", "", ["--setup-step"]),
    ("icz2-route", "icz2-sonic-cold-cube-button-bridge-320.bk2", 5, 1, "sonic", "", ["--title-card"]),
    ("mhz12-route", "mhz2-team-incoming-320.bk2", 7, 0, "sonic", "tails", []),
    ("soz1-route", "soz1-cold-sonic.bk2", 8, 0, "sonic", "", ["--intros"]),
    ("soz2-route", "soz2-cold-knuckles.bk2", 8, 1, "knuckles", "", ["--intros"]),
    ("lrz-hpz-route", "lrz-boss-sonic-tails-cold-hpz-320.bk2", 9, 0, "sonic", "tails", []),
    ("ssz1-route", "ssz1-sonic-tails-cold-complete-800.bk2", 10, 0, "sonic", "tails", ["--width", "800"]),
    ("dez12-route", "dez2-sonic-tails-incoming-clear-320.bk2", 11, 0, "sonic", "tails", []),
    ("dez2-route", "dez2-knuckles-cold-first-gravity-320.bk2", 11, 1, "knuckles", "", ["--title-card"]),
    ("mhz12k-route", "mhz2-knuckles-cold-complete-320.bk2", 7, 0, "knuckles", "", []),
    ("ddz-route", "ddz-super-fresh-320.bk2", 12, 0, "sonic", "", ["--emeralds", "1111111", "--input-start", "1"]),
]


def trace_dir(game, name):
    if game == "s1":
        return TRACES / "s1" / (name + "_completerun")
    if game == "s3k":
        return TRACES / "s3k" / (name + "_completerun")
    return TRACES / "s2" / name


def matrix():
    cases = []
    for game, names in (("s1", S1_TRACES), ("s2", S2_TRACES), ("s3k", S3K_TRACES)):
        for name in names:
            cases.append({"id": f"{game}-{name}", "game": game, "kind": "trace",
                          "level": name, "path": str(trace_dir(game, name))})
    for rid, file, zone, act, main, sidekick, extra in S3K_ROUTES:
        cases.append({"id": f"s3k-{rid}", "game": "s3k", "kind": "route", "level": rid,
                      "path": str(ROUTES / file), "zone": zone, "act": act,
                      "main": main, "sidekick": sidekick, "extra": extra})
    return cases


def java_command(case, csv_path, jfr_path, jfc, method_trace=None):
    cp = "target/classes:" + Path("target/cp.txt").read_text().strip()
    cmd = [JAVA, "-Dmse=off", "-Dopenggf.headless.gl=surfaceless-egl",
           "--enable-native-access=ALL-UNNAMED"]
    if jfr_path:
        # method-trace (JDK 25, JEP 520) records a stack per call of the named
        # methods, e.g. the tilemap invalidations, so a render-time rebuild can be
        # traced back to the logic that requested it.
        trace = f",method-trace={method_trace}" if method_trace else ""
        cmd.append(f"-XX:StartFlightRecording=settings={jfc},filename={jfr_path},dumponexit=true{trace}")
    cmd += ["-cp", cp]
    if case["kind"] == "trace":
        cmd += ["com.openggf.tools.TraceBenchmarkTool", "--trace", case["path"], "--mode", "full",
                "--warmup-frames", "0", "--measure-frames", "200000", "--iterations", "1",
                "--rewind", "--frame-log", str(csv_path)]
    else:
        cmd += ["com.openggf.tools.RouteFrameBudgetProbe", "--game", "s3k", "--route", case["path"],
                "--zone", str(case["zone"]), "--act", str(case["act"]),
                "--main", case["main"], "--sidekick", case["sidekick"],
                "--rewind", "--audio", "--frame-log", str(csv_path)] + case["extra"]
    return cmd


def ensure_jfc(out):
    jfc = out / "hitch.jfc"
    if not jfc.exists():
        subprocess.run(["jfr", "configure", "--input", "profile",
                        "jdk.ExecutionSample#period=1ms", "jdk.NativeMethodSample#period=1ms",
                        "--output", str(jfc)], check=True)
    return jfc


def cmd_run(args):
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    jfc = ensure_jfc(out) if args.jfr else None
    env = {k: v for k, v in os.environ.items()
           if k not in ("DISPLAY", "WAYLAND_DISPLAY", "JDK_JAVA_OPTIONS", "LD_LIBRARY_PATH")}
    pattern = re.compile(args.only) if args.only else None
    for case in matrix():
        if pattern and not pattern.search(case["id"]):
            continue
        for rep in range(1, args.repeat + 1):
            stem = f"{case['id']}-r{rep}"
            csv_path = out / f"{stem}.csv"
            if csv_path.exists():
                continue
            jfr_path = out / f"{stem}.jfr" if args.jfr else None
            cmd = java_command(case, csv_path, jfr_path, jfc, args.method_trace if args.jfr else None)
            started = time.time()
            print(f"[{time.strftime('%H:%M:%S')}] {stem} ...", flush=True)
            with open(out / f"{stem}.log", "w") as log:
                try:
                    proc = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                          text=True, timeout=args.timeout)
                    status = proc.returncode
                    lines = proc.stdout.splitlines()
                except subprocess.TimeoutExpired as e:
                    status = "timeout"
                    lines = (e.stdout or b"").decode(errors="replace").splitlines() if isinstance(
                        e.stdout, bytes) else (e.stdout or "").splitlines()
                # java.util.logging INFO pairs are noise; keep everything else.
                keep, skip_next = [], False
                for line in lines:
                    if skip_next:
                        skip_next = False
                        if line.startswith("INFO:"):
                            continue
                    if re.match(r"^[A-Z][a-z]{2} \d+, \d{4} ", line):
                        skip_next = True
                        continue
                    keep.append(line)
                log.write("\n".join(keep) + "\n")
            # Other agents' JVMs share this machine; the load average beside each
            # case marks runs whose isolated spikes may be interference.
            print(f"    exit={status} {time.time() - started:.0f}s csv={'yes' if csv_path.exists() else 'NO'} "
                  f"load1={os.getloadavg()[0]:.1f}", flush=True)


# ---------------------------------------------------------------- summarise

BASE_TRACE = {"measured_frame", "trace_index", "act", "frame_ms", "epoch_us", "wall_ms", "alloc_kb", "gc_count", "gc_ms",
              "zone", "player_x", "trace_x", "player_y", "trace_y", "camera_x", "camera_y"}
BASE_ROUTE = {"frame", "epoch_us", "wall_ms", "step_ms", "render_ms", "alloc_kb", "gc_count", "gc_ms",
              "zone", "act", "player_x", "player_y", "camera_x", "camera_y"}


def pct(sorted_values, p):
    if not sorted_values:
        return float("nan")
    k = (len(sorted_values) - 1) * p
    lo, hi = math.floor(k), math.ceil(k)
    return sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * (k - lo)


def load_csv(path):
    with open(path, newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        rows = [r for r in reader if r]
    cols = {name: i for i, name in enumerate(header)}
    kind = "trace" if "trace_index" in cols else "route"
    base = BASE_TRACE if kind == "trace" else BASE_ROUTE
    sections = [h for h in header if h not in base]
    return kind, cols, sections, rows


def progress(kind, cols, rows):
    """How far the replay got: acts visited, on-route share, divergence and stalls."""
    acts, last = [], None
    for r in rows:
        key = (int(r[cols["zone"]]), int(r[cols["act"]]))
        if key != last:
            acts.append({"frame": len(acts) and int(r[0]) or 0, "zone": key[0], "act": key[1]})
            last = key
    xs = [int(r[cols["player_x"]]) for r in rows]
    ys = [int(r[cols["player_y"]]) for r in rows]
    # Stall: longest run of frames with an unchanged player position.
    longest, start, run_start = 0, -1, 0
    for i in range(1, len(rows)):
        if xs[i] != xs[i - 1] or ys[i] != ys[i - 1]:
            run_start = i
        elif i - run_start > longest:
            longest, start = i - run_start, run_start
    # Per (zone, act): frames played, on-route frames (traces), first big reload.
    per_act = {}
    for i, r in enumerate(rows):
        key = f"{int(r[cols['zone']])}/{int(r[cols['act']])}"
        a = per_act.setdefault(key, {"frames": 0, "on_route": 0, "max_x": -1, "reloads": [],
                                     "first": i})
        a["frames"] += 1
        a["max_x"] = max(a["max_x"], xs[i])
        if kind == "trace":
            a["on_route"] += (abs(int(r[cols["player_x"]]) - int(r[cols["trace_x"]])) <= 16
                              and abs(int(r[cols["player_y"]]) - int(r[cols["trace_y"]])) <= 16)
        if float(r[cols["alloc_kb"]]) >= RELOAD_KB and i - a["first"] > 3:
            a["reloads"].append(i)
    result = {"acts": acts, "per_act": per_act, "max_x": max(xs) if xs else -1, "final": [xs[-1], ys[-1]] if xs else None,
              "longest_still": longest, "longest_still_from": start}
    if kind == "trace":
        off = [abs(int(r[cols["player_x"]]) - int(r[cols["trace_x"]])) > 16
               or abs(int(r[cols["player_y"]]) - int(r[cols["trace_y"]])) > 16 for r in rows]
        diverged, streak = None, 0
        for i, o in enumerate(off):
            streak = streak + 1 if o else 0
            if streak >= 60:
                diverged = i - 59
                break
        result["on_route_pct"] = 100.0 * (len(off) - sum(off)) / max(1, len(off))
        result["diverged_at"] = diverged
        last_trace = int(rows[-1][cols["trace_index"]]) if rows else 0
        result["trace_rows_driven"] = last_trace + 1
    return result


RELOAD_KB = 40 * 1024


def spike_tag(i, alloc, acts, xs, ys):
    """Classify a spike frame so known costs can be separated from open causes.

    ``act-load``: the zone/act changes within three frames. ``reload``: the
    frame allocated over 40 MB without an act change, which on these routes is
    the death respawn's level reload (owned elsewhere). ``gc``: handled by the
    caller from the GC column. Anything else is ``open``.
    """
    lo, hi = max(0, i - 3), min(len(acts) - 1, i + 3)
    if any(acts[j] != acts[i] for j in range(lo, hi + 1)):
        return "act-load"
    if alloc[i] >= RELOAD_KB:
        return "reload"
    return "open"


def summarise_file(path, warmup):
    kind, cols, sections, rows = load_csv(path)
    wall = [float(r[cols["wall_ms"]]) for r in rows]
    alloc = [float(r[cols["alloc_kb"]]) for r in rows]
    gcn = [int(r[cols["gc_count"]]) for r in rows]
    gcms = [int(r[cols["gc_ms"]]) for r in rows]
    sec = {s: [float(r[cols[s]]) for r in rows] for s in sections}
    settled = range(min(warmup, len(rows)), len(rows))
    sw = sorted(wall[i] for i in settled)
    med = {s: statistics.median([v[i] for i in settled]) if settled else 0.0 for s, v in sec.items()}
    mean_sec = {s: (sum(v[i] for i in settled) / max(1, len(settled))) for s, v in sec.items()}
    acts = [(int(r[cols["zone"]]), int(r[cols["act"]])) for r in rows]
    xs = [int(r[cols["player_x"]]) for r in rows]
    ys = [int(r[cols["player_y"]]) for r in rows]
    spikes = []
    for i in settled:
        if wall[i] <= BUDGET_120:
            continue
        excess = {s: sec[s][i] - med[s] for s in sections}
        unsectioned = wall[i] - sum(sec[s][i] for s in sections)
        excess["(unsectioned)"] = unsectioned
        top = sorted(excess.items(), key=lambda kv: -kv[1])[:3]
        r = rows[i]
        spikes.append({"frame": int(r[0]), "wall": wall[i], "alloc_kb": alloc[i], "gc_ms": gcms[i],
                       "tag": spike_tag(i, alloc, acts, xs, ys),
                       "gc": gcn[i], "zone": int(r[cols["zone"]]), "act": int(r[cols["act"]]),
                       "x": int(r[cols["player_x"]]), "y": int(r[cols["player_y"]]),
                       "epoch_us": int(r[cols["epoch_us"]]) if "epoch_us" in cols else None,
                       "top": [[k, round(v, 3)] for k, v in top]})
    spikes.sort(key=lambda s: -s["wall"])
    warm_wall = wall[:min(warmup, len(rows))]
    salloc = sorted(alloc[i] for i in settled)
    return {
        "file": path.name, "kind": kind, "frames": len(rows),
        "settled_frames": len(settled),
        "mean": sum(sw) / max(1, len(sw)), "p50": pct(sw, .5), "p99": pct(sw, .99), "p999": pct(sw, .999),
        "max": sw[-1] if sw else float("nan"),
        "over_8": sum(1 for v in sw if v > BUDGET_120), "over_16": sum(1 for v in sw if v > BUDGET_60),
        "alloc_med_kb": pct(salloc, .5), "alloc_mean_kb": sum(salloc) / max(1, len(salloc)),
        "gc_count": sum(gcn[i] for i in settled), "gc_ms": sum(gcms[i] for i in settled),
        "warm_over_16": sum(1 for v in warm_wall if v > BUDGET_60), "warm_max": max(warm_wall, default=0),
        "warm_first": wall[0] if wall else 0,
        "sections_mean": dict(sorted(mean_sec.items(), key=lambda kv: -kv[1])[:8]),
        "progress": progress(kind, cols, rows),
        "spikes": spikes[:25],
        "spikes_over_16": [s for s in spikes if s["wall"] > BUDGET_60],
        "open_over_16": sum(1 for s in spikes if s["wall"] > BUDGET_60 and s["tag"] == "open"),
        "reload_over_16": sum(1 for s in spikes if s["wall"] > BUDGET_60 and s["tag"] == "reload"),
        "actload_over_16": sum(1 for s in spikes if s["wall"] > BUDGET_60 and s["tag"] == "act-load"),
    }


def cmd_summarise(args):
    out = Path(args.out)
    cases = {c["id"]: c for c in matrix()}
    results = []
    for path in sorted(out.glob("*-r[0-9]*.csv")):
        m = re.match(r"(.+)-r(\d+)\.csv$", path.name)
        cid, rep = m.group(1), int(m.group(2))
        try:
            s = summarise_file(path, args.warmup)
        except Exception as e:  # a truncated CSV should not hide the rest
            print(f"skip {path.name}: {e}", file=sys.stderr)
            continue
        s.update({"id": cid, "rep": rep, "game": cases.get(cid, {}).get("game", cid.split("-")[0]),
                  "case": cases.get(cid)})
        results.append(s)
    reproducible = reproducible_spikes(results)
    (out / "summary.json").write_text(json.dumps(results, indent=1))
    (out / "reproducible.json").write_text(json.dumps(reproducible, indent=1))
    print(f"{'level':24} {'rep':>3} {'frames':>6} {'mean':>6} {'p99':>6} {'p99.9':>6} {'max':>7} "
          f"{'>8.3':>5} {'>16.7':>5} {'open':>4} {'rld':>3} {'act':>3} {'allocKB':>7} {'gc':>4} {'gcms':>5} "
          f"{'warm>16':>7} progress")
    for s in results:
        p = s["progress"]
        prog = (f"onroute {p['on_route_pct']:.0f}% div@{p['diverged_at']}" if s["kind"] == "trace"
                else f"acts {[(a['zone'], a['act']) for a in p['acts']]}")
        print(f"{s['id']:24} {s['rep']:>3} {s['frames']:>6} {s['mean']:6.2f} {s['p99']:6.2f} {s['p999']:6.2f} "
              f"{s['max']:7.1f} {s['over_8']:>5} {s['over_16']:>5} {s['open_over_16']:>4} {s['reload_over_16']:>3} "
              f"{s['actload_over_16']:>3} {s['alloc_med_kb']:7.1f} {s['gc_count']:>4} "
              f"{s['gc_ms']:>5} {s['warm_over_16']:>7} {prog} still={p['longest_still']}")
    if reproducible:
        print("\nOver-budget frames seen in at least two runs (frame: ms per run, tag)")
        for cid, items in reproducible.items():
            for it in items:
                print(f"  {cid:22} {it['frame']:>6}: {' / '.join(f'{v:.1f}' for v in it['ms'])}  {it['tag']}  "
                      f"{it['alloc_kb']:.0f}KB {it['top']}")
    if args.html:
        write_html(results, Path(args.html), args.notes, reproducible)
        print("Wrote", args.html)


def reproducible_spikes(results, slack=2):
    """Frames over 16.67 ms in two or more runs of a case (within ``slack`` frames).

    A one-run spike with no allocation is usually interference from the shared
    machine (other JVMs, the desktop on the same GPU); a spike that recurs at the
    same route frame is engine work.
    """
    by_case = {}
    for s in results:
        by_case.setdefault(s["id"], []).append(s)
    out = {}
    for cid, runs in by_case.items():
        if len(runs) < 2:
            continue
        runs.sort(key=lambda r: r["rep"])
        seen = []
        for r in runs:
            for sp in r["spikes_over_16"]:
                if any(abs(sp["frame"] - x["frame"]) <= slack for x in seen):
                    continue
                ms = []
                for other in runs:
                    near = [o for o in other["spikes"] if abs(o["frame"] - sp["frame"]) <= slack]
                    ms.append(max((o["wall"] for o in near), default=0.0))
                if sum(1 for v in ms if v > BUDGET_60) >= 2:
                    seen.append({"frame": sp["frame"], "ms": ms, "tag": sp["tag"], "alloc_kb": sp["alloc_kb"],
                                 "x": sp["x"], "y": sp["y"], "zone": sp["zone"], "act": sp["act"],
                                 "top": sp["top"][0][0]})
                else:
                    seen.append({"frame": sp["frame"], "ms": ms, "skip": True})
        kept = [x for x in seen if not x.get("skip")]
        if kept:
            out[cid] = sorted(kept, key=lambda x: -max(x["ms"]))
    return out


def esc(text):
    return (str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def write_html(results, path, notes_path, reproducible=None):
    first = [s for s in results if s["rep"] == 1]
    games = {"s1": "Sonic 1", "s2": "Sonic 2", "s3k": "Sonic 3 & Knuckles"}
    parts = ["<!doctype html><meta charset=utf-8><title>OpenGGF all-level performance sweep</title>",
             "<style>body{font:14px system-ui,sans-serif;margin:24px;max-width:1400px;color:#1b1b1f}"
             "table{border-collapse:collapse;margin:8px 0 24px}td,th{border-bottom:1px solid #ddd;padding:3px 8px;"
             "text-align:right;white-space:nowrap}th{background:#f4f4f6;position:sticky;top:0}"
             "td.l,th.l{text-align:left}.bad{color:#b3261e;font-weight:600}.warn{color:#9a6700}"
             "h2{margin-top:32px}code{background:#f4f4f6;padding:0 3px}.note{max-width:1100px}"
             "svg text{font:11px system-ui,sans-serif}</style>",
             "<h1>OpenGGF all-level performance sweep</h1>"]
    if notes_path and Path(notes_path).exists():
        parts.append(f"<div class=note>{Path(notes_path).read_text()}</div>")
    # Cross-level chart: p99 and max (log scale) per level, first run.
    parts.append("<h2>Cross-level frame time (first run, settled frames)</h2>")
    parts.append(chart(first))
    for game in ("s1", "s2", "s3k"):
        rows = [s for s in first if s["game"] == game]
        if not rows:
            continue
        parts.append(f"<h2>{games[game]}</h2><table><tr><th class=l>level</th><th class=l>driver</th>"
                     "<th>frames</th><th>mean ms</th><th>p99</th><th>p99.9</th><th>max</th><th>&gt;8.33</th>"
                     "<th>&gt;16.67</th><th>alloc KB/f (med)</th><th>alloc KB/f (mean)</th><th>GC n</th>"
                     "<th>GC ms</th><th>warmup &gt;16.67</th><th class=l>progress</th>"
                     "<th class=l>worst settled spikes (frame: ms, top section)</th></tr>")
        for s in rows:
            p = s["progress"]
            if s["kind"] == "trace":
                prog = f"on-route {p['on_route_pct']:.0f}%"
                if p["diverged_at"] is not None:
                    prog += f", diverged @{p['diverged_at']}"
            else:
                prog = "acts " + " → ".join(f"{a['zone']}/{a['act']}" for a in p["acts"])
            if p["longest_still"] > 600:
                prog += f", still {p['longest_still']}f from @{p['longest_still_from']}"
            spikes = "; ".join(f"{x['frame']}: {x['wall']:.1f} {x['top'][0][0]}" for x in s["spikes"][:4])
            parts.append(
                f"<tr><td class=l>{esc(s['id'])}</td><td class=l>{s['kind']}</td><td>{s['frames']}</td>"
                f"<td>{s['mean']:.2f}</td><td>{s['p99']:.2f}</td><td>{s['p999']:.2f}</td>"
                f"<td class={'bad' if s['max'] > BUDGET_60 else ''}>{s['max']:.1f}</td><td>{s['over_8']}</td>"
                f"<td class={'bad' if s['over_16'] else ''}>{s['over_16']}</td><td>{s['alloc_med_kb']:.1f}</td>"
                f"<td>{s['alloc_mean_kb']:.1f}</td><td>{s['gc_count']}</td><td>{s['gc_ms']}</td>"
                f"<td>{s['warm_over_16']}</td><td class=l>{esc(prog)}</td><td class=l>{esc(spikes)}</td></tr>")
        parts.append("</table>")
    if reproducible:
        parts.append("<h2>Reproducible over-budget frames (&gt;16.67 ms in at least two runs)</h2>"
                     "<table><tr><th class=l>level</th><th>frame</th><th class=l>ms per run</th><th>alloc KB</th>"
                     "<th class=l>zone/act</th><th>x</th><th>y</th><th class=l>tag</th>"
                     "<th class=l>dominant section</th></tr>")
        for cid, items in reproducible.items():
            for it in items:
                parts.append(f"<tr><td class=l>{esc(cid)}</td><td>{it['frame']}</td>"
                             f"<td class=l>{' / '.join(f'{v:.1f}' for v in it['ms'])}</td>"
                             f"<td>{it['alloc_kb']:.0f}</td><td class=l>{it['zone']}/{it['act']}</td>"
                             f"<td>{it['x']}</td><td>{it['y']}</td><td class=l>{esc(it['tag'])}</td>"
                             f"<td class=l>{esc(it['top'])}</td></tr>")
        parts.append("</table>")
    repeats = [s for s in results if s["rep"] > 1]
    if repeats:
        parts.append("<h2>Repeat runs</h2><table><tr><th class=l>level</th><th>run</th><th>mean</th><th>p99</th>"
                     "<th>p99.9</th><th>max</th><th>&gt;16.67</th><th class=l>&gt;16.67 frames</th></tr>")
        for s in sorted(results, key=lambda s: (s["id"], s["rep"])):
            if not any(r["id"] == s["id"] for r in repeats):
                continue
            frames = ", ".join(str(x["frame"]) for x in sorted(s["spikes_over_16"], key=lambda x: x["frame"]))
            parts.append(f"<tr><td class=l>{esc(s['id'])}</td><td>{s['rep']}</td><td>{s['mean']:.2f}</td>"
                         f"<td>{s['p99']:.2f}</td><td>{s['p999']:.2f}</td><td>{s['max']:.1f}</td>"
                         f"<td>{s['over_16']}</td><td class=l>{esc(frames)}</td></tr>")
        parts.append("</table>")
    path.write_text("\n".join(parts))


def chart(rows):
    width, row_h, left, plot = 900, 16, 170, 680
    height = 40 + row_h * len(rows)
    lo, hi = math.log10(0.5), math.log10(300)

    def x(v):
        v = min(max(v, 0.5), 300)
        return left + (math.log10(v) - lo) / (hi - lo) * plot

    out = [f"<svg width={width} height={height} role=img aria-label='p99 and max frame time per level'>"]
    for tick in (1, 2, 5, 8.33, 16.67, 33.3, 100, 300):
        out.append(f"<line x1={x(tick):.1f} x2={x(tick):.1f} y1=20 y2={height - 10} "
                   f"stroke={'#b3261e' if tick == 16.67 else '#ddd'} stroke-dasharray={'4 3' if tick in (8.33, 16.67) else '0'}/>"
                   f"<text x={x(tick):.1f} y=14 text-anchor=middle fill=#555>{tick:g}</text>")
    for i, s in enumerate(rows):
        y = 28 + i * row_h
        out.append(f"<text x={left - 6} y={y + 4} text-anchor=end>{esc(s['id'])}</text>")
        out.append(f"<line x1={x(s['p50']):.1f} x2={x(s['max']):.1f} y1={y} y2={y} stroke=#c9c9d4 stroke-width=2/>")
        out.append(f"<circle cx={x(s['p50']):.1f} cy={y} r=3 fill=#6b6b80><title>p50 {s['p50']:.2f} ms</title></circle>")
        out.append(f"<circle cx={x(s['p99']):.1f} cy={y} r=4 fill=#2a6fdb><title>p99 {s['p99']:.2f} ms</title></circle>")
        out.append(f"<circle cx={x(s['max']):.1f} cy={y} r=4 fill={'#b3261e' if s['max'] > BUDGET_60 else '#7a7a7a'}>"
                   f"<title>max {s['max']:.1f} ms</title></circle>")
    out.append("</svg><p>Grey dot p50, blue dot p99, red/grey dot max (ms, log scale). Dashed lines 8.33 / "
               "16.67 ms budgets.</p>")
    return "".join(out)


# ---------------------------------------------------------------- attribute

JFR_EVENT = re.compile(r"^(jdk\.\w+) \{")
JFR_TIME = re.compile(r"startTime = (\d+):(\d+):(\d+)\.(\d+) \((\d+)-(\d+)-(\d+)\)")
JFR_FRAME = re.compile(r"^\s+([\w$.]+)\.([\w$<>]+)\(.*?\)\s+line: (\d+)")


def jfr_events(jfr, events):
    """Stream (name, epoch_us, thread, frames, fields) from ``jfr print`` text output.

    Text output is parsed line by line so a multi-minute 1 ms recording never
    has to fit in memory. Times are printed in local time to the millisecond.
    """
    import datetime
    proc = subprocess.Popen(["jfr", "print", "--stack-depth", "64", "--events", ",".join(events), str(jfr)],
                            stdout=subprocess.PIPE, text=True, bufsize=1 << 20)
    name, when, thread, frames, fields, in_stack = None, None, None, [], {}, False
    for line in proc.stdout:
        m = JFR_EVENT.match(line)
        if m:
            name, when, thread, frames, fields, in_stack = m.group(1), None, None, [], {}, False
            continue
        if name is None:
            continue
        stripped = line.strip()
        if stripped == "}" and not line.startswith("  "):
            yield name, when, thread, frames, fields
            name = None
            continue
        if in_stack:
            if stripped.startswith("]"):
                in_stack = False
                continue
            f = JFR_FRAME.match(line)
            if f:
                frames.append(f"{f.group(1)}.{f.group(2)}:{f.group(3)}")
            continue
        if stripped.startswith("startTime"):
            t = JFR_TIME.search(stripped)
            if t:
                hh, mm, ss, ms, yy, mo, dd = (int(x) for x in t.groups())
                dt = datetime.datetime(yy, mo, dd, hh, mm, ss, ms * 1000)
                when = int(dt.timestamp() * 1_000_000)
        elif stripped.startswith(("sampledThread", "eventThread")):
            q = re.search(r'"([^"]*)"', stripped)
            thread = q.group(1) if q else None
        elif stripped.startswith("stackTrace = ["):
            in_stack = True
        elif " = " in stripped:
            k, v = stripped.split(" = ", 1)
            fields[k] = v
    proc.wait()


def engine_frame(frames, skip=("com.openggf.debug.", "com.openggf.tools.")):
    for f in frames:
        if f.startswith("com.openggf.") and not f.startswith(skip):
            return f
    return frames[0] if frames else "?"


def engine_chain(frames, n=2, skip=("com.openggf.debug.", "com.openggf.tools.")):
    """First ``n`` engine frames (allocation site and its caller)."""
    chain = [f for f in frames if f.startswith("com.openggf.") and not f.startswith(skip)][:n]
    return " <- ".join(chain) if chain else (frames[0] if frames else "?")


def weight_kb(text):
    m = re.match(r"([\d.]+) (bytes|kB|MB|GB)", text or "")
    if not m:
        return 0.0
    return float(m.group(1)) * {"bytes": 1 / 1024, "kB": 1, "MB": 1024, "GB": 1024 * 1024}[m.group(2)]


def cmd_attribute(args):
    """Attribute spike frames and steady-state allocation in one CSV/JFR pair."""
    kind, cols, sections, rows = load_csv(args.csv)
    if "epoch_us" not in cols:
        sys.exit("CSV has no epoch_us column; rerun the case with the current probes")
    windows = []
    for i, r in enumerate(rows):
        wall = float(r[cols["wall_ms"]])
        if i >= args.warmup and wall > args.threshold:
            start = int(r[cols["epoch_us"]])
            # JFR prints milliseconds: widen by 1 ms each side.
            windows.append((start - 1000, start + int(wall * 1000) + 1000, int(r[0]), wall,
                            float(r[cols["alloc_kb"]])))
    windows.sort()
    settled_from = int(rows[min(args.warmup, len(rows) - 1)][cols["epoch_us"]]) if rows else 0
    # The probe writes its CSV after the last frame; that is not engine allocation.
    settled_to = (int(rows[-1][cols["epoch_us"]]) + int(float(rows[-1][cols["wall_ms"]]) * 1000)) if rows else 0
    hits = {w[2]: {"leaf": {}, "engine": {}, "inclusive": {}, "alloc": {}, "n": 0} for w in windows}
    steady_alloc, steady_total = {}, 0.0
    steady_cpu, steady_samples = {}, 0
    starts = [w[0] for w in windows]
    import bisect
    epochs = [int(r[cols["epoch_us"]]) for r in rows]
    traced = []
    for name, when, thread, frames, fields in jfr_events(args.jfr, ["jdk.ExecutionSample", "jdk.NativeMethodSample",
                                                                   "jdk.ObjectAllocationSample", "jdk.MethodTrace"]):
        if when is None or (args.thread and thread != args.thread):
            continue
        if name == "jdk.MethodTrace":
            # Calls are matched to the frame whose start precedes them (1 ms resolution).
            f = bisect.bisect_right(epochs, when) - 1
            method = fields.get("method", "?").split("(")[0]
            traced.append((int(rows[f][0]) if f >= 0 else -1, method, fields.get("duration", "?"),
                           engine_chain(frames, 4)))
            continue
        k = bisect.bisect_right(starts, when) - 1
        win = windows[k] if k >= 0 and windows[k][0] <= when <= windows[k][1] else None
        if name == "jdk.ObjectAllocationSample":
            kb = weight_kb(fields.get("weight"))
            key = f"{fields.get('objectClass', '?').split(' ')[0]} @ {engine_chain(frames)}"
            if settled_from <= when <= settled_to and win is None:
                steady_alloc[key] = steady_alloc.get(key, 0.0) + kb
                steady_total += kb
            if win:
                h = hits[win[2]]["alloc"]
                h[key] = h.get(key, 0.0) + kb
            continue
        if not win:
            if settled_from <= when <= settled_to:
                steady_samples += 1
                for f in set(fr.rsplit(":", 1)[0] for fr in frames
                             if fr.startswith(("com.openggf.", "org.lwjgl.")) and not fr.startswith(
                                 ("com.openggf.tools.", "com.openggf.debug."))):
                    steady_cpu[f] = steady_cpu.get(f, 0) + 1
            continue
        h = hits[win[2]]
        h["n"] += 1
        leaf = frames[0] if frames else "?"
        h["leaf"][leaf] = h["leaf"].get(leaf, 0) + 1
        e = engine_frame(frames)
        h["engine"][e] = h["engine"].get(e, 0) + 1
        for f in set(fr.rsplit(":", 1)[0] for fr in frames if fr.startswith("com.openggf.")):
            h["inclusive"][f] = h["inclusive"].get(f, 0) + 1

    def top(d, n):
        return sorted(d.items(), key=lambda kv: -kv[1])[:n]

    out = []
    for start, end, frame, wall, alloc in sorted(windows, key=lambda w: -w[3])[:args.top]:
        h = hits[frame]
        out.append(f"== frame {frame}: {wall:.1f} ms, {alloc:.0f} KB allocated, {h['n']} samples")
        for k, v in top(h["inclusive"], args.depth):
            out.append(f"   incl {v:4d}  {k}")
        for k, v in top(h["leaf"], 6):
            out.append(f"   leaf {v:4d}  {k}")
        for k, v in top(h["alloc"], 6):
            out.append(f"   alloc {v:8.0f} KB  {k}")
    if traced:
        out.append(f"== traced method calls ({len(traced)}; frame, method, duration, engine callers)")
        for frame, method, duration, chain in traced[:args.trace_limit]:
            out.append(f"   {frame:6d}  {method}  [{duration}]  <- {chain}")
    out.append(f"== steady-state allocation outside spike frames: {steady_total / 1024:.1f} MB sampled weight")
    for k, v in top(steady_alloc, args.depth):
        out.append(f"   {100 * v / max(1e-9, steady_total):5.1f}%  {v / 1024:8.1f} MB  {k}")
    out.append(f"== steady-state CPU outside spike frames: {steady_samples} main-thread samples (inclusive share)")
    for k, v in top(steady_cpu, args.depth * 3):
        out.append(f"   {100 * v / max(1, steady_samples):5.1f}%  {k}")
    text = "\n".join(out)
    print(text)
    if args.output:
        Path(args.output).write_text(text + "\n")


def cmd_list(_args):
    for c in matrix():
        ok = Path(c["path"]).exists()
        print(f"{c['id']:24} {c['kind']:6} {c['path']}{'' if ok else '  MISSING'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list")
    run = sub.add_parser("run")
    run.add_argument("--out", default="target/perf-sweep")
    run.add_argument("--only")
    run.add_argument("--repeat", type=int, default=1)
    run.add_argument("--jfr", action="store_true")
    run.add_argument("--timeout", type=int, default=2400)
    run.add_argument("--method-trace", help="JFR method-trace filter (with --jfr), e.g. "
                     "'com.openggf.level.LevelTilemapManager::invalidateForegroundTilemap;...'")
    summ = sub.add_parser("summarise")
    summ.add_argument("--out", default="target/perf-sweep")
    summ.add_argument("--warmup", type=int, default=600)
    summ.add_argument("--html")
    summ.add_argument("--notes", help="HTML fragment (findings) to place above the tables")
    att = sub.add_parser("attribute", help="match a JFR recording to spike frames of its CSV")
    att.add_argument("--csv", required=True)
    att.add_argument("--jfr", required=True)
    att.add_argument("--threshold", type=float, default=BUDGET_60, help="spike frame wall ms")
    att.add_argument("--warmup", type=int, default=600)
    att.add_argument("--thread", default="main", help="sampled thread name ('' for all)")
    att.add_argument("--top", type=int, default=15, help="spike frames to print")
    att.add_argument("--depth", type=int, default=14, help="rows per spike / steady list")
    att.add_argument("--output")
    att.add_argument("--trace-limit", type=int, default=200, help="traced method calls to print")
    args = parser.parse_args()
    {"list": cmd_list, "run": cmd_run, "summarise": cmd_summarise, "attribute": cmd_attribute}[args.command](args)


if __name__ == "__main__":
    main()
