package com.openggf.mods.scene;

/**
 * What a scene draws with, in logical screen pixels with the origin at the top-left. Calls
 * are drawn in order, so later calls appear on top. Colours are {@code 0xAARRGGBB} unless a
 * method says {@code rgb}. A canvas is only valid during the {@link ModScene#draw} call that
 * received it.
 */
@com.openggf.game.ModApi
public interface SceneCanvas extends com.openggf.mods.ui.PixelCanvas {
    /** The logical screen width in pixels (the same as {@link SceneContext#width()}). */
    int width();

    /** The logical screen height in pixels (224). */
    int height();

    /** Fills the whole screen with an opaque colour ({@code 0xRRGGBB}), ignoring any {@link #clip}. */
    void clear(int rgb);

    /** Fills a rectangle; the colour's alpha blends it over what is below. */
    void fill(int x, int y, int w, int h, int argb);

    /** Draws an image with its top-left at (x, y). */
    void draw(SceneImage image, float x, float y);

    /**
     * Draws an image with its top-left at (x, y), styled: scaling grows it right and down from
     * that corner, and mirroring flips it in place (unlike a {@link SceneSprite}, whose origin
     * is the pivot).
     */
    void draw(SceneImage image, float x, float y, SceneDraw style);

    /**
     * Draws a sprite with its origin at (x, y). Scaling and mirroring happen around the
     * origin, as on hardware.
     */
    void draw(SceneSprite sprite, float x, float y, SceneDraw style);

    /**
     * Draws part of an image (source rectangle {@code sx, sy, sw, sh} in image pixels) stretched
     * into the destination rectangle {@code dx, dy, dw, dh}; the style's flips mirror the source
     * and its scale is ignored (the destination size already says how big).
     */
    void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float dx, float dy, float dw, float dh,
            SceneDraw style);

    /**
     * Draws a {@link SceneBackdrop} into the rectangle {@code x, y, width, height}: image row
     * {@code top} lands on screen row {@code y}, and each band is scrolled to
     * {@link SceneBackdrop#column}{@code (band, scrollX, ticks)} and repeated across the width.
     * It draws exactly inside the rectangle (it never changes the {@link #clip}); rows of the
     * rectangle that fall outside the image are left as they were, so keep {@code top} within
     * {@code 0 .. image height - height} to cover it.
     *
     * <pre>{@code
     * SceneBackdrop bg = ctx.art().rom().zoneBackdrop(0, 0);           // Angel Island act 1
     * canvas.drawBackdrop(bg, 0x100, scrollX, ctx.ticks());            // the whole screen
     * canvas.drawBackdrop(bg, 40, 30, 160, 90, 0x100, scrollX, ctx.ticks());   // a window
     * }</pre>
     */
    default void drawBackdrop(SceneBackdrop backdrop, int x, int y, int width, int height, int top, double scrollX,
            long ticks) {
        SceneImage image = backdrop.image();
        int imageWidth = image.width();
        for (SceneBackdrop.Band band : backdrop.bands()) {
            int y0 = Math.max(band.top(), top);
            int y1 = Math.min(band.top() + band.height(), top + height);
            if (y0 >= y1) {
                continue;
            }
            int column = backdrop.column(band, scrollX, ticks);
            for (int done = 0; done < width; ) {
                int source = (column + done) % imageWidth;
                int run = Math.min(imageWidth - source, width - done);
                drawRegion(image, source, y0, run, y1 - y0, x + done, y + y0 - top, run, y1 - y0,
                        SceneDraw.plain());
                done += run;
            }
        }
    }

    /** Draws a {@link SceneBackdrop} over the whole screen; see the windowed overload. */
    default void drawBackdrop(SceneBackdrop backdrop, int top, double scrollX, long ticks) {
        drawBackdrop(backdrop, 0, 0, width(), height(), top, scrollX, ticks);
    }

    /**
     * Draws text with the engine's outlined menu font (9x10 glyphs on a 10-pixel advance,
     * letters, digits and common punctuation). {@code argb} tints it.
     */
    void text(String text, int x, int y, int argb);

    /** Width in pixels of {@link #text} output. */
    int textWidth(String text);

    /**
     * Restricts later drawing to a rectangle until {@link #unclip}. Clips do not nest: a second
     * call replaces the first rectangle rather than intersecting with it.
     */
    void clip(int x, int y, int w, int h);

    /** Drawing covers the whole screen again. */
    void unclip();
}
