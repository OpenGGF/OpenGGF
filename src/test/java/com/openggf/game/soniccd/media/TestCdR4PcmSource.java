package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests FIRST, author Java UNRUN. Original-media custody only; no driver/audio/World admission. */
class TestCdR4PcmSource {
    private static final String SHA = "7746a04d60551aeeae724a2c6d77b48d7ff52bb050707495010b514e10b41296";

    @Test void noPublicRawFactoryAndNullCannotMintSource() {
        assertEquals(0, CdR4PcmSource.class.getConstructors().length);
        assertEquals(List.of("bytes/0", "file/0", "fromPrograms/1", "source/0"),
                Arrays.stream(CdR4PcmSource.class.getDeclaredMethods())
                        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                        .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList());
        assertThrows(NullPointerException.class, () -> CdR4PcmSource.fromPrograms(null));
    }

    @Test void sourceDescriptorRemainsFiniteMetadataNotAUsableOwner() {
        assertThrows(IllegalArgumentException.class, () -> new CdR4PcmSource.Source(
                CdReleaseVariant.JAPAN, CdRetailReleases.USA.normalizedSha256(), SHA));
        assertThrows(IllegalArgumentException.class, () -> new CdR4PcmSource.Source(
                CdReleaseVariant.USA, "0".repeat(64), SHA));
        assertThrows(IllegalArgumentException.class, () -> new CdR4PcmSource.Source(
                CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(), "0".repeat(64)));
    }

    @Test void commonValidatorRejectsCorruptionAndFileSubstitutionWithoutMintingAnOwner() throws Exception {
        byte[] malformed = new byte[201562];
        assertThrows(IOException.class, () -> CdPcmFileSource.validate(CdPcmFileSource.R4, malformed));
        assertThrows(IOException.class, () -> CdPcmFileSource.validate(CdPcmFileSource.R3, malformed));
        assertThrows(NullPointerException.class, () -> CdPcmFileSource.validate(CdPcmFileSource.R4, null));
    }

    @Nested class ActualUsa {
        static CdProgramAssets closed;
        static CdR4PcmSource data;
        static byte[] original;

        @BeforeAll static void sourceOnly() throws Exception {
            var p = CdProgramAssets.open(cue("USA"), Optional.of(CdReleaseVariant.USA), Optional.empty());
            closed = p;
            try (p) {
                assertEquals(CdRetailReleases.USA, CdRetailReleases.find(p.identity()).orElseThrow());
                assertEquals("/SNCBNK4B.BIN;1", p.entry(CdNativeFile.R4PCM).path());
                assertEquals(201562, p.entry(CdNativeFile.R4PCM).size());
                assertThrows(IllegalArgumentException.class, () -> p.mmd(CdNativeFile.R4PCM));
                original = p.read(CdNativeFile.R4PCM, 0, 201562);
                data = CdR4PcmSource.fromPrograms(p);
            }
        }

        @Test void originalExactBankIsRetainedAfterDiscCloses() throws Exception {
            assertThrows(IOException.class, () -> closed.read(CdNativeFile.R4PCM, 0, 1));
            assertThrows(IOException.class, () -> CdR4PcmSource.fromPrograms(closed));
            assertEquals(CdNativeFile.R4PCM, data.file());
            assertEquals(201562, data.bytes().length());
            assertArrayEquals(original, data.bytes().copy());
            assertEquals(SHA, sha(data.bytes().copy()));
            assertEquals(new CdR4PcmSource.Source(CdReleaseVariant.USA,
                    CdRetailReleases.USA.normalizedSha256(), SHA), data.source());
        }

        @Test void nativeNonBossHeaderAndTemplateAreSourceDataNotInitDriver() throws Exception {
            var b = data.bytes();
            assertArrayEquals("SNCBNK28.S28    ".getBytes(StandardCharsets.US_ASCII), b.slice(0, 16).copy());
            assertEquals(0x4EFA0906L, b.unsignedLong(0x10));
            assertEquals(0x4EFA0F90L, b.unsignedLong(0x14));
            assertArrayEquals(new byte[0x900], b.slice(0x18, 0x900).copy());
            assertEquals("cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b",
                    sha(b.slice(0x18, 0x900).copy()));
        }

        @Test void changedHeaderTemplateAndTailAreRejectedBeforeAnySourceExists() throws Exception {
            for (int offset : new int[]{0,0x10,0x14,0x18,0x917,201561}) {
                var corrupt=original.clone();corrupt[offset]^=1;
                assertThrows(IOException.class,()->CdPcmFileSource.validate(CdPcmFileSource.R4,corrupt));
            }
            assertThrows(IOException.class,()->CdPcmFileSource.validate(CdPcmFileSource.R3,original));
            assertThrows(IOException.class,()->CdPcmFileSource.validate(CdPcmFileSource.R1,original));
        }

        @Test void immutableBankHasExactFileBoundsWithoutSectorPadding() {
            var b = data.bytes();
            assertSame(b, data.bytes());
            byte[] copy = b.copy();
            copy[0] ^= 1;
            assertArrayEquals(original, b.copy());
            var first = b.buffer();
            var second = b.buffer();
            first.get();
            assertEquals(0, second.position());
            assertThrows(ReadOnlyBufferException.class, () -> second.put(0, (byte) 1));
            assertEquals(0, b.slice(201562, 0).length());
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedByte(201562));
            assertThrows(IndexOutOfBoundsException.class, () -> b.slice(0x40000, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedLong(201561));
        }
    }

    @Test void configuredJapanRemainsUnsupported() throws Exception { reject("Japan"); }
    @Test void configuredEuropeRemainsUnsupported() throws Exception { reject("Europe"); }

    private static void reject(String region) throws Exception {
        try (var p = CdProgramAssets.open(cue(region))) {
            assertThrows(IOException.class, () -> CdR4PcmSource.fromPrograms(p));
        }
    }

    private static Path cue(String region) throws Exception {
        var root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Explicit original media source opt-in");
        var directory = Path.of(root).resolve(region);
        Assumptions.assumeTrue(Files.isDirectory(directory), "Explicit configured regional media absent: " + region);
        try (var paths = Files.list(directory)) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return cues.getFirst();
        }
    }

    private static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
