package com.openggf.level;

/** Internal camera setup access for an explicitly positioned gameplay entry. */
public final class LevelCameraInitialization {
    private LevelCameraInitialization() { }

    /**
     * Rebind bounds and placement around the caller's declared player position.
     * Cold ROM focus overrides belong only to a real level-load initialization.
     */
    public static void recenterPositionedEntry(LevelManager level) {
        level.initCameraForLevel(false);
    }
}
