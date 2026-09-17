# S3K Death Egg Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-s3k-dez-bring-up` in `.worktrees/ai-s3k-dez-bring-up`;
execution base develop `9cba6dbb6` (pin this SHA for the combined change-based validation; re-pin
and re-measure if the branch is cut later). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to **Sonic 3 & Knuckles** Death Egg: `$B00`, `$B01` and
the final-boss act `$1700` (zone `$17` act 0). Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[dez-analysis.md](../research/s3k-zones/dez-analysis.md).

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
- Work stays on the branch until a major gain (at minimum: cold `$B00` → `$B01` boss → `$1700`
  load). No per-increment develop merges. Reverse gravity ships with DEZ in the same merge (user
  decision 2026-09-17); its shared validation and S1/S2 non-regression still run before that merge.

## Scope decisions (HPZ/DDZ precedents)

| Question | Decision | Reason |
| --- | --- | --- |
| Cold entry | Level-select/direct `$B00` load. The SSZ → DEZ launch cutscene belongs to whichever of the SSZ and DEZ campaigns lands second | `usesLevelIntroPlayerRun()` already covers `$B00` (`Obj_LevelIntro_PlayerRun`, `loc_6986`). Never position past the intro |
| `$1700` entry | Through the `Obj_DEZEndBoss` exit (`Act3_flag`, `Act3_ring_count`, `Act3_timer`, `StartNewLevel $1700`), plus an independent direct `$1700` load for short checks | ROM level select lists `$1700` as "DDZ act 2" (`sonic3k.asm:10161`); the engine level select has no such entry (`Sonic3kLevelSelectConstants:96-97`). Adding it is in scope |
| Exit | Implement `loc_803D6` in full: `SaveGame`, then `$C00` when `Player_mode < 2` and `Chaos_emerald_count == 7`, else `$D01`, else (`Player_mode == 3`) `Game_mode 0` | Closes DDZ's recorded dependency. After it lands, DDZ's cold entry inherits the camera fraction and `V_int_run_count` from a real chain: re-measure and remove the DDZ seeded-entry caveat. `$D01` stays the ending campaign; record what the engine does after the request |
| Roster | Mandatory: Sonic + Tails, Sonic alone, Tails alone. Knuckles: level-select access to `$B00`/`$B01` only (user decision 2026-09-17): he must load and play both acts from level select with correct reverse-gravity behaviour, but no Knuckles story route advances into DEZ and no Knuckles cold-chain, `$1700` or trace obligation exists | `LaunchProfile.sanitizedFor` allows native S3K Knuckles and ROM level select does **not** deny Knuckles `$B00`/`$B01` (`LevelSelect_CheckKnuckles` denies `$A00`, `$C00`, `$1600`, `$1700`). His story never reaches DEZ, but the ROM carries Knuckles reverse-gravity code (glide, slide, wall climb). No Knuckles `$1700` row from level select; the chained `$1700` with Knuckles is recorded, not mandatory |
| Tails differences | Own rows | Miniboss landing Y `$3B0` vs `$3AC` (`loc_7E44C`), `$D01` exit, flight and carry under reverse gravity (`Tails_Carry_Sonic`, `Tails_Test_For_Flight`) |
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
  exists, is cleared at level load and is snapshotted. Consumers today: springs, `SolidObjectProvider`,
  `ObjectTerrainUtils`, lost rings/`RingManager`, `GlideWallGrabTerrain`, `TailsCarryController`,
  `SidekickCpuController`, `PlayableHurtRadiusTransition`, Super Tails flickies, FBZ wire cage, and
  two lines in `PlayableSpriteMovement`. The ROM has **116** `Reverse_gravity_flag` references (the
  analysis says "~20+"): `MoveSprite_TestGravity(2)`, `Player_TouchFloor`, `Player_HitCeiling(AndWalls)`,
  `Tails_/Knux_DoLevelCollision`, `Sonic_/Tails_/Knux_Jump`, `Player_JumpFlipSet`, `Player_DoRoll`,
  `Sonic_Balance`, `*_RollSpeed`, `*_InputAcceleration_Path`, `Call_Player_AnglePos`,
  `ChooseChkFloorEdge`, `Player_Boundary_CheckBottom`, `MvSonicOnPtfm`, `SolidObject_cont`,
  `SolidObjectTopSloped_1P`, `Touch_Monitor`, `Obj_Spikes`, `Obj_DashDust`, `Obj_Tails_Tail`, all
  four shields, `Tails_Catch_Up_Flying`, `Tails_Check_Screen_Boundaries`, and the render flip
  (`eori.b #2,render_flags` after `Animate_Sonic`, `loc_10C62`). This is the campaign's largest
  shared change.
- **The act 2 boss is a gravity boss.** `Obj_DEZEndBoss` reads the flag (`sub_7F8A0` inverts its
  `$38` gravity, `sub_7F8CA`) and clears it on exit (`loc_7FC3E`). Slice order follows.
- **No S3K DEZ production code beyond loading.** Present: level data and music
  (`Sonic3kZoneRegistry` 11 and 23), `$B00` intro run, slope-angle rule
  (`Sonic3kZoneFeatureProvider:63`), results-screen DEZ title-card exception, PLC art for
  Spikebonker, Chainspike, still sprites `$30-$32` and the DEZ door (`addDezEntries`),
  `FbzDezPlayerLauncher` (`$78`), door (`$3C`), shared springs/spikes/monitors/starposts. Absent by
  name and by role: `Sonic3kDEZEvents`, any runtime state, any scroll handler (both zones fall to
  the default; **`$1700` currently gets `hpzHandler`** because the provider keys zone `$17` without
  the act), `AnPal_DEZ1/2`, `AniPLC_DEZ`, every SKL object `$4A-$61`, badniks `$A4/$A5` (the
  S3KL ids are Sparkle/Batbot), `$A6/$A7` bosses, `Obj_DEZ3_Boss`, the `$1700` resource profile
  (only `$1701` has one), `InvisibleShockBlock` (`$6D` SKL; verify), level-select `$1700`.
  No `TestS3kDez*` and no matrix; coverage backlog rows are "Audit pending".
- **Placements** (decoded from `Levels/DEZ/Object Pos`): act 1 has 365 objects, act 2 has 494,
  `$1700` none. Gravity switch `$58`, teleporter `$59`, gravity swap `$5B`, hub `$5C`, retracting
  spring `$5D` and floating platform `$4A` are act 2 only; hover machine `$5E`, gravity room
  `$5F`, bumper wall `$60`, puzzle `$61`, lift pad `$4E` and launcher `$78` are act 1 only. Act 1
  still flips gravity through `$5A` tubes and `$5F`. Lightning `$52` (48/94) and torpedo launchers
  `$4D` (36/38) dominate.
- **The act 1 → 2 signal is the results object.** No DEZ boss sets `Events_fg_5` before line
  171700; the seamless change is driven by the shared results/end-sign path. Verify the engine's
  results object raises the event for DEZ before building the transition.

## Design: who owns what

Resolve these owners before any consumer.

| State | ROM | Engine owner |
| --- | --- | --- |
| `Reverse_gravity_flag` | Global byte `$F768`; set by `$58/$5A/$5B/$5C/$5F`, teleporter, act 2 boss, debug A | Stays in `GameStateManager` (already rewound). All writers go through one setter; add the level-load clear test. A semantic capability on the S3K physics/feature provider (`GameRules`-level: "game supports reverse gravity") gates the shared branches; **no zone check**, matching the ROM, which tests only the flag. S1/S2 never set it |
| Inverted player physics | The 116-reference set above | `PlayableSpriteMovement`, collision probes and `AbstractPlayableSprite` render flip, each branch citing its routine and commented for `FixBugs = 0`. Position writes through `NativePositionOps`. One owner for "effective floor sensor direction" so ground, air, roll and Knuckles glide/climb read the same answer |
| Event words `Events_fg_4/5`, `Events_routine_fg/bg`, `Events_bg+$00..$16`, `Act3_*` | `DEZ1/2/3_Screen/BackgroundEvent`, `Obj_DEZEndBoss`, `Obj_DEZ3_Boss` | New `S3kDezZoneRuntimeState` (pattern `DdzZoneRuntimeState`) with a `RewindSnapshottable` adapter, added to `currentRuntimeStateUsesThisEventInstance` (the DDZ restore bug). `Act3_*` carry is a game-state field, not zone state: it crosses a level load |
| Events, camera locks, seamless act change | `DEZ1_BackgroundEvent` stages 0-1 (queue DEZ2 blocks/patterns, PLC `$38`, wait `Kos_modules_left == 0`, reload `$B01`, offset X −`$3600` Y +`$400`, `Offset_ObjectsDuringTransition`, `Pal_DEZ2+$20`), boss gradual bounds | New `Sonic3kDEZEvents`, registered in `Sonic3kLevelEventManager`; reuse the AIZ/ICZ/MHZ seamless-transition owner and `S3kCameraGradualObjectInstance`/`S3kCameraStoredBounds` |
| Layout mutation | DEZ1 `$BD` at `$14(a3)+$6E`; DEZ2 `$D7,$DC,$D7` and `$BC`; `$1700` chunk pairs `$603/$903/$603/$807`, BG layout clear | `ZoneLayoutMutationPipeline`/`LevelMutationSurface`, then targeted redraw. A full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes |
| Scroll | `PlainDeformation` with BG camera forced to 0,0 (acts 1-2); `$1700`: `sub_5A508`, `sub_5A76C`, `loc_5A734`, `ApplyDeformation2`, `ShakeScreen_Setup`, FG drawn through the BG event, `Draw_PlaneVertBottomUp` stages | New `SwScrlS3kDez` (acts 1-2: confirm the default handler really produces a static BG, do not assume) and `SwScrlS3kDezFinalBoss`; provider keys `$17` on act. Plane swap reuses the DDZ/FBZ2 FG-plane render mode, no `@ModApi` change |
| Palette | `AnPal_DEZ1` (3 channels) / `AnPal_DEZ2` (2), boss lines `Pal_DEZMiniboss1/2`, `Pal_DEZEndBoss`, `$1700` palette `$40`, boss flashes | `Sonic3kPaletteCycler` cases for `$0B` and `$17` act 0; boss writes through `S3kPaletteOwners`/`S3kPaletteWriteSupport` so cycles and flashes do not fight over line 2/3 |
| Animated art | `AniPLC_DEZ` (8 scripts, five gated on `Level_trigger_array[0,1,3,4]`), `$1700` laser DMA `sub_5A79E` (`ArtUnc_DEZFBLaser` → tile `$208`) | `Sonic3kPatternAnimator` plus a trigger-array reader; the laser is a direct upload owned by the final-boss events. Script 7's `$84` frame count is unresolved (see open questions) |
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
boundary review → evidence entry. Independent review for 1, 2, 5, 8, 9, 10; families 3, 4, 6 share
one each.

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 0. Baseline and identity | Three matrices and coverage-backlog rows; trace identity table above committed to the frontier log; current replay frontier of the DEZ and `$1700` classes (command, commit, first error); placement histogram per act with registered/unregistered split; resources (`Pal_DEZ1/2`, PLC `$36/$38/$4C`, `PLCKosM_DEZ`, title cards, music); `raw-00` captures of all three acts as they are today | The "before" footage exists first. Confirm what `$1700` loads at all without a resource profile |
| 1. Presentation foundation | `S3kDezZoneRuntimeState`, `Sonic3kDEZEvents` shell, `SwScrlS3kDez`, `AnPal_DEZ1/2`, `AniPLC_DEZ` with trigger-array gating, DEZ1/DEZ2 screen-event chunk writes | Counter/step/limit and timer reload values from the tables; act 1 runs channel 0 then falls into act 2's; who sets each `Level_trigger_array` entry; wide-viewport BG edges |
| 2. Reverse gravity core | The shared player set listed in Findings, in three reviewed steps: (a) air/ground movement, floor/ceiling sensor swap, jump, roll, balance, render flip, bottom boundary; (b) solids, platforms, springs, spikes, monitors, rings, dust, shields; (c) Tails flight/carry/CPU catch-up, Tails' tails, Knuckles glide/slide/climb, Super forms | Driven by a test-only flag write and the ROM's debug-A toggle before any DEZ object exists. Adjacent phases: flip while airborne, while rolling, while standing on an object, while hurt, on a slope; flip back. Every branch must be inert with the flag clear: S1/S2/S3K trace non-regression is the gate |
| 3. Gravity objects | `$58` switch, `$5B` swap, `$5C` hub, `$5A` tube, `$5F` room, `$61` puzzle, `$59` teleporter (four flag references) | Crossing direction and `render_flags` bit 0 select set vs clear; sidekick crossing; which participant owns the global flag when P1 and P2 are on opposite sides |
| 4. Traversal objects | `$4A` platform, `$4B` tilting bridge and `$4F` staircase (shared mappings), `$4C` hang carrier, `$4D` torpedo launcher, `$4E` lift pad, `$50` conveyor belt, `$52` lightning, `$53` conveyor pad (flag-aware), `$55/$56` energy bridges, `$5D` retracting spring, `$5E` hover machine, `$60` bumper wall, `$6D` shock block, Spikebonker, Chainspike, still sprites and door verification | Art from `ArtTile_DEZMisc/Misc2/2Extra` bases; act 1 PLC `$36` puts miniboss art at `Misc2`, act 2 PLC `$38` replaces it with `DEZ2Extra`: art keys differ per act. Slot and allocator order for children |
| 5. Light tunnels | `$57` launcher, `Obj_DEZTunnelControl`, `DEZTunnelPaths`, modes Normal/CircleLarge/CircleSmall/SineDown/SineUp, scale and wait tables, `Obj_DEZTransRingSpawner`/`TransRing` | Path parsing and fixed-point; sidekick capture; release velocity; camera follow; rewind mid-tunnel |
| 6. Act 1 cold route and miniboss | Cold `$B00` route; `Obj_DEZMiniboss` (`$A6`): `Check_CameraInRange word_7DDA4`, PLC `$7B`, `ArtKosM_DEZMinibossMisc`, `Pal_DEZMiniboss1/2`, landing Y by `Player_mode`, `Obj_EndSignControl`, gradual Y bounds | `s3k-implement-boss`; sprite-composition audit; palette line 1 ownership; first blocker recorded per roster |
| 7. Seamless `$B00` → `$B01` | Results signal, `DEZ1_BackgroundEvent` stages, art queue readiness, offsets, `DEZ2_ScreenEvent` stage 0 and `DEZ2_BackgroundEvent` stages 0-1 (reached only through the transition) | Timeline isolation if the reload clears history; objects and rings offset together; direct `$B01` load starts at the other stages and must differ observably |
| 8. Act 2 route and boss | Cold route to the arena; `Obj_DEZEndBoss` (`$A7`): range `word_7F0BE`, arena `word_7F0C6`, PLC `$76`, `ArtKosM_DEZEndBoss`, 8 hits, six routines, gravity interaction, `loc_7FC3E` clear, music restore, `Obj_IncLevEndXGradual $3620`, Robotnik run, `Act3_*` save, `StartNewLevel $1700` | Boss under both gravity states; player hit while inverted; carry values across the load; rewind across the exit |
| 9. `$1700` arena | Resource profile, scroll/plane swap, `DEZ3_ScreenInit` spawns (`Obj_5A7C8`, `Obj_5A8E6`, `Obj_DEZ3_Boss` at `$3C0,$F8`), ring/timer restore, BG event stages 0-8, arena `$6C0 → $2C0 → 0`, `$1FF` wrap, shake, laser DMA, level-select entry | Build the arena with a stub boss driving the event words first. Plane redraw stages against retained history; wide viewport against the `$1FF` wrap |
| 10. Final boss and exit | `Obj_DEZ3_Boss` 12 routines, fireball, crane/debris art swaps, Master Emerald chase (`loc_80382`), `loc_803D6` three-way exit, `SaveGame` | The longest coupled graph: split by phase with a rewind spot each. Seeded no-emerald Sonic run for the `$D01` branch; Tails native for `$D01`; record post-request engine behaviour |
| 11. Routes and acceptance | Cold Sonic + Tails chain `$B00` → exit from movie input (`--input-start` 468982; capture runs one frame behind the headless fixture; skip movie input on repeated native `lfc`), Tails chain from 444059, authored Sonic-alone route, full matrix, rewind spots, strict replay frontiers, DDZ entry re-measure | A positioned boss success does not advance the cold frontier |
| 12. Media and delivery | Reel, archive index, change-based validation against the pinned base, docs, integration | Below |

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
| First gravity flip (act 1) | first `Reverse_gravity_flag` 0 → 1 | Same-frame order of flag write, velocity, sensor swap and render flip; sidekick lag |
| Light tunnel | first `object_control` capture by `$57` | Per-mode path cadence, release velocity |
| Miniboss | `Boss_flag` set in act 1 | Landing Y per character, palette swap frame, defeat → results |
| Act change | `Current_zone_and_act` `$B00` → `$B01` | Frames between the signal, `Kos_modules_left == 0` and the offset; plane rebuild |
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
intro run, scroll, each AnPal channel, each AniPLC script and its trigger, each screen-event chunk
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
- **Clock-seeded RNG/aim** (`V_int_run_count`: Mecha Sonic, DEZ turrets as in DDZ) needs a declared
  seed for movie-route matching until the full cold chain supplies it; label such evidence seeded.
- **Knuckles trace testing is out of scope (user decision 2026-09-17).** One Knuckles replay class
  exists (`TestS3kKnucklesLbz2BigArmTraceReplay`); the `s3k-knuckles-complete-superemeralds` run has no
  segment classes. A campaign may add one where cheap, but owes no Knuckles replay frontier; Knuckles
  rows rest on authored routes and native probes from that movie.

## Open questions and kill conditions

| Question | Kill condition |
| --- | --- |
| Does the default scroll handler give acts 1-2 a static BG at 0,0? | Native vs engine BG plane at two camera positions in slice 1 |
| AniPLC script 7 frame count `$84` vs 1,600 bytes of art | Read `zoneanimdecl` expansion and the byte table at `AniPLC_DEZ`; native VRAM `$26D` over one cycle |
| Does the engine results object raise the DEZ act-change signal? | Trace `Events_fg_5` writers in the ROM results path and the engine equivalent before slice 7 |
| `$1700` start: metadata `$0030,$00CD` with `x_speed $600` vs start file `$0060,$0070` | Explain from `SpawnLevelMainSprites`/`Act3_flag` before coding slice 9 |
| Is `$6D` `InvisibleShockBlock` implemented for SKL? | Registry factory lookup in slice 0 (56 placements in act 2) |
| Do V5 aux rows carry `Reverse_gravity_flag`? | Inspect `aux_state` keys in slice 0 |
| Knuckles chained into `$1700` | Decide after slice 8 whether to record or block; ROM only denies the level-select path |
| Which campaign owns SSZ → DEZ | Settled by landing order; record in both plans |

## Status

| Claim | State |
| --- | --- |
| Implemented | Not started. Present before work: level load, music, `$B00` intro run, slope-angle rule, shared objects, partial PLC art, inert reverse-gravity flag with scattered consumers |
| Cold-reachable | Not started |
| Rewind-verified | Not started (the flag itself is already snapshotted) |
| Native behaviour matched | Not started; replay frontiers unmeasured at `9cba6dbb6` |
| Visually matched | Not started |

Out of scope, recorded as dependencies: `$D01` ending and credits (ending campaign); the SSZ
launch cutscene if SSZ lands second.

## Evidence log

(empty)
