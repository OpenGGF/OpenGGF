package com.openggf.audio;

import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void beginsDrainsAndIdempotentlyClosesLiveHandle() {
        StreamBackedDeterministicAudioRuntime runtime = runtimeWithSamples(1, 10, 2, 20);
        audio.setDeterministicAudioRuntime(runtime);

        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        audio.advanceGameplayFrameAudio();

        assertEquals(2, handle.sampleRate());
        assertEquals(1, handle.frameRate());
        assertEquals(2, handle.maxStereoFramesPerPacket());
        short[] captured = new short[4];
        assertEquals(2, handle.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {1, 10, 2, 20}, captured);

        handle.close();
        handle.close();

        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void rejectsNoOpOrUnsupportedRuntime() {
        audio.setDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));

        audio.setDeterministicAudioRuntime(new UnsupportedRuntime());
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));
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
