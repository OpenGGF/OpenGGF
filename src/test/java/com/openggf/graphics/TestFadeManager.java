package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestFadeManager {

    /**
     * Number of update frames for a standard fade to complete.
     * Matches FadeManager.FADE_DURATION (7 steps per RGB channel = 21 frames).
     */
    private static final int FADE_DURATION_FRAMES = 21;

    @Test
    public void testFadeToWhiteCompletes() {
        FadeManager fadeManager = new FadeManager();

        fadeManager.startFadeToWhite(null);
        for (int i = 0; i < FADE_DURATION_FRAMES; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.HOLD_WHITE, fadeManager.getState());
        fadeManager.update();
        assertEquals(FadeManager.FadeState.NONE, fadeManager.getState());
        assertFalse(fadeManager.isActive());
    }

    @Test
    public void testFadeToBlackWithHoldCompletes() {
        FadeManager fadeManager = new FadeManager();

        fadeManager.startFadeToBlack(null, 5);
        for (int i = 0; i < FADE_DURATION_FRAMES; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.HOLD_BLACK, fadeManager.getState());

        for (int i = 0; i < 5; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.NONE, fadeManager.getState());
    }

    @Test
    public void customDurationFadeToBlackCompletesOnExactFrameAndHoldsBlack() {
        FadeManager fadeManager = new FadeManager();
        int[] completions = {0};

        fadeManager.startFadeToBlack(() -> completions[0]++, 0, 60);
        for (int i = 0; i < 59; i++) {
            fadeManager.update();
        }
        assertEquals(0, completions[0]);

        fadeManager.update();

        assertEquals(1, completions[0]);
        assertEquals(FadeManager.FadeState.HOLD_BLACK, fadeManager.getState());
    }

    @Test
    void fadeFromBlackCanRetainTheRomTerminalNoOpVblank() {
        FadeManager fadeManager = new FadeManager();
        int[] completions = {0};

        fadeManager.startFadeFromBlack(() -> completions[0]++, 1);
        for (int i = 0; i < FADE_DURATION_FRAMES; i++) {
            fadeManager.update();
        }

        assertEquals(FadeManager.FadeState.FADING_FROM_BLACK, fadeManager.getState());
        assertEquals(0, completions[0]);
        assertEquals(0.0f, fadeManager.getFadeColor()[0]);
        assertEquals(0.0f, fadeManager.getFadeColor()[1]);
        assertEquals(0.0f, fadeManager.getFadeColor()[2]);

        fadeManager.update();

        assertEquals(FadeManager.FadeState.NONE, fadeManager.getState());
        assertEquals(1, completions[0]);
    }
}
