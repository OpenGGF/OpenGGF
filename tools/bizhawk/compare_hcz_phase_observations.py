#!/usr/bin/env python3
"""Compare saved HCZ phase observations without launching a core or supplying engine input.

Inputs: S3kHczPhaseProbe JSONL frames/events, its checked site manifest, and a
separate cold pass of the same movie/window. Origin: original-E HCZ investigation,
2026-11-10. Raw bytes and clocks stay literal; input encodings remain separate.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import sys


REGIONS = {
    "B000_004A": (0xB000, 0x4A),
    "EE00_0100": (0xEE00, 0x100),
    "F600_0080": (0xF600, 0x80),
    "FE00_0020": (0xFE00, 0x20),
    "wall_slot11_B32E_004A": (0xB32E, 0x4A),
}
PUBLICATION = {
    "camera_x": (0xEE78, 2), "camera_y": (0xEE7C, 2),
    "camera_x_copy": (0xEE80, 2), "camera_y_copy": (0xEE84, 2),
    "BG_x_copy": (0xEE8C, 2), "BG_y_copy_word": (0xEE90, 2),
    "BG_y_copy_long": (0xEE90, 4),
    "camera_x_diff": (0xEE3E, 2), "camera_y_diff": (0xEE40, 2),
    "wall_accumulator_u32": (0xEED2, 4), "BG_collision_flag": (0xF664, 1),
    "shake_offset_word": (0xEECE, 2), "shake_last_word": (0xEED0, 2),
}
COLLISIONS = {"find_floor_bg_diff_read", "find_wall_bg_y_diff_read"}
ACTORS = {0xFFB000: "P1", 0xFFB04A: "Tails"}
READ_FIELDS = {"find_floor_bg_diff_read": ("camera_x_diff", "camera_y_diff"),
               "find_wall_bg_y_diff_read": ("camera_y_diff",)}
PROFILE_MOVIE_SHA256 = "82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf"
PROFILE_SITES = {"process_sprites_entry", "hcz2_screen_entry", "hcz2_background_entry",
                 "hcz2_wall_move_entry", "shake_setup_entry", "bg_diff_published", *COLLISIONS}


def integer(value, name):
    if type(value) is not int or value < 0:
        raise ValueError(f"{name} must be a nonnegative integer")
    return value


def raw_regions(row):
    raw = row["raw_regions_hex"]
    if set(raw) != set(REGIONS):
        raise ValueError("All five declared raw regions are required")
    decoded = {}
    for key, (_, length) in REGIONS.items():
        value = raw[key]
        if not isinstance(value, str) or len(value) != 2 * length:
            raise ValueError(f"Wrong raw region length: {key}")
        decoded[key] = bytes.fromhex(value)
        if len(decoded[key]) != length:
            raise ValueError(f"Wrong decoded region length: {key}")
    return decoded


def word(regions, address, length=2):
    for key, (base, size) in REGIONS.items():
        if base <= address and address + length <= base + size:
            return int.from_bytes(regions[key][address - base:address - base + length], "big")
    raise ValueError(f"Address outside declared regions: {address:X}")


def publication(regions):
    return {name: word(regions, address, length)
            for name, (address, length) in PUBLICATION.items()}


def frame_index(rows):
    result = {}
    previous = None
    for sequence, row in enumerate(rows, 1):
        frame = integer(row["completed_frame"], "completed_frame")
        if (row["sequence"] != sequence or frame == 0
                or (previous is not None and frame != previous + 1)
                or row["bk2_input_index_0based"] != frame - 1):
            raise ValueError("Completed frame/input/sequence continuity is invalid")
        result[frame] = (row, raw_regions(row))
        previous = frame
    if len(result) < 2:
        raise ValueError("At least two completed samples are required")
    return result


def analyze(frames, events, sites, cold, *, first_frame, last_frame):
    integer(first_frame, "first_frame")
    integer(last_frame, "last_frame")
    if first_frame >= last_frame:
        raise ValueError("A declared window of at least two frames is required")
    current = frame_index(frames)
    reference = frame_index(cold)
    expected = list(range(first_frame, last_frame + 1))
    if list(current) != expected or list(reference) != expected:
        raise ValueError("Incomplete or different declared observation window")
    issues = []
    for frame in sorted(current.keys() & reference.keys()):
        row, raw = current[frame]
        old, old_raw = reference[frame]
        for key in ("completed_frame", "bk2_input_index_0based", "trace_row_decimal",
                    "is_lagged", "core_lag_count"):
            if row[key] != old[key]:
                issues.append({"kind": "cold_identity", "frame": frame, "field": key,
                               "cold": old[key], "observed": row[key]})
        # The cold observer's historical input_mask is the individual-button mask.
        # Never compare it to trace_logical_mask, which collapses ABC into Jump.
        if row["individual_button_mask"] != old["input_mask"]:
            issues.append({"kind": "cold_individual_buttons", "frame": frame,
                           "cold": old["input_mask"], "observed": row["individual_button_mask"]})
        for key in REGIONS:
            if raw[key] != old_raw[key]:
                issues.append({"kind": "cold_raw_bytes", "frame": frame, "region": key,
                               "cold": old["raw_regions_hex"][key],
                               "observed": row["raw_regions_hex"][key]})

    site_map = {site["name"]: site for site in sites["sites"]}
    if len(site_map) != len(sites["sites"]):
        raise ValueError("Duplicate observation site")
    bounds = defaultdict(dict)
    collision_order = []
    counts, actor_counts = Counter(), Counter()
    changed_fields = Counter({field: 0 for field in PUBLICATION})
    read_discrimination = {site: {field: 0 for field in fields}
                           for site, fields in READ_FIELDS.items()}
    read_changed_events = Counter({site: 0 for site in READ_FIELDS})
    collisions = decisive = total = field_comparisons = 0
    last_target = -1
    background, setups = [], []
    for total, event in enumerate(events, 1):
        target = integer(event["scheduled_completed_target"], "scheduled_completed_target")
        site = event["site"]
        if (event["sequence"] != total or target < last_target or target not in current
                or site not in site_map):
            raise ValueError("Event sequence, target or registered site is invalid")
        last_target = target
        if (event["host_completed_frame_raw"] != target - 1
                or event["advance_before_completed_frame"] != target - 1
                or event["inflight_input_index_0based"] != target - 1):
            raise ValueError("Callback clocks disagree; no offset fitting is permitted")
        pc = site_map[site]["pc"]
        if event["registered_trigger_pc"] != pc or event["observed_cpu_pc"] != pc:
            issues.append({"kind": "callback_PC", "sequence": total, "site": site,
                           "registered": event["registered_trigger_pc"],
                           "observed": event["observed_cpu_pc"], "manifest": pc})
        raw = raw_regions(event)
        counts[site] += 1
        if site in ("process_sprites_entry", "hcz2_screen_entry"):
            bounds[target].setdefault(site, []).append(total)
        if site == "hcz2_background_entry":
            background.append({"target": target, "sequence": total,
                               "stage": word(raw, 0xEEC2), "end_pulse": word(raw, 0xEEC6),
                               "redraw_rows": word(raw, 0xEECA)})
        if site == "shake_setup_entry":
            setups.append(target)
        if site not in COLLISIONS:
            continue
        actor = event["registers"]["A0"] & 0xFFFFFF
        if actor not in ACTORS:
            issues.append({"kind": "unsampled_actor", "sequence": total, "A0": actor})
            continue
        collisions += 1
        actor_counts[ACTORS[actor]] += 1
        collision_order.append((target, total))
        if target - 1 not in current:
            issues.append({"kind": "missing_previous_sample", "sequence": total})
            continue
        observed = publication(raw)
        previous = publication(current[target - 1][1])
        eventual = publication(current[target][1])
        changed = False
        read_changed = False
        for name in PUBLICATION:
            field_comparisons += 1
            if observed[name] != previous[name]:
                issues.append({"kind": "previous_publication", "sequence": total, "field": name,
                               "previous": previous[name], "observed": observed[name]})
            if previous[name] != eventual[name]:
                changed = True
                changed_fields[name] += 1
                if name in READ_FIELDS[site]:
                    read_discrimination[site][name] += 1
                    read_changed = True
        decisive += changed
        read_changed_events[site] += read_changed
    if not collisions:
        raise ValueError("No sampled P1/Tails BG collision read; empty evidence cannot qualify")
    for target, sequence in collision_order:
        group = bounds[target]
        if (not any(i < sequence for i in group.get("process_sprites_entry", []))
                or not any(i > sequence for i in group.get("hcz2_screen_entry", []))):
            issues.append({"kind": "collision_order", "target": target, "sequence": sequence})
    if not decisive:
        issues.append({"kind": "unchanged_only_control",
                       "message": "No collision read distinguishes prior from eventual publication"})

    transitions = []
    end = None
    previous_stage = None
    for frame, (_, raw) in current.items():
        stage = word(raw, 0xEEC2)
        if previous_stage == 4 and stage == 8 and word(raw, 0xFE11, 1) == 1 and end is None:
            end = frame
        if stage != previous_stage:
            transitions.append({"frame": frame, "stage": stage,
                                "zone": word(raw, 0xFE10, 1), "act": word(raw, 0xFE11, 1)})
        previous_stage = stage
    resume = next((target for target in setups if end is not None and target >= end), None)
    dispatches = [row for row in background
                  if end is not None and resume is not None and end <= row["target"] <= resume]
    site_reads = {
        site: {"events": counts[site], "events_with_any_read_field_changed": read_changed_events[site],
               "read_fields": {field: {"address": f"{PUBLICATION[field][0]:04X}",
                                        "changing_events": changed,
                                        "unchanging_events": counts[site] - changed}
                               for field, changed in fields.items()}}
        for site, fields in read_discrimination.items()
    }
    complete_read_control = all(counts[site] > 0 and all(n == counts[site] for n in fields.values())
                                for site, fields in read_discrimination.items())
    return {
        "status": ("differences" if issues else "values_match_timing_discriminated"
                   if complete_read_control else "values_match_timing_partial"),
        "counts": {"completed_samples": len(current), "first_frame": first_frame,
                   "last_frame": last_frame, "execute_events": total,
                   "collision_events": collisions, "publication_fields": len(PUBLICATION),
                   "field_comparisons": field_comparisons,
                   "decisive_collision_events": decisive, "site_hits": dict(counts),
                   "actor_counts": dict(actor_counts), "changed_fields": dict(changed_fields)},
        "differences": issues,
        "per_site_read_discrimination": site_reads,
        "all_read_field_timing_discriminated": complete_read_control,
        "completed_stage_transitions": transitions,
        "end_transition": {"end_target": end, "first_setup_target_at_or_after_end": resume,
                           "background_dispatches_through_resume": dispatches},
        "limits": ["Sampled P1/Tails readers and this original movie/window only.",
                   "Equality does not discriminate a field's timing when prior and eventual values agree.",
                   "The original window never changes camera_y_diff at either read site; Y timing is not discriminated.",
                   "Whole-publication changing-event counts include fields the instruction does not read.",
                   "End-transition entries are descriptive; they are not a separate expected-transition assertion.",
                   "Raw equality covers five regions; not absence of every observer effect.",
                   "No timed/death/Knuckles/all-object, engine parity or route certification.",
                   "Registered trigger and independently observed CPU PC are distinct values.",
                   "This analysis launches nothing and supplies no gameplay/timing authority."],
    }


def read_jsonl(path):
    with path.open() as stream:
        for line in stream:
            yield json.loads(line)


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def terminal_evidence(args):
    observed = json.loads(args.summary.read_text())
    cold = json.loads(args.cold_summary.read_text())
    expected_samples = args.last_frame - args.first_frame + 1
    for summary in (observed, cold):
        if summary["execution_status"] != "completed":
            raise ValueError("Producer did not complete")
        for key, expected in {"first_requested_completed_frame": args.first_frame,
                              "last_requested_completed_frame": args.last_frame,
                              "samples": expected_samples, "last_completed_frame": args.last_frame,
                              "inputs_applied": args.last_frame, "RAM_writes": 0,
                              "state_loads": 0}.items():
            if type(summary[key]) is not int or summary[key] != expected:
                raise ValueError(f"Producer summary disagrees: {key}")
        if summary["video_enabled"] is not False:
            raise ValueError("Expected video-disabled producer")
    for key, expected in {"execute_callbacks": 0, "initial_completed_frame": 0,
                          "expected_samples": expected_samples}.items():
        if type(cold[key]) is not int or cold[key] != expected:
            raise ValueError("Cold producer was not the declared callback-free power-on pass")
    if (observed["registration_before_completed_frame"] != args.first_frame - 1
            or observed["registered_callbacks_disposed"] is not True
            or observed["opcode_checks_before_registration"] is not True):
        raise ValueError("Callback registration/disposal or opcode qualification is incomplete")
    movie = observed.get("movie_sha256")
    if args.phase_provenance is not None:
        provenance_movie = json.loads(args.phase_provenance.read_text())["movie"]["sha256"]
        if movie is not None and movie != provenance_movie:
            raise ValueError("Summary/provenance movie mismatch")
        movie = provenance_movie
    if movie != PROFILE_MOVIE_SHA256 or cold["movie_sha256"] != movie:
        raise ValueError("Producer movie identities are absent or disagree with the fixed profile")
    hashes = [(observed["sites_sha256"], args.sites),
              (observed["completed-frames.jsonl_sha256"], args.frames),
              (observed["execute-events.jsonl_sha256"], args.events),
              (cold["observations_sha256"], args.cold_frames)]
    if any(expected != digest(path) for expected, path in hashes):
        raise ValueError("Producer summary file hash mismatch")
    if set(observed["hit_counts"]) != PROFILE_SITES or any(
            type(n) is not int or n <= 0 for n in observed["hit_counts"].values()):
        raise ValueError("Every declared profile site must have positive recorded hits")
    integer(observed["execute_event_count"], "execute_event_count")
    return observed


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("frames", "events", "sites", "cold-frames", "summary", "cold-summary", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--phase-provenance", type=Path,
                        help="Original run provenance required only for the older phase summary without movie_sha256")
    parser.add_argument("--first-frame", required=True, type=int)
    parser.add_argument("--last-frame", required=True, type=int)
    args = parser.parse_args(argv)
    if args.output.exists():
        parser.error("Output must not exist")
    try:
        summary = terminal_evidence(args)
        result = analyze(read_jsonl(args.frames), read_jsonl(args.events),
                         json.loads(args.sites.read_text()), read_jsonl(args.cold_frames),
                         first_frame=args.first_frame, last_frame=args.last_frame)
        if (result["counts"]["execute_events"] != summary["execute_event_count"]
                or result["counts"]["site_hits"] != summary["hit_counts"]):
            raise ValueError("Event log is incomplete or disagrees with terminal hit counts")
        result["input_sha256"] = {name: {"path": str(path.resolve()), "sha256": digest(path)}
                                  for name, path in vars(args).items()
                                  if isinstance(path, Path) and name != "output"}
        with args.output.open("x") as stream:
            json.dump(result, stream, indent=2)
            stream.write("\n")
    except (KeyError, ValueError, OSError, TypeError) as error:
        print(f"Invalid observation evidence: {error}", file=sys.stderr)
        return 2
    print(json.dumps({"status": result["status"], "counts": result["counts"],
                      "differences": len(result["differences"]),
                      "all_read_field_timing_discriminated": result["all_read_field_timing_discriminated"]}))
    return 1 if result["differences"] else 0


if __name__ == "__main__":
    sys.exit(main())
