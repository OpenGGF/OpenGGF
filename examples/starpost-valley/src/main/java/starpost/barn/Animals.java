package starpost.barn;

import starpost.core.Calendar;
import starpost.core.Game;

/**
 * The farm's animals by kind (design doc §6.7): Sonic 1's freed animals, each with its home and
 * what it gives. Cuckies lay eggs daily; Peckies lay Ice Eggs every other day, in winter only
 * (winter's animal income); Pockies grow fluff every three days; Pickies dig Hill Truffles from
 * the grass on dry days outside winter; Rocky lives in the farm pond and fishes for you.
 */
public final class Animals {
    public static final int COOP = 0;
    public static final int PEN = 1;
    public static final int POND = 2;

    private Animals() {
    }

    /** Every kind, in menu order. */
    public static String[] kinds() {
        return new String[] {"cucky", "pecky", "pocky", "picky", "rocky"};
    }

    public static boolean known(String kind) {
        for (String k : kinds()) {
            if (k.equals(kind)) {
                return true;
            }
        }
        return false;
    }

    public static int home(String kind) {
        return switch (kind) {
            case "cucky", "pecky" -> COOP;
            case "pocky", "picky" -> PEN;
            default -> POND;
        };
    }

    public static String name(String kind) {
        return kind.toUpperCase();
    }

    /** What one costs at its home. */
    public static int price(String kind) {
        return switch (kind) {
            case "cucky" -> 400;
            case "pecky" -> 1200;
            case "pocky" -> 1600;
            case "picky" -> 2400;
            default -> 3000;
        };
    }

    /** What it gives (Rocky's is a fish, chosen each morning). */
    public static String product(String kind) {
        return switch (kind) {
            case "cucky" -> "cucky_egg";
            case "pecky" -> "ice_egg";
            case "pocky" -> "pocky_fluff";
            case "picky" -> "hill_truffle";
            default -> null;
        };
    }

    /** Days old before it gives anything. */
    public static int adult(String kind) {
        return switch (kind) {
            case "cucky" -> 2;
            case "pocky" -> 4;
            case "picky" -> 5;
            default -> 3;
        };
    }

    /** Days between goods: a Pocky's fluff every three (two for a Shepherd), Ice Eggs every two. */
    public static int every(String kind, Game game) {
        return switch (kind) {
            case "pocky" -> game.has("shepherd") ? 2 : 3;
            case "pecky" -> 2;
            default -> 1;
        };
    }

    /** Whether today's season and weather let it give: Ice Eggs in winter, truffles on dry days outside it. */
    public static boolean givesToday(String kind, int season, int weather) {
        return switch (kind) {
            case "pecky" -> season == Calendar.WINTER;
            case "picky" -> season != Calendar.WINTER && weather == Game.SUN;
            default -> true;
        };
    }

    /** Whether it goes out today (grazes and is seen on the field): dry weather, or snow for a Pecky. */
    public static boolean outside(String kind, int weather) {
        return kind.equals("rocky") || weather == Game.SUN || weather == Game.SWARM
                || kind.equals("pecky") && weather == Game.SNOW;
    }

    /** Names for new animals, the first one not yet taken is used. */
    public static String[] names(String kind) {
        return switch (kind) {
            case "cucky" -> new String[] {"NUGGET", "PEEP", "DOTTY", "SUNNY", "CLUCKS", "BEAKY", "OMELETTE", "PENNY"};
            case "pecky" -> new String[] {"TUX", "FLIPPER", "CHILLY", "BRRR", "WADDLE", "SLEET", "PUDDLE", "FROSTY"};
            case "pocky" -> new String[] {"BUN", "COTTON", "HOPS", "FLUFFY", "THUMPER", "CLOUD", "MUFFIN", "SOCKS"};
            case "picky" -> new String[] {"OINKS", "SNOUT", "PORKY", "BACON", "MUD", "ROOTER", "GOLDIE", "PIGGY"};
            default -> new String[] {"SPLASH", "WHISKERS", "BLUBBER", "FINN", "DIVER", "SKIPPER", "BOBBER", "PEBBLE"};
        };
    }

    /** Affection a day's petting gives (doubled for a Cuddler). */
    public static int petGain(Game game) {
        return game.has("cuddler") ? 30 : 15;
    }

    /** Hearts shown for an affection (0-1000): five, a heart per 200. */
    public static int hearts(int affection) {
        return Math.max(0, Math.min(5, affection / 200));
    }

    /** Percent chance a fed adult gives when due: half, plus up to half again with affection. */
    public static int chance(int affection) {
        return 50 + affection * 50 / 1000;
    }
}
