# Flying Battery Zone object inventory

This is a planning inventory for the locked-on S3K FBZ object layouts. It does
not describe implemented behavior.

## Sources and count convention

- Placements: `docs/skdisasm/Levels/FBZ/Object Pos/1.bin` and `2.bin`.
- Names: `docs/skdisasm/Levels/Misc/Object pointers - SK Set 1.asm`
  (`Sprite_Listing3`, the S3KL table used by zones 0-6).
- Record decoding: `CommonPlacementParser.parseObjectRecords`: six-byte records,
  with X=`$FFFF` terminating the list.

| Act | File bytes | Six-byte records | Terminators | Actual spawns | IDs |
|---|---:|---:|---:|---:|---:|
| 1 | 2526 | 421 | 1 | 420 | 39 |
| 2 | 2646 | 441 | 1 | 440 | 37 |
| Total | 5172 | 862 | 2 | 860 | 44 distinct across both acts |

The often-quoted 421/441 and 862 totals include the final
`FFFF 0000 0000` record in each file. Runtime spawn totals are 420/440 and
860. The terminators must not be counted as two subtype-0 `Obj_Ring` records.

## Live implementation status

`Sonic3kObjectRegistry` has concrete factories for the shared families used by
FBZ: monitor `$01`, path swap `$02`, spring `$07`, spikes `$08`, collapsing
bridge `$0F`, auto-spin `$26`, invisible block `$28`, cork floor `$2A`, still
sprite `$2F`, button `$33`, star post `$34`, invisible hurt blocks `$6A/$6B`,
hidden monitor `$80`, and special-stage entry ring `$85`.

Every placed FBZ-specific family is currently a `PlaceholderObjectInstance`.
IDs `$A8/$A9` do have factories, but those factories only construct the MHZ
SKL remaps and explicitly return placeholders in FBZ's S3KL set. Shared
`Obj_Ring` `$00` and `Obj_RetractingSpring` `$3D` also lack factories.

| Act | Concrete shared placements | FBZ-specific placeholders | Other missing shared | Total placeholders |
|---|---:|---:|---:|---:|
| 1 | 136 | 284 | 0 | 284 |
| 2 | 189 | 249 | 2 (`$00`, `$3D`) | 251 |
| Total | 325 | 533 | 2 | 535 |

No FBZ end-boss `$AC` placement exists because the screen event dynamically
creates it. It is also unimplemented.

## Act 1 exact placement matrix

Subtype entries are `subtype=count`.

| ID | S3KL name | Total | Subtypes |
|---|---|---:|---|
| `$01` | `Obj_Monitor` | 11 | `$01`=3, `$03`=3, `$05`=1, `$06`=2, `$08`=2 |
| `$02` | `Obj_PathSwap` | 31 | `$02`=1, `$09`=8, `$0D`=3, `$11`=7, `$12`=1, `$15`=10, `$91`=1 |
| `$07` | `Obj_Spring` | 15 | `$00`=2, `$02`=9, `$10`=3, `$20`=1 |
| `$08` | `Obj_Spikes` | 21 | `$00`=9, `$03`=2, `$10`=2, `$20`=3, `$40`=5 |
| `$0F` | `Obj_CollapsingBridge` | 1 | `$00`=1 |
| `$26` | `Obj_AutoSpin` | 2 | `$04`=2 |
| `$28` | `Obj_InvisibleBlock` | 20 | `$11`=5, `$21`=3, `$22`=8, `$31`=4 |
| `$2F` | `Obj_StillSprite` | 5 | `$28`=2, `$29`=2, `$2A`=1 |
| `$33` | `Obj_Button` | 4 | `$20`=2, `$21`=1, `$22`=1 |
| `$34` | `Obj_StarPost` | 5 | `$01`=1, `$02`=1, `$03`=1, `$04`=1, `$05`=1 |
| `$6A` | `Obj_InvisibleHurtBlockHorizontal` | 1 | `$71`=1 |
| `$6B` | `Obj_InvisibleHurtBlockVertical` | 16 | `$11`=2, `$13`=1, `$41`=10, `$F1`=3 |
| `$6F` | `Obj_FBZWireCage` | 6 | `$10`=6 |
| `$70` | `Obj_FBZWireCageStationary` | 9 | `$00`=2, `$01`=5, `$02`=2 |
| `$71` | `Obj_FBZFloatingPlatform` | 20 | `$00`=2, `$10`=6, `$20`=3, `$30`=2, `$38`=2, `$41`=1, `$45`=1, `$46`=1, `$49`=1, `$4F`=1 |
| `$72` | `Obj_FBZChainLink` | 10 | `$0F`=1, `$14`=1, `$1B`=1, `$83`=1, `$84`=1, `$88`=2, `$C3`=1, `$C7`=1, `$C8`=1 |
| `$73` | `Obj_FBZMagneticSpikeBall` | 52 | `$00`=8, `$01`=2, `$80`=21, `$81`=21 |
| `$74` | `Obj_FBZMagneticPlatform` | 7 | `$0F`=7 |
| `$75` | `Obj_FBZSnakePlatform` | 8 | `$00`=1, `$01`=1, `$02`=1, `$03`=1, `$04`=1, `$05`=1, `$06`=1, `$07`=1 |
| `$76` | `Obj_FBZBentPipe` | 32 | `$00`=8, `$01`=16, `$02`=8 |
| `$77` | `Obj_FBZRotatingPlatform` | 6 | `$00`=3, `$0C`=3 |
| `$78` | `Obj_FBZDEZPlayerLauncher` | 6 | `$00`=6 |
| `$79` | `Obj_FBZDisappearingPlatform` | 7 | `$79`=1, `$99`=1, `$B9`=2, `$D9`=2, `$F9`=1 |
| `$7A` | `Obj_FBZScrewDoor` | 4 | `$11`=1, `$12`=1, `$20`=1, `$50`=1 |
| `$7B` | `Obj_FBZSpinningPole` | 8 | `$0C`=2, `$0E`=2, `$14`=2, `$1A`=2 |
| `$7C` | `Obj_FBZPropeller` | 11 | `$00`=11 |
| `$7D` | `Obj_FBZPiston` | 1 | `$28`=1 |
| `$7E` | `Obj_FBZPlatformBlocks` | 10 | `$00`=1, `$02`=1, `$14`=8 |
| `$7F` | `Obj_FBZMissileLauncher` | 10 | `$02`=5, `$72`=3, `$F2`=2 |
| `$80` | `Obj_HiddenMonitor` | 2 | `$05`=1, `$06`=1 |
| `$85` | `Obj_SSEntryRing` | 2 | `$01`=1, `$02`=1 |
| `$A8` | `Obj_Blaster` | 10 | `$08`=1, `$20`=9 |
| `$A9` | `Obj_TechnoSqueek` | 13 | `$00`=3, `$02`=6, `$04`=4 |
| `$AA` | `Obj_FBZMiniboss` | 1 | `$00`=1 |
| `$CF` | `Obj_FBZEggPrison` | 6 | `$00`=1, `$01`=3, `$02`=2 |
| `$D0` | `Obj_FBZSpringPlunger` | 5 | `$00`=5 |
| `$E0` | `Obj_FBZWallMissile` | 2 | `$10`=1, `$20`=1 |
| `$E1` | `Obj_FBZMine` | 32 | `$00`=32 |
| `$E4` | `Obj_FBZFlamethrower` | 8 | `$00`=1, `$02`=1, `$03`=3, `$40`=1, `$80`=2 |

Act 1 table sum: **420**.

## Act 2 exact placement matrix

| ID | S3KL name | Total | Subtypes |
|---|---|---:|---|
| `$00` | `Obj_Ring` | 1 | `$00`=1 |
| `$01` | `Obj_Monitor` | 7 | `$01`=1, `$03`=2, `$05`=1, `$06`=2, `$08`=1 |
| `$02` | `Obj_PathSwap` | 15 | `$01`=1, `$09`=2, `$0A`=1, `$0E`=3, `$11`=1, `$16`=2, `$21`=1, `$61`=1, `$91`=3 |
| `$07` | `Obj_Spring` | 6 | `$00`=1, `$02`=3, `$04`=1, `$10`=1 |
| `$08` | `Obj_Spikes` | 89 | `$00`=8, `$10`=3, `$30`=1, `$40`=77 |
| `$0F` | `Obj_CollapsingBridge` | 2 | `$00`=2 |
| `$26` | `Obj_AutoSpin` | 5 | `$04`=4, `$80`=1 |
| `$28` | `Obj_InvisibleBlock` | 6 | `$17`=1, `$41`=1, `$61`=4 |
| `$2A` | `Obj_CorkFloor` | 2 | `$10`=2 |
| `$2F` | `Obj_StillSprite` | 9 | `$28`=3, `$29`=2, `$2C`=4 |
| `$33` | `Obj_Button` | 16 | `$20`=1, `$21`=1, `$22`=1, `$23`=1, `$24`=1, `$25`=1, `$26`=1, `$27`=1, `$28`=1, `$29`=1, `$2A`=1, `$2B`=1, `$2C`=1, `$2D`=1, `$2F`=2 |
| `$34` | `Obj_StarPost` | 6 | `$01`=1, `$02`=1, `$03`=1, `$04`=1, `$05`=1, `$06`=1 |
| `$3D` | `Obj_RetractingSpring` | 1 | `$04`=1 |
| `$6A` | `Obj_InvisibleHurtBlockHorizontal` | 2 | `$71`=2 |
| `$6B` | `Obj_InvisibleHurtBlockVertical` | 22 | `$13`=1, `$41`=11, `$51`=4, `$61`=6 |
| `$6F` | `Obj_FBZWireCage` | 7 | `$18`=2, `$98`=3, `$A4`=1, `$A6`=1 |
| `$71` | `Obj_FBZFloatingPlatform` | 2 | `$00`=1, `$4F`=1 |
| `$72` | `Obj_FBZChainLink` | 10 | `$05`=1, `$12`=1, `$16`=1, `$1B`=2, `$83`=2, `$C3`=3 |
| `$73` | `Obj_FBZMagneticSpikeBall` | 62 | `$00`=6, `$80`=28, `$81`=28 |
| `$74` | `Obj_FBZMagneticPlatform` | 13 | `$0E`=4, `$0F`=9 |
| `$78` | `Obj_FBZDEZPlayerLauncher` | 5 | `$00`=5 |
| `$79` | `Obj_FBZDisappearingPlatform` | 4 | `$89`=1, `$A9`=1, `$C9`=1, `$E9`=1 |
| `$7A` | `Obj_FBZScrewDoor` | 18 | `$0A`=1, `$10`=1, `$11`=1, `$12`=1, `$14`=1, `$16`=1, `$18`=1, `$19`=1, `$1A`=1, `$1B`=1, `$1F`=2, `$2D`=1, `$43`=1, `$4C`=1, `$55`=2, `$57`=1 |
| `$7E` | `Obj_FBZPlatformBlocks` | 7 | `$00`=3, `$14`=4 |
| `$85` | `Obj_SSEntryRing` | 2 | `$03`=1, `$04`=1 |
| `$8A` | `Obj_FBZExitHall` | 11 | `$00`=2, `$04`=9 |
| `$A8` | `Obj_Blaster` | 14 | `$20`=12, `$30`=2 |
| `$A9` | `Obj_TechnoSqueek` | 26 | `$00`=5, `$02`=14, `$04`=7 |
| `$AB` | `Obj_FBZ2Subboss` | 1 | `$00`=1 |
| `$CE` | `Obj_FBZExitDoor` | 1 | `$00`=1 |
| `$CF` | `Obj_FBZEggPrison` | 1 | `$02`=1 |
| `$E1` | `Obj_FBZMine` | 28 | `$00`=28 |
| `$E2` | `Obj_FBZElevator` | 12 | `$0F`=1, `$1E`=1, `$24`=2, `$25`=1, `$32`=1, `$37`=3, `$3B`=1, `$4B`=2 |
| `$E3` | `Obj_FBZTrapSpring` | 7 | `$00`=6, `$02`=1 |
| `$E4` | `Obj_FBZFlamethrower` | 12 | `$00`=1, `$02`=3, `$80`=8 |
| `$E5` | `Obj_FBZSpiderCrane` | 2 | `$2C`=2 |
| `$FF` | `Obj_FBZMagneticPendulum` | 6 | `$00`=4, `$80`=2 |

Act 2 table sum: **440**.

## Dynamic-spawn graph from the disassembly

These objects are absent from placement counts and must be included in object
family implementation/tests. Line references are to `docs/skdisasm/sonic3k.asm`.

- `FBZ2_ScreenEvent` / `SetUp_FBZ2BossEvent` (`109329-109507`) creates
  `Obj_FBZEndBossEventControl`, `Obj_FBZBossPillar`, and ten `Obj_FBZCloud`
  objects. The event controller creates `Obj_FBZEndBoss` (`109825-109884`).
- `Obj_FBZEndBoss` (`148698`) creates `Obj_FBZRobotnikShip`, two lateral
  weapon/arm children plus a central child (`ChildObjDat_70EE0`), further
  internal flame/arm/debris groups (`ChildObjDat_70EF4`, `70EFC`, `70F04`,
  `70F0A`, `70F24`), boss explosions, a song-fade controller, `Obj_EggCapsule`,
  and camera-bound helpers. The Robotnik ship creates its Robotnik head/flame
  children through the shared Robotnik child tables (`136500-136704`).
- `Obj_FBZMiniboss` (`146766`) creates seven linked body/weapon children
  (`ChildObjDat_6FA76`), an additional child (`6FAA8`), a helper (`6FAB0`),
  five capsule-animal-like children (`ChildObjDat_89ED0`), five shared capsule
  children (`ChildObjDat_86B7A`), boss explosions, and music helpers.
- `Obj_FBZ2Subboss` (`148033`) creates four repeated main pieces
  (`ChildObjDat_703C8`), two controller/Robotnik children (`703D0`), a sprite
  mask (`703DE`), two repeated secondary pieces (`703E4`), another child
  (`703EC`), boss explosions, and music/palette/PLC helpers.
- `Obj_Blaster` (`186411`) creates an attack-effect child plus two badnik
  projectile forms (`ChildObjDat_89726`, `8972E`, `89746`).
- `Obj_TechnoSqueek` (`186710`) creates a persistent attached child
  (`ChildObjDat_89B24`); detached/falling routines create the same child.
- `Obj_FBZEggPrison` (`187035`) creates its top/door child
  (`ChildObjDat_89EA8`), five freed-animal children (`89EB0`), and boss
  explosions. Placed `Obj_FBZSpringPlunger` objects are a separate linked
  family, not entries generated by the placement parser.
- `Obj_FBZMagneticPlatform` (`78926`) allocates a tall multi-sprite chain/field
  companion; `Obj_FBZSnakePlatform` (`79081`) allocates three additional
  segments; `Obj_FBZRotatingPlatform` (`79276`) expands to subtype-selected
  groups of 1-6 platform/connector objects.
- `Obj_FBZMissileLauncher` (`80110`) may allocate a launcher companion and
  repeatedly allocates missile children; missiles transition to explosions.
  `Obj_FBZWallMissile` (`80354`) repeatedly allocates horizontal missile
  children; `Obj_FBZMine` transitions itself to `Obj_Explosion`.
- `Obj_FBZElevator` (`80507`) periodically allocates elevator-car objects.
  `Obj_FBZFlamethrower` (`80659`) allocates damaging flame objects while also
  using inline child sprites. `Obj_FBZSpiderCrane` (`80940`) allocates its
  grabbing/claw companion. `Obj_FBZMagneticPendulum` (`81111`) allocates its
  linked pendulum companion.

Implementation should use `spawnChild`/shared lifetime ownership for these
relationships and add rewind recreation/link coverage for every separate slot.

## Reproduction

The inventory was generated by reading each file in six-byte steps, stopping
only for runtime counts when decoded X equals `$FFFF`, grouping byte 4 as ID and
byte 5 as subtype, and resolving byte 4 through the commented hexadecimal
indices in `Object pointers - SK Set 1.asm`.

Useful independent checks:

```powershell
Get-Item 'docs/skdisasm/Levels/FBZ/Object Pos/1.bin', `
  'docs/skdisasm/Levels/FBZ/Object Pos/2.bin' | Select-Object Name,Length
# 2526 / 6 = 421; 2646 / 6 = 441

Format-Hex 'docs/skdisasm/Levels/FBZ/Object Pos/1.bin' | Select-Object -Last 2
Format-Hex 'docs/skdisasm/Levels/FBZ/Object Pos/2.bin' | Select-Object -Last 2
# Both tails end in FF FF 00 00 00 00.
```
