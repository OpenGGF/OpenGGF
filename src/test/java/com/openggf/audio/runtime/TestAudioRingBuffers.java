package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Equivalence proofs for the cursor/arraycopy rewrites of {@link PcmHistoryRing}
 * and {@link AudioOutputFifo}. The reference classes are verbatim copies of the
 * per-frame modulo implementations; randomized operation sequences plus directed
 * wrap/exact-capacity cases must produce identical outputs, return values, and
 * counters. PcmHistoryRing is exercised through its only read path — reverse
 * cursors (held-rewind presentation reads) — including fractional rates and
 * cursor commits.
 */
class TestAudioRingBuffers {

    // ---------------------------------------------------------------------
    // PcmHistoryRing
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 64, 1024})
    void pcmHistoryRingFuzzMatchesReference(int capacity) {
        for (long seed = 1; seed <= 3; seed++) {
            PcmHistoryRing production = new PcmHistoryRing(capacity);
            ReferencePcmHistoryRing reference = new ReferencePcmHistoryRing(capacity);
            Random random = new Random(seed * 1000 + capacity);
            short next = 0;

            PcmHistoryRing.ReverseCursor prodCursor = null;
            ReferencePcmHistoryRing.ReverseCursor refCursor = null;

            for (int op = 0; op < 600; op++) {
                switch (random.nextInt(6)) {
                    case 0, 1 -> { // write a batch (occasionally larger than capacity)
                        int frames = random.nextInt(3) == 0
                                ? capacity + random.nextInt(capacity + 4)
                                : random.nextInt(2 * capacity + 1);
                        short[] data = new short[frames * 2];
                        for (int i = 0; i < data.length; i++) {
                            data[i] = next++;
                        }
                        production.write(data, frames);
                        reference.write(data, frames);
                        prodCursor = null;
                        refCursor = null;
                    }
                    case 2 -> { // open fresh reverse cursors
                        prodCursor = production.createReverseCursor();
                        refCursor = reference.createReverseCursor();
                        double rate = switch (random.nextInt(4)) {
                            case 0 -> 1.0;
                            case 1 -> 2.0;
                            case 2 -> 0.5;
                            default -> 0.25 + random.nextDouble() * 3.0;
                        };
                        prodCursor.setRate(rate);
                        refCursor.setRate(rate);
                    }
                    case 3, 4 -> { // reverse read
                        if (prodCursor != null) {
                            int frames = random.nextInt(capacity + 8);
                            short[] prodOut = new short[frames * 2];
                            short[] refOut = new short[frames * 2];
                            int prodRead = prodCursor.readPrevious(prodOut, frames);
                            int refRead = refCursor.readPrevious(refOut, frames);
                            assertEquals(refRead, prodRead, "reverse read count diverged at op " + op);
                            assertArrayEquals(refOut, prodOut, "reverse read PCM diverged at op " + op);
                        }
                    }
                    default -> { // commit or clear
                        if (prodCursor != null && random.nextBoolean()) {
                            production.commitReverseCursor(prodCursor);
                            reference.commitReverseCursor(refCursor);
                            prodCursor = null;
                            refCursor = null;
                        } else if (random.nextInt(8) == 0) {
                            production.clear();
                            reference.clear();
                            prodCursor = null;
                            refCursor = null;
                        }
                    }
                }
                assertFullReverseStateMatches(production, reference, capacity, "op " + op);
            }
        }
    }

    @Test
    void pcmHistoryRingExactCapacityAndRepeatedWrap() {
        int capacity = 16;
        PcmHistoryRing production = new PcmHistoryRing(capacity);
        ReferencePcmHistoryRing reference = new ReferencePcmHistoryRing(capacity);
        short next = 0;

        // no-wrap partial fill, exact-capacity write, then repeated wrapping writes.
        int[] writes = {5, capacity, 3, capacity, capacity * 2 + 1, 1, capacity - 1, capacity + 7};
        for (int frames : writes) {
            short[] data = new short[frames * 2];
            for (int i = 0; i < data.length; i++) {
                data[i] = next++;
            }
            production.write(data, frames);
            reference.write(data, frames);
            assertFullReverseStateMatches(production, reference, capacity, "after write of " + frames);
        }
    }

    @Test
    void pcmHistoryRingZeroFrameWriteAndEmptyReads() {
        PcmHistoryRing production = new PcmHistoryRing(8);
        ReferencePcmHistoryRing reference = new ReferencePcmHistoryRing(8);
        production.write(new short[0], 0);
        reference.write(new short[0], 0);
        assertFullReverseStateMatches(production, reference, 8, "after empty write");

        short[] prodOut = new short[12];
        short[] refOut = new short[12];
        int prodRead = production.createReverseCursor().readPrevious(prodOut, 6);
        int refRead = reference.createReverseCursor().readPrevious(refOut, 6);
        assertEquals(refRead, prodRead);
        assertArrayEquals(refOut, prodOut, "empty ring reverse read should zero-fill identically");
    }

    /** Drains the full retained history through fresh reverse cursors and compares. */
    private static void assertFullReverseStateMatches(
            PcmHistoryRing production, ReferencePcmHistoryRing reference, int capacity, String context) {
        short[] prodOut = new short[(capacity + 2) * 2];
        short[] refOut = new short[(capacity + 2) * 2];
        int prodRead = production.createReverseCursor().readPrevious(prodOut, capacity + 2);
        int refRead = reference.createReverseCursor().readPrevious(refOut, capacity + 2);
        assertEquals(refRead, prodRead, "retained frame count diverged " + context);
        assertArrayEquals(refOut, prodOut, "retained history diverged " + context);
    }

    // ---------------------------------------------------------------------
    // AudioOutputFifo
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 64, 1024})
    void audioOutputFifoFuzzMatchesReference(int capacity) {
        for (long seed = 1; seed <= 3; seed++) {
            AudioOutputFifo production = new AudioOutputFifo(capacity);
            ReferenceAudioOutputFifo reference = new ReferenceAudioOutputFifo(capacity);
            Random random = new Random(seed * 2000 + capacity);
            short next = 0;

            for (int op = 0; op < 800; op++) {
                if (random.nextBoolean()) {
                    int frames = random.nextInt(2) == 0
                            ? capacity + random.nextInt(capacity + 4)
                            : random.nextInt(capacity + 2);
                    short[] data = new short[frames * 2];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = next++;
                    }
                    assertEquals(reference.write(data, frames), production.write(data, frames),
                            "write return diverged at op " + op);
                } else if (random.nextInt(12) == 0) {
                    production.flush();
                    reference.flush();
                } else {
                    int frames = random.nextInt(capacity + 4);
                    short[] prodOut = new short[frames * 2];
                    short[] refOut = new short[frames * 2];
                    assertEquals(reference.drain(refOut, frames), production.drain(prodOut, frames),
                            "drain return diverged at op " + op);
                    assertArrayEquals(refOut, prodOut, "drained PCM diverged at op " + op);
                }
                assertEquals(reference.availableFrames(), production.availableFrames(),
                        "availableFrames diverged at op " + op);
                assertEquals(reference.underruns(), production.underruns(),
                        "underruns diverged at op " + op);
                assertEquals(reference.overruns(), production.overruns(),
                        "overruns diverged at op " + op);
            }
        }
    }

    @Test
    void audioOutputFifoExactCapacityWrapAndUnderrun() {
        int capacity = 8;
        AudioOutputFifo production = new AudioOutputFifo(capacity);
        ReferenceAudioOutputFifo reference = new ReferenceAudioOutputFifo(capacity);
        short next = 0;

        // exact-capacity fill, overrun write, partial drain, wrapping write, full
        // drain, then drain-on-empty underrun.
        int[][] script = {{1, capacity}, {1, 3}, {0, 5}, {1, 4}, {0, capacity}, {0, 2}};
        for (int[] step : script) {
            int frames = step[1];
            if (step[0] == 1) {
                short[] data = new short[frames * 2];
                for (int i = 0; i < data.length; i++) {
                    data[i] = next++;
                }
                assertEquals(reference.write(data, frames), production.write(data, frames));
            } else {
                short[] prodOut = new short[frames * 2];
                short[] refOut = new short[frames * 2];
                assertEquals(reference.drain(refOut, frames), production.drain(prodOut, frames));
                assertArrayEquals(refOut, prodOut);
            }
            assertEquals(reference.availableFrames(), production.availableFrames());
            assertEquals(reference.underruns(), production.underruns());
            assertEquals(reference.overruns(), production.overruns());
        }
    }

    // ---------------------------------------------------------------------
    // Verbatim reference copies of the pre-optimization implementations
    // ---------------------------------------------------------------------

    /**
     * FROZEN: verbatim copy of the pre-optimization PcmHistoryRing (per-frame
     * floorMod writes) — do not modify; it is the equivalence reference.
     */
    private static final class ReferencePcmHistoryRing {
        private static final int CHANNELS = 2;

        private final short[] samples;
        private final int capacityFrames;
        private long nextFrameIndex;
        private int storedFrames;

        ReferencePcmHistoryRing(int capacityFrames) {
            this.capacityFrames = capacityFrames;
            this.samples = new short[capacityFrames * CHANNELS];
        }

        void write(short[] source, int frames) {
            for (int frame = 0; frame < frames; frame++) {
                int sourceIndex = frame * CHANNELS;
                int targetIndex = ringSlot(nextFrameIndex) * CHANNELS;
                samples[targetIndex] = source[sourceIndex];
                samples[targetIndex + 1] = source[sourceIndex + 1];
                nextFrameIndex++;
                storedFrames = Math.min(capacityFrames, storedFrames + 1);
            }
        }

        ReverseCursor createReverseCursor() {
            return new ReverseCursor(nextFrameIndex - 1, nextFrameIndex - storedFrames);
        }

        void commitReverseCursor(ReverseCursor cursor) {
            if (cursor == null) {
                return;
            }
            long newNextFrameIndex = cursor.committedNextFrameIndex();
            long oldestRetainedFrame = cursor.oldestReadableFrame;
            nextFrameIndex = Math.max(oldestRetainedFrame, newNextFrameIndex);
            storedFrames = (int) Math.max(0, Math.min(capacityFrames, nextFrameIndex - oldestRetainedFrame));
        }

        void clear() {
            nextFrameIndex = 0;
            storedFrames = 0;
            Arrays.fill(samples, (short) 0);
        }

        private int ringSlot(long frameIndex) {
            return (int) Math.floorMod(frameIndex, capacityFrames);
        }

        final class ReverseCursor {
            private double sourceFrame;
            private final long oldestReadableFrame;
            private double rate = 1.0;

            private ReverseCursor(long initialSourceFrame, long oldestReadableFrame) {
                this.sourceFrame = initialSourceFrame;
                this.oldestReadableFrame = oldestReadableFrame;
            }

            void setRate(double rate) {
                if (Double.isNaN(rate) || rate <= 0.0) {
                    this.rate = 1.0;
                    return;
                }
                this.rate = rate;
            }

            int readPrevious(short[] target, int frames) {
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

    /**
     * FROZEN: verbatim copy of the pre-optimization AudioOutputFifo (per-frame
     * modulo write/drain) — do not modify; it is the equivalence reference.
     */
    private static final class ReferenceAudioOutputFifo {
        private static final int CHANNELS = 2;

        private final short[] samples;
        private final int capacityFrames;
        private int readFrame;
        private int writeFrame;
        private int availableFrames;
        private long underruns;
        private long overruns;

        ReferenceAudioOutputFifo(int capacityFrames) {
            this.capacityFrames = capacityFrames;
            this.samples = new short[capacityFrames * CHANNELS];
        }

        int write(short[] source, int frames) {
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

        int drain(short[] target, int frames) {
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

        void flush() {
            readFrame = 0;
            writeFrame = 0;
            availableFrames = 0;
        }

        int availableFrames() {
            return availableFrames;
        }

        long underruns() {
            return underruns;
        }

        long overruns() {
            return overruns;
        }
    }
}
