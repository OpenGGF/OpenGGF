# Sonic CD stages: route matrix (USA)

## Motion eligibility maintenance (2026-11-16)

Sonic CD ordinary levels now opt into the shared live interpolation pipeline.
All seven provider handlers supply immutable projection phases. Native camera
arithmetic selects bands on private scratch composition while cloud, water,
light and swap clocks remain frozen; native scroll-lock/dead gates capture a
held background. `TestCdMotionContinuity` checks positive provider/profile
eligibility and a capability-negative control, plus PPZ native pan/rebuild rules.

`TestCdLiveMotionPixels` boots the user-supplied USA disc for every zone's Act 1
Present stage. It checks actual midpoint pixels, complete background motion
pairing and amounts, repeated drawings, exact pictures after blending, native
scroll state/player/frame isolation and frozen scroll-lock capture after release.
Per-zone phase tests cover the stage/program variants, immutable dependencies,
water/ramp/band arithmetic and 448-row sampling. The combined initial check runs
54 tests without skips. No new native-reference, cold-route or time-travel frontier
is certified. Rendered pixels for other periods/acts, exhaustive wraps, swap edges,
and complete within-stage rewind histories remain obligations. Load and time-travel
rewind histories also remain open beyond the tested R11A → R11D time warp and
R11A → R12A act clear. The motion checks supersede the earlier test that pinned
CD's profile exclusion.

Game `scd`, all seven zones, Sonic only. A partial matrix under the
[zone and act testing standard](../../../guide/contributing/level-test-standard.md):
it records which stages a native recording certifies and how far. It is not a
completion certificate. Loading a stage is not certification; all 70 load.

**Measured at integration `6c5c0e2764`** (2026-11-13) and unchanged, stage by
stage, at `45463c80bd` with every lane merged. The per-stage cells
below and the totals in the re-host plan's
[progress measure](../../plans/2026-11-12-sonic-cd-rehost.md#progress-measure)
are the only places these numbers live; update both together when a frontier
moves. The native recordings are user-local (BizHawk 2.11, USA disc) and never
committed; every test below skips without them.

## Palmtree Panic boulder obligation (2026-11-15)

`TestPpzBoulderArt` checks the first section's 19 boulder tiles against the
independently decoded asset hash for all ten R1 payloads. It needs only the
USA disc, not native recordings. `TestCdObjectRegistryStageScope` checks the
zone's boulder factory and name; common-object and PPZ solid tests cover the
retained solid routine. The [development ledger](../../../changelog/v0.8-prerelease-detailed.md#2026-11-15-palmtree-panic-boulder-art)
records the original failures and loading correction. This bounded art check
does not advance the route matrix; section-transition screenshots, broader
viewport coverage and inherited rewind/lifecycle gaps remain unassessed.

## Time Attack object rules

`TestCdTimeAttackObjects` loads the USA disc for R11A and R31A, both with
the IPX Time Attack selection and with the flag clear. It checks a placed
time post, the reserved HUD icon, the 1-Up monitor, the special-stage ring,
and the two Amy routines and intro spikes. It also checks when all seven
Time Stones can force a good future. `TestCdTimeAttackFlow` covers entry from
the title and restores the running mode through the existing rewind adapter.
The campaign interaction with Amy in R11A still uses a placeholder. These
checks cover the object rules in the two tested stage files.

## Playtest follow-up (2026-11-15)

The [bounded validation record](../2026-11-15-sonic-cd-playtest-fixes.md)
covers flower progression, diagonal springs, monitor item mappings, stage-local
object names, PPZ row sampling, results digits, audio balance and hosted-game
suspension. OBJECT/PRESENT gain focused object/art evidence; CAMERA/PRESENT
gain disc-backed row sampling and ten-variant PPZ scroll checks; LIFE gains
suspension/settings checks. Rendered results digits and same-live-instance restore passed. Composed
validation and matched-baseline attribution are complete; the broad smoke
verdict remains FAILED with input/skip gaps. End-to-end rewind and the user's
manual checklist remain pending. These working-tree
checks do not requalify the historical stage cells or close inherited width,
whole-route, native-picture or lifecycle gaps.

## Stage keys

A stage is its program name `R<zone><act><period>`: zone 1 Palmtree Panic,
3 Collision Chaos, 4 Tidal Tempest, 5 Quartz Quadrant, 6 Wacky Workbench,
7 Stardust Speedway, 8 Metallic Madness; period A Present, B Past, C Good
Future, D Bad Future. Engine zone is the `CdZone` ordinal and the act index is
0-3 for act 1 A-D, 4-7 for act 2 A-D, 8-9 for act 3 C-D (`SonicCdZoneRegistry`).
Entry is the stage-select secret's route; time travel is a level transition
between periods of one act (`CdWarpTransition`).

## Evidence kinds

| Kind | Check | What it certifies |
| --- | --- | --- |
| Route | `TestSonicCdFixtureFrontier` with `-Dsoniccd.fixtures.root=<native root>/rehost-long`: a cold power-on movie from stage start to the signpost, replayed row by row | Player, camera and compared fields equal native to the route's end. The ring counter is not a frontier field |
| Act end | `TestSonicCdActEndNative`: the same routes over the signpost, results and level exit | `asserted`: every channel equal to native over the end window |
| Fixture | `TestSonicCdFixtureFrontier` on `rehost-fixtures`: 60 idle frames, then Right with a jump every 60 frames, about 14 seconds | The stage's opening seconds only |
| Boss | `Test<Zone>BossNative` over native fights | Player, camera and every boss slot against native RAM |
| Picture | zone and object picture tests | Pixels against native screenshots at named rows; masks and pairing rules per test |

## Matrix

Route column: rows equal before the first divergence / route rows, or
**green** when equal to the end. Fixture column: **green**, or the first
diverging row and the frontier test's cause label (a guess, not a diagnosis).

| Stage | Route | Act end | Fixture | Notes |
| --- | --- | --- | --- | --- |
| R11A | **green** 4,220 | asserted | green | K2 trace green on all 1,411 rows |
| R11B | **green** 3,171 | asserted | green | |
| R11C | **green** 5,790 | asserted | **green** 829 | The short fixture matches through slope fall-off at row 677 |
| R11D | none | none | green | |
| R12A | **green** 4,244 | asserted | green | |
| R12B, R12C, R12D | none | none | green | |
| R13C, R13D | boss movies | boss | green | Both fights green to the stage exit; act end loads R31A |
| R31A | **green** 5,290 | asserted | green | |
| R31B | **green** 8,241 | asserted | green | |
| R31C | **green** 6,995 | asserted | green | |
| R31D | none | none | green | |
| R32A | **green** 6,682 | asserted | green | |
| R32B, R32C, R32D | none | none | green | |
| R33C, R33D | boss movies | boss | green | Both fights equal native on every row to the stage exit |
| R41A | **green** 5,449 | asserted | green | |
| R41B | **green** 4,721 | asserted | green | |
| R41C | **green** 6,013 | asserted | green | |
| R41D | none | none | green | |
| R42A | **green** 6,411 | asserted | green | All three break-pole grips, underwater `$37` damage, hurt recovery and signpost match. Results exit on row 6411 and load R43D |
| R42B, R42C, R42D | none | none | green | |
| R43C, R43D | boss movies | partial | green | Chase matches in both futures. Fight player and camera match through row 5959, before the engine misses water entry at row 5960. Slot parity stops at the first bubble pop, which ends a frame late in the engine. Native movies cover the capsule and results through NextLevel. The engine has no parity assertion for that tail. |
| R51A | **green** 4,756 | asserted | green | |
| R51B | **green** 5,494 | asserted | **green** 569 | The conveyor switches to its warp step on the trigger frame |
| R51C | **green** 7,151 | asserted | green | |
| R51D | none | none | green | |
| R52A | **green** 4,772 | asserted | green | |
| R52B, R52C, R52D | none | none | green | |
| R53C, R53D | boss movies | boss | green | Both fights green past the capsule to the stage exit; act end reaches R61A |
| R61A | **green** 4,892 | asserted | green | |
| R61B | **green** 5,449 | asserted | green | The full-act recording observes both projector animals entering at row 2749 and leaving at row 3209. The route passes above their sprites. |
| R61C | **green** 6,767 | asserted | green | The short fixture observes the flying animal placed at `($0488,$04D0)`. Its on-screen region matches native at rows 631 and 647 at 3 bits per color channel. |
| R61D | none | none | green | |
| R62A | **green** 4,787 | asserted | green | |
| R62B, R62C, R62D | none | none | green | |
| R63C, R63D | boss movies | boss | green | Complete fights by real play: defeat, escape, capsule, results and the next stage equal native. Both defeat fades have per-V-INT palette comparisons. |
| R71A | **green** 3,977 | asserted | green | |
| R71B | **green** 4,918 | asserted | green | |
| R71C | **green** 6,096 | asserted | green | |
| R71D | none | none | green | |
| R72A | **green** 4,399 | asserted | green | |
| R72B, R72C, R72D | none | none | green | |
| R73C, R73D | boss movies | boss | green | Both race wins equal native on every row; ten movies with zero slot mismatches |
| R81A | **green** 4,736 | asserted | green | |
| R81B | **green** 3,991 | native, engine pending | green | The separate 5,396-row act-end recording reaches the signpost, tally, and R82A exit state. Engine replay first differs at row 2635 on a Scarab rebound. |
| R81C | **green** 4,524 | none | green | A separate 4,694-row attempt reaches `($1AD5,$042E)` before stalling against terrain. |
| R81D | none | none | green | |
| R82A | **green** 5,697 | none | green | A separate 6,958-row attempt takes Mini Sonic through the lower passage and reaches `($1D9C,$0168)`, below the exit lane. |
| R82B, R82C, R82D | none | none | green | |
| R83C, R83D | boss movies | partial | green | Both fights are recorded through defeat, escape, Amy, and the stage-select return to title. `TestMmzPlayerMovies` matches player and camera through rows 6965 and 5576, respectively. Boss slots and the stage exit lack native assertions. |

Totals at `6c5c0e2764` and `45463c80bd`: routes 146,390 of 149,633 rows (97.8%; 26 of 28 to
their end); fixtures 56,879 of 57,049 rows (68 of 70). Later focused
reruns closed the R82A and R42A routes and the R11C and R51B short
fixtures. The historical totals count the diverging row as compared.
The cells above give the matched prefix length. In the act-end column
`none` means the route has no end-of-act assertion: it is not in
`TestSonicCdActEndNative`'s set, either
because it does not reach the goal or because no assertion was written. All 25
routes in that set are asserted after the R42A rerun. R81B has a separate
native act-end assertion and an earlier engine frontier described below. The
per-stage tables behind these numbers are local measurement output.

## First-divergence recheck (2026-11-18)

All four recordings reproduced their original divergence in the following
USA disc checks. Each selection used one focused fixture test with zero skips.

- Full routes on `95cac51440`, using `TestSonicCdFixtureFrontier` with
  `soniccd.fixtures.root=<native root>/rehost-long` and
  `soniccd.fixture.payloads=R42A,R82A`.
- Short fixtures on `9008241e6f`, using the same test with
  `soniccd.fixtures.root=<native root>/rehost-fixtures` and
  `soniccd.fixture.payloads=R11C,R51B`.

The R42A replay first differs when object `$05` at `x=$1590` changes from
routine 2 to 4. `ObjectIndex` (scddisasm/src/r4/object_index_2a.asm:3-8) resolves it to
the break pole.
`BreakPoleObject_0_Routine2` (scddisasm/src/objects/r4/break_pole.asm:27-52)
sets Sonic's x to the pole's x plus `$18`
on entry from the left and selects animation `$11`. The native row shows
`x=$15A8` and animation `$11`. The engine stays at `x=$158D` and animation
`$0F`. Both underwater flags are set. The frontier tool's Water label came
from the existing underwater bit.

The break-pole follow-up matched R42A through row 4403, including the grips at
4286 and 4329 and the releases at 4301 and 4384. Row 4404 is the new first
divergence. Native takes underwater hurt and spills rings near the `$37` spike
ball at `($1690,$0750)`. R41A, R41B and R41C still match to their recorded
ends. `TestSonicCdPlayerMovies` pins the matched R42A prefix. `TestTtzObjects`
checks either approach, both climb limits, the two timers, input-edge release,
death and replacement cleanup, unload, ownership through rewind recreation, and
the two disc-backed mapping frames.

The stationary spike ball follow-up matches R42A through row 4493, including the
underwater hit at row 4404. The first difference is X on the row 4494 recovery
from routine `$04` to `$02` (native `$162F`, engine `$162E`). The third grip
at row 4666 is outside the matched prefix. `TestTtzObjects` checks clearance
before contact, underwater knockback, ring spill, recreation and repeat contact
after rewind, and the disc mapping and nontransparent PLC tiles in R42A, R42C
and R42D. R42C/D still have fixture and focused evidence only. See the
[trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-tidal-tempest-stationary-spike-ball).

The hurt-input follow-up uses the logical pad word from the last normal
player pass while the hurt routine lands. On row 4494 the raw pad is
neutral, and the post-scroll current reads the retained Right bit. Player X
now matches at `$162F`; camera X remains `$158E`. The strict long-route
test originally reached row 5678, including the third grip at row 4666, before
a Time Stars lookup interrupted the next row. With the catalogue correction,
the strict movie matches all 6,411 rows. The separate act-end assertion
matches 748 rows around the signpost and results, then requests R43D on the
native exit row 6411. See the
[trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-logical-input-on-hurt-recovery).

In R82A, routing the right twin-walker platform's top-solid check to that
platform fixed the horizontal speed loss at row 4577. The next stop at row
5029 came from an invisible block cutting short Sonic's upward movement.
The native routine lets him pass through the underside. With both fixes,
R82A matches every one of its 5,697 recorded rows. The same selection kept
R81A, R81B and R81C green. The source notes and commands are in the
[trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-twin-walker-top-solid-receiver).

## Palmtree Panic slope fall-off recheck (2026-11-18)

The R11C short fixture first differed at row 677 in the animation ID and
mapping frame. The frontier label inferred object support from the retained
standing-slot byte `$2D`. The native On Object bit was clear and Air was set
on that row. Ground Right had selected Walk before the slope fall-off
locked movement. Publishing the admitted pose before the new lock brings
the short fixture to 829/829 rows and retains 5,790/5,790 on the long route.
See the [row evidence and commands](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-r11c-slope-fall-off-pose).

## Quartz Quadrant conveyor recheck (2026-11-18)

The R51B short fixture first differed at row 549, when Sonic triggered the
warp out of the Past. The conveyor selects its block list from the live period
and its speed from the period and warp flag. The trigger changes both values
before the conveyor runs in the same player update. Sonic's x velocity stays
at `$0616` while the conveyor's step changes from half a pixel to two pixels.
The short fixture now matches through all 569 recorded rows, including the 20
rows that differed on the baseline. R51A, R51C, R52A and R52B also match through
their short recordings. The R51A, R51B, R51C and R52A long routes match through
their recorded ends. These comparisons use the existing frontier fields and
recordings. See the [trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-quartz-quadrant-conveyor-at-warp-trigger).

## Palmtree Panic time-stop monitor

The USA disc places subtype 5 at `$0BF0,$0350` in R13C and R13D
(`StageObjects`, r1/stage_object_maps_3.asm:65-68). The item writes 300
(`ObjMonitorItem_Main`, Level/_Objects/Monitor and Time Post/Main.asm:593-611).
Native R13C captures at completed level frames 98–456 retained 300 and
the same palette step and timer. The matching unbroken control continued
cycling. `LevelStart` clears the word with the flower buffer
(Level/Main.asm:178-184), including death reentry (Level_MainLoop,
Level/Main.asm:401-408) and loading a warp destination.

`TestSonicCdCommonObjects#placedTimeStopMonitorFreezesTheStageUntilRestart`
drives Sonic into the disc placement in R13C and checks palette and ring
mapping frames across 401 subsequent level frames, rewind, and a death
reload. The ring witness uses an added slot because this part of the layout
has no rings. The native probe staged a monitor break and sampled palette
state. The ring witness and alternate future lack a native comparison.

## Standard obligations

| Obligation | State | Gap |
| --- | --- | --- |
| ENTRY | All 70 stages load from the disc (`TestCdStageLoaderAllPayloads`); three fixtures enter through the retail title and stage-select route (`TestCdRetailRouteReplay`); the master title lists Sonic CD and `TestSonicCdPlayerLaunch` starts a new game from it and equals native on 806 compared rows of R11A | Ordinary attract entries R11A, R43C and R82A have idle-title coverage in `TestCdStageDemos`. Intro-skip remains open. |
| OBJECT | Per-zone object tests and slot-exact appearance windows. `TestTtzObjects#timeStarsAnimateAndRetireInR42a` exercises all four active time-warp stars on the authenticated USA disc from a direct post-touch setup | Many objects have no native picture; layout entries without a saved-state bit respawn while live (R41C); ring collection order (R51A) |
| EVENT, CAMERA | Level events, boss arenas and scroll locks hosted; routes cover them where they pass | Wide-view triggers unchecked |
| BOSS | Seven bosses with native fight recordings | Tidal Tempest after the fight comparison limits and Metallic Madness boss slots and stage exit lack native assertions |
| LIFE | Deaths, game over and checkpoints run through the program flow (`TestCdGameFlowNative`, six native runs set up with RAM pokes); the idle timer and give-up equal native on an 11,490-row movie; saves persist across runs, and RAM DATA resets the Sonic CD record through the title menu (`TestCdDurableSaves`) | Checkpoint respawn after time travel unverified |
| LOAD | Act clear to the next stage, warp to another period, giant ring to the special stage and back; state a stage change leaves behind is cleared as native does (`TestSonicCdStageChangeIsolation`); stage-load and video-read timing in normal play is estimated from emulator measurements (`gameplay.loadTimeSimulation`) | Carry into a native warp destination unverified; the checkpoint and warp entries are single samples, and 12 stages' checkpoint and warp keys may name the wrong queued list ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |
| REWIND | Boss rewind tests for six zones, rings, zone signals, the Stardust Speedway lamp, the Collision Chaos drain. `TestCdStageLoadRewind` verifies that held rewind stops at the destination floor after R11A → R11D (time warp) and R11A → R12A (act clear) on the USA disc. See the [stage-load rewind policy](../../../status/known-discrepancies.md#sonic-cd-stage-load-live-rewind). | Within-stage rewind across complete routes and the other load edges remain unassessed. |
| PRESENT | Zone pictures per the presentation lanes (most early and end shots equal native); `TestCdTitlePicture` | Uncertified shots listed in each zone test; widths other than 320 draw but have no native reference |
| ROUTE | 28 full-act routes as above | Act 1 Bad Future and act 2 Past and futures: fixtures only; no complete-run recording |
| ORACLE | Constants and branches cite `docs/scddisasm`; trace rows comparison-only | Lag-frame CPU timing not modelled ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |

The USA idle rotation reads 23 controller samples before R11A's first level
iteration and 42 before each of R43C and R82A. R43C and R82A skip the title card
and drain a stage-art queue. All three return after sample 2048, for 2025, 2006,
and 2006 level iterations respectively. The disc-backed test compares Sonic's
position and rings at iterations 360, 360, and 120 respectively. R43C matches
native through all 2,006 level iterations.

- **R11A.** The first difference is at iteration 2. The first gameplay
  samples hold Right (`$0800`). On the disc, controls are locked before the
  level loop (scddisasm/src/Level/Main.asm:262-275, `LevelStart` sets
  `ctrlLocked=1`), so Sonic stands still.
  The engine applies Right for one iteration at X velocity 12, leaving an
  X sub-pixel offset of `$0C00` before `CdPlayerStage.applyControlLock` takes
  effect. That offset first shows as a one-pixel difference at iteration 154.
  Velocities match until Sonic rolls down a slope at iteration 1423. Rings
  first differ at iteration 1658. Normal stage starts share the control-lock
  gap, which has its own follow-up.
- **R82A.** The runs match through iteration 125. The engine hurts Sonic at
  iteration 126. On the same approach on the disc, Sonic pushes into laser
  `$1D` at (7033, 1415) at iteration 127 and enters the hurt routine at 128.
  See `loc_20B62A` (scddisasm/src/objects/r8/laser.asm:183-216) for the
  overlap check and hurt write. Rings first differ at iteration 212. The
  follow-up still needs to establish whether the laser cycle or spawn timing
  causes the earlier contact.

Breadth: Sonic CD has one character and no teams; it is not a donor. Donation
into it has bounded R11A coverage only (see
[cartridge donor hosting](#cartridge-donor-hosting-2026-11-16)). Widths 400, 528 and 800 draw full pictures in
the capture tools but have no native reference; the other presets are unchecked.

## Sound Test Special Stage 8

`TestCdSoundTestStageEntry` enters via the USA disc's title and Sound Test.
The completion case sets the UFO count to zero and empties the six UFO slots
after entry. The production stage session then detects completion and runs
its exit fade. The test checks the acknowledgement at 320 and 400 pixels,
the ending and title cues, held versus fresh confirmation, and unchanged
backup RAM and campaign progress. A separate case checks Start abort.
Both local native recordings end with six UFOs remaining. A natural clear
and parity for the original `SPEEND.MMD` credits presentation remain open.

## Stage pictures and Wacky Workbench defeat fades

`StageMainLoop` (common/main.asm:329-340) waits for `VBlankStage`
(common/main.asm:854-877). That V-INT uploads the palette, horizontal scroll
and sprite table prepared by the same stage iteration. It also updates the
animated tiles in `VBlankStagePlayerGfxDone` (common/main.asm:891-902).
The other stage dialect does the same in `VInt_Level` (Level/Main.asm:821-847).
`StageMainLoopUpdate` (common/main.asm:406-410) runs objects and
`StageMainLoopDraw` (common/main.asm:443-453) cycles colors after drawing
objects. In Wacky Workbench, `CycleColor` (r6/palette_cycle_c.asm:23-46),
`BossFadeStageWritePalette` (common/palette_fade.asm:412-434), and
`ElectricBeamsAnimateMainPalette` (objects/r6/electric_beams.asm:201-211)
write the palette for that iteration.

The live engine draws the completed iteration's sprites, scroll, palette,
and animated tiles together. This is the picture the disc displays at the
next V-INT. The base engine already matched ordinary palette rows at this
pairing. Comparing engine-after-R to the CRAM sampled at the end of native
row R made ordinary writes appear early.

`EggmanDefeatSequenceFadeScreen` and `EggmanDefeatSequenceFadeObjects`
(objects/r6/boss.asm:545-559) hold the stage loop twice. The restore reads
the separate target buffer filled by `LoadFadePalette`
(common/palette_load_fade.asm:3-15). The palette cycler changes only the
active buffer.

The boss calls each fade from inside `UpdateObjects`
(`StageMainLoopUpdate`, common/main.asm:406-410). The two waits set routine
`$12` and call VSync 22 times each (`FadeToWhite`,
common/palette_fade.asm:285-295, and `BossFadeObjectsFromWhite`,
common/palette_fade.asm:463-484). `VBlank` (common/main.asm:716-738) writes
vertical scroll before dispatch. `VBlankIndex` (common/main.asm:754-765)
dispatches `VBlankPaletteFade` (common/main.asm:951-962). Its
`VBlankCommon` (common/main.asm:1014-1037) reads the pads and uploads CRAM.
`VBlankCommonResume` (common/main.asm:1039-1054) then transfers the sprite
table to VRAM `$F800` and H-scroll to `$FC00`, using the buffers that the
last completed stage iteration prepared. The fade services
`ProcessGfxQueueFast` (common/gfx_queue.asm:103-112) and advances the
decompressor setup after VSync, when the fade computes the next step. `VBlankDispatchDone`
(common/main.asm:740-750) still flushes FM requests and advances its
interrupt-owned counters.

The blocked object pass has yet to reach `StageMainLoopUpdateScroll` and
`StageMainLoopDraw` (common/main.asm:440-453). The fade interrupt omits
`LoadPlayerGfx` and `VBlankStagePlayerGfxDone`
(`VBlankStage`, common/main.asm:854-900). Existing player art, stage
animation patterns, plane maps and HUD digit tiles remain resident.
`DrawStage` (r6/stage_draw_3c.asm:17-34 and r6/stage_draw_3d.asm:17-34)
would stream the plane-map rows and columns on a regular stage V-INT.
`UpdateHudNumbers` (objects/hud_points.asm:210-257) ticks the timer and
writes its centiseconds on that same ordinary V-INT. During the four held
windows, the native hashes of H-scroll, the SAT, both plane maps, HUD art,
WWZ animation art and player art were constant within each hold. Timer
RAM was also constant.

The engine retains the last sprite and scroll picture before the boss
enters the wait. It displays the fade palette prepared by each held tick
with that picture, using the same palette pairing as ordinary frames.
The stage art and HUD publish when the next ordinary picture is due.
In both restore holds, a duplicate offscreen ring had previously consumed
16 pixels of the sprite scanline budget and clipped the escape pod.
Placed rings now enter the table through their object slots, as in
`ObjRing_Main` (Level/_Objects/Ring/Main.asm:214-232) and `DrawObject`
(common/object.asm:100-154). Tiles-only pictures already matched in all
four holds. The full-picture differences came from sprite admission and
which sprite table was on screen.

The native probe reads CRAM after `FrameAdvance`, alongside the video
buffer used for pictures. `TestWwzBossFades` compares CRAM and the backdrop
at each of the 672 recorded V-INTs to the engine after the preceding row.
The two recordings have no lag in these windows. Good Future hold rows are
5473-5495 and 5553-5575. Bad Future hold rows are 5724-5746 and 5804-5826.
The test also compares the stage frame counter at the same replay row,
including when the stage loop resumes. The user-local CRAM recordings are
under `rehost-boss-wwz/R63C-fades` and `R63D-fades`. Each hold window
includes the entry and 22 subsequent V-INTs. A rewind check restores the
first Good Future hold at row 5481 and checks the held rows, palette words,
and gameplay resumption at row 5496 on both passes. `TestWwzBossNative`
checks slots and timers at completed level frames.

`TestWwzBossHoldPictures` compares all 320×224 pixels at native channel
precision, including the HUD, during both holds in each recording and
across their boundaries. It also checks the stage animation counters
during the holds, when the fade can conceal a premature art update.
Restoring a snapshot inside a wait also restores the last published HUD
digits from the HUD object's captured values.
The Good Future picture windows are rows 5475–5495
and 5555–5575. The Bad Future picture windows are 5726–5746 and 5806–5826. These
rows have no lag. The lossless PNGs are user-local, under
`rehost-boss-wwz/{R63C,R63D}-fades/pictures/`, indexed by trace row.
The cartridge palette upload paths need their own displayed-frame checks.

## Palmtree Panic campaign Amy Rose (hosted)

R11A hosts Amy (`$2F`) and her hearts
(`$30`). `TestPpzAmyRose` compares slot, sampled position and despawn with
the stage-select R11A recording. Its disc-backed cases cover the stationary
chase and wait thresholds, grab, jump release, removal while holding Sonic
before and after rewind, and the Time Attack write of the level-end palette.
See `ObjAmyRose` (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:7-26).
The stage-select route passes Amy without a grab. Native grab and release
still need a dedicated recording.

## Parked Sonic CD gaps

- **Metallic Madness boss door.** `BossDoorObject_0_Routine4`
  (objects/r8/boss_door.asm:64-88) clears foreground columns as the door opens.
  `PlaceBlock` draws to VDP plane A and leaves the layout and collision data
  intact (r8/stage_draw_3c.asm:398-428, r8/stage_draw_3d.asm:398-428).
  `PlaceBlock` (r8/stage_draw_3c.asm:415-416) skips the draw when
  `CheckBlockVisible` fails, so off-screen blocks stay intact.
  The R83C and R83D fight probes record camera positions and door `$3D`
  slots. Use those movies to capture the foreground plane A tile entries
  from before the trigger through the slide and the next camera redraw.
  Use the visible lifetime of the cleared columns to specify an engine render operation.

The three screen items below stay parked until native picture sequences
are available to certify parity. Each needs a visual review against its
capture before it can close.

- **Pencil Test playback.** Visual Mode plays all 840 picture banks. The
  stream has no measured seek and uses `CdFmvStreamer.SEEK_VINTS` (20 V-INTs).
  Capture native frames from entry through exit, including the border,
  palette fades and closing sequence. Use the program's disc data for
  the border and palettes. See `Start` (FMV/Main (Pencil Test).asm:71-98)
  and `MainLoop` (FMV/Main (Pencil Test).asm:118-137).
- **Thank You presentation.** Visual Mode opens an archive contact sheet
  after the good ending. It reads the Nemesis tiles at `[$0452, $0A1C)`,
  one lookahead byte at `$0A1C`, and the Enigma map at `[$0A1C, $0AAA)`
  in `THANKS_D.BIN;1`. The 64 palette words come from `[$19CE, $1A4E)`
  in `THANKS_M.MMD;1`. Capture native frames from entry through the
  composition, animations, fades, and exit. See `ThankYou_CommandLoop`
  and `ThankYou_UpdateFrame` (Thank You/Main.asm:70-107).
- **Comin' Soon presentation.** Stage select opens an archive contact
  sheet. It reads the 150 tiles at `[$02D8, $07DC)` and palette at `$02B8`
  in `COME__.MMD;1`. Capture the native composition and palette changes
  through the return to title.
  See `Start` (Comin Soon/Main.asm:49-118) and
  `VInterrupt` (Comin Soon/Main.asm:119-156).

## Open Sonic CD fixes (owned)

- **Recorded-input pause audio.** [PR #413](https://github.com/OpenGGF/OpenGGF/pull/413) owns the fix.
  The engine's recorded-input pause omits the music pause requests.
  `PauseMusic` selects PCM for ordinary Past music and CDDA otherwise,
  including Past invincibility and speed shoes (Level/Main.asm:620-643).
- **Extra-life ring sound.** The disc's `CollectRing .GainLife` requests only
  `SCMD_YESSFX` (Level/_Objects/Ring/Main.asm:290-312). The engine's shared
  collector also plays the ordinary pickup cue. The pickup cue fix is
  planned after [PR #399](https://github.com/OpenGGF/OpenGGF/pull/399), which
  adds the YES voice.
- **R43C and R43D boss fight.** The fix is in progress on
  `bugfix/ai-scd-ttz-boss-water-entry`. `TestTtzBossNative` matches player
  and camera through row 5959. The engine misses water entry on row 5960.
  The slot check stops at the first bubble pop because the engine ends its
  animation one frame late. The 6,843- and 6,842-row native traces cover the
  fight, capsule release, and results through NextLevel. Both probes log the
  subsequent program changes. The engine still needs a comparison past the
  two divergences through the exit.
  [`TestTtzBossNative`](../../../../src/test/java/com/openggf/game/soniccd/TestTtzBossNative.java#L39-L84).

## Metallic Madness act-end routes (2026-11-21)

Cold BizHawk 2.11 replays used the USA disc and controller-only movies from
retail stage select. The movies, v5 traces and goal probes stay under the
user-local native root, in `u50-mmz-act-end/R81B` and
`u50-mmz-act-end/partial/{R81C,R82A}`. The original route pins remain the
source of the player and camera counts in the table.

- **R81B.** The 7,995-input movie triggers the signpost on trace row 4677 and
  stops the timer. The results object appears on row 4857 with 4,000 time points
  and 200 ring points. The score rises from 300 to 4,500. The goal probe
  continues one row beyond the trace to catch restart 2 and the Act 2 Present
  (R82A) exit state at row 5396. Retail stage select returns to the title.
  The results' exit state supplies the campaign selection reference.
  `TestSonicCdActEndNative#metallicMadnessPastActEnd` checks the native tail
  and bounds engine comparison at row 2635, where native reverses Sonic's
  downward speed from `$0F18` to `-$0F18` when Scarab `$2F` becomes
  explosion `$18`. The engine retains `$0F18`.
  `loc_206CD8` (scddisasm/src/r8/player_object_collide.asm:456-463)
  dispatches the rear touch to `sub_2069E8`.
  `loc_206A5E` (scddisasm/src/r8/player_object_collide.asm:203-216)
  awards points, changes the object ID, and negates downward speed when
  Sonic is above the enemy. The engine's `MmzScarabBody.enemy` calls
  `killAsEnemy` on the special-touch path, which bypasses the shared
  enemy rebound.
- **R81C.** The 7,047-input movie stalls against terrain at `($1AD5,$042E)`.
  Additional branches reached the upper platform and the spring below the
  ceiling, and no branch reached the signpost.
- **R82A.** The 9,297-input movie takes the lower passage as Mini Sonic and
  reaches the upper chamber below the exit lane. The return toward the
  restoring laser stalls at the one-way door near `$1DA8`.
  That movie has no signpost trigger.

## Metallic Madness fight comparison

`rehost-boss-mmz` has 7,331 rows for R83C and 6,520 for R83D, with boss,
child, and door slots in the probes. R83C records three hits, defeat near row
6666, Amy around 6797, and the stage restart at 7163. R83D records the defeat,
Amy, and stage end at row 6353.
`TestMmzPlayerMovies` caps the R83C player and camera comparison at row 6965
after Amy. It caps R83D at row 5576, before the native player is hurt on
5577. The remaining rows, boss slots, and stage exits need native assertions.
[`TestMmzPlayerMovies`](../../../../src/test/java/com/openggf/game/soniccd/TestMmzPlayerMovies.java#L57-L76).

## Unreachable in shipped data

- **Palmtree Panic waterfall.** The Present object index calls `$04` an
  unused, broken waterfall generator (`ObjectIndex`, Level/Palmtree Panic/Object Index (Act 1 Present).asm:7-11).
  [`TestPpzObjectsA`](../../../../src/test/java/com/openggf/game/soniccd/TestPpzObjectsA.java#L548-L562)
  confirms zero placements across all ten USA Palmtree Panic layouts.
  The generator's `PlaceBlockAtPos` writes are unimplemented
  (`ObjWaterfall_Main`, Level/Palmtree Panic/Objects/Effects/Main.asm:36-58).

## Native recordings still needed

Use the USA disc in BizHawk 2.11. Keep the starting state and input together
with the recorded rows or audio and pictures needed by the check.

| Gap | Already recorded | Missing evidence |
| --- | --- | --- |
| Campaign endings | R83C and R83D stage-select fights return to the title. `rehost-programs` has video and audio of both endings entered through Visual Mode. | On the campaign route, clear R83C with all Time Stones or all good futures, and clear R83D with neither set complete. Capture the final stage exit, saved flags, `GameDone` selection, and the campaign path through credits back to title (CD Initial Program/IPX.asm:301-326). |
| Full campaign | `rehost-timetravel` records New Game warps. `rehost-flow` records act and giant-ring handoffs with RAM setup. | One cold-boot movie from empty backup RAM and New Game through the R83 clear. Include ordinary act exits, a warp, a giant-ring return, carryover between acts, and the last save and ending. |
| Natural Special Stage 8 clear | `rehost-programs/st-ss8` records Sound Test entry. `rehost-special` adds a timeout and a terrain run, both with all six UFOs left at the last active sample. | Clear all six UFOs by controller input. Capture the success state, fade, `SPEEND.MMD` credits with audio, and return to title (`SoundTest_SpecStg8`, CD Initial Program/IPX.asm:1065-1079). |
| R81C and R82A act ends | The new partial attempts have 4,694 and 6,958 trace rows, respectively. The goal probes capture no signpost trigger. | Complete both routes through the signpost, time and ring tallies, and results exit. |
| R41A swing and switch elevator ([PR #403](https://github.com/OpenGGF/OpenGGF/pull/403)) | The long trace logs spawn events for the `$29` swing and `$30` elevator. Its player rows show no support by either object. | Ride the swing seat at `($0480,$0180)` and press its switch. At the elevator at `($09E0,$0540)`, press and release the switch and ride the side platform. Capture rider support, swing phase, switch states, and column and platform motion during those interactions. See `StageObjects` (r4/stage_object_maps_1.asm:38-41, 93-94). |

## Cartridge donor hosting (2026-11-16)

`TestCdDonorHosting` boots R11A on the USA disc with six cartridge combinations:
S1 Sonic, S2 Sonic/Tails and S3K Sonic/Tails/Knuckles. It checks the bound character,
semantic spring animation, host movement ownership, ordinary rightward movement
past entry and S3K shield grants. Unsupported donor characters and disc donation
have explicit refusal checks. This is bounded host/character coverage; it does
not certify full donated routes, mini/3D drawings, special stages, bosses,
time travel, other periods, teams or viewport combinations. The native matrix
above still describes plain Sonic only. See the [runtime validation record](../2026-11-16-sonic-cd-mod-api.md).
