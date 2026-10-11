#!/usr/bin/env python3
"""Build Three Islands against this checkout; optionally install or launch it.

Origin: Three Islands creator mod, 2026-10-08 (adapted from Starfall Frontier's build script). Requires Java 21 and Maven.
Uses the ordinary creator packaging validator. Installation explicitly enables
and trusts only the resulting local jar; existing mod entries are preserved.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_example


def install(jar):
    mods = build_example.ROOT / "mods"
    mods.mkdir(exist_ok=True)
    state_path = mods / "modstate.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else {"formatVersion": 1, "entries": []}
    if state.get("formatVersion") != 1 or not isinstance(state.get("entries"), list):
        raise ValueError("Unsupported local mod state; no installed files were changed")
    entries = state["entries"]
    current = next((entry for entry in entries if entry.get("id") == "three-islands"), None)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if current is None:
        current = {"id": "three-islands", "order": max((e.get("order", 0) for e in entries), default=-1) + 1}
        entries.append(current)
    current.update(enabled=True, trusted=True, trustedJarSha256=digest)
    destination = mods / "three-islands.jar"
    temporary = mods / "three-islands.jar.pending"
    shutil.copyfile(jar, temporary)
    temporary.replace(destination)
    pending_state = mods / "modstate.json.pending"
    pending_state.write_text(json.dumps(state, indent=2) + "\n")
    pending_state.replace(state_path)
    print(f"Installed, enabled and trusted {destination}")
    print("Restart the JVM engine, then select Sonic 3 & Knuckles to play.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-engine", action="store_true", help="reuse target/classes and its dependency classpath")
    parser.add_argument("--install", action="store_true", help="install, enable and trust this exact jar in the checkout's mods directory")
    parser.add_argument("--run", action="store_true", help="launch the mod through the development runner")
    args = parser.parse_args()
    jar = build_example.build("three-islands", skip_engine=args.skip_engine)
    if args.install:
        install(jar)
    if args.run:
        root = build_example.ROOT
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
        classpath = os.pathsep.join([str(root / "target/classes"), (root / "target/examples-classpath.txt").read_text().strip()])
        subprocess.run([java, "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli", "run",
                        str(jar.parent / "classes")], cwd=root, check=True)


if __name__ == "__main__":
    main()
