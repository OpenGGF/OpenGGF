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

At integrated `ad3d6a9965952a38b6a0ac2e9eb0285f721ee895`, hosted run
`37823876863` attempt 1 completed all 674 guards with 673 passes and one existing
source-scanner infrastructure error: `TestObjectUpdateClockTerminologyGuard`
exceeded its unchanged two-minute child-process deadline, then its terminated
stream raised `IOException: Stream closed`. Attempt 2 reran the unchanged normal
profile and passed all 674 cases without failures/errors/skips (7:40 Maven time,
finished 18:45:47Z). No engine or test edit was needed. Windows then rebuilt all
16 mods and generated/audited the 14,992-entry contract, but the pinned compiler
rejected `final` on the alias stub. Graal aliases must omit that modifier; the
actual target Engine field remains final and the hosted check still requires it.
The corrected build runs Windows compilation alongside guards, with both required
before delivery. The packaged README also points to Microsoft's official x64
runtime installer if a recipient lacks the platform's C++ runtime DLLs.

The next Windows run, `37827958311` at integrated `913c5a3516b1eafd4fb63cc0111d16dfeb7ac3c5`,
accepted the alias and activated the real SQLite native feature. It then stopped
at the hosted fail-closed option check: the key class was requested from the old
`core.option` package. Inspection of the checksum-pinned Windows `svm-shared.jar`
and `javap` of the real `RuntimeClassLoading.Options` confirms its declared field
type is `com.oracle.svm.shared.option.HostedOptionKey`, whose public `getValue()`
method is present. The private correction derives the key type from the actual
option field and exports its actual module package. This avoids reflecting the
private anonymous subclass and preserves the requirement that class loading is
really enabled. Corrections `b2f05d6b9407c6b82506b5a655aa69c8311e9ed9`
and `889718ce6` were integrated and published at
`f5de9524a943d55191dbf798d405ca8e8e20ca9e` before the coordinated main freeze.
Run `37829797174` rebuilt the Java 21 engine/SDK and all 16 mods. Its Windows
job completed successfully: the compiler's real option check passed, all 14,992
members/1,097 types audited, deliberate absent fields/methods rejected with
ordinary exit 1, and all 16 JVM/native registrations agreed. Each of the 14
code mods also passed the actual Engine external-content boot/registration in
a separate Windows native process. Native compilation took 15:44, with measured
5.79 GiB peak RSS and 48.7% of build time in GC at the fixed 5 GiB heap; this is
a compiler measurement, not gameplay performance.

The final ZIP was produced 19:32:46Z, then downloaded and independently checked:
95,330,595 bytes, 1,133 entries, SHA-256
`382e9d183e89c49e11da9f7116c0cb8770d17be97d3f9aa45f9e25228f304d8c`.
ZIP CRCs and every internal SHA-256 entry passed; the executable and six DLLs
have AMD64 PE headers. All 16 mod hashes, shortcut/quick-directory pairs,
`s1.gen`/`s2.gen`/`s3k.gen` configuration, notices and pinned source identity
matched. No ROM, toolchain archive or probe log entered the distribution.
These results qualify the experimental Windows packaging and startup controls;
rendering, full gameplay, rewind/save behavior and frame pacing remain unqualified.

PE inspection found Java-related imports in `management_ext.dll`; the
[pinned hosted feature](https://github.com/oracle/graal/blob/95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b/substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jdk/JNIRegistrationManagementExt.java)
statically links its management API implementation into the executable. An
additional Wine 11.19 control used a newly created prefix with no Windows JDK,
Java environment variables removed, a Windows-only system search path and a
runtime folder containing spaces. The unchanged downloaded executable passed
the full member audit, all 16 registrations and all 14 real engine boots there.
This supplements the actual Windows qualification and exercises startup without
an installed Windows Java runtime; it does not extend the gameplay claims.
`jar --describe-module` additionally identifies the key's owning module as
`org.graalvm.nativeimage.shared`, rather than `org.graalvm.nativeimage.builder`;
the export flag follows that shipped module descriptor.

The normal guard jobs at `913c5a351` and `f5de9524a` each completed all 674
cases with 673 passes, zero assertion failures/skips and the same existing
`TestObjectUpdateClockTerminologyGuard` child-process deadline/closed-stream
error. The latter finished 19:21:56Z with 10:58 Maven time. These failed
invocations are retained as failed infrastructure qualification; they do not
replace the observed 674-case pass at `ad3d6a996` or establish a fresh pass for
the Windows successor. No guard source, deadline or selection was changed.
Run `37829797174` attempt 2 reran only the failed guard job at the identical
`f5de9524a` inputs, leaving the successful Windows job intact. The unchanged
normal `-Pguards` profile passed all 674 cases without failures/errors/skips,
finished 19:45:21Z with 9:40 Maven time. The previously timing-out scanner
completed in 110.3 seconds. Both required workflow jobs are now successful;
the unsuccessful first invocations remain part of the evidence above.

After the coordinated hold was released, this evidence-only follow-up was
integrated onto published `019dd454b0d63b10a1d0585450bb28f34e360c04`.
That destination differs from qualified actual-main
`b317e94ebdce60c6f81553113543295c75b1d826` only in the parity audit Markdown;
its ordinary/guard qualification belongs to normal run
`20261009T015657Z-e417e53f`, against pinned canonical
`d4993a7307241bf90f004e0d7cf90936f075cf46`. The
[complete source-attributed assertion/skip table](../audits/2026-10-07-stock-parity-gap-verification.md#updated-actual-main-full-assertion-and-skip-summary)
preserves all 26 matching inherited failures and 63 literal causal skips, with
zero new/worsened/unattributed negatives. This follow-up changes only this
research note, so it needs documentation/link checks rather than repeated engine
execution. The Windows image remains pinned to `f5de9524a`; later engine/API
changes and the Starfall Frontier/Eggman's Sky examples are outside that
16-mod archive and its native qualification.

The plan at base `d2a501ebc9919e6c43412a2eedf02a372eac5309` selects all 3,058
ordinary classes plus guards because the new tools/workflow are unclassified.
The previously completed ordinary engine qualification at frozen `6124a524e`
is reusable only for the identical ordinary executable inputs: through the
Windows build at `f5de9524a`, its diff is empty for production/test Java,
resources, examples, API, POM, hooks and testing tools. Its 26,535 cases retain
27 known failures and 62 literal inherited skips;
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

### Experimental Linux friends ZIP follow-up (2026-10-09)

The Linux successor starts from `8668a901216717d8f3696945a95bbfe8e628b652`,
including the new Starfall Frontier, Eggman's Sky and Flappy Tails examples.
Its builder and extra Java sources live outside Maven source roots under
[`tools/modding/native-linux`](../../../tools/modding/native-linux/README.md).
It reuses the independently qualified isolated hosted feature/substitution.
Initial tool-only checkpoints leave stock Java unchanged; the gameplay follow-up
also corrects the bounded overlay/scaffold defects documented below. POM, API
surface/version, hooks, testing tools and release workflow remain unchanged.

The pinned CE Linux archive SHA-256 is
`05ccbbe783210b6886ff7b08fcd0b061c5dce4852b05db87284fc0e24abb08e2`.
Compilation uses an owned rootless Ubuntu 22.04.5 base archive
(`242cd8898b33ea806ef5f13b1076ed7c76f9f989d18384452f7166692438ff1a`),
with Ubuntu's GCC 11/glibc 2.35 build packages, rather than this rolling host's
newer glibc. GraalVM Community licensing and corresponding-source obligations
remain those established above; full CE notices accompany the ZIP.

Qualification extends registration with real native OpenGL framebuffers,
production discovery/loader/fault boundaries, bounded level input sequences,
mod-owned zone selection and both campaign acts, scene debug routes, lifecycle
reopening and registered module-state roundtrips. Prepared creator audio is
installed before level loading. Sitar's asynchronous preparation must reach PLAY
with nonzero final PCM; elapsed wall time is allowed for preparation rather than
mistaking a fast artificial tick loop for a completed performance. A new
ROM-library lease is created for each scene reopening. Data overrides are checked
against their actual prepared music/art targets. These are smoke checks; complete
campaign, networking, full-world rewind and recipient performance remain outside
this evidence. The generated static-member contract and ordinary-exit negative
controls remain mandatory before creator code executes.

The category plan selects 3,073 ordinary classes because these standalone sources
are unclassified. That fallback is disproportionate here: no production/test/build
selection input changes, and the actual new executable/archive consumers are
exercised directly. Use the native contract, all-mod registration/boot/rendered
checks, archive/launcher regression controls and separate fresh normal guards.
This is focused validation, not a green whole-engine suite claim. Initial tool
preflight found the default Lua was not 5.4; `LUA_BIN=/usr/bin/lua5.4` passed the
Java/Lua/PowerShell preflight without executing tests.

Final binary, source attribution and terminal qualification results are recorded
below when completed; this initial implementation checkpoint is not a native
artifact qualification.

The first Linux image at tool source `da8b70bae0c0999a0b93b3a57e2760b46401c9f9`
passed the member audit, missing-member controls and Eggman's Sky registration and
engine boot, then aborted (exit 134) during its rendered route. The fatal method
was `java.lang.runtime.ObjectMethods.bootstrap`, invoked by generated record
methods. Package preservation is exact: `java.lang` does not retain
`java.lang.runtime`. A separately compiled runtime-loaded `BootstrapControl`
reproduced that same fatal without graphics. It exercises record equality,
hashing, string formatting and a pattern-switch bootstrap. It is excluded from
the image classpath to prevent closed-world analysis from masking the fault, and
removed before immutable distribution assembly. The correction preserves the
small JDK bootstrap package; successful registration alone remains insufficient
evidence of gameplay support.

The corrected image's bootstrap control and Eggman's Sky, Flappy Tails and Hello
Scene rendered checks passed. Infinite Sonic then rejected
`ModBackedGamePatch$1.getZoneRegistry()` with `NoSuchMethodException`: its owner
boundary queries concrete engine module methods reflectively. Static creator
references alone cannot infer this host reflection seam. The standalone contract
generator now accepts explicit implementation roots; Linux requests `GameModule`,
retaining concrete and anonymous engine implementations plus their ancestors.
A bytecode control covers an otherwise unreferenced implementation. Applied to
the earlier image, the extended audit rejects 143 missing members with ordinary
exit 1; an isolated contract row also rejects the exact missing wrapper getter.
This detects the demonstrated reflection omission before creator execution.

Matched JVM gameplay exposed two additional issues. The generated badnik sample
submitted native sound byte zero with a nonempty payload on destruction. Its
scaffold and checked samples now resolve `GameSound.BADNIK_HIT` through the active
game's sound map. The art-overlay provider omitted nine existing title-card and
runtime-art admission methods, so respawn fell into unsupported defaults. It now
forwards unchanged leases, scalar identities, ownership kinds, policies and base
exceptions. Nested-overlay and stale-lease regression tests reproduced the old
failure; no new admission algorithm or public API is introduced. The Flappy
qualification also applies the creator's required launch team through the normal
save/session launch context, rather than selecting Sonic for a Tails-only route.

Validation scope was reassessed for these localized production consumers. The
combined runner plan still selects 3,073 classes. Proportionate validation uses
the affected art/scaffold/mod integration tests, S3K title-card and rewind tests,
ROM loading invariants and mapping checks, fresh normal structural guards and
the actual all-mod native rendered qualification. The forwarding fix preserves
the base provider's existing timing/ownership rules; no PLC decoding, registry
entries, ROM offsets, shared physics, build policy or API contract changes.
This remains focused validation. Baseline at `8668a9012` passes all 49 cases in
the seven directly affected existing suites, without skips. The added regression
tests fail on old code: both admission controls reproduce unsupported defaults;
the badnik control reproduces the zero-byte/payload rejection.

While the final image waited in the queue, Sitar Hero passed the corrected
image's native performance route with offline PCM peak 5,962. A ROM-free native
standalone check exposed another exact JDK-package omission: the runtime-generated
proxy invoked `java.lang.reflect.Proxy.<init>(InvocationHandler)` and aborted
with exit 134. The runtime-loaded regression fixture now also generates a proxy
for its own interface; Linux preserves `java.lang.reflect` explicitly. This is
an experimental image correction, with no further engine/API change.

Image `17babc7b7` passed the runtime-loaded record/switch/proxy fixture and the
first three rendered scenes, then Infinite Sonic reached its terrain loader and
raised `AbstractMethodError` at `Arrays.stream(profile).min()`. The interface
metadata existed, but the primitive-stream implementation was not executable.
A separately runtime-loaded control reproduces the same ordinary exit 1; its
JVM counterpart passes. Linux now preserves `java.util.stream` explicitly, and
the fixture also covers min/max, distinct/sorted arrays and reference-to-primitive
filter/map/sum dispatch. This is another exact-package omission, not a change to
Infinite Sonic or the engine's terrain algorithm. Member metadata audit and
runtime invocation controls answer different questions and both remain required.

The remaining checks on that image passed fourteen other mods (seventeen of
nineteen total), including both standalone samples and Sitar's PCM route. Slay
the Robotnik aborted with `Cannot load undefined field` for
`java.nio.charset.StandardCharsets.UTF_8`. The image's native compiler had folded
the field away from runtime-loaded access. Linux separately preserves
`java.nio.charset`; the runtime-loaded fixture now roundtrips a non-ASCII UTF-8
string through the same constant and codecs. Neither failed image is distributed.

The corrected image at composed source `751fdc66d1e61f9526b3dbac98c29d01b037292c`
passes the stream/UTF-8 fixture. Infinite Sonic then reaches progress saving and
aborts on `StandardCopyOption.REPLACE_EXISTING`; Slay progresses past UTF-8 and
aborts on `StringWriter.<init>()`. All other seventeen rendered checks pass again.
The static-field omission is independently rejected by a single-row metadata
audit with ordinary exit 1. Static input analysis is now extended rather than
adding only the latest observed package.

Linux opts into `--jdk-members`: every direct JDK method, constructor and static
field reference in creator bytecode, including dormant callbacks, method handles
and nested dynamic constants, enters the same startup contract. Symbolic owners
resolve through JDK hierarchy without class initialization; constructors never
resolve through ancestors. The declaring packages enter a generated preservation
list. Existing Windows/default generation remains unchanged. Controls cover
dormant JDK fields/handles, inherited methods, constructor non-inheritance, missing
fields and unchanged default mode. The actual nineteen-mod contract grows to
15,844 entries / 1,210 types, with eighteen derived JDK packages. Applied to image
`751fdc66d`, it rejects 71 missing members with ordinary exit 1 before creator code.
This also catches the dormant `Level.WARNING` field and file-save constants.

Metadata lookup still does not prove virtual invocation or compiler-generated
bootstrap behavior. The separately runtime-loaded fixture retains record, switch,
proxy, UTF-8 and primitive/reference stream execution checks, and now exercises a
writer plus file copy and atomic replacement in an owned temporary directory.
Reflection-only/dynamically named JDK calls are outside static reference discovery;
the known reflection seam and direct native gameplay checks remain necessary.

These corrections change only standalone experiment tools. The immutable Maven
engine/SDK and nineteen mod JARs remain pinned to `751fdc66d`; their bytes are reused
for the image-only successor. Native-tool source is independently frozen during
preparation, recorded in `build-info.json`, and linked beside engine/mod source in
the distribution. This avoids conflating an unchanged engine artifact's commit
stamp with the experimental compiler configuration. The updated category plan at
`751fdc66d` against actual published destination `ed45a1990` selects 3,075 ordinary
classes. The same proportionate scope applies; the incoming route-drawing changes
affect only tests/documentation and were independently published/qualified.

The image-only attempt at native-tool source `777a6545312b881786a21f28886e203483ade707`
stops before analysis: the preservation request cannot find `java.util.logging`.
The compiler's default module graph does not expose that JDK package even though
the source contract resolves it on the JVM. The native-image help documents
`--add-modules` as adding root modules; the builder now explicitly resolves
`java.logging`. This is a compiler-input correction, with unchanged engine/SDK/mod
bytes and no native artifact from the failed attempt.

Resolving that module clears the package error, but analysis at `c681ddd73`
terminates with `OutOfMemoryError: GC overhead limit exceeded` in the existing
5 GiB heap. The pinned compiler's `--expert-options-detail=Preserve` offers
package/module/path selectors, without an individual-class selector. The
generator's complete direct-member coverage is retained. The image successor
uses an 8 GiB heap and four threads, submitted through the existing exclusive
queue mode (`OPENGGF_MAVEN_QUEUE=serial`) because it exceeds the normal shared
reservation. Other running jobs drain normally; nothing is cancelled. No engine,
mod, testing-policy or queue implementation changes accompany this correction.

#### Terminal Linux artifact qualification

The successor at native-tool source
`2698bac036c4bbfdf9b41dda6ef762d56db5198d` compiled successfully at
15:49:52 BST on 2026-10-09, in 1m 53s, with peak compiler RSS 8.07 GiB.
The executable SHA-256 is
`9c9193eddf0aba09b43697bdf7f5c4ea6033920639238bdee4e9f7a8fe0a1a12`.
Engine, SDK and all nineteen mod JARs remain from the clean composed source
`751fdc66d1e61f9526b3dbac98c29d01b037292c`, incorporating published
`ed45a1990cf6baf089be7d167ea114341870c3a1` without runtime conflicts.
The engine JAR SHA-256 is
`a3e5ab4fb1c48b99139c42a074c86d0952cc649668381a447341343d35982e66`;
SDK SHA-256 is
`912cd95ab513967757f6cf4fb83429cb87bfd7b86a2cdb0fdcb5c29b5f90bbab`.

| Completed check | Source attribution | Observed result |
| --- | --- | --- |
| Seven existing affected suites, original S2/S3K ROMs | Baseline `8668a9012` | 49 cases, zero failures/errors/skips |
| Same suites with the three reproducing regressions | Production fix `85d8b1851` | 52 cases, zero failures/errors/skips |
| S3K load/bootstrap/decoding, title-card/rewind, PLC mapping and renderer invariants | `85d8b1851` | 180 cases, zero failures/errors/skips |
| Separate fresh normal `-Pguards` | `85d8b1851` | 674 cases, zero failures/errors/skips |
| Contract generator and JVM audit | Engine/mods `751fdc66d`, tools `2698bac03` | 15,844 entries / 1,210 types; dormant-member and constructor-resolution controls pass |
| Native startup audit and deliberately absent field/method | Native image `2698bac03` | Full audit passes; both absent controls exit 1 ordinarily |
| Separately runtime-loaded JDK execution fixture | Same image; fixture excluded from image classpath | Records, switch, proxy, UTF-8, streams, writer, copy and atomic replacement pass |
| Matched JVM/native registration | Same nineteen immutable mod JARs | All 19 results match exactly |
| Actual engine boot capability/trust/registration | Same image | All 17 code-bearing mods pass |
| Native rendered gameplay | Same image, original ROMs rehashed | All 19 pass, including Infinite Sonic and Slay; no owner findings/disabled owners; registered module-state roundtrips and scene reopening where applicable |
| Actual ZIP extracted into a path with spaces, launched from `/` | Final archive below | 1,406 file hashes; all 19 shortcuts pass; CRC, ELF architecture, modes, exploded-mod bytes and ROM exclusion pass |
| ROM-free native gameplay from the extracted ZIP | No JDK or ROM mounts | Both authored standalone samples pass |
| Normal `Engine.main` through `Launch standalone.sh` | Same ZIP contents in a disposable path with spaces | Remains running for 12 seconds with no JDK/ROM mounts; owned process deliberately stopped; bounded startup check only |

The final native rendering used an Ubuntu 22.04 rootfs with no JDK, a cleared
environment and Mesa software OpenGL. Original ROM SHA-1/CRC32 identities matched
the repository's canonical table; no ROM was renamed, copied or linked.
Final captures explicitly retain Tails through Flappy death/respawn/title-card
phases and finish alive in LEVEL at frame 599. Both campaign acts are exercised.
Offline PCM peaks are 5,962 for Sitar, 9,608 for the music override and 6,727 for
the campaign. Infinite/Slay/Flappy final framebuffers were visually inspected.
These are representative gameplay checks, not complete campaign/network/driver
or full-world rewind certification, and not an ordinary whole-suite pass.

Artifact: `OpenGGF-experimental-linux-x64-with-mods.zip`, 147,884,606 bytes;
SHA-256 `4a21cbfee8903f5f4ea9c3528854c02e02e39471b31f3a3fc7a9bcd1c5610ddf`.
The executable and all fifteen shared libraries were checked with `readelf`;
the highest required GLIBC symbol is 2.34. The supported baseline remains glibc
2.35 / Ubuntu 22.04, with X11/XWayland and OpenGL drivers. Shell shortcuts have
executable ZIP permissions. Configuration expects `s1.gen`, `s2.gen`, `s3k.gen`.
Full engine/GraalVM notices and corresponding-source links are included; no ROM,
temporary regression mod, Java installation, capture or source save is packaged.

Reproduction uses the committed Linux README's queued Maven package/input steps,
`build_linux.py --stage prepare`, then the exclusive queued `--stage compile`
with the pinned toolchain/rootfs. `--stage qualify --runtime-rootfs ... --roms`
takes the three original absolute ROM paths and an external `--captures` directory.
This artifact used `target/linux-friends-inputs-r4` and
`target/linux-friends-native-r7`; those directory labels are local staging names,
not source versions. Durable final ZIP, checksum, qualification/ELF/archive
summaries and final PNG/CSV/PCM captures are retained in the external task
directory `openggf-native-linux-2026-10-09` (local path represented by
`$OPENGGF_LINUX_CAPTURE_ROOT`).
Failed/rehearsal images and raw logs remain temporary and are removed at cleanup.
