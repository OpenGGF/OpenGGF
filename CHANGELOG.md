# Changelog

All notable changes to the OpenGGF project are documented in this file.

## v0.6.prerelease (Current development snapshot)

- Implemented the S3K Launch Base Zone Act 1 interior reveal screen events.
  Entering the ROM trigger rectangles now copies the hidden staging chunk cells
  into the visible foreground layout, and leaving the corresponding exit ranges
  restores the covered cells.

- **OOZ popping platform (Obj33) auto-pop starts in mode 2 not mode 0 — fires one frame later, matching ROM routine_secondary init (advances OOZ1 f1133→f1756):** `OOZPoppingPlatformObjectInstance` (Obj33, OOZ green burner lid) initialised the auto-pop (subtype 0) variant directly into `TIMER_COUNTDOWN` (mode 0). ROM `Obj33_Init` (docs/s2disasm/s2.asm:49653-49657) sets `routine_secondary` to **2** (`addq.b #2,routine_secondary`) for **all** variants, and only overrides it to 4 (wait-for-player) when `subtype != 0`. So the auto-pop variant first runs one mode-2 frame (`loc_23BEA`, s2.asm:49710-49728) with `objoff_32` (velocity) = 0: y stays at home, velocity becomes `$3800` (< `$10000`), so it does `subq.b #2,routine_secondary` back to mode 0 and applies the bounce — the `$78` timer countdown therefore starts the *next* frame, making every auto-pop fire one frame later than starting straight in the timer. Starting in mode 0 popped the platform a frame early, dragging the riding sidekick down a frame too soon (OOZ1 f1133: `y` 0x0664 vs 0x065A while riding the popping platform). Fix: subtype-0 variant now initialises to `POP_PHYSICS` (mode 2); `updatePopPhysics()` with velocity 0 already reproduces the ROM's immediate transition-to-timer + bounce. Per-object S2 change in Obj33's own class, branching on the ROM `subtype` data field — no zone/route/frame/gameId carve-out, no shared physics change, comparison-only. OOZ1 f1133→f1756 (new divergence is an unrelated sidekick CPU `tails_x` follow-steering off-by-one). Same-game regression guard single-fork: EHZ1/SCZ/WFZ all green, zero regressions. See docs/TRACE_FRONTIER_LOG.md.
- **CPZ spin tube (Obj1E) waypoint subpixel + Timer_second entry-path parity (advances CPZ2 f2542→f2888):** `CPZSpinTubeObjectInstance` snapped each captured waypoint with `setCentreX`/`setCentreY`, which zero the player's 16-bit subpixel fraction. ROM `Obj1E` writes every waypoint with a word `move.w d4,x_pos(a1)` / `move.w d5,y_pos(a1)` (loc_22688 docs/s2disasm/s2.asm:48531-48545, loc_2271A s2.asm:48577-48586, loc_227FE s2.asm:48655-48662) — preserving the subpixel low word that the loc_22902 velocity recompute (`sub.w x_pos(a1),d0`, s2.asm:48761-48815) integrates over. The four waypoint snaps now use `setCentreXPreserveSubpixel`/`setCentreYPreserveSubpixel`. Separately, the timer-alternated entry-path selector (`byte_2266E` value 2 → `move.b (Timer_second).w,d2 / andi.b #1,d2`, loc_2265E s2.asm:48499-48503) read the live on-screen TIME seconds digit; the engine derived it from the raw replay frame counter (`frameCounter/60`), which diverges whenever the act starts with a non-zero level timer and flips the path parity. `update()` now reads `services().levelGamestate().getElapsedSeconds()` (== ROM Timer_second, and `& 1` is parity-equal since 60 is even). S2-only object class; no zone/route/frame/gameId carve-out, comparison-only (no trace hydration). New f2888 divergence is post-tube-exit sidekick `tails_x` follow physics, a distinct subsystem. Same-game regression guard single-fork: EHZ1/SCZ/WFZ all green, no regression. See docs/TRACE_FRONTIER_LOG.md.
- **CPZ SpinyOnWall (ObjA6) detection + spike-fire parity — ROM horizontal-only attack band, closest-player selection, fixed x_flip fire direction (advances CPZ1 f3303→f3329):** `SpinyOnWallBadnikInstance` (CPZ wall spiny, ObjA6) had invented a `0x80`×`0x40` box plus a "player must be in front" facing gate before firing, and offset the spawned spike by ±8px from the body. ROM `loc_38BBA` (docs/s2disasm/s2.asm:76445-76449) calls `Obj_GetOrientationToPlayer` then gates *only* on `addi.w #$60,d2 / cmpi.w #$C0,d2 / blo` — the signed horizontal distance to the *closer* of MainCharacter/Sidekick must lie in `[-$60,$60)`; there is no vertical gate and no facing gate. `Obj_GetOrientationToPlayer` (s2.asm:72755-72781) picks the closer character by absolute horizontal distance and leaves `d2` as that signed distance. The fire routine `loc_38C6E` (s2.asm:76509-76526) spawns the spike at the spiny's exact `x_pos`/`y_pos` with `x_vel=$300` negated by `render_flags.x_flip` (the spiny's fixed flip, not player side), `y_vel=0`, and `Obj98_SpinyShotFall` (s2.asm:74628-74632) then applies `+$20` gravity. The engine's extra gates fired the spike at the wrong frames and the ±8 muzzle offset shifted its landing point; in CPZ1 the resulting falling spike struck CPU Tails — a hurt the ROM never produces. Fix replaces the box/facing logic with the ROM `(dx+$60)<$C0` horizontal band against the closest player (`closestPlayer` mirrors the `Obj_GetOrientationToPlayer` selection via `ObjectPlayerQuery`), and removes the muzzle offset. Per-object S2 change confined to ObjA6's own class — no zone/route/frame/gameId carve-out, no shared physics edit, comparison-only. CPZ1 f3303→f3329 (errors 231→214; new owner is an unrelated downstream player `y_speed`/`tails_air` jump divergence). Same-game guard single-fork: EHZ1/SCZ/WFZ all green, zero regressions. See docs/TRACE_FRONTIER_LOG.md.
- **MCZ Monitor (Obj26) inclusive right solid edge — ROM `bhi` bound keeps the right-edge-exact contact (advances MCZ2 f4252→f4485):** `MonitorObjectInstance` (Obj26) now overrides `usesInclusiveRightEdge()` to `true`. ROM Obj26 `SolidObject_Monitor_*` (docs/s2disasm/s2.asm:25586-25631) loads the monitor's width `move.w #$1A,d1` (s2.asm:25587) and falls through to `SolidObject_cont`, whose X-bounds gate is `cmp.w d3,d0 / bhi.w SolidObject_TestClearPush` with `d3 = d1*2` (the full width) (docs/s2disasm/s2.asm:35347-35348). `bhi` is an unsigned *strictly-greater* test, so a player whose relative X equals exactly `width*2` — i.e. centred on the monitor's right solid edge — does NOT branch out: it stays inside the box and resolves as a zero-distance side contact in `SolidObject_AtEdge` (docs/s2disasm/s2.asm:35427-35446), which sets the pushing bit without shoving `x_pos`. The engine default uses an exclusive (`>=`) right bound (`ObjectManager` line 7935: `relX > width2 || (!inclusiveRightEdge && relX == width2)`), which dropped that boundary pixel: in MCZ2 the CPU Tails walking right into the Obj26 monitor at x=0x0E10 reached centreX 0x0E2A (= 0x0E10 + $1A, the exact right edge), the exclusive gate returned no contact, Tails was never pinned/pushing, and the divergence appeared as `tails_x` (ROM expected 0x0E2A held against the monitor vs engine 0x0E2C drifting past). Matching the ROM `bhi` semantics via the existing per-object `usesInclusiveRightEdge()` hook (already used by springs/flippers/spikes) keeps the right-edge contact. Per-object S2 fix in Obj26's own class through the narrowest owning abstraction (no `if (gameId==…)`, no zone/route/frame carve-out, no tolerance band, comparison-only). MCZ2 f4252→f4485 (704→527 errors; new divergence f4485 is a downstream CPU-Tails subpixel-rounding `tails_x` 0x0EAB vs 0x0EAC near the same monitor cluster). Same-game regression guard single-fork: EHZ1/SCZ/WFZ all green, zero regressions. See docs/TRACE_FRONTIER_LOG.md.
- **ARZ ChopChop (Obj91) X movement parity — ObjectMove subpixel integration (advances ARZ2 f549→f566):** `ChopChopBadnikInstance` (Aquatic Ruin piranha, Obj91) applied its patrol X velocity as `currentX += (xVelocity >> 8)`. Patrol x_vel is `move.w #$40,x_vel(a0)` (docs/s2disasm/s2.asm:73621-73626) = 0x40 = 0.25px/frame, and `0x40 >> 8 == 0`, so the badnik never moved horizontally and its position drifted from ROM. Both Obj91 movement states call `JmpTo26_ObjectMove` (s2.asm:73642, 73688), and `ObjectMove` integrates `x_pos(32) += x_vel<<8` (s2.asm:30185-30199) — the low byte of x_vel carries into the sub-pixel and into the whole pixel. The fix routes both states through a shared 16:8 X accumulator (`applyXVelocitySubpixel`). Charge X (`Obj91_HorizontalSpeeds`, s2.asm:73678-73680) is ±2 whole pixels written via `move.b` to the high byte (x_vel = ±0x200, zero subpixel carry); charge Y (`Obj91_VerticalSpeeds`, s2.asm:73682-73684) is $80 to the low byte of y_vel = 0.5px/frame. Per-object change in ChopChop's own class — no zone/route/frame/gameId carve-out, no shared-code edit, comparison-only. ARZ2 f549→f566 (new divergence f566 is an unrelated sidekick CPU `tails_x_speed` follow-steering issue). Same-game guard single-fork: EHZ1/SCZ/WFZ green, no regression. See docs/TRACE_FRONTIER_LOG.md.
- **OOZ Fan push preserves sub-pixel (advances OOZ1 f756→f1133):** The OOZ Fan (`FanObjectInstance`, Obj3F) applied its wind push with `setCentreX`/`setCentreY`, which zero the player's sub-pixel fraction. ROM `Obj3F_Horizontal` (`add.w d0,x_pos(a1)`, docs/s2disasm/s2.asm loc_2A8F8) and `Obj3F_Vertical` (`add.w d1,y_pos(a1)`, docs/s2disasm/s2.asm loc_2A990) add the push directly to the 16-bit position (pixel) word and leave `x_sub`/`y_sub` untouched. Zeroing the sub-pixel every fan-push frame dropped ~0x9C00 of accumulated `y_sub`, producing a 1-pixel-Y carry divergence one frame later (OOZ1 f756: ROM `y_sub` 9C00 vs engine 0000). Fix: push via `shiftX(push)`/`shiftY(push)` (`xPixel += delta`/`yPixel += delta`, sub-pixel preserved), matching the ROM `add.w d,pos(a1)` semantics. S2-only object class, no shared physics change, no gameId/zone/route/frame carve-out, comparison-only. OOZ1 f756→f1133 (new divergence is an unrelated OOZPoppingPform carry issue). Same-game regression guard single-fork: EHZ1/SCZ/WFZ all green. See docs/TRACE_FRONTIER_LOG.md.
- **MTZ button off-screen render-flag gate (advances MTZ1 f863→f1000):** S2 Button (Obj47) now skips its entire main routine when off-screen, matching ROM `Obj47_Main` which gates everything behind `render_flags.on_screen` before the `SolidObject` call and the `bclr/bset` on `ButtonVine_Trigger` (`_btst #render_flags.on_screen,render_flags(a0)` / `_beq.s BranchTo_JmpTo12_MarkObjGone`, docs/s2disasm/s2.asm:50825-50847). The engine previously ran the button routine every frame regardless of visibility, so a far off-screen unpressed button sharing a switch id with an on-screen pressed button cleared the shared trigger bit each frame via `bclr d3,(a3)` — MTZ1 has two Obj47 buttons on switch 0 (x=0x06CC and x=0x0858), and the off-screen one clobbered the press so the subtype-7 long platform never retracted, dropping Sonic through the floor (mtz1 f863 air/y divergence). `ButtonObjectInstance.update` now early-returns when `!isWithinSolidContactBounds()` (the engine's render_flags bit-7 / `width_pixels` model; button `width_pixels=0x10`=16 matches the default on-screen half-width). Models ROM render-flag visibility, not a zone/route/frame carve-out; comparison-only. MTZ1 f863→f1000 (new owner an unrelated Sonic `g_speed` air divergence). Same-game greens unchanged: EHZ1/SCZ/WFZ all green. See docs/TRACE_FRONTIER_LOG.md.
- **DEZ Mecha Sonic Aim&Dash wind-up length + boss touch-collision 1-frame lag (advances DEZ1 f1023→f1366):** Two ROM-faithful corrections to `Sonic2MechaSonicInstance`, both confined to that boss class. (1) `ANIM_3_SPEEDUP` had 21 displayed frames (three leading `3`s). ROM `byte_39DFE` (docs/s2disasm/s2.asm:78141-78142) is `dc.b 3, 3,3, 6,6,6, ... ,$FC`: byte[0]=$3 is the animation SPEED (held separately in `ANIM_SPEEDS[3]`), leaving **20** displayed frames (two leading `3`s) — confirmed by the sibling scripts `byte_39DEE`/`byte_39DF4`/`byte_39DF8` (s2.asm:78135-78140) whose byte[0] is likewise the speed. `AnimateSprite_Checked` holds each frame speed+1 (=4) game frames, so the spurious frame stretched the Aim&Dash wind-up (`loc_39A1C`) by 4 frames per attack cycle, delaying the dash and shifting the boss body ~0x1F px right by the second DEZ attack — so the rolling player's boss-hit overlap registered ~4 frames late and the ROM deflection (`neg.w x_vel`/`neg.w y_vel`, Touch_Enemy multi_sprite branch, s2.asm:85261-85276) never fired at f1023. (2) ROM `loc_398C0` (s2.asm:77570-77584) calls `loc_39D1C` to refresh `collision_flags` from `mapping_frame` (`loc_39D24`, s2.asm:78013-78039: $1A standing vs $9A ball) **before** the per-routine handler runs `AnimateSprite_Checked`, so the touch category lags the displayed frame by one frame. The engine computed it from the live frame; now a `collisionFrame` latch captured at the top of `updateBossLogic` reproduces the lag, so a rolling fall onto the boss takes the standing-form deflect rather than a HURT. No gameId/zone/route/frame carve-out, no tolerance band, comparison-only. DEZ1 f1023→f1366 (errors 199→148; new first error is a single-frame post-defeat camera-unlock off-by-one, distinct subsystem). Same-game regression guard EHZ1/SCZ/WFZ green; Mecha Sonic is a DEZ-only S2 boss so no shared physics/cross-game impact. See docs/TRACE_FRONTIER_LOG.md.
- **CNZ2 vertical-flipper sidekick parity — per-player standing state + shared launch trigger (advances CNZ2 f1775→f1784):** `FlipperObjectInstance` (Obj86 `Obj86_UpwardsType`) now models the ROM's per-player vs shared object-variable split exactly. ROM calls `loc_2B20A` **twice** — once for the MainCharacter against its own standing byte `objoff_36(a0)` (`Ctrl_1_Logical`, `p1_standing_bit`) and once for the Sidekick against `objoff_37(a0)` (`Ctrl_2`, `p2_standing_bit`) — so the "first-stand vs already-on" branch (`move.b (a3),d0 / bne`) is **per player** (docs/s2disasm/s2.asm:58281-58332). The launch trigger `objoff_38(a0)` is instead a **single shared byte**: `loc_2B288` sets it when *either* standing player presses jump, and after both passes `Obj86_UpwardsType` checks it once and calls `loc_2B290` for *both* the Sidekick and the MainCharacter, launching every player whose standing bit is still set (docs/s2disasm/s2.asm:58296-58303, 58361-58407). The engine had used one shared `int` for standing state (so Sonic's stand suppressed Tails' first-stand seating — Tails seated 5px too high with no rolling bit, st=09 vs ROM st=0D, CNZ2 f1775) and gated launch per-player on `isJumpPressed()` (so a leader-jump never launched the CPU sidekick — Tails seated forever, f1783 `tails_air` expected=1 actual=0). Fix makes `playerFlipperState`/lock-tracking per-player `IdentityHashMap`s and adds a shared `verticalLaunchTriggered` pass (`processVerticalLaunch`) run once after both players are processed. The first-stand +5 y_pos nudge (`addq.w #5,y_pos`, docs/s2disasm/s2.asm:58323-58325) now writes `centre += 5` directly via `setCentreYPreserveSubpixel` (the legacy `getRollHeightAdjustment()` only netted the correct +5 for Sonic, seating Tails 4px high). No zone/route/frame/gameId carve-out — Obj86 is S2-specific object code modelling actual per-player object RAM. CNZ2 f1775→f1784 (857 errors; Tails now launches, new divergence is the launch *velocity* magnitude `tails_y_speed`, a downstream `loc_2B290` sine-launch issue). Same-game guards green: EHZ1, SCZ, WFZ unchanged. See docs/TRACE_FRONTIER_LOG.md.
- **MCZ vine off-screen sidekick release (advances MCZ1 f2181→f2362):** S2 Obj80 (`MovingVineObjectInstance`, the MCZ pulley vine / WFZ hook) now models the ROM `Obj80_Action` off-screen release branch. ROM tests `render_flags.on_screen` on the grabbed character and branches to `loc_29B42` — clearing `obj_control(a1)`, clearing the grab flag, and setting a 60-frame release delay with **no** jump velocity — *before* the `routine>=4` and A/B/C-press checks (docs/s2disasm/s2.asm:56707-56708, 56741-56744). The engine deliberately skipped this on-screen check, and the only modeled release for a CPU sidekick was a raw `Ctrl_2` A/B/C press, which a CPU Tails can never satisfy (it writes only `Ctrl_2_Logical`). So when the camera tracked Sonic right and the pinned vine-grabbed Tails scrolled off the left edge, the engine kept Tails pinned (`x_speed=0`) while ROM had released it to resume normal CPU following / free-fall. Fix adds the off-screen-release branch gated on `player.hasRenderFlagOnScreenState() && !player.isRenderFlagOnScreen()` (the `render_flags.on_screen` bit, refreshed every frame by `SpriteManager` via `camera.isVisibleForRenderFlag`), calling the existing no-jump release path. Per-object S2 fix inside Obj80's modeled routine — not shared physics, no `PhysicsFeatureSet` flag (S1/S3K have no Obj80 equivalent), no zone/route/frame/gameId carve-out, comparison-only. MCZ1 f2181→f2362 (new divergence is an unrelated sidekick CPU-jump-application issue, out of scope). Same-game regression guard: EHZ1 / SCZ / WFZ stay green; MCZ2 still first-diverges at its pre-existing f4009 (not a regression). See docs/TRACE_FRONTIER_LOG.md.
- **CPZ spin tube captures the sidekick — per-character routine matching ROM Obj1E_Main dual-slot dispatch (advances CPZ2 f2518→f2542):** The ROM `Obj1E_Main` runs the spin-tube capture + path-follow routine once per playable character each frame — once for `MainCharacter` using state slot `objoff_2C(a0)`, then once for `Sidekick` using `objoff_36(a0)` (docs/s2disasm/s2.asm:48447-48457) — and its capture gate (`loc_225FC`, s2.asm:48467-48480) is ONLY: not Debug_placement_mode, x distance < `objoff_2A`, y distance < 0x80, and `anim(a1) != $20`; there is no rolling / obj-control / cooldown gate. The engine had a single shared tube-state slot (so only the main player could be in the tube) plus engine-invented rolling/`TUBE_EXIT_COOLDOWN_FRAMES`/recently-released guards that blocked the rolling Tails sidekick from ever being captured, leaving it to free-fall under gravity instead of being pinned to the tube's 0x800 velocity (CPZ2 trace f2518: ROM `tails_y_speed`=0x0800 tube velocity vs engine 0x04D0 gravity fall). Fix: `CPZSpinTubeObjectInstance` now keeps one independent `CharacterState` slot per playable (IdentityHashMap keyed on the sprite), runs the state machine against the main player AND every sidekick each frame (the objoff_2C/objoff_36 two-pass dispatch), replaces the engine guards with the ROM `anim != $20` gate, and keeps `isPersistent()` true while any character is mid-tube. No zone/route/frame/gameId carve-out — Obj1E is an S2-only object and the change is confined to that object class; comparison-only. CPZ2 f2518→f2542 (errors 1646→1041; new divergence is the next downstream tube `tails_y_speed` step). Same-game guard EHZ1/SCZ/WFZ all green; CPZ1 first-error frame unchanged at f2822. See docs/TRACE_FRONTIER_LOG.md.
- **CPZ Speed Booster (Obj1B) per-character boost + move_lock parity (advances CPZ1 f2822→f3303):** ROM `Obj1B_Main` (docs/s2disasm/s2.asm:48166-48211) computes one +/-$10 box and checks **both** characters against it independently — `lea (MainCharacter).w,a1` (s2.asm:48179-48194) then `lea (Sidekick).w,a1` (s2.asm:48196-48209) — calling `Obj1B_GiveBoost` for each grounded one. The engine had run the box-check against only the single main player, so CPU Tails was never boosted inside a booster pad. Now `SpeedBoosterObjectInstance.update()` also iterates `services().playerQuery().sidekicks()` (participation model, no zone/route/gameId carve-out). Additionally `Obj1B_GiveBoost` (s2.asm:48215-48238) sets `move.w #$F,move_lock(a1)` (s2.asm:48230) — the `move_lock` timer, not a spring flag — which `SidekickCpuController` reads via `getMoveLockTimer()`; the prior `setSpringing()` was a no-op for CPU Tails so a boosted sidekick immediately resumed following. Switched to `setMoveLockTimer(0xF)` (equivalent for the main character, which `PlayableSpriteMovement` blocks identically on either field), and added the missing `bclr #status.player.pushing,status(a1)` (s2.asm:48234) via `setPushing(false)`. CPZ1 f2822→f3303 (new owner is a downstream `tails_air` divergence on the now-boosted Tails). Same-game guard green (EHZ1/SCZ/WFZ failures=0). See docs/TRACE_FRONTIER_LOG.md.
- **MTZ3 dead-sidekick fall parity — dead CPU Tails fall bypasses the `Screen_Y_wrap` mask on the SpriteManager dead-fall path (advances MTZ3 f3719→f6913):** The static `SpriteManager.applyScreenYWrapValueAfterControl(playable)` variant unconditionally applied the `Screen_Y_wrap_value` mask to a dead CPU sidekick's falling body. ROM `Obj02_Dead` (docs/s2disasm/s2.asm:41125-41131) runs `jsr (ObjectMoveAndFall).l` (s2.asm:30158-30173 — plain `y_pos += y_vel` with no wrap mask) and only despawns once `Obj02_CheckGameOver` (s2.asm:41136-41149) sees `y_pos > Tails_Max_Y_pos + $100`, branching to `TailsCPU_Despawn` (s2.asm:39391+). Masking the falling body at the 0x800 wrap boundary (`0x0807 & 0x07FF = 0x0007`) meant `getCentreY()` never crossed the despawn threshold, so MTZ3 diverged on `tails_y` at f3719. Fix mirrors the already-proven bypass in `PlayableSpriteMovement.applyScreenYWrapValueAfterControl()`, gated by `SidekickCpuController.deadFallBypassesScreenYWrapValue()` → S2/S3K `PhysicsFeatureSet.sidekickDeathUsesDeferredDespawn` (S1 = false; S1 has no Tails CPU sidekick). No gameId/zone/route/frame carve-out; comparison-only (reads engine state only, never trace data). MTZ3 f3719→f6913 (new owner is an unrelated downstream main-player `g_speed` divergence). Same-game guard EHZ1/SCZ/WFZ stay green (zero regressions). See docs/TRACE_FRONTIER_LOG.md.
- **MCZ rotating-platform subtype-0x18 parent is a real solid/rendering platform (advances MCZ2 f4009→f4049):** `MCZRotPformsObjectInstance` (Obj6A) treated the MCZ subtype-0x18 parent as an invisible non-solid spawner that only allocated its two child platforms once on the spawn frame. In the ROM (`docs/s2disasm/s2.asm` Obj6A_Init), the `cmpi.b #$18,subtype` / `bne.w` check gates **only** the child-spawn block; after allocating its two children the parent falls through (`bra.s loc_27BC4` → `loc_27BD0` → `loc_27CA2`) and runs routine 4 (`loc_27C66`) every frame as a full moving/solid/rendering platform via `JmpTo13_SolidObject`. Obj6A_Init sets the Crate art tile and `mapping_frame=0` with no invisibility flag, so the parent renders and collides like any other Obj6A platform. Fix: `isSolidFor` returns `!isDestroyed()` (no longer excludes the parent); `update()` spawns the children once then continues into the same MCZ move/collide path the children use (`phaseIndex=0x18` reads the table normally); `appendRenderCommands` no longer early-returns for the parent. The `isParent` flag is still derived from ROM subtype `0x18` in MCZ — no zone/route/frame/gameId carve-out, comparison-only. MCZ2 f4009→f4049 (the new f4049 divergence is a downstream Tails `tails_y_speed` mismatch, unrelated to the parent platform). Same-game greens EHZ1/SCZ/WFZ stay green; no S2 frontier moved backward (mcz1 2181/mtz1 863/mtz2 641/mtz3 3719 unchanged). See docs/TRACE_FRONTIER_LOG.md.

- **S2 object load-cadence parity — post-camera gap-scan now applies the S2 vertical-filter bypass (restores CNZ1 f3831→f3906, advances HTZ2 f1078→f2306):** The engine's post-camera spawn-window catch-up (`ObjectManager.postCameraPlacementUpdate` → `inlineCreateObject`) materializes spawns that enter the horizontal load window on the frame the camera crosses a chunk boundary, matching ROM `ObjectsManager` running after the camera update. That creation path was calling `isSpawnVerticallyEligibleForLoad(spawn, false)` — i.e. *with* the S2 Camera_Y filter — while the engine's primary pre-camera S2 load (`syncActiveSpawnsLoad(true)`) correctly *bypasses* it. ROM S2 `ObjectsManager_GoingForward`/`GoingBackward` call `ChkLoadObj` immediately after the X-window scan with **no** `Camera_Y_pos` filter (docs/s2disasm/s2.asm:33095-33136). The mismatch meant a spawn horizontally in-window but not-yet-vertically-near on the crossing frame was added to the active set but had its instance deferred to the next frame's pre-camera load — costing the object one update relative to ROM. The CNZ Big Block (Obj D4, s2.asm:58759-58799) at CNZ1 target 0x0F00 therefore ran exactly one update behind (engine `upd=500` vs ROM 501 at f3831 → block @0F47 vs ROM @0F46 → its `SolidObject_cont` side-push pushed the player 1px right). Fix: `inlineCreateObject` now passes `true`, so both S2 load paths share one vertical-eligibility policy and the spawn materializes on the same frame in both. The bypass only activates for `skipVerticalSpawnLoadFilterForGame` (SONIC_2 slot layout); S1 (counter-based, not on this path) and S3K (two-axis, `postCameraPlacementUpdate` returns early) are unaffected. No zone/route/frame/object-id carve-out, no position nudge, comparison-only. CNZ1 f3831→f3906 (errors 289→199; ObjD4 blocks now match ROM exactly, new divergence is an unrelated Tails-on-LauncherSpring carry issue); HTZ2 f1078→f2306 (same object-load-cadence class). Full single-fork `*TraceReplay` sweep: no green regressed (EHZ1/SCZ/WFZ + all S1), no frontier moved backward (S2 arz1 2043/arz2 549/cnz2 1775/cpz1 2822/cpz2 2518/dez1 1023/htz1 5647/mcz1 2181/mcz2 4009/mtz1 863/mtz2 641/mtz3 3719/ooz1 756/ooz2 389; S3K aiz 8941/cnz 17276/mgz 4124); `TestS2ObjectOccupancyOracle` 4/4 green; S3K must-keep units green; `TestCNZBigBlockObjectInstance` green. See docs/TRACE_FRONTIER_LOG.md.

- **Sidekick CPU off-screen despawn — unified ROM `interact(a0)` slot-dereference model (advances MTZ1 f375→f863 AND MTZ3 f2638→f3719 together):** Reconciles two independently-correct fixes that edited the same `SidekickCpuController` despawn path. ROM `TailsCPU_CheckDespawn` (`cmp.b id(a3),d0`, docs/s2disasm/s2.asm:39403-39429) dereferences the persistent `interact(a0)` byte slot **unconditionally** and compares the live id there against the latched `Tails_interact_ID` snapshot. The engine had collapsed two physically-distinct ROM states into a single `-1` ("empty"), so neither fix could express both without the other regressing. The reconciled model resolves the slot's ROM-effective live id in `romEffectiveInteractSlotId(...)` across all three cases: (1) **never rode anything** (`interactSlotIndex < 0`) → ROM `interact(a0)` defaults to slot 0 = `MainCharacter` = `ObjID_Sonic` 0x01 (it is written only by `RideObject_SetRide`, s2.asm:35980-36006, never cleared; s2.constants.asm:603,1101); (2) **slot occupied by a live object** → that object's id (`ObjectManager.objectIdInSlot`); (3) **rode something, slot since deleted/recycled away** → ROM reads id 0 because `DeleteObject` zeroes the whole object RAM (s2.asm:30324-30339). The raw slot read is kept separate (`rawInteractSlotObjectId()`) so `refreshInteractIdSnapshot` still preserves the last *real* occupant id across a momentarily-empty slot (the `TailsCPU_UpdateObjInteract` snapshot, s2.asm:39435-39446), and the `lastInteractObjectId >= 0` guard semantics are intact — keeping green EHZ1 (whose CPU Tails always has a set interact slot at its off-screen frames, so case-1 substitution never fires; A/B confirmed EHZ1 green with and without the change). At MTZ1 f375 CPU Tails lands off-screen on a SteamSpring (0x42) having never ridden anything → snapshot 0x01 ≠ live 0x42 → immediate `TailsCPU_Despawn` (f375→f863, next divergence a genuine Sonic `air` issue). At MTZ3 f2638 the MTZ Long Platform (Obj65) Tails rode deletes itself off-screen (`cmpi.w #$280` window, s2.asm:52886-52899) → freed slot reads 0 ≠ snapshot 0x65 → despawn (f2638→f3719, the historical peak; new owner is the distinct downstream Tails dead-fall `tails_y` divergence). Gated to S2's `PhysicsFeatureSet.sidekickDespawnUsesObjectIdMismatch`; S3K keeps its freed-slot riding-instance path (`sidekickDespawnUsesRidingInstanceLoss`) and never reaches this method. No gameId/zone/route/frame carve-out; comparison-only. `ObjectManager.objectIdInSlot` Javadoc updated to match; fixes the two pre-existing parity tests (`TestSidekickCpuDespawnParity.s2DestroyedRideSlotDespawnsThroughFreedObjectIdMismatch`, `offscreenObjectSwitchDespawnsUsingLatchedInteractObjectId`). Full single-fork `*TraceReplay` sweep: no green regressed (EHZ1/SCZ/WFZ + all S1 ×10), no frontier moved backward (S2 arz1 2043/arz2 549/cnz1 3831/cnz2 1775/cpz1 2822/cpz2 2518/dez1 1023/htz1 5647/htz2 1078/mcz1 2181/mcz2 4009/mtz2 641/ooz1 756/ooz2 389; S3K aiz 8941/cnz 17276/mgz 4124 byte-identical); `TestS2ObjectOccupancyOracle` green; S3K must-keep + sidekick units green. See docs/TRACE_FRONTIER_LOG.md.

- **MTZ3 giant-cog ride-release parity (jump-off carry/push timing):** The MTZ giant cog (`CogObjectInstance`, Obj70) now opts into the existing `SolidObjectProvider.airborneStaleStandingBitReturnsNoContact` contract. Obj70 collides via the standard `SolidObject` helper (`JmpTo16_SolidObject`, docs/s2disasm/s2.asm:55132), whose standing branch (s2.asm:35021-35044) runs first: when the ridden tooth's standing bit `d6` is still set on the player **and** `Status_InAir` is set, it takes `loc_1975A` (s2.asm:35035-35040) — clears `Status_OnObj`/`d6`, sets `Status_InAir`, returns `d4=0` **without** reaching `SolidObject_cont`, so the platform carry (`MvSonicOnPtfm`, s2.asm:35635-35659) and the side push (`SolidObject_AtEdge`, s2.asm:35432-35444) are both skipped on the frame the rider jumps off. The engine had cleared the rider's riding state before the inline solid pass, so the just-jumped airborne Tails was reclassified as a fresh side contact against the rotated tooth and shoved `+0xD` one frame early (MTZ3 trace f2047 `tails_x`: engine `0x07CA` vs ROM `0x07BD`; ROM applies the displacement only at f2048). Fix is a one-object opt-in to a shared ROM-faithful flag — no shared collision-code change — so zones without a cog are unaffected. MTZ3 frontier f2047→f2638 (errors 2518→1465). Full single-fork `*TraceReplay` sweep: no green regressed (EHZ1/SCZ/WFZ + all S1), no frontier moved backward (S2 arz1 2043/arz2 549/cnz1 3831/cnz2 1775/cpz1 2822/cpz2 2518/dez1 1023/htz1 5647/htz2 1078/mcz1 2181/mcz2 4009/mtz1 375/mtz2 641/ooz1 756/ooz2 389; S3K aiz 8941/cnz 17276/mgz 4124); `TestS2ObjectOccupancyOracle` 45/22 unchanged from baseline; S3K must-keep units green. See docs/TRACE_FRONTIER_LOG.md.

- **Object-lifetime parity (transient explosion self-delete timing):** Re-modelled the shared S1/S2/S3K badnik-death explosion (`ExplosionObjectInstance`, Obj27) on the ROM `anim_frame_duration` countdown (`subq #1 / bpl display / reload 7 / advance mapping_frame / delete at frame 5`) instead of a uniform 8-frame-per-sprite-frame delay. The old approximation held frame 0 for 8 game frames in every game, so in S2/S3K the explosion lingered 4 frames past the ROM `DeleteObject` (EHZ1 occupancy oracle: slot held 0x27 to ~f192 where ROM frees it at f188). The frame-0 hold is the one genuine per-game difference: S1 `ExItem_Main` loads `move.b #7,obTimeFrame` (frame 0 = 8 game frames, delete +39), while S2 `Obj27_Init` and S3K `loc_1E626` load `move.b #3` (frame 0 = 3 frames, delete +35). Modelled as object animation data via new `GameModule.explosionInitialAnimDuration()` (default 3; `Sonic1GameModule` overrides 7) — resolved from the active module at the explosion's first update, never a gameId branch. Cites: docs/s2disasm/s2.asm:46672-46684; docs/skdisasm/sonic3k.asm:42195-42205; docs/s1disasm/_incObj/24, 27 & 3F Explosions.asm. Verified by new unit tests (`TestExplosionObjectInstance` S1/S2 frame-exact deletion) and by enabling the Task 1.7 `TestS2ObjectOccupancyOracle` assertion for green S2 traces EHZ1/SCZ/WFZ (Obj27 self-delete-timing scope). No green trace regressed and no frontier moved in the full single-fork `*TraceReplay` sweep. (Points Obj29 timing was already ROM-correct; the Animal Obj28 and a one-frame spawn-windowing offset on Obj27/Obj29 are left to the ridden/windowing object-lifetime work — see docs/TRACE_FRONTIER_LOG.md.)

- **Foundation change (knowingly regresses two S2 frontiers pending cascade cleanup):** Re-modelled the sidekick CPU despawn check on ROM's slot-based `interact(a0)` (`TailsCPU_CheckDespawn`/`TailsCPU_UpdateObjInteract`, docs/s2disasm/s2.asm:39403-39446). The sprite now carries the persistent ROM SST `interact(a0)` *slot index* (`AbstractPlayableSprite.interactSlotIndex`, written by the `RideObject_SetRide`-equivalent in `ObjectManager` (s2.asm:35980-36006), never cleared on dismount/despawn/death), and the controller's `Tails_interact_ID` snapshot (`SidekickCpuController.lastInteractObjectId`) is refreshed each non-despawning frame from the *live* object currently in that slot (`ObjectManager.objectIdInSlot`). The off-screen despawn fires only on a real slot recycle (slot now holds a different-id object), removing the non-ROM `!= 0` guards that masked the model. This is the ROM-universal interact-slot model (S2 and S3K both have it); the S2 slot-id-mismatch despawn consumption stays gated by `PhysicsFeatureSet.sidekickDespawnUsesObjectIdMismatch` (S2 true, S3K false), and S3K keeps its `sub_13EFC` deleted-slot path via `sidekickDespawnUsesRidingInstanceLoss` (S3K true). Cascade: HTZ2 advances f795→f1078 (spurious off-screen despawn fixed); EHZ1/SCZ/WFZ stay green; all S1 (×10) and S3K (AIZ f8941 / CNZ f17276 / MGZ f4124) byte-identical. **Knowingly regresses** MTZ3 f3719→f2638 and leaves MTZ1 at f375: both depend on the engine recycling the off-screen interact slot the way ROM does (a separate object-load/unload-window parity gap) and on the EHZ-class follow/landing-target correction; the prior `isLatchedRideSlotFreed`/id-latch heuristic (introduced/refined across `15c98e1be`/`841370074`/`3942e9032`, MTZ3 advance `518e120dc`) happened to despawn MTZ3 f2638 correctly but mis-fired on MTZ1/HTZ2 — it was compensating for the broken model and must be re-derived on this correct foundation. See docs/TRACE_FRONTIER_LOG.md for the full before→after cascade map.

- Advanced six more S2 trace-replay frontiers (trace-green-fleet pass 4; verified together single-fork, no green/S1/S3K regressions): ARZ2 swinging-platform SolidObject half-width +0xB (f482→549); CNZ2 Crawl badnik (f1490→1775); CPZ1 pipe-exit spring (f2038→2822); CPZ2 CPZ platform + per-object `getBalanceWidthPixels()` for the on-object balance routine (default = on-screen half-width, so all other objects/games unchanged; f2251→2518); MTZ3 MTZ-cog multi-piece earlier-sibling side-push ordering via opt-in `resolvesEarlierPiecesBeforeRidingPiece()` (f2111→3719); MCZ2 moving vine (f3991→4009).

- Fixed a latent object-construction bug: `ObjectConstructionContext.construct()` cleared the construction context unconditionally in its `finally`, so any object/boss spawning **multiple children from its own constructor** lost the context after the first child — children 2..N got a null injected `ObjectServices` and threw on the first `services()` call. It now save-and-restores the prior context (nested-construction safe). Surfaced by the S2 Death Egg Robot, whose `ForearmChild.updatePunch()` crashed; covered by new test `TestDEZDeathEggRobot`.
- Advanced seven more S2 trace-replay frontiers (trace-green-fleet pass 3 + DEZ unblock; verified together by a single-fork sweep, no green/S1/S3K regressions): ARZ1 Grounder badnik (f1208→2043); ARZ2 swinging platform (f264→482); CPZ1 Blue Balls object (f1905→2038); CPZ2 Grabber (f1609→2251); MCZ1 `PhysicsFeatureSet.fullSolidBottomOverlapUsesCurrentYRadiusOnly` set true for S2 (symmetric underside box matching `SolidObject_cont`/S1; S3K stays false; removes a phantom Stomper ceiling hit; f2005→2049); MCZ2 Flasher badnik (f3729→3991); DEZ Mecha Sonic advance + the ForearmChild construction-context fix above (f536→1023, no longer crashes).

- Advanced eight more S2 trace-replay frontiers (trace-green-fleet pass 2; no green/S1/S3K regressions, all verified together with a single-fork sweep), each disassembly-cited with no zone/route/frame carve-out: ARZ2 `SolidObjectProvider.allowsZeroDistanceTopSolidLanding()` models `SolidObject_TopBottom` `blo` zero-distance landing (universal SolidObject behavior; f241→264); MTZ2 MTZ platform carry (f453→641); CPZ1 Spiny badnik + projectile (f844→1905); MTZ3 `Camera.isVisibleForRenderFlag` ROM-accurate vertical-wrap window (bias-before-mask; per-game mask/margin gated via `PhysicsFeatureSet.useScreenYWrapValueForVisibility()`, cited for S2 BuildSprites + S3K Render_Sprites; f1379→2111); MCZ1 sliding spikes (f1455→2005); CPZ2 Grabber (f1607→1609); MCZ2 vine switch (f3003→3729); OOZ1 fan (f741→756).

- Added `AudioManager.outputSampleRate()` so `TraceCaptureTool` reads the synthesis rate through `AudioManager` rather than `GameServices.audio().getBackend()` (satisfies `TestAudioBackendBypassGuard`, which the prior singleton-closure fix had tripped).
- Routed the trace-capture code through `EngineServices`/`GameServices` instead of raw process singletons so `TestProductionSingletonClosureGuard` passes: `LevelRenderer` resolves `TraceRenderVisibility` from its injected `configService`; `TraceCaptureSession`/`TraceCaptureTool` use `GameServices.audio()`/`GameServices.configuration()`; `HeadlessGameBoot` reads managers off the `EngineContext`; and `TraceCaptureTool` configures `EngineServices` once at `main()` as the CLI composition root. The two headless composition roots are allowlisted for the legacy-bootstrap bridge like `Engine`.
- Extracted the SMPS audio synthesis out of `LWJGLAudioBackend` into a device-agnostic `AbstractSmpsAudioBackend` base (synthesis, sequencer, music stack, SFX lifecycle, snapshot/rewind, deterministic-runtime binding) behind a small set of device-output hooks. `LWJGLAudioBackend` now implements those hooks with its existing OpenAL code (live audio unchanged), and a new `HeadlessSmpsAudioBackend` implements them as no-ops — a **true no-device** backend that synthesizes for headless trace capture without opening any audio device (works on machines with no audio hardware). This replaces and reverts the temporary `offlineNoDevice` flag on `LWJGLAudioBackend`. Headless trace capture now uses `HeadlessSmpsAudioBackend`.

- Advanced seven S2 trace-replay frontiers (no green regressions; S1/S3K byte-identical), each a disassembly-cited ROM fix with no zone/route/frame carve-out: ARZ1 FindFloor blank extension-tile default in shared `GroundSensor` (universal floor physics; f1106→1208); ARZ2 Obj82 swinging platform `y_radius` for `fixBugs=0` + SolidObject landing (f225→241); CPZ2 Obj A8 Grabber legs touch box + one-frame grab deferral (f1515→1607); HTZ1 Obj41 horizontal-spring contact-independent proximity launch (f5511→5647); MCZ1 Obj77 bridge uses SolidObject landing, not PlatformObject snap (f1085→1455); MCZ2 vine switch reads the raw `Ctrl_2` press, not the CPU follow jump, so Tails is not released early (f264→3003); OOZ1 runs OilSlides pre-physics via a new default-no-op `LevelEventProvider.updatePrePhysics()` hook so oil friction lands a frame earlier as in ROM (f563→741).

- `TraceCaptureTool` gained a `--no-ghosts` / `--ghosts` flag (per-run override of `TRACE_SHOW_DESYNC_GHOSTS`) and a `trace-capture` skill documenting the tool's invocation, flags, audio behavior, and pipeline wiring.
- Fixed trace-capture audio (was silent, then — once a backend was added — playing to the speakers and too fast). `HeadlessGameBoot` now installs a real `LWJGLAudioBackend` so the deterministic capture runtime binds an actual SMPS stream (the default `NullAudioBackend` synthesized nothing → digital silence). The backend gains an `offlineNoDevice` mode for headless capture that suppresses all OpenAL output: `update()`/`startStream()` no-op and the listener is muted. This also makes the capture runtime the *sole* consumer of the presentation FIFO — previously the backend's streaming drained the same FIFO, stealing samples from the capture and speeding the audio up. Capture also drives at the backend's real 48 kHz synthesis rate instead of a hardcoded 44.1 kHz, so the clock, FLAC tag, and synthesis all agree.
- Trace desync ghosts now render in headless trace capture, not just live Trace Test Mode: the ghost render hook was generalized into `TraceGhostHook`, which both the live launcher and the capture session register into. `LevelRenderer` queries it (instead of the launcher singleton) to draw ghosts and to decide whether the `TRACE_SHOW_*` HUD flags apply, so capture honors the visibility config too.
- Trace capture records *through* comparator desyncs (injectable pause callback; the headless `TraceCaptureTool` passes a no-op) so a capture runs to trace completion instead of freezing on the first mismatch, with a defensive frame cap so a stuck cursor can't grow the ffmpeg temp unbounded.
- Added `TRACE_SHOW_DESYNC_GHOSTS` / `TRACE_SHOW_GAME_HUD` / `TRACE_SHOW_DEBUG_HUD` config flags for trace replay and capture visibility (foundation for the trace capture recorder). The game-HUD and debug-HUD visibility gates only apply while a trace session is active, so normal gameplay rendering (incl. the debug overlay) is unaffected.

- Added `CAPTURE_OUTPUT_DIR` / `CAPTURE_SCALE` / `CAPTURE_FPS` / `CAPTURE_CODEC` config for trace video capture.

- Added `FfmpegEncoder`, a two-phase `CaptureEncoder` that streams raw RGBA frames to ffmpeg (FFV1 video with ffmpeg-side vflip + integer nearest-neighbor upscale), buffers per-frame s16le PCM, then muxes a lossless FFV1+FLAC MKV. Includes PATH-based ffmpeg discovery (`findFfmpeg`/`findFfmpegOnPath`).

- Added `DrainPcmAudioTap`, an `AudioFrameTap` that drains the current gameplay frame's stereo PCM from `AudioManager` capture mode (`drainCaptureFrame`) for the trace video capture pipeline.

- Added `GlReadPixelsGrabber`, a `VideoFrameGrabber` that reads the back buffer via `glReadPixels` (raw RGBA, OpenGL bottom-up order; ffmpeg-side `vflip` corrects orientation) for the trace video capture pipeline.

- Extracted `TraceReplayDriver` (UI-agnostic deterministic trace-replay bootstrap) out of `TraceSessionLauncher`; the live Trace Test Mode launcher now delegates the reusable replay drive (reset/team/zone-load/title-card/playback/bootstrap/comparator) to the driver, which the headless trace-capture tool reuses. Behavior-preserving extraction (no replay behavior change).

- Gated the desync-ghost, game-HUD, and debug-HUD render sites in `LevelRenderer` behind `TraceRenderVisibility` (`showGhosts`/`showGameHud`/`showDebugHud`), resolved once per frame from config. Honored by both live Trace Test Mode and the headless trace-capture recorder; the ghost site still also requires an active trace session so normal gameplay is unaffected.

- Added `HeadlessGameBoot`, an `AutoCloseable` helper that boots a fully wired gameplay session against a hidden offscreen GL context (no `Engine`, no master-title flow): mirrors the `VisualReferenceGenerator` GL setup and the `Engine.initializeGameplayRuntime` session wiring (open session, attach managers, build/wire a `GameLoop`, load zone/act, register the active team, focus the camera) for the headless trace-capture tool to step frame-by-frame.

- Added `TraceCaptureSession`, which ties a booted `GameLoop` + `TraceReplayDriver` + `VideoFrameGrabber` + `AudioFrameTap` + `CaptureRecorder` into a per-frame step→render→grab→submit loop for the headless trace-capture tool. `stepAndCapture()` advances one game tick, renders the LEVEL scene the same way `Engine.draw()` does (clear with the level background colour, `drawWithSpritePriority`, flush, `glFinish`), grabs the back buffer, drains that frame's stereo PCM, and submits a `CapturedFrame`; it returns `false` once the trace is complete. `start()`/`finish()` install and tear down `AudioManager` capture mode around the recorder lifecycle.

- Added `TraceCaptureTool`, a CLI (`com.openggf.tools.TraceCaptureTool`) that records a recorded trace's deterministic replay to a lossless MKV. It resolves `--trace <id|name|dir>` against `TraceCatalog`, boots a headless gameplay session via `HeadlessGameBoot`, drives `TraceReplayDriver` + `TraceCaptureSession` with a `CaptureRecorder` wrapping `FfmpegEncoder`, and runs the full step→render→grab→submit loop to completion. `--scale` / `--fps` / `--codec` / `--out-dir` default from the `CAPTURE_*` config; the UTC output timestamp is formatted in `main` so the recorder stays deterministic. Run with `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" "-Dexec.args=--trace <id> --out-dir target/trace-videos"`.

- Configuration moved from a flat `config.json` to a grouped, commented, deterministically-ordered `config.yaml`. All developer/debug settings are compartmentalised into a single `debug:` block. Existing `config.json` files are migrated automatically on first run (backed up to `config.json.bak`). Window size/scale are deprecated under `debug.window`; widescreen is driven by `display.aspect` profiles.

- **Fixed Sonic getting stuck rolling after exiting a CPZ spin tube (Obj1E).** `CPZSpinTubeObjectInstance.exitTube` called `player.setPinballMode(true)` on exit, which is not in the ROM. Combined with S2's `pinballLandingPreservesRoll` / `pinballLandingPreservesPinballMode` landing flags, `PlayableSpriteMovement.resetOnFloor` then skipped both the roll-clear and the pinball-clear permanently, so landing on terrain (instead of bouncing off the exit spring) left Sonic locked in a ball. The ROM exit routine (`loc_227A6`) only masks `y_pos`, sets the object state, clears `obj_control`, and plays the spindash-release sound — it never sets `spindash_flag` / `pinball_mode`. Removed the stray `setPinballMode(true)` so the airborne rolling bit uncurls naturally on landing, matching the ROM. Corrected the now-inaccurate `TestCPZObjectBugs` docstrings.

- **Fixed the S2 Egg Prison capsule releasing no visible animals.** The capsule opened (explosion, lock fly-off) and the released animals were still spawned, but every one rendered invisibly. `EggPrisonAnimalInstance` captures its sprite renderer in its constructor via `getRenderManager()`, and the egg prison spawned them with raw `new ... + ObjectManager.addDynamicObject(...)`, which sets the object's services only *after* construction. Since the DI-migration cleanup made `getRenderManager()` resolve through per-instance `tryServices()` (null during context-less construction), the captured renderer was `null`. `EggPrisonObjectInstance.spawnInitialAnimals`/`spawnRandomAnimal` now spawn through `spawnFreeChild(...)` (identical FindFreeObj/`addDynamicObject` slot semantics, but it sets the construction context so the animal can resolve its renderer), matching the CLAUDE.md child-spawn convention. Added `TestEggPrisonAnimalRelease`.

- **Swept the codebase for the same "spawns but invisible" renderer-capture bug and added a guard.** The HCZ end-boss egg capsule had the identical defect — `HczEndBossEggCapsuleInstance.spawnAnimals()` spawned `EggPrisonAnimalInstance` via raw `addDynamicObject`, so its released animals were invisible; it now uses `spawnFreeChild(...)`. Added `TestNoRendererCaptureInUnsafeSpawn`, a source-scanning guard that fails if any object caching a renderer in its constructor (via the inherited `getRenderManager()`/`getRenderer()` helpers) is spawned through a context-less `addDynamicObject*` API (catches both inline and two-line spawn forms; the parameter-injected `AnimalObjectInstance` path used by Rexon/Turtloid is correctly treated as safe). `AbstractObjectInstance.getRenderManager()` now logs a one-time warning per class when it returns null for lack of services/construction context, so the otherwise-silent failure is visible at runtime.

- **Fixed corrupted graphics on the ARZ floating platform (Obj18).** `ARZPlatformObjectInstance` chose its sprite mapping table by comparing `Level.getZoneIndex()` (which returns the ROM zone id) against a level-select index constant (`ZONE_AQUATIC_RUIN = 2`) instead of Aquatic Ruin Zone's ROM zone id `0x0F`. In ARZ the check never matched, so the platform fell back to the EHZ `Obj18A` mappings (and the wrong solid `y_radius`) rather than the ARZ-only `Obj18B` table (`Obj18_MapUnc_1084E`; s2.asm `Obj18_Init`, `cmpi.b #aquatic_ruin_zone,(Current_Zone).w`), producing garbled tiles. It now compares against `Sonic2Constants.ZONE_ARZ`, and the misleading `ZONE_AQUATIC_RUIN` constant was removed. Added `TestArzPlatformZoneDetection`.

- **Fixed intermittent S2/S3K monitor break misses.** Monitor touch callbacks are now continuous while Sonic remains overlapping the box, matching ROM `TouchResponse` polling. This prevents a first overlap with `status.player.rolling` set but a still-stale non-roll animation byte from suppressing all later break attempts until Sonic leaves the hitbox. Added S2 and S3K monitor touch-profile regressions.

- **Fixed the S2 1-up monitor always showing Tails' face.** The Sonic 1-up monitor icon piece (`obj26` frame 2) maps to tile `$154`, which in the ROM is `ArtTile_ArtNem_life_counter` (`= ArtTile_ArtNem_Powerups + $154`) — VRAM shared with the HUD life counter — so the monitor displays the main character's face (`PlrList_Std1` loads Sonic by default; `PlrList_TailsLife` and the Knuckles lock-on patch override it; s2.asm:89193/89271). The engine hard-loaded Tails life art at that tile, so every standard monitor showed Tails even when Sonic was the lead. `Sonic2ObjectArt` now loads Sonic's life art there by default and `Sonic2ObjectArtProvider` overrides the tile with the lead character's life icon (Tails native, Knuckles via the palette-remapped S3K donor art); `MonitorObjectInstance.resolveIconFrame` is now subtype-based (matching ROM `Obj2E_Init` and the S3K monitor) instead of branching on who broke the box. Added `TestSonic2MonitorIconArt`.

- **Fixed the EHZ Act 2 boss drillcone rendering behind the car.** The front spike/drillcone (`EHZBossSpike`) shared render bucket 4 with the ground vehicle body, and—being spawned last—lost the within-bucket slot tiebreak, so it drew behind the car. It now uses bucket 3 (the front wheels' bucket), matching the ROM `Obj56_Init` priorities where the spike's `priority = 2` sits in front of the ground vehicle's `priority = 3` (s2.asm:63194). Added `TestEHZBossRenderPriority`.

- **Fixed the EHZ Act 2 boss drillcone vanishing when the boss leaves and re-enters the screen.** The boss is event-spawned (not respawn-tracked) and oscillates past the locked-arena edge; the leading drillcone crossed the 128px-rounded out-of-range cull one chunk ahead of the body, so it was unloaded as an ordinary dynamic object and never rebuilt. Boss parts now persist with the boss (`Sonic2EHZBossInstance.isPersistent()`, inherited by children via `AbstractBossChild`), matching the ROM, which never culls fixed-arena boss parts off-screen during the fight. To keep post-defeat despawn ROM-accurate, the fly-off routine now deletes the main body when it reaches max-X and goes off-screen (`loc_2F46E`), cascading to the flying parts (the spike self-deletes once its parent is gone), while the wrecked ground vehicle persists as debris (`loc_2F5F6` never deletes it; `EHZBossGroundVehicle` opts out of the parent-destroy cascade). Added `TestEHZBossPersistence`.

- **Fixed the S2 CPU sidekick being able to break monitors.** `MonitorObjectInstance` now gates the break path on the lead-character check from ROM `Touch_Monitor.breakMonitor` (`cmpa.w #MainCharacter,a0`; s2.asm:85245-85249), so a CPU sidekick can no longer destroy a monitor in single-player mode. The sidekick can still knock a monitor down from below — matching the ROM, where the gate sits only in the break path, not the hit-from-below fall path. S1 and S3K monitors already had this gate; only S2 was missing it.

- **Implemented S3K LBZ tube elevator Obj10.** S3KL slot `$10` now routes to
  `Obj_LBZTubeElevator`, using ROM-backed LBZ tube transport mappings/art,
  automatic-tunnel path data, two-layer elevator rendering, capture/spin/path
  travel/release states, closed-destination suppression while Sonic is inside
  an active tube elevator, and release cleanup for object-owned rotating player
  frames.

- **Implemented S3K LBZ ride grapple Obj17.** S3KL slot `$17` now routes to
  `Obj_LBZRideGrapple`, including ROM path-range subtype selection, chain and
  handle extension/sway, native P2-before-P1 grab handling, `object_control=3`
  carry state, jump release launch state, path-end ejection, and ROM-backed LBZ
  misc mappings/art registration. The handle swing now reads the high byte of
  the ROM angle word, avoiding low-byte cardinal jumps while Sonic is carried,
  and jump release suppresses the stale held-button edge so insta-shield waits
  for a real release/re-press.

- **Implemented S3K LBZ cup elevator Obj18/Obj19.** S3KL slots `$18` and `$19`
  now route to `Obj_LBZCupElevator` and `Obj_LBZCupElevatorPole`, using the
  ROM-backed LBZ cup elevator mapping table from the locked-on data include,
  LBZ misc art, subtype-driven travel distance/start phase, cup attach/base
  children, player carry/release handling with the ROM twist-frame pose table,
  pole variants, and rewind-safe structural child state.

- **Implemented S3K LBZ moving platform Obj11.** S3KL slot `$11` now routes to
  `Obj_LBZMovingPlatform`, using ROM-backed LBZ misc level art and the S&K-side
  moving-platform mapping table. The implementation covers stationary bob,
  horizontal/vertical/diagonal oscillation, diagonal lift, square paths,
  horizontal sweep, and delayed/active falling subtypes while preserving the
  SKL/MHZ `$11` mushroom-platform remap.

- **Recovered clobbered widescreen title-card and CI metadata changes.** Sonic 2 title cards once again center their native 320-wide composition in the active projection, extend blackout/blue/yellow/red planes across the full viewport, and apply edge margins so slide-out elements clear widescreen side bands. Added a guard test for that manager wiring. Removed invalid disassembly gitlinks that had no `.gitmodules` entries, which broke GitHub Actions checkout/policy cleanup while leaving local reference checkouts ignored.

- **Recovered clobbered MHZ parity and CI guard changes.** Restored the missing object-control, native-player participation, touch-response-profile, rewind-policy, hidden-monitor, twisted-ramp, LBZ alarm, still-sprite footprint, and cross-platform disassembly-label fixes that had been present locally but were not included in the pushed branch, so CI now exercises the same recovered tree as local verification.

- **Expanded S3K Mushroom Hill parity and art-safety coverage.** MHZ now has fixes across Knuckles cutscene art/palette/music cleanup, swing/curled/twisted vines, mushroom platforms/catapults, pulley lifts, Madmole and Dragonfly behavior, miniboss arena/flame/music handling, debug object-name resolution, and post-cutscene palette restoration. Added engine-level sprite mapping corruption suppression/logging, ROM-backed S3K object-art crawler coverage, SKL/S3-side art-address guards with reviewed exceptions, and RomOffsetFinder support for labels inside included mapping files.

- **Implemented S3K Ribot ObjBF.** The Launch Base Ribot badnik now registers
  in the S3KL object set, uses the existing ROM-backed Ribot art plan, spawns
  subtype-specific appendage children, alternates the parent child-gate bits
  from the ROM `$38` state pattern, and exposes the child hurt collision.

- **Implemented S3K Orbinaut ObjC0.** The Launch Base badnik now registers in
  the S3KL object set, loads its existing ROM-backed Orbinaut art, spawns four
  orbiting hurt orbs at the ROM cardinal offsets, and moves left or right only
  while P1 is grounded and moving, matching `Obj_Orbinaut` / `sub_8C6D4`.
  Focused Orbinaut and neighboring S3K badnik coverage passes.

- **Implemented S3K LBZ rolling drum Obj31.** S3KL slot `$31` now routes to
  `Obj_LBZRollingDrum`, the invisible cylinder controller that uses subtype as
  half-width, keeps native P1/P2 ride-angle state, applies the ROM sine-based
  rider `y_pos` path, preserves the previous ride angle on middle-window
  recapture, and handles the `RideObject_SetRide` landing, animation restart,
  flip/ground-speed, and release side effects. The captured rider now uses the
  ROM `Anim_Tumble` render flips for `flip_type=$80/$81` and ignores impossible
  stale-air / lost player-latch release state while still inside the live drum
  controller's horizontal window.

- **Fixed widescreen players walking past the level's right edge to their death.** The right level-boundary clamp was computed with `camera.getWidth()`, so at a widescreen viewport it moved right with the screen (ULTRA_21_9 → level edge + 184px). Since `camera.getMaxX()` is the native ROM scroll limit (`level_edge − 320`) and the level geometry only exists up to `maxX + 320`, this let the player walk past the right wall into the void beyond a camera lock and fall to their death. The boundary now uses the fixed native `LEVEL_DESIGN_WIDTH = 320` — it tracks the level's wall, not the render viewport — reproducing the ROM `+$128` / `+$128 + $40` values at every aspect ratio. The despawn/visibility windows still widen with the viewport (objects in view are not culled); only the level-wall clamp stays native. See KNOWN_DISCREPANCIES.md "Right-Boundary Is Viewport-Independent (Level Edge)".

- **Added the S3K MHZ updraft airflow path.** SKL slot `$14` now routes to `Obj_Updraft`, including the ROM `$40/$80` horizontal window, oscillating vertical lift curve, airborne/jump-state cleanup, shield action cancellation, `ground_vel=1`, positive-subtype flip setup, negative-subtype animation `$0F`, and quiet wind SFX cadence.

- **Added the S3K MHZ swing vine grab/release path.** SKL slot `$10` now routes to `Obj_MHZSwingVine`, including the ROM handle position at `y_pos+$10`, hanging grab window, `object_control=3` capture, `byte_22A4C` hanging frame selection, swing-mode handoff, and jump release using the S&K `GetSineCosine` velocity scale.

- **Added the S3K MHZ sticky vine capture/retraction path.** SKL slot `$0A` now routes to `Obj_MHZStickyVine`, including the ROM overlap capture window, level-art display footprint, active child-chain deformation state, route-impact player pull/friction effect, spindash-release arming delay, and the `loc_3EB50`-style retraction state seeded with `y_vel=-$600`.

- **Added the S3K MHZ vertical swing bar side-grab path.** SKL slot `$0C` now routes to `Obj_MHZSwingBarVertical`, including the ROM side-dependent speed gates, side grab windows, grounded/object-control rejection, `object_control=3` hanging capture, `x_pos +/- $12` snap, zeroed player velocities, `$62` initial frame, raw climb animation offsets, normal jump release with `y_vel=-$500`, and the `$1000` horizontal auto-release with `move_lock=15`.

- **Added the S3K MHZ horizontal swing bar grab/release path.** SKL slot `$0B` now routes to `Obj_MHZSwingBarHorizontal`, including the ROM `x_pos +/- $16` by `y_pos+$15..+$24` grab window, `object_control=3` hanging capture, `y_pos+$14` snap, zeroed player velocities, mapping frame `$94/$95` selection from incoming vertical speed, and normal jump release with `y_vel=-$500`, rolling radii, and cleared object control.

- **Added the S3K MHZ curled vine object routing and top-solid range state.** SKL slot `$09` now remaps to `Obj_MHZCurledVine` in Mushroom Hill instead of the S3KL AIZ1 tree object, including the ROM `$280` priority bucket, display-child `$40/$30` on-screen bounds, initial `$FFF40000` curve state, `$36/$37`-style ridden segment indexing, the `byte_3E8F6` standable range table, and one-`$10000`-per-frame curve-state approach toward the selected target.

- **Added the S3K MHZ twisted vine entry path.** SKL slot `$03` now remaps to `Obj_MHZTwistedVine` in Mushroom Hill instead of the S3KL AIZ hollow-tree object, including the ROM lower/upper entry windows, `RideObject_SetRide` on-object latch, facing writes, `move_lock=20`, flip-angle seeds, slow-entry ground-speed clamps to `+$600` / `-$600`, and slow/airborne release cleanup.

- **Added the S3K MHZ pulley lift grab path.** SKL slot `$06` now remaps to `Obj_MHZPulleyLift` in Mushroom Hill instead of the S3KL AIZ ride-vine object, including the ROM left/right handle spawn offsets, falling-player grab window, `object_control=3` carry pose, `y_pos+$42` snap, directional jump release with `y_vel=-$380`, and pulley handle retraction/pull offsets.

- **Fixed S3K MHZ1 cutscene logical-input clearing under S3K control-lock latching.** The Knuckles cutscene and Player 2 stopper now use the explicit logical-input clear path so `Ctrl_1_logical` / `Ctrl_2_logical` are zeroed even when the S3K physics feature set preserves normal logical-pad writes while control is locked.

- **Added the S3K MHZ mushroom parachute carry path.** SKL slot `$12` now routes to `Obj_MHZMushroomParachute`, including the ROM narrow falling-player grab window at `y_pos+$25`, `object_control=3` carry, `loc_3F51C` falling motion seeded with `y_vel=$300`, player velocity mirroring, and jump release with `y_vel=-$380`, directional `x_vel`, rolling radii, and regrab cooldown.

- **Extended the S3K MHZ miniboss return-wait callback.** Routine `$1E` now reuses the ROM `loc_754A0` path with the static `byte_75F0D` raw animation, applies `MoveSprite2` during the `$2E=$2A` wait, and callbacks through `loc_75330` into routine `$06` with the stored hover height, `x_vel=$400`, cleared `y_vel`, and `$2E=$1F`.

- **Extended the S3K MHZ miniboss return landing state.** Routine `$1C` now ports the `loc_755AE` return-bounce landing check, using ROM `MoveSprite` gravity order before snapping back to stored height `$3C` and seeding routine `$1E` with mapping frame 5, `x_vel=$400`, `$2E=$2A`, cleared `$38` bit 6, and reset raw-animation state.

- **Extended the S3K MHZ miniboss return-bounce path.** Routine `$1A` now runs the ROM `loc_754A0` `MoveSprite2` wait after the `$18` bounce-threshold handoff and calls `loc_755A0` when `$2E` expires, entering routine `$1C` with bit 6 of `$38` restored for the return bounce.

- **Added the S3K Mushroom Hill Zone parallax, animated-BG, event, and first badnik/boss foundation.**
  MHZ routes to a dedicated scroll handler porting the shared `MHZ_Deform` camera projection (ROM
  `5/32 + $76` BG vertical, `3/8` BG horizontal scroll for both acts' normal background), publishing
  the BG X workspace through `MhzZoneRuntimeState` so `Sonic3kPatternAnimator` runs the two custom
  `AnimateTiles_MHZ` split BG transfers via `AnimatedTileChannelGraph` before the regular AniPLC
  scripts. `Sonic3kMHZEvents` covers MHZ1 screen-event basics, MHZ2 routine `$04` camera boundary
  routing, the MHZ2 season palette state machine (green/autumn/gold blocks, five `sub_55008` trigger
  regions), and the first ship-transition handoff (`Pal_MHZ2Ship`, managed ship controller plus two
  propellers, `loc_5583E` ship motion/Scroll_lock signal, `sub_54F8C` H-scroll/propeller offsets,
  published ship H-int state). SKL badnik slots `$8C-$90` route to Madmole, Mushmeanie, Dragonfly,
  Butterdroid, and Cluckoid (Madmole activation/rise/drill/sink/cooldown, Butterdroid `Chase_Object`
  flight, Cluckoid player-range/wind/breath cadence); SKL slots `$A8/$A9` route to the cutscene
  Knuckles/button pair (ROM `_unkFAB8` clamps Sonic at `x_pos=$389`, locks/forces input, pans the
  camera to `$5B0`). SKL boss slots `$91/$92/$93` dispatch to MHZ miniboss-tree/miniboss/end-boss
  handlers with ROM hit-count and collision-size metadata (instead of the overlapping S3KL AIZ/Jawz
  meanings), and the MHZ art plan registers ROM-backed miniboss, miniboss-log, miniboss-tree,
  end-boss, and spike mappings from verified S&K offsets. The miniboss runs its ROM `loc_75220`
  camera-relative spawn through the swing/dash/chop state machine (`loc_752D4` wait, `loc_75330`
  dash, `loc_75356`/`loc_753A4`/`loc_753B6` deceleration, `loc_753DE` camera-approach swing, the
  `byte_75EBB` chopping script with `sfx_ChopTree`, routines `$E`-`$1A`); the `$1A`-`$1E`
  return/bounce continuation is covered by the dedicated miniboss-routine entries above.

- **Sonic 2 Metropolis Zone object/badnik/boss parity pass.** Closed disassembly-verified gaps across the MTZ slice: the Shellcracker claw (ObjA0) gained its enemy touch hitbox ($9A); the MTZ boss (Obj54) now spawns its laser projectile (Obj54_Laser subtype 4) and its shield orbs (Obj53) detach, bounce, and burst on hits with ROM-accurate break-away physics; Obj65/6A/6B/6E/70 platforms render from their real Kosinski level-art mappings on the correct palette line instead of a CPZ stair-block substitute; the cog uses its own mappings (0x26F04); Spin Tube (Obj67) and Nut (Obj69) drive both the main character and sidekick; Twin Stompers (Obj64) use the ROM coarse-X despawn window; Lava Bubble (Obj71) ping-pongs its animation via SWITCH; child spawns route through `spawnChild`; and the MTZ background scroll tracks through `BackgroundCamera`. MTZ3 boss-arena events match `LevEvents_MTZ3` (unconditional min-X follow in routine 5). Pre-existing MTZ trace frontiers (sidekick CPU / platform sub-pixel carry) are unchanged by this work.

- **Completed Sonic 2 Wing Fortress Zone parity.** WFZ now has ROM-backed
  object, badnik, boss, PLC/art, palette, scroll, rewind, intro Tornado
  cutscene, and WFZ-to-DEZ transition coverage, with the WFZ level-select
  trace replay matching end-to-end.

- **Added opt-in Discord Rich Presence.** When enabled, OpenGGF publishes menu and gameplay status through the local Discord desktop client, including game, character/team, zone/act, and timer details subject to privacy toggles. Presence remains disabled by default and includes distinct master-title, game title-screen, level-select, data-select, and gameplay states.

- **Fixed Discord Rich Presence elapsed-time resets.** Activity updates now reuse a stable Discord `timestamps.start` value so Discord's own "time playing" display no longer resets when OpenGGF refreshes gameplay/menu details.

- **Added safe-area projection scope to the UI render pipeline.** `GraphicsManager.beginSafeAreaProjection/endSafeAreaProjection` pushes a centered-320 ortho override using the existing `projectionMatrixBuffer` local-override path; `UiRenderPipeline.beginSafeArea/endSafeArea` expose the scope to callers. At native 320 px width pad=0, making this a no-op. Callers must call `endSafeArea()` before `renderFadePass()` so the fade pass runs at the full viewport.

- **TEST_MODE_ENABLED forces NATIVE_4_3 (320×224) resolution.** When `TEST_MODE_ENABLED=true`, `resolveDisplayAspect()` overrides any configured `DISPLAY_ASPECT` with `NATIVE_4_3`. Trace replay tests and the test-mode trace picker are parity-critical and only valid at 320×224; a developer's widescreen `DISPLAY_ASPECT` (e.g. `ULTRA_21_9`) must never leak into those runs. `resetToDefaults()` now also re-invokes `resolveDisplayAspect()` so any widescreen value loaded into the transient overlay during singleton construction is cleared when the test harness resets config.

- **Object despawn and visibility windows now track viewport width.** `AbstractObjectInstance.isInRange()` replaces the hardcoded `128 + 320 + 192 = 640` ROM constant with `128 + viewportWidth + 192`, and `isChkObjectVisible()` derives its screen rectangle from `cameraBounds` instead of literal `320` / `224`. At native viewport width (320 px, `DISPLAY_ASPECT = NATIVE_4_3`) both methods are byte-identical to the ROM; at widescreen widths they widen with the configured viewport so objects near the right edge are no longer wrongly despawned or hidden (declared divergence, see KNOWN_DISCREPANCIES.md "Object Despawn and Visibility Windows").

- **Player right-boundary now tracks viewport width.** Extracted boundary math into `RightBoundary.compute()` and replaced the hardcoded `SCREEN_WIDTH = 320` in `PlayableSpriteMovement.doLevelBoundary()` with `camera.getWidth()`. At native 320px width the ROM-accurate `+$128` / `+$128 + $40` constants are reproduced exactly; widescreen viewports widen the boundary to the configured viewport width (declared divergence, see KNOWN_DISCREPANCIES.md).

- **Added display aspect config surface (experimental).** Three new config keys wire the widescreen foundation into the configuration layer: `DISPLAY_ASPECT` selects a preset pixel width (`NATIVE_4_3` / `WIDE_16_10` / `WIDE_16_9` / `ULTRA_21_9` / `SUPER_32_9`), `DISPLAY_WINDOW_AUTOSIZE` controls whether the OS window is derived from the preset at 2x baseline, and `WIDESCREEN_DEADZONE_MODE` prepares the camera deadzone policy for wide layouts. Derived pixel/window values are resolved into a non-persisted in-memory overlay so they are never written to `config.json`. Only `NATIVE_4_3` is fully supported; widescreen rendering (UI pillarbox, extended parallax) is not yet complete.

- **Added an in-game level editor MVP.** Toggle into an edit mode mid-play, paint chunks with the mouse, undo/redo strokes via `Block.saveState()/restoreState()`, and persist edits through the editor save envelope. Editor enter/exit uses teardown+rebuild while `WorldSession` survives, re-applying `MutableLevel` edits on resume.

- **Added the deterministic rewind/playback framework.** New snapshot registry (`RewindSnapshottable`/`CompositeSnapshot`/`RewindRegistry`), in-memory keyframe store, segment cache (O(1) backward step), and `RewindController`/`PlaybackController`, with per-subsystem snapshot adapters (level, object manager, camera, game state, RNG, timers, fade, parallax, water, palette/zone/animated-tile/render registries, level-event managers, RingManager). Generic per-object/sprite field capture, optimized ring and level snapshotting, and trace-mode rewind playback wiring were layered on top, including follow-history buffers and `SidekickCpuController` state so the CPU sidekick resumes identical behavior after seek/replay.

- **Added config-gated live rewind.** Hold-to-rewind gameplay playback with an on-screen HUD overlay and dedicated input handler/stepper, gated behind a new configuration flag. The rewind HUD counter resets to 0 at level/act/zone boundaries, and a `stepBackward` crash when the earliest keyframe fell off the keyframe-interval grid after a boundary reset was fixed.

- **Added audio rewind runtime delivery.** A deterministic audio runtime (PCM/FIFO history rings, audio command timeline, and chip/SMPS snapshots) so sound replays correctly during gameplay rewind.

- **Rewind: slow-motion (sub-1.0) step rates and speed-matched reverse audio.** Tape-coast rewind supports sub-1.0 step rates for slow-motion rewind (`LIVE_REWIND_TAPE_COAST_MIN_STEPS` floor) and resamples reverse audio playback to match the current rewind speed.

- **Rewind audio: configurable PCM history cap with larger defaults.** The PCM history cap is now user-configurable by time or size via `REWIND_AUDIO_HISTORY_LIMIT_TYPE` / `REWIND_AUDIO_HISTORY_SECONDS` / `REWIND_AUDIO_HISTORY_SIZE_MB`, and the default limits were raised from 10 s / 2 MB to 60 s / 10 MB.

- **Completed the runtime session migration.** Retired the `GameRuntime` and `RuntimeManager` façades; gameplay-state ownership flows through `EngineServices`, `SessionManager`, and `GameplayModeContext`.

- **Implemented the S3K Ice Cap Zone object set.** ICZ ice block (top-solid), ice cube, snow pile (zone variants/art), tension platform, breakable wall, Freezer, harmful ice hazard, crushing column (ROM-sized trigger footprint), stalagtite, and ice spikes, registering the corresponding S3KL object ids.

- **Implemented the S3K ICZ path-follow platform and swinging platform.** The path-follow platform at its terminal right-wall stop spawns the revealed spring, displaces Sonic off the platform, and deletes the block after the route completes; ridden moving platforms use the ROM `Fast_V_scroll_flag` fast vertical camera cap. The ICZ swinging platform (object 0xB4) has ROM-accurate swing motion, solid collision, and palette-correct rendering.

- **Implemented the S3K Ice Cap Zone minibosses/bosses.** ICZ1 miniboss (ROM-backed art, post-boss palette cleanup, ICZ1→ICZ2 transition gated on `Apparent_act`) and the ICZ2 end boss (egg capsule, snow-pile interaction) on a shared S3K boss camera-gate.

- **S3K ICZ opening-sequence and background parity.** ICZ scroll handler, opening mountain palette setup, snowboard intro event shell, ROM-gated palette cycling (holds line 4 until the indoor flag is active), animated BG tile uploads, and indoor/outdoor palette event writes. Post-snowboard wall-crash handoff with falling big snow pile, jump-escape collision, lock-on background snow rendering, sprite-priority masking, and segment-column shatter debris. Fixed the snowboard intro title-card handoff and board-launch height (Sonic pinned to the ROM-computed terrain-arc point before board-bounce velocities).

- **Implemented S3K badniks: Penguinator and StarPointer.** Both with ROM-accurate behavior/art and registered object ids.

- **Completed the S3K CNZ Act 2 first-Knuckles cutscene.** Pre-seeded flood water level, button screen-shake, end-of-cutscene palette restore, and an invisible blocking wall holding Sonic during the scene; fixed the button-press chain, the water recede (so `Obj_CNZWaterLevelCorkFloor` observes the real CorkFloor child before setting the recede target), and aligned CNZ Act 2 palette cycling and BG scroll with the ROM. Added CNZ actors (CutsceneKnuckles CNZ2 A/B, Batbot/Sparkle badnik, water-level/cutscene button objects, `CnzLightsFlashChild`).

- **S3K CNZ miniboss and traversal-object parity.** The CNZ miniboss stays dormant until the arena trigger (`Camera_X >= $31E0`) and 2-second `Obj_Wait`, matching `Obj_CNZMiniboss`; its looping BG band is clamped to the ROM's 256px height. Restored the miniboss act-transition flow (scroll control, top/spark behavior, signpost handoff) and repaired CNZ traversal objects (barber pole crossing handoff, cannon, scripted-velocity animation, debug-mode latch gating, lightbulb, trap-door open-hold, sparkle phases, signpost/results lifetime).

- **S3K AIZ route fixes.** Fire-curtain effect survives the seamless AIZ1→AIZ2 reload while clearing stale cache on AIZ1 start / AIZ exit; post-bombing AIZ2 forest-loop wrap lands inside the forest mask and AIZ2 tree objects persist until their ROM delete predicates. Fixed the collapsing fire-log bridge top landing boundary/top-solid gating, AIZ end-boss active-collision timing, AIZ object placement-window rewind after the ship loop, sidekick boundaries after the battleship camera-bounds wrap, and ship-bomb touch response. The AIZ2 battleship auto-scroll now runs in a pre-physics phase with a temporary camera scroll-lock freeze (ROM `SpecialEvents`-before-`Process_Sprites` ordering), and the AIZ2 resize state machine runs before the screen-event handoff.

- **S3K AIZ physics parity.** Ground-wall push only sets `Status_Push` when the player faces into the contacted wall (S3K), water-exit y-velocity doubling is skipped on fast upward exits (S2/S3K), and the CPU sidekick follow/push logic was reworked to match ROM ordering — gated by new `PhysicsFeatureSet` flags, not zone carve-outs.

- **Fixed the S3K spindash release sound effect** so the release plays `sfx_Dash` instead of reusing the spindash charge SFX.

- **Fixed music being delayed at level start after a non-gameplay window** (e.g. title → level select → level) by clamping the gameplay audio frame to forward-only progression so backlogged audio commands drain immediately.

- **Newly added default config keys are backfilled into an existing `config.json` on load.**

- **Fixed the S3K end signpost so it persists into Act 2 across the seamless act reload** (offset by the transition world delta) for CNZ/HCZ/MGZ.

- **Fixed S3K ICZ frozen-block break damage** — shieldless freeze release spends rings via the hurt/death path; shielded damage only strips the shield. Also fixed stale object grounding in ICZ2, kept the AIZ hollow tree as live support, gated ICZ miniboss touch regions until the live routine starts, and fixed the ICZ2 CorkFloor roll break.

- **Fixed the S3K HCZ2 end-boss defeat handoff lifetime** and the S3K seamless results-screen transition gate.

- **S3K MGZ route fixes** — MGZ2 end-boss parity (drilling Robotnik art/PLC + events), swinging-platform despawn, object trace parity (dash trigger, swinging platform, monitor, spring, Bubbles badnik), air-roll/sidekick air-collision physics parity (new `PhysicsFeatureSet` flag), and MGZ2 rescue-Tails cleanup.

- **S2 sidekick death now uses the deferred-despawn flow (`Obj02_Dead`).** Gated by `PhysicsFeatureSet.sidekickDeathUsesDeferredDespawn`.

- **Touch-response framework: ENEMY-category callbacks now poll continuously every frame** (ROM `Touch_Loop`) instead of firing only on first overlap; SPECIAL/monitor contacts remain edge-triggered.

- **Sidekick CPU control tracks the delayed jump-press bit separately from held buttons** (`getJumpPressHistory`).

- **Improved Sonic 2 Sky Chase Zone parity** (SCZ object placement, Turtloid projectile, Tornado ride input timing, object hurt/platform landing).

- **Fixed Sonic 2 OOZ oil-surface landing to match ROM `PlatformObject_ChkYRange`** (per-player submersion state, ROM-accurate landing window/snap/inertia).

- **Restored the S2/S3K water enter/exit splash** via the fixed `Sonic_Dust` object (new slot-free splash mode).

- **Switching display color profiles now updates on-screen colors live** (reloads active level palettes via `Engine.refreshDisplayPalettes` / `LevelManager.reloadLevelPalettes`).

- **Fixed S3K AutoSpin tunnel landings** to preserve the `spin_dash_flag` mirror (S2 pinball landings still clear the pinball-mode mirror). New `PhysicsFeatureSet` flag.

- **Gated embedded monitor content (icon) timing to match ROM** across S2 and S3K monitors.

- **Spindash release no longer resets the camera position history** (only the horizontal scroll-frame offset), reproducing the ROM's old-position camera jerk (S2/S3K).

- **Keep moving CNZ hex bumpers alive based on range bounds** rather than unloading prematurely.

- **Fixed CNZ cylinder traversal so the CPU sidekick is recaptured correctly.**

- **Fixed players getting stuck on the master title screen** after returning from a trace/gameplay session (clears the stale runtime `FadeManager` reference).

- **Fixed an object-slot/memory leak** where air-unseat latches for permanently destroyed spawnless dynamic objects were never evicted.

- **Object slot inventory now resets together with placement state** for deterministic spawn windowing.

- **Aligned object solid-contact parity hooks** across `ObjectManager` and `SolidObjectProvider`/`SolidObjectListener`.

- **Performance: menu and disclaimer text now mega-batch into a single GL text draw per frame** (master title, trace picker, simple data-select, legal disclaimer).

- **Display color profiles can now be cycled at runtime.**
  The renderer supports raw RGB, darker Mega Drive analog, and softened NTSC-style
  palette presentation profiles. Press `V` by default to cycle them; the selection is
  persisted to `config.json` and confirmed briefly in the bottom-left corner.

- **S3K SMPS pitch, modulation, and resume timing now match the driver more closely.**
  Pitch ramps now restore signed accumulated pitch state through audio rewind snapshots,
  S3K modulation wait timing advances on the ROM's delayed cadence, spindash modulation
  continues during the freeze window, and 1-up music restore timing waits for the game
  audio profile's resume delay instead of restarting too early.

- **S3K foreground mask water alignment now uses the rendered viewport origin.**
  The foreground mask water fill follows the same background viewport math as the level
  renderer, preventing the water overlay from drifting when the camera/view origin is
  shifted.

- **CNZ1 carry-in Tails follow-ups: fly-off now actually rises, and rewinding back into the carry re-creates the carrier.**
  (1) The throwaway carrier sank to the floor and drifted right instead of flying off, because the Tails
  flight-ascent flap (`Tails_Move_FlySwim`, in `applyFlyingCarryVerticalVelocity`) is gated by
  `usesFlyingCarryMovement()`, which excluded the `CARRY_FLYOFF` state — so the injected A/B/C flaps did
  nothing and only +0x08 descent gravity applied. `usesFlyingCarryMovement()` now includes `CARRY_FLYOFF`,
  so routine `$10` rises (≈1px/frame up, slow rightward drift) and leaves the screen.
  (2) Rewinding back into the carry after the carrier flew off left Sonic object-controlled with no carrier:
  `SpriteManager`'s rewind restore removed temporary sidekicks missing from the snapshot but never re-created
  one that the snapshot still contained. `SpriteManager` now keeps a per-code re-creation factory
  (`addTemporarySidekick(sprite, name, recreator)`) that survives removal, and rebuilds the carrier on restore
  before reapplying its state; `Sonic3kCNZEvents` registers the carry-in Tails factory.

- **Fixed the CNZ1 solo-Sonic carry-in Tails: it now actually carries, flies off correctly, and survives rewind.**
  Four issues, all root-caused against `sonic3k.asm`:
  (1) *Sonic dropped from the sky.* The throwaway carrier was spawned in `Sonic3kCNZEvents.init()`
  (the `initLevelEvents` load step), which the immediately-following `spawnSidekicks` step deleted
  via `removeTemporarySidekicks()`. Moved the spawn to `applyZonePlayerState()` (the ROM
  `SpawnLevelMainSprites loc_68D8` location, which runs after sidekick placement in every load path).
  (2) *Fly-off shot off at extreme speed.* `updateCarryFlyoff` advanced position by a fixed +6px/-4px
  per frame; ROM routine `$10` (`loc_1408A`) pulses A/B/C+Right every 16 frames and lets normal
  `Tails_FlyingSwimming` physics carry it off at flight pace. Now mirrors the ROM.
  (3) *Jumping off left Tails following with full AI.* Jump-off/latch/hurt releases routed the
  throwaway carrier to `NORMAL`; ROM keeps it in routine `$E`'s `loc_14534` cooldown/regrab loop
  (re-grabbing Sonic in pickup range, playing `sfx_Grab`) until he lands → routine `$10` fly-off,
  never follow AI. Modelled faithfully. The `Tails_Carry_Sonic` A/B/C jump-out itself is ROM-accurate.
  (4) *Rewind keyframe capture crashed* (`RewindIdentityTable has no registered id for player reference`)
  once the carrier flew off while an object it had touched still referenced it. A captured reference to
  a player outside the team-slot rewind space is now encoded as a null/dangling reference instead of
  throwing (general fix for any removable/temporary player).

- **Repaired nine guard/functional test failures from the recent S3K CNZ/ICZ/AIZ bring-up.**
  All root-caused without zone/route/frame carve-outs: restored the slide-launch roll
  animation (`ScriptedVelocityAnimationProfile` now gates the airborne external-force
  null on `!isSliding()`, so a water-slide jump keeps `id_Roll` while the CNZ hover-fan
  walk animation still persists); registered the CNZ/ICZ miniboss
  `defeatExplosionController` fields in the central `DefaultObjectRewindPolicies` map
  (`DEFERRED`, matching every sibling boss) and baselined two ICZ structural-parent
  `@RewindTransient` pointers; refactored three object-physics-standardization
  violations to the standard contracts (`ObjectControlState.none()`, explicit
  `IczMiniboss.getTouchResponseProfile`, `ObjectPlayerQuery` for native P2); moved the
  throwaway carry-in Tails despawn out of `SidekickCpuController` (no more
  `GameServices`) into a post-update sweep in `SpriteManager`; extended the ArchUnit
  freeze store for the established `LiveRewindManager` audio and `ObjectManager`
  CNZ-miniboss rewind-recreation patterns; and corrected the CNZ miniboss headless
  tests to ROM-accurate dormancy timing (`Obj_CNZMiniboss`/`Obj_Wait`,
  `sonic3k.asm:144823-144895`). Verified trace-neutral against the committed AIZ/CNZ/MGZ
  trace frontiers.

- **Solo Sonic is now carried into Carnival Night Zone Act 1 by a throwaway Tails.**
  Matching the ROM (`SpawnLevelMainSprites` `loc_68D8`), a temporary Tails carries
  a sidekick-less Sonic into CNZ1; after dropping him on landing it flies up and to
  the right and removes itself once off-screen (Tails CPU routine `$10`), instead of
  following him. The persistent Sonic+Tails carry path is unchanged.

- fix(trace): TraceBinder now dedupes per-frame comparison results by frame number (TreeMap keyed by frame) instead of appending to an unbounded list. Fixes a memory balloon in test-mode held rewind where each SegmentCache rebuild re-compared already-compared frames and appended duplicate FrameComparison entries (and their full FieldComparison maps) to TraceBinder.allComparisons. Memory now bounded by trace length.

- perf(rewind): pool capture scratch buffers and add CompositeSnapshot.owned() ownership path for the registry hot path; reduces per-frame allocations in rewind.capture / rewind.step / rewind.restore without weakening the public CompositeSnapshot immutability contract.

- **`RewindController.stepBackward` keyframe-restore primer work now credits to `rewind.step` instead of falling into an unattributed gap before `rewind.tick` opens.**

- **Performance overview and rewind contributor guide updated for new profiler sections.**

- **Rewind profiler sections (`rewind.restore` / `rewind.step` / `rewind.seek` / `rewind.tick`) are now wired in production via `GameplayModeContext`.**

- **`RewindController.seekTo` now attributes work to `rewind.seek` / `rewind.tick` profiler sections (exception-safe).**

- **`RewindController.stepBackward` now attributes work to `rewind.step` / `rewind.tick` profiler sections (exception-safe).**

- **`RewindRegistry.restore` now wrapped in a `rewind.restore` profiler section; registry field narrowed to `SectionProfiler` interface.**

- **Legal disclaimer screen shown on engine startup before the master title screen.**
  White text on black, 5-second readability gate, any-key dismiss, fade-in/out/master-title-fade-in
  transitions. Toggle with the new `SHOW_LEGAL_DISCLAIMER_ON_STARTUP` config key (default `true`).

- **Trace replay tooling and shared object-code groundwork for the S3K route slices.**
  Trace replay tooling now records richer per-frame diagnostics, keeps trace
  comparison read-only, removes S2 bootstrap zone carve-outs in favour of recorder
  capabilities or live object semantics, and restores S2 native-prelude sidekick
  timing from ROM-visible title-card history; the no-zone-carveout rule is documented
  in agent docs and mirrored trace-replay skills. Shared solid/touch/object control
  code gained tighter participation, riding, camera-bound, ring, collision, and rewind
  handling to support the AIZ/CNZ/MGZ/ICZ route slices above without game-specific
  engine hacks. (Specific zone, object, and boss work is itemized in the entries
  above.) Full non-trace test suite passes; current trace frontier state is recorded
  in `docs/TRACE_FRONTIER_LOG.md`.

- **Object physics standardization final cleanup pass.**
  Follow-up cleanup moved additional object-control call sites onto `ObjectControlState`,
  routed event/feature player selection through `ObjectPlayerQuery` participation policies,
  converted transient helper-object expiry to `ObjectLifetimeOps.expireDynamic`, and added
  canonical touch-response profile declarations to a small projectile sample. Agent-facing
  docs and mirrored object-implementation skills now direct playable native position writes
  through `NativePositionOps` instead of raw preserve-subpixel setters.
  A follow-up review fix preserves S2 springboard P1-to-native-P2 launch sequencing for its
  shared animation state while still using the query/policy layer, and mirrors the
  object-behavior contract guidance into `AGENTS_S3K.md` plus boss implementation skills.

- **Object physics standardization review fixes.**
  Tightened post-standardization object physics behavior after external review: restored the
  left-wall previous-tile fall-through distance in `ObjectTerrainUtils`, fixed MGZ twisting-loop
  native-P2 selection, preserved multi-sidekick participation for OOZ launcher behavior, kept CNZ
  wire cage native-P2 intent while preventing non-sprite update fallbacks from selecting the main
  player, and repaired lifecycle/query/control edge cases around no-respawn deletion, test
  `ObjectServices` defaults, S2 flipper object-control preservation, and S2 sidekick destroyed-ride
  despawn timing. The HCZ breakable bar and conveyor object-control migrations intentionally expose
  ROM-style touch vulnerability while captured; this is an acknowledged gameplay-observable change
  from the previous engine behavior. `ObjectPlayerQuery`'s extended native-P2 policy remains a
  semantic caller-intent distinction from `ALL_ENGINE_PLAYERS`; current participant sets are
  equivalent until per-sidekick latch semantics are added.

- **S2 ceiling extension scan missing `+16` correction fixed (ARZ1 f1102 -> f1106).**
  `GroundSensor.scanTileVertical` with `isExtension=true` and `metric<0, adjusted<0` returned
  `(byte)~yInTile` directly from FindFloor2's `not.w d1`, but the ROM's FindFloor
  (`loc_1E7E2`, s2.asm:42989) follows the FindFloor2 call with `addi.w #$10,d1` (+16).
  Without the +16 the extension tile at y=037F (one tile above the probe at y=038F) in ARZ1
  produced distance=-1 at frame 1102 — a spurious ceiling hit that zeroed ySpeed and pushed
  Sonic 1px too low. Fix: changed `(byte)~yInTile` to `(byte)(~yInTile + 16)` in the
  `isExtension=true, metric<0, adjusted<0` branch. ROM refs: `FindFloor2` `loc_1E900`
  (`not.w d1`, s2.asm:43064); `FindFloor` `loc_1E7E2` (`addi.w #$10,d1`, s2.asm:42989).
  Advances ARZ1 frontier from frame 1102 (648 errors) to frame 1106 (624 errors).

- **S2 Springboard (Obj40) sidekick (Tails) contact drives animation switch (MCZ2 f2418 -> f3003).**
  `SpringboardObjectInstance.update()` previously called `updateLaunchSequence` only for the
  main character (Sonic). The ROM's `Obj40_Main` calls `SlopedSolid_SingleCharacter` and
  `loc_2641E` for BOTH `MainCharacter` (p1_standing_bit) and `Sidekick` (p2_standing_bit)
  (s2.asm:51839-51847). When Tails stands on the high side of the Springboard, loc_2641E
  checks the threshold and switches the animation from IDLE to COMPRESSED. Without this,
  the engine kept using `Obj40_SlopeData_DiagUp` (height[sampleX=12]=0x0E=14) instead of
  `Obj40_SlopeData_Straight` (height[sampleX=12]=0x0C=12), placing Tails 2px too high
  (actual y=0x02EB, expected y=0x02ED at frame 2418). Fix: resolve the checkpoint batch
  once via `checkpointAll()` (avoids double-resolution) and call `updateLaunchSequence`
  for each sidekick using their result from the shared batch.
  Advances MCZ2 frontier from frame 2418 (571 errors) to frame 3003 (527 errors).

- **S2 MTZ object parity fixes for steam springs, signposts, and long platforms.**
  `SteamSpringObjectInstance` now clears both `status.player.on_object` and the
  `ObjectManager` riding latch when Obj42 launches the player, matching ROM `bclr
  #status.player.on_object` behavior and preventing stale platform support after launch.
  `SignpostObjectInstance` and `MTZLongPlatformObjectInstance` now compare
  `services().currentZone()` against `Sonic2ZoneConstants.ROM_ZONE_MTZ` instead of the
  internal engine zone id, restoring the MTZ Act 2 signpost exception and the MTZ Act 3
  subtype-5 two-stop conveyor branch. Added focused regressions in
  `TestSonic2ObjectBugFixes`.

- **S2 Springboard (Obj40) first-contact launch guard and sloped catch range (MCZ2 f2226 -> f2418).**
  Two ROM-accuracy fixes for `SpringboardObjectInstance`:
  (1) `addsSlopeCatchRangeToVerticalOverlap()` overridden to return `true`: ROM
  `SlopedSolid_cont` (s2.asm:35066) adds the object's half-height (d2=8) to the catch range
  before computing relY: `add.w d3,d2` (d2=halfHeight+yRadius), then `add.w d2,d3`
  (d3=playerY−baseY+4+halfHeight+yRadius). The default in `SlopedSolidProvider` was `false`,
  placing Sonic 2px too low on initial contact (frame 2226: actual y=0x0340, expected=0x033E).
  (2) `updateLaunchSequence` no longer falls through to the `mapping_frame==0` launch check
  on the same frame the animation switches from idle to compressed. ROM `loc_26446`
  (s2.asm:51868): `cmpi.b #1,anim(a0) / beq.s loc_26456` — if NOT already compressed, it
  sets anim=1 and `rts`, returning without checking `mapping_frame`. The engine previously
  fell through unconditionally; since `mapping_frame==0` at the start of IDLE→COMPRESSED
  transitions, the launch fired one frame early, producing gSpeed=0x0001 (twirl inertia)
  instead of gSpeed=0x0100 (xSpeed set by the landing snap). Fix: add `return` after
  `setAnimId(ANIM_COMPRESSED)` when the animation was not already compressed.
  Advances MCZ2 frontier from frame 2226 (724 errors) to frame 2418 (571 errors). ARZ1
  improved as a downstream effect from frame 964 (664 errors) to frame 964 (625 errors).
  MCZ1 unchanged at frame 1085 (452 errors). EHZ1 still passes full trace.

- **S2 Tails `minStartRollSpeed` corrected from 264 to 128 (ARZ1 f980 -> f1102).**
  `PhysicsProfile.SONIC_2_TAILS.minStartRollSpeed` was 264 (0x108) — a stale placeholder.
  ROM `Tails_Roll` (`s2.asm:39962`) uses `cmpi.w #$80,d0`, identical to `Sonic_Roll`
  (`s2.asm:36983`). With the wrong threshold, Tails's `doCheckStartRoll()` returned early
  when `|gSpeed|=253 < 264`, so Tails never rolled when starting to be carried on a monitor
  at frame 980. Root cause: the `minStartRollSpeed` field comment said "Tails-specific" and
  the value 264 was carried from before ROM verification. Fix: changed threshold to 128 (0x80)
  and updated the comment with the ROM reference.
  Advances ARZ1 frontier from frame 980 (675 errors) to frame 1102 (648 errors).

- **S2 Monitor (Obj26) rolling gate bypassed for already-standing player (ARZ1 f964 -> f980).**
  `MonitorObjectInstance.isSolidFor()` returned `!player.getRolling()` unconditionally for the
  main character. When Sonic started rolling at frame 964 while standing on a monitor, this
  returned `false`, causing `SolidContacts` to clear the riding state. The terrain probe then
  set `air=1` since Sonic was above the terrain surface. Root cause: ROM's
  `SolidObject_Monitor_Sonic` (s2.asm:25448-25453) gates on rolling only for *new* landings.
  When the p1_standing_bit is already set in the monitor's status byte it skips the rolling
  check entirely (`btst d6,status(a0)` / `bne.s Obj26_ChkOverEdge`) and goes straight to the
  edge/carry path. Fix: added `if (mainCharacterStanding) { return true; }` before the rolling
  check, matching the ROM's "already standing → bypass" path.
  Advances ARZ1 frontier from frame 964 (664 errors) to frame 980 (675 errors).

- **S2 MCZ Drawbridge (Obj81) landing position, zero-distance landing gate, and half-width (MCZ2 f1774 -> f2226).**
  Three ROM-accuracy fixes for `MCZDrawbridgeObjectInstance` (Obj81 / `JmpTo22_SolidObject`):
  (1) `PARAMS_DOWN.halfWidth` corrected from 64 to 75 (ROM `loc_2A1A8` s2.asm:56578:
  `move.w #$4B,d1` — `d1` is the halfWidth passed to `JmpTo22_SolidObject`; the narrower
  `width_pixels=$40=64` in the object header is used only for the secondary
  `SolidObject_Landed` inner hit-width check, not the primary detection/riding width).
  (2) `allowsZeroDistanceTopSolidLanding()` overridden to return `true`: ROM
  `SolidObject_TopBottom` → `SolidObject_Landed` is guarded by `blo.s SolidObject_Landed`
  (unsigned lower, includes d3=0), so zero-distance landings are valid on SolidObject-based
  objects. The global `topSolidLandingAllowsZeroDist=false` for S2 was modeled on
  `PlatformObject_ChkYRange`, which has a different gate.
  (3) `usesPlatformObjectLandingSnap()` overridden to return `false` in
  `MCZDrawbridgeObjectInstance` and the new default `true` added to `SolidObjectProvider`:
  `applyNonUnifiedTopSolidLandingHeightOverride` implements `PlatformObject_ChkYRange`
  (`anchorY - groundHalfHeight - yRadius - 1`), but Obj81 uses `SolidObject_Landed`
  (`playerY - relY + 3`), which `resolveContactInternal` already computes correctly. Without
  this guard the override was overwriting the correct position with a wrong one.
  Advances MCZ2 frontier from frame 1774 (806 errors) to frame 2226 (724 errors).

- **S2 Arrow Shooter (Obj22) sidekick-detection and animation-timing fixes (ARZ1 f311 -> f964).**
  Three bugs in `ArrowShooterObjectInstance` caused the fired Arrow to be 28px behind ROM position
  at frame 311, preventing Tails from being hit. (1) `updateDetection` only checked the main player
  (Sonic) via `update()`'s `playerEntity` arg; the ROM checks both `MainCharacter` and `Sidekick`
  (`bsr Obj22_DetectPlayer` called twice). In this trace Tails is the closer character, so the
  engine never detected via Sonic only and transitioned from idle→detecting 15 frames early when
  Sonic briefly crossed the 0x40-pixel threshold. (2) On the idle→firing transition, `animTimer`
  was set to `DELAY_FIRING=7` instead of 0; ROM's `AnimateSprite` processes the first firing-entry
  immediately on the same call that sets `anim=2` (since `anim_frame_duration` is reset to 0 then
  immediately decremented to −1), adding 8 extra frames. (3) The engine fired the arrow after all
  5 `FIRING_SEQUENCE` entries (`FIRING_CALLBACK_INDEX=5`) instead of after entry index 2 (which
  represents the frame after the ROM's `$FC` callback); ROM's `$FC` fires after showing only
  frames 3 and 4 (entries 0–1). Fix: `updateDetection` now uses `isWithinDetectionRange(entity)`
  for both the main player and all sidekicks from `services().sidekicks()`; `animTimer` is
  initialised to 0 on FIRING entry; `FIRING_CALLBACK_INDEX` changed from 5 to 3 (fires after
  FIRING_SEQUENCE[2]=4 is set, matching the frame after ROM's `$FC`); `fireArrow()` uses
  `addDynamicObjectNextFrame` instead of `addDynamicObject` to replicate the 1-frame gap between
  `$FC` changing `routine` and `Obj22_ShootArrow` actually allocating the arrow on the next frame.
  Advances ARZ1 frontier from frame 311 (868 errors) to frame 964 (664 errors).

- **S2 Springboard (Obj40) stale launch sequence fires when riding nearby swinging platform (MCZ2 f1487 -> f1774).**
  `SpringboardObjectInstance.updateLaunchSequence()` used `result.postContact().onObject()` as
  part of the `launchContactNow` guard. `postContact().onObject()` reflects the player's GLOBAL
  `isOnObject()` state after the springboard's checkpoint runs — not contact with this specific
  springboard. In MCZ2 at frame 1487, Sonic is riding a SwingingPlatform within X range of the
  springboard. The stale `launchSequenceActive` flag combined with `postContact().onObject()=true`
  kept `launchContactNow=true`, triggering `applyLaunch()` (air=1, ySpeed=FC00, gSpeed=0001)
  while Sonic was 564 px below the springboard. Root cause: the previous `else if` branch that
  preserved `launchSequenceActive` within X range also bypassed the `clearLaunchSequence()` call
  when the player was riding any object nearby, not specifically this springboard.
  Fix: replaced `launchContactNow` with `result.kind() != ContactKind.NONE` (per-object contact
  only), and changed `else if` persistence to a plain `else` (always clear when no contact).
  This matches the ROM's `SlopedSolid_SingleCharacter` fast-path: the standing bit is cleared
  on any frame where `SolidObject_TestClearPush` or `loc_1980A` determines the player is out
  of Y range, airborne, or out of X range — there is no separate persistence window. Advances
  MCZ2 frontier from frame 1487 (773 errors) to frame 1774 (806 errors). ARZ1 error count
  increases from 813 to 816 (same frontier frame 304, pre-existing tails_x_speed mismatch;
  the 3 extra errors appear after the existing frontier, not before it).

- **S2 ARZ Rising Pillar (Obj2B) player-release clears on_object and riding state (ARZ1 f304 -> f311).**
  `RisingPillarObjectInstance.releasePlayerAndBreak()` set rolling=true and in_air=true on the
  launched player but omitted `bclr #status.player.on_object,status(a1)` from ROM `loc_25AF6`
  (s2.asm:51401), leaving the engine player with both in_air=true and on_object=true. Two
  consequences:
  (1) The stale riding-state entry in `ObjectManager.SolidContacts.ridingStates` caused
  `PlayableSpriteMovement`'s pre-movement recovery (line 456) to re-ground the player on the
  next frame (`hasGroundingObjectSupport()` returned true from stale data → setAir(false),
  setOnObject(true)), applying object-riding deceleration instead of pure air deceleration.
  Engine produced tails_x_speed=0x00C2; ROM expects 0x00D0 (−0x18 air deceleration per frame).
  Fix: call `player.setOnObject(false)` (matching ROM bclr on_object) and
  `objectManager.clearRidingObject(player)` (clears stale riding state) in `releasePlayerAndBreak`.
  Advances ARZ1 (TestS2ArzLevelSelectTraceReplay) trace frontier from frame 304 (813 errors)
  to frame 311 (846 errors — Tails misses arrow hit due to pre-existing Obj22 Arrow
  position divergence, separate issue). EHZ1 passes; ARZ2/MCZ1/MCZ2 unchanged.

- **S2 Tails y_radius preservation on non-rolling terrain/object landing (MCZ2 f1290 -> f1487).**
  `ObjectManager.SolidContacts.clearRollingOnLanding()` had an else-if branch that called
  `applyStandingRadii(false)` whenever a player landed with `rolling=false` but with non-default
  radii (e.g. y_radius=14 left over from a prior rolling state). This is S3K behavior:
  ROM `Player_TouchFloor` (`sonic3k.asm:24341-24343` / `29134-29136`) unconditionally resets
  y_radius/x_radius before testing `Status_Roll`, so S3K can leave Tails with rolling radii
  but no roll bit set (via the despawn marker's `Status_InAir` direct write) and still recover
  on the next landing. S2 `Tails_ResetOnFloor` (`s2.asm:40624-40641`) uses `btst #Status_Roll;
  bne Tails_ResetOnFloor_Part2`, gating the y_radius reset on `Status_Roll` being set. When
  not rolling it skips the reset, preserving the stale y_radius value that the despawn path
  left behind. In MCZ2 at frame 1290, `TailsCPU_Flying` respawn (`s2.asm:38797`) clears rolling
  via a direct status byte write without touching y_radius, leaving y_radius=14 (rollYRadius).
  The spurious `applyStandingRadii(false)` call then reset y_radius 14→15 one pixel too early,
  raising Tails' ceiling probe position by 1 px and causing a different ceiling collision result.
  Fix: gate the non-rolling radius reset on `featureSet.landingRollClearUsesCurrentYRadiusDelta()`
  (true only for S3K). Advances MCZ2 trace frontier from frame 1290 (816 errors) to frame 1487
  (773 errors — air mismatch from SwingingPlatform oscillation divergence, pre-existing issue).
  MCZ1 (frame 1085, 452 errors) and EHZ1 (frame 304, 813 errors) unchanged.
  Also fixed `GroundSensor.scanTileVertical()` PARTIAL_EMPTY path: the `-16` correction
  applied to `prevResult` distance after a `FindFloor2` extension pass was double-counting
  the tile-relative offset. ROM `loc_1E86A` applies `subi.w #$10,d1` to adjust for
  FindFloor2's d2-relative distance, but the engine's `scanTileVertical` always computes
  distance relative to `origY` (not the shifted check position), so the -16 is already
  embedded. Removed the redundant subtraction.

- **S2 SwingingPlatform (Obj15) out-of-range unload and CalcSine angle convention.**
  Advances MCZ2 trace frontier from frame 1009 (909 errors) to frame 1290 (816 errors).
  Two bugs fixed:
  (1) The constructor called `updatePositions(0)` which placed the platform 136 px east
  of its pivot (for chainCount=8), exceeding the 640 px `isOutOfRangeS1` threshold.
  Because the S2 exec loop runs `syncActiveSpawnsLoad` before `runExecLoop`, the object
  was immediately unloaded and marked dormant before its first `update()` call, preventing
  it from ever becoming active. Fix: removed the constructor call; `this.x` starts at
  `baseX`. Added `getOutOfRangeReferenceX()` override returning `baseX` so the range
  check anchors on the spawn pivot, matching the ROM's `obX(a0)` = parent `x_pos` = baseX
  at the time of the check (ROM `s2.asm:22541-22551` / `loc_FE50`).
  (2) `updatePositions` applied `swingAngle = (oscValue - 0x40) & 0xFF` before looking
  up sin/cos, then used `(-sin, cos)` as `(X, Y)`. This assumed the SINCOSLIST is
  perfectly antisymmetric (SINCOSLIST[i+128] == -SINCOSLIST[i]), but the ROM table is
  not: SINCOSLIST[147] = -117 while SINCOSLIST[19] = 115 (difference = 2; verified
  against `docs/s2disasm/misc/sinewave.bin`). The ROM calls `CalcSine(oscValue)` directly
  (`s2.asm:22604`) and assigns d0=sin to Y, d1=cos to X. Fix: removed the swingAngle
  offset; now calls `calcSine(oscValue)` and `calcCosine(oscValue)` directly (verified
  against ROM's fixed-point accumulation for all 3840 osc×chainCount combinations).
  BOUNCE_LEFT/BOUNCE_RIGHT clamp logic was also corrected to remove a spurious
  player-proximity gate (ROM sub_FE70 has no gate) and fix BOUNCE_LEFT osc==0x3F
  threshold (plays knock sound + clamps; was previously clumped with `< 0x40`).

- **S2 MTZ2 Conveyor (Obj6C) parent factory re-spawn loop.**
  `ConveyorObjectInstance.createOrSpawnChildren` returned `null` for
  parent-spawner subtypes (bit 7 set). Because the `ObjectManager` placement
  `sortedNewSpawns` gate (`ObjectManager.java:2362`) only suppresses spawns
  already in `activeObjects`, the parent re-entered the spawn list every
  frame it stayed in the camera window — Diagnostic logging showed the
  8-child cohort spawning ≈25,914 times in a single MTZ2 trace. Fix: mirror
  ROM `Obj6C_Init` `loc_28112` (`s2.asm:54269-54301`), which uses
  `movea.l a0,a1` to reuse the parent slot as the first child. The factory
  now constructs the first child from `layout[0]` using the parent's own
  `ObjectSpawn` and returns it, so `registerActiveObject` records the spawn
  and placement stops re-spawning. Remaining 7 children still spawn via
  `addDynamicObject`. Advances MTZ2 trace frontier y mismatch at frame 305
  from 7 px (engine 0x05F2 vs ROM 0x05EB) to 1 px (engine 0x05EA);
  MTZ1/MTZ3/EHZ1/CNZ/MCZ2 unchanged.

- **S2 SteamSpring (Obj42) bypasses offscreen sidekick full-solid gate.**
  Advances MTZ3 trace frontier from frame 460 (`tails_air` 0 vs 1, Tails
  missed-landing event) to frame 765 (`air` 1 vs 0); error count drops
  2626 -> 2601. ROM `Obj42` (`s2.asm:52030-52049`, `loc_26688`) calls
  `SolidObject_Always_SingleCharacter` for BOTH Sonic and Tails, which jumps
  directly to `SolidObject_cont` (`s2.asm:35147`) without consulting the
  regular `SolidObject` P2 `render_flags.on_screen` gate (`s2.asm:34825-34828`)
  or the `SolidObject_OnScreenTest` (`s2.asm:35140-35145`). The engine
  applied `shouldSkipOffscreenSidekickFullSolid` to the SteamSpring's P2 pass,
  so off-screen Tails passed through the piston instead of landing. Fix mirrors
  `SpringObjectInstance.bypassesOffscreenSolidGate() = true` and follows
  pitfall P27. MTZ/MTZ2/MCZ2/EHZ1 baselines unchanged. Noted-but-not-applied:
  Obj66 (MTZSpringWall), Obj7B (PipeExitSpring), Obj86 (Flipper), and ObjD6
  (PointPokey) also use `SolidObject_Always_SingleCharacter` and currently
  lack the override; pursue when the next relevant trace frontier surfaces.

- **S2 CNZ2 frontier investigation: Crawl closer-player selection (doc-only, reverted).**
  Confirmed `CrawlBadnikInstance` must use `Obj_GetOrientationToPlayer`-style closest-player
  selection (by absolute X distance); the fix advances CNZ2 from f1490 to f630, but f630 then
  exposes a pre-existing 1px X drift in the first CNZ2 Crawl (slot s20) producing a wrong bounce
  angle. Both the s20 (1px) and s17 (3px) Crawl X drifts are pre-existing, unrelated bugs, so the
  closer-player fix is reverted until the drift is diagnosed. See `TRACE_FRONTIER_LOG.md`.

- **S2 MTZ3 Obj6A per-phase activation gate.** `MCZRotPformsObjectInstance.
  loadPhaseParameters()` did not mirror ROM `loc_27CA2`'s `move.b #0,objoff_36
  (a0)` (`s2.asm:53844`). ROM Obj6A in MTZ runs via routine 2 (`loc_27BDE` at
  s2.asm:53754) which gates `ObjectMove` on `objoff_36`, so each call to
  `loc_27CA2` clears the gate and the next frame falls back to standing-bit
  walk-off detection. The engine latched `activated=true` permanently after
  the first walk-off, cycling through all four MTZ phases unattended; over
  ~340 frames the cumulative drift put MTZ3 slot 17's platform 0x2C px right
  and 0x16 px up of ROM, exactly where engine Tails lands on the platform top
  while ROM Tails keeps falling. Fix: reset `activated` on phase end when
  `isMtz` (MCZ uses routine 4 which ignores `objoff_36`). Advances MTZ3 trace
  frontier frame 340 (`tails_g_speed` 0x0000 vs 0x0018) -> frame 460
  (`tails_air` 0 vs 1, Tails missed-landing event). MTZ1 errors drop from
  945 -> 905 at the same frame 375 frontier. MTZ2/EHZ1/ARZ/CPZ/CNZ/HTZ/MCZ/
  OOZ/SCZ/WFZ/DEZ unchanged; S3K AIZ/CNZ/MGZ unchanged.

- **S2 SwingingPlatform (Obj15) chainCount cap removal + half-link offset.**
  Advances MCZ2 trace frontier from frame 1006 to frame 1079 (781 -> 802 errors).
  `SwingingPlatformObjectInstance` clamped subtype's low nybble to `min(7, ...)`
  for chainCount, but ROM `Obj15_Init` (`s2.asm:22480`) only does
  `andi.w #$F,d1` with no upper cap; MCZ2's first swinging platform (spawn
  subtype `0x18`) needs 8 chains. The platform's position offset from the
  pivot was also missing the half-link bias: ROM `sub_FE70`
  (`s2.asm:22645-22654`) accumulates `sin/cos*0x10` per chain link then halves
  the last increment (`asr.l #1`) for the platform's final position, yielding
  `(chainCount + 0.5) * 0x10`, not `chainCount * 0x10`. Together these put the
  engine's MCZ2 platform 24 pixels too high at `(0x0620, 0x05B8)` while ROM
  had it at `(0x0620, 0x05D0)`, causing the engine to catch Sonic on a
  platform he never landed on in ROM. Removed the `min(7, ...)` cap and added
  the `+8` half-link offset to `chainLength` for the platform position only
  (chain link positions still use `(i+1)*0x10`).

- **S2 MTZ2 Conveyor (Obj6C) child base-position fix.** Children spawned by an
  Obj6C parent (bit 7 of subtype set) were each constructed with their own
  offset spawn position used as the waypoint-path origin (`baseX/baseY`,
  `objoff_30/_32`). ROM `Obj6C_LoadSubObject` (`s2.asm:54137-54151`) captures
  the PARENT's `x_pos`/`y_pos` into `d2`/`d3` before the spawn loop and writes
  those unchanged into every child's `objoff_30/_32`, while the child's
  `x_pos/y_pos` is set to `parent + layoutOffset`. Without this each child
  orbited its own initial offset point instead of the shared parent center,
  scattering the MTZ2 layout-1 platforms (engine had conveyors at
  x=0x0340/0x037D where ROM had them at x=0x0320 forming the vertical spine
  across the lava pit). Added a second `ConveyorObjectInstance` constructor
  accepting explicit `baseX/baseY` and routed the parent's position through
  `createOrSpawnChildren`. MTZ2 trace replay: engine now correctly lands Sonic
  on the s29 conveyor at frame 305; frontier frame unchanged (305 y-snap delta),
  error count 2189 → 2325 (post-landing y is 7 px low for the conveyor run
  instead of falling off-screen). Other MTZ acts, MTZ3, and EHZ1 unaffected.

- **S2 Spring (Obj41) clears Hurt routine on vertical/diagonal launch.**
  Advances MCZ2 trace frontier from frame 925 to frame 1006 (737 -> 781 errors).
  `SpringObjectInstance.applyUpSpring`/`applyDownSpring`/`applyDiagonalSpring`
  did not clear the engine's `hurt` flag when launching the player. ROM
  `Obj41_Up loc_189CA` (`s2.asm:33735`), `Obj41_Down loc_18CC6` (`s2.asm:34023`),
  and the diagonal launchers (`loc_18DD8` line 34090, `loc_18EE6` line 34173)
  all unconditionally write `move.b #2,routine(a1)` — overwriting the routine
  byte from 4 (`Obj01_Hurt`) to 2 (`Obj01_Control`) so subsequent airborne
  frames use `Obj01_MdAir`'s +$38 gravity plus `Sonic_UpVelCap` (-$FC0) instead
  of `Obj01_Hurt`'s +$30 gravity (no cap). The engine left `hurt=true`, which
  skipped `doJumpHeight()`/`applyUpwardVelocityCap` and returned 0x30 from
  `getGravity()`. MCZ2 f925: ROM `y_speed=-0F88` (cap from -$1000 to -$FC0
  then +$38) vs engine `-0FD0` (uncapped -$1000 + $30); diff 0x48 = missing
  0x40 cap delta plus 0x08 gravity-step shortfall. `applyHorizontalSpring`
  intentionally left alone — `Obj41_Horizontal loc_18AEE` does NOT write
  `routine = 2` (player stays grounded).
- **S2 MTZ3 Obj6A (MCZRotPforms) zone-aware behavior fix.** Advances MTZ3 trace
  frontier from frame 298 (`air` 0 vs 1) to frame 340 (`tails_g_speed`).
  `MCZRotPformsObjectInstance` was hard-wired to MCZ behavior in every zone:
  MCZ velocity tables (`byte_27CF4`/`byte_27D12`), MCZ `y_radius=0x20`,
  unconditional movement, and `spawn.subtype() & 0x0F` as the table cursor.
  ROM `Obj6A_Init` (`s2.asm:53686-53751`) branches on `Current_Zone`: MTZ uses
  `byte_27CDC` (4-phase), `y_radius=0x0C`, routine 2 (`loc_27BDE`, wait for
  the player to walk off), and skips the subtype-0x18 child-spawn block; MCZ
  uses `byte_27CF4`/`byte_27D12`, `y_radius=0x20`, routine 4 (`loc_27C66`,
  move unconditionally), and spawns two child platforms for subtype 0x18.
  ROM also stores the FULL `subtype` byte into `objoff_38` (`s2.asm:53750`)
  and uses it as a byte offset into the velocity table — same "byte offset
  is not array index" pitfall as the Obj65 fix. Rewrote the constructor to
  detect zone via `services().currentZone()`, pick the correct table /
  `y_radius` / parent-flag, store the full subtype byte as the phase cursor,
  and gate movement on `activated` (MTZ waits, MCZ starts activated).
- **S2 MTZ Long Platform (Obj65) properties-index and non-conveyor carry fix.**
  Two bugs in `MTZLongPlatformObjectInstance` that combined to keep button-
  triggered platforms stationary at spawn and then, once they moved, to carry
  the rider as if they were conveyor belts. (1) ROM `Obj65_Init`
  (`s2.asm:52379-52414`) decodes the subtype byte into a byte-offset `d0` into
  the 2-byte-per-entry `Obj65_Properties` table (`s2.asm:52366-52376`) and uses
  a separate second `lsr.w #2,d0` for `mapping_frame`; the engine was using the
  shifted-twice value as the `PROPERTIES[]` row index, so a subtype `0xB1`
  platform read width/y_radius from entry 3 and maxDist/childSubtype from entry
  4 instead of entries 6/7, ending up with `moveSubtype = 0` (stationary) and
  `currentDist = 0` (no retract baseline). (2) Once the platform-index fix made
  the subtype-7 retract platform glide left, the engine's continued-riding path
  shifted the rider by the full `currentX - ridingX` delta, whereas ROM `Obj65`
  refreshes `objoff_2E` to the new x_pos in `loc_26D50` (subtypes 1/2/6/7) and
  `loc_26E1A` (subtype 3) so `MvSonicOnPtfm` (`s2.asm:35402-35423`) sees a
  zero carry delta. Added `SolidObjectProvider.carriesRiderOnHorizontalMove`
  (default true) and overrode it on `MTZLongPlatformObjectInstance` to return
  true only for the conveyor subtype 5 (`loc_26E4A`), matching ROM's two
  carry-reference refresh paths. MTZ2 trace frontier moves from frame 221 to
  frame 305 (-181 errors); MTZ act 1 drops from 1015 to 989 errors at the
  same frame 281 Steam Spring frontier; ARZ/CNZ/CNZ2/EHZ1/MTZ3/OOZ unchanged.

- **S2 MCZ VineSwitch (0x7F) edge-trigger release and Tails grab support
  (MCZ2 trace replay).** Two bugs in `VineSwitchObjectInstance`. (1) The
  release-on-button check used `isJumpPressed()` (held state). ROM
  `Obj7F_Action` loads `Ctrl_1` as a word and then `andi.b #ABC,d0`, which
  operates on the low byte that holds `Ctrl_1_Press` (just-pressed this
  frame), not `Ctrl_1_Held` — so the player only releases on a fresh ABC
  press. With held state, Sonic was released a single frame after grabbing
  whenever B was still held from the jump that brought him to the vine.
  Switched to `isJumpJustPressed()`. (2) The sidekick branch (ROM
  `lea (Sidekick).w,a1 / move.w (Ctrl_2).w,d0 / bsr.s Obj7F_Action`) was
  marked as deferred, so Tails could never grab the vine. Added a sidekick
  pass via `services().sidekicks()`. Advances the MCZ2 trace frontier from
  frame 198 to frame 925 (936 -> 737 errors).

- **S2 OOZ Aquis (Obj50) four-bug ROM-accuracy fix.** Combined patch addressing
  four confirmed divergences from `s2disasm` Obj50: (1) missing initial
  `move.w #-$100, x_vel` from `Obj50_Init` (s2.asm:60100); (2) `bmi`-style
  timer semantics in `Obj50_FollowPlayer` and `Obj50_WaitForNextShot`
  (s2.asm:60244-60245, 60275-60276) replacing the engine's off-by-one
  `if (--timer <= 0)`; (3) closer-player orientation via
  `Obj_GetOrientationToPlayer` (s2.asm:72320-72346) using the sidekick when
  it's closer than the main player; (4) `render_flags.on_screen` one-frame lag
  in `Obj50_CheckIfOnScreen` (s2.asm:60181-60188) replacing the engine's
  instantaneous `isOnScreen(32)` check with a paired this-frame/last-frame
  flag set from `appendRenderCommands()`. Reduces OOZ2 trace-replay error
  count from 1329 to 1280 (-49). OOZ1 first-error frontier (Buzzer at f509)
  and OOZ2 first-error frontier (Tails+Spring/Fan `tails_y_speed` at f301)
  are both gated by unrelated bugs and do not move; see
  `docs/TRACE_FRONTIER_LOG.md` for details. No EHZ1/MTZ/CNZ regression.

- **S2 OOZ Aquis investigation (no committed fix).** Documented an Aquis (`Obj50`)
  investigation against the OOZ1 (frontier frame 563 `g_speed`) and OOZ2 (frontier
  frame 389 `y_speed` sign reversal) traces. Three ROM-vs-engine deltas identified
  — missing `move.w #-$100, x_vel(a0)` in `Obj50_Init` (`s2.asm:60100`), P30
  `bmi`-style timer in `Obj50_FollowPlayer`/`Obj50_WaitForNextShot`
  (`s2.asm:60244, 60275`), and closer-player orientation
  (`Obj_GetOrientationToPlayer`, `s2.asm:72320-72346`). Each fix reduces OOZ2
  errors but introduces an OOZ1 regression; none advances either frontier.

- **S2 CNZ2 pinball_mode preservation flag fix.** `PhysicsFeatureSet.SONIC_2.
  pinballLandingPreservesPinballMode` was still `false`, causing CNZ2 to regress to
  frame 936. ROM `Sonic_ResetOnFloor` / `Tails_ResetOnFloor` (`s2.asm:37770-37771,
  40625-40626`) never clear `pinball_mode`; both branch to `Part3` which only clears
  in_air/pushing/rolljumping/jumping. Flag flipped to `true` restores the CNZ2
  frontier to frame 1490.

- **S2 MTZ SteamSpring timing and MTZLongPlatform props-lookup fix.** Two
  independent MTZ object fixes advancing the MTZ1 frontier from frame 281 to 375
  and MTZ2 from 221 to 222. `SteamSpringObjectInstance` switched to
  `MANUAL_CHECKPOINT` mode so `SolidObject_Always_SingleCharacter` runs before the
  state machine moves `y_pos`, matching ROM `loc_26688` (`s2.asm:52030-52049`).
  `MTZLongPlatformObjectInstance.init()` used `d0 >> 2` for both the props-table
  entry index and `mapping_frame`, collapsing 8 ROM entries to 4; ROM
  `s2.asm:52386-52394` uses `d0/2` for the entry and `d0/4` for the frame
  independently.

- **S2 OOZ Octus collision size and rise timing fix.** Two fixes advancing OOZ1
  frontier from frame 509 to 563 and OOZ2 from 301 to 389. `OctusBadnikInstance`
  used touch size index 0x0C ((20,16) half-extents) where ROM writes
  `collision_flags=$A` → Touch_Sizes[0xA]=(16,8) (`s2.asm:59905`). Octus
  rise/hover state transitions used `timer <= 0` (triggered at zero) where ROM uses
  `bmi` (triggered when timer goes negative), and the rise transition did not apply
  `y_vel=-$200` on the transition frame as ROM's fall-through to `JmpTo19_ObjectMove`
  does (`s2.asm:59958-59988`).

- **S2 CNZ2 pinball-mode Tails jump and landing-clear fix.** Three related fixes
  advancing the CNZ2 trace frontier from frame 554 to frame 1490. (1) ROM
  `Tails_ResetOnFloor` (`s2.asm:40624-40660`) branches past the roll-clear block
  when `pinball_mode` is set and never clears it; `PlayableSpriteMovement.
  resetOnFloor()` was unconditionally calling `setPinballMode(false)`, preventing
  Tails from staying in pinball mode through a landing. (2) ROM `Obj02_MdRoll`
  (`s2.asm:39279-39282`) skips `Tails_Jump` entirely when `pinball_mode` is set;
  `SidekickCpuController.updateNormal()` did not mirror this check, so the 16-frame
  delayed B-press fired a jump even while Tails was rolling in pinball mode on the
  ground. (3) `doCheckStartRoll()` had an over-broad move_lock CPU guard that
  prevented S2 Tails from rolling during move_lock; S1/S2 `Sonic_RollStart` has no
  such gate (`s2.asm:36954-36963,39939-39942`), so the guard is now scoped to S3K
  only where `Tails_InputAcceleration_Path` actually gates it
  (`sonic3k.asm:27797-27815`).

- **S2 Crawl (0xC8) bounce physics and ENEMY dispatch fix (CNZ2 trace replay).**
  Two bugs combined to produce wrong post-bounce velocity. First, `COLLISION_SIZE_INDEX`
  was 0x09 (12×16 px) instead of ROM's 0x17 (8×8 px), inflating the touch window.
  Second, `applyBounce` applied a flat −$700 y_vel instead of the ROM's radial
  `CalcAngle + CalcSine` computation (`loc_3D3A4`, s2.asm:81932-81958), and the
  ENEMY touch dispatch in `ObjectManager` called `applyEnemyBounce` after
  `onPlayerAttack` because `hpBeforeHit=0 && !wasAlreadyDestroyed`, overwriting
  the radial y_vel with the standard kill-bounce (+256 offset). `CrawlBadnikInstance`:
  size index corrected to 0x17; `applyBounce` rewritten with `TrigLookupTable`.
  `ObjectManager`: `applyEnemyBounce` now gated on instance being destroyed by
  `onPlayerAttack` (both player and sidekick paths), so shield-bounce objects that
  handle their own velocity without self-destructing are not double-bounced.
  CNZ2 trace frontier advanced from frame 435 (`x_speed`) to frame 554 (`tails_y`).

- **S2 horizontal spring right-edge collision parity (CNZ2 trace replay).** ROM
  `Obj41_Horizontal` routes through `SolidObject_cont` (`s2.asm:35147`), which
  rejects the X range with `bhi` (strictly greater than). This makes `relX ==
  halfWidth*2` (player centre exactly at the spring's right edge) a valid side
  contact. The engine's `usesInclusiveRightEdge()` defaulted to `false`
  (`relXRaw >= rightLimit`), silently skipping that one-pixel boundary case.
  `SpringObjectInstance` now overrides `usesInclusiveRightEdge()` to return
  `true` for `TYPE_HORIZONTAL`, matching the existing pattern in
  `Sonic3kSpringObjectInstance`. CNZ2 trace frontier advanced from frame 205
  (`camera_x`) to frame 435 (`x_speed`).

- **S2 HTZ Rexon detection-window asymmetry (HTZ trace replay).** ROM
  `Obj94_WaitForPlayer` (s2.asm:73716-73722) uses `Obj_GetOrientationToPlayer`
  to compute signed `d2 = body_x - player_x`, adds `$60`, and compares against
  `$100` as UNSIGNED word (`bhs.s`). The window is asymmetric around the body:
  signed `body_x - player_x` must lie in `[-$60, +$A0)`. The engine ported the
  check as `Math.abs(...) + 0x60 < 0x100`, which is symmetric and includes a
  64-px-wide left-side band (`(-$A0, -$60)`) that ROM rejects. With the player
  approaching from the right, this fired detection ~34 frames earlier than ROM,
  stopping the body several pixels right of ROM and shifting every downstream
  head trajectory by the same offset. The Tails hurt-bounce direction at f5044
  ended up reversed (engine +$200 vs ROM -$200) because the head that ultimately
  hit Tails had drifted across her x-position. Fix ports the literal ROM
  `(signedDelta + $60) & $FFFF < $100` window in `RexonBadnikInstance.
  checkPlayerInRange`. HTZ frontier advanced f5044 (446 errs) -> f5511 (398 errs).
  P12-class pitfall already catalogued.

- **S3K AIZ1 Tails dormant-marker timing (AIZ trace replay).** ROM
  `loc_13A10` (sonic3k.asm:26389-26397) writes Tails' dormant sentinel
  (x_pos=$7F00, y_pos=0, Tails_CPU_routine=$A) on the FIRST tick that
  dispatches Tails_CPU_Control — inside the same ROM frame that
  SpawnLevelMainSprites placed her at `Player_1 - $20`. The engine's
  `SidekickCpuController.updateInit` previously split the AIZ1 dormant
  marker into two ticks (prime placement, then apply sentinel on the next
  tick), so the first comparison frame after gameplay started (AIZ trace
  frame 290) saw Tails at the level-start offset (0x0020, 0x0424) while
  ROM already had her at (0x7F00, 0x0000). Combining the level-start
  placement + dormant marker into a single `updateInit` branch matches
  the ROM's single-tick sequence. AIZ first error advanced f290 -> f720.

- **S3K sidekick bootstrap parity (CNZ/MGZ trace replay).** Three S3K
  level-start divergences shared the same root cause: the engine cleared
  `Status_InAir` on the sidekick after `applyZonePlayerState` set it for
  MGZ1 / HCZ1 / LRZ1 / SSZ falling intros. Fix preserves the zone-event
  in-air state in `SidekickCpuController.applyLevelStartSidekickPlacement`,
  re-runs `Sonic3kLevelEventManager.applyZonePlayerState` after the test
  fixture's `repositionRegisteredSidekicks` step (matching ROM's
  `SpawnLevelMainSprites_SpawnPlayers` -> `SpawnLevelMainSprites` order,
  sonic3k.asm:8132-8427), and adds a one-tick S3K sidekick prelude for
  seed-frame-mode traces (CNZ Sonic+Tails) so the Tails carry trigger
  (`loc_13A5A`, sonic3k.asm:26405-26415) and one in-air gravity tick
  (`MoveSprite_TestGravity`) run before the frame-0 seed comparator
  fires. CNZ first error advanced f0 -> f4790; MGZ f0 -> f1538.

- **S2 native-prelude trace infrastructure (spec 2026-05-15).** The engine's
  title-card phase now runs `ObjectManager` and player physics every frame
  (universal ADR-1, matching ROM `TitleCard_Main` across S1/S2/S3K). This
  populates Sonic's `Sonic_Pos_Record_Buf` natively during the prelude,
  removing the root cause of the early-frontier Tails sub-state divergence
  that capped every S2 level-select trace at ~300 frames. New trace
  infrastructure surfaces: `TraceBinder.compareBootstrapFrame0` + new
  `BootstrapDivergence` / `EngineSnapshot` records assert engine frame-0
  state against the recorder's `player_history_snapshot`,
  `cpu_state_snapshot`, and `object_state_snapshot` events for traces
  recorded at `lua_script_version >= 9.2-s2` (see
  `TraceMetadata.nativePreludeMode()`). Comparator results flow into
  `DivergenceReport` as a new `bootstrap` category rendered ahead of the
  per-frame divergences. `SidekickCpuController.hydrateFromRomCpuState`
  widened to accept `targetX`/`targetY`. The deprecated
  `*PreludeFramesForTraceReplay` knobs on `TraceReplayBootstrap` return 0
  unconditionally (workaround for the title-card freeze, no longer needed).
  New `oggf.trace.hydrate` system property (see CONFIGURATION.md) lets
  developers snap engine state to the recorded frame-0 snapshot for
  prelude-vs-gameplay bug isolation; off by default and CI-asserted off.
  S2 level-select trace tests will pick up the improvement once their
  metadata files are re-recorded with the v9.2-s2 recorder (T9 follow-up).


- **Fix regressions from architectural review hardening.** Two
  follow-up fixes for issues introduced by the previous entry.
  `GenericFieldCapturer.usesCodecFieldSnapshot` now skips the codec
  snapshot path for non-final fields whose codec declares
  `requiresExistingTargetValue()` (currently `ObjectAnimationStateCodec`),
  falling through to `deepCloneValue` so lazy-initialised animation
  state — notably `MonitorObjectInstance.animationState` — restores
  cleanly when null at restore time. `RewindSchemaRegistry`'s compact
  path already had the equivalent guard; the generic path now matches.
  Resolves `IllegalStateException: Cannot restore in-place rewind field
  ... because the target value is null` in `TestRewindTorture`,
  `TestRewindTraceSeekDeterminism`, `TestRewindIter1631Diagnostic`, and
  `TestAbstractObjectInstanceRewindCapture#defaultClassFallsBackToGenericSidecarForNullableAnimationState`.
  Separately, the MGZ BG-rise state machine now propagates correctly
  to the registered `SwScrlMgz`: `MgzZoneRuntimeState` gains
  `publishBgRiseState` (canonical events-side write) and
  `syncBgRiseToScrollHandler` (resolves the registered handler via
  `GameServices.parallaxOrNull` so the events package retains its
  architectural separation), `SwScrlMgz.setBgRiseState` write-throughs
  to runtime state, and `Sonic3kMGZEvents.updatePrePhysics` syncs the
  handler after its state machine — previously `LevelFrameStep.execute`
  skipped `parallaxManager.update()` so the handler cache stayed stale
  between event tick and render, and `setBgRiseState` only mutated the
  local cache which the next `update()` overwrote. Verified against
  `MGZ2_BGEventTrigger` (`sonic3k.asm:107117`) and `MGZ2_BGDeform`
  (`Lockon S3/Screen Events.asm:1090-1126`). Restores
  `TestS3kMgz2BgRiseHeadless#eventsStateTransitionPropagatesToRegisteredSwScrlMgz`
  and `#swScrlMgz_stateEightProducesDifferentScrollFromStateZero`.

- **Architectural review hardening.** `RewindRegistry.capture()` now rejects `null` snapshots and
  `restore()` requires explicit `RewindSnapshottable.resetForMissingSnapshot()` (default throws, so
  subsystems fail closed) instead of silently skipping missing entries that masked coverage gaps.
  `GameplayModeContext.isGameplayRuntimeReady()` returns `false` once `tearDownManagers()` has run.
  MGZ scroll-event state (screen shake, BG rise routine/offset, boss BG scroll offset) moved off
  direct `SwScrlMgz` setters onto a new `MgzZoneRuntimeState` adapter installed through
  `ZoneRuntimeRegistry` (`SwScrlMgz.update()` reads runtime state at frame time, `init()` clears
  event-owned overrides on re-entry); `Sonic3kMGZEvents`, `MGZTriggerPlatformObjectInstance`,
  `MgzMinibossInstance`, and `TunnelbotBadnikInstance` migrated, with a `TestArchitecturalReviewGuard`
  guard rejecting future direct `SwScrlMgz` use. `BossExplosionObjectInstance` drops its
  `ObjectRenderManager` constructor argument and resolves the renderer via `services().renderManager()`
  at draw time (threaded through `AbstractBossInstance.spawnDefeatExplosion`, `Sonic1FZBossInstance`,
  `FZPlasmaLauncher`, the S2 boss-explosion factory, and `CPZBossFallingPart`), and
  `Aiz2BossEndSequenceController` compares the HCZ transition Y threshold against `getCentreY()`
  (ROM `y_pos`) rather than `getY()`. `MgzMinibossInstance.KnucklesSpikePlatformChild` takes
  parent-captured camera coords and `TurboSpikerBadnikInstance` moves its water-splash SFX onto the
  parent so child constructors no longer call `services()`; `TestNoServicesInObjectConstructors` is
  hardened to catch qualified `obj.services()` calls and modifier-less constructors.
  `GraphicsManager.getUiRenderPipeline()` syncs the live runtime `FadeManager` after rebuild, and
  `DefaultPowerUpSpawner` resolves `ObjectServices` lazily so it survives teardown/rebuild.
  Trace-replay invariant tests move out of a hidden Surefire exclusion into the trace-replay profile
  (`**/tests/trace/**/*.java`) so they actually run, with new coverage (`TestRewindRegistry`,
  `TestGameplayModeContextRewindRegistry`, `TestS3kZoneRuntimeStateAdapters`,
  `TestAiz2BossEndSequenceObjects`, `TestGraphicsManagerFadeRebinding`); `archunit.properties` pins
  `freeze.store.default.allowStoreUpdate=false` so frozen baselines don't auto-update.
- **Rewind capture for final in-place helper fields.**
  `GenericFieldCapturer` now captures and restores `final` helper
  fields (e.g. `final SubpixelMotion.State motion = new ...`) through
  `RewindCodec`-driven `CodecFieldSnapshot` payloads. Codecs that opt
  in via `RewindCodec.capturesFinalFields()` serialize the helper's
  scalar state into a `RewindStateBuffer` at capture and restore it
  back into the existing field at replay, bypassing the previous
  reject-on-final policy that required helper references to be
  reassignable. `CutsceneKnucklesAiz1Instance.motionState` is
  annotated `@RewindTransient` to document that the helper is a
  scratch holder rebuilt from captured scalar position/velocity
  fields; the rewind annotation baseline in
  `TestRewindArchitectureGuard` is updated accordingly. New tests:
  `TestGenericFieldCapturer.roundTripsFinalSubpixelMotionStateInPlace`
  covers the codec-backed round trip;
  `TestCutsceneKnucklesAiz1Instance.rewindCaptureSkipsScratchMotionState`
  and the parallel `TestSonic3kMonitorObjectInstance` case cover the
  AIZ cutscene and S3K monitor capture paths called out as motivating
  examples.
- **Complete architecture guard coverage.** Expands the ArchUnit and source
  guard suite across runtime/session ownership, runtime-owned registry
  construction, concrete Sonic provider construction, trace replay hydration
  invariants, trace parser dependencies, rewind override/annotation growth,
  `GameId` behavior branching, runtime disassembly asset access,
  `Engine.java` responsibility budgets, `ObjectArtData` game-specific surface
  growth, object coordinate hazard scanning, and singleton/gameplay lifecycle
  setup drift in tests. New frozen ArchUnit baselines document existing
  migration debt while preventing new violations. Stabilizes the sidekick
  follow parity fixture so it resets the active game module before each test in
  reused Maven forks, and migrates sidekick, timer, level-init, and object
  lifecycle tests onto `TestEnvironment.resetAll()` to shrink the lifecycle
  baseline. Strengthens the object-service migration guard with one
  consolidated scanner that covers game object packages and shared object
  infrastructure for direct global runtime access, leaving only documented
  line-level bridge exceptions. Shared object guard helpers now live in one
  test utility, `ObjectManager` resolves rewind overlap sidekicks through
  injected `ObjectServices`, and the contributor guide documents the object
  service access contract. The shared-layer frozen ArchUnit baseline is also
  refreshed for the current `DefaultPowerUpSpawner` lambda bytecode form.
- **ArchUnit architecture guard adoption.** Adds the ArchUnit JUnit 5 test
  dependency and bytecode-level architecture rules for the existing dependency
  invariants around rewind snapshot interfaces, shared/service boundaries,
  level-layer S3K coupling, and cross-game data-select delegates. The
  source-text guard remains for comment/string/XML surfaces that bytecode
  analysis cannot inspect. Broader object-service, shared-layer, per-game slice,
  and JUnit 5 API rules now guard against new architectural drift, with current
  debt frozen under `src/test/resources/archunit/frozen` and documented in
  `docs/architecture/archunit-exceptions.md`.
- **Palette-cycle rewind coverage.** Adds a compact schema codec for palette
  cycle state and extends Sonic 2/S3K palette and level-animation managers with
  snapshot coverage so animated palette progress survives rewind round-trips.
  New tests cover the palette-cycle codec and S3K ICZ rewind restoration.
- **S3K CNZ miniboss completion.** Carnival Night Zone Act 1 now drives the
  miniboss arena through ROM-cited event flow: the tunnel approach, arena
  camera clamps, miniboss music, PLC/palette load, vertical scroll-control
  handoff, layout-wall mutations, and defeat release are all represented.
  The CNZ miniboss now has raw boss animations, explicit top/spinner and
  coil children, top-piece terrain/base collision behavior, closed-coil
  player touch routing through `ObjectManager`, and focused headless/object
  coverage. The CNZ cylinder carry path also preserves the held player
  position before launch so Sonic follows the cylinder down consistently.
- **Rewind automatic-capture tooling.** Moves `RewindScanSupport` into
  main sources so both tests and tools can share the runtime-owner source
  scanner, and replaces the disabled manual field-inventory JUnit test
  with `com.openggf.tools.rewind.RewindFieldInventoryTool`. The tool
  emits unsupported rewind fields and exits non-zero until a migration
  worklist is closed; `--object-rollout-candidates` reports concrete
  object classes currently covered by default subclass scalar capture.
  Adds the compact schema foundation under
  `com.openggf.game.rewind.schema`: cached class schemas, field policy
  classification, little-endian scalar buffers, codecs for supported value
  shapes, immutable state blobs, policy registry support, context-aware
  capture, and `CompactFieldCapturer` tests. Adds stable rewind identity
  value records/table for players, objects, and spawns, plus compact codecs
  for helper state, value collections/maps, immutable records, player
  references, and object references. This path runs beside
  `GenericFieldCapturer`; object/player snapshot rollout remains a
  follow-up after policy and codec coverage are proven.
- **Rewind object rollout now minimizes leaf-object churn.**
  `GenericRewindEligibility.usesDefaultObjectSubclassCapture(...)`
  centrally opts concrete `AbstractObjectInstance` subclasses into
  default subclass scalar capture when they do not declare custom
  `captureRewindState` / `restoreRewindState` overrides.
  `GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(...)`
  and `RewindFieldInventoryTool --object-rollout-candidates` expose the
  audit surface. Generic capture now also excludes `@RewindDeferred`
  fields, and shared type/policy decisions should live in codecs or
  `RewindPolicyRegistry` instead of repeated per-object annotations.
- **Rewind blueprint follow-through.** Default non-badnik object subclasses
  now capture compact schema-backed sidecar state through
  `PerObjectRewindSnapshot.compactGenericState` whenever all default scalar
  fields have codecs, falling back to the legacy generic snapshot only for
  unsupported shapes. `RewindFieldInventoryTool --annotation-density`
  reports transient/deferred annotation density and redundant transient
  annotations. `ChildGraphPolicyInventoryTool` adds an audit-only scan for
  child/spawn graph hotspots and policy prompts. A new encounter-validation
  test harness compares engine forward-only snapshots against engine
  rewind+replay snapshots, with an enabled S2 EHZ1 baseline scenario.
- **Rewind: snapshot monitor `effectTarget` by sprite code.**
  `AbstractMonitorObjectInstance.effectTarget` (the player who broke
  the monitor and is owed the power-up at icon apex) was annotated
  `@RewindDeferred` and so was excluded from snapshotting. Rewinding
  back into a frame mid-icon-rise nulled out the field, and the apex
  guard `if (!effectApplied && effectTarget != null)` skipped the
  `applyPowerup` call -- the player never received the
  shield/speed-shoes/etc. that the reference forward-run granted. The
  divergence surfaced as `sprites[0].playerExtra.shield: A=true B=false`
  plus the monitor slot's `effectApplied` field at iteration 1521 of
  `TestRewindTorture#tortureProgressiveLongRewinds` (the next gap after
  the `lastAnimationId` fix). `AbstractMonitorObjectInstance` now
  overrides `captureRewindState`/`restoreRewindState` to capture the
  player's stable sprite code in a new `MonitorRewindExtra` record on
  `objectSubclassExtra`, resolving back via `SpriteManager.getSprite`
  on restore. The `@RewindDeferred` annotation is retained as the
  audit-policy flag (effectTarget is still excluded from
  genericState), with its `reason` updated to point at the manual
  capture path. The torture test stays `@Disabled` because a separate
  object-manager slot-drift coverage gap surfaces at iteration ~1575;
  description updated.
- **Rewind: capture `PlayableSpriteAnimation.lastAnimationId`.**
  `lastAnimationId` is the previous-animation tracker compared against
  `sprite.animationId` on every animation update; a mismatch resets the
  script's `animationFrameIndex` and `animationTick` to 0. Without
  snapshotting it, repeated forward+rewind cycles (e.g. via
  `TestRewindTorture#tortureProgressiveLongRewinds`) drifted the
  tracker out of sync with the captured animation cursor, producing
  spurious script resets (or skipping real ones) on the first replay
  step. `mappingFrame`, `animationFrameIndex`, and `animationTick`
  diverged after roughly 720 progressive-long cycles. Adds a
  `PlayableSpriteAnimation.RewindState` record carrying
  `lastAnimationId`, captured/restored alongside the existing
  movement and spindash-dust state on `PlayerRewindExtra`. The torture
  test stays `@Disabled` because a separate snapshot-coverage gap
  (monitor-icon `effectTarget` is `@RewindDeferred`, breaking shield
  acquisition replay) surfaces deeper in the run; description updated.
- **Rewind torture test infrastructure.** Adds `TestRewindTorture` (S2
  EHZ1 trace) plus three pluggable `RewindTorturePattern`
  implementations -- adjacent rewinds (`FixedAdjacent` cycles of
  `forward=2, rewind=1`), end-to-end long rewinds with progressive
  landing (`ProgressiveLongRewind`), and seeded random
  forward/rewind cycles (`Random_`). The driver runs the pattern
  end-to-end against the trace, asserting `controller.currentFrame()`
  matches the simulated logical frame after every cycle and comparing
  full `CompositeSnapshot` content against a precomputed forward-only
  reference at scheduled checkpoints. The shared
  `RewindSnapshotDiff` helper produces path-based per-key diffs
  (e.g. `object-manager.slot[16].state.dynamicSpawnX: A=0 B=551`)
  capped at 20 leaf-diff lines per key, indexing
  `ObjectManagerSnapshot.slots` / `childSpawns` by slot identity so
  `IdentityHashMap`-induced ordering noise does not mask real state
  divergence. All five test methods are currently `@Disabled`
  pending the snapshot-coverage gaps each surfaces -- the
  infrastructure itself is the deliverable for future rewind work.
  Includes one fix surfaced by the test:
  `AbstractBadnikInstance.restoreRewindState` previously called
  `updateDynamicSpawn(currentX, currentY)` unconditionally after
  hydrating `BadnikRewindExtra`, which overwrote
  `dynamicSpawn = null` (set by the base-class restore from
  `s.hasDynamicSpawn() == false`) at frame-0-style snapshots where
  `currentX/Y` are at spawn position but `dynamicSpawn` had never been
  touched. Now gated by `s.hasDynamicSpawn()` so capture-after-restore
  round-trips at every frame.
- **LZ wind tunnels now preserve the player's subpixel fraction across
  the tunnel's per-frame X push and Y curve/input nudges.** ROM
  `LZWindTunnels` (`docs/s1disasm/_inc/LZWaterFeatures.asm:338,341,348,353`)
  applies its `addq.w #4,obX(a1)` X push, `add.w d0,obY(a1)` curve,
  and `subq.w #1,obY(a1)` / `addq.w #1,obY(a1)` up/down input nudges
  with word-only writes that touch only the pixel half of `obX`/`obY`,
  leaving `obSubpixelX`/`obSubpixelY` (offsets 0xA / 0xE) untouched.
  The engine called `setCentreX` / `setCentreY`, which zero
  `xSubpixel`/`ySubpixel`, so every frame Sonic stayed inside the
  tunnel the engine wiped his subpixel fraction. Migrated all four
  call sites (LZ + SBZ3 wind-tunnel updates) to
  `setCentreXPreserveSubpixel` / `setCentreYPreserveSubpixel`. The
  trace-replay sub_x desync of `0x6400` against the LZ3 credits-demo
  recording now matches ROM. The frame-221 +2 Y bump that remains is
  a separate, documented REV01 ROM-bug discrepancy (`d0` is overwritten
  by `move.b (v_vbla_byte).w,d0` then read as if it still held `obX`
  for the curve check); see `docs/KNOWN_DISCREPANCIES.md`.
  Also moved the wind-tunnel and water-slide rushing-water sound
  timers from a local frame counter to the global `v_vbla_byte`
  (`ObjectManager.getVblaCounter()`) so the sound cadence matches the
  ROM's global-vblank phasing rather than drifting whenever Sonic
  enters/exits the tunnel zone.
- **SBZ Rotating Junction (object 0x66) now preserves the player's
  subpixel fraction across `Jun_ChgPos` and the grab-midpoint adjust.**
  ROM `Jun_ChgPos`
  (`docs/s1disasm/_incObj/66 Rotating Junction.asm:167-172`) sets the
  player's pixel position with `move.w d0,obX(a1)` /
  `move.w d0,obY(a1)`, which writes only the upper word of each
  4-byte position field (`obX = 8`, `obSubpixelX = 0xA`,
  `obY = 0xC`, `obSubpixelY = 0xE` per `_Constants.asm:142-150`) and
  leaves the subpixel fraction untouched. The grab body
  (`obj66:87-93`) similarly relies on word-only `add.w` and `asr.w`
  on `obX(a1)`/`obY(a1)` while the disc rotates Sonic into place.
  The engine implementation called `setCentreX` /  `setCentreY`,
  which zero `xSubpixel`/`ySubpixel` on every write, so each
  junction frame advance was wiping any subpixel Sonic had
  accumulated before being grabbed. After release, gravity-driven
  `SpeedToPos` then accumulated from a zero subpixel base while the
  ROM continued from a non-zero residue, producing a 1-pixel drift
  by the time Sonic re-landed. On the SBZ1 credits demo this
  surfaced at trace frame 285 (`y=0x01A8` vs ROM `0x01A9`) with
  `ENG sub_y=0xA800` vs `ROM sub_y=0x2000`, and the 1-pixel offset
  cascaded through the rest of the demo (58 errors). Switching the
  two write sites to the `*PreserveSubpixel` helpers mirrors the
  word-only ROM stores. Greens `TestS1Credits05Sbz1TraceReplay`.
  Adds focused regression `TestSonic1JunctionSubpixelPreservation`.

- **Touch-response on-screen gate now checks Y as well as X.**
  `AbstractObjectInstance.isOnScreenForTouch()` previously returned true
  for any object whose pre-update X was within the camera viewport,
  ignoring Y entirely. ROM's gate is `obRender(a1) bit 7`, set by
  `BuildSprites` (`docs/s1disasm/_inc/BuildSprites.asm:71-78` for the
  default `.assumeHeight` branch when `obRender` bit 4 is clear, the
  case for rings and most gameplay objects), which marks an object
  off-screen when `obY - cameraY` is outside `[-32, 256)` — i.e. the
  visible 224-line viewport plus a 32 px margin above and below.
  ROM's `ReactToItem` (`docs/s1disasm/_incObj/sub ReactToItem.asm:26-27`)
  reads that bit with `tst.b obRender(a1) / bpl.s .next` and skips
  objects whose bit 7 is clear, so a ring whose Y has scrolled past
  the camera viewport is not eligible for touch responses. The engine
  was over-collecting: the SYZ3 credits demo at frame 253 collected an
  off-screen ring s43 at (0x186E, 0x0662) while the camera was at
  (0x17C2, 0x0556), giving rings=21 vs ROM rings=20. The fix uses
  `cameraBounds.contains(preUpdateX, preUpdateY, halfWidth, 32)` so
  the gate matches the previous frame's BuildSprites pass with the
  same 32 px Y margin the ROM uses. Greens the SYZ3 credits demo
  trace replay at frame 253. Adds focused regression
  `TestS1OffscreenYRingTouchSkip` and refreshes the cached
  `cameraBounds` inside `ObjectManager.snapshotTouchResponseState()` so
  the inline-physics path's gate sees the post-camera-update bounds
  matching ROM's BuildSprites-then-ReactToItem ordering.
  `TestHTZBossTouchResponse` setUp now also pins `camera.setY` to the
  boss arena Y; previously the test relied on the X-only on-screen
  gate to bypass a Y mismatch between camera (Y=0) and boss (Y=0x0580).
- **Touch-response Y gate is now S1-only.** The new
  `cameraBounds.contains(x, y, halfWidth, 32)` Y check above is
  ROM-correct for S1 only. ROM S2 `Touch_Loop`
  (`docs/s2disasm/s2.asm` ~84502-84551) has no equivalent render-flag
  gate at all — every active object is iterated regardless. ROM S3K
  `TouchResponse` (`docs/skdisasm/sonic3k.asm:20655`) consumes a
  pre-built `Collision_response_list` where the gate happens upstream
  during list build, not at touch time. Applying the X+Y check
  universally regressed S3K MGZ trace replay's first-fail from frame
  2395 to frame 1659 (Tails picked up an unintended `tails_rolling`
  state from objects ROM had on the response list). Added
  `PhysicsFeatureSet.touchResponseUsesRenderFlagYGate` per the
  per-game framework: `SONIC_1=true`, `SONIC_2=false`, `SONIC_3K=false`.
  `AbstractObjectInstance.isOnScreenForTouch()` branches on the flag —
  S1 keeps the X+Y gate (preserves the SYZ3 fix above); S2/S3K fall
  back to the pre-Task-3 X-only gate (`cameraBounds.containsX(x)`).
  Restores S3K MGZ trace replay first-fail to frame 2395, with
  S1 SYZ3 still at trace match.
- **MZ Push Block: skip inline solid resolution while in falling/sliding
  state.** `Sonic1PushBlockObjectInstance.updateActive` now gates its
  `checkpointAll()` call on the entering `solidState` being 0, mirroring
  ROM's `loc_C186` dispatch
  (`docs/s1disasm/_incObj/33 Pushable Blocks.asm:238-289`): only the state-0
  branch (`loc_C218`) calls `Solid_ChkEnter`. ROM's state-4 (`loc_C1AA`)
  and state-6 (`loc_C1F2`) paths return without ever testing for the
  player. Without the gate, the engine published a STANDING contact on
  the same frame the block transitioned from state 4 (falling) to state
  0 (lava motion), which established a riding state one frame too early.
  On the IMMEDIATELY next frame, `processInlineRidingObject`'s
  `shiftX(deltaX)` platform-rider carry then dragged the player along
  with the block's lava-slide -1 px movement — one frame ahead of ROM,
  where `MvSonicOnPtfm` only fires once `obSolid==2` (set on a different
  frame). Greens the MZ2 credits demo trace at frame 341 (ROM x=0x0E1A,
  ENG was 0x0E19). Adds focused regression
  `TestS1PushBlockSideContact` exercising the lava-slide first-frame
  carry against the live MZ2 credits demo input.
- **SLZ Elevator: post-jump rider pull-up.** `Sonic1ElevatorObjectInstance`
  now opts into `SolidObjectProvider.carriesAirborneRiderAfterExitPlatform`
  so the inline-riding carry runs after `ExitPlatform` clears the player's
  on-object bit on the same frame Sonic launches. Mirrors ROM
  `Elev_Action` (`docs/s1disasm/_incObj/59 SLZ Elevators.asm:84-101`),
  which calls `ExitPlatform` → `Elev_Move` → unconditional
  `MvSonicOnPtfm2` (`docs/s1disasm/_incObj/15 Swinging Platforms.asm:177-194`)
  even when the rider has just jumped. Without the override the engine
  applied the `Sonic_Jump` `addq.w #5, obY(a0)` rolling-radius adjust but
  missed the elevator's continued-riding y_pos write, which left the
  player ~2 px below ROM whenever the elevator moved up at the same
  time as the jump. Greens the SLZ3 credits demo trace at frame 500
  (ROM y=0x01F0, ENG was 0x01F2). Adds focused regression
  `TestS1JumpFromElevator` exercising the same jump-while-riding code
  path against a live SLZ act-3 fixture.
- **Architecture cleanup: renamed `EngineServices` → `EngineContext`.**
  Aligns with the design vocabulary in
  `docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md`,
  which calls the engine-globals container `EngineContext`. Mechanical
  rename across 113 Java files (~419 token occurrences); the test class
  `TestEngineServices` moved to `TestEngineContext`. Method names on
  `RuntimeManager` (`getEngineServices`/`currentEngineServices`/
  `configureEngineServices`) and `GameRuntime` (`getEngineServices`) were
  intentionally left alone — they're stable API; only the return type
  changed. The class file moved from `com.openggf.game.EngineServices` to
  `com.openggf.game.session.EngineContext`. CLAUDE.md and AGENTS.md
  updated to reflect the rename + the parking removal.
- **Architecture cleanup: dropped `RuntimeManager.parkCurrent` /
  `resumeParked` editor parking flow.** Per the runtime ownership migration
  design, world data lives on `WorldSession` (durable across mode swaps) and
  gameplay state lives on `GameplayModeContext` (disposable). With both in
  place, the parking mechanism — which preserved the runtime intact across
  editor mode entry — was redundant. `Engine.enterEditorFromCurrentPlayer`
  now does a proper teardown via `RuntimeManager.destroyCurrent()`,
  capturing/restoring the world-scoped state (loaded `Level`, zone/act,
  camera bounds) on `WorldSession` since `LevelManager.resetState()`
  write-throughs `null` during teardown. `Engine.resumePlaytestFromEditor`
  uses `initializeGameplayRuntime` + `LevelManager.restoreInheritedLevel()`
  to rebuild a fresh runtime over the surviving world. Removed
  `parkCurrent` / `resumeParked` / `parked` field /
  `suppressedGameplayMode` / `destroyParkedRuntimeIfSupersededBy` from
  `RuntimeManager`. Removed lazy-create-on-`getCurrent`, since that
  mid-flow side effect could re-attach fresh managers (replacing camera,
  sprite, etc.) to a still-referenced gameplay mode and surprise callers
  holding manager refs across the transition. Six parking-only tests
  removed; four tests updated to call `createGameplay()` explicitly
  instead of relying on auto-create. `TestEditorToggleIntegration`'s
  editor round-trip tests still pass — proving world preservation +
  gameplay counter reset on exit.
- **`spawnChild` / `spawnFreeChild` migration sweep across S1 object
  code.** Replaced direct `objectManager.addDynamicObject(...)` calls
  in S1 instance classes with the inherited
  `AbstractObjectInstance.spawnChild(() -> ...)` /
  `spawnFreeChild(() -> ...)` helpers so that the
  `CONSTRUCTION_CONTEXT` ThreadLocal is set before each child
  constructor runs. This guarantees children calling `services()`
  during construction see a non-null context and stops the migration
  guard from regressing. Original ROM allocation semantics are
  preserved (`addDynamicObject` -> `spawnFreeChild` for `FindFreeObj`,
  `addDynamicObjectAfterCurrent` -> `spawnChild` for
  `FindNextFreeObj`). Batches: badniks (Buzz Bomber, Cannonball,
  Crabmeat, Motobug, Newtron); bosses (FZ, MZ, GHZ, SLZ, SYZ, FZ
  plasma launcher, false floor, boss block, boss fire, SLZ spikeball);
  level objects (breakable wall, bumper, collapsing floor/ledge, egg
  prison, elevator, ending, gargoyle, giant ring, glass block, grass
  fire, junction, lamppost, large grassy platform, lava
  geyser/maker/wall, LZ conveyor, monitor, push block, ring flash,
  seesaw, signpost, smash block, spin conveyor). `addDynamicObject`
  call count inside classes extending `AbstractObjectInstance` under
  `game/sonic1/objects/` reduced to zero.

- **G4 follow-up: retired the broken
  `Sonic2SmpsLoader.resolveMusicOffsetFromRom` and removed the deferred
  priority-inversion TODO.** Investigation showed the function's premise
  was wrong, not just its byte order: the S2 driver's `zMasterPlaylist`
  flag table and per-bank pointer tables (`MusicPoint1`/`MusicPoint2`)
  live inside the **Saxman-compressed** Z80 driver blob in 68K ROM, so
  reading them as if they were uncompressed yields garbage regardless
  of endianness. The previous implementation also indirected through a
  stray pointer-to-pointer-table address (`MUSIC_PTR_TABLE_ADDR`,
  pointing into mid-driver code) and decoded the resulting Z80
  little-endian pointers as big-endian. On top of the compression
  problem, the engine's `Sonic2Music` IDs are systematically shifted
  relative to the disassembly's `zMasterPlaylist` entry order
  (`EMERALD_HILL.id == 0x81` loads the EHZ track, but
  `zMasterPlaylist[0]` is `Mus_2PResult`), so even a fully Z80-decompressed
  lookup would disagree with the engine's intended track for most IDs
  — `testChemicalPlantNoiseChannelEmitsVolume` confirmed this when the
  prototype ROM-first priority was tried. `findMusicOffset` is now a
  thin lookup over the hardcoded REV01 `musicMap` (returns -1 on miss);
  `resolveMusicOffsetFromRom` and the misleading `MUSIC_FLAGS_ADDR` /
  `MUSIC_PTR_TABLE_ADDR` constants were removed. The Javadoc captures
  the two prerequisites for a future ROM-driven path (decompress the
  Z80 driver first; reconcile engine-vs-disasm music ID schemes).
- **Architecture cleanup: removed game-id branching from
  `DefaultPowerUpSpawner`; documented G4 priority-inversion deferral in
  code.** `DefaultPowerUpSpawner.spawnInvincibilityStars` no longer
  switches on `instanceof Sonic3kGameModule`; instead, a new
  `GameModule.getInvincibilityStarsFactory()` default returns the
  game-agnostic `InvincibilityStarsObjectInstance::new`, and
  `Sonic3kGameModule` overrides it to return
  `Sonic3kInvincibilityStarsObjectInstance::new`. The S1 fixed shield
  slot (ROM `v_shieldobj` at slot 6) is now expressed as
  `PhysicsFeatureSet.shieldObjectFixedSlotIndex` (S1=6, S2/S3K=-1) and
  consumed by `addPowerUpObject`, replacing the second
  `instanceof Sonic1GameModule` check. Per-game behavioral differences
  in this class are now gated entirely through `GameModule` factories or
  `PhysicsFeatureSet` flags as required by `CLAUDE.md`. Separately, the
  G4 priority-inversion deferral previously documented only in the
  commit message now has an explicit `// TODO(G4-followup):` comment at
  the top of `Sonic2SmpsLoader.findMusicOffset` citing the symptom
  (Metropolis 0x82 / Chemical Plant 0x83 break TestRomAudioIntegration
  when ROM resolution is primary) and the byte-order root cause inside
  `resolveMusicOffsetFromRom`.
- **G4: consolidated S2 uncompressed-track constants; documented why
  ROM-resolution priority inversion is deferred.** The four uncompressed
  track ROM addresses (1-Up, Game Over, Got Emerald, Credits) and their
  explicit byte sizes are now named constants in `Sonic2SmpsConstants`
  (`UNCOMPRESSED_*_ADDR` / `_SIZE`), shared between
  `Sonic2SmpsLoader.musicMap` and `calculateUncompressedSize`. The
  intended priority inversion in `findMusicOffset` (try
  `resolveMusicOffsetFromRom` first, fall back to the empirical map)
  could not be applied — the existing ROM-resolution path produces
  wrong-but-non-negative offsets for several REV01 IDs (Metropolis 0x82,
  Chemical Plant 0x83), as confirmed by TestRomAudioIntegration failing
  when ROM resolution ran first. The endianness fix inside
  `resolveMusicOffsetFromRom` is a separate audio-engine change requiring
  independent verification; once that lands, the priority inversion
  becomes a one-line follow-up. A deferral note is in `findMusicOffset`'s
  Javadoc.
- **G3: residual cleanup of the runtime-ownership migration.**
  `StubObjectServices` now overrides `zoneRuntimeRegistry()` and
  `zoneRuntimeState()` so unit tests using the stub get a deterministic
  isolated `ZoneRuntimeRegistry` instead of silently routing through
  `GameServices` (which defaults to `new ZoneRuntimeRegistry()` when no
  runtime exists, producing a different fresh registry on each call —
  brittle for tests that read state back). `rng()` and
  `solidExecutionRegistry()` were already overridden.
  `TestEnvironment.resetAll()` now calls
  `AbstractObjectInstance.resetCameraBoundsForTests()` so the static
  `cameraBounds` field starts every test from `(0, 0, 320, 224)` rather
  than whatever the previous test left behind. Manager teardown moved
  off `GameRuntime.destroy()` and onto a new
  `GameplayModeContext.tearDownManagers()` helper called by
  `GameRuntime.destroy()`. `GameplayModeContext.destroy()` (the
  ModeContext interface override) remains a documented stub: the editor
  flow's `SessionManager.destroyCurrentMode()` must NOT trigger manager
  teardown while a parked runtime expects its managers to be alive on
  resume. Once parking is replaced with a proper world-preserving
  teardown, `tearDownManagers` can become `destroy()` directly. No
  behavioral change for production code paths.
- **G2: fixed Y-coord mix in `DebugRenderer.renderPlayerPlaneState` and
  expanded the pattern atlas range table.** The plane-state debug label
  was computing `screenY` from `playable.getY()` (top-left) while every
  other label used `getCentreY()`, producing a ~19px vertical drift.
  Changed to `getCentreY()` to match the sensor-dot rendering. Also
  documented `0x34000` (S3K dust art) and the shared-base contexts at
  `0x40000` and `0x50000` (multiple mutually-exclusive game subsystems
  reuse the same base) in `docs/KNOWN_DISCREPANCIES.md`. Note that
  `PatternAtlas.registerRange(...)` exists as a diagnostic collision
  detector but is not enforced at every call site; adding bootstrap-time
  `registerRange` calls in each owning subsystem is a follow-up.
- **G1: removed render-path allocation and scan hotspots in `PatternAtlas`
  and `GraphicsManager`.** `PatternAtlas.isSlotShared()` previously walked
  all 8192 fast entries plus the sparse map every time `removeEntry()` was
  called — the CNZ slot machine `uncachePattern` loop turned that into
  ~393K array reads per frame. Replaced the scan with a per-`(atlasIndex,
  slot)` reference count maintained by `putEntry`/`removeEntry`/`cleanupCommon`,
  so the alias-safety check is now O(1). Behaviour is preserved: the
  existing `TestPatternAtlasSlotReclamation` cases (slot reuse,
  alias-doesn't-free, free-slot capacity) all pass. In
  `GraphicsManager.endSpriteSatCollectionAndReplay()` and
  `buildSpriteSatReplayCommands()` the per-frame defensive copy of
  `spriteSatEntries`, the `new ArrayList<PatternRenderCommand>()` in the
  replay builder, and the per-piece `new PatternDesc()` in
  `appendDirectReplayCommands()` were eliminated. `process(...)` is the
  only consumer of the live entry list and either returns a fresh list or
  `List.of()` so the input can be drained directly; `reusableReplayCommands`
  and `reusableReplayDesc` are now reusable instance fields cleared at
  start of each replay (mirroring `PlayerSpriteRenderer.reusableDesc`).
  Net effect on the SAT replay hot path: 0 `ArrayList` allocations and 0
  `PatternDesc` allocations per call (was 2 + N).
- **F3: extracted `LevelManager` rendering pipeline into `LevelRenderer`.**
  Moved the per-frame rendering pass off `LevelManager`. The new
  `LevelRenderer` (in `com.openggf.level`) owns the pre-allocated
  `GLCommand` lambdas (water shader setup, BG ensure-capacity / tile pass /
  scroll, FG low+high priority passes, high-priority FBO pass, shimmer
  enable/disable), their mutable backing fields, the `viewportBuffer`, the
  resolved `AdvancedRenderFrameState`, the `currentShimmerStyle` tracker,
  and the bodies of `drawWithRenderOptions / renderSpriteObjectPass /
  renderEndingBackground / renderBackgroundShader / updateWaterShaderState
  / enqueueForegroundTilemapPass / renderHighPriorityTilesToFBO`.
  `LevelManager` keeps the public `draw / drawWithSpritePriority /
  drawWithRenderOptions / renderSpriteObjectPass / renderEndingBackground`
  entry points as one-line delegators so existing callers (`Engine.draw*`,
  `S1/S2DataSelectImageCacheManager`, visual regression tests) are
  unchanged. The render output is byte-identical: GL command registration
  order and shader uniform values are preserved. `LevelManager` shrinks
  from 4812 to 3768 lines (~22% reduction) and now imports only
  `glClearColor` from LWJGL (down from four `org.lwjgl.opengl.GL*.*`
  wildcard imports). The water shader state block is part of the
  extraction (`waterShaderSetupCommand`, `disableShimmerCommand`,
  `disableWaterShaderCommand`). Test profile matches the baseline at
  4216 passed / 44 failed / 0 errors.
- **F2 phase 4: completed `ScrollEffectComposer` adoption across all scroll
  handlers.** Migrated the remaining eight handlers from inline buffer
  bookkeeping to the shared composer: S2 `SwScrlOoz`, `SwScrlArz`,
  `SwScrlCnz` (rippling segment + 9 banded segments), `SwScrlDez` (36-element
  TempArray-driven row segments), `SwScrlWfz` (data-array-driven layer
  selection with normal/transition arrays), `SwScrlHtz` (gradient parallax
  for animated clouds + earthquake mode), S3K `SwScrlSlots` (per-line
  parallax driven by background deform segments + plane row updates), and
  S3K `SwScrlGumball` (per-column FG VSCROLL for machine body). Each handler
  now drives its own `ScrollEffectComposer` instance, writes its packed
  scroll output through the composer (including per-line ripple, segment
  fills, and pre-packed `int` writes), and publishes the composed buffer
  back to the caller's `horizScrollBuf` via `copyPackedScrollWordsTo`. Min/
  max scroll-offset bounds and `vscrollFactorBG` flow through the composer's
  tracked state. Added two helper overloads to the composer to support
  HTZ's pre-packed scroll writes:
  `writePackedScrollWord(int, int)` and
  `fillPackedScrollWords(int, int, int)`. With this commit, every
  `AbstractZoneScrollHandler` subclass (26 of 26: 7 S1, 11 S2, 8 S3K) goes
  through `ScrollEffectComposer`, completing the F2 scroll-handler unification.
  All scroll-handler unit tests remain green at the prior baseline; the
  pre-existing `SwScrlArzTest` / `SwScrlMczTest` setUp errors
  (EngineServices not configured) are unchanged.
- **F2 phase 3: migrated banded scroll handlers to `ScrollEffectComposer`.**
  Continues the F2 architectural fix by migrating the next set of scroll
  handlers - those whose `update()` writes a sequence of constant-bg-per-band
  fills, possibly with a small per-line section. Migrated: S3K `SwScrlCnz`
  (boss + normal CNZ paths); S2 `SwScrlEhz`, `SwScrlCpz`, `SwScrlMcz`; S1
  `SwScrlGhz` (and by inheritance `SwScrlEnd`), `SwScrlMz`, `SwScrlSlz`,
  `SwScrlSyz`. Each handler now drives its own `ScrollEffectComposer`,
  building the packed scroll buffer via `fillPackedScrollWords` for constant
  bands and `writePackedScrollWord` for per-line writes (water surface
  ripple, perspective interpolation, ARZ-style row variation), then
  publishes the composed words to the caller's `horizScrollBuf` via
  `copyPackedScrollWordsTo`. Vscroll factor and min/max scroll-offset
  bounds now flow through the composer. EHZ preserves its ROM-bug behavior
  (lines 222-223 left untouched in caller buffer) by copying only the
  written line range. Output is byte-identical to the prior loops; all
  zone-specific scroll tests (Ghz, Mz, Cpz, Cnz, Mcz, Ooz,
  TestScrollEffectComposer, ParallaxMczTest, TestS3kCnzBossScrollHandler,
  TestSonic3kCnzScroll, TestSwScrlHtzEarthquakeMode, plus the prior-migrated
  Aiz/Hcz/Mgz/Slots tests) still pass on the prior baseline. Remaining
  banded handlers (S2 `SwScrlOoz`, `SwScrlArz`, `SwScrlCnz`, `SwScrlDez`,
  `SwScrlWfz`) and complex handlers (S2 `SwScrlHtz`, S3K `SwScrlSlots`,
  `SwScrlGumball`) will be migrated in subsequent phases.
- **F2 phase 2: migrated trivial scroll handlers to `ScrollEffectComposer`.**
  The architectural review found `ScrollEffectComposer` was used by only 3 of
  8 S3K scroll handlers, and 0 of the 11 S2 + 7 S1 handlers. This commit
  migrates the seven handlers whose `update()` is a uniform/constant FG/BG
  parallax fill (no per-line VScroll, no waterline, no deform): S3K
  `SwScrlS3kDefault` and `SwScrlPachinko`; S2 `SwScrlMtz` and `SwScrlScz`; S1
  `SwScrlLz`, `SwScrlSbz`, and `SwScrlFz`. Each handler now drives a
  per-instance `ScrollEffectComposer` with `fillPackedScrollWords` /
  `setVscrollFactorBG`, and copies the composed buffer back into the caller's
  `horizScrollBuf` via `copyPackedScrollWordsTo`. Min/max scroll-offset
  tracking now flows through the composer's bounds rather than the legacy
  `trackOffset()` calls. Output bytes are byte-identical to the prior loops.
  Remaining S1/S2 banded handlers, S2/S3K complex handlers (HTZ, Slots,
  Gumball), and the S3K CNZ handler will follow in later phases.
- **S2 badnik child spawn safety: migrated direct `objectManager.addDynamicObject()`
  calls to `spawnFreeChild()` for `BalkiryBadnikInstance`, `AsteronBadnikInstance`,
  and `AquisBadnikInstance`.** Follow-up to the prior S1 badnik migration covering
  the three S2 badniks explicitly named alongside the S1 ones in the F1b
  architectural-fix review. Balkiry's jet-exhaust child, Asteron's
  explosion + 5-spike-projectile burst, and Aquis's bullet projectile were all
  calling `services().objectManager().addDynamicObject(child)` directly,
  bypassing `AbstractObjectInstance.CONSTRUCTION_CONTEXT`. Routing through
  `spawnFreeChild(Supplier)` sets the construction-time `ObjectServices`
  ThreadLocal before the child factory runs and preserves the ROM-equivalent
  `FindFreeObj` (low-slot) allocation semantics that the prior
  `addDynamicObject` path had. Slot ordering, child types, and spawn timing are
  byte-for-byte identical; the only behavioral difference is that child
  constructors may now safely call `services()`. The full S2 test suite stays
  on its prior baseline (4119 passed, 44 failed, 23 errors — pre-existing,
  unrelated `TestSonic1SBZEvents` etc. configuration failures, identical
  before and after the change). Other S1/S2 object instances still call
  `objectManager.addDynamicObject` directly (~63 S1 callers plus several S2
  badniks such as Octus/Slicer/Shellcracker/Sol/Turtloid) and will be migrated
  in subsequent passes; this commit covers only the badniks explicitly listed
  in the F1b architectural review.
- **S1 badnik child spawn safety: migrated direct `objectManager.addDynamicObject()`
  calls to `spawnFreeChild()` for `Sonic1BallHogBadnikInstance`,
  `Sonic1BombBadnikInstance`, `Sonic1CaterkillerBadnikInstance`, and
  `Sonic1OrbinautBadnikInstance`.** The four S1 badniks that ROM-spawn projectile,
  fuse/explosion/shrapnel, body-segment, or orbiting-spike children were calling
  `services().objectManager().addDynamicObject(child)` directly. That bypasses
  `AbstractObjectInstance.CONSTRUCTION_CONTEXT`, so any child constructor that
  invokes `services()` would throw `IllegalStateException` when reached through
  these spawn paths. Routing through the inherited `spawnFreeChild(Supplier)`
  helper sets the construction-time `ObjectServices` ThreadLocal before the child
  factory runs, preserves the ROM-equivalent `FindFreeObj` (low-slot) allocation
  semantics that the prior `addDynamicObject` call had, and keeps the existing
  `allocateSlotAfter` chains for Bomb/Caterkiller/Orbinaut intact (the helper is
  a no-op when a slot is already pre-assigned). Slot ordering, child types, and
  spawn timing are byte-for-byte the same; the only behavioral difference is that
  child constructors may now safely call `services()`. Per-object regression tests
  (`TestSonic1CaterkillerBodyChaining`, `TestSonic1LabyrinthObjectsBasic`) and the
  S1 trace replays (`TestS1Ghz1TraceReplay`, `TestS1Mz1TraceReplay`,
  `TestS1Credits00Ghz1TraceReplay`, `TestS1Credits06Sbz2TraceReplay`,
  `TestS1Credits07Ghz1bTraceReplay`) all stay green; the five pre-existing credits
  trace failures (Mz2/Syz3/Lz3/Slz3/Sbz1) are unchanged in count and first-error
  frame relative to the baseline. Other S1 object instances still call
  `objectManager.addDynamicObject` directly and will be migrated in subsequent
  passes; the listed badniks are the highest-risk subset because they are the
  ones explicitly named in the F1b architectural-fix task and the ones whose
  child types are most likely to gain `services()`-using constructors next.
- **S1 badnik/object subpixel math: migrated to shared `SubpixelMotion` helper.**
  ~17 Sonic 1 badnik and object instances each maintained their own private
  `xSubpixel` / `ySubpixel` int fields and reimplemented 16:8 (`<<8`) or 16.16
  (`<<16`) ROM fixed-point integration inline (`pos = (px << 8) | sub; pos +=
  vel; ...`). All occurrences in `game.sonic1.objects` (Crabmeat, Caterkiller
  head + body, Cannonball, Chopper, Motobug, Newtron, Roller, Yadrin, Orbinaut
  + spike, BallHog, Gargoyle fireball, GirderBlock, LavaBall, LavaGeyser,
  LavaWall, PushBlock) now consolidate the accumulators into a single
  `SubpixelMotion.State motion` field per class and call
  `SubpixelMotion.moveSprite` / `moveSprite2` / `moveX` / `speedToPos` /
  `speedToPosY` for the integration. Existing (sometimes ROM-divergent)
  semantics are preserved verbatim -- e.g. Cannonball/BallHog still apply
  gravity *before* the Y move via a manual pre-increment + `moveSprite2`,
  Gargoyle's fireball X-only path remains numerically identical for its
  `±$200` velocity, and PushBlock's slow-sink direct 16.16 add and per-axis
  velocity guards stay byte-for-byte the same. Pre-existing baseline test
  failures (S1 trace replays, S3K trace replays, etc.) are unchanged in count
  and first-error frame, confirming zero regression.
- **HCZ wall chase: migrated BG-high overlay render and active flag off
  `LevelManager` / `GameStateManager`.** The S3K-only inline render method
  `LevelManager.renderBgHighPriorityOverlay()` (and its caller in
  `LevelManager.draw()`) and the matching
  `GameStateManager.bgHighPriorityOverlayActive` field were a zone-specific
  leak in shared infrastructure -- the same architectural concern just fixed
  for HTZ in the previous commit. The overlay was extracted into a new
  `HczWallChaseBgOverlayEffect` (`com.openggf.game.sonic3k.render`) that
  registers itself at the `AFTER_SPRITES` stage from
  `Sonic3kZoneFeatureProvider`. The active flag storage moved into
  `Sonic3kHCZEvents.wallChaseBgOverlayActive` (now the canonical source for
  `HczZoneRuntimeState.wallChaseBgOverlayActive()`); a private
  `setWallChaseBgOverlayActive(boolean)` setter encapsulates the
  activation/deactivation transitions previously written through
  `gameState().setBgHighPriorityOverlayActive(...)`. The
  `bgHighPriorityOverlayActive` field plus its getter/setter on
  `GameStateManager` are gone. HCZ-specific reference counts in
  `LevelManager.java` and `GameStateManager.java` dropped to comments only
  (no runtime references).
- **HTZ earthquake: migrated BG-high overlay render and active flag off
  `LevelManager` / `GameStateManager`.** The HTZ-only inline render method
  `LevelManager.renderHtzEarthquakeBgHighOverlay()` (and its caller in
  `LevelManager.draw()`) and the matching `GameStateManager.htzScreenShakeActive`
  field were a long-standing zone-specific leak in shared infrastructure. The
  shared `SpecialRenderEffectRegistry` framework already exists for exactly
  this case (CNZ slot overlay, AIZ battleship, water surface), so the overlay
  was extracted into a new `HtzEarthquakeBgOverlayEffect`
  (`com.openggf.game.sonic2.render`) that registers itself at the
  `AFTER_FOREGROUND` stage from `Sonic2ZoneFeatureProvider`. The active flag
  storage moved into `Sonic2HTZEvents.earthquakeActive` (now the canonical
  source for `HtzRuntimeState.earthquakeActive()`); a new
  `setEarthquakeActive(boolean)` setter encapsulates the screen-shake-active
  + tilemap-invalidation side effects that previously lived in
  `ParallaxManager.setHtzScreenShake`. `ParallaxManager.setHtzScreenShake` and
  the `htzScreenShakeActive` getter/setter on `GameStateManager` are gone.
  `LevelTilemapManager` now consults a generic
  `ZoneRuntimeState.requiresFullWidthBgTilemap()` default method (overridden
  by `HtzRuntimeStateView`) instead of reading the HTZ flag from
  `GameStateManager`. Htz reference counts dropped from 7→0 in
  `LevelManager.java` and from 5→0 in `GameStateManager.java`.
- **WaterSystem: moved per-game visual oscillation behind `WaterDataProvider`.**
  `WaterSystem.getVisualWaterLevelY()` previously hard-coded
  `gameId == GameId.S2 && zoneId == ZONE_ID_CPZ` and `gameId == GameId.S1 &&
  (zoneId == LZ || (zoneId == SBZ && actId == 2))` branches in shared
  infrastructure -- a direct violation of the feature-flag/provider rule.
  Added a new `int getVisualWaterLevelOffset(int zoneId, int actId)` default
  method to `WaterDataProvider` (default returns 0). `Sonic2WaterDataProvider`
  overrides for CPZ to apply oscillator-0 bobbing centered at -8 (~ring
  height); `Sonic1WaterDataProvider` overrides for LZ and SBZ3 with the ROM's
  `oscillation >> 1` LZWaterFeatures.asm formula; S3K provider keeps the
  default 0 (no oscillation). `WaterSystem` now resolves the provider via
  `GameServices.module().getWaterDataProvider()` and adds the offset to the
  base level. `getGameId()` count in `WaterSystem` dropped from 1 to 0; the
  unused `GameId` and `OscillationManager` imports were removed.
- **PhysicsFeatureSet: replaced game-id branches in `LevelManager` with feature flags.**
  `LevelManager` had three branches that dispatched on game identity: two
  copies of `gameModule.getGameId() == GameId.S3K` to opt the S3K respawn-
  table latch in (line 917 in level load, line 4441 in act-transition
  rebind), and one `activeModule instanceof Sonic1GameModule` arm in
  `objectsExecuteAfterPlayerPhysics()` that bridged S1 onto the post-physics
  object-execution path (its collisionModel is UNIFIED, so the prior
  `DUAL_PATH || instanceof S1` test added S1 explicitly). Per CLAUDE.md's
  "never use game-name if/else chains -- always use feature flags" rule,
  promoted both to `PhysicsFeatureSet` fields: `permanentRespawnTableLatch`
  (true for S3K only, cite sonic3k.asm:20953 `bset #7,status(a1)` in
  `Touch_EnemyNormal`) and `objectsExecuteAfterPlayerPhysics` (true for S1/S2/S3K
  per the 2026-04-18-solid-ordering-rom-accuracy plan). `LevelManager` now
  reads both flags through `gameModule.getPhysicsProvider().getFeatureSet()`,
  the `Sonic1GameModule` import is gone, and the `getGameId()` count in
  `LevelManager` dropped from 3 to 1 (the one remaining use is in unrelated
  diagnostic logging). `CrossGameFeatureProvider` and
  `TestHybridPhysicsFeatureSet` propagate both new fields from the base
  game; `TestPhysicsProfile` adds regression cases asserting the per-game
  values.
- **GameServices: unified `hasRuntime()` predicate with `gameplayModeOrNull()`,
  migrated `bonusStage()` accessor off `RuntimeManager.getCurrent()`.**
  `GameServices.hasRuntime()` previously checked
  `RuntimeManager.getActiveRuntime() != null` while `gameplayModeOrNull()` and
  every `*OrNull()` accessor checked
  `SessionManager.getCurrentGameplayMode() != null && mode.getCamera() != null`.
  After `RuntimeManager.parkCurrent()`, those two predicates disagreed:
  `hasRuntime()` returned `false` while `gameplayModeOrNull()` could still
  return non-null (the gameplay mode lives on past the runtime in
  `SessionManager`). Code that guarded on `hasRuntime()` and then read via the
  `*OrNull()` accessors could read parked-context state. Unified
  `hasRuntime()` to delegate to `gameplayModeOrNull() != null` so both
  predicates always agree.
  Separately, `GameServices.bonusStage()` previously called
  `requireRuntime()` -> `RuntimeManager.getCurrent()`, which has a side
  effect: when the current runtime's `GameplayModeContext` no longer matches
  `SessionManager.getCurrentGameplayMode()` it calls `current.destroy()` and
  clears `current`. Calling `bonusStage()` during a mode transition could
  silently destroy a live runtime. Migrated the active bonus-stage provider
  field off `GameRuntime` onto `GameplayModeContext` (gameplay-scoped
  lifetime, transferred across `parkCurrent`/`resumeParked`); `GameRuntime.
  getActiveBonusStageProvider()` and `setActiveBonusStageProvider()` now
  delegate to the mode context for source compatibility. `GameServices.
  bonusStage()` and `bonusStageOrNull()` now resolve through
  `requireGameplayMode(...)` / `gameplayModeOrNull()` and never call
  `RuntimeManager.getCurrent()`. `requireRuntime(...)` is now unused inside
  `GameServices`, marked `@Deprecated`. New tests cover the predicate-
  equivalence invariant across no-runtime/active/parked/destroy transitions
  and verify that repeated `bonusStage()` calls do not destroy the active
  runtime. Architectural fix Task B1.
- **Engine: reset GL state before post-fade `CREDITS_DEMO` sprite pass.**
  In `Engine.display()`, the credits-demo branch that re-renders sprites
  on top of the fade overlay (`shouldRenderDemoSpritesOverFade()`) now
  invokes `GraphicsManager.resetForFixedFunction()` before the sprite
  pass. The fade shader binds a program and toggles blend/depth state,
  and although `FadeManager` restores blend on its own, the subsequent
  sprite pass should not inherit the fade pass's leftover shader/texture
  bindings. Architectural fix Task A3.
- **S1 credits-demo bootstrap: removed trace-derived starting-pose override.**
  `Sonic1CreditsDemoBootstrap.applyStartingPose` previously forced a
  per-demo `setAnimationId` (WALK for demo 0, WAIT for demos 1-7) and
  `setDirection(RIGHT)` whose values were derived from frame-zero trace
  recordings rather than from the ROM. This was a spec violation of the
  CLAUDE.md "Trace Replay Tests" comparison-only invariant — the bootstrap
  must be ROM-derived only. `applyStartingPose` is deleted entirely; the
  engine's natural post-spawn init and first `Sonic_Animate` pass now drive
  the frame-zero pose. The credits-demo tests retain the same 3-pass /
  5-fail profile (failures are pre-existing engine bugs at frame 221+, now
  documented under "Sonic 1 credits demo trace replay divergences" in
  `docs/KNOWN_DISCREPANCIES.md`). The class-level Javadoc citation in
  `Sonic1CreditsDemoBootstrap` was also incorrect (pointed at
  `Level_ChkDemo` at sonic.asm:2987-2990 — a timer/restart check, not the
  demo bootstrap); corrected to `EndingDemoLoad` (sonic.asm:3827) and
  `EndDemo_LampVar` (sonic.asm:3879). The same incorrect line numbers in
  `Sonic1CreditsDemoData` (4171/4176) are corrected in step.
- **Trace replay: hardened invariant guard and removed S1 credits-demo
  hydration.** `AbstractCreditsDemoTraceReplayTest.applyFrameZeroPlayerSnapshot`
  and `setupLzDemoState` previously read `TraceEvent.StateSnapshot.fields()`
  and `TraceFrame.rings()/cameraX()/cameraY()` on frame 0 and wrote ~10
  player/camera fields back into the engine — exactly the per-frame
  comparison-only invariant violation that CLAUDE.md "Trace Replay Tests"
  forbids. `TestTraceReplayInvariantGuard` did not catch this because its
  forbidden-string list missed the new patterns. The two debug probes
  (`DebugS1Credits03LzDoorProbe`, `DebugS1Credits05SbzJunctionProbe`)
  inherited the same anti-pattern. Replaced the hydration with a
  deterministic constants-only `Sonic1CreditsDemoBootstrap` helper that
  applies the LZ Act 3 lamppost state from `Sonic1CreditsDemoData`
  constants. (The starting-pose override added in this commit was itself a
  spec violation and has been removed in the follow-up bullet above.) The LZ ring count is set to 0 (matching
  ROM `Lamp_LoadInfo` in `_incObj/79 Lamppost.asm`, which loads
  `v_lamp_rings` then immediately clears `v_rings` to 0) instead of the
  `LZ_LAMP_RINGS=13` table value that ROM loads but never keeps. The guard
  now rejects: any `applyFrameZeroPlayerSnapshot(`/`applyCustomRadii(` call,
  any `fields.get("...")` snapshot read, any `frameZero != null` /
  `recordedRings = frameZero` / `recordedCamera...` local-variable binding
  that downstream-feeds engine setters, and a generic regex
  `\.set[A-Z]\w*\([^)]*\b(state|frame|snapshot|sn)\.\w+` that catches setter
  calls reading directly from a trace-side identifier. All 8 S1 credits demo
  trace replay tests retain their pre-existing pass/fail profile after the
  cleanup (3 pass, 5 fail on long-standing engine divergences unrelated to
  this task).
- **S3K: HCZ object art now ROM-only — eliminated `docs/skdisasm/` runtime
  reads.** `Sonic3kObjectArtProvider` previously parsed three HCZ object
  mapping tables (`Map_HCZMiniboss`, `Map_HCZEndBoss`, `Map_HCZWaterWall`) by
  reading `.asm` source files from `docs/skdisasm/Levels/HCZ/Misc Object
  Data/` at runtime via `Files.readAllLines`, violating the project's "ROM
  only for runtime assets" hard rule and silently degrading to invisible
  sprites whenever the disassembly tree was absent (CI / fresh clones). All
  three call sites now use `S3kSpriteDataLoader.loadMappingFrames` with
  ROM-verified table-base addresses
  (`MAP_HCZ_MINIBOSS_ADDR=0x3629E0`,
  `MAP_HCZ_END_BOSS_ADDR=0x3634D4`,
  `MAP_HCZ_WATERWALL_ADDR=0x22EE10`); the existing
  `MAP_HCZ_MINIBOSS_ADDR=0x362A28` constant was incorrect (pointed at the
  first frame body rather than the offset table) and is now corrected. The
  old asm-include parser, the duplicate-frame workaround for shared
  `Frame_362BB0` labels (no longer needed because ROM-based reading of
  duplicate offsets yields duplicate frames naturally), and the three `Path`
  constants under `docs/` are removed.
- **Runtime ownership migration: GameServices decoupled from GameRuntime
  façade.** `GameServices` now resolves all gameplay-scoped manager accessors
  through `SessionManager.getCurrentGameplayMode()` directly rather than via
  `RuntimeManager.getCurrent()`/`GameRuntime.getX()`. Migrated ~58 mechanical
  call sites across 27 files (engine top-level, level/sprite/graphics, S2/S3K
  game-specific, plus tests) from `RuntimeManager.getCurrent().getX()` and
  `runtime.getX()` patterns to the appropriate `GameServices.X()` accessors.
  After the change, the `GameRuntime` façade still exists as a lifecycle
  handle but is no longer load-bearing for production gameplay code; the only
  remaining `GameRuntime` references are foundational (constructor parameters
  for `DefaultObjectServices`/`RuntimeSaveContext`, the
  `TraceReplayFixture.runtime()` interface contract, lifecycle methods on
  `RuntimeManager`, and tests that legitimately exercise runtime instance
  identity). Tests that asserted "post-`destroyCurrent` GameServices throws"
  were updated to also call `SessionManager.clear()` since the new lifecycle
  is "destroy runtime → managers reset, but gameplay-mode context still
  alive; clear session → gameplay-mode context gone, GameServices throws".
- **Runtime ownership migration: gameplay state split by lifetime.**
  Per `docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md`,
  the design's load-bearing split is now in place: `WorldSession` owns the
  durable world data (active `GameModule`, the loaded `Level` including its
  `MutableLevel` layout, and zone/act metadata); `GameplayModeContext` owns
  the disposable gameplay-scoped managers (Camera, Timer, GameState, Fade,
  Rng, SolidExecution, Water, Parallax, TerrainCollision, Collision, Sprite,
  LevelManager) and the runtime-shared registries (`ZoneRuntimeRegistry`,
  `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`,
  `SpecialRenderEffectRegistry`, `AdvancedRenderModeController`,
  `ZoneLayoutMutationPipeline`). `GameRuntime` is now a thin coordinator
  façade whose getters delegate to the gameplay mode context — its 18
  manager fields are gone. `LevelManager` keeps a write-through cache for
  zone/act/level reads but `WorldSession` is the source of truth, so a
  freshly-constructed `LevelManager` after editor exit re-inherits the
  loaded level automatically. `GameplayModeContext.initializeFreshGameplayState()`
  resets the design's "non-preserved" counters (score, timer, checkpoint)
  on editor exit. New tests
  `editorRoundTrip_preservesWorldSessionAndResetsGameplayCounters` and
  `editorRoundTrip_preservesMutableLevelMutations` verify the editor enter/exit
  round trip preserves world data + a `MutableLevel` mutation while resetting
  session counters. `LevelManager.restoreInheritedLevel()` is added as a
  building block for the future "drop `RuntimeManager.parkCurrent`" cleanup.
  The empty `EngineContext` stub is removed (its role is already played by
  `EngineServices`). Eliminating the `GameRuntime` façade entirely (51 file
  refs) and replacing the parking flow with direct teardown remain mechanical
  follow-ups, not blocking.
- **Force-snap camera centres on `sprite_x - 160`, matching ROM.**
  `Camera.updatePosition(force=true)` previously placed the camera at
  `sprite.getCentreX() - 152` (the midpoint of the 144-160 horizontal
  scroll deadzone). The ROM's level-load routine (s1disasm
  `_inc/LevelSizeLoad & BgScrollSpeed.asm:111`, s2.asm:14787,
  sonic3k.asm:38241) snaps to `MainCharacter.x_pos - $A0` (160) — the
  right edge of the deadzone — before clamping to level bounds. The
  off-by-8 error showed up as a +8 px engine `camera_x` at frame 0 in
  six S1 credits demo trace replays (Mz2, Syz3, Slz3, Sbz1, Sbz2,
  Ghz1b) plus S3K MGZ, but was hidden whenever the snap was clamped to
  the left boundary (S1 GHZ1, S1 MZ1, S2 EHZ1, S3K AIZ all start
  near `x=0` so the clamp masked the bug). With the formula corrected,
  S1 Credits06 Sbz2 and S1 Credits07 Ghz1b pass cleanly; the remaining
  S1 credits demos and S3K MGZ no longer report `camera_x` as the first
  error and instead surface downstream parity issues for follow-up.
- **Trace replay now validates camera position pixel-for-pixel.** The
  BizHawk trace recorders (`tools/bizhawk/s{1,2,3k}_trace_recorder.lua`)
  already capture ROM `Camera_X_pos` / `Camera_Y_pos` each frame, but
  `TraceBinder` only displayed them as diagnostic context — divergent
  engine camera scrolling was silently ignored. `TraceBinder.compareFrame`
  now produces `camera_x` / `camera_y` field comparisons whenever both
  ROM trace and engine diagnostics recorded coordinates, with both sides
  masked `& 0xFFFF` to align ROM's u16 representation with the engine
  `Camera.getX()/getY()` short return value across the sign boundary.
  `ToleranceConfig` gains `cameraWarn` / `cameraError` (default 1/1, so
  any mismatch is an ERROR) and a `withCameraTolerances(warn, error)`
  opt-out for explicit per-test relaxation; the default is unchanged
  pixel-perfect. `EngineDiagnostics` now stores `cameraY` alongside
  `cameraX` and exposes a `formattedWithCamera(x, y, text)` factory so
  `AbstractTraceReplayTest`'s precollapsed-context wrapper retains
  numerics for comparison. The new comparator path enabled `S2 EHZ1`
  trace replay end-to-end and surfaced previously-hidden S3K camera
  divergences (AIZ/CNZ/MGZ/HCZ replay tests now show `camera_x` or
  `camera_y` deltas at specific frames, e.g. AIZ1 frame 289 reports
  `camera_y` expected `0x0396`, actual `0x0390`) for follow-up triage.
- **S2 EHZ trace replay — Tails frame 3644 slope-resist parity fix.**
  Engine `doSlopeResist` previously applied slope force to all games
  whenever `g_speed == 0` and `|slope_force| >= 0x0D`, mirroring S3K
  `Player_SlopeResist` (sonic3k.asm:23830-23856) which branches to
  `loc_11DDC` when stationary and applies the force conditionally.
  S1/S2 ROM (s1disasm/_incObj/01 Sonic.asm:1243-1244;
  s2.asm:37394-37395, 40249-40250) instead returns unconditionally on
  `tst.w inertia(a0) / beq.s` — a stationary S1/S2 player on a steep
  slope stays put. Gated the at-rest kick behind a new
  `PhysicsFeatureSet.slopeResistAppliesAtZeroInertia` flag (true for
  S3K, false for S1/S2). `TestS2Ehz1TraceReplay.replayMatchesTrace`
  goes from 26 errors at frame 3644 (Tails decelerated to `g_speed=0`
  on angle 0xD0, ROM kept her stationary while engine slid her back
  down the loop) to a full pass; S3K trace replays unaffected (S3K
  flag is `true`, behaviour unchanged).
- **Trace Test Mode — pause-time camera focus visualiser.** While
  paused during a live trace session, the user can now cycle the
  camera between up to five focus targets using the configured P1
  LEFT/RIGHT keys: `Default` (the camera position at pause entry),
  `Sidekick (Eng)` / `Sidekick (Trace)` (centred on the engine's
  first sidekick or the recorded ROM-trace sidekick position), and
  `Main (Eng)` / `Main (Trace)` (centred on the engine's main
  playable sprite or the trace's recorded position). Trace variants
  are skipped when their position equals the engine's; sidekick
  options are skipped when no engine sidekick is spawned; main
  options are skipped when the main player is despawned. The active
  focus is shown in the top-right HUD as `Camera: <Mode>` with a
  `<- -> Cycle Cameras` hint. On unpause, the camera snaps back to
  its pre-pause position; gameplay determinism is preserved across
  frame-step (camera is restored before the step runs and re-applied
  after). The controller mutates only `Camera.setX/setY` — it never
  calls `updatePosition` or any other manager update path, so no
  object placement, parallax, or trace-recording state is disturbed.
- **CNZ Trace F8123 — CNZ bumper misses sidekick Tails touch at
  pixel-edge overlap (diagnosis only).** After the F7923 Clamer cprop
  fix landed, the next CNZ first error is at F8123 (2683 errors). Tails
  is following Sonic in `Tails_CPU_routine=6` (`loc_13D4A`,
  sonic3k.asm:26656), airborne+rolling (`status=0x07`) at
  `(x_pos=0x0F05, y_pos=0x0472)` with `(x_vel=0x00D7, y_vel=0x0268)`.
  The CNZ stationary bumper at slot 14 (`object_code=0x00032EAA =
  loc_32EAA`, sonic3k.asm:68850-68886) sits at `(0x0F00, 0x0488)` with
  `width_pixels=$10, height_pixels=$10`, so its top edge is exactly at
  Tails' bottom edge (`y=0x0480`). ROM treats this exact-edge contact
  as a hit, runs `sub_32F56` (sonic3k.asm:68950-68992):
  `x_vel = sin(arctan(bumper-player)+frame&3) × -$700 / 256` and
  `y_vel = cos(...) × -$700 / 256`, plus `bset Status_InAir`,
  `bclr Status_RollJump`, `bclr Status_Push`, `clr.b jumping`, then
  spawns `Obj_EnemyScore` (`loc_2CD0C`, sonic3k.asm:61375). Three
  evidence lines from `aux_state.jsonl` confirm this is the path:
  (1) an `object_appeared` event at F8123 for slot 7 with
  `object_type=0x0002CCE0` (`Obj_EnemyScore`) at the bumper's
  `(0x0F00, 0x0488)`; (2) the next `Obj_EnemyScore` spawn at F8150 (27
  frames later, the orbit-period gap) confirming the bumper as the
  spawn source; (3) ROM `tails_x_speed` jumps `0x00D7 → 0x0230`,
  `tails_y_speed` jumps `0x0268 → -0x06A5` -- discontinuous changes
  incompatible with `Tails_InputAcceleration_Freespace` drag plus
  `MoveSprite_TestGravity` air physics, but consistent with the bumper
  full sin/cos/-$700 reseed. Engine `tails_x_speed` ends at `0x00BF`
  (`= 0x00D7 - 0x18` air drag) and `tails_y_speed` at `0x02A0`
  (`= 0x0268 + 0x38` gravity), i.e. the sidekick's frame-end state is
  just the airborne-roll physics with no bumper bounce applied. The
  divergence is upstream of `applyBounce` (which is sin/cos/-$700
  correct) -- the engine's per-frame near-object scan window for the
  sidekick appears to drop the stationary CNZ bumper before the touch
  test runs (Sonic at `(0x0DE5, 0x0309)` is >600px from the bumper
  while Tails' AABB edge overlaps it). Documented in
  `docs/S3K_KNOWN_BUGS.md` with three engine-side fix candidates.
  CNZ first-error stable at F8123 this round. AIZ first-error at
  F8927 unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.
- **AIZ Trace F8927 — diagnosis-only entry for the next first
  trace error after the F7660 swing-bounce fix landed.** Trace
  shows Sonic rolling+airborne (`status=0x06`,
  `status_secondary=0x11` Fire Shield) descending into the AIZ2
  boss-arena entrance with `x_speed` capped at `0x0179` from F8923
  through F8926; at F8927 the ROM zeroes `x_speed` and freezes
  `x_pos` at `0x1208`, while the engine retains `0x0179` and drifts
  ahead, producing 896 cascading errors over the next ~340 frames
  including a phantom land at F8942. ROM frames F8931 onward show
  the canonical "rolling-air sliding into a flush right-side wall"
  signature: `x_speed` cycles 0 → 0x18 → 0x30 → 0x48 → 0x60 → 0
  every 5 frames with a sub-x snap pushback, matching
  `SonicKnux_DoLevelCollision`'s `CheckRightWallDist` arm
  (sonic3k.asm:24061-24065 -- "stop Sonic since he hit a wall"
  `move.w #0,x_vel(a0)`). The engine never observes that wall
  hit. Three candidate root causes (missing terrain solid bit at
  the boss-arena right wall, quadrant-routing skip in our
  `DoLevelCollision` equivalent, or an x_radius-vs-fixed-`+10`
  probe-offset mismatch matching the player path's
  `addi.w #$A,d3` at sonic3k.asm:20195) are documented; the most
  testable is the probe-offset hypothesis since rolling drops
  `x_radius` from 9 to 7. No engine change in this round; only a
  documented diagnosis. AIZ first-error stays at F8927 (errors
  896). CNZ first-error at F7923 unchanged. S1 GHZ / S1 MZ1 / S2
  EHZ trace replays remain GREEN.
- **CNZ1 Trace F7923 — Clamer latched-cprop fired on wrong player
  (FIXED).** ROM `Touch_Special.loc_103FA` (sonic3k.asm:21186-21194)
  accumulates per-touch into the spring-child's
  `collision_property(a1)` byte with a player-identity-dependent
  increment: `+1` for Player_1 (Sonic), `+2` for Player_2 (sidekick
  Tails). `Check_PlayerCollision` (sonic3k.asm:179904-179924) then
  masks `& 3` and indexes `word_85890 = [P1, P1, P2, P2]` to pick the
  launch target before clearing the byte. The engine's
  `ClamerObjectInstance` was collapsing this to a single boolean
  `springCprop`, so when the post-cooldown latch fired the engine
  always launched the primary `playerEntity` passed into `update()`
  (Sonic) instead of resolving the byte to the actual toucher. At
  F7923 the engine launched Sonic into the air with the spring's
  triplicate `-0x0800` write while ROM had Sonic still on the ground
  and was re-firing the same spring on Tails. Replaced the boolean
  with the ROM cprop byte; `onTouchResponse` increments by `+1` for
  primary, `+2` for `playerEntity.isCpuControlled()` (Tails), and the
  two latch-fire branches in `advanceSpringRoutine` resolve the
  target via `cprop & 3` (`1 → primary`, `2 or 3 → first sidekick
  from services().sidekicks()`). Cprop is cleared on consumption to
  mirror `clr.b collision_property(a0)`. CNZ first-error advances
  F7923 -> F8123 (2767 -> 2683 errors); AIZ first-error stable at
  F8927; S1/S2 trace replays unaffected; `TestClamerObjectInstance`
  GREEN.
- **AIZ Mini-boss F7660 — `Swing_UpAndDown` peak bounce-back ROM
  parity restored.** ROM `Swing_UpAndDown` (sonic3k.asm:177851-177879)
  applies a bounce-back at the swing apex: when the velocity reaches
  `±maxSpeed`, the routine flips direction (`bset/bclr #0,$38(a0)`),
  negates `d0`, and falls into `loc_84812` which adds the now-opposite
  `d0` back to `d1` in the same frame, so the stored peak velocity is
  `±maxSpeed ∓ accel`, not the clamped extreme. The engine's
  `AizMinibossSwingMotion.update()` was clamping the peak to
  `±maxSpeed` (skipping the `loc_84812` step), so the swing apex held
  the extreme velocity for one extra frame each half-cycle and the
  swing drifted ~6 frames out of phase with ROM by trace F7660.
  With the drifted swing the engine's miniboss y was 3 units low
  vs ROM at F7660, which let the engine see the boss/Sonic AABB
  overlap one frame ahead of ROM. ROM boss `Touch_ChkHurt`
  (sonic3k.asm:20911-20915) negates `x_vel`, `y_vel`, and
  `ground_vel` on a boss hit, so the ahead-by-one-frame detection
  flipped Sonic's `g_speed`/`x_speed`/`y_speed` signs at F7660 in
  the engine while ROM still showed them positive (ROM bounced at
  F7661). Engine now applies the ROM bounce-back step
  (`vel += accel` at the up peak, `vel -= accel` at the down peak)
  so the swing apex matches ROM cycle-for-cycle. AIZ first-error
  advances 7660 → 8927 (errors 975 → 896). CNZ first-error at F7923
  unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.
- **AIZ Mini-boss F7552 — sidekick hurt-airborne boundary clamp now
  matches ROM order (MOVE before BOUNDARY).** ROM `Obj01_Hurt`
  (s2.asm:37820-37834), `Sonic_Hurt` (s1disasm/_incObj/01
  Sonic.asm:1791-1804), and S3K `loc_122D8`/`loc_156D6`
  (sonic3k.asm:24449-24467, 29194-29209) all run
  `MoveSprite_TestGravity2`/`ObjectMove`/`SpeedToPos` BEFORE
  `Sonic_LevelBound`/`Tails_Check_Screen_Boundaries` for routine 4
  (hurt). The engine's `PlayableSpriteMovement.modeAirborne` ran the
  boundary check pre-move for both normal and hurt airborne paths,
  which lost one frame of lateral motion against
  `Camera_max_X_pos+$128` during hurt knockback. AIZ Mini-boss F7552
  trace expected `tails_x=0x1208, tails_x_speed=0x0000` and engine
  produced `tails_x=0x1207, tails_x_speed=0x0200` (off-by-one px,
  one frame behind on the right-edge clamp). Engine now reorders
  the hurt airborne path: `doObjectMoveAndFall` → underwater
  gravity reduction → `updateSensors` → `doLevelCollision`
  (Sonic_HurtStop equivalent) → `doLevelBoundary`. AIZ first-error
  advances 7552 → 7660 (errors 977 → 975). CNZ first-error at F7919
  unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.
- **CNZ F=621 Clamer re-fire — Touch_Special cprop latch landed (round 4).**
  `ClamerObjectInstance` now models the ROM spring child's `(a0) =
  loc_890AA -> loc_890C8 -> loc_890D0 -> loc_890AA` cycle (sonic3k.asm:185953-185973)
  with a three-state machine (LIVE / COOLDOWN_DRAIN / COOLDOWN_DONE)
  plus a `springCprop` boolean mirroring `collision_property(a0)`
  (sonic3k.asm:21162-21194). Touch on a cooldown frame latches the
  cprop byte; the next non-cooldown spring update consumes it and
  fires (matches the ROM F=619/F=621 fire schedule recorded in the
  v6.15-s3k CNZ aux events). Spring rect uses ROM-correct cflags
  `$D7` (`$40 | $17`, 8x8) at all times -- the engine-only
  `SPRING_RELATCH_COLLISION_FLAGS = $40 | $12` widening and the
  `springReenableFrame` mechanism are removed. Adds
  `usesS3kTouchSpecialPropertyResponse()` override so the engine
  decoder routes `cflags=$D7` through SPECIAL via the
  Touch_Special property index list (sonic3k.asm:21165-21194),
  consistent with `CnzBalloonInstance`. `TestS3kCnzTraceReplay`
  first error advances F7919 -> F7923; F=619-625 zero errors.
  `TestS3kAizTraceReplay` first error stable at F7552. S1 GHZ /
  S1 MZ1 / S2 EHZ trace replays GREEN. `TestClamerObjectInstance`
  6/6 GREEN.

- **Trace visualizer ghost characters.** Test-mode visual trace sessions now
  render grayscale, distance-faded ghost copies of the traced main character
  and first sidekick during desyncs. Ghosts hydrate only render state from the
  trace, keep isolated sidekick-style DPLC banks so their animation/art state
  cannot corrupt real players or sidekicks, share the mirrored character's
  render bucket and tile-priority layer, and draw behind the live characters.

- **CNZ F=621 Clamer re-fire — ROM dispatch path narrowed (doc-only, round 2).**
  ROM-side trace established that `Check_PlayerCollision` (sonic3k.asm:179904-179916)
  consumes `collision_property(a0)` written by `Touch_Special` (sonic3k.asm:21162-21194)
  — not a geometric overlap test — and the spring child only re-adds itself to
  `Collision_response_list` in `loc_890AA` (not the `loc_890C8` cooldown), yet the trace
  still records ROM firing at F=621. Localising the F=621 mechanism needs a recorder
  extension to capture per-frame `Collision_response_list` membership and each object's
  `collision_property` at `TouchResponse` time. No code change; probes reverted; baselines
  preserved (CNZ F7919/2757, AIZ F7552/977, S1/S2 PASS). Comparison-only invariant preserved.
  (Superseded by the round-4 cprop-latch fix above.)
- **S3K AIZ F7552 round-4 audit — divergence isolated to boundary clamp + wall-push pair (doc-only).**
  The regenerated v6.13-s3k AIZ fixture's `terrain_wall_sensor_per_frame` events show ROM advancing
  Tails `0x1207 -> 0x1208` via two writes: a `Tails_Check_Screen_Boundaries` `loc_14F5C` boundary
  clamp, then a `Tails_DoLevelCollision` `+1` wall push (using `add.w/sub.w`, so it never appears in
  `position_write` events). Engine fires neither because `doLevelBoundary()` reads
  `camera.getMaxX() = 0x4640` (raw `LevelSizes.AIZ2 xend`) while ROM's effective `Camera_max_X_pos`
  is `~0x10DF` (AIZ Mini-boss arena right edge). The fix is an AIZ2 miniboss camera max-X lock in
  `Sonic3kAIZEvents.updateAiz2SonicResize2`; confirming the exact value needs the recorder's
  `aiz_boundary_state_per_frame` extended to F7549-F7560. No engine code or trace fixture change;
  baselines stable (AIZ F7552/977, CNZ F7919). Comparison-only invariant preserved.
