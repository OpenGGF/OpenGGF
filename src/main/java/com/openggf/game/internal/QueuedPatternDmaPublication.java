package com.openggf.game.internal;

import com.openggf.game.resources.PlcLifecyclePhase;

/** Internal game-owned queued-pattern publication at an already claimed VBlank.
 * This observes the production loop classification; it supplies no gameplay or
 * reference values and does not add a hardware boundary or a public Mod API.
 */
public interface QueuedPatternDmaPublication {
    /** The frame's own runtime art coordinator and level, not ambient session globals. */
    void serviceQueuedPatternDma(PlcLifecyclePhase phase, boolean explicitDmaService,
            com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator,
            com.openggf.level.LevelManager levelManager);
}
