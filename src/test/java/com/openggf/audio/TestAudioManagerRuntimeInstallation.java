package com.openggf.audio;

import com.openggf.audio.runtime.AudioFrameClock;
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
    void liveCaptureNeverSwitchesLegacyRuntimeOrTouchesBackendStreams() {
        TestLwjglBackend backend = new TestLwjglBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        backend.installLegacyStreams(new CountingStereoStream(1), new ConstantStereoStream(10));
        backend.pumpSpeaker();
        assertArrayEquals(new short[] {11, 11}, backend.firstSpeakerFrames(1));

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);

        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
        backend.pumpSpeaker();
        assertArrayEquals(new short[] {1035, 1035}, backend.firstSpeakerFrames(1),
                "attaching capture must not rebind or advance backend streams");

        audio.presentFrame(com.openggf.audio.presentation.PresentationMode.FORWARD);
        short[] captured = new short[capture.maxStereoFramesPerPacket() * 2];

        int capturedFrames = capture.drainPresentationFrame(captured);
        assertEquals(capture.sampleRate() / 60, capturedFrames);
        for (int i = 0; i < capturedFrames * 2; i++) {
            assertEquals(0, captured[i]);
        }
        assertEquals(capturedFrames, capture.totalStereoFrames());
        assertEquals(capturedFrames, capture.clockSnapshot().totalSamplesProduced());

        capture.close();
        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
        backend.pumpSpeaker();
        assertArrayEquals(new short[] {2059, 2059}, backend.firstSpeakerFrames(1),
                "detaching capture must not flush or migrate backend streams");
    }

    @Test
    void stoppingCaptureDuringRewindLeavesProducerReverseOwnershipUntouched() {
        TestLwjglBackend backend = new TestLwjglBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        backend.installLegacyStreams(new CountingStereoStream(1), null);

        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        audio.beginReverseAudioPresentation();
        capture.close();

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
        org.junit.jupiter.api.Assertions.assertTrue(
                AudioManagerTestDiagnostics.producerFingerprint(audio).reverseActive(),
                "detaching capture must not end held reverse presentation");

        audio.endReverseAudioPresentation();

        assertSame(NoOpDeterministicAudioRuntime.INSTANCE, backend.attachedRuntime);
        org.junit.jupiter.api.Assertions.assertFalse(
                AudioManagerTestDiagnostics.producerFingerprint(audio).reverseActive());
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
