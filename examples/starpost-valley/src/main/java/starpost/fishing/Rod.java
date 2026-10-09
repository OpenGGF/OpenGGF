package starpost.fishing;

import com.openggf.mods.scene.SceneCanvas;

/**
 * The fishing rod in the farmer's hand: an original drawing in Mega Drive colours (simple shapes
 * are the mod's own art; no ROM has a rod). A red blank with a dark outline tapers to a white tip,
 * with a dark grip at the hand and a grey reel under it. Its angle and the bend of its tip follow
 * the line: up at rest, back over the shoulder winding up, whipped forward by the cast, low and
 * still while waiting, bent and shaking on a bite, lifted again as the line comes in.
 */
final class Rod {
    /** From the butt to the tip, in pixels. */
    static final int LENGTH = 28;
    /** The butt sticks out behind the hand by this much. */
    private static final int BUTT = 5;
    private static final int OUTLINE = 0xFF240000;
    private static final int BLANK = 0xFFDB2400;
    private static final int BLANK_LIT = 0xFFFF6D49;
    private static final int GRIP = 0xFF492400;
    private static final int REEL = 0xFFB6B6B6;
    private static final int REEL_DARK = 0xFF494949;
    private static final int TIP = 0xFFFFFFFF;

    /** Angles (degrees above the facing direction) of the rod's poses. */
    static final float REST = 64;
    static final float WAIT = 32;
    static final float BACK = 150;
    static final float CAST = 18;
    static final float BITE = 24;
    static final float REELED = 72;
    /** Ticks of the cast's whip forward, and of the lift as the line comes in. */
    static final int WHIP_TICKS = 6;
    static final int REEL_TICKS = 12;

    private Rod() {
    }

    /**
     * The rod's angle {@code ticks} into a cast: whipped from over the shoulder to low and forward,
     * then settling at the waiting angle.
     */
    static float castAngle(int ticks) {
        if (ticks < WHIP_TICKS) {
            float t = ticks / (float) WHIP_TICKS;
            return BACK + (CAST - BACK) * t * t;
        }
        return Math.min(WAIT, CAST + (ticks - WHIP_TICKS) * 2f);
    }

    /** The rod's angle {@code ticks} after the line came in: lifted, then lowered to rest. */
    static float reelAngle(int ticks) {
        if (ticks < REEL_TICKS) {
            return WAIT + (REELED - WAIT) * (float) Math.sin(ticks / (float) REEL_TICKS * Math.PI / 2);
        }
        return Math.max(REST, REELED - (ticks - REEL_TICKS));
    }

    /**
     * Where the farmer's front hand is in his standing frame, as {forward, up} pixels from the
     * point between his feet: Sonic's frame $BA and Knuckles's $56 hold it at the hip; Tails is
     * shorter.
     */
    static float[] hand(String farmer) {
        return switch (farmer) {
            case "tails" -> new float[] {6, 13};
            case "knuckles" -> new float[] {8, 16};
            default -> new float[] {6, 17};
        };
    }

    /** The rod's tip at {@code angle}, as {forward, up} pixels from the point between the feet. */
    static float[] tip(String farmer, float angle) {
        float[] hand = hand(farmer);
        double a = Math.toRadians(angle);
        float reach = LENGTH - BUTT;
        return new float[] {hand[0] + (float) Math.cos(a) * reach, hand[1] + (float) Math.sin(a) * reach};
    }

    /** The bend of a hooked line: the tip pulled down and shaking. */
    static float bend(long ticks, boolean hooked) {
        return hooked ? 6 + (float) Math.sin(ticks / 2.0) * 2 : 0;
    }

    /**
     * Draws the rod gripped at ({@code hx}, {@code hy}) facing {@code dir} (1 right, -1 left),
     * {@code angle} degrees above level, its tip pulled down {@code bend} pixels. Returns the tip.
     */
    static float[] draw(SceneCanvas canvas, float hx, float hy, float dir, float angle, float bend) {
        double a = Math.toRadians(angle);
        float ux = (float) Math.cos(a) * dir, uy = -(float) Math.sin(a);
        float bx = hx - ux * BUTT, by = hy - uy * BUTT;
        int steps = LENGTH * 2;
        // Outline first, then the blank over it, so the rod reads on grass, water and sky alike.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = bx + ux * LENGTH * t, y = by + uy * LENGTH * t + bend * t * t;
                int px = Math.round(x), py = Math.round(y);
                boolean thick = t < 0.45f;
                if (pass == 0) {
                    int size = thick ? 4 : 3;
                    canvas.fill(px - 1, py - 1, size, size, OUTLINE);
                } else if (t * LENGTH < BUTT + 1) {
                    canvas.fill(px, py, 2, 2, GRIP);
                } else if (t > 0.97f) {
                    canvas.fill(px, py, 1, 1, TIP);
                } else {
                    canvas.fill(px, py, thick ? 2 : 1, thick ? 2 : 1, thick ? BLANK : BLANK_LIT);
                    if (thick) {
                        canvas.fill(px, py, 1, 1, BLANK_LIT);
                    }
                }
            }
        }
        // The reel hangs under the rod just ahead of the hand.
        float rx = hx + ux * 3, ry = hy + uy * 3 + 3;
        int x0 = Math.round(rx) - 1, y0 = Math.round(ry) - 1;
        canvas.fill(x0 - 1, y0 - 1, 5, 5, OUTLINE);
        canvas.fill(x0, y0, 3, 3, REEL);
        canvas.fill(x0 + 1, y0 + 1, 1, 1, REEL_DARK);
        return tipAt(hx, hy, dir, angle, bend);
    }

    /** Where {@link #draw} puts the tip for the same arguments. */
    static float[] tipAt(float hx, float hy, float dir, float angle, float bend) {
        double a = Math.toRadians(angle);
        float ux = (float) Math.cos(a) * dir, uy = -(float) Math.sin(a);
        float reach = LENGTH - BUTT;
        return new float[] {hx + ux * reach, hy + uy * reach + bend};
    }
}
