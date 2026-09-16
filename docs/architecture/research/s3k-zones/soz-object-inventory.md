# SOZ placed-object inventory

Initial inventory at `2b2bf8e28`, 2026-09-15. Locked-on ROM SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`; source submodule
`1a454a0e335137a1a016d1090a9f4528d36944cf` (working reference has unrelated edits).

Placement binaries match ROM bytes at `$1F4866` (3600 bytes, Act 1) and
`$1F5676` (2946 bytes, Act 2). Each ends with one six-byte terminator;
live counts are **599 + 490 = 1089**. IDs and subtypes below are hexadecimal.
The older analysis object table uses decimal IDs. Pointer names come from
`Object pointers - SK Set 2.asm`; pointer set is SKL, independent of art half.

Factory column is the current discovery-profile classification, not execution
or route certification. Concrete shared families still need their SOZ data and
subtypes tested through production. The initial SKL-only factory is `$14`
(not placed here). Dynamic children/art/audio ownership remain an explicit
inventory obligation before implementing each family.

| ID | Subtype | ROM pointer owner | Act 1 | Act 2 | Current factory |
| --- | --- | --- | ---: | ---: | --- |
| `$01` | `$01` | `Obj_Monitor` | 2 | 1 | shared concrete |
| `$01` | `$03` | `Obj_Monitor` | 21 | 8 | shared concrete |
| `$01` | `$04` | `Obj_Monitor` | 0 | 1 | shared concrete |
| `$01` | `$05` | `Obj_Monitor` | 3 | 2 | shared concrete |
| `$01` | `$06` | `Obj_Monitor` | 2 | 2 | shared concrete |
| `$01` | `$07` | `Obj_Monitor` | 1 | 2 | shared concrete |
| `$01` | `$08` | `Obj_Monitor` | 5 | 1 | shared concrete |
| `$02` | `$00` | `Obj_PathSwap` | 0 | 1 | shared concrete |
| `$02` | `$01` | `Obj_PathSwap` | 1 | 1 | shared concrete |
| `$02` | `$05` | `Obj_PathSwap` | 0 | 1 | shared concrete |
| `$02` | `$06` | `Obj_PathSwap` | 8 | 4 | shared concrete |
| `$02` | `$08` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$02` | `$09` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$02` | `$0D` | `Obj_PathSwap` | 1 | 3 | shared concrete |
| `$02` | `$0E` | `Obj_PathSwap` | 2 | 3 | shared concrete |
| `$02` | `$10` | `Obj_PathSwap` | 0 | 1 | shared concrete |
| `$02` | `$11` | `Obj_PathSwap` | 1 | 1 | shared concrete |
| `$02` | `$12` | `Obj_PathSwap` | 1 | 2 | shared concrete |
| `$02` | `$15` | `Obj_PathSwap` | 0 | 2 | shared concrete |
| `$02` | `$22` | `Obj_PathSwap` | 2 | 2 | shared concrete |
| `$02` | `$25` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$02` | `$26` | `Obj_PathSwap` | 1 | 2 | shared concrete |
| `$02` | `$27` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$02` | `$30` | `Obj_PathSwap` | 0 | 2 | shared concrete |
| `$02` | `$42` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$02` | `$88` | `Obj_PathSwap` | 2 | 2 | shared concrete |
| `$02` | `$90` | `Obj_PathSwap` | 8 | 6 | shared concrete |
| `$07` | `$00` | `Obj_Spring` | 0 | 1 | shared concrete |
| `$07` | `$01` | `Obj_Spring` | 3 | 0 | shared concrete |
| `$07` | `$02` | `Obj_Spring` | 0 | 7 | shared concrete |
| `$07` | `$03` | `Obj_Spring` | 5 | 4 | shared concrete |
| `$07` | `$10` | `Obj_Spring` | 7 | 0 | shared concrete |
| `$07` | `$12` | `Obj_Spring` | 10 | 8 | shared concrete |
| `$07` | `$20` | `Obj_Spring` | 2 | 1 | shared concrete |
| `$07` | `$A0` | `Obj_Spring` | 0 | 3 | shared concrete |
| `$08` | `$00` | `Obj_Spikes` | 2 | 2 | shared concrete |
| `$08` | `$01` | `Obj_Spikes` | 1 | 0 | shared concrete |
| `$08` | `$10` | `Obj_Spikes` | 9 | 7 | shared concrete |
| `$08` | `$11` | `Obj_Spikes` | 7 | 2 | shared concrete |
| `$08` | `$20` | `Obj_Spikes` | 2 | 1 | shared concrete |
| `$08` | `$30` | `Obj_Spikes` | 1 | 1 | shared concrete |
| `$08` | `$40` | `Obj_Spikes` | 16 | 8 | shared concrete |
| `$0D` | `$00` | `Obj_BreakableWall` | 7 | 11 | shared concrete |
| `$0D` | `$04` | `Obj_BreakableWall` | 4 | 5 | shared concrete |
| `$0E` | `$00` | `Obj_TwistedRamp` | 6 | 0 | shared concrete |
| `$0F` | `$02` | `Obj_CollapsingBridge` | 9 | 0 | shared concrete |
| `$0F` | `$04` | `Obj_CollapsingBridge` | 3 | 2 | shared concrete |
| `$0F` | `$06` | `Obj_CollapsingBridge` | 1 | 4 | shared concrete |
| `$0F` | `$08` | `Obj_CollapsingBridge` | 1 | 0 | shared concrete |
| `$26` | `$30` | `Obj_AutoSpin` | 10 | 8 | shared concrete |
| `$28` | `$00` | `Obj_InvisibleBlock` | 2 | 2 | shared concrete |
| `$28` | `$01` | `Obj_InvisibleBlock` | 3 | 1 | shared concrete |
| `$28` | `$02` | `Obj_InvisibleBlock` | 2 | 0 | shared concrete |
| `$28` | `$04` | `Obj_InvisibleBlock` | 0 | 1 | shared concrete |
| `$28` | `$05` | `Obj_InvisibleBlock` | 0 | 1 | shared concrete |
| `$28` | `$10` | `Obj_InvisibleBlock` | 3 | 0 | shared concrete |
| `$28` | `$11` | `Obj_InvisibleBlock` | 0 | 1 | shared concrete |
| `$28` | `$20` | `Obj_InvisibleBlock` | 0 | 1 | shared concrete |
| `$2F` | `$2E` | `Obj_StillSprite` | 80 | 86 | shared concrete |
| `$2F` | `$2F` | `Obj_StillSprite` | 0 | 7 | shared concrete |
| `$30` | `$04` | `Obj_AnimatedStillSprite` | 5 | 0 | shared concrete |
| `$30` | `$05` | `Obj_AnimatedStillSprite` | 4 | 1 | shared concrete |
| `$30` | `$06` | `Obj_AnimatedStillSprite` | 5 | 2 | shared concrete |
| `$30` | `$07` | `Obj_AnimatedStillSprite` | 7 | 5 | shared concrete |
| `$34` | `$01` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$02` | `Obj_StarPost` | 1 | 1 | shared concrete |
| `$34` | `$03` | `Obj_StarPost` | 1 | 1 | shared concrete |
| `$34` | `$04` | `Obj_StarPost` | 1 | 1 | shared concrete |
| `$34` | `$05` | `Obj_StarPost` | 1 | 1 | shared concrete |
| `$34` | `$06` | `Obj_StarPost` | 0 | 1 | shared concrete |
| `$38` | `$04` | `Obj_SOZQuicksand` | 0 | 1 | placeholder |
| `$38` | `$06` | `Obj_SOZQuicksand` | 3 | 1 | placeholder |
| `$38` | `$07` | `Obj_SOZQuicksand` | 1 | 1 | placeholder |
| `$38` | `$08` | `Obj_SOZQuicksand` | 2 | 1 | placeholder |
| `$38` | `$09` | `Obj_SOZQuicksand` | 1 | 1 | placeholder |
| `$38` | `$0A` | `Obj_SOZQuicksand` | 2 | 0 | placeholder |
| `$38` | `$0B` | `Obj_SOZQuicksand` | 0 | 2 | placeholder |
| `$38` | `$0C` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$0D` | `Obj_SOZQuicksand` | 0 | 1 | placeholder |
| `$38` | `$0F` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$10` | `Obj_SOZQuicksand` | 4 | 0 | placeholder |
| `$38` | `$13` | `Obj_SOZQuicksand` | 0 | 2 | placeholder |
| `$38` | `$14` | `Obj_SOZQuicksand` | 0 | 1 | placeholder |
| `$38` | `$16` | `Obj_SOZQuicksand` | 1 | 1 | placeholder |
| `$38` | `$17` | `Obj_SOZQuicksand` | 1 | 1 | placeholder |
| `$38` | `$18` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$20` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$4A` | `Obj_SOZQuicksand` | 0 | 2 | placeholder |
| `$38` | `$50` | `Obj_SOZQuicksand` | 1 | 1 | placeholder |
| `$38` | `$51` | `Obj_SOZQuicksand` | 2 | 0 | placeholder |
| `$38` | `$52` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$88` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$90` | `Obj_SOZQuicksand` | 2 | 2 | placeholder |
| `$38` | `$98` | `Obj_SOZQuicksand` | 4 | 0 | placeholder |
| `$38` | `$A0` | `Obj_SOZQuicksand` | 0 | 1 | placeholder |
| `$38` | `$A8` | `Obj_SOZQuicksand` | 1 | 0 | placeholder |
| `$38` | `$C8` | `Obj_SOZQuicksand` | 9 | 0 | placeholder |
| `$38` | `$D0` | `Obj_SOZQuicksand` | 21 | 4 | placeholder |
| `$39` | `$12` | `Obj_SOZSpawningSandBlocks` | 1 | 0 | `SozSpawningSandBlocksObjectInstance` |
| `$39` | `$15` | `Obj_SOZSpawningSandBlocks` | 2 | 0 | `SozSpawningSandBlocksObjectInstance` |
| `$3A` | `$09` | `Obj_SOZPathSwap` | 2 | 2 | `SozPathSwapObjectInstance` |
| `$3A` | `$11` | `Obj_SOZPathSwap` | 8 | 6 | `SozPathSwapObjectInstance` |
| `$3B` | `$21` | `Obj_SOZLoopFallthrough` | 0 | 3 | `SozLoopFallthroughObjectInstance` |
| `$3E` | `$00` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$01` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$02` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$03` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$04` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$05` | `Obj_SOZPushableRock` | 0 | 1 | `SozPushableRockObjectInstance` |
| `$3E` | `$06` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$09` | `Obj_SOZPushableRock` | 1 | 0 | `SozPushableRockObjectInstance` |
| `$3E` | `$0A` | `Obj_SOZPushableRock` | 0 | 1 | `SozPushableRockObjectInstance` |
| `$3E` | `$87` | `Obj_SOZPushableRock` | 0 | 1 | `SozPushableRockObjectInstance` |
| `$3F` | `$00` | `Obj_SOZSpringVine` | 12 | 5 | `SozSpringVineObjectInstance` |
| `$40` | `$60` | `Obj_SOZRisingSandWall` | 4 | 2 | `SozRisingSandWallObjectInstance` |
| `$41` | `$04` | `Obj_SOZLightSwitch` | 0 | 23 | `SozLightSwitchObjectInstance` |
| `$41` | `$84` | `Obj_SOZLightSwitch` | 0 | 2 | `SozLightSwitchObjectInstance` |
| `$42` | `$00` | `Obj_SOZFloatingPillar` | 2 | 5 | placeholder |
| `$42` | `$01` | `Obj_SOZFloatingPillar` | 2 | 4 | placeholder |
| `$42` | `$02` | `Obj_SOZFloatingPillar` | 0 | 2 | placeholder |
| `$42` | `$03` | `Obj_SOZFloatingPillar` | 1 | 0 | placeholder |
| `$42` | `$04` | `Obj_SOZFloatingPillar` | 25 | 9 | placeholder |
| `$42` | `$05` | `Obj_SOZFloatingPillar` | 5 | 5 | placeholder |
| `$42` | `$10` | `Obj_SOZFloatingPillar` | 1 | 1 | placeholder |
| `$42` | `$11` | `Obj_SOZFloatingPillar` | 0 | 2 | placeholder |
| `$42` | `$12` | `Obj_SOZFloatingPillar` | 0 | 1 | placeholder |
| `$42` | `$13` | `Obj_SOZFloatingPillar` | 1 | 0 | placeholder |
| `$42` | `$14` | `Obj_SOZFloatingPillar` | 4 | 6 | placeholder |
| `$42` | `$15` | `Obj_SOZFloatingPillar` | 2 | 1 | placeholder |
| `$42` | `$21` | `Obj_SOZFloatingPillar` | 1 | 1 | placeholder |
| `$42` | `$22` | `Obj_SOZFloatingPillar` | 1 | 1 | placeholder |
| `$42` | `$24` | `Obj_SOZFloatingPillar` | 7 | 1 | placeholder |
| `$42` | `$25` | `Obj_SOZFloatingPillar` | 3 | 2 | placeholder |
| `$43` | `$04` | `Obj_SOZSwingingPlatform` | 0 | 2 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$05` | `Obj_SOZSwingingPlatform` | 3 | 2 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$06` | `Obj_SOZSwingingPlatform` | 1 | 0 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$13` | `Obj_SOZSwingingPlatform` | 7 | 4 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$14` | `Obj_SOZSwingingPlatform` | 6 | 2 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$15` | `Obj_SOZSwingingPlatform` | 2 | 1 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$16` | `Obj_SOZSwingingPlatform` | 0 | 2 | `SozSwingingPlatformObjectInstance` |
| `$43` | `$17` | `Obj_SOZSwingingPlatform` | 1 | 0 | `SozSwingingPlatformObjectInstance` |
| `$44` | `$00` | `Obj_SOZBreakableSandRock` | 17 | 13 | `SozBreakableSandRockObjectInstance` |
| `$45` | `$01` | `Obj_SOZPushSwitch` | 0 | 2 | placeholder |
| `$45` | `$02` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$03` | `Obj_SOZPushSwitch` | 0 | 2 | placeholder |
| `$45` | `$05` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$06` | `Obj_SOZPushSwitch` | 0 | 2 | placeholder |
| `$45` | `$07` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$08` | `Obj_SOZPushSwitch` | 0 | 2 | placeholder |
| `$45` | `$0A` | `Obj_SOZPushSwitch` | 0 | 2 | placeholder |
| `$45` | `$0C` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$0D` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$0E` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$19` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$1F` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$45` | `$9B` | `Obj_SOZPushSwitch` | 0 | 1 | placeholder |
| `$46` | `$01` | `Obj_SOZDoor` | 0 | 2 | placeholder |
| `$46` | `$02` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$03` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$05` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$06` | `Obj_SOZDoor` | 0 | 2 | placeholder |
| `$46` | `$07` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$08` | `Obj_SOZDoor` | 0 | 2 | placeholder |
| `$46` | `$0A` | `Obj_SOZDoor` | 0 | 2 | placeholder |
| `$46` | `$0B` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$0E` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$13` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$19` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$1C` | `Obj_SOZDoor` | 0 | 2 | placeholder |
| `$46` | `$1D` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$46` | `$1F` | `Obj_SOZDoor` | 0 | 1 | placeholder |
| `$47` | `$0A` | `Obj_SOZSandCork` | 0 | 1 | `SozSandCorkObjectInstance` |
| `$47` | `$18` | `Obj_SOZSandCork` | 0 | 1 | `SozSandCorkObjectInstance` |
| `$47` | `$9C` | `Obj_SOZSandCork` | 0 | 1 | `SozSandCorkObjectInstance` |
| `$47` | `$9E` | `Obj_SOZSandCork` | 0 | 1 | `SozSandCorkObjectInstance` |
| `$48` | `$04` | `Obj_SOZRapelWire` | 1 | 0 | `SozRapelWireObjectInstance` |
| `$48` | `$05` | `Obj_SOZRapelWire` | 2 | 1 | `SozRapelWireObjectInstance` |
| `$48` | `$06` | `Obj_SOZRapelWire` | 0 | 2 | `SozRapelWireObjectInstance` |
| `$48` | `$07` | `Obj_SOZRapelWire` | 0 | 1 | `SozRapelWireObjectInstance` |
| `$48` | `$08` | `Obj_SOZRapelWire` | 1 | 0 | `SozRapelWireObjectInstance` |
| `$48` | `$42` | `Obj_SOZRapelWire` | 1 | 0 | `SozRapelWireObjectInstance` |
| `$48` | `$46` | `Obj_SOZRapelWire` | 0 | 1 | `SozRapelWireObjectInstance` |
| `$48` | `$81` | `Obj_SOZRapelWire` | 2 | 0 | `SozRapelWireObjectInstance` |
| `$49` | `$00` | `Obj_SOZSolidSprites` | 8 | 6 | `SozSolidSpritesObjectInstance` |
| `$49` | `$01` | `Obj_SOZSolidSprites` | 8 | 8 | `SozSolidSpritesObjectInstance` |
| `$6B` | `$00` | `Obj_InvisibleHurtBlockVertical` | 7 | 5 | shared concrete |
| `$6B` | `$01` | `Obj_InvisibleHurtBlockVertical` | 1 | 3 | shared concrete |
| `$6B` | `$02` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | shared concrete |
| `$6B` | `$04` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | shared concrete |
| `$6B` | `$06` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | shared concrete |
| `$6B` | `$07` | `Obj_InvisibleHurtBlockVertical` | 0 | 3 | shared concrete |
| `$6B` | `$08` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | shared concrete |
| `$6B` | `$0A` | `Obj_InvisibleHurtBlockVertical` | 0 | 3 | shared concrete |
| `$6B` | `$0C` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | shared concrete |
| `$6B` | `$20` | `Obj_InvisibleHurtBlockVertical` | 2 | 1 | shared concrete |
| `$6B` | `$30` | `Obj_InvisibleHurtBlockVertical` | 2 | 1 | shared concrete |
| `$6B` | `$40` | `Obj_InvisibleHurtBlockVertical` | 10 | 1 | shared concrete |
| `$6B` | `$80` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | shared concrete |
| `$6B` | `$F0` | `Obj_InvisibleHurtBlockVertical` | 2 | 3 | shared concrete |
| `$80` | `$01` | `Obj_HiddenMonitor` | 1 | 0 | shared concrete |
| `$80` | `$03` | `Obj_HiddenMonitor` | 1 | 0 | shared concrete |
| `$80` | `$05` | `Obj_HiddenMonitor` | 1 | 0 | shared concrete |
| `$85` | `$00` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$01` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$02` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$03` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$04` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$05` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$06` | `Obj_SSEntryRing` | 1 | 0 | shared concrete |
| `$85` | `$07` | `Obj_SSEntryRing` | 0 | 1 | shared concrete |
| `$85` | `$08` | `Obj_SSEntryRing` | 0 | 1 | shared concrete |
| `$85` | `$09` | `Obj_SSEntryRing` | 0 | 1 | shared concrete |
| `$85` | `$0A` | `Obj_SSEntryRing` | 0 | 1 | shared concrete |
| `$8B` | `$40` | `Obj_SpriteMask` | 0 | 1 | `SozSpriteMaskObjectInstance` |
| `$94` | `$20` | `Obj_Skorp` | 5 | 1 | `SkorpBadnikInstance` |
| `$94` | `$30` | `Obj_Skorp` | 0 | 2 | `SkorpBadnikInstance` |
| `$94` | `$38` | `Obj_Skorp` | 1 | 0 | `SkorpBadnikInstance` |
| `$94` | `$40` | `Obj_Skorp` | 8 | 4 | `SkorpBadnikInstance` |
| `$94` | `$50` | `Obj_Skorp` | 1 | 1 | `SkorpBadnikInstance` |
| `$94` | `$60` | `Obj_Skorp` | 4 | 1 | `SkorpBadnikInstance` |
| `$94` | `$80` | `Obj_Skorp` | 2 | 1 | `SkorpBadnikInstance` |
| `$94` | `$90` | `Obj_Skorp` | 0 | 1 | `SkorpBadnikInstance` |
| `$94` | `$C0` | `Obj_Skorp` | 1 | 0 | `SkorpBadnikInstance` |
| `$94` | `$E0` | `Obj_Skorp` | 0 | 2 | `SkorpBadnikInstance` |
| `$95` | `$00` | `Obj_Sandworm` | 24 | 8 | `SandwormBadnikInstance` |
| `$96` | `$1B` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$36` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$41` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$44` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$45` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$47` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$4C` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$4D` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$4F` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$5A` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$73` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$81` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$94` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$B2` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$D1` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$F1` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$96` | `$F6` | `Obj_Rockn` | 1 | 0 | `RocknBadnikInstance` |
| `$98` | `$00` | `Obj_SOZEndBoss` | 0 | 1 | placeholder |
| `$AB` | `$00` | `Obj_SOZHyudoroCapsuleLoadArt` | 0 | 1 | `SozHyudoroArtTriggerObjectInstance` |
| `$AB` | `$04` | `Obj_SOZHyudoroCapsuleLoadArt` | 0 | 1 | `SozHyudoroArtTriggerObjectInstance` |
| `$AC` | `$00` | `Obj_SOZHyudoroCapsule` | 0 | 1 | `SozHyudoroCapsuleObjectInstance` |

## First family contract: quicksand `$38`

`Obj_SOZQuicksand` uses subtype bits 7–6 for four invisible region controllers:
normal vertical strip, horizontal slide, tumbling sand waterfall and deep sand.
Low six bits set the long half-extent to `subtype & $3F`, multiplied by eight.
It has no mapped art, dynamic children or PLC jobs. Jump branches request
`sfx_Jump`. Per-native-player held bits and the slide recapture timers are
persistent state; extra followers need independent equivalents and rewind.
The waterfall reads `Level_frame_counter`, not the object update V-int argument.

Owning branches: `sub_3FD4E`, `sub_3FE70`, `loc_3FF9E`, `sub_400F0`.
The first Act 1 placement is centre `($230,$640)`, subtype `$10`.
Initial focused regression: `TestSozQuicksand`; implementation/production-route
and native matching results belong in the execution plan and act matrices.

## Dynamic and non-placement obligations still open

- Act 1 event spawner, miniboss children and the two end-door owners.
- Act 2 ghost spawner/bodies, both capsule branches, switch ownership and restart.
- Act 2 end-boss children, wall/solid reconstruction, defeat and outgoing route.
- Per-family mappings/animation/PLC/audio, allocation failure and deletion graph.

These open obligations prevent calling this a complete zone inventory gate.

Current quicksand candidate replaces all 84 placed `$38` placeholders (61 Act 1,
23 Act 2). The ROM-backed inventory test pins both placement byte hashes and
expects 202 remaining placeholder placements in Act 1 and 196 in Act 2 after
the spring-vine continuation replaces all 17 `$3F` placements. These
counts describe implementation gaps, not passing routes.

## Spring-vine family `$3F`

`Obj_SOZSpringVine` (`$40786`) creates one independent later-slot display SST,
with eight frame-zero pieces from mapping `$40B0C` and SOZ level art `$3C9`,
palette 2. The controller does not draw. Its 96-byte one-pixel slope and the
child's eight heights are generated together. P2 runs before P1 for tension and
launch decisions; P1 runs before P2 for the subsequent top-solid pass. A signed
side marker detects crossing directional X `$3C`; the launch is `-$EF0` on Y
and `-$EF0` on X (positive when horizontally flipped), without a jump-button gate.
The native controller/child each use coarse-X lifetime checks.

First ordinary Act 1 target: `($298,$698)`. Other Act 1/2 placements and complete
routes retain the act matrices' outstanding reachability/rewind/pixel obligations.

## Breakable sand rocks

All 30 `$44` placements bind to the ROM-backed five-frame sand rock. Mapping
`$4182E`, art `SOZMisc+$10`, palette2; native owner `$41702`. Save roll animation
before solid collision, then read the object's standing latch. Breakup releases
all standing riders and rebounds only those that were rolling. No debris child
allocation occurs: five mapping frames shrink the rock before coarse-X deletion.
Remaining placeholder placements after this family: **185 Act 1 / 183 Act 2**.
The unsuccessful cold approach and positioned production coverage are tracked in
the execution plan; binding counts do not certify route reachability.

Pushable-rock continuation: all seven Act 1 and three Act 2 `$3E` placements
bind to `SozPushableRockObjectInstance`. The low five subtype bits select the
ROM-backed alternating Y/X track. Saved player pushing status and object-owned
push bits control one-pixel movement every five eligible passes; floor distance
above 14 starts the track. At that delivery, the `$87` push-switch coupling remained open; the
connected-mechanism continuation below supplies that consumer. Remaining placeholder placements: **178 Act 1 / 180 Act 2**.
See the [v2 execution plan](../../plans/2026-09-15-soz-methodology-v2.md) for
source evidence, rejected carry behavior and production validation.

## Loop exits and solid terrain sprites

SKL `$3B` captures players inside a 32×32 unsigned-word region at signed Y speed
at least `$800`, excluding hurt/dead, existing support and object control. Capture
writes the literal rolling body and `$81` object control; movement begins next
pass. Old signed velocities integrate into 16.16 positions before signed-word
`y - ((subtype & $7F) << 4) >= originY` releases ownership. Three Act 2 placements
use subtype `$21` (528 pixels). No art, children or sound are involved.

SKL `$49` has 30 placements across both acts. Zero subtype selects the 32×48
pillar; every nonzero byte selects the 64×16 ledge. Both call full solid collision
before coarse-X culling. Mapping `$41FC8` uses three/two pieces and terrain tile
base 1, palette 2; no standalone art or child allocation. Remaining placeholders:
**162 Act 1 / 163 Act 2**. Binding coverage remains distinct from cold reachability.

## Connected switch, door and floating-pillar mechanics

SKL `$42` has 55 Act 1 and 41 Act 2 placements. `Obj_SOZFloatingPillar`
(`$41176`) reads ROM shape bytes at `$4116A`; frames 0/1/2 are ordinary,
upper-spiked and lower-spiked. Movement modes 0–6 are static or three oscillator
amplitudes along X/Y, with status bit 0 reversing displacement. Full solid
collision uses the pre-movement X for rider carry. Spiked faces use new native
contact flags and `sub_24280`, including its normal damage sound for this owner.

SKL `$45` has 19 Act 2 placements. It is a **horizontal push switch**, not a
weight button: four eligible pushes move its body and player one pixel, charging
an existing `Level_trigger_array` byte to 128. The fixed base is a same-SST child
sprite. Release decays once per ten eligible passes, or every pass when subtype
bits `$70` are nonzero. Offscreen charged owners release their placement, run an
additional decay immediately and remain invisible until exhausted or replaced.
Subtype bit 7 enables `sub_41AA8` rock contact through the last published native
rock slot (`_unkF7C4`); a reused slot or changed rock routine invalidates the link.

SKL `$46` has 20 Act 2 placements. Each door approaches its trigger byte by one
pixel per pass. The subtype high nibble selects horizontal versus vertical;
status bit 0 selects sign. Initial displacement reads the current trigger.
Horizontal doors capture collision X after movement and therefore do not carry
riders horizontally. Mapping frames are 7/5 pieces at `$41C72`; switch mapping
`$41B56` is 2/1 pieces. Both use `SOZMisc+$8C`, palette 2. Pillar mapping
`$412E0` is 16/20/20 pieces using terrain base 1, palette 2.

These three factories replace 135 further placed placeholders, leaving
**107 Act 1 / 83 Act 2**. The CNZ owners at these numeric IDs remain distinct.
See the execution plan and act matrices for actual validation and inherited gaps.

## Completion campaign object batches

The first mechanism/badnik batches and the light/ghost batch are integrated on
the completion branch, including final boss `382eee7fc`. Both acts now have
zero placed placeholders: 599 Act 1 and 490 Act 2 records bind to concrete factories.
The Act 1 miniboss `$97` is dynamically created by the arena and implemented separately. This count is factory coverage, not a claim
that either complete route or every compatibility configuration is verified.

Native low-level gaps remain explicit: cork `loc_41E78` reads the art-tile low
byte of SST slot seven positions earlier for its quiet skid sound. No unrelated
frame clock is substituted; arbitrary foreign-slot bytes are not modeled. The
boss charge's failed-allocation terminal-slot poll has the same general limit.
