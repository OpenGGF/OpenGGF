package com.openggf.game;

/** Engine-internal native state writes; this bridge is outside the Mod API surface. */
public final class NativeGameStateOps {
    private NativeGameStateOps() { }

    /** Clear the existing captured collection word without changing other session state. */
    public static void clearSpecialRingCollection(GameStateManager state) {
        state.clearSpecialRingCollection();
    }
    public static int specialRingCollection(GameStateManager state) {
        return state.specialRingCollection();
    }

    /** Transfers a completed native tally's stored score. */
    public static void restoreScore(GameStateManager state, int score) {
        state.restoreScore(score);
    }

    /** {@code Life_count} carried into a standalone trace segment. */
    public static void restoreLives(GameStateManager state, int lives) {
        state.restoreLives(lives);
    }

    /** Native special-stage awards increment the unsigned lives byte. */
    public static void awardByteLife(GameStateManager state) {
        state.restoreLives((state.getLives() + 1) & 0xFF);
    }

    public static void capContinues(GameStateManager state, int maximum) {
        state.capContinues(maximum);
    }

    public static void restoreSpecialStageSaveProgress(GameStateManager state, int rings, int stageIndex) {
        state.restoreSpecialStageSaveProgress(rings, stageIndex);
    }
}
