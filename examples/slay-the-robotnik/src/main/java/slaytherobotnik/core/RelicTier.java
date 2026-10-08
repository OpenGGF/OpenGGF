package slaytherobotnik.core;

/** Relic rarity tiers, as in Slay the Spire. See {@link CardType} for why these are Strings. */
public final class RelicTier {
    /** The character's starting relic. */
    public static final String STARTER = "Starter";
    public static final String COMMON = "Common";
    public static final String UNCOMMON = "Uncommon";
    public static final String RARE = "Rare";
    /** Offered after each act boss; powerful with a drawback. */
    public static final String BOSS = "Boss";
    /** Only sold by the Egg Robo. */
    public static final String SHOP = "Shop";
    /** Only granted by events. */
    public static final String EVENT = "Event";

    private RelicTier() {
    }
}
