package slaytherobotnik.core;

/**
 * Who a card (or potion) needs the player to point at when it is played.
 * See {@link CardType} for why these are String constants.
 */
public final class CardTarget {
    /** The player chooses one living enemy. */
    public static final String ENEMY = "Enemy";
    public static final String ALL_ENEMIES = "AllEnemies";
    /** The card picks a living enemy itself (per hit for multi-hit cards). */
    public static final String RANDOM_ENEMY = "RandomEnemy";
    public static final String SELF = "Self";
    public static final String NONE = "None";

    private CardTarget() {
    }

    public static boolean needsChoice(String target) {
        return ENEMY.equals(target);
    }
}
