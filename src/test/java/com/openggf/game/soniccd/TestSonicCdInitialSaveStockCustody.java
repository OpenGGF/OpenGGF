package com.openggf.game.soniccd;

import com.openggf.game.soniccd.media.CdInitialSaveAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailRelease;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Tests first: actual detached source DATA only; no profile I/O, bootstrap or World admission. Java UNRUN. */
class TestSonicCdInitialSaveStockCustody {
    @TempDir Path temporary;
    private record SourceKey(String root, CdReleaseVariant variant) { }
    private static final Map<SourceKey,CdGameDataSource> SOURCES = new HashMap<>();

    @Test void actualUsaPreparationRetainsTheOriginalPairAndAll706BytesAfterItsCueCloses() throws Exception {
        var source = source(CdReleaseVariant.USA);
        var initial = source.initialSaveAssets().orElseThrow();
        var id = initial.identity();
        assertSame(CdRetailReleases.USA, source.release());
        assertEquals(CdReleaseVariant.USA, id.variant());
        assertEquals(CdNativeFile.BURAMINIT, id.initializationFile());
        assertEquals(CdNativeFile.BURAMSUB, id.subCpuFile());
        assertEquals("5895de24b19d0a362f9184ff049d4e5e857f32b0e591184027b0802342765fe6", id.initializationSha256());
        assertEquals("a1585624a99082c7a41a4107848763e73f3a0e86a6fd74749c8868dcac114ab9", id.subCpuSha256());
        assertEquals(source.assets().identity().observedWholeDiscSha256(), id.observedWholeDiscSha256());
        assertEquals(source.release().normalizedSha256(), id.observedWholeDiscSha256());
        assertEquals(706, initial.initialData().length());
        assertEquals("c8e9bcc03252fd3cd3df44cbc2810c6bcc9ac1b1d155f2039b19ff76222abee1", sha(initial.initialData().copy()));
        assertEquals(676, initial.timeAttack().length());
        assertEquals(10742, initial.fileBackedInitializationCopyBytes());
        assertEquals(1546, initial.unresolvedInitializationCopyBytes());
        assertSame(initial, source.initialSaveAssets().orElseThrow());
        assertTrue(source.rom().isEmpty());
    }

    @Test void independentlyAuthenticatedSameCueProductsMatchEveryFiniteViewAndDescriptor() throws Exception {
        var source = source(CdReleaseVariant.USA);
        var retained = source.initialSaveAssets().orElseThrow();
        CdInitialSaveAssets independent;
        try (var programs = CdProgramAssets.open(suppliedCue(CdReleaseVariant.USA), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            independent = CdInitialSaveAssets.fromPrograms(programs);
            assertEquals(source.release().normalizedSha256(), programs.identity().normalizedSha256());
        }
        assertNotSame(retained, independent);
        assertEquals(retained.identity(), independent.identity());
        assertEquals(retained.initializationEntry(), independent.initializationEntry());
        assertEquals(retained.subCpuEntry(), independent.subCpuEntry());
        assertEquals(retained.initializationHeader(), independent.initializationHeader());
        assertEquals(retained.ranges(), independent.ranges());
        assertEquals(retained.transfers(), independent.transfers());
        assertEquals(retained.initialMainFields(), independent.initialMainFields());
        assertArrayEquals(retained.initialData().copy(), independent.initialData().copy());
        for (var table : CdInitialSaveAssets.Table.values()) {
            assertEquals(retained.range(table), independent.range(table));
            assertArrayEquals(retained.view(table).copy(), independent.view(table).copy());
        }
        byte[] detached = retained.initialData().copy();
        int original = retained.initialData().unsignedByte(0);
        detached[0] ^= 0x55;
        assertEquals(original, retained.initialData().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> retained.initialData().buffer().put(0, (byte) 1));
    }

    @Test void everyOldConstructorKeepsItsExplicitInitialSaveOmission() throws Exception {
        var s = source(CdReleaseVariant.USA);
        var candidates = List.of(
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets()),
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms()),
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms(), s.act2Assets()),
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms(), s.act2Assets(), s.act2PastAssets()),
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms(), s.act2Assets(), s.act2PastAssets(), s.act3FutureAssets()),
                new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms(), s.act2Assets(), s.act2PastAssets(), s.act3FutureAssets(), s.r31aAssets()));
        for (var candidate : candidates) {
            assertTrue(candidate.initialSaveAssets().isEmpty(), "Old constructors must not secretly load or default initial data");
            assertEquals(s.identity(), candidate.identity());
            assertSame(s.release(), candidate.release());
            assertSame(s.assets(), candidate.assets());
            assertSame(s.fmSoundAssets().orElseThrow(), candidate.fmSoundAssets().orElseThrow());
        }
    }

    @Test void explicitTypedRetentionKeepsAllExactEarlierOwnersAndTheExistingIdentity() throws Exception {
        var s = source(CdReleaseVariant.USA);
        var initial = s.initialSaveAssets().orElseThrow();
        var retained = retain(s, Optional.of(initial));
        assertSame(initial, retained.initialSaveAssets().orElseThrow());
        assertSame(s.release(), retained.release());
        assertSame(s.assets(), retained.assets());
        assertSame(s.fmSoundAssets().orElseThrow(), retained.fmSoundAssets().orElseThrow());
        assertSame(s.timeWarpPrograms().orElseThrow(), retained.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(), retained.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(), retained.act2PastAssets().orElseThrow());
        assertSame(s.act3FutureAssets().orElseThrow(), retained.act3FutureAssets().orElseThrow());
        assertSame(s.r31aAssets().orElseThrow(), retained.r31aAssets().orElseThrow());
        assertEquals(s.identity(), retained.identity(), "The existing whole-disc identity is DATA, not a presence certificate");
        assertTrue(retain(s, Optional.empty()).initialSaveAssets().isEmpty());
    }

    @ParameterizedTest(name = "{0}: initial-save data stays absent and foreign USA ownership rejects")
    @EnumSource(value = CdReleaseVariant.class, names = {"JAPAN", "EUROPE"})
    void regionalPreparationPreservesAbsenceAndRejectsTheOriginalUsaPair(CdReleaseVariant variant) throws Exception {
        var other = source(variant);
        assertTrue(other.initialSaveAssets().isEmpty());
        assertTrue(retain(other, Optional.empty()).initialSaveAssets().isEmpty());
        var usa = source(CdReleaseVariant.USA).initialSaveAssets();
        assertThrows(IllegalArgumentException.class, () -> retain(other, usa));
        assertTrue(other.fmSoundAssets().isEmpty());
        assertTrue(other.timeWarpPrograms().isEmpty());
        assertTrue(other.r31aAssets().isEmpty());
    }

    @Test void nullAndContradictoryDiscMetadataRejectWithoutDefaultOrPrivateOwnerForgery() throws Exception {
        var s = source(CdReleaseVariant.USA);
        assertThrows(NullPointerException.class, () -> retain(s, null));
        var wrong = new CdRetailRelease("constructed-invalid-disc", CdReleaseVariant.USA, "0".repeat(64), s.release().reference());
        assertThrows(IllegalArgumentException.class, () -> new CdGameDataSource(wrong, s.assets(), s.fmSoundAssets(),
                s.timeWarpPrograms(), s.act2Assets(), s.act2PastAssets(), s.act3FutureAssets(), s.r31aAssets(), s.initialSaveAssets()));
        assertSame(s.initialSaveAssets().orElseThrow(), retain(s, s.initialSaveAssets()).initialSaveAssets().orElseThrow());
    }

    @Test void typedBorrowIsPackagePrivateAndPublicSourceApisStayExact() throws Exception {
        var borrow = CdGameDataSource.class.getDeclaredMethod("initialSaveAssets");
        assertFalse(Modifier.isPublic(borrow.getModifiers()));
        assertFalse(Modifier.isProtected(borrow.getModifiers()));
        assertFalse(Modifier.isPrivate(borrow.getModifiers()));
        assertEquals("java.util.Optional<com.openggf.game.soniccd.media.CdInitialSaveAssets>", borrow.getGenericReturnType().getTypeName());
        var field = CdGameDataSource.class.getDeclaredField("initialSaveAssets");
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(borrow.getGenericReturnType(), field.getGenericType());
        assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10, 11), Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).map(v -> v.getParameterCount()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).noneMatch(v -> Modifier.isPublic(v.getModifiers())));
        assertEquals(List.of("act2Assets", "act2PastAssets", "act3FutureAssets", "assets", "fmSoundAssets", "identity", "openAsset", "r31aAssets", "release", "rom", "timeWarpPrograms"),
                Arrays.stream(CdGameDataSource.class.getDeclaredMethods()).filter(v -> Modifier.isPublic(v.getModifiers())).map(v -> v.getName()).sorted().toList());
        assertEquals(List.of("prepare"), Arrays.stream(CdStockLaunch.class.getDeclaredMethods()).filter(v -> Modifier.isPublic(v.getModifiers())).map(v -> v.getName()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredFields()).noneMatch(v -> v.getType() == CdProgramAssets.class || v.getType() == Path.class));
    }

    @Test void retentionAddsNoHostAssetPathAndPreservesTheThreeImmutableStreams() throws Exception {
        var s = source(CdReleaseVariant.USA);
        for (String path : List.of("BRAMINIT.MMD;1", "BRAMSUB.BIN;1", "save/initial.bin", "profile/default.json", "../BRAMINIT.MMD;1"))
            assertThrows(FileNotFoundException.class, () -> s.openAsset(path));
        for (String path : List.of(CdGameDataSource.DECODED_BLOCKS, CdGameDataSource.DECODED_TILES, CdGameDataSource.INITIAL_PALETTE)) {
            byte[] a;
            try (var first = s.openAsset(path); var second = s.openAsset(path)) {
                a = first.readAllBytes();
                assertArrayEquals(a, second.readAllBytes());
            }
            byte[] changed = a.clone(); changed[0] ^= 0x55;
            try (var again = s.openAsset(path)) { assertArrayEquals(a, again.readAllBytes()); }
        }
    }

    @Test void oldStagePreflightAndMissingSourceFailuresRemainExplicit() {
        assertThrows(UnsupportedOperationException.class, () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"),
                new CdStockLaunch.Request(CdStagePayload.R31A.stage(), Optional.of(CdReleaseVariant.USA), Optional.empty())));
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"), CdStockLaunch.Request.r11a()));
    }

    private static CdGameDataSource retain(CdGameDataSource s, Optional<CdInitialSaveAssets> initial) {
        return new CdGameDataSource(s.release(), s.assets(), s.fmSoundAssets(), s.timeWarpPrograms(), s.act2Assets(), s.act2PastAssets(), s.act3FutureAssets(), s.r31aAssets(), initial);
    }
    private static synchronized CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        String root = configuredRoot();
        var key = new SourceKey(root, variant);
        var cached = SOURCES.get(key);
        if (cached != null) return cached;
        var created = CdStockLaunch.prepare(suppliedCue(variant), CdStockLaunch.Request.r11a());
        SOURCES.put(key, created);
        return created;
    }
    private static String configuredRoot() {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Optional original media absent; set soniccd.media.root");
        return root;
    }
    private static Path suppliedCue(CdReleaseVariant variant) throws IOException {
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(configuredRoot()).resolve(directory))) {
            var cues = files.filter(v -> v.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured source directory requires exactly one original CUE");
            return cues.getFirst();
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
