package com.openggf.game.ghost;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestGhostFrameCodec {
    @Test
    void roundTripsAllFields() {
        GhostFrame f = new GhostFrame(0x1234, 0xFEDC, 0xAB, true, false, true, 5, true);
        byte[] buf = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(f, buf, 0);
        assertEquals(f, GhostFrameCodec.decode(buf, 0));
    }

    @Test
    void packsBitsPerSpec() {
        GhostFrame f = new GhostFrame(0x0102, 0x0304, 7, true, true, false, 3, false);
        byte[] buf = new byte[7];
        GhostFrameCodec.encode(f, buf, 0);
        assertEquals(0x01, buf[0] & 0xFF); assertEquals(0x02, buf[1] & 0xFF); // x BE
        assertEquals(0x03, buf[2] & 0xFF); assertEquals(0x04, buf[3] & 0xFF); // y BE
        assertEquals(7, buf[4] & 0xFF);
        assertEquals(0b0000_0011, buf[5] & 0xFF); // hFlip|vFlip, no finished
        assertEquals(0b0000_0011, buf[6] & 0xFF); // bucket=3, high=false
    }

    @Test
    void treatsCoordinatesAsUnsigned16() {
        GhostFrame f = new GhostFrame(0xFFFF, 0x8000, 0, false, false, false, 0, false);
        byte[] buf = new byte[7];
        GhostFrameCodec.encode(f, buf, 0);
        GhostFrame back = GhostFrameCodec.decode(buf, 0);
        assertEquals(0xFFFF, back.x());
        assertEquals(0x8000, back.y());
    }

    @Test
    void reservedLayerBitsDecodeIgnoredAndEncodeZero() {
        byte[] buf = new byte[7];
        GhostFrameCodec.encode(new GhostFrame(1, 1, 1, false, false, false, 7, true), buf, 0);
        assertEquals(0, (buf[6] & 0xF0));
        buf[6] |= (byte) 0xF0; // future extension bits must not break decode
        assertEquals(7, GhostFrameCodec.decode(buf, 0).priorityBucket());
    }
}
