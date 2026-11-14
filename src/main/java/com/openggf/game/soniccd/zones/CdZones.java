package com.openggf.game.soniccd.zones;

import com.openggf.game.soniccd.CdZone;

/** The one table from round to zone fragment. Lanes edit their fragment, not this file. */
public final class CdZones {
    private CdZones() { }

    public static CdZoneFragment fragment(CdZone zone) {
        return switch (zone) {
            case PALMTREE_PANIC -> new com.openggf.game.soniccd.zones.ppz.PpzZone();
            case COLLISION_CHAOS -> new com.openggf.game.soniccd.zones.ccz.CczZone();
            case TIDAL_TEMPEST -> new com.openggf.game.soniccd.zones.ttz.TtzZone();
            case QUARTZ_QUADRANT -> new com.openggf.game.soniccd.zones.qqz.QqzZone();
            case WACKY_WORKBENCH -> new com.openggf.game.soniccd.zones.wwz.WwzZone();
            case STARDUST_SPEEDWAY -> new com.openggf.game.soniccd.zones.ssz.SszZone();
            case METALLIC_MADNESS -> new com.openggf.game.soniccd.zones.mmz.MmzZone();
        };
    }
}
