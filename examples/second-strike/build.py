#!/usr/bin/env python3
"""Build Second Strike, or launch it with its own native save/config profile.

Origin: Second Strike prototype, 2026-10-13. Reads the player's original ROM;
packages no ROM assets. Java 25 and Maven are required.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
OUTPUT = ROOT / "target" / "second-strike"
sys.path.insert(0, str(ROOT / "tools/testing"))
from maven_queue import worktree_build_slot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="open the interactive engine")
    parser.add_argument("--rom", type=Path, help="original Sonic 3 & Knuckles ROM, required with --run")
    parser.add_argument("--profile", type=Path, default=OUTPUT / "player-profile",
                        help="owned config/save directory, separate from stock saves")
    parser.add_argument("--character", choices=("sonic", "tails", "knuckles"), default="sonic")
    parser.add_argument("--solo", action="store_true", help="disable CPU Tails for Sonic")
    args = parser.parse_args()
    if args.run and (args.rom is None or not args.rom.is_file()):
        parser.error("--run requires --rom pointing to your original locked-on ROM")
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    def tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name
    def run(command, cwd=ROOT):
        subprocess.run(command, cwd=cwd, check=True)
    run([sys.executable, "tools/testing/maven_queue.py", "-B", "-Dmse=off", "-DskipTests",
         "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/second-strike-classpath.txt"])
    classpath = os.pathsep.join([str(ROOT / "target/classes"),
                                (ROOT / "target/second-strike-classpath.txt").read_text().strip()])
    classes = OUTPUT / "classes"
    # The author compiler and packager also hold the repository's build/test
    # lock, preventing either from reading a concurrently changing build tree.
    with worktree_build_slot(ROOT):
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir(parents=True)
        run([tool("javac"), "--release", "25", "-cp", classpath, "-d", str(classes),
             *map(str, sorted((PROJECT / "src/main/java").rglob("*.java")))])
        shutil.copytree(PROJECT / "src/main/resources", classes, dirs_exist_ok=True)
        jar = OUTPUT / "second-strike.jar"
        jar.unlink(missing_ok=True)
        run([tool("java"), "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
             "com.openggf.tools.modsdk.GgfModCli", "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built {jar}", flush=True)
    if args.run:
        profile = args.profile.absolute()
        if any(path.is_symlink() for path in (profile, profile / "config.yaml", profile / "saves")):
            raise SystemExit("Profile/config/saves must be owned paths, not symlinks")
        profile.mkdir(parents=True, exist_ok=True)
        config = profile / "config.yaml"
        if not config.exists():
            # JSON is a valid YAML subset, and avoids unsafe path interpolation.
            config.write_text(json.dumps({"SONIC_3K_ROM": str(args.rom.resolve()),
                "S3K_SKIP_INTROS": False, "MAIN_CHARACTER_CODE": args.character,
                "SIDEKICK_CHARACTER_CODE": "tails" if args.character == "sonic" and not args.solo else "",
                "LAUNCH_S3K_MAIN_CHARACTER": args.character,
                "LAUNCH_S3K_SIDEKICK": "tails" if args.character == "sonic" and not args.solo else ""}, indent=2) + "\n")
        run([tool("java"), "--enable-native-access=ALL-UNNAMED",
             "-Dggfmod.dev.modDir=" + str(classes), "-Dopenggf.saveRoot=" + str(profile / "saves"),
             "-cp", classpath, "com.openggf.Engine"], cwd=profile)


if __name__ == "__main__":
    main()
