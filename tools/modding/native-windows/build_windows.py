#!/usr/bin/env python3
"""Build and qualify the experimental Windows x64 friends ZIP (2026-10-08).

Inputs: Java 21 Maven artifacts, source-built mods and the checksum-pinned CE
GraalVM below. Run on an MSVC-enabled Windows host. Does not publish a release,
change production native policy, download ROMs or write user configuration.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import urllib.request
import zipfile

from build_inputs import ROOT, tool

GRAAL_VERSION = "25.4.4.1.1+1.1"
GRAAL_SHA256 = "2733ea1331f1a98b05dd55a768b07347051dad48f030dbbfa191b11a2ccf4144"
GRAAL_URL = ("https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.4.4.1.1/"
             "graalvm-community-jdk-25i4-25.0.4.1.1_windows-x64_bin.zip")
MAIN = "com.openggf.tools.nativewindows.ExperimentalNativeEngine"


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def run(command, cwd, expected=None):
    result = subprocess.run(list(map(str, command)), cwd=cwd, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800)
    print(result.stdout, end="", flush=True)
    if expected is None:
        if result.returncode != 0: raise RuntimeError(f"Command failed ({result.returncode}): {command[0]}")
    elif result.returncode != 1 or expected not in result.stdout:
        raise AssertionError(f"Expected ordinary exit 1 and {expected!r}, found {result.returncode}")
    return result.stdout


def extract_sdk(graal, destination):
    # The SDK jmod contains Java-11-compatible hosted APIs/annotations. Compile
    # these extra sources with Java 21, without changing Maven's toolchain.
    with zipfile.ZipFile(graal / "jmods/org.graalvm.nativeimage.jmod") as module:
        with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as jar:
            for name in module.namelist():
                if name.startswith("classes/") and name.endswith(".class"):
                    jar.writestr(name.removeprefix("classes/"), module.read(name))


def extract_jar(archive, directory):
    with zipfile.ZipFile(archive) as jar:
        for entry in jar.infolist():
            relative = Path(entry.filename)
            if relative.is_absolute() or ".." in relative.parts: raise ValueError("Unsafe JAR path")
            jar.extract(entry, directory)


def pe_x64(path):
    data = Path(path).read_bytes()
    if data[:2] != b"MZ": raise ValueError("Missing Windows PE header: " + str(path))
    offset = struct.unpack_from("<I", data, 60)[0]
    if data[offset:offset + 4] != b"PE\0\0" or struct.unpack_from("<H", data, offset + 4)[0] != 0x8664:
        raise ValueError("Expected AMD64 Windows binary: " + str(path))


def assemble(bundle, image, engine, inputs, contract, graal, evidence):
    """Assemble exclusively owned inputs; also exercised by the fixture tests."""
    bundle.mkdir(parents=True, exist_ok=False)
    shutil.copy2(image, bundle / "OpenGGF.exe")
    pe_x64(bundle / "OpenGGF.exe")
    for dll in image.parent.glob("*.dll"):
        pe_x64(dll)
        shutil.copy2(dll, bundle / dll.name)
    with zipfile.ZipFile(engine) as jar:
        dlls = [name for name in jar.namelist() if name.startswith("windows/x64/org/lwjgl/") and name.endswith(".dll")]
        if len(dlls) < 5: raise ValueError("Missing Windows x64 LWJGL libraries")
        for name in dlls:
            target = bundle / Path(name).name
            target.write_bytes(jar.read(name))
            pe_x64(target)
    catalog = json.loads((inputs / "build-info.json").read_text())
    shutil.copytree(inputs / "mods", bundle / "mods")
    shutil.copy2(contract / "members.tsv", bundle / "native-mod-members.tsv")
    catalog.update({"experimental": True, "platform": "windows-x64", "graalVersion": GRAAL_VERSION,
                    "graalArchiveSha256": GRAAL_SHA256, "nativeSha256": digest(image), "validation": evidence})
    (bundle / "build-info.json").write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    (bundle / "config.yaml").write_text(
        '# Put your own ROMs beside this file.\nroms:\n  sonic1: "s1.gen"\n  sonic2: "s2.gen"\n'
        '  sonic3k: "s3k.gen"\n  directory: "."\n  default: "s2"\nstartup:\n'
        '  titleScreen: true\n  masterTitleScreen: true\n  legalDisclaimer: true\n', encoding="utf-8")
    launcher = ('@echo off\r\nsetlocal\r\npushd "%~dp0"\r\n'
                'if "%~1"=="" (\r\n  OpenGGF.exe\r\n) else (\r\n'
                '  OpenGGF.exe "-Dggfmod.dev.modDir=%~dp0quick-mods\\%~1"\r\n)\r\n'
                'set "result=%errorlevel%"\r\npopd\r\n'
                'if not "%result%"=="0" pause\r\nexit /b %result%\r\n')
    (bundle / "_launch.bat").write_bytes(launcher.encode("ascii"))
    (bundle / "OpenGGF.bat").write_bytes(b'@echo off\r\ncall "%~dp0_launch.bat"\r\n')
    for mod in catalog["mods"]:
        slug = mod["slug"]
        if not slug or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in slug):
            raise ValueError("Unsafe launcher slug: " + slug)
        jar = bundle / "mods" / (slug + ".jar")
        if digest(jar) != mod["jarSha256"]: raise ValueError("Mod hash drift: " + slug)
        extract_jar(jar, bundle / "quick-mods" / slug)
        (bundle / ("Launch " + slug + ".bat")).write_bytes(
            ('@echo off\r\ncall "%~dp0_launch.bat" "' + slug + '"\r\n').encode("ascii"))
    shutil.copy2(ROOT / "LICENSE", bundle / "LICENSE")
    shutil.copytree(ROOT / "LICENSES", bundle / "LICENSES")
    for name in ("CREDITS.md", "NOTICE.md"):
        if (ROOT / name).is_file(): shutil.copy2(ROOT / name, bundle / name)
    notices = bundle / "LICENSES/GraalVM"
    notices.mkdir()
    for name in ("LICENSE.txt", "THIRD_PARTY_LICENSE.txt"):
        shutil.copy2(graal / name, notices / name)
    shutil.copytree(graal / "legal", notices / "legal")
    (bundle / "README.txt").write_text(
        "OpenGGF experimental native Windows x64 build\n\n"
        "Extract the entire ZIP, then double-click OpenGGF.bat or a Launch *.bat shortcut.\n"
        "Java is not required. Keep OpenGGF.exe, the DLLs and folders together.\n"
        "Use a current Windows x64 system and compatible OpenGL graphics driver.\n"
        "If Windows reports a missing VCRUNTIME140/MSVCP140 DLL, install Microsoft's\n"
        "supported x64 Visual C++ runtime: https://aka.ms/vc14/vc_redist.x64.exe\n"
        "For Sonic games and ROM-dependent samples supply your own s1.gen, s2.gen and s3k.gen\n"
        "beside config.yaml. Authored standalone examples can run without ROMs.\n"
        "Shortcuts select a development mod directory; the normal launcher shows the engine menus.\n"
        "Use the game's prompts for controls and any code-mod trust confirmation.\n\n"
        "This uses experimental GraalVM runtime class loading. All bundled mods passed the\n"
        "member audit and Windows native validation/registration; full gameplay and performance\n"
        "of every mod are not certified. The usual released native engine policy is unchanged.\n"
        "Run OpenGGF.exe --audit or --check-mods for bounded diagnostic checks.\n\n"
        "OpenGGF is distributed under GNU GPL version 3. Corresponding engine, mod and\n"
        "experimental build source for this exact build:\n"
        f"https://github.com/OpenGGF/OpenGGF/tree/{catalog['sourceCommit']}\n"
        "GraalVM Community source and upstream release:\n"
        "https://github.com/oracle/graal/tree/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b\n"
        "https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1\n"
        "Compiler/runtime and third-party notices are retained under LICENSES.\n",
        encoding="utf-8")


def archive_bundle(bundle, output):
    if any(bundle.rglob("*.gen")): raise ValueError("ROM files must not enter a friends bundle")
    paths = sorted(path for path in bundle.rglob("*") if path.is_file())
    (bundle / "SHA256SUMS.txt").write_text("".join(
        f"{digest(path)}  {path.relative_to(bundle).as_posix()}\n" for path in paths), encoding="utf-8")
    with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(bundle.rglob("*")):
            if path.is_file(): archive.write(path, Path(bundle.name) / path.relative_to(bundle))
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None: raise ValueError("ZIP CRC verification failed")
    output.with_suffix(".zip.sha256").write_text(f"{digest(output)}  {output.name}\n", encoding="ascii")


def build(args):
    if os.name != "nt": raise ValueError("Compile on Windows with MSVC and the Windows SDK")
    work = args.output.resolve()
    work.mkdir(parents=True, exist_ok=False)
    engine, inputs = args.engine_jar.resolve(strict=True), args.inputs.resolve(strict=True)
    catalog = json.loads((inputs / "build-info.json").read_text())
    if digest(engine) != catalog["engineSha256"]: raise ValueError("Engine/mod input identity changed")
    archive = work / "graalvm.zip"
    urllib.request.urlretrieve(GRAAL_URL, archive)
    if digest(archive) != GRAAL_SHA256: raise ValueError("GraalVM publisher checksum mismatch")
    extract_jar(archive, work / "toolchain")
    graal = work / "toolchain" / ("graalvm-community-" + GRAAL_VERSION)
    sdk = work / "hosted-sdk.jar"
    extract_sdk(graal, sdk)
    classes = work / "host-classes"
    sources = sorted((ROOT / "tools/modding/native-feasibility").glob("NativeModMember*.java"))
    sources += [ROOT / "tools/modding/native-feasibility/NativeModRegistrationProbe.java"]
    sources += sorted(Path(__file__).parent.glob("*.java"))
    run([tool("javac"), "--release", "21", "-cp", os.pathsep.join(map(str, (engine, sdk))),
         "-d", classes, *sources], work)
    classpath = os.pathsep.join(map(str, (classes, engine)))
    java = [tool("java"), "-cp", classpath]
    run([*java, "com.openggf.tools.NativeModMemberContractTest", work / "fixtures"], work)
    contract = work / "contract"
    run([*java, "com.openggf.tools.NativeModMemberContract", engine, inputs / "mods", contract], work)
    run([*java, "com.openggf.tools.NativeModMemberAudit", contract / "members.tsv"], work)
    metadata = work / "metadata"
    metadata.mkdir()
    (metadata / "reflect-config.json").write_text(
        '[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]\n')
    deps = args.runtime_classpath.resolve(strict=True).read_text().strip()
    native_classpath = os.pathsep.join(map(str, (classes, contract / "preserved-engine.jar",
                                               ROOT / "target/classes"))) + os.pathsep + deps
    native = work / "native"
    native.mkdir()
    arguments = ["-march=compatibility", "-J-Xmx5g", "--parallelism=4",
        "--initialize-at-run-time=org.lwjgl,java.awt,javax.swing,sun.awt,sun.java2d",
        "-H:+UnlockExperimentalVMOptions", "-H:+RuntimeClassLoading",
        f"-H:Preserve=path={contract / 'preserved-engine.jar'},package=java.lang,package=java.lang.invoke,package=java.util,package=java.util.function",
        r"-H:IncludeResources=com/openggf/.*\.class", f"-H:ConfigurationFileDirectories={metadata}",
        "-J-Dopenggf.experimental.native.mods=true",
        "-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.hub=ALL-UNNAMED",
        "-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.shared.option=ALL-UNNAMED",
        "--features=com.openggf.tools.nativewindows.ExperimentalNativeFeature",
        "-cp", native_classpath, MAIN, str(native / "OpenGGF")]
    argfile = work / "native-image.args"
    argfile.write_text("\n".join(json.dumps(arg) for arg in arguments), encoding="utf-8")
    run([graal / "bin/native-image.cmd", "@" + str(argfile)], native)
    image = native / "OpenGGF.exe"
    # Use the final bundle layout during qualification. Package only after every
    # actual Windows process has succeeded; a crash is never a passing rejection.
    evidence = {"memberAudit": "pending", "registrations": [], "engineBootRegistrations": [],
                "gameplay": "not certified"}
    bundle = work / "OpenGGF-experimental-windows-x64-with-mods"
    assemble(bundle, image, engine, inputs, contract, graal, evidence)
    run([bundle / "OpenGGF.exe", "--audit"], bundle)
    original = (bundle / "native-mod-members.tsv").read_bytes()
    for kind, desc in (("F", "I"), ("M", "()V")):
        try:
            (bundle / "native-mod-members.tsv").write_text(
                f"{kind}\tcom.openggf.game.rules.GameRules\t__absent_control\t{desc}\n")
            run([bundle / "OpenGGF.exe", "--audit"], bundle, "MISSING native member: " + kind)
        finally:
            (bundle / "native-mod-members.tsv").write_bytes(original)
    for mod in catalog["mods"]:
        owner = mod["id"]
        for command, cwd in (([*java, "com.openggf.tools.NativeModRegistrationProbe", inputs / "mods", owner], work),
                             ([bundle / "OpenGGF.exe", "--check-mods", owner], bundle)):
            output = run(command, cwd)
            if f"PASS registration: {owner};" not in output: raise AssertionError("Missing registration result: " + owner)
        evidence["registrations"].append(owner)
        with zipfile.ZipFile(inputs / "mods" / (mod["slug"] + ".jar")) as jar:
            contains_code = any(name.endswith(".class") for name in jar.namelist())
        if contains_code:
            output = run([bundle / "OpenGGF.exe",
                f"-Dggfmod.dev.modDir={bundle / 'quick-mods' / mod['slug']}", "--check-engine", owner], bundle)
            if f"PASS engine native boot registration: {owner}" not in output:
                raise AssertionError("Missing actual engine boot result: " + owner)
            evidence["engineBootRegistrations"].append(owner)
    evidence["memberAudit"] = "passed; missing field and method controls rejected with exit 1"
    # Reassemble from immutable inputs, excluding every probe-created mutable
    # config/service/cache output, including future new ones. This whole folder
    # belongs to this build; no user-authored input is ever in it.
    shutil.rmtree(bundle)
    assemble(bundle, image, engine, inputs, contract, graal, evidence)
    artifact = work / (bundle.name + ".zip")
    archive_bundle(bundle, artifact)
    print(f"PASS Windows native ZIP: {artifact}; {len(catalog['mods'])} JVM/native registrations; SHA256 {digest(artifact)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine-jar", required=True, type=Path)
    parser.add_argument("--inputs", required=True, type=Path)
    parser.add_argument("--runtime-classpath", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    build(parser.parse_args())
