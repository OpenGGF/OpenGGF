package slaytherobotnik.core;

/** How damage interacts with modifiers and Block, as in Slay the Spire. */
public final class DamageType {
    /** Attacks: modified by Strength, Weak and Vulnerable; absorbed by Block. */
    public static final String ATTACK = "Attack";
    /** Reactive or relic damage (Thorns, explosions): unmodified, absorbed by Block. */
    public static final String THORNS = "Thorns";
    /** Direct HP loss: unmodified and ignores Block. */
    public static final String HP_LOSS = "HpLoss";

    private DamageType() {
    }
}
