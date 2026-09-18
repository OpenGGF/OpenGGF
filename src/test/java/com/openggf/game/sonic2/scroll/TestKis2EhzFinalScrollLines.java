package com.openggf.game.sonic2.scroll;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestKis2EhzFinalScrollLines {
    @Test
    void finalLinesUseAdvancedGradientWhileStockRetainsPriorBufferWords() {
        ParallaxTables tables = mock(ParallaxTables.class);
        int[] stock = new int[224];
        int[] patched = new int[224];
        Arrays.fill(stock, 0x12345678);
        Arrays.fill(patched, 0x12345678);
        new SwScrlEhz(tables).update(stock, 0x600, 0, 1, 0);
        new SwScrlEhz(tables, true).update(patched, 0x600, 0, 1, 0);
        assertArrayEquals(Arrays.copyOf(stock, 222), Arrays.copyOf(patched, 222));
        assertEquals(0x12345678, stock[222]);
        assertEquals(0x12345678, stock[223]);
        // d2=-1536; gradient starts -192, increments -12 for 78 scanlines.
        int expected = ((-0x600 & 0xFFFF) << 16) | (-1128 & 0xFFFF);
        assertEquals(expected, patched[222]);
        assertEquals(expected, patched[223]);
        assertNotEquals(patched[221], patched[222]);
    }
}
