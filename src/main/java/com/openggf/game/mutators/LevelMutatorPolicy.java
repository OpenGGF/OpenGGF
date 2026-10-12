package com.openggf.game.mutators;

import java.util.Set;

/**
 * Immutable engine-owned level decisions. Placement classification and acquisition
 * domains stay with native producers; a stage puzzle never inherits a main-level
 * ring prohibition merely because it shares a counter implementation.
 */
public record LevelMutatorPolicy(Set<MonitorContent> removedMonitorContents,
                                 boolean noRings, boolean noCheckpoints,
                                 boolean noSpecialStages, boolean noBonusStages) {
    public static final LevelMutatorPolicy STOCK =
            new LevelMutatorPolicy(Set.of(), false, false, false, false);

    public LevelMutatorPolicy {
        removedMonitorContents = Set.copyOf(removedMonitorContents);
    }
}
