package com.openggf.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSolidProfileDecoder {
    @Test
    void preservesIndicesSignedBytesAndIndependentProfileStorage() {
        byte[] heights = new byte[32], widths = new byte[32], angles = {(byte) 0xFF, (byte) 0x80};
        heights[0] = -12; heights[16] = 7; widths[31] = -3;
        SolidTile[] result = SolidProfileDecoder.decode(heights, widths, angles);
        assertEquals(2, result.length);
        assertEquals(0, result[0].getIndex()); assertEquals(1, result[1].getIndex());
        assertEquals(-1, result[0].getAngle()); assertEquals(-128, result[1].getAngle());
        assertEquals(-12, result[0].heights[0]); assertEquals(7, result[1].heights[0]);
        assertEquals(-3, result[1].widths[15]);
        heights[0] = 0; widths[31] = 0; angles[0] = 0;
        assertEquals(-12, result[0].heights[0]); assertEquals(-3, result[1].widths[15]);
        assertEquals(-1, result[0].getAngle());
        result[0].heights[0] = 5;
        assertEquals(0, heights[0]); assertEquals(7, result[1].heights[0]);
    }

    @Test
    void retainsCopyOfRangePaddingAndFailureBehavior() {
        assertEquals(0, SolidProfileDecoder.decode(null, null, new byte[0]).length);
        SolidTile[] padded = SolidProfileDecoder.decode(new byte[]{5}, new byte[0], new byte[]{0});
        assertEquals(5, padded[0].heights[0]); assertEquals(0, padded[0].heights[15]);
        assertEquals(0, padded[0].widths[15]);
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> SolidProfileDecoder.decode(new byte[0], new byte[0], new byte[2]));
        assertThrows(NullPointerException.class,
                () -> SolidProfileDecoder.decode(null, new byte[16], new byte[1]));
    }
}
