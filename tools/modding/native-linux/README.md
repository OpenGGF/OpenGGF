# Experimental native Linux friends ZIP

The Linux x64 image uses checksum-pinned GraalVM Community 25.4.4.1.1+1.1
runtime class loading. It shares the isolated hosted feature and capability
substitution from [the Windows experiment](../native-windows/README.md).
Normal engine, Maven native and release policy are unchanged.

The complete ZIP contains all maintained top-level example mods and seven code
samples plus two data samples. Each has an executable shell shortcut. Configuration
expects `s1.gen`, `s2.gen` and `s3k.gen`; ROMs are never packaged. Java is unnecessary
on recipients' machines. The runtime requires Linux x86_64, glibc 2.35 or newer,
an X11/XWayland display and OpenGL drivers. This is an experiment, not a release.

Build with Java 21, Python 3.12+ and bubblewrap on Linux. Output directories must
be new. The rootless builder changes only its owned output directory; the Ubuntu
rootfs avoids imposing a rolling host's newer glibc on friends. Both archives are
verified against upstream SHA-256 values. Ubuntu build packages come from its
22.04 repositories. Inspect the final ELF's required symbol versions as well.

```bash
python3 tools/modding/native-linux/prepare_toolchain.py --output target/linux-toolchain
python3 tools/testing/maven_queue.py -B -Dmse=off -DskipTests -Puniversal-jar package dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/native-runtime-classpath.txt
engine=$(realpath target/*-jar-with-dependencies.jar)
sdk=$(realpath target/*-openggf-mod-sdk.jar)
python3 tools/modding/native-windows/build_inputs.py --engine-jar "$engine" --sdk-jar "$sdk" --output target/linux-friends-inputs
python3 tools/modding/native-linux/build_linux.py --stage prepare --engine-jar "$engine" --inputs target/linux-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --graal target/linux-toolchain/graalvm-community-25.4.4.1.1+1.1 --output target/linux-friends-native
# Reserve the normal queue budget for the 5 GiB / four-thread image compilation.
python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=python3 "-Dexec.args=tools/modding/native-linux/build_linux.py --stage compile --engine-jar $engine --inputs target/linux-friends-inputs --runtime-classpath target/native-runtime-classpath.txt --graal target/linux-toolchain/graalvm-community-25.4.4.1.1+1.1 --rootfs target/linux-toolchain/ubuntu-rootfs --output target/linux-friends-native"
```

Then run the `qualify` stage with the same artifact arguments and `--roms` followed
by the three **original absolute** S1/S2/S3K paths, plus `--captures` pointing to an
explicit task directory outside the repository. Keep path arguments quoted.
Use `--runtime-rootfs target/linux-toolchain/ubuntu-rootfs` for an Ubuntu 22.04
runtime with no JDK, a cleared environment, Mesa software rendering and only the
distribution, captures and original read-only ROMs mounted. It uses the current
X11 display/socket and `XAUTHORITY` when provided. Build classes, Maven libraries
and the GraalVM toolchain are absent from this runtime.
Qualification requires a display. Do not upload ROMs or source-directory saves.
Omitting ROMs explicitly limits qualification to audit/registration and is
recorded by the empty `renderedGameplay` list; it does not qualify gameplay.

The generated member contract covers dormant creator callbacks as well as live
ones. The Linux build also retains engine implementations of `GameModule`,
including anonymous wrappers, because owner-bound dispatch inspects their concrete
methods reflectively. Their members enter the same mandatory startup audit.
Missing-field and missing-method controls must return ordinary exit 1;
crashes never pass. A separately compiled, runtime-loaded regression mod exercises
record equality/hash/string and pattern-switch bootstraps; it never enters the
image classpath or final distribution. `java.lang.runtime` is explicitly preserved
because preservation of `java.lang` does not include its subpackages.
Each mod runs in a new native process, through production
discovery, loaders and owner fault boundaries. Rendered checks exercise scene
menus/debug routes or 600 real gameplay frames, inspect non-flat framebuffers,
reject owner findings/disabled owners, roundtrip registered module state and reopen
scene mods. Sitar's finite music preparation must reach PLAY, with nonzero offline
PCM; a loading or error screen cannot pass as gameplay. These are representative
checks, not full campaign, networking, rewind-world or performance certification.

Qualification writes only owned disposable probe directories. The builder then
reassembles from immutable inputs so caches, saves and generated configuration
cannot enter the ZIP. The ZIP retains executable bits, engine/third-party licences,
the CE legal notices, source links, exact input hashes and validation scope.

```bash
python3 -m unittest discover -s tools/modding/native-linux -p 'test_*.py' -v
```
