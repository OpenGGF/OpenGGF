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
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.RomDetectionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLevelManagerInitialPresentationPlcLifecycle {
    @Test
    void completionRunsOncePerLevelEntryTransition() {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        LevelManager manager = managerFor(module);
        LevelLoadContext omittedPresentation = new LevelLoadContext();
        omittedPresentation.setShowTitleCard(false);

        manager.requestTitleCardIfNeeded(omittedPresentation);
        manager.completeInitialTitleCardPresentation();
        verify(profile).completeInitialPresentationPlcs();

        manager.requestTitleCardIfNeeded(omittedPresentation);
        manager.completeInitialTitleCardPresentation();
        verify(profile, times(2)).completeInitialPresentationPlcs();
    }

    private static LevelManager managerFor(GameModule module) {
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        EngineContext context = new EngineContext(
                configuration,
                mock(GraphicsManager.class),
                mock(AudioManager.class),
                mock(RomManager.class),
                mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class),
                mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class));
        LevelManager manager = new LevelManager(
                mock(Camera.class),
                mock(SpriteManager.class),
                mock(ParallaxManager.class),
                mock(CollisionSystem.class),
                mock(WaterSystem.class),
                new GameStateManager(),
                context,
                new WorldSession(module));
        manager.gameModule = module;
        return manager;
    }
}
