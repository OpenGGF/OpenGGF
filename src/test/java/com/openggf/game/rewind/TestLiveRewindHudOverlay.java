package com.openggf.game.rewind;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLiveRewindHudOverlay {

    @Test
    void renderDrawsNothingWhenStatusIsBlank() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay overlay = new LiveRewindHudOverlay(() -> "");

        overlay.render(text);

        assertTrue(text.draws.isEmpty(), "blank status should not render a live rewind HUD");
    }

    @Test
    void renderDrawsTitleAndStatusWhenStatusIsAvailable() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay overlay = new LiveRewindHudOverlay(() -> "Hold R Rewind");

        overlay.render(text);

        assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("LIVE REWIND")));
        assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("Hold R Rewind")
                && draw.color == DebugColor.CYAN));
        assertFalse(text.batchOpen, "overlay should close its text batch");
    }

    private static final class RecordingTextRenderer extends PixelFontTextRenderer {
        private final List<Draw> draws = new ArrayList<>();
        private boolean batchOpen;

        @Override
        public void beginBatch() {
            batchOpen = true;
        }

        @Override
        public void endBatch() {
            batchOpen = false;
        }

        @Override
        public void drawShadowedText(String text, int x, int y, DebugColor color, float scale) {
            draws.add(new Draw(text, x, y, color, scale));
        }
    }

    private record Draw(String text, int x, int y, DebugColor color, float scale) {}
}
