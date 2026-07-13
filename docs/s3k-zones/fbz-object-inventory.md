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
FBZ: placed ring `$00`, monitor `$01`, path swap `$02`, spring `$07`, spikes `$08`, collapsing
bridge `$0F`, auto-spin `$26`, invisible block `$28`, cork floor `$2A`, still
sprite `$2F`, button `$33`, star post `$34`, retracting spring `$3D`, invisible hurt blocks `$6A/$6B`,
hidden monitor `$80`, and special-stage entry ring `$85`.

Every placed FBZ-specific family is currently a `PlaceholderObjectInstance`.
IDs `$A8/$A9` do have factories, but those factories only construct the MHZ
SKL remaps and explicitly return placeholders in FBZ's S3KL set. Shared
Placed `Obj_Ring` `$00` and `Obj_RetractingSpring` `$3D` now use concrete
shared factories. The ring remains distinct from the placement terminator and
the retracting spring wraps the canonical shared spring collision/art/launch
profile while moving its native centre through the ROM `$36/$38/$3A` cycle.

| Act | Concrete shared placements | FBZ-specific placeholders | Other missing shared | Total placeholders |
|---|---:|---:|---:|---:|
| 1 | 136 | 284 | 0 | 284 |
| 2 | 191 | 249 | 0 | 249 |
| Total | 327 | 533 | 0 | 533 |

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

### Task 10 badnik orientation matrix

Subtype and placement orientation are independent inputs. The two Y-word
orientation bits become initial render/status bits; they must not be folded into
the subtype decode.

| Act | Family/subtype | flags 0 | bit 0 | bit 1 | Total |
|---|---|---:|---:|---:|---:|
| 1 | Blaster `$08` | 1 | 0 | 0 | 1 |
| 1 | Blaster `$20` | 3 | 0 | 6 | 9 |
| 2 | Blaster `$20` | 8 | 0 | 4 | 12 |
| 2 | Blaster `$30` | 0 | 0 | 2 | 2 |
| 1 | TechnoSqueek `$00` | 2 | 1 | 0 | 3 |
| 1 | TechnoSqueek `$02` | 5 | 1 | 0 | 6 |
| 1 | TechnoSqueek `$04` | 3 | 1 | 0 | 4 |
| 2 | TechnoSqueek `$00` | 5 | 0 | 0 | 5 |
| 2 | TechnoSqueek `$02` | 14 | 0 | 0 | 14 |
| 2 | TechnoSqueek `$04` | 1 | 6 | 0 | 7 |

Blaster init clears render bit 1 after recording it as the magnetic-ceiling
capability. Therefore the 6+4+2 bit-1 placements are the exact 12 consumers of
the shared FBZ `_unkF7C1` state; the other 12 Blasters never enter that path.
TechnoSqueek subtype `$02` additionally sets render bit 1 during its own init,
but that is its horizontal inverted presentation and is unrelated to Blaster's
magnetic-capability latch.

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
- `Obj_FBZMiniboss` (`146766`) creates seven body/weapon children
  (`ChildObjDat_6FA76`). Each of its two arm controllers independently creates
  a five-link `loc_6F3DE` chain from `word_6FAA2`, for ten links at full
  allocation (not five total); the endpoint links close each arm/chain into a
  cycle. The persistent full graph is 18 slots including the boss, and an
  attack-start palette child (`6FAA8`) temporarily raises it to 19. Defeat
  creates one helper (`6FAB0`), five freed animals (`ChildObjDat_89ED0`), five
  capsule fragments (`ChildObjDat_86B7A`), boss explosions, and music/sign
  helpers. Every after-current table stops on its first allocation failure;
  later independently called defeat tables are still attempted.
- `Obj_FBZ2Subboss` (`148033`) creates four repeated main pieces
  (`ChildObjDat_703C8`), two controller/Robotnik children (`703D0`), a sprite
  mask (`703DE`), two repeated secondary pieces (`703E4`), another child
  (`703EC`), boss explosions, and music/palette/PLC helpers.
- `Obj_Blaster` (`186411`) creates a parent-relative attack-effect slot
  (`ChildObjDat_89726`) and two independent projectile/effect siblings
  (`8972E`, `89746`). The first two allocation attempts occur in that order
  after the 17-update attack wait; `89746` is attempted later only at attack
  `anim_frame==6`. `89726` refreshes from the parent slot but never checks or
  deletes with it; its `$F4` animation callback is its only terminator. Every
  attempt is one-shot with no retry.
- `Obj_TechnoSqueek` (`186710`) makes one one-shot attempt to create its
  persistent parent-owned attached child (`ChildObjDat_89B24`). `$CF` subtype 2
  later creates the detached/falling Blaster and TechnoSqueek entry routines
  through `ChildObjDat_89F16/89F24`; those bodies are independent, and each
  falling TechnoSqueek creates its own `89B24` child before converting in-place
  to normal patrol on landing.
- Both placed families begin behind `Obj_WaitOffscreen`: the first visible
  update only restores their real code, and initialization occurs next frame.
  After-current slots execute later in their creation frame. `89726` initializes
  without drawing; `8972E/89746` initialize, move with signed 8.8 velocities
  integrated into 16.16 positions, apply gravity, animate from frames 5 to 6 and
  7 to 8, then cull/touch that same frame. `89B24` initializes and draws frame 2
  that same frame. After the 33-update turn animation, the parent writes
  `$2E=$10`; the moving routine's `Obj_Wait` reaches zero after 16 updates and
  underflows on update 17, invoking `loc_89926` before the child runs and
  clearing bit 5. Raw-animation `$F4` reaches the same callback on update 93,
  but is redundant because the child already resumed on update 17.
- `Obj_FBZEggPrison` (`187035`) creates its top/door child
  (`ChildObjDat_89EA8`), five freed-animal children (`89EB0`), and boss
  explosions. `Obj_FBZSpringPlunger` is a separate, placed-only family: the
  five `$D0` Act 1 placement records each run their own init/rider/delete path
  and allocate no children (`sonic3k.asm:187094-187119`).
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
  using inline child sprites. Its flames are independent after-current siblings.
  `Obj_FBZSpiderCrane` (`80940`) allocates one independent visual companion only
  after P1 is grabbed. `Obj_FBZMagneticPendulum` (`81111`) allocates a true
  three-slot cascade: placed pivot, endpoint/interactor, then five-link inline
  chain owner. Despite its name, the pendulum never reads the FBZ magnetic bit.

Implementation must preserve each relationship's actual lifetime: structural
children use shared lifetime ownership; launcher missiles, flames, and the
spider visual are independent after-current siblings. Every separate slot needs
rewind recreation/link coverage.

### Dynamic allocation contract

The locked-on child helpers `CreateChild1_Normal`, `CreateChild2_Complex`,
`CreateChild3_NormalRepeated`, `CreateChild4_LinkListRepeated`,
`CreateChild5_ComplexAdjusted`, and `CreateChild6_Simple` all call
`AllocateObjectAfterCurrent` (`sonic3k.asm:176924-177142`), i.e. the ROM
`FindNextFreeObj`/after-parent policy. Direct `AllocateObject` calls use the
global `FindFreeObj` policy. Directly replacing `(a0)` reuses the parent slot.

| Reachable spawn owner | ROM allocation primitive |
|---|---|
| `FBZ1/2_BackgroundInit` outdoor-motion object | `AllocateObject` = `FindFreeObj` |
| `SetUp_FBZ2BossEvent` event control | `AllocateObject` = `FindFreeObj` |
| Boss pillar and ten clouds | `CreateNewSprite4` = `FindNextFreeObj`; cloud address slots retain exact identities |
| End-boss event control -> end boss | `AllocateObject` = `FindFreeObj` |
| Magnetic platform companion; snake segments; rotating-platform group | `AllocateObjectAfterCurrent` = `FindNextFreeObj` |
| Missile companion/projectiles/explosions; wall missiles; elevator cars; independent flames; independent spider visual; pendulum endpoint then chain owner | `AllocateObjectAfterCurrent` = `FindNextFreeObj`; mine and impact missiles replace `(a0)` with `Obj_Explosion` = in-place parent-slot reuse. Pendulum is a three-slot cascade; flames and spider visual are independent siblings. |
| Blaster attack effect/projectiles; TechnoSqueek attached child | `CreateChild1/5` = `FindNextFreeObj`; `89726` refreshes from its parent but self-terminates only at `$F4`, `89B24` parent-checks, and `8972E/89746` are independent siblings; all failures are one-shot/no-retry |
| `$CF` subtype-2 `89F16/89F24` falling badnik entries | prison uses `CreateChild1` = `FindNextFreeObj`; falling bodies are independent, TechnoSqueek makes its own `89B24` with `FindNextFreeObj`, and landing changes `(a0)` to the normal `_2` routine in-place |
| FBZ miniboss child tables `6FA76`, `6FAA8`, `6FAB0`, `89ED0`, `86B7A` | `CreateChild1/6` = `FindNextFreeObj`; song fade uses `AllocateObject` = `FindFreeObj` |
| FBZ2 subboss tables `703C8`, `703D0`, `703DE`, `703E4`, `703EC` | `CreateChild1/3/6` = `FindNextFreeObj`; song fade uses `AllocateObject` = `FindFreeObj` |
| End-boss tables `70EE0`, `70EF4`, `70EFC`, `70F04`, `70F0A`, `70F24`, ship/head/flame, debris | `CreateChild1/3/6` = `FindNextFreeObj`; object-specific transitions that overwrite `(a0)` use parent-slot reuse |
| Egg prison top/door and freed animals | `CreateChild1/6` = `FindNextFreeObj`; destroyed prison/explosion transitions reuse the parent slot |
| Generic final `Obj_EggCapsule` | `AllocateObject` = `FindFreeObj`; it is not the placed `$CF` family |

## Per-family implementation contract

`placement` below means the ordinary placement-loader slot. `FNFO` means
`FindNextFreeObj`/`AllocateObjectAfterCurrent`; `FFO` means global
`FindFreeObj`/`AllocateObject`. “Native pair” means the ROM's P1/P2 loops;
engine extensions must use the task's participation-policy audit without
changing native ordering.

| ID / family | Factory now | Route impact | Art / map / animation | Participation | Allocation | Test owner | Status |
|---|---|---|---|---|---|---|---|
| `$00` Ring | concrete | collectible | shared ring art | all engine players | placement | `TestFbzSharedObjectSubtypes` | concrete; exact placed-object collision/sparkle/lifetime covered |
| `$01` Monitor | concrete | reward | shared monitor PLC/map | native pair/shared policy | placement | `TestFbzSharedPlacedObjects` | concrete; FBZ subtype validation pending |
| `$02` PathSwap | concrete | plane routing | none | each touching player | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$07` Spring | concrete | traversal | shared spring PLC/map | native pair | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$08` Spikes | concrete | hazard | shared map; tile `$200` supplied by AniPLC | native pair | placement | `TestFbzSharedPlacedObjects`, `TestFbzAniPlc` | concrete; ownership validation pending |
| `$0F` CollapsingBridge | concrete | traversal | `Map_FBZCollapsingBridge` | native pair | placement + fragments FNFO | `TestFbzSharedPlacedObjects` | concrete; FBZ parity pending |
| `$26` AutoSpin | concrete | forced traversal | none | native pair | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$28` InvisibleBlock | concrete | collision | none | native pair | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$2A` CorkFloor | concrete | breakable terrain | `Map_FBZCorkFloor` | native pair | placement + debris FNFO | `TestFbzSharedPlacedObjects` | concrete; FBZ parity pending |
| `$2F` StillSprite | concrete | scenery | FBZ level PLC frames | none | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$33` Button | concrete | event trigger | `ArtKosM_FBZButton`/`PLCKosM_FBZ` | native pair | placement | `TestFbzSharedPlacedObjects` | concrete; FBZ linkage pending |
| `$34` StarPost | concrete | checkpoint | shared art | main/native pair per shared contract | placement | `TestFbzSharedPlacedObjects` | concrete; FBZ restore pending |
| `$3D` RetractingSpring | concrete | traversal | shared spring art/map | native pair | placement | `TestFbzSharedObjectSubtypes` | concrete; exact movement/hold/SFX/rewind cycle covered |
| `$6A/$6B` InvisibleHurtBlock | concrete | hazard | none | native pair | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$6F` WireCage | concrete | carrier | player mappings/DPLC selected by `RawAni_3A220`; no standalone FBZ map | native pair/all riders | placement | `TestFbzWireCages` | concrete; counted subtypes and extended participants covered |
| `$70` StationaryWireCage | concrete | solid/trap | inline multi-sprite frames from player mappings; no standalone FBZ map | native pair | placement | `TestFbzWireCages` | concrete; counted subtypes and per-player 16:16 track state covered |
| `$71` FloatingPlatform | concrete | traversal | `Map_FBZFloatingPlatform` | native pair | placement | `TestFbzRailAndChainPlatforms` | concrete; all counted movement modes covered |
| `$72` ChainLink | concrete | grab/carrier | `Map_FBZChainLink` | native pair; one owner per ROM contact | placement | `TestFbzRailAndChainPlatforms` | concrete; vertical/horizontal decode and isolated participant state covered |
| `$73` MagneticSpikeBall | concrete | magnetic-bit mover/static hazard/active field | `Map_FBZMagneticSpikeBall`; narrow field uses tile `$C9`, other forms `$CA` | native P1/P2 extended safely to all | placement | `TestFbzMagneticObjects` | concrete; all 114 placements/subtypes, exact 256-frame bit edges and culls covered |
| `$74` MagneticPlatform | concrete | magnetic-bit traversal + `$8D` hurt | `Map_FBZMagneticPlatform` | native P1/P2 extended safely to all | placement + one companion FNFO with up to eight inline sprites | `TestFbzMagneticObjects` | concrete; all 20 placements/subtypes, 16:16 motion, solid/hurt and chain shape covered |
| `$75` SnakePlatform | concrete | traversal | `Map_FBZSnakePlatform` | native pair riders | placement + 3 segments FNFO | `TestFbzSnakeAndRotatingPlatforms` | concrete; eight routes/four-slot delay train covered |
| `$76` BentPipe | concrete | static full-solid | `Map_FBZBentPipe` | all solid participants | placement | `TestFbzPlayerTransportObjects` | concrete; three exact solid shapes covered |
| `$77` RotatingPlatform | concrete | traversal | `Map_FBZRotatingPlatform` | native pair riders | placement + 1-6 group FNFO | `TestFbzSnakeAndRotatingPlatforms` | concrete; used 6/2 member tables and special first form covered |
| `$78` DEZPlayerLauncher | concrete | forced launch | `Map_FBZDEZPlayerLauncher` | native pair extended to engine sidekicks | placement | `TestFbzPlayerTransportObjects` | concrete; 12-tick acceleration/return covered |
| `$79` DisappearingPlatform | concrete | timed traversal | `Map_FBZDisappearingPlatform` / `Ani_FBZDisappearingPlatform` | native pair riders | placement | `TestFbzDisappearingPlatformAndScrewDoor` | concrete; subtype phase masks/offsets and solid window covered |
| `$7A` ScrewDoor | concrete | gate | `Map_FBZScrewDoor` / `Ani_FBZScrewDoor` | native pair solids | placement | `TestFbzDisappearingPlatformAndScrewDoor` | concrete; every placed trigger/axis/direction row covered |
| `$7B` SpinningPole | concrete | grab/transport | player mappings/DPLC | native pair plus extended sidekicks | placement | `TestFbzPolePropellerPistonAndBlocks` | concrete; scalable participant state covered |
| `$7C` Propeller | concrete | inline blade touch hazard | `Map_FBZPropeller` | collision response list | placement | `TestFbzPolePropellerPistonAndBlocks` | concrete; exact four-phase collision flags covered |
| `$7D` Piston | concrete | horizontal moving solid | `Map_FBZPiston` | all riders | placement | `TestFbzPolePropellerPistonAndBlocks` | concrete; subtype `$28` 320px recurrence covered |
| `$7E` PlatformBlocks | concrete | P1-Y-triggered moving solid | `Map_FBZPlatformBlocks` | full solid; P1 trigger source | placement | `TestFbzPolePropellerPistonAndBlocks` | concrete; every placed width/travel row covered |
| `$7F` MissileLauncher | concrete | projectile hazard | `Map_FBZMissileLauncher` | native touch | placement + companion/projectiles FNFO; impact explosion replacement | `TestFbzMissileObjects` | concrete; cadence/trajectory/failure rules covered |
| `$80` HiddenMonitor | concrete | reward | shared monitor PLC/map | native pair/shared policy | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$85` SSEntryRing | concrete | bonus entry | shared ring art | main/native pair per shared contract | placement | `TestFbzSharedPlacedObjects` | concrete; subtype validation pending |
| `$8A` ExitHall | placeholder | finale traversal | `Map_FBZExitHall`, exit handoff | none/solid scenery | placement | `TestFbzExitHall` | pending |
| `$A8` Blaster | S3KL placeholder (SKL factory exists) | badnik; 24 placements, 12 magnetic ceiling consumers | `Map_Blaster` (11 frames; pieces `4,4,4,1,1,1,1,1,1,1,1`, max tile `$27`), `ArtKosM_Blaster`, raw anim tables | initial velocity tracks P1; attack selects closest native P1/P2 with P1 tie, safely extended to extra sidekicks; continuous ENEMY touch | placement + parent-relative but independently terminating `89726` FNFO + independent `8972E/89746` FNFO; `$CF` falling form owned by Task 10 | `TestFbzBlaster`, `TestFbzBadnikGraphRewind` | pending; exact `101->38` wave and MHZ remap guarded |
| `$A9` TechnoSqueek | S3KL placeholder (SKL factory exists) | badnik; 39 placements; horizontal `$00/$02`, vertical `$04` | `Map_TechnoSqueek` (10 one-piece frames, max base tile `$22`), `ArtKosM_Technosqueek`, raw anim tables | no target/fire routine; continuous ENEMY touch | placement + one parent-owned `89B24` FNFO; `$CF` falling form and its child owned by Task 10 | `TestFbzTechnoSqueek`, `TestFbzBadnikGraphRewind` | pending; exact `101->38` wave and MHZ remap guarded |
| `$AA` Act1 miniboss | placeholder | mandatory boss; one S3KL placement at `$2F00,$05E0`; object-owned camera/plunger arena; scripted six-cycle self-damage | `Map_FBZMiniboss` (18 frames; pieces `4,1,1,2,2,2,2,4,6,6,6,6,6,6,6,6,6,2`), direct `ArtKosM_FBZMiniboss`, `Pal_FBZMiniboss`, shared raw boss-explosion PLC; S3 PLC `$5E` is unused | closest native-pair aimer/terminal touch; P1-only lunge target; only plunger status bit 3 (P1 standing) starts the fight, while P2/extras may ride and collide without setting root bit 0; SKL `$AA` remains Hyudoro | placement + seven-child FNFO table + two five-link cyclic FNFO chains + transient palette FNFO + defeat helper/5 animals/5 fragments FNFO + fade FFO | `TestFbzAct1Miniboss`, `TestFbzMinibossChildren`, `TestFbzMinibossRewind` | pending; completeness decreases exactly one; `$78` music wait fires on wait update 121 and cover `$20/$20/$40` phases consume 33/33/65 wait updates; boss does not set `Events_fg_5`, later level-results flow does |
| `$AB` Act2 subboss | placeholder | mandatory boss | `Map_FBZ2Subboss`, character PLCs/palette | native pair; character-id branch | placement + child tables FNFO + fade FFO | `TestFbzAct2Subboss` | pending |
| `$CE` ExitDoor | placeholder | finale gate | `Map_FBZExitDoor`, exit handoff | native pair solids | placement | `TestFbzExitDoor` | pending |
| `$CF` FBZEggPrison | placeholder | destructible/reward; subtype 2 releases falling badnik forms | `Map_FBZEggCapsule`, `ArtNem_FBZEggCapsule` | native pair attack/touch | placement + top/animals FNFO; subtype-2 `89F16/89F24` integrates Task 10 concrete falling forms; explosion slot reuse | `TestFbzEggPrison` plus Task 10 badnik tests | pending; badnik behavior must not be reimplemented here |
| `$D0` SpringPlunger | placeholder | miniboss/finale traversal | `ObjDat_FBZSpringPlunger` mapping/art attributes | native pair riders | placement only; no child allocation | `TestFbzEggPrison` | pending |
| `$E0` WallMissile | concrete | projectile hazard | `Map_FBZWallMissile` | native touch | placement + parentless missiles FNFO | `TestFbzMissileObjects` | concrete; on-screen cadence/muzzle lockout covered |
| `$E1` Mine | concrete | ordered proximity/blink/hurt/explosion hazard | `Map_FBZMine` | ordered all-engine extension of native P1/P2 | placement; explosion in-place | `TestFbzMine` | concrete; all 60 placements and same-slot lifecycle covered |
| `$E2` Elevator | placeholder | traversal | `Map_FBZElevator` | native pair riders | placement + car FNFO | `TestFbzElevator` | pending |
| `$E3` TrapSpring | concrete | forced launch | `Map_FBZTrapSpring` / `Ani_FBZTrapSpring` | P1 visual selector; native P1/P2 launch extended safely to all riders | placement | `TestFbzTrapSpring` | concrete; all 7 placements, both subtypes and exact terminal animation scripts covered |
| `$E4` Flamethrower | concrete | solid trap + independent fire hazards | `Map_FBZFlameThrower`; `ArtTile_FBZMisc+$A4` | scalable rider set | placement + independent flames FNFO + inline nozzles | `TestFbzFlamethrower` | concrete; all 20 placements/subtypes, 17-update flames and fire-shield reaction covered |
| `$E5` SpiderCrane | concrete | strict-P1 grab/carrier | `Map_FBZSpiderCrane` | `MAIN_ONLY_NATIVE` | placement + independent visual companion FNFO after grab | `TestFbzSpiderCraneAndPendulum` | concrete; both placements, single allocation attempt and release path covered |
| `$FF` MagneticPendulum | concrete | strict-P1 three-slot grab/swing/launcher; does not consume magnetic bit | `Map_FBZMagneticPendulum` | `MAIN_ONLY_NATIVE` / Ctrl1 | pivot + endpoint + five-link inline chain owner FNFO | `TestFbzSpiderCraneAndPendulum` | concrete; all 6 placements, 8.8 swing phase, cascade and Clank/Jump cues covered |

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

## Audio cue matrix

All rows are reachable from a placed or dynamic FBZ family. Line references are
to the locked-on `sonic3k.asm`; shared monitor, spring, spike, star-post, ring,
and destruction sounds retain their shared-family test ownership.

| Owner/routine | Cue | Edge | Test owner |
|---|---|---|---|
| Chain Link grab/release paths (`78520`, `78645`, `78792`) | `sfx_Grab` | one-shot per successful grab edge | `TestFbzRailAndChainPlatforms` |
| Magnetic Spike Ball (`78896`) | `sfx_MagneticSpike` | one-shot polarity/motion edge | `TestFbzMagneticObjects` |
| Magnetic Platform (`79005`) | `sfx_ChainTension` | one-shot tension edge | `TestFbzMagneticObjects` |
| DEZ Player Launcher (`79475`) | `sfx_FloorLauncher` | one-shot launch | `TestFbzPlayerTransportObjects` |
| Screw Door (`79653`) | `sfx_DoorOpen` | one-shot state transition | `TestFbzDisappearingPlatformAndScrewDoor` |
| Missile Launcher (`80199`, `80255`, `80346`) | `sfx_LevelProjectile`, `sfx_TubeLauncher`, `sfx_Explode` | one-shot fire/launch/impact | `TestFbzMissileObjects` |
| Wall Missile (`80392`) | `sfx_LevelProjectile` | one-shot fire | `TestFbzMissileObjects` |
| Mine (`80499`) | `sfx_Explode` | one-shot detonation | `TestFbzMine` |
| Trap Spring (`80648`) | `sfx_Spring` | one-shot launch | `TestFbzTrapSpring` |
| Flamethrower (`80723`, `80762`) | `sfx_FlamethrowerLoud` | retriggered by active flame cadence; stop on inactive routine | `TestFbzFlamethrower` |
| Magnetic Pendulum endpoint/release (`81248`, `81379`) | `sfx_Clank`, `sfx_Jump` | one-shot at clamped endpoint / manual P1 release | `TestFbzSpiderCraneAndPendulum` |
| Act 1 miniboss init/defeat (`146833-146871`) | `cmd_FadeOut`, `mus_Miniboss`, fade-to-level controller | one-shot transitions | `TestFbzAct1Miniboss` |
| Act 1 miniboss (`147548`, `147902`) | `sfx_MechaLand`, `sfx_BossHit` | one-shot landing/hit | `TestFbzAct1Miniboss` |
| Act 2 subboss (`148071-148222`, `148438`, `148458`, `148573`) | `mus_Miniboss`, fade-to-level, `sfx_Charging`, `sfx_BossLaser`, `sfx_BossHit` | transition or one-shot attack/hit edge | `TestFbzAct2Subboss` |
| End boss init (`148703-148708`) | `cmd_FadeOut`, `mus_EndBoss` | one-shot transition | `TestFbzEndBossAudioAndPlc` |
| End boss (`149196`, `149248`, `149264`, `149470`) | `sfx_FlamethrowerQuiet`, `sfx_FloorThump`, `sfx_SpikeBalls`, `sfx_BossHit` | routine-timed one-shots; quiet flame follows attack cadence | `TestFbzEndBossAudioAndPlc` |
| Egg Prison (`187219`) | `sfx_RingLoss` | one-shot prison break/release edge | `TestFbzEggPrison` |

## Mapping, animation, art, and PLC address manifest

`RomOffsetFinder --game s3k search FBZ` was run on 2026-07-12. The entries
below are the locked-on `sonic3k.asm` / S&K-side results. Included compressed
binary labels do not receive a calculated ROM offset from the finder; that is
recorded as `included-binary` rather than substituting an S3-half address.

| Family | Mapping / animation (S&K ROM offset) | Art / PLC ownership (S&K side) |
|---|---|---|
| Shared FBZ bridge/cork | `Map_FBZCollapsingBridge` `$2108E`; `Map_FBZCorkFloor` `$2A920` | level PLC `$1A/$1B` or `$1C/$1D` |
| Floating/chain/magnetic | `Map_FBZFloatingPlatform` `$3A742`; `Map_FBZChainLink` `$3AD8A`; `Map_FBZMagneticSpikeBall` `$3B25C`; `Map_FBZMagneticPlatform` `$3B4DE`; `Map_FBZMagneticPendulum` `$3D9AE` | `ArtNem_FBZMisc`, included-binary, PLC `$1A-$1D` |
| Snake/bent/rotating/launcher | `Map_FBZSnakePlatform` `$3B6CE`; `Map_FBZBentPipe` `$3B73C`; `Map_FBZRotatingPlatform` `$3B91A`; `Map_FBZDEZPlayerLauncher` `$3BA8A` | `ArtNem_FBZMisc`, included-binary, PLC `$1A-$1D` |
| Disappearing/screw/pole/propeller | `Ani_FBZDisappearingPlatform` `$3BB9A`; `Map_FBZDisappearingPlatform` `$3BBBE`; `Ani_FBZScrewDoor` `$3BD5E`; `Map_FBZScrewDoor` `$3BD8E`; `Map_FBZSpinningPole` `$3C19C`; `Map_FBZPropeller` `$3C20C` | `ArtNem_FBZMisc`, S&K included-binary at `sonic3k.asm:201450`, PLC `$1A/$1B` and `$1C/$1D` |
| Piston/blocks/missiles/mine | `Map_FBZPiston` `$3C328`; `Map_FBZPlatformBlocks` `$3C416`; `Map_FBZMissileLauncher` `$3C78E`; `Map_FBZWallMissile` `$3C906`; `Map_FBZMine` `$3CA06` | `ArtNem_FBZMisc` / `ArtNem_FBZMisc2`, S&K included-binary at `sonic3k.asm:201450/201456`, level PLC `$1A-$1D` |
| Elevator/trap/flame/spider | `Map_FBZElevator` `$3CB0C`; `Ani_FBZTrapSpring` `$3CC4C`; `Map_FBZTrapSpring` `$3CC5A`; `Map_FBZFlameThrower` `$3CFD0`; `Map_FBZSpiderCrane` `$3D2FC` | trap/spider use `ArtNem_FBZMisc2`; flamethrower uses `ArtNem_FBZMisc` (`ArtTile_FBZMisc+$A4`); S&K included binaries at `sonic3k.asm:201450/201456`, PLC `$1C/$1D` |
| Act 1 miniboss | `Map_FBZMiniboss` `$6FAF8` | `ArtKosM_FBZMiniboss`, included-binary; queued at spawn |
| Act 2 subboss | `Map_FBZ2Subboss` `$70440`; `Map_FBZRobotnikRun` `$6837E`; `Map_FBZRobotnikHead` `$68454`; `Map_FBZRobotnikStand` `$6847C` | `PLC_FBZ2Subboss_SonicTails` / `PLC_FBZ2Subboss_Knuckles`; `ArtNem_FBZ2Subboss` and character art, S&K included-binary |
| Boss-event scenery | `Map_FBZ2Preboss` `$53518` | `ArtKosM_FBZCloud`, `ArtKosM_FBZBossPillar`, included-binary; `PLCKosM_FBZ2Subboss` |
| End boss/exit | `Map_FBZEndBoss` `$70FB4`; `Map_FBZEndBossFlame` `$71090`; `Map_FBZExitDoor` `$70F7E`; `Map_FBZExitHall` `$86D2A` | `PLC_6F`; `PLCKosM_FBZEndBoss_Exit`; `ArtKosM_FBZExitDoor` / `ArtKosM_FBZExitHall` S&K included-binary at `sonic3k.asm:201670/201658` |
| Egg prison/capsule | `Map_FBZEggCapsule` `$1871E8` | `ArtNem_FBZEggCapsule`, included-binary, PLC `$1A-$1D` |
| AniPLC channels | `AniPLC_FBZ1` / `AniPLC_FBZ2` at `sonic3k.asm:55812-55882` | `ArtUnc_AniFBZ__0..4`, included-binary; destinations `$210`, `$230`, `$238`, `$200`, `$208` |
| Blaster | `Map_Blaster` `$8977C`, S&K include at `sonic3k.asm:186706`; 11 frames, piece counts `4,4,4,1,1,1,1,1,1,1,1`, max tile `$27`; raw animations `byte_8975E`, `89763`, `89768`, `89771`, `89775` | `ArtKosM_Blaster` `$DC6C2`, S&K included-binary at `sonic3k.asm:201024`; palette 1/high plane priority; `PLCKosM_FBZ` entry at `64387`, `ArtTile_Blaster=$506` |
| TechnoSqueek | `Map_TechnoSqueek` `$89B78`, S&K include at `sonic3k.asm:187031`; 10 one-piece frames, max base tile `$22`; raw animation tables `byte_89B2C`, `89B37`, `89B42`, `89B4D`, `89B52`, `89B5D`, `89B68`, `89B73` | `ArtKosM_Technosqueek` `$DC9C4`, S&K included-binary at `sonic3k.asm:201027`; palette 1/high plane priority; `PLCKosM_FBZ` entry at `64388`, `ArtTile_Technosqueek=$52E` |
| Wire cages | no standalone mapping: `Obj_FBZWireCage` uses player mappings/DPLC plus `RawAni_3A220`; stationary form builds inline child sprites (`sonic3k.asm:77585-78154`) | player art banks; no independent PLC label |
| Button | mapping comes from the FBZ misc object routine/level-art bank | `ArtKosM_FBZButton`, S&K included-binary at `sonic3k.asm:201676`; `PLCKosM_FBZ` entry at `64389` |
| Robotnik/EggRobo character art | Robotnik run `$6837E`, head `$68454`, stand `$6847C`; shared EggRobo mappings resolved by their generic labels | `ArtNem_FBZRobotnikStand`, `ArtNem_FBZRobotnikRun`, `ArtNem_EggRoboStand`, `ArtNem_EggRoboRun`; S&K included-binary PLC entries `148683-148695` |
| End-boss ship/explosion/capsule shared dependencies | shared `Map_RobotnikShip`, `Map_BossExplosion`, generic `Map_EggCapsule` labels | `ArtNem_RobotnikShip`, `ArtNem_BossExplosion`, `ArtNem_EggCapsule` in `PLC_6F` (`sonic3k.asm:199991-199997`); separate placed FBZ prison uses `Map_FBZEggCapsule` |

## VRAM / PLC handoff matrix

| Stage | Required ordered load | Ownership/result | Test owner |
|---|---|---|---|
| Act 1 load | PLC `$1A/$1B` | misc, outdoors, egg-prison/capsule art; AniPLC owns `$200-$247` | `TestFbzArtAndPlcRegistry`, `TestFbzAniPlc` |
| Seamless Act 2 reload | PLC `$1C/$1D` | misc1 -> misc2 and egg-prison/capsule art; AniPLC remains sole `$200-$247` writer | `TestFbzActTransition`, `TestFbzAniPlc` |
| Act 1 miniboss entry | `ArtKosM_FBZMiniboss` plus shared boss-explosion PLC | temporary miniboss bank | `TestFbzAct1Miniboss` |
| Act 2 subboss entry | character-selected `PLC_FBZ2Subboss_*` | Robotnik art for Sonic/Tails; EggRobo art for Knuckles | `TestFbz2SubbossCharacterArt` |
| Act 2 subboss defeat | `PLCKosM_FBZ2Subboss`, then `PLC_Monitors`, then `PLC_MonitorsSpikesSprings` | cloud/pillar queue followed by restoration of monitor and spike/spring banks in ROM order | `TestFbz2SubbossArtHandoff` |
| End boss spawn | PLC `$6F` | end boss, head, flame, ship, explosions, capsule | `TestFbzEndBossAudioAndPlc` |
| End boss aftermath | `PLCKosM_FBZEndBoss_Exit` | exit door and exit hall art; must complete before consumers render | `TestFbzExitDoor`, `TestFbzExitHall` |
