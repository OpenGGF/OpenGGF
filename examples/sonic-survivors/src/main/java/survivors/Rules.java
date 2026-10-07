package survivors;

/**
 * Eggman's Rules: optional handicaps chosen in camp, each raising the rings a run banks.
 * Bits of {@link Profile#rules} and {@link RunState#rules}; only append.
 */
final class Rules {
    private Rules() { }

    static final int HEAVY_TOLL = 0, NO_FEVER = 1, ELITE_HORDE = 2, TOUGH_HIDES = 3, NO_MAGNET = 4, BRITTLE = 5;
    static final int COUNT = 6;

    static String name(int rule) {
        return switch (rule) {
            case HEAVY_TOLL -> "HEAVY TOLL";
            case NO_FEVER -> "NO FEVER";
            case ELITE_HORDE -> "ELITE HORDE";
            case TOUGH_HIDES -> "TOUGH HIDES";
            case NO_MAGNET -> "NO MAGNETS";
            default -> "BRITTLE RINGS";
        };
    }

    static String effect(int rule) {
        return switch (rule) {
            case HEAVY_TOLL -> "HITS COST 50% MORE RINGS";
            case NO_FEVER -> "BOUNCES NEVER CHARGE FEVER";
            case ELITE_HORDE -> "ELITES ARRIVE TWICE AS OFTEN";
            case TOUGH_HIDES -> "BADNIKS AND BOSSES +50% HP";
            case NO_MAGNET -> "RING PULL ONLY FROM MONITORS";
            default -> "LOST RINGS NEVER SCATTER";
        };
    }

    /** Extra banked rings, in percent. */
    static int bonus(int rule) {
        return switch (rule) {
            case HEAVY_TOLL -> 25;
            case NO_FEVER -> 20;
            case ELITE_HORDE -> 20;
            case TOUGH_HIDES -> 30;
            case NO_MAGNET -> 15;
            default -> 15;
        };
    }

    static int totalBonus(int rules) {
        int total = 0;
        for (int rule = 0; rule < COUNT; rule++) if ((rules & 1 << rule) != 0) total += bonus(rule);
        return total;
    }
}
