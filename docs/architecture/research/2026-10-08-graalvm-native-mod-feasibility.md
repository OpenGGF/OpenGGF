# Experimental GraalVM native mod feasibility

Date: 2026-10-08. Engine/input baseline: `6124a524eef9b42efb800d5bcb95376147507c9e`
on `develop`. Investigation branch: `feature/ai-graal-native-mod-feasibility-20261008`.
Host: Linux x86_64, kernel `7.2.8-2-cachyos`; Java sources compiled with Java 21.

## Decision

**Proceed with an explicitly experimental Community Edition prototype; keep the
production native code-mod rejection.** Runtime class loading is available in
the current Community distribution, and the unchanged production mod classloader
can load and execute JARs absent from the image build. **All sixteen friends-bundle
packages now pass real native validation/snapshot/registration checks** with the
generated retention contract described below. The original manual configuration
passed fourteen; the follow-up fixes both standalone bootstrap failures and adds
missing-member preflight. No engine gameplay was qualified by this study.

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

The original Java 21 JVM control passed all sixteen packages. The original
manual-retention native image passed fourteen, including twelve code-bearing mods
and two data-only packages. These historical results motivated the passing
sixteen-package follow-up below:

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

### Reproduction of the original registration configuration

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
  tools/modding/native-feasibility/NativeModMemberAudit.java \
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

## Follow-up: both standalone mods and automatic member coverage

Follow-up branch: `bugfix/ai-graal-mod-preservation-20261008`, based on the published
study successor `17ae561add0c88ec6e2e5c84d55a7457066b1a3b`, then fast-forwarded to
`378c1d715a2565249b696babab4f7aaed943cc6e` after main's coordinated qualification
finished. That upstream delta contains only two evidence/measurement Markdown
paths. Before integration, main advanced to
`e4485262716591e0b504d7a53cd39266022649fa`; its additional delta is one unrelated
Sandopolis qualification Markdown artifact. Rebase preserved the verified probe
bytes exactly. The engine JAR, mod JARs and CE toolchain above remain the inputs.

Both standalone module constructors use `GameRules.SONIC_2`. The original
`package=com.openggf.game` retention did not include `com.openggf.game.rules`.
A single-variable control adding that package made **Phase 3 standalone and
Platformer both register successfully**. This establishes trimming as the cause
of their previous bootstrap failure, rather than a defect in either mod.

The durable fix derives retention instead of maintaining a list of engine
packages. `NativeModMemberContract` starts from
`ModApiSurfaceInventory.annotatedTypes()` in the supplied engine JAR, walks
engine supertypes and signature types, and scans every method in every mod JAR
for engine class, field and method references. This includes dormant callbacks,
constructors, inherited symbolic owners, descriptors, method handles,
`invokedynamic` arguments and nested dynamic constants. Unresolved input members
fail generation with their JAR/class/member identity.

The generator writes a deterministic JAR containing **unchanged original engine
class bytes** and a type/member manifest. This exact classpath slice precedes the
full engine JAR and is preserved with `-H:Preserve=path=...`; mod JARs remain off
the image classpath. Public/protected API members are retained even when no
current mod calls them. Existing engine `.class` resources remain included for
the production bytecode validators. The pinned builder's
`--expert-options-detail=Preserve` confirms that path retention includes complete
classes and their reachability metadata. No engine bytecode or API pins change.

For these inputs, the contract includes **880 canonical API types**, expanding to
**1,097 retained engine types and 14,992 type/member entries**. The native image
passes the entire manifest before entering registration, then all sixteen mods
pass individually; the two data-only packages continue to pass the native policy
control. The earlier production code-mod rejection is checked for every code mod.
The first generated image built in 2m13s with a 5 GiB heap/four threads, peak RSS
5.87 GiB, and a 221.38 MiB executable (plus generated native library).

`NativeModMemberAudit` checks class and declared field/method/constructor metadata
without reading static fields or initializing creator code. Missing entries fail
with bounded member diagnostics before registration. Negative field/method
controls must be rejected. The bytecode regression controls additionally reject
missing types, fields, overloaded methods and a constructor that exists only on
the superclass; they verify dormant method handles and unchanged class bytes.
Rebuilding the original incomplete retention settings with the auditor rejects
the actual `GameRules.SONIC_2` field and all sixteen audited `GameRules`
constructors/methods with normal exit 1, before creator execution. The whole
manifest finds 7,102 missing entries in that old image, versus zero in the
generated image. This is a pruning regression control, not just a synthetic typo
in a manifest.

Reproduce the generated configuration and all JVM/native registrations with:

```bash
python3 tools/modding/native-feasibility/registration_probe.py \
  --graalvm-home /absolute/path/graalvm-community-25.4.4.1.1+1.1 \
  --engine-jar /absolute/path/OpenGGF-universal.jar \
  --mods /absolute/path/mods --check-pruning
```

This Linux-only runner requires Java 21 on PATH, downloads nothing, extracts only
the bundled Linux x64 LWJGL libraries, isolates configuration initialization,
disables core dumps and removes its owned temporary directory on exit. Its
SQLite configuration exclusion is the same diagnostic-only workaround described
above. `--check-pruning` adds the historical incomplete-image build and requires
both real field and method omissions to fail audit; omit it for normal generated
image checks. It does not package or enable a native engine for users.

**Coverage limits remain explicit.** Metadata lookup is not proof that every
invocation works on the experimental VM. Exact-class preservation and actual
registration controls provide separate evidence. The contract covers the
canonical engine API and static engine references in supplied mods; it cannot
infer string-based reflection, generated classes, arbitrary third-party/JDK
members, or gameplay-only state transitions. The JDK helper packages from the
original controls still have explicit retention. Full gameplay, graphics,
storage, rewind, relaunch, dependencies, fault recovery and other platforms remain
unqualified. This follow-up resolves standalone **registration**, not the whole
native mod release qualification list.

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
save reload. With standalone registration resolved, qualify both standalone games
through their playable bootstrap and gameplay paths.
Run the existing mod/dependency/fault/rewind tests through the native path and
test Linux, Windows and macOS independently. Benchmark interpreter and JIT-enabled
builds with frame pacing and memory measurements before choosing either for users.

## Validation scope

### Experimental Windows friends ZIP follow-up

The 2026-10-08 Windows packaging request has a separate reproducible build under
[`tools/modding/native-windows`](../../../tools/modding/native-windows/README.md)
and an additive artifact-only workflow. The compiler is the checksum-pinned CE
Windows archive, SHA-256
`2733ea1331f1a98b05dd55a768b07347051dad48f030dbbfa191b11a2ccf4144`.
It compiles extra sources outside the production Maven source tree. Its hosted
feature verifies the actual `RuntimeClassLoading` option and unchanged private
engine capability field before applying an image-local accessor substitution.
Only `compiledModsSupported` is redirected; native detection and the ordinary
trust/snapshot/ownership/fault paths remain intact. A no-display control invokes
the engine's real external-content boot and registration for each code mod.

The Windows image uses proper runtime dependency JARs and SQLite's native-image
feature. It does not inherit the registration diagnostic's SQLite exclusion.
All packaged mods are rebuilt from source using the real Java 21 SDK converters
and validator. The distribution is reconstructed after qualification, so service
and configuration writes from the probes never enter the ZIP. Five LWJGL DLLs,
builder-produced support DLLs, per-mod `.bat` shortcuts, the member contract,
source identity, requested ROM filenames and licence notices accompany the image.
ROMs remain outside this distribution and outside GitHub Actions inputs.

Local preparation passed Java 21 compilation of the hosted/launcher/check
sources, bytecode contract regression controls, fresh conversion/packaging of all
16 mods, the portable ZIP controls and `actionlint` 1.7.12. Archive controls cover
path spaces, changed JAR hashes, wrong PE architecture, traversal, ROM exclusion,
licence retention, checksums and crashes wrongly presented as missing-member
rejections. Windows compiler and actual native results are still pending at this
preparation checkpoint; no usable Windows ZIP is claimed yet.

The plan at base `d2a501ebc9919e6c43412a2eedf02a372eac5309` selects all 3,058
ordinary classes plus guards because the new tools/workflow are unclassified.
The previously completed ordinary engine qualification at frozen `6124a524e`
is reusable only for the identical ordinary executable inputs: the diff remains
empty for production/test Java, resources, examples, API, POM, hooks and testing
tools. Its 26,535 cases retain 27 known failures and 62 literal inherited skips;
it is not a fresh or green suite claim. The new artifact workflow is a guard input
and the experimental native image is a new executable contract: fresh normal
guards and direct Windows member/registration/engine-boot qualification remain
required before delivery. This bounded additive build leaves the existing
production build and release selection policy unchanged.

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

For the follow-up, `run_categories.py --base
e4485262716591e0b504d7a53cd39266022649fa` again selected 3,058 ordinary classes
plus guards because standalone experiment tools are unclassified. The same
proportionate exception applies: Maven never builds these Java files, and no
production/test sources, resources, examples, API, POM, hooks, workflow or testing
policy change. Focused validation covers the executable behavior directly:
Java 21 compilation, bytecode omission/inheritance controls, the full JVM/native
member audits, sixteen JVM and sixteen native registrations, synthetic missing
field/method controls, and historical real-pruning rejection. Python syntax,
CLI/exit-status handling, temporary-output cleanup and documentation links are
checked separately. The new reproducer qualifies neither full gameplay nor an
engine suite. Tool preflight passed with `LUA_BIN=/usr/bin/lua5.4`; the default
`lua` was 5.5 and correctly failed that unrelated engine prerequisite.
