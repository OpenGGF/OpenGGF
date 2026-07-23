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
    void lwjglBackendKeepsLegacyPresentationUntilLiveCaptureStarts() {
        TestLwjglBackend backend = new TestLwjglBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        backend.installLegacyStreams(new CountingStereoStream(1), new ConstantStereoStream(10));
        backend.pumpSpeaker();
        assertArrayEquals(new short[] {11, 11}, backend.firstSpeakerFrames(1));

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);

        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, backend.attachedRuntime);

        audio.advanceGameplayFrameAudio();
        backend.pumpSpeaker();
        short[] captured = new short[4];

        assertArrayEquals(new short[] {1035, 1035, 1036, 1036}, backend.firstSpeakerFrames(2));
        assertEquals(2, capture.drainPresentationFrame(captured));
        assertArrayEquals(new short[] {1035, 1035, 1036, 1036}, captured,
                "speaker draining must not consume the capture-owned PCM");

        audio.advanceGameplayFrameAudio();
        capture.close();
        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
        backend.pumpSpeaker();
        assertArrayEquals(new short[] {
                1037, 1037, 1038, 1038, 1039, 1039
        }, backend.firstSpeakerFrames(3), "stop must bridge queued runtime PCM into legacy playback");
    }

    @Test
    void stoppingCaptureDuringRewindDefersLegacyRestoreUntilReverseEnds() {
        TestLwjglBackend backend = new TestLwjglBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        backend.installLegacyStreams(new CountingStereoStream(1), null);

        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        audio.advanceGameplayFrameAudio();
        audio.beginReverseAudioPresentation();
        capture.close();

        assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, backend.attachedRuntime,
                "the rewind cursor owner must remain attached until reverse presentation ends");

        audio.endReverseAudioPresentation();

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
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

        private void installLegacyStreams(AudioStream music, AudioStream sfx) {
            currentStream = music;
            sfxStream = sfx;
        }
    }

    private static final class CountingStereoStream implements AudioStream {
        private int nextFrame;

        private CountingStereoStream(int firstFrame) {
            nextFrame = firstFrame;
        }

        @Override
        public int read(short[] buffer) {
            return read(buffer, buffer.length);
        }

        @Override
        public int read(short[] buffer, int samples) {
            for (int i = 0; i < samples; i += 2) {
                short value = (short) nextFrame++;
                buffer[i] = value;
                buffer[i + 1] = value;
            }
            return samples;
        }
    }

    private static final class ConstantStereoStream implements AudioStream {
        private final short value;

        private ConstantStereoStream(int value) {
            this.value = (short) value;
        }

        @Override
        public int read(short[] buffer) {
            return read(buffer, buffer.length);
        }

        @Override
        public int read(short[] buffer, int samples) {
            Arrays.fill(buffer, 0, samples, value);
            return samples;
        }
    }
}
