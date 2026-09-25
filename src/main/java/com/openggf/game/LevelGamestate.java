package com.openggf.game;

import com.openggf.audio.GameAudioProfile;

/**
 * Manages transient state for a single level execution, such as Rings and Time.
 * Typically reset when a level is loaded or restarted (except checkpoints?).
 * Fresh levels reset rings; checkpoint loading may reinstate the saved ring
 * and extra-life threshold bank according to the active game rules.
 */
public class LevelGamestate implements LevelState {
    private final LevelTimer timer;
    private int rings;
    private int ringExtraLifeFlags;
    private int displayedRings;
    private boolean ringDisplayDirty = true;
    private boolean ringDisplayLatched;


    public LevelGamestate() {
        this.timer = new LevelTimer();
        this.rings = 0;
        this.ringExtraLifeFlags = 0;
    }

    public void update() {
        timer.update();
    }

    public LevelTimer getTimer() {
        return timer;
    }

    public int getRings() {
        return rings;
    }

    public void setRings(int rings) {
        this.rings = Math.max(0, rings);
        ringDisplayDirty = true;
    }

    @Override
    public int getRingExtraLifeFlags() {
        return ringExtraLifeFlags;
    }

    @Override
    public void setRingExtraLifeFlags(int flags) {
        ringExtraLifeFlags = flags & 0x06;
    }

    @Override
    public void resetRingsForLoss() {
        rings = 0;
        ringDisplayDirty = true;
        ringExtraLifeFlags = 0;
    }

    public void addRings(int amount) {
        if (amount != 0) {
            int previousRings = rings;
            int next = rings + amount;
            this.rings = Math.max(0, next);
            ringDisplayDirty = true;

            // Ring Bonus Logic: 100 and 200 rings grant an extra life
            if (amount > 0) {
                int thresholdFlag = 0;
                if (previousRings < 100 && rings >= 100 && (ringExtraLifeFlags & 0x02) == 0) {
                    thresholdFlag = 0x02;
                } else if (previousRings < 200 && rings >= 200 && (ringExtraLifeFlags & 0x04) == 0) {
                    thresholdFlag = 0x04;
                }
                if (thresholdFlag != 0) {
                    ringExtraLifeFlags |= thresholdFlag;
                    GameServices.gameState().addLife();
                    GameAudioProfile profile = GameServices.audio().getAudioProfile();
                    if (profile != null) {
                        GameServices.audio().playMusic(profile.getExtraLifeMusicId());
                    }
                }
            }
        }
    }

    // A native HUD redraw flag is distinct from Ring_count. Ordinary mutators
    // request a redraw; script writes may deliberately leave the digits retained.
    int ringsForDisplay() { return ringDisplayLatched ? displayedRings : rings; }

    void writeRingsWithoutRefresh(int value) { rings = Math.max(0, value); }

    void publishRingDisplay() {
        ringDisplayLatched = true;
        if (ringDisplayDirty) {
            displayedRings = rings;
            ringDisplayDirty = false;
        }
    }

    LevelRingDisplay.State captureRingDisplay() {
        return new LevelRingDisplay.State(displayedRings, ringDisplayDirty, ringDisplayLatched);
    }

    void restoreRingDisplay(LevelRingDisplay.State state) {
        displayedRings = state.value();
        ringDisplayDirty = state.dirty();
        ringDisplayLatched = state.latched();
    }

    @Override
    public boolean isTimeOver() {
        return timer.isTimeOver();
    }

    @Override
    public String getDisplayTime() {
        return timer.getDisplayTime();
    }

    @Override
    public boolean shouldFlashTimer() {
        return timer.shouldFlash();
    }

    @Override
    public boolean getFlashCycle() {
        return timer.getFlashCycle();
    }

    @Override
    public void pauseTimer() {
        timer.pause();
    }

    @Override
    public void resumeTimer() {
        timer.resume();
    }

    @Override
    public boolean isTimerPaused() {
        return timer.isPaused();
    }

    @Override
    public int getElapsedSeconds() {
        return timer.getElapsedSeconds();
    }

    @Override
    public long getTimerFrames() {
        return timer.getTotalFrames();
    }

    @Override
    public void setTimerFrames(long frames) {
        timer.setTotalFrames(frames);
    }
}
