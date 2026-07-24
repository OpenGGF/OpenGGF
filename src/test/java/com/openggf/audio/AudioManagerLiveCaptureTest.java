package com.openggf.audio;

import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioManagerLiveCaptureTest {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(2));
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void beginsDrainsAndIdempotentlyClosesAuthoritativeProducerHandle() {
        var producerBefore = AudioManagerTestDiagnostics.producerFingerprint(audio);

        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, handle.sampleRate());
        assertEquals(1, handle.frameRate());
        assertEquals(2, handle.maxStereoFramesPerPacket());
        short[] captured = new short[4];
        assertEquals(2, handle.drainPresentationFrame(captured));
        assertEquals(0, captured[0]);
        assertEquals(0, captured[1]);
        assertEquals(0, captured[2]);
        assertEquals(0, captured[3]);
        assertEquals(2, handle.totalStereoFrames());
        assertEquals(new AudioFrameClock.Snapshot(2, 1, 2, 0),
                handle.clockSnapshot());

        handle.close();
        handle.close();
        var producerAfter = AudioManagerTestDiagnostics.producerFingerprint(audio);
        assertEquals(producerBefore.voiceIdentities(), producerAfter.voiceIdentities(),
                "attach/detach must preserve registry identity and state");
        assertNotEquals(producerBefore.clock(), producerAfter.clock(),
                "only the explicitly presented frame advances producer time");

        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void attachesRegardlessOfLegacyRuntimeCapabilityWithoutReplacingIt() {
        audio.setDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        audio.beginLiveCaptureAudio(1).close();

        UnsupportedRuntime unsupported = new UnsupportedRuntime();
        audio.setDeterministicAudioRuntime(unsupported);
        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void rejectsSecondSimultaneousHandle() {
        audio.setDeterministicAudioRuntime(runtimeWithSamples());
        LiveCaptureAudioHandle first = audio.beginLiveCaptureAudio(1);

        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));

        first.close();
    }

    @Test
    void handleRejectsDrainAfterClose() {
        audio.setDeterministicAudioRuntime(runtimeWithSamples());
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        handle.close();

        assertThrows(IllegalStateException.class,
                () -> handle.drainPresentationFrame(new short[4]));
    }

    @Test
    void handleRejectsDrainAfterRuntimeReplacement() {
        StreamBackedDeterministicAudioRuntime replacedRuntime = runtimeWithSamples();
        audio.setDeterministicAudioRuntime(replacedRuntime);
        LiveCaptureAudioHandle replacedHandle = audio.beginLiveCaptureAudio(1);

        audio.setDeterministicAudioRuntime(runtimeWithSamples());

        assertThrows(IllegalStateException.class,
                () -> replacedHandle.drainPresentationFrame(new short[4]));
        replacedRuntime.openPresentationAudioCapture(2, 1).close();

        LiveCaptureAudioHandle replacementHandle = audio.beginLiveCaptureAudio(1);
        replacedHandle.close();
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));
        replacementHandle.close();
    }

    private static StreamBackedDeterministicAudioRuntime runtimeWithSamples(int... samples) {
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(2, 1),
                new AudioOutputFifo(8));
        runtime.setMusicStream(new SequenceStream(samples));
        return runtime;
    }

    private static final class FixedRateNullBackend extends NullAudioBackend {
        private final int outputSampleRate;

        private FixedRateNullBackend(int outputSampleRate) {
            this.outputSampleRate = outputSampleRate;
        }

        @Override
        public int outputSampleRate() {
            return outputSampleRate;
        }
    }

    private static final class UnsupportedRuntime implements DeterministicAudioRuntime {
        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
        }
    }

    private static final class SequenceStream implements AudioStream {
        private final short[] samples;
        private int cursor;

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
            return count;
        }
    }
}
