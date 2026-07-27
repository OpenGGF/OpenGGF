package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import java.util.Objects;
import java.util.Set;

/** Immutable rewind state for the bounded hardware-timing replay ledger. */
public record HardwareTimingReplaySnapshot(
        HardwareTimingSchedule schedule,
        int edgeCursor,
        Set<String> consumedIdentities,
        Integer rawFrameLatch,
        HardwareServiceBoundary lastAppliedBoundary,
        boolean installed,
        boolean runComplete) {

    public HardwareTimingReplaySnapshot {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(consumedIdentities, "consumedIdentities");
        if (edgeCursor < 0 || edgeCursor > schedule.edges().size()) {
            throw new IllegalArgumentException(
                    "edgeCursor is outside the installed schedule: " + edgeCursor);
        }
        consumedIdentities = Set.copyOf(consumedIdentities);
    }
}
