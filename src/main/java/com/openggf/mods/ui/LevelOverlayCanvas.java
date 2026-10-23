package com.openggf.mods.ui;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import java.util.Objects;

/** Queues immutable screen-space rectangles without changing the level camera. Use during drawing. */
@com.openggf.game.ModApi
public final class LevelOverlayCanvas implements PixelCanvas {
    private final GraphicsManager graphics;
    private final int width;
    private final int height;
    public LevelOverlayCanvas(GraphicsManager graphics, int width, int height) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096)
            throw new IllegalArgumentException("Invalid logical viewport");
        this.width = width; this.height = height;
    }
    public int width() { return width; }
    public int height() { return height; }
    /**
     * Screen-space origin of the native HUD's 320-pixel frame at this canvas width,
     * honouring the live HUD anchor. Add native row offsets to align overlay text.
     * Without an installed HUD the frame is centered; bonus-stage HUDs use zero.
     */
    public int hudOriginX() {
        return com.openggf.level.objects.HudProfileAccess.nativeOriginX(graphics, width);
    }
    public void fill(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        int left = Math.max(0, x), top = Math.max(0, y);
        int right = (int) Math.min(width, (long) x + w);
        int bottom = (int) Math.min(height, (long) y + h);
        if (right > left && bottom > top) graphics.registerCommand(
                GLCommand.screenSpaceRect(graphics, height, left, top, right - left, bottom - top, argb));
    }
}
