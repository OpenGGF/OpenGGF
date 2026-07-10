package com.openggf.audio;

import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void unusedBackendDoesNotAllocateHistoryBeforeOrAfterFill() throws Exception {
        assertNull(pcmHistoryRing(backend),
                "backend history must remain unallocated until a rewind consumer arms it");

        backend.fillBuffer(0);

        assertNull(pcmHistoryRing(backend),
                "ordinary buffer fills must not allocate unused rewind history");
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
    void armingAllocatesHistoryAndRecordsPcm() throws Exception {
        backend.setRewindHistoryArmed(true);
        PcmHistoryRing ring = pcmHistoryRing(backend);
        assertNotNull(ring, "arming must allocate backend-owned PCM history");
        assertEquals(0, storedFrames(ring), "newly armed history must start empty");

        backend.fillBuffer(0);

        assertTrue(storedFrames(ring) > 0, "arming must enable PCM history recording");
    }

    @Test
    void disarmingReleasesHistoryAndRearmingCreatesAnEmptyRing() throws Exception {
        backend.setRewindHistoryArmed(true);
        PcmHistoryRing firstRing = pcmHistoryRing(backend);
        backend.fillBuffer(0);
        assertTrue(storedFrames(firstRing) > 0, "precondition: first ring contains PCM");

        backend.setRewindHistoryArmed(false);
        assertNull(pcmHistoryRing(backend), "disarming is a hard boundary that releases history");
        backend.fillBuffer(0);
        assertNull(pcmHistoryRing(backend), "fills while disarmed must not recreate history");

        backend.setRewindHistoryArmed(true);
        PcmHistoryRing secondRing = pcmHistoryRing(backend);
        assertNotNull(secondRing, "rearming must allocate history again");
        assertNotSame(firstRing, secondRing, "rearming must not retain samples from the released ring");
        assertEquals(0, storedFrames(secondRing), "rearmed history must start empty");
    }

    @Test
    void repeatedArmingDoesNotInterruptReversePlayback() throws Exception {
        backend.setRewindHistoryArmed(true);
        PcmHistoryRing ring = pcmHistoryRing(backend);
        short[] recorded = new short[AbstractSmpsAudioBackend.STREAM_BUFFER_SIZE * 2];
        int lastFrame = (AbstractSmpsAudioBackend.STREAM_BUFFER_SIZE - 1) * 2;
        recorded[lastFrame] = 1_234;
        recorded[lastFrame + 1] = -1_234;
        ring.write(recorded, AbstractSmpsAudioBackend.STREAM_BUFFER_SIZE);

        backend.beginReversePresentation();
        backend.setRewindHistoryArmed(true);
        backend.setRewindHistoryArmed(true);
        backend.fillBuffer(0);

        assertEquals(1_234, backend.streamData[0],
                "idempotent arming must preserve the active reverse cursor");
        assertEquals(-1_234, backend.streamData[1],
                "reverse playback must remain stereo after repeated arming");
    }

    @Test
    void timeLimitedHistoryUsesEffectiveInternalOutputRate() throws Exception {
        config.setConfigValue(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, true);
        config.setConfigValue(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE, "time");
        config.setConfigValue(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS, 2);

        backend.setRewindHistoryArmed(true);

        int effectiveOutputRate = (int) Math.round(backend.getSmpsOutputRate());
        assertEquals(effectiveOutputRate * 2, capacityFrames(pcmHistoryRing(backend)),
                "time-limited history must cover configured seconds at the emitted sample rate");
    }

    @Test
    void sizeLimitedHistoryKeepsConfiguredByteCapacityAtInternalOutputRate() throws Exception {
        config.setConfigValue(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, true);
        config.setConfigValue(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE, "size");
        config.setConfigValue(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB, 1);

        backend.setRewindHistoryArmed(true);

        assertEquals((1024 * 1024) / (2 * Short.BYTES), capacityFrames(pcmHistoryRing(backend)),
                "size-limited history must retain its configured stereo PCM byte capacity");
    }

    @Test
    void destroyAndReinitCannotRetainStaleHistoryOrReverseCursor() throws Exception {
        backend.setRewindHistoryArmed(true);
        PcmHistoryRing firstRing = pcmHistoryRing(backend);
        short[] recorded = new short[AbstractSmpsAudioBackend.STREAM_BUFFER_SIZE * 2];
        recorded[recorded.length - 2] = 321;
        firstRing.write(recorded, AbstractSmpsAudioBackend.STREAM_BUFFER_SIZE);
        backend.beginReversePresentation();
        assertNotNull(reverseCursor(backend), "precondition: reverse presentation owns a cursor");

        backend.destroy();

        assertNull(pcmHistoryRing(backend), "destroy must release backend PCM history");
        assertNull(reverseCursor(backend), "destroy must cancel reverse presentation");

        backend.init();
        backend.setRewindHistoryArmed(true);
        PcmHistoryRing reinitializedRing = pcmHistoryRing(backend);
        assertNotNull(reinitializedRing, "reinitialized backend must support arming again");
        assertNotSame(firstRing, reinitializedRing, "re-init must not recover the destroyed history ring");
        assertEquals(0, storedFrames(reinitializedRing), "stale PCM must not survive destroy/re-init");
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

    private static int capacityFrames(PcmHistoryRing ring) throws Exception {
        Field field = PcmHistoryRing.class.getDeclaredField("capacityFrames");
        field.setAccessible(true);
        return field.getInt(ring);
    }

    private static PcmHistoryRing.ReverseCursor reverseCursor(AbstractSmpsAudioBackend backend) throws Exception {
        Field field = AbstractSmpsAudioBackend.class.getDeclaredField("reverseCursor");
        field.setAccessible(true);
        return (PcmHistoryRing.ReverseCursor) field.get(backend);
    }

    private static final class RecordingArmBackend extends NullAudioBackend {
        final java.util.List<Boolean> armCalls = new java.util.ArrayList<>();

        @Override
        public void setRewindHistoryArmed(boolean armed) {
            armCalls.add(armed);
        }
    }
}
