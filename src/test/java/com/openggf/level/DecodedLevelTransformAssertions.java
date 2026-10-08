package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.*;
import com.openggf.data.*;
import com.openggf.debug.*;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.resources.*;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

/** Exercises the real final install seam from the selected mod integration regression. */
public final class DecodedLevelTransformAssertions {
    private DecodedLevelTransformAssertions() { }
    public static void executeLoadProfile(GameModule module) throws java.io.IOException {
        manager(module,mock(Game.class)).loadLevel(0);
    }
    public static void verifyAllLoadPaths() throws Exception {
        for (String route : List.of("native", "override", "prepared")) {
            Game game=mock(Game.class,withSettings().extraInterfaces(PreparableLevelLoader.class));
            PreparableLevelLoader loader=(PreparableLevelLoader)game;
            Level decoded=level(128,1), transformed=level(256,2);
            GameModule module=mock(GameModule.class);
            when(module.getGameplayPolicyProvider()).thenReturn(GameplayPolicyProvider.EMPTY);
            ZoneRegistry registry=mock(ZoneRegistry.class);
            when(module.getZoneRegistry()).thenReturn(registry);
            when(registry.zoneKey(0)).thenReturn(ZoneKey.stock(0));
            var calls=new AtomicInteger();
            when(module.transformDecodedLevel(decoded)).thenAnswer(invocation -> { calls.incrementAndGet();return transformed; });
            when(game.loadLevel(17)).thenReturn(decoded);
            when(module.loadLevelOverride(17)).thenReturn(route.equals("override")?decoded:null);
            LevelManager manager=manager(module,game);
            if(route.equals("prepared")) {
                PreparedLevelBuild prepared=mock(PreparedLevelBuild.class);
                when(prepared.level()).thenReturn(decoded);
                when(loader.prepareLevelBuildTask(17,"transition")).thenReturn(() -> prepared);
                when(loader.installPreparedLevel(prepared)).thenReturn(decoded);
                LevelDescriptor descriptor=mock(LevelDescriptor.class);when(descriptor.levelIndex()).thenReturn(17);
                manager.levels.add(List.of(descriptor));
                assertTrue(manager.prepareActTransitionLevelLoad(0,0,"transition"));
            }
            assertSame(transformed,route.equals("prepared")
                    ?manager.loadActTransitionLevelData(17,DeferredLevelResourceTracker.none(),"transition")
                    :manager.loadLevelData(17));
            assertEquals(1,calls.get(),route+" must transform exactly once after final decode selection");
            assertSame(transformed,manager.getCurrentLevel());
            assertEquals(256,manager.blockPixelSize,route+" rebuilds installed geometry");
            assertEquals(512,manager.getLayerLevelWidthPx((byte)0));
            assertNotNull(manager.getTilemapManager());
            assertFalse(manager.hasPrebuiltTilemaps(),"No original geometry cache survives replacement with an unmapped level");
            if(route.equals("prepared")) {
                assertEquals(1,manager.preparedLevelInstallCount());verify(loader).installPreparedLevel(any());verify(game,never()).loadLevel(17);
            } else if(route.equals("override")) verify(game,never()).loadLevel(17);
            else verify(game).loadLevel(17);
        }
    }
    private static Level level(int blockSize,int width) {
        Level result=mock(Level.class);when(result.getBlockPixelSize()).thenReturn(blockSize);
        when(result.getChunksPerBlockSide()).thenReturn(blockSize/16);
        when(result.getLayerWidthBlocks(anyInt())).thenReturn(width);
        when(result.getLayerHeightBlocks(anyInt())).thenReturn(1);return result;
    }
    private static LevelManager manager(GameModule module,Game game) {
        var configuration=mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        var context=new EngineContext(configuration,mock(GraphicsManager.class),mock(AudioManager.class),mock(RomManager.class),
                mock(PerformanceProfiler.class),mock(DebugOverlayManager.class),mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class),mock(CrossGameFeatureProvider.class));
        var manager=new LevelManager(mock(Camera.class),mock(SpriteManager.class),mock(ParallaxManager.class),
                mock(CollisionSystem.class),mock(WaterSystem.class),new GameStateManager(),context,new WorldSession(module));
        manager.gameModule=module;manager.game=game;return manager;
    }
}
