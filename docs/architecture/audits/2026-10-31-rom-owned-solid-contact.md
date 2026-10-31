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

Size: `ObjectSolidContactController` 5,702 → 4,553 lines (−1,149). Whole
branch: 142 files, +935 / −2,957 (main sources +496 / −2,164).

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
