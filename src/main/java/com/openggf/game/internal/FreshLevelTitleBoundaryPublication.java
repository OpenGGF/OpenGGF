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

    /**
     * Whether the dispatch that observes this owner's fresh-level release is
     * itself a serviced loop iteration whose V-int the object clock still owes.
     * S3K's terrain wait ends inside LoadLevelLoadBlock's loc_7870 loop, after
     * that iteration's Wait_VSync; owners without such a loop keep the default.
     */
    default boolean releaseScanServicesLoadLoopVBlank() {
        return false;
    }
}
