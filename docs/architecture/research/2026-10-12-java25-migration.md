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
cost about 24 plus 10 minutes per tree. This task's complete ROM-backed ordinary
runs actually took 65.2 minutes on Java 21 and 57.4 minutes on Java 25, with
5.3 and 4.7 minutes for guards. These single runs had different test counts and
shared-host load; they are verification costs, not a Java 25 performance benchmark.
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

The initial implementation is committed as `84fe179b4`; subsequent compatibility
fixes and completed verification are recorded below. Local ordinary/trace failures
remain explicitly attributed to the unchanged baseline, not described as passes.

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


## Transport probe scheduling race

The next hosted run
[38247110837](https://github.com/OpenGGF/OpenGGF/actions/runs/38247110837), at
`efe80a0da9e80d08eec1237c03f22ffa0850e1a1`, stopped in ordinary testing:
23,551 executions, one failure, zero errors, 4,288 skips. The sole failure was
`TestGolfTransport.localhostOrderingBoundsDeadlineAndCleanup`: “room gets
overflow hold event”. Its 15-case class passed in the previous complete hosted
run on identical ordinary code; the actual unchanged e76 Java 21 baseline also
passed all 15 cases, with this method taking 5.212 seconds. Guards/native jobs
did not run in this failed workflow.

The cause is an existing test synchronization assumption. `GolfConnection.
disconnect` publishes its atomic closed flag, then closes the outbound mailbox,
then publishes `Disconnected` under the incoming monitor. Observing the first
write does not establish happens-before for the later event. The test awaited
`isClosed()` and drained once, so it could miss that event; the production room
drains each tick and consumes eventual publication. Both Java 21 and 25 permit
this ordering. No evidence attributes the race itself to a Java 25 semantic
change. A production locking change and blanket timeout increases were rejected.

The probe now accumulates drain snapshots until a terminal event appears within
the probe’s existing 3-second await bound, asserts exactly one notification and
retains the exact overflow/deadline reasons. The same correction covers the
adjacent asynchronous reconnect and deadline checks; synchronous send-overflow
is unchanged. A new deterministic case holds the outbound mailbox monitor during
a real 257-packet overflow, proves closure is visible before event publication,
then releases the monitor and checks one overflow event, an empty queue and
retired workers. No production source changes are involved.

## Completed destination comparison

The actual unchanged production baseline is `e76a380c3d0c8e3769fcdb745f27ef3d8889c8fe`.
The frozen candidate tested locally and in the successful hosted workflow is
`80f3d7c28aa7b0255521b8af130a7c4c3ed9a550`. The later develop
`15f3cf371a49e9755568588b00b1f515225aaa4d` changes only the mod audit,
remediation plan and backlog; merge `555c0ce71` preserves them and changes the
plan's active toolchain requirement to Java 25. Its historical Java 21 audit
measurements remain intact. The executable baseline is therefore unchanged;
documentation reconciliation does not require repeating these engine checks.

All local Maven invocations unset inherited `LD_LIBRARY_PATH`. The baseline uses
system OpenJDK 21.0.12.1; the candidate uses task-owned Temurin 25.0.4.1+1 via
`JAVA_HOME` and `PATH`. Both actual runner preflights passed Java, Lua 5.4 and
PowerShell. The candidate plan selected all 3,097 ordinary classes and all ten
categories, followed by all guards in a fresh JVM, without manually narrowing
the selection. Queue statistics had risen to about 64 minutes median and
91 minutes p95 for ordinary work, so both final runs used a 120-minute limit
per invocation and the existing 10-minute no-output limit; queue waiting does
not count. The original f9 results above are historical, not the final baseline.

```bash
# Unchanged e76 baseline, in its own detached cow worktree, Java 21:
env -u LD_LIBRARY_PATH LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py --category all --max-minutes 120 --run
# Frozen 80f candidate, JAVA_HOME/PATH selecting the owned JDK 25:
env -u LD_LIBRARY_PATH LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base e76a380c3d0c8e3769fcdb745f27ef3d8889c8fe --max-minutes 120 --run
```

Both recorded ordinary commands supplied the original absolute ROM inputs below.
`OPENGGF_MAIN` denotes the original main checkout's absolute path; the executed
commands contained resolved paths, not this documentation placeholder:

```text
-Dsonic1.rom.path=${OPENGGF_MAIN}/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=${OPENGGF_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen
-Ds3k.rom.path=${OPENGGF_MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen
```

| Completed lane | Reports | Case executions | Failures | Errors | Skips | Seconds |
|---|---:|---:|---:|---:|---:|---:|
| e76 Java 21 ordinary | 3,095 | 26,927 | 26 | 0 | 63 | 3,913.84 |
| e76 Java 21 guards | 87 | 674 | 0 | 0 | 0 | 317.95 |
| 80f Java 25 ordinary | 3,095 | 26,932 | 26 | 0 | 63 | 3,446.22 |
| 80f Java 25 guards | 87 | 676 | 0 | 0 | 0 | 283.72 |

These are case executions, including parameterized/repeated invocations, not
unique test classes. All 26 ordinary failure identities and exception types
match the historical route rows above, excluding the historical stale UI and
inherited-loader-environment failures. Twenty-five full assertions match byte
for byte. The remaining `TestSszTailsColdRouteCapture.
coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` assertion is
2,915 baseline / 2,914 candidate characters; the short runner message is truncated
at 2,048, so comparison used the full assertion in `detail`. Only incidental
`RewindObjectStateBlob@[0-9a-f]+` identity tokens differ. Normalizing exactly those
tokens gives identical SHA-256
`a6ba1eedc5b76ccb078dc97b50f53b9a320c61e41fb1a6d40cad3021fd43a026`.
Frame 4018, slot bits 24/29, EggRobo child kinds, counts, coordinates and every
serialized-state difference remain unchanged; no general hexadecimal/address
normalization was used. There are no new or worsened observed failures.

All 63 skip identities and semantic reasons match (JDK stack-frame line numbers
differ). They comprise 32 inapplicable Infinite Sonic platform/flyer routes,
five graphics prerequisites, one CPZ spin-tube assumption and 25 opt-in
capture/diagnostic/native/local-fixture cases. None is a missing-ROM skip.
The required AIZ skip-headless, both level-loading classes, bootstrap resolver
and decoding checks passed all 60 cases without skips on both runtimes.
The five added ordinary executions are the cross-JDK benchmark comparator,
SFX overflow, transport publication, Java 25 bytecode validator and fixed-audio-
runtime rejection regressions. The two added guards cover Java-version formats
and Maven-selected packaging JVMs. The separate final focused command
`maven_queue.py --lean -Dmse=off -Dtest=TestGolfTransport,TestModTestKitDistributionGuard test`
passed all 16 transport and two distribution cases, with zero skips.

Run IDs `20261012T113340Z-476f1bc8` and `20261012T124612Z-c10a5587` were inspected,
compared and acknowledged through the runner; both diagnostic directories were
deleted. The clean detached baseline worktree was removed after accounting for
generated outputs and links. No original ROM or shared configuration was removed.

## Trace, graphics and native packaging evidence

Both runtimes ran the same queued, single-fork command with the three absolute
ROM properties above:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS1Ghz1TraceReplay,TestS2Ehz1TraceReplay,TestS3kAizTraceReplay \
  <the three quoted absolute ROM properties> test
```

Each completed 18 cases with one failure, zero errors and zero skips. S1 GHZ1
and S2 EHZ1 passed; 15 of 16 AIZ cases passed. The failing AIZ `replayMatchesTrace`
assertion is identical: 57 errors, zero warnings, first error frame 20302,
`player_animation_id`, expected `0x0000`, actual `0x0005`. This compares the
reported assertion and observed frontier, not an unexamined per-row trace diff.
No gameplay change or trace-frontier movement was made.

The first five-class graphics attempt with `EGL_PLATFORM=surfaceless` skipped
all five because no suitable EGLConfig existed; build success was not graphics
coverage. Existing display `:0` reported accelerated AMD Radeon RX 9070 XT,
Mesa 26.2.4, OpenGL 4.6. Repeating only those checks through their supported
native-display mode passed all five, zero skips, in 23.465 seconds:

```bash
env -u LD_LIBRARY_PATH -u EGL_PLATFORM \
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.test.gl.native=true \
  -Dtest=TestForegroundWindowRendering,TestSceneRenderer,TestBackgroundScrollWrapPixels,TestShaderPixelCentreSampling,TestArenaMaskRenderer test
```

After consuming test evidence, a clean target was built with task-owned GraalVM
Community 25.0.2+10.1, without changing system alternatives. `JAVA_HOME`,
`GRAALVM_HOME` and `PATH` selected that toolchain:

```bash
python3 tools/testing/maven_queue.py -Dmse=off clean
MAVEN_OPTS=-Xmx2g python3 tools/testing/maven_queue.py -Dmse=off \
  -Pnative -DskipTests '-DjvmArgs=-Xmx6g,-XX:ActiveProcessorCount=4' package
```

Package completed in 2m22s; native-image generation took 1m8s and reported
4.58 GB peak RSS. The actual command contained `-J-Xmx6g` and
`-J-XX:ActiveProcessorCount=4`, retaining the configured compatibility architecture
and runtime initialization. The Linux x64 ELF's dynamic dependencies resolved.
The Linux archive assembled from this clean output passed the exact release
workflow package-smoke script, including executable, configuration, native
libraries, notices and absence of bundled code mods. Retained user-test outputs:
`target/OpenGGF`, `target/OpenGGF-linux.tar.gz` and the extracted
`target/linux-package/OpenGGF/` directory. No interactive native launch was
claimed; launching the engine can write configuration, and the task worktree's
config is shared. The extracted package has its own copied config and requires
the user's ROM paths.

LWJGL 3.3.3 still emits the JDK's terminal-deprecation warning for
`Unsafe.objectFieldOffset`; GraalVM also warns that its bundled
`JNIConfigurationResources` / `ReflectionConfigurationResources` metadata options
will require explicit unlocking in a future version. Current compilation,
native loading and graphics passed without experimental Byte Buddy flags or
warning suppression. These future dependency-maintenance costs remain visible.

## Hosted acceptance and handoff boundary

The nonpublishing [Release run 38249906448](https://github.com/OpenGGF/OpenGGF/actions/runs/38249906448)
completed successfully at exact `80f3d7c28`, with `validate_roms=false`:

- Ordinary: 23,552 executions, zero failures/errors, 4,288 skips, 12m00s.
- Fresh structural guards: 676 executions, zero failures/errors/skips, 10m25s.
- SFX 3, OwnedMocks 2 and transport 16 cases all passed without skips.
- Windows x64, Linux x64 and macOS 15 ARM native compilation, archive packaging
  and package smoke checks passed; universal JVM JAR packaging/smoke passed.
- All four artifacts were uploaded. Release and notification jobs were skipped;
  nothing was published.

Hosted skips cover the cloud's missing ROM/display/media and opt-in prerequisites;
they do not replace the local ROM/trace evidence above. Cross-platform package
success does not certify interactive gameplay on those operating systems, macOS
x64 JVM execution, experimental native-mod execution, every trace fixture or
every opt-in benchmark. The hermetic audio wrapper's fixed `/usr/bin/java` remains
Java 21 on this host; its Java 25 rejection contract was tested, but a full
fixed-runtime Java 25 audio capture was not performed. No performance gain is
claimed. User gameplay testing and merge remain pending.

The team workers and single combined read-only reviewer are complete. Source
commits are `84fe179b4`, `a6103d57e`, reconciled merge `eb874411a`, guard expectation
`efe80a0da`, transport regression `80f3d7c28`, and documentation merge `555c0ce71`.
The parent verified the actual PR policy range through 80f successfully (12,367
tree entries); final documentation policy is checked again before PR delivery.
The unmerged feature worktree/branch, native outputs and owned Temurin/GraalVM
toolchains are intentionally retained for user testing. Consumed raw logs and
runner diagnostics are not archived. The parent owns PR creation and watching
its mandatory checks; this record does not claim the PR has already passed.

## PR 221 develop update

After PR 221 passed its checks at `746936694`, develop advanced to
`a0e2b79cc3a47cedd11a0f9daf6a32ea4ced19cb` with the Mutator Lab implementation.
The update uses the repository's merge convention. The sole textual conflict was
an insertion in the measurement-hazard catalogue: both the Java 25 full-assertion
lesson and every upstream native-stage/capture lesson are retained. Automatic
merges retain the new head-inventory CLI commands, original-ROM test setup,
isolated-display policy and API signature pins alongside the selected-JVM and
native-access migration changes.

The new Mutator Lab builder and creator/graphics probes now compile for release
25. Its builder and the isolated window diagnostic select JAVA_HOME when set and
declare native access; current guide commands require Java 25. Historical Java 21
qualification records remain unchanged. No mutator, timing or gameplay algorithm
was rewritten as part of this resolution.

Validation used distro OpenJDK 25.0.4.1 on the resolved merge candidate
(`746936694` plus `a0e2b79cc` and the adaptations above):

- Reset only Maven's generated compiler directories, preserving the retained
  native packages and extracted user-test configuration. Then
  `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify`
  passed in 1m24s, compiling 3,824 production and 3,794 test sources for release
  25 and passing bundled-mod, SDK and artifact preparation/verification.
- With `LUA_BIN=/usr/bin/lua5.4`, queued lean
  `-Dtest=TestExampleMutatorsPackage,TestSpriteGraphicsAccess,TestGgfModCliCommands,TestBuildToolingGuard test`
  passed 130 cases, zero failures/errors/skips. This is focused validation.
- `python3 examples/example-mutators/build.py` passed package validation with
  zero findings; all 14 generated classfiles are major 69. No Engine window was
  launched.
- Python suites `test_engine_window_capture.py` (11),
  `test_compare_category_outcomes.py` (19), and `test_run_categories.py` (35)
  passed. Syntax, mirrored guidance and whitespace checks passed. Preflight
  passed after selecting Lua 5.4 explicitly; the ambient Lua failed its version
  prerequisite before tests.

The change plan against the prior PR head selected 3,144 ordinary classes because
it includes the complete upstream feature intake. This maintenance update follows
the requested build-before-push flow and tests the resolved toolchain/packaging
boundaries directly; it does not claim another local full engine, trace, graphics
or native run. Earlier broad/native evidence above refers to its named source,
not this newer merge. Fresh PR ordinary, guard, policy and creator-kit gates must
pass on the pushed update before handoff.
