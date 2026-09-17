package com.openggf.level.rings;

/**
 * Engine-internal access to a full {@code Ring_status_table} wipe for zone code whose native loop
 * clears it in place, such as the Doomsday Zone level wrap ({@code loc_81726}, sonic3k.asm:173370-173378).
 * Not part of the Mod API.
 */
public final class RingStatusTableWipe {
    private RingStatusTableWipe() {
    }

    public static void wipe(RingManager ringManager) {
        if (ringManager != null) {
            ringManager.wipeRingStatusTable();
        }
    }
}
