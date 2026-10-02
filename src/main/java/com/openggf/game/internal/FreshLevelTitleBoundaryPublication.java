package com.openggf.game.internal;

/** Production title-loop permission to publish initialized positions before terrain loading finishes. */
public interface FreshLevelTitleBoundaryPublication {
    /** The native title loop starts on an installed palette, with no blocking reveal fade. */
    default boolean hasImmediateFreshLevelPalette() {
        return false;
    }

    boolean shouldPublishFreshLevelTransitionInitialBoundary();
}
