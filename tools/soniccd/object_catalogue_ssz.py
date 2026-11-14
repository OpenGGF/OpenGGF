"""Stardust Speedway object anchors for tools/soniccd/object_catalogue.py (owner: ssz).

Owned by the Stardust Speedway object lane; writes src/main/resources/soniccd/object-catalogue-ssz.tsv,
read by objects/ssz/SszObjectCatalogue. Only Stardust Speedway stages (FID_R71A..FID_R73D) are searched;
each table is located through the instruction that loads it, as the core module describes. Signatures
were checked against all ten stage files (one operand per stage, or none where the stage does not link
the routine). Origin: lane O-SSZ of the 2026-11-12 Sonic CD re-host (scddisasm pin 1ecc5f3af).
"""


def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R7")



ANCHORS = [
    ('meshMappings', '317C 42F8 0002 217C AAAAAAAA 0004', 'optional',
     'MeshObject_0_Routine0 (objects/r7/mesh.asm:19-23): move.w #$42F8,obj.sprite_tile(a0); move.l #MeshSprites,obj.sprite_data(a0)'),
    ('sparksMappings', '317C 8338 0002 217C AAAAAAAA 0004', 'optional',
     'SparksObject_0_Routine0 (objects/r7/sparks.asm:19-23): move.w #$8338,obj.sprite_tile(a0); move.l #SparksSprites,obj.sprite_data(a0)'),
    ('sparksAnimations', '317C 8338 0002 217C ???????? 0004 43F9 AAAAAAAA 4EF9', 'optional',
     'SparksObject_0_Routine2 (objects/r7/sparks.asm:25-27): lea SparksAnims,a1; jmp AnimateObject'),
    ('boosterMappings', '317C 0300 0002 217C AAAAAAAA 0004 117C 0020', 'optional',
     'BoosterObject_0_Routine0 (objects/r7/launch.asm:948-953): move.w #$300,obj.sprite_tile(a0); move.l #LaunchSprites,obj.sprite_data(a0); move.b #$20,obj.width_2(a0)'),
    ('launchAnimations', '4E75 43F9 AAAAAAAA 4EF9 ???????? 4A28', 'optional',
     'SpringLauncherObject_0_Routine2 .animate (objects/r7/launch.asm:165-171): lea LaunchAnims,a1; jmp AnimateObject; Routine4 tst.b obj.subtype(a0)'),
    ('pathSwapMappings', '217C AAAAAAAA 0004 117C 0018 ???? 117C 0018 ???? 117C 0018', 'optional',
     'PathSwapObject_0_Routine0 (objects/r7/path_swap.asm:36-39): move.l #PathSwapSprites,obj.sprite_data(a0); move.b #$18,obj.width/width_2/height(a0)'),
    ('pathSwapAnimations', '43F9 AAAAAAAA 4EF9 ???????? 0068 8000 0002', 'optional',
     'PathSwapObject_0_Routine4 (objects/r7/path_swap.asm:106-113): lea PathSwapAnims,a1; jmp AnimateObject; Routine6 ori.w #$8000,obj.sprite_tile(a0)'),
    ('lampMappings', '117C 0000 ???? 317C 451C 0002 217C AAAAAAAA 0004', 'optional',
     'LampObject_0_Routine0 (objects/r7/lamp.asm:19-23): move.b #0,obj.sprite_layer(a0); move.w #$451C,obj.sprite_tile(a0); move.l #LampSprites,obj.sprite_data(a0)'),
    ('searchlightMappings', '217C AAAAAAAA 0004 117C 007F', 'optional',
     'SearchlightObject_0_Routine0 (objects/r7/searchlight.asm:19-24): move.l #SearchlightSprites,obj.sprite_data(a0); move.b #$7F,obj.width_2(a0)'),
    ('lightEdgeMappings', '317C 82FC 0002 217C AAAAAAAA 0004', 'optional',
     'LightEdgeObject sub_206604 (objects/r7/light_edge.asm:18-22): move.w #$82FC,obj.sprite_tile(a0); move.l #LightEdgeSprites,obj.sprite_data(a0)'),
    ('lightEdgeAnimations', 'D168 0008 43F9 AAAAAAAA 4EF9', 'optional',
     'LightEdgeObject sub_20661C animation (objects/r7/light_edge.asm:60-63): add.w d0,obj.x(a0); lea LightEdgeAnims,a1; jmp AnimateObject'),
    ('spikeLaunchMappings', '317C 038E 0002 217C AAAAAAAA 0004', 'optional',
     'JumpSpikeBallObject (objects/r7/jump_spike_ball.asm:11-12): move.w #$38E,obj.sprite_tile(a0); move.l #SpikeLaunchSprites,obj.sprite_data(a0)'),
    ('jumpSpikeBallMappings', '317C 0386 0002 217C AAAAAAAA 0004 317C 0020 002E', 'optional',
     'JumpSpikeBallObject_0_Routine0 (objects/r7/jump_spike_ball.asm:62-64): move.w #$386,obj.sprite_tile(a0); move.l #JumpSpikeBallSprites,obj.sprite_data(a0); move.w #$20,obj.var_2e(a0)'),
    ('kanabunMappings1', '43FA PPPP 45FA ???? 303C FE00 223C FFFFC000', 'optional',
     'KanabunObject (objects/r7/kanabun.asm:17-20): lea KanabunSprites1(pc),a1; lea KanabunAnims1(pc),a2; move.w #-$200,d0; move.l #-$4000,d1'),
    ('kanabunAnimations1', '43FA ???? 45FA PPPP 303C FE00 223C FFFFC000', 'optional',
     'KanabunObject (objects/r7/kanabun.asm:17-20): lea KanabunAnims1(pc),a2'),
    ('kanabunMappings2', '43FA PPPP 45FA ???? 303C FF00 223C FFFFC000', 'optional',
     'KanabunObject (objects/r7/kanabun.asm:23-26): lea KanabunSprites2(pc),a1; lea KanabunAnims2(pc),a2; move.w #-$100,d0'),
    ('kanabunAnimations2', '43FA ???? 45FA PPPP 303C FF00 223C FFFFC000', 'optional',
     'KanabunObject (objects/r7/kanabun.asm:23-26): lea KanabunAnims2(pc),a2'),
    ('kabasiraMappings', '337C 23C6 0002 237C AAAAAAAA 0004', 'optional',
     'sub_20D532 (objects/r7/kabasira.asm:103-112): move.w #$23C6,obj.sprite_tile(a1); move.l #KabasiraSprites,obj.sprite_data(a1)'),
    ('kabasiraAnimations', '43FA PPPP 4EB9 ???????? 4EB9 ???????? 4EF9', 'optional',
     'KabasiraObject (objects/r7/kabasira.asm:30-33): lea KabasiraAnims(pc),a1; jsr AnimateObject; jsr DrawObject; jmp CheckObjectDespawn'),
    ('hotaruMappings1', '43FA PPPP 45FA ???? 203C FFFF0000', 'optional',
     'HotaruObject_0_Routine0 (objects/r7/hotaru.asm:188-191): lea HotaruSprites1(pc),a1; lea HotaruAnims1(pc),a2; move.l #-$10000,d0'),
    ('hotaruAnimations1', '43FA ???? 45FA PPPP 203C FFFF0000', 'optional',
     'HotaruObject_0_Routine0 (objects/r7/hotaru.asm:188-191): lea HotaruAnims1(pc),a2'),
    ('hotaruMappings2', '43FA PPPP 45FA ???? 203C FFFF8000', 'optional',
     'HotaruObject_0_Routine0 (objects/r7/hotaru.asm:196-199): lea HotaruSprites2(pc),a1; lea HotaruAnims2(pc),a2; move.l #-$8000,d0'),
    ('hotaruAnimations2', '43FA ???? 45FA PPPP 203C FFFF8000', 'optional',
     'HotaruObject_0_Routine0 (objects/r7/hotaru.asm:196-199): lea HotaruAnims2(pc),a2'),
    ('sectionRanges', '43FA PPPP 7000 7200 3038 ???? B059 65?? 5401 60??', 'optional',
     'InitSectionGfx (r7/section_gfx_1a.asm:3-11): lea SectionGfxRanges(pc),a1; the update and init PLC lists follow it (:48-58)'),
]
