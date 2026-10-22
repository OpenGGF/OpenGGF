package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneClip;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

import java.util.Optional;

/**
 * A scene canvas for views drawn over a running level (the lobby menu over the lobby level): a
 * view's full-screen {@link #clear} becomes a translucent panel, so the level stays visible
 * behind the menu. Everything else draws as on the underlying canvas.
 */
public final class OverLevelCanvas implements SceneCanvas {
    /** Panel opacity over the level (0-255). */
    public static final int PANEL_ALPHA = 0x88;

    private final SceneCanvas canvas;

    public OverLevelCanvas(SceneCanvas canvas) {
        this.canvas = java.util.Objects.requireNonNull(canvas, "canvas");
    }

    @Override public int width() { return canvas.width(); }
    @Override public int height() { return canvas.height(); }

    /** A translucent panel in {@code rgb} instead of an opaque fill. */
    @Override public void clear(int rgb) {
        canvas.fill(0, 0, canvas.width(), canvas.height(), (PANEL_ALPHA << 24) | (rgb & 0xFFFFFF));
    }

    @Override public void fill(int x, int y, int w, int h, int argb) { canvas.fill(x, y, w, h, argb); }
    @Override public void draw(SceneImage image, float x, float y) { canvas.draw(image, x, y); }
    @Override public void draw(SceneImage image, float x, float y, SceneDraw style) { canvas.draw(image, x, y, style); }
    @Override public void draw(SceneSprite sprite, float x, float y, SceneDraw style) { canvas.draw(sprite, x, y, style); }

    @Override
    public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float dx, float dy, float dw, float dh,
                           SceneDraw style) {
        canvas.drawRegion(image, sx, sy, sw, sh, dx, dy, dw, dh, style);
    }

    @Override public void text(String text, int x, int y, int argb) { canvas.text(text, x, y, argb); }
    @Override public int textWidth(String text) { return canvas.textWidth(text); }
    @Override public void clip(int x, int y, int w, int h) { canvas.clip(x, y, w, h); }
    @Override public void unclip() { canvas.unclip(); }
    @Override public Optional<SceneClip> clipBounds() { return canvas.clipBounds(); }
}
