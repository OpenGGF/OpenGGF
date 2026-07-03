package com.openggf.audio;

import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Continuous PCM rewind-history recording must stay disarmed until a rewind
 * consumer (held-key live rewind, Trace Test Mode) actually arms it —
 * otherwise every audio buffer pays a copy into a ring that nothing can ever
 * read back, and stale samples can survive to leak across a later boundary.
 */
class TestRewindHistoryArming {

    private SonicConfigurationService config;
    private HeadlessSmpsAudioBackend backend;

    @BeforeEach
    void setUp() {
        config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        backend = new HeadlessSmpsAudioBackend(config, PerformanceProfiler.getInstance());
        backend.init();
    }

    @AfterEach
    void tearDown() {
        backend.destroy();
        config.resetToDefaults();
    }

    @Test
    void recordingStaysDisarmedByDefault() throws Exception {
        PcmHistoryRing ring = pcmHistoryRing(backend);
        assertNotNull(ring, "headless backend must construct its rewind-history ring on init");

        backend.fillBuffer(0);

        assertEquals(0, storedFrames(ring),
                "PCM history must not record until a rewind consumer arms it");
    }

    @Test
    void audioManagerForwardsArmingToTheBackend() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        RecordingArmBackend recordingBackend = new RecordingArmBackend();
        audio.setBackend(recordingBackend);
        try {
            audio.setRewindHistoryArmed(true);
            assertEquals(java.util.List.of(true), recordingBackend.armCalls);

            audio.setRewindHistoryArmed(false);
            assertEquals(java.util.List.of(true, false), recordingBackend.armCalls);
        } finally {
            audio.resetState();
        }
    }

    @Test
    void armingEnablesRecordingAndDisarmingStopsIt() throws Exception {
        PcmHistoryRing ring = pcmHistoryRing(backend);

        backend.setRewindHistoryArmed(true);
        backend.fillBuffer(0);

        int framesAfterArm = storedFrames(ring);
        assertTrue(framesAfterArm > 0, "arming must enable PCM history recording");

        backend.setRewindHistoryArmed(false);
        backend.fillBuffer(0);

        assertEquals(framesAfterArm, storedFrames(ring),
                "disarming must stop further PCM history recording");
    }

    private static PcmHistoryRing pcmHistoryRing(AbstractSmpsAudioBackend backend) throws Exception {
        Field field = AbstractSmpsAudioBackend.class.getDeclaredField("pcmHistory");
        field.setAccessible(true);
        return (PcmHistoryRing) field.get(backend);
    }

    private static int storedFrames(PcmHistoryRing ring) throws Exception {
        Field field = PcmHistoryRing.class.getDeclaredField("storedFrames");
        field.setAccessible(true);
        return field.getInt(ring);
    }

    private static final class RecordingArmBackend extends NullAudioBackend {
        final java.util.List<Boolean> armCalls = new java.util.ArrayList<>();

        @Override
        public void setRewindHistoryArmed(boolean armed) {
            armCalls.add(armed);
        }
    }
}
