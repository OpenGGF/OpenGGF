#!/usr/bin/env python3
"""Native check of a cold route with filler-row composition: observe native lag VBlanks, compare.

Purpose: a route (`<stem>.route.json` + `.script`) boots straight into a level, so native
BizHawk plays it after an entry prefix: rows [0, prefix) of a recorded movie whose next row is
the route's row 0. A native lag VBlank consumes a movie row without polling the pad; this tool
finds each one by observation and inserts a filler row there (a copy of the previous row, never
read by the game), so native polls exactly the route's rows. Each pass composes the movie with
the fillers known so far (`compose_route_on_movie_prefix.py --filler-before`), captures it with
TraceChaser's headless harness, and walks the native rows, comparing each polled row's players
field for field with the engine's track of the same rows (`RouteTrackRecorder`). A lag frame is a
row whose `lag_counter` (`Lag_frame_count`) rose; one that consumed a route row instead of a
filler is a new lag fact. The walk continues past it while the row native then polls holds the
same buttons as the row it should have read, so one pass can find several; the next pass
composes them all and captures the aligned run again. Comparison
only: nothing here reaches the engine, and lag is observed, never predicted.

Inputs: the route's derived movie (`RouteFiles.exportBk2`), its engine track, the entry movie
and prefix length, the trace's first movie row (its `bk2_frame_offset`), the harness roots, the
ROM and an empty work directory. Output: `WORK/result.json` with the fillers (lag facts as
`beforeRow` route rows), the harness identity and the verdict: `agrees` through the last
compared row, or `diverged` at the first route row and field that differ.

Example (SSZ1 Sonic+Tails; the complete-run movie arms SSZ at 396720):
  python3 -I tools/traces/route_native_check.py --route-movie /tmp/route.bk2 \\
      --engine-track /tmp/route-track.csv.gz \\
      --movie src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2 \\
      --prefix-rows 396719 --trace-offset 396720 --rom /path/s3k.gen --work /tmp/native-check

Origin: shared route planner step 6 (2026-11-02), the SSZ1 cold routes after their exact
re-synchronising repair; replaces the hand-composed SSZ1 check that stopped at its first lag.
"""
import argparse
import csv
import gzip
import json
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
FIELDS = [("x", "x"), ("y", "y"), ("x_sub", "xsub"), ("y_sub", "ysub"), ("x_speed", "xvel"),
          ("y_speed", "yvel"), ("g_speed", "gvel"), ("air", "air"), ("rolling", "roll")]


def signed16(value):
    v = int(value, 16)
    return v - 0x10000 if v & 0x8000 else v


def native_value(row, prefix, field):
    raw = row[prefix + field]
    if field in ("air", "rolling"):
        return int(raw)
    if field.endswith("speed"):
        return signed16(raw)
    return int(raw, 16)


def engine_track(path):
    with gzip.open(path, "rt") as handle:
        lines = [line.rstrip("\n") for line in handle]
    columns = lines[1].split(",")
    return [dict(zip(columns, line.split(","))) for line in lines[2:]]


def native_rows(directory, offset):
    for segment in sorted(os.listdir(directory)):
        meta_path = os.path.join(directory, segment, "metadata.json")
        if not os.path.isfile(meta_path):
            continue
        with open(meta_path) as handle:
            meta = json.load(handle)
        if meta.get("bk2_frame_offset") != offset:
            continue
        physics = os.path.join(directory, segment, "physics.csv")
        if os.path.exists(physics):
            with open(physics) as handle:
                return list(csv.DictReader(handle))
        with gzip.open(physics + ".gz", "rt") as handle:
            return list(csv.DictReader(handle))
    raise SystemExit(f"no captured segment starts at movie row {offset} in {directory}")


def walk(native, track, route, fillers, offset_shift):
    """Walks one capture of the route composed with `fillers`.

    A native lag frame is a row whose `lag_counter` (`Lag_frame_count`, V-int routine 0 runs)
    rose. Lag frames that consumed a filler are expected. An unexpected one consumed a route
    row unpolled, so every later poll of this capture reads one row ahead; while that next row
    holds the same buttons as the row the game should have read, gameplay is unaffected and the
    walk continues, recording each such lag as a new filler. Returns
    ('lags', new_before_rows) when the capture can no longer stand for the aligned run,
    ('filler-polled', row) when a known filler was polled (a lag that did not recur),
    ('diverged', row, field, native, engine) or ('agrees', last_row, new_before_rows).
    """
    sequence = []
    before = {}
    for k in fillers:
        before[k] = before.get(k, 0) + 1
    for k in range(len(route)):
        sequence.extend([None] * before.get(k, 0))
        sequence.append(k)
    new_lags = []
    intended = None
    last = -1
    for r, row in enumerate(native):
        j = r + offset_shift
        if j >= len(sequence):
            break
        item = sequence[j]
        if intended is None:
            if item is None:
                raise SystemExit("the first traced row is a filler")
            intended = item
        lag = r > 0 and int(row["lag_counter"], 16) > int(native[r - 1]["lag_counter"], 16)
        if lag:
            if item is not None:
                new_lags.append(intended)
            continue
        if item is None:
            return ("filler-polled", intended)
        k = intended
        intended += 1
        if k >= len(route) or k >= len(track):
            break
        if route[item] != route[k]:
            return ("lags", new_lags)
        engine = track[k]
        for player, prefix, eprefix in (("P1", "player_", ""), ("P2", "sidekick_", "p2")):
            if player == "P2" and (row.get("sidekick_present") != "1" or not engine.get("p2x")):
                continue
            for nfield, efield in FIELDS:
                n = native_value(row, prefix, nfield)
                e = int(engine[eprefix + efield])
                if n != e:
                    if new_lags:
                        return ("lags", new_lags)
                    return ("diverged", k, f"{player}.{efield}", n, e)
        last = k
    return ("agrees", last, new_lags)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--route-movie", required=True)
    parser.add_argument("--engine-track", required=True)
    parser.add_argument("--movie", required=True)
    parser.add_argument("--prefix-rows", type=int, required=True)
    parser.add_argument("--trace-offset", type=int, required=True)
    parser.add_argument("--rom", required=True)
    parser.add_argument("--work", required=True)
    parser.add_argument("--tracechaser-root", default=os.path.join(HERE, "..", "tracechaser"))
    parser.add_argument("--repository-root", default=os.path.join(HERE, "..", ".."))
    parser.add_argument("--fillers", default="", help="known lag facts (beforeRow list) to start from")
    parser.add_argument("--max-passes", type=int, default=40)
    args = parser.parse_args(argv)

    tracechaser = os.path.realpath(args.tracechaser_root)
    repository = os.path.realpath(args.repository_root)
    work = os.path.realpath(args.work)
    os.makedirs(work, exist_ok=True)
    track = engine_track(args.engine_track)
    with zipfile.ZipFile(args.route_movie) as z:
        route = [line for line in z.read("Input Log.txt").decode().splitlines() if line.startswith("|")]
    route_rows = len(route)
    fillers = sorted(int(k) for k in args.fillers.split(",") if k.strip())
    revision = subprocess.run(["git", "-C", tracechaser, "rev-parse", "--short", "HEAD"], capture_output=True,
                              text=True, check=True).stdout.strip()
    harness = f"tracechaser {revision} bizhawk-headless BizHawk 2.11 Genplus-gx"
    shift = args.trace_offset - args.prefix_rows
    verdict = None
    for attempt in range(args.max_passes):
        composite = os.path.join(work, f"pass-{attempt}.bk2")
        subprocess.run([sys.executable, "-I", os.path.join(HERE, "compose_route_on_movie_prefix.py"),
                        "--movie", args.movie, "--prefix-rows", str(args.prefix_rows), "--route", args.route_movie,
                        "--filler-before", ",".join(map(str, fillers)), "--out", composite], check=True)
        output = os.path.join(work, f"pass-{attempt}")
        env = dict(os.environ)
        for name in ("DISPLAY", "WAYLAND_DISPLAY", "LD_LIBRARY_PATH"):
            env.pop(name, None)
        with open(output + ".log", "w") as log:
            subprocess.run([os.path.join(tracechaser, "bizhawk-headless", "run.sh"), "--tracechaser-root", tracechaser,
                            "--input-repository-root", repository, "--fixture-root",
                            os.path.join(repository, "src", "test", "resources", "traces"), "--mode", "trace",
                            "--rom", args.rom, "--movie", composite, "--output", output,
                            "--trace-profile", "complete_run"], check=True, env=env, stdout=log, stderr=log)
        result = walk(native_rows(output, args.trace_offset), track, route, fillers, shift)
        # Keep only the route's segment; the entry prefix's zones are not evidence for this check.
        for segment in os.listdir(output):
            path = os.path.join(output, segment)
            meta = os.path.join(path, "metadata.json")
            if os.path.isdir(path) and os.path.isfile(meta):
                with open(meta) as handle:
                    keep = json.load(handle).get("bk2_frame_offset") == args.trace_offset
                if not keep:
                    shutil.rmtree(path)
        print(f"pass {attempt}: fillers {len(fillers)} -> {result}", flush=True)
        if result[0] == "lags" and not result[1]:
            raise SystemExit("a misaligned poll without a new lag fact")
        if result[0] == "lags" or (result[0] == "agrees" and result[2]):
            # New lag facts: capture again with them so the recorded run is the aligned one.
            fillers = sorted(fillers + result[-1] if result[0] == "agrees" else fillers + result[1])
            continue
        verdict = result[:2] if result[0] == "agrees" else result
        break
    report = {"harness": harness, "entryMovie": os.path.relpath(os.path.realpath(args.movie), repository),
              "prefixRows": args.prefix_rows, "routeRows": route_rows, "fillers": fillers,
              "verdict": list(verdict) if verdict else ["incomplete", args.max_passes]}
    with open(os.path.join(work, "result.json"), "w") as handle:
        json.dump(report, handle, indent=1)
    print(json.dumps(report))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
