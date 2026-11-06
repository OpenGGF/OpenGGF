package com.openggf.game.internal;

/** Internal score-counter bound supplied by the loaded game's semantic owner. */
@FunctionalInterface
public interface ScoreAdditionPolicy {
    // AddPoints stores units of ten; the HUD adds the final zero in both retail games:
    // S1 AddPoints (sub AddPoints.asm:18-58), S2 AddPoints (s2.asm:87804-87813).
    ScoreAdditionPolicy RETAIL_CAPPED = () -> 9_999_990;

    int maximumScore();
}
