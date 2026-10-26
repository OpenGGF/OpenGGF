package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.Game;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.MusicReference;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.RomDataSource;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.level.objects.TouchResponseTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Isolated
class TestGameDataSourceSharedFetches {
    @AfterEach void clearSession() { SessionManager.clear(); }

    @Test
    void cartridgeSourceAdaptersRequireRomCapabilityButPreserveStockRomBytes(@TempDir Path directory) throws Exception {
        Sonic1GameModule sonic1 = mock(Sonic1GameModule.class, CALLS_REAL_METHODS);
        Sonic2GameModule sonic2 = mock(Sonic2GameModule.class, CALLS_REAL_METHODS);
        Sonic3kGameModule sonic3k = mock(Sonic3kGameModule.class, CALLS_REAL_METHODS);
        var modules = java.util.List.<GameModule>of(sonic1, sonic2, sonic3k);
        GameDataSource missing = missingSource();
        for (GameModule module : modules) {
            assertThrows(IllegalStateException.class, () -> module.createGame(missing));
            assertThrows(IllegalStateException.class, () -> module.createTouchResponseTable(missing));
        }

        Path path = directory.resolve("source.gen");
        Files.write(path, new byte[] { 0x12, 0x34, 0x56 });
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(path.toString()));
            Game expectedGame = mock(Game.class);
            TouchResponseTable expectedTable = mock(TouchResponseTable.class);
            // Keep the source adapters real while isolating cartridge decoding.
            doReturn(expectedGame).when(sonic1).createGame(rom);
            doReturn(expectedGame).when(sonic2).createGame(rom);
            doReturn(expectedGame).when(sonic3k).createGame(rom);
            doReturn(expectedTable).when(sonic1).createTouchResponseTable(any(RomByteReader.class));
            doReturn(expectedTable).when(sonic2).createTouchResponseTable(any(RomByteReader.class));
            doReturn(expectedTable).when(sonic3k).createTouchResponseTable(any(RomByteReader.class));
            RomDataSource source = new RomDataSource(rom, "rom:test-uncatalogued");

            for (GameModule module : modules) {
                assertSame(expectedGame, module.createGame(source));
                assertSame(expectedTable, module.createTouchResponseTable(source));
            }
            ArgumentCaptor<RomByteReader> reader = ArgumentCaptor.forClass(RomByteReader.class);
            verify(sonic1).createTouchResponseTable(reader.capture());
            verify(sonic2).createTouchResponseTable(reader.capture());
            verify(sonic3k).createTouchResponseTable(reader.capture());
            for (RomByteReader value : reader.getAllValues()) {
                assertArrayEquals(new byte[] { 0x12, 0x34, 0x56 }, value.slice(0, 3));
            }
        }
    }

    @Test
    void stockCompositionRootPinsExactRomWithStableDiagnosticIdentity() throws Exception {
        Rom rom = mock(Rom.class);
        when(rom.readAllBytes()).thenReturn(new byte[] { 1, 2, 3 });
        GameModule module = mock(GameModule.class);
        when(module.getIdentifier()).thenReturn("s2");

        RomDataSource source = StockGameDataSources.pinned(rom, module);

        assertSame(rom, source.rom().orElseThrow());
        assertEquals("rom:s2:sha256:039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                source.identity());
    }

    @Test
    void providerlessWaterInitializationNeverQueriesRomCapability() throws Exception {
        LevelManager levelManager = mock(LevelManager.class);
        levelManager.gameModule = mock(GameModule.class);
        levelManager.zoneFeatureProvider = null;

        new LevelWaterCoordinator(levelManager).initialize();

        verify(levelManager, never()).worldDataSource();
    }

    @Test
    void lazyParallaxConsumerAcceptsSessionWithoutRomCapability() {
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        SessionManager.openGameplaySession(module, module, missingSource(), null);

        assertFalse(new ParallaxManager().advanceCameraDrivenScroll(
                0, 0, mock(Camera.class), 0));
    }

    @Test
    void levelModuleAndAudioNullPathsUseExactSourceWithoutRomManager() throws Exception {
        GameDataSource source = spy(missingSource());
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(com.openggf.game.AbstractLevelInitProfile.EMPTY);
        Game game = mock(Game.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        when(module.createGame(source)).thenReturn(game);
        GameAudioProfile profile = mock(GameAudioProfile.class);
        when(module.getAudioProfile()).thenReturn(profile);
        when(module.getLevelMusicReference(anyInt(), anyInt())).thenReturn(new MusicReference.Stock(1));
        when(game.getSoundMap()).thenReturn(Map.of());
        ParallaxManager parallax = mock(ParallaxManager.class);
        AudioManager audio = spy(AudioManager.class);
        doNothing().when(audio).setSoundMap(anyMap());
        doNothing().when(audio).resetRingSound();
        doNothing().when(audio).playMusic(anyInt());
        LevelManager levelManager = manager(source, module, parallax, audio, mock(WaterSystem.class));

        // This fixture observes the production ingress without realizing a native
        // loader. The four source-identity controls below use the real manager.
        try (var audioIngress = mockStatic(AudioManagerInternalAccess.class)) {
            levelManager.initGameModule(0);
            levelManager.initAudio(0);

            verify(parallax).load(null);
            verify(module).createGame(source);
            audioIngress.verify(() -> AudioManagerInternalAccess.installMatchedSource(
                    same(audio), isNull(), same(profile)));
            verify(source, atLeastOnce()).rom();
        }
    }

    @Test
    void objectInitializationUsesCanonicalSourceFactory() throws Exception {
        GameDataSource source = missingSource();
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        IOException sentinel = new IOException("source overload reached");
        when(module.createTouchResponseTable(source)).thenThrow(sentinel);
        LevelManager levelManager = manager(source, module, mock(ParallaxManager.class),
                mock(AudioManager.class), mock(WaterSystem.class));
        levelManager.gameModule = module;

        assertSame(sentinel, assertThrows(IOException.class, levelManager::initObjectManager));
        verify(module).createTouchResponseTable(source);
    }

    @Test
    void zoneFeatureRomLookupIsLazyAndProviderReceivesNull() throws Exception {
        GameDataSource source = spy(missingSource());
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        LevelManager levelManager = manager(source, module, mock(ParallaxManager.class),
                mock(AudioManager.class), mock(WaterSystem.class));

        levelManager.initializeZoneFeatureProvider(null);
        verify(source, never()).rom();

        ZoneFeatureProvider provider = mock(ZoneFeatureProvider.class);
        levelManager.initializeZoneFeatureProvider(provider);
        verify(source).rom();
        verify(provider).initZoneFeatures(isNull(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void waterProviderAndLegacyFallbackReceiveNullFromSource() throws Exception {
        GameDataSource source = spy(missingSource());
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        WaterSystem water = mock(WaterSystem.class);
        LevelManager levelManager = manager(source, module, mock(ParallaxManager.class),
                mock(AudioManager.class), water);
        levelManager.gameModule = module;
        WaterDataProvider provider = mock(WaterDataProvider.class);
        when(module.getWaterDataProvider()).thenReturn(provider);

        levelManager.waterCoordinator.initialize();

        verify(water).loadForLevelFromProvider(eq(provider), isNull(), anyInt(), anyInt(),
                any(), eq(false));

        reset(source, module, water);
        when(source.rom()).thenReturn(Optional.empty());
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        when(module.getWaterDataProvider()).thenReturn(null);
        ZoneFeatureProvider legacy = mock(ZoneFeatureProvider.class);
        when(legacy.hasWater(anyInt())).thenReturn(true);
        levelManager.zoneFeatureProvider = legacy;
        levelManager.level = mock(Level.class);
        when(water.hasWater(anyInt(), anyInt())).thenReturn(false);

        levelManager.waterCoordinator.initialize();

        verify(water).loadForLevel(isNull(), anyInt(), anyInt(), any());
    }

    @Test
    void romSessionPropagatesTheExactSameRomAcrossSharedConsumers() throws Exception {
        Rom rom = mock(Rom.class);
        RomDataSource source = new RomDataSource(rom, "rom:equivalence");
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(com.openggf.game.AbstractLevelInitProfile.EMPTY);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        Game game = mock(Game.class);
        when(module.createGame(source)).thenReturn(game);
        GameAudioProfile profile = mock(GameAudioProfile.class);
        when(module.getAudioProfile()).thenReturn(profile);
        when(module.getLevelMusicReference(anyInt(), anyInt())).thenReturn(new MusicReference.Stock(1));
        when(game.getSoundMap()).thenReturn(Map.of());
        ParallaxManager parallax = mock(ParallaxManager.class);
        AudioManager audio = spy(AudioManager.class);
        doNothing().when(audio).setSoundMap(anyMap());
        doNothing().when(audio).resetRingSound();
        doNothing().when(audio).playMusic(anyInt());
        WaterSystem water = mock(WaterSystem.class);
        LevelManager levelManager = manager(source, module, parallax, audio, water);

        try (var audioIngress = mockStatic(AudioManagerInternalAccess.class)) {
            levelManager.initGameModule(0);
            levelManager.initAudio(0);
            ZoneFeatureProvider zone = mock(ZoneFeatureProvider.class);
            levelManager.initializeZoneFeatureProvider(zone);
            WaterDataProvider waterProvider = mock(WaterDataProvider.class);
            when(module.getWaterDataProvider()).thenReturn(waterProvider);
            levelManager.waterCoordinator.initialize();

            verify(parallax).load(same(rom));
            audioIngress.verify(() -> AudioManagerInternalAccess.installMatchedSource(
                    same(audio), same(rom), same(profile)));
            verify(zone).initZoneFeatures(same(rom), anyInt(), anyInt(), anyInt());
            verify(water).loadForLevelFromProvider(eq(waterProvider), same(rom), anyInt(), anyInt(),
                    any(), eq(false));
        }
    }

    @Test
    void configureAudioReplacesDistinctProfileWithoutLoadingEitherWrongRom() throws Exception {
        Rom previousRom = mock(Rom.class, "previousRom");
        Rom destinationRom = mock(Rom.class, "destinationRom");
        ExactRomAudioProfile previousProfile = new ExactRomAudioProfile(previousRom);
        ExactRomAudioProfile destinationProfile = new ExactRomAudioProfile(destinationRom);
        AudioManager audio = initializedAudioManager();
        try {
            audioManager(previousRom, previousProfile, audio).configureAudio();
            audio.presentationFrameRate();

            // Both factories assert before returning a loader: a transient wrong-ROM
            // preparation must fail even if the destination profile later repairs it.
            audioManager(destinationRom, destinationProfile, audio).configureAudio();

            assertSame(destinationProfile, audio.getAudioProfile());
            assertSame(destinationRom, readField(baseAudioSource(audio), "rom"));
            assertSame(destinationProfile.loader, readField(baseAudioSource(audio), "loader"));
            assertEquals(1, previousProfile.loaderCreations);
            assertEquals(1, destinationProfile.loaderCreations);
        } finally {
            audio.destroy();
        }
    }

    @Test
    void configureAudioWithNoInstalledProfileBindsDestinationRomBeforePreparingLoader() throws Exception {
        Rom previousRom = mock(Rom.class, "previousRom");
        Rom destinationRom = mock(Rom.class, "destinationRom");
        ExactRomAudioProfile destinationProfile = new ExactRomAudioProfile(destinationRom);
        AudioManager audio = initializedAudioManager();
        try {
            // A ROM may already be bound while the profile is still cold.
            audio.setRom(previousRom);
            assertNull(audio.getAudioProfile());

            audioManager(destinationRom, destinationProfile, audio).configureAudio();

            assertSame(destinationProfile, audio.getAudioProfile());
            assertSame(destinationRom, readField(baseAudioSource(audio), "rom"));
            assertSame(destinationProfile.loader, readField(baseAudioSource(audio), "loader"));
            assertEquals(1, destinationProfile.loaderCreations);
        } finally {
            audio.destroy();
        }
    }

    @Test
    void configureAudioWithSameProfileAndRomPreservesLoaderAndPresentationIdentity() throws Exception {
        Rom rom = mock(Rom.class);
        ExactRomAudioProfile profile = new ExactRomAudioProfile(rom);
        AudioManager audio = initializedAudioManager();
        try {
            LevelManager levelManager = audioManager(rom, profile, audio);
            levelManager.configureAudio();
            audio.presentationFrameRate();
            Object source = baseAudioSource(audio);
            Object producer = AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class);
            Object session = AudioManagerTestDiagnostics.presentationField(audio, "shadowSmpsSession", Object.class);
            assertSame(profile.loader, readField(source, "loader"));
            assertNotNull(producer);
            assertNotNull(session);

            levelManager.configureAudio();

            assertSame(profile, audio.getAudioProfile());
            assertSame(source, baseAudioSource(audio));
            assertSame(profile.loader, readField(baseAudioSource(audio), "loader"));
            assertSame(producer, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class));
            assertSame(session, AudioManagerTestDiagnostics.presentationField(audio, "shadowSmpsSession", Object.class));
            assertEquals(1, profile.loaderCreations);
        } finally {
            audio.destroy();
        }
    }

    @Test
    void configureAudioWithSameProfileAndNewRomPreparesOnlyTheDestinationLoader() throws Exception {
        Rom previousRom = mock(Rom.class, "previousRom");
        Rom destinationRom = mock(Rom.class, "destinationRom");
        SmpsLoader previousLoader = new AudioTestFixtures.StubSmpsLoader();
        SmpsLoader destinationLoader = new AudioTestFixtures.StubSmpsLoader();
        int[] loaderCreations = {0};
        GameAudioProfile profile = new AudioTestFixtures.StubAudioProfile(previousLoader) {
            @Override public SmpsLoader createSmpsLoader(Rom rom) {
                if (rom == previousRom) {
                    loaderCreations[0]++;
                    return previousLoader;
                }
                assertSame(destinationRom, rom, "same-profile replacement must use an exact source ROM");
                loaderCreations[0]++;
                return destinationLoader;
            }
        };
        AudioManager audio = initializedAudioManager();
        try {
            audioManager(previousRom, profile, audio).configureAudio();
            assertSame(previousLoader, readField(baseAudioSource(audio), "loader"));

            audioManager(destinationRom, profile, audio).configureAudio();

            assertSame(profile, audio.getAudioProfile());
            assertSame(destinationRom, readField(baseAudioSource(audio), "rom"));
            assertSame(destinationLoader, readField(baseAudioSource(audio), "loader"));
            assertEquals(2, loaderCreations[0]);
        } finally {
            audio.destroy();
        }
    }

    @Test
    void allFiveSharedFetchSitesUseTheWorldSessionSourceAndKeepGuardsLazy() throws Exception {
        String levelManager = Files.readString(Path.of(
                "src/main/java/com/openggf/level/LevelManager.java"));
        String water = Files.readString(Path.of(
                "src/main/java/com/openggf/level/LevelWaterCoordinator.java"));
        String parallax = Files.readString(Path.of(
                "src/main/java/com/openggf/level/ParallaxManager.java"));
        String engine = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));

        assertFalse(levelManager.contains("GameServices.rom().getRom()"));
        assertFalse(water.contains("GameServices.rom().getRom()"));
        assertFalse(parallax.contains("GameServices.rom().getRom()"));
        assertTrue(levelManager.contains("gameModule.createGame(source)"));
        String configureAudio = levelManager.substring(
                levelManager.indexOf("public void configureAudio()"),
                levelManager.indexOf("public void beginLevelEntry()"));
        assertTrue(configureAudio.contains(
                "var rom = worldSession.getDataSource().rom().orElse(null);"));
        assertTrue(configureAudio.contains(
                "AudioManagerInternalAccess.installMatchedSource("));
        assertTrue(configureAudio.contains(
                "audioManager, rom, gameModule.getAudioProfile()"));
        assertTrue(levelManager.contains(
                "gameModule.createTouchResponseTable(worldSession.getDataSource())"));
        assertTrue(levelManager.indexOf("if (zoneFeatureProvider != null)")
                < levelManager.indexOf("Rom rom = worldSession.getDataSource().rom().orElse(null)",
                levelManager.indexOf("initializeZoneFeatureProvider")));
        assertTrue(water.indexOf("if (waterProvider != null)")
                < water.indexOf("levelManager.worldDataSource().rom().orElse(null)"));
        // The scroll provider still resolves ROM bytes through the world-session
        // data source, but must do so null-safely: a lazy scroll probe can run
        // with no active WorldSession (e.g. the missing-ROM logging test), where
        // the throwing GameServices.worldSession() accessor would break it.
        assertTrue(parallax.contains(
                "worldSession.getDataSource().rom().orElse(null)"));
        assertTrue(parallax.contains("SessionManager.getCurrentWorldSession()"),
                "scroll provider must resolve the world session null-safely so a "
                        + "probe with no active session does not throw");
        assertFalse(engine.contains(
                "SessionManager.openGameplaySession(rootModule, module, null)"));
        assertTrue(engine.contains("StockGameDataSources.pinned(rom, rootModule)"));
    }

    private static AudioManager initializedAudioManager() {
        AudioManager audio = AudioManager.createSilent();
        audio.setBackendForLaunch(new AudioTestFixtures.RecordingAudioBackend());
        return audio;
    }

    private static LevelManager audioManager(Rom rom, GameAudioProfile profile, AudioManager audio) {
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        when(module.getAudioProfile()).thenReturn(profile);
        LevelManager levelManager = manager(new RomDataSource(rom, "rom:audio-test"), module,
                mock(ParallaxManager.class), audio, mock(WaterSystem.class));
        levelManager.gameModule = module;
        levelManager.game = mock(Game.class);
        when(levelManager.game.getSoundMap()).thenReturn(Map.of());
        return levelManager;
    }

    private static Object baseAudioSource(AudioManager audio) throws ReflectiveOperationException {
        var field = AudioManager.class.getDeclaredField("sourcePublication");
        field.setAccessible(true);
        return readField(field.get(audio), "baseAudioSource");
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static final class ExactRomAudioProfile extends AudioTestFixtures.StubAudioProfile {
        private final Rom expectedRom;
        private final SmpsLoader loader;
        private int loaderCreations;

        private ExactRomAudioProfile(Rom expectedRom) {
            this(expectedRom, new AudioTestFixtures.StubSmpsLoader());
        }

        private ExactRomAudioProfile(Rom expectedRom, SmpsLoader loader) {
            super(loader);
            this.expectedRom = expectedRom;
            this.loader = loader;
        }

        @Override public SmpsLoader createSmpsLoader(Rom rom) {
            assertSame(expectedRom, rom, "profile must only prepare its exact source ROM");
            loaderCreations++;
            return super.createSmpsLoader(rom);
        }
    }

    private static GameDataSource missingSource() {
        return new GameDataSource() {
            @Override public Optional<Rom> rom() { return Optional.empty(); }
            @Override public java.io.InputStream openAsset(String normalizedPath)
                    throws IOException {
                throw new IOException("no assets");
            }
            @Override public String identity() { return "missing:test"; }
        };
    }

    private static LevelManager manager(GameDataSource source, GameModule module,
            ParallaxManager parallax, AudioManager audio, WaterSystem water) {
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(zones.getAllZones()).thenReturn(
                java.util.List.of(java.util.List.of(mock(LevelDescriptor.class))));
        when(zones.progressionPlan()).thenReturn(ZoneProgressionPlan.LINEAR);
        when(zones.progressionTopology()).thenReturn(
                ZoneProgressionPlan.ZoneTopology.linear(1));
        when(module.getZoneRegistry()).thenReturn(zones);
        SessionManager.openGameplaySession(module, module, source, null);
        EngineContext services = mock(EngineContext.class);
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        when(services.graphics()).thenReturn(mock(GraphicsManager.class));
        when(services.audio()).thenReturn(audio);
        when(services.configuration()).thenReturn(configuration);
        when(services.debugOverlay()).thenReturn(mock(DebugOverlayManager.class));
        when(services.profiler()).thenReturn(mock(PerformanceProfiler.class));
        when(services.crossGameFeatures()).thenReturn(mock(CrossGameFeatureProvider.class));
        WorldSession world = SessionManager.getCurrentWorldSession();
        return new LevelManager(mock(Camera.class), mock(SpriteManager.class),
                parallax, mock(CollisionSystem.class), water,
                new com.openggf.game.GameStateManager(), services, world);
    }
}
