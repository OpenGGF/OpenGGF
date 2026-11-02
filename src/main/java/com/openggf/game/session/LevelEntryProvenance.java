package com.openggf.game.session;

/** Authority for entry placement; never inferred from coordinates or compared gameplay state. */
public enum LevelEntryProvenance {
    /** Production load owns player placement, camera and native history initialization. */
    NATIVE_LEVEL_START,
    /** Production restore owns placement. Reserved for explicitly supplied return provenance. */
    RETURN_LEVEL_START,
    /** A caller intentionally places an assembled team without repeating native initialization. */
    POSITIONED_ENTRY
}
