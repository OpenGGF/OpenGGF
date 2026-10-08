package sitarhero.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

/**
 * Draw-time easing. The scene changes screens and selections on the tick the input arrives;
 * these helpers only shape what is drawn in the frames after it, so a transition can never
 * delay a key press, hide a control or move the music clock.
 */
public final class Motion {
    /** Ticks for a screen's content to settle after it opens. */
    public static final int ENTER_TICKS = 10;
    /** Ticks for the selection highlight to glide to a new row. */
    public static final int GLIDE_TICKS = 6;

    private Motion() { }

    /** 0 at {@code start}, 1 once {@code duration} ticks have passed. */
    public static double progress(long now, long start, int duration) {
        if (duration <= 0 || now >= start + duration) return 1;
        return now <= start ? 0 : (now - start) / (double) duration;
    }

    /** Decelerating curve: quick to respond, gentle to land. */
    public static double easeOut(double t) {
        double inverse = 1 - Math.max(0, Math.min(1, t));
        return 1 - inverse * inverse * inverse;
    }

    public static int lerp(int from, int to, double t) { return (int) Math.round(from + (to - from) * t); }

    /** {@code argb} with its alpha scaled by {@code fraction} (0-1). */
    public static int fade(int argb, double fraction) {
        int alpha = (int) Math.round((argb >>> 24) * Math.max(0, Math.min(1, fraction)));
        return alpha << 24 | (argb & 0xFFFFFF);
    }

    /**
     * A value that glides from where it was to a new target. Call {@link #moveTo} from
     * {@code update}; {@link #at} is a pure function of the tick for {@code draw}.
     */
    public static final class Glide {
        private int from, to;
        private long start = Long.MIN_VALUE;
        private final int duration;

        public Glide(int duration) { this.duration = duration; }

        public void moveTo(int target, long now) {
            if (start == Long.MIN_VALUE) { snap(target); return; }
            if (target == to) return;
            from = at(now); to = target; start = now;
        }

        /** Jumps straight to {@code target}, as if it arrived long ago. */
        public void snap(int target) { from = target; to = target; start = Long.MIN_VALUE / 2; }

        public int at(long now) { return lerp(from, to, easeOut(progress(now, start, duration))); }

        public int target() { return to; }

        /** 0 just after the last move, 1 once {@code ticks} have passed since it. */
        public double settled(long now, int ticks) { return progress(now, start, ticks); }
    }

    /**
     * Draws through to another canvas, shifted and faded. A screen's content is drawn through
     * one while it eases in, so every panel, text and sprite moves together. Backdrops use
     * {@link SceneCanvas}'s default band-by-band {@code drawRegion}, so they fade too.
     */
    public static final class ShiftedCanvas implements SceneCanvas {
        private final SceneCanvas target;
        private final int dx;
        private final double alpha;

        public ShiftedCanvas(SceneCanvas target, int dx, double alpha) {
            this.target = target; this.dx = dx; this.alpha = Math.max(0, Math.min(1, alpha));
        }

        @Override public int width() { return target.width(); }
        @Override public int height() { return target.height(); }
        @Override public void clear(int rgb) {
            if (alpha >= 1) target.clear(rgb);
            else target.fill(0, 0, target.width(), target.height(), fade(0xFF000000 | rgb, alpha));
        }
        @Override public void fill(int x, int y, int w, int h, int argb) { target.fill(x + dx, y, w, h, fade(argb, alpha)); }
        @Override public void draw(SceneImage image, float x, float y) { draw(image, x, y, SceneDraw.plain()); }
        @Override public void draw(SceneImage image, float x, float y, SceneDraw style) {
            target.draw(image, x + dx, y, style.withAlpha((float) alpha));
        }
        @Override public void draw(SceneSprite sprite, float x, float y, SceneDraw style) {
            target.draw(sprite, x + dx, y, style.withAlpha((float) alpha));
        }
        @Override public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float x, float y,
                                         float w, float h, SceneDraw style) {
            target.drawRegion(image, sx, sy, sw, sh, x + dx, y, w, h, style.withAlpha((float) alpha));
        }
        @Override public void text(String text, int x, int y, int argb) { target.text(text, x + dx, y, fade(argb, alpha)); }
        @Override public int textWidth(String text) { return target.textWidth(text); }
        @Override public void clip(int x, int y, int w, int h) { target.clip(x + dx, y, w, h); }
        @Override public void unclip() { target.unclip(); }
    }
}
