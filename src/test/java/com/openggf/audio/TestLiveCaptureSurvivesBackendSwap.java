package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starting a recording on the master title screen and then entering a game
 * must not silently kill the recording's audio.
 *
 * <p>Entering gameplay runs {@code Engine.initializeGlobalGameplayServices},
 * which calls {@code AudioManager.setBackend(new LWJGLAudioBackend(...))}.
 * {@code setBackend} tears the presentation producer down and rebuilds it, and
 * the teardown used to close the live capture lease outright. The recorder's
 * next drain then threw {@code "Live capture audio handle is no longer
 * attached"}, which the Task 11 degradation caught and answered with clocked
 * silence — so the recording did not fail, it just lost its audio for good the
 * moment the player started a game, which is worse than failing.
 *
 * <p>A backend swap replaces the output device. It is not a reason to stop
 * recording what the engine is playing, so the lease is carried across.
 */
class TestLiveCaptureSurvivesBackendSwap {

    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(2));
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void aRecordingStartedBeforeGameplaySurvivesTheBackendSwap() {
        LiveCaptureAudioHandle recording = audio.beginLiveCaptureAudio(1);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(2, recording.drainPresentationFrame(new short[4]),
                "the recording drains before the swap");

        // What entering a game does.
        audio.setBackend(new FixedRateNullBackend(2));
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, recording.drainPresentationFrame(new short[4]),
                "the recording must keep receiving packets after the backend swap");
    }

    /**
     * The capture clock must not restart, or the recorded audio would jump
     * relative to the video at the moment the player entered the game.
     */
    @Test
    void theCaptureClockContinuesAcrossTheSwap() {
        LiveCaptureAudioHandle recording = audio.beginLiveCaptureAudio(1);
        audio.presentFrame(PresentationMode.FORWARD);
        recording.drainPresentationFrame(new short[4]);
        long framesBefore = recording.totalStereoFrames();

        audio.setBackend(new FixedRateNullBackend(2));
        audio.presentFrame(PresentationMode.FORWARD);
        recording.drainPresentationFrame(new short[4]);

        assertTrue(recording.totalStereoFrames() > framesBefore,
                "the clock must advance, not restart");
        assertEquals(framesBefore * 2, recording.totalStereoFrames(),
                "two equal frames either side of the swap");
    }

    /** Tearing the engine down is still a real close, not a reattach. */
    @Test
    void resetStateStillRetiresTheLease() {
        LiveCaptureAudioHandle recording = audio.beginLiveCaptureAudio(1);

        audio.resetState();

        assertThrows(IllegalStateException.class,
                () -> recording.drainPresentationFrame(new short[4]),
                "a full teardown must genuinely retire the lease");
    }

    private static final class FixedRateNullBackend extends NullAudioBackend {
        private final int outputSampleRate;

        private FixedRateNullBackend(int outputSampleRate) {
            this.outputSampleRate = outputSampleRate;
        }

        @Override
        public int outputSampleRate() {
            return outputSampleRate;
        }
    }
}
