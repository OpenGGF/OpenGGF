package com.openggf.level.animation;

/** Internal presentation port for a ROM-owned retained stage-ring animation clock. */
public interface StageRingAnimationFrameProvider {
    int stageRingAnimationFrame();

    /** Whether this loop dispatches the shared spilled-ring animation clock. */
    default boolean advancesSharedSpillAnimation() { return true; }
}
