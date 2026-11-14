"""Wacky Workbench boss anchors for tools/soniccd/object_catalogue.py (owner: bosswwz).

Owned by the Wacky Workbench boss lane; writes src/main/resources/soniccd/object-catalogue-bosswwz.tsv, read by
objects/boss/wwz/WwzBossCatalogue. Only the act 3 stages (R63C, R63D) link the boss: Wacky Workbench/R6 Shared.asm
includes objects/r6/boss.asm for R6_VARIANT 8 (Good Future) and 9 (Bad Future). Each table is located through the
instruction that reads it; the inits tell the parts apart by oWidth, oYRadius and oTile before oMap.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R63")


ANCHORS = [
    ('eggmanMappings', '317C 03EC 0002 217C AAAAAAAA 0004', 'optional',
     'EggmanInitialize (objects/r6/boss.asm:114-127): move.w #$3EC,obj.sprite_tile; move.l #EggmanSprites,obj.sprite_data'),
    ('eggmanAnimations', '6100 ???? 6100 ???? 7000 1028 0024 303B 00?? 4EBB 00?? 6100 ???? 43F9 AAAAAAAA', 'optional',
     'EggmanObject (objects/r6/boss.asm:3-14): two bsr.w, the routine dispatch, bsr.w EggmanUpdateBossFlags, lea EggmanAnims,a1'),
    ('machineMappings', '117C 0040 0019 117C 0030 0016 317C 2300 0002 217C AAAAAAAA 0004', 'optional',
     'BossMachineInit (objects/r6/boss.asm:706-715): width $40, height $30, tile $2300, BossMachineSprites'),
    ('spikesMappings', '317C 2300 0002 217C AAAAAAAA 0004 4E75 3268 002E 3169 0008 0008 3169 000C 000C 0468 0040 000C',
     'optional', 'BossSpikesInit/BossSpikesFollowParent (objects/r6/boss.asm:753-776): BossSpikesSprites, then the'
     ' parent X and Y - $40'),
    ('spikesAnimation', '45F9 AAAAAAAA 5228 001A', 'optional',
     'BossSpikesAdvanceFrame (objects/r6/boss.asm:797-817): lea BossSpikesAnim,a2; addq.b #1,obj.sprite_frame'),
    ('exhaustMappings', '317C 2300 0002 217C AAAAAAAA 0004 4E75 3268 002E 3169 0008 0008 3169 000C 000C 0668 0040 000C',
     'optional', 'BossExhaustInit/BossExhaustFollowParent (objects/r6/boss.asm:846-862): BossExhaustSprites, then the'
     ' parent X and Y + $40'),
    ('exhaustAnimations', '117C 00BE 0020 117C 0002 0021 7000 1028 0024 303B 00?? 4EBB 00?? 43F9 AAAAAAAA', 'optional',
     'BossExhaustObject (objects/r6/boss.asm:823-832): collide $BE/2, the routine dispatch, lea BossExhaustAnims,a1'),
    ('fallSpikeMappings', '117C 0004 0019 117C 000C 0016 317C 2300 0002 217C AAAAAAAA 0004', 'optional',
     'FallSpikeInit (objects/r6/boss.asm:972-995): width 4, height $C, tile $2300, FallSpikeSprites'),
    ('smokeMappings', '117C 0004 0001 317C 2300 0002 217C AAAAAAAA 0004 4A28 001C', 'optional',
     'BossSmokeInit (objects/r6/boss.asm:889-893): sprite flags 4, tile $2300, BossSmokeSprites, tst.b obj.anim_id'),
    ('smokeAnimations', '43F9 AAAAAAAA 4EB9 ???????? 0C28 0007 001A', 'optional',
     'BossSmokeFollowParent (objects/r6/boss.asm:914-928): lea BossSmokeAnims,a1; jsr AnimateObject;'
     ' cmpi.b #7,obj.sprite_frame'),
    ('conductorMappings1', '4A28 0028 ???? 217C AAAAAAAA 0004', 'optional',
     'ConductorInit (objects/r6/boss.asm:1422-1442): tst.b obj.subtype; bmi.s; move.l #ConductorSprites1,obj.sprite_data'),
    ('conductorMappings2', '117C 0008 0019 117C 0010 0016 217C AAAAAAAA 0004 117C 000A 0024', 'optional',
     'ConductorInitAlternate (objects/r6/boss.asm:1457-1463): width 8, height $10, ConductorSprites2, routine $A'),
    ('debrisMappings1', '317C 4440 0002 217C AAAAAAAA 0004', 'optional',
     'FloorDebrisInit (objects/r6/boss.asm:1044-1053): tile $4440, DebrisSprites1'),
    ('debrisAnimations', '317C 4440 0002 217C ???????? 0004 4E75 43F9 AAAAAAAA', 'optional',
     'FloorDebrisInit/FloorDebrisFall (objects/r6/boss.asm:1044-1069): rts, then lea DebrisAnims,a1'),
    ('debrisMappings2', '237C AAAAAAAA 0004 1342 001A', 'optional',
     'BossInitFloorDebrisPiece (objects/r6/boss.asm:1814-1816): move.l #DebrisSprites2,obj.sprite_data(a1);'
     ' move.b d2,obj.sprite_frame(a1)'),
    ('floorPieceMappings', '317C 0440 0002 217C AAAAAAAA 0004', 'optional',
     'FloorPieceInit (objects/r6/boss.asm:1144-1161): tile $440, FloorPieceSprites'),
    ('electricityMappings', '317C 4470 0002 217C AAAAAAAA 0004', 'optional',
     'ElectricityInit (objects/r6/boss.asm:1092-1119): tile $4470, ElectricitySprites'),
    ('descentTargets', '7000 1028 002D D040 303B 00QQ B068 000C 6F02', 'optional',
     'EggmanDescend (objects/r6/boss.asm:176-185): move.w EggmanDescentTargets(pc,d0.w),d0; cmp.w obj.y; ble.s'),
    ('ascentTargets', '7000 1028 002D D040 303B 00QQ B068 000C 6C02', 'optional',
     'EggmanAscend (objects/r6/boss.asm:266-275): move.w EggmanAscentTargets(pc,d0.w),d0; cmp.w obj.y; bge.s'),
    ('targetYTable', '7000 1028 002D 5340 D040 303B 00QQ', 'optional',
     'BossGetEggmanTargetY (objects/r6/boss.asm:1671-1678): move.w BossEggmanTargetYTable(pc,d0.w),d0'),
    ('floorPieceMotion', 'C0FC 0010 43F9 AAAAAAAA', 'optional',
     'FloorPieceInit (objects/r6/boss.asm:1144-1161): mulu.w #$10,d0; lea FloorPieceMotionTable,a1'),
    ('floorPieceXTable', '45F9 AAAAAAAA 3032 1000', 'optional',
     'BossSpawnSpikeWave (objects/r6/boss.asm:1726-1754): lea BossSpikeSpawnXTable,a2; move.w (a2,d1.w),d0'),
    ('debrisMotion', '45F9 AAAAAAAA 7403 2248', 'optional',
     'BossSpawnFloorDebrisBurst (objects/r6/boss.asm:1793-1796): lea BossFloorDebrisMotionTable,a2; moveq #3,d2;'
     ' movea.l a0,a1'),
    ('electricityYTable', 'D040 363B 00QQ 343C 0A10', 'optional',
     'BossSpawnSpikePair (objects/r6/boss.asm:1835-1838): move.w BossSpikeYTable(pc,d0.w),d3; move.w #$A10,d2'),
    ('escapeRightBound', '31F9 AAAAAAAA F72A', 'optional',
     'EggmanDefeatSequenceReward (objects/r6/boss.asm:578-600): move.w R6ScrollInitialStateTable+4,right_bound'),
    ('fadeStagePalettes', '1680 47FB 00QQ 31DB ????', 'optional',
     'BossFadeStageWritePalette (common/palette_fade.asm:410-413): move.b d0,(a3);'
     ' lea BossFadeStagePaletteTable(pc,d0.w),a3; move.w (a3)+,palette+$40'),
]
