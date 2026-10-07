package survivors;

/**
 * The run's route and every act's arena. A run visits the ten stages in order (Sky Chase has
 * no ground to fight on and is skipped); at each stage the player picked one of that zone's acts.
 * Each act's arena is a stretch of its stock terrain with continuous, pit-free floor, found by
 * surveying the act's collision and checked in captures: the camera and the player's level
 * boundary are held to it, so the level's goal is never reachable.
 */
final class Stages {
    private Stages() { }

    static final int COUNT = 10;
    static final int EHZ = 0, CPZ = 1, ARZ = 2, CNZ = 3, HTZ = 4, MCZ = 5, OOZ = 6, MTZ = 7, WFZ = 8, DEZ = 9;

    /** Sonic 2 registry zone for a stage (registry 8 is Sky Chase, which the route skips). */
    static int zone(int stage) {
        return stage >= WFZ ? stage + 1 : stage;
    }

    /** The stage for a registry zone, or -1 for Sky Chase and anything outside the route. */
    static int stageOfZone(int zone) {
        if (zone < 0 || zone > 10 || zone == 8) return -1;
        return zone > 8 ? zone - 1 : zone;
    }

    static String name(int stage) {
        return switch (stage) {
            case EHZ -> "EMERALD HILL";
            case CPZ -> "CHEMICAL PLANT";
            case ARZ -> "AQUATIC RUIN";
            case CNZ -> "CASINO NIGHT";
            case HTZ -> "HILL TOP";
            case MCZ -> "MYSTIC CAVE";
            case OOZ -> "OIL OCEAN";
            case MTZ -> "METROPOLIS";
            case WFZ -> "WING FORTRESS";
            default -> "DEATH EGG";
        };
    }

    static int acts(int stage) {
        return switch (stage) {
            case MTZ -> 3;
            case WFZ, DEZ -> 1;
            default -> 2;
        };
    }

    /** Act 2 (and Metropolis act 3) are harder: tougher, more numerous badniks and richer drops. */
    static String actFlavour(int act) {
        return switch (act) {
            case 0 -> "NORMAL";
            case 1 -> "HARD  +50% RINGS";
            default -> "BRUTAL  +100% RINGS";
        };
    }

    /**
     * Arena bounds for a stage's act: {left, right, floorTop, floorBottom} in level pixels.
     * left/right are the walls; the floor surface between them lies within floorTop..floorBottom.
     */
    static int[] arena(int stage, int act) {
        return switch (stage * 4 + act) {
            case EHZ * 4 -> new int[]{32, 1300, 643, 703};
            case EHZ * 4 + 1 -> new int[]{9700, 10840, 1027, 1091};
            case CPZ * 4 -> new int[]{9000, 10380, 1023, 1087};
            case CPZ * 4 + 1 -> new int[]{9250, 10450, 767, 767};
            case ARZ * 4 -> new int[]{9500, 10700, 975, 1039};
            case ARZ * 4 + 1 -> new int[]{5350, 6480, 975, 1010};
            case CNZ * 4 -> new int[]{8250, 9420, 1727, 1887};
            case CNZ * 4 + 1 -> new int[]{9260, 10320, 1605, 1663};
            case HTZ * 4 -> new int[]{5980, 6750, 1471, 1474};
            case HTZ * 4 + 1 -> new int[]{6560, 7410, 319, 322};
            case MCZ * 4 -> new int[]{3600, 4320, 1535, 1599};
            case MCZ * 4 + 1 -> new int[]{3440, 4430, 1919, 1982};
            case OOZ * 4 -> new int[]{3480, 4190, 1215, 1343};
            case OOZ * 4 + 1 -> new int[]{3240, 4440, 895, 959};
            case MTZ * 4 -> new int[]{8100, 9100, 511, 575};
            case MTZ * 4 + 1 -> new int[]{7080, 8080, 383, 447};
            case MTZ * 4 + 2 -> new int[]{4510, 5210, 959, 991};
            case WFZ * 4 -> new int[]{2700, 3800, 895, 1023};
            default -> new int[]{600, 1800, 383, 383};
        };
    }

    // ---- Badnik line-ups: ground and air species for each stage. ----
    static int[] ground(int stage) {
        return switch (stage) {
            case EHZ -> new int[]{Species.COCONUTS, Species.MASHER};
            case CPZ -> new int[]{Species.SPINY};
            case CNZ -> new int[]{Species.CRAWL};
            case HTZ -> new int[]{Species.SPIKER};
            case MCZ -> new int[]{Species.CRAWLTON};
            case OOZ -> new int[]{Species.OCTUS};
            case MTZ -> new int[]{Species.SHELLCRACKER, Species.SLICER};
            case WFZ -> new int[]{Species.CLUCKER};
            default -> new int[0];
        };
    }

    static int[] air(int stage) {
        return switch (stage) {
            case EHZ -> new int[]{Species.BUZZER};
            case CPZ -> new int[]{Species.GRABBER};
            case ARZ -> new int[]{Species.WHISP, Species.CHOPCHOP};
            case HTZ -> new int[]{Species.SOL};
            case MCZ -> new int[]{Species.FLASHER};
            case OOZ -> new int[]{Species.AQUIS};
            case MTZ -> new int[]{Species.ASTERON};
            case WFZ -> new int[]{Species.BALKIRY};
            default -> new int[0];
        };
    }

    /** Survival time before the boss arrives, in seconds. Death Egg is a straight boss fight. */
    static int survivalSeconds(int stage, int mode) {
        if (stage == DEZ) return 0;
        if (mode == RunState.ENDLESS) return -1;
        // Development: -Dsonic-survivors.survival=N shortens every stage's clock (for filming and tests).
        String override = System.getProperty("sonic-survivors.survival");
        if (override != null) {
            try { return Math.max(1, Integer.parseInt(override.trim())); } catch (NumberFormatException ignored) { }
        }
        return mode == RunState.LONG ? 600 : 300;
    }

    /** Overall difficulty tier: one per stage, plus the act's extra difficulty. */
    static int tier(int stage, int act) {
        return stage + act;
    }

    static String bossName(int stage) {
        return switch (stage) {
            case ARZ -> "WHISP QUEEN";
            case WFZ -> "BALKIRY ACE";
            case OOZ -> "OIL SENTINEL";
            case DEZ -> "SILVER SONIC";
            default -> "EGGMAN";
        };
    }

    // ---- Boss art. ----
    static String bossKey(int stage) {
        return switch (stage) {
            case EHZ -> "ehz_boss";
            case CPZ -> "cpz_boss_eggpod";
            case ARZ -> "arz_boss_main";
            case CNZ -> "cnz_boss";
            case HTZ -> "htz_boss";
            case MCZ -> "mcz_boss";
            case OOZ -> "ooz_boss";
            case MTZ -> "mtz_boss";
            case WFZ -> "wfz_boss";
            default -> "dez_silver_sonic";
        };
    }

    /** Frames: normal, laugh (after hurting Sonic), hurt. */
    static int[] bossFrames(int stage) {
        return switch (stage) {
            case EHZ -> new int[]{16, 18, 21};
            case CPZ -> new int[]{1, 3, 6};
            case ARZ -> new int[]{0, 2, 5};
            case CNZ -> new int[]{6, 8, 11};
            case HTZ -> new int[]{1, 1, 16};
            case MCZ -> new int[]{14, 16, 19};
            case OOZ -> new int[]{1, 1, 1};
            case MTZ -> new int[]{12, 14, 17};
            case WFZ -> new int[]{0, 2, 4};
            default -> new int[]{0, 1, 5};
        };
    }

    /** The boss's projectile art key. */
    static String bossShotKey(int stage) {
        return switch (stage) {
            case EHZ -> "ehz_boss";
            case CNZ -> "cnz_boss";
            case HTZ -> "sol";
            case MCZ -> "mcz_falling_rocks";
            case OOZ -> "ooz_boss";
            case MTZ -> "mtz_boss";
            case WFZ -> "wfz_boss";
            case DEZ -> "dez_silver_sonic";
            default -> "super_sonic_stars";
        };
    }

    /** The boss's projectile frame in {@link #bossShotKey}. */
    static int bossShotFrame(int stage) {
        return switch (stage) {
            case EHZ -> 8;
            case CNZ -> 18;
            case HTZ -> 3;
            case MCZ -> 0;
            case OOZ -> 4;
            case MTZ -> 3;
            case WFZ -> 13;
            case DEZ -> 15;
            default -> 2;
        };
    }

    /** Boss hitpoints: grows along the route. */
    static int bossHp(int stage, int act) {
        // Development: -Dsonic-survivors.bossHp=N sets every boss's hitpoints (for filming).
        String override = System.getProperty("sonic-survivors.bossHp");
        if (override != null) {
            try { return Math.max(1, Integer.parseInt(override.trim())); } catch (NumberFormatException ignored) { }
        }
        return stage == DEZ ? 140 : 24 + 10 * tier(stage, act);
    }
}
