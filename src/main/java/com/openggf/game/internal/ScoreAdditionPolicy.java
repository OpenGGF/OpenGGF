package com.openggf.game.internal;

/** Internal score-counter bound supplied by the loaded game's semantic owner. */
@FunctionalInterface
public interface ScoreAdditionPolicy {
    // AddPoints stores units of ten; the HUD adds the final zero in both retail games:
    // S1 AddPoints (sub AddPoints.asm:18-58), S2 AddPoints (s2.asm:87804-87813).
    ScoreAdditionPolicy RETAIL_CAPPED = () -> 9_999_990;

    int maximumScore();

    /**
     * The score just reached {@code score} (after the cap). A game whose {@code AddPoints} awards a life at a
     * score threshold does it here; the default awards none.
     */
    default void scoreAdded(com.openggf.game.GameStateManager state, int score) { }
}
