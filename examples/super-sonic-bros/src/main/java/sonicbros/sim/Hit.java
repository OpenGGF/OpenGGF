package sonicbros.sim;

/**
 * One hitbox window of a move: a circle at an offset from the fighter's body centre (x forward,
 * y down), live from frame {@code start} to {@code end} inclusive.
 *
 * <p>Knockback follows the familiar platform-fighter formula: base knockback plus growth scaled by
 * the target's damage and weight (see {@link Knockback}). {@code angle} is in degrees from the
 * attacker's forward direction, counter-clockwise, so 90 sends straight up and 270 is a spike;
 * {@link #SAKURAI} launches low hits along the ground and strong hits at 40 degrees.
 *
 * @param group    hits sharing a group connect once per target per move; later windows of a
 *                 multi-hit move use new groups or a {@code rehit} interval
 * @param rehit    0 to hit a target once per group, otherwise frames between repeated hits
 * @param effect   presentation and elemental behaviour, one of the {@code FX_} constants
 * @param shieldMul multiplier on the damage this hit does to shields
 */
public record Hit(int start, int end, double x, double y, double r, double damage, int angle,
        double baseKb, double growth, int group, int rehit, int effect, double shieldMul, boolean grounded) {
    public static final int SAKURAI = 361;
    public static final int FX_NORMAL = 0;
    public static final int FX_SPIN = 1;
    public static final int FX_FIRE = 2;
    public static final int FX_ELECTRIC = 3;
    public static final int FX_HEAVY = 4;
    public static final int FX_SLASH = 5;
    public static final int FX_EXPLOSION = 6;

    public static Hit of(int start, int end, double x, double y, double r, double damage, int angle, double baseKb,
            double growth) {
        return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, 0, 0, FX_NORMAL, 1.0, false);
    }

    public Hit group(int g) { return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, g, rehit, effect, shieldMul, grounded); }

    public Hit rehit(int frames) { return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, group, frames, effect, shieldMul, grounded); }

    public Hit fx(int fx) { return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, group, rehit, fx, shieldMul, grounded); }

    public Hit shield(double mul) { return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, group, rehit, effect, mul, grounded); }

    /** Only hits targets standing on the ground (sweeps and shockwaves). */
    public Hit groundOnly() { return new Hit(start, end, x, y, r, damage, angle, baseKb, growth, group, rehit, effect, shieldMul, true); }

    public boolean live(int frame) { return frame >= start && frame <= end; }
}
