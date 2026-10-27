package com.openggf.game.rewind;

import com.openggf.game.GameServices;
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

    public void render(PixelFontTextRenderer text, int viewportWidth) {
        String status = statusSupplier.get();
        if (status == null || status.isBlank()) {
            return;
        }
        String detail = status;
        if (status.startsWith("REWIND ")) {
            String frame = status.substring(7);
            detail = (frame.length() <= 8 ? "FRAME " : "F ") + frame;
        }
        GraphicsManager graphics;
        try { graphics = GameServices.graphics(); }
        catch (IllegalStateException unavailable) { graphics = null; }
        TransportHud.render(text, graphics, viewportWidth, TransportHud.Placement.ABOVE_LIVES,
                new TransportHud.Message(TransportHud.Status.REWIND, "LIVE REWIND", detail, "", -1));
    }
}
