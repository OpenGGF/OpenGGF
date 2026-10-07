package com.openggf.game;

import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** PlayLevel owns a new campaign; Continue and level reload do not. */
class TestTitleNewGameSpecialStageProgress {
    @AfterEach
    void cleanup() {
        SessionManager.clear();
    }

    @Test
    void sonic1TitleStartClearsEmeraldIdentitiesAndStageCursor() {
        assertSonic1TitleStart(new Sonic1GameModule());
    }

    @Test
    void decoratedSonic1TitleStartRetainsTheBaseLifecycleOwner() {
        assertSonic1TitleStart(new DelegatingGameModule(new Sonic1GameModule(), "test-lifecycle") {});
    }

    @Test
    void decoratedSonic1TitleStartPreservesConfiguredProgressDimensions() {
        GameModule module = new DelegatingGameModule(new Sonic1GameModule(), "test-custom-cycle") {
            @Override public int getSpecialStageCycleCount() { return 4; }
            @Override public int getChaosEmeraldCount() { return 3; }
        };
        GameStateManager state = populatedState(module);
        state.startNewGameFromTitle();
        assertEquals(4, state.getSpecialStageCount());
        assertEquals(3, state.getChaosEmeraldCount());
        assertEquals(0, state.getCurrentSpecialStageIndex());
        assertEquals(0, state.getEmeraldCount());
        assertTrue(state.getCollectedChaosEmeraldIndices().isEmpty());
    }

    private void assertSonic1TitleStart(GameModule module) {
        GameStateManager state = populatedState(module);
        state.startNewGameFromTitle();
        assertEquals(3, state.getLives());
        assertEquals(0, state.getContinues());
        assertEquals(0, state.getScore());
        assertEquals(0, state.getCurrentSpecialStageIndex());
        assertEquals(0, state.getEmeraldCount());
        assertTrue(state.getCollectedChaosEmeraldIndices().isEmpty());
        assertEquals(6, state.getChaosEmeraldCount());
        assertEquals(0, state.consumeCurrentSpecialStageIndexAndAdvanceSkippingCollected(false),
                "a new campaign begins with the first available special stage");
    }

    @Test
    void sonic2TitleStartPreservesItsExistingSpecialStageProgress() {
        assertTitlePreservesProgress(new Sonic2GameModule());
    }

    @Test
    void sonic3kDefaultHookPreservesSaveProgress() {
        assertTitlePreservesProgress(new Sonic3kGameModule());
    }

    private void assertTitlePreservesProgress(GameModule module) {
        GameStateManager state = populatedState(module);
        state.startNewGameFromTitle();
        assertEquals(1, state.getEmeraldCount());
        assertTrue(state.hasEmerald(2));
        assertEquals(1, state.getCurrentSpecialStageIndex());
    }

    @Test
    void sonic1ContinueAndLevelResetPreserveEmeraldsAndCursor() {
        GameStateManager state = populatedState(new Sonic1GameModule());
        assertTrue(state.consumeContinue());
        state.resetForLevel();
        assertEquals(1, state.getEmeraldCount());
        assertTrue(state.hasEmerald(2));
        assertEquals(1, state.getCurrentSpecialStageIndex());
    }

    private GameStateManager populatedState(GameModule module) {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
        GameStateManager state = GameServices.gameState();
        state.markEmeraldCollected(2);
        state.consumeCurrentSpecialStageIndexAndAdvance();
        state.addContinue();
        state.addScore(100);
        return state;
    }
}
