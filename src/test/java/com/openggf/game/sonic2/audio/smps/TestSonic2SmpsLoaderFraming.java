package com.openggf.game.sonic2.audio.smps;

import com.openggf.data.Rom;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestSonic2SmpsLoaderFraming {
    @Test
    void saxmanPayloadLengthUsesOnlyTheRetailLittleEndianHeader() {
        assertEquals(0x1234,
                Sonic2SmpsLoader.requireSaxmanPayloadLength(
                        0x34, 0x12, 0x2000));

        assertThrows(IllegalArgumentException.class,
                () -> Sonic2SmpsLoader.requireSaxmanPayloadLength(
                        0x01, 0x20, 0x1000),
                "a plausible byte-swapped length must not hide a malformed header");
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2SmpsLoader.requireSaxmanPayloadLength(
                        0x00, 0x00, 0x1000));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2SmpsLoader.requireSaxmanPayloadLength(
                        0x01, 0x00, 0));
    }

    @Test
    void uncompressedSongsRequireAnExactRetailBoundary() {
        assertEquals(0xED,
                Sonic2SmpsLoader.requireUncompressedMusicSize(0x0FD48D));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2SmpsLoader.requireUncompressedMusicSize(0x123456));
    }

    @Test
    void unreadableDacCatalogFailsClosed() {
        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(unreadableRom());

        assertNull(loader.loadDacData());
    }

    private static Rom unreadableRom() {
        return new Rom() {
            @Override
            public byte readByte(long offset) throws IOException {
                throw new IOException("injected unreadable ROM");
            }

            @Override
            public long getSize() {
                return 0x20_0000L;
            }
        };
    }
}
