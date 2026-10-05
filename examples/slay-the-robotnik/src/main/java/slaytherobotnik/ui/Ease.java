package slaytherobotnik.ui;

/** Easing curves for animations; {@code t} runs 0..1. */
public final class Ease {
    private Ease() {
    }

    public static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    public static float outCubic(float t) {
        float u = 1f - clamp(t);
        return 1f - u * u * u;
    }

    public static float inOutQuad(float t) {
        t = clamp(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    /** Overshoots then settles (a card snapping into place). */
    public static float outBack(float t) {
        t = clamp(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    /** Linear interpolation. */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Moves {@code current} towards {@code target} by a fraction each tick (smooth follow). */
    public static float approach(float current, float target, float fraction) {
        float next = current + (target - current) * fraction;
        return Math.abs(target - next) < 0.05f ? target : next;
    }
}
