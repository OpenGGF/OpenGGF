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
class TestCdR3PcmSource {
    private static final String SHA = "82b9ac37eddbf379155ab76407efc1596e61c7707c592eb47e41cfc468012340";

    @Test void noPublicRawFactoryAndNullCannotMintSource() {
        assertEquals(0, CdR3PcmSource.class.getConstructors().length);
        assertEquals(List.of("bytes/0", "file/0", "fromPrograms/1", "source/0"),
                Arrays.stream(CdR3PcmSource.class.getDeclaredMethods())
                        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                        .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList());
        assertThrows(NullPointerException.class, () -> CdR3PcmSource.fromPrograms(null));
    }

    @Test void sourceDescriptorRemainsFiniteMetadataNotAUsableOwner() {
        assertThrows(IllegalArgumentException.class, () -> new CdR3PcmSource.Source(
                CdReleaseVariant.JAPAN, CdRetailReleases.USA.normalizedSha256(), SHA));
        assertThrows(IllegalArgumentException.class, () -> new CdR3PcmSource.Source(
                CdReleaseVariant.USA, "0".repeat(64), SHA));
        assertThrows(IllegalArgumentException.class, () -> new CdR3PcmSource.Source(
                CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(), "0".repeat(64)));
    }

    @Nested class ActualUsa {
        static CdProgramAssets closed;
        static CdR3PcmSource data;
        static byte[] original;

        @BeforeAll static void sourceOnly() throws Exception {
            var p = CdProgramAssets.open(cue("USA"), Optional.of(CdReleaseVariant.USA), Optional.empty());
            closed = p;
            try (p) {
                assertEquals(CdRetailReleases.USA, CdRetailReleases.find(p.identity()).orElseThrow());
                assertEquals("/SNCBNK3B.BIN;1", p.entry(CdNativeFile.R3PCM).path());
                assertEquals(242597, p.entry(CdNativeFile.R3PCM).size());
                assertThrows(IllegalArgumentException.class, () -> p.mmd(CdNativeFile.R3PCM));
                original = p.read(CdNativeFile.R3PCM, 0, 242597);
                data = CdR3PcmSource.fromPrograms(p);
            }
        }

        @Test void originalExactBankIsRetainedAfterDiscCloses() throws Exception {
            assertThrows(IOException.class, () -> closed.read(CdNativeFile.R3PCM, 0, 1));
            assertThrows(IOException.class, () -> CdR3PcmSource.fromPrograms(closed));
            assertEquals(CdNativeFile.R3PCM, data.file());
            assertEquals(242597, data.bytes().length());
            assertArrayEquals(original, data.bytes().copy());
            assertEquals(SHA, sha(data.bytes().copy()));
            assertEquals(new CdR3PcmSource.Source(CdReleaseVariant.USA,
                    CdRetailReleases.USA.normalizedSha256(), SHA), data.source());
        }

        @Test void nativeNonBossHeaderAndTemplateAreSourceDataNotInitDriver() throws Exception {
            var b = data.bytes();
            assertArrayEquals("SNCBNK27.S28    ".getBytes(StandardCharsets.US_ASCII), b.slice(0, 16).copy());
            assertEquals(0x4EFA0906L, b.unsignedLong(0x10));
            assertEquals(0x4EFA0F90L, b.unsignedLong(0x14));
            assertArrayEquals(new byte[0x900], b.slice(0x18, 0x900).copy());
            assertEquals("cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b",
                    sha(b.slice(0x18, 0x900).copy()));
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
            assertEquals(0, b.slice(242597, 0).length());
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedByte(242597));
            assertThrows(IndexOutOfBoundsException.class, () -> b.slice(0x40000, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedLong(242596));
        }
    }

    @Test void configuredJapanRemainsUnsupported() throws Exception { reject("Japan"); }
    @Test void configuredEuropeRemainsUnsupported() throws Exception { reject("Europe"); }

    private static void reject(String region) throws Exception {
        try (var p = CdProgramAssets.open(cue(region))) {
            assertThrows(IOException.class, () -> CdR3PcmSource.fromPrograms(p));
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
