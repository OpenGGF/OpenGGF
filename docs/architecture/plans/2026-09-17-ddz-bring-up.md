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
| Implemented | Nothing DDZ-specific (data-only zone at `832554260`) |
| Cold-reachable | Level load only |
| Rewind-verified | Not started |
| Native behaviour matched | Not started |
| Visually matched | Not started |

Out of scope, recorded as dependencies: DEZ final boss → `$C00` (DEZ campaign); `$D01` ending and
credits (ending campaign, which also owns the mislabelled `ddz` segments); level-select `$1700`.

## Evidence log

(Executor: append dated entries per slice — RED/GREEN summary, commands and counts, commits,
native probe table, rejected approaches with their kill evidence, clip table.)
