# ROM-owned solid contact (2026-10-31)

Branch `bugfix/ai-rom-owned-solid-contact`, based on `feature/ai-weakness-pass`
at `36aaddb37`. Follows the
[sidekick CPU fitted-logic removal](2026-10-31-sidekick-cpu-fitted-logic-removal.md),
whose "root cause" section blamed frame order for the remaining solid-contact
compensations.

The user asked to "fix the ordering and start pruning". The lead verified that
the outer frame order already matched the ROM: player slots, then objects in
slot order, each object resolving players inside its own routine (S1
`ExecuteObjects`, _inc/ExecuteObjects.asm:10-31; S2 `RunObjects`,
s2.asm:29805-29846; S3K `Process_Sprites`, sonic3k.asm:36005-36031). What
differed was *who writes* the contact state and *which register* the helper
carries the rider by. The design synthesis (architect runners Opus + Sol, lead
synthesis) set six steps; this lane landed steps 1-3 and stopped at step 3's
stop condition (below).

Disassembly citations are at the project pins (`s1disasm` `28872a46e2d4`,
`s2disasm` `34fc7f130a96`, `skdisasm` `8b2cf3c4178f`).

## Target

Only ROM writers write contact state. Player routines read `Status_OnObj` and
write only the bits their ROM routines write; objects write contact state at
their ROM call site; nothing runs after the object pass. Every solid helper
call carries the ROM input registers, including `d4`, the x the standing
branch carries the rider from (`x_pos(a1) -= d4 - x_pos(a0)`: `MvSonicOnPtfm`
/ `loc_19BA2` s2.asm:35641-35662; `MvSonicOnPtfm` / `loc_1E1CA` / `loc_1E1F4`
sonic3k.asm:41687-41742; S1 `MvSonicOnPtfm2` / `MoveWithPlatform`, _incObj/sub
MvSonicOnPtfm.asm:18-42).

## What landed

| Commit | Step | Change |
| --- | --- | --- |
| `79e0f8a8b` | 1 | Deleted the legacy player-side solid sweep (`ObjectSolidContactController.update` / `processGlobalSolidInstance`, `SpriteManager.applySolidContacts`, `CollisionSystem.runSolidObjectResolution` / `resolveSolidContacts`, `ObjectManager.updateSolidContacts`), the always-false `deferSideToPostMovement` mode, the `FrameCollisionPlan` solid phase and `objectResolutionOnly()`, and the `ObjectInteractionRules.objectsExecuteAfterPlayerPhysics` rule flag with its objects-first branches (`LevelFrameStep`, `GameLoopEndingSession`, AIZ vines, ring slot deferral, `LevelManager` water tail). |
| `2c9deefcc` | 2 | `SolidCall` (d1-d4) and `ObjectSolidExecutionContext.solid(call)` / `solidFor(player, call)` / `entryCall()`; `ObjectExecutionController` captures d4 (and each folded piece's x) at routine entry. Deleted `RidingState.x/y` and their rewind fields, `refreshRidingTrackingPosition` (Tornado, speed launcher), `seedsNewRideCarryFromPreUpdateX` (4 objects), `usesPreUpdateXForContinuedRide` (ICZ end boss), and `resolveSolidNow(player)` (resolved every player). |
| `b441879db`, `993794609`, `140ff90d9`, `8f64c0287` | 2 (follow-ups) | Per-object ROM d4: ObjC0 speed launcher (`move.w x_pos(a0),d4`, s2.asm:80889-80896), Obj83 platform children (`Obj83_last_x_pos`, s2.asm:57733-57741, seeded at 57593-57606), Obj45 horizontal spring (saved d4 restored per character, s2.asm:50399-50430; deleted its same-frame rider carry and next-frame suppression flags), WFZ boss floating platform (pushed x before ObjectMove, `ObjC5_PlatformMakeSolid` s2.asm:81870-81877). `SolidObjectProvider.tailCallD4(routineEntryX)` lets an AUTO routine declare its tail d4. |
| `df93f7c13` | 3 | Only object solid calls write `Status_OnObj` / `Status_InAir`. Deleted together: the post-object finaliser (`finishInlineFrame` / `finalizeInlinePlayer`) and its markers (`inlineSupportedPlayers`, `markObjectSupportThisFrame` in the S2 spiral, SOZ loop/quicksand, DEZ gravity tube; `forceAirOnStaleSupportLoss` and the seamless-transition flag); the player-slot grounding recovery and `hasGroundingObjectSupport` with the `providesPreMovementGroundAttachmentSupport` / `suppressesGroundingRecoveryFromAirborneStaleRide` hooks; the AnglePos stale-support clear (Player_AnglePos returns on Status_OnObj, sonic3k.asm:18771-18777); the slope-repel support fallback; the cached standing/headroom snapshots; the S1 collapsing-floor grounded-clear note and restore; the cross-object air unseat and `airborneRiderUnseatRequiresOwnCheckpoint` (10 overrides). Solid-contact rewind state shrank from 11 lists to the ride record plus standing/pushing bits. |
| `e40c4a3a9`, `ef9d43171` | 3 (follow-ups) | Objects that release a rider now clear its `Status_OnObj` where their ROM routine does: S2 Obj40 springboard (`loc_264BC`, s2.asm:52398-52400), S2 Obj32 breakable block (`Obj32_BouncePlayer`, s2.asm:49405-49416), S1 Obj51 smash block (_incObj/51 MZ Smashable Green Block.asm:52-67), S1 Obj6C vanishing platform (_incObj/6C SBZ Vanishing Platforms.asm:110-118), S1 elevator, platform, spin conveyor and spin platform releases. The ARZ boss arrow starts its decay timer after both characters' PlatformObject call (FixBugs=0 `Obj89_Arrow_Platform`, s2.asm:65816-65833). |
| `d716b0f59` | 3 | The CPU reads the leader's live `Status_OnObj` (loc_13DA6, sonic3k.asm:26730-26734); the kill path no longer ORs the frame-start bit back in (`Kill_Character`, sonic3k.asm:21172-21193). |

Size: `ObjectSolidContactController` 5,702 → 4,553 lines (−1,149). Main
sources: 76 files, +514 / −2,171 (unchanged since `ef9d43171`). The whole branch
including tests and docs is 149 files, +1,330 / −2,987.

## Deviations from the synthesis

- **No `SolidHelper` enum.** `SolidRoutineProfile` / `SolidRoutineKind`
  already select the helper branch; a second helper vocabulary would be two
  ways to name one thing. `SolidCall` carries d1-d4 only.
- **No separate `SolidContactLedger` class or registered RewindSnapshottable.**
  The ROM-shaped state (ride record = `interact`, standing and pushing bits)
  stays in the controller and is still captured through
  `ObjectManagerSnapshot.SolidContactState`, now four fields. The side maps the
  synthesis listed are gone except `objectStandingBitSnapshot` (rebuilt each
  frame, no longer rewound), `standingBitEstablishedThisFrame` and
  `controllerAirborneReleaseSupports` (MGZ top platform), which belong to step
  4/5 callers.
- **`tailCallD4` added** (the synthesis' `provider.tailSolidCall(d4)`, d4
  only, so the provider's params are still read once at the helper site).
  `carriesRiderOnHorizontalMove` (27 overrides) still expresses "d4 = x_pos"
  for continued rides: a duplicate to convert in step 4/6.
- **Immediate re-run checkpoints** (`processImmediateInlineSolidCheckpoint`:
  CorkFloor, MGZ trigger platform, HCZ2 wall) are second calls for objects
  that already ran this frame; they use the object's current x as d4 so they
  cannot carry a rider twice. They remain slot-order compensations.
- **Boss children run by their parent** (`AbstractBossInstance.updateChildren`)
  see a post-move routine-entry x at their own slot. Only the WFZ platform was
  fixed (trace evidence). S1 FZ cylinder / plasma launcher and the HCZ end-boss
  water column are solid boss children with the same exposure and no measured
  regression.
- **Left in place, now unused or questionable:** `PostPlayerUpdateHook` has no
  production caller (only the deleted legacy branch called it; MHZ swing vine
  and S2 spiral tests call it directly); `runTouchResponsesForPlayer`'s
  pre-update-state flag is always true in production; the frame-start OnObj
  reads in crouch/balance (`PlayableSpriteMovement`) were not converted.

## Rejected or stopped

- Making `clearRidingObject` also clear the player's `Status_OnObj` globally
  was rejected: some callers drop the engine record deliberately while a
  controller owns the bit. Releases were fixed per object, each against its
  cited ROM `bclr`.
- **Stopped at step 3.** S1 SLZ1 (f4055 `air` 0/1) and SLZ2 (f4510) stay red
  without a cited fix. Instrumented at SLZ1: Sonic leaves collapsing floor s54
  for s63; ROM keeps `Status_OnObj` (status `$0C` every frame, stand-on slot
  `$36`→`$3F` at f4052). The engine's s63 lands him one frame like the ROM, but
  s54's own `.delayCollapse` walk-off clear (engine
  `Sonic1CollapsingFloorObjectInstance.updateDisplay`) fires two frames later,
  after the hand-off, so nothing re-lands him. The engine models S1
  `ExitPlatform` twice for this object (the solid kernel's riding branch and the
  object's walk-off clear); the deleted grounded-clear restore pair masked the
  late second clear. Fixing it needs the ROM's per-frame s54 flag/timer state,
  not a frame-keyed gate. Steps 4-6 were not started.
- **SSZ1 engine-authored cold routes stay red.** Both native320 controller
  routes go off their recorded path at input 1366, where P1 jumps from the
  `$77` cutscene bridge inside a `$14` updraft's band. The updraft zeroes
  `y_vel` (`sub_3FBC4`, sonic3k.asm:84482-84533). At base the bridge's own
  solid call then landed P1 again in the same call. On this branch the bridge
  still holds its standing bit, so it takes the airborne release (`loc_1E2E0`,
  sonic3k.asm:41852-41857) and returns, which is the ROM's order. The inputs
  were authored against the old re-landing. Re-authoring them would be a
  re-fit, so this lane did not do it. Recorded in the
  [SSZ1 matrix](../validation/levels/s3k-ssz-act1.md#rom-owned-solid-contact-opening-bridge-jump-2026-10-31).

## Measurement

Same protocol as the sidekick audit. Every measured commit was checked out in
one separate measurement worktree (`.worktrees/solid-order-baseline-36aaddb37`,
created and removed by this lane), with `target/classes` and
`target/test-classes` deleted first, Java 25, surfaceless EGL,
`DISPLAY`/`WAYLAND_DISPLAY`/`LD_LIBRARY_PATH` unset, the three original ROMs,
one reused fork, alphabetical order:

```bash
COMMON="-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical"
# A: the sidekick audit's -Ptrace-replay class list (S2 + S3K Sonic+Tails)
python3 tools/testing/maven_queue.py --normal -Dmse=off <ROM paths> -Ptrace-replay $COMMON \
  -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=<sidekick audit A list>' test -B
# A1: S1 (solid-heavy, no sidekick)
python3 tools/testing/maven_queue.py --normal -Dmse=off <ROM paths> -Ptrace-replay $COMMON \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dtest=TestS1Credits*TraceReplay,TestS1*CompleteRunTraceReplay,TestS1Ghz1TraceReplay,TestS1Mz1TraceReplay,TestS1GhzMazeRoundTripChain,TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix' test -B
# B: the whole -Ptrace-segments profile
python3 tools/testing/maven_queue.py --normal -Dmse=off <ROM paths> -Ptrace-segments $COMMON test -B
```

143 classes per measurement (A 42 classes / 111 tests, A1 32 classes / 34
tests, B 69 classes / 70 tests). Execution per measurement: A ~7m00s, A1
~1m50s, B ~4m30s. "Identical" compares every failing testcase's full message
with worktree paths normalised. The baseline at `36aaddb37` reproduces the
sidekick audit's after-state exactly (A 9 failures + 1 error, B 48 + 8) and
adds A1: 31/32 S1 classes green, `TestS1CompleteEmeraldRunChain` red
(segment 12).

### Transitions per measured commit (each against the `36aaddb37` baseline)

| Step | Measured commit | green→green | green→red | red→red identical | red→red changed | red→green |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `79e0f8a8b` | 79 | 0 | 64 | 0 | 0 |
| 2 | `2c9deefcc` | 76 | 3 | 63 | 1 | 0 |
| 2 follow-up | `b441879db` | 78 | 1 | 63 | 1 | 0 |
| 3 | `df93f7c13` | 66 | 13 | 56 | 8 | 0 |
| 3 follow-up | `e40c4a3a9` (includes `8f64c0287`) | 76 | 3 | 57 | 7 | 0 |
| 3b + arrow | `ef9d43171` (HEAD; includes `d716b0f59`) | 77 | 2 | 57 | 7 | 0 |

Step 1 was byte-identical, as expected (all three games already set the flag).

### Every non-identical class

| Class | First seen | Baseline | At HEAD | Root cause / writer |
| --- | --- | --- | --- | --- |
| `TestS2WfzLevelSelectTraceReplay` | step 2: f271 `x` $0237/$0243; 2b: f10908 `x` $2B29/$2B2A | green | green | ObjC0_Main d4 = x_pos (s2.asm:80889-80896); WFZ boss platform d4 = x pushed before the move, ObjC5_PlatformMakeSolid (s2.asm:81870-81877) |
| `TestS2Arz2LevelSelectTraceReplay` | step 2: f3708 `x` $1BB5/$1BB4; 3: f2874 `tails_status_byte` $02/$0A; 3 fix: f6172 $06/$0E | green | green | Obj83 child d4 (s2.asm:57733-57741); Obj40 springboard bclr (s2.asm:52398-52400); arrow decay timer written after both characters, Obj89_Arrow_Platform (s2.asm:65816-65833) |
| `TestS2Ooz2LevelSelectTraceReplay` | step 2: f1602 `tails_x` $049B/$049A | green | green | Obj45 saved d4 per character (s2.asm:50399-50430) |
| `TestS2Cpz2LevelSelectTraceReplay` | step 3: f983 `tails_status_byte` $02/$0A | green | green | Obj40 springboard |
| `TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay` | step 3: f2433 same | green | green | Obj40 springboard |
| `TestS2Mcz2LevelSelectTraceReplay` | step 3: f2422 same | green | green | Obj40 springboard |
| `TestS2CpzLevelSelectTraceReplay` | step 3: f3438 `dynamic_art.edge[2].present` | green | green | recovered with `e40c4a3a9` |
| `TestS2HtzLevelSelectTraceReplay` | step 3: f1261 `angle` $00/$08 | green | green | Obj32 breakable block bclr (s2.asm:49405-49416) |
| `TestS2Htz2LevelSelectTraceReplay` | step 3: f1024 `dynamic_art` | green | green | recovered with `e40c4a3a9` |
| `TestS1Mz2CompleteRunTraceReplay` | step 3: f11207 `player_animation_id` 5/6 | green | green | Obj51 smash block bclr (51 MZ Smashable Green Block.asm:52-67) |
| `TestS1Mz3CompleteRunTraceReplay` | step 3: f8178 same | green | green | Obj51 smash block |
| `TestS1Sbz1CompleteRunTraceReplay` | step 3: f3234 `air` 1/0 | green | green | Obj6C vanishing platform bclr (6C SBZ Vanishing Platforms.asm:110-118) |
| `TestS1Slz1CompleteRunTraceReplay` | step 3: f4055 `air` 0/1 | green | **red, 2747 errors** | not fixed; see "Rejected or stopped" |
| `TestS1Slz2CompleteRunTraceReplay` | step 3: f4510 `air` 0/1 | green | **red, 1744 errors** | not fixed; same S1 collapsing-floor walk-off |
| `TestS1CompleteEmeraldRunChain` | step 3 | red, 5 axes | red, 26 axes | same failing segments (12, 15, 26, 32); segment 15 errors 6 → 5187 after the same first frame (the SLZ regression) |
| `TestS3kLbzZoneSliceTraceReplay` | step 2 | 1663 errors, first f30582 `tails_y` | report: 1596 errors, same first error, then `Invalid rewind reference closure` at f42015 | post-frontier trajectory reaches a latent `LbzFinalBoss1Instance$MuzzleLaserChild` → `LaserHeadChild` rewind-identity gap |
| `TestS3kMgzZoneSliceTraceReplay` | step 3 | 10634, first f5255 | 10587, same first | after the frontier |
| `TestS3kSonicTailsAiz5SegmentTraceReplay` | step 3 | 2522, first f0 | 2518, same first | after the frontier |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | step 3 | 1177, first f0 | 1306, same first | after the frontier |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | step 3 | 2157, first f121 | 2155, same first | after the frontier |
| `TestS3kSonicTailsLrzSegmentTraceReplay` | step 3 | 8870, first f208 | 8844, same first | after the frontier |

`TestS3kAizTraceReplay` (the AIZ → HCZ route) stayed green at every step,
including after the AIZ1→AIZ2 stale-support flag was deleted: the AIZ
transition floor's own SolidObjectTop call releases the rider. The S2 OOZ1
f447 dead-Tails `Status_OnObj` frontier from the sidekick audit is unchanged
(the finaliser is gone, but the writer is still unidentified).

## Validation

Per step, in this worktree (`maven_queue.py --normal -Dmse=off`, three ROM
paths, surfaceless EGL):

- Step 1: 36 focused classes incl. the S3K keep-green set, 879 tests; 9
  failures were fixture gaps the deleted sweep never hit (CPU Tails without an
  on-screen render flag for the P2 gate, camera bounds for the SolidObjectFull
  render gate, the legacy objects-first order in frame-order mocks) and
  were fixed in the fixtures; the immediate-allocation ring case
  (`newlyAllocatedLaterSlotExecutesButPastSlotWaitsWithoutLateRescue`) tested
  the deleted flag=false mode and was removed. Re-run: 0 failures.
- Step 2: 42 classes, 872 tests, 0 failures after migrating the deleted
  `resolveSolidNow` tests to the per-player form and the hook-value assertions
  of the four deleted carry hooks.
- Step 3: 169 classes, 1,902 tests, 0 failures after fixture updates (the
  springboard/OOZ scripted resolver now answers participant calls; the stale
  AnglePos test now asserts the ROM early return); 8 skips are opt-in SOZ
  capture tests gated on system properties.
- Step 3b and the arrow fix: 36 + 2 classes, 0 failures.
- `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`: green at every
  step.
- `-Pguards`: 820 tests, 0 failures at steps 1, 2, 3, the d4 follow-up and
  HEAD (one intermediate run caught the stale OOZ-spring exact rewind policy,
  fixed in `140ff90d9`).
- Mod API candidate pin regenerated at steps 1, 2, 3 and the tail-d4 commit
  (removals of the deleted members; additions `SolidCall`, `solid`, `solidFor`,
  `entryCall`, `tailCallD4`, `placeOnTerrain`).

Change-based smoke at `771e02403`, run with
`run_categories.py --base 36aaddb37 --workers 2 --run`, surfaceless EGL (run
`20261031T232437Z-6bbd419d`, 4,366 classes in 88 batches):

- Ordinary lane: 36,166 tests, 12 failures, 0 errors, 49 skips, 1,471 s.
- Guards: 820 tests, 0 failures, 139 s.
- Eight failures also fail at base: `TestMasterTitleScreenLayout` (2),
  `TestSonic2InterlacePixels`, `TestNativeCompetitionViews`,
  `TestNativeSpriteSatGameplay` (3) and `TestPrivateLobbyScreenRendering`.
- The other four come from this branch. The four classes run at both commits
  give 56 tests and 0 failures at `36aaddb37`, and exactly these four failures
  at `771e02403`:
  - `TestS1Ghz1Headless#testStandingOnObjectDoesNotTriggerSlopeRepel` and
    `TestS3kIcz2StaleObjectGrounding` asserted deleted compensations: the
    slope-repel object-support fallback and the AnglePos stale clear.
    `7d29ab35a` rewrites them to the ROM routines. S1 `Sonic_SlopeRepel` has no
    `Status_OnObj` test. A rider's angle stays 0 because SolidObject's landing
    clears it and `Sonic_AnglePos` returns on the bit. Re-run: 31/0 and 1/0.
    The ICZ2 test no longer guards the original screenshot bug, and the object
    that left that stale bit is still unidentified.
  - `TestSszBossInputAuthorTool#authoredFatalHitReplaysFromColdWithIdenticalObservedState`
    and `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost`
    replay the SSZ1 controller routes and stay red (see *Rejected or stopped*).
- Skips: all 49 are gated by a system property, `exhaustive-audit`, a perf or
  native-GPU check, or a capture tag. The exception is
  `TestCPZObjectBugs#testSpinTubeForcesRolling`, a runtime assumption that
  skips the same way at `36aaddb37`.

## Continuation: items A-D (2026-11-01)

Branch `bugfix/ai-rom-owned-solid-contact-2`, based on `feature/ai-weakness-pass`
at `c81ffaf7c` (the merge of steps 1-3). Same measurement protocol and 143
classes as above. Baseline and every measured commit ran in a separate
copy-on-write worktree with `target/classes` and `target/test-classes` deleted
first. The baseline at `c81ffaf7c` reproduces the step-3 head: 77 green, 66 red,
215 tests, no skips.

### What landed

| Commit | Item | Change |
| --- | --- | --- |
| `1bee975c8` | A | S1 collapsing floor (Obj53) releases its rider where `ExitPlatform` does. Instrumented SLZ1 f4045-f4062: at f4052 floor s54 enters `CFlo_OnPlatform` with timer 1, decrements to 0, and `CFlo_WalkOff` releases Sonic at x `$1341` (band `[$1300,$1340)`), resetting the floor to routine 2 (_incObj/sub ExitPlatform.asm:21-34); s63 lands him in the same frame; at f4053 `CFlo_ChkTouch` sees the flag with a zero timer (_incObj/1A, 53 Collapsing Ledges and Floors.asm:176-180) and takes `Fragmentate_8x2Floor`, which clears the flag (_incObj/1A, 53 Collapsing Ledges and Floors.asm:278-279). The engine had two errors: `pendingNoResetFragment` also held the rider on the routine-4 frame whose timer had just reached zero (`df2d03d60` added that clause while the timer ran a frame late; `8b1120208` fixed the timer and left the clause), and the routine 4 -> 2 reset ran only for an airborne rider. The floor therefore fragmented with the flag kept, and its `.delayCollapse` port cleared the bit s63 had set two frames later. |
| `d2678cf45` | B | LBZ final boss muzzle drops its head reference where `loc_7321A`'s routines stop reading parent3: a counting-down muzzle deletes itself when the head has bit 7 (`loc_73232` / `loc_7326A`); a blinking or charging muzzle holds the head's last position (the head's `Obj_FlickerMove` flight is not modelled, see [S3K known bugs](../../status/s3k-known-bugs.md)); the fired bolt (`loc_732BA` / `loc_732F6`) drops it. |
| `c7d6a81b1` | D | Deleted `PostPlayerUpdateHook` (no production caller since the objects-first order went; the S2 cylinder, MHZ swing vine and both AIZ ride vines already did the hook's work in their update; the S2 signpost walk-off now runs in update for every collision model) and the unread `GroundWallResponseState.preControlGSpeed`. |
| `b6669b030` | D | `carriesRiderOnHorizontalMove` folded into the call's d4 for 25 of 26 overrides: AUTO routines return their current x from `tailCallD4` (or the entry x when they carry), MANUAL routines pass their d4 through the new `ObjectSolidExecutionContext.solidWithD4` / `checkpointAll(int)`. |
| `64e49c0f5` | D (step 4) | S1 collapsing floor is a MANUAL routine with the ROM's calls: `PlatformObject` in `CFlo_ChkTouch`, `CFlo_WalkOff` in `CFlo_OnPlatform` and `.delayCollapse`, no call on the `Fragmentate_8x2Floor_NoReset` frame. `.delayCollapse` applies `ExitPlatform`'s global `bclr` and tests Sonic's live `Status_OnObj` as the ROM does. Routine transitions read the checkpoint result for v_player (MANUAL checkpoints fire no `SolidObjectListener` callbacks). Deleted its `suppressSlopeSampleThisFrame` / `defersAirborneRiderUnseatThisFrame` hold, both listener methods and `pendingNoResetFragment`. |
| `95d25ddd8`, `84b48e6d8` | D (step 6) | Deleted `projectsPreMovementAirYForSolidContact` (monitor only; it ran only for non-post-movement checkpoints, which AUTO routines never get), `getFullSolidPlayerPositionHistoryFrames` (no override), both `SolidRoutineAdapter`s, `SolidRoutineProviderForwarding` and both `SolidRoutineProfile.adapt` factories (test-only), and the S2 monitor's pending-x projection, whose premise was the deleted objects-first order (`Obj26_Main`, s2.asm:25579-25605). Two monitor tests that relied on the y projection now place Sonic at his post-move y in a post-movement checkpoint. |
| `f8214016b` | D | The S3K crouch check reads the live `Status_OnObj` as `SonicKnux_Roll` does before writing Duck (sonic3k.asm:23258-23300) instead of also accepting the player-slot entry snapshot, which existed for a deleted mid-slot clear. |

Size against `c81ffaf7c`: `ObjectSolidContactController` 4,553 -> 4,520
lines (-33); `SolidObjectProvider` 871 -> 843. Main sources: 50 files,
+218 / -716. Whole branch: 78 files, +319 / -875. Hooks and types deleted:
`PostPlayerUpdateHook`, `preservesMovingSideContactVelocity` (attempted, see
below; not deleted), `projectsPreMovementAirYForSolidContact`,
`getFullSolidPlayerPositionHistoryFrames`, `SolidRoutineAdapter` x2,
`SolidRoutineProviderForwarding`, `SolidRoutineProfile.adapt` x2, 25
`carriesRiderOnHorizontalMove` overrides, Obj53's two pass-hold overrides and
its listener. The Mod API candidate pin was regenerated at `c7d6a81b1` (-4),
`b6669b030` (+2: `solidWithD4`, `checkpointAll(int)`) and `95d25ddd8` (-70).

### Bisect for B

The LBZ boss classes are byte-identical at `36aaddb37` and `c81ffaf7c`; the new
`TestLbzFinalBoss1GraphRewind#muzzleKeepsNoReferenceToARetiredHead` fails all
three stages (countdown, blink, fired) at `36aaddb37` with the trace's exact
message. Steps 1-3 only changed the post-frontier trajectory that reaches it.

### Transitions per measured commit (each against `c81ffaf7c`)

| Measured commit | green->green | green->red | red->red identical | red->red changed | red->green |
| --- | --- | --- | --- | --- | --- |
| `1bee975c8` (A) | 77 | 0 | 63 | 1 | 2 |
| `d2678cf45` (B) | 77 | 0 | 62 | 2 | 2 |
| `b6669b030` (d4) | 77 | 0 | 62 | 2 | 2 |
| `64e49c0f5` (Obj53 MANUAL) | 77 | 0 | 62 | 2 | 2 |
| `84b48e6d8` (strip) | 77 | 0 | 62 | 2 | 2 |
| `f8214016b` (crouch) | 77 | 0 | 62 | 2 | 2 |

Red->green: `TestS1Slz1CompleteRunTraceReplay`, `TestS1Slz2CompleteRunTraceReplay`
(full passes). Changed reds: `TestS1CompleteEmeraldRunChain` (same failing
segments 12, 15, 26, 32; segment 15 back from 5,187 errors to 6, the
`36aaddb37` count) and `TestS3kLbzZoneSliceTraceReplay` (no closure exception;
the replay completes with 2,091 errors, first error unchanged at f30582
`tails_y`). Every commit after `d2678cf45` is byte-identical to it.

### Stopped or deferred

- **C, SSZ1 cold routes.** All 16 `ssz1-*.script` routes share P1's inputs
  through input 1366 (the 800-px, solo and paired variants first differ at
  1379-3408), so every SSZ1 cold route meets the same frame. Base `36aaddb37`
  and the branch were captured side by side
  (`GameplayCaptureTool`, Sonic+Tails native320, 20,720 inputs). The
  base engine's swallowed jump at 1366 cost exactly one bridge carry (2 px);
  under the corrected order the jump costs two (the release frame and the
  landing frame), and dropping the press costs none, so P1 ends 2 px right or
  left of the base path for the rest of the bridge. No local input edit can
  reproduce one lost carry: every airborne episode on the bridge costs at least
  two frames, and Sonic's grounded acceleration and friction (`$C`) move him in
  multiples of 12 subpixels, so a 512-subpixel correction is unreachable before
  the spindash. The no-jump variant re-converges in the coarse capture state
  from 2648 to 3528 and then drifts (y at 3528, hidden phase from the offset
  camera history). Re-authoring therefore means re-authoring every SSZ1 route
  from 1366 to its end, boss fights included. Not done in this lane; the two
  smoke reds (`TestSszBossInputAuthorTool#authoredFatalHitReplaysFromColdWithIdenticalObservedState`,
  `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost`)
  remain. The whole-route captures (`TestSszColdRouteCapture`,
  `TestSszTailsColdRouteCapture`, `TestSszSoloColdRouteCapture`,
  `TestSszWidePaired*ColdRouteCapture`) are `exhaustive-audit` and share the
  frame; they were not run.
- **S2 ARZ boss pillar MANUAL conversion, rejected.** `Obj89_Pillar_Sub0` /
  `Sub2` call `Obj89_Pillar_SolidObject` before the body moves
  (s2.asm:65451-65497,65652-65661), and FixBugs=0 `Sub4` makes no call and
  leaves a rider's `Status_OnObj` set (65601-65640). Moving the call there
  (with or without deleting `preservesMovingSideContactVelocity`) turns
  `TestS2Arz2LevelSelectTraceReplay` red at f4707 (`x` `$2B4D`/`$2B4F`): the
  rising right pillar returns no contact. Cause: `SolidObject_OnScreenTest`
  (s2.asm:35336-35341) reads the bit the previous BuildSprites set with its
  approximate 32 px Y test (30603-30611); at y `$501` against camera y `$400`
  that test only passes with the ARZ screen-shake offset, and the engine's live
  on-screen proxy (`isWithinSolidContactBounds`) has none. The old AUTO call
  saw the post-raise y, one pixel higher, which masked it. Needs that gate
  fixed first ([known bugs](../../status/known-bugs.md)).
- **Not started:** MGZ top platform (its hooks fake a pre-move call and its
  state machine reacts to the result a frame late; the last
  `carriesRiderOnHorizontalMove` override stays until it is converted), CPZ
  staircase and ARZ arrow (`preservesRidingPushStatus`, step 5), MTZ / conveyor
  / elevator (`staleHorizontalLogicalInputFramesWhileRiding`), S3K breakable
  wall, CNZ trap door (`getTopSolidPlayerPositionHistoryFrames` covers a loose
  state-machine port of `loc_31CCA`, which always calls `SolidObjectTop` and
  then `Animate_Sprite`), AIZ transition floor and the other immediate
  checkpoints, ICZ snow pile, the balance check's frame-start reads
  (`terrainMoveDispatchOwnsDetachAnimation`), and the offscreen-sidekick skips
  (the P2 gate exists only in S2 `SolidObject` and S3K `SolidObjectFull`, which
  the engine's helper vocabulary cannot yet name).

### Validation

- Per step, focused `maven_queue.py --normal -Dmse=off` runs with the three ROM
  paths and surfaceless EGL: Obj53 unit tests and the S1 headless set (39-40
  tests), the LBZ boss classes (53 tests), the d4-conversion classes (945 tests;
  the only failure was the known SSZ1 route), the strip classes (178 tests),
  `TestSonic2ObjectBugFixes` (70 tests; one pre-existing order-dependent error,
  `mtzCogHighSpeedLeftwardReleaseStillSkipsFoldedSiblingSideStop`, which errors
  the same way in isolation at `64e49c0f5`) and `TestPlayableSpriteMovement`
  (188 tests).
- `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`: green at every
  step.
- `-Pguards`: 820 tests, 0 failures at `c7d6a81b1`, `b6669b030`, `64e49c0f5`,
  `84b48e6d8` and `f8214016b`.
- Change-based smoke at `ba98725c2`, run with
  `run_categories.py --base c81ffaf7c --workers 2 --run`, surfaceless EGL (run
  `20261101T022042Z-3b8a1c9d`, 4,366 classes in 88 batches): ordinary lane
  36,169 tests, 12 failures, 0 errors, 49 skips, 1,484 s; guards 820 tests,
  0 failures, 137 s. Eight failures are the pre-existing set
  (`TestMasterTitleScreenLayout` x2, `TestSonic2InterlacePixels`,
  `TestNativeCompetitionViews`, `TestNativeSpriteSatGameplay` x3,
  `TestPrivateLobbyScreenRendering`); two are the SSZ1 routes above; two were
  fixtures that only answered the routine-entry call
  (`TestSonic1SpikeObjectInstance#updateAppliesCheckpointContactToPlayersFromObjectPlayerQuery`,
  `TestSolidObjectManager#sozPushToFallDoesNotCarryThePreviousPushButTrackMotionCarries`),
  fixed in `1ea6b5874` and re-run (119 tests, 0 failures). The 49 skips are the
  same gated set as the step-3 smoke (system properties, captures, perf and
  native-GPU checks, and `TestCPZObjectBugs#testSpinTubeForcesRolling`'s
  runtime assumption).

## Continuation 3: on-screen gate, ARZ pillar, remaining families (2026-11-02)

Branch `bugfix/ai-rom-owned-solid-contact-3`, based on `feature/ai-weakness-pass`
at `fa313dfa3` (the merge of the first continuation). Same protocol and 143
classes; baseline and every measured commit ran in a separate copy-on-write
worktree on `/home` with `target/classes` and `target/test-classes` deleted
first. The baseline at `fa313dfa3` gives 79 green, 64 red, 215 tests, no skips.

### A. The on-screen gate reads the previous render pass

All three games' full-solid helpers gate side/top/bottom resolution on the
object's `render_flags.on_screen`: S1 `SolidObject` / `Solid_ChkEnter`
(`tst.b obRender`, _incObj/sub SolidObject.asm:112,153), S2
`SolidObject_OnScreenTest` (s2.asm:35336-35341) and S3K `loc_1DF88`
(sonic3k.asm:41435-41436). Only the sprite renderer writes that bit, at the end
of the frame: S1 `BuildSprites` against `v_screenposx/y`
(_inc/BuildSprites.asm:38-116), S2 `BuildSprites` against `Camera_X_pos_copy` /
`Camera_Y_pos_copy` (s2.asm:30560-30611), which the zone's scroll routine has
already moved by the screen-shake ripple (e.g. `SwScrl_ARZ`, s2.asm:17709-17728),
and S3K `Render_Sprites` against the same copies after ScreenEvents has added
`Screen_shake_offset` (sonic3k.asm:36358-36410). S1/S2 test Y with an assumed
32-px radius unless the object sets the explicit-height flag
(`BuildSprites_ApproxYCheck`, s2.asm:30603-30611; S1 `.assumeHeight`,
_inc/BuildSprites.asm:62,86-94); S3K always uses `height_pixels`
(sonic3k.asm:36396-36404).

The engine evaluated a live proxy at the solid call instead: the object's
current position (post-move for AUTO routines), the live camera without shake,
and a default 16-px Y extent. Now:

- `ObjectManager.refreshPostCameraRenderState` (the existing end-of-frame
  BuildSprites slot in `LevelFrameStep`) records every live object's bit with
  `AbstractObjectInstance.captureRenderFlagOnScreen` before the objects' own
  retained-flag refreshes. Its camera bounds are the copy words plus the
  published shake offsets, so the ~60 objects with their own retained render
  flags also see the shaken camera. The Y test follows
  `CameraRules.useScreenYWrapValueForVisibility`, the rule that already selects
  the playable sprite's render-flag Y test.
- `isWithinSolidContactBounds()` returns that bit (rewind-captured as two new
  `PerObjectRewindSnapshot` components). An object no pass has seen yet answers
  from its live render box, as the playable render flag does.
- Object routines that test their render box when they run keep that check
  under its own name, `isWithinOnScreenRenderBounds()` (22 classes renamed, no
  behaviour change).
- S2 objects that set `render_flags.explicit_height` and call `SolidObject` now
  declare it: Obj2A stomper (`y_radius` `$50`, s2.asm:24224-24225), Obj2F
  smashable ground (`$20`, s2.asm:49158-49159) and Obj64 twin stompers (`$6C` for
  mapping frame 0, plus its `width_pixels` from `Obj64_Properties`,
  s2.asm:52712-52718). FixBugs=0 Obj2B and Obj82 leave the flag clear
  (s2.asm:51821-51830, 57285-57290).

Deleted: the S3K spike and FBZ rotating-platform `isWithinSolidContactBounds`
overrides (pre-update-position approximations of the retained bit), Obj89's
fitted `getOnScreenHalfHeight` `$21` ("one-pixel lower-edge slack"), and an
unreachable catch in `LrzShootingTriggerObjectInstance.onScreen` with its
broad-catch baseline row. Residual approximations (display-queue membership,
multi-sprite boxes, objects registered mid-frame) are in
[known bugs](../../status/known-bugs.md#solid-on-screen-gate-render-pass-approximations--2026-11-02).

Measured at `a1a9b0619`: no green class turned red. Two reds changed:

- `TestS2CompleteEmeraldRunChain` keeps its failing segments, but segment 19
  (`seg13_arz2`) now first diverges at f4433 `sidekick_x` ROM `$2B5E` / engine
  `$2B4D` (was f5084 `sidekick_y`): the engine pushes Tails to the right
  pillar's left edge one frame before the ROM does. Instrumented: the ROM row
  f4434 shows the pillar's p2-pushing bit, so its bit was clear at the f4432
  pass (pillar y `$502`, ripple Y 2 -> y - copy = `$100`, outside the band) and
  set at f4433 (y `$501`, ripple 2 -> `$FF`). The engine's f4432 pass used
  ripple Y 3 because `SwScrl_ARZ` indexes `SwScrl_RippleData` with
  `Level_frame_counter` (s2.asm:17712), and the engine's model
  (`LevelManager.frameCounter`) is `$115F` on that row while the ROM's is
  `$1145`; it is already +26 at the pillar's spawn row f4419 (`$1152` vs
  `$1138`). The ROM clears the counter before the title card (s2.asm:4773) and
  increments it only in `Level_MainLoop` (s2.asm:5092). This is a pre-existing
  level-clock desync in the chain's ARZ2 segment that only rendering read until
  the gate consumed the shake; the standalone `TestS2Arz2LevelSelectTraceReplay`,
  where the clocks agree, stays green. Kept; recorded in
  [known bugs](../../status/known-bugs.md#s2-run-chain-arz2-level-clock-runs-26-frames-ahead--2026-11-02).
- `TestS3kSonicTailsHcz3SegmentTraceReplay`: 1,171 -> 1,172 errors after the
  unchanged f0 `rings` first error.

### B. ARZ boss pillar at its ROM call sites

`Obj89_Pillar_Sub0` and `Sub2` call `Obj89_Pillar_SolidObject` (d1 `$23`, d2
`$44`, d3 `$45`, d4 `x_pos`, y temporarily +4) at their head, before the pillar
rises or shakes (s2.asm:65451,65469,65652-65661). Shipped (FixBugs=0) `Sub4`
makes no solid call while the pillar sinks, and its `DeleteObject` leaves a
rider's `Status_OnObj` set (s2.asm:65601-65640). `54b9ebe7d` makes the pillar a
MANUAL routine with exactly those calls and drops its `dropStandingPlayers`,
which was the fixed branch. With A in place the rising right pillar resolves at
f4707 as the ROM does: `TestS2Arz2LevelSelectTraceReplay`,
`TestS2ArzLevelSelectTraceReplay` and `TestS2Arz1CompleteEmeraldsSegmentTraceReplay`
stay green, and the 143-class set is byte-identical to A.

Deleted: `SolidObjectProvider.preservesMovingSideContactVelocity` (its only
override kept CPU Tails' `x_vel` at the pillar's side, citing the AUTO call's
post-move timing) and its controller branch, the pillar's
`usesPreUpdatePositionForSolidContact`, `usesPreUpdateYForContinuedRide` and
`isSolidFor`, and four tests that asserted the deleted hook's values.

### C. Remaining families

| Commit | Family | Change |
| --- | --- | --- |
| `b0cfb12ef` | ARZ boss arrow | `Obj89_Arrow_Sub4` (s2.asm:65749) runs `Obj89_Arrow_Platform` (s2.asm:65779,65816-65836, FixBugs=0), which calls `PlatformObject` only while the decay timer is clear; a `p1_standing` result writes `#$1F` and falls through to the decrement in the same call; the countdown frames make no call. The arrow is MANUAL with that call and reads Sonic's checkpoint result. Deleted its `preservesRidingPushStatus`, `suppressSlopeSampleThisFrame`, `isSolidFor` and listener overrides and the `timerStartPending` / `timerExpiredThisFrame` pair. Byte-identical to B. |
| `c6f80dd7f` | MTZ / conveyor / elevator | `staleHorizontalLogicalInputFramesWhileRiding` let three S2 riding solids hide a newly pressed direction from Sonic for three frames (the MTZ form keyed on two MTZ3 x windows). The player slots already run before the objects and `Sonic_Move` reads `Ctrl_1_Held_Logical` directly (s2.asm:36559-36564). With the window disabled the five S2 MTZ/CNZ level-select traces stayed green; deleted both provider overloads, `preservesStaleHorizontalInputEdgeWhileMoving`, the three overrides, the movement tracker and its four `RewindState` components, and five hook-value tests. |
| `cc4b53a0a` | CNZ trap door | `loc_31CCA` (sonic3k.asm:67262-67293) checks both players against `sub_31CFA`, then always calls `SolidObjectTop` (d1 `$20`, d3 9), then `Animate_Sprite`. The engine port gated solidity on its open state, missed the trigger box's `$20` bound and read the player's previous-frame position for new landings (`getTopSolidPlayerPositionHistoryFrames`). The real rule behind that hook is the helper's landing band: `loc_1E45A` accepts only d0 in [-`$10`,-1] and returns on d0 = 0 (sonic3k.asm:42044-42053). At `TestS3kCnzTraceReplay` f9801 Sonic's bottom meets the door's top exactly and the ROM lands him at f9802. The door is MANUAL with the ROM call and the zero-distance rejection the other S3K top-solid ports already declare; the hook, its controller branch and its test object are deleted, and the controller test that asserted d0 = 0 landings now asserts the rejection. |
| `5a6ee4fb0` | Balance frame-start reads | The standing-still test after the ground move let an airborne-at-end frame keep the Wait/Balance write only when `Status_OnObj` was clear at this and the previous frame start (`95806f3a9`, MCZ2). `Obj01_MdNormal` (s2.asm:36469-36481) runs `Sonic_Move` (s2.asm:36551-36578), whose inertia test makes that write, before ObjectMove and AnglePos can set `Status_InAir`. With both reads removed, the S2 level-select and complete-emerald segments, the S1 complete runs and `TestS3kAizTraceReplay` were unchanged; the two `Status_OnObj` frame-start snapshots, their rewind components, their test class and the movement test that asserted the previous-frame veto are deleted. |

Rejected attempt for the trap door: the always-solid MANUAL call without the
zero-distance rejection moved `TestS3kCnzTraceReplay` from f17322 to f9801
(`y_speed` `$07A8`/0) and the CNZ zone slice from f14616 to f8327: the engine
landed Sonic one frame early. With the rejection all three CNZ traces return
to their exact baseline reports.

### Stopped or deferred

- **CPZ staircase (`preservesRidingPushStatus` stays).** Obj78 is a parent and
  three `AllocateObjectAfterCurrent` children, each running its own
  `SolidObject` and ORing the touch result into the parent's `objoff_2E`
  (s2.asm:56079-56146). The engine folds the four steps into one
  `MultiPieceSolidProvider` and only reserves the child slots, so there is no
  per-slot call to move; one checkpoint per ROM piece slot needs real child
  objects (rendering, rewind recreation, despawn with the parent). Not started.
- **MGZ top platform and the S3K breakable wall.** The platform (2,290 lines)
  carries its rider as an object-controlled player with a projected `x_vel`;
  `projectsPreMovementGroundXForSolidContact` only fires for such carriers
  (post-movement passes return early), which is why the breakable wall also
  overrides it. Both hooks and the last `carriesRiderOnHorizontalMove` override
  go with a MANUAL port of the platform's routine. Not started.
- **AIZ transition floor and the immediate checkpoints.** The floor's
  `rejectsZeroDistanceTopSolidLanding` counts 20 or 21 rejected passes by the
  rider's subpixel phase, a fitted window on the AIZ -> HCZ route, so it was not
  touched. The three `processImmediateInlineSolidCheckpoint` callers (cork
  floor, MGZ trigger platform, HCZ2 wall) re-run an object whose engine slot
  precedes the ROM's; their fix is slot allocation, not the solid call.
- **ICZ snow pile.** No solid hook; its own retained `render_flags` could now use
  the base render pass from A. Not changed.
- **Offscreen-sidekick skips: no change needed.** The controller's P2 gate
  (`shouldSkipOffscreenSidekickFullSolid`) already applies exactly where the
  object gate applies: S2 `SolidObject` (s2.asm:35014,35023-35024) and S3K
  `SolidObjectFull` (sonic3k.asm:41045,41051-41052); `SolidObject_Always`,
  `SolidObject45`, `SolidObjectFull2`, top-only and sloped helpers have neither.
  No other offscreen-sidekick skip remains.
- **Follow-up found on the way: the zero-distance landing rule.** S2
  `PlatformObject_ChkYRange` (s2.asm:35968-35977) and S3K `loc_1E45A`
  (sonic3k.asm:42044-42053) both return on d0 = 0, but the engine accepts it
  unless an object opts into `rejectsZeroDistanceTopSolidLanding` (about 35 do).
  The CNZ trap door's history hook and the AIZ transition floor's counted
  rejects both trace back to that default. Making the rejection the top-solid
  helpers' rule and deleting the opt-ins is the next pruning step; it changes
  every top-solid object that has not opted in, so it needs its own measurement.

### Transitions per measured commit (each against `fa313dfa3`)

| Measured commit | green->green | green->red | red->red identical | red->red changed | red->green |
| --- | --- | --- | --- | --- | --- |
| `a1a9b0619` (A, on-screen gate) | 79 | 0 | 62 | 2 | 0 |
| `54b9ebe7d` (B, ARZ pillar) | 79 | 0 | 62 | 2 | 0 |
| `b0cfb12ef` (ARZ arrow) | 79 | 0 | 62 | 2 | 0 |
| `c6f80dd7f` (stale riding input) | 79 | 0 | 62 | 2 | 0 |
| `cc4b53a0a` (CNZ trap door) | 79 | 0 | 62 | 2 | 0 |
| `5a6ee4fb0` (balance reads) | 79 | 0 | 62 | 2 | 0 |

The two changed reds are the ones A introduced (S2 chain segment 19, S3K
Sonic+Tails HCZ3 +1 error); every commit after A is byte-identical to A.
215 tests per measurement, no skips. Maven execution per measurement (A / A1 /
B): 7m09s-8m19s / 1m54s-2m01s / 4m27s-4m39s; the baseline took 7m17s / 1m55s /
4m31s. Queue waits came on top (shared queue).

### Size

Against `fa313dfa3`: `ObjectSolidContactController` 4,520 -> 4,502 lines;
`SolidObjectProvider` 843 -> 784; `PlayableSpriteMovement` 6,037 -> 5,939.
Main sources: 40 files, +309 / -582; the six code commits including tests:
62 files, +706 / -1,010. Deleted hooks:
`preservesMovingSideContactVelocity`, `getTopSolidPlayerPositionHistoryFrames`,
`staleHorizontalLogicalInputFramesWhileRiding` (both overloads),
`preservesStaleHorizontalInputEdgeWhileMoving`, and the
`getOnObjectAtFrameStart` / `getOnObjectAtPreviousFrameStart` snapshots; the
fitted overrides removed with them are listed per item above. Mod API candidate
pin regenerated at every code commit (net -13 lines).

### Validation

- Per commit, focused `maven_queue.py --normal -Dmse=off` runs with the three
  ROM paths and surfaceless EGL: A, 1,386 tests over every class that calls or
  overrides the gate or the render refresh plus the keep-green four,
  `TestSonic2ObjectBugFixes` and `TestCPZObjectBugs` (fixture fixes in five
  classes, then 99 re-run tests green); B, the pillar's new
  `TestArzBossPillarSolidCalls` (fails on the old AUTO routine) with
  `TestSonic2ObjectBugFixes` and the keep-green four (215 tests; the one error is
  the known order-dependent `mtzCogHighSpeedLeftwardReleaseStillSkipsFoldedSiblingSideStop`);
  arrow, 610 tests; stale input and trap door, 1,000 tests then 83 after the
  trap-door test corrections; balance, 561 tests then 187
  (`TestPlayableSpriteMovement`) after the last test deletion. The one skip is
  `TestGroundedDetachNativeProfiles`' KiS2 lock-on ROM assumption.
- Trace spot checks before each commit: the three S2 ARZ classes (A, B, arrow),
  the five S2 MTZ/CNZ level-select traces (stale input), the three S3K CNZ
  traces (trap door), and 61 S1/S2/S3K-AIZ trace tests (balance).
- `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`: green at A, B, the
  stale-input/trap-door tree and C4.
- `-Pguards`: at A, 820 tests with one failure (`TestBroadCatchSwallowingGuard`:
  the LRZ trigger's unreachable catch changed hash); the catch and its row were
  retired and that class re-run green. At B, one failure
  (`TestObjectPhysicsStandardizationGuard`: the pillar's `updatePillarLower`
  signature changed); the baseline row was renamed and the class re-run green.
  Full runs on the stale-input + trap-door tree and on C4: 820 tests,
  0 failures.
- No broad `run_categories.py` smoke was run in this lane (the lead runs one
  combined smoke).
