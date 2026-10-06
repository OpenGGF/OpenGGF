package slaytherobotnik.scene;

/**
 * Rings scattered as a hurt player loses them, following {@code Obj_Bouncing_Ring}'s set-up
 * (loc_1A67A) and fall (loc_1A75C). At most 32 rings leave in mirrored pairs: angle byte $88 up
 * to $F8 in steps of $10 through {@code GetSineCosine}, the speed shifted left by the angle
 * word's high byte (2, then 1, then 0 as each fan of 16 is used up). Each frame a ring moves,
 * gains $18 of fall, and - when falling, on one frame in eight staggered by the ring - checks
 * the floor eight pixels below its centre, bouncing off at three quarters of its speed. The
 * shared spin (Ring_spill_anim_frame) slows as Ring_spill_anim_counter runs down from $FF; when
 * it reaches 0 the rings are gone.
 */
final class SpilledRings {
    private static final int MAX = 0x20;
    private static final int GRAVITY = 0x18;

    private final int count;
    private final int[] x;
    private final int[] y;
    private final int[] xVel;
    private final int[] yVel;
    private final boolean[] alive;
    private int counter = 0xFF;
    private int accum;
    private int frame;
    private int ticks;

    /**
     * Rings leaving centre ({@code cx}, {@code cy}): {@code rings} of them (32 at most), speeds
     * from {@code sine} (the ROM's SineTable: 256 steps and a quarter more, scaled by 256).
     */
    SpilledRings(int[] sine, float cx, float cy, int rings) {
        count = Math.max(0, Math.min(MAX, rings));
        x = new int[count];
        y = new int[count];
        xVel = new int[count];
        yVel = new int[count];
        alive = new boolean[count];
        int angle = 0x288;
        int vx = 0;
        int vy = 0;
        for (int i = 0; i < count; i++) {
            x[i] = Math.round(cx * 256);
            y[i] = Math.round(cy * 256);
            alive[i] = true;
            if ((short) angle >= 0) {
                int a = angle & 0xFF;
                int shift = (angle >> 8) & 0xFF;
                vx = (short) (sine[a] << shift);
                vy = (short) (sine[a + 0x40] << shift);
                // addi.b #$10,d4 / bcc; subi.w #$80,d4 / bcc; else start again at $288.
                int low = (angle & 0xFF) + 0x10;
                angle = (angle & 0xFF00) | (low & 0xFF);
                if (low > 0xFF) {
                    angle -= 0x80;
                    if (angle < 0) {
                        angle = 0x288;
                    }
                }
            }
            xVel[i] = vx;
            yVel[i] = vy;
            vx = -vx;
            angle = (short) -angle & 0xFFFF;
        }
    }

    /** One frame; {@code floor} gives the floor row under each column of the window (clamped at its edges). */
    void tick(int[] floor) {
        if (counter > 0) {
            accum = (accum + counter) & 0xFFFF;
            frame = (accum >> 9) & 3;
            counter--;
        }
        ticks++;
        for (int i = 0; i < count; i++) {
            if (!alive[i]) {
                continue;
            }
            x[i] += xVel[i];
            y[i] += yVel[i];
            yVel[i] += GRAVITY;
            if (yVel[i] < 0 || ((ticks + i) & 7) != 0) {
                continue;
            }
            int col = Math.max(0, Math.min(floor.length - 1, x[i] >> 8));
            int depth = ((y[i] >> 8) + 8) - floor[col];
            if (depth > 0) {
                y[i] -= depth << 8;
                yVel[i] -= yVel[i] >> 2;
                yVel[i] = -yVel[i];
            }
            if (counter == 0) {
                alive[i] = false;
            }
        }
    }

    int count() {
        return count;
    }

    boolean alive(int ring) {
        return alive[ring] && counter > 0;
    }

    float x(int ring) {
        return x[ring] / 256f;
    }

    float y(int ring) {
        return y[ring] / 256f;
    }

    int xVel(int ring) {
        return xVel[ring];
    }

    int yVel(int ring) {
        return yVel[ring];
    }

    /** The shared spin frame (Map_Ring 0-3). */
    int frame() {
        return frame;
    }

    /** True while any ring is still out. */
    boolean active() {
        return counter > 0;
    }
}
