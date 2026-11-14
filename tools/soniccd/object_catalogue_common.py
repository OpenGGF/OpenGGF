"""Common-object anchors for tools/soniccd/object_catalogue.py (owner: common).

Owned by the common-object lanes; writes src/main/resources/soniccd/object-catalogue-common.tsv,
read by CdObjectCatalogue. Columns OBJ0 first, then COMMON2. explosionTile is data: the Tidal
Tempest copy writes $0680 and never tests the layer, the others write $8680 and clear the
priority bit on layer 2 (src/objects/explosion.asm:68-77; Level/_Objects/Explosion/Main.asm:67-73).
"""
from object_catalogue import operands
from stage_inventory import require

ANCHORS = [
    ('ringMappings', '237C AAAAAAAA 0004 337C A7AE 0002', 'required',
     'ObjRing_Init (Ring/Main.asm:156-157) .InitSubObj: move.l #MapSpr_Ring,oMap(a1); move.w #$A7AE,oTile(a1)'),
    ('ringAnimations', '03F2 0002 43F9 AAAAAAAA', 'required',
     'ObjRing_Collect bset d1,2(a2,d0.w); ObjRing_Sparkle lea Ani_Ring,a1 (Ring/Main.asm:271-277)'),
    ('monitorMappings', '217C AAAAAAAA 0004 317C 05A8 0002', 'required',
     'ObjMonitor_Init: move.l #MapSpr_MonitorTime,oMap(a0); move.w #$5A8,oTile(a0); ObjMonitor_Init (Monitor and Time Post/Main.asm:304-305)'),
    ('monitorAnimations', '43F9 AAAAAAAA 6100 ???? 6100 ???? 4EF9', 'required',
     'ObjMonitor_Animate: lea Ani_Monitor,a1; bsr.w AnimateObject; ObjMonitor_Draw bsr.w DrawObject; jmp CheckObjDespawn (Monitor and Time Post/Main.asm:366-375)'),
    ('springMappings1', '217C AAAAAAAA 0004 317C 0520 0002', 'required',
     'ObjSpring_Init: move.l #MapSpr_Spring1,oMap(a0); move.w #$520,oTile(a0) (Spring/Main.asm:188-189)'),
    ('springMappings2', '117C 0008 0024 117C 0008 0019 117C 0010 0016 217C AAAAAAAA 0004', 'required',
     'ObjSpring_Init sideways: move.l #MapSpr_Spring2,oMap(a0) (Spring/Main.asm:198-201)'),
    ('springMappings3', '217C AAAAAAAA 0004 2F00 700F', 'required',
     'ObjSpring_Init diagonal: move.l #MapSpr_Spring3,oMap(a0); move.l d0,-(sp); moveq #$F,d0 (Spring/Main.asm:216-218)'),
    ('springAnimations', '43F9 AAAAAAAA 6000 ???? 08A8 0003 0022', 'required',
     'ObjSpring_Anim_Up: lea Ani_Spring,a1; bra.w AnimateObject; ObjSpring_Reset_Up bclr #3,oFlags(a0) (Spring/Main.asm:282-289)'),
    ('spikesMappings', '5428 0024 217C AAAAAAAA 0004 0028 0004 0001 117C 0003 0018', 'optional',
     'ObjSpikes_Init: addq.b #2,oRoutine(a0); move.l #MapSpr_Spikes,oMap(a0) (Spikes/Main.asm:20-23); absent where a zone has its own spikes object'),
    ('explosionMappings', ('5428 0024 0028 0004 0001 117C 0001 0018 317C 8680 0002 4A28 0029 67?? 0228 007F 0002 217C AAAAAAAA 0004', '5428 0024 0028 0004 0001 117C 0001 0018 317C 0680 0002 217C AAAAAAAA 0004'), 'required',
     'ObjExplosion_Init: move.l #MapSpr_Explosion,oMap(a0) (Explosion/Main.asm:67-73; Tidal Tempest: src/objects/explosion.asm:68-77)'),
    ('explosionAnimations', '317C 0100 001C 43F9 AAAAAAAA', 'required',
     'ObjExplosion_Init move.w #$100,oAnim(a0); ObjExplosion_Main lea Ani_Explosion,a1 (scddisasm/src/Level/_Objects/Explosion/Main.asm:83-90)'),
    ('pointsMappings', '317C 06C6 0002 217C AAAAAAAA 0004', 'required',
     'ObjPoints_Init (HUD and Points/Main.asm:36-37): move.w #$6C6,oTile(a0); move.l #MapSpr_Points,oMap(a0)'),
    ('hudMappings', '217C AAAAAAAA 0004 317C 8568 0002', 'required',
     'ObjHUD_Init (HUD and Points/Main.asm:89-90): move.l #MapSpr_HUD,oMap(a0); move.w #$8568,oTile(a0)'),
    ('hudNumbersArt', '7800 43F9 AAAAAAAA', 'required',
     'HUD_DrawNum and siblings: moveq #0,d4; lea Art_HUDNumbers,a1 (HUD and Points/Main.asm:687-688)'),
    ('lifeIconArt', '2D40 0004 43F9 AAAAAAAA', 'required',
     'LoadLifeIcon: move.l d0,4(a6); lea Art_LifeIcon,a1 (Level/Main.asm:606-607)'),
    ('titleCardMappings', '317C 8360 0002 217C AAAAAAAA 0004', 'required',
     'ObjTitleCard_Init: move.w #$8360,oTile(a0); move.l #MapSpr_TitleCard,oMap(a0) (Title Card/Main.asm:46-47)'),
    ('titleCardData', '7C07 45F9 AAAAAAAA', 'required',
     'ObjTitleCard_Init: moveq #7,d6; lea ObjTitleCard_Data,a2 (Title Card/Main.asm:50-51)'),
    ('objectTileIds', '43F9 AAAAAAAA D040 383B', 'optional',
     'SetObjectTileID: lea ObjectTileIDs,a1; add.w d0,d0; move.w ObjectTileIDs(pc,d0.w),d4 (Object Tile ID Set.asm:7-10); absent where the stage links the null variant'),
    ('ringDeltas', '43F9 AAAAAAAA 1A31 0000', 'required',
     'ObjRing_Init: lea ObjRing_Deltas,a1; move.b (a1,d0.w),d5 (Ring/Main.asm:81-82)'),
    # --- common2 ---
    ('boulderMappings', '117C 0004 0018 217C AAAAAAAA 0004 117C 0010 0019 117C 0010 0016', 'required',
     'ObjBoulder_Init: move.b #4,oPriority(a0); move.l #MapSpr_Boulder,oMap(a0); oWidth/oYRadius $10; ObjBoulder_Init (Boulder/Main.asm:26-32)'),
    ('floatBlockMappings', '0028 0004 0001 217C AAAAAAAA 0004 7005', 'optional',
     'ObjFloatBlock_Init: ori.b #4,oSprFlags(a0); move.l #MapSpr_FloatBlock,oMap(a0); moveq #5,d0; ObjFloatBlock_Init (Floating Block/Main.asm:39-43); absent where a zone has its own block'),
    ('floatBlockAnimations', '117C 0002 0024 4E75 43F9 AAAAAAAA', 'optional',
     'ObjFloatBlock_Appear: move.b #2,oRoutine(a0); rts; .Animate lea Ani_FloatBlock,a1; ObjFloatBlock_Appear (Floating Block/Main.asm:101-110)'),
    ('checkpointMappings', '217C AAAAAAAA 0004 317C 06CB 0002 117C 0004 0001', 'required',
     'ObjCheckpoint_Init: move.l #MapSpr_Checkpoint,oMap(a0); move.w #$6CB,oTile(a0) (Checkpoint/Main.asm:74-76)'),
    ('checkpointAnimations', '5428 0024 4E75 43F9 AAAAAAAA 6000', 'required',
     'ObjCheckpoint_Ball addq.b #2,oRoutine(a0); rts; ObjCheckpoint_Animate lea Ani_Checkpoint,a1 (Checkpoint/Main.asm:178-189)'),
    ('flowerMappings', '4A28 0029 67?? 0228 007F 0002 217C AAAAAAAA 0004 4A28 0028', 'optional',
     "ObjFlower_Init: andi.b #$7F,oTile(a0); move.l #MapSpr_Flower,oMap(a0); tst.b oSubtype(a0) (Flower/Main.asm:37-44); Tidal Tempest's dialect differs"),
    ('flowerAnimations', '5468 000C 43F9 AAAAAAAA 6000', 'required',
     'ObjFlower_Seed .Fall addq.w #2,oY(a0); ObjFlower_Animate lea Ani_Flower,a1 (Flower/Main.asm:88-97)'),
    ('powerupMappings', '217C AAAAAAAA 0004 117C 0004 0001 117C 0001 0018 117C 0010 0019 317C 0544 0002', 'required',
     'ObjPowerup_Init: move.l #MapSpr_Powerup,oMap(a0); oSprFlags 4; priority 1; oWidth $10; move.w #$544,oTile(a0) (Powerup/Main.asm:23-27)'),
    ('powerupAnimations', '0228 007F 0002 43F9 AAAAAAAA 4EB9', 'optional',
     "ObjPowerup_Shield andi.b #$7F,oTile(a0); .Animate lea Ani_Powerup,a1; jsr AnimateObject (Powerup/Main.asm:55-59); Tidal Tempest's dialect differs"),
    ('robotGeneratorBaseTiles', '117C 0020 0016 43FA PPPP 7000 1039', 'optional',
     'ObjRobotGenerator_Init: move.b #$20,oYRadius(a0); lea ObjRobotGenerator_BaseTileList(pc),a1 (Robot Generator/Main.asm:49-58)'),
    ('goalPostTileIds', 'D001 317B 00PP 0002', 'optional',
     'ObjGoalPost_SetBaseTile: add.b d1,d0; move.w .TileIDs(pc,d0.w),oTile(a0) (Level End/Main.asm:664-677)'),
    ('goalSignpostMappings', '0028 0080 0002 217C AAAAAAAA 0004', 'ambiguous',
     'ObjSignpost_Init: ori.b #$80,oTile(a0); .NotHighPriority move.l #MapSpr_GoalSignpost,oMap(a0) (Level End/Main.asm:739-752)'),
    ('signpostAnimations', '43F9 AAAAAAAA 4EB9 ???????? 5328', 'ambiguous',
     'ObjSignpost_Spin: lea Ani_Signpost,a1; jsr AnimateObject; subq.b #1,oLvlEndTimer(a0) (Level End/Main.asm:792-795)'),
    ('bigRingMappings', '317C 2488 0002 217C AAAAAAAA 0004', 'optional',
     'ObjBigRing_Init: move.w #$2488,oTile(a0); move.l #MapSpr_BigRing,oMap(a0) (Level End/Main.asm:465-479)'),
    ('resultsInitData', '45F9 AAAAAAAA 7C?? 7200 2248 317C 01E0 0032', 'ambiguous',
     'ObjResults_WaitPLC .LoadResults: lea ObjResults_InitData,a2; moveq #n,d6; moveq #0,d1; movea.l a0,a1; ObjResults_WaitPLC (Results/Main.asm:90-94)'),
    ('resultsBadMappings', '237C AAAAAAAA 0004 4A39 ???????? 67?? 237C ???????? 0004 3401', 'ambiguous',
     'ObjResults .NotSSZ3: move.l #MapSpr_ResultsBad,oMap(a1) (Results/Main.asm:126-131)'),
    ('resultsGoodMappings', '237C ???????? 0004 4A39 ???????? 67?? 237C AAAAAAAA 0004 3401', 'ambiguous',
     'ObjResults .NotSSZ3: move.l #MapSpr_ResultsGood,oMap(a1) (Results/Main.asm:126-131)'),
    ('resultsBadSsz3Mappings', '337C 82F2 0002 237C AAAAAAAA 0004 4A39 ???????? 67?? 237C ???????? 0004 60??', 'ambiguous',
     'ObjResults .LoadResults, Stardust Speedway act 3: move.w #$82F2,oTile(a1); move.l #MapSpr_ResultsBadSSZ3,oMap(a1) (Results/Main.asm:116-124); read in act 3 of Stardust Speedway only'),
    ('resultsGoodSsz3Mappings', '337C 82F2 0002 237C ???????? 0004 4A39 ???????? 67?? 237C AAAAAAAA 0004 60??', 'ambiguous',
     'ObjResults .LoadResults, Stardust Speedway act 3: move.l #MapSpr_ResultsGoodSSZ3,oMap(a1) (Results/Main.asm:116-124); read in act 3 of Stardust Speedway only'),
    ('timeBonuses', 'D040 31FB 00PP', 'optional',
     'StartResults .GetBonus: add.w d0,d0; move.w .TimeBonuses(pc,d0.w),timeBonus.w (Level End/Main.asm:816-859)'),
    ('gameOverMappings', '317C 8544 0002 217C AAAAAAAA 0004', 'optional',
     'ObjGameOver_Init: move.w #$8544,oTile(a0); move.l #MapSpr_GameOver,oMap(a0) (Game Over/Main.asm:35-36)'),
    ('timeOverMappings', '217C AAAAAAAA 0004 5439', 'optional',
     'ObjGameOver_Init: move.l #MapSpr_TimeOver,oMap(a0); addq.b #2,powerup (Game Over/Main.asm:43-44)'),
    ('robotGeneratorMappings', '3171 0000 0002 217C AAAAAAAA 0004', 'optional',
     'ObjRobotGenerator_Init: move.w (a1,d0.w),oTile(a0); move.l #MapSpr_RobotGenerator,oMap(a0); ObjRobotGenerator_Init (Robot Generator/Main.asm:62-63)'),
    ('robotGeneratorAnimations', '4EB9 ???????? 43FA PPPP 4EF9', 'optional',
     'ObjRobotGenerator_ActivePast .Solid: jsr SolidObject; lea Ani_RobotGenerator(pc),a1; jmp AnimateObject (Robot Generator/Main.asm:117-119)'),
    ('robotGeneratorExplosions', '217C AAAAAAAA 002C 3168 000C 0030', 'optional',
     'ObjRobotGenerator_Init: move.l #ObjRobotGenerator_ExplosionLocs,oRobotGeneratorSchedule(a0); ObjRobotGenerator_Init (Robot Generator/Main.asm:63-64)'),
    ('shieldArtIndex', '5500 D040 227B 00PP', 'required',
     'LoadShieldArt (Powerup/Main.asm:157-164): subq.b #2,d0; add.w d0,d0; movea.l the art index entry,a1'),
    ('bigRingAnimations', '137C 0001 0028 43F9 AAAAAAAA', 'optional',
     'ObjBigRing_Main: move.b #1,oSubtype(a1); ObjBigRing_Animate lea Ani_BigRing,a1 (Level End/Main.asm:488-521)'),
    ('bigRingFlashMappings', '317C 03EF 0002 217C AAAAAAAA 0004', 'optional',
     'ObjBigRingFlash_Init: move.w #$3EF,oTile(a0); move.l #MapSpr_BigRingFlash,oMap(a0); ObjBigRingFlash_Init (Level End/Main.asm:404-408)'),
    ('bigRingFlashAnimations', '317C 03EF 0002 217C ???????? 0004 43F9 AAAAAAAA', 'optional',
     'ObjBigRingFlash_Animate: lea Ani_BigRingFlash,a1 (Level End/Main.asm:404-415)'),
]
# --- common2: object IDs ---
# RunObjects: lea ObjectIndex,a1; movea.l -4(a1,d0.w),a1 (Object Functions.asm:11-25).
OBJECT_INDEX = "D040 D040 43F9 AAAAAAAA 2271 00FC 4E91"
# (column, signature, byte distance(s) from the routine's entry to the signature's first byte, citation).
# The stage's index slot whose entry starts that routine is the object ID it uses; zones place some
# common routines at other IDs (the robot generator) or reuse an ID for another object.
ROUTINE_IDS = [
    ("boulderId", ANCHORS[[a[0] for a in ANCHORS].index("boulderMappings")][1], (40,),
     "ObjBoulder (Boulder/Main.asm:7-30): the entry to ObjBoulder_Init's priority write"),
    ("floatBlockId", "0028 0004 0001 217C AAAAAAAA 0004 7005", (64,),
     "ObjFloatBlock (Floating Block/Main.asm:7-41): the entry to ObjFloatBlock_Init's sprite flags write"),
    ("checkpointId", "217C AAAAAAAA 0004 317C 06CB 0002 117C 0004 0001", (38,),
     "ObjCheckpoint (Checkpoint/Main.asm:50-74): the entry to ObjCheckpoint_Init's oMap write"),
    ("flowerId", "4A28 0029 67?? 0228 007F 0002 217C AAAAAAAA 0004 4A28 0028", (54,),
     "ObjFlower (Flower/Main.asm:11-37): the entry to ObjFlower_Init's priority test"),
    ("robotGeneratorId", "3171 0000 0002 217C AAAAAAAA 0004", (100,),
     "ObjRobotGenerator (Robot Generator/Main.asm:25-62): the entry to the base tile write"),
    ("goalPostId", "117C 0004 0018 217C AAAAAAAA 0004 117C 0010 0017", (104,),
     "ObjGoalPost (Level End/Main.asm:568-614): the entry to ObjGoalPost_Init's priority write"),
    ("signpostId", "0028 0080 0002 217C AAAAAAAA 0004", (80,),
     "ObjSignpost (Level End/Main.asm:708-750): the entry to ObjSignpost_Init's priority-bit write"),
    ("bigRingId", "317C 2488 0002 217C AAAAAAAA 0004", (98,),
     "ObjBigRing (Level End/Main.asm:429-478): the entry to ObjBigRing_Init's tile write"),
    ("gameOverId", "317C 8544 0002 217C AAAAAAAA 0004", (50,),
     "ObjGameOver (Game Over/Main.asm:11-35): the entry to ObjGameOver_Init's tile write"),
    ("resultsId", "237C AAAAAAAA 0004 4A39 ???????? 67?? 237C ???????? 0004 3401", (172,),
     "ObjResults (Results/Main.asm:42-127): the entry to .NotSSZ3's mappings write"),
    ("testBadnikId", "117C 0001 0018 217C AAAAAAAA 0004 317C 0541 0002", (38,),
     "ObjTestBadnik (Test Badnik/Main.asm:11-34): the entry to ObjTestBadnik_Init's priority write"),
    ("animalId", "217C 0808 0408 0016 217C AAAAAAAA 0004 3168 0008 002A 3168 000C 002C", (48, 46),
     "ObjAnimal (Palmtree Panic/Objects/Animal/Main.asm:7-38): the entry to ObjFlyingAnimal_Init's radii"
     " write; the R43 demo copy's CheckAnimalPrescence call is two bytes shorter"),
]
EXTRA_COLUMNS = ["explosionTile"] + [r[0] for r in ROUTINE_IDS]
# --- end common2: object IDs ---
# Tidal Tempest's explosion keeps tile $0680 with no layer test (src/objects/explosion.asm:68-77).
EXPLOSION_TILE_SIGNATURES = {0x8680: ANCHORS[9][1][0], 0x0680: ANCHORS[9][1][1]}
assert ANCHORS[9][0] == "explosionMappings"


def extra_columns(stage: str, data: bytes) -> dict:
    tiles = [tile for tile, signature in EXPLOSION_TILE_SIGNATURES.items() if operands(data, signature)]
    require(len(tiles) == 1, f"{stage} explosionTile: expected one dialect, got {tiles}")
    row = {"explosionTile": tiles[0]}
    row.update(routine_ids(stage, data))
    row.update(live_copies(stage, data, row))
    return row


# --- shared: live copies through the ObjectIndex ---
# Collision Chaos acts 1-2 (R31B-R32D) link two results bodies and Stardust Speedway (R71A-R73D) a
# second signpost-like init, so the "ambiguous" anchors above read "-" there. The copy the stage's
# ObjectIndex starts at resultsId / signpostId is the live one; its operands sit at fixed distances
# from that entry (ObjResults, Results/Main.asm:42-127: .LoadResults at 80, the SSZ3 tile and mappings writes at
# 140, .NotSSZ3 at 172;
# ObjSignpost, Level End/Main.asm:708-795: ObjSignpost_Init at 80, ObjSignpost_Spin at 186).
# Where the anchor found one operand it is this copy, so only the empty cells change. Owned by the
# shared-object lane.
LIVE_COPIES = [
    ("resultsInitData", "resultsId", 80), ("resultsBadMappings", "resultsId", 172),
    ("resultsGoodMappings", "resultsId", 172), ("resultsBadSsz3Mappings", "resultsId", 140),
    ("resultsGoodSsz3Mappings", "resultsId", 140), ("goalSignpostMappings", "signpostId", 80),
    ("signpostAnimations", "signpostId", 186),
]


def live_copies(stage: str, data: bytes, row: dict) -> dict:
    from object_catalogue import pattern
    from stage_catalogue import WORD_RAM
    signatures = {a[0]: a[1] for a in ANCHORS}
    tables = operands(data, OBJECT_INDEX)
    require(len(tables) == 1, f"{stage} ObjectIndex: expected one operand, got {tables}")
    out = {}
    for column, id_column, distance in LIVE_COPIES:
        object_id = row.get(id_column)
        if object_id is None:
            continue
        address = int.from_bytes(data[tables[0] + 4 * (object_id - 1):tables[0] + 4 * object_id], "big")
        entry = address - WORD_RAM
        m = pattern(signatures[column]).match(data, entry + distance)
        if m is None:
            continue
        out[column] = (int.from_bytes(m.group("abs"), "big") & 0xFFFFFF) - WORD_RAM
    return out

# The Level dialect's ObjSpikes (Spikes/Main.asm:7-84) is $26 only in Palmtree Panic; that ID is the door
# in Collision Chaos, the elevator in Tidal Tempest and the jump spike ball in Stardust Speedway, whose
# spikes are other routines. spikesId gives the stage's ObjSpikes slot for a stage-mapped registration:
# the entry to ObjSpikes_Init's addq/oMap writes (scddisasm/src/Level/_Objects/Spikes/Main.asm:7-23) is 18 bytes.
ROUTINE_IDS.append(("spikesId", ANCHORS[[a[0] for a in ANCHORS].index("spikesMappings")][1], (18,),
                    "ObjSpikes (Spikes/Main.asm:7-23): the entry to ObjSpikes_Init"))
EXTRA_COLUMNS.append("spikesId")
# --- end shared ---


def routine_ids(stage: str, data: bytes) -> dict:
    """Per routine, the lowest object ID whose ObjectIndex entry starts it; None when no slot does."""
    from object_catalogue import pattern
    from stage_catalogue import WORD_RAM
    tables = operands(data, OBJECT_INDEX)
    require(len(tables) == 1, f"{stage} ObjectIndex: expected one operand, got {tables}")
    entries = []
    while len(entries) < 0x80:
        address = int.from_bytes(data[tables[0] + 4 * len(entries):tables[0] + 4 * len(entries) + 4], "big")
        if not WORD_RAM <= address < WORD_RAM + len(data):
            break
        entries.append(address - WORD_RAM)
    out = {}
    for column, signature, distances, _ in ROUTINE_IDS:
        regex = pattern(signature)
        out[column] = next((i + 1 for i, entry in enumerate(entries)
                            if any(regex.match(data, entry + d) for d in distances)), None)
    return out
