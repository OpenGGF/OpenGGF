# S3K Angel Island Act 2 coverage matrix

Canonical binding: `S3K_ANGEL_ISLAND_2`, engine zone 0/act 1, ROM `$0001`, S3KL object set.
Edges: AIZ1 incoming seamless handoff; HCZ1 outgoing full entry.
Character routes: Sonic/Tails share the normal ship/end-boss path; Knuckles has a separate native route and end-boss branch.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kAiz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kAiz2EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| OBJECT / EVENT / CAMERA: battleship loop and bomb child | `TestAiz2ShipLoopRewindRoundTrip#battleshipSurvivesRewindRestoreAndCameraLockIsNotOrphaned`, `TestAiz2TransientChildRewind#shipBombSurvivesRewindRestoreWithMidFlightState` | Constructed ship/child plus declared camera/autoscroll state; active graph checks do not prove natural placement admission. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| OBJECT / SLOT: battleship propellers, ship exit and tree maker | `TestAiz2BattleshipSlotOwnership`, `TestAizBattleshipPropellerSlots` (a28c5ab47); `TestAiz2MakeTreeScriptOwnership`, `TestSonic3kAIZEvents#aiz2TreeSpawnerReadsAizMakeTreeScriptFromTheRom` | Propellers and ship exit (a28c5ab47) match native SST occupancy on `aiz_5` sample rows 2377-2830 and 3861-3951 (comparison-only slot probe); their old-source witnesses failed 5/5 on f96d31379. The tree maker reads AIZMakeTreeScript from ROM $23C13C: all 17 entries, one after-current attempt per pass, retry on failed allocation, deletion on the following pass, script priority. Its old-source witnesses failed 8 of 9 by assertion on a28c5ab47 (the rewind case is completeness, not a witness), and a same-pass-delete mutation failed the lifetime witness. Native occupancy on `aiz_5` rows 4001-4096 now equals the engine on every sample (was none), and the boss sits in slot 7 at 4109; on `aiz_completerun` the tree slots equal native at every sampled row 22768-22860 and tree screen X equals native `x_pos` - 128 to row 22816. Chain segment 8 went from 1692 errors (first 6111) to 35 (first 6989 `camera_y` +2 in the Knuckles cutscene); see the trace frontier log, 2026-10-16. LIMITED/RAW_RGB pictures against native (BK2 23700-23860) show the two formerly dropped trunks; whole frames still differ through the AnPal_AIZ2 cycle phase (engine one frame late, index two steps ahead; owner open) and player pose. Inherited gaps: the Translate_Camera2ObjX shake term for propellers and bombs (Y +-4 on shake rows); the small craft's bucket 2 against ROM priority $300; the tree X source is the engine smooth scroll, its Events_fg_1 model (3 px from `aiz_completerun` row 22837); bomb/explosion slot rows from 2861/3149/3215; the `loc_69482` child (ChildObjDat_69D66, native slot 9 at row 24683), so the engine capsule sits in slot 9 against native 10. The bridge display SST (`b96ef2c4d`: `TestAizCollapsingLogBridgeDisplaySst`, old source 4/4 red) and the ship's escape signal (`cdeb2dbc0`: `TestAizEndBossInstance#shipEscapeSignalWaitsForStartDefeatAfterTheFadeToLevelMusicWait`) put the bridge child, StillSprite, drawbridge and Robotnik's ship in their native slots; the zone slice falls from 9 errors (first 25038) to 1 (26179) and chain segment 8, after the spin dash camera fix (`11af51ca0`), to 4 errors on the HCZ destination row 7126 (engine PLC drain one row late). Chain rows 6111 and 6989 now match. |
| BOSS / LOAD: end-boss graph, splash and sequence | `TestS3kAizEndBossGraphRewind`, `TestAiz2BossEndSequenceObjects`, `TestAizEndBossInstance` | Component/late boss contracts; independent arena entry and all required character routes remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| OBJECT / CAMERA: forest ring and team bounds | `TestAiz2ForestRingCameraGeneration`, `TestS3kAiz2BigRingFormation`, `TestS3kAiz2BigRingCollision`, `TestS3kAiz2SidekickBoundsSync` | Local scene/bounds contracts; configuration and before/active/after placement breadth remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kAiz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | `TestS3kSonicTailsAiz2SegmentTraceReplay`, `TestS3kTailsFullChainAiz2SegmentTraceReplay` | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Qualification — 2026-10-14

Base/runtime lineage: `5b2ff8e78`; branch tip before this slice `390ebd765`, plus this commit’s explicit native-scripted art owner and test. Worktree `.worktrees/ai-s3k-parity-hpz`, Java 25.0.4.1, all three original ROMs by absolute path, `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null`.

`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kCanonicalEntryLifecycle,TestLevelPlayableArtInitializerModArt,TestDonorTailsTailBanks,TestSpriteManagerRewindCapture,TestS3kCnzSoloCarryInHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"` with `-Ds3k.rom.path`, `-Dsonic1.rom.path`, `-Dsonic2.rom.path`, then `test`: request 31958 completed 10:58:41 BST (09:58:41 UTC), 169 tests/0 failures/0 errors/0 skips, Maven 37.197 s. The new class contributes 70 cases/0/0/0 (7.070s); these are bounded focused checks, not broad smoke/full-suite certification. No integration/push is claimed by this local record.

The shared selection’s first measured CNZ NPC art failure, rejected alternatives and coherent correction are recorded in the [CNZ1 matrix](s3k-cnz-act1.md). They do not imply a new native scene-parity qualification for this act.

## Finite normal-entry products — 2026-10-14

The independent per-act class above supplies 140 distinct rows: native 75, S1 Sonic 20, S2 Sonic 25 and S2 Tails 20, each spanning 320/352/400/528/800 production presets. Solo, supported single follower, mixed two, duplicate two and declared seven-follower examples deduplicate only when donor character support makes rows identical. Unsupported follower names are explicitly substituted by the generator's declared Sonic intent; that is not a menu fallback or a participant cap. Every admitted identity and CPU leader is checked at entry and every ordinary/replay step. Two full-world restore/45-frame forward cycles remain in the same loaded world.

The eight-act/animation-consumer request 83519 on `6e9d951aa` plus the initial carry-art candidate completed 1186 tests/1 failure/8 errors/0 skips at 11:44:40 BST (10:44:40 UTC), Maven 1m53s. All seven other acts passed their 140 rows (CNZ1 also passed 3 native mixed/duplicate/seven-follower carry-authority vectors). AIZ2 passed 139/140; its S2-Tails/800/seven-Sonic case reordered player refs 3/5 in AutoSpin's unordered identity map during immediate restoration. Separately, 8 mocked art-manager consumers exposed a bridge-dispatch setup issue, corrected by the carry slice and its 61-case gate. These consumer errors do not erase the completed per-act rows and are not described as a green invocation.

After qualified carry art `a54c1a8ae`, Root's collision dependency `eea476471` and canonical player-key map capture dependency `e77b387a3` were adopted as `6c5ae479d`/`cacd63b77`. The valid-source follow-up request 30705 completed 163/0/0/0 at 12:06:02 BST (11:06:02 UTC), Maven 1m23s: AIZ2's complete 140 rows, player-reference codec 5, AIZ2 object codec 3, object-manager snapshot 11 and carried-art/handoff 4. Its exact selector is `TestS3kAiz2EntryMatrix,TestRewindPlayerReferenceCodecs,TestNativeCarriedPlayerArtBinding,TestAiz2ObjectRewindCodecs,TestObjectManagerRewindSnapshot`. All use the local queued lean wrapper, `-Dmse=off`, surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY, null OpenAL and original absolute S1/S2/S3K ROM properties. An earlier dependent request was accidentally submitted during dependency prose conflicts and was canceled at compilation with exit 130; it executed no tests and earns no behavioral credit.

Thus 1120 distinct normal-entry rows are qualified through retained 980 unaffected rows and the repaired AIZ2 source's 140, with explicit source scope; this is not one pristine 1120-case green run. No current native pixel, strict trace, physical post, death/reload, incoming/outgoing lifecycle or whole-act completion claim follows from this entry product. The listed remaining mechanic, route and presentation obligations stay open.

## Independent actual restart floor — 2026-10-14

[`TestS3kCanonicalDeathLifecycle`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalDeathLifecycle.java) independently boots this actual act binding, native 320 solo Sonic, then invokes the production pit-death owner twice. It writes no position, clock, ring, life, checkpoint or destination state. Each real `GameLoop` reload replaces the object/runtime owner, costs exactly one life, restores the current typed roster, resolved camera width and P1 focus, and retires nonempty outgoing live history. Title/fade/fresh assembly release is required separately from any intrinsic intro control. Each released same-world destination has immediate full-registry restoration and two 45-frame neutral forward cycles; no raw snapshot crosses the load.

Qualified source: `5d334596e` (includes early donor boot, frozen native camera and single production fade ownership) plus this commit's private dead-main mask correction and the two new death test classes. Queued lean request 40522 completed 12:59:51 BST (11:59:51 UTC), Maven 1m16s: 247 tests / 0 failures / 0 errors / 0 skips. The eight-act floor contributes 8 cases; `TestMainDeathVerticalWrap` contributes 18 literal main-motion/real-roster/live-mask cases; existing death/radius/movement/CPU rewind, reverse-gravity and S1 LZ3 wrap neighbors contribute 221. Command: `python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestMainDeathVerticalWrap,TestDeathRestartRoutineParity,TestDeathRadiusTransition,TestS3kCanonicalDeathLifecycle,TestS3kReverseGravityBoundary,TestS1Lz3VerticalWrap,TestPlayableSpriteMovement,TestSidekickCpuControllerRewindCapture"`, original absolute S1/S2/S3K ROM properties, then `test`; DISPLAY/WAYLAND_DISPLAY unset and `ALSOFT_DRIVERS=null`.

This is the independently executed native 320/Sonic restart floor, not the remaining finite lifecycle cross-product, physical pit traversal, post activation, route completion, native pixels or a strict trace sweep. Shared-change smoke, integration and push remain lead-owned and pending. The [MGZ1 record](s3k-mgz-act1.md#native-main-death-wrap-owner-correction-2026-10-14) retains the measured gameplay blocker and rejected partial repair.

## Finite actual restart products — 2026-10-14

[`TestS3kAiz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kAiz2EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

After each title/fade/fresh-assembly release, the complete registered world is captured, advanced for 45 neutral logical ticks, restored immediately and replayed twice. Each forward tick checks P1 survival, unchanged lives and actual configured owners. Intrinsic intro control remains native-owned; this is not a forced unlock or a raw restore across the load. Physical pit traversal, physical post capture/respawn, completion edges and later mechanic lifetimes remain separate.

One coherent focused invocation qualified all eight acts: command session54147 on runtime `e57236d519a861c9fbc43ba867230793dbd6fb0d` plus this commit's nine test-only deltas, 1120 tests / 0 failures / 0 errors / 0 skips (each act 140/0/0/0), actual terminal exit0 at 13:42:47 BST (12:42:47 UTC), Maven 3m05s. Exact selection: `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix`, `TestS3kIcz2EntryMatrix`, each restricted with `#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice` in the comma-separated `-Dtest` selector. Command prefix: `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`; original absolute S1/S2/S3K ROM properties were supplied, followed by `test`. Entry rows were not rerun merely to repeat their unchanged evidence. This is finite lifecycle qualification, not complete act, native-pixel, strict-trace or arbitrary roster certification; combined smoke/integration/push remain lead-owned.

## AnPal bank carry across the AIZ1 reload — 2026-10-16

AnPal_AIZ1 and AnPal_AIZ2 share `Palette_cycle_counter0`, `Palette_cycle_counter1` and
`Palette_cycle_counters` (sonic3k.constants.asm:599-615). Level's loc_60DE clears them
(sonic3k.asm:7684), but the seamless reload in AIZ1BGE_Finish does not reach it. The
engine used to rebuild the AIZ2 cycles with private counters starting at zero. On the
reload row it also ticked the new AIZ2 cycler: engine observation `palobs1` at row 6300
shows timer 5, counter0 8, counter2 6. The native `aiz_completerun` RAM instead carries
counter1 4, counter0 `$1620` and counters+$02 `$2A` into AIZ2. Native row 6300 is still
AnPal_AIZ1's call, 6301 is a lag frame, and the first AIZ2 call is 6302, where channel B
starts. Channel A first runs at 6306 and reads entry 7. LevelLoop calls Animate_Palette
(7923) before DeformBgLayer/ScreenEvents (7929-7930), so the reload iteration belongs
to the outgoing act.

The bank now lives at ROM width in `Sonic3kGlobalAnimationState`. The module owns that
state, so it outlives the per-act animators. The fresh-Level step clears it, it rides
the animator's rewind snapshot, and the AIZ cycles read and write it. At the start of
a reload, the outgoing S3K animator runs that iteration's Animate_Palette if it has not
yet run, and the incoming animator skips its AnPal call for that one iteration. A reload
consumed at the frame top runs before the frame counter advances, so it is unchanged.

`TestS3kAizAnPalBankCarry` derives every expectation from the ROM routines and tables
and observes only the level palette:
- AIZ1 history: runs 6 and 7 with their table entries, after the fresh-Level fade.
- Seamless carry: no channel-A write until the inherited counter1 expires. Channel B
  starts on the first AIZ2 call, and channel A then shows trickle entry 7 and water
  entry 3.
- A reload inside a LevelLoop iteration: that iteration runs AnPal_AIZ1, with no AIZ2
  write.
- A full load into AIZ2 clears the bank: entry 0 on the first call.
- Two restore and forward-replay cycles over a carried window.

Results:

| Run (queued, focused, S3K ROM) | Source | Result |
|---|---|---|
| green3 | candidate | 4 run, 0 failed, 0 errors, 0 skipped (Maven 23:08:12+01:00) |
| redold | `src/main` of `73d296f12` | 3 failed at the intended assertions: an immediate channel-A write at the first AIZ2 call or on the reload iteration, and the window phase. The full-load guard passed (Maven 23:11:28+01:00) |
| redcarry | candidate with the reload hook disabled | 1 failed: the torch wrote on the reload iteration (Maven 23:18:45+01:00) |

The first engine witness (LIMITED/RAW_RGB `TraceCaptureTool` clips at the bank-only
commit `8d51c342a`) is in `spot-checks/aiz-palette-bank/`. The witness compares without
normalisation at the native pixels of the unique trickle and torch colours. The engine's
channel-A changes fell on native's rows, but the entry was a constant native + 4. The
engine's AIZ1 counters+$02 was 18 at row 6297, where native has `$2A`.

### AIZ1 boundaries behind the inherited phase — 2026-10-17

Three AnPal_AIZ1 boundaries differed from the ROM:
- The counters+$02 cycle used `Camera.isLevelStarted()` as its gate. The ROM tests
  `Palette_cycle_counters+$00` (3217). Obj_AIZPlaneIntro sets it at Sonic's x `$13D0`
  (loc_67712, 135755-135756), and Obj_CutsceneKnuckles clears it as Knuckles leaves
  (loc_61F22, 128795-128796).
- A fire-branch counters+$02 of `$30`/`$36` was re-wrapped to 0 before the read. The ROM
  reads it (3219-3223), past the 48-byte table.
- The fire/normal choice tested the current camera inside the call. The ROM uses
  `AIZ1_palette_cycle_flag` as AIZ1_Resize (loc_1C46E, 38913-38917) left it in the
  previous frame's DeformBgLayer (38343-38356). Level's own DeformBgLayer (7792) sets it
  before the first call.

The two writers now call `S3kPaletteCycleCounterWriter` on the animator. The byte lands
after that frame's palette call, as Process_Sprites (7926) follows Animate_Palette
(7923).

| Run (queued, focused, S3K ROM) | Source | Result |
|---|---|---|
| g2green1: `TestS3kAiz1AnPalBoundaries`, `TestS3kAiz1AnPalGateWrites`, `TestS3kAizAnPalBankCarry` | candidate | 9 run, 0 failed, 0 errors, 0 skipped (Maven 2026-10-17T00:10:47+01:00) |
| g2redold: `TestS3kAiz1AnPalBoundaries` | `src/main` of `8d51c342a` | 2 failed at the intended assertions: run 2 held while `Level_started_flag` was clear, and the carried `$30` read as entry 0 (00:15:59+01:00) |

The second witness was captured at the same source and moments, in
`spot-checks/aiz-palette-bank-v2/` (provenance.md). The tally counts rows where both
sides resolve to a table-entry set; equal means the sets match.

| Window | L3c4 | L3c8 | Torch L4c1 |
|---|---|---|---|
| Handoff 6294-6330, before (`8d51c342a`) | 4/5 | 0/2 | 0/5 |
| Handoff 6294-6330, after | 14/15 | 6/6 | 0/5 |
| AIZ2 22800-22880, before (rows 22800-22834) | 29/35 | 0/18 | 6/12 |
| AIZ2 22800-22880, after | 81/81 | 42/42 | 10/27 |

The one handoff miss is row 6316. There the fire curtain's edge leaves an engine colour
that is in no table entry. The trickle entry and its row now equal native from the
engine's own counters.

### Torch channel B settled from palette words — 2026-10-17

A comparison-only engine probe ran during `TestS3kAizZoneSliceTraceReplay` (one queued
job, rows 6290-6340 and 22790-22890, Maven 2026-10-17T01:31:19+01:00). After each row it
logged the AnPal bank and the level palette words. Those are compared with native RAM at
the same row: the bank at `$F632`/`$F634`/`$F650-$F65B`, and `Normal_palette`. Evidence is
in `spot-checks/aiz-palette-bank-v3/`.
- 22790-22890: bank 101/101 and every compared word 101/101. That covers torch L4c1
  (counters+$04/+$08), trickle L3c4/L3c8/L4c11, L3c14 and water L4c12-15.
- Handoff: bank 51/51 and torch 51/51. L3c4, L3c8, L4c11 and L4c15 differ on rows
  6290-6299 only. They are static AIZ1 fire-transition words that no active AnPal_AIZ1
  branch writes, so they are event-owned. From the reload row 6300 every word matches.

The earlier torch pixel disagreement is therefore not palette. The L4c1 colour paints the
burning background's fire shapes, which sit in different places in the engine, and
Sonic's shield frame differs (`side-row22866.png`, `torch-pixels-row22866.png`). That
inherited background/sprite rendering difference is not attributed here.

## Entry replay input correction — 2026-10-17

`CanonicalEntryMatrixSupport.advanceEntry(fixture, row, frames, right)` passed its `right` flag as the second argument of `HeadlessTestFixture.stepFrame(up, down, left, right, jump)` in every committed revision from the helper's introduction in `a54c1a8ae` through `0197c5a5a`. The `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice` runs recorded above on those sources therefore held **Down**, not Right, in each 45-frame capture/replay window, so a grounded P1 ducked in place instead of moving. Request 83519's uncommitted initial candidate cannot be inspected here; treat its replay input as unverified rather than as Right. Those records keep their original commands, sources and counts and remain evidence within that Down-held scope: actual start table, owners, survival, unchanged lives and full-world restore/replay equality. They did not exercise held-Right movement, the camera scroll it causes or the object placement that scroll admits. The restart products' post-release windows were already neutral and are not affected.

The helper now passes the flag as `right`. [`TestS3kCanonicalEntryRightInput`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalEntryRightInput.java) drives the same helper on native 320 solo Sonic in CNZ2, whose start is grounded on flat floor, and checks the production joypad path on each of 45 frames (Right held; Up, Down and Left absent; not crouching). It then requires P1 still grounded with `ground_vel` 45 × `$C` (`Sonic_Move` adds `Acceleration` for `button_right` in `Ctrl_1_held_logical`) and forward movement.

Corrected qualification, all on `0197c5a5a` plus this commit's test-only deltas (helper SHA-1 `49e31e0026db`), command prefix `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, original absolute S1/S2/S3K ROM properties, then `test`:

- Eight-act entry selection: `-Dtest` lists `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix` and `TestS3kIcz2EntryMatrix`, each restricted with `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`, plus the first version of the input test. It finished at 02:02:24 UTC after a 193 s queue wait and 1m46s of Maven: 1121 tests / 1 failure / 0 errors / 0 skips. Every act passed 140/0/0/0 with held Right. The one failure was the input test's own premise. It first used MGZ1, whose Sonic start (ROM `$C0,$F00`) is airborne, so `ground_vel` stays zero while air control raises `x_vel` by 24 each frame. The test was moved to CNZ2; the helper was not changed.
- Revised input test with the helper restored to its `0197c5a5a` bytes: fails at Right frame 0 (`isRightPressed` false), 02:04:26–02:05:01 UTC, 1 test / 1 failure / 0 errors / 0 skips.
- Revised input test with the fix: 02:05:15–02:05:44 UTC, 1 test / 0 failures / 0 errors / 0 skips.

Death/reload products, combined smoke and integration were not rerun here. This requalifies the held-Right entry floor only; it is not act certification.

## Root composition: managed ship-exit sibling — 2026-10-17

Root composed the qualified `11af51ca0`..`704a49587` lane onto `8d0763bea` as `a1a381dd5`, keeping the later
`ec1d6b8dc` title-archive work and its failing follow-up separate. The composed ship exit still called raw
`addDynamicObjectAfterSlot` / `addDynamicObjectAfterCurrent`, causing two structural guard failures. It now calls
`spawnAfterCurrentSibling` while the ship still holds its SST, then detaches that SST, retires only the old wrapper
through `ObjectLifetimeOps.expireDynamic`, and installs the small craft at the transferred slot. The independent
sibling keeps the native `AllocateObjectAfterCurrent` result without acquiring the old ship's child lifetime.
The owning ROM order and slot outcome are documented by `Obj_AIZBattleshipMain` (pinned `044fa46`:105327–105339).

On `a1a381dd5` plus this ship-only repair, normal queued Maven, Java 25, surfaceless EGL, null OpenAL and the three
original absolute ROM paths:

- Focused `-Dtest=TestAiz2BattleshipSlotOwnership,TestAizBattleshipPropellerSlots,TestAiz2MakeTreeScriptOwnership,TestAiz2ShipLoopRewindRoundTrip,TestAizEndBossInstance,TestS3kAizEndBossGraphRewind,TestAiz2BossEndSequenceObjects,TestPlayableSpriteMovement,TestSozLastCheckpointShortcutProduction,TestSozEndBossProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
  **360/0F/0E/0S**, finished 03:01:09 UTC. Slot transfer, same-pass tree allocation and world rewind all pass.
- Fresh `-Pguards` with TestArchitecturalSourceGuard, TestRewindFieldDispositionGuard, TestModApiSignatureSurface,
  TestHardwareTimingAuthorityGuard and TestObjectPhysicsStandardizationGuard: **142/0F/0E/0S**, 03:02:54 UTC.
  Before this repair the same selection had two failures from the ship's raw additions (12 against budget 10).
- `-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`: **1/1F/0E/0S**, 03:04:59 UTC;
  exactly one comparator error at row 26179, `rings`, native 72 / engine 0, as on the qualified lane.
- `-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`,
  single fork / alphabetical: **1/1F/0E/0S**, 03:08:38 UTC. Segment 8 retains four physics errors on row 7126
  (first non-camera `x`, native `$0280` / engine `$0000`); segment 11 still loses production ownership at BK2
  cursor 68801, load generation 9, HCZ1. The canonical route is not green.
- `-Ptrace-replay -Dtest=TestS2Ehz1TraceReplay#replayMatchesTrace`, same fork/order: **1/0F/0E/0S**, 03:09:05 UTC.
  This is affected S2 replay coverage for the shared spin-dash camera change, not a complete S2 sweep.

Commands used the updated main-workspace maven_queue.py from this composition's cwd, with `--normal -Dmse=off`.
No broad smoke or release full suite was run in this slice. Title-card/load, strict HCZ and complete-route/native
presentation obligations remain open.

## Original fullrun normal bridge standing SST — 2026-10-17

Bounded native320 Sonic + CPU Tails qualification, base `8030832d2ab6080e5324cdfe35e971677fd97b41`
in T3 checkout `t3-98d036a9`, branch `bugfix/ai-s3k-aiz-bridge-standing-gpt61`.
Only `S3kBossDefeatSignpostFlow.updateSpawnSignpost` changes runtime behavior.
Its tested source SHA256 is `9339eadb3c26abb3115c2eb85f885392f68b58b0359d9c287276c2c9fb97a5da`.

The original `aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2` is unchanged
(SHA256 `6837de0f67db7eb68f20b6f6df6a2872713a613d8b4dbc804847209c16b56e97`), as are
its V5 CSV/aux data and the existing bootstrap/input/lag/clock driver. The original
locked-on ROM has SHA1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
`TestS3kAizTraceReplay#normalBridgeStandingSstMatchesOriginalFullrun` reaches row9513
and collects all four exact standing assertions, the normal bridge's actual
placement `(1AE5,04F8)`, subtype8, and Sonic's centre X/Y. Comparison values never
write gameplay. No assigned/seeded SST, movie-offset adjustment or native capture
was used. The earlier `b96ef2c4d` display-child proof at alternate row22845 is not
substituted for these original rows.

The initial unchanged-runtime prefix (77167, terminal1 at10:39:14 UTC) has one
failed case, four inner `player_stand` failures, zero errors/skips: rows9510–9513
expect12 and read10. At completed row9391, engine decorations `(192A,049A)` and
`(1982,04DD)` hold11/12 versus native10/11. Admission9392 therefore puts the normal
bridge in engine10 versus native12; both display children already hold17.

One authorized shorter diagnostic prefix ended at8941 (85012, 1/0F/0E/0S,
10:42:12 UTC), using the same driver. At8938/39, the actual retained boss is7,
results owner4, detached flow21, signpost22 and stub23. Native control7 instead
creates signpost10/stub11. At8940 the engine's camera workers take10/11, while
native X takes12. This earlier signpost allocation, not bridge initialization,
causes the later decoration permutation. The temporary diagnostic method was
removed after inspecting its result.

`Obj_EndSignControlDoSign -> CreateChild6_Simple` calls `AllocateObjectAfterCurrent`
from the native control SST (pinned skdisasm `044fa46`, sonic3k.asm:180483–180492,
177205–177225,37952–37969). The repair uses existing
`spawnChildAfterSlot(nativeControlSlot, factory)`; unset boundaries retain the
current-flow fallback, and an in-place control uses its own slot. It changes no
allocator, bounds worker, timing/input authority or trace field.

| Obligation | Observed result |
| --- | --- |
| Exact standing / placed owner / P1 centres | Single post-fix prefix99559, 1/0F/0E/0S, twenty assertions, terminal0 at10:48:30 UTC; all four slots12 and centres match. At9392 decorations are10/11, parent12, display17. |
| Independent allocation / recreation / forward replay | `TestS3kSignpostNativeControlSst`: normal allocation creates retained17, transient18, flow19; the transient retires itself. Old runtime73232 reads sign20 instead of18 after two complete-world restore/replay cycles (1/1F/0E/0S). Repaired detached case reads18; in-place and unset controls retain20. All three force recreation and compare every registered key immediately after restore and after45 neutral steps, twice. No slot is assigned. |
| Existing owner controls and mandatory S3K4 |60576, 88/0F/0E/0S, terminal0 at10:47:27 UTC; both LevelLoading identities included. Queue wait11s, Maven1m09s. Independent owner3, existing flow4, signpost20, AIZ camera1, mandatory startup60. |
| Fresh source / rewind / authority / object guards |83910, 134/0F/0E/0S, terminal0 at10:52:19 UTC; source72, rewind4, timing-authority25, object-standardization33. Queue wait187s, Maven32.965s. |

All commands used Java25, normal admission through the current Main absolute
`maven_queue.py` from this checkout, original absolute `s3k.gen`, surfaceless EGL,
null OpenAL and no DISPLAY/WAYLAND_DISPLAY. With `MAIN_QUEUE` and `S3K_ROM` bound
to those existing absolute paths, command prefix is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk ALSOFT_DRIVERS=null python3 "$MAIN_QUEUE" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
```

Append `-Ds3k.rom.path="$S3K_ROM" test -B` after each selection:

- Exact initial/post-fix prefix: `-Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dtest=TestS3kAizTraceReplay#normalBridgeStandingSstMatchesOriginalFullrun`.
- Independent old-red: `-Dtest=TestS3kSignpostNativeControlSst#detachedFlowAllocatesAfterTheRetainedControlAndReplaysTwice`.
- Corrected focused controls: `-Dtest=TestS3kSignpostNativeControlSst,TestS3kBossDefeatSignpostFlow,TestS3kSignpostInstance,TestAizMinibossCameraUnlock,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
- Fresh guards: `-Pguards -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestObjectPhysicsStandardizationGuard`.

Rows9510–9513 are all `FULL_LEVEL_FRAME`: raw CSV values9510–9513,
trace LF`23F6..23F9`, sampled engine LF`23F5..23F8`, fixture executed count9206–9209.
Admission9391–9393 has trace LF`237F..2381`, sampled engine LF`237E..2380`,
executed9087–9089. These are separate observed clocks, not a fitted row/BK2 offset.
The supplemental `EngineTiming` facade read0 in this headless context; it is not
used as an object-clock measurement or assertion.

**R1 three-worker gap, separately closed by R2 below:** exact ROM bytes at `$85DE0` jump to
`CreateChild1_Normal` at `$84060`; table `$85DE8` has count-minus-one2 and pointers
`$84A48` (X), `$84AA4` (min-Y), `$84AD2` (max-Y), each with zero position offsets.
Native8940 retains X12 and max-Y14; min-Y13 is absent from the completed row.
Its `$4000` first carry has integer part0 and the signed `ble` already-at/beyond-
target branch snaps and deletes it. Native X retires at9272 and max-Y at8989.
At R1 the AIZ engine owner creates only X/max-Y and snaps min-Y directly. The
R1 unit does not repair or qualify that omitted SST, three-worker allocation,
nontrivial min-Y continuation, allocation failure or those workers' rewind.
R2 below addresses the handoff separately; allocation exhaustion remains open.

The change-based preview against803 selects2828 smoke classes plus guards.
The authorized bounded owner/startup/trace/guard selection replaces that broad
run here; Root owns combined composition/integration validation. This certifies
only these four standing rows, their placed owner/centres and the independent
flow component replay. It is not a full-prefix physics/animation sweep, full SST
or auxiliary-schema certificate, another BK2, native pixels, all configurations,
full chain or act certification. Missing advertised aux families remain missing.
No full chain, broad smoke or release suite was rerun.

## Three Act2 level-size workers — 2026-10-17 R2

Separate owner unit on R1 `da70653308ecf6290b3df020ebed3fd624399789`, same
checkout/branch and immutable original input evidence above. Only
`AizMinibossInstance` changes runtime behavior; final tested/restored SHA256 is
`3aa35fede24c479ef72af6ead674f4f1cbf9595837ea600c0874ffb59ec15cd2`.
No shared flow, worker implementation, allocator, bounds manager, title timing,
bootstrap, input or trace payload changes.

References here use this checkout's skdisasm pin
`044fa46725c71187399e13f5ddb70e11d32dc024`, not universal line numbers:
`Obj_EndSignControlDoStart`180506–180510 calls `Change_Act2Sizes`180666;
`Make_LevelSizeObj`180688 uses `Child1_Act2LevelSize`180694–180701, whose three
entries are X/min-Y/max-Y. The previously verified ROM jump/table at
`$85DE0/$85DE8` and targets `$84A48/$84AA4/$84AD2` agree. Each
`CreateChild1_Normal` iteration177010–177041 calls `AllocateObjectAfterCurrent`
37952–37969 from the active control, with no remembered results-slot input.
The workers178245–178318 accumulate `$4000/$4000/$8000`; X uses unsigned `bhs`,
min-Y signed `ble`, and max-Y signed `bgt` for retirement.

The handoff now publishes Act2 max-X/max-Y targets and creates the existing
`S3kIncLevelEndXGradualInstance` plus the two
`S3kCameraGradualObjectInstance.forActTwoLevelSizes` modes through
`spawnAfterCurrentSibling`, in source order, before deleting the retained control.
It preserves min-X and the deferred title-completion poll. It removes the direct
min-Y snap and the remembered-results reservation. No native equivalent of that
reservation was found; the independent allocation witness demonstrates its effect.

`TestAizAct2LevelSizeWorkers` declares the late retained-control component entry
in an actual AIZ2 world, with a real active boss/results owner and normally
allocated objects that retire to leave holes. Production ObjectManager scans,
with read-only witnesses interleaved in the live SST, observe all three births
before min-Y dispatch. Observed control18 has lower hole17, first eligible hole19,
middle witness20, former results hole21, last hole22 and tail witness23. These are
results of normal allocation, not assigned slots or runtime constants.

The old-red12271 (11:05:50 UTC, 2/2F/0E/0S, Maven24.162s, no queue wait) creates
the old X/max-Y controllers at21/22, skips19 and omits min-Y. Both cases complete
two whole-registry45-scan restore/replay cycles before failing their behavior
assertions. A min-Y distance16 snaps to the target on dispatch1 rather than
remaining16 for the first three dispatches. Corrected births are X19/min-Y21/max-Y22.
At distance0 the middle witness sees min-Y21 before it self-retires in that same
creation scan; counting only surviving workers would miss this obligation.

Both distance0 and distance16 cases compare every accelerating bound and live
owner over45 production scans. With X/max-Y distances16, X retires on dispatch13;
nontrivial min-Y also retires13. Max-Y remains live at equality on dispatch8 and
retires9. No ordinary camera tail is executed in this component check, so the
observations are worker dispatches, not trace rows or complete engine frames.
Each case captures the full registered world before handoff and after scan1,
forces object recreation, restores each snapshot twice, compares every registered
key immediately, then replays45 or44 scans and compares the complete final world.
The distance16 live snapshot recreates all three actual workers and their
fractional accumulators/Act2 target modes.

All Maven commands use the exact Java25/Main absolute wrapper/environment/ROM
prefix above, with `-Ds3k.rom.path="$S3K_ROM" test -B`. Selections and results:

| Selection | Completed result |
| --- | --- |
| `-Dtest=TestAizAct2LevelSizeWorkers,TestAizMinibossCameraUnlock,TestS3kBossDefeatSignpostFlow,TestS3kSignpostNativeControlSst,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` |55530, 70/0F/0E/0S, 11:07:36 UTC, Maven1m12s, no wait; includes existing factory/acceleration controls and all mandatory S3K4 identities. |
| `-Dtest=TestAizAct2LevelSizeWorkers,TestGenericRecreate` after strengthening live-worker restore |93137, 13/0F/0E/0S, 11:09:56 UTC, Maven23.353s, no wait. |
| Original exact standing selection from R1 above, once on R2 |36591, 1/0F/0E/0S, 11:11:22 UTC, Maven1m12s, no wait; all twenty assertions pass, rows9510–9513 stand12, admission9392 parent12/display17, same clocks as R1. No initial R2 red prefix. |
| `-Pguards -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestObjectPhysicsStandardizationGuard` |87743, 134/0F/0E/0S, 11:15:17 UTC, Maven34.867s, no wait; source72/authority25/rewind4/object33. |
| `-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`, existing distinct movie |R2 45125, 1/1F/0E/0S, 11:13:37 UTC, queue77s/Maven35.395s. Authorized exact matched R1 75679, 1/1F/0E/0S, 11:17:01 UTC, no wait/Maven1m20s. The complete76-entry error arrays, warnings, bootstrap, verification groups and missing-aux arrays are equal. |

The distinct `_movies/s3k-complete-sonic-tails.bk2` has SHA256
`82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`.
Both alternate runs compare26041 frames and retain76 errors: first11350
`player_animation_id` expected`0x13`/actual`0x05`, eight queue differences at
11350–11351,67 animation differences through11858, and trailing26179 `rings`
72/0. This is inherited on exact R1, not the earlier composition's single-ring
frontier and not a green route. At the first difference the control still has
`End_of_level_flag=false`; matching the full arrays, rather than that observation
alone, establishes no R2 worsening. No title behavior was tuned.

For the matched control only the owned runtime file was temporarily restored
under the intended-tree lease; all production sources then matched `da706`.
The new test stayed unchanged. Exact R2 bytes/hash above were restored under the
lease afterward; earlier R2 prefix/guards apply to those identical source bytes.
`target/` currently contains the last R1 control compilation, not R2 classes.
No rebuild is claimed or requested here; Root will compile the composed source.
Inspected reports and the bounded source helper are consumed/removed normally.

The actual task-tree change-based preview against R1 selects3040 smoke classes
plus guards. This bounded owner/startup/recreation/trace/guard selection replaces
that broad run here; Root owns one combined composition validation. Allocation
exhaustion, native full-SST/position equivalence, the absent advertised auxiliary
families, other configurations/rosters, complete-route title frontier, native
pixels and whole-chain/act certification remain open. No new native/media run,
whole chain, broad smoke or release suite was run.


## Physical miniboss handoff and alternate ring blocker — 2026-10-17 R3

This record supersedes the ownership model, not the historical R1/R2 results
above. Base is Root `7ed6ba5b8b05d4e460c64307a50630f94e795552`, same
`t3-98d036a9` / `bugfix/ai-s3k-aiz-bridge-standing-gpt61`. Only
`AizMinibossInstance` changes runtime; final SHA256 is
`1878f322bfa03f2c20970c5dd2a371839a4e90481b93c872c332695331027626`.
Shared flow, allocator, bounds workers, collision/touch, inputs and trace data
are unchanged. Root owns subsequent composition and integration clearance.

### Source and rejected compensation

Citations use this tree's skdisasm pin
`044fa46725c71187399e13f5ddb70e11d32dc024`; line numbers are not universal.
`Obj_AIZMiniboss`137287–137292 dispatches its routine before the HP/collision
poll `loc_68F62`137826. Fatal touch now carries the existing production HP write
into that local post-routine poll, with ordinary movement/Obj_Wait first and
score/hurt/defeat side effects once. The previous13-entry fatal delay and carried
catch-up/results terms had no owning ROM state and are removed together.

**Rejected hypothesis:** `$3F` was not invented. `BossDefeated_StopTimer`180900
falls through `BossDefeated`180908–180913, which writes literal63 to `$2E`.
`Wait_FadeToLevelMusic`179742–179759 consumes64 later own dispatches, then
installs fresh119 and creates independent SongFade with plain global
`AllocateObject`179749. The existing `spawnDynamicObject` helper is AfterCurrent;
the correct existing API here is `spawnDynamicObjectLowestFreeSlot`. A lower
free SST waits until the next production scan; no inline update compensates.

`loc_68C02`137455–137458 calls `Obj_EndSignControl` in the same physical SST.
Existing detach/transfer/deleteNoRespawn/replacement APIs now install the generic
flow there and invoke `beginNativeReplacement`. The generic owner retains fresh
119, literal results90, normal sign INIT/stub dispatch and DoStart's X/min-Y/max-Y
workers. The old AIZ shadow camera bridge, title-art early-release predicate,
results-slot reservation and displaced-entry state are obsolete and removed.
R2's short camera/three-worker tests now enter actual generic DoStart rather than
the removed private path. Nontrivial min-Y acceleration, at-target creation-pass
retirement and twice-forced recreation before and during live workers remain.

`loc_68FB6`137858–137869 creates Child6_CreateBossExplosion subtype `$10`.
The unchanged existing `MhzMinibossExplosionController` models that exact native
independent owner:31 consumed attempts, allocation before RNG/sound, final
next-pass deletion. It is an independent AfterCurrent sibling, survives physical
boss replacement and finishes its tail after capture/recreation. Early parent
music fade and the inline subtype0 explosion helper are removed; SongFade owns
its own lifetime/flags. No new generic lifecycle or dispatch contract was added.

### Attribution and short old-red witnesses

Root41825 on7ed had original AIZ80 errors (8 queue/72 animation), first8218
animation13/5, and chain segment4 11789 errors (10255 physics/1534 animation),
first6749 x_speed -00E0/0000. Control59027 restores only AIZ to da706 (R2 off),
all other production7ed: both exact selectors fail with the complete same arrays.
Control58516 additionally restores shared flow to Main803 (R1 off), running only
original AIZ: zero errors. These controls exonerate R2; they do not alone
attribute segment4. Exact Root source bytes were restored before implementation.

New independent `TestAizEndSignControlReplacement` declares component inlets,
uses normal allocation/hole retirement and production scans, and never seeds a
slot, wait word, clock or trace value. Old-red38946:2/1F/0E/0S, actual AIZ
control17/sign18 first birth Y0, no stub, no physical replacement; the in-place
control initializes correctly. Old-red59829:1/1F/0E/0S, the local fatal poll and
fresh wait/replacement are missing. Old-red33946:1/1F/0E/0S, a naturally freed
lower hole17 is skipped for music19, birth updates1 instead of0, and music
expires one own dispatch early. Each behavior witness completes full-world
forced-recreation/restore and forward replay twice before asserting behavior.

The single authorized original-driver prefix53263 through8941 on interim source
`600d70aa7e2b99c582efce3a055b7235ebefd4031229703aaf1aaa63c74177a6`
localized the global music call. Native7973 has music4, control7, sign10/stub11;
engine music10 instead shifts sign11/stub12. Native8940 X12/max-Y14 contrasts
engine X10/max-Y14; min-Y13 self-retires in that creation pass. Full native
`slot_dump` plus appearance/retirement evidence proves the static occupants8/9
exist in both: the missing-static-owner hypothesis is rejected. Filtered
`object_state` alone could not prove their absence.

These are trace-row/phase observations, not guessed BK2 offsets. At7972–7975,
FULL_LEVEL_FRAME, trace LF1DF9–1DFC, engine LF1DF8–1DFB, actual ObjectManager
Vbla2110–2113, executed7673–7676, next BK2 cursor8484–8487. At8938–8941,
trace LF21BB–21BE, engine LF21BA–21BD, Vbla24D6–24D9, executed8635–8638,
next cursor9450–9453. GameplayModeContext binds the object clock to that manager;
the unbound EngineTiming facade's0 is not object-clock evidence.

### Commands and observed results

All commands below run from this tree with this exact common prefix and suffix:
`OPENGGF_MAIN` denotes the original absolute Main checkout, not this task tree;
its current wrapper is invoked by absolute path while output remains in this tree.

```text
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
 LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
 python3 $OPENGGF_MAIN/tools/testing/maven_queue.py --normal \
 -Dmse=off -Dopenggf.headless.gl=surfaceless-egl [selection] \
 -Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen \
 -Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen \
 -Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen test -B
```

Java25, original S3K SHA1`CFBF98C36C776677290A872547AC47C53D2761D6`;
inherited JDK_JAVA_OPTIONS/MAVEN_OPTS preserved. Trace selections add
`-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical`.
No source changed while queued/live. Times below are UTC; execution excludes wait.

| Selection / source | Terminal observation |
| --- | --- |
| `-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunPrefix#twoSpecialStageReturnsThroughThirdEntry,TestS3kAizTraceReplay#replayMatchesTrace`, R2-off control |59027,11:29:00,2/2F/0E/0S,Maven1m35s,no wait; exact Root errors retained. |
| `-Ptrace-replay -Dtest=TestS3kAizTraceReplay#replayMatchesTrace`, both AIZ fixes off |58516,11:31:14,1/0F/0E/0S,Maven1m21s,no wait. |
| New birth / fatal / music methods separately before their owner corrections |38946,11:35:49,Maven1m07s;59829,11:40:53,23.537s;33946,11:52:26,23.605s; old-red counts above, no wait. |
| `-Dtest=TestAizEndSignControlReplacement,TestAizAct2LevelSizeWorkers,TestAizMinibossCameraUnlock,TestS3kBossDefeatSignpostFlow,TestS3kSignpostNativeControlSst,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestMhzMinibossExplosionAllocation,TestMhzMinibossDefeat`, final1878 |75120,11:56:09,77/0F/0E/0S,Maven1m15s,wait36s. Includes four new owner cases, two worker cases, independent Mhz3 and mandatory S3K4. Music17=natural hole17 below control18, birth updates0, alive at120. |
| `-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunPrefix#twoSpecialStageReturnsThroughThirdEntry,TestS3kAizTraceReplay#normalBridgeStandingSstMatchesOriginalFullrun+replayMatchesTrace`, final1878 |56721,11:57:19,3/0F/0E/0S,Maven59.417s,no wait. All20 standing assertions: actual12 at9510–9513, bridge12/display17 at9392, correct centres/identity. Original AIZ20463 rows, zero errors/warnings/bootstrap; prefix segments0/2/4 complete, zero physics/animation errors. |
| Same three selectors on interim600, before the demonstrated global music correction |93177,11:47:18,3/1F/0E/0S,Maven1m42s,no wait. Original and prefix green, four standing-slot failures10/12; new source repair justifies the final affected replay. Earlier focused63597 passed73. |
| Read-only unchanged original prefix through8941, interim600 |53263,11:50:36,1/0F/0E/0S,Maven29.973s,no wait; temporary test removed. |
| `-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`, final1878, distinct existing BK2 |33952,11:58:08,1/1F/0E/0S,Maven40.703s,no wait:11 ring errors, no animation/errors in other fields,26041 compared rows, zero warnings/bootstrap. **Alternate UNQUALIFIED.** |
| One read-only alternate observation, same driver via existing frame observer and semantic prefix13742 |86679,12:02:23,1/1F/0E/0S,Maven31.570s,no wait: same first13740 rings1/2. Prior20566 failed test compilation before executing any replay; private helper misuse was corrected, not a second executed variant. Temporary observer removed. |

The single planned guards command was `-Pguards -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestObjectPhysicsStandardizationGuard`:
40441,12:05:39 UTC,134/1F/0E/0S,Maven33.151s,no wait. Source72/authority25/
rewind4 and32 object cases pass. The exact raw-allocation inventory alone expects10,
now9 because this correction removes AIZ's obsolete inline raw insertion. The
owned inventory ratchet is tightened10→9, with no assertion weakening. Authorized
`-Dtest=TestObjectPhysicsStandardizationGuard#productionObjectLifecycleRawCallCountsDoNotGrow`
then runs once:27275,12:07:05 UTC,1/0F/0E/0S,Maven24.683s,no wait. This is the
other133 retained passes plus one corrected-method pass, not a pristine repeated
134-case invocation. No further Maven/check is requested.

### Unattributed alternate ring owner: integration blocker

R1/R2's historical76-error alternate frontier is not the final result. The new
source removes its queue/animation errors but exposes ten ring1/2 spans:
13740–13932,13934–13935,13937–13938,13940–13960,13962–13993,13995,13997,
13999–14208,14210–14214,14216–16669. The eleventh entry is inherited trailing
26179 rings72/0. The ten new spans are **not attributed or waived**; reduced
error totals do not establish no worsening or integration clearance.

The authorized observation identifies the actual engine increment owner.
Completed13739 FULL: LF358E, ObjectManager Vbla3933, executed13710,
next BK2 cursor14681, count1. LostRing SST13 at1BB0/01EE, velocity00C4/FE74,
phase96, flags47, collected=false/routineStarted=false, source spawn
1B2D/01F9,id37, currentTouch=true. Completed13740 FULL: LF358F,Vbla3934,
executed13711,cursor14682,count2; that same SST13/position alone becomes
collected/routineStarted=true, flags0. P1 centre1BA0/01FF→1BA2/01FF,
radii7/14,status04,routine2,invulnerability8→7. P2 centre1B7C/019F→1B7F/01A0,
radii7/14,status02,objectControlled=true. Existing last-touch state has P1's
left/top1B9A/01F4,height22/radius11, but the later P2 skip clears the hit list.
P1 is supported by eligibility/geometry; no passive per-increment actor hook
records it directly. Both players read the shared count, not two increments.

Source `Touch_ChkValue`20810–20835 writes routine4 and returns; bouncing-ring
`loc_1A7C2`35699 later owns GiveRing. Existing engine LostRing.update likewise
starts the collection routine/adds one ring in its own dispatch. Native13740
slot_dump lists bouncing-ring owners12–15 and52–54/56–59, but filtered
object_state/object_near has no ring positions/routines at13735–13742. The
native position/writer witness needed to distinguish ring trajectory/cadence
from touch admission is absent. No LostRing/touch/collision/debris change, clock
fit, count correction or further variant/prefix was made. Root owns the next
ring evidence/owner unit; this R3 correction is useful but not integration clearance.

Native `loc_68C02` also creates four `loc_68E98` debris children. That inherited
omission is outside this correction and limits complete SST occupancy proof.
Original's seven advertised aux schemas remain absent; alternate has eight
absent families. Full native aux/position/RNG equivalence, allocation exhaustion,
configuration/roster/viewport breadth, cold routes, other BK2s, native pixels and
full-chain/act certification remain open. No new native/media/fullchain/broad run.
The final target compilation is runtime1878 plus restored maintained replay tests
and the tightened guard inventory, from27275. Temporary observer source is absent.
All inspected diagnostics and bounded source helpers are consumed under the metadata
lease alone; no receipts/log archives remain. All job handles are terminal/released.


## Alternate ring birth and earlier placement admission — 2026-10-17 R4

Read-only unit in the same t3-98d036a9 on Root5da817090e2925cd03d5f72b8546ae53a7687025;
AIZ runtime1878f322bfa03f2c20970c5dd2a371839a4e90481b93c872c332695331027626 unchanged.
The one unchanged alternate-driver prefix69634 ends13742:1/1F/0E/0S,12:17:26UTC,
Maven1m11s/no queue wait,13713 compared rows,zero warnings/bootstrap,seven-plus-one
known absent advertised aux families. First13740 rings1/2 remains; no green claim.
It uses the R3 common Main-wrapper/normal/all-three-original-ROM/EGL/nullAL/no-ambient
environment and trace-segments single-fork/reuse/alphabetical flags, selecting
TestS3kAizZoneSliceTraceReplay#observeAlternateRingBirthAdmissionReadOnly.
The temporary observer supplies only logging/semantic end, no gameplay values.

Native fixture13565 frees11–15, including PathSwap12 and decor11. Engine retires
the same source identities at13565, but in12–16. Native lowest hole11 contrasts
engine actual firstFree12 at13565–13567. The engine monitor at1C08/04E0 occupies11,
while the same native monitor is10. Engine13568 consumes free12 for the first
negative-00C4 ring and13/phase96 for positive00C4; native uses11 and12/phase97.
Both positive rings start1B2D/01F5, thirteen rings and count13→0. Thus the ring
allocator does not skip actual lowest-free12; its reservation fault hypothesis
is rejected. No pending queue remains at the completed sampled boundaries; that
is not a claim about absence during the production pass. Earlier pool permutation
precedes the hurt. First engine positive-vector bounce is13656:1B71/0207,
y-velocity0454→FCAF, actual ObjectManager Vbla38E0,LF353B,executed13627,
next input cursor14598. The first differing earlier admission is still unobserved.

The three exact identities are ROM placements, read directly from the original
ROM: pointer1E3D9C→AIZ2 object list1E4876, matching own skdisasm2.bin.

| Placement / engine identity at13567 | ROM record / native owner and lifetime |
| --- | --- |
|256 Sonic3kInvisibleBlockObjectInstance, engine6, id28/sub11,rawY0490 |1E4E76=`1b9004902811`; native5/code1EC6C, first appearance12612; no retirement through13742. Obj_InvisibleBlock/loc_1EC6C owns solid and coarse-X range return (own pin42696–42745). |
|258 AnimatedStillSpriteInstance, engine5, id30/sub01,renderFlags1,rawY2331 |1E4E82=`1b9723313001`; native23/code2BF5A, first appearance13025; no retirement through13742. Obj_AnimatedStillSprite→loc_2BF5A→Animate_Sprite/Sprite_OnScreen_Test (60417–60438). |
|265 Sonic3kMonitorObjectInstance, engine11, id01/sub07,rawY04E0 |1E4EAC=`1c0804e00107`; nativecode1D566, first appearance12806/slot31, retirement12912/slot31, reappearance12967/slot10, no later retirement through13742. Obj_Monitor/Init/Main/range tail40482–40548. |

All line citations use skdisasm044fa46725c71187399e13f5ddb70e11d32dc024.
Static258 is not a child or mutable-spawn explanation: its only production
construction is the registered placement factory plus rewind recreation; the
class never updates dynamic spawn or overrides getSpawn. The six-byte record,
layoutIndex258 and source coordinates agree. Native lifetime events are actual
object_appeared/object_removed, corroborated by full slot_dump, rather than the
first filtered object_state. Engine admission before13563 was not observed.

Root's separately produced immutable taskD/aiz-ring-native13740-v2 supplies the
missing native positions. Its single process47191 exited1 because the final
assertion incorrectly required a nonzero Touch_ChkValue callback: nine raw
Touch_Process boundaries and zero ChkValue are retained evidence, not a green
diagnostic/certificate. ProbeSHA256a889b8740c33bfb18d56dd02c128ddd595a16866e3957589a1211ae6f7850cbc,
original movie82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf.
At native FF06 Touch_Process input14681 (next cursor14682),LF358F,VInt3934,
P1/P2 and rings1 agree: B378/native12 positive00C4 ring is1BB0/04B9,vy0C34.
The later completed RAM boundary is1BB1/04C5,vy0C4C; do not merge these phases.
Engine before that touch retains13 at1BB0/01EE,vyFE74 and collects it.

The targeted next question is placement258's first engine admission relative
to256 and265, including the monitor's native reload cycle. Its rawY2331 bit15
is clear: native loc_1BA92 applies the normal Y gate. At12612 camera1982/048A,
window0400–0680 excludes0331; at13024 camera1A37/03FB,window0300–0580 includes it,
then native13025 births it. This suggests a specific initial/deferred/cursor/
camera-order question, not a proven AnimatedStill, monitor, ring, phase or allocator
fix. One newly authorized unchanged prefix ending13026 will observe only these
three admissions/retirements and event-neighbor placement/camera state. No new
native, media, full alternate, canonical, guards or broad run is authorized here.
The alternate's ten new ring spans and inherited terminal ring error still block integration.


### Exact-three-placement observation: gate/lifetime hypothesis rejected

Authorized final prefix11174 ends13026 using the original alternate replay
method via its existing passive closure hook/semantic prefix. Same runtime1878,
base5da and common flags/environment above; selector
TestS3kAizZoneSliceTraceReplay#observeThreeExactPlacementAdmissionsReadOnly.
Terminal0,12:27:37UTC,1/0F/0E/0S,Maven32.766s,no wait,12998 compared rows,
zero errors/warnings/bootstrap. This shorter prefix stops before the known ring
frontier and does not qualify the full alternate. Initial attempt30691 stopped
with a diagnostic null-spawn NPE after3278 compared rows,12:26:40UTC,
1/0F/1E/0S,28.408s/no wait; its null handling alone was corrected. It supplies
no target-admission result or green credit.

All three target admission/lifetime **rows match native**:

| Event | Engine observation | Native event/full-pool evidence |
| --- | --- | --- |
|256 first birth12612 |InvisibleBlock6 |InvisibleBlock5, actual appearance12612 |
|258 deferred12612 |absent, deferredVerticalLoad=true, active=true; rawY2331 |absent from full12612 slot_dump; native gate excludes0331 |
|265 birth12806 |Monitor30 |Monitor31, actual appearance12806 |
|265 retire12912 |absent/active=false |Monitor31 removed12912 |
|265 reload12967 |Monitor11 |Monitor10 appeared12967 |
|258 birth13025 |AnimatedStill5, deferredVerticalLoad=false |AnimatedStill23, actual appearance13025 |

At12612 live/copy/manager cameras all1982/048A, minY0, normal Y window0400–0680;
skipVerticalFilter=false. Source258 remains deferred. At13024 all cameras
1A37/03FB, source258 remains absent/deferred; at13025 all cameras1A37/03EF,
twoAxisCameraYCoarse changes0400→0380 and the deferred Y pass admits it.
The X cursors are272/222, X coarse1A00, with no pending X load. Actual13025
ObjectManager Vbla3669, LF32C5, executed12997, next BK2 cursor13967. This
disconfirms early static admission, a missed vertical filter, or monitor reload
timing as the repair for these three owners. No production change is justified.

The permutation already exists before target256. Completed12610–12611 engine
firstFree4, holes4/6/18/22+, with rock240 at slot5:1AC0/05D8,id05/sub11,
ROM1E4E16=`1ac005d80511`. The same native rock is6, born12510. Thus engine
loads256 into genuine next free6, while native can use5. At13024 engine5 is
genuinely free (the prior rock/animal is gone), so static258 correctly takes5.
The source owner and its normal retirement are different from an allocator skip.

Existing full native slot_dump12586 lists rock6, static7, spring8, bridge9,
static10–11, PathSwap12, static13, SS ring14, statics15–16 and bridge display17,
plus transient bubble owners4/5/18/22. Native bubbles4/5 retire12592/12596;
no intervening birth before12612 fills4/5. Before native rock admission12510,
slots4/5 still hold placements208/209: static1803/04DF,code2BF5A,record
1E4D56=`180324df3001`; AutoSpin1810/0460,code1E896,record
1E4D5C=`181024602680` (id26/sub80). Both retire12554. Native rock6, spring8,
bridge9 and its display17 all appear12510. Later engine event pools already have
bridge9/display10 and rock5, but this observation does not capture their own
12510 admission or the preceding208/209 retirement. The remaining targeted gap
is those earlier owners' pool/admission history, not a full110-slot certificate.
No shared placement, lifetime, collision, phase or allocator fix is claimed.


### Earlier208/209 and rock/bridge observation: culling exonerated

One authorized original-driver passive prefix81466 ends12555 on unchanged
base5da/runtime1878. Selector
`TestS3kAizZoneSliceTraceReplay#observeEarlierPlacementLifetimeReadOnly`,
using the same current Main absolute wrapper, normal lane, trace-segments,
all three original absolute ROM properties, single fork/reuse/alphabetical,
EGL/nullAL/no DISPLAY or WAYLAND environment above. Terminal0 at12:35:40UTC,
1/0F/0E/0S, Maven32.568s/no queue wait,12527 compared rows, zero errors,
warnings and bootstrap errors. The same eight advertised aux families remain
absent. This prefix ends before the known alternate ring frontier; it is not
alternate qualification. The observer only reads existing owners, placement
bits, camera/cursor/clocks and the existing pure range predicate. No gameplay,
bootstrap, input, lag, allocation, physics or timing mutation.

Identity correction: source209 is **AutoSpinObjectInstance**, not spikes.
The ROM record1E4D5C=`181024602680` is id26/sub80; registry AUTO_SPIN=$26
selects AutoSpin, and native loc_1E896 is Obj_AutoSpin's horizontal crossing
routine. Spikes is id08. Tracking used the exact layout identity, so the prior
name error did not affect observation. Sources208/209 are first naturally
admitted together at12217 and both naturally retire at12554, matching native.

| Event | Engine ordered owners / free holes | Native event and full-pool evidence |
| --- | --- | --- |
|12216 before target admission |X worker5; free4/6+ |The corresponding native X worker is21, not absent. Native5 is free after earlier owners retire. |
|12217 first208/209 |208 AnimatedStill4; X worker5;209 AutoSpin6;211 collapsing platform7; firstFree8 |208 still4;209 AutoSpin5; platform6; X worker21. All three placement birth rows match. |
|12509 |208 still4,209 AutoSpin6; firstFree5; holes5/7/9/10/18+; no reservations |208 still4,209 AutoSpin5 remain live. Native X worker21 already retired12335. |
|12510 rock/bridge births |rock240=5,spring241=7,bridge243=9,display=10; firstFree18 |rock6,spring8,parent9,display17. Native10–16 already contain earlier placements. |
|12554 retirement |208/209 removed, firstFree4, holes4/6/22+ |208/209 removed from4/5 on this same row. |
|12555 |same rock/spring/parent/display5/7/9/10; holes4/6/22+ |No new target lifetime difference demonstrated. |

The rock is ROM1E4E16=`1ac005d80511`; bridge is
1E4E28=`1ae504f82c08`. The display is the actual DisplayChild whose private
parent reference is the layout243 bridge, not a guessed id/position match.
No reservation occupies the engine holes at the emitted event boundaries.
At12510 the bridge's own update allocates its display into the genuine next
free slot10. Native display17 follows its different existing pool. These are
observed results, not grounds for changing bridge allocation or seeding slots.

At12217 live/copy/manager cameras all1617/03D3, X cursors214/197,
lastCameraX160A/coarse1600, Y coarse0380. Source208/209 are active with no
pending/deferred/dormant/destroy latches. Actual completed12510 is
FULL_LEVEL_FRAME, LF30C2,ObjectManager Vbla3466, executed12482,
next BK2 cursor13452; live/copy/manager camera1881/04DA, placement's
lastCameraX1880/coarse1880, X cursors244/207. These clocks and cursor are
read from their actual owners; no EngineTiming facade or fitted row offset.

The existing ObjectManager.isObjectOutOfRange query is evaluated against the
**sampled completed live camera**, not mislabeled as an intra-pass observation.
At12553 the owners still exist and that query is already true at live1901/04BA.
The earlier object pass used camera18FD (also placement.lastCameraX):
base1800, object-aligned1800, unsigned distance0. At12554 the next pass uses
1901, base1880 and distanceFF80, so both owners unload; completed live is1906.
That matches native retirement12554. The later camera/sample phase explains
why the sampled predicate can be true while the just-completed object is still
live. Obj_AnimatedStillSprite/loc_2BF5A tails to Sprite_OnScreen_Test;
Obj_AutoSpin/loc_1E896 tails to Delete_Sprite_If_Not_In_Range. Both source
helpers use Camera_X_pos_coarse_back and unsigned $280. Engine's existing
non-custom per-object end-of-pass check consumes the corresponding reference
X and native-width $280. The observed lifetime/cull hypothesis is rejected.

The genuine earlier boundary is **X-worker allocation before12217**. Native
Obj_IncLevEndXGradual/code84A48 is born12001 in21 and retires12335. Engine's
same semantic worker is already5 at12216; this is not proof of an extra worker
or a wrong lifetime. Actual native12001 full pool has placements/owners in5–20,
X21 and maxY23. Native5 is exact ROM placement159, AnimatedStill1037/0322,
id30/sub0, record1E4C30=`103703223000`, appeared9509 and removed12018.
Other stable native occupants include160 static1049/0346 in8,169 static119C/02E3
in7 and177 static12A0/02C4 in6. The four-placement observer did not emit the
engine worker birth pool at12001 or locate159 at that boundary. Missing owner,
earlier retirement, another permutation, or a cursor difference cannot yet be
selected from slot numbers. No production repair or independent old-red is
justified by this remaining gap.

Owning source at skdisasm044fa46725c71187399e13f5ddb70e11d32dc024:
Obj_EndSignControlDoStart180506 calls Change_Act2Sizes180666, which falls into
Make_LevelSizeObj180688 and the three-entry table180694. CreateChild1_Normal
177010 repeatedly calls AllocateObjectAfterCurrent37952. The existing engine
flow likewise uses spawnAfterCurrentSibling for all three workers. Native
maxY23 with X21 and the table's minY entry is consistent with minY22 retiring
in its creation pass; no native minY appearance event is asserted. The next
specific missing witness is the actual engine pool/source159 lifetime when
DoStart creates the workers, not a new global allocator or culling fix.

Temporary observer source is restored byte-for-byte to HEAD after terminal81466;
production remains1878 and no runtime edit is proposed. Target retains the last
temporary observer compilation, not a fresh maintained-test compilation.
Inspected diagnostic summaries/facts are recorded here; consumed reports are
removed under the metadata lease alone. Original native/capture inputs stay
immutable. No new native/media/alternate/canonical/guards/broad run. The ten new
alternate ring spans and inherited terminal ring error remain integration blockers.


## R5 low-SST journal and animal lifetime owner — 2026-10-17

R4 evidence is preserved separately at e207faf7dd14c9b6c9cc435fb438922d8f689f52.
One passive original-alternate-driver prefix84110 through12019 ran on that
commit/base5da/runtime1878, with the same current Main absolute wrapper,
normal/all-three-ROM/EGL/nullAL/no-ambient environment, trace-segments,
single-fork/reuse/alphabetical flags. Selector
TestS3kAizZoneSliceTraceReplay#observeEarliestLowSstTransitionsReadOnly.
Terminal0 at12:44:57UTC,1/0F0E0S,Maven45.503s/no wait;11994 compared rows,
zero errors/warnings/bootstrap, eight known absent advertised aux families.
The completed-frame low-SST4–23 journal has362 ownership/lifecycle-state events,
2,423,252 bytes below its10,000,000-byte cap. Initial full pool and requested
previous/current birth/control pools were recorded; no110-slot certification,
within-pass listener, source API, state seeding or gameplay writes.

Source159 first naturally appears9077 in31 in both engine/native, unloads9230,
and returns9509 in engine8/native5. It stays live through DoStart and retires
12018 in both. Thus the R4 slot5-specific9509 birth was a re-admission, not its
first lifetime. It is neither missing nor prematurely culled at the worker handoff.
Actual12000 flow occupies4, phaseAWAIT_ACT_TRANSITION,nativeControlSlot4,
End_of_level_flag=true,159in8, genuine free5/21/22/23. Actual12001 X worker5 has
accumulator4000 and maxY22 has8000; flow4 is gone. The independent workers have
no retained engine-parent backlink; the previous flow and source factory identify
the caller. Source order is consistent with minY21 self-retiring in its birth
pass; that transient is not claimed as a direct completed-frame observation.
Native workers are21/23, with the same source-consistent minY22 transient.
Actual12001 FULL_LEVEL_FRAME LF2EC8,ObjectManager Vbla3269,executed11976,
next BK2 cursor12943, live/copy camera10E0/02B8. No allocator defect is shown.

The same journal identifies a concrete earlier lifetime disagreement:

| Boundary | Engine | Existing native evidence |
| --- | --- | --- |
|9279 freed animal birth |AnimalObjectInstance5,source0E10/033C |Obj_Animal/code2C778 in5,same centre |
|9295,9315,9316 pop |0E10/0316,0E10/0328,0E10/032B |Same centres,routine2 |
|9323 landing |FLY,0E10/0342 |routine0E,0E10/0342 |
|9373–9374 native render edge |The later engine9377 sample remainsFLY at0D74/02EE;9378 at0D71/02EF, consistent with the same -300 flight |9373 centre0D80/02EC,camera0D88/02DA: right edge0;9374 centre0D7D/02EC,camera0D86/02DA: right edge-1 |
|9375 next object pass |Animal5 remains live |Animal5 actually removed |
|9378 next native admission |Engine5 still animal |PathSwap/code1CD8A at0CF0/02E8 takes5 |
|9418 |Animal5 finally retires;5 becomes free |PathSwap5 still live |
|9463 decor admission |layout152 F80/038A takes genuine free5,153 F85/0338 takes10 |PathSwap5 retires during this pass after loading;152 takes10,153 takes32 |
|9509 re-admission159 |159 takes8,160 takes11 |159 takes5,160 takes8 |

This proves a causal lifetime branch, rather than assigning blame from slot
numbers. It does not certify every intervening owner or put an unobserved engine
PathSwap allocation into a guessed slot. The journal observes completed passes;
source/load order explains the still-occupied native5 at9463 loading. Existing
native object_state/appear/remove/full slot_dump and original physics camera rows
are comparison-only inputs to this analysis.

At own skdisasm044fa46725c71187399e13f5ddb70e11d32dc024,
Obj_Animal60938 dispatches initial pop through loc_2C9E0 (61139), then this
species' flight through loc_2CA7C. loc_2CAD0 (61211) tests the prior
render_flags bit7 and deletes through loc_2C9DA (61135). Render_Sprites
36376–36417 clears then recomputes that bit using copied camera and width8;
the left edge is inclusive at dx=-8 and outside at dx=-9. Animal init writes
y_radius=C and width_pixels=8, but leaves height_pixels=0. That zero render
height is distinct from its collision radius and S2's approximate32-pixel Y band.
Engine AnimalObjectInstance.onScreen(64) uses a fresh64-pixel margin because
GameRules.SONIC_3K.animalObjectUsesRenderFlagDeleteBounds is false. Its existing
cached-render path assumes S2 height32, so simply enabling that switch is incomplete.
The observed animal's pop/floor/flight agree before retirement; no physics, RNG,
points or allocator correction is justified by this evidence.

Root authorized one independent old-red production-birth/previous-render-edge
regression with natural pop/landing/flight, next-pass retirement/free-hole reuse,
in-range control and twice forced whole-world recreation/forward replay, followed
by the smallest animal lifetime/S3K semantic seam repair. Shared owner scope is
explicitly coordinated before editing. Affected animal/S2/RNG/rewind and mandatory
S3K checks, then one existing alternate and exact standing/original/twoSS methods,
remain required. No new ancestor prefix/native/media/fullchain/broad work.
The ten new alternate ring spans remain integration blockers until requalified.
Temporary observer source is restored exactly; its journal and inspected reports
are consumed normally after these facts are recorded. Target retains84110's
last temporary observer compilation until the next authorized test build.


### R5 animal repair qualification and unchanged alternate blocker

The final runtime delta from R4e207 is only AnimalObjectInstance and the existing
GameRules.SONIC_3K animal lifetime switch. No new rule shape, factory API,
physics/RNG/points/allocator behavior, collision radius, native input, capture or
state hydration. The existing camera semantic
useScreenYWrapValueForVisibility selects the S3K copied-camera/word-mask render
path. S1/S2 keep their original lifetime flags and geometry. MAIN now polls
before movement; walk/fly keep their post-work poll. First init draws first, and
existing captured romRenderOnScreen/firstDisplayFrame are retained through
recreation. The class handles ordinary subtype0 freed animals; nonzero-subtype
player-relative branches and separate ending/capsule animals are not certified.

| Exact check | Observed result |
| --- | --- |
| Independent old-red TestS3kFreedAnimalRenderLifetime on runtime1878 |15578,12:55:08UTC,2/2F0E0S,Maven24.170s/no wait. Natural edge pass92,X0D77/dx-9: cachedtrue,retirementfalse,reuse-1. Geometry's eight assertions expose outside/height0/wrap and MAIN motion before deletion. Twice forced whole-world recreation from birth/live edge replays consistently. |
| Affected animal/S2/RNG/rewind and mandatory S3K checks after repair |27140,12:57:40UTC,73/0F0E0S,Maven1m11s/no wait, ten actual classes. Newanimal2,AnimalRNG3,S2animal1,DestructionEffects6,EggPrisonAnimalRewindRendering1 and mandatory S3K60 pass. Natural edge now cachedfalse,next-pass retirementtrue,reused4; two complete forced-recreation cycles pass. |
| One corrected existing alternate replay |39799,12:59:18UTC,1/1F0E0S,Maven36.231s/no wait,26041 compared rows,zero warnings/bootstrap. Physics11,animation0. All eleven field/span/expected/actual identities match R3's durable33952 record, including first13740 rings1/2. **Alternate remains UNQUALIFIED.** |
| Original AIZ + exact standing + two-special-stage prefix |6148,13:01:20UTC,3/0F0E0S,Maven1m01s/no wait. Original20463 rows/zeroerrors,warnings,bootstrap,seven known absent advertised aux schemas. Standing9510–9513actual12/all20assertions,admissionparent12/display17. Prefix0/2/4complete,physics0/animation0/warnings0/bootstrap0;five dynamic-art gaps/failures0. |

Final source hashes: AnimalObjectInstance
73f1c45485ca81a7ef055d58c0e26abda1664089456eba4f4f4829dff734b5ed;
GameRules7b694eba04238262861a76cc6977f85eb93a8709f30464c1eb891fe4fecaeb1c.
Independent exact-source review found no material issue and made no execution
claim. The actual focused/trace results above supply that separate evidence.
No extra review loop, guard sweep, ancestor prefix, native/media or full chain.
Root owns combined smoke/source-authority guards and composition.

Root's matched published803 control78232 (separate owned baseline tree) executed
only the exact alternate case and retained **one**26179 rings72/0 error,
1/1F0E0S,12:52:40.260085UTC,Maven1m25s,zero warnings. It used
**-Ptrace-replay**, while worker39799 used **-Ptrace-segments**. The POM has
identical3GiB argLine,fork/reuse/order,tmp/system properties for these profiles;
only suite selection/includes/excludes/groups differ, and the exact requested
case executed once in both. No runtime/bootstrap/fixture property difference or
rerun is inferred. Record the actual profile names rather than claiming literal
command equality.

The ten13740–16669 spans are therefore confirmed **new versus803**, even though
the animal-rule defect itself was inherited. The hypothesis that this one
lifetime correction alone closes those ring spans is rejected by39799. It
repairs a demonstrated native lifetime and has not located the remaining
post-fix ring admission/trajectory owner. No post-fix SST certificate, phase
adjustment, collision/debris/LostRing change or green integration claim follows.
Four native loc68E98 debris omissions, full native aux/RNG/position equivalence,
original's seven/alternate's eight missing advertised aux families, allocation
exhaustion, wider viewport/donor/roster, cold/other BK2 routes and full chain/act
coverage remain open.

All R5 commands ran from the owned tree. In this reproducible command form,
OPENGGF_MAIN denotes the original absolute Main checkout; JDK_JAVA_OPTIONS and
MAVEN_OPTS remain inherited. No direct toolchain probe or outer activity lease.

```bash
r5_maven() {
  env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
    LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
    python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal \
    -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
    -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical \
    "$@" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" \
    "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
}
r5_maven '-Dtest=TestS3kFreedAnimalRenderLifetime'
r5_maven '-Dtest=TestS3kFreedAnimalRenderLifetime,TestAnimalObjectRngOwnership,TestSonic2AnimalObjectTiming,TestDestructionEffects,TestEggPrisonAnimalRewindRendering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils'
r5_maven -Ptrace-segments '-Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace'
r5_maven -Ptrace-replay '-Dtest=TestS3kAizTraceReplay#normalBridgeStandingSstMatchesOriginalFullrun+replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix#twoSpecialStageReturnsThroughThirdEntry'
```

These are the executed old-red/focused/alternate/three-control selections, not
requests to repeat them. Source remained frozen queued/live. Reports and the
bounded observer journal were inspected and consumed under metadata ownership
alone, with no receipts/log archives. No live handles remain. Current target is
the final animal source plus maintained tests compiled by6148; no temporary
observer source or compilation claim is carried forward. Local owner commit
only; R4e207 remains separate, Main803 is untouched and publication is blocked.


## R6 post-animal live pool and remaining boss-child lifetime — 2026-10-17

One authorized read-only original-alternate-driver prefix9577 ran through raw
row13742 on e207 plus the eleven staged R5 files, **before the R5 local commit**.
Frozen SHA256: AnimalObjectInstance73f1c45485ca81a7ef055d58c0e26abda1664089456eba4f4f4829dff734b5ed,
GameRules7b694eba04238262861a76cc6977f85eb93a8709f30464c1eb891fe4fecaeb1c,
AizMinibossInstance1878f322bfa03f2c20970c5dd2a371839a4e90481b93c872c332695331027626.
The sole unstaged observer added to the existing alternate test had SHA256
f174c3665f074420f657f3da3707911a364ce9c34bb4ba93c236e59335a49c4e.
It used the original superclass driver/bootstrap/input/lag/hardware-timing
contract, its existing post-rewind-closure observer and semantic prefix seam;
normal current Main absolute wrapper, all three original absolute ROMs, EGL/nullAL,
no ambient display, inherited JDK_JAVA_OPTIONS/MAVEN_OPTS and the same explicit
single-fork/reuse/alphabetical flags. Exact selection:
`-Ptrace-segments -Dtest=TestS3kAizZoneSliceTraceReplay#observePostAnimalLivePoolReadOnly`.
Use the preceding R5 command template for the unchanged environment/ROM flags.
No trace gameplay hydration, input changes, timer/slot/RNG seeding, gameplay writes,
production API/hooks, native/media, full alternate, broad run or second prefix.

9577 terminal1 at13:14:15UTC, Maven41.509s, queue wait0s,1/1F0E0S.
13713 compared rows, physics1/animation0, warnings/bootstrap0. The existing
13740 rings expected1/actual2 error spans13740–13742; this diagnostic is red,
not a trace certificate. Eight known advertised aux families remain absent.
The target-only journal has223 events/5,805,290bytes under its10,000,000-byte cap.
Only low-SST4–23 ownership/lifecycle changes and requested boundary pools were
emitted, with selected source identities outside that window. It records
completed observer passes, not within-pass transient allocation or110-slot
certification. ObjectManager currentExecSlot is correctly -1 at this boundary.
Stable registered ObjectRefId, original source spawn versus mutable live spawn,
class/layout/subtype and actual allocated slot identify owners without assigning
native-to-engine slot translations.

The animal correction **does execute on the real route**:

| Raw row | Actual engine result | Existing native comparison |
| --- | --- | --- |
|9279 |Animal identity19, registered dynamicId955, actual5, source0E10/033C, MAIN/firstDisplay=true; both lifetime/render semantic rules true |Obj_Animal2C778 birth5, same source centre |
|9373 |FLY,0D80/02EC, copied/live camera0D88/02DA, signed dx-8, prior/current render bit true |Same centre/camera; right edge0 is included |
|9374 |FLY,0D7D/02EC, copied/live0D86/02DA, dx-9; preceding bittrue, newly captured bitfalse |Same centre/camera; outside edge |
|9375 |Fly work then prior-false poll retires5; retained diagnostic reference destroyed, allocator5free, registered identity removed |Actual object_removed5/2C778 on9375 |
|9378–9463 |PathSwap source123,0CF0/02E8, naturally reuses5 at9378 and retires9463 |Same native5/code1CD8A lifetime |
|9463 |1520F80/038A takes10,1530F85/0338 takes32 while PathSwap5 still owned at loading |Same native admissions; no guessed phase within the completed-frame observation |
|9509–12018 |1591037/0322 returns in5,1601049/0346 in8;159 retires12018 |Same native placements/lifetimes |
|9418 |The held animal remains destroyed and has no registered identity; its old physical slot number is not live occupancy |Rejects the old fresh64-margin retirement frontier |

Actual clocks are separate from raw row labels and from each other. These are
FULL_LEVEL_FRAME observations, with hex VInt/LF and decimal executed/input cursor:

| Raw row | ObjectManager VInt | Level_frame_counter | Executed runner frames | Next BK2 cursor |
| --- | --- | --- | --- | --- |
|9279 |27C7 |2432 |9266 |10221 |
|9375 |2827 |248D |9357 |10317 |
|9463 |287F |24E5 |9445 |10405 |
|9509 |28AD |2513 |9491 |10451 |
|10837 |2DDD |2A41 |10817 |11779 |
|10901 |2E1D |2A81 |10881 |11843 |
|11021 |2E95 |2AF9 |11001 |11963 |
|12001 |3269 |2EC8 |11976 |12943 |
|13568 |3888 |34E3 |13539 |14510 |
|13740 |3934 |358F |13711 |14682 |

These clocks come from the attached ObjectManager, LevelManager and original
fixture RecordingFrameDriver cursor, not the earlier unbound EngineTiming0.
The native callback input14681 at13740 is a Touch_Process boundary; the table
is after the completed engine row with the next input cursor14682. They are
related by the original driver contract, not conflated as identical phases.

The first remaining demonstrated lifetime branch in this recorded causal chain
is the **normal miniboss's five component children**, not the animal or allocator:

* At9768 engine/native births agree: body10 (native686E8), arm15 (68720),
  barrels21/22/23 (68C12). They are real admitted children, not missing factories.
* At10837 engine `AizMinibossInstance.onDefeatStarted`250–254 immediately marks
  all five childComponents destroyed and clears the list. The prior completed
  pool still had all five. Native status bit7 marks the defeated parent, while
  all five child SSTs remain occupied through10900.
* At10901 native control4 becomes EndSignControlWait85BD6; its child bit4 tail
  changes these five pointers to Delete_Current_Sprite1ABB6. They actually clear
  on10902. Native global music allocation therefore uses34, not10. Engine has
  already freed10 and creates SongFadeTransition10 at10901.
* At11021 engine control4 allocates sign15/stub21 while music10 is still occupied
  at that earlier control pass; music then retires10 in its own pass. Native
  sign10/stub15/music34 follow the same source allocation semantics with the
  correct earlier lifetimes. No allocator helper fault follows from this.
* At12001 native5–20 are occupied and X takes21/maxY23. Engine has a genuine
  free10 and takes X10/maxY23. Both X workers retire12335. The source-consistent
  minY22 transient is not directly visible at a completed-pass boundary.
* At12235 still-live engine X10 displaces static layout220192A/049A to11;
  native layout220 takes10. At12510 both bridge parents take9, while the actual
  display child takes engine10/native17. Monitor2651C08/04E0 reloads12967 in
  engine11/native10 because engine display10 still owns that hole.
* At13567 engine11 is monitor265 and12 is genuinely free. Hurt rings13568
  naturally take12 onward, versus native11 onward. The positive00C4 vector is
  actual engine13/phaseOffset96 versus native12/countdown97. At13739/13740 it
  remains1BB0/01EE with engine vyFE74 and is collected at13740, versus the
  existing native callback1BB0/04B9/vy0C34. Static258 now occupies23 in both;
  the animal repair corrected an earlier permutation without closing this one.

Source authority is own skdisasm044fa46725c71187399e13f5ddb70e11d32dc024:
body loc_686E8 at136974–136983; arm loc_68720 at136993–136995;
Child_Draw_Sprite2/Child_DrawTouch_Sprite2 at178173–178197 test parent `$38`
bit4, independently of parent status bit7. Go_Delete_Sprite_2 at179232–179235
installs Delete_Current_Sprite for the following dispatch. Obj_EndSignControl
at180463–180469 sets that bit4, after Wait_FadeToLevelMusic's global allocation
at179742–179759 (AllocateObject call179751). Native pointer/retirement events and
full slot_dump corroborate the source; the filtered aux does not itself expose
all parent `$38` writes. Line numbers refer to this pin only.

**Next bounded owner proposal, not an implemented fix:** one independent old-red
with production-allocated normal AIZ body/arm/three barrels through fatal poll,
literal64-pass wait, physical control replacement/bit4, and next-own-pass
retirement/free reuse; include whole-world forced recreation/forward replay and
independent music lifetime. Then, if authorized, correct only this normal AIZ
child tail using existing native-pending/physical-owner semantics. Body/arm/barrel
already have a captured nativeRetirementPending path for the cutscene; normal
boss linkage/recreation after physical replacement must be preserved rather than
introducing a generic dispatch/allocator contract. No slot assignment, phase tune,
new arbitrary wait, LostRing/touch/debris implementation or collision rewrite.
Further retirement/animation equivalence and matched replay closure are unproven.

Temporary observer source is restored exactly; R5 staging is preserved unchanged.
The journal/report facts above are retained here and in the existing Plan. Seven
owned files were consumed under the metadata slot alone: two Surefire reports,
three trace diagnostics, the journal and the compiled temporary alternate observer
class. No raw archive/receipt or rebuild. Remaining target contents last came from
9577 and are **not** claimed as a maintained build. All handles are terminal/leases
released. No R6 production change or JVM
beyond9577. R5 focused73/three controls remain their historical bounded results;
alternate11/tennewvs803 still block integration. Four native loc68E98 debris,
full aux/config/cold/BK2/wide/roster/fullchain limits remain open. Root owns the
next owner authorization, policy prerequisite and combined composition/validation.


R5 commit prerequisite is now resolved through Root's independently reviewed
policy-only ff9e4be617c3d16ab3bb1f2c23ad0893c10694e5 (39 actual shell/PowerShell
policy cases plus syntax/diff checks, supplied by Root; not rerun here). Its exact
three committed files were copied under the owned build lease and committed with
normal hooks using --only as b834344890603c1f3919c302be245ffadd863994. All eleven
R5 staged path/blob identities and both R6 worktree document contents were verified
unchanged afterward. Normal R5 commit3cce122ffcdb5e0c5371ed32f7a7158163b544c8
then preserved the tested animal hashes and passed hooks; its seven policy trailers
and final exact GPT-6.1 coauthor were parsed. No fabricated pin, API reshape,
stash/reset, bypass or extra Java. The policy owner notes a pre-existing generic
RHS-comma false positive outside this preset; that separate limitation was not
widened into this patch. R6 remains an evidence-only handback, not child-tail
implementation or integration clearance.


## R7 physical parent and five-child defeat retirement — 2026-10-17

Same owned tree98d/branch, base0eeacbf81bca0ee0d404f4d4b2cb94d54c61f43d;
R1–R6 identities/results remain historical. This unit closes the R6 demonstrated
five-child lifetime cause. Normal production body/arm/three barrels dispatch
from their own ordinary SSTs and retain a captured physical parent3 slot. They
survive parent status7 and all64 literal Wait_FadeToLevelMusic passes. The same
physical replacement publishes parent38 bit4; the five children run their own
routine, install pending Delete_Current, then clear on their following entry.
No old unregistered boss reference or inline parent dispatch remains in these five.

The replacement flow captures only actual x/y/render_flags/$38/defeated scalars,
with package-private reads and the literal barrel bclr1 writer. Public flow
constructors and other callers are unchanged when that projection is unset.
Its retained XY offsets independently of signpostX/getY0. FallingShot keeps its
registered barrel ObjectRef but captures the grandparent physical slot, so INIT/
routine work precedes status7 even when born on the final flag-observing pass.
One obsolete Napalm Java-parent schema-policy entry is removed; no schema,
allocator, generic boss dispatch, global hook or public API contract changes.
Unknown nonempty physical headers fail rather than inventing full native state.
Existing attack cleanup, music, explosion tail, scoring, physics/RNG and the four
omitted loc68E98 debris are not expanded into another owner unit.

Authority: own skdisasm044fa46725c71187399e13f5ddb70e11d32dc024,
sonic3k.asm loc_686E8 at136974–136983, loc_68720 at136993–136995,
loc_68C12 at137461–137467 and loc_68C64/68C84 at137496–137509.
Refresh_ChildPositionAdjusted copies native word XY/facing at177386–177412;
Child_Draw_Sprite2/Child_DrawTouch_Sprite2 test parent38 bit4 at178173–178197;
Go_Delete_Sprite_2 installs next-entry deletion at179232–179235.
Wait_FadeToLevelMusic/global music allocation and Obj_EndSignControl's bit4
write are179742–179759 and180463–180469. These line numbers belong to this pin.

Red-first production test42418:1/1F0E0S, four lifetime failures through fatal/
wait/pending, ordinary control17/children28–32, fatal183; no assigned expected
slots. A pre-existing lower free5 invalidated the first reuse assumption, so the
maintained assertion fills ordinary free holes until it actually reuses child28.
Beforefatal/livewait/pending/deleted world captures each force recreation and
forward replay twice. All five real AfterCurrent children are higher than the
control; no impossible lower-slot products were manufactured.

Independent exact-hash review then found two masked branches: barrel
`defeated→IDLE` suppressed callback/pair birth through the new live wait, and body
`defeated→frame2` froze Animate_Raw before the separate status7 touch gate.
Corrected Knuckles active old-red22347:1/1F0E0S, natural control11/fatal379,
body continuation and final delayed-barrel flare/FallingShot birth red. It
observes the natural SWING_PREP_COMPLETE wordzero before the final-hit stimulus;
no timer/routine/RNG/slot/trace seeding. The initial default-Sonic20704 did not
qualify barrel activation;9329 and91691 were owned test-compile prerequisite
failures, no executed-test claims. Both review findings are resolved in the
final runtime, independently read again at all ten exact hashes; no review pass
is substituted for execution. The existing Plan records those corrections.

| Execution / source epoch | Actual outcome | Limits |
| --- | --- | --- |
|32436 initial own-SST/header patch |14/0F0E0S,13:39:53UTC,24.866s/queue67s |Before the two review corrections |
|80769 remaining callers + S3K4 |193/0F0E0S,13:41:57UTC,31.229s/no wait |Same earlier epoch; unaffected controls reused, not a claim that207 all reran on final bytes |
|70775 affected16guard classes |118/1F0E0S,13:44:05UTC,1m37/no wait |Only terminology red: three owned dummy override names, renamed without behavior change |
|34345 final reviewed10-file runtime |65/0F0E0S,13:53:23UTC,28.184s/queue27s |Ten affected child/body/barrel/FallingShot/cutscene/graph classes; final-pass pair, header bclr1/pose/offset, twice-forced whole-world replay |
|31137 exact affected guards |2/0F0E0S,13:55:16UTC,1m19/queue7s |Terminology and changed field-policy guard only;116 other guard cases retained from70775 |
|99318 original + standing + twoSS |3/0F0E0S,13:56:45UTC,59.964s/no wait |Original20463rows physics/animation/warnings/bootstrap0;20 exact assertions; seg0/2/4 complete0errors, lag0/11/14;5dynamic-art gaps/0failures |
|72859 distinct original alternate |1/1F0E0S,13:58:19UTC,37.343s/queue29s |26041rows; only26179rings72/0; physics1/animation0/warnings/bootstrap0 |

All20 standing assertions retain actual SST12 at raw rows9510–9513,
FULL_LEVEL_FRAME; traceLF23F6–23F9, engineLF23F5–23F8, executed9206–9209.
The standing admission print still contains the old unbound EngineTiming0;
that is not object-clock evidence and is not used as such. R6 separately records
bound object VInt/LF/executed/input cursor evidence. No row↔BK2 offset is fitted.

**Ten new alternate ring spans13740–16669 are closed.** The sole remaining
one-row error is exactly field rings,26179..26179,expected72/actual0,
noncascading physics; it matches Root's published803 control78232. Root used
-Ptrace-replay and this worker -Ptrace-segments; the previously inspected POM
has identical runtime/fork settings and exact case execution, not literally
identical profiles. The raw alternate remains red; the earlier ten-new-span
integration blocker is resolved locally, while composed qualification belongs
to Root. This field result does not prove every later SST/phase/native position.

Final source SHA256: AizMinibossInstance
0e8ff2faee2f384ca6bce23e0c945f401775aac188a5af77974dbbb255affc7f;
Napalm6c55413936662423640dd60251150f36bdfa34bafb7e3d61bd2360f7053fb3ab;
shared flow3a4c5f9477ef23450416454f4e4fd1d3571733a7d2e8f29a5f2a7fb757b89e4a.
All ten runtime hashes matched independent review and stayed frozen queued/live.
The R5 animal/rules hashes and original BK2/ROM identities above are unchanged.
Use the R5 `r5_maven` template above for the actual normal Main absolute wrapper,
all three original absolute ROMs, exact single-token JAVA_TOOL_OPTIONS/EGL,
nullAL/noambient display, inherited JDK_JAVA_OPTIONS/MAVEN_OPTS and fork/reuse/order.
Executed selections (not requests to repeat):

```bash
r5_maven '-Dtest=TestAizMinibossChildRetirement'
r5_maven '-Dtest=TestAizMinibossChildRetirement#naturalActiveBarrelsAndBodyRunThroughFatalWaitAndTheFinalReplacementPass'
r5_maven '-Dtest=TestAizMinibossChildRetirement,TestAizEndSignControlReplacement,TestS3kAizMinibossGraphRewind,TestS3kBossDefeatSignpostFlow'
r5_maven '-Dtest=TestAizMinibossBarrelCounter39,TestAizMinibossBarrelShotChild,TestAizMinibossBarrelWaitCallback,TestAizMinibossCameraUnlock,TestAizMinibossCommonShotRetirement,TestAizMinibossCutsceneInstance,TestAizMinibossFallingShotNativeGraph,TestAizMinibossFlareNativeGraph,TestAizMinibossNapalmProductionRoute,TestAizMinibossNapalmProjectile,TestAizMinibossNapalmRoute,TestAizMinibossRegistry,TestAizMinibossSeamlessTransition,TestAizMinibossShotNativeGraph,TestAizMinibossShotNativeInit,TestAizMinibossSpecialExplosionOwner,TestAizCutsceneNativeTransition,TestAizAct2LevelSizeWorkers,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils'
r5_maven -Pguards '-Dtest=TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard,TestObjectPriorityBucketGuard,TestS3kRuntimeStateReadGuard,TestS3kTransitionBridgeGuard,TestS3kAizWriteBridgeGuard,TestRewindFieldDispositionGuard,TestRewindArchitectureGuard,TestParentDependentGraphCoverageGuard,TestRewindTransientGuard,TestConstructionContextGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayReferenceClosureGuard,TestTraceReplayInvariantGuard,TestRuntimeSingletonGuard'
r5_maven '-Dtest=TestAizMinibossChildRetirement,TestAizMinibossFallingShotNativeGraph,TestAizMinibossNapalmRoute,TestAizMinibossNapalmProductionRoute,TestAizMinibossNapalmProjectile,TestAizMinibossBarrelWaitCallback,TestAizMinibossBarrelCounter39,TestAizMinibossCutsceneInstance,TestAizCutsceneNativeTransition,TestS3kAizMinibossGraphRewind'
r5_maven -Pguards '-Dtest=TestObjectUpdateClockTerminologyGuard,TestRewindFieldDispositionGuard'
r5_maven -Ptrace-replay '-Dtest=TestS3kAizTraceReplay#normalBridgeStandingSstMatchesOriginalFullrun+replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix#twoSpecialStageReturnsThroughThirdEntry'
r5_maven -Ptrace-segments '-Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace'
```

No new native/media/ancestor/fullchain/broad execution. The four omitted native
loc68E98 debris, seven original/eight alternate missing advertised aux families,
full native SST/position/RNG comparison, allocation exhaustion, cold/otherBK2,
viewport/donor/roster/configuration and complete continuous act/chain coverage
remain open. No fixture/physics/aux data changed or gameplay hydrated. All reports
were inspected including skips and exact error arrays before metadata-only
consumption; no raw archives or temporary observer remain. Target last compiled
final maintained source/tests in72859, no unrebuilt source variant. Local owner
qualification only; Main803 unchanged, no integration/push/tree removal.


### Presentation smoke repair R5 — 2026-10-17

The obsolete results-wait test now enters the actual EndSignControl component
through `S3kBossDefeatSignpostFlow.beginNativeReplacement`, allowing ordinary
SST allocation, signpost landing, production results construction, art readiness
and tally. Native `loc_2DCC0` / `Obj_LevelResultsWait2` at pinned044 lines
62715–62730 own literal90. The maintained test observes first fallthrough1,
retention through89 and transition on90. It neither reflects the removed
`RESULTS_WAIT_DURATION_ADJUSTMENT` nor constructs the old13 compensation.
No AIZ runtime changed. The component inlet alone passed old-red job51248;
all16 results tests and17 Kos/child controls pass final3511, no skips. Existing
R7 fatal/physical-handoff world replays are reused at their recorded source
epoch; this test does not rerun or replace those route qualifications. Exact
commands, shared displayed-fade repair and limits are in the existing
[playable-completion Plan](../../plans/2026-10-13-s3k-playable-completion.md#presentation-rewind-smoke-repair-r5--2026-10-17).

### Flipping-bridge sound clock — 2026-10-20

Native `sub_2AA7E` at pinned skdisasm `044fa46725c71187399e13f5ddb70e11d32dc024`
reads the low byte of `Level_frame_counter` after an animation-timer expiry,
then requests `sfx_GlideLand` only when `((lowByte + 3) & 7) == 0` and the
display child is on screen. Both directions have the same gate. Original
locked-on ROM SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` corroborates
`move.b ($FE05).w,d0 / addq.b #3,d0 / andi.b #7,d0` at `$2AAB4/$2AADC`.
The address `+1` selects the word's low byte; it is not a counter increment.
The two actual AIZ2 records at ROM `$1E5422/$1E54DC` are flipped subtype `$52`
bridges at centres `($2C40,$0514)` and `($2E40,$05F4)`.

`AizFlippingBridgeObjectInstance` now reads the injected level manager's
already-incremented counter instead of the independent V-int argument. Only
components without a level manager retain their explicit input clock, matching
the nearby object idiom. Animation, visibility, collision and child-slot behavior
are unchanged; no persistent state is added.

On Root HEAD `691de5183` plus the composed uncommitted sources, one normal queued
focused request completed **8 tests, 0 failures, 0 errors, 0 skips, exit 0**.
Its selection was `TestAizFlippingBridgeRewind` (all seven cases) and
`TestCnzHoverFanObjectInstance#hoverpadSoundGatesOnTheLevelFrameCounterLowByte`
(one neighboring clock control). Maven reported 1m15s and queue wait 0s;
4154 main and 4225 test sources compiled inside that lifecycle. The all-source
freeze `ff368df0239d03611c5566436093f62b39a04cb718bcfb90c1f27bab77268340`
remained unchanged through execution. Bridge runtime/test SHA-256 values are
`56aaba17a99cfc3891e765ae15e5fa7f4ef3bd921a09f0f04f7eed289399879f` and
`8fc9e33d00b054a45e4841acf0d492dc35a1a5ab735b05c3cb977f5cfe69af5c`.

The new mocked-level component controls separate level `$0105`/V-int `$0200`
(emit) from level `$0104` or `$0106`/V-int `$0205` (silent), in both directions.
Timer and offscreen negatives, explicit-clock fallback and the three existing
recreation/codec/child-slot controls pass. The declared local dynamic-object
inlet does not establish actual placed-route admission. Audible PCM, natural
route cadence, retained display-child visibility, viewport/donor/team breadth
and complete-world replay restoring both level and object clocks remain
unqualified. This local source correction is not native audio or whole-act
certification; combined delivery and integration remain Root-owned.

## TAS mechanism accuracy — 2026-10-26

The initialized Knuckles raw slot is captured separately from typed animation references. Actual retirement/reuse, self-occupancy, empty/unarmed/other-occupant controls and rewind distinguish the button behavior. Level reset clears the slot; boss-sequence reset preserves it. The full published bridge allocation/camera history remains unexecuted. See the [repair record](../2026-10-26-tas-accuracy-repairs.md) for source identities, commands and limits.
