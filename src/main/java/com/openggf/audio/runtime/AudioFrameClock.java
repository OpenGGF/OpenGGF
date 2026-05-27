package com.openggf.audio.runtime;

public final class AudioFrameClock {
    private final int sampleRate;
    private final int frameRate;
    private long totalSamplesProduced;
    private int remainder;

    public AudioFrameClock(int sampleRate, int frameRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive");
        }
        this.sampleRate = sampleRate;
        this.frameRate = frameRate;
    }

    public int samplesForNextFrame() {
        int numerator = sampleRate + remainder;
        int samples = numerator / frameRate;
        remainder = numerator % frameRate;
        totalSamplesProduced += samples;
        return samples;
    }

    public Snapshot captureSnapshot() {
        return new Snapshot(sampleRate, frameRate, totalSamplesProduced, remainder);
    }

    public void restoreSnapshot(Snapshot snapshot) {
        if (snapshot.sampleRate() != sampleRate || snapshot.frameRate() != frameRate) {
            throw new IllegalArgumentException("snapshot clock rate does not match this clock");
        }
        totalSamplesProduced = snapshot.totalSamplesProduced();
        remainder = snapshot.remainder();
    }

    /** Resets the clock to fresh-boot state (0 samples, 0 remainder).
     *  Used by AudioManager.resetForLevelRewindSegment to give the
     *  level a clean audio-frame origin. */
    public void reset() {
        totalSamplesProduced = 0;
        remainder = 0;
    }

    public long totalSamplesProduced() {
        return totalSamplesProduced;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int remainder() {
        return remainder;
    }

    public record Snapshot(int sampleRate, int frameRate, long totalSamplesProduced, int remainder) {
    }
}
