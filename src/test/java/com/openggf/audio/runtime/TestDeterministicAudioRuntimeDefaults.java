package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TestDeterministicAudioRuntimeDefaults {

    @Test
    void noOpRuntimeReportsNoActivePresentationByDefault() {
        assertFalse(NoOpDeterministicAudioRuntime.INSTANCE.hasActivePresentation());
    }
}
