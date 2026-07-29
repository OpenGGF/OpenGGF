package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Game;
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
import com.openggf.level.resources.CompressionType;
import com.openggf.level.resources.DeferredLevelResourceDescriptor;
import com.openggf.level.resources.DeferredLevelResourceLoader;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.level.resources.DeferredLevelResourceTracker;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TestLevelManagerDeferredResourceLoading {
    private static final int LEVEL_INDEX = 17;

    @Test
    void ordinaryTrackerUsesTheBaseGameLoader() throws Exception {
        Game game = mock(Game.class);
        Level level = stubLevel();
        when(game.loadLevel(LEVEL_INDEX)).thenReturn(level);
        LevelManager manager = managerFor(game);

        assertSame(level, manager.loadLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none()));

        verify(game).loadLevel(LEVEL_INDEX);
    }

    @Test
    void explicitTrackerUsesTheGameSpecificDeferredLoaderWithoutReplacingItsFence()
            throws Exception {
        DeferredLevelResourceDescriptor descriptor =
                new DeferredLevelResourceDescriptor(
                        DeferredLevelResourceDescriptor.Kind.PATTERNS_8X8,
                        0x1234, CompressionType.KOSINSKI_MODULED, 0x40);
        DeferredLevelResourceTracker tracker =
                new DeferredLevelResourceManifest(java.util.List.of(descriptor))
                        .newTracker();
        Game game = mock(Game.class, withSettings().extraInterfaces(
                DeferredLevelResourceLoader.class));
        DeferredLevelResourceLoader deferredLoader =
                (DeferredLevelResourceLoader) game;
        Level level = stubLevel();
        AtomicReference<DeferredLevelResourceTracker> received =
                new AtomicReference<>();
        doAnswer(invocation -> {
            DeferredLevelResourceTracker supplied = invocation.getArgument(1);
            received.set(supplied);
            supplied.omitIfRequested(descriptor);
            return level;
        }).when(deferredLoader).loadLevelWithDeferredResources(
                LEVEL_INDEX, tracker);
        LevelManager manager = managerFor(game);

        assertSame(level, manager.loadLevelData(LEVEL_INDEX, tracker));

        assertSame(tracker, received.get(),
                "LevelManager must pass the transition's original tracker to the provider");
        tracker.verifyFullyConsumed();
        verify(deferredLoader).loadLevelWithDeferredResources(LEVEL_INDEX, tracker);
        verify(game, never()).loadLevel(LEVEL_INDEX);
    }

    private static LevelManager managerFor(Game game) {
        SonicConfigurationService configuration =
                mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS))
                .thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS))
                .thenReturn(224);
        EngineContext context = new EngineContext(configuration,
                mock(GraphicsManager.class), mock(AudioManager.class),
                mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class));
        LevelManager manager = new LevelManager(mock(Camera.class),
                mock(SpriteManager.class), mock(ParallaxManager.class),
                mock(CollisionSystem.class), mock(WaterSystem.class),
                new GameStateManager(), context,
                new WorldSession(mock(GameModule.class)));
        manager.game = game;
        return manager;
    }

    private static Level stubLevel() {
        Level level = mock(Level.class);
        when(level.getBlockPixelSize()).thenReturn(128);
        when(level.getChunksPerBlockSide()).thenReturn(8);
        when(level.getLayerWidthBlocks(anyByte())).thenReturn(1);
        when(level.getLayerHeightBlocks(anyByte())).thenReturn(1);
        return level;
    }
}
