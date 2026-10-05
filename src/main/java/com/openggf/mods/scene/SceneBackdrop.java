package com.openggf.mods.scene;

import java.util.List;
import java.util.Objects;

/**
 * A zone's background plane as one opaque picture, cut into horizontal bands that scroll at
 * their own speeds: the game's parallax. {@link SceneRomArt#zoneBackdrop} returns these for
 * stock zones; a mod may also build one from its own image.
 *
 * <p>Every row repeats horizontally at {@code image().width()}, and the bands cover the image
 * from row 0 to the bottom in order, without gaps. A band shows the image column
 * {@link #column} at the screen's left edge, so drawing it twice side by side (or more on
 * wide screens) fills any width:
 *
 * <pre>{@code
 * SceneBackdrop bg = ctx.art().rom().zoneBackdrop(0, 0);   // Angel Island act 1, or null
 * int top = 0x100;                                         // which image rows the screen shows
 * int w = bg.image().width();
 * for (SceneBackdrop.Band band : bg.bands()) {
 *     int y0 = Math.max(band.top(), top);
 *     int y1 = Math.min(band.top() + band.height(), top + canvas.height());
 *     if (y0 >= y1) {
 *         continue;
 *     }
 *     for (int x = -bg.column(band, scrollX, ctx.ticks()); x < canvas.width(); x += w) {
 *         canvas.drawRegion(bg.image(), 0, y0, w, y1 - y0, x, y0 - top, w, y1 - y0, SceneDraw.plain());
 *     }
 * }
 * }</pre>
 *
 * <p>Build images once (in {@link ModScene#enter} or lazily) and reuse them; the engine
 * caches stock backdrops per zone and act.
 *
 * @param image the opaque picture
 * @param bands top to bottom, covering every image row exactly once
 */
@com.openggf.game.ModApi
public record SceneBackdrop(SceneImage image, List<Band> bands) {
    /**
     * Rows {@code top .. top + height - 1} of the image, moving at {@code speed} times the
     * scene's scroll (1 moves with the foreground, 0 stays still) plus {@code drift} pixels
     * per tick on their own (drifting clouds; 0 for most bands). Positive values move the
     * picture left, as the level background does when the camera moves right.
     */
    @com.openggf.game.ModApi
    public record Band(int top, int height, double speed, double drift) {
        public Band {
            if (top < 0 || height <= 0) {
                throw new IllegalArgumentException("Band rows out of range: top " + top + ", height " + height);
            }
            if (!Double.isFinite(speed) || !Double.isFinite(drift)) {
                throw new IllegalArgumentException("Band speed and drift must be finite");
            }
        }
    }

    /** Checks that {@code bands} cover the image rows in order and copies the list. */
    public SceneBackdrop {
        Objects.requireNonNull(image, "image");
        bands = List.copyOf(bands);
        int next = 0;
        for (Band band : bands) {
            if (band.top() != next) {
                throw new IllegalArgumentException("Bands must cover the image in order: expected row " + next
                        + ", got " + band.top());
            }
            next = band.top() + band.height();
        }
        if (next != image.height()) {
            throw new IllegalArgumentException("Bands cover " + next + " rows of a " + image.height() + "-row image");
        }
    }

    /**
     * The image column a band shows at the screen's left edge after the scene has scrolled
     * {@code scrollX} pixels and {@code ticks} ticks have passed: {@code scrollX * speed +
     * ticks * drift}, rounded down and wrapped into {@code 0 .. width - 1}.
     */
    public int column(Band band, double scrollX, long ticks) {
        long position = (long) Math.floor(scrollX * band.speed() + ticks * band.drift());
        return (int) Math.floorMod(position, (long) image.width());
    }
}
