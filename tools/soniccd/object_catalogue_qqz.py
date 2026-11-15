"""Quartz Quadrant object anchors for tools/soniccd/object_catalogue.py (owner: qqz).

Owned by the Quartz Quadrant object lane; writes src/main/resources/soniccd/object-catalogue-qqz.tsv,
read by objects/qqz/QqzObjectCatalogue. Only Quartz Quadrant stages (R51A..R53D) are searched; each
table is linked at most once per stage. Signatures are the instruction that loads the table (see the
core module for the token syntax); the routines are src/objects/r5/*.asm, assembled into every R5 stage.
"""


def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R5")


ANCHORS = [
    # --- Noro Noro (objects/r5/noro_noro.asm) ---
    ('noroMappings1', '217C FFFFC000 002C 5428 0024 43FA PPPP 4A28 0028', 'optional',
     'NoroNoroObject_0_Routine0 (objects/r5/noro_noro.asm:25-39): move.l #-$4000,obj.var_2c(a0); addq.b #2,obj.routine(a0); lea NoroNoroSprites1(pc),a1; tst.b obj.subtype(a0)'),
    ('noroMappings2', '4A28 0028 67?? 43FA PPPP 2149 0004', 'optional',
     'NoroNoroObject_0_Routine0 (objects/r5/noro_noro.asm:36-41): tst.b obj.subtype(a0); beq.s; lea NoroNoroSprites2(pc),a1; move.l a1,obj.sprite_data(a0)'),
    ('noroAnimations', '317C 0000 003E 43FA PPPP 4EF9 ???????? 44A8 002C', 'optional',
     'loc_20D0F0 (objects/r5/noro_noro.asm:96-98): move.w #0,obj.var_3e(a0); lea NoroNoroAnims(pc),a1; jmp AnimateObject; loc_20D0FA neg.l obj.var_2c(a0)'),
    ('noroSpikesMappings', '3368 0002 0002 237C AAAAAAAA 0004 137C 00B0 0020', 'optional',
     'NoroNoroObject_0_Routine4 (objects/r5/noro_noro.asm:78-81): move.w obj.sprite_tile(a0),obj.sprite_tile(a1); move.l #NoroSpikesSprites,obj.sprite_data(a1); move.b #$B0,obj.collide_type(a1)'),
    # --- Large break wall, break wall (objects/r5/large_break_wall.asm, objects/r5/break_wall.asm) ---
    ('largeBreakWallMappings', '317C 446B 0002 217C AAAAAAAA 0004', 'optional',
     'LargeBreakWallObject_0_Routine0 (objects/r5/large_break_wall.asm:27-28): move.w #$446B,obj.sprite_tile(a0); move.l #BigBreakWallSprites,obj.sprite_data(a0)'),
    ('breakWallMappings', '317C 44A7 0002 217C AAAAAAAA 0004', 'optional',
     'BreakWallObject_0_Routine0 (objects/r5/break_wall.asm:25-26): move.w #$44A7,obj.sprite_tile(a0); move.l #BreakWallSprites,obj.sprite_data(a0)'),
    # --- Kemusi (objects/r5/kemusi.asm) ---
    ('kemusiMappings', '337C 23FE 0002 237C AAAAAAAA 0004', 'optional',
     'sub_20D710 (objects/r5/kemusi.asm:121-122): move.w #$23FE,obj.sprite_tile(a1); move.l #KemusiSprites,obj.sprite_data(a1)'),
    # --- Sasori (objects/r5/sasori.asm) ---
    ('sasoriMappings1', '43FA PPPP 203C FFFF0000 323C 0003 4A28 0028', 'optional',
     'SasoriObject_0_Routine0 (objects/r5/sasori.asm:56-60): lea SasoriSprites1(pc),a1; move.l #-$10000,d0; move.w #3,d1; tst.b obj.subtype(a0)'),
    ('sasoriMappings2', '4A28 0028 67?? 43FA PPPP 203C FFFF8000 323C 0007', 'optional',
     'SasoriObject_0_Routine0 (objects/r5/sasori.asm:60-65): tst.b obj.subtype(a0); beq.s; lea SasoriSprites2(pc),a1; move.l #-$8000,d0; move.w #7,d1'),
    ('sasoriAnimations', '4EBB 00?? 43FA PPPP 4EB9 ???????? 4EB9 ???????? 3028 002A 4EF9', 'optional',
     'SasoriObject loc_20D212 (objects/r5/sasori.asm:14-22): jsr off_20D240(pc,d0.w); lea SasoriAnims(pc),a1; jsr AnimateObject; jsr DrawObject; move.w obj.var_2a(a0),d0; jmp CheckObjectDespawn2'),
    ('sasoriGunMappings', '137C 0010 0016 237C AAAAAAAA 0004 1368 0028 001A', 'optional',
     'SasoriObject_0_Routine2 (objects/r5/sasori.asm:96-99): move.b #$10,obj.height(a1); move.l #SasoriGunSprites,obj.sprite_data(a1); move.b obj.subtype(a0),obj.sprite_frame(a1)'),
    ('sasoriMissileMappings', '137C 00B2 0020 237C AAAAAAAA 0004', 'optional',
     'SasoriObject_0_RoutineC (objects/r5/sasori.asm:203-205): move.b #$B2,obj.collide_type(a1); move.l #SasoriMissileSprites,obj.sprite_data(a1)'),
    ('sasoriMissileAnimations', 'D1A8 0008 43FA PPPP 4EB9 ???????? 4EB9 ???????? 4EF9', 'optional',
     'loc_20D502 (objects/r5/sasori.asm:287-293): add.l d0,obj.x(a0); lea SasoriMissileAnims(pc),a1; jsr AnimateObject; jsr DrawObject; jmp CheckObjectDespawn'),
    # --- Kumo Kumo (objects/r5/kumo_kumo.asm) ---
    ('kumoMappings1', '43FA PPPP 45FA ???? 203C FFF80000', 'optional',
     'KumoKumoObject_0_Routine0 (objects/r5/kumo_kumo.asm:318-321): lea KumoKumoSprites1(pc),a1; lea KumoKumoAnims1(pc),a2; move.l #-$80000,d0'),
    ('kumoAnimations1', '43FA ???? 45FA PPPP 203C FFF80000', 'optional',
     'KumoKumoObject_0_Routine0 (objects/r5/kumo_kumo.asm:318-321): lea KumoKumoAnims1(pc),a2'),
    ('kumoMappings2', '43FA PPPP 45FA ???? 203C FFFB0000', 'optional',
     'KumoKumoObject_0_Routine0 (objects/r5/kumo_kumo.asm:322-325): lea KumoKumoSprites2(pc),a1; lea KumoKumoAnims2(pc),a2; move.l #-$50000,d0'),
    ('kumoAnimations2', '43FA ???? 45FA PPPP 203C FFFB0000', 'optional',
     'KumoKumoObject_0_Routine0 (objects/r5/kumo_kumo.asm:322-325): lea KumoKumoAnims2(pc),a2'),
    ('kumoWebMappings', '137C 00F7 0020 237C AAAAAAAA 0004', 'optional',
     'KumoKumoObject_0_RoutineC (objects/r5/kumo_kumo.asm:203-204): move.b #$F7,obj.collide_type(a1); move.l #KumoWebSprites,obj.sprite_data(a1)'),
    ('kumoWebAnimations', '4269 0010 4269 0014 43FA PPPP 4EB9', 'optional',
     'loc_20DA98 (objects/r5/kumo_kumo.asm:269-276): clr.w obj.x_speed(a1); clr.w obj.ground_speed(a1); lea KumoWebAnims(pc),a1; jsr AnimateObject'),
    # --- Stalactite, conveyor switch, platforms, bridge ---
    ('stalactiteMappings', '217C AAAAAAAA 0004 303C 0342', 'optional',
     'StalactiteObject_0_Routine0 (objects/r5/stalactite.asm:25-26): move.l #StalactiteSprites,obj.sprite_data(a0); move.w #$342,d0'),
    ('conveyorSwitchMappings', '317C 4797 0002 217C AAAAAAAA 0004', 'optional',
     'ConveyorSwitchObject_0_Routine0 (objects/r5/conveyor_switch.asm:24-25): move.w #$4797,obj.sprite_tile(a0); move.l #ConveyorSwitchSprites,obj.sprite_data(a0)'),
    ('sinePlatformMappings', '337C 4487 0002 237C AAAAAAAA 0004', 'optional',
     'SinePlatformInit .InitFirstPlatform (objects/r5/sine_platform.asm:76-77): move.w #$4487,obj.sprite_tile(a1); move.l #SinePlatformSprites,obj.sprite_data(a1)'),
    ('circlePlatformMappings', '137C 0008 0016 337C 4300 0002 237C AAAAAAAA 0004', 'optional',
     'CirclePlatformInit .Initialize (objects/r5/circle_platform.asm:60-63): move.b #8,obj.height(a1); move.w #$4300,obj.sprite_tile(a1); move.l #CirclePlatformSprites,obj.sprite_data(a1)'),
    ('bridgeMappings', '117C 0003 0018 317C 4300 0002 217C AAAAAAAA 0004', 'optional',
     'BridgeInit (objects/r5/bridge.asm:35-37): move.b #3,obj.sprite_layer(a0); move.w #$4300,obj.sprite_tile(a0); move.l #BridgeSprites,obj.sprite_data(a0)'),
    ('bridgeBendYOffsets', '3800 49F9 AAAAAAAA 7008 E948', 'optional',
     'BridgeBend (objects/r5/bridge.asm:159-165): move.w d0,d4; lea BridgeBendYOffsets,a4; moveq #8,d0; lsl.w #4,d0'),
    ('bridgeBendStrengths', '7A00 4BF9 AAAAAAAA 1A35 3000', 'optional',
     'BridgeBend (objects/r5/bridge.asm:170-174): moveq #0,d5; lea BridgeBendStrengths,a5; move.b (a5,d3.w),d5'),
    ('swingMappings', '117C 0008 0016 117C 0003 0018 317C 0358 0002 217C AAAAAAAA 0004', 'optional',
     'SwingInit (objects/r5/swing.asm:28-37): move.b #8,obj.height(a0); move.b #3,obj.sprite_layer(a0); move.w #$358,obj.sprite_tile(a0); move.l #SwingSprites,obj.sprite_data(a0)'),
    ('swingProperties', '43FA PPPP 1028 0028 0240 FFFE D040 D2C0', 'optional',
     'SwingInit (objects/r5/swing.asm:38-43): lea SwingProperties(pc),a1; move.b obj.subtype(a0),d0; andi.w #$FFFE,d0; add.w d0,d0; adda.w d0,a1'),
    # --- Section art (Section Art Load): the init PLC per camera section, as Palmtree Panic ---
    ('sectionRanges', '43FA PPPP 7000 7200 3038 ???? B059 65?? 5401 60??', 'optional',
     'InitSectionGfx (r5/section_gfx_1a.asm:5-21): lea SectionGfxRanges(pc),a1; moveq #0,d0; moveq #0,d1; move.w scroll_fg_x,d0; cmp.w (a1)+,d0 (the update and init PLC lists follow the ranges)'),
]
