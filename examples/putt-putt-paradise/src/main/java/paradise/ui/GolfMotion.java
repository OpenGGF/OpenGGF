package paradise.ui;

/** Deterministic easing on presentation ticks. Nothing here reads a clock or gameplay state. */
public final class GolfMotion {
    private GolfMotion() { }

    /** Linear 0..1 progress of an animation that started {@code elapsed} ticks ago. */
    public static float progress(long elapsed, int duration) {
        if (duration <= 0) return 1;
        return Math.clamp(elapsed / (float) duration, 0f, 1f);
    }
    public static float easeOut(float t) { float u = 1 - t; return 1 - u * u * u; }
    public static float easeIn(float t) { return t * t * t; }
    public static float easeInOut(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
    /** Overshoots slightly, then settles: cards that "land" on screen. */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
    /** Falling letters: three diminishing bounces. */
    public static float bounce(float t) {
        float n = 7.5625f, d = 2.75f;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) { t -= 1.5f / d; return n * t * t + 0.75f; }
        if (t < 2.5f / d) { t -= 2.25f / d; return n * t * t + 0.9375f; }
        t -= 2.625f / d; return n * t * t + 0.984375f;
    }
    public static int lerp(int from, int to, float t) { return Math.round(from + (to - from) * t); }
    /** 0..1..0 triangle wave with the given period in ticks. */
    public static float pulse(long clock, int period) {
        long phase = Math.floorMod(clock, period);
        float half = period / 2f;
        return phase < half ? phase / half : (period - phase) / half;
    }
    public static boolean blink(long clock, int period) { return Math.floorMod(clock, period) < period / 2; }
    /** Linear blend of two 0xRRGGBB colours. */
    public static int mix(int from, int to, float t) {
        t = Math.clamp(t, 0f, 1f);
        int r = lerp(from >> 16 & 255, to >> 16 & 255, t), g = lerp(from >> 8 & 255, to >> 8 & 255, t), b = lerp(from & 255, to & 255, t);
        return r << 16 | g << 8 | b;
    }
}
