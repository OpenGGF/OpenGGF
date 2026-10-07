package survivors;

/**
 * The one difficulty curve every pressure lever reads: enemy and boss hitpoints, spawn rate and
 * batch size, the ring toll and the rings each badnik drops all come from the route tier and the
 * current arena's pressure clock ({@link Stage#pressureSeconds}). Nothing reads total run time, so
 * a new arena never opens with soft enemies and a crushing toll, and starting further along the
 * route is not safer than playing through to it.
 */
final class Difficulty {
    private Difficulty() { }

    /** Pressure in two-minute units. */
    static double progress(int pressureSeconds) {
        return Math.max(0, pressureSeconds) / 120.0;
    }

    /** Enemy hitpoint multiplier over a species' base hitpoints. */
    static double hpScale(int tier, int act, int pressureSeconds) {
        double p = progress(pressureSeconds);
        // Superlinear in the route: the last zones are meant to demand a long-built profile.
        return (1 + 0.40 * tier + 0.05 * tier * tier) * (1 + 1.4 * p + 0.4 * p * p) * (1 + 0.2 * act);
    }

    /**
     * Boss hitpoints: a multiple of an ordinary badnik's at the moment the boss arrives, so the
     * stage climax keeps pace with the waves before it. The Death Egg has no survival clock and
     * is rated as if five minutes had passed.
     */
    static int bossHp(int stage, int act, int pressureSeconds, int mode) {
        String override = System.getProperty("sonic-survivors.bossHp");
        if (override != null) {
            try { return Math.max(1, Integer.parseInt(override.trim())); } catch (NumberFormatException ignored) { }
        }
        int tier = Stages.tier(stage, act);
        int seconds = stage == Stages.DEZ ? 300 : pressureSeconds;
        double factor = stage == Stages.DEZ ? 18 : 12;
        double hp = Stages.averageHp(stage) * hpScale(tier, act, seconds) * factor * (mode == RunState.LONG ? 1.25 : 1);
        return (int) Math.max(40 + 12 * tier, Math.round(hp));
    }

    /** Frames between ordinary spawn batches. */
    static int spawnInterval(int tier, int pressureSeconds) {
        return Math.max(24, 60 - tier - pressureSeconds / 10);
    }

    /** Badniks per ordinary batch. */
    static int batch(int tier, int pressureSeconds) {
        return 1 + pressureSeconds / 75 + tier / 4;
    }

    /** The ring toll's multiplier: route position plus this arena's pressure. */
    static double tollThreat(int stage, int act, int pressureSeconds) {
        return 1 + 0.25 * pressureSeconds / 60.0 + 0.20 * stage + 0.10 * act;
    }

    /** Rings a defeated ordinary badnik is worth before Greed, emeralds, combo and act bonuses. */
    static double dropValue(int tier) {
        return 1 + tier / 3.0;
    }

    /** Elite spacing: every 30 seconds at first, never closer than twelve. */
    static int elitePeriod(int tier, int pressureSeconds) {
        return Math.max(12 * 60, 30 * 60 - pressureSeconds * 2 - tier * 60);
    }
}
