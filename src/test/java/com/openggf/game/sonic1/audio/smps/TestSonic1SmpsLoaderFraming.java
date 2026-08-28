package com.openggf.game.sonic1.audio.smps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic1SmpsLoaderFraming {
    @Test
    void psgEnvelopeMustContainTheRetailHoldTerminator() {
        assertArrayEquals(new byte[] {0, 2, 4, (byte) 0x80},
                Sonic1SmpsLoader.requirePsgEnvelope(
                        new byte[] {0, 2, 4, (byte) 0x80}));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic1SmpsLoader.requirePsgEnvelope(
                        new byte[] {0, 2, 4}));
    }
}
