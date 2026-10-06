package slaytherobotnik.core;

/**
 * Card keywords that change how the card moves between piles, and the glossary behind the
 * tips for every highlighted card term. See {@link CardType} for why these are String constants.
 */
public final class Keyword {
    /** After play, the card goes to the exhaust pile for the rest of the combat. */
    public static final String EXHAUST = "Exhaust";
    /** If still in hand at the end of the turn, the card is exhausted. */
    public static final String ETHEREAL = "Ethereal";
    /** Not discarded at the end of the turn. */
    public static final String RETAIN = "Retain";
    /** Starts each combat in the opening hand. */
    public static final String INNATE = "Innate";
    /** Cannot be played at all. */
    public static final String UNPLAYABLE = "Unplayable";

    private Keyword() {
    }

    /**
     * What a highlighted card term means, for the tips shown beside a focused card: the pile
     * keywords above, the powers cards apply, and Sonic's Combo. Accepts the forms card text
     * uses ("Exhausted", "Retained"). Empty when the term has no entry; terms that name a
     * card, such as Ring Bomb or Dent, are explained from that card instead.
     */
    public static String tooltip(String term) {
        return switch (term) {
            case EXHAUST, "Exhausted" -> "Removed until end of combat.";
            case ETHEREAL -> "If this is in your hand at the end of turn, it is Exhausted.";
            case RETAIN, "Retained" -> "Not discarded at the end of turn.";
            case INNATE -> "Start each combat with this card in your hand.";
            case UNPLAYABLE -> "Cannot be played.";
            case "Combo" -> "The effect after Combo N happens N times. Focus adds to every Combo.";
            case Powers.FOCUS -> "Sonic's stat. Each point makes every Combo effect happen once more.";
            case Powers.STRENGTH -> "Each point adds 1 damage to every hit of an Attack.";
            case Powers.DEXTERITY -> "Each point adds 1 to the Block cards give.";
            case Powers.VULNERABLE -> "Takes 50% more damage from Attacks. Counts down each turn.";
            case Powers.WEAK -> "Deals 25% less damage with Attacks. Counts down each turn.";
            case Powers.FRAIL -> "Gains 25% less Block from cards. Counts down each turn.";
            case Powers.ARTIFACT -> "Negates the next debuff, one per point.";
            case Powers.INTANGIBLE -> "Reduces all damage taken to 1 until the start of the next turn.";
            default -> "";
        };
    }

    /** The name a tip is headed with: the term as {@link #tooltip} files it ("Exhausted" -> "Exhaust"). */
    public static String headword(String term) {
        return switch (term) {
            case "Exhausted" -> EXHAUST;
            case "Retained" -> RETAIN;
            default -> term;
        };
    }

    /** Keywords printed before the card text (the rest go after it), as in Slay the Spire. */
    public static boolean printedFirst(String keyword) {
        return keyword.equals(UNPLAYABLE) || keyword.equals(INNATE) || keyword.equals(ETHEREAL)
                || keyword.equals(RETAIN);
    }
}
