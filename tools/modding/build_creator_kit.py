#!/usr/bin/env python3
"""Create a reproducible, versioned unpublished creator kit from local artifacts.

Contains only matching packaged jars, generated API docs, handbook, launchers and
authored starter sources. No ROMs, runtime config, build trees or saves are copied.
Origin: creator readiness 2026-10-07. This command never publishes an API/release.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CAMPAIGN_SOURCE = Path("src/test/resources/mods/sample-two-act-campaign-src/project")
CAMPAIGN_ASSETS = frozenset(("patterns.bin", "chunks.bin", "blocks.bin", "fg-map.bin", "bg-map.bin",
                             "solid-heights.bin", "solid-widths.bin", "solid-angles.bin",
                             "collision-primary.bin", "collision-secondary.bin", "palettes.bin"))


def version(root):
    return ET.parse(root / "pom.xml").findtext("{http://maven.apache.org/POM/4.0.0}version")


def project_pom(name):
    return f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>example.mods</groupId><artifactId>{name}</artifactId><version>1.0.0</version>
<properties><maven.compiler.release>21</maven.compiler.release><openggf.creator.testSources>src/creator-tests-disabled</openggf.creator.testSources><skipTests>true</skipTests></properties>
<dependencies>
<dependency><groupId>com.openggf</groupId><artifactId>engine-local</artifactId><version>0</version><scope>system</scope><systemPath>${{openggf.engine.jar}}</systemPath></dependency>
<dependency><groupId>com.openggf</groupId><artifactId>sdk-local</artifactId><version>0</version><scope>system</scope><systemPath>${{openggf.sdk.jar}}</systemPath></dependency>
<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.10.3</version><scope>test</scope></dependency>
<dependency><groupId>org.junit.platform</groupId><artifactId>junit-platform-console-standalone</artifactId><version>1.10.3</version><scope>test</scope></dependency>
</dependencies>
<profiles><profile><id>creator-tests</id><activation><property><name>openggf.testkit.version</name></property></activation><properties><openggf.creator.testSources>src/test/java</openggf.creator.testSources><skipTests>false</skipTests></properties><dependencies>
<dependency><groupId>com.openggf</groupId><artifactId>OpenGGF</artifactId><version>${{openggf.testkit.version}}</version><classifier>mod-testkit</classifier><scope>test</scope></dependency>
</dependencies></profile></profiles><build><testSourceDirectory>${{openggf.creator.testSources}}</testSourceDirectory><plugins>
<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.0</version></plugin>
<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId><version>3.1.0</version><executions><execution><id>clean-package-output</id><phase>initialize</phase><goals><goal>run</goal></goals><configuration><target><delete file="${{project.build.directory}}/{name}-mod.jar" quiet="true"/></target></configuration></execution></executions></plugin>
<plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.1.0</version><executions><execution><id>validated-mod</id><phase>prepare-package</phase><goals><goal>exec</goal></goals><configuration><executable>java</executable><classpathScope>compile</classpathScope><arguments><argument>-cp</argument><classpath/><argument>com.openggf.tools.modsdk.GgfModCli</argument><argument>package</argument><argument>--input</argument><argument>${{project.build.outputDirectory}}</argument><argument>--out</argument><argument>${{project.build.directory}}/{name}-mod.jar</argument></arguments></configuration></execution></executions></plugin>
</plugins></build></project>'''.encode()


def exported_projects(root):
    """Declare portable examples and the maintained campaign; fail on incomplete sources."""
    projects = {}
    for project in sorted((root / "examples").iterdir()):
        if (project / "src/main/resources/META-INF/openggf-mod.yaml").is_file():
            projects[project.name] = project
    campaign = root / CAMPAIGN_SOURCE
    for relative in ("README.md", "tools/generate_assets.py", "src/main/java/example/tide/TideCircuitMod.java",
                     "src/main/resources/META-INF/openggf-mod.yaml"):
        if not (campaign / relative).is_file():
            raise ValueError("Missing maintained campaign source input: " + str(CAMPAIGN_SOURCE / relative))
    for act in (1, 2):
        level = campaign / f"src/main/resources/levels/tide/act{act}/level.json"
        if not level.is_file():
            raise ValueError("Missing maintained campaign level: " + str(level.relative_to(root)))
        assets = json.loads(level.read_text()).get("assets", {})
        if set(assets.values()) != CAMPAIGN_ASSETS:
            raise ValueError("Campaign level must declare its complete original asset inventory: " + str(level.relative_to(root)))
        for name in sorted(CAMPAIGN_ASSETS):
            if not (level.parent / name).is_file():
                raise ValueError("Missing maintained campaign asset: " + str((level.parent / name).relative_to(root)))
    if "tide-circuit" in projects:
        raise ValueError("Export name tide-circuit is reserved for the maintained campaign")
    projects["tide-circuit"] = campaign
    return dict(sorted(projects.items()))


def export_examples(root):
    entries = {}
    for name, project in exported_projects(root).items():
        for file in sorted((project / "src/main").rglob("*")):
            if file.is_file():
                entries["examples/" + name + "/" + file.relative_to(project).as_posix()] = file.read_bytes()
        if name == "tide-circuit":
            entries["examples/tide-circuit/tools/generate_assets.py"] = (project / "tools/generate_assets.py").read_bytes()
        if name == "hello-scene":
            specimen = project / "src/test/java/hello/HelloSceneIntegrationTest.java"
            if specimen.is_file():
                entries["examples/hello-scene/src/test/java/hello/HelloSceneIntegrationTest.java"] = specimen.read_bytes()
        readme = project / "README.md"
        manifest = (project / "src/main/resources/META-INF/openggf-mod.yaml").read_text()
        base_game = re.search(r"(?m)^baseGame:\s*(s1|s2|s3k)\s*$", manifest)
        game = base_game.group(1) if base_game else "s2"
        instructions = f'''# Build {name} from the creator kit

Use a Java **21 JDK** and Python 3, or Maven **3.8 or newer**. Keep `engine.jar`
and `sdk.jar` from the same creator kit/source commit; this is a mutable candidate.

From this project directory, build with the portable launcher:

```sh
python3 ../../tools/build_project.py .
```

The distributable is **`target/{name}-mod.jar`**. Only `src/main` classes
and resources enter that jar. Alternatively, build with Maven and matching absolute
artifact paths:

```sh
mvn package -Dopenggf.engine.jar=/absolute/kit/engine.jar -Dopenggf.sdk.jar=/absolute/kit/sdk.jar
```

Launch with your own ROM paths. This example supplies the {game} path; add the
required game/donor paths using `--s1`, `--s2` or `--s3k` as needed:

```sh
python3 ../../tools/build_project.py . --run --{game} /absolute/own-{game}.gen
```

The launcher keeps generated runtime configuration and saves under `target/play`.
ROMs are read from your explicit paths and are not included in the exported project.

## Project behavior and source notes

The original project notes follow. Checkout paths and play/build scripts mentioned
there refer to the pinned source tree; use the creator-kit commands above here.

'''
        entries[f"examples/{name}/README.md"] = instructions.encode() + (readme.read_bytes() if readme.exists() else b"")
        entries[f"examples/{name}/pom.xml"] = project_pom(name)
    return entries


def build_identity(data, path):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return dict(line.split("=", 1) for line in archive.read(path).decode().splitlines()
                    if "=" in line and not line.startswith("#"))


def normalize_jar(data):
    """Preserve payload bytes while fixing Maven archive order/time/permissions."""
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Creator artifact contains duplicate ZIP entries")
        for name in sorted(names, key=lambda value: (value != "META-INF/MANIFEST.MF", value)):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o40755 if name.endswith("/") else 0o100644) << 16
            target.writestr(info, source.read(name))
    return output.getvalue()


def verify_testkit(data):
    """Reject incomplete or mixed classifier contents before distributing them."""
    prefix = "com/openggf/mods/testing/"
    required = {prefix + name + ".class" for name in
                ("ModTestKit", "DeterministicInput", "CreatorTestLauncher")}
    metadata = {"META-INF/MANIFEST.MF", "META-INF/openggf-build.properties",
                "META-INF/maven/com.openggf/OpenGGF/pom.xml",
                "META-INF/maven/com.openggf/OpenGGF/pom.properties"}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        files = {name for name in archive.namelist() if not name.endswith("/")}
        if not required <= files:
            raise ValueError("Testkit classifier is incomplete: " + ", ".join(sorted(required - files)))
        unexpected = {name for name in files if name not in metadata
                      and not (name.startswith(prefix) and name.endswith(".class"))}
        if unexpected:
            raise ValueError("Testkit classifier contains unrelated inputs: " + ", ".join(sorted(unexpected)))


def relocate_markdown_links(root, entries, commit):
    """Keep exported links local, and pin checkout-only references to source SHA."""
    source_to_export = {}
    for name in entries:
        if name.startswith("handbook/"):
            source_to_export[(root / "docs/modding" / name.removeprefix("handbook/")).resolve()] = name
    projects = exported_projects(root)
    for name in entries:
        if name.startswith("examples/"):
            _, project, relative = name.split("/", 2)
            source_to_export[(projects[project] / relative).resolve()] = name
    documents = tuple(source_to_export.items())
    # Source guides often link a complete project directory; open its portable README.
    for name, source in projects.items():
        source_to_export[source.resolve()] = f"examples/{name}/README.md"
    for source, exported in documents:
        if not exported.endswith(".md"):
            continue
        def replace(match):
            target = match.group(1)
            if target.startswith(("#", "http:", "https:", "mailto:")) or " " in target:
                return match.group(0)
            path, mark, anchor = target.partition("#")
            destination = (source.parent / path).resolve()
            if destination in source_to_export:
                target = Path(os.path.relpath(source_to_export[destination], Path(exported).parent)).as_posix()
            else:
                try:
                    target = "https://github.com/OpenGGF/OpenGGF/blob/" + commit + "/" + quote(destination.relative_to(root.resolve()).as_posix())
                except ValueError:
                    return match.group(0)
            return "](" + target + (mark + anchor if mark else "") + ")"
        entries[exported] = re.sub(r"\]\(([^)]+)\)", replace, entries[exported].decode()).encode()


def collect(root, commit, allow_dirty=False, source_dirty=False):
    release = version(root)
    final = "OpenGGF-" + release
    artifacts = {"engine.jar": final + "-jar-with-dependencies.jar", "sdk.jar": final + "-openggf-mod-sdk.jar",
                 "mod-testkit.jar": final + "-mod-testkit.jar", "api-docs.jar": final + "-openggf-mod-sdk-javadoc.jar"}
    entries = {name: normalize_jar((root / "target" / source).read_bytes()) for name, source in artifacts.items()}
    identity = build_identity(entries["engine.jar"], "version.properties")
    for name in ("sdk.jar", "mod-testkit.jar", "api-docs.jar"):
        if build_identity(entries[name], "META-INF/openggf-build.properties") != identity:
            raise ValueError(f"Creator artifacts do not match: {name}")
    verify_testkit(entries["mod-testkit.jar"])
    if identity.get("app.baseVersion") != release or not identity.get("app.commit") or not commit.startswith(identity["app.commit"]):
        raise ValueError("Creator artifacts do not match this checkout's version/commit; rebuild them")
    dirty = identity.get("app.dirty") == "true" or source_dirty
    if dirty and not allow_dirty:
        raise ValueError("Artifact or current kit sources were dirty; commit and rebuild, or use --allow-dirty for a labelled local development kit")
    policy = dict(line.split("=", 1) for line in (root / "mod-api-release-policy.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    for file in sorted((root / "docs/modding").rglob("*")):
        if file.is_file():
            entries["handbook/" + file.relative_to(root / "docs/modding").as_posix()] = file.read_bytes()
    for file in ("build_project.py",):
        entries["tools/" + file] = (root / "tools/modding" / file).read_bytes()
    entries.update(export_examples(root))
    relocate_markdown_links(root, entries, commit)
    entries["ggfmod"] = b'#!/bin/sh\nset -eu\nkit=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec java -cp "$kit/engine.jar:$kit/sdk.jar" com.openggf.tools.modsdk.GgfModCli "$@"\n'
    entries["ggfmod.ps1"] = b'param([Parameter(ValueFromRemainingArguments=$true)][string[]]$CommandArgs)\n& java -cp "$PSScriptRoot/engine.jar$([IO.Path]::PathSeparator)$PSScriptRoot/sdk.jar" com.openggf.tools.modsdk.GgfModCli @CommandArgs\nexit $LASTEXITCODE\n'
    entries["README.md"] = f'# OpenGGF {release} creator kit\n\nMutable unpublished Mod API {policy["currentApi"]} {policy["currentStatus"]}, built at {commit}. Read handbook/getting-started.md. Java 21 and Maven 3.8+; Python 3 for portable examples. No ROMs are included. This kit does not publish or freeze an API baseline.\n\nUse `sh ggfmod init /absolute/project --id my-mod --kind scene --package example.mymod`. Build generated starters with matching absolute engine.jar and sdk.jar properties. Use tools/build_project.py for exported examples. API docs are in api-docs.jar (unzip to browse); testkit support is in mod-testkit.jar.\n'.encode()
    entries["creator-kit.json"] = json.dumps({"formatVersion": 1, "engineVersion": release, "apiVersion": policy["currentApi"], "apiStatus": policy["currentStatus"], "sourceCommit": commit, "sourceDirty": dirty, "sha256": {name: hashlib.sha256(data).hexdigest() for name, data in sorted(entries.items())}}, indent=2, sort_keys=True).encode() + b"\n"
    return entries


def write_zip(entries, output):
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive output prevents accidental replacement of another candidate kit.
    with output.open("xb") as stream, zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if name == "ggfmod" else 0o100644) << 16
            archive.writestr(info, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--allow-dirty", action="store_true", help="label a local development kit built from uncommitted sources")
    args = parser.parse_args()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    source_dirty = bool(subprocess.check_output(["git", "status", "--porcelain", "-z"], cwd=ROOT))
    write_zip(collect(ROOT, commit, args.allow_dirty, source_dirty), args.out)
    print(args.out.resolve())


if __name__ == "__main__":
    main()
