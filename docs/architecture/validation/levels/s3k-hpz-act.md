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
| ENTRY: `$1601` resources, bounds, object set | Registry/sprite/screen-event tables, LevelSizes `$1880`×`$B20` | native | `TestSonic3kLevelLoading#hiddenPalaceActLoadsRom1601BoundsAndObjectSet`, `TestSonic3kNonlinearHpzProfile` | implemented | pass (`e3ae26530`) | Title-card presentation not asserted |
| ENTRY: character start and ScreenInit limits | Knuckles start `$10,$2EC`, Sonic/Tails `$30,$AEC`; `HPZ_ScreenInit` | 30 width × character/donor rows + 4 team rows | `TestS3kHpzCompatibilityMatrix#coldEntryAppliesCharacterStartLimitsAndPaletteControl` | implemented | pass, 34 cases (uncommitted run on `4a93aebf0` + test) | Sonic/Tails upper-route `$AA0` left limit only covered at 320 (`TestS3kHpzActEventsHeadless`) |
| EVENT: palette control at camera X `$460` | `Obj_HPZPaletteControl` | native | `TestS3kHpzActEventsHeadless#paletteControlSwitchesToPalHpzWhenTheCameraCrosses460` | implemented | pass (`362771221`) | Width sensitivity: camera X is the trigger; wide rows not run |
| EVENT: `Events_fg_4` collapse chunks | `HPZ_ScreenEvent` row 7 cols `$30/$31` = `$61` | native, synthetic trigger | `TestS3kHpzActEventsHeadless#foregroundCollapseWritesChunk61IntoRowSeven` | implemented | pass | Production trigger belongs to the Knuckles fight lane |
| PRESENT: AnPal_HPZ / AniPLC_HPZ | ROM tables entries 45/47 | native | `TestHpzZoneRuntimeStatePaletteCycle`, `TestS3kHpzPatternAnimation`, `TestS3kHpzActEventsHeadless#anPalHpz...` | implemented | pass | Native pixel comparison open |
| PRESENT: `$EC0` background seam redraw | `HPZ_BackgroundEvent` state machine | native | Visual only (`raw-08-bg-seam-ec0`) | partial (not ported; parallax origin switch only) | engine capture showed no tear | Native reference needed |
| OBJECT: teleporter transport | `Obj_SSZHPZTeleporter` + `Obj_TeleporterBeam` | 34 rows | `TestS3kHpzTeleporterHeadless`, `TestS3kHpzCompatibilityMatrix#lowerTeleporterTransportReplaysAtChargeAndRise` | implemented | pass, 1 + 34 cases | Upper pad subtype 0 intangibility during transport not asserted directly |
| REWIND: entry, teleporter charge, rise | Registry restore equals capture; forward replay equals original | 34 rows × 3 spots | `TestS3kHpzCompatibilityMatrix` | implemented | pass | Settle boundary and beam contraction/deletion spot not yet replayed |
| ROUTE (Knuckles): upper teleporter → `$A01` | `loc_45B94` | native 320 | `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`; demo `06-hpz-knuckles-teleporter-exit-to-ssz2.mp4` | implemented | pass | Width/donor breadth for the exit and a cold route from the Knuckles start open |
| BOSS: Knuckles fight, emerald theft, collapse | `CutsceneKnux_HPZ` | — | fight lane `feature/ai-hpz-knuckles-fight` | missing (in progress) | unrun | — |
| ROUTE (Sonic/Tails): altar teleporter → `$A00` | `loc_45AD6`, `loc_45BF4` | — | fight lane | missing (in progress) | unrun | — |
| OBJECT: placed Master/Super Emeralds in `$1601` | `Obj_HPZMasterEmerald`, `Obj_HPZSuperEmerald` | native | Visual only (`raw-09-altar-1601`) | partial (sanctuary implementation reused) | unrun | Verify `$1601` branches against ROM |
| LIFE: checkpoint `$34` sub 2 at `$CF0,$3E8`, death/reload | StarPost; `HPZ_ScreenInit` Knuckles `$AA0` limit | 25 Sonic/Tails width × donor rows; 5 Knuckles rows | `TestS3kHpzLifecycleProduction#touchingThePlacedStarPostThenDyingReloadsAtThePost` | implemented | pass, 30 cases | Knuckles cannot reach this post (camera max X `$AA0`), asserted instead; rewind at activation not replayed here |
| LOAD: LRZ3 → `$1601` incoming | LRZ events | — | — | missing (LRZ) | blocked | LRZ bring-up |
| ORACLE: route timing | Sonic+Tails complete-run `hpz22_2` rows `$1E46+` | — | `TestS3kSonicTailsHpz2SegmentTraceReplay` (expected red) | — | unrun | Measure after the fight lane lands |

## Execution evidence

Worktree `.worktrees/ai-hpz-bring-up`, all ROMs by absolute path, `maven_queue.py -Dmse=off`.
`TestS3kHpzCompatibilityMatrix`: 68 tests (34 entry + 34 teleporter), 0 failures, 0 errors,
0 skips, 6.2 s. Changing the teleporter end assertion to `< $100` produced 34 failures at
that assertion (proves the teleporter rows execute to the end); reverted before commit.

`TestS3kHpzLifecycleProduction` (`315a483db` + test): 31 tests, 0 failures, 0 skips. The
first run failed only the five Knuckles post rows (checkpoint index -1): his `$AA0` camera
boundary keeps him away from the post, which the rows now assert.
