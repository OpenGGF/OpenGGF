package eggsky.world;

/**
 * A stock act a planet's terrain is remixed from, with the climate it implies.
 *
 * @param game     "s1", "s2" or "s3k"
 * @param zone     public zone id in that game
 * @param act      zero-based act
 * @param title    the biome's descriptive name ("Frozen", "Volcanic"...)
 * @param climate  one of the {@code CLIMATE_} constants
 * @param weather  one of the {@code WEATHER_} constants (the planet's storms)
 * @param music    S3K music id played on the surface
 * @param flora    one of the {@code FLORA_} constants (procedural plant style)
 * @param lush     0-3: how much flora and fauna the planet carries
 * @param rows     terrain rows to keep from the top of the act's layout (0 = all)
 */
public record Biome(String game, int zone, int act, String title, int climate, int weather, int music, int flora,
        int lush, int rows) {
    public static final int CLIMATE_TEMPERATE = 0;
    public static final int CLIMATE_LUSH = 1;
    public static final int CLIMATE_HOT = 2;
    public static final int CLIMATE_COLD = 3;
    public static final int CLIMATE_TOXIC = 4;
    public static final int CLIMATE_RADIOACTIVE = 5;
    public static final int CLIMATE_EXOTIC = 6;
    public static final int CLIMATE_BARREN = 7;
    public static final int CLIMATE_DEAD = 8;

    public static final int WEATHER_NONE = 0;
    public static final int WEATHER_RAIN = 1;
    public static final int WEATHER_SNOW = 2;
    public static final int WEATHER_EMBERS = 3;
    public static final int WEATHER_SAND = 4;
    public static final int WEATHER_SPORES = 5;
    public static final int WEATHER_ACID = 6;
    public static final int WEATHER_ASH = 7;
    public static final int WEATHER_SPARKS = 8;

    public static final int FLORA_JUNGLE = 0;
    public static final int FLORA_FUNGAL = 1;
    public static final int FLORA_CRYSTAL = 2;
    public static final int FLORA_DESERT = 3;
    public static final int FLORA_FROST = 4;
    public static final int FLORA_TECH = 5;
    public static final int FLORA_CORAL = 6;
    public static final int FLORA_EMBER = 7;

    public String key() {
        return game + "-" + zone + "-" + act;
    }

    /** Whether the climate drains hazard protection. */
    public boolean hazardous() {
        return climate == CLIMATE_HOT || climate == CLIMATE_COLD || climate == CLIMATE_TOXIC
                || climate == CLIMATE_RADIOACTIVE;
    }

    public static String climateName(int climate) {
        return switch (climate) {
            case CLIMATE_LUSH -> "Lush";
            case CLIMATE_HOT -> "Scorched";
            case CLIMATE_COLD -> "Frozen";
            case CLIMATE_TOXIC -> "Toxic";
            case CLIMATE_RADIOACTIVE -> "Irradiated";
            case CLIMATE_EXOTIC -> "Exotic";
            case CLIMATE_BARREN -> "Barren";
            case CLIMATE_DEAD -> "Dead";
            default -> "Temperate";
        };
    }

    /** The hazard the climate threatens, for the HUD. */
    public static String hazardName(int climate) {
        return switch (climate) {
            case CLIMATE_HOT -> "HEAT";
            case CLIMATE_COLD -> "COLD";
            case CLIMATE_TOXIC -> "TOXIN";
            case CLIMATE_RADIOACTIVE -> "RADIATION";
            default -> "";
        };
    }

    public static String weatherName(int weather) {
        return switch (weather) {
            case WEATHER_RAIN -> "Rainstorms";
            case WEATHER_SNOW -> "Blizzards";
            case WEATHER_EMBERS -> "Firestorms";
            case WEATHER_SAND -> "Sandstorms";
            case WEATHER_SPORES -> "Spore clouds";
            case WEATHER_ACID -> "Acid rain";
            case WEATHER_ASH -> "Ash storms";
            case WEATHER_SPARKS -> "Electrical storms";
            default -> "Calm";
        };
    }
}
