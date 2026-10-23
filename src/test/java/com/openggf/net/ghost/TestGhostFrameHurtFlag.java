package com.openggf.net.ghost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hurt flag rides in the flags byte's spare bit, so frames keep their seven bytes. */
class TestGhostFrameHurtFlag {
    @Test
    void hurtRoundTripsInTheSpareFlagBit() {
        GhostFrame hurt = new GhostFrame(1234, 567, 26, true, false, false, 2, true, true);
        byte[] bytes = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(hurt, bytes, 0);
        assertEquals(0x08 | 0x01, bytes[5] & 0xFF, "hFlip and hurt");
        assertEquals(hurt, GhostFrameCodec.decode(bytes, 0));
    }

    @Test
    void framesWithoutTheBitAreNotHurt() {
        GhostFrame plain = new GhostFrame(10, 20, 3, false, true, true, 1, false);
        assertFalse(plain.hurt(), "the eight-value constructor is a player who is not hurt");
        byte[] bytes = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(plain, bytes, 0);
        assertEquals(0x02 | 0x04, bytes[5] & 0xFF);
        GhostFrame decoded = GhostFrameCodec.decode(bytes, 0);
        assertFalse(decoded.hurt());
        bytes[5] |= 0x08;
        assertTrue(GhostFrameCodec.decode(bytes, 0).hurt());
    }
}
