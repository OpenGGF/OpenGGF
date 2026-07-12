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
import com.openggf.game.ZoneProgressionPlan;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
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

    @Test
    void advanceToNextLevelUsesConfiguredSuccessorRedirect() throws Exception {
        WorldSession worldSession = new WorldSession(mock(GameModule.class));
        LevelManager levelManager = spy(new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession));
        doNothing().when(levelManager).loadCurrentLevel();
        for (int zone = 0; zone < 12; zone++) {
            levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        }
        levelManager.currentZone = 7;
        levelManager.currentAct = 0;
        ZoneProgressionPlan.ZoneTopology topology = topologyWithAppendedMod();
        levelManager.setZoneProgressionPlan(
                ZoneProgressionPlan.builder(topology).insertAfter(7, 11).build(), topology);

        levelManager.advanceToNextLevel();

        assertEquals(11, worldSession.getCurrentZone());
        assertEquals(0, worldSession.getCurrentAct());
        assertFalse(levelManager.consumeCreditsRequest());
    }

    @Test
    void advanceZoneActOnlyUsesSamePlanButPreservesLegacyCreditsWrap() {
        WorldSession worldSession = new WorldSession(mock(GameModule.class));
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession);
        levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        levelManager.currentZone = 0;
        levelManager.currentAct = 0;

        levelManager.advanceZoneActOnly();

        assertEquals(0, worldSession.getCurrentZone());
        assertEquals(0, worldSession.getCurrentAct());
        assertTrue(levelManager.consumeSpecialStageReturnLevelReloadRequest());
    }

    @Test
    void rejectedTopologyDoesNotReplaceTheInstalledPlan() {
        WorldSession worldSession = new WorldSession(mock(GameModule.class));
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession);
        levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        ZoneProgressionPlan.ZoneTopology valid = ZoneProgressionPlan.ZoneTopology.linear(1);
        levelManager.setZoneProgressionPlan(ZoneProgressionPlan.LINEAR, valid);
        ZoneProgressionPlan.ZoneTopology invalid = ZoneProgressionPlan.ZoneTopology.linear(1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> levelManager.setZoneProgressionPlan(ZoneProgressionPlan.LINEAR, invalid));
        assertDoesNotThrow(levelManager::advanceToNextLevel);

        assertTrue(levelManager.consumeCreditsRequest());
    }

    @Test
    void planTopologyMetadataMismatchIsRejectedWithoutReplacingInstalledPlan() {
        WorldSession worldSession = new WorldSession(mock(GameModule.class));
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession);
        levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        ZoneProgressionPlan.ZoneTopology terminal = ZoneProgressionPlan.ZoneTopology.of(List.of(
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.RESULTS_DRIVEN),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL)));
        ZoneProgressionPlan.ZoneTopology resultsDriven = ZoneProgressionPlan.ZoneTopology.of(List.of(
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.EVENT_CHAINED),
                new ZoneProgressionPlan.ZoneMetadata(1, ZoneProgressionPlan.Completion.TERMINAL)));
        levelManager.setZoneProgressionPlan(ZoneProgressionPlan.LINEAR, terminal);
        ZoneProgressionPlan incompatiblePlan = ZoneProgressionPlan.builder(resultsDriven).build();

        assertThrows(IllegalArgumentException.class,
                () -> levelManager.setZoneProgressionPlan(incompatiblePlan, terminal));
        levelManager.currentZone = 1;
        levelManager.currentAct = 0;
        assertDoesNotThrow(levelManager::advanceToNextLevel);

        assertTrue(levelManager.consumeCreditsRequest());
        assertEquals(terminal.zoneCount(), worldSession.getCurrentZone());
    }

    @Test
    void earlyTerminalCreditsSentinelIsPastAppendedModZones() {
        WorldSession worldSession = new WorldSession(mock(GameModule.class));
        LevelManager levelManager = new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                mock(ParallaxManager.class), mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), engineContext(), worldSession);
        for (int zone = 0; zone < 12; zone++) {
            levelManager.levels.add(List.of(LevelData.DEATH_EGG));
        }
        levelManager.currentZone = 10;
        levelManager.currentAct = 0;
        ZoneProgressionPlan.ZoneTopology topology = topologyWithAppendedMod();
        levelManager.setZoneProgressionPlan(ZoneProgressionPlan.builder(topology).build(), topology);

        assertDoesNotThrow(levelManager::advanceToNextLevel);

        assertEquals(topology.zoneCount(), worldSession.getCurrentZone());
        assertTrue(levelManager.consumeCreditsRequest());
    }

    private static ZoneProgressionPlan.ZoneTopology topologyWithAppendedMod() {
        java.util.ArrayList<ZoneProgressionPlan.ZoneMetadata> zones = new java.util.ArrayList<>();
        for (int zone = 0; zone < 12; zone++) {
            ZoneProgressionPlan.Completion completion = zone == 10
                    ? ZoneProgressionPlan.Completion.TERMINAL
                    : ZoneProgressionPlan.Completion.RESULTS_DRIVEN;
            zones.add(new ZoneProgressionPlan.ZoneMetadata(1, completion));
        }
        return ZoneProgressionPlan.ZoneTopology.of(zones);
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
