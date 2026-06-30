package com.openggf.graphics;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestFadeManager {

    @BeforeEach
    public void setUp() {
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    /**
     * Number of update frames for a standard fade to complete.
     * Matches FadeManager.FADE_DURATION (7 steps per RGB channel = 21 frames).
     */
    private static final int FADE_DURATION_FRAMES = 21;

    @Test
    public void testFadeToWhiteCompletes() {
        GameServices.fade().resetState();
        FadeManager fadeManager = GameServices.fade();

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
        GameServices.fade().resetState();
        FadeManager fadeManager = GameServices.fade();

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
}
