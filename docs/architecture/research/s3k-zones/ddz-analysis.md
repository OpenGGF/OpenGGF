# S3K DDZ Zone Analysis

Date: 2026-09-17. Source audit of `docs/skdisasm/sonic3k.asm` (locked-on S3K) at develop
`832554260`. `loc_`/`sub_` names are ROM addresses; line numbers are given separately.
This is a starting inventory for the [DDZ bring-up plan](../../plans/2026-09-17-ddz-bring-up.md):
every row is a source-audit claim to reverify against the owning routine before it is
implemented, and rows marked *unverified* were not traced to their exact line.

## Summary

- **Zone:** The Doomsday Zone (DDZ), zone `$0C`, `Current_zone_and_act = $C00`. Zone set SKL.
- **Acts:** one. The level-select "DDZ act 2" row is `$1700` (the DEZ boss arena, zone `$17`);
  the LevelLoadBlock second row (`sonic3k.asm:199458`) is unused and the act-2 object/ring
  files are empty.
- **Roster:** Sonic only. `Player_mode < 2` enters; the controller zeroes Player 2 RAM, so a
  Sonic + Tails game plays DDZ as Sonic alone. Level select denies Knuckles `$C00`/`$1700`
  (`:10181-10185`) and Tails `$C00` (`:10204`).
- **Water:** none. **AniPLC:** `AnimateTiles_NULL`. **AnPal:** `AnPal_None` (exact table rows
  *unverified*). All palette motion is object-driven.
- **Unique mechanics:** an object owns the camera (16.16 autoscroll, lock, `$2000` wrap); the
  player is held by `object_control` and flown by that object; hurt costs scroll speed, not
  rings; the boss body is the **foreground plane**, not sprites; every collision is a custom
  range box with `collision_flags = 0`.

## Entry and exit

| Concern | ROM owner | Notes |
| --- | --- | --- |
| DEZ final boss handoff | `loc_803D6` (`:171517`) | After white fade `loc_85E64`: `SaveGame`; `Player_mode < 2` and `Chaos_emerald_count == 7` → `StartNewLevel $C00` (`:171527`); otherwise non-Knuckles → `$D01`, Knuckles → `Game_mode = 0` |
| Save gating | `SaveGame` `:15868-15895` (`loc_C478`/`loc_C488`) | Sonic whose next level is DDZ without 7 emeralds is saved as completed |
| Level select | `:10160` `$C00`, `:10161` `$1700`, text `:10567` | `Debug_DDZ2` uses the DEZ boss list (`:200310`) |
| LevelSizes | `:38121-38122` | x `0..$6000`, y `0..$1000`; the controller overrides camera bounds at runtime |
| Start position | `Start Location/Sonic/1.bin` | x `$0000`, y `$0100` |
| Title card | `ArtKosM_DDZTitleCard` `:62440`, `Obj_TitleCardAct` `:62387` | No act number; `PLC_SpikesSprings` reload skipped for zone `$C` (`:62288`) |
| LevelLoadBlock | `:199457` | PLC `$3A/$3A`, palette `$22`, `ArtKosM_DDZ`, `DDZ_16x16_Kos`, `DDZ_128x128_Kos`; `PLC_3A_3B_3C_3D_3E_3F` (`:199824`) is `ArtNem_DiagonalSpring` only |
| Other PLC | `PLCKosM_DDZ` `:64438` (`ArtKosM_EggRoboBadnik`, use by DDZ objects *unverified*), `PLC_Animals_DDZ` `:180972` | |
| Music | `LevelMusic_Playlist` `:7489` `mus_DDZ` (`$1A`) | Tempo object `loc_82722` (`:174758`): `Change_Music_Tempo` 8 when `Ring_count <= 10`, else 0 |
| Exit | `loc_82E2C` (`:175554`) → `loc_81BBE` (`:173738`) → `loc_81CA4` (`:173810`) | `Ctrl_1_locked`, `Super_frame_count = $7FFF`, camera +1/frame, explosions, white fade, player +8 px/frame, `Super_palette_status = 0`, `SaveGame`, `StartNewLevel $D01` |
| Ending zone `$D01` | handlers `:120858+`; `Ending_ScreenEvent` gates `:120943` (Chaos) and `:120979` (Super → `Obj_5DFEE`) | Ending variants *not traced* |

## Controller and forced transformation

`DDZ_ScreenInit` (`:118813`) spawns controller `loc_81492`.

- **Init `loc_81554` (`:173241`):** `Scroll_lock`, `Boss_flag = 1`, player `object_control = $81`,
  anim 2, high priority, zero Player 2 RAM, queue `ArtKosM_DDZMisc`.
- **After `$18` frames, `loc_8160A` (`:173296`):** `Ring_count += 50`, `Super_palette_status = 1`,
  `Super_Sonic_Knux_flag = 1`, `Super_frame_count = 60`, `Map_SuperSonic`, anim `$1F`, max speed
  `$A00` / accel `$30` / decel `$100`, `sfx_Whistle` (not the normal transform SFX).
- **`loc_8167C` (`:173312`):** when `object_control` clears → `object_control = 1`, x_vel and
  ground_vel `$1000`; `Super_emerald_count == 7` → `sub_5FCCE` (`:126664`, flag −1 = Hyper) plus
  `Obj_HyperSonic_Stars` / `Obj_HyperSonicKnux_Trail`; otherwise custom star object `loc_8242A`.
  Loads `PLC_BossExplosion`.
- **Ring drain:** standard `SonicKnux_SuperHyper` (`:23579`), one ring per 61 frames; it still runs
  with `object_control` bit 0 (called from `loc_10C36`, `:22005`).
- **Player dispatch:** no zone check in `Obj_Sonic`. `loc_10BFC` (`:21974`) skips `Sonic_Modes` under
  `object_control = 1`; `TouchResponse` still runs, so layout rings collect through the normal path.

| Routine | Line | Behaviour |
| --- | --- | --- |
| `sub_82772` | `:174780` | Controller velocity decays `$40`/frame per axis; D-pad `$300` (diagonal `$21F`, `word_82832`); A/B/C in the *press* byte while not invulnerable sets the dash velocity from `word_82872` (`$600`, diagonal `$43E`, no D-pad = `+$600` X); result written to player x_vel/y_vel |
| `sub_828B2` | `:174900` | Moves the controller within the camera-relative box `_unkFAB0..B6` (init `word_81602` = `$20..$C0` both axes, `:173289`); `$38` bit 1 = at the right edge |
| `sub_829D2` | `:175043` | Copies controller position to the player; camera follow windows (X when bit 3, Y when bit 2) |
| Hurt | `:174785-174797` | No `HurtCharacter`, no ring loss. A hit sets `invulnerability_timer` (89/59/90); while it is ≥ 30 the player spins (`angle += $10`), control is off and velocity = −(scroll speed >> 8) |
| `sub_82742` → `loc_8179E` | `:174768`, `:173396` | Once transformed (bit 7) and `Super_Sonic_Knux_flag == 0`: anim `$1A`, `MoveSprite` gravity fall, below `Camera_Y + $F0` → `Kill_Character` |
| `sub_8151C` | `:173213` | Debug-mode toggle handling |

## Camera, wrap and planes

| Routine | Line | Behaviour |
| --- | --- | --- |
| `sub_82920` | `:174989` | 16.16 autoscroll speed `_unkFA82` (start `$10000`), accel `_unkFA8A` (`$800`; `−$20` during the phase-1 death). Cap `$60000`, or `$80000` with the player pinned at the right edge and +x_vel (adds x_vel << 4). Adds to `Camera_X_pos`, sets `Camera_min/max_X`, accumulates `Events_bg+$06`, publishes the per-frame camera delta in `_unkFA90`. Floor `$10000` |
| `sub_829A0` | `:175028` | Camera-locked variant: scrolls the background only |
| Speed penalties | asteroid `:174346`, `sub_82C6A` `:175340` | Asteroid `−$10000`; projectile or boss hit `−$30000` |
| `loc_81726` | `:173350` | Wrap: `Camera_X >= $7400` → `−$2000`, `Seek_Object_Manager`, clear `Ring_status_table` (`$400` bytes), recompute `Camera_X_pos_coarse_back`, `_unkFAAE = $2000`, which every DDZ object subtracts from x_pos that frame. Only after phase 2 starts (`_unkFAB8` bit 0) |
| `DDZ_ScreenEvent` + `sub_59648` | `:118828`, `:118923` | **Foreground plane = boss body.** FG scroll `_unkEE98/_unkEE9C` = Camera − boss position (`Events_bg+$02/$04`, written by the boss each frame, `:173428`) + `Events_bg+$00` (`$200` phase 1, `$600` phase 2), `+$100` Y. Four sub-states redraw with `Draw_TileColumn/Row` and `Draw_PlaneVertBottomUp` on phase change; V-scroll written directly (`:118976`) |
| `DDZ_BackgroundEvent` + `sub_596EA` | `:118959` | BG Y = `Camera_Y / 2`; six parallax speeds from `Events_bg+$06` (×1/16, each −1/8 step); `DDZ_BGDeformArray` `:119006` = `$B0,$10,8,8,$18,$38`; `ApplyDeformation2`. No H-int or water |
| Event table | `:102307-102310` | `No_Resize` for Dynamic_Resize (row *unverified*) |

## Objects

Pointer table `Levels/Misc/Object pointers - SK Set 2.asm:193-195`: `$B6 Obj_DDZEndBoss`,
`$B7 Obj_DDZAsteroid`, `$B8 Obj_DDZMissile`. **The layout is not empty:** `Object Pos/1.bin` has
477 entries over x `$290..$73D0` — 426 asteroids (subtypes 0-5, `$10-$12`, `$15`, `$20`, `$21`,
`$23`, `$25`), 50 missiles (subtype 0) and one boss at (`$53F0`, `$80`), entry 371.
`Ring Pos/1.bin` holds about 194 rings through the normal ring manager. There is no dynamic
asteroid or ring spawner.

- **Asteroid (`:174310`, `sub_83146` `:175853`).** Subtype high nibble = size (16/24/40 px; frames
  `$26/$27/$28`; range boxes), low nibble = x_vel from `word_8317E` (`−$80..$80`, `−$200`).
  Collision through `Check_InMyRange` only. Hit `loc_8222A` (`:174336`): `sfx_Collapse`, controller
  x_vel `−$400`, speed `−$10000`; size `$20` splits into three size-`$10` asteroids (three layouts
  by player Y relation), then 5 or 7 debris `loc_823EE`; clears the respawn bit.
- **Missile (`:174131`).** Subtype 0 (layout): flies left 2 px/frame, `sfx_Dash` on first
  on-screen, angle-based eight-direction hitboxes `off_82BBC`. Subtype 1 (boss-launched by
  `loc_81F94`, `:174094`, three at a time, `sfx_TubeLauncher`): launch states then homing
  `sub_82A82` (`:175103`; ±2 angle on 3 of 4 frames of `V_int_run_count`), speed 2×sin/cos.
  `sub_82B06` (`:175161`): on screen, subtype ≠ 0, boss body `_unkFAA4` with `collision_flags == 0`
  and inside `word_82BB4` → `collision_property−−`, flags `$FF`; otherwise it hits the player
  (invulnerability 89). Exhaust and trail children `loc_8214A`, `loc_8218E`.
- **Boss phase 1 (`Obj_DDZEndBoss`, `:173421`, eight routines).** Init sets `_unkFAB8` bit 1: the
  controller locks the camera (`Obj_Dec/IncLevStart/EndXGradual`). Body `loc_81E3C`
  (`collision_property = 7`, eight missile hits; `$20`-frame flash on palette line 3 via `sub_82D72`,
  `sfx_ThumpBoss`, `HUD_AddToScore` 100), three flicker parts `loc_81F36`, three aiming turrets
  `loc_81E82` firing `loc_81F14` every `$60` frames (eight directions, `$400`; hit = invulnerability
  59, `sfx_Explode`). Held Right nudges boss X ±2 (`sub_830C0`, `:175784`). Defeat: routine 6 (five
  seconds of `loc_82E9A` explosions), 8 (fall, `DecColor_Obj` line 3, `sfx_Rumble2`), `$A` (white
  flash `loc_83108`, reload `Pal_DDZ+$20`, queue `ArtKosM_BossMasterEmerald`).
- **Boss phase 2 (routines `$C/$E`, `:173607-173735`).** Ship rises, `Events_bg = $600`; children
  `loc_81D72` ×3, Master Emerald `loc_81CC6` (palette rotation `word_8141E` only when all seven
  `Collected_emeralds_array == 3`), `loc_81F7E` ×2; sets `_unkFAB8` bit 0 (wrap mode),
  `collision_property = 7`. Damage by direct player overlap `loc_82DCE` (`:175521`, box
  `word_82E92`, `sfx_BossHit`, invulnerability 90, knockback `−$1000`, speed `−$30000`) → eight
  hits. Attacks `sub_8307C` (`:175758`) every `$82` frames: four `loc_825CA` when the player is below
  boss + `$68` (`sfx_BossProjectile`), otherwise four `loc_8249A` (`sfx_TubeLauncher`).

## Palette, art and sound

- `Pal_DDZ` (`:200674`, 96 bytes, lines 2-4). Object-driven palette only: Super/Hyper cycle, boss
  flash tables `word_82D86/82D9E` (`:175512`), `Run_PalRotationScript` on the Master Emerald,
  `DecColor_Obj`, white fades `loc_85E64` / `loc_85EE6` / `sub_85EB4` (`:180664-180733`).
- Art: `ArtKosM_DDZ`, `DDZ_16x16_Kos`, `DDZ_128x128_Kos` (`:202259-202266`); `Layout_DDZ` (268 B,
  `:200523`); `Solid_DDZ` (`:200422`); `ArtKosM_DDZMisc` (KosM, 7026 B, `:201844`);
  `Map_DDZMissileAsteroid` (`:176031`, no DPLC); `ArtKosM_BossMasterEmerald` (`:201838`) /
  `Map_BossMasterEmerald` (`:201921`); `ArtUnc_SuperSonic_Stars` by DMA (`:174532`).
- SFX: Whistle (`$46`), Rumble2, MissileExplode, Blast, TubeLauncher, Dash, Collapse, Explode,
  ThumpBoss, BossHit, BossProjectile.

## Shared routines

`loc_85E64` fade (also the DEZ boss, `:171493`), `Child6_CreateBossExplosion` (`:176888`),
`Swing_UpAndDown` (`:177856`), `Obj_*LevStart/End*Gradual` (`:178159-178215`),
`Check_InMyRange/InTheirRange` (`:179934`/`:179964`), `Run_PalRotationScript` (`:180076`),
`sub_8622C` angle-to-target (`:181154`), `Obj_FlickerMove`, Master Emerald art and mappings.

## Size and principal risks

Controller, boss, missile, asteroid and data: `:173130-176032`, about 2,900 lines (about 350 data);
screen/background events about 200; mappings 254; entry/transition touchpoints about 100.

1. The boss body is foreground-plane tiles scrolled relative to a boss object, with staged plane
   redraws.
2. An object owns the camera: 16.16 autoscroll, lock/unlock, `$2000` wrap with object-manager
   reseek, ring-status clear, and `_unkFAAE` / `_unkFA90` compensation in every object.
3. The player is driven externally under `object_control` `$81`/`1`; hurt is non-standard; every
   collision is a custom range box.
4. Rewind state: about twelve globals `_unkFA82.._unkFAB8`, `Events_bg+0..6`, `_unkEE98..EEA2`.
5. Homing-missile angle arithmetic gated on `V_int_run_count`.
6. The Hyper/Super branch and the `$D01` ending handoff.

## Engine state at `832554260`

Data only: zone id, registry row, level bounds and start, `mus_DDZ`, title card, one PLC art entry
(`DDZ_EGG_ROBO`), animals, level-select slot 24, save-progress code. Missing: events class,
scroll handler (`Sonic3kScrollHandlerProvider` has no `ZONE_DDZ` case, so zone `$0C` silently gets
`SwScrlS3kDefault`), object factories (`$B6-$B8` are names only in `getSklName`), runtime state,
flight control, a level-start forced-Super API (`SuperStateController` offers only
`debugActivate`, `activateFromMonitor`, `activateFromAirAbility`), DEZ events and the DEZ → DDZ
transition, an S3K ending/credits provider, a per-act matrix and a coverage-backlog row.
`Sonic3kObjectArt.java:1086,1107` comments cite zone `0x16` for the DDZ HUD digits (stale).
