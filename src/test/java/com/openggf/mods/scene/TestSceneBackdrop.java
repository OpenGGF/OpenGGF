package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SceneBackdrop}'s band contract and scroll arithmetic, without a ROM. */
class TestSceneBackdrop {
    private static SceneImage image(int width, int height) {
        return new SceneImage(width, height, new int[width * height]);
    }

    @Test
    void bandsMustCoverTheImageInOrder() {
        SceneImage image = image(8, 10);
        new SceneBackdrop(image, List.of(new SceneBackdrop.Band(0, 4, 0.5, 0), new SceneBackdrop.Band(4, 6, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 4, 0.5, 0))), "short of the bottom");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 4, 0.5, 0), new SceneBackdrop.Band(5, 5, 1, 0))), "gap");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 6, 0.5, 0), new SceneBackdrop.Band(4, 6, 1, 0))), "overlap");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop.Band(0, 0, 1, 0), "empty band");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop.Band(0, 1, Double.NaN, 0), "NaN");
    }

    @Test
    void bandListIsCopied() {
        List<SceneBackdrop.Band> bands = new ArrayList<>(List.of(new SceneBackdrop.Band(0, 2, 1, 0)));
        SceneBackdrop backdrop = new SceneBackdrop(image(4, 2), bands);
        bands.clear();
        assertEquals(1, backdrop.bands().size());
        assertThrows(UnsupportedOperationException.class, () -> backdrop.bands().clear());
    }

    @Test
    void columnScrollsDriftsAndWraps() {
        SceneBackdrop backdrop = new SceneBackdrop(image(512, 2), List.of(new SceneBackdrop.Band(0, 2, 0.75, 0)));
        SceneBackdrop.Band band = backdrop.bands().get(0);
        assertEquals(0, backdrop.column(band, 0, 0));
        assertEquals(75, backdrop.column(band, 100, 0));
        assertEquals(512 - 75, backdrop.column(band, -100, 0), "scrolling left wraps from the right edge");
        assertEquals(76, backdrop.column(band, 101.5, 0), "rounds down");
        assertEquals(2250 % 512, backdrop.column(band, 3000, 0), "wraps at the image width");
        SceneBackdrop.Band clouds = new SceneBackdrop.Band(0, 2, 11 / 64.0, 0.75);
        assertEquals(Math.floorMod((long) Math.floor(640 * 11 / 64.0 + 0.75 * 333), 512L),
                backdrop.column(clouds, 640, 333));
        assertEquals(Math.floorMod((long) Math.floor(0.75 * 9_000_000L), 512L),
                backdrop.column(clouds, 0, 9_000_000L), "long scenes keep exact positions");
    }

    @Test
    void drawBackdropFillsExactlyItsRectangleFromEachBandsColumn() {
        // A 16-wide image of two bands: the top one still, the bottom one at half the scroll.
        SceneBackdrop backdrop = new SceneBackdrop(image(16, 4),
                List.of(new SceneBackdrop.Band(0, 2, 0, 0), new SceneBackdrop.Band(2, 2, 0.5, 0)));
        RecordingCanvas canvas = new RecordingCanvas(40, 30, null);
        canvas.drawBackdrop(backdrop, 5, 7, 20, 3, 1, 10, 0);
        List<SceneDrawOp> ops = canvas.ops();
        // Rows 1-3 of the image: band 0 contributes row 1, band 1 rows 2-3 (screen rows 7 and 8-9).
        // Band 0 starts at column 0: 16 columns, then 4 more from column 0. Band 1 starts at column 5.
        assertEquals(4, ops.size(), ops.toString());
        assertRegion(ops.get(0), 0, 1, 16, 1, 5, 7);
        assertRegion(ops.get(1), 0, 1, 4, 1, 21, 7);
        assertRegion(ops.get(2), 5, 2, 11, 2, 5, 8);
        assertRegion(ops.get(3), 0, 2, 9, 2, 16, 8);
        for (SceneDrawOp op : ops) {
            assertTrue(op.x0() >= 5 && op.x1() <= 25, "inside the rectangle: " + op);
            assertNull(op.clip(), "the clip is left alone");
        }
        RecordingCanvas full = new RecordingCanvas(40, 30, null);
        full.drawBackdrop(backdrop, 0, 0, 0);
        assertEquals(40f, full.ops().stream().filter(op -> op.y0() == 0).mapToDouble(op -> op.x1() - op.x0()).sum(),
                0.001, "the full-screen overload covers the screen width");
    }

    private static void assertRegion(SceneDrawOp op, int sx, int sy, int sw, int sh, int dx, int dy) {
        assertEquals(sx, op.u0(), "source x " + op);
        assertEquals(sy, op.v0(), "source y " + op);
        assertEquals(sx + sw, op.u1(), "source width " + op);
        assertEquals(sy + sh, op.v1(), "source height " + op);
        assertEquals(dx, op.x0(), "screen x " + op);
        assertEquals(dy, op.y0(), "screen y " + op);
        assertEquals(dx + sw, op.x1(), "unscaled " + op);
    }

    @Test
    void levelStagesKeepTheirOwnFloor() {
        int[] floor = {100, 101, 102, 103};
        SceneLevelStage stage = new SceneLevelStage(500, 101, 4, floor);
        floor[0] = 0;
        assertEquals(100, stage.floorAt(500), "copied in");
        stage.floor()[1] = 0;
        assertEquals(101, stage.floorAt(501), "copied out");
        assertEquals(103, stage.floorAt(503));
        assertEquals(100, stage.floorAt(400), "left of the stage: its first column");
        assertEquals(103, stage.floorAt(900), "right of it: its last");
        assertThrows(IllegalArgumentException.class, () -> new SceneLevelStage(0, 0, 3, floor), "one row per column");
        assertThrows(IllegalArgumentException.class, () -> new SceneLevelStage(0, 0, 0, new int[0]), "empty");
        assertThrows(IllegalArgumentException.class, () -> new SceneLevelStage(0, 0, 1, null), "no floor");
        assertEquals(4, stage.width());
        assertEquals(new SceneLevelStage(500, 101, 4, new int[] {100, 101, 102, 103}), stage, "a value");
        assertNotEquals(new SceneLevelStage(500, 101, 4, new int[] {100, 101, 102, 104}), stage);
    }
}
