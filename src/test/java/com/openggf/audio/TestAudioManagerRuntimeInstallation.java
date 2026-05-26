package com.openggf.audio;

import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}
