# Native Linux friends ZIP

The Linux x64 friends package uses the [default native build recipe](../../native-image/README.md),
including pinned GraalVM Community 25.4.4.1.1+1.1 runtime class loading,
generated API/service retention and the production mod loader. It adds all
maintained example mods and launch shortcuts. It has no separate image flags,
entry point, capability substitution or retention recipe.

The complete ZIP contains all maintained top-level example mods and seven code
samples plus two data samples. Each has an executable shell shortcut. Configuration
expects `s1.gen`, `s2.gen` and `s3k.gen`; ROMs are never packaged. Java is unnecessary
on recipients' machines. The runtime requires Linux x86_64, glibc 2.35 or newer,
an X11/XWayland display and OpenGL drivers. This is an experiment, not a release.

Build with Java 25, Python 3.12+ and bubblewrap on Linux. Output directories must
be new. The rootless builder changes only its owned output directory; the Ubuntu
rootfs avoids imposing a rolling host's newer glibc on friends. Both archives are
verified against upstream SHA-256 values. Ubuntu build packages come from its
22.04 repositories. Inspect the final ELF's required symbol versions as well.

```bash
python3 tools/modding/native-linux/prepare_toolchain.py --output target/linux-toolchain
python3 tools/testing/maven_queue.py -B -Dmse=off -DskipTests -Dlwjgl.core.classifier=unsafe package dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/native-runtime-classpath.txt
engine=$(realpath target/*-jar-with-dependencies.jar)
sdk=$(realpath target/*-openggf-mod-sdk.jar)
python3 tools/modding/native-windows/build_inputs.py --engine-jar "$engine" --sdk-jar "$sdk" --output target/linux-friends-inputs
python3 tools/modding/native-linux/build_linux.py --stage prepare --engine-jar "$engine" --inputs target/linux-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --graal target/linux-toolchain/graalvm/graalvm-community-25.4.4.1.1+1.1 --output target/linux-friends-native
# Use the exclusive queue lane for the compiler heap and its non-heap overhead.
OPENGGF_MAVEN_QUEUE=serial python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=python3 "-Dexec.args=tools/modding/native-linux/build_linux.py --stage compile --engine-jar $engine --inputs target/linux-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --graal target/linux-toolchain/graalvm/graalvm-community-25.4.4.1.1+1.1 --rootfs target/linux-toolchain/ubuntu-rootfs --output target/linux-friends-native"
```

Then run the `qualify` stage with the same artifact arguments and `--roms` followed
by the three **original absolute** S1/S2/S3K paths, plus `--captures` pointing to an
explicit task directory outside the repository. Keep path arguments quoted.
Use `--runtime-rootfs target/linux-toolchain/ubuntu-rootfs` for an Ubuntu 22.04
runtime with no JDK, a cleared environment, Mesa software rendering and only the
distribution, captures and original read-only ROMs mounted. Use an owned isolated display for this legacy X11 qualification; never drive the
shared desktop. Direct default-image qualification can instead select surfaceless EGL. Build classes, Maven libraries
and the GraalVM toolchain are absent from this runtime.
Qualification requires a display. Do not upload ROMs or source-directory saves.
Omitting ROMs explicitly limits qualification to audit/registration and is
recorded by the empty `renderedGameplay` list; it does not qualify gameplay.

The generated member contract covers dormant creator callbacks as well as live
ones. The Linux build also retains engine implementations of `GameModule`,
including anonymous wrappers, because owner-bound dispatch inspects their concrete
methods reflectively. Their members enter the same mandatory startup audit.
Both platform builders also preserve world-policy service implementations and
enable `--service-factories`: the shared generator scans the bytecode of retained
Class-keyed factories with erased `Object` returns, including returned anonymous
implementations. It repeats discovery for new service-bearing classes. Runtime
bootstrap controls exercise the actual prepared catalog and HUD policy dispatch
without ROMs; rendered gameplay remains a separate required check when supplied.
Scene-based examples bootstrap the backing game's initial zone, then open their
scene; the first listed authored zone can be a guarded activity that requires
that scene's retained session. Direct level examples still exercise owned zones.
Direct JDK method, constructor and static-field references also enter the contract,
including dormant callbacks and method handles. Their declaring packages are
preserved automatically. The image explicitly resolves `java.logging` so its
packages are available to preservation as well as the default `java.base` module.
JDK constructors are never resolved through inheritance.
Missing-field and missing-method controls must return ordinary exit 1;
crashes never pass. A separately compiled, runtime-loaded regression mod exercises
record equality/hash/string, pattern-switch and generated-proxy bootstraps,
plus UTF-8 constants/codecs, writer/file copy/atomic replacement and primitive/reference stream operations; it
never enters the image classpath or final distribution. `java.lang.runtime` and
`java.lang.reflect` are explicitly preserved because preservation of `java.lang`
does not include its subpackages. `java.util.stream` is retained separately from
`java.util`; `java.nio.charset` retains the runtime-readable charset constants.
Metadata lookup alone cannot establish executable interface dispatch.
Each mod runs in a new native process, through production
discovery, loaders and owner fault boundaries. Rendered checks exercise scene
menus/debug routes or 600 real gameplay frames, inspect non-flat framebuffers,
reject owner findings/disabled owners, roundtrip registered module state and reopen
scene mods. Sitar's finite music preparation must reach PLAY, with nonzero offline
PCM; a loading or error screen cannot pass as gameplay. These are representative
checks, not full campaign, networking, rewind-world or performance certification.

Qualification writes only owned disposable probe directories. The builder then
reassembles from immutable inputs so caches, saves and generated configuration
cannot enter the ZIP. The ZIP includes the default first-party `bundled/` folder and retains executable bits, engine/third-party licences,
the CE legal notices, source links, exact input hashes and validation scope.
Engine/mod source and experimental native-tool source have separate commit pins;
an image-only correction can reuse unchanged immutable Maven and mod artifacts.

```bash
python3 -m unittest discover -s tools/modding/native-linux -p 'test_*.py' -v
```

Native images use LWJGL's official `unsafe` core classifier for JNI/Unsafe callbacks; the normal JVM build keeps its Java 25 FFM backend.

Linux and Windows share the default `-O2`, 8 GiB G1 compiler heap, three active
processors and two-worker recipe for their 16 GiB standard runners; macOS alone
uses `-Os` and 5 GiB on `macos-15`. `OPENGGF_NATIVE_IMAGE_HEAP` can override the
heap limit for larger reference sets in the exclusive queue lane. Platform
selection and flags live only in `tools/native-image/build.py`.
