#!/usr/bin/env python3
"""Build, test, run or diagnose mod projects using matching candidate artifacts.

Inputs are a project and absolute engine/SDK jar paths. No engine checkout, ROM
links, downloaded assets or Maven publication is needed. Origin: creator readiness
2026-10-07. Runtime configuration lives in target/play and never changes user config.
"""
import argparse
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import zlib


def matching_artifacts(engine, sdk, testkit=None):
    identities = []
    artifacts = [(engine, "version.properties"), (sdk, "META-INF/openggf-build.properties")]
    if testkit:
        artifacts.append((testkit, "META-INF/openggf-build.properties"))
    for artifact, identity_path in artifacts:
        try:
            with zipfile.ZipFile(artifact) as archive:
                identities.append(dict(line.split("=", 1) for line in archive.read(identity_path).decode().splitlines()
                                       if "=" in line and not line.startswith("#")))
        except (OSError, KeyError, ValueError, RuntimeError, zlib.error) as error:
            raise ValueError(f"Cannot read candidate identity from {artifact}: {error}") from error
    if any(identity != identities[0] for identity in identities[1:]):
        raise ValueError("Engine, SDK and optional testkit must come from the same candidate build")
    if not identities[0].get("app.commit") or "${" in identities[0]["app.commit"]:
        raise ValueError("Candidate artifacts must record their source commit")
    return identities[0]


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    return str(Path(home) / "bin" / (name + (".exe" if os.name == "nt" else ""))) if home else name


def runtime_directory(project, roms):
    play = Path(project) / "target/play"
    play.mkdir(parents=True, exist_ok=True)
    config = 'configFormat: 2\nroms:\n  directory: "roms"\n'
    for key in ("sonic1", "sonic2", "sonic3k"):
        value = (roms or {}).get(key)
        if value is not None:
            value = Path(value).resolve(strict=True)
            if not value.is_file():
                raise ValueError(f"ROM is not a file: {value}")
        config += f"  {key}: {json.dumps(str(value) if value else '')}\n"
    (play / "config.yaml").write_text(config, encoding="utf-8")
    return play


def copy_resources(project, classes):
    """Copy authored resources and run an optional trusted project resource generator.

    The generator receives only the output resource root. Generated binary assets
    stay in build output; checkout and artifact-only example builds share this path.
    """
    project, classes = Path(project).resolve(), Path(classes).resolve()
    shutil.copytree(project / "src/main/resources", classes, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("ggfmod.local.json"))
    generator = project / "generate_resources.py"
    if generator.is_file():
        subprocess.run([sys.executable, str(generator), str(classes)], check=True, cwd=project)


def convert_starter_resources(project, classes, cli):
    """Support the SDK starter's authored source convention, as its POM does.

    Custom Maven plugin executions are intentionally left to the Maven workflow.
    Outputs live only in the newly cleaned classes directory.
    """
    source = project / "src/main/mod"
    if (source / "sample.png").is_file():
        output = classes / "art/sample.ggfs"
        output.unlink(missing_ok=True)
        subprocess.run([*cli, "convert", "art", "--image", str(source / "sample.png"),
                        "--sheet", str(source / "sample-sheet.yaml"), "--out", str(output)], check=True)
    if (source / "level-source").is_dir():
        output = classes / "levels/sample"
        if output.exists():
            shutil.rmtree(output)
        subprocess.run([*cli, "convert", "level", "--from-export", str(source / "level-source"),
                        "--out", str(output)], check=True)


def build(project, engine, sdk, *, run=False, roms=None, testkit=None, test=False):
    project, engine, sdk = (Path(p).resolve() for p in (project, engine, sdk))
    for artifact in (engine, sdk):
        if not artifact.is_file():
            raise ValueError(f"Missing matching creator artifact: {artifact}")
    matching_artifacts(engine, sdk, testkit)
    if test and testkit is None:
        raise ValueError("--test requires the matching separate --testkit artifact")
    if not (project / "src/main/resources/META-INF/openggf-mod.yaml").is_file():
        raise ValueError(f"No mod manifest in project: {project}")
    classes = project / "target/classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join(map(str, (engine, sdk)))
    sources = sorted((project / "src/main/java").rglob("*.java"))
    if sources:
        subprocess.run([java_tool("javac"), "--release", "25", "-cp", classpath,
                        "-d", str(classes), *map(str, sources)], check=True)
    copy_resources(project, classes)
    cli = [java_tool("java"), "--enable-native-access=ALL-UNNAMED", "-cp", classpath, "com.openggf.tools.modsdk.GgfModCli"]
    convert_starter_resources(project, classes, cli)
    jar = project / "target" / (project.name + "-mod.jar")
    jar.unlink(missing_ok=True)
    subprocess.run([*cli, "package", "--input", str(classes), "--out", str(jar)], check=True)
    if test:
        test_project(project, engine, sdk, Path(testkit).resolve(), roms=roms)
    if run:
        # This directory is exclusively generated by this launcher; JSON strings
        # are valid YAML strings and safely preserve spaces/Windows separators.
        subprocess.run([*cli, "run", str(classes)], cwd=runtime_directory(project, roms), check=True)
    return jar


def test_project(project, engine, sdk, testkit, *, roms=None):
    """Run actual Jupiter discovery from isolated test output, never production classes."""
    libraries = project / "target/test-libs"
    libraries.mkdir(parents=True, exist_ok=True)
    console = libraries / "junit-platform-console-standalone-1.10.3.jar"
    if not console.is_file():
        subprocess.run(["mvn", "-B", "-ntp", "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy",
                        "-Dartifact=org.junit.platform:junit-platform-console-standalone:1.10.3",
                        "-DoutputDirectory=" + str(libraries)], cwd=libraries, check=True)
    tests = project / "target/test-classes"
    if tests.exists():
        shutil.rmtree(tests)
    tests.mkdir(parents=True)
    classpath = os.pathsep.join(map(str, (tests, project / "target/classes", engine, sdk, testkit, console)))
    sources = sorted((project / "src/test/java").rglob("*.java"))
    if sources:
        subprocess.run([java_tool("javac"), "--release", "25", "-cp", classpath,
                        "-d", str(tests), *map(str, sources)], check=True)
    resources = project / "src/test/resources"
    if resources.is_dir():
        shutil.copytree(resources, tests, dirs_exist_ok=True)
    properties = []
    for game, property_name in (("sonic1", "sonic1.rom.path"), ("sonic2", "sonic2.rom.path"),
                                ("sonic3k", "s3k.rom.path")):
        if (roms or {}).get(game) is not None:
            properties.append("-D" + property_name + "=" + str(Path(roms[game]).resolve(strict=True)))
    subprocess.run([java_tool("java"), "--enable-native-access=ALL-UNNAMED", "-Xmx1g", *properties, "-cp", classpath,
                    "com.openggf.mods.testing.CreatorTestLauncher", "execute",
                    "--scan-class-path=" + str(tests), "--include-classname=.*",
                    "--include-engine=junit-jupiter", "--details=summary", "--disable-ansi-colors",
                    "--fail-if-no-tests"], cwd=project, check=True)


LOCAL_CONFIG = "ggfmod.local.json"
PATH_KEYS = ("engine", "sdk", "testkit", "s1", "s2", "s3k")
LIMITATIONS = [
    "File availability and candidate identity only; no gameplay correctness is implied.",
    "Manifest inspection checks presence/nonempty text only; use ggfmod validate for semantic validation.",
    "ROM availability is checked, not ROM identity or required game/donor coverage.",
    "Maven availability is checked without executing it; dependency resolution/network access is not tested.",
]


def load_config(path, *, required=False):
    """Read a strict local-only schema; never create or rewrite personal settings."""
    if not path.exists() and not required:
        return {}
    def strict_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate configuration key: {key}")
            result[key] = value
        return result
    data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=strict_object)
    if not isinstance(data, dict):
        raise ValueError("Local configuration must be a JSON object")
    if type(data.get("schemaVersion")) is not int or data["schemaVersion"] != 1:
        raise ValueError("Local configuration requires schemaVersion: 1")
    unknown = set(data) - {"schemaVersion", *PATH_KEYS}
    if unknown:
        raise ValueError("Unknown local configuration keys: " + ", ".join(sorted(unknown)))
    result = {}
    for key in PATH_KEYS:
        if key in data:
            if not isinstance(data[key], str) or not data[key].strip():
                raise ValueError(f"Configuration {key} must be a nonempty path string")
            result[key] = (path.parent / data[key]).resolve()
    return result


def finding(code, message, path=None, guidance="", severity="error"):
    return dict(code=code, severity=severity, message=message,
                path=str(path) if path is not None else None, guidance=guidance)


def doctor(project, engine, sdk, *, test=False, testkit=None, roms=None):
    """Read-only preflight: no build, install, launch, download or directory creation."""
    findings = []
    for tool in ("java", "javac", "jar"):
        executable = java_tool(tool)
        try:
            probe = subprocess.run([executable, "--version"], capture_output=True, text=True, timeout=10)
            version = re.search(r"(?:java|openjdk|javac|jar)\s+(?:version\s+)?[\"]?(\d+)",
                                probe.stdout + probe.stderr)
            if probe.returncode or not version or version.group(1) != "25":
                raise ValueError("expected Java 25")
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            findings.append(finding("jdk.tool", f"{tool}: {error}", executable,
                                    "Install a Java 25 JDK; set JAVA_HOME to its root."))
    manifest = project / "src/main/resources/META-INF/openggf-mod.yaml"
    try:
        if not manifest.read_text(encoding="utf-8").strip():
            raise ValueError("manifest is empty")
    except (OSError, ValueError) as error:
        findings.append(finding("project.manifest", str(error), manifest,
                                "Select a mod project containing src/main/resources/META-INF/openggf-mod.yaml."))
    artifacts = [("engine", engine), ("sdk", sdk)]
    # An explicitly supplied testkit is verified even without --test, as before.
    if testkit is not None:
        artifacts.append(("testkit", testkit))
    if test and testkit is None:
        findings.append(finding("testkit.required", "Tests require a matching testkit.", guidance="Set testkit in local config or pass --testkit."))
    missing = False
    for name, path in artifacts:
        if not path.is_file():
            missing = True
            findings.append(finding("artifact.missing", f"Missing {name} artifact", path,
                                    f"Supply --{name} from the same creator kit."))
    if not missing:
        try:
            matching_artifacts(engine, sdk, testkit)
        except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
            findings.append(finding("artifact.identity", str(error), engine,
                                    "Use engine, SDK and testkit from the same candidate build."))
    for game, path in (roms or {}).items():
        if path is not None and not path.is_file():
            findings.append(finding("rom.missing", f"Supplied {game} ROM is unavailable", path,
                                    "Correct the supplied path to your original ROM; ROMs are never downloaded."))
    console = project / "target/test-libs/junit-platform-console-standalone-1.10.3.jar"
    if test and not console.is_file() and shutil.which("mvn") is None:
        findings.append(finding("test.maven", "JUnit Console is not cached and Maven is unavailable.", console,
                                "Install Maven 3.8+ to resolve JUnit when running --test."))
    if test and not any((project / "src/test/java").rglob("*.java")):
        findings.append(finding("test.sources", "No Java test sources found.", project / "src/test/java",
                                "Add Jupiter tests; empty discovery fails."))
    return findings


def report(findings, output_format):
    ok = not any(item["severity"] == "error" for item in findings)
    result = dict(formatVersion=1, command="doctor", result="pass" if ok else "fail",
                  findings=findings, limitations=LIMITATIONS)
    if output_format == "json":
        print(json.dumps(result, sort_keys=True))
    else:
        print("Doctor: " + result["result"])
        for item in findings:
            print(f"{item['severity']} {item['code']}: {item['message']}"
                  + (f" [{item['path']}]" if item['path'] else "")
                  + f"\n  {item['guidance']}")
        for limitation in LIMITATIONS:
            print("Limit: " + limitation)
    return 0 if ok else 1


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    kit = Path(__file__).resolve().parent.parent
    parser.add_argument("--engine", type=Path)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--config", type=Path, help="explicit local JSON config (default: PROJECT/ggfmod.local.json)")
    parser.add_argument("--doctor", action="store_true", help="read-only prerequisites and candidate identity checks")
    parser.add_argument("--format", choices=("text", "json"), default="text")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--test", action="store_true", help="compile and run the authored Jupiter tests")
    parser.add_argument("--testkit", type=Path, help="verify an optional matching testkit before local test setup")
    parser.add_argument("--verify-only", action="store_true", help="verify artifact version/source identity without compiling")
    for name in ("s1", "s2", "s3k"):
        parser.add_argument("--" + name, type=Path)
    config_path = None
    try:
        args = parser.parse_args(argv)
        if args.doctor and args.verify_only:
            raise ValueError("Use --doctor or --verify-only, not both")
        project = args.project.resolve()
        config_path = args.config.resolve() if args.config else project / LOCAL_CONFIG
        config = load_config(config_path, required=args.config is not None)
        values = {key: (getattr(args, key).resolve() if getattr(args, key) is not None else config.get(key))
                  for key in PATH_KEYS}
        engine, sdk = values["engine"] or kit / "engine.jar", values["sdk"] or kit / "sdk.jar"
        roms = dict(zip(("sonic1", "sonic2", "sonic3k"), (values["s1"], values["s2"], values["s3k"])))
        if args.verify_only:
            print(json.dumps(matching_artifacts(engine, sdk, values["testkit"]), sort_keys=True))
            return 0
        findings = doctor(project, engine, sdk, test=args.test, testkit=values["testkit"], roms=roms)
        if args.doctor:
            return report(findings, args.format)
        if findings:
            report(findings, "text")
            return 1
        print(build(project, engine, sdk, run=args.run, roms=roms, testkit=values["testkit"], test=args.test))
        return 0
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        if "--doctor" in argv:
            return report([finding("config.invalid" if config_path else "arguments.invalid", str(error), config_path,
                                   "Check arguments and local config paths/schema; use --help.")],
                          "json" if "--format=json" in argv or any(
                              argv[i:i + 2] == ["--format", "json"] for i in range(len(argv))) else "text")
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
