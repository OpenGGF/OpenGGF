package com.openggf.audio.runtime;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestStreamBackedDeterministicAudioRuntime {
    @Test
    void normalFrameRendersMusicIntoOutputFifo() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        DeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(6, 3), fifo);
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20));

        runtime.advanceFrame(1, FrameAudioMode.NORMAL);

        short[] target = new short[4];
        assertEquals(true, runtime.providesPresentationPcm());
        assertEquals(2, runtime.drainPcm(target, 2));
        assertArrayEquals(new short[] {1, 10, 2, 20}, target);
    }

    @Test
    void silentStepAdvancesStreamsWithoutEnqueuingPresentationPcm() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(6, 3), fifo);
        SequenceStream stream = new SequenceStream(1, 10, 2, 20, 3, 30, 4, 40);
        runtime.setMusicStream(stream);

        runtime.advanceFrame(1, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);

        short[] target = new short[4];
        assertEquals(2, runtime.drainPcm(target, 2));
        assertArrayEquals(new short[] {3, 30, 4, 40}, target);
    }

    @Test
    void normalFrameMixesSfxIntoMusicWithClamping() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(6, 3), fifo);
        runtime.setMusicStream(new SequenceStream(30_000, -30_000, 1, 2));
        runtime.setSfxStream(new SequenceStream(10_000, -10_000, 3, 4));

        runtime.advanceFrame(1, FrameAudioMode.NORMAL);

        short[] target = new short[4];
        runtime.drainPcm(target, 2);
        assertArrayEquals(new short[] {Short.MAX_VALUE, Short.MIN_VALUE, 4, 6}, target);
    }

    @Test
    void presentationOnlyReverseDoesNotAdvanceStreamsOrClock() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(6, 3), fifo);
        SequenceStream stream = new SequenceStream(1, 10, 2, 20);
        runtime.setMusicStream(stream);

        runtime.advanceFrame(1, FrameAudioMode.PRESENTATION_ONLY_REVERSE);

        assertEquals(0, stream.samplesRead);
        assertEquals(0, runtime.totalSamplesProduced());
    }

    @Test
    void variableFrameSizesReadOnlyTheCurrentFrameWindow() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(5, 2), fifo);
        SequenceStream stream = new SequenceStream(1, 10, 2, 20, 3, 30, 4, 40, 5, 50, 6, 60, 7, 70);
        runtime.setMusicStream(stream);

        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);
        runtime.advanceFrame(3, FrameAudioMode.NORMAL);

        short[] target = new short[14];
        assertEquals(7, runtime.drainPcm(target, 7));
        assertEquals(14, stream.samplesRead);
        assertArrayEquals(new short[] {1, 10, 2, 20, 3, 30, 4, 40, 5, 50, 6, 60, 7, 70}, target);
    }

    @Test
    void consumesSubmittedFrameCommandsInOrderBeforeRenderingFrame() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo);
        List<String> calls = new ArrayList<>();
        runtime.setCommandHandler(command -> calls.add(((AudioCommand.PlaySfx) command).sfxName()));
        runtime.setMusicStream(new CallbackStream(calls, "read"));

        runtime.submit(new AudioTimelineEntry(4, 1,
                new AudioCommand.PlaySfx(-1, "B", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null)));
        runtime.submit(new AudioTimelineEntry(4, 0,
                new AudioCommand.PlaySfx(-1, "A", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null)));

        runtime.advanceFrame(4, FrameAudioMode.NORMAL);

        assertEquals(List.of("A", "B", "read"), calls);
    }

    @Test
    void consumesSubmittedCommandsAcrossFramesInTimelineOrder() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo);
        List<String> calls = new ArrayList<>();
        runtime.setCommandHandler(command -> calls.add(((AudioCommand.PlaySfx) command).sfxName()));

        runtime.submit(new AudioTimelineEntry(5, 0,
                new AudioCommand.PlaySfx(-1, "C", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null)));
        runtime.submit(new AudioTimelineEntry(4, 1,
                new AudioCommand.PlaySfx(-1, "B", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null)));
        runtime.submit(new AudioTimelineEntry(4, 0,
                new AudioCommand.PlaySfx(-1, "A", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null)));

        runtime.advanceFrame(4, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(5, FrameAudioMode.SILENT_STEP);

        assertEquals(List.of("A", "B", "C"), calls);
    }

    @Test
    void normalFramesMirrorFinalMixedPcmIntoReverseHistory() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4));
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20, 3, 30, 4, 40));
        runtime.setSfxStream(new SequenceStream(100, 1000, 200, 2000, 300, 3000, 400, 4000));

        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();

        short[] target = new short[8];
        assertEquals(4, runtime.drainPcm(target, 4));
        assertArrayEquals(new short[] {404, 4040, 303, 3030, 202, 2020, 101, 1010}, target);
    }

    @Test
    void silentStepDoesNotEnterReverseHistory() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4));
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20, 3, 30, 4, 40));

        runtime.advanceFrame(1, FrameAudioMode.SILENT_STEP);
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();

        short[] target = new short[8];
        assertEquals(2, runtime.drainPcm(target, 4));
        assertArrayEquals(new short[] {4, 40, 3, 30, 0, 0, 0, 0}, target);
    }

    @Test
    void reverseReleaseCrossfadesFromLastReverseFrameIntoForwardPcm() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4), 2);
        runtime.setMusicStream(new SequenceStream(0, 0, 1000, 1000));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();

        short[] reverse = new short[2];
        assertEquals(1, runtime.drainPcm(reverse, 1));
        assertArrayEquals(new short[] {1000, 1000}, reverse);

        runtime.endReversePresentation();

        short[] forward = new short[4];
        assertEquals(2, runtime.drainPcm(forward, 2));
        assertArrayEquals(new short[] {500, 500, 1000, 1000}, forward);
    }

    @Test
    void releaseCrossfadeWaitsForActualForwardFifoFrames() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4), 2);
        runtime.setMusicStream(new SequenceStream(0, 0, 1000, 1000));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();
        short[] reverse = new short[2];
        runtime.drainPcm(reverse, 1);
        runtime.endReversePresentation();
        runtime.flushPresentationFifo();

        short[] underrun = new short[4];
        assertEquals(0, runtime.drainPcm(underrun, 2));
        assertArrayEquals(new short[] {0, 0, 0, 0}, underrun);

        runtime.setMusicStream(new SequenceStream(0, 0, 1000, 1000));
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);

        short[] forward = new short[4];
        assertEquals(2, runtime.drainPcm(forward, 2));
        assertArrayEquals(new short[] {500, 500, 1000, 1000}, forward);
    }

    @Test
    void reverseSessionWithoutOutputDoesNotReusePreviousReleaseSample() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4), 2);
        runtime.setMusicStream(new SequenceStream(1000, 1000));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();
        short[] reverse = new short[2];
        runtime.drainPcm(reverse, 1);
        runtime.endReversePresentation();

        runtime.beginReversePresentation();
        runtime.endReversePresentation();
        runtime.flushPresentationFifo();
        runtime.setMusicStream(new SequenceStream(0, 0, 1000, 1000));
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);

        short[] forward = new short[4];
        assertEquals(2, runtime.drainPcm(forward, 2));
        assertArrayEquals(new short[] {0, 0, 1000, 1000}, forward);
    }

    @Test
    void clearingPcmHistoryCancelsPendingReleaseCrossfade() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(4), 2);
        runtime.setMusicStream(new SequenceStream(1000, 1000));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.beginReversePresentation();
        short[] reverse = new short[2];
        runtime.drainPcm(reverse, 1);

        runtime.clearPcmHistory();
        runtime.flushPresentationFifo();
        runtime.setMusicStream(new SequenceStream(0, 0, 1000, 1000));
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);

        short[] forward = new short[4];
        assertEquals(2, runtime.drainPcm(forward, 2));
        assertArrayEquals(new short[] {0, 0, 1000, 1000}, forward);
    }

    private static final class SequenceStream implements AudioStream {
        private final short[] samples;
        private int cursor;
        private int samplesRead;

        private SequenceStream(int... samples) {
            this.samples = new short[samples.length];
            for (int i = 0; i < samples.length; i++) {
                this.samples[i] = (short) samples[i];
            }
        }

        @Override
        public int read(short[] buffer) {
            int count = Math.min(buffer.length, samples.length - cursor);
            System.arraycopy(samples, cursor, buffer, 0, count);
            cursor += count;
            samplesRead += count;
            return count;
        }
    }

    private static final class CallbackStream implements AudioStream {
        private final List<String> calls;
        private final String label;

        private CallbackStream(List<String> calls, String label) {
            this.calls = calls;
            this.label = label;
        }

        @Override
        public int read(short[] buffer) {
            calls.add(label);
            Arrays.fill(buffer, (short) 1);
            return buffer.length;
        }
    }
}
