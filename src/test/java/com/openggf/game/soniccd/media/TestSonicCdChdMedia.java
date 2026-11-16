package com.openggf.game.soniccd.media;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.cd.ChdDiscImage;
import com.openggf.data.cd.CueDiscImage;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A lossless CHD of the USA disc is accepted exactly as its BIN/CUE is: by the unchanged
 * {@code scd-cue-layout-content-v1} content fingerprint ({@link CdRetailReleases#USA}, Redump 29188).
 * The real-disc checks are opt-in: {@code -Dsoniccd.chd.root=<directory of USA .chd files>} made with chdman
 * from the user's own disc, beside {@code -Dsoniccd.media.root} for the BIN/CUE comparison.
 */
@Isolated
class TestSonicCdChdMedia {
    private static final String CHD_ROOT_PROPERTY = "soniccd.chd.root";

    @TempDir Path dir;

    @Test void everyUsaChdHasTheBinCueObservationAndMatchesTheUsaRecord() throws Exception {
        Path cue = usaCue();
        List<Path> chds = usaChds();
        CdObservedMediaIdentity expected;
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            expected = CdObservedMediaIdentity.capture(disc, Optional.empty());
        }
        assertEquals(CdRetailReleases.USA, CdRetailReleases.find(expected).orElseThrow());
        for (Path chd : chds) {
            try (ChdDiscImage disc = ChdDiscImage.open(chd)) {
                CdObservedMediaIdentity actual = CdObservedMediaIdentity.capture(disc, Optional.empty());
                assertEquals(expected.normalizedSha256(), actual.normalizedSha256(), chd.getFileName().toString());
                assertEquals(expected.tracks(), actual.tracks(), chd.getFileName().toString());
                assertEquals(CdRetailReleases.USA, CdRetailReleases.find(actual).orElseThrow(), chd.getFileName().toString());
                assertTrue(disc.verifyDataSha1(), chd.getFileName() + " raw-data SHA-1");
            }
        }
    }

    @Test void theLauncherFindsAndOpensAUsaChdNamedByTheKeyOrInTheRomFolder() throws Exception {
        Path chd = usaChds().getFirst();
        try (var cleared = CdTestMedia.withoutDiscProperties()) {
            var config = SonicConfigurationService.createStandalone(dir);
            config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, dir.toString());
            config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, chd.toString());
            var found = CdDiscLocator.locate(config);
            assertEquals(chd.toRealPath(), found.disc().orElseThrow().toRealPath(), found.detail());
            config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, "");
            config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, chd.getParent().toString());
            assertTrue(CdDiscLocator.locate(config).found(), "the folder scan probes .chd files");
            try (var source = CdHostedMedia.openVerified(chd)) {
                assertEquals(CdRetailReleases.USA.normalizedSha256(), source.assets().identity().normalizedSha256());
                assertTrue(source.assets().hasExpectedTrackShape());
                assertEquals(262144, source.assets().files().stream()
                        .filter(entry -> entry.path().equals("/R11A__.MMD;1")).findFirst().orElseThrow().size());
            }
        }
    }

    // --- Always-on locator checks with synthetic files ---------------------------------------------------

    @Test void theTestPropertyLayoutPrefersTheCueAndOtherwiseTakesAChd() throws IOException {
        Path usa = Files.createDirectories(dir.resolve("USA"));
        Files.write(usa.resolve("b.chd"), new byte[1]);
        assertEquals(usa.resolve("b.chd"), CdHostedMedia.propertyDisc(dir.toString()));
        Files.write(usa.resolve("c.CUE"), new byte[1]);
        assertEquals(usa.resolve("c.CUE"), CdHostedMedia.propertyDisc(dir.toString()));
        Files.delete(usa.resolve("b.chd"));
        Files.delete(usa.resolve("c.CUE"));
        IOException none = assertThrows(IOException.class, () -> CdHostedMedia.propertyDisc(dir.toString()));
        assertTrue(none.getMessage().contains(".cue or .chd"), none.getMessage());
    }

    @Test void aDamagedChdInTheRomFolderIsNamedWhenNoDiscQualifies() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("roms/Sonic CD"));
        byte[] chd = Files.readAllBytes(Path.of("src/test/resources/cd/chd/mixed-cdzl.chd"));
        Files.write(folder.resolve("Sonic CD (USA).chd"), java.util.Arrays.copyOf(chd, 200));
        var config = SonicConfigurationService.createStandalone(dir);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, dir.resolve("roms").toString());
        try (var cleared = CdTestMedia.withoutDiscProperties()) {
            var found = CdDiscLocator.locate(config);
            assertFalse(found.found());
            assertTrue(found.detail().startsWith("Sonic CD (USA).chd: the disc image could not be read"), found.detail());
        }
        assertTrue(CdDiscLocator.placementHint().contains(".chd"));
    }

    @Test void theDiscPathNamesTheDiscAndTheMediaRootWinsOverIt() throws IOException {
        Path usa = Files.createDirectories(dir.resolve("media/USA"));
        Path sheet = Files.write(usa.resolve("a.cue"), new byte[1]);
        Path chd = Files.write(dir.resolve("Sonic CD (USA).chd"), new byte[1]);
        try (var cleared = CdTestMedia.withoutDiscProperties()) {
            assertEquals(Optional.empty(), CdHostedMedia.propertyDisc());
            System.setProperty(CdHostedMedia.DISC_PATH_PROPERTY, chd.toString());
            assertEquals(Optional.of(chd), CdHostedMedia.propertyDisc());
            System.setProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY, dir.resolve("media").toString());
            assertEquals(Optional.of(sheet), CdHostedMedia.propertyDisc(), "the caller's media root wins");
            System.clearProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
            System.setProperty(CdHostedMedia.DISC_PATH_PROPERTY, dir.resolve("missing.chd").toString());
            IOException missing = assertThrows(IOException.class, CdHostedMedia::propertyDisc);
            assertTrue(missing.getMessage().startsWith(CdHostedMedia.DISC_PATH_PROPERTY + " names "), missing.getMessage());
            var found = CdDiscLocator.locate(null);
            assertFalse(found.found());
            assertEquals(missing.getMessage(), found.detail());
        }
    }

    @Test void aDiscPathNamingADamagedChdIsRefusedNotReplacedByTheFolderSearch() throws IOException {
        byte[] chd = Files.readAllBytes(Path.of("src/test/resources/cd/chd/mixed-cdzl.chd"));
        Path damaged = Files.write(dir.resolve("Sonic CD (USA).chd"), java.util.Arrays.copyOf(chd, 200));
        var config = SonicConfigurationService.createStandalone(dir);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, dir.toString());
        try (var cleared = CdTestMedia.withoutDiscProperties()) {
            System.setProperty(CdHostedMedia.DISC_PATH_PROPERTY, damaged.toString());
            var found = CdDiscLocator.locate(config);
            assertFalse(found.found());
            assertTrue(found.detail().startsWith("Sonic CD (USA).chd: the disc image could not be read"), found.detail());
        }
    }

    /** The bin/cue form the CHDs are compared with: needs the media root's sheet and a CHD folder. */
    private static Path usaCue() throws IOException {
        String chds = System.getProperty(CHD_ROOT_PROPERTY);
        Assumptions.assumeTrue(chds != null && !chds.isBlank(),
                "Set soniccd.chd.root and soniccd.media.root for the real CHD checks");
        return CdTestMedia.regionCue("USA");
    }

    private static List<Path> usaChds() throws IOException {
        usaCue();
        Path root = Path.of(System.getProperty(CHD_ROOT_PROPERTY));
        List<Path> chds;
        try (Stream<Path> files = Files.list(root)) {
            chds = files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".chd")).sorted().toList();
        }
        assertFalse(chds.isEmpty(), "soniccd.chd.root names a directory without .chd files: " + root);
        return chds;
    }
}
