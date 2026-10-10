package starpost.core;

/**
 * How the three farmers differ on the farm (design doc §4). Sonic's spin dash is the strongest and
 * tills its whole roll. Tails's spin is weaker and short, but his two tails fan the Water Shield's
 * water over the next plot too. Knuckles digs instead of tilling, sometimes turning up what is
 * buried, and punches rocks apart without the Fire Shield; he is too heavy for the farm loop's
 * full payoff.
 *
 * <p>Sneakers ({@link Sneakers}) widen every farmer's dash the same way: each column the dash
 * tills becomes as many rows as the sneakers reach. Tails's limit counts columns, so his three
 * plots become three columns (a 3x3 patch in Speed Shoes); Knuckles's dig stays one plot.
 */
public final class Farmers {
    public static final String SONIC = "sonic";
    public static final String TAILS = "tails";
    public static final String KNUCKLES = "knuckles";
    /** One dig in this many turns something up. */
    public static final int DIG_ODDS = 8;

    private Farmers() {
    }

    /** The spin dash's release speed, in pixels a tick. */
    public static float dashSpeed(String farmer) {
        return switch (farmer) {
            case TAILS -> 6;
            case KNUCKLES -> 8;
            default -> 9;
        };
    }

    /**
     * How many columns one spin dash may till (Integer.MAX_VALUE: all it rolls over); each column is
     * one plot in plain Sneakers and as many rows as better ones reach.
     */
    public static int dashTills(String farmer) {
        return farmer.equals(TAILS) ? 3 : Integer.MAX_VALUE;
    }

    /** Plots one Water Shield charge waters: the one underfoot, and for Tails the next one on. */
    public static int waterReach(String farmer) {
        return farmer.equals(TAILS) ? 2 : 1;
    }

    /** Whether the farmer breaks rocks without the Fire Shield. */
    public static boolean punchesRocks(String farmer) {
        return farmer.equals(KNUCKLES);
    }

    /** Momentum from a lap of the farm loop: the hourly full lap, or a token lap in between. */
    public static int lapBonus(String farmer, boolean full) {
        int bonus = full ? 30 : 5;
        return farmer.equals(KNUCKLES) ? bonus / 2 : bonus;
    }

    /**
     * What Knuckles's dig turns up, or null (most digs, and every other farmer's tilling): rings
     * as {@code "rings:N"}, otherwise an item id from the catalog (marble chips, a museum relic of
     * the season, or the season's forage).
     */
    public static String dig(Game game) {
        if (!game.farmer.equals(KNUCKLES) || game.rng.nextInt(DIG_ODDS) != 0) {
            return null;
        }
        int roll = game.rng.nextInt(4);
        if (roll < 2) {
            return "rings:" + (5 + game.rng.nextInt(16));
        }
        if (roll == 2) {
            // Half the stones he turns up are relics for the museum (the season's: starpost.museum.Finds).
            String relic = starpost.museum.Finds.buriedRelic(game);
            return game.rng.nextInt(2) == 0 && game.catalog.hasItem(relic) ? relic : "marble_chip";
        }
        String forage = switch (game.calendar.season()) {
            case Calendar.SPRING -> "totem_leek";
            case Calendar.SUMMER -> "loop_berry";
            case Calendar.FALL -> "palm_coconut";
            default -> "snow_spud";
        };
        return game.catalog.hasItem(forage) ? forage : "marble_chip";
    }
}
