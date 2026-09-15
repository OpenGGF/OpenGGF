package com.openggf.game.internal;

/** Production title-loop permission to publish initialized positions before terrain loading finishes. */
public interface FreshLevelTitleBoundaryPublication {
    boolean shouldPublishFreshLevelTransitionInitialBoundary();
}
