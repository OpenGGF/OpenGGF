package com.openggf.level.animation;

/** ROM-owned animation work after initial object dispatch, before gameplay ticks. */
public interface InitialLevelAnimationPass {
    void runInitialLevelAnimationPass();
}
