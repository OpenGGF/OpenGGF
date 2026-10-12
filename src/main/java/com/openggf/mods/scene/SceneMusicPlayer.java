package com.openggf.mods.scene;

/** A finite performance. Positions count stereo sample frames, and are negative during lead-in. */
@com.openggf.game.ModApi
public interface SceneMusicPlayer {
    /** Source selector for a cue continuing the selected part at the next render cursor. */
    long PLAYHEAD = Long.MIN_VALUE;
    /**
     * Mix a short enveloped fragment of the selected ROM part into this performance.
     * Source is a song sample or PLAYHEAD; duration is 1..sampleRate/4, rates 0.5..2,
     * gain 0..1 and stereo balance -1..1, all finite. The rate glides linearly.
     * Cues ignore whammy, preserve ROM stereo and do not alter the song clock.
     * Six voices are available; returns false when paused, stopped, exhausted or full,
     * or when PLAYHEAD is requested before the song starts.
     * Pause freezes accepted cues; stop discards them. The finite song tail fades
     * and discards any remaining cues. Unsupported hosts return false
     * without validating cue arguments.
     * @throws IllegalArgumentException if a supporting host receives malformed arguments
     */
    default boolean cuePart(long sourceSample, int durationSamples, double startRate, double endRate,
                            double gain, double pan) { return false; }
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
