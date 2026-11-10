#!/usr/bin/env python3
"""Build/package the MICA mod against this checkout (Java 25 + Maven).

Origin: MICA skeleton project, 2026-10-07. No ROM data is included in the jar.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent
OUTPUT = ROOT / "target" / "mica-and-talon"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="launch the JVM engine with this development mod")
    parser.add_argument("--save-root", type=Path, default=OUTPUT / "player-settings",
                        help="developer player settings root (requested settings only)")
    args = parser.parse_args()
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    def java_tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name
    def run(command):
        subprocess.run(command, cwd=ROOT, check=True)
    import sys
    run([sys.executable, "tools/testing/maven_queue.py", "-B", "-Dmse=off", "-DskipTests",
         "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/mica-classpath.txt"])
    classes = OUTPUT / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join([str(ROOT / "target/classes"),
                                (ROOT / "target/mica-classpath.txt").read_text().strip()])
    run([java_tool("javac"), "--release", "25", "-cp", classpath, "-d", str(classes),
         *map(str, sorted((PROJECT / "src/main/java").rglob("*.java")))])
    shutil.copytree(PROJECT / "src/main/resources", classes, dirs_exist_ok=True)
    (classes / "art").mkdir(exist_ok=True)
    cli = [java_tool("java"), "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
           "com.openggf.tools.modsdk.GgfModCli"]
    run([sys.executable, "tools/modding/playable_art/assemble.py",
         str(PROJECT / "art/mica"), str(PROJECT / "src/main/mod")])
    run([*cli, "convert", "art", "--playable", "--image", str(PROJECT / "src/main/mod/mica.png"),
         "--sheet", str(PROJECT / "src/main/mod/mica-sheet.yaml"), "--out", str(classes / "art/mica.ggfp")])
    run([sys.executable, "tools/modding/playable_art/assemble.py",
         str(PROJECT / "art/talon"), str(PROJECT / "src/main/mod")])
    run([*cli, "convert", "art", "--playable", "--image", str(PROJECT / "src/main/mod/talon.png"),
         "--sheet", str(PROJECT / "src/main/mod/talon-sheet.yaml"), "--out", str(classes / "art/talon.ggfp")])
    for host in ("s2", "s3k"):
        run([sys.executable, "tools/modding/playable_art/assemble.py", str(PROJECT / "art/mica/powered" / host), str(PROJECT / "src/main/mod")])
        run([*cli, "convert", "art", "--playable", "--image", str(PROJECT / f"src/main/mod/mica_powered_{host}.png"),
             "--sheet", str(PROJECT / f"src/main/mod/mica_powered_{host}-sheet.yaml"), "--out", str(classes / f"art/mica-powered-{host}.ggfp")])
    shutil.copytree(PROJECT / "art/mica/presentation", classes / "art/mica/presentation", dirs_exist_ok=True)
    for character in ("mica", "talon"):
        shutil.copytree(PROJECT / "art" / character / "competition-menu", classes / "art" / character / "competition-menu", dirs_exist_ok=True)
        shutil.copytree(PROJECT / "art" / character / "reels", classes / "art" / character / "reels", dirs_exist_ok=True)
        shutil.copytree(PROJECT / f"art/{character}/competition", classes / f"art/{character}/competition", dirs_exist_ok=True)
    shutil.copytree(PROJECT / "art/talon/endings", classes / "art/talon/endings", dirs_exist_ok=True)
    stage_assets = classes / "art/mica/wave3"
    stage_assets.mkdir(parents=True, exist_ok=True)
    for frame in (PROJECT / "art/mica/wave3").glob("*.png"):
        if not frame.stem.endswith("-fixed"):
            shutil.copy2(frame, stage_assets / frame.name)
    for surface in ("continue", "portrait", "snowboard"):
        shutil.copytree(PROJECT / "art/mica" / surface, classes / "art/mica" / surface, dirs_exist_ok=True)
    ending_assets = classes / "art/mica/endings/s1"
    ending_assets.mkdir(parents=True, exist_ok=True)
    for frame in (PROJECT / "art/mica/endings/s1").glob("*.png"):
        shutil.copy2(frame, ending_assets / frame.name)
    ending_assets = classes / "art/mica/endings/s2"
    ending_assets.mkdir(parents=True, exist_ok=True)
    for frame in (PROJECT / "art/mica/endings/s2").glob("*.png"):
        shutil.copy2(frame, ending_assets / frame.name)
    ending_assets = classes / "art/mica/endings/s3k"
    ending_assets.mkdir(parents=True, exist_ok=True)
    for frame in (PROJECT / "art/mica/endings/s3k").glob("*.png"):
        shutil.copy2(frame, ending_assets / frame.name)
    jar = OUTPUT / "mica-and-talon.jar"
    jar.unlink(missing_ok=True)
    cli = [java_tool("java"), "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
           "com.openggf.tools.modsdk.GgfModCli"]
    run([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built {jar}", flush=True)
    if args.run:
        save_root = args.save_root.absolute()
        if save_root.is_symlink():
            raise SystemExit("Settings roots must be owned directories, not symlinks")
        save_root.mkdir(parents=True, exist_ok=True)
        run([java_tool("java"), "--enable-native-access=ALL-UNNAMED",
             "-Dggfmod.dev.modDir=" + str(classes),
             "-Dopenggf.saveRoot=" + str(save_root), "-cp", classpath, "com.openggf.Engine"])


if __name__ == "__main__":
    main()
