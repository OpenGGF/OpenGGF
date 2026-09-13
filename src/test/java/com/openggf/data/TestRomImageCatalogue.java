package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomImageCatalogue.Resolution;
import com.openggf.data.RomImageCatalogue.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.data.SyntheticRomImages.S1_SIZE;
import static com.openggf.data.SyntheticRomImages.S1_TITLE;
import static com.openggf.data.SyntheticRomImages.S2_SIZE;
import static com.openggf.data.SyntheticRomImages.S2_TITLE;
import static com.openggf.data.SyntheticRomImages.S3_SIZE;
import static com.openggf.data.SyntheticRomImages.S3_TITLE;
import static com.openggf.data.SyntheticRomImages.SK_SIZE;
import static com.openggf.data.SyntheticRomImages.SK_TITLE;
import static com.openggf.data.SyntheticRomImages.image;
import static com.openggf.data.SyntheticRomImages.physical;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resolution precedence over synthetic images: explicit key, verified image, directory order, composite. */
class TestRomImageCatalogue {

    private static final PhysicalImage S1_A = physical("a-sonic1.gen", image(S1_SIZE, (byte) 0x0A, S1_TITLE, null));
    private static final PhysicalImage S1_B = physical("b-sonic1.gen", image(S1_SIZE, (byte) 0x0B, S1_TITLE, null));
    private static final PhysicalImage S3 = physical("sonic3.gen", image(S3_SIZE, (byte) 0x33, S3_TITLE, null));
    private static final PhysicalImage SK = physical("sk.gen", image(SK_SIZE, (byte) 0x55, SK_TITLE, null));
    private static final PhysicalImage S3K = physical("s3k.gen", image(SK_SIZE + S3_SIZE, (byte) 0x77, SK_TITLE, S3_TITLE));
    private static final PhysicalImage KIS2 = physical("sk-s2.gen", image(0x340000, (byte) 0x22, SK_TITLE, S2_TITLE));

    @Test
    void firstImageInDirectoryOrderWinsWhenNothingIsVerified() {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S1_B, S1_A), Map.of(), false);
        Resolution resolution = catalogue.resolve(RomIdentity.S1).orElseThrow();
        assertEquals(Source.DIRECTORY_ORDER, resolution.source());
        assertSame(S1_B, resolution.parts().get(0).image());
        assertEquals(S1_B.path(), resolution.wholeImagePath().orElseThrow());
        assertFalse(resolution.isComposite());
    }

    @Test
    void explicitKeyBeatsDirectoryOrder() throws IOException {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S1_B, S1_A), Map.of(RomIdentity.S1, S1_A), false);
        Resolution resolution = catalogue.resolve(RomIdentity.S1).orElseThrow();
        assertEquals(Source.EXPLICIT_KEY, resolution.source());
        assertSame(S1_A, resolution.parts().get(0).image());
        assertEquals(0x0A, catalogue.open(RomIdentity.S1).readU8(0x1000));
    }

    @Test
    void explicitKeyNamingAnImageWithoutTheRomServesThatImageWhole() throws IOException {
        // The user named the file: it is served as configured and the game
        // detectors judge it, never silently swapped for another image.
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S3, S1_A), Map.of(RomIdentity.S1, S3), false);
        Resolution resolution = catalogue.resolve(RomIdentity.S1).orElseThrow();
        assertEquals(Source.EXPLICIT_KEY, resolution.source());
        assertSame(S3, resolution.parts().get(0).image());
        assertEquals(S3.path(), resolution.wholeImagePath().orElseThrow());
        assertEquals(S3_SIZE, catalogue.open(RomIdentity.S1).size());
    }

    @Test
    void explicitKeyMayNameALockOnDumpForTheGameItContains() {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(KIS2), Map.of(RomIdentity.S2, KIS2), false);
        Resolution s2 = catalogue.resolve(RomIdentity.S2).orElseThrow();
        assertEquals(Source.EXPLICIT_KEY, s2.source());
        assertEquals(0x200000, s2.parts().get(0).offset());
        assertEquals(S2_SIZE, s2.parts().get(0).length());
        assertTrue(s2.wholeImagePath().isEmpty(), "a window is not a whole file");
        assertTrue(catalogue.isAvailable(RomIdentity.SK));
        assertTrue(catalogue.isAvailable(RomIdentity.KIS2_CHIP));
        assertTrue(catalogue.isAvailable(RomIdentity.KIS2));
        assertFalse(catalogue.isAvailable(RomIdentity.S3));
        assertFalse(catalogue.isAvailable(RomIdentity.S3K));
    }

    @Test
    void compositeIsUsedOnlyWhenNoSingleImageContainsTheRom() throws IOException {
        RomImageCatalogue separate = new RomImageCatalogue(List.of(S3, SK), Map.of(), false);
        Resolution composite = separate.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.COMPOSITE, composite.source());
        assertTrue(composite.isComposite());
        assertEquals(List.of(RomIdentity.SK, RomIdentity.S3),
                composite.parts().stream().map(RomImageCatalogue.Part::rom).toList());
        RomByteReader s3k = separate.open(RomIdentity.S3K);
        assertEquals(SK_SIZE + S3_SIZE, s3k.size());
        assertEquals(0x55, s3k.readU8(0x1FFFFF));
        assertEquals(0x33, s3k.readU8(0x200000));
        assertEquals(0x5533, s3k.readU16BE(0x1FFFFF));

        RomImageCatalogue dump = new RomImageCatalogue(List.of(S3, SK, S3K), Map.of(), false);
        Resolution whole = dump.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.DIRECTORY_ORDER, whole.source());
        assertSame(S3K, whole.parts().get(0).image());
        assertEquals(0x77, dump.open(RomIdentity.S3K).readU8(0x200000));
    }

    @Test
    void preferCompositeBeatsALockOnDumpButFallsBackWhenAPartIsMissing() throws IOException {
        RomImageCatalogue preferred = new RomImageCatalogue(List.of(S3, SK, S3K), Map.of(), true);
        Resolution resolution = preferred.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.COMPOSITE, resolution.source());
        assertEquals(0x33, preferred.open(RomIdentity.S3K).readU8(0x200000));
        assertEquals(0x55, preferred.open(RomIdentity.S3K).readU8(0x1FFFFF));

        // Parts follow the same directory-order rule: a lock-on window listed
        // first serves the part, so a composite may be assembled from one dump.
        RomImageCatalogue dumpFirst = new RomImageCatalogue(List.of(S3K, S3, SK), Map.of(), true);
        assertEquals(0x77, dumpFirst.open(RomIdentity.S3K).readU8(0x200000));

        RomImageCatalogue skOnly = new RomImageCatalogue(List.of(SK), Map.of(), true);
        assertTrue(skOnly.resolve(RomIdentity.S3K).isEmpty(), "no S3 anywhere: no composite either");
        assertTrue(skOnly.isAvailable(RomIdentity.SK));
    }

    @Test
    void compositePartsComeFromTheDumpWindowsWhenNoStandaloneImageExists() {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S3K), Map.of(), true);
        Resolution resolution = catalogue.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.COMPOSITE, resolution.source());
        assertEquals(2, resolution.parts().size());
        assertSame(S3K, resolution.parts().get(0).image());
        assertEquals(0, resolution.parts().get(0).offset());
        assertEquals(0x200000, resolution.parts().get(1).offset());
    }

    @Test
    void missingRomsResolveEmptyAndOpenFails() {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S1_A), Map.of(), false);
        assertTrue(catalogue.resolve(RomIdentity.S2).isEmpty());
        assertTrue(catalogue.resolve(RomIdentity.S3K).isEmpty());
        assertTrue(catalogue.resolve(RomIdentity.KIS2).isEmpty());
        assertThrows(IOException.class, () -> catalogue.open(RomIdentity.S2));
        assertThrows(NullPointerException.class, () -> catalogue.resolve(null));
    }

    @Test
    void openedReadersAreCachedViewsAndUnverifiedImagesAreStillServed() throws IOException {
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(S1_A), Map.of(), false);
        assertSame(catalogue.open(RomIdentity.S1), catalogue.open(RomIdentity.S1));
        String label = catalogue.verificationLabel(catalogue.resolve(RomIdentity.S1).orElseThrow());
        assertTrue(label.startsWith("S1 unverified"), label);
    }

    @Test
    void buildScansTheDirectoryAndHonoursExplicitKeys(@TempDir Path dir) throws IOException {
        Path roms = Files.createDirectories(dir.resolve("roms"));
        Files.write(roms.resolve("z-second.gen"), image(S1_SIZE, (byte) 2, S1_TITLE, null));
        Files.write(roms.resolve("a-first.bin"), image(S1_SIZE, (byte) 1, S1_TITLE, null));
        Files.write(roms.resolve("notes.txt"), image(S1_SIZE, (byte) 9, S1_TITLE, null));
        Files.write(roms.resolve("junk.md"), new byte[10]);
        Path elsewhere = dir.resolve("elsewhere.gen");
        Files.write(elsewhere, image(S2_SIZE, (byte) 3, S2_TITLE, null));

        SonicConfigurationService configuration = SonicConfigurationService.createStandalone(dir);
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, "roms");
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "");
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, elsewhere.toString());
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "");

        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, dir);

        assertEquals(List.of("elsewhere.gen", "a-first.bin", "junk.md", "z-second.gen"),
                catalogue.images().stream().map(image -> image.path().getFileName().toString()).toList(),
                "explicit files first, then the directory in name order; .txt ignored");
        Resolution s1 = catalogue.resolve(RomIdentity.S1).orElseThrow();
        assertEquals(Source.DIRECTORY_ORDER, s1.source());
        assertEquals("a-first.bin", s1.wholeImagePath().orElseThrow().getFileName().toString());
        assertEquals(1, catalogue.open(RomIdentity.S1).readU8(0x100));
        Resolution s2 = catalogue.resolve(RomIdentity.S2).orElseThrow();
        assertEquals(Source.EXPLICIT_KEY, s2.source());
        assertEquals(elsewhere.toAbsolutePath().normalize(), s2.wholeImagePath().orElseThrow());
        assertTrue(catalogue.configuredValue(RomIdentity.S1).isEmpty());
        assertTrue(catalogue.configuredValue(RomIdentity.S3K).isEmpty());
        assertTrue(catalogue.resolve(RomIdentity.S3K).isEmpty());
    }

    @Test
    void explicitKeyNamingAMissingFileFailsClosedButTheDefaultHintDoesNot(@TempDir Path dir) throws IOException {
        Path roms = Files.createDirectories(dir.resolve("roms"));
        Files.write(roms.resolve("elsewhere-named.gen"), image(S1_SIZE, (byte) 1, S1_TITLE, null));
        Files.write(roms.resolve("other-two.gen"), image(S2_SIZE, (byte) 2, S2_TITLE, null));
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone(dir);
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, "roms");
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "typo/../typo-s1.gen");
        assertEquals("s2.gen", configuration.getString(SonicConfiguration.SONIC_2_ROM), "built-in default hint");

        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, dir);

        assertTrue(catalogue.resolve(RomIdentity.S1).isEmpty(), "an explicit missing file never falls back");
        assertEquals("typo/../typo-s1.gen", catalogue.explicitMissingValue(RomIdentity.S1).orElseThrow());
        assertThrows(IOException.class, () -> catalogue.open(RomIdentity.S1));
        Resolution s2 = catalogue.resolve(RomIdentity.S2).orElseThrow();
        assertEquals(Source.DIRECTORY_ORDER, s2.source(), "the absent default hint lets the scan apply");
        assertTrue(catalogue.explicitMissingValue(RomIdentity.S2).isEmpty());
    }

    @Test
    void explicitKeyNamingAnUnrecognisedFileServesItWhole(@TempDir Path dir) throws IOException {
        Path odd = dir.resolve("odd.bin");
        Files.write(odd, new byte[] {0x7A, 1, 2, 3});
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone(dir);
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, odd.toString());

        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, dir);

        Resolution s2 = catalogue.resolve(RomIdentity.S2).orElseThrow();
        assertEquals(Source.EXPLICIT_KEY, s2.source());
        assertEquals(odd.toAbsolutePath().normalize(), s2.wholeImagePath().orElseThrow());
        assertEquals(4, catalogue.open(RomIdentity.S2).size());
        assertEquals(0x7A, catalogue.open(RomIdentity.S2).readU8(0));
    }

    @Test
    void buildToleratesAMissingDirectory(@TempDir Path dir) {
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone(dir);
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, "does-not-exist");
        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, dir);
        assertTrue(catalogue.images().isEmpty());
        assertFalse(catalogue.isAvailable(RomIdentity.S1));
    }
}
