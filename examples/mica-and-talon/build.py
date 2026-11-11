#!/usr/bin/env python3
"""Build/package the MICA and TALON example against this checkout (Java 25 + Maven).

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


# The complete jar inventory: playable sheets and presentation surfaces.
INVENTORY = {
    "sheets": (
        ("art/mica/sheet", "mica", "art/mica.ggfp"),
        ("art/talon/sheet", "talon", "art/talon.ggfp"),
        *((f"art/mica/powered/{host}", f"mica_powered_{host}", f"art/mica-powered-{host}.ggfp")
          for host in ("s2", "s3k")),
    ),
    "surfaces": tuple(
        (f"art/{character}/{surface}", f"art/{character}/{surface}", (("**/*.txt",) if surface == "powered" else ("**/*.png", "**/*.mask", "**/*.clear", "**/*frames.txt")))
        for character, surfaces in (
            ("mica", ("hud-and-screens", "special-stage", "competition-menu", "reels", "competition",
                      "titles", "stage-poses", "continue", "portrait", "snowboard", "endings", "powered")),
            ("talon", ("hud-and-screens", "special-stage", "competition-menu", "reels", "competition",
                       "titles", "stage-poses", "continue", "portrait", "endings", "powered")),
        )
        for surface in surfaces
    ),
}


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
    generated = OUTPUT / "generated"
    for source, name, destination in INVENTORY["sheets"]:
        run([sys.executable, "tools/modding/playable_art/assemble.py",
             str(PROJECT / source), str(generated)])
        run([*cli, "convert", "art", "--playable", "--image", str(generated / (name + ".png")),
             "--sheet", str(generated / (name + "-sheet.yaml")),
             "--out", str(classes / destination)])
    for source, destination, patterns in INVENTORY["surfaces"]:
        for pattern in patterns:
            for asset in sorted((PROJECT / source).glob(pattern)):
                target = classes / destination / asset.relative_to(PROJECT / source)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(asset, target)
    jar = OUTPUT / "mica-and-talon.jar"
    jar.unlink(missing_ok=True)
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
