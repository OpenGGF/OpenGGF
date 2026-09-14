package com.openggf.game.internal;

import com.openggf.game.resources.PlcLifecyclePhase;

/** Internal game-owned queued-pattern publication at an already claimed VBlank.
 * This observes the production loop classification; it supplies no gameplay or
 * reference values and does not add a hardware boundary or a public Mod API.
 */
public interface QueuedPatternDmaPublication {
    void serviceQueuedPatternDma(PlcLifecyclePhase phase, boolean explicitDmaService);
}
