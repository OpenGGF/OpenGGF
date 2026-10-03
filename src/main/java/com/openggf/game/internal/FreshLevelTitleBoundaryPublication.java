package com.openggf.game.internal;

/** Production title-loop permission to publish initialized positions before terrain loading finishes. */
public interface FreshLevelTitleBoundaryPublication {
    /** Pre-initialization capability used by the completed level-load fade callback. */
    default boolean installsImmediateFreshLevelPalette() {
        return false;
    }

    /** The native title loop starts on an installed palette, with no blocking reveal fade. */
    default boolean hasImmediateFreshLevelPalette() {
        return false;
    }

    boolean shouldPublishFreshLevelTransitionInitialBoundary();
}
