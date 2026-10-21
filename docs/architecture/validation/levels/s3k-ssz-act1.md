# S3K Sky Sanctuary Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_1`, engine zone `$0A` act index 0,
ROM `Current_zone_and_act = $A00`, SKL object set.
Character routes: Sonic, Sonic + Tails, Tails (`LevelSelect_CheckKnuckles` denies Knuckles except
with `Debug_cheat_flag != 0`, and no Knuckles art or route exists for act 1).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: all 213 placed records have concrete owners; arrival Death Egg, bosses,
results and the DEZ launch are implemented. Historical cold Sonic + Tails routes at 320/800,
Sonic solo at 320 and Tails solo at 320/800 defeat all three bosses and load DEZ1
without leader deaths or position/health/ring seeds. Native 800px paired coverage
also checks the released destination, native P2 roster and full-load history
isolation. Sonic solo at 800, intermediate viewport widths, donors, additional
team shapes and native presentation acceptance remain open. The current800 paired
route is failing on exact Root base `0e4f7a9f202f477018d4adeb01295316704c0ea1`:
its first-replica endpoint has48 rather than46 rings, and its complete route dies
at input11664. Older800 paired reachability, completion and replay evidence below
retains its dated runtime attribution; it does not qualify this base. See the
[bounded current investigation](#wide-paired-current-frontier--2026-10-21).
Matched R2 Main control passes the46-ring first case; the current48-ring endpoint
is therefore a campaign-introduced outcome difference. See the
[R2 Main attribution](#wide-paired-matched-main-control-r2--2026-10-21).
Nothing below certifies the act.

Current native320 Sonic + Tails controller evidence on runtime `39eca8c35`,
parent `3a0b7b5fa` plus the post11051 input delta, covers the physical first post,
both replicas and the 20,720-input complete route through released DEZ1. The
complete method passes50 two-cycle45 whole-world windows and actual load/assembly
20550/20551. Earlier records retain their stated sources and limits; see the
[current complete controller qualification](#post11051-complete-controller-qualification--2026-10-21).

Original native320 solo Sonic author evidence uses the frozen runtime `39eca8c35`
and parent `9cc150c12183297293cb45bbf8f24023a95b8dbc`, with controller resources
and controller-derived test data only. The8300-input replica route and14544-input
complete route pass the existing solo class, including45+49 full-registry
45-input replay windows and strict DEZ entry/history assertions. See the
[solo controller qualification](#solo-sonic-native320-controller-qualification--2026-10-21).

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
| CENSUS: placed objects and rings | `SSZ1_Sprites $1F90EE` (213 records, 66 rows), `SSZ1_Rings $1F9616` (180 records, first `(0,0)`); `sub_1BA0C`/`loc_1BA4A`; `Load_Rings` `loc_E8BE` | ROM decode | `TestS3kSszPlacementCensus` | yes | n/a | n/a | yes (decode pinned to the ROM) | n/a | `actOneLeavesNoPlaceholderFamilies` asserts every placement has an implemented owner |
| CENSUS: wrap-seam cloud records | Two `$7D` records store Y `$103C`/`$104C`; the `& $FFF` mask puts them at `$03C`/`$04C` | ROM decode | `TestS3kSszPlacementCensus#theTwoWrapSeamCloudRecordsMaskAcrossTheSeam` | yes | n/a | n/a | yes | n/a | Bouncy-cloud production behavior exists; connected wrap-seam route comparison remains open |
| CENSUS: leading `(0,0)` ring record | `loc_E8BE` starts the scan at `max(Camera_X - 8, 1)`, so the record is always stepped over | ROM decode | `TestS3kSszPlacementCensus#ringRecordsMatchTheRomIncludingTheLeadingZeroRecord` | yes | n/a | n/a | yes | n/a | Shared `Sonic3kRingPlacement` already uses the corrected floor; ROM totals remain179/0. The old pending-merge claim is historical. |
| ARRIVAL: no-starpost screen init and controller | `SSZ1_ScreenInit`, `Obj_57C1E`/`loc_57CAC`/`loc_57CD2`/`loc_57D3C`, `Obj_57D64`, `loc_57DA2` | 320 + 800; Sonic, Sonic + Tails | `TestS3kSszArrivalHeadless` | yes | yes (cold load) | not exercised | forced camera, `Camera_Y + $65`, the 8 px rise and the skipped final camera step all match; one-frame phase against `hpz` row 0 open | `01-ssz-arrival-beam-sonic-tails.mp4` | Tails-solo row and the wide rewind spot open |
| ARRIVAL: Tails helper and CPU routine | `Obj_57DCC`, `loc_13AB4` (`sub_13ECA`, `Tails_CPU_routine $A`, `object_control $83`) | Sonic + Tails, 320 | `TestS3kSszArrivalHeadless#theSidekickParksOffScreenUntilTheArrivalHelperReleasesHer` | yes | yes | not exercised | `($7F00,0)` park and in-air status match the fixture's row-0 sidekick sentinel | `01-ssz-arrival-beam-sonic-tails.mp4` | Her swing arc is not compared to native |
| BOUNDS: act-1 dynamic Y bounds and Y-wrap | `sub_575EA` `word_5778A`/`word_5779A`; wrap `-$100 … $1000` | 320 | `TestS3kSszKnucklesBridgeHeadless#theCutsceneReleasesTheBridgeAndOpensTheAct` | yes | yes (the band is asserted at the bridge release) | not exercised | band values asserted from the tables | — | GHZ/MTZ bosses and positioned arena reachability are implemented; cold route and complete wrap coverage remain separate |
| CUTSCENE: Knuckles spawner, button `$AF`, bridge `$77`, pseudo-starpost | `Obj_57E34` → `CutsceneKnux_SSZ` (11 routines `0..$14`), `loc_658F2`, `loc_65976`, `Obj_SSZCutsceneBridge` `loc_44FA2`/`loc_44FBA`/`loc_4501A` | 320 | `TestS3kSszKnucklesBridgeHeadless` | yes | yes: the bridge retracts 2 px/frame and clears `Events_bg+$05`, and a pre-set star post starts it extended with no arrival | not exercised | flag order, retract rate and the `($140,$C6C)` checkpoint match the routines; no native probe | `02a`/`02b` clips | 2026-09-24: palette/children implemented; native and engine landing both($3A0,$C64); matched full-scene pixels remain open |
| BG: plain sky framing and the `$1800` latch | `sub_579F0` `loc_57A12`/`loc_57A30`/`loc_57A4C` | 320 | `TestS3kSszScrollBands` | yes | yes (the arrival opens in plain sky) | n/a (derived per frame) | offsets and the latch's re-rounding asserted from the routine | `04a-ssz-sky-and-clouds.mp4` | The engine reads `Camera_X/Y_pos`, not the `_copy` words; identical until the launch's screen shake |
| BG: cloud band, per-band deformation and drift | `sub_57A60`, `SSZ1_BGDeformArray`, `ApplyDeformation` | 320 + 800 | `TestS3kSszScrollBands`, `TestS3kSszBackgroundLayout` | yes | yes (the arrival rise crosses `$F00` into the band) | n/a | the thirty-word fan, the halved wrapped background Y, the seven visible bands at camera `($800,$E00)` and the `$500`/frame accumulator all asserted from `sub_57A60`; the background layout itself is decoded from the ROM and compared with the engine's layer column for column | `04a`/`04b` clips show the arrival band only, which the ROM's layout makes flat sky; the cloud source-window correction has historical footage; whole-scene comparison remains open | **Historical cloud-window defect, subsequently corrected.** Plain mode's flat sky at the arrival is correct (layout rows 0-2 are one repeated chunk and `Camera_Y $F49 + $160` wraps to row 1), but `loc_5786A`/`loc_57946`/`loc_5799A` pin the cloud plane to layout X `$1C00` (columns 56-59, `moveq #$20,d6`) and `SwScrlSsz` now supplies that source. The separate plain-mode pixel demonstration remains open in known bug #41 |
| BG: mode transition machine | `SSZ1_BackgroundEvent` routines 0/4/8/`$C`, `Events_bg+$0C`/`+$0E` | 320 | `TestS3kSszScrollBands` | yes | yes | yes (the whole state is in `SszZoneRuntimeState`) | routine order and the frozen framing asserted; the ROM's multi-frame `Draw_PlaneVertBottomUp` completes in one engine frame | `04a` | Recorded difference: the cloud bands appear one frame after the ROM starts filling them |
| BG: roaming clouds `loc_57BB2` and `sub_5758A` | five `word_58758` rows, `Gradual_SwingOffset($1C00,$80)`, the `$1FF`/`$FF` screen masks | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes (capture/restore + forward replay) | row count, drift, per-cloud RNG phase and the screen periods asserted from the routine | `04a`/`04b` | The `$1FF` period is screen-space, so a wide viewport shows the wrap seam inside the visible area |
| BG: cloud oscillator `_unkEE9C` and the ten solid clouds | `loc_57B6A`/`loc_57B76`, `loc_57B8E`, `word_5853E`, `SolidObjCheckSloped2` | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes | ten rows decoded from the ROM inside the test; `y_pos = y_vel - _unkEE9C` asserted for 240 frames | `04a`/`04b` | The selected 800px paired cold path rides the first sloped cloud before the upper bouncy-cloud/Y-wrap carrier; other cloud rows remain separate; eight of the ten rows over-read their own slope table into the next one, as the ROM does |
| ANIM: AniPLC (6 scripts), and none in act 2 | `AniPLC_SSZ` `$28AA4`; `Offs_AniFunc` entries 40-43 (`AnimateTiles_DoAniPLC` / `AnimateTiles_NULL`) | 320 | `TestS3kSszPatternAnimation` | yes | yes (runs from the cold load) | n/a (the animator's counters are snapshotted by `PatternAnimatorSnapshot`) | all six scripts' duration, destination tile, frame count and tiles per frame asserted from the declarations, including the third script's three frames | `04a` | No per-tile pixel comparison against native |
| OBJECT: `$79` SSZ pads (all ten act-1 placements) | `Obj_SSZHPZTeleporter` init, `loc_455BA`, `loc_455CC`-`loc_45790`, `loc_457BE`, `loc_4581C`, `sub_45866` | 320 | `TestS3kSszTeleporterPads` | yes | selected cold routes at320/800 reach their receiving platforms; see current route closure below | not exercised | lift `(subtype & $3F) * $10`, the gated-pad sink and 4-frame rise, the launch bounds/`Scroll_lock`/`Events_bg+$05` writes and `byte_466E8` all asserted from the routines; the historical component cases use a declared seeded `st (Events_bg+$00)` in their pre-boss setup | `08-ssz-teleporter-pad-launch.mp4`, frame 200 | Component seeds remain declared; full cold320/800 paths drive the actual encounters and selected pads without flag seeds. The ten-placement/participant product and native comparison remain open |
| OBJECT: `$7F` floating platform (8 placements) | `Obj_SSZFloatingPlatform` / `loc_44AA0`; `SolidObjectTop` `d1 $2B`, `d2`/`d3` `$11` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | the 4-pixel dip and its one-pixel-per-frame ramp asserted from `loc_44AA0` | `06-ssz-floating-platform-dips.mp4`, frame 150 | Wide-viewport and donor rows open |
| OBJECT: `$7E` collapsing column + debris (25 placements) | `Obj_SSZCollapsingColumn` / `loc_44B30`/`loc_44B90`, debris `loc_44BCC`/`loc_44BF8`, `word_46618` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | eight pieces from `word_46618` decoded in the test, the `routine(a0)` report count and the `$7FFF` park asserted from the routines | `07-ssz-column-breaks-into-eight.mp4`, frame 90 | The `Random_Number` bob phase is drawn per column, so the column order of a load moves the RNG stream; not compared to native |
| OBJECT: `$7C` collapsing bridge + shared debris (8 placements) | `Obj_SSZCollapsingBridge` / `loc_44C76`/`loc_44C9C`/`loc_44D22`, debris `loc_45052` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | subtype bit 7 (the only bit read), the four pieces with their `$102` frame word and 6/12/18/24 delays, and the 8-px-per-six-frames shrink to the `$7FFF` park, all asserted from the routine | `07-ssz-column-breaks-into-eight.mp4` shows the `$7E` sibling; `$7C`'s own clip is owed | Wide-viewport and donor rows open |
| OBJECT: `$74 $75 $76 $7A $7B $7D`, EggRobo `$A0` | per-object inits | native + bounded wide cases | `TestS3kSszCarriersAndSprings`, `TestS3kSszTraversalPlatforms`, `TestS3kSszEggRobo`, `TestS3kSszPlacementCensus` | implemented; zero placeholder placements | selected full cold paths at320/800 are qualified; complete family breadth remains open | local family checks; complete participant/route product open | source-backed; complete native sequences open | historical local captures | Earlier113-placeholder count was a slice baseline, not current status |
| LIFE: death, starposts `$34:$02/$03/$04`, respawn, Y seam | `LevelSetup` (sonic3k.asm:102185) `clr.l Events_bg+$00/$04/$08/$0C`; `Level:` (7623) `clearRAM _unkFA80,$80`; `SSZ1_ScreenInit` starpost path; `sub_575EA` MTZ pre-lock | 320 | `TestS3kSszLifecycleProduction` | yes | declared restart, not a route: `SSZ1_ScreenInit` drags a leader with no star post back to the arrival column, so `$34:$03` is written the way the ROM writes it | yes: the reload resets the timeline instead of continuing the pre-death history, and builds a new `SszZoneRuntimeState` (`assertSame` fails) | yes: `hpz_2` is itself a `$34:$03` restart (metadata `start_x 0x14C0 / start_y 0x00E8`) and its row 0's player `($14C0,$EC)` and camera `($1420,$8C)` are matched exactly | `16-ssz-death-restarts-at-the-star-post.mp4`, frames 77/150/220 | Only `$34:$03` is driven; `$34:$02`/`$34:$04` and a wide row are owed, as is a capture of a post being physically touched |
| BOSS: GHZ recreation hits, flash, scatter | `sub_7A5A0` / `sub_7A614` / `word_7A622` / `word_7A628`; `Touch_Enemy` `.checkhurtenemy` (sonic3k.asm:20922); `loc_849D8` / `Obj_FlickerMove` / `Set_IndexedVelocity` / `Obj_VelocityIndex`; `ObjDat3_7A678`; `word_7A65A` | 320 | `TestS3kSszGhzArenaHeadless#onlyTheBallHasCollisionAndItHurtsThePlayerThroughTheTouchPass`, `#oneRealHitFlashesThreePaletteWordsFromTheShippedRow`, `#theKillingHitScattersTheChainOneLinkAFrameAndTakesTheBallsHitbox`, `#theEmitterFlickersEveryOtherFrameAndCarriesNoCollisionByte` | yes | all eight hits and the ball's hurt go through `ObjectTouchResponseController` with the player pinned attacking/non-attacking; no `onPlayerAttack` call anywhere | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($160,$7C0)`, 1142 rows: its aux `object_appeared` rows carry each slot's code-pointer address, which dates the whole fight. Spawn `($270,$780)`, first dispatch init + 33, chain drop at `x = $200`, `$3F + 1 = 64` fade frames, `$78 = 120` escape frames all match the port's intervals. The 184-frame defeat total matches too: it was 183 here until these rows showed the killing frame takes no decrement on the cartridge, and `defeatDeferralAppliesToThisBoss()` now models that, as `HczMinibossInstance` does. Native rings fall 55 → 45 across the window, which the port could not do before the ball got its box. The rows **corrected** the scatter: all six links convert on the killing-hit frame, not one a frame | none yet | Palette is not in this schema, so the flash is still uncompared. The flash writes line 0 colours 7/14/15, measured as already holding `word_7A628`'s first row before the fight, so the row the ROM leaves behind is invisible |
| BOSS: GHZ recreation lock / fight / defeat | `sub_575EA` `loc_57686`-`loc_576E8`; `Obj_SSZGHZBoss` `off_7A2B4` routines 0-`$A`, `sub_7A5A0`, `loc_7A3CE`-`loc_7A3F8`; children `ChildObjDat_7A684`/`_7A69E`, `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszGhzArenaHeadless` | yes | declared restart at `($200,$7C8)`: the leader settles on the arena floor at `($200,$86C)` with the camera at `($160,$7C0)` by the act's own physics, and both gates then fire. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | yes: mid-swing with all six links out; nulling the links' `chainParent` restore fails five of the six | **not claimed.** Values come from the routines only; the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142 rows) is unread | `17-ssz-green-hill-recreation-ball-and-chain.mp4`, frames 330/420/600/900 (pre-review: no ball hitbox, no scatter) and `18-ssz-green-hill-ball-and-chain-hurts.mp4`, frames 150/250/330/**361**/400 — 361 is the ball on top of a standing Sonic, the frame he is killed, which the pre-review fight could not do. Filmed with `--star-post --x 0x200 --y 0x7C8`; a bare teleport cannot capture this arena, because `SSZ1_ScreenInit`'s no-star-post intro overrides `--x/--y` | The `$79:$AA` pad's post-defeat rise is implemented but not driven end to end |
| BOSS: MTZ recreation lock / spawn / entry | `sub_575EA` `loc_5770C`-`loc_5775C`; `Obj_SSZMTZBoss` init, `loc_7A712`, `off_7A728`, `loc_7A72C`, `loc_7A7C4`; `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszMtzArenaHeadless#theUpperArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles`, `#thePreLockLeftLimitIsTheGreenHillWordsToChoose`, `#theShipEntersAtSeventeenHundredAndDispatchesThirtyThreeFramesAfterItsInit`, `#theSetupOverwritesTheEggRoboPairingWord` | yes | declared restart at `($1700,$420)`: the leader settles on the upper arena floor, the lock fires with `Camera_X` at `$1660` through `nativeFramedCameraX` at both widths, and the spawn follows when the camera eases to `$380`. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($1660,$380)`, 1004 rows: the boss slot appears on native frame 6156 and takes `loc_7A71A` on 6189 — init + 33, which the test asserts as `$1F + 2`. The Mecha Sonic head and all seven orbs appear together on 6190, one frame later, which is what `loc_7ADA2`'s `movea.l a0,a1` plus its six allocations produce. The arena floor `y = $42C` and the restart position come from the same window | `19-ssz-metropolis-orb-ring.mp4` (`raw-31-ssz-mtz-fight`), frames 215/260/**318** — 215 is the ship and its ring entering the arena, 260 the ring turning around it, 318 an orbiting orb killing a ring-less Sonic. Filmed with `--star-post --x 0x1700 --y 0x420` | The spawn writes world coordinates, so unlike Green Hill nothing here needs the camera offset |
| BOSS: MTZ ship motion, orbs, hits, lasers, defeat, pad `$79:$F6` | `off_7A7F0` arms 0-`$E`, `sub_7A85A`, `off_7AA60`, `sub_7AB56`, `ChildObjDat_7AB80`; `sub_7AC06` / `sub_7ACF2` / `sub_7AD6A` / `word_7AD7E`; `loc_7AD8A` routines 0-8, `sub_7AEB0`, `sub_7AF5A`, `sub_7B0C2`; `loc_7AC7A`-`loc_7ACA4`; `loc_4554E` gated pad on `Events_bg+$02` | 320 | `TestS3kSszMtzArenaHeadless#theDescentIsOnePixelAFrameAndTurnsToFaceThePlayerAtFourTwoZero`, `#thePatrolTurnsAtItsTwoLimitsAndHoversFourPixels`, `#theShipLeavesThePatrolOnItsSecondTurnAroundAndNotItsFirst`, `#theOrbRingSortsItselfFrontAndBackFromTheCosineOfItsAngle`, `#aHitLaunchesExactlyOneOrbAndSpendsOneArmCycle`, `#theHitWindowFlashesTheShippedRowAndReopensTheBoxAtTheSmallerSize`, `#theLastArmCycleStartsTheLaserPassAndFiresThreePairs`, `#theDefeatTakesTheCartridgesFrameCountAndOpensTheGatedPad` | yes | every hit goes through `ObjectTouchResponseController` with the player pinned attacking, and the tests pop the launched orbs the same way — `loc_7A98A` will not release the ship until `$30(a0)` is zero, so the fight has to be played rather than stepped | yes: mid-fight with one orb off the ring; the restore relinks all seven orbs and `$30(a0)` | **compared, comparison-only.** The same window dates the killing hit at 6651 and `loc_7AC92` at 6715 — `64 = $3F + 1` — which is why `defeatDeferralAppliesToThisBoss()` was overridden before a line of the fight was written rather than after. Native rings fall 55 → 38 across the window. The orbit, the sort, the laser cadence and the flash have **no** native comparison | `20-ssz-metropolis-orb-launched-by-a-hit.mp4` (`raw-34-ssz-mtz-chase`, frames 417/441/490/706), `21-ssz-metropolis-laser-pass.mp4` (`raw-35-ssz-mtz-laser`, 1745/1800/1855) and `22-ssz-metropolis-defeat-and-gated-pad.mp4` (`raw-34`, 1737/2205/2260/2369). All three want `--rings`, ported here from the Lava Reef campaign's `b35f59d33`: the declared restart starts Sonic at zero rings and every orbiting orb is `$87`, a `Touch_ChkHurt` byte that hurts an attacking player too, so clip `19`'s ring-less capture died at first contact. With 355 seeded, one 320 route lands all eight hits and rides the pad out. The laser pass needed its own run — the eighth hit lands about 30 frames after the seventh, so `raw-35` branches `raw-34`'s input at frame 1720 and stops attacking for 240 frames. The arm-raise cycles are still not cut to a clip of their own | Gaps recorded in `s3k-known-bugs.md`: explosion replacement-slot stop-byte fidelity remains open; orbs still on the ring are deleted with the ship where the cartridge leaves them orbiting a freed slot; the shared `S3K_SPECIAL_PROPERTY` arm teleports the sidekick onto every `$C0` object. The laser gap is `$1E + $10 = 46` frames, because `loc_7AA44` returns on `$33(a0)` before `sub_7AB56` is reached |
| BOSS: Mecha Sonic spawner, entry and attack loop | `loc_45A66`-`loc_45AB0`; `Obj_SSZEndBoss` `SSZEndBoss_Index` act-1 entries 0-`$28` (`loc_7B2DC`, `loc_7B308`, `loc_7B3E6`, `loc_7B41C`, `loc_7B44A`, `loc_7B462`, `loc_7B484`, `loc_7B4EC`, `loc_7B544`, `loc_7B57A`, `loc_7B5E8`, `loc_7B63A`-`loc_7B804`); `sub_7D2D8` / `byte_7D2FC`; `sub_7D312`; `sub_7D35A`; `loc_7D216`; `sub_7D236` / `byte_7D24C`; `DPLCPtr_MechaSonic` | 320 | `TestS3kSszMechaSpawnHeadless` | yes | declared restart at the pad's own `($1A40,$670)`: the final arena's lock fires, the camera eases to `$5C0`, and `loc_45A84`'s `Camera_Y == Camera_max_Y` gate allocates the boss through the plain-`AllocateObject` path. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | yes: mid-entry, with the two `ChildObjDat_7D47A` after-images relinked | **compared, comparison-only.** The Sonic + Tails run's SSZ **third** segment, `hpz_3` (`zone_id 10`, start `($1880,$968)`), carries the whole fight; the first reading of this row said the `hpz` window ended before the arena, and that was wrong. Camera `($19A0,$5C0)` at the spawn, so `Cam_X + $160 = $1B00` and `Cam_Y + $A0 = $660` are the native spawn exactly, and the routine dump's resting X values `$1AC0` and `$19C0` are `_unkFAB6` and `_unkFAB4` — `loc_7D216`'s two limits. Pad at 358, boss at 402, routine 4 first dispatched 406, pad removed 460 (the boss passes `$1A40` about 426, plus `loc_45AB0`'s `$20`). The graph runs 4 → 8 → `$0A` → `$0C` → `$0E` → `$10` → `$12` → `$14` → `$16` → `$18` → `$0A`, then a long landing straight to `$14` → `$1A` → `$1C`, which is the two `loc_7B484` branches and `byte_7B62E` stepping 0 then 1. Defeat 1310, routine 2 on 1438 and routine 4 plus the `loc_7D056` slot on 1439. **Routine 6 never appears** in the dump because the boss is off screen for it, which is a recorder property and not a ROM fact | `23-ssz-mecha-sonic-entry-and-attacks.mp4` (`raw-38-ssz-mecha-line1`, frames 200/268/330/430/560): the entry from the right, the return along the top and the attack cycle on the arena floor. The pad's own explosion is driven by the test only — at this camera it sits where the column meets the floor — and subsequent `raw-43-mecha-complete-graph` covers implemented defeat/results/launch through DEZ1 | Stages 1 and 2 of slice 7. `loc_7B81A`'s act-1 routines 0/2/4 and `loc_7D056`'s handover are in; the later campaign implements palette/spark/defeat owners. The bare `loc_7B39C` allocation writes no SST and needs no object. Native hit-window/slot-reuse verification remains open |
| BOSS: Mecha Sonic spawn / fight / defeat, results + save | `loc_45A84`, `sub_868F8`, `Check_TailsEndPose`, `loc_2DCA0` | 320 | `TestS3kSszMechaSpawnHeadless` | partial | declared final-pad checkpoint; real results reaches end flag | yes: real results graph restore and forward replay | ROM source | recording owed | Production results added in campaign; native P2 pose, allocation exhaustion, signed countdown. Save/launch and broader route matrix still open. |
| EVENT: crumble, hot-swap, Death Egg BG, debris, ramp script, `$B00` request | `SSZ1_ScreenEvent` stages 0/4/8, `Obj_57E96`, `sub_5750C`, `sub_574DC`, `loc_58192`, `loc_581D2` | 320, 400, 800 | `TestS3kSszMechaSpawnHeadless`, `TestSszLaunchState`, `TestSszLaunchBackground` | partial | declared checkpoint and positioned boss hits; production results, first forced jump and neutral-input launch reach actual DEZ1 load | graph recreation and forward replay at four launch boundaries, including retained Plane B | source-backed; native parity not yet measured | clip 24: controller Hyper checkpoint clear, full 4800-frame capture through DEZ1 | Native Sonic 320/400/800, Sonic + Tails 320, Tails 400 and S1 donor 320 pass component handover. Cold320/800 paired and solo-Tails complete routes verify actual-load history isolation. Extra-followers, exhausted slots, other handover configurations and native comparison remain open. |
| ROUTE: native320 paired all three bosses → released DEZ1 | Ordinary cold start; unchanged physical post2 and upper waypoints; Mecha/results/launch/destination owners | Sonic + Tails, donor off,320px | `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` (preserved3a prefix); whole `TestSszColdRouteCapture` (fresh follow-up) | existing owners; controller-only repair | yes, all20720 complete inputs alive, three real killing hits and actual DEZ1 | upper25/complete50 spots, two45-input full-registry restore/replay cycles each; actual full-load history retirement plus incoming intro/released windows | no new native comparison; selected-shot producer identity unknown | independent fully drawn engine capture; no native pixel claim | Runtime39eca8c35, parent3a plus841 post11051 input changes. Root owns later dependency/integration qualification; inherited configuration breadth remains open. |
| ROUTE: native320 solo Sonic all three bosses → DEZ1 | Ordinary cold boot10/0; genuine eight-hit GHZ/MTZ/Mecha defeats, transports, results and actual load | Sonic solo, donor off,320px; zero CPU followers | whole `TestSszSoloColdRouteCapture` | existing owners; controller-only repair | yes,8300 replica/14544 complete inputs; no leader death | unchanged45 replica+49 complete full-registry45-input windows; actual-load live-history isolation | no native comparison or cause claim | independent ordinary cold captures; results and later incoming DEZ scene inspected | Runtime39eca8c35 / parent9cc plus exact solo input/data hashes below. Root owns current-POM/backend/composed qualification; widths/donors/native acceptance remain open. |
| ROUTE: native800px paired all three bosses → released DEZ1 | Ordinary cold entry; GHZ/MTZ/Mecha native health8..0; `loc_7D056` results; `Obj_57E96`/`loc_581D2` launch; `loc_6468` destination assembly | Sonic + Tails, donor off,800px | `TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | existing owners; current controller route unqualified | **fail on0e4f7a9f**: first forward branch11640+24 dies at11664 | Current run stops before MTZ/Mecha/DEZ; historical44 semantic two-cycle45 windows retain their original runtime | ROM-backed owners; no new native comparison | Ordinary fully drawn replay also dies11664; no native pixel claim | All21014 maintained inputs and all predicates retained. Bounded eight-candidate exploration does not qualify a replacement; see current frontier below. |
| LOAD: `$B00` (DEZ) actual destination entry | DEZ campaign; `loc_6468`, `Obj_LevelIntro_PlayerRun`/`loc_44A26`/`loc_44A56` | Other qualified cold configurations; paired800 historical only | Complete cold-route checks; `TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | yes on other qualified routes; current paired800 unreached | actual assembly and native entrance control release on qualified configurations | paired800 two-cycle45 intro/released windows and full-load history retirement are historical; current paired800 remains open | ROM start/floor seating and intro owner; native scene comparison open | independent live DEZ engine still | Destination entry only. Current paired800 stops before MTZ and cannot qualify DEZ. Full DEZ traversal and later endpoints belong to the DEZ campaign; no native pixel certification. |
| BOSS: MTZ orb allocation / first dispatch | `loc_7A72C`, `loc_7ADA2`, `loc_7AE12`, global `AllocateObject` vs forward `CreateChild1_Normal` | Declared sparse SST component; native320 whole-world encounter boundaries | `TestSszMetropolisRecipeBirth#metropolisFirstOrbAboveParentReturnsFromItsOwnSetupWithoutOrbitOrPublication`; other `metropolis*` allocator/graph controls; `TestS3kSszMtzArenaHeadless` | one pending global first orb; own setup creates at most six global siblings and returns | Component pressure is not a cold-route result | pending setup, init, orbit and prefix failures; destructive graph replay and world checks | literal shipped-ROM source; no new native run | not measured | See [orb initialization evidence](#mtz-orb-allocation-and-first-dispatch--2026-10-21). Current-route impact remains unknown. |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz{,2,3}SegmentTraceReplay`, `TestS3kTailsFullChainHpz{,2}SegmentTraceReplay` | `-Ptrace-segments` | — | — | — | — | not measured | — | Slice 10 records each frontier |

## Opening Death Egg camera tracking (2026-09-30)

`TestSszDeathEggCutscene.cameraTrackerSuppliesSignedHalfDeltaToEggAndStationaryChildren`
checks `loc_66072` initialization and `loc_6607E`'s signed `SUB.W` / `ASR.W`
camera movement, including odd negative deltas and word wrap. The Egg, mask and
cloud consume the same delta. `productionCutsceneGraphRecreatesAndReplaysEveryRegistryKey`
checks ordinary-entry allocation before the Egg, then deletes and recreates the
helper and cutscene graph and compares all registered state after 45 right-held
frames, at 320, 352, 400, 528 and 800 pixels. This local camera mechanic is
independent of movement donation; donor, roster and lifecycle breadth remains
as recorded elsewhere in this matrix. The oracle is `loc_65794`'s allocation
order and `MoveSprite_SSZBGAdjust`, rather than a fitted screen offset.

Validation on `develop`, based on `9cbb22dcbed15258f11187c41f19114674f2abee`:

- Queued Maven `-Dmse=off -Dtest=TestSszDeathEggCutscene test`: 13 passed,
  zero skipped. The initial wide test reused a native-width session; reopening
  the session after configuring the width repaired that test setup.
- Queued Maven `-Dmse=off -Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
  59 passed, zero skipped (completed together with the initial cutscene run).
- Queued Maven `-Dmse=off -Pguards -Dtest=TestRewindCoverageGuard,TestRewindArchitectureGuard,TestObjectPriorityBucketGuard,TestObjectServicesMigrationGuard,TestObjectUpdateClockTerminologyGuard,TestSonic3kObjectProfileRegistryGuard,TestPatternSpriteRendererCorruptionGuard test`:
  24 passed, zero skipped in a fresh JVM.
- `GameplayCaptureTool --game s3k --zone ssz --act 1 --input <400 neutral; 300 right> --frames 601 --every 100 --no-video`:
  frame 500 shows the Egg farther right with the restored parallax. All 601
  player/camera/input CSV rows match the rebuilt baseline byte for byte.

ROM-backed tests received the existing root ROM's absolute path through
`-Ds3k.rom.path`. This file has CRC32 `0C06AA82` / SHA-1
`B711A909CCE238CA4AF3E517A2EDCA306228EFA5`, rather than the canonical identity
listed in `AGENTS.md`; the allocation and tracker instruction bytes at
`$657B0` / `$66072` / `$6607E` were checked directly against the disassembly.
These are focused checks, not a full-suite pass. The change-based plan selected
2,465 classes across six categories; that is disproportionate for restoring this
one object-local camera observer. Broad tool preflight found Lua 5.5 on the default
path and no PowerShell. The guard command used `LUA_BIN=/usr/bin/lua5.4`.
Whole-scene native capture comparison and inherited matrix gaps remain open.

## Historical cold-route closure (2026-09-24)

`TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents`
drives `ssz1-sonic-tails-cold-complete-320.bk2` from the normal act start for
19,492 controller frames. Its companion `.script` is the reproducible source.
The route reaches the killing hit for GHZ, MTZ and Mecha Sonic, retires both
replicas, rides all three transports, completes results and the spiral launch,
and asserts the actual DEZ1 load at `(48,2476)`. It never writes player position,
health, rings or event state. The shorter 11,051-frame replica test remains independent.

The complete test captures the full registered rewind graph at 37 spots, advances
45 frames, restores and compares all participants after replay. The 15 added
spots cover upper wrap/climbing, carrier release, transport, retracting spring
chain, final ascent, Mecha entry/fight/defeat, results, spiral and departure.
Live rewind is enabled only after those spots, records outgoing history, then
asserts that the actual DEZ load starts a fresh timeline. This closes the native320
Sonic + Tails cold reachability and transition-isolation gaps in the historical
rows above; it does not supply native-emulator parity or other configurations.

This extension exposed a shared player snapshot omission: the live tile-priority
bit and sprite-order bucket were absent. At input 14840 the replay inherited the
future priority and rewrote follower history differently. Both fields now restore;
a small regression checks both priority directions and independent sprite buckets.
The pre-fix route and unit tests failed; the corrected focused command
`-Dtest=TestAbstractPlayableSpriteRewindCapture,TestPlayableSpriteRewindState,TestSpriteManagerRewindCapture,TestSszColdRouteCapture`
with native GL and the actual S3K ROM passed **24 tests, zero failures/errors/skips**.
This is focused validation, not a new full-suite result.

External video: `$VIDEO_ROOT/ssz-bring-up/campaign-20260924-cold-complete-320/capture.mp4`,
frames16900–19731 of the cold run. State CSV confirms zero deaths over all19,732
frames; the additional240 neutral frames show DEZ's entrance run and return to
normal play. Stills17195/18600/19700 show defeat, spiral and DEZ respectively;
all were visually inspected, and the MP4 fully decoded without errors.

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

- The old ring-window sentinel gap is closed by the shared loader correction.
- The act-1 `LevelData` start `$100,$C00` is never used on the no-starpost path: `SSZ1_ScreenInit`
  overwrites the camera and `Obj_57C1E` the player. Resolved in slice 1.
- The engine's arrival begins one frame later than fixture `hpz` row 0 implies, because the screen
  init runs from pre-physics of frame 1 rather than inside the level load. Values match exactly;
  the phase is slice 10's to settle.
- Rewind spots now exist for slice 3's six families, the `$7E` debris deletion (the first SSZ spot
  where an `ObjectRefId` sidecar is load-bearing, because the children really are gone at the
  restore) and the Green Hill fight. The new arrival checks below cover rise/swing/release at all five widths and native rosters. Mid-cutscene and complete act breadth remain open.
- The bridge-written checkpoint now has a cold arrival/two-death lifecycle regression; see
  the 2026-09-27 follow-up below. Slice 4's older `$34:$03` restart remains a separate case.
- Historical cutscene issue (addressed in the 2026-09-24 follow-up): the Death Egg's palette/children and cutscene Knuckles'
  resting X are filed in [s3k-known-bugs](../../../status/s3k-known-bugs.md).

### Recreated-boss defeat explosion follow-up (2026-09-22)

Both GHZ and MTZ allocate subtype-4 `Child6_CreateBossExplosion` forward from
its ship, with three-frame emissions and no RNG on failed child allocation.
Their existing defeat tests now capture, remove the explosion graph, restore it,
and replay forward during escape, while retaining the 184-frame release check.
`TestSszBossExplosionAllocation` covers exhaustion, slot reuse, RNG word ordering
and deferred deletion. Shared ROM artwork is loaded and asserted ready.
The refreshed Metropolis controller capture `raw-41-mtz-defeat-explosions` shows
bursts and the exit pad (source frames 1680–2399, 320 px, native Sonic alone,
checkpoint `$1700,$420`, 355 rings, zero deaths). Other widths/teams, GHZ visual
coverage, native timing/pixels and arbitrary replacement-slot `$38` remain open.
This adds a local defeat obligation; it does not certify either complete route.

Runtime snapshot follow-up: `TestSszZoneRuntimeState` now pins the EggRobo pairing
word and boss-active flag across retirement/fly-by changes and recreation. Both
were omitted from the prior codec. The 62-case runtime/EggRobo/three-boss/launch
selection passed without skips; full boundary coverage remains partial.


Mecha palette/spark follow-up: the defeat test now walks all twelve
`word_7D842` rows, the repeat edge and a rotation-disabled pause, asserting the
same-pass `$E88` spark gate and alternating frames. Launch recreation also removes
and recreates sparks. The full ROM art crawler validates the new eight-frame
prefix; 23 Mecha cases plus the updated 16-sheet inventory pass without skips.
`raw-42-mecha-defeat-sparks` shows the effect during results (frame 1805), using
the declared Hyper checkpoint setup, 320 px native Sonic alone. Native timing,
other visual breadth and arbitrary replacement-slot byte semantics remain open.
The bare `loc_7B39C` allocation is a search with no SST write, not a missing owner.


Secondary hitbox follow-up: `secondaryHurtBoxFollowsTheRomFrameTableAndSurvivesAHitWindow`
walks 1600 attack frames against `byte_7D280`, then verifies actual ring loss
while the main boss box is disabled. The killing-hit test checks retirement,
and the entry rewind spot recreates the removed collision owner. All 24 Mecha
cases passed, followed by the explicit recreation case. Native hit-window phase
and cold-route certification remain open. Refreshed `raw-43-mecha-complete-graph`
includes this hitbox and reaches DEZ1 at frame 4223 with no deaths over 4800
frames; declared Hyper checkpoint setup, not a cold route.


### Arrival and census reconciliation (2026-09-23)

Source/profile/census reconciliation confirms zero placeholders in both acts;
Act2's old expected `$B2:$00` gap was stale after SszCraneShip landed.
`TestS3kSszArrivalHeadless#freshArrivalRestoresAndReplaysRiseSwingAndRelease`
now covers all five widths for Sonic solo, Sonic+Tails and Tails solo (15cases).
At40,108and173gameplay passes it restores every registry key, advances45neutral
passes and compares every key again, then reaches released control without death.
The width helper now selects the actual matching aspect enum rather than labelling
all non320widths16:9. The seven-class arrival/census/bridge/lifecycle/background
selection passes60tests, zero skips, at23:41:08 BST on b6c1147a2+campaign edits.
This is native-roster local evidence, not donor or cold full-act certification.

The rising Death Egg now implements loc_659CC's full RNG reseed, palette patch
and restoration, plus sub_66054's missile cadence and loc_65B70 missile motion.
The seven ChildObjDat_665C4 cloud/mask/trail children are now implemented.
Native observation confirms Knuckles lands at($3A0,$C64), matching the engine;
$2A8 was incorrectly interpreted as his final X instead of the leap trigger.
Native scene comparison and wider matrix obligations remain open.

`TestSszDeathEggCutscene` now covers palette restoration after recreation, full
V-int reseeding, pre-movement missile allocation/cadence, positive/negative
scatter, deferred inclusive culling and every-registry-key forward replay after
missile recreation. Four component cases pass with the59required S3K checks
(63total/zero skips,23:56:50 BST). The subsequent child follow-up passes91focused cases including full-registry
restore/45-input replay across initial animation and the cloud drift boundary.
Both updated320/800videos decode with zero hurt/death. Native scene comparison
and broad campaign certification remain open.


### Cold permanent-staircase route repair (2026-09-24)

The previous cold frontier atX$6EB was a recording-input frontier, not yet a
validated traversal. An authored continuation exposed a real$7B collision gap:
getSlopeData returnednull and selected the shared flat-solid fallback, despite
the direct sample test reading correct ROM bytes. The native slope window now
reaches production landing/carry and changes with the collapse pointer. The
walkway also publishes its native high hardware priority independently of SAT3.

`TestS3kSszColdRoutes#authoredContinuationTraversesThePermanentDiagonalStaircase`
first failed on death at continuation223, then passes throughX2960/Y<3080, checks
the signed ROM surface, and compares every registry key after slope recreation
and15right-input replay. The combined route/traversal/compatibility/requiredS3K
selection passes89tests/zero skips at00:21:26 BST,69s.

Task-tree input sources `routes/s3k/ssz1-sonic-tails-cold-staircase-320.{script,bk2}`
preserve the first2501inputs from82EA movie offset396720 and add ordinary jumps
and rightward movement. Both2779-frame cold captures complete without death:
320ends(3043,3052),5rings,34hurt rows;800ends(3072,3051),12rings,no hurt. Film
2480..2778,299PNGs; full MP4 decode and still2750 reviewed at both widths.
External videos: campaign-20260924-cold-staircase-{320,800}. This advances the
route through the permanent staircase; the remainder of the act is still owed.


### Cold first-replica arena route (2026-09-24)

The authored native320 Sonic+Tails input now reaches the GHZ replica arena from
fresh SSZ1 arrival in4473frames, no ring/emerald/position seeds and no deaths.
It traverses the large spring, upper return walkways, rotating carrier,
collapsing columns and bouncy cloud. At4472 P1=(488,2156),37rings,
camera=(352,1984); the production object manager contains the first replica boss.
Inputs: routes/s3k/ssz1-sonic-tails-cold-ghz-320.{script,bk2}. This is arena
entry, not boss defeat or full-act completion.

TestSszColdRouteCapture drives GameplayCaptureSession's production loop and
render path, comparing all registry keys after45-input replay at carrier3440,
column3818, cloud4168 and arena4400. Queued command:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk DISPLAY=:0 python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen -Dtest=TestSszColdRouteCapture,TestS3kSszColdRoutes test`.
On b6c1147a2+campaign edits,3tests pass,0failures/errors/skips,26.334s,
2026-09-24 00:36:46 BST. Earlier attempt with HeadlessTestFixture did not
reach the arena lock (maxX remained$19A0 instead of$160); its bootstrap/runner
is not the production capture loop, and that mismatch remains uninvestigated.
The final test deliberately exercises the captured production path; this does
not establish equivalence of the two harnesses or native-ROM parity.

Both320/800 captures run4473frames without death; film2779..4472=1694PNGs.
The same input at800 stops earlier at(2047,2540),44rings; wide continuation
is still owed. Both MP4s fully decode; native final frame reviewed. External
archive: campaign-20260924-cold-ghz-{320,800}, with commands/input hash.
Rejected left-wall jumps atX842 were a route-choice error: landing on the
nearby cloud atX880 supplies the ascent. No runtime change was made for them.
Combined campaign validation/integration/push remain pending.


### Cold first replica defeat and transport (2026-09-24)

The native320 Sonic+Tails cold route now defeats the GHZ replica and takes its
released teleporter to the upper receiving platform, without position/ring/
emerald seeds or deaths. Task-tree input:
`routes/s3k/ssz1-sonic-tails-cold-first-replica-320.{script,bk2}`,5827frames.
Endpoint(512,1420),0rings,control released. This advances the preceding entry
frontier; it is not a full-act completion. Wide continuation remains separate.

A temporary read-only feedback probe authored ordinary direction/jump input,
then the frozen BK2 was replayed from cold through the production loop. The
initial static jump sequence died after two hits; centre/follow approaches died
after five/six hits. Following slightly to the right of the ship succeeded.
These rejected control choices supplied no evidence for changing boss physics.
One ordinary jump lands on the risen pad after escape and triggers transport.
A further600R attempt dies at5937,(904,1388),with0rings; do not use that suffix as
successful traversal. The next route task starts from the receiving pad.

`TestSszColdRouteCapture` now verifies boss creation, killing hit, removal,
native defeat flag, receiving position and released control; all registry keys
match after45-input replay at3440/3818/4168/4400/5100/5600 (carrier,column,cloud,
arena,killing-hit window,transport). Queued focused command:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk DISPLAY=:0 python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen -Dtest=TestSszColdRouteCapture,TestS3kSszGhzArenaHeadless test`.
15tests pass,0failures/errors/skips,28.146s,00:43:03 BST on b6c1147a2+campaign
edits. This is focused production-route/component validation, not a full suite.
No runtime code was changed for this extension.

External `campaign-20260924-cold-first-replica-320/capture.mp4` films4473..5826
(1354PNGs). All5827state rows show0deaths;120hurt rows; endpoint matches the
test. Full MP4 decode passes, frames5100/5600 reviewed; command and input hash
in provenance.json. Not native pixel parity. The previous headless-fixture
mismatch remains uninvestigated; full campaign integration/push still pending.


### Carrier lifetime and missing replica Eggmobiles (2026-09-24)

Cold-route extension exposed an invalid rewind reference from swinging arc to
rider bar at the second transport window. The generic object-manager range
check had removed the swinging tip while its hub/arc remained live. ROM
loc_46142 owns the hub coarse-X cull; loc_461FE and loc_462B6 delete arm and bar
only through the parent's signal. All three now bypass generic pre-culling and
retain the existing hub-owned cascade. The short carrier test moves the camera
out of range, verifies all three retire, then recreates the complete graph from
rewind. This is a lifetime correction, not a nullable-reference workaround.

The correction changes traversal at input7076: Sonic now lands on the formerly
missing bar. The old input ended at(3152,...) instead of its lower-path endpoint.
An ordinary jump off the bar, followed by a jump from the monitor at(3840,1392),
now reaches the upper walkway. Updated input
`routes/s3k/ssz1-sonic-tails-cold-middle-320.{script,bk2}` has7912frames and ends
at(5045,1196),25rings,no deaths. The old7681-frame lower-path capture
`campaign-20260924-cold-middle-320` is superseded diagnostic evidence, not the
current route. Remaining act traversal and wide input still need completion.

User review identified invisible Eggmobile bodies in both Act1 replica fights.
Both boss owners already request ROBOTNIK_SHIP frame$A; addSszEntries registered
that sheet only inside the Act2 crane branch. Consequently the renderer was null
and appendRenderCommands silently skipped the body while separate heads/attacks
drew. ROM PLC_78_79_7A_7B loads ArtNem_RobotnikShip; ObjDat_SSZGHZBoss and
loc_7A72C use Map_RobotnikShip frame$A,palette0,low hardware priority. The shared
sheet is now registered for both SSZ acts. This was missing art registration,
not another priority-bit mistake; no priority override was introduced.

The new SSZ1 art test failed first with missing standalone entry, then checks
ROM address,palette,nonempty frame$A and bounded tile references. The cold
production route also asserts that its live ship renderer exists and is ready.
Existing encounter logic tests had not asserted this dependency, and earlier
visual review failed to catch the head-only presentation; those old captures
must not be treated as complete visual acceptance.

Verification on b6c1147a2+campaign edits:
- The first lifetime selection passed76cases but failed the old route endpoint;
  the dangling-reference error was gone. No claim of a green run was made.
- `-Dtest=TestS3kSszCarriersAndSprings` passes18/0skip at08:02:43 BST,62s,
  including hub retirement and graph recreation.
- Queued Java21/DISPLAY=:0/absoluteS3K-ROM selection:
  `-Dtest=TestSszColdRouteCapture,TestS3kSszCarriersAndSprings,TestSonic3kPlcArtRegistry#sszAct1ReplicaBossesHaveTheRomEggmobileFrame+sszAct2CraneGraphHasRomBackedSheetsIncludingKnucklesHead+s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
 83pass,0fail/errors/skips,72s,08:09:08 BST. Cold route compares all registry keys
 across45-input replays at3440,3818,4168,4400,5100,5600,6300,6459,7076,7460.

`campaign-20260924-{ghz,mtz}-eggmobile-{320,800}` shows both restored ships.
Each is a declared positioned checkpoint,40rings,neutral input,450steps,
film150..449=300PNGs,0deaths/44hurt rows. Full MP4 decoding passes and frame250
was inspected at all four configurations. These establish engine presentation,
not native whole-fight pixel parity or cold wide completion.
The combined campaign selection currently has2902ordinary classes plus guards;
that run, integration,push and cleanup remain pending.

Current cold-route film `campaign-20260924-carrier-fix-320/capture.mp4` is7912
steps,film5827..7911=2085PNGs,0deaths,endpoint(5045,1196),25rings. Full MP4
decode passes and frame7076 confirms Sonic riding the formerly culled bar.

### SSZ replica widescreen locks — 2026-09-24 follow-up

User review of the static-mask prototype exposed an underlying camera bug. The
800px GHZ camera followed knockback from X112 to200; MTZ snapped from5488 to5728,
hiding the fight behind the fixed mask. Entry gates already added the native
framing inset, but the provider enabled projected bounds only for SSZ2. SSZ1
now derives projection ownership from the existing captured lock/fight flags,
including allocation and defeat until the launch releases bounds. Native camera
bounds remain $160/$1660. No extra rewind state or gameplay bound changes.

Queued Maven with absolute s3k.gen and Java21: TestNativeArenaCameraFraming,
TestS3kSszGhzArenaHeadless, TestS3kSszMtzArenaHeadless, TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading, TestSonic3kBootstrapResolver, TestSonic3kDecodingUtils:
112 tests, zero failures/errors/skips, 2026-09-24 09:07:09 BST, dirty campaign
HEAD b6c1147a2. Ten new cases cover both replica locks at320/352/400/528/800,
player displacement, preview, defeat/release and restored ownership. This is
focused validation; combined campaign delivery remains pending.

Recaptured campaign-20260924-{ghz,mtz}-camera-fixed-{320,800}: 450 steps each,
film150..449, positioned checkpoint/40rings/neutral input. All300 filmed rows
retain X352/112 forGHZ and5728/5488 forMTZ respectively, with44hurt rows each.
Sonic stays inside the native central rectangle throughout; full MP4 decode
passes and wide frame400 was inspected for both fights. Earlier eggmobile
captures remain art evidence but are superseded for camera presentation.

The reported roaming-cloud/wrecking-ball overlap matches shipped sprite order:
loc_57BB2 writes cloud priority0; ObjDat3_7A678 writes ball priority$280 (bucket5),
and chain rows write$300 (bucket6). Lower buckets precede later sprites in SAT.
Ball mapping pieces and base art word are low hardware priority; raising that
bit would not be a faithful sprite-order repair. No priority override applied.
This conclusion is disassembly-backed; no new native-video comparison claimed.

### Native cloud ordering corroboration — 2026-09-24

Replayed unmodified s3k-sonic-tails-complete-emeralds.bk2 from native save448920
through454000 using BizHawk2.11/GPGX and the native-reference host. ROM SHA1
CFBF98C36C776677290A872547AC47C53D2761D6; movie SHA256
AD40FB0B0A74FA12B08AB71B2E48A7455B388D14F43F4CDED502AC4A15D1B3C0.
No gameplay RAM writes. External archive:
`$HOME/Videos/OGGF/ssz-bring-up/native-ghz-cloud-order-20260924`.
Host completed with no failures;3001 continuous observed frames,151 screenshots.
Frame453000 visibly shows a roaming cloud covering the wrecking ball's upper-left
region and the Eggmobile. Frame453080 shows Sonic covered during the same fight;
frame451860 also shows player/cloud overlap on the approach. Live cloud code
$57BF6 retains priority0; the ball uses$280. Thus the reported occlusion is
original behavior, not justification for a priority override. This is native
visual corroboration of ordering, not a matched engine/native pixel comparison.

Full Render_Sprites traversal preserves bucket0 before5. No boss suppression was
found in sub_5758A or loc_57BF6. Corrected a misleading engine comment: flag$40
is multi-draw, while CLEAR bit2 selects screen coordinates in loc_1AE58.

### Shared arena-mask trial — 2026-09-24

User approved explicit activation for the GHZ/MTZ replica fights only; integration
and push require confirmation of the live trial. Common ArenaMaskState supplies
activate(width)/release()/advance(), captured inside SSZ runtime. The common
renderer reads the semantic ArenaMaskSource contract, never zone or boss IDs,
and draws into the current framebuffer before HUD.45-frame reversible envelope,
per-gameplay-frame noise, no gameplay RNG; native width is a render no-op.
The SSZ event coordinator derives activation/release from its existing lock owner.

Focused queued Java21/Maven run (absolute S3K ROM, DISPLAY=:0, native GL enabled):
TestArenaMaskState, TestArenaMaskRenderer, TestNativeArenaCameraFraming,
TestS3kSszGhzArenaHeadless, TestS3kSszMtzArenaHeadless, TestSszColdRouteCapture,
TestS3kAiz1SkipHeadless, TestSonic3kLevelLoading, TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils:115tests, zero failures/errors/skips. After making the
GraphicsManager entry package-private through an internal bridge and adding
event activation assertions, reran mask state/realGPU/both replica events/cold
route:31tests, zero failures/errors/skips, finished09:24:53BST. GPU checks cover
all five widths at1x/2x, offset viewports, capture FBO, centre identity, per-frame
noise and deterministic replay, and GL state restoration. Cold route retains
its ten full-registry rewind/replay spots with the additional presentation state.

Live shader recordings: campaign-20260924-{ghz,mtz}-live-static-{320,800},450
frames each from explicit checkpoint/40rings/neutral input. Full MP4 decodes pass.
All450 gameplay CSV rows per recording match pre-mask camera-fixed recordings
exactly. All300 common filmed frames preserve the central320 pixels exactly
before encoding; native320 entire frames are identical. Wide frame400 of both
fights inspected: mask remains active through knockback, HUD readable.
Shared implementation is also present as uncommitted trial code in separate
codex/ssz-arena-static-demo. Reconcile the common patch once at integration.
Full combined suite/guards, broader lifecycle/display-shader coverage and user
confirmation remain pending. No runtime feature commit or push claimed.

### Defeat/release review captures — 2026-09-24

GHZ and MTZ defeat/exit recordings now cover400/528/800. GHZ uses a declared
checkpoint(512,1992), Sonic+Tails,355rings; the authored input defeats the boss,
waits, then jumps onto the pad.1707frames, film350..1706, zero deaths; at1500
player(512,1382) has exited and the mask is gone. MTZ uses checkpoint(5888,1056),
soloSonic,355rings and the existing ssz-mtz-fight-chase input:2600frames,
film1600..2599, zero deaths. At2369 player(5888,81) is above the arena with no
mask. All widths share these positions; viewport-specific camera X retains
centred framing. GHZ wide1230/1400 retain the mask after boss defeat until the
pad releases bounds;1500 shows full width restored. Archives:
`campaign-20260924-ghz-checkpoint-exit-{400,528,800}` and
`campaign-20260924-mtz-unlock-{400,528,800}` under external SSZ task root.

Earlier campaign-20260924-ghz-unlock-{400,528,800} attempts replayed the320cold
route, diverged in earlier traversal and never reached the boss. They are failed
route attempts, NOT release evidence; wide cold-route traversal remains open.
Read-only independent review of shared mask/SSZ adapter found no actionable issues.

### Automatic bounds mask follow-up (2026-09-24)

The user replaced per-arena activation with a shared bounds-derived default.
Native camera/player words remain unchanged; presentation masks outside the
native view union, with world-relative fade history for newly covered visible
pixels and deterministic rewind. The 99-case focused geometry/GPU/presentation/
SSZ-route/required-S3K selection passes without skips. Positioned800 activation
footage is under `campaign-20260924-bounds-activation-800` in this zone's external
capture directory. SSZ additionally has GHZ800/400 and MTZ800/352 defeat/release
captures; all zero deaths. This does not close cold-route or cross-game breadth
obligations; combined delivery checks remain pending.


### Cold second-replica completion and upper-platform frontier (2026-09-24)

`routes/s3k/ssz1-sonic-tails-cold-upper-320.{script,bk2}` extends the prior7912-frame
middle route to11051 ordinary controller frames. It reaches the real MTZ replica,
delivers all eight hits, waits through its escape, uses the released pad and ends
at(5888,140),2rings,on the upper platform with player control free. No gameplay
position/speed/health writes occur. The BK2 author validates the compiled script
round trip. Both prior GHZ and MTZ bosses are absent at the endpoint.

`TestSszColdRouteCapture` now asserts both live killing hits and both exits, the
previous middle frontier, and sixteen full-registry capture/restore/forward spots:
3440,3818,4168,4400,5100,5600,6300,6459,7076,7460,8063,8750,9250,9850,10850,11000.
Queued Java21/absolute-ROM focused verification passed1test without skips. Its
initial launch ran before authoring finished and failed for a missing movie;
the completed input was then independently authored and the test rerun.

Production capture `campaign-20260924-cold-second-replica-exit-320` in the external
SSZ archive boots cold, records all11051 state rows and films frames8000–11050.
Every row matches the earlier input-authoring probe; zero deaths, full MP4 decode.
Frame11050 visibly places Sonic on the upper pad while Tails remains below.
This closes cold native-team reachability and the tested rewind spots for the
second replica/transport, not the rest of the act, wide input or native parity.


### 2026-09-24 — retracting spring side contact and route refresh

The upper cold traversal exposed an incorrect top-only spring contract.
`sub_1DD0E` uses the full sloped classifier, and `sub_46536` consumes the side
contact flag (d6 bit 16), not standing (bit 20). Both approach directions now
launch at ±$C00; top landing does not launch. `TestS3kSszCarriersAndSprings`
exercises these contacts at 320/800 and compares registered snapshots across
12 frames of launch/recoil. Its 23 tests pass with zero skips.

The fixed controller input for `TestSszColdRouteCapture` has been refreshed for
that behavior. It still reaches both replica defeats and the upper receiving
platform in 11,051 frames, now with 22 rewind/replay spots covering both spring
launches, traversal, fights, defeats and transport. The old 7,911-frame position
assertion is superseded by the upper middle walkway at (5157,1004). A combined
focused run of the route, traversal, camera framing, all-object round trips and
DEZ final-boss tests passes 1,484 tests with zero skips. This remains an upper
platform frontier, not cold completion of the final Mecha fight or act exit.

`ssz-bring-up/campaign-20260924-cold-spring-route-320/capture.mp4` films input
frames2750–3299 after cold boot. All3300 state rows match the authoring probe;
the file decodes completely. A positioned upper-spring capture was rejected as
route evidence: it does not carry the already-collapsed bridges from the earlier
pass and therefore takes the lower path. The cold route remains the authority.

The focused rewind-field/coverage/architecture guard selection also passed
78 tests, zero failures/errors/skips (`-Pguards`,
`TestRewindFieldDispositionGuard,TestRewindCoverageGuard,TestArchitecturalSourceGuard,TestRewindArchitectureGuard`).


### Exit presentation correction (2026-09-24)

The cold completion's Sonic spiral retained his facing flip. `loc_58016` now
clears both render flips and suppresses ordinary animation until the script
releases its mappings. `TestSszColdRouteCapture` asserts the spiral's flips.
The column overlap defect was a shared player-wrap omission: `loc_10C26` checks
current camera minimum `-$100` before masking Y. Preserving the parked `$7FFF`
allows the crumble owner's existing fallback to run at the intended time.
`TestPlayableSpriteMovement` covers suspended and re-enabled wrapping for S2/S3K.

The focused six-class selection passed239tests without skips, including the
complete320 Sonic+Tails route,37 registered replay spots and DEZ timeline reset.
Fresh native emulator frame467920 corroborates the unmirrored pose and intact
column; this is visual comparison, not frame-perfect parity. Replacement clip:
`ssz-bring-up/campaign-20260924-exit-corrected-320/capture.mp4`. See the campaign
audit for references, rejected priority changes, exact command and limitations.
Widescreen/other-character exit breadth remains an inherited matrix obligation.


## Solo Sonic replica route (2026-09-25)

`ssz1-sonic-solo-cold-replicas-320.{script,bk2}` now preserves11200 native320
controller inputs from cold SSZ1 through both eight-hit replica bosses and
escapes, no follower/donor or position/ring/health seeds, no deaths. The MTZ
attempt using a target16px left of the observed boss retains all28 incoming
rings. This is controller strategy only; no runtime changes. The first
team-movie reuse died13086; copying its carrier and cloud timing does not prove
a solo completion. New releases traverse both carriers and the upper middle
platforms through production contact.

Fresh capture at`$HOME/Videos/OGGF/ssz-bring-up/campaign-20260925-sonic-solo-mtz-clear-320/`
matches all11200 authored rows on20 state fields. The MTZ video spans8900–11199,
2300 frames at60fps (38.333333s), with full decoding and killing-hit still inspection.
This is engine route/presentation evidence, not native pixel parity.
`TestSszSoloColdRouteCapture` has42 whole-registry restore/45-input replay windows,
all eight hits per boss and both escaped owners absent. Focused verification on
`33a5b0e21` plus these test additions passed: 1 test, 0 failures/errors/skips,
30.902s Maven, using Java 21, DISPLAY=:0 and the explicit root S3K ROM. Command:
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestSszSoloColdRouteCapture test`. The final Mecha/DEZ handoff remains under authoring, as do
other outstanding breadth/lifecycle/native obligations.

## Solo Sonic complete cold route (2026-09-25)

On `15d712413` plus test/input additions, solo Sonic now completes SSZ1 and
loads DEZ1 in 19,845 controller inputs, without deaths, follower, donor or
position/ring/health seeds. Canonical input: `ssz1-sonic-solo-cold-complete-320`.
Its first 11,200 inputs retain the independently certified replica route.
Upper carriers/cloud platforms and both remaining transport pads use ordinary
contact. All eight final Mecha hits, collapse, results and the actual DEZ load
are observed. Final input 19,844 loads zone 11/0 at (48,2476).

`TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory`
adds 41 whole-registry restore/45-input forward replay windows and verifies
outgoing live history followed by a fresh DEZ timeline. Alongside the shorter
42-window replica case, focused verification passes 2 tests, 0 failures/errors/
skips, 42.345s Maven: Java 21, DISPLAY=:0,
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestSszSoloColdRouteCapture test`. These are test/input changes; focused
verification exercises the complete affected route and all new assertions.

Fresh capture `$VIDEO_ROOT/ssz-bring-up/campaign-20260925-sonic-solo-clear-320-v2/`
replays 21,286 inputs including an extended neutral DEZ tail. All 18,286 author
rows match 20 recorded state fields; no deaths/follower. Video 16,276–21,285
has 5,010 frames at 60fps and fully decodes. Attack/defeat stills 17,506 and
18,285 were inspected. This proves engine route/presentation, not native parity.

Fixed pursuit controllers repeatedly died; their failure did not justify a
runtime change. Snapshot-forked controller trials (12 policies, 180-input
horizon, 30-input commitment) found a surviving attack sequence. Restoration
includes external held-button history; the selected buttons are replayed from
fresh boot for certification. An initial temporary serializer dropped B/C from
the cold prefix (first mismatch 426, death 2556); preserving all recorded buttons
repairs that authoring error. No physics or boss behavior changed. Tails solo,
remaining width/donor/lifecycle products and native whole-scene checks remain open.

## Tails opening flight and ceiling correction (2026-09-25)

Cold `ssz1-tails-solo-cold-flight-320` contains 2,556 ordinary inputs with no
position/ring/health seeds or follower. At 2,345 the old runtime stalled at
(1205,3087), camera minimum -256, zero upward velocity; it died at 2,545.
The same input after the signed `loc_14892` correction reaches (1317,3007) at
2,345 and (2085,2954) at the end, without death. This is an opening-route
frontier, not Tails' whole SSZ completion. Five registry replay windows cover
flight activation, ascent and the raised-platform crossing.

Fresh video `$VIDEO_ROOT/ssz-bring-up/campaign-20260925-tails-flight-ceiling-fixed/`
has 331 frames at 60fps, fully decodes, and its 2,345 still was inspected.
The ROM's signed branch is the authority; this capture is engine presentation,
not a matched native trajectory. Existing Sonic solo routes still pass.

The short flight regression passed all five windows: 1 test, no failures/errors/
skips, 24.128s Maven with Java 21 and explicit S3K ROM. See the campaign audit
for the exact command and broader final-flight compatibility checks.

## Tails route continuation and widescreen approach (2026-09-26)

Native320 solo Tails completes the cold route in17,670 inputs, actual DEZ load
at17,669, without death, follower or gameplay seeds. The shorter9,675-input
replica route includes both eight-hit fights and both gated transports. New
`TestSszTailsColdRouteCapture` covers53 replica and47 upper/final registry replay
windows and live-history isolation at the actual DEZ load; all7 cases pass
without failures/errors/skips, including the five-width cold approach regression.
Independent captures match all19 gameplay-state fields (6,100 GHZ,9,675 MTZ,
15,380 final author rows), and all three MP4s fully decode. Engine presentation
stills were inspected; native whole-scene parity remains open.

The800 replay exposed an actual missing GHZ entry, not a route-input certification:
preliminary bounds lacked the camera projection used by the equality gate.
The candidate fix and passing five-width cold approach regression are recorded in the
[campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md). Wide cold completion and broader integration validation remain separate.


The800 variant now preserves9,683 cold inputs through both replicas and their
receiving pads. Its53 registry windows also pass; native320 and800 variants
pass together (2 cases, no skips) after adding the width parameter. The separate
five-width entry and native complete-route cases are unchanged. Fresh800 GHZ
and MTZ movies include defeat/release, exactly match the independent probe rows,
and fully decode at60fps. The final wide Mecha encounter remains open.


## Final Mecha widescreen geometry correction (2026-09-26)

A cold800 route exposed a35px shift of Mecha's native attack box, missed by all
five aligned checkpoint starts. `coldMechaEntryRetainsItsNativeWorldBox` fails
before the fix and passes at320/800 after it, including rewind over allocation.
The final Events_bg+$06 flag now supplies the existing widescreen-only fixed-X
camera policy at native$19A0; the shared camera algorithm and player bounds
are unchanged, and vertical launch behavior remains independent. Merely enabling
projected bounds was tested and rejected because ScrollHoriz's deadzone branch
performs no clamp. The short five-preset regression anchors the box against ROM
world coordinates rather than only the current camera. Full wide fight/launch
completion is still being authored and must be verified separately.


The final lock regression additionally replays pre-activation and allocation
windows at native320/800. Both full Tails variants now pass:17,670 inputs at320
and16,893 at800, all eight Mecha hits, no deaths, actual DEZ1 at(48,2476), cleared
SSZ X-lock policy and isolated live history. Native has47 upper/final replay
spots; wide has49, alongside the existing53 replica spots each. The final
four-case entry/full-route command passes with no skips (1m13s Maven).
The fresh wide movie fully decodes at60fps and matches all14,587 author rows;
remaining donor/roster/lifecycle products and native presentation parity stay open.


### Native fight presentation corroboration (2026-09-26)

Fresh read-only BizHawk2.11 movie playback and current `3d0c32eec` engine
captures are recorded in the campaign audit under "SSZ native fight presentation
survey". Both source hashes, frame windows, actual rendered samples and limitations
are preserved externally in`ssz-bring-up/native-fights-20260926`. This is
phase-based corroboration with different inputs, not whole-scene pixel/timing parity.

GHZ/MTZ Eggmobile and Mecha-head rendering are visible; native body priority
buckets4/3 and art`$052E` agree with production. Native cloud overlap is retained.
The fresh19,732-frame engine team capture matches every state row of the accepted
corrected route and has no deaths. Hyper Sonic kills the original MTZ boss before
it completes descent, so later normal orb/laser phases still need another native
observation. Act1 Mecha and defeat retain`$A3F4`/bucket5.


### Normal MTZ phase follow-up (2026-09-26)

The later-phase observation gap above is superseded by two declared native
checkpoint experiments: Sonic covers0/A/C/E, including954 laser-phase frames;
Tails covers0/2/4/6/8 with all eight HP and seven orbs intact. The campaign audit
records their setup and limits. Native laser art`$A41F`, buckets5/2, subtypes0/2,
eight-frame trailing delay and46-frame pair spacing corroborate the implementation,
except for a now-corrected missing high tile priority and trailing subtype ordinal.
The production three-pair regression reproduces the priority omission before the
fix. Combined candidate encounter, cold-route and S3K gates pass92 cases with no
skips. Strict aligned pixel/timing parity and remaining matrix breadth stay open.


## Cold bridge checkpoint and repeated respawn (2026-09-27)

`TestSszBridgeCheckpointCapture` starts SSZ1 normally, follows the arrival with
ordinary Right input and waits for the bridge to publish checkpoint1. It never
injects a checkpoint or saved position. It then invokes the production pit-death
entry twice, allowing GameLoop to perform each actual reload. Each restart must
retain `($140,$C6C)`, skip the arrival, rebuild the SSZ runtime, isolate populated
live rewind history and admit the already-extended bridge during ordinary movement.

The25 configurations use the actual five `WidescreenAspect` presets
(320/352/400/528/800): native Sonic, Tails and Sonic+Tails at each width, plus
S1 Sonic and S2 Sonic+Tails at each width. Older matrix widths512/640 are not selectable
presets in the current capture/runtime configuration; this check derives its
values from the production enum and asserts the resolved width after reload.
It verifies donor identity and spindash capability after each reload. Knuckles'
Act2 entry, mixed/max/duplicate teams and native framebuffer matching remain separate.

The red test reproduced the bridge failure in all20 supported native/S2 rows
and five initially unsupported S1+Tails rows. The latter were replaced by
menu-supported S1 Sonic solo; all final rows assert launch-profile sanitization. The
checkpoint index survives death, but its separate used-post activation record is
empty. `Obj_SSZCutsceneBridge` tests `Last_star_post_hit` before dispatching to
`loc_4501A`; our bridge incorrectly tested the activation record and waited at
placement X+$C0 for a cutscene which correctly no longer runs. Its selector now
reads the restored checkpoint index, matching the ROM and SSZ screen init.
No shared respawn, physics, camera or rewind behavior changes.

Execution and delivery evidence is recorded in the campaign audit.

## 2026-09-27 MTZ orb touch ownership

The local orb callback now ignores ordinary harmful `$87` and unlisted special
`$DA` contacts, while `$C6` records the native player touch. A separate
`PoweredScreenAttackSpecial` hook implements `HyperTouch_Special`'s OR3. No shared
touch algorithm or native Player2 Hyper effect changes. The encounter test drives
an actual launched orb to onscreen `$C6`, invokes the production powered-attack
port with its real frozen response list, and captures/restores/replays the pending
property through the pop. Ordinary callback cases are explicit boundary stimuli;
this is not a new controller-only Hyper route. The seven nonfatal-hit orbit counts
are checked through the existing laser and defeat encounters.

Native read-only capture `native-fights-20260926/run1` shows orbit counts7→0 at
frames455111→455524, with the last launched orbs popping455572 and gone455573,
before ship deletion after455755. The previous visible late-orb discrepancy claim
is withdrawn; arbitrary freed/reused SST behavior remains an inherited gap. The
excerpt is `$VIDEO_ROOT/ssz-bring-up/campaign-20260927-mtz-native-orb-audit/capture.mp4`.
This is phase/ownership corroboration, not matched engine/native trajectory parity.

The correction invalidated the paired cold fight's old inputs (death 9435;
matched base passed). The final controller-only repair changes 309 overlapping
input rows before the upper transport endpoint at 11050. Its 11,051-input replica
route and 19,457-input full clear both pass. The latter reaches actual DEZ at
`(48,2476)`, 35 inputs earlier than before. All later traversal inputs up to that
load remain unchanged. The original 22/37 whole-registry replay spots, spiral
orientation and live-history isolation checks remain intact. Solo Sonic and Tails
routes also pass unchanged. No position/ring/health/emerald seeds are added.

Final engine clips (60fps, 640x448, zero deaths, full decode checked):
- `$VIDEO_ROOT/ssz-bring-up/campaign-20260927-mtz-orb-touch-final-320/capture.mp4`
- `$VIDEO_ROOT/ssz-bring-up/campaign-20260927-orb-fixed-paired-clear-final-320/capture.mp4`

The latter continues with 143 neutral inputs after the fixture to show DEZ's
entrance. Encounter + paired-route validation passes 21 cases; unchanged solo
route validation passes 14. These are focused checks, not full-suite certification.


### Collapsing-column parent lifetime (2026-09-28)

The cold800 Sonic+Tails continuation exposed a rewind capture failure while
column debris remained visible after its parent parked and left the active
world. `loc_44C32` still samples parent Y on the zero-delay frame; subsequent
negative-delay entries use only local gravity. The engine now releases the
unused parent reference after that final sample, preserving forward movement.

`TestS3kSszTraversalPlatforms#fallingDebrisRewindsAfterItsColumnLeavesTheActiveWorld`
reproduces the original unregistered-parent failure at800px, then verifies two
whole-registry restore/forward cycles after retirement. The existing recreation
check uses ROM `word_46618` delays to distinguish hanging pieces, whose links
must survive, from released pieces. The delay-one row also checks the final
parent-Y read before detachment. These are short independent checks; the full
800px pair route and visual evidence remain in progress in the campaign audit.


### Cold 800px pair through the first replica (2026-09-28)

`TestSszWidePairedColdRouteCapture#coldFirstReplicaAndTransportReplayWithRetiredColumnDebris`
drives the committed `ssz1-sonic-tails-cold-first-replica-800` BK2 from ordinary
SSZ entry for 9,914 inputs. Sonic and Tails reach the first receiving platform
at (512,1420), after eight GHZ replica hits, with 46 rings and zero deaths.
Forty whole-registry checkpoints each run two restore/forward cycles across
arrival, bridge, traversal, released column debris, preliminary/final arena
bounds, fight, escape and transport. The focused test passed with no skips.

A separate uninterrupted GameplayCaptureTool replay matches all recorded fields
of all 9,914 CSV rows. Its 60fps video covers source frames 6000–9913, including
defeat and transport, under `~/Videos/OGGF/ssz-bring-up/campaign-20260928-paired-cold-first-replica-800`.
This closes that pair-route prefix and its listed rewind windows; the complete
800px pair route, other breadth and synchronized native visual matching remain
open. No emulator parity is inferred from the engine movie.

## Arrival camera and beam presentation (2026-09-30)

`TestS3kSszArrivalHeadless#presentedTitleCardPreservesTheScreenInitCameraLock`
checks real GameLoop title entry at 320/800 after the initial screen/object pass,
with only the already-initialized presentation provider substituted. Title entry
must retain the scripted `$F49` camera and the first rise remains 8px. A forced
player snap previously clamped the locked camera to `$BC0`.
`releaseCameraTracksTheRomSwingAndSettlesAtTheArrivalFloor` checks the native
`$BA8` swing apex and `$BC0` landing camera at both widths. The small upward
swing followed by downward settling is intentional: `Obj_57D64` and
`MoveCameraY`, corroborated by the comparison-only `hpz` recording.

`TestTeleporterBeamRendering` verifies the two expanded beam columns and
odd-frame flicker. `Render_Sprites` at `loc_1AEE4` branches to `loc_1AF1C` when
the multi-sprite main `mapping_frame` is 0; it does not draw mapping0 (the pad).
The beam now honors this, removing the floating second pad. Existing arrival
restore/replay checks retain their character/team/viewport coverage; strict
whole-scene pixels and outstanding donor/route products remain inherited gaps.

Validation on develop base `27edb95989b90ae53663f13ceee463690c4cd456`
plus this working change:

- Java 21 queued focused selection `-Dtest=TestS3kSszArrivalHeadless,TestTeleporterBeamRendering`
  passed. The final combined run includes 25 arrival cases and 1 beam case,
  all passing without skips; arrival covers the existing character/team/viewport
  restore/replay matrix. AIZ skip, level loading, bootstrap and decoding also
  passed without skips.
- `python3 tools/testing/run_categories.py --base 27edb95989b90ae53663f13ceee463690c4cd456 --preflight`
  passed after temporary official Lua 5.4 and PowerShell tools were placed on
  `LUA_BIN`/`PATH`; no system installation changed.
- The same command with `--run` selected 2,952 ordinary classes plus guards.
  Completed ordinary lane: 25,200 tests, 12 failures, 162 errors, 126 skips,
  1,844.91 seconds. Completed guards: 672 tests, 0 failures/errors/skips,
  500.66 seconds. This is a failed broad run, not a green delivery.
- 159 errors share unsupported GLSL 4.10 during native initialization.
  Matched queued `-Dtest=TestAiz2ForestRingCameraGeneration,TestS3kMovingCameraPresentation`
  checks on the unchanged detached baseline and current code reproduce both
  representative initialization errors. Native captures/pixel acceptance remain
  unverified on this Mac.
- Bounded baseline/current selections of the failing mod/build and audio classes
  reproduce 11 failures and 3 errors: macOS `base64`, sample conversion/build,
  missing `/usr/bin/bash`, Bash shell syntax and secure-directory traversal.
  The selected classes are `TestPhase2SampleModIntegration`,
  `TestPhase3SampleCharacterIntegration`, `TestPhase3StandaloneSampleIntegration`,
  `TestSamplePlatformerIntegration`, `TestProjectScaffolder`,
  `TestOverrideResumeReferenceBundle`, `TestS1AudioParityCli`,
  `TestS1OverrideResumeAudioOracle`, `TestS2OverrideResumeAudioOracle`,
  `TestCompleteRunAudioCli`, and `TestS1GameplayAudioTimelineCli`.
  Both use `python3 tools/testing/maven_queue.py -Dmse=off -Dtest=<selection> test -q`
  with Java 21 and the absolute root S3K ROM property. No engine changes were
  made to repair these unrelated failures.
- With the same PowerShell PATH as the broad run, matched baseline/current
  `-Dtest=TestModApiHookPolicy#pinRenameRequiresDescriptorAndCurrentRequiresBothCompanions`
  commands each reproduce 1 failure, 0 errors/skips. Without PowerShell this case
  skips, which is why the first bounded selection alone could not attribute it.
- The 126 ordinary skips include legacy hardcoded `s2.gen` lookups, unavailable
  KiS2 lock-on dumps, and opt-in/native prerequisites. Root ROM identities were
  discovered by the runner; no ROM was renamed or copied to satisfy a test.

The rejected approach is suppressing the entire post-rise camera swing: native
`hpz` rows corroborate its `$BA8` apex and `$BC0` landing, so removing it would
change shipped-ROM behavior. Only the forced title-entry snap and the extra
beam pad were corrected. Diagnostics are consumed and acknowledged; the
baseline checkout is temporary and removed after attribution.

## SSZ presentation fixes (2026-09-30)

Direct `develop` delivery starts at `332eaadf44482d4e5358be44783c6bbdf89f7188`.
Issues are fixed, verified and pushed individually in the requested order. The
combined change-based plan selects 2,952 classes through fallback classification
and an unrelated untracked S2 movie. These localized contracts use proportionate
focused validation; no full-suite or whole-scene parity claim is made. Java21,
Lua5.4 and PowerShell preflight passes with the tools in
`/private/tmp/openggf-validation-tools`.

1. **Opening Death Egg:** `loc_65B24` already has a stationary frame `$C` mask
   child, but SSZ lacked SAT-mask registration. Enable the existing pass rather
   than change the correct art priority or force all terrain in front.
   `TestSszDeathEggCutscene.risingEggPresentationIsClippedAtTheStationaryBackgroundMask`
   submits ROM art and the real child through CPU presentation and asserts visible
   egg pixels above `$C38` and none below. Existing cutscene graph recreation and
   whole-registry forward replay cover the mask lifetime. Focused selection:
   `TestSszDeathEggCutscene,TestS3kSpriteMaskSupport,TestGraphicsManagerSpriteSatReplay,TestSonic3kZoneFeatureProvider`
   plus the four mandated S3K stability classes, using queued Maven `-Dmse=off`
   and the absolute locked-on ROM path. **78 tests passed, no skips** on
   the candidate based on `332eaadf`, before issue1 delivery.

Inherited gaps: this local presentation change does not certify native visual
parity across every viewport, donor, character or team. The existing route and
compatibility obligations above remain open.

2. **Opening bridge:** `sub_45026` draws three frame-zero sections at
   X-$40/X/X+$40, with art priority bit15. Previously only the middle mapping
   drew against the correct full-width solid.
   `TestS3kSszKnucklesBridgeHeadless.allThreeSectionsDrawAcrossTheSolidSpanDuringExtensionAndReplay`
   checks72 ROM-backed tiles, the192px span, art priority and96 extension
   passes plus restore/replay at320/800. Queued Maven selection:
   `TestS3kSszKnucklesBridgeHeadless,TestS3kSszLifecycleProduction`.
   Candidate based on issue1 `d3e95d1486`: **5 bridge tests and3 lifecycle
   tests passed, no skips**. The first run exposed a test assertion using the
   mapping-piece priority instead of the object art occlusion mask; corrected
   the oracle field and reran the5 bridge tests. Production span already passed.

Native capture limitation: the skill's `exec:java` invocation fails macOS
first-thread validation. A forked `exec:exec` JVM with `-XstartOnFirstThread`
reaches GL, but `HeadlessGameBoot` requests2.1 while shaders require4.1, failing
before frames. No screenshot parity is claimed; unrelated tool changes were
not included.

3. **Elevator hang:** restore `loc_45400`'s `andi.b #$FC,render_flags(a1)`.
   `$E9` has distinct pose art, so preserving the incoming left
   render flip reverses the intended orientation. Do not modify Status_Facing.
   `TestS3kSszCarriersAndSprings.elevatorHangUsesNativeMappingFlipsAndKeepsIncomingFacing`
   checks both incoming facings, both native hang frames, real player mapping/DPLC
   presentation and render-flip restoration. Existing elevator whole-world replay
   and the carrier/spring interaction selection run alongside it. Candidate based
   on `451daa47d5`; queued `-Dtest=TestS3kSszCarriersAndSprings` with the absolute
   S3K ROM path. **23 existing carrier/spring cases passed** in the first
   run (also two accidental UP/DOWN variants); the left-art flip-bit assertion
   in the new test was wrong and failed. Reading Map_Sonic `$E5`/`$E9` showed
   distinct art with unflipped pieces. Corrected that oracle and restricted the
   cases to LEFT/RIGHT: **both passed, no skips**.

4. **EggRobo attachment:** body anchors now read the object's live x/y owner.
   Base `currentX/currentY` were stale despite `getX/getY` moving, so the gun
   departed from the visibly stationary body. Native art faces left unflipped;
   child offsets and mapping flips now use the same `!facingLeft` convention.
   Gun `$30/$32` history starts zero; `sub_91930` then falls back to live Y during
   launch, records the hover lag, and is skipped during the firing cooldown.
   Flame Y follows `Refresh_ChildPositionAdjusted`, without gun history.
   `TestSszEggRoboPresentation` exercises launch movement in both orientations,
   verifies ROM-backed body/child drawing, and checks recreation of the graph.
   Queued selection `TestSszEggRoboPresentation,TestS3kSszEggRobo`, absolute S3K
   ROM path, candidate based on `012acaaee8`. **8 existing cases and4 new
   presentation cases passed, no skips**. Turning/mirrored muzzle and shot
   direction are included. Initial direct-drive launch setup omitted cached
   object camera bounds; supplying those made the launch run. A rejected oracle
   compared emitted whole-tile positions to nontransparent pixel FrameBounds;
   replaced it with independently decoded ROM mapping geometry.

   Issue4 rewind follow-up on `39645745aa`: the broader column-recreation check
   exposed EggRobo dynamicSpawn being reset to stale base currentX/Y by
   AbstractBadnikInstance.restoreRewindState. A bounded matched check of
   `TestS3kSszTraversalPlatforms#restoringPastTheDebrisDeletionRecreatesThePiecesAndTheirColumnLink`
   failed on that candidate and passed with the four pre-fix EggRobo sources from
   `012acaaee8` (same bridge candidate/setup, no skips). EggRobo now resyncs its
   own restored x/y after the base restore, and the presentation recreation test
   compares object-manager state immediately. Follow-up: queued `TestSszEggRoboPresentation,TestS3kSszEggRobo,TestS3kSszTraversalPlatforms`
   within the bridge selection: **21 cases passed, no skips**. Four new bridge
   cases still failed on a test-harness reference after recreation; handled in issue5.

5. **Diagonal bridge collapse:** `loc_44EBA` retains the shrinking solid but
   does not submit the intact mapping. Eight `loc_45052` pieces now replace it,
   copying flips and art bit15. Native delays6..48 were encoded in
   `ObjectSpawn.renderFlags`, whose canonical constructor masks to two bits;
   store that synthetic child's delay in its16-bit rawYWord metadata instead.
   The same fragment routine serves flat bridges, whose four pieces now retain
   delays/flips and replace the intact image as `loc_44D22` requires.
   `TestSszBridgeCollapsePresentation` checks that the fragment set exactly
   reproduces every intact ROM tile/position/flip, then staggers the falls,
   recreates deleted fragments and compares all registry keys after forward
   replay. Four independent cases cover both flips and both collapse sides.
   Queued selection `TestSszBridgeCollapsePresentation,TestS3kSszTraversalPlatforms`,
   absolute S3K ROM path, candidate based on `39645745aa` plus EggRobo restore
   follow-up `f78333ecd6`. **9 existing traversal cases passed** in the combined
   run; after repairing the component harness, **4 new cases passed, no skips**.
   Initial tile-set failures on rightward/mirrored collapse exposed the missing
   upper-word preservation: `loc_44E80` tests d6.l but adjusts d6.w, so the low
   word must continue past zero along the slope. Rejected Java int arithmetic
   oscillated between two heights. Initial replay setup had not registered the
   lazy insta-shield, and reused the retired dynamic bridge after restore;
   completing registration and resolving the recreated bridge fixed the harness.

6. **Rotating player pose:** `loc_460A6` writes the pose table's low two render
   flags and mapping frame, without changing Status_Facing. The helper now does
   the same; changing Direction left object-owned render flips stale.
   `TestSszRotatingPlatformPresentation` independently reads all 256 angle rows
   and Map_Sonic geometry, verifies real player mapping/DPLC drawing for both
   incoming facings, and restores the displayed pose. Four production-component
   cases drive both the post and carrier through a complete native rotation and
   check position, frame, flips and retained facing. Existing carrier interaction
   and whole-registry replay tests remain in the selection. Candidate based on
   `4ec32623eb`, queued `TestSszRotatingPlatformPresentation,TestS3kSszCarriersAndSprings`
   with the absolute S3K ROM path. **31 cases passed, no skips**. Native visual capture remains
   unavailable as recorded above; compatibility breadth remains inherited.

Issue4 architecture follow-up: the all-guards run rejected the new EggRobo
restore override added in `f78333ecd6`. Retire that override rather than adding
a baseline exception: synchronize inherited badnik currentX/Y with the live
native x/y after movement, so standard BadnikRewindExtra restores matching
spawn metadata. Native movement/render anchors are unchanged. The immediate
object-manager recreation and broader column replay checks verify the standard
restore. The final combined EggRobo/traversal/background selection ran63
cases: **61 passed, no skips**; both failures were the new transition test's
800px session/viewport setup, repaired separately. All21 EggRobo/traversal
cases passed, including the new standard badnik coordinate assertions. Fresh
RewindArchitectureGuard: **4 checks passed, no skips** on the final candidate.

7. **Background black flash:** `loc_5799A` retains the outgoing plain framing
   during the entering-cloud redraw. The engine replaces a whole layout cache,
   so its source window must stay with that displayed framing until cloud
   deformation begins. Switching the source to `$1C00` early sampled blank rows
   with the plain Y. Displayed mode and native HScroll words now live in captured
   SSZ state, avoiding handler caches from a future frame on rewind re-render.
   `TestSszBackgroundTransitions` drives actual parallax/window selection and
   CPU descriptor-cache generation at both `$800` and `$F00` boundaries, entering
   and leaving, at320/800. ROM pattern pixels confirm the viewport has background
   art each frame; restore re-render checks identical scanline words, source,
   VScroll, tile bytes and unchanged runtime state after visiting future modes.
   On baseline `05b5e35863`, two transition-only cases failed at pass1 with
   routine4/sourceX7168/scrollY2399 and zero visible samples; two independent
   rewind cases failed on restored scroll words. Candidate queued selection:
   `TestSszBackgroundTransitions,TestS3kSszBackgroundLayout,TestS3kSszBackgroundClouds,TestS3kSszScrollBands,TestSszZoneRuntimeState,TestS3kSszLifecycleProduction,TestSszDeathEggCutscene,TestSszAct2BackgroundPriority`
   with the absolute S3K ROM path. **42 cases passed, no skips**. This is CPU background-sampling
   evidence; the native staged nametable timing and whole-scene GL/native capture
   remain inherited gaps, as does broader donor/team compatibility.

Combined delivery validation remains proportionate and focused. The unchanged
runner plan against initial `332eaadf44482d4e5358be44783c6bbdf89f7188` selects
2,956 ordinary classes plus guards: its fallback treats the SSZ provider/runtime/
scroll files as unknown, and also sees the unrelated untracked S2 movie. The
changed implementations are SSZ-local presentation owners; the direct ROM-backed
regressions, production interactions, state recreation and affected background
consumers bound their failure modes. The ordinary full suite was not run.
The queued `-Dmse=off -Pguards test -B` run selected all87 guard classes:
**672 checks, 3 failures, 1 error, no skips**. The EggRobo custom-restore failure
was corrected in `6e19d6a3ef`; the fresh `TestRewindArchitectureGuard` run passed
all4 checks. The remaining results were reproduced with all13 task-modified
production files temporarily loaded from `332eaadf4448` in this same develop
checkout, then restored in a try/finally block (no branch switch or worktree):

- `TestObjectPhysicsStandardizationGuard#productionObjectPhysicsStandardizationHasNoUnapprovedViolations`:
  the same existing LRZ flame touch-profile violation on baseline and candidate.
- `TestObjectUpdateClockTerminologyGuard`: the same120-second subprocess timeout
  and closed-stream error on baseline and candidate.
- `TestBuildToolingGuard#releaseGateToolsShouldRejectCorruptAndChangedEvidence`:
  unchanged Python checkout-path normalization assertion. The isolated
  `python3 -m unittest test_release_trace_collection.CollectTests.test_failure_messages_and_report_payloads_have_checkout_paths_normalized`
  from `tools/testing` reproduced its `/var` versus `/private/var` mismatch;
  the test and dependencies have no diff against the pre-task base.

Five strict SSZ slices were checked with queued `-Dmse=off -Ptrace-segments
-Dtest=TestS3kSonicTailsHpzSegmentTraceReplay,TestS3kSonicTailsHpz2SegmentTraceReplay,TestS3kSonicTailsHpz3SegmentTraceReplay,TestS3kTailsFullChainHpzSegmentTraceReplay,TestS3kTailsFullChainHpz2SegmentTraceReplay`,
absolute S3K ROM path, `test`. These historically named HPZ fixtures contain SSZ;
the fixtures named SSZ are Death Egg and were not substituted. Both candidate
(`05b5e35863` plus the background fix) and matched pre-task production sources
ran **5 cases, 5 failures, no skips**, with identical error counts and first errors:

| Fixture class suffix | Errors | First frame/field | Expected / actual |
| --- | ---: | --- | --- |
| SonicTailsHpz | 2150 |119 `tails_x` | `$7F00` / `$0100` |
| SonicTailsHpz2 |1140 |0 `rings` |150 /0 |
| SonicTailsHpz3 |283 |0 `rings` |177 /0 |
| TailsFullChainHpz |617 |1337 `g_speed` | `$0000` / `$0001` |
| TailsFullChainHpz2 |948 |0 `x` | `$0640` / `$0100` |

The final standard EggRobo capture-coordinate follow-up changes no native
movement or render anchor; its object/recreation checks passed separately.
No trace frontier moved. No full-suite pass or GL/native visual parity is claimed.

Final background selection on `6e19d6a3ef` plus issue7: the combined63-case
run passed all21 EggRobo/traversal cases and the existing background consumers.
Two new800px cases exposed test-session reuse that retained a320px camera;
resetting the session and asserting its width repaired the harness. The final
queued `-Dmse=off -Dtest=TestSszBackgroundTransitions` with the absolute S3K ROM
path passed **all4 transition/re-render cases, no skips**, at their asserted
320/800 widths. No production correction was needed for those harness failures.


### Route drawing policy follow-up (2026-10-09)

The affected cold-route/checkpoint JUnit checks now simulate every input and
service queued render work every tick, with drawing at snapshot/replay and
playable/load checkpoints. Complete forward/replay branches stay fully drawn;
discarded screenshot readback is omitted. Their normal result establishes route
behavior and these drawn checkpoints, not presentation on every intervening frame.
For every-frame drawing use `-Dopenggf.tests.drawEveryFrame=true`. The bounded
state/pixel controls and their limits are described in the
[headless testing guide](../../../guide/contributing/headless-testing.md).
This performance change supplies no new native reference, full-route visual
certification or missing viewport/donor/character coverage; inherited gaps remain.

### Current-base route and EggRobo retirement follow-up (2026-10-13)

Task tree `.worktrees/ai-s3k-finish-ssz`, branch `bugfix/ai-s3k-finish-ssz`,
pinned develop base `5e2f51f07917d5eff8ac120007dac55327f93f32`. Java 25 and
the original absolute-path locked-on ROM are used; independently verified
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.

The bounded baseline selects the native Sonic+Tails and solo Sonic replica
routes, plus the two Tails replica widths. It completes four cases with three
failures, zero errors/skips: team death at input 7311, solo death 7671, and
the 800px Tails object-manager replay at 4018. Native 320px Tails passes.
The failing wide replay retains gun/flame children whose parent is absent
from the captured world; forward execution continues using the old Java
parent while restored execution resolves their parent IDs to null and retires
them, changing later slot allocation.

Native `Sprite_CheckDeleteTouch` branches to `loc_85094`, which sets parent
status bit 7 before scheduling `Delete_Current_Sprite`. Both EggRobo children
end in `Child_Draw_Sprite`, which reads that bit. Manager offscreen retirement
had removed the body without publishing the signal. EggRobo now uses the
existing `onUnload`/`destroyRespawnableOffscreen` contract, as Chainspike already
does for the same native tail. No shared allocator, slot order, child null
tolerance, snapshot format, movement, timing or input owner changes.

The independent two-facing regression removes the body through the real
object manager, checks both children retire, restores the complete live graph
and forwards it, then captures/restores the retired-parent boundary and checks
orphan retirement repeats. Both cases first fail on the missing native signal
(two failures, zero errors/skips), before the production correction.

Separate uninterrupted controller-only observations reproduce both Sonic
deaths without rewind. The team is already falling by 7050 and its old 7077 jump
does not recover the carrier route; the solo route also runs into the lower
pit. These are current navigation frontiers, without evidence of a new ROM
movement defect. Tails' full input reaches DEZ with the actual live main player
still at (0, 0) and `hasPendingFreshLevelTransitionBoundary=true`; session/current
player identity is equal, so a stale cached sprite alone does not explain the
old 48/0 endpoint failure. Shared fresh-load assembly is owned by the integration
lane; destination assertions remain unchanged.

Commands use the prefix `python3 tools/testing/maven_queue.py -Dmse=off` and
`-Ds3k.rom.path=<verified absolute ROM>`:

- Baseline: `-Dtest=TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport,TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches,TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters test`.
- Independent red, with `--lean`: `-Dtest=TestSszEggRoboPresentation#parentRetirementSignalsBothChildrenAndRestoredGraphFollowsItsLiveOwner test`.

Candidate verification completes 79 tests with zero failures, errors or skips:
`-Dtest=TestSszEggRoboPresentation,TestSszEggRoboClockGates,TestS3kSszEggRobo,TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`.
The original absolute S1/S2 ROM paths are also supplied for the loading checks;
both matching `TestSonic3kLevelLoading` classes execute. This includes both
Tails replica widths and the formerly failing 800px replay at 4018.

The maintained `GameplayInputBranchTool`, with full scene drawing on the
corrected owner, reproduces team death 7311 when the jump starts at 7060.
Earlier controller jumps from the same cold-replayed 7030 prefix reach the
monitor ledge alive; a 7030 jump actually catches the bar at 7087. These are
exploratory branches, not independently replayed complete routes. Navigation
repair and the separate shared DEZ assembly correction remain open. No act,
donor/roster/viewport product, native presentation or complete route
certification is inferred from this bounded selection.


## 2026-10-13 controller candidates and native entry ownership

The lane `bugfix/ai-s3k-finish-ssz` at `bd7f58a17` combines the already
qualified `de5e291a5` EggRobo retirement with the integration lane's fresh
assembly commit `8438d7040` (local cherry-pick `bd7f58a17`). New controller
fixtures and endpoint checks remain uncommitted while their focused world
replays wait for shared Maven admission. Build-only `test-compile` completes
with exit 0 in23.009 seconds; this is compilation, not route qualification.

The maintained `GameplayInputBranchTool` and `SszBossInputAuthorTool` use a
coherent compiled snapshot copied under `maven-worktree.lock`. Standalone
exploration is bounded to1 GiB and2 active processors with surfaceless EGL.
It supplies controller inputs only: no position, health, rings, native clock
or trace state is written after the cold boot. Both selected Sonic routes
start with the ordinary native320 entry and donor off. InputLogAuthorTool
round-trips every selected BK2; its actual header count is checked, including
semicolon-delimited author tails rather than counting physical script lines.

| Route | Selected input lengths | Fresh uninterrupted observation | World replay status |
| --- | --- | --- | --- |
| Sonic + Tails | replicas11051, complete20540 | both replicas escape; receiving platform `(5888,140)`; all three killing hits; DEZ actually loads at20539 and the next ordinary entry dispatch releases Sonic `(48,2476)` with one native follower | replica and complete methods pass, one case each, zero failures/errors/skips |
| Sonic solo | replicas11200, complete17600 | corrected initial cloud/column staircase, both replicas and Mecha killing hit15298; DEZ actually loads at17599 and the next ordinary entry dispatch releases Sonic `(48,2476)` with no followers | replica and complete methods pass, one case each, zero failures/errors/skips |
| Tails solo | existing complete17670/16893 at320/800 | full drawing reaches actual DEZ; next ordinary entry dispatch releases the live Tails sprite `(48,2480)` | unchanged input fixtures; both complete-width cases pass, zero failures/errors/skips |

The early carrier repair catches the actual bar at7087. Reusing the old MTZ
combat and later traversal inputs did not finish either Sonic route, and
copying the team's later inputs into solo Sonic missed the first wrapped
columns. The selected controller tails instead observe real combat state and
land the narrow columns before the third teleporter. None of those rejected
controller policies changes gameplay code or proves a native discrepancy.

`loc_1BE7A` selects `Sonic_Start_Locations` for both Sonic and Tails; the DEZ1
entry is raw bytes `0030 09AC`. `loc_6468` owns the assembly after the
load-producing tick, so the test takes one ordinary neutral capture step only
when fresh/title ownership is still pending. It then requires those owners
to be released, the session/main/camera sprite identities to agree, ordinary
control and the proper roster. `Sonic_Init` installs radius `$13` and
`Tails_Init` radius `$0F`: the first floor pass seats both at foot coordinate
`$9BF`, giving Sonic centre2476 and Tails centre2480. This replaces a borrowed
Sonic-centre oracle for Tails without bypassing the native presentation phase.

Complete-route replay spots now include the selected staircase, third and
final transports, real Mecha combat and spiral. The last independent replay
is `movieLength-170`; live history begins at `movieLength-120`, after every
45-input replay window. Actual DEZ load and the subsequent fresh-history
isolation remain mandatory. Sonic wide/donor breadth, native presentation
acceptance and the full act matrix remain open; these candidates do not
certify the act or move a trace frontier.

The selected route gates subsequently pass on the combined provisional source
at local `ad6454611`: qualified EggRobo/common assembly/native controller
dependencies, the reviewed fresh-counter candidate, and coherent native ending
source patch SHA256
`9481c00cb1292b91467a212d3c6ea317e281e1b2f421945b8b83839ebcafde7c`.
The counter and ending files stay unstaged until their owning lanes qualify
and commit them. These are observed route checks on that source, not a claim
that the provisional dependencies or the full act matrix are delivered.

All commands use `python3 tools/testing/maven_queue.py -Dmse=off`, the verified
absolute S3K ROM property and `test`:

- `-Dtest=TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory`: two widths, zero failures/errors/skips, completed16:37:58 BST,36.675 seconds total.
- `-Dtest=TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport,TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches`: two cases, zero failures/errors/skips, completed16:38:32 BST,32.930 seconds total.
- `-Dtest=TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents`: one case, zero failures/errors/skips, completed16:41:11 BST,29.438 seconds total.
- `-Dtest=TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory`: one case, zero failures/errors/skips, completed16:45:58 BST,29.254 seconds total.

The ordinary drawing policy applies: render work is serviced each input;
checkpoints, independent replay branches and load endpoints are drawn. No
every-frame visual or native pixel claim follows from these passes.


## Current native 800px paired cold completion (2026-10-14)

The isolated lane `bugfix/ai-s3k-finish-ssz-wide`, based on
`7d0a9acbbd7b5b34907571d8577b7a985b1d7a24`, completes the explicitly outstanding
wide Sonic + Tails continuation. The selected
`ssz1-sonic-tails-cold-complete-800.{script,bk2}` contains 21014 inputs. All 9914
incoming first-replica inputs are byte-identical and parsed-frame-identical to
`ssz1-sonic-tails-cold-first-replica-800.bk2`. Only ordinary controller input is
authored; no position, physics, native clock, health, ring, emerald or event
state is supplied after the cold boot. Runtime source is unchanged.

The route lands the moving sloped cloud, jumps during the native upper
bouncy-cloud countdown, reaches the wrapped rotating carrier and narrow column,
then traverses the lower pads and spring zigzag to the final Mecha arena.
Waiting on the stationary rotating post was rejected: it changes the held pose,
not height. Copying the native320 continuation to800 dies before completing the
first encounter. Those are navigation negatives, without evidence for changing
physics. The selected sequence defeats GHZ, MTZ and Mecha through every native
health value 8..0, completes results and the spiral launch, and actually loads
DEZ1 at input 20863.

`loc_6468` completes fresh assembly on the next ordinary dispatch 20864: Sonic
centre 48/2476, fresh rings 0, native Tails centre 16/2480, matching session/main
and camera focus. This is distinct from ordinary pad control. The first full
contract incorrectly required the pad lock to be clear immediately after
assembly and failed at that assertion. Native `Obj_LevelIntro_PlayerRun`,
`loc_44A26`, retains the saved start+$B0 target, locks Ctrl1 and writes held
Right until playerX+$10 reaches it; `loc_44A56` clears both pad locks and deletes
the owner. The corrected contract observes that live owner at assembly and
its actual retirement/control release at 20946. No runtime repair or movie
change was made for that rejected oracle. The short neutral/Right/neutral
destination tail ends grounded at 437/2476 with 3 earned rings and unlocked
control. Native CPU Tails offscreen recovery remains allowed; the test proves
P1 survival/lives and the exact native P2 roster, not a no-death claim for CPU
Tails.

`TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline`
passes 44 semantic spots: 29 later traversal/results/launch checkpoints, actual
encounter/hit/defeat and results/spiral labels, a native DEZ intro window and
the actual control-release window. Each compares the complete registered
world immediately after restore and after two independently replayed 45-input
branches, with no snapshot-key filtering. Both cycles preserve the level world,
P1 life count/survival and typed native P2 slot. Every source replay ends before
the actual full load; the last window 20693→20738 also finishes before live
history starts 20743. Real outgoing history exists and the fresh DEZ timeline
retires it before either incoming raw-registry window. The separate 40-window
first-replica contract remains independent.

Focused command from `.worktrees/ai-s3k-finish-ssz-wide`, with Java 25,
`DISPLAY`/`WAYLAND_DISPLAY` unset, `JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`,
`LUA_BIN=/usr/bin/lua5.4` and `ALSOFT_DRIVERS=null`. The portable paths below
name the main checkout with `OPENGGF_ROOT`, the original absolute locked-on ROM
with `S3K_ROM`, and the external task root with `S3K_FINISH_ARTIFACTS`:

```text
python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off
  "-Ds3k.rom.path=$S3K_ROM"
  "-Dtest=TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline" test -B
```

Completed 00:16:01 BST on 2026-10-14: **1 test, zero failures/errors/skips**;
Maven execution 55.789 seconds, test 28.53 seconds, queue waiting excluded.
The test is fully drawn. The independent cold `GameplayCaptureTool` replay
completed all 21014 inputs, with no leader death or absent-P2 CSV row; every
state field through 20863 matches the selected author run. Its final still
shows the live pair on the DEZ entrance platform. This supplies no native
emulator/pixel parity or full-suite claim.

Compact author/capture command and source identity remain outside the repo in
`$S3K_FINISH_ARTIFACTS/ssz-wide-finish-7d0a9acbb/`.
Immutable runtime jar SHA256:
`0e0396a39033d20e3f76c7b51b6bb733c5a6ef34e3cc596beabfadffc0fdb990`;
3892 runtime Java source digest:
`da831ff1a61398f393588f1d107c6f10c5fc8d339945910bdb17d1dc67cadfbb`.
Selected BK2 SHA256:
`11b8b1490554e3dd164a98c1b09da06d2508f6a29871e6447a5fd24481b1f25f`;
script SHA256:
`0822999b6acbefa9c7f6e91c31e7b0dffdfe0e344e67ebe592ca9f5110e3d7dd`.
The original absolute-path locked-on ROM is CRC32`63522553`,
SHA-1`CFBF98C36C776677290A872547AC47C53D2761D6`.

This qualification is explicitly on 7d0 source. Newly fetched native movement
changes require the integration lead's subsequent combined-source check;
this record does not claim that untested merge. Sonic solo at800, intermediate
widths, donors, additional teams, deliberate death/starpost breadth and native
acceptance remain inherited obligations.


The lead's newer-source follow-up on `967b6db0a` completes at00:31:25 BST:
1 test, zero failures/errors/skips, 52.897 seconds Maven /27.91 seconds test,
with637 seconds queue waiting excluded. All44 spots and unchanged inputs pass
with actual load20863, assembly20864 and native control release20946. The
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md#composed-source-focused-qualification-and-launch-plan-2026-10-14)
records this combined-source command. Subsequent bounded
[composed-source smoke/guard qualification](../../plans/2026-10-13-s3k-playable-completion.md#completed-composed-source-qualification)
completes on `aaff73747`, with the separately attributed inherited CLI assertion
and 680 passing guard tests. Develop source delivery and owned-worktree cleanup are complete; the inherited
breadth above remains open.


## Physical checkpoint products and incoming replay (2026-10-14)

All three placed posts2/3/4 receive165 products:75 supported Sonic/Tails solo
and90 explicit-roster cases. Local approaches declare saved index1 before the
initial dispatch solely to select the ROM no-arrival branch; posts2..4 remain
untouched until physical contact. This is not a cold or physically authored
post1 claim. A separate maintained controller-only cold opening, with no
position/checkpoint writes, naturally touches post2 at($640,$5E8) before input
7912. The independent placement census also proves the Knuckles final arena
(SSZ Act2) has no authored star posts.

Each product approaches the literal ROM-placed star post through ordinary
movement, captures the full registered world on both sides of contact, and
replays and reverses it twice. It then invokes the production pit-death owner
twice, waits for actual GameLoop reload/title/fade/control release, and verifies
one lost life, saved coordinates/index, fresh runtime/object manager, retained
ordered roster/CPU leader identities, donor art and retired outgoing history.
Each incoming world independently captures and replays a45-input neutral window
twice in both restore directions, without hiding a second death or life loss.
All comparisons retain every registry key; no incoming raw restore crosses a
load. The configured rosters include duplicate names and seven followers;
these are finite examples of the uncapped API, not proof of every possible team.

The five cases per configuration use the actual320/352/400/528/800 presets.
Supported solo pairs are off×{Sonic,Tails,Knuckles}, S1×Sonic and
S2×{Sonic,Tails}, where the act has that character route. Six explicit configured
rosters cover native mixed/duplicate/large teams, S1 duplicate Sonic, and S2
mixed/large teams. The common test is `TestLrzSszCheckpointRoutes`; independent
census, layout witnesses and an S2 Tails/seven-follower800 witness run before
the long product method. The combined class passes888 tests, zero failures,
errors or skips, on `a8c9f892e855855f96c7ecad368e127880c9af3e` plus the final
class (SHA256`3ed4c7346c79cd06d694e7fc17cf625aaa941d4951db606702734d631115f818`),
request99024, completed2026-10-14 12:40:30 UTC. Exact commands, prerequisites,
rejected setups and the distinction from earlier qualification are recorded in
[the completion plan](../../plans/2026-10-13-s3k-playable-completion.md#checkpoint-product-qualification-2026-10-14).

This qualifies local physical contact and reload/replay products. It supplies
no cold route for every configuration, native whole-scene/audio acceptance or
combined smoke/guard/integration claim. Other matrix obligations stay open.


## Native opening-bridge contact follow-up (2026-10-14)

Independently fresh native320 Sonic+Tails and engine scenes consume original
AD40 suffix448920 after a separate neutral initial ProcessSprites pass,
polls0/LFC0. Losing that first ordinary row caused the earlier Death Egg X
drift; it was a host-boundary issue. Native blue/gold circles are present in
both captures; a player-row SAT count is not a circle-row overflow witness.
Corrected P1 coordinates/camera agree through poll1341. At1342 the native
bridge moves to894/3208 and acquires P1 at808/3179; engine had808/3192,
airborne. Decision callbacks and full RAM identify the exact bridge standing
bit, with no shared terrain landing. Updraft first moves P1 to3192; the native
surface3208-9 gives d0=-16 and relative Y3179. The former d2=16 profile
misses that contact. Existing provider flags now select d3, reject exact zero
and preserve the relative snap before TouchFloor. Shared solver/clocks,
allocator and fixture-directed runtime branches are unchanged.

Literal `TestSszBridgeNativeTopContact` on656e21eb1 plus its test draft has
10/6/0/0 in37975 at13:57:53 UTC. Final affected81009 is terminal0 at
14:02:18 UTC,54/0/0/0 (1:26 Maven): ten controls, one original-controller
contact/replay,13 profiles,25 bridge/death/reload capture products and five
Knuckles bridge cases. The real original-input case compares every registry
key twice around45 inputs, retaining current owners/lives and actual ride;
no raw restore crosses a load. Normal queued admission, Java25, surfaceless
EGL, original absolute ROMs, null OpenAL and no ambient desktop are used.

Native/engine external task evidence: `native-ssz-contact-pair-ntsc-v1` and
`ssz-contact-boundary-656e21eb1-e3b0c44298fc-v1` under the task spot-checks.
Their provenance/command JSON pins exact argv, original ROM/BK2, immutable
bf3c539 video host and passive decision instrumentation. Native762427 epochs
1791985804.0648851–1791985816.9969594; Java762737/mainSszContactBoundaryProbe
1791985817.4445887–1791985820.1126516, both0 (request8791 at13:50:20 UTC).
Its C# adapter SHA256 is6c8bf0e09852af0ab0df216ff969a1a914c11841264e30f7b181fc6592c459c0.
The callback CSV mislabeled06/07 as radii: those are visual height/width24.
Actual9/19 is verified at1F/1E in unchanged raw RAM; no radius inference uses
those mislabeled columns. OriginalRGB and prepared/published identities are
retained. The prior14908 helper int/uint compile error is terminal1 at
13:47:54 UTC, before emulator/engine-probe launch; engine compilation succeeds.
Actual child intervals are relayed to the authorized performance coordinator.
This is bounded owner/contact evidence, not whole-scene pixel/audio, every
cold product, full suite or combined delivery. Maintained cold completions
and corrected final engine spot captures follow. Consumed target redirects
are removed after this durable account.


## Corrected cold incoming-world qualification (2026-10-14)

On b930df4868 plus the qualified route drafts, request12506 is terminal0 at
15:17:45 UTC: native320 completion and upper route, paired800 completion,
and five independent bonus pilots pass8/0/0/0. The native completion has50
and paired800 has44 two-cycle45-input whole-registry windows, including actual
DEZ assembly and released incoming worlds, with outgoing history retired.
The320 movie preserves its original20540 rows and appends180 neutral rows.
Actual load/assembly is20550/20551 at320 and20874/20875 at800; both fades
take22 presentations. Semantic load predicates replace obsolete double-fade
row assumptions. Exact matched negative attribution, source hashes and
commands are in the [completion plan](../../plans/2026-10-13-s3k-playable-completion.md#ssz-cold-boundaries-and-bonus-return-pilots-2026-10-14).

The corrected original-input contact capture matches1432 native P1/camera/
counter/ring observations. The subsequent arrival-owner repair below removes the previously recorded
one P2 X and74 P2 Y discrepancies in the arrival window119–209.
These bounded routes/contact checks do not certify all viewport/character/
donor/roster combinations, whole-scene original RGB/audio or entire campaign
parity. Remaining matrix obligations retain their prior status.


## Native arrival continuation and palette qualification (2026-10-14)

On558e74833 plus the arrival candidate, request79082 executes143 cases:
119 unchanged neighbors pass and18 of24 new cases pass. Its six failures
are fixture premises: preloaded placements move the controller away from the
independently cold native slot4, and direct update calls do not advance palette
ownership epochs. The fixture retains helper3 behind its controller and uses
ordinary frames for the settle check; production is unchanged. Corrected exact
class request21831 completes20:09:21 UTC,24/0/0/0 (25.233s Maven;162s queue).
Together these runs qualify143 distinct identities, not a single green143 run.

Five widths verify helper3's next-pass start and twelve real dispatches,
four-pass ROM light pairs, the Tails-main Player_mode branch and two all-key
whole-world rewind/replay cycles. Three pressure cases cover live zero-ID
occupants, the penultimate/last carried DBEQ probe and exhausted budget without
fallback to slot33. The local settle inlet is explicitly declared; it proves
24 final palette dispatches and next-pass retirement, not cold traversal.

Passive original AD40 recapture25248 is terminal0 at20:12:52 UTC, using the
unchanged production runtime and original1431 ordinary input polls after its
one-shot initial ProcessSprites. All1432 rows now match the recorded native P1 X/Y,
X/Y velocities, ground speed and mapping frame, P2 X/Y, both camera
coordinates/copies, level clock and rings. P2 velocities and body/tail animation
are not present in that CSV; later selected RAM observations are separate. The former one-X/74-Y P2 deficit is gone. Eight earlier native
observer controls remain byte-identical; no gameplay values are imported.

Cutscene-bridge rider pose (2026-10-15 UTC): `Obj_SSZCutsceneBridge` sets its own
status bit 7, so Tails' `loc_14A16` standing branch keeps Wait (anim5) instead of
Balance. The bridge now advertises that negative header to the shared edge-balance
check. `TestS3kSszArrivalHeadless#stationaryTailsUsesTheCutsceneBridgeNativeNoBalanceHeader`
passed (1/0/0/0, request79598, 00:33:37) after the old Balance6 negative at input
1338. It asserts anim5/no balance/tail anim1; the native map `$AD`/tail map `$22`
and their 4,3,2 timers at inputs 1341-1343 are now asserted too (below).

Direct original-RGB spot comparison (2026-10-15 UTC, composed Root `d5bfcc1c0`
with the saved Limited expansion and Raw RGB profile, unmodified production
runtime, original AD40 suffix from 448920 after the one-shot initial
ProcessSprites; native `native-ssz-arrival-allocation-ntsc-v1` PPMs; no
normalization, quantization, shift or crop):

Capture method: `GameplayCaptureSession` sets `S3K_SKIP_INTROS=true` in its
constructor; the declared arrival inlet must restore `false` after construction
and before boot. Render every setup/movie step, including discarded intermediate
frames, as the qualified probe does. Set private `user.dir` before any
`GameServices` access, request colour settings before boot, and verify the
renderer's effective expansion/profile; saved preferences alone are not evidence
that the renderer applied them. The [current-source input1342 check](#current-source-arrival-input1342-2026-10-17) follows this protocol.

| Input | Differing pixels | Attribution |
|---|---:|---|
| 118, 119, 330, 680, 1430 | 0 | Exact |
| 1341, 1342, 1343 | 15/19/19 → 0 | Fixed: SAT-collected zones submitted Tails' tail before his body, putting the tail in front. `Tails_tails` follows `Player_2` with the parent's copied priority (`Obj_Tails_Tail_Main`, sonic3k.asm:30034), so native sprite-table order puts the body first (native SAT entries 22 Sonic, 24 body `$6A0`, 25 tail `$6B0`) |
| 120 | 538 | Inlet limit: native P2 is mapping 0, anim2, `object_control` 1 in RAM, as is the engine. `Player_prev_frame_P2` was zeroed by Level's `clearRAM`, so mapping 0 issues no `Tails_Load_PLC2` DMA and the native draws D400 tiles retained from the HPZ teleporter. The declared direct SSZ boot has no prior VRAM. The joined cold route has not been compared |
| 0 | 71382 | Inlet limit: the native frame still shows the SSZ title card; the declared direct boot shows gameplay. Frames from 118 match exactly |

`TestS3kSszArrivalHeadless#stationaryTailsUsesTheCutsceneBridgeNativeNoBalanceHeader`
now also pins P2 map `$AD`, tail map `$22`, both 4,3,2 timers and published
body-over-tail order at 1341-1343; without the order fix it fails at 1341. The
positive selection (`TestS3kSszArrivalHeadless`, `TestSpriteManagerRewindCapture`)
passed 37/0/0/0 at 02:31:22 UTC. Captures:
external task capture `spot-checks/ssz-arrival-boundary-d5bfcc1c0-373a1e1d6ee9-af8673d1d8-limited-v1/`
(with `spot-review.json`).

Viewed native and engine gameplay pictures remain bounded spot evidence,
not whole-scene original-RGB acceptance. Native input0 still publishes its
title-card presentation while the engine capture omits title cards. A global
previous-row pixel shift is rejected: it worsens logical pixel differences
rather than establishing a published VDP boundary. The native330/current-engine330
pair retains360 logical three-bit pixel differences; original RGB expansion
remains separately different. Full SAT/scroll/CRAM publication and other scene
owners must explain those residuals before a pixel-parity claim.

Exact commands, clocks and source/probe hashes are in the
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md#native-ssz-arrival-owner-qualification-2026-10-14).


## Native cloud priority and RINGS glyph (2026-10-14)

Original current-poll SAT330 entries13/14 carry art wordsE310/E320. Verified
`ArtTile_SSZMisc=$2D4` plus `$3C` identifies roaming clouds, not columns.
`loc_57BB2`, the bouncy-cloud/puff art word and both collapsing-column paths
set VDP bit15 independently of their SAT buckets. Five local overrides now
preserve those native high-priority bits. The initial column-only attribution
is rejected: its360 diagnostic pixels at330 remain unchanged. The HUD
`Map_HUD` RINGS row starts at text tile8, whereas SCORE uses a distinct R at6;
using the native ROM glyph removes the remaining two HUD pixels. S1/S2 HUD
mappings and shared render/colour policy are unchanged.

On committed da27f4f3d plus this production draft, focused10369 completes
22:12:06 UTC:73 tests, zero failures/errors/skips,1:14 Maven. Exact selection
is `TestHudStaticArtLivesFrameMappings,TestS3kSszColumnSpritePriority,`
`TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,`
`TestSonic3kDecodingUtils`, using the local normal queued wrapper, EGL,
absolute original S3K ROM, null audio and unset ambient displays. Independent
negative priority66895 (3/2/0/0), cloud66640 (3/3/0/0), and HUD38028
(1/1/0/0) precede the owning fixes; traversal79239 passes12/0/0/0.

Unchanged passive observer23561 completes22:15:19.727238 UTC, exit0.
External task directory `ssz-arrival-boundary-da27f4f3d-f4b6b8b25b36-v1`
pins production diff SHA256
`f4b6b8b25b36997f8e6a90a121bbf4b199a572c22bb58ca3b9636bff8739cbe1`,
observer6016797a066d4a595a996c82efcf3d6d76899610d737e0ecace6b5d45732a2bf
and original ROM/AD40 movie. Original native/current engine images were viewed
at330 and1342;330,680,1430 now have zero diagnostic three-bit bin differences.
1342 retains475 differences at x267–306/y161–191, including the visible Tails
pose. All four original RGB pictures still differ in71680 pixels because the
colour expansion is distinct. Diagnostic bins identify an owner; they do not
certify original RGB, native colour policy, other frames or whole-scene parity.
No frame shift, fitted scroll or gameplay hydration is used. Exact compiler
and JVM bounds/argv are retained beside the media and relayed to coordination;
the normal parent retains the source/classes worktree lease for both children.

Stronger whole-world sky replay23335 fails one of21 tests at22:00:22 UTC:
`theSkySurvivesACaptureRestoreAndForwardReplay` reports prepared sprite tile
count496 versus304. Matched unchanged-da27 arm39050 at22:07:53 UTC reports
the identical failure (1/1/0/0); all five priority candidate files are restored
byte-identically after attribution. This inherited publication failure remains
active owner work. Combined smoke/guards and integration/push remain pending.
Consumed owned Maven redirects are removed after this durable account.


## Beam structural art and complete sky replay (2026-10-14)

The inherited prepared496→304 sky failure is repaired on17b47cb48 plus the
beam draft. Passive table attribution67368 identifies192 missing SSZ beam
tiles; unchanged motion/child fields concealed a constructor-only selector.
Generic recreation's harmless parent-null probe chooses the HPZ art bank.
The beam now captures its own native art selector and reconstructs from it,
rejecting a missing selector. Ordinary SSZ transports likewise select the SSZ
bank; native art-word bit15 remains1 there and0 in loc_45908's HPZ branch.
Shared graphics policy, frame/scroll clocks and public snapshot shapes stay
unchanged. Literal probe-art negative6748 fails1/1/0/0 at22:24:52 UTC.

The corrected source's36057 at22:27:16 UTC completes11/2/0/0: all three beam
cases and six original sky cases pass; the two separately authored actual
Slot portal cases fail only the missing interior-rewind support obligation.
The unmatched TestSszArrivalControllerNative selector contributes no tests.
Final SSZ/HPZ affected97786 completes22:30:27 UTC,22/0/0/0,25.937s Maven:
`TestTeleporterBeamRendering,TestS3kSszBackgroundClouds,TestS3kSszTeleporterPads,`
`TestS3kHpzTeleporterHeadless`. All five actual viewport widths compare every
registry key, both restoration directions and forward replay twice around
120→121 ordinary frames. The captured motion, owning art bank and prepared
picture survive together. Commands use the normal local queue, original
absolute S3K ROM, EGL, null audio and no ambient desktop.

Root composed load/deformation check65987 separately passes78 of79 tests
at22:21:09 UTC (1:18); its one failure is the same now-repaired sky publication
owner. Final22 is the bounded new repair gate; it is not a claimed green79
rerun. The original RGB and1342 Tails pose limits above remain active.
Consumed attribution/focused redirects are removed after this account.

## Native single-fade retiming (2026-10-15)

8179ccd80 stopped `GameplayCaptureSession.step` from advancing a runtime-ready
loop's FadeManager a second time. Movies authored before it budget half of each
frozen fade-out (11 of the native 22 frames, during which gameplay and object
VInt do not advance), so their later inputs landed early. `FadeWindowRetimeTool`
(41405e073, 5333aece6) measured each owned frozen window in the production harness,
inserted the native remainder as repeats of the held input and verified every
movie by one uninterrupted replay with no death. On the LRZ Knuckles and Tails
chains the retimed rows equal the 22d5e6ffc rows shifted by the inserted frames,
VInt included (0 differing rows). Test frame constants move by 11 at and after each
old load frame. Base: Root ef61a560f; no runtime behaviour changed.

| Movie | Old SHA-256 | New SHA-256 | Windows `start:native→+inserted` | Frames | New full loads | Verified end |
|---|---|---|---|---|---|---|
| `ssz1-sonic-solo-cold-complete-320.bk2` | `26e16d273bca81b1983028c89d683a3a7c616e104ec350900db431fe98401d10` | `6c7469ca579364cd9b0abb121b37f8eaaee0171e11a1a811ae82ae9095d4742b` |  | 17600 |  | 6899,294, 3 rings, zone 10/0 |

Validation on ef61a560f plus the retimed fixtures (`TestSszSoloColdRouteCapture`), focused selectors through
`tools/testing/maven_queue.py` with the S3K ROM: run `cold-fade-verify` (Maven
Finished 04:15:51Z) passed `TestDezIncomingFinalRouteCapture` 7/0/0/0,
`TestDezSoloActTwoColdRouteCapture` 4/0/0/0, `TestLrzActTwoColdRouteCapture` 2/0/0/0,
`TestLrzTailsColdRouteCapture` 4/0/0/0 and the fade rows of `TestLrzKnucklesColdRouteCapture`
and `TestSszSoloColdRouteCapture`; run `cold-joined-retimed` (Finished 04:21:19Z) passed
`TestS3kHpzJoinedColdRouteCapture` 2/0/0/0 (pair 66 spots, HPZ 52947/52948, SSZ1
67037/67038, release 67211; Knuckles 27 spots, HPZ 49297/49298, SSZ2 50160/50161,
release 51586). That test's LRZ live-history tail now starts 36 frames before the HPZ
load (14 live frames before the native 22-frame fade-out). Rows still red there are
not fade timing and are owned elsewhere: the Knuckles act-2 restore at 37130 and the
LRZ boss sidekick overlap parity at 47471 (rewind-snapshot rows) and solo Tails' SSZ1
opening camera (behaviour row). No skips.

## Cold LRZ→HPZ→SSZ1→DEZ1 pair continuation: first attempt stopped (2026-10-15)

Attempted on ef61a560f from the fade-retimed joined pair chain
(`lrz-sonic-tails-cold-hpz-ssz1-320.bk2`, `0ac8acb508ba16f4f579f2566554237ccf0d55636a612497a847c5cbc2be1607`,
SSZ1 control release 67211) with the direct `ssz1-sonic-tails-cold-complete-320.bk2` as the
candidate continuation. At release both runs share P1 (256,3085), camera, rings, the
sidekick and the RNG seed (`cfcd80f3`); only V_int differs (67142 vs 174). Three bounded
splices failed in the same traversal region: the plain splice died at direct input 6143,
and controller idles of 104 and 616 frames at the release spot (V_int mod 256/1024
alignment) died at 6045 and 6039 (the release spot is not a standing spot; P1 falls
3085→3148 while idling). A divergence probe located the cause: Tails' CPU sidekick differs
by one pixel from direct input 190 and the RNG from 192 (roaming clouds), and P1 first
differs at direct input 1366 on the Knuckles button cutscene bridge, where the spliced run
drops through while the direct run is still grounded. Making the pair continuation needs
traversal re-authoring of SSZ1 from that bridge onward plus a Mecha encounter search; this
lane stopped there. Solo Tails (needs an HPZ Knuckles-fight author for Tails plus the same
SSZ1 traversal) and solo Sonic (no Sonic-alone LRZ/HPZ cold movie exists) are not started.

## Cold LRZ→HPZ→SSZ1→DEZ1 pair campaign (2026-10-15)

`TestS3kLrzHpzSszColdCampaign#pairColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`
replays one uninterrupted controller-only native320 Sonic+Tails movie from the declared
`boot(9,0)` LRZ1 inlet (no position, clock, emerald or RNG seeds): LRZ1→LRZ3, HPZ
`loc_45D8A` (`$A00`), the SSZ1 arrival, the GHZ and MTZ replicas, the clouds and
vertical-wrap section, Mecha Sonic, the act-1 results SaveGame (`loc_2DCA0` takes the zone
`$A` branch for act 0, sonic3k.asm:62668-62679) and the launch into DEZ1 (`$B00`) through
`loc_44A26`'s intro run to control release.

| Movie | SHA-256 | Inputs | Source |
|---|---|---|---|
| `lrz-sonic-tails-cold-hpz-ssz1-dez1-320.bk2` | `8930f7c924ca6f55b831a75316ac4c3a136eca8ce2cb7ffc5f25cccbe4ca32be` | 85544 | inputs 0–67211 equal `lrz-sonic-tails-cold-hpz-ssz1-320.bk2` (`0ac8acb508ba16f4f579f2566554237ccf0d55636a612497a847c5cbc2be1607`) through the SSZ1 control release |

Authoring (base 7cfefb6af for cold2's part, then Root 19b7d70a6 merged; the prefix was
re-confirmed after the merge: the saved 79707-input candidate reaches the identical state,
P1 (3353,3939), V_int 79637, RNG `c97fadd8`). Cold2: the direct SSZ1 movie followed with
time-shift resyncs from the joined release; GHZ authored with `SszBossInputAuthorTool`
(defeat after input 71823), followed again, MTZ authored (76536), followed into the cloud
section, where time-shift resync stopped working (P1 caught by `Obj_SSZRotatingPlatform`,
RNG-phased bouncy clouds and collapsing columns; errors 213–452 px). Cold3: a
progress search along the direct movie's P1 path (the algorithm committed as
`ReferencePathInputAuthor`; P was authored by the identical search in a temporary batch
helper) crossed the
cloud/wrap section (direct indices 12171→12910, no hurt) and, after a stuck rule that
abandoned a slowly rising transport pad was relaxed, the rest of act 1 to the Mecha arena
pad (12900→17463, 86 decisions, 38 s); `SszBossInputAuthorTool` MECHA then defeated Mecha
Sonic (hp 0 after input 82834; Sonic lost rings 18→1 in the fight, no death or life
loss), and neutral input carried the results, the launch (DEZ1 load after input 85143)
and the DEZ intro. The movie's script expands byte-identically to its BK2 input log.

Fresh replay (run `p-verify2`, Maven Finished 16:20:18Z, admitted after the host memory stall): 1/0/0/0 in 52.2 s;
HPZ load 52947, SSZ1 load 67037, GHZ defeat 71823, MTZ defeat 76536, Mecha defeat 82834,
results tally-to-wait (the SaveGame dispatch) 83452, DEZ1 load/assembly 85143/85144 with P1
(48,2476), 0 rings, one registered Tails, `loc_44A26`'s control lock and
`LevelIntroPlayerRunInstance`, intro release 85226. SSZ's live history (armed 36 inputs
before the DEZ load) is retired by the DEZ timeline. Every input is drawn; 45-input two-cycle
whole-world replays at the GHZ, MTZ and Mecha defeats, the results save and the DEZ release.

Engine pictures (320 px) in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/cold-campaign-v1/p/` were
inspected: cloud/wrap entry (79707), the lower transport pad (80167), the Mecha arena pad
(81907, P1 hidden by the pad as it rises), Mecha seated after defeat (82835) and Sonic and
Tails released in DEZ1 at 0:06 (85544). The hidden player on the rising pad is flagged for
native comparison. Native windows: `runs/s3k-sonic-tails-complete-emeralds` SSZ1 from
448920 and DEZ from 468982 (that run carries emeralds).

Limits: the boot inlet has no save session, so the results SaveGame is observed as the
results owner's tally-to-wait transition (with `Collected_special_ring_array` clear), and
its persisted next level is covered by the save-site tests; widescreen/donor breadth of
the campaign remains open.

## Cold LRZ→HPZ→SSZ1→DEZ1 Tails campaign (2026-10-15)

`TestS3kLrzHpzSszColdCampaign#tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`
replays one uninterrupted controller-only native320 Tails-alone movie from the declared
`boot(9,0)` LRZ1 inlet: the qualified cold Tails LRZ chain into HPZ, HPZ's Knuckles fight,
theft, collapse and altar (the single-player helper timers) to `loc_45D8A` (`$A00`), Tails'
SSZ1 arrival, the Knuckles-button bridge, both replicas, the clouds, Mecha Sonic, the results
SaveGame and the launch into DEZ1 through the intro run's release. The shared verifier (also
used by the pair test) additionally arms HPZ live history 120 inputs before the SSZ1 load and
asserts its retirement and the SSZ1 assembly (P1 X `$100`, Camera Y + `$65`, 0 rings).

| Movie | SHA-256 | Inputs | Source |
|---|---|---|---|
| `lrz-tails-cold-hpz-ssz1-dez1-320.bk2` | `54d25084b828db637068a3ba158954e3f64d5d859d49c686e5d6ddcb0b2282ff` | 86090 | inputs 0–62545 equal `lrz-tails-cold-hpz-320.bk2` (`229c5036eb936aacb38603fd2195e76baa0c150c36deae900f1fc37e9ec0ec94`, all of it) |

Authoring on Root 19b7d70a6 (two finite batches, ts1 and ts2): the reference-path search
followed the joined pair chain's HPZ P1 path to the lower teleporter (pair index 62475); the
HPZ fight policy (`HpzJoinedControllerAuthor`'s FightBot, which reads only P1 and the live
Knuckles owner) carried Tails through the fight, theft, collapse and altar to the SSZ1 load
(input 69861; rings 12→1, no death); neutral input to the arrival release (input 70036, P1
(256,3085), RNG `cfcd80f3`, the direct pair movie's release seed). In SSZ1 the search first
stopped at the Knuckles-button bridge: P1 stood at the camera-bounded edge (max X 512) while
`CutsceneKnux_SSZ` was still on its way to the button, so the search could not progress;
1076 neutral inputs later `Obj_SSZCutsceneBridge` had retracted and `loc_44FBA` opened the
act bounds. The search then followed the direct pair movie's P1 path (Tails flies where it
helps), `SszBossInputAuthorTool` defeated GHZ (after 72972), MTZ (76304) and Mecha (83380;
rings 52→0, no death, lives 3), and neutral input carried the results, launch and DEZ intro.
The script expands byte-identically to the BK2 input log.

Fresh replay (run `v3`, Maven Finished 17:18:46Z): 2/0/0/0 with the pair method. Tails: HPZ
62424, SSZ1 69861/69862, GHZ 72972, MTZ 76304, Mecha 83380, results save 83998, DEZ1
85689/85690 (Tails feet at `$9AC+$13`, centre Y 2480, no sidekick), release 85772; HPZ and
SSZ histories retired at their loads; 45-input two-cycle replays at the three defeats, the
results save and the DEZ release. The pair run in the same JVM matches its committed
numbers (52947, 67037/67038, 71823, 76536, 82834, 83452, 85143/85144, 85226).

Engine pictures (320 px) in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/cold-campaign-v1/t/` were
inspected: Tails in the HPZ lower teleporter (64546), the SSZ1 load and arrival with
cutscene Knuckles beaming in over Tails' arrival spot (69862/70036), the opened bridge
(71252), the GHZ replica's defeat (72973), MTZ (76305), the Mecha arena pad (81965), Mecha's
defeat (83381; Tails off-screen above) and Tails released in DEZ1 at 0:06 (86090). Native
windows: `runs/s3k-tails-full-chain-all-emeralds` HPZ from 419298, SSZ1 423903, DEZ 444059.

Limits: as for the pair, the boot inlet has no save session; widescreen/donor breadth
remains open.

## Cold LRZ→HPZ→SSZ1 solo Sonic campaign: first attempt stopped (2026-10-15)

Superseded by the [2026-10-16 campaign](#cold-lrzhpzssz1dez1-solo-sonic-campaign-2026-10-16),
which found the cause: the route had not taken LRZ3's fire shield. The next-attempt advice
at the end of this section (a ring-keeping survival author) was not needed.

No solo Sonic LRZ/HPZ cold movie exists, so lane cold3 authored one from the declared
`boot(9,0)` inlet along the joined pair chain's P1 path (`ReferencePathInputAuthor`; the
reference only scores positions) in finite batches on Root 19b7d70a6. It reached:

- LRZ1: a spin dash through the rock wall the pair breaks (spin-dash candidates were added
  for this); the door at (2800,1688) opened from its horizontal button (2618,1779), which
  position progress had skipped; the dash elevator (4768,1976) by replaying the pair's
  D+A taps (a stuck recovery that steers to where the reference stood 60–650 indices
  earlier and replays its inputs); `Obj_LRZMiniboss` defeated with `SszBossInputAuthorTool`
  `lrz1` (hits 0 after input 13030, no death).
- LRZ2 (load after input 13370): the door at (10160,1576) opened from `Obj_Button` (9816,1652);
  a search trial that touched giant ring s9 broke a restore (the special stage registers
  its own rewind subsystem), after which both searches treat a giant-ring flash like a death.
- LRZ3 (load ≈ input 21445): the end-boss arena (camera locked at `$A00`, 77 rings) at input
  24204.

It stopped there by the kill condition: three bounded searches failed in the same
`Obj_LRZEndBoss` phase, routine 4 (the shot phase, callback 0), each time after the shots
had taken all of Sonic's rings. The boss takes every hit from its own drifting mines, so
the player only has to survive about 5500 inputs (the pair needed 46900→52500).

| Attempt | Failure | Boss routine/timer/shots/interval/hits | V_int | RNG |
|---|---|---|---|---|
| s9 (search state from s8) | arena entry already doomed at 24669 | 4 / 31 / 2 / `$6F` / 14 | 24645 | `7c8be79b` |
| s11 `lrz3` boss-policy search | death at 24873 | 4 / 5 / 0 / `$4F` / 13 | 24848 | `0f0d7083` |
| s12 survival chase near (2720,1500) | no candidate survives 120 inputs at 25724 (neutral death 25809) | 4 / 27 / 1 / `$5F` / 11 (flipped side) | 25700 | `512a4c93` |

Lava drift clock at those states: 208, 410, 308 (stream `$FFFF`, `$FFFF`, `1`). The partial
inputs (miniboss defeat at 13031, arena entry at 24204, the last survival state) and the
finite batch lists are preserved with SHA-256 sums in the task directory
(`cold3-continuation/`). A next attempt needs a survival author for the shot phase that
keeps rings (dodging the boss's projectiles over the lava platforms) rather than chasing
the boss or a fixed spot; HPZ and the SSZ1 arrival for solo Sonic were not reached.

## Cold LRZ→HPZ→SSZ1→DEZ1 solo Sonic campaign (2026-10-16)

`TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`
replays one uninterrupted controller-only native320 Sonic-alone movie from the declared
`boot(9,0)` LRZ1 inlet through LRZ1, LRZ2, LRZ3's fire-shield monitor and end boss, HPZ's
Knuckles fight, theft, collapse and altar to `loc_45D8A` (`$A00`), the SSZ1 arrival, the
Knuckles-button bridge, both replicas, the clouds, Mecha Sonic, the results SaveGame and the
launch into DEZ1 through the intro run's release, with the same shared verifier, checkpoints
and HPZ/SSZ isolation windows as the pair and Tails campaigns. No death, no seeds; lives never
drop.

| Movie | SHA-256 | Inputs | Source |
|---|---|---|---|
| `lrz-sonic-cold-hpz-ssz1-dez1-320.bk2` | `41cc9e73afc5a62a901a7ad3afaedc509b19ac68127673be09ca018054b5d9b5` | 52271 | inputs 0–23899 equal cold3's `s-10a` (task directory `cold3-continuation/`, SHA-256 `7f02648c…`); no qualified prefix movie exists for this roster |

Boundary inputs (fresh replay): LRZ3 boss created 24033, activation (`loc_797FA`) 24425,
defeat 28750 (activation + 4325, as natively), release 29767, HPZ load 30123, SSZ1 load
39248/39249, GHZ 43233, MTZ 45735, Mecha 49728, results save 50375, DEZ1 load 52067/52068,
release 52150.

Why cold3 stopped and what changed. The 2026-10-15 attempt (section above) died in
`Obj_LRZEndBoss`'s shot phase. Round 2 first compared the boss with the ROM
([LRZ boss matrix](s3k-lrz-boss.md#solo-sonic-cold-fight-2026-10-16)): the engine's boss,
mine, platform and lava routines match `sonic3k.asm`, and the engine and both native fixtures
take the 14 mine hits at identical offsets from activation, so the fight is
player-independent clockwork and no engine change was made. The difference was the shield:
the pair's Sonic breaks LRZ3's placed fire-shield monitor (`Obj_Monitor` subtype 5 at
`($AE8,$481)`) before the arena and keeps it, and `Obj_59FC4`'s `loc_5A0AC` spares Player 1
with `Status_FireShield` from `sub_24280`'s lava hurt. The pair's path touches the sloped
lava (first at activation + 160 on the entry platform's left edge); cold3's solo route had
passed over the monitor without breaking it, so every such contact hurt.

Authoring (finite `-Dsolo.maxSeconds` batches of a temporary helper, never committed,
normal queue admission): from `s-10a` input 23900 a target chase broke the monitor (FIRE at
23985); a neutral fall onto the lava at x 2576 put the camera at `($A00,max Y)`, so
`loc_59C8C` created the boss (24033); a policy search boarded the entry platform and an
exact tap scan parked Sonic at x 2692, where the pair's Sonic stands at activation. From
activation the pair's P1 pads were replayed time-aligned with a per-input left/right
correction of the 16.8 fixed-point X error against the pair's P1 (worst error 3.6 px, one recovery), through
all 14 hits, the capsule and, aligned on `loc_799E0`'s `Restore_PlayerControl` (1017 inputs
after the defeat here, 967 for the pair), the walk to `Obj_StartNewLevel` and HPZ. HPZ used
`ReferencePathInputAuthor` along the pair's HPZ path to its lower teleporter (pair index 62490)
and the HPZ fight policy to the SSZ1 load. In SSZ1 the direct solo Sonic SSZ1 movie
(`ssz1-sonic-solo-cold-complete-320`, released at its input 174) was replayed time-aligned on
the arrival release (input 39422, P1 (256,3085)) to the Knuckles-button bridge, 66 neutral
inputs waited for `loc_44FBA` to open the bounds (max X 6560), then `ReferencePathInputAuthor`
followed that movie's path to each arena and `SszBossInputAuthorTool` defeated GHZ, MTZ and Mecha
(rings 16→0 there, no death); neutral input carried the results, launch and DEZ intro. The
script expands byte-identically to the BK2 input log.

Fresh replay (run `v2` on round-2 integration `6405313ce` plus this change, Maven Finished
17:29:46Z): the solo Sonic, pair, Tails and Knuckles methods 4/0/0/0 (solo Sonic 30.6 s),
`TestReferencePathInputAuthor` 1/0/0/0 and `TestSszBossInputAuthorTool` 2/0/0/0, no skips.
Solo Sonic: HPZ 30123, SSZ1 39248/39249, GHZ 43233, MTZ 45735, Mecha 49728, results save
50375, DEZ1 52067/52068, intro release 52150; 45-input two-cycle replays at the three
defeats, the results save and the DEZ release. The pair, Tails and Knuckles runs in the same
JVM reproduce their committed numbers. Red first: the same verifier on this movie truncated
to 45000 inputs ran its per-input checks through HPZ, SSZ1 and the GHZ defeat and then
failed `expected: <45735> but was: <-1>` (the missing MTZ defeat).

Engine pictures (320 px, Limited over Raw RGB) and their provenance are in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/round2-solo/`; all were
inspected: the monitor break and FIRE shield (23940–23985), the parked entry platform
(24195), activation (24426), a mine hit, a hit flash and the defeat (24836, 26000, 28751), the
capsule, results card (ring bonus 7700) and release (29200–29768), HPZ (30125 black load
frame, 34004 inside the lower teleporter, 36165), the SSZ1 arrival with cutscene Knuckles
beaming in over the arrival spot as in the Tails pictures (39424), the bridge (40640), the GHZ,
MTZ and Mecha defeats (43234, 45736, 49729), the launch ramp (P1 behind the tower at 51200)
and DEZ1 (52151, 52271). No native solo Sonic run exists. Against the native pair run
(`s3k-sonic-tails-complete-emeralds`, BizHawk frame 437200, about activation + 1663 by the
`hpz22_2` segment offset 434069 + 1468): at activation + 1663 the engine's boss is at (2608,1525) with 8 hits left, its crest
(`loc_79BF6`) at (2608,1507) and its pilot (`loc_79C1C`) at (2629,1526), exactly the native
SST values, with the same platform layout. The visible differences are player-owned (native
Tails at (2578,1489), Sonic's position, HUD totals) and the crest frame: its 1→2→3 loop
(`byte_7A1C2`, 3 frames each) is phased from the boss's creation, not its activation, and the
engine draws all three frames at the native position. SSZ1 frames at the native pair
pictures' offsets from the load (+330, +680) are not the same state: the Knuckles cutscene
and the Death Egg rise follow the players' route. A native replay of this movie was not
attempted (it starts from the engine's declared `boot(9,0)` inlet).

Limits: as for the pair and Tails, the boot inlet has no save session; widescreen/donor
breadth remains open; the death-restart title-card timing is irrelevant (death-free movie).

## Solo Tails movies re-authored for the native opening bridge (2026-10-15)

Two ROM-correct runtime changes invalidated the five solo Tails controller movies:
82539953f (the opening bridge's top contact follows `sub_45026` → `SolidObjectTop`
`loc_1E44C`/`loc_1E45A`, so a re-landing keeps its y subpixel) and 56615e135 (Tails
keeps the Wait pose on the bridge, `loc_14A16`, so the held Down that used to stand
in the Balance pose now ducks and `D+B` spindashes). Attribution and the
old-emulation proof that these two changes alone made the rows red are in the
behaviour lane's record; no runtime behaviour changed here.

Re-authoring, all controller-only from a cold boot (no position, ring, clock or
health seeds): inputs 1379-1382 drop the held Down (`1 -`, `3 B`), which reproduces
the old run exactly to 1419; after that the native re-landings drift (3px at 2243,
40px at 4142), but the old inputs still reach the GHZ replica. Each width's GHZ,
MTZ and Mecha fights were authored by the committed `SszBossInputAuthorTool`
(12 policies, 180-input horizon, 30-input commit) from prefixes 4460 (320) and 4476
(800); the transits between them followed the old route's P1 path (a temporary
reference-path follower: old inputs while within 6px, otherwise scored time-shifted
old inputs, waits and steer/jump policies from rewind marks, the method of the
cold-campaign `ReferencePathInputAuthor` candidate); the Death Egg lift ends on
neutral input at the actual DEZ1 load. Replica movies are the complete-movie prefix
to the MTZ pad release (320: 8001, 800: 8225) plus neutral input standing at the top.
Rewind-spot frames up to 4460 are unchanged; later ones are the old spots mapped
piecewise-linearly between matching landmarks (GHZ lock, pad rides, MTZ lock, Mecha
spawner pad, Mecha lock, Death Egg lift, DEZ load) and land in the same phases.

| Movie | Old SHA-256 | New SHA-256 | Frames | Landmarks (new) | Verified end |
|---|---|---|---|---|---|
| `ssz1-tails-solo-cold-flight-320.bk2` | `e6edf7fc12c54c6c44fd8c60b05b3be77e3aab089506d3b1abaf0380103e0537` | `4bc319486424dd52f0689b9a453fd5f39a464cf549144e7b511a368f59fe06ac` | 2556 | 2345: (1323,3006) flying, minY −$100 | (2091,2953), 3 rings (was 2085,2954) |
| `ssz1-tails-solo-cold-replicas-320.bk2` | `2b06d0e42d9158e87ff6d0407386af1abb68e2d3c5d06ca29e3f988c8b4d87bc` | `7895138785a8f37ed8da9996dbf2b7a83a9518043eb9ad949a7427736c90eb01` | 8200 | GHZ 4452→hp0 4895; pad 5218-5476; MTZ 6819→hp0 7417; pad 7733-8000 | (5888,144), 3 rings |
| `ssz1-tails-solo-cold-replicas-800.bk2` | `bb2455d0ce681e120bf7b5a31f04230174e1d0b7b67576503839e652f5ce18ac` | `e502d16c49912a95d4a4db276078c1af0d0ad0855426c9360266feaca1884c8a` | 8425 | GHZ 4468→hp0 4839; pad 5158-5416; MTZ 6779→hp0 7640; pad 7956-8224 | (5888,144), 0 rings |
| `ssz1-tails-solo-cold-complete-320.bk2` | `ffdc452123aa06188a77879a8a018ae43fcd3ccb5c05027ccebd5cf11bd83a91` | `0e57c593403e4bf73019e6b7613da2c492c165534f15be70eac9696b619150cd` | 13667 | as replicas; Mecha pad 10450, hp8 10685→hp0 11348; lift 12228 | DEZ1 load 13666 |
| `ssz1-tails-solo-cold-complete-800.bk2` | `bd285c6b5f033523fa96ef4bc35f9337001af9bb2f06fea5078909526f0bdba7` | `61a3d2fe1352340d50c977a04f84904e147bd82978895aaae625408a3d6c16f4` | 15132 | as replicas; Mecha pad 10680, hp8 10909→hp0 12814; lift 13693 | DEZ1 load 15131 |

Test updates: `TestSszTailsColdRouteCapture` frame counts, per-width spot sets, Mecha
entry windows (320: 10443/10645, 800: 10667/10819) and replica-end rings (3/0);
`TestSszSoloColdRouteCapture` flight end (2091,2953); `TestSszBossInputAuthorTool`
authors from 4829 (two GHZ hits left) within 240 inputs. The five-width GHZ
approach case is unchanged (spawn 4452 at 320/352/400, 4453 at 528, 4468 at 800).

Validation on 0b03f8705 (focused selectors through `tools/testing/maven_queue.py`, normal
lane, S3K/S1/S2 ROMs, run `v-ssz`, Maven Finished 17:47:00Z):
`TestSszTailsColdRouteCapture` 11/0/0/0, `TestSszBossInputAuthorTool` 2/0/0/0,
`TestSszSoloColdRouteCapture` 3/0/0/0 — 16 tests, no failures, errors or skips.


## Current-source arrival input1342 (2026-10-17)

On `78472d97a75a0eea56874d165b73037bb4a2068c` (Main803 runtime plus
Knuckles capture tooling), exactly one fresh engine picture matches the retained
original native `native-ssz-arrival-allocation-ntsc-v1/input-1342.ppm`:
**0/71680 differing RGB pixels**, both320×224, no crop, normalization, rescale,
shift, masks or clock search. The original native preview was verified RGB-identical
to its PPM before viewing; that image and the fresh engine PNG were inspected.

This is the declared direct-SSZ Sonic+Tails inlet: native ProcessSprites
`loc_6468`→`$6484`, Level_frame_counter0, VInt0/RNG0, then one neutral setup step
and original AD40 movie rows448920..450262 inclusive (1343 inputs), with the
picture after ordinal1342. Effective renderer modes were `LIMITED`/`RAW_RGB`,
NTSC, intros enabled, FAST load simulation, Java25.0.4.1. Private preferences
preceded all services; `user.dir` was restored and Main config SHA256/mtime
were unchanged. No gameplay state was supplied from observations.

Durable task evidence is `ssz-input1342-main803-v1/` under
`$TASK_EVIDENCE_ROOT/` (the external task capture root):
`provenance.json`, `effective.properties`, `state.csv`, `comparison.json`,
`input-1342.png` and the immutable temporary adapter source. Exact hashes and
queued command are in the [task plan](../../plans/2026-10-13-s3k-playable-completion.md#current-source-ssz-arrival-input1342-2026-10-17).
The temporary adapter/classes/launcher/log were consumed and removed; captures stopped.
This adds one current-source checkpoint. Cold LRZ/HPZ continuity, input0 title
presentation, inherited VRAM at120, other moments/widths/rosters, audio, timing
and whole-scene qualification remain outside this observation.


## R16 current cold Sonic campaign blocker (2026-10-17)

The unchanged maintained solo320 LRZ→HPZ→SSZ→DEZ campaign fails at movie input49613
on production00c54, sole observation42765: **1/1F0E0S**, original `cold death49613`.
The actual matched Main803 case24967 passes;00c54 contains later composition than
failed Root02b, so this does not isolate a regression or establish native death
correctness. Historical completion rows above are not current-source acceptance.

A63-input passive window observes P1 alive after terrain and dead before the
first cloud checkpoint. Only cloud/teleporter solid callbacks are named; the
proxy has no touch/damage callback. Mecha parent state and ROM collision tables
are controls, not selected-provider or kill-writer evidence. Earlier ring loss
is outside the window. See the [R16 audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r16-cold-sonic-campaign-first-death-bracket--2026-10-17)
for exact clocks, pose, movie hash, command and prerequisite accounting.
No runtime repair, native/media, variant or further campaign execution follows;
first rising-dead writer/cause and current campaign completion remain open.


## R17 engine death-writer clarification (2026-10-17)

One original maintained prefix48401 on00c54 plus R16docs/passive logs again fails
colddeath49613 (1/1F0E0S). The actual rising setDead stack now identifies shared
TouchResponse.applyHurt→applyHurtOrDeath→applyDeath, selecting Mecha **parent**
class as HURT/NORMAL with hadRingsfalse. The secondary child/custom update/cloud
checkpoint is not this observed writer. See [R17 exact witness](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r17-cold-sonic-campaign-rising-death-writer--2026-10-17).
No shared damage defect or earlier trajectory/ring producer is established;
current campaign completion and native comparison remain open. Temporary logs
are removed; no runtime repair, new route, native/media or further execution.


## R13 matched cold-campaign first tuple difference — 2026-10-17

**Comparison evidence, no runtime repair/native truth claim.** Same98d/native01a1496e
ran the original maintained solo Sonic cold case once on explicit published Main803
`8030832d2ab6080e5324cdfe35e971677fd97b41`, then once on exact composed
`f196e95da4583206a1201b3b4d02b1f626f7026e` (Slot2077 +ICZ70e +R16/R17 docs;
no unqualified DEZ WIP). The original class, GameplayCaptureSession and fixed BK2
are byte-identical across both. Historical author policies do not execute here.
Root reviewed the same two temporary test-source bytes; removing exactly five
marked logging blocks reconstructs the original driver byte-for-byte. Existing
step/render pairs stay adjacent; only passive scalar reads/output follow them.
All original input/assert/capture/restore/history calls and ordering remain intact.

| Epoch | Handle / terminalUTC | Original selected case | Queue / Maven / method |
| --- | --- | --- | --- |
|803 baseline |38651 /18:17:02 /exit0 |1/0F0E0S, full campaign and five45-step forward+two-replay windows pass |0s /1m46 /33.642s |
|f196 current |49615 /18:22:28 /exit1 |1/1F0E0S, original `cold death49613`; later assertions unreached |0s /1m40 /30.874s |

Both actual trees compiled within their single normal Maven lifecycle, unchanged
reviewed source, no separate build/repair/retry or target copy. Both journals pass
header/schema/length/cap validation and every serial, unique explicit metadata key,
actual P1/P2/host input and primitive before/after passivity check. Baseline has
52946 verified rows (52271 main+675 window steps), current49884 (49614+270).
**All49884 current keys match the baseline prefix exactly**; no metadata mismatch,
row-offset fit, interpolation or comparison past a mismatched key. Clocks are
actual bound ObjectManager VInt, LevelManager LF and ObjectManager frame, distinct
from outer movie, input-list and BK2 indexes. Phase is after original step+render.

The first unequal scalar is main outer/input/BK2 **48275**, binary sequence48545
because270 real rewind-window steps preceded it; **SSZ1, VInt48206/LF9027/OM9028**.
At48274 the complete declared tuple still agrees: P1`1885.B000/0A6C.6800`,
status`21`, vx/g`FD4C`, cameraYphysical/copy`0A0C`. At48275 both apply Left
(input4/action0), P1X`187D.F000`, Yfraction`6800`, vx/g`F400`, vy0, rings15,
no shield, same RNG. Baseline Y becomes **`0A64`**, status **`29`** (on-object bit3);
current Y stays **`0A6C`**, status **`21`**. Baseline cameraYphysical/copy`0A04`
versus current`0A0C`. At48276 both receive Jump16/A1 and retain the8px Y difference.
This pins the first standing/snap outcome; the actual object/contact writer is
**not identified** by this primitive-only journal. Do not blame a spring, cloud,
monitor, allocator or camera source from these values alone.

Later observed differences within the same explicit-key prefix:

- First hurt/state difference49161: baseline remains hurt with invulnerability120;
  current clears hurt with120, then49162 counters120/119.
- First ring-count difference49201 is current1 versus baseline0 (an earlier/extra
  increment, not a deficit). At49375 both lose all rings: baseline2→0/current3→0,
  both hurt/invulnerability120. No shield type or has-shield difference occurs.
- First current ring deficit49581: baseline0→1/current0→0. P1 Y is baseline
  `0634.0600`, current`0633.FE00`, a full16:16 difference`0800` (1/32pixel).
  The ring-count writer/ring identity is not observed. At49613 baseline1→0 becomes
  hurt/invulnerability120, while the ringless current becomes dead, matching R17's
  separate engine Mecha-parent HURT witness. No new death/contact owner is inferred.
- No VInt/LF/OM-frame, RNG presence/seed, zone/act/mode or load-bit difference
  occurs anywhere in the49884-row shared prefix. No clock-only first difference.

Binary v1 is a500-byte header plus41 big-endian int32 fields per164-byte record,
hard60000 rows/10MiB. Baseline8,683,644bytes SHA256
`2f872001b8f92012ec9a4076d2be7a99bbc54886d1a7f031aa223c0f4742bb09`;
current8,181,476bytes SHA256
`55d9eb54d984c559b149d31ff86619945de59d9ff3c8a312602db82f9f0bf40c`.
Root independently read both original binaries with actual schemas/explicit keys
and confirmed48274/75/76,49161,49201,49375,49581,49613. After preserving these facts,
both raw binaries are consumed under metadata-slot ALONE, no copies/archive/receipt.
Inspected XML/TXT reports were already consumed. The two temporary sources are
restored/removed and exactly two instrumented classes removed under the intended
writer lease. No live job, runtime edit or extra JVM; no rebuild is claimed.
Main config hash/mtime, regular private bytecopy, native checkouts,803 and70e refs
are preserved. Target last compiled f196+temporary observer; instrumented cold/helper
classes are now removed, so this is not a fresh maintained build. [Exact source hashes, commands and stopping limits](../../plans/2026-10-13-s3k-playable-completion.md#r13-matched-cold-campaign-baselinecurrent-comparison--2026-10-17).
Current cold completion stays blocked; no native/world/full-route certificate follows.


## Matched cold contact order R14 and R15 — 2026-10-17

Actual matched original-driver prefixes through48276 pass separately:
80339089 **1/0F0E0S** and exactf1961886 **1/0F0E0S**. Each has3explicit keys,
152verified events/passivity checks; later full-campaign final assertions are
intentionally UNREACHED. They do not close the current f196 death49613 or
certify native correctness. [Exact commands, epochs and Root-inspected evidence](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#matched-cold-contact-order-r14-and-r15--2026-10-17).

At48275, baseline retracting spring206/slot6 SIDE-pushes X1882→187D before
flat bridge204/slot32 TOP-snaps Y0A6C→0A64. Current bridge204/slot8 sees X1882
outside right1880 before spring206/slot9 pushes inside, returning NONE. Radius13,
bridge width20hex/uncollapsed/eligibility, actual VInt48206/LF9027/OM9028 and all
12emitted terrain-result payloads agree. Source-derived solid params remain
separate from actual callback arguments. Native geometry correctness is open.

The first recorded BEFORE48274 already differs in bridge204 slot32vs8. Its
admission/allocation/lifetime producer is UNOBSERVED before this window; no
allocator, Animal, geometry, restore or controller defect follows from slots
alone. Ordinary original45step/two-cycle replays remain in place. Root directly
verified both original bounded journals before consumption; temporary observer
sources/classes and4inspected reports are removed, no archives. This is an
honest contact-order frontier, not a natural native/world/campaign certificate.


## Matched cold pool producer R16 to R18 (2026-10-17)

Original Sonic ColdCampaign BASE803/CURRENTf196 each1/0F0E0S, **PREFIX ONLY** through
48276, identical inputs/driver/observer and real45-forward/two-replay windows.
Root directly inspected both original journals;18,603positive verified observations
and6restore markers per arm validate declared source/slot/ID/pool changes, not native
birth/correctness or complete hidden-world equality. [Exact audit/limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#matched-cold-pool-producer-r16-to-r18-2026-10-17)
and Plan preserve command/epoch/hash/count/skip/cleanup details.

Three spans43945→43956,44245→44254,47250→47262 self-close. First surviving48195
Animal629 deletion, then630/631, frees CURRENT6/7/8 before48201 admissions632/633/634:
BASE9/30/32 vsCURRENT6/7/8; same48213spring635 BASE6/CURRENT9. No allocator fault,
BASE clock fill at sparse48195 or unique cold parent callback claim. Source follow-up
is inherited EggRobo loc_917C0 special launch approximated by shared generic Animal
MAIN/frame2/X0/Y-400; native table launch/frame1/routine4or6 differs. Mode/velocity/
prior-render branch remains unrecorded. Shared correct R5 render rule is retained.

Current full campaign remains blocked at historical49613; later finals untested by
this prefix. No runtime fix/new input/native/media/extra campaign. Both originals
consumed after Root inspection/durable facts; original driver restored and compiled
instrumentation removed. Useful helper source remains temporarily owned pending
preservation decision. Independent release-inlet old-red SOURCE ONLY is separate.


## R29 native EggRobo animal launch unit — 2026-10-17

**Qualified component repair; original cold campaign remains blocked.** Same98d,
parent HEADb264/runtimef196 plus frozen Animal944a2933/EggRobob9e3474e; original
ColdCampaign9616921c and promoted helper8492f006 unchanged. Native pin
`044fa46725c71187399e13f5ddb70e11d32dc024`, loc_915F6 → ChildObjDat_919E6 →
loc_917C0/917E2/91800/9181C (own sonic3k.asm198614..198638/198774..198819),
word_2C7EA60996..61000. Line numbers apply only to this pin. Independent runtime
ROM read confirms first two rows Rabbit `FE00/FC00/0002CF32`, Chicken
`FE00/FD00/0002CEBA`. Native caller uses AfterCurrent; child's own first dispatch
consumes ONE full Random_Number, low bit selects the fixed table row, original
high word yields `-max($100,high&$1FF)`, captured parent renderbit0 signs X,
frame1/routine4or6. Initial draw does not translate. Flags5 require fixed render
hFlip for both X signs; collision radiusC is distinct from render height0.

The semantic deferred factory retains all three existing constructor signatures
and bodies. Private nontransient launch-mode/profile enums/facing scalars are
captured by the existing codec before and after own init, with no parent link,
new SST/API/schema/pin/allocator/physics change. The discriminator survives RNG
deferral clearing so later render arguments remain native. Generic POP, zone
animal selection, S1/S2 and the correct R5 previous-render lifetime are unchanged.

| Epoch / handle | Actual result / reached contract | Limits |
|---|---|---|
|R20 compiler70304 | missing pure getter; NO gameplay | bounded getter prerequisite correction only |
|R20 actual24069 |4/4F0E0S, original expected5/actual7 allocator assertion | native oracle/world replay UNREACHED; not old-red launch proof |
|R23 raw projection50817 |4/4F0E0S at same +1 assertion; four validated ABC rows | ABC4/4/7; capture unchanged, scan adds placements11/12/13 plus animal and removes placement0; registered projection only |
|R24 source0d2d | ONLY four existing public camera assignments moved before unchanged warm loop | ordinary placement inlet correction; no extra scan/wait/filter/count waiver |
|R25 corrected96038 |4/4F0E0S, exact four native launch failures AFTER ordinary allocation/RNG and twice full-world recreation/replay prerequisites | genuine independent component old-red, not coldAnimal629 attribution |
|R27 focused36866 |78/0F0E0S,10 actual classes from9 simple selectors | real-parent4 +pending-before-own-dispatch4 +RNG/render7 +S2/lifetime/S3K4 controls; no campaign/native route certificate |
|R28 guard51551 |6/0F0E0S, exact requested methods/0skips | fresh JVM; field/coverage/no-hydrate/source/API surface controls, no pin changes |
|R28 original campaign96020 |1/1F0E0S, SAME `cold death49613` | first original failure STOP, no retry; later full-route finals unqualified |

R25 expected/old actual: X-512 or+512/0, Y-256 or-421/-1024,
WALK orFLY/MAIN, frame1/2. Exact unchanged four-case oracle0d2d now passes.
Pending mode test5702 captures a normally allocated child BEFORE its own first
dispatch; constructor/capture/restore consume no RNG. Two forced whole-world
recreation cycles cover pending and initialized snapshots plus identical forward
replay/no redraw. RNG/render test81f7 checks actual drawFrameIndex arguments for
both velocity signs after deferral clears, with generic-render negative and null
profile/no-RNG controls. All new tests are green; no failing new test is committed.

All commands use current Main absolute wrapper from own cwd, normal lane, all3
original absolute ROMs, Java25.0.4.1, single EGL JTO plus CLI, nullAL/noambient/
Lua5.4/fork1/reuse/alphabetical. Focus/no-profile; guards `-Pguards`; original
campaign/no-profile/no `openggf.cold.*` or other new property. Queue0 for these
three qualified phases; Maven1m09/26.652s/52.053s respectively. Exact commands,
all six source hashes, actual10class/78case and6method identities, timing and
artifact disposition are in the [existing Plan](../../plans/2026-10-13-s3k-playable-completion.md).

The original campaign class/movie/session/step-render/45-forward/two-replay/
assertion ordering is byte-unchanged. Its sole failure remains original method454
→ verifyDeathEggCampaign506, expectedfalse/actualtrue at49613. Full-route final
load/boss/results/DEZ/history/release assertions are unreached; no post-fix
first-tuple/pool equality, native P1 correctness or unique coldAnimal629 callback
is implied by the unchanged death message. Fly-by scaling, complete subtype/
participant/width/donor/native-scene breadth and composed broad validation remain
open. No new campaign/native/media/diagnostic was run after STOP. All inspected
standard reports were durably accounted then consumed metadata-slot ALONE, no
copy/archive/receipt. The opt-in helper remains promoted; original driver has no
instrumentation. R29 preserves this bounded fix locally, not integration clearance.


## R32/R33 typed route evidence, R34 tool preservation — 2026-10-17

Exact803/committed781c matched original Sonic prefixes56945/70631 both1/0F0E0S,
no skips, through48276 AFTER original assertions/windows only. Each18603verified
observations;1323/1319records,78/81typed events; all2642records/159objects/fourreports
Root DIRECT inspected. Common2613actualkeys have equal clocks/load. First phase
43944Animal264priorrendertrue/false precedes self-closing pool spans43945→43956,
44245→44254,47250→47262. First surviving48195 now retires630slot7;629+631 current
48196 versus BASE629/63048202 and63148204. Actual new launch mode is observed on
629/630/631 at same48149/65/81: FLY/FLY/WALK, frame1, X-512, Y-279/-256/-281;
BASE MAIN/frame2/0/-1024. Same releaser count drops are boundary associations only.
Bridge204 still takesBASE32/current8 at48201, spring206BASE6/current9 at48213;
known camera Y differs48275. This does not close original full-route49613 or prove
native/unique-parent/callback/allocator truth. No arbitrary motion-only first split.

Preserve the tested opt-in typed helper in existing tools, header-only relative
f8ae; no installed campaign calls/property-only activation. Original Cold9616
restored, known compiled instrumentation removed, no rebuild. Same normal Main
absolute wrapper/no-profile/all3ROM/Java25/EGL/nullAL/noambient/fork1/reuse/alpha;
queue1/0s, Maven1m46/1m41. Originals consumed only after normal local evidence commit
under metadata_slot ALONE/fullhashchecks (10850465B), no archive/copy. Source/native
planning only follows. [Exact commands, hashes, control limits and accounting](../../plans/2026-10-13-s3k-playable-completion.md#r30r34-matched-typed-pool-evidence-and-preservation--2026-10-17).

## EggRobo entry and movement components — 2026-10-20

The first two invocations below retain their historical source scope. The
retained-render correction and third invocation at the end supersede that owner.

Isolated candidate worktree `ai-s3k-ssz-eggrobo-gameplay-20261020`, base
`685800dfa3bcb2dcb237fe9d10e3449e5df9c30e`, runtime owner SHA-256
`59a870641c81d26440daa443e6836a700923fc97077c123d66575ad0ec3d8401`.
`Obj_EggRobo` resumes subtype initialization only after the retained-render
placeholder and pointer-restore dispatches (`sonic3k.asm:180271–180303,198438`).
`loc_918C4` falls through into `sub_918E2`: fighter X velocity stays zero,
Y velocity/maximum are `$100`, acceleration is 8. The selected active routine
then runs in the initialization dispatch. `MoveSprite2` retains both 16.16
position fractions; `loc_915F6` launches with `-$300` when render bit 0 is clear
and `+$300` when set. No runtime art-scaling work is included.

The unchanged new clock/geometry oracles reproduce seven baseline assertion
failures: three entry/subtype cases, two fighter-velocity cases and both launch
directions. Baseline invocation completed 27/7F/2E/0S at 07:36:40Z; the two errors
were a new replay fixture locating moving actors by dynamic spawn X, corrected
to the captured actual SST slot. They are not native defect evidence.

Candidate invocation completed 27/0F/0E/0S at 07:40:17Z, Java 25.0.4.1, Maven 1m19s,
queue 0s (baseline queue 30s). `TestSszEggRoboClockGates` contributes 10 cases,
`TestSszEggRoboPresentation` 9 and `TestS3kSszEggRobo` 8. Command, from this tree:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl ALSOFT_DRIVERS=null \
  python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSszEggRoboClockGates,TestSszEggRoboPresentation,TestS3kSszEggRobo' \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Component replay captures the retained visibility bit, restored-but-uninitialized
continuation and active fractional positions separately, destroys the body/children,
then compares every registered world key after restoration and forward dispatch.
Existing gun/muzzle/child and retirement cases remain passing. These components
explicitly supply the fighter pairing bit; they do not prove natural pairing or
cold traversal. Existing placed-object tests retain their positioned-inlet scope.
The inspected streams contain the existing FAST-load/no-manifest warning; no
timing/readiness, native pixels, complete act, roster/donor/viewport breadth or
whole-route claim follows. Combined validation, integration and push remain open.

### Retained render visibility correction

Review found that the original 27-case qualification did not disconfirm the live
centre-based checks still implementing `loc_91548` and `loc_915F6`. Both native branches
read the preceding `Render_Sprites` bit 7. `loc_85B02` restores the continuation
without calling `Draw_Sprite`, so even a camera move must retain that bit until
an actual subsequent submission is rendered (`sonic3k.asm:180279–180303,
198455–198467,198512–198519,36318–36395`). This is a source-backed correction;
no extra baseline/native invocation was used to manufacture timing evidence.

Revised owner SHA-256 is
`3b75827f644ebb58e9f5d80cd93d58916038a2c761052d8d5cbc287626054285`.
The owner captures the retained bit and pending draw submission. Only submitted
draws refresh visibility, using the current native half extents: placeholder and
fly-by `$20,$20`; releaser `4,4`; fighter and converted releaser `$14,$18`
(`ObjDat3_9199A/919A6/919B2`). The undrawn restore dispatch preserves the bit.

Six additional component cases cover releaser centres 1/3/4/31 pixels outside
the viewport, a 32-pixel camera move while remaining in the coarse-X window,
the 4-pixel active releaser extent, fly-by pairing while the render box still
overlaps, and destructive restoration of the undrawn retained-visibility state.
The fly-by reaches negative velocity through its actual acceleration without
supplying velocity, phase or pairing-word state. These remain declared component
camera inlets, not cold traversal.

The same exact command above completed its third invocation at 07:52:07Z:
33 tests / 0 failures / 0 errors / 0 skips, Maven 1m19s, queue 1s. Inspected original
XML confirms Java 25.0.4.1 and the original absolute ROM path. ClockGates contributes
15 cases, Presentation 10 and the existing placed-object class 8. All stream lines
are the existing FAST-load/no-manifest warning. The earlier seven old-red oracle
failures and two corrected fixture errors are retained with their original scope;
the later 27-case pass is not evidence for the six added edges. No runtime/test
edit followed this 33-case run. The prior route/configuration/native acceptance
limits and integration obligations remain open.

A fresh independent source-only review of this revised owner reported no material
findings. Root checked the full runtime diff and all three original XML reports;
the retained-render P2 is resolved within this component scope. This review adds
no executed coverage or route/native-presentation qualification.

## Composed EggRobo confirmation (2026-10-20)

The reviewed EggRobo candidate is composed locally over Root HEAD
`691de5183b8563f32ff3f5ed35c96ba8709fe770`, with uncommitted runtime SHA-256
`95ce2280443d5a791eebd739b884f9a4924678a69b14ed7520e2baad5e01fe3f`.
This preserves Root's `AnimalType`, corrected class comment and complete R29
animal-release method. The three accepted EggRobo test files remain unchanged.
The R29 real-parent inlet (`f415f9037bb3ceab70d7ee9020fca3fa29c08d39c2f6274cd46ad87744b2fc17`)
now uses ordinary placeholder/restore/init passes before its original release
boundary, preserving allocation, RNG, native launch oracles and twice-recreated
world replay. The opt-in journal source
`9914bfc453b2ec3e6daf25b2fcd8ada53d3b8301420a7650abb0100141f64ad4`
records explicit source-epoch absence for old/new entry fields and reads position
fractions from their actual superclass owner, outside sparse phase keys.
A fresh independent consumer/R29-preservation review found no material findings.

Root's combined confirmation selected the same fourteen classes against frozen
compiled inputs using **`surefire:test` only, without recompilation**. Session
`70535`, chunks `f53742`/`134c9a`, ended **exit 0 / BUILD SUCCESS: 139T0F0E0S**,
Java 25.0.4.1, Maven 6.792s, queue wait 0s; terminal recorded 2026-10-20T09:02:07Z.
EggRobo contributes 33 cases and R29 animal launch/recreation another 8; Slot and
the mandatory S3K four provide the remaining 98. All 114 prior reports, 17,226
compiled classes and 10,734 source/POM inputs remained exact in bytes, mode and
integer nanosecond mtime across confirmation. Its 28 new reports remain in
`target/gameplay-composition-focus-20261020-confirm/surefire-reports`.

The first lifecycle's 139 test passes and lost Maven exit record retain their
original scope. Confirmation closes that concrete recording gap on unchanged
compiled inputs; it does not create 278 unique cases. The [combined confirmation
entry](s3k-bonus-stage-transitions.md#composed-slot-and-eggrobo-confirmation-2026-10-20)
contains all four current owner hashes, the complete fourteen-class selector and
actual command with the three absolute original ROM paths, headless EGL, null
OpenAL and Java/Lua environment.

Natural pairing, cold traversal, opt-in journal execution, broader loading and
roster/donor/viewport breadth, native capture/pixel/timing parity and whole-route
completion remain unqualified. The component tests' declared pairing/camera and
positioned inlets do not become cold-route evidence. Main integration and push
remain open; inherited matrix obligations above still apply.

## Jet allocation and own-dispatch lifecycle — 2026-10-20

The Jet now preserves the raw unmirrored table-row birth at (-$C,$1C), then
executes a separate INIT with frame6/priority5/extents$C,$10 and no MAIN refresh
or draw. MAIN refreshes the exact parent and chooses the signed-word velocity
frame before checking parent retirement. That check selects an undrawn
next-own-dispatch deletion; captured phase/draw/pending state survives recreation.

Candidate10 passes all12 new TestSszEggRoboJetNativeDispatch cases, the10 affected
Presentation cases,15 ClockGates cases and8 placed-ROM EggRobo cases.
The new checks include zero/one available after-current slots without retry,
both raw-birth flips, signed thresholds/wrapping coordinates, actual final
retired-parent refresh and twice-recreated exact-ID equal-coordinate families.
A deliberately pre-dispatch unregistered-parent snapshot is a missing-link
delayed-retirement negative; it does not certify unavailable final XY operands.

These45 SSZ cases pass inside the overall239T/3F/0E/0S focused run. All three
failures belong to the new HCZ world tests, not these SSZ assertions.
The [composition record](../../plans/2026-10-13-s3k-playable-completion.md#jet-dispatch-lower-platform-excursion-and-terminal-world-failures--2026-10-20)
records the exact command and source epoch. This is component/registered-world
coverage, not natural pairing, cold traversal, complete SSZ/native pixels.
Gun INIT, vertical flip/copied art priority and earlier broader gaps remain open.

## Gun dispatch and deferred retirement — 2026-10-20

Candidate13 passes all19 new Gun dispatch cases plus Jet12, Presentation10,
ClockGates15 and placed-ROM EggRobo8:64 SSZ cases, without skips. The Gun keeps
the raw unmirrored second child-table row, an undrawn INIT, MAIN's actual
parent refresh and fire before the retirement tail, and the literal cooldown
underflow. Pending deletion is undrawn and retires on its next own dispatch.
Zero/one/two-slot allocation prefixes and failed Shot allocation do not retry.

Exact-ID equal-coordinate families and raw/INIT/cooldown/underflow states
survive two fresh recreation cycles. The retired MAIN case preserves its final
refresh/fire through recreation; retired cooldown checks are component cases.
These are declared parent-word/dispatch inlets, not a natural paired-fighter
route or native-pixel certificate. The [composition result](../../plans/2026-10-13-s3k-playable-completion.md#resync-retirement-piece-standing-and-gun-validation--2026-10-20)
records the source epoch and240T/1F/0E/0S overall result; the sole failure is a
separate synthetic solid-contact fixture. Shot charging-parent lifetime,
copied art priority, vertical flip, aiming and wider swing behavior remain open.

## Charging Shot parent lifetime — 2026-10-20

Candidate14 passes all 22 TestSszEggRoboShotNativeLifetime cases and the
64 affected Gun/Jet/presentation/clock/placed controls. Production allocation
binds a charging Shot to its immediate Gun. Each charge entry preserves the
native flash, decrement and possible arming velocity/collision/SFX before
Child_DrawTouch_Sprite checks that Gun's retirement. A Gun in pending DELETE
already has the native retirement status although it is not physically deleted.
The Shot suppresses current touch publication and drawing, then physically
deletes on its next own entry. A successfully armed Shot releases the link;
its independent moving routine does not keep consulting the Gun.

The new checks cover both orientations, repeated/nonconsecutive V-int values
versus one decrement per routine entry, early/mid/arming retirement, exact SFX
ordering, pending versus latched-retired versus missing/unregistered parents,
public unparented/player-owned inlets, actual collision-list and CPU output,
and two fresh full-registry replay cycles at four boundaries with two distinct
equal-coordinate families. The production-manager case uses real Body alignment
and the real onPlayerAttack destruction owner before one actual same-pass
Gun-to-Shot dispatch. Its direct callback inlet does not prove natural selected
touch or a complete SSZ route.

The [composition record](../../plans/2026-10-13-s3k-playable-completion.md#charging-shot-lifetime-and-solid-fixture-closure--2026-10-20)
records the exact source and command:187T/0F/0E/0S, with no skips. Generic Gun
unload/native physical slot reuse, copied art priority, vertical flip, aiming,
swing breadth and raw Shot initialization remain separate obligations.

## Art priority copied at allocation — 2026-10-20

Candidate15 passes all 14 TestSszEggRoboArtPriority cases and the 86 affected
SSZ lifetime, dispatch, presentation, clock and placement controls. Raw bodies
remain low before the actual attributes write; a rejected fighter does not
acquire priority merely because initialization was entered. Accepted rows set
the body's high bit. Releaser conversion clears it before allocating children,
and the rising threshold later raises only the body. Gun and Jet retain their
own allocation copies through delayed INIT; Shot copies the Gun and keeps that
bit after releasing its charging-parent link. Public/probe/player-shot and the
existing three-argument binder preserve their previous low defaults.

Checks use the original ROM attribute rows and all 17 fighter mapping pieces,
actual CPU tiles, real manager masks and slot order, complete SAT collector
entries, prepared versus published frames, and two fresh full-registry replay
cycles at four boundaries with distinct equal-coordinate families. The
controlled render manager uses the actual S3K registry and no placements;
separate world-replay cases use the fixture's registered manager. Pairing words,
fire requests and manual routine entries are explicit component inlets.

The [composition record](../../plans/2026-10-13-s3k-playable-completion.md#eggrobo-priority-copy-validation--2026-10-20)
records the exact 193T/0F/0E/0S run and tested source. This closes copied-priority
coverage within those inlets. Ordinary physical display-bank/queue integration,
global native input/SAT admission, native pixels, fly-by scaled art, aiming,
vertical-flip carriers, broader swing/route and generic unload remain separate.

Original-ROM placement control also narrows the flip obligation: the complete
SSZ1 list contains 26 EggRobos, 18 with flags0 and eight with horizontal flags1;
SSZ2 contains none. No stock EggRobo row has initial vertical flags2/3. This
does not establish later runtime writers or implement the missing carrier.

## Signed word aiming validation — 2026-10-20

Candidate16 passes all20 TestSszEggRoboPresentation cases, including ten added
cases for the signed WORD BodyX minus Player1X branch. Equality selects the
native left-facing result; wrap and the8000 boundary follow SUB.W/BPL rather
than unsigned coordinate ordering. Both initial horizontal flags, a body and
gun that first faced right, and the inclusive Y-distance8 gate are exercised.
The original X getter executes once, and swing, movement, history, targeting
guards and charging behavior retain their order.

Literal coordinate expectations and public player-pose writes drive the real
fighter initialization. Two fresh whole-registry restore/forward cycles at
each tie/wrap boundary retain exact body/Gun/Jet IDs, complete world keys and
immutable frames. The declared pairing-word and manual routine-entry inlets
remain component controls. The selected17 classes pass203T/0F/0E/0S with no
skips; the14 art-priority cases and all affected lifetime/dispatch/clock/loading
controls pass again. See the [composition record](../../plans/2026-10-13-s3k-playable-completion.md#eggrobo-signed-word-aim-validation--2026-10-20).

This closes the scoped aim branch and its replay controls. Natural route and
touch coverage, vertical-flip carriers, full swing breadth, generic unloading,
scaled fly-by art, ordinary physical display production and native pixels
remain separate obligations.

## Vertical facing and child Y offsets — 2026-10-20

The isolated correction on `feature/ai-s3k-eggrobo-vflip-20261020`, based on
`cf3cfd0b715050d94a3c2ac7d550126ecd733a83`, preserves the body's initial render
bit1 through its existing immutable placement carrier. Native SetUp only sets
world-coordinate bit2; the inspected EggRobo body writer set never changes V.
Gun MAIN copies V after choosing delayed `$32` Y or its zero-word live fallback,
then mirrors signed `child_dy`; Jet MAIN mirrors its offset against live Y.
Each child's captured local V survives cooldown and the final parent-link clear.
Birth/INIT stay unmirrored and undrawn. Shot source is unchanged: CreateChild10
copies H only, and ShotY always equals GunY-4.

`TestSszEggRoboVerticalFlip` passes32 cases with the actual ROM, registered
managers, original child allocation and real INIT/MAIN. Public ObjectSpawn
flags0/1/2/3, pairing state and manual own-routine calls are declared component
inlets; no private state, snapshot seeds, reflection or policy bypass is used.
Literal controls cover C00 Gun/Jet anchors, C01/C02 native hover advancement,
delayed Gun versus live Jet, and the real releaser's zero-history fallback.
Natural public-player arming covers the96th cooldown underflow, latched drawing,
charging/armed/moving Shot V=false and the fixed -4 muzzle offset. Actual retired
parents still supply the last MAIN operands before undrawn pending deletion.

Original ROM mappings independently check complete CPU tile geometry, H/V flags,
palette, art priority and occlusion masks, including actual SAT admission/replay
output. This does not inspect the private SAT collector or prove native global
SAT admission. Sixteen checkpoint cases each run two fresh complete-world
restore/forward cycles: raw, after INIT, after MAIN, charging/cooldown95,
cooldown0, pending parent retirement and armed Shot. Equal-coordinate families
with opposing V retain exact Body/Gun/Jet/Shot IDs, the complete remaining ID
namespace, all registered world keys and immutable frames.

The affected18-class selection passes235T/0F/0E/0S, including all203 existing
C16 cases and the32 new cases. Java25.0.4.1 and the required surfaceless backend
and original absolute ROM paths were verified in the original reports; all three
ROM CRC32/SHA-1 identities matched. Source fingerprint
`399a688a364036ad62a9c96678998c6eb5733a04a382e2887535455df8981ee2`
was unchanged through execution. The [existing plan](../../plans/2026-10-13-s3k-playable-completion.md#isolated-eggrobo-vertical-facing-correction--2026-10-20)
records the command, source definition, rejected alternatives and execution limits.

This closes the scoped component carrier/offset gap. SSZ1's26 stock EggRobos
still have only flags0/1, and SSZ2 has none: no natural stock V-route proof is
claimed. Broader swing, natural route/touch, generic unloading/physical slot
reuse, scaled fly-by art/shared DMA, ordinary physical display production and
native pixels remain open. Broad smoke/guards, independent review, integration
and publication belong to Root and are not results of this isolated run.


## Literal EggRobo swing and paused history — 2026-10-20

The test-only continuation of accepted V carrier commit
`545aac9389baea8be5501c8b5efb3b2e80cf5975` extends
`TestSszEggRoboVerticalFlip` from32 to44 cases. Four flag0/1/2/3 controls use20
literal native waypoints through actual hover entry128, counting fighter INIT's
same-dispatch first hover as entry1. They check signed velocity WORD, full16.16
Y, delayed Gun versus live Jet anchors and independent mapping-based CPU/SAT
frames. `Swing_UpAndDown`, `MoveSprite2`, `sub_918E2`, `loc_9159A` and
`sub_91930` own the operands; no Java motion helper computes the expected rows.
The repeated negative endpoint at64 is FF08, followed by FF10 at65; the positive
endpoint at127 repeats00F8, followed by00F0 at128. The126-entry motion period
has zero displacement, with +/-15.5 extrema held at31/32 and94/95. Entry127
repeats entry1 motion but retains a different delayed history; entry128 closes
the two-word history as well.

Four pause controls reach entry61 with null-player dispatches, reject public
Player1 distance9 at62, and arm at distance8 after movement63. Gun stores95 on
firing MAIN;95 subsequent Body→Gun→Jet passes reach zero without movement,
history advancement, reaiming or following the moved Player1. The96th cooldown
underflows toFFFF after Body already ran armed; the next Body selects HOVER
and returns, then movement64/65 resumes with the literal fractions and C00 Gun
anchor. Gun Y is BFD/C05 by V; Shot remains H-only at GunY-4 (BF9/C01), with
H-dependent Gun/Shot X1E4/1EF or21C/211. Jet uses live Y and the signed velocity
bands for frames6/5/4.

Two new restore spots cover unarmed63 before the endpoint and naturally
armed63 at cooldown0 before underflow. Each H direction supplies equal-XY
opposite-V families, two destructive removal/fresh recreation cycles, exact
Body/Gun/Jet/Shot IDs and complete remaining namespace, immutable boundary
frames, four ordered forward passes and all registered keys at every pass.
Existing Shot link/restoration checks remain in the same helper path. The
Shot's own charging dispatches occur in the forward segment; the95 pause
passes intentionally dispatch only Body/Gun/Jet.

These are bounded component controls using declared pairing state, public
placement flags and Player1 poses, and manual own-routine dispatch. They do
not establish a natural BK2 route, native scene clock, physical SST reuse,
stock flags2/3 placement, route/touch completion, fly-by scaling/shared DMA,
ordinary physical display production or native pixels. The
[dated plan](../../plans/2026-10-13-s3k-playable-completion.md#literal-eggrobo-full-cycle-and-paused-history--2026-10-20)
records the focused run and its limits; Root retains broad smoke and delivery.

## EggRobo Shot shield descriptor — 2026-10-21

Independent inherited contract correction on base
`7bfb7e8eb316ea5a94c8b661e3278f9ab21f8602`. The verified locked-on ROM
(SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`) has SK SSZ entry
`$95522 -> $91518`. Fresh `loc_60DE` initialization and normal
`Delete_Referenced_Sprite` clear the SST, including `shield_reaction` byte `$2B`.
`AllocateObjectAfterCurrent` only scans empty slots; it does not clear them.
Gun `loc_91712` creates the shot through `CreateChild10_NormalAdjusted`
(`$84328`), which neither copies nor writes `$2B`. `SetUp_ObjAttributes3`
(`$84040`) and Shot `loc_91756..loc_917BE` also leave it untouched. Thus its
native descriptor is zero at allocation, charge, arming and motion, with no
shield deflection or elemental immunity. These source chains and bounded raw
instruction/data bytes were rechecked against disassembly commit
`044fa46725c71187399e13f5ddb70e11d32dc024`, with shipped `FixBugs=0`.

Only `EggRoboShotInstance`'s raw/profile shield flags and capability change:
`8/SHIELD_DEFLECT` becomes `0/NONE`. The old inherited `onShieldDeflect`
returned false, so the inspected stock consumer already produced ordinary
shield-loss hurt and left Hyper-touched shots unchanged. Adding radial bounce
or clearing collision would contradict the native zero-byte contract. No
shared touch, movement, charge, lifetime, input, art or rewind state changes.

`TestSszEggRoboShotShieldReaction` uses the existing VerticalFlip component
inlets: declared subtype2 fighter/pairing and public P1 pose, actual Body→Gun→Shot
allocation, child INIT and manual own entries. Both H directions assert literal
raw/profile zero and `NONE` at birth, all32 charge entries, arming and motion.
The hurt cases run an ordinary manager pass to build the completed publication,
then the public player-slot touch consumer. They verify actual visibility and
exclude competing published overlaps. ROM `$FF9A` supplies Shot radius24×4;
gap40 is shield-only and gap16 is body contact, away from the producing family.
Each BASIC/FIRE/LIGHTNING/BUBBLE case retains its shield at gap40, then receives
ordinary hurt, loses the shield and retains10 rings. Shot stays alive with
collision `$9C` and its next literal ±8 movement. Two actual published-shot
Hyper controls preserve collision and movement too.

The pre-fix queued class run on this base plus the new test executed12 cases:
2 descriptor failures,0 errors,0 skips. Each failure reports raw/profile8
instead of0 and `SHIELD_DEFLECT` instead of `NONE`; all8 real-shield cases and
both Hyper controls already passed. Queue wait233s; Maven execution1m13s.
This is an independently detectable descriptor defect, not an established
cause of selected input2200/6501 or another cold-route failure. No native
producer/caller identity or new route pass is claimed. Native320 Sonic with
donation off is the bounded fixture configuration; other participants, widths,
donors, natural contact, native pixels and route/rewind breadth remain open.
The static correction adds no mutable snapshot state; existing lifetime/fresh
family restoration coverage remains separate. Root owns combined validation,
independent adoption review and integration.

Focused green validation on the same base plus the correction:162 passed,
0 failures/errors/skips. Actual class counts were GunNativeDispatch19,
ShotNativeLifetime22 (including its existing fresh family replay controls),
ShotShieldReaction12, PoweredScreenAttack9, TouchResponseManager79 and
TouchResponseProfileMapping21. The generic genuine-deflection, elemental-immunity
and Insta-Shield/blocker controls remain green. Final fixture assertions also
verify Sonic, width320 and donation off. Queue wait543s; Maven execution1m24s.
The entire frozen tracked/untracked input fingerprint matched before/after
(`ba110c2a0e5ec6666fcffc788ce02d9f808bfc74d15253f89356efd165e8913d`,
14,121 regular non-symlink files, Path-sorted path/content SHA256 projection).
Only this prose result was added afterward; runtime and test bytes are unchanged.

Both runs used the absolute Main `tools/testing/maven_queue.py --normal`,
`-Dmse=off`, `test -B`, verified absolute S1/S2/S3K ROM properties, Java25,
Lua5.4, null audio, and `JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`
with `EGL_PLATFORM=surfaceless`. Ambient display and JVM overrides were unset.
The red selector was the new class alone; the green selector was exactly:

```text
-Dtest=com.openggf.game.sonic3k.objects.badniks.TestSszEggRoboShotShieldReaction,com.openggf.game.sonic3k.objects.badniks.TestSszEggRoboShotNativeLifetime,com.openggf.game.sonic3k.objects.badniks.TestSszEggRoboGunNativeDispatch,com.openggf.level.objects.TestTouchResponseManager,com.openggf.level.objects.TestTouchResponseProfileMapping,com.openggf.level.objects.TestPoweredScreenAttack
```

Actual tool preflight passed. The inspected change-based plan selected3,588
smoke classes, including all categories because the preserved TraceChaser
checkout differs from the new base's gitlink. Focused validation is proportionate
for this static Shot-only descriptor change; no broad or release suite was run.
All selected report identities/failures/skips were read, and the two task-owned
report/temp directories were consumed and removed without archives.

The single focused five-class run passes110T/0F/0E/0S: VerticalFlip44,
ClockGates15, Presentation20, GunNativeDispatch19 and JetNativeDispatch12.
Original reports verify Java25.0.4.1, surfaceless EGL and all three original
absolute ROM paths. The test/source fingerprint is unchanged before/after;
there was one source epoch and no retry. This is focused qualification, not
a broad smoke or full-suite result.

## Enemy-art production and restoration requests — 2026-10-21

Bounded leaf based on accepted integration `bf2c7c7211a7c71385e4649717ed80fa7383b0a9`,
fast-forwarded from parent local Main `c13be267fd06771b982679a5ec840a23c2a26b11` in
`feature/ai-s3k-ssz-enemy-art-requests-20261021`. Main is not integrated or pushed
by this leaf; the dirty TraceChaser setup is preserved outside its commit.

`Offs_LoadEnemyArt -> PLCKosM_SSZ` names `ArtKosM_EggRoboBadnik` at native tile
`$0500`. `loc_91570` writes the pairing word, queues that same archive, and
then deletes the fly-by. Stock ROM `$17B17E` starts with header `$09E0`: one
module with `$4F0` final words, exactly 2,528 bytes at VRAM `$A000`. The new
provider recipe carries explicit physical-publication intent through its
admission fingerprint and `PlcProgressSnapshot`; other recipes retain their
existing timing-only semantics and fingerprints. StarPost/explosion flushes
preserve the same intent. Incidental requests defer unarmed physical intent;
a retired title may append it while the incidental handle is still unfinished.
The title owner remains the admission authority;
preparation, cached payloads and ready unclaimed jobs do not retire it.

The coordinator's retained-owner path shares physical/timed capacity, ROM
preparation, owner binding and target setup with the existing global producer.
An internal admission callback validates physical header/ROM ownership before
timed submission, then commits the physical FIFO; timing ordinal exhaustion
cannot strand an unmatched entry. The public standalone enqueue retains its
existing immediate-head/deferred-successor behavior.
The provider claims its one retained handle; fly-by retirement uses the
coordinator's captured automatic claim. Neither claim publishes pixels. The
physical FIFO preserves immediate head initialization and deferred successor
initialization, actual child receipts, queued DMA and the sparse rewind journal.
Ordinary full-FIFO rejection leaves pairing and actor deletion in native order.

Rejected shortcut: exposing `MgzEndBossArtScaler` or projecting only its
mapping-selected `Pattern[]` cannot supply native `$FFD000` scratch writes.
The first `$7C` scale's mapping references 256 bytes but native DMA transmits 384.
Current module transfers clone payloads; they do not read then-current shared RAM
at drain. Full scratch/knownness, causal interrupted Kos writes/backreferences,
live DMA source lifetime and displayed `$A000` generation closure remain open.
There is no scaled-art completion, trace parity or natural-route claim here.

| Contract | Focused control | Scope |
| --- | --- | --- |
| Title retirement and pre-submission intent | `TestSonic3kTitleCardKosQueue#sszTitleRetirementRetainsOnePhysicalRequestAndRewindsItsPublicationIntent` | Real SSZ recipe; cached and queued-title cases; following runtime pass; retained ordinals and whole registered state |
| Stock physical tail and one claim | `TestS3kKosPatternPublication#retainedEggRoboRequestPublishesExactNativeTailWithoutAnAutomaticClaim` | Submitted/started/claimed/pending/published states; two forwards and both restore directions; recording sink |
| Rejection and malformed/foreign owner | `TestS3kKosPatternPublication#retainedRequestRejectionAndPreparationFailureLeaveNoUnpairedMutation`, `#retainedRequestRejectsEitherFullFifoWithoutConsumingAnOrdinal` | Both capacity domains; preparation and owner failures before enqueue |
| Native pairing/request/deletion order | `TestSszEggRoboPresentation#retirementPublishesPairingBeforeTheRequestAndDeletesEvenWhenTheFifoRejects` | Declared component inlet; full and available FIFO |
| Placed fly-by and actor-independent DMA | `TestS3kSszEggRobo#theFlyByArcsOffTheScreenAndOnlyThenDoesItsFighterSurvive` | Existing group 6 checkpoint inlet, 320/800 native Sonic solo; registered-world replay twice across deletion and publication |

Focused execution is recorded below. These component/checkpoint
inlets do not establish cold traversal or the complete viewport/donor/team matrix.
Movement donation does not own archive/claim semantics; additional route and
presentation coverage remains with the delivery lead. One new genuine title
parent/direct child adds FIFO occupancy and a `$4F0`-word DMA; queue ordinals,
polling and later release timing can change. No final route/release timing
qualification is inherited from earlier request-free runs.

Source epochs and first-run correction record:

- The first completed five-class command tested base `bf2c7c721` plus source/test
  diff SHA-256 `4eca1a617c0ea9398d9c3c7a1fda49c091910d59aa84f4937f957d72aa69fc81`.
  It finished 71 tests, 2 failures, 2 errors, 0 skips. Both boolean instances of
  the new retirement-order control expected one spy callback but saw zero:
  ObjectManager registration had replaced the injected spy. Both 320/800
  checkpoint instances read an empty pending-DMA list after assuming one step.
  Other 67 tests passed. This is not the final candidate's validation.
- After that run, source changes are confined to provider eager flushes retaining
  unarmed physical intent, and armed pending admission proceeding independently
  of unfinished incidental handles. Tests attach the spy after registration,
  wait on actual queued-DMA state and replay the observed bounded step count,
  and add both incidental-producer owner/restore cases. Gameplay service timing
  and native FIFO/readiness rules are not changed to satisfy these controls.
- The second invocation is frozen at source/test diff SHA-256
  `d55ad09dfb61101622f735f933532d411a4db54507ca968dbe742b232a885dc3`.
  Fingerprints are `git diff --binary bf2c7c7211a7c71385e4649717ed80fa7383b0a9 -- src/main src/test | sha256sum`;
  documentation and the preserved TraceChaser setup are excluded. Root's
  independent final acceptance review remains pending.

Final focused execution at the second frozen epoch: **160 tests, 0 failures,
0 errors, 0 skips**, Maven exit 0. The 14 selectors matched 15 actual classes
because both `com.openggf.game.sonic3k.TestSonic3kLevelLoading` (36 cases) and
`com.openggf.tests.TestSonic3kLevelLoading` (7) match the simple name. Other
completed classes were BootstrapResolver (6), DecodingUtils (3), the single mapping
corruption guard (1), StarPostInvitationArtAdmission (17), EggRoboPresentation (22),
KosPatternWorldRestore (2), KosPatternPublication (15), TitleCardKosQueue (15),
KosinskiModuleQueue (6), its DmaJournal (12), its GameplayIntegration (1),
Aiz1SkipHeadless (8) and placed SszEggRobo (9). Every new owner/order/replay instance
above passed. XML identities, failures and skips were inspected before removing
consumed reports; no validation receipt or log archive is retained.

Both commands ran from the isolated task worktree with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and `LUA_BIN=/usr/bin/lua5.4`. The final
reports confirm Java 25.0.4.1, the surfaceless EGL flag and all three absolute ROM
paths. Original ROM CRC32/SHA-1 identities match the repository table. Actual
category-tool preflight passed without executing tests. The first invocation
waited 233s for admission and Maven reported 87s including compilation; the second
waited 779s and Maven reported 89s including compilation. These are separate
queue/execution measurements, not a summed delivery wall time.

In the commands below, `OPENGGF_MAIN` names the existing Main checkout; exact
resolved paths and task/run identities are retained in the task registry.

```bash
# First epoch (71T/2F/2E/0S; the four new harness failures described above).
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  '-Dtest=TestSonic3kTitleCardKosQueue,TestS3kKosPatternPublication,TestSszEggRoboPresentation,TestS3kSszEggRobo,TestKosinskiModuleQueueDmaJournal' test -B

# Final frozen epoch (160T/0F/0E/0S).
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$OPENGGF_MAIN/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$OPENGGF_MAIN/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  '-Dtest=TestSonic3kTitleCardKosQueue,TestS3kKosPatternPublication,TestSszEggRoboPresentation,TestS3kSszEggRobo,TestKosinskiModuleQueueDmaJournal,TestKosinskiModuleQueue,TestKosinskiModuleQueueGameplayIntegration,TestS3kKosPatternWorldRestore,TestSonic3kStarPostInvitationArtAdmission,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits' test -B
```

The change-based plan selects 3,587 smoke candidates and guards for shared or
unclassified sources (including preserved setup). That combined coverage belongs
to Root under this delegation; neither it nor the release-only full suite was
launched by this leaf. Native timeline/release effects, scaled fly-by pixels,
complete route/configuration breadth and independent final Root acceptance remain
open. Local source review addressed preparation failure, exhausted ordinals,
alternate flush paths, title authority, retained claims and actor-independent
rewind ownership; it is not Root's final acceptance stamp.

## Incidental producers and armed title backpressure — 2026-10-21

Root's final source review of `c7fdd30471f91678924f096ddd59a4d08e4d0cd1`
identified `SSZ-C7-INCIDENTAL-ARMED-FULL` (P2). The runtime pump retained an
armed physical SSZ request while either FIFO was full, but the incidental
StarPost/explosion flush bypassed that capacity check and translated normal
rejection into an uncaught exception. This is a source-supported component
counterexample, not a reproduced natural route or measured timing failure.
The original commit, its failed first epoch and its final 160-test result above
remain preserved; none is relabelled as qualification of this followup.

The corrective policy remains the existing bounded engine admission model.
Pending physical title intent defers when unarmed or when either FIFO lacks
capacity. An incidental one-shot request can enter its own logical slot; if
none remains it is rejected without an ordinal, handle or invented retry state.
If the title consumes the last logical slot, title-first order remains intact.
The exact title lease, batch fingerprint and pending intent survive backpressure.
`tryQueueRingExplosionRestoration` keeps its existing actor-owned retry contract.
Native StarPost `sub_2D3C8` and CNZ `loc_6E778` issue one request without a
retained failed-request owner. Native `Queue_Kos_Module` scans beyond four slots
when overfilled; this bounded rejection policy does not claim overflow parity.

`TestSonic3kTitleCardKosQueue#incidentalProducersRespectArmedTitleBackpressureAndReplayBothFifos`
uses the existing cached-title owner inlet and declared physical-only FIFO setup.
Both producers cover physical-only full, logical-only full and one logical slot
remaining. Exact lease/fingerprint, ordinals, FIFO state, distinct incidental
versus title handles, eventual one-to-one title pairing, provider claims and
complete registered-world restoration are checked, with two forward/reverse
cycles. Real timed predecessors recover capacity through ordinary service;
only declared physical setup is cleared. No readiness or gameplay values are
supplied by comparison data.

RED was observed on unchanged `c7` production source with regression-only
source/test diff SHA-256
`464c75530593f65962cfc386197d7cd0aefd34102292cbee23f008fa12253ecc`:
6 tests, 6 failures, 0 errors, 0 skips. Cases 1/2 (physical full) and 3/4
(logical full) threw `Enemy art physical KosM FIFO is full` from
`queueEnemyKosEntry -> flushAdmittedEnemyKosEntries`; cases 5/6 (last logical
slot) threw `S3K KosM module FIFO is full` from the subsequent incidental
submission. Each producer failed its `assertDoesNotThrow` at the callback,
with the actual cause inspected in Surefire XML. Admission wait was 479s;
Maven reported 27.351s including test compilation. The Java 25.0.4.1,
surfaceless EGL and verified absolute S3K ROM properties were present.
Consumed XML/text reports were removed after inspection without an archive.

The provider-only source correction checks both FIFO capacities before flushing
an armed physical entry and checks the incidental producer's own logical slot
after that flush. It adds no snapshot fields, handles, retry state or capacity.
The GREEN invocation is frozen at source/test delta SHA-256
`bc1e73c56bd253e6b7fcd7e9d800833c7845a0e49b3443b141066263cb038c70`
relative to `c7`. No source/test edit occurs while either invocation is live.

GREEN completed on that unchanged `bc1e73c5` delta: **53 tests, 0 failures,
0 errors, 0 skips**, Maven exit 0. Whole title-card coverage ran 21 cases,
including all six new counterexamples; whole `TestSszEggRoboClockGates` ran 15
and `TestSonic3kStarPostInvitationArtAdmission` ran 17. XML testcase identities,
all six parameter instances, environment properties, failures and skips were
inspected. Admission wait was 430s; Maven reported 86s including compilation.
Java 25.0.4.1, surfaceless EGL and the original absolute S3K ROM were verified.
Consumed reports were removed after inspection; no receipt or archive is kept.

Both invocations used the isolated task checkout, with `OPENGGF_MAIN` denoting
the existing Main checkout in these repository-safe command examples. Exact
resolved commands and session/run IDs remain in the existing task registry.

```bash
# RED, unchanged c7 production plus regression-only delta 464c7553.
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  '-Dtest=TestSonic3kTitleCardKosQueue#incidentalProducersRespectArmedTitleBackpressureAndReplayBothFifos' test -B

# GREEN, provider correction plus identical regression, frozen delta bc1e73c5.
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  '-Dtest=TestSonic3kTitleCardKosQueue,TestSszEggRoboClockGates,TestSonic3kStarPostInvitationArtAdmission' test -B
```

Fingerprints use `git diff --binary c7fdd30471f91678924f096ddd59a4d08e4d0cd1 -- src/main src/test | sha256sum`.
The complete source/test diff from accepted integration `bf2c7c721` is
`62b4951dd7eecd1a9cca7d9bab08e5d8f88b76408ef1b8ed305b75d365937ca9`.
The historical 160 result is reused only for unchanged pair/FIFO/mapping and
other unaffected inputs, not presented as a fresh full followup run. No broad
smoke, release-only full suite, guard or controller qualification was launched.
Root retains the new independent delta review, combined guards and final-source
SSZ entry/release/controller/route qualification; old e253 route outcomes are
not adopted as proof. Shared scratch/scaled-art/DMA/presentation-generation
limits remain exactly as recorded above.


## Native320 controller qualification — 2026-10-21

This is the partial `3a0b7b5fa` record, retained as history. The post11051
follow-up below supersedes its current-route blocker without changing that result.

Frozen code: `39eca8c35c25433b7405b6bdc724f7c6111b55ec`, isolated worktree
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-1d563f14`, branch
`feature/ai-s3k-ssz-native-opening-20261021`. Only the owning complete/upper
`.script` and `.bk2` resources and this matrix change. The preserved TraceChaser
setup is outside the commit. Root owns combined checks and adoption.

Both routes use ordinary cold native320 Sonic + Tails, donor off, neutral P2.
Production `InputLogAuthorTool --script <owning.script> --out <owning.bk2> --game s3k`
compiled and round-tripped every input and both pads: complete20720, upper11051.
An independent exact row check agrees with both scripts and proves all11051 upper
inputs are the complete movie's prefix. Complete inputs after11051 are identical
to the original committed suffix, including the independent180-input destination
tail. Input or gameplay clocks, position, health, rings, mode, camera, phase,
readiness and RNG are never seeded or written by this controller repair.
No engine, fixture, native source, assertion or test literal changes accompany it.

All input/frame indices here are zero-based. The earlier e253 proposal was replayed
from an actual cold entry on this final code before suffix editing. It survived7912
inputs but ended at `(5045,1196)`, which fails the unchanged required `(5157,1004)`.
The chosen fresh jump7612 reaches the intermediate ledge; braking7662..7671 and
jumping left7672 lands on the cloud atX4948. Its observed contact7702..7708 and
normal `-0x700` launch7709 land Sonic on the upper walkway, reaching the existing
wall stop `(5157,1004)`. Seven finite cloud branches were tried; the other six
failed to reach that waypoint. No fitted position delta was applied.

The earlier MTZ inputs left five hits, stopped attacking and died10724. The
maintained `SszBossInputAuthorTool` cold prefix8440 / `mtz` / max1600 authored879
ordinary inputs, observing the killing hit9318. A separate cold replay reproduced
that hit and the escape9501. Of three finite pad approaches,12Right/12Left at9319
followed by neutral433, the existing30A jump9776 and neutral1245 reaches the actual
receiving platform `(5888,140)`. The other approaches miss the pad's horizontal activation
window. GHZ's authored fight4400..4782, its real pad landing5151, the shot dodge2190,
stair turn2950/jump3070 and arena-entry jump4138 are retained in both scripts.
These production authoring branches are exploration; the independent maintained
methods below supply the fresh gameplay/rewind evidence.

Final selected-movie command, with the original absolute ROM (SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`). Set `OPENGGF_MAIN` to the
existing absolute Main checkout containing that ROM; the resolved command remains
in the task registry:

```bash
env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS -u MAVEN_OPTS -u DISPLAY \
  -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal \
  -Dmse=off \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  '-Dtest=TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport+coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents,TestReferencePathInputAuthor#recoversTheReferencePathAfterADeliberateDivergenceAndReplaysUninterrupted,TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost' \
  test -B
```

| Exact maintained method | Result / XML test seconds |
| --- | --- |
| `TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` | PASS,2.157s; initially inactive checkpoint becomes index2, savedX`$640`/savedY`$5E8` physically before7912; survival throughout |
| `TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | PASS,4.564s; GHZ camera/renderer4472, first receiver`(512,1420)`5826, real elevator-bar hold6300, unchanged`(5157,1004)`7911, both replicas defeated/escaped and released`(5888,140)`11050;25 whole-registry windows, two45-input restore/replay cycles each |
| `TestReferencePathInputAuthor#recoversTheReferencePathAfterADeliberateDivergenceAndReplaysUninterrupted` | PASS,10.158s; ordinary recovery and independent uninterrupted endpoint replay |
| `TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | FAIL,6.838s; death at14554. Authored snapshot windows through14450 complete; Mecha, later windows, results, spiral flip19683, actual DEZ load/assembly, outgoing-history retirement and incoming replay/release are unqualified |

Exact XML inspection:4 tests,1 failure,0 errors,0 skipped. Reported queue waiting0s;
Maven execution49.833s; wrapper elapsed51.01s. Maven includes build/test execution;
XML durations measure tests. Initial strict-post diagnostic also passed1/0/0/0
(2.477s JUnit class,27.729s Maven,0s queue); the first upper attempt failed10724
(7.474s JUnit class,33.509s Maven,0s queue) before the MTZ controller repair.
Those earlier results do not replace the final selected-movie checks.

One bounded cold replay of the retained late suffix corroborates the terminal
fall:14331 starts `(5330,236)`,14450 is `(5997,77)`, then Sonic stalls against the
wall atX6261 and falls toY1221 at14554 with16 rings. No Mecha was allocated.
This is a controller-route blocker, with no native-cause or engine-mismatch claim.
The complete route is not green and is not ready as a complete-route replacement.
A continuation must author the late upper-gap approach from ordinary input and
then rerun the affected complete method. Deliberate death/reload, the later live
history boundary, wider viewports, donors and other teams are not established by
this bounded opening/upper work.

The native movement/descriptor leaves were independently source-reviewed by Root;
actual selected-shot producer/birth/charge/release/facing/XY identity remains
**unknown**. Controller authoring does not establish that chain, native parity,
native timing or native pixels. Old c13 success is not an oracle. No old baseline
was rerun or fitted here. No broad smoke/release suite was launched. Consumed
owned probe images, CSVs and logs are removed after input promotion; useful
controller inputs are now maintained resources, not retained diagnostic archives.

Final route-resource binary diff SHA-256 against39eca8c35:
`fe924468da04cd537205ae93076a7a2ade0d58bde3f1a0b54bf210b7d01d3d2e`.

| Resource under `src/test/resources/routes/s3k/` | SHA-256 |
| --- | --- |
| `ssz1-sonic-tails-cold-complete-320.script` | `1638d01dfd65defe61e75c40b2a38d4ab18654492164a864bd796ecada681160` |
| `ssz1-sonic-tails-cold-complete-320.bk2` | `e84a5ee3d85752049c643f8eca15c548bd2472d9d069dae7689dc6eaa9abc7da` |
| `ssz1-sonic-tails-cold-upper-320.script` | `65dbd58d4729b927873314c283435aedb8527fe22dcf254fc9d826c2ea8b70cf` |
| `ssz1-sonic-tails-cold-upper-320.bk2` | `221028bc88a32a4c3353b4f47ae6778b198fa1a931aec498d8052362c8e9246c` |


## Post11051 complete controller qualification — 2026-10-21

The follow-up keeps runtime `39eca8c35c25433b7405b6bdc724f7c6111b55ec` and
parent commit `3a0b7b5fa4c1362be88d52f4dcc34aefdd519d54` frozen. Only the complete
route's `.script`, production-generated `.bk2` and this matrix change. The upper
script/BK2 remain byte-identical. `3a`'s three passes/one failure and Root's
independent source/data NMF review remain historical; this is a separate input
qualification, with its exact follow-up task/run association in the existing
continuation registry. No runtime, dependency, fixture, utility or test edits occur.
Root's newer dependency/caller integration is outside this tested epoch.

All indices are zero-based. The new cold observation reproduces the old fall at
14554, but shows earlier controller problems. A fresh left jump11160 avoids the
recorded hurt near the fighter placement atX5440; it does not identify the actual
selected damage producer. After landing11769, release/repress and steering reach
the solid cloud and the seam bouncers. A real bouncer contact11993..11999, launch
12000 and leftward brake land Sonic across Y=0 on the lower ledge at`(3129,4076)`.
This is ordinary `sub_45170`/`loc_451DC` cloud behavior and `loc_10C26` wrap input,
not a world-position write. The unsuccessful rightward continuations fell back
through Y=0, so they were rejected.

A normal run-up then catches the rotating platform's carrier at12285; a fresh
right-side jump12345 releases it through `loc_45F74` toward the next column.
The first release attempt fell back into the upper corridor; the selected release
stays on the lower route. The first spike approach jumped too late and recorded a
spike contact at`(5058,3826)` and hurt12780. The selected held Right+Jump span12660..12719 and fresh jump12755
clear that approach without another hurt.
Braking12850 for40Left lands inside the real pad at`(5376,3824)`; its normal
charge/rise reaches the receiving platform`(5376,3276)`, alive with18 rings.
The earlier braking alternatives stop outside its horizontal activation window.
Finite production `GameplayInputBranchTool` candidates answered these individual
contact/landing questions. They are exploration, not fresh-pass or native proof.

Exact controller-data comparison against3a:

- Complete length remains20720; P2 remains neutral. Exactly841 P1 rows change,
  first11160 and last12889. No row at or before the upper endpoint11050 changes.
- The first11160 rows are identical to3a, including every one of the shared11051
  upper inputs. Both upper resource files are byte-identical to3a.
- Every original controller row from12890 through20719 is recovered exactly:
  7830 unchanged rows, including the180-input destination tail. This is input-row
  equality; no whole-state/native-journal equality is inferred.
- `InputLogAuthorTool` compiles the maintained complete script and round-trips
  every frame/both pads through `Bk2MovieLoader`. Independent exact script/log
  checks agree. The BK2 contains only `Header.txt` and `Input Log.txt`, no state.

The selected complete movie then ran independently through `GameplayCaptureTool`
from ordinary cold native320 Sonic + Tails, donor off, without setup seeds.
All20720 inputs survive. The retained later controls reach Mecha/results/spiral
and the actual DEZ scene; final P1 is`(370,2476)`, with native CPU Tails present.
This fully drawn engine capture uses the same canonical ROM, EGL/null-audio
launch environment as the method check below. It is not native-emulator parity
or pixel acceptance. Queue waiting0s; Maven execution19.710s; wrapper20.70s.

Final maintained whole-class command (`OPENGGF_MAIN` is the existing absolute Main
checkout containing the original ROM, SHA-1`CFBF98C36C776677290A872547AC47C53D2761D6`):

```bash
env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS -u MAVEN_OPTS -u DISPLAY \
  -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  -Dtest=TestSszColdRouteCapture test -B
```

| Exact maintained method | Fresh result / XML method seconds |
| --- | --- |
| `TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | PASS,11.945s; three killing hits, no leader death, unchanged spiral flip assertion19683,50 two-cycle45 full-registry windows, actual DEZ load20550/assembly20551, retired outgoing history, native destination roster/player/camera binding, fresh rings, intro ownership and released control with both incoming replay windows |
| `TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | PASS,4.863s; all unchanged early waypoints and25 two-cycle45 full-registry windows |

Exact XML:2 tests,0 failures,0 errors,0 skipped; class17.090s. Reported queue wait0s,
Maven execution43.454s, wrapper44.52s. Assertions, literals, replay key sets,
world/mode/load boundaries, lives and death checks are unchanged. Strict-post and
reference-author methods were not rerun as suffix-iteration ceremony: their
qualified3a inputs and runtime are preserved. No broad/release suite ran.

This closes the maintained ordinary native320 paired complete-route blocker on
this frozen epoch. It does not qualify Root's later combined tree, other teams,
widths/donors, deliberate death/respawn breadth or native comparisons. Selected-shot
producer identity remains unknown; no native cause, clock, parity or pixel claim
is added. Useful13400 controller proposals are retained for Root accounting;
consumed raw probes/captures/logs and inspected XML are removed, without deleting
build classes or creating result archives/pass caches. Root owns independent
review, adoption and combined validation.

Complete-resource binary diff SHA-256 against3a:
`ecaac09c295b580e6f6bb02afbb6fc89e7c818f1083d5a5ffc38ce8c8a73d578`.

| Resource under `src/test/resources/routes/s3k/` | Final SHA-256 |
| --- | --- |
| `ssz1-sonic-tails-cold-complete-320.script` | `f0abe1fb7a7dcc2cba05980862f581471a639c3e702843e2e1c3bc1a6ef976cc` |
| `ssz1-sonic-tails-cold-complete-320.bk2` | `670bd74f17b069cf81d92b1f0c98d5acbe8213e6566a709b5b194bb1692a917a` |


## Solo Tails320 replica controller on frozen production (2026-10-21)

This lane restores **only the cold-zone solo Tails320 two-replica route** on
`39eca8c35c25433b7405b6bdc724f7c6111b55ec`, in
`feature/ai-s3k-ssz-tails-route-20261021` at
`<OWNED_WORKTREE>`. The supplied branch remains at
`efd605142603146509995cffbb56a6b48f9c1c1b`; its clean setup, ignored ROM/configuration
and TraceChaser checkout `322851cbfa2e1c1803332142e11ab74b4eb34eea` were preserved.
The placeholders name the registered app checkout, original Main checkout and
owned durable task-output directory; exact paths are in the ownership registry.
The TraceChaser checkout appears as an inherited gitlink difference and is excluded from this
controller commit. No runtime, physics, art/timing owner, author utility,800 movie,
complete320 movie, Mecha geometry, results, DEZ or history-isolation oracle changes.
This is an engine cold-zone fixture, **not ROM power-on or native pixel parity**.

The smallest exact maintained320 invocation ran once before authoring. Through the
Main workspace's current absolute queue wrapper, Java25.0.4.1 and surfaceless EGL,
JUnit Console selected
`--select-iteration=method:com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[0]`.
Actual XML was `[1]320`,1 test/1 failure/0 errors/0 skips: the original line187
`ghzSeen && mtzSeen` assertion failed after8,200 surviving inputs and the original
rewind comparisons. That is fresh39 evidence; the earlier cf3/c13 results are historical.
The original movie still allocates GHZ4452, lands its eighth hit4895 and releases
the first transport5476. It runs uncurled into EggRobo5571 at(908,1392), loses25
rings and drops below the intended route; its next jump is5596. MTZ never allocates.

The maintained source is `ssz1-tails-solo-cold-replicas-320.script`; its BK2 was
regenerated by unmodified `InputLogAuthorTool` and round-tripped through production
`Bk2MovieLoader`. All8,350 ordered P1/P2 rows also match the independently authored
proposal; P2 is neutral throughout. Original rows0..5534 and5565..6739 are exact.
The first change is30 ordinary Right+Jump inputs5535..5564, which avoids the first
EggRobo body. From6740 a ground jump/flaps clear the pillar; Right from6860 descends
into the genuine MTZ allocation7045. Existing `SszBossInputAuthorTool` authors7046..7651;
the player lands all eight accepted MTZ hits. Neutral after7651, a single ground
jump7942 and neutral to8349 board the raised pad and reach the top. No position,
RNG, health, phase, ready, clock, save/history or trace state was supplied or changed.

Rejected bounded alternatives on the same frozen runtime: jumps5520 and5550 avoid
the first hit but suffer later hits6071 and6002. Overlong pillar flight allocates
MTZ7512. Earlier grounded jump6728/shorter flight allocates MTZ6995, but reusing the
successful fight inputs50 frames earlier accepts only one hit and dies7966. It
was rejected rather than retiming runtime or claiming a generalized controller.
Single pad jumps7882/7912 miss boarding;7942 succeeds. All exploratory restores
used the engine's own registry and held-input history. Their results alone did not
qualify the maintained route.

Two **fresh separate JVM** ordinary cold replays of the final maintained BK2
(separate read-only `GameplayCaptureSession` observer, no author search or rewind,
`renderFrame` every input) agree on every event below and the final state. They
retain three lives, solo Tails with no followers, no death/life loss, both bosses'
eight monotonic health drops and despawn, both transports released, zone10/act0,
(5888,144),34 rings, no player object control and no centered arena projection.
Their complete per-input player/owner projection digest is identically
`bbaaa49f88fc4d61e373562410d3d4d220c9b2d1e09d0cccd1f95990fa1d90de`;
that is a bounded read-only projection, not a whole-world or native comparison.
Maven-reported times were7.594s and7.234s, with zero printed queue wait. Two end
stills and the useful controller proposal are retained outside the repository at
`<TASK_OUTPUT>/`.

| Actual owner | Zero-based ordinary input |
|---|---|
| GHZ allocation; accepted hits7..0; despawn |4452;4551,4697,4729,4762,4794,4829,4861,4895;5079 |
| GHZ pad charge/rise/settle/release |5218/5368/5411/5476 |
| Tails carrier control acquisition/release |5779/5824 |
| MTZ allocation; accepted hits7..0; despawn |7045;7279,7324,7385,7455,7516,7587,7619,7651;7835 |
| MTZ pad charge/rise/settle/release |7967/8116/8171/8236 |
| Final8350-input movie |(5888,144),34 rings, released controls/camera; both bosses absent |

Root authorized a scoped controller-data adaptation after the unchanged-oracle
trial:320 replica count8200→8350 and endpoint rings3→34. These are authored movie
outcomes, not ROM constants. All53 original320 replay indices retain identity
mapping **old i→new i**, as additional controller samples; none is dropped or
claimed to retain an obsolete phase label. Sixteen actual owner boundaries are
added:5779,5824,7045,7279,7324,7385,7455,7516,7587,7619,7651,7835,7967,8116,8171,8236.
Thus69 windows retain the same45-input full-registry restore and forward-replay
assertions and normal drawing. Every original gameplay/HP/death/roster/despawn,
zone/act/end-position and camera-release assertion remains. The complete320 and
all800 conditional expectations remain byte-for-byte unchanged. A literal inverse
of just this count/ring/added-index/comment patch recreates the original test file.

The old complete320 movie is a separate outstanding route: it shares the new
replica movie's rows0..5534, then deliberately retains its original controller.
No new shared prefix, Mecha arrival, final fight, DEZ handoff or isolated-history
pass is inferred from this replica result. The shared-prefix break is explicit;
future complete-route authoring must replay actual reached events rather than
blindly shifting its unreached Mecha/DEZ windows.

The focused maintained 320 check passes 1/0/0/0, actual XML `[1]320`, with all 69
whole-world windows and original semantic assertions (8.717s Console test time).
The only changed expected values are the authorized controller data above.
The affected whole class then completed **11 tests, 4 failures, 0 errors, 0 skips**:
all five GHZ-approach widths and both replica widths pass. The remaining failures
are in the byte-identical original complete-route methods and movies:

| Actual XML identity | Width | Exact failure |
|---|---|---|
| `coldMechaEntryRetainsItsNativeWorldBox(int)[1]` |320 | `approach death at 9465`, line88 |
| `coldMechaEntryRetainsItsNativeWorldBox(int)[2]` |800 | `approach death at 9560`, line88 |
| `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` |320 | `death at 9465`, line242 |
| `coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` |800 | `death at 9560`, line242 |

These are fresh results on 39, not a whole-class pass or a fresh baseline suite.
The 800 complete failure was not in the initially supplied historical cf3 list;
it is explicitly unresolved here. No Mecha world-box, accepted final-boss damage,
results, DEZ load or history-isolation qualification follows from these deaths.
Printed queue wait was 15s, Maven time 50.652s and XML suite time 23.647s; the queue
estimate is not a measured runtime, and compiler/end-to-end delivery times were not
measured separately. All 11 identities, complete failure messages/assertion frames,
skips and XML environment properties were inspected. Consumed exact owned reports
and exploratory diagnostics were removed, preserving build classes/native extraction
and the useful authored inputs; no raw archives, class cleanup or pass receipts.

Validation environment: original absolute ROM
`<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen`,
4,194,304 bytes, SHA-1`CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32`63522553`;
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`, audio disabled by
the existing session settings, explicit `ALSOFT_DRIVERS=null` for final validation,
`DISPLAY`/`WAYLAND_DISPLAY` and all ambient JVM option variables unset. Production
`src/main` path-and-byte SHA-256 remains
`00c3cae4b58fd67022b1c5049303bd4b9b4313cecff08b4ebc3a30ac5a7a7f27`.
The final `src/test` path-and-byte SHA-256 is
`64cc0daf8be79e9e781b49f572ee969a29471b97cce8231088ec5c8eef04bf01`;
the binary `src/main`/`src/test` diff from 39 hashes to
`185cc081958637c01280635e50b59158abe56f7ac2cdd908d700241c9fdadef2`.
The final BK2 SHA-256 is
`d963211317a803854e65061b8b3e4df02a88b1fa505baaf4dda3fa1efbcd451d`.

Focused commands used the exact Console selector above with `exec.classpathScope=test`,
`--fail-if-no-tests --include-engine=junit-jupiter`, and unique owned report directories;
`test-compile` preceded the candidate invocation. Whole-class selection is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS -u MAVEN_OPTS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null \
  python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Dtest=TestSszTailsColdRouteCapture -Dopenggf.surefire.reports=target/ssz-tails-whole-class test -B
```

Validation is proportional controller coverage, not broad smoke/full-suite or a
new native-runtime qualification. Four mandatory S3K runtime controls are not
triggered because runtime source is unchanged. Root owns combined delivery,
review and integration; this lane makes a local commit only, without push,
Main integration or worktree/branch removal.

## 2026-10-21 — solo Tails320 complete controller on frozen39 (R2)

This continuation starts from immutable replica commit
`5bad0be8e034472fb6c753cc068c1783061b3e30`, parent runtime
`39eca8c35c25433b7405b6bdc724f7c6111b55ec`, on
`feature/ai-s3k-ssz-tails-route-20261021`. It supersedes the preceding complete320
frontier, without revising the R1 measurements. Only complete320 script/BK2,
320 controller-derived data/comments in `TestSszTailsColdRouteCapture`, this
matrix and the dated plan change. Production source, replica inputs and all800
resources/branch expectations remain exact. The separate Root MTZ-orb candidate
was not adopted or used to infer a route cause. Supplied branch and dirty
TraceChaser setup remain preserved; Root owns composition and integration.

The maintained complete320 movie now contains **14923 ordinary inputs**. Its
rows0..8349, both controllers and ordering, are identical to the qualified8350
replica movie, including MTZ pad release8236 and endpoint8350. Compared with the
old13667-input complete movie, the first different row is5535 and total length
increases1256. The qualified prefix replaces the obsolete complete-prefix fight;
no old trajectory is a runtime oracle. `InputLogAuthorTool` generated the BK2
and its production-loader roundtrip passed. An independent comparison checked
all14923 ordered P1/P2 rows against the selected authored proposal and the exact
8350-row shared prefix. No ZIP bytes or savestate shortcut were authored.

The route jumps during the lower bouncy-cloud countdown9084, takes its native
launch9088 and crosses the upward camera wrap9214. It flies east to the actual
5376/3824 pad, rides to5376/3312 and releases at9988. A later flight clears the
spike barrier; the ordinary spring at6336/3272 launches Tails at10698. The
retracting-spring zigzag launches at10904/10964/11053, then Tails jumps onto the
6720/2480 pad and centers for the final lift. Mecha actually initializes11571.

Rejected ordinary navigation is useful evidence, not a reason to tune physics:
flight above the seam at the wrong X exhausted before the camera wrapped;
premature Right returned to walls or the original upper route. Continuing Right
past the lower pad fell through the wrap and died; braking onto it transported
correctly. Running at the spike wall stalled safely, while an early flight met
EggRobo on landing. The later jump/flight crossed the barrier and reached the
real spring. Staying Right on the next landing stalled at the side spring;
turning Left entered the native zigzag. Running through the final pad at ground
height missed its top; a jump and small centering movement activated it. These
are controller negatives on39, with no production omission established.

Two separate fresh JVMs independently replayed the proposed committed BK2 from
ordinary cold SSZ10:0, solo Tails, default world/player/seed/save inlet. The
read-only temporary observer stepped and drew every input, observed public
owners, and enabled the same late live-history configuration as the maintained
test; it did not restore a branch, hydrate a trace or write gameplay state.
Both runs agreed on all6573 tail-state rows and every observed event. They saw
GHZ/MTZ/Mecha each take eight accepted health drops without healing, no player
death or life loss, and three lives throughout. Accepted Mecha damage occurred
at11807,11886,11946,12005,12128,12205,12251,12613. The eighth hit precedes genuine
defeat landing12742, results12862, launch owner13339 and actual DEZ load14922.

At initialization both runs observed camera6560/1472 (`$19A0/$5C0`), Mecha
6912/1632 (`$1B00/$660`) and boss bounds6592..6848 (`$19C0..$1AC0`). Final pad
control releases11600. Deferred DEZ assembly occurs on the next ordinary input:
zone11 act0, Tails48/2480, rings0, radius15 and feet2495, no object control,
identical main playable/camera focus. Outgoing SSZ live history reaches97;
the fresh timeline is1 after assembly and31 after30 ordinary inputs. The load
boundary PNGs were black and discarded as regenerable, uninformative images;
no visible destination or native-pixel qualification follows. Normal production
drawing is exercised by both observers and the maintained checks.

The two fresh runs preceded the explicitly authorized controller-data adaptation:

|320 authored data | Old → new | Actual basis |
|---|---|---|
|Mecha-entry first window |10443 →11324 | On final pad top before activation11337 |
|Mecha-entry second window |10645 →11524 | Final lift, before Mecha init11571 |
|Mecha-entry input limit |10784 →11572 | Includes actual first initialized dispatch11571 |
|Complete movie count |13667 →14923 | Includes actual load-producing input14922 |
|Derived history start |13547 →14803 | Existing `movie.getFrameCount() -120` formula unchanged |
|Complete replay indices |47 →98 windows | All47 originals retained,51 reached owner boundaries added |

The51 additions are grouped by actual observed owner boundaries, rather than
an offset applied to unreached gates:

|Owner coverage | Added input indices |
|---|---|
|Prefix endpoint/carrier latch and releases/bar |8350,8529,8540,8683,8781,9022,9041 |
|Cloud jump/launch/player and camera wrap/east departure |9084,9088,9174,9214,9250 |
|Lower pad activation/lift/arrival/release, receiving exit |9743,9892,9923,9988,10040 |
|Spike-wall jump, vertical spring, zigzag, upper exit |10221,10698,10904,10964,11053,11132 |
|Final pad approach/activation/lift/arrival/init/release |11324,11337,11484,11524,11535,11571,11600 |
|Mecha entry/attack phases and all eight accepted drops |11619,11683,11763,11807,11886,11946,12005,12128,12205,12251,12613 |
|Defeat landing/results |12741,12742,12862 |
|Launch allocation/stages/player handover/spiral jump/hover |13339,13369,13469,13484,13565,14689,14719 |

All98 windows retain the original45-input full-registry capture/restore and
forward-replay comparisons, input-history restoration and drawing. Native camera,
world geometry, eight-hit/no-healing, death/solo roster, actual DEZ load/fresh
rings/main/camera identity/radius/feet, retired SSZ lock and history-isolation
predicates are unchanged. No ring expectation changes in R2. Every800 literal
and the entire replica method remain unchanged. A literal inverse of the six
320 data/comment substitutions exactly recreates the5bad test source; no oracle
logic, assumption, skip or gate was added or removed.

The exact320 maintained selection passes **2T0F0E0S** (actual XML identities
`coldMechaEntryRetainsItsNativeWorldBox(int)[1]` and
`coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]`, XML12.523s).
The affected whole class then completes **11T2F0E0S**, XML30.871s, Maven57.799s,
printed queue wait4s. Both320 final methods, both replica widths and all five
GHZ-approach widths pass. The only remaining failures are unchanged800:

|Actual XML identity | Exact assertion |
|---|---|
|`coldMechaEntryRetainsItsNativeWorldBox(int)[2]` | `approach death at9560`, line89 |
|`coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | `death at9560`, line251 |

This is a bounded320 completion result, **not a whole-class green claim**, an800
completion or a true ROM-power-on route. There is no new native runtime or pixel
qualification. All11 XML identities, assertions, skips and environment properties
were read. The two unchanged800 failures match R1 by identity and assertion frame.
No broad smoke/full suite or repeated baseline ceremony was run; unchanged
production does not trigger the four mandatory S3K runtime controls.

Commands ran from `<OWNED_WORKTREE>` through the current
`<MAIN_WORKSPACE>/tools/testing/maven_queue.py` absolute path. Each used Java25,
Lua5.4, surfaceless EGL and null audio, with ambient display and JVM option
variables removed. The original absolute ROM is
`<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen`, 4194304 bytes,
SHA-1`CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32`63522553`.
XML confirms Java25.0.4.1 and `openggf.headless.gl=surfaceless-egl`.

```bash
# Focused: exactly the two320 iterations, no800 or separate replica rerun.
python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Dexec.mainClass=org.junit.platform.console.ConsoleLauncher -Dexec.classpathScope=test \
  '-Dexec.args=execute --select-iteration=method:com.openggf.tools.TestSszTailsColdRouteCapture#coldMechaEntryRetainsItsNativeWorldBox(int)[0] --select-iteration=method:com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[0] --reports-dir=target/ssz-tails-r2-focused --details=summary --fail-if-no-tests --include-engine=junit-jupiter --disable-banner' \
  test-compile exec:java -B
# One final affected whole class.
python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Dtest=TestSszTailsColdRouteCapture \
  -Dopenggf.surefire.reports=target/ssz-tails-r2-whole-class test -B
```

Fresh replayA/B used two queued `exec:exec -Dexec.executable=python3` invocations
of the temporary observer, each launching Java25 with `%classpath`, the same
original ROM and committed14923-input BK2. Maven times10.919/11.149s, queue0s.
Their state/event diagnostics were consumed; no pass receipts or raw archives
are retained. Useful complete proposal script/BK2 remain in
`<TASK_OUTPUT>/complete320-r2/`; actual paths and task/run/native ownership are
registered outside the repository. One command/observer owned each job, with
no duplicate test request or lock removal.

Sorted tracked `relative-path NUL file-bytes NUL` SHA-256 fingerprints:
production `src/main` remains
`00c3cae4b58fd67022b1c5049303bd4b9b4313cecff08b4ebc3a30ac5a7a7f27`;
`src/test` is
`19de60e842c2a5f86a3124f23900d3a638abcf8f5a3d0946243f44d227b35b60`.
Binary main/test diff from39 hashes to
`638387037b60aaeb2724bad9d3f6c333a4ca2da6d59d830d28477d50750ccd67`;
R2-only diff from5bad hashes to
`886a2c181956cbdc58620a3447c1bba2d8f81be52be20b0aa6bd607b3a9318df`.
Complete script/BK2 SHA-256:
`a8688321bdfa29763f881917ee7ea201c060ef47ba03609ee90585f281c2963a` /
`c3d3d5446064c376086cea3e7438b45e2277364f99db1eeea32ca0c985a20c73`.
All14923 ordered controller rows plus final newline hash to
`08abc4365004102cbe4cc7e000b3260dbb36bf8a889e57c13a6e4fdc14ead866`.

Recovery inverse: restore only the complete320 script/BK2 from parent5bad,
reverse the six documented320 test-data/comment substitutions (including removing
only the51 additions), and remove this R2 matrix/plan append. The qualified
replica commit,800 data, runtime, supplied branch and setup are independent.
This lane makes a local normal-hooks commit only; Root owns review, final
composed qualification, integration and push.

## MTZ orb allocation and first dispatch — 2026-10-21

The bounded implementation starts from `2bad8b9d18dda2f5c2d42b6947878370f0a1d1ff`
in `feature/ai-ssz-mtz-orb-init-20261021`. The locked-on source separates the head's
forward `CreateChild1_Normal` scan from the single first orb's global
`AllocateObject` scan (dynamic slots4–93). Only that first orb's code pointer and
parent reference are written by `loc_7A72C`. Its own `loc_7ADA2` dispatch initializes
itself and at most six siblings in global free-slot order, then returns at
`loc_7AE12`; it does not call the orbit, collision-list or drawing tail. A sibling
in a later SST slot can orbit in that same sweep; a sibling allocated into an
already-passed slot retains its seed until its next dispatch. This preserves the
shipped `FixBugs = 0` path; no conditional repair is introduced.

The ship retains actual successfully allocated orb instances, including a pending
first orb. Exhaustion retains the successful prefix, never retries, and excludes
the destroyed shell returned by the existing helper. No shared allocator or native
policy API changes. Captured routine, angles, draw/publication state and raw
parent/list references survive restoration. Existing controllers and route health
remain outside this component claim.

The test-only RED diff was
`1f0f148f06a7314e436d1e1c788369facd353825e3caa0b0662de8c26cb6d4ef`
(SHA-256 of `git diff --binary` against the pinned base). Queued Main-tool command
`--lean -Dmse=off -Dtest=TestSszMetropolisRecipeBirth test -B` ran18 tests:
11 expected failures, one expected invalid-rewind-reference error from a retained
failed orb shell, zero skips; all six original recipe controls passed. The
above-parent first orb advanced `$24` to `$28`; below-parent allocation instead
created the forward prefix and slot`-1` shells. Those controls reject the old
parent-owned seven-allocation shortcut. They are declared component controls,
not native emulator observations or proof of a successful cold route.

Queued component GREEN ran18 tests with zero failures/errors/skips. The final
normal-lane11-class bundle ran1,459 tests with zero failures/errors/skips, including
19 recipe/allocator controls,25 MTZ arena cases,17 GHZ arena cases,1,334 isolated
object rewind cases, three exact boss-child cases, one identity-rebinding case,
and all60 tests in the five mandatory S3K control classes. The three initializer
world cases compare every registered key before and after each boundary, with
two destructive recreation/forward cycles at each spot. The tightened fixture
explicitly re-registers the ordinarily born ship into a later free SST slot via
public ObjectManager APIs and fills other free slots with persistent occupants,
so a pending first orb below the ship is guaranteed. It covers zero, two and six
available siblings, without private routine, position or HP writes. This declares
an allocator component, not a naturally allocated cold-route slot landscape.

The unchanged production diff SHA-256 is
`0be7d0d33cd2ed3299d7399c7890e199c3cc74af7e788222fc2c1893f32152f3`
(`git diff --binary -- src/main` against the pinned base). Exact commands and
per-class counts are recorded in the existing
[playable completion plan](../../plans/2026-10-13-s3k-playable-completion.md#ssz-mtz-orb-allocation-and-first-dispatch--2026-10-21).
The change-based plan selected3,124 smoke classes; that combined selection is
Root-owned. Focused leaf validation does not claim a broad smoke/full-suite,
canonical trace, new native-emulator comparison or controller-route pass.

After that bundle, the strengthened pending-init fixture alone was rerun with the
same normal-lane ROM/EGL arguments and selector
`TestS3kSszMtzArenaHeadless#orbInitializationBoundariesRecreateAndReplayTheCompleteWorldTwice`:
three tests passed, zero failures/errors/skips. The other1,456 tests were not
repeated; production code remained unchanged.


## Solo Sonic native320 controller qualification — 2026-10-21

This is the new solo continuation after immutable paired commit
`9cc150c12183297293cb45bbf8f24023a95b8dbc`, on
`feature/ai-ssz-solo-sonic-controller-20261021`. Production Java/physics/collision,
EggRobo/gun/shield, native timing, ROM, fixtures, configuration, paired/Tails
movies and authoring tools are unchanged. Runtime source remains
`39eca8c35c25433b7405b6bdc724f7c6111b55ec`; the tested tree is9cc plus the
four solo resources and the existing test's controller-derived data identified
below. Root's later POM/backend reconciliation and pending isolated MTZ orb
work are outside this result. No native mismatch, selected-shot producer,
clock, presentation parity or cause is inferred from a controller pass.

All indices below are zero-based input-row identities, not ROM constants.
Both movies boot through public `GameplayCaptureSession.boot(ROM,10,0,settings)`:
320px, Sonic real main, donor off, no sidekick and P2 neutral. No positions,
health, rings, phases, RNG, readiness, clocks or history are supplied by inputs.
No gameplay is advanced outside normal session input. The full test still checks
Sonic/zero followers/no leader death, monotonic health and all eight required
hits, escaped replica owners absent, zone10/0, receiver `(5888,140)` and released
control. Complete still requires all eight Mecha hits/health0, actual zone11/0,
fresh rings0, `(48,2476)`, main/focus identity, no object control and zero
followers. The ordinary pending-assembly step, outgoing history `>10`,30
incoming neutral steps and `DEZ timeline < outgoing` predicates are unchanged.
The entire short independent Tails flight method and its resource are exact.

### Observed frontier and finite controller choices

The original replica method was run once on this frozen source:1 test,1 failure,
0 errors/skips, `death at6679` (Maven29.826s; wrapper30.60s; reported queue0s).
An ordinary cold movement probe also dies6679. It shows the old input never
reaches the GHZ arena; its authored boss controls execute in the lower opening.
The old complete and replica movies share their first11051 input rows, so the
historical complete death6679 was reused instead of another unchanged long run.
No c13 trajectory was rerun or treated as a native oracle.

Blind paired-input reuse is also insufficient: solo prefix4400 followed by a
bounded1800-input GHZ author ends `LIMIT_REACHED` at6200, still on the carrier
with no boss. Four900-input carrier-release candidates miss the arena. Existing
`ReferencePathInputAuthor` then authors normal solo inputs against the qualified
paired P1 comparison path: first window3400..4400 reaches reference4513 in3880
solo inputs; upper window5826..8440 reaches8685 at6460; third-pad window
11050..13400 reaches13675 at9860; final approach13675..17454 reaches17457 at11220.
The existing finite horizons/commit/stuck limits apply. Reference positions score
controller choices only; they never populate the solo world or create followers.

Existing `SszBossInputAuthorTool` uses finite new-input budgets: GHZ1800
(actual420 inputs), MTZ2400 (actual842), Mecha2400 (actual976). Their selected
inputs are independently cold-replayed below. First-pad trials show why
standing alone is insufficient: a22-input left brake stops atX499, outside
`loc_45660`'s `x-padX+$C < $18` centre gate;18Left stops at516, then the fresh4800
jump produces contact4855 and first receiving-pad contact5137. This changes
controller braking, not the gate. Cloud/carrier traversal uses real contacts
(`sub_45170` / `loc_451DC`, `loc_45F74`); native Y wrap and all collision owners
remain authoritative.

Actual ordinary encounter observations on the selected input are:

| Event | Input identity |
| --- | --- |
| GHZ allocation / accepted hits1..8 / last escaped-owner row | 3932 /4048,4096,4128,4164,4197,4233,4265,4299 /4482 |
| First active pad / receiving pad | 4855 /5137; receiver `(512,1420)` |
| MTZ allocation / accepted hits1..8 / last escaped-owner row | 6516 /6813,6889,6938,7055,7119,7181,7241,7301 /7484 |
| Second active pad / receiving pad | 7857 /8149; released end `(5888,140)`,3rings |
| Seam bouncer / wrapped rotating carrier / third activating pad / receiver | 8930 /8979 /9678 /9951 |
| Final activating pad / Mecha allocation | 11024 /11250 |
| Mecha accepted hits1..8 | 11493,11533,11643,11691,11820,11943,11988,12195 |
| Mecha defeat fall / floor landing / last owner row | 12323 /12354 /13213 |
| Results presentation / tally | inspected12600/12700; source handover `loc_7D056` and ordinary results owner |
| Spiral entry / actual DEZ load / ordinary destination assembly | 13195 /14543 /14544 |

The selected route survives ordinary hurts2036/2297/6701/11386; no producer or
new engine cause is claimed. A single3200-neutral exploratory exit candidate
stops on the real load at14543. The maintained movie ends on that input; it
contains14544 rows, with the test's unchanged next neutral input assembling DEZ.

### Independent cold replays before changing derived data

Two separate ordinary cold `GameplayCaptureTool` JVMs establish each selection
before the existing test's lengths/rings/event indices are changed. Both8300-row
replica captures are byte-equal across every recorded CSV row, alive with zero
followers, ending at `(5888,140)` with3rings. Both complete captures replay14544
movie rows plus the ordinary assembly neutral input; all14545 recorded CSV rows
are byte-equal, alive/zero followers, with actual load14543 and assembly14544 at
`(48,2476)`,0rings. Their first8300 state rows also equal the replica captures.
This is capture-field repeatability, not an official/native journal or
whole-world parity claim. The strict maintained whole-registry checks follow.

| Fresh JVM | Reported queue wait | Maven execution | Wrapper elapsed |
| --- | ---: | ---: | ---: |
| replicas-cold1 | 0s | 17.536s | 18.19s |
| replicas-cold2 | 0s | 17.527s | 18.26s |
| complete-cold1 | 143s | 18.719s | 162.84s |
| complete-cold2 | 0s | 18.282s | 19.02s |

The first incoming assembly image is black and is not used as a positive scene
witness. A separate bounded capture adds30 ordinary neutral inputs after
assembly: input14574 shows the drawn DEZ scene, Sonic `(71,2476)`,0rings and no
follower/death. Reported queue53s, Maven18.168s, wrapper71.64s. Results and
incoming-scene images were inspected, then consumed. No native pixel claim.

The old derived movie lengths11200/17611 and26 end rings are replaced by observed
8300/14544 and3 rings. Both new movies retain the old leading2190 rows. Among
common indices,4032 replica P1 rows and8564 complete P1 rows change; old trailing
2900/3067 rows are removed by the new lengths. No P2 row is non-neutral. The
complete movie shares every one of the8300 new replica inputs and their ordered
script instructions; there is no split prefix or inferred state recovery.

Production `InputLogAuthorTool` generated both maintained BK2s from the maintained
scripts and verified the `Bk2MovieLoader` round trip on every row/both pads. An
independent exact row comparison matches the separately replayed candidates.
ZIP members remain only `Header.txt` and `Input Log.txt`; no state container.

### Rewind identity mapping

The old45 replica and49 complete identities are preserved one-for-one in
chronological execution order, with new indices bound to actual selected-route
contacts/encounters. Old numbers identify prior test data; the old movie does
not reach its later events on this source. These are explicit new observations,
not offsets applied to unreached old events. Every window keeps the original
45-input forward/restore/replay loops, complete snapshot key-set/content
comparisons, external held-input restore and normal branch drawing. No test
algorithm, assertion predicate or snapshot-key list changes. The final relative
`movieLength-170` identity and `movieLength-120` live-history enable remain intact.

Replica mapping (45 unique windows):

| Old input identity | New input identity | Observed selected-route window |
| --- | --- | --- |
| 2888 | 2888 | stair approach |
| 3440 | 3348 | rotating carrier hold |
| 3540 | 3408 | carrier release |
| 3818 | 3500 | retracting spring ledge |
| 4168 | 3750 | upper stairs |
| 4400 | 3932 | GHZ allocation |
| 4700 | 4048 | GHZ hit1 |
| 4930 | 4096 | GHZ hit2 |
| 5020 | 4164 | GHZ hit4 |
| 5070 | 4299 | GHZ hit8 |
| 5328 | 4482 | GHZ last escape row |
| 5470 | 4855 | first active pad landing |
| 5630 | 5100 | first transport rise |
| 6200 | 5137 | first receiving pad landing |
| 6400 | 5300 | first receiver exit |
| 6600 | 5420 | upper traversal jump |
| 6800 | 5540 | upper traversal jump |
| 7000 | 5660 | diagonal platform traversal |
| 7199 | 5780 | upper rise |
| 7339 | 5900 | upper rise |
| 7450 | 6020 | upper stairs |
| 7740 | 6140 | solid cloud approach |
| 7800 | 6180 | cloud jump |
| 7850 | 6220 | upper cloud ascent |
| 7920 | 6260 | cloud release |
| 7995 | 6340 | upper walkway |
| 8150 | 6420 | MTZ approach |
| 8430 | 6516 | MTZ allocation |
| 8458 | 6701 | ordinary hurt/recovery |
| 8570 | 6813 | MTZ hit1 |
| 8626 | 6889 | MTZ hit2 |
| 8690 | 6938 | MTZ hit3 |
| 8890 | 7055 | MTZ hit4 |
| 9040 | 7119 | MTZ hit5 |
| 9300 | 7181 | MTZ hit6 |
| 9344 | 7241 | MTZ hit7 |
| 9600 | 7301 | MTZ hit8 |
| 9840 | 7484 | MTZ last escape row |
| 9950 | 7802 | second pad fresh jump |
| 10000 | 7857 | second active pad landing |
| 10200 | 7980 | second pad charge |
| 10300 | 8061 | second transport ascent |
| 10410 | 8124 | second receiver off-pad |
| 10500 | 8149 | second receiving pad landing |
| 11050 | 8250 | second receiver released/stable |

Complete mapping (49 unique windows):

| Old input identity | New input identity | Observed selected-route window |
| --- | --- | --- |
| 11200 | 8300 | second receiver exit |
| 11334 | 8479 | rotating platform contact |
| 11850 | 8746 | solid cloud first landing |
| 11911 | 8804 | solid cloud second landing |
| 11971 | 8865 | collapsing column contact |
| 12031 | 8893 | seam solid cloud landing |
| 12061 | 8930 | seam bouncy cloud contact |
| 12131 | 8979 | wrapped rotating carrier catch |
| 12191 | 9077 | lower collapsing column |
| 12221 | 9276 | diagonal bridge contact |
| 12259 | 9451 | third-pad bouncy cloud |
| 12309 | 9517 | third-pad first landing |
| 12339 | 9618 | third-pad second landing |
| 12459 | 9678 | third-pad activating landing |
| 12579 | 9800 | third-pad charge |
| 12750 | 9924 | third transport release |
| 12790 | 9951 | third receiving pad landing |
| 13000 | 10118 | lower spring contact |
| 13200 | 10228 | upper spring contact |
| 13239 | 10431 | upper diagonal bridge contact |
| 13400 | 11024 | final pad activating landing |
| 13596 | 11180 | final transport rise |
| 13680 | 11250 | Mecha allocation |
| 13800 | 11362 | Mecha flying routine and hurt/recovery |
| 13980 | 11493 | Mecha hit1 |
| 14050 | 11533 | Mecha hit2 |
| 14226 | 11643 | Mecha hit3 |
| 14350 | 11691 | Mecha hit4 |
| 14510 | 11820 | Mecha hit5 |
| 14550 | 11943 | Mecha hit6 |
| 14660 | 11988 | Mecha hit7 |
| 14750 | 12195 | Mecha hit8 |
| 14850 | 12323 | Mecha defeat fall |
| 15000 | 12354 | Mecha defeat landing |
| 15126 | 12400 | post-defeat handover |
| 15246 | 12500 | results presentation |
| 15298 | 12600 | results presentation |
| 15400 | 12700 | results presentation |
| 15600 | 12800 | results presentation |
| 15850 | 12900 | collapse approach |
| 16000 | 13000 | forced launch jump |
| 16260 | 13100 | collapse fall |
| 16400 | 13195 | spiral entry |
| 16600 | 13300 | spiral ascent |
| 16743 | 13500 | spiral ascent |
| 16900 | 13700 | spiral ascent |
| 17150 | 13900 | spiral ascent |
| 17300 | 14250 | spiral top/launch |
| movieLength-170 (=17441) | movieLength-170 (=14374) | late outgoing SSZ window before live-history enable/load |

### Maintained qualification and limits

`TestSszSoloColdRouteCapture` passes3 tests,0 failures,0 errors,0 skips on the
exact input/test-data hashes below, frozen runtime39 / parent9cc:

| Maintained method | Result | Covered obligation |
| --- | --- | --- |
| `coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | PASS6.696s | both8-hit defeats/escaped owners,45 full-registry windows, zone10/0 and released second receiver |
| `coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | PASS5.720s | Mecha8hits/health0,49 full-registry windows, actual DEZ entry/main/focus/control/rings/roster and isolated history |
| `coldTailsFliesPastTheOpeningSpikesWithNegativeCameraMinimum` | PASS0.822s | unchanged independent short Tails resource/method and5 replay spots |

XML suite13.531s; Maven39.115s; wrapper39.74s; reported queue wait0s. XML properties
confirm Java25.0.4.1, JDK25, the canonical absolute S3&K ROM and surfaceless EGL.
ROM SHA1 is `CFBF98C36C776677290A872547AC47C53D2761D6`; no ROM copy/link was made.
The whole-class command, from the isolated branch, uses `OPENGGF_MAIN` for the
existing absolute Main checkout and its current queue wrapper:

```bash
env -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS -u MAVEN_OPTS -u DISPLAY \
  -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off \
  "-Ds3k.rom.path=$OPENGGF_MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  -Dtest=TestSszSoloColdRouteCapture test -B
```

The original negative selector was the same prefix/environment with
`-Dtest=TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches`.
Each cold capture used that queued `exec:java` lane with
`com.openggf.tools.GameplayCaptureTool`, `--game s3k --zone ssz --act 1 --width 320
--main sonic --sidekick none --donor off --input <maintained-or-exact-candidate.bk2>
--frames <8300-or-14545> --every 100000 --no-video --out-dir <owned-empty-directory>`
and explicit `--rom` pointing to the same verified absolute ROM. No seed flags.

No paired check, broad/release suite or unchanged runtime-control suite was
repeated in this lane. Root owns final current-POM/backend/composed-source
qualification and integration. Other viewport/donor/roster, death/respawn and
native acceptance breadth remains open; this source-qualified controller result
does not certify the act or validate the pending MTZ runtime proposal.
Only useful controller inputs are retained for handoff; owned raw CSV/PNG/log/XML
products are consumed after inspection. The worktree/old branches and unrelated
TraceChaser setup remain preserved for Root accounting.

| File under `src/test/` | SHA-256 |
| --- | --- |
| `resources/routes/s3k/ssz1-sonic-solo-cold-replicas-320.script` | `a2d7e77811c9a362b9dd46fc58d91d026b27ed4b5606cbcd400695e79645f148` |
| `resources/routes/s3k/ssz1-sonic-solo-cold-replicas-320.bk2` | `3d18908bfa9c6c10580eb2a023de5dd376bcfb93ebf81bc483389848fc246789` |
| `resources/routes/s3k/ssz1-sonic-solo-cold-complete-320.script` | `1ac87458262519888381fc7477faa5640eb27f0f0e74b1e8c4b737148178f62c` |
| `resources/routes/s3k/ssz1-sonic-solo-cold-complete-320.bk2` | `52719ecb9279f26016dbe69def98eeb7e9029ec8d5443a711a5e4ee46799a049` |
| `java/com/openggf/tools/TestSszSoloColdRouteCapture.java` | `07081cbad325a3fa4b9cb12cb02ff2ff3b03751b331a8cbd33cbf92b1fd316ec` |

Four-resource binary diff SHA-256 against9cc:
`ff06f25cd706c6d9cb2b2daca578fb5c8a597e9fc0ed91001fc6d5872b15f313`.

Four resources plus controller-test-data binary diff SHA-256 against9cc:
`f793643b75c4f52349c96b768a0aa7607be41cfb815f9da4965e25d0f461160b`.

## Composed Solo Sonic and visible Tails destination — 2026-10-21

Root adopted the reviewed Solo Sonic controller/data leaf `ba71a62c` in normal
merge `8c78e7762b4394b5c5d9a531ccb2b21807491028`. On that composed tree,
which includes the MTZ first-dispatch correction and current backend/POM,
`TestSszSoloColdRouteCapture` passed **3T/0F/0E/0S**. All three original XML
identities,69 properties, full streams and TXT summaries were inspected; no
skips. The replica and complete methods retain45+49 full-registry45-input
windows and the unchanged independent Tails opening passes. XML suite13.757s;
Maven38.922s; queue waiting and individual compilation intervals are unknown.
The two earlier Solo Sonic failures at6679 used superseded controller inputs.

This current result is distinct from the author's frozen39 qualification above.
No runtime, input or assertion was changed during this run. FAST/no-manifest and
unmeasured Kos-profile deterministic-estimate warnings remain. Exact outputs were
consumed after inspection; this is focused class coverage, not a broad suite or
native presentation acceptance.

A separate ordinary full-render Tails320 continuation on Root `c18506cf`
used all14923 committed complete-route inputs followed by401 neutral ticks.
The public gameplay boot and normal drawing ran throughout; no gameplay seeds,
restore or trace hydration. All15324 observed rows retain the same main/focus,
no sidekick and no dead state. The actual DEZ load at14922 and fresh assembly
at14923 remain distinct: assembly at48/2480 has zero rings and
`objectControlled=false`, but that first assembly image is black. At15042 the
original engine image shows Tails at322/2480, the DEZ floor and HUD. At15323 it
shows idle Tails at370/2480, the enemy and2 HUD rings after ordinary continuation.
Root inspected both original images; the black fade-boundary sample is not used
as visible destination evidence.

Only two useful images remain in the owned external capture task
`s3k-tails320-dez-visible-20261021/frames/`:

| Image | SHA-256 |
| --- | --- |
| `15042.png` | `d00596330a7bfe81aabb6f07e6a563b2f6b27f1610b0072644504bcb28665c9f` |
| `15323.png` | `0d9198cd780ffcf89eead64031b15e47163da0110123c9655e6c1d75858d3b3f` |

Maven11.198s; queue waiting unknown. The temporary probe, regenerable state CSV
and six boundary/duplicate images were consumed. This closes this engine-visible
destination sample only: no native pixel, physical SAT/DMA, true reset, full DEZ
route, lives-counter or input-lock-release certificate follows. Detailed commands
and remaining composed limits are in the [dated plan](../../plans/2026-10-13-s3k-playable-completion.md#root-composed-soniccnz-and-visible-dez-checkpoint--2026-10-21).

## 2026-10-21 — solo Tails800 complete controller on currentcdada (R3)

R3 starts a new owned `feature/ai-ssz-tails800-route-20261021` branch at exact
`cdada405a384ab19c88fc82375b79cbc38c9f30c`. This explicitly adopts Root's merged
MTZ74 runtime owners, current POM and native703 gitlink before authoring. It
supersedes the preceding800 complete frontier; R1/R2 commits and their measured
history remain immutable. The old96 feature ref and suppliedefd ref remain
preserved. The unrelated actual TraceChaser checkout322 is unchanged and unused
by this Java controller lane; the source gitlink703 remains the adopted base.
Only complete800 script/BK2,800 controller data/comments in the existing Tails
class, this matrix and the dated plan change. All320 resources/conditional data,
all replica resources/methods/oracles, runtime, native code and POM remain exact.

The maintained complete800 movie now has **15072 inputs**, including the exact
**8425 ordered P1/P2 replica prefix**, with P2 neutral throughout. The old15132
complete movie shared only8225 replica rows; its first changed row is8225 and
the new total is60 inputs shorter. The new6647-input continuation is ordinary
controller input. `InputLogAuthorTool` generated the BK2 and passed its production
loader roundtrip; a separate comparison checked every ordered input against the
selected proposal and all8425 prefix rows. The author tool owns ZIP generation. No external gameplay state, private
clock/readiness/HP/RNG/ring/camera/slot values, trace rows or savestate shortcuts
were supplied. Exploratory checkpoints use the engine registry; neither fresh
replay restores a checkpoint. The ordinary cold SSZ10:0 solo-Tails inlet is retained.

Actual800 traversal reaches the hanging carrier8603 and releases8606, then
latches/releases the elevator bar9008/9027. Landing on the lower cloud9061 starts
its native seven-input counter. Jump9064 retains the launch9068 in Tails flight;
player/camera wrap occur9145/9196. `Obj_SSZBouncyCloud`, `sub_45170`/`loc_451DC`,
own the counter, saved horizontal speeds and `-$700` launch. The route flies
onto the actual lower5376/3824 pad: activation9722, lift9872, receiving arrival9903,
release9968, grounded receiving exit10020. The ordinary6336/3272 spring launches
at10680; the retracting-spring zigzag launches10889/10949/11039. Tails lands the
final pad top11282 and centers Right to activate11369, lift11516, arrive11567
and initialize Mecha11605. Final transport control releases11632.

Bounded negatives explain the authoring choices. A copied320 continuation took
EggRobo damage near4484; the chosen shorter flight release crossed that section
without damage. A held carrier jump recaught the carrier, while pulse flight
escaped. Jumping before the bar actually latched left the input held when it did
latch; the later release landed the cloud. The cloud countdown jump produced the
real wrap. The source-supported spring/pad navigation proposal reached the final
pad's left edge; its old centering direction missed activation, so actual800
centering uses Right. These observations support controller changes. No new
runtime defect or physics/timing adjustment was inferred.

The existing bounded Mecha author uses ordinary inputs from the reached11684
arena state and stops at12745 after the actual eighth hit12744, within its1800
input bound. Neutral input then reaches real results and the DEZ load. The chosen
movie was independently replayed in **two separate fresh cold JVMs**, without
exploratory branch restoration, before changing any expected test data. Each
input serviced normal production drawing. Both replays agree byte-for-byte on
all15072 state rows and every observed owner transition. They observe GHZ/MTZ/
Mecha each8..0 without healing, no player death/life loss and three lives.

Actual Mecha accepted drops are11891,12009,12167,12311,12368,12652,12698,12744.
Initialization observes boss6912/1632 (`$1B00/$660`),800 camera6320/1472 and
world bounds6592..6848 (`$19C0..$1AC0`). Defeat landing/act-end allocation12883,
results13003 and launch stages13488/13518/13618/13713 precede counter`$910`14837,
jump14838, hover14868 and **actual DEZ load15071**. The next ordinary dispatch
assembles zone11 act0, Tails48/2480, rings0, radius15/feet2495, released control,
same main playable/camera focus, no pending boundary/title card and a retired SSZ
horizontal lock. Outgoing live history is97; the fresh timeline is1 after
assembly and31 after30 ordinary inputs. The ordinary Mecha-init images agree
and were inspected; they are gameplay observations without a native-pixel claim.
The load-producing PNG contains destination tiles before deferred player assembly;
it supplies no playable-destination visual qualification. The next ordinary
dispatch owns the semantic assembly checks. This remains a cold-zone fixture
rather than a ROM-power-on/native-pixel result.

Only the following800 authored data changes after those two observations:

|800 data | Old → new | Reached basis |
|---|---|---|
|Mecha-entry first45-input window |10667 →11369 | Final positive pad activation |
|Mecha-entry second45-input window |10819 →11516 | Actual lift before Mecha initialization |
|Mecha-entry input limit |11306 →11606 | Includes first initialized dispatch11605 |
|Complete count |15132 →15072 | Includes load-producing input15071 |
|Derived history start |15012 →14952 | Existing count-minus120 formula unchanged |
|Complete replay windows |49 →108 | All49 old identities retained,59 reached boundaries/predecessors added |

The old-to-new mapping for every original800 complete index is identity:
8225,8298,8347,8451,8535,8660,8793,8848,8897,8932,9022,9134,9217,9308,9357,9426,
9635,9705,9775,9844,9928,10053,10179,10262,10471,10541,10631,10680,11053,11306,
11359,11473,11562,11651,11841,11968,12120,12247,12374,12501,12602,12678,12818,
13008,13388,13828,14228,14628,14878 each maps to itself as a supplementary
sample. The existing10680 now directly covers the actual vertical-spring launch.
Actual gate-role coverage uses the added measured indices below. Original late
14878+45 ends14923, before live history begins14952, so no window is shortened.

|Added actual owner coverage | New checkpoint indices |
|---|---|
|Qualified prefix endpoint/carrier and bar latch/releases |8425,8603,8606,9008,9027 |
|Cloud landing/jump/launch and wrap predecessors/boundaries |9061,9064,9068,9144,9145,9195,9196,9230 |
|Lower pad activation/lift/arrival/release/receiving exit |9722,9872,9903,9968,10020 |
|Spike-wall jump and three actual zigzag launches |10201,10889,10949,11039 |
|Final pad landing/activation/lift/arrival; Mecha allocation/init/release |11282,11369,11516,11567,11604,11605,11632 |
|Mecha entry/attack phase boundaries |11653,11717,11797 |
|Every accepted Mecha drop, predecessor and result |11890/11891,12008/12009,12166/12167,12310/12311,12367/12368,12651/12652,12697/12698,12743/12744 |
|Defeat landing/results |12872,12883,13003 |
|Launch allocation/stages/player handover/counter/jump/hover |13488,13518,13618,13633,13713,14837,14838,14868 |

All108 windows keep the original45-input full-registry restore/forward replay,
input-history restoration and drawing. The added predecessors replay actual wrap,
Mecha allocation and accepted-damage dispatches through their transitions. All
native geometry/camera/boss HP/no-healing/death/solo/actual DEZ/fresh rings/main
and camera identity/radius/feet/control/retired lock/history predicates remain.
No ring expectation changes, maintained generic test/helper logic, assumptions,
gates or skips were added. A literal inverse of the six data/comment substitutions exactly
recreates the cdada test file, including every320 branch and the replica methods.

The exact maintained800 selection passes **2T0F0E0S**, actual XML identities
`coldMechaEntryRetainsItsNativeWorldBox(int)[2]` and
`coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]`, XML17.670s,
printed queue24s. One final affected class passes **11T0F0E0S**, XML42.153s,
reported Maven1:09, queue0s. All eleven actual XML identities/assertions/skips/
environment properties were inspected: both final-route widths, both replica
widths and all five GHZ approaches pass. The two Root-baseline800 assertions
`approach death at9560` and `death at9560` now pass by their exact identities.
Root's prior1472-case qualification is reused as supplied; no broad/full-suite,
native-core or mandatory-runtime-control rerun was launched in this controller
lane. Root owns independent source/data review and final composed qualification.

Commands run from `<OWNED_WORKTREE>` through the current Main absolute queue
wrapper in the normal7GiB/8-core lane. The launch environment unsets DISPLAY,
WAYLAND_DISPLAY, JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS, _JAVA_OPTIONS and MAVEN_OPTS;
it supplies JAVA_HOME`/usr/lib/jvm/java-25-openjdk`, LUA_BIN`/usr/bin/lua5.4`,
ALSOFT_DRIVERS`null` and explicit surfaceless EGL. XML confirms Java25.0.4.1 and
the original absolute `<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen`,
4194304 bytes, SHA1`CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32`63522553`.

```bash
python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Dexec.mainClass=org.junit.platform.console.ConsoleLauncher -Dexec.classpathScope=test \
  '-Dexec.args=execute --select-iteration=method:com.openggf.tools.TestSszTailsColdRouteCapture#coldMechaEntryRetainsItsNativeWorldBox(int)[1] --select-iteration=method:com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1] --reports-dir=target/ssz-tails-r3-focused --details=summary --fail-if-no-tests --include-engine=junit-jupiter --disable-banner' \
  test-compile exec:java -B
python3 <MAIN_WORKSPACE>/tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=<MAIN_WORKSPACE>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  -Dtest=TestSszTailsColdRouteCapture \
  -Dopenggf.surefire.reports=target/ssz-tails-r3-whole-class test -B
```

FreshA/B each use queued `exec:exec -Dexec.executable=python3` to launch a fresh
Java25 process with `%classpath`, the original ROM and proposed committed BK2;
no branch/helper-author state enters either replay. Maven14.904/14.917s, queue0/
56s. Consumed owned diagnostics and temporary observer are removed without class
or native-extraction cleanup, pass receipts or raw archives. Useful authored
proposal script/BK2 and inspected ordinary Mecha-init images remain outside the
repo in `<TASK_OUTPUT>/complete800-r3/`. Exact native/task/run/cwd/branch ownership
and expiry13:04:49.177308Z are registered only under the R3 continuation using the
common lock, fresh strict whole JSON and0600 same-directory atomic replacement.

Sorted tracked `relative-path NUL file-bytes NUL` SHA256:
`src/main` remains `7d0abf2dca9031cfab99acfea62c676878d07ea1887a596b1bbe3f05e5685aaf`;
`src/test` is `1af0a02ef40901cccf3dfc8b809ad5ecd63530dd81a8eca11944a569911b73af`.
Unchanged POM SHA256 is
`ee155587e971cb5d41ceadc1f7e670dce114d2b9722458ed8b4f45d520163710`.
Binary main/test diff from cdada hashes to
`cebfea916f4f3230b4cab594ff5492924666791145f386974ef5709a25e927be`.
Complete800 script/BK2 SHA256:
`3a3eebfb3e923a36bfdfa5a5e3b4f7e021bc1c186ba5eeece276bd9de61b135e` /
`0df391c49e5831d108d2322d7e1e5fccc433afbbd094f8041b5cb03d76258b52`.
All15072 ordered input rows plus final newline hash to
`d750d62128ce93d9aec15adc33494ec090675f3f402aa05323254a065d2d2389`.

Recovery inverse restores only complete800 script/BK2 from parentcdada, reverses
the six documented800 data/comment substitutions (removing only the59 additions)
and removes this R3 matrix/plan append. All320, replica, runtime, POM, native/setup
and earlier branch history are independent. Delivery is one local normal-hooks
commit with seven documentation trailers and the final GPT-6.1-Sol coauthor;
there is no push, Main/Root integration or worktree cleanup in this lane.


## Composed Tails800 completion — 2026-10-21

Root adopted the reviewed controller/data commit
`c3f39ffcab145ce3eb227a951bb7f798cf6ad1c5` in normal merge
`8b24afe65b324e09e17a7f225537269163ac7892`. The exact script/BK2 rows,
8,425-row replica prefix, neutral P2 and six-substitution whole-test inverse
were independently checked. All49 original800 windows remain, with59 actual
owner/predecessor additions; the strict45-input full-registry, drawing and
gameplay predicates are unchanged. All320 and replica inputs/data remain exact.

On composed `f9bb32cce833d4ea9952e88f3f19247cbf85abd1`, one queued Console
request selected only800 Mecha entry and completion. Both actual XML identities
are `(int)[2]`, with display name `[2] 800`: **2T/0F/0E/0S**.
The Console selectors use zero-based iteration `[1]`; the inspected XML, rather
than that argument alone, establishes the executed width. Suite17.365s and
Console17.384s are distinct observations; Maven duration and queue waiting are
unknown. Main was up to date;4,263 test sources compiled at release25 inside
that sole lifecycle. Java25.0.4.1, required absolute ROMs and surfaceless EGL
were inspected. The source/configuration fingerprint remained unchanged.

These inputs supersede the two prior Root800 approach-death9560 failures.
The author's11-case pass and the previous Root checks retain their own tested
states and coverage; this two-case request is not another whole-class or broad
suite run. The current contract still checks genuine boss defeats, actual DEZ
load/assembly and isolated history. The load-producing800 image remains
destination tiles before deferred player assembly, with no playable-DEZ or
native-pixel claim. The separate visible Tails320 evidence above is unchanged.
Exact consumed reports and the command are accounted for in the
[composition checkpoint](../../plans/2026-10-13-s3k-playable-completion.md#root-tails800-origin-copy-and-knuckles-dez2-composition--2026-10-21).


## Wide paired current frontier — 2026-10-21

Exact Root base `0e4f7a9f202f477018d4adeb01295316704c0ea1`, owned branch
`feature/ai-ssz-wide-paired-route-20261021`: the whole
`TestSszWidePairedColdRouteCapture` diagnostic is **2T/2F/0E/0S**. The first
method reaches its40 two-cycle45 full-registry windows, eight GHZ hits and
512/1420 receiving platform, then fails46 expected rings versus48. The complete
method fails its **first forward** branch at checkpoint11640, n24, input11664,
before restore. Its downstream MTZ/Mecha/DEZ obligations remain unverified here.
This is recurrence on the exact Root runtime, not new attribution against Main.

The only test edit adds existing pending/frame/n operands to the forward and
replay death messages. Original scripts/BK2s, counts, ring expectations, all
semantic predicates and complete whole-registry comparisons are retained.
An independent ordinarily drawn uninterrupted replay also dies11664, with21
rings at4853/2908; first hurt is10969 at2528/1452. Actual touch receiver and native
causality are unproven. Seven-argument800/Sonic/Tails/off Settings retains its
existing omitted-title inlet; no position/clock/ring/health seeds were supplied.

Eight bounded controller alternatives found ways past the first fall but no
qualified complete route. The best later branches reach only x4583 and do not
encounter MTZ. A fresh no-jump candidate using the remaining original tail dies
16349. No controller or ring-data repair is accepted, no production omission is
established, and no unchanged final class or broad suite is repeated. Required
ordinary qualification and the passing final leaf class remain open. Original
normal-render frontier stills and the rejected controller proposal are external;
regenerable diagnostics are consumed. The [dated plan](../../plans/2026-10-13-s3k-playable-completion.md#widepaired800-current-controller-frontier-2026-10-21)
records the exact command, source/environment pins, alternative inputs and limits.
This does not certify full-game, native timing, pixels, true boot or additional
width/donor/team routes.


## Wide paired matched Main control R2 — 2026-10-21

One exact Main `efd605142603146509995cffbb56a6b48f9c1c1b` first-case control is
**1T/0F/0E/0S**: `TestSszWidePairedColdRouteCapture#coldFirstReplicaAndTransportReplayWithRetiredColumnDebris`.
It reaches and passes the46-ring endpoint assertion. The identical first-method
source and input/driver/drawing paths produce48 in R1's current Root-runtime run.
This establishes a campaign-introduced endpoint difference against Main; it is
not an inherited Main negative and46 must not be changed to48 to silence it.
No first-divergent event or native cause is proven. Main's complete case remains
unrun/unknown; the current paired800 MTZ/Mecha/DEZ obligations stay unreached.

After that control, the same isolated checkout returns to new owned
`feature/ai-ssz-wide-paired-r2-20261021` at exact Root
`393b831f3cefcc0ae7c446d65dd0ab35d7eb9472`, production7fbc. R1/supplied refs, actual
reference/native HEADs/status/tracked bytes and config are preserved. No build or
JVM state is reused across source arms. The passing-Main condition stops all R2
cloud/controller work: zero ordinary projections, alternatives or fresh replays.
No Java, controller, ring-oracle or runtime input changes follow this control.

Static next question: which first real ledger event differs on the unchanged9914
pads—P1/P2 acceptance/own-pass award or native-P2 hurt/lost-ring handling—and does
its preceding trajectory already differ? The changed native-P2 hurt tail and
deferred attracted-ring award owner are candidates, not causal findings. The
[dated plan](../../plans/2026-10-13-s3k-playable-completion.md#widepaired800-matched-main-control-r2-2026-10-21)
records source owners, exact command/pins and coverage limits. One passing Main
case does not certify the current route, a whole class, native timing/pixels or
true boot. Owned regenerable diagnostics are consumed after inspection.


## Wide paired first matched ordinary state delta R3 — 2026-10-21

Two ordinary cold row0 NORMAL-DRAW first-replica projections, exact Main
`efd605142603146509995cffbb56a6b48f9c1c1b` and current
`a4d78a16df5b312e75a23b28df797cef88e496c6`, reproduce **46/48 rings** at512/1420.
Original9914 pads/P2neutral and Settings7/titlefalse are unchanged. Each observation
XML records9914 inputs/29742 phase rows/115 public fields/0 skipped inputs;
these are not JUnit/suite or native parity results. No restore, import or seed is used.

The first observed difference is3491 POST_STEP: P1's persisted interaction slot
Main28/current27; P2 follows3507. The actual owner correspondence is unobserved.
At6721 PRE_STEP all115 fields agree; POST_STEP first diverges in P2 motion/hurt:
Main normal/iframes0, current hurt/iframes120 and knockback, with37 rings in both.
P1 first differs in motion7164 (vertical speed-856/+856); camera differs7166.
Only at7172 does the shared ring count differ, Main45/current46, when current
alone adds placed-ring bit5/sparkle5:7172. No attracted entry is visible in either
series. Prior trajectory/hurt differences preclude treating this as an isolated
award-order or ring-data question; actual collector/receiver/order are not observed.

The next seam is actual original contact ownership behind3491's slot write,
without rerunning the provider; equivalent-owner allocation remains possible.
The later6721 executed damage receiver/decision remains a separate unexposed seam.
No cause/native correctness or runtime repair is claimed. Expected46 and every
maintained controller/assertion stay intact. The [dated R3 plan](../../plans/2026-10-13-s3k-playable-completion.md#widepaired800-first-matched-ordinary-state-delta-r3-2026-10-21)
records exact boundaries/pins/limits. Complete fixed rows and the useful adapter
are external; raw diagnostics are consumed. Exactly two requests, no third or
maintained-class/broad rerun. Current paired800 MTZ/Mecha/DEZ remain unqualified.


## Wide paired original contact and hurt owners R4 — 2026-10-21

Two further matched ordinary row0 NORMAL-DRAW projections use exact Main `efd605142`
and current `a4d78a16d`, unchanged9914 pads/P2neutral/Settings7 titlefalse, stopping
at input7164 (7165 inputs). Each XML records21495 phase rows/115 public fields,
0 skipped inputs and0 JUnit tests. All115 fields at PRE/POST of3491,3507,6721,7164
match the retained same-arm R3 rows. These shortened runs add no endpoint result.

Original raw-reference carriers bind both3491 P1 and3507 P2 standing contacts to
one local floating-platform instance per JVM, from **ROM layout115**, full spawn
`(3848,3088,$7F,$00,flags0,rawY3088,respawn=false)`. Its construction/admission at3138
already assigns Main28/current27; the original latch writes those slots. This is
placement correspondence, not cross-JVM instance equality, full dynamic-state
identity, native slot lineage or an explanation of the allocation difference.

At6721 both actual P2 loops use the sidekick path and visit monitor, EggRobo, shot
in that order. Original P2 box operands are left2896/top2516/width16/height22 in both.
The selected shot operands differ: Main `(2763,2512)`/slot29 misses; current
`(2873,2512)`/slot31 overlaps, flags `$9C`, radii24/4, category HURT and
`MAIN_FULL_SIDEKICK_HURT_ONLY`. Current's existing sidekick handler calls
`P2.applyHurt(2873)`, returning **true** normally. Main executes no hurt call.
Neither pass evaluates the new normal-handler native-P2 hurt-tail query, a rings
choice or an ENEMY attack predicate here. Raw damaged actor equals the registered
P2 locally; nativeP2-query equality remains UNKNOWN because that query is unexecuted.

Earlier world geometry is therefore exposed: corresponding placed EggRobo layout63
is born6551 at slot10/27 and resolves touch position `(2514,2520)`/`(2624,2518)` at6721.
Each local shot is born6660 with original spawnX2531/2641, layout-1. Its actual
Gun/body parent binding and cross-arm shot correspondence are not observed.
At7164 Main's original layout23 EggRobo ENEMY hit has attack=true, HP0,
destroyed=false→true; the original bounce reads Yvel856, playerY2277/enemyY2304
and resolves-856. Current retains no P1 touch-member/attack/bounce event there;
empty-list versus earlier entry gating and that badnik's prior lifetime remain unknown.

The next seam is the original layout63 fighter motion/initialization and Gun→Shot
allocation operands before6660, plus the separate slot-allocation frontier by3138.
No causal link between those frontiers, native correctness or runtime repair is
established. Temporary instrumentation is restored whole byte exact and its classes
removed; expected46, movies, assertions and runtime stay unchanged. The
[dated R4 plan](../../plans/2026-10-13-s3k-playable-completion.md#widepaired800-original-contact-and-hurt-owner-r4-2026-10-21)
records original branch/native sources, protocol and remaining limits. Current
paired800 MTZ/Mecha/DEZ stay unqualified; Main complete remains unknown.


## Wide paired fighter and pickup attribution R5 — 2026-10-21

Exactly two full ordinary row0 NORMAL-DRAW projections, Main `efd605142` and
current `605bf14de` (SSZ execution footprint a4d), retain unchanged9914 pads,
P2neutral and Settings7/titlefalse. Each XML records9914 inputs/29742 phase rows/
115 fields/0 skipped inputs/0 JUnit tests. All115 fields agree with all13 retained
same-arm R3 rows, including the eight R4 public guards and endpoints **46/48 rings**.
Available R4 touch/bounce primitive tuples also agree; dynamic Java identities
are never equated across JVMs. Prior raw series are not reconstructed.

The first measured layout63 **fighter motion-field** difference is constructor6551:
Main Xvelocity−128/current0, both FIGHTER/group1. Main immediately performs110
original hover moves with `-128 >> 8 = -1`, X2624→2514 and zero swing/yvelocity.
Current waits, initializes6596 with max/yvelocity256 and acceleration8, then performs
65 original hover moves with Xvelocity0 and full16.16 Y; X stays2624. Original
normal allocation returns and constructor links bind **that body→actual Gun→actual
Shot** within each JVM. At6660 the Gun's original parentX reads2514/2624, delayedY2520
in both; the returned shot origins are2531/2641,Y2512. Current's stored charging
parent is that same raw Gun. Native fighter/gun initialization and motion sources
support current's changed operations; this does not certify48 as a native oracle.

Layout23's missing bounce is resolved further: current's same raw fighter enters
initialization7006, writes destroyed=false→true and returns GONE, without a captured
attack callback. Source maps this outcome to the fighter pair-gate early-delete
branch; its underlying group2 flag/registry operand was not retained. Main instead
has P1's original ENEMY attack/callback/destruction and856→−856 bounce at7164.
The current absence is an initialization retirement, with no measured prior P2 kill.

Original placed-ring overlap and `addRings(1)` normal-return carriers record46/48
unique source-index awards. Current's extra **indices5 at7172 and3 at7178 are P1
collections**; shared index1 is Main P2 at7220/current P1 at7184. Those two extra
ordinary awards account numerically for+2; `ringAcquired` is unused as an oracle.
No attracted entry is visible in either full projection. Native collector addresses,
full-route correctness and why the group2 gate rejects remain unqualified.

Expected46, every maintained movie/assertion and runtime stay unchanged. All
temporary source is restored whole byte exact; its compiled classes and inspected
raw output are consumed. The [dated R5 plan](../../plans/2026-10-13-s3k-playable-completion.md#widepaired800-fighter-gun-shot-and-pickup-attribution-r5--2026-10-21)
records native owners, strong bindings, source verdict and limits. No third request,
maintained-class/broad/native run, oracle adaptation or current paired800
MTZ/Mecha/DEZ certification follows this attribution; Main complete remains unknown.

## Wide paired original group2 gate R7 — 2026-10-21

Exactly two ordinary row0 NORMAL-DRAW prefixes use Main `efd605142` and current
`a49194fd0`, unchanged movie/P2neutral/Settings7 native800 paired/off/titlefalse,
ending at input7007 inclusive. Each actual XML records7008 inputs/21024 phase rows/
115 fields/**0JUnit tests/0skips**. All115 fields match the six retained same-arm
PRE/POST3491,3507,6721 rows; no other full115 R5 public rows through7007 were retained.
No later endpoint or maintained-class result is added.

The actual layout23 fighter is born6962 in both JVMs, original spawn
`(1312,2304,$A0,$22,flags1,rawY10496,respawn=false)`. Original raw-reference bindings
join its gate lookup to the same locally installed SSZ state and registry; the
state is **non-null** in both arms. These are local bindings, not cross-JVM identity.

| Original layout23 gate | Main | Current |
|---|---|---|
| Input of original evaluation | 6962, early movement gate | 7006, initialization after wait |
| Evaluated word / mask | `$000F` / `$0004` | `$000B` / `$0004` |
| `eggRoboFlyByPassed(2)` / waiting return | true / false | false / true |
| Original selected outcome | admission | GONE/delete returns normally |

Both arms construct/install one SSZ state at cold boot. Constructor capture has
**no evaluated word**; JVM default0 is source knowledge. Original query counts are
four per arm (one per group3,0,1,2), including exactly one layout23/group2 query.
There is no whole-word setter, act2 word write, restore write or registry replacement
before this gate. The only registry clear is explicitly CLOSE after input7007.

The actual group2 publisher is Main's layout30 fly-by, born6792 at
`(1552,2448,$A0,$20,flags0,rawY2448,respawn=false)`. Its original6819 publication
reads11, ORs mask4 and writes15 to that exact local state. Current constructs the
corresponding placed fly-by at6792 but has **no group2 publication through7007**;
its last preceding write is group1 at6563,9→11. No null lookup, overwrite or state
replacement explains the measured clear bit.

Shipped `sub_91914` tests group2/mask4 and deletes when clear, matching the observed
conditional outcomes for the measured operands. Native runtime publication timing
and the layout30 continuation/flight/exit/lifetime remain unmeasured; this does
not certify the word11, the trajectory or48 rings as native-correct. That fly-by's
original exit/publication boundary is the next precise question if needed.
Expected46, movies/tests/runtime/POM remain unchanged. Whole temporary sources are
restored and their compiler-created classes/raw outputs consumed; a normal restored
current compile only accounts for outputs. Useful source/inverses/compact facts
remain external. The [dated R7 plan](../../plans/2026-10-13-s3k-playable-completion.md#wide-paired-original-group2-gate-r7--2026-10-21)
records complete ordered write history and limits. Main complete and current
paired800 MTZ/Mecha/DEZ remain unqualified; no third prefix/native/broad run occurs.
