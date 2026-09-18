# Hidden Palace Zone: methodology v2 bring-up plan

Date: 2026-09-16. Branch `feature/ai-hpz-bring-up` in `.worktrees/ai-hpz-bring-up`;
execution base develop `70aa0a0b7`. Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) to the playable
Hidden Palace act (`$1601`). The Super Emerald sanctuary (`$1701`) was delivered
earlier and is in scope only where the two acts share owners.

## Goal and delivery rule

Goal (user, 2026-09-16): finish HPZ. Every feature or fix gets a short demo video
with at least half a second (30 frames) of lead-in and lead-out around the moment it
shows. Demos are `GameplayCaptureTool` output kept outside the repository in
`~/Videos/OGGF/hpz-bring-up/` (raw captures `raw-*`, clips numbered by slice, input
scripts in `inputs/`, clip helpers `make_clip.sh` and `side_by_side.sh`). A demo is
not parity evidence. Where a before/after is shown, the "before" build disables only
the demonstrated registration in an uncommitted temporary edit that is reverted and
recompiled immediately (`git status` checked clean).

## Objective and selection

HPZ was ranked the lowest-effort remaining 0.7 campaign zone (score 5/10) in a
read-only five-zone analysis of LRZ, HPZ, SSZ, DEZ and DDZ on 2026-09-16. The
analysis estimated 43 placed objects in 10 distinct types, no badniks, about 300
lines of event code and two hard objects: `CutsceneKnux_HPZ` and the teleporter
ending. These are source-audit estimates, not measurements.

Deliver ordinary entry, traversal, the Sonic/Tails Knuckles fight and teleporter
exit to SSZ (`$A00`), and the short Knuckles route to SSZ act 2 (`$A01`). Cold
entry uses the data-select destination `LevelList_DA6E` → `$1601`
(`sonic3k.asm:17510`); the ordinary LRZ3 → HPZ transition is blocked until LRZ has
events and remains a recorded gap, not a reason to position the player.

## Findings that changed the plan

- **Identity (fixed, `e3ae26530`).** Engine zone `$16` act 1 resolved the `$1701`
  sanctuary resources, so HPZ was unreachable and its data-select slot entered the
  sanctuary. ROM evidence: sprite table `LRZ3, HPZ, DEZ3, HPZMini`
  (`sonic3k.asm:202440-202443`), screen events `HPZ_*` for `$1601` and `HPZS_*` for
  `$1701` (`102347-102354`), LevelSizes rows (`38141-38144`), title-card selection
  (`62149-62151`). `$1601` now resolves the linear ROM level; the sanctuary keeps
  `$1701`.
- **Research corrections (`8e70761fd`).** `hpz-analysis.md` described `$1701` as the
  main act and placed both slots one zone too high. Its object, event, palette and
  animation descriptions otherwise refer to `HPZ_*` routines and remain the
  starting inventory, subject to reverification.
- **Inherited scroll routing.** `Sonic3kScrollHandlerProvider` selects handlers by zone
  only, so LRZ3 (`$1600`) also receives `SwScrlHpz`. Out of HPZ scope; LRZ work owns it.

## Dependency-ordered slices

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 1. Identity | Registry, resource profile, title card, art plan, runtime state | Done: `$1601` bounds/object set, `$1701` sanctuary lifecycle unchanged |
| 2. Screen and background events (done except the `$EC0` background redraw machine, see evidence) | `HPZ_ScreenInit` / `HPZ_ScreenEvent` (character camera clamps, `Obj_HPZPaletteControl` spawn, screen shake, `Events_fg_4` chunk `$61` write and redraw); `HPZ_BackgroundInit` / `HPZ_BackgroundEvent` (`$EC0` seam redraw machine, Knuckles background patch) | Clamp edges per character; the chunk write goes through `ZoneLayoutMutationPipeline`; seam redraw before/at/after `$EC0` |
| 3. Palette and animated tiles (done) | `AnPal_HPZ`, `AniPLC_HPZ` (4 scripts), `Obj_HPZPaletteControl` (`$B1`, intro → main palette at camera X `$460`) | Line-4 writer order with the Master Emerald; timer/counter phase at entry |
| 4. Placed objects in the playable act | `$B0` Master Emerald and `$B4` Super Emerald placements in `$1601`, generic shared objects | Behaviour outside the sanctuary controller; emerald-state gating |
| 5. Knuckles fight | `$82` subtype `$28` `CutsceneKnux_HPZ` (47 routines), dizzy stars, dust, `mus_Knuckles`, three DPLC sets, collapse via `Events_fg_4` | Registry currently falls back to the AIZ2 cutscene for subtype `$28`; rewind across every state |
| 6. Teleporter and exits | `Obj_SSZHPZTeleporter` (`$79`) full behaviour, camera lock `$1600`, line-4 palette takeover, `StartNewLevel` `$A00`; Knuckles subtype `$4A` save and `$A01` | Handoff into SSZ, which has no events yet: verify the request and load, record the SSZ gap |
| 7. Routes and acceptance | Cold controller routes (Sonic + Tails, Tails, Knuckles), per-act matrix, widths/donors, rewind spots, moving visual inspection | A positioned fight does not advance the cold frontier |

## Status

| Claim | State |
| --- | --- |
| Implemented | Slices 1-3; slice 5 (Knuckles fight, Master Emerald theft, collapse); slice 6 (teleporters, Knuckles `$A01` exit, Sonic/Tails altar ending to `$A00`) |
| Cold-reachable | Level load only; no traversal verified |
| Rewind-verified | Fight, crane grab, spark chains, altar beam and ending hold (restore + forward replay, `TestS3kHpzKnucklesFightHeadless`); dust child and cold routes pending |
| Native behaviour matched | Not started |
| Visually matched | Not started |

## Evidence log

### 2026-09-16 slice 1 — identity split

Commit `e3ae26530` on base `70aa0a0b7` + `8e70761fd`. Focused command from the
worktree, all three ROMs by absolute path:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -B -q \
  "-Dtest=TestSonic3kNonlinearHpzProfile,TestSonic3kHpzRuntimeStateRegistration,TestS3kHpzSanctuaryHeadless,TestSonic3kLevelLoading,TestHpzSanctuaryObjects,TestS3kHpzGraphRewind,SwScrlHpzTest,TestS3kAiz1SkipHeadless,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" \
  -Dsurefire.failIfNoSpecifiedTests=false -Ds3k.rom.path=... -Dsonic1.rom.path=... -Dsonic2.rom.path=... test
```

Result from fresh `target/surefire-reports`: 107 tests, 0 failures, 0 errors,
0 skips. Focused validation only. The new `$1601` load test asserts the HPZ
LevelSizes row (`$1880` × `$B20`), which the sanctuary bounds (`$1500-$1640`,
`$320`) cannot satisfy. The sanctuary headless lifecycle cases now run on `$17`
only, the ROM giant-ring destination.

### 2026-09-16 slices 2-3 — events, palette and animation

Commit `362771221` plus the guard repair that follows it. Implemented:
`Sonic3kHPZEvents` (`HPZ_ScreenInit` character limits, `HPZ_BackgroundInit` Knuckles
row patch, one-shot `Obj_HPZPaletteControl` allocation after the fade, `Events_fg_4`
chunk `$61` writes to foreground row 7 through the mutation pipeline); the shared
HPZ screen shake now also runs for `$1601`; `HPZPaletteControlObjectInstance`;
`AnPal_HPZ` and `AniPLC_HPZ` for `$1601` and `$1701` (OffsAnPal / Offs_AniFunc
entries 45 and 47); `Obj_HPZMasterEmerald` init writes `Palette_cycle_counter1 = 7999`.
AnPal counters, the `Palette_cycle_counters+$00` gate, `Events_bg+$00` and
`Events_fg_4` are captured by `HpzZoneRuntimeState`.

Decisions and limits:
- `HPZ_BackgroundEvent`'s `$EC0` seam state machine only sequences plane redraws;
  `SwScrlHpz` already selects the parallax origin from the same predicate. Not ported;
  a moving visual check across `$EC0` is still required before calling it equivalent.
- `HPZ_ScreenInit` runs on the first event pass rather than before the first frame.
- In headless fixtures the palette cycler resolves through a local ownership registry,
  so the AnPal test asserts palette colours rather than `ownerAt`. Breaking the cycle
  registration made that test fail (verified, then restored).

Evidence (worktree, all ROMs by absolute path, `maven_queue.py -Dmse=off`):
- Focused HPZ set (`TestHpzZoneRuntimeStatePaletteCycle`, `TestS3kHpzActEventsHeadless`,
  `TestS3kHpzPatternAnimation`, the slice-1 classes and the four required S3K classes):
  119 tests, 0 failures, 0 errors, 0 skips across the two runs.
- `-Pguards` full run on `362771221`: 669 tests, 1 failure
  (`TestZoneEventRuntimeAccessGuard`: the new events class called `GameServices`
  directly). Repaired by routing through a `paletteFadeActive()` event helper; the
  guard class and `TestS3kHpzActEventsHeadless` rerun green (1 + 5 tests, 0 skips).
  The full guard suite has not been rerun after the repair.

## Next: slice 5 inventory (Knuckles fight)

`CutsceneKnux_HPZ` (`sonic3k.asm:131264`) is a 47-routine AI fight, not a scripted
cutscene: proximity/decision tables (`loc_660BE`), `Find_SonicTails`, three DPLC sets
switched through `$44(a0)`, `mus_Knuckles` via a delayed music object, `Pal_CutsceneKnux`
on line 2, and shared coordination bits in `_unkFAB8` with the Robotnik ship sequence
(`PLC_KnuxHPZCutsceneShip`, `sonic3k.asm:131961`) that follows the fight. The collapse
sets `Events_fg_4` at `sonic3k.asm:132337`; music returns to `mus_LRZ2`
(`131801-131805`) and `mus_Miniboss` is used at `132591`. The routine block spans
roughly `131264-133503`; decompose it into fight, ship/emerald theft and collapse
children before implementation. Engine subtype `$28` still falls back to
`CutsceneKnucklesAiz2Instance`.

### 2026-09-17 slices 5-6b — Knuckles fight, theft, collapse and altar ending

Commits `020611217` (port) and `1cb447aab` (rewind sidecars, guard baselines) on
`feature/ai-hpz-knuckles-fight`. `CutsceneKnux_HPZ` copies itself into SST slot 48
(`Dynamic_object_RAM+object_size*45`) and runs all 47 routines; the theft adds
`loc_64C70`, the `loc_64E88` ship with head, flame, crane, emerald debris and both
`loc_6531E` spark chains, the gradual camera workers, `Obj_CreateBossExplosion` `$14`
and the `loc_655B2` block; the altar teleporter becomes `loc_45AD6` and its helper runs
`loc_45BF4`. `S3kRawAnimation` interprets `Animate_Raw*` scripts read from the ROM.

Measured against the Sonic + Tails complete-run segment `hpz22_2` (comparison only):
camera `$1580` to Knuckles releasing the player 294 frames (ROM `$2F2E`-`$3054`), which
needs Knuckles in slot 48 after the low-slot controller (295 before the slot copy);
last stage-2 camera shake to Player 1 `x_vel -$100` 279 frames (ROM `$37A1`-`$38B8`);
pad to `StartNewLevel` 120 frames (ROM `Level_frame_counter` `$1ADC`-`$1B54`); player
falls to Y `$64C`, Player 1 is held at `$1628` and lands on the pad at `($15C0,$562)`
as in the recording.

Findings: Knuckles' body and the boss dust use the player DPLC layout (count word,
count-1 in the top nibble), not `Perform_DPLC`'s, so their standalone sheets load through
`S3kSpriteDataLoader.loadDplcFrames`. `ChildObjDat_6664A` asks for 32 fragments but
`CreateChild6_Simple` stops at the first allocation failure, so the block's slot bounds the
count (30 in the scripted route). Generic rewind capture did not restore object links in
these children, and a class without a probe-compatible constructor was silently dropped on
restore (the spark orbiters); both surfaced only once restore windows ran while those
objects were alive.

Deferred or approximated: `Current_music+1` writes, the `Target_palette_line_2` copy,
`Player_Load_PLC`/`Queue_Kos_Module` timing and the shared VRAM ranges (ship over the
teleporter, dizzy stars over the dust) use standalone sheets; the slot-48 copy keeps an
existing occupant instead of overwriting it; the `loc_64D5C` camera fraction starts at
zero; `loc_64DAA` locks controller 1 without the ROM's stale logical word. Not verified:
reload of the copy after `Delete_Sprite_If_Not_In_Range`, Tails-alone and widescreen
routes, and a cold route into the fight (the dust child is covered since `a69c34437`).

Evidence (worktree, all ROMs by absolute path, `maven_queue.py -Dmse=off`): focused HPZ
and required S3K set 119 tests, 0 failures, 0 skips; `-Pguards` 669 tests, 0 failures,
0 skips on `1cb447aab`. The first guard run on `020611217` failed 5 guards (rewind
override/annotation, coverage, parent-dependent, raw `addDynamicObject`, static state),
including inherited teleporter entries; all were resolved in `1cb447aab`.

### 2026-09-17 native fight sequence and review fixes

Commit `a69c34437`. `TestS3kHpzKnucklesFightNativeSequence` seeds the state of `hpz22_2` row
`$287F` (movie frame 444437) after the level's first frame and plays the complete-run BK2.
Its expected values are the native slot-48 observations (`native/probe-fight`) and the
`_unkFAB8` column of `probe-fight`/`probe-altar`/`probe-ending`; movie frame N is row
N - 434070.

The first seeded probe diverged at rows `$2925` (glide end one frame early), `$294E`
(routine 4 instead of 0) and `$29B7` (Player 1 "falls through the floor"). None of these was
fight logic. A per-frame comparison against the trace's player rows showed three seed defects:
the level's first frame (initial player assembly) put Tails back at Player 1 - `$20` with zero
speed after the seed was written; the fixture had no rings, so Knuckles' `HurtCharacter` killed
the players (the "floor" fall at `$29B7` was Sonic's death arc); and `Level_frame_counter` was
unseeded, so Tails' auto-jump gate fired on other frames. With the seed written after the first
frame, Player 1's 64-entry position record, `Ring_count $27`, `Level_frame_counter $A77`,
`Camera_max_Y_pos $394` and the `loc_85CA4` wait word at 92 (created at row `$2863`), the engine
matched every Player 1/Player 2 position and speed and every Knuckles value until row `$29CD`.
That row was a real bug: `loc_64090` used the jump's all-hurt table instead of `off_640CC`, so
Tails beside the glide was hurt instead of hitting Knuckles. After the fix, every compared
field matches through row `$331D` except camera X from `$305A`.

| Row | Routine | HP | Knuckles | Engine |
| --- | --- | --- | --- | --- |
| `$28A5` | 2 | 8 | `$11F0,$42C` | same row |
| `$28B2`, `$2969`, `$29CD`, `$2A3D`, `$2AE9`, `$2B4C`, `$2BBE` | `$16` | 7, 6, 5, 4, 3, 2, 1 | per hit | same rows and positions |
| `$2A69` / `$2AA5` | `$12` / `$14` | 4 | `$1122,$42C` / `$1122,$430` | same; dust created `$2A79`, deleted `$2AA5` |
| `$2C2F` | `$18` | 0 | `$11B0,$3E3` | same |
| `$2C5B`, `$2CA8`, `$2D28`, `$2D38`, `$2D40` | `$1A`-`$22` | 0 | `$112C,$42C` | same |
| `$2DAE` | `$24` | 0 | `$1620,$3AC` | same |
| `_unkFAB8` | `$28DC`=1, `$2DAE`=0, `$2F2E`=1, `$3054`=3, `$32B5`=`$F`, `$32B6`=`$1F`, `$331D`=`$3F` | | | same rows |

All 41 routine/HP transitions between `$287F` and `$2DAE` match row and position. Later
comparison (scratch probe, not asserted): `$37DC`=`$7F` matches; bit 7 is set at `$386C`
against native `$386D`, and the ending then stays one frame ahead.

Remaining differences are not in the fight objects:

- **Camera X from `$305A`, 1 px late.** `loc_64D5C` adds speed into the `Camera_X_pos`
  longword. The native low word is `$6400`-`$67FF`, left by an earlier zone, since only the
  title screen clears `Camera_RAM`. The engine camera has no low word, and a cold level-select
  start would also have 0.
- **Player 1's jump at `$331F`.** `loc_64DAA` sets `Ctrl_1_locked` while `Ctrl_1_logical`
  still holds the jump button. The engine latches the logical word but movement reads the
  unlocked effective input, so the jump is cut short (shared input path, not changed here).
- **Lag frame at `$33DD`.** `Level_frame_counter` does not advance for one frame, which puts
  every later native event one frame behind. The seeded fixture does not model hardware timing.

The same pass applied the review fixes:

- **Hurt.** `HurtCharacter` reads `Ring_count` only for Player 1, so a sidekick is knocked
  back and never killed. Player 1 with rings drops them.
- **Explosions.** `CreateBossExp14` is `Obj_Wait` + `Obj_NormalExpControl`: normal explosions
  stay at `($1880,$3D0)`, outlive Knuckles, and draw `Random_Number` only after a child exists.
  `Obj_Explosion` routine 2 animates and draws on its first pass, including the ceiling
  explosions. The native altar frames show the same normal-explosion puffs.
- **Slot 48.** The copy runs on its creation frame; a probe saw slot 4 placed and slot 48
  initialised after one frame. The occupied-slot fallback now logs a warning.
- **Art and ROM reads.** The player DPLC layout is a `StandaloneArtEntry` field, and ROM
  script slices are cached per instance.
- **Bot-route assertions.** The pan now asserts the exact `$1581` (this route's pan starts from
  an odd camera X) and exactly 30 fragments.

Evidence on `a69c34437` (`maven_queue.py -Dmse=off`, ROM by absolute path): focused set
`TestS3kHpz*`, `TestHpz*`, `TestSonic3kTitleCardSublevelMappings`, `TestSonic3kObjectArtProvider`
and the four required S3K classes, 21 classes, 273 tests, 0 failures, 0 skips; `-Pguards`
669 tests, 0 failures, 0 skips. The sequence test fails when the `off_640CC` fix is reverted.

## Demo captures

| Clip | Shows | Setup | Moment |
| --- | --- | --- | --- |
| `01-hpz-playable-act-loads.mp4` | `$1601` loads the playable act (slice 1) | Cold `--zone hpz --act 2`, frames 0-150 | Whole clip; there is no pre-load lead-in |
| `02a-hpz-aniplc-teleporter-tiles-before-after.mp4` | `AniPLC_HPZ`: teleporter tube spirals instead of placeholder letter tiles | Teleport `$B40,$3C0`, idle, frames 100-280 | Continuous |
| `02b-hpz-anpal-wall-light-glow-before-after.mp4` | `AnPal_HPZ` wall-light glow | Teleport `$E40,$340`, idle, frames 100-280 | Continuous |
| `03-hpz-palette-control-camera-460.mp4` | `Obj_HPZPaletteControl` switches `Pal_HPZIntro` → `Pal_HPZ` (top gems pink → purple) | Cold start, `inputs/run-right-jump.txt`, frames 585-660 | Camera re-crosses `$460` at frame 621 |
| `04-hpz-knuckles-background-patch-before-after.mp4` | `HPZ_BackgroundInit` Knuckles row patch removes the Master Emerald backdrop | `--main knuckles`, same input, frames 385-660 | Backdrop enters at ~423 |
| `05-hpz-teleporter-lower-to-upper-pad.mp4` | `Obj_SSZHPZTeleporter` + `Obj_TeleporterBeam`: charge, rise `$4A0`, settle on the upper pad | Teleport `$AF0,$8B0`, `inputs/jump-onto-pad.txt`, frames 80-480 | Lands ~118, rise 260-340, released ~440 |
| `06-hpz-knuckles-teleporter-exit-to-ssz2.mp4` | Knuckles' forced `$4A` upper pad lifts him above camera Y `$240`; `loc_45B94` saves and starts `$A01` | `--main knuckles`, teleport `$AF0,$400`, `inputs/jump-onto-pad.txt`, frames 80-460 | Exit request frame 286, SSZ act 2 loaded at 298 (SSZ presentation itself is unimplemented) |
| `07-hpz-chaos-pedestal-enters-super-emerald-stage.mp4` | `365ac485b`: a Chaos Emerald (state 1) pedestal in `$1601` arms and starts the Super Emerald stage | `--emeralds 1111111`, teleport `$1520,$3AC`, `inputs/jump-onto-pedestal.txt`, frames 40-200 | Lands ~95, stage entry at 112 (15-frame `loc_90926` timer) |
| `08a-hpz-title-card-hidden-palace-no-act.mp4` | `bc8e5be6e`: `$1601` title card reads HIDDEN PALACE ZONE without an act number | `--title-card`, cold load, frames 0-200 | Card frames ~20-140 |
| `08b-lrz-boss-slot-title-card.mp4` | Same fix: engine zone 22 act 0 (`$1600`) shows the Lava Reef card instead of Hidden Palace | `--zone 22 --act 1 --title-card`, frames 0-180 | Act number reflects a direct load's apparent act (LRZ-owned, not verified here) |
| `20-hpz-knuckles-cold-route-uncut.mp4` | Uncut cold Knuckles route to SSZ act 2 from his movie input | `--main knuckles --input <knuckles complete-run bk2> --input-start 411496`, frames 0-999 | Exit ~880 |
| `21-hpz-sonic-tails-cold-route-to-fight-uncut.mp4` | Uncut cold Sonic + Tails route from `$1601` entry to the fight floor (current frontier) | `--input <sonic-tails complete-run bk2> --input-start 441757`, frames 0-2999 | Teleporter ~1500-1750 |
| `10-hpz-knuckles-fight-start.mp4` | Knuckles in slot 48, camera locks to `$10E0` (music plays off-camera; captures have no audio) | `raw-10-knuckles-fight-route`: `--x 0x1080 --y 0x42C`, `inputs/10-bot.txt` (scripted bot, no invincibility), frames 0-240 | Lock at 121 |
| `11-hpz-knuckles-fight-hit.mp4` | First hit, Knuckles knocked back | same raw, 140-260 | Hit at 188 |
| `12-hpz-knuckles-defeat-dizzy-runs-off.mp4` | Eighth hit, defeat, room shake and explosions, "!" stars, Knuckles runs off (re-cut after `a69c34437`: explosions draw on their first frame) | 1320-1780 | Defeat 1358, first explosion 1471, stars 1598 |
| `13a-hpz-emerald-theft-crane-grab.mp4` | Camera pan, Knuckles hugs the emerald, crane grabs it with sparkles and chips | 2150-2600 | Pan done 2204 |
| `13b-hpz-ship-sparks-zap-knuckles.mp4` | Knuckles hangs on the carried emerald, spark chains, zap, fall | 2640-3260 | Zap 3108, lands 3211 |
| `14-hpz-altar-floor-collapse.mp4` | `Obj_NormalExpControl` explosions around `($1880,$3D0)` and `Events_fg_4` collapse, player falls to Y `$64C` (re-cut after `a69c34437`) | 3340-3540 | Explosions 3385-3437, collapse 3402 |
| `15-hpz-knuckles-punches-collapse-block.mp4` | Knuckles punches the `loc_655B2` block into fragments | 3990-4110 | Break ~4061 |
| `16-hpz-ending-hold-knuckles-beamed-away.mp4` | Player held at `$1628`, camera shake, Knuckles jumps to the pad, beam lifts and removes him | 4400-4740 | Knuckles deleted 4701 |
| `17-hpz-sonic-teleports-exit-to-ssz.mp4` | Player walks onto the pad, vanishes, `$A00` load (SSZ presentation unimplemented) | 4690-4859 | SSZ act 1 at 4826 |

Not demonstrable at width 320: the `$AA0` camera limits (Knuckles right, Sonic/Tails
upper-route left). On the reachable routes a wall stops the player before the camera
reaches `$AA0`, so captures with and without the limits were identical
(`raw-04-knuckles-run*`, `raw-05-upper-left-*`). They remain covered by
`TestS3kHpzActEventsHeadless`. The screen shake and `Events_fg_4` collapse have no
production trigger until the Knuckles fight (slice 5) lands.

Located with `LevelTileUsageLocatorTool`: `AniPLC_HPZ` tiles `$2D0-$2DB` are placed only at
X `$B00-$B7F`, Y `$380-$47F` and `$880` (the teleporters); line-4 colours 1-2 at X
`$D00-$177F`, Y `$300-$47F`. Neither is visible from the level start, which is why the
first cold-load comparison showed no difference.

### 2026-09-16 slice 6a — teleporter transport and Knuckles exit

Implemented `TeleporterBeamObjectInstance` (faithful `Obj_TeleporterBeam`: spawn, wait,
expand, contract, `$46` progress byte) and the HPZ teleporter state machine (idle light and
`AnPal_HPZ` gate, charge, rise, `Gradual_SwingOffset` settle, Knuckles `$4A` override) plus
`HpzTeleporterRouteHelperObjectInstance` (`loc_45B94`: Knuckles exit to `$A01`; Sonic/Tails
helpers below X `$1000` delete). The altar teleporter's ending branch (`loc_45AD6`,
`loc_45BF4`) is deferred to the Knuckles fight slice because it drives the fight object
through `_unkFAA4` and `_unkFAB8`.

Route truth for the ending: the Sonic + Tails complete-run segment
`runs/s3k-sonic-tails-complete-emeralds/hpz22_2` (comparison data, used only to read the
route) enters `$1601` at row `$1E46`, rides the `$B40` teleporter at `$2328-$251C`, fights
Knuckles near X `$11C0` (`$2904-$2DE6`), crosses the altar at Y `$3AC`, drops through the
collapse near X `$18B0` to Y `$64C` (`$34BC`), then walks left and is held at X `$1628`
(`$37AA`) before the `$A00` exit. The existing `CnzTeleporterBeamInstance` is a timing
stand-in, not a port; CNZ keeps it until that route is revisited.

Evidence: `TestS3kHpzTeleporterHeadless` (new) plus `TestHpzSanctuaryObjects`,
`TestS3kHpzSanctuaryHeadless`, `TestS3kHpzGraphRewind`: 32 tests, 0 failures, 0 skips.

## Remaining acceptance work (2026-09-17)

User direction: tackle every open item, following methodology v2 as applied in the FBZ and
SOZ campaigns. Ordered so shared evidence precedes consumers; each row carries its own
evidence columns in the act matrix (implemented / reachable / rewind / native / visual).

| # | Work | Evidence target |
| --- | --- | --- |
| 1 | Knuckles fight, emerald theft, collapse, altar teleporter ending to `$A00` (lane `feature/ai-hpz-knuckles-fight`) | Focused ROM-derived tests, rewind at fight/ending spots, demo clips, independent boundary review |
| 2 | Cold controller routes from `$1601` entry: Sonic + Tails to `$A00`, Sonic solo, Tails, Knuckles to `$A01`; inputs preserved as authored logs, first blocker recorded | Route tests with actual resolved roster/width/donor; full uncut capture per route |
| 3 | Question-led native probes with the shared BizHawk capture host: `$EC0` background redraw visibility; `$1601` altar Master/Super Emerald presentation; teleporter beam/light cadence; palette-control switch | Declared question, ROM identity, entry recipe, fields and interval; engine vs native comparison recorded |
| 4 | Port `HPZ_BackgroundEvent`'s redraw machine only if probe 3 shows a visible difference; otherwise record the rejected change with evidence (SOZ precedent) | Native vs engine frames around `$EC0` |
| 5 | Verify `$1601` altar emerald object branches against the ROM and native capture | Focused tests + native frame |
| 6 | Remaining rewind spots: teleporter settle and beam deletion, fight/ending states, `$A00`/`$A01` load timeline isolation | Restore equality + forward replay |
| 7 | Breadth gaps: Knuckles exit and palette control at every width/donor; upper-route left limit wide rows; team shapes through the fight and exit | Matrix rows with case counts |
| 8 | Strict trace: `TestS3kSonicTailsHpz2SegmentTraceReplay` and the Knuckles `hpz22` segment; record frontier, fix HPZ-owned divergences from ROM evidence; LRZ3-owned prefix recorded as a dependency | Trace frontier log entry with command/commit/first error |
| 9 | Title card and sanctuary regression: assert the Hidden Palace card; add `$1701` sanctuary matrix rows for the AnPal/AniPLC change | Tests + matrix |
| 10 | Media: act-ordered highlights reel from verified captures, archive kept separate | `gameplay-highlights` skill |
| 11 | Delivery: change-based validation against `70aa0a0b7`, guards, docs/changelog reconciliation, integration to develop, push, worktree cleanup | Recorded commands and counts |

Out of HPZ scope and recorded as dependencies: LRZ3 → `$1601` incoming transition (needs LRZ
events); SSZ presentation after the exits (SSZ bring-up); replacing the CNZ beam stand-in.

### 2026-09-17 pedestal selectability and inherited run-chain failure

`365ac485b`: `Obj_HPZSuperEmerald` arms states 1 and 2 (`loc_907A8`, `$38` bit 0); the engine
armed only state 2, and `TestHpzSanctuaryObjects` asserted that engine choice. Corrected with the
ROM citation. Sanctuary set (`TestHpzSanctuaryObjects,TestS3kHpzSanctuaryHeadless,TestS3kHpzGraphRewind,*SuperEmerald*,*Sanctuary*`):
7 classes, 44 tests, 1 failure, 0 skips. The failure,
`TestS3kKnucklesSuperEmeraldRunChain#aiz1ToDoomsdayAcrossEverySpecialAndBonusStage` ("Segment 0 (aiz)
exit boundary (giant_ring) was never observed"), reproduces with the same message on the base commit
`70aa0a0b7` in `.worktrees/hpz-baseline-verify` (1 test, 1 failure): inherited, not attributed to HPZ work.

Native evidence note: the first complete-run capture replayed from movie frame 0 and had not reached
HPZ after seven minutes, so it was stopped. `capture_hpz_route_reference.lua` now saves native states
at planned movie frames on one pass; later probes load the nearest state (user recommendation).

### 2026-09-17 cold routes, capture alignment and native states

- `TestS3kHpzColdRoutes`: Sonic + Tails movie input from frame 441758 (row `$1E09`) reaches the
  fight floor via the `$B40` teleporter; Knuckles movie input from frame 411496 meets the `$A01`
  exit condition within 60 frames of the ROM's row `$35C` and loads SSZ act 2. 2 tests, 0 skips.
- `GameplayCaptureTool --input-start` plays a late movie section. The capture path runs one frame
  behind `HeadlessTestFixture` on the same input (per-frame comparison: first position difference
  at frame 6, a constant one-frame lag), so route captures use start frame - 1. Recorded as a
  harness difference, not a gameplay divergence.
- Native states for probes (host exit 0, 436 s, BK2 SHA-256 `AD40FB0B…3C0`, ROM SHA-1 verified):
  `native/sonic-tails-states/states/{0441749,0442965,0444117,0444373,0445781,0447381}.State`
  = rows `$1E00` (entry), `$22C0` (before lower teleporter), `$2740` and `$2840` (seam),
  `$2DC0` (after the fight, before the altar), `$3400` (collapse and ending).

### 2026-09-17 native probes from saved states

Six probes (`native/run-probes.sh`, exporter `capture_hpz_route_reference.lua`, BizHawk 2.11,
ROM SHA-1 `CFBF98C3…61D6`, movie SHA-256 `AD40FB0B…3C0`, host exit 0, 2-5 s each) answered:

| Question | Native evidence | Engine comparison | Conclusion |
| --- | --- | --- | --- |
| Palette control at camera X `$460` | Pink `Pal_HPZIntro` gem colours on screen before movie frame 442050 (camera `$45D`→`$463`), green `Pal_HPZ` colours after | Same threshold from source; demo `03` | Corroborated; first visible switched frame not isolable (tiles off screen at the crossing) |
| Teleporter light, gate, charge, rise, settle | `$040C/$0408` + gate while on screen; AnPal resumes at 8-frame cadence off screen; `word_4670C` every 4 frames with colour 1 = second word; drift start → first `$10` rise step 60 frames; 74 steps; settle offsets over the first 24 frames `0,0,0,-2,-4,…,-36` | Engine capture `raw-06`: drift → rise 60 frames, 74 steps, identical 24-frame settle offsets | Native behaviour matched for the sampled fields and interval |
| `$EC0` background redraw | `Events_routine_bg` 0→4 at movie frame 444308 (P1 X `$EC2`), →8 at 444315; the brick background stays continuous across all frames | Engine keeps continuous parallax without the redraw machine | Redraw machine port rejected: no visible native effect at this boundary |
| `$1601` altar pedestals | Save holds all Super Emeralds (`3333333`); coloured pedestals at the ROM positions, red/orange/blue/cyan colours | `--emeralds 3333333` capture at `$15A8,$3AC`: same colours and positions, frame-7 flicker on alternate frames | Presentation matches by inspection; camera Y differs only because the engine view was positioned |

Measurement hazard found and fixed: the first probe read palette line 4 at `$FC32`; palette lines
are `$20` bytes (`Normal_palette_line_4 = $FC60`), so those columns were wrong until the exporter fix.
The palette and teleporter conclusions above use the corrected re-run.

### 2026-09-17 rewind guard repair for the teleporter objects

The sanctuary-reveal lane's `-Pguards` run on `c7b890672` reported three failures owned by
`1000dc342` (not by that lane): `TestParentDependentGraphCoverageGuard` (new parent-dependent
`TeleporterBeamObjectInstance`), `TestRewindArchitectureGuard` (untriaged capture/restore overrides
in the teleporter, beam and route helper) and `TestRewindCoverageGuard` (final scalar fields and the
helper's `teleporter` reference).

Rejected: switching the three objects to generic capture. `TestS3kHpzCompatibilityMatrix` failed
34/34 teleporter rows at the "beam progress 8 roll" forward replay (restored teleporter lost its beam
link, so the replayed frame kept animation 5 instead of 2). Kept: typed `ObjectRefId` sidecars, with
explicit override triage in `TestRewindArchitectureGuard`, `CAPTURED` field policies in
`DefaultObjectRewindPolicies`, non-final scalar fields, and graph evidence
`TestS3kHpzCompatibilityMatrix` in `RewindRoundTripHarness`. Result: the four guard classes 33 tests
and the HPZ teleporter/breadth/lifecycle/route classes 140 tests, 0 failures, 0 skips.

### 2026-09-17 cold Sonic+Tails route matched to native through the first Knuckles hits

Continuous native capture `probe-route` (movie 441758-444437, every frame, from
`states/0441749.State`) against the cold `$1601` fixture on the recorded input. Engine row
after step *f* is native movie frame 441759+*f*. Four causes, fixed in order of first error:

1. **441759, camera X -64:** `SpawnLevelMainSprites` loc_6986 places `Obj_LevelIntro_PlayerRun`
   (sonic3k.asm:89940) for `$1601`, `$B00`, and Knuckles in `$300`/`$900`: held Right plus
   `Scroll_forced_X_pos` = start X + `$B0` until `x_pos+$10` reaches it. New
   `LevelIntroPlayerRunInstance`; the target comes from `Sonic_/Knux_Start_Locations`
   because positioned harness starts otherwise moved it (first attempt used the live
   player and broke the Knuckles exit and upper-corridor rows).
2. **443079, Sonic lifted onto the `$B40` pad edge:** `SolidObjCheckSloped2` uses the absolute
   `byte_466E8` sample and loc_1E45A's 1..16 overlap window; the engine subtracted the
   table's first byte as a baseline and used the wide window.
3. **443341/443426, charge and lift:** `SolidObjectTopSloped2_1P`'s standing path ignores
   `object_control` and only re-seats while `Status_OnObj` is set; loc_457A2 clears it.
   Native probe `probe-pad` (watch_codes on the teleporter routines) shows player status
   `$08` then `$04` (Roll, no InAir) and the pad's p1 standing bit held through rise and
   settle, cleared the frame after release. The engine had set the rider airborne, freezing
   camera Y through the rise.
4. **443631, unroll shifted Y -10:** `Sonic_RollSpeed` adds `y_radius - default_y_radius`;
   the teleporter sets Roll without roll radii, so the shift is 0.

Result: camera, Player_1 and Player_2 match every frame 441761-445002 (players and camera);
first remaining difference Player_2 at 445002, inside the fight lane's scope. Native camera X is
`$28` during the load frames (441750-441758, before the intro exists), carried from the
previous act through the load-time `DeformBgLayer`, so a cold load trails for two frames.
`TestS3kHpzColdRoutes#recordedInputsMatchNativeCheckpointsThroughTheFirstKnucklesHits` asserts
11 checkpoints (break-checked with a `$A0` run distance). Traces for the shared unroll change:
AIZ/CNZ/HCZ/ICZ/LBZ/MGZ/MHZ zone slices, MGZ and SOZ complete-run: identical error totals and
first errors to `54284527b` without the change (all inherited red). Guards 669/0.

### 2026-09-17 merged lanes: cold route matches native through the fight

Merges `8d14d2591` (fight lane to `6bb3aed8e`; parent-dependent baseline conflict resolved to the
fight lane's covered dust row) and `d507bcf22` (reveal lane `b1a9896c8`). After `mvn clean`, the
HPZ/results/required-S3K set is 357 tests, 0 failures. The cold Sonic+Tails route on recorded input
now matches native camera and both players every frame from 441761 through the whole fight window
(`probe-fight` 444437-445845, all hits and defeat). Remaining differences:

- From 446448: camera X 1 px (the fight lane's camera low-word finding; native value is history).
- **From 447157 (row `$331E`): Sonic's jump is capped.** Instrumented: the latched logical word
  holds jump, `handleMovement` receives jump, but `PlayableSpriteMovement.handleMovementDispatch`
  clears it because `controlLockBlocksScriptedMovement` treats `Ctrl_1_locked` like
  `object_control`. The ROM lock only stops the `Ctrl_1` to `Ctrl_1_logical` copy (loc_10760), so
  movement keeps the latched jump. Rejected quick fix: reading the latched word in `SpriteManager`
  alone (no effect, because the movement gate still clears it); changing that gate changes the
  shared contract behind 58 S3K `setControlLocked(true)` sites that expect an immobile player, so
  it needs a per-site ROM audit, not a route fix.
- The engine loads SSZ act 1 at 448777 against native 448755.

Demos (`GameplayCaptureTool --input <sonic-tails bk2> --input-start 441757`, raw
`raw-26-sonic-tails-cold-route-full`, frames 0-7149; before = `raw-21`):

| Clip | Shows | Frames |
| --- | --- | --- |
| `31a-hpz-level-intro-run-in-before-after.mp4` | Level intro run-in: camera held at `$40` while Sonic is walked right | 0-150 |
| `31b-hpz-teleporter-edge-no-landing-before-after.mp4` | Before: Sonic caught on the pad edge; after: runs past as native | 1280-1440 |
| `31c-hpz-teleporter-grounded-lift-camera-follows.mp4` | Charge, lift with camera following, settle, unroll in place | 1550-1920 |
| `26-hpz-sonic-tails-cold-route-full-uncut.mp4` | Uncut cold route from `$1601` entry through fight, altar and ending to SSZ | 0-7149 |

### 2026-09-17 route through the ending, background width, rider animation

- **Held jump through `loc_64DAA` (`581855733`).** The ROM lock leaves `Ctrl_1_logical` latched, so a
  jump held at the lock keeps full height. The theft controller carries the latched held bits as
  forced input (press suppressed) and rewrites the follower-history slot with the held-only word, so
  Tails replays what the ROM recorded. First attempt without the history rewrite made Tails jump at
  447194 instead of 447199 (the forced mask publishes a press); rejected. The shared
  `controlLockBlocksScriptedMovement` contract is unchanged: 38 of 44 ROM `Ctrl_1_locked` sets also
  write `Ctrl_1_logical`, and only 6 keep the held word.
- **Native lag frame 447347.** `loc_64964` sets `Events_fg_4`, starts the shake and spawns the
  collapse fragments on one frame; the pad is not polled. This CPU overrun is not a registered
  hardware-timing kind, so the cold route test declares it and skips that movie frame's input.
  With it, camera and both players match native from 441761 to 448821 (altar, collapse, ending)
  apart from camera X (sub-pixel history, 25 frames), and Sky Sanctuary act 1 loads.
- **Background cut off (user report, `581855733`).** The HPZ background was wrapped at 512 px, but
  its clouds occupy only layout columns 1-6, so background X 513+ showed column 0's empty chunks
  (black beside the teleporters). The scroll handler now widens the period to the rightmost visible
  column. First attempt widened by band spread only; no effect at camera Y where all lines are in
  deform band 0; rejected. Frames 2000-2012 are then pixel-identical to native (threshold 40,
  HUD excluded).
- **Rider animation (`b6a9f6d46`).** `object_control` 3 during the rise skips `Animate_Sonic`:
  mapping frame 0, and the roll resumes mid-script at the settle. Native `probe-anim` matches every
  frame 443540-443640.
- **Remaining visual difference:** the Robotnik ship hit flash alternates one frame earlier than
  native (engine grey on even capture frames, native on odd). The flash state and timer match; the
  phase question (palette upload versus sprite publication) is open.

Whole-route pixel diff (`raw-27`, 4872 frames with native screenshots): outside the title card,
residual differences are the lift animation (fixed above), the flash phase, the camera sub-pixel
offset, and positions after 447347 in a capture that cannot skip the lag frame.

Demos: `32a-hpz-background-full-width-before-after.mp4` (1960-2320),
`32b-hpz-teleporter-rise-settle-animation-before-after.mp4` (1760-1890),
`32c-hpz-altar-jump-held-through-lock-before-after.mp4` (5360-5470),
`28-hpz-sonic-tails-cold-route-full-uncut-final.mp4` (raw `raw-28`, 0-7149; before = `raw-26`).

### 2026-09-17 results exit fade and ship flash phase

- **Results exit (`968ca8694`, Mod API approved).** `SpecialStageProvider.resultsExitFadesToWhite()`
  defaults to the Sonic 1/2 behaviour (`SS_NormalExit`, S2 `PlaySound` + `Pal_FadeToWhite`); S3K returns
  `false`: its results object only writes `Game_mode = $C` (`loc_2E5B8`/`loc_2E5EC`) and `Level` fades to
  black (`loc_5FF6`) with no exit SFX. The return level fades in from the same colour. Pedestal capture:
  results 255 → 206 → 60 → 19 → level fade-in. The same commit repinned
  `GameLoop.debugCompleteSpecialStageWithEmerald()`, which `bfe15d493` pushed with a stale pin
  (fully qualified `@com.openggf.game.ModApi` hid it from the policy hook; both files now use `@ModApi`).
- **Ship hit flash phase (open, measured).** Native `probe-zapshots2` (every frame, `pal1_c7` = `$FC0E`):
  `Normal_palette` line 0 colour 7 becomes `$222` at movie 446860, the same logic frame as the engine's
  `sub_66372` write, but the native screenshot first shows the grey hull at 446861; engine captures show
  it at 446860. Sprite positions in the same screenshots match same-frame RAM, and a colour-set change
  comparison over 442400-442460 (palette cycling) shows no consistent one-frame shift, so this is not
  yet a proven global CRAM-upload lag. Left unchanged; a presentation-timing change needs a probe that
  isolates CRAM upload from sprite and plane uploads first. The exporter records `pal1_c7`.

### 2026-09-17 V-int palette latch (ship flash solution)

- **Mechanism.** S3K `VInt_8` uploads `Normal_palette` (or `Water_palette`) to CRAM in the same V-int as
  the sprite table and H-scroll buffer. The engine already publishes the sprite table and scroll at V-int
  (`LevelSpritePresentation`), but level palette writes reached the GPU immediately, one frame early.
- **Native proof.** CNZ1 (`probe-cnzpal`, full palette per frame): in all 400 frames where palette RAM
  changed, the screenshot shows the previous frame's RAM, none the current frame. BizHawk maps each colour
  nibble to `n*17`.
- **Change.** Level palette pipelines (ownership resolution, palette write supports, S3K cycler) upload
  through `PaletteUploadPresentation.cacheLevelPalette`; while an S3K sprite-table publication phase is
  active those lines (and the underwater texture) wait for the next publication. Direct
  `cachePaletteTexture` callers (special stages, menus, loads) stay immediate even if a latch is left
  armed; the rewind palette recache (`PaletteColorStateAdapter`) stays immediate because a restore redraws
  without a V-int. A non-publishing phase, the sanctuary results scene publication or a rewind restore
  releases and flushes. The latch state uses graphics-layer `PaletteView` types (ArchUnit layer rule). Only S3K's level init profile publishes, so S1, S2 and KiS2 never latch.
- **Evidence.** HPZ whole route (`raw-29` vs `raw-28`, 4872 frames with native screenshots): 144 frames
  closer to native, 0 further; over-200px frames 1127 → 1085; ship flash grey on native frames
  446861/863/865. Pedestal → special stage → results → level capture: frames identical with and
  without the latch. CNZ1 capture comparison got worse (843 frames): the pre-existing cycle start phase
  (`s3k-known-bugs.md`: Palette Cycles Run During the Level Fade-In), which the immediate upload masked.
- **Rejected.** (1) Latching every `cachePaletteTexture` call and releasing at `GameLoop` mode boundaries:
  `GameLoop` now carries the imported `@ModApi` spelling, so the policy hook requires a pin change for any
  edit; also a stale latch would hold special-stage palettes. (2) A `FadeManager.isActive()` gate on
  `Sonic3kPaletteCycler`: no measurable effect (headless fixture and capture tool do not run the ROM
  fade before level frames), reverted.

### 2026-09-17 Palette_fade_timer model (palette cycle start phase)

- **Instrumentation.** Native `probe-cnzfade` adds `Palette_fade_timer` (`$EE50`) and `Game_mode` to the
  exporter: the title-card loop holds `Game_mode $8C` with the frame counter at 0; LevelLoop starts at
  movie 131870 with the timer at 22, reading 21 at counter 1 and 0 at counter 22; the first `AnPal_CNZ`
  write is at counter 23. An engine log in `Sonic3kPaletteCycler` (removed) showed ticks from counter 1
  and `FadeManager` never active on the production path: the engine plays its fade during its own
  title-card mode, so the fade manager cannot stand in for the ROM timer.
- **Fix.** `Sonic3kGlobalAnimationState` models `Palette_fade_timer`: armed to `$16` in the fresh-level
  init step that already resets the ring clock (`Level/loc_64DC`; seamless reloads skip it), consumed
  once per level-frame animation update, and appended to the rewind snapshot tail. While it runs the
  cycler resolves palette owners but advances no AnPal cycle. `Sonic3kZoneEvents.paletteFadeActive()`
  (HPZ palette control allocation) reads the timer before the fade manager.
- **Evidence.** Headless CNZ1: line-3/line-4 change frames and colour values equal native palette RAM
  (`TestS3kPaletteFadeTimerGatesPaletteCycles`, break-checked: without the arm the writes start at 1).
  CNZ1 capture vs native (3330 position-matched frames): 3329 closer, 0 further, differing pixels
  17.0M → 2.9M. HPZ route capture unchanged. `TestS3kHpzActEventsHeadless`'s AnPal budget gained the
  22 fade frames. FBZ's `AnPal_FBZ` gate still uses `FadeManager` (no behaviour change inside the first
  22 frames: its edge needs counter & $FF == 0).
