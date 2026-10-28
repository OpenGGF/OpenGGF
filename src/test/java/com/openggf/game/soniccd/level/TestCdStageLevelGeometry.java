package com.openggf.game.soniccd.level;

import com.openggf.level.ChunkDesc;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed native-format conversion vectors, not an authenticated or running Past stage. */
class TestCdStageLevelGeometry {
    @Test void pastLastPatternBlockAndChunkKeepTheirActualNestingAndRawAttributes() {
        var f = new Fixture(); f.fg[2] = (byte) (106 | 0x80);
        word(f.chunks, 105 * 512, 0xFA93); // Block659, XY flips, both solidity bits.
        word(f.blocks, 659 * 8, 0xFAE9); // Pattern745, XY flips, palette3, priority.
        f.patterns[745 * 32] = (byte) 0xA3; f.ids[659] = (byte) 255;
        f.angles[255] = (byte) 0xE1; f.heights[255 * 16] = (byte) 0xF0;
        f.widths[255 * 16 + 15] = (byte) 0xFF;
        var terrain = f.terrain(); var level = f.level(terrain);
        assertEquals(746, level.getPatternCount()); assertEquals(660, level.getChunkCount());
        assertEquals(107, level.getBlockCount()); assertEquals(256, level.getSolidTileCount());
        assertEquals(106, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        assertEquals(0xEA, terrain.layout().cell(CdStageTerrain.Plane.FOREGROUND, 0, 0).rawByte());
        var block = level.getBlock(106); assertEquals(0xFE93, block.getChunkDesc(0, 0).get());
        assertEquals(659, block.getChunkDesc(0, 0).getChunkIndex());
        var chunk = level.getChunk(659); assertEquals(0xFAE9, chunk.getPatternDesc(0, 0).get());
        assertEquals(745, chunk.getPatternDesc(0, 0).getPatternIndex());
        assertEquals(3, chunk.getPatternDesc(0, 0).getPaletteIndex());
        assertTrue(chunk.getPatternDesc(0, 0).getPriority());
        assertTrue(chunk.getPatternDesc(0, 0).getHFlip()); assertTrue(chunk.getPatternDesc(0, 0).getVFlip());
        assertEquals(10, level.getPattern(745).getPixel(0, 0)); assertEquals(3, level.getPattern(745).getPixel(1, 0));
        assertEquals(255, chunk.getSolidTileIndex()); assertEquals(255, chunk.getSolidTileAltIndex());
        assertEquals(0xE1, Byte.toUnsignedInt(level.getSolidTile(255).getAngle()));
        assertEquals(-16, level.getSolidTile(255).getHeightAt((byte) 0));
        assertEquals(-1, level.getSolidTile(255).getWidthAt((byte) 15));
    }

    @Test void pastPlaneDimensionsKeepEveryClearedNativeCellWithoutRepeatingData() {
        var f = new Fixture(); f.fg[2] = (byte) 0x81; f.fg[2 + 3 * 42 + 41] = 106;
        f.bg[2] = 2; f.bg[2 + 10 + 9] = 105; var terrain = f.terrain(); var level = f.level(terrain);
        assertEquals(42, level.getLayerWidthBlocks(0)); assertEquals(4, level.getLayerHeightBlocks(0));
        assertEquals(10, level.getLayerWidthBlocks(1)); assertEquals(2, level.getLayerHeightBlocks(1));
        assertEquals(64, level.getMap().getWidth()); assertEquals(16, level.getMap().getHeight());
        for (int plane = 0; plane < 2; plane++) for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
            int expected = plane == 0 ? (x == 0 && y == 0 ? 1 : x == 41 && y == 3 ? 106 : 0)
                    : (x == 0 && y == 0 ? 2 : x == 9 && y == 1 ? 105 : 0);
            assertEquals(expected, Byte.toUnsignedInt(level.getMap().getValue(plane, x, y)));
        }
        assertEquals(0x81, terrain.layout().nativeCell(CdStageTerrain.Plane.FOREGROUND, 0, 0).rawByte());
    }

    @Test void independentMutableEngineViewsDoNotModifyTheirSharedNativeSource() {
        var f = new Fixture(); var terrain = f.terrain(); var one = f.level(terrain); var two = f.level(terrain);
        assertNotSame(one.getBlock(0).getChunkDesc(0, 0), one.getBlock(0).getChunkDesc(1, 0));
        assertNotSame(one.getBlock(0).getChunkDesc(0, 0), two.getBlock(0).getChunkDesc(0, 0));
        one.getBlock(0).setChunkDesc(0, 0, new ChunkDesc(7));
        byte[] changed = new byte[32]; Arrays.fill(changed, (byte) 0x55); one.getPattern(0).fromSegaFormat(changed);
        assertEquals(0, two.getBlock(0).getChunkDesc(0, 0).get());
        assertEquals(0, two.getPattern(0).getPixel(0, 0)); assertEquals(0, terrain.pixel(0, 0, 0));
        assertEquals(0, terrain.chunkWord(1, 0, 0).rawWord());
    }

    @Test void unreferencedUnavailableDrawingCellsRemainExplicitWhenLaterRequested() {
        var f = new Fixture(); word(f.chunks, 105 * 512, 660); var level = f.level(f.terrain());
        var failure = assertThrows(UnsupportedOperationException.class, () -> level.getBlock(106).getChunkDesc(0, 0));
        assertTrue(failure.getMessage().contains("unloaded drawing block 660"));
        assertThrows(UnsupportedOperationException.class, level.getBlock(106)::saveState);
        assertEquals(0, level.getBlock(1).getChunkDesc(0, 0).get());
    }

    @Test void aStaticConversionDoesNotInventRuntimePatternsOrNativeCollision() {
        var f = new Fixture(); var level = f.level(f.terrain());
        level.ensurePatternCapacity(746); assertEquals(746, level.getPatternCount());
        assertThrows(UnsupportedOperationException.class, () -> level.ensurePatternCapacity(747));
        assertThrows(UnsupportedOperationException.class, () -> level.resolveCollisionBlockIndex(1, 0, 0));
        assertEquals(2, level.getBackdropPaletteLine()); assertEquals(256, level.getBlockPixelSize());
        assertEquals(16, level.getChunksPerBlockSide());
    }

    @Test void nativeFlipSolidityAndIgnoredHighBitsConvertIndependentlyOfStageShape() {
        var f = new Fixture(); f.fg[2] = 1;
        int[] expected = {0x0001, 0x5001, 0xA001, 0xF001, 0x0401, 0x5401, 0xA401, 0xF401,
                0x0801, 0x5801, 0xA801, 0xF801, 0x0C01, 0x5C01, 0xAC01, 0xFC01};
        for (int flips = 0; flips < 4; flips++) for (int solidity = 0; solidity < 4; solidity++)
            word(f.chunks, (flips * 4 + solidity) * 2, 1 | (flips << 11) | (solidity << 13) | 0x8000);
        var level = f.level(f.terrain());
        for (int i = 0; i < 16; i++) assertEquals(expected[i], level.getBlock(1).getChunkDesc(i, 0).get());
    }

    @Test void explicitTerrainAndWholePaletteAreRequiredWithoutAProfileFallback() {
        var f = new Fixture();
        assertThrows(NullPointerException.class, () -> new SonicCdLevelGeometry(null, f.palette(), 0, 0, 1, 0, 1));
        assertThrows(NullPointerException.class, () -> new SonicCdLevelGeometry(f.terrain(), null, 0, 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SonicCdLevelGeometry(f.terrain(), CdAssetBytes.copyOf(new byte[126]), 0, 0, 1, 0, 1));
    }

    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static final class Fixture {
        final byte[] blocks = new byte[5280], patterns = new byte[23872], chunks = new byte[54272];
        final byte[] fg = new byte[170], bg = new byte[22], ids = new byte[800];
        final byte[] angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Fixture() { fg[0] = 41; fg[1] = 3; bg[0] = 9; bg[1] = 1; }
        CdStageTerrain terrain() { return CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PAST,
                blocks, patterns, chunks, fg, bg, ids, angles, heights, widths); }
        CdAssetBytes palette() { return CdAssetBytes.copyOf(new byte[128]); }
        SonicCdLevelGeometry level(CdStageTerrain terrain) { return new SonicCdLevelGeometry(terrain, palette(), 0, 0, 0x2897, 0, 0x310); }
    }
}
