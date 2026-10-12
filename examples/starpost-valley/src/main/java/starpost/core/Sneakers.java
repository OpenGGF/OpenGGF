package starpost.core;

/**
 * The spin dash is the hoe, and sneakers widen it (design doc §6.2). Every farmer starts in plain
 * Sneakers, which till the row the dash rolls along; Power Sneakers also till the row in front;
 * Speed Shoes both neighbouring rows; Chaos Sneakers till like Speed Shoes and run across water
 * (the farm pond holds a farmer who keeps up half of Sonic's top speed). Tails builds each pair at
 * his workshop. The tier is a story flag ({@link #flag}), so it saves with the game's other flags
 * and needs no save format of its own.
 *
 * <p>How the farmers' own differences ({@link Farmers}) combine with a tier: a dash tills a
 * <em>column</em> of rows at each step, so Tails's weaker spin (three plots) becomes three columns,
 * each as wide as his sneakers (a 3x3 patch in Speed Shoes); Sonic and Knuckles till every column
 * they roll over. Every plot still costs a point of Momentum, and Knuckles's dig (the action
 * button on one plot) is unchanged by sneakers.
 */
public final class Sneakers {
    public static final int SNEAKERS = 0;
    public static final int POWER = 1;
    public static final int SPEED = 2;
    public static final int CHAOS = 3;
    /** Running on water needs at least this speed along the field (pixels a tick): half of the belt controller’s top speed. */
    public static final float WATER_RUN_SPEED = 3;
    /** Ticks a farmer may dawdle on the water before sinking back to the bank. */
    public static final int SINK_GRACE = 12;

    private Sneakers() {
    }

    /** The farmer's tier: the best pair Tails has built. */
    public static int tier(Game game) {
        for (int t = CHAOS; t > SNEAKERS; t--) {
            if (game.flags.contains(flag(t))) {
                return t;
            }
        }
        return SNEAKERS;
    }

    /** Gives the farmer a tier (and so every tier below it). */
    public static void grant(Game game, int tier) {
        for (int t = POWER; t <= Math.min(CHAOS, tier); t++) {
            game.flags.add(flag(t));
        }
    }

    /** The story flag a tier sets, or null for the plain Sneakers everyone starts in. */
    public static String flag(int tier) {
        return switch (tier) {
            case POWER -> "sneakers_power";
            case SPEED -> "sneakers_speed";
            case CHAOS -> "sneakers_chaos";
            default -> null;
        };
    }

    /** The catalogue item that names and pictures a tier. */
    public static String item(int tier) {
        return switch (tier) {
            case POWER -> "power_sneakers";
            case SPEED -> "speed_shoes";
            case CHAOS -> "chaos_sneakers";
            default -> "sneakers";
        };
    }

    public static String name(int tier) {
        return switch (tier) {
            case POWER -> "POWER SNEAKERS";
            case SPEED -> "SPEED SHOES";
            case CHAOS -> "CHAOS SNEAKERS";
            default -> "SNEAKERS";
        };
    }

    /**
     * Row offsets a dash tills from its own row; positive is toward the screen (the row in front).
     * A new array each call (no static tables).
     */
    public static int[] rows(int tier) {
        return switch (tier) {
            case POWER -> new int[] {0, 1};
            case SPEED, CHAOS -> new int[] {0, -1, 1};
            default -> new int[] {0};
        };
    }

    /** The field rows a dash along {@code row} tills, nearest first, leaving out rows off the field. */
    public static int[] rowsTilled(int row, int tier) {
        int[] offsets = rows(tier);
        int n = 0;
        int[] out = new int[offsets.length];
        for (int offset : offsets) {
            int r = row + offset;
            if (r >= 0 && r < Farm.ROWS) {
                out[n++] = r;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    public static boolean runsOnWater(int tier) {
        return tier >= CHAOS;
    }

    /**
     * Whether a farmer on water goes under: without Chaos Sneakers at once; in them, after
     * {@link #SINK_GRACE} ticks slower than {@link #WATER_RUN_SPEED} along the field.
     *
     * @param slowTicks ticks in a row spent on the water below running speed, this one included
     */
    public static boolean sinks(int tier, int slowTicks) {
        return !runsOnWater(tier) || slowTicks > SINK_GRACE;
    }

    /**
     * What Tails asks for a tier at his workshop, in rings: along the Water Shield tank's ladder
     * (500, 2,000, 5,000), the last pair a little dearer.
     */
    public static int price(int tier) {
        return switch (tier) {
            case POWER -> 600;
            case SPEED -> 2500;
            case CHAOS -> 6000;
            default -> 0;
        };
    }

    /** The material a tier needs: scrap for the first two, an Emerald Shard from the Ruins for Chaos Sneakers. */
    public static String material(int tier) {
        return tier == CHAOS ? "emerald_shard" : "scrap";
    }

    public static int materialCount(int tier) {
        return switch (tier) {
            case POWER -> 10;
            case SPEED -> 30;
            default -> 1;
        };
    }

    /**
     * One spin dash's tilling, engine-free: each new column it rolls over tills the rows the
     * farmer's sneakers reach (a point of Momentum a plot; rocks, stumps and placed objects are
     * rolled past), and a column counts toward {@link Farmers#dashTills} only when something in it
     * was tilled. Make a new one for each dash.
     */
    public static final class Dash {
        private int columns;
        private int last = -1;

        /** The dash over a plot; returns the field rows it tilled there (empty when nothing). */
        public int[] over(Game game, int row, int column) {
            boolean newColumn = column != last;
            if (newColumn && columns >= Farmers.dashTills(game.farmer)) {
                return new int[0];
            }
            int[] rows = rowsTilled(row, tier(game));
            int[] tilled = new int[rows.length];
            int n = 0;
            for (int r : rows) {
                Plot plot = game.farm.plot(r, column);
                if (plot != null && !plot.tilled && plot.cover != Plot.ROCK && plot.cover != Plot.STUMP
                        && plot.object == null && game.spend(1)) {
                    plot.cover = Plot.GRASS;
                    plot.tilled = true;
                    tilled[n++] = r;
                }
            }
            if (n > 0 && newColumn) {
                last = column;
                columns++;
            }
            return java.util.Arrays.copyOf(tilled, n);
        }

        /** Columns tilled so far. */
        public int columns() {
            return columns;
        }
    }

    /** Whether a speed along the field keeps a Chaos Sneakers farmer on top of the water. */
    public static boolean fastEnough(float speed) {
        return Math.abs(speed) >= WATER_RUN_SPEED;
    }
}
