package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Original-media static projection only; no FULL, gameplay, collision solver or pixel admission. */
class TestCdR13FutureLevelGeometry {
    @Test void actualGoodFutureProjectsItsFiniteGeometryAndKeepsMissingArtExplicit() throws Exception {
        verify(TestCdR13FutureAssets.retail().good(), 760, 788, "R13C");
    }
    @Test void actualBadFutureProjectsItsFiniteGeometryAndKeepsMissingArtExplicit() throws Exception {
        verify(TestCdR13FutureAssets.retail().bad(), 756, 814, "R13D");
    }
    private static void verify(CdR13FutureAssets assets, int nativeBlockCount, int patterns, String label) {
        var b = assets.bounds();
        var geometry = new SonicCdLevelGeometry(assets.terrain(), assets.initialPalette(), 0,
                b.left(), b.right(), b.top(), b.bottom());
        assertEquals(patterns, geometry.getPatternCount());
        assertEquals(nativeBlockCount, geometry.getChunkCount());
        assertEquals(107, geometry.getBlockCount()); // 106 original native chunks plus blank layout0.
        assertEquals(15, geometry.getLayerWidthBlocks(0));
        assertEquals(4, geometry.getLayerHeightBlocks(0));
        assertEquals(10, geometry.getLayerWidthBlocks(1));
        assertEquals(2, geometry.getLayerHeightBlocks(1));
        assertEquals(b.left(), geometry.getMinX()); assertEquals(b.right(), geometry.getMaxX());
        assertEquals(b.top(), geometry.getMinY()); assertEquals(b.bottom(), geometry.getMaxY());
        assertEquals(2, geometry.getBackdropPaletteLine());
        assertTrue(geometry.getObjects().isEmpty()); assertTrue(geometry.getRings().isEmpty());
        assertDoesNotThrow(() -> geometry.ensurePatternCapacity(patterns));
        assertEquals(label + " static view cannot invent unloaded pattern/resident data",
                assertThrows(UnsupportedOperationException.class,
                        () -> geometry.ensurePatternCapacity(patterns + 1)).getMessage());
        assertEquals(label + " static view does not implement native collision/loop lookup",
                assertThrows(UnsupportedOperationException.class,
                        () -> geometry.resolveCollisionBlockIndex(0, 0, 0)).getMessage());
    }
}
