# Infinite Sonic — GHZ1 solo Sonic prototype coverage

This mod replaced stock GHZ1 (registry zone 0, act 0, level ID `0x80`) only when
its explicit patch activates for solo Sonic. Since 0.8.0 it replaces every act of
registry zones 0–5 (GHZ, MZ, SYZ, LZ, SLZ, SBZ including SBZ3); Final Zone and the
ending remain stock. The GHZ1 rows below remain the deep coverage; the per-act row
covers the other zones. It is an endless terrain course, not a
new stock act. [Design and decisions](../../designs/2026-10-01-infinite-sonic.md).

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Real code package | `TestInfiniteSonic.compileAndValidate` compiles project sources and calls SDK `package` validation | No ROM payloads or baked assets shipped |
| Entry and traversal | `protectedTraversalPreservesEncountersAcrossRebaseAndReplay` uses explicit test-only invulnerability, 1× clock resets and camera recentering to isolate terrain; runs Right with gap/wall-aware Jump input for 6,000 frames, asserts no death and at least four world rebases | All five current `WidescreenAspect` presets: 320, 352, 400, 528, 800; resolved camera width asserted |
| Rewind at world recycling | Same test captures the full registry, runs 800 frames across recycling, restores and replays | Compares X/Y, fractional X, ground speed entire map, enemy state and occupied object slots; native donor/off; does not test GameLoop's interactive history recorder |
| Backtracking | Same terrain-only setup runs Left with gap/wall-aware Jump input for 1,800 frames, climbing ledge drops in reverse and proves reverse rebasing; normal challenge backtracking can be fatal | Same five widths |
| Every pre-Final act (0.8.0) | `everyZoneActBuildsATraversableDryCourse` runs all 18 registry acts of zones 0–5: block budget, controller-only placement, no water, course start on the flat opening, 3,000 protected Right/Jump frames without death and at least two rebases | 320px only; damage, attacks, rewind and jump-reach physics are GHZ1-only; terrain renders reviewed offline (no GPU, no sprites) |
| Fresh reload / act isolation | `freshReloadResetsTheCourseAndFinalZoneRemainsStock` loads Final Zone, checks stock placements, reloads GHZ1 and checks original generated layout | 320px; physical death/respawn and live-history reset not yet covered |
| Character/team scope | `patchOnlyActivatesForSoloSonic` checks solo Sonic, solo Tails, Sonic+Tails and wrong-game selection | Other teams/characters use stock GHZ, by explicit activation policy |
| Install/registration | Local production scanner, state/trust handling and restricted classloader registration check | IntelliJ default project-root working directory; enabled local jar |
| Art and presentation | Reuses ROM pipeline; mirrored chunks and stock GHZ background | Native-width opening/HUD rendered in 0.4; background seam continuity at recycling and other-width GPU review remain open |
| Encounter habitats | `encountersAreSeededSpacedAndFitTheirEntirePatrolCorridor` checks 997 sections, both kinds, rest gaps and floor profiles against decoded collision | Fixed seed; ground relief and flying clearance checked across full patrol |
| Jump requirement | `holdingRightCannotCompleteTheCourse` exercises ordinary unassisted Right input after the safe opening | All five widths; normal damage, bounded live slots; automatic respawn remains open |
| Attacks and explosions | `aPhysicalStompDestroysTheBadnikAwardsScoreAndDoesNotRespawnIt` exercises ground and air enemies | Real touch response from a positioned descending spin; score, no immediate respawn and explosion rewind/replay |
| Ring collection | `ringRowsCollectThroughGameplayAndRestoreWithTheCourse` exercises real touch collection, ring count and restore/replay | Opening row, no repeated award after sparkle; GPU review remains open |
| Corridor geometry | `corridorsAreBoundedSeededAndHaveLevelRunways` checks 1,000 sections; encounter habitat test compares actual decoded collision with the terrain oracle | 64–192px widths, ±32/±64px steps, pit-less drops only, four elevation tiers, seamless section joins, no enemies in corridors, flat banks |
| Jump reach | `realPhysicsCanClearEachGapInBothDirections` uses actual Sonic movement and landing | All five widths, each at its largest paired elevation change, both directions (one climbs), 3px/frame initial speed with held direction and Jump |
| Gameplay breadth | Terrain-aware bounded patrols, ring trails, pits, hills/dips, speed/countdown HUD, no finish | No missiles, moving platforms, breakable floors, checkpoints or bosses; donors unverified |

## Execution

Main checkout, `feature/ai-infinite-sonic`, base
`67c850fc5132156acede8687ca169ada074f795f`, uncommitted prototype, 2026-10-01:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH \
python3 tools/testing/maven_queue.py -q -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Completed: **7 tests, 0 failures, 0 errors, 0 skipped**, 13.59 seconds test time.
`build.py` also completed engine compilation and SDK package validation. Focused
validation only; no full ordinary/guard-suite or trace-parity claim.

The pre-existing ROM-load warning for S1 mappings at `0xE8DF` appeared during the
stock bootstrap as well as the mod load; this task does not alter object art.
The implementation and the passing assertions establish a playable terrain
prototype, not full compliance with the stock zone/act certification standard.

### Enemy follow-up

Base `ff18f7ffd45d192802441f3effa2d4d9fc2a279a`, uncommitted enemy changes in the
same checkout, 2026-10-01: **15 tests passed, 0 failures, 0 skipped**, 12.67 seconds.
Java 21 `javac --release 21` compiled the mod and `TestInfiniteSonic`; JUnit Platform
1.10.3 `LauncherFactory` selected that class using `target/classes`, the cached Maven
dependency classpath and the absolute World REV01 ROM path above. The final test run
includes all five traversal and damage widths plus both enemy stomp variants.

This was a direct focused JUnit run against cached unchanged engine classes, not a
completed Maven/category run: the queue could not write its admission lock because
`.git` became read-only. SDK package validation also passed for the installed 0.2.0 jar. The production scanner,
trust store and restricted classloader loaded it with two patch registrations and no
rejections or registration failures.
No full suite, structural guards, GPU review or automatic death/respawn claim.

### Queued Maven verification before commit

On 2026-10-02, filesystem permissions were restored. On the same base and enemy
changes, the queued Maven command from the original execution section was rerun
with Java 21 (also first on `PATH`). Surefire reports **15 tests, 0 failures,
0 errors, 0 skipped**, 11.47 seconds; Maven exited successfully. This supersedes
the cached-engine limitation for delivery. SDK packaging runs inside this test.
Validation remains focused; the full ordinary suite, structural guards and GPU
review were not run.

### Rings and jump corridors (0.3.0)

On 2026-10-02, main checkout `feature/ai-infinite-sonic`, base
`b6abb05e818954054392a144ca56377671aa7bfa` plus the uncommitted mod follow-up:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH \
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Completed: **20 tests, 0 failures, 0 errors, 0 skipped**, 12.83 seconds test time,
34.786 seconds Maven time. Includes SDK package validation. The real jump checks
start at 3px/frame with held directional input, so subsequent air acceleration is
part of the tested maneuver. Traversal protection suppresses enemy damage only;
pit collision and jumping remain real. Normal unprotected Right-only tests prove
that running alone cannot complete the course. Rings now replace the original
zero-ring behavior; the earlier results above describe previous versions.

The user explicitly requested mod-specific validation only. No full engine suite,
structural guards, visual capture, movement donor or automatic respawn claim.

A subsequent focused `-Dtest=TestInfiniteSonic#holdingRightCannotCompleteTheCourse`
run tightened the existing assertion: all five widths die before logical X=2560,
within 2,400 frames, proving the first 128px pit requires a jump. **5 passed,
0 failures/errors/skips**. Production code was unchanged after the 20-test run.
The final 0.3.0 jar passed `build.py` SDK packaging and replaced the existing enabled,
trusted local `mods/infinite-sonic.jar`; the stored SHA-256 matches the installed jar.

### Escalating survival challenge (0.4.0)

Base `30f4e0654e79d3b56e0976277eede2919f0ad983`, same branch and checkout,
2026-10-02. Native physics constants are unchanged; interactive presentation pumps
complete gameplay steps. The clock and fractional step budget are module rewind
state. Whole-game speed increases every 30 seconds of active play (including PAL
cadence), up to the bounded 32× host ceiling.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| First speedup during traversal | `normalTraversalReachesFirstSpeedup` | All five widths; native physics, real scroll pressure and recycling; test-only enemy invulnerability |
| 30-second linear steps | `speedAndCountdownUseThirtySecondLinearIntervals` | Exact first two boundaries (+0.25× each), 2250 simulation ticks for the second interval, PAL and ceiling |
| Scroll and score | `minimumScrollAllowsFasterRunningAndSurvivalScoreScales` | 1×/1.25×/1.5×; actual camera and score over 60 paced frames; positioned above terrain to isolate timing |
| Left-edge game over | `fallingBehindEndsRunDespiteRingsAndInvulnerability` | All five widths; partial visibility survives, complete exit kills, lives exhausted, score/clock/camera freeze |
| Boundary rewind | `speedupBoundaryRestoresAndReplaysWithScoreCameraAndHud` | Positioned 1×→1.25× boundary; clock adapter, controller, score, camera and HUD text |
| Interactive host pacing | `TestGameLoop.customPresentationPacing*` | Complete-step budget, canonical single-step entry, scene boundaries, pause, rewind input and bounded creator budget |
| Rendered HUD | `GameplayCaptureSession` diagnostic using actual `stepPresentationFrame()` and an explicitly wrapped module | Native 320px; frame 60 positions the clock at 29.98s to show the speedup without a long route |

Earlier bidirectional gap and long traversal tests now explicitly recenter the
camera (and hold the terrain traversal clock at 1×). Those remain terrain-only
checks; they are not survival claims. The independent challenge checks above
exercise the live scroll/failure rules without this protection.

Final focused command (Java 21; Maven on PATH):

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestInfiniteSonic,TestGameLoop,TestDelegatingGameModuleCoversInterface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModApiSignatureSurface' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

**154 tests passed, no failures/errors/skips**, 114 seconds Maven time. This includes
the final same-session level-load pacing boundary and the corrected descriptor.
`build.py` packaged version 0.4.0 successfully; the existing local enabled/trusted
installation was replaced, its digest updated, and the production scanner, effective
catalog and classloader registered both patches with zero failures.

Change-based command: `run_categories.py --base 30f4e0654e79d3b56e0976277eede2919f0ad983 --run`,
with Maven and portable PowerShell 7.4.6 on PATH and `LUA_BIN=/usr/bin/lua5.4`.
Preflight passed. The ordinary lane completed 2,955 reports / 22,371 tests in
402 seconds: **1 failure, 45 errors, 3,025 skipped**. Three errors were the task's
temporary descriptor comment, fixed and covered by the focused pass above.
The runner stopped before guards because the descriptor repair changed the tree
during validation; guards were run separately against the final candidate (results below).

Forty-two errors were null S3K ROM lookups in seven audio classes. The root S3K
file has SHA-1 `b711a909cce238ca4af3e517a2edca306228efa5`, not the required
`cfbf98c36c776677290a872547ac47c53d2761d6`, so automatic discovery did not supply
`s3k.rom.path`. No ROM was renamed, linked or replaced. Sampled skips include
missing S3K, KiS2 lock-on, opt-in benchmarks, and tests that require literal
`s2.gen` despite the valid absolute S2 property. The runner retains only a bounded
skip sample (1,000 of 3,025); no complete ROM coverage is claimed.

The remaining failure is `TestRemainingRewindTailInventory`: expected total/passed
1315/1072, actual 1316/1073. A source export of the starting commit under `target/`
(no worktree/branch change and no shared build output) reproduces that mismatch.
The first exported-baseline run skipped the seven audio classes (29 cases): its
working directory changed relative ROM path resolution. Repeating with Surefire's
working directory set to the original checkout, while retaining the baseline's
own compiled classes/build tree and the same absolute S1/S2 properties, reproduced
**all 43 candidate failure/error case keys, types and messages identically**:
1 inventory failure and 42 null-path errors, no skips. The candidate's isolated
matched command also reports 43 tests / 1 failure / 42 errors / no skips.
No unrelated engine or test inventory changes were made to turn the suite green.

Both matched runs selected only:
`TestS3kBlueSphereAudioRom,TestS3kFadeOutPsgSilence,TestS3kMusicTempoRuntime,TestS3kOneUpRestoreRom,TestS3kProductionAdmissionObservation,TestS3kSfxLifecycleRom,TestS3kSfxRuntimePathWithMusic,TestRemainingRewindTailInventory`.
They used the queued Maven `-Dmse=off -Dtest=<list> test` command with the same
S1/S2 properties as the broad lane. The baseline source revision remained the
pre-task SHA above; only the temporary fork working directory was adjusted.

The standalone `maven_queue.py -Dmse=off -Pguards test -B` run (same environment
and absolute S1/S2 properties) completed **672 tests / 1 failure / 0 errors /
0 skips** in 243 seconds. Its sole failure was
`TestObjectPhysicsStandardizationGuard#productionObjectPhysicsStandardizationHasNoUnapprovedViolations`:
`LrzFlameObjectInstance.getShieldReactionFlags` reports
`TOUCH_PROFILE_HOOK_WITHOUT_PROFILE`. A baseline-only queued `-Pguards -Dtest=<that
class#method> test` reproduced exactly the same violation. The other 671 guard
checks passed. Ordinary and guard diagnostics were inspected and removed; the
category-run directory was acknowledged. This remains a non-green broad suite
with reproduced pre-existing failures and incomplete ROM coverage, alongside a
green focused candidate validation.

Visual evidence is outside the repository at
`$HOME/captures/infinite-sonic-2026-10-02-whole-game/`: frame 10 shows 1×,
frame 90 shows 1.5×, and frame 239 shows game over. The diagnostic seeded only
the challenge clock at presentation frame 60; it did not modify Sonic's physics.
State rows 61/62 advance 6/12 pixels, confirming alternating complete steps at 1.5×.
These images verify the native HUD and opening, not full high-speed route coverage.


## Right-margin camera follow correction (2026-10-02)

Follow-up to `841fb3d98b`, in the same feature checkout. Immediate velocity matching
prevented Sonic from banking a lead; the camera now retains its 4.5px/tick minimum
until Sonic reaches a 48px right margin, then follows his position. No native
physics, engine timing, API or rewind state shape changed.

`sonicGainsGroundAcrossSpeedupAndIsHeldAtTheFollowPoint` checks all five viewport
widths through 600 presentation frames using the actual module step budget across
1×→1.25×. It isolates horizontal integration from terrain by resetting height and
vertical speed, supplies the normal 6px/tick running velocity, and verifies that
Sonic gains ground, reaches the margin, remains bounded and survives. The separate
normal traversal tests retain real terrain and movement. Scroll/score coverage
also checks that high velocity before reaching the margin does not accelerate
the camera. Existing game-over, recycling and rewind checks remain green.

Validation: queued `-Dmse=off -Dtest=TestInfiniteSonic` with an absolute
`sonic1.rom.path`, **38 tests, zero failures/errors/skips**, 40.7 seconds Maven time.
The change-based plan against `841fb3d98b` selects 2,957 classes plus guards because
example sources are unclassified. Proportionate focused validation replaces that
run: production changes are confined to this mod's camera policy, whose viewport,
pacing, terrain, score, death and rewind consumers are exercised by this class.
This is not a full-suite pass or a new visual capture; earlier broad-suite failures
and ROM coverage limits above remain unchanged. The SDK build/package also passed.


### Follow point moved just right of centre

Follow-up to `382ae02673`: the follow point is now 60% of viewport width rather
than 48px from the right edge. The same five-width regression checks the new
position, lead recovery and the 1.5× transition. Queued `-Dmse=off
-Dtest=TestInfiniteSonic` with absolute S1 ROM path: **38 passed, zero failures,
errors or skips**, 38.4 seconds Maven time; SDK packaging passed. The plan against
that base again selects all 2,957 classes because examples are unclassified;
proportionate focused validation applies to this isolated mod framing adjustment
for the same reasons above. No engine timing or physics changes; no new visual
capture. The local enabled/trusted jar was rebuilt and refreshed.

## Five-second warning, matching audio and restored percussion (2026-10-02)

Base `e21eaa933d52a2b9ba039565e075b0f87bf381ca`, same feature checkout. The mod's
warning is clock-derived and covers 6→5 seconds, 1 second, the speedup, restored
clock state, maximum speed and end-of-run playback reset. Existing five-width
terrain, scrolling, scoring and world/object rewind regressions remain applicable.
The shared host rate is continuous rather than the alternating integer tick
budget, and is released on pause, rewind input, mode changes and teardown.

Focused queued Maven checks (`-Dmse=off`, absolute S1 ROM property):
`TestInfiniteSonic` **39 passed**, and `TestAudioPresentationProducer` **25 passed**.
A subsequent focused invocation of `TestGameLoop`, `TestSmpsPhysicalPolicy`,
`TestSonic1UnifiedAudioPresentationRomIntegration`, and `TestModApiSignatureSurface`
passed **118 tests, no failures/errors/skips**. Earlier donor-policy assertions
expected S1's obsolete 202-write legacy stop program; they were corrected to the
36-write shipped StopAllSound program, with literal register assertions retained.
The SDK example build/package passed, producing version 0.5.0.

A bounded baseline diagnostic compiled only the pre-task S1 physical policy into
an isolated temporary class directory and selected the new cold-percussion JUnit
method with that class preceding current production/test classes. Same checkout,
absolute S1 ROM path and dependencies: **1 failed, no skips**, on the GHZ drum
waveform assertion. The corrected policy passes that same method. All sample
bytes and music programs came from the ROM; the test solos physical DAC/FM6 and
requires changing output in both speakers after register settling.

Shared audio/timing/API changes require ordinary and structural validation, not
the earlier mod-only proportionate exception. The plan selects 2,957 ordinary
classes and all guards. Preflight passes with Java 21, portable PowerShell 7.4.6
on PATH and `LUA_BIN=/usr/bin/lua5.4`. Final broad results are recorded below.
No new gameplay screenshot or end-to-end high-speed route certification is claimed.

Final broad command: `run_categories.py --base e21eaa933d52a2b9ba039565e075b0f87bf381ca --run`,
on the candidate working tree in this checkout, run `20261002T122612Z-58d701bb`.
Ordinary: **22,381 tests / 2 failures / 42 errors / 3,025 skips**, 392.86 seconds.
Guards: **672 tests / 1 failure / 0 errors / 0 skips**, 250.96 seconds.
The existing inventory failure (1315/1072 expected versus 1316/1073 actual),
42 S3K null-ROM errors in the same seven classes, and LRZ flame profile guard
violation match the previously baseline-reproduced failures documented above.
Those unrelated paths were not changed. The skip sample again includes missing
S3K/KiS2 ROMs, literal `s2.gen` requirements and opt-in diagnostics; the runner
retained 1,000 of 3,025 skip reasons, so this is incomplete ROM coverage.

The new percussion regression initially inherited the six-Hz dummy backend from
`TestGameLoopAudioPresentationModes`. `resetState()` deliberately retains the
device; zero/one sample per packet cannot demonstrate a within-packet waveform.
The regression now installs a normal-rate no-device backend before ROM loading.
A matched temporary JVM run of the preceding class sequence reproduced the
failure and verified the correction. Final queued Maven command:
`-Dmse=off -Dtest=TestGameLoopAudioPresentationModes,TestSonic1UnifiedAudioPresentationRomIntegration
-Dsurefire.runOrder=alphabetical -Dsonic1.rom.path=<absolute S1 path> test`:
**29 tests passed, no failures/errors/skips**. Only test setup changed after the
broad run; the production candidate was unchanged. The boundary/policy follow-up
also passed **107 tests**, including same-frame scene exit and rewind rate release.

`exec:exec@prepare-openggf-mod-sdk` passed actual SDK/Javadoc generation; the
normalized signature generator exactly matches the committed candidate pin.
The example build/package passed and the enabled, trusted local jar was refreshed.
Results and skip/failure summaries were inspected, and the category run acknowledged
and deleted. Focused validation is green; the broad suite remains non-green for
the recorded unrelated failures and is not represented as full certification.

## SMPS tempo follows accelerated playback (2026-10-02)

Base `5637105e5c7aa4172c509bb94a7e63d9765e401d`, same branch/checkout. The prior
connection raised chip pitch without speeding up note services. The corrected
producer interleaves service/render intervals and captures fractional source
phase with audio snapshots. No mod source, physics, game pacing or ROM asset
change is involved.

Focused queued Maven checks (`-Dmse=off`, absolute S1 ROM property where needed):
`TestAudioPresentationProducer,TestAudioPresentationProducerRewind,
TestUnifiedAudioPresentationIntegration,TestAudioPresentationAllocationBudget`:
**54 passed, no failures/errors/skips**, including zero steady-state allocation.
`TestAcceleratedSmpsPlayback,TestModApiSignatureSurface`: **19 passed, no
failures/errors/skips**. The new ROM class checks eight region/rate combinations,
full note state and final PCM against the normal-speed source, fractional restore
with silent presentation, and rate changes back to 1×. It uses independent
48 kHz no-device presentations and the real S1 ROM loader.

A bounded baseline diagnostic compiled only the pre-task producer and its nested
classes into a separate temporary directory, ahead of current production/test
classes on the classpath. Same checkout, dependencies and absolute S1 ROM path:
the initial four NTSC speed cases plus fractional restore **all failed** against
the old producer and **all passed** against the correction. The speed cases fail
on note state, independently of audible pitch. No baseline engine build tree was
shared or modified.

The change-based plan selects **1,020 of 2,958 ordinary classes** (audio, common,
rewind and tooling) plus guards. Java 21/Lua 5.4/PowerShell preflight passed.
This is partial-suite selection, not a claim of complete engine or native audio
parity coverage. The first category attempt was interrupted while rollback tests
were added; the focused set above was then repeated on the final producer:
**75 passed, no failures/errors/skips**.

Category run `--base 5637105e5c --run` (Maven 3.9.16, Java 21, portable
PowerShell 7.4.6, `LUA_BIN=/usr/bin/lua5.4`): ordinary **9,001 tests / 7
failures / 185 errors / 58 skipped** in 400 seconds; guards **672 tests / 2
failures** in 279 seconds. `TestAudioPresentationArchitectureGuard` still named
the removed `mixSessionForward`. It now guards `mixSessionForwardAtRate`
(session render plus `mixPcmVoices`, no legacy `mixer.mix`) and also requires
the source service inside it. The queued guard class passed **38/38**.

Every other failure is outside this change. The tooling selection added more
S3K ROM consumers: all 185 errors and the remaining ordinary failures except
the inventory are null S3K ROM lookups, a "requires the verified S3K ROM" boss
explosion, or ROM-assumption aborts inside `assertThrows`. The root S3K SHA-1 is
still `b711a909…`, so it was not supplied. `TestRemainingRewindTailInventory`
(1315/1072 vs 1316/1073) and the `LrzFlameObjectInstance` physics-standardization
guard are the baseline-reproduced failures recorded above. Diagnostics were
inspected and acknowledged. The broad selection remains non-green and is not
represented as certification.

## Elevation tiers and wider pits (0.6.0, 2026-10-02)

Main checkout, `feature/ai-infinite-sonic`, base `7e14f78180`. Focused validation of
the mod only, with the S1 REV01 ROM:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=/absolute/path/to/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

**41 passed, 0 failures, 0 skipped.** The engine is unchanged, so no engine
category run was needed. Gameplay rendering of ledges and raised tiers has not
been visually reviewed.

## Every zone before Final Zone (0.8.0, 2026-10-02)

Main checkout, `feature/ai-infinite-sonic`, base `bb62addc50`. Focused validation of
the mod only (engine unchanged), with the S1 REV01 ROM, using the command above:
**61 passed, 0 failures, 0 skipped** (35 s), including 18 per-act course runs.
`build.py` packaged the 0.8.0 jar through `ggfmod` validation.

A temporary test-side CPU render of each act's generated planes (world X 0–2560,
Y 512–1280, with the terrain oracle overlaid) was reviewed for all 18 acts. It
caught MZ lava chosen as ground (fixed, see the design record). Backgrounds were
drawn without parallax, so this is a terrain review, not presentation evidence.
Not covered outside GHZ1: damage, attacks, rewind/replay, backtracking, jump reach,
non-native widths and live GPU presentation.

## Zone badniks, session lives and death menu (0.10.0, 2026-10-03)

Main checkout, `feature/ai-infinite-sonic`, base `cb5fe701ee`. Mod, test and docs only;
the engine is unchanged, so focused validation replaces an engine category run.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Zone line-ups and loaded art | `encountersUseTheZonesOwnBadniksWithLoadedRomArt` | GHZ/MZ/SYZ/LZ/SLZ/SBZ act 1 plus SBZ3: 600 planned sections use only the zone's species and every one appears; each species' ROM art renderer is registered; live spawns carry the planned species |
| Habitat fit per species | `encountersAreSeededSpacedAndFitTheirEntirePatrolCorridor` | GHZ1: ground Y = floor − species depth; flyers clear 48 px by their own depth |
| Indestructible hazards | `walkingBombsHurtInsteadOfBreaking` | SLZ1: a descending rolling Sonic loses rings and the bomb survives |
| Stomp, score, explosion rewind | `aPhysicalStompDestroysTheBadnikAwardsScoreAndDoesNotRespawnIt` | GHZ1 Motobug and Buzz Bomber only |
| Last-life game over | `lastLifeGameOverSkipsStockCardAndJumpRestartsTheCourse` | Held corpse, no stock card, press-space fresh session |
| CONTINUE | `deathMenuContinueResumesScoreSpeedAndSessionLives` | Score, 1.5× (stage 2) and countdown position, session lives; a second death continues with the last life |
| RESTART | `deathMenuRestartBeginsAFreshSession` | Edge-detected cursor, fresh 1× session with 3 lives |
| Score extra lives | `everyFiftyThousandPointsAwardsASessionLife` | Survival and enemy points cross 50,000/100,000; one life per threshold; HUD lives text |
| Left-edge death | `fallingBehindEndsRunDespiteRingsAndInvulnerability` | All five widths; now leaves two session lives and the CONTINUE menu |

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=/absolute/path/to/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

**73 passed, 0 failures, 0 skipped.** Not covered: Orbinaut spike contact, Roller and
Ball Hog interactions, rewind across a CONTINUE reload, and live rendering of the new
species and the death menu (no capture or visual review was made).

## Act line-ups, ring-only lives and in-place CONTINUE (0.11.0, 2026-10-03)

Main checkout, `feature/ai-infinite-sonic`, base `9423b53e23`; mod, test and docs only.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Act-derived line-ups | `encountersUseTheZonesOwnBadniksWithLoadedRomArt` | Six zones' act 1 plus SBZ3; planned species come only from the act's line-up, all appear, art registered |
| Ring-only lives | `livesComeOnlyFromEveryHundredRings` | 0 at start; 120,000 points award nothing; 100/200/300 each award one (no stock duplicate); re-reaching 100 after a loss |
| In-place CONTINUE | `deathMenuContinueRevivesInPlaceAtTheLastSafeSpot` | After 900 frames of course: no respawn request, same controller, exact safe spot and floor Y, blink, one life spent, rings 0, score/1.5×/countdown kept, on screen, standing, scroll resumes; a second continue |
| CONTINUE after a pit | `continueAfterAPitDeathRevivesBeforeThePit` | Real fall into the next pit; revived at the recorded spot before it, standing |
| No-lives game over | `lastLifeGameOverSkipsStockCardAndJumpRestartsTheCourse` | Restart gives a fresh session with 0 spare lives |
| RESTART | `deathMenuRestartBeginsAFreshSession` | Cursor and fresh 0-life session |

**74 passed, 0 failures, 0 skipped** (same focused command). The line-ups for all 18 acts
were printed from the live course with a temporary diagnostic (not kept). Not covered:
continuing after a left-edge death specifically, rewind across a continue, and any live-engine
render of the new behavior.

## Platform stretches, 16:9 and single-level zones (0.12.0, 2026-10-03)

Main checkout, `feature/ai-infinite-sonic`, base `d112667006` (develop merged); mod,
engine hooks, tests and docs.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Stretch geometry | `platformStretchesBridgeTheirPitWithTheActsStockPlatforms` | All 18 acts, 250 stretches each: 320–448 px bottomless pit, flat ≥352/≥160 px banks, ≤1 tier, spans 16–144 px, stones level with the lower bank, no encounters, kinds only from the act's placement; acts without stones keep ordinary corridors |
| Real crossing on stock objects | `sonicCrossesAPlatformStretchOnSpawnedStockPlatforms` | Each act with stones, first stretch, 4:3 and 16:9: every planned stone spawns as the stock object at its planned height, Sonic rides one and lands beyond; skipped for MZ3, LZ1–3, SLZ1, SBZ2–3 |
| Traversal through stretches | `everyZoneActBuildsATraversableDryCourse`, `protectedTraversalPreservesEncountersAcrossRebaseAndReplay` | Input policy over 3000/6000 frames including stretches; rebase deferral and rewind/replay of stock stones exercised indirectly |
| Ordinary corridors | `corridorsAreBoundedSeededAndHaveLevelRunways` | Platform stretches excluded; ring arcs not asserted geometrically |
| Session aspect / level select | `TestEngine.requiredModuleAspectPinsTheSessionUntilOverridesClear`, `TestGameLoop.testTitleScreenExitStartsLevelWhenModuleSuppressesLevelSelect`, `sessionPinsWidescreenAndHidesLevelSelect` | Hook unit coverage; no windowed launch was run |

**129 run, 0 failures, 14 skipped** (same focused command). Not covered: a falling Obj18
stone collapsing under a waiting Sonic, a forced rebase with stones loaded, a dedicated
rewind across a stone ride, and any live render of stones or ring arcs.

## Gentler hits, shield monitors and slower scroll (0.13.0, 2026-10-05)

Main checkout, `feature/ai-infinite-sonic`, base `741b2b34cc`; mod, tests, docs and the
one-line `ModAssetSnapshot` temp-root fix.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| 20-ring toll, no knockback | `ringTollOrShieldAbsorbsAnEnemyHitWithoutKnockback[toll]` | GHZ ground badnik, real running touch: 25 → 5 rings, not hurt, speed kept, blink set, no repeat charge over 4 frames |
| Shield absorbs one hit | `ringTollOrShieldAbsorbsAnEnemyHitWithoutKnockback[shield]` | Shield removed, all 5 rings kept, no knockback |
| Stock hit below 20 rings | `ringTollOrShieldAbsorbsAnEnemyHitWithoutKnockback[few]`, `walkingBombsHurtInsteadOfBreaking` | Knockback and full ring loss unchanged |
| Shield monitor placement | `shieldMonitorsAreSeededOnLevelGround` | GHZ1, 1000 sections: seeded, section ≥ 6, never corridors/stretches, standing on flat floor |
| Shield monitor pickup | `touchingAShieldMonitorBreaksItGivesAShieldAndReplays` | Real non-rolling touch breaks the box and grants the stock shield; break and burst rewind/replay |
| Slower scroll and scoring | `minimumScrollAllowsFasterRunningAndSurvivalScoreScales` | 240 camera px and points per second at 1×, scaling with speed |
| Traversal and rewind with shields | `protectedTraversalPreservesEncountersAcrossRebaseAndReplay`, `everyZoneActBuildsATraversableDryCourse` | Long runs now pick up shield monitors; shield restore across rewind |

Not covered: the toll or shield against Orbinaut spikes, Rollers and flyers specifically
(same listener path), and live play or a visual check of the monitor art.

## Ring spill, top speed and follow point (0.14.0, 2026-10-05)

Main checkout, `feature/ai-infinite-sonic`, base `5220937207`; mod, tests and docs only.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Toll spills 20 stock lost rings, no knockback | `ringTollOrShieldAbsorbsAnEnemyHitWithoutKnockback[toll]` | 20 live `LostRingObjectInstance`s, 5 rings kept after 4 frames, blink on the contact frame |
| Course top speed 0x540 | `normalTraversalReachesFirstSpeedup`, `speedupBoundaryRestoresAndReplaysWithScoreCameraAndHud` | Profile cap through the real physics provider, kept across speedup and rewind |
| Follow point at 30% | `sonicGainsGroundAcrossSpeedupAndIsHeldAtTheFollowPoint`, `minimumScrollAllowsFasterRunningAndSurvivalScoreScales` | Every supported width |
| Jumps still clear pits and stones | `sonicCrossesAPlatformStretchOnSpawnedStockPlatforms`, `everyZoneActBuildsATraversableDryCourse` | All acts with stones at 4:3 and 16:9 |

**134 run, 0 failures, 14 skipped** (`TestInfiniteSonic`, queued Maven, S1 REV01). Not
covered: a rebase deferred by live spilled rings, and live play.


## Monitors, exit and run frames (0.15.0, 2026-10-05)

Main checkout, `feature/ai-infinite-sonic`, base `2a4ca53aa0`; mod, tests and docs only.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Monitor mix and rewards | `monitorsAreSeededOnLevelGroundWithAMixOfKinds`, `touchingAMonitorBreaksItGivesItsRewardAndReplays[4,5,6]` | Shield, Invincibility, Super Ring; break and burst replay |
| EXIT and Escape request the title | `deathMenuExitLeavesForTheTitleScreen`, `deathMenuWithASpareLifeOffersContinueRestartAndExit`, `escapeLeavesTheCourseForTheTitleScreen` | Headless: asserts the `TITLE_SCREEN` exit request and released audio rate; the `GameLoop` fade/title entry is the stock path, not run here |
| Run frames at course speed | `cruisingSonicShowsTheFullSpeedRunFrames` | GHZ flat ground: run mapping frames below 0x600 |

**140 run, 0 failures, 14 skipped** (`TestInfiniteSonic`, queued Maven, S1 REV01; the
skips are the platform crossing in acts without platforms). Not covered: a visual check of
the monitor icons, the live title return, and live play.

## High routes and zone hazards (0.16.0, 2026-10-05)

Main checkout, `feature/ai-infinite-sonic`, base `e974573148`; mod, tests and docs only.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Route geometry | `highRoutesHangTheActsStockPlatformsAboveLevelGround` | All 18 acts, 1000 sections: stationary stock kinds the act places, one level ledge 48 px over the highest floor and at most 64 px over any, 24 px gaps, mostly ground-guarded, no hazard |
| Route reachable and runnable | `sonicJumpsOntoAHighRouteAndRunsAcrossIt` | GHZ, MZ, SYZ, SLZ, SBZ act 1 at 16:9: one held jump 140 px early lands on the ledge and runs to its last platform without dropping |
| Hazard placement | `hazardsAreTheZonesOwnOnLevelGroundOrInPits` | All 18 acts (SBZ3 follows its LZ layout): zone's own kinds, fireballs only in MZ/SLZ pits, no badnik/monitor/route sharing |
| Hazards need timing; hits use the ring toll | `eachZoneHazardNeedsTimingAndHitsThroughTheRingToll` | First signature hazard of each zone's act 1, 42 coast/jump timings from one snapshot: at least one passes, at least one is hit (20-ring toll, no knockback), motion replays |
| SLZ elevator platforms | `platformStretchesBridge...`, `sonicCrossesAPlatformStretch...` | SLZ1 now crosses on Obj59 at 4:3 and 16:9 |

**187 run, 0 failures, 12 skipped** (`TestInfiniteSonic`, queued Maven, S1 REV01; the skips
are the platform crossing in acts without platforms). Not covered: spike beds' timing trial
(geometry and toll path are shared), a visual check of every hazard sprite, and live play.

## Split paths, hazard frequency, no Invincibility (0.17.0, 2026-10-05)

Main checkout, `feature/ai-infinite-sonic`, base `5bceccc8bc`; mod, tests and docs only.

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| High road geometry | `highRoadsClimbOnStockPlatformsToASeparatePathAboveTheGround` | All 18 acts, 250 stretches: stationary kinds the act places, climbs of at most 48 px with 120 px step gaps, a level road 128 px over the highest floor beneath with 8 px gaps, ends before the corridor, at most 8 stones per section, no hazards and mostly ground patrols beneath |
| Climb and run the road | `sonicClimbsOntoAHighRoadAndRunsItsLength` | GHZ, MZ, SYZ, SLZ, SBZ act 1 at 16:9: a policy that only chooses jump timing and air braking reaches the road and runs it to its end without dropping |
| Hazard frequency | `hazardsAreTheZonesOwnOnLevelGroundOrInPits` | Per 1000 act-1 sections: GHZ 160 wrecking balls (was 54), SYZ 146 balls, LZ 187 chains, SBZ 127 flames, MZ/SLZ 60 pit fireballs; spikes fill in elsewhere |
| Monitors: shields and Super Rings only | `monitorsAreSeededOnLevelGroundWithAMixOfKinds`, `touchingAMonitorBreaksItGivesItsRewardAndReplays[4,6]` | |

**186 run, 0 failures, 12 skipped** (`TestInfiniteSonic`, queued Maven, S1 REV01). Not covered:
live play and a visual check of the road and hazards.
