#!/usr/bin/env python3
"""Build the checked S1 FM5 first-attack source programs.

This tool never derives a cycle from a desired output gap.  Its only timing
inputs are the authenticated native instruction ledger and the post-update YM
write events joined to that ledger.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESEARCH = ROOT / "docs/architecture/research/audio"
AUDIT = RESEARCH / "s1-ring-ym-write-audit-v2.json"
PAN_LEDGER = RESEARCH / "s1-ring-ym-write-instruction-ledger-v1.tsv"
NO_PAN_LEDGER = RESEARCH / "s1-ring-no-pan-ym-write-instruction-ledger-v1.tsv"
LEDGER_BUILDER = (ROOT / "tools/bizhawk-headless/native/gpgx-audio-lab"
                  / "build-representative-ledger.sh")
LEDGER_HEADER = (
    "occurrence_ordinal\tframe\tafter_source_ordinal\tcpu\tpc\topcode\t"
    "start_master_cycle\tnext_pc\tdelta_to_next_start\tflow\tbranch_outcome\t"
    "roles\tsource"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def qualifying_no_pan_group(oracle: dict) -> dict:
    if oracle.get("schema") != "openggf.s1-s2-ym-write-timing-audit.v2":
        raise ValueError("unexpected oracle schema")
    if oracle.get("game") != "s1":
        raise ValueError("no-pan extraction requires the S1 oracle")
    source = oracle.get("source_authentication", {})
    if source.get("sound_id") != "0xB5" or source.get("fm_channel") != 4:
        raise ValueError("oracle does not authenticate the S1 FM5 Ring owner")
    candidates = []
    for group in oracle.get("groups", []):
        writes = group.get("writes", [])
        terminal = writes[-1] if writes else {}
        b4_writes = [write for write in writes
                     if write.get("port") == 1 and write.get("register") == 0xB1]
        if (group.get("classification") == "isolated"
                and len(writes) == 30
                and len(b4_writes) == 1
                and terminal.get("port") == 0
                and terminal.get("register") == 0x28
                and terminal.get("value") == 0xF5
                and all(write.get("dma_stall_count") == 0 for write in writes)):
            candidates.append(group)
    if not candidates:
        raise ValueError("no isolated 30-write S1 FM5 group qualifies")
    selected = min(candidates, key=lambda group: group["group_ordinal"])
    if selected["group_ordinal"] != 1:
        raise ValueError("lowest qualifying no-pan authority is no longer group 1")
    return selected


def extract_no_pan_ledger(oracle_path: Path, instructions_path: Path,
                          output_path: Path) -> None:
    if output_path.exists():
        raise ValueError(f"output already exists: {output_path}")
    oracle_bytes = oracle_path.read_bytes()
    instruction_bytes = instructions_path.read_bytes()
    oracle = json.loads(oracle_bytes)
    selected = qualifying_no_pan_group(oracle)
    group_ordinal = selected["group_ordinal"]

    with instructions_path.open("r", encoding="utf-8", newline="") as stream:
        reader = csv.reader(stream, delimiter="\t")
        header = next(reader)
        if header != ["frame", "group_ordinal", "after_source_ordinal", "cpu",
                      "pc", "opcode", "cycles"]:
            raise ValueError("native instruction header differs")
        selected_rows = [row for row in reader if int(row[1]) == group_ordinal]
    if not selected_rows:
        raise ValueError("selected group has no decoded instructions")
    after_ordinals = {int(row[2]) for row in selected_rows}
    if after_ordinals != set(range(-1, 29)):
        raise ValueError("selected instruction stream does not densely cover every write gap")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="s1-no-pan-ledger-",
                                     dir=output_path.parent) as temporary:
        temporary_path = Path(temporary)
        filtered = temporary_path / "selected.tsv"
        built = temporary_path / "ledger.tsv"
        with filtered.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
            writer.writerow(header)
            for row in selected_rows:
                row[1] = "0"  # the checked ledger builder selects its representative as 0
                writer.writerow(row)
        subprocess.run(["bash", str(LEDGER_BUILDER), "s1", str(filtered), str(built)],
                       cwd=ROOT, check=True)
        lines = built.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != LEDGER_HEADER:
        raise ValueError("checked ledger builder returned a different schema")
    comments = [
        f"# oracle_sha256={sha256(oracle_bytes)}",
        f"# native_instructions_sha256={sha256(instruction_bytes)}",
        f"# group_ordinal={group_ordinal}",
        "# selection=lowest authenticated isolated 30-write S1 FM5 group with one B4/pan-field write, terminal key-on, and zero DMA stalls",
    ]
    output_path.write_text("\n".join([lines[0], *comments, *lines[1:]]) + "\n",
                           encoding="utf-8")


def read_ledger(path: Path) -> tuple[list[dict], dict[str, str]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != LEDGER_HEADER:
        raise ValueError(f"ledger header differs: {path}")
    metadata: dict[str, str] = {}
    rows = []
    for line in lines[1:]:
        if line.startswith("# "):
            key, value = line[2:].split("=", 1)
            metadata[key] = value
            continue
        if not line:
            continue
        values = line.split("\t")
        if len(values) != 13:
            raise ValueError(f"ledger row width differs: {path}")
        rows.append(dict(zip(LEDGER_HEADER.split("\t"), values, strict=True)))
    for ordinal, row in enumerate(rows):
        if int(row["occurrence_ordinal"]) != ordinal:
            raise ValueError("ledger occurrence ordinals are not dense")
    return rows, metadata


def unique_sources(rows: list[dict]) -> str:
    values = []
    for row in rows:
        source = row["source"]
        if source not in values:
            values.append(source)
    if not values:
        raise ValueError("program write has no source citation")
    return "; ".join(values)


def build_program(shape: str, group: dict, ledger_path: Path,
                  selection: str) -> dict:
    rows, metadata = read_ledger(ledger_path)
    writes = group["writes"]
    pan_count = 1 if shape == "VOICE_PAN_NOTE" else 0
    expected_writes = 30 + pan_count
    if len(writes) != expected_writes:
        raise ValueError(f"{shape} write count differs")
    expected_after = set(range(-1, expected_writes - 1))
    actual_after = {int(row["after_source_ordinal"]) for row in rows}
    if actual_after != expected_after:
        raise ValueError(f"{shape} ledger does not cover every source interval")

    result_writes = []
    consumed = set()
    for ordinal, write in enumerate(writes):
        after = ordinal - 1
        source_rows = [row for row in rows if int(row["after_source_ordinal"]) == after]
        occurrences = [int(row["occurrence_ordinal"]) for row in source_rows]
        if any(occurrence in consumed for occurrence in occurrences):
            raise ValueError("instruction occurrence consumed twice")
        consumed.update(occurrences)
        if not source_rows:
            raise ValueError("write has no authenticated source occurrences")
        busy_rows = [row for row in source_rows if "busy_poll" in row["roles"]]
        first_busy = int(busy_rows[0]["start_master_cycle"]) if busy_rows else None
        previous_cycle = writes[ordinal - 1]["master_cycle"] if ordinal else None
        fixed_before_busy = 0 if ordinal == 0 or first_busy is None else first_busy - previous_cycle
        status_read_cycles = (int(busy_rows[0]["delta_to_next_start"])
                              if busy_rows and busy_rows[0]["delta_to_next_start"] != "key_on"
                              else 0)
        status_starts = [int(row["start_master_cycle"]) for row in busy_rows
                         if row["pc"] in ("0x7272E", "0x72764", "0x7277C")]
        loop_cycles = status_starts[1] - status_starts[0] if len(status_starts) > 1 else 0
        ready_start = status_starts[-1] if status_starts else None
        after_ready = 0 if ready_start is None else write["master_cycle"] - ready_start
        if min(fixed_before_busy, status_read_cycles, loop_cycles, after_ready) < 0:
            raise ValueError("negative checked source cost")
        if ordinal < 26:
            section = "FM_VOICE_UPLOAD"
        elif pan_count and ordinal == 26:
            section = "TRACK_PAN_WRITE"
        elif ordinal == 26 + pan_count:
            section = "KEY_OFF"
        else:
            section = "FREQUENCY_AND_KEY_ON"
        result_writes.append({
            "write_ordinal": ordinal,
            "section": section,
            "port": write["port"],
            "register": write["register"],
            "row_zero_anchor": ordinal == 0,
            "fixed_cycles_before_first_status_read": fixed_before_busy,
            "status_read_cycles": status_read_cycles,
            "taken_busy_loop_cycles": loop_cycles,
            "cycles_after_ready_status_to_data_write": after_ready,
            "captured_relative_master_cycle": write["relative_master_cycle"],
            "source_occurrence_first": occurrences[0],
            "source_occurrence_last": occurrences[-1],
            "source_occurrences": occurrences,
            "source": unique_sources(source_rows),
        })
    if consumed != set(range(len(rows))):
        raise ValueError("ledger contains unconsumed instruction occurrences")
    authority = {
        "ledger_path": ledger_path.name,
        "ledger_sha256": sha256(ledger_path.read_bytes()),
        "ledger_row_count": len(rows),
        "group_ordinal": group["group_ordinal"],
        "selection": selection,
        "native_instruction_capture_sha256": (
            metadata.get("native_instructions_sha256")
            or load_json(AUDIT)["provenance"]["native_instructions_sha256"]),
    }
    return {
        "shape": shape,
        "sections": {
            "FM_VOICE_UPLOAD": 26,
            "TRACK_PAN_WRITE": pan_count,
            "KEY_OFF": 1,
            "FREQUENCY_AND_KEY_ON": 3,
        },
        "authority": authority,
        "writes": result_writes,
    }


def build_document() -> dict:
    audit = load_json(AUDIT)
    groups = {group["group_ordinal"]: group for group in audit["groups"]}
    if 0 not in groups or qualifying_no_pan_group(audit)["group_ordinal"] != 1:
        raise ValueError("retained S1 authority groups differ")
    return {
        "schema": "openggf.s1-ym-busy-program.v1",
        "clock": {
            "master_cycles_per_m68k_cycle": 7,
            "master_cycles_per_ym_sample": 1_008,
            "busy_ym_cycles_after_data_write": 47,
            "busy_ym_cycles_per_sample": 24,
        },
        "programs": [
            build_program(
                "VOICE_NOTE", groups[1], NO_PAN_LEDGER,
                "lowest authenticated isolated 30-write S1 FM5 group"),
            build_program(
                "VOICE_PAN_NOTE", groups[0], PAN_LEDGER,
                "retained representative authenticated isolated 31-write S1 FM5 group"),
        ],
    }


def write_markdown(path: Path, document: dict) -> None:
    lines = [
        "# Sonic 1 FM5 YM busy-write source program",
        "",
        "This checked artifact models the relative timing between hardware data writes for",
        "the source-authenticated first FM5 voice attack. It does not model service-entry",
        "time or use a sound/zone/movie runtime carve-out.",
        "",
        "The calculation uses seven master cycles per 68000 cycle, 1,008 master cycles",
        "per internal YM sample, a 47-cycle busy interval after a data write, and a",
        "24-cycle decrement at each internal sample. Only row zero is normalized to zero;",
        "busy state continues through every later section.",
        "",
        "| Shape | Writes | Ledger SHA-256 |",
        "|---|---:|---|",
    ]
    for program in document["programs"]:
        lines.append(f"| `{program['shape']}` | {len(program['writes'])} | "
                     f"`{program['authority']['ledger_sha256']}` |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--extract-no-pan-ledger", action="store_true")
    parser.add_argument("--oracle", type=Path)
    parser.add_argument("--instructions", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path)
    args = parser.parse_args()
    if args.extract_no_pan_ledger:
        if args.oracle is None or args.instructions is None:
            parser.error("extraction requires --oracle and --instructions")
        extract_no_pan_ledger(args.oracle, args.instructions, args.output)
        return
    if args.oracle is not None or args.instructions is not None:
        parser.error("--oracle/--instructions are valid only for extraction")
    document = build_document()
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n",
                           encoding="utf-8")
    if args.markdown_output is not None:
        write_markdown(args.markdown_output, document)


if __name__ == "__main__":
    main()
