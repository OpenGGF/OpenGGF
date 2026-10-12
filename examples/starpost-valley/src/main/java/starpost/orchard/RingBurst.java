package starpost.orchard;

import java.util.ArrayList;
import java.util.List;
import starpost.ruins.RuinsRules;

/**
 * The Ring Fruit Tree's harvest: shaken, it bursts its rings out like a hit's scattered rings and
 * the farmer has to catch them. Engine-free. The spray is Sonic 1's {@code RLoss_Count}
 * ({@link RuinsRules#ringBurst}) at half speed, because the tree drops them rather than a hit
 * flinging them; along the field's depth the mirrored pairs fan out a little so they land on the
 * neighbouring rows. They fall with {@code RLoss_Bounce}'s $18 gravity and bounce losing a quarter
 * of their speed; the field has no walls to stop them, so the speed along it loses a quarter at
 * each bounce too. All of them blink out after the shared 255 frames, as scattered rings do.
 */
public final class RingBurst {
    /** A caught ring must be this close to the farmer's feet (pixels along, across, and up). */
    public static final float REACH_X = 12;
    public static final float REACH_DEPTH = 8;
    public static final float REACH_HEIGHT = 28;
    /** A held Lightning Shield pulls rings from this far (as in the Ruins), at this speed. */
    public static final float PULL_RANGE = 64;
    public static final float PULL_SPEED = 3;

    /** One ring in the air or on the ground: world x, depth (feet row), height above the ground. */
    public static final class Ring {
        public float x;
        public float depth;
        public float height;
        float vx;
        float vDepth;
        float vHeight;

        Ring(float x, float depth, float height, float vx, float vDepth, float vHeight) {
            this.x = x;
            this.depth = depth;
            this.height = height;
            this.vx = vx;
            this.vDepth = vDepth;
            this.vHeight = vHeight;
        }
    }

    private final List<Ring> rings = new ArrayList<>();
    private int timer;
    private final float minDepth;
    private final float maxDepth;

    /**
     * Rings burst from {@code height} pixels up a tree standing at ({@code x}, {@code depth}),
     * landing between {@code minDepth} and {@code maxDepth} (the field's feet rows).
     */
    public RingBurst(int count, float x, float depth, float height, float minDepth, float maxDepth) {
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        int[][] spray = RuinsRules.ringBurst(Math.min(RuinsRules.MAX_SCATTER, count));
        for (int i = 0; i < spray.length; i++) {
            float vx = spray[i][0] / 256f / 2;
            float up = -spray[i][1] / 256f / 2;
            float fan = (i % 4 < 2 ? 1 : -1) * (0.15f + (i / 4 % 4) * 0.12f);
            rings.add(new Ring(x, depth, height, vx, fan, up));
        }
        timer = RuinsRules.LOST_RING_FRAMES;
    }

    /** One frame of flight and bouncing; the rings vanish together when the timer runs out. */
    public void step() {
        if (timer <= 0) {
            rings.clear();
            return;
        }
        timer--;
        for (Ring r : rings) {
            r.x += r.vx;
            r.depth = Math.max(minDepth, Math.min(maxDepth, r.depth + r.vDepth));
            r.vHeight -= RuinsRules.LOST_RING_GRAVITY;
            r.height += r.vHeight;
            if (r.height <= 0 && r.vHeight < 0) {
                r.height = 0;
                int bounced = RuinsRules.bounce(Math.round(-r.vHeight * 256));
                r.vHeight = -bounced / 256f;
                r.vx -= r.vx / 4;
                r.vDepth -= r.vDepth / 4;
            }
        }
        if (timer == 0) {
            rings.clear();
        }
    }

    /**
     * Rings the farmer at ({@code x}, {@code depth}) catches this frame: they are removed and
     * counted. With the Lightning Shield held, rings in range first fly toward the farmer.
     */
    public int collect(float x, float depth, float jumpHeight, boolean lightning) {
        int caught = 0;
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            float dx = x - r.x, dd = depth - r.depth;
            if (lightning && Math.abs(dx) < PULL_RANGE && Math.abs(dd) < PULL_RANGE) {
                float d = (float) Math.max(1, Math.hypot(dx, dd));
                float step = Math.min(PULL_SPEED, d);
                r.x += dx / d * step;
                r.depth += dd / d * step;
                r.height = Math.max(jumpHeight, r.height - 1);
                r.vx = 0;
                r.vDepth = 0;
                r.vHeight = 0;
                dx = x - r.x;
                dd = depth - r.depth;
            }
            if (Math.abs(dx) < REACH_X && Math.abs(dd) < REACH_DEPTH && Math.abs(r.height - jumpHeight) < REACH_HEIGHT) {
                rings.remove(i);
                caught++;
            }
        }
        return caught;
    }

    public List<Ring> rings() {
        return rings;
    }

    public boolean done() {
        return rings.isEmpty();
    }

    /** Frames left before the rest blink out. */
    public int timer() {
        return timer;
    }
}
