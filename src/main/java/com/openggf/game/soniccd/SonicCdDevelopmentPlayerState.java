package com.openggf.game.soniccd;

import com.openggf.game.LevelState;

/** Absent native HUD/rings/timer for the named terrain PLAYER entry, with ordinary ticks admitted. */
final class SonicCdDevelopmentPlayerState implements LevelState {
    // Deliberate DEV omission. The source scroll's explicit HUD-time byte is zero.
    // Source player and V-int clocks are owned by the frame, never this HUD adapter.
    @Override public void update() { }
    @Override public int getRings() { return 0; }
    @Override public void addRings(int amount) { requireZero(amount, "rings"); }
    @Override public void setRings(int rings) { requireZero(rings, "rings"); }
    @Override public void setRingExtraLifeFlags(int flags) { requireZero(flags, "ring rewards"); }
    @Override public boolean isTimeOver() { return false; }
    @Override public String getDisplayTime() { return "0:00"; }
    @Override public boolean shouldFlashTimer() { return false; }
    @Override public boolean getFlashCycle() { return false; }
    @Override public void pauseTimer() { }
    @Override public void resumeTimer() { throw SonicCd.unsupported("native HUD timer in terrain-player admission"); }
    @Override public boolean isTimerPaused() { return true; }
    @Override public int getElapsedSeconds() { return 0; }
    @Override public long getTimerFrames() { return 0; }
    @Override public void setTimerFrames(long frames) { requireZero(frames, "native timer restore"); }

    private static void requireZero(long value, String operation) {
        if (value != 0) throw SonicCd.unsupported(operation);
    }
}
