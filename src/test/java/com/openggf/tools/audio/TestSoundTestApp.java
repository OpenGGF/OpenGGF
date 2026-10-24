package com.openggf.tools.audio;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.debug.SoundTestCatalog;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1SoundTestCatalog;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SoundTestCatalog;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSoundTestCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.openggf.configuration.SonicConfiguration.DEFAULT_ROM;
import static com.openggf.configuration.SonicConfiguration.SONIC_1_ROM;
import static com.openggf.configuration.SonicConfiguration.SONIC_2_ROM;
import static com.openggf.configuration.SonicConfiguration.SONIC_3K_ROM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSoundTestApp {
    @TempDir
    Path configurationDirectory;

    private EngineContext previousEngine;
    private SonicConfigurationService configuration;

    @BeforeEach
    void installIsolatedConfiguration() {
        previousEngine = EngineServices.current();
        configuration = SonicConfigurationService.createStandalone(configurationDirectory);
        configuration.setConfigValue(DEFAULT_ROM, "s2");
        configuration.setConfigValue(SONIC_1_ROM, "configured-s1.gen");
        configuration.setConfigValue(SONIC_2_ROM, "configured-s2.gen");
        configuration.setConfigValue(SONIC_3K_ROM, "configured-s3k.gen");
        EngineServices.configure(new EngineContext(configuration, previousEngine.graphics(),
                previousEngine.audio(), previousEngine.roms(), previousEngine.profiler(),
                previousEngine.debugOverlay(), previousEngine.playbackDebug(),
                previousEngine.romDetection(), previousEngine.crossGameFeatures(),
                previousEngine.moduleResolutionService()));
    }

    @AfterEach
    void restoreEngine() {
        EngineServices.configure(previousEngine);
    }

    @ParameterizedTest
    @CsvSource({"S1, s1", "s2, s2", "S3K, s3k"})
    void noArgumentsUseTheConfiguredGameAndItsRom(String configuredGame, String gameId)
            throws Exception {
        configuration.setConfigValue(DEFAULT_ROM, configuredGame);

        assertEquals(new ParsedOptions("configured-" + gameId + ".gen", gameId,
                -1, false, false, true), parse());
    }

    @ParameterizedTest
    @CsvSource({"S1, s1", "s2, s2", "S3K, s3k"})
    void explicitGameOverridesConfigurationInBothArgumentForms(String argument, String gameId)
            throws Exception {
        configuration.setConfigValue(DEFAULT_ROM, "s3k");
        ParsedOptions expected = new ParsedOptions("configured-" + gameId + ".gen", gameId,
                -1, false, false, true);

        assertEquals(expected, parse("--game", argument));
        assertEquals(expected, parse("--game=" + argument));
    }

    @Test
    void explicitRomOverridesTheSelectedGamesConfiguredPathInBothArgumentForms()
            throws Exception {
        String path = configurationDirectory.resolve("selected ROM.gen").toString();
        ParsedOptions expected = new ParsedOptions(path, "s1", -1, false, false, true);

        assertEquals(expected, parse("--rom", path, "--game", "S1"));
        assertEquals(expected, parse("--game=S1", "--rom=" + path));
    }

    @ParameterizedTest
    @CsvSource({"0x8C, 140", "0X81, 129", "140, 140", "0, 0"})
    void songIdsAcceptHexAndDecimalInBothArgumentForms(String argument, int expected)
            throws Exception {
        assertEquals(expected, parse("--song", argument).songId());
        assertEquals(expected, parse("--song=" + argument).songId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0xZZ", "-1", "2147483648"})
    void invalidSongIdsRetainTheCatalogDefaultSentinel(String argument) throws Exception {
        assertEquals(-1, parse("--song", argument).songId());
        assertEquals(-1, parse("--song=" + argument).songId());
    }

    @Test
    void uiIsTheDefaultAndTheLastConsoleOrUiFlagWins() throws Exception {
        assertTrue(parse("--game=s1").interactiveWindow());
        assertTrue(parse("--ui").interactiveWindow());
        assertFalse(parse("--console").interactiveWindow());
        assertTrue(parse("--console", "--ui").interactiveWindow());
        assertFalse(parse("--ui", "--console").interactiveWindow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--null-audio", "--no-audio"})
    void bothSilentAliasesSelectNoDeviceOutput(String argument) throws Exception {
        assertTrue(parse(argument).nullAudio());
        assertFalse(parse().nullAudio());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--help", "-h"})
    void helpAliasesRequestUsage(String argument) throws Exception {
        assertTrue(parse(argument).help());
        assertFalse(parse().help());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    void selectedGamePairsItsProfileCatalogAndDefaultSong(GameSelection expected)
            throws Exception {
        ParsedOptions options = parse("--game", expected.gameId());
        GameAudioProfile profile = profile(options.gameId());
        SoundTestCatalog catalog = catalog(options.gameId());

        assertEquals(expected.profileType(), profile.getClass());
        assertEquals(expected.catalogType(), catalog.getClass());
        assertEquals(expected.presentationGameId(), profile.presentationGameId());
        assertEquals(expected.gameName(), catalog.getGameName());
        assertEquals(-1, options.songId());
        assertEquals(expected.defaultSong(), catalog.getDefaultSongId());
        assertTrue(catalog.getValidSongs().contains(expected.defaultSong()));
        assertEquals(expected.defaultTitle(), catalog.lookupTitle(expected.defaultSong()));
        assertEquals(expected.sfxBase(), catalog.getSfxIdBase());
        assertEquals(expected.sfxMax(), catalog.getSfxIdMax());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    void sfxProbeIncludesBothCatalogBoundsAndRejectsMissingEffects(GameSelection expected)
            throws Exception {
        SoundTestCatalog catalog = catalog(expected.gameId());
        List<Integer> requestedIds = new ArrayList<>();
        SmpsLoader loader = mock(SmpsLoader.class);
        var data = new AudioTestFixtures.StubSmpsData("available-sfx");
        when(loader.loadSfx(anyInt())).thenAnswer(invocation -> {
            int id = invocation.getArgument(0);
            requestedIds.add(id);
            return id <= expected.sfxBase() || id >= expected.sfxMax() ? data : null;
        });
        Method probe = SoundTestApp.class.getDeclaredMethod(
                "probeValidSfx", SmpsLoader.class, int.class, int.class);
        probe.setAccessible(true);

        Object valid = probe.invoke(null, loader, catalog.getSfxIdBase(), catalog.getSfxIdMax());

        assertEquals(IntStream.rangeClosed(expected.sfxBase(), expected.sfxMax())
                .boxed().toList(), requestedIds);
        assertEquals(new TreeSet<>(List.of(expected.sfxBase(), expected.sfxMax())), valid);
    }

    private static Stream<GameSelection> games() {
        return Stream.of(
                new GameSelection("s1", Sonic1AudioProfile.class, Sonic1SoundTestCatalog.class,
                        "base", "Sonic 1", 0x81, "Green Hill Zone", 0xA0, 0xD0),
                new GameSelection("s2", Sonic2AudioProfile.class, Sonic2SoundTestCatalog.class,
                        "base", "Sonic 2", 0x8C, "Chemical Plant Zone", 0xA0, 0xF0),
                new GameSelection("s3k", Sonic3kAudioProfile.class, Sonic3kSoundTestCatalog.class,
                        "s3k", "Sonic 3&K", 0x01, "Angel Island Zone Act 1", 0x33, 0xDB));
    }

    private static GameAudioProfile profile(String gameId) throws Exception {
        Method factory = SoundTestApp.class.getDeclaredMethod("createProfileForGame", String.class);
        factory.setAccessible(true);
        return (GameAudioProfile) factory.invoke(null, gameId);
    }

    private static SoundTestCatalog catalog(String gameId) throws Exception {
        Method factory = SoundTestApp.class.getDeclaredMethod("createCatalogForGame", String.class);
        factory.setAccessible(true);
        return (SoundTestCatalog) factory.invoke(null, gameId);
    }

    private static ParsedOptions parse(String... arguments) throws Exception {
        Class<?> optionsType = Class.forName(SoundTestApp.class.getName() + "$Options");
        Method parser = optionsType.getDeclaredMethod("fromArgs", String[].class);
        parser.setAccessible(true);
        Object options = parser.invoke(null, (Object) arguments);
        return new ParsedOptions((String) field(options, "romPath"),
                (String) field(options, "gameId"), (int) field(options, "songId"),
                (boolean) field(options, "nullAudio"), (boolean) field(options, "help"),
                (boolean) field(options, "interactiveWindow"));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private record ParsedOptions(String romPath, String gameId, int songId,
                                 boolean nullAudio, boolean help, boolean interactiveWindow) {
    }

    private record GameSelection(String gameId, Class<? extends GameAudioProfile> profileType,
                                 Class<? extends SoundTestCatalog> catalogType,
                                 String presentationGameId, String gameName, int defaultSong,
                                 String defaultTitle, int sfxBase, int sfxMax) {
        @Override
        public String toString() {
            return gameId;
        }
    }
}
