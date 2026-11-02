package com.openggf.game.session;

/** Authority for entry placement; never inferred from coordinates or compared gameplay state. */
public enum LevelEntryProvenance {
    /** Production load owns placement, camera and native history initialization. */
    NATIVE_LEVEL_START,
    /** Production restore owns placement; reserved for explicit return provenance. */
    RETURN_LEVEL_START,
    /** Intentional positioning with no claim about fresh/return or spawn ordering. */
    POSITIONED_ENTRY,
    /** Fresh body immediately after SpawnPlayers, before any object or terrain dispatch. */
    POSITIONED_FRESH_SPAWN,
    /** Restored body immediately after SpawnPlayers, before any object or terrain dispatch. */
    POSITIONED_RETURN_SPAWN;

    public boolean isPositioned() {
        return this == POSITIONED_ENTRY || hasPostSpawnCameraOrigin();
    }

    public boolean hasPostSpawnCameraOrigin() {
        return this == POSITIONED_FRESH_SPAWN || this == POSITIONED_RETURN_SPAWN;
    }

    public boolean isReturn() {
        return this == RETURN_LEVEL_START || this == POSITIONED_RETURN_SPAWN;
    }
}
