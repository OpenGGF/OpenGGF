# S3K reverse gravity: every ROM reference and its engine consumer

Written 2026-09-17 at `9cba6dbb6` for the
[S3K DEZ bring-up plan](../../plans/2026-09-17-s3k-dez-bring-up.md) (slice 2 and its consumers).
Disassembly submodule `1a454a0e335137a1a016d1090a9f4528d36944cf`; line numbers are
`docs/skdisasm/sonic3k.asm` lines at that revision, `loc_`/`sub_` names are ROM addresses.

Count: `grep -c 'Reverse_gravity_flag' docs/skdisasm/sonic3k.asm` = **116** (each line holds one
reference; the definition in `sonic3k.constants.asm:684` is not counted). Engine search:
`grep -rniE 'reverseGravity|reverse_gravity' src/main/java` plus a search by role (classes that
negate a player Y adjustment, swap floor/ceiling probes or mirror the player sprite). "Engine
consumer" is what exists today; "new" means the owning object does not exist yet.

## The model the ROM uses (read this before coding)

- **The flag is one global byte, `Reverse_gravity_flag` at `$FFFFF7C6`.** (`$F768` is
  `Primary_Angle`; the `$FFFFF768` in `GameStateManager`'s Javadoc and in the first draft of the
  plan is wrong. Fix the comment when slice 2 touches that file.) It is not per player: Player 2
  inverts with Player 1, and **every writer acts for Player 1 only**: `$58` tests `d6 & $14`
  (Player 1's top/bottom contact bits; Player 2's `$28` only release riders in `sub_48B40`), `$5B`
  calls `sub_49228` for `Player_1` alone (asm 95486-95487), `$59` has `cmpa.w #Player_1,a1`
  (`loc_48DCA`). Player 2 can never change gravity.
- **Velocity is not inverted; position integration is.** `MoveSprite_TestGravity` still adds `$38`
  to `y_vel` and then adds `-y_vel` to `y_pos` (sonic3k.asm:36069-36083). Positive `y_vel` always
  means "falling", toward whichever surface is the floor. Every other branch follows from this:
  Y *position* adjustments are negated, floor and ceiling probes swap (`sub_11FD6`/`sub_11FEE`),
  the terrain angle is mirrored with `+$40, neg, -$40` around the probe, the death plane moves to
  the top of the level, and sprites are drawn with `render_flags` bit 1 toggled. One exception: the
  act 2 boss (`sub_7F8A0`, asm 170322-170341) negates its own **acceleration** (`-$38`) and
  integrates normally; it does not use `MoveSprite_TestGravity`.
- **The ROM never checks the zone.** Any level with the flag set behaves this way (the debug cheat
  proves it). The engine gate is therefore the flag itself, read through
  `GameStateManager.isReverseGravityActive()`. **Do not add a `GameRules` capability:** `GameRules`
  and its nested rule records are `@com.openggf.game.ModApi` (fully qualified spelling, which the
  pin hook does not catch at commit time) and a new component breaks the 0.7 signature pin. S1 and
  S2 never write the flag, so their behaviour cannot change; the non-regression gate proves it.
- **Writers** (all others only read): the three debug toggles, `Obj_DEZGravitySwitch` (`$58`),
  `Obj_DEZTeleporter` (`$59`), `Obj_DEZGravitySwap` (`$5B`) and the act 2 boss's clearer object
  `loc_7FC3E`. That is not an exit hook: `loc_7FBD6` (boss **defeat**, asm 170711-170720) allocates a
  separate object whose whole routine is `clr.b (Reverse_gravity_flag).w; rts`. It never deletes
  itself, so it clears the flag every frame from defeat until the `$1700` load; a `$58`/`$5B` write
  after defeat is undone on that object's next update (slot order decides same-frame visibility).
  `Obj_DEZGravityTube` (`$5A`) only reads it. `Obj_DEZGravityHub` (`$5C`), `Obj_DEZGravityRoom`
  (`$5F`) and `Obj_DEZGravityPuzzle` (`$61`) contain **no** reference: they move the player with
  `object_control`, and any gravity change near them comes from a `$58`/`$5B` placed alongside.
- **Clears.** By name the flag is zeroed by `loc_7FC3E` and by `$5B` (`move.b #0` at 95511 and 95536,
  before its conditional set). Level load clears it because
  `clearRAM Tails_CPU_interact,$100` (`$F700-$F7FF`, sonic3k.asm:7621) covers `$F7C6`; that runs for
  a normal load, a death restart and `StartNewLevel` (the same wipe also runs in `Title_Screen`,
  asm 5415). It does **not** run for the seamless
  `$B00` → `$B01` change (`loc_593EC` calls `Load_Level`/`LoadSolids` only), so the flag survives
  the act change. Death itself does not clear it: the dying player keeps falling "up" until the
  reload. **Engine gap:** `GameStateManager` clears the field only in `resetSession()` (line 252),
  not in `resetForLevel()` (line 275), so today a death or level change with the flag set would
  carry it into the next load. The field is snapshotted for rewind. Slice 2 adds the level-load
  clear next to the other RAM-wipe fields in `resetForLevel()`, citing `clearRAM
  Tails_CPU_interact,$100`, after checking its two callers: `LevelManager:991` (level load) and
  `LevelActTransitionExecutor:117` (act transition). If the seamless `$B00` → `$B01` path goes
  through the second, the clear must not run there. First failing tests: death with the flag
  set respawns upright; `$B00` → `$B01` seamless change keeps the flag; `StartNewLevel` clears it.
- **One shipped bug to preserve.** `Tails_Test_For_Flight` (`loc_1515C`, line 28655) negates `d0`
  but then adds `d1`: Tails' unroll adjustment when he starts flying is *not* inverted. Model
  `FixBugs = 0`, comment the branch, and do not "fix" it.
- **Indirect consumers the grep cannot show.** `ChkFloorEdge_ReverseGravity`,
  `RingCheckFloorDist_ReverseGravity` and `Obj_Bouncing_Ring_Reverse_Gravity` are selected by the
  branches below and contain no flag test of their own. The wrappers `sub_11FD6` (10 callers),
  `sub_11FEE` (9), `ChooseChkFloorEdge` (7), `Call_Player_AnglePos` (10),
  `MoveSprite_TestGravity` (9) and `MoveSprite_TestGravity2` (16) spread the behaviour to every
  caller: port the wrapper once and route every engine equivalent of those callers through it.

## References by owner

### A. Shared integration and sensor wrappers

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 19696 | `loc_F638` | `sub_F61C` (CalcRoomInFront): negates the projected `y_vel` before the wall probe | — | missing |
| 22330 | `Call_Player_AnglePos` | `Call_Player_AnglePos`: mirrors `angle` (`+$40, neg, -$40`) around `Player_AnglePos`, which then uses ceiling sensors | — | missing |
| 23191 | `Player_Boundary_CheckBottom` | `Player_Boundary_CheckBottom`: death plane becomes the **top** (`loc_11722`) | — | missing |
| 24128 | `sub_11FD6` | `sub_11FD6`: floor check becomes `Sonic_CheckCeiling` with mirrored angle (10 callers) | — | missing |
| 24142 | `sub_11FEE` | `sub_11FEE`: ceiling check becomes `Sonic_CheckFloor` with mirrored angle (9 callers) | — | missing |
| 24156 | `ChooseChkFloorEdge` | `ChooseChkFloorEdge`: selects `ChkFloorEdge_ReverseGravity` (7 callers: balance, glide, climb) | `GlideWallGrabTerrain.align` only | partial |
| 36069 | `MoveSprite_TestGravity` | `MoveSprite_TestGravity`: `y_vel += $38` as usual, **position** integrates `-y_vel` (9 callers) | — | missing |
| 36089 | `MoveSprite_TestGravity2` | `MoveSprite_TestGravity2`: position integrates `-y_vel` (16 callers) | — (`SidekickCpuController:4991` documents the clear-flag case) | missing |

### B. Sonic (and Sonic/Knuckles shared) routines

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 21952 | `Sonic_Control` | `Sonic_Control` debug cheat: button A toggles the flag (`Debug_mode_flag` only) | — (tests use the `GameStateManager` setter) | n/a |
| 22011 | `loc_10C62` | `loc_10C62`: `eori.b #2,render_flags` after `Animate_Sonic` (vertical mirror) | — | missing |
| 22623 | `loc_11276` | look-down camera bias: moves the opposite way **and** the limit changes from 8 to `$D8` (`loc_112A6`) | — | missing |
| 22646 | `loc_112B0` | look-up camera bias: moves the opposite way **and** the limit changes from `$C8` to `$18` (`loc_112E0`) | — | missing |
| 22988 | `loc_11578` | `Sonic_RollSpeed` unroll: negates the radius Y adjustment | — | missing |
| 23265 | `Player_DoRoll` | `Player_DoRoll`: `+5` becomes `-5` (`subi.w #2*5`) | — | missing |
| 23294 | `Sonic_Jump` | `Sonic_Jump`: mirrors the launch angle | — | missing |
| 23346 | `loc_1182E` | `Sonic_Jump` (`loc_1182E`): negates the roll-radius Y adjustment | — | missing |
| 23694 | `loc_11C5E` | `SonicKnux_Spindash` release: `+5` becomes `-5` | — | missing |
| 24081 | `loc_11F6E` | `SonicKnux_DoLevelCollision` `loc_11F6E`: negates the floor snap distance | — | missing |
| 24178 | `Player_HitCeiling` | level collision, ceiling hit: negates the push-out distance | — | missing |
| 24213 | `loc_12074` | level collision (`loc_12074`): floor landing through `sub_11FD6`; negates the snap distance, then sets `angle` and zeroes `y_vel` | — | missing |
| 24246 | `loc_120C2` | level collision (`loc_120C2`): negates the push-out distance | — | missing |
| 24284 | `loc_1211A` | level collision (`loc_1211A`): negates the push-out distance | — | missing |
| 24308 | `loc_12148` | level collision (`loc_12148`): negates the push-out distance | — | missing |
| 24350 | `Player_TouchFloor` | `Player_TouchFloor`: negates the roll-clear radius Y adjustment | `PlayableHurtRadiusTransition:32` | partial |
| 24426 | `loc_12246` | `BubbleShield_Bounce` (`loc_12246`): negates the radius Y adjustment | — | missing |
| 24475 | `sub_12318` | `sub_12318` (hurt): death-plane test flips to the top | — | missing |
| 24552 | `loc_123DE` | `loc_123DE` (dead/respawn): off-screen test uses `Camera_Y - $10` going up | — | missing |
| 24716 | `sub_125E0` | `sub_125E0` (hurt/dead animate): vertical mirror | — | missing |

### C. Tails routines

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 26166 | `Tails_Control` | `Tails_Control` debug cheat toggle | — | n/a |
| 26255 | `loc_138C8` | `loc_138C8`: vertical mirror after `Animate_Tails` | — | missing |
| 27868 | `loc_14AA0` | look-down camera bias: moves the opposite way **and** the limit changes from 8 to `$D8` (`loc_14AA0`) | — | missing |
| 27891 | `loc_14ADA` | look-up camera bias: moves the opposite way **and** the limit changes from `$C8` to `$18` (`loc_14ADA`) | — | missing |
| 28233 | `loc_14DA2` | `Tails_RollSpeed` unroll: negates the radius Y adjustment | — | missing |
| 28426 | `loc_14F30` | `Tails_Check_Screen_Boundaries` (`loc_14F30`): death plane at the top | — | missing |
| 28500 | `loc_14FC4` | `Tails_Roll` (`loc_14FC4`): `+1` becomes `-1` (`subq.w #2`) | — | missing |
| 28525 | `Tails_Jump` | `Tails_Jump`: mirrors the launch angle | — | missing |
| 28572 | `loc_1504C` | `Tails_Jump` (`loc_1504C`): negates the roll-radius Y adjustment | — | missing |
| 28655 | `loc_1515C` | `Tails_Test_For_Flight` (`loc_1515C`): **shipped bug** — `neg.w d0` negates the wrong register, so the unroll adjustment in `d1` is *not* inverted. Model `FixBugs = 0`: no inversion | — | missing |
| 28748 | `loc_1527C` | `Tails_Spindash` release: `+1` becomes `-1` (`subq.w #2`) | — | missing |
| 28917 | `loc_15444` | `Tails_DoLevelCollision` `loc_15444`: negates the floor snap | — | missing |
| 28974 | `loc_154C4` | `Tails_DoLevelCollision` `loc_154C4`: negates the push-out | — | missing |
| 29009 | `loc_1550E` | `Tails_DoLevelCollision` `loc_1550E`: negates the push-out | — | missing |
| 29042 | `loc_1555C` | `Tails_DoLevelCollision` `loc_1555C`: negates the push-out | — | missing |
| 29080 | `loc_155B4` | `Tails_DoLevelCollision` `loc_155B4`: negates the push-out | — | missing |
| 29104 | `loc_155E2` | `Tails_DoLevelCollision` `loc_155E2`: negates the push-out | — | missing |
| 29143 | `Tails_TouchFloor` | `Tails_TouchFloor`: negates the roll-clear radius Y adjustment | `PlayableHurtRadiusTransition:32` (verify it runs for Tails) | partial |
| 29220 | `sub_15716` | `sub_15716` (hurt): death-plane test flips to the top | — | missing |
| 29336 | `sub_15842` | `sub_15842` (hurt/dead animate): vertical mirror | — | missing |
| 29594 | `loc_15A7A` | `loc_15A7A` (`Animate_Tails` rotation frames): vertical mirror applied inside the animator | — | missing |

### D. Tails CPU, flight catch-up and carry

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 26495 | `loc_13B50` | `Tails_Catch_Up_Flying` (`loc_13B50`): respawn Y is `target + $C0` instead of `- $C0` | `SidekickCpuController` (comment only, no branch) | missing |
| 27287 | `loc_14474` | `Tails_Carry_Sonic` (`loc_14474`): carried player at `-$1C` instead of `+$1C` | `TailsCarryController:83,164` | covered |
| 27298 | `loc_14492` | `loc_14492`: carried player vertical mirror | — | missing |
| 27354 | `loc_14542` | carry pick-up window: `+$50` to the Y delta (`loc_14542`) | `TailsCarryController:68` | covered |
| 27407 | `sub_1459E` | `sub_1459E` carry release: `y_pos -= $38` and vertical mirror | — | missing |

### E. Knuckles routines

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 30394 | `Knuckles_Control` | `Knuckles_Control` debug cheat toggle | — | n/a |
| 30453 | `loc_16614` | `loc_16614`: vertical mirror after `Animate_Knuckles` | — | missing |
| 30840 | `Knuckles_Gliding_HitWall` | `Knuckles_Gliding_HitWall`: reverse-gravity ledge probe (`.reverseGravity`) | `GlideWallGrabTerrain.align` via `PlayableSpriteMovement:2557` | covered |
| 30880 | `Knuckles_Gliding_HitWall` | `Knuckles_Gliding_HitWall` (left wall): same | `GlideWallGrabTerrain.align` | covered |
| 30921 | `Knuckles_Fall_From_Glide` | `Knuckles_Fall_From_Glide`: negates the radius Y adjustment | — | missing |
| 30977 | `Knuckles_Sliding` | `Knuckles_Sliding`: negates the radius Y adjustment | — | missing |
| 31004 | `Knuckles_Sliding` | `Knuckles_Sliding`: negates the floor snap | — | missing |
| 31068 | `Knuckles_Wall_Climb` | `Knuckles_Wall_Climb` up: `.climbingUp_ReverseGravity` probes | — | missing |
| 31205 | `Knuckles_Wall_Climb` | `Knuckles_Wall_Climb` down: `.climbingDown_ReverseGravity` probes | — | missing |
| 31485 | `Knuckles_DoLedgeClimbingAnimation` | `Knuckles_DoLedgeClimbingAnimation`: negates the table Y delta | `PlayableSpriteMovement:2387` | covered |
| 31896 | `loc_172A8` | look-down camera bias: moves the opposite way **and** the limit changes from 8 to `$D8` (`loc_172A8`) | — | missing |
| 31919 | `loc_172E2` | look-up camera bias: moves the opposite way **and** the limit changes from `$C8` to `$18` (`loc_172E2`) | — | missing |
| 32261 | `loc_175AA` | `Knux_RollSpeed` unroll: negates the radius Y adjustment | — | missing |
| 32441 | `Knux_Jump` | `Knux_Jump`: mirrors the launch angle | — | missing |
| 32488 | `loc_1775C` | `Knux_Jump` (`loc_1775C`): negates the roll-radius Y adjustment | — | missing |
| 32663 | `loc_179B4` | `Knux_DoLevelCollision` `loc_179B4`: negates the floor snap | — | missing |
| 32692 | `loc_179F2` | `Knux_DoLevelCollision` `loc_179F2`: negates the push-out | — | missing |
| 32724 | `loc_17A36` | `Knux_DoLevelCollision` `loc_17A36`: floor landing through the floor wrapper; negates the snap distance, sets `angle`, zeroes `y_vel` | — | missing |
| 32758 | `loc_17A94` | `Knux_DoLevelCollision` `loc_17A94`: negates the push-out | — | missing |
| 32782 | `loc_17ACA` | `Knux_DoLevelCollision` `loc_17ACA`: negates the push-out | — | missing |
| 32802 | `loc_17AEC` | `Knux_DoLevelCollision` `loc_17AEC`: negates the push-out | — | missing |
| 32839 | `Knux_TouchFloor` | `Knux_TouchFloor`: negates the roll-clear radius Y adjustment | `PlayableHurtRadiusTransition:32` (verify it runs for Knuckles) | partial |
| 32911 | `sub_17C10` | `sub_17C10` (hurt): death-plane test flips to the top | — | missing |
| 33017 | `sub_17D1E` | `sub_17D1E` (hurt/dead animate): vertical mirror | — | missing |

### F. Dust, Tails' tails, shields, Super Tails birds

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 30063 | `loc_1613C` | `Obj_Tails_Tail` (`loc_1613C`): vertical mirror except for the directional animation 3 | — | missing |
| 34038 | `loc_18C20` | `Obj_DashDust` (`loc_18C20`): status Y-flip and `-4` Y offset | — | missing |
| 34113 | `loc_18D14` | `Obj_DashDust` (`loc_18D14`): negates the skid-dust Y offset | — | missing |
| 34594 | `Obj_InstaShield_Main` | `Obj_InstaShield_Main`: Y-flip status bit | — | missing |
| 34666 | `Obj_FireShield_Main` | `Obj_FireShield_Main`: Y-flip status bit | — | missing |
| 34747 | `Obj_LightningShield_Main` | `Obj_LightningShield_Main`: Y-flip status bit | — | missing |
| 34911 | `Obj_BubbleShield_Main` | `Obj_BubbleShield_Main`: Y-flip status bit | — | missing |
| 35081 | `Obj_SuperTailsBirds_Main` | `Obj_SuperTailsBirds_Main`: render Y-flip | `SuperTailsFlickyFlockObjectInstance:96` | covered |
| 35132 | `Obj_SuperTailsBirds_GetDestination` | `Obj_SuperTailsBirds_GetDestination`: target `+$20` instead of `-$20` | `SuperTailsFlickyFlockObjectInstance:126,272` | covered |

### G. Lost rings

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 35550 | `loc_1A67A` | ring spill (`loc_1A67A`): spawns `Obj_Bouncing_Ring_Reverse_Gravity` | `LostRingObjectInstance:208` (selection only) | partial |
| 35621 | `loc_1A738` | ring spill first object (`loc_1A738`): takes the reverse-gravity body | `LostRingObjectInstance:208-214` integrates `+yVel` with gravity negated; ROM `loc_1A7E8` keeps `y_vel += $18` and integrates `-y_vel` (`MoveSprite_TestGravity2`). No negation of the initial spill velocity was found, so the arc looks mirrored the wrong way. Not executed: settle with the 2b test | partial |

### H. Solid objects and platforms

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 41407 | `SolidObject_cont` | `SolidObject_cont`: vertical overlap uses the mirrored radii (`default_y_radius`) | — | missing |
| 41569 | `loc_1E0FC` | `sub_1E0C2` `loc_1E0FC`: negates the vertical push-out | — | missing |
| 41623 | `loc_1E154` | `sub_1E0C2` `loc_1E154`: landing from "above" is from below (`neg d3`, `+2`) | — | missing |
| 41648 | `MvSonicOnPtfm` | `MvSonicOnPtfm`: rider placed under the platform (`loc_1E1AA`) | — | missing |
| 41661 | `loc_1E1AA` | `MvSonicOnPtfm` unused S1 branch (`y + 9`); unreachable, record only | — | n/a |
| 41999 | `loc_1E44C` | `sub_1E410` (`SolidObjectTop` landing): reverse variant `loc_1E4D6` | — | missing |

### I. Monitors, springs, spikes

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 20802 | `Touch_Monitor` | `Touch_Monitor`: negates `y_vel` before the break-from-below / bounce test (with the monitor Y-flip bit) | `Sonic3kMonitorObjectInstance` (no flag read) | missing |
| 47577 | `Spring_Down` | `Spring_Down` init: becomes `Spring_Up` | `Sonic3kSpringObjectInstance:425` | covered |
| 47628 | `Spring_Up` | `Spring_Up` init: becomes `Spring_Down` | `Sonic3kSpringObjectInstance:425` | covered |
| 47722 | `sub_22F98` | `sub_22F98` (up-spring launch): `+8` becomes `-8` | `Sonic3kSpringObjectInstance` (no flag read at launch) | missing |
| 48095 | `sub_233CA` | `sub_233CA` (down-spring launch): `-8` becomes `+8` | `Sonic3kSpringObjectInstance` (no flag read at launch) | missing |
| 48958 | `loc_23FE8` | `Obj_Spikes` init (`loc_23FE8`): toggles the Y-flip bit that selects upright vs upside-down behaviour | `Sonic3kSpikeObjectInstance` (no flag read) | missing |

### J. DEZ objects

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 93727 | `loc_47AA6` | `Obj_DEZConveyorPad` (`$53`, `loc_47AA6`): negates the X carry | new | missing |
| 94874 | `loc_48B7E` | `Obj_DEZGravitySwitch` (`$58`, `loc_48B7E`): **writer** — toggles the flag 4 frames after a top/bottom press, then a 20-frame rearm | new | missing |
| 94990 | `loc_48CB0` | `Obj_DEZTeleporter` (`$59`): negates the unroll radius Y adjustment | new | missing |
| 95045 | `loc_48D78` | `Obj_DEZTeleporter`: Y-flip on the captured player frames | new | missing |
| 95075 | `loc_48DCA` | `Obj_DEZTeleporter` (`loc_48DCA`): compares subtype bit 7 with the flag (Player 1 only) | new | missing |
| 95080 | `loc_48DF2` | `Obj_DEZTeleporter` (`loc_48DF2`): **writer** — flag = subtype bit 7 | new | missing |
| 95277 | `loc_48FBA` | `Obj_DEZGravityTube` (`$5A`, `loc_48FBA`) exit: mirrors `flip_angle`, Y-flip | new | missing |
| 95322 | `loc_4904A` | `Obj_DEZGravityTube` (`loc_4904A`): Y-flip while riding | new | missing |
| 95511 | `sub_49228` | `Obj_DEZGravitySwap` (`$5B`, `sub_49228`): **writer** — clear | new | missing |
| 95514 | `sub_49228` | `Obj_DEZGravitySwap`: **writer** — set when `render_flags` bit 0 is clear | new | missing |
| 95536 | `loc_49270` | `Obj_DEZGravitySwap` (`loc_49270`, opposite crossing): **writer** — clear | new | missing |
| 95539 | `loc_49270` | `Obj_DEZGravitySwap`: **writer** — set when `render_flags` bit 0 is set | new | missing |

### K. DEZ act 2 boss

| Line | Label | What the branch changes | Engine consumer at `9cba6dbb6` | Status |
| ---: | --- | --- | --- | --- |
| 170329 | `sub_7F8A0` | `Obj_DEZEndBoss` `sub_7F8A0`: its own acceleration `$38` becomes `-$38`, integrated normally (exception to the inversion model) | new | missing |
| 170349 | `sub_7F8CA` | `sub_7F8CA`: inverts the `$3A` test that starts routine 4 | new | missing |
| 170746 | `loc_7FC3E` | `loc_7FC3E`: **writer** — persistent clearer object spawned at boss defeat (`loc_7FBD6`); clears the flag every frame | new | missing |

Groups the brief asked for that have **no** reference: camera code proper (the look up/down bias
lives in the player routines, groups B/C/E), other zones' objects (the FBZ wire cage's engine
branch at `FbzWireCageStationaryObjectInstance:124` mirrors a radius adjustment and has no flag
test in the ROM object; re-derive it in step 2 rather than trusting it), and level init (the clear
is the RAM wipe described above).

## Totals

| Group | References | Covered | Partial | Missing | n/a |
| --- | ---: | ---: | ---: | ---: | ---: |
| A. Shared integration and sensor wrappers | 8 | 0 | 1 | 7 | 0 |
| B. Sonic (and Sonic/Knuckles shared) routines | 20 | 0 | 1 | 18 | 1 |
| C. Tails routines | 21 | 0 | 1 | 19 | 1 |
| D. Tails CPU, flight catch-up and carry | 5 | 2 | 0 | 3 | 0 |
| E. Knuckles routines | 24 | 3 | 1 | 19 | 1 |
| F. Dust, Tails' tails, shields, Super Tails birds | 9 | 2 | 0 | 7 | 0 |
| G. Lost rings | 2 | 0 | 2 | 0 | 0 |
| H. Solid objects and platforms | 6 | 0 | 0 | 5 | 1 |
| I. Monitors, springs, spikes | 6 | 2 | 0 | 4 | 0 |
| J. DEZ objects | 12 | 0 | 0 | 12 | 0 |
| K. DEZ act 2 boss | 3 | 0 | 0 | 3 | 0 |
| **Total** | **116** | **9** | **6** | **97** | **4** |

"Covered" means a flag-reading branch exists at the cited engine line; none of it has been
exercised with the flag set in a level, because nothing sets the flag today. Treat covered rows as
"verify", not "done". `n/a` rows are the three debug-cheat toggles and one unreachable S1 leftover.

## Implementation order and the test that proves each step

Each step lands with the flag forced by a test-only call to
`GameStateManager.setReverseGravityActive(true)` in a headless S3K level, before any DEZ object
exists. Expected values come from the cited ROM branch, never from the Java under test. Every
branch must be inert with the flag clear.

| Step | Groups and rows | First failing test (`src/test/java/com/openggf/tests/`) | Expectation source |
| --- | --- | --- | --- |
| 2a-1 | A: 36069, 36089, 19696 | `TestS3kReverseGravityIntegration`: airborne Sonic with `y_vel = +$400` moves **up** 4 px/frame while `y_vel` still grows by `$38`; subpixel preserved | `MoveSprite_TestGravity` |
| 2a-2 | A: 22330, 24128, 24142, 24156, 23191; B: 24081-24350, 24475-24716; matching C and E collision, touch-floor, hurt and death rows | `TestS3kReverseGravityTerrain`: lands on a ceiling, walks a ceiling slope with the mirrored angle, is pushed out of a floor when jumping "up" into it, dies at the top boundary and not at the bottom | `Call_Player_AnglePos`, `sub_11FD6/11FEE`, `Player_Boundary_CheckBottom` |
| 2a-3 | B: 22011, 22623-23694, 24426; matching C and E rows including 28655 (bug) and 29594 | `TestS3kReverseGravityPlayerActions`: jump launch vector on flat and on a `$20` slope, roll/unroll/spindash Y offsets (`±5`, Tails `±1`), look up/down bias direction, `render_flags` bit 1, bubble bounce, and Tails' flight start **not** inverted | `Sonic_Jump`, `Player_DoRoll`, `loc_1515C` |
| 2b | H, I, G, F | `TestS3kReverseGravityObjects`: stand under a solid and ride a moving platform from below, up-spring placed on a ceiling launches down-screen with `-8`, spikes hurt from their mirrored face, a Y-flipped monitor breaks from the new "below", ring spill launches away from the ceiling-floor and bounces on it (expect this to fail against today's `LostRingObjectInstance`), shields/dust/tails mirrored | `SolidObject_cont`, `MvSonicOnPtfm`, `sub_22F98`, `loc_23FE8`, `Touch_Monitor` |
| 2c | D, E glide/slide/climb rows, F birds, Super forms | `TestS3kReverseGravityCompanions`: CPU Tails respawns from the bottom (`+$C0`), carry offset `-$1C`, release `-$38` with mirror, Knuckles glide → wall grab → climb → ledge climb on an inverted wall, slide landing | `loc_13B50`, `sub_1459E`, `Knuckles_Wall_Climb` |
| 3 | J | per-object tests in the DEZ object slice | each object's routine |
| 8 | K | `TestS3kDezAct2BossHeadless` | `sub_7F8A0`, `sub_7F8CA`, `loc_7FBD6`/`loc_7FC3E` |

Adjacent phases every step must include: flip while airborne, rolling, standing on an object,
hurt, on a slope, while carried; flip back; both players on screen (only Player 1 can trigger a writer); capture/restore with the flag
set followed by forward replay.

Validation for the whole of step 2 is normal change-based validation plus matched before/after S1,
S2 and S3K trace profiles. It is a shared timing/physics change: never proportionate validation.
