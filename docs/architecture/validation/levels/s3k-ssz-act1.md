# S3K Sky Sanctuary Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_1`, engine zone `$0A` act index 0,
ROM `Current_zone_and_act = $A00`, SKL object set.
Character routes: Sonic, Sonic + Tails, Tails (`LevelSelect_CheckKnuckles` denies Knuckles except
with `Debug_cheat_flag != 0`, and no Knuckles art or route exists for act 1).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: in progress (slices 0, 1, 1b, 2, 3, 4, 5 and 6). Nothing below certifies the act.

Incoming: HPZ teleporter altar ending → `$A00` (`HpzTeleporterRouteHelperObjectInstance`), level
select, save progression. Outgoing: `StartNewLevel $B00` from the Death Egg launch (`loc_581D2`);
DEZ presentation and route belong to the DEZ campaign.

Native fixtures (SSZ is filed under `hpz`; `zone_id 10` is the ROM zone. The one-time trace
directory identity table is owned by the LRZ campaign's edit to
[trace frontier log](../../../status/trace-frontier-log.md); it is referenced, not duplicated here):
`runs/s3k-sonic-tails-complete-emeralds/hpz` (7638 rows, `bk2_frame_offset` 448920, start
`$100,$FAE`), `…/hpz_2` (4352, 460334), `…/hpz_3` (3937, 465044);
`runs/s3k-tails-full-chain-all-emeralds/hpz` (6023, 423903), `…/hpz_2` (10582, 433476);
`hpz_completerun` (18641, 396720).

Five claims are tracked separately per row: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. There is no aggregate
green label, and "implemented" alone never closes a row.

## Obligations

| Obligation + spot | Contract / oracle (ROM owner) | Config cases | Test binding | Implemented | Cold-reachable | Rewind-verified | Native matched | Visually matched | Gap / action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOAD: identity, resources, music, title card (no act number) | `Sonic3kZoneRegistry` zone 10; `ArtKosM_SSZTitleCard $15C7FA`; `Pal_SSZ1` `$32`, collision `$1E/$1F` | 320 | — | pre-existing | yes (cold load) | — | — | baseline capture `raw-00-ssz1-before` | Resource identity not yet asserted by a test |
| CENSUS: placed objects and rings | `SSZ1_Sprites $1F90EE` (213 records, 66 rows), `SSZ1_Rings $1F9616` (180 records, first `(0,0)`); `sub_1BA0C`/`loc_1BA4A`; `Load_Rings` `loc_E8BE` | ROM decode | `TestS3kSszPlacementCensus` | yes | n/a | n/a | yes (decode pinned to the ROM) | n/a | Concrete-class assertions arrive in slice 3 |
| CENSUS: wrap-seam cloud records | Two `$7D` records store Y `$103C`/`$104C`; the `& $FFF` mask puts them at `$03C`/`$04C` | ROM decode | `TestS3kSszPlacementCensus#theTwoWrapSeamCloudRecordsMaskAcrossTheSeam` | yes | n/a | n/a | yes | n/a | Their in-level behaviour is slice 3 |
| CENSUS: leading `(0,0)` ring record | `loc_E8BE` starts the scan at `max(Camera_X - 8, 1)`, so the record is always stepped over | ROM decode | `TestS3kSszPlacementCensus#ringRecordsMatchTheRomIncludingTheLeadingZeroRecord` | yes | n/a | n/a | yes | n/a | Engine window floor is `max(cameraX - 8, 0)`; gap filed in [s3k-known-bugs](../../../status/s3k-known-bugs.md). LRZ `3418eba6e` fixes it in shared `Sonic3kRingPlacement` and arrives at merge; the test asserts the ROM totals (179/0) and tolerates the sentinel only as a sentinel |
| ARRIVAL: no-starpost screen init and controller | `SSZ1_ScreenInit`, `Obj_57C1E`/`loc_57CAC`/`loc_57CD2`/`loc_57D3C`, `Obj_57D64`, `loc_57DA2` | 320 + 800; Sonic, Sonic + Tails | `TestS3kSszArrivalHeadless` | yes | yes (cold load) | not exercised | forced camera, `Camera_Y + $65`, the 8 px rise and the skipped final camera step all match; one-frame phase against `hpz` row 0 open | `01-ssz-arrival-beam-sonic-tails.mp4` | Tails-solo row and the wide rewind spot open |
| ARRIVAL: Tails helper and CPU routine | `Obj_57DCC`, `loc_13AB4` (`sub_13ECA`, `Tails_CPU_routine $A`, `object_control $83`) | Sonic + Tails, 320 | `TestS3kSszArrivalHeadless#theSidekickParksOffScreenUntilTheArrivalHelperReleasesHer` | yes | yes | not exercised | `($7F00,0)` park and in-air status match the fixture's row-0 sidekick sentinel | `01-ssz-arrival-beam-sonic-tails.mp4` | Her swing arc is not compared to native |
| BOUNDS: act-1 dynamic Y bounds and Y-wrap | `sub_575EA` `word_5778A`/`word_5779A`; wrap `-$100 … $1000` | 320 | `TestS3kSszKnucklesBridgeHeadless#theCutsceneReleasesTheBridgeAndOpensTheAct` | yes | yes (the band is asserted at the bridge release) | not exercised | band values asserted from the tables | — | The GHZ/MTZ lock branches are implemented but unreached until their bosses exist; wrap crossing is slice 4 |
| CUTSCENE: Knuckles spawner, button `$AF`, bridge `$77`, pseudo-starpost | `Obj_57E34` → `CutsceneKnux_SSZ` (11 routines `0..$14`), `loc_658F2`, `loc_65976`, `Obj_SSZCutsceneBridge` `loc_44FA2`/`loc_44FBA`/`loc_4501A` | 320 | `TestS3kSszKnucklesBridgeHeadless` | yes | yes: the bridge retracts 2 px/frame and clears `Events_bg+$05`, and a pre-set star post starts it extended with no arrival | not exercised | flag order, retract rate and the `($140,$C6C)` checkpoint match the routines; no native probe | `02a`/`02b` clips | Death Egg palette and children, and Knuckles' resting X, filed in s3k-known-bugs |
| BG: plain sky framing and the `$1800` latch | `sub_579F0` `loc_57A12`/`loc_57A30`/`loc_57A4C` | 320 | `TestS3kSszScrollBands` | yes | yes (the arrival opens in plain sky) | n/a (derived per frame) | offsets and the latch's re-rounding asserted from the routine | `04a-ssz-sky-and-clouds.mp4` | The engine reads `Camera_X/Y_pos`, not the `_copy` words; identical until the launch's screen shake |
| BG: cloud band, per-band deformation and drift | `sub_57A60`, `SSZ1_BGDeformArray`, `ApplyDeformation` | 320 + 800 | `TestS3kSszScrollBands`, `TestS3kSszBackgroundLayout` | yes | yes (the arrival rise crosses `$F00` into the band) | n/a | the thirty-word fan, the halved wrapped background Y, the seven visible bands at camera `($800,$E00)` and the `$500`/frame accumulator all asserted from `sub_57A60`; the background layout itself is decoded from the ROM and compared with the engine's layer column for column | `04a`/`04b` clips show the arrival band only, which the ROM's layout makes flat sky; the cloud band is not visually verified and currently renders wrong | **Cloud mode is wrong.** Plain mode's flat sky at the arrival is correct (layout rows 0-2 are one repeated chunk and `Camera_Y $F49 + $160` wraps to row 1), but `loc_5786A`/`loc_57946`/`loc_5799A` pin the cloud plane to layout X `$1C00` (columns 56-59, `moveq #$20,d6`) and the engine reads camera-derived columns, so the whole ascent renders sky — s3k-known-bugs #41 |
| BG: mode transition machine | `SSZ1_BackgroundEvent` routines 0/4/8/`$C`, `Events_bg+$0C`/`+$0E` | 320 | `TestS3kSszScrollBands` | yes | yes | yes (the whole state is in `SszZoneRuntimeState`) | routine order and the frozen framing asserted; the ROM's multi-frame `Draw_PlaneVertBottomUp` completes in one engine frame | `04a` | Recorded difference: the cloud bands appear one frame after the ROM starts filling them |
| BG: roaming clouds `loc_57BB2` and `sub_5758A` | five `word_58758` rows, `Gradual_SwingOffset($1C00,$80)`, the `$1FF`/`$FF` screen masks | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes (capture/restore + forward replay) | row count, drift, per-cloud RNG phase and the screen periods asserted from the routine | `04a`/`04b` | The `$1FF` period is screen-space, so a wide viewport shows the wrap seam inside the visible area |
| BG: cloud oscillator `_unkEE9C` and the ten solid clouds | `loc_57B6A`/`loc_57B76`, `loc_57B8E`, `word_5853E`, `SolidObjCheckSloped2` | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes | ten rows decoded from the ROM inside the test; `y_pos = y_vel - _unkEE9C` asserted for 240 frames | `04a`/`04b` | Riding one is not yet exercised by a route; eight of the ten rows over-read their own slope table into the next one, as the ROM does |
| ANIM: AniPLC (6 scripts), and none in act 2 | `AniPLC_SSZ` `$28AA4`; `Offs_AniFunc` entries 40-43 (`AnimateTiles_DoAniPLC` / `AnimateTiles_NULL`) | 320 | `TestS3kSszPatternAnimation` | yes | yes (runs from the cold load) | n/a (the animator's counters are snapshotted by `PatternAnimatorSnapshot`) | all six scripts' duration, destination tile, frame count and tiles per frame asserted from the declarations, including the third script's three frames | `04a` | No per-tile pixel comparison against native |
| OBJECT: `$79` SSZ pads (all ten act-1 placements) | `Obj_SSZHPZTeleporter` init, `loc_455BA`, `loc_455CC`-`loc_45790`, `loc_457BE`, `loc_4581C`, `sub_45866` | 320 | `TestS3kSszTeleporterPads` | yes | not yet: no cold route reaches a pad past the bridge | not exercised | lift `(subtype & $3F) * $10`, the gated-pad sink and 4-frame rise, the launch bounds/`Scroll_lock`/`Events_bg+$05` writes and `byte_466E8` all asserted from the routines; the gated-pad cases use a declared seeded `st (Events_bg+$00)` because slice 5's boss does not exist | `08-ssz-teleporter-pad-launch.mp4`, frame 200 | Two cases seed a boss-defeat flag; the `($1A40,$670)` spawner draws but allocates no boss (s3k-known-bugs #42) |
| OBJECT: `$7F` floating platform (8 placements) | `Obj_SSZFloatingPlatform` / `loc_44AA0`; `SolidObjectTop` `d1 $2B`, `d2`/`d3` `$11` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | the 4-pixel dip and its one-pixel-per-frame ramp asserted from `loc_44AA0` | `06-ssz-floating-platform-dips.mp4`, frame 150 | Wide-viewport and donor rows open |
| OBJECT: `$7E` collapsing column + debris (25 placements) | `Obj_SSZCollapsingColumn` / `loc_44B30`/`loc_44B90`, debris `loc_44BCC`/`loc_44BF8`, `word_46618` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | eight pieces from `word_46618` decoded in the test, the `routine(a0)` report count and the `$7FFF` park asserted from the routines | `07-ssz-column-breaks-into-eight.mp4`, frame 90 | The `Random_Number` bob phase is drawn per column, so the column order of a load moves the RNG stream; not compared to native |
| OBJECT: `$7C` collapsing bridge + shared debris (8 placements) | `Obj_SSZCollapsingBridge` / `loc_44C76`/`loc_44C9C`/`loc_44D22`, debris `loc_45052` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | subtype bit 7 (the only bit read), the four pieces with their `$102` frame word and 6/12/18/24 delays, and the 8-px-per-six-frames shrink to the `$7FFF` park, all asserted from the routine | `07-ssz-column-breaks-into-eight.mp4` shows the `$7E` sibling; `$7C`'s own clip is owed | Wide-viewport and donor rows open |
| OBJECT: `$74 $75 $76 $7A $7B $7D`, EggRobo `$A0` | per-object inits | — | slice 3 (remaining) | open (placeholders) | open | open | open | open | 113 of the 154 slice-3 placements remain |
| LIFE: death, starposts `$34:$02/$03/$04`, respawn, Y seam | `LevelSetup` (sonic3k.asm:102185) `clr.l Events_bg+$00/$04/$08/$0C`; `Level:` (7623) `clearRAM _unkFA80,$80`; `SSZ1_ScreenInit` starpost path; `sub_575EA` MTZ pre-lock | 320 | `TestS3kSszLifecycleProduction` | yes | declared restart, not a route: `SSZ1_ScreenInit` drags a leader with no star post back to the arrival column, so `$34:$03` is written the way the ROM writes it | yes: the reload resets the timeline instead of continuing the pre-death history, and builds a new `SszZoneRuntimeState` (`assertSame` fails) | yes: `hpz_2` is itself a `$34:$03` restart (metadata `start_x 0x14C0 / start_y 0x00E8`) and its row 0's player `($14C0,$EC)` and camera `($1420,$8C)` are matched exactly | `16-ssz-death-restarts-at-the-star-post.mp4`, frames 77/150/220 | Only `$34:$03` is driven; `$34:$02`/`$34:$04` and a wide row are owed, as is a capture of a post being physically touched |
| BOSS: GHZ recreation hits, flash, scatter | `sub_7A5A0` / `sub_7A614` / `word_7A622` / `word_7A628`; `Touch_Enemy` `.checkhurtenemy` (sonic3k.asm:20922); `loc_849D8` / `Obj_FlickerMove` / `Set_IndexedVelocity` / `Obj_VelocityIndex`; `ObjDat3_7A678`; `word_7A65A` | 320 | `TestS3kSszGhzArenaHeadless#onlyTheBallHasCollisionAndItHurtsThePlayerThroughTheTouchPass`, `#oneRealHitFlashesThreePaletteWordsFromTheShippedRow`, `#theKillingHitScattersTheChainOneLinkAFrameAndTakesTheBallsHitbox`, `#theEmitterFlickersEveryOtherFrameAndCarriesNoCollisionByte` | yes | all eight hits and the ball's hurt go through `ObjectTouchResponseController` with the player pinned attacking/non-attacking; no `onPlayerAttack` call anywhere | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($160,$7C0)`, 1142 rows: its aux `object_appeared` rows carry each slot's code-pointer address, which dates the whole fight. Spawn `($270,$780)`, first dispatch init + 33, chain drop at `x = $200`, `$3F + 1 = 64` fade frames, `$78 = 120` escape frames all match the port's intervals. The 184-frame defeat total matches too: it was 183 here until these rows showed the killing frame takes no decrement on the cartridge, and `defeatDeferralAppliesToThisBoss()` now models that, as `HczMinibossInstance` does. Native rings fall 55 → 45 across the window, which the port could not do before the ball got its box. The rows **corrected** the scatter: all six links convert on the killing-hit frame, not one a frame | none yet | Palette is not in this schema, so the flash is still uncompared. The flash writes line 0 colours 7/14/15, measured as already holding `word_7A628`'s first row before the fight, so the row the ROM leaves behind is invisible |
| BOSS: GHZ recreation lock / fight / defeat | `sub_575EA` `loc_57686`-`loc_576E8`; `Obj_SSZGHZBoss` `off_7A2B4` routines 0-`$A`, `sub_7A5A0`, `loc_7A3CE`-`loc_7A3F8`; children `ChildObjDat_7A684`/`_7A69E`, `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszGhzArenaHeadless` | yes | declared restart at `($200,$7C8)`: the leader settles on the arena floor at `($200,$86C)` with the camera at `($160,$7C0)` by the act's own physics, and both gates then fire. No cold route reaches the arena yet | yes: mid-swing with all six links out; nulling the links' `chainParent` restore fails five of the six | **not claimed.** Values come from the routines only; the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142 rows) is unread | `17-ssz-green-hill-recreation-ball-and-chain.mp4`, frames 330/420/600/900 (pre-review: no ball hitbox, no scatter) and `18-ssz-green-hill-ball-and-chain-hurts.mp4`, frames 150/250/330/**361**/400 — 361 is the ball on top of a standing Sonic, the frame he is killed, which the pre-review fight could not do. Filmed with `--star-post --x 0x200 --y 0x7C8`; a bare teleport cannot capture this arena, because `SSZ1_ScreenInit`'s no-star-post intro overrides `--x/--y` | The `$79:$AA` pad's post-defeat rise is implemented but not driven end to end |
| BOSS: MTZ recreation lock / spawn / entry | `sub_575EA` `loc_5770C`-`loc_5775C`; `Obj_SSZMTZBoss` init, `loc_7A712`, `off_7A728`, `loc_7A72C`, `loc_7A7C4`; `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszMtzArenaHeadless#theUpperArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles`, `#thePreLockLeftLimitIsTheGreenHillWordsToChoose`, `#theShipEntersAtSeventeenHundredAndDispatchesThirtyThreeFramesAfterItsInit`, `#theSetupOverwritesTheEggRoboPairingWord` | yes | declared restart at `($1700,$420)`: the leader settles on the upper arena floor, the lock fires with `Camera_X` at `$1660` through `nativeFramedCameraX` at both widths, and the spawn follows when the camera eases to `$380`. No cold route reaches this arena either | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($1660,$380)`, 1004 rows: the boss slot appears on native frame 6156 and takes `loc_7A71A` on 6189 — init + 33, which the test asserts as `$1F + 2`. The Mecha Sonic head and all seven orbs appear together on 6190, one frame later, which is what `loc_7ADA2`'s `movea.l a0,a1` plus its six allocations produce. The arena floor `y = $42C` and the restart position come from the same window | owed | The spawn writes world coordinates, so unlike Green Hill nothing here needs the camera offset |
| BOSS: MTZ ship motion, orbs, hits, lasers, defeat, pad `$79:$F6` | `off_7A7F0` arms 0-`$E`, `sub_7A85A`, `off_7AA60`, `sub_7AB56`, `ChildObjDat_7AB80`; `sub_7AC06` / `sub_7ACF2` / `sub_7AD6A` / `word_7AD7E`; `loc_7AD8A` routines 0-8, `sub_7AEB0`, `sub_7AF5A`, `sub_7B0C2`; `loc_7AC7A`-`loc_7ACA4`; `loc_4554E` gated pad on `Events_bg+$02` | 320 | `TestS3kSszMtzArenaHeadless#theDescentIsOnePixelAFrameAndTurnsToFaceThePlayerAtFourTwoZero`, `#thePatrolTurnsAtItsTwoLimitsAndHoversFourPixels`, `#theShipLeavesThePatrolOnItsSecondTurnAroundAndNotItsFirst`, `#theOrbRingSortsItselfFrontAndBackFromTheCosineOfItsAngle`, `#aHitLaunchesExactlyOneOrbAndSpendsOneArmCycle`, `#theHitWindowFlashesTheShippedRowAndReopensTheBoxAtTheSmallerSize`, `#theLastArmCycleStartsTheLaserPassAndFiresThreePairs`, `#theDefeatTakesTheCartridgesFrameCountAndOpensTheGatedPad` | yes | every hit goes through `ObjectTouchResponseController` with the player pinned attacking, and the tests pop the launched orbs the same way — `loc_7A98A` will not release the ship until `$30(a0)` is zero, so the fight has to be played rather than stepped | yes: mid-fight with one orb off the ring; the restore relinks all seven orbs and `$30(a0)` | **compared, comparison-only.** The same window dates the killing hit at 6651 and `loc_7AC92` at 6715 — `64 = $3F + 1` — which is why `defeatDeferralAppliesToThisBoss()` was overridden before a line of the fight was written rather than after. Native rings fall 55 → 38 across the window. The orbit, the sort, the laser cadence and the flash have **no** native comparison | owed | Gaps recorded in `s3k-known-bugs.md`: no `Child6_CreateBossExplosion`; orbs still on the ring are deleted with the ship where the cartridge leaves them orbiting a freed slot; the shared `S3K_SPECIAL_PROPERTY` arm teleports the sidekick onto every `$C0` object. The laser gap is `$1E + $10 = 46` frames, because `loc_7AA44` returns on `$33(a0)` before `sub_7AB56` is reached |
| BOSS: Mecha Sonic spawn / fight / defeat, results + save | `loc_45A84`, `loc_7B2DC`, `loc_7B308`, `loc_2DCA0` | — | slice 7 | open | open | open | open | open | Slice 7 |
| EVENT: crumble, hot-swap, Death Egg BG, debris, ramp script, `$B00` request | `SSZ1_ScreenEvent` stages 0/4/8, `Obj_57E96`, `sub_5750C`, `sub_574DC`, `loc_58192`, `loc_581D2` | — | slice 8 | open | open | open | open | open | Slice 8 |
| LOAD: `$B00` (DEZ) presentation after the request | DEZ campaign | — | — | blocked | blocked | blocked | blocked | blocked | Out of scope; record what the engine does after the request |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz{,2,3}SegmentTraceReplay`, `TestS3kTailsFullChainHpz{,2}SegmentTraceReplay` | `-Ptrace-segments` | — | — | — | — | not measured | — | Slice 10 records each frontier |

## Execution evidence

Worktree `.worktrees/ai-ssz-bring-up`, branch `feature/ai-ssz-bring-up`, base develop `035e48a58`.
All Maven through `python3 tools/testing/maven_queue.py -Dmse=off …` with
`-Ds3k.rom.path=<absolute path to the worktree>/s3k.gen` (a symlink
to the locked-on ROM, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`).

Slice 0, 2026-09-17. `-Dtest=TestS3kSszPlacementCensus`: **7 tests, 0 failures, 0 errors, 0 skips**.
The comparison was broken on purpose first (expected 214 records) and reported
`expected: <214> but was: <213>` at the same skip count, so the census is live rather than absent.
Baseline captures (no SSZ events, scroll or objects yet):
`~/Videos/OGGF/ssz-bring-up/raw-00-ssz1-before` (Sonic, 320, 360 frames; level start `(256,3072)`
= `$100,$C00` from `LevelData`, camera `(96,2976)`) and `raw-00-ssz2-before` (Knuckles, 320, 360
frames; level start `(128,32)` = `$80,$20` from `Knux_Start_Locations`). Frames 200 of each were
inspected: act 1 renders the sanctuary terrain against a flat blue sky with no cloud background;
act 2 renders the static cloud layout. Neither is a fact about final behaviour.

Slice 2, 2026-09-17. `-Dtest=TestS3kSszScrollBands` 11 tests, `-Dtest=TestS3kSszPatternAnimation`
3 and `-Dtest=TestS3kSszBackgroundClouds` 6, all 0 failures and 0 skips; the combined run with
`TestEveryObjectRewindRoundTrip` and `TestRewindHarnessCoverageRatchet` was 1158 tests, 0 failures,
0 skips. The scroll comparison was broken on purpose with four perturbed ROM constants and came
back `Tests run: 11, Failures: 5`, naming the framing offsets, `HScroll_table word 21` and the
drift accumulator. The first rewind spot in this campaign lives here and caught two real defects:
a `$500` drift advance on a re-rendered frame and sixteen load-time sky objects being unloaded as
soon as they left the camera window.

Slices 1 and 1b, 2026-09-17, commit `686824e73`. `-Dtest=TestS3kSszArrivalHeadless` 6 tests and
`-Dtest=TestS3kSszKnucklesBridgeHeadless` 3 tests, both 0 failures and 0 skips, both seen red on
real defects first. Shared checks in the same tree
(`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
`TestSonic3kDecodingUtils`, `TestS3kHpz*`, `TestS3kDdz*`, `TestEveryObjectRewindRoundTrip`,
`TestS3kSsz*`): 1391 tests, 0 failures, 0 skips. `-Pguards test -B`: 669 tests, 0 failures, 0 skips.
This is focused validation, not a suite pass. Media: `raw-01-arrival-sonic-tails`,
`raw-02-knuckles-cutscene-bridge` (first attempt, kept) and
`raw-03-knuckles-cutscene-bridge-walk`, with clips `01`, `02a` and `02b`.

Slice 3, 2026-09-17/18, commits `6523ddcc8`, `44b69c113`, `cbb66adca`, `2bdca80eb`, `ac93480de`.
All eleven act-1 placed families implemented. Focused runs on the final tree:
`-Dtest=TestS3kSszCarriersAndSprings` 11, `-Dtest=TestS3kSszEggRobo` 5,
`-Dtest=TestS3kSszPlacementCensus` 9, `-Dtest=TestS3kSszTraversalPlatforms` 7,
`-Dtest=TestS3kSszTeleporterPads` 8 — 0 failures, 0 skips each. Combined with the four mandatory
S3K classes and the SSZ, HPZ and DDZ suites plus `TestEveryObjectRewindRoundTrip` and
`TestRewindHarnessCoverageRatchet`: **1476 tests, 0 failures, 0 errors, 0 skips**.
`-Pguards test -B`: **669, 0 failures, 0 skips**. Four comparisons were broken on purpose and each
was caught — the cloud's `byte_46698` index, the spring's `Level_frame_counter` gate, the EggRobo
pairing gate and its animal release. `TestEveryObjectRewindRoundTrip` caught one real defect: the
swinging-carrier arc forced `barSpawned` true on restore, so a rewind past its allocation
re-spawned nothing. This is focused validation, not a suite pass.

**Claims still open for slice 3.** Of the five matrix claims, this slice closes *implemented* and
*ROM-derived expectations* for all eleven families and *rewind round-trip* through the generic
harness. It does **not** close:

| Claim | State |
| --- | --- |
| Cold-reachable | **Advanced.** The native fixture that carries SSZ act 1 is the one named `hpz_completerun` (`zone_id 10`, start `($100,$FAE)`, `bk2_frame_offset 396720`, `s3k-complete-sonic-tails.bk2`); the `ssz`-named fixtures are `zone_id 11`, Death Egg. `TestS3kSszColdRoutes` drives that movie's inputs from the SSZ entry: the bridge finishes at route frame **1390** and the route carries Sonic to X **`$6EB`** inside 6000 frames with nobody dying. That is past the bridge and the ledge, and stops just short of the `$7B` diagonal-walkway cluster at `$740` |
| Rewind-verified | **Six spots delivered**, one per family, each taken mid-action: cloud mid sag ramp, bar while holding, post with its carrier, the whole carrier chain, spring mid extension, EggRobo mid animal release. Each captures, steps, captures, restores, compares, replays one frame and compares again. What they cannot see is recorded in the helper: at every spot the instances survive the restore in place, so an `ObjectRefId` sidecar restore is never exercised — disabling the post's carrier restore leaves both these spots and `TestEveryObjectRewindRoundTrip` green. An `assertSame` on the resolved reference was written, found unable to disagree, and removed rather than shipped. A spot that forces recreation is owed |
| Visually matched | Four of six filmed: `10` the bouncy cloud throwing the player with its puffs (frame 68), `11` the elevator bar hanging him at mapping frame `$E5` (120), `12` the rotating post walking him through `byte_468C4` (200), `13` the swinging carrier's jointed arc (200). The `$74` spring and the `$A0` EggRobo are owed, with the reasons in `~/Videos/OGGF/ssz-bring-up/INDEX.md` |
| Breadth | **Delivered.** `TestS3kSszCompatibilityMatrix`: 320 and 800, no donor and the S1 donor, Sonic / Tails / Knuckles and two team shapes, ten scenarios each walking nine checkpoints, asserting every slice 1b-3 class loads and all eight ROM art keys have a ready renderer |

### The cold route, as far as it is measured

`TestS3kSszColdRoutes` drives `s3k-complete-sonic-tails.bk2` from frame 396720. The first attempt
gave it 1200 frames and the bridge never finished; widening the budget to 6000 was the cheaper of
the two candidates to rule out and it was the right one — the cutscene simply takes longer from a
cold load than the HPZ routes do. Measured 2026-09-18: bridge open at route frame **1390**,
furthest X **`$6EB`**, nobody dead across 6000 frames. The case pins both with a little slack so an
ordinary physics wobble does not fail it while a regression that stalls at the bridge or the ledge
will. What it does **not** do is compare against the fixture's own rows: this is recorded-input
reachability, not a trace replay, and no frontier is claimed past `$6EB`.

## Open items carried into later slices

- Engine ring-window floor admits the `(0,0)` record at `Camera_X <= 8` where the ROM does not.
- The act-1 `LevelData` start `$100,$C00` is never used on the no-starpost path: `SSZ1_ScreenInit`
  overwrites the camera and `Obj_57C1E` the player. Resolved in slice 1.
- The engine's arrival begins one frame later than fixture `hpz` row 0 implies, because the screen
  init runs from pre-physics of frame 1 rather than inside the level load. Values match exactly;
  the phase is slice 10's to settle.
- Rewind spots now exist for slice 3's six families, the `$7E` debris deletion (the first SSZ spot
  where an `ObjectRefId` sidecar is load-bearing, because the children really are gone at the
  restore) and the Green Hill fight. Still not exercised: a spot mid-arrival or mid-cutscene, and
  any spot at a wide viewport.
- The pseudo-starpost's other half — die after the bridge and respawn at `($140,$C6C)` — is still
  owed. Slice 4 drives a real death and reload, but at `$34:$03`, not at the bridge's write.
- The Death Egg's `Pal_KnuxSSZEnd` patch, its missile and cloud children, and cutscene Knuckles'
  resting X are filed in [s3k-known-bugs](../../../status/s3k-known-bugs.md).
