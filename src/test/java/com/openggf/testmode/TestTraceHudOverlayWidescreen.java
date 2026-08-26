package com.openggf.testmode;

import com.openggf.game.GameServices;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestTraceHudOverlayWidescreen {

    @AfterEach
    void resetProjectionWidth() {
        GameServices.graphics().setProjectionWidth(320);
    }

    @Test
    void ordinaryTraceHudUsesCenteredNativeOriginAtLiveWidths() {
        LiveTraceComparator comparator = mock(LiveTraceComparator.class);
        when(comparator.recentMismatches()).thenReturn(List.of());
        RecordingTextRenderer text = new RecordingTextRenderer();
        TraceHudOverlay overlay = new TraceHudOverlay(
                comparator, () -> "ENTER", () -> true, () -> "Sidekick");
        overlay.setPlaybackStatusSupplier(() -> null);

        for (int width : new int[] {320, 352, 400}) {
            GameServices.graphics().setProjectionWidth(width);
            text.draws.clear();
            overlay.render(text);
            int origin = (width - 320) / 2;
            assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("ERRORS    0")
                    && draw.x == origin + 4));
            assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("Camera: Sidekick")
                    && draw.x == origin + 276));
        }
    }

    private static final class RecordingTextRenderer extends PixelFontTextRenderer {
        private final List<Draw> draws = new ArrayList<>();

        @Override public void beginBatch() {}
        @Override public void endBatch() {}
        @Override public int measureWidth(String text, float scale) { return 40; }
        @Override public void drawShadowedText(String text, int x, int y,
                                                com.openggf.debug.DebugColor color, float scale) {
            draws.add(new Draw(text, x, y));
        }
    }

    private record Draw(String text, int x, int y) {}
}
