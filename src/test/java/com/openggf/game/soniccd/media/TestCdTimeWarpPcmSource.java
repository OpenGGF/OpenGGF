package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Original-media PCM source custody only, opt in with soniccd.media.root.
 * Retained file data establishes no current installation, initialized driver, clock, music or output.
 * Existing TestCdTimeWarpPrograms controls retain complete-disc and regional rejection coverage.
 */
class TestCdTimeWarpPcmSource {
    private static final int FILE_BYTES = 217866;
    private static final String PCM_SHA = "fd7ab10c908bd26545afc5bf5da0765e7178fe8fe2929b15957acf9bd7d6e558";
    private static CdProgramAssets originalOwner;
    private static CdTimeWarpPrograms detached;
    private static byte[] originalPcm;

    @BeforeAll static void detachOnlyFromTheActualAuthenticatedUsaOwner() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for original PCM source checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty());
        originalOwner = owner;
        try (owner) {
            assertEquals("/SNCBNK1B.BIN;1", owner.entry(CdNativeFile.R1PCM).path());
            assertEquals(FILE_BYTES, owner.entry(CdNativeFile.R1PCM).size());
            assertEquals(CdNativeFile.Kind.PCM_DRIVER, CdNativeFile.R1PCM.kind());
            assertThrows(IllegalArgumentException.class, () -> owner.mmd(CdNativeFile.R1PCM));
            originalPcm = owner.read(CdNativeFile.R1PCM, 0, FILE_BYTES);
            detached = CdTimeWarpPrograms.fromPrograms(owner);
        }
    }

    @Test void authenticatedOriginalPcmRemainsExactAfterItsDiscOwnerCloses() throws Exception {
        assertThrows(IOException.class, () -> originalOwner.entry(CdNativeFile.R1PCM));
        assertThrows(IOException.class, () -> originalOwner.read(CdNativeFile.R1PCM, 0, 1));
        assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(originalOwner));
        assertEquals(FILE_BYTES, pcm().length());
        assertArrayEquals(originalPcm, pcm().copy());
        assertEquals(PCM_SHA, sha(pcm().copy()));
        assertEquals(new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(),
                "a417c910f61e0d95b33f292b3e84206e1f71018c4478c83da86737b10d0ffe06",
                "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265"), detached.source());
    }

    @Test void nativeHeaderEntriesAndZeroTemplateRemainInertOriginalFileData() throws Exception {
        // N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic.asm:17-19;
        // DriverOrigin / Variables (_Driver.asm:15-34). These are source bytes, not executed instructions.
        assertArrayEquals("SNCBNK25.S28    ".getBytes(StandardCharsets.US_ASCII), pcm().slice(0, 16).copy());
        assertEquals(0x4EFA0906L, pcm().unsignedLong(0x10));
        assertEquals(0x4EFA0F90L, pcm().unsignedLong(0x14));
        assertArrayEquals(new byte[0x900], pcm().slice(0x18, 0x900).copy());
        assertEquals("cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b",
                sha(pcm().slice(0x18, 0x900).copy()));
        // pdrvOn and pdrvSndPlay are still the original zero template. Lazy InitDriver is a later algorithm.
        assertEquals(0, pcm().unsignedByte(0x18 + 2));
        assertEquals(0, pcm().unsignedByte(0x18 + 9));
    }

    @Test void oneImmutablePcmViewReturnsOwnedCopiesAndIndependentReadOnlyCursors() throws Exception {
        assertSame(pcm(), detached.r1Pcm());
        byte[] changed = pcm().copy();
        changed[0] ^= 0x55;
        changed[FILE_BYTES - 1] ^= 0x55;
        assertArrayEquals(originalPcm, pcm().copy());
        var first = pcm().buffer();
        var second = pcm().buffer();
        first.get();
        assertEquals(1, first.position());
        assertEquals(0, second.position());
        assertEquals(FILE_BYTES, second.remaining());
        assertThrows(ReadOnlyBufferException.class, () -> second.put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> pcm().slice(0x18, 4).buffer().put(0, (byte) 0));
        assertEquals(PCM_SHA, sha(pcm().copy()));
    }

    @Test void sourceBoundsEndAtTheOriginalFileAndExcludeSectorTailAndMmdCoordinates() {
        assertEquals(0, pcm().slice(FILE_BYTES, 0).length());
        assertEquals(originalPcm[FILE_BYTES - 1], pcm().signedByte(FILE_BYTES - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().signedByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().unsignedByte(FILE_BYTES));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().unsignedWord(FILE_BYTES - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().unsignedLong(Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().slice(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().slice(1, Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().slice(FILE_BYTES, 1270));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().slice(0x40000, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> pcm().slice(0xFF2000, 1));
    }

    private static CdAssetBytes pcm() { return detached.r1Pcm(); }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
