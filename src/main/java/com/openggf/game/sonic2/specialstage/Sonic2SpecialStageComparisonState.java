package com.openggf.game.sonic2.specialstage;

/**
 * Read-only per-frame snapshot of {@link Sonic2SpecialStageManager} state used by a
 * trace replay harness to compare engine state against a recorded ROM trace.
 * <p>
 * Produced exclusively by {@link Sonic2SpecialStageManager#captureComparisonState()}.
 * No mutators, no caching — every field is a pure read of existing manager/animator/
 * player state at the moment of the call.
 */
public record Sonic2SpecialStageComparisonState(
        int speedFactor,
        int currentSegmentIndex,
        int trackAnimFrame,        // animator.getCurrentFrameInSegment()
        int drawingIndex,          // manager drawingIndex field
        int trackFrameDelayCounter,// animator getFrameDelayCounter() (counts up 0..duration-1)
        int combinedRings,         // manager.getRingsCollected()
        int tailsControlCounter,
        boolean finished,
        PlayerState sonic,         // null if absent
        PlayerState tails) {       // null if absent

    public record PlayerState(int ssX, int ssY, int ssZ, int angle,
                               String routine, int routineSecondary,
                               int anim, int animFrame) {
    }
}
