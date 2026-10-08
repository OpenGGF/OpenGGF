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
import com.openggf.level.resources.PreparableLevelLoader;
import com.openggf.level.resources.PreparedLevelBuild;
import com.openggf.level.resources.DeferredLevelResourceDescriptor;
import com.openggf.level.resources.DeferredLevelResourceLoader;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import com.openggf.level.resources.DeferredLevelResourceTracker;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void ordinaryLoadRejectsTransitionPreparedForTheSameIndex() throws Exception {
        Game game = mock(Game.class, withSettings().extraInterfaces(PreparableLevelLoader.class));
        PreparableLevelLoader loader = (PreparableLevelLoader) game;
        Level ordinary = stubLevel();
        Level transition = stubLevel();
        PreparedLevelBuild prepared = mock(PreparedLevelBuild.class);
        when(prepared.level()).thenReturn(transition);
        when(loader.prepareLevelBuildTask(LEVEL_INDEX, "fire")).thenReturn(() -> prepared);
        when(loader.installPreparedLevel(prepared)).thenReturn(transition);
        when(game.loadLevel(LEVEL_INDEX)).thenReturn(ordinary);
        LevelManager manager = managerFor(game);
        manager.levels.add(java.util.List.of(LevelData.SKY_CHASE));

        assertTrue(manager.prepareActTransitionLevelLoad(0, 0, "fire"));
        assertSame(ordinary, manager.loadLevelData(LEVEL_INDEX));
        assertEquals(0, manager.preparedLevelInstallCount());
        verify(loader, never()).installPreparedLevel(prepared);
    }

    @Test
    void deferredOrdinaryLoadRejectsTransitionPreparedForTheSameIndex() throws Exception {
        Game game = mock(Game.class, withSettings().extraInterfaces(PreparableLevelLoader.class));
        PreparableLevelLoader loader = (PreparableLevelLoader) game;
        Level ordinary = stubLevel();
        Level transition = stubLevel();
        PreparedLevelBuild prepared = mock(PreparedLevelBuild.class);
        when(prepared.level()).thenReturn(transition);
        when(loader.prepareLevelBuildTask(LEVEL_INDEX, "fire")).thenReturn(() -> prepared);
        when(loader.installPreparedLevel(prepared)).thenReturn(transition);
        when(game.loadLevel(LEVEL_INDEX)).thenReturn(ordinary);
        LevelManager manager = managerFor(game);
        manager.levels.add(java.util.List.of(LevelData.SKY_CHASE));

        assertTrue(manager.prepareActTransitionLevelLoad(0, 0, "fire"));
        assertSame(ordinary, manager.loadLevelData(LEVEL_INDEX, DeferredLevelResourceTracker.none()));
        assertEquals(0, manager.preparedLevelInstallCount());
        verify(loader, never()).installPreparedLevel(prepared);
    }

    @Test
    void matchingTransitionInstallsItsPreparedLevel() throws Exception {
        PreparedFixture fixture = preparedFixture();
        assertSame(fixture.transition(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire"));
        assertEquals(1, fixture.manager().preparedLevelInstallCount());
    }

    @Test
    void differentMutationDiscardsPreparedLevel() throws Exception {
        PreparedFixture fixture = preparedFixture();
        assertSame(fixture.ordinary(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "different"));
        assertSame(fixture.ordinary(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire"));
        assertEquals(0, fixture.manager().preparedLevelInstallCount());
    }

    @Test
    void gameplayResetDiscardsPreparationEvenWhenTheSameLoaderIsReused() throws Exception {
        PreparedFixture fixture = preparedFixture();
        Game loader = fixture.manager().game;
        fixture.manager().resetGameplayState();
        fixture.manager().game = loader;
        assertSame(fixture.ordinary(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire"));
        assertEquals(0, fixture.manager().preparedLevelInstallCount());
    }

    @Test
    void cancellationDiscardsPreparedLevel() throws Exception {
        PreparedFixture fixture = preparedFixture();
        fixture.manager().discardPreparedLevelLoad();
        assertSame(fixture.ordinary(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire"));
        assertEquals(0, fixture.manager().preparedLevelInstallCount());
    }

    @Test
    void ordinaryLoadDiscardsPreparationInsteadOfLeavingItForALaterTransition() throws Exception {
        PreparedFixture fixture = preparedFixture();
        assertSame(fixture.ordinary(), fixture.manager().loadLevelData(LEVEL_INDEX));
        assertSame(fixture.ordinary(), fixture.manager().loadActTransitionLevelData(
                LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire"));
        assertEquals(0, fixture.manager().preparedLevelInstallCount());
    }

    @Test
    void placementPlansRunAfterNormalPreparedAndDeferredDecodeOnlyOnce() throws Exception {
        for (String path : java.util.List.of("normal", "prepared", "deferred")) {
            Game game = mock(Game.class, withSettings().extraInterfaces(
                    PreparableLevelLoader.class, DeferredLevelResourceLoader.class));
            PlacementLevel level = new PlacementLevel();
            LevelManager manager = managerFor(game);
            GameModule module = mock(GameModule.class);
            var zones = mock(com.openggf.game.ZoneRegistry.class);
            when(module.getZoneRegistry()).thenReturn(zones);
            when(module.getGameplayPolicyProvider()).thenReturn(com.openggf.game.GameplayPolicyProvider.EMPTY);
            when(zones.zoneKey(0)).thenReturn(com.openggf.game.ZoneKey.stock(0));
            when(module.getGameCode()).thenReturn("s3k");
            when(module.getGameService(RegisteredLevelPlacements.class)).thenReturn(placements());
            manager.gameModule = module;
            when(game.loadLevel(LEVEL_INDEX)).thenReturn(level);
            Level installed;
            if (path.equals("prepared")) {
                PreparableLevelLoader loader = (PreparableLevelLoader) game;
                PreparedLevelBuild build = mock(PreparedLevelBuild.class);
                when(loader.prepareLevelBuildTask(LEVEL_INDEX, "fire")).thenReturn(() -> build);
                when(loader.installPreparedLevel(build)).thenReturn(level);
                manager.levels.add(java.util.List.of(LevelData.SKY_CHASE));
                assertTrue(manager.prepareActTransitionLevelLoad(0, 0, "fire"));
                assertEquals(1, level.getObjects().size(), "preparation does not admit placements");
                installed = manager.loadActTransitionLevelData(LEVEL_INDEX, DeferredLevelResourceTracker.none(), "fire");
                assertEquals(1, manager.preparedLevelInstallCount());
                verify(game, never()).loadLevel(LEVEL_INDEX);
            } else if (path.equals("deferred")) {
                var descriptor = new DeferredLevelResourceDescriptor(DeferredLevelResourceDescriptor.Kind.PATTERNS_8X8,
                        0x1234, CompressionType.KOSINSKI_MODULED, 0x40);
                var tracker = new DeferredLevelResourceManifest(java.util.List.of(descriptor)).newTracker();
                doAnswer(call -> { tracker.omitIfRequested(descriptor); return level; })
                        .when((DeferredLevelResourceLoader) game).loadLevelWithDeferredResources(LEVEL_INDEX, tracker);
                installed = manager.loadLevelData(LEVEL_INDEX, tracker);
                tracker.verifyFullyConsumed();
                verify(game, never()).loadLevel(LEVEL_INDEX);
            } else {
                installed = manager.loadLevelData(LEVEL_INDEX);
            }
            assertSame(level, installed, "the concrete level is preserved on " + path);
            assertSame(level.nativePost, level.getObjects().getFirst());
            assertEquals(2, level.getObjects().size());
            assertEquals("owner:sentry", level.getObjects().getLast().objectKey());
            assertEquals(java.util.List.of(level.safeRing), level.getRings());
            manager.loadLevelData(LEVEL_INDEX); // returning an already decoded instance must not double-add
            assertEquals(2, level.getObjects().size());
        }
    }

    private static final class PlacementLevel extends AbstractLevel {
        final com.openggf.level.objects.ObjectSpawn nativePost = new com.openggf.level.objects.ObjectSpawn(
                120, 150, 0x79, 2, 0, true, 150, 7);
        final com.openggf.level.rings.RingSpawn safeRing = new com.openggf.level.rings.RingSpawn(100, 150, 0);
        PlacementLevel() {
            super(7);
            objects = java.util.List.of(nativePost);
            rings = java.util.List.of(safeRing, new com.openggf.level.rings.RingSpawn(110, 150, 1));
        }
    }

    private static RegisteredLevelPlacements placements() throws Exception {
        var constructor = RegisteredLevelPlacements.class.getDeclaredConstructor(String.class, String.class,
                java.util.Map.class, java.util.function.Consumer.class, RegisteredLevelPlacements.class,
                com.openggf.level.objects.ObjectPlacementEncoding.class);
        constructor.setAccessible(true);
        var plan = new LevelPlacementPlan(new LevelPlacementPlan.Bounds(90, 100, 200, 200),
                java.util.List.of(new com.openggf.level.rings.RingSpawn(100, 150, 0)),
                java.util.List.of(new LevelPlacementPlan.ObjectAddition("sentry", 170, 150, 0, 0)));
        java.util.function.Consumer<Runnable> boundary = Runnable::run;
        return constructor.newInstance("owner", "s3k", java.util.Map.of(LEVEL_INDEX, plan), boundary, null,
                new com.openggf.game.common.CommonObjectPlacementEncoding());
    }

    private record PreparedFixture(LevelManager manager, Level ordinary, Level transition) { }

    private static PreparedFixture preparedFixture() throws Exception {
        Game game = mock(Game.class, withSettings().extraInterfaces(PreparableLevelLoader.class));
        PreparableLevelLoader loader = (PreparableLevelLoader) game;
        Level ordinary = stubLevel();
        Level transition = stubLevel();
        PreparedLevelBuild prepared = mock(PreparedLevelBuild.class);
        when(prepared.level()).thenReturn(transition);
        when(loader.prepareLevelBuildTask(LEVEL_INDEX, "fire")).thenReturn(() -> prepared);
        when(loader.installPreparedLevel(prepared)).thenReturn(transition);
        when(game.loadLevel(LEVEL_INDEX)).thenReturn(ordinary);
        LevelManager manager = managerFor(game);
        manager.levels.add(java.util.List.of(LevelData.SKY_CHASE));
        assertTrue(manager.prepareActTransitionLevelLoad(0, 0, "fire"));
        return new PreparedFixture(manager, ordinary, transition);
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
