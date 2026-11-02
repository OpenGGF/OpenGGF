package com.openggf.level;

import com.openggf.game.session.LevelEntryProvenance;
import java.util.Objects;

/** Internal entry assembly access; not part of the creator-facing LevelManager surface. */
public final class LevelEntryInitialization {
    private LevelEntryInitialization() { }

    public static void initializeCamera(LevelManager level, LevelEntryProvenance provenance) {
        Objects.requireNonNull(provenance, "provenance");
        if (provenance.isPositioned()) {
            level.initCameraForLevel(true, provenance);
        }
    }

    public static void repositionSidekicks(LevelManager level, int xOffset, int yOffset,
                                          LevelEntryProvenance provenance) {
        level.repositionSidekicks(xOffset, yOffset, provenance);
    }
}
