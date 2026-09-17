# LRZ placed-object inventory

Initial inventory at `9cba6dbb6`, 2026-09-17, for the
[LRZ bring-up plan](../../plans/2026-09-17-lrz-bring-up.md). Locked-on ROM SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`; source submodule
`1a454a0e335137a1a016d1090a9f4528d36944cf` (working reference has unrelated edits).
Decoded from the disassembly's `Levels/LRZ/Object Pos/{1,2,3}.bin` and `Ring Pos/{1,2,3}.bin` with a
throwaway script (research only; the engine reads the same bytes from the ROM). Every binary was
found byte-identical, exactly once, in the ROM:

| Data | Label | ROM offset | Bytes | Live records |
| --- | --- | --- | ---: | ---: |
| Act 1 objects (`$900`) | `LRZ1_Sprites` | `$1F6E50` | 3660 | **609** |
| Act 2 objects (`$901`) | `LRZ2_Sprites` | `$1F7C9C` | 2736 | **455** |
| Boss act objects (`$1600`) | `LRZ3_Sprites` | `$1FCBA2` | 216 | **35** |
| Act 1 rings | `LRZ1_Rings` | `$1F874C` | 1330 | **331** |
| Act 2 rings | `LRZ2_Rings` | `$1F8C7E` | 1130 | **281** |
| Boss act rings | `LRZ3_Rings` | `$1FCD82` | 214 | **52** |

Object records are six bytes (X word, Y word with flag bits 12-15, ID byte, subtype byte); each file
ends with one six-byte terminator `FFFF 0000 0000`, not counted. Ring files are four-byte X/Y records
ending in a two-byte `FFFF`; **the first record of every ring file is a `(0,0)` leading sentinel**
that the ROM skips (`loc_EB52`: `addq.w #4,a1` before counting `Perfect_rings_left`), so the live
counts above are one fewer than the record counts (332/282/53). Check that
`Sonic3kRingPlacement.parseRawRingRecords` does not spawn that sentinel as a ring at `(0,0)`
(unverified here; slice 0).

Total **1099** placed objects. IDs and subtypes below are hexadecimal. **Pointer set:** SK Set 2
(`Sprite_ListingK`, `Levels/Misc/Object pointers - SK Set 2.asm`) for all three acts: `loc_1B6A8`
selects it for `Current_zone` `$7-$D` and `$16+`, so both `$900/$901` (zone 9) and `$1600`
(zone `$16`) use it; it is independent of the art half. The engine's `S3kZoneSet.forZone` returns
`SKL` for every zone above 6, which is right for these acts.

The factory column is the classification read from `Sonic3kObjectRegistry` at `9cba6dbb6`, not
execution or route certification:

- **shared concrete**: an unconditional or LRZ-aware factory builds a real object. LRZ data and
  subtypes still need testing through production ("verify only" in the plan).
- **concrete zone-specific**: an LRZ-only class exists.
- **placeholder (SKL branch)**: the id is registered for its S3KL meaning and the factory returns a
  name-only `PlaceholderObjectInstance` under SKL.
- **unregistered**: no factory; the registry default returns a placeholder.

The slice column is the [plan](../../plans/2026-09-17-lrz-bring-up.md) slice that owns the row;
`V` is "already concrete: verify only" (slice 0 census, then the act's route slice), `V*` see notes.

| Classification | Rows | Placements | Act 1 | Act 2 | Boss act |
| --- | ---: | ---: | ---: | ---: | ---: |
| shared concrete | 105 | 533 | 340 | 174 | 19 |
| concrete zone-specific (`$31`) | 3 | 27 | 27 | 0 | 0 |
| shared concrete, SOZ-named sprite mask (`$8B`) | 3 | 5 | 3 | 0 | 2 |
| placeholder (SKL branch) | 120 | 507 | 226 | 267 | 14 |
| unregistered (`$1A $1C $1D $25`) | 20 | 27 | 13 | 14 | 0 |
| **Total** | **251** | **1099** | **609** | **455** | **35** |

Baseline placeholder count: **239 act 1 / 281 act 2 / 14 boss act** (534 of 1099).

| ID | Subtype | ROM pointer owner | Act 1 | Act 2 | Boss act | Current factory | Slice |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| `$01` | `$01` | `Obj_Monitor` | 5 | 1 | 0 | shared concrete | V |
| `$01` | `$02` | `Obj_Monitor` | 0 | 2 | 0 | shared concrete | V |
| `$01` | `$03` | `Obj_Monitor` | 16 | 6 | 0 | shared concrete | V |
| `$01` | `$05` | `Obj_Monitor` | 3 | 5 | 1 | shared concrete | V |
| `$01` | `$06` | `Obj_Monitor` | 5 | 1 | 1 | shared concrete | V |
| `$01` | `$07` | `Obj_Monitor` | 1 | 1 | 0 | shared concrete | V |
| `$01` | `$08` | `Obj_Monitor` | 1 | 1 | 0 | shared concrete | V |
| `$02` | `$00` | `Obj_PathSwap` | 0 | 2 | 0 | shared concrete | V |
| `$02` | `$01` | `Obj_PathSwap` | 0 | 2 | 0 | shared concrete | V |
| `$02` | `$05` | `Obj_PathSwap` | 0 | 2 | 0 | shared concrete | V |
| `$02` | `$06` | `Obj_PathSwap` | 0 | 3 | 0 | shared concrete | V |
| `$02` | `$09` | `Obj_PathSwap` | 0 | 1 | 0 | shared concrete | V |
| `$02` | `$21` | `Obj_PathSwap` | 4 | 6 | 0 | shared concrete | V |
| `$02` | `$22` | `Obj_PathSwap` | 2 | 0 | 0 | shared concrete | V |
| `$02` | `$41` | `Obj_PathSwap` | 4 | 1 | 0 | shared concrete | V |
| `$02` | `$49` | `Obj_PathSwap` | 1 | 0 | 0 | shared concrete | V |
| `$02` | `$4D` | `Obj_PathSwap` | 1 | 0 | 0 | shared concrete | V |
| `$02` | `$65` | `Obj_PathSwap` | 0 | 1 | 0 | shared concrete | V |
| `$02` | `$66` | `Obj_PathSwap` | 0 | 1 | 0 | shared concrete | V |
| `$05` | `$40` | `Obj_AIZLRZEMZRock` | 37 | 0 | 0 | shared concrete | 3 |
| `$05` | `$44` | `Obj_AIZLRZEMZRock` | 31 | 0 | 0 | shared concrete | 3 |
| `$05` | `$50` | `Obj_AIZLRZEMZRock` | 18 | 0 | 0 | shared concrete | 3 |
| `$05` | `$68` | `Obj_AIZLRZEMZRock` | 3 | 0 | 0 | shared concrete | 3 |
| `$05` | `$F4` | `Obj_AIZLRZEMZRock` | 0 | 21 | 0 | shared concrete | 7 |
| `$07` | `$00` | `Obj_Spring` | 0 | 8 | 0 | shared concrete | V |
| `$07` | `$01` | `Obj_Spring` | 3 | 0 | 0 | shared concrete | V |
| `$07` | `$02` | `Obj_Spring` | 0 | 7 | 0 | shared concrete | V |
| `$07` | `$03` | `Obj_Spring` | 7 | 5 | 0 | shared concrete | V |
| `$07` | `$10` | `Obj_Spring` | 3 | 1 | 0 | shared concrete | V |
| `$07` | `$12` | `Obj_Spring` | 2 | 3 | 0 | shared concrete | V |
| `$08` | `$00` | `Obj_Spikes` | 10 | 0 | 0 | shared concrete | V |
| `$08` | `$01` | `Obj_Spikes` | 1 | 0 | 0 | shared concrete | V |
| `$08` | `$10` | `Obj_Spikes` | 2 | 2 | 0 | shared concrete | V |
| `$08` | `$20` | `Obj_Spikes` | 0 | 3 | 0 | shared concrete | V |
| `$08` | `$30` | `Obj_Spikes` | 0 | 7 | 0 | shared concrete | V |
| `$08` | `$40` | `Obj_Spikes` | 3 | 1 | 0 | shared concrete | V |
| `$08` | `$50` | `Obj_Spikes` | 1 | 0 | 0 | shared concrete | V |
| `$08` | `$70` | `Obj_Spikes` | 0 | 1 | 0 | shared concrete | V |
| `$0D` | `$00` | `Obj_BreakableWall` | 0 | 4 | 0 | shared concrete | 7 |
| `$0E` | `$00` | `Obj_TwistedRamp` | 3 | 1 | 0 | shared concrete | V |
| `$0F` | `$00` | `Obj_CollapsingBridge` | 0 | 24 | 0 | shared concrete | 7/9 |
| `$0F` | `$01` | `Obj_CollapsingBridge` | 0 | 0 | 6 | shared concrete | 7/9 |
| `$0F` | `$03` | `Obj_CollapsingBridge` | 0 | 1 | 0 | shared concrete | 7/9 |
| `$0F` | `$0A` | `Obj_CollapsingBridge` | 0 | 0 | 2 | shared concrete | 7/9 |
| `$15` | `$00` | `Obj_LRZCorkscrew` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$16` | `$00` | `Obj_LRZWallRide` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$17` | `$00` | `Obj_LRZSinkingRock` | 11 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$18` | `$01` | `Obj_LRZFallingSpike` | 4 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$18` | `$02` | `Obj_LRZFallingSpike` | 5 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$18` | `$03` | `Obj_LRZFallingSpike` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$18` | `$04` | `Obj_LRZFallingSpike` | 3 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$18` | `$05` | `Obj_LRZFallingSpike` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$00` | `Obj_LRZDoor` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$01` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$02` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$03` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$04` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$05` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$06` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$07` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$08` | `Obj_LRZDoor` | 0 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$09` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0A` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0B` | `Obj_LRZDoor` | 1 | 1 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0C` | `Obj_LRZDoor` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0D` | `Obj_LRZDoor` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0E` | `Obj_LRZDoor` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$19` | `$0F` | `Obj_LRZDoor` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1A` | `$00` | `Obj_LRZBigDoor` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1B` | `$10` | `Obj_LRZFireballLauncher` | 4 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$14` | `Obj_LRZFireballLauncher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$16` | `Obj_LRZFireballLauncher` | 4 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$18` | `Obj_LRZFireballLauncher` | 5 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$1A` | `Obj_LRZFireballLauncher` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$1C` | `Obj_LRZFireballLauncher` | 3 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$20` | `Obj_LRZFireballLauncher` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$24` | `Obj_LRZFireballLauncher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$28` | `Obj_LRZFireballLauncher` | 3 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$30` | `Obj_LRZFireballLauncher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1B` | `$38` | `Obj_LRZFireballLauncher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1C` | `$01` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$02` | `Obj_LRZButtonHorizontal` | 0 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$03` | `Obj_LRZButtonHorizontal` | 0 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$04` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$05` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$06` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$07` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$08` | `Obj_LRZButtonHorizontal` | 0 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$09` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$0A` | `Obj_LRZButtonHorizontal` | 0 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$0B` | `Obj_LRZButtonHorizontal` | 1 | 1 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$0C` | `Obj_LRZButtonHorizontal` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$0D` | `Obj_LRZButtonHorizontal` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1C` | `$0F` | `Obj_LRZButtonHorizontal` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1D` | `$A0` | `Obj_LRZShootingTrigger` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1D` | `$C2` | `Obj_LRZShootingTrigger` | 1 | 0 | 0 | unregistered (default placeholder) | 3 |
| `$1E` | `$1A` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1E` | `$1D` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1E` | `$20` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1E` | `$46` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1E` | `$B6` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1E` | `$B9` | `Obj_LRZDashElevator` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1F` | `$50` | `Obj_LRZLavaFall` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1F` | `$60` | `Obj_LRZLavaFall` | 3 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$1F` | `$70` | `Obj_LRZLavaFall` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$20` | `$02` | `Obj_LRZSwingingSpikeBall` | 2 | 10 | 0 | placeholder (SKL branch) | 3 |
| `$20` | `$03` | `Obj_LRZSwingingSpikeBall` | 4 | 4 | 0 | placeholder (SKL branch) | 3 |
| `$20` | `$04` | `Obj_LRZSwingingSpikeBall` | 5 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$09` | `Obj_LRZSmashingSpikePlatform` | 3 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$0B` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$0F` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$10` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$11` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$14` | `Obj_LRZSmashingSpikePlatform` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$19` | `Obj_LRZSmashingSpikePlatform` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$1A` | `Obj_LRZSmashingSpikePlatform` | 2 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$1C` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$21` | `$1D` | `Obj_LRZSmashingSpikePlatform` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$22` | `$00` | `Obj_LRZSpikeBall` | 5 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$22` | `$C0` | `Obj_LRZSpikeBall` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$24` | `$55` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$56` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$57` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$58` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$59` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$D5` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$D6` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$D7` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$D8` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$24` | `$D9` | `Obj_AutomaticTunnel` | 0 | 1 | 0 | shared concrete | 7 |
| `$25` | `$80` | `Obj_LRZChainedPlatforms` | 0 | 1 | 0 | unregistered (default placeholder) | 7 |
| `$25` | `$81` | `Obj_LRZChainedPlatforms` | 0 | 1 | 0 | unregistered (default placeholder) | 7 |
| `$25` | `$82` | `Obj_LRZChainedPlatforms` | 0 | 1 | 0 | unregistered (default placeholder) | 7 |
| `$28` | `$00` | `Obj_InvisibleBlock` | 1 | 0 | 0 | shared concrete | V |
| `$28` | `$11` | `Obj_InvisibleBlock` | 3 | 0 | 7 | shared concrete | V |
| `$28` | `$12` | `Obj_InvisibleBlock` | 0 | 1 | 0 | shared concrete | V |
| `$28` | `$13` | `Obj_InvisibleBlock` | 3 | 11 | 0 | shared concrete | V |
| `$28` | `$17` | `Obj_InvisibleBlock` | 0 | 0 | 1 | shared concrete | V |
| `$28` | `$31` | `Obj_InvisibleBlock` | 11 | 2 | 0 | shared concrete | V |
| `$28` | `$32` | `Obj_InvisibleBlock` | 0 | 1 | 0 | shared concrete | V |
| `$28` | `$34` | `Obj_InvisibleBlock` | 1 | 0 | 0 | shared concrete | V |
| `$28` | `$51` | `Obj_InvisibleBlock` | 1 | 0 | 0 | shared concrete | V |
| `$28` | `$71` | `Obj_InvisibleBlock` | 1 | 0 | 0 | shared concrete | V |
| `$28` | `$91` | `Obj_InvisibleBlock` | 2 | 0 | 0 | shared concrete | V |
| `$29` | `$08` | `Obj_LRZFlameThrower` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$10` | `Obj_LRZFlameThrower` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$13` | `Obj_LRZFlameThrower` | 0 | 15 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$14` | `Obj_LRZFlameThrower` | 0 | 4 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$15` | `Obj_LRZFlameThrower` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$16` | `Obj_LRZFlameThrower` | 0 | 6 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$18` | `Obj_LRZFlameThrower` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$93` | `Obj_LRZFlameThrower` | 0 | 4 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$94` | `Obj_LRZFlameThrower` | 0 | 4 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$95` | `Obj_LRZFlameThrower` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$29` | `$96` | `Obj_LRZFlameThrower` | 0 | 12 | 0 | placeholder (SKL branch) | 7 |
| `$2B` | `$00` | `Obj_LRZOrbitingSpikeBallHorizontal` | 0 | 8 | 0 | placeholder (SKL branch) | 7 |
| `$2B` | `$80` | `Obj_LRZOrbitingSpikeBallHorizontal` | 0 | 4 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$00` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$10` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$20` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$30` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$40` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$50` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$60` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$70` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$80` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$90` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$A0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$B0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$C0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$D0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$E0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2C` | `$F0` | `Obj_LRZOrbitingSpikeBallVertical` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$00` | `Obj_LRZSolidMovingPlatforms` | 0 | 8 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$01` | `Obj_LRZSolidMovingPlatforms` | 0 | 9 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$02` | `Obj_LRZSolidMovingPlatforms` | 0 | 12 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$04` | `Obj_LRZSolidMovingPlatforms` | 0 | 3 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$05` | `Obj_LRZSolidMovingPlatforms` | 0 | 4 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$06` | `Obj_LRZSolidMovingPlatforms` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$10` | `Obj_LRZSolidMovingPlatforms` | 0 | 10 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$12` | `Obj_LRZSolidMovingPlatforms` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$13` | `Obj_LRZSolidMovingPlatforms` | 0 | 1 | 0 | placeholder (SKL branch) | 7 |
| `$2D` | `$15` | `Obj_LRZSolidMovingPlatforms` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$2F` | `$22` | `Obj_StillSprite` | 59 | 0 | 0 | shared concrete | V |
| `$2F` | `$23` | `Obj_StillSprite` | 12 | 0 | 0 | shared concrete | V |
| `$2F` | `$24` | `Obj_StillSprite` | 6 | 0 | 0 | shared concrete | V |
| `$2F` | `$25` | `Obj_StillSprite` | 28 | 0 | 0 | shared concrete | V |
| `$2F` | `$26` | `Obj_StillSprite` | 12 | 0 | 0 | shared concrete | V |
| `$30` | `$02` | `Obj_AnimatedStillSprite` | 7 | 0 | 0 | shared concrete | V |
| `$30` | `$03` | `Obj_AnimatedStillSprite` | 0 | 6 | 0 | shared concrete | V |
| `$31` | `$00` | `Obj_LRZCollapsingBridge` | 11 | 0 | 0 | concrete zone-specific | V |
| `$31` | `$01` | `Obj_LRZCollapsingBridge` | 12 | 0 | 0 | concrete zone-specific | V |
| `$31` | `$02` | `Obj_LRZCollapsingBridge` | 4 | 0 | 0 | concrete zone-specific | V |
| `$32` | `$00` | `Obj_LRZTurbineSprites` | 0 | 7 | 0 | placeholder (SKL branch) | 7 |
| `$32` | `$01` | `Obj_LRZTurbineSprites` | 0 | 11 | 0 | placeholder (SKL branch) | 7 |
| `$33` | `$03` | `Obj_Button` | 1 | 0 | 0 | shared concrete | V |
| `$33` | `$05` | `Obj_Button` | 0 | 1 | 0 | shared concrete | V |
| `$33` | `$0A` | `Obj_Button` | 1 | 0 | 0 | shared concrete | V |
| `$33` | `$0E` | `Obj_Button` | 1 | 0 | 0 | shared concrete | V |
| `$34` | `$01` | `Obj_StarPost` | 1 | 1 | 1 | shared concrete | V |
| `$34` | `$02` | `Obj_StarPost` | 1 | 1 | 0 | shared concrete | V |
| `$34` | `$03` | `Obj_StarPost` | 1 | 1 | 0 | shared concrete | V |
| `$34` | `$04` | `Obj_StarPost` | 1 | 1 | 0 | shared concrete | V |
| `$34` | `$05` | `Obj_StarPost` | 1 | 1 | 0 | shared concrete | V |
| `$34` | `$06` | `Obj_StarPost` | 1 | 0 | 0 | shared concrete | V |
| `$37` | `$50` | `Obj_LRZSpikeBallLauncher` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$37` | `$60` | `Obj_LRZSpikeBallLauncher` | 0 | 5 | 0 | placeholder (SKL branch) | 7 |
| `$37` | `$70` | `Obj_LRZSpikeBallLauncher` | 0 | 2 | 0 | placeholder (SKL branch) | 7 |
| `$6B` | `$11` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | 0 | shared concrete | V |
| `$6B` | `$13` | `Obj_InvisibleHurtBlockVertical` | 2 | 3 | 0 | shared concrete | V |
| `$6B` | `$14` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | 0 | shared concrete | V |
| `$6B` | `$15` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | 0 | shared concrete | V |
| `$6B` | `$32` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | 0 | shared concrete | V |
| `$6B` | `$41` | `Obj_InvisibleHurtBlockVertical` | 0 | 1 | 0 | shared concrete | V |
| `$6B` | `$51` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | 0 | shared concrete | V |
| `$6B` | `$71` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | 0 | shared concrete | V |
| `$6B` | `$81` | `Obj_InvisibleHurtBlockVertical` | 2 | 0 | 0 | shared concrete | V |
| `$6B` | `$91` | `Obj_InvisibleHurtBlockVertical` | 1 | 0 | 0 | shared concrete | V |
| `$6B` | `$A1` | `Obj_InvisibleHurtBlockVertical` | 2 | 0 | 0 | shared concrete | V |
| `$6C` | `$0E` | `Obj_TensionBridge` | 1 | 0 | 0 | shared concrete | V |
| `$6E` | `$31` | `Obj_InvisibleLavaBlock` | 7 | 0 | 0 | placeholder (SKL branch) | 1 |
| `$6E` | `$71` | `Obj_InvisibleLavaBlock` | 8 | 4 | 0 | placeholder (SKL branch) | 1 |
| `$6E` | `$B1` | `Obj_InvisibleLavaBlock` | 1 | 0 | 0 | placeholder (SKL branch) | 1 |
| `$6E` | `$F1` | `Obj_InvisibleLavaBlock` | 18 | 0 | 6 | placeholder (SKL branch) | 1 |
| `$80` | `$03` | `Obj_HiddenMonitor` | 1 | 0 | 0 | shared concrete | V |
| `$80` | `$05` | `Obj_HiddenMonitor` | 1 | 0 | 0 | shared concrete | V |
| `$80` | `$06` | `Obj_HiddenMonitor` | 1 | 0 | 0 | shared concrete | V |
| `$85` | `$02` | `Obj_SSEntryRing` | 1 | 0 | 0 | shared concrete | V |
| `$85` | `$03` | `Obj_SSEntryRing` | 1 | 0 | 0 | shared concrete | V |
| `$85` | `$04` | `Obj_SSEntryRing` | 1 | 0 | 0 | shared concrete | V |
| `$85` | `$05` | `Obj_SSEntryRing` | 0 | 1 | 0 | shared concrete | V |
| `$85` | `$06` | `Obj_SSEntryRing` | 0 | 1 | 0 | shared concrete | V |
| `$85` | `$07` | `Obj_SSEntryRing` | 0 | 1 | 0 | shared concrete | V |
| `$85` | `$08` | `Obj_SSEntryRing` | 0 | 1 | 0 | shared concrete | V |
| `$85` | `$09` | `Obj_SSEntryRing` | 0 | 1 | 0 | shared concrete | V |
| `$8B` | `$44` | `Obj_SpriteMask` | 0 | 0 | 2 | shared concrete (SOZ-named mask) | V* |
| `$8B` | `$84` | `Obj_SpriteMask` | 1 | 0 | 0 | shared concrete (SOZ-named mask) | V* |
| `$8B` | `$F1` | `Obj_SpriteMask` | 2 | 0 | 0 | shared concrete (SOZ-named mask) | V* |
| `$99` | `$00` | `Obj_Fireworm` | 20 | 9 | 0 | placeholder (SKL branch) | 4 |
| `$9A` | `$00` | `Obj_Iwamodoki` | 32 | 34 | 0 | placeholder (SKL branch) | 4 |
| `$9B` | `$00` | `Obj_Toxomister` | 22 | 9 | 0 | placeholder (SKL branch) | 4 |
| `$9C` | `$00` | `Obj_LRZRockCrusher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$9C` | `$02` | `Obj_LRZRockCrusher` | 1 | 0 | 0 | placeholder (SKL branch) | 3 |
| `$9D` | `$00` | `Obj_LRZMiniboss` | 1 | 0 | 0 | placeholder (SKL branch) | 6 |
| `$9E` | `$00` | `Obj_LRZ3Autoscroll` | 0 | 0 | 1 | placeholder (SKL branch) | 9 |
| `$AD` | `$00` | `Obj_LRZ3Platform` | 0 | 0 | 1 | placeholder (SKL branch) | 9 |
| `$AD` | `$01` | `Obj_LRZ3Platform` | 0 | 0 | 1 | placeholder (SKL branch) | 9 |
| `$AD` | `$02` | `Obj_LRZ3Platform` | 0 | 0 | 2 | placeholder (SKL branch) | 9 |
| `$AD` | `$04` | `Obj_LRZ3Platform` | 0 | 0 | 3 | placeholder (SKL branch) | 9 |
| `$AE` | `$00` | `Obj_LRZ2CutsceneKnuckles` | 0 | 1 | 0 | placeholder (SKL branch) | 8 |
| `$B3` | `$2D` | `Obj_StartNewLevel` | 0 | 1 | 0 | placeholder (SKL branch) | 8 |

## Per-ID totals and notes

| ID | Owner | Act 1 | Act 2 | Boss | Note |
| --- | --- | ---: | ---: | ---: | --- |
| `$05` | `Obj_AIZLRZEMZRock` | 89 | 21 | 0 | `AizLrzRockObjectInstance` has `LRZ1`/`LRZ2` configs; subtypes `$40 $44 $50 $68` (act 1) and `$F4` (act 2) need a production check each |
| `$0F` | `Obj_CollapsingBridge` | 0 | 25 | 8 | ROM picks mappings by `Current_zone`: zone 9 → `Map_LRZCollapsingPlatform`, zone `$16` → `Map_HPZCollapsingBridge` **for `$1600` too**. The engine switch does the same (`ZONE_HPZ` = `$16` → `initHPZ`). Verify the `$1600` art at that tile is what LRZ3's PLC loads |
| `$19`/`$1C` | `Obj_LRZDoor` / `Obj_LRZButtonHorizontal` | 15/10 | 11/11 | 0 | Both index `Level_trigger_array` with `subtype & $F`. Act 1 doors `$00-$07,$09-$0F`: triggers come from `$1C` (`1,4,5,6,7,9,B,C,D,F`), shared `$33 Obj_Button` (`3,A,E`) and, by low nibble, the `$1D` shooting triggers (`$A0` → 0, `$C2` → 2; `sub_42EC0` sets bit 0 only when the touching player has `anim == 2`; high nibble × 4 = shot period). Act 2 doors `$01-$0B` all have a `$1C` button; `$33/$05` is extra |
| `$1D` | `Obj_LRZShootingTrigger` | 2 | 0 | 0 | Subtypes `$A0`, `$C2` |
| `$24` | `Obj_AutomaticTunnel` | 0 | 10 | 0 | Subtypes `$55-$59` and `$D5-$D9`; engine paths 21-25 are labelled LRZ2 |
| `$28` | `Obj_InvisibleBlock` | 23 | 15 | 8 | Shared |
| `$2F` | `Obj_StillSprite` | 117 | 0 | 0 | Subtypes `$22-$26` = engine table entries 34-38 (LRZ rock/gear) |
| `$30` | `Obj_AnimatedStillSprite` | 7 | 6 | 0 | Subtype 2 = LRZ1 lava, 3 = LRZ2 |
| `$31` | `Obj_LRZCollapsingBridge` | 27 | 0 | 0 | `LrzCollapsingBridgeInstance`. Also spawned dynamically by the rock crusher with `$32(a1) = 1` |
| `$6E` | `Obj_InvisibleLavaBlock` | 34 | 4 | 6 | Sets `shield_reaction` bit 4 then falls into `Obj_InvisibleHurtBlockHorizontal`. `sub_1F58C` skips the hurt when `shield_reaction(a0) & $73 & shield_reaction(a1)` is non-zero; the engine's H hurt block has no such mask yet |
| `$8B` | `Obj_SpriteMask` | 3 | 0 | 2 | Factory builds `SozSpriteMaskObjectInstance` for any SKL zone. `V*`: confirm it carries no SOZ-only assumption, then rename or leave |
| `$99 $9A $9B` | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister` | 20/32/22 | 9/34/9 | 0 | Art keys exist; no badnik classes |
| `$9C` | `Obj_LRZRockCrusher` | 2 | 0 | 0 | Subtype 0 at `($FA0,$71C)`, subtype 2 at `($5A0,$81C)`. **Act 1**, not act 2 |
| `$9D` | `Obj_LRZMiniboss` | 1 | 0 | 0 | Placed at `($2CA0,$880)`: the miniboss is a layout object, not event-spawned |
| `$9E` | `Obj_LRZ3Autoscroll` | 0 | 0 | 1 | `($A0,$4AC)` |
| `$AD` | `Obj_LRZ3Platform` | 0 | 0 | 7 | Subtypes 0, 1, 2 (×2), 4 (×3) |
| `$AE` | `Obj_LRZ2CutsceneKnuckles` | 0 | 1 | 0 | `($38B0,$240)`; self-deletes when `character_id == 2`; range `word_63B94` = X `−$10…+0`, Y `−$240…+0` |
| `$B3` | `Obj_StartNewLevel` | 0 | 1 | 0 | `($3FE0,$E0)`, subtype `$2D` → `$1601` (decode reads a word at `subtype`; SST `$2D` must be 0). No character gate in the object; `SaveGame` only when `Player_mode == 3` and zone 9 |

Not placed in any LRZ act although LRZ-named in the pointer table: `$2E Obj_LRZSolidRock`.

## Dynamic objects not in placements

| Object | Spawner (label) | Act |
| --- | --- | --- |
| `Obj_56EA0` dome lava platform | `loc_56E40` (region entry from `sub_56DCA`) | `$900` |
| `Obj_LRZCollapsingBridge` ×2 at `($F00,$760)`, `($F80,$760)`; ×1 at `($540,$860)` | `loc_90512` / `loc_9056E` (rock crusher timer child `loc_90502`) | `$900` |
| Rock crusher children | `ChildObjDat_9067A`, `ChildObjDat_90626`, `ChildObjDat_90658`, `Child7_ChangeLevSize` | `$900` |
| Miniboss children and `Obj_EndSignControl` | `Obj_LRZMiniboss` child tables (count them in slice 6); end sign via `jmp (Obj_EndSignControl)` inside the miniboss code | `$900` |
| `loc_5711E` BG Death Egg sprite | `LRZ2_BackgroundInit`, `loc_5700C` | `$901` (not Knuckles) |
| `Obj_CutsceneKnuckles` subtype `$24`, boulder `loc_63C3E`, `loc_863C0` | `loc_63B40` / `Obj_LRZ2CutsceneKnuckles` | `$901` |
| `Obj_59FC4` sloped lava surface | `LRZ3_BackgroundInit` | `$1600` |
| `Obj_CollapsingBridge` at `($60,$4D0)`, flash object | `loc_794BE` (Death Egg flash sequence, from `loc_79416`) | `$1600` |
| `Obj_LRZEndBoss` | `loc_59C8C` (`LRZ3_BackgroundEvent`) | `$1600` |
| `Obj_EggCapsule` | `loc_79998` | `$1600` |
| `Obj_IncLevEndXGradual`, `Obj_StartNewLevel` `$2D` at `($FE8,$5E0)` | `loc_79A30` and the lines before it | `$1600` |
| Badnik children (Fireworm segments, Iwamodoki debris, Toxomister mist) | each badnik's child tables | `$900`, `$901` |
| Rock sprites | `Draw_LRZ_Special_Rock_Sprites` / `sub_1CB68`: **not objects**, no SST slot; emitted after bucket 0 and before bucket 1, zone 9 only | `$900`, `$901` |

Dynamic children, art and audio ownership remain an explicit inventory obligation before each
family is implemented. This count is factory coverage, not a claim that any route is verified.
