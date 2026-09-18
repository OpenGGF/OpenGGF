# S3K Death Egg Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-s3k-dez-bring-up` in `.worktrees/ai-s3k-dez-bring-up`;
execution base develop `9cba6dbb6` (pin this SHA for the combined change-based validation; re-pin
and re-measure if the branch is cut later). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to **Sonic 3 & Knuckles** Death Egg: `$B00`, `$B01` and
the final-boss act `$1700` (zone `$17` act 0). Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Inputs, all checked against
the disassembly on 2026-09-17: [placement inventory](../research/s3k-zones/dez-object-inventory.md)
(every placed ID and subtype with its current factory),
[reverse-gravity reference table](../research/s3k-zones/s3k-reverse-gravity-references.md) (all 116
ROM references with engine consumers) and the corrected
[dez-analysis.md](../research/s3k-zones/dez-analysis.md) (read its corrections box first; its line
numbers run about 5 low, so find labels, not numbers).

This is not Sonic 2 DEZ. The `*dez-boss-fixes*` and `cpz2-dez-trace-regressions` documents, and
`TestDEZ*`/`TestS2Dez*`, are Sonic 2. New S3K names carry an explicit prefix: `Sonic3kDEZEvents`,
`S3kDezZoneRuntimeState`, `S3kDez*ObjectInstance`, `TestS3kDez*`, matrices `s3k-dez-act1.md`,
`s3k-dez-act2.md`, `s3k-dez-final-boss.md`.

## Goal and delivery rule

Deliver S3K DEZ from cold `$B00` entry through the seamless `$B01` change, the act 2 boss, the
`$1700` final boss and the `loc_803D6` exit request (`$C00`, `$D01`, or `Game_mode 0`), with every
slice demonstrated on video and a final act-ordered highlights reel.

- Every feature or fix gets a short `GameplayCaptureTool` demo with at least 30 frames of lead-in
  and lead-out. Media live outside the repository in `~/Videos/OGGF/s3k-dez-bring-up/`: raw captures
  `raw-NN-*` (never overwritten), clips numbered by slice, `inputs/`, `native/`, `reel/`. Copy
  `make_clip.sh`, `side_by_side.sh` and the reel scripts from `~/Videos/OGGF/ddz-bring-up/`.
- A demo is not parity evidence. A "before" build disables only the demonstrated registration in an
  uncommitted edit, reverted and recompiled immediately (`git status` clean).
- Track five claims separately per matrix row: implemented, cold-reachable, rewind-verified,
  native behaviour matched, visually matched. No aggregate green label.
- Work stays on the local branch until the campaign is complete: **one develop merge at the end**
  (user decision 2026-09-17), no per-increment or per-act merges. Cold `$B00` → `$B01` boss →
  `$1700` load is an internal milestone, not a merge point. Reverse gravity ships with DEZ in that
  same merge (user decision 2026-09-17); its shared validation and S1/S2 non-regression still run
  before it.

## Rules for the implementer

Read before every slice. Each line is a past failure, not a style preference.

- **ROM, not fixtures.** Expected values come from a cited routine or table or an independent native
  capture, never from the Java under test, a trace row, a frame index or a fitted number. See each
  test fail for the right reason before implementing. Model `FixBugs = 0` and comment the branch.
- **Owners.** Objects use injected `services()`, never `getInstance()`. No zone-name or game-name
  checks in shared code: reverse gravity is gated by the flag alone (below). Gameplay tile edits go
  through `ZoneLayoutMutationPipeline`/`LevelMutationSurface`. Player position writes use
  `NativePositionOps` (`x_pos`/`y_pos` are centre coordinates). Name the ROM clock each gate reads.
- **Mod API.** `GameRules` and its rule records are `@com.openggf.game.ModApi`. Add no public member
  to any `@ModApi` type (grep both `@ModApi` and `@com.openggf.game.ModApi`); keep helpers in
  non-API classes. A surface change needs the descriptor, `ModApiVersion` and pins together and the
  user's explicit agreement; this campaign plans none.
- **Ratchets.** `GameLoop` (3072 effective lines) and `Engine.draw` (3 lines) are size-ratcheted:
  put logic in managers and run `-Pguards` before committing near them.
- **Rewind.** Every new object needs recreation (a probe constructor for `genericRecreate`) and
  captured state; zone state needs a `RewindSnapshottable` adapter and an entry in
  `currentRuntimeStateUsesThisEventInstance`; no transient object references across a restore.
- **Builds and tests.** `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=…" -Ds3k.rom.path=<absolute path> test`.
  Wrong ROM paths skip silently: read the skip count. Never build in another worktree, never edit
  the tree during a category run, never `git stash`, never `--no-verify`, start git chains with an
  explicit `cd`, check `git log -1` after each commit, all seven commit trailers.
- **Evidence.** One demo clip per feature or fix (≥ 30 frames lead-in and lead-out). Five separate
  claims per matrix row. Break every new comparison on purpose once. Gaps go to
  `docs/status/s3k-known-bugs.md`; the discrepancies file is intentional-only.
- **Names.** Everything new says S3K: `Sonic3kDEZEvents`, `S3kDez…`, `TestS3kDez…`. Sonic 2 has its own
  `DEZ` classes and tests; do not touch or reuse them.

## Scope decisions (HPZ/DDZ precedents)

| Question | Decision | Reason |
| --- | --- | --- |
| Cold entry | Level-select/direct `$B00` load. The SSZ → DEZ launch cutscene belongs to whichever of the SSZ and DEZ campaigns lands second | `usesLevelIntroPlayerRun()` already covers `$B00` (`Obj_LevelIntro_PlayerRun`, `loc_6986`). Never position past the intro |
| `$1700` entry | Through the `Obj_DEZEndBoss` exit (`Act3_flag`, `Act3_ring_count`, `Act3_timer`, `StartNewLevel $1700`), plus an independent direct `$1700` load for short checks | ROM level select lists `$1700` as "DDZ act 2" (`sonic3k.asm:10161`); the engine level select has no such entry (`Sonic3kLevelSelectConstants:96-97`). Adding it is in scope |
| Exit | Implement `loc_803D6` in full: `SaveGame`, then `$C00` when `Player_mode < 2` and `Chaos_emerald_count == 7`, else `$D01`, else (`Player_mode == 3`) `Game_mode 0` | Closes DDZ's recorded dependency. After it lands, DDZ's cold entry inherits the camera fraction and `V_int_run_count` from a real chain: re-measure and remove the DDZ seeded-entry caveat. `$D01` stays the ending campaign; record what the engine does after the request |
| Roster | Mandatory: Sonic + Tails, Sonic alone, Tails alone. Knuckles: level-select access to `$B00`/`$B01` only (user decision 2026-09-17): he must load and play both acts from level select with correct reverse-gravity behaviour, but no Knuckles story route advances into DEZ and no Knuckles cold-chain, `$1700` or trace obligation exists | `LaunchProfile.sanitizedFor` allows native S3K Knuckles and ROM level select does **not** deny Knuckles `$B00`/`$B01` (`LevelSelect_CheckKnuckles` denies `$A00`, `$C00`, `$1600`, `$1700`). His story never reaches DEZ, but the ROM carries Knuckles reverse-gravity code (glide, slide, wall climb). No Knuckles `$1700` row from level select; the chained `$1700` with Knuckles is recorded, not mandatory |
| Tails differences | Own rows | Post-act-change transport landing Y `$3B0` vs `$3AC` (`loc_7E44C`, act 2 coordinates; not the miniboss landing), `$D01` exit, flight and carry under reverse gravity (`Tails_Carry_Sonic`, `Tails_Test_For_Flight`) |
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from the first slice; donors per the level test standard | The `$1700` arena wraps `Camera_X & $1FF` and redraws planes from camera-relative words; bosses lock the camera. Gameplay geometry stays native |
| Traces | Strict replay late; movies supply cold routes and native states from slice 1 | v2 |

## Findings that change the plan

- **Trace directory names are shifted one zone.** Identify by `zone_id`/`act` (1-based), never by
  name. In `runs/s3k-sonic-tails-complete-emeralds`: `ssz` is **DEZ** (`zone_id 11`, start
  `$0030,$09AC`, 40,049 rows, `bk2_frame_offset` 468982, both acts and the exit handover);
  `dez23_8` is **`$1700`** (`zone_id 23`, act 1 = index 0, 5,181 rows, offset 509032); `hpz*` is
  SSZ; `dez23`..`dez23_7` are `$1701` sanctuary visits. Classes:
  `TestS3kSonicTailsSszSegmentTraceReplay` and `…Dez238SegmentTraceReplay`. The frontier log has a
  stale line calling `Dez238` "Hidden Palace proper"; its camera X `$80` and carried 163 rings are
  `DEZ3_ScreenInit`/`Act3_ring_count`. Correct it in slice 0.
- **Tails-alone native evidence exists and takes the `$D01` branch.**
  `runs/s3k-tails-full-chain-all-emeralds`: `ssz` (act 1, 23,249 rows, offset 444059), `ssz_2`
  and `ssz_3` (act 2, 5,202 and 3,877 rows: act 2 restarts, i.e. lifecycle evidence), `dez23_8`
  (5,550 rows), then `ddz` (`zone_id 13`) with no `zone_id 12`. The Sonic + Tails movie has seven
  emeralds and takes `$C00`. Only Sonic with fewer than seven emeralds needs seeded evidence.
- **Reverse gravity is a flag with no player physics.** `GameStateManager.reverseGravityActive`
  exists and is snapshotted, but is cleared only in `resetSession()`, **not** in `resetForLevel()`:
  the level-load clear is missing (ROM: `clearRAM Tails_CPU_interact,$100` covers the flag at
  `$F7C6`; the seamless act change does not clear it). Consumers that really branch on it today
  (verified by grep at `9cba6dbb6`): spring init swap, lost rings/`ObjectTerrainUtils`,
  `GlideWallGrabTerrain`, `TailsCarryController`, `PlayableHurtRadiusTransition`, Super Tails
  flickies, FBZ wire cage, and two Knuckles lines in `PlayableSpriteMovement`. `SolidObjectProvider`
  and `SidekickCpuController` do **not** (the latter only mentions it in a comment). Of the ROM's
  **116** references, 9 are covered, 6 partial (lost rings look mirrored the wrong way), 97 missing, 4 n/a: see the
  [reference table](../research/s3k-zones/s3k-reverse-gravity-references.md), which also states the
  model (velocity keeps its sign, position integration and probes invert). The references include: `MoveSprite_TestGravity(2)`, `Player_TouchFloor`, `Player_HitCeiling(AndWalls)`,
  `Tails_/Knux_DoLevelCollision`, `Sonic_/Tails_/Knux_Jump`, `Player_JumpFlipSet`, `Player_DoRoll`,
  `Sonic_Balance`, `*_RollSpeed`, `*_InputAcceleration_Path`, `Call_Player_AnglePos`,
  `ChooseChkFloorEdge`, `Player_Boundary_CheckBottom`, `MvSonicOnPtfm`, `SolidObject_cont`,
  `SolidObjectTopSloped_1P`, `Touch_Monitor`, `Obj_Spikes`, `Obj_DashDust`, `Obj_Tails_Tail`, all
  four shields, `Tails_Catch_Up_Flying`, `Tails_Check_Screen_Boundaries`, and the render flip
  (`eori.b #2,render_flags` after `Animate_Sonic`, `loc_10C62`). This is the campaign's largest
  shared change. One shipped bug must be preserved: `Tails_Test_For_Flight` (`loc_1515C`) negates
  the wrong register, so that adjustment is not inverted.
- **Only three objects write the flag:** `$58` switch (pressed solid, toggles 4 frames later), `$59`
  teleporter (subtype bit 7) and `$5B` swap (crossing, `render_flags` bit 0; it also zeroes the flag by name
  before its conditional set), plus the debug cheat and the boss's clearer object. **Every writer
  acts for Player 1 only** (`$58`: `d6 & $14`; `$5B`: `sub_49228` called for `Player_1`; `$59`:
  `cmpa.w #Player_1`), so Player 2 can never change gravity. `$5A` tube reads it; `$5C` hub, `$5F` room and `$61` puzzle
  never reference it. Act 1 places **no writer** (`$58/$59/$5B` are act 2 only): establish in slice
  3 whether gravity ever reverses in act 1 (kill condition: native flag watch over the act 1 part of
  the DEZ segment). If it does not, act 1's `$5A`/`$5F`/`$61` are plain movers there.
- **The act 2 boss is a gravity boss.** `Obj_DEZEndBoss` reads the flag (`sub_7F8A0` inverts its
  `$38` acceleration and integrates normally, an exception to the player model; `sub_7F8CA`). At
  **defeat** `loc_7FBD6` spawns a persistent object `loc_7FC3E` that clears the flag every frame
  until the `$1700` load; it is not an exit hook. Slice order follows.
- **No S3K DEZ production code beyond loading.** Present: level data and music
  (`Sonic3kZoneRegistry` 11 and 23), `$B00` intro run, slope-angle rule
  (`Sonic3kZoneFeatureProvider:63`), results-screen DEZ title-card exception, PLC art for
  Spikebonker, Chainspike, still sprites `$30-$32` and the DEZ door (`addDezEntries`),
  `FbzDezPlayerLauncher` (`$78`), door (`$3C`), shared springs/spikes/monitors/starposts. Absent by
  name and by role: `Sonic3kDEZEvents`, any runtime state, any scroll handler (both zones fall to
  the default; **`$1700` currently gets `hpzHandler`** because the provider keys zone `$17` without
  the act), `AnPal_DEZ1/2`, `AniPLC_DEZ`, every SKL object `$4A-$61`, badniks `$A4/$A5` (the
  S3KL ids are Sparkle/Batbot), `$A6/$A7` bosses, `Obj_DEZ3_Boss`, the `$1700` resource profile
  (only `$1701` has one), `InvisibleShockBlock` (`$6D` SKL: confirmed placeholder, 78 placements), `$5D-$61` (no factory at
  all), level-select `$1700`.
  No `TestS3kDez*` and no matrix; coverage backlog rows are "Audit pending".
- **Placements** (decoded from `Levels/DEZ/Object Pos`): act 1 has 365 objects, act 2 has 494,
  `$1700` none. Gravity switch `$58`, teleporter `$59`, gravity swap `$5B`, hub `$5C`, retracting
  spring `$5D` and floating platform `$4A` are act 2 only; hover machine `$5E`, gravity room
  `$5F`, bumper wall `$60`, puzzle `$61`, lift pad `$4E` and launcher `$78` are act 1 only.
  (An earlier draft said act 1 flips gravity through `$5A` and `$5F`; the disassembly shows neither
  writes the flag, see above.) Lightning `$52` (48/94), shock blocks `$6D` (22/56) and torpedo
  launchers `$4D` (36/38) dominate. Rings: 278 / 198 / 0. Both bosses (`$A6`, `$A7`) are placed
  objects. Factory state: 297 placements already concrete, 526 behind S3KL-only factories, 36 with
  no factory at all (`$5D-$61`). Full table: [inventory](../research/s3k-zones/dez-object-inventory.md).
- **The act 1 → 2 signal is the results object (resolved).** ROM: `Obj_LevelResultsCreate` sets
  `Events_fg_5` for every act 1 except AIZ and ICZ (sonic3k.asm:62615-62621). Engine:
  `S3kResultsScreenObjectInstance.signalActTransitionIfNeeded` already calls
  `S3kTransitionWriteSupport.signalActTransition` for DEZ; only the consumer (`Sonic3kDEZEvents`) is
  missing.
- **`Events_fg_4` has three writers (resolved from the disassembly).** Write 1 (`loc_7DFB8`,
  installed by `loc_7EE42` when the hit counter `$42` reaches 8) fires mid-fight in act 1 →
  `DEZ1_ScreenEvent` chunk `$BD`. Write 2 (`loc_7E342`) fires after results **and after the act
  change**: its cutscene walks Player 1 to x `$140` and places explosions at `$100/$180,$760`, act 2
  coordinates → `DEZ2_ScreenEvent` stage 0 (`$D7,$DC,$D7`). `loc_593EC` leaves `Events_routine_fg`
  at 0, which is why stage 0 is reachable only seamlessly (a direct load starts at stage 1). Write
  3 is the act 2 boss (asm 169742) → stage 1 (`$BC`). The native log in slice 7 confirms, not decides.
- **The miniboss object outlives act 1.** After write 2 its helper chain `loc_7E3EC` → `loc_7E420` →
  `loc_7E44C` carries Player 1 up (`y_vel −$1000`), lands them at Y `$3AC` (`$3B0` when
  `Player_mode == 2`), spawns the act 2 `Obj_TitleCard` (`$3E` set) and 120 frames later
  (`loc_7E4A2`) loads `Pal_DEZMiniboss2` to line 2 and releases control. All of this is slice 7.
- **`AniPLC_DEZ` has no trigger gating.** The first `zoneanimdecl` field is the frame duration, not
  a `Level_trigger_array` index (the analysis was wrong; corrected). All eight scripts always run
  through the generic `AnimateTiles_DoAniPLC`; script 7 really has 132 one-byte frames. `$1700` uses
  `AnimateTiles_NULL`.
- **The `$1700` carry is three values plus the shield.** `loc_7F310` saves `Act3_ring_count`,
  `Act3_timer` and `Saved2_status_secondary` (shield bits) before `StartNewLevel $1700`; `Act3_flag`
  also suppresses the title card (`loc_62B6`) and is cleared at `loc_62FE`.

## Design: who owns what

Resolve these owners before any consumer.

| State | ROM | Engine owner |
| --- | --- | --- |
| `Reverse_gravity_flag` | Global byte `$F7C6` (`$F768` is `Primary_Angle`; fix the `GameStateManager` Javadoc). Written by `$58`, `$59`, `$5B` (all Player 1 only), the debug cheat and the boss-defeat clearer object `loc_7FC3E` (every frame until the `$1700` load); cleared by the level-load RAM wipe, not by the seamless act change | Stays in `GameStateManager` (already rewound). All writers use `setReverseGravityActive`; add the level-load clear to `resetForLevel()` only after checking that `LevelActTransitionExecutor:117` is not the seamless DEZ path. **The gate is the flag itself**, exactly as in the ROM: no zone check and **no new `GameRules` member** (`GameRules` is `@ModApi`; a new component breaks the pin). S1/S2 never set it, so their branches stay inert |
| Inverted player physics | The 116-reference set above | `PlayableSpriteMovement`, collision probes and `AbstractPlayableSprite` render flip, each branch citing its routine and commented for `FixBugs = 0`. Position writes through `NativePositionOps`. One owner for "effective floor sensor direction" so ground, air, roll and Knuckles glide/climb read the same answer |
| Event words `Events_fg_4/5`, `Events_routine_fg/bg`, `Events_bg+$00..$16`, `Act3_*` | `DEZ1/2/3_Screen/BackgroundEvent`, `Obj_DEZEndBoss`, `Obj_DEZ3_Boss` | New `S3kDezZoneRuntimeState` (pattern `DdzZoneRuntimeState`) with a `RewindSnapshottable` adapter, added to `currentRuntimeStateUsesThisEventInstance` (the DDZ restore bug). `Act3_*` carry is a game-state field, not zone state: it crosses a level load |
| Events, camera locks, seamless act change | `DEZ1_BackgroundEvent` stages 0-1 (queue DEZ2 blocks/patterns, PLC `$38`, wait `Kos_modules_left == 0`, reload `$B01`, offset X −`$3600` Y +`$400`, `Offset_ObjectsDuringTransition`, `Pal_DEZ2+$20`), boss gradual bounds | New `Sonic3kDEZEvents`, registered in `Sonic3kLevelEventManager`; reuse the AIZ/ICZ/MHZ seamless-transition owner and `S3kCameraGradualObjectInstance`/`S3kCameraStoredBounds` |
| Layout mutation | DEZ1 `$BD` at `$14(a3)+$6E`; DEZ2 `$D7,$DC,$D7` and `$BC`; `$1700` chunk pairs `$603/$903/$603/$807`, BG layout clear | `ZoneLayoutMutationPipeline`/`LevelMutationSurface`, then targeted redraw. A full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes |
| Scroll | `PlainDeformation` with BG camera forced to 0,0 (acts 1-2; `SwScrlS3kDefault` scrolls the BG at 1/4 speed on both axes, so it is wrong here); `$1700`: `sub_5A508`, `sub_5A76C`, `loc_5A734`, `ApplyDeformation2`, `ShakeScreen_Setup`, FG drawn through the BG event, `Draw_PlaneVertBottomUp` stages | New `SwScrlS3kDez` (acts 1-2) and `SwScrlS3kDezFinalBoss`; provider keys `$17` on act. Plane swap reuses the DDZ/FBZ2 FG-plane render mode, no `@ModApi` change |
| Palette | `AnPal_DEZ1` (3 channels) / `AnPal_DEZ2` (2), boss lines `Pal_DEZMiniboss1/2`, `Pal_DEZEndBoss`, `$1700` palette `$40`, boss flashes | `Sonic3kPaletteCycler` cases for `$0B` and `$17` act 0; boss writes through `S3kPaletteOwners`/`S3kPaletteWriteSupport` so cycles and flashes do not fight over line 2/3 |
| Animated art | `AniPLC_DEZ`: 8 always-running scripts, global durations `0,1,3,-1,4,4,1,0` (frame held duration + 1 passes; `-1` = per-frame pairs), script 7 = 132 one-byte frames; `$1700`: `AnimateTiles_NULL` plus the laser DMA `sub_5A79E` (`ArtUnc_DEZFBLaser` → tile `$208`) | `Sonic3kPatternAnimator` registration for zone `$0B` both acts, no trigger reader; the laser is a direct upload owned by the final-boss events |
| Light tunnels, teleporters, tubes, hover machine | `Obj_DEZTunnelLauncher/Control`, `DEZTunnelPaths`, `Obj_DEZTeleporter`, `Obj_DEZGravityTube`, `Obj_DEZHoverMachine` | Objects using the generic `object_control` path and `services()`; path tables read from ROM. Compare with `AutomaticTunnelObjectInstance` and `SSZHPZTeleporterObjectInstance` before writing new movers |
| Object registration | SKL pointer set | Zone-set-aware factories as SOZ/HPZ did; `$A4-$A7` and `$6D` collide with S3KL names |

Rules binding every slice: `GameRules`/providers, never zone-name carve-outs in shared code; objects
use `services()`; each gate names the ROM clock it reads; constants cite their routine (`loc_`
labels are addresses); nothing keys on a fixture, frame or fitted value; trace rows never hydrate
gameplay. `GameLoop` and `Engine.draw` are size-ratcheted: run `-Pguards` before committing there.
Grep both `@ModApi` spellings before adding a public member anywhere near player or render types.

## Dependency-ordered slices

Each slice: reverify its inventory rows in the disassembly → discriminating failing test with
ROM-derived expectations → implementation → cold-route extension with preserved inputs → short
native comparison on a named question → 320 + wide + donor + team check → rewind spot → demo clip →
boundary review → evidence entry. Independent review for 1, 2, 5, 7, 8, 9, 10; families 3, 4, 6
share one each. Order is a dependency order: 2 before 3, 3 before the act 2 parts of 4 and before 8;
1 before 7 and 9; 4-6 before the cold act 1 route; 7 before the cold act 2 route.

Paths below are under `src/main/java/com/openggf/game/sonic3k/` (`…/`) and
`src/test/java/com/openggf/tests/` (tests). "Rows" are
[inventory](../research/s3k-zones/dez-object-inventory.md) IDs with act 1 / act 2 counts; every
placed ID appears in exactly one slice or is marked verify-only. "Done" lists what the slice must
show for each of the five claims; a claim it cannot reach is recorded as open, never implied.

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 0. Baseline and identity | Matrices, coverage rows, trace identity, replay frontiers, resources, `raw-00` captures | The "before" footage exists first |
| 1. Presentation foundation | Runtime state, events shell, scroll, `AnPal_DEZ1/2`, `AniPLC_DEZ`, screen-event chunk writes | Counter/step/limit values from the tables; wide-viewport BG edges |
| 2. Reverse gravity core | Reference-table groups A-I in steps 2a-1, 2a-2, 2a-3, 2b, 2c | Inert with the flag clear; S1/S2/S3K trace non-regression is the gate |
| 3. Gravity objects | `$58 $59 $5A $5B $5C $5F $61` | Writers respond to Player 1 only; Player 2 inverts with the global flag wherever it is |
| 4. Traversal objects | `$4A-$56`, `$5D`, `$5E`, `$60`, `$6D`, badniks, shared-object verification | Per-act art bases; child slot/allocator order |
| 5. Light tunnels | `$57`, tunnel control, paths, trans rings | Path fixed-point; sidekick capture; rewind mid-tunnel |
| 6. Act 1 cold route and miniboss | Cold `$B00`, `$A6` | Sprite composition (mask child); palette line ownership |
| 7. Seamless `$B00` → `$B01` | `DEZ1_BackgroundEvent`, `DEZ2_*Event` transition-only stages | Flag and objects survive; timeline isolation |
| 8. Act 2 route and boss | Cold `$B01`, `$A7`, `$1700` request | Boss under both gravity states; carry across the load |
| 9. `$1700` arena | Resource profile, scroll/plane, `DEZ3_ScreenInit` spawns, BG stages | Plane redraw vs retained history; `$1FF` wrap when wide |
| 10. Final boss and exit | `Obj_DEZ3_Boss`, chase, `loc_803D6` | Longest coupled graph; per-branch exit evidence |
| 11. Routes and acceptance | Cold chains, matrix breadth, rewind spots, strict replay, DDZ re-measure | Positioned success does not advance the cold frontier |
| 12. Media and delivery | Reel, validation, docs, the single develop merge | Below |

### Slice work cards

**0. Baseline and identity.** Skills: `s3k-zone-bring-up`, `gameplay-capture`.
Files: `docs/architecture/validation/levels/s3k-dez-act1.md`, `s3k-dez-act2.md`,
`s3k-dez-final-boss.md`; `docs/status/level-test-coverage.md` (`S3K_DEATH_EGG_1/2`, `S3K_DEZ_BOSS`
rows); `docs/status/trace-frontier-log.md` (identity table, the stale `Dez238` line, first-error
frame/field of `TestS3kSonicTailsSszSegmentTraceReplay`, `…Dez238…`,
`TestS3kTailsFullChainSsz/Ssz2/Ssz3/Dez238SegmentTraceReplay` with `-Ptrace-segments`).
Check: which class `$78` resolves to (duplicate registration); what `$1700` loads without a resource
profile; `Pal_DEZ1/2`, PLC `$36/$38/$4C`, `PLCKosM_DEZ`, title cards, music. No production code.
Done: three matrices with every row "not started" and its products; `raw-00-*` for all three acts.

**1. Presentation foundation.** Skills: `s3k-zone-events`, `s3k-parallax`, `s3k-palette-cycling`,
`s3k-animated-tiles`. Rows: none placed. Files: new `…/runtime/S3kDezZoneRuntimeState.java`,
`…/events/Sonic3kDEZEvents.java` (register in `Sonic3kLevelEventManager`, including the
`currentRuntimeStateUsesThisEventInstance` switch near line 1650), `…/scroll/SwScrlS3kDez.java`
(+ `Sonic3kScrollHandlerProvider`, act-keyed for `$17`; see the cross-campaign note),
`Sonic3kPaletteCycler` cases, `Sonic3kPatternAnimator` registration. First failing tests:
`TestS3kDezScrollHeadless` (BG H/V scroll words are 0 at two camera positions: `DEZ1_BackgroundInit`
clears `Camera_X/Y_pos_BG_copy` and nothing rewrites them; today `SwScrlS3kDefault` gives camera/4),
`TestS3kDezPaletteCycling` (counters, steps, limits and colour words from `AnPal_DEZ1/2`, act 1
running channel 0 then act 2's channels), `TestS3kDezAnimatedTiles` (destination tiles, tiles per
frame, durations `0,1,3,-1,4,4,1,0`, script 7's 132-entry order, from `AniPLC_DEZ`),
`TestS3kDezScreenEvents` (`Events_fg_4` → chunk `$BD` at layout row `$14(a3)` offset `$6E`; act 2
direct load starts at stage 1). Done: implemented + focused tests; native BG and palette compared at
entry; moving 320 and wide capture inspected; rewind of cycle counters and event routine words.

**2. Reverse gravity core.** Skills: `s3k-disasm-guide`; read
[implementation pitfalls](../implementation-pitfalls.md) (collision, rewind). Rows: exercises
`$01` (19/22, 9 Y-flipped in act 2), `$07` (20/15), `$08` (31/60) among shared objects. Work and
tests are defined row by row in the
[reference table](../research/s3k-zones/s3k-reverse-gravity-references.md#implementation-order-and-the-test-that-proves-each-step):
step **2a-1** group A integration (`MoveSprite_TestGravity(2)`, `sub_F61C`); **2a-2** group A
probes/angle/boundary plus every collision, touch-floor, hurt and death row of groups B, C, E;
**2a-3** the action rows of B, C, E (jump, roll, spindash, look bias, render mirror, bubble bounce,
the preserved `loc_1515C` bug, `loc_15A7A`); **2b** groups H, I, G, F; **2c** group D, Knuckles
glide/slide/climb, Super forms. Also: the level-load clear in `GameStateManager.resetForLevel()` and
the Javadoc address fix. Files: `sprites/managers/PlayableSpriteMovement.java`, the collision probe
owners it calls (one new non-API helper owns "which probe is the floor" and the angle mirror, so
ground, air, roll and Knuckles code share one answer), `AbstractPlayableSprite` render path,
`SolidObject` contact code, `Sonic3kSpringObjectInstance`, `Sonic3kSpikeObjectInstance`,
`Sonic3kMonitorObjectInstance`, shields, dash dust, Tails' tails, `SidekickCpuController`,
`TailsCarryController`. No `GameRules` change. Gate: tests drive the flag with
`setReverseGravityActive(true)` in an existing S3K level; each step is reviewed independently; after
2c run the normal change-based validation and matched before/after S1, S2 and S3K trace profiles
(clean build first; measure the baseline in the same tree). Done: implemented + rewind with the flag
set; "cold-reachable" and native claims stay open until slices 3 and 8 provide a ROM route.

**3. Gravity objects.** Skill: `s3k-implement-object`. Rows: `$58` (0/5), `$59` (0/21), `$5A`
(24/17), `$5B` (0/11), `$5C` (0/3), `$5F` (1/0), `$61` (1/0). Files: `…/objects/S3kDezGravitySwitchObjectInstance`,
`S3kDezTeleporterObjectInstance`, `S3kDezGravityTubeObjectInstance`, `S3kDezGravitySwapObjectInstance`,
`S3kDezGravityHubObjectInstance`, `S3kDezGravityRoomObjectInstance`, `S3kDezGravityPuzzleObjectInstance`;
SKL-bound registrations in `Sonic3kObjectRegistry`, constants in `Sonic3kObjectIds`,
`Sonic3kObjectProfile` sets, `Sonic3kPlcArtRegistry`/art keys. First failing test:
`TestS3kDezGravityObjectsHeadless`: `$5B` sets on one crossing direction and clears on the other,
selected by `render_flags` bit 0 (`sub_49228`), ignores debug placement and **ignores Player 2**; `$58` toggles exactly 4
frames after a top or bottom press and rearms after 20 (`loc_48AD6`/`loc_48B7E`); `$59` writes
subtype bit 7 for Player 1 only (`loc_48DCA`); `$5A` mirrors `flip_angle` on exit (`loc_48FBA`).
Done: all seven concrete; act 2 cold route reaches its first flip; native comparison of the first
flip's same-frame order (flag, velocity, probe swap, mirror, sidekick); rewind mid-tube and
mid-teleport; act 1 "no writer" question closed from the native flag watch.

**4. Traversal objects.** Skills: `s3k-implement-object`, `s3k-plc-system`. Rows: `$4A` (0/10),
`$4B` (1/3), `$4C` (3/1), `$4D` (36/38), `$4E` (7/0), `$4F` (18/15), `$50` (8/5), `$52` (48/94),
`$53` (4/5, reads the flag), `$55` (13/12), `$56` (1/0), `$5D` (0/13), `$5E` (11/0), `$60` (10/0),
`$6D` (22/56), `$A4` (7/11), `$A5` (6/12). Verify-only, already concrete: `$2F` (19/20, DEZ frames),
`$3C` (11/6), `$78` (10/0), `$02` (1/0), `$28` (26/24), `$34` (3/4), `$6A` (0/1), `$6B` (0/5).
Files: one `…/objects/S3kDez<Name>ObjectInstance` per owner, badniks under `…/objects/badniks/`
(`SpikebonkerBadnikInstance`, `ChainspikeBadnikInstance`, SKL-bound: S3KL `$A4/$A5` are
Sparkle/Batbot); `$6D` as the existing hurt-block class with a shield-reaction parameter (lightning,
`bset #5,shield_reaction`), shared with LRZ's `$6E` (fire, bit 4). Order by placement weight:
`$52`, `$6D`, `$4D`, `$4F`, `$55`, then the rest. First failing test per family
(`TestS3kDez<Name>Headless`) takes sizes, timers, velocities and art bases from the owner routine;
for `$6D`: lightning shield immune, other shields hurt, face chosen by `status` bits 0/1. Done: zero
placeholders for both acts (assert with the `Sonic3kObjectProfile` guard and a DEZ registry test);
children listed in the inventory's dynamic table accounted for; per-act art keys (PLC `$36` vs `$38`).

**5. Light tunnels.** Skill: `s3k-implement-object`. Rows: `$57` (3/4); dynamic
`Obj_DEZTunnelControl`, `Obj_DEZTransRingSpawner`, `Obj_DEZTransRing`. Files:
`…/objects/S3kDezTunnelLauncherObjectInstance`, `S3kDezTunnelControlObjectInstance`,
`S3kDezTransRingObjectInstance`; path tables read from ROM (`DEZTunnelPaths`,
`DEZTunnelControl_ScaleFactors`, `_WaitTimers`). Compare with `AutomaticTunnelObjectInstance` first.
First failing test: `TestS3kDezLightTunnelHeadless` (countdown length, per-mode position sequence
for Normal, CircleLarge, CircleSmall, SineDown, SineUp, release velocity, from the control routine).
Done: all seven placements traversable cold; sidekick capture; rewind mid-tunnel; native cadence per mode.

**6. Act 1 cold route and miniboss.** Skills: `s3k-implement-boss`, `bk2-input-authoring`,
`gameplay-capture`. Rows: `$A6` (1/0, placed; `Check_CameraInRange word_7DDA4`). Files:
`…/objects/bosses/S3kDezMinibossInstance` and children (inventory dynamic table, including the
`Obj_SpriteMask` child), `Pal_DEZMiniboss1/2` through `S3kPaletteOwners`, PLC `$7B`,
`ArtKosM_DEZMinibossMisc`. First failing test: `TestS3kDezMinibossHeadless` (arena bounds, landing Y
hit count 8 → `loc_7EE42` installs `loc_7DFB8` and `Events_fg_4` write 1, gradual Y
bounds, `Obj_EndSignControl`). Cold route: `TestS3kDezColdRoutes` from the Sonic + Tails movie input
(`--input-start` 468982) and the Tails movie (444059); record the first blocker per roster.
Done: cold `$B00` entry → results for Sonic + Tails and Tails alone; sprite-composition audit.

**7. Seamless `$B00` → `$B01`.** Skill: `s3k-zone-events`. Files: `Sonic3kDEZEvents`, the shared
seamless owner (`S3kSeamlessMutationExecutor`, `S3kTransitionWriteSupport`). First failing test:
`TestS3kDezActTransitionHeadless`: signal → queue `DEZ2_16x16_Secondary_Kos`, `ArtKosM_DEZ2_Secondary`
at tile `$292`, PLC `$38` → wait `Kos_modules_left == 0` → zone/act `$B01`, players, objects and
camera (position, copy, min/max, target max Y) offset X −`$3600`, Y +`$400`, `Pal_DEZ2+$20` to
lines 3-4, `Boss_flag`/`Respawn_table_keep` cleared, `Reverse_gravity_flag` **kept**; afterwards
`DEZ2_ScreenEvent` is at stage 0 and `DEZ2_BackgroundEvent` redraws bottom-up (stages 0-1), while a
direct `$B01` load starts at stages 1 and 2. Then the surviving miniboss chain: `Events_fg_4`
write 2 (`loc_7E342`) → stage 0 chunks `$D7,$DC,$D7`, transport landing Y `$3AC`/`$3B0` by
`Player_mode` (`loc_7E44C`), act 2 title card, `Pal_DEZMiniboss2` and control release 120 frames
later. Done: continuous scenery on a moving capture;
timeline isolation if the reload clears history; native frame counts between signal and offset.

**8. Act 2 route and boss.** Skill: `s3k-implement-boss`. Rows: `$A7` (0/1, placed; range
`word_7F0BE`, arena `word_7F0C6`). Files: `…/objects/bosses/S3kDezEndBossInstance` and children, PLC
`$76`, `ArtKosM_DEZEndBoss`, `Pal_DEZEndBoss`; the **shared act-3 carry owner** (game state, not zone
state; see Cross-campaign coordination: build it here unless LRZ already landed it, then reuse it)
including `Saved2_status_secondary`. First failing test: `TestS3kDezAct2BossHeadless` (8
hits, six routine-table entries with four distinct handlers, `sub_7F8A0` acceleration sign and
`sub_7F8CA` inverted test under both flag states, the `loc_7FC3E` clearer object from defeat
(`loc_7FBD6`) undoing a later `$5B` write on its next update, `Events_fg_4` → chunk `$BC`, `Obj_IncLevEndXGradual $3620`, request when
`Player_1 x ≥ Camera_X + $160`, saved rings/timer/shield). Done: cold `$B01` → `$1700` request for
both mandatory rosters; Knuckles level-select rows for both acts (load, play, invert, glide/climb
inverted; no cold chain); rewind across the exit.

**9. `$1700` arena.** Skills: `s3k-zone-events`, `s3k-parallax`, `s3k-animated-tiles`. Files:
`$1700` resource profile beside the `$1701` one, `…/scroll/SwScrlS3kDezFinalBoss.java`, level-select
entry in `Sonic3kLevelSelectConstants` (ROM lists `$1700` after `$C00`; Knuckles denied),
`Sonic3kDEZEvents` act-3 part, `…/objects/S3kDezArenaFloorObjectInstance` (`Obj_5A7C8`, falling
blocks `Obj_5A872`), `Obj_5A8E6`, `Obj_5A922`, `Obj_5A94C`. First failing test:
`TestS3kDezFinalArenaHeadless`: camera X `$80`, scroll lock, `Events_bg+$00 = $6C0`, boss spawn
`$3C0,$F8`, rings/timer/shield restored, no title card (`Act3_flag`), BG layout cleared, stub boss
drives `$6C0 → $2C0 → $6C0 → 0` (Verified ROM values), players placed by `loc_7FD9E`; stage 0
restores rings/timer through the shared act-3 carry owner. Done: arena stages against native plane captures; wide viewport vs the
`$1FF` wrap recorded as a presentation decision; laser DMA phase.

**10. Final boss and exit.** Skill: `s3k-implement-boss`. Files: `…/objects/bosses/S3kDezFinalBossInstance`
and children (dynamic table), fireball, `loc_803D6` exit. First failing test:
`TestS3kDezFinalBossHeadless` per phase, then `TestS3kDezExitBranches`: `SaveGame`, `$C00` when
`Player_mode < 2` and 7 Chaos Emeralds, else `$D01`, `Player_mode == 3` → `Game_mode 0`. Done: cold
Sonic + Tails → `$C00` (native), Tails → `$D01` (native), seeded Sonic < 7 emeralds → `$D01`
(labelled seeded); a rewind spot per phase; post-request engine behaviour recorded.

**11. Routes and acceptance.** Skills: `gameplay-capture`, `trace-replay-bug-fixing`,
`bizhawk-native-reference-capture`. Cold chains `$B00` → exit for Sonic + Tails, Tails alone,
authored Sonic alone; matrix breadth (widths, donors, teams); every rewind spot; strict replay
frontiers logged; DDZ entry re-measured from the real chain and its seeded caveat removed or kept
with evidence. Skip movie input on repeated native `lfc`; capture runs one frame behind the fixture.

**12. Media and delivery.** Skill: `gameplay-highlights`. Reel, archive index, combined validation,
docs, then the single develop merge.

## Native probes

Question-led, disassembly first, BizHawk 2.11 through the shared capture host with an S3K DEZ
exporter modelled on `tools/bizhawk/capture_ddz_route_reference.lua` (output to `OGGF_OUT` only, no
`print()`, keep the `plan.slots` slot-history option). Pass 1 saves states near every window from
both movies; later probes load them. Record ROM SHA-1, movie SHA-256, host exit code and the
probe's own error status (a failing Lua probe exits 0). Windows are located in pass 1 by state, not
by guessed rows:

| State | Locator | Question |
| --- | --- | --- |
| `$B00` entry | segment row 0 | Intro run release frame, first-frame art/palette, AnPal phases |
| First gravity flip | first `Reverse_gravity_flag` (`$F7C6`) 0 → 1 anywhere in the DEZ segment; also report whether it ever changes before the `$B01` handover | Same-frame order of flag write, velocity, sensor swap and render flip; sidekick lag; closes the "act 1 has no writer" question |
| Light tunnel | first `object_control` capture by `$57` | Per-mode path cadence, release velocity |
| Miniboss | `Boss_flag` set in act 1 | `Events_fg_4` write 1 frame, palette swap frame, defeat → results |
| Act change | `Current_zone_and_act` `$B00` → `$B01` | Frames between the signal, `Kos_modules_left == 0` and the offset; plane rebuild; then `Events_fg_4` write 2, transport landing Y per character, title card and control release |
| Act 2 boss | `Boss_flag` set in act 2 | Gravity cadence, flag clear on exit, `$1700` request frame, carried rings/timer |
| `$1700` entry and each arena shrink | `Events_bg+$00` changes | Plane redraw stages, wrap, shake offset, laser tile phase |
| Chase and exit | `Obj_DEZ3_Boss` routine changes; `loc_803D6` | Chase camera, save, request frame and destination per movie |

Choose fields and intervals before looking at engine output. Break each new comparison on purpose
once. The flag itself must be added to the exporter; check whether V5 aux rows already carry it
before adding a trace field (no new trace contract in this campaign).

## Demo and reel plan

Clips (renumber as needed): `00` baselines for all three acts; `01` act 1 load, intro run, palette
cycles and animated tiles before/after; `02a-c` reverse gravity with the debug toggle: run, jump,
roll, slope, spring, monitor, ring scatter, shields; `02d` Tails flight and carry inverted; `03`
each gravity object; `04a..` one clip per traversal object family; `05` light tunnel per mode;
`06` miniboss arrival, fight, defeat; `07` seamless act change (continuous scenery); `08a` act 2
gravity route, `08b` boss under both gravity states, `08c` defeat, Robotnik run and `$1700`
request; `09a` arena entry with restored rings, `09b` each shrink and wrap, `09c` laser; `10a-d`
final boss phases, chase, exit per branch; `20/21/22` uncut cold routes (Sonic + Tails, Tails,
Sonic alone); one wide-viewport route. Captures have no audio: SFX/music claims are test-backed.

Reel (`gameplay-highlights`, scripts copied from DDZ): act order, bosses and exits at their route
positions, one example per feature, delivered revision only, recorded speed, nearest-neighbour
integer scaling, labels stating positioned vs cold. Re-read state CSVs after every route change.

## Acceptance matrix (seed)

`s3k-dez-act1.md`, `s3k-dez-act2.md`, `s3k-dez-final-boss.md`. Rows: load/identity, title card,
intro run, scroll, each AnPal channel, each AniPLC script (duration and frame order), each screen-event chunk
write, reverse gravity (one row per ROM routine family in slice 2, per character), each gravity
object, each traversal object and badnik, light tunnel per mode, checkpoint respawn under each
gravity state (the flag clears at load: assert the respawn side), death/restart, miniboss,
results, seamless change, act 2 boss, `Act3_*` carry, arena entry, shrink stages, wrap, shake,
laser, final boss per phase, chase, exit per branch, SSZ entry (blocked or delivered), `$D01`
(blocked). Columns: the five claims with command, commit, configuration, setup, result, skips and
limits; products: character/team × width × donor. Rewind spots (before / active / after, restore
equality plus forward replay): mid-flip airborne, inverted on an object, mid-tunnel, teleporter,
hover machine, miniboss, the act change, act 2 boss inverted, the `$1700` load, each arena redraw,
laser active, chase, exit fade; timeline isolation for death reloads, the `$1700` load and the
exit load.

## Validation and delivery

Focused tests and `run_categories.py --category NAME --run` during slices, through
`maven_queue.py` with `-Dmse=off` and absolute ROM paths (wrong paths skip silently: inspect
skips). Mandatory S3K checks stay green: `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`. **Slice 2 is a shared timing/physics
change: normal change-based validation, never proportionate validation**, plus a matched
before/after of the S1, S2 and S3K trace profiles (plain `mvn test` runs zero trace tests; stale
XML counts as passes; clean build after merging; attribute any red to a baseline measured in the
same tree). One combined `run_categories.py --base 9cba6dbb6 --run` at delivery, after focused
fixes and docs, with class count, cost and stopping rule stated first; no tree edits during the
run; acknowledge the run. Record the DEZ and `$1700` strict-replay frontiers for both movies in
`docs/status/trace-frontier-log.md`.

Docs at delivery: matrices and coverage backlog, this plan's status/evidence, `CHANGELOG.0.7.md`,
`s3k-known-bugs.md` for gaps (the discrepancies file is intentional-only), the DDZ plan and matrix
(entry caveat), agent-workflow README for promoted probes, lessons into existing catalogues.
Commits carry all seven trailers; no `--no-verify`; never `git stash`; start git chains with an
explicit `cd`; never build in another lane's worktree.

## Cross-campaign coordination

Written together with the [LRZ](2026-09-17-lrz-bring-up.md), [SSZ](2026-09-17-ssz-bring-up.md) and
[S3K DEZ](2026-09-17-s3k-dez-bring-up.md) plans; the three campaigns can run in parallel worktrees.

- **Trace directories in `s3k-sonic-tails-complete-emeralds` are named one zone off.** `lrz` = LRZ1/2
  (`$1600` handover at row 38817), `hpz22` = LRZ3 autoscroll, `hpz22_2` = LRZ3 boss → `$1601` → SSZ,
  `hpz*` = SSZ (`zone_id 10`), `ssz*` = DEZ (`zone_id 11`), `dez23_8` = `$1700`, `zone0c` = DDZ,
  `ddz` = ending. Always select by `zone_id`/`zone_act_state`. Whichever campaign starts first records
  this table once in `docs/status/trace-frontier-log.md`; renaming fixtures is a separate task.
- **One shared edit:** `Sonic3kScrollHandlerProvider` maps zones `$16` and `$17` to `hpzHandler` without
  the act. LRZ (`$1600`) and DEZ (`$1700`) both need it act-keyed; the first campaign to land makes the
  provider act-aware for both zones and keeps `$1601` on `SwScrlHpz`; the second rebases onto it.
- **Handoffs:** LRZ owns `$1600` → `$1601` (closes HPZ's entry dependency). SSZ owns the HPZ teleporter
  arrival and the `$A00` → `$B00` request. DEZ owns the `$B00` arrival presentation, `$1700` and the
  `loc_803D6` → `$C00`/`$D01` branch (closes DDZ's seeded-entry caveat). `$D01` and the Knuckles ending
  stay with the ending campaign. A handoff is verified by the requesting side as a request plus load
  attempt, and by the receiving side from a cold chain once both exist.
- **The act-3 carry is one shared engine owner.** LRZ2 → `$1600` (`loc_63C14`, restored by
  `LRZ3_ScreenEvent` stage 0 `loc_59B1C`) and DEZ2 → `$1700` (`loc_7F310`, restored by
  `DEZ3_ScreenEvent` stage 0 `loc_5A49A`) run identical code: `Act3_flag` skips the title card
  (`loc_62B6`) and is cleared at `loc_62FE`; the load zeroes rings/timer but keeps
  `Saved2_status_secondary`; stage 0 restores `Act3_ring_count` and `Act3_timer` when non-zero and
  flags the HUD. Whichever of LRZ and DEZ lands first builds it as a game-state owner with a rewind
  adapter; the other reuses it. In this plan that is slice 8 (save) and slice 9 (restore).
- **The ring sentinel is going away (LRZ `3418eba6e`).** Shared `Sonic3kRingPlacement` currently
  spawns a leading `(0,0)` sentinel as a real ring in every S3K act; the LRZ branch drops it and the
  fix arrives at merge time. Do not duplicate it here. Any DEZ ring-count assertion targets the ROM
  counts (278 / 198 / 0); a number inflated by the sentinel is marked
  "pending LRZ `3418eba6e`" rather than baked in as expected.
- **The act-aware scroll provider landed in LRZ `bbd156d37`**, not as a separate shared commit:
  `$1600`/`$1700` take the default handler, `$1601`/`$1701` keep `SwScrlHpz`, `getHandler` becomes
  two-argument and `currentActFor()` supplies the live-level fallback. Slice 9 takes it with
  `git checkout bbd156d37 -- src/main/java/com/openggf/game/sonic3k/scroll/Sonic3kScrollHandlerProvider.java`,
  then removes the three LRZ-only lines (the `lrzHandler` field, its `new SwScrlLrz()` and the
  `ZONE_LRZ` case) and re-adds slice 1's DEZ `$0B` case. This supersedes the "first campaign to land
  makes the provider act-aware" note above: LRZ landed it.
- **Clock-seeded RNG/aim** (`V_int_run_count`: Mecha Sonic, DEZ turrets as in DDZ) needs a declared
  seed for movie-route matching until the full cold chain supplies it; label such evidence seeded.
- **Knuckles trace testing is out of scope (user decision 2026-09-17).** One Knuckles replay class
  exists (`TestS3kKnucklesLbz2BigArmTraceReplay`); the `s3k-knuckles-complete-superemeralds` run has no
  segment classes. A campaign may add one where cheap, but owes no Knuckles replay frontier; Knuckles
  rows rest on authored routes and native probes from that movie.

## Verified ROM values

Re-read in the disassembly on 2026-09-17 by the planner and an independent verifier (lines are
`sonic3k.asm` at submodule `1a454a0e`). Use these; do not re-derive them by measurement.

| Item | Value | Source |
| --- | --- | --- |
| `Reverse_gravity_flag` | `$F7C6`; writers `$58`, `$59`, `$5B`, debug cheat, `loc_7FC3E`; all player-triggered writers are Player 1 only | constants 684; 94874, 95075-95080, 95486-95539 |
| Miniboss trigger / arena | range Y `$18C-$38C`, X `$3400-$3780`; arena `$28C,$28C,$3680,$36C0` (min Y, max Y, min X, max X) | `word_7DDA4`, 167659-167661 |
| Miniboss `Events_fg_4` write 1 | hit counter `$42` reaches 8 → `loc_7DFB8` | `loc_7EE42`, 169357-169367 |
| Post-change transport | `y_vel −$1000`, land Y `$3AC` (`$3B0` Tails), title card, 120 frames, `Pal_DEZMiniboss2` → line 2 | `loc_7E3EC`-`loc_7E4A2`, 168223-168290 |
| Act change | X −`$3600`, Y +`$400`; `Pal_DEZ2+$20` `$40` bytes → lines 3-4; direct `$B01`: `Events_routine_fg = 4`, `_bg = 8` | `loc_593EC`; 118724, 118770 |
| `AnPal_DEZ1`-only channel | counter+`$0A` period `$10`, step 8, limit `$30` → line 4 + `$18` | 3661-3714 |
| Shared AnPal channels | period 5, step 4, limit `$30` → line 3 + `$1A`; period `$14`, step `$A`, limit `$28` → line 3 + `$10` | same |
| `AniPLC_DEZ` | durations `0,1,3,-1,4,4,1,0`; script 7 = 132 entries; `$1700` = `AnimateTiles_NULL` | 56079-56260, 53933 |
| Act 2 boss trigger / arena | range Y `$198-$498`, X `$33E0-$3480`; arena `$218,$288,$3400,$34E0`; 8 hits; 6 table entries, 4 handlers | `word_7F0BE`, `word_7F0C6`, 169567-169611 |
| Act 2 boss gravity | acceleration `$38` → `-$38`, normal integration; clearer object from defeat | `sub_7F8A0`; `loc_7FBD6`/`loc_7FC3E` |
| `$1700` request | `Player_1 x ≥ Camera_X + $160`; saves rings, timer, `Saved2_status_secondary`; max X grows to `$3620` | 169772-169787, 169754 |
| `$1700` player start | P1 `$30,$CD`, P2 `$10,$CD`, `object_control $81`, `x_vel = ground_vel = $600`, +4 Y for Tails; released in `loc_7FE96` on `_unkFAB8` bit 1 (the Start Location file is overwritten) | `loc_7FD9E`/`sub_7FE06`, 170939-170978 |
| `$1700` arena word `Events_bg+$00` | `$6C0` (init) → `$2C0` (`loc_810A0`, Camera X ≥ `$520`) → `$6C0` (`loc_80058`, boss routine `$12 → $14`) → 0 (`loc_8011E`). `loc_5A61A` advances on `≠ $2C0` and spawns `Obj_5A94C`; `loc_5A676` waits for 0; `Obj_5A8E6` (x `$40`, y `$F0`) deletes itself once the word is not `$6C0`. `Events_bg+$16` separately receives `$2C0` | 120287, 172782, 171228, 171290, 120463 |
| `$1700` scroll | `Camera_Y_copy = $20 + shake`; BG X = `Camera_X_copy − Events_bg+$02 + Events_bg+$00`; BG Y = `Camera_Y_copy − Events_bg+$04 + $180`; FG wrap `Camera_X_copy & $1FF`; laser DMA `$40` words to tile `$208` when `Events_bg+$10 ≠ +$12` | `sub_5A508`, `sub_5A76C`, `sub_5A79E` |
| Light tunnel | modes Done, Setup, Normal, CircleLarge, CircleSmall, SineDown, SineUp; scale `-$80,-$40,-$80,-$80`; wait `1,0,1,1`; setup `ground_vel $800`, `object_control $81`; waypoint speed `$C00`; subtype `& $1F` | `off_48524`, 94420-94630 |
| Exit | `SaveGame`; `Player_mode < 2` and `Chaos_emerald_count == 7` → `$C00`; else `Player_mode ≠ 3` → `$D01`; else `Game_mode 0` | `loc_803D6`, 171516-171538 |

Rows for the miniboss/boss arenas, AnPal, `$1700` scroll and tunnel tables come from the verifier's
reading and were not re-read line by line by the planner; every other row was read by both.

## Open questions and kill conditions

Resolved on 2026-09-17 from the disassembly and code (details in Findings and the research docs):
default scroll is wrong for acts 1-2 (BG must be static); `AniPLC_DEZ` script 7 has 132 real
frames and nothing is trigger-gated; the engine results object already raises the act-change
signal; `$6D` is unimplemented (placeholder); the engine trace code has no reverse-gravity field;
the `Events_fg_4` write order and the `$1700` start position (both in Verified ROM values).

| Question | Kill condition | Slice |
| --- | --- | --- |
| Does gravity ever reverse in act 1 (no `$58/$59/$5B` placed there)? | Native watch of `$F7C6` over the act 1 rows of the DEZ segment | 3 (pass 1 probe in 0) |
| ~~Does `LevelActTransitionExecutor:117` (`resetForLevel`) run on the seamless DEZ path?~~ **Answered 2026-09-17: yes.** `executeClaimed` is the in-place act-change path and calls it, so the executor saves and restores the flag around the call | Read the executor's callers before adding the flag clear | 2 (closed) |
| Does the TraceChaser recorder carry `Reverse_gravity_flag` in V5 aux rows? | `tools/tracechaser` was not initialised in the planning worktree; inspect `aux_state` keys. No new trace field in this campaign either way | 0 |
| Does `PlayableHurtRadiusTransition` run for Tails and Knuckles (`Tails_/Knux_TouchFloor`)? | Read its callers; test 2a-2 covers all three characters | 2 |
| Knuckles chained into `$1700` | Record what happens after slice 8; not mandatory (ROM denies only the level-select path) | 8 |
| Which campaign owns SSZ → DEZ | Settled by landing order; record in both plans | 12 |

## Status

Slices 0 and 1 delivered 2026-09-17 on base `035e48a58` (`0d9a4f3ea`, `4e7655bf9`); see the
evidence log. **Slice 2 is delivered and gated twice** (runs `20260918T100237Z-e62d5573` and
`20260918T105742Z-cc4590b5`, both acknowledged), with the reference table at **86 covered, 4
partial, 22 missing, 4 n/a**. The 22 missing rows are, by owner: the Knuckles glide/slide/climb
rows behind the glide probe wrapper (5), the dash-dust and Tails'-tail render rows (3), the two
rows blocked on upright behaviour the engine does not model (`Touch_Monitor`, `Obj_Spikes`),
`loc_1E44C`'s rebuilt comparison (1), the act 2 boss's three, and slice 3's remaining eight
DEZ-object rows. **Slice 3 is part-delivered**: `$5B` `Obj_DEZGravitySwap` is concrete, which
makes `Reverse_gravity_flag` reachable in ordinary play for the first time. `$58`, `$59`, `$5A`,
`$5C`, `$5F` and `$61` remain; the ROM reading for `$58` and `$59` is written up in the last
evidence entry so the next session does not repeat it.

The blocker for the rest is not ROM reading — it is that **no fixture exists in which an inverted
player can be shown landing on real ceiling terrain**. Three candidates were tried and rejected
with evidence (see the slice 2 part 2 evidence entry). Building a controlled flat-floor/flat-ceiling
fixture is the prerequisite for everything that follows, because without it the push-out sites,
landing tails, player actions, objects and companions all have expectations that cannot be
asserted against anything real.

| Claim | State |
| --- | --- |
| Implemented | Slice 1: static background, `AnPal_DEZ1`/`DEZ2`, `AniPLC_DEZ`, the runtime event words and the screen-event chunk writes. Slice 2 part 1: inverted position integration (`MoveSprite_TestGravity`/`2` and `CalcRoomInFront`), the death plane at the top of the level, the level-load clear and the seamless act change's preserve. Slice 2 part 2: the `sub_11FD6`/`sub_11FEE` probe swap and its angle mirror. Slice 2 part 3: the ceiling-sensor activation swap that the probe swap needed, and the six airborne push-out and snap sites measured for all three characters against real Death Egg act 2 terrain. Reverse gravity now stands at 34 of 116 ROM references covered, 6 partial, 72 missing. Present before work: level load, music, `$B00` intro run, slope-angle rule, shared objects (297 of 859 placements concrete), partial PLC art |
| Cold-reachable | Not started |
| Rewind-verified | Palette cycle counters and event routine words (`TestS3kDezPresentationRewind`); the flag itself was already snapshotted. Capture/restore across an *inverted physics state* is not verified and cannot be until step 2a-2 |
| Native behaviour matched | Not started; replay frontiers measured at `035e48a58` (slice 0), all six classes red from frame 0 |
| Visually matched | Slice 1 presentation inspected at 320 and 800 px with before/after clips; no native pixel comparison |

Out of scope, recorded as dependencies: `$D01` ending and credits (ending campaign); the SSZ
launch cutscene if SSZ lands second.

## Evidence log

### 2026-09-17 — Slice 0: baseline and identity

Worktree `.worktrees/ai-s3k-dez-bring-up`, branch `feature/ai-s3k-dez-bring-up`, base develop
**`035e48a58`** (the plan's header says `9cba6dbb6`; the only difference is the docs merge that
brought the three bring-up plans in — the execution base is re-pinned to `035e48a58` and all
slice-0 numbers are stamped with it). No production code changed in this slice.

**Replay frontiers.** One invocation, six classes, 6 failures, 0 errors, **0 skipped** (the ROM
path resolved; a wrong path would have skipped silently):

```
python3 tools/testing/maven_queue.py -Dmse=off \
  -Ds3k.rom.path=<abs>/.worktrees/ai-s3k-dez-bring-up/s3k.gen -Ptrace-replay-r7 \
  "-Dtest=TestS3kSonicTailsSszSegmentTraceReplay,TestS3kSonicTailsDez238SegmentTraceReplay,\
TestS3kTailsFullChainSszSegmentTraceReplay,TestS3kTailsFullChainSsz2SegmentTraceReplay,\
TestS3kTailsFullChainSsz3SegmentTraceReplay,TestS3kTailsFullChainDez238SegmentTraceReplay" \
  -DfailIfNoSpecifiedTests=false test
```

`-Ptrace-segments` covers only `tests/trace/s3k/sonictails`, so it cannot run the four
`tailsfullchain` classes the plan lists; `-Ptrace-replay-r7` includes both directories and was
used instead. Every class diverges on its first compared row, so these are bootstrap numbers,
not route depth. Full table in `docs/status/trace-frontier-log.md` (2026-09-17 entry) and in each
matrix's ORACLE rows.

**Trace identity, settled from the fixtures, not from names.** `dez23_8/metadata.json` is
`zone_id 23`, `act 1` (the fixtures number acts from 1, so act index 0), `bk2_frame_offset`
509032, `start_x 0x0030`, `start_y 0x00CD` — exactly `loc_7FD9E`'s `$1700` Player 1 start. The
2026-08-15 frontier-log entry calling `Dez238` "Hidden Palace Zone proper (level-size row
`sonic3k.asm:38142`)" is wrong and is corrected in place; the right row is `:38143`
(`dc.w 0, $6000, $20, $20 ; DEZ Boss`), and the segment's `camera_x` `$80` and 163 rings are
`DEZ3_ScreenInit` and `Act3_ring_count`, not a save/run-inventory boundary. `ssz` is `zone_id 11`
act 1, start `$0030,$09AC`, which matches the engine's own cold `$B00` start. The campaign-wide
one-zone-off directory table is the LRZ campaign's single edit and is not duplicated here.

**Placement census.** New `TestS3kDezPlacementCensus` (4 tests, 0 failures, 0 skipped, 0.34 s).
It separates ROM facts (`DEZ1_Sprites` `$1F98F4` 366 records / 365 live, `DEZ2_Sprites` `$1FA188`
495 / 494, `DEZ3_Sprites` `$1FCEE8` terminator only / 0, each with the full six-byte
`$FFFF,0,0,0,0` terminator asserted) from a recorded engine baseline (concrete 140 / 157,
placeholder 225 / 337; exactly `$01 $02 $07 $08 $28 $2F $34 $3C $6A $6B $78` resolve to a real
factory under SKL). Broken on purpose once — `PLACEHOLDER_ACT_1` 225 → 224 produced
`act 1 placeholder placements ==> expected: <224> but was: <225>` and exactly that one failure,
with the other three tests still green — then restored and re-run green. The `$1700` address
`$1FCEE8` is from `sonic3k.lst`; the inventory document does not carry it.

**Slice-0 checks the work card asks for.**

- `$78` duplicate registration: `Sonic3kObjectRegistry.registerDefaultFactories` calls
  `factories.put(FBZ_DEZ_PLAYER_LAUNCHER, ...)` at line 218 (`FbzDezPlayerLauncherInstance`, 303
  lines) and again at line 1412 (`FbzDezPlayerLauncherObjectInstance`, 95 lines). Both are in the
  same method, so the later call wins and the 303-line class is dead code for every zone. The two
  differ (only the dead one implements `SolidRoutineProfile` and plays a `Sonic3kSfx`), so this is
  silent behaviour selection. Pinned by the census test and recorded in
  `docs/status/s3k-known-bugs.md`.
- What `$1700` loads today: the standard profile (`Sonic3kLevelResourceProfile.resolve` gives a
  custom profile only for `$1701`), so the real DEZ3 layout, art and palette `$40` load and the
  Earth backdrop renders. The players come from the Start Location file at centre `$60,$70`
  instead of `loc_7FD9E`'s `$30,$CD` / `$10,$CD`, there is no `Obj_5A7C8` arena floor, and Sonic
  falls out of the level and dies at frame 98. No title card. Recorded in the final-boss matrix
  and in `s3k-known-bugs.md`.
- Resources: act 1 `levartptrs $36,$36,$20`, act 2 `$38,$38,$21`, `$1700` `$4C,$4C,$40`
  (sonic3k.asm:199455, 199456, 199483). `PLCKosM_DEZ` is the shared enemy-art list for both acts
  (`Offs_LoadEnemyArt` entries, :64339-64340). Music `Sonic3kMusic.DEZ1`/`DEZ2`
  (`Sonic3kZoneRegistry`), `$1700` reuses `DEZ2`. LevelSizes `0,$6000,0,$B20` / `0,$6000,0,$F10`
  / `0,$6000,$20,$20` (:38119, :38120, :38143).
- V5 aux rows and the reverse-gravity flag: `tools/tracechaser` is still uninitialised in this
  worktree, so the open question stays open. No trace field is being added either way, so it does
  not block any slice.

**Media.** `~/Videos/OGGF/s3k-dez-bring-up/` laid out like DDZ's (`inputs/`, `native/`, `reel/`,
`make_clip.sh`, `make_clips.sh`, `side_by_side.sh`, `INDEX.md`), with
`raw-00-baseline-before-work/{b00-act1,b01-act2,1700-final-boss}` — 320 px, Sonic + Tails, cold
loads, `inputs/baseline-run-right.txt`. Frames inspected: `b00-act1/frames/00300.png` (machine
room, HUD, background visibly scrolling at camera/4 — the defect slice 1 fixes) and
`1700-final-boss/frames/00040.png` (Earth backdrop, Sonic already falling). These three are never
overwritten.

**Matrices and backlog.** `docs/architecture/validation/levels/s3k-dez-act1.md`,
`s3k-dez-act2.md`, `s3k-dez-final-boss.md` seeded with the five claims kept separate and every
obligation "not started"/"unrun"; the three `level-test-coverage.md` rows moved off "Audit
pending".

**Open issues from this slice.** The `$78` duplicate (known-bugs); the reverse-gravity trace-field
question (TraceChaser submodule not initialised); nothing blocking slice 1.

### 2026-09-17 — Slice 1: presentation foundation

Commit `4e7655bf9` on top of slice 0's `0d9a4f3ea`. RED→GREEN for every comparison: each of the
four new tests was written before its implementation and failed against the unimplemented engine,
and after going green each was **broken on purpose once** and produced exactly the perturbed
failure, then restored.

**What the ROM says, re-read here rather than taken from the plan.**

- *Scroll.* `DEZ1_BackgroundInit` (sonic3k.asm:118641) and `DEZ2_BackgroundInit` (:118770) both
  `clr.w` `Camera_X_pos_BG_copy` and `Camera_Y_pos_BG_copy` and then run `PlainDeformation`
  (:103598), which reads both and writes neither. A grep of every reference to those two words
  shows they are only ever written by a zone's own deformation routine, so for the whole of both
  acts the background horizontal scroll word is 0 and `V_scroll_value_BG` — copied from
  `Camera_Y_pos_BG_copy` at the end of `ScreenEvents` (:102254) — is 0 with it. The plan's claim
  that the default handler is wrong is confirmed: `SwScrlS3kDefault` was scrolling the background
  at camera/4, which the `raw-00` baseline shows tearing away from the foreground.
- *Palette.* Re-read `AnPal_DEZ1`/`AnPal_DEZ2` (:3661-3718). The Verified ROM values table is
  right about all three channels' periods, steps and limits, and **incomplete about channel A's
  destination**: it names `Normal_palette_line_4+$18`, but the routine writes two longwords,
  `(a0,d0.w)` to `+$18` and `4(a0,d0.w)` to `+$1C`, so eight contiguous bytes — palette index 3
  colours 12 to 15, not two colours. Tables: `AnPal_PalDEZ12_1` `$3444` (`$30` bytes),
  `AnPal_PalDEZ12_2` `$3474` (`$28`), `AnPal_PalDEZ1` `$349C` (`$30`), all from `sonic3k.lst` and
  contiguous in that order. Two tables repeat frames — `AnPal_PalDEZ12_2` frames 1 and 3 are
  byte-identical, and so are `AnPal_PalDEZ1` frames 1/5 and 2/4 — which the first version of the
  test tripped over; the check now tries every alignment a matching colour set allows.
- *Animated tiles.* `AniPLC_DEZ` is at `$28AEE`, and it is data, not a function: `Offs_AniFunc`
  and `Offs_AniPLC` are one interleaved table (:53841), and Death Egg's entries 22 and 23 pair
  `AnimateTiles_DoAniPLC` with `AniPLC_DEZ`. Nothing gates the eight scripts. Durations
  `0,1,3,-1,4,4,1,0` as the plan says; script 7 really is `$84` = 132 one-byte frames, every odd
  one tile `$2D` and the even ones stepping `0,5,$A,$F,$14,$19,$1E,$23` six frames each before
  holding `$28` for the last eighteen. Entry 46 (`$1700`) is `AnimateTiles_NULL`, asserted too.
- *Screen events.* `ScreenEvents` (:102233) enters the foreground handler with
  `a3 = Level_layout_main`, whose first `$40` words interleave foreground row `n` at offset `4n`
  with background row `n` at `4n + 2` (constants.asm:288; HPZ's existing `$1C(a3)` = row 7 is the
  cross-check). So the plan's unresolved offsets resolve to: `DEZ1` chunk `$BD` at **foreground
  row 5, column `$6E`**; `DEZ2` stage 0 `$D7,$DC,$D7` at **row 14, columns 1-3**; `DEZ2` stage 1
  `$BC` at **row 6, column `$6B`**. Stage 2 (`loc_594F8`) neither consumes `Events_fg_4` nor
  advances, and `DEZ1_ScreenEvent` has no routine index at all, so it repeats on every raise.

**Files.** New `scroll/SwScrlS3kDez.java` (registered as `Sonic3kZoneConstants.ZONE_DEZ` only —
zone `$17` is left on the default for the LRZ campaign's act-keying edit),
`runtime/S3kDezZoneRuntimeState.java` (+ `S3kRuntimeStates.currentDez`),
`events/Sonic3kDEZEvents.java`; `Sonic3kLevelEventManager` gains the construction, dispatch,
runtime-state install and `currentRuntimeStateUsesThisEventInstance` case; `Sonic3kPaletteCycler`
gains `case 0x0B` and a `DezCycle`; `Sonic3kPatternAnimator` gains one `resolveAniPlcAddr` case;
`Sonic3kConstants` and `S3kPaletteOwners` gain the addresses and the owner key. No `@ModApi` type
was touched and `GameRules` was not changed. Every shared-file edit is a single additive case.

**Tests.** `python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen -Dtest=… test`:

| Class | Tests | Result |
| --- | ---: | --- |
| `TestS3kDezScrollHeadless` | 6 | pass |
| `TestS3kDezPaletteCycling` | 4 | pass |
| `TestS3kDezAnimatedTiles` | 7 | pass |
| `TestS3kDezScreenEvents` | 6 | pass |
| `TestS3kDezPresentationRewind` | 2 | pass |
| `TestS3kDezPlacementCensus` | 4 | pass |

Combined with the four mandatory S3K classes (`TestS3kAiz1SkipHeadless`,
`TestSonic3kLevelLoading` — both of them, `TestSonic3kBootstrapResolver`,
`TestSonic3kDecodingUtils`): **86 tests, 0 failures, 0 errors, 0 skipped**.
`maven_queue.py -Dmse=off -Pguards test -B` also ran green (**669 tests, 0 failures, 0 errors,
0 skipped**). `GameLoop` and `Engine.draw` were not touched, and reading
`TestArchitecturalSourceGuard` first confirmed that none of its budgets — the embedded-runtime-data
list is entirely Sonic 1 files, and the concrete-reference and dispatch-method budgets are
`Engine` and `GameLoop` only — covers anything this slice edited. This is focused
validation, not a suite pass; no category run was made and the trace profiles were not re-run,
because slice 1 changes only presentation registrations for one zone that had none.

Deliberate breaks, each producing exactly the perturbed failure and nothing else: background
scroll word 0 → 1 (4 failures, one per camera case); channel B period 5 → 6 (`pass 72 was
[3598, 0] instead of [0, 3584]`); script 0 duration 0 → 1; chunk `$BD` → `$BE` (`expected: <190>
but was: <189>`); and, for the rewind test, deleting both `rewind().restore(...)` calls (both
cases failed). All reverted and re-run green.

**Media.** `raw-01-presentation-320` and `raw-02-presentation-800` at `4e7655bf9`, then three
"before" builds each disabling exactly one registration in an uncommitted edit, reverted and
recompiled immediately (`git status` clean after each). Clips, 300 frames each:
`01a-dez-static-background-before-after.mp4`, `01b-dez-anpal-console-cycles-before-after.mp4`,
`01c-dez-aniplc-machinery-tiles-before-after.mp4`, `01d-dez-presentation-800-wide.mp4`. Frames
extracted and inspected: the before/after background pair shows the old handler dragging the
backdrop left and leaving a black gap at the right edge while the new one holds it in register;
the palette pair shows frozen versus cycling console colours; the tile pair shows the static
placeholder machinery versus the animated art. The 800 px capture fills the wide viewport with no
background edge gap.

**Open issues from this slice.**

- `$1700` still resolves to `SwScrlHpz`: the provider keys zone `$17` without the act. That is the
  LRZ campaign's shared edit, deliberately not duplicated here; slice 9 adds the real handler.
- Everything above is engine-side inspection. No native pixel or probe comparison has been made
  for the background, the palette phase or the tile DMA order, so "native behaviour matched" stays
  open for all of it.
- The `DEZ1` and `DEZ2` chunk writes have no production trigger yet; the tests drive
  `Events_fg_4` from the runtime state. The miniboss (slice 6) and end boss (slice 8) supply it.
- `DEZ2_ScreenEvent` stage 0 is unreachable on a direct load by design, so its test drives
  `Events_routine_fg` back to 0. It becomes cold-reachable with the seamless change in slice 7.

### 2026-09-17 — Slice 2, part 1: the reverse-gravity integration and the flag's lifecycle

**Partial slice. Steps 2a-2, 2a-3, 2b and 2c were not started.** What landed is the integration
step (2a-1), the death plane, and the flag's level-load lifecycle; 92 of the 116 reference rows
remain missing. The remaining rows and why they were left are at the end of this entry.

Worktree `.worktrees/ai-s3k-dez-bring-up`, branch `feature/ai-s3k-dez-bring-up`, base
`f60b3f3e2`. Disassembly read at submodule `1a454a0e`.

**What the ROM actually says, re-read row by row rather than taken from the table.**

- `MoveSprite_TestGravity` (sonic3k.asm:36068-36083) and `MoveSprite_TestGravity2` (:36088-36101):
  with the flag set, `x_vel` integrates normally, `addi.w #$38,y_vel(a0)` still runs unchanged, and
  only the copy of `y_vel` that feeds `add.l d0,y_pos(a0)` is negated. The stored velocity keeps
  its sign.
- Which routines reach those wrappers was settled by listing the call sites and resolving each to
  its enclosing label, not by grepping for the flag (the flag test is inside the wrapper, so
  callers do not grep). Nine `MoveSprite_TestGravity` callers: `Sonic_MdAir`, `Sonic_MdJump`,
  `loc_123AA` (dead), `Tails_Stand_Freespace`, `loc_149BA`, `loc_157C8`, `Knux_Stand_Freespace`,
  `Knux_Spin_Freespace`, `loc_17CA2`. Sixteen `MoveSprite_TestGravity2` callers: `Sonic_MdNormal`,
  `loc_10FEA`, `loc_122D8` (hurt), `loc_125C6` (drown sink), `loc_14760`, `Tails_FlyingSwimming`,
  `loc_14956`, `loc_156D6`, `loc_15828`, `Knux_Stand_Path`, `Knux_Glide_Freespace`, `loc_170CC`,
  `loc_17BD0`, `loc_17D04`, `loc_1A7E8` (the bouncing ring) and `loc_49E0A` (a Death Egg object,
  slice 3). Every other object in the game calls plain `MoveSprite`/`MoveSprite2` and never
  inverts — that is why the inversion is at the player movement call sites and **not** inside
  `AbstractSprite.move`, which every object shares.
- `loc_125C6` (:24702-24704) is the drowning pre-death sink: `MoveSprite_TestGravity2` **then**
  `addi.w #$10,y_vel`. It does invert. Noted separately: the engine's drowning branch adds the
  `$10` *before* moving, so it integrates the post-add velocity where the ROM integrates the
  pre-add one. That is a pre-existing one-frame ordering difference unrelated to this slice and was
  deliberately left alone — changing it would not be inert with the flag clear.
- `sub_F61C` `loc_F638` (:19688-19700) applies the same `neg.w` to the projected `y_vel` before the
  wall probe, so `CalcRoomInFront` looks where the player will actually be.
- `Player_Boundary_CheckBottom` (:23188-23206). The reverse branch `loc_11722` is
  `move.w (Camera_min_Y_pos).w,d0 / cmp.w y_pos(a0),d0 / blt.s <alive>`, i.e. **alive while
  `Camera_min_Y_pos < y_pos`**, dead at or above it, with no `$E0` offset — the `$E0` belongs to
  the upright branch alone. `Disable_death_plane` gates both. `Tails_Check_Screen_Boundaries`
  `loc_14F30`/`loc_14F4C` (:28423-28441) is byte-for-byte the same pair, so the engine's single
  shared boundary owner closes both table rows at once.
- The open question "does `LevelActTransitionExecutor:117` run on the seamless DEZ path?" is
  **answered: yes.** `executeClaimed` is the in-place act-change path and calls
  `gameState.resetForLevel()`. Since `loc_593EC` (:118724) runs `Load_Level`/`LoadSolids` with no
  RAM wipe, the executor now saves and restores the flag around that call. The clear itself lives
  in `resetForLevel()` next to the other RAM-wipe fields, citing
  `clearRAM Tails_CPU_interact,$100` (:7621).
- `GameStateManager`'s Javadoc cited `$FFFFF768` (which is `Primary_Angle`). Corrected to `$F7C6`
  on the field and the getter, as the reference document asked.

**Owners.** One new engine-internal helper, `com.openggf.physics.ReverseGravity`
(`integrationYSpeed`, `mirrorAngle`, `mirrorYDelta`). It is deliberately **not** a `GameRules`
member and carries no annotation: `GameRules`, `CollisionSystem` and `GameStateManager` are all
`@com.openggf.game.ModApi` and a new component or public member would break the 0.7 signature pin.
Both spellings were grepped before the class was added. No public member was added to any
`@ModApi` type: the `CollisionSystem` and `GameStateManager` edits are a comment, two local
variables, a Javadoc correction and one field assignment inside an existing method. Nothing is
zone- or game-keyed; every branch reads the flag itself through `GameStateManager`.

**RED → GREEN, per group.**

| Step | Test | RED | GREEN |
| --- | --- | --- | --- |
| 2a-1 (A: 36069, 36089, 19696) | `TestS3kReverseGravityIntegration` | `inverted: y_pos += -old y_vel ==> expected: <-4> but was: <4>` | 2/2 |
| A: 23191 + C: 28426 | `TestS3kReverseGravityBoundary` | `reverseGravityKillsAtTheTopBoundary ==> expected: <true> but was: <false>`; `reverseGravitySparesTheBottomBoundary ==> expected: <false> but was: <true>` | 4/4 |
| flag lifecycle | `TestS3kReverseGravityFlagLifecycle` | `aLevelLoadClearsTheFlag ==> expected: <false> but was: <true>` | 3/3 |

Both boundary positive controls (upright kill below the bottom, upright survival above the top)
passed in the RED run, which is what proves the gate under test was live rather than unreachable.
`theSeamlessActChangeKeepsTheFlag` also passed in the RED run — it had to, because nothing cleared
the flag yet. It earns its keep only now that `resetForLevel()` does clear it, as the guard that
the executor's restore is what keeps Death Egg act 2 inverted.

**Two measurement mistakes made and corrected here, both worth the next agent's attention.**

1. The first version of the integration test asserted that the *pixel* delta mirrors on every
   frame. It does not, and the ROM does not claim it does: `add.l d0,y_pos(a0)` carries through the
   16 subpixel bits, so a `$438` step reads as `+4` pixels with a `$38` fraction one way and `-5`
   with a `$C8` fraction the other. The expectation had come from Java intuition rather than the
   cited routine. The test now asserts the 32-bit `y_pos` step, which *is* the exact negation.
2. `-Dtest=A+B` is not a surefire selector. That run reported `BUILD FAILURE` with
   `No tests matching pattern` — it executed **zero** tests, and had the message been any less
   explicit it would have looked like a clean result. The separator is a comma.

**Commands.** All through `maven_queue.py` from this worktree with
`-Ds3k.rom.path=<worktree>/s3k.gen`; the queue waited behind the LRZ and SSZ lanes several times,
which is normal. Every run reported `Skipped: 0`, so the ROM path was right.

**Exactly what remains of slice 2.** The reference table's totals are now 14 covered, 6 partial,
92 missing, 4 n/a. Covered rows added here: A 19696, 23191, 36069, 36089 and C 28426. Everything
else in the table is untouched by this entry, and in particular:

- **Step 2a-2 is not started.** Group A rows 22330 (`Call_Player_AnglePos`), 24128 (`sub_11FD6`),
  24142 (`sub_11FEE`) and 24156 (`ChooseChkFloorEdge`), plus every collision, touch-floor, hurt
  and death row of groups B, C and E. This is the large one: it rewrites "which probe is the
  floor" inside `CollisionSystem`, and a half-finished version is worse than none, because the
  position integration now inverts while the probes still look down. `ReverseGravity.mirrorAngle`
  is already written and unit-tested for it (`addi.b #$40 / neg.b / subi.b #$40`, which fixes both
  walls and swaps floor with ceiling); nothing calls it yet.
- **Steps 2a-3, 2b and 2c are not started** (player actions, solid objects/springs/spikes/monitors/
  rings, companions and Knuckles).
- **No demo clips were produced, deliberately.** With 2a-2 missing, a seeded clip would show the
  player integrating upward while the collision probes still find the floor below — a picture of a
  half-implemented slice, not of the feature. The clip obligation belongs to the finished slice.
- **No rewind spot was added for an active flip.** The flag byte itself was already snapshotted
  before this slice; what is not yet verifiable is a capture/restore across an inverted *physics*
  state, which needs 2a-2.

**One finding that contradicts the reference document.** It says the engine "clears the field only
in `resetSession()` (line 252), not in `resetForLevel()` (line 275)" and proposes adding the clear
there "after checking its two callers … If the seamless `$B00` → `$B01` path goes through the
second, the clear must not run there." It does go through the second, so the clear alone would have
been wrong: the executor needed the matching save/restore. Both halves landed together.


### 2026-09-17 — Slice 2, part 2: the probe swap, and the trace baseline for part 1

Base for the trace comparison `f60b3f3e2`; part 1 is `5aa6a8673`.

**Trace non-regression for part 1 — matched, both sides measured in this worktree with
`clean test` and `-Ptrace-replay`.** Not a full profile: four classes chosen to cover all three
games' player physics. Baseline was measured by detaching this worktree to `f60b3f3e2`, not by
reasoning from HEAD.

| Class | `f60b3f3e2` | `5aa6a8673` |
| --- | --- | --- |
| `TestS1Ghz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS1Mz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS2Ehz1TraceReplay` | red, 16388 errors, first error frame 6 `dynamic_art.outstanding_transfer_ids` (expected=[2], actual=[]) | red, 16388 errors, frame 6, same field and values |
| `TestS3kAizTraceReplay` | 3/16 red, 59 errors, first error frame 5497 `camera_x` expected `0x0010` actual `0x0012` | 3/16 red, 59 errors, frame 5497, same values |

Identical on both sides, failure for failure. The two reds are pre-existing and
**baseline-attributed**; part 1 moved nothing. This is a four-class subset, not "the S1, S2 and S3K
trace profiles", and it is not evidence that any other trace class is unaffected.

**What part 2 implements.** `sub_11FD6` / `sub_11FEE` as a production selector
(`CollisionSystem.floorProbeSensors` / `ceilingProbeSensors`) plus the angle mirror
(`surfaceAngle`), wired into all six probe sites of `resolveAirCollision` and into the two places
that write `angle(a0)` (`landOnFloor`, `doCeilingCollision`).

One ROM subtlety worth keeping: **only `d3` is mirrored, not the shared angle registers.**
`FindFloor` writes the raw angle to `Primary_Angle`/`Secondary_Angle` before the wrapper's
`addi.b #$40 / neg.b / subi.b #$40` runs, and it is those raw bytes that the character control tail
copies into `next_tilt`/`tilt`. So `publishAirFloorAngleRegisters` and `applyPairedAngleWrites`
keep receiving unmirrored results and only `angle(a0)` gets the mirrored value.

**RED → GREEN.** `TestS3kReverseGravityProbeSelection` (2 tests, seam) and the angle-mirror test in
`TestS3kReverseGravityTerrain`. Twelve reverse-gravity tests green, `Skipped: 0`.

**What part 2 does NOT establish, and why — read this before trusting the table.** There is no test
of an inverted player landing on a real ceiling. Three fixtures were tried and each failed for a
reason worth recording rather than working around:

1. **The Death Egg act 1 spawn is not standing on terrain.** Instrumenting the probes showed the
   ground sensors returning `null` on every frame while the player was nonetheless grounded: the
   spawn rests on a solid *object*. Object contact is group H / step 2b and is not gravity-swapped
   yet, so an upright "landing" there proves nothing about `sub_11FD6`.
2. **The Angel Island act 1 spawn is held by the intro cutscene** (`object_control` set, position
   frozen for every frame). With `withSkippedZoneIntro()` the player moves, but there the sensors'
   own stride reports no floor where `ObjectTerrainUtils.checkFloorDist` reports one at distance 0.
   That disagreement is about engine sensor semantics, not about reverse gravity, and chasing it
   here would have meant tuning a fixture until it passed.
3. An earlier version of the reverse-gravity test **could not have disagreed**: with 2a-1 landed,
   an inverted player simply rises away from the floor below, so "the floor did not catch it"
   passes whether or not the probes swap. It was replaced with a descending case (negative `y_vel`
   under the flag) before any implementation, and that version did fail for the right reason
   (`y_vel = -912`, exactly `-$400 + 2 × $38`: gravity still accumulating, nothing stopping it).

A further instrumentation mistake, since it will bite the next agent: `TerrainCollisionManager.getSensorResult`
returns a **shared pooled buffer**. Probing the ground sensors and then the ceiling sensors and
printing both shows the ceiling results twice. Clone the array before the next call.

**What slice 2 still needs.** A controlled flat-ceiling fixture is the prerequisite for the rest of
2a-2: without it, the five push-out sites, the landing/`ground_vel` tails and everything in 2a-3,
2b and 2c have no way to be asserted against real terrain. Building that fixture — a small synthetic
collision layout, or a verified coordinate pair (flat floor, flat ceiling above it) in one act — is
the first task of the next session, not more per-zone archaeology.


### 2026-09-17 — Slice 2, part 3: the Death Egg act 2 fixture, and the blocker it found

**The fixture exists now.** `$58`/`$5B` placements were decoded straight from the ROM rather than
through the engine: `DEZ2_Sprites` at `$1FA188`, 495 six-byte records (x word, y word with the
render-flag nibble in the top four bits, object id, subtype). The decode self-checks against the
placement inventory — exactly 5 `$58` and 11 `$5B`, an `FFFF` terminator, and the `$5B` flag split
6 unflipped / 5 flipped — so the record layout is confirmed rather than assumed.

| `$58` | x | y |  | `$5B` | x | y |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `$0E30` | `$08D8` |  | 1-11 | `$0700`-`$3100` | `$0340`-`$0940` |
| 2 | `$0FA8` | `$0748` |  |  | (all in open corridor, no terrain within ±32 px) | |
| 3 | `$1AAC` | `$0588` |  |  | | |
| 4 | `$1C40` | `$06B8` |  |  | | |
| 5 | `$1F54` | `$0848` |  |  | | |

Terrain was then measured around each. The `$5B` sites are all in open space. Site 3 has a usable
corridor 32 px to its right: **x=`$1ACC`, floor surface y=`$055F`, ceiling surface y=`$051F`** — a
64 px gap, both terrain rather than objects. That is the fixture, and
`TestS3kReverseGravityDezCorridor` re-derives all four numbers from the level data so it fails
loudly if the level changes.

**The upright control passes there**: a falling player lands on the corridor floor at exactly
`$054C` with `y_vel` zeroed, which is `loc_11F9C`. So the fixture works and the probe route is live.

**The inverted cases do not, and the cause is not reverse gravity.** Instrumenting the fall showed
the ceiling-sensor array coming back empty on every frame. Sweeping the whole act 2 region (4161
points, x `$0E00`-`$2000` × y `$0300`-`$0A00`) with the solidity bit forced to `$C`, `$D` and `$E`
in turn gave **4161 downward hits and 0 upward hits at every bit**. That table kills two
hypotheses at once: it is not the ceiling data, and it is not the solidity bit — identical
downward counts across all three means that path is not discriminating on the bit. The upward
scan itself never fires. Written up as its own entry in `s3k-known-bugs.md` with a kill condition.

This is the blocker for the remainder of slice 2. Nothing in 2a-2's push-out sites, 2a-3, 2b or 2c
can be asserted against terrain until an inverted player can land on something, so the next session
should start there and not with more reference-table rows.

**Two hypotheses I held and had to drop, recorded so they are not re-run.**

1. *"The DEZ ceilings are top-solid one-way platforms, so the lrb probe correctly misses them."*
   Killed by the bit table above. It also rested on a misreading worth flagging:
   `Sonic_CheckCeiling` never loads `d5` at all — it inherits the solidity bit from its caller, and
   `loc_11F00` (:24042) loads `lrb_solid_bit` once for the entire airborne routine, floor branch
   included. The engine's `GroundSensor` comment that a downward probe uses `top_solid_bit` "in ALL
   modes" cites the *grounded* `Player_AnglePos`, which is a different caller.
2. *"The engine's `Direction` encoding already reproduces the ROM's five `neg.w d1` sites."* Still
   plausible and still unmeasured — it cannot be measured until the upward sensors work, which is
   exactly why those five rows stay `missing` in the reference table rather than being credited.

**A reference-table error found while starting 2a-3, and why 2a-3 was then left alone.**
The table describes row 23294 as "`Sonic_Jump`: mirrors the launch angle". Reading the whole
routine (sonic3k.asm:23286-23351) shows it does not. The mirrored copy of `angle(a0)` is consumed
by `loc_117FC`'s `addi.b #$80,d0` / `CalcRoomOverHead` — the **headroom check**. The jump vector
at `loc_1182E` (:23314-23317) then re-reads `angle(a0)` raw with no flag test at all. Implementing
the row as written would have mirrored the wrong thing. Rows 28525 and 32441 carry the same
description for Tails and Knuckles and are now flagged "verify before implementing"; they were not
re-read line by line here.

The three 2a-3 changes this made concrete — the headroom angle, the roll-entry offset
(`Player_DoRoll` `addq.w #5` then `subi.w #2*5`, net −5, shared with Tails' `+1`/`−1` at :28500)
and the jump roll-radius negation at :23344-23351 — were **deliberately not implemented**. None of
them can be asserted while the upward sensors are dead: the roll-entry offset is erased within the
same frame by the `AnglePos` re-snap, and the jump cases need a grounded inverted player. Having
already been wrong three times in this slice about how this code behaves (the `Direction`-encoding
assumption, the solidity-bit hypothesis, and this row description), implementing shared player
physics blind is the worse risk. They are the first rows to land once the sensor blocker clears.



### 2026-09-17 — Slice 2, part 3: the sensor blocker was a harness artefact plus one real gap

**Root cause of "upward sensors never fire", in one experiment.** `Sensor.doScan` returns `null`
when `active` is false, and `AbstractPlayableSprite.updateSensors` (:4977) deactivates the ceiling
pair whenever the player is grounded *or* airborne moving mostly downward. The 4161-point sweep
probed `getCeilingSensors()` on a grounded/falling sprite, so every sample returned `null` for that
reason alone — in any zone, not just Death Egg. The control: one probe at the corridor point
(x=$1ACC, centre y=$053A) printed `active=false result=null`, then the same sensor after
`setActive(true)` printed `dist=7 angle=1`. The ceiling data was there the whole time. Both
hypotheses the previous entry recorded as "killed by the bit table" were killed against a
measurement that could not have produced a hit under any bit.

**The real engine gap underneath it.** The ROM has no per-sensor enable: `sub_11FD6` simply calls
`Sonic_CheckCeiling` instead of `Sonic_CheckFloor` when the flag is set (sonic3k.asm:24127-24137).
The engine models the same quadrant dispatch twice — once as `CollisionSystem`'s switch and once as
`updateSensors`' activation — and only the first had been swapped, so under reverse gravity the
quadrant switched off exactly the array the swapped probe was about to scan. `updateSensors` now
picks the floor/ceiling pair through the same swap and the grounded branch is deliberately left
alone (`Call_Player_AnglePos` :22329 mirrors `angle(a0)` instead, so ground attachment keeps using
the ground sensors with a ceiling ground mode).

**RED → GREEN.** `TestS3kReverseGravityDezCorridor.invertedGravityLandsOnTheCorridorCeiling` was
written first and failed with `air=true` — the player fell through the ceiling for eight frames.
After the activation swap it lands. The whole class was then re-run with the fix disabled
(`boolean reverseGravity = false && …`): 9 of the 16 tests fail, every one of them an inverted
assertion, and the upright controls stay green. The two horizontal-quadrant push-out cases pass
either way, because those quadrants already activate both pairs; they are credited by the probe
swap and the push-out arithmetic, not by the activation fix.

**A one-pixel disagreement that cost a round.** The fixture's `RESTING_ON_CEILING_Y` was derived
from `ObjectTerrainUtils.checkCeilingDist`, which puts the corridor ceiling's zero-distance row at
$051F. The ceiling *sensor* puts it at $0520, and an upright head-bonk comes to rest there
(measured: head $0520 on frames 2-4 of a rising probe). Shipped play runs through the sensor, so
every expected position in the fixture is now taken from an upright control measured in the same
corridor rather than from either helper's constant. Recorded in the pitfall catalogue together with
the inactive-sensor hazard.

**What is now measured, not argued.** Six push-out and snap sites × three characters = 18 rows,
plus the two wrapper rows. Quadrant $00 floor snap (`loc_11F6E` :24081 / `loc_15444` :28917 /
`loc_179B4` :32663), quadrant $80 push-out (`loc_120C2` :24246 / `loc_1555C` :29042 / `loc_17A94`
:32758), and both horizontal quadrants' ceiling push-out and floor snap (`Player_HitCeiling` :24178,
`loc_1211A` :24284, `loc_12074` :24213, `loc_12148` :24308 and the Tails/Knuckles twins). The
engine has one `resolveAirCollision` owner for all three `DoLevelCollision` routines, and the test
is parameterised over the three characters through
`SonicConfiguration.MAIN_CHARACTER_CODE`, so the Tails and Knuckles rows are run as those
characters rather than credited by analogy. Reference table: **34 covered, 6 partial, 72 missing,
4 n/a** (was 14 / 8 / 90 / 4).

**Non-regression.** Four-class trace comparison against the slice base `f60b3f3e2`, `clean test`
with `-Ptrace-replay` in this worktree, ROM paths passed absolutely:

| Class | `f60b3f3e2` (recorded in the part-2 entry) | this change |
| --- | --- | --- |
| `TestS1Ghz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS1Mz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS2Ehz1TraceReplay` | red, 16388 errors, frame 6 `dynamic_art.outstanding_transfer_ids` (expected=[2], actual=[]) | red, 16388 errors, frame 6, same field and values |
| `TestS3kAizTraceReplay` | 3/16 red, 59 errors, frame 5497 `camera_x` expected `0x0010` actual `0x0012` | 3/16 red, 59 errors, frame 5497, same values |

Failure for failure identical; both reds stay **baseline-attributed**. This is the same four-class
subset as the part-2 entry, not the trace profiles, and it is not evidence about any other class.

Focused collision suites, one invocation, `Skipped: 0`: `TestS3kReverseGravity*`,
`TestCollisionSystemAirLanding`, `TestGroundSensor`, `TestCollisionLogic`, `TestObjectTerrainUtils`,
`TestS1Ghz3BridgeTerrainCollision`, `TestS3kHcz2RaisedFloorWallCollisionHeadless`,
`TestTodo2_DualCollisionAddresses`, `TestGlideWallGrabTerrain`, `TestTerrainCollisionManager` —
87 tests, 0 failures. With the flag clear the activation swap is the identity, and the S1 and S2
classes above exercise that.

**Still open in slice 2.** The grounded path (`Call_Player_AnglePos` :22330,
`ChooseChkFloorEdge` :24156) and all of 2a-3, 2b and 2c. The three 2a-3 changes the previous entry
left blocked — headroom angle, roll-entry offset, jump roll-radius — are now unblocked: an inverted
player can be grounded on the corridor ceiling, so a grounded inverted fixture is available to
assert them against.

**Where slice 2 should resume, and why not further in this session.** The airborne path is done;
the next row is the **grounded** one, `Call_Player_AnglePos` :22330, and it has to land before
2a-3's player actions rather than after them. Two findings say so:

1. *2a-3's roll, jump and spindash rows need a grounded inverted player, and the engine cannot
   produce a correct one yet.* An inverted player now lands on the ceiling, but its `angle` and
   ground mode come from the airborne path only. The ROM's grounded attachment mirrors `angle(a0)`
   around `Player_AnglePos` (:22329-22343), so a ceiling-standing player runs `WalkCeiling` with a
   `$80` terrain angle mirrored back to `$00`. Until that exists, asserting a roll-entry offset on
   a ceiling-grounded player would be measuring a half-built state, not the ROM.
2. *The roll rows are a coordinate-convention trap, not a sign flip.* `Player_DoRoll` (:23259-23268)
   does `addq.w #5,y_pos` and then, under the flag, `subi.w #2*5` — a **centre**-Y delta of −5 where
   upright is +5. The engine applies the equivalent to **top-left** Y through
   `getRollHeightAdjustment()`, which returns the full height difference (10 for Sonic) because the
   roll also shrinks the box; the centre moves 5. Negating that helper would move the centre by −10,
   not −5. The correct engine top-left delta under the flag is `fullDiff/2 + (−fullDiff/2)` = **0**
   in GROUND/CEILING mode and `−fullDiff/2` on a wall. `loc_11578` (:22975-22991), `loc_1182E`
   (:23344) and `loc_11C5E` (:23694) share the same shape with the radius difference in `d0`.
   Whoever implements these must state the convention in the test, not just flip a sign.

Rows left in slice 2 after this session: group A 22330 and 24156; all of 2a-3 (B 22011,
22623-23694, 24426 and the C/E twins, including the preserved `Tails_Test_For_Flight` bug at
28655); 2b (groups F, G, H, I); 2c (group D, the Knuckles glide/slide/climb rows, Super forms).
No demo clips and no rewind spots were produced: both belong to the finished player groups, and the
grounded path is still missing. No category run was launched for the same reason — this is a
partial slice, and its verification is the focused evidence recorded above, not a delivery gate.


### 2026-09-17 — Slice 2, part 4: the grounded inverted path

**What landed.** `Call_Player_AnglePos` (:22329-22343) as a wrapper around
`CollisionSystem.resolveGroundAttachment`, the engine's single `Player_AnglePos` owner: mirror
`angle(a0)`, run the attachment against the raw terrain angle, mirror back. Nothing else changed;
with the flag clear the wrapper is not entered at all.

**RED → GREEN, and what the first RED did not catch.** The first test — an inverted player settling
on the corridor ceiling for four frames — failed only on ground mode (`GROUND`, expected `CEILING`);
it stayed attached at the right y and angle anyway, because a stationary player on a flat surface
does not need a correct probe direction to sit still. That is a comparison that nearly could not
disagree, so a second case was added before implementing: give the landed player ground speed and
run it along the ceiling. That one fails hard without the wrapper — `air=true`, the player walks off
its own surface — and passes with it. Both run as Sonic, Tails and Knuckles.

**`ChooseChkFloorEdge` :24156 stays partial, deliberately.** Its seven callers are all the three
`Balance` routines, and `PlayableSpriteMovement.checkTerrainEdgeBalance` probes through the ground
sensors, which rotate with the CEILING ground mode and therefore reach the same tiles as
`ChkFloorEdge_ReverseGravity` on a flat ceiling. But the ROM selects the reverse-gravity variant
from the *flag*, ignoring ground mode, so the two models diverge on a wall — and the upright engine
already has the same divergence against `ChkFloorEdge_Part2`. Crediting the row would mean crediting
a coincidence. Asserting it properly needs a ceiling *edge* fixture, which act 2 does not obviously
provide near the measured corridor (the ceiling steps down at x=$1AE0 rather than ending).

**Verification.** 105 focused collision and reverse-gravity tests green, `Skipped: 0`, one
invocation. Reference table: **35 covered, 6 partial, 71 missing, 4 n/a**.


### 2026-09-17 — Slice 2, part 5: step 2a-3's radius and jump rows

**Seventeen rows, all three characters.** Roll entry (`Player_DoRoll` :23265, `loc_14FC4` :28500),
unroll (`loc_11578` :22988, `loc_14DA2` :28233, `loc_175AA` :32261), spindash release (`loc_11C5E`
:23694, `loc_1527C` :28748), bubble-shield bounce (`loc_12246` :24426), the jump's headroom angle
(:23294, :28525, :32441) and its radius delta (`loc_1182E` :23346, `loc_1504C` :28572, `loc_1775C`
:32488), and touch-floor roll clear (:24350, :29143, :32839). Table: **52 covered, 3 partial,
57 missing, 4 n/a**.

**The coordinate trap, confirmed by the RED.** The prediction recorded at `fe7b43eb3` held exactly:
the inverted head sat 10 px clear of the ceiling (Sonic and Knuckles) and 2 px (Tails) — the full
height difference, not the radius difference. `PlayableSpriteMovement.applyRollRadiusShift` now
writes the mirrored ROM *centre* under the flag and leaves the upright top-left arithmetic alone.

**A row the plan did not predict.** With the radius rows done, the inverted jump test still failed —
`air=false`, the player never left the ceiling. The cause is row 23294: `Sonic_Jump` mirrors the
angle it hands to `CalcRoomOverHead` (sonic3k.asm:23290-23300), and without it the headroom probe
measured into the ceiling the player was standing on and refused the jump every frame. `Tails_Jump`
(:28524-28576) and `Knux_Jump` (:32438-32493) were then read line by line and confirm both halves of
the `b38402c2a` correction: the headroom angle is mirrored, the launch vector at `loc_1182E` re-reads
`angle(a0)` raw. That closes the "verify before implementing" flag on 28525 and 32441.

**How each assertion was made able to disagree.** The roll, unroll and jump tests were written
first and failed. The two touch-floor tests were written after the change, so they were validated
the other way: the flag test in the landing roll-clear branch was disabled and all three characters
failed with the predicted 10/10/2 px error, then restored. That is a weaker sequence than RED-first
and is recorded as such.

**Verification.** 215 focused collision, physics, roll and spindash tests green, `Skipped: 0`;
`-Pguards` 669/669; the four-class trace comparison against `f60b3f3e2` identical failure for
failure for the third time this session (S1 2/2 green, S2 16388 errors at frame 6, S3K AIZ 3/16 red,
59 errors at frame 5497).


### 2026-09-17 — Slice 2, part 6: the hurt death plane and the spring launches

**The hurt routines' death plane is implemented but stays uncredited.** `sub_12318` (:24475) and
its Tails and Knuckles copies branch to `loc_12336` under the flag and read `Camera_min_Y_pos` with
no `$E0` offset. The branch is in `applyHurtStopBottomKill`, but the reference table keeps the rows
**partial**, because the engine has no observable difference: `Player_LevelBound`'s kill plane —
already covered — fires later in the same frame at the same boundary. Measured by disabling the new
branch and watching both new assertions still pass. The two tests are kept as behaviour guards with
that stated in their javadoc, rather than named after a row they do not discriminate.

**The spring launches (47722, 48095) are covered, and the pairing is the whole trick.** The obvious
test — same authored spring, flag on versus off — cannot disagree: the init swap (:47576-47637,
already covered) and the launch mirror are both negations of the same 8 px nudge and cancel exactly.
Under reverse gravity the player falls *up* the screen and stands on ceilings, so the spring
underfoot is the authored **down** spring running the up-spring body, whose negative launch velocity
integrates through `MoveSprite_TestGravity`'s negated copy into down-screen motion away from the
ceiling. `TestS3kReverseGravitySpringLaunch` pairs each gravity with the authored subtype whose body
it selects; disabling the launch mirror flips both inverted cases and leaves both upright controls
green.

Table: **54 covered, 6 partial, 52 missing, 4 n/a**. 89 focused tests green, `Skipped: 0`.


### 2026-09-17 — Slice 2, part 7: the camera look pans, and two rows that are not ours to port

**Six rows from one owner.** The look-up and look-down pans reverse both their direction and their
limit under the flag: `loc_11276`/`loc_112A6` (:22615-22637) walks the bias up to `$D8` instead of
down to 8, and `loc_112B0`/`loc_112E0` (:22638-22660) down to `$18` instead of up to `$C8`. Tails
(:27868, :27891) and Knuckles (:31896, :31919) repeat the code verbatim and the engine has one
`Camera`, so `decrementLookDownBias`/`incrementLookUpBias` own all six.
`TestS3kReverseGravityCameraLook` walks 120 frames from the `$60` default in each combination;
disabling the flag branch sends both inverted cases to the upright targets (8 and 200 instead of
216 and 24). `Camera` is `@ModApi`, so the change is body-only plus two private constants, and
`-Pguards` (669/669) was run before committing.

**Two rows stay missing on purpose.** `Touch_Monitor` :20802 negates the `y_vel` copy feeding the
monitor's "is the player moving into me" test — the `render_flags` bit 1 upside-down branch at
:20800-20830 — and the engine has no such test: `Sonic3kMonitorObjectInstance.onTouchResponse`
breaks on the roll animation and negates `y_vel` unconditionally. `Obj_Spikes` :48958 toggles the
status Y-flip bit that selects `loc_2413E`, while `Sonic3kSpikeObjectInstance` picks its movement
from `subtype & $F`. Both rows modify upright structure the engine does not model; porting them
means porting that structure first, which would change shipped upright behaviour and belongs to
those objects' own work. Recorded in the table with the reason rather than implemented against a
shape the ROM does not have here.

Table: **60 covered, 6 partial, 46 missing, 4 n/a**. 221 focused tests green, `Skipped: 0`;
`-Pguards` 669/669.


### 2026-09-17 — Slice 2, part 8: the sprite render mirror

**Seven rows, one net effect.** `loc_10C62` (:22011), `sub_125E0` (:24716), `loc_138C8` (:26255),
`sub_15842` (:29336), `loc_15A7A` (:29594), `loc_16614` (:30453) and `sub_17D1E` (:33017) all
`eori.b #2,render_flags(a0)` right after their animator. The thing that makes them portable is what
runs immediately before: `Animate_Sonic` clears `render_flags` bits 0-1 and rewrites bit 0 from the
facing status (:24754-24757), so the XOR's net effect is that the player's Y-flip *equals the flag*
every frame the animator runs. Porting the XOR literally into an engine whose animator does not
rewrite the flags would alternate the sprite every frame — the same class of mistake as negating
`getRollHeightAdjustment()`.

`PlayableSpriteAnimation.applyReverseGravityRenderMirror` writes that net, under the same
`btst #1,object_control` gate that skips the animator (the engine's `isObjectMappingFrameControl`,
which already carries the "object mappings keep their paired flags" rule). Disabling the flag read
fails all three inverted characters; the FBZ wire-cage, rail, chain and propeller tests — the
engine's existing users of a player Y-flip — stay green, 255 focused tests in one invocation with
`Skipped: 0`.

Table: **67 covered, 6 partial, 39 missing, 4 n/a**, from 14 / 8 / 90 / 4 at the start of this
session.


### 2026-09-18 — Slice 2, part 9: the broad run caught the render mirror, and what it taught

**The end-of-slice category run went red on the first attempt, for a good reason.**
`run_categories.py --base 035e48a58 --run` selected the whole ordinary suite plus guards (shared
collision, animation and camera code changed): **2709 classes, 22049 tests, 7 failures, 0 errors,
27 skipped**, guards **669/669 green**, ordinary lane 1055 s. Every skip was an opt-in profile
(`openggf.audio.repeatedPlaybackBenchmark`, `openggf.checkpoint.measure`, `rewind.soak`,
`openggf.rewind.alloc.measure`, `openggf.aiz1.routes` and the like) — none silent.

All seven failures were mine, and all from part 8: six in `TestPlayableSpriteAnimation` and one in
`TestHeadlessTestFixture`, every one of the form "expected the native Y flip to be set, was clear".

**The lesson.** The engine's stored `renderVFlip` is not the ROM's `render_flags` bit 1. The ROM's
animator clears that bit every frame; the engine's *sets* it to encode native mapping orientation —
the flipped fourth slope bank (S1 walk at angle $18) and the negative-flip-type tumble — and objects
read it back to write it again. Writing the ROM's net into it therefore erased an unrelated meaning
the same field carries. The focused suites chosen for part 8 (the FBZ objects that set a player
Y-flip) did not cover the animator's own uses, so only the broad run could find it: a precise
demonstration of why the change-based selection widens to everything when shared code moves.

**The fix.** `AbstractPlayableSprite.renderVFlipForDraw` composes the flag at the three player draw
sites and leaves the stored flip alone. Package-private, so only `Sonic`, `Tails` and `Knuckles`
see it and it stays off the `@ModApi` surface this class pins; the ghost and hyper-trail samplers
keep sampling the stored value deliberately. The corridor test now asserts both halves — the drawn
flip follows the flag, and the stored flip does not.


### 2026-09-18 — Slice 2 demo clips (seeded)

Five clips in `~/Videos/OGGF/s3k-dez-bring-up/`, all at `8f5da1c8a`, S3K zone 11 act 2, Sonic solo,
cold load at the act's own start, 40 neutral frames of lead-in and lead-out. Every one **seeds
`Reverse_gravity_flag` itself** through the new `GameplayCaptureTool --reverse-gravity`; the ROM's
writers (`$58`, `$59`, `$5B`) arrive in the object slice, so none of this is reachable in normal
play yet. `INDEX.md` states that on every row.

| Clip | Shows | The `state.csv` line that proves it |
| --- | --- | --- |
| `020-inverted-run-320` | falls *up* out of the act start, lands on the ceiling, runs along it upside down | `yvel` positive while `y` falls 940 to 777; `air` clears at frame 58 on y=723 |
| `021-inverted-jump-320` | two ceiling jumps *down* the screen, away from the surface, falling back up onto it | frame 100 `yvel` = −1664 with `y` rising 718 to 748 |
| `022-inverted-roll-320` | rolling along the ceiling | `rolling` 1 for frames 90-106 at `gspeed` 684, and `y` = **718** rolling against 723 standing — `Player_DoRoll`'s reverse-gravity −5 centre move, visible in a capture |
| `023-camera-look-pans-320` | the look pans reversed | Up held: `cam_y` 627 to 695 (camera moves **down**). Down held: 627 to 527 (**up**) |
| `020-inverted-run-528` | the same run at 528 px | lands frame 47, same ceiling y=723 |

**Two things the captures corrected.** The first roll take never rolled: Down was pressed after the
player had already hit a wall and lost `ground_vel`, and `SonicKnux_Roll` (sonic3k.asm:23240-23258)
also refuses a roll while left or right is held. `state.csv` showed `rolling` flat at 0 before any
frame was opened — the skill's "read the CSV first" rule doing its job. And the run clips stop at
x=437 because that is where the act-start ceiling meets a wall: terrain, not a physics stall. A
longer inverted run needs a flat-ceiling stretch nobody has measured; the corridor the tests use
(x=$1ACC) is flat for only ±16 px.


### 2026-09-18 — Slice 2, part 10: the end-of-slice gate, and four more groups

**The gate that part 9 left open is closed, green.** Base `035e48a58`, head `b79e0f129` at the
time of the gate.

| Check | Result |
| --- | --- |
| The seven part-8 failures plus every `TestS3kReverseGravity*` | 122 tests, 0 failures, **0 skipped** — `TestPlayableSpriteAnimation` 48/48 and `TestHeadlessTestFixture` 8/8, the two classes that held all seven |
| `-Pguards` (standalone) | 669/669 |
| Four-class trace comparison | identical to the recorded `f60b3f3e2` baseline, failure for failure |
| `--preflight` | Java 21, Lua 5.4, PowerShell all present |
| `run_categories.py --base 035e48a58 --run` | run id `20260918T100237Z-e62d5573`. Ordinary **2710 reports, 22044 tests, 0 failures, 0 errors, 27 skipped**, 1028 s. Guards **85 reports, 669 tests, 0/0/0**, 195 s. Acknowledged. |

Every one of the 27 skips is an opt-in profile (`openggf.audio.repeatedPlaybackBenchmark`,
`rewind.soak`, the SOZ capture properties, `openggf.aiz1.*`) or an unavailable-GL assumption
(`Surfaceless EGL unavailable`, `OpenGL 4.1 unavailable`). None is a silently missing ROM: no
`@RequiresRom` class skipped.

The trace comparison's current side was measured in this worktree with `clean test` and
`-Ptrace-replay`, ROM paths absolute: `TestS1Ghz1TraceReplay` and `TestS1Mz1TraceReplay` green,
`TestS2Ehz1TraceReplay` red with 16388 errors at frame 6 on
`dynamic_art.outstanding_transfer_ids` (expected `[2]`, actual `[]`), `TestS3kAizTraceReplay` 3/16
red with 59 errors, first error frame 5497 `camera_x` expected `0x0010` actual `0x0012`. **The
`f60b3f3e2` side was not re-detached this session**: it is the baseline recorded identically in the
part-2 and part-3 entries, and the current numbers match it error for error and field for field.
That is a comparison against a twice-recorded baseline, not two fresh measurements.

**Four more groups, 67 → 80 covered.**

| Group | Rows | Commit | What the ROM actually said |
| --- | --- | --- | --- |
| G lost rings | 35550, 35621 | `c7fe5443b` | The table's recorded doubt was right. `loc_1A7E8` (:35675-35678) integrates `-y_vel` and keeps `addi.w #$18` **positive**; the engine did the sign conjugate (negated gravity, `+y_vel`). Those agree on position only if the launch velocity is negated too — and the spill loop (:35592-35613) has no flag branch, so every inverted spill arc was mirrored, throwing rings into the ceiling they stand on |
| F shields | 34594, 34666, 34747, 34911 | `5aedf369f` | All four `andi.b #1,status(a0)` **before** `ori.b #2`, so this is a set, not the toggle the ROM's own comment claims: the shield's Y-flip equals the flag. One owner, `ShieldAnimationArtLifecycle.reverseGravityMirror`; nothing added to `ShieldObjectInstance`, which is `@ModApi` |
| D sidekick | 26495, 27298, 27407 | `42b95dcde` | `loc_13B50` (:26493-26499) nets `+$C0`, but `Tails_CPU_target_Y` is **not** mirrored — the ROM writes it from the leader's raw `y_pos` before the branch. The two carry rows fall out of `renderVFlipForDraw`, which composes the flag at every draw and not only the ones the animator reached; carry sets `object_control`, so the animator never runs for a carried player |
| H solid objects | 41407, 41569, 41623, 41648 | `24d14f342` | `SolidObject_cont`'s reverse branch is `loc_1DFD6` plus exactly one `neg.w d3`. `loc_1E154` turns `y - d3 + 3` into `y + d3 - 3` — the asymmetric constant is the ROM's, from the upright path's extra `subq.w #1` |

**The measurement that mattered.** Group H's first version wrote the mirrored landing correctly —
a probe on every `player.setY` in the file showed `RG_LAND newCentreY=956` against an upright
`900`, both exactly 28 px from the block's centre — and the test still failed, because the very
next frame put the player back. The continued-ride site that actually runs is **not** the one the
`MvSonicOnPtfm` comment sits on; the file has two, and only the other one is reached for a full
solid. Instrumenting every Y write found it in one run. A landing test that did not step a second
frame would have passed and shipped a rider that snaps back through the platform.

**The fixture group H needed.** The measured reverse-gravity corridor at x=$1ACC is a 64 px gap —
a 38 px player plus a 16 px block does not fit with clearance on both faces — so
`TestS3kReverseGravitySolidObject` searches Death Egg act 1 for a clear column and guards it.
Both cases are expressed as an offset from the block's centre and asserted to be exact negations,
so the test never restates the block's own `d2`/`d3` and fails for any partial mirror.

**One row deliberately left missing.** `loc_1E44C` :41999 is not a sign flip. `loc_1E4D6`
(:42053-42071) rebuilds the comparison — feet at `y_pos - y_radius - 4` against `y_pos(a0) + d3` —
and lands at `objBottom + y_radius` where the upright path lands at `objTop - y_radius - 1`, a
deliberate one-pixel asymmetry. The engine's sloped/top path carries its own compensations, so
porting it by analogy with the other four would be a guess.

**Every new assertion was shown able to fail.** The lost-ring and sidekick tests were written RED
first, each with its upright control already passing. The shield test asserts a method that did
not exist before, so it could not have been RED: it was checked by stubbing
`reverseGravityMirror` to return `false` and confirming the insta-shield row goes red.

Reference table after this session: **80 covered, 4 partial, 28 missing, 4 n/a** (from 67 / 6 / 39
/ 4). Groups D and G are complete; F has 3 rows left and H has 1.

**The gate re-run, after the four groups.** `run_categories.py --base 035e48a58 --run` again
selected everything — 2712/2712 classes, full ordinary suite plus guards, because shared collision
code moved again. Run id `20260918T105742Z-cc4590b5`: ordinary **2712 reports, 22049 tests, 0
failures, 0 errors, 27 skipped**, 1040 s; guards **85 reports, 669 tests, 0/0/0**, 202 s;
exit 0, acknowledged. The 27 skips are the same set as the first gate, reason for reason.

Group H is a shared solid-object change that S1 and S2 run, so the four-class trace comparison was
repeated on top of it, `clean test` with `-Ptrace-replay` in this worktree, ROM paths absolute:

| Class | Recorded `f60b3f3e2` baseline | After `24d14f342` |
| --- | --- | --- |
| `TestS1Ghz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS1Mz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS2Ehz1TraceReplay` | red, 16388 errors, frame 6 `dynamic_art.outstanding_transfer_ids` (expected=[2], actual=[]) | red, 16388 errors, frame 6, same field and values |
| `TestS3kAizTraceReplay` | 3/16 red, 59 errors, frame 5497 `camera_x` expected `0x0010` actual `0x0012` | 3/16 red, 59 errors, frame 5497, same values |

Failure for failure identical; both reds stay **baseline-attributed**. Four classes, not the trace
profiles, and no evidence about any other class.

**No clips for this part, recorded rather than worked around.** None of the four groups can be
staged with the seeds `GameplayCaptureTool` has: lost rings need a ring count and a hit, the
shields need a shield, the sidekick rows need Tails to die or carry, and the solid-object rows need
an inverted player to reach a solid object — the act-start inverted run stops at x=437 on a wall,
and no solid object has been measured within reach of it. `INDEX.md` carries that table. Two things
would unblock all four: slice 3's `$58`/`$59`/`$5B` writers, which make the flag reachable in
ordinary play, or `--rings` and `--shield` seeds beside the existing `--emeralds` and
`--reverse-gravity`.


### 2026-09-18 — Two more reference rows, and slice 3's first writer

**Group A's remaining non-slice-3 rows, two of them closed.** Base `035e48a58`, head
`ff080949c` after the first commit of this session.

`loc_123DE` :24552 is the dead player's own off-screen test — the one that spends the life and
restarts the act — and its flag branch **rebuilds** the comparison rather than mirroring it.
Upright: `addi.w #$100,d0 / cmp.w y_pos(a0),d0 / bge locret`. Set: `subi.w #$10,d0 / cmp.w
y_pos(a0),d0 / bge loc_12410`. Two different offsets, `$100` against `$10`, and no
competition-mode `$70` on the reverse side — that `subi.w` sits after the branch, on the upright
path only. The engine owner is `PlayableSpriteMovement.hasFallenPastDeathRestartRow`, which now
carries both forms. Without it an inverted corpse, whose `-$700` launch and growing positive
`y_vel` `MoveSprite_TestGravity` integrates *up* the screen, never triggers the restart at all.

`loc_1515C` :28655 is the one row the ROM gets wrong, and it is now modelled as wrong.
`Tails_Test_For_Flight` puts `y_radius - default_y_radius` in **d1**, tests the flag, and on the
set side runs `neg.w d0` — a register holding nothing this site uses — before `add.w d1,y_pos(a0)`.
Every other Tails unroll site (`loc_14DA2` :28233, `loc_14FC4` :28500, `loc_1527C` :28748) negates
`d0` because `d0` *is* the adjustment there. `TailsFlightController.activate`'s write was already
unconditional and therefore already right; it now carries the `FixBugs = 0` branch comment and a
test that pins it. Measured delta is ±1, not ±5: Tails' radii are `$F`/`$E`.

**Rows deliberately left where they were, with the reason.** `Knuckles_Fall_From_Glide` :30921,
`Knuckles_Sliding` :30977 and :31004 all sit behind one prerequisite that is not a sign flip:
`checkGlideFloorDist` (`PlayableSpriteMovement:2534`) probes with
`ObjectTerrainUtils.checkFloorDistWithFlipAwareAngle` directly, while the ROM's `.continueSliding`
calls **`sub_11FD6`**, the swapping wrapper. Porting the three `neg` sites without first routing
the glide probe through the wrapper would negate a distance measured against the wrong surface.
:31068 and :31205 are whole alternate climb bodies (`.climbingUp_ReverseGravity`) and need a
vertical-wall fixture that does not exist yet. `loc_1E44C` :41999 keeps the reason recorded at
`24d14f342`. `Touch_Monitor` :20802 and `Obj_Spikes` :48958 were re-confirmed blocked on upright
behaviour the engine does not model, not on reverse gravity. The three K rows need the act 2 boss,
which is slice 8. The dash-dust rows :34038/:34113 are a two-line change in
`SpindashDustController.draw` (`TAILS_Y_OFFSET` negates, `vFlip` becomes the flag) but that class
is `@com.openggf.game.ModApi`, so it cannot grow a test seam, and `PlayableSpriteRenderer.drawFrame`
is the only observable — left for whoever can stub that renderer cheaply.

**Slice 3 opens with `$5B`, and the flag is now reachable in ordinary play.**
`S3kDezGravitySwapObjectInstance` implements `Obj_DEZGravitySwap` (sonic3k.asm:95472-95543): the
eleven invisible act 2 triggers, six unflipped and five X-flipped.

| ROM fact | Where it came from |
| --- | --- |
| The init falls through into the first crossing check in the same frame — there is no `rts` between `move.l #loc_49214,(a0)` and `loc_49214` | :95483-95484. A trigger seeded at exactly the player's x keeps the "on the left" latch (`bhs`) and then fails the "still on the left" test (`bhi`), so it fires on its own spawn frame |
| It writes, never toggles | Both bodies run `move.b #0,(Reverse_gravity_flag).w` before the conditional `move.b #1` (:95511/95514, :95536/95539) |
| The flip bit chooses the direction, not the value | Left-to-right sets when `btst #0,render_flags(a0)` is **clear** (`bne.s locret`); right-to-left sets when it is **set** (`beq.s locret`) |
| The Y band is `[y_pos - $20, y_pos + $20)` | `$30(a0) = $20`, then `cmp.w d2,d4 / blt` and `cmp.w d3,d4 / bge` (:95495-95507): signed word compares, top edge inclusive, bottom exclusive |
| The side latch is consumed even when nothing is written | `move.b #1,-1(a2)` sits **before** the band test (:95492-95495) |
| Player 2 is never watched | `lea (Player_1).w,a1` with no second call (:95217-95219) |

Every one of those is a separate assertion in `TestS3kDezGravityObjectsHeadless`, and the
assertions were shown able to fail: removing the band test reddens both band cases *and* the
latch case (the out-of-band precondition starts writing); replacing `!xFlipped()` with `true`
reddens the X-flipped left-to-right case; replacing `xFlipped()` with `false` reddens the
X-flipped right-to-left case. The rewind spot captures **after** the object has set the flag,
runs a clearing crossing forward, restores, and replays that crossing — a restore that returned
the global flag but not the object's `$32` latch would run `sub_49228` again and set the flag
instead of clearing it, so the replay is what makes the latch's round trip observable.

**The wiring bug none of that would have caught.** `render_flags` bit 0 does not come from a
subtype — it comes from the placement record's **y word top nibble**, which
`CommonPlacementParser` reads as `(yWord >> 13) & 3`. Decoding `DEZ2_Sprites` directly shows the
eleven `$5B` records as six with top nibble `0` and five with top nibble `2`; nibble `2` is bit
13, so it arrives as `renderFlags` bit 0, which is the bit the ROM tests. Read the wrong bit and
all eleven behave as unflipped: the census still passes, the object still resolves, the focused
behaviour tests still pass because they construct their own spawns, and half of act 2's gravity
triggers quietly do the wrong thing. `TestS3kDezPlacementCensus` now asserts the 6/5 split from
the ROM's own records so that decoding is pinned rather than assumed. The `$5B` sites, decoded:
x `$0700`/`$0B00`(x2)/`$0D80`/`$1280`/`$1A00`/`$1A40`/`$1F40`/`$2640`/`$3090`/`$3100`, all
subtype `$00`.

One assertion in that class is weaker than the others and is recorded as such: the
"Player 2 writes nothing" case moves the sidekick across the trigger and then calls `update`
with the leader, which today can only pass because the update signature takes one player. It
does not exercise a sidekick loop, because there is none; it is a guard against someone adding
one, not evidence about existing behaviour.

Census ratchet: act 2 placeholders 337 → 326, concrete 157 → 168, `$5B` added to
`CONCRETE_DEZ_IDS`. Reference table: **86 covered, 4 partial, 22 missing, 4 n/a**.

`-Pguards` caught three consequences the focused tests could not, and then caught a fourth when
the first fix was wrong. `TestRewindArchitectureGuard`'s override baseline needed the two new
entries with their triage note. `Sonic3kObjectProfile` needed `$5B` — but *not* in
`SKL_ONLY_IDS`, which was the obvious-looking place and failed
`sharedIdsAreExactlyTheIdsImplementedInEveryZone` on the next run. The registry now resolves
`$5B` to a real class in **every** zone, because S3KL `$5B` is `Obj_MGZTopPlatform` and was
already concrete; only the SKL side was a placeholder. So the id belongs in
`SHARED_IMPLEMENTED_IDS` even though the two objects are unrelated. Neither consequence is
visible from a `-Dtest=` run, and the second was not visible from the first guard failure
either — the profile guard has two tests and only one of them fired the first time.

**What slice 3 still owes.** `$58` (5 act 2 placements) is read and understood but not written:
`SolidObjectFull` with `d1=$1B, d2=8, d3=9`, `d6 & $14` for Player 1's top/bottom contact bits,
the pad sinking 8 px (negated by its own Y-flip bit), `sub_48B40` releasing both players
(velocities zeroed, `Status_InAir` set, `Status_OnObj` cleared — unconditionally, with only the
8 px nudge masked by `$14`/`$28`), `sfx_Transporter`, then `$30 = 3` counting down to the
`eori.b #1` toggle four frames later and a 20-frame rearm that will not start while anything is
standing on the pad (`loc_48B7E`, `loc_48B9C`, :94874-94910). `$59` (21 placements) is four
routines per player with its own animation table `RawAni_48DB2` and a `Perform_Player_DPLC` call;
its flag write is `loc_48DF2`, `Player_1` only, taking the value from subtype bit 7 via
`rol.b #1,d0 / andi.b #1,d0`. `$5A` only reads the flag. `$5C`, `$5F` and `$61` contain no flag
reference at all — they move the player with `object_control`, and any gravity change near them
comes from a `$58`/`$5B` placed alongside.

**No clips this session.** Slice 3's first writer makes the flag reachable in principle, but the
staged captures the previous session listed still need a player route that reaches one of the
eleven `$5B` sites, which is act 2 cold-route work the gate for this change came first. The
`INDEX.md` table of unstaged clips stands unchanged.

**Handover: what the next session picks up, in order.**

1. **`$58` `Obj_DEZGravitySwitch`** (5 act 2 placements, x/y decoded in the part-3 entry). The
   whole object is `loc_48AD6`-`loc_48BE4` (:94809-94910) and is four routines, not one:
   `SolidObjectFull` with `d1 = $1B, d2 = 8, d3 = 9, d4 = x_pos`; `swap d6` then
   `andi.w #$14,d0` for Player 1's contact bits (`$28` is Player 2's, and Player 2 only ever
   reaches `sub_48B40`'s release, never the toggle); on a press, `mapping_frame = 1`,
   `$30 = 3`, and the pad sinks `d0 = 8` (negated by its own `render_flags` bit 1) before
   `sub_48B40` runs for **both** players — zeroing `anim`, `flip_type`, `double_jump_flag`,
   `jumping`, `spin_dash_flag`, `ground_vel`, `x_vel`, `y_vel`, setting `Status_InAir` and
   clearing `Status_OnObj` unconditionally, with only the final `add.w d0,y_pos(a1)` nudge
   masked by `d3`; then `sfx_Transporter`. `loc_48B7E` counts `$30` down and on the frame it
   goes negative (four frames after the press) does `eori.b #1,(Reverse_gravity_flag).w` — a
   **toggle**, unlike `$5B`'s write — sets `$30 = 19` and moves to `loc_48B9C`, which will not
   start the rearm while `Status_OnObj(a0)` is set (it resets `$30` to 0 each such frame).
   Note `sub_48B40` returns immediately when `object_control(a1)` is non-zero.
2. **`$59` `Obj_DEZTeleporter`** (21 act 2 placements) — the biggest of the seven. Four routines
   per player through `off_48C3C`, run for Player 1 and Player 2 with separate `$30`/`$3A`
   state blocks. Its flag write is `loc_48DF2` (:95080), Player 1 only
   (`cmpa.w #Player_1,a1`), taking the value from subtype bit 7 via `rol.b #1,d0 /
   andi.b #1,d0`; `1(a4)` is set when that value differs from the current flag, and the exit
   offsets at `loc_48E2C`-`loc_48E8E` read it. It also owns `loc_48CB0`'s unroll (with the
   ordinary `neg.w d0` under the flag, :94990) and `loc_48D78`'s Y-flip on the captured player
   frames (:95045), which is the row that needs `Perform_Player_DPLC`.
3. **`$5A` `Obj_DEZGravityTube`** (24/17) reads the flag only: `loc_48FBA` mirrors `flip_angle`
   on exit and `loc_4904A` Y-flips while riding.
4. **`$5C`, `$5F`, `$61`** contain no `Reverse_gravity_flag` reference at all. They move the
   player with `object_control`; any gravity change near them comes from a `$58`/`$5B` placed
   alongside. Do not look for a flag branch in them.
5. **The cold act 2 route and the clips**, which need at least `$58` or a route that reaches a
   `$5B` site. Until then the `INDEX.md` clip table stays as it is.
6. **The eleven open group A-I rows**, listed with their reasons in
   [s3k-known-bugs](../../status/s3k-known-bugs.md). The cheapest real one is Knuckles:
   route `checkGlideFloorDist` through the `sub_11FD6` wrapper first, then :30921, :30977 and
   :31004 fall out together.

**The four-class trace comparison for this session**, `clean test` with `-Ptrace-replay` in this
worktree, all three ROM paths absolute, on top of `04a1e84f7`:

| Class | Recorded `f60b3f3e2` baseline | After `04a1e84f7` |
| --- | --- | --- |
| `TestS1Ghz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS1Mz1TraceReplay` | 1/1 green | 1/1 green |
| `TestS2Ehz1TraceReplay` | red, 16388 errors, frame 6 `dynamic_art.outstanding_transfer_ids` (expected=[2], actual=[]) | red, 16388 errors, frame 6, same field and values |
| `TestS3kAizTraceReplay` | 3/16 red, 59 errors, frame 5497 `camera_x` expected `0x0010` actual `0x0012` | 3/16 red, 59 errors, frame 5497, same values |

Failure for failure identical; both reds stay **baseline-attributed**. As in the previous
sessions, the `f60b3f3e2` side was not re-detached — it is the baseline recorded identically in
the part-2, part-3 and part-10 entries, and these numbers match it error for error. That is a
comparison against a thrice-recorded baseline, not two fresh measurements. Four classes, not the
trace profiles, and no evidence about any other class.

**The gate.** Preflight (Java 21, Lua 5.4, PowerShell) passed in the launch environment.
`run_categories.py --base 035e48a58 --run` selected **BROAD**: 2713/2713 classes, the full
ordinary suite plus all structural guards in a separate JVM. Stated before launching: tens of
minutes, with the runner's 40-minute per-invocation timeout as the stopping rule.

| Lane | Result |
| --- | --- |
| Ordinary | 2713 reports, **22066 tests, 1 failure, 0 errors, 27 skipped**, 1039.9 s |
| Guards | 85 reports, **669 tests, 0/0/0**, 194.4 s |

Run id `20260918T121620Z-f86a6ea0`. All 27 skips are opt-in profiles
(`openggf.audio.repeatedPlaybackBenchmark`, `openggf.checkpoint.measure`, `rewind.soak`,
`openggf.rewind.alloc.measure`, the `openggf.aiz1.*` trio, the eight `soz.*` capture properties,
`openggf.scrollNative`, `openggf.slotNative`, `openggf.performance.rewindDispatch.measure`,
`openggf.test.gl.native`, the shaderlib and background-sampling diagnostics) or unavailable-host
assumptions (Surfaceless EGL, OpenGL 4.1, a local BizHawk reference, a local timeline capture,
and one CPZ spin-tube capture assumption). **None is a missing ROM**: no `@RequiresRom` class
skipped, which is the check that a silent ROM-path mistake would fail.

The one failure is a third ratchet, and it is the sanctioned shape:
`TestRemainingRewindTailInventory` counts round-trip tail types and read
`total=1110 passed=875` against a recorded `1109/874`. The new class is one more type and one
more **passing** type; `graphCovered` and `noCodec` are unchanged and no failure bucket grows. It
was bumped to `1110/875` with that note and verified narrowly rather than by repeating the broad
run, since nothing else in the tree changed. Three ratchets in one slice — the profile registry,
the rewind override baseline and this one — is worth recording: a new object class touches more
recorded inventories than a new branch does, and none of the three is visible from a focused run.

### 2026-09-18 — Slice 3 clips, `$58`, and two measurements that changed the plan

**The `$5B` sites are not in open space.** The part-3 entry records them as "all in open
corridor, no terrain within ±32 px". That is wrong, and it mattered: every one of the
eleven has flat floor about `$1F` below the trigger centre, flat across at least ±64 px,
and that floor sits *inside* the trigger's `±$20` Y band. A walking player therefore
crosses the band as a matter of course, which is what makes a positioned-entry clip
possible at all. Corrected here and in `INDEX.md`.

Finding it needed the probe to be fixed first, and the fault is worth recording because it
produces a confident wrong answer: `TerrainCheckResult.hasCollision()` means
**overlapping**, not "found". A floor 8 px below the probe returns `hasCollision() == false`
with `distance() == 8`; nothing found returns `distance() == 32767`. A sweep written around
`hasCollision()` reported *no terrain anywhere in act 2*, which is obviously false and was
only caught by running the same probe against the known corridor at x=`$1ACC`. Calibrate a
terrain sweep against a known-good point before believing a negative result.

**Three clips, filmed from real triggers with no `--reverse-gravity` seed.** `030` (320 and
528) walks Sonic into the unflipped `$5B` at `$1A40,$08C0`: the flip lands on frame 104 at
x=`$1A44`, one frame past the trigger, with `air` 0→1 and `yvel` growing by `$38` while `y`
decreases. `031` collects three rings and crosses the **flipped** `$5B` at `$2640` right to
left — the direction that sets the flag on a flipped placement — and carries the rings
inverted. `032` is `030` as Knuckles, flipping on the same frame at the same x. The
per-clip evidence is in `INDEX.md`.

**Two clips are still blocked, and the reason is now measured.** A positioned entry
teleports the *leader only*: the sidekick Tails take was made, inspected and deleted
because Tails never leaves the act start. And decoding `DEZ2_Sprites` and `DEZ2_Rings`
around all eleven sites shows no monitor and no ring within `$180` px of six of them, the
nearest concrete hurt block 256 px across and 488 px up from the nearest trigger, and every
site's own neighbour a `$5A` gravity tube that slice 3 has not implemented. The rings/hit,
shield and solid-object clips are blocked on **slice 3's remaining objects and the act 2
route**, not on reachability.

**`$58` `Obj_DEZGravitySwitch` landed**, the second and last of the two writers that a
player can reach on foot. It is a **toggle**, not a write: `eori.b #1,(Reverse_gravity_flag).w`
(:94879). Its three routines, and the assertions that pin each:

| ROM fact | Test |
| --- | --- |
| The toggle lands on the **fourth** update after the press: `move.w #3,$30(a0)` (:94822) then `subq.w #1,$30 / bpl` (:94877) | `theGravitySwitchTogglesOnTheFourthUpdateAfterThePress` asserts nothing toggles on updates 1-3 |
| It is a toggle, so a second press reverses it | `asecondPressOfTheGravitySwitchTogglesBack` starts with the flag set and asserts it clears |
| `andi.w #$14,d0` (:94817) is Player 1's top **and bottom** contact bits, which is why the pad works from either face and therefore under either gravity | `theGravitySwitchIsPressedFromItsUndersideAsWellAsItsTop`, and a side contact is rejected |
| `sub_48B40` (:94848-94861) releases the rider: `ground_vel`, `x_vel`, `y_vel` zeroed, `Status_InAir` set, `Status_OnObj` cleared | `theGravitySwitchReleasesTheRiderThatPressedIt` asserts all five |
| `loc_48B9C` (:94892-94896) resets `$30` to 0 every frame `Status_OnObj(a0)` is set, so it cannot rearm under a standing player; otherwise 20 frames (`move.w #20-1,$30`) | the occupancy and rearm tests |

**One of those tests was tautological on its first version, and the break caught it.**
`theGravitySwitchDoesNotRearmWhileItIsStillOccupied` originally held contact for 60 frames
and asserted `isPressed()` at the end. Removing the occupancy reset left it **green**: the
pad rearmed, was immediately pressed again by the still-reported contact, and `isPressed()`
read true either way — and with a ~24-frame press/toggle/rearm cycle, sampling the flag at
the end cannot discriminate either. It now counts the flag's *transitions* across 120
frames and asserts zero; with the reset removed that reads 4. Every other new assertion
failed on its first break: `TOGGLE_DELAY = 0`, ignoring the underside bit, and replacing the
toggle with a write each reddened their own tests, five failures across the five mechanisms.

Two gaps recorded rather than invented. The pad has real art
(`Map_DEZGravitySwitch`, `make_art_tile(ArtTile_DEZMisc+$143,1,0)`, two mapping frames) and
none of it is registered for Death Egg, so the pad is **solid and functional but invisible**;
and `sfx_Transporter` (:94833) has no `GameSound` constant, so the press is silent. Adding
either is shared-surface work that belongs with the DEZ misc-object art, not with this
slice. Both are in the act 2 matrix as inherited gaps, and no `$58` clip was filmed,
because a clip of an invisible pad would show nothing.

Census: act 2 placeholders 326 → 321, concrete 168 → 173. Reference table **87 covered, 4
partial, 21 missing, 4 n/a**. Four ratchets moved again (census, profile, rewind override
baseline, rewind tail inventory) — the same four as `$5B`, which is now a reliable checklist
for the next object. `-Pguards` added a fifth thing to that checklist:
`TestObjectServicesMigrationGuard` rejects `services() == null` in object code (use
`tryServices()` for an optional path), which both `$58` and `$5B` were doing. Fixed in both;
the guard is the only thing that would have caught it, since a null `services()` never
happens in a test that adds the object through the `ObjectManager`.

**Handover, revised 2026-09-18 after `$5B` and `$58`.** Both flag writers a player can
reach on foot are done; what remains of slice 3 is the objects that *move* the player.

1. **`$59` `Obj_DEZTeleporter`** (21 act 2 placements) is the big one: four routines per
   player through the jump table `off_48C3C` (:94933-94936), run twice with separate state
   blocks at `$30(a0)` for Player 1 and `$3A(a0)` for Player 2.
   - Capture (`loc_48C44`, :94944-94990): an X window of `$10` around the object, widened by
     `$A` when `status` bit 0 is set; a Y window of `$40` centred on it; and four refusals —
     `object_control(a1)` non-zero, `Status_InAir` set, `_unkFAB8` bit 0, and an
     already-captured player whose `interact` points at another teleporter. On capture it
     sets `object_control = $83`, zeroes the velocities, snaps `x_pos(a1)` to the object's,
     and unrolls with the ordinary reverse-gravity `neg.w d0` (:94990).
   - Ride (`loc_48D2C`, :95008-95045): `4(a4)` climbs by 8 to `$300`, at which point the
     subtype's low 7 bits become the frame budget `6(a4)` and half of it `8(a4)`, and
     `y_vel` is `±$1000` by `status` bit 1. The pose comes from `RawAni_48DB2` and its
     companion flag table `byte_48DBE` (:95051-95053), and the reverse-gravity row is
     `ori.b #2,d0` on that flag byte (:95045) before it is OR-ed into `render_flags(a1)`.
   - **The flag write** (`loc_48DCA`, :95075-95080): only when `6(a4) == 8(a4)` (the
     midpoint) and only for `Player_1` (`cmpa.w #Player_1,a1`). The value is subtype bit 7
     via `rol.b #1,d0 / andi.b #1,d0`, and `1(a4)` is set when that value *differs* from the
     current flag — the exit offsets at `loc_48E2C`-`loc_48E8E` read `1(a4)` to decide how
     far to nudge the player, with separate constants for Sonic and Tails.
   - Release (`loc_48E94`, :95160-95168): clears the state block once the player is more
     than `$10` from the object in X.
2. **`$5A` `Obj_DEZGravityTube`** (24 act 1, 17 act 2) only *reads* the flag: `loc_48FBA`
   mirrors `flip_angle` on exit and `loc_4904A` Y-flips the rider. It is the neighbour of
   every `$5B` site, so it is also what blocks the remaining clips.
3. **`$5C`, `$5F`, `$61`** contain no `Reverse_gravity_flag` reference at all. Do not go
   looking for one; they move the player with `object_control` and any gravity change near
   them comes from a `$58`/`$5B` placed alongside.
4. **The checklist a new gravity object needs**, learned twice now and stable: the class,
   its SKL registration branch, a RED test per ROM mechanism (and *break each one* — one of
   `$58`'s was tautological until the break exposed it), a rewind sidecar plus a
   capture/restore/replay spot, and **five** recorded inventories — the DEZ census, the
   S3K object profile (`SHARED_IMPLEMENTED_IDS` when the S3KL side is already concrete, not
   `SKL_ONLY_IDS`), the rewind override baseline, the rewind tail inventory, and
   `TestObjectServicesMigrationGuard`'s no-null-check-on-`services()` rule. Only `-Pguards`
   sees the last three.
5. **Then the cold act 2 route** on the `ssz`-named DEZ fixture's native input, and the
   clips that are still blocked: sidekick (needs a team teleport or the route), rings + hit
   + lost rings, a shield, and a solid-object ride.
