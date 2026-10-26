package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;

/**
 * Reviewed schema-v2 extents plus source/retail-checked player/background camera table views at scddisasm 1ecc5f3a.
 * A source blob is not a consumer read extent. Encoding names cover only the
 * inventory's checked consumer; UNKNOWN is preserved even for .nem-named blobs.
 * No data bytes, JSON parser, source path or runtime disassembly dependency.
 */
public enum CdR11aAsset {
    ART_3D_PLANT("Art_3DPlant", Kind.SOURCE_BLOB, 0x3AC7A, 0x3AC7A, 392, Encoding.NEMESIS),
    ART_3D_RAMP_BOOST("Art_3DRampBoost", Kind.SOURCE_BLOB, 0x3AE02, 0x3AE02, 678, Encoding.NEMESIS),
    ART_AMY_ROSE("Art_AmyRose", Kind.SOURCE_BLOB, 0x3F2A6, 0x3F2A6, 2678, Encoding.NEMESIS),
    ART_ANIMALS("Art_Animals", Kind.SOURCE_BLOB, 0x381C0, 0x381C0, 446, Encoding.NEMESIS),
    ART_ANTON("Art_Anton", Kind.SOURCE_BLOB, 0x36E46, 0x36E46, 484, Encoding.NEMESIS),
    ART_BIG_RING("Art_BigRing", Kind.SOURCE_BLOB, 0x31CD0, 0x31CD0, 1034, Encoding.NEMESIS),
    ART_BIG_RING_FLASH("Art_BigRingFlash", Kind.SOURCE_BLOB, 0xDB3C, 0xDB4A, 384, Encoding.NEMESIS),
    ART_BOULDER("Art_Boulder", Kind.SOURCE_BLOB, 0x364C2, 0x364C2, 270, Encoding.NEMESIS),
    ART_BUTTON("Art_Button", Kind.SOURCE_BLOB, 0x37EFE, 0x37EFE, 282, Encoding.UNKNOWN),
    ART_CHECKPOINT("Art_Checkpoint", Kind.SOURCE_BLOB, 0x35DC6, 0x35DC6, 212, Encoding.NEMESIS),
    ART_DIAGONAL_SPRING("Art_DiagonalSpring", Kind.SOURCE_BLOB, 0x3460C, 0x3460C, 780, Encoding.NEMESIS),
    ART_EXPLOSIONS("Art_Explosions", Kind.SOURCE_BLOB, 0x3513E, 0x3513E, 1256, Encoding.NEMESIS),
    ART_FLOAT_BLOCK("Art_FloatBlock", Kind.SOURCE_BLOB, 0x365D0, 0x365D0, 230, Encoding.NEMESIS),
    ART_FLOWER("Art_Flower", Kind.SOURCE_BLOB, 0x35F4E, 0x35F4E, 646, Encoding.NEMESIS),
    ART_FLOWER_CAPSULE("Art_FlowerCapsule", Kind.SOURCE_BLOB, 0x31628, 0x31628, 1704, Encoding.UNKNOWN),
    ART_GAME_OVER("Art_GameOver", Kind.SOURCE_BLOB, 0x33180, 0x33180, 1024, Encoding.RAW_COPY),
    ART_GOAL_POST("Art_GoalPost", Kind.SOURCE_BLOB, 0x320DA, 0x320DA, 448, Encoding.NEMESIS),
    ART_HUD("Art_HUD", Kind.SOURCE_BLOB, 0x35C9A, 0x35C9A, 300, Encoding.NEMESIS),
    ART_HUD_NUMBERS("Art_HUDNumbers", Kind.SOURCE_BLOB, 0x3589A, 0x3589A, 1024, Encoding.UNKNOWN),
    ART_INV_STARS("Art_InvStars", Kind.SOURCE_BLOB, 0x33DAC, 0x33DAC, 1152, Encoding.RAW_COPY),
    ART_LEVEL_TILES("Art_LevelTiles", Kind.SOURCE_BLOB, 0x3C4EE, 0x3C4EE, 10374, Encoding.NEMESIS),
    ART_LIFE_ICON("Art_LifeIcon", Kind.SOURCE_BLOB, 0x3571A, 0x3571A, 384, Encoding.UNKNOWN),
    ART_LOG_SHADOW("Art_LogShadow", Kind.SOURCE_BLOB, 0x35EC0, 0x35EC0, 24, Encoding.NEMESIS),
    ART_LOG_SHADOW_UNUSED("Art_LogShadowUnused", Kind.SOURCE_BLOB, 0x35E9A, 0x35E9A, 38, Encoding.UNKNOWN),
    ART_MONITOR_TIME_POSTS("Art_MonitorTimePosts", Kind.SOURCE_BLOB, 0x34B3A, 0x34B3A, 1540, Encoding.NEMESIS),
    ART_MOSQUI("Art_Mosqui", Kind.SOURCE_BLOB, 0x3702A, 0x3702A, 846, Encoding.NEMESIS),
    ART_PATA_BATA("Art_PataBata", Kind.SOURCE_BLOB, 0x37378, 0x37378, 714, Encoding.NEMESIS),
    ART_PLATFORM("Art_Platform", Kind.SOURCE_BLOB, 0x362FE, 0x362FE, 452, Encoding.NEMESIS),
    ART_POINTS("Art_Points", Kind.SOURCE_BLOB, 0x315C4, 0x315C4, 100, Encoding.NEMESIS),
    ART_RESULTS("Art_Results", Kind.SOURCE_BLOB, 0x32876, 0x32876, 1354, Encoding.NEMESIS),
    ART_RINGS("Art_Rings", Kind.SOURCE_BLOB, 0x35626, 0x35626, 244, Encoding.NEMESIS),
    ART_ROBOT_GEN_WITHERED("Art_RobotGenWithered", Kind.SOURCE_BLOB, 0x3846C, 0x3846C, 332, Encoding.NEMESIS),
    ART_SHIELD("Art_Shield", Kind.SOURCE_BLOB, 0x3392C, 0x3392C, 1152, Encoding.RAW_COPY),
    ART_SIGNPOST("Art_Signpost", Kind.SOURCE_BLOB, 0x3229A, 0x3229A, 1500, Encoding.NEMESIS),
    ART_SONIC("Art_Sonic", Kind.SOURCE_BLOB, 0x20000, 0x20000, 69471, Encoding.UNKNOWN),
    ART_SONIC_HOLE("Art_SonicHole", Kind.SOURCE_BLOB, 0x3AC08, 0x3AC08, 114, Encoding.NEMESIS),
    ART_SPIKES("Art_Spikes", Kind.SOURCE_BLOB, 0x38018, 0x38018, 86, Encoding.NEMESIS),
    ART_SPINNING_DISC("Art_SpinningDisc", Kind.SOURCE_BLOB, 0x3676C, 0x3676C, 166, Encoding.NEMESIS),
    ART_SPINNING_DISC_DRILL("Art_SpinningDiscDrill", Kind.SOURCE_BLOB, 0x3837E, 0x3837E, 238, Encoding.UNKNOWN),
    ART_SPRING_WHEEL("Art_SpringWheel", Kind.SOURCE_BLOB, 0x366B6, 0x366B6, 182, Encoding.UNKNOWN),
    ART_SPRINGBOARD("Art_Springboard", Kind.SOURCE_BLOB, 0x37DC6, 0x37DC6, 312, Encoding.UNKNOWN),
    ART_SPRINGS("Art_Springs", Kind.SOURCE_BLOB, 0x34918, 0x34918, 546, Encoding.NEMESIS),
    ART_SWINGING_PLATFORM("Art_SwingingPlatform", Kind.SOURCE_BLOB, 0x3806E, 0x3806E, 338, Encoding.UNKNOWN),
    ART_TAGA_TAGA("Art_TagaTaga", Kind.SOURCE_BLOB, 0x37642, 0x37642, 782, Encoding.UNKNOWN),
    ART_TAMABBOH("Art_Tamabboh", Kind.SOURCE_BLOB, 0x37950, 0x37950, 1142, Encoding.NEMESIS),
    ART_TIME_OVER("Art_TimeOver", Kind.SOURCE_BLOB, 0x32DC0, 0x32DC0, 960, Encoding.RAW_COPY),
    ART_TIME_STARS("Art_TimeStars", Kind.SOURCE_BLOB, 0x3422C, 0x3422C, 992, Encoding.RAW_COPY),
    ART_TITLE_CARD("Art_TitleCard", Kind.SOURCE_BLOB, 0x33580, 0x33580, 940, Encoding.NEMESIS),
    ART_TITLE_CARD_TEXT("Art_TitleCardText", Kind.SOURCE_BLOB, 0x361D4, 0x361D4, 298, Encoding.NEMESIS),
    ART_TUNNEL_DOOR("Art_TunnelDoor", Kind.SOURCE_BLOB, 0x36AFE, 0x36AFE, 154, Encoding.NEMESIS),
    ART_TUNNEL_DOOR_SPLASH("Art_TunnelDoorSplash", Kind.SOURCE_BLOB, 0x36B98, 0x36B98, 686, Encoding.UNKNOWN),
    ART_TUNNEL_WATERFALL("Art_TunnelWaterfall", Kind.SOURCE_BLOB, 0x36812, 0x36812, 328, Encoding.NEMESIS),
    ART_WATERFALL("Art_Waterfall", Kind.SOURCE_BLOB, 0x3695A, 0x3695A, 420, Encoding.UNKNOWN),
    COL_ANGLE_MAP("ColAngleMap", Kind.SOURCE_BLOB, 0x385B8, 0x385B8, 256, Encoding.UNKNOWN),
    COL_HEIGHT_MAP("ColHeightMap", Kind.SOURCE_BLOB, 0x386B8, 0x386B8, 4096, Encoding.UNKNOWN),
    COL_WIDTH_MAP("ColWidthMap", Kind.SOURCE_BLOB, 0x396B8, 0x396B8, 4096, Encoding.UNKNOWN),
    LAYOUT_BG("LayoutBG", Kind.SOURCE_BLOB, 0x3ABD6, 0x3ABD6, 50, Encoding.UNKNOWN),
    LAYOUT_FG("LayoutFG", Kind.SOURCE_BLOB, 0x3AA84, 0x3AA84, 338, Encoding.UNKNOWN),
    LEVEL_BLOCKS("LevelBlocks", Kind.SOURCE_BLOB, 0x3B0A8, 0x3B0A8, 5190, Encoding.NEMESIS),
    LEVEL_CHUNKS("LevelChunks", Kind.SOURCE_BLOB, 0x10000, 0x10000, 61952, Encoding.UNKNOWN),
    LEVEL_COLLISION("LevelCollision", Kind.SOURCE_BLOB, 0x3A6B8, 0x3A6B8, 900, Encoding.UNKNOWN),
    LEVEL_START_LOC("LevelStartLoc", Kind.SOURCE_BLOB, 0x29EE, 0x29EA, 4, Encoding.UNKNOWN),
    OBJECT_PLACEMENT_RECORDS("ObjectPlacementRecords", Kind.SOURCE_BLOB, 0x7906, 0x7914, 3984, Encoding.UNKNOWN),
    PAL_LEVEL("Pal_Level", Kind.SOURCE_BLOB, 0x6F4, 0x6F4, 96, Encoding.UNKNOWN),
    PAL_LEVEL_END("Pal_LevelEnd", Kind.SOURCE_BLOB, 0x7B4, 0x7B4, 96, Encoding.UNKNOWN),
    PAL_LEVEL_PROTO("Pal_LevelProto", Kind.SOURCE_BLOB, 0x754, 0x754, 96, Encoding.UNKNOWN),
    PAL_S1_LEV_SEL("Pal_S1LevSel", Kind.SOURCE_BLOB, 0x654, 0x654, 128, Encoding.UNKNOWN),
    PAL_S1_TITLE("Pal_S1Title", Kind.SOURCE_BLOB, 0x5D4, 0x5D4, 128, Encoding.UNKNOWN),
    PAL_SONIC("Pal_Sonic", Kind.SOURCE_BLOB, 0x6D4, 0x6D4, 32, Encoding.UNKNOWN),
    ANI_SONIC("Ani_Sonic", Kind.TABLE, 0x599A, 0x59A8, 578, Encoding.UNKNOWN),
    // Bounded player consumers at scddisasm 1ecc5f3a. The mini view admits only
    // the 60 valid Ani_Sonic IDs, not the source's unused $3C..$4F entries.
    SONIC_MINI_ANIMATIONS("ObjSonic_GetMiniAnim.MiniAnims", Kind.TABLE, 0x594A, 0x5958, 60, Encoding.UNKNOWN),
    SONIC_RAMP_CHUNKS("ObjSonic_Handle3DRamp.ChunkList", Kind.TABLE, 0x4710, 0x470C, 6, Encoding.UNKNOWN),
    SPEC_CHUNKS("SpecChunks", Kind.TABLE, 0x29F2, 0x29EE, 4, Encoding.UNKNOWN),
    SINE_TABLE("SineTable", Kind.TABLE, 0x872, 0x872, 640, Encoding.UNKNOWN),
    // CalcAngle can index quotient 256. The following EVEN byte is not read.
    ARC_TAN_TABLE("ArcTanTable", Kind.TABLE, 0xB58, 0xB58, 257, Encoding.UNKNOWN),
    // PP Scroll:1123-1164 / 1178-1183 at scddisasm 1ecc5f3a; retain the offscreen top row.
    // One byte per 16 pixels; selectors 0/2/4/6 double to four big-endian camera-address longs.
    // These 24-bit RAM addresses are not MMD file offsets. No drawing consumer is installed here.
    BG_CAMERA_SECT_IDS("BGCameraSectIDs", Kind.TABLE, 0x3534, 0x3530, 130, Encoding.UNKNOWN),
    BG_CAMERA_SECTS("BGCameraSects", Kind.TABLE, 0x35B6, 0x35B2, 16, Encoding.UNKNOWN),
    CAM_BOUNDS("CamBounds", Kind.TABLE, 0x2950, 0x294C, 12, Encoding.UNKNOWN),
    CLOUD_SECT_SIZES("CloudSectSizes", Kind.TABLE, 0x2C72, 0x2C6E, 10, Encoding.UNKNOWN),
    CLOUD_UPSIDE_DOWN_SECT_SIZES("CloudUpsideDownSectSizes", Kind.TABLE, 0x2C7C, 0x2C78, 9, Encoding.UNKNOWN),
    DPLC_SONIC("DPLC_Sonic", Kind.TABLE, 0x30F60, 0x30F60, 1636, Encoding.UNKNOWN),
    LEVEL_COL_INDEX("LevelColIndex", Kind.TABLE, 0x1A9A, 0x1A96, 32, Encoding.UNKNOWN),
    LEVEL_DATA_INDEX("LevelDataIndex", Kind.TABLE, 0xFBB6, 0xFBC4, 16, Encoding.UNKNOWN),
    LEVEL_LAYOUTS("LevelLayouts", Kind.TABLE, 0x3AA3C, 0x3AA3C, 72, Encoding.UNKNOWN),
    MAP_SPR_SONIC("MapSpr_Sonic", Kind.TABLE, 0x1F200, 0x1F200, 3004, Encoding.UNKNOWN),
    OBJECT_INDEX("ObjectIndex", Kind.TABLE, 0x3E34, 0x3E30, 256, Encoding.UNKNOWN),
    OBJECT_LAYOUTS("ObjectLayouts", Kind.TABLE, 0x78FA, 0x7908, 12, Encoding.UNKNOWN),
    PLC_LISTS("PLCLists", Kind.TABLE, 0xFBC6, 0xFBD4, 38, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS1("PalCycleColors1", Kind.TABLE, 0x2C8, 0x2C8, 6, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS2("PalCycleColors2", Kind.TABLE, 0x2D6, 0x2D6, 6, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS3("PalCycleColors3", Kind.TABLE, 0x2E4, 0x2E4, 6, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS4("PalCycleColors4", Kind.TABLE, 0x2F2, 0x2F2, 6, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS5("PalCycleColors5", Kind.TABLE, 0x300, 0x300, 6, Encoding.UNKNOWN),
    PAL_CYCLE_COLORS6("PalCycleColors6", Kind.TABLE, 0x30E, 0x30E, 6, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT1("PalCycleScript1", Kind.TABLE, 0x2C0, 0x2C0, 8, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT2("PalCycleScript2", Kind.TABLE, 0x2CE, 0x2CE, 8, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT3("PalCycleScript3", Kind.TABLE, 0x2DC, 0x2DC, 8, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT4("PalCycleScript4", Kind.TABLE, 0x2EA, 0x2EA, 8, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT5("PalCycleScript5", Kind.TABLE, 0x2F8, 0x2F8, 8, Encoding.UNKNOWN),
    PAL_CYCLE_SCRIPT6("PalCycleScript6", Kind.TABLE, 0x306, 0x306, 8, Encoding.UNKNOWN),
    PALETTE_TABLE("PaletteTable", Kind.TABLE, 0x5A4, 0x5A4, 48, Encoding.UNKNOWN),
    SECTION_INIT_PLCS("SectionInitPLCs", Kind.TABLE, 0xCF1C, 0xCF2A, 10, Encoding.UNKNOWN),
    SECTION_RANGES("SectionRanges", Kind.TABLE, 0xCF08, 0xCF16, 10, Encoding.UNKNOWN),
    SECTION_UPDATE_PLCS("SectionUpdatePLCs", Kind.TABLE, 0xCF12, 0xCF20, 10, Encoding.UNKNOWN),
    SHIELD_ART_INDEX("ShieldArtIndex", Kind.TABLE, 0x65BE, 0x65CC, 20, Encoding.UNKNOWN),
    WATER_DEFORM_SECTS("WaterDeformSects", Kind.TABLE, 0x2C86, 0x2C82, 12, Encoding.UNKNOWN);

    public enum Kind { SOURCE_BLOB, TABLE }
    public enum Encoding { UNKNOWN, NEMESIS, RAW_COPY }

    private final String nativeLabel;
    private final Kind kind;
    private final int usaOffset;
    private final int japanEuropeOffset;
    private final int length;
    private final Encoding encoding;

    CdR11aAsset(String nativeLabel, Kind kind, int usaOffset, int japanEuropeOffset, int length, Encoding encoding) {
        this.nativeLabel = nativeLabel;
        this.kind = kind;
        this.usaOffset = usaOffset;
        this.japanEuropeOffset = japanEuropeOffset;
        this.length = length;
        this.encoding = encoding;
    }
    public String nativeLabel() { return nativeLabel; }
    public Kind kind() { return kind; }
    public int length() { return length; }
    public Encoding encodingForReviewedConsumer() { return encoding; }
    public int fileOffset(CdReleaseVariant variant) {
        java.util.Objects.requireNonNull(variant, "variant");
        return variant == CdReleaseVariant.USA ? usaOffset : japanEuropeOffset;
    }
}
