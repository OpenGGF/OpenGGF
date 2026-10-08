# Experimental GraalVM native mod feasibility

Date: 2026-10-08. Engine/input baseline: `6124a524eef9b42efb800d5bcb95376147507c9e`
on `develop`. Investigation branch: `feature/ai-graal-native-mod-feasibility-20261008`.
Host: Linux x86_64, kernel `7.2.8-2-cachyos`; Java sources compiled with Java 21.

## Decision

**Proceed with an explicitly experimental Community Edition prototype; keep the
production native code-mod rejection.** Runtime class loading is available in
the current Community distribution, and the unchanged production mod classloader
can load and execute JARs absent from the image build. Fourteen of the sixteen
friends-bundle packages pass real native validation/snapshot/registration checks
with the preservation configuration below. The two standalone packages still
fail on trimmed bootstrap state. No engine gameplay was qualified by this study.

This is a feasibility result, not a native mod release. The JVM bundle remains
the usable distribution for all sixteen mods. The engine's native capability
gate, trust decisions, release toolchain and Mod API have not been changed.

## Toolchain and provenance

Tested **GraalVM Community Edition 25.4.4.1.1+1.1**, an innovation release
[published 2026-09-22](https://www.graalvm.org/release-notes/25.4/), based on
OpenJDK `25.0.4.1.1+1-jvmci-25.4-b23`. Native Image reports `25.0.4.1.1`.
This is a newer image builder reading Java 21 bytecode; it does not change the
project's Java 21 build requirement.

The [official CE archive](https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1)
was downloaded into this worktree's `target/`, with no global JDK installation.
The release asset digest and separate `.sha256` asset agreed with the downloaded
bytes:

| Input | SHA-256 |
|---|---|
| `graalvm-community-jdk-25i4-25.0.4.1.1_linux-x64_bin.tar.gz` | `05ccbbe783210b6886ff7b08fcd0b061c5dce4852b05db87284fc0e24abb08e2` |
| Downloaded `LICENSE.txt` | `64a84fbc7c0170afa3bb45e839ed67cb779b1b101c3eac55c9b88a9f50b2531c` |
| Downloaded `THIRD_PARTY_LICENSE.txt` | `502544557d6e8d9ce9c8550310830eae085d7b8949c9081630d64e3982405106` |
| Friends-bundle `OpenGGF-universal.jar` | `f8180f77f9e5d2e0acee9acd17de64e7f14bd0f24feadd68ea3f9e9982a35d9c` |

The engine JAR and sixteen separately packaged mods were reused from the completed
2026-10-08 portable bundle at the exact baseline above. No mutable Maven build
tree was shared. Its `build-info.json` records the individual package hashes.
No mod JAR was on either native image's build classpath.

## Licensing terms and distribution choice

The downloaded CE `LICENSE.txt` identifies GPLv2 with the Classpath Exception,
with separate licences for some components. The release's
[product licence](https://github.com/oracle/graal/blob/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b/vm/LICENSE_GRAALVM_CE)
and [interpreter source header](https://github.com/oracle/graal/blob/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b/substratevm/src/com.oracle.svm.interpreter/src/com/oracle/svm/interpreter/Interpreter.java)
confirm that the experimental interpreter is covered by GPLv2 with that exception.
This is not an Oracle-only feature requiring its proprietary licence.

The exception expressly permits static or dynamic linking with independently
licensed modules and distribution of the resulting executable under chosen terms,
provided the independent modules' licences are respected. OpenGGF's root
[`LICENSE`](../../../LICENSE) is **GPLv3**, not LGPL. The engineering assessment
is that unmodified CE runtime code linked with OpenGGF has a viable GPLv3
distribution path under the exception; using Native Image does not remove
OpenGGF's GPLv3 obligations or relicense it as GPLv2.

For a release, retain the applicable runtime/dependency licences and notices,
provide OpenGGF's corresponding source and build scripts for the released
revision, and account for the source/notice obligations of every incorporated
component. If a complete CE JDK or its module image is bundled, also satisfy the
distribution obligations for that runtime; its licence includes a source-code
offer. A public source link must resolve to the actual released inputs. The
Classpath Exception is not permission to strip licences or distribute modified
GraalVM library source under arbitrary terms. No GraalVM patches are proposed here.

**Oracle GraalVM is a different licensing choice.** Its current
[GFTC including Early Adopter terms](https://www.oracle.com/downloads/licenses/graal-free-license.html)
allow the specified uses of unmodified programs and redistribution subject to
conditions, including no associated distribution/use fees and preserving notices.
The agreement also covers Oracle program portions incorporated into Native Image
output. Separately licensed open-source components retain their separate terms.
Early Adopter technology can change incompatibly; the
[Oracle 25 licensing manual](https://docs.oracle.com/en/graalvm/jdk/25/docs/licensing-information/)
identifies Native Image as Early Adopter technology. These restrictions require a
separate compatibility determination for any Oracle runtime portions embedded in
a GPLv3 release; this study does not establish that route. CE avoids choosing
those GFTC-covered portions for this experiment.

Do not apply the older
[2021 Oracle Enterprise OTN licence](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html)
to CE or to the current GFTC download: its Early Adopter GPL restrictions are not
the current CE terms. Conversely, “free of charge” is not a substitute for reading
the selected distribution's licence. The [official FAQ](https://www.graalvm.org/faq/)
distinguishes CE and Oracle GraalVM.

## Observed experiments

### External code controls

The committed [reproducer](../../../tools/modding/native-feasibility/probe.py)
compiles the unchanged `ModDependencyClassLoader` and a small host. It builds
three images, **then** compiles two versions of an external plugin into separate
JARs. The plugin implements a host interface, calls a retained host method through
a lambda, handles an exception branch and concatenates its result. Defining-loader
identity and exact callback results are asserted. The native executable's hash
must remain unchanged between replacement JARs.

Command, exit 0:

```bash
python3 tools/modding/native-feasibility/probe.py \
  --graalvm-home target/graalvm-toolchain/graalvm-community-25.4.4.1.1+1.1 --jit
```

| Control | Both external JAR versions | Binary bytes |
|---|---|---:|
| Ordinary closed-world image | Rejected with `ClassNotFoundException: external.Plugin` | 35,457,416 |
| `-H:+RuntimeClassLoading` | Passed callback and defining-loader assertions | 64,555,472 |
| Also `-H:+GraalJITCompileAtRuntime` | Passed the same assertions | 94,964,176 |

These are executable sizes for this probe, not engine package sizes or performance
measurements. The JIT-enabled image passed; the short callback does not prove
that it reached a compilation threshold or that a game sustains 60 Hz. No external
JDK, `lib/modules`, or `-Djava.home` was supplied to these executables.

### Real packaged mods

[`NativeModRegistrationProbe`](../../../tools/modding/native-feasibility/NativeModRegistrationProbe.java)
uses the production scanner, catalog validator, bytecode validator, immutable
snapshot factory, private `ModContext` transactions and installed fault boundary.
Each trusted input package is selected individually. It first verifies the
unchanged native-policy rejection for code-bearing packages, then explicitly
allows code only inside the diagnostic and checks registration failures.
The source is deliberately outside the normal engine build.

The final Java 21 JVM control passed all sixteen packages. The final native
interpreter image passed fourteen, including twelve code-bearing mods and two
data-only packages:

| Package / owner | Native registration result |
|---|---|
| Hello Scene / `hello-scene` | Passed |
| Infinite Sonic / `infinite-sonic` | Passed |
| Putt Putt Paradise / `putt-putt-paradise` | Passed |
| Robotnik Tower Defence / `robotnik-tower-defense` | Passed |
| Sitar Hero / `sitar-hero` | Passed |
| Slay the Robotnik / `slay-the-robotnik` | Passed |
| Sonic Survivors / `sonic-survivors` | Passed |
| Music / `openggf-gallery-music-sample` | Passed; data-only |
| Reskin / `phase2-reskin` | Passed; data-only |
| Badnik and zone / `phase2-sample` | Passed |
| Character / `phase3-character` | Passed |
| Standalone / `phase3-standalone` | Aborted on pruned `GameRules.SONIC_2` after native-library setup |
| Flappy / `sample-flappy` | Passed |
| Platformer / `sample-platformer` | Aborted on the same bootstrap field |
| ROM art remix / `sample-rom-art-remix` | Passed |
| Tide Circuit / `sample-tide-circuit` | Passed |

Registration does not run gameplay callbacks, render a scene, load a playable
route, verify saves/rewind, or exercise actual fault recovery. These inputs have
no declared mod-to-mod dependencies. Neither multi-owner dependency linkage nor
session disposal/restart was qualified. A trimmed VM method/field can abort the
process instead of producing a catchable creator exception; that is a release
blocker even where registration passes.

### Reproduction of the registration configuration

Supply the same unmodified universal JAR and packaged mod folder. Use Java 21
`javac` and the downloaded CE image builder explicitly. Variables below refer
only to experiment inputs/output; do not replace the project's JDK globally.

```bash
OPENGGF_PROBE_ROOT="$PWD/target/native-mod-registration"
OPENGGF_PROBE_ENGINE="/absolute/path/OpenGGF-universal.jar"
OPENGGF_PROBE_MODS="/absolute/path/mods"
GRAALVM_PROBE_JDK="/absolute/path/graalvm-community-25.4.4.1.1+1.1"
mkdir -p "$OPENGGF_PROBE_ROOT/classes" "$OPENGGF_PROBE_ROOT/metadata" "$OPENGGF_PROBE_ROOT/run"
printf '%s\n' '[{"name":"sun.net.www.protocol.jar.Handler","allDeclaredConstructors":true}]' \
  > "$OPENGGF_PROBE_ROOT/metadata/reflect-config.json"
javac --release 21 -cp "$OPENGGF_PROBE_ENGINE" -d "$OPENGGF_PROBE_ROOT/classes" \
  tools/modding/native-feasibility/NativeModRegistrationProbe.java
"$GRAALVM_PROBE_JDK/bin/native-image" -march=compatibility -J-Xmx5g --parallelism=4 \
  --exclude-config '.*OpenGGF-universal.jar' 'META-INF/native-image/org.xerial/sqlite-jdbc/.*' \
  '-H:IncludeResources=com/openggf/.*\.class' -H:+UnlockExperimentalVMOptions \
  -H:+RuntimeClassLoading \
  '-H:Preserve=package=com.openggf.mods.code,package=com.openggf.mods.scene,package=com.openggf.mods.scene.*,package=com.openggf.io,package=com.openggf.game,package=com.openggf.game.patch,package=com.openggf.game.presentation,package=com.openggf.level,package=com.openggf.level.objects,package=com.openggf.level.rings,package=com.openggf.sprites.playable,package=java.lang,package=java.lang.invoke,package=java.util,package=java.util.function' \
  "-H:ConfigurationFileDirectories=$OPENGGF_PROBE_ROOT/metadata" \
  -cp "$OPENGGF_PROBE_ROOT/classes:$OPENGGF_PROBE_ENGINE" \
  com.openggf.tools.NativeModRegistrationProbe "$OPENGGF_PROBE_ROOT/registration"
cd "$OPENGGF_PROBE_ROOT/run"
ulimit -c 0
"$OPENGGF_PROBE_ROOT/registration" "$OPENGGF_PROBE_MODS" hello-scene
```

Run the JVM control with `java -cp` using the same classes and engine JAR. Pass
each owner ID separately to the native probe so one native abort cannot hide the
other results. Do not run a native experiment from a directory containing the
user's live configuration: standalone registration can initialize legacy services.
For the two standalone probes, extract the five `linux/x64/org/lwjgl/**/*.so`
entries from the universal JAR into an experiment-only directory, flatten their
basenames as the production native profile does, and pass
`-Dorg.lwjgl.librarypath=/absolute/experiment/native-libs` before the probe arguments.
They initially failed with missing `liblwjgl.so`; supplying those exact bundled
libraries exposed the remaining `GameRules.SONIC_2` failure.

The universal JAR includes SQLite Native Image configuration naming a feature
class absent from that shaded artifact. The first build failed on
`org.sqlite.nativeimage.SqliteJdbcFeature`. Excluding **only** that configuration
allowed this registration experiment; it is not a production packaging fix and
does not qualify SQLite-backed saves. A real native engine build must use and
qualify its proper dependency/toolchain setup. The final diagnostic executable
was 223,545,808 bytes; build time about 1m54s with a 5 GiB builder heap and four
threads, reported peak RSS 6.07 GiB. It was not shipped.

## Rejected assumptions and implementation requirements

- A single runtime-loading flag is insufficient. The first external callback
  failed to resolve `LambdaMetafactory`. Preserving `java.lang.invoke` then exposed
  an uncompiled `StringConcatHelper` method and process abort. Preserving the
  consumed JDK packages fixed these controls.
- Preserving executable methods alone is insufficient. Without original engine
  `.class` resources, native validation incorrectly reported missing object
  recreation paths and unsupported static state for packages accepted by the JVM.
  Including the same engine bytecode resources restored those validation paths.
- Registration reachability is narrower than gameplay reachability. Successive
  bounded configurations exposed pruned `ModInputLimits`, `LevelPatch`, `List.of`,
  `SecondaryAbility`, ring constructors, and finally `GameRules` bootstrap state.
  Do not add package exceptions indefinitely and call the result general support.
  Define retention from the complete supported Mod API, its supertypes, fields,
  default methods, JDK helpers and resource-based validator inputs; qualify it
  against code compiled after the image, not a fixed baked-in mod list.
- [Pinned GraalVM runtime-loading documentation](https://github.com/oracle/graal/blob/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b/substratevm/docs/runtime-class-loading.md)
  documents unsupported parallel class loading and no fallback for members
  removed from image classes. Missing JDK classes can instead use the JRT file
  system with an external `lib/modules`, but that creates another bundled-runtime
  and licensing obligation. The passing controls did not need that fallback.

An implementation should introduce a separately labelled experimental build and
an engine-owned capability check, retaining the normal trust, snapshots, loader
ownership, dependency graph and transaction/fault boundaries. First qualify
Hello Scene on the actual native engine: enter/update/draw/exit, ROM art, input,
audio, storage and clean relaunch. Then qualify Sonic Survivors: representative
busy gameplay, object recreation, capture/restore/replay, owner attribution and
save reload. Resolve standalone bootstrap and qualify both standalone packages.
Run the existing mod/dependency/fault/rewind tests through the native path and
test Linux, Windows and macOS independently. Benchmark interpreter and JIT-enabled
builds with frame pacing and memory measurements before choosing either for users.

## Validation scope

The direct native/JVM experiments above are focused validation of standalone
diagnostics and unchanged production paths. The final Python runner additionally
passed exit-status handling, failed-control rejection, timeout cleanup, syntax
and CLI checks. Native outputs/logs/toolchain are temporary worktree artifacts;
only the reproducer and conclusions are retained.

`run_categories.py --base 6124a524eef9b42efb800d5bcb95376147507c9e` selected
3,058 ordinary classes plus guards because the new probe sources were unclassified.
The repository's proportionate-validation exception applies: these sources are
outside Maven's production/test inputs, and no production Java, POM, workflow,
test selection or API contract changed. Those engine suites and trace profiles
were not run for this investigation. This is not a full-suite pass.
