package com.openggf.mods.scene;

/** A finite performance. Positions count stereo sample frames, and are negative during lead-in. */
@com.openggf.game.ModApi
public interface SceneMusicPlayer {
    long samplePosition();
    /** Maps an input timestamp from System.nanoTime's domain onto the audible clock. */
    long samplePositionAt(long monotonicNanos);
    void pause();
    void resume();
    void setPartAudible(boolean audible);
    /** A normalized [0,1] whammy amount; selected part only, backing stays on its clock. */
    void setWhammy(double amount);
    boolean finished();
    boolean paused();
    long underrunCount();
    void stop();
}
