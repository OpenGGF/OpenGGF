# S3K DEZ placed-object inventory

Initial inventory at `9cba6dbb6`, 2026-09-17, for the
[S3K DEZ bring-up plan](../../plans/2026-09-17-s3k-dez-bring-up.md). This is **Sonic 3 & Knuckles**
Death Egg (`$B00`, `$B01`, final-boss act `$1700`), not Sonic 2 DEZ. Locked-on ROM SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`; disassembly submodule
`1a454a0e335137a1a016d1090a9f4528d36944cf`. IDs and subtypes are hexadecimal.

Decoded for research from the disassembly binaries (the engine reads the same bytes from the ROM;
the disassembly is never a runtime source). The binaries were byte-matched against the ROM:

| Data | Disassembly file | Bytes | ROM offset | Label |
| --- | --- | ---: | --- | --- |
| Act 1 objects | `Levels/DEZ/Object Pos/1.bin` | 2196 | `$1F98F4` | `DEZ1_Sprites` |
| Act 2 objects | `Levels/DEZ/Object Pos/2.bin` | 2970 | `$1FA188` | `DEZ2_Sprites` |
| `$1700` objects | `Levels/DEZ/Object Pos/3.bin` | 6 | (terminator only) | `DEZ3_Sprites` |
| Act 1 rings | `Levels/DEZ/Ring Pos/1.bin` | 1118 | `$1FAD22` | `DEZ1_Rings` |
| Act 2 rings | `Levels/DEZ/Ring Pos/2.bin` | 798 | `$1FB180` | `DEZ2_Rings` |
| `$1700` rings | `Levels/DEZ/Ring Pos/3.bin` | 6 | (empty) | `DEZ3_Rings` |

Object records are six bytes (`x`, `flags|y`, `id`, `subtype`; bits 13/14 of the Y word are
`render_flags` bits 0/1, bit 15 is never set in DEZ). Each object file ends with one
`$FFFF,0,0` terminator, which is not counted. Live counts: **365 + 494 + 0 = 859**.

Ring files start with a four-byte `0,0` lead record that `sub_EB1A` skips (`addq.w #4,a1`,
sonic3k.asm:18596) and end with `$FFFF`. Ring counts, which are also `Perfect_rings_left`:
**act 1 278, act 2 198, `$1700` 0**. The `$1700` rings come from `Act3_ring_count`, not placements.
`Sonic3kRingPlacement.parseRawRingRecords` does not skip a lead record; check that the ring pointer
the engine reads already points past it before asserting a DEZ ring total (cross-zone, not DEZ work).

Regenerate in minutes: read six-byte big-endian records until `x == $FFFF`, count by
`(id, subtype, act)`. No decoder was promoted; `ObjectDiscoveryTool` with `Sonic3kObjectProfile`
gives the same per-ID report from the ROM once a build is available.

## Pointer set

DEZ is zone `$0B`; `S3kZoneSet.forZone` gives **SKL** for zones 7-13 and for `$16`/`$17`
(see [AGENTS_S3K.md](../../../../AGENTS_S3K.md)). Owner names below come from
`Levels/Misc/Object pointers - SK Set 2.asm`. The same numeric IDs mean different objects in the
S3KL set: `$4A` Bumper, `$4B` CNZ triangle bumper, `$4C-$4E` CNZ objects, `$4F` SinkingMud,
`$50-$5C` MGZ objects, `$6D` HCZWaterSplash, `$A4` Sparkle, `$A5` Batbot, `$A6` CNZMiniboss,
`$A7` CNZEndBoss. Every new DEZ factory must be zone-set bound (`registerZoneSetBound(..., SKL, ...)`
or the existing `zoneSet ==` branch) and, where SOZ/LRZ/SSZ reuse the ID under SKL, zone bound too.

## Factory classification

Read from `Sonic3kObjectRegistry` at `9cba6dbb6`. It is a registration fact, not route evidence.

- **shared concrete**: one factory serves every zone set.
- **placeholder (S3KL-only factory)**: a factory exists for the ID but returns
  `PlaceholderObjectInstance` when the zone set is not S3KL (or the zone is not its own).
- **unregistered**: no factory; `create` falls to the default placeholder. No `Sonic3kObjectIds`
  constant exists either.

| Class | Act 1 | Act 2 |
| --- | ---: | ---: |
| Shared concrete | 140 | 157 |
| Placeholder (S3KL-only factory) | 202 | 324 |
| Unregistered | 23 | 13 |
| **Total** | **365** | **494** |

`$6D` resolved: `Obj_InvisibleShockBlock` is **not implemented**. Under SKL the `$6D` factory
(`HCZ_WATER_SPLASH`, registry line 585) returns a placeholder, and no class names or models a shock
block. ROM (sonic3k.asm:43265): `bset #5,shield_reaction(a0)` then falls into
`Obj_InvisibleHurtBlockHorizontal`, exactly as `Obj_InvisibleLavaBlock` sets bit 4. Placement
`status` bit 0 selects `loc_1F4C4`, bit 1 selects `loc_1F528`, neither selects `loc_1F45E`; DEZ uses
no-flip (56) and Y-flip (22) only. The engine's `Sonic3kInvisibleHurtBlockHObjectInstance` checks
`hasShield()` generically; the shock block needs the lightning-shield reaction bit. The LRZ plan
adds the lava variant (`$6E`): land the shield-reaction parameter once and share it.

`$78`: `Sonic3kObjectRegistry` registers `FBZ_DEZ_PLAYER_LAUNCHER` twice (line 217
`FbzDezPlayerLauncherInstance`, line 1411 `FbzDezPlayerLauncherObjectInstance`); the later `put`
wins. Verify which class DEZ actually gets before testing it.

## Placed objects by ID

| ID | ROM pointer owner | Act 1 | Act 2 | Current factory | Plan slice |
| --- | --- | ---: | ---: | --- | --- |
| `$01` | `Obj_Monitor` | 19 | 22 | shared concrete | 2b (verify: 9 act 2 monitors are Y-flipped; `Touch_Monitor` reads the flag) |
| `$02` | `Obj_PathSwap` | 1 | 0 | shared concrete | verify only (6) |
| `$07` | `Obj_Spring` | 20 | 15 | shared concrete | 2b |
| `$08` | `Obj_Spikes` | 31 | 60 | shared concrete | 2b |
| `$28` | `Obj_InvisibleBlock` | 26 | 24 | shared concrete | verify only (6, 8) |
| `$2F` | `Obj_StillSprite` | 19 | 20 | shared concrete | 4 (verify DEZ frames `$30-$32` art) |
| `$34` | `Obj_StarPost` | 3 | 4 | shared concrete | 6, 8 (respawn rows) |
| `$3C` | `Obj_Door` | 11 | 6 | shared concrete | 4 (verify DEZ art and trigger) |
| `$4A` | `Obj_DEZFloatingPlatform` | 0 | 10 | concrete: full-solid DEZ oscillation/sweep table | 4 |
| `$4B` | `Obj_DEZTiltingBridge` | 1 | 3 | placeholder | 4 |
| `$4C` | `Obj_DEZHangCarrier` | 3 | 1 | concrete: dual-player capture, lift, ceiling turn and jump release | 4 |
| `$4D` | `Obj_DEZTorpedoLauncher` | 36 | 38 | concrete: timed launcher, closing animation and touch projectile | 4 |
| `$4E` | `Obj_DEZLiftPad` | 7 | 0 | concrete: triggered accelerating lift arm and top-solid pad | 4 |
| `$4F` | `Obj_DEZStaircase` | 18 | 15 | placeholder | 4 |
| `$50` | `Obj_DEZConveyorBelt` | 8 | 5 | concrete: invisible two-sided grounded-player conveyor | 4 |
| `$52` | `Obj_DEZLightning` | 48 | 94 | concrete: ROM animation/wait/touch, art and local SFX | 4 |
| `$53` | `Obj_DEZConveyorPad` | 4 | 5 | concrete: triggered finite/floor-following full-solid conveyor | 4 |
| `$55` | `Obj_DEZEnergyBridge` | 13 | 12 | `S3kDezEnergyBridgeObjectInstance` | 4 |
| `$56` | `Obj_DEZEnergyBridgeCurved` | 1 | 0 | concrete: timed curved collision-index field | 4 |
| `$57` | `Obj_DEZTunnelLauncher` | 3 | 4 | placeholder | 5 |
| `$58` | `Obj_DEZGravitySwitch` | 0 | 5 | **concrete** (`S3kDezGravitySwitchObjectInstance`; art not registered) | 3 |
| `$59` | `Obj_DEZTeleporter` | 0 | 21 | `S3kDezTeleporterObjectInstance` | 3 |
| `$5A` | `Obj_DEZGravityTube` | 24 | 17 | `S3kDezGravityTubeObjectInstance` | 3 |
| `$5B` | `Obj_DEZGravitySwap` | 0 | 11 | **concrete** (`S3kDezGravitySwapObjectInstance`) | 3 |
| `$5C` | `Obj_DEZGravityHub` | 0 | 3 | `S3kDezGravityHubObjectInstance` | 3 |
| `$5D` | `Obj_DEZRetractingSpring` | 0 | 13 | `S3kDezRetractingSpringObjectInstance` | 4 |
| `$5E` | `Obj_DEZHoverMachine` | 11 | 0 | concrete: flicker owner plus oscillating lift field child | 4 |
| `$5F` | `Obj_DEZGravityRoom` | 1 | 0 | `S3kDezGravityRoomObjectInstance` | 3 |
| `$60` | `Obj_DEZBumperWall` | 10 | 0 | `S3kDezBumperWallObjectInstance` | 4 |
| `$61` | `Obj_DEZGravityPuzzle` | 1 | 0 | `S3kDezGravityPuzzleObjectInstance` | 3 |
| `$6A` | `Obj_InvisibleHurtBlockHorizontal` | 0 | 1 | shared concrete | verify only (8) |
| `$6B` | `Obj_InvisibleHurtBlockVertical` | 0 | 5 | shared concrete | verify only (8) |
| `$6D` | `Obj_InvisibleShockBlock` | 22 | 56 | concrete: shared hurt block plus lightning-shield reaction | 4 |
| `$78` | `Obj_FBZDEZPlayerLauncher` | 10 | 0 | shared concrete | 4 (verify; duplicate registration above) |
| `$A4` | `Obj_Spikebonker` | 7 | 11 | `SpikebonkerBadnikInstance` | 4 |
| `$A5` | `Obj_Chainspike` | 6 | 12 | implemented | 4 |
| `$A6` | `Obj_DEZMiniboss` | 1 | 0 | placeholder | 6 |
| `$A7` | `Obj_DEZEndBoss` | 0 | 1 | placeholder | 8 |

Both bosses are **placed** objects gated by `Check_CameraInRange`, not event spawns.

Placement flags that select behaviour: `$5B` gravity swap, 6 unflipped and 5 X-flipped
(`render_flags` bit 0 chooses which crossing direction sets the flag, `sub_49228`); `$58` gravity
switch, 2 unflipped and 3 Y-flipped (bit 1 chooses the press direction); `$59` teleporter subtype
bit 7 is the destination gravity (`loc_48DCA`: `rol.b #1` of the subtype compared with the flag),
so subtypes `$80+` arrive inverted.

## Placed objects by ID and subtype

| ID | Subtype | ROM pointer owner | Act 1 | Act 2 | Current factory |
| --- | --- | --- | ---: | ---: | --- |
| `$01` | `$01` | `Obj_Monitor` | 0 | 2 | shared concrete |
| `$01` | `$02` | `Obj_Monitor` | 0 | 1 | shared concrete |
| `$01` | `$03` | `Obj_Monitor` | 9 | 12 | shared concrete |
| `$01` | `$05` | `Obj_Monitor` | 1 | 1 | shared concrete |
| `$01` | `$06` | `Obj_Monitor` | 5 | 4 | shared concrete |
| `$01` | `$07` | `Obj_Monitor` | 2 | 2 | shared concrete |
| `$01` | `$08` | `Obj_Monitor` | 2 | 0 | shared concrete |
| `$02` | `$21` | `Obj_PathSwap` | 1 | 0 | shared concrete |
| `$07` | `$00` | `Obj_Spring` | 1 | 2 | shared concrete |
| `$07` | `$02` | `Obj_Spring` | 1 | 0 | shared concrete |
| `$07` | `$10` | `Obj_Spring` | 2 | 2 | shared concrete |
| `$07` | `$12` | `Obj_Spring` | 13 | 6 | shared concrete |
| `$07` | `$20` | `Obj_Spring` | 3 | 1 | shared concrete |
| `$07` | `$22` | `Obj_Spring` | 0 | 2 | shared concrete |
| `$07` | `$23` | `Obj_Spring` | 0 | 2 | shared concrete |
| `$08` | `$00` | `Obj_Spikes` | 6 | 7 | shared concrete |
| `$08` | `$01` | `Obj_Spikes` | 0 | 1 | shared concrete |
| `$08` | `$10` | `Obj_Spikes` | 1 | 12 | shared concrete |
| `$08` | `$11` | `Obj_Spikes` | 1 | 0 | shared concrete |
| `$08` | `$20` | `Obj_Spikes` | 1 | 12 | shared concrete |
| `$08` | `$30` | `Obj_Spikes` | 0 | 6 | shared concrete |
| `$08` | `$40` | `Obj_Spikes` | 3 | 0 | shared concrete |
| `$08` | `$50` | `Obj_Spikes` | 17 | 3 | shared concrete |
| `$08` | `$60` | `Obj_Spikes` | 2 | 16 | shared concrete |
| `$08` | `$70` | `Obj_Spikes` | 0 | 3 | shared concrete |
| `$28` | `$01` | `Obj_InvisibleBlock` | 2 | 0 | shared concrete |
| `$28` | `$13` | `Obj_InvisibleBlock` | 4 | 2 | shared concrete |
| `$28` | `$16` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$17` | `Obj_InvisibleBlock` | 2 | 6 | shared concrete |
| `$28` | `$18` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$1A` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$1B` | `Obj_InvisibleBlock` | 1 | 1 | shared concrete |
| `$28` | `$1F` | `Obj_InvisibleBlock` | 10 | 15 | shared concrete |
| `$28` | `$31` | `Obj_InvisibleBlock` | 2 | 0 | shared concrete |
| `$28` | `$71` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$28` | `$81` | `Obj_InvisibleBlock` | 1 | 0 | shared concrete |
| `$2F` | `$30` | `Obj_StillSprite` | 15 | 16 | shared concrete |
| `$2F` | `$31` | `Obj_StillSprite` | 1 | 0 | shared concrete |
| `$2F` | `$32` | `Obj_StillSprite` | 3 | 4 | shared concrete |
| `$34` | `$01` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$02` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$03` | `Obj_StarPost` | 1 | 0 | shared concrete |
| `$34` | `$05` | `Obj_StarPost` | 0 | 1 | shared concrete |
| `$34` | `$06` | `Obj_StarPost` | 0 | 1 | shared concrete |
| `$34` | `$07` | `Obj_StarPost` | 0 | 1 | shared concrete |
| `$34` | `$08` | `Obj_StarPost` | 0 | 1 | shared concrete |
| `$3C` | `$02` | `Obj_Door` | 11 | 6 | shared concrete |
| `$4A` | `$00` | `Obj_DEZFloatingPlatform` | 0 | 3 | placeholder |
| `$4A` | `$01` | `Obj_DEZFloatingPlatform` | 0 | 1 | placeholder |
| `$4A` | `$02` | `Obj_DEZFloatingPlatform` | 0 | 1 | placeholder |
| `$4A` | `$03` | `Obj_DEZFloatingPlatform` | 0 | 1 | placeholder |
| `$4A` | `$04` | `Obj_DEZFloatingPlatform` | 0 | 4 | placeholder |
| `$4B` | `$00` | `Obj_DEZTiltingBridge` | 1 | 3 | placeholder |
| `$4C` | `$25` | `Obj_DEZHangCarrier` | 1 | 1 | placeholder |
| `$4C` | `$34` | `Obj_DEZHangCarrier` | 1 | 0 | placeholder |
| `$4C` | `$44` | `Obj_DEZHangCarrier` | 1 | 0 | placeholder |
| `$4D` | `$09` | `Obj_DEZTorpedoLauncher` | 1 | 0 | placeholder |
| `$4D` | `$0C` | `Obj_DEZTorpedoLauncher` | 2 | 0 | placeholder |
| `$4D` | `$0E` | `Obj_DEZTorpedoLauncher` | 1 | 0 | placeholder |
| `$4D` | `$10` | `Obj_DEZTorpedoLauncher` | 5 | 1 | placeholder |
| `$4D` | `$11` | `Obj_DEZTorpedoLauncher` | 2 | 0 | placeholder |
| `$4D` | `$12` | `Obj_DEZTorpedoLauncher` | 9 | 3 | placeholder |
| `$4D` | `$14` | `Obj_DEZTorpedoLauncher` | 11 | 12 | placeholder |
| `$4D` | `$16` | `Obj_DEZTorpedoLauncher` | 2 | 11 | placeholder |
| `$4D` | `$17` | `Obj_DEZTorpedoLauncher` | 2 | 0 | placeholder |
| `$4D` | `$18` | `Obj_DEZTorpedoLauncher` | 1 | 7 | placeholder |
| `$4D` | `$20` | `Obj_DEZTorpedoLauncher` | 0 | 4 | placeholder |
| `$4E` | `$07` | `Obj_DEZLiftPad` | 5 | 0 | placeholder |
| `$4E` | `$27` | `Obj_DEZLiftPad` | 2 | 0 | placeholder |
| `$4F` | `$00` | `Obj_DEZStaircase` | 4 | 4 | placeholder |
| `$4F` | `$04` | `Obj_DEZStaircase` | 14 | 11 | placeholder |
| `$50` | `$08` | `Obj_DEZConveyorBelt` | 1 | 0 | placeholder |
| `$50` | `$10` | `Obj_DEZConveyorBelt` | 2 | 4 | placeholder |
| `$50` | `$18` | `Obj_DEZConveyorBelt` | 2 | 1 | placeholder |
| `$50` | `$20` | `Obj_DEZConveyorBelt` | 2 | 0 | placeholder |
| `$50` | `$28` | `Obj_DEZConveyorBelt` | 1 | 0 | placeholder |
| `$52` | `$24` | `Obj_DEZLightning` | 1 | 1 | placeholder |
| `$52` | `$2D` | `Obj_DEZLightning` | 0 | 3 | placeholder |
| `$52` | `$36` | `Obj_DEZLightning` | 1 | 11 | placeholder |
| `$52` | `$39` | `Obj_DEZLightning` | 1 | 0 | placeholder |
| `$52` | `$3C` | `Obj_DEZLightning` | 4 | 7 | placeholder |
| `$52` | `$3F` | `Obj_DEZLightning` | 4 | 2 | placeholder |
| `$52` | `$42` | `Obj_DEZLightning` | 1 | 3 | placeholder |
| `$52` | `$45` | `Obj_DEZLightning` | 1 | 0 | placeholder |
| `$52` | `$48` | `Obj_DEZLightning` | 0 | 1 | placeholder |
| `$52` | `$4B` | `Obj_DEZLightning` | 10 | 4 | placeholder |
| `$52` | `$50` | `Obj_DEZLightning` | 0 | 6 | placeholder |
| `$52` | `$51` | `Obj_DEZLightning` | 1 | 0 | placeholder |
| `$52` | `$54` | `Obj_DEZLightning` | 3 | 1 | placeholder |
| `$52` | `$58` | `Obj_DEZLightning` | 0 | 1 | placeholder |
| `$52` | `$5A` | `Obj_DEZLightning` | 4 | 5 | placeholder |
| `$52` | `$5F` | `Obj_DEZLightning` | 2 | 0 | placeholder |
| `$52` | `$64` | `Obj_DEZLightning` | 4 | 8 | placeholder |
| `$52` | `$69` | `Obj_DEZLightning` | 3 | 0 | placeholder |
| `$52` | `$6C` | `Obj_DEZLightning` | 0 | 4 | placeholder |
| `$52` | `$6E` | `Obj_DEZLightning` | 1 | 5 | placeholder |
| `$52` | `$72` | `Obj_DEZLightning` | 0 | 1 | placeholder |
| `$52` | `$73` | `Obj_DEZLightning` | 2 | 2 | placeholder |
| `$52` | `$78` | `Obj_DEZLightning` | 1 | 15 | placeholder |
| `$52` | `$7D` | `Obj_DEZLightning` | 1 | 3 | placeholder |
| `$52` | `$7E` | `Obj_DEZLightning` | 1 | 5 | placeholder |
| `$52` | `$84` | `Obj_DEZLightning` | 0 | 4 | placeholder |
| `$52` | `$8C` | `Obj_DEZLightning` | 1 | 1 | placeholder |
| `$52` | `$96` | `Obj_DEZLightning` | 1 | 0 | placeholder |
| `$52` | `$A0` | `Obj_DEZLightning` | 0 | 1 | placeholder |
| `$53` | `$00` | `Obj_DEZConveyorPad` | 1 | 1 | placeholder |
| `$53` | `$01` | `Obj_DEZConveyorPad` | 1 | 0 | placeholder |
| `$53` | `$28` | `Obj_DEZConveyorPad` | 1 | 0 | placeholder |
| `$53` | `$2C` | `Obj_DEZConveyorPad` | 0 | 1 | placeholder |
| `$53` | `$38` | `Obj_DEZConveyorPad` | 0 | 2 | placeholder |
| `$53` | `$48` | `Obj_DEZConveyorPad` | 0 | 1 | placeholder |
| `$53` | `$B0` | `Obj_DEZConveyorPad` | 1 | 0 | placeholder |
| `$55` | `$01` | `Obj_DEZEnergyBridge` | 4 | 9 | implemented |
| `$55` | `$05` | `Obj_DEZEnergyBridge` | 0 | 1 | implemented |
| `$55` | `$06` | `Obj_DEZEnergyBridge` | 2 | 0 | implemented |
| `$55` | `$45` | `Obj_DEZEnergyBridge` | 0 | 2 | implemented |
| `$55` | `$61` | `Obj_DEZEnergyBridge` | 2 | 0 | implemented |
| `$55` | `$66` | `Obj_DEZEnergyBridge` | 5 | 0 | implemented |
| `$56` | `$07` | `Obj_DEZEnergyBridgeCurved` | 1 | 0 | placeholder |
| `$57` | `$00` | `Obj_DEZTunnelLauncher` | 1 | 0 | placeholder |
| `$57` | `$01` | `Obj_DEZTunnelLauncher` | 1 | 0 | placeholder |
| `$57` | `$03` | `Obj_DEZTunnelLauncher` | 0 | 1 | placeholder |
| `$57` | `$04` | `Obj_DEZTunnelLauncher` | 0 | 1 | placeholder |
| `$57` | `$05` | `Obj_DEZTunnelLauncher` | 0 | 1 | placeholder |
| `$57` | `$06` | `Obj_DEZTunnelLauncher` | 1 | 0 | placeholder |
| `$57` | `$07` | `Obj_DEZTunnelLauncher` | 0 | 1 | placeholder |
| `$58` | `$00` | `Obj_DEZGravitySwitch` | 0 | 5 | **concrete** |
| `$59` | `$0D` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$15` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$18` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$22` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$25` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$3A` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$45` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$4D` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$8D` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$95` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$A2` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$A5` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$BA` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$59` | `$C5` | `Obj_DEZTeleporter` | 0 | 2 | implemented |
| `$59` | `$CD` | `Obj_DEZTeleporter` | 0 | 1 | implemented |
| `$5A` | `$08` | `Obj_DEZGravityTube` | 7 | 2 | implemented |
| `$5A` | `$10` | `Obj_DEZGravityTube` | 6 | 6 | implemented |
| `$5A` | `$12` | `Obj_DEZGravityTube` | 0 | 3 | implemented |
| `$5A` | `$18` | `Obj_DEZGravityTube` | 2 | 1 | implemented |
| `$5A` | `$22` | `Obj_DEZGravityTube` | 0 | 1 | implemented |
| `$5A` | `$48` | `Obj_DEZGravityTube` | 8 | 0 | implemented |
| `$5A` | `$50` | `Obj_DEZGravityTube` | 1 | 0 | implemented |
| `$5A` | `$92` | `Obj_DEZGravityTube` | 0 | 1 | implemented |
| `$5A` | `$98` | `Obj_DEZGravityTube` | 0 | 1 | implemented |
| `$5A` | `$9C` | `Obj_DEZGravityTube` | 0 | 1 | implemented |
| `$5A` | `$A4` | `Obj_DEZGravityTube` | 0 | 1 | implemented |
| `$5B` | `$00` | `Obj_DEZGravitySwap` | 0 | 11 | **concrete** |
| `$5C` | `$05` | `Obj_DEZGravityHub` | 0 | 1 | implemented |
| `$5C` | `$06` | `Obj_DEZGravityHub` | 0 | 1 | implemented |
| `$5C` | `$0F` | `Obj_DEZGravityHub` | 0 | 1 | implemented |
| `$5D` | `$02` | `Obj_DEZRetractingSpring` | 0 | 13 | implemented |
| `$5E` | `$00` | `Obj_DEZHoverMachine` | 11 | 0 | unregistered |
| `$5F` | `$00` | `Obj_DEZGravityRoom` | 1 | 0 | implemented |
| `$60` | `$00` | `Obj_DEZBumperWall` | 2 | 0 | implemented |
| `$60` | `$18` | `Obj_DEZBumperWall` | 4 | 0 | implemented |
| `$60` | `$38` | `Obj_DEZBumperWall` | 2 | 0 | implemented |
| `$60` | `$80` | `Obj_DEZBumperWall` | 2 | 0 | implemented |
| `$61` | `$00` | `Obj_DEZGravityPuzzle` | 1 | 0 | implemented |
| `$6A` | `$F1` | `Obj_InvisibleHurtBlockHorizontal` | 0 | 1 | shared concrete |
| `$6B` | `$F1` | `Obj_InvisibleHurtBlockVertical` | 0 | 5 | shared concrete |
| `$6D` | `$61` | `Obj_InvisibleShockBlock` | 1 | 6 | placeholder |
| `$6D` | `$71` | `Obj_InvisibleShockBlock` | 4 | 13 | placeholder |
| `$6D` | `$81` | `Obj_InvisibleShockBlock` | 1 | 0 | placeholder |
| `$6D` | `$E1` | `Obj_InvisibleShockBlock` | 13 | 29 | placeholder |
| `$6D` | `$F1` | `Obj_InvisibleShockBlock` | 3 | 8 | placeholder |
| `$78` | `$00` | `Obj_FBZDEZPlayerLauncher` | 10 | 0 | shared concrete |
| `$A4` | `$20` | `Obj_Spikebonker` | 4 | 10 | implemented |
| `$A4` | `$40` | `Obj_Spikebonker` | 3 | 1 | implemented |
| `$A5` | `$00` | `Obj_Chainspike` | 6 | 12 | implemented |
| `$A6` | `$00` | `Obj_DEZMiniboss` | 1 | 0 | placeholder |
| `$A7` | `$00` | `Obj_DEZEndBoss` | 0 | 1 | placeholder |

## Dynamic objects not in the placement files

Found by scanning `move.l #…,(a1)` allocations and `ChildObjDat_*` tables inside each owner's
address range. It is a list of labels to inventory, not a verified child count: read each
`ChildObjDat` (count word, then `dc.l routine` + offset pairs) when the slice starts, and record
allocator (`AllocateObject` vs `AllocateObjectAfterCurrent`/`CreateChild*`), slot order, art and
clock there.

| Owner / spawner | Dynamic objects (label, sonic3k.asm line) | Slice |
| --- | --- | --- |
| `SpawnLevelMainSprites` `loc_69AE` (`$B00` only) | `Obj_LevelIntro_PlayerRun` (89940): locks controls, holds right until `x ≥ start + $A0`, forces the camera | 0 (exists: `usesLevelIntroPlayerRun`) |
| `Obj_DEZTiltingBridge` | `loc_46E4C` (92714) | 4 |
| `Obj_DEZTorpedoLauncher` | `loc_4728A` torpedo (93033) | 4 |
| `Obj_DEZStaircase` | `loc_476FE` steps (93353) | 4 |
| `Obj_DEZHoverMachine` | `loc_494EA` (95708) | 4 |
| `Obj_Spikebonker` / `Obj_Chainspike` | `ChildObjDat_91C2C` (`loc_91AD2`, `loc_91BA8`), `ChildObjDat_91C34` (199122-199126) | 4 |
| `Obj_DEZTunnelLauncher_Main` | `Obj_DEZTunnelControl` (94250) | 5 |
| `Obj_DEZTunnelControl` | `Obj_DEZTransRingSpawner` (94376) | 5 |
| `Obj_DEZTransRingSpawner_Main` | `Obj_DEZTransRing` (94732) | 5 |
| `Obj_DEZMiniboss` (`$A6`; the object survives the act change: `loc_7E342`-`loc_7E4A2` run in act 2, slice 7) | `Obj_SpriteMask` (167915), `loc_7E25C` (168085), `loc_863C0` (168106), `Obj_CreateBossExplosion` ×2 (168154, 168162), `Obj_DecLevStartYGradual` / `Obj_IncLevEndYGradual` (168179, 168185), `Obj_TitleCard` (168270), `Obj_EndSignControl`; child tables `ChildObjDat_7EF8E … _7EFD2` (169484-169517: `loc_7E768`, `loc_7E80E`, `loc_7E916`, `loc_7EAB6`, `loc_7EACC`, `loc_7E4CE`, `loc_7E74A`, `loc_7E97E`, `loc_7EA6C`, `loc_7EB0E`) | 6 |
| `Obj_DEZEndBoss` (`$A7`) | `Obj_IncLevEndXGradual` (169754), persistent flag-clearer object `loc_7FC3E` allocated at defeat by `loc_7FBD6` (170720; clears `Reverse_gravity_flag` every frame, never deletes itself), Robotnik-run and `$1700` request chain `loc_7F2FE`/`loc_7F310`; child tables `ChildObjDat_7FC8C … _7FCCE` (170782-170811: `loc_7F71C`, `loc_7F782`, `loc_7F336`, `loc_7F64A`, `loc_7F398`, `loc_7F6FA`, `loc_7F60A`, `loc_7F7CE`, `loc_7F7F6`) | 8 |
| `DEZ3_ScreenInit` | `Obj_5A7C8` arena floor controller (120272), `Obj_5A8E6` (120275), `Obj_DEZ3_Boss` at `$3C0,$F8` (120278) | 9 |
| `DEZ3_BackgroundEvent` | `Obj_5A922` (120436), `Obj_5A94C` (120472) | 9 |
| `Obj_5A7C8` | `Obj_5A872` falling floor block, `AllocateObjectAfterCurrent` (120658) | 9 |
| `Obj_DEZ3_Boss` | `loc_80DE0`, `loc_80D72` (170949-170954), `Obj_Song_Fade_Transition` (170994), `loc_8642E` ×4, `loc_810A0`, `loc_804F0`, `loc_806DA`, `loc_80590`, `loc_80160`, `loc_85E64`, `loc_8060C`, `loc_807BC`, `Obj_DEZ3_Boss_Fireball` (172001) | 10 |

The `Obj_SpriteMask` child means the miniboss needs the production SAT mask post-pass (SKL `$8B`
is already concrete); include it in the sprite-composition audit.
