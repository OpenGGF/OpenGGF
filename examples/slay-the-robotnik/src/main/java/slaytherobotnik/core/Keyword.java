package slaytherobotnik.core;

/**
 * Card keywords that change how the card moves between piles.
 * See {@link CardType} for why these are String constants.
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

    /** Tooltip text for a keyword or term shown on cards. */
    public static String tooltip(String keyword) {
        return switch (keyword) {
            case EXHAUST -> "Removed until end of combat.";
            case ETHEREAL -> "If this is in your hand at the end of turn, it is Exhausted.";
            case RETAIN -> "Not discarded at the end of turn.";
            case INNATE -> "Start each combat with this card in your hand.";
            case UNPLAYABLE -> "Cannot be played.";
            default -> "";
        };
    }

    /** Keywords printed before the card text (the rest go after it), as in Slay the Spire. */
    public static boolean printedFirst(String keyword) {
        return keyword.equals(UNPLAYABLE) || keyword.equals(INNATE) || keyword.equals(ETHEREAL)
                || keyword.equals(RETAIN);
    }
}
