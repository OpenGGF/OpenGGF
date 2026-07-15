package com.openggf.game.sonic3k.events;

public interface S3kTransitionEventBridge {
    void signalActTransition();

    void requestHczPostTransitionCutscene();

    default boolean restorePendingPostResultsPlayerControl() {
        // Only transitions with a retained native end-sign owner consume it.
        return false;
    }

    void requestMgzPostTransitionRelease();

    void requestCnzPostTransitionRelease(int framesUntilRelease);
}
