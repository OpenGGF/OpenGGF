package com.openggf.mods.ui;

import com.openggf.mods.scene.*;
import java.util.Objects;
import java.util.Optional;

/**
 * A translated, faded view valid only inside one draw callback. Use try-with-resources
 * to restore the previous clip even when drawing throws. Nested transforms compose;
 * clear still covers the full target viewport and ignores translation and clipping.
 */
@com.openggf.game.ModApi
public final class SceneCanvasTransform implements SceneCanvas, AutoCloseable {
    private final SceneCanvas target;
    private final int dx, dy;
    private final float alpha;
    private final Optional<SceneClip> prior;
    private boolean closed;

    public SceneCanvasTransform(SceneCanvas target, int dx, int dy, float alpha) {
        Objects.requireNonNull(target, "target");
        if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Opacity must be finite and within 0..1");
        if (target instanceof SceneCanvasTransform parent) {
            parent.open();
            this.target = parent.target;
            this.dx = Math.addExact(parent.dx, dx); this.dy = Math.addExact(parent.dy, dy);
            this.alpha = parent.alpha * alpha;
        } else {
            this.target = target; this.dx = dx; this.dy = dy; this.alpha = alpha;
        }
        prior = Objects.requireNonNull(this.target.clipBounds(), "clip state");
    }

    private void open() { if (closed) throw new IllegalStateException("Canvas transform is closed"); }
    private int x(int x) { open(); return Math.addExact(x, dx); }
    private int y(int y) { open(); return Math.addExact(y, dy); }
    private float x(float x) { open(); return coordinate((double)x + dx); }
    private float y(float y) { open(); return coordinate((double)y + dy); }
    private static float coordinate(double value) {
        if (!Double.isFinite(value) || value < -Integer.MAX_VALUE || value >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Canvas coordinate exceeds the finite pixel range");
        return (float)value;
    }
    private int fade(int argb) { return (Math.round((argb >>> 24) * alpha) << 24) | (argb & 0xFFFFFF); }
    private SceneDraw fade(SceneDraw style) {
        Objects.requireNonNull(style, "style");
        if (!Float.isFinite(style.scaleX()) || !Float.isFinite(style.scaleY())
                || style.scaleX() <= 0 || style.scaleY() <= 0)
            throw new IllegalArgumentException("Image scale must be finite and positive");
        return style.withAlpha(alpha);
    }
    private static void rect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException("Negative rectangle size");
        Math.addExact(x, w); Math.addExact(y, h);
    }
    private void restore(Optional<SceneClip> clip) {
        if (clip.isPresent()) { SceneClip c = clip.get(); target.clip(c.x(), c.y(), c.width(), c.height()); }
        else target.unclip();
    }

    @Override public int width() { open(); return target.width(); }
    @Override public int height() { open(); return target.height(); }
    @Override public void clear(int rgb) {
        open();
        if (alpha == 1) { target.clear(rgb); return; }
        var current = target.clipBounds();
        target.unclip();
        try { target.fill(0, 0, target.width(), target.height(), fade(0xFF000000 | (rgb & 0xFFFFFF))); }
        finally { restore(current); }
    }
    @Override public void fill(int x, int y, int w, int h, int argb) {
        int tx = x(x), ty = y(y); rect(tx, ty, w, h); target.fill(tx, ty, w, h, fade(argb));
    }
    @Override public void draw(SceneImage image, float x, float y) { draw(image, x, y, SceneDraw.plain()); }
    @Override public void draw(SceneImage image, float x, float y, SceneDraw style) {
        target.draw(image, x(x), y(y), fade(style));
    }
    @Override public void draw(SceneSprite sprite, float x, float y, SceneDraw style) {
        target.draw(sprite, x(x), y(y), fade(style));
    }
    @Override public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float x, float y,
                                     float w, float h, SceneDraw style) {
        float tx = x(x), ty = y(y);
        if (w < 0 || h < 0) throw new IllegalArgumentException("Negative image region size");
        coordinate(tx + (double)w); coordinate(ty + (double)h); rect(sx, sy, sw, sh);
        target.drawRegion(image, sx, sy, sw, sh, tx, ty, w, h, fade(style));
    }
    @Override public void text(String text, int x, int y, int argb) { target.text(text, x(x), y(y), fade(argb)); }
    @Override public int textWidth(String text) { open(); return target.textWidth(text); }
    @Override public void clip(int x, int y, int w, int h) {
        int tx = x(x), ty = y(y); rect(tx, ty, w, h); target.clip(tx, ty, w, h);
    }
    @Override public void unclip() { open(); target.unclip(); }
    @Override public Optional<SceneClip> clipBounds() {
        open(); return target.clipBounds().map(c -> new SceneClip(Math.subtractExact(c.x(),dx),
                Math.subtractExact(c.y(),dy), c.width(), c.height()));
    }
    @Override public void close() {
        if (!closed) { restore(prior); closed = true; }
    }
}
