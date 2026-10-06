package slaytherobotnik.core;

/**
 * Card frame colour, which also names the card pool a card belongs to.
 * See {@link CardType} for why these are String constants.
 */
public final class CardColor {
    /** Sonic's pool. */
    public static final String BLUE = "Blue";
    /** Tails' pool. */
    public static final String ORANGE = "Orange";
    /** Knuckles' pool. */
    public static final String RED = "Red";
    /** Shared cards any character can find (shops, events, some relics). */
    public static final String COLORLESS = "Colorless";
    public static final String STATUS = "Status";
    public static final String CURSE = "Curse";

    private CardColor() {
    }

    /** Frame tint used by the card renderer, as 0xRRGGBB. */
    public static int rgb(String color) {
        return switch (color) {
            case BLUE -> 0x2858E8;
            case ORANGE -> 0xF0A020;
            case RED -> 0xD82828;
            case COLORLESS -> 0x9898A8;
            case CURSE -> 0x583070;
            default -> 0x606068;
        };
    }
}
