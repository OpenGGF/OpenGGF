# Representative TAS trick verdicts — 2026-10-26

This follow-up records executed verdicts for concrete named tricks from the
previous audit. Final classification delivery remains pending the named
Sandopolis flame-dash under-slope zip described below. The user clarified
that representative proof is sufficient: publication-specific edge cases are
recorded separately and do not prevent a verdict for the demonstrated mechanic.
The delivery base is `c69447d77baddd8d3f7e4a177d73c8d327dd223d`.

## What the verdict means

- **Supported:** a representative valid entry state, action and observable exit
  reproduce the named native behavior through the production owners. A relevant
  control distinguishes the behavior from an ordinary trajectory or fixture
  artifact. Existing executed regressions count when they already do this.
- **Unsupported:** a reached representative scenario contradicts an independently
  specified native outcome, or an actual required capability is exercised and
  unavailable. The record identifies the engine result and the native result.
  Missing evidence, capture setup failures and elapsed investigation time do not
  establish unsupported behavior.

A passing current-limitation test confirms a specific incompatibility; it is not
native-parity success. Such tests must be named and reported separately from
support regressions. Where practical, first execute the native expectation and
record its failure, then retain a deterministic check for that known limitation.

Broad audit families may contain distinct tricks with different verdicts. Each
concrete child receives its own verdict, and the original family ID remains as a
cross-reference. An aggregation of tested supported and unsupported children is
not an unverified outcome. Vague source-note placeholders require interpretation
before they can be treated as tricks; absent descriptions must never be renamed
as engine limitations.

The report counts named scenarios separately from original families and test
methods. Publication associations belong to each named scenario: an unsupported
variant does not automatically block every publication associated with its
broader family. The original `S1_UNSPECIFIED_ROUTE` entry is a provenance
placeholder for unnamed shortcuts, not a defined mechanism. Its source references
remain visible separately; it receives neither a support verdict nor test credit.

Full movie synchronization, every historical ROM revision, every route variant
and visual pixel parity are separate claims. A local mechanism regression is
sufficient for this inventory; the test's entry state and observation boundaries
must be explicit. Native trace rows remain comparison-only.

## Execution ownership and checkpoint

Three isolated game workers own game-specific tests and verdict matrices. The
parent owns this combined record, integration validation, report reconciliation
and delivery. All start from `c69447d77`; no production repair is requested by
this follow-up. Existing prior tests and repaired scoring remain available.

The reviewed game records preserve the original publication and family IDs,
with native expectations, controls, selectors and per-child evidence commits:

- [S1 matrix](2026-10-26-tas-verdicts-s1.md): initial `ee4f1102549675cda3e082a90b23c74a98da1c42`, grounded rolling-cap follow-up `77e4c3845319f47ec840da7c2148fe4cb48a685c`.
- [S2/KiS2 matrix](2026-10-26-tas-verdicts-s2.md): initial `4f5eb1ee2de0b6128c39728289516c079d71064e`, slope/CPU/camera follow-up `d1f2f9cf831868d46ab7b193b581b008d3ebea6d`.
- [S3K matrix](2026-10-26-tas-verdicts-s3k.md): initial `964c29d25fdab09fe8edd4acbe541a27fc64904c`, movement/mesh/superglide/boss/ordinary-slide follow-up `57e89fb6c5e2a0a68691892b641f9c27033b37b0`.

The S3K follow-up is an interim handoff. Its U01 mechanism—fire dash into a
Sandopolis slope, remain embedded while sliding, then zip at the bottom—has
no support verdict yet. Two independently captured native inlets instead
produce ordinary slides; one exactly matches the engine at tick 100. The
retained ordinary-slide test proves that constituent and its exit lock only.
Neither an unsuccessful inlet search nor the absence of an engine/native
contradiction establishes unsupported behavior. The remaining investigation
seeks the actual exploit inlet from the published movies, rather than treating
this distinct named mechanism as an edge case.

## Combined validation

The first combined batch ran at `1b93271b8fe9bf92311eaf3ee003fe69834b5284`
with prose-only parent edits. It selects all settled new/changed test classes
except `TestTasS3kTerrainEntry`, whose follow-up was still active, and adds the
backend-property isolation regression: **45 classes, 123 cases, zero failures,
errors or skips**. It finished at `2026-10-26T14:46:23Z`, with **97 seconds Maven
execution**, **zero queue wait**, and **98.62 seconds command wall time**.
All four original ROM paths were absolute; `DISPLAY` and `WAYLAND_DISPLAY`
were unset and surfaceless EGL was selected. No desktop input or window was used.

The change-based plan selected 3,135 smoke classes before the final S2 additions.
That selection is disproportionate for test/docs-only changes with unchanged
production and shared helpers. Focused combined batches cover every changed test
and the required S3K domain gates; the settled batch also exercises cross-game
and reused-JVM backend isolation. This is bounded validation, not a full-suite,
complete-route or full-movie pass. Java 25, Lua 5.4 and PowerShell preflight passed.

`MAIN_WORKSPACE` below denotes the original main checkout with the verified ROMs;
it was `$HOME/code/projects/OpenGGF` in this run. The literal class selector is:

```bash
TAS_VERDICT_SETTLED_SELECTORS='com.openggf.TestTasS3kTitleCapabilities,com.openggf.game.sonic1.objects.TestTasS1FinishVerdicts,com.openggf.game.sonic1.objects.TestTasS1PlatformVerdicts,com.openggf.game.sonic1.objects.TestTasS1SlopeVerdict,com.openggf.game.sonic1.specialstage.TestTasS1EmeraldVerdicts,com.openggf.game.sonic2.TestTasS2CameraStreaming,com.openggf.game.sonic2.TestTasS2CurrentLimitations,com.openggf.game.sonic2.TestTasS2DezArtAlias,com.openggf.game.sonic2.TestTasS2ManualTails,com.openggf.game.sonic2.TestTasS2OilHops,com.openggf.game.sonic2.TestTasS2PlatformZip,com.openggf.game.sonic2.TestTasS2SidekickSchedule,com.openggf.game.sonic2.TestTasS2SlopeJump,com.openggf.game.sonic2.TestTasS2VerticalCamera,com.openggf.game.sonic2.kis2.TestTasKis2MonitorMadness,com.openggf.game.sonic2.kis2.TestTasKis2SkyChaseChain,com.openggf.game.sonic2.kis2.TestTasS2StarpostEntry,com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint,com.openggf.game.sonic2.objects.TestTasS2CapsuleManipulation,com.openggf.game.sonic2.objects.TestTasS2CapsuleRng,com.openggf.game.sonic2.objects.TestTasS2SupportUnload,com.openggf.game.sonic2.objects.TestTasS2WfzSpindashStorage,com.openggf.game.sonic2.specialstage.TestTasS2JumpLag,com.openggf.game.sonic2.titlescreen.TestTasS2IntroP2Start,com.openggf.game.sonic3k.TestTasS3kUnavailableCapabilities,com.openggf.game.sonic3k.features.TestTasS3kWaterSkimLifetime,com.openggf.game.sonic3k.objects.TestTasS3kDdzMissileSteering,com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations,com.openggf.game.sonic3k.objects.TestTasS3kSignpostRng,com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereStartDelay,com.openggf.level.objects.TestTasS3kOffscreenSolid,com.openggf.level.objects.TestTasS3kOrtExhaustion,com.openggf.physics.TestTasS1ContinuousZip,com.openggf.tests.TestTasS1CapsuleVerdicts,com.openggf.tests.TestTasS1Movement,com.openggf.tests.TestTasS1ObjectVerdicts,com.openggf.tests.TestTasS1PausePlcVerdicts,com.openggf.tests.TestTasS1TerrainVerdicts,com.openggf.tests.TestTasS1WaterVerdicts,com.openggf.tools.TestHeadlessBackendPropertyIsolation,com.openggf.tools.TestTasS3kBonusBounds,com.openggf.tools.TestTasS3kNewGamePlus,com.openggf.tools.TestTasS3kPauseClock,com.openggf.tools.TestTasS3kSpecialStageEntryInput,com.openggf.tools.TestTasS3kTallyDeath'
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dtest=$TAS_VERDICT_SETTLED_SELECTORS" \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" \
  "-Dsonic2.rom.path=$MAIN_WORKSPACE/s2.gen" \
  "-Ds3k.rom.path=$MAIN_WORKSPACE/s3k.gen" \
  "-Dkis2.rom.path=$MAIN_WORKSPACE/kis2.gen" test
```

The second combined batch ran at `87432bff75282fecde9b9dfb963e696388809154`
with prose-only parent edits: **8 classes, 83 cases, zero failures, errors or
skips**, finishing at `2026-10-26T15:39:55Z`. Maven execution was **34.355
seconds**, queue wait was **31 seconds**, and command wall time was **66.61
seconds**. It covers the three
affected S3K classes and all required S3K gates, including both level-loading
test classes. Its exact command uses the same original ROMs and environment:

```bash
TAS_VERDICT_S3K_SELECTORS='com.openggf.physics.TestTasS3kTerrainEntry,com.openggf.game.sonic3k.objects.bosses.TestTasS3kMhzRapidHit,com.openggf.tests.TestTasS3kFlameSlope,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading'
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dtest=$TAS_VERDICT_S3K_SELECTORS" \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" \
  "-Dsonic2.rom.path=$MAIN_WORKSPACE/s2.gen" \
  "-Ds3k.rom.path=$MAIN_WORKSPACE/s3k.gen" \
  "-Dkis2.rom.path=$MAIN_WORKSPACE/kis2.gen" test
```

The source comparison from the first tested commit to the second finds only
`TestTasS3kTerrainEntry`, `TestTasS3kMhzRapidHit` and `TestTasS3kFlameSlope`
changed under `src/`. All 45 earlier selected classes and all production/shared
helpers are unchanged. Thus the two disjoint selections provide **206 passing
cases across 53 classes** for the settled changes. This does not close U01 or
validate the separately developed test-recording tool. New executable edits
require affected validation; later prose edits do not change these results.

## Recorder integration validation

The user also requested a tool to record today's actual test fixtures and stitch
short clips. Its [implementation record](2026-10-26-tas-test-reel.md) and
[usage guide](../../../tools/test_reel/README.md) distinguish static inventory,
executed invocations and real rendered frames. The initial merged implementation
is `78296018bbc1ceb041a29c0292e0d0d3d3ee99d9`.

At integration `e0ca9b1e2dfa4d51c2ca7fa7bca5ad7ca5e31d6e`, with only scope JSON
and prose edits, **13 classes / 104 cases passed with zero failures, errors or
skips**. The run finished at `2026-10-26T16:21:56Z`: Maven **36.977 seconds**,
queue wait **58 seconds**, command wall **95.60 seconds**. Recording was disabled
for ordinary fixture execution; the extension's own tests explicitly exercise
recording, parameterized identities, body versus final JUnit outcome, a failing
teardown, and absent menu state. Graphics/audio/isolation guards and the required
S3K gates ran in the same selection. This requalifies the three adapted fixtures
following their recording-hook edits; counts overlap the earlier 206-case record
and must not be added as distinct cases.

The change planner selects 4,224 smoke classes for the new shared test extension,
service registration and unclassified tooling. The implementation is opt-in and
leaves production code unchanged. Focused lifecycle, opt-out fixture, rendering
registration, audio and reused-JVM isolation checks address its bounded effects;
this is not a broad smoke-suite result. Actual rendered opt-in runs remain
separately documented in the recorder record. A subsequent Python-only provenance
correction must preserve actual executed fixture hashes without changing this
Java evidence.

```bash
TAS_REEL_INTEGRATION_SELECTORS='com.openggf.tests.TestTasS1Movement,com.openggf.game.sonic2.TestTasS2ManualTails,com.openggf.tools.TestTasS3kPauseClock,com.openggf.tests.reel.TestTestReelExtension,com.openggf.tests.TestHeadlessStateTeardownGuard,com.openggf.tools.TestHeadlessBackendPropertyIsolation,com.openggf.tools.TestRawGlfwTestSetupGuard,com.openggf.tools.TestHeadlessAudioOwnership,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading'
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dtest=$TAS_REEL_INTEGRATION_SELECTORS" \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" \
  "-Dsonic2.rom.path=$MAIN_WORKSPACE/s2.gen" \
  "-Ds3k.rom.path=$MAIN_WORKSPACE/s3k.gen" \
  "-Dkis2.rom.path=$MAIN_WORKSPACE/kis2.gen" test
```

## Observed limitations

The native Start pause clock has reached contradictory outcomes in S1, S2 and S3K
at the delivery base. S1's native GPGX control advances V-int 629 through 633
while level frame 181 holds; the engine's three paused iterations keep V-int at
1 instead of 4. S3K independently reaches the same frozen object/global clock
and resumes it with gameplay. The explicit checks are
`TestTasS1PausePlcVerdicts#currentLimitationNativeStartPauseFreezesCapsulePhaseClock`
and `TestTasS3kPauseClock#currentLimitationStartPauseFreezesVintAlongWithLevelAndPlayer`.
The S1 retained limitation/resume check passed in its 54-case final focused
selection after the native assertion failed at the reached paused state. S3K's
retained limitation and running/resume controls also passed. S2's
`TestTasS2CapsuleManipulation#currentLimitationPauseFreezesVintAndCannotOmitTheLateAnimal`
also passed through recorded Start/release/Start and the real frame clock.
The frozen clock makes resume hit the original animal-spawn phase; the resulting
animal advances RNG and delays results. An ordinary off-mask running control
avoids the animal and reaches the real results transition.

S1 also reaches the missing offscreen standing retention used by the SBZ3 slope
trigger. `TestTasS1SlopeVerdict` acquires the real floating platform, moves the
camera offscreen and walks beyond the platform edge. Native `FBlock_Action`
omits `SolidObject` and leaves standing latched; the engine releases the rider.
The onscreen release control passes. This concerns collision dispatch while
the object remains alive, not object deletion. Both retained cases also passed
in the first combined batch; their native proof is in the S1 matrix.

These observations are linked from the [open-bug ledger](../../status/known-bugs.md#tas-pause-clock-does-not-advance-during-native-start-pause--2026-10-26).
They do not convert setup mistakes into unsupported tricks: S1's capsule
approach initially missed the native post-boss camera maximum, and the platform
component fixture initially failed to acquire standing contact. Both setups
were corrected or replaced before assigning a mechanism verdict.
