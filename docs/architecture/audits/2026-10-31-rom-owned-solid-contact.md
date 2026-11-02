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

### C follow-up: native settlement and bridge walk-off (2026-11-02)

Branch `bugfix/ai-ssz1-native-routes`, based on `feature/ai-weakness-pass` at
`fa313dfa3`.

- **Native settles 1366.** A composite movie keeps the complete-run rows
  `[0,396719)` and appends `ssz1-sonic-tails-cold-complete-320`'s first 2000 rows,
  so route input k is movie row 396719 + k. TraceChaser's headless GPGX harness
  captured it with `--trace-profile complete_run` (the SSZ segment is labelled
  `hpz`, `zone_id` 10). Native follows `fa313dfa3` exactly for P1 and P2 through
  input 1955 and leaves base `36aaddb37` at 1366, where base re-landed P1 on the
  bridge in the same call. The corrected order is native behaviour. Alignment
  rests on exact agreement from input 1; a prefix one row late (396720) fakes an
  early divergence near input 286.
- **Bridge standing-bit lag fixed (`f02d2deaf`).** With physics equal, the
  bridge's standing bits still lagged. `SolidObjectTop_1P`'s `loc_1E2E0`
  (sonic3k.asm:41852-41857) clears the walking-off player's d6 bit in the same
  call; the engine left it for the next stale-rider pass (P1 1416 -> 1417,
  P2 1430 -> 1431). `Obj_SSZCutsceneBridge` now opts into
  `clearsStandingBitOnContinuedRideExit`, like `Obj_CollapsingBridge` and the DEZ
  lift pad. Other `SolidObjectTop` users without the opt-in keep the one-frame
  lag; they were not measured here.
- **Native fixture.** The capture is committed as
  `traces/s3k/ssz1_cold_route_native` with
  `_movies/s3k-ssz1-cold-route-composite.bk2`, registered in
  `hardware-timing-publication.tsv`. `TestS3kSsz1ColdRouteNativeTrace` compares
  the cold engine with native on inputs 1-1956 (the first native lag frame is
  1957). The composite is regenerated by
  `tools/traces/compose_route_on_movie_prefix.py`.
- **Route re-authoring deferred.** Re-authoring the SSZ1 cold routes from 1366
  moves to the route-planner design programme. The two smoke reds
  (`TestSszBossInputAuthorTool#authoredFatalHitReplaysFromColdWithIdenticalObservedState`,
  `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost`)
  remain open and expected, as do the `exhaustive-audit` SSZ route captures. The
  LRZ->HPZ->SSZ1 routes (`lrz-*-hpz-ssz1*`) reach the same arrival and bridge and
  belong in that re-authoring set.
