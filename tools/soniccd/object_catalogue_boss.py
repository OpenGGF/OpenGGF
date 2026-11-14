"""Boss-pattern anchors for tools/soniccd/object_catalogue.py (owner: boss).

Owned by the boss pattern (objects/boss); writes src/main/resources/soniccd/object-catalogue-boss.tsv,
read by objects/boss/CdBossCatalogue. These are the routines every act 3 stage links beside its boss:
the flower capsule (ObjCapsule, Level/_Objects/Level End/Main.asm:105-380) and LoadCapsulePal
(:214-222). A zone's own boss tables live in its own owner, object_catalogue_boss<zone>.py.
"""

def STAGES_COVERED(stage: str) -> bool:
    """Act 3 stages (R?3C, R?3D): the only ones with a boss and its capsule."""
    return stage[2] == "3"


ANCHORS = [
    ('capsuleMappings', '117C 0004 0018 217C AAAAAAAA 0004 317C 2481 0002', 'optional',
     'ObjCapsule_Init (Level End/Main.asm:159-167): move.b #4,oPriority; move.l #MapSpr_FlowerCapsule,oMap; move.w #$2481,oTile'),
    ('capsuleAnimations', '117C 0018 0016 43F9 AAAAAAAA', 'optional',
     'ObjCapsule_Init/_Main (Level End/Main.asm:167-175): move.b #$18,oYRadius; lea Ani_FlowerCapsule,a1'),
    ('capsulePalette', '43F9 AAAAAAAA 45F8 FB20', 'optional',
     'LoadCapsulePal (Level End/Main.asm:913-921): lea Pal_FlowerCapsule,a1; lea palette+($10*2).w,a2'),
    ('capsuleExplosionOffsets', 'E448 0240 0007 D040 45FB 00QQ', 'optional',
     'ObjCapsule_Explode (Level End/Main.asm:225-230): lsr.w #2,d0; andi.w #7,d0; add.w d0,d0; lea .ExplosionPos(pc,d0.w),a2'),
    ('capsuleSeedSpeeds', '337C FA00 0012 337B 10QQ 0010', 'optional',
     'ObjCapsule_SpawnSeeds (Level End/Main.asm:294-295): move.w #-$600,oYVel(a1); move.w .SeedVel(pc,d1.w),oXVel(a1)'),
]
