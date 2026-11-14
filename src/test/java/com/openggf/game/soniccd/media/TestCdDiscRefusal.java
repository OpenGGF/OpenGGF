package com.openggf.game.soniccd.media;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameId;
import com.openggf.game.launch.HostedMediaLaunch;
import com.openggf.game.soniccd.CdFixtureReplayAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The launcher refuses a Sonic CD disc that is not the USA release, as it refuses a wrong cartridge ROM: on the
 * shelf by the data track's header ({@link CdDiscLocator}), and at start by the whole disc's content
 * ({@link CdHostedMedia#openVerified}, Redump records). Uses the user's own discs read-only: the USA disc beside
 * a European one under {@code soniccd.media.root} (skips when either is absent).
 */
@Isolated
class TestCdDiscRefusal {
    @TempDir Path dir;
    private String mediaRoot;
    private Path usa;
    private Path europe;

    @BeforeEach void discs() throws IOException {
        usa = CdFixtureReplayAccess.usaCue();
        mediaRoot = System.getProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        Path folder = Path.of(mediaRoot, "Europe");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(folder), "no European disc beside the USA one");
        try (Stream<Path> cues = Files.list(folder)) {
            europe = cues.filter(p -> p.toString().toLowerCase().endsWith(".cue")).findFirst().orElse(null);
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(europe != null, "no European .cue");
        System.clearProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
    }

    @AfterEach void restore() {
        if (mediaRoot != null) System.setProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY, mediaRoot);
    }

    private SonicConfigurationService romFolder(Path folder) {
        var config = SonicConfigurationService.createStandalone(dir);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, folder.toString());
        return config;
    }

    @Test void aRomFolderHoldingOnlyAnotherReleaseIsNotOfferedAndCannotStart() {
        var config = romFolder(europe.getParent());
        var found = CdDiscLocator.locate(config);
        assertFalse(found.found());
        assertEquals(europe.getFileName() + " is the Europe release. OpenGGF plays the USA release of Sonic CD.",
                found.detail());
        var refused = assertThrows(IOException.class,
                () -> new HostedMediaLaunch().open(GameId.SCD, config, dir.resolve("saves")));
        assertEquals(found.detail(), refused.getMessage());
        assertFalse(Files.exists(dir.resolve("saves")), "a refused disc attaches no saves");
    }

    @Test void theUsaDiscIsFoundAmongOtherReleasesAndAnotherReleaseFailsTheWholeDiscCheck() throws Exception {
        var found = CdDiscLocator.locate(romFolder(Path.of(mediaRoot)));
        assertEquals(usa.toRealPath(), found.cue().orElseThrow().toRealPath(), "the USA sheet, by header, among three discs");
        try (var launch = new HostedMediaLaunch()) {
            var opened = launch.open(GameId.SCD, romFolder(Path.of(mediaRoot)), dir.resolve("saves"));
            assertEquals(GameId.SCD, opened.module().getGameId());
        }
        // Content, not the header, decides at start: the European disc matches its own Redump record.
        var refused = assertThrows(IOException.class, () -> CdHostedMedia.openVerified(europe));
        assertTrue(refused.getMessage().contains("it matches the EUROPE release"), refused.getMessage());
    }

    @Test void theKeyNamesTheDiscAndTheFolderSearchIsTheFallback() throws Exception {
        // roms.soniccd set: that sheet, even with the ROM folder pointing elsewhere.
        var config = romFolder(dir);
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, usa.toString());
        assertEquals(usa.toRealPath(), CdDiscLocator.locate(config).cue().orElseThrow().toRealPath());
        // Blank: the folder search under roms.directory.
        config = romFolder(Path.of(mediaRoot));
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, "");
        assertEquals(usa.toRealPath(), CdDiscLocator.locate(config).cue().orElseThrow().toRealPath());
    }

    @Test void aKeyNamingAnotherReleaseIsRefusedWithTheSameMessageAndNotReplacedByTheSearch() {
        var config = romFolder(Path.of(mediaRoot));          // the folder search would find the USA disc
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, europe.toString());
        var found = CdDiscLocator.locate(config);
        assertFalse(found.found());
        assertEquals(europe.getFileName() + " is the Europe release. OpenGGF plays the USA release of Sonic CD.",
                found.detail());
        var refused = assertThrows(IOException.class,
                () -> new HostedMediaLaunch().open(GameId.SCD, config, dir.resolve("saves")));
        assertEquals(found.detail(), refused.getMessage());
    }

    @Test void theTestPropertyWinsOverTheKey() throws Exception {
        var config = romFolder(dir);
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, europe.toString());
        System.setProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY, mediaRoot);
        assertEquals(usa.toRealPath(), CdDiscLocator.locate(config).cue().orElseThrow().toRealPath());
    }
}
