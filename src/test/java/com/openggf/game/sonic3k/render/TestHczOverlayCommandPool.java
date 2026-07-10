package com.openggf.game.sonic3k.render;

import com.openggf.graphics.TilemapGpuRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestHczOverlayCommandPool {
    @Test
    void twoOutstandingCommandsKeepTheirOwnViewportAndScalarSnapshots() {
        RecordingRenderer renderer = new RecordingRenderer();
        var first = new HczBgHighPriorityTileRenderer.OverlayCommand().configureCaptured(
                renderer, 320, 224, 11, 12, 13, 14, 15, 16, 17, true, 18,
                new int[]{1, 2, 3, 4});
        var second = new HczBgHighPriorityTileRenderer.OverlayCommand().configureCaptured(
                renderer, 420, 244, 22, 23, 24, 25, 26, 27, 28, false, 29,
                new int[]{5, 6, 7, 8});

        assertNotSame(first, second);
        second.execute(0, 0, 0, 0);
        first.execute(0, 0, 0, 0);

        assertEquals(List.of(
                "420,244:5,6,7,8:22.0,23.0:24,25,26,27,28:false,29.0",
                "320,224:1,2,3,4:11.0,12.0:13,14,15,16,17:true,18.0"), renderer.calls);
        var reused = HczBgHighPriorityTileRenderer.acquireCaptured(
                renderer, new int[]{9, 10, 11, 12}, 33);
        assertTrue(reused == first || reused == second);
        reused.discard();
    }

    private static final class RecordingRenderer extends TilemapGpuRenderer {
        private final List<String> calls = new ArrayList<>();
        @Override public void render(Layer layer, int ww, int wh, int vx, int vy, int vw, int vh,
                float ox, float oy, int aw, int ah, int at, int pt, int upt, int pp,
                boolean wy, boolean mask, boolean uw, float water) {
            calls.add(ww + "," + wh + ":" + vx + "," + vy + "," + vw + "," + vh
                    + ":" + ox + "," + oy + ":" + aw + "," + ah + "," + at + "," + pt
                    + "," + upt + ":" + uw + "," + water);
        }
    }
}
