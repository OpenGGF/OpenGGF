package com.openggf;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameMode;
import com.openggf.game.GameStateManager;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.dataselect.DataSelectAction;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.dataselect.S1DataSelectImageCacheManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.dataselect.S2DataSelectImageCacheManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.camera.Camera;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.game.RomDetectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class TestEngine {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @Test
    void drawMasterTitleScreenDoesNotRequireGameplayCamera() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Engine engine = new Engine();
        MasterTitleScreen masterTitleScreen = mock(MasterTitleScreen.class);

        setPrivateField(engine, "masterTitleScreen", masterTitleScreen);
        engine.getGameLoop().setGameMode(GameMode.MASTER_TITLE_SCREEN);

        assertDoesNotThrow(engine::draw);
        verify(masterTitleScreen).setProjectionMatrix(engine.getProjectionMatrixBuffer());
        verify(masterTitleScreen).draw();
    }

    @Test
    void renderThreadTasksRunWhenPumped() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        CompletableFuture<Integer> future = graphics.submitRenderThreadTask(() -> 42);

        assertFalse(future.isDone());

        graphics.runPendingRenderThreadTasks();

        assertTrue(future.isDone());
        assertEquals(42, future.join());
    }

    @Test
    void resetState_discardsPendingRenderThreadTasks() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        AtomicInteger runs = new AtomicInteger();
        CompletableFuture<Integer> future = graphics.submitRenderThreadTask(() -> runs.incrementAndGet());

        graphics.resetState();
        graphics.runPendingRenderThreadTasks();

        assertEquals(0, runs.get());
        assertTrue(future.isCancelled());
    }

    @Test
    void cleanup_discardsPendingRenderThreadTasks() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        AtomicInteger runs = new AtomicInteger();
        CompletableFuture<Integer> future = graphics.submitRenderThreadTask(() -> runs.incrementAndGet());

        graphics.cleanup();
        graphics.runPendingRenderThreadTasks();

        assertEquals(0, runs.get());
        assertTrue(future.isCancelled());
    }

    @Test
    void sonic1GameModule_exposesWarmupCapableImageCacheManager() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Sonic1GameModule module = new Sonic1GameModule();

        S1DataSelectImageCacheManager manager = module.getGameService(S1DataSelectImageCacheManager.class);
        S1DataSelectImageCacheManager secondLookup = module.getGameService(S1DataSelectImageCacheManager.class);

        assertNotNull(manager);
        assertTrue(manager instanceof Sonic1GameModule.S1DataSelectImageWarmup);
        assertSame(manager, secondLookup);
    }

    @Test
    void sonic2GameModule_exposesWarmupCapableImageCacheManager() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Sonic2GameModule module = new Sonic2GameModule();

        S2DataSelectImageCacheManager manager = module.getGameService(S2DataSelectImageCacheManager.class);
        S2DataSelectImageCacheManager secondLookup = module.getGameService(S2DataSelectImageCacheManager.class);

        assertNotNull(manager);
        assertTrue(manager instanceof Sonic2GameModule.S2DataSelectImageWarmup);
        assertSame(manager, secondLookup);
    }

    @Test
    void initializeGame_runsS1WarmupBeforeStartupModeEntry() throws Exception {
        try (BootstrapHarness harness = createBootstrapHarness(true)) {
            harness.engine.initializeGame();

            assertEquals(1, harness.cacheManager.ensureStartedCalls);
            assertEquals(1, harness.cacheManager.renderTaskRuns.get());
            var order = inOrder(harness.graphics, harness.levelManager);
            order.verify(harness.graphics).runPendingRenderThreadTasks();
            order.verify(harness.levelManager).loadZoneAndAct(0, 0);
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void initializeGame_skipsS1WarmupWhenDonationIsInactive() throws Exception {
        try (BootstrapHarness harness = createBootstrapHarness(false)) {
            harness.engine.initializeGame();

            assertEquals(0, harness.cacheManager.ensureStartedCalls);
            assertEquals(0, harness.cacheManager.renderTaskRuns.get());
            var order = inOrder(harness.graphics, harness.levelManager);
            order.verify(harness.graphics).runPendingRenderThreadTasks();
            order.verify(harness.levelManager).loadZoneAndAct(0, 0);
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void createDataSelectSaveContext_preservesClearSaveStateFromPayload() throws Exception {
        Path saveRoot = Files.createTempDirectory("engine-dataselect-save");
        SaveManager saveManager = new SaveManager(saveRoot);
        saveManager.writeSlot("s3k", 1, Map.of(
                "zone", 6,
                "act", 1,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "lives", 9,
                "continues", 3,
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5, 6),
                "superEmeralds", List.of(0, 2),
                "clear", true
        ));

        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.S3K);

        DataSelectAction action = new DataSelectAction(
                DataSelectActionType.CLEAR_RESTART,
                1,
                10,
                0,
                new SelectedTeam("sonic", List.of("tails")));

        SaveSessionContext context = Engine.createDataSelectSaveContext(module, action, saveManager);

        assertEquals(1, context.activeSlot().orElseThrow());
        assertTrue(context.isClear(), "Clear-save launch context should preserve the clear flag");
        assertEquals("sonic", context.selectedTeam().mainCharacter());
        assertEquals(List.of("tails"), context.selectedTeam().sidekicks());
        assertEquals(10, context.startZone());
        assertEquals(0, context.startAct());
    }

    @Test
    void dataSelectLaunchSaveReason_mapsExistingSlotLoad() {
        assertEquals(Optional.of(SaveReason.EXISTING_SLOT_LOAD),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.LOAD_SLOT));
        assertEquals(Optional.of(SaveReason.NEW_SLOT_START),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NEW_SLOT_START));
        assertEquals(Optional.of(SaveReason.CLEAR_RESTART_COMMIT),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.CLEAR_RESTART));
        assertEquals(Optional.empty(),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NO_SAVE_START));
    }

    @Test
    void resolveMainPlayableSprite_prefersSelectedTeamOverConfigDuringGameplay() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

        Engine engine = new Engine();
        SpriteManager spriteManager = mock(SpriteManager.class);
        Camera camera = mock(Camera.class);
        LevelManager levelManager = mock(LevelManager.class);

        Knuckles knuckles = new Knuckles("knuckles", (short) 100, (short) 624);
        when(spriteManager.getSprite("knuckles")).thenReturn(knuckles);

        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(mock(GameModule.class),
                SaveSessionContext.noSave("s1", new SelectedTeam("knuckles", List.of()), 0, 0));
        setPrivateField(engine, "gameplayMode", gameplayMode);
        setPrivateField(engine, "spriteManager", spriteManager);
        setPrivateField(engine, "camera", camera);
        setPrivateField(engine, "levelManager", levelManager);

        var method = Engine.class.getDeclaredMethod("resolveMainPlayableSprite");
        method.setAccessible(true);
        Object resolved = method.invoke(engine);

        assertSame(knuckles, resolved);
    }

    private static final class TrackingS1ImageCacheManager extends S1DataSelectImageCacheManager
            implements Sonic1GameModule.S1DataSelectImageWarmup {
        int ensureStartedCalls;
        final AtomicInteger renderTaskRuns = new AtomicInteger();
        private final GraphicsManager graphics;

        TrackingS1ImageCacheManager(Path cacheRoot, GraphicsManager graphics) {
            super(cacheRoot,
                    SonicConfigurationService.getInstance(),
                    () -> "test-rom-sha",
                    new com.fasterxml.jackson.databind.ObjectMapper());
            this.graphics = graphics;
        }

        @Override
        public synchronized void ensureGenerationStarted() {
            ensureStartedCalls++;
            graphics.submitRenderThreadTask(() -> {
                renderTaskRuns.incrementAndGet();
                return 42;
            });
        }
    }

    private BootstrapHarness createBootstrapHarness(boolean donorActive) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_SOURCE, donorActive ? "s3k" : "s2");
        if (donorActive) {
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        }
        config.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        GraphicsManager graphics = spy(new GraphicsManager());
        RomManager romManager = mock(RomManager.class);
        Rom rom = mock(Rom.class);
        when(romManager.getRom()).thenReturn(rom);
        AudioManager audioManager = mock(AudioManager.class);
        PerformanceProfiler profiler = mock(PerformanceProfiler.class);
        DebugOverlayManager debugOverlayManager = mock(DebugOverlayManager.class);
        PlaybackDebugManager playbackDebugManager = mock(PlaybackDebugManager.class);
        RomDetectionService romDetectionService = mock(RomDetectionService.class);
        CrossGameFeatureProvider crossGameFeatureProvider = mock(CrossGameFeatureProvider.class);
        EngineContext services = new EngineContext(config, graphics, audioManager, romManager, profiler,
                debugOverlayManager, playbackDebugManager, romDetectionService, crossGameFeatureProvider);

        TrackingS1ImageCacheManager cacheManager = new TrackingS1ImageCacheManager(tempDir, graphics);
        Sonic1GameModule module = new Sonic1GameModule() {
            @Override
            public <T> T getGameService(Class<T> type) {
                if (type == S1DataSelectImageCacheManager.class) {
                    return type.cast(cacheManager);
                }
                return super.getGameService(type);
            }
        };

        Camera camera = new Camera();
        SpriteManager spriteManager = mock(SpriteManager.class);
        LevelManager levelManager = mock(LevelManager.class);
        GameStateManager gameState = new GameStateManager();
        doReturn(false).when(spriteManager).addSprite(any());
        doAnswer(invocation -> {
            assertEquals(donorActive ? 1 : 0, cacheManager.renderTaskRuns.get());
            return null;
        }).when(levelManager).loadZoneAndAct(0, 0);
        when(romDetectionService.detectAndCreateModule(rom)).thenReturn(java.util.Optional.of(module));

        MockedStatic<GameplaySessionFactory> gameplayFactory =
                mockStatic(GameplaySessionFactory.class, CALLS_REAL_METHODS);
        gameplayFactory.when(() -> GameplaySessionFactory.attachManagers(
                        any(GameplayModeContext.class), any(EngineContext.class)))
                .thenAnswer(invocation -> {
                    GameplayModeContext gameplayMode = invocation.getArgument(0);
                    gameplayMode.tearDownManagers();
                    when(spriteManager.rewindSnapshottable()).thenReturn(
                            new com.openggf.game.rewind.RewindSnapshottable<com.openggf.game.rewind.snapshot.SpriteManagerSnapshot>() {
                        @Override
                        public String key() {
                            return "sprites";
                        }

                        @Override
                        public com.openggf.game.rewind.snapshot.SpriteManagerSnapshot capture() {
                            return null;
                        }

                        @Override
                        public void restore(com.openggf.game.rewind.snapshot.SpriteManagerSnapshot snapshot) {
                        }
                    });
                    gameplayMode.attachGameplayManagers(
                            camera,
                            new com.openggf.timer.TimerManager(),
                            gameState,
                            new com.openggf.graphics.FadeManager(),
                            new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2),
                            new com.openggf.game.solid.DefaultSolidExecutionRegistry());
                    gameplayMode.attachLevelManagers(
                            new com.openggf.level.WaterSystem(),
                            new com.openggf.level.ParallaxManager(),
                            mock(com.openggf.physics.TerrainCollisionManager.class),
                            mock(com.openggf.physics.CollisionSystem.class),
                            spriteManager,
                            levelManager);
                    gameplayMode.attachSharedRegistries(
                            new com.openggf.game.zone.ZoneRuntimeRegistry(),
                            new com.openggf.game.palette.PaletteOwnershipRegistry(),
                            new com.openggf.game.animation.AnimatedTileChannelGraph(),
                            new com.openggf.game.render.SpecialRenderEffectRegistry(),
                            new com.openggf.game.render.AdvancedRenderModeController(),
                            new com.openggf.game.mutation.ZoneLayoutMutationPipeline());
                    return null;
                });

        MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class);
        donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(donorActive);
        donor.when(CrossGameFeatureProvider::isActive).thenReturn(false);

        return new BootstrapHarness(
                new Engine(services),
                graphics,
                levelManager,
                cacheManager,
                donor,
                gameplayFactory);
    }

    private static final class BootstrapHarness implements AutoCloseable {
        final Engine engine;
        final GraphicsManager graphics;
        final LevelManager levelManager;
        final TrackingS1ImageCacheManager cacheManager;
        final MockedStatic<CrossGameFeatureProvider> donor;
        final MockedStatic<GameplaySessionFactory> gameplayFactory;

        BootstrapHarness(Engine engine,
                         GraphicsManager graphics,
                         LevelManager levelManager,
                         TrackingS1ImageCacheManager cacheManager,
                         MockedStatic<CrossGameFeatureProvider> donor,
                         MockedStatic<GameplaySessionFactory> gameplayFactory) {
            this.engine = engine;
            this.graphics = graphics;
            this.levelManager = levelManager;
            this.cacheManager = cacheManager;
            this.donor = donor;
            this.gameplayFactory = gameplayFactory;
        }

        @Override
        public void close() {
            donor.close();
            gameplayFactory.close();
        }
    }

    private static GraphicsManager replaceGraphicsManagerSingleton(GraphicsManager replacement) throws Exception {
        Field field = GraphicsManager.class.getDeclaredField("graphicsManager");
        field.setAccessible(true);
        GraphicsManager previous = (GraphicsManager) field.get(null);
        field.set(null, replacement);
        return previous;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
