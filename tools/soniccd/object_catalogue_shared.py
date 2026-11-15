"""Shared-object anchors for tools/soniccd/object_catalogue.py (owner: shared).

Owned by the shared-object lane (objects several zones assemble, but not every zone); writes
src/main/resources/soniccd/object-catalogue-shared.tsv, read by objects/shared/SharedObjectCatalogue.

Each routine is found through the stage's own ObjectIndex (RunObjects: lea ObjectIndex,a1;
movea.l -4(a1,d0.w),a1, Object Functions.asm:11-25): the lowest index slot whose entry starts
the routine is its object ID (`<routine>Id`), and that live copy's operands and immediates give
the routine's per-stage data. Resolving through the index picks the live copy where a stage
links two (Tidal Tempest act 3 and Stardust Speedway act 3 link a second block body).

A routine is (name, key signature, distance(s) from the entry to the key, citation, fields);
a field is (column, signature, distance from the entry, kind, citation) where kind is
"operand" (the signature's AAAAAAAA/PPPP/QQ target as a file offset) or ("u8"|"u16", byte
offset into the match) for an immediate. A stage that does not index the routine reads "-".
"""
from object_catalogue import pattern
from stage_catalogue import WORD_RAM
from stage_inventory import require

ANCHORS = []

# RunObjects: lea ObjectIndex,a1; movea.l -4(a1,d0.w),a1 (Object Functions.asm:11-25).
OBJECT_INDEX = "D040 D040 43F9 AAAAAAAA 2271 00FC 4E91"

ROUTINES = [
    # Roll tunnel, also Palmtree Panic's and Wacky Workbench act 1 present's spin tunnel tag: one
    # body (Collision Chaos/Objects/Roll Tunnel/Main.asm:7-210; Spin Tunnel/Main (Tag).asm:7-170;
    # objects/roll_tunnel.asm:3-179). Init: move.w #$544,oTile; move.l #MapSpr_Powerup,oMap;
    # move.b oSubtype,oMapFrame (Roll Tunnel/Main.asm:27-33), 48 bytes from the entry.
    ("rollTunnel", "317C 0544 0002 217C ???????? 0004 1168 0028 001A", (48,),
     "ObjRollTunnel_Init (Collision Chaos/Objects/Roll Tunnel/Main.asm:27-33)", []),
    # Block, Tidal Tempest and Stardust Speedway (objects/r4/block.asm:3-26; objects/r7/block.asm:3-26):
    # one body; priority, tile, size table and mappings are per-stage data. The key runs to the
    # SolidObject call so Stardust Speedway act 3's boss block (objects/r7/boss_block.asm:3-27,
    # same init, tst.b oSubtype2 after it) is not taken.
    ("block", "117C 00?? 0018 317C ???? 0002 217C ???????? 0004 7000 1028 0028 1140 001A D040 43FB 00?? "
              "1019 1211 1140 0017 1140 0019 1141 0016 43F8", (16,),
     "BlockObject (objects/r4/block.asm:3-24)", [
         ("blockPriority", "117C 00?? 0018", 16, ("u8", 3),
          "move.b #n,obj.sprite_layer(a0) (objects/r4/block.asm:8)"),
         ("blockTile", "317C ???? 0002", 22, ("u16", 2),
          "move.w #$446A,obj.sprite_tile(a0) (objects/r4/block.asm:9)"),
         ("blockMappings", "217C AAAAAAAA 0004", 28, "operand",
          "BlockObject: move.l of the mappings to obj.sprite_data(a0) (objects/r4/block.asm:3-10)"),
         ("blockSizes", "43FB 00QQ", 48, "operand",
          "lea byte_20EDC2(pc,d0.w),a1 (objects/r4/block.asm:15)"),
     ]),
    # Quartz Quadrant's falling block, a different routine under the same name
    # (objects/r5/block.asm:12-197). BlockInit's oXRadius/oWidth/oYRadius/oTile/oMap writes
    # (objects/r5/block.asm:35-44), 62 bytes from the entry.
    ("fallingBlock", "117C 0010 0017 117C 0010 0019 117C 0010 0016 317C ???? 0002 217C ???????? 0004", (62,),
     "BlockInit (objects/r5/block.asm:35-44)", [
         ("fallingBlockTile", "317C ???? 0002", 80, ("u16", 2),
          "move.w #$4332,obj.sprite_tile(a0) (objects/r5/block.asm:43)"),
         ("fallingBlockMappings", "217C AAAAAAAA 0004", 86, "operand",
          "BlockInit: move.l of the mappings to obj.sprite_data(a0) (objects/r5/block.asm:35-44)"),
     ]),
    # Metallic Madness's crusher (objects/r8/crusher.asm:3-209); Wacky Workbench's
    # (objects/r6/crusher.asm) is another routine and does not match. CrusherObject_0_Routine0's
    # oMap/oTile/oPriority/var_30/y_speed/x_speed writes (:22-27), 40 bytes from the entry.
    ("crusher", "217C ???????? 0004 317C ???? 0002 117C 0003 0018 3168 000C 0030 4268 0012 4268 0010", (40,),
     "CrusherObject_0_Routine0 (objects/r8/crusher.asm:19-27)", [
         ("crusherMappings", "217C AAAAAAAA 0004", 40, "operand",
          "CrusherObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r8/crusher.asm:19-22)"),
         ("crusherTile", "317C ???? 0002", 48, ("u16", 2),
          "move.w #$498,obj.sprite_tile(a0) (objects/r8/crusher.asm:23)"),
         ("crusherPhases", "343B 10QQ 3142 0032", 88, "operand",
          "move.w word_20BB04(pc,d1.w),d2; move.w d2,obj.var_32(a0) (objects/r8/crusher.asm:33-34)"),
         ("crusherAmplitudes", "343B 10QQ 3142 0036", 104, "operand",
          "move.w word_20BB0C(pc,d1.w),d2; move.w d2,obj.var_36(a0) (objects/r8/crusher.asm:38-39)"),
         ("crusherSteps", "343B 10QQ 3142 0034", 212, "operand",
          "move.w word_20BB10(pc,d1.w),d2; move.w d2,obj.var_34(a0) (objects/r8/crusher.asm:85-86)"),
         ("crusherPauses", "143B 10QQ 1142 0038", 266, "operand",
          "move.b byte_20BB76(pc,d1.w),d2; move.b d2,obj.var_38(a0) (objects/r8/crusher.asm:104-105)"),
     ]),
    # Quartz Quadrant's collapse floor (objects/r5/collapse_floor.asm:3-177). Tidal Tempest's and
    # Metallic Madness's copies differ in instructions (r8 adds a solid, non-collapsing negative
    # subtype) and do not match. CollapseFloorObject_1_Routine0's writes (:21-30), 30 bytes from the entry.
    ("collapseFloor", "5428 0024 117C 0004 0001 217C ???????? 0004 117C 0003 0018 117C 0011 0016 303C ???? "
                      "0C39 0002 ???????? 66?? 303C", (30,),
     "CollapseFloorObject_1_Routine0 (objects/r5/collapse_floor.asm:21-30)", [
         ("collapseFloorMappings", "217C AAAAAAAA 0004", 40, "operand",
          "CollapseFloorObject_1_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r5/collapse_floor.asm:21-24)"),
         ("collapseFloorTile", "303C ???? 0C39", 60, ("u16", 2),
          "move.w #$4350,d0 (objects/r5/collapse_floor.asm:27)"),
         ("collapseFloorTileAct3", "303C ????", 74, ("u16", 2),
          "cmpi.b #2,act; bne.s; move.w #$43A0,d0 (objects/r5/collapse_floor.asm:28-30)"),
     ]),
    # Quartz Quadrant's spikes (objects/r5/spikes.asm:18-252), another routine than the Level
    # dialect's ObjSpikes (common lane). SpikesInit's writes (:69-75), 112 bytes from the entry.
    ("qqzSpikes", "5428 0024 0028 0004 0001 303C ???? 3140 0002 117C 0003 0018 217C ???????? 0004", (112,),
     "SpikesInit (objects/r5/spikes.asm:69-75)", [
         ("qqzSpikesTile", "303C ???? 3140 0002", 122, ("u16", 2),
          "move.w #$44B,d0; move.w d0,obj.sprite_tile(a0) (objects/r5/spikes.asm:72-73)"),
         ("qqzSpikesMappings", "217C AAAAAAAA 0004", 136, "operand",
          "SpikesInit: move.l of the mappings to obj.sprite_data(a0) (objects/r5/spikes.asm:69-75)"),
         ("qqzSpikesProperties", "D000 D001 43FB 00QQ", 172, "operand",
          "add.b d0,d0; add.b d1,d0; lea SpikesProperties(pc,d0.w),a1 (objects/r5/spikes.asm:85-87)"),
     ]),
    # Switch, Wacky Workbench and Metallic Madness: one body (objects/r6/switch.asm:3-62;
    # objects/r8/switch.asm:3-67); Metallic Madness picks the tile by act (:9-14). Tidal Tempest's
    # (objects/r4/switch.asm) is another routine. Init writes, 16 (r6) or 18 (r8, bne.w) bytes in.
    ("switch", "117C 0001 0018 117C 0010 0017 117C 0010 0019 317C ???? 0002 217C ???????? 0004 117C 0008 0016",
     (16,), "SwitchObject (objects/r6/switch.asm:6-13)", [
         ("switchTile", "317C ???? 0002", 34, ("u16", 2),
          "move.w #$39A,obj.sprite_tile(a0) (objects/r6/switch.asm:11)"),
         ("switchMappings", "217C AAAAAAAA 0004", 40, "operand",
          "SwitchObject: move.l of the mappings to obj.sprite_data(a0) (objects/r6/switch.asm:3-12)"),
     ]),
    ("switchByAct", "117C 0001 0018 117C 0010 0017 117C 0010 0019 303C ???? 0C39 0002 ???????? 66?? 303C ???? "
                    "3140 0002 217C ???????? 0004", (18,), "SwitchObject (objects/r8/switch.asm:6-17)", [
         ("switchByActTile", "303C ???? 0C39", 36, ("u16", 2),
          "move.w #$4E4,d0 (objects/r8/switch.asm:9)"),
         ("switchByActTileAct3", "303C ????", 50, ("u16", 2),
          "cmpi.b #2,act; bne.s; move.w #$386,d0 (objects/r8/switch.asm:10-12)"),
         ("switchByActMappings", "217C AAAAAAAA 0004", 58, "operand",
          "SwitchObject: move.l of the mappings to obj.sprite_data(a0) (objects/r8/switch.asm:3-15)"),
     ]),
    # Wacky Workbench's door (objects/r6/door.asm:3-152); Collision Chaos's, Tidal Tempest's and
    # Metallic Madness's doors are other routines. DoorObject_0_Routine0's writes (:30-44), 56 bytes in.
    ("wwzDoor", "0028 0004 0001 117C 0003 0018 217C ???????? 0004 3168 000C 0032 317C ???? 0002 117C 0020 0016 "
                "117C 0008 0019 0C39 0002 ???????? 66?? 317C", (56,), "DoorObject_0_Routine0 (objects/r6/door.asm:30-44)", [
         ("wwzDoorMappings", "217C AAAAAAAA 0004", 68, "operand",
          "DoorObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r6/door.asm:30-34)"),
         ("wwzDoorTile", "317C ???? 0002", 82, ("u16", 2),
          "move.w #$3A0,obj.sprite_tile(a0) (objects/r6/door.asm:36)"),
         ("wwzDoorTileAct3", "317C ???? 0002", 110, ("u16", 2),
          "cmpi.b #2,act; bne.s; move.w #$330,obj.sprite_tile(a0) (objects/r6/door.asm:39-41)"),
     ]),
    # Wacky Workbench's platform (objects/r6/platform.asm:3-110; Platform/Main (Normal).asm in act 1
    # present); Palmtree Panic's and Quartz Quadrant's platforms are other routines.
    # PlatformObject_0_Routine0's writes (:29-37), 52 bytes from the entry.
    ("wwzPlatform", "0028 0004 0001 117C 0001 0018 317C ???? 0002 217C ???????? 0004 3168 0008 0036 3168 000C 0032 "
                    "117C 000C 0016 117C 0018 0019", (52,), "PlatformObject_0_Routine0 (objects/r6/platform.asm:29-37)", [
         ("wwzPlatformTile", "317C ???? 0002", 64, ("u16", 2),
          "PlatformObject_0_Routine0: move.w #$436A,obj.sprite_tile(a0) (objects/r6/platform.asm:29-32)"),
         ("wwzPlatformMappings", "217C AAAAAAAA 0004", 70, "operand",
          "PlatformObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r6/platform.asm:29-33)"),
     ]),
    # Stardust Speedway's spike ball (objects/r7/spike_ball.asm:3-102); Tidal Tempest's
    # (objects/r4/spike_ball.asm) is a static one. SpikeBallObject_0_Routine0's writes (:21-36), 36 bytes
    # from the entry; the tables are the two lea d16(PC) of :32-36 and :37-45.
    ("spikeBall", "5428 0024 0028 0004 0001 117C 0003 0018 117C 00B5 0020 117C 0010 0017 117C 0010 0019 "
                  "117C 0010 0016 317C ???? 0002 217C ???????? 0004 3168 0008 002A", (36,),
     "SpikeBallObject_0_Routine0 (objects/r7/spike_ball.asm:21-31)", [
         ("spikeBallTile", "317C ???? 0002", 76, ("u16", 2),
          "move.w #$386,obj.sprite_tile(a0) (objects/r7/spike_ball.asm:29)"),
         ("spikeBallMappings", "217C AAAAAAAA 0004", 82, "operand",
          "SpikeBallObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r7/spike_ball.asm:30)"),
         ("spikeBallPauses", "43FA PPPP 7000 1039 ???????? D040 3171 0000 002E", 96, "operand",
          "SpikeBallObject_0_Routine0: lea of the rest table; move.w (a1,d0.w),obj.var_2e(a0) (objects/r7/spike_ball.asm:32-36)"),
         ("spikeBallMoves", "43FA PPPP 7000 1039 ???????? E948 7200 1228 0028", 116, "operand",
          "SpikeBallObject_0_Routine0: lea of the move table (objects/r7/spike_ball.asm:37-45)"),
     ]),
    # Wacky Workbench's Poh-Bee (objects/r6/poh_bee.asm:3-239) and its missile (:255-310); Metallic
    # Madness's (objects/r8/poh_bee.asm) is another routine. PohBeeObject_1_Routine0's writes (:33-42),
    # 70 bytes from the entry. The animation table is the first lea of :11-14, the missile's sprite
    # pointer and animation table are :192 and :303.
    ("wwzPohBee", "5428 0024 0028 0004 0001 117C 0001 0018 117C 0018 0017 117C 0018 0019 117C 000C 0016 "
                  "317C ???? 0002 117C 0031 0020 317C FFF8 0030", (70,),
     "PohBeeObject_1_Routine0 (objects/r6/poh_bee.asm:33-42)", [
         ("wwzPohBeeTile", "317C ???? 0002", 104, ("u16", 2),
          "move.w #$A457,obj.sprite_tile(a0) (objects/r6/poh_bee.asm:40)"),
         ("wwzPohBeeAnims", "43FA PPPP 4EB9 ???????? 4EB9 ???????? 4EF9", 28, "operand",
          "PohBeeObject: lea of the animations; jsr AnimateObject (objects/r6/poh_bee.asm:11-12)"),
         ("wwzPohBeeMappings1", "43FA PPPP 203C FFFF0000", 122, "operand",
          "PohBeeObject_1_Routine0: lea of the first mapping set; move.l #-$10000,d0 (objects/r6/poh_bee.asm:43-44)"),
         ("wwzPohBeeMappings2", "43FA PPPP 203C FFFF8000", 138, "operand",
          "PohBeeObject_1_Routine0: lea of the second mapping set; move.l #-$8000,d0 (objects/r6/poh_bee.asm:47-48)"),
         ("wwzPohMissileMappings", "237C AAAAAAAA 0004 137C 0001 0018", 496, "operand",
          "PohBeeObject_1_Routine10: move.l of the missile mappings to obj.sprite_data(a1) (objects/r6/poh_bee.asm:192-193)"),
         ("wwzPohMissileAnims", "43FA PPPP 4EF9 ???????? 0002", 1164, "operand",
          "lea PohMissileAnims(pc),a1; jmp AnimateObject (objects/r6/poh_bee.asm:303-304)"),
     ]),
    # H door (objects/r1/h_door.asm:3-93), assembled by the zones of Collision Chaos, Quartz Quadrant,
    # Wacky Workbench, Stardust Speedway and Metallic Madness; its splash is HDoorSplashObject (:131-170)
    # and the tunnel splash TunnelSplashObject (objects/r1/tunnel_splash.asm:3-38). Routine0's writes
    # (:53-73), 114 bytes from the entry; the animation table is Routine4's lea (:84).
    ("hDoor", "5428 0024 217C ???????? 0004 117C 0001 0018 0028 0004 0001 117C 002C 0019 0C28 0002 0028", (114,),
     "HDoorObject_0_Routine0 (objects/r1/h_door.asm:52-69)", [
         ("hDoorMappings", "217C AAAAAAAA 0004", 118, "operand",
          "HDoorObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r1/h_door.asm:54)"),
         ("hDoorAnims", "43F9 AAAAAAAA 6000", 188, "operand",
          "HDoorObject_0_Routine4: lea of the animations; bra AnimateObject (objects/r1/h_door.asm:84-85)"),
     ]),
    ("hDoorSplash", "5428 0024 117C 0004 0001 117C 0001 0018 217C ???????? 0004 1168 0028 001C 700D", (20,),
     "HDoorSplashObject_0_Routine0 (objects/r1/h_door.asm:146-153)", [
         ("hDoorSplashMappings", "217C AAAAAAAA 0004", 36, "operand",
          "HDoorSplashObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r1/h_door.asm:150)"),
         ("hDoorSplashAnims", "43F9 AAAAAAAA 6100", 80, "operand",
          "HDoorSplashObject_0_Routine2: lea of the animations; bsr AnimateObject (objects/r1/h_door.asm:163-164)"),
     ]),
    ("tunnelSplash", "5428 0024 0028 0004 0001 217C ???????? 0004 317C ???? 0002 4A39", (20,),
     "TunnelSplashObject_0_Routine0 (objects/r1/tunnel_splash.asm:18-28)", [
         ("tunnelSplashMappings", "217C AAAAAAAA 0004", 30, "operand",
          "TunnelSplashObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r1/tunnel_splash.asm:21)"),
         ("tunnelSplashTilePresent", "317C ???? 0002", 38, ("u16", 2),
          "move.w #$3E4,obj.sprite_tile(a0) (objects/r1/tunnel_splash.asm:22)"),
         ("tunnelSplashTilePast", "317C ???? 0002", 52, ("u16", 2),
          "tst.b time_zone; bne.s; move.w #$39E,obj.sprite_tile(a0) (objects/r1/tunnel_splash.asm:23-25)"),
         ("tunnelSplashAnims", "43F9 AAAAAAAA 6100", 64, "operand",
          "TunnelSplashObject_0_Routine2: lea of the animations; bsr AnimateObject (objects/r1/tunnel_splash.asm:31-32)"),
     ]),
    # Spike chain, Collision Chaos and Wacky Workbench (objects/r6/spike_chain.asm:3-213). The parent's
    # SpikeChainObject_0_Routine0 writes (:27-35), 40 bytes from the entry; the child's
    # SpikeChainObject_1_Routine0 (:196-202), 478 bytes in.
    ("spikeChain", "5428 0024 0028 0004 0001 117C 0001 0018 117C 0008 0019 117C 0008 0016 217C ???????? 0004 "
                   "317C 03F8 0002 317C 0100 002C", (40,),
     "SpikeChainObject_0_Routine0 (objects/r6/spike_chain.asm:27-35)", [
         ("spikeChainMappings", "217C AAAAAAAA 0004", 68, "operand",
          "SpikeChainObject_0_Routine0: move.l of the parent mappings to obj.sprite_data(a0) (objects/r6/spike_chain.asm:33)"),
         ("spikeChainTile", "317C ???? 0002", 76, ("u16", 2),
          "move.w #$3F8,obj.sprite_tile(a0) (objects/r6/spike_chain.asm:34)"),
         ("spikeChainChildMappings", "217C AAAAAAAA 0004 317C 03F8 0002 117C 00B9 0020", (494, 568, 642), "operand",
          "SpikeChainObject_1_Routine0: move.l of the ball mappings to obj.sprite_data(a0) (objects/r6/spike_chain.asm:200)"),
     ]),
    # Wacky Workbench's tube door (objects/r6/tube_door.asm:3-170); Metallic Madness's
    # (objects/r8/tube_door.asm) is another routine. TubeDoorObject_0_Routine0's writes (:38-45),
    # 62 bytes from the entry.
    ("wwzTubeDoor", "5428 0024 0028 0004 0001 117C 0003 0018 317C 4410 0002 217C ???????? 0004 117C 0004 0019 "
                    "117C 0020 0016", (62,), "TubeDoorObject_0_Routine0 (objects/r6/tube_door.asm:38-45)", [
         ("wwzTubeDoorTile", "317C ???? 0002", 78, ("u16", 2),
          "move.w #$4410,obj.sprite_tile(a0) (objects/r6/tube_door.asm:42)"),
         ("wwzTubeDoorMappings", "217C AAAAAAAA 0004", 84, "operand",
          "TubeDoorObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r6/tube_door.asm:43)"),
     ]),
    # Moving spring of every zone but Metallic Madness (objects/spring.asm:3-97; Level/_Objects/Spring/Main.asm:7-83):
    # a platform that crawls along the floor carrying an upward spring. Metallic Madness's (MoveSpringObjectR8,
    # objects/r8/move_spring.asm) is another routine. sub_208254 writes (:27-61), 52 bytes from the entry;
    # the animation table is sub_2082E2 lea (:93-94).
    ("movingSpring", "5428 0024 0028 0004 0001 117C 0004 0018 217C ???????? 0004 117C 0008 0019 117C 0007 0016 "
                     "3168 0008 0036 317C 0180 0010 700E", (52,),
     "sub_208254 (objects/spring.asm:27-61)", [
         ("movingSpringMappings", "217C AAAAAAAA 0004", 68, "operand",
          "sub_208254: move.l of the mappings to obj.sprite_data(a0) (objects/spring.asm:31)"),
         ("movingSpringAnims", "43F9 AAAAAAAA 4EB9 ???????? 4EF9", 236, "operand",
          "sub_2082E2: lea of the animations; jsr AnimateObject (objects/spring.asm:93-94)"),
     ]),
    # Stardust Speedway's swing (objects/r7/swing.asm:3-175); Palmtree Panic's, Tidal Tempest's and Quartz
    # Quadrant's swings are other routines. SwingObject_0_Routine0's writes (:62-69), 180 bytes from the
    # entry; the motion table is loc_20CFC4's lea (:102-107).
    ("swing", "5428 0024 0028 0004 0001 117C 0003 0018 217C ???????? 0004 317C 0366 0002 117C 0020 0019 "
              "117C 0008 0016 4A28 003C", (180,), "SwingObject_0_Routine0 (objects/r7/swing.asm:62-70)", [
         ("swingMappings", "217C AAAAAAAA 0004", 196, "operand",
          "SwingObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r7/swing.asm:66)"),
         ("swingTile", "317C ???? 0002", 204, ("u16", 2),
          "move.w #$366,obj.sprite_tile(a0) (objects/r7/swing.asm:67)"),
         ("swingMotion", "43FA PPPP 43F1 0000", 344, "operand",
          "SwingObject_0_Routine0: lea of the motion table; lea (a1,d0.w),a1 (objects/r7/swing.asm:106-107)"),
     ]),
    # Stardust Speedway's Hotaru (objects/r7/hotaru.asm:3-558); Metallic Madness's (objects/r8/hotaru.asm) is
    # another routine. HotaruObject_2_Routine0's writes (:23-50), 42 bytes from the entry; the firefly's
    # sprite and animation tables are loc_20DE60's leas (HotaruObject_0_Routine0, :188-198), 512 and 528 in;
    # the spark's mappings and animations are HotaruObject_1_Routine0 (:533-541) and Routine2 (:543-546).
    ("hotaru", "5428 0024 7000 1028 0023 43F9 ???????? 3200 D241 D041 7200 1239 ???????? D041 43F1 0002 2149 002A "
               "43F9 ???????? 1028 0028 0240 000C D2C0 2149 002E", (42,),
     "HotaruObject_2_Routine0 (objects/r7/hotaru.asm:23-40)", [
         ("hotaruOffsets", "43F9 AAAAAAAA 1028 0028 0240 000C", 82, "operand",
          "HotaruObject_2_Routine0: lea of the child offsets; andi.w #$C,d0; adda.w d0,a1 (objects/r7/hotaru.asm:36-39)"),
         ("hotaruTile", "303C ???? 0C39 0002", 114, ("u16", 2),
          "move.w #$845D,d0 (objects/r7/hotaru.asm:44)"),
         ("hotaruTileAct3", "303C ???? 3140 0002", 128, ("u16", 2),
          "cmpi.b #2,act; bne.s; move.w #$8320,d0 (objects/r7/hotaru.asm:45-47)"),
         ("hotaruMappings1", "43FA PPPP 45FA ???? 203C FFFF0000", 512, "operand",
          "HotaruObject_0_Routine0: lea of the first mapping set; move.l #-$10000,d0 (objects/r7/hotaru.asm:188-190)"),
         ("hotaruAnims1", "43FA ???? 45FA PPPP 203C FFFF0000", 512, "operand",
          "HotaruObject_0_Routine0: lea of the first animation set (objects/r7/hotaru.asm:189)"),
         ("hotaruMappings2", "43FA PPPP 45FA ???? 203C FFFF8000", 528, "operand",
          "HotaruObject_0_Routine0: lea of the second mapping set; move.l #-$8000,d0 (objects/r7/hotaru.asm:196-198)"),
         ("hotaruAnims2", "43FA ???? 45FA PPPP 203C FFFF8000", 528, "operand",
          "HotaruObject_0_Routine0: lea of the second animation set (objects/r7/hotaru.asm:197)"),
         ("hotaruSparkMappings", "217C AAAAAAAA 0004", 1422, "operand",
          "HotaruObject_1_Routine0: move.l of the spark mappings to obj.sprite_data(a0) (objects/r7/hotaru.asm:541)"),
         ("hotaruSparkAnims", "43FA PPPP 4EB9 ???????? 4EF9", 1430, "operand",
          "HotaruObject_1_Routine2: lea of the spark animations; jsr AnimateObject (objects/r7/hotaru.asm:544-545)"),
     ]),
    # Quartz Quadrant's tunnel path (objects/r5/tunnel_path.asm:3-266); Wacky Workbench's
    # (objects/r6/tunnel_path.asm) is another routine under the same name. Both open with the
    # object-state clear (:4-31) and differ at :35 (r5 tests the subtype for the conveyor, r6 goes
    # straight to the main update), so the key runs from the entry to that first difference. The path
    # directory is the lea of TunnelPathObject_0_Routine0 (:92-94), 198 bytes from the entry.
    ("qqzTunnelPath", "0839 0007 ???????? 67?? 7000 1028 ???? 67?? 43F9 ???????? 3200 D241 D041 7200 1239 ???????? 0881 0007 1438 ???? 4882 4442 D242 6A?? 7200 60?? 0C41 0003 65?? 7202 D041 08B1 0007 0002 4A28 ???? 6A?? 67?? 4A39 ???????? 66?? 0828 0000 ???? 67?? 60?? 0828 0000 ???? 67?? 4DF8 D000 0C2E 002B ???? 67?? 0C2E 0006 ???? 64?? 7000 1028 ???? 323B ???? 4EBB 1012 0C28 0004 ???? 64?? 4EF9 ???????? 4E75 0008 0040 00D0 00E4", (0,),
     "TunnelPathObject (objects/r5/tunnel_path.asm:3-38)", [
         ("qqzTunnelPaths", "45FA PPPP D4F2 0000", 198, "operand",
          "TunnelPathObject_0_Routine0: lea TunnelPaths(pc),a2; adda.w (a2,d0.w),a2 (objects/r5/tunnel_path.asm:92-93)"),
     ]),
    ("wwzTunnelPath", "0839 0007 ???????? 67?? 7000 1028 ???? 67?? 43F9 ???????? 3200 D241 D041 7200 1239 ???????? 0881 0007 1438 ???? 4882 4442 D242 6A?? 7200 60?? 0C41 0003 65?? 7202 D041 08B1 0007 0002 4DF8 D000 0C2E 002B ???? 67?? 0C2E 0006 ???? 64?? 7000 1028 ???? 323B ???? 4EBB 1012 0C28 0004 ???? 64?? 4EF9 ???????? 4E75 0008 0052 0118 0132", (0,),
     "TunnelPathObject (objects/r6/tunnel_path.asm:3-39)", [
         ("wwzTunnelPaths", "45FA PPPP D4F2 0000", 182, "operand",
          "TunnelPathObject_0_Routine0: lea TunnelPaths(pc),a2; adda.w (a2,d0.w),a2 (objects/r6/tunnel_path.asm:79-80)"),
     ]),
    # Quartz Quadrant's platform (objects/r5/platform.asm:20-617); Wacky Workbench's (objects/r6/platform.asm) is
    # another routine, found as wwzPlatform. The key is PlatformObject's dispatch (:20-31) with its routine table, which
    # differs from the r6 body. PlatformInit (:163-232) writes the tile (acts 1-2, act 3) and mappings, then reads the
    # three property tables (:187-197), all past the tables that follow the dispatch.
    ("qqzPlatform", "7000 1028 0024 303B 0016 4EBB 0012 4EB9 ???????? 3028 0036 4EF9 ???????? 0096 0156 43F8 D000 3628 0008 "
                    "3828 000C 4EF9", (0,), "PlatformObject (objects/r5/platform.asm:20-31)", [
         ("qqzPlatformTile", "303C ???? 0C39 0002", 196, ("u16", 2),
          "PlatformInit: move.w #$4304,d0 (objects/r5/platform.asm:168)"),
         ("qqzPlatformTileAct3", "303C ???? 3140 0002", 210, ("u16", 2),
          "PlatformInit: cmpi.b #2,act; bne.s; move.w #$4378,d0 (objects/r5/platform.asm:169-171)"),
         ("qqzPlatformMappings", "217C AAAAAAAA 0004", 218, "operand",
          "PlatformInit: move.l of the mappings to obj.sprite_data(a0) (objects/r5/platform.asm:175)"),
         ("qqzPlatformProperties", "45F9 AAAAAAAA 45F2 1000", 260, "operand",
          "PlatformInit: lea PlatformProperties,a2 (objects/r5/platform.asm:187)"),
         ("qqzPlatformConveyorFlags", "45F9 AAAAAAAA 1172 0000 003F", 278, "operand",
          "PlatformInit: lea PlatformConveyorFlags,a2 (objects/r5/platform.asm:192)"),
         ("qqzPlatformSpringCounts", "45F9 AAAAAAAA 1232 0000 6742", 290, "operand",
          "PlatformInit: lea PlatformSpringCounts,a2 (objects/r5/platform.asm:195)"),
     ]),
    # Dango, Stardust Speedway's and Metallic Madness's badnik: one body (objects/r7/dango.asm:3-281; objects/r8/dango.asm).
    # The key is DangoObject's dispatch (:3-31) with its fifteen routine offsets; Routine0 (:33-60), 62 bytes from the entry,
    # writes the tile for the present and future ($245D), then for the past ($23E9), and loads one of two sprite/animation sets
    # by subtype.
    ("dango", "4EB9 ???????? 7000 1028 0024 303B 0012 4EBB 000E 4EB9 ???????? 4EF9 ???????? 001E 007A 0096 00AA 0102 00E6 010C "
              "00E6 0120 015A 0142 015A 01DC 00E6 01F6", (0,), "DangoObject (objects/r7/dango.asm:3-31)", [
         ("dangoTile", "303C ???? 4A39", 102, ("u16", 2),
          "DangoObject_0_Routine0: move.w #$245D,d0 (objects/r7/dango.asm:46-47)"),
         ("dangoTilePast", "303C ???? 3140 0002", 114, ("u16", 2),
          "DangoObject_0_Routine0: tst.b time_zone; bne.s; move.w #$23E9,d0 (objects/r7/dango.asm:46-50)"),
         ("dangoMappings1", "43FA PPPP 45FA ???? 4A28 0028 6708", 122, "operand",
          "DangoObject_0_Routine0: lea DangoSprites1(pc),a1 (objects/r7/dango.asm:51-52)"),
         ("dangoAnims1", "43FA ???? 45FA PPPP 4A28 0028 6708", 122, "operand",
          "DangoObject_0_Routine0: lea DangoAnims1(pc),a2 (objects/r7/dango.asm:51-53)"),
         ("dangoMappings2", "43FA PPPP 45FA ???? 2149 0004", 136, "operand",
          "DangoObject_0_Routine0: lea DangoSprites2(pc),a1 (objects/r7/dango.asm:54-56)"),
         ("dangoAnims2", "43FA ???? 45FA PPPP 2149 0004", 136, "operand",
          "DangoObject_0_Routine0: lea DangoAnims2(pc),a2 (objects/r7/dango.asm:54-57)"),
     ]),
    # Metallic Madness's Dango, object $2D of act 2 (objects/r8/dango.asm:3-275): the same body as Stardust Speedway's but Routine0 (:33-58)
    # writes one tile word ($242F) for every period, so the routine is 14 bytes shorter and every later offset in the dispatch
    # (:14-29) differs from the key above. Act 1 links an unindexed copy of the Stardust Speedway body (the key above matches its
    # code, no ObjectIndex slot starts it), which this routine does not take.
    ("mmzDango", "4EB9 ???????? 7000 1028 0024 303B 0012 4EBB 000E 4EB9 ???????? 4EF9 ???????? 001E 006C 0088 009C 00F4 00D8 00FE "
                 "00D8 0112 014C 0134 014C 01CE 00D8", (0,), "DangoObject (objects/r8/dango.asm:3-31)", [
         ("mmzDangoTile", "317C ???? 0002 43FA", 102, ("u16", 2),
          "DangoObject_0_Routine0: move.w #$242F,obj.sprite_tile(a0) (objects/r8/dango.asm:41)"),
         ("mmzDangoMappings1", "43FA PPPP 45FA ???? 4A28 0028 6708", 108, "operand",
          "DangoObject_0_Routine0: lea DangoSprites1(pc),a1 (objects/r8/dango.asm:42)"),
         ("mmzDangoAnims1", "43FA ???? 45FA PPPP 4A28 0028 6708", 108, "operand",
          "DangoObject_0_Routine0: lea DangoAnims1(pc),a2 (objects/r8/dango.asm:42-43)"),
         ("mmzDangoMappings2", "43FA PPPP 45FA ???? 2149 0004", 122, "operand",
          "DangoObject_0_Routine0: lea DangoSprites2(pc),a1 (objects/r8/dango.asm:44-46)"),
         ("mmzDangoAnims2", "43FA ???? 45FA PPPP 2149 0004", 122, "operand",
          "DangoObject_0_Routine0: lea DangoAnims2(pc),a2 (objects/r8/dango.asm:44-47)"),
     ]),
    # Wacky Workbench's crusher, object $26 in acts 2 and 3 (objects/r6/crusher.asm:3-165); Metallic Madness's is another
    # routine, found as crusher. The key is CrusherObject's dispatch (:3-14) with its five routine offsets; Routine0 (:31-39)
    # writes the tile and mappings, and CrusherUpdateMotion (:112-149) loads the extending and retracting motion records.
    ("wwzCrusher", "7000 1028 0024 303B 0012 4EBB 000E 4EB9 ???????? 4EF9 ???????? 001C 004C 0082 009A 00CC", (0,),
     "CrusherObject (objects/r6/crusher.asm:3-14)", [
         ("wwzCrusherTile", "317C ???? 0002 217C", 70, ("u16", 2),
          "CrusherObject_0_Routine0: move.w #$44E8,obj.sprite_tile(a0) (objects/r6/crusher.asm:35)"),
         ("wwzCrusherMappings", "217C AAAAAAAA 0004", 76, "operand",
          "CrusherObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r6/crusher.asm:36)"),
         ("wwzCrusherExtending", "45F9 AAAAAAAA 4A28 003C 6706", 258, "operand",
          "CrusherUpdateMotion: lea CrusherExtendingMotionRecords,a2 (objects/r6/crusher.asm:113-115)"),
         ("wwzCrusherRetracting", "45F9 AAAAAAAA 4A28 003A 661E", 270, "operand",
          "CrusherUpdateMotion: lea CrusherRetractingMotionRecords,a2 (objects/r6/crusher.asm:115-117)"),
     ]),
    # Metallic Madness's tube door, object $20 (objects/r8/tube_door.asm:3-286); Wacky Workbench's is another routine, found as
    # wwzTubeDoor. The key is TubeDoorObject's form tests and dispatch (:3-34) with its routine offsets. The part initialiser
    # (sub_20F1D0, :87-99) writes the tile and mappings through a6, 262 and 286 bytes from the entry; the animations are the
    # lea of Routine6's animate (:188-195).
    ("mmzTubeDoor", "4A28 0029 6B00 ???? 4A28 0028 6A0E 3268 003E 0C29 0020 0000 6600 ???? 7000 1028 0024 303B 001E 4EBB 001A "
                    "4EB9 ???????? 4A28 0028 6B0A 3028 002A 4EF9 ???????? 4E75 0008 0020 0042 00EC", (0,),
     "TubeDoorObject (objects/r8/tube_door.asm:3-34)", [
         ("mmzTubeDoorTile", "3D7C ???? 0002", 262, ("u16", 2),
          "sub_20F1D0: move.w #$33C,obj.sprite_tile(a6) (objects/r8/tube_door.asm:91)"),
         ("mmzTubeDoorMappings", "2D7C AAAAAAAA 0004", 286, "operand",
          "sub_20F1D0: move.l of the mappings to obj.sprite_data(a6) (objects/r8/tube_door.asm:95)"),
         ("mmzTubeDoorAnims", "43FA PPPP 4EF9", 532, "operand",
          "TubeDoorObject_0_Routine6: lea TubeDoorAnims(pc),a1; jmp AnimateObject (objects/r8/tube_door.asm:193-195)"),
     ]),
    # Metallic Madness's tunnel path (objects/r8/tunnel_path.asm:80-377); the body is the Quartz Quadrant one with a fixed $1000
    # ground speed. After the object-state clear (:80-102) it tests the player and, in the USA build, the free-path subtype $80
    # ({cmpi.b #$80,obj.subtype(a0)}, :113-121), which distinguishes it from the Wacky Workbench routine. The directory is the
    # lea of TunnelPathObject_1_Routine0 (:151-162), 188 bytes from the entry.
    ("mmzTunnelPath", "0839 0007 ???????? 67?? 7000 1028 ???? 67?? 43F9 ???????? 3200 D241 D041 7200 1239 ???????? 0881 0007 1438 ???? 4882 4442 D242 6A?? 7200 60?? 0C41 0003 65?? 7202 D041 08B1 0007 0002 4DF8 D000 0C2E 002B ???? 67?? 0C2E 0006 ???? 64?? 0C28 0080 ???? 6700 ????", (0,),
     "TunnelPathObject (objects/r8/tunnel_path.asm:80-121)", [
         ("mmzTunnelPaths", "45FA PPPP D4F2 0000", 188, "operand",
          "TunnelPathObject_1_Routine0: lea TunnelPaths(pc),a2; adda.w (a2,d0.w),a2 (objects/r8/tunnel_path.asm:161-162)"),
     ]),
    # Wacky Workbench's seesaw, object $2C (objects/r6/seesaw.asm:3-216); Metallic Madness's is another routine (objects/r8/seesaw.asm).
    # The key is SeesawObject's child test and dispatch (:3-21) with its three routine offsets. Routine0 (:23-48) writes the tile
    # and mappings, 74 and 80 bytes from the entry; Routine2's animate is the lea of the animation table (:93-97).
    ("wwzSeesaw", "4A28 0028 6600 ???? 7000 1028 0024 303B 0012 4EBB 000E 4EB9 ???????? 4EF9 ???????? 0006 00D4 01BE", (0,),
     "SeesawObject (objects/r6/seesaw.asm:3-21)", [
         ("wwzSeesawTile", "317C ???? 0002", 74, ("u16", 2),
          "SeesawObject_0_Routine0: move.w #$3B8,obj.sprite_tile(a0) (objects/r6/seesaw.asm:31)"),
         ("wwzSeesawMappings", "217C AAAAAAAA 0004", 80, "operand",
          "SeesawObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r6/seesaw.asm:32)"),
         ("wwzSeesawAnims", "43FA PPPP 4EF9", 324, "operand",
          "SeesawObject_0_Routine2: lea of the animation table, then jmp AnimateObject (objects/r6/seesaw.asm:96-97)"),
     ]),
    # Metallic Madness's seesaw, object $35 in the R8 indexes (objects/r8/seesaw.asm:3-289); Wacky Workbench's is another routine, found as
    # wwzSeesaw. The key is SeesawObject's dispatch, DrawObject and its own despawn test (:3-21). Routine0 (:32-82) writes the mappings,
    # the tile (the same $3A0 for the platform) and, for the ball, $43A0, 72, 80 and 148 bytes from the entry.
    ("mmzSeesaw", "7000 1028 0024 303B 0032 4EBB 002E 4EB9 ???????? 3028 0008 0240 FF80 3238 F700 0441 0080 0241 FF80 9041 0C40 0280 "
                  "6200 0004 4E75 4EF9 ???????? 0004 0112", (0,),
     "SeesawObject (objects/r8/seesaw.asm:3-30)", [
         ("mmzSeesawMappings", "217C AAAAAAAA 0004", 72, "operand",
          "SeesawObject_0_Routine0: move.l of the mappings to obj.sprite_data(a0) (objects/r8/seesaw.asm:35)"),
         ("mmzSeesawTile", "317C ???? 0002", 80, ("u16", 2),
          "SeesawObject_0_Routine0: move.w #$3A0,obj.sprite_tile(a0) (objects/r8/seesaw.asm:36)"),
         ("mmzSeesawBallTile", "337C ???? 0002", 148, ("u16", 2),
          "SeesawObject_0_Routine0: move.w #$43A0,obj.sprite_tile(a1) (objects/r8/seesaw.asm:48)"),
     ]),
]

EXTRA_COLUMNS = [c for r in ROUTINES for c in [r[0] + "Id"] + [f[0] for f in r[4]]]


def index_entries(stage: str, data: bytes) -> list[int]:
    from object_catalogue import operands
    tables = operands(data, OBJECT_INDEX)
    require(len(tables) == 1, f"{stage} ObjectIndex: expected one operand, got {tables}")
    entries = []
    while len(entries) < 0x80:
        at = tables[0] + 4 * len(entries)
        address = int.from_bytes(data[at:at + 4], "big")
        if not WORD_RAM <= address < WORD_RAM + len(data):
            break
        entries.append(address - WORD_RAM)
    return entries


def field_value(stage: str, data: bytes, entry: int, column: str, signature: str, distance, kind):
    # A tuple of distances is for code whose length depends on a stage-wide assembly condition (the
    # spike chain's child count by STAGE_TIME): exactly one of them must match.
    distances = distance if isinstance(distance, tuple) else (distance,)
    matches = [m for m in (pattern(signature).match(data, entry + d) for d in distances) if m is not None]
    require(len(matches) == 1, f"{stage} {column}: signature not at exactly one of entry+{list(distances)}")
    m = matches[0]
    if kind == "operand":
        groups = m.groupdict()
        if groups.get("abs") is not None:
            offset = (int.from_bytes(groups["abs"], "big") & 0xFFFFFF) - WORD_RAM
        elif groups.get("d16") is not None:
            offset = m.start("d16") + int.from_bytes(groups["d16"], "big", signed=True)
        else:
            offset = m.start("d8") - 1 + int.from_bytes(groups["d8"], "big", signed=True)
        require(0 <= offset < len(data), f"{stage} {column} at {offset:#x} is outside the stage file")
        return offset
    size, at = kind
    start = m.start() + at
    return data[start] if size == "u8" else int.from_bytes(data[start:start + 2], "big")


def extra_columns(stage: str, data: bytes) -> dict:
    entries = index_entries(stage, data)
    row = {}
    for name, key, distances, _, fields in ROUTINES:
        regex = pattern(key)
        found = next(((i + 1, entry) for i, entry in enumerate(entries)
                      if any(regex.match(data, entry + d) for d in distances)), None)
        row[name + "Id"] = found[0] if found else None
        for column, signature, distance, kind, _ in fields:
            row[column] = field_value(stage, data, found[1], column, signature, distance, kind) if found else None
    return row
