"""Metallic Madness boss anchors for tools/soniccd/object_catalogue.py (owner: bossmmz).

Owned by the Metallic Madness boss lane; writes src/main/resources/soniccd/object-catalogue-bossmmz.tsv, read by
objects/boss/mmz/MmzBossCatalogue. Only the act 3 stages (R83C, R83D) link the boss: R8 Shared.asm includes
objects/r8/boss.asm, amy_rose.asm, boss_door.asm and hotaru.asm for R8_VARIANT 9 and 10 only (Level/Metallic
Madness/R8 Shared.asm:239-268). Each table is found through the instruction that consumes it; nothing is transcribed.
Object offsets: oTile 2, oMap 4, oPriority $18, oWidth $19, oYRadius $16, oAnim $1C, oRoutine $24, oSubtype $28.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R83")


ANCHORS = [
    # ---- mappings -------------------------------------------------------------------------------------------
    ('eggmanMappings', '117C 0018 0019 117C 0010 0016 317C 03DE 0002 217C AAAAAAAA 0004', 'optional',
     'EggmanObject_0_Routine0 (objects/r8/boss.asm:265-272): width $18, height $10, tile $3DE, EggmanSprites'),
    ('eggMobileMappings', '117C 0024 0019 117C 0024 0016 317C 23DE 0002 217C AAAAAAAA 0004', 'optional',
     'EggMobileObject_0_Routine0 (objects/r8/boss.asm:1952-1959): width $24, height $24, tile $23DE, EggMobileSprites'),
    ('panelMappings1', '117C 0020 0019 117C 0020 0016 317C 2220 0002 217C AAAAAAAA 0004', 'optional',
     'BossPanelObject_0_Routine0 (objects/r8/boss.asm:2337-2344): width $20, height $20, tile $2220, BossPanelSprites1'),
    ('panelMappings2', '0C2B 0001 003A 66?? 217C AAAAAAAA 0004', 'optional',
     'BossPanelObject_0_Routine6 (objects/r8/boss.asm:2536-2539): cmpi.b #1,obj.var_3a(a3); bne.s; move.l #BossPanelSprites2'),
    ('panelMappings3', '0CA8 ???????? 0004 67?? 217C AAAAAAAA 0004 60??', 'optional',
     'BossPanelObject_0_RoutineA (objects/r8/boss.asm:2822-2825): beq.s loc_20CB40; move.l #BossPanelSprites3; bra.s'),
    ('panelMappings4', '4228 002B 5228 002D 0CA8 AAAAAAAA 0004 67??', 'optional',
     'BossPanelObject_0_RoutineA (objects/r8/boss.asm:2822-2823): clr.b var_2b; addq.b #1,var_2d; cmpi.l #BossPanelSprites4,obj.sprite_data(a0); beq.s'),
    ('panelMappings5', '217C AAAAAAAA 0004 2028 0034 D1A8 0008', 'optional',
     'loc_20CB40 (objects/r8/boss.asm:2829-2834): move.l #BossPanelSprites5; move.l obj.var_34(a0),d0; add.l d0,obj.x(a0)'),
    ('panelMappings6', '117C 0003 0018 5228 002D 4228 002A 217C AAAAAAAA 0004', 'optional',
     'loc_20C8DC (objects/r8/boss.asm:2583-2587): sprite_layer 3; addq.b #1,var_2d; clr.b var_2a; move.l #BossPanelSprites6'),
    ('panelSpriteSets', '4BF9 AAAAAAAA 3035 0000', 'optional',
     'loc_20C75E (objects/r8/boss.asm:2389-2396): lea BossPanelSprites,a5; move.w (a5,d0.w),d0'),
    ('sparksMappings', '117C 0008 0019 117C 0008 0016 317C 03DE 0002 217C AAAAAAAA 0004', 'optional',
     'SparksObject_0_Routine0 (objects/r8/boss.asm:3020-3027): width 8, height 8, tile $3DE, SparksSprites'),
    ('amyMappings', '117C 0010 0019 117C 0014 0016 317C 2220 0002 217C AAAAAAAA 0004', 'optional',
     'AmyRoseObject_1_Routine0 (objects/r8/amy_rose.asm:47-53): width $10, height $14, tile $2220, AmyRoseSprites'),
    ('bossDoorMappings', '237C AAAAAAAA 0004 337C 8797 0002', 'optional',
     'sub_20B728 (objects/r8/boss_door.asm:28-47): move.l #BossDoorSprites,obj.sprite_data(a1); move.w #$8797,obj.sprite_tile(a1)'),
    ('hotaruMappings', '317C 23B0 0002 217C AAAAAAAA 0004', 'optional',
     'sub_20F364 (objects/r8/hotaru.asm:30-37): move.w #$23B0,obj.sprite_tile(a0); move.l #HotaruSprites,obj.sprite_data(a0)'),
    # ---- animations -----------------------------------------------------------------------------------------
    ('eggmanAnimations', '7000 1028 0024 303B 00?? 4EBB 00?? 43F9 AAAAAAAA 4EB9 ???????? 2648 D6FC 003D', 'optional',
     'EggmanObject (objects/r8/boss.asm:28-35): the routine dispatch, lea EggmanAnims,a1; jsr AnimateObject; movea.l a0,a3; adda.w #obj.var_3d,a3'),
    ('eggmanDispatch', '7000 1028 0024 303B 00QQ 4EBB 00?? 43F9 ???????? 4EB9 ???????? 2648 D6FC 003D', 'optional',
     'EggmanObject (objects/r8/boss.asm:28-35, 51-79): move.w off_20B266(pc,d0.w),d0 before lea EggmanAnims (the routine jump table)'),
    ('amyAnimations', '43F9 AAAAAAAA 4EB9 ???????? 6100 ???? 0C68 0EA0 0008', 'optional',
     'AmyRoseObject_1_Routine4 (objects/r8/amy_rose.asm:72-80): lea AmyRoseAnims,a1; jsr AnimateObject; bsr.w sub_20D14E; cmpi.w #$EA0,obj.x(a0)'),
    ('hotaruAnimations', '117C 0004 001C 43FA PPPP', 'optional',
     'HotaruObject_2_Routine2 (objects/r8/hotaru.asm:52-54): move.b #4,obj.anim_id(a0); lea HotaruAnims(pc),a1'),
    # ---- Eggman's data tables -------------------------------------------------------------------------------
    ('eggmanScripts', '303B 00QQ D041 47FB', 'optional',
     'sub_20BFF6 (objects/r8/boss.asm:1494-1504): move.w off_20C030(pc,d0.w),d0; add.w d1,d0; lea off_20C030(pc,d0.w),a3'),
    ('eggmanSpeeds', 'C0FC 000A 47F9 AAAAAAAA', 'optional',
     'sub_20BFA4 (objects/r8/boss.asm:1457-1462): mulu.w #$A,d0; lea dword_20BFC4,a3'),
    ('eggmanSwings', '47F9 AAAAAAAA 284B D8C0', 'optional',
     'loc_20B9FC (objects/r8/boss.asm:868-876): lea off_20BAEA,a3; movea.l a3,a4; adda.w d0,a4'),
    ('panelRemap', '7000 102B 003A D040 303B 00QQ', 'optional',
     'loc_20C7D2 (objects/r8/boss.asm:2435-2442): moveq #0,d0; move.b obj.var_3a(a3),d0; add.w d0,d0; move.w off_20C810(pc,d0.w),d0'),
    ('panelSettleFrames', '7000 102B 003A D000 303B 00QQ', 'optional',
     'loc_20CA86 (objects/r8/boss.asm:2741-2749): moveq #0,d0; move.b $3A(a3),d0; add.b d0,d0; move.w off_20CAC4(pc,d0.w),d0'),
    ('panelOffsets', '102A 0034 D040 303B 00QQ', 'optional',
     'sub_20CBEA (objects/r8/boss.asm:2909-2915): move.b obj.var_34(a2),d0; add.w d0,d0; move.w off_20CC38(pc,d0.w),d0'),
    ('panelDropXs', '102A 003F 49FB 00QQ', 'optional',
     'loc_20C9AA (objects/r8/boss.asm:2637-2640): move.b obj.var_3f(a2),d0; lea word_20C9D6(pc,d0.w),a4'),
    ('sparksOffsets', '1028 0034 D040 303B 00QQ 47FB', 'optional',
     'SparksObject_0_Routine2 (objects/r8/boss.asm:3033-3037): move.b obj.var_34(a0),d0; add.w d0,d0; move.w off_20CEE4(pc,d0.w),d0'),
    ('bossPaletteCycle', '47FB 00QQ 49F8 FB30', 'optional',
     'CycleBossPalette (r8/palette_cycle_c.asm:109-125, palette_cycle_d.asm:251-267): lea word_20027E(pc,d0.w),a3; lea (palette+$30).w,a4'),
    # ---- the Hotaru intro (hotaru.asm) ----------------------------------------------------------------------
    ('hotaruFadePalettes', '43F9 AAAAAAAA 43F1 0000', 'optional',
     'sub_20F81E (objects/r8/hotaru.asm:663-665): lea word_20F894,a1; lea (a1,d0.w),a1'),
    ('hotaruFadeIn', '47F9 AAAAAAAA 4EBA PPPP 0C28 0010 003A 66', 'optional',
     'HotaruObject_0_Routine4 (objects/r8/hotaru.asm:470-473): lea byte_20F874,a3; jsr sub_20F83C(pc); cmpi.b #$10,obj.var_3a(a0); bne.s'),
    ('hotaruFadeOut', '47F9 AAAAAAAA 4EBA PPPP 0C28 0010 003A 67', 'optional',
     'HotaruObject_0_RoutineC (objects/r8/hotaru.asm:557-561, good future): lea byte_20F884,a3; jsr sub_20F83C(pc); cmpi.b #$10; beq.s'),
    ('hotaruFlashScript', '103B 00QQ 5228 003C', 'optional',
     'HotaruObject_2_Routine8 (objects/r8/hotaru.asm:202-206): move.b byte_20F4F0(pc,d0.w),d0; addq.b #1,obj.var_3c(a0)'),
]
