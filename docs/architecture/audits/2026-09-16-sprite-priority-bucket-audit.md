# Sprite priority bucket audit (2026-09-16)

Base: develop `3998a7029`. Branch: `bugfix/ai-sprite-priority-buckets`.

## Question

Implementing agents regularly get an object's sprite priority (bucket) wrong. Is the
engine's bucket model different from the ROM's, causing translation errors?

## Answer

The model is the same; the encoding and two silent engine defaults are what agents miss.

- **Model.** All three ROMs keep eight `$80`-byte display lists
  (`Sprite_Table_Input` / `Object_Display_Lists`). `DisplaySprite` (S1/S2) and
  `Draw_Sprite` (S3K, sonic3k.asm:36131) append the object to the list its `priority`
  field selects; BuildSprites walks list 0 first, so bucket 0 is front-most. The engine's
  `RenderPriority` (0-7) and `ObjectManager` (paints bucket 7 first) match this.
- **Encoding.** S1/S2 store the bucket index as a byte (`move.b #4,priority(a0)`).
  S3K stores the list byte offset as a word: `move.w #$280,priority(a0)` is bucket 5
  (`word / $80`), and ObjDat/ObjDat3 tables consumed by `SetUp_ObjAttributes3`
  (sonic3k.asm:176908) carry the same word third. `$80` reads like a flag but is bucket 1.
- **Silent clamp.** `RenderPriority.clamp(0x280)` returned 7. Four S3K classes returned
  the raw word: `CnzHoverFanInstance`, `CnzCannonInstance` (both `$280`, bucket 5),
  `CnzTrapDoorInstance`, `LrzCollapsingBridgeInstance` (both `$80`, bucket 1). The LRZ
  bridge drew behind the player instead of in front.
- **Silent default.** `ObjectInstance.getPriorityBucket()` defaults to 0, front-most. In
  the ROM a fresh SST slot is also 0, but nearly every displayed object writes `priority`
  in its init; the engine has no equivalent pressure. The structural guard written for
  this audit flagged 88 concrete classes (nested included) that draw with no bucket in
  their chain; the sampled priority fix commits since June (`643a4b4a7`, `442ccf8e6`,
  `8149d03ff`, `a37a4a55d`) were all "add the missing override", not "fix the number".
- **Two properties share one word.** The art word's bit 15 (`make_art_tile(.., 1)`) is
  sprite-versus-plane priority, `isHighPriority()` in the engine. The disassembly calls
  both "priority". `RenderPriority.HIGH_START`/`isHigh()` (dead code) suggested bucket
  4+ meant "high"; removed.
- **Guidance gap.** The three `*-implement-object` skills said nothing about priority;
  330 of 360 S3K `getPriorityBucket` bodies cited no ROM constant.

## What landed

- `RenderPriority.bucket(int)` (throws outside 0-7) and `RenderPriority.fromS3kWord(int)`
  (requires a multiple of `$80` up to `$380`); `clamp` is reserved for the render loop.
- `TestObjectPriorityBucketGuard` (`-Pguards`): every concrete `ObjectInstance` whose own
  `appendRenderCommands` body does something must declare `getPriorityBucket()` in its
  chain. A ROM priority of 0 opts in by returning `bucket(0)` with the citation.
- `ObjectScaffoldTool` emits a bucket placeholder that throws until transcribed.
- Skill checklists (s1/s2/s3k-implement-object) and the implementation pitfalls
  catalogue state the encoding rule.
- Every flagged class audited against its ROM routine (tables below).

## Shared cross-game classes (lead)

| Class | ROM writes | Bucket |
|---|---|---|
| `AnimalObjectInstance` | S1 `#6` (28, 29 Animals and Points.asm:142), S2 `#6` (s2.asm:24590), S3K `$300` (sonic3k.asm:61043) | 6 |
| `EggPrisonAnimalInstance` | capsule creates without writing priority (s2.asm:85045, sonic3k.asm:198673); after delay S2 `#1` (24738), S3K `$80` (61203) | 0 waiting, 1 released |
| `ExplosionObjectInstance` | S1 `#1` (27, 3F Explosions.asm:35), S2 `#1` (46728), S3K `$80` (42196) | 1 |
| `SplashObjectInstance` | S1 `#1` (08 LZ Water Splash.asm:21), S2 Obj08 `#1` (42725), S3K Obj_DashDust `$80` (33971) | 1 |
| `SkidDustObjectInstance` | S2 Obj08 `#1` (42725), S3K Obj_DashDust `$80` (33971) | 1 |
| `BreathingBubbleInstance` | drowning: S1 `#1` (0A LZ Drowning Countdown.asm:39), S2 `#1` (41890), S3K `$80` (64753); ChopChop child S2 Obj91 `#4` (74201) | 1; spawner-supplied (ChopChop 4) |
| `BossExplosionObjectInstance` | S2 Obj58 `#0` (61320), S3K ObjDat word 0; S1 reuses Obj3F `#1` (27, 3F Explosions.asm:78) | 0; S1 callers and `AbstractS1EggmanBossInstance` pass 1 |
| `BoxObjectInstance`, `PlaceholderObjectInstance` | engine primitives, not ROM objects | explicit 0 |

## Sonic 1 and Sonic 2 classes (audit lane)

Disassembly line numbers are the current submodule files.

| Class | ROM label / file | Lines | ROM byte | Bucket | Art bit 15 | Action |
|---|---|---|---|---|---|---|
| Sonic1SceneryObjectInstance | Scen_Main / Scen_Values, _incObj/1C GHZ, SYZ Scenery.asm | 28; 47, 53 | subtype 0-2: 2; subtype 3: 1 | 2 or 1 by subtype | clear | added |
| Sonic1ElectrocuterObjectInstance | Elec_Main, 6E SBZ Electrocuter.asm | 18-31 | never written | 0 (verified) | clear | added |
| Sonic1EdgeWallObjectInstance | 44 GHZ Edge Walls.asm | 23 | 6 | 6 | clear | added |
| Sonic1FlappingDoorObjectInstance | Flap_Main, 0C LZ Flapping Door.asm | 19-29 | never written | 0 (verified) | clear | added |
| Sonic1MonitorPowerUpObjectInstance | Pow_Main, 26, 2E Monitors and Power-Ups.asm | 233 | 3 | 3 | clear | added |
| Sonic1RockObjectInstance | Rock_Main, 3B GHZ Purple Rock.asm | 27 | 4 | 4 | clear | replaced unused constant, added |
| Sonic1SmallDoorObjectInstance | 2A SBZ Small Door.asm | 22 | 4 | 4 | clear | added |
| Sonic1SpinPlatformObjectInstance | Spin_Main, 69 SBZ Spinning Platforms and Trapdoors.asm | 22-64 | never written | 0 (verified) | clear | added |
| Sonic1StomperDoorObjectInstance | Sto_Main, 6B SBZ Stomper and Sliding Door.asm | 84 | 4 | 4 | clear | replaced unused constant, added |
| Sonic1SawObjectInstance | Saw_Main, 6A SBZ Saws and Pizza Cutters.asm | 25 | 4 | 4 | clear | added |
| Sonic1ScrapEggmanInstance (body / button) | SEgg_ObjData, 82, 83 SBZ Eggman Cutscene and Crumbling Floor.asm | 30, 44 / 31, 63 | 3 / 3 | 3 / 3 | clear | added (button has its own table row) |
| BridgeStakeObjectInstance | Obj1C_Init / Obj1C_InitData | 24093; 24023-24043 | per subtype 6,6,1,6,4,4,1,1,1 then 4 | table by subtype | clear | added table-driven override |
| BonusBlockObjectInstance | ObjD8_Init | 60159 | 1 | 1 | clear | added |
| BumperObjectInstance | Obj44_Init | 45095 | 1 | 1 | clear | added |
| EHZWaterfallObjectInstance | Obj49_Init | 46433 | 0 (explicit) | 0 | clear | added |
| HexBumperObjectInstance | ObjD7_Init | 59967 | 1 | 1 | clear | added |
| MTZLongPlatformCogInstance | Obj65_Init / child alloc | 52863; 52918 | 4 | 4 | clear | added |
| MTZLongPlatformObjectInstance | Obj65_Init | 52863 | 4 | 4 | clear | added |
| MTZPlatformObjectInstance | Obj6B_Init | 54411 | 3 | 3 | clear | added |
| ARZPlatformObjectInstance | Obj18_Init | 23195 | 4 | 4 | clear | added |
| CPZPlatformObjectInstance | Obj19_Init | 47980; art 47961/47965/47969 | 4 | 4 | set only for the WFZ art | added; `isHighPriority()` true in WFZ |
| CPZStaircaseObjectInstance | Obj78_LoadSubObject | 56105 | 3 | 3 | clear | added |
| MCZRotPformsObjectInstance | Obj6A_Init | 54170 | 4 | 4 | clear | added |
| MonitorContentsObjectInstance | Obj2E_Init | 25750; art 25747 | 3 | 3 | set | added; `isHighPriority()` true |
| SidewaysPformObjectInstance | Obj7A_LoadSubObject | 56325; art 56298/56302 | 4 | 4 | set for CPZ, clear for MCZ | added; `isHighPriority()` = !MCZ |
| BarrierObjectInstance | Obj2D_Init | 24323 | 4 | 4 | clear | added |
| LauncherBallObjectInstance | Obj48_Init | 51284 | 1 | 1 | clear | added |
| ForcedSpinObjectInstance | Obj84_Init | 46782 | 5 (never displayed) | 5 | clear | added |
| WFZPalSwitcherObjectInstance | Obj8B_Init | 47004 | 5 (never displayed) | 5 | clear | added |
| InvisibleBlockObjectInstance | Obj74_Init | 46586-46602 | never written | 0 (verified) | set (46589) | added; `isHighPriority()` true |
| Sonic2OOZBossInstance | Obj55_Init / Laser_Init / Laser_CreateWave | 68382; 68987; 69066 | 3 (inside `if ~~fixBugs`), laser 4, wave 2 | live word, bucket `(word >> 8) & 7` | clear | added runtime model (below) |

OOZ boss runtime model: Obj55 is multi-sprite and its `priority` word (`$18`) is the same SST
word as `sub3_y_pos` (s2.constants.asm:23, 99). With `fixBugs = 0` the ROM keeps displaying
through `DisplaySprite` (s2.asm:68513-68520), so every write of the second child sprite's y
also rewrites the bucket, and the value persists into later phases. The class syncs its
priority word from that child y at the three chain-loop sites it already implements
(s2.asm:68660-68665, 68831-68837, 68915-68934). The bug-fixed branch would force list 3
through `DisplaySprite3`.

## Sonic 3 & Knuckles classes (audit lane)

| Class | ROM label | sonic3k.asm | ROM priority word(s) | Bucket set | Art bit 15 handled? | Action |
|---|---|---|---|---|---|---|
| AizRockFragmentChild | BreakObjectToPieces fragment | 45811 (45810 art) | `move.b priority(a0),priority(a1)` copies only the high byte into a zeroed slot: $0000 | bucket(0) | set by `ori.w #high_priority`; inherited `true` from GravityDebrisChild | verified-0 override added |
| RockDebrisChild | BreakObjectToPieces fragment | 45811 | same quirk: $0000 | bucket(0) | set; inherited `true` | verified-0 override added |
| AizMinibossFlameChild | Obj_AIZMiniboss_Flame / ObjDat_AIZMiniboss_Flame; AIZMiniboss_ImpactFlame_Init | 137848, 137182 | $100; impact flame rewrites $100 | 2 | set (137847); already `true` | override added |
| CutsceneKnucklesAiz2Instance | CutsceneKnux_AIZ2 / ObjSlot_CutsceneKnux via SetUp_ObjAttributesSlotted | 134800, 178886 | $180 | 3 | set (134797); already `true`, speculative comment replaced with citation | override added |
| CutsceneKnucklesLbz1ThrownBomb | loc_6282A / ObjDat3_6640E | 134831 | $80 | 1 | set (134830); was default `false` | override + `isHighPriority()=true` |
| CutsceneKnucklesSkIntroInstance (Knuckles) | CutsceneKnux_SKIntro / ObjSlot_KnuxIntroLay | 134814 | $180 | 3 | clear: `make_art_tile(ArtTile_Player_2,0,0)` (134811); no later art_tile write in 130554-131058; was `true` | override + `isHighPriority()` flipped to `false` |
| CutsceneKnucklesSkIntroBombInstance (same file) | loc_63790 / ObjDat3_66486 | 134881 | $180 | 3 | clear (134880) | override added |
| CutsceneKnucklesSkIntroEggRoboEntryInstance | loc_639C8 / ObjDat3_919A6 | 198862 | $280 | 5 | set (198861); was default `false` | override + `true` |
| CutsceneKnucklesSkIntroEggRoboLowerVisualChild | loc_916A8 / word_919BE | 198870 | $280 | 5 | inherited from EggRobo via CreateChild1_Normal; was default `false` | override + `true` |
| CutsceneKnucklesSkIntroEggRoboUpperVisualChild | loc_916EE / word_919C4 | 198873 | $280 | 5 | inherited; was default `false` | override + `true` |
| CutsceneKnucklesSkIntroEggRoboLaserChild | loc_91756 / word_919CA (CreateChild10_NormalAdjusted copies art_tile, 177248) | 198876 | $280 | 5 | inherited; was default `false` | override + `true` |
| HCZWaterSplashObjectInstance | Obj_HCZWaterSplash | 75263, 75285 | $300 on both subtype paths | 6 | clear (pal 0/2, bit clear) | override added |
| IczBigSnowPileInstance | Obj_ICZ1BigSnowPile | 110438-110484 | never written, never Draw_Sprite | bucket(0) | n/a (never displayed) | verified-0 override added with "ROM never displays the pile" comment (per your follow-up). Note: the "$80" hint belongs to loc_53ADE Obj_ICZTeleporterMain at 110493, not the pile |
| LbzMinibossBoxInstance | Obj_LBZMinibossBox; pieces loc_8CE64 / ObjDat3_8D23C | 192789 | box never draws; pieces $100 | 2 | clear (192788) | override added; see unmodelled |
| LightningSparkObjectInstance | Obj_LightningShield_CreateSpark | 34827 | $80 | 1 | inherits the shield's art_tile (34825); engine shield carries no bit, default `false` consistent | override added; see unmodelled |
| Mgz2CapsuleAnimalInstance | Obj_EggCapsule loc_86820 / word_86B50; loc_8689C | 182169, 181867 | $280, then $80 when the orbit timer expires | 5, then 1 via the existing `released` flag | set, inherited from ObjDat_EggCapsule (182157); already `true`, citation added | runtime-switching override added |
| MhzShipPropellerInstance | loc_55814 | 113467 | $380 | 7 | set (113468); was default `false` | override + `true` |
| IczMinibossInstance | Obj_ICZMiniboss / ObjDat3_71960 | 150441 | $280 | 5 | set (150440); was default `false` | override + `true`; see unmodelled |
| Lbz1RobotnikEventController | Obj_LBZ1Robotnik / ObjDat_LBZ1Robotnik | 192784 | $100 | 2 | clear: `make_art_tile(ArtTile_RobotnikShip,0,0)` (192783); Obj_RobotnikHead3 copies the ship's bit (136198-136200); no art_tile write in 192152-192362; was `true` | override + `isHighPriority()` flipped to `false`; see unmodelled |
| IczSnowboardIntroInstance (outer) | Obj_LevelIntroICZ1 | 77008 | $80 | 1 | clear (77007) | override added |
| IczSnowboardIntroInstance.SnowboardFlyAwayInstance | loc_393EE | 76755 | $100 | 2 | clear (76753) | override added |
| IczSnowboardIntroInstance.SnowboardDustInstance | sub_39924 | 77161 | $100 | 2 | clear (77159) | override added |
| FbzDezPlayerLauncherInstance | Obj_FBZDEZPlayerLauncher loc_3B956 | 79406 | $280 | 5 | clear (79396/79399) | override added; satisfies existing `assertEquals(5, launcher.getPriorityBucket())` in TestFbzPlayerTransportObjects:61 |
| LbzMinibossBoxKnuxInstance | Obj_LBZMinibossBoxKnux; loc_8D046 spawns ChildObjDat_8D25C pieces (ObjDat3_8D23C) | 192789 | box children never draw; pieces $100 | 2 | clear | override added; see unmodelled |
| CutsceneKnucklesHcz2Instance | ObjSlot_CutsceneKnux | 134800, 178886 | $180 | 3 | set; already `true` | override added |
| CutsceneKnucklesLbz1Instance | ObjSlot_CutsceneKnux | 134800 | $180 | 3 | set; already `true` | override added |
| CutsceneKnucklesLbz2Instance (outer) | ObjSlot_CutsceneKnux | 134800 | $180 | 3 | set (134797); was default `false` | override + `true` |
| CutsceneKnucklesLbz2Instance.SwingChild | loc_629CE / ObjDat3_6641A | 134836 | $280 | 5 | clear (134835) | override added |
| FbzEndBossFlameChild | loc_70BB0 calls Child_GetPriority every drawn frame | 149198, 180198-180205 | copies parent3 (the weapon) priority word and art bit 7 | `weapon.getPriorityBucket()` (falls back to MIN if unlinked) | now `weapon.isHighPriority()` instead of hard-coded `true`; the weapon already mirrors the boss | delegating overrides |
| HPZSanctuaryFallingCrystalObjectInstance | loc_90CA2 / ObjDat3_90FCC | 198374 | 0 | bucket(0) | set (198373); already `true` | verified-0 override added |
| HPZSuperEmeraldReturnEffectObjectInstance | loc_2ECD0 | 64173-64193 | never written | bucket(0) | set (64176); was default `false` | verified-0 override + `true` |
| LbzMinibossInstance | Obj_LBZMiniboss / ObjDat_LBZMiniboss | 151903 | $280 | 5 | set (151902); was default `false` | override + `true`; see unmodelled |
| LbzPlayerLauncherInstance (outer) | Obj_LBZPlayerLauncher | 51821 | $80 | 1 | clear (51818) | override added |
| LbzPlayerLauncherInstance.LauncherArmChild | loc_2629C | 51946 | $80 | 1 | clear (51943) | override added |
| LbzRideGrappleInstance | Obj_LBZRideGrapple | 52132 (helper copies it, 52149) | $80 | 1 | clear (52140) | override added |
| MhzPollenParticleInstance | Obj_MHZ_Pollen_Spawner | 81662, 81699 | `move.w #0,priority(a1)` | bucket(0) | no art_tile write (0) | verified-0 override added |
| S3kResultsElementObjectInstance | Obj_LevelResultsCreate | 62591-62612 | never written | bucket(0) | clear (loc_2DD8E writes 0 or $28) | verified-0 override added |
| CutsceneKnucklesCnz2AInstance | ObjSlot_CutsceneKnux | 134800 | $180 | 3 | set; already `true` | override added |
| CutsceneKnucklesCnz2BInstance | ObjSlot_CutsceneKnux | 134800 | $180 | 3 | set; already `true` | override added |
| FbzAct2CameraResizeWorker | Obj_IncLevEnd* style worker | n/a | never drawn | none | n/a | left: empty `appendRenderCommands`, no displayed SST |
| HczEndBossInstance | Obj_HCZEndBoss / ObjDat_HCZEndBoss | 142153 | $100 | 2 | set (142152); was default `false` | override + `true` |
| CnzTrapDoorInstance | Obj_CNZTrapDoor | 67217 | $80 only in 67213-67258 | already 1 | n/a | left: no runtime self-write exists; the "$280 later" is 67266, Obj_CNZLightBulb's init |
| CnzCannonInstance | Obj_CNZCannon | 66879; 66998; 67079 | $280 init; $380 and $100 are written to the captured player, not the cannon | already 5; player MAX then PLAYER_DEFAULT already modelled | n/a | left: no "$280 / $80 later" self-writes in 66875-67127 |
| CnzHoverFanInstance | Obj_CNZHoverFan | 67293 | $280 only | already 5 | n/a | left |
| LrzCollapsingBridgeInstance | Obj_LRZCollapsingBridge; loc_39DAA fragments | 77387; 77529-77531 | $80; fragments $80 plus `ori.w #high_priority` | already 1 / child 1 + `true` | already cited | left |

| Class | ROM label | sonic3k.asm | ROM priority word(s) | Bucket set | Art bit 15 handled? | Action |
|---|---|---|---|---|---|---|
| ClamerObjectInstance.ClamerAutoCloseProjectile | S3KBadnikProjectile_Init / ObjDat3_8913C | 182264, 186019 | $200 | 4 | set (186018); already `true` | override added |
| ClamerObjectInstance (outer) | ObjSlot_Clamer | 186011 | $280 | already literal `5` | set; already `true` | left: already declared, not in the flagged list |
| ClamerObjectInstance.ClamerSpringChild | loc_8908C / word_89136 | 186014 | $280 with `clr.w art_tile` | none | n/a | left: slot-only, empty render body |
| CutsceneKnucklesLbz2Instance.SwingChild | see first table | | | 5 | | done in first batch |
| CutsceneKnucklesMhz2Instance (outer) | CutsceneKnux_MHZ2 / ObjSlot_CutsceneKnux_MHZ2 | 134807, 178886 | $180 | 3 | set (134803); already `true`, citation added | override added |
| CutsceneKnucklesMhz2Instance.Mhz2KnucklesLeafParticle | loc_63324 / ObjDat3_6646E | 134871 | 0 | bucket(0) | set: `make_art_tile(ArtTile_MHZMisc+$21,3,1)` (134870); was default `false` | verified-0 override + `true` |
| CutsceneKnucklesSkIntroEggRobo{Entry,Laser,LowerVisual,UpperVisual} | see first table | | | 5 | `true` | done in first batch |
| HCZWaterDropObjectInstance.WaterDropChild | loc_382DE whole-SST copy from Obj_WaterDrop | 75175-75178; parent `#0` at 75154 | copies parent's $0 | bucket(0) | clear: `make_art_tile(ArtTile_HCZ2Slide,1,0)` (75152) | verified-0 override added |
| HCZWaterDropObjectInstance (outer) | Obj_WaterDrop | 75154 | $0 | none | n/a | left: spawner render body is empty (comment only) |
| IczSnowboardIntroInstance nested children | see first table | | | 2 / 2 | | done in first batch |
| LbzPlayerLauncherInstance.LauncherArmChild | see first table | | | 1 | | done in first batch |
| TensionBridgeObjectInstance.BridgeFragment | sub_389DE | 75861 (art), 75862, 75865 | `move.w priority(a3),priority(a1)` | parent's bucket, passed into a new `priorityBucket` field at spawn (rewind placeholder 0, reapplied by the field capturer like `highPri`) | copies parent's art_tile, already modelled via `highPri`; citation added | override added, private constructor gained the parent's bucket |
| CluckoidBadnikInstance.ArrowChild | loc_8E2BE / ObjDat3_8E3EA | 194322 | $280 | 5 | set (194321); was default `false` | override + `true` |
| CluckoidBadnikInstance.BreathDebrisChild | loc_8E236 / ObjDat3_8E3F6 | 194327 | 0 | bucket(0) | set: `make_art_tile(ArtTile_MHZMisc+$1C,3,1)` (194326); was default `false` | verified-0 override + `true`; matches existing TestCluckoidBadnikInstance:88 expectation of 0 |
| IczEndBossInstance (outer) | loc_71C36 / ObjDat3_72306 | 151276 | $280 | 5 | set (151275); was default `false` | override + `true` |
| IczEndBossInstance.IczEndBossDefeatDebrisChild | loc_720F2 / word_72330 | 151293 | $180 | 3 | inherited from the boss via CreateChild1_Normal; already `true` from GravityDebrisChild | override added |

Lead correction to the two `BreakObjectToPieces` rows: `move.b priority(a0),priority(a1)`
(sonic3k.asm:45811) copies the parent word's HIGH byte, and piece 0 stays in the parent slot
(a1=a0). So `RockDebrisChild` under the `$200` rock is bucket 4 on both paths and
`AizRockFragmentChild` under the `$180` cutscene rock is bucket 3 for piece 0 and bucket 2
(`$0100`) for pieces 1-11, not bucket 0 as the lane first transcribed.

### Runtime priority writes not modelled (recorded gaps)

- LBZ miniboss box pieces (LbzMinibossBoxInstance, LbzMinibossBoxKnuxInstance, and the carried box in Lbz1RobotnikEventController): each piece rewrites $380 at loc_8CF10 (192504) when its post-release flight timer expires. `LbzMinibossBoxRig` draws all pieces inside the owner's render call, so per-piece buckets need the rig to own instances. In the Robotnik controller the ship itself stays $100.
- ICZ miniboss children (IczMinibossInstance): orbs word_7196C $280; ObjDat3_71972 $280 then $180 at loc_7153A (150011); sub_717B8 $180 / $300 (150313-150316); shards word_7197E $180. They are OrbState/ShardState records drawn inline under the boss's bucket 5.
- LBZ miniboss arm panels (LbzMinibossInstance): per-subtype word_727E2 ($300/$380/$300/$380/$300/$280) at loc_727B0 (151737); drawn inline via PanelState.
- Lightning spark art bit (LightningSparkObjectInstance): the ROM spark inherits the shield's bit 15, which tracks Player_1's art_tile (34724-34726, 34751-34753). `LightningShieldObjectInstance` never sets the bit, so the chain is absent; fixing it means editing the shield, which was outside the list.
- ICZ end boss body children (IczEndBossInstance): ObjDat3_72324 $80 (151290) and the ChildObjDat_72336/7233E body parts are folded into the parent's inline drawing, so they render under bucket 5.
- HCZ end boss children (HczEndBossInstance): fan/bomb $200, platform/column $80, flicker child $200, geyser debris $280 (142156-142170, 142139) are separate classes or inline draws not on the list; only the body ($100) was set.
- FBZ end boss flame initial ObjDat (sub_70D10 / off_70D88) is irrelevant because Child_GetPriority overwrites it every drawn frame; the Obj_Wait phase is never drawn.

### Art-word bit flips on ROM evidence

- CutsceneKnucklesSkIntroInstance: `isHighPriority()` true to false (ObjSlot_KnuxIntroLay uses `make_art_tile(ArtTile_Player_2,0,0)`).
- Lbz1RobotnikEventController: `isHighPriority()` true to false (ObjDat_LBZ1Robotnik uses `make_art_tile(ArtTile_RobotnikShip,0,0)`).
- FbzEndBossFlameChild: `isHighPriority()` hard-coded true to `weapon.isHighPriority()` (Child_GetPriority).

## Rejected

- A per-game `GameRules` priority provider for the shared classes: the values agree
  across games except the boss explosion and the ChopChop bubble, which are caller
  choices, so a constructor/hook parameter is the smaller change.
- Promoting the audit scripts: the guard replaces the omission scan and `fromS3kWord`
  replaces the cited-constant cross-check; both regenerate in minutes.
