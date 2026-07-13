package com.openggf.level;

import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevel {
    @Test
    void romFreeBuilderSupportsBothBlockGeometriesAndCopiesCreatorBuffers() throws Exception {
        for (int side : new int[] { 8, 16 }) {
            byte[] patterns = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            byte[] chunks = new byte[Chunk.CHUNK_SIZE_IN_ROM];
            byte[] blocks = new byte[side * side * 2];
            byte[] foreground = { 0 };
            byte[][] palettes = paletteLines();
            byte[] heights = new byte[SolidTile.TILE_SIZE_IN_ROM];
            byte[] widths = new byte[SolidTile.TILE_SIZE_IN_ROM];
            byte[] angles = { 0 };
            int[] collision = { 0 };

            ModLevel level = ModLevel.builder(4, side, patterns, chunks, blocks)
                    .layout(1, 1, foreground, null)
                    .paletteLines(palettes)
                    .solidProfiles(heights, widths, angles)
                    .collisionIndices(collision, collision)
                    .boundaries(1, 1024, -32, 512)
                    .spawns(List.of(), List.of(), ringSheet())
                    .build();

            patterns[0] = 0x7F;
            chunks[0] = 0x7F;
            blocks[0] = 0x7F;
            foreground[0] = 0x7F;
            palettes[0][0] = 0x7F;
            heights[0] = 0x7F;
            collision[0] = 7;

            assertEquals(side, level.getChunksPerBlockSide());
            assertEquals(side * 16, level.getBlockPixelSize());
            assertEquals(side, level.getBlock(0).getGridSide());
            assertEquals(1, level.getPatternCount());
            assertEquals(1, level.getChunkCount());
            assertEquals(1, level.getBlockCount());
            assertEquals(1, level.getSolidTileCount());
            assertEquals(0, level.getMap().getValue(0, 0, 0));
            assertEquals(0, level.getMap().getValue(1, 0, 0),
                    "missing background must become an exact zero layer");
            assertEquals(1, level.getMinX());
            assertEquals(1024, level.getMaxX());
            assertEquals(-32, level.getMinY());
            assertEquals(512, level.getMaxY());
        }
    }

    @Test
    void rejectsMissingReferencesAndWrongGeometryRecordSizes() {
        byte[] missingPattern = new byte[Chunk.CHUNK_SIZE_IN_ROM];
        missingPattern[1] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> complete(ModLevel.builder(4, 8,
                        new byte[Pattern.PATTERN_SIZE_IN_ROM], missingPattern,
                        new byte[8 * 8 * 2])).build());
        assertThrows(IllegalArgumentException.class,
                () -> ModLevel.builder(4, 16, new byte[Pattern.PATTERN_SIZE_IN_ROM],
                        new byte[Chunk.CHUNK_SIZE_IN_ROM], new byte[128]));
        assertThrows(IllegalArgumentException.class,
                () -> ModLevel.builder(4, 12, new byte[Pattern.PATTERN_SIZE_IN_ROM],
                        new byte[Chunk.CHUNK_SIZE_IN_ROM], new byte[12 * 12 * 2]));
    }

    private static ModLevel.Builder complete(ModLevel.Builder builder) {
        return builder.layout(1, 1, new byte[] { 0 }, null)
                .paletteLines(paletteLines())
                .solidProfiles(new byte[16], new byte[16], new byte[] { 0 })
                .collisionIndices(new int[] { 0 }, new int[] { 0 })
                .boundaries(0, 128, 0, 128)
                .spawns(List.of(), List.of(), ringSheet());
    }

    private static byte[][] paletteLines() {
        return new byte[4][Palette.PALETTE_SIZE_IN_ROM];
    }

    private static RingSpriteSheet ringSheet() {
        return new RingSpriteSheet(new Pattern[0], List.of(), 1, 8, 0, 0);
    }
}
