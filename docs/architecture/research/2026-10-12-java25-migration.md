# Java 25 baseline migration

## Scope and ownership

The user requested an Astra-led investigation and migration team, an isolated
copy-on-write worktree, and a PR retained for their testing and merge decision.
The task starts from develop `f9d4e04747a6474c81cc9c9424951fecf3de191b` on
`feature/ai-java25-migration`. No release version or Mod API signature changes
are intended. The build/runtime worker owns dependencies and launchers; the
CI/SDK worker owns workflow/platform and creator compilation contracts; the
orchestrator owns tooling, documentation, integration and validation.

## Findings and decisions

- The engine, Maven guard, preflight, dynamic Java compilers, creator templates,
  examples, profiling tools and CI must agree on release 25 (class-file major
  69). Merely running release-21 bytecode on a newer VM would leave the requested
  baseline migration incomplete. Builds require JDK 25 exactly; the JVM artifact
  requires Java 25 or later. Existing Java 21 validator fixtures continue checking
  older bytecode while a new Java 25 entrypoint test checks the new baseline.
- Mockito 5.14.2 brings Byte Buddy 1.15.4, whose supported class versions stop
  before Java 25. Mockito 5.20.0 brings Byte Buddy 1.17.7; upstream's Java 25
  minimum is 1.17.0. Keep explicit Mockito startup instrumentation rather than
  dynamic attachment or experimental Byte Buddy version bypasses.
- ASM 9.9.1 already understands Java 25. Retain it, ArchUnit 1.4.2 and LWJGL
  3.3.3 unless execution demonstrates an incompatibility. Updating LWJGL solely
  because the JDK changes would also change GLFW and memory backends without a
  demonstrated need. Declare native access for launchers, tests, executable JAR
  manifests and native-image builds. Dependency Unsafe warnings require explicit
  investigation; do not silence them indiscriminately.
- GraalVM 25 supports native images on Linux x64/aarch64, Windows x64 and macOS
  aarch64. Its removal of macOS x64 does **not** remove Java 25 JVM support there.
  The release matrix explicitly uses ARM macOS and x64 Windows/Linux. Existing
  experimental Linux/Windows mod images already pin a CE 25 toolchain; migrate
  their Java input contract without rewriting the historical feasibility evidence.
- Keep shipped-ROM behavior, GC, compact-header defaults, AOT configuration and
  concurrency design unchanged. Scoped values becoming final does not justify a
  source refactor; structured concurrency remains preview. No performance or
  memory improvement is claimed without workload-specific measurements.

- First actual Java 25 compilation failed because `Entry` inside an anonymous
  `LinkedHashMap` resolved to inherited `Map.Entry` rather than the enclosing
  `NewIdentityCache.Entry` record. Qualifying that type restores the existing
  override; the next `test-compile` passed 3,777 production and 3,650 test sources.
- Universal-JAR 32-bit native payloads are retained as bundled dependency contents;
  their presence is not a claim that a supported Java 25 VM exists for that target.

- The opt-in allocation benchmark now generates Java 25 measurements. Its comparator
  can still read archived Java 21 pairs but requires identical full environment
  headers; a new regression rejects cross-JDK comparisons. Existing historical
  measurement payloads and provenance remain unchanged.

- A combined read-only review found that Maven packaging helpers and generated
  creator executions could select Java 21 from PATH even when Maven used
  `JAVA_HOME` 25. Pin child JVMs to `${java.home}/bin/java`, and propagate native
  access to the creator CLI's engine subprocess and the queue's lean test mode.
  The hermetic audio parity wrapper deliberately retains `/usr/bin/java` and
  rejects ambient overrides; it now diagnoses a non-25 fixed runtime before
  creating output. Local task-owned JDK selection does not satisfy that wrapper.

## Costs and verification boundaries

The main cost is verification across test instrumentation, SDK compilation,
JNI/graphics/audio and platform packaging. The ordinary suite and guards historically
cost about 24 plus 10 minutes per tree; baseline and candidate both need coverage.
Local Linux evidence cannot certify Windows/macOS native packages. The parent will
open the PR and run the release workflow's nonpublishing manual dispatch on the
candidate branch (`validate_roms=false`) alongside PR checks; hosted packaging
is separate from local ROM/trace evidence. User gameplay acceptance remains pending.

## Primary references

- [Byte Buddy Java compatibility](https://github.com/raphw/byte-buddy#java-version-compatibility)
- [Mockito 5.20.0 dependency POM](https://repo.maven.apache.org/maven2/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.pom)
- [ASM release compatibility](https://asm.ow2.io/versions.html)
- [LWJGL releases](https://github.com/LWJGL/lwjgl3/releases)
- [GraalVM 25 release notes](https://www.graalvm.org/release-notes/JDK_25/)
- [GraalVM 25 supported platforms](https://docs.oracle.com/en/graalvm/jdk/25/docs/support/)
- [Native-image options](https://www.graalvm.org/jdk25/reference-manual/native-image/overview/Options/)

- [Oracle migration preparation](https://docs.oracle.com/en/java/javase/25/migrate/preparing-migration.html)
- [Removed/deprecated JDK components](https://docs.oracle.com/en/java/javase/25/migrate/removed-tools-and-components.html)

## Validation evidence

The initial implementation is committed as `84fe179b4`; verification and the
compatibility fixes discovered by hosted execution remain in progress.

Completed focused evidence on the task worktree with Temurin 25.0.4.1+1:

- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile`: pass
  after the cache type qualification (3,777 production / 3,650 test sources).
- `python3 -m unittest discover -s tools/testing -p 'test_run_category_*.py' -q`:
  109 passed. An initial launch on the old PATH JDK failed two dynamic compiler
  tests (`release version 25 not supported`); the correct JDK 25 rerun passed.
  After native-access profiler updates, the twelve `test_run_category_*memory*.py`
  tests passed again.
- Creator-kit Python12, Linux packaging Python3 and Windows packaging Python3:
  pass; these are packaging logic tests, not native-platform execution.
- FM profile preparation and malformed-input harnesses: pass on Java 25.
- `jdeps --jdk-internals --ignore-missing-deps --multi-release 25 target/classes`:
  no reported internal API usage; static analysis does not detect reflection.
- Change-based plan selects the full ordinary suite (3,080 candidate classes)
  and guards; actual Java 25 / Lua5.4 / PowerShell preflight passed.
- Combined read-only review: Python AST25 files, Bash13 scripts, XML10
  POM/templates, whitespace and mirrored guidance checks passed. No unresolved
  critical/important findings remained; this is not execution certification.

Exact completed broad commands, outcomes, remaining failure identities and
coverage limits are recorded here before handoff.

The first unchanged Java 21 baseline invocation, `LUA_BIN=/usr/bin/lua5.4
python3 tools/testing/run_categories.py --category all --run`, stopped at its
40-minute execution limit (run `20261012T092936Z-410c6146`, 2401.26 seconds).
It is incomplete: 2,686 reports / 23,342 cases / one failure / zero errors /
57 skips; guards had not started. The completed reports exposed the pre-existing
`TestModManagerScreenHost.hostMapsRealInputHandlerAndPixelFontAcrossTheNeutralModUiBoundary`
failure at line 58 (`expected: <true> but was: <false>`): the test expects
`No mods discovered.` while existing production renders
`No mods. Apply to rescan jars.`. No missing-ROM skips occurred. Skips comprised
19 opt-in diagnostics/captures/soaks, five unavailable graphics contexts,
32 inapplicable Infinite Sonic act routes and one CPZ spin-tube assumption.
This timeout justifies a full unchanged-baseline retry with `--max-minutes 90`;
partial reports are not full-suite acceptance. Diagnostics were inspected and
acknowledged before retrying.

Focused candidate verification then reproduced that same UI assertion unchanged
on Java 25: `-Dtest=TestGgfModCliCommands,TestCompleteRunAudioCli,TestSmpsRepeatedPlaybackBenchmarkComparator,TestModManagerScreenHost`
ran 36 cases, one identical line58 failure, zero errors/skips. Only the stale
expected UI text was corrected; production rendering is unchanged. This repairs
pre-existing test debt needed for PR checks, not a Java 25 engine defect.
The nine-class SDK/SQLite/tooling check ran 187 cases with zero skips: all 67
SDK/SQLite cases passed; two of 120 tooling guard cases exposed outdated JNI
argLine and launcher-mock expectations. Those assertions were updated and a
new executable launcher test proves JAVA_HOME precedence even when PATH Java
would fail. The launcher Python suite passes all ten cases.


At `84fe179b4`, the complete corrected `TestBuildToolingGuard` (120) and
`TestModManagerScreenHost` (2) rerun passed all 122 cases with zero skips using
`maven_queue.py --lean -Dmse=off -Dtest=TestBuildToolingGuard,TestModManagerScreenHost test`.
The actual `ci-pr` policy range from the pinned base to this commit passed,
including the release-tree audit (12,293 entries), documentation trailers and
both exact model coauthors.

Mixed-runtime packaging used Temurin 25 via JAVA_HOME while PATH `java` remained
21.0.12.1: `maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify` passed
in 31.5 seconds. Two creator-kit ZIP assemblies were byte-identical and recorded
clean source commit `84fe179b4707fdd6030a8688ce96defce7562ce0`; the engine class
major was 69 and the executable manifest declared native access. All seven
exported starter kinds (music/reskin/object/character/zone/scene/standalone)
initialized, packaged with explicit `-DskipTests` using their inspected generated
POMs, and validated with zero findings/warnings in that same split-JDK environment.
The portable hello-scene builder and matching testkit identity check also passed.
This checks artifact construction, not user gameplay or native-platform execution.

The parent pushed only the feature branch and dispatched the nonpublishing
[Release workflow 38238511966](https://github.com/OpenGGF/OpenGGF/actions/runs/38238511966)
on that exact commit with `validate_roms=false`. Its ordinary tests completed
23,302 cases, one failure, two errors and 4,286 skips; guards/native jobs were
skipped after failure. Two genuine migration regressions were exposed:

- `TestSonic1SfxData.musicSfxAndZeroAddressVoicesPreserveFormatBoundsAndCopyIsolation`
  expected `IllegalArgumentException` at line 58, but Java 25 `Arrays.copyOfRange`
  instead reached `arraycopy` and threw `ArrayIndexOutOfBoundsException` for a
  length of -2147483600 at `Sonic1SfxData:98`. A voice offset/end overflow had been
  relying on the JDK's validation order. The unchanged Java 21 baseline passed
  both methods in this class with no skips. The fix must use nonoverflowing
  bounds while retaining normal voice bytes, copy isolation and the explicitly
  tested historical exception boundary.
- Both `TestOwnedMocks` methods errored with `DisabledMockException` after owned
  inline mocks were cleared (lines23/43). Both passed on the unchanged Java 21 /
  Mockito 5.14.2 baseline. Mockito 5.20 explicitly documents disabled access after
  clearing; the helper's per-owned cleanup is correct, while its old tests assumed
  cleared concrete mocks reverted to real methods. Updated coverage must retain
  the stronger requirement that another fixture's same-class mock survives.

The still-waiting candidate broad, trace and graphics jobs were canceled cleanly
before execution to avoid knowingly running uncorrected source. The baseline
jobs were preserved. Original focused reproduction and corrective verification
are recorded below when complete; no successful hosted/native result is claimed.

Primary behavior evidence:
[OpenJDK 21 Arrays](https://raw.githubusercontent.com/openjdk/jdk/jdk-21-ga/src/java.base/share/classes/java/util/Arrays.java),
[OpenJDK 25 Arrays](https://raw.githubusercontent.com/openjdk/jdk/jdk-25-ga/src/java.base/share/classes/java/util/Arrays.java),
[Mockito 5.20 cleanup contract](https://raw.githubusercontent.com/mockito/mockito/v5.20.0/mockito-core/src/main/java/org/mockito/MockitoFramework.java).


The 90-minute f9 baseline retry (`20261012T101159Z-091381b7`) completed its
ordinary lane just before cancellation: 3,078 reports / 26,679 cases / 28 failures /
zero errors / 63 skips in 3,483.52 seconds. Its guard lane had started and was
interrupted after upstream advanced to `e76a380c3d0c8e3769fcdb745f27ef3d8889c8fe`.
The combined run is **incomplete and superseded**, not a full-suite pass. The
completed ordinary failures are historical baseline evidence only; e76 becomes
the required actual-destination baseline. No missing-ROM skips occurred: 32 skips
were inapplicable Infinite Sonic routes, five unavailable graphics contexts,
one CPZ spin-tube assumption and 25 optional diagnostics/captures/native checks.
Diagnostics were inspected and acknowledged after capturing these identities.

| Historical f9 ordinary failure | Message |
|---|---|
| `com.openggf.TestModManagerScreenHost.hostMapsRealInputHandlerAndPixelFontAcrossTheNeutralModUiBoundary` | expected: <true> but was: <false> |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | late pulley owns Tails ==> expected: <true> but was: <false> |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute.incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | late pulley owns Sonic ==> expected: <true> but was: <false> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | death at 26706 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | death at 26750 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | expected: <96> but was: <0> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | death at 53897 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture.coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | expected: <96> but was: <0> |
| `com.openggf.tools.TestLrzActTwoColdRouteCapture.coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | death at input 36526 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzBossColdRouteCapture.coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | death at input 36526 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzKnucklesColdRouteCapture.coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | expected: <1069> but was: <899> |
| `com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | death at input 19460 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | death at input 19460 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | death at input 19460 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzTailsColdRouteCapture.coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | death at input 19460 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestLrzWideBossColdRouteCapture.coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | expected: <2796> but was: <524> |
| `com.openggf.tools.TestMhzPairColdRouteCapture.pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | the route must observe the actual history-reset boundary ==> expected: <true> but was: <false> |
| `com.openggf.tools.TestMhzWideColdRouteCapture.wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | [wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false> |
| `com.openggf.tools.TestSszColdRouteCapture.coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | death at input 7311 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestSszColdRouteCapture.coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | death at input 7311 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestSszSoloColdRouteCapture.coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | death at 7671 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestSszSoloColdRouteCapture.coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | death at 7671 ==> expected: <false> but was: <true> |
| `com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | expected: <48> but was: <0> |
| `com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | expected: <48> but was: <0> |
| `com.openggf.tools.TestSszTailsColdRouteCapture.coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | replay at 4018 object-manager: [object-manager.usedSlotsBits differs, object-manager.usedSlotsBits.onlyA: 24, 29, object-manager.dynamic[6][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC]] missing in B (A=DynamicObjectEntry[className=com.openggf.g… (truncated; full-message SHA-256 5344e2df6b89fd8630a267752e704c71dfb3bc4d9d862368600246eae3f7e22b) |
| `com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineCli.shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup` | expected: <0> but was: <4> |

Both `TestSonic1SfxData` methods and both `TestOwnedMocks` methods passed on this
unchanged Java 21 baseline with zero skips. The local Java 25 focused reproduction
then ran those exact four cases: one IAE-versus-AIOOBE failure and two
`DisabledMockException` errors, zero skips (24.33 seconds, source 84fe179b4).
This independently matched the hosted failures before either fix was applied.


The corrected focused run passed all five cases (SfxData 3, OwnedMocks 2), zero
failures/errors/skips, in 70 seconds. Long offset/end arithmetic now prevents
wraparound aliasing while retaining the tested invalid-range exception and all
valid voice bytes/copy isolation. OwnedMocks still clears only its own instances;
tests now enforce Mockito's disabled lifecycle, surviving same-class foreign mocks,
discarded default answers and idempotent close. LWJGL 3.3.3 emitted its expected
JDK 25 `Unsafe.objectFieldOffset` deprecation warning; access succeeded and no
suppression or experimental compatibility flag was added.


## Updated destination reconciliation

While verification was queued, develop advanced to
`e76a380c3d0c8e3769fcdb745f27ef3d8889c8fe`. Its Time Attack extraction,
bundled-mod packaging, racing server, scoped networking dependencies and new Mod
API signatures are retained. The migration follows the moved identity cache and
raises both the new bundled-mod compiler and standalone racing compiler to 25.
The new Maven packager uses Maven's own Java executable; racing launchers declare
native access and use the selected JAVA_HOME toolchain. The bundled packaging
regression checks actual generated class files for major version 69.

A clean Java 25 focused run of `TestBundledModPackager,TestBundledModSource,
TestActPracticeExample,TestTimeAttackModPackage,TestSqliteIdentityStore` passed
27 tests, zero failures/errors/skips, in 85 seconds. It used the queued lean lane
and all three absolute original ROM properties. This verifies the newly moved
SQLite consumer and bundled runtime packaging, not full-suite acceptance.

The obsolete f9 baseline was stopped and acknowledged after its ordinary stage;
its incomplete guard stage is not a pass. The replacement unchanged e76 Java 21
baseline selects 3097 ordinary classes plus guards. Both replacement baseline and
candidate use a 120-minute per-invocation ceiling and the existing 10-minute
no-output limit: queue history now measures about 64 minutes median / 91 minutes
p95 for broad holds, substantially longer than the old 24-minute estimate. Queue
waiting is excluded. Both launch environments remove inherited LD_LIBRARY_PATH,
which the documented hermetic audio CLI deliberately rejects; negative-injection
tests retain their own controlled values.

The reconciled Python runner safety discovery (`test_run_categor*.py`, including
`test_run_categories.py`) passed all 137 tests under JAVA_HOME 25 in 24 seconds.

The reconciled mixed-runtime build (`JAVA_HOME=25`, `PATH` Java 21,
`maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify`) passed in 34.8 seconds.
The universal engine and embedded Time Attack mod contain class-file version 69;
the bundled manifest's size and SHA-256 match the embedded jar. This exercises
the new Maven-selected bundled packager without relying on PATH's Java.


## Reconciled hosted verification checkpoint

Merge `eb874411a8624b609271adb2800455e8fcb55058` retains destination e76 and
passed the actual PR policy range (12,367 audited release-tree entries). The
non-publishing Release run
[38243371330](https://github.com/OpenGGF/OpenGGF/actions/runs/38243371330) passed
ordinary testing: 3,095 reports, 23,551 case executions, zero failures/errors,
4,288 skips. Counts include repeated invocation identities (899 duplicate
identity executions); they are not deduplicated test classes. Skips reflect the
cloud's missing ROM/graphics/media and opt-in coverage, not local ROM acceptance.
All three SfxData cases, both OwnedMocks cases and both final Java 25 Time Attack
packaging cases passed without skips. The duplicate local two-case packaging
request was canceled before admission rather than repeating that proof.

The subsequent hosted guard stage ran 676 cases with one failure, zero errors
and zero skips: `TestModTestKitDistributionGuard.
effectiveEngineInputsExcludeTheActualSupportClassifier` expected bare `java`
instead of the intentional `${java.home}/bin/java` POM executable. Its sole
executable expectation is corrected; classifier exclusions, support-only jar
contents, input arguments and isolated composition/rewind checks remain intact.
The candidate broad and profile requests were canceled while still waiting
before this test edit; the unchanged e76 baseline continues. The hosted ordinary
result remains evidence for the unchanged production source. Native jobs did not
run because the mandatory guard stage failed.

After the merge commit, mixed-runtime universal verification was refreshed to
stamp clean provenance (42.6 seconds). Two creator-kit ZIPs were byte-identical,
with sourceCommit eb874411a and sourceDirty false. Portable hello-scene and the
new act-practice example compiled and validated with zero findings using
JAVA_HOME 25 while PATH selected Java 21; matching testkit identity verified.
`racing/server/build.py --skip-engine` compiled 153 class files, all version 69;
the generated launcher selects that JDK and declares native access.
