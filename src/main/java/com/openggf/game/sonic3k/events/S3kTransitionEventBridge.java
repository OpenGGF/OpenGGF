package com.openggf.game.sonic3k.events;

public interface S3kTransitionEventBridge {
    void signalActTransition();

    void requestHczPostTransitionCutscene();

    default void restorePendingPostResultsPlayerControl() {
        // Only transitions with a retained native end-sign owner consume it.
    }

    void requestMgzPostTransitionRelease();

    void requestCnzPostTransitionRelease(int framesUntilRelease);
}
