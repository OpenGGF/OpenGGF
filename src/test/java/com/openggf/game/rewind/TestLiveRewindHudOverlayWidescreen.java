package com.openggf.game.rewind;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLiveRewindHudOverlayWidescreen {

    @Test
    void renderCentersNativeHudAtEveryLiveWidth() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay overlay = new LiveRewindHudOverlay(() -> "REWIND 12");

        for (int width : new int[] {320, 352, 400}) {
            text.draws.clear();
            overlay.render(text, null, width);
            int expectedX = (width - 320) / 2 + 8;
            assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("LIVE REWIND")
                    && draw.x == expectedX + 22));
            assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("FRAME 12")
                    && draw.x == expectedX + 8));
        }
    }

    private static final class RecordingTextRenderer extends PixelFontTextRenderer {
        private final List<Draw> draws = new ArrayList<>();

        @Override public void beginBatch() {}
        @Override public void endBatch() {}
        @Override public void drawShadowedText(String text, int x, int y,
                                                DebugColor color, float scale) {
            draws.add(new Draw(text, x, y, color));
        }
    }

    private record Draw(String text, int x, int y, DebugColor color) {}
}
