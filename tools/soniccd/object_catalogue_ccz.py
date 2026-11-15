"""Collision Chaos object anchors for tools/soniccd/object_catalogue.py (owner: ccz).

Owned by the Collision Chaos object lane; writes src/main/resources/soniccd/object-catalogue-ccz.tsv,
read by objects/ccz/CczObjectCatalogue. Only the Collision Chaos stages (FID_R31A..FID_R33D) are searched.
The USA build assembles every Collision Chaos stage with the recovered "legacy" object layout
(Level/Collision Chaos/R3 Legacy Act 1 Shared.asm:59-77 and its siblings: every CC_LEGACY_*_ABI is 1), and
Act 1 Present assembles its intro objects from objects/r3/intro.asm, so signatures follow the recovered code.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R3")


ANCHORS = [
    # ---- Act 1 Present intro (objects/r3/intro.asm) ----
    ('introSpikesMappings', '317C 031E 0002 117C 0003 ???? 217C AAAAAAAA 0004', 'optional',
     'IntroSpikesInit (objects/r3/intro.asm:595-605): move.w #$31E,tile; move.b #3,layer; move.l #IntroSpikesSprites'),
    ('metalSonicMappings', '317C 03D0 0002 117C 0002 ???? 217C AAAAAAAA 0004', 'optional',
     'MetalSonicInit (objects/r3/intro.asm:35-42): move.w #$3D0,tile; move.b #2,layer; move.l #MetalSonicSprites'),
    ('metalSonicAnimations', '117C 0050 0030 43FA PPPP 6000', 'optional',
     'MetalSonicHover (objects/r3/intro.asm:101-117): move.b #$50,var_30; lea MetalSonicAnims(pc),a1; bra.w IntroAnimateObject'),
    ('metalSonicExhaustAnimations', '3140 ???? 43FA PPPP 4EF9', 'optional',
     'MetalSonicExhaustMain (objects/r3/intro.asm:849-870): move.w d0,obj.y(a0); lea MetalSonicExhaustAnims(pc),a1; jmp AnimateObject'),
    # ---- Level/Collision Chaos/Objects ----
    ('doorMappings', '317C 0371 0002 117C 0003 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjDoor_Init (Door/Main.asm:54-60): move.w #$371,oTile; move.b #3,oPriority; move.l #MapSpr_Door,oMap'),
    ('metalPlatformMappings', '317C 034E 0002 117C 0003 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjMetalPlatform_Init (Metal Platform/Main.asm:55-61): move.w #$34E,oTile; move.b #3,oPriority; move.l #MapSpr_MetalPlatform'),
    ('drumPlatformMappings', '317C 036A 0002 117C 0003 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjDrumPlatform_Init (Drum Platform/Main.asm:121-126): move.w #$36A,oTile; move.b #3,oPriority; move.l #MapSpr_DrumPlatform'),
    ('fireShootMappings', '317C 0379 0002 117C 0002 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjFireShooter_Emitter_Init (Fire Shooter/Main.asm:100-107): move.w #$379,oTile; move.b #2,oPriority; move.l #MapSpr_FireShoot'),
    ('fireShootAnimations', '1142 ???? 5428 ???? 43FA PPPP 4EB9', 'optional',
     'ObjFireShooter_Projectile_Init (Fire Shooter/Main.asm:405-409): move.b d2,oAnim(a0); addq.b #2,oRoutine(a0); lea Ani_FireShoot(pc),a1'),
    ('bumperPlatformMappings', '217C AAAAAAAA 0004 317C 435E 0002', 'optional',
     'ObjBumperPlatform_Init (Bumper Platform/Main.asm:40-43): move.l #MapSpr_BumperPlatform,oMap; move.w #$435E,oTile'),
    ('glassBreakMappings', '3140 0002 217C AAAAAAAA 0004 700A', 'optional',
     'ObjGlassShatter_Init (Glass Shatter/Main.asm:40-46): move.w d0,oTile; move.l #MapSpr_GlassBreak,oMap; moveq #10,d0'),
    ('glassBreakAnimations', '6100 ???? 43FA PPPP 4EF9', 'optional',
     'ObjGlassShatter_Animate (Glass Shatter/Main.asm:49-51): bsr.w SpawnPoints; lea Ani_GlassBreak(pc),a1; jmp AnimateObject'),
    ('gaMappings2', '317C 003D ???? 43FA PPPP 4A28 ???? 6B ??', 'optional',
     'ObjGa_Init (Ga/Main.asm:46-52): move.w #$3D,oGaTimer(a0); lea GaSprites2(pc),a1; tst.b oSubtype(a0); bmi.s .SetMap'),
    ('gaMappings1', '317C 003D ???? 43FA ???? 4A28 ???? 6B ?? 43FA PPPP 50E8 003F', 'optional',
     'ObjGa_Init (Ga/Main.asm:50-55): lea GaSprites1(pc),a1; st oVar3F(a0)'),
    ('gaAnimations', '317C 0033 002A 43F9 AAAAAAAA 4EB9', 'optional',
     'ObjGa_Routine4 (Ga/Main.asm:77-83): move.w #$33,oGaTimer(a0); .Animate: lea Ani_Ga,a1; jsr AnimateObject'),
    ('pocketMappings1', '317C 03AF 0002 217C AAAAAAAA 0004', 'optional',
     'ObjPocket_Init (Pocket/Main.asm:63-69): move.w #$3AF,oTile; move.l #MapSpr_Pocket1,oMap'),
    ('pocketMappings2', '317C 43AF 0002 217C AAAAAAAA 0004', 'optional',
     'ObjPocket_Child_Init (Pocket/Main.asm:251-259): move.w #$43AF,oTile; move.l #MapSpr_Pocket2,oMap'),
    ('pocketAnimations', '4EF9 ???????? 43FA PPPP 4EB9', 'ambiguous',
     'ObjPocket_Child_FollowParent (Pocket/Main.asm:262-270): jmp DeleteObject; .Draw: lea Ani_Pocket(pc),a1; jsr AnimateObject'),
    ('tentouMappings2', '317C A46A 0002 43FA PPPP 4A28', 'optional',
     'ObjTentou_Init (Tentou/Main.asm:44-49): move.w #$A46A,oTile; lea MapSpr_Tentou2(pc),a1; tst.b oSubtype(a0)'),
    ('tentouMappings1', '317C A46A 0002 43FA ???? 4A28 ???? 6B ?? 43FA PPPP 50E8 003F', 'optional',
     'ObjTentou_Init (Tentou/Main.asm:47-52): lea MapSpr_Tentou1(pc),a1; st oVar3F(a0)'),
    ('tentouAnimations', '43FA PPPP 4EB9 ???????? 4EB9 ???????? 4EF9', 'ambiguous',
     'ObjTentou_Parent (Tentou/Main.asm:24-31): lea Ani_Tentou(pc),a1; jsr AnimateObject; jsr DrawObject; jmp CheckObjectDespawn'),
    ('tentouBombMappings', '317C 0495 0002 217C AAAAAAAA 0004', 'optional',
     'ObjTentou_Bomb_Init (Tentou/Main.asm:285-294): move.w #$495,oTile; move.l #MapSpr_TentouBomb,oMap'),
    ('tentouBombAnimations', '43FA PPPP 4EF9 ???????? 117C 0018 0000', 'optional',
     'ObjTentou_Bomb_Fuse (Tentou/Main.asm:320-326): lea Ani_TentouBomb(pc),a1; jmp AnimateObject; ObjTentou_Bomb_Explode: move.b #$18,oID(a0)'),
    ('kamaKamaMappings1', '223C AAAAAAAA 103C 0001 1168', 'optional',
     'ObjKamaKama_Init (KamaKama/Main.asm:41-50): move.l #MapSpr_KamaKama1,d1; move.b #1,d0'),
    ('kamaKamaMappings2', '223C AAAAAAAA 103C 0002 1140', 'optional',
     'ObjKamaKama_Init (KamaKama/Main.asm:45-48): move.l #MapSpr_KamaKama2,d1; move.b #2,d0; .SetSprite: move.b d0,oMapFrame(a0)'),
    ('kamaKamaAnimations', '4A41 6A ?? 5428 ???? 43FA PPPP 4EB9', 'optional',
     'ObjKamaKama_FallToFloor (KamaKama/Main.asm:66-71): tst.w d1; bpl.s; addq.b #2,oRoutine(a0); lea Ani_KamaKama(pc),a1'),
    ('kamaSickleMappings', '317C 23F2 0002 217C AAAAAAAA 0004', 'optional',
     'ObjKamaSickle_Init (KamaKama/Main.asm:247-254): move.w #$23F2,oTile; move.l #MapSpr_KamaSickle,oMap'),
    ('kamaSickleAnimations', '0C29 0024 0000 67 ?? 4EF9 ???????? 43FA PPPP 4EF9', 'optional',
     'ObjKamaSickle_WaitForLaunch (KamaKama/Main.asm:271-282): cmpi.b #$24,0(a1); beq.s .Animate; jmp DeleteObject; lea Ani_KamaSickle(pc),a1'),
    # ---- Act 3 drain (objects/r3/drain_*.asm) ----
    ('drainBlockMappings', '317C 043E 0002 217C AAAAAAAA 0004', 'optional',
     'DrainBlock_Init (objects/r3/drain_block.asm:35-42): move.w #$43E,sprite_tile; move.l #DrainBlockSprites,sprite_data'),
    ('drainLightMappings', '317C 643E 0002 217C AAAAAAAA 0004', 'optional',
     'DrainLight_Init (objects/r3/drain_light.asm:30-37): move.w #$643E,sprite_tile; move.l #DrainLightSprites,sprite_data'),
    ('drainSwitchMappings', '317C 443E 0002 217C AAAAAAAA 0004', 'optional',
     'DrainSwitch_Init (objects/r3/drain_switch.asm:30-38): move.w #$443E,sprite_tile; move.l #DrainSwitchSprites,sprite_data'),
    ('sectionRanges', '43FA PPPP 7000 7200 3038 ???? B059 65 ?? 5401 60 ??', 'optional',
     'LoadSectionArt (Section Art Load.asm:7-10): lea SectionRanges(pc),a1; the update and init PLC lists follow it (Section Data (Act 1 Present).asm:12-31)'),
    ('gaDustAnimations', '06A8 0001 0000 000C 43FA PPPP 4EF9', 'optional',
     'ObjGa_Dust_Main (Ga/Main.asm:180-187): addi.l #$10000,oY(a0); lea Ani_GaDust(pc),a1; jmp AnimateObject'),
    ('drainBlockAnimations', '317C 01FF 001C 5428 0024 43FA PPPP 4EF9', 'optional',
     'DrainBlock_Lower (objects/r3/drain_block.asm:93-102): move.w #$1FF,obj.anim_id; addq.b #2,obj.routine; lea DrainBlockAnims(pc),a1'),
    ('drainSwitchAnimations', '0668 FFFF 002A 43FA PPPP 4EF9', 'optional',
     'DrainSwitch_Main (objects/r3/drain_switch.asm:61-70): addi.w #-1,drain_switch.cooldown; lea DrainSwitchAnims(pc),a1; jmp AnimateObject'),
    # --- block, hidden block and spikes (Collision Chaos/Objects/Block/Main.asm, Spikes/Main.asm; the legacy ABI is what the USA build links) ---
    ('blockMappings', '317C 433E 0002 117C 0003 0018 217C AAAAAAAA 0004', 'optional',
     'ObjBlock_Init (Block/Main.asm:67-72): move.w #$433E,oTile(a0); move.b #3,oPriority(a0); move.l #MapSpr_Block,oMap(a0)'),
    ('blockMotionData', 'D000 D000 43FB 00QQ 1159 003A', 'optional',
     'ObjBlock_Update .CheckMotion (Block/Main.asm:116-129): add.b d0,d0 twice; lea ObjBlock_MotionData(pc,d0.w),a1; move.b (a1)+,oBlockTimer(a0)'),
    ('hiddenBlockMappings', '317C 039F 0002 117C 0003 0018 217C AAAAAAAA 0004', 'optional',
     'ObjHiddenBlock_Init (Block/Main.asm:255-259): move.w #$39F,oTile(a0); move.b #3,oPriority(a0); move.l #MapSpr_HiddenBlock,oMap(a0)'),
    ('spikesMappings', '303C 031E 0C39 0002 ???????? 66?? 303C 03FC 3140 0002 117C 0003 0018 217C AAAAAAAA 0004', 'optional',
     'ObjSpikes_Init (Spikes/Main.asm:70-78): move.w #$31E,d0; act 3 move.w #$3FC,d0; move.w d0,oTile(a0); move.b #3,oPriority(a0); move.l #MapSpr_Spikes,oMap(a0)'),
    ('spikesProperties', '43FB 00QQ 1159 0019 1159 0016 1159', 'optional',
     'ObjSpikes_Init (Spikes/Main.asm:88-91): lea ObjSpikes_Properties(pc,d0.w),a1; move.b (a1)+,oWidth(a0); move.b (a1)+,oYRadius(a0); move.b (a1)+,oColType(a0)'),
    # ---- Collision Chaos' own bumper and flipper (objects/r3/bumper_flipper.asm; Level/.../Bumper and Flipper/Main.asm) ----
    ('bumperMappings', '217C AAAAAAAA 0004 317C 04CE 0002 117C 0004', 'optional',
     'ObjBumper_Init (Bumper and Flipper/Main.asm:20-30): move.l #MapSpr_Bumper,oMap; move.w #$4CE,oTile; move.b #4,oSprFlags'),
    ('bumperAnimations', '43F9 AAAAAAAA 6100 ???? 4EB9 ???????? 3028 ???? 4EF9', 'optional',
     'ObjBumper_Main .Animate (Bumper and Flipper/Main.asm:109-114): lea BumperAnims,a1; bsr.w AnimateObject; jsr DrawObject; move.w oBumperBaseX,d0; jmp CheckObjDespawn2'),
    ('flipperMappings', '217C AAAAAAAA 0004 317C 049D 0002', 'optional',
     'ObjFlipper_Init (Bumper and Flipper/Main.asm:132-139): move.l #MapSpr_Flipper,oMap; move.w #$49D,oTile'),
    ('flipperAnimations', '43F9 AAAAAAAA 6100 ???? 4EB9 ???????? 4EF9', 'optional',
     'ObjFlipper_Main .Animate (Bumper and Flipper/Main.asm:145-150): lea FlipperAnims,a1; bsr.w AnimateObject; jsr DrawObject; jmp CheckObjDespawn'),
    ('flipperTopSlope', '47F9 AAAAAAAA 4A69 ???? 6A ?? 47F9', 'optional',
     'ObjFlipper_CheckPlayer .GetSlopeData (Bumper and Flipper/Main.asm:187-192): lea FlipperTopSlope,a3; tst.w oYVel(a1); bpl.s .GetColumn; lea FlipperBottomSlope,a3'),
    ('flipperBottomSlope', '47F9 ???????? 4A69 ???? 6A ?? 47F9 AAAAAAAA', 'optional',
     'ObjFlipper_CheckPlayer .GetSlopeData (Bumper and Flipper/Main.asm:187-192): lea FlipperBottomSlope,a3'),
    # ---- Amy Rose and her heart (objects/r3/intro.asm), Act 1 Present only ----
    ('amyRoseMappings', '317C 235E 0002 117C 0001 ???? 217C AAAAAAAA 0004', 'optional',
     'AmyRoseInit (objects/r3/intro.asm:271-296): move.w #$235E,sprite_tile; move.b #1,sprite_layer; move.l #AmyRoseSprites'),
    ('amyRoseAnimations', '3149 0034 43F9 AAAAAAAA 6100 ???? 6000', 'optional',
     'AmyRoseAnimateAndSpawnHeart (objects/r3/intro.asm:370-376): lea AmyRoseAnims,a1; bsr.w IntroAnimateObject; bra.w AmyRoseSpawnHeart'),
    # ---- Collision Chaos animal (Animal/Main.asm) ----
    ('amyRosePalette', '47FA PPPP 49F8 ???? 4CDB 000F', 'optional',
     'AmyRoseLoadPalette (objects/r3/intro.asm:478-496): lea AmyRosePalette(pc),a3; IntroLoadPalette: lea palette+$20,a4; movem.l (a3)+,d0-d3'),
    ('animalFlyingMappings', '217C 08080108 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjCCAnimal_Flying_Init (Animal/Main.asm:46-51): move.l #$8080108,oYRadius(a0); move.l #MapSpr_CCAnimalFlying,oMap(a0)'),
    ('animalFlyingAnimations', '43FA PPPP 4EB9 ???????? 4EB9 ???????? 3028 ???? 4EF9', 'optional',
     'ObjCCAnimal_Flying_Orbit (Animal/Main.asm:76-84): lea Ani_CCAnimalFlying(pc),a1; jsr AnimateObject; jsr DrawObject; move.w oCCAnimalBaseX(a0),d0; jmp CCAnimalDespawn2'),
    ('animalGroundMappings', '217C 0C080408 ???? 217C AAAAAAAA 0004', 'optional',
     'ObjCCAnimal_Ground_Init (Animal/Main.asm:132-136): move.l #$C080408,oYRadius(a0); move.l #MapSpr_CCAnimalGround,oMap(a0)'),
]

EXTRA_COLUMNS = ["animalBaseTiles"]


def extra_columns(stage: str, data: bytes) -> dict:
    """ObjCCAnimal_BaseTileList (Animal/Main.asm:208-212), the lea inside ObjCCAnimal_SetBaseTile.

    Other objects have a routine of the same shape (act * 4 + timeZone indexes a word list), so the list is found
    through the animal's own init: its bsr.w to SetBaseTile follows the ground animal's oMap store.
    """
    import re
    from object_catalogue import operands
    m = re.search(b"\\x21\\x7c\\x0c\\x08\\x04\\x08..\\x21\\x7c.{4}\\x00\\x04\\x61\\x00(..)", data, re.S)
    if not m:
        return {"animalBaseTiles": None}
    target = m.start(1) + int.from_bytes(m.group(1), "big", signed=True)
    lea = data[target:target + 4]
    if lea[:2] != bytes.fromhex("43FA"):
        return {"animalBaseTiles": None}
    return {"animalBaseTiles": target + 2 + int.from_bytes(lea[2:4], "big", signed=True)}
