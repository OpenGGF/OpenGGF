package com.openggf.audio;

import com.openggf.audio.AudioTestFixtures.RecordingAudioBackend;
import org.junit.jupiter.api.Test;
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
}
