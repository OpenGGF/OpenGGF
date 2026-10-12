#!/usr/bin/env python3
"""Build/package Post Two Ambush; optionally launch with an isolated JVM configuration.

Inputs: maintained Java/manifest and an existing user ROM for --run. No ROM
payload enters the jar. Origin: Hardened S3K prototype, 2026-10-07; follows the
Putt Putt Paradise two-artifact build recipe.
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
OUTPUT = ROOT / "target" / "hardened-s3k"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--rom", type=Path, help="existing locked-on Sonic 3 & Knuckles ROM")
    parser.add_argument("--skip-engine", action="store_true", help="reuse the current engine build")
    args = parser.parse_args()
    if args.run:
        if args.rom is None or not args.rom.expanduser().resolve().is_file():
            parser.error("--run requires --rom naming an existing file")
        args.rom = args.rom.expanduser().resolve()
    elif args.rom is not None:
        parser.error("--rom requires --run")
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""

    def java(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name

    def call(command, cwd=ROOT):
        subprocess.run(command, cwd=cwd, check=True)

    OUTPUT.mkdir(parents=True, exist_ok=True)
    dependencies = OUTPUT / "engine-classpath.txt"
    if not args.skip_engine or not dependencies.exists():
        call([sys.executable, "tools/testing/maven_queue.py", "-B", "-Dmse=off", "-DskipTests",
              "compile", "dependency:build-classpath", f"-Dmdep.outputFile={dependencies}"])
    classpath = os.pathsep.join([str(ROOT / "target/classes"), dependencies.read_text().strip()])
    classes = OUTPUT / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    call([java("javac"), "--release", "21", "-cp", classpath, "-d", str(classes),
          *map(str, sorted((PROJECT / "src/main/java").rglob("*.java")))])
    shutil.copytree(PROJECT / "src/main/resources", classes, dirs_exist_ok=True)
    jar = OUTPUT / "hardened-s3k.jar"
    jar.unlink(missing_ok=True)
    cli = [java("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    call([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built and SDK-validated {jar}", flush=True)
    if args.run:
        launch = OUTPUT / "run"
        launch.mkdir(exist_ok=True)
        # Never follow/edit the checkout's shared config.yaml or stock save root.
        config = (
            "roms:\n  sonic3k: " + json.dumps(str(args.rom)) + "\n  default: s3k\n"
            "startup:\n  masterTitleScreen: false\n  legalDisclaimer: false\n  titleScreen: true\n"
            "characters:\n  main: sonic\n  sidekick: ''\n"
            "display:\n  aspect: NATIVE_4_3\n"
            "input:\n  pause: P\n  player1:\n    start: BACKSPACE\n"
            "crossGame:\n  enabled: false\n  source: off\n"
        )
        (launch / "config.yaml").write_text(config)
        call([*cli, "run", str(classes)], cwd=launch)


if __name__ == "__main__":
    main()
