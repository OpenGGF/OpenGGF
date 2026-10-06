#!/usr/bin/env python3
"""Build, package and optionally run an example mod from examples/<name> against this checkout.

Usage:
  python3 examples/build_example.py hello-scene          # compile and package target/examples/hello-scene/hello-scene.jar
  python3 examples/build_example.py hello-scene --run    # ...then launch the engine with it as a development mod
  python3 examples/build_example.py slay-the-robotnik --skip-engine   # reuse the engine already built in target/

It compiles the engine once through the Maven queue (unless --skip-engine), compiles the
example's src/main/java against it with javac, copies src/main/resources beside the classes,
and packages the jar with `ggfmod package`, which runs the mod validator. Needs Java 21.
Examples hold only code and text: art and audio come from the player's own ROM at runtime.

Origin: Slay the Robotnik's build script (2026-10-05), shared by the examples on 2026-10-06.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def build(name, run=False, skip_engine=False):
    project = ROOT / "examples" / name
    if not (project / "src/main/resources/META-INF/openggf-mod.yaml").exists():
        sys.exit(f"{project} is not an example mod (no src/main/resources/META-INF/openggf-mod.yaml)")
    output = ROOT / "target" / "examples" / name
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""

    def java_tool(tool):
        return str(Path(java_home) / "bin" / (tool + suffix)) if java_home else tool

    def call(command):
        subprocess.run(command, cwd=ROOT, check=True)

    classpath_file = ROOT / "target" / "examples-classpath.txt"
    if not skip_engine or not classpath_file.exists():
        call([sys.executable, "tools/testing/maven_queue.py", "-B", "-q", "-Dmse=off", "-DskipTests",
              "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/examples-classpath.txt"])
    classes = output / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join([str(ROOT / "target/classes"), classpath_file.read_text().strip()])
    call([java_tool("javac"), "--release", "21", "-cp", classpath, "-d", str(classes),
          *map(str, sorted((project / "src/main/java").rglob("*.java")))])
    shutil.copytree(project / "src/main/resources", classes, dirs_exist_ok=True)
    jar = output / f"{name}.jar"
    jar.unlink(missing_ok=True)
    cli = [java_tool("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    call([*cli, "package", "--input", str(classes), "--out", str(jar)])
    print(f"Built {jar}", flush=True)
    if run:
        call([*cli, "run", str(classes)])
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("name", help="the example's directory under examples/, e.g. hello-scene")
    parser.add_argument("--run", action="store_true", help="launch the JVM engine with this development mod")
    parser.add_argument("--skip-engine", action="store_true", help="reuse the existing engine build in target/")
    args = parser.parse_args()
    build(args.name, run=args.run, skip_engine=args.skip_engine)


if __name__ == "__main__":
    main()
