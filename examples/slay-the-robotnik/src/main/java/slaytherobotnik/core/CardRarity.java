package slaytherobotnik.core;

/**
 * Card rarity. BASIC cards only come from the starter deck; SPECIAL cards are created by
 * other cards, relics or events (tokens such as Tails' Ring Bomb) and never appear as
 * rewards. See {@link CardType} for why these are String constants.
 */
public final class CardRarity {
    public static final String BASIC = "Basic";
    public static final String COMMON = "Common";
    public static final String UNCOMMON = "Uncommon";
    public static final String RARE = "Rare";
    public static final String SPECIAL = "Special";
    public static final String CURSE = "Curse";

    private CardRarity() {
    }
}
