#!/usr/bin/env python3
"""Build a test-only Sonic Echoes capture jar for GameplayCaptureTool --mod.

Purpose: film puzzle rooms with a per-frame session sidecar (PromoCaptureMod) and, with --actions,
scheduled Echo/Undo/Reset presses at authored loop frames (PromoActionCaptureMod).
Inputs: compiled engine target/classes and target/examples-classpath.txt (run examples/sonic-echoes/
build.py once first), JAVA_HOME pointing at JDK 25, an output directory.
Output: <out>/promo-capture.jar or <out>/promo-actions.jar; the distributed mod jar is untouched.
Capture properties: -Dechoes.capture.room=<id> (omit for title chains), -Dechoes.capture.session=<csv>,
-Dechoes.capture.actions=loop:loopFrame:ECHO,... (zero-based loop).
Origin: Sonic Echoes promo captures, 2026-10-24 (base 5fca08819f).
"""
import argparse
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MOD = ROOT / "examples/sonic-echoes"

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("out", type=Path, help="output directory (outside the repository)")
parser.add_argument("--actions", action="store_true", help="also schedule Echo/Undo/Reset presses")
args = parser.parse_args()

classes = args.out / "classes"
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
cp = os.pathsep.join([str(ROOT / "target/classes"), (ROOT / "target/examples-classpath.txt").read_text().strip()])
sources = sorted(str(p) for p in (MOD / "src/main/java").rglob("*.java"))
sources.append(str(MOD / "src/test/java/echoes/PromoCaptureMod.java"))
if args.actions:
    sources.append(str(MOD / "src/test/java/echoes/PromoActionCaptureMod.java"))
javac = Path(os.environ["JAVA_HOME"]) / "bin" / "javac"
subprocess.run([str(javac), "--release", "25", "-cp", cp, "-d", str(classes), *sources], check=True)
shutil.copytree(MOD / "src/main/resources", classes, dirs_exist_ok=True)
manifest = classes / "META-INF/openggf-mod.yaml"
entry = "echoes.PromoActionCaptureMod" if args.actions else "echoes.PromoCaptureMod"
manifest.write_text(manifest.read_text().replace("entrypoint: echoes.EchoesMod", f"entrypoint: {entry}"))
jar = args.out / ("promo-actions.jar" if args.actions else "promo-capture.jar")
with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted(classes.rglob("*")):
        if p.is_file():
            z.write(p, p.relative_to(classes))
print(jar)
