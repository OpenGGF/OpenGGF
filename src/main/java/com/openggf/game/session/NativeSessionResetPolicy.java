package com.openggf.game.session;

/** Native game initialization semantics, independent of the movie/controller format. */
public interface NativeSessionResetPolicy {
    enum Kind { WARM_RESET, COLD_POWER }

    /** Capture only the game-owned fields that survive this initialization path. */
    RetainedState capture(GameplayModeContext outgoing, Kind kind);

    @FunctionalInterface
    interface RetainedState {
        void restore(GameplayModeContext replacement);
    }
}
