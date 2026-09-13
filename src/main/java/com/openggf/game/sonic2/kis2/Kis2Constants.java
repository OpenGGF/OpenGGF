package com.openggf.game.sonic2.kis2;

/**
 * Addresses and tables the Knuckles in Sonic 2 lock-on program reads.
 *
 * <p>Every entry cites {@code docs/kis2/BRANCH_DIFFS.md}. The lock-on address
 * space places the S&amp;K cart at {@code $000000-$1FFFFF}, the Sonic 2 cart at
 * {@code $200000-$2FFFFF} and the 256 KiB chip at {@code $300000-$33FFFF}
 * ({@code s2.lockon.asm}). Tier one reads only the first two windows.
 */
public final class Kis2Constants {

    private Kis2Constants() {
    }

    /** Patch identifier and built-in owner id. */
    public static final String PATCH_ID = "kis2";

    // ---- Lock-on address windows (s2.lockon.asm) ----
    public static final int SK_WINDOW_START = 0x000000;
    public static final int SK_WINDOW_END = 0x200000;
    public static final int S2_WINDOW_START = 0x200000;
    public static final int S2_WINDOW_END = 0x300000;
    public static final int CHIP_WINDOW_START = 0x300000;
    public static final int CHIP_WINDOW_END = 0x340000;

    // ---- S&K half (BRANCH_DIFFS.md §Which image holds what) ----

    /** {@code Off_Objects_KiS2} / S&amp;K {@code S2K_Sprite_Lists}: 34 longword pointers, index {@code zone*8 + act*4}. */
    public static final int OFF_OBJECTS_KIS2 = 0x0DF370;
    /** Longword entries in {@link #OFF_OBJECTS_KIS2} before the trailing boundary. */
    public static final int OFF_OBJECTS_KIS2_ENTRIES = 34;
    /** {@code ArtNem_EndingKnuckles} / S&amp;K {@code ArtNem_KnuxEndPose}. */
    public static final int ART_NEM_ENDING_KNUCKLES = 0x0DEA00;
    /** {@code ArtUnc_Knuckles}: 4092 uncompressed tiles in S3K palette indices. */
    public static final int ART_UNC_KNUCKLES = 0x1200E0;
    public static final int ART_UNC_KNUCKLES_SIZE = 0x1FF80;
    /** {@code MapUnc_Knuckles} (S3K mapping format, {@code SonicMappingsVer := 3}). */
    public static final int MAP_UNC_KNUCKLES = 0x14A8D6;
    /** {@code MapRUnc_Knuckles}: the DPLC table {@code LoadSonicDynPLC_Part2} walks. */
    public static final int MAP_RUNC_KNUCKLES = 0x14BD0A;
    /** S&amp;K {@code AniKnuckles}: byte-identical to KiS2's {@code SonicAniData} (37 scripts). */
    public static final int KNUCKLES_ANIM_DATA = 0x017EF4;
    public static final int KNUCKLES_ANIM_SCRIPT_COUNT = 37;
    /**
     * S&amp;K {@code Pal_KnuxEndPose}: the S2-layout Knuckles palette line
     * (indices 0-1 and 6-15 equal S2 {@code Pal_SonicTails}; 2-5 are Knuckles'
     * colours where {@link #ART_CONV_TABLE} places them). Verified on
     * 2026-09-13; see BRANCH_DIFFS.md "Palette verification".
     */
    public static final int PAL_KNUCKLES_S2_LAYOUT = 0x060BEA;
    /**
     * S&amp;K {@code ArtNem_KnucklesLifeIcon}. Tier-one stand-in for the chip's
     * "Knuckles lives counter.nem" ({@code ArtNem_Sonic_life_counter} in KiS2).
     */
    public static final int ART_NEM_KNUCKLES_LIFE_ICON = 0x190E4C;

    // ---- KiS2 SonicAniData slots (BRANCH_DIFFS.md §Animation) ----
    // The lock-on program replaces SonicAniData with the 37-entry KnucklesAni_*
    // table: entries 0-$1F keep Sonic 2's SonAni_* slot ids and $20-$24 add the
    // glide states. Ids are slot indexes into that table.
    public static final int ANIM_WALK = 0x00;          // SonAni_Walk_ptr -> KnucklesAni_Walk
    public static final int ANIM_RUN = 0x01;           // SonAni_Run_ptr -> KnucklesAni_Run
    public static final int ANIM_ROLL = 0x02;          // SonAni_Roll_ptr -> KnucklesAni_Roll
    public static final int ANIM_ROLL2 = 0x03;         // SonAni_Roll2_ptr -> KnucklesAni_Roll2
    public static final int ANIM_PUSH = 0x04;          // SonAni_Push_ptr -> KnucklesAni_Push
    public static final int ANIM_WAIT = 0x05;          // SonAni_Wait_ptr -> KnucklesAni_Wait
    public static final int ANIM_BALANCE = 0x06;       // SonAni_Balance_ptr -> KnucklesAni_Balance
    public static final int ANIM_LOOK_UP = 0x07;       // SonAni_LookUp_ptr -> KnucklesAni_LookUp
    public static final int ANIM_DUCK = 0x08;          // SonAni_Duck_ptr -> KnucklesAni_Duck
    public static final int ANIM_SPINDASH = 0x09;      // SonAni_Spindash_ptr -> KnucklesAni_Spindash
    public static final int ANIM_STOP = 0x0D;          // SonAni_Stop_ptr -> KnucklesAni_Stop (skid)
    public static final int ANIM_SPRING = 0x10;        // SonAni_Spring_ptr -> KnucklesAni_Spring
    public static final int ANIM_DROWN = 0x17;         // SonAni_Drown_ptr -> KnucklesAni_Drown
    public static final int ANIM_DEATH = 0x18;         // SonAni_Death_ptr -> KnucklesAni_Death
    public static final int ANIM_HURT = 0x1A;          // SonAni_Hurt2_ptr -> KnucklesAni_Hurt (HurtCharacter)
    public static final int ANIM_GLIDE = 0x20;         // KnuxAni_Glide_ptr -> KnucklesAni_Gliding
    public static final int ANIM_FALL_AFTER_GLIDE = 0x21; // KnuxAni_FallAfterGlide_ptr
    public static final int ANIM_CLIMB_LEDGE = 0x22;   // KnuxAni_ClimbLedge_ptr -> KnucklesAni_GetUp
    public static final int ANIM_LAND_AFTER_GLIDE = 0x23; // KnuxAni_LandAfterGlide_ptr -> KnucklesAni_HardFall
    public static final int ANIM_SHADOW_BOX = 0x24;    // KnuxAni_ShadowBox_ptr -> KnucklesAni_Badass

    /**
     * {@code ArtConvTable} (KiS2 {@code LoadSonicDynPLC_Part2},
     * {@code KPLC_ConvertArtFromS3K}): S3K palette index → S2-layout index,
     * applied to every Knuckles tile before it is DMA'd to
     * {@code ArtTile_ArtUnc_Sonic}. The ROM table is 256 bytes keyed by the
     * whole pixel pair; both nybbles use this 16-entry map.
     */
    public static final int[] ART_CONV_TABLE = {
        0x0, 0x6, 0x5, 0x3, 0x2, 0x4, 0xC, 0xD, 0xE, 0xF, 0xA, 0xB, 0x7, 0x8, 0x9, 0x1
    };
}
