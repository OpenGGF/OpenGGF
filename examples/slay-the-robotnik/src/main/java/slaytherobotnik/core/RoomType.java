package slaytherobotnik.core;

/** Map node types, as in Slay the Spire. See {@link CardType} for why these are Strings. */
public final class RoomType {
    /** The Tornado flight that opens each act (Neow in Slay the Spire). */
    public static final String START = "Start";
    public static final String MONSTER = "Monster";
    public static final String ELITE = "Elite";
    public static final String EVENT = "Event";
    public static final String TREASURE = "Treasure";
    public static final String SHOP = "Shop";
    /** A Starpost: rest or smith. */
    public static final String REST = "Rest";
    public static final String BOSS = "Boss";

    private RoomType() {
    }

    public static boolean isCombat(String type) {
        return type.equals(MONSTER) || type.equals(ELITE) || type.equals(BOSS);
    }

    public static String label(String type) {
        return switch (type) {
            case START -> "Tornado";
            case MONSTER -> "Badniks";
            case ELITE -> "Elite";
            case EVENT -> "Unknown";
            case TREASURE -> "Treasure";
            case SHOP -> "Egg Robo Shop";
            case REST -> "Starpost";
            case BOSS -> "Boss";
            default -> type;
        };
    }
}
