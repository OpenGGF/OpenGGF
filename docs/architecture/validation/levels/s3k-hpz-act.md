# S3K Hidden Palace coverage matrix

Game / canonical zone / act: S3K `S3K_HIDDEN_PALACE`, engine zone `$16` act index 1,
ROM `Current_zone_and_act = $1601`, SKL object set.
Character routes: Sonic/Tails (lower floor → `$B40` teleporter → upper corridor →
Knuckles fight → altar → floor collapse → altar teleporter → SSZ act 1 `$A00`) and
Knuckles (start `$10,$2EC` → `$B40` upper teleporter forced to subtype `$4A` → SSZ act 2
`$A01`). Owning plan: [HPZ bring-up](../../plans/2026-09-16-hpz-bring-up.md).
Status: in progress. Nothing below certifies the act.

Raw registry slots and transitions: `$1600` LRZ boss (separate slot), `$1601` this act,
`$1701` Super Emerald sanctuary ([separate partial matrix](s3k-hpz-sanctuary.md) under `S3K_SPECIAL_STAGE_ARENA`).
Incoming: implemented LRZ boss exit → `$1601`; Knuckles bypasses the boss slot
and leaves LRZ Act2 directly. Current cold LRZ Sonic+Tails at 320px/Sonic+Tails at 800px/Tails at 320px/Knuckles at 320px
checks reach released HPZ; direct HPZ→SSZ route checks remain separate. Data-select
slot 10 is `LevelList_DA6E`. Outgoing: `$A00` (Sonic/Tails), `$A01` (Knuckles).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as SOZ): supported presets 320/352/400/528/800; the recorded legacy
component tests below still use 320/400/512/640/800 and do not fill the current
preset cross-product. Supported character/donor pairs
off×{Sonic,Tails,Knuckles}, S1×Sonic, S2×{Sonic,Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1601` resources, bounds, object set, title card | Registry/sprite/screen-event tables, LevelSizes `$1880`×`$B20`; `Obj_TitleCardName`/`Obj_TitleCardAct` overrides | native | `TestSonic3kLevelLoading#hiddenPalaceActLoadsRom1601BoundsAndObjectSet`, `TestSonic3kNonlinearHpzProfile`, `TestSonic3kTitleCardSublevelMappings` | implemented | pass | Title card rendering not captured |
| ENTRY: character start and ScreenInit limits | Knuckles start `$10,$2EC`, Sonic/Tails `$30,$AEC`; `HPZ_ScreenInit` | 30 width × character/donor rows + 4 team rows | `TestS3kHpzCompatibilityMatrix#coldEntryAppliesCharacterStartLimitsAndPaletteControl` | implemented | pass, 34 cases (uncommitted run on `4a93aebf0` + test) | Sonic/Tails upper-route `$AA0` left limit only covered at 320 (`TestS3kHpzActEventsHeadless`) |
| EVENT: palette control at camera X `$460`; Sonic/Tails upper-route `$AA0` left limit | `Obj_HPZPaletteControl`; `HPZ_ScreenInit` | native crossing; 34 width/character/donor/team rows from the upper corridor | `TestS3kHpzActEventsHeadless#paletteControlSwitchesToPalHpzWhenTheCameraCrosses460`, `TestS3kHpzCompatibilityMatrix#upperCorridorEntrySelectsPalHpzAndCharacterCameraLimit` | implemented | pass, 1 + 34 | Native switch timing pending probe |
| EVENT: `Events_fg_4` collapse chunks | `HPZ_ScreenEvent` row 7 cols `$30/$31` = `$61` | native, synthetic trigger | `TestS3kHpzActEventsHeadless#foregroundCollapseWritesChunk61IntoRowSeven` | implemented | pass | Production trigger belongs to the Knuckles fight lane |
| PRESENT: AnPal_HPZ / AniPLC_HPZ | ROM tables entries 45/47 | native | `TestHpzZoneRuntimeStatePaletteCycle`, `TestS3kHpzPatternAnimation`, `TestS3kHpzActEventsHeadless#anPalHpz...` | implemented | pass | Native pixel comparison open |
| PRESENT: `$EC0` background seam redraw | `HPZ_BackgroundEvent` state machine | native | Native probe `probe-seam` (redraw routine 4 for 7 frames, no visible change) + engine capture `raw-08-bg-seam-ec0` | not-applicable (redraw machine rejected: no visible native effect) | native evidence recorded | Revisit only with a boundary that exposes the redraw |
| OBJECT: teleporter transport | `Obj_SSZHPZTeleporter` + `Obj_TeleporterBeam` | 34 rows | `TestS3kHpzTeleporterHeadless`, `TestS3kHpzCompatibilityMatrix#lowerTeleporterTransportReplaysAtChargeAndRise`; native `probe-teleporter` | implemented; native behaviour matched (charge interval, 74 rise steps, 24-frame settle curve, light cadence) | pass, 1 + 34 cases | Upper pad subtype 0 intangibility during transport not asserted directly; beam pixels not compared |
| REWIND: entry, upper-corridor entry, teleporter charge, rise, settle release, beam deletion | Registry restore equals capture; forward replay equals original | 34 rows × 6 spots | `TestS3kHpzCompatibilityMatrix` | implemented | pass | Positioned fight/theft/ending graph and dust replay are bound below; their snapshot canonicalization limits remain explicit. Joined incoming/load products and current configuration breadth remain open |
| ROUTE (Knuckles cold): start `$10,$2EC` → `$B40` pad → `$A01` | Knuckles complete-run BK2 input from movie frame 411496 (segment `hpz22` row 0); ROM loads SSZ act 2 at row 859 | native 320, Knuckles | `TestS3kHpzColdRoutes#knucklesRecordedInputsLeaveForSkySanctuaryActTwo` | implemented | pass (exit condition within 60 frames of row `$35C`) | Exact exit-frame comparison open |
| ROUTE (Knuckles): upper teleporter → `$A01` | `loc_45B94` | 5 widths (Knuckles has no donor support per `LaunchProfile`) | `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`; demo `06-hpz-knuckles-teleporter-exit-to-ssz2.mp4` | implemented | pass, 5 | Fresh direct-HPZ Knuckles cold route is covered above; exact exit-frame and other route products remain open. Arrival/crane replay compares player/camera/SSZ state, not the entire registry |
| ROUTE (Sonic + Tails cold): `$1601` entry → run-in → lower teleporter → fight → theft → collapse → altar teleporter → `$A00` | Complete-run BK2 input from 441758; native `probe-route/fight/altar/ending/anim`; declared native lag frame 447347 | native 320, Sonic + Tails | `TestS3kHpzColdRoutes#recordedInputsMatchNativeCheckpointsThroughTheEndingToSkySanctuary` (18 position + 5 animation checkpoints), `#recordedInputsReachTheKnucklesFightThroughTheLowerTeleporter` | implemented; native behaviour matched (camera and both players every frame 441761-448821 except camera X sub-pixel) | pass | Camera X trails for 2 load frames and by 1 px from 446448 (history from earlier acts); the later ship palette-latch correction is recorded in the [HPZ plan](../../plans/2026-09-16-hpz-bring-up.md); whole-scene parity remains open |
| BOSS: Knuckles fight, emerald theft, collapse | `CutsceneKnux_HPZ`; registry subtype `$28` → `CutsceneKnucklesHpzInstance` | positioned Sonic/Sonic+Tails; separately declared native fight seed | `TestS3kHpzKnucklesFightHeadless` (five cases), `TestS3kHpzKnucklesFightNativeSequence` (two cases) | implemented with native child graph and 47 routines | Existing HPZ selections record passing implementation/replay evidence; see the plan and current binding note below, not a new `7d0` execution | Positioned/seeded fixtures are not cold LRZ→HPZ campaign evidence; full configuration/native conformance remains open |
| ROUTE (Sonic/Tails): altar teleporter → `$A00` | `loc_45AD6`, `loc_45BF4`; `HpzTeleporterRouteHelperObjectInstance` and semantic full-save exit | positioned and fresh direct HPZ entry | `TestS3kHpzKnucklesFightHeadless#theftCollapseAndTeleporterEndingRunToSkySanctuary`, `#sonicAndTailsEndingMatchesTheRecordedTimers`; existing `TestS3kHpzColdRoutes` | implemented | Recorded HPZ fight/exit and direct-route qualification | Not one joined cold LRZ→HPZ→SSZ route; disk persistence and remaining configuration/native presentation breadth are separate |
| OBJECT: placed Master/Super Emeralds in `$1601` | `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` have no zone branch; states 1 and 2 are selectable (`loc_907A8`) | unit; native all-Super save | `TestHpzSanctuaryObjects#pedestalStatesUseRomCentrePositionsAndFourStateBehavior`; native `probe-altar` vs `raw-22-altar-all-super` | implemented (state-1 selectability corrected) | pass; visual match by inspection | Production pedestal special-stage entry from `$1601` not route-tested |
| LIFE: checkpoint `$34` sub 2 at `$CF0,$3E8`, death/reload | StarPost; `HPZ_ScreenInit` Knuckles `$AA0` limit | 25 Sonic/Tails width × donor rows; 5 Knuckles rows | `TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost` | implemented | pass, 30 cases | Knuckles cannot reach this post (camera max X `$AA0`), asserted instead; rewind at activation not replayed here |
| LOAD: LRZ3 → `$1601` incoming; Knuckles LRZ Act2 bypass | LRZ boss/end-level and direct character handoff owners | Sonic+Tails at 320px/Sonic+Tails at 800px/Tails at 320px/Knuckles at 320px | `TestLrzBossColdRouteCapture`, `TestLrzWideBossColdRouteCapture`, `TestLrzTailsColdRouteCapture`, `TestLrzKnucklesColdRouteCapture` | implemented | Current cold arrival at released HPZ: Sonic+Tails at 320px/62 windows, Sonic+Tails at 800px/71, Tails at 320px/62, Knuckles at 320px/89; owning LRZ matrices retain exact source/commands | Incoming checks stop at released HPZ; direct HPZ→SSZ rows above are independent |
| ORACLE: route timing | Sonic+Tails complete-run `hpz22_2` rows `$1E46+` | — | `TestS3kSonicTailsHpz222SegmentTraceReplay` (expected red) | — | Historical 1902 errors, first frame 0 `camera_y`, on `84c9e38d8`; that replay never left the LRZ3 prefix | Current strict trace requalification remains open; implemented LRZ incoming is no longer the missing-runtime premise. Preserve the dated 2026-09-17 trace frontier |

## Current boss, exit and incoming bindings — 2026-10-13

The current table replaces stale “fight lane missing” and “LRZ has no events”
claims; it does not rerun those suites or certify the act. Historical HPZ port
`020611217`/sidecars `1cb447aab` record 119 focused cases and 669 guards; native-
sequence selection `a69c34437` records 273 cases; merged `8d14d2591`/`d507bcf22`
records 357 HPZ/results/required-S3K cases. The [owning plan](../../plans/2026-09-16-hpz-bring-up.md)
retains those commands and later ship palette-latch evidence.

The five positioned fight cases cover slot 45/camera admission, eight-hit defeat,
fight replay, theft/collapse/altar exit and Sonic+Tails ending timers. Two native-
sequence cases use declared fight state and test its routine/hit/flag timeline
and dust replay. Their registry comparisons canonicalize dynamic ordering and
exclude `LevelSnapshot.epochAtCapture`, `ObjectManager.bucketsDirty` and
`peakSlotCount`; this is not unfiltered whole-world equality.

Current ordinary LRZ cold routes qualify released HPZ arrival: Sonic+Tails at 320px reaches
it in 53,047 inputs with 62 windows; Sonic+Tails at 800px in 50,659 with 71; solo Tails at 320px in 62,524
with 62; Knuckles at 320px in 49,526 with 89. [LRZ Act2](s3k-lrz-act2.md) and the
[boss-act matrix](s3k-lrz-boss.md) record their current source and exact checks.
These endpoints are not joined to the fresh direct-HPZ→SSZ fixtures above.

## Execution evidence

Worktree `.worktrees/ai-hpz-bring-up`, all ROMs by absolute path, `maven_queue.py -Dmse=off`.
`TestS3kHpzCompatibilityMatrix`: 68 tests (34 entry + 34 teleporter), 0 failures, 0 errors,
0 skips, 6.2 s. Changing the teleporter end assertion to `< $100` produced 34 failures at
that assertion (proves the teleporter rows execute to the end); reverted before commit.

`TestS3kHpzLifecycleProduction` (`315a483db` + test): 31 tests, 0 failures, 0 skips. The
first run failed only the five Knuckles post rows (checkpoint index -1): his `$AA0` camera
boundary keeps him away from the post, which the rows now assert.

Breadth follow-up: `TestS3kHpzCompatibilityMatrix` 102 tests and `TestS3kHpzLifecycleProduction`
35 tests, 0 failures, 0 skips. Title-card set (`-Dtest=TestSonic3kTitleCardSublevelMappings,*TitleCard*`):
19 classes, 81 tests, 0 failures, 0 skips.

## Full SaveGame consumer boundary — 2026-10-08

Both existing HPZ full SaveGame exit gates (sonic3k.asm:91524/91718) now dispatch through the semantic helper; camera/player and ending-countdown gates are unchanged.

`828bc94d8` clears the native32-bit collected-ring mask at exactly seven existing
full-SaveGame gates; existing game-state rewind owns the mask. Focused138 cases
pass without failures/errors/skips, including direct Results tally/helper and
mask-restoration checks. The other six non-tally live routes, successful disk
persistence, live SK-alone, and this matrix's remaining route/rewind/breadth
products are not newly certified. Generic persistence and special-stage/lives/
death/reload/seamless semantics remain distinct. See the [lane audit](../../audits/2026-10-07-s3k-parity-gap-verification.md)
for native ordering and scope, and [combined qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#final-composed-canonical-replay-2026-10-08)
for exact commands/frontiers. [Actual-main qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-delivery-qualification)
retains 27 concrete inherited ordinary failures and 62 literal skips with zero
errors; all 672 fresh guards pass. This preserves the route/breadth limits above.
