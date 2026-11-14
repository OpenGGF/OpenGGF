package com.openggf.game.soniccd;

/** Native round names retain the missing R2 slot. */
public enum CdZone {
    PALMTREE_PANIC(1), COLLISION_CHAOS(3), TIDAL_TEMPEST(4), QUARTZ_QUADRANT(5),
    WACKY_WORKBENCH(6), STARDUST_SPEEDWAY(7), METALLIC_MADNESS(8);

    private final int nativeRound;

    CdZone(int nativeRound) {
        this.nativeRound = nativeRound;
    }

    public int nativeRound() {
        return nativeRound;
    }

    public static CdZone fromNativeRound(int round) {
        for (CdZone zone : values()) {
            if (zone.nativeRound == round) {
                return zone;
            }
        }
        throw new IllegalArgumentException("Unknown CD round: " + round);
    }
}
