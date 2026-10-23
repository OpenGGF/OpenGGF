# S3K Sky Sanctuary Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_1`, engine zone `$0A` act index 0,
ROM `Current_zone_and_act = $A00`, SKL object set.
Character routes: Sonic, Sonic + Tails, Tails (`LevelSelect_CheckKnuckles` denies Knuckles except
with `Debug_cheat_flag != 0`, and no Knuckles art or route exists for act 1).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: all 213 placed records have concrete owners; arrival Death Egg, bosses,
results and the DEZ launch are implemented. Cold Sonic + Tails routes at 320/800,
Sonic solo at 320 and Tails solo at 320/800 defeat all three bosses and load DEZ1
without leader deaths or position/health/ring seeds. Native 800px paired coverage
also checks the released destination, native P2 roster and full-load history
isolation. Sonic solo at 800, intermediate viewport widths, donors, additional
team shapes and native presentation acceptance remain open.
Nothing below certifies the act.

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
| LIFE: death, starposts `$34:$02/$03/$04`, respawn, Y seam | `LevelSetup` (sonic3k.asm:102231) `clr.l Events_bg+$00/$04/$08/$0C`; `Level:` (7623) `clearRAM _unkFA80,$80`; `SSZ1_ScreenInit` starpost path; `sub_575EA` MTZ pre-lock | 320 | `TestS3kSszLifecycleProduction` | yes | declared restart, not a route: `SSZ1_ScreenInit` drags a leader with no star post back to the arrival column, so `$34:$03` is written the way the ROM writes it | yes: the reload resets the timeline instead of continuing the pre-death history, and builds a new `SszZoneRuntimeState` (`assertSame` fails) | yes: `hpz_2` is itself a `$34:$03` restart (metadata `start_x 0x14C0 / start_y 0x00E8`) and its row 0's player `($14C0,$EC)` and camera `($1420,$8C)` are matched exactly | `16-ssz-death-restarts-at-the-star-post.mp4`, frames 77/150/220 | Only `$34:$03` is driven; `$34:$02`/`$34:$04` and a wide row are owed, as is a capture of a post being physically touched |
| BOSS: GHZ recreation hits, flash, scatter | `sub_7A5A0` / `sub_7A614` / `word_7A622` / `word_7A628`; `Touch_Enemy` `.checkhurtenemy` (sonic3k.asm:20958); `loc_849D8` / `Obj_FlickerMove` / `Set_IndexedVelocity` / `Obj_VelocityIndex`; `ObjDat3_7A678`; `word_7A65A` | 320 | `TestS3kSszGhzArenaHeadless#onlyTheBallHasCollisionAndItHurtsThePlayerThroughTheTouchPass`, `#oneRealHitFlashesThreePaletteWordsFromTheShippedRow`, `#theKillingHitScattersTheChainOneLinkAFrameAndTakesTheBallsHitbox`, `#theEmitterFlickersEveryOtherFrameAndCarriesNoCollisionByte` | yes | all eight hits and the ball's hurt go through `ObjectTouchResponseController` with the player pinned attacking/non-attacking; no `onPlayerAttack` call anywhere | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($160,$7C0)`, 1142 rows: its aux `object_appeared` rows carry each slot's code-pointer address, which dates the whole fight. Spawn `($270,$780)`, first dispatch init + 33, chain drop at `x = $200`, `$3F + 1 = 64` fade frames, `$78 = 120` escape frames all match the port's intervals. The 184-frame defeat total matches too: it was 183 here until these rows showed the killing frame takes no decrement on the cartridge, and `defeatDeferralAppliesToThisBoss()` now models that, as `HczMinibossInstance` does. Native rings fall 55 → 45 across the window, which the port could not do before the ball got its box. The rows **corrected** the scatter: all six links convert on the killing-hit frame, not one a frame | none yet | Palette is not in this schema, so the flash is still uncompared. The flash writes line 0 colours 7/14/15, measured as already holding `word_7A628`'s first row before the fight, so the row the ROM leaves behind is invisible |
| BOSS: GHZ recreation lock / fight / defeat | `sub_575EA` `loc_57686`-`loc_576E8`; `Obj_SSZGHZBoss` `off_7A2B4` routines 0-`$A`, `sub_7A5A0`, `loc_7A3CE`-`loc_7A3F8`; children `ChildObjDat_7A684`/`_7A69E`, `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszGhzArenaHeadless` | yes | declared restart at `($200,$7C8)`: the leader settles on the arena floor at `($200,$86C)` with the camera at `($160,$7C0)` by the act's own physics, and both gates then fire. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | yes: mid-swing with all six links out; nulling the links' `chainParent` restore fails five of the six | **not claimed.** Values come from the routines only; the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142 rows) is unread | `17-ssz-green-hill-recreation-ball-and-chain.mp4`, frames 330/420/600/900 (pre-review: no ball hitbox, no scatter) and `18-ssz-green-hill-ball-and-chain-hurts.mp4`, frames 150/250/330/**361**/400 — 361 is the ball on top of a standing Sonic, the frame he is killed, which the pre-review fight could not do. Filmed with `--star-post --x 0x200 --y 0x7C8`; a bare teleport cannot capture this arena, because `SSZ1_ScreenInit`'s no-star-post intro overrides `--x/--y` | The `$79:$AA` pad's post-defeat rise is implemented but not driven end to end |
| BOSS: MTZ recreation lock / spawn / entry | `sub_575EA` `loc_5770C`-`loc_5775C`; `Obj_SSZMTZBoss` init, `loc_7A712`, `off_7A728`, `loc_7A72C`, `loc_7A7C4`; `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszMtzArenaHeadless#theUpperArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles`, `#thePreLockLeftLimitIsTheGreenHillWordsToChoose`, `#theShipEntersAtSeventeenHundredAndDispatchesThirtyThreeFramesAfterItsInit`, `#theSetupOverwritesTheEggRoboPairingWord` | yes | declared restart at `($1700,$420)`: the leader settles on the upper arena floor, the lock fires with `Camera_X` at `$1660` through `nativeFramedCameraX` at both widths, and the spawn follows when the camera eases to `$380`. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($1660,$380)`, 1004 rows: the boss slot appears on native frame 6156 and takes `loc_7A71A` on 6189 — init + 33, which the test asserts as `$1F + 2`. The Mecha Sonic head and all seven orbs appear together on 6190, one frame later, which is what `loc_7ADA2`'s `movea.l a0,a1` plus its six allocations produce. The arena floor `y = $42C` and the restart position come from the same window | `19-ssz-metropolis-orb-ring.mp4` (`raw-31-ssz-mtz-fight`), frames 215/260/**318** — 215 is the ship and its ring entering the arena, 260 the ring turning around it, 318 an orbiting orb killing a ring-less Sonic. Filmed with `--star-post --x 0x1700 --y 0x420` | The spawn writes world coordinates, so unlike Green Hill nothing here needs the camera offset |
| BOSS: MTZ ship motion, orbs, hits, lasers, defeat, pad `$79:$F6` | `off_7A7F0` arms 0-`$E`, `sub_7A85A`, `off_7AA60`, `sub_7AB56`, `ChildObjDat_7AB80`; `sub_7AC06` / `sub_7ACF2` / `sub_7AD6A` / `word_7AD7E`; `loc_7AD8A` routines 0-8, `sub_7AEB0`, `sub_7AF5A`, `sub_7B0C2`; `loc_7AC7A`-`loc_7ACA4`; `loc_4554E` gated pad on `Events_bg+$02` | 320 | `TestS3kSszMtzArenaHeadless#theDescentIsOnePixelAFrameAndTurnsToFaceThePlayerAtFourTwoZero`, `#thePatrolTurnsAtItsTwoLimitsAndHoversFourPixels`, `#theShipLeavesThePatrolOnItsSecondTurnAroundAndNotItsFirst`, `#theOrbRingSortsItselfFrontAndBackFromTheCosineOfItsAngle`, `#aHitLaunchesExactlyOneOrbAndSpendsOneArmCycle`, `#theHitWindowFlashesTheShippedRowAndReopensTheBoxAtTheSmallerSize`, `#theLastArmCycleStartsTheLaserPassAndFiresThreePairs`, `#theDefeatTakesTheCartridgesFrameCountAndOpensTheGatedPad` | yes | every hit goes through `ObjectTouchResponseController` with the player pinned attacking, and the tests pop the launched orbs the same way — `loc_7A98A` will not release the ship until `$30(a0)` is zero, so the fight has to be played rather than stepped | yes: mid-fight with one orb off the ring; the restore relinks all seven orbs and `$30(a0)` | **compared, comparison-only.** The same window dates the killing hit at 6651 and `loc_7AC92` at 6715 — `64 = $3F + 1` — which is why `defeatDeferralAppliesToThisBoss()` was overridden before a line of the fight was written rather than after. Native rings fall 55 → 38 across the window. The orbit, the sort, the laser cadence and the flash have **no** native comparison | `20-ssz-metropolis-orb-launched-by-a-hit.mp4` (`raw-34-ssz-mtz-chase`, frames 417/441/490/706), `21-ssz-metropolis-laser-pass.mp4` (`raw-35-ssz-mtz-laser`, 1745/1800/1855) and `22-ssz-metropolis-defeat-and-gated-pad.mp4` (`raw-34`, 1737/2205/2260/2369). All three want `--rings`, ported here from the Lava Reef campaign's `b35f59d33`: the declared restart starts Sonic at zero rings and every orbiting orb is `$87`, a `Touch_ChkHurt` byte that hurts an attacking player too, so clip `19`'s ring-less capture died at first contact. With 355 seeded, one 320 route lands all eight hits and rides the pad out. The laser pass needed its own run — the eighth hit lands about 30 frames after the seventh, so `raw-35` branches `raw-34`'s input at frame 1720 and stops attacking for 240 frames. The arm-raise cycles are still not cut to a clip of their own | Gaps recorded in `s3k-known-bugs.md`: explosion replacement-slot stop-byte fidelity remains open; orbs still on the ring are deleted with the ship where the cartridge leaves them orbiting a freed slot; the shared `S3K_SPECIAL_PROPERTY` arm teleports the sidekick onto every `$C0` object. The laser gap is `$1E + $10 = 46` frames, because `loc_7AA44` returns on `$33(a0)` before `sub_7AB56` is reached |
| BOSS: Mecha Sonic spawner, entry and attack loop | `loc_45A66`-`loc_45AB0`; `Obj_SSZEndBoss` `SSZEndBoss_Index` act-1 entries 0-`$28` (`loc_7B2DC`, `loc_7B308`, `loc_7B3E6`, `loc_7B41C`, `loc_7B44A`, `loc_7B462`, `loc_7B484`, `loc_7B4EC`, `loc_7B544`, `loc_7B57A`, `loc_7B5E8`, `loc_7B63A`-`loc_7B804`); `sub_7D2D8` / `byte_7D2FC`; `sub_7D312`; `sub_7D35A`; `loc_7D216`; `sub_7D236` / `byte_7D24C`; `DPLCPtr_MechaSonic` | 320 | `TestS3kSszMechaSpawnHeadless` | yes | declared restart at the pad's own `($1A40,$670)`: the final arena's lock fires, the camera eases to `$5C0`, and `loc_45A84`'s `Camera_Y == Camera_max_Y` gate allocates the boss through the plain-`AllocateObject` path. Selected cold Sonic + Tails routes at320/800 also reach and defeat this boss | yes: mid-entry, with the two `ChildObjDat_7D47A` after-images relinked | **compared, comparison-only.** The Sonic + Tails run's SSZ **third** segment, `hpz_3` (`zone_id 10`, start `($1880,$968)`), carries the whole fight; the first reading of this row said the `hpz` window ended before the arena, and that was wrong. Camera `($19A0,$5C0)` at the spawn, so `Cam_X + $160 = $1B00` and `Cam_Y + $A0 = $660` are the native spawn exactly, and the routine dump's resting X values `$1AC0` and `$19C0` are `_unkFAB6` and `_unkFAB4` — `loc_7D216`'s two limits. Pad at 358, boss at 402, routine 4 first dispatched 406, pad removed 460 (the boss passes `$1A40` about 426, plus `loc_45AB0`'s `$20`). The graph runs 4 → 8 → `$0A` → `$0C` → `$0E` → `$10` → `$12` → `$14` → `$16` → `$18` → `$0A`, then a long landing straight to `$14` → `$1A` → `$1C`, which is the two `loc_7B484` branches and `byte_7B62E` stepping 0 then 1. Defeat 1310, routine 2 on 1438 and routine 4 plus the `loc_7D056` slot on 1439. **Routine 6 never appears** in the dump because the boss is off screen for it, which is a recorder property and not a ROM fact | `23-ssz-mecha-sonic-entry-and-attacks.mp4` (`raw-38-ssz-mecha-line1`, frames 200/268/330/430/560): the entry from the right, the return along the top and the attack cycle on the arena floor. The pad's own explosion is driven by the test only — at this camera it sits where the column meets the floor — and subsequent `raw-43-mecha-complete-graph` covers implemented defeat/results/launch through DEZ1 | Stages 1 and 2 of slice 7. `loc_7B81A`'s act-1 routines 0/2/4 and `loc_7D056`'s handover are in; the later campaign implements palette/spark/defeat owners. The bare `loc_7B39C` allocation writes no SST and needs no object. Native hit-window/slot-reuse verification remains open |
| BOSS: Mecha Sonic spawn / fight / defeat, results + save | `loc_45A84`, `sub_868F8`, `Check_TailsEndPose`, `loc_2DCA0` | 320 | `TestS3kSszMechaSpawnHeadless` | partial | declared final-pad checkpoint; real results reaches end flag | yes: real results graph restore and forward replay | ROM source | recording owed | Production results added in campaign; native P2 pose, allocation exhaustion, signed countdown. Save/launch and broader route matrix still open. |
| EVENT: crumble, hot-swap, Death Egg BG, debris, ramp script, `$B00` request | `SSZ1_ScreenEvent` stages 0/4/8, `Obj_57E96`, `sub_5750C`, `sub_574DC`, `loc_58192`, `loc_581D2` | 320, 400, 800 | `TestS3kSszMechaSpawnHeadless`, `TestSszLaunchState`, `TestSszLaunchBackground` | partial | declared checkpoint and positioned boss hits; production results, first forced jump and neutral-input launch reach actual DEZ1 load | graph recreation and forward replay at four launch boundaries, including retained Plane B | source-backed; native parity not yet measured | clip 24: controller Hyper checkpoint clear, full 4800-frame capture through DEZ1 | Native Sonic 320/400/800, Sonic + Tails 320, Tails 400 and S1 donor 320 pass component handover. Cold320/800 paired and solo-Tails complete routes verify actual-load history isolation. Extra-followers, exhausted slots, other handover configurations and native comparison remain open. |
| ROUTE: native800px paired all three bosses → released DEZ1 | Ordinary cold entry; GHZ/MTZ/Mecha native health8..0; `loc_7D056` results; `Obj_57E96`/`loc_581D2` launch; `loc_6468` destination assembly | Sonic + Tails, donor off,800px | `TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | existing owners; controller-only completion | yes,21014 inputs, no leader death/life loss | two-cycle45 whole-world restore/replay at44 semantic spots; actual full-load history retirement and separate released DEZ window | ROM-backed owners/start/roster; no new native-emulator comparison | independent fully drawn engine capture; no native pixel claim | Qualified first9914 inputs preserved; native CPU Tails recovery allowed. Donor, extra-followers, Sonic solo800, intermediate widths and native acceptance remain open. |
| LOAD: `$B00` (DEZ) actual destination entry | DEZ campaign; `loc_6468`, `Obj_LevelIntro_PlayerRun`/`loc_44A26`/`loc_44A56` | native selected cold configurations; paired800 detailed below | Complete cold-route checks; `TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline` | yes | actual assembly and native entrance control release | paired800 two-cycle45 intro/released world windows plus actual full-load history retirement | ROM start/floor seating and intro owner; native scene comparison open | independent live DEZ engine still | This row covers destination entry. Full DEZ traversal and later endpoints belong to the DEZ campaign; no native pixel certification. |
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

| Input | Differing pixels | Attribution |
|---|---:|---|
| 118, 119, 330, 680, 1430 | 0 | Exact |
| 1341, 1342, 1343 | 15/19/19 → 0 | Fixed: SAT-collected zones submitted Tails' tail before his body, putting the tail in front. `Tails_tails` follows `Player_2` with the parent's copied priority (`Obj_Tails_Tail_Main`, sonic3k.asm:30074), so native sprite-table order puts the body first (native SAT entries 22 Sonic, 24 body `$6A0`, 25 tail `$6B0`) |
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

## Object-pipeline performance follow-up (2026-10-22)

Death Egg trails use raw owned-slot dereference: pending-deletion positions remain readable until removal, empty slots supply zero, and a new occupant of any type supplies its position. `TestSszDeathEggCutscene` adds the raw/removal/replacement boundary; trail animation continues to own lifetime.

The [query/allocation audit](../../audits/performance/2026-10-22-native-object-query-performance.md)
and its companion implementation records own exact tested states, commands and
remaining delivery checks. These local checks do not close inherited viewport,
donor/team, full-route, load/respawn, audiovisual or whole-world rewind products.
