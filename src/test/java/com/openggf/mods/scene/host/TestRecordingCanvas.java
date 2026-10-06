package com.openggf.mods.scene.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the recording canvas turns API drawing calls into. */
class TestRecordingCanvas {
    private static SceneImage image(int width, int height) {
        return new SceneImage(width, height, new int[width * height]);
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

}
