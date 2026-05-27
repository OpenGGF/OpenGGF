package com.openggf.audio;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestAudioManagerReverseBracket {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void endReverseAudioPresentationRestoresThePreReverseSnapshot() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        AudioTestFixtures.RecordingAudioBackend backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);

        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        int restoreCallsBefore = backend.restoreLogicalSnapshotCalls;

        audio.beginReverseAudioPresentation();
        audio.endReverseAudioPresentation();

        assertNotNull(before);
        assertEquals(restoreCallsBefore + 1, backend.restoreLogicalSnapshotCalls,
                "endReverseAudioPresentation must invoke exactly one backend.restoreLogicalSnapshot");
    }

    @Test
    void endReverseAudioPresentationRestoresTheRuntimeClock() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime runtime =
                new com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime(
                        new com.openggf.audio.runtime.AudioFrameClock(120, 60),
                        new com.openggf.audio.runtime.AudioOutputFifo(120));
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        // Advance a few audio frames to seed a non-zero pre-rewind clock.
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 0));
        for (int i = 0; i < 5; i++) {
            audio.advanceGameplayFrameAudio();
        }
        long preRewindAudioFrame = runtime.captureClockSnapshot().totalSamplesProduced();

        audio.beginReverseAudioPresentation();
        // Simulate a burst pulling the clock backward — this is what the
        // ReverseResynthesizer would do in production.
        runtime.restoreClockSnapshot(new com.openggf.audio.runtime.AudioFrameClock.Snapshot(
                120, 60, 0L, 0));
        assertEquals(0L,
                runtime.captureClockSnapshot().totalSamplesProduced(),
                "Sanity: burst left the clock at the historical audio-frame");

        audio.endReverseAudioPresentation();

        assertEquals(preRewindAudioFrame,
                runtime.captureClockSnapshot().totalSamplesProduced(),
                "endReverseAudioPresentation must restore the runtime clock to the pre-rewind position");
    }
}
