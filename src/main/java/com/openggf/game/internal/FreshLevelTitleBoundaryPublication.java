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
     * Whether a fresh Level: entry's title owner deletes on its first dispatch,
     * before it creates archives or children, while the Level routine's title
     * and terrain waits still run. Such a destination suppresses only the
     * presentation; its terrain handoff and wait remain this owner's.
     */
    default boolean freshLevelTitleOwnerDeletesAtInit(int zoneIndex, int actIndex) {
        return false;
    }

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
