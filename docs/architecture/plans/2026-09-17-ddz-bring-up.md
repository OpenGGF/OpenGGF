# The Doomsday Zone: methodology v2 bring-up plan

Date: 2026-09-17. Branch `feature/ai-ddz-bring-up` in `.worktrees/ai-ddz-bring-up`; execution
base develop `832554260` (pin this SHA for the combined change-based validation). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[FBZ](2026-09-14-fbz-completion.md), [SOZ](2026-09-15-soz-methodology-v2.md) and
[HPZ](2026-09-16-hpz-bring-up.md) campaigns to DDZ (`$C00`). Design and plan by Fable 5.1;
implementation and execution by Opus, as for HPZ. Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[ddz-analysis.md](../research/s3k-zones/ddz-analysis.md).

## Goal and delivery rule

Deliver DDZ from cold entry to the `$D01` exit request as Super Sonic and as Hyper Sonic, with
every slice demonstrated on video and a final act-ordered highlights reel like the HPZ one.

- Every feature or fix gets a short `GameplayCaptureTool` demo with at least 30 frames of lead-in
  and lead-out around the moment it shows. Media live outside the repository in
  `~/Videos/OGGF/ddz-bring-up/`: raw captures `raw-NN-*` (never overwritten; a correction gets a
  new directory), clips numbered by slice, `inputs/`, `native/`, `reel/`. Copy `make_clip.sh` and
  `side_by_side.sh` from `~/Videos/OGGF/hpz-bring-up/`.
- A demo is not parity evidence. A before/after "before" build disables only the demonstrated
  registration in an uncommitted edit, reverted and recompiled immediately (`git status` clean).
- Track five claims separately per matrix row: implemented, cold-reachable, rewind-verified,
  native behaviour matched, visually matched. No aggregate green label.
- Work stays on this branch until the cold route reaches the exit; no per-increment develop merges.

## Scope decisions (HPZ precedents)

| Question | Decision | Reason |
| --- | --- | --- |
| Cold entry | Level-select/direct `$C00` load | DEZ has no events or final boss, so `loc_803D6` → `$C00` cannot run. Recorded as a DEZ-owned dependency, as LRZ3 → HPZ was. Never position past the intro |
| Exit | Verify the `SaveGame` + `StartNewLevel $D01` request and the load attempt | No S3K `EndingProvider` exists. The ending zone is its own campaign, as SSZ was for HPZ. Record what the engine does after the request |
| Roster | Sonic alone and Sonic + Tails (Tails removed by the controller). Tails and Knuckles are ROM-denied | Derive from the production launch contract; assert the live roster after the controller zeroes Player 2. No raw debug override to fly Tails or Knuckles |
| Forms | Super (`Super_emerald_count < 7`) and Hyper (`== 7`) are both mandatory rows | Different child objects, palette and the Master Emerald rotation gate |
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from the first slice; donors per the level test standard | The control box `$20..$C0`, the right-edge speed boost, the FG-plane boss and the wrap are all camera-relative: DDZ is the most width-sensitive zone so far |
| Traces | Strict replay of `zone0c` stays late; its movie supplies the cold route and native states from slice 2 | v2: short native sequences per slice, full-route replay late |

## Findings that change the plan

- **The `ddz` trace segments are the ending, not DDZ.** `runs/s3k-sonic-tails-complete-emeralds/ddz`
  has `zone_id 13`, camera fixed at `$200`, zero rings: it is `$D01`. The Doomsday route is
  `runs/s3k-sonic-tails-complete-emeralds/zone0c` (`zone_id 12`, 10,126 rows, `bk2_frame_offset`
  514214, handover to zone 13 at row 10056, `Game_Mode` bit 7). Its camera wraps are visible
  (`$6A95` → `$5A50`), and rings run 0 → `$52` → 9. `ddz_completerun` and the Tails full-chain `ddz`
  also need their identity checked before use. `TestS3kSonicTailsZone0cSegmentTraceReplay` is the
  DDZ replay class; `…DdzSegmentTraceReplay` belongs to the ending campaign.
- **The save in that movie decides which form is native.** Read `Super_emerald_count` from the
  native state before planning Hyper probes. If it is below 7, Hyper native evidence is seeded
  (declared setup write) and is labelled so.
- **The layout is populated.** 477 placed objects and about 194 rings use the normal managers, so
  the wrap's `Seek_Object_Manager`, respawn-bit clears and `Ring_status_table` clear are
  load-bearing, not cosmetic.
- **DDZ silently receives `SwScrlS3kDefault`**; there is no AniPLC or AnPal to port.
- **`SuperStateController` has no level-start entry.** `debugActivate` adds 50 rings by
  coincidence; it is not the ROM path (`loc_8160A` sets fields directly, plays `sfx_Whistle`, and
  skips the transform animation).

## Design: who owns what

Resolve these owners before any consumer (v2 step 3; `s3k-zone-bring-up` deliver step 1).

| State | ROM | Engine owner |
| --- | --- | --- |
| Autoscroll speed/accel, camera delta, wrap offset, control box, phase bits (`_unkFA82`, `_unkFA8A`, `_unkFA90`, `_unkFAAE`, `_unkFAB0..B6`, `_unkFAB8`, `_unkFAA4`) | Controller `loc_81492` and boss | New `DdzZoneRuntimeState` (pattern: `SozEventState`, HPZ runtime state), registered with a `RewindSnapshottable` adapter. Objects read it through `services()`; none cache the camera delta |
| Camera X, min/max X, lock, `$2000` wrap | `sub_82920`, `sub_829A0`, `loc_81726` | `Sonic3kDDZEvents` drives the camera through the existing camera API, in the controller's slot phase. The wrap calls the production object-manager reseek and ring-status clear; no direct list edits |
| FG plane scroll and staged redraws, BG parallax | `DDZ_ScreenEvent`/`sub_59648`, `DDZ_BackgroundEvent`/`sub_596EA` | New `SwScrlDdz` plus event-owned FG offset (`Events_bg+$00..$06` in the runtime state). Plane redraws go through the existing redraw path; remember a full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes |
| Player flight | `sub_82772`, `sub_828B2`, `sub_829D2` | A controller object using the generic object-control path in `AbstractPlayableSprite` (as the S2 Tornado does), not a new movement mode and not a `Sonic3kZoneFeatureProvider` hook: the ROM has no zone check in `Obj_Sonic`. Native writes through `NativePositionOps` |
| Forced Super/Hyper | `loc_8160A`, `loc_8167C`, `sub_5FCCE` | A narrow engine-internal entry on `SuperStateController` that sets the ROM fields without the transform animation. Check the Mod API pin before adding a public member to any `@ModApi` type (grep both annotation spellings); keep helpers in non-API classes |
| Hurt, death, tempo | `:174785-174797`, `sub_82742`/`loc_8179E`, `loc_82722` | Controller object; standard ring drain stays with `SonicKnux_SuperHyper`'s engine owner and must still run under object control |
| Collision | `Check_InMyRange`, `word_82BB4`, `word_82E92`, `off_82BBC` | Range-box helper reused from existing `Check_InMyRange` ports; `collision_flags` stay 0 so the generic touch path never sees these objects |

Rules that bind every slice: `GameRules`/providers for differences, never zone-name carve-outs in
shared code; objects use `services()`; each gate names the ROM clock it reads (the homing gate is
`V_int_run_count`); `FixBugs = 0` branches commented; constants cite their routine; nothing keys on
a fixture, frame index or fitted measurement; trace rows never hydrate gameplay. `GameLoop` and
`Engine.draw` are size-ratcheted: put logic in managers and run `-Pguards` before committing there.

## Dependency-ordered slices

Each slice: reverify its inventory rows in the disassembly → discriminating failing test with
ROM-derived expectations → implementation → cold-route extension with preserved inputs → short
native comparison on a named question → 320 + wide + donor check → rewind spot → demo clip →
boundary review → plan evidence entry. Coupled boundaries (2, 3, 6, 7) get an independent review;
routine families (4, 5) share one.

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 0. Baseline and identity | Matrix `validation/levels/s3k-ddz.md`, coverage-backlog row, `zone0c` identity and native form check, media root, capture of the current broken state (`raw-00`). Confirm `$C00` resources, `Pal_DDZ`, PLC `$3A`, title card, no-act card, `mus_DDZ`, act-2 slot behaviour | The "before" footage exists before anything changes; level-select act 2 (`$1700`) stays DEZ-owned |
| 1. Runtime state and background | `DdzZoneRuntimeState`, `Sonic3kDDZEvents` shell, `SwScrlDdz` (`sub_596EA`, `DDZ_BGDeformArray`, BG Y = `Camera_Y/2`), registration in the event manager and scroll provider | Band boundaries and fixed-point fractions from the table, not from measurement; wide-viewport seams; capture/restore of `Events_bg+$06` |
| 2. Controller: intro and transformation | `loc_81492`, `loc_81554`, `loc_8160A`, `loc_8167C`: `object_control $81`, `$18`-frame wait, +50 rings, Super fields, `sfx_Whistle`, `$1000` launch, Hyper branch and star/trail children, `PLC_BossExplosion`, `ArtKosM_DDZMisc` queue, Player 2 removal | First cold frames must match `zone0c` rows 0.. (start `$FFE1,$C0` in the trace vs `$0000,$0100` in the start file: explain from the ROM before coding). Art readiness before first draw |
| 3. Flight, autoscroll and camera | `sub_82772`, `sub_828B2`, `sub_829D2`, `sub_82920`: decay, D-pad and dash tables, press-byte dash gate, control box, follow windows, 16.16 speed/accel/caps, right-edge boost, `_unkFA90` | Adjacent phases: dash while invulnerable, diagonal vs cardinal, pinned at the right edge with +x_vel. The box is camera-relative: decide from the ROM what a wide viewport does to it and record the presentation choice; gameplay geometry stays native |
| 4. Rings, asteroids, layout missiles | `$B7` (all 14 subtypes, split layouts by player Y, 5/7 debris, respawn-bit clear), `$B8` subtype 0 (eight-direction hitboxes, `sfx_Dash` once), ring drain under object control, tempo object | Hurt model: spin, control off, velocity = −(speed >> 8), speed penalties and the `$10000` floor. No ring loss. Slot order decides sibling RNG/draw order; allocator choice decides same-frame execution |
| 5. Death and restart | `sub_82742` → `loc_8179E` → `Kill_Character`; life loss and restart of `$C00` | Rings hitting 0 mid-dash and mid-hurt; level variables cleared on reload; timeline isolation across the reload |
| 6. Boss phase 1 | `Obj_DDZEndBoss` routines 0-`$A`, camera lock (`sub_829A0`), FG plane body (`DDZ_ScreenEvent`/`sub_59648`, four redraw sub-states), body `loc_81E3C`, turrets and shots, boss-launched homing missiles `sub_82A82`/`sub_82B06` redirected into the body, flash tables, defeat sequence, `ArtKosM_BossMasterEmerald` | Follow `s3k-implement-boss`. Sprite-composition audit: plane body vs sprite children vs player, bucket/slot/piece order, palette line 3 ownership between flash, `DecColor_Obj` and the white flash. Missile that misses the body must hit the player |
| 7. Boss phase 2 and wrap | Routines `$C/$E`, Master Emerald child and the Hyper-only palette rotation, `_unkFAB8` bit 0, wrap `loc_81726`, overlap damage `loc_82DCE`, `sub_8307C` attack selection | The wrap frame: every live object, ring state, coarse-back camera and the FG plane move together; rewind across it; rings respawn after the clear. Verify against the native `$6A95` → `$5A50` crossings |
| 8. Defeat and exit | `loc_82E2C` → `loc_81BBE` → `loc_81CA4`: control lock, `Super_frame_count = $7FFF`, catch-up, fade, `SaveGame`, `StartNewLevel $D01` | Ring drain must stop; the save result per emerald state; record the engine's behaviour after the request as the ending-campaign dependency |
| 9. Routes and acceptance | Cold Super route from the `zone0c` movie input (`--input-start` 514214; the capture path runs one frame behind the headless fixture), authored cold Hyper route (`--emeralds 3333333`), full matrix breadth, rewind spots, strict `zone0c` replay frontier, moving visual inspection at 320 and wide | A positioned boss success does not advance the cold frontier. Native lag frames desync cold BK2 replay: skip the movie input on repeated `lfc` |
| 10. Media and delivery | Highlights reel, archive index, change-based validation against `832554260`, docs, integration | See below |

## Native probes

Question-led, disassembly first, BizHawk 2.11 through the shared capture host with a DDZ exporter
(`bizhawk-native-reference-capture`; model on `capture_hpz_route_reference.lua`, output to
`OGGF_OUT` only, no `print()`). Pass 1 saves states from the Sonic + Tails movie near each window;
later probes load them (2-5 s each in HPZ). Record ROM SHA-1, movie SHA-256, host exit code and
the probe's own error status (a failing Lua probe exits 0). Planned windows, as `zone0c` rows:

| State | Row (approx.) | Question |
| --- | --- | --- |
| Entry | 0 | Intro hold, transformation frame, launch velocity, first-frame art and palette |
| Asteroid field | `$2BC` | Dash/decay cadence, control box limits, hurt spin and speed penalty |
| Boss arrival | `$F00` (camera `$52xx` lock) | Lock easing, FG-plane body offset and redraw stages, turret cadence |
| Missile redirect | during phase 1 | Homing gate on `V_int_run_count`, body hit and flash on line 3 |
| Phase change | `$1800` | Defeat fall, white flash, palette reload, phase-2 plane redraw |
| Wrap | `$1B58`-`$1E14` | One-frame object/ring/plane shift at `$7400` |
| Exit | before 10056 | Catch-up, fade phase, `$D01` request frame |

Choose fields and intervals before looking at engine output. Palette lines are `$20` bytes
(`Normal_palette_line_4 = $FC60`). Break each new comparison on purpose once before trusting it.

## Demo and reel plan

Clips (renumber as the work dictates): `00` broken baseline; `01` load and title card; `02`
parallax before/after; `03` intro and Super transformation; `03h` Hyper transformation with stars
and trail; `04` flight, dash and the right-edge boost; `05a` asteroid hit and spin, `05b` large
asteroid split, `05c` layout missile; `06` low-ring tempo change and ring-out death; `07a` boss
arrival and camera lock, `07b` turret fire, `07c` homing missile redirected into the body, `07d`
phase-1 defeat and white flash; `08a` phase-2 chase with Master Emerald, `08b` wrap crossing
(continuous scenery), `08c` final hit; `09` exit and `$D01` request; `20` uncut cold Super route;
`21` uncut cold Hyper route; one wide-viewport route. Captures have no audio: note SFX/music
claims as test-backed, not shown.

Reel (`gameplay-highlights`, `reel/build_reel.py`, `edit_list.csv`, `chapters.csv`, copied from
HPZ and adapted): route order — entry and transformation → flight and field → hazards → death
(brief) → boss phase 1 → phase change → phase 2 and wrap → final hit and exit, with the Hyper
transformation as the form variant. Moving footage from the delivered revision only, one example
per feature, recorded speed, nearest-neighbour integer scaling, labels that state positioned vs
cold. Re-read state CSVs after every route change; old frame numbers expire. Uncut cold runs stay
in the archive as the traversal evidence; the reel is a work summary.

## Acceptance matrix (seed for `s3k-ddz.md`)

Rows: load/identity, title card, parallax, intro/transformation (Super, Hyper), flight and dash,
autoscroll speed model, ring collection and drain, tempo, asteroid (per size), layout missile,
hurt model, ring-out death and restart, boss arrival/lock, FG-plane body, turrets, homing
redirect, phase-1 defeat, phase-2 chase, wrap, overlap damage, attack selection, exit request,
DEZ entry (blocked), ending (blocked). Columns: the five claims, each with command, commit,
configuration, setup, result, skips and limits; products: form × width × donor × team shape.
Rewind spots (before / active / after, restore equality plus forward replay): transformation,
mid-dash, hurt spin, asteroid split, camera lock, missile in flight, phase change, the wrap frame,
final hit, exit fade; timeline isolation for the death reload and the `$D01` load.

## Validation and delivery

Focused tests and `run_categories.py --category NAME --run` during slices, through
`maven_queue.py` with `-Dmse=off` and absolute ROM paths (wrong paths skip silently: inspect
skips). Mandatory S3K checks stay green: `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`. One combined
`run_categories.py --base 832554260 --run` at delivery, after focused fixes and docs, with class
count, cost and stopping rule stated first; no tree edits during the run; acknowledge the run.
Shared-owner changes (camera API, `SuperStateController`, object manager reseek, ring manager)
make this a normal change-based validation, not proportionate validation. Attribute any red to a
matched baseline check. Strict replay: record the `zone0c` frontier (command, commit, first error
frame/field) in `docs/status/trace-frontier-log.md`.

Docs at delivery: matrix and coverage backlog, this plan's status/evidence, `CHANGELOG.0.7.md`
entry, S3K known bugs for gaps (the discrepancies file is intentional-only), agent-workflow README
for promoted probes/tools, lessons into existing catalogues. Commits carry all seven trailers; no
`--no-verify`; never `git stash`; start git chains with an explicit `cd`. Integrate to develop only
when the cold route reaches the exit, then push and remove the worktree.

## Status

| Claim | State |
| --- | --- |
| Implemented | Controller, transformation, scroll/FG plane, asteroids, missiles, end boss both phases, wrap, defeat, exit request, ring-out death (2026-09-17) |
| Cold-reachable | Seeded Sonic route from level start to the `$D01` request (`TestS3kDdzColdRoutes`, production-loop capture `raw-14`); unseeded entry diverges at 4178 |
| Rewind-verified | Boss fight, first wrap and exit fade on the native route; mid-transformation and mid-flight on 34 breadth rows |
| Native behaviour matched | Player x/y, camera and rings on all 10058 gameplay rows; boss exit routines on the native frames; slot load order around wraps |
| Visually matched | Moving side-by-sides at entry, boss arrival, first wrap and exit; residual gaps in `s3k-known-bugs.md` (Doomsday entry) |

Out of scope, recorded as dependencies: DEZ final boss → `$C00` (DEZ campaign); `$D01` ending and
credits (ending campaign, which also owns the mislabelled `ddz` segments); level-select `$1700`.

## Evidence log

### 2026-09-17 slice 0 — baseline, identity and native state

- Media root `~/Videos/OGGF/ddz-bring-up/` created (`inputs/`, `native/`, `reel/`, clip helpers copied from HPZ).
- `raw-00-baseline-before-work`: base `832554260`, `GameplayCaptureTool --zone ddz --act 1 --sidekick tails`, movie
  input from 514213: Sonic and Tails stand on nothing, fall and die at frame 196. No controller, no autoscroll.
- Native pass 1 (`native/run-pass1.sh`, exporter `tools/bizhawk/capture_ddz_route_reference.lua`, BizHawk 2.11,
  ROM SHA-1 `CFBF98C3…61D6`, movie SHA-256 `AD40FB0B…3C0`, host exit 0, 502 s): seven savestates at
  movie frames 514214, 514914, 517798, 519846, 521382, 522918, 523942 and windows `entry`, `boss_arrival`,
  `phase_change`, `wrap1`, `exit`.
- **The native save holds seven Super Emeralds** (`Super_emerald_count = 7`): the movie route is Hyper Sonic
  (`Super_Sonic_Knux_flag = -1` from frame 50). Super-form native evidence needs a seeded probe.
- Native entry facts (exporter frame numbering, frame 0 = `loc_81554` ran): controller routine 2 at frame 0,
  transformation at frame 24 (+50 rings, `Super_Sonic_Knux_flag = 1`), release at frame 50 (`object_control = 1`,
  `Super_Sonic_Knux_flag = -1`), 26 palette passes. The camera X low word is `$2700` before the level starts
  (inherited from the Death Egg); a level-select entry starts at 0, so camera X can read one pixel lower.
  `V_int_run_count` at frame 0 is 512489.
- Native HUD shows 0 rings after the +50: `loc_8160A` does not flag a HUD ring update. Engine shows 50 (open).
- Chunk geometry: the phase-1 plane body is layout chunks `6,7/9,8` at FG columns 4-5, rows 2-3 (X `$200-$3FF`,
  Y `$100-$1FF`); native `_unkEE98` near `$18A` puts it on screen without any nametable wrap.
- Trace segment identity: `zone0c` (`zone_id 12`) is Doomsday; the `ddz` directory (`zone_id 13`) is the ending.
  Physics row `r` of `zone0c` is exporter/capture frame `r + 1`.

### 2026-09-17 slices 1-3 — controller, transformation, scroll, plane (`6145ccce3`, `3f5a01184`)

- `DdzZoneRuntimeState`, `SwScrlDdz` (`DDZ_ScreenEvent` state machine + `sub_596EA` bands), the
  `DdzForegroundPlaneRenderMode` (per-line FG scroll + FG V-scroll override, the FBZ2 boss mechanism, no Mod API
  change), `DdzFlightControllerObjectInstance` (all `loc_81492` modes), `DdzMusicTempoObjectInstance`, sidekick
  suppression for zone `$0C`, `Sonic3kSuperStateController.startDoomsdayTransformation` /
  `upgradeDoomsdayFormToHyper`. `HpzCameraGradualObjectInstance` became `S3kCameraGradualObjectInstance`
  reading `S3kCameraStoredBounds`.
- **Shared fix:** S3K transformations released `object_control` after a fixed 30 frames (Tails/Knuckles after one
  pass). ROM `SuperHyper_PalCycle` (sonic3k.asm:4608-4660) runs `Palette_timer $F` then six 2-frame fade steps for
  Sonic and finishes on the first expiry for Tails/Knuckles. RED: `TestS3kDdzFlightControllerHeadless` release at
  frame 54 instead of 50 (diagnostic run before the fix). GREEN after. `TestSonic3kSuperStateRewind` updated
  (its one-tick pop was not trace-backed, commit `a1c45eb38`).
- Focused: `TestS3kDdzFlightControllerHeadless` 1/1; Super/Hyper tests (18 classes) green after the test update;
  rewind guards `TestEveryObjectRewindRoundTrip` 1090, `TestRewindHarnessCoverageRatchet`, construction tests green.
- Engine vs native entry window (`native/compare_entry.py`): positions and camera identical frames 0-128 except
  the inherited camera fraction (1 px on 16 frames).

### 2026-09-17 slices 4-8 — asteroids, missiles, end boss (`2e0067d9a` and working tree)

- Objects: asteroid/debris, missile/exhaust/puff, `Obj_CreateBossExplosion` sets 0/8, boss phases 1-2 and exit,
  body, parts, turrets and shots, launcher, ship parts, Master Emerald with `word_8141E`, bombs, rockets and
  flames, defeat/exit explosion chains, white flash and fade, `DdzPalette`.
- Cold route with movie input (`native/run_route_compare.sh`, `native/compare_trace.py`): engine matches the native
  trace positions and camera (within the inherited fraction) for **4178 frames**, through the asteroid field, boss
  arrival, the first two missile hits on the body (engine 3920/4083 vs native 3919/4082) and a homing missile hit
  on Player 1.
- Divergences found and fixed with ROM evidence: (1) split asteroid children ran the same frame because
  `Go_Delete_Sprite` / `Sprite_CheckDelete` objects left their slot immediately; native keeps the slot one more
  frame (`Delete_Current_Sprite` code `$1ABB6` visible in aux slot dumps) — `AbstractDdzObjectInstance.goDelete`.
  (2) launcher missile ride timer is `index * 16` (`add.w d0,d0` then `lsl.w #3`), not `* 8`.
- Remaining divergence at frame 4179: turret aim reads `V_int_run_count & $F`; native 512489 vs engine 1 at
  frame 0 changes turret shot directions. A cold level-select entry legitimately has another counter value, so
  full-route native matching needs a declared clock seed or the trace replay bootstrap.
- Strict replay `TestS3kSonicTailsZone0cSegmentTraceReplay` (`-Ptrace-segments`): before the work 411 errors, first
  frame 0 `x`; the controller was absent because the replay's placement reset cleared the ScreenInit allocation.
  Added the DDZ case to `restoreEventOwnedObjectsAfterPlacementReset`: now 699 errors, first frame 0 `camera_y`
  `$A0` vs `$60`. Cause: the bootstrap places Player 1 at metadata start (`$FFE1,$C0`, already moved by the
  controller's init pass) and derives the camera from it, so replay Sonic flies `$40` higher and clips asteroids
  native missed (the 15-frame `x_speed -$3C0` spans are asteroid pushback decay). Also seeded: none of the camera
  fraction; the V-int phase seed is only 3 bits. Harness-level; recorded for the strict-replay slice.


### 2026-09-17 slices 7-9 — wraps, load order and the exit (working tree)

- Seeded comparison (`TestS3kDdzColdRoutes`, declared `V_int_run_count` 512489 and camera fraction `$2700`):
  exact player/camera/rings to 7631, then within 1 px to 8626 after making every DDZ object persistent (the
  engine's spawn-window unload deleted a rocket waiting off-screen) and allocating `Obj_CreateBossExplosion`
  children after the current slot.
- **Wrap seek.** The first post-wrap design called `adjustPlacementTrackingForWrap` (AIZ's cursor shift) and loaded
  nothing afterwards; removing it made the engine's big-jump refresh load ascending into low slots. ROM
  `loc_81726` calls `Seek_Object_Manager` (sonic3k.asm:37986): cursors move around `(Camera_X + $400) & $FF80`
  without loading, and the next `Load_Sprites` backward branch loads right to left. Added package-private
  `ObjectPlacementController.seekCursors` behind the non-API `ObjectPlacementSeek`. First divergence moved from
  8248 (1 px) to 8249 exact / 8627 ring phase.
- **Slot history probe.** `capture_ddz_route_reference.lua` gained `plan.slots` (slot-code change log) and
  `plan.boss_code`. Engine vs native slot logs (`native/probe-slots0`, `probe-slots1`) diverged at row 173: native
  loaded asteroid `$490` into slot 8 while the offscreen asteroid in slot 6 was still alive; the engine deleted it a
  frame earlier. `Sprite_OnScreen_Test` compares against `Camera_X_pos_coarse_back`, latched by `Load_Sprites`
  before `Process_Sprites`; the DDZ controller scrolls the camera inside the object pass, so later objects must
  use the frame-start value. `DdzZoneRuntimeState.latchCameraXCoarseBack` (set by the slot-4 controller) now feeds
  `DdzObjectSupport.outOfRangeX`. The Y test uses live `Camera_Y_pos` (`Sprite_CheckDeleteXY`) and is unchanged.
- **Result: exact position, camera and ring parity for all 10058 gameplay rows** (two wraps, both phases, the
  defeat, exit routines 2/4/6 at native frames 9903/9968/10025) and the `$D01` request on the native frame.
  Native freezes during the fade; the recording frame driver keeps stepping gameplay until the load at 10080
  (both reach `$D01` on the same frame). `GameLoop` applies the freeze (`isNonRewindableTransitionPending`),
  so this is a harness gap, not runtime behaviour.
- Remaining native slot differences: slots 8-10 `$2D690`/`$2D95C` for frames 0-34 (the Super/Hyper transformation
  stars, not implemented), which do not change later allocation.
- **Rewind and breadth (same tree).** `TestS3kDdzColdRoutes#rewindAtBossWrapAndExitRestoresTheNativeRoute`
  (capture at 4000/7420/9990, 45 idle frames, restore, route continues) and `TestS3kDdzCompatibilityMatrix`
  (34 rows) found and fixed: `currentRuntimeStateUsesThisEventInstance` lacked DDZ, so every
  `ensureZoneRuntimeStateInstalled` during restore zeroed the state and allocated a second controller; DDZ
  children had no probe constructor for `genericRecreate` (turrets vanished on restore; private
  `(ObjectSpawn)` probes added); the boss's transient `exitFade` reference was null after restore (now a live
  lookup of the hold fade, `$44(a0)`); `Sonic3kSuperStateController` restored TRANSFORMING with normal physics
  (transformation start installs the Super constants); donor leaders without a powered form soft-locked in
  `loc_8167C` (engine extension: 26-frame release, `$38` bit 7 clear); DDZ culls used a bare `$280` and churned
  objects at 800 px (now `coarseXCullRange`). Result: 37/37 DDZ tests, 1220 shared tests green, 0 skips.
- **Slice 5 and visual inspection (same day).** `TestS3kDdzLifecycleProduction` (320/800): ring-out ends
  the form, `loc_8179E` drops Sonic and calls `Kill_Character`; the death arc never ran because the engine's
  death gravity honoured `object_control` (`loc_8179E` sets `$81`), while Sonic routine 6 (`loc_12390`) calls
  `MoveSprite_TestGravity` regardless — shared fix in `PlayableSpriteMovement.applyDeathMovement`; the reload
  then reinstalls one controller. Moving inspection of `GameplayCaptureTool` captures (320, seeded; exact to
  native on all rows with `--settle 1`) against every-frame native footage (`native/probe-vid-*`) found
  (1) missing boss explosions: DDZ never registered `PLC_BossExplosion` (`loc_8167C`); (2) phase-2 bombs
  vanishing on the wrap frame: `loc_81726` rewrites `Camera_X_pos_coarse_back` after the camera subtract,
  which the latch model had missed (regression check in `TestS3kDdzColdRoutes`, red with the line removed).
  Residual presentation differences are in `docs/status/s3k-known-bugs.md` (Doomsday entry) plus Master
  Emerald flicker phase and the white-fade tint/HUD icon, not yet investigated.

### 2026-09-17 follow-up — user-reported visual defects (`c1fab4cf8`, `92c1abba0`)

- **Boss pieces in the asteroid field.** `DDZ_ScreenInit` `Refresh_PlaneFull` fills the 512x256 nametable from
  layout (0,0), which is empty; plane A shows only that until `DDZ_ScreenEvent` leaves routine 0. The engine sampled
  the layout at `_unkEE98` (Camera X) and drew the body chunks (X `$200`) and the phase-2 chunk (`$600`). A first
  attempt wrapped the words at 512x256 (`c1fab4cf8`) and made it worse, because the engine plane does not wrap
  and `$1B8 + 320` still reaches `$200`. Final: while routine 0 is active and that layout region is empty, show the
  draw at (0,0) (`92c1abba0`; `@ModApi` `AdvancedRenderFrameState` has no blank-plane option).
- **Phase-1 defeat explosions.** Frame-count comparison against native every-frame footage found an extra
  cluster on the ship from row 5484. Native slot history: after the defeat spawner (`$82E9A`) is deleted at row
  5476, its `loc_82F78` children keep spawning bursts from `sub_82C86`'s unchecked `parent3` read of the cleared
  slot, i.e. near (0,0), off-screen. `AbstractDdzObjectInstance.parentXPos/parentYPos` model the cleared slot.
- **Dark phase-2 ship.** Native palette dumps: line 3 colours 1/2/5 (jets and highlights) are `$EEE/$06E/$0EE` in
  phase 2; the engine kept `$888/$008/$088` (three `DecColor_Obj` passes from the phase-1 fall). `loc_819CE` reloads
  `Pal_DDZ+$20` in RAM before copying Normal to Target; the engine's palette-ownership write was deferred, so the
  flash faded back to the darkened target. `DdzPalette.loadDdzLine3` now resolves pending writes.
- Each fix has a `TestS3kDdzColdRoutes` check shown red without its change (palette, bursts; the plane check pins
  the displayed words). Tools: every-frame native probes `native/probe-vis-*` and `cmp.sh`-style side-by-sides.
