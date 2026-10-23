package com.openggf.level;

import java.util.Objects;

/** Native load profiles install object/HUD art without admitting campaign player DPLCs. */
public final class LevelArtInternalAccess {
    private LevelArtInternalAccess() { }

    public static void initializeObjectArt(LevelManager level) {
        Objects.requireNonNull(level, "level").initObjectArt();
    }

    /** Install dual-camera ring cursors before the new manager's first placement reset. */
    public static void initializeNativeCompetitionRings(LevelManager level,
            com.openggf.level.rings.NativeCompetitionRingStreaming streaming) {
        LevelManagerInitializationSupport.initializeNativeCompetitionRings(
                Objects.requireNonNull(level, "level"), Objects.requireNonNull(streaming, "streaming"));
    }
}
