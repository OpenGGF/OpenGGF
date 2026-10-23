package com.openggf.level;

import java.util.Objects;

/** Native load profiles install object/HUD art without admitting campaign player DPLCs. */
public final class LevelArtInternalAccess {
    private LevelArtInternalAccess() { }

    public static void initializeObjectArt(LevelManager level) {
        Objects.requireNonNull(level, "level").initObjectArt();
    }
}
