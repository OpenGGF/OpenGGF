#!/usr/bin/env python3
"""Generate Sonic CD's measured load-time manifest (and seed its FAST copy) from native recordings.

Purpose: write src/main/resources/load-time-profiles/soniccd-v1.json, the generator-owned manifest
gameplay.loadTimeSimulation PROFILED reads for Sonic CD, from the measurements load_time_census.py
makes. With --fast it also writes soniccd-fast-v1.json, the copy FAST reads, which may be hand-tuned
afterwards (as s3k-fast-v1.json is); regenerate only the measured file once the copy has been tuned.
The two kinds are the owner-approved normal-play load kinds (contract section "2026-11-13 extension:
Sonic CD normal-play load kinds"):
  scd_stage_load  one stage load, LevelStart from .ClearFlowers (Level/Main.asm:182) to
                  Level_MainLoop's first VSync (:333-335): the row-0 levelVIntCounter minus iteration
                  1's V-INT. Keyed by the stage file (ISO extent, byte length) and the PLC list
                  LoadLevelData queues for the entry (Palmtree Panic/Load Level Data.asm:37-48): 0 on a
                  fresh entry, SectionInitPLCs at the camera's section when spawnMode is set
                  (LoadSectionArt, Section Art Load.asm:7-22).
  cd_read_ready   a video stream read: ROMREADN to the first sector (ReadFMVSectors, SP.asm:720-757),
                  keyed by the file's ISO extent and the sector count FileEngine_LoadFMV's .GetSectors
                  computes (SP.asm:691-699). Only the three video streams (OPN, GOODEND, BADEND).
serviceFrames is the most common measured value (ties: the lower); min/max keep every sample, so a
context that differs (R12A after an act clear: one V-INT more) shows as a range, not a fit.

Inputs: the native root (default ~/Downloads/Sonic-CD/native; see load_time_census.py), the media
root holding USA/*.cue (default ~/Downloads/Sonic-CD/media), and the repository's
src/main/resources/soniccd/object-catalogue-<zone>.tsv (each stage's SectionRanges offset).
Output: the manifest(s) under --out (default src/main/resources/load-time-profiles).

Origin: LOADTIME lane part 2 (2026-11-13), after the owner approved both kinds.
"""
import argparse
import csv
import hashlib
import json
import os
import struct
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import lag_census  # noqa: E402
import load_time_census as census  # noqa: E402
from retail_profiles import parse_cue  # noqa: E402
from stage_inventory import RawIso  # noqa: E402

REPO = HERE.parent.parent
SERVICE_MODEL = "soniccd-load-v1"
ZONE_CATALOGUE = {"1": "ppz", "3": "ccz", "4": "ttz", "5": "qqz", "6": "wwz", "7": "ssz", "8": "mmz"}
RUN_STREAMS = (("opening-", "OPN.STM;1"), ("vm-goodend", "GOODEND.STM;1"), ("vm-badend", "BADEND.STM;1"))


def fingerprint(kind, rom, length, dest, dest_length, variant, modules):
    """HardwareSubmissionFingerprint.computeCanonical: DataOutputStream big-endian ints, length-prefixed UTF-8."""
    def text(value):
        raw = value.encode("utf-8")
        return struct.pack(">i", len(raw)) + raw
    data = (text(kind) + struct.pack(">iiii", rom, length, dest, dest_length) + text(variant)
            + struct.pack(">i", modules))
    return "sha256:" + hashlib.sha256(data).hexdigest()


def stage_fingerprint(entry, plc_list):
    return fingerprint("SCD_STAGE_LOAD", entry["lba"], entry["size"], plc_list, 0, "LoadLevelData", 0)


def sectors(size):
    """FileEngine_LoadFMV's .GetSectors: start at 1, add one per further $800 bytes (SP.asm:691-699)."""
    count, rest = 1, size
    while True:
        rest -= 0x800
        if rest <= 0:
            return count
        count += 1


def read_fingerprint(entry):
    return fingerprint("CD_READ_READY", entry["lba"], sectors(entry["size"]), 0, 0, "ROMREADN", 0)


def section_init_plc(stage, image, camera_x):
    """LoadSectionArt (Section Art Load.asm:7-22): the first SectionRanges bound above cameraX picks the
    SectionInitPLCs entry. The table offset comes from the zone's object catalogue."""
    zone = ZONE_CATALOGUE.get(stage[1])
    path = REPO / "src/main/resources/soniccd" / f"object-catalogue-{zone}.tsv"
    with open(path, newline="") as f:
        rows = [r for r in csv.reader(f, delimiter="\t") if r and not r[0].startswith("#")]
    header, body = rows[0], {r[0]: r for r in rows[1:]}
    cell = body[stage][header.index("sectionRanges")]
    if cell in ("", "-"):
        raise SystemExit(f"{stage}: no SectionRanges in {path.name}")
    ranges = int(cell, 16)
    words = []
    while True:
        value = struct.unpack_from(">H", image, ranges + 2 * len(words))[0]
        words.append(value)
        if value == 0xFFFF:
            break
    section = next(i for i, bound in enumerate(words) if bound == 0xFFFF or camera_x < bound)
    return struct.unpack_from(">H", image, ranges + 4 * len(words) + 2 * section)[0]


def entry(kind, fp, values, fixture_indexes):
    counts = Counter(values)
    top = max(counts.values())
    return {"kind": kind, "submissionFingerprint": fp,
            "serviceFrames": min(v for v, n in counts.items() if n == top),
            "eligibleBoundaries": ["vint_service"], "sampleCount": len(values),
            "minFrames": min(values), "maxFrames": max(values), "fixtureIndexes": sorted(set(fixture_indexes))}


def build(native_root, media_root):
    cues = sorted((media_root / "USA").glob("*.cue"))
    if len(cues) != 1:
        raise SystemExit("expected one USA CUE under " + str(media_root))
    track = (cues[0].parent / parse_cue(cues[0].read_text())[0]["file"]).resolve()
    dirs = [os.path.join(native_root, d) for d in sorted(os.listdir(native_root)) if d.startswith("rehost-")]
    stages = census.stage_entries(native_root, list(lag_census.find_traces(dirs)))
    flows = census.flow_reentries(native_root)
    reads = census.disc_reads(native_root)

    fixtures, samples = [], {}

    def fixture(name):
        if name not in fixtures:
            fixtures.append(name)
        return fixtures.index(name)

    def add(kind, fp, value, source):
        samples.setdefault((kind, fp), []).append((value, fixture(source)))

    with RawIso(track) as disc:
        for s in stages:
            file = disc.files[f"{s['stage']}__.MMD;1"]
            for source in s["sources"]:
                add("scd_stage_load", stage_fingerprint(file, 0), s["rowZeroVIntCounter"] - 1, source)
        # Re-entries inside one probe run; the run's first entry is the shared New Game prefix (fixtures cover it).
        first = {}
        for r in flows:
            first.setdefault(r["run"], r["clearFrame"])
        for r in flows:
            if r["clearFrame"] == first[r["run"]]:
                continue
            stage = flow_stage(r)
            file = disc.files[f"{stage}__.MMD;1"]
            plc = 0
            if r["spawnModeAtClear"] != 0:
                image = disc.read(file["lba"] * 2048, file["size"])
                plc = section_init_plc(stage, image, r["cameraXAtLoad"])
            add("scd_stage_load", stage_fingerprint(file, plc), r["rowZeroVIntCounter"] - 1,
                r["source"].rsplit("/", 1)[0] + "@" + str(r["levelFrames1Frame"]))
        for r in reads:
            # Which stream a run reads is its route (rehost-programs/README.md): the opening from the title's
            # attract demo, an ending from Visual Mode. The read's sector count must agree with that file.
            name = next((f for prefix, f in RUN_STREAMS if r["run"].startswith(prefix)), None)
            if name is None or r["sectors"] != sectors(disc.files[name]["size"]):
                continue
            add("cd_read_ready", read_fingerprint(disc.files[name]), r["frames"], r["source"].rsplit("/", 1)[0])

    entries = [entry(kind, fp, [v for v, _ in vals], [i for _, i in vals]) for (kind, fp), vals in samples.items()]
    entries.sort(key=lambda e: (e["kind"], e["submissionFingerprint"]))
    return fixtures, entries


def flow_stage(r):
    """The stage file a flow re-entry loaded: zone/act/timeZone as LevelStart sees them (R<zone><act><period>)."""
    zone_digit = {0: 1, 1: 3, 2: 4, 3: 5, 4: 6, 5: 7, 6: 8}[r["zone"]]
    period = {0: "B", 1: "A", 2: "D" if not r.get("goodFuture") else "C"}[r["timeZone"] & 0x7F]
    return f"R{zone_digit}{r['act'] + 1}{period}"


def manifest(profile, fixtures, entries):
    return {"formatVersion": 1, "profile": profile, "serviceModel": SERVICE_MODEL,
            "fixtures": fixtures, "entries": entries}


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--native-root", default=os.path.expanduser("~/Downloads/Sonic-CD/native"))
    p.add_argument("--media-root", default=os.path.expanduser("~/Downloads/Sonic-CD/media"))
    p.add_argument("--out", default=str(REPO / "src/main/resources/load-time-profiles"))
    p.add_argument("--fast", action="store_true", help="also (re)seed soniccd-fast-v1.json as a copy")
    args = p.parse_args()
    fixtures, entries = build(os.path.abspath(args.native_root), Path(args.media_root))
    write(os.path.join(args.out, "soniccd-v1.json"), manifest("soniccd", fixtures, entries))
    if args.fast:
        single = [e["submissionFingerprint"] for e in entries if e["sampleCount"] == 1]
        note = ("header: soniccd-fast-v1.json is the hand-tunable copy of the generator-owned soniccd-v1.json; "
                "entries resting on a single recording: " + ", ".join(
                    "%s %s" % (e["kind"], fixtures[e["fixtureIndexes"][0]]) for e in entries
                    if e["submissionFingerprint"] in single))
        write(os.path.join(args.out, "soniccd-fast-v1.json"), manifest("soniccd-fast", fixtures + [note], entries))
    print(f"{len(entries)} entries from {len(fixtures)} recordings", file=sys.stderr)


if __name__ == "__main__":
    main()
