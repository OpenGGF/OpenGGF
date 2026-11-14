"""Tidal Tempest boss anchors for tools/soniccd/object_catalogue.py (owner: bossttz).

Owned by the Tidal Tempest boss lane; writes src/main/resources/soniccd/object-catalogue-bossttz.tsv,
read by objects/boss/ttz/TtzBossCatalogue. Only the act 3 stages (R43C, R43D; R4_VARIANT 8 and 9) link the
boss door, the chase and the boss (Level/Tidal Tempest/R4 Shared.asm:256-264, which include
objects/r4/boss_door.asm, boss_chase.asm and boss.asm). Each init writes oYRadius, oTile, then oMap; the
radius and the instruction after tell the parts apart.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R43")


ANCHORS = [
    ('bossDoorMappings', '117C 0040 0016 317C 446A 0002 217C AAAAAAAA 0004', 'optional',
     'BossDoorObject_0_Routine0 (objects/r4/boss_door.asm:28-30): move.b #$40,obj.height; move.w #$446A,obj.sprite_tile; move.l #BossDoorSprites,obj.sprite_data'),
    ('chaseMobileMappings', '117C 0038 0016 317C 031E 0002 217C AAAAAAAA 0004 217C 0001 C000 003C', 'optional',
     'EggMobileChaseObject_0_Routine0 (objects/r4/boss_chase.asm:43-46): move.b #$38,obj.height; move.w #$31E,obj.sprite_tile; move.l #EggMobileChaseSprites,obj.sprite_data; move.l #$1C000,obj.var_3c'),
    ('chaseMobileAnimations', '43F9 AAAAAAAA 4A28 002D', 'optional',
     'EggMobileChaseObject (objects/r4/boss_chase.asm:8-9): lea EggMobileChaseAnims,a1; tst.b obj.var_2d(a0)'),
    ('chaseEggmanMappings', '117C 0038 0016 317C 031E 0002 217C AAAAAAAA 0004 6100', 'optional',
     'EggmanChaseObject_0_Routine0 (objects/r4/boss_chase.asm:711-714): move.b #$38,obj.height; move.w #$31E,obj.sprite_tile; move.l #EggmanChaseSprites,obj.sprite_data; bsr.w sub_20C014'),
    ('chaseEggmanAnimations', '1169 0022 0022 43F9 AAAAAAAA', 'optional',
     'loc_20C19C (objects/r4/boss_chase.asm:737-738): move.b obj.flags(a1),obj.flags(a0); lea EggmanChaseAnims,a1'),
    ('chasePath', '43F9 AAAAAAAA 7000 1028 0025', 'optional',
     'loc_20BB32 (objects/r4/boss_chase.asm:75-80): lea byte_20C062,a1; moveq #0,d0; move.b obj.routine_2(a0),d0'),
    ('chasePathUpper', '0828 0007 0025 67?? 43F9 AAAAAAAA', 'optional',
     'EggMobileChaseObject_0_Routine2 (objects/r4/boss_chase.asm:60-62): btst #7,obj.routine_2(a0); beq.s loc_20BB22; lea byte_20C0D4,a1'),
    ('chasePathLower', '0828 0006 0025 67?? 43F9 AAAAAAAA', 'optional',
     'loc_20BB22 (objects/r4/boss_chase.asm:67-70): btst #6,obj.routine_2(a0); beq.s loc_20BB32; lea byte_20C0E6,a1'),
    ('eggmanMappings', '117C 002C 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'EggmanObject_0_Routine0 (objects/r4/boss.asm:56-58): move.b #$2C,obj.height; move.w #$31E,obj.sprite_tile; move.l #EggmanSprites,obj.sprite_data'),
    ('eggmanAnimations', '4EBB 00?? 43F9 AAAAAAAA 4EB9 ???????? 4EB9 ???????? 4E75', 'optional',
     'EggmanObject loc_20C3FC (objects/r4/boss.asm:25-29): jsr off_20C41E(pc,d0.w); lea EggmanAnims,a1; jsr AnimateObject; jsr DrawObject; rts'),
    ('eggMobileMappings', '117C 001C 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'EggMobileObject_0_Routine0 (objects/r4/boss.asm:539-541): move.b #$1C,obj.height; move.w #$31E,obj.sprite_tile; move.l #EggMobileSprites,obj.sprite_data'),
    ('missileMappings', '117C 0004 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'BossMissileObject_0_Routine0 (objects/r4/boss.asm:1223-1225): move.b #4,obj.height; move.w #$31E,obj.sprite_tile; move.l #BossMissileSprites,obj.sprite_data'),
    ('missileAnimations', '4EBB 00?? 43F9 AAAAAAAA 4EB9 ???????? 4EF9', 'optional',
     'BossMissileObject (objects/r4/boss.asm:1204-1207): jsr off_20CFA2(pc,d0.w); lea BossMissileAnims,a1; jsr AnimateObject; jmp DrawObject'),
    ('bubbleMappings', '117C 0010 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'BossBubbleObject_0_Routine0 (objects/r4/boss.asm:625-627): move.b #$10,obj.height; move.w #$31E,obj.sprite_tile; move.l #BossBubbleSprites,obj.sprite_data'),
    ('bubbleAnimations', '4228 001B 43F9 AAAAAAAA', 'optional',
     'loc_20CAB6/loc_20CAD4 (objects/r4/boss.asm:708-711): clr.b obj.anim_index(a0); lea BossBubbleAnims,a1'),
    ('explosionOffsets', '50E9 0025 45F9 AAAAAAAA D4C2', 'optional',
     'sub_20C928 (objects/r4/boss.asm:566-568): st obj.routine_2(a1); lea word_20C984,a2; adda.w d2,a2'),
    ('bubbleThresholds', '1028 0033 103B 00QQ', 'optional',
     'loc_20C686 (objects/r4/boss.asm:260-262): move.b obj.var_33(a0),d0; move.b byte_20C6AC(pc,d0.w),d0'),
    ('ringSpacing', '1029 002D D040 303B 00QQ', 'optional',
     'BossBubbleObject_0_Routine10 (objects/r4/boss.asm:968-970): move.b obj.var_2d(a1),d0; add.w d0,d0; move.w word_20CDBE(pc,d0.w),d0'),
    ('missileLaunch', '45F9 AAAAAAAA 7003', 'optional',
     'sub_20CEFA (objects/r4/boss.asm:1139-1140): lea byte_20CF5A,a2; moveq #3,d0'),
    ('camBounds', '43F9 AAAAAAAA 3019 3219 3419 3619', 'optional',
     'EggmanObject_0_Routine4 (objects/r4/boss.asm:371-375): lea word_202BA4+2,a1; move.w (a1)+,d0; move.w (a1)+,d1; move.w (a1)+,d2; move.w (a1)+,d3'),
]
