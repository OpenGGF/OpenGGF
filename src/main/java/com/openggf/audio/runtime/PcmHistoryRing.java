package com.openggf.audio.runtime;

import java.util.Arrays;

public final class PcmHistoryRing {
    private static final int CHANNELS = 2;

    private final short[] samples;
    private final int capacityFrames;
    private long nextFrameIndex;
    private int storedFrames;

    public PcmHistoryRing(int capacityFrames) {
        if (capacityFrames <= 0) {
            throw new IllegalArgumentException("capacityFrames must be positive");
        }
        this.capacityFrames = capacityFrames;
        this.samples = new short[capacityFrames * CHANNELS];
    }

    public void write(short[] source, int frames) {
        validateBuffer(source, frames);
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
