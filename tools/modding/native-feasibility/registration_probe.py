#!/usr/bin/env python3
"""Linux native API-retention experiment; origin: 2026-10-08 mod follow-up.

Inputs: an explicit CE GraalVM home, the unmodified universal engine JAR,
trusted packaged mods, and Java 21 on PATH. Generates retention from the
canonical API and every mod's static engine references, audits member metadata
before loading code, and checks each package separately on JVM and native.
No Maven, downloads, production policy edits, ROM access or gameplay claim.
All generated files/logs and initialized configuration are removed on exit.
"""

import argparse
import os
from pathlib import Path
import resource
import subprocess
import sys
import tempfile
import zipfile

from probe import run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--graalvm-home", required=True, type=Path)
    parser.add_argument("--engine-jar", required=True, type=Path)
    parser.add_argument("--mods", required=True, type=Path)
    parser.add_argument("--check-pruning", action="store_true",
                        help="also rebuild the old incomplete image and require SONIC_2/method pruning to be detected")
    args = parser.parse_args()
    if sys.platform != "linux":
        parser.error("This experiment has only been qualified on Linux x86_64")
    version = subprocess.check_output(["javac", "-version"], text=True).strip()
    if not version.startswith("javac 21."):
        parser.error(f"Use Java 21 javac on PATH, found {version}")
    native_image = args.graalvm_home.resolve() / "bin/native-image"
    engine = args.engine_jar.resolve(strict=True)
    mods = args.mods.resolve(strict=True)
    sources = Path(__file__).resolve().parent
    root = sources.parents[2]
    print(subprocess.check_output([native_image, "--version"], text=True).strip(), flush=True)
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    (root / "target").mkdir(exist_ok=True)
    previous = Path.cwd()
    with tempfile.TemporaryDirectory(prefix="native-mod-registration-", dir=root / "target") as temporary:
        work = Path(temporary)
        # Standalone registration can initialize services/configuration. Isolate even failures.
        os.chdir(work)
        try:
            classes = work / "classes"
            run("compile", ["javac", "--release", "21", "-cp", engine, "-d", classes,
                sources / "NativeModMemberContract.java", sources / "NativeModMemberAudit.java",
                sources / "NativeModRegistrationProbe.java", sources / "NativeModMemberContractTest.java"], work)
            classpath = f"{classes}:{engine}"
            print(run("contract-tests", ["java", "-cp", classpath,
                "com.openggf.tools.NativeModMemberContractTest", work / "contract-tests"], work).strip(), flush=True)
            contract = work / "contract"
            run("contract", ["java", "-cp", classpath, "com.openggf.tools.NativeModMemberContract",
                engine, mods, contract], work)
            print(run("jvm-audit", ["java", "-cp", classpath,
                "com.openggf.tools.NativeModMemberAudit", contract / "members.tsv"], work).strip(), flush=True)
            metadata = work / "metadata"
            metadata.mkdir()
            (metadata / "reflect-config.json").write_text(
                '[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]\n')
            libs = work / "native-libs"
            libs.mkdir()
            with zipfile.ZipFile(engine) as jar:
                for name in jar.namelist():
                    if name.startswith("linux/x64/org/lwjgl/") and name.endswith(".so"):
                        (libs / Path(name).name).write_bytes(jar.read(name))
            if not list(libs.glob("*.so")):
                raise ValueError("Universal JAR contains no Linux x64 LWJGL libraries")
            image = work / "registration"
            print("Building native contract (600s timeout, 5 GiB heap, 4 threads)", flush=True)
            run("native-build", [native_image, "-march=compatibility", "-J-Xmx5g", "--parallelism=4",
                "--exclude-config", ".*", "META-INF/native-image/org.xerial/sqlite-jdbc/.*",
                r"-H:IncludeResources=com/openggf/.*\.class", "-H:+UnlockExperimentalVMOptions",
                "-H:+RuntimeClassLoading", f"-H:Preserve=path={contract / 'preserved-engine.jar'},"
                "package=java.lang,package=java.lang.invoke,package=java.util,package=java.util.function",
                f"-H:ConfigurationFileDirectories={metadata}", "-cp",
                f"{classes}:{contract / 'preserved-engine.jar'}:{engine}",
                "com.openggf.tools.NativeModRegistrationProbe", image], work)
            print(run("native-audit", [image, "--audit", contract / "members.tsv"], work).strip(), flush=True)
            # Require the audit to reject absent fields AND methods without entering registration.
            for kind, desc in (("F", "I"), ("M", "()V")):
                invalid = work / f"missing-{kind}.tsv"
                invalid.write_text(f"{kind}\tcom.openggf.game.rules.GameRules\t__absent_control\t{desc}\n")
                run(f"reject-{kind}", [image, "--audit", invalid], work,
                    expected_failure="MISSING native member: " + kind)
            listing = run("list", ["java", "-cp", classpath,
                "com.openggf.tools.NativeModRegistrationProbe", "--list", mods], work)
            owners = [line.split("\t", 1)[1] for line in listing.splitlines() if line.startswith("OWNER\t")]
            if not owners or len(set(owners)) != len(owners):
                raise ValueError("Expected nonempty unique mod IDs")
            for index, owner in enumerate(owners):
                for label, command in (
                    ("jvm", ["java", f"-Dorg.lwjgl.librarypath={libs}", "-cp", classpath,
                             "com.openggf.tools.NativeModRegistrationProbe"]),
                    ("native", [image, f"-Dorg.lwjgl.librarypath={libs}"]),
                ):
                    output = run(f"{label}-{index}", command + ["--members", contract / "members.tsv", mods, owner], work)
                    if f"PASS registration: {owner};" not in output:
                        raise AssertionError(f"Missing registration evidence for {owner}")
                    print(f"PASS {label} registration: {owner}", flush=True)
            print(f"PASS registrations: {len(owners)} JVM and {len(owners)} native; metadata preflight passed", flush=True)
            if args.check_pruning:
                # Exact original package selection: no game.rules and no generated retention path.
                pruned = work / "pruned"
                print("Building historical pruning control (600s timeout, 5 GiB heap, 4 threads)", flush=True)
                run("pruned-build", [native_image, "-march=compatibility", "-J-Xmx5g", "--parallelism=4",
                    "--exclude-config", ".*", "META-INF/native-image/org.xerial/sqlite-jdbc/.*",
                    r"-H:IncludeResources=com/openggf/.*\.class", "-H:+UnlockExperimentalVMOptions",
                    "-H:+RuntimeClassLoading", "-H:Preserve=package=com.openggf.mods.code,"
                    "package=com.openggf.mods.scene,package=com.openggf.mods.scene.*,package=com.openggf.io,"
                    "package=com.openggf.game,package=com.openggf.game.patch,package=com.openggf.game.presentation,"
                    "package=com.openggf.level,package=com.openggf.level.objects,package=com.openggf.level.rings,"
                    "package=com.openggf.sprites.playable,package=java.lang,package=java.lang.invoke,"
                    "package=java.util,package=java.util.function", f"-H:ConfigurationFileDirectories={metadata}",
                    "-cp", classpath, "com.openggf.tools.NativeModRegistrationProbe", pruned], work)
                for kind, name, desc in (("F", "SONIC_2", "Lcom/openggf/game/rules/GameRules;"),
                                         ("M", "camera", "()Lcom/openggf/game/rules/CameraRules;")):
                    omitted = work / f"pruned-{kind}.tsv"
                    omitted.write_text(f"{kind}\tcom.openggf.game.rules.GameRules\t{name}\t{desc}\n")
                    run(f"detect-pruned-{kind}", [pruned, "--audit", omitted], work,
                        expected_failure=f"MISSING native member: {kind}\tcom.openggf.game.rules.GameRules\t{name}")
                print("PASS historical pruning detected before registration, without native abort", flush=True)
        finally:
            os.chdir(previous)


if __name__ == "__main__":
    main()
