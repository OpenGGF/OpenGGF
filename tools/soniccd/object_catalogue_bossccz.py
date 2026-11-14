"""Collision Chaos boss anchors for tools/soniccd/object_catalogue.py (owner: bossccz).

Owned by the Collision Chaos boss lane; writes src/main/resources/soniccd/object-catalogue-bossccz.tsv,
read by objects/boss/ccz/CczBossCatalogue. Only the act 3 stages (R33C, R33D) link the boss
(Collision Chaos/R3 Shared.asm:144 includes objects/r3/boss.asm; Act 3 Good Future.asm and Act 3 Bad
Future.asm include R3 Shared.asm). Every part's init writes obj.sprite_tile then obj.sprite_data; the part's
obj.width_2 ($19), obj.height ($16) and obj.sprite_layer ($18) before them tell the parts apart. Tables the
routines read through d16(PC) or d8(PC,Xn) are anchored on the consuming instruction.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R33")


ANCHORS = [
    ('entryBarrierMappings', '117C 0030 0019 117C 0010 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'EntryBarrier_Init (objects/r3/boss.asm:58-61): width $30, height $10, tile $31E, EntryBarrierSprites'),
    ('entryBarrierAnimations', '117C 0000 001C 43F9 AAAAAAAA', 'optional',
     'EntryBarrier_Retract (objects/r3/boss.asm:96-97): move.b #0,obj.anim_id(a0); lea EntryBarrierAnims,a1'),
    ('machineMappings', '117C 0050 0019 117C 0010 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'BossMachine_Init (objects/r3/boss.asm:239-242): width $50, height $10, tile $31E, BossMachineSprites'),
    ('eggmanMappings', '117C 0006 0018 117C 0010 0019 117C 0010 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'Eggman_Init (objects/r3/boss.asm:747-751): layer 6, width $10, height $10, tile $31E, EggmanSprites'),
    ('eggmanAnimations', '4A28 0001 6A?? 43F9 AAAAAAAA', 'optional',
     'EggmanObject (objects/r3/boss.asm:725-727): tst.b obj.sprite_flags(a0); bpl.s .Draw; lea EggmanAnims,a1'),
    ('gearMappings', '117C 0003 0018 117C 0010 0019 117C 0010 0016 317C 031E 0002 217C AAAAAAAA 0004', 'optional',
     'GearObject (objects/r3/boss.asm:825-829): layer 3, width $10, height $10, tile $31E, GearSprites'),
    ('gearAnimations', '0829 0002 002C 67?? 43F9 AAAAAAAA', 'optional',
     'GearObject .FollowBoss (objects/r3/boss.asm:836-838): btst #2,boss.flags(a1); beq.s .Draw; lea GearAnims,a1'),
    ('launcherMappings', '117C 0018 0019 117C 0010 0016 317C 047C 0002 217C AAAAAAAA 0004', 'optional',
     'BombLauncher_Init (objects/r3/boss.asm:881-884): width $18, height $10, tile $47C, BombLaunchSprites'),
    ('launcherFrames', '103B 00QQ 1140 001A', 'optional',
     'BombLauncher_AnimateFiring .SetFrame (objects/r3/boss.asm:945-947): move.b BombLauncher_FiringFrames(pc,d0.w),d0; move.b d0,obj.sprite_frame(a0)'),
    ('bombMappings', '117C 0008 0019 117C 0008 0016 317C 047C 0002 217C AAAAAAAA 0004', 'optional',
     'Bomb_Init (objects/r3/boss.asm:993-996): width 8, height 8, tile $47C, BombSprites'),
    ('bombExplosionMappings', '317C 0680 0002 217C AAAAAAAA 0004 43F9', 'optional',
     'Bomb_ConvertToExplosion (objects/r3/boss.asm:1119-1123): tile $680, ExplosionSprites; Bomb_Explosion lea'),
    ('bombExplosionAnimations', '317C 0680 0002 217C ???????? 0004 43F9 AAAAAAAA', 'optional',
     'Bomb_Explosion (objects/r3/boss.asm:1120-1123): lea ExplosionAnims,a1'),
    ('exitBarrierMappings', '317C 0474 0002 217C AAAAAAAA 0004', 'optional',
     'ExitBarrier_InitCommon (objects/r3/boss.asm:1249-1250): tile $474, ExitBarrierSprites'),
    ('exitBarrierAnimations', '43FA PPPP 4EB9 ???????? 1028 001A B028 002A', 'optional',
     'ExitBarrier_Extend (objects/r3/boss.asm:1254-1258): lea ExitBarrierAnims(pc),a1; jsr AnimateObject; frame vs target'),
    ('playerYThresholds', '303B 00QQ 3238 D00C B240', 'optional',
     'BossMachine_CanSpawnExitBarrier (objects/r3/boss.asm:325-328): move.w BossMachine_PlayerYThresholds(pc,d0.w),d0; move.w player_object+obj.y,d1; cmp.w d0,d1'),
    ('exitBarrierY', '303B 00QQ 3340 000C', 'optional',
     'BossMachine_SpawnExitBarrier (objects/r3/boss.asm:383-385): move.w BossMachine_ExitBarrierYPositions(pc,d0.w),d0; move.w d0,obj.y(a1)'),
    ('defeatExplosions', '0C68 0078 003E 67?? 64?? 45FA PPPP', 'optional',
     'BossArena_UpdateDefeatExplosions (objects/r3/boss.asm:582-587): cmpi.w #$78; beq; bcc; lea BossArena_DefeatExplosionPattern(pc),a2'),
    ('lowerHitExplosions', '0010 FFF0 0020 0000 5268 003E 0C68 0004 003E 64?? 45FA PPPP', 'optional',
     'BossMachine_SpawnLowerHitExplosions (objects/r3/boss.asm:615-623), after the defeat pattern: lea BossMachine_LowerHitExplosionPattern(pc),a2'),
    ('upperHitExplosions', 'FFE0 0000 0020 0000 5268 003E 0C68 0004 003E 64?? 45FA PPPP', 'optional',
     'BossMachine_SpawnUpperHitExplosions (objects/r3/boss.asm:636-645), after the lower pattern: lea BossMachine_UpperHitExplosionPattern(pc),a2'),
    ('defeatHitExplosions', '0C68 0003 003E 64?? 45FA PPPP', 'optional',
     'BossMachine_SpawnDefeatHitExplosions (objects/r3/boss.asm:664-667): cmpi.w #3; bcc; lea BossMachine_DefeatHitExplosionPattern(pc),a2'),
]
