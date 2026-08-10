package com.openggf.game.sonic3k.events;

public interface S3kTransitionEventBridge {
    default int resultsCreateGateDispatches() {
        return 9;
    }

    void signalActTransition();

    void requestHczPostTransitionCutscene();

    default boolean restorePendingPostResultsPlayerControl() {
        // Only transitions with a retained native end-sign owner consume it.
        return false;
    }

    /**
     * Runs the retained {@code Obj_EndSignControlDoStart -> Change_Act2Sizes}
     * handoff before the camera step on an in-level title-card completion.
     */
    default void preparePreloadedActTitleCardCompletion() {
    }

    /** Delegates Big Arm's falling-floor handoff to LBZ2's retained workers. */
    default void prepareLbzBigArmFloorTransition() {
    }

    /** Loads the native LBZ post-Big-Arm PLC before the replacement head. */
    default void loadLbzBigArmPostGatePlc() {
    }

    /** Publishes Big Arm's positive timed {@code Screen_shake_flag}. */
    default void startLbzBigArmTimedShake(int frames) {
    }

    /**
     * Runs any transition-specific handoff that the native owner publishes at
     * the title card's runtime-art admission boundary.
     */
    default void preparePreloadedActTitleCardRuntimeArtAdmission() {
    }

    /**
     * Additional title-owner polls retained after the shared drained-child
     * observation for a preloaded next-act handoff.
     */
    default int preloadedActCameraReleaseAdditionalDispatches() {
        return -1;
    }

    void requestMgzPostTransitionRelease();

    void requestCnzPostTransitionRelease();
}
