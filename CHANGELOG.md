# Changelog

All notable changes to the OpenGGF project are documented in this file.

## Unreleased

### v0.6.prerelease (Current development snapshot)

The active 0.6 prerelease line is focused on S3K vertical-slice parity, trace-driven ROM accuracy, release hardening, and gameplay-scoped rewind reliability. Detailed per-frontier notes were moved out of this top-level changelog so it stays readable; see [docs/TRACE_FRONTIER_LOG.md](docs/TRACE_FRONTIER_LOG.md) for frame-by-frame trace evidence and [docs/changelog/v0.6-prerelease-detailed.md](docs/changelog/v0.6-prerelease-detailed.md) for the previous verbose merge ledger.

- **S1 SYZ3 is GREEN (f12490 -> end), S1's 9th green trace — three S1 boss-defeat/escape fixes resolve the post-boss camera-unlock tail:** (1) `Sonic1SYZBossInstance.defeatDeferralAppliesToThisBoss()` returns `true` so the defeat countdown's first decrement lands one frame after the killing hit — ROM `BSYZ_Defeated` sets `ob2ndRout`+`GenericTimer` then `rts`, and `BSYZ_ShipMain` re-reads `ob2ndRout` next frame (`docs/s1disasm/_incObj/75, 76 Boss - SYZ Main and Blocks.asm:70-74,154-160`); without it the escape's `addq.w #2,(v_limitright2)` camera scroll started a frame early (f12490 `camera_x` 0x2C02 vs 0x2C00). (2) `AbstractS1EggmanBossInstance.isPersistent()` returns `true` — the Eggman-ship bosses end with `jmp (DisplaySprite).l` and are never `out_of_range`-culled (they self-delete from the escape routine once `v_limitright2` reaches `boss_*_end`); the escaping ship flies 8px/frame and was culled mid-escape, freezing the right boundary at 0x2CA4 short of boss_syz_end 0x2D40 (f12575). (3) Removed the premature `setCurrentBossId(0)` from `Sonic1SYZBossInstance.updateDefeatWait` — ROM never clears `f_lockscreen` at defeat (only the Egg Prison does), so the strict right boundary stays active through the run to the capsule; clearing it early let the player overrun the wall (f12767 `x_speed` 0x531 vs 0). Object-local to S1 boss classes, no zone/route/frame carve-out. Full S1 sweep + S2 EHZ1 show zero regression (9 S1 greens GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/SBZ1/SYZ3; 10 known reds byte-identical: FZ f3907, LZ1 f5745, LZ2 f6418, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431).
- **S1 SYZ3 advances f11169 -> f12490 — SYZ boss block-drop "shake" reads the word timer's low byte (ROM objoff_3C/3D aliasing) so the boss-hit bounce fires on the right frame:** `Sonic1SYZBossInstance.updateDropHold`/`updateDropSettle` now derive the ±2 vertical shake offset from `(timer & 2)` / `(timer & 1)` instead of a separate static `justReturnedFlag`. In ROM the shake direction comes from `btst #1,PhaseTimer` / `btst #0,PhaseTimer`, where `PhaseTimer` (objoff_3D) is the LOW BYTE of the word timer `GenericTimer` (objoff_3C); the per-frame `subq.w #1,GenericTimer` overwrites it, so the shake alternates with the decrementing timer (`docs/s1disasm/_incObj/75, 76 Boss - SYZ Main and Blocks.asm:20-21,264-295`). The engine's static flag left the ship's collision Y stuck at obBossY+2 (0x0558) instead of oscillating, so when the rolling player fell onto the ship at f11169 the boss was 2px too low and the `React_BossHit` bounce (`neg/neg/asr/asr` of both velocities, `docs/s1disasm/_incObj/Sonic ReactToItem.asm:259-263`) fired one frame late (f11170). With the alternating shake, the ship sits at obBossY-2 (0x0554) at the React frame (end of f11168, timer=2, bit1 set), overlapping the player's hitbox so the bounce lands on f11169 (`x_speed` neg(-0xA9)>>1 = 0x0054, `y_speed` neg(0x4E0)>>1 = -0270). Object-local to the S1 SYZ boss (S1-only class), no shared/touch-response code touched, no zone/route/frame carve-out. Advances SYZ3 complete-run **f11169 -> f12490** (errors 94 -> 20; new root is a distinct boss-ESCAPE camera-expand `camera_x` divergence, boss defeated). ZERO regression: 8 S1 greens (GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/SBZ1) + S2 EHZ1 GREEN; all other S1 reds byte-identical (FZ f3907, LZ1 f5745, LZ2 f2394, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431).
- **S1 LZ2 advances f2394 -> f6418 — LZ Waterfalls (Obj 0x65) despawn off-screen-left via the exact ROM `out_of_range` window instead of a symmetric margin:** `Sonic1WaterfallObjectInstance.isPersistent()` now uses `isInRangeAt(getX())` (the ROM `out_of_range` macro: delete once the object's chunk-aligned X leaves `[camera-128, camera-128 + 0x280]`) instead of the symmetric `isOnScreenX(192)`. Every ROM `WFall` routine ends at `RememberState`, whose `out_of_range.w .offscreen` deletes the waterfall (and clears its respawn-block flag) once it scrolls off (`docs/s1disasm/_incObj/65 LZ Waterfalls.asm:52` -> `docs/s1disasm/_incObj/sub RememberState.asm:9` -> `docs/s1disasm/Macros.asm:273-290`). The old `isOnScreenX(192)` kept the object alive ~64px too far to the left: at LZ2 f2394 three waterfalls at x=0x358 (camera left 0x415) sat at the engine's left bound 0x355 and were retained, while ROM had already deleted them (their chunk-aligned 0x300 is left of the camera-128 chunk 0x380). Those three extra waterfalls held SST slots 35/39/43, so when the air-bubbles maker (Obj 0x64) ran `FindFreeObj` for a new bubble the engine returned slot 0x24 where ROM returned the now-free slot 0x23 (`obj_s23_slot` exp 0x23 act 0x24). Pinned by a throwaway full-slot occupancy probe vs the trace `slot_dump` aux: ROM and engine spawn the three waterfalls into the identical slots 35/39/43 at f1594 and agree through f1650, then ROM frees all three at f2288 (camera left reaches 0x415) while the engine kept them to f2394. Object-local to the S1 LZ Waterfall (S1-only class), no zone/route/frame carve-out. Advances LZ2 complete-run **f2394 -> f6418** (errors 1473 -> 1290; new root is a separate `obj_s44_slot` -1 offset). Zero-regression: 8 S1 greens (GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/SBZ1) + S2 EHZ1 GREEN; all other S1 reds byte-identical (FZ f3907, LZ1 f5745, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431, SYZ3 f11169); S1 Lz3 credits trace (LZ waterfalls) holds; `TestRewindCoverageGuard`/`TestNoDirectMapMutationsInGameplay`/`TestObjectServicesMigrationGuard`/`TestS3kAiz1SkipHeadless` pass.
- **S1 SYZ3 advances f10880 -> f11169 — SYZ3 boss blocks (Obj 0x76) persist off-screen like ROM so the two rightmost arena blocks are not culled:** `Sonic1BossBlockInstance.isPersistent()` now returns `true` for the solid/grabbed/breaking block (fragments stay non-persistent). ROM `BossBlock_Main` creates all 10 blocks at once in one FindFreeObj loop (x = `boss_syz_x+$10 .. +$130` = 0x2C10..0x2D30) while the camera is still left of the arena, and each solid block's routine (`BossBlock_Action` -> `BossBlock_Solid` -> `SolidObject` -> `DisplaySprite`) has **no** `out_of_range`/`DeleteObject` path, so the blocks persist regardless of screen position (`docs/s1disasm/_incObj/75, 76 Boss - SYZ Main and Blocks.asm:725-791`); only fragments delete off-screen (`BossBlock_Frag`: `tst.b obRender / bpl BossBlock_Delete`, :798-804). The engine spawned all 10 but its generic off-screen unload (`ObjectManager.unloadCounterBasedOutOfRange`) culled the two rightmost blocks (0x2D10/0x2D30, ~272px right of the camera at spawn time) and they never respawned (dynamically spawned, not layout objects). At SYZ3 f10880 the player walks off the terrain edge onto the boss-block floor; ROM keeps him standing on block slot 0x2B @0x2D10 (`status=08`, on-object) while the engine — with no block there — went airborne (`air` exp 0 act 1). Instrumentation confirmed the engine processed only the 8 leftmost blocks (0x2C10..0x2CF0). Object-local to the S1 SYZ3 boss block (S2/S3K unaffected), no zone/route/frame carve-out. Advances SYZ3 complete-run **f10880 -> f11169** (errors 112 -> 94; new root is a distinct boss-fight `x_speed`/`y_speed` bounce). ZERO regression: 8 S1 greens (GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/SBZ1) + S2 EHZ1 GREEN; S3K AizCompleteRun f1095/4309 byte-identical; all 10 other S1 reds byte-identical (FZ f3907, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431); `TestCollisionLogic`/`TestTerrainCollisionManager`/`TestS3kSlotCollisionSystem`/`TestS3kAiz1SkipHeadless` pass.
- **S1 LZ2 advances f1068 -> f2394 — Animals (Obj 0x28) defer the resolved routine's first-frame fall (ROM `Anml_Main` routine-0 init + `DisplaySprite`):** `Sonic1AnimalsObjectInstance.update()` now returns after `ensureInitialized()` on the first (routine-0) frame instead of falling straight through to the resolved routine's body. ROM `Anml_Main` (routine 0) sets `obRoutine` (`addq.b #2,obRoutine` for from-enemy animals, `obSubtype*2` for ending animals) and ends with `bra DisplaySprite` WITHOUT running the resolved routine that frame (`docs/s1disasm/_incObj/28, 29 Animals and Points.asm:130,177,183`); the fall (`Anml_ChkFloor`, routine 2) runs only from the next frame. The engine collapsed routine 0 into lazy init and then ran the routine-2 `ObjectFall` on the spawn frame, so a from-enemy LZ2 animal in SST slot 0x20 ran one frame ahead — it crossed the `BuildSprites` off-left bound (screenX <= -9) and self-deleted at f361 where ROM keeps it until f362, freeing slot 0x20 a frame early so the f361 Waterfall (Obj 0x65) + Burrobot (Obj 0x2D) took slot 0x20 instead of ROM slots 0x24/0x25 (a +5 slot-cadence cascade that surfaced at the trace's compared Bubbles-maker slot, f1068). Pinned by a BizHawk RAM probe of ROM slot `$D800` (obX/obRoutine/obRender): ROM lands routine 2->0xA at f330 (engine f329), the animal is 1px to the right at every frame through f360, its render bit-7 clears f361, and `DeleteObject` runs f362 (matching the trace `ObjectRemoved slot=32 type=0x28`); the engine camera X is byte-identical to the trace `camera_x` column and the off-left bound formula matches ROM, so the one-frame-early landing was the sole cause. Object-local to the S1 Animals object (S1-only class; ending-subtype animals also correctly defer their first-frame movement now — all 8 S1 credits traces hold). Advances LZ2 complete-run **f1068 -> f2394** (errors 1823 -> 1473; new root is a separate Bubbles-maker Obj 0x64 +1 slot offset). Zero-regression: 8 S1 greens (GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/SBZ1) + S2 EHZ1 GREEN; all other S1 reds byte-identical (FZ f3907, LZ1 f5745, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431, SYZ3 f10880); `TestRewindCoverageGuard`/`TestNoDirectMapMutationsInGameplay`/`TestObjectServicesMigrationGuard`/`TestS3kAiz1SkipHeadless` pass.
- **S1 SBZ1 GOES GREEN — Ball Hog animation first-step off-by-one (`AnimateSprite` cadence; SBZ1 f6082 -> PASS):** `Sonic1BallHogBadnikInstance` rolled its own count-up animation timer that held the first animation step for only 9 frames instead of 10, advancing the whole animation one frame early. ROM `AnimateSprite` uses a `subq.b #1,obTimeFrame; bpl` countdown from obTimeFrame=0, so every step (including the first) lasts speed+1=10 frames (`docs/s1disasm/_incObj/sub AnimateSprite.asm:17-23`). The early animation reached the cannonball-launch trigger frame (Ani_Hog index 20, value 1) on call 200 instead of ROM's 201, so the Ball Hog (Obj 0x1E) threw its Cannonball (Obj 0x20) one frame ahead of ROM (ROM spawn f6018), leaving it permanently 1px/1frame down its arc and hurting Sonic one frame early at f6082 (engine x_speed=-0200/air=1 where ROM was still grounded with rings intact). `updateAnimation()` now mirrors ROM `AnimateSprite` exactly (decrement-then-reload obTimeFrame, read-then-increment obAniFrame, afEnd wraps to index 0). Object-local to the Ball Hog (no zone/frame carve-out). **SBZ1 complete-run f6082 -> PASS (GREEN).** S1 complete-run greens now: GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1/**SBZ1** (8). Zero-regression: full S1 complete-run sweep + S2 EHZ1 -> the 7 prior greens held, SBZ3 (also a Ball Hog zone) held, SBZ1/SBZ2 credits demos held, S3K must-keeps (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`) green; remaining reds unchanged (FZ f3907, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431, SYZ3 f6549).
- **S1 SYZ3 advances f6549 -> f10880 — `FindFloor` retains the foot tile's angle when walking off a top-solid, zero-height-column tile into the air:** `GroundSensor.scanVertical`'s no-surface default now returns the FOOT tile's angle (with h/v flips) instead of the `FLAGGED_ANGLE` sentinel when that foot tile is solid (a non-zero collision block). This mirrors ROM `FindFloor`, which writes the tile's angle to the angle buffer at `.issolid` (`docs/s1disasm/_incObj/sub FindNearestTile & FindFloor & FindWall.asm:131`) BEFORE reading the height; a zero/overflow height then branches to `.isblank`/`.negfloor` (`:159,:175`) and extends one tile down without clearing the buffer, and `FindFloor2`'s blank path (`:199`) never overwrites it. The engine previously discarded a solid foot tile's angle on `metric==0`, falling to the flagged sentinel which `applyAngleFromSensor` cardinal-snapped — zeroing the airborne angle. At SYZ3 f6549 the player runs off a down-slope crest (tile 0x9B, angle 0x24, height 0 at the probed column; extension tile 0x9C not top-solid); ROM keeps angle 0x24 (decaying via `Sonic_JumpAngle`) and reports the same airborne state at the same byte-identical position, while the engine had snapped angle to 0x00 (the divergent `ground_mode`/`angle` fields are both derived from angle). Shared `physics/GroundSensor` fix, no zone/route carve-out. Advances SYZ3 complete-run **f6549 -> f10880** (errors 114 -> 112; new root is `air`). ZERO regression: 7 S1 greens (GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/MZ1) + S2 EHZ1 GREEN; S3K AizCompleteRun f1095/4309 byte-identical (stash-verified); all 11 other S1 reds byte-identical (FZ f3907, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ1 f6082, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431); `TestCollisionLogic`/`TestTerrainCollisionManager`/S3K HCZ/ICZ ground+floor collision tests pass.
- **S1 MZ1 GOES GREEN + SYZ3 advances — ring groups clear their respawn-block flag on spawn (`Ring_Main` `bclr`; S1 MZ1 f6222 -> PASS, SYZ3 f6358 -> f6549):** `Sonic1RingInstance` now models ROM `Ring_Main`'s `bclr #7,(a2)` — when a ring group object runs its main routine it clears its own respawn-table bit 7 (the ObjPosLoad respawn-block flag) for every uncollected ring (`docs/s1disasm/_incObj/25, 37 Rings.asm:91,99`). ObjPosLoad's REV01 `bset #7,2(a2,d2.w)` (`docs/s1disasm/_inc/ObjPosLoad.asm:269`) sets the bit when the group (re)spawns, and the ring group clears it again on execution so the cursor re-spawns the group when it re-scans the layout entry (e.g. a camera backtrack). The engine previously set the bit on ring spawn (`trySpawnCountered`) but never modelled the `bclr`, so a still-loaded or previously-loaded ring group kept bit 7 set and the backward (`OPL_MovedLeft`) ObjPosLoad scan **skipped** it, shifting the whole slot-allocation cascade (the S1 `v_objstate` sweep-counter slot-cadence family). Pinned by comparing the committed trace `v_objstate` aux against the engine `objState[]` at LZ2 f215: ROM kept bit 7 CLEAR for every live ring group (counter index 4/9/10/11/12) while the engine had them SET. Fully-collected groups keep bit 7 set (no `bclr`), matching ROM `Ring_SpawningDone`. Object-local to the S1 ring object (S2/S3K use separate ring systems — cross-game safe). **MZ1 complete-run f6222 -> PASS (GREEN):** the realigned ring slot cadence puts the Basaran (Obj 0x55) back in its ROM slot, so its slot-derived drop/fly gate fires on the ROM frame and the React bounce lands. **SYZ3 complete-run f6358 -> f6549** (errors 384 -> 114): the backward-pass ring (Obj 0x25 @0x186E) now respawns into slot 37 on the tf6086 backtrack, freeing the Button/FloatingBlock slot order. S1 complete-run greens now: GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2/**MZ1** (7). Zero frame-regression: full S1 sweep first-error frames byte-identical vs base except MZ1 (-> GREEN) and SYZ3 (f6358 -> f6549); LZ2 holds f1068 (internal slot-occupancy divergence advances f217 -> f361, the separate air-bubble-maker cadence); FZ f3907, LZ1 f5745, LZ3 f8499, MZ2 f2823, MZ3 f9917, SBZ1 f6082, SBZ2 f2323, SLZ1 f2872, SLZ3 f3249, SYZ1 f4431 unchanged; S2 EHZ1 GREEN; S3K AizTraceReplay f8941 / AizCompleteRun f1095 held; `TestSonic1RingInstance`/`TestSonic1RingPlacement`/`TestS1RingFlashGraphRewind`/`TestS1OffscreenYRingTouchSkip`/`TestRewindCoverageGuard`/`TestNoDirectMapMutationsInGameplay` pass (`TestS1Mz1SlotLayoutRegression` stays 8/15, a pre-existing known-red slot-cadence characterization unchanged by this fix).
- **Hurt player zeroes all velocity when landing on an angled ceiling (cross-game `Sonic_HurtStop`; S1 SYZ1 f4430 -> f4431):** `CollisionSystem.doCeilingCollision`'s angled-ceiling land branch now zeroes `y_vel`/`x_vel`/`g_speed` (inertia) when the landing player was in the hurt state, instead of setting `g_speed = ±y_vel`. ROM `Sonic_FloorUp.angledceiling` (`docs/s1disasm/_incObj/01 Sonic.asm:1720-1731`; S2 `loc_1B02C` `s2.asm:38048-38057`; S3K `loc_120EA` `sonic3k.asm:24258-24264`) clears the in-air bit (`Sonic_ResetOnFloor`) and sets inertia from `y_vel`, but the hurt routine then re-runs `Sonic_HurtStop` (`01 Sonic.asm:1918-1923`; S2 `s2.asm:38216-38221`; S3K `sub_12318` `sonic3k.asm:24492-24496`): after `Sonic_Floor`/`DoLevelCollision` returns it re-checks `Status_InAir`, and because the angled-ceiling land cleared it, it overwrites `y_vel`/`x_vel`/`inertia` with 0 before reverting hurt->control. The engine's `resetWallCeilingLandingState` already clears the hurt flag (via `setAir(false)`, which mirrors the routine-revert + flash-time), but the velocity zeroing was missing — so a hurt player flying up into an angled ceiling kept `g_speed = ±y_vel` and a non-zero `y_vel`. At SYZ1 f4430 a hurt-recoil player (routine 4, `obVelY=0xFC90`) landed on an angled ceiling and the engine left `y_speed=-0x370` (the converted knockback inertia) where ROM had `y_speed=0x0000`. Pinned by a BizHawk M68K PC-execute probe capturing the mid-frame `Sonic_FloorUp.angledceiling` -> `Sonic_HurtStop` path. **Identical in all three games — core hurt recovery, not a per-game divergence (verified S1/S2/S3K disasm), so no `PhysicsFeatureSet` gate.** Advances S1 SYZ1 complete-run **f4430 -> f4431** (errors 554 -> 406; the new f4431 root is the separate slot-0x54 ridden-object frontier). Shared collision-path change, but scoped to the hurt + angled-ceiling-land case only (normal landings, the floor-land handlers, and the wall/flat-ceiling quadrants are untouched). Zero-regression: full S1 sweep first-error frames byte-identical vs base except SYZ1 (f4430 -> f4431); GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2 GREEN held (6); S2 EHZ1 GREEN; S3K AizTraceReplay f8941 / AizCompleteRun f1095 held; `TestCollisionSystemAirLanding` (7), `TestCollisionModel` (10), `TestSonic1FZBossDamageFlash` pass.
- **S1 Swinging Platform (Obj 0x15) rejects the exact-touch (d0=0) top-solid landing (PlatformObject-family flag-completeness audit):** `Sonic1SwingingPlatformObjectInstance.rejectsZeroDistanceTopSolidLanding()` now returns `true`. ROM `Swing_SetSolid` (routine 2) lands a new rider through `Swing_Solid`, which ends `move.w obY(a0),d0 / sub.w d3,d0 / bra Plat_NoXCheck_AltY` (`docs/s1disasm/_incObj/sub PlatformObject.asm:165-179`). `Plat_NoXCheck_AltY` is the shared PlatformObject Y land band — `sub.w d1,d0 / bhi Plat_Exit` (reject d0>0) then `cmpi.w #-16,d0 / blo Plat_Exit` (`docs/s1disasm/_incObj/sub PlatformObject.asm:36-52`); `blo` is unsigned lower-than `0xFFF0` so `d0=0x0000 <u 0xFFF0` is rejected (standable band `[-16,-1]`, strict penetration), exactly the `PlatformObject_ChkYRange` contract and NOT the `SolidObject_Landed` `blo #$10` band that accepts `d3=0`. The engine's shared top-solid default accepts `detectionDistY=0`, so a fast-falling player whose feet reach exactly the swing-platform surface seated one frame early vs ROM. The override is honoured even though `usesPlatformObjectLandingSnap()=false` makes the continued-ride `allowsZeroDistanceTopSolidLanding()` permissive — the resolver ORs in this explicit reject (`ObjectSolidContactController` lines ~3000 and ~3344). This was the only gap found by a full 13-object S1 `SolidObjectProvider`-flag-vs-ROM-routine audit (`usesCollisionHalfWidthForTopLanding` / `rejectsZeroDistanceTopSolidLanding` / `getTopLandingSnapAdjustment` / `carriesAirborneRiderAfterExitPlatform` / `usesPreUpdatePositionForSolidContact`); every other ridable object (Obj 18/52/56/59/5A/63/6C/1A/53/5E/11/33) was already ROM-correct, and the FloatingBlock Obj 0x56 is a full `SolidObject` (so its `false` for the PlatformObject flags is correct — the SYZ3 f6358 frontier is a wall-pushback 1px-integer / RAM-gated residual with matching `x_sub`, NOT a flag gap). Object-local to Obj 0x15; no shared physics/controller code changed. Zero-regression: full S1 sweep first-error frames byte-identical vs base; GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/SLZ2 GREEN held (6); S2 EHZ1 GREEN; `TestSolidRoutineProfiles`/`TestSolidObjectManager`/`TestS1JumpFromElevator`/`TestS1BridgeSolidParity` green; S3K unaffected (S1-object-only change). Correctness fix — closes a real ROM divergence (no current red frontier touches Obj 0x15 before its existing frontier). Companion to the SLZ Elevator (Obj 0x59) and SLZ Circling Platform (Obj 0x5A) entries below.
- **S1 SLZ GOES GREEN — SLZ Elevator (Obj 0x59) rejects the exact-touch (d0=0) top-solid landing (SLZ2 f3927 -> PASS):** `Sonic1ElevatorObjectInstance.rejectsZeroDistanceTopSolidLanding()` now returns `true`. ROM `Elev_Platform` (routine 2) lands the player through `PlatformObject` (`docs/s1disasm/_incObj/59 SLZ Elevators.asm:77-80` -> `docs/s1disasm/_incObj/sub PlatformObject.asm:36-52`), whose Y land band is gated by an UNSIGNED `cmpi.w #-16,d0 / blo Plat_Exit` (platform top = obY-8) after a signed `bhi Plat_Exit` on d0>0 — rejecting the exact-touch case d0=0 (`0x0000 <u 0xFFF0`): the standable band is d0 in [-16,-1] (strict penetration, the player's bottom edge must be at least 1px below the surface). The engine default accepts `detectionDistY=0`, so a fast-falling player whose bottom edge reaches exactly the surface seated one frame early. SLZ2 f3927: a rolling-jump player falling ~18px/frame past the elevator at (0x1B80,0x0368) hit d0=0 at f3927 (bottom edge 0x0360 == surface 0x0368-8=0x0360) — ROM rejects and keeps falling (`air=1`, `y_speed=0x11E0`, preserved roll `g_speed=0xFC0B`), but the engine landed him (`air 1->0`, `g_speed FC0B->FE8C`, `y_speed->0`). The elevator was the only PlatformObject-family S1 object missing this override (Obj 18/52/53/5A/63/6C/1A already have it); its top-landing detection already uses `airHalfHeight=8` (= obY-8) so no `getTopLandingSnapAdjustment` is needed. **SLZ2 complete-run f3927 -> PASS (GREEN).** Object-local to Obj 0x59 (SLZ-only); no shared physics/controller code changed. Zero-regression: full S1 sweep first-error frames byte-identical vs base except SLZ2 (now GREEN); GHZ1/GHZ2/GHZ3/SYZ2/SBZ3 GREEN held; S2 EHZ1 GREEN; S3K unaffected (S1-object-only change). S1 complete-run greens now: GHZ1/GHZ2/GHZ3/SYZ2/SBZ3/**SLZ2** (6).
- **S1 SLZ Circling Platform (Obj 0x5A) applies the unconditional `MvSonicOnPtfm2` re-seat on the ride-exit frame (SLZ2 f3353 -> f3927):** `Sonic1CirclingPlatformObjectInstance.carriesAirborneRiderAfterExitPlatform()` now returns `true`. ROM `Circ_Action` (routine 4, `docs/s1disasm/_incObj/5A SLZ Circling Platform.asm:38-45`) runs `ExitPlatform` -> `Circ_Types` (move platform) -> **unconditionally** `MvSonicOnPtfm2`, which does NOT test the on-object bit and seats the rider to `platformY-9-obHeight` using the platform's POST-move position (`docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`). When the descending circling platform's X-edge slides past the rider on the exit frame, `ExitPlatform` clears the on-object bit but the rider still gets one final post-move seat. The engine dropped the ride on X-exit without that seat, leaving the rider 1px high (SLZ2 f3353: platformY post-move 0x013C -> ROM centre 0x013C-9-0x13=0x0120; the engine kept the pre-exit 0x011F, re-converging at f3356). Structurally identical to Obj18 Plat_Action2 / Obj52 MBlock_StandOn / Obj59 Elev_Action, which already opt in; the circling platform was the missing family member. The exit-frame X-carry + Y-seat is already implemented in `ObjectSolidContactController.processInlineRidingObject`'s exit branch — the override just enables it for Obj 0x5A. **This DEFINITIVELY FALSIFIES the prior "SLZ2 f3353 = oscillation phase-init, BizHawk-gated" assessment:** a throwaway diagnostic confirmed the engine's `OscillationManager` osc8 (`v_oscillate+$22`) and osc9 (`v_oscillate+$26`, the platform Y driver) byte-match the v3.11 trace at f3353 (`1E68`/`0C88`), and the engine platform X/Y also byte-match (platY 0x013C) — oscillation and platform geometry are correct; the 1px was purely the ride-exit seat. The gfc-plateau osc-skip gating is correct as-is (both ROM and engine skip exactly 10 osc ticks before f3353; the one-frame off-by-one between ROM's osc-freeze and BizHawk's `islagged()` flag is phase-neutral). New frontier f3927 is an unrelated `g_speed` divergence. Object-local to Obj 0x5A; no shared physics/controller code changed. Zero-regression: full S1 sweep first-error frames byte-identical vs base except SLZ2 (f3353 -> f3927, errors 131 -> 45); GHZ1/GHZ2/GHZ3/SYZ2/SBZ3 GREEN held; S2 EHZ1 GREEN; S3K unaffected (S1-object-only change).
- **S1 recorder v3.10 adds a `v_oscillate` per-frame aux event (comparison-only; unblocks the osc-phase decodes):** `s1_complete_run_recorder.lua` now emits one new per-frame aux event, `v_oscillate`, carrying the FULL global oscillation state as a compact hex string — the 2-byte `v_oscillate` direction bitfield at `0xFFFE5E` plus the `$40`-byte oscillating-values array (`v_timingvariables`) immediately after at `0xFFFE60` (`$42` bytes total). Addresses verified from `docs/s1disasm/sonic.lst` instruction operands (`lea (v_oscillate).w,a1` -> `43F8 FE5E`; `move.b (v_oscillate+2).w,d0` -> `1038 FE60`) and `docs/s1disasm/_Variables.asm:400-403`. This unblocks the osc-phase cluster (SLZ2 f3353 circling-platform 1px): the comparator can read the exact oscillator-8 byte (`v_oscillate+$22` = `0xFFFE80` = values-array offset `$20`, since per-object oscillators index the array at `N*4`) per frame to disambiguate an osc-phase-seed offset (fixable) from the ride-exit seat (shared-riding wall, like SLZ1). CSV schema unchanged; metadata `lua_script_version` -> "3.10" and `aux_schema_extras` gains `v_oscillate_per_frame`. Java side: `TraceEvent.VOscillate(frame, byte[])` record + `v_oscillate` parser case + `TraceData.vOscillateForFrame(int)` + `TraceMetadata.hasVOscillate()` + DivergenceReport diagnostics rendering (`TraceEventFormatter` summarises it as `vOscillate bits=.. osc=[N=hh ..]` showing the per-oscillator value-byte phase) — all legacy-absent-safe so v3.9 traces parse unchanged. Strictly comparison-only diagnostic context — replay never hydrates engine oscillation state from these bytes. Validated: `lupa` compile of the recorder (115 top-level locals, under the 200 main-chunk limit), `mvn test-compile`, `TestTraceDataParsing` (42)/`TestTraceEventFormatting` (16)/`TestTraceBinder` (59) green, and the legacy SLZ2 complete-run trace still parses (f3353/131 errors, no parser crash on the new event).
- **S1 recorder v3.9 adds `objoff_34`/`objoff_36`/`objoff_38` to `object_near` (comparison-only; unblocks the object-COUNTER-phase decodes):** `s1_complete_run_recorder.lua` now emits three more per-object WORD fields on the existing `object_near` aux event for every object already in the player proximity window — `objoff_34`/`objoff_36`/`objoff_38` (per-object counter/timer/sub-state words at offsets +0x34/+0x36/+0x38, `read_u16_be`). These pin object-counter-phase frontiers where the seat/spawn reaches a counter value one frame before ROM — the GHZ3 1-frame-counter-defer shape, now visible per-object: SLZ Staircase Obj5B ride counter (SLZ1 f2872), geyser-maker timer (MZ2 f2819), platform oscillation phase, etc. CSV schema is unchanged; the fields ride the existing proximity gate so they only cost bytes for nearby objects. (NB +0x38 here is the object-frame objoff_38 word, distinct from the player-only stick-convex byte.) metadata `lua_script_version` -> "3.9" and `aux_schema_extras` gains `object_near_objoff_34_36_38`. Java side: `TraceEvent.ObjectNear` gains `objoff34`/`objoff36`/`objoff38`, the parser defaults all three to `""` (legacy v3.8 traces parse unchanged), and `TraceEventFormatter` renders them as `o34=`/`o36=`/`o38=` when present. Strictly comparison-only diagnostic context — replay never hydrates engine object state from these values. Validated: `lupa` compile of the recorder (112 top-level locals, under the 200 main-chunk limit), `mvn test-compile`, `TestTraceDataParsing` (42)/`TestTraceEventFormatting` (16)/`TestTraceBinder` (59) green, and the legacy SLZ1 complete-run trace still parses (f2872/163 errors, no parser crash on the new fields).
- **S1 moving spikes no longer drag a standing rider horizontally (MZ1 f4230 -> f6222, +1992):** `Sonic1SpikeObjectInstance.carriesRiderOnHorizontalMove()` now returns `false`. The ROM Spikes object reaches `MvSonicOnPtfm` through the shared `SolidObject` standing branch (`docs/s1disasm/_incObj/sub SolidObject.asm:46-55`), which carries the rider by the X-delta between the caller-supplied carry-reference `d4` and the object's current `obX`. But the Spikes caller runs `bsr.w Spikes_Move` (which updates `obX` to the new position) *before* `move.w obX(a0),d4` (`docs/s1disasm/_incObj/36 Spikes.asm:52,96`), so it passes the *post-move* `obX` as the carry reference and `MvSonicOnPtfm`'s `sub.w obX(a0),d2` computes a **zero** delta (`docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:38-39`). A horizontally-moving spike (subtype `$x2`) therefore slides out from under a standing player who stays put and then walks off the edge. The engine's generic continued-ride carry (default `carriesRiderOnHorizontalMove==true`) was instead dragging the player +8px/frame with the moving spike at MZ1 f4230 (engine x climbed from 0x0B3D while ROM held 0x0B35, then ROM dropped off and fell at f4234). This is an object-wide property of Spikes (every Spikes caller passes the post-move `obX`), not a zone carve-out. New frontier f6222 is an unrelated airborne `y_speed` jump/bounce near MZ Batbrains/Chained Stompers. Zero regression: full S1 sweep first-error frames byte-identical vs base except MZ1 (f4230 -> f6222); GHZ1/GHZ2/GHZ3 GREEN held; S2 EHZ1 GREEN; S3K AizTraceReplay f8941 / AizCompleteRun f1095 held; `TestCamera` + `TestRewindCoverageGuard` pass.

- **S1 GHZ3 GOES GREEN -- Egg Prison switch releases the player into the air and stops being solid after firing (GHZ3 f8838 -> PASS):** `Sonic1EggPrisonButtonObjectInstance.onSolidContact()` now mirrors ROM `Pri_Switch` (`docs/s1disasm/_incObj/3E Prison Capsule.asm:94,101-102`): on the switch-trigger frame the capsule does `bclr #3,(v_player+obStatus).w` (clear `Status_OnObj`) + `bset #1,(v_player+obStatus).w` (set `Status_InAir`), so the engine now calls `setOnObject(false)` + `setAir(true)` to drop the player off the depressed switch under gravity while controls stay locked to `btnR`. ROM `Pri_Switch` also does `move.b #$A,obRoutine(a0)`, leaving routine 4 (the only routine that calls `SolidObject`) for `Pri_Explosion` (`$A`), which never re-asserts solidity; the engine's button is a separate object that stayed solid forever and re-seated the just-released player onto the depressed switch each frame (`air=0`, `Status_OnObj`, `y_speed=0`) where ROM was airborne and falling. `isSolidFor()` now returns `!triggered` so the button is solid through the trigger frame only. Without these the engine kept the player grounded/riding (camera_y 1px high at f8838, then a held y_speed=0). Object-local to the S1 Egg Prison Button (Obj 0x3E, end-of-act). Zero regression: full S1 sweep first-error frames byte-identical vs base (GHZ1/GHZ2 GREEN held, all other zones hold at their documented frontiers, FZ f3907 identical); S2 EHZ1 identical to base; S3K AizTraceReplay f8941 / AizCompleteRun f1095 held; `TestRewindCoverageGuard` passes.
- **S1 GHZ boss defeat routine deferred one frame (read-once), fixing the escape camera-scroll phase (GHZ3 f8569 -> f8838):** `Sonic1GHZBossInstance.defeatDeferralAppliesToThisBoss()` now returns `true`. ROM's `BGHZ_Defeated` (reached from `BGHZ_ShipUpdate` when the killing hit sets `obStatus` bit 7) does `move.b #8,ob2ndRout(a0)` + `move.w #$B3,BGHZ_BossGenericTimer(a0)` then `rts` without falling through to `BGHZ_Explode`, and `BGHZ_ShipMain` re-reads `ob2ndRout` fresh at the top next frame, so the Explode routine and its first `subq.w #1,BGHZ_BossGenericTimer` are deferred one frame (`docs/s1disasm/_incObj/3D, 48 Boss - GHZ Main and Wrecking Ball.asm:67-69,140-145,224`). The engine selects the defeat routine during the touch-response pass that precedes the boss's own `update()`, so without the deferral `updateDefeatWait()` decremented the `$B3` timer on the routine-change frame, running the explode -> recover -> escape cycle one frame ahead of ROM. The slip surfaced at `BGHZ_Escape` (ob2ndRout `$0C`), whose `addq.w #2,(v_limitright2)` ran a frame early (GHZ3 `camera_x` 0x2962 vs ROM 0x2960 at f8569). Pinned with the v3.8 `object_near` `routine2`/`objoff_3c` aux (ROM boss holds the generic timer at `$B3` for exactly one frame before the first decrement, and a boss-state eng-vs-trace frame-offset diff showed +10560 through combat dropping to +10559 only at the defeat transition). Object-local to the GHZ boss (Obj 0x3D, GHZ3 only); the shared `AbstractS1EggmanBossInstance` base is unchanged. New frontier f8838 is the unrelated post-boss Egg Prison Button (Obj 0x3E) landing. Zero regression: full S1 sweep byte-identical vs base except GHZ3 (f8569 -> f8838); GHZ1/GHZ2 GREEN held; S2 EHZ1 GREEN; S3K AizTraceReplay f8941 / AizCompleteRun f1095 held; `TestRewindCoverageGuard` passes.
- **S1 recorder v3.8 adds `ob2ndRout` + `objoff_3C` to `object_near` (comparison-only; unblocks the GHZ3 boss-slip + FZ cylinder-coupling decodes):** `s1_complete_run_recorder.lua` now emits two extra per-object fields on the existing `object_near` aux event for every object already in the player proximity window — `routine2` (ob2ndRout, object offset +0x25; pins boss post-defeat phase transitions the primary routine byte hides, e.g. GHZ boss Obj3D entering ESCAPE ~1 frame early at GHZ3 f8569) and `objoff_3c` (objoff_3C at +0x3C, read as an unsigned 32-bit big-endian word; the generic timer / 32-bit sub-pixel accumulator — BGHZ_BossGenericTimer and the FZ cylinder Obj84 rise accumulator that seeds the player x_sub at FZ f3901). CSV schema is unchanged; the fields ride the existing proximity gate so they only cost bytes for nearby objects. metadata `lua_script_version` -> "3.8" and `aux_schema_extras` gains `object_near_routine2_objoff3c`. Java side: `TraceEvent.ObjectNear` gains `routine2`/`objoff3c`, the parser defaults both to `""` (legacy v3.7 traces parse unchanged), and `TraceEventFormatter` renders them as `r2=`/`o3c=` when present. Strictly comparison-only diagnostic context — replay never hydrates engine object state from these values. Validated: `lupa` compile of the recorder, `mvn test-compile`, `TestTraceDataParsing` (42)/`TestTraceEventFormatting` (16)/`TestTraceBinder` (59) green, and the legacy GHZ3 complete-run trace still parses (f8569/15 errors, no parser crash on the new fields).
- **Camera pipeline runs in ROM order — player-follow scroll BEFORE the zone event handler, boundary easing AFTER (S1 MZ1 f2101 -> f4230, GHZ3 f8021 -> f8569, FZ f1724 -> f3907):** `LevelFrameStep.execute` (and the `GameLoop` ending-demo path) now run `camera.updatePosition()` (ROM `ScrollHoriz` + `ScrollVertical`: camera move + clamp to the prior-frame bottom boundary) BEFORE the dynamic level-event handler, then run `camera.updateBoundaryEasing()` (the ROM `DynamicLevelEvents` boundary tail) AFTER it — matching ROM `DeformLayers (REV01).asm:16-18` (`ScrollHoriz`/`ScrollVertical` run before `DynamicLevelEvents`) and `DynamicLevelEvents.asm:5-49` (the zone handler runs first, then the boundary easing reads the POST-scroll camera + previous-frame `v_limitbtm2` to pick the +2 vs airborne +8 step). The engine previously ran the event handler + boundary easing BEFORE the camera move, which applied the airborne +8 bottom-boundary acceleration to the same frame's camera one frame early (MZ1 f2101 `camera_y` 0x02EA vs ROM 0x02E8) and fed the SBZ/FZ camera-X gates + left-boundary lock a one-frame-stale pre-scroll camera X. The `Camera.cameraClampMaxY` one-frame-lag hack (a workaround for the inverted order) is removed; the clamp now reads `maxY` directly because the prior-frame boundary is already in `maxY` when `updatePosition()` runs. Coupled object-side camera-ordering compensations were unwound to read the live post-scroll camera: the S1 FZ boss (`Sonic1FZBossInstance.updateWait`) drops its `previousFrameCamX` defer and reads `camera().getX()` directly (the boss executes in object-exec, before the camera step, so `getX()` is already the prior-frame post-scroll X ROM's `ExecuteObjects`-time read sees), and the S3K AIZ event handler (`Sonic3kAIZEvents`) replaces its `previewNextX()` end-of-frame predictions with `getX()` (the post-scroll camera under the new order) and its `AIZ2BGE_WaitFire` release now falls through to the `$310` check on the frame `Events_bg+$00` is set, matching ROM's same-frame fall-through (`sonic3k.asm:105076-105084`). The FZ bottom-cylinder extension boost is corrected to ROM `cmpi.w #-$10 / bge` semantics (`< -0x10`, not `<= -0x10`; `_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:793-796`), which the now-ROM-correct cadence ordering lets land cleanly. S1 + cross-game results: S1 MZ1 f2101->f4230 (+2129), GHZ3 f8021->f8569, FZ f1724->f3907 (+2183), SBZ2 f2224->f2323 (bonus); GHZ1/GHZ2/SYZ2/SBZ3 GREEN held; every other S1 complete-run first-error frame byte-identical (LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ2 f2823, MZ3 f9917, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SBZ1 f6082, SYZ1 f4430, SYZ3 f6358); S2 EHZ1 GREEN; S3K AIZ complete-run f1095 unchanged; `TestCamera` (26), `TestCameraRewindSnapshot` (3), `TestLookScrollDelay` (5), `TestRewindCoverageGuard` all pass. S3K `TestS3kAizTraceReplay` holds its f8941 baseline and `TestS3kAizCompleteRunTraceReplay` stays at f1095 — the reorder briefly exposed a pre-existing AIZ fire-transition release-timing approximation (f8941 -> f5544) that is fixed in the same branch by the AIZ fake-fire redraw-budget change below.
- **S3K AIZ fake-fire `Camera_max_X_pos` release gated on the ROM redraw frame budget (holds AizTraceReplay at f8941 under the camera reorder):** the AIZ1->AIZ2 fake-fire transition (`Sonic3kAIZEvents`) now releases the post-reload camera-X lock on the ROM-measured `AIZ2BGE_FireRedraw` -> `AIZ2BGE_WaitFire` plane-redraw budget (`AIZ2_FIRE_REDRAW_FRAMES=8` + `AIZ2_WAIT_FIRE_REDRAW_FRAMES=38` = reload+46 gameplay ticks) instead of a fixed `Camera_Y_pos_BG_copy >= $310` threshold. ROM releases `Camera_max_X_pos=$6000` when the AIZ2 background plane redraw COMPLETES (`Draw_PlaneVertBottomUp` `bmi`), not when the fire BG-copy crosses `$310` — the continuous `AIZ1_FireRise` ramp passes `$310` well before the reload (`docs/skdisasm/sonic3k.asm:105036` AIZ2BGE_FireRedraw, `:105054`/`:105084`/`:105095` AIZ2BGE_WaitFire; `:104727` AIZ1BGE_Finish; `docs/skdisasm/s3.asm:70383` AIZ1_FireRise). Verified against a fresh BizHawk regen via the new comparison-only `aiz_fire_transition` aux: reload at trace f5496, redraw rtn `$00`->`$04` at f5504 (8 ticks), rtn `$04`->`$08` + maxX release at f5542 (38 ticks), ROM lag frame f5543, camera follows ($10->$28) at f5544. The `AIZ1_FireRise` ramp model is unchanged (now purely the cosmetic fire-curtain position) and the `act2WaitFireDrawActive` same-frame fall-through is preserved; the release write stays in the level-event handler step (consumed by the next frame's scroll, ROM `ScreenEvents`-after-`MoveCameraX`) with no camera re-commit. Adds the `aiz_fire_transition` per-frame aux to `s3k_trace_recorder.lua` (v6.27-s3k) / `s3k_complete_run_recorder.lua` (v6.28) plus the Java parser/report plumbing (`TraceEvent.AizFireTransition`, `TraceData.aizFireTransitionForFrame`, `TraceMetadata.hasPerFrameAizFireTransition`, DivergenceReport/TraceEventFormatter) — comparison-only diagnostics, never written back to engine state; CSV schema unchanged, legacy lean trace still parses. No committed trace change (the fix advances against the lean committed trace). Zero-regression: S3K `TestS3kAizTraceReplay` f8941 (fire-reveal camera-lock subtest passes), `TestS3kAizCompleteRunTraceReplay` f1095, `TestS3kAiz1SkipHeadless` / `TestSonic3kLevelLoading` / `TestSonic3kBootstrapResolver` / `TestSonic3kDecodingUtils` pass; all S1 frontiers above intact.
- **S1 MZ Chained Stomper ($31) reserves 2 child SST slots (not 3) when its spikes are collision-less (subtype upper nybble == $20):** `Sonic1ChainedStomperObjectInstance.getReservedChildSlotCount()`/the constructor's `allocateChildSlotsAfter` count now return `spikesHaveCollision ? 3 : 2` instead of an unconditional 3. ROM `CStom_Main`'s `CStom_Loop` runs with `d1=3` (4 iterations: main block into its own slot + 3 `FindNextFreeObj` children = spike/chain/ceiling), but the spike iteration does `cmpi.b #1,obFrame(a1) / subq.w #1,d1` and, for a `$20`-subtype stomper, `cmpi.w #$20,d0 / beq.s CStom_MakeStomper` — re-running the body **without** a fresh `FindNextFreeObj`, so the collision-less spike consumes no separate SST slot (`31 MZ Chained Stompers.asm`). MZ3's opening pair of stompers are `$20`-subtype, so the engine was reserving 6 child slots where the ROM uses 4; that one-extra-slot-per-stomper over-reservation was the first slot-occupancy divergence in the MZ3 complete-run (engine reserved slots 86/87 at f115 that the ROM leaves empty), seeding the downstream `FindFreeObj` slot drift the whole act inherits. Derived from the existing `spikesHaveCollision` field (no new rewind-captured scalar; coverage guard green). Zero-regression, MZ-only scope: MZ3 complete-run 644→641 errors and the first slot-occupancy divergence moves f115→f953; MZ1 (f2101) and MZ2 (f2823) byte-identical; S2/S3K unaffected. The headline MZ3 frontier f9917 does **not** advance — the residual slot cascade now flows through the Caterkiller ($78) multi-segment materialization timeline at f953 (BizHawk-gated, see frontier log).
- **S1 SBZ Teleporter capture preserves the player's sub-pixel fraction (subpixel-push audit companion):** `Sonic1TeleporterObjectInstance.capturePlayer` now snaps the captured player to the teleporter's centre with `setCentreXPreserveSubpixel`/`setCentreYPreserveSubpixel` instead of `setCentreX`/`setCentreY` (which zero `x_sub`/`y_sub`). ROM `72 SBZ Teleporter.asm:73-74` writes `move.w obX(a0),obX(a1)` / `move.w obY(a0),obY(a1)` — the player's `x_pos`/`y_pos` PIXEL words only, leaving the sub-pixel fractions untouched. Found by a systematic audit (originating from the conveyor fix below) of every S1 object→player position write against the disassembly: a `-Dsubpxaudit` probe in `AbstractSprite.setCentreX/Y` flagged the call sites that zero a non-zero player sub-pixel; the teleporter capture was confirmed a genuine ROM-preserves bug, while the level-side-boundary clamp was confirmed ROM-faithful (ROM `Boundary_Sides` explicitly `move.w #0,obSubpixelX`, `01 Sonic.asm:1100`, so its `setCentreX` is correct and left as-is). Latent correctness fix; zero-regression (SBZ/LZ complete-runs byte-identical, full keep-green) — it does not by itself advance a frontier (the capture runs controls-locked) but removes a real sub-pixel-discard. The audit pattern is catalogued as `s1-implement-object/rom-pitfalls.md` P15 (+ S2 P53 / S3K P24).
- **S1 Conveyor Belt preserves the rider's sub-pixel X fraction when pushing (SBZ2 f2224 -> f2323):** `Sonic1ConveyorBeltObjectInstance.moveSonic` now pushes the rider with `player.shiftX(convSpeed)` (an integer pixel add that leaves `x_sub` intact) instead of `setCentreX(getCentreX() + convSpeed)`, which zeroed `x_sub`. ROM `add.w conv_speed(a0),obX(a1)` adds the conveyor speed to the player's `x_pos` PIXEL word only — the sub-pixel fraction is untouched. The engine's `setCentreX()` discards `x_sub`, so the player lost up to ~1px of accumulated fraction on every frame he rode a conveyor, drifting progressively behind ROM. In SBZ2 the rider's `x_sub` 0x2800 was zeroed at f2168, seeding a constant 0x4C00 (~0.3px) offset that surfaced as the f2224 first-error. Advances the S1 SBZ2 complete-run frontier **f2224 -> f2323** (errors 625 -> 595; the new f2323 root is a separate 2px `x` divergence). Object-local to the S1 conveyor. This corrects the "subpixel needs BizHawk x_sub" assessment on this frontier: the ROM sub-pixel was already present in physics.csv columns 12-13 (since CSV v2.0); a local probe comparing `getXSubpixelRaw()` against the trace `x_sub` showed the engine byte-exact until the conveyor frame, and a `setCentreX` caller dump pinned the conveyor. Cross-zone (the S1 conveyor appears in SBZ): regression-free — SBZ1 f6082 (the other conveyor zone) and every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, MZ1 f2101, MZ2 f2823, MZ3 f9917, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and 61 conveyor/physics unit tests + `TestRewindCoverageGuard` pass.
- **S1 Moving Block (Obj 0x52) rejects the exact-touch (d0=0) top-solid landing (MZ3 f8973 -> f9917):** `Sonic1MovingBlockObjectInstance` now overrides `rejectsZeroDistanceTopSolidLanding()=true`. ROM `MBlock_Platform` routes the landing through `PlatformObject -> Plat_NoXCheck_AltY`, whose land band is gated by an UNSIGNED `cmpi.w #-16,d0 / blo Plat_Exit` (`docs/s1disasm/_incObj/sub PlatformObject.asm:51-52`), which rejects the exact-touch case d0=0 (`0x0000 <u 0xFFF0`): the standable band is d0 in [-16,-1] (strict penetration — the feet must be at least 1px below the surface). The engine default accepts d0=0 (S3K `SolidObjectTop_1P` semantics), so a player whose feet landed exactly on the surface seated one frame early. In MZ3 a player falling-right onto the moving block (Obj 0x52 @0x13CF; feet 0x07B0 == surface 0x07B0, d0=0) grounded at f8973 (y 0x079C, g_speed 0x200, air 0) while ROM stays airborne (y_speed 0x1B0) and lands at f8974 (d0<=-1). The MovingBlock was the only PlatformObject-family object missing this override (Obj 18/53/5A/63/6C/1A already had it). Advances the S1 MZ3 complete-run frontier **f8973 -> f9917** (errors 804 -> 644; the new f9917 root is a separate `y_speed` divergence). Object-local to Obj 0x52. Cross-zone (MovingBlocks appear in MZ/LZ/SBZ): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, MZ1 f2101, MZ2 f2823, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SBZ1 f6082, SBZ2 f2224, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and 47 MovingBlock/physics unit tests + `TestRewindCoverageGuard` pass.
- **S1 Moving Block (Obj 0x52) uses its full `obActWid` landing width (MZ3 f8646 -> f8973):** `Sonic1MovingBlockObjectInstance` now overrides `usesCollisionHalfWidthForTopLanding()=true`. ROM `MBlock_Platform` (routine 2) passes `move.b obActWid(a0),d1` directly into `PlatformObject`, whose X-range landing check uses that d1 as the full landing half-width (land range `[objX-obActWid, objX+obActWid)`; `docs/s1disasm/_incObj/52 Moving Blocks.asm:57-61`, `sub PlatformObject.asm:27-34`). obActWid (0x10) is already the standable half-width, so it must NOT receive the generic SolidObjectFull `-$B` narrowing, which shrank the landing width to `obActWid-0xB=0x5`. In MZ3 a falling rolling player at relX 0xC from the rightward-moving block (Obj 0x52 @0x1289) was outside the narrowed 0x5 landing width, so `resolveContact` returned no contact and he fell through (stayed airborne, g_speed 0x310 vs ROM grounded 0). With the full obActWid landing width the landing is detected, the player seats and stays grounded. The root was the landing-DETECTION width, NOT the seat — so no shared-collision change was needed (the earlier candidate-(b) absolute-seat exploration was unnecessary once the detection width was corrected). Advances the S1 MZ3 complete-run frontier **f8646 -> f8973** (a frontier advance; the 515 -> 804 error rise is MZ3's own new f8973 downstream cascade, a separate 3px `y` divergence, not a held-frontier regression). Object-local to Obj 0x52; same obActWid-direct landing-width pattern as the swing platform (Obj 0x15) and CollapsingFloor (Obj 0x53). Cross-zone (MovingBlocks appear in MZ/LZ/SBZ): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, MZ1 f2101, MZ2 f2823, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SBZ1 f6082, SBZ2 f2224, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and 47 MovingBlock/physics unit tests + `TestRewindCoverageGuard` pass.
- **S1 Smashable Green Block (Obj 0x51) preserves the player's centre Y across the smash's standing->rolling transition (MZ3 f7982 -> f8646):** `Sonic1SmashBlockObjectInstance.smashBlock` now captures `getCentreY()` before `setRolling(true)` and writes it back (via `NativePositionOps.writeYPosPreserveSubpixel`) when the player was not already rolling. ROM `.smash` sets `obHeight=sonic_roll_height` directly without adjusting obY (`docs/s1disasm/_incObj/51 MZ Smashable Green Block.asm:60-66`); since ROM obY is the centre, shrinking the height leaves the recorded y_pos unchanged. The engine stores y_pos as the TOP-left and derives the centre as `top + height/2`, so `setRolling(true)`'s height shrink (38->28px) moved the derived centre UP by `(sonic_height - sonic_roll_height)` = 5px. In MZ3 the rolling roll-jump lander un-rolled to standing during `Solid_ResetFloor` and seated at ROM's exact centre 0x6CC, but `setRolling(true)` then shifted it to 0x6C7, so the `-0x300` smash rebound launched 5px high. Advances the S1 MZ3 complete-run frontier **f7982 -> f8646** (errors 602 -> 515; the new f8646 root is a separate `g_speed` divergence). Object-local to Obj 0x51 (MZ-only). The seat itself was already ROM-correct (the lander entered the smash at centre 0x6CC == ROM); the fix targets the rolling-transition centre shift, not the seat. Cross-zone: regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, MZ1 f2101, MZ2 f2823, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SBZ1 f6082, SBZ2 f2224, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and 50 SmashBlock/physics unit tests + `TestRewindCoverageGuard` pass.
- **S1 Spikes (Obj 0x36) anchor their out_of_range unload check on the spawn-origin X, not the moved current X (MZ3 f6813 -> f7982):** `Sonic1SpikeObjectInstance` now overrides `getOutOfRangeReferenceX()` to return its spawn-origin `baseX`. ROM `Spikes_Display` runs `out_of_range.w DeleteObject,spikes_origX(a0)` against the spawn origin (objoff_30), NOT the current moved obX (`docs/s1disasm/_incObj/36 Spikes.asm:163,167`; spikes_origX captured at :47). Horizontal-moving spikes (subtype `$x2`, Spik_Type02) extend their obX away from the origin every frame, so the engine's default moved-`getX()` anchor pushed the rounded reference past the out_of_range threshold and deleted them up to a chunk early while the spawn origin was still in range. In MZ3 the sideways moving spike at objpos origin (0xDEC,0x710) sub0x52 spawned then was deleted ~285 frames before the player rolling-jumps UP into its solid underside at f6813 — Spikes are a `SolidObject` (`36 Spikes.asm:67,97`), so ROM bonks his head there (y_speed 0xFB78 -> 0x0000, head pinned, y pushed back to 0x722), whereas the engine had no ceiling and continued rising to 0x719. Anchoring the unload window on `baseX` keeps the spike loaded exactly as long as ROM does, restoring the ceiling. Advances the S1 MZ3 complete-run frontier **f6813 -> f7982** (errors 933 -> 602; the new f7982 root is a separate 5px `y` divergence ~1169 frames further in). Object-local to Obj 0x36; same spawn-origin out_of_range-anchor pattern as the merged conveyor/saw origX fixes. Clean advance — zero benign churn. Cross-zone (Spikes appear in GHZ/MZ/SLZ/SBZ/LZ/SYZ): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, MZ1 f2101, MZ2 f2823, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SBZ1 f6082, SBZ2 f2224, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and 12 S1 spike unit tests + `TestRewindCoverageGuard` pass. (CORRECTION: an earlier draft of this entry claimed a residual `isOutOfRangeS1` screen-anchor gap — that was disproven and is NOT a bug. The engine `camera.getX()` is ROM `v_screenposx`, so the unload anchor `(cameraX-128)&0xFF80` and load anchor `cameraX&0xFF80` are both byte-exact vs ROM `out_of_range` / `ObjPosLoad`; the 0x80 load/unload difference is ROM-intentional hysteresis. The spike spawn-then-despawn was solely the origX-anchor bug fixed here.)
- **S1 Swinging Platform (Obj 0x15) carries a final MvSonicOnPtfm Y re-seat when a rider walks off onto an abutting platform (MZ3 f6558 -> f6813):** `Sonic1SwingingPlatformObjectInstance` now also overrides `carriesAirborneRiderAfterExitPlatform()=true`. ROM Obj15 routine 4 `Swing_Action2` is `bsr ExitPlatform / bsr Swing_Move / bsr MvSonicOnPtfm` with `d3 = obHeight+1` (`docs/s1disasm/_incObj/15 Swinging Platforms.asm:144-154`): `ExitPlatform` clears the rider's on-platform bit when he crosses the platform's full `2*obActWid` width (`cmp d2,d0 / blo .return`, `sub ExitPlatform.asm:24-34`), but `Swing_Move` then advances the platform and `MvSonicOnPtfm` re-seats the rider's Y UNCONDITIONALLY — it does not test the on-platform bit (`sub MvSonicOnPtfm.asm:11-40`). So on the frame the rider crosses the platform's right edge he still receives one final post-move Y carry from that platform's surface, instead of being dropped at his stale terrain Y. The engine had released the ride without that final carry. At MZ3 f6558 (player X byte-exact, pcx=0xC3F = platform B slot 49's right edge) ROM re-seats him to 0x74D from B's post-move surface 0x769 while the engine kept the pre-exit terrain Y 0x74F; platform A (slot 38) acquires him the next frame. `forceAirOnRideExit()` stays at its default but is a no-op for S1's UNIFIED collision model, so no airborne frame is forced. Same ExitPlatform-then-unconditional-MvSonicOnPtfm shape as the Obj 18 platform family and Obj 52 moving block. Advances the S1 MZ3 complete-run frontier **f6558 -> f6813** (errors 935 -> 933; the new f6813 root is a separate 9px `y` divergence). Object-local to Obj 0x15. **MZ2 error count 1028 -> 1029 is PROVEN-BENIGN cascade-internal churn, NOT a regression:** MZ2's frontier f2823 is UNCHANGED, the diverging-frame set is identical (zero pass->fail transitions, zero frames fixed), and the single +1 is a non-kinematic field flip ~3300 frames into MZ2's already-fully-diverged post-f2823 geyser cascade — no previously-passing frame regresses. (A future validation sweep should treat MZ2 1029 as the expected baseline, not flag it.) Cross-zone (Swinging Platforms appear in GHZ/MZ/SLZ; SBZ's spiked ball is non-solid): otherwise byte-identical — GHZ3 f8021/199, MZ1 f2101/169, SLZ1 f2872/163, SLZ2 f3353/131, SLZ3 f3249/710, SBZ1 f6082/207, SBZ2 f2224/625, LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525, SYZ1 f4430/554, SYZ3 f6358/384, FZ f1724/437; GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095/4309 unchanged, and `TestRewindCoverageGuard` passes.
- **S1 Swinging Platform (Obj 0x15) uses its full `obActWid` landing width and seats new riders from the platform's pre-move position (MZ3 f6430 -> f6558):** `Sonic1SwingingPlatformObjectInstance` now overrides `usesCollisionHalfWidthForTopLanding()=true` and `usesPreUpdatePositionForSolidContact()=true`. ROM `Swing_SetSolid`/`Swing_Solid` passes `obActWid` directly as the landing half-width (no generic SolidObjectFull `-$B` narrowing; `docs/s1disasm/_incObj/15 Swinging Platforms.asm:128-133`, `sub PlatformObject.asm:165-179`), so the engine's narrowed range had opened a gap at the platform edge that dropped the player airborne between two abutting platforms. ROM also runs `Swing_Solid` (routine 2) BEFORE falling through to `Swing_Action -> Swing_Move` (the `v_oscillate+$1A`-driven swing nudge), so a fresh landing seats from the platform's PRE-move `obY` (`15 Swinging Platforms.asm:128-138`; seat at `sub PlatformObject.asm:177`); the engine moved the platform from `OscillationManager` during `update()` before the solid pass, so the landing seat read the post-move Y and seated the rider 2px off ROM on the landing frame only (MZ3 f6430: engine 0x74D from post-move slot Y 0x769, ROM 0x74F from pre-move Y 0x76B). Continued-ride frames already matched because routine-4 `Swing_Action2` moves THEN re-seats via `MvSonicOnPtfm`. Advances the S1 MZ3 complete-run frontier **f6430 -> f6558** (errors 706 -> 935; the new f6558 root is the next continued-ride handoff where the engine drops the ride across the next abutting-platform boundary while ROM keeps `standOnObj`). Object-local to Obj 0x15; same PlatformObject-before-move order as the Obj 18 platform family and the SLZ circling platform (`Sonic1CirclingPlatformObjectInstance`). Cross-zone (Swinging Platforms appear in GHZ/MZ/SLZ; SBZ's spiked ball is non-solid): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823 with errors IMPROVED 1044 -> 1028 from the shared swing fix, SBZ1 f6082, SBZ2 f2224, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095 unchanged, and `TestRewindCoverageGuard` passes.
- **S1 Collapsing Floor (Obj 0x53) drops the player airborne when the collapse timer expires, and enters the collapse routine one frame later to match ROM (MZ3):** `Sonic1CollapsingFloorObjectInstance` now (1) when the routine-6 collapse timer reaches 0 with the player still standing, clears the player's `onObject` flag and sets him airborne (`setOnObject(false)` + `setAir(true)`), mirroring ROM `CFlo_FragmentPiece` `.delayCollapse`'s `bclr #3,obStatus(a1)` (clear on-platform → lose support → airborne) + `bclr #5` (`docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:233-240`); and (2) defers the first routine-4 (`CFlo_OnPlatform`) update by one frame via a `collapseEnteredThisFrame` guard, because ROM's `PlatformObject` sets routine 4 during the routine-2 (`CFlo_ChkTouch`) execution so the routine-4 timer countdown only begins the FOLLOWING frame, whereas the engine's `onSolidContact` set routine 4 during the pre-update solid pass and ran routine 4 (decrementing) the same frame the player landed. Previously the engine's `clearRidingObject` only dropped the riding-state link but left the player's `onObject` set and grounded, so he never went airborne — and even with the drop the collapse timer reached 0 one frame early. In MZ3 the player rides a collapsing floor (Obj 0x53 slot 0x2D @~(0x26d,0x4cc)) and ROM drops him airborne at f2174 (`air` 0→1) when the ridden fragment's collapse timer hits 0; the engine kept him grounded. Both fixes together land the airborne transition on ROM's exact frame. Advances the S1 MZ3 complete-run frontier **f2174 -> f6430** (errors 1060 -> 706; the new f6430 root is an unrelated `air` divergence ~4256 frames further in). Object-local to Obj 0x53. PRESERVES the crush-kill (`CFlo_Block` KillSonic) and fragment-fall behavior. Cross-zone (Collapsing Floors appear in MZ/SLZ/SBZ): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, SBZ1 f6082, SBZ2 f2224, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f8941 unchanged, and `TestRewindCoverageGuard` passes.
- **S1 Buzz Bomber Missile (Obj 0x23) only cancels-on-parent-destroyed during its flare phase, not while active (MZ3 slot interleave):** `Sonic1BuzzBomberMissileInstance` now gates its `Msl_ChkCancel` equivalent (`isParentSlotExplosionItem()` -> delete) to the FLARE_COUNTDOWN/FLARE_ANIM phases only, skipping it in ACTIVE. ROM calls `Msl_ChkCancel` (delete the missile if its parent Buzz Bomber became `id_ExplosionItem`) only from `Msl_Main` (routine 0) and `Msl_Animate` (routine 2); `Msl_FromBuzz` (routine 4, the active flight phase) does NOT call it and only deletes at the bottom-boundary check (`docs/s1disasm/_incObj/22, 23 Badnik - Buzz Bomber and Missile.asm:162-194,220-249`). The engine checked it every frame, so in MZ3 the missile (fired f205, active by ~f223) vanished the frame Sonic destroyed its parent Buzz Bomber (slot 80 -> ExplosionItem 0x27 at f239) — ~840 frames before ROM deletes it (f1081, when it descends past the bottom level boundary). The early deletion freed the missile's OST slot, so the badnik-destruction Animal (Obj 0x28, f239) took slot 35 instead of 36, shifting every later object down one slot — including a Batbrain (Obj 0x55) that landed in slot 73 vs ROM 74. That 1-slot offset shifted the Batbrain's `(v_vbla+127-slot)&7` drop gate, locking its flight Y ~3px high so the player's rolling-jump React overlap missed and the React_BadnikHit `-0x100` bounce never fired (y_speed -03C8 vs ROM -02C8 at f2079). Keeping the missile alive through ACTIVE restores the slot cadence: the Batbrain lands in slot 74 and the bounce fires. Advances the S1 MZ3 complete-run frontier **f2079 -> f2174** (errors 1123 -> 1060; the new f2174 root is an unrelated `air` divergence ~95 frames further in). Object-local to Obj 0x23. Cross-zone (Buzz Bombers appear in GHZ/MZ): regression-free — every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, SBZ1 f6082, SBZ2 f2224, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f8941 unchanged, and `TestBuzzBomberLifecycle` (3), `TestBuzzBomberMissileInstance`, `TestS2Ehz1BuzzerSpawnRegression` (5), `TestRewindCoverageGuard` pass.
- **S1 Walking Bomb shrapnel (Obj 0x5F, routine 6) deletes via the ROM render-flag bound, not a raw camera margin (SBZ2 slot interleave):** `Sonic1BombShrapnelInstance` now deletes/keeps-alive on the `obRender` bit-7 render-box test (`isWithinSolidContactBounds()`, the BuildSprites on-screen bound `[camX−obActWid, camX+320+obActWid)` with the shrapnel's `obActWid=12`) instead of the raw `isOnScreenX(160)` (`[camX−160, camX+480]`). ROM `Bom_Shrapnel` deletes with `tst.b obRender(a0) / bpl.w DeleteObject` (`docs/s1disasm/_incObj/5F Badnik - Walking Bomb.asm:218-219`), where `obRender` bit 7 is set by BuildSprites' render-box overlap test (`docs/s1disasm/_inc/BuildSprites.asm:47-58`); `obActWid=12` is inherited from `Bom_Main` (`24/2`, same file line 28). The wider raw margin kept shrapnel alive far longer than ROM, inflating the live count (SBZ2/SLZ3 region: 24 live vs ROM 16) and shifting the FindFreeObj survival set so later objects/shrapnel landed in different OST slots. Matching the render bound restores the survival set (SLZ3 region 24→15 ≈ ROM 16) and the slot occupancy. Advances the S1 SBZ2 complete-run frontier **f1698 -> f2224** (the new f2224 root is an unrelated 1px `x` continued-ride divergence ~526 frames further in). Object-local to Obj 0x5F shrapnel; added a `getOnScreenHalfWidth()=12` override for the ROM `obActWid`. Cross-zone (Walking Bombs appear in SLZ1/SLZ3/SBZ1/SBZ2): regression-free — SLZ1 f2872, SLZ3 f3249, SBZ1 f6082 byte-identical (SLZ3 f3249's shrapnel-arc miss persists — a deeper per-shrapnel position residual, not the survival count). Every other S1 complete-run byte-identical (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SYZ1 f4430, SYZ3 f6358, FZ f1724), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ f1095 unchanged, `TestRewindCoverageGuard` passes (no new coverage gaps).
- **S1 Swinging Platform / Spiked-Ball-Chain (Obj 0x15) allocates one OST-slot child per chain link, matching ROM `Swing_Main` `.makechain` (SBZ2 slot interleave):** the anchor now spawns its chain links as real object-slot children (render-only `SwingChainLinkChild`, positioned by the anchor's swing math each frame, no collision) instead of rendering them internally on the single parent object. ROM `Swing_Main` allocates one SST slot per chain link via FindFreeObj and sets each link to routine $A (`Swing_Display`) — the links just display while the anchor positions them in `Swing_Move2` (`docs/s1disasm/_incObj/15 Swinging Platforms.asm:67-105,215-241`); the links carry no collision (only the anchor/ball is solid/hurt). The count is `chainCount+1` links (non-giant; ROM `dbf d1` with `d1=chainCount` runs d1+1 times, the final `d3<0` link being the frame-2 anchor piece) or `chainCount` (giant ball, `subq.w #1,d1`). The engine previously modelled the whole chain as one object with internal link arrays, so each chain occupied 1 OST slot vs ROM's `chainCount+1`; in SBZ2 the ~8-slot deficit shifted the Walking Bombs (Obj 0x5F) down from ROM slots 0x69-0x6C to 0x64-0x67 (`obj_s69_slot exp 0x69 act 0x64`). Modelling the links as OST-slot children restores the FindFreeObj occupancy: the chain fills its ROM slots and the bombs land in 0x69-0x6C. Advances the S1 SBZ2 complete-run frontier **f1447 -> f1698** (the new f1698 root is an unrelated 1px `y` divergence ~250 frames further in). The riding/solid/hurt behavior is unchanged (it was always on the anchor); the children are pure render-only slot occupants. Rewind: the links are deterministically parent-recreated — on restore the anchor (`SpawnRewindRecreatable`) re-runs `ensureInitialized` and re-spawns its links, then repositions them every frame, so they hold no independent rewind state; their `#recreate` + `#finalScalar#frame`/`#finalScalar#linkPriority` coverage keys are baselined in `src/test/resources/rewind/coverage-baseline.txt`, matching the existing parent-spawned render-child precedent (`Sonic1SpikedBallChainObjectInstance$ChainChild`, `Sonic1CollapsingLedgeObjectInstance$CollapsingLedgeFragmentInstance`). Regression-free across all four Obj-0x15 zones: GHZ1/GHZ1CompleteRun/GHZ2 GREEN, MZ1 f2101/MZ2 f2823/MZ3 f2079 byte-identical, SLZ1 f2872/SLZ2 f3353/SLZ3 f3249 byte-identical; every other S1 complete-run byte-identical (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, SYZ1 f4430, SYZ3 f6358, SBZ1 f6082, FZ f1724), SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095 unchanged, and `TestS1SwingingPlatformSurfaceRegression`, `TestSwingMotion` (4), `TestRewindCoverageGuard`, `TestS1SimpleObjectGenericRecreate` (45), `TestSpawnRewindRecreatableCleanup` (46), `TestS1JumpFromElevator` pass.
- **S1 monitor (Obj 0x26) clears its collision type once broken so a spent monitor no longer preempts ReactToItem for a later monitor (SYZ3):** `Sonic1MonitorObjectInstance.getCollisionFlags()` now returns 0 while `broken` instead of the constant `0x46` (`col_32x32|col_item`), matching ROM `Mon_BreakOpen`'s `move.b #col_none,obColType(a0)` (`docs/s1disasm/_incObj/26, 2E Monitors and Power-Ups.asm:183`). ROM `ReactToItem` skips zero-`obColType` objects (`docs/s1disasm/_incObj/Sonic ReactToItem.asm:52-53`) and exits on the first overlapping object (`rts`), so a broken-but-not-yet-deleted monitor still reporting `0x46` keeps preempting later monitors. SYZ3 has two adjacent monitors a rolling Sonic rolls through — invincibility @0x19A0 then speed-shoes @0x19C8 — and the broken @0x19A0 monitor blocked the @0x19C8 shoes monitor's per-frame break check for ~3 frames, so the speed-shoes air-acceleration doubling (`-0x30` rolling-air accel vs `-0x18`) applied late and `x_speed` diverged at f6065. S2/S3K monitors already gated on `broken` (`MonitorObjectInstance.java:324`, `Sonic3kMonitorObjectInstance.java:502`); this brings S1 to parity. Advances the S1 SYZ3 complete-run frontier **f6065 -> f6358** (the new f6358 root is an unrelated 1px `x` divergence). Object-local to S1 Obj 0x26. Regression-free: SYZ2/GHZ1/GHZ1CompleteRun GREEN, S2 EHZ1 GREEN, S3K AIZ f8941 + SYZ1 f4430 unchanged (pre-existing), and `TestSonic1MonitorObjectInstance` (8), `TestS2Ehz1MonitorBreakRegression` (4), `CollisionSystemTest` (54) pass.
- **S1 FZ boss (Obj 0x85) uses the ROM-inclusive SolidObject right edge so the rolling-into-boss bounce fires on the exact-edge frame (FZ):** the Final Zone boss now overrides `usesInclusiveRightEdge()=true`. Every FZ-boss solid phase calls plain `SolidObject` (`BossFinal_Eggman_Crush` -> `loc_19F2E jsr (SolidObject)`, plus the run/jump/escape phases; `docs/s1disasm/_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:177-182,418,723,907`), whose right-edge X gate is `cmp.w d3,d0 / bhi.w Solid_NoCollision` (`docs/s1disasm/_incObj/sub SolidObject.asm:167-168`). `bhi` is exclusive-greater, so the exact-edge case `relX == width*2` (`d0 == d3`, player centre == `bossX + $2B`) IS a valid contact. The engine's default exclusive gate (`relX >= width*2` -> no contact, `ObjectSolidContactController.resolveContact`) rejected the single frame a rolling-jumping Sonic grazes the boss body's right edge, so the `loc_19F50` rolling-into-boss rebound (`move.w #$300,(v_player+obVelX)` + boss damage) fired one frame late. At FZ trace f837 ROM bounces Sonic right (x_speed `+0x300`) the frame his rolling jump touches the boss's right edge at x=`0x24FB`; the engine missed that frame and kept rolling left (x_speed `-0xF8`), bouncing one frame late at f838. Opting into the inclusive right edge (the same flag S3K horizontal springs and S1 spikes already use for their `SolidObject`-family `bhi` gates) restores the f837 bounce. Advances the S1 FZ complete-run frontier **f837 -> f1724** (the new f1724 root is an unrelated 1px `y` divergence ~887 frames further in; the entire f837-1723 cylinder/plasma combat section now matches ROM, so the error count rises 121 -> 437 as the much longer post-f1724 cascade is now reached). Object-local to Obj 0x85 (FZ-boss only). Regression-free: every other S1 complete-run byte-identical first-error frame (GHZ3 f8021, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SYZ1 f4430, SYZ3 f6065, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249), GHZ1/GHZ1CompleteRun GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 unchanged, and `CollisionSystemTest` (54), `TestSolidRoutineProfiles` (13), `TestRewindCoverageGuard`, `TestS1FzBossGraphRewind` (3), `TestSonic1FZBossDamageFlash`, `TestSonic1FzBossEscapeHitCue` (2), `TestFZPlasmaBallAnimation` pass.
- **S1 GHZ wrecking ball (Obj 0x48) starts swinging only when the boss reaches combat-reverse (GHZ):** the wrecking ball's chain-extension gate (`GHZBossWreckingBall.extendChain`) now latches `chainFullyExtended` (swing start) when the parent ship's `routineSecondary >= 6` (STATE_COMBAT_REVERSE) instead of `>= 4`. ROM `GBall_Base` advances the ball to `GBall_Base2` (where the swing runs) only when the chain has fully extended AND the ship's `ob2ndRout == 6` (`docs/s1disasm/_incObj/3D, 48 Boss - GHZ Main and Wrecking Ball.asm:506-511`). The engine started one boss-state early (STATE_COMBAT_MOVE = 4), so the ball's swing phase ran ahead of ROM and the ball (col_40x40|col_hurt = 0x81, the HURT touch category) overlapped the rolling airborne player at the swing's convergence point where ROM's correctly-phased ball did not — spuriously hurting Sonic (rings 16->0, g_speed zeroed by the hurt). The swing math (ROM `GBall_Move`/`Swing_Move2`: `objoff_3E += 8` then `obAngle += objoff_3E`, side-flip at +/-0x200) was already faithful; only the start phase was off. Advances the S1 GHZ3 complete-run frontier **f7652 -> f8021** (the new f8021 root is an unrelated `y_speed` divergence on a later rolling boss bounce ~369 frames further in, player rings intact). Object-local to Obj 0x48 (GHZ boss only). Regression-free: every other S1 complete-run byte-identical first-error frame (LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SYZ1 f4430, SYZ3 f6065, SLZ1 f2872, SLZ2 f3353, SLZ3 f3249, FZ f837), GHZ1/GHZ1CompleteRun/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 unchanged, and `TestS1GhzBossGraphRewind` (3) passes.
- **S1 boss-hit bounce halves the negated rebound velocity (all S1 bosses):** the boss-hit touch response (`ObjectTouchResponseController.applyBossBounce`) now halves the negated x/y velocity on S1, gated by a new `PhysicsFeatureSet.bossHitHalvesBounceVelocity` flag. ROM S1 `React_BossHit` (`docs/s1disasm/_incObj/Sonic ReactToItem.asm:260-263`) does `neg.w obVelX / neg.w obVelY / asr.w obVelX / asr.w obVelY` — it negates AND arithmetic-shift-right-halves both components — whereas S2 `Touch_Enemy_Part2` and the multi-sprite boss path (`s2.asm:85330-85331, 85343-85344`) only negate, and S3K's `boss_hitcount2`/collision_property paths negate (with ground-vel) but do not halve. The engine previously only negated for all games (S2 behavior), so a rolling Sonic bouncing off an S1 boss rebounded at double the ROM speed. The three boss-bounce sites (player ENEMY, sidekick ENEMY, and the SPECIAL `applyBossBounce`) are now unified through `applyBossBounce`, which applies the per-game halve and the existing S3K ground-velocity negate. Advances the S1 GHZ3 complete-run frontier **f7279 -> f7652** (engine y_speed 0x0418 was exactly double ROM's 0x020C at the GHZ boss (Obj 0x3D) bounce; halving yields 0x020C exactly; the new f7652 root is an unrelated `g_speed` divergence ~373 frames further in). `SONIC_1.bossHitHalvesBounceVelocity=true`, `SONIC_2`/`SONIC_3K=false`. Regression-free: every S1 complete-run byte-identical first-error frame (LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SYZ1 f4430, SYZ3 f6065, SLZ1 f2872, SLZ2 f3353, SLZ3 f3085, FZ f837), GHZ1/GHZ1CompleteRun/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 unchanged, and `TestPhysicsProfile` (35), `TestPhysicsProfileRegression` (18), `TestCollisionModel` (10), `TestSpindashGating` (4), `TestHTZBossTouchResponse` (4), `TestS1GhzBossGraphRewind` (3), `TestAizBossSmallInstance`, `TestS1Ghz3BridgeTerrainCollision` pass.
- **S1 SLZ Elevator (Obj 0x59) reports its full obActWid as the edge-balance width (SLZ):** the SLZ elevator now overrides `getBalanceWidthPixels()` to return its platform half-width (`$28`) instead of the shared default `getOnScreenHalfWidth()` (16). ROM `Sonic_Move` edge-balance reads the stood-on object's `obActWid` (`docs/s1disasm/_incObj/01 Sonic.asm:414-431`), and the elevator sets `obActWid` from `Elev_Var1` (= `80/2` = `$28`; `docs/s1disasm/_incObj/59 SLZ Elevators.asm:22,59`). With the narrow 16px default the engine's balance window (`d1` in `[balanceShift, 2*width-balanceShift)`) was far too tight for the elevator's `$28` platform, so a player standing centered on a rising elevator was treated as edge-balancing — which suppresses the ROM look-up/look-down camera pan (the standing-on-object path branches to `Sonic_LookUp`/`Sonic_Duck` only when NOT balancing). At SLZ3 trace f3085 Sonic ducks (Down held) on a rising elevator: ROM pans the camera down (`v_lookshift` toward 8) while the engine, falsely balancing, eased the bias back to the default, leaving `camera_y` 2px off (ROM 0x01ED vs engine 0x01EB). Advances the S1 SLZ3 complete-run frontier **f3085 -> f3249** (711 -> 710 errors; the new f3249 root is an unrelated `x_speed`/jump divergence ~164 frames further in). Object-local to Obj 0x59 (SLZ-only spawn). Regression-free: SLZ1 f2872/163 and SLZ2 f3353/131 byte-identical, GHZ1/GHZ1CompleteRun GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 unchanged, and `TestSonic1ElevatorObjectInstance`/`TestS1JumpFromElevator` pass.
- **S1 GHZ Collapsing Ledge (Obj 0x1A) clears the rider's on-object/pushing status when it collapses (GHZ):** when a fragmenting ledge releases a still-standing player (`Ledge_FragmentPiece` -> `.delayCollapse`, timer reached zero), the engine now clears the player's `Status_OnObj` and `Status_Push` directly, matching ROM `bclr #3,obStatus(a1) / bclr #5,obStatus(a1)` (`docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:104-105`). The engine's `clearRidingObject` only drops the internal riding bookkeeping; it did NOT clear the player's on-object status, so on the release frame the player stayed object-attached (`isOnObject()` true) and `CollisionSystem`'s terrain floor re-seat was suppressed — the player was pinned at the ledge's last slope Y instead of dropping onto the terrain surface ROM lands him on. The CollapsingLedge slope rides slightly below the underlying terrain near the right edge, so when the ledge releases the player ROM re-seats him ~2px up onto terrain. The engine's GHZ3 collision data already has that terrain (verified: SolidTile full height at the seat column); the bug was purely the missing on-object clear blocking the terrain re-collision (a collision-resolution-ORDER/state bug, not slope data — the SLOPE_DATA and seat formula are byte-faithful). Advances the S1 GHZ3 complete-run frontier **f6464 -> f7279** (271 -> 100 errors; at f6464 the engine held centre 0x038E where ROM re-seats to the 2px-higher terrain 0x038C; the new f7279 root is an unrelated `y_speed` jump-bounce divergence ~815 frames further in). Object-local to Obj 0x1A (GHZ-only spawn). Regression-free: every other S1 complete-run byte-identical first-error frame (LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SYZ1 f4430, SYZ3 f6065, SLZ1 f2872, SLZ2 f3353, SLZ3 f3085, FZ f837), GHZ1/GHZ1CompleteRun/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 unchanged.
- **S1 SLZ Seesaw (Obj 0x5E) rejects the exact-touch zero-distance landing (SLZ):** the seesaw now overrides `rejectsZeroDistanceTopSolidLanding()=true`. ROM `See_Slope` (routine 2) lands the falling player via `SlopeObject`, which falls into the shared `Plat_NoXCheck_AltY` landing detection (`docs/s1disasm/_incObj/sub PlatformObject.asm:128-152,52-66`) whose band is gated by the UNSIGNED `cmpi.w #-16,d0 / blo` test — so the exact-touch case `d0=0` (player bottom flush with the slope surface) is rejected; the standable band is `d0` in `[-16,-1]` (strict penetration), the same gate Obj 18 and the SLZ circling platform already use. The engine's sloped top-landing path (`ObjectSolidContactController.resolveSlopedContact` -> `resolveContactInternal`) honours `rejectsZeroDistanceTopSolidLanding`, but the seesaw didn't set it, so a player falling onto the seesaw was seated one frame early on the flush-contact frame (SLZ3 trace f1416: a rolling-jump Sonic falls onto the seesaw — ROM keeps him airborne at f1416 and seats him at f1417 when he penetrates, the engine seated him at f1416). The seesaw surface comes from the heightmap (`obY - heightByte`) in both the landing (`SlopeObject`) and continued-ride (`SlopeObject_AssumeStoodOn`) paths, so no `obY-8` vs `obY-9` detect/ride split is needed (unlike Obj 18 — only the zero-distance gate). Advances the S1 SLZ3 complete-run frontier **f1416 -> f3085** (1394 -> 711 errors; the new f3085 root is an unrelated SLZ Fan (Obj 0x5D) `camera_y` divergence ~1669 frames further in). Object-local to Obj 0x5E (SLZ-only spawn). Regression-free: SLZ1 f2872/163 and SLZ2 f3353/131 byte-identical, every non-SLZ S1 complete-run byte-identical first-error frame (GHZ3 f6464, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SYZ1 f4430, SYZ3 f6065, FZ f766), GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 GREEN, S3K AIZ complete-run f1095/4309 + keep-green unchanged, and `TestSeesawBallGraphRewind` (6), `CollisionSystemTest` (54), `TestSolidRoutineProfiles` (13), `TestSonic1PlatformObjectInstanceRespawn`, `TestSonic1LargeGrassyPlatformObjectInstance`, `TestRewindCoverageGuard`, `TestCollisionLogic` (79/0) pass. (The pre-existing `TestS1Credits04Slz3TraceReplay` f448 `status_byte` failure is unchanged — verified present on develop without this fix.)
- **S1 Final Zone boss reads the previous-frame camera for its wait-exit, and the cylinder defers its first extension one frame (FZ):** two FZ-boss-only cadence fixes that align the cylinder/plasma attack cycle with ROM. (1) `Sonic1FZBossInstance.updateWait` now gates the wait-exit on a tracked previous-frame camera X instead of the live `camera.getX()`. ROM `BossFinal_Eggman_Wait` reads `(v_screenposx).w` from inside `ExecuteObjects`, which runs *before* `DeformLayers`/`ScrollHoriz` in the level loop (`docs/s1disasm/_inc/DeformLayers (REV01).asm:16-18`), so the boss observes the previous frame's camera; the engine's live camera read during the boss update is one frame ahead, which exited the wait (and started the whole attack cycle) one frame early. With the previous-frame read the wait spans the ROM-correct frame count naturally, so the prior `waitFirstFrame` seed-compensation hack is removed (the first cylinder-select seed is 0x7F without it). (2) `FZCylinder` defers its first extension step one frame after activation (`activationDeferred`): ROM `EggmanCylinder_Action` (routine 2) sets `objoff_29` and advances `obRoutine` to 4 but falls through to its body with `objoff_3C` still cleared to 0 (`docs/s1disasm/_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:692`), so the activation frame seats the cylinder at rest and the first actual extension runs the next frame. Together these advance the S1 FZ complete-run frontier **f766 -> f837** (135 -> 121 errors; the new f837 root is the roll-into-boss damage-bounce hit geometry — ROM `x_speed=0x300`, engine one frame late — a deeper residual unchanged by these cadence fixes). FZ-boss-only. Regression-free: all 18 other S1 complete-run frontiers frame+count byte-identical, GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged.
- **Monitor (Obj 0x26) top-overlap releases a rising rider who jumped off it (all games):** `ObjectSolidContactController.resolveMonitorContact` no longer re-seats / grounds a player who is moving upward off the monitor's top because he just jumped. ROM `Mon_Solid` -> `SolidObject` gates the top-overlap floor re-seat (`Solid_ResetFloor`/`RideObject_SetRide`: clear `Status_InAir`, set `Status_OnObj`, zero `y_vel`) behind `tst.w y_vel(a1) / bmi` — a rising player crossing the top gets no re-seat (the `SolidObjectFull` upward-velocity branch, `docs/skdisasm/sonic3k.asm:41625-41626` `loc_1E198 moveq #0,d4 / rts`). The engine's monitor path applied the landing unconditionally, so a rolling jump off a monitor cleared the airborne state on the jump frame (the air-clear bypassed `setAir()` via `applyObjectLandingState` -> `setAirAfterObjectHurtLanding()`/`setAir(false)` from `resolveMonitorContact`, which is why earlier `setAir` instrumentation missed it). Release is gated on `y_speed < 0` AND `Status_Jump` (`isJumping()`): ROM's gate is `y_vel<0` alone, but a separate engine object-execution-order issue gives a NON-jumping player a transient upward `y_vel` at the monitor pass (MZ1 f1260: Sonic falls toward an adjacent monitor while a broken monitor's break-bounce flips his `y_vel` up just before the solid pass — ROM lands him because ROM's `y_vel` is still `>=0` there). Gating on `Status_Jump` too keeps the fix to the genuine jump-off case without masking that MZ1 ordering bug. Advances the S1 SLZ3 complete-run frontier **f1187 -> f1416** (1055 -> 1394 errors; the new f1416 root is an unrelated Seesaw (Obj 0x5E) ride divergence reached 229 frames further in). Shared monitor full-solid handling, but regression-free: all 19 S1 complete-runs byte-identical first-error frame (MZ1 f2101/169 confirmed un-regressed after the `Status_Jump` narrowing; MZ2 f2823, MZ3 f2079, GHZ3 f6464, LZ1 f5745, LZ2 f1068, LZ3 f8499, SBZ1 f6082, SBZ2 f1447, SLZ1 f2872, SLZ2 f3353, SYZ1 f4430, SYZ3 f6065, FZ f766), GHZ1/GHZ2/SYZ2/SBZ3 GREEN; S2 EHZ1 GREEN, CPZ f3365/310, CPZ2 f2889, MCZ (act 1) GREEN at baseline; S3K AIZ complete-run f1095/4309 unchanged; monitor + collision units pass: `TestS2Ehz1MonitorBreakRegression` (4), `TestSonic1MonitorObjectInstance` (6+2), `TestSonic3kMonitorObjectInstance`/`TestMonitorObjectInstance`/`TestMonitorIconTiming`, `CollisionSystemTest` (54), `TestSolidRoutineProfiles` (13), `TestSonic1PlatformObjectInstanceRespawn`, `TestRewindCoverageGuard`.
- **S1 SLZ Circling Platform (Obj 0x5A) completes the Obj 18 landing family with pre-move solid contact (SLZ):** the circling platform now overrides `usesPreUpdatePositionForSolidContact()=true`, the 5th and final Obj 18 PlatformObject landing-family override — ROM `Circ_Platform` (routine 2) runs `PlatformObject` before `Circ_Types` moves the platform (`docs/s1disasm/_incObj/5A SLZ Circling Platform.asm:28-34`), the same `ExitPlatform`-before-move order as Obj 18 (`18 Platforms.asm:54-67`), so first-landing detection observes the pre-move surface. Corrects the continued-ride seat on the platform's descent: a non-frontier-moving fidelity gain dropping the S1 SLZ2 complete-run error count **151 -> 131** (first error stays f3353, the deep 1px oscillation-phase walk-off-edge residual). Object-local to Obj 0x5A (SLZ-only). Regression-free: SLZ1 f2872/163 and SLZ3 f1187/1055 byte-identical, every non-SLZ S1 complete-run byte-identical, GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 PASS, S3K AIZ unchanged.
- **Edge-balancing suppresses look-up/down camera pan, computed current-frame (all games):** the standing-still look-bias code in `PlayableSpriteMovement.doGroundMove` now gates the look-up/down pan on whether the player is edge-balancing THIS frame. ROM `Sonic_Move` runs `Sonic_Balance` BEFORE `Sonic_LookUp`/`Sonic_Duck`, and when the player balances on a floor or object edge it branches to `Sonic_ResetScr` ("prevent looking up/down", `docs/s1disasm/_incObj/01 Sonic.asm:435-460`; the standing-on-object path lines 409-431 reaches `.balance` -> `Sonic_ResetScr`), easing `v_lookshift` back to the default $60 instead of panning. The engine computes the balance state in `updateCrouchState()`, which runs AFTER the `doGroundMove()` look code, so on the FIRST balancing frame the look code read a stale not-balancing state and panned once. A new side-effect-free `computeCurrentFrameBalancing()` snapshots the direction + balance level, runs the existing `updateBalanceState()` detection, reads `isBalancing()`, then restores both — so the authoritative balance state and facing are still produced at their normal time by `updateCrouchState()` and no other trace's facing/balance timing changes (the `checkObjectEdgeBalance` facing side-effects cited for CPZ2 Tails-CPU / MCZ1 f2362 are fully restored). When balancing, the look gate falls to the `Obj01_ResetScr` ease-to-default branch like ROM. Fixes SLZ3 f1167: post-hurt Sonic lands edge-balancing on a Monitor while holding Down; ROM eases the camera back to the default bias (camera up) while the engine had ducked it ~5px down. Advances the S1 SLZ3 complete-run frontier **f1167 -> f1187** (1056 -> 1055 errors; the new f1187 root is an unrelated rolling-jump-vs-grounded divergence — ROM jumps, engine stays grounded). Shared player-movement change, but regression-free across the full cited blast radius: S2 CPZ f3365/310, **CPZ2 f2889**, MCZ2 f4485, **MCZ1 (act 1) GREEN**, EHZ1 GREEN all at baseline first-error frames; all 19 S1 complete-runs byte-identical first-error frame (GHZ1/GHZ2/SYZ2/SBZ3 GREEN; GHZ3 f6464, LZ1 f5745, LZ2 f1068, LZ3 f8499, MZ1 f2101, MZ2 f2823, MZ3 f2079, SBZ1 f6082, SBZ2 f1447, SLZ1 f2872, SLZ2 f3353, SYZ1 f4430, SYZ3 f6065, FZ f766); S3K AIZ complete-run f1095/4309 unchanged; `TestCamera` (26), `TestCameraRewindSnapshot` (3), `TestLookScrollDelay` (5), `TestPhysicsProfile` (35), `TestCollisionModel` (10), `CollisionSystemTest` (54) pass (`TestSidekickCpuFollowParity`'s 2 failures pre-exist on develop, unaffected).
- **S1 Ball Hog (Obj 0x1E) and Cannonball (Obj 0x20) use ROM `ObjectFall` velocity ordering (SBZ):** both objects integrated motion with gravity applied *before* the position move (manual `velY += gravity` then `moveSprite2`), one gravity-step ahead of ROM. ROM `ObjectFall` (`docs/s1disasm/_incObj/sub ObjectFall & SpeedToPos.asm:8-23`) reads the *old* `y_vel` into `d0`, adds gravity to the stored `y_vel` (for the next frame), then adds the old `d0` to the position — a one-frame-delayed gravity. Both now call the existing ROM-faithful `SubpixelMotion.objectFallXY` / `objectFall` helpers. The cannonball's flight + bounce arc was integrating too fast, so it reached the player ~2 frames early and ~12px off its arc. Advances the S1 SBZ1 complete-run frontier **f6081 -> f6082** (387 errors note: 330 -> 207); the cannonball trajectory now matches ROM's shape frame-for-frame, with a residual 1-frame-early Ball-Hog *launch* (animation-cadence phase) remaining at f6082. SBZ-only (Ball Hog/Cannonball appear only in SBZ1/SBZ2). Regression-free: SBZ2 f1447/977 byte-identical (also has Ball Hogs), every other S1 complete-run frame+count byte-identical, GHZ1/GHZ2/SYZ2/SBZ3 GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged, object/collision/rewind units 100/0.
- **S1 SLZ Circling Platform (Obj 0x5A) uses its full obActWid as the top-landing width, matching ROM PlatformObject (SLZ):** the SLZ circling platform (`Sonic1CirclingPlatformObjectInstance`) now opts into the Obj 18 PlatformObject landing family. ROM `Circ_Platform` (routine 2) passes `obActWid` (= $18) straight to `PlatformObject` as its standable width (`docs/s1disasm/_incObj/5A SLZ Circling Platform.asm:31-34`), so the engine's generic `Solid_Landed` "-$B" narrowing (collision halfWidth = width_pixels + $B, narrowed back for new landings) must NOT apply — `usesCollisionHalfWidthForTopLanding()` now returns true (same as Obj 18, whose `Plat_Solid` also passes obActWid directly). The platform also now models the ROM `MvSonicOnPtfm2` obY-9 continued-ride surface (`HALF_HEIGHT` 8 -> 9) and recovers the `PlatformObject` obY-8 first-landing detect/snap via `getTopLandingSnapAdjustment()=-1`, and rejects the exact-touch zero-distance landing (`rejectsZeroDistanceTopSolidLanding()=true`) — ROM's unsigned `cmpi.w #-16,d0 / blo` band is [-16,-1] (strict penetration), exactly the Obj 18 set. Without these, a player arcing onto the circling platform near its inner edge was caught late: the narrowed $0D landing window rejected him until he had moved further in (SLZ2 trace f3332 — a rolling-jump Sonic lands on the circling platform; ROM catches and unrolls him at relX=8 / f3332, the engine's narrow window missed until relX=12 / f3335, so the rolling player kept falling ~3 frames). Advances the S1 SLZ2 complete-run frontier **f3332 -> f3353** (105 -> 151 errors; the new f3353 root is a distinct 1px continued-ride seat residual on the same platform). Object-local to Obj 0x5A (SLZ-only spawn). Regression-free: SLZ1 (f2872/163) and SLZ3 (f1167/1056) byte-identical, every non-SLZ S1 complete-run frame+count byte-identical (GHZ3 f6464/271, LZ1 f5745/662, LZ3 f8499/1525, MZ1 f2101/169, MZ2 f2823/1043, MZ3 f2079/1123, SBZ1 f6081/330, SBZ2 f1447/977, SYZ1 f4430/554, SYZ3 f6065/483, FZ f766/135), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles`/`TestSonic1PlatformObjectInstanceRespawn`/`TestSonic1LargeGrassyPlatformObjectInstance`/`TestRewindCoverageGuard` (72/0) pass.
- **S1 SBZ spinning platform / trapdoor (Obj 0x69) releases the SolidObject latch on detach so the rider falls on the ROM-correct frame (SBZ):** when the platform stops being solid and detaches the rider, the SolidObject pass had latched this instance onto the player (`latchedSolidObjectId`/`latchedSolidObjectInstance`). That latch makes `finalizeInlinePlayer` skip clearing `Status_OnObj` and the stale-support transition — the latch guard exists for CNZ-style latch-and-own controllers — so the rider stayed pinned to the (now non-solid) platform with `Status_OnObj` set, and a same-frame ground check kept him grounded. The detach now drops the latch for *this* instance (`getLatchedSolidObjectInstance() == this` → `setLatchedSolidObjectId(0)`) and no longer force-sets `air`: ROM `.notsolid2`/`.notsolid` clears the on-object/standing bits (`bclr #3,obStatus(a1)` + `clr.b obSolid(a0)`, `docs/s1disasm/_incObj/69 SBZ Spinning Platforms and Trapdoors.asm:124-130,83-89`) but does **not** set `Status_InAir` — the rider only becomes airborne on the next frame, when his own grounded floor check (which runs before the platform in ExecuteObjects) finds no floor. Forcing air the same frame went airborne one frame early (f5531); releasing the latch and letting the grounded check set air matches ROM. Advances the S1 SBZ1 complete-run frontier **f5532 -> f6081** (the new f6081 root is a distinct BallHog/Cannonball hit-detection divergence, unrelated to the spin platforms; error count 150 -> 330 reflects that newly-reached BallHog cascade, the entire f5531-f6080 spin-platform-row section now matching ROM). SBZ-Obj-0x69-only (latch release gated to this instance). Regression-free: every other S1 complete-run frame+count byte-identical (LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525, MZ2 f2823/1043, MZ3 f2079/1123, SBZ2 f1447/977, SLZ1 f2872/163, SLZ2 f2898/121, SLZ3 f814/1024, SYZ1 f4430/554, SYZ3 f6065/483, GHZ3 f6464/271, FZ f766/135), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged, object/collision/rewind units 126/0.
- **S1 SLZ Seesaw spikeball spawns after the seesaw and falls through on its launch frame (SLZ):** two ROM-grounded ordering fixes to the SLZ seesaw spikeball (Obj 5E child, `Sonic1SeesawBallObjectInstance` + `Sonic1SeesawObjectInstance`). (1) *Spawn slot order:* the seesaw now spawns its spikeball via `spawnChild` (FindNextFreeObj) instead of `spawnFreeChild` (FindFreeObj). ROM `See_Main` allocates the ball with `FindNextFreeObj` (`docs/s1disasm/_incObj/5E SLZ Seesaw.asm:38`), which scans forward from the parent and lands the ball in a slot ABOVE the seesaw; `ExecuteObjects` then runs the seesaw first, so the ball's `See_MoveSpike` reads the seesaw's `see_frame` (objoff_3A) that the seesaw already updated this frame via `See_Slope2`/`See_ChgFrame`. The engine used FindFreeObj (lowest free slot), placing the ball at a LOWER slot than the seesaw, so the ball launched off the PREVIOUS frame's tilt target — one frame late. (2) *Launch-frame fall-through:* `See_MoveSpike` ends with `bra.s See_SpikeFall` (`...asm:168-169`), so on the launch frame the ball already applies its launch `ObjectFall` step(s) (including the ascending double-gravity past the apex); the engine's `updateResting` set the launch velocity and returned, leaving the ball stationary for one frame. Together these put the ball's whole flight — and the landing that springs the standing player via `See_Spring` (`...asm:232-250`) — ~2 frames behind ROM: at SLZ3 trace f814 ROM lands the ball and launches the player UP (`y_speed=-0x0A80`, air 0->1) while the engine still had the player riding (`y_speed=0`, air 0), firing the spring at f816. Advances the S1 SLZ3 complete-run frontier **f814 -> f1167** (1024 -> 1056 errors; the new f1167 root is an unrelated camera_y tracking divergence) and the S1 SLZ2 complete-run frontier **f2898 -> f3332** (121 -> 105 errors; the f2898 frontier was the same seesaw spring-launch bug). Object-local to Obj 5E (SLZ-only spawn). Regression-free: SLZ1 (f2872/163) byte-identical, every non-SLZ S1 complete-run frame+count byte-identical (GHZ3 f6464/271, LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525, MZ1 f2101/169, MZ2 f2823/1043, MZ3 f2079/1123, SBZ1 f5532/150, SBZ2 f1447/977, SYZ1 f4430/554, SYZ3 f6065/483, FZ f766/135), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged, and `TestSeesawBallGraphRewind`/`TestSeesawBallGraphRewindTest`/`TestObjectManagerChildSlotAllocation`/`TestSlotAllocator`/`TestRewindCoverageGuard` (22/0) pass.
- **S1 SBZ spinning platform / trapdoor (Obj 0x69) gates its spin on `v_framecount` and drops the rider into the air on detach (SBZ):** two ROM-grounded fixes in `Sonic1SpinPlatformObjectInstance`. (1) `Spin_Spinner` reads `(v_framecount).w` — the gameplay Level frame counter — to gate the spin cycle (`docs/s1disasm/_incObj/69 SBZ Spinning Platforms and Trapdoors.asm:96`), but `ObjectManager` passed the VBla clock (`v_vbla_byte`) into `update()`, running the spin ~14 frames out of phase with ROM; the gate now routes through `LevelManager.getFrameCounter()+1` (same pattern as the SBZ Electrocuter f1925 fix), aligning the spinner's `obj_frame` with ROM frame-for-frame so a walking player rides each flat (frame-0) platform on the exact frames ROM does. (2) ROM `.notsolid2`/`.notsolid` clears the rider's standing bit (`bclr #3,obStatus(a1)`, `...asm:124-130,83-89`) when the platform stops being solid; the engine's `clearRidingObject` only removed the ride record, so the rider stayed grounded a frame after the platform spun away. The detach now also calls `forceAirOnStaleObjectSupportLoss` so `finalizeInlinePlayer` sets `air` on the frame support is lost. Advances the S1 SBZ1 complete-run frontier **f5530 -> f5532** with errors **387 -> 150** (the new f5532 root is a distinct 1px object-detach landing residual). SBZ-Obj-0x69-only. Regression-free: every other S1 complete-run frame+count byte-identical (LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525, MZ2 f2823/1043, MZ3 f2079/1123, SBZ2 f1447/977, SLZ1 f2872/163, SLZ2 f2898/121, SLZ3 f814/1024, SYZ1 f4430/554, SYZ3 f6065/483, GHZ3 f6464/271, FZ f766/135), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged.
- **S1 SBZ spinning-platform conveyor (Obj 0x6F) clears its `v_obj63` spawner latch when a cluster leaves the screen, so the cluster re-spawns on return (SBZ):** `Sonic1SpinConveyorObjectInstance.onUnload()` now calls `Sonic1ConveyorState.clearSpawned(pathIndex)` when a platform-mode child unloads via the counter-based `out_of_range` path, modelling ROM `SpinC_OutOfRange`'s `bclr #0,(v_obj63,slot)` (`docs/s1disasm/_incObj/6F SBZ Spin Platform Conveyor.asm:24-28`). Previously the engine spawned each spinning-platform cluster once, despawned it when its `objoff_30` baseX left the camera window (ROM-correct), but never re-spawned it because the shared `v_obj63` spawned-latch stayed set and `clearSpawned` was dead code — so a re-loaded spawner self-deleted instead of re-creating children. The fix is gated to `mode==PLATFORM && initialized && !isDestroyed()` so the spawner's own post-spawn self-delete does not clear the latch; all of a spawner's 8 children share one group baseX (the out_of_range reference is the fixed path baseX, not the live position) so they leave together, and each spawner slot maps 1:1 to its children's `pathIndex` (subtype `0x80+N` spawns children `0xN0-0xN3`). The ROM `object_near` slot history confirms the re-spawn (the SBZ1 group3 cluster vanishes ~f3550-3650 and reappears in fresh slots by f3750). Advances the S1 SBZ1 complete-run frontier **f3971 -> f5530** (560 -> 387 errors; the new f5530 root is a distinct Obj 0x69 spinning-platform ride/walk-off residual). SBZ-Obj-0x6F-only. Regression-free: sibling Obj 0x63 LZ conveyor (LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525) and every other S1 complete-run frame+count byte-identical, GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged.
- **S1 Final Zone boss adds 7 to `v_random` on every cylinder-attack side-contact frame (FZ):** the FZ boss (Obj 0x85, `Sonic1FZBossInstance.onSolidContact`) now runs the ROM `addq.w #7,(v_random).w` (`loc_19F50`, `docs/s1disasm/_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:192-195`) on every frame the boss `SolidObject` reports a side collision (`d4 == 1`, `bgt.s loc_19F50`, before the rolling/bounce check), i.e. while Sonic pushes/rolls into the boss body during the cylinder-attack phase. `addq.w` targets `(v_random).w` — the big-endian high word of the 32-bit seed — so it is a 16-bit add to the high word with no carry into the low word. This advances `v_random` the ROM-correct number of times before `BossPlasma_MakeBalls`, so the four plasma-ball target-spread `RandomNumber` draws (`targetX = boss_fz_x+$128 + objoff_32*-$4F + (rand&$1F) - $10`) match the ROM ball drop endpoints; without it the engine's ball spread used a stale seed and a ball overlapped Sonic where ROM's did not. Advances the S1 FZ complete-run frontier **f743 -> f766** (156 -> 135 errors; the new f766 root is a distinct cylinder-attack 1px-Y / host-cylinder-selection residual). FZ-boss-only. Regression-free: all 18 other S1 complete-run frontiers frame+count byte-identical, GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (complete-run f1095/4309) unchanged.
- **S1 Final Zone boss advances `v_random` every wait-state frame (FZ):** the FZ boss (Obj 0x85, `Sonic1FZBossInstance.updateWait`) now runs `addq.l #1,(v_random).w` every frame it is in the `BossFinal_Eggman_Wait` sub-state, matching the ROM loc_19EA2 fall-through tail (`docs/s1disasm/_incObj/85,84,86 Boss - FZ Main, Cylinders, and Plasma Balls.asm:131-133`). This deterministically advances `v_random` through the boss-intro wait so the first `BossFinal_Eggman_Crush` cylinder-select `RandomNumber` draw consumes the ROM seed (0x7F at the first select) and the correct cylinder pair / plasma spread attacks the player; without it the wait left `v_random=0` and the wrong attack diverged the player physics. A one-shot `waitFirstFrame` compensating bump replays the single wait-frame the engine loses because ROM runs `DynamicLevelEvents` *after* `ScrollHoriz` inside `DeformLayers` (`docs/s1disasm/_inc/DeformLayers (REV01).asm:16-18`, so DLE_FZ_Boss reads the post-scroll camera and spawns Obj 0x85 one frame earlier than the engine's pre-scroll level-event ordering — `LevelFrameStep` step 4 vs step 5). The ROM wait spans 127 frames (camera-gated; the 501-tile FZ-boss PLC at 3 tiles/frame finishes before the camera reaches `boss_fz_x`, so the `tst.l (v_plc_buffer)` gate never extends the wait and our synchronous art pipeline matches). Advances the S1 FZ complete-run frontier **f713 -> f743** (155 -> 156 errors; the new f743 root is the distinct FZ plasma-ball (Obj 0x86) spread/drop trajectory geometry — unchanged by this RNG fix). FZ-boss-only. Regression-free: all 18 other S1 complete-run frontiers frame+count byte-identical (GHZ3 f6464/271, LZ1 f5745/662, LZ2 f1068/1616, LZ3 f8499/1525, MZ1 f2101/169, MZ2 f2823/1043, MZ3 f2079/1123, SBZ1 f3971/560, SBZ2 f1447/977, SLZ1 f2872/163, SLZ2 f2898/121, SLZ3 f814/1024, SYZ1 f4430/554, SYZ3 f6065/483), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, and S3K AIZ (complete-run f1095/4309, trace f8941/208) unchanged.
- **S1 SLZ Staircase executes from its highest child slot, after the SLZ Fan (SLZ):** the SLZ Staircase (Obj 5B, `Sonic1StaircaseObjectInstance`) now reserves its 3 ROM child-block slots via `ObjectManager.allocateChildSlotsAfter` and overrides `getExecutionSlotIndex()` to run the consolidated instance from the highest reserved child slot, matching the ROM SST slot order. ROM `Stair_Main` allocates the parent block then 3 children with `FindNextFreeObj`, which scans *forward* from the parent (`docs/s1disasm/_incObj/sub FindFreeObj.asm:32-48`), so the child blocks land in slots *above* the parent (`docs/s1disasm/_incObj/5B SLZ Staircase.asm:39-60`). The SLZ Fan (Obj 5D) loads from the object layout into a slot *between* the staircase parent and its children (verified from `docs/s1disasm/objpos/slz2.bin`: staircase idx 124 then fan idx 125 both at X=0x1790, so ObjPosLoad gives the parent the lower slot, the fan the next, and `Stair_Main` then allocates the children above the fan). In `ExecuteObjects` (ascending slot order) the fan therefore pushes the rider's `x_pos` (`add.w d0,obX(a1)`, `docs/s1disasm/_incObj/5D SLZ Fan.asm:75`) *before* the ridden child block re-runs its `Stair_Solid` `SolidObject` continued-ride bounds check. The engine folds the parent+3 children into one instance executing at the *parent's* (low) slot, so the ride re-seat ran *before* the fan push: at SLZ2 trace f2554 the staircase saw the pre-push `centreX=0x1809` (relX=0x34, still inside the wide $1B ride bounds) and re-seated the rider one frame too long up the rising step (engine y=0x0212 vs ROM 0x0211), where ROM's fan first pushed `centreX` to 0x180B (relX=0x36, off the exclusive `blo` right edge, `docs/s1disasm/_incObj/sub SolidObject.asm:21-44`) so the child block exited the ride and kept the prior seat. Executing the staircase from its highest child slot restores the fan-before-ride-reseat order. The X-direction analog of the prior Y-direction fan/staircase slot-order fix (the `getPlayerCentreYAtExecStart` snapshot, kept — now harmless since the staircase runs after the fan). Advances the S1 SLZ2 complete-run frontier **f2554 -> f2898** (156 -> 121 errors; the new f2898 `y_speed` root is an unrelated launch/bounce divergence much later in the act). The staircase (Obj 5B) is placed only in SLZ, so the change cannot affect non-SLZ traces. Regression-free: SLZ1 (f2872/164) and SLZ3 (f814/1024) byte-identical, every other S1 complete-run frontier verified byte-identical against develop (SYZ1 f4430/554, GHZ3 f6464/271, LZ3 f8499/1525, SBZ2 f1447/977, plus LZ1 f5745/662, MZ2 f2819/1043, SBZ1 f3971/560), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, S2 EHZ1 PASS, S3K AIZ (f1095/4309) unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles`/`TestSonic1PlatformObjectInstanceRespawn`/`TestSonic1SpringObjectInstance`/`TestSonic1StaircaseActivation`/`TestSonic1StaircaseWallCollision`/`TestObjectManagerChildSlotAllocation`/`TestSlotAllocator`/`TestRewindCoverageGuard` (87 tests) all pass.
- **S1 Lava Geyser head defers its first action one frame after spawn (MZ):** the MZ Lava Geyser (Obj 4D, `Sonic1LavaGeyserObjectInstance`) HEAD piece no longer runs its `Geyser_Action` gravity+move on the same frame it runs `Geyser_Main` init. ROM `Geyser_Index` is a `jmp` dispatch (`docs/s1disasm/_incObj/4C, 4D MZ Lava Geyser and Maker.asm:139`): on the head's spawn frame `obRoutine=0` runs `Geyser_Main`, which only inits (`move.w obY,objoff_30`, subtype-1 lavafall `subi.w #$250,obY`, `Geyser_Speeds` velocity, spawns the body+third children) and `addq.b #2,obRoutine` -> routine 2, then RETURNS without falling into `Geyser_Action` (`asm:157-167`); the gravity (`addi.w #$18,obVelY`) and `SpeedToPos` of `Geyser_Action` (routine 2, `asm:235-242`) run the NEXT frame. The engine's `update()` collapsed `ensureInitialized()` (Geyser_Main) and `updateHead()` (Geyser_Action) into one frame, so the lavafall column fell one frame early. Compounded across the column, the engine's col_hurt lava wall reached Sonic ahead of ROM: at MZ2 trace f2819 Sonic's position is byte-perfect (x=0x0395,y=0x044C in both) but the engine spuriously hurt-bounced him (g_speed 0, air 1) while ROM kept him grounded and running (g_speed 0x18, air 0) because the engine's wall had fallen ~1 frame further onto him. Fix: a `ranGeyserMainThisFrame` one-shot that runs Geyser_Main init on the spawn frame then returns, deferring the head's first Geyser_Action to the next frame. The THIRD piece is constructed already-initialized at routine 2 (it never runs Geyser_Main, so it acts immediately, matching ROM's higher-slot same-pass execution) and the BODY (routine 4) merely tracks the head, so both are unaffected. Advances the S1 MZ2 complete-run frontier **f2819 -> f2823** (1116 -> 1043 errors; the residual f2823 is the GeyserMaker's free-running eruption cadence materialising a few frames early — a placement-materialization-frame issue, NOT this object-local action-frame defer). Object-local to Obj 4D (MZ-only). Regression-free: GHZ1/GHZ2/SYZ2 stay GREEN, MZ1 frontier f2101 unchanged but its cascade improves (205 -> 169 errors, the geyser column also touches MZ1 timing), every other S1 complete-run frontier frame+count byte-identical (MZ3 f2079/1123, LZ1 f5745/662, LZ3 f7952/1778, SLZ1 f2872/164, SLZ2 f2552/137, SLZ3 f814/1024, SBZ1 f3971/560), and `TestSonic1LavaGeyserOutOfRange` (4/0) passes.
- **S1 curled (dormant) Roller is not collidable until it activates (SYZ):** the SYZ Roller (Obj 0x43, `Sonic1RollerBadnikInstance`) no longer hurts Sonic while it is still curled in its initial waiting state. ROM `Roll_Main` (routine 0) never sets `obColType`, and `Roll_Action_FromLeft` (ob2ndRout=0) leaves it untouched until the Roller activates (`docs/s1disasm/_incObj/43 Badnik - Roller.asm:19-38,86-100`); `obColType` is first written only on activation (`Roll_Action_FromLeft` sets `$8E`, line 96) or stop-and-unfold (`Roll_Action_StopAndUnfold` sets `$0E`, line 177), both of which advance `ob2ndRout` away from 0 and never return to it. A Roller still in its waiting state therefore has `obColType = 0` (col_none), so `ReactToItem` skips it entirely (`move.b obColType(a1),d0 / bne ...`, `docs/s1disasm/_incObj/Sonic ReactToItem.asm:52-53`). The engine's Roller reported `getCollisionFlags() = 0x0E` (destroyable enemy) from spawn, so a dormant curled Roller was treated as a live badnik: at S1 SYZ1 trace f2338 Sonic fell straight down onto a never-activated Roller (engine slot 34, `@0710,0253`, still in `STATE_ROLL_CHK`) and the engine registered an enemy touch — hurting him, scattering all 31 rings (`rtn` 2 -> 4, LostRing objects spawned) — where ROM falls past it unscathed (rings stay 31). Fix: `getCollisionFlags()` returns 0 while `secondaryState == STATE_ROLL_CHK` (equivalent to ROM's unset `obColType`, since the Roller never re-enters that state once it has activated). Advances the S1 SYZ1 complete-run frontier **f2338 -> f4430** (the new f4430 root is an unrelated slope/wall `x_speed`/`y_speed` physics divergence much later in the act, by which point Sonic has legitimately lost his rings in both ROM and engine; the error count rises 359 -> 554 as the longer post-f4430 cascade is now reached). Object-local to Obj 0x43 (SYZ-only spawn) and derived from existing state (no new rewind-captured field). Regression-free: every other S1 complete-run frontier frame+count byte-identical (LZ1 f5745/662, GHZ3 f6464/271, MZ1 f2101/169, MZ2 f2819/1116, SBZ1 f3971/560, SLZ1 f2872/164, SYZ3 f6065/483), GHZ1/GHZ2 stay GREEN, S2 EHZ1 PASS, S3K AIZ f1095/4309 unchanged, and `TestS1BadnikGenericRecreate`/`TestRewindCoverageGuard` (13 tests) pass.
- **S1 SLZ Fan reads the player's pre-object-seat Y, matching ExecuteObjects slot order (SLZ):** the SLZ Fan (Obj 5D, `Sonic1FanObjectInstance`) now evaluates its vertical wind-range gate against the player's centre Y captured at the *start* of the object exec pass (post-physics, before any object re-seated him this frame), via the new `ObjectManager.getPlayerCentreYAtExecStart`, instead of the live centre Y. ROM `Fan_Action` reads `obY(a1)` at the fan's slot inside `ExecuteObjects` (`docs/s1disasm/_incObj/5D SLZ Fan.asm:53-56`); objects whose ROM slot is *higher* than the fan have not repositioned Sonic yet. The SLZ staircase Sonic rides (Obj 5B) allocates its child pieces *above* the fan's slot and re-seats his Y each frame as he walks up the steps, so at the fan's slot ROM still sees the pre-step-lift Y. The engine folds the staircase into a *lower* slot than the fan, so the fan read the already-step-lifted Y, putting Sonic inside the fan's vertical trigger range (`dy = playerY + $60 - fanY`, in-range `0 <= dy < $70`) one frame early and applying the +2px wind push a frame before ROM. At SLZ2 trace f2552 the engine pushed Sonic to x=0x1808 while ROM (whose fan sees dy=-1, out of range) left him at 0x1806; the recomputed-by-hand ROM fan force is byte-identical +2 (so it is *not* a force/formula bug, and there is *no* wall negating the push — Sonic is moving right at x_speed=0xE4, not blocked), and ROM applies that +2 one frame later at f2553. Reading the exec-start Y restores the slot-order phase. Advances the S1 SLZ2 complete-run frontier **f2552 -> f2554** (the new f2554 root is the parked SLZ staircase ride-seat +1px-Y family, unrelated to the fan; the SLZ2 error count rises 137 -> 156 as the trace now reaches that deeper pre-existing root two frames later). Object-local to Obj 5D's wind gate (only the fan reads the exec-start snapshot; the snapshot capture in `ObjectManager.update` is inert for every other object). Regression-free: SLZ1 (f2872/164), SLZ3 (f814/1024), LZ1 (f5745/662), SBZ1 (f3971/560), SYZ1 (f2338/359), MZ2 (f2819/1116) all byte-identical, GHZ1/GHZ2/SYZ2 stay GREEN, S2 EHZ1 PASS, S3K AIZ (f1095/4309) unchanged.
- **S1 leftward horizontal camera scroll is uncapped (SYZ/all S1 zones):** the camera's per-frame horizontal scroll cap now applies only to *rightward* movement in Sonic 1, matching the shipped ROM. S1's `ScrollHoriz` caps the rightward move at 16px (`SH_MoveCameraRight`, unconditional) but the *leftward* cap (`SH_MoveCameraLeft`) is gated behind `if FixBugs`, and the shipped ROM has `FixBugs = 0` (`docs/s1disasm/sonic.asm:20`), so the left branch runs straight to `.moveLeft` and adds the full offset with no per-frame limit (`docs/s1disasm/_inc/ScrollHoriz & ScrollVertical.asm:59-99`). S2 (`s2.asm:18102-18105`) and S3K (`sonic3k.asm:38403-38406`) cap *both* directions, so this is an S1-only divergence. The engine's shared `Camera.computeNextHorizontalCameraX` capped both directions, so whenever Sonic moved left faster than 16px/frame the camera lagged ROM. At S1 SYZ1 trace f816 Sonic rides a leftward-moving FloatingBlock then springs back; the camera needed to move -17px left in one frame but the engine capped it at -16, leaving `camera_x` 1px right of ROM (0x0194 vs 0x0193) and cascading every subsequent camera_x frame. Fix: a `PhysicsFeatureSet.uncappedLeftwardHorizontalScroll` flag (true for `SONIC_1`, false for `SONIC_2`/`SONIC_3K`), plumbed to `Camera.setUncappedLeftwardScroll` from `LevelManager` alongside the existing `fastScrollCap` wiring, gating only the leftward branch. (The earlier "FloatingBlock riding miss" hypothesis was wrong — Sonic's kinematics matched ROM every frame; the 1px camera_x was a pure horizontal-scroll-cap symptom.) Advances the S1 SYZ1 complete-run frontier **f816 -> f2338** (360 -> 359 errors; the new f2338 root is an unrelated `x_speed` physics divergence) and additionally reduces the S1 MZ1 cascade (f2101 frontier, 204 -> 169 errors, same first-error frame) since MZ1 also has fast-leftward camera moments. Shared `Camera`/`PhysicsFeatureSet` change but regression-free: GHZ1/GHZ2 stay GREEN, every other S1 complete-run frontier frame+count byte-identical (LZ1 f5745/662, GHZ3 f6464/271, SLZ2 f2552/137, SBZ1 f3971/560, SYZ3 f6065/483), S2 EHZ1 PASS, S3K AIZ f1095/4309 unchanged, and `TestPhysicsProfile`/`TestPhysicsProfileRegression`/`TestCamera`/`TestCameraRewindSnapshot`/`TestLookScrollDelay`/`TestCollisionModel` (97 tests) all pass.
- **S1 conveyor act-3 out-of-range left-extension keeps platforms one chunk past the left edge (LZ/SBZ):** the LZ Conveyor (Obj 63, `Sonic1LZConveyorObjectInstance`) and SBZ Spin Conveyor (Obj 6F, `Sonic1SpinConveyorObjectInstance`) platforms now honour the ROM's act-3-only wider despawn window. The base `out_of_range` macro deletes when the chunk-aligned reference X is more than 640 px right of the window's left edge (`cmpi.w #128+320+192,d0 / bhi exit`). In **act 3 only**, both conveyors' out-of-range tail does NOT immediately delete on that `bhi`: `LabyrinthConvey`/`SpinConvey` first run `cmpi.b #act3,(v_act).w` and, when in act 3, `cmpi.w #-$80,d0 / bhs.s LCon_Display` (`docs/s1disasm/_incObj/63 LZ Conveyor.asm:16-20`; `docs/s1disasm/_incObj/6F SBZ Spin Platform Conveyor.asm:17-21`), so a platform whose aligned baseX (`objoff_30`) is up to one chunk (0x80) to the LEFT of the window (`d0 in [0xFF80,0xFFFF]`) is kept alive and displayed instead of deleted. The engine's `isPersistent()` used the standard window for all acts, so in LZ3 the 12-platform path-4 cluster (shared baseX `0x0D00`) fully despawned when the camera scrolled right past it (camera `0x0E02`, baseX one aligned chunk behind the left edge) and — because the consumed spawner's `v_obj63` bit stays latched and the cluster never respawns (ROM behaviour too) — only a single platform was left when Sonic backtracked left to land at trace f7952, so he fell through where ROM carries him across two platforms (`stand_on_obj` 0x4E -> 0x4B). Modelled on the ROM act value (a `cmpi.b #act3` ROM-state branch, the same object using the standard window in acts 1/2), via a new shared `AbstractObjectInstance.isInRangeAtWithLeftExtension(referenceX, leftChunks)` that mirrors the `bhs #-(leftChunks*$80)` keep. Advances the S1 LZ3 complete-run frontier **f7952 -> f8499** (1778 -> 1525 errors; the new f8499 `g_speed` root is an unrelated airborne-bounce divergence ~250 px below any conveyor). LZ1/SBZ1 are act 1 so the extension does not trigger (both byte-identical). Regression-free: every other S1 complete-run frontier byte-identical (LZ1 f5745, LZ2 f1068, SBZ1 f3971, SBZ2 f1000, MZ1 f2089, MZ2 f2819, MZ3 f2079, SLZ1 f2872, SLZ2 f2552, SLZ3 f814, SYZ1 f816, SYZ3 f6065, GHZ3 f6464), GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, and `CollisionSystemTest`/`TestSolidRoutineProfiles`/`TestSonic1PlatformObjectInstanceRespawn`/`TestRewindCoverageGuard` + S3K `TestS3kAiz1SkipHeadless`/`TestSonic3kLevelLoading` (112/0) pass.
- **S1 SBZ Saw unloads off-screen against its saw_origX anchor instead of staying forever persistent (SBZ):** the SBZ Saw / Pizza Cutter (Obj 0x6A, `Sonic1SawObjectInstance`) `isPersistent()` returned `!isDestroyed()`, so an off-screen saw never unloaded. ROM `Saw_Action` ends with `out_of_range.s .delete, saw_origX(a0)` (`docs/s1disasm/_incObj/6A SBZ Saws and Pizza Cutters.asm:39`), deleting the saw when its ORIGIN anchor (`saw_origX`, objoff_3A) leaves the camera window — checked AFTER the type subroutine runs (and, for ground saws, updates `saw_origX` to the moved `obX`, lines 338/400 = ROM `move.w obX(a0),saw_origX(a0)`). The fix: `isPersistent()` -> `false` (so the shared counter-based out_of_range unload runs), `getOutOfRangeReferenceX()` -> `origX` (the saw_origX anchor; ground saws track the moved position, oscillating pizza cutters keep the spawn origin — `origX` is correct for both), and `checksOutOfRangeAfterRoutine()` -> `true` (ROM checks post-move at line 39). Previously two SBZ2 saws at `@0298,038B`/`@02A0,018B` sat ~239 px left of the camera (cam 0x387) yet stayed loaded, pushing the rings ROM placed in slots 0x61/0x62 down to 0x63/0x64 at trace f239. Advances the S1 SBZ2 complete-run frontier **f1395 -> f1447** (1000 -> 977 errors); the new f350 object-table divergence is a separate downstream root. Object-local to Obj 0x6A (SBZ-only spawn). Regression-free: SBZ1 (f3971/560) byte-identical, GHZ1/GHZ2/SYZ2 stay GREEN, every other S1 frontier unchanged (LZ2 f1068/1616, MZ2 f2819/1116, SLZ1 f2872/164), and `CollisionSystemTest` (54/0), `TestSolidRoutineProfiles` (13/0), `TestRewindCoverageGuard` (1/0), `TestSonic1PlatformObjectInstanceRespawn` (1/0) pass.
- **S1 badnik out_of_range unload runs AFTER the routine (post-move), via `checksOutOfRangeAfterRoutine()` (LZ + all S1 zones):** the S1 counter-based exec loop (`ObjectManager.updateCounterBasedExecThenLoad`) now defers the `out_of_range` unload check to *after* `executeObjectWithSolidContext` for objects whose ROM routine checks out_of_range at the END (post-move). A new `ObjectInstance.checksOutOfRangeAfterRoutine()` predicate (default `false`) is overridden to `true` on `AbstractBadnikInstance`: S1 badniks run `SpeedToPos` then `bra.w RememberState`, and `RememberState` tests the object's CURRENT post-move `x_pos` (`docs/s1disasm/_incObj/sub RememberState.asm:9`; tails for Jaws `2C:58-59`, Crabmeat `1F:116,53`, Chopper `2B:35,11`, Burrobot `2D:70,42`, Moto Bug `40:89,67`, Newtron `42:129,38`, Roller `43:124,51`, Yadrin `50:110,88`, Ball Hog `1E,20:55`, Basaran `55:130`, Walking Bomb `5F:167,158`, Orbinaut `60:196,122`). When the predicate is true the loop SKIPS the pre-execute check and re-checks out_of_range post-execute, on the moved position; otherwise (static scenery / fixed-anchor objects whose ROM out_of_range is at routine START) the pre-execute check is kept unchanged. The global order is NOT flipped. This mirrors the non-counter `runExecLoop` (S2/S3K), which already checks out_of_range post-execute (MarkObjGone at routine end). Previously the pre-execute check tested the PREVIOUS frame's position, so a moving badnik that left the camera window unloaded one frame late: at LZ2 the slot-37 Jaws (`@012F,0430`, `x_vel=-0x40`) is `object_removed` by ROM at f196 (aux) but the engine kept it until f197, holding a slot ROM had freed and drifting later `FindFreeObj` allocations. Advances the S1 LZ2 first object-table divergence **f196 -> f217** (the slot-37 Jaws now unloads at f196 exactly like ROM); the committed `obj_s20_slot` metric stays at f1068/1616 because that symptom's root is the separate downstream f217 ring-sparkle-vs-Jaws-respawn slot interleave. Scoped to the S1 counter-based path; S2/S3K (which use `runExecLoop`) are not gated by the predicate. Regression-free (full frame+count sweep): GHZ1/GHZ2/SYZ2 stay GREEN; every other S1 complete-run frontier byte-identical (LZ1 f5745/662, LZ3 f7952/1778, MZ1 f2101/204, MZ2 f2819/1116, MZ3 f2079/1123, SLZ1 f2872/164, SLZ2 f2552/137, SLZ3 f814/1024, SYZ1 f816/360, SYZ3 f6065/483, SBZ1 f3971/560, SBZ2 f1395/1000, GHZ3 f6464/271, FZ f713/155); S2 EHZ1 GREEN, S3K ICZ f3139 unchanged; `CollisionSystemTest` (54/0), `TestSolidRoutineProfiles` (13/0), `TestSonic1PlatformObjectInstanceRespawn` (1/0), and S3K `TestS3kAiz1SkipHeadless` (8/0) / `TestSonic3kLevelLoading` / `TestSonic3kBootstrapResolver` / `TestSonic3kDecodingUtils` all pass.
- **S1 conveyor platform survives its spawn frame before lazy baseX init (LZ/SBZ):** the LZ Conveyor (Obj 63, `Sonic1LZConveyorObjectInstance`) and SBZ Spin Conveyor (Obj 6F, `Sonic1SpinConveyorObjectInstance`) platforms no longer self-cull on the frame their spawner creates them. ROM `LabyrinthConvey` runs the routine dispatch (`LCon_Main`, which reads the path table and writes `objoff_30`=baseX) **before** the trailing `out_of_range.s loc_1236A,objoff_30(a0)` macro (`docs/s1disasm/_incObj/63 LZ Conveyor.asm:5-13,53-63`; SBZ `SpinC_Main` is identical, `docs/s1disasm/_incObj/6F SBZ Spin Platform Conveyor.asm:5-13,37-54`), so a freshly spawned platform always has a valid `objoff_30` when its first `out_of_range` check runs. The engine loads `baseX` lazily in `ensureInitialized()` on the first `update()`; until then `baseX` is the sentinel `0`, and the object's `isPersistent()` (which mirrors ROM `out_of_range` via `isInRangeAt(baseX)`) returned false for any uninitialized platform whose spawn cluster the exec loop's out-of-range pre-pass reached before the platform's own first routine ran. The LZ1 spawner at `@1070,0280` produces 8 child platforms (subtypes 0x00/0x02×3/0x04/0x05×3); the three subtype-0x02 children were despawned on their spawn frame (f5281/f5282) with `baseX=0`, permanently removing them from the conveyor loop (the consumed spawner's `v_obj63` bit stays latched, so they never respawn — matching ROM, which also spawns the cluster once). The missing platforms left a gap exactly where ROM's slot-0x48 platform carries Sonic at trace f5745: ROM walks Sonic off slot 0x20 (f5736) and lands him on slot 0x48 at x=0x1012,y=0x02EE (f5745), but the engine had no platform there so he free-fell. Returning `true` from `isPersistent()` while `!initialized` (the platform initializes on its update this frame; the next frame's check uses the real baseX) keeps the ROM "routine sets objoff_30 before out_of_range" invariant. Advances the S1 LZ1 complete-run frontier from a `camera_y` symptom at f5745 to a 1px `y` landing residual at the same frame (**2214 -> 662 errors**; the residual is a sub-pixel corner-rounding phase on one of the three left-side ascending platforms — two of the three match ROM byte-for-byte, the lead one is 1px/1frame ahead). Object-local to Obj 63 / Obj 6F (LZ/SBZ spawns). Regression-free: every other S1 complete-run frontier byte-identical (LZ2 f1068, LZ3 f7952, SBZ1 f3971, MZ2 f2819, MZ3 f2079, SBZ2 f1000, SLZ1 f164, SLZ2 f137, SLZ3 f1024, SYZ1 f360, SYZ3 f483, GHZ3 f6464, MZ1 f2089), GHZ1/GHZ2/SYZ2 stay GREEN, and `CollisionSystemTest`, `TestSolidRoutineProfiles`, `TestSonic1PlatformObjectInstanceRespawn`, `TestRewindCoverageGuard`, S3K `TestS3kAiz1SkipHeadless`/`TestSonic3kLevelLoading` all pass.
- **Camera bottom-boundary airborne acceleration reaches the camera one frame later (all games, MZ):** the camera's bottom-boundary easing (`Camera.updateBoundaryEasing` / `updatePosition`) now defers the airborne +8px boundary acceleration to the camera clamp by one frame, matching ROM ordering. ROM `DeformLayers` runs `ScrollVertical` (camera move + clamp to the current `v_limitbtm2`) **before** `DynamicLevelEvents` (`docs/s1disasm/_inc/DeformLayers (REV01).asm:16-18`), and `.move_boundary_down` accelerates the bottom boundary from +2px to +8px/frame once Sonic is airborne and the camera is within 8px of it (`docs/s1disasm/_inc/DynamicLevelEvents.asm:35-49`). Because the boundary update runs *after* the camera move, that +8 rate change is not visible to the camera until the next frame's `ScrollVertical`. The engine eases boundaries *before* moving the camera (`updateBoundaryEasing()` then `updatePosition()`), which is harmless while the boundary descends at a constant rate but applied the freshly-accelerated boundary to the same frame's camera clamp — running the +8 one frame early at the ground→air transition. At S1 MZ1 trace f2089 (Sonic going airborne, camera pinned to the descending bottom boundary), the engine jumped the camera to `0x02C4` while ROM was still at `0x02BE` (+6px early). Fix: a derived `cameraClampMaxY` that the camera clamp consumes, advancing in the boundary-descending direction by the step `maxY` took on the *previous* frame — so a constant +2 keeps perfect pace, but the +8 jump reaches the camera one frame late exactly like ROM. The ascending/snap path (ROM `SV_BottomBoundaryMoving`, GHZ2's rising boundary under a roll) still follows `maxY` same-frame, so GHZ2 stays green. `cameraClampMaxY` is derived (re-synced on `setMaxY` and reset to the converged value on rewind restore), so it needs no `CameraSnapshot` field. Advances the S1 MZ1 complete-run frontier **f2089 -> f2101** (205 -> 204 errors; the new f2101 root is a deeper boundary-easing-convergence divergence — the engine's `maxY` itself diverges from ROM `v_limitbtm2`, which needs the recorder to capture `v_limitbtm2` to resolve). Shared `Camera` change but regression-free: GHZ1/GHZ2 stay GREEN, every other S1 complete-run frontier byte-identical (LZ1 f5745, GHZ3 f6464, SLZ1 f2872, SYZ1 f816), S2 EHZ1 + S3K AIZ (f1095) unchanged, and `TestCamera`/`TestCameraRewindSnapshot`/`TestLookScrollDelay` pass.
- **S1 Seesaw tilt target computed post-player, atomic with the frame advance (SLZ):** the SLZ Seesaw (Obj 5E, `Sonic1SeesawObjectInstance`) now computes its tilt target (`See_ChkSide`) inside `update()` immediately before the `See_ChgFrame` mapping-frame advance, instead of latching it in `onSolidContact`. ROM `See_Slope2` (routine 4, `docs/s1disasm/_incObj/5E SLZ Seesaw.asm:71-118`) runs `See_ChkSide` (sets `see_frame` from the player's current x, then falls into `See_ChgFrame`) entirely inside `ExecuteObjects`, AFTER the player slot has moved. The engine runs S1 objects after player physics (`objectsExecuteAfterPlayerPhysics=true`), so `update()` already observes Sonic's post-move x — but the target was being latched in `onSolidContact`, which fires during the player's solid pass (BEFORE the post-physics object update), so `See_ChgFrame` advanced `obFrame` using the PREVIOUS frame's target. As the player rocked across the seesaw, the engine's tilt flip (`obFrame` 0x02→0x01 when the rocking player crossed within 8px of centre) lagged ROM by a frame, re-seating the rider on the stale (tilted) slope: at SLZ3 trace f745 ROM's seesaw is flat (`obj_frame=0x01`, confirmed by the v3.5 recorder `object_near` aux) and seats Sonic at y=0x02D8, while the engine still had it tilted (`0x02`) and seated him 3px low at 0x02DB; the lag also shifted the spikeball launch (f786). Moving `See_ChkSide` into `update()` (gated on the standing bit, which `onSolidContact` still maintains) keeps the ROM `ChkSide→ChgFrame` order atomic and post-move. Advances the S1 SLZ3 complete-run frontier **f745 -> f814** (resolving the f745/f756 tilt re-seat blips AND the f786 spikeball-launch cascade; the new f814 root is a deeper spikeball launch `y_speed` divergence). Object-local to Obj 5E (SLZ-only spawn). Regression-free: SLZ1 (f2872) and SLZ2 (f1714) byte-identical, MZ3 (f2079) unchanged, GHZ1/GHZ2/SYZ2/SBZ3 stay GREEN, and `TestSeesawBallGraphRewind`, `TestS1SlzBossSpikeballGraphRewind`, `TestSonic1LargeGrassyPlatformObjectInstance`, `TestCollisionLogic` all pass.
- **S1 Vanishing Platform reads v_framecount for its cycle gate + PlatformObject landing family (SBZ):** two stacked fixes to the SBZ vanishing platform (Obj 6C, `Sonic1VanishingPlatformObjectInstance`). (1) *Cycle-phase gate:* `updateIdle` now reads `v_framecount` (`levelManager.getFrameCounter()+1`) for the routine 6->2 transition gate `(v_framecount - objoff_36) & objoff_38 == 0` instead of the `update()` `frameCounter` parameter. ROM `VanP_Sync` reads `(v_framecount).w` (`docs/s1disasm/_incObj/6C SBZ Vanishing Platforms.asm:51-56`), but the engine's `frameCounter` param is the VBla clock for S1 objects (ObjectManager passes vblaCounter), which runs out of phase with `v_framecount`. The platform therefore entered its vanish/appear cycle at the wrong absolute frame (SBZ1 trace: VBla `0x8500` multiple at vfc 2170 vs ROM's `v_framecount` `0x800` multiple at vfc 2048 — a 122-frame phase error), leaving the `@0BB0,0648` platform half a cycle out of phase (engine vanished while ROM solid) so Sonic fell through. Same fix class as the SBZ Electrocuter vfc gate. The v3.5 trace's `object_near` `obj_frame` confirmed the target platform is solid (obj_frame 0) at f2268 in both ROM and the engine after this fix. (2) *Landing-surface family:* the platform now opts into the PlatformObject landing-surface family — `HALF_HEIGHT` 8 -> **9** (`MvSonicOnPtfm2 subi.w #9`, `docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`), `usesCollisionHalfWidthForTopLanding()`, `rejectsZeroDistanceTopSolidLanding()`, and `getTopLandingSnapAdjustment()=-1` (PlatformObject's obY-8 first-landing detection, `docs/s1disasm/_incObj/sub PlatformObject.asm:37-38`). ROM `VanP` lands the first contact via `PlatformObject` (routine 2) and continued riding via `MvSonicOnPtfm2` (routine 4) (`6C SBZ Vanishing Platforms.asm:82-93`) — the same top-solid family as Obj 18 (`Sonic1PlatformObjectInstance`). Without these the now-solid platform still failed to catch the falling player on the ROM frame (engine `y=0x062F` airborne vs ROM `y=0x062C` landed). Together the two fixes land Sonic on the re-appeared platform exactly like ROM. Advances the S1 SBZ1 complete-run frontier **f2268 -> f3971** (805 -> 560 errors). Object-local to Obj 6C (SBZ-only). Regression-free: GHZ1/GHZ2/SBZ3/SYZ2 stay GREEN, every other S1 complete-run frontier byte-identical (SBZ2 f1395, MZ2 f2819, SLZ1 f2872, LZ1 f5745), and `CollisionSystemTest` (54/0), `TestSolidRoutineProfiles` (13/0), `TestSonic1PlatformObjectInstanceRespawn` (1/0) pass.
- **Scaled-window title/game transitions:** switching between the master title screen and gameplay now re-reads the actual GLFW framebuffer size after applying launch display dimensions, then reshapes the viewport from framebuffer pixels instead of the requested logical window size. This keeps high-DPI/full-scaled windows from rendering the new mode into a small corner with the rest of the framebuffer black.
- **Master-title launch profiles preserve the host window:** switching between the master title screen and gameplay no longer calls `glfwSetWindowSize()` when a selected game's launch profile changes the display aspect. The engine still updates the logical render/projection width for widescreen profiles, but reshapes against the existing framebuffer so the OS window does not resize or jump position during title/game transitions.
- **S1 Seesaw uses absolute slope values for landing (SLZ):** the SLZ Seesaw (Obj 5E, `Sonic1SeesawObjectInstance`) now returns `getSlopeBaseline()=0` instead of `COLLISION_HEIGHT` (8). ROM `See_Slope` (routine 2, `docs/s1disasm/_incObj/5E SLZ Seesaw.asm:67`) lands the falling player via `SlopeObject`, which computes the surface as an ABSOLUTE value — `d0 = obY(a0) - heightmapByte` with no baseline subtraction (`docs/s1disasm/_incObj/sub PlatformObject.asm:150-152`). The non-zero baseline pushed the engine's sampled seesaw top surface 8px below ROM's, so a player falling onto the seesaw fell ~8px further before the engine registered the top-solid landing, landing one frame late: at SLZ3 trace f718 ROM snaps `y_speed` to 0 and seats the player on the seesaw (status bit 3, `g_speed=x_speed=-0168`), while the engine kept falling (`y_speed=0x0610`, airborne) until f719. Matching the sibling `SlopeObject` user (`Sonic1CollapsingLedgeObjectInstance`, which already returns baseline 0 for the same ROM reason) realigns the landing frame. Advances the S1 SLZ3 complete-run frontier **f718 -> f745** (1073 -> 916 errors; a residual seesaw-tilt re-seat 1-frame phase blip at f745/f756 then a deeper player-physics root at f786 are the new frontier). Object-local to Obj 5E (SLZ-only spawn). Regression-free: SLZ1 (f2872) and SLZ2 (f1714) byte-identical, GHZ1/GHZ2/SYZ2 stay GREEN, and `TestSonic1LargeGrassyPlatformObjectInstance`, `TestSeesawBallGraphRewind`, `TestS1SlzBossSpikeballGraphRewind`, `TestCollisionLogic` all pass.
- **S1 LR spring control lock counts down only on grounded frames (SLZ/SYZ):** the S1 horizontal spring (Obj 41, `Sonic1SpringObjectInstance.applyHorizontalSpring`) now drives the 15-frame D-pad control lock through the player's `moveLockTimer` instead of the bespoke `springingFrames` countdown. ROM `Spring_BounceLR` writes `move.w #15,locktime(a1)` (`docs/s1disasm/_incObj/41 Springs.asm:145`) — `locktime` (objoff_3E) is the same RAM word S2's horizontal spring writes as `move_lock` (`docs/s2disasm/s2.asm:34031`, `loc_18B1C`). ROM only decrements `locktime` on grounded frames, inside `Sonic_SlopeRepel` (`docs/s1disasm/_incObj/01 Sonic.asm:1383,1410`), which `Sonic_MdNormal`/`Sonic_MdRoll` call but the airborne modes (`Sonic_MdJump`/`MdJump2`) do not — so while Sonic is airborne the lock is **frozen**. The engine models `locktime` as `moveLockTimer` (likewise decremented only in grounded `doSlopeRepel()`), but the S1 spring set `springingFrames`, which `tickStatus()` decrements every frame including airborne. When an LR spring launches Sonic off a ledge into the air the bespoke lock expired several frames early, so the engine started applying D-pad deceleration before ROM did. At S1 SLZ2 trace f1714 the LR spring fired at f06A2 (lock=15) and Sonic was airborne for 6 frames (f06AA-f06AF) that must freeze the lock; ROM still had the lock active at f06B2 and ignored the just-pressed Right input (inertia held at F06C), while the engine's lock had already expired and applied the 0x80 Right-deceleration (F06C->F0EC, x 0x15F2 vs 0x15F3). Driving the lock through `moveLockTimer` restores the grounded-only decrement so the post-launch lock window matches ROM frame-for-frame; `springing` is kept for the air-spring animation / carry marker consumed elsewhere. Advances the S1 SLZ2 complete-run frontier **f1714 -> f2552** (215 -> 137 errors). Object-local to Obj 41's horizontal-spring path (only `Spring_LR` sets `locktime`; vertical/down springs and S2/S3K springs, which write `move_lock` directly, are untouched). Regression-free: GHZ1/GHZ2/SBZ3/SYZ2 stay GREEN, every other S1 complete-run frontier frame/field is byte-identical (SYZ1 stays at f816, +9 downstream-cascade errors in an already-red trace), `TestSonic1SpringObjectInstance` (2/0) and `TestSpringObjectInstance` (10/0) pass, and the S3K must-keep-green tests (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`) pass.
- **S1 type-03 platform fall-timer starts on the landing frame (GHZ/SLZ/SYZ):** the Obj 18 fall-on-stand platform (type 03, `Sonic1PlatformObjectInstance`) now starts its 30-frame countdown on the frame Sonic first lands, matching ROM. ROM `Plat_Solid` (routine 2) calls `PlatformObject` which sets the standing bit (`bset #3,obStatus(a0)`) then falls through to `Plat_Action`, which calls `Plat_Move`; `.type03` (`docs/s1disasm/_incObj/18 Platforms.asm:201-216`) reads the just-set standing bit and writes `objoff_3A=30` on that same frame. The engine's `moveFallOnStand()` read the PREVIOUS frame's `playerStanding` value (false on the landing frame, because `checkpointAll()` hadn't run yet), so the timer was initialised one frame late, the platform transitioned to falling one frame late, and MvSonicOnPtfm2 held Sonic one frame too long on the surface — in GHZ1 the platform at `x=0x13A0, baseY=0x0188, subtype=03` held Sonic 1px above his ROM Y at trace f3246. Fix: after `checkpointAll()` updates `playerStanding`, detect the fresh first-time standing and set `timer = FALL_STAND_DELAY` if not already set. Greens the S1 GHZ1 complete-run (255 -> 0 errors); zero regressions across all other S1 complete-runs (GHZ2/SYZ2 stay GREEN, SLZ2 stays at f1493, all remaining traces unchanged). Object-local to Obj 18 (shared across GHZ/SLZ/SYZ acts).
- **S1 SpikedPoleHelix animCounter phase and hurt-direction source X (GHZ):** two fixes to the GHZ Spiked Pole Helix (Obj 17, `Sonic1SpikedPoleHelixObjectInstance`). (1) *Phase fix (f4650 -> f5043):* derives `v_ani0_frame` from the trace-seeded gfc each update instead of a per-object unseeded counter. ROM `Hel_RotateSpikes` (`docs/s1disasm/_incObj/17 GHZ Spiked Pole Helix.asm:95-105`) reads the GLOBAL `v_ani0_frame`. (2) *animCounter off-by-one (f5043 -> f6464):* ROM `Level_MainLoop` runs `ExecuteObjects` BEFORE `SynchroAnimate` (`docs/s1disasm/sonic.asm:2988 vs 3010`), so objects at gfc=N read the value from after N-1 SynchroAnimate calls. The correct formula is `(-((gfc+10)/12)) & 7` (not `+11`); the old formula applied one extra tick at multiples of 12, marking a spike harmful 1 trace-frame early. (3) *Hurt-direction source X:* ROM `HurtSonic.checkDirection` (`docs/s1disasm/_incObj/Sonic ReactToItem.asm:402-405`) compares Sonic's X against the individual child spike's `obX(a2)`, not the parent helix X. Engine `applyHurt` was using `instance.getX()` (parent at 0x1688 instead of spike at 0x16C8), reversing the bounce direction. Fix: `TouchResponseResult` now carries a `regionX` from `processMultiRegionTouch`; `applyHurt` uses it when `hasRegionX()`. Cumulatively advances the S1 GHZ3 complete-run frontier **f4650 -> f6464**; object-local to Obj 17 (GHZ-only). Regression-free: GHZ1/GHZ2/SYZ2 stay GREEN, all unit tests pass.
- **S1 SmashableWall smash x-adjust preserves sub-pixel (GHZ/SLZ):** the GHZ/SLZ smashable wall (Obj 3C, `Sonic1BreakableWallObjectInstance`) now applies its post-smash ±4px x adjustment via `player.shiftX(±4)` instead of `player.setCentreX(value)`. ROM `Smash_Solid` (`docs/s1disasm/_incObj/3C GHZ, SLZ Smashable Wall.asm` lines 57-64) adjusts the player position with `addq.w #4,obX(a1)` / `subq.w #8,obX(a1)` — 68000 word writes that modify ONLY `obX` (the integer pixel word), leaving `obSubX` (sub-pixel) intact. `setCentreX(short)` clears the sub-pixel to 0, while `shiftX(int)` adds to the pixel integer only, preserving the fractional accumulation (per `AbstractSprite.shiftX`). At GHZ3 trace f2691 (the smash frame), Sonic's sub-pixel was 0x1200 after the `SolidObject` snap; the engine zeroed it to 0x0000. Over the next two frames the missing 0x1200 sub-pixel meant the carry bit didn't fire at f2693 (engine sub 0x7C00 + 0x7600 = 0xF200 no-carry vs ROM 0x8E00 + 0x7600 = 0x10400 carry), leaving the integer x 1px short (0x083B vs ROM 0x083C). Advances the S1 GHZ3 complete-run frontier **f2693 -> f4650** (477 -> 363 errors); object-local to Obj 3C. Regression-free: GHZ1/GHZ2/SYZ2 stay GREEN, SLZ2 stays at f1493, all other S1 frontiers byte-identical. `TestSonic1BreakableWallObjectInstance` 2/0 pass.
- **S1 Staircase 1px seat-height + timer countdown fidelity (SLZ):** riding the SLZ Staircase (Obj 5B) now seats Sonic at the correct Y. Three bugs stacked on the staircase interaction. (1) `Stair_Type00`/`Stair_Type02` (`docs/s1disasm/_incObj/5B SLZ Staircase.asm:104-119, 122-137`): when the timer is first set the ROM routine falls through to `locret_10FBE` (rts) without decrementing — the engine ran both SET and DECREMENT in the same `update()` call, advancing the countdown 1 frame early. Fixed by `return` after `timer = DELAY` in `Sonic1StaircaseObjectInstance.updateType00` and `updateType02`. (2) Non-riding sibling pieces were applying `Solid_Landed` Y snaps for overlapping X ranges, overriding the ridden-piece re-seat. In ROM, each piece occupies a separate SST slot; the ridden piece (highest slot) runs last and its `MvSonicOnPtfm`/`SolidObject` result is authoritative. Fixed by tracking `ridingCentreYToRestore` from `processInlineRidingObject` and restoring it after the sibling-piece pass in `ObjectSolidContactController.processMultiPieceCollision`. (3) `processMultiPieceCollision` used `groundHalfHeight=17` for grounded players in the Y bounding-box check, but ROM `SolidObject_cont` (`docs/s1disasm/_incObj/sub SolidObject.asm:170-176`; S2 equivalent `s2.asm:35361-35373`) always uses `d2 = airHalfHeight = 16` for the Y detection window (`add.w y_radius, d2`). Using `groundHalfHeight` inflated `maxTop` by 1, giving `distY=4` (engine) vs `distY=3` (ROM) and snapping Sonic 1px too low. Fixed by using `params.airHalfHeight()` unconditionally in `processMultiPieceCollision` (correct for S1/S2/S3K — all three ROM `SolidObject` variants pass the top half-height in d2 for Y detection; `groundHalfHeight`/d3 is only for `MvSonicOnPtfm` continued-ride re-seat). Advances S1 SLZ1 frontier **f933 -> f2872** (246 -> 164 errors). GHZ2 and SYZ2 stay green; all other S1 frontiers byte-identical. Object-local staircase changes; `processMultiPieceCollision` fix is shared but regression-free across S1/S2/S3K multi-piece objects.
- **S1 Orbinaut satellite orbit uses ROM CalcSine integer arithmetic (LZ/SLZ/SBZ):** the Orbinaut satellite spike (Obj 60, `OrbSpikeObjectInstance`) now computes its orbit position via `TrigLookupTable.sinHex`/`cosHex` with an arithmetic right-shift of 4 (`>> 4`), matching ROM `Orb_CircleSpikeball` (`docs/s1disasm/_incObj/60 Badnik - Orbinaut.asm:181-191`): `jsr CalcSine` → `asr.w #4,d1` (cosine component) → `add.w obX(a1),d1` → `obX = d1`; same for sine/Y. The engine previously used `Math.round(Math.cos(radians) * 16.0)` — floating-point rounds 254/16 = 15.875 up to 16, placing a satellite 1 px lower than ROM's `254 >> 4 = 15`. At SLZ2 trace f1016, this 1px Y difference moved the satellite's bottom edge from ROM's 0x00BB to the engine's 0x00BC, aligning it with the player's touch-box top edge at 0x00BC and triggering a premature HURT touch that ROM avoided entirely. Advances the S1 SLZ2 complete-run frontier **f1016 -> f1493** (221 errors before; 277 errors after — different cascade). Object-local to `OrbSpikeObjectInstance`; S1-only (S3K's Orbinaut is a distinct class that already uses integer trig). Regression-free: SYZ2 and GHZ2 stay GREEN, all LZ/SBZ complete-run frontier frames unchanged (LZ1 f5745, LZ2 f1068, LZ3 f6517, SBZ1 f2268, SBZ2 f1395), and `TestSonic1CaterkillerBodyChaining`, `CollisionSystemTest`, `TestSolidRoutineProfiles`, `TestSonic1SpringObjectInstance`, `TestOrbinautBadnikInstance` (S3K) all pass.
- **S1 platform walk-off Y re-seat (SYZ/all moving-platform zones):** when Sonic walks off an S1 moving platform (Obj 18), moving block (Obj 52), or SLZ elevator (Obj 59), the engine now re-seats his Y to the platform's post-move surface on the exit frame, matching ROM. ROM Obj 18 routine 4 (`Plat_Action2`) runs `ExitPlatform` (clears the on-object bit) and then **unconditionally** `MvSonicOnPtfm2` (`docs/s1disasm/_incObj/18 Platforms.asm:74-87`), which sets both Sonic's X (the post-move carry) **and** his Y (`obY = platform_Y - 9 - obHeight`, `sub MvSonicOnPtfm.asm:18-41`). The engine's `ObjectSolidContactController` exit block (`carriesAirborneRiderAfterExitPlatform`) already carried X but skipped the Y re-seat, so when a platform moved vertically on the exit frame (e.g. the Obj 18 `Plat_Nudge` bob) the rider held the pre-move surface Y and ended up 1px off ROM. S1 SYZ3 f3476: the platform bobbed 02DC->02DD on the walk-off frame; ROM re-seated the rider to centre 0x02C1, the engine kept 0x02C0. Adding the flat-surface exit re-seat (guarded so the existing sloped-exit re-seat still owns the slope case) advances the S1 SYZ3 complete-run frontier **f3476 -> f6065** (485 -> 483 errors) and greens the S1 SYZ2 complete-run (which was blocked at f6845 by the same exit-frame fidelity gap). Shared `ObjectSolidContactController` change but gated to the three objects that opt into `carriesAirborneRiderAfterExitPlatform` (all of whose ROM equivalents call `MvSonicOnPtfm2` unconditionally on exit). Regression-free: every other S1 complete-run byte-identical, GHZ2/SBZ3 stay green, S2 EHZ1 + S3K AIZ unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles` plus the S1 platform/moving-block/elevator unit tests (`TestSonic1PlatformObjectInstanceRespawn`, `TestSonic1LargeGrassyPlatformObjectInstance`, `TestS1SwingingPlatformSurfaceRegression`, `TestSonic1MovingBlockObjectInstance`, `TestSonic1ElevatorObjectInstance`, `TestS1JumpFromElevator`) all pass.
- **S1 LR spring right-edge solidity (SYZ/all zones):** the S1 spring (Obj 41, `Sonic1SpringObjectInstance`) now uses an inclusive right edge in its `SolidRoutineProfile` (`fullSolid(sticky, inclusiveRightEdge=true, false)`). ROM spring routines call `SolidObject`, whose x-range check `Solid_ChkCollision` (`docs/s1disasm/_incObj/sub SolidObject.asm:160-166`) rejects only when `d0 > 2*halfWidth` (`cmp.w d3,d0; bhi.w Solid_NoCollision`), so the right edge — Sonic's solid body exactly flush against the object's right face — still collides. The engine's contact x-range gate excluded that boundary (`relX >= 2*halfWidth` rejects), so a Sonic falling flush against the right side of a horizontal (LR) spring was treated as out of range, the spring's side contact never fired, and `Spring_LR` never set the push bit to bounce him — Sonic fell to the terrain below instead of launching. S1 SYZ1 f502: the LR spring at `@0218` has its right solid edge at `0218 + 19 = 022B`, exactly Sonic's centre x, so `relX == 2*halfWidth` was rejected. The `inclusiveRightEdge` flag already exists (used by `Sonic1GirderBlock`/`Junction`/`PushBlock`/`InvisibleBarrier`); the spring used the single-arg `fullSolid` which defaults it false. Advances the S1 SYZ1 complete-run frontier **f502 -> f816** (484 -> 351 errors); object-local to Obj 41. Regression-free: every other S1 complete-run is byte-identical, GHZ2/SBZ3 stay green, S2 EHZ1 + S3K AIZ unchanged, and `CollisionSystemTest`/`TestSolidRoutineProfiles`/`TestHtzSpringLoop`/`TestSonic1SpringObjectInstance` (assertion updated for the new inclusive edge) all pass.
- **S1 Electrocuter zap frame-counter source (SBZ):** the SBZ Electrocuter (Obj 6E, `Sonic1ElectrocuterObjectInstance`) now reads ROM `v_framecount` for its `(v_framecount & elec_freq) == 0` zap gate from the trace-seeded `Level_frame_counter` (`LevelManager.frameCounter + 1`) instead of `ObjectManager.getFrameCounter()`. ROM `Elec_Shock` (`docs/s1disasm/_incObj/6E SBZ Electrocuter.asm`) only sets the `col_144x16|col_hurt` ($A4) HURT box on the zap animation's frame 4. The engine's elec timing was correct relative to `v_framecount`, but `ObjectManager`'s own frame counter is free-running and is not seeded from the trace on replay (only `LevelManager`/`Sprites` are), so it ran one ahead of ROM `v_framecount` — making the frame-4 hurt fire one trace-frame early and zapping the rolling player at SBZ1 trace f1925 (ROM zaps him a frame later at f1926). `LevelManager.frameCounter` is the engine's canonical `Level_frame_counter`; objects execute with `frameCounter + 1` (pre-increment), so `getFrameCounter() + 1` is the current frame's `v_framecount`. (The elec's `update(frameCounter, …)` argument is the VBla clock for S1 objects and cannot be used.) Advances the S1 SBZ1 complete-run frontier **f1925 -> f2268** (997 -> 805 errors); object-local to Obj 6E (SBZ-only). Regression-free: SBZ2 (f1395) byte-identical, SBZ3 green, GHZ1/GHZ2/MZ1/SLZ1 and S2 EHZ1 + S3K AIZ unchanged.
- **S1 off-screen self-delete badnik respawn (MZ/SYZ):** an S1 object that owns its `out_of_range` tail (sets `usesCustomOutOfRangeCheck()` with a no-op `isCustomOutOfRange`) and deletes itself off-screen via `setDestroyedByOffscreen()` — e.g. the Caterkiller (Obj 0x78) — now clears its counter-based respawn-table bit so the placement cursor re-spawns it when the player returns, matching ROM. `ObjectManager`'s destroyed-removal path previously only called `placement.clearStayActive(spawn)` and left the spawn's respawn counter latched, so such a badnik never came back: once its head walked off-screen it was gone permanently. The new `resetRespawnStateForOffscreenSelfDelete` helper mirrors ROM `Cat_Despawn` (`docs/s1disasm/_incObj/78 Badnik - Caterkiller.asm:139-148` `bclr #7,2(a2,d0.w)` into the `v_objstate` respawn table) by calling `placement.clearCounterForSpawn` + `markDormant` — exactly what `unloadCounterBasedOutOfRange` already does for the standard path — but only for off-screen self-deletes (`isDestroyedRespawnable()`); player kills keep the bit set and must not respawn. In MZ2 the ROM respawns the Caterkiller (head walking left to x=0x0414) as Sonic returns and rolls into it; ROM `React_BadnikHit` then negates the falling rolling player's y_vel (`neg.w obVelY` -> -0x568) to bounce him up, but the engine had no badnik there (it never respawned after despawning ~f1065) so Sonic fell through to terrain. Verified by BizHawk (`tools/bizhawk/mz2_cat_scan.lua`: ROM Caterkiller slots 0x37-0x3A present and walking at f2578; engine had zero objects near Sonic). Gated on `placement.isCounterBasedRespawn()` so it is S1-only (S2/S3K use the non-counter placement path and are untouched). Advances the S1 MZ2 complete-run frontier **f2578 -> f2819**; all other S1 complete-runs (GHZ1/GHZ3/MZ1/MZ3/LZ1/SBZ1/SYZ1) byte-identical, GHZ2 stays GREEN, and the placement/respawn/Caterkiller/solid-routine unit tests pass.
- **S1 GHZ collapsing-ledge top-landing width (GHZ):** the GHZ collapsing ledge (Obj 1A, `Sonic1CollapsingLedgeObjectInstance`) now opts into `usesCollisionHalfWidthForTopLanding()`, so a player falling onto it lands across the ledge's full `PLATFORM_HALF_WIDTH` (0x30) rather than the generic `SolidObjectFull` `-$B`-narrowed width (0x25). ROM `Ledge_ChkTouch` passes `#96/2` (= 0x30) directly as `SlopeObject`'s `d1` (`docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:31-33`), and `SlopeObject` runs its X-range check on that `d1` with no narrowing (`docs/s1disasm/_incObj/sub PlatformObject.asm:133-139`); the engine was applying the S2 `Solid_Landed` `obActWid` narrowing that does not exist on this S1 path. Without the override, a player falling onto the ledge near its left edge was rejected as out-of-landing-width for several frames and overshot the landing: s1_ghz1 f2790 (Sonic at relX=2, distY=6 already inside the land band) was rejected until relX=12 at f2793, so the engine dropped Sonic airborne 3 frames longer than ROM (which lands him on the second collapsing ledge at f2790), cascading a player-trajectory divergence forward. Matches the sibling collapsing FLOOR (`Sonic1CollapsingFloorObjectInstance`) which already opts in for the same ROM reason. Advances the S1 GHZ1 complete-run frontier **f2790 -> f3246** (436 -> 255 errors); object-local to Obj1A (GHZ-only spawn). Regression-free: GHZ2 stays GREEN, every other S1 complete-run + credits, S2 EHZ1/CPZ, and S3K AIZ/ICZ/LBZ are byte-identical, and the camera unit tests (TestCamera/TestLookScrollDelay/TestCameraRewindSnapshot/TestSonic3kCnzScroll) pass.
- **S1 Walking Bomb fuse spawn-frame timer (SLZ/SBZ):** the S1 Walking Bomb fuse (Obj5F sub4, `Sonic1BombFuseInstance`) no longer decrements its `bom_time` countdown on the frame it is spawned. ROM `Bom_CheckStartFuse` creates the fuse via `FindNextFreeObj` and sets `bom_time=143`, but the new slot is not run by `ExecuteObjects` on its creation frame, so the fuse holds `143` that frame (BizHawk: SLZ1 bk2 f137203 ends with `bom_time=143`) and only begins counting down the next frame, expiring when `0 -> -1` (`subq.w #1,bom_time; bmi`, `docs/s1disasm/_incObj/5F Badnik - Walking Bomb.asm`). The engine spawned the fuse through the same-frame-exec path, so it decremented `143 -> 142` on its creation frame and expired one frame early, spawning the 4 shrapnel one frame early; the whole ~38-frame shrapnel flight shifted back one frame and hurt the (ducking) player at SLZ1 trace f723 instead of ROM's f724. Marking the fuse `skipsSameFrameUpdateAfterSpawn()` defers its first update so it holds the spawn-frame `bom_time`, matching ROM. Advances the S1 SLZ1 complete-run frontier **f723 -> f933** (661 -> 246 errors); SBZ2 (which also uses the Walking Bomb) improves 1035 -> 1000 errors at the same first-error frame; all other S1 complete-runs, S2 EHZ1, and S3K AIZ are byte-identical, GHZ2/SBZ3 stay green. Object-local to the S1 Walking Bomb fuse.
- **DEZ Death Egg Robot group-animation end-marker frame (DEZ):** the Death Egg Robot (Obj C7) boss group-animation player (`stepGroupAnimation`) now spends the ROM `$C0` end-marker frame before reporting a script complete. ROM `ObjC7_GroupAni` / `loc_3E1AA` reads `anim_frame` at the start of each frame: it plays the last real keyframe's final substep on frame N (advancing `anim_frame` to the `$C0` entry), then on frame N+1 reads `$C0`, runs the end handler (`loc_3E23E` -> `loc_3E27E` -> `loc_3E236` `clr anim_frame` / `moveq #1,d1`) and returns done without applying deltas — so completion costs one extra frame after the last keyframe's substeps finish (e.g. `off_3E3D0` crouch = `0,1,2,$C0` is 41 frames, not 40). The engine's keyframe sequences omit the `$C0` marker and returned done on the same frame the last keyframe finished, so every group-anim-gated attack-phase transition (crouch, walk-punch, stand-up walk) advanced one frame early, drifting the whole DEZ attack clock — by the f4007 jet-stomp the targeting-sensor lock-on snapped 7-8 frames early, so the descent stomped ~7 px right of ROM and the player's roll-up missed the boss-body bounce box. Modeling the end-marker frame realigns the attack clock: the f4007 jet-stomp bounce now connects (player x_vel/y_vel negate to match ROM). Object-local to `Sonic2DeathEggRobotInstance` (boss spawns only in DEZ). Collapses the S2 DEZ1 trace from 127 errors -> 98 and advances its frontier f4007 -> f4933 (a later attack still carries a residual 1-frame sensor-report drift); no other trace is affected.
- **DEZ Silver Sonic does not clear Current_Boss_ID (DEZ):** the DEZ Silver Sonic / Mecha Sonic (Obj AF) no longer clears `Current_Boss_ID` to 0 on defeat. ROM `loc_397BA` sets `Current_Boss_ID=9` when the Silver Sonic fight locks its arena (`s2.asm:77528`) and **no S2 boss ever writes `move.b #0,(Current_Boss_ID).w`** — it stays 9 through the Death Egg Robot fight that follows in the same act, so `Sonic_LevelBound` keeps the boss-strict right player boundary `Camera_Max_X + $128` with no `+$40` lenient extension (`s2.asm:37244-37251`). The engine cleared the boss id on Silver Sonic defeat, reverting the player's right boundary to the lenient `+$40` (0x8A8 vs ROM's 0x868) for the entire Death Egg Robot fight, so the player ran past the DEZ arena's right edge instead of stopping. Removing the premature clear restores the strict boundary; advances the S2 DEZ1 frontier f4933 (`x_speed`, player not clamped at 0x868) -> f5261 (roots 13 -> 5, all remaining a residual boss-bounce sensor-report drift). DEZ-only object; the ending walk (`Camera_Max_X` opens to 0x1000) stays unblocked (target 0xEC0 < 0x1000+$128).
- **DEZ Death Egg Robot jet-stomp reads the targeting sensor one frame late (DEZ):** the Death Egg Robot jet-stomp now observes the targeting sensor's reported X one frame after the sensor produces it, matching ROM object-slot ordering. ROM `ObjC7` `loc_3D784` reads the body's `objoff_28` (the sensor target) at the start of the body's frame, but `ChildObjC7_TargettingSensor` is a separate object in a higher RAM slot, so it runs AFTER the body in `ExecuteObjects` and writes `objoff_28` only on its lock-on-report frame (`loc_3DE62`) — the body sees the report the NEXT frame. The engine updated the sensor child inline inside the body's wait phase and read `targetedPlayerX` the same frame, so the descent began one frame early, draining a frame from every jet-stomp's lock-on wait and re-drifting the DEZ attack clock after the `$C0` end-marker fix. Capturing `targetedPlayerX` before advancing the sensor (then advancing it for next frame) restores the one-frame report latency. Advances the S2 DEZ1 frontier f5261 -> f5952 and collapses errors 98 -> 46 (roots 5 -> 3); object-local to `Sonic2DeathEggRobotInstance` (DEZ-only). A small residual attack-clock drift still misses a later jet-stomp bounce (f5952).
- **Sonic 2 sidekick chaining:** trailing sidekicks now keep following the root leader while a freshly landed direct Sonic sidekick warms its delayed follow-history ring, and S2 Tails fly-in completion no longer carries the approach timer into NORMAL as a false Player 2 manual-control pause.
- **Sidekick CPU control-counter parity:** the CPU sidekick now models ROM `Tails_control_counter` ($F702) strictly as the manual-control timer (set to 600 on Player 2 input and counted down, never incremented per frame), separated from the engine's internal multi-sidekick approach/spawn frame counter. This removes the spurious per-frame counter increment during CPU fly-in, clearing the S2 ARZ1 trace and advancing the MTZ1/HTZ1/MTZ3/CNZ1 frontiers.
- **CPZ Spiny spike timing:** the Spiny (Obj A5) spike projectile now spends one extra init-phase stationary frame so its trajectory aligns with ROM `Obj98_SpinyShotFall` frame-for-frame in the engine's object-execution phase, instead of arriving one frame early and hurting the co-located CPU sidekick a frame before ROM. Advances the S2 CPZ1 frontier substantially.
- **HCZ end-boss rewind recreate construction context:** `HczEndBossBladeSplash` and `HczEndBossBladeWaterChute` `recreateForRewind()` now create their instances through `ObjectConstructionContext.construct(ctx.objectServices(), ...)` (matching `AizEndBossInstance`), so `ObjectServices` is available when their constructors run during rewind restore. Fixes a `TestNoServicesInObjectConstructors` guard violation left by the HCZ end-boss child-codec deletion refactor.
- **S1 vertical-wrap player Y mask (LZ3/SBZ2):** the per-frame `Screen_Y_wrap_value` mask on the player's `y_pos` is now applied only for games that actually have a `Screen_Y_wrap_value` (S3K). S1/S2 have none — their LZ3/SBZ2 vertical wrap masks Sonic's Y only in the same frame the camera crosses the wrap boundary (ROM `ScrollVertical` `SV_BottomBoundary`/`SV_TopBoundary`), which the camera already mirrors. The previous unconditional `y & 0x7FF` wrongly wrapped Sonic at `y=0x800` long before the camera reached the boundary (S1 LZ3 f466 `y` 0x0807→0x0007). Advances the S1 LZ3 frontier ~949 frames with no other trace regressions.
- **S1 LZConveyor platform PlatformObject contact uses pre-move position (LZ):** the LZ Conveyor platform (Obj 63, `Sonic1LZConveyorObjectInstance`) now checks player contact at the platform's pre-move position, matching ROM. ROM `LCon_Platform` (routine 2, `docs/s1disasm/_incObj/63 LZ Conveyor.asm:149-153`) calls `PlatformObject` **before** `LCon_Platform_Update` (which calls `SpeedToPos`), so the contact check always sees the platform's position from before movement. The engine called `applyConveyorMovement()` first, then relied on `ObjectSolidContactController` for contact; without `usesPreUpdatePositionForSolidContact()=true` the contact was checked at the post-move position (1 px below ROM's pre-move y). At S1 LZ3 trace f6517, a group-4 platform at pre-move y=0x2BE (post-move y=0x2BD) correctly landed Sonic at y=0x2A2 in ROM (`2BE - 28 = 2A2`), but the engine reported `no-touch` at y=0x2BD (1 px short), leaving Sonic airborne with y_speed=0x0860. Also adds the full `PlatformObject`+`MvSonicOnPtfm2` solid profile to match `Sonic1PlatformObjectInstance`: `HALF_HEIGHT=9` (`MvSonicOnPtfm2 subi.w #9,d0`; `docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`), `getTopLandingSnapAdjustment()=-1` (detect at obY-8; `docs/s1disasm/_incObj/sub PlatformObject.asm:17`), `rejectsZeroDistanceTopSolidLanding()` (UNSIGNED `blo #-16`; `asm:21-22`), `usesCollisionHalfWidthForTopLanding()` (`LCon_Platform` passes `obActWid` directly to `PlatformObject` as d1; `asm:150-152`), and `carriesAirborneRiderAfterExitPlatform()` (`LCon_OnPlatform` calls `MvSonicOnPtfm2` unconditionally; `asm:157-164`). Advances the S1 LZ3 complete-run frontier **f6517 -> f7952** (1726 -> 1778 errors at a different root); GHZ2 and SYZ2 stay GREEN, all other S1 complete-runs byte-identical.
- **S3K lightning-shield attracted-ring give timing (MGZ):** an attracted ring (lightning shield magnetism) now tests its give-ring overlap against its position from before the current frame's `AttractedRing_Move`, matching ROM `Obj_Attracted_Ring` (sonic3k.asm loc_1A88C/loc_1A8C6): the ring moves and adds itself to the collision-response list, which the player's touch pass processes the *next* frame, so the ring is given based on its previous-frame position. The engine had tested the overlap after the move, collecting one frame early. Fixes the S3K MGZ +1-ring divergence (rings collected a frame early), advancing the MGZ complete-run and level-select rings frontier by tens of thousands of frames (MGZ level-select f539 -> f33271, now blocked by a pre-existing trace input-alignment limit) with no other S3K (or S1/S2) trace regressions; inert for S1/S2 which have no lightning shield.
- **S1 collapsing-ledge exact-touch landing (GHZ):** the GHZ collapsing ledge (Obj 1A) now rejects a zero-distance (exact-touch) top-solid landing, matching ROM `Plat_NoXCheck_AltY` (sonic.lst 0x7B00-0x7B0A). The land band uses the unsigned `cmpi.w #-16,d0` / `blo` trick, which excludes `d0=0` — so ROM lands only on strict penetration `d0 in [-16,-1]`, not at the exact touch frame. Verified by BizHawk capture (GHZ1-CR BK2 3361 `d0=0` keeps falling; BK2 3362 `d0=-9` lands); the engine had been landing one frame early. Advances the S1 GHZ1 complete-run frontier f2573->f2790 with no GHZ2/GHZ3/MZ1 trace regressions.
- **S1 platform new-landing detection surface (GHZ/SLZ/SYZ):** the generic platform (Obj 18) new-landing detection now uses ROM `Plat_NoXCheck`'s entry surface `obY-8` (`subq #8`) instead of the riding surface `obY-9`. The detection band in `ObjectSolidContactController` now applies `getTopLandingSnapAdjustment` to new-landing (sticky=false) `distY` so the detect surface matches the snap surface, while continued riding keeps `obY-9` (MvSonicOnPtfm2) — previously the engine snapped to the right position but detected the landing one frame early. With the strict-penetration reject (unsigned `blo #-16`), the platform now lands on ROM's frame. Verified by BizHawk capture (GHZ2-CR BK2 8991 `d0=0` keeps falling; BK2 8992 `d0=-5` lands). Advances S1 GHZ2 complete-run f2369->f2591 with no GHZ3 (or other S1) trace regressions; change is S1-UNIFIED-gated so S2/S3K are unaffected.
- **S1 moving-platform pre-move walk-off bounds + exit-frame carry (SYZ2 green):** the inline platform-ride walk-off bounds check now references the platform's PRE-move x_pos (the stored ride baseline `ridingX`) instead of its post-move position, for objects that opt into `usesPreUpdatePositionForSolidContact()`. ROM `ExitPlatform` (S1 Obj18 routine 4 `Plat_Action2`, `docs/s1disasm/_incObj/18 Platforms.asm:74-87`; `docs/s1disasm/_incObj/sub ExitPlatform.asm:20-27`) runs BEFORE `Plat_Move`, so its `obX(a1)-obX(a0)+width` window observes the platform where it was at the start of the frame; the rider is then carried by the post-move delta via the unconditional `MvSonicOnPtfm2` (`docs/s1disasm/_incObj/sub MvSonicOnPtfm.asm:18-41`). The engine had used the post-move x for the bounds, so a horizontally-oscillating platform sliding left under a rider dropped him one frame early (s1_syz2 f6845: SonicX 0x211C vs platform pre-move 0x20FD gives relX 0x3F < 0x40 = stays; post-move 0x20FB gives relX 0x41 = spurious exit), skipping the -2px carry and sending Sonic airborne a frame ahead of ROM. A second fix applies the final `MvSonicOnPtfm2` carry on the out-of-bounds exit frame for objects with `carriesAirborneRiderAfterExitPlatform()` (S1 Obj18/Obj52/Obj59), since ROM's `MvSonicOnPtfm2` does not test the on-object bit and still pulls the just-exited rider by the platform delta (s1_syz2 f6846: ROM x 0x211F vs un-carried 0x2120). **Greens the S1 SYZ2 complete-run trace** (`TestS1Syz2CompleteRunTraceReplay`, 55 errors -> 0). Verified regression-free: GHZ2 stays green, every other S1 complete-run (GHZ1/GHZ3/LZ1-3/MZ1-3/SBZ1-2/SLZ1-3/SYZ1/SYZ3) and the affected S3K objects' traces (CNZ-CR f1846, MGZ-CR f866) are byte-identical clean-vs-fixed. Both fixes are gated on existing provider predicates so unrelated solids are unchanged; shared S1/S2/S3K solid-contact code, no zone/game branch.
- **Headroom ceiling-probe double-flip (SYZ2 jump-under-overhang):** the player jump headroom check (`CalcRoomOverHead` quadrant 0x80, `CollisionSystem.describeCalcRoomOverHeadProbes`) no longer pre-applies the `eori.w #$F` ceiling Y-flip as a probe Y offset. ROM `Sonic_FindCeiling` (`docs/s1disasm/_incObj/sub FindNearestTile & FindFloor & FindWall.asm:361-403`) flips the top-edge probe Y once and then `FindFloor` returns a single distance `15 - (metric + (probeY & $F))` for both floor and ceiling (the `eor.w d6($1000)`/`neg.w` negates the negative ceiling height into the floor form). The engine's `Direction.UP` `scanVertical`/`calculateVerticalDistance` path already models that flip, so the probe additionally passing the flip as a `dy` offset double-applied it and computed the wrong ceiling distance. BizHawk capture (S1 SYZ2 bk2 f1088, `Sonic_FindCeiling` hook at 0x156CE/0x156D2): ROM obX=0x074F, obWidth=9, left probe column 6, **leftDist=8** for ceiling tile 0x0093 (col-6 height -2) — but the engine computed 3, so its headroom (3) fell below the 6px `Sonic_Jump` gate and blocked a jump ROM performs (headroom 8). Dropping the pre-flip makes the engine's UP path return 8, matching ROM. Advances the S1 SYZ2 complete-run frontier **f1088 -> f6845** (311 -> 55 errors), as a side effect reduces GHZ3's error count (492 -> 477, first error frame unchanged at f2693), and leaves GHZ1/LZ1/SLZ1/SLZ2/SLZ3 and S2 EHZ1/ARZ/CPZ + S3K AIZ/ICZ/LBZ + GHZ2 (stays green) byte-identical; MZ1's first-error frame is unchanged at f2089 (its total count shifts 185 -> 205 entirely within its pre-existing f4230+ physics cascade, a region already fully divergent). Shared S1/S2/S3K ceiling-probe code; game-agnostic, no zone/game branch.
- **Camera bottom-boundary follow on a non-scrolling frame (GHZ2 green):** the vertical camera now re-clamps to the bottom level boundary whenever that boundary is actively moving (`maxYChanging`), not only on frames where the grounded/airborne vertical scroll already moved the camera. ROM `ScrollVertical`'s `SV_OnGround` path consults `f_bgscrollvert` (`docs/s1disasm/_inc/ScrollHoriz & ScrollVertical.asm:148-149,157-158`): when the bottom boundary is moving this frame it branches to `SV_BottomBoundaryMoving` (line 210), which forces `d0=0` and falls through `SV_SweetSpot` -> `SV_BottomBoundary` (line 259), clamping the camera to the freshly-moved `v_limitbtm2` *even when Sonic is exactly at the sweet spot and the normal scroll produced no movement*. `DynamicLevelEvents` (`docs/s1disasm/_inc/DynamicLevelEvents.asm:5-49`) steps `v_limitbtm2` toward `v_limitbtm1` and sets `f_bgscrollvert=1` *before* `ScrollVertical` runs, so the camera follows the moving boundary on the same frame. The engine mirrors `f_bgscrollvert` with `maxYChanging` (set by `updateBoundaryEasing`, called before `updatePosition`), but its boundary clamp was gated only on `y != yBeforeVerticalScroll`, so a sweet-spot frame whose scroll produced no movement skipped the clamp and the camera lagged the rising bottom boundary by one frame. Verified by BizHawk capture (GHZ2-CR: bottom boundary eases `0x0400 -> 0x0300` under a grounded fast roll-land; ROM camera reaches `0x034A` at f3349 while the engine held `0x034C`). This is shared S1/S2/S3K `ScrollVertical` behaviour (game-agnostic, no zone/game branch). **Greens the S1 GHZ2 complete-run trace** (`TestS1Ghz2CompleteRunTraceReplay`, 2 errors -> 0) with no S1/S2/S3K trace regressions (GHZ1/GHZ3/MZ1/LZ1/SLZ1/SLZ3, S2 EHZ1/ARZ/CPZ, S3K AIZ/ICZ/LBZ complete-runs and the S3K must-keep-green suite all byte-identical or still green; camera unit tests pass).
- **S1 platform jump-off post-move re-seat (GHZ/SLZ/SYZ):** the generic platform (Obj 18) now carries an airborne rider after `ExitPlatform` on the jump-off frame, matching ROM `Plat_Action2` (routine 4, `18 Platforms.asm:74-87`) which runs `ExitPlatform` -> `Plat_Move`/`Plat_Nudge` -> unconditionally `MvSonicOnPtfm2`. `MvSonicOnPtfm2` (`sub MvSonicOnPtfm.asm:18`) does not check the rider's velocity, so on the launch frame it still pulls the player's `y_pos` to `platformY-9-obHeight` using the platform's post-move position, overwriting the `Sonic_Jump` rolling-radius adjust (`sonic.asm:1228` `addq.w #sonic_height-sonic_roll_height,obY(a0)`). `Sonic1PlatformObjectInstance` now opts into `carriesAirborneRiderAfterExitPlatform()` (matching Obj52 `Sonic1MovingBlockObjectInstance` / Obj59 `Sonic1ElevatorObjectInstance`) and resolves a single post-move solid checkpoint (mirroring the moving block) so the airborne-rider carry re-seats to the platform's new `y` rather than its pre-move `y`. Verified by BizHawk capture (GHZ2-CR: platform `obY` 0x026E->0x0270, height 0x13->0x0E, player `y_pos` 0x0252 -> 0x0257 (+5 jump adjust) -> 0x0259 (`MvSonicOnPtfm2`)); the engine had stopped at the +5 adjust, leaving the player 2 px high. Advances the S1 GHZ2 complete-run frontier f2591->f3349 (next divergence is the unrelated shared-camera vertical-settle `camera_y` cluster) and, as side effects of the same Obj18 carry, advances GHZ3 (first error f1246 `y` -> f2693 `x`) and reduces GHZ1's error count (537 -> 436, first error frame unchanged at f2790) with no other S1 trace regressions (SLZ1/SLZ3/MZ1/LZ1 byte-identical). GHZ2 is **not** green yet: two residual 2 px `camera_y` divergences (f3349/f3366) are a separate pre-existing shared-`Camera.ScrollVertical` vertical-settle-timing bug (a fast roll-land convergence rounding) that also dominates GHZ1's remaining errors.
- **S1 right-wall odd-angle snap (LZ3/CNZ):** the player ground-angle selection (`CollisionSystem.selectSensorWithAngle`) no longer keeps a non-ROM cross-frame "pending odd-sensor fallback" angle. ROM `Sonic_Angle` (docs/s1disasm/_incObj/Sonic AnglePos.asm:186-208) snaps an odd selected sensor angle straight to `(angle+0x20)&0xC0` from the *current* angle, with no cross-frame alternate-angle cache; the engine had resurrected a stale `0xD0` when the RIGHTWALL ground sensor returned zero distance + flagged angle `0xFF`, momentarily reverting the player angle one frame before the cardinal snap caught up. Removing the `pendingOddSensorFallbackAngles` cache and restoring the plain `applyAngleFromSensor` snap advances the S1 LZ3 complete-run frontier f1415 (`angle` 0x00C0 vs 0x00D0) -> f6517 (next divergence is an unrelated LZ-conveyor landing) with no S1 trace regressions; this is the same shared S1/S3K right-wall path that clears the S3K CNZ `angle` divergence (duplicate of the off-develop sibling commit `1f4fb901f`).
- **S1 collapsing-floor landing surface (MZ/SLZ/SBZ):** the collapsing floor (Obj 0x53, `Sonic1CollapsingFloorObjectInstance`) now models ROM `CFlo_ChkTouch`'s `PlatformObject` entry surface `obY-8` for new landings while keeping `CFlo_WalkOff`'s `MvSonicOnPtfm2` riding surface `obY-9` (docs/s1disasm/_incObj/1A, 53 Collapsing Ledges and Floors.asm:176-216). It opts into `getTopLandingSnapAdjustment() = -1` (recovering the `obY-8` detect/snap surface from the `obY-9` solid params), `rejectsZeroDistanceTopSolidLanding()` (ROM `Plat_NoXCheck_AltY` gates the band with the unsigned `cmpi.w #-16,d0` / `blo`, excluding the exact-touch `d0=0`), and `usesCollisionHalfWidthForTopLanding()` (`CFlo_ChkTouch` passes `#64/2` directly as PlatformObject's `d1`, so no generic `+$B` narrowing). Previously the engine modeled both surfaces at `obY-9`, so it detected the landing one frame late: ROM lands rolling on the floor then re-seats to the standing `obY-9` surface the next frame, but the engine's late landing left the player 1 px high for that frame. Verified by BizHawk capture (MZ3 BK2 45145 ROM lands at `obY=0x0491` rolling, BK2 45146 `MvSonicOnPtfm2` re-seats to `0x048C` standing; the engine landed a frame later). Advances the S1 MZ3 complete-run frontier f1702 (`y` 0x048C vs 0x048B) -> f2079 (next divergence is an unrelated jump-release `y_speed` cluster) and, as a side effect of the same landing-surface fix, advances S1 SLZ2 f651 (`g_speed`) -> f1016 with no MZ1/MZ2/SLZ3/SBZ1 (or other S1) trace regressions; the object only spawns in MZ/SLZ/SBZ.
- **CPZ spin-tube re-capture timing:** when a CPZ spin tube (Obj 1E) checks entry collision against a player already mid-traversal of another tube (`obj_control=$81`), it now reads the player's frame-start (pre-physics) centre instead of the position the owning tube already advanced this frame. This mirrors ROM `Obj1E_Main` slot ordering (s2.asm:48447-48457): the lower-slot capturing tube runs before the owning tube and so never re-captures a player on the same frame the owner steps it. Fixes the CPU sidekick double-stepping a tube frame (S2 CPZ2 f2888 `tails_x` -16 vs ROM -8), advancing the frontier with no other trace regressions.
- **S2 Rexon head oscillation stagger (HTZ):** each Rexon head (Obj97) now seeds its oscillation frame counter (ROM `objoff_39`) with its head number instead of 0, matching `Obj97_Init` (s2.asm:74316-74318). The phase/oscillation update only runs when `(objoff_39 + 1) & 3 == 0` (Obj97_Normal, s2.asm:74407-74414), so each head must advance its part of the snake wave on a different frame of the 4-frame cycle. Starting all heads at 0 collapsed that stagger and left the heads a couple of pixels off the ROM, which nudged the attackable tip head (collision_flags `0x0B`) just outside Sonic's 16x16 touch band and made the engine miss the rolling kill bounce the ROM lands (`Touch_KillEnemy` `neg.w y_vel`, s2.asm:85385). Advances the S2 HTZ2 frontier f1078 -> f1343 (`y_speed` now flips +0568 -> -0568) with HTZ1 held at its f6114 baseline and no other S2 trace regressions; the Rexon is HTZ-only.
- **CPU sidekick push-bypass auto-jump re-trigger (HTZ2):** the CPU sidekick auto-jump trigger gate now fires on its `$3F` cadence frame while pushing even when the `Tails_CPU_jumping` latch is still held, matching ROM. ROM reaches the trigger gate (`loc_13E9C` / `TailsCPU_Normal_FilterAction_Part2`) along two routes: the normal route runs the latch carry/clear (`loc_13E64` / `FilterAction`) first and is gated on the latch being absent, but the push-bypass route (Tails pushing AND delayed-Sonic not pushing) branches **directly** to the trigger gate, skipping the latch carry/clear entirely (S3K `loc_13DD0` `btst #Status_Push;beq / btst #5,d4;beq.w loc_13E9C`, sonic3k.asm:26702-26705; S2 `btst #pushing,status;beq + / btst #pushing,d4;beq.w TailsCPU_Normal_FilterAction_Part2`, s2.asm:39297-39300). The trigger gate itself never consults the latch, so on the push-bypass route it must press jump on the cadence frame regardless of latch state. The engine previously guarded the gate on `!jumpingFlag`, so a grounded sidekick pushing a solid object (e.g. the HTZ breakable block) whose latch stuck set from an earlier `$3F` jump never re-jumped, while ROM did (S2 HTZ2 f1343: dy=-10 so only the push-bypass passes the height gate). Advances the S2 HTZ2 frontier f1343 -> f3315 (next divergence is an unrelated rising-lava-platform ride) with no other S2, S3K (AIZ + complete-run byte-identical), or S1 trace regressions; the gate is shared S2/S3K CPU sidekick code driven by real push state, never a zone carve-out.
- **S3K LBZ rolling-drum chain handoff (LBZ):** entering a `LbzRollingDrum` while already riding a different drum now clears the previous drum's per-player standing flag before installing the new ride, mirroring ROM `RideObject_SetRide` (sonic3k.asm:42027) which does `bclr d6,status(a3)` on the player's prior interact object. Without this, the just-vacated drum still ran its release path the same frame, leaving its old horizontal window and forcing the player airborne — clobbering the new drum's seamless ride. Fixes the LBZ rolling-drum-chain handoff so the player stays grounded across drum-to-drum transfers, advancing the S3K LBZ complete-run frontier f1694->f1950 (`air`/`status_byte`; next divergence is an unrelated wall-push `Status_Pushing` while riding) with no AIZ (or other S3K/S1/S2) trace regressions. LBZ-only object, so no cross-zone exposure.
- **S3K MGZ/LBZ Smashing Pillar flush-edge push (LBZ/MGZ):** the MGZ/LBZ Smashing Pillar (`Obj_MGZLBZSmashingPillar`) now opts into the ROM-accurate inclusive right edge for its `SolidObjectFull` body (`MGZLBZSmashingPillarObjectInstance.usesInclusiveRightEdge()`). ROM `SolidObjectFull_1P` -> `SolidObject_cont` rejects the X bounding box with `bhi` (unsigned strictly-greater): `cmp.w d3,d0 / bhi.w loc_1E0A2` (sonic3k.asm:41405), so a grounded player shoved flush against the pillar (`d0 == width*2`) stays a live SIDE contact and the pillar re-sets `Status_Push` (loc_1E06E `bset #Status_Push`, sonic3k.asm:41500) every frame the player holds against it. The engine's default exclusive (`>=`) X gate dropped the contact at the flush edge, so the pillar never re-set push and the player's standing-still push-clear cleared it a frame later. Advances the S3K LBZ complete-run frontier f1950 -> f2270 (`status_byte`; next divergence is an unrelated downstream sidekick `tails_x` delta) with the MGZ frontiers held exactly at their develop baselines (complete-run f866, level-select f523) and no other S3K (or S1/S2) trace regressions; the object only spawns in MGZ/LBZ.
- **S3K ICZ path-follow platform balance width (ICZ):** the ICZ path-follow platform (Obj `0x89F38`) now reports its ROM `width_pixels` of `$20` for the player on-object balance check, instead of the shared 16 px default. ROM `Sonic_Move` reads `width_pixels(a1)` (sonic3k.asm:22455) for the `d1 = player_x + width - object_x` balance window, which differs from the platform's `$2B` `SolidObjectFull` X-collision half-width. The 16 px default shifted the balance window inward by 16 px, placing a still, RIGHT-facing rider on the platform's left edge and spuriously flipping its facing to LEFT (`status` bit0). Advances the S3K ICZ complete-run frontier f3116->f3139 (the next divergence is the unrelated sinking-platform `Status_Push` interaction) with no other S3K (or S1/S2) trace regressions. ROM `Sonic_Move` reads `width_pixels(a1)` (sonic3k.asm:22455) for the `d1 = player_x + width - object_x` balance window, which differs from the platform's `$2B` `SolidObjectFull` X-collision half-width. The 16 px default shifted the balance window inward by 16 px, placing a still, RIGHT-facing rider on the platform's left edge and spuriously flipping its facing to LEFT (`status` bit0). Advances the S3K ICZ complete-run frontier f3116->f3139 (the next divergence is the unrelated sinking-platform `Status_Push` interaction) with no other S3K (or S1/S2) trace regressions.
- **Playable sprite slope rendering:** slow steep-slope turnarounds now refresh the displayed slope walk frame immediately when facing changes, preventing a one-frame mismatch between vertical flip flags and the previous slope frame set.
- **S3K playable route coverage:** expanded AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and LBZ coverage with route objects, badniks, bosses/minibosses, events, camera locks, scroll/parallax, animated tiles, palette cycling, PLC state, seamless transitions, and visual/rendering fixes. AIZ through HCZ remains the primary release slice, with later zones prioritized by route blockers and trace frontiers.
- **Trace replay parity and diagnostics:** added and refined complete-run and level-select trace suites across S1, S2, and S3K; moved many first-divergence frontiers forward by modelling ROM object state, sidekick CPU state, object load/unload cadence, solid-contact behavior, pause/title-card setup, RNG/bootstrap state, and trace-entry capabilities. Trace reports now default to frontier-focused, divergent-column diagnostics with detailed evidence kept in [docs/TRACE_FRONTIER_LOG.md](docs/TRACE_FRONTIER_LOG.md).
- **Gameplay rewind:** added gameplay-scoped rewind infrastructure and expanded object restore coverage across bosses, hazards, traversal objects, cutscenes, bonus-stage objects, particles, and end-of-act flows. Rewind restore now has stronger identity handling, construction-child adoption, generic recreate hooks, coverage analysis, and round-trip guards for captured objects.
- **Lost-ring rewind generic restore:** spilled lost-ring dynamic objects now restore through `RewindRecreatable` generic recreate while preserving their shared spill-animation owner, deleting the dedicated shared lost-ring dynamic codec.
- **Shield rewind generic restore:** basic shield dynamics now restore through the generic player-bound `RewindRecreatable` path, deleting the final shared dynamic codec helper.
- **S2 CPZ rewind graph restore:** CPZ boss child graphs now restore through generic recreate with graph-level tests for identity, counts, and parent/sibling references.
- **S2 DEZ bomb rewind graph restore:** Death Egg Robot bombs now restore through graph-tested generic recreate with nearest live boss relinks while deleting the explicit bomb dynamic codec.
- **S2 ARZ arrow rewind graph restore:** ARZ boss arrows and eyes now restore through graph-tested generic recreate while deleting the explicit arrow dynamic codec.
- **S2 WFZ rewind graph restore:** WFZ boss laser walls, floating platforms, and platform hurt children now restore through graph-tested generic recreate while deleting their explicit dynamic codecs.
- **S2 HTZ rewind graph restore:** HTZ boss flamethrower and lava-ball hazards now restore through graph-tested generic recreate while deleting their explicit dynamic codecs.
- **S1/S2 seesaw ball rewind graph restore:** Seesaw ball children now restore through graph-tested parent relinks while deleting their explicit dynamic codecs.
- **S2 badnik child rewind graph restore:** Grounder, Balkiry, Rexon, Shellcracker, Slicer, and Sol dynamic child graphs now restore through generic recreate with parent/sibling relinks while deleting their explicit dynamic codecs.
- **Checkpoint/starpost rewind graph restore:** Sonic 1 lamppost twirls, Sonic 2 checkpoint dongles/stars, and S3K starpost star children now restore through generic recreate with live parent relinks while deleting their explicit dynamic codecs.
- **S1 ring flash rewind graph restore:** Giant-ring flash effects now restore through graph-tested generic recreate, preserving null-parent behavior while deleting the explicit dynamic codec.
- **S1 MZ glass reflection rewind graph restore:** MZ glass-block reflection shines now restore through generic recreate with live parent relinks while deleting their explicit dynamic codec.
- **S1 FZ boss rewind graph restore:** Final Zone cylinders, plasma launcher, and plasma balls now restore through graph-tested generic recreate with boss/launcher relinks while deleting their explicit dynamic codecs.
- **S1 GHZ boss rewind graph restore:** GHZ boss wrecking balls now restore through graph-tested generic recreate with live boss relinks while deleting their explicit dynamic codec.
- **S1 SLZ boss spikeball rewind graph restore:** SLZ boss spikeballs and fragments now restore through graph-tested generic recreate while deleting their explicit dynamic codec.
- **S1 ending Sonic rewind graph restore:** ending Sonic now restores through graph-tested generic recreate with captured emerald-family references while deleting its explicit dynamic codec.
- **S1 Grass Fire rewind graph restore:** MZ grass fire walkers and stationary child flames now restore through captured platform/fire graph references while deleting the final S1 dynamic codec.
- **S1 SYZ boss block rewind graph restore:** SYZ boss blocks now restore through graph-tested generic recreate with live boss relinks while deleting their explicit dynamic codec.
- **S1 badnik child rewind graph restore:** Bomb fuses, Caterkiller body segments, and Orbinaut spike children now restore through graph-tested generic recreate with live parent relinks while deleting their explicit dynamic codecs.
- **S2 detached badnik child rewind restore:** Slicer pincers and Sol fireballs now survive rewind restore after their parent unloads, preserving detached falling/flying behavior through generic recreate.
- **S3K AIZ miniboss rewind graph restore:** AIZ miniboss body, arm, napalm, flame barrel, flame, barrel-shot, and flare children now restore through graph/session-first generic recreate, preserving multi-barrel parent/sibling links while deleting their live-reference dynamic codecs.
- **S3K AIZ end-boss rewind graph restore:** AIZ end-boss machine, ship, arm, propeller, flame, bomb, and smoke objects now restore through graph/session-first generic recreate while deleting their live-reference dynamic codecs.
- **S3K AIZ ship-bomb rewind graph restore:** AIZ2 battleship bombs now restore through graph-tested generic recreate while deleting their live-reference dynamic codec.
- **S3K AIZ spiked-log rewind graph restore:** spiked-log spike collision children now restore through graph-tested generic recreate with exact parent identity while deleting their dynamic codec.
- **S3K AIZ falling-log rewind graph restore:** falling-log log/splash pairs now restore through graph-tested generic recreate with exact bidirectional relinks while deleting the log dynamic codec.
- **S3K HCZ end-boss rewind graph restore:** HCZ end-boss ship, turbine, blade, splash, water chute, and water column children now restore through graph/session-first generic recreate while deleting their live-reference dynamic codecs.
- **S3K AIZ intro rewind graph restore:** AIZ intro biplane and wave children now restore through graph-tested generic recreate while deleting their live-reference dynamic codecs.
- **S3K badnik child rewind graph restore:** Dragonfly linked bodies, Spiker top spikes, and Turbo Spiker shells now restore through graph-tested generic recreate while deleting their dynamic codecs.
- **S3K signpost rewind graph restore:** signpost stub children now restore through graph-tested generic recreate while deleting their dynamic codec.
- **S3K LBZ1 cutscene rewind graph restore:** Knuckles range and collapse helpers now restore through graph-tested generic recreate while deleting their dynamic codecs.
- **S3K MHZ cutscene rewind graph restore:** MHZ1 door/P2 stopper and MHZ2 route-switch helpers now restore through graph-tested generic recreate with exact parent/owner relinks while deleting their dynamic codecs.
- **S3K MHZ miniboss flame rewind graph restore:** MHZ miniboss flame children now restore through graph-tested generic recreate with exact parent relinks while deleting their dynamic codec.
- **S3K MHZ miniboss escape-shard rewind graph restore:** MHZ miniboss escape shards now restore through graph-tested generic recreate with exact/compact parent relinks while deleting their dynamic codec.
- **S3K SS-entry flash rewind graph restore:** special-stage entry flash effects now restore through graph-tested generic recreate with exact parent-ring relinks while deleting their dynamic codec.
- **S3K nested hurtbox rewind graph restore:** MGZ miniboss drill arms and ICZ ice-spike hurt children now restore through graph-tested generic recreate with exact parent relinks while deleting their dynamic codecs.
- **S3K cutscene Knuckles rewind graph restore:** AIZ rock children and CNZ2 blocking walls now restore through graph-tested generic recreate with exact parent/owner relinks while deleting their dynamic codecs.
- **S3K invisible block rewind restore:** invisible blocks and invisible hurt blocks now restore through spawn-based generic recreate, clearing their rewind coverage gaps.
- **S1 invisible barrier rewind restore:** invisible barriers now restore through spawn-based generic recreate, clearing their rewind coverage gaps.
- **S1 badnik rewind restore:** Ball Hog, Batbrain, Burrobot, and Crabmeat now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 badnik rewind restore:** Bomb, Buzz Bomber, Motobug, Roller, and Yadrin now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 badnik rewind restore:** Chopper, Jaws, and Newtron now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S1 static object rewind restore:** Flapping Door, Pylon, Purple Rock, Spinning Light, Waterfall, and Waterfall Sound now restore through spawn-based generic recreate coverage.
- **S1 simple object rewind restore:** Harpoon, Hidden Bonus, MZ Brick, Pole That Breaks, and Scenery now restore through spawn-based generic recreate coverage.
- **S1 hazard/platform rewind restore:** Big Spiked Ball, Electrocuter, Saw, Small Door, and Vanishing Platform now restore through spawn-based generic recreate coverage.
- **S1 hazard/conveyor rewind restore:** Conveyor Belt, Lava Ball, Lava Tag, Spikes, and Spiked Pole Helix now restore through shared spawn-based generic recreate coverage.
- **S1 utility/hazard rewind restore:** Fan, Giant Ring, Girder Block, Lamppost, and Spring now restore through shared spawn-based generic recreate coverage.
- **S1 platform/hazard rewind restore:** Button, Elevator, Flamethrower, Labyrinth Block, and Moving Block now restore through shared spawn-based generic recreate coverage.
- **S1 platform-family rewind restore:** Platform, Spin Platform, Staircase, and Swinging Platform now restore through shared spawn-based generic recreate coverage.
- **S1 edge/monitor/conveyor rewind restore:** Edge Walls, Monitor, LZ Conveyor, and Spin Conveyor now restore through shared spawn-based generic recreate coverage.
- **S1 bridge/signpost trap rewind restore:** Bridge, Gargoyle, Lava Ball Maker, and Signpost now restore through shared spawn-based generic recreate coverage.
- **S1 destructible/spawner rewind restore:** Breakable Wall, Bubbles, Collapsing Ledge, and Smash Block parents now restore through shared spawn-based generic recreate coverage.
- **S1 stomper/push/ending rewind restore:** Chained Stomper, Push Block, and ending STH text now restore through generic recreate coverage.
- **Shared helper rewind restore:** animal, explosion, and skid-dust dynamic objects now restore through generic recreate with restore-time services, deleting their shared private dynamic codec helpers.
- **Shared signpost sparkle rewind restore:** signpost sparkle dynamics now restore through generic recreate with compact scalar position restore, deleting the shared exact-spawn dynamic codec helper path.
- **S2 invisible block rewind restore:** invisible blocks now restore through spawn-based generic recreate, moving them onto the rewind round-trip ratchet.
- **S2 Egg Prison button rewind graph restore:** Egg-prison buttons now restore through graph-tested generic recreate with exact parent/button relinks while deleting their dynamic codec.
- **S2 OOZ burner flame rewind graph restore:** burner flame children now restore through graph-tested generic recreate with exact platform relinks while deleting their dynamic codec.
- **Gameplay rewind performance:** reduced multi-sidekick keyframe capture cost by omitting unused terminal sidekick follow-history arrays and empty sidekick touch-overlap snapshots, with a deterministic 20-sidekick rewind/replay performance trace.
- **Runtime-owned framework stack:** continued moving zone behavior onto shared runtime-owned systems for typed zone state, palette ownership, animated tile channels, live layout mutation, scroll composition, staged render effects, and frame-level render-mode overrides. Older zone-local paths remain where migration has not yet paid for itself.
- **Release hardening and architecture guards:** tightened branch/release policy hooks, trace/rewind invariants, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture source guards, and assertion-quality tests. Test-suite cleanup replaced diagnostic-only or tautological tests with behavioral oracles where they protect real release risk.
- **Player-facing systems:** added or improved S3K data select/save support, cross-game donation paths, ROM-derived master-title previews, legal-disclaimer startup flow, display shader support, pause/HUD presentation fixes, multi-sidekick behavior, level-editor plumbing, and user-facing configuration/docs.
- **S1/S2 parity uplift:** closed selected Sonic 1 and Sonic 2 trace/object/boss gaps where fixes reduced active release risk or shared-code duplication, including sidekick CPU behavior, object-slot lifetime, solid/contact parity, boss/end-sequence behavior, and level-specific traversal objects.
- **S2 SCZ/WFZ visual parity:** restored Sky Chase Tornado rider art, moved stage rings into their ROM sprite-priority bucket, applied Wing Fortress object priority bits, and corrected WFZ underside flame flicker cadence.
- **S2 CPZ spin tube control:** CPZ spin tubes now use player-local `obj_control` without asserting global `Control_Locked`, keeping logical input refresh aligned with the ROM while the tube owns movement.
- **S2 Crawl contact parity:** walking into an attacking Crawl now routes through the ROM hurt collision flags instead of silently ignoring non-rolling contact.
- **S2 Casino Night slot machine:** restored the ROM packed-target reel order so the stopped slot faces line up with the reward paid out by linked Point Pokey cages.

## v0.5.20260411 (Released 2026-04-11)

Analysis range: `v0.4.20260304..v0.5.20260411` on `develop` (`2479` commits, `2298` non-merge commits,
`1588` files changed, `477351` insertions, `28266` deletions). Net code growth is ~449,100 lines.

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe runtime teardown, and future multi-instance play-testing. Sonic 3 & Knuckles
gameplay coverage has advanced significantly across Angel Island and Hydrocity: the AIZ2 Flying
Battery bombing sequence, AIZ2 end boss, post-boss capsule/cutscene flow, AIZ-to-HCZ transition,
HCZ1 miniboss, and HCZ1-to-HCZ2 transition are now represented, alongside all three S3K bonus-stage
families in active implementation.

### Architectural Overhaul: Two-Tier Service Architecture

The engine's object model has been fundamentally restructured from direct singleton access to a
two-tier dependency injection pattern.

- **GameServices** (global tier): facade over ROM, graphics, audio, camera, level, fade, and
  configuration singletons. Accessed anywhere via `GameServices.camera()`, `GameServices.audio()`, etc.
- **ObjectServices** (context tier): injected into every game object at spawn time via
  `ObjectManager`. Provides camera, game state, zone features, sidekick access, and level queries
  scoped to the object's lifecycle. Accessed via `services()` within any `AbstractObjectInstance`.
- **ThreadLocal construction context**: `ObjectServices` is available during object construction
  without requiring constructor parameters, via a `ThreadLocal` injection pattern.
- **Migration scope**: 105 Sonic 2 object files, 50 Sonic 1 object files, 25 Sonic 3K object files,
  and 6 game-agnostic base classes migrated from `getInstance()` / `LevelManager.getInstance()` to
  `services()` or `GameServices` as appropriate. All singleton `.getInstance()` calls removed from
  object classes.
- **NoOp sentinels**: null-returning provider methods replaced with NoOp sentinel objects across
  the provider interfaces (zone features, physics, water, scroll handlers), eliminating null checks
  throughout the object layer.
- **GameId enum**: replaced string-based game identification with type-safe `GameId` enum throughout
  `CrossGameFeatureProvider` and module detection.

### Architectural Overhaul: GameRuntime and Singleton Lifecycle

- **GameRuntime**: introduced `GameRuntime` as the explicit owner of all mutable gameplay state.
  `ObjectServices` is backed by the runtime instance rather than global singletons.
- **resetState() lifecycle**: all singletons (`Camera`, `RomManager`, `GraphicsManager`,
  `AudioManager`, `CollisionSystem`, `CrossGameFeatureProvider`, `DebugOverlayManager`,
  `SonicConfigurationService`, `Sonic1ConveyorState`, `Sonic1SwitchManager`,
  `TerrainCollisionManager`) now implement `resetState()` for clean teardown without destroying
  the singleton instance. `resetInstance()` deprecated across the board.
- **Generation counter**: `GameContext` tracks a generation counter for stale reference detection.
- **SingletonResetExtension**: JUnit 5 extension with `@FullReset` annotation for automated
  per-test singleton reset, replacing manual `resetInstance()` boilerplate across 35+ test classes.
- **GameRuntime lifecycle wired into test harness**: `resetPerTest()` now creates/destroys
  `GameRuntime` for CI stability.

### Architectural Overhaul: LevelManager Decomposition

`LevelManager` (previously the largest class in the engine) has been broken into focused components:

- **LevelTilemapManager**: extracted ~18 methods and ~22 fields for tilemap rendering, chunk
  lookup, and tile-level queries.
- **LevelTransitionCoordinator**: extracted ~43 methods and ~25 fields for act transitions,
  seamless level mutation, title cards, results screens, and game mode flow.
- **LevelDebugRenderer**: extracted ~12 methods and ~14 fields for collision overlay, sensor
  display, camera bounds, and other debug visualizations.
- **LevelGeometry** and **LevelDebugContext** records introduced as data carriers between the
  decomposed components.
- Game-specific art dispatching extracted from `LevelManager` into per-game modules.

### Architectural Overhaul: Cross-Game Abstraction Hardening

Systematic removal of game-specific coupling from the engine core:

- **PlayableEntity interface**: extracted from `AbstractPlayableSprite` to decouple `level.objects`
  from `sprites.playable`. Includes `isOnObject()`, `getAnimationId()`, and all methods needed
  by game objects to interact with the player.
- **PowerUpSpawner interface**: breaks `sprites.playable` dependency on `level.objects` for
  monitor/power-up spawning.
- **DamageCause**, **GroundMode**, **ShieldType** relocated from `sprites.playable` to `game`
  package for cross-game reuse.
- **SecondaryAbility enum**: replaced `instanceof Tails` checks throughout the codebase.
- **CanonicalAnimation enum** and **AnimationTranslator**: cross-game animation vocabulary
  enabling bidirectional sprite donation between any pair of games.
- **DonorCapabilities interface**: each `GameModule` declares its donation capabilities (S1, S2,
  S3K all implemented), replacing hardcoded branches in `CrossGameFeatureProvider`.
- S1 wired as donor for forward donation into S2/S3K games.
- CNZ slot machine renderer moved to `ZoneFeatureProvider`; seamless mutation moved to
  `GameModule`; Tails tail art loading moved to `GameModule`; sidekick zone suppression moved
  from hardcoded S2 IDs to `GameModule`.
- 11 cross-game classes relocated from `sonic2` to generic packages; 5 cross-game dependency
  classes decoupled.

### Common Code Extraction (Phase 1-5)

A systematic 5-phase refactoring pass eliminated structural duplication across all three games:

- **Phase 1 — Common utilities**: `SubpixelMotion` (16.16 fixed-point gravity helpers),
  `AnimationTimer` (cyclic frame animation), `FboHelper` (centralised FBO creation),
  `PatternDecompressor.nemesis()` (eliminated private Nemesis copies), `refreshDynamicSpawn()`
  extracted into `AbstractObjectInstance`, `isOnScreen(margin)` guard migrated across all objects,
  `buildSpawnAt()` helper and `getRenderer()` helper inherited by all object classes.
- **Phase 2 — Base class extraction**: `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`
  (S2/S3K), `AbstractProjectileInstance` (S1 missiles), `AbstractPointsObjectInstance`,
  `AbstractFallingFragment` (collapsing platforms), `AbstractSoundTestCatalog`,
  `AbstractAudioProfile`, `AbstractObjectRegistry`, `AbstractZoneRegistry`,
  `AbstractZoneScrollHandler` (~20 scroll handlers migrated), `AbstractLevelInitProfile`
  (with `buildCoreSteps()`), `AniPlcScriptState` and `AniPlcParser` extracted from pattern
  animators.
- **Phase 3 — Behavior helpers**: S1 badnik migration to shared destruction config, shared ring/object
  placement record parsers, shared title card sprite rendering utility, shared S1 Eggman boss
  methods extracted into base class.
- **Phase 4 — Gravity and debris**: `GravityDebrisChild`, `PlatformBobHelper` (3 platform objects
  migrated), `ObjectFall()` method in `SubpixelMotion`.
- **Phase 5 — Cleanup**: shared constants, `loadArtTiles` path, shader path standardization,
  `ParallaxShaderProgram` extends `ShaderProgram` (deleted lifecycle duplication).
- **Debug render migration**: all S1, S2, and S3K objects migrated from legacy
  `appendDebug`/`appendLine` API to `DebugRenderContext`.

### MutableLevel (Level Editor Foundation)

- **MutableLevel**: a new level data abstraction supporting snapshot, mutation, and dirty-region
  tracking. Wraps the read-only level data and provides `setChunkDesc()`, `getGridSide()`,
  `saveState()`/`restoreState()` for undo/redo support.
- **Block**: added `saveState()`/`restoreState()` and `setChunkDesc()` for chunk-level mutation.
- **Dirty-region processing pipeline**: `processDirtyRegions()` wired into `LevelFrameStep` for
  efficient per-frame GPU updates of only the modified tile regions.
- MutableLevel preserves game-specific overrides and `ringSpriteSheet` across mutations.
- Round-trip and integration tests verify snapshot fidelity and mutation correctness.

### Sonic 3 & Knuckles Expansion

#### Knuckles: Playable Character

Knuckles is now a fully playable character with his complete S3K ability set, working natively
in S3K and via cross-game donation into S1 and S2.

- **Glide state machine**: glide activation on jump re-press with ROM-accurate turn physics
  (sine/cosine velocity from `doubleJumpProperty` angle, gravity balance). Direct mapping frame
  control using `RawAni_Knuckles_GlideTurn` table (frames 0xC0–0xC4).
- **Floor landing and sliding**: flat surfaces enter sliding state with deceleration (0x20/frame
  while jump held, matching ROM's `.continueSliding` routine). Slide follows terrain via
  `ObjectTerrainUtils` floor probing, snapping Y position to surface with correct angle. Ledge
  detection enters fall state when floor distance >= 14.
- **Wall grab and climbing**: wall grab with climbing animation cycling (frames 0xB7–0xBC every
  4 frames). Ledge climb using `Knuckles_ClimbLedge_Frames` table (4 keyframes with x/y deltas).
  Wall jump away with facing flip and normal jump animation.
- **Fall-from-glide landing**: ROM-accurate crouch pose with 15-frame `move_lock`.
- **ROM-accurate jump height**: `PhysicsProfile.SONIC_3K_KNUCKLES` with jump velocity 0x600
  (vs Sonic's 0x680), water jump 0x300 (vs 0x380), matching `Knux_Jump` in disassembly.
- **Shield ability gating**: fire/lightning/bubble shield abilities gated to Sonic only per ROM;
  Knuckles gets passive shield protection with glide as his secondary ability. Bubble shield
  bounce correctly suppressed on glide landing (gates on `SecondaryAbility.INSTA_SHIELD`, not
  `doubleJumpFlag` value).
- **Knuckles palette**: `Pal_Knuckles` (0x0A8AFC) loaded for both native S3K and cross-game
  donation. Cross-game palette fix ensures correct palette is loaded based on character config.
- **Life icon art**: `ArtNem_KnucklesLifeIcon` (0x190E4C) with character-specific rendering.
- **Sound effects**: GRAB and GLIDE_LAND SFX registered in S3K audio profile.
- **Character detection fix**: `Sonic3kLevelEventManager.getPlayerCharacter()` now resolves from
  config (was hardcoded to `SONIC_AND_TAILS`), enabling all character-gated object behaviour.
- **AIZ rock breaking**: knucklesOnly rocks (subtype bit 7) now trigger on airborne side contacts
  (jumping/gliding into them), not just grounded push.

#### S2 Cross-Game Knuckles Support

- **Lock-on palette**: "Knuckles in Sonic 2" palette loaded from S3K ROM at 0x060BEA. Only
  indices 2–5 differ from S2's `Pal_SonicTails` (Knuckles' reds vs Sonic's blues); title cards,
  badniks, and rings are unaffected. HUD text index 4 tweaked (green→orange) for readability.
- **Lives icon**: `ArtNem_KnucklesLifeIcon` decompressed from S3K donor ROM with pixel index
  remap from S3K palette layout to S2-compatible layout (`S3K_TO_S2_PALETTE_REMAP`).
- **HUD rendering**: lives name tiles use palette 0 (no flash cycling) when donor art is active,
  via `livesNameUsesIconPalette` flag in `HudRenderManager`.
- **Palette utility**: `Palette.mergeColorsFrom()` added for targeted color range copying.

#### Title Screen

- Full S3 title screen implemented with 6-phase state machine: SEGA logo with palette fade,
  12-frame Sonic morphing animation, white flash transition, and interactive menu with banner
  bounce physics, sprite animations, and menu selection.
- ROM data loading for 7 Kosinski art sets, 4 Nemesis sprite sets, 14 Enigma plane mappings.
- Hardcoded sprite mapping frames for banner, &Knuckles text, menu text, Sonic finger/wink,
  and Tails plane sprites (`Sonic3kTitleScreenMappings`).
- FadeManager transition fix: title screen exit now renders fade-to-black internally to avoid
  `GameLoop`/`UiRenderPipeline` FadeManager instance mismatch after `RuntimeManager` migration.

#### Level Select Screen

- ROM-accurate S3K level select matching the S3 disassembly menu infrastructure.
- `Sonic3kLevelSelectConstants`: data tables (level order, mark table, switch table, icon table,
  zone text, mapping offsets) from `s3.asm` with S&K zones replacing disabled/competition entries.
- `Sonic3kLevelSelectDataLoader`: loads Nemesis art (font, menu box, icons), Enigma mappings
  (screen layout, background, icons), uncompressed SONICMILES animation art, and palettes from ROM.
  Builds screen layout in-memory with S3K zone names via the LEVELSELECT codepage.
- `Sonic3kLevelSelectManager`: two-layer rendering (Plane B SONICMILES background + Plane A
  foreground), input navigation with disabled-entry skipping, sound test (0x00–0xFF), selection
  highlight, and zone icons.

#### Special Stage Character Support

- S3K Blue Ball special stages now dynamically resolve `PlayerCharacter` from config: Sonic &
  Tails (with AI sidekick), Sonic alone, Tails alone (with spinning tails appendage), and
  Knuckles (with correct palette patch to colors 8–15 per ROM's `Pal_SStage_Knux`).

#### AIZ Object Lifecycle Fixes

- **Vine dismount**: suppressed stale jump press on release to prevent immediate insta-shield
  (Sonic) or glide (Knuckles) activation. Added edge detection so holding jump from the vine-
  reaching jump doesn't cause immediate dismount.
- **Vine respawn**: removed self-destruct cull checks from both vine objects. The vine's coarse
  range was narrower than the Placement window, causing permanent respawn prevention via the
  `destroyedInWindow` latch.
- **Collapsing platform respawn**: removed `markRemembered()` call — ROM uses
  `Delete_Current_Sprite` (allows respawn), not `Remember_Sprite`. Platforms now correctly
  respawn when the player scrolls away and returns.
- **Breakable boulders**: preserved rolling state when smashing AIZ/LRZ rocks from the side,
  matching `SolidObjectFull` behaviour.
- **Special stage return**: restored saved centre coordinates correctly on Blue Ball exit,
  preventing the player from being embedded in the floor after returning from the big ring.
- **Results screen spawn path**: signpost flow now uses `spawnChild()` for the results object,
  preserving `ObjectServices` context and fixing the end-of-act bubble monitor crash.

#### AIZ Miniboss Completion
- AIZ miniboss defeat flow fully implemented: `S3kBossDefeatSignpostFlow` reusable sequence,
  staggered explosions with `S3kBossExplosionController`, per-explosion `sfx_Explode`.
- Knuckles napalm attack: `AizMinibossNapalmController` and `AizMinibossNapalmProjectile` with
  launch/drop/explode lifecycle, gated to Knuckles-only appearance.
- AIZ2 dynamic resize state machine for correct camera boundaries during miniboss spawn.

#### AIZ2 Boss and Transition Progress
- AIZ2 Flying Battery bombing sequence implemented with battleship overlay rendering, ship-relative
  bomb placement, explosion children, background tree spawners, and object-art loading for the
  bombership / small Robotnik craft frames.
- AIZ2 end boss implemented with Robotnik ship/head overlays, arm/propeller/flame/bomb/smoke child
  systems, camera scripting, boss state flow, and regression coverage for ship bomb timing.
- Post-boss capsule/cutscene flow now includes the AIZ2 egg capsule release path and handoff toward
  the Hydrocity transition. Follow-up fixes restore AIZ transition zone-feature state and prevent
  bombership art regressions after act-transition reinitialization.

#### Signpost and Results Screen
- `S3kSignpostInstance` with 5-state machine (idle/spin/slowdown/sparkle/done), stub and sparkle
  children, `PLC_EndSignStuff` art loading from ROM.
- `S3kHiddenMonitorInstance` with signpost interaction.
- Results screen: full state machine with tally, element system rendering, art loading from ROM,
  act display via `Apparent_act`, exit timing, control lock, victory pose, and Tails-specific
  victory animation.
- End-of-level flag and `endOfLevelActive` state wired through defeat flow.

#### Blue Ball Special Stages (WIP)
- Blue Ball special stage implemented (work in progress): gameplay, rendering, HUD, banner,
  ring animation, emerald collection, exit sequence.
- `SSEntryRing` art, animation, and special stage entry sequence from giant rings.
- Special stage results screen with art loading.
- Tails P2 support in special stages with tails sprite and delayed jump.
- Player returns to big ring location after special stage (not checkpoint).

#### S3K Bonus Stages: Slots, Gumball, and Glowing Sphere (WIP)
- `Sonic3kBonusStageCoordinator` now implements the S3K ring-threshold selection formula and
  zone/music routing for the three lock-on bonus stages: Slots, Glowing Sphere (Pachinko), and
  Gumball. StarPost bonus-star entry and saved-state return are wired into the S3K bonus-stage
  lifecycle.
- Bonus-stage title card support added to `Sonic3kTitleCardManager` and mappings, including the
  dedicated `BONUS STAGE` layout and bonus-specific fade timing.
- **Gumball stage bring-up:** `GumballMachineObjectInstance`, `GumballItemObjectInstance`, and
  `GumballTriangleBumperObjectInstance` implemented with ROM-driven machine state, dispenser /
  container / exit-trigger child chains, machine Y drift and slot tracking, subtype-specific item
  behavior, spring bounce/crumble parity, shield persistence, sidekick safety, and dedicated
  `SwScrlGumball` scrolling.
- **Glowing Sphere / Pachinko bring-up:** `PachinkoFlipperObjectInstance`,
  `PachinkoTriangleBumperObjectInstance`, `PachinkoBumperObjectInstance`,
  `PachinkoPlatformObjectInstance`, `PachinkoItemOrbObjectInstance`,
  `PachinkoMagnetOrbObjectInstance`, and `PachinkoEnergyTrapObjectInstance` implemented, with
  stage entry/return flow, top-exit handling, and dedicated `SwScrlPachinko` scrolling.
- **Zone animation support:** `Sonic3kPatternAnimator` and `Sonic3kPaletteCycler` now cover the
  bonus-stage-specific Gumball direct-DMA tile animation plus Pachinko animated tiles, DMA-driven
  background strips, and palette cycling.
- **Render-path parity for the gumball machine:** per-piece VDP priority from ROM mapping data,
  SAT-style sprite-mask post-processing, and replay-role metadata now preserve the intended glass /
  shell / interior pile layering for mixed-priority machine frames.
- Pachinko energy trap bootstrap now stays persistent like the ROM object, keeps its spawned
  column/beam children alive until scripted teardown, and force-releases players from competing
  magnet orbs before trap capture. Capture now zeros X/Y/G speed and cleanly holds the player on
  the beam.
- Bonus-stage title card exit no longer freezes the pachinko trap update loop. Persistent power-up
  re-registration now clears stale object slots before `ObjectManager` rebuilds, preventing slot
  aliasing during bonus-stage entry and post-title-card resume.
- **Slot Machine stage bring-up:** `S3kSlotRomData`, `S3kSlotStageController`,
  `S3kSlotStageState`, `S3kSlotCollisionSystem`, `S3kSlotPlayerRuntime`,
  `S3kSlotOptionCycleSystem`, `S3kSlotPrizeCalculator`, and reward/cage object runtime wiring now
  cover ROM table loading, rotating-stage movement, projected ground/air physics, grid collision,
  tile interactions, reel option cycling, match detection, cage capture/release, interpolated ring
  and spike rewards, exit wind-down/fade, and slot-specific sound effects.
- **Slot Machine rendering:** `S3kSlotLayoutRenderer`, `S3kSlotLayoutAnimator`,
  `S3kSlotMachineRenderer`, `S3kSlotMachinePanelAnimator`, `S3kSlotMachineDisplayState`,
  `SwScrlSlots`, and `shader_s3k_slots.glsl` implement layout animation, palette cycling,
  goal/peppermint/reel display updates, background row refresh, debug visibility, and FG glass /
  player priority ordering.
- **Slot Machine remediation:** state ownership was moved into the slot runtime with `ObjectManager`
  rendering for cage/reward objects, preserved special collision bits across probes, authoritative
  follow-up state, persistent wall animation state, capture-cycle restart coverage, and fixes for
  player swap focus, title-card bootstrap, runtime ownership, launch physics, spike reward ring
  drain, reel display, and exit fade.
- Added regression coverage for coordinator lifecycle, bonus title card mappings/flow, gumball
  machine drift and priority diagnostics, sprite-mask helper consumption and replay ordering,
  pachinko palette/pattern animation, slot ROM data, slot collision/player/runtime/rendering/reward
  systems, registry wiring, and live trap/orb/title-card integration.

#### Per-Character Physics
- Per-character physics profiles for Sonic, Tails, and Knuckles (speed, acceleration, jump height).
- Super spindash speed table and slope sprite selection fixes.
- Ducking while moving at slow speeds (S3K-specific behavior).

#### Palette and Visual Systems
- Palette cycling implemented for all remaining zones: HCZ, CNZ, ICZ, LBZ, LRZ, BPZ, CGZ, EMZ
  (plus existing AIZ).
- Per-frame palette mutation system for AIZ1 hollow tree reveal (`palette[2][15]`).
- AIZ fire curtain overlay with cached BG descriptors and fire palette fixes, looping linger and
  graceful scroll-off.
- Heat haze deformation applied to AIZ2 background layer.
- HUD text loaded from ROM; digit rendering uses mapping frames (not tile indices).

#### New Objects and Badniks
- `BreakableWall` (0x0D), `CorkFloor`, `FloatingPlatform`, `CaterkillerJr` (with body segment
  despawn), `AutoSpin`, `Falling Log`, `InvisibleBlock`, `StarPost`, `TwistedRamp`,
  `AIZCollapsingLogBridge` (0x2C), `AIZSpikedLog` (0x2E), `AIZFlippingBridge` (0x2B), and the
  zone-specific `Button` object (0x33).
- HCZ expansion: water surface, water rush sequence (`HCZBreakableBarObjectInstance`,
  `HCZWaterRushObjectInstance`, `HCZWaterWallObjectInstance`, `HCZWaterTunnelHandler`),
  `HCZConveyorBeltObjectInstance`, `HCZCGZFanObjectInstance`, `HCZHandLauncherObjectInstance`,
  `HCZLargeFanObjectInstance`, `HCZBlockObjectInstance`, `HCZConveyorSpikeObjectInstance`,
  `HCZTwistingLoopObjectInstance`, `HczMinibossInstance`, and `DoorObjectInstance` for HCZ/CNZ/DEZ.
- Additional S3K objects and badniks: `CollapsingBridgeObjectInstance`, `BubblerObjectInstance`,
  `Sonic3kInvisibleHurtBlockHObjectInstance`, `MegaChopperBadnikInstance`,
  `PoindexterBadnikInstance`, `BlastoidBadnikInstance`, `BuggernautBadnikInstance` /
  `BuggernautBabyInstance`, and `TurboSpikerBadnikInstance`.
- `Sonic3kLevelTriggerManager` added for AIZ trigger state such as boss-driven burn activation.
- All zone badnik entries populated in `Sonic3kPlcArtRegistry`.
- Initial badnik implementations wired into object system.
- **Badnik destruction effects**: destroying S3K badniks now spawns animals and floating points
  popups, matching S1/S2 behavior. Zone-specific animal pairs loaded from ROM per
  `PLCLoad_Animals_Index` (all 13 zones mapped). Enemy score art parsed from `Map_EnemyScore`
  (shared `ArtNem_EnemyPtsStarPost` blob). `Sonic3kPointsObjectInstance` provides S3K-specific
  score-to-frame mapping.

#### Spindash Dust
- **S3K spindash dust**: implemented native `SpindashDustArtProvider` for Sonic 3&K. Art loaded
  from ROM (`ArtUnc_DashDust` at 0x18A604, `Map_DashDust` at 0x18DF4, `DPLC_DashSplashDrown` at
  0x18EE2). Uses virtual pattern base 0x34000 to avoid collision with ring tiles in the atlas.
- **Multi-character dust isolation**: sidekick dust renderers now get isolated DPLC banks
  (shifted into `SIDEKICK_PATTERN_BASE + 0x2000` range), preventing atlas corruption when
  multiple characters spindash simultaneously.

#### Invincibility Stars
- **S3K invincibility stars**: `Sonic3kInvincibilityStarsObjectInstance` implements ROM-accurate
  Obj_Invincibility (sonic3k.asm:33751) with 1 parent group + 3 trailing child groups.
  Each group renders 2 sub-sprites at opposite positions on a 32-entry circular orbit table
  (`byte_189A0`). Children trail via `PlayableEntity.getCentreX/Y(framesAgo)` at 3/6/9 frames
  behind the player; parent orbits fast (9 entries/frame), children orbit slow (1 entry/frame).
  Rotation reverses when facing left. Art loaded from ROM (`ArtUnc_Invincibility` at 0x18A204,
  `Map_Invincibility` at 0x018AEA). `DefaultPowerUpSpawner` branches on `Sonic3kGameModule`
  to create the S3K variant; S1/S2 `InvincibilityStarsObjectInstance` remains unchanged.

#### Audio
- Music tempo scaling and all-spheres SFX fix.
- Ring collection sound alternates left/right channels.
- Correct SFX: `sfx_Death` for normal hurt (not `sfx_SpikeHit`), jump SFX fix.
- S3K tumble frame base corrected to `0x31` (not S2's `0x5F`).

#### Miscellaneous S3K Fixes
- VDP priority bit correctly extracted in S3K sprite mapping loader.
- Collapsing platforms stay solid during fragment phase (S2/S3K).
- Shield re-registration after act transition; StarPost bonus stage routing fix.
- AIZ1 level bounds use normal `LevelSizes` entry.
- Prevented OOM in S3K DPLC frame loading by parsing only 1P entries (combined mapping table fix).
- SONIC art address corrected; camera bounds restored after transition.
- Lightning shield sparks rendered directly instead of via DPLC.
- Save/restore `Dynamic_resize_routine` across big ring special stage transitions (ROM: `Saved2_dynamic_resize_routine`). Without this, the resize state machine restarted from routine 0 on return, rapidly re-processing boundary thresholds and causing incorrect camera locks in AIZ Act 2.
- Title card showed wrong act after AIZ mid-act fire transition. Death or special stage return displayed "Act 2" instead of "Act 1" because the engine lacked the ROM's `Apparent_zone_and_act` variable. Added `apparentAct` tracking to `LevelManager`: seamless transitions (fire) only update `currentAct`, normal act changes update both, title card requests read `apparentAct`. Results screen exit sets `apparentAct = 1` matching ROM's `move.b #1,(Apparent_act).w`.
- AIZ2 water incorrectly enabled for Knuckles on level select load. `LevelManager.initWater()` hardcoded `SONIC_AND_TAILS` instead of resolving the actual player character from the level event manager. ROM `CheckLevelForWater` (sonic3k.asm:9754-9759) gates AIZ2 water on `Player_mode` and `Apparent_zone_and_act`, disabling it for Knuckles on direct load but enabling it during seamless AIZ1→AIZ2 transitions. Both cases now handled correctly via a `seamlessTransition` flag threaded through `WaterDataProvider`.

### Insta-Shield Implementation

Full S3K insta-shield ability implemented with ROM parity:
- ROM constants, art key, and art loading (including cross-game donation path).
- Activation via `tryShieldAbility()` with character gating (Sonic only, not Tails/Knuckles).
- Hitbox expansion in `TouchResponses` for the active insta-shield frames.
- Persistent `InstaShieldObjectInstance` lifecycle (survives level transitions).
- DPLC cache invalidation on seamless level transitions.
- Lazy art initialization to handle sprite-before-level-load ordering.
- Half-arc animation bug fix (prevented double-update per frame).

### Multi-Sidekick System

- Comma-separated sidekick config enables spawning multiple sidekicks (e.g. `"sonic,tails"`).
- `SidekickRespawnStrategy` interface extracted with `TailsRespawnStrategy` and per-character
  `requiresPhysics()` (Sonic walk-in vs Tails fly-in).
- Parallel sidekick respawn via effective leader reference.
- Virtual pattern ID range validation in `PatternAtlas` for safe multi-bank allocation.
- Sidekick DPLC banks placed in dedicated `0x30000+` range, capped at `0x800` limit with bank
  sharing on overflow.
- Sidekick rendered behind main player to match VDP sprite priority order.
- Leader reference preserved across `reset()` — sidekicks no longer become permanently idle.
- Directional input maintained during approach phase.
- Slot reclamation added to `PatternAtlas` for efficient VRAM management.

#### S3K Sidekick Knuckles Fixes

- **VRAM isolation**: every sidekick now unconditionally gets its own isolated pattern bank in the
  `SIDEKICK_PATTERN_BASE` (0x38000) range, eliminating sprite corruption when characters share
  the same ART_TILE base (Knuckles and Sonic both use 0x0680 in S3K). Removed the name-based
  `computeVramSlots` optimization that missed this collision.
- **Palette isolation**: per-sidekick `RenderContext` palette blocks loaded via
  `PlayerSpriteArtProvider.loadCharacterPalette()`. When a sidekick uses a different palette
  than the main character (e.g. Knuckles' `Pal_Knuckles` vs Sonic's `Pal_SonicTails`), a
  dedicated palette context is created so the sidekick renders with correct colors. Propagated
  to spindash dust and Tails tail appendage sub-renderers.
- **Knuckles glide-in respawn**: `KnucklesRespawnStrategy.requiresPhysics()` now returns
  `true` during the drop phase so the physics pipeline applies gravity. Previously Knuckles
  would hang in mid-air after the glide because `SpriteManager` skipped physics for all
  `APPROACHING` strategies. `GLIDE_DROP` animation set during the glide approach phase.
- **Palette texture resize safety**: `GraphicsManager.cachePaletteTexture()` now preserves
  existing palette data when the texture grows to accommodate new contexts, preventing level
  palette corruption on resize.

#### S3K Zone Bring-Up Skill System

A 7-skill agentic system for systematic, per-zone implementation of S3K visual and behavioural
features (events, parallax, animated tiles, palette cycling). Designed for agent-driven analysis
of the disassembly followed by parallel feature implementation across worktrees.

- **s3k-zone-analysis**: reads the S3K disassembly and produces a structured zone feature spec
  covering events, parallax, animated tiles, palette cycling, notable objects, and cross-cutting
  concerns. Includes Phase 4 shared state trace for cross-category dependency detection (VRAM
  ownership conflicts, palette mutation vs cycling overlaps, event flag gating).
- **s3k-zone-events**: implements `Sonic3kZoneEvents` subclasses porting `Dynamic_Resize` routines
  from the disassembly — camera locks, boss arenas, cutscenes, act transitions, palette mutations.
- **s3k-animated-tiles**: implements AniPLC script triggers in `Sonic3kPatternAnimator` with
  zone-specific gating conditions and dynamic art overrides.
- **s3k-palette-cycling**: implements or validates `AnPal` handlers in `Sonic3kPaletteCycler` using
  the counter/step/limit pattern. Supports both new implementation and validation of existing zones.
- **s3k-parallax** *(updated)*: now accepts a zone analysis spec as optional input to accelerate
  deform routine discovery.
- **s3k-zone-bring-up**: orchestrator that dispatches zone analysis, parallel feature agents in
  worktrees, merge reconciliation, build verification, and validation.
- **s3k-zone-validate**: visual validation via stable-retro reference screenshots compared against
  engine output using agent image recognition (feature presence, not pixel-perfect diffing).
- All skills published in dual format (`.claude/skills/` + `.agent/skills/`) for agent-agnostic use.
- YAML frontmatter standardised across all 20 `.claude/skills/` and 8 `.agent/skills/` files.
- HCZ zone analysis spec produced as smoke test (`docs/s3k-zones/hcz-analysis.md`).
- AIZ zone analysis cross-validated against engine implementation: events and palette cycling
  matched byte-for-byte; parallax matched 13/14 checks; animated tiles revealed 3 cross-category
  gating omissions that motivated the Phase 4 shared state trace addition.

### Tails AI Improvements

- Comprehensive Tails CPU AI rework:
  - WFZ/DEZ/SCZ now suppress the CPU sidekick in gameplay and rendering.
  - Tails switches to FLYING when Sonic dies instead of despawning.
  - Respawn uses ROM's 64-frame gate plus A/B/C/Start bypass, blocking on object-control,
    air, roll-jump, underwater, and prevent-respawn conditions.
  - Manual P2 override for gameplay and special stages.
  - PANIC mode reworked to use `move_lock` + frame-counter timing.
  - Flying/despawn reworked with on-screen checks, water clamp, exact landing criteria.
  - Boss/event updates wired for EHZ2, HTZ2, MCZ2, CNZ2, CPZ2, ARZ2, and MTZ3.
  - Special-stage Tails uses its own replay buffer + P2 takeover path.

### Rendering Pipeline Improvements

- **PatternAtlas slot reclamation**: freed VRAM slots can be reused by new pattern uploads.
- **Batched DPLC atlas updates**: `DynamicPatternBank` batches multiple pattern updates per frame
  instead of individual uploads.
- **Virtual pattern ID validation**: range checks prevent silent VRAM corruption from out-of-bounds
  pattern references.
- **FboHelper**: centralised FBO creation utility, migrated 4 renderer files.
- **writeQuad()** extracted from `BatchedPatternRenderer` for reuse.
- Fail-fast on shader compilation/linking errors with GL resource cleanup.

### Logging and Error Handling

- 22 `e.printStackTrace()` calls migrated to structured `java.util.logging`.
- 28 swallowed exceptions in S3K code replaced with `LOG.fine()`.
- Production `System.out.println` calls replaced with `LOGGER.fine()`.
- Remaining logging gaps fixed across 6 files.

### Performance

- Batched DPLC atlas updates in `DynamicPatternBank`.
- Cached `LevelManager` reference in `DefaultObjectServices` (eliminates per-call singleton lookup).
- Per-frame `ObjectSpawn` allocation eliminated in `AbstractBadnikInstance`.
- Pre-allocated debug overlay lists, collision/sensor/camera bounds command lists.
- Reduced per-frame allocations in collision, rendering, and audio hot paths.
- Batched glyph rendering for debug text.

### BizHawk Trace Replay Testing

A new automated accuracy verification system that records per-frame physics state from the real ROM
running in BizHawk emulator, then replays the same inputs through the engine and compares every
field frame-by-frame.

- **Lua trace recorder** (`tools/bizhawk/`): BizHawk Lua script that captures player position,
  speed, angle, ground mode, air/rolling flags, and controller input every frame during a BK2
  movie playback. Outputs `metadata.json`, `physics.csv`, and `aux_state.jsonl`.
- **stable-retro trace recorder** (`tools/retro/`): cross-platform Python equivalent of the
  BizHawk Lua recorder, using stable-retro (Genesis Plus GX) for headless emulation. Produces
  byte-identical output format (same CSV, JSONL, and metadata.json) consumed by the same Java
  test infrastructure. Supports stable-retro BK2 replay, BizHawk BK2 parsing, savestate boot,
  and credits demo recording. Enables trace generation on macOS and Linux without BizHawk.
  Verified byte-for-byte output match against BizHawk reference traces for first 2100+ frames
  of GHZ1 before GPGX version-specific lag frames diverge the runs.
- **stable-retro BK2 alignment** (`--bk2-offset`): replays BizHawk BK2 movies through
  stable-retro by shifting BK2 inputs to the emulator's gameplay start frame. Handles GPGX
  byte-swap, exact-0x0C game_mode detection, and `|system|P1|P2|` BK2 group parsing.
- **Lag frame handling in credits demo tests**: `AbstractCreditsDemoTraceReplayTest` now
  detects lag frames (identical physics state on consecutive frames with non-zero speed) and
  skips both engine physics and demo input advancement on those frames. Reduced MZ2 credits
  divergences from 28 errors/131 warnings to 10 errors/57 warnings. Remaining errors are
  genuine engine divergences (missed bounces, slope collision, object timing).
- **Trace replay test infrastructure** (`tests.trace` package): `TraceData` loader, `TraceFrame`
  parser, `TraceBinder` per-frame comparator with configurable tolerances, `DivergenceReport`
  with JSON output and context windows, lag frame detection for VBlank sync.
- **`AbstractTraceReplayTest`**: base class for trace replay tests with graceful skip when ROM,
  BK2, or trace data files are absent. Subclasses only specify game/zone/act/path.
- **First trace: S1 GHZ1** full-run recording (3,905 frames): passes with 0 errors, 6 warnings.
- **Second trace: S1 MZ1** full-run recording (7,936 frames): baseline added with
  `TestS1Mz1TraceReplay`, regenerated GHZ1 traces, and ROM-verified zone/act metadata.
- **Recorder/diagnostics upgrades**: trace format now captures subpixel position, player routine,
  camera state, ring count, raw status, `v_framecount`, `standOnObj`, slot dumps, routine-change
  events, and ObjPosLoad cursor state for direct ROM/engine comparison.
- **Engine-side context windows**: divergence reports now include ROM and engine routine/object
  diagnostics, riding-object context, and placement cursor counters to narrow parity failures.
- **Buzz Bomber proximity fix**: removed overcorrecting player position prediction from the
  proximity detection check. The engine's 1-frame late spawn (pre-camera X vs ROM's post-camera X)
  and the pre-physics player position naturally cancel, placing the Buzz Bomber at the correct
  stop position without prediction.
- **Post-camera object placement sync**: `LevelFrameStep` now runs a post-camera placement
  catch-up pass after the camera update, closing the spawn timing gap when the camera crosses a
  chunk boundary between object placement (step 2) and camera update (step 5).
- **Placement parity narrowing**: S1 `out_of_range` timing, dormant-spawn handling, and
  ObjPosLoad callback groundwork reduced the remaining MZ1 investigation to a terrain /
  solid-contact parity problem rather than cursor drift.

#### Physics Accuracy Fixes (discovered via trace replay)

- **16:16 fixed-point subpixel positions**: `AbstractSprite.move()` upgraded from 16:8 to 16:16
  fixed-point arithmetic, matching the ROM's 32-bit `move.l obX(a0),d2` / `asl.l #8,d0` /
  `add.l d0,d2` position update. `xSubpixel`/`ySubpixel` widened from `byte` to `short`.
  `setX()`/`setY()` no longer zero the subpixel fraction (ROM's `move.w` to x_pos doesn't
  touch x_sub). Collision adjustments use new `shiftX()`/`shiftY()` to preserve subpixel.
- **GroundMode enum order fix**: `LEFTWALL` and `RIGHTWALL` were swapped; corrected to match
  ROM's quadrant assignment (0x40 = LEFTWALL, 0xC0 = RIGHTWALL).
- **CalcRoomInFront probe quadrant**: wall probe now uses `anglePosQuadrant()` (asymmetric
  rounding matching ROM's AnglePos dispatch) instead of `(angle+0x20)&0xC0`. Fixes false wall
  detections at steep slope angles (e.g. rotated angle 0xA0).
- **CalcRoomInFront 32-bit prediction**: probe prediction uses full 16-bit subpixel, matching
  ROM's 32-bit position arithmetic.
- **Air collision landing split**: separated `doTerrainCollisionAirDirect()` for movement
  quadrants 0x40/0xC0 (land immediately when floor detected, no speed threshold) from
  quadrant 0x00 (speed-dependent threshold). Matches ROM's per-quadrant landing logic.
- **Double ground mode update**: second `updateGroundMode()` after `selectSensorWithAngle()`
  uses the new angle from terrain probes, matching ROM's end-of-frame ground mode calculation.
- **Arithmetic right shift for air drag**: `xSpeed / 32` changed to `xSpeed >> 5` to match
  68000's `asr.w #5,d1` which rounds toward negative infinity (Java `/` truncates toward zero).
- **Jump transition defers air physics**: on jump, air physics are deferred to the next frame
  (ROM's `addq.l #4,sp` pops the return address, skipping the rest of ground movement).
  `sprite.setOnObject(false)` now called before jump to match `bclr #sta_onObj`.
- **BCC carry flag parity**: spindash release speed clamp `gSpeed > 0` changed to `gSpeed >= 0`
  to match 68000's carry flag behavior (carry SET on unsigned overflow, BCC NOT taken for zero).
- **`groundWallCollisionEnabled` feature flag**: new `PhysicsFeatureSet` field. S1 does not
  call CalcRoomInFront during ground movement (no equivalent in `Sonic_MdNormal`); S2/S3K do.
- **Air-control superspeed preservation**: S3K now preserves airborne speeds already above
  `topSpeed` after ramps and springs, while S1/S2 retain the original hard cap. `TwistedRamp`
  tumble frames now remain visible while rolling.

#### Object System Fixes (discovered via trace replay)

- **Deterministic object iteration order**: active objects now sorted by spawn X position,
  matching ROM's slot-order correlation with spawn-window entry order.
- **Touch response timing**: `runTouchResponsesForPlayer()` extracted and called during the
  player physics tick (after `handleMovement()`, before solid contacts), matching ROM's
  ReactToItem timing within Sonic's ExecuteObjects slot.
- **S1 UNIFIED collision model in SpriteManager**: pre-movement solid pass skipped for S1
  (ROM processes all solid objects after Sonic's movement); post-movement pass with
  `postMovement=true` disables velocity classification adjustment.
- **SolidContacts post-movement parameter**: `updateSolidContacts()` gains `postMovement` and
  `deferSideToPostMovement` flags to support the S1/S2 collision timing difference.
- **ROM-accurate `out_of_range` semantics**: `AbstractObjectInstance.isInRange()` now matches the
  ROM's chunk-aligned X-only range check with 16-bit wraparound, and S1 now performs
  out-of-range deletion during object execution rather than before it.
- **Dormant spawn tracking**: objects deleted by S1 `out_of_range` stay dormant between ObjPosLoad
  cursors until the cursor naturally re-processes them, preventing premature or missing reloads
  during camera backtracking.
- **Standing/contact parity fixes**: `MvSonicOnPtfm` now uses `groundHalfHeight` (`d3`) for
  standing Y, HURT touch responses now remain continuous after invulnerability expires, and
  staircase / MTZ platform / nut / button / elevator contact state now uses ROM-style boolean
  latches instead of diverging frame counters.

### Object Lifecycle Safety

- Removed constructor-time `services()` usage from 38 object classes; all affected objects now
  lazily initialize renderer and service-dependent state after `ObjectServices` injection.
- `TestNoServicesInObjectConstructors` now hard-fails constructor-time service access, unsafe
  `addDynamicObject(new X(...))` patterns, and pre-registration method calls that transitively
  depend on injected services.
- Sonic 1 lava geysers now defer initialization until first update, preventing pre-registration
  crashes; the lavafall third piece also no longer cascade-spawns infinite children.

### Sonic 1 Fixes

- Drowning visuals: breathing air bubble animation frames and countdown number positioning corrected.
- LZ credits demo spike collision fix and frame tick ordering unification.
- Yadrin top-hit behaviour and underwater palette/animation fixes for LZ.
- Minor LZ fix for jumping while sliding.
- GHZ bridge collision fix with corresponding tests.
- Monitor collision fix (particularly when in a tree).
- Bubble breathing now uses the fallback animation chain correctly, so grabbing an air bubble shows
  the intended breathing animation instead of preserving the rolling/spinning pose.
- SLZ staircase activation now uses ROM-style per-frame contact latches and has dedicated headless
  regression coverage.
- Bubble makers, push blocks, and related S1 objects now use ROM-accurate range semantics; spike
  standing dimensions now match the ROM's `d2`/`d3` values, including sideways spike extension.

### Sonic 2 Fixes

- HTZ water configuration corrected (Hill Top Zone no longer reports water).
- Collapsing platforms in MCZ stay solid during fragment phase.
- Special stage results screen decoupled from object system.
- S2/S3K collapsing platforms remain solid during fragment phase.
- CPZ staircase, MTZ platforms, nuts, buttons, and elevators now use boolean contact latches
  instead of frame-counter comparisons, fixing activation regressions during title cards and
  multi-sprite updates.
- Invincibility stars (Obj35) rewritten to match s2disasm: star 0 orbits at player's current
  position with fast rotation ($12/frame), stars 1-3 trail behind via position history buffer
  (3/6/9 frames behind) with slow rotation ($02/frame). Each star renders 2 sub-sprites at
  180 degrees apart. Corrected orbit offset table (7 entries had wrong X values), animation
  tables (parent uses byte_1DB82; trailing stars use per-star primary/secondary tables), and
  direction-aware rotation (angle negated when facing left).
- RNG parity paths tightened through shared `GameRng` coverage and `Sonic2Rng` regression tests,
  including CNZ slot-machine consumers and S2 object/boss call sites.

### Cross-Game Feature Donation Enhancements

- S1 wired as donor for forward donation into S2/S3K (previously only S2/S3K could donate).
- `DonorCapabilities` interface replaces hardcoded game-specific branches.
- `CanonicalAnimation` enum provides a game-neutral animation vocabulary for cross-game translation.
- `AnimationTranslator` handles bidirectional profile translation between any pair of games.
- Spindash speed table sourced from donor `PhysicsFeatureSet`.
- Cross-game art keys promoted to `ObjectArtKeys` for game-agnostic constant references.
- Import leak cleanup: removed cross-game S2 animation ID imports from game-agnostic sidekick code.

### Test and Quality

- `SingletonResetExtension` and `@FullReset` for automated per-test singleton lifecycle.
- `GameRuntime` lifecycle wired into 35 test classes with optimized Surefire configuration.
- Multi-sidekick integration smoke tests.
- Insta-shield test suite: gating, hitbox expansion, and visual frame-by-frame capture.
- MutableLevel round-trip and integration tests.
- S3K results screen tally mechanics unit tests.
- S3K registry coverage tests for all zones.
- Per-character respawn strategy unit tests.
- Migration guard scanner for detecting `getInstance()` / `GameServices` violations in object code.
- Annotated guard tests for services() migration completeness.
- AudioManager.resetState() field-clearing verification.
- Added `TestS1Mz1TraceReplay`, `TestSonic1StaircaseActivation`, `TestAbstractObjectInstanceRange`,
  and expanded lava geyser / constructor-safety guard coverage.
- Fixed 7 test failures caused by leaked runtime state: updated S3K Knuckles physics assertion
  to expect `SONIC_3K_KNUCKLES` profile (jump=0x600), saved/restored `RuntimeManager` in render
  tests, guarded teardown camera calls with null checks, used `destroyForReinit()` for
  `TestGraphicsManagerHeadless`.

#### Test Suite Cleanup

Systematic audit and remediation of the test suite. Net result: +34 passing tests, 36→0 skipped,
no new failures.

- **Stale @Ignored stubs replaced with real tests**: `TestTodo14` (PlayerCharacter ordinals),
  `TestTodo13` (19 SBZ/FZ event routine tests), `TestTodo17` (boss flag gating), `TestTodo19`
  (rock debris table parity), `TestTodo34` (water slide chunk detection).
- **Broken live tests fixed**: `TestTodo3` (MonitorType reflection instead of test-local enum copy),
  `TestTodo37` (ROM-vs-engine constant parity via reflection).
- **Dead test files deleted**: 8 fully-@Ignored TestTodo stubs for unimplemented features (Yadrin
  spiky-top, Knuckles monitor, Super transform, rock width, rock push, ChopChop bubbles, control
  lockout, SBZ2 transition); 8 zero-assertion diagnostic dumps; `TestTodo29` (SCALE no-op).
- **Low-value tests pruned**: constant-equals-itself assertions (Knuckles cutscene timers, emerald
  scatter constants), ROM-only checks with no engine cross-reference (angle table size, CNZ
  romDataPresent), duplicate coverage (edge balance constants, water provider hasWater), test
  infrastructure self-tests (SharedLevel, InitStep fields).
- **Test uplifts**: `TestTodo1` cross-references ROM water heights against `Sonic2WaterDataProvider`;
  `TestTodo31` adds real end-game zone boundary assertions; 7 S3K palette cycling test files (AIZ2,
  CNZ, EMZ, HCZ, ICZ, LBZ, LRZ) strengthened from "color changed" to specific RGB value assertions
  using `Sonic3kPaletteCycler` with StubLevel; water data provider tests deduplicated between
  provider and handler files.
- **Integration gaps closed**: removed blocked @Ignored stubs from `TestGameLoop` (special stage
  mode) and `TestTodo4` (MCZ boss collision boxes); removed reference-file-dependent test from
  `TestSonic3kVoiceData`; removed diagnostic dump stubs from `TestS3kSonicSpriteDiag`.

### Documentation

- Comprehensive user guide for three audiences (players, developers, contributors).
- OpenSMPSDeck music tracker design spec and implementation plan.
- Rendering pipeline improvements spec and plan.
- Unified execution roadmap and Phase 0+1 implementation plan.
- GameRuntime architecture spec and implementation plan.
- Two-tier service architecture design spec and implementation plan.
- MutableLevel (Phase 3) spec and implementation plan.
- Insta-shield design spec and implementation plan.
- Multi-sidekick daisy chain design spec and implementation plan.
- Cross-game bidirectional animation donation design spec and implementation plan.
- Game-specific leak fixes spec and plan.
- Services migration cleanup design spec and implementation plan.
- Architectural fixes design spec, implementation plan, and review passes.
- Singleton lifecycle documentation.
- Phase 4 common refactoring design spec (5 phases, 25 patterns) and implementation plan (21 tasks).
- Virtual pattern IDs and multi-sidekick system documented in AGENTS.md.
- Known discrepancies documentation for multi-sidekick rendering.
- Added the `s1-trace-replay` skill and refreshed skill descriptions for the parity-driven
  object/boss/disassembly workflow docs.

## v0.4.20260304 (Released 2026-03-04)

Analysis range: `v0.3.20260206..v0.4.20260304` on `develop` (`1790` commits, `1589` non-merge commits,
`2040` files changed, `218141` insertions, `195996` deletions).

> Note: the large deletion count reflects the package rename from `uk.co.jamesj999.sonic` to
> `com.openggf`, which deleted and recreated most source files. Net code growth is ~22,100 lines.

### Sonic 1 Expansion and Content Completion

- Added full Sonic 1 title screen pipeline and title-screen-to-level-select flow
  (`Sonic1TitleScreenManager`, loader, mappings, transition handling).
- Implemented Sonic 1 rings and lamppost/checkpoint behavior.
- Implemented Sonic 1 special stage gameplay and integration:
  - Game-agnostic special stage provider refactor.
  - `Sonic1SpecialStageManager`, renderer/background renderer/data loader, block types, and results screen.
  - Giant Ring route from normal gameplay into special stage flow.
- Introduced per-zone event coverage for Sonic 1 with zone-specific managers/events for GHZ, MZ, SYZ,
  LZ (including water events), SLZ, SBZ, and ending/FZ handling.
- Major object implementation wave for Sonic 1: `117` new object-related classes
  (`78` general objects, `23` badnik classes, `16` boss-related classes).
- Boss coverage expanded to GHZ, MZ, SYZ, LZ, SLZ, and FZ with child objects/projectiles and event integration.
- Added/finished LZ water behavior and bubble systems, including per-ROM drowning music selection.
- Added ending/outro flow updates and initial credits sequence implementation.
- Added SBZ2 post-level-end sequence.
- Fixed S1 physics regressions with test coverage (multiple passes).

### Sonic 2 Gameplay Additions

- Added Sonic 2 title screen architecture and title-screen audio regression coverage.
- Added major object coverage passes:
  - Metropolis Zone object set (`16` objects) and engine crush detection.
  - Sky Chase/Tornado object set and spawn path integration.
  - Wing Fortress object set and supporting hazards/platforms.
  - Oil Ocean object and oil-surface behavior improvements.
- Added MCZ boss implementation (`Sonic2MCZBossInstance` + falling debris support) with follow-up fixes.
- Added MTZ Boss (Obj54) with S2 boss event stubs.
- Added WFZ Boss (ObjC5) with laser platform attack cycle, plus ROM-accuracy pass (17 issues).
- Added DEZ Mecha Sonic boss (ObjAF) with full state machine, plus ROM-accuracy pass (17 issues).
- Added DEZ Death Egg Robot (ObjC7) — final S2 boss, plus ROM-accuracy pass (12 issues).
- Added Robotnik escape sequence between DEZ boss fights (ObjC6).
- Six passes of DEZ boss ROM-accuracy corrections: Silver Sonic facing direction, LED overlay,
  animation phase gating, Egg Robo collision/render priorities, Death Egg Robot child systems.
- Added `61` new Sonic 2 object-related files (`45` general objects, `14` badnik classes, `2` boss files),
  including additional SCZ/WFZ/MTZ/OOZ badnik/object coverage.
- Refactored and expanded Sonic 2 zone events (`Sonic2LevelEventManager` + per-zone event classes).
- Implemented Sonic 2 credits and ending system:
  - `EndingPhase` enum, `EndingProvider` interface, and `ENDING_CUTSCENE` GameMode.
  - `Sonic2CreditsTextRenderer`, `Sonic2CreditsMappings`, `Sonic2CreditsData` with timing constants.
  - `Sonic2EndingCutsceneManager` and `Sonic2EndingArt` with DEZ star field background rendering.
  - `Sonic2LogoFlashManager` with ROM-accurate palette strobe.
  - `Sonic2EndingProvider` wired to DEZ boss ending trigger.
  - Rewritten for ROM parity with ObjCA/ObjCC, DPLC player sprites, tornado visibility.
  - `Sonic1EndingProvider` refactored to use shared `EndingProvider` interface.
- Added demo playback functionality with enhancements and routing to objects.
- Systematic TODO resolution pass: water heights, monitor effects, distortion table, sliding spikes,
  dual collision, Yadrin spiky-top collision, water slide control lockout, LZ rumbling SFX,
  boss flag wiring to AIZ pattern animations, plus TODO/FIXME coverage tests with disassembly validation.
- Various object fixes: PointPokey positioning, MCZRotPlatforms child accumulation, signpost/screen
  locking, object loading improvements, ROM-accurate bumper/bonus block/rising pillar/diagonal spring physics.

### Super Sonic and Per-Game Physics

- Added cross-game physics abstraction:
  - `PhysicsProfile`, `PhysicsFeatureSet`, `PhysicsModifiers`, `PhysicsProvider`, and `CollisionModel`.
  - Validation tests for profile behavior, collision model differences, spindash gating, and speed capping.
- Implemented Sonic 2 Super Sonic flow:
  - Base state machine via `SuperState`/`SuperStateController`.
  - Integration into playable sprite/game loop/module plumbing.
  - ROM-based animation loading, ROM-exact palette cycling, and S2 constants wiring.
  - Invulnerability/enemy-destruction behavior and shield/power-up interaction guards.
  - Debug toggle support and Super Sonic stars object support.
- Added Sonic 3K Super Sonic controller stub/hook points for future parity work.
- Added cross-game Super Sonic delegation to S1 and S2 game modules via `CrossGameFeatureProvider`,
  including palette, audio, and renderer integration, invincibility, and S3K slope animation offset.

### Sonic 3K Bring-Up (AIZ-Focused)

- Extended Sonic 3K bootstrap/audio readiness (voice/sfx index fixes, ROM loading fixes, SoundTestApp support).
- Implemented Angel Island intro cinematic pipeline:
  - AIZ event wiring and intro state-machine objects (`AizPlaneIntroInstance`, Knuckles cutscene objects,
    emerald scatter, wave/plane/glow/booster children).
  - Intro art loading/caching and terrain swap integration.
- Added AIZ gameplay object work with parity-focused fixes:
  - Ride vines and giant ride vines.
  - Hollow tree traversal and reveal/tilemap support.
  - Multiple parity fixes (angle bytes, state retention, endianness, momentum, despawn guards) plus regressions.
- Added AIZ miniboss object set and child components.
- Added initial S3K badnik framework and first wired badnik implementations.
- Added S3K shield object implementations and fixed deferred PLC loading after AIZ intro.
- Added Sonic 3K title card manager/mappings and S3K pattern/palette animation work.
- Implemented S3K water system:
  - Game-agnostic `WaterDataProvider` and `DynamicWaterHandler` interfaces.
  - `ThresholdTableWaterHandler` for table-driven water zones.
  - `Sonic3kWaterDataProvider` with static heights, dynamic handlers, and underwater palette loading.
  - `Sonic1WaterDataProvider` migration to the new provider architecture.
  - Wired into LevelManager and S3K zone features, deprecated game-specific water loading methods.
  - Correct water threshold tables, `setMeanDirect`, zone scope, and starting heights matching ROM.
  - S3K water locked flag, shake timer, LBZ2 pipe plug handler.
  - AIZ2 Knuckles water exclusion, raise speed inheritance, `update()` overshoot fixes.
- Implemented seamless AIZ fire transition flow (`S3kSeamlessMutationExecutor`).
- AIZ miniboss cutscene and barrel shot child updates.
- Expanded AIZ scroll handler work (`SwScrlAiz`).

### PLC, Art Loading, and Tooling

- Major PLC and sprite-pattern refactor across S1/S2/S3K pipelines.
- Added/expanded PLC systems:
  - `Sonic2PlcLoader`, `Sonic2PlcArtRegistry`, and broader S3K PLC loading paths.
  - Shared sprite/mapping loader use (`S1SpriteDataLoader`, `S2SpriteDataLoader`, `S3kSpriteDataLoader`).
- Expanded ROM/disassembly tooling:
  - Object profile abstractions per game (`Sonic1ObjectProfile`, `Sonic2ObjectProfile`, `Sonic3kObjectProfile`).
  - Shared-ID handling in S3K object checklist generation.
  - PLC cross-referencing in `RomOffsetFinder`/`DisassemblySearchTool` and `ObjectDiscoveryTool`.

### Audio, Stability, and Engine Hardening

- Audio updates:
  - Music/SFX catalog refactor to enum-driven paths.
  - PSG GPGX hybrid parity work and tests.
  - S3K pitch wrapping and SFX index fixes.
  - YM2612/SMPS fixes (including SSG-EG active-count leak and loop counter bounds).
  - Thread-safety fixes in SMPS/audio backend paths and output mixing saturation safeguards.
- Engine hardening and safety:
  - ROM read synchronization and bounds checks.
  - Kosinski/resource loading safety limits.
  - Graphics cleanup fixes (resource leaks, reset-state gaps, allocation reductions).
  - Additional stability fixes across water/drowning handling, invulnerability timing, and debug movement modifiers.
- Performance passes across level/render/audio hot paths and internal debug profiling updates.
- Fixed SFX channel replacement: kill old SFX track on shared channel to prevent priority lock.
- Synth-core review fixes: resource safety, encapsulation, dead code cleanup.
- HTZ earthquake fixes: descending through floor, tile display, rising lava subtype 4 hurt behaviour.
- Consolidated duplicate sine/cosine tables to `TrigLookupTable`.
- Fixed cross-game features breaking layer switchers.
- Fixed special stage transition softlocks and S1 results fade type.

### Test and Quality Coverage

- Added `83` new test files across this range, including:
  - Sonic 1 special stage, object, badnik, boss, and routing regressions.
  - Sonic 3K AIZ intro/state timeline/hollow tree traversal parity regressions.
  - Title screen audio regression coverage.
  - PSG/YM2612 and per-game physics/profile parity checks.
- Expanded headless and subsystem-focused tests in support of object/event/audio refactors.
- Added 21 headless bug reproduction tests for 17 reported S1/S2 bugs.
- JUnit 5 migration: deleted 54 self-verifying tests, replaced with parameterized tests.
- Parallelized test execution with 8 forked JVMs.
- Test grouping by level: merged headless tests sharing the same level load into groups
  (EHZ1: 4→1, ARZ1: 3→1, CNZ1: 3→1, HTZ1: 2→1, AIZ1: 2→1, GHZ1: 6→1), eliminating 14 redundant
  level loads.
- Added TODO/FIXME coverage tests with disassembly validation.

### Cross-Game Feature Donation

Implemented cross-game feature donation system: a donor game (S2 or S3K) provides player sprites,
spindash dust, physics, palettes, and SFX while the base game (e.g. S1) handles levels, collision,
objects, and music. Enabled via `CROSS_GAME_FEATURES_ENABLED` and `CROSS_GAME_SOURCE` config keys.

- `CrossGameFeatureProvider` singleton: opens donor ROM as secondary ROM (no module detection
  side-effect), creates game-specific art loaders (`Sonic2PlayerArt`/`Sonic3kPlayerArt`,
  `Sonic2DustArt`), builds hybrid `PhysicsFeatureSet` (spindash from donor, everything else S1),
  loads donor character palette, initializes donor audio.
- `RenderContext` palette isolation: base game occupies palette lines 0-3, each donor gets its own
  block of 4 lines (4-7, 8-11, etc.) via static registry with `getOrCreateDonor()`.
  `uploadDonorPalettes()` pushes donor palettes to GPU. `getDonorContexts()` for iteration.
- `GameId` enum with `fromCode()` for type-safe donor identification.
- `RomManager.getSecondaryRom()` opens donor ROM without triggering game module detection.
- `LevelManager` art loading paths (`initPlayerSpriteArt`, `initSpindashDust`, `initTailsTails`)
  check `CrossGameFeatureProvider.isActive()` and delegate to donor art providers, attaching
  donor `RenderContext` to each `PlayerSpriteRenderer`.
- `Engine` initialization gates sidekick spawning on `GameModule.supportsSidekick()` or
  `CrossGameFeatureProvider.isActive()`, with cleanup on shutdown.
- GPU palette texture dynamically resized via `RenderContext.getTotalPaletteLines()`. All shaders
  (`shader_the_hedgehog`, `shader_tilemap`, `shader_water`, `shader_sprite_priority`,
  `shader_instanced_priority`, `shader_cnz_slots`) updated from hardcoded `/4.0` to
  `/TotalPaletteLines` uniform.
- Underwater palette derivation for donor sprites:
  - `RenderContext.deriveUnderwaterPalette()` synthesizes donor underwater colors using the base
    game's global average per-channel color shift ratio (not per-index, which would mismatch
    palette layouts across games).
  - `GraphicsManager.cacheUnderwaterPaletteTexture()` extended to populate donor palette rows
    automatically from the base game's normal-to-underwater shift.
- Donor SMPS driver config for correct SFX playback:
  - `SmpsSequencerConfig` threaded through `AudioManager.registerDonorLoader()` (4-arg overload),
    stored per donor game in `donorConfigs` map.
  - `AudioBackend.playSfxSmps()` 4-arg overload accepting explicit config; `LWJGLAudioBackend`
    uses donor config when provided, falling back to base game config.
  - `CrossGameFeatureProvider.initializeDonorAudio()` passes `donorProfile.getSequencerConfig()`.
- Donor audio overlay in `AudioManager`: `donorLoaders`, `donorDacData`, `donorSoundBindings` maps;
  `playSfx()` falls through to donor path when base game sound map has no entry.
- S3K Tails tail appendage support: `CrossGameFeatureProvider.hasSeparateTailsTailArt()` and
  `loadTailsTailArt()` delegate to donor's `Sonic3kPlayerArt` for separate Obj05 tail art.
  `LevelManager.initTailsTails()` checks donor game module when cross-game is active, selecting
  correct art loading path and `ANI_SELECTION_S3K` animation tables.
- SFX re-trigger fix in `SmpsDriver`: re-triggering the same SFX ID now replaces the old sequencer
  instead of competing for the same FM/PSG channels (prevents priority lock ping-pong with S1/S2
  jump SFX priority 0x80).
- Tests: `TestRenderContext` (9 tests covering palette isolation, line allocation, reset,
  underwater palette derivation), `TestDonorAudioRouting` (donor SFX routing and sequencer config),
  `TestGameId`, `TestHybridPhysicsFeatureSet`, `TestSidekickGating`.

### Master Title Screen

- Implemented `MasterTitleScreen` (404 lines): engine-wide title screen displayed on startup before
  entering game-specific title flow. PNG-based background, animated clouds, title emblem, and game
  selection text rendered via `TexturedQuadRenderer` and `PixelFont`.
- New rendering infrastructure: `PngTextureLoader` (85 lines), `TexturedQuadRenderer` (139 lines),
  `PixelFont` (144 lines), `shader_rgba_texture` vertex/fragment shaders.
- Configurable via `TITLE_SCREEN_ON_STARTUP` config key (default: enabled).

### Sonic 1 Fixes and Improvements

- Fixed Sonic spawning 5px underneath terrain on level reset by restoring standing radii in
  `AbstractPlayableSprite` respawn path (ROM: `Obj01_Init` unconditionally sets `y_radius=$13`).
- Object collision fixes: `ObjectManager` solid overlap test now always uses `airHalfHeight`
  matching ROM behaviour (d3 is overwritten by playerYRadius before read). Added
  `Sonic1ButtonObjectInstance` and `Sonic1MzBrickObjectInstance` collision support.
  `TestHeadlessSonic1ObjectCollision` (291 lines) regression test added.
- Fixed edge balance mode for S1 (single balance state, force face edge) while preserving S2's
  4-state extended balance. `PhysicsFeatureSet.extendedEdgeBalance` gates behaviour.
  `TestEdgeBalance` (91 lines) and `TestHeadlessSonic1EdgeBalance` (369 lines) added.
- Fixed MZ2 push block: longer blocks no longer get pushed "out of the way" when Sonic pushes them
  against walls. `SolidContact` improvements. `TestHeadlessMZ2PushBlockGap` (132 lines) added.
- SBZ fixes: Flamethrower positioning corrected for vflip/hflip variants. StomperDoor objects fixed.
  Junction now locks the player correctly. SBZ3 water oscillation implemented.
- LZ fixes: Wind tunnels now play correct player animation. Breakable poles play correct animation.
  Water splash effect implemented (`Sonic1SplashObjectInstance`).
- Demo playback now sent to objects (`AbstractPlayableSprite` demo input routing).
- Push stability fixes for solid objects. `TestHeadlessSonic1PushStability` (220 lines) added.
- Outro/credits improvements (`Sonic1CreditsManager`, `FadeManager` enhancements).
- `TestSbz1CreditsDemoBug` (162 lines) and `TestS1FlamethrowerObjectRendering` (58 lines) added.
- S1 "fast" mode SMPS sequencer support.
- S1 outro improvements: disable control on outro, change 'back to main menu' key.
- S1 ending sequence flowers fix.
- S1 object collision fixes.

### Sonic 2 Fixes

- Fixed badnik palette lines (Spiny now uses palette line 1 matching `make_art_tile`), signpost
  frame order corrected to match `obj0D_a.asm` ROM mapping order, CPZ stair block / MTZ platform
  art sheet rebuilt with hand-crafted mappings (ROM mappings reference level art tiles).
- Swinging platform art loading fix for non-S2 games.
- S2 ending cutscene parity: DEZ white fade (not black), star field background, pilot visibility,
  BG scroll compensation, DPLC player sprites, tornado visibility, falling timing.
- Prevented DEZ Robot despawn during defeat ending sequence.
- Fixed DEZ boss visual and collision issues (multiple passes).
- Fixed S2 credits visual accuracy: ROM-correct font, mappings, and player detection.
- Fixed S2 `Sonic2LevelEventManager` zone constants alignment with `ZoneRegistry`.

### Physics and Collision Fixes

- Fixed solid object edge jitter: `SolidContacts` snaps player to resolved edge on static solids
  to prevent subpixel accumulation. Push-driven objects opt in to ROM-style subpixel preservation
  via `SolidObjectProvider.preservesEdgeSubpixelMotion()`.
- S1 slope crest sensor guard: prefer floor-class probe over wall-class probe at crest transitions,
  preventing one-frame wall/air mode flips.
  `TestHeadlessStaticObjectPushStability` (208 lines) and
  `TestSonic1GhzSlopeTopDiagnostic` (519 lines) added.
- Sonic no longer jumps if the player holds jump while airborne via a non-jump (spring, slope
  launch, etc.).
- Various physics tweaks aimed at S1: physics modifiers cleanup, `FadeManager` fade-to-black
  transitions no longer flash back to "off" briefly before fade-in begins.
- Fixed results screen rendering issue for both S1 and S2.

### Package Rename

- Renamed root package from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
  All source files, test files, and references updated.

### Profile-Driven Level Loading

- Introduced `LevelInitProfile` abstraction with `InitStep` and `StaticFixup` primitives for
  declarative, ROM-aligned level loading.
- Implemented per-game profiles (`Sonic1LevelInitProfile`, `Sonic2LevelInitProfile`,
  `Sonic3kLevelInitProfile`) with 13 finer-grained ROM-aligned steps each.
- `LevelLoadContext` provides shared state across load steps.
- `LevelManager.loadLevel()` routed through profile steps; old fallback path removed.
- Per-step timing and logging for load diagnostics.
- Profile-driven teardown and per-test reset replaces `TestEnvironment` and `GameContext.forTesting()`.
- `CHARACTER_APPEAR` phase uses `Map_Sonic`/`Map_Tails` Float2 animation.

### Testability Refactor

- `GameContext` holder with `production()` and `forTesting()` factories for singleton lifecycle.
- `SharedLevel` for reusable level loading across test classes.
- `HeadlessTestFixture` builder pattern for test setup, with 14 test classes converted.
- `TestEnvironment.resetAll()` delegates to `GameContext.forTesting()` for consistent teardown.

### Docs and Planning

- Added release-planning/implementation docs for unified level events, Super Sonic, and AIZ intro work.
- Added cross-game donation fixes design doc and implementation plan.
- Added `docs/CONFIGURATION.md` with full config key reference.
- Expanded disassembly/reference and skill documentation used for parity-driven object/boss implementation workflows.
- Added DEZ boss fixes design and implementation plans.
- Added Sonic 2 credits and ending sequence design and implementation plans.
- Added cross-game Super Sonic design and implementation plan.
- Added S3K water system design and implementation plan.
- Added testability improvement design (GameContext + HeadlessTestFixture) and implementation plan.
- Added headless test level grouping design and implementation plan.
- Added profile-driven level loading plans (Phase 3 and Phase 4).
- Added ending parallax background design and implementation plan.
- Added level editor design and implementation plan.
- Added ROM-driven init profiles design and implementation plan.

## v0.3.20260206

366 commits, 541 files changed, ~99,000 lines added.

### Multi-Game Architecture

- Complete engine refactor to support multiple Sonic games through a provider-based abstraction layer
  - `GameModule` interface defines 15+ provider methods for all game-specific behaviour
  - `GameModuleRegistry` singleton holds the active game module
  - `RomDetectionService` auto-detects ROM type via registered `RomDetector` implementations
- New provider interfaces: `ZoneRegistry`, `ObjectRegistry`, `ObjectArtProvider`, `ZoneArtProvider`,
  `ScrollHandlerProvider`, `ZoneFeatureProvider`, `RomOffsetProvider`, `SpecialStageProvider`,
  `BonusStageProvider`, `DebugModeProvider`, `DebugOverlayProvider`, `TitleCardProvider`,
  `LevelEventProvider`, `ResultsScreen`, `MiniGameProvider`
- `GameServices` facade for centralised access to `gameState()`, `timers()`, `rom()`, `debugOverlay()`
- NoOp implementations for optional providers (`NoOpBonusStageProvider`, `NoOpSpecialStageProvider`, etc.)
- Sonic 2 fully migrated to provider architecture (`Sonic2GameModule` and all provider implementations)
- `Sonic2Constants.java` expanded by 663+ lines of ROM offset constants
- `Sonic2ObjectIds.java` expanded with 118 new object type ID constants

### Tails (Miles Prower) - Playable Character

- `Tails.java` playable sprite: shorter height (30px vs Sonic's 32px), adjusted sensor offsets (±15px vs ±19px), otherwise identical physics
- `TailsCpuController.java` ROM-accurate AI follower with 5-state machine: `INIT`, `NORMAL` (input replay), `FLYING` (helicopter chase), `PANIC` (spindash escape), `SPAWNING` (respawn wait)
- Input replay system: Tails replays Sonic's recorded inputs from 17 frames ago via position/status history buffer
- AI overrides: direction correction when >16px off, forced jumps when Sonic is 32+ pixels above, spindash escape every 128 frames when stuck >120 frames
- Despawn after 300 frames off-screen, respawn 192 pixels above Sonic when safe
- `TailsTailsController.java` (Obj05): separate rotating tails animation with 10 states (Blank, Swish, Flick, Directional, Spindash, Skidding, Pushing, Hanging)
- Art loaded from ROM at `0x64320` (uncompressed, `0xB8C0` bytes) with separate mappings and reversed mappings
- Configurable via `SIDEKICK_CHARACTER_CODE` in config.json: `"tails"` (default), `""` to disable, `"sonic"` for Sonic clone
- Can be spawned as main player character or as CPU-controlled sidekick
- Flying mode bypasses normal physics, using direct position updates for aerial chase
- Per-player riding state: solid object contacts refactored to `IdentityHashMap` so Sonic and Tails can independently ride different platforms (13 files updated)
- Test: `TestTailsCpuController` covering state transitions, input replay, distance gating, despawn/respawn

### Sonic 1 Initial Support (23 new files, 3,729 lines)

- ROM auto-detection via `Sonic1RomDetector` (header-based)
- `Sonic1.java` game entry point with level loading and data decompression from S1 ROM
- `Sonic1Level.java` implementing S1-specific level data format (different structure from S2)
- `Sonic1ZoneRegistry` covering all 7 zones: Green Hill, Marble, Spring Yard, Labyrinth, Star Light, Scrap Brain, Final
- `Sonic1Constants.java` with verified ROM addresses for S1 REV01
- `Sonic1PlayerArt.java` loading player sprites with S1-specific mapping format
- Parallax scroll handlers for all 7 zones (`SwScrlGhz`, `SwScrlMz`, `SwScrlSyz`, `SwScrlLz`, `SwScrlSlz`, `SwScrlSbz`, `SwScrlFz`)
- `Sonic1PatternAnimator` for S1 tile animation scripts (waterfall, flowers, lava, conveyors)
- `Sonic1PaletteCycler` for S1 zone-specific palette cycling
- `Sonic1AudioProfile` and `Sonic1SmpsData` for S1 ROM audio playback via SMPS driver
- `Sonic1ObjectRegistry` and `Sonic1ObjectPlacement` stubs for S1 object format parsing
- `Sonic1LevelSelectManager` (394 lines): 21-item vertical menu with zone/act selection, wrap-around navigation, sound test
- `Sonic1LevelSelectDataLoader` and `Sonic1LevelSelectConstants` for ROM-based graphics and layout
- `LevelSelectProvider` interface extracted for game-agnostic level select support
- `Sonic1TitleCardManager` (468 lines), `Sonic1TitleCardMappings` (306 lines): S1-specific title card rendering
- `Sonic1ObjectArtProvider` for S1 HUD rendering (life icons, ring display)
- Tests: `TestGhzChunkDiagnostic` (GHZ chunk loading), `Sonic1PlayerArtTest` (player sprite loading)

### Physics Engine

#### Core Physics Rewrite
- Complete physics rewrite in `PlayableSpriteMovement` (1,814 lines, replacing 1,134-line predecessor)
- Movement modes now explicitly mirror ROM state machine: `Obj01_MdNormal` (ground walking), `Obj01_MdRoll` (ground rolling), `Obj01_MdAir`/`MdJump` (airborne)
- ROM-accurate slope resistance/repulsion formulas with correct angle offset (0x20) and mask (0xC0)
- Slope repel minimum speed threshold (0x280) matching ROM `Sonic_SlopeRepel`
- Rolling physics: dedicated roll deceleration (0x20), controlled roll constants, minimum start roll speed gating
- Spindash fully reimplemented using ROM speed table (`s2.asm:37294`) indexed by `spindash_counter >> 8`
- Spindash counter charging/decay logic matching ROM `Sonic_UpdateSpindash`
- Fixed subpixel accuracy: subpixels were not being used correctly in velocity/position calculations
- Near-apex air drag implemented (when -1024 <= ySpeed < 0), matching ROM `Sonic_MdJump` behaviour
- Upward velocity cap added at -0xFC0
- Roll height adjustment fixed (5px to 10px) for all roll-mode transitions, preventing visual "fall" on transition
- ROM-identical angle/quadrant selection table in `TrigLookupTable` (256 entries from `misc/angles.bin`)
- `calcAngle()` method exactly matching ROM `CalcAngle` routine (s2.asm:4033-4076)
- Jump angle calculation and slope angle assist/repel gating adjustments

#### Player Mechanics
- Pinball mode flag (`pinballMode`) preventing rolling from being cleared on landing; gives boost instead of stopping at speed 0. Used by CNZ tubes, blue balls, launcher springs. Preserved through launcher spring bounces
- Ledge balance animation with 4 balance states matching ROM (BALANCE through BALANCE4) based on proximity and facing direction (s2.asm:36246-36373)
- Look up/down delay counter (`lookDelayCounter`) matching ROM `Sonic_Look_delay_counter` timing
- Spring control lock fixed to only apply when grounded (was incorrectly locking controls in air)
- Run animation starts the moment left/right are pressed
- Three distinct control lock types matching ROM: `objectControlled` (blocks all input), `moveLocked` (blocks directional but allows jump), `springing` (blocks grounded directional)
- Signpost walk-off fix: control lock no longer cancels forced input, allowing Sonic to properly walk off-screen after act end
- Position history buffer (64 entries) for camera lag and spindash compensation

#### Collision System
- New unified `CollisionSystem` (214 lines) orchestrating a 3-phase pipeline:
  1. Terrain probes (ground/ceiling/wall sensors via `TerrainCollisionManager`)
  2. Solid object resolution (platforms, moving solids via `ObjectManager.SolidContacts`)
  3. Post-resolution adjustments (ground mode, headroom checks)
- Supports trace recording via `CollisionTrace` interface for debugging and testing
- `GroundSensor` rewrite (437 lines): separated vertical scanning (floor/ceiling) from horizontal scanning (walls), ROM-accurate negative metric handling, full-tile edge detection with previous-tile lookback, horizontal wall scanning with regress/extend states
- Collision order fix: solid objects now processed before terrain, preventing objects from being overridden
- Sensor adjustment timing changed to earlier in the tick
- Collision path reset on level switch to prevent falling through levels on wrong layer
- Ceiling collision improvements: better ceiling sensors on walls/ceilings, angle-based landing detection, ceiling mode (0x80) correctly adjusts only Y velocity
- Wall pushing fix
- Solid object landing now resets ground mode and angle (matching solid tile landing)
- New `ObjectTerrainUtils` (296 lines) for game object terrain collision (floor, ceiling, left wall, right wall), mirroring ROM `ObjCheckFloorDist`

### Camera

- Complete vertical scroll rewrite matching ROM behaviour:
  - Y position bias system (`Camera_Y_pos_bias`) with default value of 96
  - Look up bias (200) with gradual 2px/frame increment
  - Look down bias (8) with gradual 2px/frame decrement
  - Bias easing back to default at 2px/frame
  - Grounded scroll speed cap: 2px (looking), 6px (normal), 16px (fast, inertia >= 0x800)
- Airborne camera uses +/-32px window around current bias matching ROM `ScrollVerti` airborne path
- Horizontal scroll delay (`horizScrollDelayFrames`) replaces old `framesBehind` system. Matches ROM where `ScrollHoriz` checks `Horiz_scroll_delay_val` but `ScrollVerti` does not
- Rolling height compensation: camera subtracts 5px from Y delta when rolling (1px for Tails)
- Spindash camera fixed to use horizontal scroll delay rather than full camera freeze
- Screen shake system: `shakeOffsetX`/`shakeOffsetY` with `getXWithShake()`/`getYWithShake()` for rendering (used by HTZ earthquake)
- Boundary clamping to `minX`/`minY` (was only clamping to 0)
- Full freeze (death/cutscenes) now separate from horizontal scroll delay

### Water System

- Complete `WaterSystem` (462 lines): water level loaded from ROM at correct height, water oscillation in CPZ2 via `OscillationManager`, water surface sprites rendering in front of solid tiles
- `WaterSurfaceManager` (282 lines) for surface sprite management
- Water surface sprites appear for CPZ2, ARZ1, and ARZ2
- Water entry/exit detection based on player centre Y vs water surface
- Underwater physics: speed halving on water entry (xSpeed/2, ySpeed/4), halved acceleration/deceleration/max speed, corrected jump height, corrected hurt gravity and launch amount
- `DrowningController` (289 lines): 30-second air timer with frame-accurate countdown, warning chimes at air levels 25/20/15, drowning countdown music at air level 12, countdown number bubbles (5/4/3/2/1/0), breathing bubble spawning, music restart on water exit or air replenishment, air bubble collection with 35-frame control lock
- Water collision aligned with oscillating visual position in CPZ2
- HUD text no longer turns red on water levels
- Special Stage results no longer overwrite water surface sprite

### Boss Fights

#### Boss Framework (game-agnostic)
- `AbstractBossInstance` (530 lines) base class: hit points, invincibility frames, state machine, defeat sequences, camera locking, explosion cascades
- `AbstractBossChild` (109 lines) base class for multi-component boss sub-objects
- `BossChildComponent` interface (45 lines) and `BossStateContext` (72 lines) for shared state
- `BossExplosionObjectInstance` for shared boss explosion effects
- `CameraBounds` for boss arena camera locking

#### Implemented Bosses
- **EHZ Boss** (Drill Car, Obj56) - `Sonic2EHZBossInstance` with 6 child components: ground vehicle, propeller, spike drill, vehicle top, wheels, animations helper
- **CPZ Boss** (Water Dropper, Obj5D) - `Sonic2CPZBossInstance` with 14 child components: container (extend, floor), dripper, falling parts, flame, gunk hazard, pipes (pump, segment), pump, Robotnik sprite, smoke puffs, animations helper
- **HTZ Boss** (Lava Flamethrower, Obj52) - `Sonic2HTZBossInstance` with flamethrower, lava ball projectiles, smoke particles. Lava bubble spawned on ground impact
- **CNZ Boss** (Electricity, Obj51) - `Sonic2CNZBossInstance` with electric ball projectiles, animations helper
- **ARZ Boss** (Hammer/Arrow, Obj89) - `Sonic2ARZBossInstance` with arrow projectiles, eye tracking component, destructible pillars
- **Egg Prison** (Obj3E) - End-of-act capsule with button, animal escape sequence, and destruction

### Badniks (15+ New Enemies)

#### CPZ
- **Spiny** (Obj A5) - Wall-crawling spike enemy
- **Spiny on Wall** (Obj A6) - Ceiling variant
- **Grabber** (Obj A7) - Descends to capture player

#### ARZ
- **ChopChop** (Obj 91) - Piranha fish that lunges at player
- **Whisp** (Obj 8C) - Floating dragonfly enemy
- **Grounder** (Obj 8D/8E) - Mole that hides behind breakable wall, throws rock projectiles
  - GrounderWallInstance (Obj 8F) - Breakable wall
  - GrounderRockProjectile (Obj 90) - Rock projectiles

#### HTZ
- **Rexon** (Obj 94/96) - Multi-segment lava-dwelling serpent
  - RexonHeadObjectInstance (Obj 97) - Shootable head segment
- **Sol** (Obj 95) - Fireball-shooting enemy with SolFireballObjectInstance
- **Spiker** (Obj 92) - Drill badnik with SpikerDrillObjectInstance (Obj 93) projectile

#### CNZ
- **Crawl** (Obj C8) - Bouncing boxing glove enemy

#### MCZ
- **Crawlton** (Obj 9E) - Snake that lunges with trailing body segments
- **Flasher** (Obj A3) - Firefly that flashes invulnerability

#### Badnik Framework
- Enhanced `AbstractBadnikInstance` base class
- Improved `AnimalObjectInstance` escape behaviour
- Enhanced `BadnikProjectileInstance` framework
- `PointsObjectInstance` moved to objects package (score popup display)

### Game Objects (50+ New)

#### Platforms and Moving Objects
- **SwingingPlatformObjectInstance** (Obj15) - Chain-suspended pendulum platform (OOZ, ARZ, MCZ)
- **SwingingPformObjectInstance** (Obj82) - ARZ swinging vine platform
- **CPZPlatformObjectInstance** (Obj19) - CPZ rotating/moving platforms
- **ARZPlatformObjectInstance** (Obj18) - ARZ-specific platform
- **MTZPlatformObjectInstance** (Obj6B) - Multi-purpose platform with 12 movement subtypes
- **SidewaysPformObjectInstance** (Obj7A) - CPZ/MCZ horizontal moving platform
- **MCZRotPformsObjectInstance** (Obj6A) - MCZ wooden crate / MTZ rotating platforms
- **ARZRotPformsObjectInstance** (Obj83) - 3 platforms orbiting centre
- **CollapsingPlatformObjectInstance** (Obj1F) - OOZ/MCZ/ARZ collapsing platform
- **SeesawObjectInstance** (Obj14) + **SeesawBallObjectInstance** - HTZ catapult seesaw with ball physics
- **HTZLiftObjectInstance** (Obj16) - HTZ zipline/diagonal lift
- **ElevatorObjectInstance** (ObjD5) - CNZ vertical moving elevator
- **CNZBigBlockObjectInstance** (ObjD4) - CNZ 64x64 oscillating platform
- **CNZRectBlocksObjectInstance** (ObjD2) - CNZ flashing "caterpillar" blocks
- **InvisibleBlockObjectInstance** (Obj74) - Invisible solid block

#### Hazards and Traps
- **RisingLavaObjectInstance** (Obj30) - HTZ invisible solid lava platform during earthquakes
- **LavaMarkerObjectInstance** (Obj31) - HTZ/MTZ invisible lava hazard collision zone
- **LavaBubbleObjectInstance** (Obj20) - Lava bubble visual effects
- **SmashableGroundObjectInstance** (Obj2F) - HTZ breakable rock platform
- **FallingPillarObjectInstance** (Obj23) - ARZ pillar that drops lower section
- **RisingPillarObjectInstance** (Obj2B) - ARZ pillar that rises and launches player
- **ArrowShooterObjectInstance** (Obj22) + **ArrowProjectileInstance** - ARZ arrow shooter trap
- **StomperObjectInstance** (Obj2A) - MCZ ceiling crusher
- **MCZBrickObjectInstance** (Obj75) - MCZ pushable/breakable brick
- **SlidingSpikesObjectInstance** (Obj76) - MCZ spike block sliding from wall
- **TippingFloorObjectInstance** (Obj0B) - CPZ tipping floor
- **BreakableBlockObjectInstance** (Obj32) - CPZ metal blocks / HTZ breakable rocks
- **BlueBallsObjectInstance** (Obj1D) - CPZ chemical droplet hazard
- **BombPrizeObjectInstance** (ObjD3) - CNZ slot machine bomb/spike penalty

#### Interactive Objects
- **SpringboardObjectInstance** (Obj40) - Pressure/lever spring (CPZ, ARZ, MCZ)
- **SpringHelper** - Shared spring velocity calculations
- **SpeedBoosterObjectInstance** (Obj1B) - CPZ/CNZ speed booster pad
- **ForcedSpinObjectInstance** (Obj84) - CNZ/HTZ forced spin (pinball mode trigger)
- **LauncherSpringObjectInstance** (Obj85) - CNZ pressure launcher spring
- **PipeExitSpringObjectInstance** (Obj7B) - CPZ warp tube exit spring
- **BarrierObjectInstance** (Obj2D) - One-way rising barrier (CPZ/HTZ/MTZ/ARZ/DEZ)
- **EggPrisonObjectInstance** (Obj3E) - End-of-act capsule with button, animal escape, destruction
- **SkidDustObjectInstance** - Skid dust particles
- **SplashObjectInstance** - Water splash effect

#### CPZ-Specific Objects
- **CPZSpinTubeObjectInstance** (Obj1E, 895 lines) - Full tube transport system
- **CPZStaircaseObjectInstance** (Obj78) - 4-piece triggered elevator platform
- **CPZPylonObjectInstance** (Obj7C) - Decorative background pylon

#### CNZ-Specific Objects
- **BumperObjectInstance** (Obj44) - Standard round bumper
- **HexBumperObjectInstance** (ObjD7) - Hexagonal bumper
- **BonusBlockObjectInstance** (ObjD8) - Drop target / bonus block (colour-changing, scoring)
- **FlipperObjectInstance** (Obj86) - Pinball flipper
- **CNZConveyorBeltObjectInstance** (Obj72) - Invisible velocity conveyor zone
- **PointPokeyObjectInstance** (ObjD6) - Cage that captures player and awards points
- **RingPrizeObjectInstance** (ObjDC) - Slot machine ring reward
- **CNZBumperManager** (574 lines) - Full bumper system with ROM-accurate bounce physics, 6 bumper types
- **CNZSlotMachineManager** (608 lines) + **CNZSlotMachineRenderer** (549 lines) - Complete slot machine system

#### ARZ-Specific Objects
- **BubbleGeneratorObjectInstance** (Obj24) - Spawns breathable bubbles underwater
- **BubbleObjectInstance** / **BreathingBubbleInstance** - Rising and breathable air bubbles
- **LeavesGeneratorObjectInstance** (Obj2C) + **LeafParticleObjectInstance** - Falling leaves on contact

#### MCZ-Specific Objects
- **VineSwitchObjectInstance** (Obj7F) - Pull switch triggering ButtonVine
- **MovingVineObjectInstance** (Obj80) - Vine pulley transport
- **MCZDrawbridgeObjectInstance** (Obj81) - Rotatable drawbridge triggered by VineSwitch
- **ButtonVineTriggerManager** - MCZ-specific vine routing

### Zone Improvements

#### Emerald Hill Zone (EHZ)
- Full boss fight (Act 2) with multi-component child objects
- Art used as base for HTZ overlay system

#### Chemical Plant Zone (CPZ)
- Full water implementation with oscillation in CPZ2
- Cycling palette implementation (water shimmer, chemical bubbles)
- Spin tubes, staircase platforms, blue balls, speed boosters, breakable blocks
- Spiny, Grabber, and Crawl badniks
- Full boss fight with multi-component gunk dropper
- Multiple collision and positioning fixes (tubes, staircases, platforms, blue balls)

#### Aquatic Ruin Zone (ARZ)
- Water surface sprites for ARZ1 and ARZ2
- Arrow shooters, swinging platforms, rotating platforms, rising/falling pillars
- ChopChop, Whisp, and Grounder badniks
- Leaves generator and leaf particle objects
- Full boss fight with hammer, arrows, and destructible pillars
- Collision fix: boss no longer attackable from the floor

#### Casino Night Zone (CNZ)
- Full bumper system with 6 bumper types and ROM-accurate bounce physics
- Complete slot machine system with shader rendering
- Flipper system (multiple rounds of fixes)
- New parallax scroll handler
- Conveyor belts, elevators, big blocks, rect blocks, point pokey, bonus blocks
- Crawl badnik
- Full boss fight with electric balls
- Physics fix: slopes no longer get stuck

#### Hill Top Zone (HTZ)
- Level resource overlay system: loads EHZ base data with HTZ-specific pattern overlays at byte offset 0x3F80 and block overlays at 0x0980. Shared chunks and collision indices
- Full earthquake system: dual architecture with `Camera_BG_Y_offset` (224-320) for BG vertical scroll and `SwScrl_RippleData` (0-3px) for screen jitter
- Earthquake trigger coordinates: Act 1 camera X >= 0x1800, Y >= 0x400; Act 2 camera X >= 0x14C0
- Rising lava with invisible solid platform, lava markers, lava bubble effects
- HTZ dynamic art loaded from ROM instead of disassembly files
- New parallax scroll handler with correct BG rendering
- Seesaws with ball physics, smashable ground, lifts, launcher springs, barriers
- Rexon, Sol, and Spiker badniks
- Full boss fight with lava flamethrower

#### Mystic Cave Zone (MCZ)
- Crawlton and Flasher badniks
- Bricks, drawbridges, vine switches, moving vines, rotating platforms, stompers, sliding spikes

#### Sky Chase Zone (SCZ)
- `SwScrlScz` (207 lines): ROM-accurate scroll handler with Tornado-driven camera movement
- BG X advances at 0.5px/frame via 16.16 fixed-point accumulator, BG Y always 0
- Act 1 phase system: fly right → descend → resume right, triggered by camera position thresholds

#### Oil Ocean Zone (OOZ)
- Full multi-layer parallax background with oil surface effects (`SwScrlOoz`, 395 lines)

#### Metropolis Zone (MTZ)
- MTZ platform with 12 movement subtypes

#### General Level System
- `LevelEventManager` massively expanded (+1,026 lines): dynamic camera boundaries, boss arenas, zone-specific event triggers, HTZ earthquake coordination
- `OscillationManager` extracted into proper abstraction (drives water oscillation, platform cycles)
- `ParallaxManager` expanded (+249 lines) with enhanced scroll offset calculations
- `BackgroundRenderer` reworked (+324 lines)
- Palette cycling system rewritten: `Sonic2PaletteCycler` (578 lines) with per-zone scripts from ROM
- Tile animation system rewritten: `Sonic2PatternAnimator` (343 lines) with ROM-based scripts
- `Sonic2LevelAnimationManager` consolidating both pattern animation and palette cycling

### Level Resource Overlay System (New)

- `LevelResourcePlan` (221 lines) - Declarative resource loading with overlay composition
- `LoadOp` (49 lines) - Individual load operations with ROM address, compression type, destination offset
- `ResourceLoader` (175 lines) - Performs loading with copy-on-write overlay pattern
- `CompressionType` enum - Nemesis, Kosinski, Enigma, Saxman, Uncompressed
- `Sonic2LevelResourcePlans` (108 lines) - Factory for zone-specific resource plans
- Overlays never mutate cached data (copy-on-write pattern)
- Tests: `LevelResourceOverlayTest` (333 lines)

### Graphics and Rendering

#### Backend Migration
- Complete migration from JOGL to LWJGL for both graphics and audio backends (multiple commits)
- GLFW window management replaces previous windowing system
- Initially tried OpenGL 4.1 core profile, settled on OpenGL 2.1 compatibility profile for broader hardware support
- Fixed shader loading when packaged as JAR
- DPI-aware window scaling via `GLFW_SCALE_TO_MONITOR`

#### GPU Rendering Pipeline
- **Pattern atlas system**: all 8x8 tile patterns uploaded to a single GPU texture (`PatternAtlas`, 326 lines) with multi-atlas fallback and buffer pooling
- **GPU tilemap renderer** (`TilemapGpuRenderer`, 198 lines): dedicated `TilemapShaderProgram` (172 lines) and `TilemapTexture` (78 lines) for GPU-side tile lookup. Covers background, water, and foreground layers. Configurable fallback to CPU rendering
- **Instanced sprite batching** (`InstancedPatternRenderer`, 696 lines): per-instance attributes with `glDrawArraysInstanced`. Enabled by default when supported, automatic fallback to existing batcher. Includes instanced water shader sync
- **Shared fullscreen quad VBO** (`QuadRenderer`, 50 lines): replaces all immediate-mode fullscreen quads across tilemap, parallax, fade, and special stage renderers
- **Priority rendering** (`TilePriorityFBO`, 177 lines): framebuffer object for tile priority bit rendering, enabling correct sprite-behind-tile ordering via GPU
- **Pattern lookup buffer** (`PatternLookupBuffer`, 70 lines): GPU-side pattern index lookup for tilemap shader

#### New Shaders
- `shader_tilemap.glsl` (132 lines) - GPU tilemap lookup and rendering
- `shader_water.glsl` (105 lines) - Water surface effects with palette-based tinting
- `shader_instanced.vert` (27 lines) - Instanced sprite vertex shader
- `shader_instanced_priority.glsl` (93 lines) - Instanced rendering with priority bit
- `shader_sprite_priority.glsl` (91 lines) - Sprite-behind-tile priority rendering
- `shader_cnz_slots.glsl` (131 lines) - CNZ slot machine display
- `shader_debug_text.frag`/`shader_debug_text.vert` (69 lines) - Debug text glyph rendering
- `shader_debug_color.vert`, `shader_basic.vert`, `shader_fullscreen.vert` - Utility shaders

#### UI Render Pipeline
- `UiRenderPipeline` (104 lines): ordered rendering phases (Scene, HUD Overlay, Fade pass)
- `RenderPhase` enum, `RenderCommand` interface, `RenderOrderRecorder` for testing

#### Debug Overlay Rendering
- Batched glyph rendering using GPU-accelerated glyph atlas texture
- Multi-size fonts with smooth anti-aliased outlines
- Proper viewport-space projection and DPI scaling
- Crisp texture filtering, correct Y-flip orientation
- Glyph atlas size increased to 1024x1024
- Bold font for SMALL and MEDIUM debug text sizes, capped at 32pt maximum
- `DebugPrimitiveRenderer` (72 lines) with `DebugColorShaderProgram` for collision/sensor overlays
- Collision overlay accessible via backtick key

#### Other Rendering Changes
- `FadeManager` rewritten for LWJGL compatibility
- `ScreenshotCapture` (231 lines) for visual regression testing
- Slot machine rendering moved from CPU to shader
- VBO sprite rendering

### Audio Engine

#### YM2612 FM Synthesis
- Complete rewrite based on Genesis-Plus-GX (GPGX) reference: SIN_HBITS/ENV_HBITS changed from 12 to 10, LFO changed from 1024-step sine to 128-step inverted triangle, TL table restructured, output clipping changed to asymmetric GPGX-style (+8191/-8192)
- `ENV_QUIET` threshold: when envelope exceeds threshold, operator output forced to 0, causing feedback buffer to naturally decay (matching real hardware)
- SSG-EG (SSG envelope generator) support
- Phase generator detune overflow matching Nemesis-verified real hardware behaviour (DT_BITS = 17)
- Internal sample rate output: YM2612 can output at CLOCK/144 (~53267 Hz) with proper band-limited resampling via `BlipDeltaBuffer` (330 lines) and `BlipResampler` (200 lines)
- Fixed operator routing order and TL position in voice format
- Fixed voice format parsing that was causing corruption and muted instruments
- Fixed low output volume
- Multiple rounds of accuracy improvements (5+ commits)

#### PSG (SN76489)
- New Experimental (Off by Default) PSG implementation with anti-aliasing
- `PsgChipGPGX` (378 lines) added as alternate implementation based on Genesis-Plus-GX (reference for future noise channel work)
- Clock divider fixed to 32.0, Noise Mode 3 corrected
- Clock speed and period calculation fixed
- Extensive spindash release SFX fixes (PSG modulation, note-off, tone bleed, noise channel)
- Default to original PSG after noise channel issues, keeping GPGX as reference

#### SMPS Driver
- Fixed frequency wrapping for high notes
- Fixed E7 command handling
- Fixed octave shifts during modulation/detune
- Fixed fill/gate time logic causing audio desync
- Fixed missing noise channel
- Refactored driver locking to prevent concurrent modification between SFX and music
- `SmpsSequencerConfig` abstraction: configurable per-game (Sonic 1 vs Sonic 2 differences in instrument loading, pitch offsets, noise channel handling)
- Sonic 1 SMPS driver accuracy fixes:
  - PSG envelope 1-based indexing: S1 `subq.w #1,d0` before table lookup; VoiceIndex=0 means no envelope
  - FM voice operator order conversion: S1 (Op4,Op3,Op2,Op1) swapped to engine's S2 format (Op4,Op2,Op3,Op1) on load
  - PC-relative pointer addressing: S1 F6/F7/F8 commands use `dc.w loc-*-1` offsets vs S2 absolute Z80 addresses
  - TIMEOUT tempo mode: S1 uses countdown-based tempo (extend durations on wrap) vs S2 accumulator overflow
  - PSG base note: S1 PSGSetFreq subtracts 0x81 (table starts at C), so `getPsgBaseNoteOffset()` returns 0
  - SFX tempo bypass: S1 SFX have normalTempo=0; skip duration extension in TIMEOUT mode when sfxMode=true
  - First-frame tempo processing matching S1 DOTEMPO behaviour

#### Sound Effects and Music
- Fixed extra life music restore (multiple playbacks no longer break original music)
- Fixed SFX-over-music priority and channel management
- Spindash release SFX: extensive multi-commit effort (14+ commits) fixing looping, noise timing, tone bleed, modulation enable, artifact prevention, overlapping playback. Invalid FM transpose value patched
- Level select: music fade on transitions, ring sound removed, double-fade fixed
- Gloop sound toggle moved from Z80 driver to `BlueBallsObjectInstance`

#### Audio Backend
- Migrated from JOAL to LWJGL OpenAL (`LWJGLAudioBackend`, 427 lines). Includes `WavDecoder` for WAV file support
- Fixed audio quality degradation in LWJGL backend
- Audio latency reduced to 16ms (one frame)
- Window minimize/restore handling: pauses audio so music doesn't play in background

#### Audio Performance
- Eliminated per-sample allocations in VirtualSynthesizer, SmpsSequencer, SmpsDriver scratch buffers
- Audio engine performance optimisations verified via regression tests

### Manager Consolidation Refactor

#### ObjectManager
- `ObjectManager.java` grew from ~200 to ~1,917 lines, absorbing 4 removed managers as inner classes:
  - `ObjectManager.Placement` (was `ObjectPlacementManager`) - Spawn windowing, remembered objects
  - `ObjectManager.SolidContacts` (was `SolidObjectManager`, -455 lines) - Riding, landing, ceiling, side collision
  - `ObjectManager.TouchResponses` (was `TouchResponseManager`, -195 lines) - Enemy bounce, hurt, category detection
  - `ObjectManager.PlaneSwitchers` (was `PlaneSwitcherManager`, -143 lines) - Plane switching logic

#### RingManager
- Consolidated from 3 separate managers as inner classes:
  - `RingManager.RingPlacement` (was `RingPlacementManager`, -93 lines) - Collection state, sparkle animation
  - `RingManager.RingRenderer` (was `RingRenderManager`, -114 lines) - Ring rendering with cached patterns
  - `RingManager.LostRingPool` (was `LostRingManager`, -304 lines) - Lost ring physics, object pooling

#### PlayableSprite Controller
- `PlayableSpriteController` (38 lines) coordinator owned by `AbstractPlayableSprite`:
  - `PlayableSpriteMovement` (1,814 lines, replaces `PlayableSpriteMovementManager`)
  - `PlayableSpriteAnimation` (renamed from `PlayableSpriteAnimationManager`)
  - `SpindashDustController` (renamed from `SpindashDustManager`)
  - `DrowningController` (289 lines, new)
- Removed `SpriteCollisionManager` (-131 lines)

#### CollisionSystem
- `CollisionSystem` (214 lines) unifying terrain probes and solid object collision
- `CollisionTrace` (40 lines), `RecordingCollisionTrace` (121 lines), `NoOpCollisionTrace` (25 lines), `CollisionEvent` (44 lines)

#### Animation System
- `Sonic2LevelAnimationManager` consolidating `AnimatedPatternManager` and `AnimatedPaletteManager`
- `Sonic2PatternAnimator` renamed from `Sonic2AnimatedPatternManager`
- `Sonic2PaletteCycler` (578 lines) replacing `Sonic2PaletteCycleManager` (-143 lines)

### Object System Framework

- `ObjectArtKeys` - Game-agnostic art key constants
- `MultiPieceSolidProvider` interface for objects with multiple solid collision pieces
- `SlopedSolidProvider` interface for sloped solid objects
- Enhanced `AbstractObjectInstance` (+55 lines), `ObjectInstance` interface (+34 lines)
- `ObjectRenderManager` significantly enhanced rendering pipeline
- `ObjectArtData` enhanced art loading (+169 lines)
- `HudRenderManager` enhanced HUD rendering (+155 lines)
- `SolidObjectProvider` extended interface
- Game-specific art loading pattern: `Sonic2ObjectArt`, `Sonic2ObjectArtProvider`, `Sonic2ObjectArtKeys`
- LayerSwitcher (Obj03) handled by PlaneSwitchers subsystem, not as rendered object

### Level Select

- `LevelSelectManager` (762 lines) - Full level select screen with keyboard navigation, zone/act selection, music playback
- `LevelSelectDataLoader` (485 lines) - Loads graphics, fonts, and preview images from ROM
- `LevelSelectConstants` (240 lines) - ROM addresses and layout data
- Palette loaded from ROM
- Menu background: `MenuBackgroundAnimator`, `MenuBackgroundDataLoader`, `MenuBackgroundRenderer` (292 lines total)
- Sound test integration via shared `Sonic2SoundTestCatalog`
- Configurable via `LEVEL_SELECT_ENABLED` config key
- Palette reset on returning to level select from gameplay
- Music fade on level select transitions

### Testing Infrastructure

#### HeadlessTestRunner
- `HeadlessTestRunner` (137 lines): physics/collision integration tests without OpenGL context
- `stepFrame(up, down, left, right, jump)` to simulate one frame with input
- `stepIdleFrames(n)` for stepping multiple idle frames
- Calls `Camera.updatePosition()`, `LevelEventManager.update()`, `ParallaxManager.update()` each frame

#### Physics and Collision Tests
- `TestHeadlessWallCollision` (133 lines) - Ground collision and walking physics
- `TestPlayableSpriteMovement` (1,483 lines) - Comprehensive movement physics tests
- `CollisionSystemTest` (460 lines) - Unified collision pipeline
- `WaterPhysicsTest` (250 lines) - Underwater physics
- `WaterSystemTest` (178 lines) - Water level system

#### Zone-Specific Tests
- `TestCNZCeilingStateExit` (403 lines), `TestCNZFlipperLaunch` (190 lines), `TestCNZForcedSpinTunnel` (193 lines), `SwScrlCnzTest` (454 lines)
- `TestHTZBossArtPalette`, `TestHTZBossChildObjects` (181 lines), `TestHTZBossEventRoutine9`, `TestHTZBossTouchResponse` (133 lines)
- `TestHTZInvisibleWallBug` (731 lines), `TestHTZRisingLavaDisassemblyParity` (115 lines), `TestSwScrlHtzEarthquakeMode` (86 lines), `TestHtzSpringLoop` (197 lines)
- `SwScrlOozTest` (487 lines), `TestOozAnimation` (248 lines), `TestPaletteCycling` (101 lines)

#### Visual Regression Tests
- `VisualRegressionTest` (393 lines) - Screenshot comparison testing
- `VisualReferenceGenerator` (265 lines) - Generate reference screenshots
- `ScreenshotCapture` (231 lines) - Headless screenshot capture
- Reference images for EHZ, CPZ, CNZ, HTZ, MCZ

#### Audio Regression Tests
- `AudioRegressionTest` (370 lines) - Audio output comparison
- `AudioReferenceGenerator` (302 lines) - Generate reference audio
- `AudioBenchmark` (109 lines) - Audio performance benchmarks
- Reference WAVs for EHZ/CPZ/HTZ music, jump/ring/spring/spindash SFX
- `TestSmpsSequencerInstrumentLoading` - SMPS instrument loading verification

#### Other Tests
- `TestSignpostWalkOff` - Signpost walk-off regression test
- `TestTailsCpuController` - Tails AI state machine and input replay
- `TestObjectManagerLifecycle` (108 lines), `TestObjectPlacementManager` (40 lines), `TestSolidObjectManager` (148 lines)
- `BossStateContextTest` (220 lines), `FadeManagerTest` (535 lines), `RenderOrderTest` (181 lines)
- `PatternAtlasFallbackTest` (33 lines), `TestSpriteManagerRender` (211 lines)
- `LevelResourceOverlayTest` (333 lines)

#### Test Annotation Framework
- `@RequiresRom(SonicGame.SONIC_1)` annotation for tests needing a real ROM file
- `@RequiresGameModule(SonicGame.SONIC_1)` annotation for tests needing a game module without ROM
- `RequiresRomRule` JUnit rule with per-game ROM resolution and auto-detection
- `SonicGame` enum: `SONIC_1`, `SONIC_2`, `SONIC_3K`
- `RomCache` for shared ROM instances across test classes

### Performance Optimisations

- Pre-allocated command lists in `LevelManager` (collisionCommands, sensorCommands, cameraBoundsCommands) using `.clear()` instead of `new ArrayList<>()` each frame
- ObjectManager and SpriteManager pre-bucketing with dirty flag to avoid re-sorting every frame
- `Sonic2SpecialStageRenderer` PatternDesc reuse instead of per-frame allocation
- Lost ring object pooling to reduce allocations during ring scatter
- Reduced per-frame allocations in collision, rendering, and audio hot paths
- Debug overlay buffer reuse
- Per-sample allocation elimination in audio synthesis pipeline
- `PerformanceProfiler` with memory stats (GC and allocation timers), Ctrl+P copies all stats to clipboard
- General memory allocation reduction passes across the engine

### GraalVM Native Build Support

- GraalVM Native Image plugin configuration in `pom.xml`
- GitHub Actions release workflow (`.github/workflows/release.yml`, 128 lines) and CI workflow
- GraalVM configuration files: `native-image.properties`, `reflect-config.json`, `resource-config.json`, `jni-config.json`
- LWJGL migration (both graphics and audio) as prerequisite for GraalVM compatibility
- `run.cmd` for Windows execution

### Tooling

#### RomOffsetFinder Enhancements
- `verify <label>` command - Verifies calculated offset against actual ROM data
- `verify-batch [type]` command - Batch verify all items of a type (shows [OK], [!!] mismatch, [??] not found)
- `export <type> [prefix]` command - Export verified offsets as Java constants
- Offset validation for searched items
- Multi-game support via `--game` flag: `--game s1`, `--game s2` (default), `--game s3k`
- `GameProfile` with per-game anchor offsets, label prefixes, ROM filenames, and disasm paths
- Auto-detection from disassembly path (`s1disasm` → S1, `skdisasm` → S3K)
- Expanded anchor offsets in `RomOffsetCalculator` for improved accuracy
- `CompressionTestTool` auto-detect compression type at offset
- Kosinski Moduled (KosM) decompression support in `KosinskiReader.decompressModuled()` — container format wrapping multiple standard Kosinski modules with 16-byte aligned padding, used extensively by Sonic 3&K art assets
- Palette macro parsing support from disassembly

#### New Tools
- `WaterHeightFinder` (114 lines) - Finds water height data in ROM
- `AudioSfxExporter` (261 lines) - Exports SFX audio data
- `SoundTestApp` refactored to use shared `Sonic2SoundTestCatalog` with channel mute/solo support

#### Claude Skills
- `.claude/skills/implement-object/SKILL.md` (410 lines) - Guided object implementation workflow
- `.claude/skills/implement-boss/skill.md` (332 lines) - Boss implementation workflow
- `.claude/skills/s2disasm-guide/skill.md` (302 lines) - Disassembly reference guide

### Other Changes

- Per-game ROM configuration: `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM` config keys with `DEFAULT_ROM` selector; `ROM_FILENAME` removed entirely
- Pause functionality (default key: Enter) and frame step when paused (default key: Q)
- `ConfigMigrationService` (95 lines) for evolving configuration format
- `GameStateManager` (104 lines) for score, lives, emeralds state management
- Window minimize/restore: pauses audio and rendering to prevent background playback and catch-up
- `docs/KNOWN_DISCREPANCIES.md` (161 lines) documenting intentional divergences from original ROM
- Old markdown docs archived to `docs/archive/`
- `AGENTS.md` and `CLAUDE.md` extensively updated with architecture documentation

---

## v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with
a few known issues. Physics have been improved, parallax backgrounds implemented and complete for
EHZ, CPZ, ARZ and MCZ. Some sound improvements, title cards, level 'outros' etc.

## v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and Badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SoundFX and music are implemented. Everything has room for improvement, but this
now resembles a playable game.

## V0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

## V0.01 (Pre-Alpha) (Unreleased; first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
