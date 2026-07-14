package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestRomArtRequest {

    private static RomArtRequest valid() {
        return new RomArtRequest(0x50000, RomArtCompression.UNCOMPRESSED, 0x2960,
                0x60000, 0x70000, 0, 1);
    }

    @Test
    void validRequestConstructs() {
        RomArtRequest r = valid();
        assertTrue(r.hasDplc());
        assertEquals(0, r.paletteLine());
    }

    @Test
    void zeroDplcAddressMeansNoDplc() {
        RomArtRequest r = new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0,
                0x60000, 0, 1, 1);
        assertFalse(r.hasDplc());
    }

    @Test
    void negativeAddressesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(-1,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, -1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, -1, 0, 1));
    }

    @Test
    void uncompressedRequiresPositiveMultipleOf32Size() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.UNCOMPRESSED, 0, 0x60000, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.UNCOMPRESSED, 33, 0x60000, 0, 0, 1));
    }

    @Test
    void compressedForbidsExplicitSize() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 32, 0x60000, 0, 0, 1));
    }

    @Test
    void paletteLineMustBeZeroToThree() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, -1, 1));
    }

    @Test
    void bankSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 0));
    }

    @Test
    void nullCompressionRejected() {
        assertThrows(NullPointerException.class, () -> new RomArtRequest(0x50000,
                null, 0, 0x60000, 0, 0, 1));
    }
}
