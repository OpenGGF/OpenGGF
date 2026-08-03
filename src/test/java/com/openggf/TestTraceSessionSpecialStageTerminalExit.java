package com.openggf;

import com.openggf.audio.GameSound;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceSessionSpecialStageTerminalExit {

    @BeforeEach
    void setUp() {
        EngineServices.configure(
                EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
    }

    @AfterEach
    void tearDown() {
        GameServices.audio().resetState();
        SessionManager.clear();
    }

    @Test
    void standaloneAudioInstallsBaseSoundMapWithoutStartingMusic()
            throws Exception {
        TraceSessionLauncher session = session();
        GameServices.audio().resetState();

        session.configureStandaloneSpecialStageAudio();

        Field soundMap = GameServices.audio().getClass()
                .getDeclaredField("soundMap");
        soundMap.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<GameSound, Integer> installed =
                (Map<GameSound, Integer>) soundMap.get(GameServices.audio());
        assertEquals(GameServices.module().getAudioProfile().getSoundMap(),
                installed);
        assertTrue(GameServices.audio().commandTimeline().entries().stream()
                        .noneMatch(entry -> entry.command()
                                instanceof AudioCommand.PlayMusic),
                "audio routing setup must not start or restart music");
    }

    @Test
    void nativeWhiteHoldDefersTeardownWithoutStartingASecondFade()
            throws Exception {
        TraceSessionLauncher session = session();
        setBoolean(session, "productionIterationInProgress", true);
        GameServices.fade().holdWhite();

        session.beginSpecialStageTerminalExit();

        assertEquals(FadeManager.FadeState.HOLD_WHITE,
                GameServices.fade().getState());
        assertTrue(getBoolean(session, "teardownPending"));
        assertFalse(session.shouldSkipCurrentSpecialStageTick(),
                "a structural unit seam without SS data remains neutral");
    }

    @Test
    void idleTerminalStateUsesTheNormalBlackFade() {
        TraceSessionLauncher session = session();

        session.beginSpecialStageTerminalExit();

        assertEquals(FadeManager.FadeState.FADING_TO_BLACK,
                GameServices.fade().getState());
        assertTrue(GameServices.fade().hasPendingCompletion());
    }

    @Test
    void existingCallbackFadeIsNeverOverwritten() {
        TraceSessionLauncher session = session();
        GameServices.fade().startFadeToWhite(() -> { });

        session.beginSpecialStageTerminalExit();

        assertEquals(FadeManager.FadeState.FADING_TO_WHITE,
                GameServices.fade().getState());
        assertTrue(GameServices.fade().hasPendingCompletion());
    }

    private static TraceSessionLauncher session() {
        return new TraceSessionLauncher(null, null,
                List.<com.openggf.trace.replay.runs.TraceRunReplayWalker
                        .SegmentPlan>of(), null);
    }

    private static void setBoolean(
            TraceSessionLauncher session, String name, boolean value)
            throws Exception {
        Field field = TraceSessionLauncher.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(session, value);
    }

    private static boolean getBoolean(
            TraceSessionLauncher session, String name) throws Exception {
        Field field = TraceSessionLauncher.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(session);
    }
}
