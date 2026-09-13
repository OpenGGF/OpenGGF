package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomImageCatalogue.Resolution;
import com.openggf.data.RomImageCatalogue.Source;
import com.openggf.game.sonic3k.Sonic3kRomDetector;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-gated checks against the user's real images. Nothing is copied or
 * linked: the tests read the images where they are and skip when absent.
 */
class TestRomCompositeImages {

    private static PhysicalImage lockOnDump() throws IOException {
        File dump = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(dump != null, "Sonic 3 & Knuckles lock-on image is required");
        PhysicalImage image = PhysicalImage.probe(dump.toPath());
        Assumptions.assumeTrue(RomImageClassifier.windowOf(image, RomIdentity.S3K).isPresent(),
                "configured S3K image is not a 4 MiB lock-on dump: " + image);
        return image;
    }

    @Test
    void compositeS3kFromTheSkAndS3WindowsEqualsTheLockOnDumpByteForByte() throws IOException {
        PhysicalImage dump = lockOnDump();
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(dump), Map.of(), true);

        Resolution resolution = catalogue.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.COMPOSITE, resolution.source());
        assertEquals(List.of(RomIdentity.SK, RomIdentity.S3),
                resolution.parts().stream().map(RomImageCatalogue.Part::rom).toList());

        RomByteReader composite = catalogue.open(RomIdentity.S3K);
        byte[] expected = Files.readAllBytes(dump.path());
        assertEquals(expected.length, composite.size());
        assertArrayEquals(expected, composite.slice(0, composite.size()));
        int seam = 0x200000;
        int expectedSeamWord = (Byte.toUnsignedInt(expected[seam - 1]) << 8) | Byte.toUnsignedInt(expected[seam]);
        assertEquals(expectedSeamWord, composite.readU16BE(seam - 1));

        String verification = catalogue.verificationLabel(resolution);
        assertTrue(verification.contains("SK verified as Sonic & Knuckles"), verification);
        assertTrue(verification.contains("S3 verified as Sonic 3"), verification);
    }

    @Test
    void compositeS3kDetectsAsSonic3AndKnucklesThroughAnInMemoryRom() throws IOException {
        PhysicalImage dump = lockOnDump();
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(dump), Map.of(), true);
        Rom rom = Rom.fromReader(catalogue.open(RomIdentity.S3K), "composite S3K");
        assertTrue(new Sonic3kRomDetector().canHandle(rom));
        assertEquals(RomHeaderName.SK, RomHeaderName.of(rom.readDomesticName()));
        Rom s3 = Rom.fromReader(catalogue.open(RomIdentity.S3), "S3 window");
        assertEquals(RomHeaderName.S3, RomHeaderName.of(s3.readDomesticName()));
        assertEquals(0x200000, s3.getSize());
        Rom sk = Rom.fromReader(catalogue.open(RomIdentity.SK), "SK window");
        assertEquals(0x200000, sk.getSize());
        assertEquals(rom.readChecksum(), sk.readChecksum(), "the S&K header is the lock-on header");
    }

    @Test
    void lockOnDumpVerifiesAgainstTheIdentityTable() throws IOException {
        PhysicalImage dump = lockOnDump();
        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(dump), Map.of(), false);
        Resolution whole = catalogue.resolve(RomIdentity.S3K).orElseThrow();
        assertEquals(Source.DIRECTORY_ORDER, whole.source());
        String label = catalogue.verificationLabel(whole);
        Assumptions.assumeTrue(label.contains("verified as"), "unverified dump: " + label);
        assertEquals("S3K verified as Sonic 3 & Knuckles lock-on", label);
    }

    @Test
    void verifiedImageBeatsAnEarlierUnverifiedImageWithTheSameHeader(@TempDir Path dir) throws IOException {
        File real = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(real != null, "Sonic 1 image is required");
        PhysicalImage verified = PhysicalImage.probe(real.toPath());
        Assumptions.assumeTrue(RomIdentityTable.verify(RomIdentity.S1, verified.fingerprint()).isPresent(),
                "configured Sonic 1 image is not the verified World REV01 dump");
        Path lookalike = dir.resolve("a-lookalike.gen");
        Files.write(lookalike, SyntheticRomImages.image(SyntheticRomImages.S1_SIZE, (byte) 0x5A,
                SyntheticRomImages.S1_TITLE, null));
        PhysicalImage unverified = PhysicalImage.probe(lookalike);

        RomImageCatalogue catalogue = new RomImageCatalogue(List.of(unverified, verified), Map.of(), false);
        Resolution resolution = catalogue.resolve(RomIdentity.S1).orElseThrow();
        assertEquals(Source.VERIFIED_IMAGE, resolution.source());
        assertEquals(verified.path(), resolution.wholeImagePath().orElseThrow());

        RomImageCatalogue explicit = new RomImageCatalogue(List.of(unverified, verified),
                Map.of(RomIdentity.S1, unverified), false);
        assertEquals(Source.EXPLICIT_KEY, explicit.resolve(RomIdentity.S1).orElseThrow().source());
        assertEquals(0x5A, explicit.open(RomIdentity.S1).readU8(0x1000));
    }

    @Test
    void knucklesInSonic2ResolvesOnlyFromAUserSuppliedLockOnDump() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, RomLocationResolver.currentWorkingDirectory());
        Assumptions.assumeTrue(catalogue.isAvailable(RomIdentity.KIS2),
                "no Sonic & Knuckles + Sonic 2 lock-on dump is available; skipping");
        Resolution kis2 = catalogue.resolve(RomIdentity.KIS2).orElseThrow();
        assertEquals(0x340000, kis2.parts().stream().mapToInt(RomImageCatalogue.Part::length).sum());
        assertTrue(catalogue.isAvailable(RomIdentity.KIS2_CHIP));
        assertEquals(0x40000, catalogue.resolve(RomIdentity.KIS2_CHIP).orElseThrow().parts().get(0).length());
    }

    @Test
    void locationResolverFindsWholeFileImagesByHeaderNotFilename() throws IOException {
        File s1 = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(s1 != null, "Sonic 1 image is required");
        Path directory = s1.toPath().toAbsolutePath().getParent();
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone(directory);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "no-such-file.gen");
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, directory.toString());

        RomLocation location = new RomLocationResolver(configuration, directory).resolve(RomGame.S1).orElseThrow();
        assertEquals(RomLocationSource.CATALOGUE, location.source());
        assertEquals(RomFingerprintPolicy.IDENTITY, location.fingerprintPolicy());
        assertEquals(RomHeaderName.S1, PhysicalImage.probe(location.resolvedPath()).headerAt0());
        assertEquals(SyntheticRomImages.S1_SIZE, Files.size(location.resolvedPath()));
    }
}
