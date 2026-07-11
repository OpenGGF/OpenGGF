package com.openggf.game.timeattack;

/** Immutable HUD snapshot consumed by the overlay each frame. */
@com.openggf.game.ModApi
public record TimeAttackHudState(boolean active, int elapsedDisplayFrames, int bestTimeFrames,
                                 int lastSplitDelta, boolean finished, boolean newBest) {
    public static final TimeAttackHudState INACTIVE =
            new TimeAttackHudState(false, 0, -1, Integer.MIN_VALUE, false, false);
}
