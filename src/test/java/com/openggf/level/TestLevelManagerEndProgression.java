package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.RomDetectionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelManagerEndProgression {

    @Test
    void advancePastFinalConfiguredLevelRequestsCreditsWithoutWrappingToZoneZero() {
        GameModule module = mock(GameModule.class);
        WorldSession worldSession = new WorldSession(module);
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession);
        levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        levelManager.currentZone = 0;
        levelManager.currentAct = 0;

        assertDoesNotThrow(levelManager::advanceToNextLevel);

        assertTrue(levelManager.consumeCreditsRequest(), "End of configured progression should request credits");
        assertEquals(1, worldSession.getCurrentZone(),
                "Current zone should remain at the terminal out-of-range sentinel, not wrap to zone 0");
        assertEquals(0, worldSession.getCurrentAct());
    }

    @Test
    void advanceToNextLevelDuringTimeAttackRequestsMenuReturnWithoutAdvancing() {
        GameModule module = mock(GameModule.class);
        WorldSession worldSession = new WorldSession(module);
        GameStateManager gameState = new GameStateManager();
        gameState.setTimeAttackActive(true);
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                gameState, engineContext(), worldSession);
        levelManager.levels.add(List.of(LevelData.DEATH_EGG, LevelData.DEATH_EGG));
        levelManager.currentZone = 0;
        levelManager.currentAct = 0;

        assertDoesNotThrow(levelManager::advanceToNextLevel);

        assertTrue(levelManager.consumeTimeAttackMenuReturnRequest(),
                "A finished/abandoned time attack attempt must request a return to the time attack menu");
        assertEquals(0, worldSession.getCurrentZone(),
                "advanceToNextLevel() must not touch zone/act counters while time attack is active");
        assertEquals(0, worldSession.getCurrentAct());
        assertFalse(levelManager.consumeCreditsRequest(),
                "The time-attack gate must return before any of the normal advance/credits requests fire");
    }

    private static EngineContext engineContext() {
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        return new EngineContext(configuration, mock(GraphicsManager.class), mock(AudioManager.class),
                mock(RomManager.class), mock(PerformanceProfiler.class), mock(DebugOverlayManager.class),
                mock(PlaybackDebugManager.class), mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class));
    }
}
