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

    /**
     * Declares the entry kind of the next level load before it begins, so the
     * load's own entry branches (e.g. S3K SpawnLevelMainSprites' return gate)
     * see the same kind the ROM saw. Non-return kinds declare a fresh entry.
     */
    public static void declareNextLoad(LevelManager level, LevelEntryProvenance provenance) {
        level.getTransitions().declareNextLoadEntry(Objects.requireNonNull(provenance, "provenance"));
    }

    /** Whether the loaded level was declared a saved-placement return. */
    public static boolean isDeclaredReturnEntry(LevelManager level) {
        return level.getTransitions().isDeclaredReturnEntry();
    }

    public static void repositionSidekicks(LevelManager level, int xOffset, int yOffset,
                                          LevelEntryProvenance provenance) {
        level.repositionSidekicks(xOffset, yOffset, provenance);
    }
}
