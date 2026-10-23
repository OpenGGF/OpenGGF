package com.openggf.game.soniccd;

import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Real supplied media is optional here; parent validation passes soniccd.media.root explicitly. */
class TestSonicCdStockPreparation {
    @TempDir Path temporary;

    @Test void unsupportedStageFailsBeforeOpeningAnyMedia() {
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11B"), Optional.empty(), Optional.empty());
        assertThrows(UnsupportedOperationException.class,
                () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), request));
    }

    @Test void missingMediaHasNoCartridgeOrStandaloneFallback() {
        assertThrows(IOException.class,
                () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), CdStockLaunch.Request.r11a()));
    }

    @ParameterizedTest(name = "{0}: detached authenticated stock source")
    @EnumSource(CdReleaseVariant.class)
    void preparedSourceRetainsOnlyDetachedDataAndExactContentIdentity(CdReleaseVariant variant) throws Exception {
        Path cue = suppliedCue(variant);
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        assertTrue(source.rom().isEmpty(), "a CD source must never expose a cartridge address space");
        assertEquals(variant, source.release().variant());
        assertEquals(CdStageKey.parse("R11A"), source.assets().identity().stage());
        var release = switch (variant) {
            case USA -> CdRetailReleases.USA;
            case JAPAN -> CdRetailReleases.JAPAN;
            case EUROPE -> CdRetailReleases.EUROPE;
        };
        assertEquals(release, source.release());
        assertTrue(source.identity().contains(release.normalizedSha256()));
        assertTrue(source.identity().contains(source.assets().identity().mmdSha256()));
        assertFalse(source.identity().contains(cue.toString()), "identity must be independent of host paths");

        // prepare() has already closed its disc. These are bounded owned-memory streams.
        try (var blocks = source.openAsset(CdGameDataSource.DECODED_BLOCKS);
             var second = source.openAsset(CdGameDataSource.DECODED_BLOCKS);
             var tiles = source.openAsset(CdGameDataSource.DECODED_TILES);
             var palette = source.openAsset(CdGameDataSource.INITIAL_PALETTE)) {
            byte[] firstBytes = blocks.readAllBytes();
            assertEquals(6432, firstBytes.length);
            assertEquals("0bfe16586ccdd38a27b3437441852f49b888ed527927d9ea635986579eddf0d7", sha(firstBytes));
            firstBytes[0] ^= 0x7F;
            assertNotEquals(firstBytes[0], second.readAllBytes()[0], "callers cannot mutate durable source bytes");
            byte[] art = tiles.readAllBytes();
            assertEquals(25600, art.length);
            assertEquals("314d9df246ee9c58371d6512d2bf496a2aecd08835e8698752c8f21a1e41223d", sha(art));
            assertArrayEquals(source.assets().loadPlan().initialPalette().copy(), palette.readAllBytes());
        }
        for (String invalid : new String[]{"../stage/R11A/tiles.bin", "/stage/R11A/tiles.bin",
                "stage/R11A/../R11A/tiles.bin", "stage\\R11A\\tiles.bin", "stage/R11B/tiles.bin",
                "stage/R11A/shield-dma.bin", "stage/R11A/tiles.bin/", ""}) {
            assertThrows(FileNotFoundException.class, () -> source.openAsset(invalid), invalid);
        }
    }

    @ParameterizedTest(name = "{0}: requested software variant cannot override disc bytes")
    @EnumSource(CdReleaseVariant.class)
    void requestedVariantRejectsAnotherRegion(CdReleaseVariant variant) throws Exception {
        var wrong = variant == CdReleaseVariant.USA ? CdReleaseVariant.JAPAN : CdReleaseVariant.USA;
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11A"), Optional.of(wrong), Optional.empty());
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(suppliedCue(variant), request));
    }

    @Test void forcedContentRecordRejectsAnotherDiscEvenWhenStageProgramIsIdentical() throws Exception {
        // JP/EU R11A MMD bytes match, but their complete disc identities do not.
        var request = new CdStockLaunch.Request(CdStageKey.parse("R11A"), Optional.empty(),
                Optional.of(CdRetailReleases.EUROPE.key()));
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(suppliedCue(CdReleaseVariant.JAPAN), request));
    }

    private static Path suppliedCue(CdReleaseVariant variant) throws IOException {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied discs");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return cues.getFirst();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
