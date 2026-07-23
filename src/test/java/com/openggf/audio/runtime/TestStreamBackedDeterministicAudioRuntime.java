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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void liveTapDrainLeavesSpeakerFifoByteIdentical() {
        AudioOutputFifo fifo = new AudioOutputFifo(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), fifo, new PcmHistoryRing(8));
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(2, 1);
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20));
        runtime.setSfxStream(new SequenceStream(100, 1000, 200, 2000));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);

        assertEquals(2, capture.sampleRate());
        assertEquals(1, capture.frameRate());
        assertEquals(2, capture.maxStereoFramesPerPacket());
        short[] captured = new short[4];
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {101, 1010, 202, 2020}, captured);
        assertEquals(2, fifo.availableFrames());

        short[] speaker = new short[4];
        assertEquals(2, runtime.drainPcm(speaker, 2));
        assertArrayEquals(new short[] {101, 1010, 202, 2020}, speaker);
    }

    @Test
    void liveTapUsesIndependentReverseCursor() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHistory(
                1, 1, 8, 1, 10, 2, 20, 3, 30, 4, 40);
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(1, 1);
        runtime.beginReversePresentation();

        short[] speakerFirst = new short[2];
        short[] captureFirst = new short[2];
        short[] speakerSecond = new short[2];
        short[] captureSecond = new short[2];

        assertEquals(1, runtime.drainPcm(speakerFirst, 1));
        assertEquals(1, capture.drainPresentationFrame(captureFirst));
        assertEquals(1, runtime.drainPcm(speakerSecond, 1));
        assertEquals(1, capture.drainPresentationFrame(captureSecond));

        assertArrayEquals(new short[] {4, 40}, speakerFirst);
        assertArrayEquals(new short[] {4, 40}, captureFirst);
        assertArrayEquals(new short[] {3, 30}, speakerSecond);
        assertArrayEquals(new short[] {3, 30}, captureSecond);
    }

    @Test
    void attachingAfterSpeakerAdvancedReverseForksExactCursorPosition() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHistory(
                1, 1, 8, 1, 10, 2, 20, 3, 30, 4, 40);
        runtime.beginReversePresentation();
        short[] speaker = new short[4];
        assertEquals(2, runtime.drainPcm(speaker, 2));
        assertArrayEquals(new short[] {4, 40, 3, 30}, speaker);

        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(1, 1);
        short[] captured = new short[2];

        assertEquals(1, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {2, 20}, captured);
    }

    @Test
    void reverseRateIsMirroredToCaptureCursor() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHistory(
                2, 1, 8, 1, 10, 2, 20, 3, 30, 4, 40, 5, 50, 6, 60);
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(2, 1);
        runtime.beginReversePresentation();
        runtime.setReversePlaybackRate(2.0);

        short[] speaker = new short[4];
        short[] captured = new short[4];

        assertEquals(2, runtime.drainPcm(speaker, 2));
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {6, 60, 4, 40}, speaker);
        assertArrayEquals(new short[] {6, 60, 4, 40}, captured);
    }

    @Test
    void captureCursorDoesNotCommitLiveHistoryOnRelease() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHistory(
                2, 1, 8, 1, 10, 2, 20, 3, 30, 4, 40, 5, 50, 6, 60);
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(2, 1);
        runtime.beginReversePresentation();

        short[] live = new short[2];
        assertEquals(1, runtime.drainPcm(live, 1));
        assertArrayEquals(new short[] {6, 60}, live);

        short[] captured = new short[4];
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {6, 60, 5, 50}, captured);

        runtime.endReversePresentation();
        runtime.beginReversePresentation();

        short[] speaker = new short[2];
        assertEquals(1, runtime.drainPcm(speaker, 1));
        assertArrayEquals(new short[] {5, 50}, speaker);
        capture.close();
    }

    @Test
    void exhaustedReverseHistoryReturnsFullZeroPaddedClockCount() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithHistory(
                1, 1, 8, 1, 10);
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(3, 1);
        runtime.beginReversePresentation();

        short[] captured = new short[6];
        assertEquals(3, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {1, 10, 0, 0, 0, 0}, captured);
    }

    @Test
    void historyClearInvalidatesCaptureCursorEpochAndReturnsSilence() {
        PcmHistoryRing history = new PcmHistoryRing(8);
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(8), history);
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        PresentationAudioCapture capture = runtime.openPresentationAudioCapture(2, 1);
        runtime.beginReversePresentation();

        runtime.clearPcmHistory();
        history.write(new short[] {9, 90, 8, 80}, 2);

        short[] captured = new short[] {-1, -1, -1, -1};
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {0, 0, 0, 0}, captured);
    }

    @Test
    void secondLeaseIsRejectedAndCloseIsIdempotent() {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1), new AudioOutputFifo(8), new PcmHistoryRing(8));
        PresentationAudioCapture first = runtime.openPresentationAudioCapture(2, 1);

        assertThrows(IllegalStateException.class,
                () -> runtime.openPresentationAudioCapture(2, 1));

        first.close();
        first.close();

        PresentationAudioCapture replacement = runtime.openPresentationAudioCapture(2, 1);
        replacement.close();
    }

    private static StreamBackedDeterministicAudioRuntime runtimeWithHistory(
            int sampleRate,
            int frameRate,
            int historyFrames,
            int... samples) {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(sampleRate, frameRate),
                new AudioOutputFifo(Math.max(historyFrames, samples.length / 2)),
                new PcmHistoryRing(historyFrames));
        runtime.setMusicStream(new SequenceStream(samples));
        int stereoFrames = samples.length / 2;
        int framesToAdvance = stereoFrames / sampleRate * frameRate;
        for (int frame = 1; frame <= framesToAdvance; frame++) {
            runtime.advanceFrame(frame, FrameAudioMode.NORMAL);
        }
        return runtime;
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
