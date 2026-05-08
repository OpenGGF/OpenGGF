package com.openggf.audio.runtime;

import java.util.Arrays;

public final class AudioOutputFifo {
    private static final int CHANNELS = 2;

    private final short[] samples;
    private final int capacityFrames;
    private int readFrame;
    private int writeFrame;
    private int availableFrames;
    private long underruns;
    private long overruns;

    public AudioOutputFifo(int capacityFrames) {
        if (capacityFrames <= 0) {
            throw new IllegalArgumentException("capacityFrames must be positive");
        }
        this.capacityFrames = capacityFrames;
        this.samples = new short[capacityFrames * CHANNELS];
    }

    public int write(short[] source, int frames) {
        validateBuffer(source, frames);
        int writable = Math.min(frames, capacityFrames - availableFrames);
        if (writable < frames) {
            overruns++;
        }
        for (int frame = 0; frame < writable; frame++) {
            int sourceIndex = frame * CHANNELS;
            int targetIndex = writeFrame * CHANNELS;
            samples[targetIndex] = source[sourceIndex];
            samples[targetIndex + 1] = source[sourceIndex + 1];
            writeFrame = (writeFrame + 1) % capacityFrames;
        }
        availableFrames += writable;
        return writable;
    }

    public int drain(short[] target, int frames) {
        validateBuffer(target, frames);
        int readable = Math.min(frames, availableFrames);
        for (int frame = 0; frame < readable; frame++) {
            int sourceIndex = readFrame * CHANNELS;
            int targetIndex = frame * CHANNELS;
            target[targetIndex] = samples[sourceIndex];
            target[targetIndex + 1] = samples[sourceIndex + 1];
            readFrame = (readFrame + 1) % capacityFrames;
        }
        availableFrames -= readable;

        if (readable < frames) {
            Arrays.fill(target, readable * CHANNELS, frames * CHANNELS, (short) 0);
            underruns++;
        }
        return readable;
    }

    public void flush() {
        readFrame = 0;
        writeFrame = 0;
        availableFrames = 0;
    }

    public int availableFrames() {
        return availableFrames;
    }

    public long underruns() {
        return underruns;
    }

    public long overruns() {
        return overruns;
    }

    private static void validateBuffer(short[] buffer, int frames) {
        if (frames < 0) {
            throw new IllegalArgumentException("frames must be non-negative");
        }
        if (buffer.length < frames * CHANNELS) {
            throw new IllegalArgumentException("buffer is too small for requested stereo frames");
        }
    }
}
