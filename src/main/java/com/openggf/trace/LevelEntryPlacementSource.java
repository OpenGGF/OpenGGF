package com.openggf.trace;

/**
 * Recorder-observed {@code level_entry_placement_source}: which branch the ROM
 * took to place the player when the segment's level load began.
 *
 * <ul>
 *   <li>S3K: {@code loc_1BE46} tests {@code Last_star_post_hit}; zero reads the
 *       start-location table, non-zero calls {@code Load_Starpost_Settings},
 *       which restores the {@code Saved_*} star-post block, or the
 *       {@code Saved2_*} special-stage block when {@code Special_bonus_entry_flag}
 *       is set ({@code loc_2D2C2}).</li>
 *   <li>S2/KiS2: {@code LevelSizeLoad} tests {@code Last_star_pole_hit}; non-zero
 *       calls {@code Obj79_LoadData}. There is no third source.</li>
 * </ul>
 */
public enum LevelEntryPlacementSource {
    START_TABLE("start_table"),
    STARPOST_RESTORE("starpost_restore"),
    SPECIAL_STAGE_RESTORE("special_stage_restore");

    private final String wire;

    LevelEntryPlacementSource(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Whether the ROM restored a saved placement instead of the start table. */
    public boolean isRestore() {
        return this != START_TABLE;
    }

    static LevelEntryPlacementSource fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (LevelEntryPlacementSource source : values()) {
            if (source.wire.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException(
                "Unknown level_entry_placement_source '" + value + "'");
    }
}
