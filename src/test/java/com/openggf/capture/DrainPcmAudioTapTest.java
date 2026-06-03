package com.openggf.capture;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures.RecordingAudioBackend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrainPcmAudioTapTest {

    @Test
    void drainsExactlyOneFramesPcmFromCaptureMode() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new RecordingAudioBackend());
        audio.beginCaptureMode(48000, 60);
        audio.advanceGameplayFrameAudio();

        DrainPcmAudioTap tap = new DrainPcmAudioTap(audio);
        short[] target = new short[2048];
        assertEquals(800, tap.drain(target), "one 48k/60 frame = 800 stereo samples");

        audio.endCaptureMode();
    }
}
