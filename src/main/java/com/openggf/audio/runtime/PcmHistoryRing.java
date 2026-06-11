package com.openggf.audio.runtime;

import java.util.Arrays;

public final class PcmHistoryRing {
    private static final int CHANNELS = 2;

    private final short[] samples;
    private final int capacityFrames;
    private long nextFrameIndex;
    private int storedFrames;
    // Always equals ringSlot(nextFrameIndex); maintained incrementally so the
    // per-frame write path avoids a long floorMod inside the stream lock.
    private int writeSlot;

    public PcmHistoryRing(int capacityFrames) {
        if (capacityFrames <= 0) {
            throw new IllegalArgumentException("capacityFrames must be positive");
        }
        this.capacityFrames = capacityFrames;
        this.samples = new short[capacityFrames * CHANNELS];
    }

    /**
     * Resolve the ring capacity in stereo frames given user-configured
     * REWIND_AUDIO_HISTORY_LIMIT_TYPE / SECONDS / SIZE_MB values. When
     * {@code limitType} equals {@code "size"} the cap is computed from
     * {@code sizeMB} (assuming 16-bit stereo samples); otherwise the cap
     * is computed from {@code sampleRate * seconds}.
     */
    public static int capacityFramesFor(int sampleRate, String limitType, int seconds, int sizeMB) {
        if ("size".equalsIgnoreCase(limitType)) {
            long bytes = (long) Math.max(1, sizeMB) * 1024L * 1024L;
            long frames = bytes / (CHANNELS * (long) Short.BYTES);
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, frames));
        }
        return Math.max(1, sampleRate * Math.max(1, seconds));
    }

    public void write(short[] source, int frames) {
        validateBuffer(source, frames);
        int copied = 0;
        while (copied < frames) {
            int chunk = Math.min(frames - copied, capacityFrames - writeSlot);
            System.arraycopy(source, copied * CHANNELS, samples, writeSlot * CHANNELS, chunk * CHANNELS);
            writeSlot += chunk;
            if (writeSlot == capacityFrames) {
                writeSlot = 0;
            }
            copied += chunk;
        }
        nextFrameIndex += frames;
        storedFrames = (int) Math.min(capacityFrames, (long) storedFrames + frames);
    }

    public ReverseCursor createReverseCursor() {
        return new ReverseCursor(nextFrameIndex - 1, nextFrameIndex - storedFrames);
    }

    public void commitReverseCursor(ReverseCursor cursor) {
        if (cursor == null) {
            return;
        }
        long newNextFrameIndex = cursor.committedNextFrameIndex();
        long oldestRetainedFrame = cursor.oldestReadableFrame;
        nextFrameIndex = Math.max(oldestRetainedFrame, newNextFrameIndex);
        storedFrames = (int) Math.max(0, Math.min(capacityFrames, nextFrameIndex - oldestRetainedFrame));
        writeSlot = ringSlot(nextFrameIndex);
    }

    public void clear() {
        nextFrameIndex = 0;
        storedFrames = 0;
        writeSlot = 0;
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
        private double sourceFrame;
        private final long oldestReadableFrame;
        private double rate = 1.0;

        private ReverseCursor(long initialSourceFrame, long oldestReadableFrame) {
            this.sourceFrame = initialSourceFrame;
            this.oldestReadableFrame = oldestReadableFrame;
        }

        public void setRate(double rate) {
            if (Double.isNaN(rate) || rate <= 0.0) {
                this.rate = 1.0;
                return;
            }
            this.rate = rate;
        }

        public int readPrevious(short[] target, int frames) {
            validateBuffer(target, frames);
            int read = 0;
            while (read < frames) {
                long pickedFrame = (long) Math.floor(sourceFrame + 0.5);
                if (pickedFrame < oldestReadableFrame) {
                    break;
                }
                int sourceIndex = ringSlot(pickedFrame) * CHANNELS;
                int targetIndex = read * CHANNELS;
                target[targetIndex] = samples[sourceIndex];
                target[targetIndex + 1] = samples[sourceIndex + 1];
                sourceFrame -= rate;
                read++;
            }
            if (read < frames) {
                Arrays.fill(target, read * CHANNELS, frames * CHANNELS, (short) 0);
            }
            return read;
        }

        long committedNextFrameIndex() {
            return (long) Math.floor(sourceFrame + 0.5) + 1;
        }
    }
}
