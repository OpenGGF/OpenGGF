package com.openggf.game.sonic3k.audio.smps;

import com.openggf.data.Rom;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;

class TestSonic3kSmpsLoaderFraming {
    @Test
    void unreadableDacCatalogFailsClosed() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(unreadableRom());

        assertNull(loader.loadDacData());
    }

    private static Rom unreadableRom() {
        return new Rom() {
            @Override
            public byte[] readBytes(long offset, int count)
                    throws IOException {
                throw new IOException("injected unreadable ROM");
            }

            @Override
            public byte readByte(long offset) throws IOException {
                throw new IOException("injected unreadable ROM");
            }

            @Override
            public long getSize() {
                return 0x40_0000L;
            }
        };
    }
}
