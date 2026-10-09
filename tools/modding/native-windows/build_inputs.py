#!/usr/bin/env python3
"""Build all maintained friends mods from source with a supplied Java 21 engine.

Origin: 2026-10-08 experimental Windows ZIP. Uses the same real converters and
package validator as TestSampleModsPackage. No ROMs, downloads, live configuration
or source-tree build output. The explicit new output directory owns every file.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]


def tool(name):
    home = os.environ.get("JAVA_HOME")
    return str(Path(home) / "bin" / (name + (".exe" if os.name == "nt" else ""))) if home else name


def build(engine, sdk, output):
    engine = Path(engine).resolve(strict=True)
    sdk = Path(sdk).resolve(strict=True)
    sys.path.insert(0, str(ROOT / "tools/modding"))
    from build_project import matching_artifacts
    matching_artifacts(engine, sdk)
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    repository = output / "mods"
    repository.mkdir()
    work = output / "work"
    work.mkdir()
    version = subprocess.check_output([tool("javac"), "-version"], text=True).strip()
    if not version.startswith("javac 21."):
        raise ValueError("Build the engine and mods with Java 21: " + version)
    classpath = os.pathsep.join(map(str, (engine, sdk)))
    cli = [tool("java"), "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]

    def run(args):
        subprocess.run([str(arg) for arg in args], cwd=work, check=True)

    def convert(*args):
        run([*cli, "convert", *args])

    def compile_project(project, classes):
        shutil.copytree(project / "src/main/resources", classes, dirs_exist_ok=True)
        sources = sorted((project / "src/main/java").rglob("*.java"))
        if sources:
            arguments = ["--release", "21", "-cp", classpath, "-d", str(classes), *map(str, sources)]
            # Windows command-line limits apply to large examples. This is javac
            # argument-file syntax, not shell interpolation.
            argfile = work / "javac.args"
            argfile.write_text("\n".join(json.dumps(value) for value in arguments), encoding="utf-8")
            run([tool("javac"), "@" + str(argfile)])

    def decoded_image(project, relative, slug):
        path = project / "src/main/mod" / relative
        encoded = path.with_name(path.name + ".base64")
        if path.exists(): return path
        destination = work / (slug + "-" + path.name)
        destination.write_bytes(base64.b64decode(encoded.read_text().strip(), validate=True))
        return destination

    def level_export(project, slug):
        target = work / (slug + "-level")
        shutil.copytree(project / "src/main/mod/level-source", target)
        encoded = target / "binary-assets.properties"
        for line in encoded.read_text().splitlines():
            if not line.strip() or line.lstrip().startswith(("#", "!")): continue
            name, data = line.split("=", 1)
            if Path(name).name != name: raise ValueError("Unexpected exported asset path: " + name)
            (target / name).write_bytes(base64.b64decode(data.strip(), validate=True))
        encoded.unlink()
        return target

    catalog = []

    def package(slug, classes):
        destination = repository / (slug + ".jar")
        run([*cli, "package", "--input", classes, "--out", destination])
        manifest = (classes / "META-INF/openggf-mod.yaml").read_text()
        def scalar(key):
            match = re.search(r"(?m)^" + key + r":\s*(.+)$", manifest)
            if not match: raise ValueError("Missing manifest " + key)
            return match.group(1).strip().strip('"\'')
        catalog.append({"slug": slug, "id": scalar("id"), "name": scalar("name"),
                        "jarSha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
        print("PASS packaged " + slug, flush=True)

    for project in sorted((ROOT / "examples").iterdir()):
        if not (project / "src/main/resources/META-INF/openggf-mod.yaml").is_file(): continue
        classes = work / (project.name + "-classes")
        classes.mkdir()
        compile_project(project, classes)
        package(project.name, classes)

    for slug, source in [
        ("badnik-zone", "sample-mod-src"), ("character", "sample-character-src"),
        ("standalone", "sample-standalone-src"), ("flappy", "sample-flappy-src"),
        ("platformer", "sample-platformer-src"), ("rom-art-remix", "sample-rom-art-remix-src"),
        ("two-act-campaign", "sample-two-act-campaign-src"),
    ]:
        project = ROOT / "src/test/resources/mods" / source / "project"
        classes = work / (slug + "-classes")
        classes.mkdir()
        compile_project(project, classes)
        if slug in ("badnik-zone", "flappy"):
            art = "sample" if slug == "badnik-zone" else "pipe"
            convert("art", "--image", project / f"src/main/mod/{art}.png", "--sheet",
                    project / f"src/main/mod/{art}-sheet.yaml", "--out", classes / f"art/{art}.ggfs")
        if slug in ("character", "standalone"):
            convert("art", "--playable", "--image", decoded_image(project, "runner.png", slug),
                    "--sheet", project / "src/main/mod/runner-sheet.yaml", "--out", classes / "art/runner.ggfp")
        if slug in ("badnik-zone", "standalone", "flappy", "rom-art-remix"):
            name = {"badnik-zone": "sample", "standalone": "sample", "flappy": "flappy",
                    "rom-art-remix": "rom-art-gallery"}[slug]
            convert("level", "--from-export", level_export(project, slug), "--out", classes / "levels" / name)
        if slug == "standalone":
            (classes / "audio/sample-tone.wav").write_bytes(base64.b64decode(
                (project / "src/main/mod/sample-tone.wav.base64").read_text().strip(), validate=True))
        if slug == "platformer":
            for art in ("zapbug", "springpad", "ring", "bolt"):
                convert("art", *(["--playable"] if art == "bolt" else []),
                        "--image", project / f"src/main/mod/{art}.png", "--sheet",
                        project / f"src/main/mod/{art}-sheet.yaml", "--out",
                        classes / (f"art/{art}.ggfp" if art == "bolt" else f"art/{art}.ggfs"))
            convert("level", "--from-tmx", project / "src/main/mod/level.tmx", "--palette",
                    project / "src/main/mod/palette.gpal", "--music", "sample-platformer:zone-theme",
                    "--out", classes / "levels/act1")
        package(slug, classes)

    music = work / "music-classes"
    shutil.copytree(ROOT / "docs/modding/samples/phase4-gallery-music-pack", music)
    run([sys.executable, music / "generate-assets.py"])
    package("music", music)
    reskin = work / "reskin-classes"
    (reskin / "META-INF").mkdir(parents=True)
    source = ROOT / "src/test/resources/mods/sample-reskin-src"
    shutil.copy2(source / "META-INF/openggf-mod.yaml", reskin / "META-INF/openggf-mod.yaml")
    image = work / "reskin.png"
    image.write_bytes(base64.b64decode((source / "reskin.png.base64").read_text().strip(), validate=True))
    convert("art", "--image", image, "--sheet", source / "reskin-sheet.yaml", "--out", reskin / "art/reskin.ggfs")
    package("reskin", reskin)
    if len({mod["id"] for mod in catalog}) != len(catalog): raise ValueError("Duplicate packaged mod IDs")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    (output / "build-info.json").write_text(json.dumps({"sourceCommit": commit,
        "engineSha256": hashlib.sha256(engine.read_bytes()).hexdigest(), "mods": catalog}, indent=2) + "\n")
    shutil.rmtree(work)
    print(f"PASS built {len(catalog)} friends mods from source", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine-jar", type=Path, required=True)
    parser.add_argument("--sdk-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.engine_jar, args.sdk_jar, args.output)
