#!/usr/bin/env python3
"""Purpose: bake this checkpoint's playable sheet into <resources>/art/mica.ggfp.
Inputs: the output resource root (argument) and OPENGGF_CLI_CLASSPATH, which the
example launchers and ExampleModHarness set to the engine/SDK classpath.
Origin: MICA and TALON tutorial part 2, 2026-11-11. Uses the finished example's
assembler; generated files stay in the build output, never in the source tree.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

PROJECT = Path(__file__).resolve().parent
ROOT = PROJECT.parents[3]
sys.path.insert(0, str(ROOT / "tools/modding/playable_art"))
import assemble  # noqa: E402


def main(resources):
    classpath = os.environ.get("OPENGGF_CLI_CLASSPATH")
    if not classpath:
        sys.exit("OPENGGF_CLI_CLASSPATH is unset; build with examples/build_example.py")
    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        assemble.assemble(PROJECT / "art/sheet", work)
        out = Path(resources) / "art/mica.ggfp"
        out.parent.mkdir(parents=True, exist_ok=True)
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
        subprocess.run([java, "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli",
                        "convert", "art", "--playable", "--image", str(work / "mica.png"),
                        "--sheet", str(work / "mica-sheet.yaml"), "--out", str(out)], check=True)


if __name__ == "__main__":
    main(sys.argv[1])
