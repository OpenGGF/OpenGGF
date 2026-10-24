package com.openggf.game.soniccd.level;

import com.openggf.game.mutation.DirectLevelMutationSurface;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.render.PlaneRasterizer;
import org.junit.jupiter.api.Test;

import static com.openggf.game.soniccd.level.CdR11aTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure component adaptation, without forging an authenticated CdR11aAssets or booting an engine. */
class TestSonicCdLevelGeometry {
    @Test
    void initialDecodeKeepsAllPatternQuadrantsAndIndependentBlankDescriptors() {
        Fixture f = new Fixture();
        word(f.blocks, 7 * 8, 0xA123);
        word(f.blocks, 7 * 8 + 2, 0x4132);
        word(f.blocks, 7 * 8 + 4, 0x6250);
        word(f.blocks, 7 * 8 + 6, 0xFB13);
        var first = f.level();
        var second = f.level();
        var chunk = first.getChunk(7);
        assertEquals(0xA123, chunk.getPatternDesc(0, 0).get());
        assertEquals(0x4132, chunk.getPatternDesc(1, 0).get());
        assertEquals(0x6250, chunk.getPatternDesc(0, 1).get());
        assertEquals(0xFB13, chunk.getPatternDesc(1, 1).get());
        assertNotSame(first.getBlock(0).getChunkDesc(0, 0), first.getBlock(0).getChunkDesc(1, 0));
        assertNotSame(first.getBlock(0).getChunkDesc(0, 0), second.getBlock(0).getChunkDesc(0, 0));
        first.getBlock(0).setChunkDesc(0, 0, new ChunkDesc(7));
        assertEquals(0, second.getBlock(0).getChunkDesc(0, 0).get());
    }

    @Test
    void engineNestingResolvesOneBasedNativeChunksAndZeroBasedNativeBlocks() {
        Fixture f = new Fixture();
        f.fg[2] = 121;
        word(f.chunks, 120 * 512, 0x7B23); // Native block 803, XY flips, both solidity bits.
        word(f.blocks, 803 * 8, 0xFB1F); // Pattern 799, XY flips, palette 3, priority.
        f.patterns[799 * 32] = (byte) 0xA3;
        f.ids[803] = (byte) 255;
        f.angles[255] = (byte) 0xE1;
        f.heights[255 * 16] = (byte) 0xF0;
        f.widths[255 * 16 + 15] = (byte) 0xFF;
        Level level = f.level();

        assertEquals(800, level.getPatternCount());
        assertEquals(804, level.getChunkCount());
        assertEquals(122, level.getBlockCount()); // Blank slot + native IDs 1..121.
        assertEquals(256, level.getSolidTileCount());
        assertEquals(256, level.getBlockPixelSize());
        assertEquals(16, level.getChunksPerBlockSide());
        int blockId = Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0));
        assertEquals(121, blockId);
        var block = level.getBlock(blockId);
        assertEquals(16, block.getGridSide());
        var desc = block.getChunkDesc(0, 0);
        assertEquals(0xFF23, desc.get());
        assertEquals(803, desc.getChunkIndex());
        assertTrue(desc.getHFlip());
        assertTrue(desc.getVFlip());
        for (int bit : new int[]{12, 13, 14, 15}) {
            assertTrue(desc.isSolidityBitSet(bit));
        }
        var chunk = level.getChunk(desc.getChunkIndex());
        var patternDesc = chunk.getPatternDesc(0, 0);
        assertEquals(0xFB1F, patternDesc.get());
        assertEquals(799, patternDesc.getPatternIndex());
        assertEquals(3, patternDesc.getPaletteIndex());
        assertTrue(patternDesc.getPriority());
        assertTrue(patternDesc.getHFlip());
        assertTrue(patternDesc.getVFlip());
        assertEquals(10, level.getPattern(799).getPixel(0, 0));
        assertEquals(3, level.getPattern(799).getPixel(1, 0));
        assertEquals(255, chunk.getSolidTileIndex());
        assertEquals(255, chunk.getSolidTileAltIndex());
        var solid = level.getSolidTile(chunk.getSolidTileIndex());
        assertEquals(255, solid.getIndex());
        assertEquals(0xE1, Byte.toUnsignedInt(solid.getAngle()));
        assertEquals(-16, solid.getHeightAt((byte) 0));
        assertEquals(-1, solid.getWidthAt((byte) 15));
        assertEquals(0x1F, Byte.toUnsignedInt(solid.getAngle(true, false)));
        assertEquals(0x9F, Byte.toUnsignedInt(solid.getAngle(false, true)));
        assertEquals(0x61, Byte.toUnsignedInt(solid.getAngle(true, true)));
    }

    @Test
    void eachNativeFlipAndSolidityCombinationConvertsToEngineBits() {
        Fixture f = new Fixture();
        f.fg[2] = 1;
        for (int flips = 0; flips < 4; flips++) {
            for (int solidity = 0; solidity < 4; solidity++) {
                int nativeWord = 1 | ((flips & 1) << 11) | ((flips & 2) << 11) | (solidity << 13);
                word(f.chunks, (flips * 4 + solidity) * 2, nativeWord | 0x8000);
            }
        }
        var level = f.level();
        for (int flips = 0; flips < 4; flips++) {
            for (int solidity = 0; solidity < 4; solidity++) {
                var desc = level.getBlock(1).getChunkDesc(flips * 4 + solidity, 0);
                assertEquals(1 | (flips << 10) | (solidity << 12) | (solidity << 14), desc.get());
                assertEquals((flips & 1) != 0, desc.getHFlip());
                assertEquals((flips & 2) != 0, desc.getVFlip());
            }
        }
    }

    @Test
    void declaredPlanesKeepNativeClearedCellsWithoutRepeatingRowsOrColumns() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9;
        f.bg[2] = 1;
        f.fg[2 + 42] = 2;
        f.ids[0] = 7; // Native block ID zero is still no collision, independently of table[0].
        var terrain = f.terrain();
        var level = f.level(terrain);
        assertEquals(64, level.getMap().getWidth());
        assertEquals(16, level.getMap().getHeight());
        assertEquals(2, level.getMap().getLayerCount());
        assertEquals(42, level.getLayerWidthBlocks(0));
        assertEquals(6, level.getLayerWidthBlocks(1));
        assertEquals(8, level.getLayerHeightBlocks(0));
        assertEquals(8, level.getLayerHeightBlocks(1));
        assertEquals(121, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        assertEquals(0xF9, terrain.layout().cell(FOREGROUND, 0, 0).rawByte());
        assertEquals(1, level.getMap().getValue(1, 0, 0));
        assertEquals(2, level.getMap().getValue(0, 0, 1));
        assertEquals(0, level.getMap().getValue(0, 42, 0));
        assertEquals(0, level.getMap().getValue(1, 6, 0));
        assertEquals(0, level.getMap().getValue(1, 0, 1));
        assertEquals(0, level.getMap().getValue(1, 63, 15));
        for (int y = 8; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(0, level.getMap().getValue(0, x, y));
                assertEquals(0, level.getMap().getValue(1, x, y));
            }
        }
        assertEquals(0, level.getBlock(0).getChunkDesc(15, 15).get());
        assertEquals(0, level.getChunk(0).getSolidTileIndex());
        assertThrows(UnsupportedOperationException.class, () -> level.resolveCollisionBlockIndex(121, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> level.getLayerWidthBlocks(2));
        assertThrows(IndexOutOfBoundsException.class, () -> level.getLayerHeightBlocks(-1));
    }

    @Test
    void unloadedDrawingCellsFailIndividuallyAndDistinctCollisionIndicesRemainNativeData() {
        Fixture f = new Fixture();
        f.fg[2] = 1;
        word(f.chunks, 120 * 512, 0x7FC); // Draw 1020; collision 2044. Chunk 121 is unused.
        word(f.chunks, 120 * 512 + 2, 0x401); // Draw 1; collision 1025, still valid to draw.
        var terrain = f.terrain();
        var level = f.level(terrain);
        assertEquals(0, level.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertThrows(UnsupportedOperationException.class, () -> level.getBlock(121).getChunkDesc(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> level.getBlock(121).saveState());
        assertEquals(1, level.getBlock(121).getChunkDesc(1, 0).getChunkIndex());
        assertEquals(0x401, terrain.chunkWord(121, 1, 0).rawWord());
        assertEquals(1, terrain.chunkWord(121, 1, 0).renderBlockIndex());
        assertEquals(1025, terrain.chunkWord(121, 1, 0).collisionBlockIndex());
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.collision().idForBlock(1025));
        assertThrows(IllegalArgumentException.class, () -> level.getChunk(1020));
        assertThrows(IllegalArgumentException.class, () -> level.getPattern(800));
        assertThrows(UnsupportedOperationException.class, () -> level.ensurePatternCapacity(801));
        assertEquals(800, level.getPatternCount());
        // A complete engine edit resolves a formerly unavailable cell without altering source authority.
        level.getBlock(121).setChunkDesc(0, 0, new ChunkDesc(2));
        assertEquals(2, level.getBlock(121).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(0x7FC, terrain.chunkWord(121, 0, 0).rawWord());
    }

    @Test
    void paletteLinesAndCameraBoundsUseTheSuppliedLoadFacts() {
        Fixture f = new Fixture();
        word(f.palette, 2, 0x000E);
        word(f.palette, 32 + 4, 0x00E0);
        word(f.palette, 64 + 6, 0x0E00);
        word(f.palette, 96 + 8, 0x0EEE);
        word(f.palette, 64, 0x000E);
        Level level = f.level();
        assertEquals(4, level.getPaletteCount());
        assertRgb(level.getPalette(0).getColor(1), 255, 0, 0);
        assertRgb(level.getPalette(1).getColor(2), 0, 255, 0);
        assertRgb(level.getPalette(2).getColor(3), 0, 0, 255);
        assertRgb(level.getPalette(3).getColor(4), 255, 255, 255);
        assertEquals(2, level.getBackdropPaletteLine());
        assertRgb(level.getBackdropColor(), 255, 0, 0);
        assertEquals(0, level.getMinX());
        assertEquals(0x2897, level.getMaxX());
        assertEquals(0, level.getMinY());
        assertEquals(0x710, level.getMaxY());
        assertNotEquals(level.getLayerWidthBlocks(0) * 256, level.getMaxX());
        assertEquals(0, level.getZoneIndex());
        assertTrue(level.getObjects().isEmpty());
        assertTrue(level.getRings().isEmpty());
        assertNull(level.getRingSpriteSheet());
    }

    @Test
    void missingPaletteWordsAndMissingAuthenticatedAssetsAreRejected() {
        Fixture f = new Fixture();
        var bounds = new CdR11aLoadPlan.Bounds(4, 0, 0x2897, 0, 0x710, 0x60);
        assertThrows(IllegalArgumentException.class,
                () -> new SonicCdLevelGeometry(f.terrain(), CdAssetBytes.copyOf(new byte[126]), bounds));
        assertThrows(NullPointerException.class, () -> new SonicCdLevel(null));
    }

    @Test
    void mutableEngineGraphIsPrivateToEachSessionAndDetachedFromNativeBytes() {
        Fixture f = new Fixture();
        var terrain = f.terrain();
        var first = f.level(terrain);
        var second = f.level(terrain);
        first.getPattern(0).setPixel(0, 0, (byte) 9);
        first.getChunk(0).getPatternDesc(0, 0).set(7);
        first.getBlock(1).getChunkDesc(0, 0).set(5);
        first.getBlock(0).getChunkDesc(0, 0).set(6);
        first.getSolidTile(0).heights[0] = -16;
        first.getSolidTile(0).widths[0] = -1;
        first.getPalette(0).getColor(0).r = (byte) 255;
        first.getMap().setValue(0, 0, 0, (byte) 1);
        assertEquals(0, second.getPattern(0).getPixel(0, 0));
        assertEquals(0, second.getChunk(0).getPatternDesc(0, 0).get());
        assertEquals(0, second.getBlock(1).getChunkDesc(0, 0).get());
        assertEquals(0, second.getBlock(0).getChunkDesc(0, 0).get());
        assertEquals(0, first.getBlock(0).getChunkDesc(1, 0).get());
        assertEquals(0, second.getSolidTile(0).getHeightAt((byte) 0));
        assertEquals(0, second.getSolidTile(0).getWidthAt((byte) 0));
        assertEquals(0, second.getPalette(0).getColor(0).r);
        assertEquals(0, second.getMap().getValue(0, 0, 0));
        assertEquals(0, terrain.pixel(0, 0, 0));
        assertEquals(0, terrain.blockWord(0, 0, 0).rawWord());
        assertEquals(0, terrain.chunkWord(1, 0, 0).rawWord());
        assertEquals(0, terrain.layout().cell(FOREGROUND, 0, 0).rawByte());
        assertEquals(0, terrain.collision().height(0, 0));
        assertEquals(0, f.palette[0]);
        assertNotSame(first.blocksReference(), second.blocksReference());
        assertNotSame(first.chunksReference(), second.chunksReference());
    }

    @Test
    void engineMutationSurfacePreservesCapturedGeometryAndSupportsReferenceRestore() {
        Fixture f = new Fixture();
        f.fg[2] = 1;
        var level = f.level();
        level.bumpEpoch(); // Same boundary used by LevelRewindSnapshotAdapter.capture().
        var snapshot = new LevelSnapshot(level.currentEpoch(), level.blocksReference(), level.chunksReference(),
                level.getMap().getData(), 0);
        var surface = new DirectLevelMutationSurface(level);
        int[] blockState = level.getBlock(1).saveState();
        blockState[0] = 7;
        int[] chunkState = level.getChunk(7).saveState();
        chunkState[0] = 9;
        chunkState[4] = 5;
        surface.restoreBlockState(1, blockState);
        surface.restoreChunkState(7, chunkState);
        surface.setBlockInMap(0, 0, 0, 2);
        assertEquals(0, snapshot.blocks()[1].getChunkDesc(0, 0).get());
        assertEquals(0, snapshot.chunks()[7].getPatternDesc(0, 0).get());
        assertEquals(1, snapshot.mapData()[0]);
        assertEquals(7, level.getBlock(1).getChunkDesc(0, 0).get());
        assertEquals(9, level.getChunk(7).getPatternDesc(0, 0).get());
        assertEquals(5, level.getChunk(7).getSolidTileIndex());
        assertEquals(2, level.getMap().getValue(0, 0, 0));
        level.replaceBlocks(snapshot.blocks());
        level.replaceChunks(snapshot.chunks());
        level.getMap().restoreData(snapshot.mapData());
        level.bumpEpoch();
        assertEquals(0, level.getBlock(1).getChunkDesc(0, 0).get());
        assertEquals(0, level.getChunk(7).getPatternDesc(0, 0).get());
        assertEquals(1, level.getMap().getValue(0, 0, 0));
        surface.setBlockInMap(0, 0, 0, 3);
        assertEquals(1, snapshot.mapData()[0]);
    }

    @Test
    void existingCpuRasterizerMatchesNativePatternSelectionAndBothLevelsOfFlips() {
        for (int flips = 0; flips < 4; flips++) {
            Fixture f = new Fixture();
            f.fg[2] = 1;
            word(f.chunks, 0, 803 | (flips << 11) | 0x6000);
            for (int half = 0; half < 4; half++) {
                word(f.blocks, 803 * 8 + half * 2, 799 | (half << 11) | (half << 13) | 0x8000);
            }
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x += 2) {
                    int left = (x + y * 3) % 15 + 1;
                    int right = (x + 1 + y * 3) % 15 + 1;
                    f.patterns[799 * 32 + y * 4 + x / 2] = (byte) ((left << 4) | right);
                }
            }
            for (int color = 0; color < 64; color++) {
                word(f.palette, color * 2, ((color & 7) << 1) | (((color >> 2) & 7) << 5)
                        | (((color >> 3) & 7) << 9));
            }
            var terrain = f.terrain();
            Level level = f.level(terrain);
            int[] actual = PlaneRasterizer.rasterize(level, (layer, index, layoutX) -> level.getPattern(index),
                    0, 0, 0, 16, 16, 0xFF102030);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(nativePixel(terrain, f.palette, 1, 0, 0, x, y, 0xFF102030),
                            actual[y * 16 + x], "flips=" + flips + " pixel=" + x + "," + y);
                }
            }
        }
    }

    static int nativePixel(CdR11aTerrain terrain, byte[] palette, int nativeChunk, int blockX, int blockY,
                           int x, int y, int backdrop) {
        int cell = terrain.chunkWord(nativeChunk, blockX, blockY).rawWord();
        int localX = (cell & 0x800) == 0 ? x : 15 - x;
        int localY = (cell & 0x1000) == 0 ? y : 15 - y;
        int pattern = terrain.blockWord(cell & 0x3FF, localX / 8, localY / 8).rawWord();
        int px = (pattern & 0x800) == 0 ? localX % 8 : 7 - localX % 8;
        int py = (pattern & 0x1000) == 0 ? localY % 8 : 7 - localY % 8;
        int color = terrain.pixel(pattern & 0x7FF, px, py);
        if (color == 0) { return backdrop; }
        int at = (((pattern >>> 13) & 3) * 16 + color) * 2;
        int cram = (Byte.toUnsignedInt(palette[at]) << 8) | Byte.toUnsignedInt(palette[at + 1]);
        int r = (((cram >>> 1) & 7) * 255 + 3) / 7;
        int g = (((cram >>> 5) & 7) * 255 + 3) / 7;
        int b = (((cram >>> 9) & 7) * 255 + 3) / 7;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void assertRgb(Palette.Color color, int r, int g, int b) {
        assertEquals(r, Byte.toUnsignedInt(color.r));
        assertEquals(g, Byte.toUnsignedInt(color.g));
        assertEquals(b, Byte.toUnsignedInt(color.b));
    }

    static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    private static final class Fixture {
        final byte[] blocks = new byte[6432];
        final byte[] patterns = new byte[25600];
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];
        final byte[] palette = new byte[128];

        Fixture() {
            fg[0] = 41;
            fg[1] = 7;
            bg[0] = 5;
            bg[1] = 7;
        }

        CdR11aTerrain terrain() {
            return CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        }

        SonicCdLevelGeometry level() { return level(terrain()); }

        SonicCdLevelGeometry level(CdR11aTerrain terrain) {
            return new SonicCdLevelGeometry(terrain, CdAssetBytes.copyOf(palette),
                    new CdR11aLoadPlan.Bounds(4, 0, 0x2897, 0, 0x710, 0x60));
        }
    }
}
