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
`Palette_cycle_counters` (sonic3k.constants.asm:580-596). Level's loc_60DE clears them
(sonic3k.asm:7652), but the seamless reload in AIZ1BGE_Finish does not reach it. The
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
