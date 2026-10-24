package com.openggf;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSpecialStageProviderRewindOwnership {
    private GameplayModeContext context;
    private GameLoop loop;

    @BeforeEach
    void configureHeadlessLoop() {
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new AudioTestFixtures.RecordingAudioBackend());
        context = SessionManager.getCurrentGameplayMode();
        loop = new GameLoop(new InputHandler());
    }

    @AfterEach
    void clearSession() {
        TestEnvironment.resetAll();
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void initializedSonic2ProviderRegistersRestoresReplaysAndRetires() {
        Sonic2SpecialStageProvider provider = assertInstanceOf(Sonic2SpecialStageProvider.class,
                context.getWorldSession().getGameModule().getSpecialStageProvider());
        enter(provider);
        Sonic2SpecialStageManager manager = provider.getManager();
        assertSame(manager, context.getWorldSession().getGameModule().getGameService(Sonic2SpecialStageManager.class));
        assertTrue(manager.isInitialized());
        CompositeSnapshot captured = captureProvider(provider);
        var expected = manager.captureComparisonState();
        int expectedIntroFrame = manager.getIntro().getFrameCounter();
        manager.markFailed();
        assertNotEquals(expected, manager.captureComparisonState());

        context.getRewindRegistry().restore(captured);
        assertEquals(expected, manager.captureComparisonState());
        assertEquals(expectedIntroFrame, manager.getIntro().getFrameCounter());
        for (int frame = 0; frame < 5; frame++) manager.update();
        int forwardIntroFrame = manager.getIntro().getFrameCounter();
        assertNotEquals(expectedIntroFrame, forwardIntroFrame);
        context.getRewindRegistry().restore(captured);
        for (int frame = 0; frame < 5; frame++) manager.update();
        assertEquals(forwardIntroFrame, manager.getIntro().getFrameCounter());

        Sonic2SpecialStageProvider replacement = new Sonic2SpecialStageProvider();
        loop.setGameMode(GameMode.LEVEL);
        enter(replacement);
        CompositeSnapshot replacementSnapshot = captureProvider(replacement);
        manager.markFailed();
        replacement.getManager().markFailed();
        context.getRewindRegistry().restore(replacementSnapshot);
        assertEquals(Sonic2SpecialStageManager.ResultState.RUNNING, replacement.getManager().getResultState());
        assertEquals(Sonic2SpecialStageManager.ResultState.FAILED, manager.getResultState(),
                "replacement registration must stop restoring the retired manager");
        retire(replacement);
        assertFalse(replacement.isInitialized());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void initializedSonic3kProviderRegistersRestoresReplaysAndRetires() {
        Sonic3kSpecialStageProvider provider = assertInstanceOf(Sonic3kSpecialStageProvider.class,
                context.getWorldSession().getGameModule().getSpecialStageProvider());
        enter(provider);
        Sonic3kSpecialStageManager manager = provider.getManager();
        assertSame(manager, context.getWorldSession().getGameModule().getGameService(Sonic3kSpecialStageManager.class));
        assertTrue(manager.isInitialized());
        for (int frame = 0; frame < 22; frame++) manager.update();
        assertTrue(manager.isEntryPresentationReady());
        CompositeSnapshot captured = captureProvider(provider);
        var expected = manager.captureComparisonState();
        manager.getPlayer().initialize(0x8000, 0x2222, 0x3333, false);
        assertNotEquals(expected, manager.captureComparisonState());

        context.getRewindRegistry().restore(captured);
        assertEquals(expected, manager.captureComparisonState());
        for (int frame = 0; frame < 5; frame++) manager.update();
        var forward = manager.captureComparisonState();
        assertNotEquals(expected, forward);
        context.getRewindRegistry().restore(captured);
        for (int frame = 0; frame < 5; frame++) manager.update();
        assertEquals(forward, manager.captureComparisonState());

        Sonic3kSpecialStageProvider replacement = new Sonic3kSpecialStageProvider();
        loop.setGameMode(GameMode.LEVEL);
        enter(replacement);
        CompositeSnapshot replacementSnapshot = captureProvider(replacement);
        var replacementExpected = replacement.getManager().captureComparisonState();
        manager.getPlayer().initialize(0x8000, 0x1111, 0x3333, false);
        var retired = manager.captureComparisonState();
        replacement.getManager().getPlayer().initialize(0x8000, 0x4444, 0x5555, false);
        context.getRewindRegistry().restore(replacementSnapshot);
        assertEquals(replacementExpected, replacement.getManager().captureComparisonState());
        assertEquals(retired, manager.captureComparisonState(),
                "replacement registration must stop restoring the retired manager");
        retire(replacement);
        assertFalse(replacement.isInitialized());
    }

    private void enter(SpecialStageProvider provider) {
        loop.doEnterSpecialStage(provider, 0, false, SpecialStageStartupPolicy.FAST);
        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
        assertSame(provider, context.getRegisteredSpecialStageProvider());
        assertSame(provider, loop.getActiveSpecialStageProvider());
    }

    private CompositeSnapshot captureProvider(SpecialStageProvider provider) {
        assertTrue(provider.isInitialized());
        CompositeSnapshot snapshot = context.getRewindRegistry().capture();
        assertTrue(snapshot.containsKey(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY));
        return snapshot;
    }

    private void retire(SpecialStageProvider provider) {
        loop.setGameMode(GameMode.SPECIAL_STAGE_RESULTS);
        assertNull(context.getRegisteredSpecialStageProvider());
        assertFalse(context.getRewindRegistry().capture()
                .containsKey(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY));
        provider.reset();
        assertFalse(context.getRewindRegistry().capture()
                .containsKey(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY));
    }
}
