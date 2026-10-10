package starpost.realvalley;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Small independent ROM-format examples; no ROM or running engine required. */
class TestValleyEncoder {
    private S1Terrain terrain() {
        byte[][] pixels = {new byte[64], new byte[64]};
        Arrays.fill(pixels[1], (byte) 1);
        int[][] chunks = {{0x4000, 0x4000, 0x4000, 0x4000},
                {0xC801, 0xD001, 0xC001, 0xD801}};
        int[][] blocks = new int[3][256];
        Arrays.fill(blocks[1], 0xF401);
        Arrays.fill(blocks[2], 0xF401);
        byte[] heights = new byte[32];
        byte[] widths = new byte[32];
        Arrays.fill(heights, 16, 32, (byte) 16);
        Arrays.fill(widths, 16, 32, (byte) 16);
        int[] palette = new int[64];
        palette[33] = 0x0ACE;
        return new S1Terrain(pixels, chunks, new int[] {0, 1}, blocks, heights, widths,
                new byte[] {0, 0x40}, palette, 1, 1, new int[] {1});
    }

    private EncodedValley encode(S1Terrain terrain, int loop, int twin) {
        return ValleyEncoder.encode(terrain, new ValleyEncoder.Spec(new int[] {1}, 1, loop, twin, 3, 15));
    }

    private int word(byte[] data, int offset) {
        return (data[offset] & 255) << 8 | data[offset + 1] & 255;
    }

    @Test void splits256BlocksKeepsFlipsCollisionAndPriority() {
        S1Terrain source = terrain();
        EncodedValley encoded = encode(source, -1, -1);
        assertEquals(2, encoded.width());
        assertEquals(3, encoded.height());
        assertEquals(0, encoded.foreground()[0]);
        int block = encoded.foreground()[2] & 255;
        int cell = word(encoded.blocks(), block * 128);
        assertEquals(0xF400, cell & 0xFC00);
        int chunk = cell & 0x3FF;
        assertEquals(1, encoded.primary()[chunk]);
        assertEquals(1, encoded.secondary()[chunk]);
        assertEquals(0xC800, word(encoded.chunks(), chunk * 8) & 0xF800);
        assertArrayEquals(source.heights(), encoded.heights());
        assertArrayEquals(source.widths(), encoded.widths());
        assertArrayEquals(source.angles(), encoded.angles());
        assertTrue(Arrays.stream(encoded.claims()).anyMatch(c -> Arrays.equals(c, new int[] {2, 1, 0xACE})));
    }

    @Test void remapsHudCellsWithoutChangingVisibleColourOrTransparency() {
        S1Terrain source = terrain();
        Arrays.fill(source.chunks()[1], 0xA001); // priority, line 1, HUD-reserved colour 1
        source.palette()[17] = 0x0E04;
        source.pixels()[1][0] = 0;
        EncodedValley encoded = encode(source, -1, -1);
        for (int[] claim : encoded.claims()) {
            assertFalse(claim[0] == 1 && (claim[1] == 1 || claim[1] == 5 || claim[1] == 12
                    || claim[1] == 14 || claim[1] == 15));
            assertFalse(claim[0] == 0 || claim[0] == 3 && claim[1] == 15);
        }
        int block = encoded.foreground()[2] & 255;
        int chunk = word(encoded.blocks(), block * 128) & 0x3FF;
        int patternWord = word(encoded.chunks(), chunk * 8);
        assertTrue((patternWord & 0x8000) != 0);
        int pattern = patternWord & 0x7FF;
        int colour = encoded.patterns()[pattern * 32] & 15;
        assertEquals(0, (encoded.patterns()[pattern * 32] & 255) >>> 4);
        int line = patternWord >>> 13 & 3;
        assertTrue(Arrays.stream(encoded.claims()).anyMatch(c -> c[0] == line && c[1] == colour && c[2] == 0xE04));
    }

    @Test void loopTwinHasAnIndependentPathAndBakesDifferentArtFlip() {
        S1Terrain source = terrain();
        Arrays.fill(source.blocks()[1], 1); // visible art, primary collision absent
        Arrays.fill(source.blocks()[2], 0xF401); // flipped secondary collision
        EncodedValley encoded = encode(source, 1, 2);
        int block = encoded.foreground()[2] & 255;
        int cell = word(encoded.blocks(), block * 128);
        assertEquals(0xC400, cell & 0xFC00);
        int chunk = cell & 0x3FF;
        assertEquals(0, encoded.primary()[chunk]);
        assertEquals(1, encoded.secondary()[chunk]);
        // Baking swaps TL/TR and toggles each pattern's X flip, preserving the drawn art.
        assertEquals(0xD800, word(encoded.chunks(), chunk * 8) & 0xF800);
    }

    @Test void reflectsTwinCollisionWhenBothPathsNeedDifferentFlips() {
        S1Terrain source = terrain();
        Arrays.fill(source.blocks()[2], 0xF001);
        for (int i = 0; i < 16; i++) source.heights()[16 + i] = (byte) (i + 1);
        EncodedValley encoded = encode(source, 1, 2);
        int block = encoded.foreground()[2] & 255;
        int cell = word(encoded.blocks(), block * 128);
        int chunk = cell & 0x3FF;
        int front = encoded.primary()[chunk];
        int twin = encoded.secondary()[chunk];
        assertNotEquals(front, twin);
        for (int i = 0; i < 16; i++) {
            assertEquals(i + 1, encoded.heights()[front * 16 + i]);
            assertEquals(16 - i, encoded.heights()[twin * 16 + i]);
            assertEquals(-16, encoded.widths()[twin * 16 + i]);
        }
        assertEquals((byte) -0x40, encoded.angles()[twin]);
    }

    @Test void verticalReflectionKeepsFullTileHeightAndReversesWidths() {
        S1Terrain source = terrain();
        Arrays.fill(source.blocks()[1], 0xF801);
        Arrays.fill(source.blocks()[2], 0xF001);
        for (int i = 0; i < 16; i++) {
            source.heights()[16 + i] = (byte) (i + 1);
            source.widths()[16 + i] = (byte) (i + 1);
        }
        EncodedValley encoded = encode(source, 1, 2);
        int block = encoded.foreground()[2] & 255;
        int chunk = word(encoded.blocks(), block * 128) & 0x3FF;
        int twin = encoded.secondary()[chunk];
        for (int i = 0; i < 16; i++) {
            assertEquals(i == 15 ? 16 : -(i + 1), encoded.heights()[twin * 16 + i]);
            assertEquals(16 - i, encoded.widths()[twin * 16 + i]);
        }
        assertEquals(0x40, encoded.angles()[twin]);
    }

    @Test void rectangularGridPlacesAllRowsAndPreservesBlankCells() {
        var encoded=ValleyEncoder.encodeGrid(terrain(),new ValleyEncoder.Spec(new int[]{1,0,0,2},1,-1,-1,1,2),2,2);
        assertEquals(4,encoded.width()); assertEquals(5,encoded.height());
        // Each S1 cell becomes 2x2 S3K blocks, after one sky row.
        assertNotEquals(0,encoded.foreground()[4]); assertEquals(0,encoded.foreground()[6]);
        assertEquals(0,encoded.foreground()[12]); assertNotEquals(0,encoded.foreground()[14]);
        assertThrows(IllegalArgumentException.class,()->ValleyEncoder.encodeGrid(terrain(),
            new ValleyEncoder.Spec(new int[]{1},1,-1,-1,1,2),2,2));
    }

    @Test void doesNotAliasCollisionInputAndCompactsRepeatedQuadrants() {
        S1Terrain source = terrain();
        EncodedValley encoded = encode(source, -1, -1);
        assertTrue(encoded.blockCount() <= 3); // blank, solid quadrant, non-solid background quadrant
        encoded.heights()[16] = 7;
        assertEquals(16, source.heights()[16]);
    }
}
