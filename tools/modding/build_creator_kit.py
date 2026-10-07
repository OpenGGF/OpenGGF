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


def export_examples(root):
    entries = {}
    for project in sorted((root / "examples").iterdir()):
        if not (project / "src/main/resources/META-INF/openggf-mod.yaml").is_file():
            continue
        for file in sorted((project / "src/main").rglob("*")):
            if file.is_file():
                entries["examples/" + project.name + "/" + file.relative_to(project).as_posix()] = file.read_bytes()
        if project.name == "hello-scene":
            specimen = project / "src/test/java/hello/HelloSceneIntegrationTest.java"
            if specimen.is_file():
                entries["examples/hello-scene/src/test/java/hello/HelloSceneIntegrationTest.java"] = specimen.read_bytes()
        readme = project / "README.md"
        entries[f"examples/{project.name}/README.md"] = (readme.read_bytes() if readme.exists() else b"") + b'\n\nExported creator project: use `python3 ../../tools/build_project.py .` from this directory, or Maven with matching absolute `-Dopenggf.engine.jar` and `-Dopenggf.sdk.jar` paths. The Python launcher accepts `--run --s1 /absolute/rom --s2 /absolute/rom --s3k /absolute/rom`; it uses target/play for isolated configuration and saves. Only src/main is packaged.\n'
        entries[f"examples/{project.name}/pom.xml"] = project_pom(project.name)
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


def relocate_markdown_links(root, entries, commit):
    """Keep exported links local, and pin checkout-only references to source SHA."""
    source_to_export = {}
    for name in entries:
        if name.startswith("handbook/"):
            source_to_export[(root / "docs/modding" / name.removeprefix("handbook/")).resolve()] = name
        elif name.startswith("examples/"):
            source_to_export[(root / name).resolve()] = name
    for source, exported in source_to_export.items():
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
