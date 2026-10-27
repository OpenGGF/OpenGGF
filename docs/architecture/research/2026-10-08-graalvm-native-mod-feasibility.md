# Experimental GraalVM native mod feasibility

Date: 2026-10-08. Engine/input baseline: `6124a524eef9b42efb800d5bcb95376147507c9e`
on `develop`. Investigation branch: `feature/ai-graal-native-mod-feasibility-20261008`.
Host: Linux x86_64, kernel `7.2.8-2-cachyos`; Java sources compiled with Java 21.

Later source-attributed qualification follows below, including the
[Windows refresh](#windows-refresh-2026-10-09); the initial sixteen-mod study
does not describe the coverage of those later artifacts.

> The production-rejection decision below is historical. It is reversed by the
> [default native promotion](#default-native-promotion-2026-10-27); the original
> experiments and their coverage limits remain as recorded.

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

### Oracle licence review (2026-10-27)

**Local benchmarking: permitted. Shipping: unresolved, and not approved as a
GPLv3 distribution route on this evidence.** GFTC restrictions are incompatible
with distribution of a GPLv3 combined work containing non-exempt GFTC runtime
portions. Whether the actual native packages contain only separately licensed or
legally exempt portions has not been established. Keep Community as the shipping
choice; Oracle performance numbers are comparison only. This is an engineering licence assessment, not legal
advice or a determination of any person's legal liability.

The exact local download was
[`graalvm-jdk-25_linux-x64_bin.tar.gz`](https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-x64_bin.tar.gz),
retrieved 2026-10-27, identifying **Oracle GraalVM 25.0.4+7.1**, Java
`25.0.4+7-LTS-jvmci-b01`, release date `2026-07-21` in `release`.
Archive SHA-256: `76007c309f821aaf435bce63162ea0395587fc77350801c81643fe7feea37276`.
The URL is mutable; these findings attach to those bytes, not every later
Oracle 25 download.

Documents actually inspected:

| Document | Version/date and relevant content |
| --- | --- |
| Archive root `LICENSE.txt`; [GFTC including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html) | Both state **last updated 12 June 2023**. The archive grants internal development/testing, conditions redistribution, and explicitly covers Oracle program portions in Native Image output. |
| Shipped `license-information-user-manual.zip` → `license-information-user-manual.html`; [Oracle GraalVM 25 LIUM](https://docs.oracle.com/en/graalvm/jdk/25/docs/licensing-information/) | **Last updated July 2026**, copyright 2026; archive member dated 2026-07-15. Describes Native Image as Early Adopter technology, permits production use, and enumerates third-party licences (including Native Image's LLVM 20.1.4 under Apache 2.0 with LLVM exceptions). |
| `NOTICE.txt`, `GRAALVM-README.md`, and module `legal/` files | Shipped with **25.0.4+7.1**; no independent revision date is supplied for the root notice/readme. The archive has **no file whose name contains `THIRD`**, and no root `THIRD_PARTY_LICENSE.txt`; its third-party material is in the LIUM and module `legal/` notices. The notice records the separation from Oracle Java SE entitlement after JDK 24. `legal/java.base/LICENSE` and `legal/jdk.graal.compiler/LICENSE` carry **Oracle NFTC** (no independent revision date printed in those files); do not assume every component is covered by one open-source licence. |
| OpenGGF [`LICENSE`](../../../LICENSE); [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) | **Version 3, 29 June 2007**; especially §§1, 5(c), 6 and 10. OpenGGF supplies no general exception for a proprietary Native Image runtime. |

**Local use.** GFTC License Rights and Restrictions (a) expressly authorizes
“developing, testing, prototyping and demonstrating your applications”. This
unmodified-toolchain, internal gameplay benchmark is testing; neither that clause
nor the shipped Early Adopter addendum imposes a benchmark-publication prohibition.
The addendum applies “all license terms, conditions” of the associated program,
subject to its modifications: development risk, compatibility/backport limits,
warranty and liability provisions. It does **not** supply an open-source linking
exception, nor does its Early Adopter label itself prohibit this local test.
No alternate negotiated Oracle agreement was supplied for this task.

**Shipping.** GFTC (b) makes redistribution subject to the same licence and bars
“fees associated with such distribution or use”, including bundled products and
services using the distributed program. The following paragraph expressly deems
Oracle portions in otherwise unmodified Native Image output to be an unmodified
program under that licence. The contingent conditions also restrict
“reverse engineering, disassembly or decompilation”, subject to applicable law;
source included for reference cannot be modified unless Separate Terms permit it.
Merely charging nothing for OpenGGF does not give downstream GPL recipients the
unrestricted modification and commercial redistribution rights they would need.

GPLv3 §6 requires object-code conveyance under §§4/5 together with an authorized
means of providing “Corresponding Source”. Section 5(c) requires licensing of the
combined covered work as a whole. Section 10 says “You may not impose any further
restrictions” on GPL rights, including recipients' rights to modify and propagate.
GPLv3 §12 also prevents conveyance when other obligations contradict its conditions.
The GFTC no-fee/unmodified-program/reverse-engineering conditions cannot simply be
passed downstream as added restrictions on a GPLv3 combined executable. Providing
OpenGGF's Java sources alone does not cure an incompatible incorporated runtime.
GFTC Separately Licensed Technology preserves its own rights; third-party notices
are **not** a blanket exception for all Oracle runtime portions.

**System Libraries are a real qualification, not an assumed escape.** GPLv3 §1
includes a compiler or interpreter among possible Major Components, and defines
System Libraries by their normal packaging and limited purpose (enabling use with
that component or implementing a Standard Interface with a public source
implementation). Section 1 also excludes general-purpose/free build tools from
Corresponding Source only when they are “not part of the work”. A proprietary
compiler used only at build time is therefore a different question from runtime
code copied into an executable. The fact that Native Image generated the file
does not establish that every copied runtime portion meets the System Library
definition. Nor does static inclusion alone settle every legal classification.
No component-by-component proof or OpenGGF copyright-holder linking permission was
found here. For **non-exempt GFTC-covered runtime portions in the combined work**,
the reviewed grants do not authorize GPLv3 distribution; shipping approval cannot
be inferred from free downloads or the compiler's output clause.

Human/legal review would need to settle:

- Exactly which proprietary/GFTC, NFTC, separately licensed and open-source runtime
  portions enter each executable and accompanying support library, with the actual
  release's terms; compiler implementation code need not all be copied into output.
- Whether each otherwise incompatible incorporated portion qualifies as a GPLv3
  System Library in this distribution, with evidence for both parts of §1.
- Whether Oracle can supply compatible alternative permission and whether **all**
  relevant OpenGGF/dependency rightsholders can authorize any necessary exception.
- What remediation is appropriate for already published native assets, including
  notices/source offers, corrected Community rebuilds, or withdrawal if advised.
  No assets were removed, replaced or republished in this task.

**Oracle 25 runtime class loading.** The exact 25.0.4+7.1 executable accepted
`native-image -H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading --expert-options-all`
(exit 0). Its option listing includes `-H:±RuntimeClassLoading`, disabled by
default, and describes enabling runtime loading with an open type world and
class-loader-aware resolution. The archive's `lib/svm/builder/svm.jar` also
contains `RuntimeClassLoading$Options` and its implementation. Thus the option is
present in this Oracle 25 release; it is not a Community-exclusive capability.
This is option/implementation inventory, **not** an external-mod runtime
qualification or proof that the complete newer 25.4 retention recipe works on
25.0. The old default `-Pnative` recipe did not enable it. Licence compatibility
still decides shipping independently of feature availability.

**Existing 0.6 exposure is confirmed, with a version correction.** Both tagged
workflows selected `distribution: 'graalvm'` and **`java-version: '21'`**, not 25:
[`v0.6.20260911`](https://github.com/OpenGGF/OpenGGF/blob/37aeb6b84d6634457616c942187cdd40dd93c24f/.github/workflows/release.yml)
and [`v0.6.20260917`](https://github.com/OpenGGF/OpenGGF/blob/498c51674ea8de3a84904b0eabe5afd218b9927d/.github/workflows/release.yml).
The action's [distribution table](https://github.com/graalvm/setup-graalvm#supported-distributions)
identifies that value as Oracle GraalVM. More decisively, successful release
[run 35281831291](https://github.com/OpenGGF/OpenGGF/actions/runs/35281831291), at
`498c51674ea8de3a84904b0eabe5afd218b9927d`, records **Oracle GraalVM
21.0.12+7.1** in the actual Windows/Linux/macOS build logs and native-image
invocations of `com.openggf.Engine`. The
[published release](https://github.com/OpenGGF/OpenGGF/releases/tag/v0.6.20260917)
has Linux/macOS/Windows native assets and a separate universal JAR, published
2026-09-17T23:11:58Z. This is observed Oracle build provenance, beyond a likely
workflow choice. It establishes a need to audit those native packages under the
**21.0.12+7.1** terms and actual incorporated components; the 25.0.4 licence review
is not a retroactive legal verdict on a different runtime. A pre-existing local
Oracle 21.0.12+7.1 toolchain was also inspected read-only: its root `LICENSE.txt`
is byte-identical to the inspected Oracle 25 GFTC file (SHA-256
`c6f989689dd98054a77260222e7c648f5db72ec14e7e13a4fb79a797dd989b81`).
This corroborates the restriction concern, but does not replace the per-binary
component/System Library audit. The tagged packaging steps copied the repository's `LICENSES/` directory, whose
only tracked file at `v0.6.20260917` was `LGPL-2.1.txt`; they did not explicitly
copy the selected GraalVM toolchain's notices. That is a concrete notice-inventory
question for the package audit, not a conclusion about every byte in the assets.
The universal JAR does not itself embed a Native Image runtime. Switching future builds to Community
neither relicenses nor repairs previously downloaded binaries. The old tagged
native profile used ordinary closed-world Native Image, with no size option.

Do not substitute the older
[2021 Oracle Enterprise OTN licence](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html)
for the actual GFTC archive. Its terms are not the current Community terms or
the inspected Oracle 25 grant. Preserve all applicable Community runtime notices,
corresponding sources and third-party obligations when shipping the chosen build.

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
  tools/native-image/NativeModMemberAudit.java \
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
the repository's canonical table; checks used those original absolute paths.
The repository's ordinary post-checkout hook created shared-development links
inside the task worktree. Those links are generated setup outputs, were not used
as test prerequisites, and are removed with the worktree while original inputs
are preserved. No ROM bytes are copied or included in the friends artifact.
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
summaries and final PNG/CSV/audio observations are retained in the external task
directory `openggf-native-linux-2026-10-09` (local path represented by
`$OPENGGF_LINUX_CAPTURE_ROOT`).
Failed/rehearsal images and raw logs remain temporary and are removed at cleanup.

#### Integrated Linux delivery verification

Integration into the main workspace's existing `develop` branch was conflict-free
at `14e0e2b63499bde2ea6b8973c49c2b49df103279`, against actual destination
`ed45a1990cf6baf089be7d167ea114341870c3a1`. All seven unrelated dirty/untracked
main paths were preserved, including file-byte and submodule-state comparisons.
The artifact's engine/mod source and native-tool pins remain those above.

Post-integration verification at that unchanged source:

- Queued lean focused tests completed at 16:02:29 BST: **232 cases**, zero
  failures/errors/skips. All 17 suite report identities and their counts match
  the development runs. The original seven-suite 49-case baseline has zero
  negatives; its existing suite identities remain present, with exactly three
  additional reproducing regression cases.
- A separate fresh normal queued `-Pguards test` completed at 16:07:07 BST:
  **87 suite reports / 674 cases**, zero failures/errors/skips, Maven exit 0.
  Every guard suite identity and count matches the development run.
- Actual main-launch Java 21/Lua 5.4/PowerShell preflight passed with
  `LUA_BIN=/usr/bin/lua5.4`; the preflight itself executes no tests.

The focused command uses `maven_queue.py --lean -B -Dmse=off test`, the original
absolute ROM properties, and these exact selectors (no profile narrowing):

```text
TestModArtOverrides,TestPhase2SampleModIntegration,TestPhase3StandaloneSampleIntegration,TestProjectScaffolder,TestSampleFlappyIntegration,TestSonic3kTitleCardManagerRewind,TestSonic3kTitleCardKosQueue,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry,TestSonic3kPlcArtRewindSnapshot,TestSonic3kTitleCardTeardownModel,TestPatternSpriteRendererCorruptionGuard
```

The full fallback selection is 3,075 ordinary classes; the documented
proportionate-validation decision above applies to this combined delivery.
No full ordinary-suite pass is claimed. Final native gameplay and archive checks
are independent experimental-domain evidence, rather than substitutes for an
unreported broad run. Final captures/ZIP are preserved externally; rehearsal
captures and regenerable captured runtime stores have been removed. Publication
is followed by removal of the clean, fully merged task worktree/local branch and
consumed temporary logs, without touching foreign jobs or source inputs.

## Windows refresh, 2026-10-09

The Windows rebuild starts from published Linux-delivery source
`b84398a29771fdaf4198c10d34dd181b4ff058b0`, in isolated
`bugfix/ai-native-windows-mods-20261009`. Stock engine, tests, API, POM, hooks,
testing policy and normal release build remain unchanged. The three original ROM
paths are used only locally; no ROM is uploaded to Actions or included in a ZIP.

The previous Windows ZIP (source `f5de9524a943d55191dbf798d405ca8e8e20ca9e`,
SHA-256 `382e9d183e89c49e11da9f7116c0cb8770d17be97d3f9aa45f9e25228f304d8c`)
was preserved. Under Wine 11.19 it passes its original 14,992-entry / 1,097-type
audit. Substituting the current 19-mod, 15,844-entry contract rejects 280 missing
members with ordinary exit 1. These include newer engine/mod references as well
as omitted `StandardCharsets`, `StandardCopyOption`, concrete Sonic modules and
wrapper methods; the total must not be attributed entirely to one original bug.
A separately compiled runtime-loaded bootstrap fixture aborts the old PE with
exit 3: `java.lang.runtime.ObjectMethods.bootstrap` was never compiled, and the
VM specifically recommends preserving `java.lang.runtime`. This independently
reproduces the Linux record-bootstrap omission in the Windows image under Wine.

The Windows builder now applies the proven Linux contract options
`--implementation-root=com.openggf.game.GameModule --jdk-members`, derives the
JDK preservation packages, retains implicit bootstrap/reflection/stream/charset
packages, resolves `java.logging`, and uses an 8 GiB compilation heap. The shared
runtime fixture is absent from image analysis and removed before shipping.
Registration PASS lines must match the JVM exactly. Every mod shortcut and the
normal launcher are actually invoked with diagnostic arguments on the Windows
builder. The Windows entry point also compiles the existing platform-neutral
OpenGL gameplay checker; its historical Linux package name does not select any
OS-specific runtime behavior. `qualify_wine.py` consumes the immutable Windows
artifact and runs the same representative gameplay recipes locally through the
extracted batch shortcuts, with a private prefix and path containing spaces.

Validation scope: the untouched ordinary engine inputs retain the prior
Linux-delivery qualification. The change-based plan against `b84398a29` selects
3,075 ordinary classes via its unclassified standalone-tool/workflow fallback.
For this bounded experimental image correction, direct native image/contract/
runtime/packaging checks and fresh structural guards replace that disproportionate
ordinary rerun. Java 21 / Lua 5.4 / PowerShell preflight passes with
`LUA_BIN=lua5.4`; the initial unqualified default Lua launch was rejected before
tests. Development-tree `maven_queue.py -B -Dmse=off -Pguards test` completed
2026-10-09 16:17:30Z: 674 cases, zero failures/errors/skips, Maven exit 0.
Portable Windows and Linux packaging controls pass (three cases each).
This is focused validation, not a whole ordinary-suite pass.

Actual Windows compilation/runtime checks and Wine rendered qualification were
pending at correction checkpoint `03b4dec34`. Their terminal evidence follows.

### Terminal Windows qualification and delivery

The correction integrated conflict-free and was pushed as
`0d3d9c1d6cfa76f8ba451599e7a54239a9f6a4a1`. Its delta from `b84398a29` is
confined to the standalone Windows tool/workflow, their tests and prose; there
is zero stock engine/test/API/POM/hook/testing-tool or normal release delta.
The actual destination's queued fresh `-B -Dmse=off -Pguards test` finished
2026-10-09 16:24:22Z, exit 0: 87 fresh suite identities, 674 cases, no failures,
errors or skips, matching the development run by identity and case count.
The main report directory also held unrelated stale ordinary reports; only
the 87 suites named by this invocation's terminal log were counted and consumed.
The Windows and Linux portable packaging suites also pass at the destination.

[Windows build run 37958994125](https://github.com/OpenGGF/OpenGGF/actions/runs/37958994125)
is terminal **success** at that exact source, including both required jobs.
On `windows-latest` with the pinned Windows CE/MSVC toolchain, the image compiled
in 6m29s at 8.35 GiB peak RSS. The actual Windows process passed:

- Contract regression controls and the complete 15,844-entry / 1,210-type audit.
- Deliberate missing field and method controls rejected with ordinary exit 1.
- The separately runtime-loaded JDK fixture: records, pattern switches, generated
  proxy, UTF-8, writers, file copy/atomic replacement, and primitive/reference streams.
- All 19 exact JVM/native registration comparisons; actual engine boot for all
  17 code mods through their batch shortcuts. Both data-mod shortcuts and
  `OpenGGF.bat` passed their diagnostics from a different working directory.

The required independent Ubuntu guard job finished 2026-10-09 16:39:17Z:
87 fresh report identities / 674 cases, zero failures/errors/skips, Maven exit 0.
The runner's clean Windows ZIP has 1,406 entries / 139,602,042 bytes, SHA-256
`af1c0e1f78b04d492d32e95b1f001c066a0b3d716ae84f7b20c74d5710c5d598`.
Engine/mod and native-builder source pins both equal `0d3d9c1d6cfa76f8ba451599e7a54239a9f6a4a1`;
`OpenGGF.exe` SHA-256 is
`ddbf6a9c14ba2704714be14bd7b2f5294d4b18778f5237599f9653588054bc43`.

Local `qualify_wine.py` consumed that immutable ZIP, verified the same three
original ROM identities, and ran every extracted `.bat` through the Windows PE
with Wine 11.19 / Mesa software OpenGL, from a path containing spaces. All 19
rendered smoke checks passed without creator findings or disabled owners, with
module-state roundtrip and scene reopen where applicable. The existing shared
recipes cover the title/scene/level paths recorded in the Linux qualification,
including Eggman's Sky planets, Flappy Tails classic/Sonic play, tower waves,
Slay combat/shop/map, Starfall cavern/warden and Sitar performance/pause/resume.
Level samples advance 600 frames and exercise additional acts where supplied.
Sonic Survivors' capture includes its camp/menu and level updates; it does not
certify a complete arena run. Eighty-three PNGs and twenty state CSVs were
retained under `$OPENGGF_WINDOWS_CAPTURE_ROOT/native-captures`, alongside the
structured qualification and a contact sheet. Sitar's offline PCM peak was
5,962; this establishes produced audio, not physical device latency or quality.
The primitive sample Flappy screenshot matches the qualified Linux sample
pixel-for-pixel; its authored test artwork is not a Windows rendering regression.

A matched local fixture check used the exact same bootstrap JAR bytes as the
old-image negative: refreshed PE exit 0 / successful registration versus old
PE exit 3 / unreachable `ObjectMethods.bootstrap`. The normal extracted
`Launch standalone.bat` also invoked `Engine.main` and remained alive for a
bounded 12-second startup observation without Java or ROMs beside the bundle;
only that owned prefix was deliberately stopped afterward. This establishes
normal startup through the shortcut, not a complete interactive route.

The deliverable was reconstructed from immutable Windows inputs to exclude
every probe-created config/cache/save, then had separately labelled Wine
evidence added to `build-info.json` and the README. Final ZIP CRC, complete
per-file checksums, all mod hashes, native PE hash, 19 launchers, CE/project
licences and `s1.gen` / `s2.gen` / `s3k.gen` configuration verified. It contains
no ROMs, JDK or diagnostic bootstrap mod. Output:
`$OPENGGF_WINDOWS_CAPTURE_ROOT/OpenGGF-experimental-windows-x64-with-mods.zip`,
1,406 entries / 142,464,410 bytes, SHA-256
`9ac1ea0642071bd908b4fb6f3e093535fc0fd79f42d4ef7c584676d9e872f1b1`.
Its checksum sidecar is retained, and the prior 2026-10-08 ZIP remains untouched.

**Limits:** actual Microsoft Windows evidence covers compilation, packaging,
JDK runtime semantics, registration and boot/shortcuts. Representative rendered
gameplay was checked under Wine, not on a physical Windows graphics/audio stack.
This does not certify every route, long session, rewind boundary, multiplayer
peer, graphics driver or future third-party mod. Static member scans cannot
prove arbitrary dynamically named reflective members; bootstrap/dispatch
controls and real gameplay remain necessary alongside metadata audits.
No whole ordinary-suite or production-native-support claim is made.

The terminal follow-up changes only this research Markdown; it does not alter
any executable, packaged source or CI qualification input. Consumed Maven/native
logs and owned Wine state are temporary; rendered captures, final ZIP and light
source-attributed evidence are retained outside the task worktree. Main's seven
unrelated dirty/untracked paths were independently byte/state checked and
preserved through integration.


## Windows Eggman's Sky Vorbis refresh — 2026-10-09

The refreshed input base is `15400390314b06ded17bf14f65f19c3449b9c82c`.
It includes the compact 122-clip Ogg/Vorbis voice bank (`dd019978f`) and the
production prepared-audio session delegation fix (`8bb155528`). Updating only
the mod JAR in the previously delivered `0d3d9c1d6` executable would retain the
missing `sfxPcm` delegation; this refresh therefore rebuilds the Windows image
and all 19 maintained mods from the current source. No codec addition is needed:
the existing STB Vorbis decoder supports the new assets.

The shared experimental `NativeGameplayCheck` now prepares audio through
`ModSubsystem.preparedAudioFactory` and retains the returned session view until
presentation is retired. It consumes stop commands before retiring streamed
cursors and before closing the mod kit. Every declared one-shot must be present through that
view, play by its namespaced SFX reference, and produce nonzero, varying final
mixed PCM. `sfx-pcm.csv` records exact decoded frames, rate, channels, peak and
range. The check applies to every mod declaring SFX, not only Eggman's Sky.
It replaces the probe's raw-port setup, which could bypass this production
wrapper defect. The authored change is confined to the shared standalone
qualification tool and prose; engine, API, POM and normal release inputs are
unchanged by this task.

A matched local Java 21 control used the current engine/mod/probe with only
`SessionExternalContentView` and its nested classes replaced by their exact
`0d3d9c1d6` bytes. It returned ordinary exit 1 at
`Missing session SFX PCM: SfxRef[owner=eggmans-sky, name=voice-systems-online]`.
Removing that sole class override returned exit 0, checked all 122 samples and
completed the representative Eggman's Sky scene/gameplay checks. This proves
the added check detects the inherited session-wrapper failure; decoder success
or nonzero music PCM cannot substitute for it. Audio peaks establish produced
PCM, not subjective quality or physical-device playback.

Validation uses the proportionate exception for this bounded diagnostic helper:
matched negative/current controls, all-mod gameplay, packaging integrity and
fresh structural guards, followed by actual Windows compilation/runtime checks
and rendered Wine checks of the delivered executable. The unchanged runner plan
against the pinned base selects 3,076 ordinary classes via its unclassified
shared-tool fallback; that ordinary rerun does not exercise this external probe.
Java 21 / Lua 5.4 / PowerShell preflight passes with `LUA_BIN=lua5.4`.
This is focused native qualification, not a whole ordinary-suite pass. The
Windows workflow and its mandatory independent fresh-guard gate are unchanged.

The final Java 21 helper passes representative rendered gameplay for all 19
mods. An initial explicit `resetState` teardown failed for the standalone music
sample after its successful gameplay check because it retired a streamed cursor
before the presentation voice snapshot. The probe now consumes music/SFX stops
on a forward presentation tick before port/kit teardown; the final all-mod rerun
and the matched old-wrapper negative both pass their expected outcomes. No
production teardown behavior was changed to make the diagnostic pass.

All 122 packaged `.ogg` resources match their provenance SHA-256 and source
bytes, with no remaining voice WAVs. The JAR is 3,240,732 bytes, SHA-256
`6eaf6ed5edd4928881df9fbf8abbcb50a5eebe70bab013f7a333d3759618bb22`.
The complete final-PCM observations match every provenance frame count and
48,000 Hz mono format. Encoded source audio totals 3,014,069 bytes; compressed
voice entries in the JAR total 2,909,992 bytes. Windows and Linux portable
packaging controls each pass their three tests.

At this source checkpoint, the already submitted local voice regression and
fresh guards are waiting in the shared Maven queue. Windows image compilation and terminal archive
qualification are pending. Their source-attributed evidence follows after the
immutable image and all 122 native PCM observations have been checked.


### Terminal Windows Vorbis image and archive evidence

The helper integrated conflict-free and was pushed as
`ee0466ef336d326bd2f3ef1d35714e7b8f1c071b`; the destination's Windows and Linux
portable packaging controls each pass three tests. All eight unrelated main
paths (including the three dirty submodules) match their original byte/state
snapshot. Engine/test/API/POM/workflow inputs are unchanged by the task.

[Windows run 37980438420](https://github.com/OpenGGF/OpenGGF/actions/runs/37980438420)
is terminal success at that exact source. The image compiled in 6m41s, with
8.61 GiB peak RSS. On Microsoft Windows it passed the 15,844-entry / 1,210-type
member audit, ordinary exit-1 missing field/method controls, the runtime-loaded
JDK bootstrap fixture, all 19 exact JVM/native registration comparisons, all
17 code-mod engine boots, and every mod/normal batch shortcut. The independent
fresh guard job completed 2026-10-09 19:39:04Z: 674 cases, zero failures/errors/
skips, Maven exit 0. This is the actual integrated source's mandatory guard gate;
no whole ordinary-suite pass is claimed.

The immutable Windows-runner ZIP has 1,406 entries / 111,523,442 bytes, SHA-256
`317a72ed1f2d6cbef58b47293e09895009dfea62c1e52a4c112c2bd57ffe001b`.
Engine/mod and native-builder source pins both equal `ee0466ef336d326bd2f3ef1d35714e7b8f1c071b`.
The PE SHA-256 is
`082a4f606e0de26c0d962fc1fb5cf35b52cbb6cd520ecd891074b3b6578233c2`.
The Windows-built Eggman's Sky JAR hash is
`a60c9c3df51c6d3ce2179ed81491948318b93742cc35f3dd75dd9cf63c84d1df`;
its complete provenance and all 122 encoded Ogg resource bytes independently
match the current source. Archive/container differences from the local Java 21
JAR are not treated as resource changes.

`qualify_wine.py` ran every extracted shortcut using that exact PE, original
rehashed ROM paths, Wine 11.19 and Mesa software OpenGL. All 19 representative
rendered gameplay checks passed, including state roundtrip and scene reopen
where applicable. The production prepared-audio session path produced final
mixed PCM for all 122 Eggman's Sky clips: every exact decoded frame count equals
the shipped provenance, every clip is 48,000 Hz mono, minimum peak is 17,272 and
minimum signal range is 26,146. The standalone sample's one SFX and platformer's
three SFX also passed. These checks establish produced PCM under Wine, not
physical Windows sound-device playback, subjective quality or every gameplay
route. The broader representative-route limits of the preceding Windows
qualification still apply.

The qualifier rebuilt a clean archive from immutable inputs and added labelled
Wine evidence. Final CRC, complete per-file hashes, all 19 JARs/launchers, native
PE hash, source pins, licences, short ROM names and both copies of the Ogg bank
pass. No ROMs, JDK or diagnostic bootstrap fixture are included. Output:
`$OPENGGF_WINDOWS_VORBIS_CAPTURE_ROOT/OpenGGF-experimental-windows-x64-with-mods.zip`,
1,406 entries / 113,822,994 bytes, SHA-256
`bab1d58789f7c2d9f043fb420d0ab259da070608060e729a30a208f67abb0086`.
It is 28,641,416 bytes smaller than the prior delivered ZIP, which remains
unchanged. The checksum sidecar, source-attributed qualification JSON, 83 PNGs
and 23 state/PCM CSVs are retained outside the repository. Generated mod
repositories/storage/saves were removed from captures; the owned Wine prefix
has no remaining process.

The additional local Maven voice regression and fresh-guard requests were
withdrawn before Maven admission after the mandatory actual-source CI guards
and exhaustive native PCM/gameplay checks completed. Neither request executed
Maven or produced a test result; neither is counted as a pass. The complete
proportionate qualification is the matched missing-wrapper negative, all 19 JVM
and Windows-PE gameplay checks, all 122 provenance/PCM comparisons, portable
packaging controls and the fresh actual-source 87-suite / 674-case CI guard gate.
Only these two task-owned unstarted requests were withdrawn; every foreign
running/waiting job was preserved.

This terminal evidence follow-up changes only the research Markdown. Its
packaged executable/mod/helper inputs remain identical to the qualified
`ee0466ef3` source. Consumed local/CI logs and generated probe/runtime outputs
remain temporary and are removed with the accounted-for task worktree;
qualified captures, the final ZIP and light evidence are retained outside it.

## Default native promotion (2026-10-27)

The user reversed the earlier decision to keep the production native rejection.
Default native builds now use the same Community runtime-loading recipe as the
friends packages, with generated canonical API/signature retention, implementation
roots, erased service-factory discovery, original engine bytecode resources and
JDK helpers. The hosted feature checks the actual RuntimeClassLoading option;
Engine no longer uses a friends-only capability substitution. Production loader
and Mod Manager paths accept code mods using the existing trust policy, immutable
snapshots, structural validation and owner fault boundaries. Native packages carry
the same build-pinned first-party jars as JVM packages, including Time Attack.
The Mod API, version descriptor and signature pins are unchanged.

The [default build guide](../../../tools/native-image/README.md) is the current
recipe; friends builders add examples/shortcuts and retain their qualification.
Pinned Community 25.4.4.1.1+1.1 archives exist for Linux x64, Windows x64 and Apple
Silicon macOS in the [official release](https://github.com/graalvm/graalvm-ce-builds/releases/tag/graal-25.4.4.1.1).
The earlier CE licensing assessment still applies. Native distributions preserve
CE legal notices and source links alongside OpenGGF/dependency notices.

The first default-build attempt exposed Time Attack's java.net.http preservation
request before Native Image had resolved that module. The shared builder now
derives owning JDK modules for every retained package instead of special-casing
a mod. Metadata auditing remains separate from executable qualification. Runtime
loading is still an experimental VM feature; arbitrary reflection, third-party
code and unexercised JDK facilities are not certified by these bounded checks.

The later rendered title-entry check exposed a pruned Jackson
`ObjectMapper.readValue(byte[], Class)` method even though registration passed.
The generator now follows non-engine referenced types to their runtime dependency
jar and retains that entire jar; it never adds creator jars to the analysis
classpath. A dormant ASM dependency reference is the regression control. The
current default inputs select Jackson Databind and YAML without a package list.

LWJGL 3.4.1's default Java 25 FFM callback generator also failed before graphics
initialization. The native profile selects upstream's official `unsafe` core
classifier and excludes the transitive ordinary FFM core from native analysis;
normal JVM builds keep their existing FFM backend. No game or Mod API code changes.

The completed Linux build used the default `native` profile, an 8 GiB compiler
heap, four compiler threads and exclusive Maven admission. Final image generation
took about 2m23s (the full queued Maven invocation executed in 2m48s). Its member
manifest audits 24,827 entries / 1,991 engine and referenced JDK types. The builder
also passed bytecode contract controls for service factories, dormant references,
missing members, exact original class bytes and referenced dependency jars.
The executable, support libraries and separate mod jars were copied into a
fresh runtime directory outside the repository; no engine/JDK classpath or ROM
copy was supplied there. The original ROMs matched the documented CRC32/SHA-1.

Native rendered checks passed for Time Attack's master-title menu (including
reopen), Hello Scene (including reopen) and the data-only Phase 2 reskin (600
frames per S2 act, with installed art bytes checked). Each checks owner findings,
disabled owners and module state roundtrip. The title-entry probe uses the real
scene host and launcher; hosted Time Attack races are explicitly outside this
menu qualification. Normal Engine boot separately loaded the actual pinned
bundled Time Attack and an installed, enabled Hello Scene jar with a matching
persisted trust hash. JVM rendered controls passed on the unchanged ordinary
FFM dependency classpath for all three packages. Native boot emits the VM's
boot-classloader warning and the upstream JNI/Unsafe deprecation warning; these
checks required no external JDK and neither warning was suppressed.

The focused loader/UI/bundled suite passed 36 tests with zero failures/errors/skips.
An earlier synthetic bundle fixture failed because its `.class` bytes were version
text; replacing that fixture payload with a real minimal GgfMod class exercises
loading and registration. The first broad smoke attempt was intentionally stopped
after the Jackson failure changed build inputs: 2/85 batches completed, 134 reports,
985 tests, zero failures/errors/skips; incomplete, superseded by the final run.

The production decision changed in
[0bf1f2feed11f4139a81fbd15fbfb2c55d8b7db4](https://github.com/OpenGGF/OpenGGF/commit/0bf1f2feed11f4139a81fbd15fbfb2c55d8b7db4).
The memory/staging and qualification follow-up is
[67011cc9fe1886128b3f6be5d9abc903e53c062b](https://github.com/OpenGGF/OpenGGF/commit/67011cc9fe1886128b3f6be5d9abc903e53c062b).
This promotion supersedes the production-rejection decision above; historical
experiments and their limitations remain evidence, not current build policy.

### Free-runner memory follow-up

The standard Apple Silicon `macos-15` runner has 7 GiB RAM. The successful 8 GiB
heap build peaked at 9.49 GiB RSS, which is a compiler working set, not the game's
runtime requirement. Preservation retains methods that only future mod bytecode
may invoke, and therefore expands analysis, reflection bridges and compiled code.
It also defaults to JNI registration of every preserved member. The pinned
[Graal source](https://github.com/oracle/graal/blob/graal-25.4.4.1.1/substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/PreserveOptionsSupport.java)
explicitly supports Java preservation with `PreserveIncludesJNI=false`, including
adapted caller-sensitive methods. Actual JNI users keep their ordinary metadata.

The isolated default-graph experiments retained the same 24,827-entry contract:

| Compiler configuration | Observed result |
| --- | --- |
| Original preservation, 4 GiB heap | Analysis heap OOM. |
| Original preservation, 5 GiB heap | Cancelled after about ten minutes without analysis completion; not an OOM verdict. |
| No blanket JNI preservation, 4 GiB heap, Parallel GC / four workers | Heap OOM after about 3m27s. |
| No blanket JNI preservation, 5 GiB heap, Parallel GC / four workers | Heap OOM after about 4m47s. |
| Same Java retention, 5 GiB G1 heap / two workers / three visible CPUs | Analysis and compilation completed; later image generation exhausted the heap after about 5m14s. |
| Same G1 configuration with `-Os` | Image generated in 4m19s, peak RSS **6.03 GiB**, 304 MiB image. |

Size optimization is also a documented
[Graal memory-pressure option](https://github.com/oracle/graal/blob/graal-25.4.4.1.1/docs/reference-manual/native-image/BuildOptions.md).
The successful image retains 24,074 reflected types; JNI registrations drop from
23,525 types to 65, while the exact Java member audit is unchanged. Its launcher
session received SIGTERM while the Maven child retained its inherited queue and
worktree locks; that existing child finished with `BUILD SUCCESS`. This diagnostic
launcher status is not presented as a clean queued-command result. The staged
production-profile build is verified separately below.

The shared recipe now defaults to that 5 GiB/G1/size configuration, and prepares
an immutable full-engine input jar for the compile stage. Larger reference sets
can change `OPENGGF_NATIVE_IMAGE_HEAP` without forking the recipe. CI first runs
Maven preparation, ends Maven, and then starts the image compiler. Native packaging
skips test compilation because its prerequisite release test job owns validation.
The standard macOS runner remains selected; no paid runner, old toolchain fallback
or workflow dispatch was used. A 6.03 GiB Linux compiler RSS is not a measurement
of the total macOS machine footprint. Apple Silicon headroom remains unverified
until CI actually builds it.

That lower-memory executable passed the embedded audit and deliberate missing
field/method controls (each ordinary exit 1), the runtime-loaded bootstrap jar,
normal bundled boot and normal Hello Scene trust controls (approved exit 0;
untrusted and stale hashes exit 1). Time Attack's name/menu and reopen, Hello
Scene and the data-only reskin all rendered successfully again. Every corresponding
captured PNG matches the JVM control's RGBA pixels, including the reskin at frame
599. These remain bounded menu/scene checks, not hosted-race qualification.

### Change-based smoke checkpoint

At `0bf1f2feed11f4139a81fbd15fbfb2c55d8b7db4`, the change-based selection contained
4,236 ordinary candidate classes, partitioned into 85 batches, plus separate
guards. The run completed 79 batches / 3,942 reports / 42,008 tests: 43 failures,
zero errors, 78 skips. It consumed 7,164.77 seconds (119m25s) of its 120-minute
execution budget, excluding queue waiting. It was stopped while waiting at the
completed-batch boundary: the remaining 35 seconds could not finish six batches.
**Coverage is incomplete:** 286 ordinary candidates were not run, and the guard
lane was not reached. No release-only full suite ran.

All 78 skips were inspected: opt-in measurements/captures/soak runs, isolated
OpenAL and CNZ capture prerequisites, acts without the relevant platform/flyer,
missing opt-in Second Strike fixture jars, and one spin-tube capture/release
assumption. None was a missing original-ROM path skip. The UI failure was the old
native-only assertion in `updatedUntrustedCodeCanBeDisabledWithoutGrantingTrust`;
it now expects the same trust-renewal prompt and explicit disable flow as the JVM.

Bounded develop-base checks at `9a796ec60f8437fab1f2fffb755174a73c7f7659`
reproduced the CNZ registration failure at line 36 and all 31 DEZ/LRZ/MHZ route
failures by exact test identity, exception type and assertion message (not totals
alone). The remaining ten HPZ/campaign/SOZ/SSZ failures also reproduced on the same base
with zero identity/type/message mismatches. Across the three route comparison
commands, 59 tests ran with 41 matching failures, no errors or skips; the separate
CNZ method is the 42nd inherited failure. Current failures were compared with the
bounded baseline runs by individual test and assertion, not by suite totals.
The terminal focused/guard and staged-build results follow below.

<details>
<summary>Exact inherited failure identities (all AssertionFailedError)</summary>

| Test | Matching baseline/current assertion |
| --- | --- |
| `game.sonic3k.objects.TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables` | `expected: <true> but was: <false>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesBothTransportersWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesUpperTransportChainsAndEastHubWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoDefeatsGravityBossAndLoadsFinalStageWithoutTransformation` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldRouteClearsAllTurbinePanelsAndOpensDoorWhileObjectControlled` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoDescendsInvertedSpringShaftWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldUpperRouteTraversesLiftsConveyorsAndTimedBridgesWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind` | `expected: <9589> but was: <3203>` |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `death at 19930 ==> expected: <false> but was: <true>` |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `death at 14721 ==> expected: <false> but was: <true>` |
| `tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `death at 36000 ==> expected: <false> but was: <true>` |
| `tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `death at 21162 ==> expected: <false> but was: <true>` |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `death at 21162 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloActTwoColdRouteCapture#coldWideSonicAloneClearsActTwoAndLoadsFinalStage` | `death at input 36000 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloActTwoColdRouteCapture#coldTailsAloneClearsActTwoAndLoadsFinalStage` | `death at input 19930 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloActTwoColdRouteCapture#coldTailsLowerGravityPadPreservesPendingContactAcrossRewind` | `death at 19930 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloActTwoColdRouteCapture#coldSonicAloneClearsActTwoAndLoadsFinalStage` | `death at input 14721 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloColdRouteCapture#coldSonicAloneClearsMinibossAndReachesPlayableActTwo` | `death at input 14721 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloColdRouteCapture#coldTailsAloneClearsMinibossAndReachesPlayableActTwo` | `death at input 19930 ==> expected: <false> but was: <true>` |
| `tools.TestDezSoloColdRouteCapture#coldWideSonicClearsBothBossPhasesAndReachesPlayableActTwo` | `both eight-hit phases must be defeated through player contact ==> expected: <2> but was: <0>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoUpperClimbsAndDoorRelease` | `death at input 5027 ==> expected: <false> but was: <true>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReplaysIncomingRouteAndFirstActTwoDoor` | `first-door death at input 15323 ==> expected: <false> but was: <true>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `death at input 5027 ==> expected: <false> but was: <true>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReachesMinibossAndRestoresTraversalAndCloudEscape` | `death at input 15323 ==> expected: <false> but was: <true>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesClearsMinibossAndRestoresFightAndActTwoHandoff` | `death at input 15323 ==> expected: <false> but was: <true>` |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoTraversalToTheMiddleCorridor` | `death at input 5027 ==> expected: <false> but was: <true>` |
| `tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActOneAndRestoresNativeInteractions` | `cold Knuckles death at 26853 ==> expected: <false> but was: <true>` |
| `tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline` | `cold Knuckles death at 26853 ==> expected: <false> but was: <true>` |
| `tools.TestS3kHpzJoinedColdRouteCapture#coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease` | `cold death 5027 ==> expected: <false> but was: <true>` |
| `tools.TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 44639 ==> expected: <false> but was: <true>` |
| `tools.TestS3kLrzHpzSszColdCampaign#tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 80320 ==> expected: <false> but was: <true>` |
| `tools.TestS3kLrzHpzSszColdCampaign#pairColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 73285 ==> expected: <false> but was: <true>` |
| `tools.TestS3kLrzHpzSszColdCampaign#knucklesColdCampaignClearsSkySanctuaryTwoThroughTheEndingAndTitle` | `cold death 5027 ==> expected: <false> but was: <true>` |
| `tools.TestSozColdRouteCapture#soloColdActCompletesWithTraversalReplayAndPlayableDestination(int, String)[5]` | `cold death at 15850 ==> expected: <false> but was: <true>` |
| `tools.TestSozColdRouteCapture#knucklesColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef` | `cold death at 11787 ==> expected: <false> but was: <true>` |
| `tools.TestSozColdRouteCapture#knucklesWideColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef` | `cold death at 23478 ==> expected: <false> but was: <true>` |
| `tools.TestSszWidePairedColdRouteCapture#coldFirstReplicaAndTransportReplayWithRetiredColumnDebris` | `expected: <46> but was: <48>` |
| `tools.TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | `FIRST_FORWARD [wide-route-11640] frame=11640 n=24 input=11664 ==> expected: <false> but was: <true>` |

</details>

### Terminal verification commands

Commands ran from the task checkout, or the detached develop-base checkout for
baseline attribution. Temporary stdout redirections are omitted here. `JDK25`
means the local Java 25 installation, `GRAAL` the checksum-verified Community
25.4.4.1.1+1.1 home under `target/graalvm-toolchain/`, and `BASE` is
`9a796ec60f8437fab1f2fffb755174a73c7f7659`. `ROM_ARGS` supplies the documented
three Maven ROM properties with absolute original paths. `S1`, `S2`, `S3K` are
the same absolute originals; no ROM was copied, renamed or uploaded. `ARTIFACTS`
is the explicit task directory outside the repository.

| Command / executable check | Result and coverage |
| --- | --- |
| `JAVA_HOME="$GRAAL" OPENGGF_MAVEN_QUEUE=serial python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=mvn '-Dexec.args=-Dmse=off -Pnative -Dmaven.test.skip=true package -B'` | Initial default Linux native build passed; original 8 GiB recipe. |
| `JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py -Dmse=off -Pnative -Dmaven.test.skip=true -Dnative.stage=prepare -Dnative.macos.package.skip=true package -B` | Final shared production-profile preparation passed, including bytecode retention controls and the 24,827-entry audit. |
| `OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=python3 '-Dexec.args=tools/native-image/build.py --stage compile'` | **Exit 0**. Final default image generated in 4m19s, queued Maven execution 4m20s, **6.02 GiB peak compiler RSS**. |
| `./target/OpenGGF --audit` | Passed: 24,827 entries / 1,991 types. Final executable SHA-256: `9a2d7286dbbad60d95dcf83d7f5ebdbd76f0e30d5591ca68edbf94d4d6f76c9e`. |
| `OpenGGF --audit "$missing_member_tsv"` (missing field, then missing method) | Both negative controls returned ordinary exit 1. |
| `OpenGGF --check-mods native-bootstrap-control` | Passed with a jar compiled after image generation: records, pattern switches, proxy, UTF-8/files/atomic replace/streams/service factories. Creator jars were absent from the image classpath. |
| `OpenGGF --check-engine time-attack` and `OpenGGF --check-engine hello-scene` | Final default image passed normal pinned-bundle and installed/enabled code-mod boot. Approved/untrusted/stale-hash controls returned 0/1/1. |
| `OpenGGF -Dopenggf.headless.gl=surfaceless-egl --check-gameplay "$slug" "$ARTIFACTS/captures/default-native/$slug" "$S1" "$S2" "$S3K"` for `time-attack`, `hello-scene`, `reskin` | All passed with DISPLAY/WAYLAND_DISPLAY unset, no-device audio and original ROMs. Time Attack and Hello Scene include reopen; reskin runs 600 frames per S2 act and verifies art overrides. Eight corresponding PNGs match JVM RGBA pixels exactly. Hosted Time Attack races remain outside this probe. |
| `JAVA_HOME="$JDK25" python3 tools/testing/maven_queue.py -Dmse=off -Dmaven.test.skip=true package dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/jvm-runtime-classpath.txt` | Ordinary JVM package passed with its normal FFM dependency classpath. |
| `java --enable-native-access=ALL-UNNAMED -Dopenggf.headless.gl=surfaceless-egl -Dorg.lwjgl.librarypath="$native_runtime" -cp "$host_classes:$engine_classes:$jvm_runtime_classpath" com.openggf.tools.nativelinux.NativeGameplayCheck "$slug" "$output" "$S1" "$S2" "$S3K"` for the same three slugs | All JVM rendered controls passed; same module-state/reopen/art checks and pixels as native. |
| `JAVA_HOME="$JDK25" python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModClassLoaderFactoryNativeGuard,TestModManagerScreenNativeGuard,TestBundledModBoot,TestBundledModPackager,TestModClassLoaders,TestModRegistrationRuntime' test` | 36 tests passed, zero failures/errors/skips. |
| Same focused selector plus `TestModManagerScreen` | 78 tests: 77 passed, one revised UI test sequence failed because its second Accept confirmed trust instead of declining it. Corrected to cancel with Back before explicit disable; no production trust-policy change. |
| `JAVA_HOME="$JDK25" python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestModManagerScreen,TestModManagerScreenNativeGuard' test` | **48 passed**, zero failures/errors/skips; final UI source. |
| `JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base "$BASE" --preflight` | Passed Java 25, Lua 5.4 and PowerShell checks. An earlier default-Lua probe found Lua 5.5; the final launch explicitly selected 5.4. |
| `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3 tools/testing/run_categories.py --base "$BASE" --headless-gl surfaceless-egl --rom-directory "$ROM_ROOT" --max-minutes 120 --run` | Incomplete at 79/85 batches, 42,008 tests / 43 failures / zero errors / 78 skips. 42 failures reproduced on the base; one obsolete native UI expectation corrected and verified narrowly. 286 candidates unrun. No full-suite claim. |
| Base: `python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables' test` | One test / one matching failure, zero errors/skips. |
| Base: `python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "${ROM_ARGS[@]}" '-Dtest=TestDezColdRouteCapture,TestDezIncomingFinalRouteCapture,TestDezSoloActTwoColdRouteCapture,TestDezSoloColdRouteCapture' test` | 25 tests / 23 matching failures, zero errors/skips. |
| Base: same flags with `-Dtest=TestLrzKnucklesColdRouteCapture,TestMhzKnucklesColdRouteCapture` | Eight tests / eight matching failures, zero errors/skips. |
| Base: same flags with `-Dtest=TestS3kHpzJoinedColdRouteCapture,TestS3kLrzHpzSszColdCampaign,TestSozColdRouteCapture,TestSszWidePairedColdRouteCapture` | 26 tests / ten matching failures, zero errors/skips. |
| `JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B` | 805 tests / seven failures / zero errors/skips. Two old native-command assertions and the Linux packaging fixture were corrected. Four unrelated guard findings are compared below. |
| `JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestBuildToolingGuard#ciAndReleaseMavenCommandsShouldDisableSilentExtension+ciAndReleaseMavenJobsMustUseDirectMavenAndTargetPaths+nativeImageCompilationMustFollowMavenPreparation' test -B` | **Three passed**, zero failures/errors/skips, including the new separate-step / standard-runner contract. |
| `JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestBuildToolingGuard#releaseGateToolsShouldRejectCorruptAndChangedEvidence' test -B` | **Passed** after the fixture repair; the release-tool safety subprocess returned success. One Jupiter gate, zero failures/errors/skips. |
| `python3 tools/testing/test_release_linux_package.py` | Passed after updating the build fixture to supply and assert runtime-loading libraries, bundled Time Attack and Community notices. |
| `python3 -m unittest discover -s tools/native-image -p 'test_*.py' -v` | Four passed, including reuse of the prepared recipe and fail-closed toolchain checks. |
| `python3 -m unittest discover -s tools/modding/native-linux -p 'test_*.py' -v` | Three packaging/ABI/path controls passed. |
| `python3 -m unittest discover -s tools/modding/native-windows -p 'test_*.py' -v` | Three packaging/negative-control tests passed; this is not a Windows image build. |
| Exact `Package (Linux)` and `Smoke validate packaged artifact` run blocks extracted from `release.yml`, executed with `bash -e -c` | Final archive and validation passed. Build/JVM-jar root was this checkout's `target/`; distribution/archive root was outside the repo. Initial local validator overrides pointed at the wrong JVM-jar root; corrected without changing production defaults. |
| POM XML / workflow YAML / Python AST parsing; `bash -n src/packaging/assemble-macos-app.sh`; `git diff --check`; changed Markdown relative-link scan | Passed for new changes. One unchanged pre-existing link to `examples/starpost-valley/generate_resources.py` remains in the agent workflow catalogue. |

Windows x64 and Apple Silicon macOS images were not built locally. Their pinned
Community archives exist; CI would compile them with the shared recipe, audit the
retained members, load pinned bundled Time Attack through normal boot and validate
the packaged support libraries/mods/notices. It does not perform ROM-backed
rendering on hosted runners. No new Windows image was available for the Wine
qualification, so that check was skipped. Full friends images with every example,
full hosted Time Attack races and arbitrary third-party/JDK facilities remain
outside these bounded native checks. No native-only mod-kind rejection remains.

The four remaining guard findings reproduced at the pinned develop base. The
bounded command was `JAVA_HOME="$JDK25" LUA_BIN=lua5.4 python3
tools/testing/maven_queue.py -Dmse=off -Pguards
'-Dtest=TestRewindCoverageGuard,TestObjectUpdateClockTerminologyGuard,TestNoAssertionFreeDiagnostics,TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow'
test -B`: five tests / four failures / zero errors/skips. Assertion values and
owning source lines match; only the checkout prefix differs in path-bearing
terminology diagnostics.

| Inherited guard identity | Matching finding |
| --- | --- |
| `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline` | `MhzEndBossInstance#objectRef#hitProxy` is absent from the coverage baseline. |
| `TestObjectUpdateClockTerminologyGuard#objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks` | `TestTasS3kWaterSkimLifetime.java:210` uses `frame`, `TestTasS3kOffscreenSolid.java:41` uses `frame`, `TestTasS3kOrtExhaustion.java:60` uses `f`. |
| `TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow` | Added static debt: `HCZWaterSkimHandler.slotRetired` and `Aiz2BossEndSequenceState.cutsceneKnucklesSlot`. |
| `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` | `TestTestReelExtension#bodyPasses` lacks an assertion/verify/fail oracle. |

The two affected workflow-command guards and the release-tool safety gate pass
after their targeted repairs; the new staged-build guard also passes. The four
inherited findings above remain. These unrelated findings were not repaired or
allowlisted by the native-build promotion. Consumed category diagnostics were acknowledged and deleted. Both temporary
baseline checkouts, consumed one-off logs and disposable runtime identities were
removed after their results were recorded. Qualified screenshots,
state/audio observations and the refreshed Linux archive stay in the external
task artifact directory. The task branch is retained locally for review; it is
not merged or pushed, and no Release workflow was dispatched.

## Linux optimization and Oracle comparison (2026-10-27)

**Final result after the user-authorized larger local compiler heap:** `-Os`
cost **11.7% mean process CPU** and **8.4% mean elapsed frame time** versus default
`-O2` in the same rendered workload. Five-run mean ranges do not overlap.
**Recommend `-Os` on macOS only**, with an 8 GiB compiler heap for the larger-runner
recipe. The successful Linux `-O2` build took 267.48 s and peaked at 9.14 GiB
compiler RSS. Windows runtime/memory and macOS performance were not measured.
Oracle remains a local-only, runtime-infeasible old-recipe control; the licence
review above does not establish a compatible Oracle shipping route.

The [follow-up factor study](#native-performance-factor-study-2026-10-27) later
on this date measured updated Oracle recipes. The failed legacy recipe below
remains a historical control.

This local study compares the new Community recipe with and without `-Os`, the
previous closed-world Oracle recipe where feasible, and a warmed JVM reference.
Oracle is a **local comparison only**; the licensing decision above keeps
Community as the shipping choice. No workflow, merge, push or release was run.

### Workload and measurement boundaries

Engine source is `b9acb03ab` in the existing
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-b59ba251` worktree, plus the benchmark-only
entry points in this commit. Both Community variants consume the **same prepared
class/jar inputs**; the initial equal-heap argument files differ only by `-Os`
and output path. The successful comparison below uses the user-authorized 8 GiB
compiler-heap extension for `-O2`.
The default compiler reports optimization level **2**. The original recipe has a
5 GiB compiler heap, G1, `ActiveProcessorCount=3`, two workers, compatibility architecture,
runtime class loading and `-H:-PreserveIncludesJNI`. Its generated program uses
Serial GC; the compiler's G1 flag does not select the game's collector.

`GameplayBenchmark` uses `GameplayCaptureSession`'s production `GameLoop.step()`
and complete renderer, including `glFinish`, with no pacing and no pixel readback
inside the timing window. This differs deliberately from `TraceBenchmarkTool`'s
lower-level replay drive, which bypasses `GameLoop`; its existing `SectionTiming`
nearest-rank statistics and `TrajectoryDigest` are reused. The workload is native
S3 AIZ1, 320×224, solo Sonic, ordinary intro, donor off, audio disabled through
the no-device boot owner, and live rewind disabled. Controller input is the
committed `examples/second-strike/walkthroughs/routes/aiz1-sonic-solo-native-cold.bk2`
(SHA-256 `91f345666286b5d646f9e819054162e1b213f642ef28b63a625c690fa08f0b87`).
It supplies controller input, never trace gameplay state. Each pass drives 6,000
frames; the compared window is **zero-based frames 2000–5999** (4,000 frames),
covering a fixed section of the committed ordinary AIZ route. The actual
trajectory checks below, rather than older walkthrough annotations, identify the
work performed by this engine revision.
Native builds discard no JIT warmup; the first 2,000 frames are the traversal
needed to reach that same window. The JVM discards two additional complete passes
and the final traversal: **14,000 frames excluded** before its 4,000 measured
frames. Each pass boots and closes a fresh production session.

Elapsed frame time encloses update and completed rendering; it includes GPU waits
and is **not CPU time**. CPU time is the sum of user/system execution across all
process threads, read from Linux `clock_gettime(CLOCK_PROCESS_CPUTIME_ID)` through
the existing LWJGL JNI bridge. It includes driver, GC and any remaining JIT worker
CPU during that frame, and can exceed elapsed time. The same high-resolution
counter is used on native and JVM; no additional native library is built. Clock
sampling overhead is not subtracted. State hashing, readback, JSON and stdout are
outside the timed frame boundaries. Total frame time is the sum of those 4,000
samples, excluding boot/traversal/discarded passes and reporting overhead.

Startup is external process-launch to the first `BENCH_FIRST_FRAME` line, emitted
immediately after the first update and completed draw, before any discarded pass.
For Community this includes its mandatory retention audit. This is a **direct
level boot**, not the interactive title/menu/save-selection path. It uses ordinary
filesystem/driver cache state; no host caches were flushed. The first presented intro
frame can be a legitimate fade/black frame. A separate nonblank final-frame
readback and exact trajectory/object/pixel controls guard the measured workload.

Five fresh processes per feasible runtime run sequentially, rotating runtime
order between repetitions. `run_isolated.py` acquires this worktree, exclusive
Maven test admission, then the exclusive repository build-activity lease; its
0.5-second bypass guard must accept the complete batch. Images and JVM classes
are built **before** taking those measurement leases. Compilers run sequentially
in the queue's exclusive lane, outside the runtime measurement lease. Timings
exclude queue waits. GNU `time -v` records compiler elapsed time and maximum RSS;
RSS is the largest process's high-water value, not summed process-tree/host RAM
and not the 5 GiB Java heap alone. Build-resource numbers are one compilation per
variant; runtime spread comes from the five repetitions.

The original ROM is
`$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen`,
4,194,304 bytes, CRC32 `63522553`, SHA-1
`cfbf98c36c776677290a872547ac47c53d2761d6`. It was read in place; no ROM was copied,
renamed, linked, uploaded or committed.

### Rejected instrument and preliminary attempt

The first instrument used `OperatingSystemMXBean.getProcessCpuTime()`. Before
accepting any runtime numbers, inspection of the
[OpenJDK 25 Linux implementation](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/jdk.management/unix/native/libmanagement_ext/OperatingSystemImpl.c)
showed `times(2)` tick quantization: nanosecond units do not imply per-frame
resolution. It was replaced by the shared Linux CPU clock and a rendered JVM
functional control before compiling the final images. The initial two-frame
control ended during the intro fade and correctly rejected a blank framebuffer;
the control after the 2,000-frame traversal passed. Those functional timings are
not performance results.

The superseded MXBean instrument built `-Os` in 262.21 s at 6,310,976 KiB maximum
RSS, but its default `-O2` attempt failed with **Java heap space OOM** after
337.84 s (5,985,060 KiB maximum RSS), after method compilation. These are
preliminary graph-specific observations, not final runtime comparisons. The
corrected instrument changes the entry point graph, so the 5 GiB default build
was checked again on the final shared inputs rather than attributing that old
failure to new source without evidence.

### Commands and exact recipe differences

All commands ran from the task worktree, never the main checkout. In the commands
below, `WT` is `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-b59ba251`, `B` is
`$WT/target/native-benchmark`, and `GRAAL` is
`$WT/target/graalvm-toolchain/graalvm-community-25.4.4.1.1+1.1`.
`ROM_ROOT` denotes the original main-workspace directory, resolved as
`ROM_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"`.
The actual runtime arguments were expanded absolute paths; this neutral variable
representation preserves reproducibility without committing a machine-local home
path, as required by the repository content hook. No ROM link/copy was made.
The actual queued driver commands were:

```bash
JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py -Dmse=off exec:exec \
  -Dexec.executable=python3 '-Dexec.args=tools/native-image/build.py --stage prepare' -B
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py \
  -Dmse=off exec:exec -Dexec.executable=python3 \
  '-Dexec.args=target/native-benchmark/build_community.py' -B
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py \
  -Dmse=off exec:exec -Dexec.executable=python3 \
  '-Dexec.args=target/native-benchmark/build_oracle.py' -B
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py \
  -Dmse=off exec:exec -Dexec.executable=python3 \
  '-Dexec.args=target/native-benchmark/build_feasible.py' -B
```

The last driver was submitted three times: two bounded functional-control failures
(null `dlsym` handle rejected by LWJGL checks, then an intentionally nonblank check
made too early in the intro fade), then the corrected successful control and
final compilations. No duplicate live request was submitted. The temporary
queued drivers only orchestrated these operations; their substantive build
commands/argument generation are reproduced here rather than preserving logs or
private run directories:

```python
# Community preparation, then the exact same immutable input graph for both.
subprocess.run(['python3', 'tools/native-image/build.py', '--stage', 'prepare',
                '--graal', str(graal)], check=True)
original = [json.loads(line) for line in
            (root / 'target/native-mods/native-image.args').read_text().splitlines()]
for label in ['community-os', 'community-default']:
    arguments = [a for a in original if label == 'community-os' or a != '-Os']
    arguments[-1] = str(base / label / 'OpenGGF')
    argfile = base / (label + '.args')
    argfile.write_text('\n'.join(json.dumps(a) for a in arguments) + '\n')
    subprocess.run(['/usr/bin/time', '-v', '-o', str(base / (label + '-final.time')),
                    str(graal / 'bin/native-image'), '@' + str(argfile)],
                   cwd=root / 'target', stdout=build_log, stderr=subprocess.STDOUT)
```

Oracle download and identity inspection:

```bash
curl -fL --max-time 180 -o target/native-benchmark/oracle25.tar.gz \
  https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-x64_bin.tar.gz
tar -xzf target/native-benchmark/oracle25.tar.gz -C target/native-benchmark
sha256sum target/native-benchmark/oracle25.tar.gz
cat target/native-benchmark/graalvm-jdk-25.0.4+7.1/release
```

Oracle's temporary driver compiled `LegacyBenchmarkEngine.java` and the identical
`GameplayBenchmark.java` with its own `javac --release 25`, into
`$B/oracle-host-classes`. `CP_ORACLE` below was, in order,
`$B/oracle-host-classes:$WT/target/native-mods/host-classes:$WT/target/classes:`
followed by the exact contents of `target/jvm-runtime-classpath.txt`. It used the
ordinary LWJGL core of the previous profile, not Community's native-only unsafe
classifier. The measured Oracle recipe follows `develop`'s pinned
`ca939333924a19826156cce871e31a3befbd450b` native profile's initialization and
architecture flags, with a measurement launcher retaining `Engine.main` and
adding the shared benchmark. Its compiler alone was bounded to the same heap,
G1 and worker settings for local resource safety; it has no Community retention
audit, preserve graph or runtime-class-loading flag:

```bash
"$B/graalvm-jdk-25.0.4+7.1/bin/javac" --release 25 -cp "$CP_ORACLE" \
  -d "$B/oracle-host-classes" tools/native-image/LegacyBenchmarkEngine.java \
  tools/native-image/GameplayBenchmark.java
"$B/graalvm-jdk-25.0.4+7.1/bin/native-image" \
  -H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading --expert-options-all
/usr/bin/time -v -o "$B/oracle.time" \
  "$B/graalvm-jdk-25.0.4+7.1/bin/native-image" "@$B/oracle.args"
```

`oracle.args` contained these exact arguments (each JSON-quoted on its own line,
with `CP_ORACLE` expanded):

```text
--no-fallback
--enable-native-access=ALL-UNNAMED
--initialize-at-run-time=org.lwjgl
--initialize-at-run-time=java.awt,javax.swing,sun.awt,sun.java2d,com.apple.laf
-march=compatibility
-J-Xmx5g
-J-XX:-UseParallelGC
-J-XX:+UseG1GC
-J-XX:ActiveProcessorCount=3
--parallelism=2
-cp
$CP_ORACLE
com.openggf.tools.nativeimage.LegacyBenchmarkEngine
$B/oracle-legacy/OpenGGF
```

This is a **distribution + compiler-version + image-policy comparison**, not an
isolated attribution to the vendor: Oracle is 25.0.4+7.1 with ML-inferred PGO and
the closed-world profile, whereas Community is 25.4.4.1.1+1.1 with runtime loading
and a much larger retention graph. The requested `-Os` comparison is the matching
Community pair. Startup/audit and image-size differences must be read in that
context. It is not a benchmark of the historical Java 21 release binary.

The runtime launchers use these shared properties:

```text
-Dopenggf.headless.gl=surfaceless-egl
-Dorg.lwjgl.librarypath=$WT/target
-Dopenggf.installDir=$WT/target
-Dopenggf.saveRoot=$B/saves
```

Native launch arrays are the chosen `$B/<variant>/OpenGGF`, those properties, and
`--benchmark-gameplay`. JVM launch is `/usr/lib/jvm/java-25-openjdk/bin/java`,
`--enable-native-access=ALL-UNNAMED -Xms512m -Xmx2g -XX:+UseG1GC`, the same
properties, `-cp`, then
`$WT/target/native-mods/host-classes:$WT/target/classes:<native_dependencies>`, and
`com.openggf.tools.nativeimage.GameplayBenchmark`. `native_dependencies` is
`build.native_dependencies(Path('target/native-runtime-classpath.txt').read_text().strip())`:
the same upstream unsafe LWJGL JNI core as the Community images. This avoids
changing the JNI/FFM path in the primary native/JVM comparison.

`native_matrix.py` appends the absolute original ROM, committed BK2 and per-run
JSON path, then `2000 4000 0` for native or `2000 4000 2` for JVM. It removes
`DISPLAY`, `WAYLAND_DISPLAY`, `JAVA_TOOL_OPTIONS` and `JDK_JAVA_OPTIONS` from each
child environment. The launch arrays are written as JSON to `$B/launchers.json`;
only successfully compiled final images are included. The accepted matrix command
and results follow below.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/performance/run_isolated.py \
  --audit-report target/native-benchmark/isolation.json --estimate-seconds 360 -- \
  python3 tools/performance/native_matrix.py \
  --launchers target/native-benchmark/launchers.json \
  --rom "$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --bk2 examples/second-strike/walkthroughs/routes/aiz1-sonic-solo-native-cold.bk2 \
  --output target/native-benchmark/runs --runs 5 --traversal 2000 --frames 4000
```

The first three-runtime batch acquired both exclusive leases after 32 s and
executed one Community pass, then failed on Oracle before its first gameplay
frame. Oracle's primary failure is `NoSuchElementException` in LWJGL
`org.lwjgl.system.ffm.BCCallUp` while creating the GLFW error callback; teardown
also reports `UnsupportedFeatureError` trying to define
`org/lwjgl/system/JNIBindingsImpl` in a closed-world image. The compiled Oracle
image therefore supplies a size/build observation, **not gameplay or startup
performance numbers**. No fallback to another LWJGL classifier or enabling runtime
loading was attempted: either would change the requested old recipe. This does
not establish a failure of the Java 21/LWJGL 3.3.3 binaries released in 0.6.

A new complete two-runtime batch excluded that infeasible Oracle launcher and
used the same command above with `--estimate-seconds 240`,
`--audit-report target/native-benchmark/isolation-feasible.json`, and
`--output target/native-benchmark/runs-feasible`. The aborted batch's single
Community result is excluded from the predefined five-run primary summary;
it is not a selected best run. All ten fresh processes in the complete batch
must finish and pass the work-equality controls for that summary to be accepted.

### Machine, input identity and isolation outcome

Host: CachyOS Linux, kernel `7.3.0-5-cachyos`, glibc 2.44; AMD Ryzen 9 9950X,
16 cores / 32 logical CPUs; 31,919,976 KiB RAM reported (30.44 GiB); CPU governor
`powersave`, boost enabled, no fixed frequency or CPU affinity. The actual EGL
renderer was **AMD Ryzen 9 9950X integrated graphics**, `radeonsi`,
`raphael_mendocino`, ACO, DRM 3.64, **Mesa 26.2.5-arch3.1**, OpenGL 4.6 compatibility.
The separate Radeon RX 9070-family GPU was not the selected renderer. Native
compilation used GCC 16.2.1. JVM reference: Arch OpenJDK **25.0.5.0.2**, release
date 2026-10-22, explicit G1 and 512 MiB initial / 2 GiB maximum heap. This is a
single Linux machine and one viewport/route, not macOS/Windows runtime or memory
qualification. The desktop and unrelated system services remained running
(including an approximately one-core background Python service); no unrelated
process was killed. Isolation excludes linked-worktree build/test activity, not
all host scheduling/graphics noise. Five-run spread is reported rather than
assuming a perfectly idle host.

Prepared engine jar SHA-256:
`2df66c0c871e1b07e7b220788dfd2813b43edf6844b72081b5c5a0a7132c70d7`.
`GameplayBenchmark.java` SHA-256:
`babddd8877c5317d336a60849ebf69b5ec10df7ae0eb4832f43d58d970970526`.
Community/JVM and Oracle compilers emitted byte-identical benchmark main classes
(SHA-256 `f9d7bd5a7d09d920ff20179ef47826cba42f74379318eb076514e04b8869329f`).

The accepted batch waited **1 s for admission**, then ran for **119.63 s**
(2026-10-27 **15:25:47.772–15:27:47.405 UTC**). Its audit reports exclusive test
and build-activity leases, exit 0 and **no observed bypass interference** at the
0.5 s sampling interval. All ten processes completed, with five fresh processes
per runtime and 4,000 measured frames each. This is measurement validation, not
a test-suite pass; no ROM/graphics skip mechanism is used by this command.

Every run matched trajectory `b20b4a74af557992`, **83,873** cumulative active-object
observations (20.97 objects/frame), final centre **(2928, 939)**, and final
framebuffer hash **2002713451**. The final framebuffer was nonblank. These checks
prove the compared work agrees for this harness/window, not ROM parity or the
completion of the older annotated walkthrough's intended route.

### Initial accepted `-Os` / JVM reference (before the heap extension)

The following summary uses the **median of the five per-run statistics** for
mean, median, p99 and total. The maximum column is the **worst individual frame
observed across all five runs**, not the best/median run maximum. CPU and elapsed
values have different meanings and must not be substituted for one another.

| Runtime | CPU mean ms/frame (run-mean range) | CPU median ms | CPU p99 ms | Worst CPU frame ms | CPU total s / 4,000 frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| Community 25.4 `-Os` | 0.805 (0.797–0.811) | 0.670 | 3.599 | 35.386 | 3.219 |
| Warmed OpenJDK 25 | 0.508 (0.496–0.520) | 0.335 | 3.183 | 44.901 | 2.032 |
| Community default `-O2`, 5 GiB control | Not measurable: 5 GiB compiler-heap OOM | — | — | — | — |
| Oracle 25.0.4 old recipe | Not measurable: LWJGL FFM startup failure | — | — | — | — |

| Runtime | Elapsed mean ms/frame (run-mean range) | Median ms | p99 ms | Worst frame ms | Elapsed total s / 4,000 frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| Community `-Os` | 1.165 (1.158–1.174) | 1.080 | 2.579 | 32.809 | 4.658 |
| Warmed JVM | 0.805 (0.791–0.808) | 0.755 | 2.040 | 20.169 | 3.221 |

| Build/runtime | Launch → first completed gameplay draw, s (median; range) | Executable bytes / MiB | Compiler time | Compiler max RSS | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| Community `-Os` | 0.260; 0.252–0.377 | 304,220,624 / 290.13 | 256.31 s | 6,304,932 KiB / 6.01 GiB | Built and five runs completed |
| Community default `-O2`, 5 GiB control | No first frame | No executable | 311.52 s | 6,024,804 KiB / 5.75 GiB | Exit 3: Java heap space OOM |
| Oracle old recipe | No first frame | 73,533,832 / 70.13 | 114.26 s | 4,553,232 KiB / 4.34 GiB | Built; gameplay launch exit 1 |
| JVM | 0.770; 0.717–0.822 | No standalone executable | Not measured (prebuilt classes) | Not measured | Five warmed runs completed |

The existing `OpenGGF-0.7.prerelease-jar-with-dependencies.jar` is 32,762,438 bytes
(31.24 MiB), a reference packaging size, **not** the exact class-path benchmark
launcher and not inclusive of a JVM/JDK. The measured JVM launched prepared
classes plus dependencies and benchmark host classes. Native sizes are executable
files only; Community's adjacent support libraries, bundled content and LWJGL
libraries are additional. The outer final Maven driver ended successfully
because it continued to Oracle after recording Community `-O2` exit 3; its overall
`BUILD SUCCESS` is **not** an `-O2` build pass.

Per-run details below preserve the spread, including maxima. Each row represents
one fresh process; totals cover its measured 4,000-frame window.

| Runtime / repetition | CPU mean / median / p99 / max ms | CPU total s | Elapsed mean / median / p99 / max ms | Elapsed total s | Startup s | Whole process s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CE `-Os` 1 | 0.810636 / 0.674243 / 3.599358 / 35.364421 | 3.242544 | 1.174442 / 1.086374 / 2.634743 / 32.808780 | 4.697766 | 0.376777 | 6.943 |
| CE `-Os` 2 | 0.797272 / 0.660613 / 3.612669 / 35.386092 | 3.189087 | 1.157770 / 1.071965 / 2.579167 / 32.604727 | 4.631079 | 0.343267 | 6.766 |
| CE `-Os` 3 | 0.804666 / 0.672983 / 3.540846 / 35.022882 | 3.218663 | 1.162750 / 1.083056 / 2.516666 / 32.541556 | 4.651000 | 0.260093 | 6.718 |
| CE `-Os` 4 | 0.802738 / 0.670323 / 3.477368 / 34.646024 | 3.210952 | 1.164633 / 1.079915 / 2.524756 / 32.165297 | 4.658532 | 0.252194 | 6.691 |
| CE `-Os` 5 | 0.805232 / 0.658123 / 3.671407 / 34.225663 | 3.220927 | 1.164584 / 1.069295 / 2.636008 / 31.775461 | 4.658337 | 0.252837 | 6.703 |
| JVM 1 | 0.508009 / 0.338482 / 3.183067 / 40.975511 | 2.032036 | 0.805245 / 0.754959 / 2.047346 / 19.620666 | 3.220978 | 0.822094 | 17.189 |
| JVM 2 | 0.520015 / 0.335101 / 3.152834 / 39.864412 | 2.080062 | 0.807376 / 0.757741 / 2.038550 / 19.785885 | 3.229506 | 0.751376 | 17.163 |
| JVM 3 | 0.518903 / 0.336532 / 3.216087 / 44.901341 | 2.075612 | 0.808496 / 0.757311 / 2.084540 / 20.169028 | 3.233985 | 0.782470 | 17.179 |
| JVM 4 | 0.496407 / 0.322280 / 3.207619 / 21.273308 | 1.985627 | 0.794953 / 0.746971 / 2.035279 / 16.981961 | 3.179812 | 0.769813 | 17.095 |
| JVM 5 | 0.503106 / 0.319523 / 3.103255 / 24.954162 | 2.012424 | 0.791146 / 0.742320 / 2.040339 / 17.453726 | 3.164585 | 0.716643 | 16.935 |

### Local compiler-heap extension authorized by the user

After the 5 GiB default-optimization failure, the user explicitly requested a
larger local `-O2` compiler heap. The next build replaces **only `-J-Xmx5g` with
`-J-Xmx8g`** in the prepared default-optimization argfile. This relaxes the original
compiler-resource constraint; it does not raise the game's heap, change its GC,
change the source/class inputs or select different runtime flags. The earlier
5 GiB failure remains a build-policy observation rather than a reason to infer
runtime performance. The previously completed JVM reference remains valid.
A fresh rotated five-run `-Os`/`-O2` batch provides the contemporaneous paired
comparison after the new image built; the earlier reference batch is retained
above rather than substituted for the new pair.

```bash
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$GRAAL" python3 tools/testing/maven_queue.py \
  -Dmse=off exec:exec -Dexec.executable=python3 \
  '-Dexec.args=target/native-benchmark/build_o2_8g.py' -B
```

That temporary driver executes:

```bash
/usr/bin/time -v -o "$B/community-default-8g.time" \
  "$GRAAL/bin/native-image" "@$B/community-default-8g.args"
```

The new argfile is the already prepared `community-default.args` with that one
compiler heap replacement. It retains compatibility architecture, G1 compiler,
ActiveProcessorCount=3, two workers, runtime loading, the preservation graph,
`-H:-PreserveIncludesJNI` and the existing default optimization level 2.

### Completed Community `-Os` / `-O2` comparison

The authorized 8 GiB `-O2` image **built successfully**, exit 0. It took **267.48 s**
and reached **9,579,680 KiB / 9.14 GiB** maximum compiler RSS. Its executable is
**337,054,160 bytes / 321.44 MiB**. The `-Os` control is unchanged from the earlier
qualified image: **256.31 s**, **6,304,932 KiB / 6.01 GiB** compiler RSS,
**304,220,624 bytes / 290.13 MiB**. Thus `-Os` saves **31.31 MiB (9.7%)** of executable
size relative to `-O2`, and 3.12 GiB of observed compiler RSS. The larger heap
changes a compiler process setting only; neither game's runtime heap/GC changed.

Argfile comparison verified that only `-Os` removal, `-J-Xmx5g` → `-J-Xmx8g`,
and output path differ. The engine jar and benchmark source hashes recorded above
were rechecked unchanged. No Maven preparation, compilation or source edit
occurred inside the measurement lease.

The final pair used the same native launch array/properties and committed BK2 as
above, with two launch labels, `community-os` and `community-default`, both ending
in `--benchmark-gameplay`. The exact command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/performance/run_isolated.py \
  --audit-report target/native-benchmark/isolation-pair.json --estimate-seconds 180 -- \
  python3 tools/performance/native_matrix.py \
  --launchers target/native-benchmark/launchers-pair.json \
  --rom "$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --bk2 examples/second-strike/walkthroughs/routes/aiz1-sonic-solo-native-cold.bk2 \
  --output target/native-benchmark/runs-pair --runs 5 --traversal 2000 --frames 4000
```

The accepted paired batch ran **66.93 s**, **15:39:31–15:40:38 UTC**
on 2026-10-27. Both exclusive leases were held, the bypass guard observed no
interference, and the audit exited 0. All ten processes matched the same trajectory
`b20b4a74af557992`, 83,873 object observations, final centre (2928, 939), and
nonblank pixel hash 2002713451. No run or outlier was dropped.

The summary below is the **median of five per-run statistics**, except the max
column, which is the **worst individual frame across all five**. JVM is the
already completed five-run reference from 15:25–15:27 UTC, not an additional run
in the paired native batch. The earlier `-Os` reference cohort and all raw
per-run summary values remain above, so the cohorts are explicit.

| Runtime | CPU mean ms/frame (range of run means) | CPU median ms | CPU p99 ms | Worst CPU frame ms | CPU total s / 4,000 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Community `-Os`, compiler 5 GiB | 0.833 (0.822–0.875) | 0.696 | 3.651 | 35.390 | 3.331 |
| Community `-O2`, compiler 8 GiB | 0.746 (0.733–0.759) | 0.605 | 3.655 | 32.934 | 2.983 |
| Warmed JVM reference | 0.508 (0.496–0.520) | 0.335 | 3.183 | 44.901 | 2.032 |

| Runtime | Elapsed mean ms/frame (range of run means) | Median ms | p99 ms | Worst frame ms | Elapsed total s / 4,000 | Startup s (median; range) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Community `-Os`, compiler 5 GiB | 1.200 (1.182–1.248) | 1.108 | 2.690 | 32.960 | 4.799 | 0.261; 0.250–0.379 |
| Community `-O2`, compiler 8 GiB | 1.107 (1.095–1.124) | 1.026 | 2.625 | 30.624 | 4.429 | 0.227; 0.226–0.368 |
| Warmed JVM reference | 0.805 (0.791–0.808) | 0.755 | 2.040 | 20.169 | 3.221 | 0.770; 0.717–0.822 |

Final paired run details (five per image):

| Runtime / repetition | CPU mean / median / p99 / max ms | CPU total s | Elapsed mean / median / p99 / max ms | Elapsed total s | Startup s | Whole process s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CE `-Os` 1 | 0.825853 / 0.684143 / 3.651057 / 35.310718 | 3.303414 | 1.191295 / 1.097275 / 2.665465 / 32.959562 | 4.765180 | 0.378792 | 7.021 |
| CE `-Os` 2 | 0.822059 / 0.683254 / 3.650407 / 35.390424 | 3.288236 | 1.182099 / 1.095275 / 2.629965 / 32.719788 | 4.728396 | 0.249717 | 6.805 |
| CE `-Os` 3 | 0.832681 / 0.695764 / 3.581695 / 34.794236 | 3.330725 | 1.199728 / 1.107865 / 2.689765 / 32.174271 | 4.798911 | 0.249914 | 6.865 |
| CE `-Os` 4 | 0.841022 / 0.696604 / 3.712298 / 35.337464 | 3.364089 | 1.204614 / 1.111445 / 2.790697 / 32.816209 | 4.818458 | 0.267465 | 6.872 |
| CE `-Os` 5 | 0.875386 / 0.736504 / 3.727067 / 34.824412 | 3.501542 | 1.248297 / 1.151925 / 2.819497 / 32.340633 | 4.993187 | 0.260802 | 7.162 |
| CE `-O2` 1 | 0.733344 / 0.587863 / 3.659559 / 32.933594 | 2.933375 | 1.096041 / 1.005663 / 2.702885 / 30.623621 | 4.384166 | 0.367997 | 6.435 |
| CE `-O2` 2 | 0.735032 / 0.601313 / 3.556167 / 30.491014 | 2.940128 | 1.095324 / 1.011103 / 2.543284 / 28.059878 | 4.381297 | 0.226407 | 6.310 |
| CE `-O2` 3 | 0.745780 / 0.611302 / 3.662127 / 30.661232 | 2.983119 | 1.107174 / 1.025563 / 2.625354 / 28.274320 | 4.428695 | 0.226055 | 6.365 |
| CE `-O2` 4 | 0.759147 / 0.619723 / 3.654885 / 30.608651 | 3.036590 | 1.123730 / 1.036414 / 2.628174 / 28.067808 | 4.494919 | 0.226718 | 6.461 |
| CE `-O2` 5 | 0.752680 / 0.605132 / 3.652008 / 31.634339 | 3.010719 | 1.118581 / 1.026244 / 2.549573 / 29.013639 | 4.474324 | 0.230769 | 6.408 |

### Interpretation and recommendation

**`-Os` has a meaningful mean runtime cost on this Linux workload.** Comparing
the medians of per-run means, it consumed **11.7% more process CPU per frame**
(0.832681 vs 0.745780 ms, +0.086901 ms) and **8.4% more elapsed frame time**
(1.199728 vs 1.107174 ms, +0.092554 ms). All five `-O2` run means are below all five
`-Os` run means, for both metrics; the gap exceeds the within-build observed
spread. This supports the mean-throughput conclusion despite the unpaced GPU
waits and ordinary host activity. Median frame CPU is about 15% higher under
`-Os`. The absolute mean cost is small against a 16.67 ms presentation budget,
but 12% extra process CPU is material when the larger runner can build `-O2`.

**Startup and p99 rankings are inconclusive.** Their differences are smaller than
the five-run ranges. CPU p99 is effectively equal (3.651 vs 3.655 ms); elapsed p99
is 2.690 vs 2.625 ms with overlapping ranges. Startup medians are 0.261 vs 0.227 s,
but ranges overlap broadly (0.250–0.379 vs 0.226–0.368 s). Worst observed elapsed
frames are 32.960 vs 30.624 ms; neither the low mean nor this small sample certifies
all frames within a 60 Hz budget. No timing event was attributed to an engine
subsystem by this study. JVM results are a warmed reference, not an estimate of
the `-Os` penalty or a claim that AOT/JIT/compiler-vendor effects are isolated.

**Recommendation: use `-Os` on macOS only.** Keep the small-runner 5 GiB compiler
recipe there; use default `-O2` and an **8 GiB compiler heap** for the 16 GiB
Linux/Windows runner class. The successful local Linux peak of 9.14 GiB compiler
RSS is below 16 GiB and leaves substantial room compared with the 7 GiB class,
while startup/p99 data supplies no reason to trade away the measured mean CPU
benefit. This is a recommendation from Linux evidence: actual Windows build peak
and runtime, and ARM64 macOS peak/performance, still need their platform validation.
CI's existing separate prepare/compile steps should remain, avoiding a Maven heap
overlapping the compiler. This test task did **not** change shipping build flags.

Community remains the shipping distribution. Oracle's exact downloaded licence
permits these local tests, but the GPLv3 redistribution analysis above remains
unresolved for actual incorporated components; the old closed-world Oracle recipe
also failed current LWJGL startup. No Oracle gameplay-performance number is claimed.

Focused validation: final preparation's generated-member regression controls and
24,827-entry audit passed. Final `-Os` and `-O2` native images built and the accepted
paired batch passed ten processes / 40,000 measured frames, plus the separate
five-process JVM reference and earlier native cohort; all work/pixel controls
matched with no skips. The four native Python controls, Python compile/CLI parse,
argument/source identity, local links and whitespace checks passed. The 5 GiB
`-O2` OOM and Oracle startup failure remain explicitly failed controls, not passes.
No engine algorithm, POM, selection policy or shipping recipe changed: these are
bounded benchmark/launcher checks, not a smoke/full-suite, ROM-parity or release
qualification. No merge, push or workflow dispatch was performed.

Tested executable SHA-256 values: `-Os`
`1a090f9484f94cee84c1a57bb5e2ecf6c3f04b6d47cbd63707d4f77771a62339`;
`-O2` `968b9a248918d8898bf93db41f509db2b2c02681cca4193b8f4e5ce8d594be01`.


## Final platform recipe and updated-branch qualification (2026-10-27)

The user adopted **`-Os` / 5 GiB on macOS only, default `-O2` / 8 GiB on
all other platforms**. Commit `06b093edcff710662fae51f37fcc9aa5bdebc7d5`
applies this in `tools/native-image/build.py`, shared by releases, local native
builds and friends builders. All targets retain compiler G1,
`ActiveProcessorCount=3` and `--parallelism=2`; `OPENGGF_NATIVE_IMAGE_HEAP`
continues to override the heap. The standard `macos-15` runner is unchanged;
Linux and Windows use their standard 16 GiB runners with conservative two-worker
compilation. The final Linux image again reported optimization level 2 and
**9.14 GiB peak compiler RSS** with the 8 GiB heap.

The feature branch merged develop
`d2f3ce1e3c3165034b1cbafb4c1792f5472fdc5f` in `dcba6ed63`.
Both additive conflicts were preserved: native-image and atlas ignore exceptions,
and the two independent measurement-hazard notes. Qualification below used engine
source `4bf1fd22d57a2018a7709ce6b98aedaf19f2ccf8`. The final follow-up only lowers
the dispatcher test budget and records evidence; engine/native inputs are unchanged.
The always-true capability arguments, unused rejection helper/reason and obsolete
notice screen/dispatch are removed. `GameMode.NATIVE_MOD_NOTICE` remains solely
as a reserved compatibility enum value because it is already in the API pin;
no API signature pin changed. The existing native-build changelog bullet was
refined; root README and feature-guide prose were not edited.

### Completed commands and scope

`UPDATED_BASE` below is the full develop SHA above. `WT`, `GRAAL`, `JDK25`,
`ROM_ROOT`, `ROM_ARGS`, `S1`, `S2`, `S3K` and `ARTIFACTS` have the meanings used
in the preceding command records. Actual ROM arguments were absolute original
paths; the three stock ROM CRC32/SHA-1 values matched the repository identities.
No ROM was copied, renamed, uploaded or newly linked. Linux rendering and Java
checks unset `DISPLAY` and `WAYLAND_DISPLAY`; Java checks selected JDK 25 and
`LUA_BIN=lua5.4`. `FAILED_ORDINARY_METHODS` means exactly the ordinary methods
in the table below, grouped as Surefire `Class#method+method` selectors with the
`com.openggf.` prefix restored. The parameterized SOZ method selector includes its
four passing sibling rows. `FAILED_GUARD_METHODS` means the four guard methods below.
Baseline checks temporarily detached this same worktree at `UPDATED_BASE`, then
restored the feature branch; no additional checkout or main-workspace update was
made. Generated compilation outputs were cleared at both source transitions.

| Command | Observed result |
| --- | --- |
| `python3 -m unittest discover -s tools/native-image -p 'test_*.py' -v` | 5 passed, no skips; platform selection, heap override, prepared-stage reuse and fail-closed toolchain/dependency controls. |
| `python3 -m unittest discover -s tools/modding/native-linux -p 'test_*.py' -v` | 3 passed, no skips. |
| `python3 -m unittest discover -s tools/modding/native-windows -p 'test_*.py' -v` | 3 passed, no skips; portable packaging checks, not a Windows image build. |
| `python3 tools/testing/maven_queue.py -Dmse=off test-compile -B` | Passed after caller migration; production and test compilation completed. |
| `python3 tools/testing/run_categories.py --base "$UPDATED_BASE" --headless-gl surfaceless-egl --rom-directory "$ROM_ROOT" --preflight` | Passed Java 25, Lua 5.4 and PowerShell prerequisites; no tests executed. |
| `JAVA_HOME="$GRAAL" OPENGGF_MAVEN_QUEUE=serial python3 tools/testing/maven_queue.py -Dmse=off exec:exec -Dexec.executable=mvn '-Dexec.args=-Dmse=off -Pnative -Dmaven.test.skip=true package -B'` | Final Linux build exit 0; tests intentionally disabled for packaging. Generated contract regression controls and 24,827-entry / 1,991-type audit passed; runtime class loading enabled; optimization level 2, 8 GiB heap, two workers, 9.14 GiB compiler peak RSS. |
| `"$WT/target/OpenGGF" --audit` | Exit 0; final embedded member contract passed. |
| `"$WT/target/OpenGGF" --check-engine time-attack` | Exit 0; pinned bundled Time Attack loaded and registered through Engine's normal boot in a disposable working directory. |
| `javac --release 25` for Hello Scene, then `GgfModCli package --input "$HELLO_CLASSES" --out "$HELLO_JAR"` | Compiled against the prepared engine/runtime classpath; packaged with zero validation findings. Resources copied beside the classes; jar installed only in the disposable probe's `mods/hello-scene.jar`. |
| `"$WT/target/OpenGGF" -Dopenggf.headless.gl=surfaceless-egl --check-gameplay hello-scene "$ARTIFACTS/captures/hello-scene" "$S1" "$S2" "$S3K"` | Exit 0; real rendered scene, no owner findings/disabled owners, module-state roundtrip and scene reopen. Three PNGs retained outside the repo; gameplay PNG visually inspected. HeadlessSmpsAudioBackend used no-device audio. No audio-quality claim. |
| `python3 tools/testing/run_categories.py --base "$UPDATED_BASE" --headless-gl surfaceless-egl --rom-directory "$ROM_ROOT" --max-minutes 240 --run` | Completed all 4,235 candidates / 85 ordinary batches and the separate guard lane. Ordinary: 41,305 tests, 42 failures, zero errors, 81 skips. Guards: 795 tests, four failures, zero errors/skips. Exit 1 from assertions; no timeout or unrun batch. Smoke excludes slow-suite, exhaustive-audit and separate trace/native profiles; this is not full-suite certification. |
| At `UPDATED_BASE`: `python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards "-Dtest=$FAILED_GUARD_METHODS" test -B` | Four cases: three identical failures, one pass, zero errors/skips. The dispatcher budget failure is new; the other three already fail on develop. |
| At `UPDATED_BASE`: `python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=$FAILED_ORDINARY_METHODS" "${ROM_ARGS[@]}" test -B` | 46 cases: all 42 candidate failure identities reproduce with identical kind, exception type and assertion message; four parameterized sibling rows pass. Zero errors/skips. |
| Restored feature: `python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards '-Dtest=com.openggf.tests.TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets' test -B` | Passed after lowering the exact budget from 276 to 263; one test, zero failures/errors/skips, fresh production/test compilation. No engine change or repeated native/broad smoke run was needed for this test-only correction. |

Two early compilation attempts failed on newly obsolete positional capability
arguments: native preparation found `ModTestKit`, and test compilation found
`TestPuttPuttParadise`, `TestEditorCampaign` and a duplicate native-only UI helper
call. These were **new, fixed** caller-migration errors before the final build and
complete category run; final production/test compilation and native build passed.

All 81 skips were inspected. There were 32 act/scenario assumptions about absent
platforms or bounce targets, nine explicit HUD/Stageplay picture refreshes, two
CNZ visual checks without initialized capture environments, ten absent packaged
Second Strike inputs, two unrequested local reference/timeline checks, and 25
opt-in diagnostics, measurements, captures, soak/isolation checks. The remaining
one spin-tube check skipped because the configured tube did not capture/release
Sonic. None reported a missing stock ROM. These skipped checks remain coverage
limits, not passes.

### Failure attribution

Every ordinary row below is **already on develop** at `UPDATED_BASE`, matched
one for one by exact case identity, failure kind, exception type and message.
Identities omit only the `com.openggf.` prefix. Guard attribution is separate.
There are **45 remaining inherited failures** (42 ordinary, three guards) and
**no remaining new or worsened failures**. The complete red run is not described
as green.

<details>
<summary>All ordinary failure identities and matched assertions</summary>

| Identity | Matching assertion on candidate and develop | Attribution |
| --- | --- | --- |
| `game.sonic3k.objects.TestCnzMinibossRegistered#numericSlotIsImplementedInBothPointerTables` | `expected: <true> but was: <false>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesBothTransportersWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoTraversesUpperTransportChainsAndEastHubWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoDefeatsGravityBossAndLoadsFinalStageWithoutTransformation` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldRouteClearsAllTurbinePanelsAndOpensDoorWhileObjectControlled` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoDescendsInvertedSpringShaftWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldUpperRouteTraversesLiftsConveyorsAndTimedBridgesWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezColdRouteCapture#coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind` | `expected: <9589> but was: <3203>` | Already on develop |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `death at 19930 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `death at 14721 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `death at 36000 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `death at 21162 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `death at 21162 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloActTwoColdRouteCapture#coldWideSonicAloneClearsActTwoAndLoadsFinalStage` | `death at input 36000 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloActTwoColdRouteCapture#coldTailsAloneClearsActTwoAndLoadsFinalStage` | `death at input 19930 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloActTwoColdRouteCapture#coldTailsLowerGravityPadPreservesPendingContactAcrossRewind` | `death at 19930 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloActTwoColdRouteCapture#coldSonicAloneClearsActTwoAndLoadsFinalStage` | `death at input 14721 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloColdRouteCapture#coldSonicAloneClearsMinibossAndReachesPlayableActTwo` | `death at input 14721 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloColdRouteCapture#coldTailsAloneClearsMinibossAndReachesPlayableActTwo` | `death at input 19930 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestDezSoloColdRouteCapture#coldWideSonicClearsBothBossPhasesAndReachesPlayableActTwo` | `both eight-hit phases must be defeated through player contact ==> expected: <2> but was: <0>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoUpperClimbsAndDoorRelease` | `death at input 5027 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReplaysIncomingRouteAndFirstActTwoDoor` | `first-door death at input 15323 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `death at input 5027 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesReachesMinibossAndRestoresTraversalAndCloudEscape` | `death at input 15323 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesClearsMinibossAndRestoresFightAndActTwoHandoff` | `death at input 15323 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestLrzKnucklesColdRouteCapture#coldKnucklesRestoresActTwoTraversalToTheMiddleCorridor` | `death at input 5027 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActOneAndRestoresNativeInteractions` | `cold Knuckles death at 26853 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline` | `cold Knuckles death at 26853 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestS3kHpzJoinedColdRouteCapture#coldKnucklesReplaysUpperPadAndTheJoinedSszCraneControlRelease` | `cold death 5027 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 44639 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestS3kLrzHpzSszColdCampaign#tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 80320 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestS3kLrzHpzSszColdCampaign#pairColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory` | `cold death 73285 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestS3kLrzHpzSszColdCampaign#knucklesColdCampaignClearsSkySanctuaryTwoThroughTheEndingAndTitle` | `cold death 5027 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestSozColdRouteCapture#soloColdActCompletesWithTraversalReplayAndPlayableDestination(int, String)[5]` | `cold death at 15850 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestSozColdRouteCapture#knucklesColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef` | `cold death at 11787 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestSozColdRouteCapture#knucklesWideColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef` | `cold death at 23478 ==> expected: <false> but was: <true>` | Already on develop |
| `tools.TestSszWidePairedColdRouteCapture#coldFirstReplicaAndTransportReplayWithRetiredColumnDebris` | `expected: <46> but was: <48>` | Already on develop |
| `tools.TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | `FIRST_FORWARD [wide-route-11640] frame=11640 n=24 input=11664 ==> expected: <false> but was: <true>` | Already on develop |

</details>

| Guard identity | Result and attribution |
| --- | --- |
| `game.rewind.coverage.TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline` | **Already on develop.** New rewind-coverage gap keys introduced (not in baseline):<br>  com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance#objectRef#hitProxy<br>Fix the object&#x27;s rewind coverage, or (only if intentional) add the key(s) to src/test/resources/rewind/coverage-baseline.txt. ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |
| `tests.TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets` | **New, fixed.** Develop passes at 276 lines; cleanup reduces the method to 263. Budget lowered to 263 and the exact guard rerun passed. |
| `tests.TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow` | **Already on develop.** S3K static session state changed; migrate new active-object/phase bridges to runtime-owned state or document why the release debt list changed<br>  added:<br>  src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java - private static boolean slotRetired;<br>  src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java - private static volatile int cutsceneKnucklesSlot = -1;<br>  removed:<br>   |
| `tests.rules.TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree` | **Already on develop.** Assertion-free @Test methods found (add a real oracle, demote off @Test, or allowlist with a reason in TestNoAssertionFreeDiagnostics):<br>  TestTestReelExtension#bodyPasses has no assertion/verify/fail oracle (src/test/java/com/openggf/tests/reel/TestTestReelExtension.java) ==&gt; expected: &lt;true&gt; but was: &lt;false&gt; |

Windows and macOS images remain **unverified locally** for this final recipe.
CI would build Windows on `windows-latest` with default `-O2` / 8 GiB and macOS
on standard `macos-15` with `-Os` / 5 GiB, using the same G1/two-worker builder.
Each runs the generated and finished-image member audit, bundled Time Attack
normal-boot check, and packaged-library/mod/licence validation. Hosted CI does
not perform ROM-backed rendering. Windows/macOS memory peaks and gameplay remain
unmeasured here. No workflow was dispatched, and no branch was pushed or merged
into develop, next or master. The task worktree/branch remain for review.

## Native performance factor study (2026-10-27)

**Five isolated runs per build found no demonstrated Oracle `-O3` speedup.**
All A–F builds ran successfully. E used 1.7% more median run-mean CPU than A,
an inconclusive difference; its 3.5% CPU reduction versus Oracle `-O2` was also
inconclusive. Removing native runtime mod support reduced CPU by **18.9%** and
elapsed time by **12.9%**. The warmed JVM used **26.4% less CPU** than A.

This follow-up answers the local Oracle `-O3` and warmed-JVM questions; it does
not reopen distribution policy. Shipping remains Community with `-O2` on Linux
and Windows and `-Os` on macOS. Oracle output is local, disposable and never part
of a package or workflow. No merge, push or workflow dispatch is authorized.

### Inputs and controls

Engine source is `6898197b2bd7eba6903b489e853cd448482c7fb8` on the existing
`feature/ai-default-native-code-mods-20261027` task worktree. No other checkout
was created or modified. `compile` reported all classes current. Native
preparation regenerated the same **24,827-member** contract and passed its
regression controls and JVM audit. All variants use that one immutable prepared
engine input and the unchanged `GameplayBenchmark.java` from the preceding
study, SHA-256 `babddd8877c5317d336a60849ebf69b5ec10df7ae0eb4832f43d58d970970526`.
Prepared `engine-input.jar` SHA-256 is
`a224e29832238be6c4727939b4a6993154d3608c57eb3a6cc2a9cd3d9c54f530`;
the benchmark class remains
`f9d7bd5a7d09d920ff20179ef47826cba42f74379318eb076514e04b8869329f`.
Before measurement, all **9,315 engine class files** on F's classpath were
compared byte-for-byte with the prepared native engine jar. Input hashes were
unchanged, and argument-file checks confirmed A/B differ only in architecture
and output, and D/E only in optimization level and output.

The workload, input movie, ROM identity, renderer, clocks, timing boundaries and
JVM warmup are exactly those specified under **Workload and measurement
boundaries** above: ordinary AIZ1 intro, 320×224, solo Sonic, donor/rewind/audio
off; 2,000 traversal frames then 4,000 measured frames; JVM additionally discards
two complete 6,000-frame passes. The original absolute ROM path at `ROM_ROOT`
is read in place. Five fresh processes per feasible build run sequentially in
one rotated matrix under exclusive test admission **and** exclusive build
activity, with the existing 0.5-second bypass guard. No image or Java build occurs inside
that measurement lease. Native compilers use queued exclusive admission, with
build time and maximum process RSS from GNU `time -v`, excluding admission wait.
Build measurements are one compilation per variant, not five-build estimates.

Every native image reports **Serial GC**. Builder G1 is only the compiler's
collector. F uses the preceding study's OpenJDK 25 JVM with explicit G1 and
512 MiB initial / 2 GiB maximum heap. This study does not change Community's GC
or independently isolate GC, JIT profiles, or native/JVM memory layouts.

### Recipes and reproduction

From the task worktree, let `WT="$PWD"`, `B="$WT/target/native-factor"`,
`CE="$WT/target/graalvm-toolchain/graalvm-community-25.4.4.1.1+1.1"`,
`ORACLE="$WT/target/native-benchmark/graalvm-jdk-25.0.4+7.1"`, and let
`ROM_ROOT` denote the original main-workspace root as in the preceding study.
Both previously downloaded toolchains are reused locally. Preparation was:

```bash
python3 tools/testing/maven_queue.py -Dmse=off compile -B
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$CE" \
  python3 tools/testing/maven_queue.py -Dmse=off exec:exec \
  -Dexec.executable=python3 '-Dexec.args=target/native-factor/prepare.py' -B
```

The temporary prepare driver runs the unchanged shipping preparer, then inspects
Oracle's supported options (the latter output is not a runtime measurement):

```bash
python3 tools/native-image/build.py --stage prepare --graal "$CE"
"$ORACLE/bin/native-image" -H:+UnlockExperimentalVMOptions \
  -H:+RuntimeClassLoading --expert-options-all
```

The complete base recipe is generated by `tools/native-image/build.py` at the
source SHA above, in `target/native-mods/native-image.args`: compatibility
architecture; compiler heap 8 GiB, G1, three active processors and two workers;
`java.base,java.logging,java.net.http`; runtime initialization of LWJGL and AWT;
experimental runtime loading; `PreserveIncludesJNI` disabled; the generated
preserved-engine/dependency/JDK-package graph; class and bundled-mod resources;
generated jar-handler reflection metadata; both hosted option exports;
`NativeModFeature`; immutable engine/host/dependency classpath; `NativeEngine`.
Every variant uses **LWJGL 3.4.1 unsafe JNI core**, excluding the transitive FFM
core. No runtime dependency differs between vendors or native variants.

The exact argument-file transformation for each build is reproduced below.
`original` contains one JSON-quoted argument per line from that generated base.
Only the output path changes in addition to the listed experimental factors:

```python
import json
from pathlib import Path
import subprocess
root = Path.cwd()
base = root / 'target/native-factor'
base.mkdir(exist_ok=True)
community = root / 'target/graalvm-toolchain/graalvm-community-25.4.4.1.1+1.1'
oracle = root / 'target/native-benchmark/graalvm-jdk-25.0.4+7.1'
original = [json.loads(line) for line in
            (root / 'target/native-mods/native-image.args').read_text().splitlines()]
for label in ['A', 'B', 'C', 'D', 'E']:
    args = ['-O3' if label == 'E' else '-O2', *original]
    if label == 'B':
        args = [a.replace('-march=compatibility', '-march=native') for a in args]
    if label == 'C':
        args = [a for a in args
                if a not in ['-H:+RuntimeClassLoading', '-H:-PreserveIncludesJNI']
                and not a.startswith(('-H:Preserve=', '--features=', '-J--add-exports='))]
        args = [('-H:IncludeResources=META-INF/openggf/bundled-mods.json'
                 if a.startswith('-H:IncludeResources=') else a) for a in args]
        args[-2] = 'com.openggf.tools.nativeimage.LegacyBenchmarkEngine'
    if label in ['D', 'E']:
        args.insert(0, '-J-Djava.lang.invoke.StringConcat.cacheThreshold=0')
        args = [a for a in args if a != '-H:-PreserveIncludesJNI']
        args = [a.replace('org.graalvm.nativeimage.shared/com.oracle.svm.shared.option',
                          'org.graalvm.nativeimage.builder/com.oracle.svm.core.option')
                for a in args]
    (base / label).mkdir(exist_ok=True)
    args[-1] = str(base / label / 'OpenGGF')
    argfile = base / (label + '.args')
    argfile.write_text('\n'.join(json.dumps(a) for a in args) + '\n')
    home = oracle if label in ['D', 'E'] else community
    subprocess.run(['/usr/bin/time', '-v', '-o', str(base / (label + '.time')),
                    str(home / 'bin/native-image'), '@' + str(argfile)],
                   cwd=root / 'target', check=True)
```

A is the current Linux recipe with its default optimization made explicit as
`-O2`. B changes only the processor target. C removes runtime loading and all
mod/JDK preservation, the retention-audit launcher/feature and class-resource
retention; it keeps the same engine bytes, dependencies, ordinary native
metadata and compiler limits. Its measurement-only legacy launcher still retains
`Engine.main`; mods are intentionally unsupported. The unsafe core's shipped
JNI metadata sufficed for the offscreen control; no FFM/JNI patch was needed.

D/E preserve A's runtime-loading contract, launcher and member audit. Oracle
25.0.4 lacks `PreserveIncludesJNI`, so that switch is omitted; its hosted option
key is in `core.option`, so the corresponding export replaces Community's
`shared.option` export. The normal feature check confirms runtime loading in
both builders. Oracle consequently registers far more JNI members. Its build
also reports **ML-inferred PGO**. D versus A therefore compares these actual
compiler versions and supported policies, not a pure compiler-vendor variable.
D versus E changes only `-O2` to `-O3` and output path.

The initial D build failed in method parsing after **160.23 s**, at
**9,417,060 KiB** maximum RSS. `MethodHandleFeature.java:259–263` in Oracle's
bundled `svm.src.zip` iterates cached soft references and invokes a method on
`value.get()` without a null check; the reported `GraalGraphError` names
`StringConcatFactory$InlineHiddenClassStrategy.CACHE` and ends in that null
receiver. This is a builder failure, not a gameplay result. The contained retry
adds `-J-Djava.lang.invoke.StringConcat.cacheThreshold=0` to D/E. Oracle's bundled
`StringConcatFactory.java:1266–1270,1363–1368` selects static concatenation at or
above this threshold and avoids populating that cache. This flag goes to the
builder JVM; any resulting concat/image-code differences remain part of the
Oracle recipe, not an independently measured compiler effect.

The queued temporary driver builds each label with the transformation above and
checks `OpenGGF --audit` after every mod-capable build. C instead passed a
nonblank offscreen functional control at traversal frame 2000; its single-frame
control timings are excluded. The actual compiler order is C, D (failed cache
control), D (retry), A, B, E, each with this queued command and its own selected
label; admission is released between compilations:

```bash
OPENGGF_MAVEN_QUEUE=serial JAVA_HOME="$CE" \
  python3 tools/testing/maven_queue.py -Dmse=off exec:exec \
  -Dexec.executable=python3 '-Dexec.args=target/native-factor/build_variants.py A' -B
```

Native launcher arrays are `$B/<label>/OpenGGF`, the same four runtime properties
as the preceding study (using `$B/saves`), then `--benchmark-gameplay`. The F
launcher is labeled `jvm` so the unchanged matrix driver supplies two discarded
passes. It uses `/usr/lib/jvm/java-25-openjdk/bin/java`,
`--enable-native-access=ALL-UNNAMED -Xms512m -Xmx2g -XX:+UseG1GC`, the same runtime
properties, and the prepared host/classes plus the same unsafe-core native
dependency classpath, ending in
`com.openggf.tools.nativeimage.GameplayBenchmark`. This is the original JVM
launcher with only its save directory moved under the new experiment directory.

Launcher JSON can be regenerated from the prepared inputs without copying any
ROM or modifying the shipping build:

```python
import json, os, sys
from pathlib import Path
root = Path.cwd()
base = root / 'target/native-factor'
sys.path.insert(0, str(root / 'tools/native-image'))
from build import native_dependencies
props = ['-Dopenggf.headless.gl=surfaceless-egl',
         '-Dorg.lwjgl.librarypath=' + str(root / 'target'),
         '-Dopenggf.installDir=' + str(root / 'target'),
         '-Dopenggf.saveRoot=' + str(base / 'saves')]
launchers = {label: [str(base / label / 'OpenGGF'), *props, '--benchmark-gameplay']
             for label in ['A', 'B', 'C', 'D', 'E']}
deps = native_dependencies((root / 'target/native-runtime-classpath.txt').read_text().strip())
cp = os.pathsep.join([str(root / 'target/native-mods/host-classes'),
                      str(root / 'target/classes'), deps])
launchers['jvm'] = ['/usr/lib/jvm/java-25-openjdk/bin/java',
                    '--enable-native-access=ALL-UNNAMED', '-Xms512m', '-Xmx2g',
                    '-XX:+UseG1GC', *props, '-cp', cp,
                    'com.openggf.tools.nativeimage.GameplayBenchmark']
(base / 'launchers.json').write_text(json.dumps(launchers, indent=2) + '\n')
```

The complete five-process-per-build runtime command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/performance/run_isolated.py \
  --audit-report target/native-factor/isolation.json --estimate-seconds 360 -- \
  python3 tools/performance/native_matrix.py \
  --launchers target/native-factor/launchers.json \
  --rom "$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --bk2 examples/second-strike/walkthroughs/routes/aiz1-sonic-solo-native-cold.bk2 \
  --output target/native-factor/runs --runs 5 --traversal 2000 --frames 4000
```

### Isolation outcome and workload equality

The complete batch waited **134 s** for exclusive test admission, then held both
exclusive leases. Its launched-command interval was **2026-10-27T19:55:39.902+00:00–2026-10-27T20:00:00.572+00:00**,
**260.67 s**, exit 0. The audit contains no
observed bypass interference at its 0.5-second sampling interval. Build-activity
drain time is outside that launched interval and was not separately measured.
No ROM/graphics skip mechanism exists in this command. All **30 processes**
completed, five per build, for **20,000 measured frames per build** and 120,000
in total. There was no aborted runtime batch or discarded performance run.

All 30 matched the preceding round's trajectory **`b20b4a74af557992`**,
**83,873 cumulative active-object observations**, final centre **(2928, 939)**,
and nonblank final framebuffer hash **2002713451**. Renderer/version also
matched across every process and the prior round: Ryzen 9 9950X integrated
radeonsi, Mesa 26.2.5-arch3.1, OpenGL 4.6 compatibility. These are the same
trajectory, aggregate-object-count and final-frame controls used previously;
they do not assert every object's internal state or every intermediate pixel.

Host kernel remains 7.3.0-5-cachyos, 31,919,976 KiB RAM, powersave governor,
boost enabled, no fixed clock or affinity. F remains Arch OpenJDK 25.0.5.0.2;
Community is Java 25.0.4.1.1+1 / GraalVM CE 25.4.4.1.1+1.1 and Oracle is Java
25.0.4+7-LTS / GraalVM 25.0.4+7.1. Desktop and unrelated services remain active.
Absolute timings differ from the earlier session, so no old JVM/native timings
are substituted into this contemporaneous comparison. The leases exclude
linked-worktree builds/tests; they do not fix host frequency or remove arbitrary
host/graphics load. Run spread remains material.

### Runtime results and spread

Each entry below is **median(run mean) / median(run p50) / median(run p99) /
worst individual frame**, in milliseconds. The first three are medians of five
per-process summaries, not a pooled p50/p99 over 20,000 samples. The maximum is
the worst across all five processes. Full per-run summaries follow, avoiding
any ambiguity about aggregation.

| Build | Process CPU ms/frame | Elapsed ms/frame | Run-mean CPU range | Run-mean elapsed range |
| --- | --- | --- | --- | --- |
| A | 0.809519 / 0.662103 / 3.719879 / 32.166277 | 1.170345 / 1.076013 / 2.871574 / 29.734854 | 0.736439–0.811631 | 1.098007–1.178222 |
| B | 0.822965 / 0.689823 / 3.745523 / 33.033115 | 1.183658 / 1.101483 / 2.733963 / 30.563132 | 0.752432–0.880792 | 1.111568–1.253157 |
| C | 0.656888 / 0.527293 / 3.494696 / 30.201588 | 1.019837 / 0.938841 / 2.417559 / 28.400489 | 0.606344–0.674159 | 0.967578–1.034704 |
| D | 0.852676 / 0.728933 / 3.779690 / 31.767873 | 1.222571 / 1.140684 / 2.829994 / 33.653984 | 0.822639–0.958070 | 1.195040–1.334043 |
| E | 0.823230 / 0.701484 / 3.764466 / 28.598793 | 1.188569 / 1.111013 / 2.656531 / 32.863552 | 0.767962–0.832038 | 1.128788–1.199025 |
| F (warmed JVM) | 0.595992 / 0.402202 / 3.535005 / 56.211875 | 0.885234 / 0.810650 / 2.288767 / 30.742753 | 0.571044–0.606813 | 0.858322–0.890141 |

Startup is direct process launch to first completed intro frame, including the
native retention audit where applicable; filesystem/driver caches were not
flushed. Executable sizes exclude sidecar native libraries, ROMs, JVM and mod
files. RSS is GNU time's maximum process RSS, not total process-tree RAM.

| Build | Startup median (min–max), s | Executable bytes / MiB | Build s | Build maximum RSS KiB / GiB |
| --- | --- | --- | --- | --- |
| A | 0.375306 (0.255945–0.386369) | 337,054,160 / 321.44 | 262.91 | 9,628,992 / 9.183 |
| B | 0.235053 (0.210292–0.340777) | 338,102,736 / 322.44 | 265.87 | 9,580,428 / 9.137 |
| C | 0.186905 (0.147181–0.197760) | 89,131,400 / 85.00 | 125.79 | 6,471,536 / 6.172 |
| D | 0.235175 (0.209542–0.337678) | 380,373,384 / 362.75 | 385.71 | 9,723,672 / 9.273 |
| E | 0.261842 (0.219495–0.385674) | 456,591,752 / 435.44 | 549.60 | 9,727,532 / 9.277 |
| F (warmed JVM) | 0.863437 (0.807288–0.943176) | n/a | n/a | n/a |

F has no separately compiled native executable; its existing JVM and shared
compiled engine inputs were reused, so an image build cost/size is not applicable.

| Build/run | CPU mean / p50 / p99 / max, ms | Elapsed mean / p50 / p99 / max, ms | Startup s |
| --- | --- | --- | --- |
| A/1 | 0.736439 / 0.605843 / 3.536005 / 31.358991 | 1.098007 / 1.018652 / 2.496369 / 28.846309 | 0.386369 |
| A/2 | 0.776904 / 0.641154 / 3.719879 / 32.166277 | 1.136370 / 1.051802 / 2.871574 / 29.734854 | 0.375973 |
| A/3 | 0.811135 / 0.663323 / 3.875408 / 31.405041 | 1.178222 / 1.077573 / 2.905575 / 28.920566 | 0.255945 |
| A/4 | 0.811631 / 0.662103 / 3.921911 / 31.407284 | 1.176027 / 1.076013 / 2.956796 / 28.731226 | 0.375306 |
| A/5 | 0.809519 / 0.680533 / 3.710947 / 31.211080 | 1.170345 / 1.093353 / 2.708373 / 28.665806 | 0.261983 |
| B/1 | 0.752432 / 0.625333 / 3.549565 / 29.927316 | 1.111568 / 1.033002 / 2.513920 / 27.833037 | 0.335415 |
| B/2 | 0.880792 / 0.726643 / 4.124419 / 33.033115 | 1.253157 / 1.140064 / 3.227988 / 30.563132 | 0.210292 |
| B/3 | 0.797865 / 0.666074 / 3.715369 / 31.335038 | 1.162869 / 1.078173 / 2.733963 / 28.893976 | 0.340777 |
| B/4 | 0.822965 / 0.689823 / 3.745523 / 31.144707 | 1.183658 / 1.101483 / 2.702473 / 28.645634 | 0.224213 |
| B/5 | 0.839809 / 0.699053 / 3.845159 / 31.438286 | 1.205845 / 1.109643 / 2.809184 / 29.026951 | 0.235053 |
| C/1 | 0.606344 / 0.483822 / 3.324328 / 29.378513 | 0.967578 / 0.897480 / 2.250086 / 27.546434 | 0.186905 |
| C/2 | 0.671459 / 0.553503 / 3.380928 / 28.833095 | 1.031833 / 0.963431 / 2.376518 / 26.338171 | 0.185347 |
| C/3 | 0.655773 / 0.527293 / 3.515892 / 30.201588 | 1.019837 / 0.938841 / 2.573951 / 28.400489 | 0.197760 |
| C/4 | 0.674159 / 0.551202 / 3.494696 / 28.835757 | 1.034704 / 0.960822 / 2.427669 / 26.627700 | 0.191788 |
| C/5 | 0.656888 / 0.523642 / 3.506686 / 29.899128 | 1.018914 / 0.938311 / 2.417559 / 27.701685 | 0.147181 |
| D/1 | 0.822639 / 0.698543 / 3.686988 / 28.816249 | 1.195040 / 1.109693 / 2.717112 / 26.390541 | 0.337678 |
| D/2 | 0.958070 / 0.807663 / 3.760437 / 27.786357 | 1.334043 / 1.217205 / 3.056336 / 26.301401 | 0.209542 |
| D/3 | 0.867391 / 0.734973 / 3.847887 / 31.767873 | 1.247755 / 1.150144 / 2.829994 / 29.777846 | 0.245520 |
| D/4 | 0.852676 / 0.726913 / 3.902458 / 27.419899 | 1.219050 / 1.136634 / 2.841004 / 33.653984 | 0.235175 |
| D/5 | 0.851356 / 0.728933 / 3.779690 / 26.213830 | 1.222571 / 1.140684 / 2.727423 / 23.684166 | 0.225210 |
| E/1 | 0.767962 / 0.644563 / 3.704475 / 24.040692 | 1.128788 / 1.056243 / 2.656531 / 21.844288 | 0.385674 |
| E/2 | 0.832038 / 0.716403 / 3.625135 / 28.243577 | 1.188569 / 1.118123 / 2.604280 / 26.170051 | 0.359261 |
| E/3 | 0.812454 / 0.687813 / 3.764466 / 26.961713 | 1.182973 / 1.098013 / 2.621981 / 32.863552 | 0.261842 |
| E/4 | 0.823230 / 0.701484 / 3.766977 / 24.626729 | 1.192593 / 1.111013 / 2.693012 / 22.301238 | 0.219495 |
| E/5 | 0.831556 / 0.708964 / 3.769704 / 28.598793 | 1.199025 / 1.118783 / 2.705373 / 27.060556 | 0.221397 |
| F/1 | 0.595992 / 0.402202 / 3.594067 / 56.211875 | 0.885234 / 0.810650 / 2.376498 / 30.742753 | 0.863437 |
| F/2 | 0.599770 / 0.410352 / 3.622046 / 41.925680 | 0.890141 / 0.817810 / 2.379248 / 23.510520 | 0.943176 |
| F/3 | 0.576878 / 0.395482 / 3.338807 / 24.794858 | 0.868167 / 0.806190 / 2.116475 / 18.302670 | 0.878199 |
| F/4 | 0.606813 / 0.413742 / 3.535005 / 43.647911 | 0.889515 / 0.820250 / 2.288767 / 20.767190 | 0.815546 |
| F/5 | 0.571044 / 0.372782 / 3.347194 / 25.254709 | 0.858322 / 0.791869 / 2.200997 / 18.035278 | 0.807288 |

For completeness, the arithmetic mean of all five equal-sized run means (the
pooled frame mean, to the precision of the per-run JSON) is:

| Build | Pooled mean CPU ms/frame | Pooled mean elapsed ms/frame |
| --- | --- | --- |
| A | 0.789126 | 1.151794 |
| B | 0.818773 | 1.183419 |
| C | 0.652925 | 1.014573 |
| D | 0.870426 | 1.243692 |
| E | 0.813448 | 1.178390 |
| F | 0.590099 | 0.878276 |

### Interpretation and limits

**Oracle `-O3` did not demonstrate a speedup over Community `-O2`.** Its median
run-mean CPU is **0.823230 versus 0.809519 ms/frame**, a **1.7% higher** point
estimate (elapsed **1.6% higher**). The CPU difference is only 0.013711 ms,
smaller than the larger 0.075192 ms run-mean range; the ranges overlap. The
pooled means likewise do not show an Oracle advantage, and are not substituted
for the predefined median comparison. This result is **inconclusive**, not
proof of an Oracle slowdown. Oracle `-O3` versus its matched `-O2` build has
**3.5% lower CPU** and **2.8% lower elapsed** point estimates, also inconclusive
against the 0.135431 ms CPU and 0.139003 ms elapsed spreads. No smaller-than-spread
difference is treated as a performance finding.

The comparison rule below uses the difference between median run means, tests
it against the larger complete min–max run-mean width, and also treats overlap
as inconclusive. It is a conservative description of five runs, not a confidence
interval or significance test. Positive percentages mean more time; negative
percentages mean less time. All rows use the second build as denominator.

| Factor / comparison | CPU time change | Elapsed time change | Finding |
| --- | --- | --- | --- |
| Processor target, B/A | +1.7% | +1.1% | Inconclusive; no measured compatibility penalty |
| Remove runtime loading/preservation, C/A | −18.9% | −12.9% | Resolvable in this batch; both mean ranges separated |
| Oracle adapted `-O2`, D/A | +5.3% | +4.5% | Inconclusive; not a pure vendor variable |
| Oracle adapted `-O3`, E/A | +1.7% | +1.6% | Inconclusive; no demonstrated speedup |
| Oracle optimization level, E/D | −3.5% | −2.8% | Inconclusive |
| Warmed JVM, F/A | −26.4% | −24.4% | Resolvable in this batch |
| Warmed JVM versus closed world, F/C | −9.3% | −13.2% | CPU inconclusive; elapsed advantage resolvable |

**The largest isolated, resolvable native factor is the runtime mod-support
bundle.** A uses **23.2% more CPU** and **14.8% more elapsed frame time** than C.
This bundles open type-world/runtime-loading policy, preserved engine/JDK/library
members and their supporting class resources. It does not price the 24,827
contract entries individually or separate open-world optimization limits from
retention/code-layout effects. The audit/launcher difference affects startup;
the audit finishes before gameplay and is outside all measured frame windows.
C needs no significant LWJGL fix: selecting the same unsafe JNI core as A avoids
the previous closed-world FFM failure while using its existing metadata.

The warmed JVM advantage over the shipping native baseline is reproduced:
**0.595992 versus 0.809519 ms CPU/frame**, about **1.36× CPU throughput** in this
window. After removing native mod support, the residual CPU point gap is
**0.060896 ms**, below C's **0.067815 ms** run spread and with overlapping ranges;
it cannot be assigned to JIT profiles or GC from these data. The JVM still has
a resolvable **13.2% elapsed-time** advantage over C. No measured gameplay-profile
PGO was supplied to these AOT images; Oracle reports ML-inferred PGO. JIT profile
specialization remains a plausible contributor, alongside runtime/driver/code
layout and collector differences, but none was independently isolated. All native
images use Serial GC and F uses G1; there is **no measured collector-cost number**.
The experiment supports a substantial combined mod-support cost, not a unique
causal explanation for every part of the JVM/native difference.

Oracle's larger JNI graph, older builder/JDK, ML-inferred PGO and concat-cache
workaround prevent D/A from answering “compiler alone” cleanly. The same-vendor
D/E pair does isolate the requested optimization-level change under those
adaptations. `-O3` increased observed executable size by **20.0%** and image-build
time by **42.5%** over Oracle `-O2`, with essentially the same maximum compiler
RSS; each resource value is one build, without a repeatability estimate.
No builds are skipped. The first D compilation remains an explicitly failed
builder control; its resources are not mixed with the successful D build.

### Verification and delivery boundary

This is bounded measurement of one Linux route/viewport, not engine-suite,
platform or release qualification. Verification consists of the shared-input
byte/hash checks, argument-difference checks, native preparation regression
controls and member audit, four finished-image member audits, C's offscreen
functional control, and the complete isolated five-run matrix with prior-round
state/render controls. No Java source, shipping recipe, workflow or configuration
changed; no engine suite was run for this research-document-only change.
Temporary drivers, binaries and raw measurement diagnostics are consumed into
this record and removed after review. Existing toolchains and unrelated prior
artifacts are preserved. The existing task worktree and branch remain in place;
there is no merge, push, workflow dispatch or Oracle binary in the commit.
