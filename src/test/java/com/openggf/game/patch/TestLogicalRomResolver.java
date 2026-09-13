package com.openggf.game.patch;

import com.openggf.data.PhysicalImage;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLogicalRomResolver {

    private static RomImageCatalogue.Resolution resolution(RomIdentity rom) {
        PhysicalImage image = PhysicalImage.ofBytes(Path.of("/synthetic/" + rom + ".gen"), new byte[] {0x11});
        return new RomImageCatalogue.Resolution(rom,
                List.of(new RomImageCatalogue.Part(rom, image, 0, 1)),
                RomImageCatalogue.Source.DIRECTORY_ORDER);
    }

    /** Synthetic 4MB "combined" image: SK half = 0x11, S3 half = 0x22. */
    private static byte[] syntheticCombined() {
        byte[] data = new byte[0x400000];
        java.util.Arrays.fill(data, 0, 0x200000, (byte) 0x11);
        java.util.Arrays.fill(data, 0x200000, 0x400000, (byte) 0x22);
        return data;
    }

    @Test
    void skWindowOverCombinedRomServesSkHalfOnly() throws IOException {
        RomByteReader sk = LogicalRomResolver.windowSkFromCombined(syntheticCombined());
        assertEquals(0x200000, sk.size());
        assertEquals(0x11, sk.readU8(0x000000));
        assertEquals(0x11, sk.readU8(0x1FFFFF));
    }

    @Test
    void skWindowBoundsGuardRejectsS3HalfReads() throws IOException {
        RomByteReader sk = LogicalRomResolver.windowSkFromCombined(syntheticCombined());
        assertThrows(IndexOutOfBoundsException.class, () -> sk.readU8(0x200000));
    }

    @Test
    void combinedImageSmallerThanSkHalfIsRejected() {
        assertThrows(IOException.class,
                () -> LogicalRomResolver.windowSkFromCombined(new byte[0x100000]));
    }

    @Test
    void byteRangeFactoryRejectsRangePastSourceEnd() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> RomByteReader.fromBytes(new byte[4], 0, 5));
    }

    @Test
    void availabilityFalseWhenNoSourceConfigured() {
        LogicalRomResolver resolver = new LogicalRomResolver(() -> null);
        assertFalse(resolver.isAvailable(LogicalRom.SK));
        assertFalse(resolver.isAvailable(LogicalRom.S3K));
        assertThrows(IllegalStateException.class, () -> resolver.openOrThrow(LogicalRom.SK));
    }

    @Test
    void combinedSourceServesSkS3AndS3kWindowsAndNothingElse() {
        int[] fetches = {0};
        LogicalRomResolver resolver = new LogicalRomResolver(() -> {
            fetches[0]++;
            return syntheticCombined();
        });
        assertTrue(resolver.isAvailable(LogicalRom.SK));
        RomByteReader sk = resolver.openOrThrow(LogicalRom.SK);
        assertEquals(0x200000, sk.size());
        assertEquals(0x11, sk.readU8(0x1FFFFF));
        assertThrows(IndexOutOfBoundsException.class, () -> sk.readU8(0x200000));

        RomByteReader s3 = resolver.openOrThrow(LogicalRom.S3);
        assertEquals(0x200000, s3.size());
        assertEquals(0x22, s3.readU8(0));

        RomByteReader s3k = resolver.openOrThrow(LogicalRom.S3K);
        assertEquals(0x400000, s3k.size());
        assertEquals(0x1122, s3k.readU16BE(0x1FFFFF));

        assertFalse(resolver.isAvailable(LogicalRom.S1));
        assertFalse(resolver.isAvailable(LogicalRom.KIS2));
        assertEquals(1, fetches[0], "combined bytes are fetched once");
    }

    @Test
    void combinedSourceOfOnlyTheSkCartDoesNotOfferS3() {
        byte[] skOnly = new byte[0x200000];
        LogicalRomResolver resolver = new LogicalRomResolver(() -> skOnly);
        assertTrue(resolver.isAvailable(LogicalRom.SK));
        assertFalse(resolver.isAvailable(LogicalRom.S3));
        assertFalse(resolver.isAvailable(LogicalRom.S3K));
    }

    @Test
    void romManagerBackendResolvesThroughTheCatalogue() throws IOException {
        RomManager romManager = mock(RomManager.class);
        RomByteReader sk = RomByteReader.fromBytes(new byte[] {0x11});
        when(romManager.resolveLogicalRom(RomIdentity.SK)).thenReturn(Optional.of(resolution(RomIdentity.SK)));
        when(romManager.openLogicalRom(RomIdentity.SK)).thenReturn(sk);

        LogicalRomResolver resolver = LogicalRomResolver.fromRomManager(romManager);

        assertTrue(resolver.isAvailable(LogicalRom.SK));
        assertEquals(0x11, resolver.openOrThrow(LogicalRom.SK).readU8(0));
        assertFalse(resolver.isAvailable(LogicalRom.KIS2));
        verify(romManager, never()).openLogicalRom(RomIdentity.KIS2);
    }

    @Test
    void romManagerBackendPreservesIoFailureForOpen() throws IOException {
        RomManager romManager = mock(RomManager.class);
        IOException failure = new IOException("cannot open configured S3K image");
        when(romManager.resolveLogicalRom(RomIdentity.SK)).thenReturn(Optional.of(resolution(RomIdentity.SK)));
        when(romManager.openLogicalRom(RomIdentity.SK)).thenThrow(failure);
        LogicalRomResolver resolver = LogicalRomResolver.fromRomManager(romManager);

        assertFalse(resolver.isAvailable(LogicalRom.SK));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> resolver.openOrThrow(LogicalRom.SK));

        assertEquals("Failed to open physical ROM for logical ROM SK", thrown.getMessage());
        assertEquals(failure, assertInstanceOf(IOException.class, thrown.getCause()));
        verify(romManager, times(2)).openLogicalRom(RomIdentity.SK);
    }

    @Test
    void romManagerBackendReportsMissingRomsWithoutOpening() throws IOException {
        RomManager romManager = mock(RomManager.class);
        when(romManager.resolveLogicalRom(any())).thenReturn(Optional.empty());
        LogicalRomResolver resolver = LogicalRomResolver.fromRomManager(romManager);

        assertFalse(resolver.isAvailable(LogicalRom.S1));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> resolver.openOrThrow(LogicalRom.S1));
        assertEquals("No physical ROM available for logical ROM S1", thrown.getMessage());
        verify(romManager, never()).openLogicalRom(any());
    }

    @Test
    void everyLogicalRomMapsToADistinctCatalogueIdentityAndBack() {
        java.util.EnumSet<RomIdentity> seen = java.util.EnumSet.noneOf(RomIdentity.class);
        for (LogicalRom rom : LogicalRom.values()) {
            assertTrue(seen.add(rom.identity()), "duplicate identity for " + rom);
            assertEquals(rom, LogicalRom.of(rom.identity()));
            assertEquals(rom.name(), rom.identity().name(), "names stay in lockstep");
        }
        assertEquals(java.util.EnumSet.allOf(RomIdentity.class), seen, "every identity has a logical ROM");
    }

    @Test
    void nullLogicalIdentityIsRejected() {
        LogicalRomResolver resolver = new LogicalRomResolver(TestLogicalRomResolver::syntheticCombined);
        assertThrows(NullPointerException.class, () -> resolver.isAvailable(null));
        assertThrows(NullPointerException.class, () -> resolver.openOrThrow(null));
        assertThrows(NullPointerException.class, () -> LogicalRomResolver.fromRomManager(null));
    }
}
