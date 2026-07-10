package com.openggf.audio;

import com.openggf.audio.AudioTestFixtures.RecordingAudioBackend;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class AudioManagerCaptureModeTest {

    @Test
    void captureModeProducesPerFramePcmEvenWithNonPresentationBackend() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();                                 // isolate from other tests
        audio.setBackend(new RecordingAudioBackend());      // null backend: no real presentation

        audio.beginCaptureMode(48000, 60);

        short[] target = new short[800 * 2];
        long total = 0;
        for (int i = 0; i < 60; i++) {
            audio.advanceGameplayFrameAudio();          // NORMAL advance (writes PCM)
            int frames = audio.drainCaptureFrame(target);
            assertTrue(frames > 0, "each frame produces PCM in capture mode");
            total += frames;
        }
        assertEquals(48000, total, "one second of stereo frames at 48kHz/60fps");

        audio.endCaptureMode();
    }

    @Test
    void drainBeforeAnyAdvanceReturnsZero() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new RecordingAudioBackend());
        audio.beginCaptureMode(48000, 60);

        assertEquals(0, audio.drainCaptureFrame(new short[800 * 2]),
                "nothing produced yet");

        audio.endCaptureMode();
    }

    @Test
    void secondDrainInSameFrameReturnsZero() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new RecordingAudioBackend());
        audio.beginCaptureMode(48000, 60);

        audio.advanceGameplayFrameAudio();
        short[] target = new short[800 * 2];
        assertEquals(800, audio.drainCaptureFrame(target), "first drain takes the frame");
        assertEquals(0, audio.drainCaptureFrame(target),
                "FIFO emptied; a second drain without advancing yields nothing");

        audio.endCaptureMode();
    }

    @Test
    void captureRuntimeTemporarilyBecomesTheOnlyPcmHistoryOwner() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        HeadlessSmpsAudioBackend backend =
                new HeadlessSmpsAudioBackend(config, PerformanceProfiler.getInstance());
        audio.setBackend(backend);
        try {
            audio.setRewindHistoryArmed(true);
            PcmHistoryRing preCaptureRing = pcmHistoryRing(backend);
            assertNotNull(preCaptureRing, "armed presentation backend must own history before capture");

            audio.beginCaptureMode(48_000, 60);

            assertNull(pcmHistoryRing(backend),
                    "capture runtime must be the only PCM history owner while capture is active");
            audio.advanceGameplayFrameAudio();
            short[] target = new short[800 * 2];
            assertEquals(800, audio.drainCaptureFrame(target),
                    "48 kHz capture at 60 Hz must produce exactly 800 stereo frames");

            audio.endCaptureMode();

            PcmHistoryRing restoredRing = pcmHistoryRing(backend);
            assertNotNull(restoredRing, "ending capture must restore armed backend history ownership");
            assertNotSame(preCaptureRing, restoredRing,
                    "capture handoff must remain a hard history boundary");
            assertEquals(0, storedFrames(restoredRing),
                    "restored backend history must not contain pre-capture or capture PCM");
        } finally {
            audio.endCaptureMode();
            audio.resetState();
            config.resetToDefaults();
        }
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
}
