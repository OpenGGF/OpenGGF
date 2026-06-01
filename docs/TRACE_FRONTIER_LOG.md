# Trace Frontier Log

## 2026-06-01 - LBZ1 ground-launch intro trace-neutral check

- Scope: S3K Launch Base Zone Act 1 startup now uses the ROM buried start
  position plus `Obj_LevelIntro_PlayerLaunchFromGround` timing: the controller
  waits for title-card handoff, holds player control for 30 live gameplay
  frames, applies `y_vel=-$0B00`, selects Sonic animation `$10`, and releases
  once `y_pos<$05C0`.
- Full trace replay comparison used a targeted stash baseline on current
  `develop`: with the LBZ changes stashed, then with the changes restored.
  Both serialized runs produced the same aggregate result:
  - `mvn -Dmse=off -Dsurefire.forkCount=1 '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
  - Result in both states: FAIL, 63 tests run, 23 failures, 0 errors, 0
    skipped.
- Current S3K frontier frames remain unchanged from the committed 2026-05-29
  entry: AIZ frame 8941 (`camera_y` mismatch), CNZ frame 17276 (`x_speed`
  mismatch), and MGZ frame 4124 (`y_speed` mismatch).
- Note: the older 2026-05-28 full-sweep count of 21 failures was not the
  current `develop` baseline for this check; the stashed pre-change baseline
  already reported 23 failures.

## 2026-05-29 - Failing-test cleanup verified trace-neutral

- Scope: fixed 9 failing non-trace test classes on committed `develop`
  (HEAD `76766c85c`): the `ScriptedVelocityAnimationProfile` airborne
  slide+roll regression from `683b9c150`; the rewind/object/arch guard
  failures from the recent CNZ/ICZ S3K bring-up
  (`DefaultObjectRewindPolicies` central entries for CNZ/ICZ miniboss
  explosion controllers, `TestRewindArchitectureGuard` transient baseline,
  3 object-physics-standardization refactors, `SidekickCpuController` off
  `GameServices`, ArchUnit freeze-store extension for the established
  ObjectManager rewind-recreation + LiveRewindManager audio patterns); and
  two stale CNZ-miniboss + one AIZ2 sidekick-bounds test that encoded
  pre-fix expectations.
- Baseline check: stashed all changes and ran the S3K frontier traces on
  pristine HEAD vs. with-changes. First-error frames are **identical** in
  both states, so the cleanup is trace-neutral:
  - `TestS3kAizTraceReplay`: frame 8941, `camera_y` mismatch
    (expected=0x02C1 actual=0x02B8), 243 errors / 23 warnings.
  - `TestS3kCnzTraceReplay`: frame 17276, `x_speed` mismatch
    (expected=0x0000 actual=0x000C), 1996 errors / 17 warnings.
  - `TestS3kMgzTraceReplay`: frame 4124, `y_speed` mismatch
    (expected=0x0000 actual=0x01AC), 3852 errors / 64 warnings.
  - Command:
    `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test`
- Note: committed HEAD's S3K frontiers are AIZ f8941 and CNZ f17276, not the
  AIZ f19714 / CNZ f22036 recorded in the 2026-05-28/05-26 entries below —
  those numbers came from the *local uncommitted* ICZ2/AIZ-hollow-tree-latch
  work described in the 2026-05-28 entry, which is not in HEAD. MGZ f4124 is
  unchanged.
- Known-green guard (all PASS):
  `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Mz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2SczLevelSelectTraceReplay#replayMatchesTrace" test`
- Full `mvn clean test` (default suite, no trace replay): PASS,
  5513 passed / 0 failed / 0 errors / 6 skipped.

## 2026-05-28 - ICZ2 stale object grounding regression guard

- Scope: local uncommitted ICZ2 stale `Status_OnObj` cleanup plus AIZ Hollow
  Tree support-latch preservation. The first implementation broadly cleared
  unsupported object grounding and regressed AIZ to frame 4540; latching the
  AIZ hollow tree as the live support owner restored AIZ to its documented
  frontier.
- Full trace sweep:
  `mvn -Dmse=off "-Dtest=*TraceReplay" test`
  - Result: FAIL, 63 tests run, 21 failures, 0 errors, 0 skipped.
  - S3K frontiers stayed at the documented frames: AIZ frame 19714
    (`x_speed expected=0x0014 actual=0x0000`), CNZ frame 22036
    (`y_speed expected=-06C8 actual=-0700`), MGZ frame 4124
    (`y_speed expected=0x0000 actual=0x01AC`).
- Focused AIZ replay:
  `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: still fails at frame 19714 with 30 errors / 23 warnings, matching
    the current logged frontier.
- Known-green trace guard:
  `mvn -Dmse=off "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Mz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2SczLevelSelectTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - PASS: 4 tests, 0 failures.
- Focused ICZ/object-support guard:
  `mvn -Dmse=off "-Dtest=com.openggf.tests.TestS3kIcz2StaleObjectGrounding,com.openggf.physics.TestCollisionSystemAirLanding,com.openggf.tests.TestS3kIczMinibossObject,com.openggf.level.objects.TestSolidObjectManager" test`
  - PASS: 65 tests, 0 failures.

## 2026-05-26 - Restore S2 non-Tornado title-card object prelude (EHZ1 regression fix)

- Bisected a `TestS2Ehz1TraceReplay` regression that appeared at the
  `feature/ai-object-physics-standardization` PR merge to the bad commit
  `d476dd132 Resolve PR merge-readiness failures` (2026-05-23). That commit
  neutered `TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay`
  and `levelObjectTitleCardPreludeFramesForTraceReplay` to unconditional
  `return 0`. `96c71496f Tighten trace replay diagnostics and bootstraps`
  partially restored the sidekick method via a tighter resolver
  (`resolveS2SidekickTitleCardPreludeFrames` requires
  `gameplayFrameCounter() == 1` at the first frame, which doesn't apply to
  most S2 native-prelude traces), but the level-object prelude was left
  dormant. Result: every S2 v9.2-s2 non-Tornado trace started with objects
  out of step with what ROM had at the BK2 cursor, and divergences
  accumulated.
- Fix routes the generic S2 object prelude through `TraceReplaySessionBootstrap`
  for all non-Tornado S2 native-prelude routes, matching ROM `Level_MainLoop`
  ticks during the title card (`docs/s2disasm/s2.asm:5004-5092`). Tornado
  routes are unchanged — they still go through `s2TornadoObjectPreludeFrames`
  plus `applyS2TornadoTitleCardScrollPrelude` / `applyS2TornadoRideStart`.
- New public method `TraceReplayBootstrap.s2GenericObjectTitleCardPreludeFramesForTraceReplay`
  returns the generic prelude count (26 frames) for S2 native-prelude traces
  that are not Tornado-route. `TraceReplaySessionBootstrap.applyBootstrap`
  uses it as a fallback when `s2TornadoObjectPreludeFrames` returns 0.
- Trace results after fix (full `*TraceReplay` sweep with `-Dmse=off`):
  - `TestS2Ehz1TraceReplay`: PASS (was failing at frame 153, y_speed mismatch
    expected=-0290 actual=-0390).
  - `TestS1Ghz1TraceReplay`, `TestS1Mz1TraceReplay`: still PASS.
  - `TestPreludeFramesKnobsZero`, `TestTraceReplayStartPositionPolicy`,
    `TestSonic2TornadoRidePrelude`: still PASS.
  - Pre-existing S2 frontiers unchanged: ARZ f1106 (581 errors), CPZ f844,
    CNZ f3906, MCZ1 f1085, HTZ f5511. Other failing S2 level-select traces
    (ARZ2 f225, CNZ2 f1490, DEZ f536, CPZ2 f1515, HTZ2 f795, MCZ2 f264,
    MTZ f375, MTZ2 f305) are pre-existing frontiers — they failed at the
    same first-error frames before and after the fix.
  - Pre-existing S3K frontiers unchanged: AIZ f19714, CNZ f22036.

## 2026-05-26 - Trace cleanup audit: no zone carve-outs for new sidekick fixes

- Policy update:
  - Added the no zone/route/frame carve-out rule to `AGENTS.md`, `CLAUDE.md`,
    `.agents/skills/trace-replay-bug-fixing/SKILL.md`, and
    `.claude/skills/trace-replay-bug-fixing/skill.md`.
  - Explicitly called out that "ROM-default behaviour except in AIZ" is still a
    zone-specific carve-out and is not acceptable.
- Sidekick cleanup:
  - Removed the trace-replay wording from the sidekick CPU ROM-state test helper
    and made `hydrateFromRomCpuState` package-private.
  - Verified no `replay_sidekicks`, `replaySidekicks`, or removed normal
    frame-counter bridge APIs remain in source/tests/docs.
- Trace bootstrap cleanup:
  - Removed S2 Tornado replay gates keyed to `metadata.zone()` and now discover
    the live ObjB2 ride-start shape through object routine/subtype predicates.
  - Removed the S2 SlotMachine title-card prelude gate keyed to
    `rom_zone_id == 0x0C`; it now depends on the recorder-advertised
    `cnz_slot_machine_state_per_frame` capability.
  - Restored S2 native-prelude sidekick title-card timing as a game-level
    execution rule derived from `Sonic_Pos_Record_Index == 0x68` (26
    `Sonic_RecordPos` calls). This removed the temporary CNZ frame-0
    sidekick-speed regression without adding a zone carve-out.
- Focused non-trace guards:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.TestTraceDataParsing,com.openggf.trace.TestPreludeFramesKnobsZero,com.openggf.testmode.TestModeTracePickerTest" test "-DfailIfNoTests=false"`
  - PASS: 52 tests, 0 failures.
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuControllerHydrate,com.openggf.sprites.playable.TestSidekickCpuControllerCarry,com.openggf.sprites.playable.TestSidekickCpuFollowParity,com.openggf.tests.TestTraceReplayInvariantGuard" test "-DfailIfNoTests=false"`
  - PASS: 55 tests, 0 failures.
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.TestTraceReplayStartPositionPolicy,com.openggf.tests.trace.TestTraceDataParsing,com.openggf.tests.trace.TestSonic2TornadoRidePrelude" test "-DfailIfNoTests=false"`
  - PASS: 51 tests, 0 failures.
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.trace.TestPreludeFramesKnobsZero,com.openggf.tests.trace.TestTraceReplayStartPositionPolicy" test "-DfailIfNoTests=false"`
  - PASS: 19 tests, 0 failures.
- Full-suite guard:
  `mvn "-Dmse=off" test`
  - PASS: 5359 tests, 0 failures, 0 errors, 6 skipped.
  - `git diff --check` reported no whitespace errors.
- S2 trace regression guard:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2SczLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2WfzLevelSelectTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - CNZ: restored to the logged frame 3906 frontier,
    `tails_y expected=0x06C0 actual=0x06C1`.
  - SCZ: PASS.
  - WFZ: still fails at the logged frame 4719,
    `x_speed expected=0x08D6 actual=0x08E2`.
  - Count: CNZ 94 errors / 0 warnings; WFZ 589 errors / 0 warnings.
- S3K trace regression guard:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - AIZ: still fails at frame 19714,
    `x_speed expected=0x0014 actual=0x0000`.
  - CNZ: still fails at frame 22036,
    `y_speed expected=-06C8 actual=-0700`.
  - MGZ: still fails at frame 4124,
    `y_speed expected=0x0000 actual=0x01AC`.
  - No S3K trace regression was observed in these gates.

## 2026-05-25 - S3K frontier audit after review cleanup (no trace regressions)

- Scope: staging-prep review after removing temporary debug output, replacing
  trace-row-derived sidekick replay bootstrap with explicit metadata, and
  narrowing S3K recorder schema advertising.
- CNZ replay:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: still fails at frame 22036
    `y_speed expected=-06C8 actual=-0700`.
  - Count: 1460 errors / 17 warnings.
- AIZ replay:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: still fails at frame 19714
    `x_speed expected=0x0014 actual=0x0000`.
  - Count: 30 errors / 23 warnings.
  - This supersedes older detailed AIZ notes that stopped at frame 19669; the
    current authoritative AIZ frontier is frame 19714.
- MGZ replay:
  `mvn "-Dmse=off" "-Dsurefire.forkCount=1" "-Dtest=com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: still fails at frame 4124
    `y_speed expected=0x0000 actual=0x01AC`.
  - Count: 3852 errors / 64 warnings.
  - This supersedes older MGZ notes that mention intermediate frames 2458 or
    3777; the current authoritative MGZ frontier is frame 4124.

No S3K trace regression was observed in the current AIZ/CNZ/MGZ gates. All
three fail at the same top frontier as the pre-cleanup review runs.

## 2026-05-25 - Touch-response single-region profile dispatch restores CNZ spark frontier (CNZ f21772 -> f22036)

- Regression triage:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  initially stopped again at frame 15299
  `tails_y expected=0x02EC actual=0x02ED`.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#openGoSpawnsRomCoilSparkChildren+openCoilSparkAppliesSidekickHurtKnockback" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossTailsPushBypassUsesHurtLatchedLeaderInput" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Bootstrap policy guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.TestTraceReplayStartPositionPolicy,com.openggf.trace.TestPreludeFramesKnobsZero" test "-DfailIfNoTests=false"`
  - PASS: 18 tests, 0 failures.
- CNZ replay after fix:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from the logged frame 21772
    `y_speed expected=-0700 actual=-0220` to frame 22036
    `y_speed expected=-06C8 actual=-0700`.
  - New count: 1460 errors / 17 warnings.
- AIZ regression guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: still fails at the known frame 19714
    `x_speed expected=0x0014 actual=0x0000`; the old f8941 AIZ regression did
    not return.

### Root cause

The f15299 regression was caused by `ObjectManager.TouchResponses` always
calling `TouchResponseProvider#getTouchResponseProfile(boolean)`, even for
single-region objects. Objects such as `CnzMinibossSparkInstance` override the
no-arg profile to opt out of the generic render-visibility touch gate because
ROM `Child_DrawTouch_Sprite` adds those spark children directly to S3K's
collision-response list (`docs/skdisasm/sonic3k.asm:178048-178053,21200-21209`).
Bypassing that no-arg override rebuilt a default profile with
`requiresRenderFlagForTouch=true`, so the trace saw the sparks present but
`gate=offscreenTouch`, and Tails stayed on the pre-hurt trajectory.

The manager now asks real multi-region providers for the boolean profile, but
uses the no-arg profile for single-region objects. This restores custom
single-region profiles without adding a CNZ-specific engine hack.

### New CNZ frontier (frame 22036)

`y_speed mismatch (expected=-06C8, actual=-0700)`. Sonic, Tails, camera, and
positions match at the first error; Sonic's balloon launch vertical speed is
one gravity step too strong after the CNZ2 balloon cluster around
`@1308,05E8`.

## 2026-05-24 - S3K CNZ rising-platform width_pixels clears Tails history drift (CNZ f21199 -> f21772)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused rising-platform guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzRisingPlatformInstance" test "-DfailIfNoTests=false"`
  - PASS: 6 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 21199
    `tails_x expected=0x1166 actual=0x1165` to frame 21772
    `y_speed expected=-0700 actual=-0220`.
  - New count: 1440 errors / 17 warnings.

### Root cause

Frame 21199 was downstream of Sonic's recorded status history, not a Tails-local
position write. Tails CPU consumed a delayed leader status byte whose facing bit
differed from ROM, so it missed the ROM `leader_on_object` one-pixel nudge. The
first upstream owner was frame 21107: engine Sonic falsely entered Balance4 and
flipped left while standing still on a CNZ rising platform, and that full status
byte was later recorded for Tails by `Sonic_RecordPos`
(`docs/skdisasm/sonic3k.asm:22119-22133`).

ROM `Obj_CNZRisingPlatform` initializes `width_pixels=$30`
(`docs/skdisasm/sonic3k.asm:67126-67136`), and `Sonic_Move` reads
`width_pixels(a1)` from the ridden object for object-edge balance
(`docs/skdisasm/sonic3k.asm:22462-22473`). The engine had the solid width at
`$30` but left the rendered/balance width at the default `$10`, making the
f21107 geometry look beyond the left edge. `CnzRisingPlatformInstance` now
exposes `$30` as its on-screen/balance half-width.

### New CNZ frontier (frame 21772)

`y_speed mismatch (expected=-0700, actual=-0220)`. The f21199 Tails X drift is
cleared. The new first error is main-player vertical launch timing in CNZ2 near
a balloon/cannon cluster around `$1080,$0700`: ROM has already applied the
strong upward balloon velocity, while the engine is still on the weaker
pre-launch trajectory and touches the nearby balloon several frames later.

## 2026-05-24 - S3K Render_Sprites exclusive solid gate clears CNZ spike push (CNZ f21147 -> f21199)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused spike guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kSpikeObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 3 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 21147
    `tails_x_speed expected=-0006 actual=0x0000` to frame 21199
    `tails_x expected=0x1166 actual=0x1165`.
  - New count: 1447 errors / 17 warnings.

### Root cause

Frame 21147 was a stale sidekick `Status_Push` carryover from a CNZ2 spike.
ROM `Obj_Spikes` runs `SolidObjectFull` before its `Sprite_OnScreen_Test2`
tail call (`docs/skdisasm/sonic3k.asm:49011-49039`), so the solid helper reads
the `render_flags` bit 7 set by the previous `Render_Sprites` pass. That
renderer rejects exact lower/right edges with `bhs`/`bge`
(`docs/skdisasm/sonic3k.asm:36347-36365`). At CNZ f21146 the spike at
`@1170,08B0` is exactly at `camera_y + 224 + height_pixels`, so ROM has
already cleared bit 7; `SolidObjectFull_1P` branches through the offscreen
clear path instead of re-setting Tails' push bit
(`docs/skdisasm/sonic3k.asm:41016-41018,41390-41394,41528-41532`).

The engine's solid-contact bounds used inclusive upper edges, keeping the spike
solid for one extra frame and making Tails CPU take `current_push_bypass` at
frame 21147. `CameraBounds` now exposes a ROM `Render_Sprites` bounds helper
with exclusive upper edges, and `AbstractObjectInstance.isWithinSolidContactBounds`
uses it only for SolidObjectFull's render-flag gate.

### New CNZ frontier (frame 21199)

`tails_x mismatch (expected=0x1166, actual=0x1165)`. Player/camera and Tails
velocities match at the first error. Tails is in `leader_on_object` near the
CNZRisingPlatform/spike cluster; the remaining one-pixel X drift appears to be
position/order state after the now-correct push release, not a current
horizontal velocity mismatch.

## 2026-05-24 - S3K CNZ cylinder jump-release uses Ctrl logical press bits (CNZ f19296 -> f21147)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 28 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 19296
    `tails_air expected=0 actual=1` to frame 21147
    `tails_x_speed expected=-0006 actual=0x0000`.
  - New count: 1558 errors / 17 warnings.

### Root cause

Frame 19296 was a CNZCylinder P2 held-rider release bug. ROM
`Obj_CNZCylinder` passes `Ctrl_1_logical` / `Ctrl_2_logical` in `d5` to
`sub_324C0`, and `loc_325B6` tests only the low-byte A/B/C press bits before
taking the jump-release branch (`docs/skdisasm/sonic3k.asm:67656-67672,
68059-68064`). The trace shows ROM `ctrl2=0000/00`, so Tails stays held with
`object_control=$03`; the engine used the live held/forced jump latch and took
`p2=release_jump` anyway.

`CnzCylinderInstance` now tests the playable's published ROM-visible logical
jump press bit for this one object path. A focused guard covers the f19296
shape: CPU Tails has live jump held but `Ctrl_2_logical` has no jump press, so
the cylinder keeps Tails grounded/object-controlled instead of releasing.

### New CNZ frontier (frame 21147)

`tails_x_speed mismatch (expected=-0006, actual=0x0000)`. Player/camera and
Tails position match at the first error. ROM Tails has current CPU input
`0404` and post-CPU speed `FFFA`, while the engine is still in
`current_push_bypass` for Tails and leaves x/ground speed at zero near a
CNZRisingPlatform/spikes cluster in CNZ2.

## 2026-05-24 - S3K CNZ stale offscreen sidekick ride slot despawn (CNZ f18917 -> f19296)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Removed the unproven CNZCylinder offscreen P2 interact-latch patch and its guard; it did not move the trace and was not needed for the bounded fix below.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity#s3kOffscreenUnloadedRideSlotDespawnsEvenWhenInstanceWasNotDestroyed,com.openggf.game.sonic3k.objects.TestCnzBalloonInstance#underwaterNegativeSubtypeLaunchesWithoutPlayerPositionSnap,com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 29 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 18917
    `tails_y_speed expected=0x0000 actual=0x0010` to frame 19296
    `tails_air expected=0 actual=1`.
  - New count: 1568 errors / 16 warnings.

### Root cause

Frame 18917 was S3K Tails CPU's stale offscreen interact-slot path. ROM
`sub_13EFC` checks offscreen Tails, sees `Status_OnObj`, reads the cached
interact slot, and compares the cached interact word with the first word at
that slot. Once the CNZCylinder slot has been freed, the first word is zero, so
ROM branches through `sub_13ECA` and writes the `$81` catch-up marker with
zeroed marker position (`docs/skdisasm/sonic3k.asm:26816-26833,26799-26809`;
freed slot via `Delete_Referenced_Sprite` at `docs/skdisasm/sonic3k.asm:36116-36124`).

The engine already had a S3K feature flag for destroyed latched ride instances,
but counter-window unload removes the instance from `ObjectManager` without
necessarily setting the destroyed flag. `SidekickCpuController` now treats a
latched ride instance that is no longer active as the same freed-slot condition,
while remaining behind the existing sidekick despawn feature gates.

### New CNZ frontier (frame 19296)

`tails_air mismatch (expected=0, actual=1)`. Player/camera and Tails position
match at the first error. ROM has Tails on CNZCylinder slot 29 at
`@17EA,0B3C` with `object_control=$03`/standing state; the engine has Tails
airborne after a `release_jump` path from the matching nearby cylinder.

## 2026-05-24 - S3K CNZ P2 horizontal cylinder side-contact frame-entry anchor (CNZ f18259 -> f18735)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance#horizontalOscillatorCpuSidekickNewSideContactUsesFrameEntryXAnchor" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 18259
    `tails_x expected=0x1440 actual=0x143F` to frame 18735
    `g_speed expected=0x01E6 actual=0x04BA`.
  - New count: 1196 errors / 17 warnings.

### Root cause

Frame 18259 was a split object/solid checkpoint anchor mismatch on a free CPU
sidekick side contact with horizontal CNZCylinder subtype `$42`. Sidekick CPU
and physics produced the expected pre-object value, then the cylinder's inline
solid checkpoint wrote Tails from `143B.E000` to `143F.E000`. ROM context had
the cylinder at `@1415,0AE0`; engine current position was `@1414,0AE0` with
`pre=@1415,0AE0`.

ROM `Obj_CNZCylinder` runs `sub_321E2`, P1 `sub_324C0`, P2 `sub_324C0`, then
passes the live object-pass `x_pos(a0)` in `d4` to `SolidObjectFull`
(`docs/skdisasm/sonic3k.asm:67656-67672,41006-41010`). `SolidObject_cont` uses
that `d4` anchor for side classification/separation
(`docs/skdisasm/sonic3k.asm:41394-41407,41488-41495`). In the engine split, the
current horizontal oscillator can be one step ahead by the deferred solid
checkpoint, so free P2 side contact must use the frame-entry cylinder X anchor.
`CnzCylinderInstance` now applies that object-local anchor for CPU sidekick
horizontal new side contact; no generic solid/object-manager behavior changed.

The replay context also keeps a clean sidekick object-mutation diagnostic
(`eng-tails-objmut`) so future sidekick post-physics overwrites can be compared
without stack-print noise.

### New CNZ frontier (frame 18735)

`g_speed mismatch (expected=0x01E6, actual=0x04BA)`. Tails is aligned at the new
first error, including position and velocities. Player position, camera,
`x_speed`, and `y_speed` also match at the first error, but engine ground speed
is stale/high while ROM has `g_speed=$01E6` near the CNZ2 cylinder /
invisible-hurt-block cluster around `$1449,$0AE0` and `$1428,$0B64`.

## 2026-05-24 - S3K CNZ cylinder standing-bit recapture has no cooldown (CNZ f18155 -> f18259)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 26 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 18155
    `x_speed expected=0x0000 actual=-03FA` to frame 18259
    `tails_x expected=0x1440 actual=0x143F`.
  - New count: 1269 errors / 17 warnings.

### Root cause

Frame 18155 was an engine-only CNZCylinder recapture cooldown. Sonic had landed
on the subtype `$42` cylinder at `$147E,$0AE0` on the previous frame, and the
standing bit was set while the player had just left object-control state. ROM
`Obj_CNZCylinder` runs P1/P2 `sub_324C0` before `SolidObjectFull`
(`docs/skdisasm/sonic3k.asm:67656-67672`). In the inactive rider path,
`sub_324C0` consumes the standing bit immediately, stores the rider distance,
writes `x_vel=0`, `y_vel=0`, `ground_vel=0`, and sets `object_control=$03`
with no recent-release cooldown (`docs/skdisasm/sonic3k.asm:67985-68005`).
The engine skipped this capture because `wasRecentlyObjectControlled(...)`
returned true, so generic movement friction left `x_speed=$FC06` instead of
the ROM zero.

`CnzCylinderInstance` now lets both on-screen and offscreen standing-bit
contacts enter the ROM capture path immediately. This stays object-local; no
generic solid-contact or movement behavior changed.

### New CNZ frontier (frame 18259)

`tails_x mismatch (expected=0x1440, actual=0x143F)`. Sonic and camera are
aligned through the previous cylinder-speed failure. The new owner is
Tails-only one pixel of X drift near the CNZ2 cylinder / triangle-bumper /
invisible-block cluster, with ROM reporting a `tailsInteract` object at
`@1580,0AD8` while the engine has no matching nearby touch at the first error.

## 2026-05-24 - S3K CNZ barber-pole stale rider instance guard (CNZ f17806 -> f18155)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused barber-pole guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzBarberPoleObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 5 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 17806
    `x expected=0x0ECB actual=0x0ECA` to frame 18155
    `x_speed expected=0x0000 actual=-03FA`.
  - New count: 1620 errors / 17 warnings.

### Root cause

Frame 17806 was a stale CNZ barber-pole rider overwrite. Trace diagnostics showed
the current pole at `@0EF0,0A10` latched Sonic and computed the ROM-correct
position `0ECB,09B7`, but an older pole at `@0E70,0990` still had a latched
engine rider state and ran later in the object pass, overwriting Sonic to
`0ECA,09AD`. ROM `loc_33376` only dispatches the continued ride path
`loc_334B6` when the current object's player standing bit is set, and
`sub_337D8` clears the previous object's standing bit before writing the new
`interact(a1)` pointer (`docs/skdisasm/sonic3k.asm:69348-69357,69461,
69775-69782`).

`CnzBarberPoleObjectInstance` now requires the sprite's latched object instance
to be this pole before executing continued ride. Stale per-instance rider state
is dropped without changing the player's current latch, matching the ROM's
single standing-bit/interact owner. The object also keeps compact
`traceDebugDetails()` output for future CNZ barber-pole comparisons.

### New CNZ frontier (frame 18155)

`x_speed mismatch (expected=0x0000, actual=-03FA)`. The f17806 player position
and camera mismatch are cleared. The new owner is later in CNZ2 near a
CNZCylinder / triangle-bumper / invisible-block cluster: ROM has Sonic stopped
and riding/on object, while the engine still carries negative ground/x speed.

## 2026-05-24 - S3K CNZ post-transition camera bounds and Act 2 size bridge (CNZ f17322 -> f17806)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused CNZ event guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzAct1EventFlow#cnzPostTransitionStartsRomAct2LevelSizeGradualAfterTitleCardHandoff+secondEventsFg5AtTransitionStageRequestsSeamlessActSwap+cnzDoTransitionAppliesRomCoordinateRemapImmediately" test "-DfailIfNoTests=false"`
  - PASS: 3 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 17322
    `camera_x expected=0x0260 actual=0x0261` to frame 17806
    `x expected=0x0ECB actual=0x0ECA`.
  - New count: 1626 errors / 17 warnings.

### Root cause

Frame 17322 was a CNZ1-to-CNZ2 transition camera-bound mismatch. The engine
offset the camera position during `CNZ1BGE_DoTransition`, but it let the Act 2
level load restore normal Act 2 camera bounds immediately. ROM offsets the live
camera position and the live min/max bounds by the transition deltas after
`Load_Level` (`docs/skdisasm/sonic3k.asm:107626-107646`), so CNZ now passes
post-transition min/max X/Y and target max Y overrides through the seamless
transition request.

That exposed the next camera-bound phase at frame 17423: ROM begins expanding
the Act 2 horizontal bound after the surviving end-sign-control chain calls
`Change_Act2Sizes`, but the engine reload removes that object chain. CNZ now
mirrors the delayed `Change_Act2Sizes` / gradual level-size children locally
using the ROM gradual accumulator cadence
(`docs/skdisasm/sonic3k.asm:180415-180419,180575-180632,178154-178168,
178192-178224,197460-197468`).

### New CNZ frontier (frame 17806)

`x mismatch (expected=0x0ECB, actual=0x0ECA)`. The transition remap, control
release, and post-transition camera clamp/gradual bound timing now match through
the prior failures. The new owner is later CNZ2 player/object interaction near
the barber-pole/bumper cluster; camera differences at the new window are
downstream of the one-pixel player position divergence.

## 2026-05-24 - S3K CNZ post-transition control and logical-input handoff (CNZ f17278 -> f17322)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused control-handoff guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestS3kSignpostInstance#mainEndingPoseDoesNotLockCtrl1LogicalInputHistory,com.openggf.tests.TestS3kCnzAct1EventFlow#cnzPostTransitionResultsHandoffRestoresPlayerControlAfterRomDelay,com.openggf.game.sonic3k.events.TestS3kTransitionWriteSupport" test "-DfailIfNoTests=false"`
  - PASS: 4 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 17278
    `x_speed expected=0x000C actual=0x0000` to frame 17322
    `camera_x expected=0x0260 actual=0x0261`.
  - New count: 1763 errors / 17 warnings.

### Root cause

Frame 17278 had two handoff defects. First, the engine rebuilt CNZ objects during
the act reload and lost the live `Obj_LevelResults` / `Obj_EndSignControl` chain
that clears `_unkFAA8` and then restores both players
(`docs/skdisasm/sonic3k.asm:62708-62720,180406-180412`). CNZ now requests a
delayed post-transition release so both players clear `object_control=$81` in
the ROM window.

Second, the engine treated the main player's signpost ending pose as a
`Ctrl_1_locked` state. ROM `Set_PlayerEndingPose` writes `object_control=$81`,
victory animation, and zero velocities only
(`docs/skdisasm/sonic3k.asm:181977-181988`); `Obj_EndSignLanded` locks only
`Ctrl_2` (`docs/skdisasm/sonic3k.asm:176198-176218`). Sonic therefore keeps
copying raw `Ctrl_1` into `Ctrl_1_logical` while object control freezes movement,
and `Sonic_RecordPos` stores that live input for Tails' delayed follow history
(`docs/skdisasm/sonic3k.asm:21541-21545,22119-22136`). The signpost pose now
keeps P1 logical input live and leaves the sidekick lock intact.

### New CNZ frontier (frame 17322)

`camera_x mismatch (expected=0x0260, actual=0x0261)`. Player and Tails
positions, velocities, and Tails CPU input now match through the previous
handoff failure. The next owner is post-transition horizontal camera clamp/hold
timing, not sidekick control release or logical-input history.

## 2026-05-24 - S3K CNZ signpost results create gate (CNZ f16669 -> f17278)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused signpost/results guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestS3kSignpostInstance,com.openggf.game.sonic3k.objects.TestS3kResultsScreenObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 5 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 16669
    `x expected=0x02D0 actual=0x32D0` to frame 17278
    `x_speed expected=0x000C actual=0x0000`.
  - New count: 2190 errors / 17 warnings.

### Root cause

Frame 16669 was the CNZ1-to-CNZ2 transition gate. The signpost bump eligibility
and landed-timer fixes brought the results object into the ROM window, but the
engine still signaled `Events_fg_5` as soon as `S3kResultsScreenObjectInstance`
was constructed. ROM `Obj_LevelResultsInit` queues three Kosinski module loads
and advances to `Obj_LevelResultsCreate` (`docs/skdisasm/sonic3k.asm:
62512-62584`); `Obj_LevelResultsCreate` polls `Kos_modules_left` and only then
sets `Events_fg_5` for Act 1 non-AIZ/non-ICZ zones
(`docs/skdisasm/sonic3k.asm:62586-62616`). CNZ screen events consume that flag
in the same frame because `Process_Sprites` runs before `ScreenEvents`
(`docs/skdisasm/sonic3k.asm:7884-7895,107603-107653`).

The engine now models that create gate before writing the transition signal.
The first local attempt opened the gate one frame early; the corrected guard
keeps the engine in CNZ1 coordinates through f16668 and performs the ROM-matched
CNZ2 remap at f16669.

### New CNZ frontier (frame 17278)

`x_speed mismatch (expected=0x000C, actual=0x0000)`. Player/camera coordinates
are now in CNZ2 and match the ROM. Tails is still frozen in engine
`object_control=$81`, while the ROM has cleared sidekick object control and the
Tails CPU resumes normal `fallthrough_sub20` acceleration. The next owner is
the in-level title-card / post-transition player-control handoff for P2, not
the signpost or CNZ act-transition remap.

## 2026-05-24 - S3K CNZ delayed post-boss BG-to-FG refresh/remap (CNZ f16360 -> f16669)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused CNZ event/scroll guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzAct1EventFlow" test "-DfailIfNoTests=false"`
  - PASS: 8 tests, 0 failures.
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzMinibossArenaHeadless#scrollControlBridgeSignalUsesRomWaitAndSlowPathBeforeEventHandoff+fgRefreshCopiesBossTunnelBgLayoutBackToForegroundCollision+fgRefreshRestoresTailsLandingCellFromBossBackgroundLayout" test "-DfailIfNoTests=false"`
  - PASS: 3 tests, 0 failures.
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzBossScrollHandler" test "-DfailIfNoTests=false"`
  - PASS: 5 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 16360
    `air expected=0 actual=1` to frame 16669
    `x expected=0x02D0 actual=0x32D0`.
  - New count: 2929 errors / 17 warnings.

### Root cause

Frame 16360 was an early foreground handoff/remap. The engine completed the
post-boss BG-to-FG layout copy immediately when `Events_fg_5` signaled, cleared
background collision, zeroed the boss scroll offset, and remapped player/camera
Y while the ROM was still executing the delayed redraw window. ROM
`CNZ1BGE_AfterBoss` primes `Draw_delayed_position=$2F0` and
`Draw_delayed_rowcount=$F`, then falls through to `CNZ1BGE_FGRefresh`
(`docs/skdisasm/sonic3k.asm:107521-107533`). `Draw_PlaneVertSingleBottomUp`
only takes the completion path after the row count decrements below zero
(`docs/skdisasm/sonic3k.asm:103436-103452,107533-107539`). The actual copy,
`Background_collision_flag` clear, `Events_bg+$08` clear, and `$1C0`
player/camera Y remap happen at `loc_51DAE`
(`docs/skdisasm/sonic3k.asm:107542-107568`), followed by the second delayed
refresh pass (`docs/skdisasm/sonic3k.asm:107572-107583`).

The engine now models the two delayed refresh passes with rowcount gating before
the BG-to-FG copy/remap and before the transition gate. The first implementation
completed one frame early at f16373; the corrected entry count preserves
pre-remap state through f16373 and matches the ROM remap at f16374.

### New CNZ frontier (frame 16669)

`x mismatch (expected=0x02D0, actual=0x32D0)`. Player/Tails scalar state remains
aligned through the delayed refresh window, and the ROM has now emitted
`act_transition_to_cnz2` / zone-act `z=3 a=1`. The engine is still in the
pre-transition world coordinate space (`cam_x=0x323B`, player `x=0x32D0`) while
the ROM has applied the CNZ2 transition coordinate remap (`cam_x=0x023B`,
player `x=0x02D0`, player `y=0x06AC`). The next owner is the seamless act swap
coordinate/world-offset handoff, not the post-boss BG collision refresh.

## 2026-05-24 - S3K CNZ miniboss top parent-defeat destruction stops false arena clears (CNZ f15735 -> f16360)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Scratch ROM diagnostic:
  `tools/bizhawk/record_s3k_trace.bat "Sonic and Knuckles & Sonic 3 (W) [!].gen" "src/test/resources/traces/s3k/cnz/s3k-cnz-sonic-tails.bk2" level_gated_reset_aware`
  with `OGGF_S3K_CNZ_EVENT_RAM_RANGE=15620-15735`.
  - Scratch output was generated locally for diagnosis and is not retained in the repository.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#defeatedParentDestroysTopBeforeMovementAndArenaClear,com.openggf.tests.TestS3kCnzMinibossArenaHeadless#arenaChunkCollisionClearRunsFromScreenEventNotTopObjectWrite" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15735
    `tails_y_speed expected=0x0000 actual=0x05C0` to frame 16360
    `air expected=0 actual=1`.
  - New count: 2942 errors / 17 warnings.

### Root cause

Frame 15735 was not a BG-to-FG refresh timing issue. Scratch ROM event-RAM
diagnostics showed `Events_bg+$00/$02` stayed `$0000/$0000` through the
f15660-f15669 window, while the engine still had false arena clears at
`f15668 snap=32D0,0310 raw=32C0,0300` and `f15669 snap=32F0,0330
raw=32E0,0320`. Those clears removed the foreground chunk descriptors under
Tails' later landing cell, leaving the engine to fall to the lower fallback row.

The false clears came from the CNZ miniboss top continuing to run terrain probes
after the parent was already defeated. ROM `Obj_CNZMinibossTopMain` checks the
parent status bit 7 before `MoveSprite2`, `SolidObjectFull`, animation, or any
terrain probe (`docs/skdisasm/sonic3k.asm:145053-145057`). When the parent is
defeated it jumps to `loc_6DDD2`, creates the explosion, clears collision,
displaces any player standing on it, and deletes the top
(`docs/skdisasm/sonic3k.asm:145190-145199`). The engine now destroys the top
before movement/arena-clear publication when its parent has entered the defeated
state.

### New CNZ frontier (frame 16360)

`air mismatch (expected=0, actual=1)`. The old f15735 Tails support-cell
failure is cleared; player and Tails positions/velocities are aligned at the
new first error, foreground descriptors under the previous Tails landing cell
are restored (`FG[0x65,5]=C7`), and the remaining owner is a later post-boss
player grounded/status transition after scroll-control handoff.

## 2026-05-24 - S3K CNZ focused-player look-down camera bias (CNZ f15569 -> f15627)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused CNZ guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossLookDownBiasIsOwnedByFocusedPlayer" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15569
    `camera_y expected=0x028E actual=0x028C` to frame 15627
    `camera_y expected=0x02B8 actual=0x02BA`.
  - New count: 1933 errors / 16 warnings.

### Root cause

Frame 15569 was a camera-bias ownership bug, not a player-position or CNZ
event/bounds mismatch. Sonic's DOWN input had reached the S3K
`scroll_delay_counter=$78` threshold and correctly decremented the global
camera Y bias from `$60` to `$5E`, but CPU Tails then ran the shared movement
reset-screen path in the same engine frame and eased the global camera bias back
to `$60`.

S3K Sonic `Obj01_LookUpDown` owns `Camera_Y_pos_bias`: the DOWN branch clamps
`scroll_delay_counter` at `$78`, subtracts 2 from `(a5)`, and skips the reset
screen easing path (`docs/skdisasm/sonic3k.asm:22615-22673`). CPU Tails'
normal ground path calls `Tails_InputAcceleration_Path` and the Tails roll
check, with no equivalent camera-bias reset/pan (`docs/skdisasm/sonic3k.asm:
25741-25746,25897-25937`). The engine now lets only non-CPU player movement
mutate the shared look/reset camera bias; CPU sidekicks still keep normal
screen-Y wrap behavior.

### New CNZ frontier (frame 15627)

`camera_y mismatch (expected=0x02B8, actual=0x02BA)`. Player and Tails
positions, subpixels, velocities, status, and camera X are aligned at the first
error. The next owner is later camera vertical follow/bounds behavior after the
CNZ miniboss post-boss handoff: engine bias is already `$0008`, but ROM holds
camera Y at `$02B8` while the engine scrolls down by two pixels.

## 2026-05-24 - S3K CNZ open-coil spark children (CNZ f15299 -> f15382)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused miniboss spark guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#openGoSpawnsRomCoilSparkChildren+openCoilSparkAppliesSidekickHurtKnockback" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15299
    `tails_y expected=0x02EC actual=0x02ED` to frame 15382
    `tails_x expected=0x32D7 actual=0x32D8`.
  - New count: 3031 errors / 15 warnings.

### Root cause

Frame 15299 was the missing open-coil spark hurt contact from the CNZ miniboss.
ROM `Obj_CNZMinibossOpenGo` creates `Child1_CNZCoilOpenSparks`, whose spark
children refresh from the parent, animate, and enter the collision response list
through `Child_DrawTouch_Sprite` (`docs/skdisasm/sonic3k.asm:144945-144951,
145323-145346,145660-145692,178048-178053`). That path calls
`Add_SpriteToCollisionResponseList` without an onscreen/render gate
(`docs/skdisasm/sonic3k.asm:21200-21209`), so the spark child must bypass the
engine's generic render-visibility touch gate. The spark uses collision byte
`$92` and the normal hurt response path sets routine 4, air status, and
knockback (`docs/skdisasm/sonic3k.asm:145660-145663,21050-21091`). The engine
was missing the children, so Tails stayed on the pre-hurt trajectory.

### New CNZ frontier (frame 15382)

`tails_x mismatch (expected=0x32D7, actual=0x32D8)`. The f15299 one-pixel
vertical divergence is cleared. The next owner is a Tails-only one-pixel X
drift after the miniboss hurt/aftermath; at the first error the CPU branch and
velocities already match (`xv/gv=0x0084`), so the new boundary is accumulated
sidekick position/history rather than an immediate miniboss hurt velocity.

## 2026-05-24 - S3K CNZ hurt-routine logical input latch (CNZ f15194 -> f15299)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused sidekick history guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#s3kHurtRoutineRecordPosKeepsPreviousLogicalInputForFollowerHistory" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Focused CNZ guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossTailsPushBypassUsesHurtLatchedLeaderInput" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15194
    `tails_g_speed expected=0x0000 actual=0x000C` to frame 15299
    `tails_y expected=0x02EC actual=0x02ED`.
  - New count: 3058 errors / 15 warnings.

### Root cause

The old f15194 owner was not a miniboss body/coil contact. Tails was in the
S3K `loc_13DD0` current `Status_Push` bypass, which preserves the delayed
`Ctrl_1_logical` word read from `Stat_table` and skips follow steering
(`docs/skdisasm/sonic3k.asm:26696-26705,26775-26785`). ROM's delayed word had
no horizontal input because the source Sonic frame was routine 4 hurt
knockback: `loc_122BE` calls `Sonic_RecordPos` directly without entering
`Sonic_Control` and without refreshing `Ctrl_1_logical`
(`docs/skdisasm/sonic3k.asm:21967-21975,22132,24449-24467`; S2 has the same
routine-4 shape at `docs/s2disasm/s2.asm:37810-37835`). The engine was
publishing fresh live RIGHT into follower history during hurt frames, so the
push-bypass frame replayed a spurious `$000C` Tails ground acceleration.

### New CNZ frontier (frame 15299)

`tails_y mismatch (expected=0x02EC, actual=0x02ED)`. The f15194 stale
ground-speed pulse is cleared. The next owner is Tails-only during/just after
the miniboss top sequence as Tails enters hurt routine 4; engine Tails is one
pixel lower and already shows divergent hurt knockback follow-on velocities.

## 2026-05-24 - S3K CNZ miniboss parent phase timing (CNZ f15059 -> f15194)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused phase guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#closingRawMultiDelayUsesPointerOnlyEntryBeforeCloseGo,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossParentSecondMovePassUsesRomPhase" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Focused miniboss suite:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics" test "-DfailIfNoTests=false"`
  - PASS: 22 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15059
    `g_speed expected=0x02B4 actual=-02B4` to frame 15194
    `tails_g_speed expected=0x0000 actual=0x000C`.
  - New count: 3175 errors / 15 warnings.

### Root cause

Frame 15059 was not a body-hitbox issue. The body collision byte `$0C` and
Touch_Enemy box math were already correct; the engine body was two pixels left
because the CNZ miniboss parent restarted the second move pass late.

Two local parent timing errors overlapped. The first Move-to-Go3 handoff reached
routine 6 two engine frames early; the trace guard now pins the ROM-visible
Go3/CloseGo handoff at f14712 for the `$90` Obj_Wait window
(`docs/skdisasm/sonic3k.asm:144912-144923,177944-177949`). Later, WaitHit's
Closing handoff was holding the first raw script pair too long. ROM
`loc_6DB4E` only swaps `$30` to `AniRaw_CNZMinibossClosing` and `$34` to
CloseGo, while `Animate_RawMultiDelay` pre-decrements `anim_frame_timer` before
loading pairs (`docs/skdisasm/sonic3k.asm:144960-144969,145707-145708,
177558-177586`). The compact parent raw-state entry now matches the
ROM-visible CloseGo at f15004, letting the body reach `x=$3338` at f15059
without applying the extra player rebound.

### New CNZ frontier (frame 15194)

`tails_g_speed mismatch (expected=0x0000, actual=0x000C)`. The f15059 main
player body rebound is cleared. The next owner is Tails-only after the
miniboss/top sequence: engine Tails is off-object with a tiny positive ground
speed while ROM keeps zero.

## 2026-05-24 - S3K CNZ miniboss opening-body repeat suppression (CNZ f14961 -> f15059)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused miniboss guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#bodyHitWhileOpeningStillSuppressesImmediateRepeatCollision,com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#productionTouchResponseClosedBodyBounceDoesNotOpenBeforeCloseGo" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Focused miniboss suite:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics" test "-DfailIfNoTests=false"`
  - PASS: 20 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 14961
    `tails_g_speed expected=-0218 actual=0x0218` to frame 15059
    `g_speed expected=0x02B4 actual=-02B4`.
  - New count: 2044 errors / 17 warnings.

### Root cause

At the old frontier, Tails had already received the ROM-correct miniboss body
rebound, but the engine left the parent body touchable while the parent was
already in Opening. The next frame applied a second immediate boss-body rebound
and mirrored Tails' ground velocity back positive.

ROM `Touch_Enemy` handles a boss-body attack before the object-specific
`CNZMiniboss_CheckPlayerHit`: it backs up `collision_flags(a1)` to `$25(a1)`,
stores the player marker in `$1C(a1)`, clears `collision_flags(a1)`, and
decrements `collision_property(a1)` (`docs/skdisasm/sonic3k.asm:20916-20921`).
`CNZMiniboss_CheckPlayerHit` then sees the cleared collision byte, seeds
`$3A(a0)=$10`, and restores `$25(a0)` only when that countdown expires
(`docs/skdisasm/sonic3k.asm:145404-145425`). `CnzMinibossInstance` now applies
that collision suppression before returning from in-progress Opening/WaitHit/
Closing player attacks, so Opening-body hits do not become repeat contacts on
the next frame.

### New CNZ frontier (frame 15059)

`g_speed mismatch (expected=0x02B4, actual=-02B4)`. The f14961 Tails repeat
rebound is cleared. Player/camera positions match at the first error, but the
engine applies a later main-player miniboss parent/body touch around
`@3336,0299` and reverses player velocity while ROM keeps the positive ground
speed through that frame.

## 2026-05-24 - S3K CNZ miniboss closed-body collision restore timer (CNZ f14650 -> f14943)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused miniboss/touch guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#playerAttackBeforeCloseGoBouncesWithoutOpeningCoil+playerAttackAfterCloseGoOpensCoilButDoesNotConsumeBossHp+productionTouchResponseCoilAttackOpensBossWithoutConsumingHp+productionTouchResponseClosedBodyBounceDoesNotOpenBeforeCloseGo,com.openggf.game.sonic3k.objects.TestS3kBossTouchResponseProfiles#cnzMinibossClosedBodyUsesRomObjDatCollisionByte,com.openggf.level.objects.TestTouchResponseManager#testS3kTouchSpecialUnlistedC0FlagDoesNotDecodeAsBoss" test "-DfailIfNoTests=false"`
  - PASS: 6 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 14650 `y_speed expected=0x0870 actual=-0870`
    to frame 14943 `g_speed expected=-0210 actual=0x0210`.
  - New count: 2564 errors / 18 warnings.

### Root cause

Frame 14650 was a repeated closed-body rebound. The first rebound at frame
14644 already matched ROM, but the engine kept the CNZ miniboss body touchable
and applied the boss-hit velocity negation again six frames later. ROM
`Touch_Enemy` backs up the body collision byte, clears `collision_flags(a1)`,
stores the player marker, and decrements `collision_property(a1)` on a boss hit
(`docs/skdisasm/sonic3k.asm:20909-20924`). On the following object pass,
`CNZMiniboss_CheckPlayerHit` sees the cleared body collision, seeds `$3A(a0)`
with `$10`, restores the collision_property count, and does not restore the
backed-up collision byte from `$25(a0)` until the timer expires
(`docs/skdisasm/sonic3k.asm:145404-145425`). `CnzMinibossInstance` now keeps
the parent body collision suppressed for that local restore window without
changing generic touch response.

### New CNZ frontier (frame 14943)

`g_speed mismatch (expected=-0210, actual=0x0210)`. The f14650 duplicate
closed-body hit is cleared. The new owner is later in the miniboss opening/top
sequence: ROM has already reversed the player velocity while the engine is still
moving right with no body/top/coil overlap at the first error.

## 2026-05-24 - S3K CNZ miniboss closed-body hit guard (CNZ f14594 -> f14650)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused miniboss/touch guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#playerAttackBeforeCloseGoBouncesWithoutOpeningCoil+playerAttackAfterCloseGoOpensCoilButDoesNotConsumeBossHp+productionTouchResponseCoilAttackOpensBossWithoutConsumingHp+productionTouchResponseClosedBodyBounceDoesNotOpenBeforeCloseGo,com.openggf.game.sonic3k.objects.TestS3kBossTouchResponseProfiles#cnzMinibossClosedBodyUsesRomObjDatCollisionByte,com.openggf.level.objects.TestTouchResponseManager#testS3kTouchSpecialUnlistedC0FlagDoesNotDecodeAsBoss" test "-DfailIfNoTests=false"`
  - PASS: 6 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 14594 `tails_x_speed expected=0x0128 actual=-0200`
    to frame 14650 `y_speed expected=0x0870 actual=-0870`.
  - New count: 2054 errors / 19 warnings.

### Root cause

Frame 14594 was an early CNZ miniboss opening mismatch. The engine entered the
Opening routine on any closed-body boss touch, which exposed the open coil/top
hurt collision early and sent Tails through the sidekick hurt-object route.
ROM `Obj_CNZMinibossInit` sets bit 3 of `$38(a0)` while the boss is still in
the initial moving/closed phase (`docs/skdisasm/sonic3k.asm:144885-144895`).
`Obj_CNZMinibossCloseGo` later clears that bit (`docs/skdisasm/sonic3k.asm:
144922-144932`), and `CNZMiniboss_CheckPlayerHit` only switches to Opening
when its `bset #3,$38(a0)` sees the bit was clear (`docs/skdisasm/sonic3k.asm:
145404-145415`). Closed-body hits can still use the normal boss-hit rebound
path (`docs/skdisasm/sonic3k.asm:20909-20924`), but they must not open the
boss before CloseGo.

### New CNZ frontier (frame 14650)

`y_speed mismatch (expected=0x0870, actual=-0870)`. The f14594 Tails hurt/open
state is gone; at f14650 the miniboss body/top/coil are closed and aligned
between ROM and engine. The next owner is main-player closed-body touch
response: engine applies full boss-hit velocity negation while ROM continues
with downward positive `y_speed`.

## 2026-05-24 - S3K CNZ Door current-X carry reference (CNZ f13575 -> f13628)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused door guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestDoorObjectInstance#horizontalDoorDoesNotCarryRiderOnSlideFrame" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 13575 `x expected=0x24C0 actual=0x24B8`
    to frame 13628 `y_speed expected=-0258 actual=-0158`.
  - New count: 3097 errors / 17 warnings.

### Root cause

Frame 13575 was a Door-specific rider carry mismatch. The horizontal CNZ Door
had moved from `$24D0` to `$24C8`, and the engine treated it like a generic
moving platform by applying `currentX - ridingX = -8` to the player on the
continued-riding pass. ROM `Obj_Door` updates the door position first, then
stores that same current `x_pos(a0)` in `d4` immediately before calling
`SolidObjectFull` for both vertical and horizontal variants
(`docs/skdisasm/sonic3k.asm:66123-66137, 66239-66258`). The continued-riding
branch copies `d4` into `d2`; `MvSonicOnPtfm` subtracts current `x_pos(a0)`
from `d2`, so the X carry delta is zero (`docs/skdisasm/sonic3k.asm:41038-41040,
41642-41680`). `DoorObjectInstance` now opts out of horizontal rider carry
without changing shared solid behavior.

### New CNZ frontier (frame 13628)

`y_speed mismatch (expected=-0258, actual=-0158)`. The next owner is after the
door sequence near the Batbot/Clamer cluster: player X/subpixels match, but the
engine has a less negative jump/fall velocity after touching/destroying the
Batbot around `$24D0,$0089`.

## 2026-05-24 - S3K CNZ door stale standing-bit no-contact return (CNZ f13486 -> f13575)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused solid guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.level.objects.TestSolidObjectManager#inlineCheckpointAirborneStaleStandingBitDoesNotRelandSameObject" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 13486 `y_speed expected=0x0038 actual=0x0000`
    to frame 13575 `x expected=0x24C0 actual=0x24B8`.
  - New count: 2354 errors / 19 warnings.

### Root cause

Frame 13486 was a main-player support-release ordering mismatch on the CNZ
door at `$24C0,$02C8`: after movement/physics the engine had applied gravity
and matched ROM `y_speed=$0038`, but the door's inline solid checkpoint then
re-landed the player from a stale standing bit and zeroed `y_speed`; a later
object cleared support, leaving ROM status but engine velocity. S3K `Obj_Door`
calls `SolidObjectFull` for both vertical and horizontal variants after
preparing d1/d2/d3/d4 (`docs/skdisasm/sonic3k.asm:66136-66137,
66249-66258`). `SolidObjectFull_1P` consumes this stale standing-bit/in-air
case by clearing support and returning `d4=0` before `SolidObject_cont` can
land the player (`docs/skdisasm/sonic3k.asm:41017-41035`); S2 has the same
helper contract (`docs/s2disasm/s2.asm:34831-34849`). The engine keeps the
pre-existing standing-bit snapshot/clear behavior for all solids, but the
early no-contact return is opt-in for SolidObjectFull-style providers so
custom objects such as CNZCylinder retain their object-local capture paths.

### New CNZ frontier (frame 13575)

`x mismatch (expected=0x24C0, actual=0x24B8)`. The new owner is later in the
same door/Clamer/Batbot/CNZCylinder cluster: the player is riding slot 10 door
in the engine while ROM is on object slot `$0C`, with an 8-pixel X offset and
one ring-count difference already accumulated.

## 2026-05-24 - S3K CNZ cylinder release reads stored ROM y_vel (CNZ f13116 -> f13486)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 25 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 13116 `y_speed expected=-0680 actual=-0880`
    to frame 13486 `y_speed expected=0x0038 actual=0x0000`.
  - New count: 2987 errors / 20 warnings.

### Root cause

Frame 13116 was a main-player jump release from subtype `$46` CNZCylinder.
Engine `currentYVelocity` contained a synthetic `-$200` displacement derived
from the vertical oscillator's one-pixel position step, so the release wrote
`-$880`. ROM `loc_325B6` copies `y_vel(a0)` and adds `-$680`
(`docs/skdisasm/sonic3k.asm:68059-68068`), but the sine vertical oscillator
path `loc_3238C` writes `y_pos(a0)` directly and advances the angle without
updating `y_vel(a0)` (`docs/skdisasm/sonic3k.asm:67865-67872`). CNZCylinder
now uses the ROM stored velocity for rider launch/threshold calculations:
mode-0 cylinders use the actual mode-0 `y_vel`; sine/circular routes contribute
zero rather than the engine-only position delta.

### New CNZ frontier (frame 13486)

`y_speed mismatch (expected=0x0038, actual=0x0000)`. The new owner appears to
be main-player object/support release around the door/vacuum/bumper cluster:
ROM has just cleared `on_object` and applies gravity, while the engine is still
at zero vertical speed after landing/support.

## 2026-05-24 - S3K CNZ P2 vertical cylinder frame-entry support anchor (CNZ f13060 -> f13116)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 24 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 13060 `tails_y_speed expected=0x0320 actual=0x0000`
    to frame 13116 `y_speed expected=-0680 actual=-0880`.
  - New count: 2603 errors / 17 warnings.

### Root cause

Frame 13060 was Tails/P2 resolving a new top contact against subtype `$46`
CNZCylinder after the engine had already advanced the vertical oscillator one
pixel upward. ROM `Obj_CNZCylinder` runs motion, P1 `sub_324C0`, P2
`sub_324C0`, then `SolidObjectFull` in the same object pass
(`docs/skdisasm/sonic3k.asm:67656-67672`); P2 participates in
`SolidObjectFull` while `render_flags(a1)` is negative
(`docs/skdisasm/sonic3k.asm:41006-41016`). The vertical oscillator body writes
`y_pos(a0)` before angle increment (`docs/skdisasm/sonic3k.asm:67865-67874`),
and `SolidObject_cont` classifies the first top contact from that object-pass
anchor (`docs/skdisasm/sonic3k.asm:41394-41440`). The engine now uses the
frame-entry anchor for this CPU sidekick vertical-oscillator upward new-contact
case, so Tails stays airborne with ROM `y_speed=$0320` through f13060.

The f13062 follow-up was the same split-phase overwrite after P2 capture:
`sub_324C0` restores radii, clears air/roll, sets `object_control=$03`, and the
same object pass still calls `SolidObjectFull` for on-screen P2
(`docs/skdisasm/sonic3k.asm:67985-68005, 41006-41016`). The capture wrote the
ROM support Y from frame-entry `$0414`, but the later solid checkpoint had been
using the already-stepped `$0413`. CNZCylinder now also uses the frame-entry Y
anchor for CPU object-controlled riders on the same vertical-oscillator upward
support path.

### New CNZ frontier (frame 13116)

`y_speed mismatch (expected=-0680, actual=-0880)`. Tails remains aligned at the
first error. The new owner is main-player launch/release near the same
CNZCylinder/cage/Clamer cluster: ROM releases Sonic with `y_speed=$F980` while
the engine has `y_speed=$F780`. This is separate from the f13060 Tails support
gate.

## 2026-05-24 - S3K CNZ vertical cylinder held-support contact anchor (CNZ f13049 -> f13060)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 22 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 13049 `y expected=0x03E9 actual=0x03E8`
    to frame 13060 `tails_y_speed expected=0x0320 actual=0x0000`.
  - New count: 2632 errors / 17 warnings.

### Root cause

Frame 13049 was a split object/solid-phase overwrite on vertical CNZCylinder
subtype `$46`. The cylinder's held-rider path first wrote Sonic to the
ROM-correct frame-entry support Y, but the later inline solid-contact checkpoint
overwrote that with the cylinder's already-updated current Y. ROM
`Obj_CNZCylinder` runs `sub_321E2`, both `sub_324C0` rider calls, and
`SolidObjectFull` inside one object pass (`docs/skdisasm/sonic3k.asm:
67656-67672`). The vertical oscillator `loc_3238C` writes `y_pos(a0)` before
angle increment (`docs/skdisasm/sonic3k.asm:67865-67874`), and
`SolidObjectFull_1P` / `MvSonicOnPtfm` carries the rider from that same
ROM-visible `y_pos(a0)` (`docs/skdisasm/sonic3k.asm:41016-41040,
41667-41679`).

CNZCylinder now uses its frame-entry Y anchor for the proven object-controlled
vertical-oscillator upward support contact, matching the same local exception
already used for the earlier circular vertical support case. The change is
object-local; no generic placement loader or shared movement behavior changed.

### New CNZ frontier (frame 13060)

`tails_y_speed mismatch (expected=0x0320, actual=0x0000)`. Sonic/player and
camera match at the first error. The new failure is Tails-only: ROM still has
Tails airborne/rolling with `y_speed=$0320`, while the engine has already
latched Tails as supported by the same CNZCylinder and zeroed his Y speed before
the ROM's next P2 cylinder capture frame. This is a separate P2
solid-support/capture timing boundary.

## 2026-05-24 - S3K CNZ cylinder mode-0 zero-cross BPL parity (CNZ f12808 -> f13049)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 21 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 12808 `y expected=0x0535 actual=0x0534`
    to frame 13049 `y expected=0x03E9 actual=0x03E8`.
  - New count: 2640 errors / 17 warnings.

### Root cause

Frame 12808 was a hidden subpixel/velocity recurrence error in the same
subtype `$20` mode-0 CNZCylinder vertical controller. The engine already
matched the visible cylinder Y through frame 12807, but at the positive-offset
return zero-crossing it treated `y_vel == 0` after the initial `-$20` step as
eligible for the UP-held `-$20` branch. ROM `loc_322AC` does `tst.w y_vel(a0)`
then `bpl.s loc_322D2`, so zero is non-negative and must take only the fixed
`-$10` step (`docs/skdisasm/sonic3k.asm:67772-67787`). The earlier hidden
subpixel difference becomes visible as the one-pixel rider/camera Y mismatch
at frame 12808.

### New CNZ frontier (frame 13049)

`y mismatch (expected=0x03E9, actual=0x03E8)`. Player X/subpixels/speeds match;
the camera follows the one-pixel player Y. The player is still
object-controlled/supporting a CNZCylinder near `$2920,$041D`; engine cylinder
slot reports `$2920,$041C` with `yv=FF00`, so the next owner appears to be the
next mode-0 cylinder's late vertical support/held timing around the
Clamer/cylinder cluster, not camera logic.

## 2026-05-24 - S3K CNZ cylinder mode-0 live held input (CNZ f12588 -> f12808)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 20 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 12588 `y expected=0x04C9 actual=0x04CA`
    to frame 12808 `y expected=0x0535 actual=0x0534`.
  - New count: 3160 errors / 16 warnings.

### Root cause

Frame 12588 was not a rider snap or camera issue. The rider Y was one pixel low
because the mode-0 CNZCylinder body itself was one pixel low after the vertical
controller update. ROM `Obj_CNZCylinder` runs `sub_321E2`, P1/P2 `sub_324C0`,
then `SolidObjectFull` in that object pass (`docs/skdisasm/sonic3k.asm:
67656-67672`). In the subtype `$20` mode-0 route, `loc_32254` calls
`MoveSprite2`, then reads the current `Ctrl_1_held_logical` /
`Ctrl_2_held_logical` while the corresponding standing bit is set before
applying the UP/DOWN acceleration adjustment (`docs/skdisasm/sonic3k.asm:
67736-67752,67772-67782`).

The engine correctly latched standing feedback across its split object/solid
phases, but it also reused the prior solid callback's held-input byte for the
mode-0 acceleration. At the f12582 UP transition this missed one `$20`
upward-acceleration adjustment, leaving the cylinder at `$04FE` instead of
ROM `$04FD` by f12588. CNZCylinder now keeps the stored standing cadence but
reads live current-frame UP/DOWN from standing riders for mode-0 acceleration.

### New CNZ frontier (frame 12808)

`y mismatch (expected=0x0535, actual=0x0534)`. Player X/subpixels/speeds still
match. Sonic is still object-controlled/supporting CNZCylinder slot 7 in the
same mode-0 cluster; ROM cylinder is at `$28A0,$0569`, engine at `$28A0,$0568`
with `pre=@28A0,056A` and `yv=FE60`, so the next owner remains the later
mode-0 vertical return/support timing around the cylinder peak/reversal.

## 2026-05-24 - S3K CNZ cylinder negative-step capture distance (CNZ f12187 -> f12588)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 19 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 12187 `x expected=0x2355 actual=0x2356`
    to frame 12588 `y expected=0x04C9 actual=0x04CA`.
  - New count: 3309 errors / 18 warnings.

### Root cause

Frame 12187 was the same CNZCylinder first-capture distance family on the
narrow horizontal oscillator subtype `$41`, but with a negative center step and
a rider on the left. ROM `Obj_CNZCylinder` runs `sub_321E2`, then P1/P2
`sub_324C0`, then `SolidObjectFull` in the same object pass
(`docs/skdisasm/sonic3k.asm:67656-67672`). The subtype `$41` route uses
`loc_322F0`, which writes the horizontal `x_pos(a0)` before advancing the angle
(`docs/skdisasm/sonic3k.asm:67807-67815`). When the inactive `sub_324C0` path
first consumes the standing bit, it stores `abs(player.x_pos - object.x_pos)`
into `2(a2)` (`docs/skdisasm/sonic3k.asm:67985-67998`), and the active held
path later adds that stored distance to `x_pos(a0)`
(`docs/skdisasm/sonic3k.asm:68019-68038`).

In the engine split pass, the deferred non-CPU standing callback for this
negative horizontal step was consumed after `centerX` had already advanced one
extra pixel toward the rider, storing distance `$14` from `$236B` instead of
the ROM-visible distance `$15` from the frame-entry `$236C`. CNZCylinder now
uses the frame-entry X anchor for non-CPU horizontal first-capture whenever the
split center update moved toward the rider; moving-away and CPU paths keep their
existing behavior.

### New CNZ frontier (frame 12588)

`y mismatch (expected=0x04C9, actual=0x04CA)`. Player X, subpixels, and speeds
match at the first error. Sonic is object-controlled/supporting CNZCylinder
slot 7 near `$28A0,$04FD/$04FE`; the engine cylinder reports
`pre=@28A0,0500` and `yv=FD70`, so the next owner appears to be vertical
support/held timing in the CNZCylinder/bumper cluster rather than camera logic.

## 2026-05-24 - S3K CNZ cylinder positive-step capture distance (CNZ f11907 -> f12187)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 18 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 11907 `x expected=0x2076 actual=0x2074`
    to frame 12187 `x expected=0x2355 actual=0x2356`.
  - New count: 3379 errors / 19 warnings.

### Root cause

Frame 11907 was a CNZCylinder first-capture distance error on the wide
horizontal oscillator subtype `$52`. ROM `Obj_CNZCylinder` runs `sub_321E2`,
then P1/P2 `sub_324C0`, then `SolidObjectFull` in the same object pass
(`docs/skdisasm/sonic3k.asm:67656-67672`). The subtype `$52` route uses
`loc_3230E`, which writes the horizontal `x_pos(a0)` before advancing the angle
(`docs/skdisasm/sonic3k.asm:67818-67825`). When the inactive `sub_324C0` path
first consumes the standing bit, it stores `abs(player.x_pos - object.x_pos)`
into `2(a2)` (`docs/skdisasm/sonic3k.asm:67985-67998`), and the next active
held path adds that stored distance back to `x_pos(a0)`
(`docs/skdisasm/sonic3k.asm:68019-68038`).

In the engine split pass, the deferred non-CPU standing callback for the
positive horizontal step was consumed after `centerX` had already advanced one
extra pixel, storing distance `$06` from `$206E` instead of the ROM-visible
distance `$08` from the frame-entry `$206C`. CNZCylinder now uses the
frame-entry X anchor for that non-CPU positive-step first-capture distance,
leaving active held writes and CPU-specific paths under their existing guards.

### New CNZ frontier (frame 12187)

`x mismatch (expected=0x2355, actual=0x2356)`. Sonic/camera/speeds are aligned
through frame 12186; Sonic is object-controlled/supporting a later CNZCylinder
near `$236B,$0460` with speeds zero. The new mismatch is one pixel to the right
on that later held/support path.

## 2026-05-24 - S3K CNZ spike init-frame solid suppression (CNZ f11818 -> f11907)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused spike guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kSpikeObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 11818 `y expected=0x0164 actual=0x0170`
    to frame 11907 `x expected=0x2076 actual=0x2074`.
  - New count: 3398 errors / 19 warnings.

### Root cause

Frame 11818 was a same-spawn-frame spike solid collision. The moving upside-down
spike at `$2000,$0145` had just become active; ROM `Obj_Spikes` initialization
stores the main routine pointer and returns before any `sub_242B6` movement or
`SolidObjectFull` call can run (`docs/skdisasm/sonic3k.asm:48925-49012`). The
engine moved the spike to `$014D` and resolved its underside solid in that same
activation frame, snapping Sonic from `$0164` to `$0170` and zeroing
`y_speed`/`ground_vel` one frame early. S3K spikes now keep their base position
and suppress solidity for that init-equivalent frame; the next execution reaches
the normal moving-spike body (`docs/skdisasm/sonic3k.asm:49075-49110,
49192-49263`).

### New CNZ frontier (frame 11907)

`x mismatch (expected=0x2076, actual=0x2074)`. Sonic has landed/captured on the
next CNZCylinder around `$206E,$01A0`; positions and speeds match through frame
11906, then engine/camera are two pixels left while object-control/support is
active.

## 2026-05-24 - S3K CNZ vertical cylinder side-contact anchor (CNZ f6678 -> f11818)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 17 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 6678 `x expected=0x15E9 actual=0x15EB`
    to frame 11818 `y expected=0x0164 actual=0x0170`.
  - New count: 3255 errors / 21 warnings.

### Root cause

The f6678 movement was already ROM-correct after player ground movement:
`x_pos=$15E9`, `x_vel=ground_vel=$FE9A`. The mismatch was a later full-solid
side response from CNZCylinder subtype `$45` at `$15C0,$04FE`, which applied
`SolidObject_cont` side separation and `loc_1E056` speed-zeroing one frame
early.

ROM `Obj_CNZCylinder` runs its motion, P1/P2 `sub_324C0`, then
`SolidObjectFull` in the object slot pass (`docs/skdisasm/sonic3k.asm:
67656-67672`). The vertical oscillator path `loc_3236E` writes `y_pos(a0)` from
the sine result (`docs/skdisasm/sonic3k.asm:67843-67851`), and
`SolidObject_cont` classifies side-vs-top before zeroing speed in the side
branch (`docs/skdisasm/sonic3k.asm:41394-41440,41473-41495`). In the engine's
split pass, the subtype `$45` body had advanced from frame-entry `$04FD` to
`$04FE` before the new side-contact classification, so it crossed the side
threshold one frame early. CNZCylinder now uses the frame-entry Y anchor for
new non-CPU, non-object-controlled vertical-oscillator contacts only.

### New CNZ frontier (frame 11818)

`y mismatch (expected=0x0164, actual=0x0170)`. Player and Tails X positions are
aligned at the first error. Sonic has just jumped/been launched near the
CNZ balloon/spikes/cylinder/cage cluster around `$2000`; engine has zeroed
player `y_speed`/`ground_vel` while ROM still has upward `y_speed=$F9F0`.

## 2026-05-24 - S3K CNZ P2 cylinder nudge/held-anchor cleanup (CNZ f4440 -> f6678)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused sidekick/cylinder guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#groundedFollowNudgeClearsQueuedLateContactBridge,com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 17 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 4440 `tails_x expected=0x1B9A actual=0x1B99`
    to frame 6678 `x expected=0x15E9 actual=0x15EB`.
  - New count: 3814 errors / 19 warnings.

### Root cause

Frame 4440 was a stale sidekick CPU bridge, not a trace-sync issue. ROM
`loc_13E0A`/`loc_13E34` applies the +/-1 Tails follow nudge immediately when
`ground_vel` is nonzero and has no deferred queue (`docs/skdisasm/sonic3k.asm:
26717-26724,26734-26741`). The engine's queued late-contact bridge is still
needed when the CPU pass was airborne and a later cylinder contact supplies the
ROM-visible ground context, but at f4440 the current CPU pass was already
grounded/nonzero. The immediate nudge now clears the queued bridge so
`Obj_CNZCylinder` capture does not apply a second one-pixel shift.

The follow-up f4447 mismatch was the same CNZCylinder split-phase held-anchor
family for P2. ROM `Obj_CNZCylinder` runs motion, P1/P2 `sub_324C0`, then
`SolidObjectFull` in one object slot pass (`docs/skdisasm/sonic3k.asm:
67656-67672`), and active rider path `loc_32538` writes the held rider from
the current ROM `x_pos(a0)` (`docs/skdisasm/sonic3k.asm:68019-68038`). In the
engine split, subtype `$41` P2 hold saw current center `$1BA1` after frame-entry
`$1BA0`; CPU sidekick horizontal-oscillator holds now use the same frame-entry
anchor as the accepted player f4320 fix.

### New CNZ frontier (frame 6678)

`x mismatch (expected=0x15E9, actual=0x15EB)`. Player is grounded near the
CNZ bumper/cylinder cluster around `$15C0-$1640`; Tails remains aligned enough
at the first error. The new failure is main-player horizontal position/speed
around nearby CNZCylinder/InvisibleHurtBlock/Bumper objects, not the previous
P2 held-cylinder path.

## 2026-05-24 - S3K CNZ cylinder held-anchor split phase (CNZ f4320 -> f4440)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 15 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 4320 `x expected=0x1BC7 actual=0x1BC6`
    to frame 4440 `tails_x expected=0x1B9A actual=0x1B99`.
  - New count: 3843 errors / 19 warnings.

### Root cause

The frame-4320 mismatch was CNZCylinder-local. ROM `Obj_CNZCylinder` executes
`sub_321E2`, then the active rider path, then `SolidObjectFull` in one object
slot pass (`docs/skdisasm/sonic3k.asm:67656-67672`). For subtype `$41`,
`loc_322F0` writes the horizontal oscillator's `x_pos(a0)` before advancing the
angle (`docs/skdisasm/sonic3k.asm:67807-67815`), and the held rider path
`loc_32538` adds the rider offset to that same `x_pos(a0)` and writes player
`x_pos(a1)` (`docs/skdisasm/sonic3k.asm:68019-68038`).

The engine split had already advanced the cylinder center from `$1BDF` to
`$1BDE` before the deferred non-CPU held-rider write, so it used the post-step
anchor one frame too early. CNZCylinder now uses its frame-entry X anchor for
non-CPU horizontal oscillator held writes whenever the center moved during the
split phase, matching the ROM-visible `x_pos(a0)` consumed by `loc_32538`.

### New CNZ frontier (frame 4440)

`tails_x mismatch (expected=0x1B9A, actual=0x1B99)`. Player/camera/rings match at
the first error. Tails is being captured/held by the same subtype `$41`
CNZCylinder at `$1BA0,$07E0`; ROM has Tails one pixel farther right while the
engine diagnostic shows `p2=capture pre=1B9A ... post=1B99`. This is a separate
P2 capture/held-offset timing issue after the player f4320 anchor mismatch.

## 2026-05-24 - S3K CNZ Clamer projectile native delete marker (CNZ f1577 -> f4320)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused Clamer / CNZ slot-pressure guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayClamerAutoCloseProjectileConsumesRomSlot+traceReplayClamerAutoCloseProjectileHoldsSlotUntilRomDeleteFrame,com.openggf.game.sonic3k.objects.TestClamerObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 15 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ advances from frame 1577 `x expected=0x0F4C actual=0x0F4D`
    to frame 4320 `x expected=0x1BC7 actual=0x1BC6`.
  - New count: 3671 errors / 15 warnings.

### Root cause

The Clamer auto-close projectile is the `ChildObjDat_89150` child spawned by
`Obj_Clamer` when the raw auto-close animation reaches mapping frame 8
(`docs/skdisasm/sonic3k.asm:185930-185940,186020-186027`). Its routine enters
`loc_86D4A`, then runs `loc_86D5E`: `MoveSprite2` followed by
`Sprite_CheckDeleteTouchXY` (`docs/skdisasm/sonic3k.asm:182257-182266`).

The engine already created the projectile in the ROM slot at frame 621, but the
generic dynamic-child out-of-range pass removed it before the ROM-equivalent
delete marker. ROM `Sprite_CheckDeleteTouchXY` branches through `Go_Delete_Sprite`,
which installs `Delete_Current_Sprite` and keeps the SST slot occupied until the
next `ExecuteObjects` pass (`docs/skdisasm/sonic3k.asm:179027-179039,
179131-179134,36108-36122`). The projectile now owns its native offscreen
lifetime instead of being freed by the manager-level generic cull.

### New CNZ frontier (frame 4320)

`x mismatch (expected=0x1BC7, actual=0x1BC6)`. Player/camera/rings match; Sonic
is object-controlled on the CNZ cylinder cluster with matching zero velocities,
but the engine is one pixel left at the first error. This is past the Clamer
projectile slot-pressure/barber-pole inversion boundary and points at the later
CNZ cylinder held-position/object-control path.

## 2026-05-23 - S3K AIZ drawbridge SolidObjectFull2 landing width (AIZ f19394 -> f19669)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused AIZ drawbridge guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects#drawBridgeFlatSupportStartsOnRoutineEntryAfterSettledAngleIsReached+drawBridgeUsesSolidObjectFull2ProfileForLandingWidthPixels" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 19394 `y expected=0x01FD actual=0x01FC`
    to frame 19669 `tails_x_speed expected=-0045 actual=-0051`.
  - New count: 39 errors / 23 warnings.

### Root cause

The AIZ drawbridge object was modeled as a top-only solid with
`groundHalfHeight=9`. ROM `Obj_AIZDrawBridge` uses `SolidObjectFull2` for the
flat bridge, with `d1=$6B`, `d2=8`, and `d3=8`
(`docs/skdisasm/sonic3k.asm:59625-59643`). Because `SolidObjectFull2` new
landings narrow from the collision half-width back to `width_pixels=$60`, the
ROM rejects Sonic at x `$4A76` and first rides the bridge at x `$4A80`; the
top-only engine profile accepted the full `$6B` width early and snapped Y one
pixel high. The bridge also now defers its settled/full-support phase until the
next routine entry after `$38` reaches `$80`, matching `loc_2B2B0`
(`docs/skdisasm/sonic3k.asm:59591-59613`).

### New AIZ frontier (frame 19669)

`tails_x_speed mismatch (expected=-0045, actual=-0051)`. Player Y/camera now
survive the old f19394 drawbridge landing window. The new failure is sidekick
movement during the post-bridge/end-capsule flow, with ROM Tails already in
air/rolling-like status `06` while the engine sidekick speed is slightly more
negative.

## 2026-05-23 - S3K AIZ end-boss selector/full-longword travel (AIZ f19019 -> f19394)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused AIZ end-boss guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestAizEndBossInstance#repositionSelectorUsesRomRawMaskNotLowTwoBits+repositionVelocityUsesFullLongwordPositionSubpixels" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 19019 `g_speed expected=0x00D4 actual=-00D4`
    to frame 19394 `y expected=0x01FD actual=0x01FC`.
  - New count: 120 errors / 23 warnings.

### Root cause

The engine's AIZ end-boss target selector used `rng.nextInt(4) * 4`, consuming
the low two bits of `Random_Number`. ROM `loc_69A66` masks the raw word with
`andi.w #$C,d0`, rejects the previous angle, then indexes `word_69AC8`
(`sonic3k.asm:138748-138756`). At the AIZ f19019 window the trace-proven raw
draw is `A1AF5778`; ROM therefore selects angle `$08`, targeting
`_unkFA84+$160 = $4A40`.

The selector alone made the boss hittable one frame early because the engine
computed travel velocity from integer pixels and preserved only an 8-bit
fractional byte. ROM subtracts the full longword `x_pos/y_pos`, doubles that
16.16 delta, and takes the high word as velocity (`sonic3k.asm:138756-138771`);
`MoveSprite2` then adds `x_vel/y_vel << 8` to the full longword position
(`sonic3k.asm:36053-36061`). Preserving the full longword subpixel phase keeps
the re-emerge/hover body at the ROM Y during the contact frame.

### New AIZ frontier (frame 19394)

`y mismatch (expected=0x01FD, actual=0x01FC)`. Player X/speeds, camera, and
Tails match at the first error. Sonic is riding the AIZ draw bridge/end-sequence
object after the boss hit; this is a separate draw-bridge/end-flow vertical
carry or support-order issue.

## 2026-05-23 - S3K AIZ end-boss reveal animation timing (AIZ f18847 -> f19019)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused AIZ end-boss guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestAizEndBossInstance#revealedRawAnimationUsesRomCallbackTimingBeforeHover" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 18847 `camera_x expected=0x4880 actual=0x4882`
    to frame 19019 `g_speed expected=0x00D4 actual=-00D4`.
  - New count: 101 errors / 23 warnings.

### Root cause

The frame-18847 camera mismatch was the AIZ end boss entering its camera-scroll
phase two frames too early. ROM routine `loc_6932C` drives the revealed animation
through `Animate_RawNoSSTMultiDelay`, using `byte_69DB3` and only taking the
`$F4` callback after the final `$00/$00` mapping frame
(`docs/skdisasm/sonic3k.asm:138120-138122,139104-139110,177558-177587`).
The engine collapsed that raw multi-delay sequence and entered `loc_6933A` hover
on update 19 instead of update 21. That two-frame lead propagated through the
hover/retreat/re-emerge sequence and advanced `loc_69456`'s `Camera_min_X_pos`
and `Camera_max_X_pos` `+2` write two frames early
(`docs/skdisasm/sonic3k.asm:138214-138224`).

### New AIZ frontier (frame 19019)

`g_speed mismatch (expected=0x00D4, actual=-00D4)`. Camera now matches through
the old f18847 window and reaches `$4980,$015A` at the new first error. The new
failure is player movement/sign during the AIZ end-boss/log-bridge section while
Tails remains aligned at the first error.

## 2026-05-23 - S3K CNZ f1577 slot-pressure regression diagnosis

- Worktree: integrated local workspace with other workers' placement/AIZ/MGZ/CNZ/shared edits preserved.
- Focused Clamer lifecycle guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestClamerObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 11 tests.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Still fails at frame 1577: `x expected=0x0F4C actual=0x0F4D`, 3795 errors / 14 warnings.

### Diagnosis

The f1577 barber-pole inversion remains a pre-1325 slot-pressure problem, not
the barber-pole order itself. A bounded Clamer lifecycle gap was confirmed:
ROM `Obj_Clamer` runs `loc_88FDC`, calls `CreateChild1_Normal` with
`ChildObjDat_89148`, and creates one hidden spring child at `y_pos-8`
(`sonic3k.asm:185875-185879,185998-186000`). The engine previously folded that
spring entirely into the parent and did not allocate the child SST slot. A local
guard now covers that child slot allocation/release.

That fix does not move the frontier. CNZ still has the old cylinder in engine
slot 4 and the later cylinder/barber-pole cluster shifted (`CNZCylinder` in
engine s7 where ROM has it in s6, low barber pole in engine s9 where ROM has
it in s8). The remaining regression is therefore earlier than the Clamer child:
likely parent object load/lifetime or two-axis placement timing before the
f569/f642/f665 balloon pressure cascade.

## 2026-05-23 - S3K AIZ sidekick dead-fall marker threshold (AIZ f18645 -> f18847)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused sidekick guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity#s3kDeadFallWaitsForCameraYPlus100BeforeDespawnMarker+s3kDeadFallAppliesDespawnMarkerAfterCameraYPlus100Threshold+s3kDespawnMarkerReturnsToCatchUpFlightRoutine+levelBoundaryKillRunsTailsTouchFloorBeforeDeathState" test "-DfailIfNoTests=false"`
  - PASS: 4 tests.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 18645 `tails_x expected=0x493F actual=0x7F00`
    to frame 18847 `camera_x expected=0x4880 actual=0x4882`.
  - New count: 102 errors / 23 warnings.

### Root cause

The engine treated S3K sidekick dead-fall as an immediate `sub_13ECA` marker
warp. ROM routine `sub_123C2` first reads `Camera_Y_pos`, adds `$100`, and
returns without calling `sub_13ECA` while Tails' `y_pos` is still within that
camera-relative threshold (`sonic3k.asm:24546-24568`). Only after the threshold
is crossed does it set routine 2 and branch to `sub_13ECA`, which writes
`x_pos=$7F00`, `y_pos=0`, `object_control=$81`, and in-air status
(`sonic3k.asm:24570-24578,26800-26809`). The caller at `loc_157C8` then still
runs `MoveSprite_TestGravity` (`sonic3k.asm:29283-29285`).

At the old frontier ROM Tails was at `y=$022F` with camera Y `$015A`; because
`$022F <= $015A+$100`, ROM kept the dead-fall body local near `$493F`, while
the engine had already parked him at `$7F00`.

### New AIZ frontier (frame 18847)

`camera_x mismatch (expected=0x4880, actual=0x4882)`. Player and Tails positions
match at the first error; the remaining issue appears to be AIZ2 end-boss
camera progression after the sidekick dead-fall marker timing is corrected.

## 2026-05-23 - S3K AIZ stale sidekick push-grace follow steering (AIZ f10586 -> f18645)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused sidekick guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalPushGraceSuppressesGroundedFollowPulseInsideAizObjectBand+groundedPushGraceUsesCurrentControlWordOutsideAizObjectOrderBridge+s3kClearedPushGraceStillAllowsGroundedFollowRightInput+s3kLocalPushGraceNearAizSpikedLogsStillFallsThroughToFollowRight+s3kFarTargetPushGraceDoesNotBypassAutoJumpHeightAndDistanceGates+s3kLocalPushGraceOutsideAizObjectOrderDoesNotBypassAutoJumpGates+cnzDoorSupportGraceFallsThroughToFollowLeftNudgeWhenPushIsClear+s3kPanicReleaseGateUsesLevelFrameCounterLowByte+aizPanicReleaseGateUsesRomVisibleReloadCounterBridge" test "-DfailIfNoTests=false"`
  - PASS: 9 tests.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 10586 `tails_x expected=0x2071 actual=0x2070`
    to frame 18645 `tails_x expected=0x493F actual=0x7F00`.
  - New count: 139 errors / 23 warnings.

### Root cause

At frame 10586 the engine was treating stale local push grace as a full
`loc_13DD0` push bypass. ROM only branches around FollowLeft/FollowRight when
Tails' current `Status_Push` bit is set; if current push is clear, it falls
through to the S3K `$30` follow steering threshold (`sonic3k.asm:26702-26729`)
and `Tails_InputAcceleration_Path` consumes the resulting control word
(`sonic3k.asm:27798-27805,28103-28122`). The engine branch
`grace_push_bypass` suppressed that FollowRight override near the AIZ spiked
logs, leaving Tails one pixel left and with stale negative ground speed.

The fix keeps the short stale-push bridge only in proven AIZ object-order
carrier contexts, where the engine can clear transient push before ROM reaches
Tails' CPU slot: hollow tree and the giant ride-vine/collapsing-platform bridge
(`sonic3k.asm:26696-26705,41668-41679,43649-43810,44784-44883,46481-46743,
46749-46950`). Ordinary local grace now falls through to FollowLeft/FollowRight.

### New AIZ frontier (frame 18645)

`tails_x mismatch (expected=0x493F, actual=0x7F00)`. Player and camera match at
the first error. ROM still has Tails visible near the AIZ2 end boss/log-bridge
area with `status=$02`, while the engine has parked Tails at `$7F00` after the
same local follow-steering section. Context points at a later sidekick
despawn/parking or end-boss/log-bridge handoff issue, not the frame-10586 stale
push-grace branch.

## 2026-05-23 - S3K AIZ/CNZ sidekick panic cadence reconciliation (AIZ f9264 -> f10586)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cadence guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#s3kPanicReleaseGateUsesLevelFrameCounterLowByte+aizPanicReleaseGateUsesRomVisibleReloadCounterBridge+normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne" test "-DfailIfNoTests=false"`
  - PASS: 3 tests.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ advances from frame 9264 `tails_y expected=0x045C actual=0x045B`
    back to frame 10586 `tails_x expected=0x2071 actual=0x2070`.
  - New count: 814 errors / 23 warnings.

### Root cause

ROM `TailsCPU_Panic` reads the low byte at `Level_frame_counter+1`; the `+1`
is the 68000 low-byte address, not an added frame
(`sonic3k.asm:26869-26884`; S2 equivalent `s2.asm:39122-39139`). CNZ therefore
must keep using the stored ROM-visible word: `$22FF` holds DOWN and `$2300`
releases.

AIZ after the Act 2 seamless reload is a separate engine scheduling gap already
covered by the AIZ normal/catch-up counter bridges: the stored engine level
counter is one tick behind the ROM-visible value during the sidekick CPU slot,
while ROM `LevelLoop` increments `Level_frame_counter` before `Process_Sprites`
(`sonic3k.asm:7888-7894`). The panic release/rev gate now uses that AIZ-local
ROM-visible bridge without changing CNZ/MGZ stored-counter cadence.

### New AIZ frontier (frame 10586)

`tails_x mismatch (expected=0x2071, actual=0x2070)`. Player and camera match at
the first error. Tails is near the AIZ Act 2 spiked-log/water-splash cluster;
ROM has `branch=fallthrough_sub20`, delayed input `0808`, and post-CPU speed
`$0080`, while the engine has `branch=grace_push_bypass`, delayed input `0004`,
and post-physics ground speed `$FFDC`.

## 2026-05-23 - S3K CNZ sidekick panic low-byte cadence (regression f8958 -> f11761)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Regression guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#s3kPanicReleaseGateUsesLevelFrameCounterLowByte" test "-DfailIfNoTests=false"`
  - PASS: 1 test.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: the integrated f8958 regression is cleared. CNZ now reaches frame 11761
    `tails_y expected=0x00FC actual=0x00F1`.
  - New count: 2805 errors / 20 warnings.

### Root cause

The ObjectManager signed `object_control` reject is now constrained to the proven CNZCylinder
side-separation path: only `CNZCylinder` opts into `rejectsBit7ObjectControlSideContact`, and the
shared check runs only in side-contact resolution. It no longer suppresses normal Door support/riding
contacts.

The frame-8958 Door symptom was a separate one-frame sidekick CPU cadence error. ROM `TailsCPU_Panic`
keeps DOWN held while `(Level_frame_counter+1) & $7F != 0` and only releases when that low byte is zero
(`sonic3k.asm:26869-26884`). The `+1` is the 68000 address of the word's low byte, not an added frame.
At CNZ f8958 the ROM-visible counter is `$22FF`, so Tails must stay in panic/spindash for one more
frame and release at `$2300`. The engine had interpreted that as `frameCounter + 1`, releasing
`Tails_Spindash` early; that path adds one pixel to `y_pos` and sets rolling on release
(`sonic3k.asm:28741-28781`), matching the regressed `tails_y=0x0231`.

### New CNZ frontier (frame 11761)

The next first error is back in the late CNZ balloon/cage/cylinder cluster. Player/camera and Tails X
match at the first error; ROM has Tails airborne at `$1F45,$00FC` with `object_control=$81`, while the
engine has Tails still marked on-object/support-like at `$1F45,$00F1`.

## 2026-05-23 - S3K CNZ circular-cylinder vertical contact anchor (CNZ f11503 -> f11752)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 15 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 11503
    `y expected=0x00EC actual=0x00ED` / `camera_y expected=0x008C actual=0x008D`
    to frame 11752 `tails_x expected=0x1F38 actual=0x1F35`.
  - New count: 2805 errors / 20 warnings.

### Root cause

Frame 11503 was not the active `CNZCylinder` rider-slot path. The engine diagnostics had no
`ObjectManager` ride/standing snapshot, but the player was still object-controlled and receiving a
fresh full-solid support contact from the nearby circular CNZ cylinder. The cylinder's current center
had already advanced vertically from `$0120` to `$0121`, while the ROM-visible object position used by
the same-frame `SolidObjectFull` support write was still `$0120`.

ROM `Obj_CNZCylinder` runs `sub_321E2`, then P1/P2 `sub_324C0`, then `SolidObjectFull`
(`sonic3k.asm:67656-67672`). The circular `loc_323EC` path writes `y_pos(a0)` for quadrant movement
while X can remain unchanged (`sonic3k.asm:67939-67958`), and `MvSonicOnPtfm` writes rider `y_pos`
from the object's `y_pos(a0)` (`sonic3k.asm:41674-41679`). The fix is local to `CNZCylinder`:
object-controlled, non-CPU, circular solid contacts use the saved frame-entry anchor only for the
proven Y-only positive step (`centerX == preUpdateX && centerY > preUpdateY`). Active held-rider X
paths and CPU sidekick paths keep their existing anchors.

### New CNZ frontier (frame 11752)

The new first error is Tails X: ROM has Tails at `$1F38`, engine at `$1F35`, with matching Tails Y and
zero velocities. Context points at the balloon/cylinder/cage cluster after a sidekick despawn marker;
main player/camera are aligned at this frontier.

## 2026-05-23 - S3K CNZ wide-cylinder CPU held anchor (CNZ f10927 -> f10967)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 11 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10927
    `tails_x expected=0x1A21 actual=0x1A20` to frame 10967
    `tails_y expected=0x0130 actual=0x0131`.
  - New count: 3518 errors / 30 warnings.

### Root cause

Frame 10927 was still in the `Obj_CNZCylinder` active held-rider path for subtype `$42`.
ROM runs `sub_321E2`, then P1/P2 `sub_324C0`, then `SolidObjectFull` in one object pass
(`sonic3k.asm:67656-67672`). The subtype `$42` wide horizontal path at `loc_3230E` writes
`x_pos(a0) = $2E(a0) + (sin(angle)>>2)` before incrementing the angle (`sonic3k.asm:67818-67825`).
The held-rider path at `loc_32538` then multiplies the stored distance word by the twist cosine and
adds `x_pos(a0)` to write the rider X (`sonic3k.asm:68019-68038`).

At frame 10927, the ROM-visible cylinder anchor was `$1A0A`, matching the engine's saved frame-entry
anchor, while the engine's split update had already advanced the current cylinder center to `$1A09`.
The existing frame-entry bridge covered non-CPU riders on the post-peak negative step, but still forced
CPU sidekicks to use the current center. The fix extends the same subtype `$42`/post-peak bridge to
CPU-held riders, still local to `CNZCylinder`; the narrower subtype `$41` post-peak guard remains on
the current-anchor path.

### New CNZ frontier (frame 10967)

`tails_y mismatch (expected=0x0130, actual=0x0131)`. Player/camera and Tails X/speeds match at the
new first error. The cylinder slot has just cleared via `p2=release_jump`; ROM has Tails at `$19C7,$0130`
with `y_speed=-$0680`, while the engine has `$19C7,$0131` with the same velocity. This is a separate
post-release jump/Y snap issue after the held-anchor path.

## 2026-05-23 - S3K CNZ cylinder external-launch release (CNZ f10727 -> f10924)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10727 `air expected=1 actual=0`
    to frame 10924 `tails_x_speed expected=0x0000 actual=0x0018`.
  - New count: 3508 errors / 21 warnings.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 9 tests, 0 failures.
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzDirectedTraversalHeadless" test "-DfailIfNoTests=false"`
  - PASS: 24 tests, 0 failures.

### Root cause

Frame 10727 is a same-frame CNZ balloon/cylinder handoff. ROM `Obj_CNZBalloon`
calls `sub_317AE`, which writes `y_vel=-$0700`, sets `Status_InAir`, clears
roll/jump/push state, and clears `object_control` (`sonic3k.asm:66804-66810`).
`Obj_CNZCylinder` still runs later in object order and executes its active rider
path `sub_324C0/loc_32538`, which writes the held rider X/twist state
(`sonic3k.asm:67656-67672,68019-68038`). When that path reaches the release
exit, `loc_32604` clears only the cylinder's rider byte (`sonic3k.asm:68076-68078`);
it does not zero the player's external launch velocity.

The engine's CNZ balloon already applied the launch before the cylinder object
pass, but the active cylinder slot treated the still-latched rider as a normal
hold and zeroed `y_speed`, leaving Sonic grounded. The fix is local to
`CNZCylinder`: when an active rider is already airborne and no longer
object-controlled, apply only the ROM held-X/twist write, clear the stale
cylinder support/slot, and preserve the external launch state.

### New CNZ frontier (frame 10924)

`tails_x_speed mismatch (expected=0x0000, actual=0x0018)`. Sonic/player and
camera match at the new frontier. Tails is near the next CNZ cylinder/monitor
cluster; ROM has Tails not on object with zero velocity, while the engine has
Tails airborne/off-object with a small residual `x_speed=+$0018`.

## 2026-05-23 - S3K CNZ wide-cylinder post-peak held anchor (CNZ f10637 -> f10727)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10637 `x expected=0x1A14 actual=0x1A13`
    to frame 10727 `air expected=1 actual=0`.
  - New count: 3329 errors / 13 warnings.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance,com.openggf.tests.TestS3kCnzDirectedTraversalHeadless" test "-DfailIfNoTests=false"`
  - PASS: 32 tests, 0 failures.

### Root cause

Frame 10637 was still in `Obj_CNZCylinder` active held-rider positioning, after the
previous subtype `$42` positive-step bridge. ROM `loc_3230E` computes the wide
horizontal cylinder's `x_pos` with `sin(angle)>>2` before adding the angle step
(`sonic3k.asm:67818-67825`), and `Obj_CNZCylinder` then calls P1/P2 `sub_324C0`
before `SolidObjectFull` in the same object routine (`sonic3k.asm:67656-67672`).
The active rider path at `loc_32538` multiplies the stored distance word by the
twist cosine and adds `x_pos(a0)` to write the rider X (`sonic3k.asm:68019-68038`).

At frame 10637 the ROM slot 18 cylinder and the engine's frame-entry cylinder
anchor were both `$1A20`, while the engine's current center had already stepped
down to `$1A1F`. Applying the existing positive-step frame-entry bridge only on
the increasing half left Sonic one pixel left on the first post-peak held frame.
The fix extends that bridge only for the wide horizontal `$42`/`loc_3230E`
negative step. The narrower `$41`/`loc_322F0` path remains on the current anchor
for post-peak frames; a guard covers the frame-4321 route where using the
frame-entry anchor would reintroduce `x actual=0x1BC8`.

### New CNZ frontier (frame 10727)

`air mismatch (expected=1, actual=0)`. Sonic's X/Y and camera are aligned through
frame 10726. At frame 10727 ROM clears on-object and launches Sonic upward with
`y_speed=-$0700` near the popped CNZ balloon/cylinder cluster, while the engine
keeps him grounded on slot 17/CNZCylinder with `y_speed=0`. This is a separate
CNZ cylinder/balloon release or support-clear timing issue after the held path.

## 2026-05-23 - S3K CNZ cylinder non-CPU held-rider anchor (CNZ f10614 -> f10637)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10614 `x expected=0x19F4 actual=0x19F3`
    to frame 10637 `x expected=0x1A14 actual=0x1A13`.
  - New count: 3396 errors / 14 warnings.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance,com.openggf.tests.TestS3kCnzDirectedTraversalHeadless" test "-DfailIfNoTests=false"`
  - PASS: 30 tests, 0 failures.

### Root cause

Frame 10614 was in the active CNZCylinder held-rider path, not the free-contact
side push path fixed at frame 10541. ROM `Obj_CNZCylinder` runs `sub_321E2`,
then P1/P2 `sub_324C0`, then `SolidObjectFull` in one object pass
(`sonic3k.asm:67656-67672`). In the active rider path, `loc_32538` computes the
held X from the stored distance word at `2(a2)` plus `x_pos(a0)`, then advances
the twist angle (`sonic3k.asm:68019-68038`).

The engine's split object/solid pipeline can consume the first non-CPU standing
callback after the horizontal oscillator has advanced one extra positive X step.
That made the stored first-capture distance and the first held-frame anchor
disagree by one pixel for Sonic on subtype `$42`. The fix keeps the ROM
distance calculation local to `CNZCylinder`: deferred first capture uses the
frame-entry X only when that is the closer ROM standing-bit anchor, and active
non-CPU held frames use the frame-entry X only for positive horizontal steps.
CPU sidekick holds remain on the current-center path because the engine already
has CPU-before-object nudge compensation for P2, and applying the same anchor to
CPU Tails regressed the earlier subtype `$41` route.

### New CNZ frontier (frame 10637)

`x mismatch (expected=0x1A14, actual=0x1A13)`. Sonic remains object-controlled
on the same CNZCylinder path; the next issue is still local to the later held
or release ordering around the cylinder/balloon/monitor cluster, with camera X
following the one-pixel player drift.

## 2026-05-23 - S3K CNZ horizontal cylinder free-contact anchor (CNZ f10541 -> f10614)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10541 `x expected=0x19DD actual=0x19DF`
    to frame 10614 `x expected=0x19F4 actual=0x19F3`.
  - New count: 2904 errors / 14 warnings.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance,com.openggf.tests.TestS3kCnzDirectedTraversalHeadless" test "-DfailIfNoTests=false"`
  - PASS: 29 tests, 0 failures.

### Root cause

Frame 10541 was a new/free side-contact with horizontal CNZCylinder subtype `$42`. The trace-visible
ROM cylinder slot 18 was at `$19B2,$0160`; the engine had the same cylinder's previous position at
`$19B2` but had already advanced the body to `$19B4` before the split inline solid checkpoint. With
`SolidObject_cont` side separation (`sonic3k.asm:41394-41407,41488-41495`), anchoring the side push
at `$19B4` pushes Sonic two pixels too far right. Subtype `$42` maps to the horizontal oscillator path
that computes `x_pos` from `sin(angle)>>2` before incrementing the angle (`sonic3k.asm:67818-67825`).

Fix: only horizontal CNZCylinder free contacts use the saved pre-update contact anchor in the engine
checkpoint. Captured/object-controlled riders still use the current post-motion anchor because
`Obj_CNZCylinder` runs `sub_324C0` before `SolidObjectFull`, and that rider path writes the held
position from current `x_pos(a0)` (`sonic3k.asm:67656-67672,67985-68038`). CPU sidekick contacts are
also left on the verified current-anchor path to preserve the earlier accepted cylinder-release
frontiers.

### New CNZ frontier (frame 10614)

`x mismatch (expected=0x19F4, actual=0x19F3)`. Sonic is now object-controlled/riding the same
horizontal cylinder; the cylinder center itself matches ROM at `$1A16,$0160`, but the held rider X is
one pixel left. This points at the CNZCylinder `sub_324C0` held-rider distance/angle carry path, not
the free-contact side separation fixed here.

## 2026-05-23 - S3K AIZ miniboss results camera lock handoff (AIZ f8839 -> f8941)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Commands:
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2MinibossResultsHandoffKeepsArenaCameraLocked" test "-DfailIfNoTests=false"` - RED before the fix: frame 8839 had `Camera_min_X_pos=$0000`, proving the results handoff restored full AIZ bounds too early.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2MinibossResultsHandoffKeepsArenaCameraLocked" test "-DfailIfNoTests=false"` - PASS after the fix, 1 test.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.game.sonic3k.objects.TestAizMinibossCameraUnlock" "-DfailIfNoTests=false"` - PASS, 1 test.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"` - FAIL at new frontier.
- Result: AIZ advanced from frame 8839 `camera_x expected=0x10E0 actual=0x10F8`
  to frame 8941 `camera_y expected=0x02C1 actual=0x02B8`
  (703 errors / 24 warnings). Player/Tails state still match at the new first
  error; the remaining mismatch is the post-title-card vertical camera/level-size
  release phase.
- Root cause/evidence:
  - Engine results exit restored full AIZ level camera bounds during the Act 1
    miniboss results-to-title-card handoff, and `AizMinibossInstance` began its
    unlock path while the in-level Act 2 title card was only exiting.
  - ROM `Obj_AIZMiniboss` locks `Camera_min_X_pos` and `Camera_max_X_pos` to
    `$10E0` through `loc_68556` for the Sonic fight
    (`sonic3k.asm:137251-137266,136774-136780`).
  - ROM `Obj_LevelResults` Act 1 path changes the results object into
    `Obj_TitleCard` without changing camera bounds
    (`sonic3k.asm:62708-62720`). The in-level title card sets
    `End_of_level_flag` only after its elements disappear
    (`sonic3k.asm:62276-62279`), and only then does
    `Obj_EndSignControlDoStart` call `Change_Act2Sizes`
    (`sonic3k.asm:180415-180419,180575-180609`).
- New frontier: frame 8941 camera-Y mismatch in the same AIZ miniboss/results
  handoff. ROM begins moving camera Y upward (`$02C1`) while engine remains
  clamped at `$02B8`; X is still locked at `$10E0`.

## 2026-05-23 - S3K AIZ2 reload sidekick fallthrough auto-jump cadence (AIZ f7082 -> f8839)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Commands:
  - `javac --release 21 -encoding UTF-8 -classpath "<target/classes;target/test-classes;deps>" -d target/classes src/main/java/com/openggf/sprites/playable/SidekickCpuController.java` and matching `javac` for `TestS3kAizTraceReplay` - PASS. This scoped compile avoided unrelated dirty-workspace lifecycle failures.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadSidekickFallthroughAutoJumpUsesRomVisibleCounter" "-DfailIfNoTests=false"` - PASS, 1 test.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadSidekickUsesRomVisiblePushAutoJumpCadence+aiz2ReloadSidekickCatchUpGateUsesRomVisibleCounter+aiz2ReloadSidekickFallthroughAutoJumpUsesRomVisibleCounter" "-DfailIfNoTests=false"` - PASS, 3 tests.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"` - FAIL at new frontier.
- Result: AIZ advanced from frame 7082 `tails_air expected=1 actual=0`
  to frame 8839 `camera_x expected=0x10E0 actual=0x10F8`
  (761 errors / 25 warnings). Player and Tails state match at the new first
  error; the mismatch is the AIZ2 miniboss/end camera lock release.
- Root cause/evidence:
  - Frame 7082 was the normal `Tails_Normal` fallthrough path, not the
    `sub_13ECA` catch-up marker path. ROM had `Level_frame_counter=$1A80`,
    Tails entered airborne/rolling with `y_vel=-$0680`, and engine diagnostics
    showed the stored counter one tick behind, so the same jump happened on
    frame 7083.
  - `LevelLoop` increments `Level_frame_counter` before `Process_Sprites`
    (`sonic3k.asm:7888-7894`). `Tails_Normal` reads the delayed
    `Pos_table`/`Stat_table` sample (`sonic3k.asm:26683-26700`), then the
    normal distance/height path at `loc_13E7C` reads the visible frame counter
    low byte (`sonic3k.asm:26760-26765`). `loc_13E9C` reads
    `Level_frame_counter+1`, masks the low six bits, ORs jump buttons into
    `Ctrl_2_logical`, and sets `Tails_CPU_auto_jump_flag`
    (`sonic3k.asm:26775-26785`).
  - The fix makes AIZ normal auto-jump cadence use that ROM-visible counter.
    It is deliberately separate from the AIZ-only `sub_13ECA` marker bridge
    controlled by `catchUpUsesRomVisibleLevelFrameCounter`, so CNZ/MGZ marker
    cadence remains on the stored-counter path.
- New frontier: frame 8839 camera-only mismatch after the AIZ2 miniboss area.
  ROM holds camera at `$10E0,$02B8` with the player/Tails parked at the
  miniboss/end setup; the engine has released camera/object-control state and
  moves to `$10F8,$02BE`.

## 2026-05-23 - S3K AIZ2 reload sidekick catch-up gate cadence (AIZ f6313 -> f7082)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Commands:
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadSidekickCatchUpGateUsesRomVisibleCounter" test "-DfailIfNoTests=false"` - RED before the fix: Tails was still parked at `$7F00,0000` with `x_speed/g_speed=$0445` on frame 6313.
  - `mvn "-Dmse=off" compile "-Dmaven.compiler.failOnError=false"` - used to repopulate `target/classes` because the normal lifecycle is currently blocked by an unrelated dirty-workspace compile error in `PlayableSpriteMovement#getStatusByte`.
  - `javac --release 21 -encoding UTF-8 -classpath "<target/classes;target/test-classes;deps>" -d target/classes src/main/java/com/openggf/sprites/playable/SidekickCpuController.java` and matching `javac` for `TestS3kAizTraceReplay`.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadSidekickCatchUpGateUsesRomVisibleCounter" "-DfailIfNoTests=false"` - PASS, 1 test.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadResumeAppliesRomCameraLock+aiz2FireRevealReleasesReloadCameraLockOnRomFrame+aiz2ReloadSidekickUsesRomVisiblePushAutoJumpCadence+aiz2ReloadSidekickCatchUpGateUsesRomVisibleCounter" "-DfailIfNoTests=false"` - PASS, 4 tests.
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"` - FAIL at new frontier.
- Result: AIZ advanced from frame 6313 `tails_x_speed expected=0x0000 actual=0x0445`
  to frame 7082 `tails_air expected=1 actual=0` (938 errors / 25 warnings).
- Root cause/evidence:
  - ROM `sub_13ECA` parks Tails by setting `Tails_CPU_routine=2`,
    `object_control=$81`, `status=Status_InAir`, `x_pos=$7F00`, and `y_pos=0`;
    it deliberately does not clear `x_vel`, `y_vel`, or `ground_vel`
    (`sonic3k.asm:26800-26809`). The stale `$0445` velocity at frames
    6255-6312 was therefore correct.
  - ROM `Tails_Catch_Up_Flying` routine 2 reads `Level_frame_counter`, masks
    the low six bits, and when the gate fires reaches `loc_13B50`, which snaps
    Tails to Sonic, writes `y_pos=Sonic.y-$C0`, clears `x_vel/y_vel/ground_vel`,
    and enters routine 4 (`sonic3k.asm:26474-26511`).
  - At AIZ2 post-reload frame 6313 the trace has `Level_frame_counter=$1780`,
    so ROM fires the gate and clears speed. The engine evaluated the stored
    counter one tick behind and fired at frame 6314, after the leader's
    pre-object position had changed. The existing AIZ intro marker already
    needed the ROM-visible counter bridge; this fix scopes the same marker-gate
    bridge to AIZ zone only. CNZ/MGZ and other normal `sub_13ECA` marker cadence
    still use the stored counter.
- New frontier: frame 7082 sidekick-only auto-jump/air mismatch after
  `aiz2_reload_resume`. Player and camera match. ROM Tails has just set
  airborne/rolling with `y_speed=-$0680` from the normal CPU fallthrough path
  at `Level_frame_counter=$1A80`, while the engine remains grounded and jumps
  one frame later.

## 2026-05-23 - S3K AIZ2 reload sidekick push auto-jump cadence (AIZ f5736 -> f6313)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Commands:
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadSidekickUsesRomVisiblePushAutoJumpCadence" test "-DfailIfNoTests=false"` - RED before the fix: Tails stayed grounded in the AIZ2 post-reload current-push bypass; PASS after the fix, 1 test.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadResumeAppliesRomCameraLock+aiz2FireRevealReleasesReloadCameraLockOnRomFrame+aiz2ReloadSidekickUsesRomVisiblePushAutoJumpCadence" test "-DfailIfNoTests=false"` - PASS, 3 tests.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"` - FAIL at new frontier.
- Result: AIZ advanced from frame 5736 `tails_air expected=1 actual=0`
  to frame 6313 `tails_x_speed expected=0x0000 actual=0x0445`
  (962 errors / 25 warnings).
- Root cause/evidence:
  - In the fresh integrated repro, the first failing sidekick branch was the
    normal-state `current_push_bypass` path after `aiz2_reload_resume`. ROM
    Tails jumped because the frame counter visible to `loc_13E9C` was `$1540`,
    while the engine evaluated the stored counter `$153F`.
  - `LevelLoop` increments `Level_frame_counter` before `Process_Sprites`
    (`sonic3k.asm:7888-7894`). `Tails_Normal` reads delayed position/status,
    then tests Tails' current `Status_Push`; when current push is set and the
    delayed push bit is clear it branches directly to `loc_13E9C`
    (`sonic3k.asm:26683-26705`). `loc_13E9C` tests the low six bits of
    `Level_frame_counter+1` and ORs jump buttons into `Ctrl_2_logical` when
    the cadence hits (`sonic3k.asm:26775-26785`).
  - The engine already had an AIZ object-order bridge for this ROM-visible
    cadence in earlier AIZ Act 1 handoffs. The AIZ2 post-reload push-bypass is
    the same zone-local ordering case, so the fix broadens that auto-jump
    counter bridge from AIZ Act 1 to AIZ zone only. Normal `sub_13ECA` despawn
    markers still use the stored frame-counter cadence outside the separate
    AIZ1 dormant-marker override (`sonic3k.asm:26478-26488,26800-26809`).
- New frontier: frame 6313 sidekick-only despawn/parking velocity mismatch.
  Player and camera match; ROM Tails is parked/visible near the ride-vine area
  with zero speed while the engine parks at `$7F00,0000` retaining
  `x_speed/g_speed=$0445`.

## 2026-05-23 - S3K AIZ2 reload camera lock/reveal release (AIZ f5497 -> f5736)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Commands:
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2ReloadResumeAppliesRomCameraLock" test "-DfailIfNoTests=false"` - PASS, 1 test.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aiz2FireRevealReleasesReloadCameraLockOnRomFrame" test "-DfailIfNoTests=false"` - PASS, 1 test.
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"` - FAIL at new frontier.
- Result: AIZ advanced from frame 5497 camera mismatch after `aiz2_reload_resume`
  to frame 5736 `tails_air expected=1 actual=0` (1023 errors / 25 warnings).
- Root cause/evidence:
  - `AIZ1BGE_Finish` subtracts `$2F00/$80` from player/camera and writes
    `$0010/$0010` to `Camera_min_X_pos/Camera_max_X_pos`, plus `$0000/$0260`
    to `Camera_min_Y_pos/Camera_max_Y_pos` and target max Y `$0260`
    (`sonic3k.asm:104747-104762`; RAM order in
    `sonic3k.constants.asm:355-362`). The engine only preserved the offset
    camera position and target max Y, so the first post-reload camera update
    followed Sonic to `$0028/$0278` instead of holding `$0010/$0260`.
  - `AIZ2BGE_WaitFire` releases the X lock by writing
    `Camera_max_X_pos=$6000` when `Camera_Y_pos_BG_copy >= $0310`, after the
    `AIZ2BGE_FireRedraw`/row-draw gate (`sonic3k.asm:105031-105092`). The
    engine lacked that release; once added, the existing resumed fire-BG start
    released too early, so the AIZ2 resumed fire scroll start is `$0140` to
    align the `$0310` reveal with the ROM-visible frame.
- New frontier: player/camera match at frame 5736. The remaining first
  divergence is sidekick-only after AIZ2 resume: ROM Tails is airborne/rolling
  with `tails_y_speed=-0680`, while the engine Tails remains grounded in
  `current_push_bypass`.

## 2026-05-23 - S3K MGZ top-platform carry arithmetic (MGZ f3721 -> f3777)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Regression guard:
  `mvn "-Dmse=off" "-Dtest=TestS3kMgzTopPlatformParityHeadless" test "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - RED before the positive-centering arithmetic fix: `positiveCenteringKickUsesRomNegThenArithmeticShift`
    expected platform `yVel=-9`, actual `-8`.
  - RED before the later `$-100` band fix: `positiveCenteringKickAllowsRomOvershootPastMinus100Band`
    expected platform `yVel=-0x0108`, actual `-0x0100`.
  - PASS after fixes: 19 tests, 0 failures.
- Focused MGZ replay/guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kMgzF498AirRollPhysics,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `TestS3kMgzF498AirRollPhysics`: PASS, 1 test.
  - `TestS3kMgzTraceReplay#replayMatchesTrace`: FAIL at new frontier.
  - Result: MGZ frontier advanced from frame 3721 `y expected=0x0AA3 actual=0x0AA4`
    through frame 3757 `y_speed expected=-0102 actual=-00F8`, then to frame 3777
    `tails_y_speed expected=0x0038 actual=0x0000`.
  - New count: 4856 errors / 68 warnings.

### Root cause

`Obj_MGZTopPlatform` positive centering at `loc_35148` copies `x_vel` to `d0`, branches only if
negative, then executes `neg.w d0` before `asr.w #4` and adds that signed shifted value to
`y_vel` after the `$8` upward kick (`sonic3k.asm:71966-71974`). The engine divided the positive
`xVel` first and then subtracted it, so small positive velocities (`4`, `8`, etc.) contributed `0`
instead of the ROM's `-1`. This left the platform/player vertical body motion one pixel/frame late
at the old frame-3721 frontier.

The later frame-3757 mismatch was another clamp ordering error in the same centering routine. ROM
`loc_35148` and its mirrored negative branch `loc_3510A` subtract `$8`, compare the result against
`-$100`, and only then add the signed `x_vel >> 4` contribution (`sonic3k.asm:71943-71951,
71966-71974`). There is no post-add clamp, so a positive-centering case can overshoot from
`-$F8` to `-$108`. The engine clamped after the add and flattened this into `-$100`, making the
post-gravity player `y_speed` `-00F8` instead of the ROM's `-0102`.

The same pass also keeps MGZ carried-player word writes/subpixel motion aligned with the ROM:
`loc_34F84` snaps only `x_pos` and preserves player velocity/subpixels (`sonic3k.asm:71804-71817`),
`loc_35070` runs `MoveSprite2` on the grabbed player (`sonic3k.asm:71901-71904`), and `sub_35202`
only word-snaps carried-player `x_pos/y_pos` (`sonic3k.asm:72046-72058`). No trace data is written
into engine state.

### New MGZ frontier (frame 3777)

Player/platform positions, subpixels, and Sonic velocities now match through the old top-platform
frontier. At frame 3777 the first divergence is sidekick-only: ROM Tails has
`tails_y_speed=0x0038`, while the engine has `0x0000`. ROM diagnostics show Tails airborne with
`onObj=04` and `tailsInteract slot=4 ptr=B128 obj=0002413E @0CA0,098B`; the engine has Tails in
the normal despawn/follow branch, no ride object, and no velocity injection. The next owner is a
Tails/MGZ object interaction or sidekick route-state issue, not the Sonic top-platform centering
formula.

## 2026-05-23 - S3K AIZ/CNZ/MGZ delegated frontier pass

- Branch: delegated S3K frontier workspace.
- Commands:
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kAizIntroEventsHeadless,com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#aizIntroSidekickStaysAtCatchUpMarkerUntilRomGate,com.openggf.sprites.playable.TestSidekickCpuControllerRewindCapture" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kAizIntroEventsHeadless#releasedAizIntroSidekickUsesRomVisibleCounterAfterWaitingForCatchUpGate,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kMgzF498AirRollPhysics,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestAizGiantRideVineObjectInstance,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#giantRideVineGrabsPlayerOnRomFrameAfterPlatformCarry" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzBumperObjectInstance#touchVisibilityUsesRomCollisionListWindowFromAnchor,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#sidekickAutoJumpsOnRomFrameAfterGiantRideVineHandoff" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"`
  - `$cp = "target/classes;target/test-classes;" + (Get-Content target/classpath.txt); javac --release 21 -encoding UTF-8 -cp $cp -d target/test-classes src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java; javac --release 21 -encoding UTF-8 -cp $cp -d target/test-classes src/test/java/com/openggf/tests/trace/s3k/S3kReplayCheckpointDetector.java`
  - `mvn "-Dmse=off" compile surefire:test "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#sidekickAutoJumpsOnRomFrameAfterGiantRideVineHandoff" "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" compile surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" compile test-compile "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#hollowTreeCameraLockBecomesVisibleOnRomFrameAfterCapture" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#hollowTreeSidekickUsesRomVisibleAutoJumpCadenceOnReleaseFrame" test "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - `$cp = "target/classes;target/test-classes;" + (Get-Content target/classpath.txt); javac --release 21 -encoding UTF-8 -cp $cp -d target/classes src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java; javac --release 21 -encoding UTF-8 -cp $cp -d target/classes src/main/java/com/openggf/trace/replay/TraceReplayFixture.java; javac --release 21 -encoding UTF-8 -cp $cp -d target/test-classes src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java`
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#hollowTreeRideAppliesRomVerticalCameraMinimum" "-DfailIfNoTests=false"`
  - `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"`
- Result: AIZ and CNZ frontiers advanced. MGZ was evaluated and left unchanged
  because the first divergence points at upstream object slot occupancy rather
  than a bounded physics/object routine fix.
  Direct Surefire was used for the latest AIZ-only recheck because unrelated
  dirty shared-workspace test sources currently block a normal `testCompile`.

Current integrated verification facts:

| Trace | Frontier / first error | Outcome |
| --- | --- | --- |
| `TestS3kAizTraceReplay#replayMatchesTrace` | frame 5497, `camera_y` expected `0x0260`, actual `0x0278` (`camera_x` also expected `0x0010`, actual `0x0028`) | moved from frame 2465 `tails_x_speed`, frame 2696 `x_speed`, frame 2721 `tails_air`, frame 3170 `tails_y`, frame 4539 `camera_x`, frame 4540 `tails_x`, frame 4577 `tails_air`, then frame 4646 `camera_y` |
| `TestS3kCnzTraceReplay#replayMatchesTrace` | frame 8963, `tails_air` expected `0`, actual `1` | moved from frame 6568 `x_speed`, then frame 8123 `tails_x_speed` |
| `TestS3kMgzTraceReplay#replayMatchesTrace` | frame 2458, `tails_g_speed` expected `0x0000`, actual `-0024` | moved from frame 2395 `g_speed` after MGZ Spiker/Tunnelbot `Obj_WaitOffscreen` lifecycle fixes; current run reported 5214 errors / 64 warnings |

### MGZ frontier movement (frame 2395 -> 2458)

MGZ's frame-2395 player `g_speed` divergence is cleared. The upstream slot
pressure issue was a pair of MGZ badniks that allocated their SST slots from
the placement loader but ran their real state machines before the ROM would.
Both `Obj_Spiker` and `Obj_Tunnelbot` enter through `Obj_WaitOffscreen`, which
installs `loc_85AD2`, draws a `$20` offscreen marker, and restores the saved
operation pointer only after `render_flags` bit 7 is set
(`sonic3k.asm:180266-180298`). `Obj_Spiker` calls this before child creation
(`sonic3k.asm:185372-185397`), and `Obj_Tunnelbot` does the same before
`Swing_Setup1`/`CreateChild1_Normal` (`sonic3k.asm:184710-184734`).

The diagnostic window now matches the ROM lifecycle points: Spiker wrapper at
frame 2216, Spiker active/children at frames 2315/2316, Tunnelbot wrapper at
frame 2326, and Tunnelbot active/arms at frames 2362/2363. The remaining
frontier at frame 2458 is Tails' interaction with ROM Obj1A920 in slots s4/s21
while the engine still has different later SST pressure near the Tunnelbot and
MGZ smashing-pillar area; no trigger/platform execution-order override was
introduced.

### AIZ frontier movement (frame 2465 -> 4646)

Focused sidekick/AIZ/rewind guards passed. The old Tails marker release
mismatch is cleared. AIZ's pre-physics release bridge waits for committed
camera X rather than a preview-only threshold crossing. The AIZ-only catch-up
path uses the ROM-visible `Level_frame_counter` edge for the intro marker
release, while normal S3K `sub_13ECA` marker catch-up stays on the stored
counter cadence (`Tails_Catch_Up_Flying` masks `Level_frame_counter` directly
with no `+1`; sonic3k.asm:26478-26488).

The frame-2696 player/vine handoff is also cleared. `Obj_AIZGiantRideVine`
now samples `AIZ_vine_angle` on the same frame as the ROM object pass
(`Process_Sprites` before `ChangeRingFrame`, sonic3k.asm:7894,7910; angle
increment at sonic3k.asm:9693), and the giant-vine grab no longer starts the
first child's activated swing integrator. ROM `sub_220C2` only writes the
handle grab byte/player fields (sonic3k.asm:46731-46743); the first child
continues the passive `loc_2248A` path reading `AIZ_vine_angle`
(sonic3k.asm:46840-46854).

The frame-2721 post-vine sidekick auto-jump is now cleared. ROM `Tails_Normal`
uses the current-push bridge into `loc_13E9C`, then writes Ctrl 2 press bits
before the player normal-mode jump step (`sonic3k.asm:26702-26705,
26775-26785`); `LevelLoop` has already incremented `Level_frame_counter`
before `Process_Sprites` (`sonic3k.asm:7888-7894`). The engine's stored level
counter is one tick behind that ROM-visible view during AIZ's local
giant-vine/collapsing-platform push handoff, so the one-tick cadence bridge is
scoped to AIZ Act 1 NORMAL current-push bypass cadence. Normal `sub_13ECA`
markers still use stored cadence.

The frame-3170 sidekick auto-jump is now cleared by the same S3K
current-push cadence rule. ROM frame 3170 has `Level_frame_counter=$0B41`, so
the `loc_13E9C` low-6-bit gate does not jump; the engine had been evaluating
the stored `$0B40` counter and entered `Tails_Jump` one frame early. The
override remains limited to AIZ Act 1 NORMAL current-push bypass cadence; normal
`sub_13ECA` catch-up markers still use stored cadence, preserving the CNZ/MGZ
marker timing established above.

The frame-4539/4540 hollow-tree camera and sidekick boundary handoff is now
cleared. `Obj_AIZHollowTree` writes `Camera_min_X_pos` and `Camera_max_X_pos`
to `$2C60` immediately after Player 1 capture (`sonic3k.asm:43702-43704`),
and Tails' boundary routine reads `Camera_min_X_pos+$10` before clamping
`x_pos`, `x_vel`, and `ground_vel` (`sonic3k.asm:28414-28450`). The engine
keeps that boundary visible to sidekick physics while deferring only the
same-frame visible camera clamp so the camera still appears on the ROM frame
after capture.

The frame-4577 hollow-tree sidekick jump is now cleared. The ROM diagnostic
`input=$7878` is not movie jump input; the BK2 input is still RIGHT-only and
ROM `Tails_CPU_Control` has ORed the CPU auto-jump bits into the delayed
`Ctrl_2_logical` word at `loc_13E9C`. ROM frame 4577 has
`Level_frame_counter=$10C0`, so the low-6-bit gate fires before `Tails_Jump`
adds the angle-derived jump velocity and sets `Status_InAir|Status_Roll`
(`sonic3k.asm:26775-26785,28519-28568`). The engine was evaluating the stored
`$10BF` counter on the hollow-tree `leader_on_object` path and jumped one frame
late. The ROM-visible one-tick cadence bridge is still AIZ-only and now covers
only the proven local object-order cases: vine/platform current-push handoff and
the hollow-tree leader-on-object release path. Normal `sub_13ECA` markers keep
the stored counter cadence.

The frame-4646 hollow-tree vertical camera clamp is now cleared. ROM
`AIZ1_Resize loc_1C550` writes `Camera_min_Y_pos=0` after the max-Y table
scan, then raises it to `$02E0` once `Camera_X_pos >= $2C00`
(`sonic3k.asm:38939-38958`). The engine already used the ROM-phase
end-of-frame camera X for the AIZ1 max-Y/palette resize checks, but did not
port the matching min-Y write, leaving `Camera_min_Y_pos=0` and allowing the
camera to scroll one pixel above the ROM top clamp during the hollow-tree ride.

New AIZ frontier: frame 5497, `camera_y` expected `0x0260`, actual `0x0278`
and `camera_x` expected `0x0010`, actual `0x0028` (1026 errors / 25 warnings).
This is after the AIZ1 -> AIZ2 seamless reload checkpoint
`aiz2_reload_resume`; player and Tails state still match at the first error,
so the next bounded owner is AIZ act-transition camera restore/lock timing.

### CNZ frontier movement (frame 6568 -> 8963)

The frame-6568 `x_speed` delta was S3K speed-shoes expiry timing. S3K
`Sonic_ChgJumpDir` doubles acceleration while speed shoes are active
(sonic3k.asm:23088-23120), but `Sonic_Display` clears speed shoes and restores
movement constants before the next movement step reads them when the display
timer expires (sonic3k.asm:21540-21561,22067-22081). The S3K profile no longer
adds an extra pre-physics timer tick for this path.

The integrated conflict check also verified normal S3K `sub_13ECA` markers must
not use the AIZ one-tick override: with the override applied globally, CNZ
regressed to frame 830 (`tails_x_speed` expected `0x006A`, actual `0x0000`).
After scoping the override to AIZ Act 1, CNZ advances to frame 8123. The new
owner is likely a Tails CNZ bumper/object velocity injection, not marker
release cadence.

The frame-8123 bumper miss is now cleared. S3K `Obj_Bumper` adds CNZ bumpers
to `Collision_response_list` using the original anchor `$30(a0)`, not the
current orbit point: `(origin_x & $FF80) - Camera_X_pos_coarse_back <= $280`
(sonic3k.asm:68823-68830,68881-68886), with
`Camera_X_pos_coarse_back = (Camera_X_pos - $80) & $FF80`
(sonic3k.asm:37472-37478). `CnzBumperObjectInstance` now mirrors that
touch-list window.

New CNZ frontier: frame 8963, `tails_air` expected `0`, actual `1`. ROM has
Tails standing on object slot 6 / door object `0x3C @1040,0248`
(`onObj=true`, `objP2=true`), while the engine reports the door as no-touch
and Tails is airborne. Next owner is likely S3K CNZ door/platform sidekick
standing logic.

### MGZ evaluated, frontier unchanged

MGZ still first diverges at frame 1538 (`y` expected `0x0DC9`, actual
`0x0DC8`). The focused `TestS3kMgzF498AirRollPhysics` guard passes. Diagnostic
comparison shows the immediate cause is trigger-platform timing from object
slot order: in the ROM the trigger platforms occupy slots `s4`/`s5` and execute
before the dash trigger in `s21`; in the engine the dash trigger is `s6` and
the platforms are `s7`/`s8`, so the trigger is published before the platforms
run. This points at earlier MGZ SST slot occupancy / child-object lifetime
divergence, not an isolated air-roll or platform formula fix.

## 2026-05-21 - S3K AIZ/CNZ integrated object-physics verification refresh

- Branch: object physics standardization worktree
- Command: `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kAizIntroEventsHeadless,com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne,com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance#speedShoesEffectFeedsSameFrameAirAccelerationAfterContentUpdate,com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kSpeedShoesDoubleAirAccelerationAfterWallZeroing,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
- Result: focused guards pass for S3K speed-shoes air acceleration after wall zeroing,
  sidekick NORMAL auto-jump cadence using the ROM-visible level-frame counter without
  an inline `+1`, S3K monitor same-frame speed-shoes content update timing, and the
  AIZ headless intro/event coverage. AIZ and CNZ trace replays remain open failures.

Current integrated verification facts:

| Trace | Errors | Warnings | Frontier / first error | Owner hypothesis |
| --- | ---: | ---: | --- | --- |
| `TestS3kAizTraceReplay#replayMatchesTrace` | 1730 | 23 | frame 2465, `tails_x_speed` expected `0x0000`, actual `-01F9` | sidekick despawn/respawn marker release state, not terrain/object collision |
| `TestS3kCnzTraceReplay#replayMatchesTrace` | 4080 | 17 | frame 6568, `x_speed` expected `0x0320`, actual `0x0308` | latest CNZ frontier after S3K monitor timing; no collision fix landed for this movement |

### AIZ frontier state (frame 2465)

The engine sidekick is in the despawn marker state at `7F00,0000` with
`object_control=81` and stale velocities `FE07,022D`. The ROM has the sidekick
visible/parked around `1B0D,031C` with `status=02` and zero speed. The current
owner remains sidekick CPU marker release / respawn state handling.

### CNZ frontier state (frame 6568)

The CNZ frontier movement to frame 6568 came from S3K monitor timing, not a
collision fix. The first error remains `x_speed` (`expected=0x0320`,
`actual=0x0308`) with 4080 errors and 17 warnings.

## 2026-05-21 - S3K AIZ intro refresh NORMAL auto-jump cadence bridge

- Branch: object physics standardization worktree
- Command: `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kAizIntroEventsHeadless,com.openggf.sprites.playable.TestSidekickCpuFollowParity#normalAutoJumpCadenceUsesLevelFrameCounterWithoutInlinePlusOne,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
- Result: focused AIZ intro event tests and the global S3K normal-cadence guard passed; AIZ trace replay still fails later.
- Frontier moved: frame 2081 `tails_air` expected `1`, actual `0` -> frame 2465 `tails_x_speed` expected `0x0000`, actual `-01F9`.
- Fix note: AIZ intro normal-refresh frame-counter bridge now publishes per ROM-visible counter at the pre-physics terrain-refresh threshold, so stored `Level_frame_counter=$06FF` exposes ROM-visible `$0700` to the NORMAL auto-jump gate without changing global NORMAL cadence.

### New AIZ frontier (frame 2465)

`tails_x_speed mismatch (expected=0x0000, actual=-01F9)` after Tails has parked at the AIZ intro marker (`x=0x7F00,y=0`) and the ROM sidekick slot resumes at Sonic's position. Next owner likely remains sidekick despawn/respawn marker release state, not terrain/object collision.

## 2026-05-21 - S3K AIZ/CNZ integrated trace smoke verification

- Branch: object physics standardization worktree
- Command: `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance#speedShoesEffectFeedsSameFrameAirAccelerationAfterContentUpdate,com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kSpeedShoesDoubleAirAccelerationAfterWallZeroing,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
- Result: focused guards passed for monitor timing and S3K speed-shoes air
  acceleration after wall zeroing. Both trace replays still fail.
- AIZ update: non-diagnostic replay now fails first at frame 2081 on
  `tails_air` (`expected=1`, `actual=0`) with 1809 errors and 25 warnings.
  This verifies the frame-2017 sidekick CPU counter bridge blocker moved; the
  bridge consumed the intended ROM-visible counter 1728. The current owner
  hypothesis is `SidekickCpuController.updateNormal` auto-jump cadence /
  ROM-visible level counter edge for Tails normal-follow, not terrain/object
  collision.

Current integrated verification facts:

| Trace | Errors | Warnings | Frontier / first error | Owner hypothesis |
| --- | ---: | ---: | --- | --- |
| `TestS3kAizTraceReplay#replayMatchesTrace` | 1809 | 25 | frame 2081, `tails_air` expected `1`, actual `0` | `SidekickCpuController.updateNormal` auto-jump cadence / ROM-visible level counter edge for Tails normal-follow |
| `TestS3kCnzTraceReplay#replayMatchesTrace` | 4080 | 17 | frame 6568, `x_speed` expected `0x0320`, actual `0x0308` | latest CNZ frontier after integrated verification |

## 2026-05-20 - S2 MCZ Drawbridge (Obj81) landing position, zero-dist gate, half-width (MCZ2 frame 1774 -> 2226)

- Branch: `develop` worktree `agent-a1d6ae52d5b453e92`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1774 (806 errors) to frame 2226 (724 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` unchanged at frame 1085 (452 errors);
  `TestS2ArzLevelSelectTraceReplay` unchanged at frame 311 (868 errors, pre-existing Tails AI mismatch).

### Root causes

Three bugs in `MCZDrawbridgeObjectInstance` (Obj81 / `JmpTo22_SolidObject`):

**Bug 1 — halfWidth=64 too narrow (s2.asm:56578):**
`PARAMS_DOWN` used `halfWidth=64` (matching `width_pixels=$40` in the object header), but ROM
`loc_2A1A8` (`move.w #$4B,d1`) passes `d1=0x4B=75` to `JmpTo22_SolidObject`. The `$40=64`
value is used only for the secondary inner hit-width check inside `SolidObject_Landed`; the
primary detection/riding halfWidth is 75. With 64 the engine detached Sonic from the bridge
3px inside the left edge, causing the frame 1796 `air mismatch`.

**Bug 2 — zero-distance landing rejected:**
The global `topSolidLandingAllowsZeroDist=false` for S2 was modeled on `PlatformObject_ChkYRange`
(`s2.asm:35696-35712`). But Obj81 routes through `SolidObject_TopBottom` →
`SolidObject_Landed` (`s2.asm:35297-35308`), gated by `blo.s SolidObject_Landed` (unsigned
lower, includes `d3=0`). Zero-distance landings ARE valid for `SolidObject`-based objects.
Override `allowsZeroDistanceTopSolidLanding()` → `true` in `MCZDrawbridgeObjectInstance`.

**Bug 3 — PlatformObject snap formula overwriting correct SolidObject position:**
`applyNonUnifiedTopSolidLandingHeightOverride` implements `PlatformObject_ChkYRange`
(`anchorY - groundHalfHeight - yRadius - 1`). But `resolveContactInternal` already produces
the correct `SolidObject_Landed` result (`playerY - distY + 3 = 0x04AC`). The override was
then replacing it with the wrong `PlatformObject_ChkYRange` result (`0x04AB`).
Added `SolidObjectProvider.usesPlatformObjectLandingSnap()` (default `true`); override to
`false` in `MCZDrawbridgeObjectInstance`; added early-return guard in the override method.

### New MCZ2 frontier (frame 2226)

`y mismatch (expected=0x033E, actual=0x0340)` — Sonic lands on a Springboard (Obj40,
`SlopedSolidProvider`) 2px too low. This is a pre-existing sloped-solid landing formula
divergence separate from the drawbridge fixes.

## 2026-05-20 - S2 Tails y_radius preservation on non-rolling landing (MCZ2 frame 1290 -> 1487)

- Branch: `develop` worktree `agent-a6820a41fdf6642c1`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1290 (816 errors) to frame 1487 (773 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors,
  pre-existing); `TestS2Ehz1TraceReplay` still at frame 304 (813 errors, pre-existing);
  `TestS2ArzLevelSelectTraceReplay` passes (unchanged).

### Root cause

`ObjectManager.SolidContacts.clearRollingOnLanding()` had an else-if branch that called
`applyStandingRadii(false)` whenever a player landed with `rolling=false` and non-default
collision radii. This is S3K behavior (`Player_TouchFloor`, `sonic3k.asm:24341-24343` /
`29134-29136` — unconditionally resets y_radius/x_radius before testing `Status_Roll`).

S2 `Tails_ResetOnFloor` (`s2.asm:40624-40641`) uses `btst #Status_Roll; bne
Tails_ResetOnFloor_Part2`, gating the y_radius reset on `Status_Roll` being set. When not
rolling, the reset is skipped entirely, preserving any stale y_radius from the despawn path.

In MCZ2 at frame 1290, the `TailsCPU_Flying` respawn path (`s2.asm:38797`) clears rolling via
a direct status byte write (`Status_InAir`) without touching y_radius, leaving y_radius=14
(rollYRadius, not standYRadius=15). The spurious `applyStandingRadii(false)` reset y_radius
14→15 one pixel too early, raising Tails' ceiling probe by 1 px and producing a different
ceiling collision result from the ROM.

### Fix

Gated the non-rolling radius reset in `clearRollingOnLanding()` on
`featureSet.landingRollClearUsesCurrentYRadiusDelta()`, which is true only for S3K. S2 and S1
now skip the else-if branch entirely when not rolling, matching `Tails_ResetOnFloor` gate.

Files changed:
- `ObjectManager.java` (`SolidContacts.clearRollingOnLanding`) — gate added
- Debug prints removed from `PlayableSpriteMovement.java`, `CollisionSystem.java`,
  `AbstractPlayableSprite.java` (no logic change)
- `DebugMCZ2BlockDump.java` deleted (diagnostic test file)

### New MCZ2 frontier (frame 1487)

`air mismatch (expected=0, actual=1)` — Sonic is in the air in the engine but on the ground
in the ROM. At frame 1487, Sonic is riding SwingingPlatform slot 17 (x=0x06A0, y=0x05B4) in
the ROM. The engine SwingingPlatform position diverges due to OscillationManager byte drift,
a pre-existing SwingingPlatform oscillation issue separate from the fix in this commit. The
frame 1290 cascade (816 → 773 errors, 43-error improvement) was hidden behind the y_radius bug.

## 2026-05-19 - S2 SwingingPlatform (Obj15) out-of-range unload + CalcSine angle convention (MCZ2 frame 1009 -> 1290)

- Branch: `develop` worktree `agent-a262078995bf698e7`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1009 (909 errors) to frame 1290 (816 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors,
  pre-existing); `TestS2Ehz1TraceReplay` still at frame 304 (813 errors, pre-existing);
  `TestS2ArzLevelSelectTraceReplay` still at frame 563 (1482 errors, pre-existing).

### Root causes

Two independent bugs in `SwingingPlatformObjectInstance`:

**Bug 1 — Constructor position-based immediate unload:**
The constructor called `updatePositions(0)` to initialise the platform position.
At oscValue=0 with chainCount=8, `cos(0)=256` produces `xOffset = 256*136/256 = 136`.
So `this.x = baseX + 136`. For the MCZ2 spawn at baseX=0x0620, camera≈0x03C0:
`isOutOfRangeS1(0x06A8, 0x03C0) = (0x06A8 - 0x03C0 + 128) = 0x380 = 896 > 640` → immediate
unload. Marked dormant before first `update()`. The platform never became active.

Fix: removed `updatePositions(0)` from constructor; `this.x = baseX`, `this.y = baseY`.
Added `getOutOfRangeReferenceX()` override returning `baseX`, matching ROM's use of
`obX(a0)` (parent `x_pos` = spawn X = baseX during the out-of-range check in `loc_FE50`).

**Bug 2 — Wrong CalcSine angle convention (1-pixel X error):**
`updatePositions` used `swingAngle = (oscValue - 0x40) & 0xFF` and mapped `(-sin, cos)` to
`(X, Y)`. This assumed SINCOSLIST perfect antisymmetry. The ROM table is NOT antisymmetric:
SINCOSLIST[147] = -117 while SINCOSLIST[19] = 115 (verified against `sinewave.bin`).
At osc=0x53: ROM X offset = cos(0x53) * chainLength / 256 = -117*40/256 ≈ -19 px;
engine: -SINCOSLIST[0x13] * 40 / 256 = -115*40/256 ≈ -18 px. Off by 1 pixel.

Fix: call `calcSine(oscValue)` / `calcCosine(oscValue)` directly (matching ROM `CalcSine`),
then use sin→Y, cos→X. Verified exhaustively: 0 divergences over all 3840
(osc ∈ 0..255) × (chainCount ∈ 1..15) combinations against the ROM fixed-point simulation.

**Additional fix — BOUNCE clamp logic:**
The `updateBounceSwing` method previously had a `Math.abs(dx) < 0x20` player-proximity gate
that does not exist in ROM `sub_FE70` (s2.asm:22556-22586). Also, BOUNCE_LEFT threshold
was `oscValue < 0x40` (should be `< 0x3F`; osc==0x3F requires sound + clamp).
Both fixed to match ROM exactly.

### New MCZ2 frontier (frame 1290)

`tails_y mismatch (expected=0x04FE, actual=0x04FF)`. Tails is not riding the
expected SwingingPlatform (`ROM: onObj=23, engine: onObj=FFFFFFFF`). The engine's
nearby platform for Tails (s23 @06D7,054E) differs from the ROM's (s25 @068F,054B)
in X position (06D7 vs 068F). This is a separate sidekick-on-platform riding
issue; not in scope for this iter.

## 2026-05-19 - S2 SteamSpring (Obj42) bypasses offscreen sidekick gate (MTZ3 frame 460 -> 765)

- Branch: `develop` (HEAD `1b5308308`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=relaxed "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ3 frontier advanced from frame 460 to frame 765 (2626 -> 2601 errors).
- Regression check (run together): TestS2Mtz3 PASS-advance; TestS2Mtz (MTZ1)
  unchanged frame 375 tails_air (905 errors, pre-existing); TestS2Mtz2 unchanged
  frame 305 y mismatch (2325 errors, pre-existing); TestS2Mcz2 unchanged
  frame 1079 y mismatch (802 errors, pre-existing); TestS2Ehz1 PASS.
  Pre-fix baseline confirmed by `git stash` run of MTZ/MTZ2 -> same
  frontier+error counts (905 @ f375 and 2325 @ f305).

### Root cause

ROM `Obj42` (SteamSpring) routine 2 (`loc_26688`, `s2.asm:52030-52049`) calls
`SolidObject_Always_SingleCharacter` for BOTH Sonic and Tails with no
intervening on-screen check:

```
loc_26688:
    ; Sonic call
    moveq #p1_standing_bit,d6
    jsrto JmpTo2_SolidObject_Always_SingleCharacter
    ; Tails call
    lea (Sidekick).w,a1
    moveq #p2_standing_bit,d6
    jsrto JmpTo2_SolidObject_Always_SingleCharacter   ; no on_screen gate
```

This is the same pattern documented as pitfall **P27**
(`.agents/skills/s2-implement-object/rom-pitfalls.md:1214-1245`):
`SolidObject_Always_SingleCharacter` jumps directly to `SolidObject_cont`
(`s2.asm:35147`) without the `SolidObject_OnScreenTest` gate at
`s2.asm:35140-35145`, and the regular `SolidObject` P2 prologue's
`render_flags.on_screen` check at `s2.asm:34825-34828` is not on this
code path.

The engine still applied `shouldSkipOffscreenSidekickFullSolid`
(`ObjectManager.java:7861`) to the SteamSpring's P2 contact pass, returning
early before reaching the new-landing detection in
`processInlineObjectForPlayer`. The gate only short-circuits when the
sidekick is off-screen for render purposes; at MTZ3 f460 Tails is at
`(0x0328, 0x02D0)` and the camera is at `(0x0282, 0x018C)`. With a 32-pixel
Y margin and a 224-pixel viewport, Tails' `relY = 0x143 = 323` exceeds the
`height + margin = 256` limit, so the gate fires. The SteamSpring at
`(0x0330, 0x02F0)` with `half_height = 0x10` exposes a landing surface at
`y = 0x02E0`; ROM lands Tails exactly there (`y = 0x02D0` with
`half_height = 0x10` puts her bottom on the surface). Engine Tails kept
falling (`y_speed = 0x0450` vs ROM's snap-to-0).

### Fix

`SteamSpringObjectInstance` now overrides `bypassesOffscreenSolidGate()`
to return `true`, mirroring `SpringObjectInstance` and
`LauncherSpringObjectInstance`. The override is per-object as required by
P27 step 3 -- the regular `SolidObject` P2 path still gates on sidekick
render state for objects that don't use the `Always` variant.

### Noted-but-not-applied (P27 follow-up candidates)

S2 callers of `SolidObject_Always_SingleCharacter` from `s2.asm` that lack
the override in the engine today:

- Obj66 (MTZSpringWall) -- `s2.asm:52805/52824` (`Obj66_Main`).
- Obj7B (PipeExitSpring) -- `s2.asm:55919/55927` (`Obj7B_Main`).
- Obj86 (Flipper) -- `s2.asm:58002/58011` (`Obj86_HorizontalType`).
- ObjD6 (PointPokey) -- `s2.asm:58597` (`ObjD6_Main`).

These are candidates for follow-up iters when their zone's trace frontier
points at sidekick missed-landing or missed-side-collision behaviour. Not
applied here per `docs/prompts/trace-frontier-advancement.md` rule 7
(minimum-viable-change).

### MCZ2 frontier (f1079) -- still blocked, not advanced this iter

MCZ2's first error is at frame 1079 with `y mismatch (expected=0x05B4,
actual=0x05A9)`. Sonic stands on a SwingingPlatform (`Obj15`); ROM's
`onObj=10` (slot 16) vs engine `onSlot=23(0x15)`. Both engine and ROM
have multiple SwingingPlatforms at similar positions, but the slot
assignment differs (engine slot 17 @0620,05D0 matches ROM slot 16; engine
slot 23 @061C,05C5 matches ROM slot 17 @0683,05C5 by y but the engine x
is 0x67 px west of the ROM x). The 5-pixel y discrepancy and 0x67-pixel
x discrepancy suggest the engine and ROM are riding *different* swinging
platforms with different rotation phases or different chain configurations.
This is a deeper SwingingPlatform parity issue that needs dedicated
investigation; not in scope for this iter.

Persistent ledger for trace replay frontier work. Update this file whenever a
trace fix is committed, a frontier moves, a previously passing trace regresses,
or a full `*TraceReplay` sweep is run to choose the next target.

## 2026-05-19 - S2 CNZ2 Crawl closer-player investigation (frontier unchanged at f1490)

- Branch: `develop` (HEAD `1b5308308`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: unchanged, 1057 errors, frame 1490 `tails_y_speed` mismatch
- Outcome: closer-player fix attempted and reverted (see below); frontier log updated only

### Investigation summary

CNZ2 frame 1490 root cause: Crawl (`ObjC8`, slot s17 at `@0735,0470`) is in ATTACKING state
(`rtn=06`) in the ROM but stays in ENEMY (`col=$17`) in the engine. ROM `Obj_GetOrientationToPlayer`
(`s2.asm:72320-72346`) picks the **closer** of Sonic and Tails by absolute X distance (`mvabs.w`
+ `bls.s`). At frame 1490, Tails (dx≈0x003D) is closer to the second Crawl than Sonic (standing
at `@0776`), so the ROM's `loc_3D416` proximity check fires on Tails → Crawl enters ATTACKING.
Engine only passes the main player (Sonic) to `updateWalking`/`updatePausing` proximity checks →
ATTACKING never fires for Tails-triggered proximity → Crawl stays ENEMY → no Tails y_speed change.

### Closer-player fix attempt

Added `closestPlayer(mainPlayer)` helper in `CrawlBadnikInstance` that walks `services().sidekicks()`
and returns the player with the smaller `|getCentreX() - currentX|`, matching the ROM's `mvabs.w +
bls.s` logic (sidekick wins only if **strictly** closer). Also fixed `updatePausing()` to accept
and use the closest player for the proximity check (ROM `loc_3D2A6` calls `loc_3D416` before the
timer-expiry branch), and corrected the pause-timer comparison from `<= 0` to `< 0` to match ROM's
`bmi` (fires when result is negative, not zero).

Result: frontier moved from **f1490 → f630** (1057 → 1096 errors). The fix correctly enters
ATTACKING at the right frame for the second Crawl (Tails trigger at f1490), but introduces a new
first-error at f630.

### Frame 630 regression analysis

At frame 630, a **different Crawl** (slot s20, the first CNZ2 Crawl at X≈0x032E/0x032F) bounces
Sonic. The bounce happened in the ROM too (ROM rtn=06 at f630, Sonic velocity changes). But the
engine Crawl s20 is 1 pixel further right (X=0x032F) than the ROM Crawl (X=0x032E), causing a
different `calcAngle(dx, dy)` → different bounce velocities (`y_speed: expected=-0666, actual=-0682`).

**Key findings:**
1. The 1px X position drift in Crawl s20 (`@032F` engine vs `@032E` ROM) is **pre-existing** —
   it exists in the original code too (confirmed by reverting the fix and observing the same
   Crawl position in the diagnostic text). The drift is invisible in the original code because
   Sonic never reaches s20 with the right trajectory to trigger a bounce.
2. The second Crawl (s17) also has a 3px X drift (`@0732` engine vs `@0735` ROM), also pre-existing.
3. Both Crawl position drifts are NOT caused by the closer-player fix. They are a separate bug in
   `CrawlBadnikInstance`'s subpixel movement accumulation or spawn-position initialization.
4. The closer-player fix is ROM-accurate per `Obj_GetOrientationToPlayer` but cannot land until
   the Crawl X-position drift is diagnosed and fixed.

### Crawl X-position drift: candidate root causes

The ROM's `ObjectMove` (s2.asm:29994-30008) uses **32-bit fixed-point**:
- Loads `(x_pos << 16 | x_sub)` as 32-bit value `d2`
- `ext.l; asl.l #8` on x_vel → for vel=0x0020, becomes 0x00002000
- `add.l d0, d2` → x_sub (lower 16 bits) += 0x2000 per frame; overflow carries to x_pos

Engine Crawl uses 8-bit xSubpixel: `xSubpixel += 0x20; if xSubpixel >= 0x100: x += 1, xSubpixel &= 0xFF`
which is equivalent to the upper byte of ROM x_sub — should produce identical pixel steps.

Candidate sources of the 1px/3px drift:
- **Spawn x_sub initialization**: if ROM initializes x_sub to a non-zero value (e.g., from
  adjacent memory or LoadSubObject), the first pixel arrives sooner in the ROM than in the engine
  (where xSubpixel starts at 0). Level layout parsing may not provide a sub-pixel component.
- **Proximity check timing delta (pre-existing, not from this fix)**: if the original engine code
  triggers ATTACKING 1 frame earlier/later than the ROM (due to Sonic-only vs closest-player
  selection), the Crawl walks 1 extra frame → accumulates 0x20 subpixels → may produce 1 pixel if
  the accumulator was at 0xE0-0xFF before the stop.
- **Walk-timer beq/bmi boundary**: ROM walking uses `beq.s` (branch when timer==0), original engine
  used `<= 0` (same for normal countdown); these are equivalent for fresh timer values.

### Prerequisites for the closer-player fix to land

1. Diagnose and fix the Crawl X-position drift (need to add Crawl x_sub/xSubpixel to the trace
   recorder or add targeted logging to narrow down which frame the 1px diverges).
2. Or, if the drift is purely from the proximity timing: fix the proxy timing independently first,
   verify Crawl position matches, then add the closer-player change on top.

### Cross-game regression check

Reverted code is on develop; no new test failures introduced. CNZ1, EHZ1, MCZ2 frontiers unchanged.

---

## 2026-05-19 - S2 SwingingPlatform chainCount cap + half-link platform offset (MCZ2 frame 1006 -> 1079)

- Branch: `develop` (HEAD `6acc363ef`)
- Worktree: isolated agent worktree, `git reset --hard develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MCZ2 frontier advanced from frame 1006 to frame 1079 (781 -> 802 errors).
- Regression check: `TestS2Mcz1LevelSelectTraceReplay` PASS;
  `TestS2Ehz1TraceReplay` PASS;
  `TestS2CnzLevelSelectTraceReplay` still at frame 3906 (22 errors, pre-existing — verified by `git stash` baseline run).

### Root cause

`SwingingPlatformObjectInstance` (Object 0x15) caused Sonic to land 24 pixels
higher than the ROM platform in MCZ2 at frame 1006. Two related bugs:

1. **chainCount clamp**: The engine capped subtype low-nybble at 7
   (`Math.min(7, subtype & 0x0F)`), but ROM `Obj15_Init` (`s2.asm:22480`) just
   does `andi.w #$F,d1` with no upper cap. The MCZ2 spawn at `(0x0620, 0x0548)`
   has subtype `0x18`, so ROM uses 8 chain links while the engine used 7.
2. **Missing half-link platform offset**: ROM `sub_FE70` (`s2.asm:22645-22654`)
   accumulates `sin/cos*0x10` per chain link in the chain loop, then halves the
   last increment (`asr.l #1`) when writing the platform's final position. Net
   effect: the platform's offset from the pivot is `(chainCount + 0.5) * 0x10`,
   not `chainCount * 0x10`. The engine omitted the half-link, putting the
   platform 8 pixels too high.

With chainCount=7 and no half-link offset, the engine placed the platform at
`baseY + 0x70 = 0x05B8` while ROM placed it at `baseY + 0x80 + 0x08 = 0x05D0`.
The 24-pixel gap meant the engine's platform-top was at y=0x05B0 (still in
Sonic's fall path) while ROM's was at y=0x05C8 (below where ROM Sonic landed at
y=0x05B4 on a different object, slot 10). Engine therefore "caught" Sonic on
the SwingingPlatform that ROM never touched.

### Fix

`SwingingPlatformObjectInstance.java`:
- Removed the `Math.min(7, ...)` cap; chainCount is now `max(1, subtype & 0x0F)`
  matching the ROM mask.
- Changed `chainLength = chainCount * 0x10` to `chainCount * 0x10 + 8` for the
  platform's position offset; chain link positions still use `(i+1)*0x10`.

### New MCZ2 frontier (frame 1079)

`y mismatch (expected=0x05B4, actual=0x05A9)`. Engine now correctly avoids
landing on the first MCZ2 SwingingPlatform during the f1006-f1009 fall, and
Sonic position matches ROM exactly through frames 1000-1078. At frame 1079
the engine has Sonic riding the **second** MCZ2 SwingingPlatform
(`onSlot=23(0x15) @061C,05C5`), while ROM has Sonic standing on `onObj=10`
(unidentified non-`near` object at y=0x05B4). The second platform spawn
(`subtype 0x38 -> BOUNCE_RIGHT, 8 chains`) sits at ROM `@0683,05C5` (positive
X offset from pivot 0x0650) but the engine computes a *negative* X offset
(@061C). This is a separate swing-direction issue: engine pre-rotates oscValue
by `-0x40` and assigns `Y=cos, X=sin`, while ROM passes oscValue directly to
`CalcSine` and assigns `Y=sin, X=cos` (mirrored convention). Attempted that
fix in this iter — it makes Sonic match ROM exactly through frames 1000-1008
but reverts the frontier to 1009 because Sonic lands 1 frame later (1 pixel
gap on the BOUNCE_LEFT platform top). Needs paired investigation of the
landing-edge condition; deferred to a follow-up iter.

## 2026-05-19 - S2 Vertical/Diagonal Spring clears Hurt routine (MCZ2 frame 925 -> 1006)

- Branch: `develop` (HEAD `4b2b42097`)
- Worktree: isolated agent worktree, `git reset --hard origin/develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MCZ2 frontier advanced from frame 925 to frame 1006 (737 -> 781 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS;
  `TestS2MczLevelSelectTraceReplay` still at frame 1085 (452 errors, pre-existing);
  `TestS2CnzLevelSelectTraceReplay` still at frame 3906 (22 errors, pre-existing);
  `TestS2Cnz2LevelSelectTraceReplay` still at frame 1490 (1057 errors, pre-existing).

### Root cause

`SpringObjectInstance.applyUpSpring` (and Down/Diagonal variants) did not clear
the engine's `hurt` flag when launching the player. ROM `Obj41_Up loc_189CA`
(`s2.asm:33735`), `Obj41_Down loc_18CC6` (`s2.asm:34023`), and the diagonal
launchers (`loc_18DD8` line 34090, `loc_18EE6` line 34173) all unconditionally
write `move.b #2,routine(a1)`, which overwrites the routine byte from 4
(`Obj01_Hurt`) to 2 (`Obj01_Control`). The ROM thus exits the Hurt routine when
a vertical spring fires while Sonic is hurt mid-air; subsequent airborne frames
use `Obj01_MdAir`'s +$38 gravity plus `Sonic_UpVelCap` (-$FC0) instead of
`Obj01_Hurt`'s +$30 gravity (no cap).

The engine encoded `routine` differently — `hurt` is a boolean and the trace
test maps `isHurt() -> rtn=04`. With `hurt` left set after the spring launch,
`PlayableSpriteMovement.airbornePhysics()` skipped `doJumpHeight()` (which
contains `applyUpwardVelocityCap`) entirely, and `getGravity()` returned 0x30
rather than 0x38. MCZ2 trace frame 925: ROM y_speed `-0F88` (cap from -$1000 to
-$FC0 then +$38), engine y_speed `-0FD0` (uncapped -$1000 + $30). Diff: 0x48
subunits = exactly the missing 0x40 cap delta plus the 0x08 gravity-step
shortfall.

### Fix

`SpringObjectInstance.java`: added `player.setHurt(false)` to `applyUpSpring`,
`applyDownSpring`, and `applyDiagonalSpring`. `applyHorizontalSpring` was left
alone because horizontal springs (`Obj41_Horizontal loc_18AEE`) do NOT write
`routine = 2` in ROM (they keep the player grounded and unchanged in routine).

### New MCZ2 frontier (frame 1006)

`y_speed expected=0x0850, actual=0x0000`. Engine landed on a SwingingPlatform
(`onSlot=17(0x15)`, `st=08`, `ride=1`), but ROM trace has Sonic still falling
(`status=02`, `air=1`). The frame-925 spring launch and the f925-f1005
airborne arc now match exactly; the divergence is a new SwingingPlatform
landing/collision issue, not the same root cause.

## 2026-05-19 - S2 MTZ2 Conveyor (Obj6C) child base position fix

- Branch: worktree `agent-a6ca59e26305ee5a1` on top of `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2325 errors (was 2189 after Long Platform). Frontier frame
  unchanged at 305.
- Frontier moved: nominal frame stays at 305 (y mismatch), but the misfire
  changed from a missed-platform fall-through to a pure landing-y-snap delta.
  Engine now lands on the correct conveyor at frame 305 (`onSlot=127(0x6C)
  ride=1 vel=(0050,0000)`) at y=0x05F2 instead of ROM's y=0x05EB (7-px low).
  Per-frame data shows perfect parity for x, x_speed, y_speed, g_speed, angle,
  air, rolling, ground_mode, tails through frame 304; only y diverges by ~6-7
  px for the post-landing run on the s29 conveyor.

Root cause: `ConveyorObjectInstance.createOrSpawnChildren()` was constructing
each child with the child's offset spawn position used as its own `baseX/baseY`
(path origin). ROM `Obj6C_LoadSubObject` (s2.asm:54137-54151) captures the
PARENT's `x_pos`/`y_pos` into `d2`/`d3` before the spawn loop and writes those
unchanged into every child's `objoff_30`/`objoff_32`, even though the child's
`x_pos`/`y_pos` is set to `parent + layoutOffset`. Without this each child
orbited its own offset point instead of orbiting the shared parent center,
scattering the platforms (engine had conveyors at x=0x0340/0x037D where ROM
had them at x=0x0320 forming the vertical spine of the route across the lava
pit).

Fix: added a second `ConveyorObjectInstance` constructor accepting explicit
`baseX`/`baseY` and threaded the parent's `x_pos`/`y_pos` through from
`createOrSpawnChildren` to each child.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java`

Cross-game regression sweep:
- MTZ (act 1): 989 errors @ frame 281 (unchanged)
- MTZ2 (act 2): 2325 errors @ frame 305 (was 2189; frontier unchanged, residual
  7-px landing-y snap delta only)
- MTZ3 (act 3): 2414 errors @ frame 298 (unchanged)
- EHZ1: pass

## 2026-05-19 - S2 OOZ post-Octus frontier diagnosis (no committed change; new blockers identified)

- Branch: `develop` (HEAD `805852e8b`)
- Worktree state: clean develop
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: both still fail, frontiers stand at post-Octus-fix locations
  - OOZ1: 1482 errors, frontier frame 563 (`g_speed` expected `0x0341`, actual `0x033D`)
  - OOZ2: 1238 errors, frontier frame 389 (`y_speed` expected `-0358`, actual `0x0358`)
- Confirmed the prompt's stated frontiers (OOZ1 f509 Buzzer, OOZ2 f301 spring/fan) are stale;
  the 2026-05-19 Octus collision-size and rise-timing fix already moved them.

### OOZ1 f563 root cause (deferred: sub-pixel friction divergence)

Sonic on ground (angle 0, ground_mode 0, rolling 0, jump 0). g_speed sequence:
f560 0x0365, f561 0x0359, f562 0x034D (both -0x0C/frame friction), f563 ROM 0x0341
(-0x0C continues), engine 0x033D (-0x10). Engine applies an extra 4 sub-pixels of
deceleration on this frame. No nearby objects in the `near` list, no input change.
Could be water-exit friction, an off-by-one on the frame `inertia` is capped, or
a friction-step issue when `g_speed` crosses a particular threshold. Needs recorder
extension to log the per-frame friction delta to pin down.

### OOZ2 f389 root cause (deferred: missing/displaced flying Aquis kill bounce)

Sonic at (0x051D, 0x0525) in air falling, y_vel +0x0320 -> engine sees gravity only
(+0x0358), ROM sees `neg.w y_vel` on a Touch_KillEnemy top-branch bounce (-0x0358 after
the same +0x38 gravity). ROM `near` lists fresh `obj+ s16 0x27 / s20 0x29 / s21 0x28
@0524,0536` (Explosion / Points / Animal): a badnik was killed at that position on
this frame. No static OOZ_2.bin spawn matches `(0x524, 0x536)`, so the kill target
was a FLYING badnik. Static Aquis spawns are at `(0x0558, 0x8558)` and `(0x05C8,
0x8578)` (idx 13, 14). Engine `eng-near` at f389-f392 still has Aquis at the static
positions: engine Aquis is not chasing far enough to reach `(0x0524, 0x0536)` and so
never gets bounce-killed there. ROM Aquis flew there, was killed, neg.w bounced Sonic.

The recent Obj50 four-bug fix (62abeb4d7) addressed init x_vel, bmi timers,
closer-player target, and on_screen lag, but the chase-distance divergence is not
covered. Two leads worth following: (a) `Obj50_Chase` enters as soon as `on_screen`,
but ROM `render_flags.on_screen` is set via `RememberState` after `DisplaySprite` runs
in the same execute pass: the engine's last-frame snapshot may still trigger one
frame late vs. ROM, delaying chase entry; (b) `Obj50_FollowPlayer` adds `+/-$10` to
both axes per frame from `Obj50_Speeds`, capped at `$100`: the engine's cap or
sub-pixel accumulator may be discarding tiny velocity increments that ROM keeps.
Without a recorder field for `Obj50_timer`, `Obj50_shots_remaining`, and per-Aquis
position, narrowing the bug further is speculative.

ROM cite: `docs/s2disasm/s2.asm:60181-60295` (Obj50 chase/shoot/flee state machine).

Did not commit a fix: chase/sub-pixel divergence requires recorder-side diagnostics
(Aquis position over time) or a careful re-derivation of the speed/cap path that
risks regressing the four bugs the previous Aquis fix addressed. EHZ1 still green
locally.

## 2026-05-19 - S2 MTZ3 Obj6A zone-aware behavior and activation gating fix

- Branch: `worktree-agent-a10cbe7b6f47980c4` (reset to `develop` @ `7eaa19993`)
- Worktree state: clean develop + Obj6A rewrite
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ3 frontier advances
  - MTZ3: 2414 → 2349 errors. Frontier moves frame 298 (`air` 0 vs 1) → frame 340 (`tails_g_speed` 0x0000 vs 0x0018)
- Regression check: MTZ1 unchanged at frame 375 (`tails_air`, 945 errors); MTZ2 unchanged at frame 222 (`x`, 2370 errors)
- Cross-game smoke: S1 GHZ1 + S2 EHZ1 still green. CNZ1 fails at frame 3906 (pre-existing, confirmed by re-running with stashed fix)

Root cause: `MCZRotPformsObjectInstance` (Obj6A) was hard-wired to MCZ behavior
across both zones. ROM `Obj6A_Init` (`s2.asm:53686-53751`) branches on
`Current_Zone`: MTZ uses `byte_27CDC` (4-phase, faster), `y_radius=0x0C`,
routine 2 (wait for player to walk off via `loc_27BDE`), and skips the
subtype-0x18 child-spawn block. MCZ uses `byte_27CF4`/`byte_27D12`,
`y_radius=0x20`, routine 4 (`loc_27C66`, move unconditionally), and spawns
two child platforms for subtype 0x18. Additionally, the engine masked
`spawn.subtype() & 0x0F` to derive `phaseIndex`, but ROM (`s2.asm:53750`)
stores the FULL subtype byte into `objoff_38` and uses it as a byte offset
into the velocity table (same P31 trap as the Obj65 fix).

For MTZ3 subtype-0 entries at `(0x02A0, 0x020C)` / `(0x02E0, 0x020C)`, the
combined defects caused platforms to use MCZ `y_radius=0x20` (32 px) instead
of MTZ `0x0C` (12 px) and start moving immediately, drifting `y +0x40` and
`x -0x4A` by frame 298 so the platform's right edge slid past the player and
triggered an air state ROM never sees.

ROM cite: `docs/s2disasm/s2.asm:53670-53871`.
## 2026-05-19 - S2 MTZ1 / MTZ2 frontier investigation (no committed change; frontiers unchanged)

- Branch: `develop`
- Worktree state: clean develop
- Commands:
  - `mvn -q -Dmse=off "-Dtest=TestS2MtzLevelSelectTraceReplay" test`
  - `mvn -q -Dmse=off "-Dtest=TestS2Mtz2LevelSelectTraceReplay" test`
- Result: unchanged frontiers
  - MTZ1: 945 errors, frontier frame 375 (`tails_air` expected `1`, actual `0`)
  - MTZ2: 2370 errors, frontier frame 222 (`x` expected `0x028C`, actual `0x028E`)

### MTZ1 root cause (deferred — requires SidekickCpuController investigation)

ROM physics CSV f0x176→f0x177 shows Tails transitioning from `(x=0x02BC,y=0x0250,status=0x09,
stand_on_obj=0x13)` to `(x=0x4000,y=0x0000,status=0x02,stand_on_obj=0x13)` with x_sub/y_sub
preserved (`0xE600,0xCF00`). These exact post-transition values are the literal stores in
`TailsCPU_Despawn` (`s2.asm:39043-39052`): `move.w #$4000,x_pos(a0); move.w #0,y_pos(a0);
move.b #$81,obj_control(a0); move.b #1<<status.player.in_air,status(a0)`. So ROM despawns
Tails one frame after the SteamSpring launch.

The trigger path is `TailsCPU_CheckDespawn` (`s2.asm:39055-39081`): when Tails is off-screen
AND `on_object` is clear (just launched off the spring), it ticks `Tails_respawn_counter`;
when on-screen WITH `on_object` set but `id(interact) != Tails_interact_ID`, it immediately
jumps to `TailsCPU_Despawn`. In the trace Tails was on slot 0x13 (SteamSpring) one frame
earlier with `interact=0x13` set, then the spring launched him (`loc_26798`: y_vel=-0xA00,
in_air=1, on_object=0). The exact frame-after-launch despawn implies an interact-ID
mismatch trigger or a separate path I didn't trace down.

Engine behavior: at the same frame the engine keeps Tails on the spring
(`eng-tails-state pos=(02B3,0251) ride=s19 type=42 st=09 ... onObj=true`) — the
`SidekickCpuController` despawn predicates aren't matching ROM's immediate-despawn case.
This shows up downstream as `tails_air=1` (ROM) vs `0` (engine) and follows as a long
tail of position/state mismatches once ROM's Tails is parked at (0x4000, 0x0000) while
engine's Tails continues riding.

Fix scope is the sidekick-CPU despawn predicates (likely a `Tails_interact_ID` check that
fires when the interact-slot's object ID changes mid-spring-cycle, or an off-screen
post-launch immediate despawn). Did NOT commit because the predicate change touches
shared sidekick logic that must not regress S2 CNZ/SCZ/MCZ traces.

### MTZ2 root cause (deferred — platform-carry timing mismatch)

ROM physics CSV around frame 222 shows Sonic riding s18 (MTZLongPlatform subtype 5
conveyor) with platform moving +2px/frame and player position incrementing by exactly
`x_vel/256` per frame (NO platform carry observed in trace). Engine increments position
by `x_vel/256 + 2 (carry)` every frame, so the divergence grows +2px/frame starting
at the frame Sonic mounts the platform:

```
f222: ROM x=0x028C ENG x=0x028E (+2)
f223: ROM x=0x028E ENG x=0x0292 (+4)
f224: ROM x=0x0290 ENG x=0x0296 (+6)
f225: ROM x=0x0292 ENG x=0x029A (+8)
...
```

Both engine and ROM have the same `x_speed=0x01F4` and same `inertia`. The platform itself
moves +2/frame in both (verified via consecutive `near s18` x positions: ENG
`02A2->02A4->02A6` lockstep with ROM `02A4->02A6->02A8`, just lagged by 2px globally).

Per `s2.asm:52450-52468` (Obj65 `loc_26C1C`): platform's update saves x_pos to
`objoff_2E`, runs subtype 5 (`loc_26E4A:addq.w #2,x_pos`), loads `d4 = objoff_2E`
(pre-move x), then calls `JmpTo10_SolidObject` → `MvSonicOnPtfm` (`s2.asm:35402-35426`):
`sub.w x_pos(a0),d2; sub.w d2,x_pos(a1)`, which carries player by `+delta = +2`. So per
my code-read of the ROM, the carry SHOULD apply.

What I ruled out:
1. **Not `obj_control` gating** — engine output shows `objCtrl=false`, ROM player isn't
   in MTZ tube state (`obj_control=$81`) here.
2. **Not `routine >= 6` gating** — Sonic routine is 0x02 (Obj01_Control) the whole sequence.
3. **Not `Debug_placement_mode`** — gameplay is active.
4. **Not the landing-frame skip** — `SolidObject_Landed` skips carry only on the landing
   frame f221; the divergence persists on every subsequent frame.
5. **Not the platform stepping forward** — both ENG and ROM platforms step +2/frame,
   verified across 10 consecutive frames.
6. **Not the inertia/x_vel calculation** — both ENG and ROM have identical x_vel/inertia
   trajectories per frame.

What I could NOT reconcile: the trace data unambiguously shows the player's 32-bit
position (`x_pos:x_sub`) advancing by exactly `x_vel*0x100` per frame with zero extra
delta from the platform, while my reading of `MvSonicOnPtfm` says +2 should be added to
the high word. Either MvSonicOnPtfm is not running for the MTZ conveyor case (some gate I
missed), or the trace recorder samples before the carry, or the inertia accumulation
secretly absorbs the carry. Engine fix would need to suppress carry for MTZ Long
Platform conveyor — but the rationale isn't clear enough to land without verifying the
exact ROM gate. Risk of regressing CPZ/OOZ/MCZ moving platform traces if applied broadly.

Did NOT commit because:
- The "engine carry = ROM carry, just verified" reading conflicts with the trace data
  by a constant +2 delta per frame. Need to instrument a recording at the exact pre/post
  MvSonicOnPtfm point to know which side of the boundary the trace samples.
- Suppressing carry for MTZLongPlatform alone would mask the bug; need to understand the
  generic rule first to avoid cross-zone regression.

Cross-game regression: not run (no commit to verify).

## 2026-05-19 - S2 OOZ Aquis (Obj50) four-bug ROM-accuracy fix

- Branch: `worktree-agent-a11d73c9194c58ed5`
- Commit context: dirty worktree, base `d140c9d07` ("Fix S1 credits trace parity")
- Targets: S2 OOZ1 + OOZ2 trace replays, `AquisBadnikInstance` (Obj50)
- Commands:
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace" test`
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test`
  - cross-game: `TestS2Ehz1TraceReplay`, `TestS2MtzLevelSelectTraceReplay`, `TestS2CnzLevelSelectTraceReplay`

Four ROM-accuracy fixes applied to `AquisBadnikInstance` in a single patch:

1. **Initial x_vel.** `Obj50_Init` (s2.asm:60100) executes
   `move.w #-$100, x_vel(a0)` immediately after the standard init block. The
   engine constructor was leaving `xVelocity = 0`. Set `xVelocity = -0x100` in
   the constructor.
2. **`bmi` timer semantics.** `Obj50_FollowPlayer` (s2.asm:60244-60245) and
   `Obj50_WaitForNextShot` (s2.asm:60275-60276) use
   `subq.b #1, timer / bmi`, which fires when the byte wraps from `0x00` to
   `0xFF`. The engine used `if (--timer <= 0)` which fires one frame early.
   Switched both call sites to `timer = (byte)(timer - 1); if (timer < 0)`.
3. **Closer-player orientation.** `Obj_GetOrientationToPlayer`
   (s2.asm:72320-72346) picks the closer of MainCharacter and Sidekick by
   `|x_pos - obX|`. The engine only used the main player. Added a
   `closestPlayer(PlayableEntity main)` helper that walks `services().sidekicks()`
   and returns the sprite with minimum absolute X distance.
4. **`render_flags.on_screen` one-frame lag.** `Obj50_CheckIfOnScreen`
   (s2.asm:60181-60188) tests `render_flags.on_screen`, which the ROM clears at
   the start of each `BuildSprites` pass and only re-sets if the sprite is
   actually drawn this frame. The engine used `isOnScreen(32)` which has no
   lag. Added paired `onScreenLastFrame` / `onScreenThisFrame` flags: snapshot
   in `updateMovement()`, set `onScreenThisFrame = true` in
   `appendRenderCommands()` when `isOnScreen(0)` (exact viewport, matching the
   ROM bounds test), and gate the WAIT_FOR_SCREEN transition on
   `onScreenLastFrame`.

Result (before -> after):

| Test                                     | Before        | After         | Delta |
|------------------------------------------|---------------|---------------|-------|
| TestS2OozLevelSelectTraceReplay          | 1117 @ f509  | 1117 @ f509  | 0     |
| TestS2Ooz2LevelSelectTraceReplay         | 1329 @ f301  | 1280 @ f301  | -49   |
| TestS2Ehz1TraceReplay                    | PASS         | PASS         | -     |
| TestS2MtzLevelSelectTraceReplay          | 1015 @ f281  | 1015 @ f281  | 0     |
| TestS2CnzLevelSelectTraceReplay          | PASS         | PASS         | -     |

OOZ2 total error count drops by 49 (Aquis-attributable mismatches eliminated),
while the OOZ1 frontier is unrelated (gated by a Buzzer `0x4A` enemy bounce at
f509) and the OOZ2 first-error frontier is also unrelated (gated by a
Tails+Spring/Fan `tails_y_speed` mismatch at f301). The first-error frame
therefore does not move for either OOZ replay even though the Aquis behaviour
is now ROM-accurate. The OOZ2 error-count improvement is the verifiable signal
that the fix is correct. No cross-game regressions in EHZ1, MTZ, or CNZ.

## 2026-05-19 - Fix S2 MCZ VineSwitch release input and Tails support

- Branch: `develop` (worktree `agent-ab982cb17d078747f`)
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mcz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Status: PASS-not-yet (frontier advanced)
- Result: MCZ2 frontier advanced from frame 198 to frame 925 (936 -> 737 errors)
- Regression: `TestS2Ehz1TraceReplay`, `TestS2Cnz1LevelSelectTraceReplay`, `TestS2Mcz1LevelSelectTraceReplay` all PASS

Two related bugs in `VineSwitchObjectInstance` (S2 Obj7F):

1. Release used held-button state (`isJumpPressed()`) instead of just-pressed
   edge (`isJumpJustPressed()`). ROM `Obj7F_Action` does
   `move.w (Ctrl_1).w,d0 / andi.b #button_B_mask|button_C_mask|button_A_mask,d0`
   - the `.b` operates on the low byte of `Ctrl_1`, which is `Ctrl_1_Press`
   (just-pressed this frame), not `Ctrl_1_Held`. With the engine reading held
   state, Sonic was released a single frame after grabbing whenever the player
   held B (which is the same input that triggered the jump that put him near
   the vine).

2. Sidekick (Tails) was never processed. ROM iterates twice: once for
   `MainCharacter` with `Ctrl_1`, once for `Sidekick` with `Ctrl_2`. The
   engine looped only over the main character with a "Player 2 deferred"
   comment. Added a sidekick pass using `services().sidekicks()` so Tails can
   grab and hang from the vine.

Files: `src/main/java/com/openggf/game/sonic2/objects/VineSwitchObjectInstance.java`

## 2026-05-19 - S2 OOZ Aquis investigation (no committed change; frontiers unchanged)

- Branch: `develop`
- Worktree state: clean develop (no commit; experimental Aquis edits reverted)
- Commands:
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: unchanged frontiers
  - OOZ1: 1412 errors, frontier frame 563 (`g_speed` 0x0341 vs 0x033D)
  - OOZ2: 1305 errors, frontier frame 389 (`y_speed` -0x0358 vs +0x0358)

OOZ2 frame 389 corresponds to CSV row 0185 (trace frame numbering differs from CSV row
numbering after pre-trace ROM warmup). ROM state at that row: Sonic at (0x051D, 0x0525)
with y_speed flipping from +0x0320 (f184) to -0x0358 (f185) — a classic enemy-bounce
negation. ROM events `obj+ s16 0x27 @0524,0536`, `obj+ s20 0x29`, `obj+ s21 0x28` show
an Aquis at slot 16 destroyed at (0x0524, 0x0536), spawning animal/points group. Engine
slot 16 still holds a live Aquis at (0x04FE, 0x054C) — 38 px left and 22 px below the
ROM position, so the engine's Sonic does not intersect the Aquis touch box (0x10, 0x08)
and never bounces.

Aquis movement diverges over many chase/shoot cycles before f389. Three ROM-vs-engine
Obj50 (`AquisBadnikInstance`) deltas were identified and individually exercised against
both OOZ traces (each reverted before the next):

1. **Initial x_vel (`s2.asm:60100`):** `move.w #-$100, x_vel(a0)` in `Obj50_Init` was not
   applied. Adding `xVelocity = -0x100` at construction time moved OOZ2 by -134 errors
   (1305 → 1171) but introduced **+27 OOZ1 errors** (1412 → 1439). Frontier frames did
   not move.
2. **P30 timer (`s2.asm:60244-60245, 60275-60276`):** `Obj50_FollowPlayer` and
   `Obj50_WaitForNextShot` both use `subq.b #1, timer; bmi.s ...`, which fires at
   timer=-1 (after the decrement); the engine used `timer--; if (timer <= 0)`, firing
   one frame earlier. Switching to the byte-signed `(byte) timer < 0` test plus
   ordering decrement-before-movement (so the transition frame skips ObjectMove, matching
   ROM `Obj50_DoneFollowing` falling through `MoveStop`) moved OOZ2 by -46 errors
   (1305 → 1259) but introduced **+54 OOZ1 errors** (1412 → 1466). Frontier frames did
   not move.
3. **Closer-player orientation (`s2.asm:72320-72346`):** `Obj_GetOrientationToPlayer`
   targets the closer of Sonic/Tails (`mvabs.w` + `bls.s`); the engine uses Sonic only.
   Adding closer-player selection on top of the P30 fix only changed OOZ2 by -1 more
   error (1259 → 1258) and did not advance the frontier. (Behaviour at f389 has Sonic
   and Tails at almost the same x_pos, so closer-player doesn't matter there.)

Combined fixes (initial x_vel + P30 + closer-player) produced a 1172-error OOZ2 result
(net -133) but kept the OOZ1 regression around +30 errors. None of the combinations
advanced the OOZ2 f389 frontier; all variants left engine Aquis position ~38 px X and
~22 px Y away from ROM at the moment Sonic should have hit it.

OOZ1 f563 (`g_speed` 0x0341 vs 0x033D, no nearby objects in diagnostic, flat ground at
camera (0462, 064C)) is a 4-subpixel velocity drift unrelated to Aquis touch geometry.
Aquis-only changes that should be ROM-accurate (especially the initial x_vel from
`Obj50_Init`) introduced new error frames after f563 in OOZ1, suggesting either a
downstream engine bug whose effect was masked by the previously-incorrect Aquis behaviour
or an additional Aquis discrepancy still unaccounted for. Without a fix that advances at
least one frontier and does not regress the other, no commit was made.

Not yet explored / ruled out:
- `Obj50_CheckIfOnScreen` triggers on `render_flags.on_screen` (set after object renders
  inside the precise camera viewport). Engine uses `isOnScreen(32)` (camera bounds + 32 px
  margin). These can fire on different frames, changing how long the first chase phase
  has to move the Aquis before the player gets close enough.
- Wing child object: ROM allocates a separate OST slot in `Obj50_Init`. Engine renders
  the wing inline. Wing should not affect physics, but the slot allocation could shift
  slot numbering for other objects in the same window.
- `Obj_CapSpeed` exact rounding — verified equivalent to engine's `clampSpeed`.
- Trace replay test harness uses CSV-after-warmup frame numbering; the diagnostic `frame
  389` corresponds to CSV row 0185 in `src/test/resources/traces/s2/ooz2/physics.csv.gz`.

## 2026-05-19 - S2 CNZ2 pinball_mode preservation flag fix (silent regression)

- Branch: `develop`
- Worktree state: clean develop + flag fix
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1057 errors — frontier restored to frame 1490 `tails_y_speed`
- Cross-game: `*TraceReplay` sweep (41 tests, 21 failures), all matching pre-existing frontiers

On a clean develop reset, CNZ2 was failing at frame 936 (`tails_air`, 1161 errors), not frame
1490 (1057 errors) as the prior commit narrative described. `PhysicsFeatureSet.SONIC_2.
pinballLandingPreservesPinballMode` was still `false`, so `PlayableSpriteMovement.resetOnFloor()`
unconditionally cleared the engine `pinball_mode` mirror on every landing. That broke
`SidekickCpuController.updateNormal()`'s rolling+pinball+!air suppress guard.

Fix: flipped `pinballLandingPreservesPinballMode` to `true` in `PhysicsFeatureSet.SONIC_2`.
ROM `Sonic_ResetOnFloor` / `Tails_ResetOnFloor` (`s2.asm:37770-37771, 40625-40626`) both
`tst.b pinball_mode` / `bne.s Part3`; Part3 only clears in_air/pushing/rolljumping/jumping —
`pinball_mode` is never cleared.

CNZ2 frame 1490 `tails_y_speed` root cause investigated: Crawl (`ObjC8`) proximity and range
checks use `Obj_GetOrientationToPlayer` (`s2.asm:72320-72346`) which tests the **closer** of
Sonic/Tails (`mvabs.w` + `bls.s`). Engine `CrawlBadnikInstance` only passes the leader (Sonic).
Implementing closer-player selection fixed the 3-px x-position drift at frame 1490, but
introduced a 1-pixel regression at frame 630 (`y_speed -0666` vs `-0682`, 1096 errors) that
could not be diagnosed. Closer-player fix reverted. Frame 630 Crawl x-position needs further
recorder diagnostics before the frame-1490 fix can land.

## 2026-05-19 - S2 MTZ SteamSpring timing and MTZLongPlatform props-lookup fix

- Branch: `develop`
- Worktree state: clean develop + MTZ fixes
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2MtzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: MTZ1 and MTZ2 frontiers advance; MTZ3 unchanged
  - MTZ1: 1015 → 945 errors. Frontier moves frame 281 (`y` 0x024D vs 0x0255) → frame 375 (`tails_air` 1 vs 0)
  - MTZ2: 2335 → 2370 errors. Frontier moves frame 221 (`y`) → frame 222 (`x` 0x028C vs 0x028E)
  - MTZ3: unchanged at frame 298 (`air` 0 vs 1); root cause is `MCZRotatingPlatformsObjectInstance` placement
- Cross-game regression: S1 GHZ1, S1 MZ1, S2 EHZ1, S2 CNZ1, S2 SCZ still green

Root causes fixed:

1. **SteamSpring (Obj42) solid-checkpoint ordering:** Engine ran the state machine THEN
   resolved solid contacts (post-update), so the player followed the spring's new y_pos one
   frame too early. ROM `loc_26688` (`s2.asm:52030-52049`) calls
   `SolidObject_Always_SingleCharacter` BEFORE the state-machine branches update `objoff_36`
   / `y_pos`. Fix: switched to `MANUAL_CHECKPOINT` mode; `checkpointAll()` runs first in
   `update()`, spring fire applied from batch result (manual mode suppresses the compatibility
   `onSolidContact` callback). MTZ1 frontier moved 281 → 375.

2. **MTZLongPlatform (Obj65) properties off-by-2 indexing:** Engine used `d0 >> 2` for
   both the props-table entry AND `mapping_frame`, collapsing 8 ROM entries to 4. ROM
   `s2.asm:52386-52394` does `lea Obj65_Properties(pc,d0.w),a3` (entry index = d0/2) then
   separately `lsr.w #2,d0` for `mapping_frame` (= d0/4). For subtype 0xB1 this picked
   entry 3 `{0x40, 0x03}` (stationary) instead of entry 6 `{0x40, 0x0C}` (moveSubtype=7,
   maxDist=0x80), so the platform never moved. Fix: `entryIndex = d0 >> 1`,
   `frameIndex = d0 >> 2`. MTZ2 frontier moved 221 → 222.

MTZ3 frame 298 `air` mismatch: `MCZRotatingPlatformsObjectInstance` (Obj6A) has placement/
subtype divergence. Noted for separate investigation.

## 2026-05-19 - S2 OOZ Octus collision and rise timing parity

- Branch: `develop`
- Worktree state: clean develop + Octus fixes
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2OozLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ooz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: both still fail, but frontiers advance
  - OOZ1: 1117 → 1412 errors. Frontier moves frame 509 (`y_speed` 0x0300 vs -0x0300) → frame 563 (`g_speed` 0x0341 vs 0x033D)
  - OOZ2: 1337 → 1305 errors. Frontier moves frame 301 (`tails_y_speed` -0x0278 vs -0x0178) → frame 389 (`y_speed` -0x0358 vs +0x0358)
- Cross-game regression: S1 GHZ1, S1 MZ1, S2 EHZ1 still green

Root causes fixed:

1. **Octus (Obj4A) touch box size:** `OctusBadnikInstance` hard-coded `COLLISION_SIZE_INDEX = 0x0C`
   ((20,16) half-extents). ROM `s2.asm:59905` writes `collision_flags = $A`, mapping to
   `Touch_Sizes[$A] = (16,8)`. The oversized box made Sonic bounce off Octuses 1–2 frames
   early, producing the OOZ1 frame-509 y_speed sign reversal and the OOZ2 frame-301 cascade.

2. **Octus rise/hover transition timing:** ROM `Obj4A_DelayBeforeMoveUp` (`s2.asm:59958-59967`)
   uses `bmi` (branch when timer is negative after decrement) and falls through to
   `JmpTo19_ObjectMove` on the transition frame, applying `y_vel=-$200` immediately. The engine
   used `timer <= 0` and skipped movement on the transition frame, leaving Octus ~4px lower than
   ROM throughout `MoveUp`. Same `bmi`-vs-zero correction applied to `Obj4A_Hover`
   (`s2.asm:59981-59988`).

## 2026-05-19 - Full test run and fresh trace frontier sweep

- Branch: `develop`
- Worktree state: dirty with S1 credits trace fixes and existing local changes
- Commands:
  - `mvn -q -Dmse=off test`
  - cleared generated `target/trace-reports`
  - `mvn -q -Dmse=off "-Dtest=*TraceReplay,*ReplayBootstrap" test -DfailIfNoTests=false`
- Result: full suite failed with 4870 tests, 21 failures, 2 errors, 6 skipped; fresh trace sweep failed with 74 tests, 38 failures, 0 errors, 0 skipped

The fresh trace sweep confirms no S1 trace replay regression from the credits
fixes: `TestS1Ghz1TraceReplay`, `TestS1Mz1TraceReplay`, and all eight
`TestS1Credits*TraceReplay` classes pass. The full suite still has a separate
debug-probe failure, `DebugS1Credits01Mz2PushBlockProbe`, because it asks for
frame 539 outside the current probe window.

Other non-trace full-suite failures remain outside this frontier table,
including `DefaultObjectServices`/object-service migration guards, a
`TestGroundSensor` top-solid expectation, rewind torture divergences, CNZ slot
machine RNG, several S3K event/object assertions, HTZ boss touch response, S2
MCZ rotating platform lifecycle, and singleton lifecycle guard coverage.

Fresh JSON reports under `target/trace-reports` produced these first
non-cascading frontiers:

| Trace | Errors | Warnings | Frames | Frontier / first error |
| --- | ---: | ---: | ---: | --- |
| `s2_arz1` | 813 | 0 | 5064 | frame 304, `tails_x_speed` expected `0x00D0`, actual `0x00C2` |
| `s2_arz2` | 2129 | 0 | 7716 | frame 225, `y` expected `0x04DC`, actual `0x04D9` |
| `s2_cnz1` | 22 | 0 | 9469 | frame 3906, `tails_y` expected `0x06C0`, actual `0x06C1` |
| `s2_cnz2` | 1057 | 0 | 12081 | frame 1490, `tails_y_speed` expected `-059B`, actual `-052B` |
| `s2_cpz1` | 434 | 0 | 5741 | frame 844, `x_speed` expected `-0200`, actual `0x00E4` |
| `s2_cpz2` | 1646 | 0 | 12142 | frame 962, `y` expected `0x03AD`, actual `0x03AC` |
| `s2_dez1` | 174 | 0 | 7888 | frame 536, `rolling` expected `1`, actual `0` |
| `s2_htz1` | 490 | 0 | 8856 | frame 5511, `x` expected `0x151B`, actual `0x1523` |
| `s2_htz2` | 815 | 0 | 10160 | frame 795, `tails_air` expected `0`, actual `1` |
| `s2_mcz1` | 452 | 0 | 6083 | frame 1085, `y` expected `0x056C`, actual `0x056B` |
| `s2_mcz2` | 936 | 0 | 10953 | frame 198, `y_speed` expected `0x0000`, actual `-0300` |
| `s2_mtz1` | 1015 | 0 | 10133 | frame 281, `y` expected `0x024D`, actual `0x0255` |
| `s2_mtz2` | 2335 | 0 | 12697 | frame 221, `y` expected `0x062D`, actual `0x062A` |
| `s2_mtz3` | 2414 | 0 | 15622 | frame 298, `air` expected `0`, actual `1` |
| `s2_ooz1` | 1117 | 0 | 11015 | frame 509, `y_speed` expected `0x0300`, actual `-0300` |
| `s2_ooz2` | 1329 | 0 | 13317 | frame 301, `tails_y_speed` expected `-0278`, actual `-0178` |
| `s2_wfz1` | 589 | 0 | 16426 | frame 4719, `x_speed` expected `0x08D6`, actual `0x08E2` |
| `s3k_aiz1` | 1738 | 22 | 20463 | frame 1057, `tails_x` expected `0x13CC`, actual `0x7F00` |
| `s3k_cnz1` | 3776 | 17 | 42242 | frame 4790, `tails_x` expected `0x6125`, actual `0x7F00` |
| `s3k_mgz1` | 2625 | 60 | 35861 | frame 1538, `y` expected `0x0DC9`, actual `0x0DC8` |

Fresh trace sweep pass rows:
`TestS1Ghz1TraceReplay`,
`TestS1Mz1TraceReplay`,
`TestS1Credits00Ghz1TraceReplay` through
`TestS1Credits07Ghz1bTraceReplay`,
`TestS2Ehz1TraceReplay`,
`TestS2SczLevelSelectTraceReplay`,
and the non-`replayMatchesTrace` S3K CNZ/AIZ focused assertions except the AIZ
bootstrap/player-window failures listed below.

`TestS3kAizReplayBootstrap` has 17 current assertion frontiers that are not
represented by the JSON comparator table: frame 0 AIZ intro object routine state,
legacy pre-level prefix classification, missing/incorrect gameplay-start anchor
at frame 1387, gameplay-start detection off by two frames, title-card/Knuckles
state around gameplay start, missing `aiz1_fire_transition_begin`, frame 1800
post-fire-transition player X, frame 1719 Monkey Dude height, frame 1983 roll
rock debris, frame 2006 rolling hitbox and replay X, frame 2110/2155 floating
platform state, and frame 4886 AIZ2 reload resume mismatches.

`TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` also
fails at trace frame 2876: player X expected `8023`, actual `7692`.

## 2026-05-19 - S1 credits LZ and MZ trace fixes

- Branch: `develop`
- Worktree state: dirty with S1 credits trace fixes
- Commands:
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits03Lz3TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits01Mz2TraceReplay,TestS1Credits03Lz3TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Credits01Mz2TraceReplay" test -DfailIfNoTests=false`
  - `mvn -q -Dmse=off "-Dtest=TestS1Ghz1TraceReplay,TestS1Mz1TraceReplay,TestS1Credits*TraceReplay" test -DfailIfNoTests=false`
- Result: targeted LZ3 and MZ2 pass; S1 GHZ1, MZ1, and all S1 credits traces pass in the targeted regression sweep

`TestS1Credits03Lz3TraceReplay` now reaches the full trace window after
emulating the Sonic 1 REV01 non-FixBugs `LZWindTunnels` d0 clobber and seeding
the LZ credits-demo vblank phase at the lamppost bootstrap.

`TestS1Credits01Mz2TraceReplay` now reaches the full trace window. Parent-spawned
lava geyser makers delay their first proximity-triggered advance by one live
tick, matching the ROM slot timing through the original frame-493 divergence.
The remaining one-pixel push-block vertical-motion delta at frame 499 was closed
by preserving the first airborne frame's REV01 launch phase: the geyser maker
sets the parent push block airborne from a later SST slot, and the block's
subpixel/velocity seed now keeps the first `#-$580` displacement before
`loc_C056`'s `+$18` gravity affects the next visible velocity.

## 2026-05-18 - Full trace replay frontier sweep after S2 act expansion

- Branch: `develop`
- Worktree state: dirty with S2 act fixture/test expansion and trace recorder updates
- Command: `mvn -q -Dmse=off '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
- Result: fail, 51 trace replay/invariant/policy methods inspected, 25 failures

This snapshot is from the current workspace after adding Sonic 2 act-specific
level-select replay tests and regenerated fixtures. Passing rows reached their
full configured trace/assertion window. Failing rows record the first mismatched
frame or first assertion failure.

| Test | Status | Errors | Warnings | Frontier / first error |
| --- | --- | ---: | ---: | --- |
| `TestTraceReplayInvariantGuard.concreteTraceReplayTestsUseSharedReplayBaseClass` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.defaultTraceReplayToleranceIsStrict` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.traceParserDataAndCatalogStayIndependentOfEngineRuntime` | pass | 0 | 0 | full trace |
| `TestTraceReplayInvariantGuard.traceReplayCodeDoesNotWriteRecordedStateBackIntoEngine` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.ordinaryS2TraceDoesNotUseTornadoRideStart` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s2SczAndWfzLevelSelectUseNativeTornadoRideStart` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel` | fail | 1 | 0 | frame 289 classified as `VBLANK_ONLY` instead of `FULL_LEVEL_FRAME` |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTraceStartsAtFrameZeroWithoutSkippingIntro` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTraceUsesLiveIntroSpawnInsteadOfRecordedFrameZeroPosition` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kGameplayTraceSeedsFrameZeroAfterSidekickTitleCardPrelude` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kGameplayTraceStillDoesNotSeedFrameZeroWhenObjectSnapshotsExist` | pass | 0 | 0 | full trace |
| `TestTraceReplayStartPositionPolicy.s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved` | fail | 1 | 0 | S3K MGZ sidekick prelude assertion |
| `TestTraceReplayStartPositionPolicy.vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState` | fail | 1 | 0 | `FULL_LEVEL_FRAME` strict comparison assertion |
| `TestS1Credits00Ghz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits01Mz2TraceReplay.replayMatchesTrace` | fail | 6 | 0 | frame 493, `y` |
| `TestS1Credits02Syz3TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits03Lz3TraceReplay.replayMatchesTrace` | fail | 6 | 0 | frame 221, `y` |
| `TestS1Credits04Slz3TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits05Sbz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits06Sbz2TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Credits07Ghz1bTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Ghz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS1Mz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2ArzLevelSelectTraceReplay.replayMatchesTrace` | fail | 813 | 0 | frame 304, `tails_x_speed` |
| `TestS2Arz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 2129 | 0 | frame 225, `y` |
| `TestS2CnzLevelSelectTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2Cnz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1262 | 0 | frame 0, `air` |
| `TestS2CpzLevelSelectTraceReplay.replayMatchesTrace` | fail | 434 | 0 | frame 844, `x_speed` |
| `TestS2Cpz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1646 | 0 | frame 962, `y` |
| `TestS2DezEndingLevelSelectTraceReplay.replayMatchesTrace` | fail | 174 | 0 | frame 536, `rolling` |
| `TestS2Ehz1TraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2HtzLevelSelectTraceReplay.replayMatchesTrace` | fail | 398 | 0 | frame 5511, `x` |
| `TestS2Htz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 821 | 0 | frame 762, `tails_y` |
| `TestS2MczLevelSelectTraceReplay.replayMatchesTrace` | fail | 452 | 0 | frame 1085, `y` |
| `TestS2Mcz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 936 | 0 | frame 198, `y_speed` |
| `TestS2MtzLevelSelectTraceReplay.replayMatchesTrace` | fail | 964 | 0 | frame 281, `y` |
| `TestS2Mtz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 2335 | 0 | frame 221, `y` |
| `TestS2Mtz3LevelSelectTraceReplay.replayMatchesTrace` | fail | 2414 | 0 | frame 298, `air` |
| `TestS2OozLevelSelectTraceReplay.replayMatchesTrace` | fail | 1117 | 0 | frame 509, `y_speed` |
| `TestS2Ooz2LevelSelectTraceReplay.replayMatchesTrace` | fail | 1329 | 0 | frame 301, `tails_y_speed` |
| `TestS2SczLevelSelectTraceReplay.replayMatchesTrace` | pass | 0 | 0 | full trace |
| `TestS2WfzLevelSelectTraceReplay.replayMatchesTrace` | fail | 589 | 0 | frame 4719, `x_speed` |
| `TestS3kAizTraceReplay.cameraMatchesTraceThroughFirstDelayedScrollBurst` | pass | 0 | 0 | full trace |
| `TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` | fail | 1 | 0 | frame 2876, assertion mismatch |
| `TestS3kAizTraceReplay.replayMatchesTrace` | fail | 1738 | 22 | frame 1057, `tails_x` |
| `TestS3kAizTraceReplay.rhinobotDoesNotDespawnOneFrameBeforeRomContact` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.replayMatchesTrace` | fail | 3876 | 17 | frame 4790, `tails_x` |
| `TestS3kCnzTraceReplay.traceReplayAppliesDelayedRightInputToTailsOnFrame123` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayAppliesFirstCarryRightPulseOnFrame31` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayAppliesFirstMainJumpOnFrame142` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayDoesNotPulseCarryRightOnFrame1` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayHorizontalSpringLandingHandoffMatchesFrame3649` | pass | 0 | 0 | full trace |
| `TestS3kCnzTraceReplay.traceReplayS3kRightWallPathRunsCeilingSeparationOnFrame5236` | pass | 0 | 0 | full trace |
| `TestS3kMgzTraceReplay.replayMatchesTrace` | fail | 2625 | 60 | frame 1538, `y` |

## 2026-05-18 - S2 level-select act coverage expansion

- Branch: `develop`
- Worktree state: dirty with generated S2 act fixtures and replay test additions
- Command: `mvn -Dmse=off '-Dtest=TestS2TraceRouteAssertions' test`
- Result: pass, 22 tests

S2 level-select replay coverage now has fixtures and concrete replay test
classes for every main Sonic 2 zone/act except EHZ Act 2. Multi-act BK2s are
split into independent fixtures using `OGGF_TRACE_GAMEPLAY_SEGMENT`, so each
fixture keeps its own `bk2_frame_offset` and contiguous gameplay input window.
DEZ uses the existing `dez_ending` fixture, which contains Death Egg gameplay
and the ending sequence. MTZ Act 3 uses ROM zone id `0x05` with raw act byte
`0`, and the recorder now emits metadata act 3 while retaining the raw ROM
zone/act in aux diagnostics.

| Fixture | Status | Errors | First error |
| --- | --- | ---: | --- |
| `TestS2Ehz1TraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Ehz2TraceReplay` | missing fixture/test | n/a | no BK2/fixture yet |
| `TestS2CpzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Cpz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2ArzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Arz2LevelSelectTraceReplay` | added test for existing fixture | n/a | not rerun |
| `TestS2CnzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Cnz2LevelSelectTraceReplay` | added test for existing fixture | n/a | not rerun |
| `TestS2HtzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Htz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2MczLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Mcz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2OozLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2Ooz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2MtzLevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2Mtz2LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2Mtz3LevelSelectTraceReplay` | added fixture/test | n/a | not rerun |
| `TestS2SczLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2WfzLevelSelectTraceReplay` | existing replay test | n/a | not rerun |
| `TestS2DezEndingLevelSelectTraceReplay` | added replay test for existing fixture | n/a | not rerun |

## 2026-05-17 - BizHawk input-indexing fix impact snapshot

- Branch: `feature/ai-trace-frontier-continuation`
- Commit under test: `535895780 Fix BizHawk trace input indexing`
- Worktree state: dirty with local CNZ slot-machine investigation edits
- Command: `mvn -q -Dmse=off '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
- Result: 31 test methods run, 14 failures

Recorder impact note: the committed recorder fix regenerated only the S2 CNZ
trace. CNZ physics row count stayed at 9469, input bytes were unchanged, and
all 9469 `vblank_counter` values changed from `0000` to real ROM VBlank
counter values. The focused clean CNZ replay stayed at the same first failure
before and after the recorder commit: frame 1691, 398 errors. The recorder fix
was diagnostic-impact only for CNZ, but it made VBlank-driven slot timing
debuggable.

| Test | Status | Errors | First error |
| --- | --- | ---: | --- |
| `TestTraceReplayInvariantGuard` | pass | 0 | |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel` | fail | n/a | first AIZ frame classified as `VBLANK_ONLY` instead of `FULL_LEVEL_FRAME` |
| `TestTraceReplayStartPositionPolicy.s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved` | fail | n/a | S3K MGZ setup prelude assertion failed |
| `TestTraceReplayStartPositionPolicy.vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState` | fail | n/a | `FULL_LEVEL_FRAME` strict-comparison assertion failed |
| `TestS1Credits00Ghz1TraceReplay` | pass | 0 | |
| `TestS1Credits01Mz2TraceReplay` | fail | 6 | frame 493, `y` |
| `TestS1Credits02Syz3TraceReplay` | pass | 0 | |
| `TestS1Credits03Lz3TraceReplay` | fail | 6 | frame 221, `y` |
| `TestS1Credits04Slz3TraceReplay` | pass | 0 | |
| `TestS1Credits05Sbz1TraceReplay` | pass | 0 | |
| `TestS1Credits06Sbz2TraceReplay` | pass | 0 | |
| `TestS1Credits07Ghz1bTraceReplay` | pass | 0 | |
| `TestS1Ghz1TraceReplay` | pass | 0 | |
| `TestS1Mz1TraceReplay` | pass | 0 | |
| `TestS2ArzLevelSelectTraceReplay` | fail | 831 | frame 304, `tails_x_speed` |
| `TestS2CnzLevelSelectTraceReplay` | fail | 413 | frame 1680, `air` |
| `TestS2CpzLevelSelectTraceReplay` | fail | 434 | frame 844, `x_speed` |
| `TestS2Ehz1TraceReplay` | pass | 0 | |
| `TestS2HtzLevelSelectTraceReplay` | fail | 398 | frame 5511, `x` |
| `TestS2MczLevelSelectTraceReplay` | fail | 394 | frame 1085, `y` |
| `TestS2OozLevelSelectTraceReplay` | fail | 1118 | frame 397, `tails_y` |
| `TestS2SczLevelSelectTraceReplay` | fail | 48 | frame 6222, `y_speed` |
| `TestS2WfzLevelSelectTraceReplay` | fail | 595 | frame 4720, `x_speed` |
| `TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` | fail | n/a | trace frame 2876, player X |
| `TestS3kAizTraceReplay.replayMatchesTrace` | fail | 1715 | frame 1057, `tails_x` |
| `TestS3kCnzTraceReplay` | fail | 3707 | frame 4790, `tails_x` |
| `TestS3kMgzTraceReplay` | fail | 2625 | frame 1538, `y` |

Next active target: S2 CNZ slot-machine release timing. With the local
investigation edits, CNZ moved from the clean-branch frame 1691 failure to frame
1680, where the engine releases Sonic from the Point Pokey cage at VBlank
`0x105A` while ROM still reports Sonic riding the cage.

## 2026-05-17 - S2 CNZ slot-machine frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with CNZ slot-machine/bootstrap fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 353 errors
- First error: frame 3830, `x_speed` (`expected=-0833`, `actual=0x0000`)

Frontier moved from the Point Pokey cage release at frame 1691 to the later
CNZ Big Block / bumper-contact segment at frame 3830. The slot-machine fix
used the regenerated `cnz_slot_machine_state` aux rows to verify the ROM target
word (`0x0203`), VBlank seed window, and same-call Routine5->Routine6
completion path. The aux rows remain diagnostic only; replay still drives the
engine from BK2 input plus one-time native timing bootstrap.

## 2026-05-17 - S2 CNZ object-streaming vertical-filter fix

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 placement fix and Big Block trace diagnostics
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3906, `tails_y` (`expected=0x06C0`, `actual=0x06BF`)

Frontier moved from the frame 3830 CNZ Big Block side-contact stop to frame
3906, where Tails lands/re-leaves a launcher spring one pixel lower than ROM.
The frame 3830 Big Block report showed only 39 engine updates for ObjD4 while
ROM-side ObjD4 had been active since frame 3330. The fix keeps the vertical
spawn-filter bypass scoped to Sonic 2 object placement, matching S2's
X-window-only `ObjectsManager_GoingForward` / `ObjectsManager_GoingBackward`
path (`docs/s2disasm/s2.asm:32870-32950`).

## 2026-05-17 - S2 CNZ Obj85 Tails recapture frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 launcher-spring fix and line-ending-only test-file noise
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3957, `tails_y` (`expected=0x06F1`, `actual=0x06F0`)

Frontier moved from frame 3906 to frame 3957. The frame 3906 mismatch was a
Tails-only Obj85 vertical launcher recapture: ROM had Tails already rolling on
the previous frame, but the engine's current-frame state had been normalized
before Obj85 capture code ran, so the object applied its Tails first-capture
one-pixel lift again. The fix makes Obj85 consult the previous recorded status
bit for Tails rolling recaptures and removes the extra top-landing lift for
Tails's shorter `$0F` standing radius. The next blocker is after the launcher
sequence: Tails lands from the launch with ROM still reporting rolling
(`status=0x05`) while the engine clears rolling (`status=0x01`).

## 2026-05-17 - S2 CNZ Obj85 roll handoff and ObjD4 lower-bound frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 roll-preservation and ObjD4 geometry fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 227 errors
- First error: frame 4121, `x_speed` (`expected=-058C`, `actual=-0559`)

Frontier moved from frame 3957 through the Obj85 Tails landing/roll-stop
sequence and then from frame 4074 to frame 4121 after the ObjD4 lower-bound
fix. Obj85 now preserves the vertical launcher rolling handoff only for that
object's post-release path, using its inclusive right-edge capture and
object-local ground-wall follow-up rather than changing shared roll or solid
behaviour. The later frame 4074 Sonic mismatch was ObjD4 Big Block: the engine
used the taller standing-radius lower-half overlap for a rolling airborne
player, falsely classified a side contact, shifted Sonic right, and zeroed
`x_speed`. S2 `SolidObject_cont` uses the live `y_radius(a1)` for ObjD4's
bottom reject bound, so ObjD4 now opts into
`fullSolidBottomOverlapUsesCurrentYRadiusOnly(...)`.

## 2026-05-17 - S2 CNZ bumper integer trig and speed-shoes timer phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ bumper trig, speed-shoes feature flag, trace diagnostics, and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.game.TestS3kCharacterSpeeds#speedShoesTimerStaysActiveForRomMovementFrames,com.openggf.game.TestPhysicsProfile#testSpeedShoesTimerPhaseCompensation_PerGame,com.openggf.game.TestHybridPhysicsFeatureSet#testExpectedHybridFeatureSetValues' test -DfailIfNoTests=false`
- Result: fail, 406 errors
- First error: frame 4351, `tails_x` (`expected=0x4000`, `actual=0x0E0D`)

Frontier moved from frame 4121 to frame 4351. The frame 4121 player
`x_speed` mismatch was the S2 CNZ map bumper angle-reflection path:
ROM uses `CalcAngle`/`CalcSine` integer tables before multiplying by
`-$A00` (`docs/s2disasm/s2.asm:32334-32677`), while the engine used a
floating-point `atan2`/`cos` approximation that rounded the incoming angle one
step differently. The later frame 4216 mismatch was speed-shoes expiry phase:
S2 `Obj01_ChkShoes` decrements the `$4B0` word timer from display after
movement and clears speed shoes on the decrement-to-zero frame
(`docs/s2disasm/s2.asm:36008-36025`), so the engine's pre-physics timer must
not add the extra phase tick for S2. S3K keeps the existing extra tick behind
`PhysicsFeatureSet.speedShoesTimerPrePhysicsExtraTicks()` because its ROM uses
a byte `(20*60)/8` timer decremented only every eighth frame
(`docs/skdisasm/sonic3k.asm:22067-22078,40818`). The next blocker is a Tails
despawn mismatch: ROM has Tails at the S2 off-screen marker `$4000,0`, while
the engine leaves her local at approximately `$0E0D,$0320`.

## 2026-05-17 - S2 CNZ Tails spawning object-control gate

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 strict sidekick spawning gate and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 459 errors
- First error: frame 4599, `tails_x` (`expected=0x0EA5`, `actual=0x0EA4`)

Frontier moved from frame 4351 to frame 4599. The frame 4351 mismatch was an
S2 `TailsCPU_Spawning` gate: ROM tests Sonic's raw `obj_control` byte before
respawning Tails (`docs/s2disasm/s2.asm:38743-38762`), while the engine only
checked the high-level full-object-control flag and missed a movement
suppression-only object-control state from Sonic's flipper/object release. The
fix is scoped to the strict S2-style spawning gate via the existing
`sidekickSpawningRequiresGroundedLeader()` feature; S3K's catch-up path keeps
its narrower gates. The next blocker is a Tails follow/respawn X drift: after
the marker delay and respawn, engine Tails is one pixel left of ROM at frame
4599.

## 2026-05-17 - S2 CNZ post-playable bumper timing and Tails landing radii

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ post-playable zone-feature hook, Obj85 Tails-only landing preserve, and shared landing-radius gate staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 219 errors
- First error: frame 5336, `tails_x_speed` (`expected=0x00C0`, `actual=0x00CC`)

Frontier moved from frame 4599 through a temporary f5294 Sonic landing
regression and the f5328 Tails one-pixel ground snap to frame 5336. The f4599
X drift came from S2 CNZ map bumpers updating too early relative to the
playable loop; `SpecialCNZBumpers` follows player/object execution in the ROM
main loop, so CNZ now updates its bumper manager through a zone-feature
post-playable-physics hook rather than the generic pre-player feature phase.
The f5294 Sonic regression was Obj85's vertical-launch roll-preserve hook
applying to Sonic even though `Sonic_ResetOnFloor` clears rolling normally.
The f5328 Tails Y mismatch came from shared landing cleanup restoring
non-rolling custom radii for S2 Tails; S2 `Tails_ResetOnFloor` only restores
Tails's `$0F/$09` standing radii inside the rolling branch
(`docs/s2disasm/s2.asm:40629-40636`), while the unconditional non-rolling
radius restore belongs to the S3K `Player_TouchFloor` radius-delta model. The
next blocker is a Tails Obj72/conveyor-area handoff: ROM puts Tails into
air+rolling with `y_vel=-0x680` at frame 5336, while the engine remains
grounded and continues normal CPU acceleration.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll delayed jump filtering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 preserved-roll sidekick CPU jump filtering and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 215 errors
- First error: frame 5399, `tails_y` (`expected=0x0329`, `actual=0x032A`)

Frontier moved from frame 5336 to frame 5399. The frame 5336 mismatch was the
same Obj85 vertical-stopper preserved-roll handoff, but on the sidekick CPU
input path: the engine suppressed all delayed jump state while the preserved
roll flag was set. That kept the earlier grounded stopper frames stable but
also cleared the fresh delayed Sonic jump press ROM later copies through
`TailsCPU_Normal` (`docs/s2disasm/s2.asm:38939-38946`), preventing
`Tails_Jump` from entering air+rolling with `y_vel=-$680`
(`docs/s2disasm/s2.asm:36996-37070`). The filter now remains scoped to the
grounded Obj85 preserved-roll handoff: stale held jump with no fresh delayed
press is cleared, while the later fresh delayed press is allowed through. The
next blocker is a one-pixel Tails Y mismatch after the later airborne/dead
routine handoff around frame 5399; ROM has `y=$0329`, engine has `y=$032A`.

## 2026-05-17 - S2 CNZ dead-fall ObjectMoveAndFall ordering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with shared death-movement ordering and S2 deferred generic-dead despawn fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 200 errors
- First error: frame 5951, `tails_y` (`expected=0x02B1`, `actual=0x02B0`)

Frontier moved from frame 5399 through the S2 Tails dead-fall marker handoff
to frame 5951. The frame 5399 mismatch came from the shared generic death
path moving with the post-gravity `y_vel`; ROM dead/fall routines load the old
velocity for position, then store the gravity-incremented velocity for the
next frame (`docs/s2disasm/s2.asm:37901-37911,40736-40738,29967-29981`;
S1 `docs/s1disasm/_incObj/01 Sonic.asm:1792-1795`; S3K
`docs/skdisasm/sonic3k.asm:29280-29285,36068-36083`). The frame 5544 mismatch
was the same S2 `Obj02_Dead` deferred-despawn rule reached through the generic
`dead` flag instead of the sidekick CPU `DEAD_FALLING` state: ROM checks
`Tails_Max_Y_pos + $100`, branches to `TailsCPU_Despawn`, then still runs that
frame's `ObjectMoveAndFall` (`docs/s2disasm/s2.asm:40736-40759,39043-39052`).
The new blocker is a Tails air+rolling transition near an Obj74 invisible
block/elevator area: ROM sets Tails airborne+rolling with `y_vel=-$680`, while
the engine leaves Tails grounded at `y=$02B0`.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll lifetime scope

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj85 preserved-roll lifetime scope fix
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 197 errors
- First error: frame 5997, `x_speed` (`expected=0x0000`, `actual=0x000C`)

Frontier moved from frame 5951 to frame 5997. The frame 5951 mismatch came from
the Obj85 preserved-roll marker leaking beyond the rolling stopper handoff: by
frame 5951 Tails was grounded and non-rolling with the marker still set, so the
engine suppressed the normal-follow auto-jump that ROM takes once the preserved
rolling state is gone. The filter now requires Tails to still be rolling before
it suppresses the Obj85 stale push/jump handoff (`docs/s2disasm/s2.asm:38939-38946,
39015-39022,57611-57625`). The new blocker is not ObjD5 gameplay: the trace CSV
input edge turns RIGHT before S2 ROM `Ctrl_1_Held_Logical` reaches `Sonic_Move`
(`docs/s2disasm/s2.asm:701,1361-1387,36253-36260`), so replay currently drives
Sonic one input edge earlier than the sampled ROM physics row.

## 2026-05-17 - S2 CNZ ObjD5 riding horizontal-input phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD5 riding-input phase hook, SCZ Tornado hook migration, regenerated CNZ trace v9.6 diagnostics, and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 289 errors
- First error: frame 6018, `tails_g_speed` (`expected=0x0000`, `actual=0x0018`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 5997 to frame 6018. The frame 5997 mismatch was a
V-int/logical-input phase issue while Sonic was riding S2 CNZ ObjD5. The
regenerated v9.6 recorder snapshots showed no `move_lock` or control lock;
instead, the BK2/CSV right edge was visible before the ROM physics row showed
the corresponding inertia change. The fix is object-scoped: shared movement
now asks the current riding `SolidObjectProvider` whether to suppress newly
pressed horizontal logical input for a small number of riding frames, with a
default of zero. ObjD5 opts into three frames based on its
`PlatformObjectD5`/`MvSonicOnPtfm` helper path (`docs/s2disasm/s2.asm:58435-58443,
35617-35657,35402-35420`). The existing SCZ Tornado stale logical-input
compensation was migrated onto the same object hook so shared movement no
longer hard-codes a Sonic 2 object id. The new blocker is Tails follow
acceleration after the Obj74/invisible-block jump window: Sonic matches at
frame 6018, but engine Tails has already applied follow steering
(`tails_g_speed=0x0018`) while ROM keeps Tails at zero ground speed.

## 2026-05-17 - S2 CNZ Obj74 SolidObject_Always offscreen bypass

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj74 offscreen-gate bypass, focused unit test, and mirrored S2 object skill notes staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestInvisibleBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 290 errors
- First error: frame 6276, `y_speed` (`expected=0x0070`, `actual=-0700`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6018 to frame 6276. The frame 6018 mismatch was S2
Obj74 invisible block side contact: subtype `$33` gives `width_pixels=$20`,
so `Obj74_Main` passes `d1=$2B` and the left edge is `$1560-$2B=$1535`,
matching ROM Tails's stopped X position. The engine skipped the contact
because Obj74 had not opted out of the shared offscreen sidekick full-solid
gate. Obj74 calls `SolidObject_Always`, whose ROM helper explicitly checks
solidity even when the object is offscreen
(`docs/s2disasm/s2.asm:34863-34873,46152-46161`), so the bypass is scoped to
`InvisibleBlockObjectInstance` rather than changing the game-wide solid gate.
The new blocker is a Sonic vertical-speed mismatch after the later monitor /
bonus-block area: ROM is falling with `y_vel=$0070` at frame 6276 while the
engine has entered an upward launch/rebound path (`y_vel=-$0700`).

## 2026-05-17 - S2 CNZ ObjD8 touch radius and inertia preservation

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD8 touch/bounce fix, focused unit test, and mirrored S2 object skill notes staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestBonusBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 284 errors
- First error: frame 6815, `y_speed` (`expected=-017A`, `actual=-06C8`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6276 through the first ObjD8 hit to frame 6815. The
frame 6276 mismatch was an early CNZ Bonus Block hit: ObjD8 sets
`collision_flags=$D7`, so the SPECIAL touch size is `Touch_Sizes[$17] = 8,8`
radii, not the engine's old center-distance 12x24 box
(`docs/s2disasm/s2.asm:59570,84623`). The object now uses the ROM
TouchResponse rectangle math for its local collision check. A follow-up frame
6281 mismatch showed that ObjD8's `loc_2C806` bounce tail sets in-air, clears
roll-jump/pushing/jumping, and plays the bonus-bumper sound, but does not
clear `inertia`; the engine now preserves `g_speed`
(`docs/s2disasm/s2.asm:59687-59692`). The new blocker is a later ObjD8 /
bumper cluster around frame 6815, where ROM has a shallow reflected velocity
but the engine has already applied a strong vertical rebound.

## 2026-05-17 - S2 CNZ ObjD8 TouchResponse latch timing

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD8 latch-timing fix and focused unit test staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestBonusBlockObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 132 errors
- First error: frame 7420, `x` (`expected=0x1C6C`, `actual=0x1C67`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 6815 to frame 7420. The frame 6815 mismatch was
ObjD8 firing inside its own update by re-polling current player overlap. ROM
does not do that: `TouchResponse` latches `collision_property(a0)`, then
`ObjD8_Main` consumes P1/P2 bits and per-player cooldown bytes at `objoff_30`
before calling `loc_2C74E` (`docs/s2disasm/s2.asm:59565-59604,59684-59702`).
ObjD8 now follows the same object-local latch model as S2 Obj44 instead of
changing the shared touch routine. The new blocker is a later forced-spin /
camera-X mismatch after the CNZ bumper/bonus-block cluster: player speed and
subpixel state match, but engine camera X is five pixels left when Sonic enters
Obj84.

## 2026-05-17 - S2 CNZ Obj84 wall-mode autoroll X preservation

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj84 wall-mode autoroll fix and focused unit test staged next
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestForcedSpinObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 130 errors
- First error: frame 7874, `camera_y` (`expected=0x020E`, `actual=0x020C`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 7420 to frame 7874. The frame 7420 mismatch was
S2 Obj84 forced-spin autoroll on a wall-mode surface: `Obj84` sets rolling,
writes the rolling radii/status/animation, and applies `addq.w #5,y_pos(a1)`,
but it never adjusts `x_pos` (`docs/s2disasm/s2.asm:46377-46495`). The engine's
generic wall-mode rolling transition can shift centre X when the width changes,
so Obj84 now preserves the ROM centre X only on this object path. The next
blocker is a later platform/ride-state camera-Y mismatch: player position and
velocity match at frame 7874, but ROM has the on-object status bit and camera
Y two pixels lower than the engine.

## 2026-05-17 - S2 CNZ Obj26 SolidObject_cont geometry

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj26 geometry fix staged next; unrelated line-ending-only dirty files remain unstaged
- Rejected experiment: overriding S2 Obj26 `getMonitorSolidObjectVerticalOffset()` to `4` regressed CNZ to frame 7870, `x_speed` (`expected=0x0450`, `actual=0x0000`), because it combined the generic `SolidObject_cont` vertical bias with the SPG monitor-special side classifier.
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestMonitorObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 56 errors
- First error: frame 8082, `x_speed` (`expected=-0800`, `actual=0x0517`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 7874 to frame 8082. The frame 7874 mismatch was S2
Obj26 monitor solid geometry: `SolidObject_Monitor_Sonic` only gates the
rolling animation hit, then branches into generic `SolidObject_cont`
(`docs/s2disasm/s2.asm:25448-25452`). Engine Obj26 was still opting into the
shared SPG monitor-special classifier, whose top/side geometry differs from
S2 `SolidObject_cont` and missed the ROM standing-bit transition on the
monitor at slot 19 (`0x1E10,0x0291`). The fix is per-object and per-game:
S2 Obj26 now uses normal solid classification after its existing roll gate.
S1 monitor behavior and S3K monitor behavior are unchanged. The new blocker is
an Obj84/ObjD4/bumper region handoff: at frame 8082 ROM launches Sonic left at
`x_speed=-$0800`, while the engine continues right at `x_speed=$0517`.

## 2026-05-17 - S2 CNZ ObjD7 moving range out_of_range

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 ObjD7 range-delete fix staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestHexBumperObjectInstance" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 11 errors
- First error: frame 8419, `tails_x` (`expected=0x0000`, `actual=0x4000`)
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail only on CNZ; S1 GHZ and S2 EHZ reports show `Failures: 0, Errors: 0`

Frontier moved from frame 8082 to frame 8419. The frame 8082 mismatch was a
missing moving ObjD7 Hex Bumper from CNZ spawn `0x1FF8,0x028C` subtype `1`:
ROM kept it alive at slot 38 and launched Sonic left, while the engine had
deleted it with the generic single-X `out_of_range` predicate. Moving ObjD7
does not tail-call `MarkObjGone`; it tests both `objoff_30` and `objoff_32`
movement bounds and deletes only when both are outside the camera window
(`docs/s2disasm/s2.asm:59489-59510`). The shared object manager now has a
narrow custom out-of-range hook, and only moving ObjD7 opts in; all other
objects keep the previous shared path unless they explicitly override it. The
new blocker is a later sidekick despawn/comparator mismatch at frame 8419.

## 2026-05-17 - S2 CNZ Tails flying timeout marker split

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Tails respawn/flying timeout fix staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity#s2FlyingRespawnTimeoutReturnsToSpawningAtZeroMarker" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity,com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 10 errors
- First error: frame 9147, `tails_x` (`expected=0x4000`, `actual=0x2908`)

Frontier moved from frame 8419 to frame 9147. The frame 8419 mismatch was not
the normal S2 `TailsCPU_CheckDespawn` marker path; ROM was in
`TailsCPU_Flying`'s offscreen timeout, which writes `x_pos=0,y_pos=0`,
`Tails_CPU_routine=2`, `obj_control=$81`, and `Status_InAir`
(`docs/s2disasm/s2.asm:38795-38806`). The engine's APPROACHING path had run
the generic normal despawn pre-check first, producing the `$4000,0` marker
instead. Tails' respawn strategy now owns the S2 flying timeout and keeps S3K
on its existing catch-up-flight path. The new blocker is an end-of-act Tails
state mismatch: ROM marks Tails airborne and at the `$4000,0` normal marker
around frame 9147 while the engine has already settled Tails beside Sonic.

## 2026-05-17 - S2 CNZ Tails shared respawn counter green

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Tails respawn-counter carry fix, focused parity test, frontier log, and mirrored trace skill notes staged next; unrelated line-ending-only dirty files remain unstaged
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.sprites.playable.TestSidekickCpuDespawnParity" test -DfailIfNoTests=false`
- Result: pass
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: pass, 0 errors
- First error: none

CNZ is green. The frame 9147 blocker was the second half of the S2 Tails
offscreen-counter split: ROM uses one `Tails_respawn_counter` word across
`TailsCPU_Flying` and the later NORMAL `TailsCPU_CheckDespawn`. The engine had
correctly separated the flying timeout marker (`0,0`) from the normal marker
(`$4000,0`), but then discarded the accumulated offscreen fly-in frames when
the approach completed. CNZ clears Tails' render flag at frame 8847, completes
fly-in at frame 8862 with 16 counted offscreen frames, and therefore reaches
the shared 300-frame normal despawn deadline at frame 9147. The fix is scoped
to the Tails respawn strategy carry hook; other sidekick strategies and S3K's
catch-up-flight path keep independent NORMAL despawn timers.

## 2026-05-18 - S2 CNZ2 frame-0 jump-edge bootstrap fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1193 errors (down from 1262)
- First error: frame 16, `tails_air` (was frame 0, `air`)

The CNZ2 trace recorded the player holding the jump button (input `0x0010`)
continuously from the last title-card frame through the first dozen gameplay
frames. The headless replay fixture skips the title card entirely, so the
leader's `jumpInputPressedPreviousFrame` / `PlayableSpriteMovement.jumpPrevious`
edge trackers entered gameplay frame 0 with virgin `false`. When the BK2
delivered a held jump on frame 0, the engine's edge detector computed
`(jump && !false) = true` and fired `Sonic_Jump` (`docs/s2disasm/s2.asm:36253-36260`)
one frame ahead of ROM, perturbing frame-0 `y`, `y_speed`, `air`, `rolling`, and
cascading to every subsequent row.

Fix: `TraceReplaySessionBootstrap.applyBootstrap` now reads the BK2 input
mask at the cursor offset `-1` (the last title-card frame the production
GameLoop would have ticked) and seeds both the sprite-level jump edge
(`AbstractPlayableSprite.setJumpInputPressed`) and the movement-controller
edge (`PlayableSpriteMovement.primeJumpPreviousForBootstrap`) accordingly.
This mirrors ROM's continuously-updated `Ctrl_1_Held` (`s2.asm:701,1361-1387`
`ReadJoypads` — updated from V-int regardless of `Sonic_ControlsLock`) so the
engine's edge detector correctly computes a held-not-pressed state.

Cross-game regression: S1 GHZ1, S2 EHZ1, S2 CNZ1 all stayed green. Other
act-2 traces (MTZ2, ARZ2, MCZ2, MTZ3, OOZ2, HTZ2, CPZ2) start frame 0 with
no jump held so their frontiers are unchanged.

Next active target: frame 16 `tails_air` on CNZ2. Diagnostic shows Tails CPU
emitting held jump state but Tails ends up `air+rolling` post-physics. The
bootstrap history-pos mismatch (`0x0068` ROM vs `0x0019` engine) may be
causing the 16-frame delayed jump-press lookup to read the wrong slot.

## 2026-05-18 - S2 CNZ2 Tails held-jump bootstrap fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1217 errors
- Frontier moved: frame 16 `tails_air` → frame 205 `camera_x`
- Cross-game `*TraceReplay` sweep: 41 tests / 22 failures, same baseline — no regressions

Root cause: `PlayableSpriteMovement.storeInputState` computed
`inputJumpPress = (jump && !jumpPrevious) || forcedJumpPress` uniformly for
both human-driven and CPU-controlled sprites. For CPU sprites whose `aiJump`
mirrors the leader's delayed held bit but whose `aiJumpPress` mirrors the
delayed press byte, the edge calculation against Tails' virgin `jumpPrevious=false`
manufactured a spurious press the first time the leader's held bit transitioned
from clear to set. CNZ2's BK2 starts with jump held in the title-card prelude,
so Tails inherited a recorded held jump bit without a matching recorded press
byte, and fired `doJump()` at frame 16 putting her into `air+rolling y_speed=-0x680`.

Fix: for `sprite.isCpuControlled()` consume only the explicit `forcedJumpPress`
signal from `SidekickCpuController.getInputJumpPress()`. ROM cite:
`TailsCPU_Normal` (`docs/s2disasm/s2.asm:38939-38946,39025-39027`) writes
the whole delayed `Ctrl_1_Logical` word into `Ctrl_2_Logical`, so Tails'
`Ctrl_2_Press` low byte always matches the leader's delayed press byte rather
than re-deriving an edge from her own held bit.

Note: the `player_history.pos expected=0x0068 actual=0x0019` BootstrapDivergence
is a comparator unit-mismatch (ROM byte offset vs engine slot index) — both
represent the same ring fill state. Not the root cause of the frame-16 failure.

## 2026-05-18 - S2 CNZ2 horizontal spring inclusive right-edge fix

- Branch: `develop`
- Command: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1130 errors (down from 1217)
- Frontier moved: frame 205 `camera_x` → frame 435 `x_speed`

Root cause: `SpringObjectInstance.getSolidParams()` (HORIZONTAL subtype) did not
override `usesInclusiveRightEdge()`, so it defaulted to `false` (exclusive `>=`).
At frame 205 Sonic's centre X = 539 = spring.centreX (520) + halfWidth (19), exactly
the right boundary. ROM `SolidObject_cont` (`docs/s2disasm/s2.asm:35147-35150`)
rejects with `bhi` (strictly greater), so `relX == width*2` IS a valid contact.
The engine's default `>=` treated that same case as out-of-range and skipped the
spring entirely. Spring at (0x0208, 0x05F0) then fired correctly: Sonic snapped to
0x0213 (x−8) with x_vel=+$1000, matching the CSV transition at frame 205.

Fix: `SpringObjectInstance` now overrides `usesInclusiveRightEdge()` to return `true`
when `getType() == TYPE_HORIZONTAL`, matching the same pattern already used in
`Sonic3kSpringObjectInstance` for `TYPE_HORIZONTAL`. ROM reference:
`Obj41_Horizontal` routes through `SolidObject_Always_SingleCharacter` →
`SolidObject_cont` (`docs/s2disasm/s2.asm:33780-33784,35147`), whose X gate uses
`bhi`, making the right-edge pixel a valid side contact.

New blocker: frame 435 `x_speed` (expected=0x01CD, actual=-0x05FE). Engine has
Sonic moving fast leftward while ROM continues rightward; engine diagnostic shows
a Crawl (0xC8) touch at slot 20 and matching subpixel position at frame start.

## 2026-05-18 - S2 CNZ2 Crawl (0xC8) bounce physics and ENEMY dispatch fix

- Branch: `develop`
- Command: `mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1078 errors (down from 1130)
- Frontier moved: frame 435 `x_speed` → frame 554 `tails_y`

Root cause: Two bugs in `CrawlBadnikInstance` and one in `ObjectManager` ENEMY dispatch.

1. **Wrong collision size index** (`CrawlBadnikInstance.COLLISION_SIZE_INDEX`): was 0x09
   (Touch_Sizes[9] = 12×16 px) instead of ROM's 0x17 (Touch_Sizes[23] = 8×8 px).
   This caused a wider touch window, producing the first touch 3 frames earlier
   (frame 435) than the actual first contact (frame 438).

2. **Incorrect radial bounce**: `applyBounce` applied a flat −$700 y_vel instead of
   the ROM's radial `CalcAngle + CalcSine × −$700 / 256` formula (`loc_3D3A4`,
   s2.asm:81932-81958). Rewritten using `TrigLookupTable.calcAngle` / `cosHex` /
   `sinHex`, matching the wobble `(Level_frame_counter >> 8) & 3` exactly.
   For the trace contact at frame 438 (dx=16, dy=12, angle=27), expected
   y_vel = −$44B (−1099) was confirmed correct.

3. **Double-bounce from ENEMY dispatch** (`ObjectManager.handleTouchResponse`):
   after `onPlayerAttack` applied the radial bounce (y_vel=−1099), the ENEMY path
   called `applyEnemyBounce` because `hpBeforeHit=0 && !wasAlreadyDestroyed`.
   `applyEnemyBounce` overwrote y_vel to −843 (+256 offset vs expected). Fix:
   `applyEnemyBounce` is now conditional on `instance.isDestroyed()` after
   `onPlayerAttack`. Normal badniks (destroy-in-one-hit) are unaffected; Crawl's
   shield bounce (survives) correctly skips the second bounce.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/badniks/CrawlBadnikInstance.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`

New blocker: frame 554 `tails_y` (expected=0x05F1, actual=0x05F0). Tails Y off by
1 px when rolling-mode transition occurs (engine `st=00`, ROM `status=04`). Crawl
in slot 20 is in WALKING state (rtn=02), not involved. Likely a Tails rolling-mode
transition timing issue: `tails mode rolling 0→1` annotation present at frame 554.

## 2026-05-18 - S2 CNZ2 pinball-mode Tails jump and landing-clear fixes

- Branch: `develop`
- Command: `mvn -q "-Dtest=com.openggf.tests.trace.s2.TestS2Cnz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 1057 errors (down from 1078)
- Frontier moved: frame 554 `tails_y` → frame 1490 `tails_y_speed`

Root cause: Two bugs in sidekick CPU and landing logic related to `pinball_mode`
(set by S2 Obj84/FORCED_SPIN when Tails crosses the x=0x0400 horizontal trigger
in CNZ2).

1. **`PlayableSpriteMovement.resetOnFloor()` cleared `pinball_mode` unconditionally**:
   ROM `Tails_ResetOnFloor` (`docs/s2disasm/s2.asm:40624-40660`) branches to
   `Tails_ResetOnFloor_Part3` when `tst.b pinball_mode(a0)` is non-zero (`bne.s`),
   skipping the roll-clear block entirely. Part3 only clears in_air/pushing/
   rolljumping/jumping flags — it never touches `pinball_mode`. The engine's
   unconditional `sprite.setPinballMode(false)` was clearing pinball mode on every
   landing, causing the 16-frame delayed B-press to fire a jump at frame 936 when
   pinball mode should have been preserved. Fix: made the `setPinballMode(false)` call
   conditional on `!(rolling && pinballMode && preservePinballRoll)`, matching both
   ROM `Sonic_ResetOnFloor` (`s2.asm:37770-37771`) and the already-correct
   `CollisionSystem.java` implementation.

2. **`SidekickCpuController.updateNormal()` did not suppress jump when rolling in
   pinball mode**: ROM `Obj02_MdRoll` (`docs/s2disasm/s2.asm:39279-39282`) skips
   `bsr.w Tails_Jump` entirely when `pinball_mode` is set. The engine's 16-frame
   delayed press at frame 937 (Tails lands at frame 930-931, fresh B-press delayed
   from frame 921) would reach `doJump()` unguarded. Fix: added pinball-mode check
   in `updateNormal()` after the existing Obj85 rolling suppression block.

3. **`doCheckStartRoll()` narrowed CPU move_lock guard to S3K only**: S1/S2
   `Sonic_RollStart` has no `move_lock` gate (`docs/s2disasm/s2.asm:36954-36963,
   39939-39942`). The prior over-broad guard for all games blocked S2 Tails from
   rolling during move_lock even though the ROM never suppresses it. Narrowed to
   S3K only (where `Tails_InputAcceleration_Path` at `sonic3k.asm:27797-27815` gates
   duck/direction input on `move_lock`). This allowed the frame 554 Tails rolling
   start to propagate correctly. Updated `TestPlayableSpriteMovement.
   cpuSidekickMoveLockSuppressesGeneratedDownRoll` to use S3K physics explicitly.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/badniks/CrawlBadnikInstance.java`
  (deferred ATTACKING touch via `TouchResponseListener`, `getCollisionFlags()` 0xD7)
- `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
  (`resetOnFloor` pinball guard, `doCheckStartRoll` S3K-only move_lock guard)
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
  (pinball-mode jump suppression in `updateNormal`)
- `src/test/java/com/openggf/sprites/managers/TestPlayableSpriteMovement.java`
  (updated `testS2LandingPreservesRollingInPinballMode` and
  `cpuSidekickMoveLockSuppressesGeneratedDownRoll` to match ROM-accurate behavior)

New blocker: frame 1490 `tails_y_speed` (expected=-0x059B, actual=-0x052B).
Tails bounced by ATTACKING Crawl (rtn=06, slot 19) at x=0x0735, but engine Crawl
is at x=0x732 (3 px left). The 3-pixel x-position difference changes the `CalcAngle`
result, reducing the upward component of the radial bounce by 0x70. Root cause is
a Crawl walking-position drift accumulated before the ATTACKING transition —
likely the proximity check fires at a different frame in engine vs ROM, causing the
Crawl to stop walking at a different x.

## 2026-05-19 - S2 MTZ Long Platform PROPERTIES table index + non-conveyor carry fixes

- Branch: `develop`
- Command: `mvn -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2189 errors (down from 2370)
- Frontier moved: frame 221 `y` -> frame 305 `y` (now an MTZ2 conveyor landing
  issue at slot s29 `0x6C` Conveyor near `(0x0320,0x0607)`)
- Cross-game `*TraceReplay` regression: S1 GHZ1, S2 EHZ1, S2 CNZ, S2 CNZ2, S2 ARZ,
  S2 MTZ3, S2 OOZ unchanged. S2 MTZ act 1 dropped from 1015 -> 989 errors at the
  same frame 281 `y` Steam Spring frontier.

Root cause: Two bugs in `MTZLongPlatformObjectInstance` (s2.asm Obj65).

1. **`PROPERTIES` table indexed by `d0 >> 2` instead of `d0 >> 1`**: ROM
   `Obj65_Init` (`docs/s2disasm/s2.asm:52379-52414`) computes
   `d0 = (subtype >> 2) & $1C` as the **byte offset** into the 2-byte-per-entry
   `Obj65_Properties` table (`s2.asm:52366-52376`), then performs a second
   `lsr.w #2,d0` to derive the mapping_frame (= entry_index / 2). The engine was
   conflating these two values and using `d0 >> 2` (= mapping_frame) as the
   `PROPERTIES[]` row index, so a button-spawned platform with subtype `0xB1`
   (entry 6, mapping_frame 3) ended up reading entry 3 `{0x40, 0x03}` instead
   of entry 6 `{0x40, 0x0C}` for width/y_radius, and entry 4 `{0x10, 0x10}`
   instead of entry 7 `{0x80, 0x07}` for maxDist and the child subtype. With
   `childSubtype` decoded as `0x10` rather than `7`, the engine set
   `moveSubtype = 0x10 & 0x0F = 0` (stationary) and skipped the
   `currentDist = maxDist` seeding, so the button-triggered platform at
   `(0x02C0, 0x064D)` in MTZ2 never moved off its spawn x. Fix: split the
   shared index into `entryIndex = d0 >> 1` (table row) and
   `frameIndex = d0 >> 2` (mapping_frame).

2. **Continued-riding carry applied for non-conveyor movement subtypes**: ROM
   `Obj65` saves `x_pos` to `objoff_2E` before each frame's subtype routine
   (`s2.asm:52454`) and uses `objoff_2E` as the `d4` carry reference for
   `MvSonicOnPtfm` (`s2.asm:35402-35423`). The movement routines for subtypes
   1/2/6/7 (`loc_26D50`) and 3 (`loc_26E1A`) refresh `objoff_2E` to the new
   `x_pos` after the move, so `MvSonicOnPtfm` computes a zero carry delta and
   the rider stays still while the platform glides underneath. Only the
   conveyor subtype 5 (`loc_26E4A`) leaves `objoff_2E` untouched, producing
   the conveyor's `+2 px/frame` rider carry. The engine's `ObjectManager`
   inline continued-riding path was unconditionally shifting the rider by
   `currentX - ridingX`, so once the platform-index bug was fixed and the
   subtype-7 retract started moving, Sonic was being carried +2 px/frame
   matching the platform speed. Fix: added
   `SolidObjectProvider.carriesRiderOnHorizontalMove(player)` (default
   `true`) and overrode it on `MTZLongPlatformObjectInstance` to return
   `moveSubtype == 5`, so only the conveyor subtype carries the rider.

Files changed:
- `src/main/java/com/openggf/game/sonic2/objects/MTZLongPlatformObjectInstance.java`
- `src/main/java/com/openggf/level/objects/SolidObjectProvider.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`

DEZ ending investigation (no fix this session): frontier still at frame 536
`rolling` (expected=1, actual=0) for `TestS2DezEndingLevelSelectTraceReplay`,
174 errors. The trace shows Sonic in an air+rolling jump from frame 417 with
a hard radial velocity reversal at frame 536 (`xv 0x0178 -> 0xFE70`,
`yv 0x03E0 -> 0xFBE8`) inside a fixed-camera arena (`camera_x=0x0224`). The
nearby DEZ_1 layout has only Obj2D barriers and the Obj C7 Death Egg Robot,
none at the bounce x; the reversal pattern looks like a scripted
boss-arena bounce. The default trace reporter does not surface enough
post-bounce engine state to localise which rolling-clear path fires before
frame 536. Tractable next step: extend the trace recorder to capture the
ENEMY/ATTACK touch slot per frame so the engine's first rolling-clear can be
isolated, or replay just the DEZ ending with a per-frame rolling-state
printout to localise the divergent frame.

## 2026-05-19 - S2 MTZ3 Obj6A per-phase activation gate

- Branch: `worktree-agent-ac2f125d8f7e5c121` (merged from `develop`)
- Command: `mvn -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2626 errors (was 2349)
- Frontier moved: frame 340 `tails_g_speed` -> frame 460 `tails_air` (Tails
  missed-landing event; ROM lands and tails_x jumps to 0x4000 indicating
  CPU respawn / despawn marker; engine keeps falling)
- Cross-game `*TraceReplay` regression check:
  - EHZ1: green (all frames match)
  - MTZ1: still frame 375 `tails_air`, errors 945 -> 905 (improved)
  - MTZ2: unchanged at frame 305 `y` (conveyor-cluster landing parity, separate bug)
  - ARZ/ARZ2/CPZ/CPZ2/CNZ/CNZ2/HTZ/HTZ2/MCZ/MCZ2/OOZ/OOZ2/SCZ/WFZ/DEZ all
    unchanged
  - S3K AIZ/CNZ/MGZ unchanged

Root cause: `MCZRotPformsObjectInstance.loadPhaseParameters()` did not mirror
ROM `loc_27CA2`'s `move.b #0,objoff_36(a0)` (`docs/s2disasm/s2.asm:53844`).
ROM Obj6A in MTZ routes through routine 2 (`loc_27BDE` at s2.asm:53754) which
gates `ObjectMove` on `objoff_36` (the activation flag). Each call to
`loc_27CA2` clears that flag at the end, so the next frame falls back to
standing-bit edge detection (`loc_27BDE` -> `loc_27C28`) and the platform
waits for another walk-off before running its next phase. The engine's
prior implementation latched `activated=true` permanently after the first
walk-off, so once Sonic stepped off any MTZ Obj6A it would cycle through
all four MTZ phases unattended -- across ~340 frames the cumulative phase
offset put slot 17's platform 0x2C px right and 0x16 px up of ROM, exactly
the position where Tails landed on the engine's platform top while ROM's
Tails kept falling.

Fix: in `loadPhaseParameters`, clear `activated` when `isMtz` (MCZ uses
routine 4 `loc_27C66` which ignores `objoff_36`, so its `activated=true`
seed must survive). Also documented the ROM citation alongside the reset.

File changed:
- `src/main/java/com/openggf/game/sonic2/objects/MCZRotPformsObjectInstance.java`

New blocker: frame 460 `tails_air` expected=0, actual=1, with cascading
tails_y_speed/tails_g_speed/tails_y/tails_x errors. ROM Tails y_speed jumps
from 0x428 (still falling) at f459 to 0 at f460 (landing), tails_x lifts to
0x4000 at f461 (CPU despawn marker). Engine continues falling with
y_speed=0x450. Diagnosis target: identify which solid surface ROM Tails
lands on at f460 (`pos=(0x0328, 0x02D0)`) and why engine misses it.

MTZ2 conveyor frontier (still frame 305 `y` 0x05EB vs 0x05F2): investigated
but not fixed. Engine player lands on a child conveyor whose Y is ~7 px
lower than ROM's equivalent conveyor (slot s29 `0x6C @0320,0607`). The
engine's `eng-near` debug list shows only one of the three left-column
conveyors (column 0x0320), so the offending slot 127 is presumably the
middle child at ~y=0x060E rather than the ROM's y=0x0607. Layout offset
tables and parent base-position routing match ROM exactly; the displacement
suggests either differential waypoint-velocity timing among siblings or a
spawn position issue not visible in the current trace surface. Tractable
next step: extend the trace recorder to dump ALL conveyor slot positions
(not just the truncated near list) so the engine's full column-0x0320
conveyor set can be diff'd against ROM s28/s29/s30.

## 2026-05-19 — S2 MTZ2 Conveyor (Obj6C) parent factory re-spawn loop fix

- Target: `mvn -q -Dmse=off "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz2LevelSelectTraceReplay#replayMatchesTrace" test`
- Baseline (HEAD develop / `1b5308308`): MTZ2 frontier frame 305, y 0x05F2 vs ROM 0x05EB (7 px), 2325 errors
- Result: frame 305, y 0x05EA vs ROM 0x05EB (1 px, sub-pixel residual), 2770 errors
- Branch context: worktree `agent-a6827fe9d2a969098` on `develop`

Root cause. `ConveyorObjectInstance.createOrSpawnChildren` returned `null` for
parent-spawner subtypes (bit 7 set), expecting `ObjectManager` to skip
registering the parent in `activeObjects`. The placement loop
(`syncActiveSpawnsLoad` `sortedNewSpawns` gate at
`ObjectManager.java:2362`) only filters spawns already in `activeObjects`,
so the parent re-entered the spawn list every frame the chunk stayed in the
camera window. Diagnostic logging in `createOrSpawnChildren` showed the
8-child cohort spawning 207,312 times across the test run (≈ 25,914 parent
re-spawns).

Fix. Mirror ROM `Obj6C_Init` `loc_28112` (`s2.asm:54269-54301`), which uses
`movea.l a0,a1` to reuse the parent slot as the first child:
`createOrSpawnChildren` now constructs the first child instance from
`layout[0]` using the parent's own spawn and returns it as the factory
result. The remaining 7 children spawn via `addDynamicObject` as before.
Because the factory now returns non-null, `registerActiveObject(spawn,
firstInstance)` runs and the placement's `activeObjects.containsKey(spawn)`
gate suppresses re-spawn.

Verification:
- `TestS2Mtz2LevelSelectTraceReplay`: frame 305 first error, y 7 px → 1 px
- `TestS2MtzLevelSelectTraceReplay`: unchanged at frame 375 `tails_air`
- `TestS2Mtz3LevelSelectTraceReplay`: unchanged at frame 460 `tails_air`
- `TestS2Ehz1TraceReplay`: green
- `TestS2CnzLevelSelectTraceReplay`: unchanged 22 errors at frame 3906
  `tails_y` 1 px (pre-existing sub-pixel residual)
- `TestS2Mcz2LevelSelectTraceReplay`: unchanged at frame 1079, 802 errors
- All S3K HCZ conveyor unit tests pass

The 1 px residual at MTZ2 f305 is sub-pixel rounding at landing; the
conveyor cluster positions now match ROM exactly (engine s28 @0320,0607
matches ROM s29 @0320,0607; engine s29 @0320,064D matches ROM s30
@0320,064D).

MTZ1 frontier (frame 375 `tails_air` expected 1 actual 0) investigated.
ROM warps Tails to (0x4000, 0x0000) with `in_air` set — the classic
`TailsCPU_Despawn` marker (`s2.asm:39043-39052`). At frame 374 Tails is
standing (yvel=0) at (0x02BC, 0x0250); the camera at (0x020C, 0x014D)
puts Tails roughly 0x103 (259) px below the bottom of the 224 px screen.
Sonic just rocketed up off the SteamSpring (`y_vel=-0x300` at f375), so
the camera is locked high enough that Tails is off-screen.
`TailsCPU_CheckDespawn` (`s2.asm:39055-39081`) immediate-despawns when
off-screen + `status.player.on_object` + `Tails_interact_ID` mismatch, or
ticks `Tails_respawn_counter` to 0x12C otherwise. Determining which path
fires in the ROM requires either the `Tails_interact_ID` value or the
`Tails_respawn_counter` to be exposed in the trace; both are currently
absent from the schema. Not committed; flagged as a recorder-extension
candidate.

## 2026-05-19 - S2 MTZ3 Tails air=0 at frame 765 investigation (no committed change; dead end)

- Branch: `develop` (HEAD `6acc363ef`)
- Worktree: isolated agent worktree
- Command: `mvn -q -Dmse=relaxed "-Dtest=com.openggf.tests.trace.s2.TestS2Mtz3LevelSelectTraceReplay#replayMatchesTrace" test -DfailIfNoTests=false`
- Result: fail, 2601 errors, frontier frame 765, field `air` (expected=1, actual=0)
- Outcome: investigation only; no fix identified; frontier unchanged

This investigation follows the SteamSpring `bypassesOffscreenSolidGate` fix that
advanced MTZ3 from f460 to f765. The new frontier has ROM Tails airborne (`air=1`)
while the engine has Tails grounded (`air=0`).

### ROM state at frame 765

From `aux_state.jsonl.gz` and `physics.csv.gz`:
- Tails at `(0x05D5, 0x01CB)`, ysp=0x0000, `air=1`, `stand_on_obj=22` (slot 22)
- Sonic undergoes routine change from 2 to 4 (hurt), `stand_obj_slot=23`
  (a LargeRotPform, Obj6E, at x=0x0534, y=0x021A — Sonic lands on it)
- Lost rings (type 0x37) spawn at slots 20, 28, 31, 32, 33
- Frame 760-765: Tails position oscillates slowly around `(0x05C5-0x05D5, 0x01D0-0x01CB)`,
  ysp=0x0000, air=1, no change in stand_on_obj (=22 throughout this window)

Nearby ROM objects at f765 (from aux slot dump):
- Slot 49: type 0x36 (Spikes), status=0x02 (upside-down), at `(0x05C0, 0x0130)`
- Slot 23: type 0x6E (LargeRotPform), `(0x0534, 0x021A)` — the platform Sonic lands on
- Slot 30: type 0x6E (LargeRotPform), `(0x056D, 0x021A)`
- Slot 16, 18, 35, 39-42: type 0x6E (additional LargeRotPforms in the MTZ3 room)
- Slot 43: type 0x9F (Shellcracker)
- Slot 29: type 0x42 (SteamSpring)

### Solid-object candidates eliminated

1. **ShellcrackerClaw (slots 44-46, 52-56, type 0xA0):** Not a `SolidObjectProvider`;
   cannot ground Tails.

2. **Spikes slot 49 (upside-down, Obj36 routine 6 at `(0x05C0, 0x0130)`):** Upside-down
   spikes call `SolidObject` (`s2.asm:29260`) to produce a ground-contact box whose top
   surface is at `y = 0x0130 - 0x10 = 0x0120 = 288`. Tails is at y=0x01CB = 459, which
   is 171 px below the spike box top. Cannot ground Tails.

3. **LargeRotPform slot 30 (`(0x056D, 0x021A)`):** Entry index for this platform (from
   byte_283C0, `s2.asm:54473`) is likely subtype 2 (width=0x60, yRadius=0x18). The
   platform top surface is at `y_centre - yRadius = 0x021A - 0x18 = 0x0202 = 514`.
   Tails is at y=0x01CB = 459. Tails is 55 px ABOVE the top of this platform — it
   cannot be the source of a ground contact from below.

4. **LargeRotPform slot 23:** Dropped out of Tails' `object_near` list by frame 765
   (too far away to register contact). Tails is still logged with `stand_on_obj=22`
   from prior frames, but slot 22 is not a nearby solid at f765.

5. **Offscreen gate false positive:** Tails is at `(0x05D5, 0x01CB)`, camera at
   approximately `(0x04E0, 0x014D)` for MTZ3 at this region. Relative Y from camera
   top is approximately 0x01CB - 0x014D = 0x7E = 126 px, well within the 224 px
   viewport plus any margin. `shouldSkipOffscreenSidekickFullSolid` returns false
   (Tails is on-screen).

### Root cause hypothesis (unconfirmed)

After the SteamSpring launches Tails at frame 460, ROM moves Tails to `(0x4000, 0x0000)`
and keeps her in `TailsCPU_Flying` state (`obj_control=0x81`) for approximately 178
frames (`s2.asm:38788-38888`). The engine's equivalent is `TailsRespawnStrategy` in
`APPROACHING` state (`objectControlled=true`, `requiresPhysics()=false`). In both ROM
and engine, terrain collision is suppressed during this flight phase.

The transition out of flight:
- **ROM:** `TailsCPU_Flying_Part2` exits to normal when Tails reaches Sonic's 16-frame-
  delayed position AND `andi.b #$D2,d2; bne.s return` clears (d2 is Sonic's status —
  must not have in_air, roll_jump, underwater, or bit7 set). On exit, Tails is placed
  at Sonic's position with `obj_control` cleared; normal physics resumes.
- **Engine:** `TailsRespawnStrategy.updateApproaching()` returns true when
  `remainingDx==0 && dy==0 && (recordedStatus & 0xD2)==0`.
  `SidekickCpuController.updateApproaching()` then clears `objectControlled`, zeroes
  XSpeed/YSpeed/GSpeed, sets `air=true`, and transitions to `NORMAL` state.

The engine Tails at frame 765 has `air=0` (grounded) while ROM Tails has `air=1`. This
implies the engine Tails exits APPROACHING at a position where terrain collision
immediately grounds her, while ROM Tails exits `TailsCPU_Flying` airborne.

Two candidate causes:
- **Y position offset on APPROACHING-to-NORMAL transition:** The engine's APPROACHING
  trajectory places Tails at a Y that happens to coincide with the top of an oscillating
  LargeRotPform at that exact frame. The oscillator phase difference between engine and
  ROM would determine which pform top is where.
- **Oscillator phase mismatch:** If `OscillationManager.getByte(0x20)` / `getByte(0x24)`
  are at a slightly different phase in the engine vs ROM at f765, one of the many
  LargeRotPforms in the MTZ3 room could present its collision top exactly at Tails' Y
  in the engine but not in the ROM.

### What would be needed to confirm

1. **Recorder extension:** Add `tails_cpu_state` (maps to `obj_control` byte),
   `tails_cpu_target_x`, and `tails_cpu_target_y` fields to `physics.csv` so the
   APPROACHING-to-NORMAL transition frame can be pinpointed precisely (currently
   impossible from existing trace fields).
2. **Oscillator field:** Add `oscillator_0x20` and `oscillator_0x24` to aux diagnostics
   so the LargeRotPform platform positions can be reconstructed from the ROM side and
   compared against the engine positions at the transition frame.
3. **Alternative:** Run the engine with diagnostic logging of the APPROACHING-to-NORMAL
   transition frame, Tails' exact position at that frame, and which solid object (if any)
   grounds her.

Did not commit a fix. Root cause requires recorder extension or runtime diagnostic
logging to identify the exact frame and platform involved. Flagged as a
recorder-extension candidate.

---

## 2026-05-20 - S2 ARZ Rising Pillar (Obj2B) player-release on_object clear (ARZ1 f304 -> f311)

- Branch: `develop` worktree `agent-ae0e08d9dae24b8cc`
- Command: `mvn test -Dtest=TestS2ArzLevelSelectTraceReplay`
- Result: ARZ1 frontier advanced from frame 304 (813 errors) to frame 311 (846 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS (0 errors); `TestS2Arz2LevelSelectTraceReplay`
  still at frame 225 (2129 errors, pre-existing); `TestS2MczLevelSelectTraceReplay` and
  `TestS2Mcz2LevelSelectTraceReplay` unchanged.

### Correction to prior log entries

Two entries from the 2026-05-20 MCZ2 session incorrectly stated:
- "TestS2Ehz1TraceReplay still at frame 304 (813 errors, pre-existing)"
- "TestS2ArzLevelSelectTraceReplay passes (unchanged)"

These were transposed. `TestS2Ehz1TraceReplay` was and remains PASS (0 errors, full trace).
`TestS2ArzLevelSelectTraceReplay` was the test at frame 304/813 errors.

### Root cause

`RisingPillarObjectInstance.releasePlayerAndBreak()` (s2.asm:51393-51405, `loc_25AF6`) set
`rolling=true` and `in_air=true` on the launched player but omitted the ROM's
`bclr #status.player.on_object,status(a1)` (s2.asm:51401), leaving on_object set.

Two cascading effects:

1. **status bit mismatch:** Engine Tails entered frame 304 with both in_air=true and
   on_object=true (status 0x0F), while ROM Tails had only in_air=true (status 0x07).

2. **Stale riding state re-grounding:** The `ridingStates` map in `ObjectManager.SolidContacts`
   still held a Tails→pillar entry from frame 303. In `PlayableSpriteMovement` at the start
   of frame 304, the pre-movement recovery check (`!isUnifiedCollision && sprite.getAir() &&
   hasGroundingObjectSupport() && sprite.getYSpeed() >= 0`) fired: `hasGroundingObjectSupport()`
   returned true from the stale riding state entry, forcing `setAir(false)` and `setOnObject(true)`
   again. This caused the solid contacts path to apply object-riding deceleration (−0x26) instead
   of air deceleration (−0x18), producing tails_x_speed=0x00C2 vs ROM's 0x00D0.

### Fix

In `releasePlayerAndBreak()`:
- Added `player.setOnObject(false)` matching ROM s2.asm:51401.
- Added `objectManager.clearRidingObject(player)` to flush the stale riding-state entry
  so the pre-movement recovery cannot re-ground the player on the next frame.

### New frontier at frame 311

ROM Tails enters hurt routine (routine 02→04) at frame 311 when hit by an Arrow (Obj22,
slot 18) at position (0x031C, 0x0378). Engine Arrow is at (0x0300, 0x0378) — 0x1C (28px)
to the left. Tails' centre Y (0x0369) with roll radius 7 px gives bottom at 0x0370; Arrow
top at 0x0376 is 6 px below Tails' bottom, so no touch contact. The Arrow position error
was already present at frame 304 (engine 0x02E4 vs ROM 0x0300) before this fix — it was
masked by the earlier pillar-launch failure. Root cause: Obj22 Arrow movement or spawn
position divergence, pre-existing and separate from this fix.

## 2026-05-20 - S2 Springboard (Obj40) stale launch sequence fires via global on-object state (MCZ2 frame 1487 -> 1774)

- Branch: `develop` worktree `agent-aaa0fe830f71211c4`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1487 (773 errors) to frame 1774 (806 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` unchanged at frame 1085 (452 errors,
  pre-existing); `TestS2ArzLevelSelectTraceReplay` frontier unchanged at frame 304 (816 errors,
  +3 from pre-existing 813; extra errors appear after existing frontier, not before it).

### Root cause

`SpringboardObjectInstance.updateLaunchSequence()` computed `launchContactNow` as:

```java
boolean launchContactNow = result.standingNow()
        || result.postContact().onObject()
        || (result.kind() != ContactKind.NONE && result.preContact().ySpeed() > 0);
```

`result.postContact().onObject()` captures the player's GLOBAL `isOnObject()` state after the
springboard's checkpoint resolves — not contact with this specific object. In MCZ2 at frame 1487,
Sonic is riding a SwingingPlatform in slot 17 (position ~0x06A0, 0x05B4), within the springboard's
X range but 564 px below it. Because `player.isOnObject()=true` globally (riding the swinging
platform), `postContact().onObject()=true`, and the stale `launchSequenceActive` flag (set when
Sonic briefly contacted the springboard in an earlier frame) was not cleared. The `else if`
persistence branch kept `launchSequenceActive` alive, causing `applyLaunch()` to fire:
`player.setAir(true)`, `player.setYSpeed(-0x0400)`, `player.setGSpeed(1)`.

The ROM's `SlopedSolid_SingleCharacter` fast-path clears the standing bit whenever
`SolidObject_TestClearPush` or `loc_1980A` determines the player is out of Y range, airborne,
or out of X range. There is no separate persistence window — the standing bit is cleared the
moment contact is lost.

### Fix

Replaced `launchContactNow` with `result.kind() != ContactKind.NONE` (per-object contact only,
from the checkpoint result). Changed the `else if` persistence branch to a plain `else`:
always call `clearLaunchSequence()` when the checkpoint returns no contact. This matches the
ROM exactly — no contact from this specific springboard = standing bit cleared = no launch.

Files changed:
- `SpringboardObjectInstance.java` (`updateLaunchSequence`) — fixed launchContactNow and else branch

### New MCZ2 frontier (frame 1774)

`y mismatch (expected=0x04AC, actual=0x04A9)` — Sonic's Y position diverges by 3 px. Involves
`MCZDrawbridge` (Obj6A) — a separate, unrelated object with independent parity issues. Not
investigated in this iter.

### ARZ1 +3 error note

ARZ1 error count increased from 813 to 816; the frontier frame is unchanged at 304
(`tails_x_speed mismatch`, pre-existing). The 3 extra errors appear after frame 304 where
the trace divergence has already compounded. They are a downstream consequence of the corrected
springboard behavior in ARZ1 after the existing cascade.

## 2026-05-20 - S2 Arrow Shooter (Obj22) sidekick-detection and animation-timing (ARZ1 f311 -> f964)

- Branch: `develop` worktree `agent-a7604e7a5d2c6d17e`
- Command: `mvn test -Dtest=TestS2ArzLevelSelectTraceReplay -q`
- Result: ARZ1 frontier advanced from frame 311 (868 errors) to frame 964 (664 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS (0 errors); `TestS2Arz2LevelSelectTraceReplay`
  still at frame 225 (2067 errors, pre-existing; 62 fewer errors vs prior 2129 is a downstream
  improvement from corrected Arrow timing, not a new failure).

### Root cause

Three bugs in `ArrowShooterObjectInstance` caused the Arrow projectile to spawn 7 frames late,
placing it 28px behind the ROM at frame 311 so Tails was not hit.

**Bug 1 — Sidekick detection missing.**
The ROM's `Obj22_DetectPlayer` subroutine is called twice per frame: once for `MainCharacter`
and once for `Sidekick`. If either is within 0x40 px of the shooter, the detecting animation
activates. The engine's `updateDetection(player)` only checked the main player (Sonic) passed
via `update()`'s `playerEntity` argument. In this trace, Tails was the closer character
(tails_x at frame 179: 0x018B, dx=0x3B; sonic_x: 0x1D1, dx=0x81). Sonic was never within
detection range at all. The engine only detected via Sonic when he briefly passed within 0x40 px
(frames 117–164), causing an idle→detecting→firing transition at frame 165 — 15 frames before
the ROM transition at frame 180 when Tails left detection range.

**Bug 2 — `animTimer` initialised to 7 instead of 0 on FIRING entry.**
ROM's `AnimateSprite` sees `anim != prev_anim` → resets `anim_frame_duration=0`, then
immediately decrements to −1 and processes the first entry, all in the same call that sets
`anim=2`. The engine set `animTimer = DELAY_FIRING = 7`, so the first firing entry was not
processed until 8 frames after the transition (7 decrements to 0, then one more to −1). This
added 8 frames of delay relative to the ROM.

**Bug 3 — Arrow fired after 5 animation entries instead of after 2.**
`FIRING_CALLBACK_INDEX = 5` caused the arrow to be fired after ALL five `FIRING_SEQUENCE`
entries `{3, 4, 4, 3, 1}` were shown (firingIndex reaches 5). In the ROM, the `$FC` callback
fires after only 2 entries (frame=3, frame=4 — `anim_frame` values 0 and 1), then `routine`
is incremented to 4 (`Obj22_ShootArrow`). The remaining entries `{4, 3, 1}` are the post-fire
animation shown by `Obj22_ShootArrow`'s own `AnimateSprite` call. The engine fired 24 extra
frames late (3 extra entries × 8 frames each).

**Net timing:** Bug 1 fired 15 frames early. Bugs 2+3 added 8+24=32 extra frames. Net: 15
early − 32 late = 17 late. But with all bugs compounding: ROM fires arrow at frame 197 (anim=2
set at 180, $FC at 196, ShootArrow at 197); engine fired at frame 204 (anim=FIRING at 165,
entry-0 at 172, entry-4 at 204). Difference: 204−197 = 7 frames → 7×4 = 28px. ✓

### Fix

`ArrowShooterObjectInstance`:
- `updateDetection` refactored to use `isWithinDetectionRange(entity)` helper, called for both
  the main player and all `services().sidekicks()`. Detection matches ROM: either character
  within 0x40 px triggers the detecting state.
- `animTimer` set to 0 (not `DELAY_FIRING`) on the idle→firing transition so the first firing
  animation entry is processed on the same frame that ANIM_FIRING is entered.
- `FIRING_CALLBACK_INDEX` changed from 5 to 3 (fires when `firingIndex` reaches 3, after
  processing `FIRING_SEQUENCE[2]=4`, which is the first post-`$FC` visible frame).
- `fireArrow()` now calls `addDynamicObjectNextFrame` instead of `addDynamicObject`, replicating
  the ROM's 1-frame gap between `$FC` setting `routine=4` and `Obj22_ShootArrow` running the
  next frame to allocate the arrow object.

Files changed:
- `ArrowShooterObjectInstance.java` — sidekick detection, animTimer init, FIRING_CALLBACK_INDEX, addDynamicObjectNextFrame

### New ARZ1 frontier (frame 964)

`air mismatch (expected=0, actual=1)` — Sonic's air flag diverges. A separate, unrelated
blocker, not investigated in this iteration.

## 2026-05-20 - S2 MCZ Drawbridge (Obj81) landing position, zero-dist gate, half-width (MCZ2 frame 1774 -> 2226)

- Branch: `develop` worktree `agent-a1d6ae52d5b453e92`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 1774 (806 errors) to frame 2226 (724 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` unchanged at frame 1085 (452 errors);
  `TestS2ArzLevelSelectTraceReplay` unchanged at frame 311 (868 errors, pre-existing Tails AI mismatch).

### Root causes

Three bugs in `MCZDrawbridgeObjectInstance` (Obj81 / `JmpTo22_SolidObject`):

**Bug 1 — halfWidth=64 too narrow (s2.asm:56578):**
`PARAMS_DOWN` used `halfWidth=64` (matching `width_pixels=$40` in the object header), but ROM
`loc_2A1A8` (`move.w #$4B,d1`) passes `d1=0x4B=75` to `JmpTo22_SolidObject`. The `$40=64`
value is used only for the secondary inner hit-width check inside `SolidObject_Landed`; the
primary detection/riding halfWidth is 75. With 64 the engine detached Sonic from the bridge
3px inside the left edge, causing the frame 1796 `air mismatch`.

**Bug 2 — zero-distance landing rejected:**
The global `topSolidLandingAllowsZeroDist=false` for S2 was modeled on `PlatformObject_ChkYRange`
(`s2.asm:35696-35712`). But Obj81 routes through `SolidObject_TopBottom` →
`SolidObject_Landed` (`s2.asm:35297-35308`), gated by `blo.s SolidObject_Landed` (unsigned
lower, includes `d3=0`). Zero-distance landings ARE valid for `SolidObject`-based objects.
Override `allowsZeroDistanceTopSolidLanding()` → `true` in `MCZDrawbridgeObjectInstance`.

**Bug 3 — PlatformObject snap formula overwriting correct SolidObject position:**
`applyNonUnifiedTopSolidLandingHeightOverride` implements `PlatformObject_ChkYRange`
(`anchorY - groundHalfHeight - yRadius - 1`). But `resolveContactInternal` already produces
the correct `SolidObject_Landed` result (`playerY - distY + 3 = 0x04AC`). The override was
then replacing it with the wrong `PlatformObject_ChkYRange` result (`0x04AB`).
Added `SolidObjectProvider.usesPlatformObjectLandingSnap()` (default `true`); override to
`false` in `MCZDrawbridgeObjectInstance`; added early-return guard in the override method.

### New MCZ2 frontier (frame 2226)

`y mismatch (expected=0x033E, actual=0x0340)` — Sonic lands on a Springboard (Obj40,
`SlopedSolidProvider`) 2px too low. This is a pre-existing sloped-solid landing formula
divergence separate from the drawbridge fixes.

## 2026-05-20 - S2 Monitor (Obj26) rolling gate bypass for already-standing player (ARZ1 frame 964 -> 980)

- Branch: `develop` worktree `agent-a1fecc74cd8033c95`
- Command: `mvn test -Dtest=TestS2Arz1LevelSelectTraceReplay -q`
- Result: ARZ1 frontier advanced from frame 964 (664 errors) to frame 980 (675 errors).

### Root cause

`MonitorObjectInstance.isSolidFor()` returned `!player.getRolling()` unconditionally for the
main character. When Sonic started rolling at frame 964 while already standing on a monitor
(slot 16, type 0x26, `stand_on_obj=0x10` visible in the trace), this returned `false`.
`SolidContacts.update()` then cleared the riding state. Because `on_object` became `false`,
the `CollisionSystem` terrain probe ran and set `air=1`, since Sonic was floating above the
terrain surface.

ROM `SolidObject_Monitor_Sonic` (s2.asm:25448-25453):
```
btst d6,status(a0)              ; is Sonic already standing on the monitor?
bne.s Obj26_ChkOverEdge         ; yes → carry him regardless of rolling
cmpi.b #AniIDSonAni_Roll,anim(a1) ; is Sonic spinning?
bne.w SolidObject_cont           ; not spinning → solid
rts                              ; spinning → not solid (blocks NEW landings only)
```
The rolling check only blocks *new* landings. A player who is already standing (p1_standing_bit
set in the monitor's status byte) bypasses the rolling check entirely and goes straight to
`Obj26_ChkOverEdge` → `MvSonicOnPtfm` (carry formula:
`y_pos(player) = y_pos(monitor) - groundHalfHeight(0x10) - y_radius(player)`).

### Fix

`MonitorObjectInstance.isSolidFor()`: added `if (mainCharacterStanding) { return true; }`
before the `return !player.getRolling()` line. The `mainCharacterStanding` field tracks whether
the main character is already standing on this monitor instance, directly matching the ROM's
p1_standing_bit check.

Files changed:
- `MonitorObjectInstance.java` — `isSolidFor()`: bypass rolling check when `mainCharacterStanding`

### New ARZ1 frontier (frame 980)

`tails_y mismatch (expected=0x03A3, actual=0x03A2)` — Tails's Y position is 1 pixel high when
rolling starts. At frame 980 in the ROM, Tails transitions from standing (yRadius=15) to rolling
(yRadius=14) while being carried on the same monitor. The monitor carry formula places Tails at
`centreY = monitorY(0x03C1) - groundHalfHeight(0x10) - yRadius`. With yRadius=14 (rolling):
0x03A3. The engine's CPU controller delays Tails's rolling by 1 frame (the 16-frame-delayed
Sonic input from frame 964 triggers rolling at frame 981 instead of 980), so yRadius=15 is
still in effect at frame 980 in the engine, yielding 0x03A2. This is a CPU controller timing
parity issue, not investigated further in this iteration.

## 2026-05-20 - S2 Tails minStartRollSpeed corrected from 264 to 128 (ARZ1 frame 980 -> 1102)

- Branch: `develop` worktree `agent-adafbc69d851f3941`
- Command: `mvn test -Dtest=TestS2ArzLevelSelectTraceReplay -q`
- Result: ARZ1 frontier advanced from frame 980 (675 errors) to frame 1102 (648 errors).
- Regression check: `TestS2Ehz1TraceReplay` PASS; `TestS2CpzLevelSelectTraceReplay` still at
  frame 844 (pre-existing); `TestS2HtzLevelSelectTraceReplay` still at frame 5511 (pre-existing);
  no regressions introduced.

### Root cause

`PhysicsProfile.SONIC_2_TAILS.minStartRollSpeed` was set to 264 (0x108) — a stale placeholder
value that was never verified against the ROM. ROM `Tails_Roll` (`s2.asm:39962`) uses
`cmpi.w #$80,d0`, setting the roll-start threshold at 0x80 = 128, identical to `Sonic_Roll`
(`s2.asm:36983`). At frame 980, the Tails CPU controller correctly set `inputDown=true` (Sonic's
16-frame-delayed input from frame 964 included DOWN), but `doCheckStartRoll()` in
`PlayableSpriteMovement` returned early because `|gSpeed|=253 < minStartRollSpeed=264`. With the
correct threshold of 128, 253 >= 128, so rolling starts on the right frame.

### Fix

`PhysicsProfile.SONIC_2_TAILS.minStartRollSpeed`: 264 → 128. Updated inline comment with ROM
reference (`s2.asm:39962`). Also updated the class-level comment for `SONIC_2_TAILS` to note
that `minStartRollSpeed` matches Sonic's `$80`, not a Tails-specific value.

Files changed:
- `PhysicsProfile.java` — `SONIC_2_TAILS.minStartRollSpeed` and related comments

### New ARZ1 frontier (frame 1102)

`y mismatch (expected=0x039D, actual=0x039E)` — Sonic is 1 pixel too low at the frame the
monitor breaks (`broken=1`). This is a different object interaction than the Tails rolling bug
and requires separate investigation.

## 2026-05-20 - S2 Springboard (Obj40) sloped catch range and first-contact launch guard (MCZ2 f2226 -> f2418)

- Branch: `develop` worktree `agent-a3702473724ceec0b`
- Command: `mvn test -Dtest=TestS2Mcz2LevelSelectTraceReplay -q`
- Result: MCZ2 frontier advanced from frame 2226 (724 errors) to frame 2418 (571 errors).
- Regression check: `TestS2MczLevelSelectTraceReplay` unchanged at frame 1085 (452 errors, pre-existing);
  `TestS2Ehz1TraceReplay` PASS (0 errors, unchanged); `TestS2ArzLevelSelectTraceReplay` improved
  to frame 964 (625 errors, was 664 pre-existing); `TestS2HtzLevelSelectTraceReplay` unchanged
  at frame 5511 (490 errors, pre-existing); `TestS2CnzLevelSelectTraceReplay` unchanged at
  frame 3906 (22 errors, pre-existing).

### Root causes

**Bug 1 - addsSlopeCatchRangeToVerticalOverlap missing override:**
ROM `SlopedSolid_cont` (s2.asm:35066) adds the object half-height (d2=8) to the vertical
overlap catch range before computing relY. `SlopedSolidProvider.addsSlopeCatchRangeToVerticalOverlap()`
defaults to `false`. `SpringboardObjectInstance` did not override it, so halfHeight was not
added to relY. With yRadius=19 (standing) and halfHeight=8: the missing 8 shifted the landing
snap Y by 2px (frame 2226: actual y=0x0340, expected=0x033E). Fix: override returns `true`.

**Bug 2 - First-contact launch fires one frame early:**
`updateLaunchSequence` switched the animation from IDLE to COMPRESSED, then fell through to
the `if (mappingFrame == 0)` check. Since IDLE starts at frame 0 (delay=0xF not yet elapsed),
`mappingFrame==0` was true and `applyLaunch` fired on the same frame as the initial landing.
ROM `loc_26446` (s2.asm:51868-51872): when anim is not already 1, sets anim=1 then `rts`
without checking `mapping_frame`. On the first contact frame the ROM returns immediately.
Fix: add `return` after `setAnimId(ANIM_COMPRESSED)` when anim was not already compressed.

### New MCZ2 frontier (frame 2418)

`tails_y mismatch (expected=0x02ED, actual=0x02EB)` - Tails stands on a Springboard 2px too high.
Sidekick slot assignment and Tails-on-Springboard riding position are pre-existing separate issues.

## 2026-05-20 - S2 Springboard sidekick contact drives animation switch (MCZ2 f2418 -> f3003)

- Branch: `develop`, worktree `agent-a6bbed386117bca64` (HEAD `093d9abb2`)
- Command: `mvn test "-Dtest=TestS2Mcz2LevelSelectTraceReplay" -q`
- Result: MCZ2 frontier advances from frame 2418 (571 errors) to frame 3003 (527 errors)
- Regression check: ARZ f1102, CNZ f3906, CPZ f844, HTZ f5511, MCZ1 f1085, EHZ1 pass — all unchanged vs develop baseline

### Root cause

`SpringboardObjectInstance.update()` called `updateLaunchSequence` only for the main character
(Sonic). ROM `Obj40_Main` (s2.asm:51839-51847) calls `SlopedSolid_SingleCharacter` and `loc_2641E`
for BOTH `MainCharacter` (p1_standing_bit) AND `Sidekick` (p2_standing_bit):

```
lea (Sidekick).w,a1        ; a1 = Tails
moveq #p2_standing_bit,d6
jsrto JmpTo_SlopedSolid_SingleCharacter
btst #p2_standing_bit,status(a0)
beq.s +
bsr.s loc_2641E            ; animation switch / launch for Tails
```

At frame 2417, Tails X (0x0755) crosses the high-side threshold (spring_x - 0x10 = 0x0764 -
0x10 = 0x0754). ROM `loc_2641E` switches the Springboard from IDLE to COMPRESSED. On frame 2418,
`AnimateSprite` advances to `mapping_frame=1`, selecting `Obj40_SlopeData_Straight` over
`Obj40_SlopeData_DiagUp`. At sampleX=12: Straight[12]=0x0C=12, DiagUp[12]=0x0E=14 — 2px
difference. Engine used DiagUp (mapping_frame stayed 0), yielding tails_y=0x02EB vs ROM 0x02ED.

### Fix

Resolved checkpoint batch ONCE via `checkpointAll()` (avoids double-resolution from calling
`resolveSolidNow` separately per player — the resolver processes all players in one shot).
For each sidekick that is an `AbstractPlayableSprite`, extract their result from the shared
batch and call `updateLaunchSequence`. No change to animation state sharing or launch state
management — Springboard has one animation and serializes launches naturally.

### New MCZ2 frontier (frame 3003)

`tails_x mismatch (expected=0x4000, actual=0x0990)` — Tails teleportation/despawn coordinate
mismatch (0x4000 is the ROM's off-screen park position for the sidekick slot). Unrelated to
Springboard; likely a Tails CPU despawn/respawn parity issue.

## 2026-05-20 - S2 ceiling extension scan missing +16 correction (ARZ1 frame 1102 -> 1106)

- Branch: `develop` worktree `agent-a973005177d2ea125`
- Command: `mvn test "-Dtest=com.openggf.tests.trace.s2.TestS2ArzLevelSelectTraceReplay" -q`
- Result: ARZ1 frontier advanced from frame 1102 (648 errors) to frame 1106 (624 errors).

### Root cause

`GroundSensor.scanTileVertical` with `isExtension=true` and `metric<0, adjusted<0` returned
`(byte)~yInTile` directly from the FindFloor2 result, but the ROM's FindFloor routine
(`loc_1E7E2`, s2.asm:42989) adds `addi.w #$10,d1` (+16) after the FindFloor2 call.

At ARZ1 frame 1102, Sonic is rolling upward with ceiling sensors at y=0x038F. The first tile
at y=0x038F has no solid ceiling (tile=null); the extension tile one tile above at y=0x037F has
a partial ceiling with `yInTile=0` (from `(origY ^ 0x0F) & 0x0F = (0x38F ^ 0xF) & 0xF = 0`).

Without the +16:
- FindFloor2 `loc_1E900` raw result: `~yInTile = ~0 = -1`
- Engine returns `distance = -1` → spurious ceiling hit → `shiftY(+1)` + `ySpeed = 0`
- Result: Sonic at y=039E (1px too low), ySpeed=0

With the +16:
- ROM result: `~yInTile + 16 = -1 + 16 = 15` → positive distance → no ceiling collision
- ROM: Sonic continues at y=039D with ySpeed=FA9E (correct)

ROM refs: `FindFloor2` `loc_1E900` (`not.w d1`, s2.asm:43064); `FindFloor` `loc_1E7E2`
(`addi.w #$10,d1`, s2.asm:42989).

Fix: in `GroundSensor.scanTileVertical`, changed `(byte)~yInTile` to `(byte)(~yInTile + 16)`
in the `isExtension=true, metric<0, adjusted<0` branch.

### New ARZ1 frontier (frame 1106)

`y mismatch (expected=0x038E, actual=0x038A)` — Sonic is 4px too high at the first ceiling
hit. Root cause: first tile at y=0x037C has `metric=-5`, `yInTile=3`, `adjusted=-2<0`. The
extension tile at prevCheckY=0x038C has no solid, so `scanTileVertical` for the extension pass
returns `null`. ROM path: FindFloor `loc_1E86A` calls FindFloor2 at d2=0x383, no solid →
`d1 = 15-3=12`, then `subi.w #$10 → d1=-4`, so ROM places ceiling at y=038A+4=038E. Engine
returns `null` → no ceiling hit → Sonic passes through 4px too far. This is the `prevResult==null`
case in the `metric<0, adjusted<0` first-pass branch; a fix of `return ~yInTile` is ROM-accurate
but causes a downstream regression at frame 1208 that requires separate investigation.

## 2026-05-23 - S3K AIZ hollow-tree camera lock phase (AIZ f4539 -> f4540)

- Worktree: integrated local workspace with other workers' CNZ/MGZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" compile surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#hollowTreeCameraLockBecomesVisibleOnRomFrameAfterCapture" "-DfailIfNoTests=false"`
  - RED before fix: frame 4539 expected camera_x `0x2C51`, actual `0x2C60`.
  - PASS after fix: 1 test, 0 failures.
- Full replay:
  `mvn "-Dmse=off" compile surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"`
  - Result: AIZ frontier advanced from frame 4539 `camera_x expected=0x2C51 actual=0x2C60`
    to frame 4540 `tails_x expected=0x2C70 actual=0x2C2B`.
  - New count: 1341 errors / 25 warnings.

### Root cause

`Obj_AIZHollowTree` capture was otherwise aligned at frame 4539: Sonic position/speed/status,
object-control state, sidekick state, and the tree's `$38=$3C` timer matched the ROM context.
The engine applied the tree's `Camera_min_X_pos=$2C60` and `Camera_max_X_pos=$2C60` writes before
its frame-end camera update, making the clamp visible one trace frame early. The ROM object code
does write those words during the capture setup (`sonic3k.asm:43688-43704`), but the trace-visible
camera position stays at the normal follow value on frame 4539 and first clamps to `$2C60` on
frame 4540.

Fix: keep the capture/timer/reveal-control effects immediate, but queue this object-local camera
boundary write until the hollow-tree object's next update pass. No shared camera or CNZ/MGZ code
was changed.

### New AIZ frontier (frame 4540)

`tails_x mismatch (expected=0x2C70, actual=0x2C2B)` after Sonic/camera match. ROM sidekick context
shows `branch=leader_on_object`, status `0x02`, onObj `0x27`, and Tails parked at `0x2C70,0x0410`;
engine Tails remains in normal follow physics at `0x2C2B,0x0411`. This is now a sidekick
leader-on-object/hollow-tree handoff diagnostic and was left untouched in this AIZ camera fix.

## 2026-05-23 - S3K CNZ Tails catch-up signed Y steering (CNZ f9344 -> f9801)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Regression guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery#flightSteersNegativeYWordDownTowardPositiveTarget" test "-DfailIfNoTests=false"`
  - RED before fix: expected Tails Y `0xFFDA`, actual `0xFFD8`.
  - PASS after fix: 1 test, 0 failures.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery,com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight,com.openggf.sprites.playable.TestSidekickCpuFollowParity#cnzDoorSupportGraceFallsThroughToFollowLeftNudgeWhenPushIsClear,com.openggf.sprites.managers.TestPlayableSpriteMovement#jumpPreservesStatusOnObjectUntilSolidObjectPass" test "-DfailIfNoTests=false"`
  - PASS: 17 tests, 0 failures.
- Full CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 9344 `tails_y expected=-0026 actual=-0028`
    to frame 9801 `y_speed expected=0x07A8 actual=0x0000`.
  - New count: 3310 errors / 15 warnings.

### Root cause

`Tails_FlySwim_Unknown` loads the delayed target from `Pos_table`, computes
`y_pos(a0) - Tails_CPU_target_Y`, then branches on the signed word flags before adding
`+1` or `-1` to Tails' Y position (`sonic3k.asm:26564-26565,26611-26620`). The engine masked
Tails' current Y to an unsigned word before subtracting, so a negative offscreen Y such as
`$FFD9` was treated as below the target rather than above it. At frame 9344 this made Tails move
up to `$FFD8` while the ROM moved down to `$FFDA`.

Fix: make the catch-up flight Y delta use a signed 16-bit subtraction result before choosing the
`+1`/`-1` steering step. No trace data is written into engine state.

### New CNZ frontier (frame 9801)

`y_speed mismatch (expected=0x07A8, actual=0x0000)`. Sonic/player matches through frame 9800, then
the engine lands on/near `CNZTrapDoor @1560,0284` one frame earlier than the ROM while the ROM still
has `status=0x02` and downward `y_speed=0x07A8`. Tails' position still matches at the new frontier,
so this is a separate CNZ trap-door/landing issue.

## 2026-05-23 - S3K CNZ trap-door top-solid object phase (CNZ f9801 -> f9951)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Regression guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.level.objects.TestSolidObjectManager#cnzTrapDoorUsesPreviousPlayerPositionForNewTopSolidLanding" test "-DfailIfNoTests=false"`
  - RED before fix: expected Sonic to remain airborne with `y_speed=0x07A8`, but the engine landed
    immediately on the trap door and zeroed Y speed.
  - PASS after fix: 1 test, 0 failures.
- Focused trap-door guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.level.objects.TestSolidObjectManager#cnzTrapDoorSolidObjectTopAcceptsExactSurfaceBoundaryAndLandsOnePixelInside,com.openggf.level.objects.TestSolidObjectManager#cnzTrapDoorUsesPreviousPlayerPositionForNewTopSolidLanding,com.openggf.tests.TestS3kCnzLocalTraversalHeadless#trapDoorOpensFromTheROMTriggerWindowAndEventuallyCloses,com.openggf.tests.TestS3kCnzLocalTraversalHeadless#trapDoorDoesNotOpenWhenPlayerCenterIsAboveTheHinge,com.openggf.tests.TestS3kCnzLocalTraversalHeadless#trapDoorChecksExtraEngineSidekickFromParticipationQuery" test "-DfailIfNoTests=false"`
  - PASS: 5 tests, 0 failures.
- Full CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 9801 `y_speed expected=0x07A8 actual=0x0000`
    to frame 9951 `tails_x_speed expected=0x0000 actual=0x0240`.
  - New count: 3433 errors / 13 warnings.
  - Fresh post-doc recheck used direct Surefire after the integrated workspace's unrelated
    `MGZTopPlatformObjectInstance#getName()` compile error blocked the normal Maven lifecycle:
    `mvn "-Dmse=off" "-Dmaven.compiler.failOnError=false" test-compile "-DfailIfNoTests=false"`,
    then `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"`.
    It reproduced the same frame-9951 frontier and count.

### Root cause

`Obj_CNZTrapDoor` runs in the object pass, checks both players through `sub_31CFA`, then immediately
calls `SolidObjectTop` with width `$20` and top offset `9` (`sonic3k.asm:67217-67225`). The
trigger window in `sub_31CFA` is independent of the top-solid landing at this frame
(`sonic3k.asm:67233-67249`). `SolidObjectTop` rejects players still above the surface and accepts the
exact boundary before `RideObject_SetRide` (`sonic3k.asm:41982-42015`).

At frame 9801 the ROM object still sees Sonic's previously completed Y position (`$025D`), so the
top-solid check rejects the landing and the CPU/player step continues to final `y_speed=$07A8`. The
engine's split pass was testing the just-moved Y position (`$0264`) against the trap door, accepted the
exact top boundary one object phase early, and zeroed Y speed. The fix is object-local: S3K CNZ trap
doors sample the previous completed player position for new `SolidObjectTop` contacts, matching the
ROM object phase without changing shared collision.

### New CNZ frontier (frame 9951)

`tails_x_speed mismatch (expected=0x0000, actual=0x0240)`. ROM context has Tails standing on object
slot 12, `CNZCylinder @1660,0284`, with `onObj=true objP2=true`; the engine has Tails off-object near
`CNZCylinder @1660,0280 no-touch` and continuing normal follow movement. Sonic remains past the
trap-door frontier, so this is a separate CNZ cylinder/sidekick support handoff issue.

## 2026-05-23 - S3K CNZ cylinder grounded P2 standing-bit reboost (CNZ f9966 -> f10009)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Regression guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzDirectedTraversalHeadless#cnzCylinderDoesNotReboostMode0WhenActiveGroundedSidekickSolidFeedbackDrops" test "-DfailIfNoTests=false"`
  - PASS after fix: 1 test, 0 failures.
- Focused cylinder guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzDirectedTraversalHeadless#cnzCylinderUsesRomRenderHeightForBottomEdgeSidekickSolidPass+cnzCylinderSeedsGroundVelocityWhenVerticalMotionReachesRomLaunchThreshold+cnzCylinderDoesNotReboostMode0WhenActiveGroundedSidekickSolidFeedbackDrops+cnzCylinderReleasesWhenStandingContactIsLostWithoutJumpInput+cnzCylinderStandingLossClearsSlotWithoutJumpSetup+cnzCylinderRecapturesOffscreenCpuSidekickMarkerFromStandingBit+cnzCylinderMaintainsIndependentRiderStateForPlayerAndSidekick" test "-DfailIfNoTests=false"`
  - PASS: 7 tests, 0 failures.
- CNZ replay:
  - Normal Maven lifecycle command was blocked in `testCompile` by unrelated integrated-workspace
    compile errors after test compile cache invalidation.
  - Direct Surefire replay:
    `mvn "-Dmse=off" surefire:test "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" "-DfailIfNoTests=false"`
    - Result: CNZ frontier advanced from frame 9966
      `tails_g_speed expected=0x0000 actual=0x0800` to frame 10009
      `tails_x expected=0x167A actual=0x1679`.
    - New count: 2713 errors / 15 warnings.

### Root cause

`loc_32208` compares `status(a0)&standing_mask` with `$3C(a0)` before the cylinder's
`sub_324C0` and `SolidObjectFull` passes (`sonic3k.asm:67709-67718,67656-67672`). It only adds
`$400` to `y_vel(a0)` for a real new standing-bit transition, and `loc_32594` later sets rider
`ground_vel=$800` only when `abs(y_vel(a0)) >= $480` (`sonic3k.asm:67725-67742,68045-68056`).

At frame 9966 ROM still has the cylinder P2 standing bit set continuously (`tailsInteract ... st=10`)
while Tails is a grounded active cylinder rider. The engine could clear its internal standing mask for
the active grounded sidekick when object-controlled solid feedback briefly dropped, then treat the
returning P2 bit as a fresh landing. That false transition re-applied the `$400` mode-0 boost and
crossed the `$480` launch threshold, producing the spurious `tails_g_speed=$0800`.

Fix: preserve the prior standing bit for active, grounded, object-controlled CNZ cylinder riders until
the release path makes the rider airborne. No shared collision or AIZ/MGZ behavior changed.

### New CNZ frontier (frame 10009)

`tails_x mismatch (expected=0x167A, actual=0x1679)`. Tails has released from
`CNZCylinder @1660,0268`; ROM and engine agree on release state and most velocities, but the engine is
one pixel left after the post-release CPU/physics step. This is a separate post-release
sidekick/subpixel issue.

## 2026-05-23 - S3K CNZ cylinder rider Ctrl logical latch fix (CNZ f10924 -> f10927)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 10 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10924
    `tails_x_speed expected=0x0000 actual=0x0018` to frame 10927
    `tails_x expected=0x1A21 actual=0x1A20`.
  - New count: 3657 errors / 30 warnings.

### Root cause

`Obj_CNZCylinder` capture writes `x_vel=0`, `y_vel=0`, `ground_vel=0`, and `object_control=$03`, then
clears roll/air/push flags (`sonic3k.asm:67999-68008`). It does not write `Ctrl_1_locked` or
`Ctrl_2_locked`. S3K `Sonic_Control` only latches `Ctrl_1_logical` when the separate
`Ctrl_1_locked` byte is nonzero (`sonic3k.asm:21539-21545`), and `Sonic_RecordPos` records that
logical word into the delayed `Stat_table` used by `Tails_CPU_Control` (`sonic3k.asm:22124-22133,
26683-26700`).

The engine mapped CNZ cylinder capture to both ROM `object_control=$03` and the separate control-lock
latch. While Sonic was held by the cylinder with raw input already zero, this preserved an older RIGHT
bit in Sonic's delayed input history. Tails CPU copied that stale delayed bit through the
`leader_on_object` branch, and normal airborne acceleration added `x_speed=$0018` after CPU at frame
10924. The fix keeps the ROM object-control movement suppression but stops CNZCylinder from owning the
separate logical-input lock.

### New CNZ frontier (frame 10927)

`tails_x mismatch (expected=0x1A21, actual=0x1A20)`. The stale delayed RIGHT input is gone
(`eng-tails-cpu ... in=0000 gen=0000`), but once Tails is held by the cylinder the engine's cylinder
center/held rider position is one pixel left of ROM (`CNZCylinder @1A09,0160` vs ROM `@1A0A,0160`).
This is a separate CNZCylinder held-position/center timing issue.

## 2026-05-23 - S3K AIZ Act 2 title/results level-size handoff (AIZ f8941 -> f8943)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ frontier advanced from frame 8941
    `camera_y expected=0x02C1 actual=0x02B8` to frame 8943
    `camera_y expected=0x02D0 actual=0x02CE`.
  - New count: 758 errors / 25 warnings.

### Root cause

The AIZ miniboss defeat handoff was still serializing the signpost/results flow behind the explosion
controller. ROM `loc_68FB6` switches the boss object to `Wait_FadeToLevelMusic` and creates
`Child6_CreateBossExplosion` in parallel; `loc_68C02`/`Obj_EndSignControl` is reached from the boss
object's timer, not from explosion completion (`sonic3k.asm:137793-137806,179651-179668,
137381-137393`).

Once that lifecycle was aligned, the previous AIZ camera unlock proxy was also too broad: it widened
the arena as soon as the title-card children were exiting. ROM only runs `Change_Act2Sizes` after the
in-level title card has set `End_of_level_flag` (`sonic3k.asm:62708-62720,62276-62279,180415-180419`),
then copies Act 2 sizes and creates the gradual level-size objects (`sonic3k.asm:180575-180609`).
The engine now gates AIZ arena release on `End_of_level_flag`, keeps X under the
`Obj_IncLevEndXGradual` `$4000` accumulator, and applies the Act 2 max-Y target plus
`Obj_IncLevEndYGradual` `$8000` current-bound update (`sonic3k.asm:178154-178169,178210-178225`).

### New AIZ frontier (frame 8943)

`camera_y mismatch (expected=0x02D0, actual=0x02CE)`. Camera X now matches through this point, and
the f8941/f8942 vertical release frames match. The remaining issue is the next vertical boundary phase
after Sonic lands from the post-title-card jump, likely in the exact interaction between
`Obj_IncLevEndYGradual` and `Do_ResizeEvents`/grounded vertical scroll ordering.

## 2026-05-23 - S3K CNZ cylinder jump-release Y/solid ordering (CNZ f10967 -> f11061)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guards:
  `mvn "-Dmse=off" clean "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 12 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 10967
    `tails_y expected=0x0130 actual=0x0131` to frame 11061
    `tails_x expected=0x1A37 actual=0x1A3E`.
  - New count: 3129 errors / 25 warnings.

### Root cause

`Obj_CNZCylinder` runs `sub_321E2`, then P1/P2 `sub_324C0`, then `SolidObjectFull`
(`sonic3k.asm:67656-67672`). On the active jump branch, `loc_325B6` changes the rider's
radii/animation/velocities and falls through to `loc_325F2`, which sets `Status_InAir` and clears
`object_control`; it does not write `y_pos` (`sonic3k.asm:68058-68078`). Because the cylinder
standing bit was set to reach `loc_32538`, the same object pass' `SolidObjectFull_1P` sees the
airborne rider and returns through `loc_1DC98`, clearing support without reaching `loc_1E154`'s
upward-velocity lift (`sonic3k.asm:41016-41034`).

The engine was recomputing release Y from the cylinder's current center after its local hold rewrite,
then allowing the generic S3K upward-velocity top lift to move Tails one pixel down on the horizontal
release. The fix preserves the rider's pre-hold `y_pos` for jump release and suppresses only that
released rider's same-frame generic solid contact for the cylinder object.

### New CNZ frontier (frame 11061)

`tails_x mismatch (expected=0x1A37, actual=0x1A3E)`. Tails' f10967 Y/release state now matches and
the earlier f9983 vertical release remains green. The new failure is later in the post-release arc
near monitors/balloon/cylinder context: ROM has Tails' x velocity/ground velocity zeroed while the
engine preserves `x_speed=$00C8` / `ground_vel=$0211`.

## 2026-05-23 - S3K CNZ monitor solid sidekick roll gate (CNZ f11061 -> f11310)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused monitor guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 8 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 11061
    `tails_x expected=0x1A37 actual=0x1A3E` to frame 11310
    `x expected=0x1B7D actual=0x1B7E`.
  - New count: 4319 errors / 25 warnings.

### Root cause

S3K monitor object code calls `SolidObject_Monitor_SonicKnux` for Player 1, then
`SolidObject_Monitor_Tails` for Player 2 (`sonic3k.asm:40480-40495`). The Player 2 path branches
directly to `SolidObject_cont` when not in competition mode, before testing Tails' roll animation
(`sonic3k.asm:40583-40590`). The shared `SolidObject_cont` classifier prioritizes side separation
when horizontal penetration is less than or equal to vertical penetration, zeroing `x_vel` and
`ground_vel` before subtracting the side distance from `x_pos` (`sonic3k.asm:41394-41509`).

The engine had ported S3K monitors as S1-style monitor solidity and used the roll-animation gate for
all players. That made rolling CPU Tails pass through the intact monitor at `$1A50,$00D0` instead of
taking the normal side push at frame 11061.

### New CNZ frontier (frame 11310)

`x mismatch (expected=0x1B7D, actual=0x1B7E)`. Tails and immediate speeds match at the first error.
The new failure is player-only while Sonic is object-controlled/on object near the next
CNZ cylinder/bumper cluster; engine is one pixel ahead with matching zero velocities.

## 2026-05-23 - S3K CNZ circular-cylinder held anchor (CNZ f11310 -> f11484)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 13 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 11310
    `x expected=0x1B7D actual=0x1B7E` to frame 11484
    `x expected=0x1D15 actual=0x1D14`.
  - New count: 3170 errors / 20 warnings.

### Root cause

Frame 11310 is an active held-rider frame on CNZCylinder subtype `$4B`. ROM
`Obj_CNZCylinder` runs `sub_321E2`, then Player 1/Player 2 `sub_324C0`, then
`SolidObjectFull` in one object pass (`sonic3k.asm:67656-67672`). The circular
route writes object `x_pos/y_pos` before rider handling (`sonic3k.asm:67901-67973`),
and active `loc_32538` writes the rider's `x_pos` from stored distance plus
`x_pos(a0)` (`sonic3k.asm:68019-68038`).

ROM frame 11310 still consumes cylinder slot 13 at `$1B93`; the engine's split
phase had already advanced current center to `$1B94` while its frame-entry anchor
still matched `$1B93`. The CNZCylinder held-anchor bridge now covers the proven
circular positive step for non-CPU riders, keeping the player X write on the
ROM-visible object anchor.

### New CNZ frontier (frame 11484)

`x mismatch (expected=0x1D15, actual=0x1D14)`. The f11310 player held-position
drift is cleared. The new failure is later around the next CNZ balloon/cylinder/
bumper cluster; player Y/speeds are aligned and camera follows the one-pixel X drift.

## 2026-05-23 - S3K CNZ circular-cylinder first-capture distance (CNZ f11484 -> f11503)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused cylinder guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCylinderInstance" test "-DfailIfNoTests=false"`
  - PASS: 14 tests, 0 failures.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: CNZ frontier advanced from frame 11484
    `x expected=0x1D15 actual=0x1D14` to frame 11503
    `y expected=0x00EC actual=0x00ED`.
  - New count: 2807 errors / 20 warnings.

### Root cause

Frame 11484 is the first held frame after a deferred first capture on CNZCylinder
subtype `$4C`. ROM `Obj_CNZCylinder` runs `sub_321E2`, P1/P2 `sub_324C0`, and
then `SolidObjectFull` in one object pass (`sonic3k.asm:67656-67672`). The
circular route writes the cylinder `x_pos/y_pos` before rider handling
(`sonic3k.asm:67901-67973`). The inactive capture path stores
`abs(player.x_pos - x_pos(a0))` in the rider distance byte (`sonic3k.asm:67985-67998`),
and the next active path adds that stored distance to current `x_pos(a0)` through
`loc_32538` (`sonic3k.asm:68019-68038`).

At frame 11483 ROM captures distance from cylinder X `$1CFE` and player X `$1D14`,
so the distance byte is `$16`. The engine's split object/solid pipeline consumed
that deferred standing bit after the cylinder had already stepped to `$1CFF`,
storing `$15`; the following held frame therefore wrote Sonic one pixel left.
The fix is local to `CNZCylinder`: circular, non-CPU, first-capture positive
steps use the saved frame-entry X for the distance calculation only. Active
held-frame anchor handling remains the existing disassembly-backed path.

### New CNZ frontier (frame 11503)

`y mismatch (expected=0x00EC, actual=0x00ED)` with matching player X and speeds.
Camera Y follows the one-pixel player Y drift. The immediate object context is
still the CNZ balloon/bumper/cylinder cluster, with Sonic object-controlled on
slot 11 and the cylinder at `$1D00,$0120/$0121` in the trace window. This is a
separate vertical support/carry issue and was left untouched.

## 2026-05-23 - S3K AIZ Act 2 grounded max-Y resize carry (AIZ f8943 -> f9264)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused AIZ miniboss guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestAizMinibossCameraUnlock" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Focused AIZ intro guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kAizIntroEventsHeadless" test "-DfailIfNoTests=false"`
  - PASS: 10 tests, 0 failures.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ frontier advanced from frame 8943
    `camera_y expected=0x02D0 actual=0x02CE` to frame 9264
    `tails_y expected=0x045C actual=0x045B`.
  - New count: 757 errors / 25 warnings.

### Root cause

ROM `LevelLoop` runs sprite objects before background deformation (`sonic3k.asm:7884-7895`), and
`DeformBgLayer` runs `MoveCameraY` before `Do_ResizeEvents` (`sonic3k.asm:38313-38316`). During the
AIZ results/title-card level-size release, `Change_Act2Sizes` copies the Act 2 bounds and creates the
gradual level-size objects (`sonic3k.asm:180575-180609`), while `Obj_IncLevEndYGradual` advances the
current bottom bound with a `$8000` 16.16 accumulator (`sonic3k.asm:178210-178225`).

The engine's generic camera step eases max-Y before the camera move. That made the AIZ-local
level-end proxy miss the ROM post-camera grounded `Do_ResizeEvents` carry once Sonic landed from the
post-title-card jump. The AIZ miniboss level-end max-Y proxy now carries that grounded +2 resize into
the next camera frame while keeping the ROM gradual object accumulator and target-bound copy intact.

### New AIZ frontier (frame 9264)

`tails_y mismatch (expected=0x045C, actual=0x045B)`. Player position/speed/status and camera match at
the first error. The remaining failure is sidekick-only near AIZ Act 2 collapsing platforms/fragments:
ROM Tails is at `15E6,045C` with `x_speed=$0997,y_speed=$036C,g_speed=$0A30`, while the engine is at
`15E5,045B` with the same immediate speed fields and no nearby tail object.

## 2026-05-23 - S3K sidekick panic cadence uses ROM-visible level frame (AIZ f9264 -> f10586)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused sidekick guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.playable.TestSidekickCpuFollowParity#s3kPanicReleaseGateUsesRomVisibleLevelFrameCounter" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- AIZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: AIZ frontier advanced from frame 9264
    `tails_y expected=0x045C actual=0x045B` to frame 10586
    `tails_x expected=0x2071 actual=0x2070`.
  - New count: 814 errors / 23 warnings.

### Root cause

Frame 9264 was not caused by collapsing-platform collision or a lost `ground_vel`.
Temporary phase probes showed Tails kept `ground_vel=$0A16` through frame start and
into the sidekick CPU slot, but the engine was still in `State.PANIC` for that slot.
It switched to `NORMAL` only at the end of the slot, so ROM's next normal follow
frame ran one tick earlier than the engine.

ROM `TailsCPU_Panic` reads `(Level_frame_counter+1).w` for both the `$7F` release
gate and the `$1F` rev gate (`sonic3k.asm:26869-26884`; S2 equivalent
`s2.asm:39122-39139`). The S3K engine sidekick CPU intentionally sources the
stored level counter for normal CPU gates, and that stored counter is one tick
behind the ROM-visible value inside the current sprite slot. `resolvePanicPhaseCounter`
now adds the same one-tick visibility bridge only when the CPU frame came from the
stored level counter.

### New AIZ frontier (frame 10586)

`tails_x mismatch (expected=0x2071, actual=0x2070)`. Player and camera match at the
first error. Tails is near AIZ Act 2 spiked logs/water splash objects; ROM reports
`status=$61`, `ground_vel=$FFE2`, delayed input `0808`, and post-CPU speed `$0080`,
while the engine is one pixel left with `branch=grace_push_bypass`, delayed input
`0004`, and post-physics `ground_vel=$FFDC`. This is a separate sidekick push/grace
or object-support handoff issue and was left for the next AIZ step.

## 2026-05-23 - S3K MGZ Load_Sprites cursor-order guard (frontier unchanged at f4124)

- Worktree: integrated local workspace with other workers' AIZ/CNZ/MGZ/shared edits preserved.
- Focused placement guard:
  `mvn "-Dmse=off" "-Dtest=TestObjectManagerVerticalPlacement" test "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - PASS: 5 tests, 0 failures.
- Nearby lifecycle/object guards:
  `mvn "-Dmse=off" "-Dtest=TestObjectManagerLifecycle,TestMGZSwingingPlatformObjectInstance" test "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - PASS: 8 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- MGZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: unchanged at frame 4124,
    `y_speed expected=0x0000 actual=0x01AC`.
  - Count remains 4069 errors / 64 warnings.

### Diagnosis

S3K `Load_Sprites` performs an X-cursor pass before the Y-camera pass in the
same loader call. The front cursor pass advances `Object_load_addr_front` while
scanning newly entered X-window entries (`docs/skdisasm/sonic3k.asm:37640-37658`).
The later Y-camera pass scans the already cursor-passed range and creates entries
that become vertically eligible (`docs/skdisasm/sonic3k.asm:37723-37762`) using
`CreateNewSprite4` / `AllocateObject` (`docs/skdisasm/sonic3k.asm:37889-37918`).

The engine previously reconsidered all active-window, not-yet-created spawns in
X order each frame. A focused guard now proves the S3K ordering boundary: when a
low-X spawn has already been cursor-passed but was Y-deferred, and a newer high-X
spawn enters the X window on the frame the low-X spawn becomes vertically eligible,
the newer X-pass spawn consumes the lower SST slot first. This is implemented as
an S3K `ObjectSlotLayout` profile behavior, preserving S1/S2 placement behavior.

The full MGZ replay is still blocked by a remaining upstream slot/lifecycle
divergence before frame 4124. At frame 4124 the ROM still has `MGZTopPlatform`
in slot s5 and `SinkingMud` in s9; the engine has `MGZTopPlatform` in s6 and
`SinkingMud` in s8, so Sonic remains object-controlled/suppressed and misses the
ROM landing handoff. The next MGZ owner should continue looking for the earlier
object lifecycle or allocation pressure that leaves one extra low slot occupied
before this cluster.

## 2026-05-24 - S3K CNZ Batbot Obj_WaitOffscreen margin (frontier f13628 -> f14005)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused Batbot guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.badniks.TestBatbotBadnikInstance" test "-DfailIfNoTests=false"`
  - PASS: 7 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 13628
    `y_speed expected=-0258 actual=-0158` to frame 14005
    `tails_air expected=1 actual=0`.
  - New count: 2921 errors / 17 warnings.

### Root cause

Frame 13628 was a false enemy-hit symptom. Engine player speed matched the ROM
until S3K enemy touch response applied the ROM `+0x100` upward-hit bounce-down
adjustment, but the touched Batbot was in the wrong place: engine Batbot slot 16
was still near its spawn at `@24D0,0089`, while the ROM Batbot had already chased
to `@24FE,00B0`.

`Obj_Batbot` enters through `Obj_WaitOffscreen` before its normal state machine
(`docs/skdisasm/sonic3k.asm:186266-186272`). `Obj_WaitOffscreen` installs
`Map_Offscreen`, sets width/height to `$20`, and restores the normal object op
only after the temporary sprite has been visible; the restored op runs on the
next frame (`docs/skdisasm/sonic3k.asm:180266-180297`). The engine used a
zero-margin point visibility check, so this high-on-screen Batbot waited until
the camera Y crossed its exact position and started its chase dozens of frames
late. Batbot now uses the ROM `$20` temporary-sprite margin and keeps collision
disabled during the one-frame restored-op delay.

### New CNZ frontier (frame 14005)

Tails becomes object-controlled/despawned by the cage/barber-pole area in the ROM
(`obj=$81`, air set, `tailsInteract` slot 5 destroyed), while the engine keeps
Tails on-object and grounded with continued sidekick CPU physics. Player/camera
still match at the first error. This is a separate CNZ cage/barber-pole/sidekick
state handoff issue.

## 2026-05-24 - S3K CNZ barber-pole unload frees latched sidekick (CNZ f14005 -> f14547)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused barber-pole guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzBarberPoleObjectInstance" test "-DfailIfNoTests=false"`
  - PASS: 4 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 14005
    `tails_air expected=1 actual=0` to frame 14547
    `x_speed expected=-025B actual=0x025B`.
  - New count: 2898 errors / 17 warnings.

### Root cause

At frame 14005 the ROM had already deleted barber-pole slot 5 while Tails still
carried the old on-object/interact context. `Obj_CNZBarberPoleSprite` processes
P1/P2 and then jumps to `Delete_Sprite_If_Not_In_Range`
(`docs/skdisasm/sonic3k.asm:69348-69357`), which reaches the normal SST-zeroing
delete path when the pole unloads. On the next sidekick CPU interact check,
`sub_13EFC` compares `Tails_CPU_interact` against the cleared slot, fails, and
branches to `sub_13ECA`; that routine writes `object_control=$81`, sets
`Status_InAir`, and parks Tails at `$7F00,0`
(`docs/skdisasm/sonic3k.asm:26816-26833,26800-26808`).

The engine removed the barber-pole instance from the object manager but left the
old instance non-destroyed, so Tails' stale latch reference continued to look
valid and the sidekick stayed grounded/on-object. `CnzBarberPoleObjectInstance`
now marks itself destroyed on unload using the same latched lifecycle operation
already used by CNZ wire cage; the change is object-local and does not touch
generic placement or solid-contact behavior.

### New CNZ frontier (frame 14547)

`x_speed mismatch (expected=-025B, actual=0x025B)`. The old f14005 Tails
object-control/air handoff is cleared. The new owner is later at the CNZ
miniboss cluster: player position/subpixels/camera match at frame 14547, but
engine player horizontal speed has the opposite sign after touching the boss
object, while Tails is aligned.

## 2026-05-24 - S3K CNZ miniboss body collision byte (CNZ f14547 -> f14594)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestS3kBossTouchResponseProfiles#cnzMinibossClosedBodyUsesRomObjDatCollisionByte,com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#productionTouchResponseCoilAttackOpensBossWithoutConsumingHp,com.openggf.level.objects.TestTouchResponseManager#testS3kTouchSpecialUnlistedC0FlagDoesNotDecodeAsBoss" test "-DfailIfNoTests=false"`
  - PASS: 3 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 14547
    `x_speed expected=-025B actual=0x025B` to frame 14594
    `tails_x_speed expected=0x0128 actual=-0200`.
  - New count: 2828 errors / 18 warnings.

### Root cause

The f14547 sign flip was an engine-only early boss/body touch. The engine had
the CNZ miniboss body exposed as `$CF`, inherited from the generic boss collision
flag synthesis. The S3K object data does not do that: `ObjDat_CNZMiniboss`
stores the complete body `collision_flags` byte `$0C` after the body
width/height/frame fields (`docs/skdisasm/sonic3k.asm:145652-145656`), while
`Obj_CNZMinibossInit` separately writes `collision_property(a0)=6`
(`docs/skdisasm/sonic3k.asm:144885-144889`). With the ROM `$0C` size/category,
the player does not overlap the body at frames 14547-14548 and rebounds at the
ROM frame 14549.

`CnzMinibossInstance` now overrides the full collision byte for the body instead
of using `AbstractBossInstance`'s `$C0 | size` default. The body still reports
collision_property 6, so the normal S3K enemy/boss hit path can negate player
velocities when the ROM-sized body actually overlaps. A focused shared touch
guard also preserves S3K `Touch_ChkValue`/`Touch_Special` behavior for actual
profile-gated `$C0` special objects: `$C0` flags route to `Touch_Special`, and
unlisted sizes return without boss-bounce (`docs/skdisasm/sonic3k.asm:20773-20778,
21162-21194`).

### New CNZ frontier (frame 14594)

`tails_x_speed mismatch (expected=0x0128, actual=-0200)`. The f14547/f14549
player miniboss body contact is cleared. The new owner is Tails-only after the
miniboss opens: ROM CPU post still has positive sidekick speed before object
response, while the engine has put Tails into the sidekick hurt-object routine
with `x_speed=-0200` near the opened body/top/coil cluster.

## 2026-05-24 - S3K CNZ miniboss parent start handoff (CNZ f14943 -> f14961)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#parentStartGateLeavesObjCnzMinibossGoHandoffFrameBeforeInit,com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#wait2RawGetFasterMatchesRomTopGoCadence" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Focused miniboss guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics" test "-DfailIfNoTests=false"`
  - PASS: 19 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 14943
    `g_speed expected=-0210 actual=0x0210` to frame 14961
    `tails_g_speed expected=-0218 actual=0x0218`.
  - New count: 2077 errors / 17 warnings.

### Root cause

The engine reached `Obj_CNZMinibossInit` two object updates earlier than the ROM.
The diagnostic `traceDebugDetails` timeline showed ROM parent state still at
routine 0 on frame 14278 and routine 2 from frame 14279, while the engine had
already entered routine 2 at frame 14277 and reached `Go2`/routine 4 two frames
early. That early parent phase left the boss still moving at the old f14943
contact window, so the player missed the ROM opening-body rebound.

ROM `Obj_CNZMiniboss` writes `#2*60` into the original wait object and stores
`Obj_CNZMinibossGo` in `$34` (`docs/skdisasm/sonic3k.asm:144838-144840`).
`Obj_CNZMinibossGo` only installs `Obj_CNZMinibossStart` and returns
(`docs/skdisasm/sonic3k.asm:144850-144859`); `Obj_CNZMinibossStart` dispatches
`Obj_CNZMinibossInit` on the following object update
(`docs/skdisasm/sonic3k.asm:144863-144900`). The engine event mirror now stores
one extra tick because it arms and decrements in the same update, and the parent
object preserves the separate `Obj_CNZMinibossGo` handoff frame before running
Init.

### New CNZ frontier (frame 14961)

`tails_g_speed mismatch (expected=-0218, actual=0x0218)`. The old f14943 player
miniboss opening/contact phase is cleared. At the new frontier, player position,
camera, and player speeds still match; the owner is Tails-only after the
miniboss opening/top/coil contact sequence. The ROM has Tails rebounded left
with negative ground speed, while the engine has the mirrored positive velocity
at trace capture and applies a later sidekick post-physics touch flip.

## 2026-05-24 - S3K CNZ miniboss top side-push lifetime (CNZ f15382 -> f15410)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.game.sonic3k.objects.TestCnzMinibossTopPhysics#groundedSquashEdgeContactSetsPushOnMinibossTop" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15382
    `tails_x expected=0x32D7 actual=0x32D8` through the follow-up
    f15383 push-lifetime boundary to frame 15410
    `y_speed expected=-02E8 actual=0x02E8`.
  - New count: 3148 errors / 19 warnings.

### Root cause

The f15382/f15383 Tails X drift was not hurt integration, spark timing, or
subpixel rounding. Tails scalar position and velocity matched through f15381,
but the ROM set `Status_Push` late in the object solid pass after Tails CPU.
`Obj_CNZMinibossTopMain` calls `SolidObjectFull` with `d1=$13,d2=$C,d3=8`
(`docs/skdisasm/sonic3k.asm:145057-145063`). In S3K `SolidObjectFull`, the
grounded lower-half squash-edge branch at `loc_1E126` jumps back to the side
helper when `abs(d0) < $10` (`docs/skdisasm/sonic3k.asm:41585-41594`), and
that side helper sets the object's push bit and player `Status_Push` for any
grounded side separation (`docs/skdisasm/sonic3k.asm:41488-41495`).

The first fix lets CNZ miniboss top opt into that exact grounded edge-push
behavior. The follow-up f15383 boundary exposed that the engine's object-push
latch was keyed by `instance.getSpawn()`. The miniboss top rebuilds its dynamic
spawn as it moves, so the f15382 no-contact clear checked a different key than
the f15381 contact set. ROM stores the pushing bit in the same top SST
`status(a0)` byte and clears it from `loc_1E0A2/sub_1E0C2`
(`docs/skdisasm/sonic3k.asm:41512-41532`), so the top now opts into an instance
solid-state latch key. The focused guard verifies both the grounded edge push
set and the next no-contact clear after the top's dynamic position changes.

### New CNZ frontier (frame 15410)

`y_speed mismatch (expected=-02E8, actual=0x02E8)`. The previous Tails
push-state/X position boundary is cleared: Tails position, subpixels, and
sidekick CPU branch match through the new frontier. The new owner is main-player
vertical rebound/sign handling near the CNZ miniboss coil/body while the parent
is opening (`r=08`) and the coil touch is active.

## 2026-05-24 - S3K CNZ miniboss OpenGo stored wait counter (CNZ f15410 -> f15569)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossParentSecondMovePassUsesRomPhase" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15410
    `y_speed expected=-02E8 actual=0x02E8` to frame 15569
    `camera_y expected=0x028E actual=0x028C`.
  - New count: 1925 errors / 15 warnings.

### Root cause

The f15410 sign flip was a real miniboss coil touch, but it happened because
the engine parent/coil kept moving right one frame too long after the second
Opening/Closing cycle. The parent then overlapped the player at f15410 and
`Touch_Enemy` negated the player's downward speed; the ROM parent/coil had
already turned left and missed that rebound.

ROM `Obj_CNZMinibossOpenGo` writes routine A, installs
`Obj_CNZMinibossChangeDir` in `$34`, sets the open bit, and spawns the open
sparks, but it does not write `$2E(a0)`
(`docs/skdisasm/sonic3k.asm:144945-144951`). `Obj_CNZMinibossWaitHit` and
`Obj_CNZMinibossClosing` do not call `Obj_Wait`
(`docs/skdisasm/sonic3k.asm:144954-144969`), so the pre-opening Move wait
counter survives until `CloseGo` returns to Move. `Obj_Wait` then decrements
that stored `$2E` and jumps through `$34` when it goes negative
(`docs/skdisasm/sonic3k.asm:177944-177952`). The engine had cleared the wait
counter in `onOpenGo()`, delaying the post-open `ChangeDir` and leaving the
coil at the wrong side of the player. `onOpenGo()` now preserves the stored
wait counter, and the focused guard asserts the ROM-visible f15409 parent X
and routine before the cleared f15410 touch window.

### New CNZ frontier (frame 15569)

`camera_y mismatch (expected=0x028E, actual=0x028C)`. At the new first error,
player and Tails positions, subpixels, velocities, status, and object state are
aligned enough; the first scalar owner is camera Y after the miniboss sequence.

## 2026-05-24 - S3K CNZ miniboss scroll-control ROM Wait/Slow handoff (CNZ f15627 -> f15723)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzMinibossArenaHeadless#scrollControlBridgeSignalUsesRomWaitAndSlowPathBeforeEventHandoff,com.openggf.tests.TestS3kCnzMinibossArenaHeadless#scrollControlPostBossPhasesSnapEnableBackgroundCollisionAndDeleteAt1C0" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15627
    `camera_y expected=0x02B8 actual=0x02BA` to frame 15723
    `camera_x expected=0x3260 actual=0x3278`.
  - New count: 1941 errors / 16 warnings.

### Root cause

The f15627 camera-Y drift was not a generic vertical camera bias problem.
`CnzMinibossScrollControlInstance.updateMain()` had an engine-only high-offset
shortcut that completed the final post-boss handoff as soon as the miniboss
defeat signal arrived with the FG offset already near `$1C0`. ROM
`Obj_CNZMinibossScrollMain` only clears `Events_fg_5`, advances the scroll
control routine, and falls through to the wait path
(`docs/skdisasm/sonic3k.asm:107770-107795`). The target max-Y restore to
`$1000` happens later in Wait2 at `loc_5209E`
(`docs/skdisasm/sonic3k.asm:107814-107828`), and the final event signal is
emitted from Wait3 only after the offset reaches `$1C0`
(`docs/skdisasm/sonic3k.asm:107841-107851`). Removing the shortcut keeps the
arena current/target max-Y at `$02B8` through the previous f15627 window, as
set when the miniboss arena starts (`docs/skdisasm/sonic3k.asm:144830-144837`).

### New CNZ frontier (frame 15723)

`camera_x mismatch (expected=0x3260, actual=0x3278)`. Player and Tails scalar
state and camera Y match at the new first error; the remaining owner is
horizontal camera/clamp release timing after the CNZ miniboss handoff.

## 2026-05-24 - S3K CNZ miniboss Boss_flag falling edge keeps arena X clamp (CNZ f15723 -> f15735)

- Worktree: integrated local workspace with other workers' AIZ/MGZ/CNZ/shared edits preserved.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.TestS3kCnzMinibossHeadless#minibossDefeatKeepsArenaXClampOnBossFlagFallingEdge,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnzMinibossPostBossKeepsArenaXClampAfterBossFlagClear" test "-DfailIfNoTests=false"`
  - PASS: 2 tests, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 15723
    `camera_x expected=0x3260 actual=0x3278` to frame 15735
    `tails_y_speed expected=0x0000 actual=0x05C0`.
  - New count: 1940 errors / 16 warnings.

### Root cause

The engine restored the saved pre-arena horizontal camera bounds on the
`Boss_flag` falling edge. ROM `Obj_CNZMinibossEndGo` clears `Boss_flag`, calls
`AfterBoss_Cleanup`, and loads end-sign PLC state
(`docs/skdisasm/sonic3k.asm:144996-145001`), but CNZ's after-boss cleanup
entry is just `rts` (`docs/skdisasm/sonic3k.asm:176489-176557`). That means
`Camera_max_X_pos` remains the arena clamp `$3260` after `Boss_flag` clears;
the later CNZ scroll-control/background handoff owns the subsequent post-boss
progression (`docs/skdisasm/sonic3k.asm:107603-107653`). The engine now only
releases wall-grab suppression on the falling edge and leaves the X clamp in
place.

### New CNZ frontier (frame 15735)

`tails_y_speed mismatch (expected=0x0000, actual=0x05C0)`. Player and camera
remain aligned through the prior f15723 camera-X window; the next owner is
Tails' post-boss/miniboss-top aftermath state near the destroyed/inactive
object slot 5 marker.

## 2026-05-24 - S3K Bubble Shield steep landing inertia ordering (CNZ f18735 -> f18916)

- Worktree: integrated local workspace with other workers' CNZ/AIZ/MGZ/shared edits preserved.
- Focused guard:
  `mvn "-Dmse=off" "-Dtest=com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kBubbleShieldSteepLandingCopiesPostBounceYSpeedToGroundSpeed" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 18735
    `g_speed expected=0x01E6 actual=0x04BA` to frame 18916
    `y expected=0x0B6C actual=0x0B58`.
  - New count: 1195 errors / 17 warnings.

### Root cause

At f18735 Sonic lands on a steep surface while underwater with Bubble Shield
bounce armed. The engine wrote `ground_vel` from the pre-bounce `y_vel`, then
`BubbleShield_Bounce` rewrote `x_vel/y_vel`, leaving stale inertia. ROM's S3K
steep air-floor path reaches `loc_11FC2`, calls
`Player_TouchFloor_Check_Spindash`, and only then copies `y_vel` into
`ground_vel` (`docs/skdisasm/sonic3k.asm:24112-24117`). That makes
`ground_vel` observe Bubble Shield's post-bounce `y_vel`.

### New CNZ frontier (frame 18916)

`y mismatch (expected=0x0B6C, actual=0x0B58)`. The f18735 Bubble Shield
inertia mismatch is cleared. The new owner is later player Y near the CNZ2
balloon/triangle-bumper/vacuum-tube cluster; player speed and `ground_vel`
are already in the high launch state at the first error.

## 2026-05-25 - S3K CNZ AirCountdown investigation cleanup (f20219 regression -> f21833)

- Worktree: integrated local workspace with other workers' edits preserved.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: f20219 `tails_y expected=0x0A2E actual=0x0A2C` was traced to an
    unretained fixed-AirCountdown investigation flag that suppressed the
    shared drowning controller before a ROM-equivalent fixed object pass was
    implemented. Removing that speculative suppression restores the run past
    the prior f21772 frontier.
  - New frontier: frame 21833
    `y_speed expected=-0700 actual=-06C8`.
  - New count: 1378 errors / 17 warnings.

### Cleanup

Removed the speculative `fixedAirCountdownController` feature flag and the
`AbstractPlayableSprite` gates that skipped drowning-controller update/reset
paths. Also removed AirCountdown-specific focused guard assertions from the CNZ
slot-pressure diagnostic test. No fixed `Breathing_bubbles` behavior is
retained; the remaining f21833 owner is the CNZ2 balloon/bumper launch window.

## 2026-05-25 - S3K fixed AirCountdown sidecars preserve CNZ2 slot/RNG cadence (CNZ f21833 -> f22036)

- Worktree: integrated local workspace with other workers' CNZ/AIZ/MGZ/shared edits preserved.
- Focused guards:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnz2FixedAirCountdownInitialCadencePreservesBubblerSlot" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#traceReplayCnz2PreBalloonSlotPressureMatchesRomSequence" test "-DfailIfNoTests=false"`
  - PASS: 1 test, 0 failures.
- Compile guard:
  `mvn "-Dmse=off" test-compile "-DfailIfNoTests=false"`
  - PASS.
- CNZ replay:
  `mvn "-Dmse=off" "-Dtest=com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay#replayMatchesTrace" test "-DfailIfNoTests=false"`
  - Result: advanced from frame 21833
    `y_speed expected=-0700 actual=-06C8` to frame 22036
    `y_speed expected=-06C8 actual=-0700`.
  - New count: 1460 errors / 17 warnings.

### Root cause

S3K fixed `Breathing_bubbles` / `Breathing_bubbles_P2` are not dynamic SST
objects. The ROM keeps them in fixed object RAM after dynamic sprites and
before ScreenEvents (`docs/skdisasm/sonic3k.constants.asm:311-312`;
`docs/skdisasm/sonic3k.asm:7893-7898,35965`), while visible countdown bubbles
allocate through normal dynamic `AllocateObject` from the fixed controller
cadence (`docs/skdisasm/sonic3k.asm:33490-33610`). The engine previously had
no equivalent fixed controller in this phase, so the f17824 AirCountdown RNG
calls and visible child slot pressure were missing. The retained S3K sidecar
executes P1 then P2 fixed controllers without consuming dynamic SST slots,
lets visible `Obj_AirCountdown` children consume normal dynamic slots, and
suppresses only the conflicting generic S3K drowning bubble RNG/cadence while
leaving air timer semantics in the player drowning controller.

The visible child lifecycle now follows the observed ROM path at f17824-f17834:
routine 0 init, routine 2 rising at `y_vel=$FF00`, surface transition to
routine 8, then deletion (`docs/skdisasm/sonic3k.asm:33306-33370`).

### New CNZ frontier (frame 22036)

`y_speed mismatch (expected=-06C8, actual=-0700)`. The prior f21833 immediate
launch mismatch is cleared. The new owner is still CNZ2 balloon phase/touch
timing around the `@1308,05E8` / `@12F8,0645` balloon cluster; engine is now
touching/popping the `@1308,05E8` balloon at f22036 while ROM has already
observed one gravity step after the corresponding launch.
