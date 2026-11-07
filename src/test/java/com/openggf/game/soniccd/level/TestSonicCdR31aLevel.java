package com.openggf.game.soniccd.level;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original-media detached conversion, without programme, current-art or World admission. */
class TestSonicCdR31aLevel {
    @Test void originalCczGeometryUsesNativeZoneBoundsPaletteAndEveryLayoutCell() throws Exception {
        var assets = TestCdR31aAssets.actual().assets();
        var level = new SonicCdR31aLevel(assets);
        assertSame(assets, level.assets());
        assertEquals(1, level.getZoneIndex());
        assertEquals(694, level.getPatternCount());
        assertEquals(656, level.getChunkCount());
        assertEquals(108, level.getBlockCount()); // 107 native chunks plus blank layout0.
        assertEquals(0, level.getMinX()); assertEquals(0x2E97, level.getMaxX());
        assertEquals(0, level.getMinY()); assertEquals(0x510, level.getMaxY());
        assertEquals(new CdR11bLoadPlan.InitialCentre(0x60, 0x3EC), level.initialCentre());
        assertSame(assets.placements(), level.nativePlacements());
        assertEquals(2, level.getBackdropPaletteLine());
        for (int line = 0; line < 4; line++) {
            var expected = new Palette();
            expected.fromSegaFormat((line == 0 ? assets.view(CdR31aAssets.Asset.PAL_SONIC)
                    : assets.view(CdR31aAssets.Asset.PAL_LEVEL).slice((line - 1) * 32, 32)).copy());
            assertTrue(expected.dataEquals(level.getPalette(line)), "source palette line " + line);
        }
        for (var plane : CdStageTerrain.Plane.values()) {
            int layer = plane == CdStageTerrain.Plane.FOREGROUND ? 0 : 1;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++)
                assertEquals(assets.terrain().layout().nativeCell(plane, x, y).chunkId(),
                        level.getMap().getValue(layer, x, y) & 255);
        }
        assertTrue(level.getObjects().isEmpty()); assertTrue(level.getRings().isEmpty());
    }

    @Test void eachViewIsMutableAndDetachedWithoutInventingMissingCurrentArtOrCollision() throws Exception {
        var assets = TestCdR31aAssets.actual().assets();
        var one = new SonicCdR31aLevel(assets); var two = new SonicCdR31aLevel(assets);
        var original = assets.terrain().patterns().copy();
        int expected = original[32] >>> 4 & 15;
        assertEquals(expected, one.getPattern(1).getPixel(0, 0));
        one.getPattern(1).setPixel(0, 0, (byte) (expected ^ 15));
        assertEquals(expected, two.getPattern(1).getPixel(0, 0));
        assertArrayEquals(original, assets.terrain().patterns().copy());
        one.getPalette(2).setColor(0, new Palette.Color((byte) 1, (byte) 2, (byte) 3));
        assertFalse(one.getPalette(2).dataEquals(two.getPalette(2)));
        assertThrows(UnsupportedOperationException.class, () -> two.ensurePatternCapacity(695));
        assertThrows(UnsupportedOperationException.class, () -> two.resolveCollisionBlockIndex(0, 0, 0));
        assertThrows(NullPointerException.class, () -> new SonicCdR31aLevel(null));
    }
}
