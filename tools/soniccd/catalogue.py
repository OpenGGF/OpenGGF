#!/usr/bin/env python3
"""Audit Sonic CD native catalogue metadata for the 2026-10-23 CD-01 task.

Inputs are an optional scddisasm checkout and committed Java catalogue sources.
This research tool checks source correspondence, not retail media or gameplay.
Java checks cover literal tables and the current disposition branch shapes.
They do not interpret Java helpers. JUnit remains the executed behavior authority.
Runtime code never reads disassembly. --output must be outside the repository.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/openggf/game/soniccd"
STAGE = re.compile(r"R[1345678][12][ABCD]|R[1345678]3[CD]")


def declarations(text: str, prefix: str, start: int) -> list[dict]:
    result = []
    value = start
    for line in text.splitlines():
        match = re.fullmatch(r"(?:(\w+)\s+)?\s*rs\.b\s+(\d+)\s*", line.split(";", 1)[0])
        if match:
            symbol = match[1] or f"{prefix}INVALID_{value}"
            if not symbol.startswith(prefix) or int(match[2]) != 1:
                raise ValueError(f"Unsupported {prefix} slot declaration: {line}")
            result.append({"symbol": symbol.removeprefix(prefix), "id": value})
            value += 1
    return result


def selected_lines(text: str, variable: str, value: str) -> str:
    """Select the simple assembly-time branches used by headers and ending streams."""
    result = []
    active = True
    matched = False
    in_branch = False
    for raw in text.splitlines():
        line = raw.split(";", 1)[0].strip()
        condition = re.fullmatch(r"(if|elseif)\s+(\w+)=(\w+)", line)
        if condition:
            if condition[2] != variable or (condition[1] == "if" and in_branch):
                raise ValueError(f"Unsupported conditional: {line}")
            if condition[1] == "if":
                matched = False
                in_branch = True
            elif not in_branch:
                raise ValueError("elseif without if")
            active = not matched and condition[3] == value
            matched |= active
        elif line == "else":
            if not in_branch:
                raise ValueError("else without if")
            active = not matched
            matched = True
        elif line == "endif":
            if not in_branch:
                raise ValueError("endif without if")
            active = True
            in_branch = False
        elif re.match(r"(?:if|elseif)\s", line):
            raise ValueError(f"Unsupported conditional: {line}")
        elif active:
            result.append(raw)
    if in_branch:
        raise ValueError("Unterminated assembly conditional")
    return "\n".join(result)


def releases(source: Path) -> list[dict]:
    common = (source / "src/_Include/Common.inc").read_text()
    regions = re.findall(r"^(JAPAN|USA|EUROPE)\s+EQU\s+(\d+)", common, re.M)
    header = (source / "src/Main.asm").read_text().split("; Initial program", 1)[0]
    result = []
    for name, number in regions:
        strings = re.findall(r'dc\.b\s+"([^"]+)"', selected_lines(header, "REGION", name))
        result.append({
            "name": name, "source_region_value": int(number),
            "disc_header_volume_id": strings[1].strip(),
            "source_build_date": next(x for x in strings if re.fullmatch(r"\d{8}", x)),
            "product_id": next(x.rstrip() for x in strings if x.startswith("GM ")),
            "header_market": strings[-1].strip(),
        })
    return result


def inventory(source: Path) -> dict:
    system = (source / "src/_Include/System.inc").read_text()
    spx = (source / "src/CD System Program/SPX.asm").read_text()
    build = (source / "make.sh").read_text()
    files = declarations(system.split("; Sub CPU commands")[0], "FID_", 0)
    commands = declarations(system.split("; Sub CPU commands")[1].split("; Addresses")[0], "SCMD_", 1)
    file_rows = re.findall(r'(File_\w+):\s*\n\s*dc\.b\s*"([^"]+)"', spx)
    if len(file_rows) != len(files):
        raise ValueError("File declaration and filename table lengths differ")
    by_label = {}
    for entry, (label, name) in zip(files, file_rows, strict=True):
        entry.update(label=label, filename=name.rstrip())
        by_label[label] = entry["symbol"]
    dispatch = spx.split(".SPCmds:\n", 1)[1].split(".SPCmdsEnd:", 1)[0]
    handlers = re.findall(r"^\s*dc\.w\s+(\w+)-\.SPCmds", dispatch, re.M)
    if len(handlers) != len(commands):
        raise ValueError("Command declaration and dispatch lengths differ")
    levels = re.findall(r"dc\.w\s+(File_\w+)-\.LevelFiles", spx.split(".LevelFiles:\n", 1)[1].split(".PCMDrivers:", 1)[0])
    pcm = re.findall(r"dc\.l\s+(File_\w+)", spx.split(".PCMDrivers:\n", 1)[1].split("SPCmd_LoadMDInit:", 1)[0])
    for entry, handler in zip(commands, handlers, strict=True):
        entry.update(handler=handler, load_files=[], stream_files=[])
        if handler == "SPCmd_LoadLevel":
            entry["load_files"] = [by_label[levels[entry["id"]]], by_label[pcm[entry["id"]]]]
            if entry["symbol"].startswith("INVALID_"):
                entry["disposition"] = "INVALID"
            elif STAGE.fullmatch(entry["symbol"]):
                entry["disposition"] = "STAGE_LOAD"
            else:
                entry["disposition"] = "DEMO_LOAD"
        elif handler.startswith("SPCmd_Load"):
            body = spx.split(handler + ":", 1)[1].split("\nSPCmd_", 1)[0]
            body = "\n".join(line.split(";", 1)[0] for line in body.splitlines())
            entry["load_files"] = [by_label[label] for label in re.findall(r"lea\s+(File_\w+)\(pc\),a0", body)]
            if not entry["load_files"]:
                raise ValueError(f"Loader without understood file references: {handler}")
            entry["disposition"] = "UNUSED_PAYLOAD_ALIAS" if re.fullmatch(r"SPCmd_LoadDummyFile\d+", handler) else "PROGRAM_LOAD"
        elif handler.startswith(("SPCmd_Read", "SPCmd_Write")):
            entry["disposition"] = "BACKUP_RAM"
        elif handler.startswith("SPCmd_ResetSSFlags"):
            entry["disposition"] = "SPECIAL_STAGE_FLAGS"
        elif handler.startswith(("SPCmd_Play", "SPCmd_Test", "SPCmd_Fade", "SPCmd_Stop", "SPCmd_Pause", "SPCmd_Unpause", "SPCmd_ResetCDDA")):
            entry["disposition"] = "AUDIO"
            body = re.split(r"\n\w+:", spx.split(handler + ":", 1)[1], maxsplit=1)[0]
            body = "\n".join(line.split(";", 1)[0] for line in body.splitlines())
            targets = re.findall(r"lea\s+(File_\w+)\(pc\),a0\s+bsr\.w\s+LoadPCMDriver\b", body)
            equal(re.findall(r"\bFile_\w+\b", body), targets, f"{handler} understood audio file targets")
            equal(len(re.findall(r"\bLoadPCMDriver\b", body)), len(targets), f"{handler} PCM load calls")
            entry["load_files"] = [by_label[label] for label in targets]
        else:
            raise ValueError(f"Unclassified native handler: {handler}")

    outputs = {name: (path.replace("\\", "/"), flags) for path, name, flags in re.findall(
        r"^assemble(?:_symbols)?\s+'([^']+)'\s+'\.\.\\out\\files\\([^']+)'([^\n]*)", build, re.M)}
    copied = re.search(r"for copy in ([^;]+); do\s+cp \"\$ROOT_DIR/out/files/([^\"]+)\"", build)
    if not copied:
        raise ValueError("Missing explicit prototype copy rule")
    copies = {name: copied[2] for name in copied[1].split()}
    prototypes = {name for name, (_, flags) in outputs.items() if "PROTOTYPE=1" in flags} | copies.keys()
    for entry in files:
        name = entry["filename"].removesuffix(";1")
        origin, _ = outputs.get(copies.get(name, name), ("", ""))
        if STAGE.fullmatch(entry["symbol"]):
            kind = "STAGE_PROGRAM"
        elif name.endswith(".STM"):
            kind = "VIDEO_STREAM"
        elif name in prototypes:
            kind = "UNUSED_COPY"
        elif name.endswith(".MMD"):
            kind = "MAIN_CPU_PROGRAM"
        elif origin.startswith("Sound Drivers/PCM/"):
            kind = "PCM_DRIVER"
        elif origin in ("DA Garden/Track Titles.asm", "Thank You/Data.asm"):
            kind = "DATA"
        elif origin:
            kind = "SUB_CPU_PROGRAM"
        else:
            raise ValueError(f"Unclassified source output: {name}")
        entry["kind"] = kind
        if name in copies:
            entry["copy_source"] = next(x["symbol"] for x in files if x["filename"] == copies[name] + ";1")

    file_by_symbol = {x["symbol"]: x for x in files}
    for entry in commands:
        if entry["disposition"] == "UNUSED_PAYLOAD_ALIAS":
            candidates = [x for x in commands if x["load_files"] == entry["load_files"] and x["disposition"] == "PROGRAM_LOAD"]
            if len(candidates) != 1:
                raise ValueError(f"Ambiguous payload alias: {entry['symbol']}")
            entry["payload_alias_of"] = candidates[0]["symbol"]
        for symbol in entry["load_files"]:
            file = file_by_symbol[symbol]
            if file["kind"] != "SUB_CPU_PROGRAM":
                continue
            origin, flags = outputs[file["filename"].removesuffix(";1")]
            sub = (source / "src" / origin).read_text()
            parameter = re.search(r"DATAFILE=(\d+)", flags)
            if parameter:
                sub = selected_lines(sub.split(".Loop:", 1)[0], "DATAFILE", parameter[1])
            entry["stream_files"].extend(re.findall(r"move\.w\s+#FID_(\w+STM),d0", sub))
    for entry in files:
        symbol = entry["symbol"]
        if entry["kind"] == "UNUSED_COPY":
            disposition = "UNUSED_PROTOTYPE_PROGRAM"
        elif any(symbol in x["stream_files"] for x in commands):
            disposition = "STREAMED_BY_PROGRAM"
        elif any(symbol in x["load_files"] for x in commands if x["disposition"] != "INVALID"):
            disposition = "SPX_LOAD_COMPONENT"
        else:
            disposition = "NO_SPX_LOAD_BINDING"
        entry["disposition"] = disposition
    return {"files": files, "commands": commands, "releases": releases(source)}


def switch_cases(text: str, expression: str) -> dict[str, str]:
    result = {}
    for names, value in re.findall(r"case\s+([A-Z0-9_,\s]+)\s*->\s*" + expression, text):
        for name in names.replace("\n", "").split(","):
            name = name.strip()
            if name in result:
                raise ValueError(f"Duplicate switch case: {name}")
            result[name] = value
    return result


def equal(actual, expected, description: str) -> None:
    if actual != expected:
        raise ValueError(f"{description} differs from source: {actual!r} != {expected!r}")


def check(data: dict, java_sources: dict[str, str] | None = None) -> None:
    texts = java_sources if java_sources is not None else {p.stem: p.read_text() for p in JAVA.glob("*.java")}
    file_rows = re.findall(r'^    (\w+)\((\d+), "([^"]+)", Kind\.(\w+)\)', texts["CdNativeFile"], re.M)
    equal([(s, int(i), n, k) for s, i, n, k in file_rows],
          [(x["symbol"], x["id"], x["filename"], x["kind"]) for x in data["files"]], "Native file table")
    command_rows = re.findall(r'^    (\w+)\((\d+), "([^"]+)"', texts["CdCommand"], re.M)
    equal([(s, int(i), h) for s, i, h in command_rows],
          [(x["symbol"], x["id"], x["handler"]) for x in data["commands"]], "Native command table")
    stages = [x for x in data["commands"] if x["disposition"] == "STAGE_LOAD"]
    equal(len(stages), 70, "Stage count")
    equal(re.findall(r'^    (R\d\d[A-D])\(CdNativeFile\.(\w+)\)', texts["CdStagePayload"], re.M),
          [(x["symbol"], x["load_files"][1]) for x in stages], "Stage PCM bindings")
    for entry in stages:
        equal(entry["load_files"][0], entry["symbol"], "Stage program identity")
    programs = [x for x in data["commands"] if x["disposition"] in ("PROGRAM_LOAD", "DEMO_LOAD", "UNUSED_PAYLOAD_ALIAS")]
    program_text = texts["CdNativeProgram"]
    actual_programs = [(name, command, re.findall(r"CdNativeFile\.(\w+)", components)) for name, command, components in re.findall(
        r'^    (\w+)\(CdCommand\.(\w+), ([^\n]+)\)[,;]', program_text, re.M)]
    equal(actual_programs, [(x["symbol"], x["symbol"], x["load_files"]) for x in programs], "Nonstage load bindings")
    equal(switch_cases(program_text, r"List\.of\(CdNativeFile\.(\w+)\)"),
          {x["symbol"]: x["stream_files"][0] for x in programs if x["stream_files"]}, "Program streams")
    equal(switch_cases(program_text, r"Optional\.of\((\w+)\)"),
          {x["symbol"]: x["payload_alias_of"] for x in programs if "payload_alias_of" in x}, "Payload aliases")
    equal(switch_cases(texts["CdNativeFile"], r"Optional\.of\((\w+)\)"),
          {x["symbol"]: x["copy_source"] for x in data["files"] if "copy_source" in x}, "Built file copies")
    catalogue = texts["CdProgramCatalogue"]
    sound_test_map = re.search(r"SOUND_TEST_PCM_DRIVERS = Map\.ofEntries\((.*?)\n    \);", catalogue, re.S)
    if not sound_test_map:
        raise ValueError("Missing sound-test PCM dependency map")
    equal(re.findall(r"Map\.entry\(CdCommand\.(\w+), CdNativeFile\.(\w+)\)", sound_test_map[1]),
          [(x["symbol"], x["load_files"][0]) for x in data["commands"]
           if x["disposition"] == "AUDIO" and x["load_files"]], "Sound-test PCM dependencies")
    if not re.search(r"var pcmDriver = SOUND_TEST_PCM_DRIVERS\.get\(command\);\s*"
                     r"if \(pcmDriver != null\) \{\s*return List\.of\(pcmDriver\);\s*\}", catalogue):
        raise ValueError("Missing sound-test PCM dependency return branch")
    invalid = next(x for x in data["commands"] if x["disposition"] == "INVALID")
    fallback = re.search(r"if \(command == CdCommand\.INVALID_39\) \{\s*return List\.of\(([^)]+)\)", catalogue)
    if not fallback:
        raise ValueError("Missing invalid-slot native fallback")
    equal(re.findall(r"CdNativeFile\.(\w+)", fallback[1]), invalid["load_files"], "Invalid-slot native fallback")
    # Guard the known branch structure rather than infer Java behavior from the source inventory.
    dispositions = re.search(
        r"public CommandDisposition disposition\(CdCommand command\) \{\s*"
        r'Objects.requireNonNull\(command, "command"\);\s*'
        r"if \(stageForCommand\(command\).isPresent\(\)\) \{\s*"
        r"return CommandDisposition\.(?P<stage>\w+);\s*\}\s*"
        r"var program = programForCommand\(command\);\s*"
        r"if \(program.isPresent\(\)\) \{\s*"
        r"if \(program.get\(\).payloadAliasOf\(\).isPresent\(\)\) \{\s*"
        r"return CommandDisposition\.(?P<alias>\w+);\s*\}\s*"
        r"return switch \(command\) \{(?P<program_cases>.*?)"
        r"default -> CommandDisposition\.(?P<program_default>\w+);\s*\};\s*\}\s*"
        r"return switch \(command\) \{(?P<command_cases>.*?)"
        r'default -> throw new IllegalStateException\("Uncatalogued native command: " \+ command\);\s*'
        r"\};\s*\}", catalogue, re.S)
    if not dispositions:
        raise ValueError("Unsupported Java command-disposition branch shape; review the audit and run JUnit")
    command_cases = switch_cases(dispositions["command_cases"], r"CommandDisposition\.(\w+)")
    program_cases = switch_cases(dispositions["program_cases"], r"CommandDisposition\.(\w+)")
    for entry in data["commands"]:
        actual = command_cases.get(entry["symbol"])
        if entry["disposition"] == "STAGE_LOAD":
            actual = dispositions["stage"]
        elif entry in programs:
            actual = (dispositions["alias"] if "payload_alias_of" in entry else
                      program_cases.get(entry["symbol"], dispositions["program_default"]))
        equal(actual, entry["disposition"], f"Command {entry['symbol']} disposition")
    file_cases = switch_cases(catalogue, r"FileDisposition\.(\w+)")
    equal(file_cases, {x["symbol"]: x["disposition"] for x in data["files"] if x["disposition"] != "SPX_LOAD_COMPONENT"}, "File dispositions")
    if not re.search(r"default -> FileDisposition\.SPX_LOAD_COMPONENT;\s*\};\s*\}", catalogue):
        raise ValueError("Unsupported Java file-disposition default; review the audit and run JUnit")
    release_rows = re.findall(r'^    (\w+)\((\d+), "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)"', texts["CdReleaseVariant"], re.M)
    equal([(n, int(v), m, d, b, p) for n, v, m, d, b, p in release_rows],
          [(x["name"], x["source_region_value"], x["header_market"], x["disc_header_volume_id"], x["source_build_date"], x["product_id"]) for x in data["releases"]], "Release header facts")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "docs/scddisasm")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output and args.output.resolve().is_relative_to(ROOT):
        parser.error("Research output must be outside the repository")
    try:
        data = inventory(args.source)
        if args.check:
            check(data)
        if args.output:
            args.output.write_text(json.dumps(data, indent=2) + "\n")
    except (OSError, ValueError, KeyError, IndexError) as error:
        parser.error(str(error))
    stages = sum(x["disposition"] == "STAGE_LOAD" for x in data["commands"])
    invalid = sum(x["disposition"] == "INVALID" for x in data["commands"])
    print(f"{len(data['files'])} file IDs, {len(data['commands'])} nonzero dispatch slots "
          f"including {invalid} unnamed invalid slot, {stages} stage payloads, "
          f"{len(data['releases'])} regional header variants audited.")
    print("The dispatch table also has an idle zero word. This is source correspondence, not media or gameplay validation.")


if __name__ == "__main__":
    main()
