"""Palmtree Panic object anchors for tools/soniccd/object_catalogue.py (owner: ppz).

Owned by the Palmtree Panic object lanes; writes src/main/resources/soniccd/object-catalogue-ppz.tsv,
read by objects/ppz/PpzObjectCatalogue. Only Palmtree Panic stages (FID_R11A..FID_R13D,
System.inc:12-25) are searched; each table is linked at most once.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R1")


ANCHORS = [
    ('plantMappings', '217C AAAAAAAA 0004 317C 4424 0002', 'optional',
     'Obj3DPlant_Init (3D Ramp/Main.asm:24-27): move.l #MapSpr_3DPlant,oMap(a0); move.w #$4424,oTile(a0)'),
    ('rampMappings', '217C AAAAAAAA 0004 317C 0441 0002', 'optional',
     'Obj3DRamp_Init (3D Ramp/Main.asm:212-217): move.l #MapSpr_3DRamp,oMap(a0); move.w #$441,oTile(a0)'),
    ('rampAnimations', '5228 ???? 43F9 AAAAAAAA 4EB9 ???????? 60??', 'optional',
     'Obj3DRamp_Main (3D Ramp/Main.asm:229-239): addq.b #1,oAnim(a0); lea Ani_3DRamp,a1; jsr R43LegacyAnimateObject'),
    ('sceneryMappings', '217C AAAAAAAA 0004 1168 ???? ???? 117C 0010 ???? 117C 0018', 'optional',
     'ObjScenery_Init (Scenery/Main.asm:22-28): move.l #MapSpr_Scenery,oMap(a0); oWidth $10; oYRadius $18'),
    ('logShadowMappings', '217C AAAAAAAA 0004 1168 ???? ???? 117C 0010 ???? 117C 0010', 'optional',
     'ObjLogShadow_Init (Log Shadow/Main.asm:22-29): move.l #MapSpr_LogShadow,oMap(a0); oWidth $10; oYRadius $10'),
    ('waterfallMappings', '217C AAAAAAAA 0004 117C 0004 ???? 117C 0001 ???? 117C 0010 ???? 317C 03BA', 'optional',
     'ObjWaterfall_Init (Effects/Main.asm:22-28): move.l #MapSpr_Waterfall,oMap(a0); move.w #$3BA,oTile(a0)'),
    ('waterfallAnimations', '7000 1028 0024 303B 00?? 4EBB 00?? 43F9 AAAAAAAA 4EB9 ???????? 4EF9', 'optional',
     'ObjWaterfall (Effects/Main.asm:7-14): lea Ani_Waterfall,a1; jsr AnimateObject; jmp DrawObject'),
    ('mosquiMappings1', '43FA PPPP 45FA ???? 203C FFFF 0000 60??', 'optional',
     'ObjMosqui_Init (Mosqui/Main.asm:26-42): lea MapSpr_Mosqui1(pc),a1; move.l #-$10000,d0'),
    ('mosquiMappings2', '43FA PPPP 45FA ???? 203C FFFF 8000', 'optional',
     'ObjMosqui_Init (Mosqui/Main.asm:46-49) .Damaged: lea MapSpr_Mosqui2(pc),a1; move.l #-$8000,d0'),
    ('mosquiAnimations1', '43FA ???? 45FA PPPP 203C FFFF 0000 60??', 'optional',
     'ObjMosqui_Init (Mosqui/Main.asm:26-42): lea Ani_Mosqui1(pc),a2'),
    ('mosquiAnimations2', '43FA ???? 45FA PPPP 203C FFFF 8000', 'optional',
     'ObjMosqui_Init (Mosqui/Main.asm:46-49) .Damaged: lea Ani_Mosqui2(pc),a2'),
    ('antonMappings', '217C AAAAAAAA 0004 117C 0018 ???? 117C 0018 ???? 117C 0013 ???? 117C 0029', 'optional',
     'ObjAnton_Init (Anton/Main.asm:24-31): move.l #MapSpr_Anton,oMap(a0); oXRadius $18; oWidth $18; oYRadius $13; oColType $29'),
    ('antonAnimations', 'D368 ???? 43FA PPPP 4EF9', 'optional',
     'ObjAnton_Main (Anton/Main.asm:68-86): add.w d1,oY(a0); lea Ani_Anton(pc),a1; jmp AnimateObject'),
    ('pataBataMappings1', '7600 43FA PPPP 60??', 'optional',
     'ObjPataBata (Pata-Bata/Main.asm:7-30): moveq #0,d3; lea MapSpr_PataBata1(pc),a1'),
    ('pataBataMappings2', '7601 43FA PPPP', 'optional',
     'ObjPataBata (Pata-Bata/Main.asm:34-39) .Damaged: moveq #1,d3; lea MapSpr_PataBata2(pc),a1'),
    ('pataBataAnimations', '2140 ???? 43FA PPPP 4EB9 ???????? 4EB9 ????????', 'optional',
     'ObjPataBata_Main (Pata-Bata/Main.asm:48-77): move.l d0,oY(a0); lea Ani_PataBata(pc),a1; jsr AnimateObject'),
    ('tamabbohMappings1', '43FA PPPP 45FA ???? 203C FFFF 6000 60??', 'optional',
     'ObjTamabboh_Init (Tamabboh/Main.asm:29-44): lea MapSpr_Tamabboh1(pc),a1; move.l #-$A000,d0'),
    ('tamabbohMappings2', '43FA PPPP 45FA ???? 203C FFFF B000', 'optional',
     'ObjTamabboh_Init (Tamabboh/Main.asm:49-52) .AltMaps: lea MapSpr_Tamabboh2(pc),a1; move.l #-$5000,d0'),
    ('tamabbohAnimations1', '43FA ???? 45FA PPPP 203C FFFF 6000 60??', 'optional',
     'ObjTamabboh_Init (Tamabboh/Main.asm:29-44): lea Ani_Tamabboh1(pc),a2'),
    ('tamabbohAnimations2', '43FA ???? 45FA PPPP 203C FFFF B000', 'optional',
     'ObjTamabboh_Init (Tamabboh/Main.asm:49-52) .AltMaps: lea Ani_Tamabboh2(pc),a2'),
    ('tamabbohMissileMappings', '217C AAAAAAAA 0004 217C 00000000 ????', 'optional',
     'ObjTamabbohMissile_Init (Tamabboh/Main.asm:226-234): move.l #MapSpr_TamabbohMissile,oMap(a0); move.l #0,oVar32(a0)'),
    ('tamabbohMissileAnimations', 'D1A8 ???? 43FA PPPP 4EF9', 'optional',
     'ObjTamabbohMissile_Main (Tamabboh/Main.asm:256-281): add.l d0,oVar2E(a0); lea Ani_TamabbohMissile(pc),a1'),
    ('tagaTagaMappings1', '43FA PPPP 45FA ???? 203C FFFC 4000 223C 00001000 60??', 'optional',
     'ObTagaTaga_Init (Taga-Taga/Main.asm:26-43): lea MapSpr_TagaTaga1(pc),a1; move.l #-$3C000,d0'),
    ('tagaTagaMappings2', '43FA PPPP 45FA ???? 203C FFFD 0000 223C 00001000', 'optional',
     'ObTagaTaga_Init (Taga-Taga/Main.asm:47-51) .Damaged: lea MapSpr_TagaTaga2(pc),a1; move.l #-$30000,d0'),
    ('tagaTagaAnimations1', '43FA ???? 45FA PPPP 203C FFFC 4000 223C 00001000 60??', 'optional',
     'ObTagaTaga_Init (Taga-Taga/Main.asm:26-43): lea Ani_TagaTaga1(pc),a2'),
    ('tagaTagaAnimations2', '43FA ???? 45FA PPPP 203C FFFD 0000 223C 00001000', 'optional',
     'ObTagaTaga_Init (Taga-Taga/Main.asm:47-51) .Damaged: lea Ani_TagaTaga2(pc),a2'),
    ('sectionRanges', '43FA PPPP 7000 7200 3038 ???? B059 65?? 5401 60??', 'optional',
     'LoadSectionArt (Section Art Load.asm:7-10): lea SectionRanges(pc),a1; the update and init PLC lists follow it (Section Data (Act 1 Present).asm:7-25)'),
    # --- ppz: objects B ---
    ('platformMappings', '317C 44BE 0002 117C 0002 0018 3168 0008 0038 3168 000C 003A 3168 000C 0036 203C AAAAAAAA', 'optional',
     'ObjPlatform_Init (Palmtree Panic/Objects/Platform/Main.asm:30-37): move.w #$44BE,oTile(a0); move.b #2,oPriority(a0); oX to oVar38, oY to oVar3A and oVar36; move.l #MapSpr_Platform,d0'),
    ('platformWidths', '117B 00QQ 0019 117C 0008 0016', 'optional',
     'ObjPlatform_Init (Main.asm:51-52): move.b ObjPlatform_Widths(pc,d0.w),oWidth(a0); move.b #8,oYRadius(a0)'),
    ('platformRanges', '117B 10QQ 002D', 'optional',
     'ObjPlatform_Init (Main.asm:55): move.b ObjPlatform_Ranges(pc,d1.w),oVar2D(a0)'),
    ('tunnelPathPaths', '45FA PPPP D4F2 0000 315A 003A', 'optional',
     'ObjTunnelPath_Init (Palmtree Panic/Objects/Tunnel Path/Main.asm:188-193): lea ObjTunnelPath_Paths(pc),a2; adda.w (a2,d0.w),a2; move.w (a2)+,oTunnelIndex(a0)'),
    ('tunnelPathSpeeds', 'D040 303B 00QQ B06E', 'optional',
     'ObjTunnelPath_SetPlayerGVel (Main.asm:381-383): add.w d0,d0; move.w ObjTunnelPath_GVels(pc,d0.w),d0; cmp.w oPlayerGVel(a6),d0'),
    ('collapseMappings1', '317C 44BE 0002 43FA PPPP 45FA ???? 1028 ???? 6A?? 43FA ???? 45FA ????', 'optional',
     'ObjCollapsePlatform_Init (Collapsing Platform/Main.asm:44-49): move.w #$44BE,oTile(a0); lea MapSpr_CollapsePlatform1(pc),a1'),
    ('collapseSizes1', '317C 44BE 0002 43FA ???? 45FA PPPP 1028 ???? 6A?? 43FA ???? 45FA ????', 'optional',
     'ObjCollapsePlatform_Init (Collapsing Platform/Main.asm:44-50): lea ObjCollapsePlatform_Sizes1(pc),a2'),
    ('collapseMappings2', '317C 44BE 0002 43FA ???? 45FA ???? 1028 ???? 6A?? 43FA PPPP 45FA ????', 'optional',
     'ObjCollapsePlatform_Init (Collapsing Platform/Main.asm:50-54): bpl.s .SetMaps; lea MapSpr_CollapsePlatform2(pc),a1'),
    ('collapseSizes2', '317C 44BE 0002 43FA ???? 45FA ???? 1028 ???? 6A?? 43FA ???? 45FA PPPP', 'optional',
     'ObjCollapsePlatform_Init (Collapsing Platform/Main.asm:50-54): lea ObjCollapsePlatform_Sizes2(pc),a2'),
    ('collapseMappings3', '337C 44BE 0002 237C AAAAAAAA 0004 237C 00020000 ????', 'optional',
     'ObjCollapsePlatform_BreakUp_MultiRow .Loop2 (Collapsing Platform/Main.asm:187-193): move.w #$44BE,oTile(a1); move.l #MapSpr_CollapsePlatform3,oMap(a1)'),
    ('collapseMappings4', '337C 44BE 0002 0029 0004 ???? 237C AAAAAAAA 0004 237C 00020000 ????', 'optional',
     'ObjCollapsePlatform_BreakUp_SingleRow .Loop3 (Collapsing Platform/Main.asm:270-276): move.l #MapSpr_CollapsePlatform4,oMap(a1)'),
    ('spinningDiscMappings', '217C AAAAAAAA 0004 7006 4EBA ????', 'optional',
     'ObjSpinningDisc_Init (Spinning Disc/Main.asm:39-44): move.l #MapSpr_SpinningDisc,oMap(a0); moveq #6,d0; jsr SetObjectTileID(pc)'),
    ('spinningDiscAnimations', '43F9 AAAAAAAA 6100 ???? 4EB9 ???????? 4EF9 ????????', 'optional',
     'ObjSpinningDisc (Spinning Disc/Main.asm:20-28): lea Ani_SpinningDisc,a1; bsr.w AnimateObject; jsr DrawObject; jmp CheckObjDespawn'),
    ('springBoardMappings', '217C AAAAAAAA 0004 0028 0004 ???? 117C 0003 ???? 117C 0010 ???? 117C 0018', 'optional',
     'ObjSpringBoard_Init (Springboard/Main.asm:129-137): move.l #MapSpr_SpringBoard,oMap(a0); ori.b #4,oSprFlags(a0); priority 3; oWidth $10; oXRadius $18'),
    ('springBoardAnimations', '43FA PPPP 4EB9 ???????? 4EF9 ???????? 43F8 ???? 7003', 'optional',
     'ObjSpringBoard_Draw (Springboard/Main.asm:167-171): lea Ani_SpringBoard(pc),a1; jsr AnimateObject; jmp DrawObject; ObjSpringBoard_FlipMain lea objPlayerSlot.w,a1; moveq #3,d0'),
    ('sonicHoleMappings', '217C AAAAAAAA 0004 117C 0010 ???? 117C 0010 ???? 117C 0006', 'optional',
     'ObjSonicHole_Init (Tunnel Path/Main.asm:33-36): move.l #MapSpr_SonicHole,oMap(a0); oWidth $10; oYRadius $10; oPriority 6'),
    # --- end ppz: objects B ---
    # --- ppz: routes A2 ---
    ('swingMappings', '217C AAAAAAAA 0004 7011', 'optional',
     'ObjSwing_Init (Palmtree Panic/Objects/Swing/Main.asm:85-90): move.l #MapSpr_Swing,oMap(a0); moveq #$11,d0; jsr SetObjectTileID'),
    ('swingMotion', '1028 ???? 0200 00F0 E208 43FA PPPP', 'optional',
     'ObjSwing_Init .SetMotion (Swing/Main.asm:130-138): andi.b #$F0,d0; lsr.b #1,d0; lea ObjSwing_MotionData(pc),a1'),
    # --- end ppz: routes A2 ---
]

# ObjBreakableWall is linked twice per stage file: the stage's own copy and a stale copy of Palmtree
# Panic act 1 Present's at a fixed offset, so one signature matches two places. The live copy is the
# routine ObjectIndex entry $36 starts (Palmtree Panic/Object Index (Act 1 Present).asm:60), so each
# operand is the match inside that routine (Breakable Wall/Main.asm:7-35, 56-76).
BREAKABLE_WALL_ID = 0x36
BREAKABLE_WALL_SIGNATURES = [
    ('breakableWallMappings', '117C 00EF ???? 317C 44BE 0002 217C AAAAAAAA 0004 1168'),
    ('breakableWallFrames', '4BFA PPPP 7000 1028 ???? E748 DAC0 49FA ???? 47FA ???? 7C05'),
    ('breakableWallDeltas', '4BFA ???? 7000 1028 ???? E748 DAC0 49FA PPPP 47FA ???? 7C05'),
    ('breakableWallSpeeds', '4BFA ???? 7000 1028 ???? E748 DAC0 49FA ???? 47FA PPPP 7C05'),
]
EXTRA_COLUMNS = [c for c, _ in BREAKABLE_WALL_SIGNATURES]
# Init, the main routine and the break-up loop all lie within this many bytes of the entry.
BREAKABLE_WALL_SPAN = 0x300


def extra_columns(stage: str, data: bytes) -> dict:
    from object_catalogue import operands, pattern
    from object_catalogue_common import OBJECT_INDEX
    from stage_catalogue import WORD_RAM
    from stage_inventory import require
    tables = operands(data, OBJECT_INDEX)
    require(len(tables) == 1, f"{stage} ObjectIndex: expected one operand, got {tables}")
    entry = int.from_bytes(data[tables[0] + 4 * (BREAKABLE_WALL_ID - 1):tables[0] + 4 * BREAKABLE_WALL_ID], "big")
    out = {column: None for column, _ in BREAKABLE_WALL_SIGNATURES}
    if not WORD_RAM <= entry < WORD_RAM + len(data):
        return out
    entry -= WORD_RAM
    for column, signature in BREAKABLE_WALL_SIGNATURES:
        hits = []
        for m in pattern(signature).finditer(data, entry, entry + BREAKABLE_WALL_SPAN):
            if m.groupdict().get("abs") is not None:
                hits.append((int.from_bytes(m.group("abs"), "big") & 0xFFFFFF) - WORD_RAM)
            else:
                hits.append(m.start("d16") + int.from_bytes(m.group("d16"), "big", signed=True))
        require(len(hits) <= 1, f"{stage} {column}: expected at most one match in the live routine, got {hits}")
        out[column] = hits[0] if hits else None
    return out
