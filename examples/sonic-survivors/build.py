#!/usr/bin/env python3
"""Build/package the Sonic Survivors mod against this checkout (Java 21 + Maven).

Origin: Sonic Survivors project, 2026-10-06. No ROM data is included in the jar.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
OUTPUT = ROOT / "target" / "sonic-survivors"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="launch the JVM engine with this development mod")
    args = parser.parse_args()
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    def java_tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name
    def run(command):
        subprocess.run(command, cwd=ROOT, check=True)
    import sys
    run([sys.executable, "tools/testing/maven_queue.py", "-B", "-Dmse=off", "-DskipTests",
         "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/survivors-classpath.txt"])
    classes = OUTPUT / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join([str(ROOT / "target/classes"),
                                (ROOT / "target/survivors-classpath.txt").read_text().strip()])
    run([java_tool("javac"), "--release", "21", "-cp", classpath, "-d", str(classes),
         *map(str, sorted((PROJECT / "src/main/java").rglob("*.java")))])
    shutil.copytree(PROJECT / "src/main/resources", classes, dirs_exist_ok=True)
    jar = OUTPUT / "sonic-survivors.jar"
    jar.unlink(missing_ok=True)
    cli = [java_tool("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    run([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built {jar}", flush=True)
    if args.run:
        run([*cli, "run", str(classes)])


if __name__ == "__main__":
    main()
