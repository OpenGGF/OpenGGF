#!/usr/bin/env python3
"""Qualify a prebuilt Windows friends ZIP with Wine and original local ROMs.

Inputs: immutable Windows ZIP, three original ROM paths, new owned work/capture
directories and a new output ZIP. Origin: 2026-10-09 Windows refresh. No ROM
copies, Java compilation, production policy changes or Microsoft-Windows gameplay
certification. Captures and structured evidence are durable; runtime files stay
in the owned work directory. Run from a Linux desktop with Wine/OpenGL available.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from build_windows import archive_bundle, digest, extract_jar, pe_x64, ROOT

# The recipes and Java probe are shared with Linux; they do not contain OS APIs.
sys.path.insert(0, str(ROOT / "tools/modding/native-linux"))
from build_linux import RECIPES


def verify_bundle(bundle):
    checked = set()
    for line in (bundle / "SHA256SUMS.txt").read_text().splitlines():
        expected, relative = line.split("  ", 1)
        path = bundle / relative
        if Path(relative).is_absolute() or ".." in Path(relative).parts:
            raise ValueError("Unsafe checksum path")
        if relative in checked: raise ValueError("Duplicate checksum path")
        if digest(path) != expected: raise ValueError("Bundle hash drift: " + relative)
        checked.add(relative)
    actual = {path.relative_to(bundle).as_posix() for path in bundle.rglob("*")
              if path.is_file() and path != bundle / "SHA256SUMS.txt"}
    if actual != checked: raise ValueError("Incomplete bundle checksum coverage")
    pe_x64(bundle / "OpenGGF.exe")
    if any(bundle.rglob("*.gen")) or any(bundle.rglob("*.smd")) or any(bundle.glob("*.bin")):
        raise ValueError("Unexpected ROM in bundle")
    catalog = json.loads((bundle / "build-info.json").read_text())
    for mod in catalog["mods"]:
        if not mod["slug"] or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in mod["slug"]):
            raise ValueError("Unsafe mod slug")
        if digest(bundle / "mods" / (mod["slug"] + ".jar")) != mod["jarSha256"]:
            raise ValueError("Mod hash drift: " + mod["slug"])
    return catalog


def qualify(args):
    archive = args.input.resolve(strict=True)
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    captures = args.captures.resolve()
    captures.mkdir(parents=True, exist_ok=False)
    if args.output.exists(): raise FileExistsError(args.output)
    roms = [path.resolve(strict=True) for path in args.roms]
    # Check actual identities before a wrong ROM can produce plausible images.
    import hashlib
    hashes = [hashlib.sha1(path.read_bytes()).hexdigest() for path in roms]
    if hashes != ["69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b",
                  "8bca5dcef1af3e00098666fd892dc1c2a76333f9",
                  "cfbf98c36c776677290a872547ac47c53d2761d6"]:
        raise ValueError("Original ROM identity mismatch")
    extracted = work / "extracted path with spaces"
    extract_jar(archive, extracted)
    roots = list(extracted.iterdir())
    if len(roots) != 1 or not roots[0].is_dir(): raise ValueError("Expected one distribution root")
    bundle = roots[0]
    catalog = verify_bundle(bundle)
    prefix = work / "wine-prefix"
    environment = dict(os.environ, WINEPREFIX=str(prefix), WINEDEBUG="-all", OPENGGF_NO_PAUSE="1",
                       LIBGL_ALWAYS_SOFTWARE="1")
    environment.pop("JAVA_HOME", None)
    logs = work / "logs"
    logs.mkdir()

    def command(arguments, label, expected=None):
        result = subprocess.run(["wine", *map(str, arguments)], cwd=bundle, env=environment,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
        (logs / (label + ".log")).write_text(result.stdout)
        if expected is None:
            if result.returncode: raise RuntimeError(f"Wine check failed: {label}; exit {result.returncode}; see {logs}")
        elif result.returncode != 1 or expected not in result.stdout:
            raise AssertionError(f"Expected ordinary exit 1: {label}; exit {result.returncode}")
        return result.stdout

    def windows_path(path):
        return subprocess.check_output(["winepath", "-w", str(path)], env=environment, text=True).strip()

    evidence = {"runtime": "Windows PE under " + subprocess.check_output(["wine", "--version"], text=True).strip(),
                "graphics": "host Linux desktop; Mesa software OpenGL", "microsoftWindowsGameplay": "not certified",
                "inputZipSha256": digest(archive), "sourceCommit": catalog["sourceCommit"],
                "nativeBuildSourceCommit": catalog["nativeBuildSourceCommit"], "nativeSha256": digest(bundle / "OpenGGF.exe"),
                "originalRomSha1": hashes, "renderedGameplay": []}
    try:
        command(["./OpenGGF.exe", "--audit"], "audit")
        original = (bundle / "native-mod-members.tsv").read_bytes()
        for kind, descriptor in (("F", "I"), ("M", "()V")):
            try:
                (bundle / "native-mod-members.tsv").write_text(
                    f"{kind}\tcom.openggf.game.rules.GameRules\t__absent_control\t{descriptor}\n")
                command(["./OpenGGF.exe", "--audit"], "missing-" + kind, "MISSING native member: " + kind)
            finally: (bundle / "native-mod-members.tsv").write_bytes(original)
        evidence["memberAudit"] = "passed; missing field and method rejected with ordinary exit 1"
        for mod in catalog["mods"]:
            slug, owner = mod["slug"], mod["id"]
            capture = captures / slug
            output = command(["cmd.exe", "/d", "/c", "call", windows_path(bundle / ("Launch " + slug + ".bat")),
                "--check-gameplay", slug, windows_path(capture), *map(windows_path, roms), *RECIPES.get(slug, [])], slug)
            if f"PASS native rendered gameplay: {owner}" not in output:
                raise AssertionError("Missing gameplay result: " + owner)
            evidence["renderedGameplay"].append(owner)
            print("PASS Windows PE rendered gameplay under Wine: " + owner, flush=True)
        command(["cmd.exe", "/d", "/c", "call", windows_path(bundle / "OpenGGF.bat"), "--audit"], "normal-batch")
        evidence["batchLaunchers"] = "all mod shortcuts forwarded gameplay arguments from an extracted path with spaces; normal launcher audited"
        # Re-extract immutable inputs to exclude all generated runtime state.
        shutil.rmtree(extracted)
        extract_jar(archive, extracted)
        bundle = next(extracted.iterdir())
        verify_bundle(bundle)
        catalog["validation"]["wineGameplay"] = evidence
        (bundle / "build-info.json").write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
        with (bundle / "README.txt").open("a", encoding="utf-8") as readme:
            readme.write("\nRefresh qualification: all bundled mods passed representative rendered gameplay\n"
                         "checks using this Windows executable under Wine. Registration, runtime JDK\n"
                         "controls and batch startup checks also passed on the Microsoft Windows builder.\n"
                         "Wine gameplay is compatibility evidence; full Microsoft Windows gameplay,\n"
                         "all routes, network sessions and graphics drivers remain unqualified.\n")
        (bundle / "SHA256SUMS.txt").unlink()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        archive_bundle(bundle, args.output)
        evidence["deliveredZipSha256"] = digest(args.output)
        (captures / "qualification.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print("PASS Windows ZIP with Wine gameplay evidence: " + str(args.output), flush=True)
    finally:
        if prefix.exists(): subprocess.run(["wineserver", "-k"], env=environment, check=False, timeout=30)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("input", "work", "captures", "output"): parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--roms", nargs=3, required=True, type=Path)
    qualify(parser.parse_args())
