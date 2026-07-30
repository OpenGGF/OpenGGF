package com.openggf.game.sonic3k.constants;

/**
 * Zone ID constants for Sonic 3 &amp; Knuckles.
 * These match the ROM's zone numbering used for table indexing.
 */
public class Sonic3kZoneIds {

    private Sonic3kZoneIds() {}

    public static final int ZONE_AIZ = 0x00;  // Angel Island
    public static final int ZONE_HCZ = 0x01;  // Hydrocity
    public static final int ZONE_MGZ = 0x02;  // Marble Garden
    public static final int ZONE_CNZ = 0x03;  // Carnival Night
    public static final int ZONE_FBZ = 0x04;  // Flying Battery
    public static final int ZONE_ICZ = 0x05;  // IceCap
    public static final int ZONE_LBZ = 0x06;  // Launch Base
    public static final int ZONE_MHZ = 0x07;  // Mushroom Hill
    public static final int ZONE_SOZ = 0x08;  // Sandopolis
    public static final int ZONE_LRZ = 0x09;  // Lava Reef
    public static final int ZONE_SSZ = 0x0A;  // Sky Sanctuary
    public static final int ZONE_DEZ = 0x0B;  // Death Egg
    public static final int ZONE_DDZ = 0x0C;  // Doomsday

    // Special/bonus zones
    public static final int ZONE_HPZ = 0x16;  // Hidden Palace

    // Competition zones
    public static final int ZONE_ALZ = 0x0D;  // Azure Lake
    public static final int ZONE_BPZ = 0x0E;  // Balloon Park
    public static final int ZONE_DPZ = 0x0F;  // Desert Palace
    public static final int ZONE_CGZ = 0x10;  // Chrome Gadget
    public static final int ZONE_EMZ = 0x11;  // Endless Mine

    // Bonus stages
    public static final int ZONE_GUMBALL        = 0x13;  // Gumball Machine
    public static final int ZONE_GLOWING_SPHERE = 0x14;  // Glowing Spheres (Pachinko)
    public static final int ZONE_SLOT_MACHINE   = 0x15;  // Slot Machine

    /**
     * True for the zones that belong to Sonic &amp; Knuckles' own act list rather than
     * Sonic 3's — FBZ plus everything from MHZ onwards.
     *
     * <p>ROM: the {@code cmpi.b #4} / {@code cmpi.b #7} pair that {@code SSEntry_CheckLevel}
     * and the Special Stage results screen ({@code loc_2E540}) both run against the current
     * zone id. OpenGGF's S3K module targets the locked-on ROM, where {@code SK_alone_flag}
     * is always clear, so the standalone S&amp;K branch is intentionally non-applicable
     * rather than inferred from zone data.
     */
    public static boolean isSkSideZone(int zone) {
        return zone >= ZONE_MHZ || zone == ZONE_FBZ;
    }

    public static final int MAIN_ZONE_COUNT = 13; // AIZ through DDZ
    public static final int TOTAL_ZONE_COUNT = 23; // AIZ(0) through canonical HPZ(22), including gaps
}
