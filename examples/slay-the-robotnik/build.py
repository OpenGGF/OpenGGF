#!/usr/bin/env python3
"""Build/package the Slay the Robotnik example mod against this checkout (Java 21 + Maven).

Usage:
  python3 examples/slay-the-robotnik/build.py          # compile and package target/slay-the-robotnik/slay-the-robotnik.jar
  python3 examples/slay-the-robotnik/build.py --run    # ...then launch the engine with the mod as a development mod

Origin: Slay the Robotnik example mod, 2026-10-05. No ROM data is included in the jar:
art and audio are read from the player's own Sonic 3 & Knuckles ROM at runtime.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
OUTPUT = ROOT / "target" / "slay-the-robotnik"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", action="store_true", help="launch the JVM engine with this development mod")
    parser.add_argument("--skip-engine", action="store_true", help="reuse the existing engine build in target/")
    args = parser.parse_args()
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""

    def java_tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name

    def run(command):
        subprocess.run(command, cwd=ROOT, check=True)

    classpath_file = ROOT / "target" / "slay-classpath.txt"
    if not args.skip_engine or not classpath_file.exists():
        run([sys.executable, "tools/testing/maven_queue.py", "-B", "-q", "-Dmse=off", "-DskipTests",
             "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/slay-classpath.txt"])
    classes = OUTPUT / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join([str(ROOT / "target/classes"), classpath_file.read_text().strip()])
    run([java_tool("javac"), "--release", "21", "-cp", classpath, "-d", str(classes),
         *map(str, sorted((PROJECT / "src/main/java").rglob("*.java")))])
    shutil.copytree(PROJECT / "src/main/resources", classes, dirs_exist_ok=True)
    jar = OUTPUT / "slay-the-robotnik.jar"
    jar.unlink(missing_ok=True)
    cli = [java_tool("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    run([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built {jar}", flush=True)
    if args.run:
        run([*cli, "run", str(classes)])


if __name__ == "__main__":
    main()
