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
        String value = status;
        if (status.startsWith("REWIND ")) {
            value = "F " + status.substring(7);
        }
        TransportHud.render(text, graphics, viewportWidth, TransportHud.Placement.ABOVE_LIVES,
                new TransportHud.Message(TransportHud.Status.REWIND, "REW", value, "", -1));
    }
}
