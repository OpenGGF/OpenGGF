package com.openggf.game.rewind.snapshot;

import com.openggf.graphics.FadeManager;

/**
 * Immutable capture of FadeManager state for rewind snapshots.
 * Captures fade phase, frame counter, color values, and hold duration.
 * Note: Callbacks (onFadeComplete) are transient and not captured -- but
 * {@link #hadPendingCompletion} remembers whether one was live at capture
 * time, so later consumers can tell a frame that never needed a callback
 * (e.g. a permanently-held fade whose callback already ran) apart from one
 * whose callback was orphaned by the restore. See {@link #isPoisoned()}.
 */
public record FadeManagerSnapshot(
        FadeManager.FadeState state,
        int frameCount,
        float fadeR,
        float fadeG,
        float fadeB,
        float fadeAlpha,
        FadeManager.FadeType fadeType,
        int holdDuration,
        int holdFrameCount,
        int effectiveFPC,
        float effectiveIncrement,
        int effectiveDuration,
        boolean hadPendingCompletion) {

    /**
     * True when this captured frame sat inside an in-flight, callback-bearing
     * fade -- {@code state != NONE} with {@link #hadPendingCompletion} true.
     * {@link FadeManager#restore(FadeManagerSnapshot)} always nulls the live
     * {@code onFadeComplete} (a transient closure, never restorable), so
     * committing to a poisoned frame permanently orphans whatever the
     * callback was going to do (e.g. advance a zone/act, request a special
     * stage) -- the fade is left stuck at full black/white forever with
     * nothing left to un-fade it. Rewind stepping/seeking must never let the
     * committed frame be one of these; see {@code RewindController}.
     */
    public boolean isPoisoned() {
        return state != FadeManager.FadeState.NONE && hadPendingCompletion;
    }
}
