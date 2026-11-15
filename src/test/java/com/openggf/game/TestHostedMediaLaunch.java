package com.openggf.game;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.internal.DurableSaveHost;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.launch.HostedMediaLaunch;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.MasterTitleLaunchCoordinator;
import com.openggf.game.soniccd.media.CdHostedMedia;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;

/**
 * The launcher's seam for a stock game that is not a cartridge ROM: the registry's media kind, the hosted
 * shelf entry, the disc search and its messages, the shelf's Start, and the stock team a hosted launch uses.
 * No disc is needed: the media property is cleared so only the configured ROM folder is searched.
 */
@Isolated
class TestHostedMediaLaunch {
    @TempDir Path folder;
    private String mediaRoot;

    @BeforeEach void clearMediaProperty() {
        mediaRoot = System.getProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        System.clearProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
    }

    @AfterEach void restore() {
        if (mediaRoot != null) System.setProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY, mediaRoot);
        UiMessages.selectLanguage("en");
    }

    private SonicConfigurationService config() {
        var config = SonicConfigurationService.createStandalone(folder);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, folder.toString());
        return config;
    }

    @Test void theRegistryNamesWhatEachStockGameBootsFrom() {
        assertEquals(GameModuleRegistry.MediaKind.CARTRIDGE_ROM, GameModuleRegistry.mediaKind(GameId.S1));
        assertEquals(GameModuleRegistry.MediaKind.CARTRIDGE_ROM, GameModuleRegistry.mediaKind(GameId.S2));
        assertEquals(GameModuleRegistry.MediaKind.CARTRIDGE_ROM, GameModuleRegistry.mediaKind(GameId.S3K));
        assertEquals(GameModuleRegistry.MediaKind.HOSTED_MEDIA, GameModuleRegistry.mediaKind(GameId.SCD));
        assertEquals(List.of(GameId.SCD), GameModuleRegistry.hostedMediaGames());
        assertThrows(IllegalArgumentException.class, () -> GameModuleRegistry.hostedMedia(GameId.S2));
        HostedGameMedia media = GameModuleRegistry.hostedMedia(GameId.SCD);
        assertEquals("Sonic CD", media.menuLabel());
        assertNotNull(GameModuleRegistry.createStockModule(GameId.SCD).getGameService(DurableSaveHost.class),
                "a player's launch can attach Sonic CD's backup RAM to the save root");

        assertEquals(Optional.of(GameId.SCD), HostedMediaLaunch.hostedGame("scd"));
        assertEquals(Optional.of(GameId.SCD), HostedMediaLaunch.hostedGame("SCD"));
        for (String cartridgeOrUnknown : new String[] {"s1", "s2", "s3k", "kis2", "", null}) {
            assertEquals(Optional.empty(), HostedMediaLaunch.hostedGame(cartridgeOrUnknown), cartridgeOrUnknown);
        }
        assertTrue(ConfigCatalog.meta(SonicConfiguration.DEFAULT_ROM).allowedValues().contains("scd"),
                "roms.default: scd starts Sonic CD without the master title");

        var entry = MasterTitleEntry.Hosted.of(GameId.SCD);
        assertEquals("scd", entry.gameId());
        assertEquals("Sonic CD", entry.menuLabel());
    }

    @Test void anEmptyRomFolderNamesWhereToPutTheDisc() {
        var availability = GameModuleRegistry.hostedMedia(GameId.SCD).probe(config());
        assertFalse(availability.available());
        assertTrue(availability.detail().contains("No Sonic CD disc image was found in " + folder), availability.detail());
        assertTrue(availability.detail().contains("roms.directory"), availability.detail());
        var refused = assertThrows(java.io.IOException.class,
                () -> GameModuleRegistry.hostedMedia(GameId.SCD).openConfiguredMedia(config()));
        assertEquals(availability.detail(), refused.getMessage());
    }

    @Test void aSheetWhoseTrackFilesAreMissingIsNamed() throws Exception {
        Path disc = Files.createDirectories(folder.resolve("Sonic CD"));
        StringBuilder cue = new StringBuilder();
        for (int track = 1; track <= 35; track++) {
            cue.append("FILE \"Sonic CD (USA) (Track ").append(String.format("%02d", track)).append(").bin\" BINARY\n")
                    .append("  TRACK ").append(String.format("%02d", track)).append(track == 1 ? " MODE1/2352\n" : " AUDIO\n")
                    .append("    INDEX 01 00:00:00\n");
        }
        Files.writeString(disc.resolve("Sonic CD (US).cue"), cue);
        var availability = GameModuleRegistry.hostedMedia(GameId.SCD).probe(config());
        assertFalse(availability.available());
        assertTrue(availability.detail().startsWith("Sonic CD (US).cue: the disc image could not be read"),
                availability.detail());
        assertTrue(availability.detail().endsWith("OpenGGF plays the USA release of Sonic CD."), availability.detail());
    }

    @Test void aKeyThatIsNotAFileSaysSoAndIsNotReplacedByTheSearch() throws Exception {
        var config = config();
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, folder.resolve("missing.cue").toString());
        var availability = GameModuleRegistry.hostedMedia(GameId.SCD).probe(config);
        assertFalse(availability.available());
        assertTrue(availability.detail().startsWith("roms.soniccd names " + folder.resolve("missing.cue")),
                availability.detail());
        assertTrue(ConfigCatalog.meta(SonicConfiguration.SONIC_CD_DISC).path().equals("roms.soniccd"));
    }

    private static MasterTitleScreen shelf(SonicConfigurationService config) {
        List<MasterTitleEntry> entries = new ArrayList<>();
        for (var game : MasterTitleScreen.GameEntry.values()) entries.add(new MasterTitleEntry.Stock(game));
        entries.add(MasterTitleEntry.Hosted.of(GameId.SCD));
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), entries);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, true);
        while (!"scd".equals(screen.getSelectedGameId())) press(screen, GLFW_KEY_RIGHT);
        return screen;
    }

    @Test void startOnAShelvedDiscWithoutMediaShowsWhyAndStartsNothing() {
        var screen = shelf(config());
        screen.setHostedAvailabilityForTest(GameId.SCD, false, "no disc here");
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isGameSelected());
        assertEquals(List.of("no disc here"), screen.errorPageLayout().detail());
    }

    @Test void startOnAShelvedDiscLaunchesItAsAHostedEntry() {
        var screen = shelf(config());
        screen.setHostedAvailabilityForTest(GameId.SCD, true, "found");
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
        assertInstanceOf(MasterTitleEntry.Hosted.class, screen.getSelectedLaunch().entry());
        assertEquals("scd", screen.getSelectedGameId());
    }

    @Test void aHostedLaunchUsesTheStockTeamWhateverTheAllGamesCharacters() {
        var config = config();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        new MasterTitleLaunchCoordinator(config).prepareHostedExit();
        assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        config.clearSessionOverrides();
        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE), "the saved setting is untouched");
    }
}
