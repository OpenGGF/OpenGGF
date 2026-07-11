package com.openggf.game.patch;

import com.openggf.data.RomByteReader;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestLogicalRomResolver {

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
    }

    @Test
    void availabilityTrueWhenCombinedSourcePresent() {
        LogicalRomResolver resolver = new LogicalRomResolver(TestLogicalRomResolver::syntheticCombined);
        assertTrue(resolver.isAvailable(LogicalRom.SK));
        assertEquals(0x200000, resolver.openOrThrow(LogicalRom.SK).size());
    }

    @Test
    void romManagerBackendLoadsCombinedS3kImage() throws IOException {
        RomManager romManager = mock(RomManager.class);
        Rom combined = mock(Rom.class);
        when(romManager.getSecondaryRom("s3k")).thenReturn(combined);
        when(combined.readAllBytes()).thenReturn(syntheticCombined());

        LogicalRomResolver resolver = LogicalRomResolver.fromRomManager(romManager);

        assertTrue(resolver.isAvailable(LogicalRom.SK));
        assertEquals(0x11, resolver.openOrThrow(LogicalRom.SK).readU8(0));
        verify(romManager, times(1)).getSecondaryRom("s3k");
        verify(combined, times(1)).readAllBytes();
    }

    @Test
    void romManagerBackendPreservesIoFailureForOpen() throws IOException {
        RomManager romManager = mock(RomManager.class);
        IOException failure = new IOException("cannot open configured S3K image");
        when(romManager.getSecondaryRom("s3k")).thenThrow(failure);
        LogicalRomResolver resolver = LogicalRomResolver.fromRomManager(romManager);

        assertFalse(resolver.isAvailable(LogicalRom.SK));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> resolver.openOrThrow(LogicalRom.SK));

        assertEquals("Failed to open physical ROM for logical ROM SK", thrown.getMessage());
        assertEquals(failure, assertInstanceOf(IOException.class, thrown.getCause()));
        verify(romManager, times(1)).getSecondaryRom("s3k");
    }

    @Test
    void nullLogicalIdentityIsRejected() {
        LogicalRomResolver resolver = new LogicalRomResolver(TestLogicalRomResolver::syntheticCombined);
        assertThrows(NullPointerException.class, () -> resolver.isAvailable(null));
        assertThrows(NullPointerException.class, () -> resolver.openOrThrow(null));
    }
}
