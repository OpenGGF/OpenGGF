package com.openggf.game.rewind;

import com.openggf.game.presentation.TransportHud;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native-pixel transport plate above the original game's lives counter.
 */
final class LiveRewindHudOverlay {

    private final Supplier<String> statusSupplier;

    LiveRewindHudOverlay(Supplier<String> statusSupplier) {
        this.statusSupplier = Objects.requireNonNull(statusSupplier, "statusSupplier");
    }

    /** {@code graphics} draws the backing plate; null (logic-only hosts) draws the text alone. */
    public void render(PixelFontTextRenderer text, GraphicsManager graphics, int viewportWidth) {
        String status = statusSupplier.get();
        if (status == null || status.isBlank()) {
            return;
        }
        String detail = status;
        if (status.startsWith("REWIND ")) {
            String frame = status.substring(7);
            detail = (frame.length() <= 8 ? "FRAME " : "F ") + frame;
        }
        TransportHud.render(text, graphics, viewportWidth, TransportHud.Placement.ABOVE_LIVES,
                new TransportHud.Message(TransportHud.Status.REWIND, "LIVE REWIND", detail, "", -1));
    }
}
