# Sonic CD stages: route matrix (USA)

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
| R42A | 4,286 / 6,411 | pending | green | Water: at row 4286 native is underwater and the engine is not (x, speeds, camera differ). The route does not reach its act end |
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
| R82A | 4,577 / 5,697 | none | green | Route stops short of the goal: at row 4577 the player loses `$600` of x speed at a walker platform's edge (native stands on object `$26`) |
| R82B, R82C, R82D | none | none | green | |
| R83C, R83D | approach only | none | green | Approach green to row 1,762 of R83C; fight and endings run in the engine with test-driven hits, not compared; no native ending recording |

Totals at `6c5c0e2764` and `45463c80bd`: routes 146,390 of 149,633 rows (97.8%; 26 of 28 to
their end); fixtures 56,879 of 57,049 rows (68 of 70). The open routes are
R42A (water) and R82A (an unhosted object); the open fixtures are R11C and
R51B (objects). The totals count the diverging row itself as compared, so each
open stage is counted one row above its first divergence; the cells above
give the first diverging row. In the act-end column `none` means the route has
no end-of-act assertion: it is not in `TestSonicCdActEndNative`'s set, either
because it does not reach the goal or because no assertion was written. Every
route in that set is `asserted` except R42A, which is `pending` until it
reaches its end window. The per-stage tables behind these numbers are local
measurement output, not committed.

## Standard obligations

| Obligation | State | Gap |
| --- | --- | --- |
| ENTRY | All 70 stages load from the disc (`TestCdStageLoaderAllPayloads`); three fixtures enter through the retail title and stage-select route (`TestCdRetailRouteReplay`); the master title lists Sonic CD and `TestSonicCdPlayerLaunch` starts a new game from it and equals native on 806 compared rows of R11A | Intro-skip and alternate entries not assessed; the attract demos are not hosted |
| OBJECT | Per-zone object tests and slot-exact appearance windows; see the lanes' per-object native checks | Many objects have no native picture; layout entries without a saved-state bit respawn while live (R41C); ring collection order (R51A) |
| EVENT, CAMERA | Level events, boss arenas and scroll locks hosted; routes cover them where they pass | Wide-view triggers unchecked |
| BOSS | Seven bosses; native fights for six, as above | Tidal Tempest defeat onwards and the Metallic Madness fight have no native comparison |
| LIFE | Deaths, game over and checkpoints run through the program flow (`TestCdGameFlowNative`, six native runs set up with RAM pokes); the idle timer and give-up equal native on an 11,490-row movie; saves persist across runs | Checkpoint respawn after time travel unverified; the RAM DATA screen is not hosted |
| LOAD | Act clear to the next stage, warp to another period, giant ring to the special stage and back; state a stage change leaves behind is cleared as native does (`TestSonicCdStageChangeIsolation`); stage-load and video-read timing in normal play is estimated from emulator measurements (`gameplay.loadTimeSimulation`) | Carry into a native warp destination unverified; the checkpoint and warp entries are single samples, and 12 stages' checkpoint and warp keys may name the wrong queued list ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |
| REWIND | Boss rewind tests for six zones, rings, zone signals, the Stardust Speedway lamp, the Collision Chaos drain | Spot coverage per the rewind contract not assessed; cross-load rewind not assessed |
| PRESENT | Zone pictures per the presentation lanes (most early and end shots equal native); `TestCdTitlePicture` | Uncertified shots listed in each zone test; widths other than 320 draw but have no native reference |
| ROUTE | 28 full-act routes as above | Act 1 Bad Future and act 2 Past and futures: fixtures only; no complete-run recording |
| ORACLE | Constants and branches cite `docs/scddisasm`; trace rows comparison-only | Lag-frame CPU timing not modelled ([known discrepancy](../../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)) |

Breadth: Sonic CD has one character and no teams; it is not a donor, and
donation into it is not assessed. Widths 400, 528 and 800 draw full pictures in
the capture tools but have no native reference; the other presets are unchecked.
