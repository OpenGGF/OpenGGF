package paradise.ui;

import com.openggf.graphics.GraphicsManager;

import java.util.Objects;

/**
 * Immutable screen-space drawing view: translation, opacity and a clip to the viewport.
 * Animated panels can slide past an edge or fade without queueing geometry outside the
 * selected logical screen. Every primitive is a {@link GolfText.Rect}.
 */
public final class GolfCanvas {
    private final GraphicsManager graphics;
    private final int width, height, dx, dy;
    private final float alpha;

    public GolfCanvas(GraphicsManager graphics, int width, int height) { this(graphics, width, height, 0, 0, 1); }
    private GolfCanvas(GraphicsManager graphics, int width, int height, int dx, int dy, float alpha) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Canvas size");
        this.width = width; this.height = height; this.dx = dx; this.dy = dy; this.alpha = Math.clamp(alpha, 0f, 1f);
    }

    public int width() { return width; }
    public int height() { return height; }
    public GolfCanvas offset(int x, int y) { return new GolfCanvas(graphics, width, height, dx + x, dy + y, alpha); }
    public GolfCanvas fade(float opacity) { return new GolfCanvas(graphics, width, height, dx, dy, alpha * opacity); }

    public void rect(int x, int y, int w, int h, int rgb, float opacity) {
        int left = Math.max(0, x + dx), top = Math.max(0, y + dy);
        int right = Math.min(width, x + dx + w), bottom = Math.min(height, y + dy + h);
        float a = alpha * opacity;
        if (right > left && bottom > top && a > 0.004f)
            graphics.registerCommand(new GolfText.Rect(left, top, right - left, bottom - top, rgb, Math.min(1f, a)));
    }
    public void rect(int x, int y, int w, int h, int rgb) { rect(x, y, w, h, rgb, 1); }

    public void frame(int x, int y, int w, int h, int rgb) {
        rect(x, y, w, 1, rgb); rect(x, y + h - 1, w, 1, rgb); rect(x, y + 1, 1, h - 2, rgb); rect(x + w - 1, y + 1, 1, h - 2, rgb);
    }

    /** A panel with a soft one-pixel bevel, the HUD's common container. */
    public void panel(int x, int y, int w, int h, int fill, float opacity, int edge) {
        rect(x + 1, y + h, w, 1, 0x06101C, 0.45f * opacity);
        rect(x, y, w, h, fill, opacity);
        rect(x, y, w, 1, edge, 0.9f * opacity);
        rect(x, y + h - 1, w, 1, 0x000000, 0.25f * opacity);
    }

    public void text(String text, int x, int y, int scale, int rgb) { text(text, x, y, scale, rgb, 1); }
    public void text(String text, int x, int y, int scale, int rgb, float opacity) {
        GolfText.glyphs(text, x, y, scale, (rx, ry, w, h) -> rect(rx, ry, w, h, rgb, opacity));
    }
    /** Drop-shadowed text keeps small HUD lettering readable over bright EHZ sky and grass. */
    public void shadowed(String text, int x, int y, int scale, int rgb) {
        text(text, x + Math.max(1, scale / 2), y + Math.max(1, scale / 2), scale, 0x0A1A24, 0.85f);
        text(text, x, y, scale, rgb);
    }
    public void centered(String text, int y, int scale, int rgb) { text(text, (width - GolfText.width(text, scale)) / 2, y, scale, rgb); }
    public void centeredShadowed(String text, int y, int scale, int rgb) {
        shadowed(text, (width - GolfText.width(text, scale)) / 2, y, scale, rgb);
    }
}
