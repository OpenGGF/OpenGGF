package com.openggf.trace.catalog;

import com.openggf.game.save.SelectedTeam;
import com.openggf.trace.TraceMetadata;

import java.nio.file.Path;

/**
 * One trace directory scanned by {@link TraceCatalog}. Constructed from
 * {@code metadata.json} + {@code physics.csv} row count + BK2 path.
 */
public record TraceEntry(
        Path dir,
        String gameId,
        int zone,
        int act,
        int frameCount,
        int bk2StartOffset,
        int preTraceOscFrames,
        SelectedTeam team,
        Path bk2Path,
        TraceMetadata metadata) {

    private static final String SPECIAL_STAGE_PROFILE = "s2_special_stage";

    /**
     * Human-readable label for this entry, used by the trace test-mode
     * picker. Special-stage traces (no meaningful zone/act) are labeled by
     * their 1-indexed {@code special_stage_index}; all other (level) traces
     * keep the zone/act-derived text.
     */
    public String displayLabel() {
        if (SPECIAL_STAGE_PROFILE.equals(metadata.traceProfile())) {
            Integer index = metadata.specialStageIndex();
            int oneIndexed = (index != null ? index : 0) + 1;
            return "S2 SPECIAL STAGE " + oneIndexed;
        }
        return String.format("Zone: %02X  Act: %d", zone, act);
    }
}
