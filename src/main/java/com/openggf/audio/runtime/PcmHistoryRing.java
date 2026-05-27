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

    /**
     * Writes {@code frames} stereo frames into the ring at logical indices
     * {@code [startAudioFrame, startAudioFrame + frames)}, where
     * {@code startAudioFrame + frames == cursor.oldestReadableFrame}. Lowers
     * the cursor's {@code oldestReadableFrame} so subsequent
     * {@link ReverseCursor#readPrevious} calls can drain the prepended range.
     *
     * <p>Invariants enforced:
     * <ol>
     *   <li>Adjacency: {@code startAudioFrame + frames == cursor.oldestReadableFrame}
     *       so the new range is contiguous below the current readable window.</li>
     *   <li>Physical-slot safety: the total span after prepend
     *       ({@code cursor.nextReadableFrame - startAudioFrame + 1}) must not
     *       exceed {@link #capacityFrames}, or the new writes would land in
     *       slots whose contents the cursor has not yet read.</li>
     * </ol>
     */
    public void prependBackward(long startAudioFrame, ReverseCursor cursor, short[] source, int frames) {
        if (cursor == null) {
            throw new IllegalArgumentException("cursor must not be null");
        }
        validateBuffer(source, frames);
        if (startAudioFrame + frames != cursor.oldestReadableFrame) {
            throw new IllegalArgumentException(
                    "prependBackward range must be adjacent to cursor.oldestReadableFrame; got start="
                            + startAudioFrame + " frames=" + frames
                            + " cursor.oldestReadableFrame=" + cursor.oldestReadableFrame);
        }
        long span = cursor.nextReadableFrame - startAudioFrame + 1;
        if (span > capacityFrames) {
            throw new IllegalArgumentException(
                    "prependBackward span would exceed ring capacity (would overwrite unread slots): span="
                            + span + " capacity=" + capacityFrames);
        }
        // Source is laid out in reverse-time order (newest-first), matching how
        // ReverseResynthesizer produces PCM. source[0] is the newest prepended frame
        // and lands at logical index (startAudioFrame + frames - 1); source[frames-1]
        // is the oldest and lands at logical index startAudioFrame.
        for (int i = 0; i < frames; i++) {
            long logicalIndex = startAudioFrame + (frames - 1 - i);
            int slot = ringSlot(logicalIndex) * CHANNELS;
            samples[slot] = source[i * CHANNELS];
            samples[slot + 1] = source[i * CHANNELS + 1];
        }
        cursor.extendOldestTo(startAudioFrame);
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
        private long oldestReadableFrame;

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

        public long oldestReadableFrame() {
            return oldestReadableFrame;
        }

        public long nextReadableFrame() {
            return nextReadableFrame;
        }

        /**
         * Returns the maximum number of frames a caller can safely
         * {@link PcmHistoryRing#prependBackward} given the current cursor
         * state. The ring is bounded; once the unread window equals
         * {@code capacityFrames}, no more frames can be prepended until the
         * cursor consumes some via {@link #readPrevious}. Returns 0 in that
         * case so the caller (typically {@code ReverseResynthesizer}) can
         * back off and let {@code drainPcm} make progress before retrying.
         */
        public int maxPrependableFrames() {
            long unread = nextReadableFrame - oldestReadableFrame + 1;
            long room = capacityFrames - unread;
            if (room <= 0) {
                return 0;
            }
            return (int) Math.min(room, (long) Integer.MAX_VALUE);
        }

        void extendOldestTo(long newOldest) {
            if (newOldest > oldestReadableFrame) {
                throw new IllegalArgumentException(
                        "extendOldestTo must lower oldestReadableFrame; got "
                                + newOldest + " > current " + oldestReadableFrame);
            }
            oldestReadableFrame = newOldest;
        }
    }
}
