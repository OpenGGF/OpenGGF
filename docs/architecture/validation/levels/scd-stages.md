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
is certified. Rendered pixels for other periods/acts, exhaustive wraps, swap edges
and complete rewind/load/time-travel histories remain obligations. These checks
supersede the earlier test that pinned CD's profile exclusion.

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
| R43C, R43D | boss movies | partial | green | Chase equal in both futures; fight compared to row 4887 of R43D; defeat, escape and capsule recorded, not compared |
| R51A | **green** 4,756 | asserted | green | |
| R51B | **green** 5,494 | asserted | **green** 569 | The conveyor switches to its warp step on the trigger frame |
| R51C | **green** 7,151 | asserted | green | |
| R51D | none | none | green | |
| R52A | **green** 4,772 | asserted | green | |
| R52B, R52C, R52D | none | none | green | |
| R53C, R53D | boss movies | boss | green | Both fights green past the capsule to the stage exit; act end reaches R61A |
| R61A | **green** 4,892 | asserted | green | |
| R61B | **green** 5,449 | asserted | green | |
| R61C | **green** 6,767 | asserted | green | |
| R61D | none | none | green | |
| R62A | **green** 4,787 | asserted | green | |
| R62B, R62C, R62D | none | none | green | |
| R63C, R63D | boss movies | boss | green | Complete fights by real play: defeat, escape, capsule, results and the next stage equal native |
| R71A | **green** 3,977 | asserted | green | |
| R71B | **green** 4,918 | asserted | green | |
| R71C | **green** 6,096 | asserted | green | |
| R71D | none | none | green | |
| R72A | **green** 4,399 | asserted | green | |
| R72B, R72C, R72D | none | none | green | |
| R73C, R73D | boss movies | boss | green | Both race wins equal native on every row; ten movies with zero slot mismatches |
| R81A | **green** 4,736 | asserted | green | |
| R81B | **green** 3,991 | none | green | Route does not reach the goal |
| R81C | **green** 4,524 | none | green | Route does not reach the goal |
| R81D | none | none | green | |
| R82A | **green** 5,697 | none | green | Player and camera match through the recording's end, before the signpost |
| R82B, R82C, R82D | none | none | green | |
| R83C, R83D | approach only | none | green | Approach green to row 1,762 of R83C; fight and endings run in the engine with test-driven hits, not compared; no native ending recording |

Totals at `6c5c0e2764` and `45463c80bd`: routes 146,390 of 149,633 rows (97.8%; 26 of 28 to
their end); fixtures 56,879 of 57,049 rows (68 of 70). Later focused
reruns closed the R82A and R42A routes and the R11C and R51B short
fixtures. The historical totals count the diverging row as compared.
The cells above give the matched prefix length. In the act-end column
`none` means the route has no end-of-act assertion: it is not in
`TestSonicCdActEndNative`'s set, either
because it does not reach the goal or because no assertion was written. All 25
routes in that set are asserted after the R42A rerun. The per-stage tables
behind these numbers are local measurement output.

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

## Standard obligations

| Obligation | State | Gap |
| --- | --- | --- |
| ENTRY | All 70 stages load from the disc (`TestCdStageLoaderAllPayloads`); three fixtures enter through the retail title and stage-select route (`TestCdRetailRouteReplay`); the master title lists Sonic CD and `TestSonicCdPlayerLaunch` starts a new game from it and equals native on 806 compared rows of R11A | Intro-skip and alternate entries not assessed; the attract demos are not hosted |
| OBJECT | Per-zone object tests and slot-exact appearance windows. `TestTtzObjects#timeStarsAnimateAndRetireInR42a` exercises all four active time-warp stars on the authenticated USA disc from a direct post-touch setup | Many objects have no native picture; layout entries without a saved-state bit respawn while live (R41C); ring collection order (R51A) |
| EVENT, CAMERA | Level events, boss arenas and scroll locks hosted; routes cover them where they pass | Wide-view triggers unchecked |
| BOSS | Seven bosses; native fights for six, as above | Tidal Tempest defeat onwards and the Metallic Madness fight have no native comparison |
| LIFE | Deaths, game over and checkpoints run through the program flow (`TestCdGameFlowNative`, six native runs set up with RAM pokes); the idle timer and give-up equal native on an 11,490-row movie; saves persist across runs, and RAM DATA resets the Sonic CD record through the title menu (`TestCdDurableSaves`) | Checkpoint respawn after time travel unverified |
| LOAD | Act clear to the next stage, warp to another period, giant ring to the special stage and back; state a stage change leaves behind is cleared as native does (`TestSonicCdStageChangeIsolation`); stage-load and video-read timing in normal play is estimated from emulator measurements (`gameplay.loadTimeSimulation`) | Carry into a native warp destination unverified; the checkpoint and warp entries are single samples, and 12 stages' checkpoint and warp keys may name the wrong queued list ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |
| REWIND | Boss rewind tests for six zones, rings, zone signals, the Stardust Speedway lamp, the Collision Chaos drain | Spot coverage per the rewind contract not assessed; cross-load rewind not assessed |
| PRESENT | Zone pictures per the presentation lanes (most early and end shots equal native); `TestCdTitlePicture` | Uncertified shots listed in each zone test; widths other than 320 draw but have no native reference |
| ROUTE | 28 full-act routes as above | Act 1 Bad Future and act 2 Past and futures: fixtures only; no complete-run recording |
| ORACLE | Constants and branches cite `docs/scddisasm`; trace rows comparison-only | Lag-frame CPU timing not modelled ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |

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

## Parked Sonic CD gaps

- **Metallic Madness boss door.** `BossDoorObject_0_Routine4`
  (objects/r8/boss_door.asm:64-88) clears foreground columns as the door opens.
  `PlaceBlock` draws to VDP plane A and leaves the layout and collision data
  intact (r8/stage_draw_3c.asm:398-428, r8/stage_draw_3d.asm:398-428).
  In R83C and R83D, record camera positions and foreground tiles from before
  the door trigger through the slide and the next camera redraw. Use the
  visible lifetime of the cleared columns to specify an engine render operation.
- **Palmtree Panic waterfall.** `ObjWaterfall_Main`
  (Level/Palmtree Panic/Objects/Effects/Main.asm:36-58) calls `PlaceBlockAtPos`
  on aligned rows. That routine writes the chunk block and redraws it if visible
  (Level/Block Draw.asm:286-319). The ten USA stage payloads have zero
  placements of object `$04` (`TestPpzObjectsA`). A shipped placement or
  spawn path would justify recording the fall and checking its writes.
- **Recorded-input pause audio.** The ROM pause path in the engine omits the
  CDDA and PCM pause requests. `PauseMusic` (Level/Main.asm:620-643) selects
  CDDA outside the Past and for invincibility or speed shoes, and PCM for
  ordinary Past music. Record Start pause and resume with a native pad movie
  and audio in R11A and R11B. Include an R11B case with invincibility or speed
  shoes and check which music source stops and resumes in each case.
- **Extra-life ring sound.** The shared collector also plays the ordinary
  pickup cue when the 100th or 200th ring awards a life.
  `CollectRing .GainLife` (Level/_Objects/Ring/Main.asm:290-312) requests
  only the extra-life voice. Start Sonic beside a placed ring at 99 and
  199 rings with the respective award flag clear. Collect it by controller
  input. Compare the cue requests and audio against an ordinary pickup in
  the same stage.
- **Palmtree Panic campaign Amy Rose.** R11A object `$2F` remains a
  placeholder (`ObjAmyRose`, Level/Palmtree Panic/Objects/Amy Rose/Main.asm:7-26).
  Start R11A in campaign mode and stay in the Present. Record Sonic approaching
  Amy near the act end, her grabbing Sonic, Sonic breaking free, and Amy
  leaving. Include Amy and heart slots, Sonic's motion, and the palette before
  and after the encounter.
- **Wacky Workbench defeat fades.** Eggman's update runs two blocking fades
  (`EggmanDefeatSequenceFadeScreen` and `EggmanDefeatSequenceFadeObjects`,
  objects/r6/boss.asm:545-559). Each runs 22 programmed VSync steps.
  See `FadeToWhite` (Level/Palette Fade.asm:273-285) and
  `BossFadeObjectsFromWhite` (common/palette_fade.asm:463-484).
  Live play takes one frame per fade. The existing comparison matches slots
  and palette-cycle timers at completed level frames. Record every VBlank
  in R63C or R63D from the final hit through the capsule spawn. Capture the
  palette and object-pass progress to verify the fade steps and held updates.

## Native recordings still needed

Use the USA disc in BizHawk 2.11. Keep the starting state and input together
with the recorded rows or audio and pictures needed by the check.

| Gap | Start and route | Evidence to retain |
| --- | --- | --- |
| Campaign endings | Start before the final fight on the campaign route in R83C with all seven Time Stones or all seven good futures secured by the clear. Repeat in R83D with neither set complete. Win both fights by controller input and let each ending finish. | Final stage exit, saved progress flags, ending selection, full video and audio, credits, and return to the title. `GameDone` selects the ending (CD Initial Program/IPX.asm:301-326). |
| Full campaign | Cold boot with empty backup RAM, select a new game, and play from R11A through the R83 clear. Use ordinary act exits and include a time warp and a giant-ring special-stage return. | One continuous movie with each stage entry and exit, the period and special-stage handoffs, lives and progress carried between acts, and the last save and ending. |
| Natural Special Stage 8 clear | Cold boot to the title, enter Special Stage 8 through Sound Test, and hit all six UFOs by controller input before time runs out. Let the credits play. | UFO count reaching zero, success state, fade, `SPEEND.MMD` credits with audio, and return to the title (`SoundTest_SpecStg8`, CD Initial Program/IPX.asm:1065-1079). |
| Remaining boss coverage | In R43C and R43D, use the recorded defeat and capsule route where it covers the interval. For missing coverage, start before the chase and fight through the exit. In R83C and R83D, start before the arena approach and play through the collapse floor, each hit and the final defeat. | Player and camera rows, boss and child slots, hit and phase transitions, escape or defeat, capsule release where present, and the next program. |
| R81B, R81C and R82A act ends | Cold boot into each stage via stage select and extend its recorded route past the signpost. Wait through the results and enter the next stage. | Signpost trigger, ring and time tallies, results exit, and first playable row of the next stage. |
| R41A swing and switch elevator | Start R41A from stage select. Take the route to the `$29` swing at `($0480,$0180)`. Board the seat, press its switch, ride, and dismount. For the `$30` elevator at `($09E0,$0540)`, land on its pressure switch, ride up, then release it and ride the side platform. See `StageObjects` (r4/stage_object_maps_1.asm:38-41, 93-94). | Player support and position, swing phase and switch state, plus the elevator column and side platform positions on press, travel and release. |

## Cartridge donor hosting (2026-11-16)

`TestCdDonorHosting` boots R11A on the USA disc with six cartridge combinations:
S1 Sonic, S2 Sonic/Tails and S3K Sonic/Tails/Knuckles. It checks the bound character,
semantic spring animation, host movement ownership, ordinary rightward movement
past entry and S3K shield grants. Unsupported donor characters and disc donation
have explicit refusal checks. This is bounded host/character coverage; it does
not certify full donated routes, mini/3D drawings, special stages, bosses,
time travel, other periods, teams or viewport combinations. The native matrix
above still describes plain Sonic only. See the [runtime validation record](../2026-11-16-sonic-cd-mod-api.md).
