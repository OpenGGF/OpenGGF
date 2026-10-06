#!/usr/bin/env python3
"""Build the source-first Putt Putt Paradise mod (Java 21 and Maven).

Origin: Putt Putt Paradise, 2026-10-05. No ROM bytes enter the package.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
OUTPUT = ROOT / "target" / "putt-putt-paradise"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="launch the JVM engine through ggfmod run")
    parser.add_argument("--rom", type=Path, help="existing Sonic 2 ROM; used only by --run, never packaged")
    parser.add_argument("--dry-run", action="store_true", help="show build steps without writing or executing")
    args = parser.parse_args()
    if args.rom is not None:
        args.rom = args.rom.expanduser().resolve()
        if not args.rom.is_file():
            parser.error("--rom must name an existing regular file")
        if not args.run:
            parser.error("--rom requires --run")
    if args.run and args.rom is None:
        parser.error("--run requires --rom (an existing Sonic 2 ROM path)")
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""

    def java_tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name

    def run(command, cwd=ROOT):
        if args.dry_run:
            print(f"[{cwd}] {shlex.join(command)}", flush=True)
        else:
            subprocess.run(command, cwd=cwd, check=True)

    dependencies = OUTPUT / "engine-classpath.txt"
    classes = OUTPUT / "classes"
    jar = OUTPUT / "putt-putt-paradise.jar"
    resources = PROJECT / "src/main/resources"
    manifest = resources / "META-INF/openggf-mod.yaml"
    # This code-only example deliberately packages just its manifest and compiled Java.
    if not args.dry_run and not manifest.is_file():
        parser.error(f"missing mod manifest: {manifest}")
    sources = sorted((PROJECT / "src/main/java").rglob("*.java"))
    if not sources:
        parser.error("no creator Java sources found")
    if not args.dry_run:
        OUTPUT.mkdir(parents=True, exist_ok=True)
        (ROOT / "target/maven-tmp").mkdir(parents=True, exist_ok=True)
    run([sys.executable, "tools/testing/maven_queue.py", "-B", "-Dmse=off", "-DskipTests",
         "compile", "dependency:build-classpath", f"-Dmdep.outputFile={dependencies}"])
    dependency_path = "<Maven dependency classpath>" if args.dry_run else dependencies.read_text().strip()
    classpath = os.pathsep.join([str(ROOT / "target/classes"), dependency_path])
    if not args.dry_run:
        if classes.exists():
            shutil.rmtree(classes)
        classes.mkdir(parents=True)
    run([java_tool("javac"), "--release", "21", "-cp", classpath, "-d", str(classes), *map(str, sources)])
    if args.dry_run:
        print(f"Copy manifest only: {manifest} -> {classes / 'META-INF/openggf-mod.yaml'}")
    else:
        (classes / "META-INF").mkdir()
        shutil.copyfile(manifest, classes / "META-INF/openggf-mod.yaml")
        jar.unlink(missing_ok=True)
    cli = [java_tool("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    run([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"{'Planned' if args.dry_run else 'Built and SDK-validated'} {jar}", flush=True)
    if args.run:
        # JVM configuration resolves config.yaml from cwd. Isolate generated settings
        # and trust state under target instead of changing the user's checkout config.
        launch = OUTPUT / "run"
        if args.dry_run:
            print(f"Write isolated {launch / 'config.yaml'}: roms.sonic2={args.rom}")
        else:
            launch.mkdir(parents=True, exist_ok=True)
            (launch / "config.yaml").write_text("roms:\n  sonic2: " + json.dumps(str(args.rom)) + "\n")
        run([*cli, "run", str(classes)], cwd=launch)


if __name__ == "__main__":
    main()
