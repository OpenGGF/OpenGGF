package sonicbros.sim;

/**
 * The launch rules. Damage makes fighters easier to launch: knockback grows with the target's
 * percent and the hit's damage, shrinks with weight, and decides launch speed and hitstun.
 */
public final class Knockback {
    /** Pixels per frame of launch speed per point of knockback. */
    public static final double SPEED_PER_KB = 0.059;
    /** Launch speed lost per frame, in pixels per frame. */
    public static final double DECAY = 0.085;
    /** Hitstun frames per point of knockback. */
    public static final double HITSTUN_PER_KB = 0.4;
    /** Knockback above which the target tumbles (and can tech, and only then crosses the top blast line). */
    public static final double TUMBLE = 70;

    private Knockback() { }

    /**
     * Knockback for a hit of {@code damage} on a target whose damage is now {@code percent}
     * (including this hit) and who weighs {@code weight}.
     */
    public static double of(double damage, double percent, double weight, double baseKb, double growth) {
        double scaled = (percent / 10 + percent * damage / 20) * (200 / (weight + 100)) * 1.4 + 18;
        return scaled * growth / 100 + baseKb;
    }

    /** Frames the target is helpless after the hit. */
    public static int hitstun(double kb) { return (int) Math.floor(kb * HITSTUN_PER_KB); }

    /** Freeze frames both fighters share on contact. */
    public static int hitlag(double damage, int effect) {
        double frames = damage * 0.38 + 4;
        if (effect == Hit.FX_ELECTRIC) frames *= 1.5;
        return (int) Math.min(22, Math.floor(frames));
    }

    /** The launch angle in degrees (counter-clockwise from the attacker's forward) for a hit. */
    public static double angle(int angle, double kb, boolean targetGrounded) {
        if (angle != Hit.SAKURAI) return angle;
        if (!targetGrounded) return 40;
        if (kb < 60) return 0;
        if (kb > 88) return 40;
        return (kb - 60) / 28 * 40;
    }
}
