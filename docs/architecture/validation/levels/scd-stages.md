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
| R11C | **green** 5,790 | asserted | 677 / 829, object | Route green, fixture not: different inputs; native stands on an object (`$03`) at the fixture's row 677 |
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
| R42A | 4,404 / 6,411 | pending | green | The first two break-pole grips and releases match. At row 4404 native Sonic takes damage beside the unhosted `$37` spike ball |
| R42B, R42C, R42D | none | none | green | |
| R43C, R43D | boss movies | partial | green | Chase equal in both futures; fight compared to row 4887 of R43D; defeat, escape and capsule recorded, not compared |
| R51A | **green** 4,756 | asserted | green | |
| R51B | **green** 5,494 | asserted | 549 / 569, object | Fixture stops on an unhosted object (`$03`, `$10` nearby) |
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
their end); fixtures 56,879 of 57,049 rows (68 of 70). The later focused
Metallic Madness rerun closed R82A. R42A (underwater hurt) remains open, as do the
short fixtures R11C and R51B. The historical totals count the diverging row as
compared. The cells above give the first diverging row. In the act-end column
`none` means the route has no end-of-act assertion: it is not in
`TestSonicCdActEndNative`'s set, either
because it does not reach the goal or because no assertion was written. Every
route in that set is `asserted` except R42A, which is `pending` until it
reaches its end window. The per-stage tables behind these numbers are local
measurement output, not committed.

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

The break-pole follow-up matches R42A through row 4403, including the grips at
4286 and 4329 and the releases at 4301 and 4384. Row 4404 is the new first
divergence. Native takes underwater hurt and spills rings near the `$37` spike
ball at `($1690,$0750)`. R41A, R41B and R41C still match to their recorded
ends. `TestSonicCdPlayerMovies` pins the matched R42A prefix. `TestTtzObjects`
checks either approach, both climb limits, the two timers, input-edge release,
death and replacement cleanup, unload, ownership through rewind recreation, and
the two disc-backed mapping frames. The third native grip at row 4666 is beyond
the matched prefix. See the [trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-tidal-tempest-break-pole).

In R82A, routing the right twin-walker platform's top-solid check to that
platform fixed the horizontal speed loss at row 4577. The next stop at row
5029 came from an invisible block cutting short Sonic's upward movement.
The native routine lets him pass through the underside. With both fixes,
R82A matches every one of its 5,697 recorded rows. The same selection kept
R81A, R81B and R81C green. The source notes and commands are in the
[trace frontier log](../../../status/trace-frontier-log.md#2026-11-18--sonic-cd-twin-walker-top-solid-receiver).

## Standard obligations

| Obligation | State | Gap |
| --- | --- | --- |
| ENTRY | All 70 stages load from the disc (`TestCdStageLoaderAllPayloads`); three fixtures enter through the retail title and stage-select route (`TestCdRetailRouteReplay`); the master title lists Sonic CD and `TestSonicCdPlayerLaunch` starts a new game from it and equals native on 806 compared rows of R11A | Intro-skip and alternate entries not assessed; the attract demos are not hosted |
| OBJECT | Per-zone object tests and slot-exact appearance windows; see the lanes' per-object native checks | Many objects have no native picture; layout entries without a saved-state bit respawn while live (R41C); ring collection order (R51A) |
| EVENT, CAMERA | Level events, boss arenas and scroll locks hosted; routes cover them where they pass | Wide-view triggers unchecked |
| BOSS | Seven bosses; native fights for six, as above | Tidal Tempest defeat onwards and the Metallic Madness fight have no native comparison |
| LIFE | Deaths, game over and checkpoints run through the program flow (`TestCdGameFlowNative`, six native runs set up with RAM pokes); the idle timer and give-up equal native on an 11,490-row movie; saves persist across runs, and RAM DATA resets the Sonic CD record through the title menu (`TestCdDurableSaves`) | Checkpoint respawn after time travel unverified |
| LOAD | Act clear to the next stage, warp to another period, giant ring to the special stage and back; state a stage change leaves behind is cleared as native does (`TestSonicCdStageChangeIsolation`); stage-load and video-read timing in normal play is estimated from emulator measurements (`gameplay.loadTimeSimulation`) | Carry into a native warp destination unverified; the checkpoint and warp entries are single samples, and 12 stages' checkpoint and warp keys may name the wrong queued list ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |
| REWIND | Boss rewind tests for six zones, rings, zone signals, the Stardust Speedway lamp, the Collision Chaos drain | Spot coverage per the rewind contract not assessed; cross-load rewind not assessed |
| PRESENT | Zone pictures per the presentation lanes (most early and end shots equal native); `TestCdTitlePicture` | Uncertified shots listed in each zone test; widths other than 320 draw but have no native reference |
| ROUTE | 28 full-act routes as above | Act 1 Bad Future and act 2 Past and futures: fixtures only; no complete-run recording |
| ORACLE | Constants and branches cite `docs/scddisasm`; trace rows comparison-only | Lag-frame CPU timing not modelled ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |

Breadth: Sonic CD has one character and no teams; it is not a donor, and
donation into it is not assessed. Widths 400, 528 and 800 draw full pictures in
the capture tools but have no native reference; the other presets are unchecked.

## Cartridge donor hosting (2026-11-16)

`TestCdDonorHosting` boots R11A on the USA disc with six cartridge combinations:
S1 Sonic, S2 Sonic/Tails and S3K Sonic/Tails/Knuckles. It checks the bound character,
semantic spring animation, host movement ownership, ordinary rightward movement
past entry and S3K shield grants. Unsupported donor characters and disc donation
have explicit refusal checks. This is bounded host/character coverage; it does
not certify full donated routes, mini/3D drawings, special stages, bosses,
time travel, other periods, teams or viewport combinations. The native matrix
above still describes plain Sonic only. See the [runtime validation record](../2026-11-16-sonic-cd-mod-api.md).
