package com.openggf.audio;

import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestAudioManagerRuntimeInstallation {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void nullBackendKeepsNoOpDeterministicRuntime() {
        CapturingNullBackend backend = new CapturingNullBackend();

        AudioManager.getInstance().setBackend(backend);

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
    }

    @Test
    void presentationBackendInstallsStreamBackedRuntime() {
        CapturingPresentationBackend backend = new CapturingPresentationBackend();

        AudioManager.getInstance().setBackend(backend);

        assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, backend.attachedRuntime);
    }

    @Test
    void lwjglBackendFeedsSpeakerAndLiveCaptureFromSameNonConsumingPresentationFrame() {
        TestLwjglBackend backend = new TestLwjglBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);

        StreamBackedDeterministicAudioRuntime runtime =
                assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, backend.attachedRuntime);
        runtime.setMusicStream(new SequenceStream(1, 10, 2, 20));
        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);

        audio.advanceGameplayFrameAudio();
        backend.pumpSpeaker();
        short[] captured = new short[4];

        assertArrayEquals(new short[] {1, 10, 2, 20}, backend.firstSpeakerFrames(2));
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {1, 10, 2, 20}, captured,
                "speaker draining must not consume the capture-owned PCM");
        capture.close();
    }

    private static class CapturingNullBackend extends NullAudioBackend {
        DeterministicAudioRuntime attachedRuntime;

        @Override
        public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
            attachedRuntime = runtime;
        }
    }

    private static final class CapturingPresentationBackend extends CapturingNullBackend {
        @Override
        public boolean supportsDeterministicRuntimePresentation() {
            return true;
        }

        @Override
        public int outputSampleRate() {
            return 120;
        }
    }

    private static final class TestLwjglBackend extends LWJGLAudioBackend {
        private DeterministicAudioRuntime attachedRuntime;
        private short[] uploaded;

        private TestLwjglBackend() {
            super(SonicConfigurationService.createStandalone());
        }

        @Override
        public void init() {
            // The integration exercises the production SMPS presentation path
            // without requiring an OpenAL device in the test process.
        }

        @Override
        public void destroy() {
        }

        @Override
        public void stopPlayback() {
        }

        @Override
        public int outputSampleRate() {
            return 120;
        }

        @Override
        public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
            attachedRuntime = runtime;
            super.attachDeterministicAudioRuntime(runtime);
        }

        @Override
        protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) {
            uploaded = Arrays.copyOf(pcm, pcm.length);
        }

        private void pumpSpeaker() {
            fillBuffer(0);
        }

        private short[] firstSpeakerFrames(int frames) {
            return Arrays.copyOf(uploaded, frames * 2);
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
