package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.core.Colour;

/**
 * Cheap world-space particles in parallel arrays: sparks, smoke, debris, exhaust, beam glow.
 * Each is a small square that moves, falls under its own gravity, shrinks and fades.
 */
public final class Particles {
    private static final int MAX = 1400;
    private final float[] x = new float[MAX];
    private final float[] y = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] gravity = new float[MAX];
    private final float[] size = new float[MAX];
    private final int[] colour = new int[MAX];
    private final int[] life = new int[MAX];
    private final int[] maxLife = new int[MAX];
    private final boolean[] glow = new boolean[MAX];
    private int count;

    public void add(float px, float py, float pvx, float pvy, float g, float s, int c, int ticks, boolean bright) {
        if (count >= MAX) {
            // Overwrite the oldest-looking slot.
            int victim = (int) ((px * 31 + py) % MAX);
            remove(Math.abs(victim) % count);
        }
        int i = count++;
        x[i] = px;
        y[i] = py;
        vx[i] = pvx;
        vy[i] = pvy;
        gravity[i] = g;
        size[i] = s;
        colour[i] = c;
        life[i] = ticks;
        maxLife[i] = ticks;
        glow[i] = bright;
    }

    /** A burst of {@code n} particles from a point. */
    public void burst(float px, float py, int n, float speed, int c1, int c2, float g, int ticks, long seed) {
        for (int k = 0; k < n; k++) {
            long h = eggsky.core.Rng.mix(seed + k * 0x9E3779B97F4A7C15L);
            double a = (h & 0xFFFF) / 65536.0 * Math.PI * 2;
            float sp = speed * (0.3f + ((h >>> 16) & 0xFF) / 255f * 0.9f);
            int c = ((h >>> 24) & 1) == 0 ? c1 : c2;
            add(px, py, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - speed * 0.3f, g,
                    1.5f + ((h >>> 32) & 3), c, ticks + (int) ((h >>> 40) & 15), true);
        }
    }

    private void remove(int i) {
        count--;
        x[i] = x[count];
        y[i] = y[count];
        vx[i] = vx[count];
        vy[i] = vy[count];
        gravity[i] = gravity[count];
        size[i] = size[count];
        colour[i] = colour[count];
        life[i] = life[count];
        maxLife[i] = maxLife[count];
        glow[i] = glow[count];
    }

    public void update() {
        for (int i = count - 1; i >= 0; i--) {
            life[i]--;
            if (life[i] <= 0) {
                remove(i);
                continue;
            }
            vy[i] += gravity[i];
            vx[i] *= 0.985f;
            x[i] += vx[i];
            y[i] += vy[i];
        }
    }

    public void clear() {
        count = 0;
    }

    /**
     * Draws relative to a camera whose screen centre is at world x {@code centreX} and whose top
     * is at world y {@code camY}; {@code wrap} is the planet width (or 0).
     */
    public void draw(SceneCanvas c, float centreX, float camY, float wrap, int width, int height) {
        for (int i = 0; i < count; i++) {
            float d = x[i] - centreX;
            if (wrap > 0) {
                d = ((d % wrap) + wrap * 1.5f) % wrap - wrap / 2;
            }
            float sx = width / 2f + d;
            float sy = y[i] - camY;
            if (sx < -8 || sy < -8 || sx > width + 8 || sy > height + 8) {
                continue;
            }
            float t = life[i] / (float) maxLife[i];
            float s = Math.max(1, size[i] * (0.4f + 0.6f * t));
            int col = Colour.fade(colour[i], Math.min(1, t * 1.6f));
            if (glow[i] && s >= 2) {
                c.fill(Math.round(sx - s), Math.round(sy - s), Math.round(s * 2 + 1), Math.round(s * 2 + 1),
                        Colour.fade(col, 0.25f));
            }
            c.fill(Math.round(sx - s / 2), Math.round(sy - s / 2), Math.round(s), Math.round(s), col);
        }
    }

    public int count() {
        return count;
    }
}
