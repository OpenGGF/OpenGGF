#!/usr/bin/env python3
"""Linux external-JAR control; origin: 2026-10-08 GraalVM mod feasibility.

Inputs: an explicitly supplied GraalVM home and Java 21 commands on PATH.
Uses the unchanged production classloader. No downloads, Maven invocation,
production policy edits, ROM access or gameplay certification. Build outputs
and logs live in an owned target temporary directory and are removed on exit.
"""

import argparse
import hashlib
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import tempfile
import time


def run(label, command, directory, *, rejected=False, expected_failure=None):
    started = time.monotonic()
    log = directory / f"{label}.log"
    with log.open("w") as output:
        process = subprocess.Popen([str(value) for value in command], stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            process.wait(timeout=600)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            # Native Image may launch a builder JVM. Stop only this invocation's
            # process group before its temporary directory is removed.
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                pass
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
            raise
    text = log.read_text(errors="replace")
    if expected_failure is not None:
        valid = process.returncode != 0 and expected_failure in text
    elif rejected:
        valid = process.returncode != 0 and "ClassNotFoundException: external.Plugin" in text
    else:
        valid = process.returncode == 0
    if not valid:
        lines = text.splitlines()
        raise RuntimeError(f"{label} exited {process.returncode}:\n"
                           + "\n".join(lines if len(lines) < 20 else lines[:8] + lines[-12:]))
    print(f"PASS {label}: exit {process.returncode}, {time.monotonic() - started:.1f}s", flush=True)
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--graalvm-home", required=True, type=Path)
    parser.add_argument("--jit", action="store_true", help="also build the optional JIT-enabled image")
    args = parser.parse_args()
    if sys.platform != "linux":
        parser.error("This bounded reproducer has only been qualified on Linux")
    version = subprocess.check_output(["javac", "-version"], text=True).strip()
    if not version.startswith("javac 21."):
        parser.error(f"Use Java 21 javac on PATH, found {version}")
    native_image = args.graalvm_home.resolve() / "bin/native-image"
    print(subprocess.check_output([native_image, "--version"], text=True).strip(), flush=True)
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    sources = Path(__file__).resolve().parent
    root = sources.parents[2]
    (root / "target").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="native-mod-probe-", dir=root / "target") as temporary:
        work = Path(temporary)
        host = work / "host"
        host.mkdir()
        run("compile-host", ["javac", "--release", "21", "-d", host,
            sources / "NativeModLoadingProbe.java",
            root / "src/main/java/com/openggf/mods/code/ModDependencyClassLoader.java"], work)
        metadata = work / "metadata"
        metadata.mkdir()
        (metadata / "reflect-config.json").write_text(
            '[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]\n')
        common = [native_image, "-march=compatibility", "-J-Xmx3g", "--parallelism=4",
                  "-H:+UnlockExperimentalVMOptions",
                  "-H:Preserve=package=com.openggf.tools,package=java.lang,"
                  "package=java.lang.invoke,package=java.util.function",
                  f"-H:ConfigurationFileDirectories={metadata}", "-cp", host]
        modes = [("closed", []), ("crema", ["-H:+RuntimeClassLoading"])]
        if args.jit:
            modes.append(("jit", ["-H:+RuntimeClassLoading", "-H:+GraalJITCompileAtRuntime"]))
        for name, flags in modes:
            print(f"Building {name} control (600s timeout, 3 GiB heap, 4 threads)", flush=True)
            run(f"build-{name}", common + flags + [
                "com.openggf.tools.NativeModLoadingProbe", work / name], work)
            print(f"{name} binary: {(work / name).stat().st_size} bytes", flush=True)
        # Neither fixture exists until every native image has been built.
        image = work / "crema"
        before = hashlib.file_digest(image.open("rb"), "sha256").hexdigest()
        for marker in ("version-one", "version-two"):
            fixture = work / marker
            fixture.mkdir()
            java = fixture / "Plugin.java"
            java.write_text((sources / "Plugin.java").read_text().replace("version-one", marker))
            classes = fixture / "classes"
            run(f"compile-{marker}", ["javac", "--release", "21", "-cp", host,
                "-d", classes, java], work)
            jar = fixture / "plugin.jar"
            run(f"package-{marker}", ["jar", "--create", "--file", jar, "-C", classes, "."], work)
            run(f"jvm-{marker}", ["java", "-cp", host, "com.openggf.tools.NativeModLoadingProbe",
                jar, f"{marker}:23"], work)
            for name, _ in modes:
                output = run(f"{name}-{marker}", [work / name, jar, f"{marker}:23"], work,
                             rejected=name == "closed")
                if name != "closed" and f"PASS: {marker}:23; owner=mod:external" not in output:
                    raise AssertionError(f"Missing callback/ownership evidence in {name}")
        after = hashlib.file_digest(image.open("rb"), "sha256").hexdigest()
        if before != after:
            raise AssertionError("Native executable changed between JAR versions")
        print("PASS external JAR replacement with unchanged native executable", flush=True)


if __name__ == "__main__":
    main()
