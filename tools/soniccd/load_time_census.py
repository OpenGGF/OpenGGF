#!/usr/bin/env python3
"""Sonic CD load-time census: how many V-INTs each native load takes, and how stable it is.

Purpose: measure, from the existing native BizHawk recordings, the hardware time of every Sonic CD
load a recording shows, so a normal-play load-time manifest (gameplay.loadTimeSimulation) can be
keyed and checked. Reports four tables and, for each value, the recordings it comes from and
whether it is constant across them:
  1. Stage entry: levelVIntCounter on trace row 0 (the counter LevelStart's .ClearFlowers cleared,
     Level/Main.asm:178-184, plus every V-INT before Level_MainLoop's first VSync was serviced,
     :333-335). Of it, 22 are the fade's VSyncs (move.w #(7*3),d4 / dbf: FadeFromBlack,
     Palette Fade.asm:23-30; FadeFromWhite, :192-199) and 1 is iteration 1's V-INT; the rest (the "load span")
     is .ClearFlowers to .SkipClear: the synchronous decompression with interrupts enabled
     (:239-252) and the .WaitPLC2 loop (:253-259).
  2. Re-entries inside one probe run (rehost-flow frames.csv): death restarts, act change,
     special-stage return, warp arrival, split into load span and fade, with the context that
     selects LoadLevelData's PLC (spawnMode, plcLoadFlags bit 1; Palmtree Panic/Load Level Data.asm:37-48).
  3. The title card's PLC cue: rows in the first 300 on which no V-INT finished (HELD: the level
     V-INT's DecompPLCSlow, Functions (General).asm:721-760, ran past the sample), per stage.
  4. Disc reads (rehost-programs frames.csv, CD PRG RAM peeks): frames from a read request
     (sector count written, sectors-read reset) to its first sector.

Inputs: the native root (default ~/Downloads/Sonic-CD/native): rehost-*/**/trace/{physics.csv,
metadata.json} (v5 stage traces), rehost-flow/*/frames.csv (FLOW2 probe runs) and
rehost-programs/*/frames.csv (END probe runs). Output: Markdown on stdout; --json writes the
same data as JSON.

Origin: LOADTIME lane (2026-11-13), for the Sonic CD load-time manifest proposal
(planning/rehost/LOADTIME-report.md). Uses lag_census.py's trace reader and row shapes.
"""
import argparse
import csv
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lag_census  # noqa: E402

FADE_VINTS = 22          # d4 = 7*3, dbf: FadeFromBlack (Palette Fade.asm:23-30), FadeFromWhite (:192-199)
FIRST_ITERATION_VINTS = 1  # Level_MainLoop's first VSync (Level/Main.asm:333-335)


def rel(root, path):
    return os.path.relpath(path, root).replace(os.sep, "/")


def stage_entries(root, traces):
    table = {}
    for t in traces:
        rows, _release, meta, events = lag_census.census(t)
        row0 = rows[0]
        held = [e["row"] for e in events if e["kind"] == "HELD" and e["row"] < 300]
        entry = table.setdefault(meta["stage_file"], {"entry": [], "card": []})
        source = rel(root, os.path.dirname(t))
        entry["entry"].append((lag_census.h(row0, "vblank_counter"), source))
        if len(rows) >= 300:
            last = held[-1] if held else None
            entry["card"].append(((len(held), held[0] if held else None, last,
                                   lag_census.h(rows[last], "gameplay_frame_counter") if held else None), source))
    out = []
    for stage in sorted(table):
        values = sorted({v for v, _ in table[stage]["entry"]})
        cards = sorted({v for v, _ in table[stage]["card"]}, key=str)
        out.append({
            "stage": stage,
            "rowZeroVIntCounter": values[0] if len(values) == 1 else values,
            "loadSpan": values[0] - FADE_VINTS - FIRST_ITERATION_VINTS if len(values) == 1 else None,
            "constant": len(values) == 1,
            "sources": sorted(s for _, s in table[stage]["entry"]),
            "titleCardHeld": (dict(zip(("heldRows", "firstHeldRow", "lastHeldRow", "levelFramesAtLast"), cards[0]))
                              if len(cards) == 1 else [list(c) for c in cards]),
            "titleCardConstant": len(cards) <= 1,
            "titleCardSources": sorted(s for _, s in table[stage]["card"]),
        })
    return out


def flow_reentries(root):
    out = []
    flow = os.path.join(root, "rehost-flow")
    if not os.path.isdir(flow):
        return out
    for run in sorted(os.listdir(flow)):
        path = os.path.join(flow, run, "frames.csv")
        if not os.path.isfile(path):
            continue
        with open(path, newline="") as f:
            rows = list(csv.DictReader(f))
        clear = None
        for i in range(1, len(rows)):
            prev, cur = rows[i - 1], rows[i]
            vc, pvc = int(cur["vintCounter"], 16), int(prev["vintCounter"], 16)
            if vc < pvc and cur["gameMode"] == "128":
                clear = i
            if clear is not None and int(cur["levelFrames"]) == 1 and int(prev["levelFrames"]) != 1:
                # .SkipClear's clr levelFrames (Level/Main.asm:300), or, on an entry whose levelFrames was
                # already 0, the title card's ctrlLocked set just before it (:271-275).
                skip = next(j for j in range(clear + 1, i + 1)
                            if (int(rows[j]["levelFrames"]) == 0 and int(rows[j - 1]["levelFrames"]) != 0)
                            or (rows[j]["ctrlLocked"] == "1" and rows[j - 1]["ctrlLocked"] == "0"))
                ctx = rows[clear]
                out.append({
                    "run": run, "zone": int(cur["zone"]), "act": int(cur["act"]), "timeZone": int(cur["timeZone"]),
                    "clearFrame": int(rows[clear]["frame"]), "skipClearFrame": int(rows[skip]["frame"]),
                    "levelFrames1Frame": int(cur["frame"]),
                    "loadSpan": int(rows[skip]["vintCounter"], 16),
                    "rowZeroVIntCounter": vc,
                    "fadeAndFirstIteration": vc - int(rows[skip]["vintCounter"], 16),
                    "spawnModeAtClear": int(ctx["spawnMode"]), "plcLoadFlagsAtClear": int(ctx["plcLoadFlags"]),
                    # cameraX as LoadLevelData reads it: set by LevelSizeLoad/LevelScroll before it (:245-248).
                    "cameraXAtLoad": int(rows[skip]["cam_x"], 16), "goodFuture": int(cur["goodFuture"]),
                    "heldRowsInLoad": sum(1 for j in range(clear + 1, skip + 1)
                                          if rows[j]["vintCounter"] == rows[j - 1]["vintCounter"]),
                    "source": rel(root, path),
                })
                clear = None
    return out


def disc_reads(root):
    out = []
    programs = os.path.join(root, "rehost-programs")
    if not os.path.isdir(programs):
        return out
    for run in sorted(os.listdir(programs)):
        path = os.path.join(programs, run, "frames.csv")
        if not os.path.isfile(path):
            continue
        with open(path, newline="") as f:
            rows = list(csv.DictReader(f))
        if not rows or "secread" not in rows[0]:
            continue
        request = None
        for i in range(1, len(rows)):
            prev, cur = rows[i - 1], rows[i]
            read, pread = int(cur["secread"], 16), int(prev["secread"], 16)
            count, pcount = int(cur["seccnt"], 16), int(prev["seccnt"], 16)
            # A request: sectors-read reset to 0 with a new sector count (SP.asm's read setup).
            if read == 0 and count != pcount and count > 0 and count < 0x100000:
                request = (i, count)
            if request is not None and pread == 0 and read > 0:
                out.append({"run": run, "requestFrame": int(rows[request[0]]["frame"]),
                            "sectors": request[1], "firstSectorFrame": int(cur["frame"]),
                            "frames": int(cur["frame"]) - int(rows[request[0]]["frame"]),
                            "source": rel(root, path)})
                request = None
    return out


def markdown(data):
    lines = ["## 1. Stage entry (trace row 0)", "",
             "| Stage | row-0 levelVIntCounter | load span | constant | recordings | title-card held rows "
             "(count, first, last, levelFrames at last) | constant |",
             "|---|---|---|---|---|---|---|"]
    for s in data["stageEntries"]:
        card = s["titleCardHeld"]
        card_text = ("%(heldRows)s, %(firstHeldRow)s, %(lastHeldRow)s, %(levelFramesAtLast)s" % card
                     if isinstance(card, dict) else str(card))
        lines.append("| %s | %s | %s | %s | %d | %s | %s |" % (
            s["stage"], s["rowZeroVIntCounter"], s["loadSpan"], "yes" if s["constant"] else "NO",
            len(s["sources"]), card_text, "yes" if s["titleCardConstant"] else "NO"))
    lines += ["", "## 2. Re-entries in one run (rehost-flow)", "",
              "| Run | zone/act/timeZone | load span | fade + iteration 1 | row-0 counter | spawnMode | "
              "plcLoadFlags | held rows in load |", "|---|---|---|---|---|---|---|---|"]
    for r in data["flowReentries"]:
        lines.append("| %s @%d | %d/%d/%d | %d | %d | %d | %d | %d | %d |" % (
            r["run"], r["levelFrames1Frame"], r["zone"], r["act"], r["timeZone"], r["loadSpan"],
            r["fadeAndFirstIteration"], r["rowZeroVIntCounter"], r["spawnModeAtClear"],
            r["plcLoadFlagsAtClear"], r["heldRowsInLoad"]))
    lines += ["", "## 4. Disc reads (rehost-programs)", "",
              "| Run | request frame | sectors | first sector frame | frames |", "|---|---|---|---|---|"]
    for r in data["discReads"]:
        lines.append("| %s | %d | %d | %d | %d |" % (r["run"], r["requestFrame"], r["sectors"],
                                                     r["firstSectorFrame"], r["frames"]))
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("root", nargs="?", default=os.path.expanduser("~/Downloads/Sonic-CD/native"))
    p.add_argument("--json", help="also write the data as JSON to this file")
    args = p.parse_args()
    root = os.path.abspath(args.root)
    dirs = [os.path.join(root, d) for d in sorted(os.listdir(root)) if d.startswith("rehost-")]
    data = {"stageEntries": stage_entries(root, list(lag_census.find_traces(dirs))),
            "flowReentries": flow_reentries(root),
            "discReads": disc_reads(root)}
    if args.json:
        with open(args.json, "w") as f:
            json.dump(data, f, indent=1, sort_keys=True)
            f.write("\n")
    print(markdown(data))


if __name__ == "__main__":
    main()
