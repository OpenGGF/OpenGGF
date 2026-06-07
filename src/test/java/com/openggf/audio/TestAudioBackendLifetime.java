package com.openggf.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestAudioBackendLifetime {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void setBackendDestroysCandidateWhenInitFailsBeforeFallingBack() {
        FailingBackend candidate = new FailingBackend();

        AudioManager.getInstance().setBackend(candidate);

        assertEquals(1, candidate.initCalls);
        assertEquals(1, candidate.destroyCalls);
        assertInstanceOf(NullAudioBackend.class, AudioManager.getInstance().getBackend());
    }

    private static final class FailingBackend extends NullAudioBackend {
        int initCalls;
        int destroyCalls;

        @Override
        public void init() {
            initCalls++;
            throw new IllegalStateException("simulated init failure");
        }

        @Override
        public void destroy() {
            destroyCalls++;
        }
    }
}
