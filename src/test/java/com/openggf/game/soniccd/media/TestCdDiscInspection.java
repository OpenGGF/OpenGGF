package com.openggf.game.soniccd.media;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.internal.HostedGameMedia.MediaKind;
import com.openggf.game.soniccd.CdReleaseVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The setup guide classifies a chosen Sonic CD image with the folder search's own probe (layout and the data
 * track's header, no hashing), by kind rather than by parsing messages. Synthetic discs carry only generated
 * metadata ({@link SyntheticProgramDisc}); no disc content is needed.
 */
@Isolated
class TestCdDiscInspection {
    @TempDir Path dir;

    @Test void relativeDiscPathAndScanUseTheActiveConfigDirectory() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("games/usa"), CdReleaseVariant.USA);
        var config = SonicConfigurationService.createStandalone(dir);
        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, "games");
        config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, dir.relativize(cue).toString());

        String root = System.clearProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        String disc = System.clearProperty(CdHostedMedia.DISC_PATH_PROPERTY);
        try {
            assertEquals(cue, CdDiscLocator.locate(config).disc().orElseThrow());
            config.setConfigValue(SonicConfiguration.SONIC_CD_DISC, "");
            assertEquals(cue, CdDiscLocator.locate(config).disc().orElseThrow());
        } finally {
            if (root != null) System.setProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY, root);
            if (disc != null) System.setProperty(CdHostedMedia.DISC_PATH_PROPERTY, disc);
        }
    }

    @Test void aCompleteUsaSheetIsReadyButOnlyIdentified() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("usa"), CdReleaseVariant.USA);
        var inspection = CdDiscLocator.inspect(cue);
        assertEquals(MediaKind.READY, inspection.kind());
        assertEquals(cue.toAbsolutePath().normalize().toString(), inspection.detail());
    }

    @Test void anotherReleaseIsNamedWithTheSearchsExactWording() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("eu"), CdReleaseVariant.EUROPE);
        var inspection = CdDiscLocator.inspect(cue);
        assertEquals(MediaKind.OTHER_RELEASE, inspection.kind());
        assertEquals(cue.getFileName() + " is the Europe release. OpenGGF plays the USA release of Sonic CD.",
                inspection.detail());
    }

    @Test void aSheetListingTooFewTracksIsIncomplete() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("short"), CdReleaseVariant.USA);
        List<String> lines = Files.readAllLines(cue);
        int cut = 0;
        for (int i = 0, files = 0; i < lines.size(); i++) if (lines.get(i).startsWith("FILE") && ++files == 13) cut = i;
        Files.write(cue, lines.subList(0, cut));
        var inspection = CdDiscLocator.inspect(cue);
        assertEquals(MediaKind.INCOMPLETE, inspection.kind());
        assertEquals(cue.getFileName() + ": a Sonic CD disc with 12 of its 35 tracks.", inspection.detail());
    }

    @Test void aMissingTrackFileMakesTheSheetUnreadableAndNamesTheFile() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("missing"), CdReleaseVariant.USA);
        Files.delete(cue.resolveSibling("audio12.bin"));
        var inspection = CdDiscLocator.inspect(cue);
        assertEquals(MediaKind.UNREADABLE, inspection.kind());
        assertTrue(inspection.detail().startsWith(cue.getFileName() + ": the disc image could not be read ("),
                inspection.detail());
        assertTrue(inspection.detail().contains("audio12.bin"), inspection.detail());
    }

    @Test void anOlderChdVersionIsUnreadableWithTheConversionAdvice() throws IOException {
        Path chd = dir.resolve("old.chd");
        ByteBuffer header = ByteBuffer.allocate(124);
        header.put("MComprHD".getBytes(StandardCharsets.US_ASCII)).putInt(124).putInt(4);
        Files.write(chd, header.array());
        var inspection = CdDiscLocator.inspect(chd);
        assertEquals(MediaKind.UNREADABLE, inspection.kind());
        assertTrue(inspection.detail().contains("CHD version 4 is not supported; convert it to version 5 with chdman"),
                inspection.detail());
    }

    @Test void anotherGamesDiscIsNotThisGameAndCandidatesAreChosenByName() throws IOException {
        Path cue = SyntheticProgramDisc.write(dir.resolve("other"), CdReleaseVariant.USA);
        Path data = cue.resolveSibling("data.bin");
        byte[] image = Files.readAllBytes(data);
        byte[] foreign = "SEGADISCSYSTEM  NOTSONICCD ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(foreign, 0, image, 0, foreign.length);
        Files.write(data, image);
        assertEquals(MediaKind.NOT_THIS_GAME, CdDiscLocator.inspect(cue).kind());
        assertTrue(CdDiscLocator.candidate(Path.of("Disc.CUE")));
        assertTrue(CdDiscLocator.candidate(Path.of("disc.chd")));
        assertFalse(CdDiscLocator.candidate(Path.of("track01.bin")));
        assertTrue(new CdHostedMedia().candidate(Path.of("x.cue")));
        assertEquals(MediaKind.NOT_THIS_GAME, new CdHostedMedia().inspect(cue).kind());
    }
}
