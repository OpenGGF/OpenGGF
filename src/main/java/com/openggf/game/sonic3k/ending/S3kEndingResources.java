package com.openggf.game.sonic3k.ending;

/**
 * Locked-on REV01 ending resources. Verified against CRC32 $63522553 by the
 * 2026-10-13 playable-completion task: owning LEA/PLC pointers and reference
 * file bytes, rather than similarly named Sonic 3 resources.
 */
public final class S3kEndingResources {
    public static final int CREDIT_TILE = 0x347;
    private S3kEndingResources() { }

    public static final int CREDIT_FONT = 0xDDE34; // ArtNem_CreditsText
    public static final int CREDIT_GLYPHS = 0x5B566; // CreditsText_PlaneMap
    public static final int CREDIT_DELAY = 0x5B656; // CreditsText_ScrollDelay
    public static final int CREDIT_SPEED = 0x5B69E; // CreditsText_ScrollSpeed
    public static final int CREDIT_PARTS = 0x5B284; // off_5B284
    public static final int POST_CREDITS_DISPATCH = 0x5EFB6;
    public static final int ENDING_PLANE = 0x163418;
    public static final int ENDING_PLANE_EXTRA = 0x162914;
    public static final int MAP_ENDING_PLANE = 0x6036E;
    public static final int MAP_ENDING_PLANE_EXTRA = 0x60486;
    public static final int TORNADO_SUBSPRITE_OFFSETS = 0x6006E; // byte_6006E
    public static final int TORNADO_NOSE_FRAMES = 0x60072; // byte_60072
    public static final int MASTER_EMERALD = 0x17FCBA;
    public static final int MAP_MASTER_EMERALD = 0x7D712; // ObjDat3_7D450's actual mapping pointer
    public static final int SONIC_POSE = 0x161660;
    public static final int SUPER_SONIC_POSE = 0x1620D2;
    // off_5E328 genuinely points into the Sonic 3 half for Tails.
    public static final int TAILS_POSE = 0x38C19A;
    public static final int MAP_SONIC_TAILS_POSE = 0x607E6;
    public static final int KNUCKLES_POSE = 0xDEA00;
    public static final int MAP_KNUCKLES_POSE = 0x60A4C;
    public static final int KNUCKLES_FLIGHT = 0x163B9A;
    public static final int MAP_KNUCKLES_FLIGHT = 0x602D6;
    public static final int S3_BANNER = 0x16461E;
    public static final int MAP_S3_BANNER = 0x60672;
    public static final int SK_BANNER = 0x16416C;
    public static final int MAP_SK_BANNER = 0x60644;
    public static final int AND_KNUCKLES = 0xDE362;
    public static final int MAP_AND_KNUCKLES = 0x60938;
    public static final int S3_ENDING_GRAPHICS = 0x241960;
    public static final int MAP_S3_ENDING_GRAPHICS = 0x24178A;
    public static final int EGGMAN_EYECATCH = 0x163246;
    public static final int MAP_EGGMAN_EYECATCH = 0x60A14;
    public static final int EGG_ROBO_EYECATCH = 0x16118E;
    public static final int MAP_EGG_ROBO_EYECATCH = 0x6096E;
    public static final int PAL_S3K_LOGO = 0x60ADA;
    public static final int PAL_SK_LOGO = 0x60ABA;
    public static final int PAL_SONIC_POSE = 0x60B6A;
    public static final int PAL_TAILS_POSE = 0x60B4A;
    public static final int PAL_SUPER_SONIC_POSE = 0x60B8A;
    public static final int PAL_KNUCKLES_POSE = 0x60BEA;
    public static final int PAL_EGGMAN_EYECATCH = 0x60BCA;
    public static final int PAL_EGG_ROBO_EYECATCH = 0x60BAA;
    public static final int PAL_SMALL_PLANE = 0x60A9A;
    public static final int PAL_EYECATCH_KNUCKLES = 0x60AFA; // loc_5F410's verified LEA
    public static final int PAL_TERMINAL_MASTER = 0x60B1A; // loc_5F8C6's verified LEA
    public static final int PAL_ENDING_ISLAND = 0xA97FC;
    public static final int PAL_ENDING_GOOD_ISLAND = 0xA985C;
    public static final int PAL_ENDING_SKY = 0x5B16E; // sub_5B11C's line 2 overwrite
    public static final int PAL_SSZ2_REUNION = 0xA97BC; // Pal_SSZ2+$20, sub_5B11C's line 3 overwrite
}
