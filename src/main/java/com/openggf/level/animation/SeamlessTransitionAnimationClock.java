package com.openggf.level.animation;

/**
 * Animation state whose ROM update still runs after a seamless reload request
 * has replaced the level-owned managers.
 */
public interface SeamlessTransitionAnimationClock {
    void advanceForSeamlessTransition();

    /**
     * Called by the act-transition executor before a seamless reload replaces this
     * outgoing animator. A main loop that animates palettes ahead of the event work
     * performing the reload has already run that call for the outgoing act in the
     * same iteration; an animator that models it runs it now if it has not yet.
     */
    default void completePaletteAnimationBeforeSeamlessReload() { }
}
