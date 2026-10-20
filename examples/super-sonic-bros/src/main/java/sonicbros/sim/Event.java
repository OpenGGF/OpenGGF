package sonicbros.sim;

/**
 * Something the presentation should show or play this frame: the simulation never draws or
 * makes sounds itself, it reports what happened.
 *
 * @param type   one of the constants
 * @param x      world position
 * @param y      world position
 * @param a      type-specific (sound id, spark strength, KO side, item kind...)
 * @param b      type-specific (effect, direction...)
 * @param port   the fighter it concerns, or -1
 */
public record Event(int type, double x, double y, int a, int b, int port) {
    /** A sound effect: {@code a} is the S3K sound id. */
    public static final int SOUND = 0;
    /** A hit landed: {@code a} is knockback (strength), {@code b} the hit's effect. */
    public static final int HIT = 1;
    /** A hit was blocked by a shield; {@code a} is damage. */
    public static final int BLOCK = 2;
    /** A fighter crossed a blast line: {@code a} is 0 left, 1 right, 2 top, 3 bottom. */
    public static final int KO = 3;
    /** A puff of dust at feet (landing, dashing, skids): {@code a} 1 for a big one. */
    public static final int DUST = 4;
    /** An explosion: {@code a} is its radius. */
    public static final int EXPLOSION = 5;
    /** A shield broke. */
    public static final int SHIELD_BREAK = 6;
    /** A monitor was opened: {@code a} is the item kind. */
    public static final int ITEM = 7;
    /** Sparkles (healing rings, power-ups). */
    public static final int SPARKLE = 8;
    /** A reflected projectile. */
    public static final int REFLECT = 9;
    /** A tech (quick recovery on landing) succeeded. */
    public static final int TECH = 10;
    /** Screen shake: {@code a} is its strength in pixels. */
    public static final int SHAKE = 11;
    /** A projectile was fired: {@code a} its kind. */
    public static final int SHOT = 12;
    /** A star KO (flying off the top far away): {@code a} unused. */
    public static final int STAR_KO = 13;
}
