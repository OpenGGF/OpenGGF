# Sidekick CPU fitted-logic removal (2026-10-31)

Branch `bugfix/ai-sidekick-rom-cpu`, based on `feature/ai-weakness-pass` at
`1f37638cc`. Code commit `e97436afa`.

The user asked for the trace-fitted sidekick logic to go. `SidekickCpuController`
had grown to 6,190 lines; its ground-follow routine (`updateNormal`) was about
880 lines against roughly 120 lines of 68000 code in the ROM routines it
claims to implement. This audit classifies every behaviour in the controller,
records where each fitted piece came from, and records what its removal does
to the sidekick trace set.

Disassembly citations are at the project pins (`s2disasm` `34fc7f130a96`,
`skdisasm` `8b2cf3c4178f`).

## Method

1. Read the pinned ROM routines in full: S2 `TailsCPU_Control` and its states
   (s2.asm:39071-39515: `TailsCPU_Init`, `_Spawning`/`_Respawn`, `_Flying`,
   `_Normal`, `_Despawn`, `_CheckDespawn`/`_UpdateObjInteract`, `_Panic`) and
   S3K `Tails_CPU_Control` with `Tails_CPU_Control_Index`
   (sonic3k.asm:26394-26426) and routines `loc_13A10`, `Tails_Catch_Up_Flying`,
   `Tails_FlySwim_Unknown`, `loc_13D4A`/`loc_13D78`, `sub_13ECA`, `sub_13EFC`,
   `loc_13F40`, `locret_13FC0` and the carry routines `loc_13FC2`/`loc_13FFA`/
   `loc_1408A`/`loc_140C6`+.
2. Read the whole controller and classify each behaviour:
   **(a)** direct translation of a named routine or branch, or the documented
   [multi-sidekick extension](../../status/known-discrepancies.md#multi-sidekick-daisy-chain);
   **(e)** engine plumbing with no gameplay choice of its own (history-ring
   indexing, comparison-only diagnostics, rewind capture);
   **(b)** fitted: no ROM counterpart, keyed on traces/frames/fixtures,
   compensating for engine ordering, or tuned thresholds.
3. `git log -S<symbol> --reverse -- src/main/java` for each (b) piece to find
   the commit that introduced it.
4. Delete all (b). No replacement heuristics. Measure the sidekick trace set
   before and after with the same commands.

## The ROM routine the follow state now implements

S2 `TailsCPU_Normal` (s2.asm:39260-39391) and S3K `loc_13D4A`/`loc_13D78`
(sonic3k.asm:26696-26838) do, in order:

1. Leader routine >= 6 (dead): enter the flight routine (S2 `TailsCPU_Flying`
   via `TailsRespawnStrategy.beginDeadLeaderFlight`; S3K routine 4).
2. Despawn check (`TailsCPU_CheckDespawn` / `sub_13EFC`).
3. Manual-control timer nonzero: decrement and return.
4. `object_control` bit 7: return. S3K also returns on leader
   `status_tertiary` bit 7.
5. `move_lock` with zero inertia: set routine 8 (panic) and continue.
6. Read the leader's `Pos_table`/`Stat_table` entry at index - `$44`:
   x, y, `Ctrl_1_logical` word (d1) and status byte (d4). S3K subtracts `$20`
   from x unless the leader has `Status_OnObj` or `ground_vel >= $400`.
7. Tails `Status_Push` set and d4 `Status_Push` clear: jump straight to the
   auto-jump cadence gate.
8. Otherwise steer: at `|dx| >=` `$10` (S2) / `$30` (S3K) replace d1's
   left/right bits; nudge `x_pos` by one pixel toward the target when moving
   and facing that way (S3K skips this under `object_control` bit 0); at
   `dx = 0` copy d4's facing bit.
9. Auto-jump flag: OR A/B/C into d1's held byte; airborne send now, grounded
   clear the flag.
10. Distance gate (`|dx| < $40` unless `(Level_frame_counter & $FF) == 0`),
    height gate (target at least `$20` above).
11. Cadence gate (`(Level_frame_counter+1) & $3F == 0`, not ducking): press and
    hold A/B/C, set the flag.
12. `move.w d1,(Ctrl_2_logical).w`.

`updateNormal` is now these steps, 247 lines including citations.

## Inventory

### (a) Retained ROM translations

| Controller behaviour | ROM routine |
| --- | --- |
| P2 input sets 600-frame manual timer; routine dispatch | `TailsCPU_Control` (s2.asm:39071-39081), `Tails_CPU_Control` (sonic3k.asm:26394-26405) |
| Adopting an externally killed sidekick into the dead routine | `Kill_Character` → `Obj02_Dead` / `loc_1578E` |
| S3K drowning installs routine `$C`; no CPU step | `Tails_Index` (sonic3k.asm:26125-26138) |
| Negative `Ctrl_2_locked` skips the CPU; ending-pose queue | `Tails_Control` (sonic3k.asm:26236-26245), `Check_TailsEndPose` |
| INIT: CNZ carry, provider-owned dormant marker, routine-0 return | `loc_13A10` (sonic3k.asm:26429-26511), `TailsCPU_Init` (s2.asm:39094-39103) |
| Level-start placement at Player_1 - `$20`, +4 and Pos_table prefill | `InitPlayers` (s2.asm:5191-5195), `loc_6AB8` in `SpawnLevelMainSprites_SpawnPlayers` (sonic3k.asm:8395-8399), `Obj01_Init` |
| SPAWNING | `TailsCPU_Spawning`/`TailsCPU_Respawn` (s2.asm:39109-39140) |
| APPROACHING (Tails strategy) | `TailsCPU_Flying` (s2.asm:39142-39258) |
| NORMAL | as listed above |
| PANIC | `TailsCPU_Panic` (s2.asm:39458-39507), `loc_13F40` (sonic3k.asm:26891-26937) |
| CATCH_UP_FLIGHT | `Tails_Catch_Up_Flying` (sonic3k.asm:26514-26571) |
| FLIGHT_AUTO_RECOVERY | `Tails_FlySwim_Unknown` (sonic3k.asm:26574-26693) |
| DORMANT_MARKER | `locret_13FC0` |
| CARRY_INIT / CARRYING / CARRY_FLYOFF / MGZ_RESCUE_WAIT and MGZ carry | `loc_13FC2`, `loc_13FFA`, `loc_1408A`, `loc_140C6`, `loc_140CE`-`loc_14286`, `Tails_Carry_Sonic` |
| Despawn check, interact-slot id snapshot, S3K interact-word compare | `TailsCPU_CheckDespawn`/`TailsCPU_UpdateObjInteract` (s2.asm:39409-39451), `sub_13EFC` (sonic3k.asm:26856-26886) |
| Despawn markers | `TailsCPU_Despawn` (s2.asm:39397-39407), `sub_13ECA` (sonic3k.asm:26840-26849) |
| Boundary kill and deferred dead fall | `Kill_Character` (sonic3k.asm:21172-21195), `Obj02_Dead`, `sub_123C2` |
| Level-event marker releases | level-event writes of `Tails_CPU_routine` (2 or 6) |

`snapshotSeedInteractSlotId` was listed as suspect. It is a correct model of
`TailsCPU_UpdateObjInteract`: the engine's single "empty slot" value covers two
ROM states (never-ridden slot 0 = `ObjID_Sonic` `$01`; freed slot = `$00`). It
was a byte-for-byte duplicate of `romEffectiveInteractSlotId` and is merged into
it; the behaviour is kept. Its comments cited trace frames (mtz1 f375, htz2 f795,
mtz3 f2638); they now cite `DeleteObject` (s2.asm:30330-30340) instead.

### Multi-sidekick extension (retained)

`resolveActiveFollowLeader`, `getEffectiveLeader`, `isSettled`
(`SETTLED_FRAME_THRESHOLD`), `resolveRespawnStartTarget` with
`MULTI_SIDEKICK_RESPAWN_*`, approach completion on crossing the main leader,
and the Sonic/Knuckles respawn strategies, as documented in
[known discrepancies](../../status/known-discrepancies.md#multi-sidekick-daisy-chain).

### (e) Engine plumbing (retained)

- `ROM_FOLLOW_DELAY_FRAMES = 16`: the engine history index points at the latest
  written slot, ROM's at the next free one, so ROM's index - `$44` is 16 engine
  entries back.
- Directional press bits from the held-edge of adjacent history samples; jump
  press from the recorded press history. Ctrl_1_logical's pressed byte is the
  held edge, so this is a representation change, not a choice.
- Leader `Status_OnObj` is read from the frame-start snapshot. Neither game's
  `Sonic_Jump` clears it (s2.asm:37374-37375, sonic3k.asm:23363-23364); only
  objects that run after both player slots write it, so the value before this
  frame's object pass is the ROM-visible value at Tails' slot. The engine
  clears it inline during the leader's update, so the live value would be
  wrong.
- `objectControlAllowsCpu` carries the bit 0-6 versus bit 7 distinction of
  `object_control`.
- CPU frame-counter source (sprite cadence argument or stored level counter).
- Comparison-only diagnostics (`NormalStepDiagnostics`, Ctrl_2 latches,
  `cpu_state` accessors) and rewind capture.
- `NativeCompanionControlSource` leased input (mutator/encounter feature).

### (b) Fitted logic, deleted

| Piece | What it did | Introduced | Why it goes |
| --- | --- | --- | --- |
| `normalPushingGraceFrames`, `PUSH_STATUS_GRACE_FRAMES` (16) | Kept a "push" alive 16 frames after the bit cleared | `c18af2f23` Fix S3K trace replay sidekick and object parity | ROM tests the current `Status_Push` bit only |
| `OBJECT_ORDER_INPUT_DELAY_FRAMES` (17), `pushBypassStatus` | Read the leader's input/status one sample older in "object-order" contexts | `e99a47564` | ROM reads d1 and d4 from the same entry |
| `RIDING_OBJECT_PUSH_BRIDGE_MIN_GRACE`, `ridingObjectPushGrace`, `preservesSidekickCpuPushGraceWhileRiding`, `sidekickCpuPushGrace{Min,Max}FramesWhileRiding` | Per-object grace windows while riding (S2 Obj36, Obj6A, Obj78) | `5df7253f5` S2 spike riding push grace; `2b5d1ea37` OOZ Obj36 | Tuned frame windows per fixture |
| `interactObjectPushGrace`, `preservesSidekickCpuPushGraceFromInteractSlot`, `preservesMovingSidekickCpuPushAtZeroGraceFromInteractSlot`, `publishesSidekickCpuPushFromInteractSlot`, `preservesSidekickDelayedLeaderPushFromInteractSlot`, `sidekickCpuPushGrace{Min,Max}FramesFromInteractSlot` | Push bridges through the interact slot (S2 Obj30, Egg Prison, ARZ arrow), including publishing a synthetic push bit | `c8cb0966b`, `81825efbe`, `8b53a74f8`, `3eb29ca86` (HTZ2/ARZ2 frontier commits) | Named for trace frontiers; synthetic status writes |
| `preservesSidekickCpuPushGraceAfterRideClears`, `releasedObjectAutoJumpGrace` | ARZ pillar side-edge window for the auto-jump bypass | `51e3a8156`, `6520b75b3` | Fitted to ARZ2 frames |
| `usesSidekickCpuCurrentPushObjectOrderInputDelay`, `usesSidekickCpuPushBypassObjectOrderStatusDelay`, `preservesSidekickDelayedLeaderPushWhileRiding` | Swapped the delayed sample per object (HTZ2 Obj30, CPZ2 Obj78) | `3440186ff`, `988f4e2de`, `7ffbc7c87` | Per-frontier sample selection |
| `sidekickCpuStalePushGraceKeepsFollowSteeringWhileRiding` | ICZ platform exception to the grace | `a2e0f5d7b` | Exception to a fitted rule |
| `isObjectOrderFollowSteeringContext`, `isObjectOrderNudgeSteeringContext`, `isDoorSupportGraceFollowSteeringContext`, `Sonic3kSidekickFollowContext` | AIZ hollow tree / vine / collapsing platform / door proximity boxes (`0x80`/`0x100` radii) gating the bridges | `e99a47564`; hollow-tree origin `a6c4bdf86`, `7470d4656` | Zone/object proximity carve-outs |
| `OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE`, `LOCAL_BELOW_TARGET_REBOUND_NUDGE_MIN_GSPEED`, `FAST_LEADER_NO_LIVE_OBJECT_*` (4 constants), `suppressLocalGraceFollowNudge`, `fastLeaderNoLiveObjectNudge`, `smallDxDelayedInputNudge`, `freshBelowTargetReboundGrace` | Stacked gates deciding when to suppress the ±1 nudge | `aceffe45c` fix: align s3k aiz sidekick follow grace | Tuned thresholds; ROM nudges whenever moving and facing the target |
| `suppressFastLeaderTinyFollowNudge`, `CollisionRules.sidekickSuppressesFastLeaderTinyFollowNudge` | Spring/wall nudge suppression | `c15dcbe0d` | No ROM counterpart |
| `PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y`, `isCurrentPushBypassContext` | Restricted the push bypass to a `0x80` vertical band | `fa8a7287d`, `0e04c3ff5` | ROM bypass has no distance test |
| `resolveFollowSteeringDx`, `resolveFollowNudgeDx`, `resolveObjectOrderNudgeDx` | Substituted adjacent history samples for dx | `a6c4bdf86`, `7470d4656` (AIZ hollow tree) | ROM uses the one delayed sample |
| `pendingGroundedFollowNudge*`, `consumePendingGroundedFollowNudge`, CNZ `applyP2CpuNudgeBeforeFirstCapture` | Queued a nudge for the CNZ cylinder to apply later | `e87edb496` | ROM applies the nudge immediately or not at all |
| `frameStartPushBypass`, `UNDERWATER_PUSH_PULSE_MIN_X_SPEED`, `isUnderwaterCurrentPushPulse` | Alternative push sources and an underwater speed heuristic | `49733fa76` stabilize sidekick release gates | Two push sources; speed is not the status bit |
| `releasedUnderwaterPushConsumed`, `clearStaleUnderwaterPushBeforeNormalCpu`, `clearReleasedUnderwaterPushAfterCpu` and helpers | Pre-/post-cleared the push bit underwater after an object release | `56644e0fd` | Synthetic status writes keyed on AIZ2 reload |
| `rollingNonzeroGroundSpeedStalePush`, `restrictUnderwaterPushBypassToContactPulses`, `CollisionRules.sidekickPushBypassUsesGraceStatus` | Ignored a live push bit while rolling with speed | `965f368ba`; rule `5d689aeb2` | Live bit is what ROM reads |
| `isPushFromGroundWallCollision` provenance (`GroundWallResponseState`) | Marked terrain pushes so the heuristics above would spare them | `86123846c` | Existed only to exempt the heuristics |
| `objectOrderGracePushBypassThisFrame`, `usedObjectOrderGracePushBypassThisFrame`, `PlayableSpriteMovement.clearStaleCpuPushVelocityBeforeGroundMove`, `CollisionRules.sidekickClearsStalePushVelocityBeforeGroundMove` | Zeroed velocity after a grace bypass | `c18af2f23`, `e99a47564` | Compensation for the bridges |
| `suppressNextAirbornePushFollowSteering` | One-frame steering skip after push jump (already a no-op) | `91776363c` | Fitted, then neutralised |
| Leader sliding `getPreZoneFeatureGSpeed`, `capturePreZoneFeatureSnapshot` | Read the leader's pre-zone-feature speed while sliding | `7a3f3714d` ICZ sliding follow threshold | Engine-ordering compensation |
| Obj85 preserved-roll stale jump suppression | Cleared delayed jump under a CNZ roll-stop | `47f3969e5` | No ROM counterpart in the CPU routine |
| `jumpingFlag && air && grace <= 2` press clear, `preservesRecordedJumpPress` | Removed the delayed press byte in one case | `68627b035` | ROM keeps d1's low byte |
| `lastNormalAutoJumpPressFrameCounter`, `freshAutoJumpFrame` | Suppressed a second cadence jump at a repeated counter value | `e99a47564` | Compensation for a held counter |
| `titleCardOwnsActiveRetainedResultsSpriteCadence`, `projectRetainedResultsSpriteCadence`, `TitleCardProvider.projectsRetainedResultsSpriteCadence` | Projected the jump/catch-up counter onto the Pos_table index phase | `95013f1d2` CNZ retained RNG cadence | The owner of the held counter is the level-frame loop, not the CPU |
| `catchUpFrameCounterOverride`, `catchUpUsesRomVisibleLevelFrameCounter`, `usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge`, `levelEventDormantMarkerReleasePending` | `+1` and override on the catch-up counter after marker releases | `cb0fd7d51`, `e99a47564` | Counter fudges |
| `nextCpuFrameCounterOverride`, `cpuFrameCounterFromStoredLevelFrame` | Dead (never set / never read) | `cb0fd7d51`, `c27723dee` | Dead |
| `suppressNextLevelEventNormalMovement`, `skipPhysicsThisFrame`, `consumeSkipPhysicsThisFrame` | Skipped one physics frame after a dormant-marker release | `e99a47564` | No ROM counterpart |
| `enteredFromSeedCompareFrame0`, `isEstablishedFollowerEntry` (`ESTABLISHED_FOLLOWER_ENTRY_BAND` 0x40), `isDormantMarkerSentinelEntry`, `establishFollowerWithoutSpawnReset` | INIT branches selected by the trace-bootstrap seed flag (no production caller remained) | `190f7bb0d` | Keyed on trace bootstrap state |
| `clearStaleDeadOnObjectAfterVisibleWindow` (`0xB8` window) | Cleared a dead Tails' on-object bit after re-entering and leaving the screen | `0e63bb546` | No ROM citation for the mechanism |
| Fresh render-entry despawn delay, `ObjectInteractionRules.sidekickNormalDespawnDelaysFreshRenderEntry` | Treated a just-visible sidekick as off-screen | `f7b36afbd` OOZ2 respawn frontier | Disabled in every rule set; dead |
| `SidekickCpuRules.sidekickDelayedJumpPressUsesHistoryEdge` | Alternative press reconstruction | `2f5bf5013` | Disabled in every rule set; dead |

Count: 31 rows of fitted behaviour (about 75 named members, 14
`SolidObjectProvider` hooks, 4 `LevelEventProvider` hooks, 1 `TitleCardProvider`
hook, 5 rule-record components, 18 rewind-record components).

### Fitted code outside the controller, not removed here

These are object/collision-side compensations for the same engine-ordering
gap. They do not live in the CPU routine and are recorded for follow-up:

- `ARZBossPillar.preservesMovingSideContactVelocity` reads the CPU diagnostic
  `appliedFollowNudge` to make a gameplay decision.
- `SolidObjectProvider.preservesRidingPushStatus` overrides (CPZ staircase and
  others) whose tests cite trace frames.
- `GroundWallResponseState.preControlGSpeed`, used by `CollisionSystem` for the
  sidekick wall probe.
- `ObjectSolidContactController.shouldSkipRidingAirUnseatForOffscreenSidekick`
  and `shouldSkipOffscreenSidekickFullSolid`.

## Root cause the bridges compensated for

Almost every deleted piece patches the same difference: ROM runs solid objects
after both player slots, so Tails' CPU reads `Status_Push`/`Status_OnObj` as the
previous frame's object pass left them. The engine resolves solid contacts
inline during each player's movement, so push and on-object bits change at
different points in the frame. Moving solid-object processing to the ROM slot
order is an engine-wide change and was not attempted here; traces that depended
on the bridges are left red.

### S2 OOZ dead-Tails `Status_OnObj` (investigated, not fixed)

`TestS2OozLevelSelectTraceReplay` goes red at frame 447, `tails_status_byte`
`$02` expected, `$0A` actual — the deleted `clearStaleDeadOnObjectAfterVisibleWindow`
heuristic's own commit (`0e63bb546`) recorded this exact frontier before adding
it. The recorded rows show Tails dying at frame 395 while standing on the OOZ oil
(`Obj07`, slot `$0E`) and keeping `Status_OnObj` until frame 447 while still in
routine 6. The oil clears only its own standing bit before calling the kill
routine (`Obj07_SuffocateCharacter`, s2.asm:50205-50211), and `KillCharacter` leaves
`Status_OnObj` set, so the bit is cleared by a later writer. It is not the oil's
`PlatformObject_SingleCharacter`: the standing bit is already clear, and the
landing path refuses a dead rider (`cmpi.b #6,routine(a1)`, s2.asm:35980-35981)
and is still above the surface at frame 447. It is not `Obj02_Dead`
(s2.asm:41131-41137). The candidate is another object's `SolidObject` release
(`loc_1975A`, s2.asm:35041-35046), which only runs for Player 2 while Tails'
`render_flags.on_screen` is set (s2.asm:35023-35025). Identifying the writer
needs a native write-watch on `Sidekick+status` at frame 447; the replacement
must come from that owner, not from a screen-window rule in the CPU controller.

## Deleted tests

Tests that asserted only the removed behaviour:

- `TestSidekickCpuFollowParity`: 65 tests (109 → 44 annotated; grace, bridge,
  object-order, fast-leader, underwater pulse/pre-clear, sliding leader,
  level-event suppression, seed-entry, history-edge and Obj30/Egg Prison
  interact tests) and the helpers `sonic3kWithSidekickContext`,
  `setNormalPushingGraceFrames`, `setReleasedUnderwaterPushConsumed` and
  `withDelayedJumpPressHistoryEdge`. Two tests that test
  ROM behaviour kept their assertions and lost only the grace setup:
  `s3kFollowSteeringReadsCurrentPushBit` (was `...RatherThanAgedLocalGrace`)
  and `s3kClearedPushFallsThroughToFollowLeftNudge` (was
  `s3kLateTerrainPushGraceFallsThroughToFollowLeft`).
- `TestSidekickCpuControllerLevelStart` (16 → 6): nine S2 Obj36 "OOZ" riding-grace tests,
  `deadSidekickClearsStaleOnObjectAfterLeavingVisibleWindowAgain` and the
  `DummyRidingObject` stub.
- `TestSidekickCpuDespawnParity.s3kEstablishedFollowerHandoffPreservesDiagnosticInteractWord`.
- `TestSonic3kSidekickFollowContext` (whole class, 2 tests).
- `TestSonic2ObjectBugFixes`: `arzBossArrowReleasedRideKeepsTailsCpuAutoJumpGateVisible`,
  `htzRisingLavaSubtypeSixUsesCpuSidekickObjectOrderInputDelay`,
  `s2SpikesDoNotClaimPushGraceFromUnrelatedSolid`; CPU-grace assertions removed
  from the ARZ pillar, MCZ platform and CPZ staircase tests.
- `TestSonic3kRetainedTitleCounterPhase.retainedOwnerKeepsHistoryProjectionAcrossGamestateReset`.
- Assertions on removed rule components in `TestKis2PhysicsProvider`,
  `TestCrossGameFeatureProviderRefactor` and `TestS3kIczSwingingPlatformObject`.

## Validation

Focused run on the change (`python3 tools/testing/maven_queue.py --normal
-Dmse=off` with the three ROM paths, surfaceless EGL, 48 classes including the
S3K keep-green set): **864 tests, 0 failures, 0 errors, 0 skips**.
`TestS3kAiz1SkipHeadless` 8/8, `TestSonic3kLevelLoading` 36+7,
`TestSonic3kBootstrapResolver` 6, `TestSonic3kDecodingUtils` 3.

Mod API candidate pin regenerated in place: 87 lines removed, 7 constructor
lines changed, nothing added. `disasm_citations.py check-staged` passes; the
controller's stale citations fell from 11 to 8 (all 8 pre-existing).

## Trace impact

Set: every S2 and S3K Sonic+Tails trace class (CPU Tails present) in the two
profiles — invocation **A** is `-Ptrace-replay` with an explicit class list
(S2 level-select, complete-emerald segments, EHZ1 and its regressions, the S2
run chains and halfpipe round trip, S3K AIZ/CNZ/MGZ, the S3K Sonic+Tails run
chain and prefix, and the SOZ/FBZ complete-run slices); invocation **B** is the
whole `-Ptrace-segments` profile (S3K Sonic+Tails segments and zone slices).
Knuckles-only, Tails-only, S1 and special-stage-only classes have no CPU sidekick
and were not run. Exact commands are in the
[frontier log entry](../../status/trace-frontier-log.md) for 2026-10-31.

Baseline: commit `1f37638cc` in a separate copy-on-write worktree. After:
`e97436afa`. Same flags, ROMs, Java 25, surfaceless EGL, alphabetical run order,
one reused fork. A: 111 tests both sides (before 8 failures / 1 error, after
9 / 1); B: 70 tests both sides (48 failures / 8 errors). No skips on either side.
Execution 7m17s / 4m37s before, 7m45s / 4m38s after; queue waits under 90 s.

| Transition | Classes |
| --- | --- |
| green → green | 48 |
| green → red | 1 (`TestS2OozLevelSelectTraceReplay`, f447 `tails_status_byte` `$02`/`$0A`) |
| red → red, identical report | 60 |
| red → red, same first error, fewer total errors | 2 (S3K Sonic+Tails CNZ segment 9069 → 9002; LRZ segment 8897 → 8870) |
| red → green | 0 |

"Identical" compares the full failure messages of every failing test with
worktree paths normalised. `TestS3kAizTraceReplay` (the AIZ → HCZ route) stays
green. The S2 greens are real sidekick comparisons: the OOZ failure is on a
Tails field, and every S2 single-trace report covers thousands of frames
(HTZ2 10,166, MTZ3 15,705, WFZ 16,427).

Most deleted bridges were therefore no longer load-bearing: the traces they were
written for (HTZ2 Obj30, CPZ2 Obj78, ARZ2 Obj89, OOZ Obj36, MCZ2, AIZ hollow
tree, CNZ cylinder, ICZ, MGZ) are green, or red at an unchanged earlier frontier,
without them.

| Inv | Class | Tests b/a | Before (first failure) | After | Transition |
| --- | --- | --- | --- | --- | --- |
| A | `TestS2Arz1CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Arz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2ArzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Cnz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2CnzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2CompleteEmeraldRunChain` | 1/1 | 14 axes: chain replay of s2-sonic-tails-complete-emeralds failed on 14 axis/axes: - [walk-failure] java.lang.AssertionE | same | red→red |
| A | `TestS2CompleteEmeraldRunPrefix` | 1/1 | pass | pass | green→green |
| A | `TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Cpz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Cpz2Seg9CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2CpzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2DezEndingLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ehz1BridgeTailsStandingRegression` | 8/8 | pass | pass | green→green |
| A | `TestS2Ehz1BuzzerSpawnRegression` | 5/5 | pass | pass | green→green |
| A | `TestS2Ehz1MonitorBreakRegression` | 4/4 | pass | pass | green→green |
| A | `TestS2Ehz1Seg1CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ehz1Seg2CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ehz1Seg2HalfpipeSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ehz1TraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ehz2Seg6CompleteEmeraldsSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2EhzHalfpipeRoundTripChain` | 2/2 | 2 axes: chain replay of s2-ehz-halfpipe-roundtrip failed on 2 axis/axes: - [dynamic-art-gap] shared dynamic-art gap co | same | red→red |
| A | `TestS2Htz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2HtzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2HtzLiftPlatformSurfaceRegression` | 5/5 | pass | pass | green→green |
| A | `TestS2Mcz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2MczLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Mtz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Mtz3LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2MtzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2Ooz2LevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2OozLevelSelectTraceReplay` | 1/1 | pass | 1 err; first f447 tails_status_byte 0x0002/0x000A | **green→red** |
| A | `TestS2ReplayBootstrapTailsFrame0` | 7/7 | pass | pass | green→green |
| A | `TestS2SczLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS2WfzLevelSelectTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS3kAizTraceReplay` | 17/17 | pass | pass | green→green |
| B | `TestS3kAizZoneSliceTraceReplay` | 1/1 | pass | pass | green→green |
| A | `TestS3kCnzTraceReplay` | 27/27 | 3112 err; first f17322 camera_x 0x0260/0x0261 (+3 more failing tests) | same | red→red |
| B | `TestS3kCnzZoneSliceTraceReplay` | 1/1 | 4255 err; first f14616 camera_x 0x0260/0x026E | same | red→red |
| A | `TestS3kFbzCompleteRunTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kHczZoneSliceTraceReplay` | 2/2 | 4354 err; first f9482 air 1/0 | same | red→red |
| B | `TestS3kIczZoneSliceTraceReplay` | 1/1 | 2430 err; first f11989 x 0x66A2/0x66A8 | same | red→red |
| B | `TestS3kLbzZoneSliceTraceReplay` | 1/1 | 1663 err; first f30582 tails_y 0x013D/0x012C | same | red→red |
| A | `TestS3kMgzTraceReplay` | 1/1 | 8469 err; first f13939 camera_x 0x0000/0x0002 | same | red→red |
| B | `TestS3kMgzZoneSliceTraceReplay` | 1/1 | 10634 err; first f5255 tails_g_speed 0x0000/-01D5 | same | red→red |
| B | `TestS3kMhzZoneSliceTraceReplay` | 1/1 | 3191 err; first f6958 rings 2/1 | same | red→red |
| B | `TestS3kSonicTailsAiz2SegmentTraceReplay` | 1/1 | Invalid rewind reference closure after game=s3k currentZone=0 currentAct=0 traceZone=aiz traceAct=1 traceIndex | same | red→red |
| B | `TestS3kSonicTailsAiz3SegmentTraceReplay` | 1/1 | 1778 err; first f0 rings 75/0 | same | red→red |
| B | `TestS3kSonicTailsAiz4SegmentTraceReplay` | 1/1 | 1238 err; first f0 rings 8/0 | same | red→red |
| B | `TestS3kSonicTailsAiz5SegmentTraceReplay` | 1/1 | 2522 err; first f0 x_speed -000C/-0006 | same | red→red |
| B | `TestS3kSonicTailsAizSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsCnzSegmentTraceReplay` | 1/1 | 9069 err; first f2695 tails_y_speed -0530/0x0000 | 9002 err; first f2695 tails_y_speed -0530/0x0000 | red→red |
| A | `TestS3kSonicTailsCompleteEmeraldRunChain` | 1/1 | Run 's3k-sonic-tails-complete-emeralds' (s3k-sonic-tails-complete-emeralds.bk2) cannot certify a complete camp | same | red→red |
| A | `TestS3kSonicTailsCompleteEmeraldRunPrefix` | 3/3 | pass | pass | green→green |
| B | `TestS3kSonicTailsDdzSegmentTraceReplay` | 1/1 | 449 err; first f0 g_speed 0x0800/0x0000 | same | red→red |
| B | `TestS3kSonicTailsDez232SegmentTraceReplay` | 1/1 | 30 err; first f0 tails_x 0x1640/0x1620 | same | red→red |
| B | `TestS3kSonicTailsDez233SegmentTraceReplay` | 1/1 | 45 err; first f0 tails_y 0x03A3/0x03A7 | same | red→red |
| B | `TestS3kSonicTailsDez234SegmentTraceReplay` | 1/1 | 60 err; first f0 tails_x 0x1640/0x1620 | same | red→red |
| B | `TestS3kSonicTailsDez235SegmentTraceReplay` | 1/1 | 68 err; first f0 tails_x 0x1640/0x1620 | same | red→red |
| B | `TestS3kSonicTailsDez236SegmentTraceReplay` | 1/1 | 71 err; first f0 tails_y 0x03A3/0x03A7 | same | red→red |
| B | `TestS3kSonicTailsDez237SegmentTraceReplay` | 1/1 | 50 err; first f0 tails_x 0x1640/0x1620 | same | red→red |
| B | `TestS3kSonicTailsDez238SegmentTraceReplay` | 1/1 | 819 err; first f0 camera_y 0x0010/0x0020 | same | red→red |
| B | `TestS3kSonicTailsDez23SegmentTraceReplay` | 1/1 | 175 err; first f0 tails_y 0x03A3/0x03A7 | same | red→red |
| B | `TestS3kSonicTailsFbzSegmentTraceReplay` | 1/1 | 4152 err; first f7619 x_speed -03FF/-03B7 | same | red→red |
| B | `TestS3kSonicTailsGumball2BonusTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsGumballBonusTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsHcz2SegmentTraceReplay` | 1/1 | 3439 err; first f0 y_speed 0x0000/0x000E | same | red→red |
| B | `TestS3kSonicTailsHcz3SegmentTraceReplay` | 1/1 | 1171 err; first f0 rings 117/0 | same | red→red |
| B | `TestS3kSonicTailsHcz4SegmentTraceReplay` | 1/1 | 464 err; first f0 rings 180/0 | same | red→red |
| B | `TestS3kSonicTailsHczSegmentTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsHpz222SegmentTraceReplay` | 1/1 | 1758 err; first f0 rings 335/0 | same | red→red |
| B | `TestS3kSonicTailsHpz22SegmentTraceReplay` | 1/1 | 489 err; first f0 y_speed 0x0038/0x0000 | same | red→red |
| B | `TestS3kSonicTailsHpz2SegmentTraceReplay` | 1/1 | 1177 err; first f0 rings 150/0 | same | red→red |
| B | `TestS3kSonicTailsHpz3SegmentTraceReplay` | 1/1 | 283 err; first f0 rings 177/0 | same | red→red |
| B | `TestS3kSonicTailsHpzSegmentTraceReplay` | 1/1 | 2157 err; first f121 tails_mapping_frame 0x0096/0x0000 | same | red→red |
| B | `TestS3kSonicTailsIcz2SegmentTraceReplay` | 1/1 | 965 err; first f0 rings 156/0 | same | red→red |
| B | `TestS3kSonicTailsIczSegmentTraceReplay` | 1/1 | 2864 err; first f2337 player_animation_id 0x000D/0x001C | same | red→red |
| B | `TestS3kSonicTailsLbzSegmentTraceReplay` | 1/1 | 7187 err; first f959 player_animation_id 0x0002/0x001C | same | red→red |
| B | `TestS3kSonicTailsLrzSegmentTraceReplay` | 1/1 | 8897 err; first f208 tails_y_speed 0x07BD/0x0000 | 8870 err; first f208 tails_y_speed 0x07BD/0x0000 | red→red |
| B | `TestS3kSonicTailsMgzSegmentTraceReplay` | 1/1 | 4807 err; first f10709 x 0x23DB/0x23DC | same | red→red |
| B | `TestS3kSonicTailsMhz2SegmentTraceReplay` | 1/1 | 631 err; first f0 tails_y_speed 0x0000/0x0038 | same | red→red |
| B | `TestS3kSonicTailsMhz3SegmentTraceReplay` | 1/1 | 348 err; first f0 rings 77/0 | same | red→red |
| B | `TestS3kSonicTailsMhz4SegmentTraceReplay` | 1/1 | 200 err; first f0 rings 85/0 | same | red→red |
| B | `TestS3kSonicTailsMhz5SegmentTraceReplay` | 1/1 | 37 err; first f0 rings 96/0 | same | red→red |
| B | `TestS3kSonicTailsMhz6SegmentTraceReplay` | 1/1 | 532 err; first f0 rings 126/0 | same | red→red |
| B | `TestS3kSonicTailsMhz7SegmentTraceReplay` | 1/1 | 493 err; first f0 camera_y 0x0360/0x0620 | same | red→red |
| B | `TestS3kSonicTailsMhz8SegmentTraceReplay` | 1/1 | 402 err; first f0 camera_y 0x05E0/0x0620 | same | red→red |
| B | `TestS3kSonicTailsMhz9SegmentTraceReplay` | 1/1 | 1125 err; first f0 camera_y 0x0260/0x0620 | same | red→red |
| B | `TestS3kSonicTailsMhzSegmentTraceReplay` | 1/1 | 12 err; first f1276 camera_y 0x0326/0x0240 | same | red→red |
| B | `TestS3kSonicTailsPachinko2BonusTraceReplay` | 1/1 | 1258 err; first f469 x_speed 0x04F3/0x0100 | same | red→red |
| B | `TestS3kSonicTailsPachinko3BonusTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsPachinkoBonusTraceReplay` | 1/1 | 1638 err; first f122 rings 262/261 | same | red→red |
| B | `TestS3kSonicTailsSoz2SegmentTraceReplay` | 1/1 | 7834 err; first f0 tails_y_speed 0x0000/0x0038 | same | red→red |
| B | `TestS3kSonicTailsSozSegmentTraceReplay` | 1/1 | 2745 err; first f4868 player_animation_id 0x001F/0x0002 | same | red→red |
| B | `TestS3kSonicTailsSs10SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=4391 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs11SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=4831 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs12SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=5230 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs13SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=4672 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs14SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=5896 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs2SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs3SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs4SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs5SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs6SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs7SpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSs8SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=5151 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSs9SpecialStageTraceReplay` | 1/1 | unmatched recorded hardware completions were never reported: [raw_frame=3698 boundary=PRE_MAIN_LOOP expected c | same | red→red |
| B | `TestS3kSonicTailsSsSpecialStageTraceReplay` | 1/1 | pass | pass | green→green |
| B | `TestS3kSonicTailsSszSegmentTraceReplay` | 1/1 | 3076 err; first f0 camera_x 0x0040/0x0000 | same | red→red |
| B | `TestS3kSonicTailsZone0cSegmentTraceReplay` | 1/1 | 674 err; first f0 y 0x00C0/0x0080 | same | red→red |
| A | `TestS3kSozCompleteRunTraceReplay` | 1/1 | 3155 err; first f32951 tails_y 0x06AF/0x06B1 | same | red→red |

## Smoke and guards

`run_categories.py --base 1f37638cc --workers 2 --headless-gl surfaceless-egl
--rom-directory <ROM dir> --max-minutes 60 --run` on `822e79eb0` (code identical
to `e97436afa`): 4,367 candidate classes in 89 batches. Ordinary lane 36,188
tests, 8 failures, 48 skips, 26m31s execution; guards 820 tests, 1 failure,
2m25s. The skips are opt-in diagnostics, captures and performance probes gated
on system properties or explicit refresh flags.

- Guard failure (caused here): `TestNoProvisionalModApiShims.collisionRulesRetainsOnlyCanonicalAndNestedAirConstructors`
  pins the `CollisionRules` constructor list, which lost the three removed
  components. Fixed in the follow-up commit; the class passes 4/4.
- The 8 ordinary failures (`TestMasterTitleScreenLayout` ×2,
  `TestSonic2InterlacePixels`, `TestNativeCompetitionViews`,
  `TestNativeSpriteSatGameplay` ×3, `TestPrivateLobbyScreenRendering`) are not
  caused by this change: the same five classes run on `1f37638cc` and on this
  branch give the same 8 failures with byte-identical messages. They remain
  unattributed pre-existing failures.

The focused sidekick set (48 classes, 864 tests) and the S3K keep-green set
passed with 0 failures and 0 skips before the smoke run.
