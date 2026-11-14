#!/usr/bin/env python3
"""Generate the Sonic CD sub-CPU audio command table and the stage music table.

Purpose: the CD audio owner maps each sub-CPU command (SCMD_*) the stage code sends to the
action the CD system program performs: play a disc audio track once or on repeat, fade,
stop, pause or resume CD audio, or write a byte to the PCM driver's queue. The stage music
table maps (zone, time zone) to the command PlayLevelMusic sends. Nothing is transcribed:
command numbers come from the rs lists, actions from following each handler in SPX.asm.

Inputs: a scddisasm checkout at pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff
  src/_Include/System.inc       SCMD_* (rsset 1)
  src/_Include/Sound.inc        CDDA_* (rsset 2): the BIOS track number
  src/CD System Program/SPX.asm .SPCmds jump table, handlers, MusID_* words
  src/Sound Drivers/PCM/_Sound IDs.inc  PCM queue ids
  src/Level/Main.asm            MusicPlayCmds
  src/CD System Program/SPX.asm .PCMDrivers (the bank SPCmd_LoadLevel loads per stage command)
Outputs: src/main/resources/soniccd/subcpu-audio-commands.tsv, stage-music.tsv,
stage-pcm-banks.tsv (or --check).
Origin: lane AUD1 of the 2026-11-12 Sonic CD re-host.

    python3 -I tools/soniccd/subcpu_audio_catalogue.py <scddisasm> [--check]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMANDS = ROOT / "src/main/resources/soniccd/subcpu-audio-commands.tsv"
STAGE_MUSIC = ROOT / "src/main/resources/soniccd/stage-music.tsv"
STAGE_BANKS = ROOT / "src/main/resources/soniccd/stage-pcm-banks.tsv"


def stage_banks(spx: str, scmd: dict[str, int]) -> str:
    """.PCMDrivers is indexed by the load command, like .LevelFiles (SPCmd_LoadLevel)."""
    lines = spx.splitlines()
    start = next(i for i, l in enumerate(lines) if l.startswith(".PCMDrivers:"))
    labels = []
    for line in lines[start + 1:]:
        m = re.match(r"\s*dc\.l\s+(File_\w+)", line)
        if not m:
            if labels and line.strip() and not line.strip().startswith(";"):
                break
            continue
        labels.append(m.group(1))
    files = {}
    for k, line in enumerate(lines):
        m = re.match(r"(File_\w+):", line)
        if m:
            name = re.match(r'\s*dc\.b\s+"([^"]+)"', lines[k + 1])
            if name:
                files[m.group(1)] = name.group(1)
    by_id = {v: k for k, v in scmd.items()}
    rows = ["# payload\tload_command\tpcm_bank"]
    for index, label in enumerate(labels):
        name = by_id.get(index, "")
        m = re.fullmatch(r"SCMD_(R\d\d[A-D])", name)
        if m:
            rows.append(f"{m.group(1)}\t{index:02X}\t{files[label]}")
    return "\n".join(rows) + "\n"


def rs_values(text: str, prefix: str) -> dict[str, int]:
    """Evaluates rsset/rs.b runs for names starting with prefix."""
    values: dict[str, int] = {}
    counter = None
    for line in text.splitlines():
        code = line.split(";", 1)[0]
        m = re.match(r"\s*rsset\s+\$?([0-9A-Fa-f]+)", code)
        if m:
            counter = int(m.group(1), 16) if "$" in code else int(m.group(1))
            continue
        m = re.match(r"(\w+)?\s+rs\.b\s+(\$[0-9A-Fa-f]+|\d+)\s*$", code)
        if m and counter is not None:
            literal = m.group(2)
            name, size = m.group(1), int(literal[1:], 16) if literal.startswith("$") else int(literal)
            if name and name.startswith(prefix):
                values[name] = counter
            counter += size
    return values


def spx_table(spx: str) -> list[str]:
    lines = spx.splitlines()
    start = next(i for i, l in enumerate(lines) if l.startswith(".SPCmds:"))
    handlers = []
    for line in lines[start + 1:]:
        m = re.match(r"\s*dc\.w\s+(\S+)", line)
        if not m:
            if handlers and line.strip() and not line.strip().startswith(";"):
                break
            continue
        handlers.append(m.group(1).split("-")[0])
    return handlers


def handler_action(spx: str, label: str, mus: dict[str, int], pcm: dict[str, int]) -> tuple[str, str]:
    """Follows a handler (including fall-through) to its CD audio or PCM action."""
    lines = spx.splitlines()
    i = next((k for k, l in enumerate(lines) if l.startswith(label + ":")), None)
    if i is None:
        return "none", ""
    track = ""
    for line in lines[i + 1:]:
        code = line.split(";", 1)[0].strip()
        if not code:
            continue
        if code.startswith("LoopCDDA:"):
            return "cdda_repeat", track
        if code.startswith("PlayCDDA:"):
            return "cdda_once", track
        m = re.match(r"lea\s+(MusID_\w+)\(pc\),a0", code)
        if m:
            track = str(mus[m.group(1)])
            continue
        if re.match(r"bra(\.[sw])?\s+LoopCDDA", code):
            return "cdda_repeat", track
        if re.match(r"bra(\.[sw])?\s+PlayCDDA", code):
            return "cdda_once", track
        m = re.match(r"move\.b\s+#(\$?\w+),PCMDrvQueue", code)
        if m:
            v = m.group(1)
            return "pcm_queue", "%02X" % (int(v[1:], 16) if v.startswith("$") else pcm[v])
        if "#FDRCHG" in code:
            return "cdda_fade", ""
        if "#MSCSTOP" in code:
            return "cdda_stop", ""
        if "#MSCPAUSEON" in code:
            return "cdda_pause", ""
        if "#MSCPAUSEOFF" in code:
            return "cdda_unpause", ""
        if re.match(r"bsr(\.[sw])?\s+ResetCDDAVol", code) and "SPCmd_ResetCDDAVol" == label:
            return "cdda_reset_volume", ""
        if re.match(r"(bra|jmp|rts)", code) or re.match(r"^[A-Za-z_]\w*:", code):
            return "none", ""
    return "none", ""


def build(root: Path) -> tuple[str, str, str]:
    inc = (root / "src/_Include/System.inc").read_text()
    snd = (root / "src/_Include/Sound.inc").read_text()
    spx = (root / "src/CD System Program/SPX.asm").read_text()
    pcm_ids = (root / "src/Sound Drivers/PCM/_Sound IDs.inc").read_text()
    level = (root / "src/Level/Main.asm").read_text()
    scmd = rs_values(inc, "SCMD_")
    cdda = rs_values(snd, "CDDA_")
    pcm = {**rs_values(pcm_ids, "PCMM_"), **rs_values(pcm_ids, "PCMS_"), **rs_values(pcm_ids, "PCMC_")}
    mus = {}
    lines = spx.splitlines()
    for k, line in enumerate(lines):
        m = re.match(r"(MusID_\w+):", line)
        if m:
            word = re.match(r"\s*dc\.w\s+(CDDA_\w+)", lines[k + 1])
            mus[m.group(1)] = cdda[word.group(1)]
    table = spx_table(spx)
    by_id = {v: k for k, v in scmd.items()}
    rows = ["# command\tname\thandler\taction\targument"]
    for index, handler in enumerate(table):
        if index == 0 or handler == "0" or index not in by_id:
            continue
        action, arg = handler_action(spx, handler, mus, pcm)
        if action != "none":
            rows.append(f"{index:02X}\t{by_id[index]}\t{handler}\t{action}\t{arg}")
    body = level[level.index("MusicPlayCmds:"):]
    stage = ["# zone\ttime_zone\tcommand\tname"]
    zone = 0
    for line in body.splitlines()[1:]:
        m = re.match(r"\s*dc\.b\s+(.*)", line.split(";", 1)[0])
        if not m:
            if line.strip() and not line.strip().startswith(";"):
                break
            continue
        for tz, name in enumerate(n.strip() for n in m.group(1).split(",")):
            stage.append(f"{zone}\t{tz}\t{scmd[name]:02X}\t{name}")
        zone += 1
    return "\n".join(rows) + "\n", "\n".join(stage) + "\n", stage_banks(spx, scmd)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    commands, stage, banks = build(Path(sys.argv[1]))
    if "--check" in sys.argv:
        ok = (COMMANDS.read_text() == commands and STAGE_MUSIC.read_text() == stage
              and STAGE_BANKS.read_text() == banks)
        print("up to date" if ok else "STALE")
        return 0 if ok else 1
    COMMANDS.write_text(commands)
    STAGE_MUSIC.write_text(stage)
    STAGE_BANKS.write_text(banks)
    print(f"wrote {COMMANDS.name} ({commands.count(chr(10)) - 1} rows), {STAGE_MUSIC.name} ({stage.count(chr(10)) - 1} rows), "
          f"{STAGE_BANKS.name} ({banks.count(chr(10)) - 1} rows)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
