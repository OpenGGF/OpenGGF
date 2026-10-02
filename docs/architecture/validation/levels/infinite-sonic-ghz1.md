# Infinite Sonic — GHZ1 solo Sonic prototype coverage

This mod replaces stock GHZ1 (registry zone 0, act 0, level ID `0x80`) only when
its explicit patch activates for solo Sonic. It is an endless terrain course, not a
new stock act. [Design and decisions](../../designs/2026-10-01-infinite-sonic.md).

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Real code package | `TestInfiniteSonic.compileAndValidate` compiles project sources and calls SDK `package` validation | No ROM payloads or baked assets shipped |
| Entry and traversal | `protectedTraversalPreservesEncountersAcrossRebaseAndReplay` uses explicit test-only invulnerability, 1× clock resets and camera recentering to isolate terrain; runs Right with gap-aware Jump input for 6,000 frames, asserts no death and at least four world rebases | All five current `WidescreenAspect` presets: 320, 352, 400, 528, 800; resolved camera width asserted |
| Rewind at world recycling | Same test captures the full registry, runs 800 frames across recycling, restores and replays | Compares X/Y, fractional X, ground speed entire map, enemy state and occupied object slots; native donor/off; does not test GameLoop's interactive history recorder |
| Backtracking | Same terrain-only setup runs Left with gap-aware Jump input for 1,800 frames and proves reverse rebasing; normal challenge backtracking can be fatal | Same five widths |
| Fresh reload / act isolation | `freshReloadResetsTheCourseAndOtherActsRemainStock` loads GHZ2, checks stock placements, reloads GHZ1 and checks original generated layout | 320px; physical death/respawn and live-history reset not yet covered |
| Character/team scope | `patchOnlyActivatesForSoloSonic` checks solo Sonic, solo Tails, Sonic+Tails and wrong-game selection | Other teams/characters use stock GHZ, by explicit activation policy |
| Install/registration | Local production scanner, state/trust handling and restricted classloader registration check | IntelliJ default project-root working directory; enabled local jar |
| Art and presentation | Reuses ROM pipeline; mirrored chunks and stock GHZ background | Native-width opening/HUD rendered in 0.4; background seam continuity at recycling and other-width GPU review remain open |
| Encounter habitats | `encountersAreSeededSpacedAndFitTheirEntirePatrolCorridor` checks 997 sections, both kinds, rest gaps and floor profiles against decoded collision | Fixed seed; ground relief and flying clearance checked across full patrol |
| Jump requirement | `holdingRightCannotCompleteTheCourse` exercises ordinary unassisted Right input after the safe opening | All five widths; normal damage, bounded live slots; automatic respawn remains open |
| Attacks and explosions | `aPhysicalStompDestroysTheBadnikAwardsScoreAndDoesNotRespawnIt` exercises ground and air enemies | Real touch response from a positioned descending spin; score, no immediate respawn and explosion rewind/replay |
| Ring collection | `ringRowsCollectThroughGameplayAndRestoreWithTheCourse` exercises real touch collection, ring count and restore/replay | Opening row, no repeated award after sparkle; GPU review remains open |
| Gap geometry | `gapsAreBoundedSeededAndHaveLevelRunways` checks 1,000 sections; encounter habitat test compares actual decoded collision with the terrain oracle | 64/96/128px widths, no enemies in jump corridors, flat banks |
| Jump reach | `realPhysicsCanClearEachGapInBothDirections` uses actual Sonic movement and landing | All three widths, both directions, 3px/frame initial speed with held direction and Jump |
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
| 30-second compounding | `speedAndCountdownUseThirtySecondCompoundingIntervals` | Exact first two boundaries, 2700 simulation ticks for the second interval, PAL and ceiling |
| Scroll and score | `minimumScrollAllowsFasterRunningAndSurvivalScoreScales` | 1×/1.5×/2.25×; actual camera and score over 60 paced frames; positioned above terrain to isolate timing |
| Left-edge game over | `fallingBehindEndsRunDespiteRingsAndInvulnerability` | All five widths; partial visibility survives, complete exit kills, lives exhausted, score/clock/camera freeze |
| Boundary rewind | `speedupBoundaryRestoresAndReplaysWithScoreCameraAndHud` | Positioned 1×→1.5× boundary; clock adapter, controller, score, camera and HUD text |
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

`sonicGainsGroundAcrossSpeedupAndIsHeldAtRightMargin` checks all five viewport
widths through 600 presentation frames using the actual module step budget across
1×→1.5×. It isolates horizontal integration from terrain by resetting height and
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
