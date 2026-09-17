# S3K Hidden Palace coverage matrix

Game / canonical zone / act: S3K `S3K_HIDDEN_PALACE`, engine zone `$16` act index 1,
ROM `Current_zone_and_act = $1601`, SKL object set.
Character routes: Sonic/Tails (lower floor → `$B40` teleporter → upper corridor →
Knuckles fight → altar → floor collapse → altar teleporter → SSZ act 1 `$A00`) and
Knuckles (start `$10,$2EC` → `$B40` upper teleporter forced to subtype `$4A` → SSZ act 2
`$A01`). Owning plan: [HPZ bring-up](../../plans/2026-09-16-hpz-bring-up.md).
Status: in progress. Nothing below certifies the act.

Raw registry slots and transitions: `$1600` LRZ boss (separate slot), `$1601` this act,
`$1701` Super Emerald sanctuary (separate matrix pending under `S3K_SPECIAL_STAGE_ARENA`).
Incoming: LRZ3 → `$1601` (blocked: LRZ has no events yet), data-select slot 10
(`LevelList_DA6E`). Outgoing: `$A00` (Sonic/Tails), `$A01` (Knuckles).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as SOZ): widths 320/400/512/640/800; supported character/donor pairs
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
| REWIND: entry, upper-corridor entry, teleporter charge, rise, settle release, beam deletion | Registry restore equals capture; forward replay equals original | 34 rows × 6 spots | `TestS3kHpzCompatibilityMatrix` | implemented | pass | Fight/ending and load-boundary spots pending |
| ROUTE (Knuckles cold): start `$10,$2EC` → `$B40` pad → `$A01` | Knuckles complete-run BK2 input from movie frame 411496 (segment `hpz22` row 0); ROM loads SSZ act 2 at row 859 | native 320, Knuckles | `TestS3kHpzColdRoutes#knucklesRecordedInputsLeaveForSkySanctuaryActTwo` | implemented | pass (exit condition within 60 frames of row `$35C`) | Exact exit-frame comparison open |
| ROUTE (Knuckles): upper teleporter → `$A01` | `loc_45B94` | 5 widths (Knuckles has no donor support per `LaunchProfile`) | `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`; demo `06-hpz-knuckles-teleporter-exit-to-ssz2.mp4` | implemented | pass, 5 | Cold route from the Knuckles start open |
| ROUTE (Sonic + Tails cold): `$1601` entry → run-in → lower teleporter → fight → theft → collapse → altar teleporter → `$A00` | Complete-run BK2 input from 441758; native `probe-route/fight/altar/ending/anim`; declared native lag frame 447347 | native 320, Sonic + Tails | `TestS3kHpzColdRoutes#recordedInputsMatchNativeCheckpointsThroughTheEndingToSkySanctuary` (18 position + 5 animation checkpoints), `#recordedInputsReachTheKnucklesFightThroughTheLowerTeleporter` | implemented; native behaviour matched (camera and both players every frame 441761-448821 except camera X sub-pixel) | pass | Camera X trails for 2 load frames and by 1 px from 446448 (history from earlier acts); ship hit flash one frame out of phase |
| BOSS: Knuckles fight, emerald theft, collapse | `CutsceneKnux_HPZ` | — | fight lane `feature/ai-hpz-knuckles-fight` | missing (in progress) | unrun | — |
| ROUTE (Sonic/Tails): altar teleporter → `$A00` | `loc_45AD6`, `loc_45BF4` | — | fight lane | missing (in progress) | unrun | — |
| OBJECT: placed Master/Super Emeralds in `$1601` | `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` have no zone branch; states 1 and 2 are selectable (`loc_907A8`) | unit; native all-Super save | `TestHpzSanctuaryObjects#pedestalStatesUseRomCentrePositionsAndFourStateBehavior`; native `probe-altar` vs `raw-22-altar-all-super` | implemented (state-1 selectability corrected) | pass; visual match by inspection | Production pedestal special-stage entry from `$1601` not route-tested |
| LIFE: checkpoint `$34` sub 2 at `$CF0,$3E8`, death/reload | StarPost; `HPZ_ScreenInit` Knuckles `$AA0` limit | 25 Sonic/Tails width × donor rows; 5 Knuckles rows | `TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost` | implemented | pass, 30 cases | Knuckles cannot reach this post (camera max X `$AA0`), asserted instead; rewind at activation not replayed here |
| LOAD: LRZ3 → `$1601` incoming | LRZ events | — | — | missing (LRZ) | blocked | LRZ bring-up |
| ORACLE: route timing | Sonic+Tails complete-run `hpz22_2` rows `$1E46+` | — | `TestS3kSonicTailsHpz222SegmentTraceReplay` (expected red) | — | blocked: 1902 errors, first frame 0 `camera_y`; replay never leaves the LRZ3 prefix (`84c9e38d8`) | Unblocks with LRZ3 bring-up; see trace frontier log 2026-09-17 |

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
