package com.openggf.mods;

import java.util.Arrays;
import java.util.Objects;

/** Exclusively owned, immutable-shape interleaved signed 16-bit PCM. */
public final class PcmData {
    private final int sampleRate;
    private final int channels;
    private final short[] ownedSamples;

    private PcmData(int sampleRate, int channels, short[] ownedSamples) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.ownedSamples = ownedSamples;
    }

    public static PcmData takeOwnership(int rate, int channels, short[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (rate < 8_000 || rate > 192_000) throw new IllegalArgumentException("sample rate must be in 8000..192000");
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("channels must be mono or stereo");
        if (samples.length == 0 || samples.length % channels != 0) {
            throw new IllegalArgumentException("PCM samples must contain complete nonempty frames");
        }
        Math.multiplyExact((long) samples.length, Short.BYTES);
        return new PcmData(rate, channels, samples);
    }

    public int sampleRate() { return sampleRate; }
    public int channels() { return channels; }
    public int sampleCount() { return ownedSamples.length; }
    public short[] copySamples() { return Arrays.copyOf(ownedSamples, ownedSamples.length); }
    short sampleAt(int index) { return ownedSamples[index]; }
    int frameCount() { return ownedSamples.length / channels; }
    long byteSize() { return Math.multiplyExact((long) ownedSamples.length, Short.BYTES); }
}
