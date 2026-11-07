package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests FIRST. Original-source custody only; no SPX/boot/selector/RAM/driver execution. Java UNRUN. */
class TestCdSystemProgramSource {
    private static final String SHA = "f0e9805a060fdd201e9f9e0043bd1d89d61ea69bd3de429bd4c2a83dd498f0d5";

    @Test void boundedSourceSurfacePreservesEveryOldPublicProgramMethodAndCatalogue() throws Exception {
        assertEquals(0, CdSystemProgramSource.class.getConstructors().length);
        assertEquals(List.of("bytes/0", "fromPrograms/1", "initializationClear/0", "source/0"),
                publicMethods(CdSystemProgramSource.class));
        assertThrows(NullPointerException.class, () -> CdSystemProgramSource.fromPrograms(null));
        assertEquals(List.of("catalogue/0", "close/0", "components/1", "entry/1", "files/0",
                "hasExpectedTrackShape/0", "header/0", "identity/0", "mmd/1", "open/1", "open/3",
                "openFile/1", "read/3", "slice/3", "stage/1"), publicMethods(CdProgramAssets.class));
        assertEquals(List.of("Component", "FileSlice", "Use"), Arrays.stream(CdProgramAssets.class.getDeclaredClasses())
                .filter(c -> Modifier.isPublic(c.getModifiers())).map(Class::getSimpleName).sorted().toList());
        var fixed = CdProgramAssets.class.getDeclaredMethod("systemProgramFile");
        assertFalse(Modifier.isPublic(fixed.getModifiers()));
        assertFalse(Modifier.isProtected(fixed.getModifiers()));
        assertEquals(0, fixed.getParameterCount());
        assertEquals(127, CdNativeFile.values().length);
        for (int i = 0; i < CdNativeFile.values().length; i++) assertEquals(i, CdNativeFile.values()[i].id());
        assertFalse(Arrays.stream(CdNativeFile.values()).anyMatch(f -> f.isoName().equals("SPX___.BIN;1")));
    }

    @Test void finiteSourceMetadataRejectsForeignNullWidthAndExtentClaims() {
        assertEquals("/SPX___.BIN;1", metadata().filePath());
        assertThrows(NullPointerException.class, () -> new CdSystemProgramSource.Source(null,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(NullPointerException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                null, "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(NullPointerException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), null, 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(NullPointerException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, null, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.JAPAN,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                "0".repeat(64), "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN", 10237, 17408, SHA, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", -1, 17408, SHA, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17409, SHA, 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, "0".repeat(64), 0xB800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, SHA, 0x100B800, 0xC000));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.Source(CdReleaseVariant.USA,
                CdRetailReleases.USA.normalizedSha256(), "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC002));
    }

    @Test void initializationDescriptorIsExactInertDataAndRejectsOtherSpans() {
        var expected = new CdSystemProgramSource.InitializationClear(0x800, 0x7000, 0x400, 0, 4);
        assertEquals(0x7400, expected.targetAddress() + expected.byteCount());
        assertEquals(4, expected.selectorBytes());
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.InitializationClear(0, 0x7000, 0x400, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.InitializationClear(0x800, 0x17000, 0x400, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.InitializationClear(0x800, 0x7000, 0x404, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.InitializationClear(0x800, 0x7000, 0x400, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSystemProgramSource.InitializationClear(0x800, 0x7000, 0x400, 0, 2));
    }

    @Nested class ActualUsa {
        static CdProgramAssets closed;
        static CdSystemProgramSource retained;
        static CdSystemProgramSource second;
        static byte[] original;
        static CdObservedMediaIdentity disc;
        static CdDiscHeader header;
        static com.openggf.data.cd.Iso9660FileSystem.Entry entry;

        @BeforeAll static void actualSource() throws Exception {
            var p = CdProgramAssets.open(cue("USA"), Optional.of(CdReleaseVariant.USA), Optional.empty());
            closed = p;
            try (p) {
                assertEquals(CdRetailReleases.USA, CdRetailReleases.find(p.identity()).orElseThrow());
                disc = p.identity(); header = p.header();
                var fixed = p.systemProgramFile(); entry = fixed.entry();
                assertEquals("/SPX___.BIN;1", fixed.entry().path());
                assertEquals(10237, fixed.entry().extentBlock());
                assertEquals(17408, fixed.entry().size());
                assertFalse(fixed.entry().directory());
                assertEquals(1, fixed.entry().version());
                original = fixed.bytes().copy();
                retained = CdSystemProgramSource.fromPrograms(p);
                second = CdSystemProgramSource.fromPrograms(p);
            }
        }

        @Test void exactOriginalSystemImageSurvivesItsDiscAndHasNoMmdHeader() throws Exception {
            assertEquals(metadata(), retained.source());
            assertEquals(17408, retained.bytes().length());
            assertEquals(SHA, sha(retained.bytes().copy()));
            assertArrayEquals(original, retained.bytes().copy());
            assertArrayEquals("R11A__.MMD;1\0".getBytes(StandardCharsets.US_ASCII), retained.bytes().slice(0, 13).copy());
            assertEquals(0x800, retained.source().entryAddress() - retained.source().loadAddress());
        }

        @Test void independentlyPinnedNativeClearOperandsDescribeOnlyTheOriginalFile() {
            var b = retained.bytes();
            assertArrayEquals(HexFormat.of().parseHex("41f870003e3c00ff20fc0000000051cffff8"), b.slice(0x800, 18).copy());
            assertEquals(0x7000, b.unsignedWord(0x802));
            assertEquals(255, b.unsignedWord(0x806));
            assertEquals(0, b.unsignedLong(0x80A));
            assertEquals(0xFFF8, b.unsignedWord(0x810));
            assertEquals(new CdSystemProgramSource.InitializationClear(0x800, 0x7000,
                    (b.unsignedWord(0x806) + 1) * 4, 0, 4), retained.initializationClear());
        }

        @Test void immutableCompleteFileRejectsPaddingAndIndependentCursorWrites() {
            var b = retained.bytes();
            assertSame(b, retained.bytes());
            byte[] copy = b.copy(); copy[0] ^= 1;
            assertArrayEquals(original, b.copy());
            var first = b.buffer(); var secondBuffer = b.buffer(); first.get();
            assertEquals(0, secondBuffer.position());
            assertThrows(ReadOnlyBufferException.class, () -> secondBuffer.put(0, (byte) 1));
            assertEquals(0, b.slice(17408, 0).length());
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedByte(17408));
            assertThrows(IndexOutOfBoundsException.class, () -> b.slice(-1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> b.slice(0, 17409));
            assertThrows(IndexOutOfBoundsException.class, () -> b.unsignedLong(17406));
            assertThrows(IndexOutOfBoundsException.class, () -> b.slice(0xC000, 1));
        }

        @Test void closedPipelineCannotReissueFixedSourceButRetainedValuesRemainUsable() {
            assertThrows(IOException.class, closed::systemProgramFile);
            assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(closed));
            assertThrows(IOException.class, closed::files);
            assertArrayEquals(original, retained.bytes().copy());
        }

        @Test void constructedUnknownAndForeignSourceClaimsCannotAuthenticateOriginalBytes() throws Exception {
            var unknown = malformed(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(original), entry);
            when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(disc.definition(), "0".repeat(64),
                    disc.cue(), disc.binaryFiles(), disc.originalContainer(), disc.tracks()));
            assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(unknown));
            var foreign = malformed(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(original), entry);
            var foreignHeader = mock(CdDiscHeader.class);
            when(foreignHeader.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.JAPAN));
            when(foreign.header()).thenReturn(foreignHeader);
            assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(foreign));
        }

        @Test void constructedChangedAndTruncatedImagesFailTheWholeFileFence() throws Exception {
            for (int at : new int[] {0, 0x806, original.length - 1}) {
                byte[] changed = original.clone(); changed[at] ^= 1;
                var incorrect = malformed(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(changed), entry);
                assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(incorrect));
            }
            var shortFile = malformed(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(
                    Arrays.copyOf(original, original.length - 1)), entry);
            assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(shortFile));
        }

        @Test void constructedWrongPathExtentAndDirectoryCannotBorrowTheSameGoodImage() throws Exception {
            for (var wrong : List.of(
                    new com.openggf.data.cd.Iso9660FileSystem.Entry("/IPX___.MMD;1", false, 10237, 17408, 1),
                    new com.openggf.data.cd.Iso9660FileSystem.Entry("/SPX___.BIN;1", false, 10238, 17408, 1),
                    new com.openggf.data.cd.Iso9660FileSystem.Entry("/SPX___.BIN;1", false, 10237, 17409, 1),
                    new com.openggf.data.cd.Iso9660FileSystem.Entry("/SPX___.BIN;1", true, 10237, 17408, 1),
                    new com.openggf.data.cd.Iso9660FileSystem.Entry("/SPX___.BIN;2", false, 10237, 17408, 2))) {
                var incorrect = malformed(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(original), wrong);
                assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(incorrect));
            }
        }

        // CONSTRUCTED negative source spy only: never used to produce a successful asset owner.
        private static CdProgramAssets malformed(com.openggf.game.soniccd.level.CdAssetBytes bytes,
                com.openggf.data.cd.Iso9660FileSystem.Entry record) throws IOException {
            var p = mock(CdProgramAssets.class);
            when(p.identity()).thenReturn(disc); when(p.header()).thenReturn(header);
            when(p.systemProgramFile()).thenReturn(new CdProgramAssets.SystemProgramFile(record, bytes));
            return p;
        }

        @Test void twoActualDataOwnersShareOnlyValuesAndNeverBorrowAnOpenFileLifetime() {
            assertNotSame(retained, second);
            assertNotSame(retained.bytes(), second.bytes());
            assertEquals(retained.source(), second.source());
            assertEquals(retained.initializationClear(), second.initializationClear());
            assertArrayEquals(retained.bytes().copy(), second.bytes().copy());
        }
    }

    @Test void configuredJapanIsNotAnOriginalUsaSystemSource() throws Exception { reject("Japan"); }
    @Test void configuredEuropeIsNotAnOriginalUsaSystemSource() throws Exception { reject("Europe"); }

    private static CdSystemProgramSource.Source metadata() {
        return new CdSystemProgramSource.Source(CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(),
                "/SPX___.BIN;1", 10237, 17408, SHA, 0xB800, 0xC000);
    }
    private static List<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList();
    }
    private static void reject(String region) throws Exception {
        try (var p = CdProgramAssets.open(cue(region))) {
            assertThrows(IOException.class, () -> CdSystemProgramSource.fromPrograms(p));
        }
    }
    private static Path cue(String region) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Optional original media source absent");
        var directory = Path.of(root).resolve(region);
        assertTrue(Files.isDirectory(directory), "Configured original source directory missing: " + region);
        try (var files = Files.list(directory)) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured source requires exactly one original CUE");
            return cues.getFirst();
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
