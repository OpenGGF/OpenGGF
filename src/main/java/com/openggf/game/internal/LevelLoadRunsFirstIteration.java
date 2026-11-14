package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Game service for a host whose level load also runs the level's first main-loop iteration
 * ({@link LoadedLevelFrameCounter} above 0): the frame that ran the load already shows that iteration, so the
 * production loop admits no further level iteration in the same frame. The load marks it; the production loop
 * forgets the mark when an iteration begins (a load outside the loop, as a test harness or a headless boot
 * performs, is not the loop's frame) and consumes it when it admits the level in the iteration that ran the load.
 */
public interface LevelLoadRunsFirstIteration {
    /** True once after a load ran the first iteration in the current loop iteration; clears the mark. */
    boolean consumeIterationRunByLoad();

    /** Forgets a mark left before the loop's current iteration began. */
    void forgetIterationRunByLoad();

    static LevelLoadRunsFirstIteration forHost(GameModule host) {
        return host == null ? null : host.getGameService(LevelLoadRunsFirstIteration.class);
    }
}
