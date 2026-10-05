package com.openggf.game.rewind;

/**
 * A rewind the game itself drives, instead of the player holding the rewind key.
 *
 * <p>A module offers one through {@link com.openggf.game.GameModule#scriptedRewind()}.
 * While one is offered, live level play records rewind history even when the player's
 * live rewind setting is off. On each presentation frame the host asks
 * {@link #requested()}; while it is true the host rewinds with the ordinary
 * presentation (VHS picture, reverse audio), {@link #stepsThisFrame()} gameplay steps
 * at a time, checking {@link #reachedTarget()} against the restored state after every
 * step. The rewind ends at the first step that reaches the target, or at the oldest
 * frame still in history, and the host then calls {@link #ended(boolean)} once.
 *
 * <p>The implementation's own state must not be part of rewind: it decides where
 * the restored game stops, so the restore must not change it.
 */
@com.openggf.game.ModApi
public interface ScriptedRewind {
    /** True while the game should rewind. Checked once per presentation frame. */
    boolean requested();

    /** Backward steps to take this frame; zero holds the picture. */
    int stepsThisFrame();

    /** Checked on the just-restored state after every backward step: true stops there. */
    boolean reachedTarget();

    /**
     * The rewind is over and normal play resumes from the restored state. {@code atTarget}
     * is false when history ran out first. {@link #requested()} must be false afterwards.
     */
    void ended(boolean atTarget);
}
