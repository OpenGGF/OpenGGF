package slaytherobotnik.scene;

/**
 * Rings drawn to the hero the way a Lightning Shield pulls them in ({@code Obj_Attracted_Ring}):
 * each axis speeds towards the hero by $30 a frame, by $C0 while still moving away
 * (AttractedRing_Move); the ring spins a frame every 4 (Obj_Attracted_RingAnimate). Touching
 * the hero (the ring's 6-pixel touch box against the player's 8 by 16) collects it - GiveRing,
 * sfx_RingRight - and it sparkles through Map_Ring frames 4-7, 6 frames each
 * (Ani_RingSparkle). The rings leaving the offering bowl one after another, with a little
 * hop, is the mod's own staging.
 */
final class AttractedRings {
    private static final int PULL = 0x30;
    /** Ani_RingSparkle: delay 5, four frames. */
    private static final int SPARKLE_FRAME = 6;
    /** A ring still circling after this long is taken as caught, so a picture never waits for ever. */
    private static final int LATE = 150;

    private final int count;
    private final int[] x;
    private final int[] y;
    private final int[] xVel;
    private final int[] yVel;
    private final int[] spin;
    private final int[] start;
    /** -1 while flying; then frames since collection. */
    private final int[] collected;
    private final int targetX;
    private final int targetY;
    private int ticks;
    private int collectedNow;

    /**
     * {@code rings} rings leaving ({@code fromX}, {@code fromY}) one every {@code interval} frames,
     * drawn to a hero centred on ({@code toX}, {@code toY}).
     */
    AttractedRings(int rings, float fromX, float fromY, float toX, float toY, int interval) {
        count = rings;
        x = new int[count];
        y = new int[count];
        xVel = new int[count];
        yVel = new int[count];
        spin = new int[count];
        start = new int[count];
        collected = new int[count];
        targetX = Math.round(toX * 256);
        targetY = Math.round(toY * 256);
        for (int i = 0; i < count; i++) {
            // Spread across the bowl, each popping up and out a little differently.
            x[i] = Math.round((fromX + ((i * 5) % 11) - 5) * 256);
            y[i] = Math.round(fromY * 256);
            xVel[i] = ((i * 37) % 0x180) - 0xC0;
            yVel[i] = -0x300 - ((i * 53) % 0x100);
            start[i] = i * interval;
            collected[i] = -1;
            spin[i] = i % 4;
        }
    }

    /** One frame. */
    void tick() {
        collectedNow = 0;
        ticks++;
        for (int i = 0; i < count; i++) {
            if (ticks <= start[i]) {
                continue;
            }
            if (collected[i] >= 0) {
                collected[i]++;
                continue;
            }
            xVel[i] += pull(x[i], targetX, xVel[i]);
            yVel[i] += pull(y[i], targetY, yVel[i]);
            x[i] += xVel[i];
            y[i] += yVel[i];
            if (((ticks - start[i]) & 3) == 0) {
                spin[i] = (spin[i] + 1) & 3;
            }
            boolean late = ticks - start[i] > LATE;
            if (late || Math.abs(x[i] - targetX) < (8 + 6) * 256 && Math.abs(y[i] - targetY) < (16 + 6) * 256) {
                collected[i] = 0;
                collectedNow++;
            }
        }
    }

    /** AttractedRing_Move on one axis: $30 towards the hero, four times that if heading away. */
    private static int pull(int pos, int target, int vel) {
        int d = PULL;
        if (target >= pos) {
            return vel < 0 ? d * 4 : d;
        }
        return vel >= 0 ? -d * 4 : -d;
    }

    int count() {
        return count;
    }

    /** True once ring {@code i} has left the bowl. */
    boolean out(int i) {
        return ticks > start[i];
    }

    /** The Map_Ring frame ring {@code i} shows, or -1 once its sparkle is over. */
    int frame(int i) {
        if (collected[i] < 0) {
            return spin[i];
        }
        int sparkle = collected[i] / SPARKLE_FRAME;
        return sparkle < 4 ? 4 + sparkle : -1;
    }

    float x(int i) {
        return x[i] / 256f;
    }

    float y(int i) {
        return y[i] / 256f;
    }

    /** Rings collected this frame (one sfx_RingRight each). */
    int collectedNow() {
        return collectedNow;
    }

    /** True once every ring has been collected and has finished sparkling. */
    boolean done() {
        for (int i = 0; i < count; i++) {
            if (frame(i) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Rings still waiting in the bowl. */
    int waiting() {
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (!out(i)) {
                n++;
            }
        }
        return n;
    }
}
