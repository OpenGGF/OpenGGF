package com.openggf.mods.code;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneAdapter;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRuntimeProfile;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.AnimatedPaletteProvider;
import com.openggf.data.AnimatedPatternProvider;
import com.openggf.data.Game;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.animation.DestinationPlan;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kModZoneRuntimeProfile;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

class TestModZoneRuntimeProfile {

    @AfterEach
    void clearSession() {
        SessionManager.clear();
    }

    @Test
    void sonic3kCustomZonesPublishTheExplicitFlatEmptyProfile() {
        assertEquals(ModZoneRuntimeProfile.flatEmpty(),
                Sonic3kModZoneRuntimeProfile.flatEmpty());
    }

    @ParameterizedTest
    @MethodSource("unsupportedProfiles")
    void unsupportedRuntimeRequirementsAreRejectedBeforeZonePublication(
            ModZoneRuntimeProfile unsupported) {
        GameModule base = mock(GameModule.class);
        ZoneRegistry stockRegistry = stockRegistry();
        ModZoneAdapter adapter = adapterFor(unsupported);
        when(base.getModZoneAdapter()).thenReturn(adapter);
        when(base.getZoneRegistry()).thenReturn(stockRegistry);

        ModRegistrationException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ModRegistrationException.class,
                () -> patchWithoutEvents().apply(base, mock(PatchContext.class)));

        assertEquals("alpha", failure.ownerModId());
        assertEquals("MOD_ZONE_RUNTIME_PROFILE_UNSUPPORTED", failure.findingCode());
    }

    private static java.util.stream.Stream<ModZoneRuntimeProfile> unsupportedProfiles() {
        return java.util.stream.Stream.of(
                new ModZoneRuntimeProfile(ModZoneRuntimeProfile.ScrollPolicy.FLAT,
                        true, false, false, false),
                new ModZoneRuntimeProfile(ModZoneRuntimeProfile.ScrollPolicy.FLAT,
                        false, true, false, false),
                new ModZoneRuntimeProfile(ModZoneRuntimeProfile.ScrollPolicy.FLAT,
                        false, false, true, false),
                new ModZoneRuntimeProfile(ModZoneRuntimeProfile.ScrollPolicy.FLAT,
                        false, false, false, true));
    }

    @Test
    void factoryAbsentCustomZoneDoesNotInheritStockLevelEvents() {
        AtomicInteger stockInitializations = new AtomicInteger();
        LevelEventProvider stockEvents = mock(LevelEventProvider.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            stockInitializations.incrementAndGet();
            return null;
        }).when(stockEvents).initLevel(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        GameModule base = mock(GameModule.class);
        when(base.getModZoneAdapter()).thenReturn(flatAdapter());
        ZoneRegistry stockRegistry = stockRegistry();
        when(base.getZoneRegistry()).thenReturn(stockRegistry);
        when(base.getLevelEventProvider()).thenReturn(stockEvents);

        GameModule decorated = patchWithoutEvents().apply(base, mock(PatchContext.class));
        int customZone = decorated.getZoneRegistry().getZoneCount() - 1;

        LevelEventProvider selected = decorated.getLevelEventProvider();
        selected.initLevel(customZone, 0);

        assertEquals(0, stockInitializations.get());
        assertEquals(ModZoneRuntimeProfile.flatEmpty(),
                ((ModZoneRegistry) decorated.getZoneRegistry())
                        .contributions().get(0).runtimeProfile());
    }

    @Test
    void stackedFactoryAbsentZonePreservesThePriorOwnersFaultBoundedEvents() {
        AtomicInteger priorInitializations = new AtomicInteger();
        AtomicInteger priorUpdates = new AtomicInteger();
        ZoneEventFactory priorFactory = () -> new LevelEventProvider() {
            @Override public void initLevel(int zone, int act) {
                priorInitializations.incrementAndGet();
            }
            @Override public void update() {
                priorUpdates.incrementAndGet();
            }
        };
        GameModule base = mock(GameModule.class);
        ZoneRegistry stockRegistry = stockRegistry();
        when(base.getZoneRegistry()).thenReturn(stockRegistry);
        when(base.getLevelEventProvider()).thenReturn(mock(LevelEventProvider.class));
        when(base.getModZoneAdapter()).thenReturn(flatAdapter());

        GameModule prior = patch("prior", priorFactory, boundary())
                .apply(base, mock(PatchContext.class));
        GameModule stacked = patch("outer", null, null)
                .apply(prior, mock(PatchContext.class));
        LevelEventProvider events = stacked.getLevelEventProvider();

        events.initLevel(1, 0);
        events.update();
        assertEquals(1, priorInitializations.get());
        assertEquals(1, priorUpdates.get());

        events.initLevel(2, 0);
        events.update();
        assertEquals(1, priorInitializations.get());
        assertEquals(1, priorUpdates.get(),
                "the outer factory-absent zone must not retain the prior active event manager");
    }

    @Test
    void stackedEventProvidersResolveOnlyTheInheritedStockRewindManager() {
        TestLevelEventManager stockEvents = new TestLevelEventManager();
        GameModule base = mock(GameModule.class);
        ZoneRegistry stockRegistry = stockRegistry();
        when(base.getZoneRegistry()).thenReturn(stockRegistry);
        when(base.getLevelEventProvider()).thenReturn(stockEvents);
        when(base.getModZoneAdapter()).thenReturn(flatAdapter());

        GameModule prior = patch("prior", null, null)
                .apply(base, mock(PatchContext.class));
        GameModule stacked = patch("outer", null, null)
                .apply(prior, mock(PatchContext.class));
        LevelEventProvider provider = stacked.getLevelEventProvider();

        assertSame(stockEvents,
                com.openggf.game.LevelEventRewindResolver.resolve(provider, 0));
        assertNull(com.openggf.game.LevelEventRewindResolver.resolve(provider, 1));
        assertNull(com.openggf.game.LevelEventRewindResolver.resolve(provider, 2));
    }

    @Test
    void customS3kZoneInstallsExplicitEmptyRuntimeContracts() throws Exception {
        ZoneRegistry stockRegistry = stockRegistry();
        AnimatedPatternManager inheritedPatterns = mock(AnimatedPatternManager.class);
        AnimatedPaletteManager inheritedPalettes = mock(AnimatedPaletteManager.class);
        Game game = mock(Game.class, withSettings().extraInterfaces(
                AnimatedPatternProvider.class, AnimatedPaletteProvider.class));
        when(((AnimatedPatternProvider) game).loadAnimatedPatternManager(any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(inheritedPatterns);
        when(((AnimatedPaletteProvider) game).loadAnimatedPaletteManager(any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(inheritedPalettes);
        Level customLevel = mock(Level.class);
        when(customLevel.getBlockPixelSize()).thenReturn(128);
        when(customLevel.getChunksPerBlockSide()).thenReturn(8);
        when(customLevel.getMinX()).thenReturn(0);
        when(customLevel.getMaxX()).thenReturn(0x100);
        when(customLevel.getMinY()).thenReturn(0);
        when(customLevel.getMaxY()).thenReturn(0x100);
        ObjectArtProvider inheritedArt = mock(ObjectArtProvider.class);
        when(inheritedArt.getRendererKeys()).thenReturn(List.of());
        when(inheritedArt.ensurePatternsCached(any(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        GameModule base = baseModule(stockRegistry, game, customLevel, inheritedArt);
        GameModule decorated = patchWithoutEvents().apply(base, mock(PatchContext.class));

        GameplayModeContext gameplay = SessionManager.openGameplaySession(
                base, decorated, missingDataSource(), null);
        WorldSession world = SessionManager.getCurrentWorldSession();
        world.setCurrentZone(1);
        LevelManager levelManager = manager(world);
        attachRuntime(gameplay, levelManager);
        levelManager.initGameModule(0x400);
        levelManager.loadLevelData(0x400);

        gameplay.getAnimatedTileChannelGraph().install(List.of(dummyChannel()));
        com.openggf.game.render.SpecialRenderEffect inheritedEffect = mock(
                com.openggf.game.render.SpecialRenderEffect.class);
        when(inheritedEffect.stage()).thenReturn(
                com.openggf.game.render.SpecialRenderEffectStage.AFTER_BACKGROUND);
        gameplay.getSpecialRenderEffectRegistry().register(inheritedEffect);
        gameplay.getAdvancedRenderModeController().register(mock(
                com.openggf.game.render.AdvancedRenderMode.class));
        gameplay.registerPlcArtAdapter(new SnapObjectArtProvider());

        levelManager.initAnimatedContent();
        levelManager.initZoneFeatures();
        levelManager.initArt();

        assertNull(levelManager.getAnimatedPatternManager());
        assertNull(levelManager.getAnimatedPaletteManager());
        assertTrue(gameplay.getAnimatedTileChannelGraph().channels().isEmpty());
        assertTrue(gameplay.getSpecialRenderEffectRegistry().isEmpty());
        assertTrue(gameplay.getAdvancedRenderModeController().isEmpty());
        assertFalse(gameplay.getRewindRegistry().capture().entries()
                .containsKey("s3k-plc-art"));
        verify(inheritedArt).loadArtForZone(-1);
        verify(inheritedArt).registerLevelTileArt(customLevel, -1);
        verify(inheritedArt, never()).loadArtForZone(customLevel.getZoneIndex());
        assertArrayEquals(new int[]{12, 34},
                decorated.getBackgroundScrollOverride(0x400, 12, 34));
    }

    private static ModBackedGamePatch patchWithoutEvents() {
        return patch("alpha", null, null);
    }

    private static ModBackedGamePatch patch(String owner, ZoneEventFactory eventFactory,
                                             ModFaultBoundary boundary) {
        ModLevelDefinition definition = TestS3kModZoneAdapter.definition(
                2, null, List.of(new ModPaletteClaim(2, 0, 0)));
        ModZoneContribution declared = new ModZoneContribution(
                "sky", new BakedLevelRef("level.json"), null, eventFactory);
        int ordinal = "outer".equals(owner) ? 1 : 0;
        PreparedModZone prepared = new PreparedModZone(
                owner, "sky", null, definition, eventFactory,
                "SKY", 0x400 + ordinal, 0x40 + ordinal, 0x20, 0x20);
        ModRegistrationPlan plan = new ModRegistrationPlan(
                owner, "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(prepared));
        return boundary == null ? new ModBackedGamePatch(plan)
                : new ModBackedGamePatch(plan, boundary);
    }

    private static ModZoneAdapter flatAdapter() {
        return adapterFor(Sonic3kModZoneRuntimeProfile.flatEmpty());
    }

    private static ModZoneAdapter adapterFor(ModZoneRuntimeProfile profile) {
        return new ModZoneAdapter() {
            @Override public void validate(String ownerModId, ModZoneLevelData level) { }
            @Override public Level load(String ownerModId, ModZoneLevelData level) {
                return mock(Level.class);
            }
            @Override public ModZoneRuntimeProfile runtimeProfile(String ownerModId,
                                                                  ModZoneLevelData level) {
                return profile;
            }
        };
    }

    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static GameModule baseModule(ZoneRegistry registry, Game game, Level level,
                                         ObjectArtProvider objectArt)
            throws Exception {
        GameModule base = mock(GameModule.class);
        ModZoneAdapter adapter = mock(ModZoneAdapter.class);
        when(adapter.runtimeProfile(any(), any())).thenReturn(
                Sonic3kModZoneRuntimeProfile.flatEmpty());
        when(adapter.load(any(), any())).thenReturn(level);
        when(base.getModZoneAdapter()).thenReturn(adapter);
        when(base.getZoneRegistry()).thenReturn(registry);
        when(base.createGame(any(GameDataSource.class))).thenReturn(game);
        when(base.getObjectArtProvider()).thenReturn(objectArt);
        when(base.getLevelEventProvider()).thenReturn(mock(LevelEventProvider.class));
        when(base.getGameplayPolicyProvider()).thenReturn(
                com.openggf.game.GameplayPolicyProvider.EMPTY);
        when(base.getIdentifier()).thenReturn("s3k");
        return base;
    }

    private static GameDataSource missingDataSource() {
        return new GameDataSource() {
            @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                return java.util.Optional.empty();
            }
            @Override public java.io.InputStream openAsset(String path)
                    throws java.io.IOException {
                throw new java.io.IOException("no asset");
            }
            @Override public String identity() { return "test:runtime-profile"; }
        };
    }

    private static LevelManager manager(WorldSession world) {
        EngineContext services = mock(EngineContext.class);
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        when(services.graphics()).thenReturn(mock(GraphicsManager.class));
        when(services.audio()).thenReturn(mock(AudioManager.class));
        when(services.configuration()).thenReturn(configuration);
        when(services.debugOverlay()).thenReturn(mock(DebugOverlayManager.class));
        when(services.profiler()).thenReturn(mock(PerformanceProfiler.class));
        when(services.crossGameFeatures()).thenReturn(mock(CrossGameFeatureProvider.class));
        return new LevelManager(new Camera(), new SpriteManager(), new ParallaxManager(),
                mock(CollisionSystem.class), new WaterSystem(), new GameStateManager(),
                services, world);
    }

    private static void attachRuntime(GameplayModeContext gameplay, LevelManager levelManager) {
        Camera camera = new Camera();
        SpriteManager sprites = new SpriteManager();
        ParallaxManager parallax = new ParallaxManager();
        TerrainCollisionManager terrain = mock(TerrainCollisionManager.class);
        CollisionSystem collision = new CollisionSystem(terrain);
        WaterSystem water = new WaterSystem();
        gameplay.attachGameplayManagers(camera, new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S3K),
                new DefaultSolidExecutionRegistry());
        gameplay.attachLevelManagers(water, parallax, terrain, collision, sprites, levelManager);
        gameplay.attachSharedRegistries(new ZoneRuntimeRegistry(),
                new PaletteOwnershipRegistry(), new AnimatedTileChannelGraph(),
                new SpecialRenderEffectRegistry(), new AdvancedRenderModeController(),
                new ZoneLayoutMutationPipeline());
    }

    private static AnimatedTileChannel dummyChannel() {
        return new AnimatedTileChannel("stock", () -> true, context -> 0,
                DestinationPlan.single(0), AnimatedTileCachePolicy.ALWAYS, context -> { });
    }

    private static final class SnapObjectArtProvider
            implements ObjectArtProvider, RewindSnapshottable<Integer> {
        @Override public void loadArtForZone(int zoneIndex) { }
        @Override public com.openggf.level.render.PatternSpriteRenderer getRenderer(String key) { return null; }
        @Override public com.openggf.level.objects.ObjectSpriteSheet getSheet(String key) { return null; }
        @Override public com.openggf.sprites.animation.SpriteAnimationSet getAnimations(String key) { return null; }
        @Override public int getZoneData(String key, int zoneIndex) { return -1; }
        @Override public com.openggf.level.Pattern[] getHudDigitPatterns() { return null; }
        @Override public com.openggf.level.Pattern[] getHudTextPatterns() { return null; }
        @Override public com.openggf.level.Pattern[] getHudLivesPatterns() { return null; }
        @Override public com.openggf.level.Pattern[] getHudLivesNumbers() { return null; }
        @Override public List<String> getRendererKeys() { return List.of(); }
        @Override public int ensurePatternsCached(GraphicsManager graphics, int base) { return base; }
        @Override public boolean isReady() { return true; }
        @Override public String key() { return "s3k-plc-art"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer snapshot) { }
    }

    private static final class TestLevelEventManager
            extends com.openggf.game.AbstractLevelEventManager {
        @Override protected int getEventDataFgSize() { return 0; }
        @Override protected int getEventDataBgSize() { return 0; }
        @Override protected int getRoutineStride() { return 2; }
        @Override protected void onInitLevel(int zone, int act) { }
        @Override protected void onUpdate() { }
        @Override public com.openggf.game.PlayerCharacter getPlayerCharacter() {
            return com.openggf.game.PlayerCharacter.SONIC_ALONE;
        }
    }

    private static ZoneRegistry stockRegistry() {
        ZoneRegistry registry = mock(ZoneRegistry.class);
        when(registry.getAllZones()).thenReturn(List.of(List.of(
                new com.openggf.level.LevelDescriptor() {
                    @Override public int levelIndex() { return 0; }
                    @Override public int startX() { return 0; }
                    @Override public int startY() { return 0; }
                })));
        when(registry.getZoneCount()).thenReturn(1);
        when(registry.progressionTopology()).thenReturn(
                ZoneProgressionPlan.ZoneTopology.linear(1));
        when(registry.progressionPlan()).thenReturn(ZoneProgressionPlan.LINEAR);
        return registry;
    }
}
