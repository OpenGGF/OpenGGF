package com.openggf.audio.runtime;

import java.util.Arrays;

public final class PcmHistoryRing {
    private static final int CHANNELS = 2;

    private short[] samples;
    private int capacityFrames;
    private final boolean expandable;
    private final int maxCapacityFrames;
    private long nextFrameIndex;
    private int storedFrames;

    public PcmHistoryRing(int capacityFrames) {
        this(capacityFrames, false, capacityFrames);
    }

    private PcmHistoryRing(int capacityFrames, boolean expandable, int maxCapacityFrames) {
        if (capacityFrames <= 0) {
            throw new IllegalArgumentException("capacityFrames must be positive");
        }
        if (maxCapacityFrames < capacityFrames) {
            throw new IllegalArgumentException("maxCapacityFrames must be >= capacityFrames");
        }
        this.capacityFrames = capacityFrames;
        this.samples = new short[capacityFrames * CHANNELS];
        this.expandable = expandable;
        this.maxCapacityFrames = maxCapacityFrames;
    }

    public static PcmHistoryRing expandable(int initialCapacityFrames, int maxCapacityFrames) {
        return new PcmHistoryRing(initialCapacityFrames, true, maxCapacityFrames);
    }

    public void write(short[] source, int frames) {
        validateBuffer(source, frames);
        ensureWriteCapacity(frames);
        for (int frame = 0; frame < frames; frame++) {
            int sourceIndex = frame * CHANNELS;
            int targetIndex = ringSlot(nextFrameIndex) * CHANNELS;
            samples[targetIndex] = source[sourceIndex];
            samples[targetIndex + 1] = source[sourceIndex + 1];
            nextFrameIndex++;
            storedFrames = Math.min(capacityFrames, storedFrames + 1);
        }
    }

    public ReverseCursor createReverseCursor() {
        return new ReverseCursor(nextFrameIndex - 1, nextFrameIndex - storedFrames);
    }

    public void commitReverseCursor(ReverseCursor cursor) {
        if (cursor == null) {
            return;
        }
        long newNextFrameIndex = cursor.nextReadableFrame + 1;
        long oldestRetainedFrame = cursor.oldestReadableFrame;
        nextFrameIndex = Math.max(oldestRetainedFrame, newNextFrameIndex);
        storedFrames = (int) Math.max(0, Math.min(capacityFrames, nextFrameIndex - oldestRetainedFrame));
    }

    public void clear() {
        nextFrameIndex = 0;
        storedFrames = 0;
        Arrays.fill(samples, (short) 0);
    }

    private int ringSlot(long frameIndex) {
        return (int) Math.floorMod(frameIndex, capacityFrames);
    }

    private void ensureWriteCapacity(int frames) {
        if (!expandable || frames <= 0 || storedFrames + frames <= capacityFrames
                || capacityFrames >= maxCapacityFrames) {
            return;
        }
        int newCapacity = capacityFrames;
        int required = Math.min(maxCapacityFrames, storedFrames + frames);
        while (newCapacity < required) {
            if (newCapacity > Integer.MAX_VALUE / 2) {
                newCapacity = required;
                break;
            }
            newCapacity *= 2;
        }
        newCapacity = Math.min(newCapacity, maxCapacityFrames);
        short[] newSamples = new short[newCapacity * CHANNELS];
        long oldestFrame = nextFrameIndex - storedFrames;
        int oldCapacity = capacityFrames;
        short[] oldSamples = samples;
        for (int offset = 0; offset < storedFrames; offset++) {
            long frameIndex = oldestFrame + offset;
            int oldSlot = (int) Math.floorMod(frameIndex, oldCapacity) * CHANNELS;
            int newSlot = (int) Math.floorMod(frameIndex, newCapacity) * CHANNELS;
            newSamples[newSlot] = oldSamples[oldSlot];
            newSamples[newSlot + 1] = oldSamples[oldSlot + 1];
        }
        samples = newSamples;
        capacityFrames = newCapacity;
    }

    private static void validateBuffer(short[] buffer, int frames) {
        if (frames < 0) {
            throw new IllegalArgumentException("frames must be non-negative");
        }
        if (buffer.length < frames * CHANNELS) {
            throw new IllegalArgumentException("buffer is too small for requested stereo frames");
        }
    }

    public final class ReverseCursor {
        private long nextReadableFrame;
        private final long oldestReadableFrame;

        private ReverseCursor(long nextReadableFrame, long oldestReadableFrame) {
            this.nextReadableFrame = nextReadableFrame;
            this.oldestReadableFrame = oldestReadableFrame;
        }

        public int readPrevious(short[] target, int frames) {
            validateBuffer(target, frames);
            int read = 0;
            while (read < frames && nextReadableFrame >= oldestReadableFrame) {
                int sourceIndex = ringSlot(nextReadableFrame) * CHANNELS;
                int targetIndex = read * CHANNELS;
                target[targetIndex] = samples[sourceIndex];
                target[targetIndex + 1] = samples[sourceIndex + 1];
                nextReadableFrame--;
                read++;
            }
            if (read < frames) {
                Arrays.fill(target, read * CHANNELS, frames * CHANNELS, (short) 0);
            }
            return read;
        }
    }
}
