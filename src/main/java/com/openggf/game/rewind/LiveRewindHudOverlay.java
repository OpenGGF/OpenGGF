package com.openggf.game.rewind;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Small lower-left HUD for optional live gameplay rewind.
 */
final class LiveRewindHudOverlay {

    private static final float SCALE = 0.5f;
    private static final int X = 4;
    private static final int TOP_Y = 184;
    private static final int LINE_HEIGHT = 6;

    private final Supplier<String> statusSupplier;

    LiveRewindHudOverlay(Supplier<String> statusSupplier) {
        this.statusSupplier = Objects.requireNonNull(statusSupplier, "statusSupplier");
    }

    public void render(PixelFontTextRenderer text, int viewportWidth) {
        String status = statusSupplier.get();
        if (status == null || status.isBlank()) {
            return;
        }
        int x = Math.max(0, (viewportWidth - 320) / 2) + X;
        text.beginBatch();
        try {
            text.drawShadowedText("LIVE REWIND", x, TOP_Y, DebugColor.LIGHT_GRAY, SCALE);
            text.drawShadowedText(status, x, TOP_Y + LINE_HEIGHT, DebugColor.CYAN, SCALE);
        } finally {
            text.endBatch();
        }
    }
}
