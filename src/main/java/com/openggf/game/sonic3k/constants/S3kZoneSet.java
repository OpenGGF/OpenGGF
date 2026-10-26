package com.openggf.game.sonic3k.constants;

/**
 * The locked-on S3K ROM uses two different object pointer tables that remap
 * many IDs depending on the current zone.
 *
 * <ul>
 *   <li><b>S3KL</b> — S3K-Level Object Set (disasm: "SK Set 1" / Sprite_Listing3):
 *       Zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ) and $0E-$15</li>
 *   <li><b>SKL</b> — SK-Level Object Set (disasm: "SK Set 2" / Sprite_ListingK):
 *       Zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ) and $16 onwards</li>
 * </ul>
 *
 * <p>The unsigned Current_zone byte selects the table at
 * loc_1B6A8 (sonic3k.asm:37451-37469), independently of game mode.
 */
public enum S3kZoneSet {
    S3KL,  // S3K-Level Object Set: Zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ)
    SKL;   // SK-Level Object Set: Zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ)

    public static S3kZoneSet forZone(int zone) {
        int nativeZone = zone & 0xFF;
        return nativeZone < 7 || nativeZone >= 0x0E && nativeZone < 0x16 ? S3KL : SKL;
    }
}
