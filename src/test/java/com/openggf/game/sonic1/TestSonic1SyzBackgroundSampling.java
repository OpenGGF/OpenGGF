package com.openggf.game.sonic1;

import com.openggf.game.sonic1.scroll.SwScrlSyz;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelGeometry;
import com.openggf.level.LevelTilemapManager;
import com.openggf.level.render.PlaneRasterizer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1SyzBackgroundSampling {
    // Sonic1's detached decoder indexes ROM zone order, whereas the registry
    // presents SYZ as the third zone in gameplay progression.
    private static final int SYZ_LEVEL_INDEX = 0x80 + Sonic1Constants.ZONE_SYZ * 3;

    @Test
    void foregroundGeometryRetainsItsLogicalHeightWhileBackgroundHasNativeRamRows() throws Exception {
        Sonic1Level level = new Sonic1(TestEnvironment.currentRom()).buildDetachedLevel(0x80);
        assertEquals(Sonic1Constants.ZONE_GHZ, level.getZoneIndex());
        assertEquals(8, level.getMap().getHeight(), "the stored map has the native background capacity");
        assertEquals(5, level.getLayerHeightBlocks(0), "GHZ1 retains its previous logical foreground bounds");
        assertEquals(8, level.getLayerHeightBlocks(1));

        LevelGeometry geometry = LevelGeometry.forLevel(level);
        assertEquals(5 * 256, geometry.fgHeightPx(), "geometry consumers see the logical foreground extent");
        assertEquals(8 * 256, geometry.bgHeightPx(), "background consumers can sample the cleared native rows");
    }

    @Test
    void shortBackgroundLayoutsKeepNativeEmptyRowsBelowTheSuppliedData() throws Exception {
        Sonic1 game = new Sonic1(TestEnvironment.currentRom());
        for (int act = 0; act < 3; act++) {
            Sonic1Level level = game.buildDetachedLevel(SYZ_LEVEL_INDEX + act);
            assertEquals(Sonic1Constants.ZONE_SYZ, level.getZoneIndex(), "the source must be the native SYZ zone");
            assertEquals(5 + act, level.getLayerHeightBlocks(0));
            assertEquals(8, level.getLayerHeightBlocks(1));
            assertTrue((level.getMap().getValue(1, 0, 0) & 0x7F) != 0);
            assertTrue((level.getMap().getValue(1, 0, 1) & 0x7F) != 0);
            for (int y = 2; y < level.getMap().getHeight(); y++) {
                for (int x = 0; x < level.getMap().getWidth(); x++) {
                    assertEquals(0, level.getMap().getValue(1, x, y), "LevelLayoutLoad leaves later RAM rows zero");
                }
            }
            // The two-block horizontal picture still repeats across wide views.
            assertEquals(level.getMap().getValue(1, 0, 1), level.getMap().getValue(1, 28, 1));
        }
    }

    @Test
    void actThreeLowestCameraUsesTheEmptyTailRatherThanTopClouds() throws Exception {
        Sonic1Level level = new Sonic1(TestEnvironment.currentRom()).buildDetachedLevel(SYZ_LEVEL_INDEX + 2);
        assertEquals(Sonic1Constants.ZONE_SYZ, level.getZoneIndex(), "the source must be the native SYZ zone");
        SwScrlSyz scroll = new SwScrlSyz();
        scroll.update(new int[224], 0, level.getMaxY(), 0, 2);
        int bgY = scroll.getVscrollFactorBG();
        assertEquals(0x127, bgY, "BgScroll_SYZ REV01 includes its one-pixel startup adjustment");
        assertEquals(518, bgY + 223, "the lowest view exposes seven rows after the 512px supplied layout");

        PlaneRasterizer.TileSource patterns = (layer, index, x) -> level.getPattern(index);
        int[] tail = PlaneRasterizer.rasterize(level, patterns, 1, 0, 512, 320, 7, 0xFF000000);
        int[] clouds = PlaneRasterizer.rasterize(level, patterns, 1, 0, 0, 320, 7, 0xFF000000);
        assertFalse(Arrays.equals(tail, clouds), "the bottom rows must not repeat the top clouds");

        LevelGeometry geometry = LevelGeometry.forLevel(level);
        LevelTilemapManager tilemap = new LevelTilemapManager(geometry, mock(GraphicsManager.class), null);
        tilemap.ensureBackgroundTilemapData((layer, x, y) -> {
            int column = Math.floorMod(x / 256, level.getMap().getWidth());
            int row = Math.floorMod(y / 256, level.getMap().getHeight());
            return level.getBlock(level.getMap().getValue(layer, column, row) & 0x7F);
        }, new Sonic1ZoneFeatureProvider(), 2, null, false);
        assertEquals(0, tilemap.getBackgroundVdpWrapHeightTiles(), "the 512px art must not use a 256px texture wrap");
        byte[] data = tilemap.getBackgroundTilemapData();
        int stride = tilemap.getBackgroundTilemapWidthTiles() * 4;
        assertFalse(Arrays.equals(Arrays.copyOfRange(data, 0, stride),
                Arrays.copyOfRange(data, 64 * stride, 65 * stride)),
                "the uploaded tilemap's Y=$200 row differs from its top row");
    }
}
