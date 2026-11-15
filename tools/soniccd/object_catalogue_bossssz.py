"""Stardust Speedway boss anchors for tools/soniccd/object_catalogue.py (owner: bossssz).

Owned by the Stardust Speedway boss lane; writes src/main/resources/soniccd/object-catalogue-bossssz.tsv,
read by objects/boss/ssz/SszBossCatalogue. Only the act 3 stages (R73C, R73D) link the race
(Level/Stardust Speedway/R7 Shared.asm:196-217: R7_VARIANT 9 and 10 include objects/r7/boss.asm,
boss_block.asm, spikes.asm and amy_rose.asm). Each init writes oTile then oMap; the part's oWidth and
oYRadius before them tell the parts apart. Script tables (a frame count and a routine offset per entry)
are read for their frame counts only.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R73")


ANCHORS = [
    ('eggmanMappings', '117C 0024 0019 117C 0024 0016 317C 0300 0002 217C AAAAAAAA 0004', 'optional',
     'EggmanObject_0_Routine0 (objects/r7/boss.asm:774-777): width $24, height $24, tile $300, EggmanSprites'),
    ('eggmanAnimations', '4EBB 00?? 6100 ???? 6100 ???? 43F9 AAAAAAAA', 'optional',
     'EggmanObject (objects/r7/boss.asm:753-756): jsr off_20C4B0(pc,d0.w); bsr.w sub_20C97C; bsr.w sub_20C940; lea EggmanAnims,a1'),
    ('metalMappings', '117C 0014 0019 117C 0018 0016 317C 037C 0002 217C AAAAAAAA 0004', 'optional',
     'MetalSonicObject_0_Routine0 (objects/r7/boss.asm:35-38): width $14, height $18, tile $37C, MetalSonicSprites'),
    ('metalAnimations', '6100 ???? 6100 ???? 7000 1028 0024 303B 00?? 4EBB 00?? 43F9 AAAAAAAA', 'optional',
     'MetalSonicObject (objects/r7/boss.asm:6-12): bsr.w sub_20BFAC; bsr.w sub_20BF54; the routine dispatch; lea MetalSonicAnims,a1'),
    ('shieldMappings', '117C 0018 0019 117C 0018 0016 317C 037C 0002 217C AAAAAAAA 0004', 'optional',
     'ElectricShieldObject_0_Routine0 (objects/r7/boss.asm:635-638): width $18, height $18, tile $37C, ElectricShieldSprites'),
    ('laserMappings', '117C 0008 0019 117C 0010 0016 317C 2300 0002 217C AAAAAAAA 0004', 'optional',
     'BossLaserObject_0_Routine0 (objects/r7/boss.asm:1482-1485): width 8, height $10, tile $2300, BossLaserSprites'),
    ('laserAnimations', '117C 0009 002B 43F9 AAAAAAAA', 'optional',
     'BossLaserObject_0_Routine2 (objects/r7/boss.asm:1534-1537): move.b #9,obj.var_2b(a0); lea BossLaserAnims,a1'),
    ('laserImpactMappings', '317C 0680 0002 217C AAAAAAAA 0004', 'optional',
     'BossLaserObject_0_Routine0 loc_20CB68 (objects/r7/boss.asm:1496-1497): move.w #$680,obj.sprite_tile; move.l #ExplosionSprites,obj.sprite_data'),
    ('laserImpactAnimations', '4A28 002D 67?? 4EF9 ???????? 43F9 AAAAAAAA', 'optional',
     'BossLaserObject_0_Routine6, loc_20CCA4 (objects/r7/boss.asm:1605-1612): tst.b obj.var_2d; beq.s; jmp DeleteObject; lea BossLaserImpactAnims,a1'),
    ('exhaustMappings', '117C 0014 0019 117C 0008 0016 317C 2300 0002 217C AAAAAAAA 0004', 'optional',
     'BossExhaustObject_0_Routine0 (objects/r7/boss.asm:1685-1688): width $14, height 8, tile $2300, BossExhaustSprites'),
    ('exhaustAnimations', 'D168 0008 3028 0038 D168 000C 43F9 AAAAAAAA', 'optional',
     'BossExhaustObject_0_Routine2 loc_20CDE0 (objects/r7/boss.asm:1719-1722): add.w d0,obj.x; move.w obj.var_38,d0; add.w d0,obj.y; lea BossExhaustAnims,a1'),
    ('metalDebrisMappings', '117C 0008 0019 117C 0008 0016 317C 037C 0002 217C AAAAAAAA 0004', 'optional',
     'MetalSonicDebrisObject_0_Routine0 (objects/r7/boss.asm:1756-1759): width 8, height 8, tile $37C, MetalSonicDebrisSprites'),
    ('debrisMappings', '117C 0004 0019 117C 0004 0016 317C 451C 0002 217C AAAAAAAA 0004', 'optional',
     'DebrisObject (objects/r7/boss.asm:1629-1632): width 4, height 4, tile $451C, DebrisSprites'),
    ('eggmanScript', '3140 0008 6100 ???? 47F9 AAAAAAAA 6000 ????', 'optional',
     'EggmanObject_0_Routine4 (objects/r7/boss.asm:899-904): move.w d0,obj.x; bsr.w sub_20C9FC; lea word_20C658,a3; bra.w sub_20CEE0'),
    ('metalScriptA', '4268 002A 4E75 6100 ???? 6100 ???? 47F9 AAAAAAAA', 'optional',
     'MetalSonicObject_0_RoutineA after sub_20C226 (objects/r7/boss.asm:465-473): clr.w obj.var_2a; rts; bsr.w; bsr.w; lea word_20C246,a3'),
    ('metalScriptC', '08A8 0006 002C 60?? 6100 ???? 6100 ???? 47F9 AAAAAAAA', 'optional',
     'MetalSonicObject_0_RoutineC after loc_20C27A (objects/r7/boss.asm:506-514): bclr #6,obj.var_2c; bra.s; bsr.w; bsr.w; lea word_20C294,a3'),
    ('metalScriptE', '6100 ???? 6100 ???? 47F9 AAAAAAAA 6100 ???? 4E75', 'optional',
     'MetalSonicObject_0_RoutineE (objects/r7/boss.asm:572-576): bsr.w; bsr.w; lea word_20C30E,a3; bsr.w sub_20CEE0; rts'),
    ('metalLiftTable', '45F9 AAAAAAAA 341A 6B00', 'optional',
     'sub_20CA8E (objects/r7/boss.asm:1407-1412): lea word_20CABA,a2; move.w (a2)+,d2; bmi.w'),
    ('eggmanSpeeds', '47FB 00PP 315B 0034', 'optional',
     'loc_20C922 (objects/r7/boss.asm:1229-1231): lea word_20C930(pc,d0.w),a3; move.w (a3)+,obj.var_34(a0)'),
    ('escapePalette', '588F 4EF9 ???????? 47FA PPPP 49F8 FB20', 'optional',
     'EggmanObject_2_Routine12, sub_20C85E (objects/r7/boss.asm:1135-1142): addq.l #4,sp; jmp DeleteObject; lea word_20C886(pc),a3; lea palette+$20,a4'),
    ('blockMappings', '317C 450C 0002 217C AAAAAAAA 0004', 'optional',
     'BossBlockObject (objects/r7/boss_block.asm:9-10): move.w #$450C,obj.sprite_tile; move.l #BossBlockSprites,obj.sprite_data'),
    ('blockSizes', '7000 1028 001A D040 43F9 AAAAAAAA', 'optional',
     'BossBlockObject loc_20EC66 (objects/r7/boss_block.asm:113-116): moveq #0,d0; move.b obj.sprite_frame,d0; add.w d0,d0; lea byte_20EBB4,a1'),
    ('spikesMappings', '317C 04FC 0002 217C AAAAAAAA 0004', 'optional',
     'SpikesObject_0_Routine0 (objects/r7/spikes.asm:22-23): move.w #$4FC,obj.sprite_tile; move.l #SpikesSprites,obj.sprite_data'),
    ('spikesDebrisSpeeds', '78F4 7603 45F9 AAAAAAAA', 'optional',
     'SpikesObject loc_20EE0A (objects/r7/spikes.asm:109-111): moveq #-$C,d4; moveq #3,d3; lea word_20EEA2,a2'),
    ('amyMappings', '317C A3CB 0002 117C 0001 0018 217C AAAAAAAA 0004', 'optional',
     'AmyRoseObject_0_Routine0 (objects/r7/amy_rose.asm:35-37): move.w #$A3CB,obj.sprite_tile; layer 1; move.l #AmyRoseSprites,obj.sprite_data'),
    ('amyAnimations', '117C 003C 003F 5428 0024 43F9 AAAAAAAA', 'optional',
     'AmyRoseObject_0_Routine2 loc_20F01A (objects/r7/amy_rose.asm:102-104): move.b #$3C,obj.var_3f; addq.b #2,obj.routine; lea AmyRoseAnims,a1'),
    ('amyPaletteSource', '0C28 0033 0000 67?? 47F9 AAAAAAAA', 'optional',
     'AmyRoseObject (objects/r7/amy_rose.asm:10-12): cmpi.b #$33,obj.id(a0); beq.s; lea StagePalette,a3'),
]
