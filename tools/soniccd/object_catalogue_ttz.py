"""Tidal Tempest object anchors for tools/soniccd/object_catalogue.py (owner: ttz).

Owned by the Tidal Tempest object lane; writes src/main/resources/soniccd/object-catalogue-ttz.tsv,
read by objects/ttz/TtzObjectCatalogue. Add one (column, signature, mode,
citation) per table; see the core module for the modes and signature tokens.
Citations name the src/objects/r4/<object>.asm routine of scddisasm pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
"""


def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R4")


ANCHORS = [
    ('sectionRanges', '43FA PPPP 7000 7200 3038 ???? B059 65?? 5401 60??', 'optional',
     'InitSectionGfx (src/r4/section_gfx_2a.asm:3-15): lea SectionGfxRanges(pc),a1; the update and init lists follow it (:52-70)'),
    ('switchMappings', '117C 0010 0017 117C 0010 0019 317C 050A 0002 217C AAAAAAAA 0004 117C 000A 0016', 'optional',
     'SwitchObject (objects/r4/switch.asm:9-13): oXRadius and oWidth $10; move.w #$50A,oTile(a0); move.l #SwitchSprites,oMap(a0); move.b #$A,oYRadius(a0)'),
    ('doorMappings', '317C 4440 0002 117C 0002 0018 217C AAAAAAAA 0004', 'optional',
     'DoorObject_0_Routine0 (objects/r4/door.asm:30-32): move.w #$4440,oTile(a0); move.b #2,oPriority(a0); move.l #DoorSprites,oMap(a0)'),
    ('doorSizes', '43FB 00QQ 1159 0019 1159 0016 1159 003B 1159 001A', 'optional',
     'DoorObject_0_Routine0 (objects/r4/door.asm:43-47): lea byte_20CD8A(pc,d0.w),a1; the width, height, travel and frame bytes'),
    ('moveWallMappings', '117C 0003 0018 117C 0020 0017 117C 0020 0019 117C 0040 0016 317C 446A 0002 217C AAAAAAAA 0004', 'optional',
     'MoveWallObject_0_Routine0 (objects/r4/move_wall.asm:21-28): move.w #$446A,obj.sprite_tile(a0); move.l #MoveWallSprites,obj.sprite_data(a0)'),
    ('rotateBlockMappings', '5428 0024 0028 0004 0001 117C 0003 0018 217C AAAAAAAA 0004 317C 4300 0002', 'optional',
     'RotateBlockObject_1_Routine0 (objects/r4/rotate_block.asm:33-38): move.l #RotateBlockSprites,obj.sprite_data(a0); move.w #$4300,obj.sprite_tile(a0)'),
    ('shooterMappings', '117C 0003 0018 117C 0008 0017 117C 0008 0019 117C 0010 0016 317C 4318 0002 217C AAAAAAAA 0004', 'optional',
     'ShooterObject (objects/r4/shooter.asm:9-15): move.w #$4318,obj.sprite_tile(a0); move.l #ShootSprites,obj.sprite_data(a0)'),
    ('missileMappings', '337C 0318 0002 237C AAAAAAAA 0004', 'optional',
     'ShooterObject (objects/r4/shooter.asm:34-48): move.w #$318,obj.sprite_tile(a1); move.l #MissileSprites,obj.sprite_data(a1)'),
    ('missileAnimations', 'D1A8 0008 43FA PPPP 4EB9', 'optional',
     'loc_20F32E (objects/r4/shooter.asm:64-74): add.l d0,obj.x(a0); lea MissileAnims(pc),a1; jsr AnimateObject'),
    ('bigDoorMappings', '0028 0004 0001 317C 446A 0002 217C AAAAAAAA 0004 217C 4040 0340 0016', 'optional',
     'BigDoorObject_0_Routine0 (objects/r4/big_door.asm:18-30): move.w #$446A,obj.sprite_tile(a0); move.l #BigDoorSprites,obj.sprite_data(a0); move.l #$40400340,obj.height(a0)'),
    ('maceMappings', '117C 0003 0018 117C 0008 0019 117C 0008 0016 217C AAAAAAAA 0004 317C 0368 0002', 'optional',
     'MaceObject_0_Routine0 (objects/r4/mace.asm:26-34): move.l #MaceSprites,obj.sprite_data(a0); move.w #$368,obj.sprite_tile(a0)'),
    ('maceChain2Mappings', '117C 0003 0018 217C AAAAAAAA 0004 317C 0368 0002', 'optional',
     'MaceObject_1_Routine0 (objects/r4/mace.asm:208-216): move.l #MaceChainSprites2,obj.sprite_data(a0); move.w #$368,obj.sprite_tile(a0)'),
    ('maceChain1Mappings', '117C 0002 0018 217C AAAAAAAA 0004 317C 0370 0002', 'optional',
     'MaceObject_1_Routine0 (objects/r4/mace.asm:220-224): move.l #MaceChainSprites1,obj.sprite_data(a0); move.w #$370,obj.sprite_tile(a0)'),
    ('waterSurfaceMappings', '0028 0004 0001 317C 8280 0002 217C AAAAAAAA 0004', 'optional',
     'WaterSurfaceObject_0_Routine0 (objects/r4/water_surface.asm:18-22): move.w #$8280,obj.sprite_tile(a0); move.l #WaterSurfaceSprites,obj.sprite_data(a0)'),
    ('waterSurfaceAnimations', '5468 000C 43F9 AAAAAAAA 4EF9', 'optional',
     'WaterSurfaceObject_0_Routine2 (objects/r4/water_surface.asm:24-45): addq.w #2,obj.y(a0); lea WaterSurfaceAnims,a1; jmp AnimateObject'),
    ('splashMappings', '117C 0004 0001 117C 0001 0018 217C AAAAAAAA 0004 317C 0298 0002', 'optional',
     'SplashObject_0_Routine0 (objects/r4/splash.asm:18-24): move.l #SplashSprites,obj.sprite_data(a0); move.w #$298,obj.sprite_tile(a0)'),
    ('splashAnimations', '5868 000C 43F9 AAAAAAAA 6100', 'optional',
     'SplashObject_0_Routine2 (objects/r4/splash.asm:34-39): addq.w #4,obj.y(a0); lea SplashAnims,a1; bsr.w AnimateObject'),
    ('fallSpikesMappings', '117C 0003 0018 117C 0010 0017 117C 0010 0019 117C 0040 0016 317C 03A0 0002 217C AAAAAAAA 0004', 'optional',
     'FallSpikesObject_0_Routine0 (objects/r4/fall_spikes.asm:40-48): move.w #$3A0,obj.sprite_tile(a0); move.l #FallSpikesSprites,obj.sprite_data(a0)'),
    ('bubbleMappings', '5428 0024 217C AAAAAAAA 0004 317C 848A 0002', 'optional',
     'BubbleSpawnerObject_0_Routine0 (objects/r4/bubble_spawner.asm:22-25): move.l #BubbleSprites,obj.sprite_data(a0); move.w #$848A,obj.sprite_tile(a0)'),
    ('bubbleAnimations', '43F9 AAAAAAAA 4EB9 ???????? 0C28 0006 001A', 'optional',
     'BubbleSpawnerObject_0_Routine2 (objects/r4/bubble_spawner.asm:47-53): lea BubbleSpawnAnims,a1; jsr AnimateObject; cmpi.b #6,obj.sprite_frame(a0)'),
    ('playerBubbleAnimations', '43F9 AAAAAAAA 4EB9 ???????? 3038 ???? B068 000C', 'optional',
     'PlayerBubblesObject_0_Routine2 (objects/r4/player_bubbles.asm:47-52): lea PlayerBubblesAnims,a1; jsr AnimateObject; move.w water_y,d0'),
    ('wobbleTable', '0240 007F 43F9 AAAAAAAA 1031 0000 4880 D068 0030', 'optional',
     'BubbleSpawnerObject_0_Routine4 (objects/r4/bubble_spawner.asm:66-77): andi.w #$7F,d0; lea WobbleTable,a1; move.b (a1,d0.w),d0; ext.w d0; add.w obj.var_30(a0),d0'),
    ('tonboMappings1', '43FA PPPP 203C FFFF 0000 323C 0004 343C 0100', 'ambiguous',
     'TonboObject_0_Routine0 (objects/r4/tonbo.asm:27-35): lea TonboSprites1(pc),a1; move.l #-$10000,d0; move.w #4,d1; move.w #$100,d2'),
    ('tonboMappings2', '43FA PPPP 203C FFFF 8000 323C 0001 343C 0200', 'ambiguous',
     'TonboObject_0_Routine0 (objects/r4/tonbo.asm:36-44): lea TonboSprites2(pc),a1; move.l #-$8000,d0; move.w #1,d1; move.w #$200,d2'),
    ('tonboAnimations', '0868 0000 0022 43FA PPPP 4EF9', 'ambiguous',
     'loc_20D5C4 (objects/r4/tonbo.asm:70-72): bchg #0,obj.flags(a0); lea TonboAnims(pc),a1; jmp AnimateObject'),
    ('circleSpikeMappings', '3D7C 0370 0002 2D7C AAAAAAAA 0004', 'optional',
     'sub_20EB82 (objects/r4/circle_spike_ball.asm:94-103): move.w #$370,obj.sprite_tile(a6); move.l #CircleSpikeBallSprites,obj.sprite_data(a6)'),
    ('fanABMappings1', '317C 4380 0002 1C28 0028 6A?? 117C 00FF 003F 43FA PPPP 117C 0010 0019 117C 0008 0016', 'optional',
     'FanObject (objects/r4/fan_a_b.asm:3-22): move.w #$4380,obj.sprite_tile(a0); lea FanSpritesAB1(pc),a1; move.b #$10,obj.width_2(a0); move.b #8,obj.height(a0)'),
    ('fanCDMappings1', '317C 4380 0002 1C28 0028 6A?? 5228 001A 43FA PPPP 117C 0010 0019 117C 0008 0016', 'optional',
     'FanObject (objects/r4/fan_c_d.asm:3-22): move.w #$4380,obj.sprite_tile(a0); lea FanSpritesCD1(pc),a1; move.b #$10,obj.width_2(a0); move.b #8,obj.height(a0)'),
    ('fanMappings2', '0806 0001 67?? 43FA PPPP 117C 0008 0019 117C 0010 0016', 'optional',
     'FanObject (objects/r4/fan_a_b.asm:22-26): btst #1,d6; lea FanSpritesAB2(pc),a1; move.b #8,obj.width_2(a0); move.b #$10,obj.height(a0) (fan_c_d.asm:24-28 is the same code)'),
    ('fanAnimations', '4A28 003F 66?? 117C 0000 001A 60?? 43FA PPPP', 'optional',
     'loc_20EED0 (objects/r4/fan_a_b.asm:60-69): tst.b obj.var_3f(a0); move.b #0,obj.sprite_frame(a0); lea FanAnimsAB(pc),a1'),
    ('elevatorMappings', '317C 435C 0002 117C 0003 0018 217C AAAAAAAA 0004', 'optional',
     'ElevatorObject_0_Routine0 (objects/r4/elevator.asm:74-82): move.w #$435C,obj.sprite_tile(a0); move.l #ElevatorSprites,obj.sprite_data(a0)'),
    ('pressureMappings', '117C 0020 0017 117C 0020 0019 317C 4310 0002 217C AAAAAAAA 0004', 'optional',
     'PressureElevatorObject_0_Routine0 (objects/r4/pressure_elevator.asm:30-40): move.w #$4310,obj.sprite_tile(a0); move.l #PressureElevatorSprites,obj.sprite_data(a0)'),
    ('crushMappings', '117C 0003 0018 117C 0010 0017 117C 0010 0019 117C 0010 0016 317C 446A 0002 217C AAAAAAAA 0004 117C 0004 001A', 'optional',
     'sub_20EB3E (objects/r4/crush_blocks.asm:19-28): move.w #$446A,obj.sprite_tile(a0); move.l #CrushBlocksSprites,obj.sprite_data(a0); move.b #4,obj.sprite_frame(a0)'),
    ('geyserMappings', '317C 6300 0002 117C 0001 0018 217C AAAAAAAA 0004 117C 0020 0019 117C 003C 0016', 'optional',
     'GeyserObject_0_Routine0 (objects/r4/geyser.asm:35-41): move.w #$6300,obj.sprite_tile(a0); move.l #GeyserSprites,obj.sprite_data(a0)'),
    ('geyserAnimations', '43F9 AAAAAAAA 4EB9 ???????? 3038 ???? 0440 0030', 'optional',
     'GeyserObject_0_Routine2 (objects/r4/geyser.asm:44-50): lea GeyserAnims,a1; jsr AnimateObject; move.w water_y,d0; subi.w #$30,d0'),
    ('amenboMappings1', '43FA PPPP 45FA ???? 203C FFFF FA00 223C 0000 0100', 'optional',
     'AmenboObject_0_Routine0 (objects/r4/amenbo.asm:25-47): lea AmenboSprites1(pc),a1; lea AmenboAnims1(pc),a2; move.l #-$600,d0; move.l #$100,d1'),
    ('amenboAnimations1', '43FA ???? 45FA PPPP 203C FFFF FA00 223C 0000 0100', 'optional',
     'AmenboObject_0_Routine0 (objects/r4/amenbo.asm:25-47): lea AmenboAnims1(pc),a2'),
    ('amenboMappings2', '43FA PPPP 45FA ???? 203C FFFF FE00 223C 0000 00C0', 'optional',
     'AmenboObject_0_Routine0 (objects/r4/amenbo.asm:25-47): lea AmenboSprites2(pc),a1; lea AmenboAnims2(pc),a2; move.l #-$200,d0; move.l #$C0,d1'),
    ('amenboAnimations2', '43FA ???? 45FA PPPP 203C FFFF FE00 223C 0000 00C0', 'optional',
     'AmenboObject_0_Routine0 (objects/r4/amenbo.asm:25-47): lea AmenboAnims2(pc),a2'),
    ('collapseFloorMappings', '317C 4364 0002 217C AAAAAAAA 0004 117C 0003 0018 117C 0011 0016', 'optional',
     'CollapseFloorObject_1_Routine0 (objects/r4/collapse_floor.asm:20-30): move.w #$4364,obj.sprite_tile(a0); move.l #CollapseFloorSprites,obj.sprite_data(a0); move.b #3,obj.sprite_layer(a0); move.b #$11,obj.height(a0)'),
    ('hMoveMappings1', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 3168 ???? 0032 117C 0030 0019 117C 0010 0016', 'optional',
     'HMoveBlockObject_1_Routine0 (objects/r4/h_move_block.asm:72-82): move.w #$446A,obj.sprite_tile(a0); move.l #HMoveBlockSprites1,obj.sprite_data(a0); move.w obj.x(a0),obj.var_32(a0); move.b #$30,obj.width_2(a0)'),
    ('hMoveMappings2', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 0020 0019 117C 0010 0016 4A28 003C', 'optional',
     'HMoveBlockObject_3_Routine0 (objects/r4/h_move_block.asm:221-230): move.l #HMoveBlockSprites2,obj.sprite_data(a0); move.b #$20,obj.width_2(a0); move.b #$10,obj.height(a0); tst.b obj.var_3c(a0)'),
    ('hMoveMappings3', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 0020 0019 117C 0020 0016 4A28 003C 66??', 'optional',
     'HMoveBlockObject_4_Routine0 (objects/r4/h_move_block.asm:320-330): move.l #HMoveBlockSprites3,obj.sprite_data(a0); move.b #$20,obj.width_2(a0); move.b #$20,obj.height(a0); tst.b obj.var_3c(a0); bne.s loc_20C50C'),
    ('hMoveMappings4', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 0040 0019 117C 0040 0016 4A28 003D', 'optional',
     'HMoveBlockObject_5_Routine0 (objects/r4/h_move_block.asm:428-436): move.l #HMoveBlockSprites4,obj.sprite_data(a0); move.b #$40,obj.width_2(a0); move.b #$40,obj.height(a0); tst.b obj.var_3d(a0)'),
    ('hMoveMappings5', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 3168 ???? 0036 117C 0020 0019 117C 0020 0016', 'optional',
     'HMoveBlockObject_6_Routine0 (objects/r4/h_move_block.asm:520-530): move.l #HMoveBlockSprites5,obj.sprite_data(a0); move.w obj.x(a0),obj.var_36(a0); move.b #$20,obj.width_2(a0); move.b #$20,obj.height(a0)'),
    ('hMoveMappings6', '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 0010 0016 4A28 003C', 'optional',
     'HMoveBlockObject_7_Routine0 (objects/r4/h_move_block.asm:622-630): move.l #HMoveBlockSprites6,obj.sprite_data(a0); move.b #$10,obj.height(a0); tst.b obj.var_3c(a0)'),
    ('tagaMappings1', '117C 0008 0016 117C 002D ???? 5428 0024 217C AAAAAAAA 0004 217C FFFF 0000 002C 317C 00C8 0036', 'optional',
     'TagaTagaObject_0_Routine0 (objects/r4/taga_taga.asm:44-57): move.b #8,obj.height(a0); move.b #$2D,obj.collide_type(a0); addq.b #2,obj.routine(a0); move.l #TagaTagaSprites1,obj.sprite_data(a0); move.l #-$10000,obj.var_2c(a0); move.w #$C8,obj.var_36(a0)'),
    ('tagaMappings2', '4A28 ???? 67?? 217C AAAAAAAA 0004 217C FFFF 8000 002C 317C 0190 0036', 'optional',
     'TagaTagaObject_0_Routine0 (objects/r4/taga_taga.asm:58-62): tst.b obj.subtype(a0); beq.s; move.l #TagaTagaSprites2,obj.sprite_data(a0); move.l #-$8000,obj.var_2c(a0); move.w #$190,obj.var_36(a0)'),
    ('tagaAnimations', '43FA PPPP 4EF9 ???? ???? 302E ???? 9068 ???? 0440 FFC8 0440 0038', 'optional',
     'TagaTagaObject_0_Routine2 loc_20DC98 (objects/r4/taga_taga.asm:156-160): lea TagaTagaAnims(pc),a1; jmp AnimateObject; sub_20DCA2: move.w obj.y(a6),d0; sub.w obj.y(a0),d0; subi.w #-$38,d0; subi.w #$38,d0'),
    ('tagaMissileMappings', '5428 0024 217C AAAAAAAA 0004 4EF9 ???? ???? 2028 002A D1A8 0008', 'optional',
     'TagaMissileObject_0_Routine0 (objects/r4/taga_taga.asm:289-295): addq.b #2,obj.routine(a0); move.l #TagaMissileSprites,obj.sprite_data(a0); jmp DrawObject; Routine4: move.l obj.var_2a(a0),d0; add.l d0,obj.x(a0)'),
    ('yagoMappings1', '317C 23F0 0002 217C AAAAAAAA 0004 06A8 0001 0000 000C 4EB9 ???????? 0C41 0005 6B?? 43F9 ???????? 4EB9 ???????? 4EB9 ???????? 3028 003E 4EF9 ???????? 5428 0024 217C 0050 0000 002A', 'optional',
     'YagoObject_0_Routine0 (objects/r4/yago.asm:41-56): move.w #$23F0,obj.sprite_tile(a0); move.l #YagoSprites1,obj.sprite_data(a0); Routine2 addi.l #$10000,obj.y(a0); jsr CheckBlockDown; loc_20DF12 tail; loc_20DF2E move.l #$500000,obj.var_2a(a0)'),
    ('yagoAnimations1', '317C 23F0 0002 217C ???????? 0004 06A8 0001 0000 000C 4EB9 ???????? 0C41 0005 6B?? 43F9 AAAAAAAA 4EB9 ???????? 4EB9 ???????? 3028 003E 4EF9 ???????? 5428 0024 217C 0050 0000 002A', 'optional',
     'YagoObject_0_Routine2 loc_20DF12 (objects/r4/yago.asm:49-56): lea YagoAnims1,a1; jsr AnimateObject; jsr DrawObject; move.w obj.var_3e(a0),d0; jmp CheckObjectDespawn2'),
    ('yagoMappings2', '317C 23F0 0002 217C AAAAAAAA 0004 06A8 0001 0000 000C 4EB9 ???????? 0C41 0005 6B?? 43F9 ???????? 4EB9 ???????? 4EB9 ???????? 3028 003E 4EF9 ???????? 5428 0024 5B41 D368 000C 217C 0050 0000 002A', 'optional',
     'YagoObject_1_Routine0 (objects/r4/yago.asm:269-290): move.l #YagoSprites2,obj.sprite_data(a0); Routine2 tail; loc_20E198 subq.w #5,d1; add.w d1,obj.y(a0); move.l #$500000,obj.var_2a(a0)'),
    ('yagoAnimations2', '317C 23F0 0002 217C ???????? 0004 06A8 0001 0000 000C 4EB9 ???????? 0C41 0005 6B?? 43F9 AAAAAAAA 4EB9 ???????? 4EB9 ???????? 3028 003E 4EF9 ???????? 5428 0024 5B41 D368 000C 217C 0050 0000 002A', 'optional',
     'YagoObject_1_Routine2 loc_20E17C (objects/r4/yago.asm:277-284): lea YagoAnims2,a1; jsr AnimateObject; jsr DrawObject; move.w obj.var_3e(a0),d0; jmp CheckObjectDespawn2'),
    ('spikesMappings', '317C 03A0 0002 117C 0004 0018 217C AAAAAAAA 0004', 'optional',
     'SpikesObject_0_Routine0 (objects/r4/spikes.asm:82-90): move.w #$3A0,obj.sprite_tile(a0); move.b #4,obj.sprite_layer(a0); move.l #SpikesSprites,obj.sprite_data(a0)'),
    ('woodBlockMappings', '117C 0010 0017 117C 0010 0019 117C 0008 0016 317C 0350 0002 217C AAAAAAAA 0004', 'optional',
     'WoodBlockObject_0_Routine0 (objects/r4/wood_block.asm:23-33): move.b #$10,obj.width(a0); move.b #$10,obj.width_2(a0); move.b #8,obj.height(a0); move.w #$350,obj.sprite_tile(a0); move.l #WoodBlockSprites,obj.sprite_data(a0)'),
    ('prizeSwitchMappings', '317C 050A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 000E 0019 117C 0008 0016', 'optional',
     'PrizeSwitchObject_0_Routine0 (objects/r4/prize.asm:21-31): move.w #$50A,obj.sprite_tile(a0); move.b #3,obj.sprite_layer(a0); move.l #PrizeSwitchSprites,obj.sprite_data(a0); move.b #$E,obj.width_2(a0); move.b #8,obj.height(a0)'),
    ('ringPrizeMappings', '117C 0008 0016 117C 0008 0017 217C AAAAAAAA 0004 317C A7AE 0002', 'optional',
     'RingPrizeObject_0_Routine0 (objects/r4/prize.asm:391-398): move.b #8,obj.height(a0); move.b #8,obj.width(a0); move.l #RingSprites,obj.sprite_data(a0); move.w #$A7AE,obj.sprite_tile(a0)'),
    ('ringPrizeAnimations', '117C 0001 0018 4EB9 ???????? 43F9 AAAAAAAA 4EB9', 'optional',
     'RingPrizeObject_0_Routine4 (objects/r4/prize.asm:442-449): move.b #1,obj.sprite_layer(a0); jsr sub_208ACA; RingPrizeObject_0_Routine6: lea RingPrizeAnims,a1; jsr AnimateObject'),
    ('pointsPrizeMappings', '217C AAAAAAAA 0004 317C 036E 0002 117C 0004 0001', 'optional',
     'PointsPrizeObject_0_Routine0 (objects/r4/prize.asm:485-489): move.l #PointsPrizeSprites,obj.sprite_data(a0); move.w #$36E,obj.sprite_tile(a0); move.b #4,obj.sprite_flags(a0)'),
]


# Routines assembled twice in a stage file (R4 Shared.asm, then again after the stage data as the "retained" copy):
# the lower address is the one the ObjectIndex reaches (the code section precedes the data section).
FIRST_HIT = {
    # AnimalObject (objects/r4/animal.asm:3-113); the boss acts (R43) assemble the routine twice.
    'animalTiles': '117C 000C 0016 43FA PPPP 7000 1039 ???? ???? E540 D039 ???? ???? D040 3171 0000 0002',
    'animalMappings1': '3171 0000 0002 0828 0000 0028 67?? 0668 2000 0002 43FA PPPP 0828 0000 0028 67??',
    'animalMappings2': '0668 2000 0002 43FA ???? 0828 0000 0028 67?? 43FA PPPP 2149 0004',
    'animalAnimations': '0868 0000 0001 0868 0000 0022 43FA PPPP 4EB9 ???? ???? 4EB9 ???? ???? 4EF9 ???? ????',
    # TwinMoveBlockObject_0_Routine0 (objects/r4/twin_move_block.asm:41-52); R42C and R42D link it, R43 does not.
    'twinMoveMappings': '317C 446A 0002 117C 0003 0018 217C AAAAAAAA 0004 117C 0010 0019 117C 0020 0016',
    # PlatformChainObject_0_Routine0 (objects/r4/platform_chain.asm:53-66); R43C and R43D assemble it twice.
    'platformChainMappings': '0028 0004 0001 317C 4340 0002 0C39 0002 ???????? 66?? 317C 4400 0002 217C AAAAAAAA 0004',
}
EXTRA_COLUMNS = tuple(FIRST_HIT)


def extra_columns(stage: str, data: bytes) -> dict:
    from object_catalogue import operands
    out = {}
    for column, signature in FIRST_HIT.items():
        hits = operands(data, signature)
        out[column] = hits[0] if hits else None
    return out
