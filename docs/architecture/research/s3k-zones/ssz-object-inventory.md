# SSZ placed-object inventory

Initial inventory at `9cba6dbb6`, 2026-09-17, for the
[SSZ bring-up plan](../../plans/2026-09-17-ssz-bring-up.md). Locked-on ROM SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6` (CRC32 `63522553`); source submodule
`1a454a0e335137a1a016d1090a9f4528d36944cf`. IDs and subtypes are hexadecimal.

**Research decode only.** Counts come from the disassembly binaries
`Levels/SSZ/Object Pos/1.bin` (1284 bytes), `2.bin` (36 bytes) and `Ending.bin` (6 bytes,
terminator only), decoded with a throwaway script. Each file was byte-matched against the ROM:
`SSZ1_Sprites` `$1F90EE`, `SSZ2_Sprites` `$1F95F2`, `SSZ1_Rings` `$1F9616` (722 bytes),
`SSZ2_Rings` `$1F98E8` (6 bytes); addresses from `sonic3k.lst` (`SpriteLocPtrs` entries at
`$1E3DE8/$1E3DEC`). The runtime loads these bytes through `Sonic3kObjectPlacement` from the ROM;
the disassembly files are never a runtime source. Each list ends with one six-byte terminator
(`$FFFF,0,0`), not counted. Live counts: **213 (act 1) + 5 (act 2) = 218**, in 68 (ID, subtype) rows.

Record format (`sub_1BA0C`, `loc_1BA4A`): X word; Y word = Y `& $FFF`, bit 13 X-flip, bit 14 Y-flip,
bit 15 "load regardless of the Y window" (`bmi` skips the range test; it is **not** a respawn bit);
ID byte; subtype byte. No SSZ record sets bit 15. Two `$7D` records (index 90 `($C70,$103C)`, 91
`($C94,$104C)`) store Y above `$FFF`; the loader masks them to `$03C/$04C`, i.e. they sit just past
the `$1000` wrap seam and belong to the top of the tower.

Pointer names come from `Levels/Misc/Object pointers - SK Set 2.asm`. SSZ is zone `$0A`, so the SKL
set applies (S3KL covers zones 0-6, SKL 7-13: `AGENTS_S3K.md`); the pointer set is independent of
the art half. Several engine constants at these numeric IDs are S3KL names (`FBZ_*` for `$74-$7F`,
`ICZ_CRUSHING_COLUMN` `$AF`, `ICZ_FREEZER` `$B2`): their factories are S3KL-bound and return
`PlaceholderObjectInstance` for SKL, so SSZ needs new SKL registrations, not edits to those.

Rings (`Sonic3kRingPlacement`, 4-byte records, `$FFFF` terminator): act 1 has **180 records, the
first of which is `(0,0)`**, so 179 positioned rings plus the leading zero record (confirm in slice 0
what the ring manager does with it; do not filter it without ROM evidence). Act 2 has only the
`(0,0)` record; its three visible rings are placed objects (`$00`).

Factory column is the classification at `9cba6dbb6`, read from
`Sonic3kObjectRegistry.registerDefaultFactories`. It is not execution or route certification.

| ID | Subtype | ROM pointer owner | Act 1 | Act 2 | Current factory |
| --- | --- | --- | ---: | ---: | --- |
| `$00` | `$00` | `Obj_Ring` | 0 | 3 | shared concrete |
| `$01` | `$01` | `Obj_Monitor` | 3 | 0 | shared concrete |
| `$01` | `$03` | `Obj_Monitor` | 8 | 0 | shared concrete |
| `$01` | `$05` | `Obj_Monitor` | 1 | 0 | shared concrete |
| `$01` | `$06` | `Obj_Monitor` | 1 | 0 | shared concrete |
| `$01` | `$07` | `Obj_Monitor` | 1 | 0 | shared concrete |
| `$01` | `$08` | `Obj_Monitor` | 2 | 0 | shared concrete |
| `$02` | `$45` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$07` | `$01` | `Obj_Spring` | 4 | 0 | shared concrete |
| `$07` | `$03` | `Obj_Spring` | 1 | 0 | shared concrete |
| `$07` | `$10` | `Obj_Spring` | 1 | 0 | shared concrete |
| `$07` | `$12` | `Obj_Spring` | 3 | 0 | shared concrete |
| `$08` | `$00` | `Obj_Spikes` | 7 | 0 | shared concrete |
| `$08` | `$10` | `Obj_Spikes` | 3 | 0 | shared concrete |
| `$08` | `$11` | `Obj_Spikes` | 1 | 0 | shared concrete |
| `$14` | `$01` | `Obj_Updraft` | 4 | 0 | shared concrete |
| `$28` | `$11` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$30` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$81` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$34` | `$02` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$03` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$04` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$74` | `$00` | `Obj_SSZRetractingSpring` | 5 | 0 | placeholder |
| `$75` | `$00` | `Obj_SSZSwingingCarrier` | 5 | 0 | placeholder |
| `$75` | `$80` | `Obj_SSZSwingingCarrier` | 1 | 0 | placeholder |
| `$75` | `$82` | `Obj_SSZSwingingCarrier` | 2 | 0 | placeholder |
| `$76` | `$00` | `Obj_SSZRotatingPlatform` | 3 | 0 | placeholder |
| `$76` | `$01` | `Obj_SSZRotatingPlatform` | 4 | 0 | placeholder |
| `$77` | `$00` | `Obj_SSZCutsceneBridge` | 1 | 0 | placeholder |
| `$79` | `$00` | `Obj_SSZHPZTeleporter` | 5 | 1 | concrete class, SSZ branch missing |
| `$79` | `$15` | `Obj_SSZHPZTeleporter` | 1 | 0 | concrete class, SSZ branch missing |
| `$79` | `$1E` | `Obj_SSZHPZTeleporter` | 1 | 0 | concrete class, SSZ branch missing |
| `$79` | `$32` | `Obj_SSZHPZTeleporter` | 1 | 0 | concrete class, SSZ branch missing |
| `$79` | `$AA` | `Obj_SSZHPZTeleporter` | 1 | 0 | concrete class, SSZ branch missing |
| `$79` | `$F6` | `Obj_SSZHPZTeleporter` | 1 | 0 | concrete class, SSZ branch missing |
| `$7A` | `$00` | `Obj_SSZElevatorBar` | 5 | 0 | placeholder |
| `$7B` | `$00` | `Obj_SSZCollapsingBridgeDiagonal` | 31 | 0 | placeholder |
| `$7B` | `$80` | `Obj_SSZCollapsingBridgeDiagonal` | 4 | 0 | placeholder |
| `$7C` | `$00` | `Obj_SSZCollapsingBridge` | 7 | 0 | placeholder |
| `$7C` | `$80` | `Obj_SSZCollapsingBridge` | 1 | 0 | placeholder |
| `$7D` | `$00` | `Obj_SSZBouncyCloud` | 27 | 0 | placeholder |
| `$7E` | `$00` | `Obj_SSZCollapsingColumn` | 25 | 0 | placeholder |
| `$7F` | `$00` | `Obj_SSZFloatingPlatform` | 8 | 0 | placeholder |
| `$A0` | `$00` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$02` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$04` | `Obj_EggRobo` | 3 | 0 | placeholder |
| `$A0` | `$10` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$12` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$20` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$22` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$30` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$32` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$40` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$42` | `Obj_EggRobo` | 2 | 0 | placeholder |
| `$A0` | `$50` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$52` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$60` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$62` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$70` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$72` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$80` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$82` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$90` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$92` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$A0` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$A0` | `$A2` | `Obj_EggRobo` | 1 | 0 | placeholder |
| `$AF` | `$00` | `Obj_SSZCutsceneButton` | 1 | 0 | placeholder |
| `$B2` | `$00` | `Obj_KnuxFinalBossCrane` | 0 | 1 | placeholder |

## Totals

| Classification | Rows | Act 1 | Act 2 | Notes |
| --- | ---: | ---: | ---: | --- |
| Shared concrete | 22 | 47 | 3 | `$00 $01 $02 $07 $08 $14 $28 $34`. Verify only: subtypes under the Y wrap, `$14` `Obj_Updraft` subtype `$01` (4, all at Y `$CC0` under the first bridge), `$28` subtypes `$11/$30/$81`, starposts `$02/$03/$04` |
| Concrete class, SSZ branch missing | 6 | 10 | 1 | `$79` resolves to `SSZHPZTeleporterObjectInstance`, which implements only the HPZ/`$1701` init (`loc_45574`). The SSZ path (`loc_455BA` onwards) is absent |
| Placeholder | 40 | 156 | 1 | `$74-$77`, `$7A-$7F`, `$A0`, `$AF`, `$B2` |
| Unregistered | 0 | 0 | 0 | Every placed ID has at least a named placeholder |

Per ID: `$74` 5, `$75` 8, `$76` 7, `$77` 1, `$79` 10+1, `$7A` 5, `$7B` 35, `$7C` 8, `$7D` 27,
`$7E` 25, `$7F` 8, `$A0` 26, `$AF` 1, `$B2` 0+1. No boss is placed: `$A1/$A2/$A3` never appear.

## Subtype semantics that are load-bearing

Read from the disassembly at the submodule revision above; each is a verification target, not a
substitute for the implementing slice's own re-read.

| Object | Placements | Meaning |
| --- | --- | --- |
| `$79` sub `$00` | `($100,$C70)` `($200,$5B0)` `($1500,$CF0)` `($1700,$B0)` `($1A40,$670)`; act 2 `($A0,$4B0)` | Receiving pad: `loc_4562C` skips the launch test for subtype 0. The pad at `($1A40,$670)` satisfies `x >= $1A00 && y < $680` (`loc_455BA`) and becomes the **Mecha Sonic spawner** `loc_45A66`/`loc_45A84` (high priority art; allocates `Obj_SSZEndBoss` when `Camera_Y == Camera_max_Y`, stores the slot in `_unkFAA4`; explodes and deletes itself once the boss X passes the pad) |
| `$79` sub `$15 $1E $32` | `($1000,$7B0)` `($1500,$EF0)` `($1A40,$9B0)` | Always-active launch pads. Lift = `(subtype & $3F) * $10` px (`loc_45744`, `loc_45804`): `$150`, `$1E0`, `$320`. Launch sets `Camera_min_Y = -$100`, `Camera_max_Y = Camera_target_max_Y = $1000`, `Scroll_lock`, clears `Events_bg+$05` |
| `$79` sub `$AA` | `($200,$870)` | Bit 7 set, bit 6 clear: gated on `Events_bg+$00` negative (GHZ beaten). Lift `$2A * $10 = $2A0` to the pad at `($200,$5B0)`. While the flag is not negative the pad is sunk `$20` px (`y_vel` reused as a displacement) and inert; once negative it rises 1 px every 4th `Level_frame_counter` tick |
| `$79` sub `$F6` | `($1700,$430)` | Bits 7 and 6 set: gated on `Events_bg+$02` negative (MTZ beaten). Lift `$36 * $10 = $360` to `($1700,$B0)` |
| `$34` | `$02 ($640,$5E8)`, `$03 ($14C0,$E8)`, `$04 ($1880,$968)` | Subtype `$01` is absent from the **placements**. The ROM still creates a pseudo-starpost: bridge `$77` (`loc_44FBA`) and cutscene Knuckles on leaving the screen (`loc_65976`) write `Last_star_post_hit = 1`, `Saved_X/Y = $140,$C6C` and call `Save_Level_Data` (the bridge then clears `Saved_timer`). `$03` and `$04` are the native respawn points seen in fixtures `hpz_2` and `hpz_3` |
| `$A0` | 26, subtypes `$00-$A2` | `_unkFA82` is a **pairing gate**, not a defeat mask. Low nibble 0 = scaled fly-by that sets bit `subtype >> 4` when it leaves (`loc_91570`; 11 indices `0-$A`). Low nibble 2 = fighter: `sub_91914` deletes it through `loc_85088` unless that bit is already set. Low nibble 4 = shooter (`loc_915F6`, gate `V_int_run_count+3 & $F`). The same RAM is overwritten by `Obj_SSZMTZBoss` (`loc_7A7C4`: bytes `$10,0,3,0,1,0` at `_unkFA82.._unkFA87`) and Mecha Sonic (`loc_7BB20`: words at `_unkFA82/84/86`), and cleared by `Level` (`clearRAM _unkFA80,$80`) |
| `$77` + `$AF` | `($320,$C88)`, `($3A0,$C7C)` | `$AF` is an inert sprite (`loc_659C6` = `Sprite_OnScreen_Test`). Cutscene Knuckles lands on terrain (`ObjCheckFloorDist`, `loc_658F2`) and sets `Events_bg+$08`; the bridge reads it, slides `$C0` px at 2 px/frame (`sfx_DoorOpen` at `$68` remaining), then clears `Events_bg+$05`, writes the act bounds and the pseudo-starpost. With `Last_star_post_hit != 0` it spawns already extended (`loc_4501A`) |
| `$75` | `$00` ×5, `$80` ×1, `$82` ×2 | Bit 7 and low bits select the carrier variant: read `Obj_SSZSwingingCarrier` init |
| `$76` | `$00` ×3, `$01` ×4 | Rotation direction/phase: read `Obj_SSZRotatingPlatform` init |
| `$7B`, `$7C` | `$00` ×31/7, `$80` ×4/1 | Bit 7 variant (mirrored/other debris table): read the collapse tables |
| `$02` | `$45 ($1A40,$6C0)` | Single path swap just below the Mecha Sonic pad |
| Act 2 | `$00` ×3 at `($A0,$440/$458/$470)`, `$79` `($A0,$4B0)`, `$B2` `($180,$430)` | The whole of `$A01`. Everything else is event-spawned |

## Dynamic objects not in the placement lists

| Object | Spawner | Act |
| --- | --- | --- |
| `Obj_57C1E` arrival controller + `Obj_TeleporterBeamExpand` | `SSZ1_ScreenInit` (no starpost only; X `$100`, `$2D = $6C`), `SSZ2_ScreenInit` (X `$A0`, `$2D = $44`) | 1, 2 |
| `Obj_57E34` (`subtype $60` = 96-frame delay) → `Obj_CutsceneKnuckles` subtype `$2C` = `CutsceneKnux_SSZ` (11 routines, `loc_65730`…), X `$100`, base Y `$C4E`; sets `_unkFAB8` bit 0 on landing; owns `_unkFAA4` until Mecha Sonic takes it | `Obj_57C1E` init, act 1 only, **every player mode** | 1 |
| `Obj_57DCC` Tails arrival helper (Player 2 roll-up, then `Tails_CPU_routine = 6`) | `loc_57CD2`, act 1 and `Player_mode == 0` only | 1 |
| `Obj_57D64` / `loc_57DA2` post-arrival bounds settle | `Obj_57C1E` successor states | 1, 2 |
| `Obj_TeleporterBeam` | `$79` launch (`loc_45660`) | 1 |
| Roaming clouds `loc_57BB2` ×5 (`word_58758`) | `SSZ1_ScreenInit` | 1 |
| Cloud oscillator `loc_57B6A`, solid clouds `loc_57B8E` ×10 (`word_5853E`) | `SSZ1_BackgroundInit` | 1 |
| `Obj_SSZGHZBoss` (+ `ChildObjDat_7A684/7A69E`, `CreateChild9_TreeList`, `Child1_MakeMechaHead`, `Child6_CreateBossExplosion`; `ArtKosM_SSZGHZMisc`, `Pal_SSZGHZMisc`, `mus_EndBoss`) | `sub_575EA` `loc_576E8` | 1 |
| `Obj_SSZMTZBoss` (+ `ChildObjDat_7AB80`, `Child1_MakeMechaHead`; `ArtKosM_SSZMTZOrbs`, `Pal_SSZMTZOrbs`) | `sub_575EA` `loc_5775C` | 1 |
| `Obj_SSZEndBoss` (+ `ChildObjDat_7D474…7D492`, `7D4CA`, `7D4D0`; `ArtKosM_MechaSonicExtra`, `PLC_BossExplosion`) | `$79` spawner `loc_45A84` (act 1); `Obj_KnuxFinalBossCrane` `loc_7CB64`→`loc_7CB82` (act 2, with `loc_7D11C`) | 1, 2 |
| `Obj_SSZ2_Boss` (+ `ChildObjDat_7D49A…7D4AE`, `ArtKosM_EndingMasterEmerald`) | In-place pointer swap in `loc_7BBE0`, act 2 only | 2 |
| Crane children (`ChildObjDat_7D4BC/7D4C4`, `Child1_MakeRoboHead4`, `Child1_MakeRoboShipFlame`; `PLC_KnuxFinalBossCrane`, `ArtKosM_KnuxFinalBossCrane`) | `Obj_KnuxFinalBossCrane` (`$B2`) | 2 |
| EggRobo children (`ChildObjDat_919D0/919DE/919E6`; shot gate `V_int_run_count+3 & $F`), `ArtKosM_EggRoboBadnik` re-queue on exit | `Obj_EggRobo` | 1 |
| `Obj_57E96` launch controller; debris `loc_58234/58360/582AC/581F2`; spiral-ramp pieces | `SSZ1_ScreenEvent` stage 0 on `End_of_level_flag` | 1 |
| `loc_59078` camera controller (`$30 = 1`); `loc_591D6` **ending island sprite mask** (`Map_KnuxEndingIslandMask`, 8 child sprites, tracks `Camera_Y_pos_BG_copy`; not a floor collapse) | `SSZ2_ScreenInit`; `SSZ2_ScreenEvent` stage 8 | 2 |
| `loc_85E64` white flash, `loc_85EE6` palette fade, `loc_5E6C0` rescue plane | `loc_7BC70`, `loc_7BCFC` (the last two belong to the ending campaign) | 2 |
