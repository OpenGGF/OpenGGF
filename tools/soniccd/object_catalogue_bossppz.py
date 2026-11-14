"""Palmtree Panic boss anchors for tools/soniccd/object_catalogue.py (owner: bossppz).

Owned by the Palmtree Panic boss lane; writes src/main/resources/soniccd/object-catalogue-bossppz.tsv,
read by objects/boss/ppz/PpzBossCatalogue. Only the act 3 stages (R13C, R13D) link the boss
(Palmtree Panic/Act 3 Good Future.asm:66 and Act 3 Bad Future.asm:66 include Objects/Boss/Main.asm,
which includes objects/r1/boss.asm). Each init writes oTile then oMap; the part's oWidth and oYRadius
before them tell the parts apart.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R13")


ANCHORS = [
    ('eggmanMappings', '317C 03FD 0002 217C AAAAAAAA 0004', 'optional',
     'EggmanObject_0_Routine0 (objects/r1/boss.asm:317-318): move.w #$3FD,obj.sprite_tile; move.l #EggmanSprites,obj.sprite_data'),
    ('eggmanAnimations', '6100 ???? 6100 ???? 6100 ???? 6100 ???? 7000 1028 0024 303B 00?? 4EBB 00?? 43F9 AAAAAAAA', 'optional',
     'EggmanObject (objects/r1/boss.asm:3-14): four bsr.w, the routine dispatch, lea EggmanAnims,a1'),
    ('bodyMappings', '317C 0359 0002 217C AAAAAAAA 0004', 'optional',
     'BossBodyObject_0_Routine0 (objects/r1/boss.asm:729-730): move.w #$359,obj.sprite_tile; move.l #BossBodySprites,obj.sprite_data'),
    ('thighMappings', '117C 0008 0019 117C 0008 0016 317C 2359 0002 217C AAAAAAAA 0004 117C 000C 0024', 'optional',
     'BossThighObject_0_Routine0 (objects/r1/boss.asm:1811-1815): width 8, height 8, tile $2359, BossThighSprites, routine $C'),
    ('calfMappings', '117C 0008 0019 117C 0014 0016 317C 2359 0002 217C AAAAAAAA 0004', 'optional',
     'BossCalfObject_0_Routine0 (objects/r1/boss.asm:2219-2222): width 8, height $14, tile $2359, BossCalfSprites'),
    ('footMappings', '117C 0020 0019 117C 0014 0016 317C 2359 0002 217C AAAAAAAA 0004', 'optional',
     'BossFootObject_0_Routine0 (objects/r1/boss.asm:2470-2473): width $20, height $14, tile $2359, BossFootSprites'),
    ('shoulderMappings', '117C 0010 0019 117C 000C 0016 317C 2359 0002 217C AAAAAAAA 0004', 'optional',
     'BossShoulderObject_0_Routine0 (objects/r1/boss.asm:1359-1362): width $10, height $C, tile $2359, BossShoulderSprites'),
    ('armMappings', '117C 0008 0019 117C 0008 0016 317C 2359 0002 217C AAAAAAAA 0004 117C 0006 0024', 'optional',
     'BossArmObject_0_Routine0 (objects/r1/boss.asm:1457-1461): width 8, height 8, tile $2359, BossArmSprites, routine 6'),
    ('forearmMappings1', '137C 0004 0018 237C AAAAAAAA 0004', 'optional',
     'sub_20BD36 (objects/r1/boss.asm:425-426): move.b #4,obj.sprite_layer(a1); move.l #BossForearmSprites1,obj.sprite_data(a1)'),
    ('forearmMappings2', '137C 0007 0018 237C AAAAAAAA 0004', 'optional',
     'sub_20BD36 (objects/r1/boss.asm:452-453): move.b #7,obj.sprite_layer(a1); move.l #BossForearmSprites2,obj.sprite_data(a1)'),
    ('bumperMappings', '117C 000C 0019 117C 0010 0016 317C 2359 0002 217C AAAAAAAA 0004', 'optional',
     'BossBumperObject_0_Routine0 (objects/r1/boss.asm:1716-1719): width $C, height $10, tile $2359, BossBumperSprites'),
    ('walkTable', '45F9 AAAAAAAA 1028 0025 D4C0 4A12', 'optional',
     'EggmanObject_0_Routine4 loc_20C02C (objects/r1/boss.asm:555-568): lea byte_20C04E,a2; move.b obj.routine_2(a0),d0; adda.w d0,a2; tst.b (a2)'),
    ('explosionOffsets', '50E9 0025 45F9 AAAAAAAA D4C2', 'optional',
     'sub_20D72A (objects/r1/boss.asm:2704-2706): st obj.routine_2(a1); lea word_20D786,a2; adda.w d2,a2'),
]
