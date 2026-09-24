# S3K Sky Sanctuary Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_1`, engine zone `$0A` act index 0,
ROM `Current_zone_and_act = $A00`, SKL object set.
Character routes: Sonic, Sonic + Tails, Tails (`LevelSelect_CheckKnuckles` denies Knuckles except
with `Debug_cheat_flag != 0`, and no Knuckles art or route exists for act 1).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: all 213 placed records have concrete owners; arrival Death Egg, bosses,
results and the DEZ launch are implemented. The native320 Sonic + Tails cold route now defeats all three bosses and loads DEZ1
without deaths or position/health/ring seeds. Wide cold routes and remaining
character/donor/team breadth and native acceptance remain open.
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
| BG: cloud oscillator `_unkEE9C` and the ten solid clouds | `loc_57B6A`/`loc_57B76`, `loc_57B8E`, `word_5853E`, `SolidObjCheckSloped2` | 320 + 800 | `TestS3kSszBackgroundClouds` | yes | yes | yes | ten rows decoded from the ROM inside the test; `y_pos = y_vel - _unkEE9C` asserted for 240 frames | `04a`/`04b` | Riding one is not yet exercised by a route; eight of the ten rows over-read their own slope table into the next one, as the ROM does |
| ANIM: AniPLC (6 scripts), and none in act 2 | `AniPLC_SSZ` `$28AA4`; `Offs_AniFunc` entries 40-43 (`AnimateTiles_DoAniPLC` / `AnimateTiles_NULL`) | 320 | `TestS3kSszPatternAnimation` | yes | yes (runs from the cold load) | n/a (the animator's counters are snapshotted by `PatternAnimatorSnapshot`) | all six scripts' duration, destination tile, frame count and tiles per frame asserted from the declarations, including the third script's three frames | `04a` | No per-tile pixel comparison against native |
| OBJECT: `$79` SSZ pads (all ten act-1 placements) | `Obj_SSZHPZTeleporter` init, `loc_455BA`, `loc_455CC`-`loc_45790`, `loc_457BE`, `loc_4581C`, `sub_45866` | 320 | `TestS3kSszTeleporterPads` | yes | not yet: no cold route reaches a pad past the bridge | not exercised | lift `(subtype & $3F) * $10`, the gated-pad sink and 4-frame rise, the launch bounds/`Scroll_lock`/`Events_bg+$05` writes and `byte_466E8` all asserted from the routines; the gated-pad cases use a declared seeded `st (Events_bg+$00)` because slice 5's boss does not exist | `08-ssz-teleporter-pad-launch.mp4`, frame 200 | Two cases seed a boss-defeat flag; the `($1A40,$670)` spawner draws but allocates no boss (s3k-known-bugs #42) |
| OBJECT: `$7F` floating platform (8 placements) | `Obj_SSZFloatingPlatform` / `loc_44AA0`; `SolidObjectTop` `d1 $2B`, `d2`/`d3` `$11` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | the 4-pixel dip and its one-pixel-per-frame ramp asserted from `loc_44AA0` | `06-ssz-floating-platform-dips.mp4`, frame 150 | Wide-viewport and donor rows open |
| OBJECT: `$7E` collapsing column + debris (25 placements) | `Obj_SSZCollapsingColumn` / `loc_44B30`/`loc_44B90`, debris `loc_44BCC`/`loc_44BF8`, `word_46618` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | eight pieces from `word_46618` decoded in the test, the `routine(a0)` report count and the `$7FFF` park asserted from the routines | `07-ssz-column-breaks-into-eight.mp4`, frame 90 | The `Random_Number` bob phase is drawn per column, so the column order of a load moves the RNG stream; not compared to native |
| OBJECT: `$7C` collapsing bridge + shared debris (8 placements) | `Obj_SSZCollapsingBridge` / `loc_44C76`/`loc_44C9C`/`loc_44D22`, debris `loc_45052` | 320 | `TestS3kSszTraversalPlatforms` | yes | not yet | not exercised | subtype bit 7 (the only bit read), the four pieces with their `$102` frame word and 6/12/18/24 delays, and the 8-px-per-six-frames shrink to the `$7FFF` park, all asserted from the routine | `07-ssz-column-breaks-into-eight.mp4` shows the `$7E` sibling; `$7C`'s own clip is owed | Wide-viewport and donor rows open |
| OBJECT: `$74 $75 $76 $7A $7B $7D`, EggRobo `$A0` | per-object inits | native + bounded wide cases | `TestS3kSszCarriersAndSprings`, `TestS3kSszTraversalPlatforms`, `TestS3kSszEggRobo`, `TestS3kSszPlacementCensus` | implemented; zero placeholder placements | full cold route open | local family checks; complete participant/route product open | source-backed; complete native sequences open | historical local captures | Earlier113-placeholder count was a slice baseline, not current status |
| LIFE: death, starposts `$34:$02/$03/$04`, respawn, Y seam | `LevelSetup` (sonic3k.asm:102185) `clr.l Events_bg+$00/$04/$08/$0C`; `Level:` (7623) `clearRAM _unkFA80,$80`; `SSZ1_ScreenInit` starpost path; `sub_575EA` MTZ pre-lock | 320 | `TestS3kSszLifecycleProduction` | yes | declared restart, not a route: `SSZ1_ScreenInit` drags a leader with no star post back to the arrival column, so `$34:$03` is written the way the ROM writes it | yes: the reload resets the timeline instead of continuing the pre-death history, and builds a new `SszZoneRuntimeState` (`assertSame` fails) | yes: `hpz_2` is itself a `$34:$03` restart (metadata `start_x 0x14C0 / start_y 0x00E8`) and its row 0's player `($14C0,$EC)` and camera `($1420,$8C)` are matched exactly | `16-ssz-death-restarts-at-the-star-post.mp4`, frames 77/150/220 | Only `$34:$03` is driven; `$34:$02`/`$34:$04` and a wide row are owed, as is a capture of a post being physically touched |
| BOSS: GHZ recreation hits, flash, scatter | `sub_7A5A0` / `sub_7A614` / `word_7A622` / `word_7A628`; `Touch_Enemy` `.checkhurtenemy` (sonic3k.asm:20922); `loc_849D8` / `Obj_FlickerMove` / `Set_IndexedVelocity` / `Obj_VelocityIndex`; `ObjDat3_7A678`; `word_7A65A` | 320 | `TestS3kSszGhzArenaHeadless#onlyTheBallHasCollisionAndItHurtsThePlayerThroughTheTouchPass`, `#oneRealHitFlashesThreePaletteWordsFromTheShippedRow`, `#theKillingHitScattersTheChainOneLinkAFrameAndTakesTheBallsHitbox`, `#theEmitterFlickersEveryOtherFrameAndCarriesNoCollisionByte` | yes | all eight hits and the ball's hurt go through `ObjectTouchResponseController` with the player pinned attacking/non-attacking; no `onPlayerAttack` call anywhere | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($160,$7C0)`, 1142 rows: its aux `object_appeared` rows carry each slot's code-pointer address, which dates the whole fight. Spawn `($270,$780)`, first dispatch init + 33, chain drop at `x = $200`, `$3F + 1 = 64` fade frames, `$78 = 120` escape frames all match the port's intervals. The 184-frame defeat total matches too: it was 183 here until these rows showed the killing frame takes no decrement on the cartridge, and `defeatDeferralAppliesToThisBoss()` now models that, as `HczMinibossInstance` does. Native rings fall 55 → 45 across the window, which the port could not do before the ball got its box. The rows **corrected** the scatter: all six links convert on the killing-hit frame, not one a frame | none yet | Palette is not in this schema, so the flash is still uncompared. The flash writes line 0 colours 7/14/15, measured as already holding `word_7A628`'s first row before the fight, so the row the ROM leaves behind is invisible |
| BOSS: GHZ recreation lock / fight / defeat | `sub_575EA` `loc_57686`-`loc_576E8`; `Obj_SSZGHZBoss` `off_7A2B4` routines 0-`$A`, `sub_7A5A0`, `loc_7A3CE`-`loc_7A3F8`; children `ChildObjDat_7A684`/`_7A69E`, `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszGhzArenaHeadless` | yes | declared restart at `($200,$7C8)`: the leader settles on the arena floor at `($200,$86C)` with the camera at `($160,$7C0)` by the act's own physics, and both gates then fire. No cold route reaches the arena yet | yes: mid-swing with all six links out; nulling the links' `chainParent` restore fails five of the six | **not claimed.** Values come from the routines only; the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142 rows) is unread | `17-ssz-green-hill-recreation-ball-and-chain.mp4`, frames 330/420/600/900 (pre-review: no ball hitbox, no scatter) and `18-ssz-green-hill-ball-and-chain-hurts.mp4`, frames 150/250/330/**361**/400 — 361 is the ball on top of a standing Sonic, the frame he is killed, which the pre-review fight could not do. Filmed with `--star-post --x 0x200 --y 0x7C8`; a bare teleport cannot capture this arena, because `SSZ1_ScreenInit`'s no-star-post intro overrides `--x/--y` | The `$79:$AA` pad's post-defeat rise is implemented but not driven end to end |
| BOSS: MTZ recreation lock / spawn / entry | `sub_575EA` `loc_5770C`-`loc_5775C`; `Obj_SSZMTZBoss` init, `loc_7A712`, `off_7A728`, `loc_7A72C`, `loc_7A7C4`; `Child1_MakeMechaHead` | 320 + 800 | `TestS3kSszMtzArenaHeadless#theUpperArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles`, `#thePreLockLeftLimitIsTheGreenHillWordsToChoose`, `#theShipEntersAtSeventeenHundredAndDispatchesThirtyThreeFramesAfterItsInit`, `#theSetupOverwritesTheEggRoboPairingWord` | yes | declared restart at `($1700,$420)`: the leader settles on the upper arena floor, the lock fires with `Camera_X` at `$1660` through `nativeFramedCameraX` at both widths, and the spawn follows when the camera eases to `$380`. No cold route reaches this arena either | covered by the row below | **compared, comparison-only.** `s3k-sonic-tails-complete-emeralds` `hpz` segment, camera `($1660,$380)`, 1004 rows: the boss slot appears on native frame 6156 and takes `loc_7A71A` on 6189 — init + 33, which the test asserts as `$1F + 2`. The Mecha Sonic head and all seven orbs appear together on 6190, one frame later, which is what `loc_7ADA2`'s `movea.l a0,a1` plus its six allocations produce. The arena floor `y = $42C` and the restart position come from the same window | `19-ssz-metropolis-orb-ring.mp4` (`raw-31-ssz-mtz-fight`), frames 215/260/**318** — 215 is the ship and its ring entering the arena, 260 the ring turning around it, 318 an orbiting orb killing a ring-less Sonic. Filmed with `--star-post --x 0x1700 --y 0x420` | The spawn writes world coordinates, so unlike Green Hill nothing here needs the camera offset |
| BOSS: MTZ ship motion, orbs, hits, lasers, defeat, pad `$79:$F6` | `off_7A7F0` arms 0-`$E`, `sub_7A85A`, `off_7AA60`, `sub_7AB56`, `ChildObjDat_7AB80`; `sub_7AC06` / `sub_7ACF2` / `sub_7AD6A` / `word_7AD7E`; `loc_7AD8A` routines 0-8, `sub_7AEB0`, `sub_7AF5A`, `sub_7B0C2`; `loc_7AC7A`-`loc_7ACA4`; `loc_4554E` gated pad on `Events_bg+$02` | 320 | `TestS3kSszMtzArenaHeadless#theDescentIsOnePixelAFrameAndTurnsToFaceThePlayerAtFourTwoZero`, `#thePatrolTurnsAtItsTwoLimitsAndHoversFourPixels`, `#theShipLeavesThePatrolOnItsSecondTurnAroundAndNotItsFirst`, `#theOrbRingSortsItselfFrontAndBackFromTheCosineOfItsAngle`, `#aHitLaunchesExactlyOneOrbAndSpendsOneArmCycle`, `#theHitWindowFlashesTheShippedRowAndReopensTheBoxAtTheSmallerSize`, `#theLastArmCycleStartsTheLaserPassAndFiresThreePairs`, `#theDefeatTakesTheCartridgesFrameCountAndOpensTheGatedPad` | yes | every hit goes through `ObjectTouchResponseController` with the player pinned attacking, and the tests pop the launched orbs the same way — `loc_7A98A` will not release the ship until `$30(a0)` is zero, so the fight has to be played rather than stepped | yes: mid-fight with one orb off the ring; the restore relinks all seven orbs and `$30(a0)` | **compared, comparison-only.** The same window dates the killing hit at 6651 and `loc_7AC92` at 6715 — `64 = $3F + 1` — which is why `defeatDeferralAppliesToThisBoss()` was overridden before a line of the fight was written rather than after. Native rings fall 55 → 38 across the window. The orbit, the sort, the laser cadence and the flash have **no** native comparison | `20-ssz-metropolis-orb-launched-by-a-hit.mp4` (`raw-34-ssz-mtz-chase`, frames 417/441/490/706), `21-ssz-metropolis-laser-pass.mp4` (`raw-35-ssz-mtz-laser`, 1745/1800/1855) and `22-ssz-metropolis-defeat-and-gated-pad.mp4` (`raw-34`, 1737/2205/2260/2369). All three want `--rings`, ported here from the Lava Reef campaign's `b35f59d33`: the declared restart starts Sonic at zero rings and every orbiting orb is `$87`, a `Touch_ChkHurt` byte that hurts an attacking player too, so clip `19`'s ring-less capture died at first contact. With 355 seeded, one 320 route lands all eight hits and rides the pad out. The laser pass needed its own run — the eighth hit lands about 30 frames after the seventh, so `raw-35` branches `raw-34`'s input at frame 1720 and stops attacking for 240 frames. The arm-raise cycles are still not cut to a clip of their own | Gaps recorded in `s3k-known-bugs.md`: explosion replacement-slot stop-byte fidelity remains open; orbs still on the ring are deleted with the ship where the cartridge leaves them orbiting a freed slot; the shared `S3K_SPECIAL_PROPERTY` arm teleports the sidekick onto every `$C0` object. The laser gap is `$1E + $10 = 46` frames, because `loc_7AA44` returns on `$33(a0)` before `sub_7AB56` is reached |
| BOSS: Mecha Sonic spawner, entry and attack loop | `loc_45A66`-`loc_45AB0`; `Obj_SSZEndBoss` `SSZEndBoss_Index` act-1 entries 0-`$28` (`loc_7B2DC`, `loc_7B308`, `loc_7B3E6`, `loc_7B41C`, `loc_7B44A`, `loc_7B462`, `loc_7B484`, `loc_7B4EC`, `loc_7B544`, `loc_7B57A`, `loc_7B5E8`, `loc_7B63A`-`loc_7B804`); `sub_7D2D8` / `byte_7D2FC`; `sub_7D312`; `sub_7D35A`; `loc_7D216`; `sub_7D236` / `byte_7D24C`; `DPLCPtr_MechaSonic` | 320 | `TestS3kSszMechaSpawnHeadless` | yes | declared restart at the pad's own `($1A40,$670)`: the final arena's lock fires, the camera eases to `$5C0`, and `loc_45A84`'s `Camera_Y == Camera_max_Y` gate allocates the boss through the plain-`AllocateObject` path. No cold route reaches this arena either | yes: mid-entry, with the two `ChildObjDat_7D47A` after-images relinked | **compared, comparison-only.** The Sonic + Tails run's SSZ **third** segment, `hpz_3` (`zone_id 10`, start `($1880,$968)`), carries the whole fight; the first reading of this row said the `hpz` window ended before the arena, and that was wrong. Camera `($19A0,$5C0)` at the spawn, so `Cam_X + $160 = $1B00` and `Cam_Y + $A0 = $660` are the native spawn exactly, and the routine dump's resting X values `$1AC0` and `$19C0` are `_unkFAB6` and `_unkFAB4` — `loc_7D216`'s two limits. Pad at 358, boss at 402, routine 4 first dispatched 406, pad removed 460 (the boss passes `$1A40` about 426, plus `loc_45AB0`'s `$20`). The graph runs 4 → 8 → `$0A` → `$0C` → `$0E` → `$10` → `$12` → `$14` → `$16` → `$18` → `$0A`, then a long landing straight to `$14` → `$1A` → `$1C`, which is the two `loc_7B484` branches and `byte_7B62E` stepping 0 then 1. Defeat 1310, routine 2 on 1438 and routine 4 plus the `loc_7D056` slot on 1439. **Routine 6 never appears** in the dump because the boss is off screen for it, which is a recorder property and not a ROM fact | `23-ssz-mecha-sonic-entry-and-attacks.mp4` (`raw-38-ssz-mecha-line1`, frames 200/268/330/430/560): the entry from the right, the return along the top and the attack cycle on the arena floor. The pad's own explosion is driven by the test only — at this camera it sits where the column meets the floor — and subsequent `raw-43-mecha-complete-graph` covers implemented defeat/results/launch through DEZ1 | Stages 1 and 2 of slice 7. `loc_7B81A`'s act-1 routines 0/2/4 and `loc_7D056`'s handover are in; the later campaign implements palette/spark/defeat owners. The bare `loc_7B39C` allocation writes no SST and needs no object. Native hit-window/slot-reuse verification remains open |
| BOSS: Mecha Sonic spawn / fight / defeat, results + save | `loc_45A84`, `sub_868F8`, `Check_TailsEndPose`, `loc_2DCA0` | 320 | `TestS3kSszMechaSpawnHeadless` | partial | declared final-pad checkpoint; real results reaches end flag | yes: real results graph restore and forward replay | ROM source | recording owed | Production results added in campaign; native P2 pose, allocation exhaustion, signed countdown. Save/launch and broader route matrix still open. |
| EVENT: crumble, hot-swap, Death Egg BG, debris, ramp script, `$B00` request | `SSZ1_ScreenEvent` stages 0/4/8, `Obj_57E96`, `sub_5750C`, `sub_574DC`, `loc_58192`, `loc_581D2` | 320, 400, 800 | `TestS3kSszMechaSpawnHeadless`, `TestSszLaunchState`, `TestSszLaunchBackground` | partial | declared checkpoint and positioned boss hits; production results, first forced jump and neutral-input launch reach actual DEZ1 load | graph recreation and forward replay at four launch boundaries, including retained Plane B | source-backed; native parity not yet measured | clip 24: controller Hyper checkpoint clear, full 4800-frame capture through DEZ1 | Native Sonic 320/400/800, Sonic + Tails 320, Tails 400 and S1 donor 320 pass component handover. Extra-followers, exhausted slots, transition timeline isolation and native comparison remain open. |
| LOAD: `$B00` (DEZ) presentation after the request | DEZ campaign | — | — | blocked | blocked | blocked | blocked | blocked | Out of scope; record what the engine does after the request |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz{,2,3}SegmentTraceReplay`, `TestS3kTailsFullChainHpz{,2}SegmentTraceReplay` | `-Ptrace-segments` | — | — | — | — | not measured | — | Slice 10 records each frontier |

## Current cold-route closure (2026-09-24)

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
- The pseudo-starpost's other half — die after the bridge and respawn at `($140,$C6C)` — is still
  owed. Slice 4 drives a real death and reload, but at `$34:$03`, not at the bridge's write.
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
