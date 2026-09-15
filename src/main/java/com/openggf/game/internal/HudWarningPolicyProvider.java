package com.openggf.game.internal;

/** Engine-owned HUD warning eligibility and phase, independent of the timer's blink clock. */
public interface HudWarningPolicyProvider {
    boolean isFlashFrame(int levelFrameCounter);
    boolean isTimerWarning(int elapsedSeconds);
}
