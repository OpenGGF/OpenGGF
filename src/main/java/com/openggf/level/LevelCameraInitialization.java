package com.openggf.level;

/** Internal camera setup access for an explicitly positioned gameplay entry. */
public final class LevelCameraInitialization {
    private LevelCameraInitialization() { }

    /** Resnaps bounds and camera around a declared position, without cold-load focus overrides. */
    public static void recenterPositionedEntry(LevelManager level) {
        level.initCameraForLevel(false);
    }
}
